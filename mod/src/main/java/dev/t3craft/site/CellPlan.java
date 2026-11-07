package dev.t3craft.site;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import org.jspecify.annotations.Nullable;

/**
 * Desired state of the cells of a box: a {@link StateKey} per cell, or {@code null} = leave that cell
 * as it is. Plans never contain block entities. This is the minimal hand-off format between the
 * site (leveling + where) and whatever builds (instant here; animated builders in {@code feat/builders}).
 * Cells are listed in {@link Box} index order, i.e. bottom layer first.
 */
public final class CellPlan {
	private final Box box;
	private final @Nullable String[] cells;
	private final Origin origin;

	/** Structure anchor handed to the builder: the pad's centre cell one above the pad, and the front. */
	public record Origin(int x, int y, int z, Facing facing) {
	}

	/** Side the base's entrance faces. */
	public enum Facing {
		NORTH, SOUTH, EAST, WEST;

		public static Facing towards(int dx, int dz) {
			if (Math.abs(dx) >= Math.abs(dz)) {
				return dx >= 0 ? EAST : WEST;
			}
			return dz >= 0 ? SOUTH : NORTH;
		}
	}

	public CellPlan(Box box, Origin origin) {
		if (box.volume() > SiteLimits.MAX_VOLUME) {
			throw new IllegalArgumentException("plan box larger than the limit: " + box.volume());
		}
		this.box = box;
		this.origin = origin;
		this.cells = new String[(int) box.volume()];
	}

	public Box box() {
		return box;
	}

	public Origin origin() {
		return origin;
	}

	public int size() {
		return cells.length;
	}

	public @Nullable String get(int index) {
		return cells[index];
	}

	public @Nullable String get(int x, int y, int z) {
		return box.contains(x, y, z) ? cells[box.index(x, y, z)] : null;
	}

	/** Sets a cell; positions outside the box are ignored (a plan never writes outside its box). */
	public void set(int x, int y, int z, @Nullable String state) {
		if (box.contains(x, y, z)) {
			cells[box.index(x, y, z)] = state;
		}
	}

	/** Overlays {@code other} (its non-null cells win), clipped to this box. */
	public void overlay(CellPlan other) {
		Box o = other.box;
		for (int i = 0; i < other.cells.length; i++) {
			String s = other.cells[i];
			if (s != null) {
				set(o.xOf(i), o.yOf(i), o.zOf(i), s);
			}
		}
	}

	/** Number of cells the plan sets. */
	public int count() {
		int n = 0;
		for (String s : cells) {
			if (s != null) {
				n++;
			}
		}
		return n;
	}

	public boolean sameContent(CellPlan o) {
		return box.equals(o.box) && origin.equals(o.origin) && java.util.Arrays.equals(cells, o.cells);
	}

	public CompoundTag toTag() {
		CompoundTag root = new CompoundTag();
		root.putString("format", "t3craft-site-plan");
		root.putInt("version", 1);
		root.putIntArray("box", box.toArray());
		root.putIntArray("origin", new int[] {origin.x(), origin.y(), origin.z()});
		root.putString("facing", origin.facing().name());
		List<String> palette = new ArrayList<>();
		Map<String, Integer> ids = new HashMap<>();
		int[] idx = new int[cells.length];
		for (int i = 0; i < cells.length; i++) {
			String s = cells[i];
			if (s == null) {
				idx[i] = -1;
				continue;
			}
			idx[i] = ids.computeIfAbsent(s, k -> {
				palette.add(k);
				return palette.size() - 1;
			});
		}
		ListTag pal = new ListTag();
		palette.forEach(s -> pal.add(StateKey.toTag(s)));
		root.put("palette", pal);
		root.put("cells", new IntArrayTag(idx));
		return root;
	}

	public static CellPlan fromTag(CompoundTag root) throws Snapshot.InvalidSnapshotException {
		if (!"t3craft-site-plan".equals(root.getStringOr("format", "")) || root.getIntOr("version", -1) != 1) {
			throw new Snapshot.InvalidSnapshotException("not a t3craft site plan (v1)");
		}
		try {
			Box box = Box.fromArray(root.getIntArray("box").orElse(null));
			int[] o = root.getIntArray("origin").orElse(new int[0]);
			if (o.length != 3) {
				throw new Snapshot.InvalidSnapshotException("bad origin");
			}
			CellPlan plan = new CellPlan(box, new Origin(o[0], o[1], o[2], Facing.valueOf(root.getStringOr("facing", "SOUTH"))));
			ListTag pal = root.getListOrEmpty("palette");
			String[] palette = new String[pal.size()];
			for (int i = 0; i < palette.length; i++) {
				palette[i] = StateKey.fromTag(pal.getCompoundOrEmpty(i));
			}
			int[] idx = root.getIntArray("cells").orElse(new int[0]);
			if (idx.length != plan.cells.length) {
				throw new Snapshot.InvalidSnapshotException("plan cell count mismatch");
			}
			for (int i = 0; i < idx.length; i++) {
				if (idx[i] >= palette.length || idx[i] < -1) {
					throw new Snapshot.InvalidSnapshotException("plan palette index out of range");
				}
				plan.cells[i] = idx[i] < 0 ? null : palette[idx[i]];
			}
			return plan;
		} catch (IllegalArgumentException e) {
			throw new Snapshot.InvalidSnapshotException("bad plan: " + e.getMessage());
		}
	}
}
