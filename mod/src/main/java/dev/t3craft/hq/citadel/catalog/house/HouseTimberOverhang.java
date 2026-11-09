package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.door;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.fence;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.groundPlant;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.lantern;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.log;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.mix;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.pane;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.planter;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.slab;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.stair;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.trap;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

/**
 * A jettied timber-framed town house: a shop or workshop on the ground floor, a dwelling above it and,
 * on some seeds, a study under the roof. Every storey juts one block out over the one below, carried by a row
 * of upside-down stairs and pendant posts, so the ground floor sits back behind a covered gallery. The frame is
 * dark oak (posts proud of the plaster, diagonal braces in the bays) on a light plaster fill over a stone
 * footing, with shuttered windows and a steep gable roof with a tall chimney.
 *
 * <pre>
 *  side section, front on the left (three storeys)       plan, ground floor (front at the top)
 *   v  B0-3 B0-2 B0-1 B0 .......... B1                    ┌──────────────────────────────┐
 *        ╲ roof ╱                                           │ counters      door (2)       │
 *   y11-12 ▐▌  attic face   (jetty 2)                       │ stair (2 wide)     hearth    │
 *   y 6-9   ▐  dwelling      (jetty 1)                      │ workbenches     shelves      │
 *   y 1-4   ▐  shop / workshop                              └──────────────────────────────┘
 * </pre>
 *
 * <b>Walkable.</b> Every storey has 4 free blocks. A hand-built 2-wide flight (head-on first step, 3 free over
 * every tread, a fence round the hole, a 2-deep landing) climbs from the shop to the dwelling and, with a second
 * flight beside it, up to the attic. Variation by seed: two or three storeys, gable to the street or to the
 * side, plaster colour, roof and shutters. Palette: dark oak, light plaster, deepslate and stone brick, a brick
 * or slate roof.
 */
public final class HouseTimberOverhang implements Variant {
	@Override
	public String id() {
		return "house_timber_overhang";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 16;
	}

	@Override
	public int minD() {
		return 17;
	}

	@Override
	public int minH() {
		return 21;
	}

	@Override
	public int maxW() {
		return 22;
	}

	@Override
	public int maxD() {
		return 22;
	}

	// ------------------------------------------------------------------ the plan of one build

	/** Everything one build decides up front: sizes, positions, materials. */
	private static final class G {
		final CitadelCanvas c;
		final Plot p;
		final int w;
		final int d;
		final int h;
		boolean front;
		int floors;
		/** Row of the eave stairs; the first roof row on the wall line is y0 + 1. */
		int y0;
		int bw;
		int bd;
		int a0;
		int a1;
		int b0;
		int b1;
		int uc;
		int vc;
		int vs;
		int t0;
		int t1;
		int s0;
		int s1;
		int planeA;
		int planeB;
		BlockState[] plaster;
		Block roof;
		Block eave;
		Block alt;
		Block ridge;
		Block ridgeSlab;
		Block shutter;
		DyeColor rug;
		DyeColor bedColor;

		G(CitadelCanvas c, Plot p) {
			this.c = c;
			this.p = p;
			w = p.w();
			d = p.d();
			h = p.h();
			Random r = p.rng(0x0FE12A47L);
			front = r.nextInt(3) != 0;
			floors = r.nextInt(2) == 0 ? 2 : 3;
			int roofKind = r.nextInt(2);
			int fillKind = r.nextInt(4);
			int shutKind = r.nextInt(3);
			rug = new DyeColor[] {DyeColor.RED, DyeColor.BLUE, DyeColor.GREEN, DyeColor.PURPLE}[r.nextInt(4)];
			bedColor = new DyeColor[] {DyeColor.RED, DyeColor.WHITE, DyeColor.CYAN, DyeColor.YELLOW}[r.nextInt(4)];
			int hMax = h - 1;
			y0 = floors == 3 ? 12 : 10;
			// widest cross dimension (wall line to wall line of the lowest floor) the roof allows under the box
			int cap = 2 * (hMax - 2 - y0) + 2 - 4;
			if (floors == 3 && cap < 12) {
				floors = 2;
				y0 = 10;
				cap = 2 * (hMax - 2 - y0) + 2 - 4;
			}
			int margin = !front && floors == 3 ? 6 : 4;
			if (!front && w - margin < 12) {
				front = true;
				margin = 4;
			}
			if (front) {
				bw = Math.min(w - 4, Math.min(cap, 22));
				bd = Math.min(d - 5, 22);
			} else {
				bw = Math.min(w - margin, 24);
				bd = Math.min(d - 5, Math.min(cap, 20));
			}
			a0 = (w - bw) / 2;
			a1 = a0 + bw - 1;
			int extra = Math.max(0, (d - 5 - bd) / 3);
			b0 = 4 + extra;
			b1 = b0 + bd - 1;
			uc = a0 + (bw - 2) / 2;
			vc = (b0 + b1) / 2;
			vs = b1 - 7;
			if (front) {
				planeA = floors == 3 ? b0 - 2 : b0 - 1;
				planeB = b1;
				t0 = a0 - 2;
				t1 = a1 + 2;
			} else {
				planeA = floors == 3 ? a0 - 2 : a0 - 1;
				planeB = floors == 3 ? a1 + 2 : a1 + 1;
				t0 = b0 - 2;
				t1 = b1 + 1;
			}
			s0 = planeA - 1;
			s1 = planeB + 1;
			plaster = switch (fillKind) {
				case 0 -> new BlockState[] {b(Blocks.SMOOTH_SANDSTONE), b(Blocks.SMOOTH_SANDSTONE), b(Blocks.CUT_SANDSTONE)};
				case 1 -> new BlockState[] {b(Blocks.CALCITE), b(Blocks.CALCITE), b(Blocks.SMOOTH_QUARTZ)};
				case 2 -> new BlockState[] {b(Blocks.SMOOTH_SANDSTONE), b(Blocks.SMOOTH_SANDSTONE), b(Blocks.SMOOTH_SANDSTONE), b(Blocks.MUD_BRICKS)};
				default -> new BlockState[] {b(Blocks.CALCITE), b(Blocks.SMOOTH_SANDSTONE), b(Blocks.CALCITE), b(Blocks.CUT_SANDSTONE)};
			};
			if (roofKind == 0) {
				roof = Blocks.BRICK_STAIRS;
				eave = Blocks.DEEPSLATE_BRICK_STAIRS;
				alt = Blocks.MUD_BRICK_STAIRS;
				ridge = Blocks.DEEPSLATE_TILES;
				ridgeSlab = Blocks.DEEPSLATE_TILE_SLAB;
			} else {
				roof = Blocks.DEEPSLATE_TILE_STAIRS;
				eave = Blocks.POLISHED_DEEPSLATE_STAIRS;
				alt = Blocks.COBBLED_DEEPSLATE_STAIRS;
				ridge = Blocks.POLISHED_DEEPSLATE;
				ridgeSlab = Blocks.POLISHED_DEEPSLATE_SLAB;
			}
			shutter = switch (shutKind) {
				case 0 -> Blocks.SPRUCE_TRAPDOOR;
				case 1 -> Blocks.MANGROVE_TRAPDOOR;
				default -> Blocks.CHERRY_TRAPDOOR;
			};
		}

