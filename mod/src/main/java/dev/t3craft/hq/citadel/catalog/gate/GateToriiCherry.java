package dev.t3craft.hq.citadel.catalog.gate;

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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * A neighbourhood torii sized to its avenue: two thick lacquered pillars (hashira) on dark stone feet
 * (kamebara) with a stair collar, a tie beam (nuki) that runs through them and out past them, a dark plaque
 * (gakuzuka) with ema boards on each face, a red under-beam (shimaki), and over it a cherry-pink kasagi capped
 * by a dark tile line whose ends sweep up. The span between the pillars is the whole roadway: nothing stands
 * in the path under the gate, and the nuki is 4 to 8 blocks over the street.
 *
 * <ul>
 * <li><b>Scale</b>: the plane of the gate is 2 deep; pillars are 2 × 2 from a frontage of 15 (1 × 2 below),
 *     so at 15–19 × 4–8 × 14 the opening is 9 to 13 wide and 7 high, and the gate reads as a landmark from the
 *     avenue. Down to 7 × 2 × 8 it keeps the old small proportions (scale 1).</li>
 * <li><b>Function</b>: stone lanterns (or tall cherry lamp posts) flank the entrance on the front (and back)
 *     rows, hanging lanterns light the underside, a plaque with ema boards faces both ways (the catalog
 *     cannot write sign text, so the boards are blank), waiting benches stand against the pillars on the
 *     sidewalk rows, and with enough depth a second, lower frame (ni-no-torii) closes the far end.</li>
 * <li><b>Style by seed</b>: myōjin (cherry kasagi with up-turned tips, nuki poking out) or shinmei (straight,
 *     flush); stone toro or lamp posts; one or two frames; gilded or dark heart of the plaque.</li>
 * <li><b>Palette</b>: black deepslate and blackstone feet and cap → stripped mangrove lacquer red (pillars,
 *     nuki, shimaki) → cherry planks (kasagi) → stone-brick lanterns, gilded plaque and lantern light.</li>
 * <li><b>Walk audit</b>: every flat top that is under something else is lower than the beams by more than
 *     eight blocks or has less than a body of headroom, the toro stand on the front rows outside the plane.</li>
 * </ul>
 * Size: 7–19 × 2–8 × 8+ (uses up to 16 blocks of height; the city asks for 15–19 × 4–8 × 14 at scale 2).
 */
public final class GateToriiCherry implements Variant {
	private static final BlockState RED = b(Blocks.STRIPPED_MANGROVE_LOG);
	private static final BlockState RED_X = RED.setValue(BlockStateProperties.AXIS, Direction.Axis.X);
	private static final BlockState LANTERN = b(Blocks.LANTERN);
	private static final BlockState CHERRY = b(Blocks.CHERRY_PLANKS);
	private static final BlockState TILES = b(Blocks.DEEPSLATE_TILES);

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
		boolean shinmei = p.hash(0, 0, 0, 3) == 0;
		int lampStyle = p.hash(1, 0, 0, 2);
		boolean second = d >= 6 && p.hash(2, 0, 0, 3) == 0;

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

		// --- elevation: cap row c0 (tips reach one higher when tall), body rows, shimaki, plaque row, nuki
		int top = Math.min(h, 16) - 1;
		boolean tall = top >= 10;
		int c0 = tall ? top - 2 : top; // the tips of a tall gate rise two and a half blocks over the cap line
		int body = top >= 13 ? 3 : top >= 9 ? 1 : 0;
		int shim = c0 - body - 1;
		int gap = shim - 1;
		int nuki = shim - 2;

