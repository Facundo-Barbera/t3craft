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
 * Brazier on a tripod: a meeting point. Three open legs (left, right and back; the front stays open) splay out
 * from a bowl of fire, a flat dais of stone slabs rings the foot, and on a big plot stone benches face the
 * fire. Nothing flammable anywhere: metal, stone and a campfire in a bowl that holds it.
 *
 * <ul>
 * <li><b>1 × 1, 2-wide or 2-deep plots</b>: a one-block column brazier (foot, thin stem, collar, bowl, fire); a
 *     plot 7+ wide and under 3 deep is a pair of columns that flank the way through.</li>
 * <li><b>3 × 3 and up</b>: the tripod. The legs stand under the bowl and lean in with a stair; from 5 × 5 the dais
 *     (dark slabs near the legs, light stone brick outwards) and 2 or 3 stone benches face the fire; 9+ wide
 *     and under 5 deep is a pair of tripods that flank the entrance with the middle free.</li>
 * <li><b>7 × 7 with height 6+</b>: the legs splay out two cells, each carrying the bowl through a short arm of
 *     upside-down slabs 3 blocks up (a walker passes under it), the dais grows to radius 3 and 2 to 4 benches
 *     sit round it, with lanterns on stone posts at the front ends.</li>
 * </ul>
 *
 * <p>By seed: leg material (iron / waxed copper / stone brick), bowl material (polished deepslate / copper), bowl
 * shape (plate with a copper lip / cup with an inward-sloping rim / cage of thin bars) and the fire (a campfire,
 * one in three a soul campfire with soul lanterns).
 *
 * <ul>
 * <li><b>Palette (3–5)</b>: polished blackstone bricks or deepslate bricks at the foot, iron / copper / stone legs,
 *     a polished deepslate or copper bowl, stone bricks for the dais and the benches.</li>
 * <li><b>Safe</b>: the fire stands 4+ blocks up on a full block, with a ring of stone round it; the dais, benches
 *     and posts are stone; there is no wood, wool or leaf within the box.</li>
 * <li><b>Walkable</b>: the dais is slabs (no jump), the cells under the bowl keep 3+ blocks of headroom, the front
 *     lane is open and every bench is a stair a person can step over.</li>
 * <li><b>Supported</b>: foot → stem → head stair → (arm) → bowl is one chain of face contacts.</li>
 * </ul>
 * Size: 1–11 × 1–11 × 5+.
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
		return 5;
	}

	@Override
	public int maxW() {
		return 11;
	}

	@Override
	public int maxD() {
		return 11;
	}

	/** The seeded look of one plot. */
	private record Look(int legs, boolean copperBowl, int shape, boolean soul) {
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
		Look look = new Look(pick(p, 1, 3), pick(p, 2, 3) != 0, pick(p, 3, 3), pick(p, 4, 3) == 0);
		int w = p.w();
		int d = p.d();
		int cv1 = (d - 1) / 2;
		if (w < 3 || d < 3) {
			if (w >= 7 && d < 3) {
				column(c, p, look, 1, cv1);
				column(c, p, look, w - 2, cv1);
			} else {
				column(c, p, look, (w - 1) / 2, cv1);
			}
			return;
		}
		if (w >= 9 && d < 5) {
			tripod(c, p, look, 1, cv1, 1);
			tripod(c, p, look, w - 2, cv1, 1);
			return;
		}
		int cu = (w - 1) / 2;
		int cv = cv1;
		int r = p.h() >= 6 && Math.min(w, d) >= 7 ? 2 : 1;
		tripod(c, p, look, cu, cv, r);
	}

	// ------------------------------------------------------------------ materials

	private static BlockState foot(Plot p, Look k, int u, int v) {
		if (k.legs() == 2) {
			return b(p.hash(u, 0, v, 3) == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.DEEPSLATE_BRICKS);
		}
		return b(p.hash(u, 0, v, 4) == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS : Blocks.POLISHED_BLACKSTONE_BRICKS);
	}

	/** The thin stem of a leg. */
	private static BlockState stem(Look k) {
		return switch (k.legs()) {
			case 0 -> b(Blocks.POLISHED_BLACKSTONE_WALL); // wrought iron
			case 1 -> b(Blocks.CUT_COPPER.waxed().exposed()); // a copper post: bars are too thin to read
			default -> b(Blocks.STONE_BRICK_WALL);
		};
	}

	/** The leaning head: an upside-down stair whose back is towards the bowl (so it faces outwards). */
	private static BlockState head(Look k, Direction outward) {
		Block s = switch (k.legs()) {
			case 0 -> Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS;
			case 1 -> Blocks.CUT_COPPER_STAIRS.waxed().exposed();
			default -> Blocks.STONE_BRICK_STAIRS;
		};
		return stairs(s, outward).setValue(StairBlock.HALF, Half.TOP);
	}

	/** An arm: an upside-down slab, 3 blocks up, so a walker passes under it. */
	private static BlockState arm(Look k) {
		Block s = switch (k.legs()) {
			case 0 -> Blocks.POLISHED_BLACKSTONE_BRICK_SLAB;
			case 1 -> Blocks.CUT_COPPER_SLAB.waxed().exposed();
			default -> Blocks.STONE_BRICK_SLAB;
		};
		return slab(s).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	private static BlockState bowlBlock(Look k) {
		return b(k.copperBowl() ? Blocks.CUT_COPPER.waxed().exposed() : Blocks.POLISHED_DEEPSLATE);
	}

	private static BlockState bowlStairs(Look k, Direction facing, Half half) {
		return stairs(k.copperBowl() ? Blocks.CUT_COPPER_STAIRS.waxed().exposed() : Blocks.POLISHED_DEEPSLATE_STAIRS, facing).setValue(StairBlock.HALF, half);
	}

	private static BlockState bowlSlab(Look k, SlabType t) {
		return slab(k.copperBowl() ? Blocks.CUT_COPPER_SLAB.waxed().exposed() : Blocks.POLISHED_DEEPSLATE_SLAB).setValue(BlockStateProperties.SLAB_TYPE, t);
	}

	private static BlockState fire(Look k) {
		return b(k.soul() ? Blocks.SOUL_CAMPFIRE : Blocks.CAMPFIRE).setValue(BlockStateProperties.LIT, true);
	}

	private static BlockState lantern(Look k) {
		return b(k.soul() ? Blocks.SOUL_LANTERN : Blocks.LANTERN);
	}

	// ------------------------------------------------------------------ column (1-wide plots)

	/** A one-block brazier: dark foot, thin stem, a slab collar, the bowl block and the fire on it. */
	private static void column(CitadelCanvas c, Plot p, Look k, int u, int v) {
		int top = p.h() >= 6 ? 5 : 4;
		p.set(c, u, 0, v, foot(p, k, u, v));
		for (int y = 1; y <= top - 3; y++) {
			p.set(c, u, y, v, stem(k));
		}
		p.set(c, u, top - 2, v, arm(k));
		p.set(c, u, top - 1, v, bowlBlock(k));
		p.set(c, u, top, v, fire(k));
	}

	// ------------------------------------------------------------------ tripod

	/**
	 * The tripod round the centre {@code (cu, cv)}: legs at distance {@code r} (1 or 2) to the left, right and back.
	 * The bowl is {@code D} up (3 or 4), the fire above it.
	 */
	private static void tripod(CitadelCanvas c, Plot p, Look k, int cu, int cv, int r) {
		int dd = Math.min(p.h() - 2, 4);
		if (r == 2) {
			dd = 4;
		}
		int[][] legs = {{-r, 0, 0}, {r, 0, 1}, {0, r, 2}};
		Direction[] out = {Direction.WEST, Direction.EAST, Direction.SOUTH};
		for (int i = 0; i < 3; i++) {
			int lu = cu + legs[i][0];
			int lv = cv + legs[i][1];
			p.set(c, lu, 0, lv, foot(p, k, lu, lv));
			for (int y = 1; y < dd - 1; y++) {
				p.set(c, lu, y, lv, stem(k));
			}
			p.set(c, lu, dd - 1, lv, head(k, out[i]));
			if (r == 2) { // the arm: one upside-down slab between the head and the bowl
				p.set(c, cu + legs[i][0] / 2, dd - 1, cv + legs[i][1] / 2, arm(k));
			}
		}
		bowl(c, p, k, cu, cv, dd);
		dais(c, p, k, cu, cv, r);
		benches(c, p, k, cu, cv, r);
	}

	/** The bowl: a base that tapers (centre block, upside-down stairs, upper corner slabs) and a rim by shape. */
	private static void bowl(CitadelCanvas c, Plot p, Look k, int cu, int cv, int dd) {
		p.set(c, cu, dd, cv, bowlBlock(k));
		Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
		int[][] sides = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
		for (int i = 0; i < 4; i++) {
			// facing outwards, upside down: the low half is towards the centre, so the base steps in to the knot
			p.set(c, cu + sides[i][0], dd, cv + sides[i][1], bowlStairs(k, dirs[i], Half.TOP));
		}
		// the base is a plus (no corners), so the bowl is narrow at the bottom and wide at the rim
		int y = dd + 1;
		for (int i = 0; i < 4; i++) {
			// the rim slopes in: low step towards the fire, tall half outside
			p.set(c, cu + sides[i][0], y, cv + sides[i][1], bowlStairs(k, dirs[i].getOpposite(), Half.BOTTOM));
		}
		switch (k.shape()) {
			case 0 -> { // cup: low slabs close the corners
				for (int su = -1; su <= 1; su += 2) {
					for (int sv = -1; sv <= 1; sv += 2) {
						p.set(c, cu + su, y, cv + sv, bowlSlab(k, SlabType.BOTTOM));
					}
				}
			}
			case 1 -> { // firepit: the corners are stone-brick posts that the rim is tied to with a low wall
				for (int su = -1; su <= 1; su += 2) {
					for (int sv = -1; sv <= 1; sv += 2) {
						p.set(c, cu + su, y, cv + sv, b(Blocks.POLISHED_BLACKSTONE_WALL));
					}
				}
			}
			default -> { // cross: a plus-shaped basin, the corners stay open
			}
		}
		p.set(c, cu, y, cv, fire(k));
	}

	// ------------------------------------------------------------------ ground

	private static boolean isLeg(int dx, int dz, int r) {
		return dx == -r && dz == 0 || dx == r && dz == 0 || dx == 0 && dz == r;
	}

	private static int dais(Plot p, int cu, int cv, int r) {
		int avail = Math.min(Math.min(cu, p.w() - 1 - cu), Math.min(cv, p.d() - 1 - cv));
		return Math.min(r + 1, avail);
	}

	/** A flat dais of slabs round the legs: dark near the foot, light stone bricks outwards, corners chamfered. */
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
				BlockState s;
				int h = p.hash(u, 0, v, 6);
				if (dist == 1) {
					s = slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB); // the one dark ring, round the foot
				} else if (dist == 2) {
					s = slab(h == 0 ? Blocks.COBBLESTONE_SLAB : h == 1 ? Blocks.POLISHED_ANDESITE_SLAB : Blocks.STONE_BRICK_SLAB);
				} else {
					s = slab(h == 0 ? Blocks.MOSSY_STONE_BRICK_SLAB : h <= 2 ? Blocks.STONE_SLAB : Blocks.POLISHED_ANDESITE_SLAB);
				}
				p.set(c, u, 0, v, s);
			}
		}
	}

	// ------------------------------------------------------------------ benches

	private static BlockState seat(Plot p, int u, int v, Direction toFire) {
		return stairs(p.hash(u, 0, v, 4) == 0 ? Blocks.MOSSY_STONE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS, toFire);
	}

	/** A bench of {@code len} seats along an axis, starting at {@code (u0, v0)}; they face the fire. */
	private static void bench(CitadelCanvas c, Plot p, int u0, int v0, boolean alongU, int len, Direction toFire) {
		for (int i = 0; i < len; i++) {
			int u = alongU ? u0 + i : u0;
			int v = alongU ? v0 : v0 + i;
			if (p.has(u, 0, v)) {
				p.set(c, u, 0, v, seat(p, u, v, toFire));
			}
		}
	}

	private static void benches(CitadelCanvas c, Plot p, Look k, int cu, int cv, int r) {
		int rd = dais(p, cu, cv, r);
		if (rd < r + 1) {
			return; // no room for a ring of benches
		}
		int q = r + 1; // bench distance
		int set = pick(p, 5, 3);
		// 0: left + right; 1: left + right + back; 2: big plot left + right + a back pair (4), small plot right + back
		boolean left = !(set == 2 && r == 1);
		boolean right = true;
		boolean back = set == 1 || set == 2 && r == 1;
		boolean backPair = set == 2 && r == 2;
		if (left) {
			bench(c, p, cu - q, cv - 1, false, 3, Direction.EAST);
		}
		if (right) {
			bench(c, p, cu + q, cv - 1, false, 3, Direction.WEST);
		}
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