		void set(int u, int y, int v, BlockState s) {
			p.set(c, u, y, v, s);
		}

		/** Height of the roof stairs at cross row {@code t}. */
		int roofY(int t) {
			return y0 + Math.min(t - t0, t1 - t);
		}

		/** Top of the ridge cap. */
		int ridgeTop() {
			return y0 + (t1 - t0) / 2 + 1;
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		G g = new G(c, p);
		floors(g);
		groundWalls(g);
		upperWalls(g);
		corbels(g);
		roof(g);
		gableFaces(g);
		chimney(g);
		flights(g);
		interiors(g);
		yard(g);
	}

	// ------------------------------------------------------------------ materials

	private static BlockState oak(Direction.Axis ax) {
		return log(Blocks.DARK_OAK_LOG, ax);
	}

	private static BlockState plaster(G g, int u, int y, int v) {
		return mix(g.p, u, y, v, g.plaster);
	}

	/** [palette] dark deepslate at the foot, a lighter stone-brick course above it (some mossy, some cracked). */
	private static BlockState stone(G g, int u, int y, int v) {
		if (y <= 0) {
			return mix(g.p, u, y, v, b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_BRICKS), b(Blocks.COBBLED_DEEPSLATE), b(Blocks.DEEPSLATE_BRICKS));
		}
		return mix(g.p, u, y, v, b(Blocks.DEEPSLATE_BRICKS), b(Blocks.STONE_BRICKS), b(Blocks.STONE_BRICKS), b(Blocks.MOSSY_STONE_BRICKS), b(Blocks.CRACKED_STONE_BRICKS));
	}

	private static BlockState floorPlank(G g, int u, int y, int v) {
		return mix(g.p, u, y, v, b(Blocks.SPRUCE_PLANKS), b(Blocks.SPRUCE_PLANKS), b(Blocks.DARK_OAK_PLANKS));
	}

	private static boolean air(G g, int u, int y, int v) {
		if (!g.p.has(u, y, v)) {
			return false;
		}
		BlockState s = g.p.at(g.c, u, y, v);
		return s == null || s.isAir();
	}

	private static BlockState face(Block bl, Direction d) {
		return b(bl).setValue(BlockStateProperties.HORIZONTAL_FACING, d);
	}

	private static BlockState barrel() {
		return b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
	}

	// ------------------------------------------------------------------ floors

	/** The footing, the first floor, and the second floor (a plank floor with the attic, beams only in a two-storey hall). */
	private static void floors(G g) {
		int ul = g.a0 - 1;
		int ur = g.a1 + 1;
		int vf = g.b0 - 1;
		int vb = g.b1;
		for (int u = ul; u <= ur; u++) {
			for (int v = vf; v <= vb; v++) {
				boolean side = u == ul || u == ur;
				boolean edge = side || v == vf || v == vb;
				// [palette] the plinth ring is dark stone one block proud, the floor inside is plank
				g.set(u, 0, v, edge ? stone(g, u, 0, v) : floorPlank(g, u, 0, v));
				g.set(u, 5, v, edge ? oak(side ? Direction.Axis.Z : Direction.Axis.X) : floorPlank(g, u, 5, v));
				if (g.floors == 2 && edge) {
					g.set(u, 10, v, oak(side ? Direction.Axis.Z : Direction.Axis.X));
				}
			}
		}
		if (g.floors == 3) {
			int u0 = g.front ? ul : g.a0 - 2;
			int u1 = g.front ? ur : g.a1 + 2;
			int v0 = g.front ? g.b0 - 2 : vf;
			for (int u = u0; u <= u1; u++) {
				for (int v = v0; v <= vb; v++) {
					boolean side = u == u0 || u == u1;
					boolean edge = side || v == v0 || v == vb;
					g.set(u, 10, v, edge ? oak(side ? Direction.Axis.Z : Direction.Axis.X) : floorPlank(g, u, 10, v));
				}
			}
		}
	}

