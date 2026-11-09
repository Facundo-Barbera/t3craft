package dev.t3craft.hq.citadel.catalog.gate;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A neighbourhood torii sized to its avenue: two thick pillars (hashira) on dark stone feet (kamebara) with
 * a brick collar and a terracotta binding under the tie beam (nuki), wedges (kusabi), a dark plaque with a lit
 * name panel (gakuzuka), a red under-beam (shimaki) and a kasagi that reads as one continuous curve. The span
 * between the pillars is the whole roadway: nothing stands in the path under the gate.
 *
 * <ul>
 * <li><b>Kasagi (sorizashi)</b>: cherry planks under a deepslate-tile cap whose top surface rises smoothly
 *     towards the tips in half-block steps (full blocks and bottom slabs, a tile stair for the very tip): no
 *     teeth, no gaps. The underside lifts at the tips and the kasagi is deeper than the pillars, so it casts an
 *     eave. A stripped-log band follows the curve under the cap. Two profiles: a gentle parabola and a flatter
 *     one with sharper tips.</li>
 * <li><b>Styles by seed</b>: <i>myōjin</i> (curved kasagi, nuki poking out past the pillars, shimaki and a
 *     red upper tie beam over the plaque: a double beam) most of the time; <i>shinmei</i> (rare): a straight,
 *     flush kasagi on round stripped-stem pillars.</li>
 * <li><b>Function</b>: stone toro or lamp posts, hanging lanterns under the nuki, a lit panel facing both
 *     ways (the catalog cannot write sign text, so it is the blank name plate of the neighbourhood), and on
 *     both sides a waiting bench with a rail back, outside the roadway. With depth, a second, smaller torii
 *     stands in front of the road (ni-no-torii).</li>
 * <li><b>Palette</b>: black polished stone feet → brick collar and red lacquer shaft with a terracotta binding →
 *     red beams → cherry kasagi → dark tile cap; stone-brick lanterns, warm lit panel.</li>
 * <li><b>Walk audit</b>: the roadway between the pillars is free; benches and lamps stand on the end rows,
 *     outside the plane of the gate.</li>
 * </ul>
 * Size: 7–19 × 2–8 × 8+ (uses up to 16 blocks of height; the city asks for 15–19 × 4–8 × 14).
 */
public final class GateToriiCherry implements Variant {
	private static final BlockState RED = b(Blocks.CONCRETE.pick(DyeColor.RED));
	private static final BlockState BAND = b(Blocks.DYED_TERRACOTTA.pick(DyeColor.RED));
	private static final BlockState BRICK = b(Blocks.RED_NETHER_BRICKS);
	private static final BlockState LANTERN = b(Blocks.LANTERN);
	private static final BlockState CHERRY = b(Blocks.CHERRY_PLANKS);
	private static final BlockState TILE = b(Blocks.DEEPSLATE_TILES);
	private static final BlockState STEM = b(Blocks.STRIPPED_CRIMSON_STEM);

	@Override
	public String id() {
		return "gate_torii_cherry";
	}

	@Override
	public Family family() {
		return Family.GATE;
	}

	@Override
	public int minW() {
		return 7; // narrower, the kasagi cannot overhang the pillars and the nuki cannot pass through: a goalpost
	}

	@Override
	public int minD() {
		return 2;
	}

	@Override
	public int minH() {
		return 8;
	}

	@Override
	public int maxW() {
		return 19;
	}

