package dev.t3craft.client.demo;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jspecify.annotations.Nullable;

/**
 * "Reset the HQ world": pure logic (no Minecraft types, unit tested). Only ever touches
 * {@code <saves>/<demo folder>}: the folder must be a real directory (not a symlink) whose real
 * path is exactly that child of the real saves dir, with the exact (case-sensitive) name, and its
 * demo marker, when present, must be a demo marker. Never deletes by pattern.
 */
public final class HqReset {
	/** Files vanilla holds open while the world runs; skipped in the backup. */
	static final String SESSION_LOCK = "session.lock";
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);

	public enum Action {
		/** Playing another world, or on a server: refuse. */
		REFUSE_OTHER_WORLD,
		/** Not playing and the demo world does not exist: nothing to reset (just create it). */
		NOTHING_TO_RESET,
		/** Ask first ("everything built in the HQ is lost"). */
		CONFIRM,
		/** Confirmed: (leave,) back up, delete, recreate. */
		RESET
	}

	private HqReset() {
	}

	/**
	 * @param inWorld     playing a world or connected to a server
	 * @param inDemoWorld that world is the demo world (integrated server on the demo folder)
	 * @param demoExists  the demo save folder exists
	 * @param confirmed   the user already confirmed losing the HQ
	 */
	public static Action decide(boolean inWorld, boolean inDemoWorld, boolean demoExists, boolean confirmed) {
		if (inWorld && !inDemoWorld) {
			return Action.REFUSE_OTHER_WORLD;
		}
		if (!inWorld && !demoExists) {
			return Action.NOTHING_TO_RESET;
		}
		return confirmed ? Action.RESET : Action.CONFIRM;
	}

	/** Result of {@link #validate}: the target to delete, or why not. */
	public record Check(@Nullable Path target, String reason) {
		public boolean ok() {
			return target != null;
		}

		static Check refuse(String reason) {
			return new Check(null, reason);
		}
	}

	/**
	 * Whether {@code <savesDir>/<folderId>} may be deleted as the demo world. Resolves nothing the
	 * caller passes except the fixed folder id; checks it exactly.
	 */
	public static Check validate(Path savesDir, String folderId) {
		if (!DemoWorldSpec.validFolderId(folderId)) {
			return Check.refuse("invalid demo folder id");
		}
		try {
			if (!Files.isDirectory(savesDir)) {
				return Check.refuse("saves directory not found");
			}
			Path savesReal = savesDir.toRealPath();
			Path target = savesReal.resolve(folderId);
			if (!target.normalize().equals(target) || !savesReal.equals(target.getParent())) {
				return Check.refuse("demo folder is not directly under saves");
			}
			if (Files.isSymbolicLink(target)) {
				return Check.refuse("demo folder is a symbolic link");
			}
			if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
				return Check.refuse("demo world folder does not exist");
			}
			Path real = target.toRealPath();
			if (!savesReal.equals(real.getParent()) || real.getFileName() == null
					|| !real.getFileName().toString().equals(folderId)) {
				return Check.refuse("demo folder name does not match exactly");
			}
			if (!markerOk(real)) {
				return Check.refuse("folder has a marker that is not a T3Craft demo marker");
			}
			return new Check(real, "ok");
		} catch (IOException | RuntimeException e) {
			return Check.refuse("could not check the demo folder (" + e.getClass().getSimpleName() + ")");
		}
	}

	/** The marker is optional (written once the server first starts); if there, it must parse as ours. */
	static boolean markerOk(Path world) throws IOException {
		Path m = world.resolve(DemoMarker.FILE_NAME);
		if (!Files.exists(m, LinkOption.NOFOLLOW_LINKS)) {
			return true;
		}
		if (!Files.isRegularFile(m, LinkOption.NOFOLLOW_LINKS)) {
			return false;
		}
		DemoMarker parsed = DemoMarker.parse(Files.readString(m, StandardCharsets.UTF_8));
		return !parsed.equals(DemoMarker.EMPTY);
	}

	/** {@code <folder>_<yyyy-MM-dd_HH-mm-ss>_reset.zip}; the folder id is already a safe file name. */
	public static String backupName(String folderId, LocalDateTime now) {
		return folderId + "_" + STAMP.format(now) + "_reset.zip";
	}

	/** {@link #backupName} under {@code dir}, with {@code -2}, {@code -3}… if that file exists. */
	static Path backupFile(Path dir, String folderId, LocalDateTime now) {
		String base = backupName(folderId, now);
		Path p = dir.resolve(base);
		String stem = base.substring(0, base.length() - ".zip".length());
		for (int i = 2; Files.exists(p, LinkOption.NOFOLLOW_LINKS); i++) {
			p = dir.resolve(stem + "-" + i + ".zip");
		}
		return p;
	}

	/** Whether to zip before deleting, from the {@code demo.resetBackup} config flag. */
	public static boolean shouldBackup(DemoConfig cfg) {
		return cfg.resetBackup();
	}

	/**
	 * Zips {@code world} (entries prefixed with its folder name, like vanilla backups) into a new
	 * file in {@code backupDir}. Symlinks inside are not followed. Returns the zip.
	 */
	static Path zip(Path world, Path backupDir, String folderId, LocalDateTime now) throws IOException {
		Files.createDirectories(backupDir);
		Path out = backupFile(backupDir, folderId, now);
		try (OutputStream os = Files.newOutputStream(out, StandardOpenOption.CREATE_NEW);
				ZipOutputStream zip = new ZipOutputStream(os)) {
			Files.walkFileTree(world, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (!attrs.isRegularFile() || file.getFileName().toString().equals(SESSION_LOCK)) {
						return FileVisitResult.CONTINUE;
					}
					String rel = world.relativize(file).toString().replace('\\', '/');
					zip.putNextEntry(new ZipEntry(folderId + "/" + rel));
					Files.copy(file, zip);
					zip.closeEntry();
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException | RuntimeException e) {
			Files.deleteIfExists(out);
			throw e;
		}
		return out;
	}

	/**
	 * Deletes the demo world after re-validating it. Symlinks inside are removed as links (never
	 * followed). Returns the deleted folder.
	 */
	static Path delete(Path savesDir, String folderId) throws IOException {
		Check c = validate(savesDir, folderId);
		Path target = c.target();
		if (target == null) {
			throw new IOException("refusing to delete: " + c.reason());
		}
		Files.walkFileTree(target, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException exc) throws IOException {
				if (exc != null) {
					throw exc;
				}
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
		return target;
	}
}
