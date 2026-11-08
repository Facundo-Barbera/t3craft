package dev.t3craft.client.npc.behavior;

import java.util.List;
import net.minecraft.world.level.BlockGetter;

/**
 * fix/crew-route-stutter: {@link BlockShapes} copied out of the level for one box, so an A* can run
 * on a background thread. {@link #capture} reads the level (the client level is not thread-safe:
 * call it on the client thread only); the copy is immutable and safe to read from any thread.
 * Cells outside the box read as air (no ground there, so not walkable).
 */
public final class SnapshotBlocks implements BlockShapes {
	private final int minX;
	private final int minY;
	private final int minZ;
	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final List<Box>[] boxes;
	private final boolean[] liquid;

	@SuppressWarnings("unchecked")
	private SnapshotBlocks(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
		this.minX = minX;
		this.minY = minY;
		this.minZ = minZ;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		int n = sizeX * sizeY * sizeZ;
		this.boxes = (List<Box>[]) new List<?>[n];
		this.liquid = new boolean[n];
	}

	/** Number of cells of the box (min and max inclusive). */
	public static long volume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
	}

	/** Copies the collision boxes and liquids of the box (inclusive) from {@code level}. Client thread only. */
	public static SnapshotBlocks capture(BlockGetter level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		SnapshotBlocks s = new SnapshotBlocks(minX, minY, minZ, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
		LevelBlocks live = new LevelBlocks(level);
		int i = 0;
		for (int x = 0; x < s.sizeX; x++) {
			for (int y = 0; y < s.sizeY; y++) {
				for (int z = 0; z < s.sizeZ; z++, i++) {
					List<Box> b = live.boxes(minX + x, minY + y, minZ + z);
					s.boxes[i] = b.isEmpty() ? List.of() : List.copyOf(b);
					s.liquid[i] = live.liquid(minX + x, minY + y, minZ + z);
				}
			}
		}
		return s;
	}

	private int index(int x, int y, int z) {
		int lx = x - minX;
		int ly = y - minY;
		int lz = z - minZ;
		if (lx < 0 || ly < 0 || lz < 0 || lx >= sizeX || ly >= sizeY || lz >= sizeZ) {
			return -1;
		}
		return (lx * sizeY + ly) * sizeZ + lz;
	}

	@Override
	public List<Box> boxes(int x, int y, int z) {
		int i = index(x, y, z);
		return i < 0 ? List.of() : boxes[i];
	}

	@Override
	public boolean liquid(int x, int y, int z) {
		int i = index(x, y, z);
		return i >= 0 && liquid[i];
	}
}
