package dev.t3craft.drop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * feat/ingame-preview-drop: the drop folder, {@code config/t3craft/preview-drop/}, and its incremental
 * reader. Drop {@code .nbt} / {@code .schem} files in it (or in one level of sub-folders) while the game
 * runs; {@link #scan()} notices added, changed and removed files by name, size and modification time and
 * re-reads only those. A file that fails keeps its <b>last good version</b> and is reported once as
 * {@code file: what}; a file still being copied (modified in the last moments) is left for the next scan.
 *
 * <p>Pure file IO and no Minecraft world, so it can run on any thread; methods are synchronized, but the
 * client calls it from one worker thread only.
 */
public final class DropFolder {
	/** Folder under the game's config dir. */
	public static final String FOLDER = "t3craft/preview-drop";
	/** Name of the instructions file written into the folder (never overwritten). */
	public static final String README = "README.txt";
	/** A file modified less than this long ago may still be copying: it waits for the next scan. */
	static final long SETTLE_MS = 700;

	private record Stamp(long size, long mtime) {
	}

	private record Known(Stamp stamp, @Nullable Schematic schematic, @Nullable String error) {
	}

	/**
	 * Result of a scan that found a difference.
	 *
	 * @param schematics every schematic now available, by name (sorted)
	 * @param changed    names added or re-read
	 * @param removed    names that disappeared
	 * @param errors     problems found by this scan ({@code file: what}); a broken file keeps its last good version
	 */
	public record Load(Map<String, Schematic> schematics, List<String> changed, List<String> removed, List<String> errors) {
		public Load {
			schematics = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(schematics)); // keeps the sorted order
			changed = List.copyOf(changed);
			removed = List.copyOf(removed);
			errors = List.copyOf(errors);
		}
	}

	private final Path dir;
	/** By file path under the folder ({@code a/b.nbt}). */
	private final Map<String, Known> known = new HashMap<>();
	private boolean first = true;

	public DropFolder(Path dir) {
		this.dir = dir;
	}

	/** The drop folder in the game's config dir. */
	public static Path defaultDir() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve(FOLDER);
	}

	public Path dir() {
		return dir;
	}

	/** Creates the folder and the {@link #README} when they are missing. Returns whether the folder exists afterwards. */
	public synchronized boolean ensure() {
		try {
			Files.createDirectories(dir);
			Path readme = dir.resolve(README);
			if (!Files.exists(readme)) {
				Files.writeString(readme, readmeText(), StandardCharsets.UTF_8);
			}
			return true;
		} catch (IOException | RuntimeException e) {
			return false;
		}
	}

	/** Forgets everything read so far: the next {@link #scan()} reads every file again and reports it as new. */
	public synchronized void forgetAll() {
		known.clear();
		first = true;
	}

	/** Problems of the files that are broken right now ({@code file: what}), in file order. */
	public synchronized List<String> brokenFiles() {
		List<String> out = new ArrayList<>();
		new TreeMap<>(known).forEach((k, v) -> {
			if (v.error() != null) {
				out.add(v.error());
			}
		});
		return out;
	}

	/** Names and files now readable, sorted by name. */
	public synchronized Map<String, Schematic> current() {
		Map<String, Schematic> out = new TreeMap<>(Comparator.comparing((String s) -> s.toLowerCase(Locale.ROOT)).thenComparing(s -> s));
		for (Map.Entry<String, Known> e : new TreeMap<>(known).entrySet()) {
			Schematic s = e.getValue().schematic();
			if (s != null) {
				// a.nbt and a.schem share the name "a": the second one is listed under its file path instead of shadowing the first
				out.put(out.containsKey(s.name()) ? e.getKey() : s.name(), s);
			}
		}
		return out;
	}

	/**
	 * Looks at the folder. Returns null when nothing changed since the last scan (the common, cheap
	 * case); otherwise what changed. The first scan always returns a {@link Load}, even for an empty folder.
	 */
	public synchronized @Nullable Load scan() {
		Map<String, Path> files = list();
		if (files == null) {
			return null; // the listing failed half way: do not take the files it missed for deleted ones
		}
		List<String> changed = new ArrayList<>();
		List<String> removed = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		long now = System.currentTimeMillis();
		boolean any = first;
		first = false;

		for (String gone : new ArrayList<>(known.keySet())) {
			if (!files.containsKey(gone)) {
				Known k = known.remove(gone);
				if (k.schematic() != null) {
					removed.add(k.schematic().name());
				}
				any = true;
			}
		}
		for (Map.Entry<String, Path> e : new TreeMap<>(files).entrySet()) {
			String rel = e.getKey();
			Path f = e.getValue();
			Stamp stamp;
			try {
				stamp = new Stamp(Files.size(f), Files.getLastModifiedTime(f).toMillis());
			} catch (IOException ex) {
				continue; // vanished between the listing and now: next scan
			}
			Known old = known.get(rel);
			if (old != null && old.stamp().equals(stamp)) {
				continue;
			}
			if (now - stamp.mtime() < SETTLE_MS && now >= stamp.mtime()) {
				continue; // may still be copying: its stamp is not recorded, so the next scan looks again
			}
			any = true;
			String name = nameOf(rel);
			try {
				Schematic s = Schematic.read(f, name);
				known.put(rel, new Known(stamp, s, null));
				changed.add(name);
			} catch (IOException | RuntimeException ex) {
				String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
				String line = msg.startsWith(f.getFileName().toString()) ? msg : rel + ": " + msg;
				Schematic keep = old == null ? null : old.schematic();
				known.put(rel, new Known(stamp, keep, line));
				errors.add(keep == null ? line : line + " (keeping the last good version)");
			}
		}
		if (!any) {
			return null;
		}
		return new Load(current(), changed, removed, errors);
	}

	/** The candidate files by path under the folder; null when the folder could not be listed. */
	private @Nullable Map<String, Path> list() {
		Map<String, Path> out = new TreeMap<>();
		if (!Files.isDirectory(dir)) {
			return out;
		}
		try (Stream<Path> walk = Files.walk(dir, 2)) {
			walk.filter(Files::isRegularFile).forEach(f -> {
				String rel = dir.relativize(f).toString().replace('\\', '/');
				String lower = rel.toLowerCase(Locale.ROOT);
				if ((lower.endsWith(".nbt") || lower.endsWith(".schem")) && !rel.startsWith("_") && !rel.contains("/_")) {
					out.put(rel, f);
				}
			});
		} catch (IOException | RuntimeException e) {
			return null; // the folder is being changed under us: the next scan sees it
		}
		return out;
	}

	/** {@code sub/house.schem} → {@code sub/house}. */
	static String nameOf(String rel) {
		int dot = rel.lastIndexOf('.');
		return dot > 0 ? rel.substring(0, dot) : rel;
	}

	/** The instructions written into the folder, English first and Spanish after. */
	public static String readmeText() {
		return """
				T3Craft preview drop
				====================

				Drop schematics here and look at them inside the game, without restarting it.

				  Accepted:  .nbt    a vanilla structure block file (Save mode)
				             .schem  a Sponge schematic v2 / v3 (WorldEdit, FAWE, Axiom, most editors)
				  Where:     this folder, or one level of sub-folders (a name starting with _ is ignored)
				  Limits:    sides up to 256 blocks, 1.5 million solid blocks, 64 MB per file
				  Ignored:   entities (only blocks and block entities such as signs and chests come in)

				How to see them
				  1. Open the T3Craft HQ world.
				  2. Walk through the portal arch (fixed in the HQ, at the north end of the inner street, at the foot
				     of the north ramp; it appears once that street is built), or run   /t3craft drop go
				     (come back with /t3craft drop back).
				  3. The showroom is a walled floor with one pedestal and one sign per file (name, size, format).

				Hot reload
				  Add, replace or delete a file while the game runs: within about a second the showroom
				  is updated (only the blocks that changed). A file that cannot be read keeps its last good
				  version and the error appears as a toast and a red chat line (file: what).
				  /t3craft drop reload   rescans now      /t3craft drop rebuild   rebuilds the whole showroom

				Files are only read here, never changed or uploaded.

				--- Español ---
				Suelta aquí esquemáticos (.nbt de bloque de estructura o .schem de Sponge/WorldEdit) y míralos
				dentro del juego sin reiniciar. En el mundo T3Craft HQ cruza el portal (fijo en el HQ, al norte de la
				calle interior, al pie de la rampa norte; aparece cuando esa calle está construida) o usa /t3craft drop go;
				vuelves con /t3craft drop back.
				Añadir, reemplazar o borrar un archivo actualiza el recinto en cerca de un segundo.
				""";
	}
}
