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
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Brazier on a tripod: a meeting point. Three open legs carry a bowl of fire, a dais of stone slabs rings the foot and
 * on a big plot stone benches face the fire. Nothing flammable anywhere: metal and stone, and a campfire in a bowl that
 * holds it, framed by a bright copper lip.
 *
 * <ul>
 * <li><b>1 × 1, 2-wide or 2-deep plots</b> (the street lamp sites): a one-block column brazier (dark foot, wall
 *     stem, slab collar, an iron cauldron for the bowl, the fire on it); a plot 7+ wide and under 3 deep is a pair of
 *     columns that flank the way through.</li>
 * <li><b>3 × 3 up to 4 deep, or height 5</b>: a compact tripod, its three legs a triangle (back left, back right, front
 *     centre) under the bowl; from 5 × 5 a dais with 2 or 3 stone benches; 9+ wide and under 5 deep is a pair of
 *     tripods that flank the entrance with the middle free.</li>
 * <li><b>5 × 5 and up with height 6+</b>: the legs splay out two cells (left, right, back; the front stays open) and
 *     carry the bowl through short arms of upside-down slabs 3 blocks up, so a walker passes under them. 5–6 wide: a
 *     dais with 4 stools between the legs. 7+: the dais grows to radius 3 with 2 to 4 benches and lanterns on stone
 *     posts at the front ends; 9+: one more ring and a low stone-brick wall as a backrest behind each bench.</li>
 * </ul>
 *
 * <p>By seed: the metal (iron: polished blackstone; copper; or stone brick; the bowl and the leg heads follow it), the rim
 * (copper lip of stairs that slope in, or of low slabs) and the fire (a campfire, one in three a soul campfire with soul
 * lanterns).
 *
 * <ul>
 * <li><b>Palette (3–5)</b>: polished blackstone bricks or deepslate bricks at the foot, the metal of the legs and bowl,
 *     the bright copper lip, stone bricks and andesite for the dais and benches.</li>
 * <li><b>Safe</b>: the fire stands 4+ blocks up on a full block with a stone ring round it; the dais, benches and posts
 *     are stone; there is no wood, wool or leaf within the box.</li>
 * <li><b>Walkable</b>: the dais is slabs (no jump), the cells under the bowl keep 3+ blocks of headroom, the front lane
 *     is open and every bench is a stair a person can step over.</li>
 * <li><b>Supported</b>: foot → stem → head (→ arm) → bowl is one chain of face contacts.</li>
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
	private record Look(int legs, int shape, boolean soul) {
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
		Look look = new Look(pick(p, 1, 3), pick(p, 3, 2) * 2, pick(p, 4, 3) == 0);
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
		int r = p.h() >= 6 && Math.min(w, d) >= 5 ? 2 : 1;
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
			case 0, 1 -> b(Blocks.POLISHED_BLACKSTONE_WALL); // wrought iron (bars are too thin to read); 1 has copper joints
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

	/** The bowl is the same metal as the heads of the legs (iron: polished blackstone, copper, or stone brick). */
	private static BlockState bowlBlock(Look k) {
		return b(switch (k.legs()) {
			case 0 -> Blocks.POLISHED_BLACKSTONE_BRICKS;
			case 1 -> Blocks.CUT_COPPER.waxed().exposed();
			default -> Blocks.STONE_BRICKS;
		});
	}

	private static BlockState bowlStairs(Look k, Direction facing, Half half) {
		return stairs(switch (k.legs()) {
			case 0 -> Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS;
			case 1 -> Blocks.CUT_COPPER_STAIRS.waxed().exposed();
			default -> Blocks.STONE_BRICK_STAIRS;
		}, facing).setValue(StairBlock.HALF, half);
	}

	private static BlockState bowlSlab(Look k, SlabType t) {
		return slab(switch (k.legs()) {
			case 0 -> Blocks.POLISHED_BLACKSTONE_BRICK_SLAB;
			case 1 -> Blocks.CUT_COPPER_SLAB.waxed().exposed();
			default -> Blocks.STONE_BRICK_SLAB;
		}).setValue(BlockStateProperties.SLAB_TYPE, t);
	}

	/** The bright copper lip round the fire: the same on every tripod, so the flame always has a glowing frame. */
	private static BlockState lipStairs(Direction facing) {
		return stairs(Blocks.CUT_COPPER_STAIRS.waxed().unaffected(), facing);
	}

	private static BlockState lipSlab() {
		return slab(Blocks.CUT_COPPER_SLAB.waxed().unaffected());
	}

	private static BlockState fire(Look k) {
		return b(k.soul() ? Blocks.SOUL_CAMPFIRE : Blocks.CAMPFIRE).setValue(BlockStateProperties.LIT, true);
	}

	private static BlockState lantern(Look k) {
		return b(k.soul() ? Blocks.SOUL_LANTERN : Blocks.LANTERN);
	}

	// ------------------------------------------------------------------ column (1-wide plots)

	/** A one-block brazier: dark foot, thin stem, a slab collar, an iron cauldron for the bowl and the fire on it. */
	private static void column(CitadelCanvas c, Plot p, Look k, int u, int v) {
		int top = p.h() >= 6 ? 5 : 4;
		p.set(c, u, 0, v, foot(p, k, u, v));
		for (int y = 1; y <= top - 3; y++) {
			p.set(c, u, y, v, stem(k));
		}
		p.set(c, u, top - 2, v, arm(k));
		p.set(c, u, top - 1, v, b(Blocks.CAULDRON)); // an iron pot: wider than the stem, a bowl in any light
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
		int[][] legs = legs(r);
		Direction[] out = {Direction.WEST, Direction.EAST, Direction.SOUTH};
		for (int i = 0; i < 3; i++) {
			int lu = cu + legs[i][0];
			int lv = cv + legs[i][1];
			p.set(c, lu, 0, lv, foot(p, k, lu, lv));
			for (int y = 1; y < dd - 1; y++) {
				p.set(c, lu, y, lv, stem(k));
			}
			if (r == 1) {
				// a compact tripod (a triangle under the bowl): the back legs end in a cap slab under a bowl corner,
				// the front leg in a stair that leans in
				boolean front = legs[i][1] < 0;
				p.set(c, lu, dd - 1, lv, front ? head(k, Direction.NORTH) : arm(k));
				if (!front) {
					p.set(c, lu, dd, lv, bowlSlab(k, SlabType.BOTTOM)); // sits on the cap: a low bowl corner
				}
			} else {
				p.set(c, lu, dd - 1, lv, head(k, out[i]));
				// the arm: one upside-down slab between the head and the bowl
				p.set(c, cu + legs[i][0] / 2, dd - 1, cv + legs[i][1] / 2, arm(k));
			}
		}
		bowl(c, p, k, cu, cv, dd);
		dais(c, p, k, cu, cv, r);
		if (r == 2) { // a dark ash bed under the bowl and in front of it (the axis cells under the arms keep the ground: headroom)
			p.set(c, cu, 0, cv, slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB));
			p.set(c, cu, 0, cv - 1, slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB));
		}
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
		// the base is a plus (no corners), so the bowl is narrow at the bottom and wide at the rim; the rim is a bright
		// copper lip, low enough for the flame to stand clear of it
		int y = dd + 1;
		for (int i = 0; i < 4; i++) {
			p.set(c, cu + sides[i][0], y, cv + sides[i][1],
					k.shape() == 0 ? lipStairs(dirs[i].getOpposite()) : lipSlab());
		}
		if (k.shape() == 0) { // cup: low lip slabs close the corners, so the fire sits in a ring that is deeper than it is wide
			for (int su = -1; su <= 1; su += 2) {
				for (int sv = -1; sv <= 1; sv += 2) {
					p.set(c, cu + su, y, cv + sv, lipSlab());
				}
			}
		}
		p.set(c, cu, y, cv, fire(k));
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
		return Math.min(r == 2 ? 4 : r + 1, avail); // a big plot gets one more ring: room for a backrest behind each bench
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
				} else {
					s = slab(h == 0 ? Blocks.POLISHED_ANDESITE_SLAB : Blocks.STONE_BRICK_SLAB); // two light stones only
				}
				p.set(c, u, 0, v, s);
			}
		}
	}

	// ------------------------------------------------------------------ benches

	private static BlockState seat(Plot p, int u, int v, Direction toFire) {
		return stairs(Blocks.DEEPSLATE_BRICK_STAIRS, toFire); // dark seats read against the light dais
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

	/** A low stone-brick wall run behind a bench (its backrest), its posts tied together. */
	private static void rail(CitadelCanvas c, Plot p, int u0, int v0, boolean alongU, int len) {
		for (int i = 0; i < len; i++) {
			int u = alongU ? u0 + i : u0;
			int v = alongU ? v0 : v0 + i;
			if (!p.has(u, 0, v)) {
				continue;
			}
			BlockState s = b(Blocks.STONE_BRICK_WALL);
			if (i > 0) {
				s = s.setValue(alongU ? BlockStateProperties.WEST_WALL : BlockStateProperties.NORTH_WALL, WallSide.LOW);
			}
			if (i < len - 1) {
				s = s.setValue(alongU ? BlockStateProperties.EAST_WALL : BlockStateProperties.SOUTH_WALL, WallSide.LOW);
			}
			p.set(c, u, 0, v, s);
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
					p.set(c, cu + s[0], 0, cv + s[1], seat(p, cu + s[0], cv + s[1], face[s[2]]));
				}
			}
			return;
		}
		if (rd < r + 1) {
			return; // no room for a ring of benches
		}
		boolean backrest = r == 2 && rd >= 4;
		int q = r + 1; // bench distance
		int set = pick(p, 5, 3);
		// 0: left + right; 1: left + right + back; 2: big plot left + right + a back pair (4), small plot right + back
		boolean left = !(set == 2 && r == 1);
		boolean right = true;
		boolean back = set == 1 || set == 2 && r == 1;
		boolean backPair = set == 2 && r == 2;
		if (left) {
			bench(c, p, cu - q, cv - 1, false, 3, Direction.EAST);
			if (backrest) {
				rail(c, p, cu - q - 1, cv - 1, false, 3);
			}
		}
		if (right) {
			bench(c, p, cu + q, cv - 1, false, 3, Direction.WEST);
			if (backrest) {
				rail(c, p, cu + q + 1, cv - 1, false, 3);
			}
		}
		if (back) {
			bench(c, p, cu - 1, cv + q, true, 3, Direction.NORTH);
			if (backrest) {
				rail(c, p, cu - 1, cv + q + 1, true, 3);
			}
		}
		if (backPair) {
			bench(c, p, cu - 2, cv + q, true, 2, Direction.NORTH);
			bench(c, p, cu + 1, cv + q, true, 2, Direction.NORTH);
			if (backrest) {
				rail(c, p, cu - 2, cv + q + 1, true, 2);
				rail(c, p, cu + 1, cv + q + 1, true, 2);
			}
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
