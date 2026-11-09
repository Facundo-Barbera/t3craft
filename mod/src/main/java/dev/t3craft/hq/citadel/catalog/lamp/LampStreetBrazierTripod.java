package dev.t3craft.hq.citadel.catalog.lamp;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Brazier on a tripod: a meeting point and a street light. A bowl of dark iron with a bright copper rim holds the fire,
 * lifted on a block of netherrack so the flame stands over the rim. It sits on a stone column (1 × 1, the size the city
 * asks for lamps: 1 × 1 × 7) or on three pale stone legs over a pale dais with stools or benches. Nothing flammable
 * anywhere: stone, metal, and a campfire (or a pot of lava) in a bowl that holds it.
 *
 * <ul>
 * <li><b>1-wide or 1-deep plots</b>, the street lamp sites: a column: dark foot, stem of wall, a block of copper for a
 *     capital (wider than the stem, the colour of the rim of the big ones), an iron cauldron for the bowl (full of lava,
 *     or empty under a soul fire) and the fire on it, as tall as the plot allows (7 on the street).</li>
 * <li><b>3 × 3 up to 4 deep, or height 6</b>: a compact tripod, three legs in a triangle (back left, back right, front
 *     centre) under a bowl as wide as the plot; from 5 × 5 a dais with 2 or 3 stone benches.</li>
 * <li><b>5 × 5 and up with height 7+</b>: the legs splay out two cells (left, right, back; the front stays open) and
 *     carry the bowl through short arms of upside-down slabs 4 blocks up, so a walker passes under them. 5–6 wide: a
 *     pale dais with 4 white stools between the legs. 7 wide: the dais grows to radius 3 with 2 to 4 benches and
 *     lanterns on stone posts at the front ends.</li>
 * </ul>
 *
 * <p>By seed: the stone of the legs (stone brick, sandstone, andesite), the rim (corners cut low or full) and the fire
 * (a campfire, one in three a soul campfire on soul soil, in teal copper, with soul lanterns).
 *
 * <ul>
 * <li><b>Palette (3–5)</b>: pale stone for legs and dais, polished blackstone for the bowl (the dark accent), copper
 *     (tan, or teal with a soul fire) for the rim and the capital, deepslate at the foot of the column, quartz for the
 *     seats.</li>
 * <li><b>Safe</b>: the fire stands on netherrack (soul soil) in a bowl of copper and stone; there is no wood, wool or
 *     leaf in the box and no water.</li>
 * <li><b>Walkable</b>: the dais is slabs (no jump), the cells under the bowl keep 3+ blocks of headroom, the front lane
 *     is open and every bench is a stair a person can step over.</li>
 * <li><b>Supported</b>: foot → stem → head (→ arm) → bowl is one chain of face contacts.</li>
 * </ul>
 * Size: 1–7 × 1–7 × 6+. The city only asks for 1 × 1 × 7 (every street lamp site), so bigger plots exist for
 * the gallery and for any plaza that wants a meeting point.
 */
public final class LampStreetBrazierTripod implements Variant {
	@Override
	public String id() {
		return "lamp_street_brazier_tripod";
	}

	@Override
	public Family family() {
		return Family.LAMP;
	}

	@Override
	public int minW() {
		return 1;
	}

	@Override
	public int minD() {
		return 1;
	}

	@Override
	public int minH() {
		return 6;
	}

	@Override
	public int maxW() {
		return 7;
	}

	@Override
	public int maxD() {
		return 7;
	}

	/** The seeded look of one plot. */
	private record Look(int legs, int shape, boolean soul, boolean tealSeed) {
		/** The rim is teal copper under a soul fire and on half of the others, tan on the rest. */
		boolean teal() {
			return tealSeed || soul;
		}
	}

	/** A seeded choice in 0..n-1, one stream per salt. */
	private static int pick(Plot p, int salt, int n) {
		Random r = p.rng(0xB2A71E5L * salt);
		r.nextInt();
		r.nextInt();
		return r.nextInt(n);
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Look look = new Look(pick(p, 1, 3), pick(p, 3, 2) * 2, pick(p, 4, 3) == 0, pick(p, 6, 2) == 0);
		int w = p.w();
		int d = p.d();
		int cu = (w - 1) / 2;
		int cv = (d - 1) / 2;
		if (w < 3 || d < 3) {
			column(c, p, look, cu, cv);
			return;
		}
		int r = p.h() >= 7 && Math.min(w, d) >= 5 ? 2 : 1;
		tripod(c, p, look, cu, cv, r);
	}

