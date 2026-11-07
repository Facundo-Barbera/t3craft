package dev.t3craft.site;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.jspecify.annotations.Nullable;

/**
 * Durable files of the site feature, all under {@code <world>/t3craft/site/}:
 * <ul>
 *   <li>{@code snapshots/<id>.nbt}: the {@link Snapshot} of a site taken before anything was
 *   written there. Never deleted by the mod; moved to {@code archive/} after a successful undo/move.</li>
 *   <li>{@code plans/<id>.nbt}: the {@link CellPlan} applied to that site (to tell the mod's own
 *   blocks from the player's later changes).</li>
 *   <li>{@code state.nbt}: the {@link SiteJournal} (active site, phase).</li>
 * </ul>
 * Every write goes to a temp file, is fsynced, atomically renamed and read back; a snapshot only
 * counts as taken once the read-back decodes to the same content.
 */
public final class SiteStore {
	private static final Pattern ID = Pattern.compile("[a-z0-9-]{1,64}");

	private final Path root;

	public SiteStore(Path worldRoot) {
		this.root = worldRoot.resolve("t3craft").resolve("site");
	}

	public Path root() {
		return root;
	}

	public Path snapshotFile(String id) {
		return root.resolve("snapshots").resolve(checkId(id) + ".nbt");
	}

	public Path planFile(String id) {
		return root.resolve("plans").resolve(checkId(id) + ".nbt");
	}

	public Path journalFile() {
		return root.resolve("state.nbt");
	}

	private static String checkId(String id) {
		if (!ID.matcher(id).matches()) {
			throw new IllegalArgumentException("bad site id " + id);
		}
		return id;
	}

	/**
	 * Writes the snapshot durably and verifies it by reading it back. Refuses to replace an existing
	 * snapshot file.
	 */
	public void saveSnapshot(Snapshot s) throws IOException {
		Path f = snapshotFile(s.id());
		if (Files.exists(f)) {
			throw new IOException("snapshot " + s.id() + " already exists");
		}
		writeDurable(f, s.toTag());
		Snapshot back;
		try {
			back = loadSnapshot(s.id());
		} catch (Snapshot.InvalidSnapshotException e) {
			throw new IOException("snapshot read-back failed: " + e.getMessage(), e);
		}
		if (!back.sameContent(s)) {
			throw new IOException("snapshot read-back differs from what was captured");
		}
	}

	public Snapshot loadSnapshot(String id) throws IOException, Snapshot.InvalidSnapshotException {
		Path f = snapshotFile(id);
		if (!Files.exists(f)) {
			f = root.resolve("archive").resolve(checkId(id) + ".nbt");
		}
		CompoundTag tag = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
		Snapshot s = Snapshot.fromTag(tag);
		if (!s.id().equals(id)) {
			throw new Snapshot.InvalidSnapshotException("snapshot file holds id " + s.id());
		}
		return s;
	}

	public void savePlan(String id, CellPlan plan) throws IOException {
		Path f = planFile(id);
		writeDurable(f, plan.toTag());
		try {
			if (!CellPlan.fromTag(NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap())).sameContent(plan)) {
				throw new IOException("plan read-back differs");
			}
		} catch (Snapshot.InvalidSnapshotException e) {
			throw new IOException("plan read-back failed: " + e.getMessage(), e);
		}
	}

	public CellPlan loadPlan(String id) throws IOException, Snapshot.InvalidSnapshotException {
		return CellPlan.fromTag(NbtIo.readCompressed(planFile(id), NbtAccounter.unlimitedHeap()));
	}

	public void saveJournal(SiteJournal j) throws IOException {
		writeDurable(journalFile(), j.toTag());
	}

	/** The journal, or {@link SiteJournal#EMPTY} when there is none. Unreadable → exception (never guessed). */
	public SiteJournal loadJournal() throws IOException {
		Path f = journalFile();
		if (!Files.exists(f)) {
			return SiteJournal.EMPTY;
		}
		return SiteJournal.fromTag(NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap()));
	}

	/** Moves a finished site's snapshot and plan to {@code archive/} (kept, never deleted). */
	public void archive(String id) throws IOException {
		Path dir = root.resolve("archive");
		Files.createDirectories(dir);
		Path s = snapshotFile(id);
		if (Files.exists(s)) {
			Files.move(s, dir.resolve(id + ".nbt"), StandardCopyOption.REPLACE_EXISTING);
		}
		Path p = planFile(id);
		if (Files.exists(p)) {
			Files.move(p, dir.resolve(id + ".plan.nbt"), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Deletes leftover {@code *.tmp} files of an interrupted write. Returns how many. */
	public int cleanTemp() throws IOException {
		if (!Files.isDirectory(root)) {
			return 0;
		}
		int n = 0;
		try (Stream<Path> files = Files.walk(root, 2)) {
			for (Path p : (Iterable<Path>) files::iterator) {
				if (p.getFileName().toString().endsWith(".tmp")) {
					Files.deleteIfExists(p);
					n++;
				}
			}
		}
		return n;
	}

	static void writeDurable(Path target, CompoundTag tag) throws IOException {
		Files.createDirectories(target.getParent());
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		NbtIo.writeCompressed(tag, bytes);
		Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
		try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
			ByteBuffer buf = ByteBuffer.wrap(bytes.toByteArray());
			while (buf.hasRemaining()) {
				ch.write(buf);
			}
			ch.force(true);
		}
		try {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		}
		fsyncDir(target.getParent());
	}

	private static void fsyncDir(@Nullable Path dir) {
		if (dir == null) {
			return;
		}
		try (FileChannel ch = FileChannel.open(dir, StandardOpenOption.READ)) {
			ch.force(true);
		} catch (IOException | UnsupportedOperationException e) {
			// Not supported on every OS (Windows); the file itself is already fsynced.
		}
	}
}
