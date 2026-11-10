package dev.t3craft.mapgen.islands;

import dev.t3craft.build.Placement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Plans the bridge between two islands: a straight line between their centres, walked one block at a time along
 * its longer axis; only the columns outside both islands get deck. The deck is three blocks wide (spruce in the
 * middle, dark oak on the sides) at the height of the grass on the first island rising or falling in one-block
 * steps to the grass of the other, with a fence post and a plank bracket every third step and a lantern on every
 * second post. Pure and deterministic: the same two islands always give the same bridge.
 */
public final class BridgePlanner {
	/** Half the deck width; the posts stand one block further out. */
	static final int HALF_WIDTH = 1;

	private static final BlockState DECK = Blocks.SPRUCE_PLANKS.defaultBlockState();
	private static final BlockState EDGE = Blocks.DARK_OAK_PLANKS.defaultBlockState();
	private static final BlockState POST = Blocks.SPRUCE_FENCE.defaultBlockState();
	private static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();

	private BridgePlanner() {
	}

	/** The id of the bridge between two islands, whichever order they are given in. */
	public static String idOf(Island x, Island y) {
		return x.id().compareTo(y.id()) <= 0 ? "b:" + x.id() + "|" + y.id() : "b:" + y.id() + "|" + x.id();
	}

	/** The bridge between {@code p} and {@code q}, or null when there is no void to cross between them. */
	public static @Nullable Bridge plan(Island p, Island q) {
		Island a = p.id().compareTo(q.id()) <= 0 ? p : q;
		Island b = a == p ? q : p;
		int dx = b.cx() - a.cx();
		int dz = b.cz() - a.cz();
		int n = Math.max(Math.abs(dx), Math.abs(dz));
		if (n == 0) {
			return null;
		}
		boolean alongX = Math.abs(dx) >= Math.abs(dz);
		int[] xs = new int[n + 1];
		int[] zs = new int[n + 1];
		int first = -1;
		int last = -1;
		for (int i = 0; i <= n; i++) {
			xs[i] = a.cx() + (int) Math.round((double) dx * i / n);
			zs[i] = a.cz() + (int) Math.round((double) dz * i / n);
			if (!a.contains(xs[i], zs[i], 0) && !b.contains(xs[i], zs[i], 0)) {
				if (first < 0) {
					first = i;
				}
				last = i;
			}
		}
		if (first < 0) {
			return null;
		}
		List<Bridge.Step> steps = new ArrayList<>();
		int span = Math.max(1, last - first);
		for (int i = first; i <= last; i++) {
			int y = a.top() + (int) Math.round((double) (b.top() - a.top()) * (i - first) / span);
			List<Placement> blocks = new ArrayList<>();
			for (int k = -HALF_WIDTH; k <= HALF_WIDTH; k++) {
				BlockPos c = lateral(xs[i], y, zs[i], k, alongX);
				if (free(a, b, c)) {
					blocks.add(new Placement(c, k == 0 ? DECK : EDGE));
				}
			}
			if ((i - first) % 3 == 1) {
				for (int side = -1; side <= 1; side += 2) {
					BlockPos bracket = lateral(xs[i], y, zs[i], side * (HALF_WIDTH + 1), alongX);
					if (free(a, b, bracket)) {
						blocks.add(new Placement(bracket, EDGE));
						blocks.add(new Placement(bracket.above(), POST));
						if ((i - first) % 6 == 1) {
							blocks.add(new Placement(bracket.above(2), LANTERN));
						}
					}
				}
			}
			steps.add(new Bridge.Step(new BlockPos(xs[i], y, zs[i]), blocks));
		}
		return new Bridge(idOf(a, b), a.id(), b.id(), steps);
	}

	/** The cell {@code k} blocks to the side of the line at {@code (x, y, z)}: sideways is z when the line runs along x. */
	private static BlockPos lateral(int x, int y, int z, int k, boolean alongX) {
		return alongX ? new BlockPos(x, y, z + k) : new BlockPos(x + k, y, z);
	}

	/** Void: neither island has ground in the column. */
	private static boolean free(Island a, Island b, BlockPos c) {
		return !a.contains(c.getX(), c.getZ(), 0) && !b.contains(c.getX(), c.getZ(), 0);
	}
}
