package dev.t3craft.site;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

/**
 * Exact copy of every cell of a box: block state (palette of {@link StateKey}s + one palette index
 * per cell, in {@link Box} index order) and full block-entity NBT. Entities (mobs, item frames,
 * armor stands) are not part of it: site selection refuses boxes that contain non-mob entities.
 *
 * <p>NBT form (gzip, {@code <world>/t3craft/site/snapshots/<id>.nbt}):
 * <pre>
 * {format:"t3craft-site-snapshot", version:1, id, dimension, created:long, box:[I;6],
 *  palette:[{Name,Properties}...], cells:[I;...], blockEntities:[{i:int, nbt:{...}}...],
 *  checksum:"sha256 of the canonical content"}
 * </pre>
 */
public final class Snapshot {
	public static final String FORMAT = "t3craft-site-snapshot";
	public static final int VERSION = 2;
	/**
	 * Version 1 snapshots were captured through a block reader broken on 26.x that saw every block as
	 * air: they hold no real terrain and must never be restored.
	 */
	public static final int BROKEN_VERSION = 1;

	private final String id;
	private final String dimension;
	private final long createdMs;
	private final Box box;
	private final List<String> palette;
	private final int[] cells;
	private final SortedMap<Integer, CompoundTag> blockEntities;
	private final String checksum;

	private Snapshot(String id, String dimension, long createdMs, Box box, List<String> palette, int[] cells,
			SortedMap<Integer, CompoundTag> blockEntities) {
		this.id = id;
		this.dimension = dimension;
		this.createdMs = createdMs;
		this.box = box;
		this.palette = List.copyOf(palette);
		this.cells = cells;
		this.blockEntities = Collections.unmodifiableSortedMap(blockEntities);
		this.checksum = computeChecksum();
	}

	public static Snapshot capture(String id, String dimension, long nowMs, BlockWorld world, Box box) {
		if (box.volume() > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("box too large");
		}
		int n = (int) box.volume();
		List<String> palette = new ArrayList<>();
		Map<String, Integer> ids = new HashMap<>();
		int[] cells = new int[n];
		SortedMap<Integer, CompoundTag> bes = new TreeMap<>();
		for (int i = 0; i < n; i++) {
			int x = box.xOf(i);
			int y = box.yOf(i);
			int z = box.zOf(i);
			String s = world.state(x, y, z);
			Integer p = ids.get(s);
			if (p == null) {
				p = palette.size();
				ids.put(s, p);
				palette.add(s);
			}
			cells[i] = p;
			CompoundTag be = world.blockEntity(x, y, z);
			if (be != null) {
				bes.put(i, be.copy());
			}
		}
		return new Snapshot(id, dimension, nowMs, box, palette, cells, bes);
	}

	public String id() {
		return id;
	}

	public String dimension() {
		return dimension;
	}

	public long createdMs() {
		return createdMs;
	}

	public Box box() {
		return box;
	}

	public int size() {
		return cells.length;
	}

	public String state(int index) {
		return palette.get(cells[index]);
	}

	public @Nullable CompoundTag blockEntity(int index) {
		CompoundTag t = blockEntities.get(index);
		return t == null ? null : t.copy();
	}

	public int blockEntityCount() {
		return blockEntities.size();
	}

	public String checksum() {
		return checksum;
	}

	/** Same box and same cell content (states and block entities); ids and timestamps are ignored. */
	public boolean sameContent(Snapshot o) {
		return box.equals(o.box) && checksum.equals(o.checksum);
	}

	private String computeChecksum() {
		StringBuilder sb = new StringBuilder(cells.length * 4);
		sb.append(FORMAT).append('|').append(dimension).append('|');
		for (int v : box.toArray()) {
			sb.append(v).append(',');
		}
		sb.append('|');
		// Resolve the palette per cell so two encodings with a different palette order hash the same.
		Map<String, Integer> firstSeen = new HashMap<>();
		for (int c : cells) {
			String s = palette.get(c);
			Integer k = firstSeen.get(s);
			if (k == null) {
				k = firstSeen.size();
				firstSeen.put(s, k);
				sb.append('#').append(s).append(';');
			}
			sb.append(k).append(',');
		}
		sb.append('|');
		for (Map.Entry<Integer, CompoundTag> e : blockEntities.entrySet()) {
			sb.append(e.getKey()).append('=').append(Canon.of(e.getValue())).append(';');
		}
		return Canon.sha256(sb.toString());
	}

