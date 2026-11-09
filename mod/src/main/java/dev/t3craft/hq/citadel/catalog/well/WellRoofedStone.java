package dev.t3craft.hq.citadel.catalog.well;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Village well: a square stone ring (deepslate footing, mossy stone-brick lip, chiseled corner
 * pedestals) under a little gable roof of deepslate tiles with a pink cherry eave line, carried on
 * four cherry posts; a beam across the shaft holds a grindstone pulley, a copper chain and a full
 * cauldron as the bucket. On 5-wide/deep plots a flagstone apron with a step and a barrel rings it.
 *
 * <pre>
 *  section across u (w = 5, d = 5)          plan (w = 5, d = 5)
 *  y7      ▲ rod / slab cap                 v0  . . step . .   apron (slabs, moss, ferns)
 *  y6    ▄███▄  ridge                       v1  . ▣ ▬ ▣ .      ▣ chiseled pedestal + post
 *  y5  ▄█▀ ⊙ ▀█▄  beam + grindstone pulley  v2  . ▬ ≈ ▬ .      ≈ water, ▬ lip slab
 *  y4  ▀  ¦ ▀     roof eaves, chain         v3  . ▣ ▬ ▣ .
 *  y2-3 |  ◘  |   posts, cauldron           v4  ▤ . . . .      ▤ barrel (corner by seed)
 *  y1  ▣▬▬▬▣      lip, pedestals
 *  y0  ▓▓≈▓▓      deepslate ring, water
 * </pre>
 *
 * Size: 3–5 × 3–5 × 7+ (the ridge cap and the copper finial are dropped when the plot is too low).
 */