	// ------------------------------------------------------------------ materials

	/** The dark foot of the column (the only dark base: grey stone on grey paving needs it). */
	private static BlockState foot(Plot p, int u, int v) {
		return b(p.hash(u, 0, v, 3) == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.DEEPSLATE_BRICKS);
	}

	/** The foot of a tripod leg: pale, with a polished block. */
	private static BlockState legFoot() {
		return b(Blocks.POLISHED_ANDESITE);
	}

	/** The thin stem of a leg: pale stone by seed. */
	private static BlockState stem(Look k) {
		return b(switch (k.legs()) {
			case 0 -> Blocks.STONE_BRICK_WALL;
			case 1 -> Blocks.SANDSTONE_WALL;
			default -> Blocks.ANDESITE_WALL;
		});
	}

	/** The leaning head: an upside-down stair whose back is towards the bowl (so it faces outwards). */
	private static BlockState head(Look k, Direction outward) {
		Block s = switch (k.legs()) {
			case 0 -> Blocks.STONE_BRICK_STAIRS;
			case 1 -> Blocks.SANDSTONE_STAIRS;
			default -> Blocks.ANDESITE_STAIRS;
		};
		return stairs(s, outward).setValue(StairBlock.HALF, Half.TOP);
	}

	/** An arm: an upside-down slab, so a walker passes under it. */
	private static BlockState arm(Look k) {
		Block s = switch (k.legs()) {
			case 0 -> Blocks.STONE_BRICK_SLAB;
			case 1 -> Blocks.SANDSTONE_SLAB;
			default -> Blocks.ANDESITE_SLAB;
		};
		return slab(s).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	/** The bowl is dark iron on every tripod: the one dark accent against the pale legs and the bright rim. */
	private static BlockState bowlBlock() {
		return b(Blocks.POLISHED_BLACKSTONE_BRICKS);
	}

	private static BlockState bowlStairs(Direction facing, Half half) {
		return stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, facing).setValue(StairBlock.HALF, half);
	}

