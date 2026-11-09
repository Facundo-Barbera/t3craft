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

/**
 * A neighbourhood torii sized to its avenue: two thick lacquer-red pillars (hashira) on dark stone feet
 * (kamebara) with a stair collar, a tie beam (nuki) that runs through them and out past them with wedges
 * (kusabi) under it, a dark plaque with a lit name panel (gakuzuka), a red under-beam (shimaki), and over it
 * a cherry-pink kasagi whose dark tile line sweeps up at both ends. The span between the pillars is the
 * whole roadway: nothing stands in the path under the gate.
 *
 * <ul>
 * <li><b>Scale</b>: the plane of the gate is 2 deep; pillars are 2 × 2 from a frontage of 15 (1 × 2 below),
 *     so at 15–19 × 4–8 × 14 the opening is 9 to 13 wide and 5 high, and the gate reads as a landmark from the
 *     avenue. Down to 7 × 2 × 8 it keeps the old small proportions (scale 1).</li>
 * <li><b>Function</b>: stone lanterns (or tall cherry lamp posts) flank the entrance on the front (and back)
 *     rows, hanging lanterns light the underside, a lit panel in a dark frame faces both ways (the catalog
 *     cannot write sign text, so it is the blank name plate of the neighbourhood), waiting benches stand
 *     against the pillars on the sidewalk rows, and with enough depth a second, lower frame (ni-no-torii)
 *     closes the front.</li>
 * <li><b>Style by seed</b>: myōjin (cherry kasagi with up-turned tips, nuki poking out) or shinmei (straight,
 *     flush); stone toro or lamp posts; one or two frames; the glow of the plaque.</li>
 * <li><b>Palette</b>: black deepslate and blackstone feet and cap → red concrete lacquer (pillars, nuki,
 *     shimaki) with dark red-brick accents → cherry planks (kasagi) → stone-brick lanterns, lit panel.</li>
 * <li><b>Walk audit</b>: every flat top that is under something else is more than eight blocks below it or has
 *     less than a body of headroom; the lamps stand on the end rows, outside the plane of the gate.</li>
 * </ul>
 * Size: 7–19 × 2–8 × 8+ (uses up to 16 blocks of height; the city asks for 15–19 × 4–8 × 14 at scale 2).
 */
public final class GateToriiCherry implements Variant {
	private static final BlockState RED = b(Blocks.CONCRETE.pick(DyeColor.RED));
	private static final BlockState LANTERN = b(Blocks.LANTERN);
	private static final BlockState CHERRY = b(Blocks.CHERRY_PLANKS);

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
		boolean shinmei = p.hash(0, 0, 0, 4) == 0; // one in four: the straight style has no horns
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