	@Override
	public int maxD() {
		return 8;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int h = p.h();
		int kind = p.hash(0, 0, 0, 6);
		boolean shinmei = kind == 0; // one in six: the straight style with round pillars
		boolean sharp = kind >= 4; // the flatter kasagi with sharper tips
		int lampStyle = p.hash(1, 0, 0, 2);

		// --- plan: pillars at the sides, the plane of the gate 2 deep in the middle of the depth
		int pw = w >= 15 ? 2 : 1;
		int ov = w >= 21 ? 2 : 1; // kasagi / nuki overhang past the pillars, in u
		int l0 = ov;
		int l1 = ov + pw - 1;
		int r0 = w - ov - pw;
		int r1 = w - ov - 1;
		int ou0 = l1 + 1; // the opening runs ou0..ou1
		int ou1 = r0 - 1;
		int ow = ou1 - ou0 + 1;
		int v0 = (d - 2) / 2;
		int v1 = Math.min(d - 1, v0 + 1);
		boolean second = d >= 6 && ow >= 9 && p.hash(2, 0, 0, 2) == 0;

		// --- elevation: cap row c0 (a tall myōjin's tips climb two rows over it), body rows, shimaki, plaque rows, nuki
		int top = Math.min(h, 16) - 1;
		boolean tall = top >= 10;
		int c0 = !tall ? top : shinmei ? top - 1 : top - 2;
		int body = !tall ? (top >= 9 ? 1 : 0) : top >= 12 ? 2 : 1;
		int shim = c0 - body - 1;
		int gapRows = top >= 13 ? 2 : 1;
		int gapTop = shim - 1;
		int gapBot = gapTop - gapRows + 1;
		int nuki = gapBot - 1;
		int rise = shinmei ? 0 : Math.min(5, 2 * (top - c0 + 1) - 1); // half blocks the tips climb over the cap line

		// --- hashira on kamebara: two dark blocks of foot, a brick collar, the shaft with a binding under the nuki
		int bandY = nuki - 1 >= 4 ? nuki - 1 : -1;
		for (int[] cols : new int[][] {{l0, l1}, {r0, r1}}) {
			for (int u = cols[0]; u <= cols[1]; u++) {
				for (int v = v0; v <= v1; v++) {
					p.set(c, u, 0, v, b(p.hash(u, 0, v, 3) == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE));
					p.set(c, u, 1, v, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
					for (int y = 2; y <= gapTop; y++) {
						BlockState s = shinmei ? STEM : RED;
						if (y == 2 || y == gapTop) {
							s = BRICK; // collar over the foot, and a ring under the shimaki
						} else if (y == bandY) {
							s = shinmei ? BRICK : BAND; // the binding under the nuki, darker red, still red
						}
						p.set(c, u, y, v, s);
					}
				}
				// the stair collar of the foot, front and back
				if (v0 - 1 >= 0) {
					p.set(c, u, 0, v0 - 1, stairs(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.SOUTH));
				}
				if (v1 + 1 <= d - 1) {
					p.set(c, u, 0, v1 + 1, stairs(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.NORTH));
				}
			}
		}
		for (int v = v0; v <= v1; v++) { // and on the outer sides
			if (l0 - 1 >= 0) {
				p.set(c, l0 - 1, 0, v, stairs(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.EAST));
			}
			if (r1 + 1 <= w - 1) {
				p.set(c, r1 + 1, 0, v, stairs(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.WEST));
			}
		}

		// --- nuki: runs through both pillars and, on a myōjin, sticks out one block past each
		int n0 = shinmei ? l0 : Math.max(0, l0 - 1);
		int n1 = shinmei ? r1 : Math.min(w - 1, r1 + 1);
		for (int u = n0; u <= n1; u++) {
			if ((u >= l0 && u <= l1) || (u >= r0 && u <= r1)) {
				continue; // the pillar is already there
			}
			for (int v = v0; v <= v1; v++) {
				p.set(c, u, nuki, v, RED);
			}
		}

		// --- gakuzuka: a dark frame with a lit name panel (both faces), one or two rows tall
		int plW = ow >= 11 ? 7 : ow >= 9 ? 5 : ow >= 7 ? 3 : 1;
		if (w % 2 == 0) {
			plW++;
		}
		int ps = (w - plW) / 2;
		BlockState lit = b(p.hash(5, 0, 0, 2) == 0 ? Blocks.OCHRE_FROGLIGHT : Blocks.SHROOMLIGHT);
		for (int u = ps; u < ps + plW; u++) {
			boolean frame = plW < 3 || u == ps || u == ps + plW - 1;
			for (int y = gapBot; y <= gapTop; y++) {
				for (int v = v0; v <= v1; v++) {
					p.set(c, u, y, v, frame ? b(Blocks.CHISELED_POLISHED_BLACKSTONE) : lit);
				}
			}
		}

		// a two-row plaque sits in a red beam: the upper plaque row is closed everywhere else (between the pillars and
		// out to the ends of the nuki), so only one free cell is left over the nuki and nobody can stand on it
		if (gapRows >= 2) {
			for (int u = n0; u <= n1; u++) {
				boolean pillar = (u >= l0 && u <= l1) || (u >= r0 && u <= r1);
				boolean plaque = u >= ps && u < ps + plW;
				if (pillar || plaque) {
					continue;
				}
				for (int v = v0; v <= v1; v++) {
					p.set(c, u, gapTop, v, RED);
				}
			}
		}

		// --- shimaki: the red under-beam, the poking ends of a myōjin taper on dark red-brick stairs
		for (int u = l0; u <= r1; u++) {
			for (int v = v0; v <= v1; v++) {
				p.set(c, u, shim, v, RED);
			}
		}
		if (!shinmei) {
			for (int v = v0; v <= v1; v++) {
				if (l0 - 1 >= 0) {
					p.set(c, l0 - 1, shim, v, stairs(Blocks.RED_NETHER_BRICK_STAIRS, Direction.EAST).setValue(BlockStateProperties.HALF, Half.TOP));
				}
				if (r1 + 1 <= w - 1) {
					p.set(c, r1 + 1, shim, v, stairs(Blocks.RED_NETHER_BRICK_STAIRS, Direction.WEST).setValue(BlockStateProperties.HALF, Half.TOP));
				}
			}
		}

		// --- the kasagi: a deeper, curved beam over the plane of the gate (a shinmei is straight and flush with the pillars)
		int kv0 = Math.max(0, v0 - 1);
		int kv1 = Math.min(d - 1, v1 + 1);
		kasagi(c, p, shinmei ? l0 : 0, shinmei ? r1 : w - 1, l0, r1, kv0, kv1, c0, body, rise, sharp, !shinmei);

		// --- hanging lanterns light the underside, at the edges of the opening, with kusabi wedges against the pillars
		if (ow >= 3 && nuki >= 5) {
			boolean brace = ow >= 7;
			for (int s = 0; s < 2; s++) {
				int u = s == 0 ? ou0 : ou1;
				int lu = brace ? (s == 0 ? ou0 + 1 : ou1 - 1) : u;
				if (brace) {
					for (int v = v0; v <= v1; v++) {
						p.set(c, u, nuki - 1, v, stairs(Blocks.RED_NETHER_BRICK_STAIRS, s == 0 ? Direction.WEST : Direction.EAST).setValue(BlockStateProperties.HALF, Half.TOP));
					}
				}
				p.put(c, lu, nuki - 1, v0, LANTERN.setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
				if (ow >= 7) {
					p.put(c, lu, nuki - 1, v1, LANTERN.setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
				}
			}
		}

		// --- flanking lamps at the outer end of each side, just outside the plane
		int backRow = d - 1;
		boolean frontRoom = v0 >= 2;
		boolean backRoom = backRow - v1 >= 2;
		boolean benches = w >= 15 && tall;
		for (int u : new int[] {0, w - 1}) {
			if (frontRoom) {
				lamp(c, p, u, 0, lampStyle);
			}
			if (backRoom) {
				lamp(c, p, u, backRow, lampStyle);
			}
		}

		// --- waiting benches on the sidewalks (outside the roadway), seat of stairs against a fence rail, between the lamp
		// post at the plot's edge and the roadway
		if (benches) {
			if (frontRoom) {
				bench(c, p, l0, l1, true, 0, Direction.NORTH);
				bench(c, p, r0, r1, false, 0, Direction.NORTH);
			}
			if (backRoom) {
				bench(c, p, l0, l1, true, backRow, Direction.SOUTH);
				bench(c, p, r0, r1, false, backRow, Direction.SOUTH);
			}
		}

		// --- ni-no-torii: a smaller frame in front of the road, inside the opening (it reads as a second, lower gate)
		if (second && nuki >= 5) {
			int inset = ow >= 11 ? 2 : 1;
			int a = ou0 + inset;
			int z = ou1 - inset;
			int n2 = nuki - 1; // its nuki
			int e0 = a - 1;
			int e1 = z + 1;
			for (int u : new int[] {a, z}) {
				p.set(c, u, 0, 0, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
				for (int y = 1; y <= n2 + 1; y++) {
					p.set(c, u, y, 0, y == 1 ? BRICK : RED);
				}
			}
			for (int u = e0; u <= e1; u++) {
				p.set(c, u, n2, 0, RED);
				p.set(c, u, n2 + 2, 0, u < a || u > z ? stairs(Blocks.RED_NETHER_BRICK_STAIRS, u == e0 ? Direction.WEST : Direction.EAST).setValue(BlockStateProperties.HALF, Half.TOP) : RED);
			}
			kasagi(c, p, e0, e1, a, z, 0, 0, n2 + 3, 1, 3, sharp, true);
			if (n2 >= 4) {
				for (int u : new int[] {a + 1, z - 1}) {
					p.put(c, u, n2 - 1, 0, LANTERN.setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
				}
			}
		}
	}

	/**
	 * A kasagi over {@code b0..b1} (u) and {@code kv0..kv1} (v) whose cap line {@code c0} is the lowest top surface.
	 * The top surface rises over the cap line by {@code round(rise · t²)} half blocks ({@code t³} when sharp; t = 0 at
	 * the middle, 1 at the tips), so neighbouring columns differ by at most half a block: full blocks alternate
	 * with bottom slabs and the very tip ends in a stair. The cap is deepslate tile; under it a stripped-log band
	 * follows the curve over cherry planks. Outside the pillars ({@code p0..p1}) the underside lifts after the top.
	 */
	private static void kasagi(CitadelCanvas c, Plot p, int b0, int b1, int p0, int p1, int kv0, int kv1, int c0, int body, int rise, boolean sharp, boolean tips) {
		int mid = b0 + b1; // 2 * the centre
		double span = Math.max(1, b1 - b0);
		for (int u = b0; u <= b1; u++) {
			double t = Math.abs(2.0 * u - mid) / span; // 0 in the middle, 1 at the tips
			int r = (int) Math.round(rise * (sharp ? t * t * t : t * t));
			boolean outside = tips && (u < p0 || u > p1);
			int rb = outside && r >= 2 ? Math.min(2, r - 1) : 0; // one block of lift at most: nobody can stand under the tip
			boolean end = u == b0 || u == b1;
			Direction out = 2 * u < mid ? Direction.WEST : Direction.EAST;
			int yb = c0 - body + rb / 2;
			int hh = 1 + r; // half blocks of the top surface over the cap line
			boolean odd = hh % 2 == 1;
			int cap = odd ? c0 + hh / 2 : c0 + hh / 2 - 1;
			for (int v = kv0; v <= kv1; v++) {
				for (int y = yb; y < cap; y++) {
					BlockState s;
					if (y == yb && rb % 2 == 1) {
						s = slab(Blocks.CHERRY_SLAB).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
					} else if (y == yb && body >= 2 && (end || (!outside && rb == 0 && (v == kv0 || v == kv1)))) {
						// the underside: thin at the faces and at the very tips, flat in the middle of the depth
						Direction face = end ? (u == b0 ? Direction.EAST : Direction.WEST) : (v == kv0 ? Direction.SOUTH : Direction.NORTH);
						if (kv0 == kv1 && !end) {
							face = Direction.SOUTH;
						}
						s = stairs(Blocks.CHERRY_STAIRS, face).setValue(BlockStateProperties.HALF, Half.TOP);
					} else if (y == cap - 1 && cap - 1 > yb) {
						s = b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X); // a grained band that follows the curve
					} else {
						s = CHERRY;
					}
					p.set(c, u, y, v, s);
				}
				BlockState piece = odd ? slab(Blocks.DEEPSLATE_TILE_SLAB) : end && tips && r > 0 ? stairs(Blocks.DEEPSLATE_TILE_STAIRS, out) : TILE;
				p.set(c, u, cap, v, piece);
			}
		}
	}

	/** A flanking lamp on the ground cell (u, v): a stone toro or a tall roofed cherry post, both ending in a lantern. */
	private static void lamp(CitadelCanvas c, Plot p, int u, int v, int style) {
		p.set(c, u, 0, v, b(Blocks.DEEPSLATE_BRICKS));
		if (style == 0) {
			p.set(c, u, 1, v, b(Blocks.STONE_BRICK_WALL));
			p.set(c, u, 2, v, b(Blocks.STONE_BRICK_WALL));
			p.set(c, u, 3, v, LANTERN);
			p.set(c, u, 4, v, slab(Blocks.POLISHED_ANDESITE_SLAB));
		} else {
			for (int y = 1; y <= 4; y++) {
				p.set(c, u, y, v, b(Blocks.CHERRY_FENCE));
			}
			p.set(c, u, 5, v, LANTERN);
			p.set(c, u, 6, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
	}

	/** A bench on row {@code v} over {@code a..z}: stairs with the tall side to {@code back}, a fence rail over each seat joined to the lamp post at the plot's edge ({@code lampLeft}: it is on the west side). */
	private static void bench(CitadelCanvas c, Plot p, int a, int z, boolean lampLeft, int v, Direction back) {
		for (int u = a; u <= z; u++) {
			p.set(c, u, 0, v, stairs(Blocks.CHERRY_STAIRS, back));
			BlockState rail = b(Blocks.CHERRY_FENCE)
					.setValue(BlockStateProperties.WEST, u > a || lampLeft)
					.setValue(BlockStateProperties.EAST, u < z || !lampLeft);
			p.set(c, u, 1, v, rail);
		}
	}
}
