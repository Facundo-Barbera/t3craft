package dev.t3craft.hq.citadel.catalog.annex;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Cherry pergola: cherry log posts on deepslate feet carry stripped-cherry beams with carved
 * brackets, fence rafters across them and a ragged canopy of pink cherry leaves with vines hanging
 * through. Two cherry benches face each other along the sides over a flagstone floor strewn with
 * petals, a little table with a lantern above it in the middle, and potted flowers at the corners.
 *
 * <pre>
 *  front (w = 6)                    plan (w = 6, d = 6)
 *  y5   ♣♣ ♣♣♣    loose canopy      v0  ╋ ✿ ▬ ▬ ✿ ╋   posts, pots at the entrance
 *  y4  ♣═══════♣  rafters, leaves   v1  ◣ ▬ ❀ ▬ ▬ ◢   benches ◣◢ (backs outwards)
 *  y3  ═ ¦  o ¦═  beams, vines, lamp v2  ◣ ▬ ┬ ┬ ▬ ◢   table ┬
 *  y2  ║◥    ◤║  posts, brackets    v3  ◣ ▬ ┬ ┬ ▬ ◢
 *  y1  ║§    §║  vines on posts     v4  ◣ ▬ ▬ ❀ ▬ ◢   ❀ petals / moss between slabs
 *  y0  ▓▬▬▬▬▬▓   deepslate feet     v5  ╋ ✿ ▬ ▬ ✿ ╋
 * </pre>
 *
 * Size: 5–8 × 4–8 × 5+ (the loose top layer of leaves from height 6; a middle pair of posts from depth 7).
 */
public final class AnnexCherryPergola implements Variant {
	private static final int BEAM = 3;
	private static final int RAFTER = 4;

	private static final BlockState LOG = b(Blocks.CHERRY_LOG);
	private static final BlockState BEAM_Z = b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
	private static final BlockState LEAVES = leaves(Blocks.CHERRY_LEAVES);
	private static final BlockState AZALEA_LEAVES = leaves(Blocks.FLOWERING_AZALEA_LEAVES);

	@Override
	public String id() {
		return "annex_cherry_pergola";
	}

	@Override
	public Family family() {
		return Family.ANNEX;
	}

	@Override
	public int minW() {
		return 5;
	}

	@Override
	public int minD() {
		return 4;
	}

	@Override
	public int minH() {
		return 5;
	}

	@Override
	public int maxW() {
		return 8;
	}

	@Override
	public int maxD() {
		return 8;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int mid = d >= 7 ? (d - 1) / 2 : -1; // a middle pair of posts on long pergolas
		int tv = d % 2 == 1 ? (d - 1) / 2 : d / 2; // table row (and d/2 - 1 too on even depths)
		int cu = w / 2; // table column (and cu - 1 too on even widths)

		floor(c, p);
		posts(c, p, mid);
		benches(c, p, mid);
		table(c, p, cu, tv);
		beams(c, p, mid);
		canopy(c, p, tv);
		vines(c, p, mid, tv);
		p.put(c, cu, BEAM, tv, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
	}

	private static boolean postRow(int v, int d, int mid) {
		return v == 0 || v == d - 1 || v == mid;
	}

	/** Rafters run over the table row and every second row from it, plus both ends. */
	private static boolean rafterRow(int v, int d, int tv) {
		return v == 0 || v == d - 1 || Math.floorMod(v - tv, 2) == 0;
	}

	// ------------------------------------------------------------------ floor (y0)

	/**
	 * [palette] flagstones: polished andesite, stone-brick and mossy stone-brick slabs mixed by hash,
	 * with pink petals and moss between them ([finish] the floor melts into the garden instead of
	 * ending in a hard edge); potted flowers inside the four corners.
	 */
	private static void floor(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		Direction[] petalsFacing = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
		for (int u = 1; u < w - 1; u++) {
			for (int v = 0; v < d; v++) {
				BlockState st = switch (p.hash(u, 0, v, 9)) {
					case 0 -> b(Blocks.PINK_PETALS).setValue(BlockStateProperties.FLOWER_AMOUNT, 3)
							.setValue(BlockStateProperties.HORIZONTAL_FACING, petalsFacing[p.hash(u, 1, v, 4)]);
					case 1 -> b(Blocks.MOSS_CARPET);
					case 2, 3 -> slab(Blocks.MOSSY_STONE_BRICK_SLAB);
					case 4, 5 -> slab(Blocks.STONE_BRICK_SLAB);
					default -> slab(Blocks.POLISHED_ANDESITE_SLAB);
				};
				p.set(c, u, 0, v, st);
			}
		}
		for (int u : new int[] {1, w - 2}) {
			for (int v : new int[] {0, d - 1}) {
				p.set(c, u, 0, v, p.hash(u, 0, v, 2) == 0 ? b(Blocks.POTTED_PINK_TULIP) : b(Blocks.POTTED_AZALEA));
			}
		}
	}

	// ------------------------------------------------------------------ posts and benches

	/** [palette] dark → light: deepslate feet (polished, chiseled by hash), cherry log posts. */
	private static void posts(CitadelCanvas c, Plot p, int mid) {
		int w = p.w();
		int d = p.d();
		for (int u : new int[] {0, w - 1}) {
			for (int v = 0; v < d; v++) {
				if (!postRow(v, d, mid)) {
					continue;
				}
				p.set(c, u, 0, v, p.hash(u, 0, v, 3) == 0 ? b(Blocks.CHISELED_DEEPSLATE) : b(Blocks.POLISHED_DEEPSLATE));
				p.set(c, u, 1, v, LOG);
				p.set(c, u, 2, v, LOG);
			}
		}
	}

	/** Benches of cherry stairs along both sides between the posts, backs outwards, facing each other. */
	private static void benches(CitadelCanvas c, Plot p, int mid) {
		int w = p.w();
		int d = p.d();
		for (int v = 1; v < d - 1; v++) {
			if (v == mid) {
				continue;
			}
			p.set(c, 0, 0, v, stairs(Blocks.CHERRY_STAIRS, Direction.WEST));
			p.set(c, w - 1, 0, v, stairs(Blocks.CHERRY_STAIRS, Direction.EAST));
		}
	}

	/**
	 * A little table in the middle: cherry fence legs joined to each other, closed cherry trapdoors
	 * as the top (one cell, or 2 × 2 on even sizes).
	 */
	private static void table(CitadelCanvas c, Plot p, int cu, int tv) {
		int w = p.w();
		int d = p.d();
		int ua = w % 2 == 0 ? cu - 1 : cu;
		int va = d % 2 == 0 ? tv - 1 : tv;
		for (int u = ua; u <= cu; u++) {
			for (int v = va; v <= tv; v++) {
				BlockState leg = b(Blocks.CHERRY_FENCE)
						.setValue(BlockStateProperties.WEST, u > ua)
						.setValue(BlockStateProperties.EAST, u < cu)
						.setValue(BlockStateProperties.NORTH, v > va)
						.setValue(BlockStateProperties.SOUTH, v < tv);
				p.set(c, u, 0, v, leg);
				p.set(c, u, 1, v, b(Blocks.CHERRY_TRAPDOOR).setValue(BlockStateProperties.HALF, Half.BOTTOM));
			}
		}
	}

	// ------------------------------------------------------------------ beams and rafters (y2..y4)

	/**
	 * [depth] stripped-cherry beams along both sides on the posts, upside-down stair brackets under
	 * them beside every post, and cherry fence rafters across (joined end to end).
	 */
	private static void beams(CitadelCanvas c, Plot p, int mid) {
		int w = p.w();
		int d = p.d();
		for (int u : new int[] {0, w - 1}) {
			for (int v = 0; v < d; v++) {
				p.set(c, u, BEAM, v, BEAM_Z);
				if (postRow(v, d, mid)) {
					continue;
				}
				if (postRow(v - 1, d, mid)) {
					p.set(c, u, 2, v, stairs(Blocks.CHERRY_STAIRS, Direction.NORTH).setValue(BlockStateProperties.HALF, Half.TOP));
				} else if (postRow(v + 1, d, mid)) {
					p.set(c, u, 2, v, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH).setValue(BlockStateProperties.HALF, Half.TOP));
				}
			}
		}
	}