	private static BlockState bowlSlab(SlabType t) {
		return slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB).setValue(BlockStateProperties.SLAB_TYPE, t);
	}

	/** The bright copper rim round the fire: tan, or teal under a soul fire, so the flame always has a glowing frame. */
	private static BlockState rimBlock(Look k) {
		return b(k.teal() ? Blocks.CUT_COPPER.waxed().weathered() : Blocks.CUT_COPPER.waxed().exposed());
	}

	private static BlockState rimStairs(Look k, Direction facing) {
		return stairs(k.teal() ? Blocks.CUT_COPPER_STAIRS.waxed().weathered() : Blocks.CUT_COPPER_STAIRS.waxed().exposed(), facing);
	}

	private static BlockState lipSlab(Look k) {
		return slab(k.teal() ? Blocks.CUT_COPPER_SLAB.waxed().weathered() : Blocks.CUT_COPPER_SLAB.waxed().exposed());
	}

	private static BlockState fire(Look k) {
		return b(k.soul() ? Blocks.SOUL_CAMPFIRE : Blocks.CAMPFIRE).setValue(BlockStateProperties.LIT, true);
	}

	private static BlockState lantern(Look k) {
		return b(k.soul() ? Blocks.SOUL_LANTERN : Blocks.LANTERN);
	}

	// ------------------------------------------------------------------ column (1-wide plots)

	/**
	 * A one-block brazier as tall as the plot (7 on the street): dark foot, stem of wall, a chiselled capital, an iron
	 * cauldron for the bowl (lava in it, or empty under a soul fire) and the fire on it.
	 */
	private static void column(CitadelCanvas c, Plot p, Look k, int u, int v) {
		int top = p.h() - 1;
		p.set(c, u, 0, v, foot(p, u, v));
		for (int y = 1; y <= top - 3; y++) {
			p.set(c, u, y, v, stem(k));
		}
		p.set(c, u, top - 2, v, rimBlock(k)); // the capital: a full block of copper over the thin stem, the colour of the rim of the big ones
		p.set(c, u, top - 1, v, k.soul() ? b(Blocks.CAULDRON) : b(Blocks.LAVA_CAULDRON)); // the bowl
		p.set(c, u, top, v, fire(k));
	}

	// ------------------------------------------------------------------ tripod

	/**
	 * The tripod round the centre {@code (cu, cv)}: legs at distance {@code r} (1 or 2) to the left, right and back.
	 * The bowl is {@code dd} up, the fire above it.
	 */
	private static void tripod(CitadelCanvas c, Plot p, Look k, int cu, int cv, int r) {
		int dd = Math.min(p.h() - 3, 4);
		int[][] legs = legs(r);
		Direction[] out = {Direction.WEST, Direction.EAST, Direction.SOUTH};
		for (int i = 0; i < 3; i++) {
			int lu = cu + legs[i][0];
			int lv = cv + legs[i][1];
			p.set(c, lu, 0, lv, legFoot());
			for (int y = 1; y < dd - 1; y++) {
				p.set(c, lu, y, lv, stem(k));
			}
			if (r == 1) {
				// a compact tripod (a triangle under the bowl): the back legs end in a cap slab under a bowl corner,
				// the front leg in a stair that leans in
				boolean front = legs[i][1] < 0;
				p.set(c, lu, dd - 1, lv, front ? head(k, Direction.NORTH) : arm(k));
				if (!front) {
					p.set(c, lu, dd, lv, bowlSlab(SlabType.BOTTOM)); // sits on the cap: a low bowl corner
				}
			} else {
				p.set(c, lu, dd - 1, lv, head(k, out[i]));
				// the arm: one upside-down slab between the head and the bowl
				p.set(c, cu + legs[i][0] / 2, dd - 1, cv + legs[i][1] / 2, arm(k));
			}
		}
		bowl(c, p, k, cu, cv, dd);
		dais(c, p, k, cu, cv, r);
		benches(c, p, k, cu, cv, r);
	}

	/**
	 * The bowl, a cup: a dark iron base that tapers (centre block, upside-down stairs, no corners) under a ring of bright
	 * copper that is wider than the base: stairs on the sides that slope in, the corners cut low (slabs) or full by seed.
	 * In the middle a block of netherrack (soul soil under a soul fire) lifts the campfire so the flame stands over the
	 * rim, in plain sight, ringed by copper on every side.
	 */
	private static void bowl(CitadelCanvas c, Plot p, Look k, int cu, int cv, int dd) {
		p.set(c, cu, dd, cv, bowlBlock());
		Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
		int[][] sides = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
		for (int i = 0; i < 4; i++) {
			// facing outwards, upside down: the low half is towards the centre, so the base steps in to the knot
			p.set(c, cu + sides[i][0], dd, cv + sides[i][1], bowlStairs(dirs[i], Half.TOP));
			// the rim on the side: a stair whose low step is towards the fire
			p.set(c, cu + sides[i][0], dd + 1, cv + sides[i][1], rimStairs(k, dirs[i].getOpposite()));
		}
		for (int su = -1; su <= 1; su += 2) {
			for (int sv = -1; sv <= 1; sv += 2) {
				p.set(c, cu + su, dd + 1, cv + sv, k.shape() == 0 ? lipSlab(k) : rimBlock(k));
			}
		}
		p.set(c, cu, dd + 1, cv, b(k.soul() ? Blocks.SOUL_SOIL : Blocks.NETHERRACK)); // the fuel
		p.set(c, cu, dd + 2, cv, fire(k));
	}

	// ------------------------------------------------------------------ ground

	/** Leg offsets from the centre: a triangle under the bowl (r 1) or three legs splayed to the left, right and back (r 2). */
	private static int[][] legs(int r) {
		return r == 1 ? new int[][] {{-1, 1}, {1, 1}, {0, -1}} : new int[][] {{-r, 0}, {r, 0}, {0, r}};
	}

	private static boolean isLeg(int dx, int dz, int r) {
		for (int[] l : legs(r)) {
			if (l[0] == dx && l[1] == dz) {
				return true;
			}
		}
		return false;
	}

	private static int dais(Plot p, int cu, int cv, int r) {
		int avail = Math.min(Math.min(cu, p.w() - 1 - cu), Math.min(cv, p.d() - 1 - cv));
		return Math.min(r + 1, avail);
	}

	/** A flat dais of pale slabs round the legs: a dark ring round the foot, light stone outwards, corners chamfered. */
	private static void dais(CitadelCanvas c, Plot p, Look k, int cu, int cv, int r) {
		int rd = dais(p, cu, cv, r);
		for (int dz = -rd; dz <= rd; dz++) {
			for (int dx = -rd; dx <= rd; dx++) {
				int dist = Math.max(Math.abs(dx), Math.abs(dz));
				if (dist == 0 || isLeg(dx, dz, r)) {
					continue;
				}
				boolean corner = Math.abs(dx) == Math.abs(dz);
				if (dist == 1 && !corner) {
					continue; // under the bowl, on the axes: keep the ground, headroom and the way in
				}
				if (rd >= 2 && corner && dist == rd) {
					continue; // chamfered corner: the dais reads round
				}
				int u = cu + dx;
				int v = cv + dz;
				int h = p.hash(u, 0, v, 6);
				BlockState s;
				if (dist == 1) {
					s = slab(Blocks.POLISHED_ANDESITE_SLAB); // a paler ring round the foot
				} else {
					s = slab(h == 0 ? Blocks.POLISHED_ANDESITE_SLAB : h == 1 ? Blocks.SMOOTH_STONE_SLAB : Blocks.STONE_BRICK_SLAB);
				}
				p.set(c, u, 0, v, s);
			}
		}
	}

	// ------------------------------------------------------------------ benches

	private static BlockState seat(Direction toFire) {
		return stairs(Blocks.QUARTZ_STAIRS, toFire); // white seats read against the grey dais
	}

	/** A bench of {@code len} seats along an axis, starting at {@code (u0, v0)}; they face the fire. */
	private static void bench(CitadelCanvas c, Plot p, int u0, int v0, boolean alongU, int len, Direction toFire) {
		for (int i = 0; i < len; i++) {
			int u = alongU ? u0 + i : u0;
			int v = alongU ? v0 : v0 + i;
			if (p.has(u, 0, v)) {
				p.set(c, u, 0, v, seat(toFire));
			}
		}
	}

	private static void benches(CitadelCanvas c, Plot p, Look k, int cu, int cv, int r) {
		int rd = dais(p, cu, cv, r);
		if (r == 2 && rd == 2) {
			// a 5–6 wide plot: stools between the legs (a stair each), 4 by seed
			int set = pick(p, 5, 3);
			int[][] seats = set == 1 ? new int[][] {{-1, 2, 0}, {1, 2, 0}, {-2, -1, 1}, {2, -1, 2}}
					: new int[][] {{-2, -1, 1}, {-2, 1, 1}, {2, -1, 2}, {2, 1, 2}};
			Direction[] face = {Direction.NORTH, Direction.EAST, Direction.WEST};
			for (int[] s : seats) {
				if (p.has(cu + s[0], 0, cv + s[1])) {
					p.set(c, cu + s[0], 0, cv + s[1], seat(face[s[2]]));
				}
			}
			return;
		}
		if (rd < r + 1) {
			return; // no room for a ring of benches
		}
		int q = r + 1; // bench distance
		int set = pick(p, 5, 3);
		// 0: left + right; 1: left + right + back; 2: big plot left + right + a back pair (4), small plot right + back
		boolean left = !(set == 2 && r == 1);
		boolean back = set == 1 || set == 2 && r == 1;
		boolean backPair = set == 2 && r == 2;
		if (left) {
			bench(c, p, cu - q, cv - 1, false, 3, Direction.EAST);
		}
		bench(c, p, cu + q, cv - 1, false, 3, Direction.WEST);
		if (back) {
			bench(c, p, cu - 1, cv + q, true, 3, Direction.NORTH);
		}
		if (backPair) {
			bench(c, p, cu - 2, cv + q, true, 2, Direction.NORTH);
			bench(c, p, cu + 1, cv + q, true, 2, Direction.NORTH);
		}
		if (r == 2) { // lantern posts at the front ends of the side benches: they light the way in
			for (int su = -1; su <= 1; su += 2) {
				int u = cu + su * q;
				int v = cv - 2;
				p.set(c, u, 0, v, b(Blocks.STONE_BRICK_WALL));
				p.set(c, u, 1, v, b(Blocks.STONE_BRICK_WALL));
				p.set(c, u, 2, v, lantern(k));
			}
		}
	}
}