		// --- hashira on kamebara: two dark blocks of foot, the red shaft up to the plaque row
		for (int[] cols : new int[][] {{l0, l1}, {r0, r1}}) {
			for (int u = cols[0]; u <= cols[1]; u++) {
				for (int v = v0; v <= v1; v++) {
					p.set(c, u, 0, v, b(p.hash(u, 0, v, 3) == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE));
					p.set(c, u, 1, v, b(Blocks.POLISHED_BLACKSTONE));
					for (int y = 2; y <= gap; y++) {
						p.set(c, u, y, v, RED);
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
				p.set(c, u, nuki, v, RED_X);
			}
		}

		// --- gakuzuka: a dark plaque in the middle of the plaque row, a gilded block in its heart, a banner each face
		int plW = ow >= 7 ? 3 : 1;
		if (w % 2 == 0) {
			plW++;
		}
		int ps = (w - plW) / 2;
		for (int u = ps; u < ps + plW; u++) {
			for (int v = v0; v <= v1; v++) {
				boolean heart = u == (w - 1) / 2 && plW >= 3 && p.hash(5, 0, 0, 2) == 0;
				p.set(c, u, gap, v, b(heart ? Blocks.GILDED_BLACKSTONE : Blocks.CHISELED_POLISHED_BLACKSTONE));
			}
		}
		// ema boards: open cherry trapdoors on both faces of the plaque, the middle left bare for the gilded heart;
		// a plaque of one block gets a single board (blank: the catalog has no sign text, a signboard to be written later).
		for (int u = ps; u < ps + plW; u++) {
			boolean board = plW < 3 || u != (w - 1) / 2;
			if (!board) {
				continue;
			}
			if (v0 - 1 >= 0) {
				p.put(c, u, gap, v0 - 1, b(Blocks.CHERRY_TRAPDOOR).setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
						CitadelCanvas.DECOR);
			}
			if (v1 + 1 <= d - 1) {
				p.put(c, u, gap, v1 + 1, b(Blocks.CHERRY_TRAPDOOR).setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH),
						CitadelCanvas.DECOR);
			}
		}

		// --- shimaki: the red under-beam, the poking ends of a myōjin taper on upside-down stairs
		for (int u = l0; u <= r1; u++) {
			for (int v = v0; v <= v1; v++) {
				p.set(c, u, shim, v, RED_X);
			}
		}
		if (!shinmei) {
			for (int v = v0; v <= v1; v++) {
				if (l0 - 1 >= 0) {
					p.set(c, l0 - 1, shim, v, stairs(Blocks.MANGROVE_STAIRS, Direction.EAST).setValue(BlockStateProperties.HALF, Half.TOP));
				}
				if (r1 + 1 <= w - 1) {
					p.set(c, r1 + 1, shim, v, stairs(Blocks.MANGROVE_STAIRS, Direction.WEST).setValue(BlockStateProperties.HALF, Half.TOP));
				}
			}
		}

		// --- kasagi body: cherry planks; a myōjin spans the whole frontage, a shinmei is flush with the pillars
		int b0 = shinmei ? l0 : 0;
		int b1 = shinmei ? r1 : w - 1;
		for (int y = c0 - body; y <= c0 - 1; y++) {
			for (int u = b0; u <= b1; u++) {
				boolean end = !shinmei && (u == 0 || u == w - 1);
				for (int v = v0; v <= v1; v++) {
					if (y == c0 - body && body >= 2) {
						// the underside of the kasagi: a trough of upside-down stairs, thin at the faces, a bead down the middle
						Direction face = end ? (u == 0 ? Direction.EAST : Direction.WEST) : (v == v0 ? Direction.SOUTH : Direction.NORTH);
						if (v0 == v1) {
							face = Direction.SOUTH;
						}
						p.set(c, u, y, v, stairs(Blocks.CHERRY_STAIRS, face).setValue(BlockStateProperties.HALF, Half.TOP));
					} else if (end && y == c0 - body) {
						p.set(c, u, y, v, stairs(Blocks.CHERRY_STAIRS, u == 0 ? Direction.EAST : Direction.WEST).setValue(BlockStateProperties.HALF, Half.TOP));
					} else if (body >= 2 && y == c0 - 1) {
						p.set(c, u, y, v, b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X)); // a grained band over the planks
					} else {
						p.set(c, u, y, v, CHERRY);
					}
				}
			}
		}