	/**
	 * [finish] the roof is alive: fence rafters on every other row, cherry leaves (a few flowering
	 * azalea tufts mixed in) filling the rows between, and from height 6 a loose second layer so the
	 * top edge is ragged, not a slab of leaves.
	 */
	private static void canopy(CitadelCanvas c, Plot p, int tv) {
		int w = p.w();
		int d = p.d();
		for (int v = 0; v < d; v++) {
			boolean rafter = rafterRow(v, d, tv);
			for (int u = 0; u < w; u++) {
				if (rafter) {
					p.set(c, u, RAFTER, v, b(Blocks.CHERRY_FENCE)
							.setValue(BlockStateProperties.WEST, u > 0)
							.setValue(BlockStateProperties.EAST, u < w - 1));
				} else if (p.hash(u, RAFTER, v, 4) != 0) {
					p.set(c, u, RAFTER, v, p.hash(u, RAFTER, v, 7) == 0 ? AZALEA_LEAVES : LEAVES);
				}
				if (p.h() > RAFTER + 1) {
					boolean edge = u == 0 || u == w - 1 || v == 0 || v == d - 1;
					if (p.hash(u, RAFTER + 1, v, edge ? 3 : 2) == 0) {
						p.set(c, u, RAFTER + 1, v, LEAVES);
					}
				}
			}
		}
	}

	/**
	 * Vines: climbing the inner face of some posts, draping from the inner face of the beams, and
	 * hanging single from the leaves between the rafters. Placement by hash.
	 */
	private static void vines(CitadelCanvas c, Plot p, int mid, int tv) {
		int w = p.w();
		int d = p.d();
		BlockState vine = b(Blocks.VINE);
		for (int v = 0; v < d; v++) {
			boolean post = postRow(v, d, mid);
			// left side: the vine is in u = 1 and clings to its west face; right side mirrored
			for (int side = 0; side < 2; side++) {
				int u = side == 0 ? 1 : w - 2;
				BlockState cling = vine.setValue(side == 0 ? BlockStateProperties.WEST : BlockStateProperties.EAST, true);
				if (post && p.hash(side, 1, v, 2) == 0) {
					p.set(c, u, 1, v, cling);
					p.set(c, u, 2, v, cling);
				} else if (!post && p.hash(side, BEAM, v, 3) == 0) {
					p.set(c, u, BEAM, v, cling);
				}
			}
			// hanging from leaves overhead (only where the cell above really is leaves)
			if (rafterRow(v, d, tv)) {
				continue;
			}
			for (int u = 2; u < w - 2; u++) {
				if (p.hash(u, RAFTER, v, 4) != 0 && p.hash(u, BEAM, v, 3) == 0) {
					p.set(c, u, BEAM, v, vine.setValue(BlockStateProperties.UP, true));
				}
			}
		}
	}
}