	// ---- NBT ----------------------------------------------------------------------------------

	public CompoundTag toTag() {
		CompoundTag root = new CompoundTag();
		root.putString("format", FORMAT);
		root.putInt("version", VERSION);
		root.putString("id", id);
		root.putString("dimension", dimension);
		root.putLong("created", createdMs);
		root.putIntArray("box", box.toArray());
		ListTag pal = new ListTag();
		for (String s : palette) {
			pal.add(StateKey.toTag(s));
		}
		root.put("palette", pal);
		root.put("cells", new IntArrayTag(cells.clone()));
		ListTag bes = new ListTag();
		for (Map.Entry<Integer, CompoundTag> e : blockEntities.entrySet()) {
			CompoundTag t = new CompoundTag();
			t.putInt("i", e.getKey());
			t.put("nbt", e.getValue().copy());
			bes.add(t);
		}
		root.put("blockEntities", bes);
		root.put("checksum", StringTag.valueOf(checksum));
		return root;
	}

	/** Decodes and validates (format, version, box, palette indices, block-entity indices, checksum). */
	public static Snapshot fromTag(CompoundTag root) throws InvalidSnapshotException {
		if (!FORMAT.equals(root.getStringOr("format", ""))) {
			throw new InvalidSnapshotException("not a t3craft site snapshot");
		}
		int version = root.getIntOr("version", -1);
		if (version == BROKEN_VERSION) {
			throw new BrokenSnapshotException("snapshot " + root.getStringOr("id", "?") + " was taken by a broken t3craft version (every block read as air)"
					+ " and cannot be restored; run /t3craft site resume to discard it");
		}
		if (version != VERSION) {
			throw new InvalidSnapshotException("unsupported snapshot version " + version);
		}
		String id = root.getStringOr("id", "");
		if (id.isEmpty()) {
			throw new InvalidSnapshotException("snapshot without id");
		}
		Box box;
		try {
			box = Box.fromArray(root.getIntArray("box").orElse(null));
		} catch (IllegalArgumentException e) {
			throw new InvalidSnapshotException("bad box: " + e.getMessage());
		}
		if (box.volume() > SiteLimits.MAX_VOLUME) {
			throw new InvalidSnapshotException("box larger than the limit");
		}
		ListTag pal = root.getListOrEmpty("palette");
		List<String> palette = new ArrayList<>(pal.size());
		for (int i = 0; i < pal.size(); i++) {
			CompoundTag p = pal.getCompoundOrEmpty(i);
			if (!p.contains("Name")) {
				throw new InvalidSnapshotException("palette entry " + i + " has no Name");
			}
			palette.add(StateKey.fromTag(p));
		}
		int[] cells = root.getIntArray("cells").orElse(new int[0]);
		if (cells.length != box.volume()) {
			throw new InvalidSnapshotException("expected " + box.volume() + " cells, found " + cells.length);
		}
		for (int c : cells) {
			if (c < 0 || c >= palette.size()) {
				throw new InvalidSnapshotException("cell palette index out of range");
			}
		}
		SortedMap<Integer, CompoundTag> bes = new TreeMap<>();
		ListTag list = root.getListOrEmpty("blockEntities");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompoundOrEmpty(i);
			int idx = t.getIntOr("i", -1);
			Tag nbt = t.get("nbt");
			if (idx < 0 || idx >= cells.length || !(nbt instanceof CompoundTag c)) {
				throw new InvalidSnapshotException("bad block entity entry " + i);
			}
			bes.put(idx, c.copy());
		}
		Snapshot s = new Snapshot(id, root.getStringOr("dimension", ""), root.getLongOr("created", 0L), box, palette, cells, bes);
		String stored = root.getStringOr("checksum", "");
		if (!stored.equals(s.checksum)) {
			throw new InvalidSnapshotException("checksum mismatch (file damaged or edited)");
		}
		return s;
	}

	public static class InvalidSnapshotException extends Exception {
		public InvalidSnapshotException(String message) {
			super(message);
		}
	}

	/** A {@link #BROKEN_VERSION} snapshot: its content is all air, not the area it was taken of. */
	public static final class BrokenSnapshotException extends InvalidSnapshotException {
		public BrokenSnapshotException(String message) {
			super(message);
		}
	}
}
