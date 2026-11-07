package dev.t3craft.client.npc.behavior;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A fake block world for tests: cells with collision boxes (block-local), like Minecraft's shapes. */
final class CellWorld implements BlockShapes {
	static final List<Box> FULL = List.of(Box.FULL);
	static final List<Box> BOTTOM_SLAB = List.of(new Box(0, 0, 0, 1, 0.5, 1));
	static final List<Box> TOP_SLAB = List.of(new Box(0, 0.5, 0, 1, 1, 1));
	static final List<Box> CARPET = List.of(new Box(0, 0, 0, 1, 0.0625, 1));
	static final List<Box> TOP_TRAPDOOR = List.of(new Box(0, 0.8125, 0, 1, 1, 1));
	/** A fence post on its own (1.5 tall). */
	static final List<Box> FENCE_POST = List.of(new Box(0.375, 0, 0.375, 0.625, 1.5, 0.625));
	/** A fence running along x (post plus arms to both sides). */
	static final List<Box> FENCE_X = List.of(new Box(0, 0, 0.375, 1, 1.5, 0.625));
	/** Stairs ascending towards +x: bottom half plus the back (+x) top quarter. */
	static final List<Box> STAIRS_EAST = List.of(new Box(0, 0, 0, 1, 0.5, 1), new Box(0.5, 0.5, 0, 1, 1, 1));
	/** A closed iron door (not openable by hand) on the -x side of its cell. */
	static final List<Box> IRON_DOOR_WEST = List.of(new Box(0, 0, 0, 0.1875, 1, 1));

	private final Map<Long, List<Box>> cells = new HashMap<>();
	private final Set<Long> liquids = new HashSet<>();

	static long key(int x, int y, int z) {
		return GridPathfinder.pack(x, y, z);
	}

	/** Floor of full blocks at y-1 from (x0,z0) to (x1,z1), so feet stand at {@code y}. */
	CellWorld floor(int x0, int z0, int x1, int z1, int y) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				set(x, y - 1, z, FULL);
			}
		}
		return this;
	}

	CellWorld set(int x, int y, int z, List<Box> boxes) {
		cells.put(key(x, y, z), boxes);
		return this;
	}

	CellWorld clear(int x, int y, int z) {
		cells.remove(key(x, y, z));
		liquids.remove(key(x, y, z));
		return this;
	}

	/** A 2-high wall of full blocks. */
	CellWorld wall(int x, int y, int z) {
		set(x, y, z, FULL);
		return set(x, y + 1, z, FULL);
	}

	CellWorld water(int x, int y, int z) {
		cells.remove(key(x, y, z));
		liquids.add(key(x, y, z));
		return this;
	}

	@Override
	public List<Box> boxes(int x, int y, int z) {
		return cells.getOrDefault(key(x, y, z), List.of());
	}

	@Override
	public boolean liquid(int x, int y, int z) {
		return liquids.contains(key(x, y, z));
	}
}