		// --- hashira on kamebara: two dark blocks of foot, the red shaft up to the plaque rows
		for (int[] cols : new int[][] {{l0, l1}, {r0, r1}}) {
			for (int u = cols[0]; u <= cols[1]; u++) {
				for (int v = v0; v <= v1; v++) {
					p.set(c, u, 0, v, b(p.hash(u, 0, v, 3) == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE));
					p.set(c, u, 1, v, b(Blocks.POLISHED_BLACKSTONE));
					for (int y = 2; y <= gapTop; y++) {
						// a ring of dark red brick above the foot and another under the shimaki break the flat lacquer
						p.set(c, u, y, v, y == 2 || y == gapTop ? b(Blocks.RED_NETHER_BRICKS) : RED);
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

		// --- the cap: a thin dark tile line over the plane of the gate; a myōjin's line sweeps up over cherry horns
		if (shinmei) {
			for (int u = l0; u <= r1; u++) {
				for (int v = v0; v <= v1; v++) {
					p.set(c, u, c0, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
				}
			}
		} else {
			for (int u = 0; u <= w - 1; u++) {
				int e = Math.min(u, w - 1 - u); // blocks from the nearer end
				Direction out = 2 * u < w - 1 ? Direction.WEST : Direction.EAST;
				int k = 0; // rows of cherry under the dark piece
				int kind = 0; // 0 slab, 1 stair rising outwards, 2 block
				if (!tall) {
					kind = e == 0 ? 1 : 0;
				} else {
					int[] t = tip(w, e);
					k = t[0];
					kind = t[1];
				}
				for (int v = v0; v <= v1; v++) {
					for (int y = 0; y < k; y++) {
						p.set(c, u, c0 + y, v, CHERRY);
					}
					BlockState piece = switch (kind) {
						case 1 -> stairs(Blocks.DEEPSLATE_TILE_STAIRS, out);
						case 2 -> b(Blocks.DEEPSLATE_TILES);
						default -> slab(Blocks.DEEPSLATE_TILE_SLAB);
					};
					p.set(c, u, c0 + k, v, piece);
				}
			}
		}

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

		// --- flanking lamps on the end rows, at the foot of each pillar, outside the plane
		int backRow = d - 1;
		boolean lampsFront = v0 >= 2 && !second;
		boolean lampsBack = backRow - v1 >= 2;
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
			for (int v = second ? 1 : 0; v <= v0 - 1; v++) {
				bench(c, p, ou0, ou1, v);
			}
			for (int v = v1 + 1; v <= d - 1; v++) {
				bench(c, p, ou0, ou1, v);
			}
		}

		// --- ni-no-torii: a lower frame closes the front, same columns, one block deep (so it shows from the avenue)
		if (second && nuki >= 5) {
			int n2 = nuki - 1;
			int e0 = Math.max(0, l0 - 1);
			int e1 = Math.min(w - 1, r1 + 1);
			for (int u : new int[] {l0, r1}) {
				p.set(c, u, 0, 0, b(Blocks.DEEPSLATE_BRICKS));
				for (int y = 1; y < n2; y++) {
					p.set(c, u, y, 0, RED);
				}
			}
			for (int u = e0; u <= e1; u++) {
				p.set(c, u, n2, 0, RED);
				boolean end = u == e0 || u == e1;
				p.set(c, u, n2 + 1, 0, end ? stairs(Blocks.DEEPSLATE_TILE_STAIRS, u == e0 ? Direction.WEST : Direction.EAST) : slab(Blocks.DEEPSLATE_TILE_SLAB));
			}
			if (ow >= 3 && n2 >= 5) {
				for (int u : new int[] {ou0, ou1}) {
					p.put(c, u, n2 - 1, 0, LANTERN.setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
				}
			}
		}
	}

	/**
	 * The dark line of a tall myōjin at {@code e} blocks from the nearer end: {rows of cherry under it, kind}
	 * (kind 0 slab, 1 stair rising outwards, 2 block). The surface climbs from half a block over the cap line
	 * up to two and a half (3 with the last stair) in steps of half a block, over cherry horns.
	 */
	private static int[] tip(int w, int e) {
		if (w >= 15) {
			return switch (e) {
				case 0 -> new int[] {2, 1};
				case 1 -> new int[] {2, 0};
				case 2 -> new int[] {1, 2};
				case 3 -> new int[] {1, 1};
				case 4 -> new int[] {1, 0};
				case 5 -> new int[] {0, 1};
				default -> new int[] {0, 0};
			};
		}
		if (w >= 9) {
			return switch (e) {
				case 0 -> new int[] {1, 1};
				case 1 -> new int[] {1, 0};
				case 2 -> new int[] {0, 1};
				default -> new int[] {0, 0};
			};
		}
		return switch (e) {
			case 0 -> new int[] {1, 1};
			case 1 -> new int[] {0, 1};
			default -> new int[] {0, 0};
		};
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

	/** A pair of seats, one against each pillar's inner side, backs to the pillars. */
	private static void bench(CitadelCanvas c, Plot p, int ou0, int ou1, int v) {
		p.set(c, ou0, 0, v, stairs(Blocks.CHERRY_STAIRS, Direction.WEST));
		p.set(c, ou1, 0, v, stairs(Blocks.CHERRY_STAIRS, Direction.EAST));
	}
}
