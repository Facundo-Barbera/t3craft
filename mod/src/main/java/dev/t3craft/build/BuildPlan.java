package dev.t3craft.build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Immutable, ordered list of {@link Placement}s relative to an origin. A {@link BuildJob} places
 * them in exactly this order; {@link #layered()} returns a copy sorted bottom-up by
 * {@link Layers}.
 *
 * <p>Convention for plans meant to be rotated toward a viewer (as the demo does): the front faces
 * north ({@code -z}), the plan extends toward {@code +z}, and {@code x = 0} is the centre line.
 */
public final class BuildPlan {
	private final List<Placement> placements;
	private final BlockPos min;
	private final BlockPos max;

	private BuildPlan(List<Placement> placements) {
		this.placements = Collections.unmodifiableList(placements);
		int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
		for (Placement p : placements) {
			BlockPos b = p.pos();
			x0 = Math.min(x0, b.getX());
			y0 = Math.min(y0, b.getY());
			z0 = Math.min(z0, b.getZ());
			x1 = Math.max(x1, b.getX());
			y1 = Math.max(y1, b.getY());
			z1 = Math.max(z1, b.getZ());
		}
		if (placements.isEmpty()) {
			this.min = BlockPos.ZERO;
			this.max = BlockPos.ZERO;
		} else {
			this.min = new BlockPos(x0, y0, z0);
			this.max = new BlockPos(x1, y1, z1);
		}
	}

	public static BuildPlan of(List<Placement> placements) {
		return new BuildPlan(new ArrayList<>(placements));
	}

	public static Builder builder() {
		return new Builder();
	}

	public List<Placement> placements() {
		return placements;
	}

	public Placement get(int index) {
		return placements.get(index);
	}

	public int size() {
		return placements.size();
	}

	public boolean isEmpty() {
		return placements.isEmpty();
	}

	/** Smallest relative corner (ZERO for an empty plan). */
	public BlockPos min() {
		return min;
	}

	/** Largest relative corner (ZERO for an empty plan). */
	public BlockPos max() {
		return max;
	}

	/** Copy ordered bottom layer first, snaking inside each layer (stable). */
	public BuildPlan layered() {
		int n = placements.size();
		int[] x = new int[n];
		int[] y = new int[n];
		int[] z = new int[n];
		for (int i = 0; i < n; i++) {
			BlockPos b = placements.get(i).pos();
			x[i] = b.getX();
			y[i] = b.getY();
			z[i] = b.getZ();
		}
		int[] order = Layers.order(x, y, z);
		List<Placement> out = new ArrayList<>(n);
		for (int i : order) {
			out.add(placements.get(i));
		}
		return new BuildPlan(out);
	}

	/**
	 * Builds a plan. Writing the same position twice keeps one entry: the last state, moved to
	 * the end of the order ("fill the wall, then cut the door" works as expected).
	 */
	public static final class Builder {
		private final Map<BlockPos, Placement> entries = new LinkedHashMap<>();

		private Builder() {
		}

		public Builder set(BlockPos pos, BlockState state, @Nullable CompoundTag blockEntity) {
			BlockPos key = pos.immutable();
			entries.remove(key);
			entries.put(key, new Placement(key, state, blockEntity));
			return this;
		}

		public Builder set(BlockPos pos, BlockState state) {
			return set(pos, state, null);
		}

		public Builder set(int x, int y, int z, BlockState state) {
			return set(new BlockPos(x, y, z), state, null);
		}

		/** Fills the inclusive box between two corners (any order), iterating y, then z, then x. */
		public Builder fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
						set(x, y, z, state);
					}
				}
			}
			return this;
		}

		public int size() {
			return entries.size();
		}

		public BuildPlan build() {
			return new BuildPlan(new ArrayList<>(entries.values()));
		}
	}
}