public final class WellRoofedStone implements Variant {
	private static final BlockState POST = b(Blocks.CHERRY_FENCE);
	private static final BlockState BEAM_X = b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X);
	/** The roof's lowest row (the eaves). */
	private static final int ROOF = 4;

	@Override
	public String id() {
		return "well_roofed_stone";
	}

	@Override
	public Family family() {
		return Family.WELL;
	}

	@Override
	public int minW() {
		return 3;
	}

	@Override
	public int minD() {
		return 3;
	}

	@Override
	public int minH() {
		return 7;
	}

	@Override
	public int maxW() {
		return 5;
	}

	@Override
	public int maxD() {
		return 5;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int mu = w >= 5 ? 1 : 0; // apron margin
		int mv = d >= 5 ? 1 : 0;
		int uL = mu;
		int uR = w - 1 - mu;
		int v0 = mv;
		int v1 = d - 1 - mv;
		int cu = (uL + uR) / 2;
		int cv = (v0 + v1) / 2;

		ring(c, p, uL, uR, v0, v1);
		if (mu + mv > 0) {
			apron(c, p, uL, uR, v0, v1, cu);
		}
		frame(c, p, uL, uR, v0, v1, cu, cv);
		roof(c, p, cu);
	}

	/** Height of the roof surface over row v: one up per row from both eaves. */
	private static int roofY(int v, int d) {
		return ROOF + Math.min(v, d - 1 - v);
	}

	// ------------------------------------------------------------------ ring (y0..y1)

	/** [palette] dark footing: deepslate bricks with cracked and cobbled cells mixed in by hash. */
	private static BlockState footing(Plot p, int u, int v) {
		return switch (p.hash(u, 0, v, 5)) {
			case 0 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
			case 1 -> b(Blocks.COBBLED_DEEPSLATE);
			default -> b(Blocks.DEEPSLATE_BRICKS);
		};
	}

	/** [palette] the lighter lip: stone-brick slabs, a third of them mossy. */
	private static BlockState lip(Plot p, int u, int v) {
		return p.hash(u, 1, v, 3) == 0 ? slab(Blocks.MOSSY_STONE_BRICK_SLAB) : slab(Blocks.STONE_BRICK_SLAB);
	}

	private static void ring(CitadelCanvas c, Plot p, int uL, int uR, int v0, int v1) {
		for (int u = uL; u <= uR; u++) {
			for (int v = v0; v <= v1; v++) {
				boolean edge = u == uL || u == uR || v == v0 || v == v1;
				boolean corner = (u == uL || u == uR) && (v == v0 || v == v1);
				if (!edge) {
					p.set(c, u, 0, v, b(Blocks.WATER)); // the shaft: water one block under the lip
					continue;
				}
				p.set(c, u, 0, v, footing(p, u, v));
				// [depth] corners rise as full chiseled pedestals above the slab lip
				p.set(c, u, 1, v, corner ? b(Blocks.CHISELED_STONE_BRICKS) : lip(p, u, v));
			}
		}
	}

	// ------------------------------------------------------------------ apron (y0, outside the ring)

	/**
	 * [finish] the transition to the ground: a flagstone ring of slabs with moss and ferns growing
	 * through, a step up to the lip at the front, and a barrel in one corner (by seed).
	 */
	private static void apron(CitadelCanvas c, Plot p, int uL, int uR, int v0, int v1, int cu) {
		int w = p.w();
		int d = p.d();
		int barrel = p.hash(0, 0, 0, 4);
		int[][] corners = {{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}};
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				if (u >= uL && u <= uR && v >= v0 && v <= v1) {
					continue;
				}
				BlockState st = switch (p.hash(u, 0, v, 7)) {
					case 0 -> b(Blocks.MOSS_CARPET);
					case 1 -> b(Blocks.FERN);
					case 2 -> slab(Blocks.MOSSY_COBBLESTONE_SLAB);
					default -> slab(Blocks.POLISHED_ANDESITE_SLAB);
				};
				p.set(c, u, 0, v, st);
			}
		}
		if (v0 > 0) {
			p.set(c, cu, 0, 0, stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH)); // the step faces the visitor
		}
		int[] bc = corners[barrel];
		boolean inApron = bc[0] < uL || bc[0] > uR || bc[1] < v0 || bc[1] > v1;
		if (inApron && !(bc[0] == cu && bc[1] == 0)) {
			p.set(c, bc[0], 0, bc[1], b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		}
	}

	// ------------------------------------------------------------------ posts, beam, pulley

	private static void frame(CitadelCanvas c, Plot p, int uL, int uR, int v0, int v1, int cu, int cv) {
		int d = p.d();
		// four cherry posts on the pedestals, up to the roof over their row
		for (int u : new int[] {uL, uR}) {
			for (int v : new int[] {v0, v1}) {
				for (int y = 2; y < roofY(v, d); y++) {
					p.set(c, u, y, v, POST);
				}
			}
		}
		// the beam across the shaft, right under the roof over the middle row; its ends close the gables
		int beamY = roofY(cv, d) - 1;
		for (int u = uL; u <= uR; u++) {
			p.set(c, u, beamY, cv, BEAM_X);
		}
		// [finish] the pulley: a grindstone wheel in the beam (axle along the beam), chain, cauldron bucket
		p.set(c, cu, beamY, cv, b(Blocks.GRINDSTONE)
				.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		BlockState chain = Blocks.COPPER_CHAIN.waxed().unaffected().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
		for (int y = beamY - 1; y > 2; y--) {
			p.put(c, cu, y, cv, chain, CitadelCanvas.HANGING);
		}
		p.put(c, cu, 2, cv, b(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3), CitadelCanvas.HANGING);
		// [finish] lanterns: under the gable overhangs when there is an apron, else under a beam end
		BlockState lantern = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
		if (uL > 0) {
			// on even depths a closing stair sits under the overhang of row cv: hang from that instead
			int ly = roofY(cv, d) - (d % 2 == 0 && cv != 0 ? 2 : 1);
			p.put(c, 0, ly, cv, lantern, CitadelCanvas.HANGING);
			p.put(c, p.w() - 1, ly, cv, lantern, CitadelCanvas.HANGING);
		} else if (cv > v0 && beamY - 1 >= 2) {
			p.put(c, uR, beamY - 1, cv, lantern, CitadelCanvas.HANGING);
		}
	}

	// ------------------------------------------------------------------ roof (y4+)

	/**
	 * [finish] gable roof, ridge along u, over the whole plot: deepslate-tile slopes, a cherry eave
	 * row (the citadel's pink line), a polished ridge with a cherry slab cap and, on odd depths, a
	 * copper finial in the middle. [depth] upside-down stairs close the gable overhangs.
	 */
	private static void roof(CitadelCanvas c, Plot p, int cu) {
		int w = p.w();
		int d = p.d();
		for (int v = 0; v < d; v++) {
			int y = roofY(v, d);
			boolean front = v < d - 1 - v;
			boolean middle = v == d - 1 - v;
			boolean eave = v == 0 || v == d - 1;
			for (int u = 0; u < w; u++) {
				BlockState st = middle ? b(Blocks.POLISHED_DEEPSLATE)
						: stairs(eave ? Blocks.CHERRY_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS, front ? Direction.SOUTH : Direction.NORTH);
				p.set(c, u, y, v, st);
				if (w >= 5 && (u == 0 || u == w - 1) && !middle && !eave && y - 1 >= ROOF) {
					p.set(c, u, y - 1, v, stairs(Blocks.DEEPSLATE_TILE_STAIRS, front ? Direction.NORTH : Direction.SOUTH).setValue(BlockStateProperties.HALF, Half.TOP));
				}
			}
		}
		int top = roofY((d - 1) / 2, d);
		if (top + 1 >= p.h()) {
			return; // no room for the cap: the polished ridge (or the meeting stairs) is the top
		}
		for (int u = 0; u < w; u++) {
			if (d % 2 == 1) {
				boolean finial = u == cu;
				p.set(c, u, top + 1, d / 2, finial ? Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState() : slab(Blocks.CHERRY_SLAB));
			} else {
				p.set(c, u, top + 1, d / 2 - 1, slab(Blocks.CHERRY_SLAB));
				p.set(c, u, top + 1, d / 2, slab(Blocks.CHERRY_SLAB));
			}
		}
	}
}