	// ------------------------------------------------------------------ walls

	/** The posts, bays and what each bay holds along one wall. */
	private static final class Run {
		boolean[] post;
		/** 0 plain, 1 window, 2 brace rising to the right, 3 brace falling to the right, 4 door. */
		int[] kind;
		int[] bayLeft;
		int[] bayWidth;
		/** Window cells in this cell's bay. */
		int[] windows;
		int doorLo;
	}

	/**
	 * Splits the wall cells {@code a..bb} into bays: a post at both ends, at the {@code fixed} cells and wherever a
	 * bay would be wider than 4. A bay of 1 or 2 is all window, a wider one keeps its ends solid; with {@code braces}
	 * every second 3- or 4-wide bay holds a diagonal brace instead of a window.
	 */
	private static Run plan(int a, int bb, boolean windows, boolean braces, int doorLo, int doorHi, int... fixed) {
		int n = bb - a + 1;
		Run run = new Run();
		run.post = new boolean[n];
		run.kind = new int[n];
		run.bayLeft = new int[n];
		run.bayWidth = new int[n];
		run.windows = new int[n];
		run.doorLo = doorLo;
		run.post[0] = true;
		run.post[n - 1] = true;
		for (int f : fixed) {
			if (f > a && f < bb) {
				run.post[f - a] = true;
			}
		}
		int l = 0;
		for (int r = 1; r < n; r++) {
			if (!run.post[r]) {
				continue;
			}
			int gap = r - l - 1;
			if (gap > 4) {
				int parts = (gap + 5) / 5;
				for (int j = 1; j < parts; j++) {
					run.post[l + (int) Math.round(j * (gap + 1) / (double) parts)] = true;
				}
			}
			l = r;
		}
		l = 0;
		int bay = 0;
		for (int r = 1; r < n; r++) {
			if (!run.post[r]) {
				continue;
			}
			int gap = r - l - 1;
			if (gap > 0) {
				boolean isDoor = doorLo >= 0 && a + l + 1 == doorLo && a + r - 1 == doorHi;
				boolean brace = braces && !isDoor && gap >= 3 && gap <= 4 && bay % 2 == 1;
				int wc = gap <= 2 ? gap : gap - 2;
				for (int k = l + 1; k < r; k++) {
					int pos = k - l - 1;
					run.bayLeft[k] = l + 1;
					run.bayWidth[k] = gap;
					run.windows[k] = wc;
					if (isDoor) {
						run.kind[k] = 4;
					} else if (brace) {
						run.kind[k] = (bay / 2) % 2 == 0 ? 2 : 3;
					} else if (windows && (gap <= 2 || (pos >= 1 && pos <= gap - 2))) {
						run.kind[k] = 1;
					}
				}
				bay++;
			}
			l = r;
		}
		return run;
	}