		// --- the cap: a thin dark tile line over the plane of the gate; a myōjin's ends sweep up
		int fv = v0;
		int bv = v1;
		if (shinmei) {
			for (int u = l0; u <= r1; u++) {
				for (int v = fv; v <= bv; v++) {
					p.set(c, u, c0, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
				}
			}
		} else {
			boolean wide = w >= 11;
			for (int u = 0; u <= w - 1; u++) {
				int e = Math.min(u, w - 1 - u); // blocks from the nearer end
				Direction out = 2 * u < w - 1 ? Direction.WEST : Direction.EAST;
				for (int v = fv; v <= bv; v++) {
					if (!tall) {
						p.set(c, u, c0, v, e == 0 ? stairs(Blocks.DEEPSLATE_TILE_STAIRS, out) : slab(Blocks.DEEPSLATE_TILE_SLAB));
					} else if (wide) {
						// the sweep: cap line 0.5 → 1.0 → 1.5 → 2.0 → 2.5 over the last four blocks
						switch (e) {
							case 0 -> {
								p.set(c, u, c0, v, TILES);
								p.set(c, u, c0 + 1, v, TILES);
								p.set(c, u, c0 + 2, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
							}
							case 1 -> {
								p.set(c, u, c0, v, TILES);
								p.set(c, u, c0 + 1, v, stairs(Blocks.DEEPSLATE_TILE_STAIRS, out));
							}
							case 2 -> {
								p.set(c, u, c0, v, TILES);
								p.set(c, u, c0 + 1, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
							}
							case 3 -> p.set(c, u, c0, v, stairs(Blocks.DEEPSLATE_TILE_STAIRS, out));
							default -> p.set(c, u, c0, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
						}
					} else if (e == 0) {
						p.set(c, u, c0, v, TILES);
						p.set(c, u, c0 + 1, v, stairs(Blocks.DEEPSLATE_TILE_STAIRS, out));
					} else if (e == 1) {
						p.set(c, u, c0, v, TILES);
						p.set(c, u, c0 + 1, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
					} else {
						p.set(c, u, c0, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
					}
				}
			}
		}

		// --- hanging lanterns light the underside, at the edges of the opening
		if (ow >= 3 && nuki >= 5) {
			boolean brace = ow >= 7 && nuki >= 5; // kusabi: upside-down wedges under the nuki, against each pillar
			for (int s = 0; s < 2; s++) {
				int u = s == 0 ? ou0 : ou1;
				int lu = brace ? (s == 0 ? ou0 + 1 : ou1 - 1) : u;
				if (brace) {
					for (int v = v0; v <= v1; v++) {
						p.set(c, u, nuki - 1, v, stairs(Blocks.MANGROVE_STAIRS, s == 0 ? Direction.WEST : Direction.EAST).setValue(BlockStateProperties.HALF, Half.TOP));
					}
				}
				p.put(c, lu, nuki - 1, v0, LANTERN.setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
				if (ow >= 7) {
					p.put(c, lu, nuki - 1, v1, LANTERN.setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
				}
			}
		}

		// --- flanking lamps in the front rows (and the back rows), at the foot of each pillar, outside the plane
		int backRow = d - 1;
		boolean lampsFront = v0 >= 2;
		boolean lampsBack = !second && backRow - v1 >= 2;
		for (int u : new int[] {l0, r1}) {
			if (lampsFront) {
				lamp(c, p, u, 0, lampStyle);
			}
			if (lampsBack) {
				lamp(c, p, u, backRow, lampStyle);
			}
		}

		// --- waiting benches against the pillars, in the rows outside the plane (the plane itself stays open)
		if (ow >= 7 && tall) {
			for (int v = 0; v <= v0 - 1; v++) {
				bench(c, p, ou0, ou1, v);
			}
			if (!second) {
				for (int v = v1 + 1; v <= d - 1; v++) {
					bench(c, p, ou0, ou1, v);
				}
			}
		}

		// --- ni-no-torii: a lower frame closes the far end, same columns, one block deep
		if (second && nuki >= 4) {
			int n2 = nuki - 1;
			int e0 = Math.max(0, l0 - 1);
			int e1 = Math.min(w - 1, r1 + 1);
			for (int u : new int[] {l0, r1}) {
				p.set(c, u, 0, backRow, b(Blocks.DEEPSLATE_BRICKS));
				for (int y = 1; y < n2; y++) {
					p.set(c, u, y, backRow, RED);
				}
			}
			for (int u = e0; u <= e1; u++) {
				p.set(c, u, n2, backRow, u == l0 || u == r1 ? RED : RED_X);
				boolean end = u == e0 || u == e1;
				p.set(c, u, n2 + 1, backRow, end ? stairs(Blocks.DEEPSLATE_TILE_STAIRS, u == e0 ? Direction.WEST : Direction.EAST) : slab(Blocks.DEEPSLATE_TILE_SLAB));
			}
			if (ow >= 3) {
				for (int u : new int[] {ou0, ou1}) {
					p.put(c, u, n2 - 1, backRow, LANTERN.setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
				}
			}
		}
	}

	/** A flanking lamp on the ground cell (u, v): a stone toro or a tall cherry post, both ending in a lantern. */
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
		}
	}

	/** A pair of seats, one against each pillar's inner side, backs to the pillars. */
	private static void bench(CitadelCanvas c, Plot p, int ou0, int ou1, int v) {
		p.set(c, ou0, 0, v, stairs(Blocks.CHERRY_STAIRS, Direction.WEST));
		p.set(c, ou1, 0, v, stairs(Blocks.CHERRY_STAIRS, Direction.EAST));
	}
}
