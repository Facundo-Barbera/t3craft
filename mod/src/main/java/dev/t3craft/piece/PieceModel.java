package dev.t3craft.piece;

import dev.t3craft.hq.Placement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * feat/piece-catalog-preview: the blocks of one catalog piece (or of a whole stage), compact enough for a
 * building of a million blocks: a packed position ({@link BlockPos#asLong}), a state and, for the few cells that
 * have one, block entity data. Cells keep the order they were added in (the order they are placed in); the
 * positions are also kept sorted, so "is there a cell here" ({@link #contains}) is a binary search and no
 * hash map of a million boxed keys is ever built. Immutable.
 *
 * <p>Bounds ({@link #minX()} ...) cover the <b>solid</b> cells only: an air cell is a cell that must be
 * cleared (the sunken floor of a lounge), not part of the shape.
 */
public final class PieceModel {
	private static final PieceModel EMPTY = new PieceModel(new long[0], new BlockState[0], Map.of(), 0, 0, 0, 0, 0, 0, 0);

	private final long[] keys;
	private final BlockState[] states;
	private final Map<Integer, CompoundTag> tags;
	private final long[] sorted;
	private final int solid;
	private final int minX;
	private final int minY;
	private final int minZ;
	private final int maxX;
	private final int maxY;
	private final int maxZ;

	private PieceModel(long[] keys, BlockState[] states, Map<Integer, CompoundTag> tags, int solid, int minX, int minY, int minZ, int maxX,
			int maxY, int maxZ) {
		this.keys = keys;
		this.states = states;
		this.tags = tags;
		this.solid = solid;
		this.minX = minX;
		this.minY = minY;
		this.minZ = minZ;
		this.maxX = maxX;
		this.maxY = maxY;
		this.maxZ = maxZ;
		long[] s = keys.clone();
		Arrays.sort(s);
		this.sorted = s;
	}

	public static PieceModel empty() {
		return EMPTY;
	}

	public static Builder builder() {
		return new Builder();
	}

	/** Cells, air included. */
	public int size() {
		return keys.length;
	}

	/** Cells that are not air. */
	public int solid() {
		return solid;
	}

	public long key(int i) {
		return keys[i];
	}

	public BlockState state(int i) {
		return states[i];
	}

	public @Nullable CompoundTag tag(int i) {
		return tags.get(i);
	}

	/** Cell {@code i} as a placement relative to the stage origin. */
	public Placement placement(int i) {
		return new Placement(BlockPos.of(keys[i]), states[i], tags.get(i));
	}

	/** Whether some cell (air included) is at the packed position {@code key}. */
	public boolean contains(long key) {
		return Arrays.binarySearch(sorted, key) >= 0;
	}

	public boolean hasSolid() {
		return solid > 0;
	}

	public int minX() {
		return minX;
	}

	public int minY() {
		return minY;
	}

	public int minZ() {
		return minZ;
	}

	public int maxX() {
		return maxX;
	}

	public int maxY() {
		return maxY;
	}

	public int maxZ() {
		return maxZ;
	}

	/** Blocks along x (0 when there is no solid cell). */
	public int sizeX() {
		return solid == 0 ? 0 : maxX - minX + 1;
	}

	public int sizeY() {
		return solid == 0 ? 0 : maxY - minY + 1;
	}

	public int sizeZ() {
		return solid == 0 ? 0 : maxZ - minZ + 1;
	}

	/** This model moved by {@code (dx, dy, dz)}; same order, same states. */
	public PieceModel shifted(int dx, int dy, int dz) {
		if (keys.length == 0 || (dx == 0 && dy == 0 && dz == 0)) {
			return this;
		}
		long[] k = new long[keys.length];
		for (int i = 0; i < k.length; i++) {
			k[i] = BlockPos.offset(keys[i], dx, dy, dz);
		}
		return new PieceModel(k, states, tags, solid, minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
	}

	/** Collects cells in placement order. One position must not be added twice. */
	public static final class Builder {
		private long[] keys = new long[1024];
		private BlockState[] states = new BlockState[1024];
		private final Map<Integer, CompoundTag> tags = new HashMap<>();
		private int n;
		private int solid;
		private int minX = Integer.MAX_VALUE;
		private int minY = Integer.MAX_VALUE;
		private int minZ = Integer.MAX_VALUE;
		private int maxX = Integer.MIN_VALUE;
		private int maxY = Integer.MIN_VALUE;
		private int maxZ = Integer.MIN_VALUE;

		private Builder() {
		}

		public Builder add(int x, int y, int z, BlockState state, @Nullable CompoundTag tag) {
			if (n == keys.length) {
				keys = Arrays.copyOf(keys, n * 2);
				states = Arrays.copyOf(states, n * 2);
			}
			keys[n] = BlockPos.asLong(x, y, z);
			states[n] = state;
			if (tag != null) {
				tags.put(n, tag);
			}
			n++;
			if (!state.isAir()) {
				solid++;
				minX = Math.min(minX, x);
				minY = Math.min(minY, y);
				minZ = Math.min(minZ, z);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);
				maxZ = Math.max(maxZ, z);
			}
			return this;
		}

		public Builder add(int x, int y, int z, BlockState state) {
			return add(x, y, z, state, null);
		}

		public int size() {
			return n;
		}

		public PieceModel build() {
			if (n == 0) {
				return EMPTY;
			}
			if (solid == 0) {
				minX = 0;
				minY = 0;
				minZ = 0;
				maxX = 0;
				maxY = 0;
				maxZ = 0;
			}
			return new PieceModel(Arrays.copyOf(keys, n), Arrays.copyOf(states, n), Map.copyOf(tags), solid, minX, minY, minZ, maxX, maxY, maxZ);
		}
	}
}