	/**
	 * [depth] One timber-frame wall: the infill sits on the wall line, the posts stand proud of it on the outer
	 * line (so every bay is a recess) and are carried by a bracket under them on a jettied floor. Windows get a
	 * sill and a flower box, or open shutters in a two-wide bay. A diagonal brace fills some bays.
	 */
	private static void wall(G g, boolean alongU, int line, int out, int a, int bb, int y1, int y2, Run run, int w1, int w2,
			boolean proud, boolean dado) {
		Direction outD = TimberKit.out(alongU, out);
		Direction toWall = outD.getOpposite();
		Direction inc = alongU ? Direction.EAST : Direction.SOUTH;
		Direction dec = inc.getOpposite();
		int n = bb - a + 1;
		for (int k = 0; k < n; k++) {
			int i = a + k;
			int u = alongU ? i : line;
			int v = alongU ? line : i;
			for (int y = y1; y <= y2; y++) {
				BlockState s;
				if (run.post[k]) {
					s = oak(Direction.Axis.Y);
				} else {
					int kind = run.kind[k];
					int j = k - run.bayLeft[k];
					int gw = run.bayWidth[k];
					if (kind == 4) {
						if (y == y1) {
							s = door(Blocks.DARK_OAK_DOOR, Direction.NORTH, false).setValue(BlockStateProperties.DOOR_HINGE, i == run.doorLo ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
						} else if (y == y1 + 1) {
							s = door(Blocks.DARK_OAK_DOOR, Direction.NORTH, true).setValue(BlockStateProperties.DOOR_HINGE, i == run.doorLo ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
						} else if (y == y1 + 2) {
							s = pane(alongU, null);
						} else {
							s = oak(alongU ? Direction.Axis.X : Direction.Axis.Z);
						}
					} else if (kind == 1 && y >= w1 && y <= w2) {
						s = pane(alongU, null);
					} else if (kind == 2 && y == y2 - (gw - 1) + j) {
						s = stair(Blocks.DARK_OAK_STAIRS, inc, false);
					} else if (kind == 3 && y == y2 - j) {
						s = stair(Blocks.DARK_OAK_STAIRS, dec, false);
					} else if (dado && y == y1) {
						s = stone(g, u, y, v);
					} else {
						s = plaster(g, u, y, v);
					}
				}
				TimberKit.put(g.c, g.p, alongU, line, i, y, s);
			}
			if (proud && run.post[k] && k > 0 && k < n - 1) {
				for (int y = y1; y <= y2; y++) {
					TimberKit.put(g.c, g.p, alongU, line + out, i, y, oak(Direction.Axis.Y));
				}
				if (y1 > 1) {
					// [depth] a bracket under every proud post of a jettied floor
					TimberKit.put(g.c, g.p, alongU, line + out, i, y1 - 1, stair(Blocks.DARK_OAK_STAIRS, toWall, true));
				}
			}
		}
		// windows: sills, flower boxes, shutters
		Direction lowFace = inc;
		Direction highFace = dec;
		for (int k = 0; k < n; k++) {
			if (run.kind[k] != 1 || run.post[k]) {
				continue;
			}
			int i = a + k;
			int u = alongU ? i : line + out;
			int v = alongU ? line + out : i;
			TimberKit.put(g.c, g.p, alongU, line + out, i, w1 - 1, stair(Blocks.DARK_OAK_STAIRS, toWall, true));
			if (run.windows[k] == 2) {
				boolean leftN = k > 0 && run.kind[k - 1] == 1 && !run.post[k - 1];
				for (int y = w1; y <= w2; y++) {
					TimberKit.put(g.c, g.p, alongU, line + out, i, y, trap(g.shutter, leftN ? highFace : lowFace, true, false));
				}
			} else if (run.windows[k] == 1 || run.windows[k] > 2) {
				TimberKit.put(g.c, g.p, alongU, line + out, i, w1, planter(g.p, u, w1, v));
			}
		}
	}

	private static void groundWalls(G g) {
		int a0 = g.a0;
		int a1 = g.a1;
		Run front = plan(a0, a1, true, false, g.uc, g.uc + 1, g.uc - 1, g.uc + 2);
		wall(g, true, g.b0, -1, a0, a1, 1, 4, front, 2, 3, true, true);
		Run back = plan(a0, a1, true, false, -1, -1);
		wall(g, true, g.b1, 1, a0, a1, 1, 4, back, 2, 3, false, true);
		Run left = plan(g.b0, g.b1, true, false, -1, -1);
		wall(g, false, a0, -1, g.b0, g.b1, 1, 4, left, 2, 3, true, true);
		Run right = plan(g.b0, g.b1, true, false, -1, -1);
		for (int k = 0; k < right.kind.length; k++) {
			int v = g.b0 + k;
			if (v >= g.vc - 1 && v <= g.vc + 2) {
				right.kind[k] = 0; // the chimney breast stands here, no window
			}
		}
		wall(g, false, a1, 1, g.b0, g.b1, 1, 4, right, 2, 3, true, true);
	}

	private static void upperWalls(G g) {
		int ul = g.a0 - 1;
		int ur = g.a1 + 1;
		int vf = g.b0 - 1;
		Run front = plan(ul, ur, true, false, -1, -1, g.uc - 1, g.uc + 2);
		wall(g, true, vf, -1, ul, ur, 6, 9, front, 7, 8, true, false);
		Run back = plan(ul, ur, true, true, -1, -1);
		wall(g, true, g.b1, 1, ul, ur, 6, 9, back, 7, 8, false, false);
		Run left = plan(vf, g.b1, true, true, -1, -1);
		wall(g, false, ul, -1, vf, g.b1, 6, 9, left, 7, 8, true, false);
		Run right = plan(vf, g.b1, true, true, -1, -1);
		wall(g, false, ur, 1, vf, g.b1, 6, 9, right, 7, 8, true, false);
		if (g.floors == 3) {
			// the knee walls of the attic, between the two gable faces
			if (g.front) {
				Run kl = plan(vf, g.b1 - 1, false, false, -1, -1);
				wall(g, false, ul, -1, vf, g.b1 - 1, 11, 12, kl, 0, 0, false, false);
				Run kr = plan(vf, g.b1 - 1, false, false, -1, -1);
				wall(g, false, ur, 1, vf, g.b1 - 1, 11, 12, kr, 0, 0, false, false);
			} else {
				Run kf = plan(ul, ur, false, false, -1, -1);
				wall(g, true, vf, -1, ul, ur, 11, 12, kf, 0, 0, false, false);
				Run kb = plan(ul, ur, false, false, -1, -1);
				wall(g, true, g.b1, 1, ul, ur, 11, 12, kb, 0, 0, false, false);
			}
		}
	}

	/** [depth] the ménsulas: pendant posts at the corners of the jetty and a row of upside-down stairs under every overhang. */
	private static void corbels(G g) {
		int ul = g.a0 - 1;
		int ur = g.a1 + 1;
		int vf = g.b0 - 1;
		int vb = g.b1;
		for (int y = 1; y <= 4; y++) {
			g.set(ul, y, vf, oak(Direction.Axis.Y));
			g.set(ur, y, vf, oak(Direction.Axis.Y));
			g.set(ul, y, vb, oak(Direction.Axis.Y));
			g.set(ur, y, vb, oak(Direction.Axis.Y));
		}
		for (int u = ul + 1; u < ur; u++) {
			if (air(g, u, 4, vf)) {
				g.set(u, 4, vf, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, true));
			}
		}
		for (int v = vf + 1; v < vb; v++) {
			if (air(g, ul, 4, v)) {
				g.set(ul, 4, v, stair(Blocks.DARK_OAK_STAIRS, Direction.EAST, true));
			}
			if (air(g, ur, 4, v)) {
				g.set(ur, 4, v, stair(Blocks.DARK_OAK_STAIRS, Direction.WEST, true));
			}
		}
		if (g.floors == 3) {
			// the attic jetty: its face stands on the line of the proud posts of the floor below
			if (g.front) {
				for (int u = ul; u <= ur; u++) {
					if (air(g, u, 9, g.b0 - 2)) {
						g.set(u, 9, g.b0 - 2, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, true));
					}
				}
			} else {
				for (int v = vf; v <= vb; v++) {
					if (air(g, g.a0 - 2, 9, v)) {
						g.set(g.a0 - 2, 9, v, stair(Blocks.DARK_OAK_STAIRS, Direction.EAST, true));
					}
					if (air(g, g.a1 + 2, 9, v)) {
						g.set(g.a1 + 2, 9, v, stair(Blocks.DARK_OAK_STAIRS, Direction.WEST, true));
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ roof

	private static void roof(G g) {
		TimberKit.gable(g.c, g.p, !g.front, g.s0, g.s1, g.t0, g.t1, g.y0, g.roof, g.eave, g.ridge, g.ridgeSlab, g.alt, true, true);
		if (g.front) {
			// a hoist beam over the street, with a lantern
			int tm = g.t0 + (g.t1 - g.t0) / 2;
			int ry = g.y0 + (g.t1 - g.t0) / 2;
			g.set(tm, ry, g.s0 - 1, oak(Direction.Axis.Z));
			g.p.put(g.c, tm, ry - 1, g.s0 - 1, lantern(true), CitadelCanvas.HANGING);
		}
	}

	/** The gable faces: plaster under a bargeboard, a king post and posts every four, and big windows. */
	private static void gableFaces(G g) {
		int a = g.front ? g.a0 - 1 : g.b0 - 1;
		int bb = g.front ? g.a1 + 1 : g.b1;
		for (int pl = 0; pl < 2; pl++) {
			int plane = pl == 0 ? g.planeA : g.planeB;
			int cm = (a + bb) / 2;
			for (int i = a; i <= bb; i++) {
				int top = g.roofY(i);
				boolean pst = i == a || i == bb || (i - cm) % 4 == 0;
				boolean win = Math.abs(i - cm) == 1 || Math.abs(i - cm) == 2;
				for (int y = 11; y <= top - 1; y++) {
					int u = g.front ? i : plane;
					int v = g.front ? plane : i;
					BlockState s;
					if (y == top - 1 && top - 1 >= 12) {
						s = b(Blocks.DARK_OAK_PLANKS);
					} else if (pst) {
						s = oak(Direction.Axis.Y);
					} else if (win && y >= 12 && y <= 14 && y <= top - 2 && top - 2 >= 13) {
						s = pane(g.front, null);
					} else {
						s = plaster(g, u, y, v);
					}
					TimberKit.put(g.c, g.p, g.front, plane, i, y, s);
				}
			}
		}
	}

	private static void chimney(G g) {
		int cu0 = g.a1 - 2;
		int rmax = 0;
		for (int k = 0; k < 3; k++) {
			rmax = Math.max(rmax, g.front ? g.roofY(cu0 + k) : g.roofY(g.vc + (k % 2)));
		}
		int top = Math.min(rmax + 2, g.h - 2);
		for (int y = 1; y <= top; y++) {
			for (int u = cu0; u <= g.a1; u++) {
				if (y > g.y0 + 1 && u == cu0) {
					continue;
				}
				for (int v = g.vc; v <= g.vc + 1; v++) {
					g.set(u, y, v, mix(g.p, u, y, v, b(Blocks.BRICKS), b(Blocks.BRICKS), b(Blocks.DEEPSLATE_BRICKS), b(Blocks.STONE_BRICKS)));
				}
			}
		}
		for (int u = g.a1 - 1; u <= g.a1; u++) {
			for (int v = g.vc; v <= g.vc + 1; v++) {
				g.set(u, top + 1, v, slab(Blocks.DEEPSLATE_BRICK_SLAB, false));
			}
		}
		// the hearth of the ground floor
		for (int v = g.vc; v <= g.vc + 1; v++) {
			g.set(cu0, 1, v, b(Blocks.CAMPFIRE).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST).setValue(BlockStateProperties.LIT, true));
		}
	}

	// ------------------------------------------------------------------ stairs

	/**
	 * A 2-wide flight along v: step {@code j} at {@code v0 + j·dv} on layer {@code lower + j}, planks under every
	 * step, and the floor above cut over the middle steps (3 free blocks over every tread). Returns the cut cells.
	 */
	private static List<int[]> flight(G g, int u, int v0, int dv, int lower, int upper) {
		int rise = upper - lower;
		Direction up = dv > 0 ? Direction.SOUTH : Direction.NORTH;
		List<int[]> holes = new ArrayList<>();
		for (int du = 0; du < 2; du++) {
			for (int j = 0; j < rise; j++) {
				int v = v0 + j * dv;
				for (int y = lower; y < lower + j; y++) {
					g.set(u + du, y, v, b(Blocks.SPRUCE_PLANKS));
				}
				g.set(u + du, lower + j, v, stair(Blocks.SPRUCE_STAIRS, up, false));
				if (j >= 1 && j < rise - 1) {
					g.set(u + du, upper - 1, v, CitadelCanvas.AIR);
					holes.add(new int[] {u + du, v});
				}
			}
		}
		return holes;
	}

	/** A fence round the cut cells, on the floor cells that touch them (not on a step). */
	private static void rails(G g, List<int[]> holes, int upper) {
		Set<Integer> hole = new HashSet<>();
		for (int[] h : holes) {
			hole.add(h[0] * 1000 + h[1]);
		}
		Set<Integer> rail = new HashSet<>();
		int[][] nb = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
		for (int[] h : holes) {
			for (int[] n : nb) {
				int nu = h[0] + n[0];
				int nv = h[1] + n[1];
				if (hole.contains(nu * 1000 + nv) || !g.p.has(nu, upper, nv)) {
					continue;
				}
				BlockState fl = g.p.at(g.c, nu, upper - 1, nv);
				boolean solid = fl != null && !fl.isAir() && !(fl.getBlock() instanceof StairBlock);
				if (solid && air(g, nu, upper, nv)) {
					rail.add(nu * 1000 + nv);
				}
			}
		}
		for (int key : rail) {
			int ru = key / 1000;
			int rv = key % 1000;
			g.set(ru, upper, rv, fence(Blocks.DARK_OAK_FENCE, rail.contains(ru * 1000 + rv - 1), rail.contains((ru + 1) * 1000 + rv),
					rail.contains(ru * 1000 + rv + 1), rail.contains((ru - 1) * 1000 + rv)));
		}
	}

	/**
	 * The flights: from the shop up to the dwelling along the left wall (first step met head-on, a 2-deep landing
	 * on top) and, for three storeys, back towards the street beside it up to the attic.
	 */
	private static void flights(G g) {
		int su = g.a0 + 1;
		List<int[]> a = flight(g, su, g.vs, 1, 1, 6);
		rails(g, a, 6);
		if (g.floors == 3) {
			List<int[]> bf = flight(g, su + 2, g.vs + 5, -1, 6, 11);
			rails(g, bf, 11);
		}
	}

	// ------------------------------------------------------------------ interiors

	private static boolean stairZone(G g, int u, int v) {
		return u >= g.a0 && u <= g.a0 + 5 && v >= g.vs - 2 && v <= g.b1;
	}

	private static boolean stackZone(G g, int u, int v) {
		return u >= g.a1 - 4 && v >= g.vc - 1 && v <= g.vc + 2;
	}

	private static boolean doorZone(G g, int u, int v) {
		return u >= g.uc - 1 && u <= g.uc + 2 && v <= g.b0 + 4;
	}

	private static boolean usable(G g, int u, int v, boolean ground) {
		return !stairZone(g, u, v) && !stackZone(g, u, v) && !(ground && doorZone(g, u, v));
	}

	/** Whether a block stands under {@code (u, y, v)} to carry furniture (not a stair, not a hole). */
	private static boolean carried(G g, int u, int y, int v) {
		BlockState s = g.p.at(g.c, u, y - 1, v);
		return s != null && !s.isAir() && !(s.getBlock() instanceof StairBlock);
	}

	/** Puts furniture only on a free, carried cell outside the stair, hearth and door zones. */
	private static void furnish(G g, int u, int y, int v, BlockState s, boolean ground) {
		if (usable(g, u, v, ground) && air(g, u, y, v) && carried(g, u, y, v)) {
			g.set(u, y, v, s);
		}
	}

	private static void bed(G g, int u, int y, int v, Direction head, DyeColor col, boolean ground) {
		int hu = u + head.getStepX();
		int hv = v + head.getStepZ();
		if (!usable(g, u, v, ground) || !usable(g, hu, hv, ground) || !air(g, u, y, v) || !air(g, hu, y, hv)
				|| !carried(g, u, y, v) || !carried(g, hu, y, hv)) {
			return;
		}
		BlockState base = Blocks.BED.pick(col).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, head);
		g.set(u, y, v, base.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		g.set(hu, y, hv, base.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
	}

	private static void interiors(G g) {
		shop(g);
		dwelling(g);
		if (g.floors == 3) {
			attic(g);
		}
	}

	/** Shop and workshop: counters either side of the door, workbenches on the left, stock on the back and right walls. */
	private static void shop(G g) {
		int ui0 = g.a0 + 1;
		int ui1 = g.a1 - 1;
		int vi0 = g.b0 + 1;
		int vi1 = g.b1 - 1;
		for (int u = ui0; u <= ui1; u++) {
			if (u <= g.uc - 3 || u >= g.uc + 4) {
				furnish(g, u, 1, vi0, b(Blocks.DARK_OAK_PLANKS), true);
				int t = g.p.hash(u, 2, vi0, 4);
				furnish(g, u, 2, vi0, t == 0 ? lantern(false) : t == 1 ? Blocks.POTTED_FERN.defaultBlockState() : t == 2 ? Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState() : CitadelCanvas.AIR, true);
			}
		}
		BlockState[] bench = {b(Blocks.CRAFTING_TABLE), b(Blocks.SMITHING_TABLE), face(Blocks.LOOM, Direction.EAST), b(Blocks.CARTOGRAPHY_TABLE), b(Blocks.FLETCHING_TABLE), barrel()};
		for (int v = vi0 + 2; v <= g.vs - 3; v++) {
			furnish(g, ui0, 1, v, bench[g.p.hash(ui0, 1, v, bench.length)], true);
		}
		for (int u = g.a0 + 6; u <= ui1; u++) {
			int t = g.p.hash(u, 1, vi1, 5);
			if (t <= 1) {
				furnish(g, u, 1, vi1, b(Blocks.BOOKSHELF), true);
				furnish(g, u, 2, vi1, b(Blocks.BOOKSHELF), true);
			} else if (t == 2) {
				furnish(g, u, 1, vi1, face(Blocks.CHEST, Direction.NORTH), true);
			} else if (t == 3) {
				furnish(g, u, 1, vi1, barrel(), true);
			} else {
				furnish(g, u, 1, vi1, face(Blocks.FURNACE, Direction.NORTH).setValue(BlockStateProperties.LIT, true), true);
			}
		}
		for (int v = g.vc + 3; v <= vi1 - 1; v++) {
			furnish(g, ui1, 1, v, g.p.hash(ui1, 1, v, 2) == 0 ? barrel() : face(Blocks.CHEST, Direction.WEST), true);
		}
		for (int v = vi0 + 2; v <= g.vc - 2; v++) {
			furnish(g, ui1, 1, v, g.p.hash(ui1, 1, v, 3) == 0 ? b(Blocks.BOOKSHELF) : barrel(), true);
		}
	}

	/** Dwelling: a dining table by the street windows, a rug, beds along the back wall. */
	private static void dwelling(G g) {
		int vm = (g.b0 + g.b1) / 2 + 1;
		int cu = g.front ? g.t0 + (g.t1 - g.t0) / 2 : (g.a0 + g.a1) / 2;
		int cv = g.front ? vm : g.t0 + (g.t1 - g.t0) / 2;
		for (int u = g.uc + 1; u <= g.uc + 4; u++) {
			furnish(g, u, 6, g.b0 + 2, slab(Blocks.SPRUCE_SLAB, false), false);
		}
		for (int u = g.uc + 2; u <= g.uc + 3; u++) {
			furnish(g, u, 6, g.b0 + 1, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false), false);
			furnish(g, u, 6, g.b0 + 3, stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false), false);
		}
		for (int u = cu - 1; u <= cu + 1; u++) {
			for (int v = cv - 1; v <= cv + 1; v++) {
				furnish(g, u, 6, v, Blocks.CARPET.pick(g.rug).defaultBlockState(), false);
			}
		}
		int bu = g.a0 + 7;
		bed(g, bu, 6, g.b1 - 2, Direction.SOUTH, g.bedColor, false);
		if (bu + 2 <= g.a1 - 1) {
			bed(g, bu + 2, 6, g.b1 - 2, Direction.SOUTH, g.bedColor, false);
			furnish(g, bu + 1, 6, g.b1 - 1, face(Blocks.CHEST, Direction.NORTH), false);
		}
		for (int v = g.b0 + 1; v <= g.vs - 3; v++) {
			furnish(g, g.a0, 6, v, b(Blocks.BOOKSHELF), false);
			furnish(g, g.a0, 7, v, b(Blocks.BOOKSHELF), false);
		}
		for (int v = g.b0 + 1; v <= g.vc - 2; v++) {
			furnish(g, g.a1, 6, v, g.p.hash(g.a1, 6, v, 2) == 0 ? barrel() : face(Blocks.CHEST, Direction.WEST), false);
		}
		// the lantern over the rug: on a chain from the ridge in a two-storey hall, hung under the attic floor otherwise
		if (g.floors == 2) {
			int ridgeY = g.y0 + (g.t1 - g.t0) / 2;
			for (int y = 10; y < ridgeY; y++) {
				g.p.put(g.c, cu, y, cv, b(Blocks.IRON_CHAIN).setValue(BlockStateProperties.AXIS, Direction.Axis.Y), CitadelCanvas.HANGING);
			}
			g.p.put(g.c, cu, 9, cv, lantern(true), CitadelCanvas.HANGING);
		} else if (!air(g, cu, 10, cv)) {
			g.p.put(g.c, cu, 9, cv, lantern(true), CitadelCanvas.HANGING);
		}
	}

	/** Attic study: shelves along the knee walls, a lectern and a desk by the gable, a rug and a lantern from the ridge. */
	private static void attic(G g) {
		int iu0 = g.front ? g.a0 : g.a0 - 1;
		int iu1 = g.front ? g.a1 : g.a1 + 1;
		int iv0 = g.front ? g.b0 - 1 : g.b0;
		int iv1 = g.b1 - 1;
		if (g.front) {
			for (int v = iv0; v <= iv1; v++) {
				if (g.p.hash(iu0, 11, v, 3) != 0) {
					furnish(g, iu0, 11, v, b(Blocks.BOOKSHELF), false);
					furnish(g, iu0, 12, v, b(Blocks.BOOKSHELF), false);
				}
				if (g.p.hash(iu1, 11, v, 3) != 0) {
					furnish(g, iu1, 11, v, b(Blocks.BOOKSHELF), false);
					furnish(g, iu1, 12, v, b(Blocks.BOOKSHELF), false);
				}
			}
		} else {
			for (int u = iu0; u <= iu1; u++) {
				if (g.p.hash(u, 11, iv0, 3) != 0) {
					furnish(g, u, 11, iv0, b(Blocks.BOOKSHELF), false);
					furnish(g, u, 12, iv0, b(Blocks.BOOKSHELF), false);
				}
				if (g.p.hash(u, 11, iv1, 3) != 0) {
					furnish(g, u, 11, iv1, b(Blocks.BOOKSHELF), false);
					furnish(g, u, 12, iv1, b(Blocks.BOOKSHELF), false);
				}
			}
		}
		int cu = g.front ? g.t0 + (g.t1 - g.t0) / 2 : (g.a0 + g.a1) / 2 + 1;
		int cv = g.front ? (g.b0 + g.b1) / 2 + 1 : g.t0 + (g.t1 - g.t0) / 2;
		for (int u = cu - 1; u <= cu + 1; u++) {
			for (int v = cv - 1; v <= cv + 1; v++) {
				furnish(g, u, 11, v, Blocks.CARPET.pick(DyeColor.BLUE).defaultBlockState(), false);
			}
		}
		if (g.front) {
			furnish(g, cu, 11, iv0, face(Blocks.LECTERN, Direction.NORTH).setValue(BlockStateProperties.HAS_BOOK, true), false);
			furnish(g, cu + 2, 11, iv0, b(Blocks.CARTOGRAPHY_TABLE), false);
			furnish(g, cu - 2, 11, iv0, face(Blocks.CHEST, Direction.NORTH), false);
		} else {
			furnish(g, iu0 + 6, 11, iv0 + 1, face(Blocks.LECTERN, Direction.NORTH).setValue(BlockStateProperties.HAS_BOOK, true), false);
			furnish(g, iu1 - 5, 11, iv0 + 1, b(Blocks.CARTOGRAPHY_TABLE), false);
		}
		int ridgeY = g.y0 + (g.t1 - g.t0) / 2;
		for (int y = 15; y < ridgeY; y++) {
			g.p.put(g.c, cu, y, cv, b(Blocks.IRON_CHAIN).setValue(BlockStateProperties.AXIS, Direction.Axis.Y), CitadelCanvas.HANGING);
		}
		g.p.put(g.c, cu, 14, cv, lantern(true), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ yard

	/** The stoop and a flagstone path to the street, lantern posts, flower beds in front and a garden on each side. */
	private static void yard(G g) {
		int uc = g.uc;
		int vStoop = g.b0 - 2;
		for (int v = 0; v <= vStoop - 1; v++) {
			for (int u = uc - 1; u <= uc + 2; u++) {
				g.set(u, 0, v, mix(g.p, u, 0, v, slab(Blocks.STONE_BRICK_SLAB, false), slab(Blocks.COBBLESTONE_SLAB, false), slab(Blocks.DEEPSLATE_BRICK_SLAB, false)));
			}
		}
		for (int u = uc - 1; u <= uc + 2; u++) {
			g.set(u, 0, vStoop, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
		}
		for (int u : new int[] {uc - 3, uc + 4}) {
			g.set(u, 0, vStoop, b(Blocks.POLISHED_DEEPSLATE));
			g.set(u, 1, vStoop, fence(Blocks.DARK_OAK_FENCE, false, false, false, false));
			g.set(u, 2, vStoop, fence(Blocks.DARK_OAK_FENCE, false, false, false, false));
			g.set(u, 3, vStoop, lantern(false));
		}
		for (int v = 1; v <= vStoop - 1; v++) {
			for (int u = g.a0 - 1; u <= g.a1 + 1; u++) {
				if (u >= uc - 2 && u <= uc + 3) {
					continue;
				}
				BlockState pl = groundPlant(g.p, u, 0, v);
				if (pl != null && g.p.hash(u, 0, v, 2) == 0) {
					g.set(u, 0, v, pl);
				}
			}
		}
		// the side gardens
		for (int side = 0; side < 2; side++) {
			int lo = side == 0 ? 0 : g.a1 + 3;
			int hi = side == 0 ? g.a0 - 3 : g.w - 1;
			if (hi - lo < 1) {
				continue;
			}
			for (int u = lo; u <= hi; u++) {
				for (int v = 0; v < g.d; v++) {
					BlockState pl = groundPlant(g.p, u, 0, v);
					if (pl != null) {
						g.set(u, 0, v, pl);
					}
				}
			}
			int bu = side == 0 ? hi : lo;
			int bv = g.b0 + 3;
			g.set(bu, 0, bv, stair(Blocks.SPRUCE_STAIRS, side == 0 ? Direction.WEST : Direction.EAST, false));
			g.set(bu, 0, bv + 1, stair(Blocks.SPRUCE_STAIRS, side == 0 ? Direction.WEST : Direction.EAST, false));
			g.set(bu, 0, g.b1 - 2, barrel());
			g.set(bu, 1, g.b1 - 2, Blocks.POTTED_FERN.defaultBlockState());
			g.set(bu, 0, g.b1 - 4, log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
			g.set(bu, 0, g.b1 - 3, log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
			g.set(bu, 1, g.b1 - 4, log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
		}
	}
}
