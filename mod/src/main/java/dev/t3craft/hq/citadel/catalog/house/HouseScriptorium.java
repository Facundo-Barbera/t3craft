package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.barrel;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.clearPane;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.door;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.facing;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.lantern;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.log;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.slab;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.stair;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.tintBlock;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.tintPane;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A scriptorium: a tall gothic nave where the agents copy and read. The long walls are rows of
 * deepslate piers with buttresses and big stained-glass windows (you see the shelves and lecterns
 * behind them), the steep deepslate-tile roof carries a glazed ridge lantern and stained skylights,
 * and the front gable has a stained rose window over a hooded door.
 *
 * <p>Concept: Cistercian scriptoria and the Sainte-Chapelle (all glass between stone ribs) in the
 * citadel palette. Dark deepslate base, polished-deepslate piers, calcite gables, cherry frieze and
 * rakes, gold lanterns, jewel-coloured glass. Memorable for the light: a glowing nave, ridge to floor.
 *
 * <pre>
 *  section (u →)                         plan (v ↓ = front → back)
 *      y15  ▲ cap  ▲                      v0  . steps + hood + piers .
 *   y13/14 ▐glass▌ ridge lantern          v1  ■ front wall: piers, lancets, door bay
 *     y8-12 / roof \ skylights            v2+ ■ nave: shelves u2, lecterns u3, table centre
 *      y7  ═ plate + tie beams ═          vB  ■ back wall, tall shelves
 *     y2-6 ▒ windows between piers
 *      y0-1 deepslate plinth + buttresses
 * </pre>
 *
 * Size: 11–13 × 11–15 × 18+.
 */
public final class HouseScriptorium implements Variant {
	private static final int PLATE = 9; // top plate; walls y1..y8, roof from y9
	private static final int WIN0 = 2; // windows y2..y7 (tinted y6..y7), frieze y8

	@Override
	public String id() {
		return "house_scriptorium";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 11;
	}

	@Override
	public int minD() {
		return 11;
	}

	@Override
	public int minH() {
		return 18;
	}

	@Override
	public int maxW() {
		return 13;
	}

	@Override
	public int maxD() {
		return 15;
	}

	/** [palette] jewel colours of the glass, in a fixed order that a hash walks along the windows. */
	private static final DyeColor[] JEWELS = {DyeColor.LIGHT_BLUE, DyeColor.PINK, DyeColor.CYAN, DyeColor.YELLOW, DyeColor.MAGENTA};

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x5C21_9701L);
		int w = p.w();
		int d = p.d();
		int cu = w / 2;
		int vb = d - 2; // back wall
		boolean shelfLeft = r.nextBoolean(); // which side carries the chiseled-shelf accents

		foundation(c, p, vb);
		frontWall(c, p, cu);
		sideWalls(c, p, vb);
		backWall(c, p, cu, vb);
		frieze(c, p, vb);
		roof(c, p, r, cu, vb);
		gables(c, p, cu, vb);
		interior(c, p, r, cu, vb, shelfLeft);
		frontYard(c, p, cu);
		backYard(c, p, r, cu, vb);
	}

	// ------------------------------------------------------------------ helpers

	private static boolean isSidePier(int v, int vb) {
		return v == vb || v == 1 || ((v - 1) % 3 == 0 && v <= vb - 2);
	}

	private static boolean isFrontPier(int u, int w, int cu) {
		return u == 1 || u == w - 2 || Math.abs(u - cu) == 2;
	}

	/** [palette] pale piers on the dark plinth: stone bricks with cracked, mossy and calcite patches, by hash. */
	private static BlockState pier(Plot p, int u, int y, int v) {
		if (y <= 1) {
			return base(p, u, y, v);
		}
		return switch (p.hash(u, y, v, 6)) {
			case 0 -> b(Blocks.CRACKED_STONE_BRICKS);
			case 1 -> b(Blocks.MOSSY_STONE_BRICKS);
			case 2 -> b(Blocks.CALCITE);
			default -> b(Blocks.STONE_BRICKS);
		};
	}

	/** [palette] the heavy plinth: deepslate bricks, cobbled and cracked, never uniform. */
	private static BlockState base(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 6)) {
			case 0 -> b(Blocks.COBBLED_DEEPSLATE);
			case 1 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
			default -> b(Blocks.DEEPSLATE_BRICKS);
		};
	}

	/** [palette] the light wall: calcite with the odd smooth-stone block. */
	private static BlockState infill(Plot p, int u, int y, int v) {
		return p.hash(u, y, v, 5) == 0 ? b(Blocks.SMOOTH_STONE) : b(Blocks.CALCITE);
	}

	private static DyeColor jewel(Plot p, int u, int y, int v) {
		return JEWELS[p.hash(u, y, v, JEWELS.length)];
	}

	/** One window cell of a lancet: clear glass low, jewel-tinted at the top. */
	private static BlockState glass(Plot p, int u, int y, int v, boolean alongU) {
		return y >= 6 ? tintPane(jewel(p, u / 2, y, v / 2), alongU) : clearPane(alongU);
	}

	// ------------------------------------------------------------------ foundation

	/** The floor slab inside, a stone footing under the walls, and buttress feet outside. */
	private static void foundation(CitadelCanvas c, Plot p, int vb) {
		int w = p.w();
		for (int u = 1; u <= w - 2; u++) {
			for (int v = 1; v <= vb; v++) {
				boolean edge = u == 1 || u == w - 2 || v == 1 || v == vb;
				if (edge) {
					p.set(c, u, 0, v, base(p, u, 0, v));
				} else {
					// [palette] dark oak boards, with a stone nave stripe down the middle
					p.set(c, u, 0, v, u == w / 2 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.DARK_OAK_PLANKS));
				}
			}
		}
		// [depth] buttress feet: a wider stone footing at every pier row of both long walls
		for (int v = 1; v <= vb; v++) {
			if (isSidePier(v, vb)) {
				p.set(c, 0, 0, v, base(p, 0, 0, v));
				p.set(c, w - 1, 0, v, base(p, w - 1, 0, v));
			}
		}
	}

	// ------------------------------------------------------------------ front wall (v = 1)

	private static void frontWall(CitadelCanvas c, Plot p, int cu) {
		int w = p.w();
		for (int u = 1; u <= w - 2; u++) {
			boolean pier = isFrontPier(u, w, cu);
			boolean bay = Math.abs(u - cu) <= 1;
			for (int y = 1; y <= 8; y++) {
				BlockState st;
				if (pier) {
					st = y == 8 ? b(Blocks.CHISELED_STONE_BRICKS) : pier(p, u, y, 1);
				} else if (bay) {
					if (u == cu && y <= 2) {
						st = door(Blocks.CHERRY_DOOR, Direction.NORTH, y == 2); // [depth] the door sits at the back of the bay
					} else if (y == 3 && u == cu) {
						st = b(Blocks.CHISELED_STONE_BRICKS); // lintel
					} else if (y >= 4 && y <= 7) {
						st = tintPane(jewel(p, u, y, 1), true); // transom of stained glass over the door
					} else if (y == 8) {
						st = log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X);
					} else if (y == 1) {
						st = pier(p, u, y, 1);
					} else {
						st = clearPane(true); // sidelights
					}
				} else if (y == 1) {
					st = base(p, u, y, 1);
				} else if (y >= WIN0 && y <= 7) {
					st = glass(p, u, y, 1, true); // lancet windows between the piers
				} else if (y == 8) {
					st = log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X);
				} else {
					st = infill(p, u, y, 1);
				}
				p.set(c, u, y, 1, st);
			}
			// [depth] piers stand a block proud of the wall, capped with stairs
			if (pier) {
				for (int y = 1; y <= 6; y++) {
					p.set(c, u, y, 0, pier(p, u, y, 0));
				}
				p.set(c, u, 7, 0, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
			}
			// [depth] a stone sill under every window
			if (!pier && !bay) {
				p.set(c, u, 1, 0, slab(Blocks.POLISHED_DEEPSLATE_SLAB, true));
			}
		}
		// [finish] a gabled hood over the door: beam, a row of stairs and two hanging lanterns
		for (int u = cu - 1; u <= cu + 1; u++) {
			p.set(c, u, 4, 0, log(Blocks.CHERRY_LOG, Direction.Axis.X));
			p.set(c, u, 5, 0, stair(Blocks.CHERRY_STAIRS, Direction.SOUTH, false));
		}
		p.put(c, cu - 1, 3, 0, lantern(true), CitadelCanvas.HANGING);
		p.put(c, cu + 1, 3, 0, lantern(true), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ long walls (u = 1 and w-2)

	private static void sideWalls(CitadelCanvas c, Plot p, int vb) {
		int w = p.w();
		for (int side = 0; side < 2; side++) {
			int u = side == 0 ? 1 : w - 2;
			int out = side == 0 ? 0 : w - 1;
			Direction toWall = side == 0 ? Direction.EAST : Direction.WEST;
			for (int v = 2; v < vb; v++) {
				boolean pier = isSidePier(v, vb);
				for (int y = 1; y <= 8; y++) {
					BlockState st;
					if (pier) {
						st = y == 8 ? b(Blocks.CHISELED_STONE_BRICKS) : pier(p, u, y, v);
					} else if (y == 1) {
						st = base(p, u, y, v);
					} else if (y >= WIN0 && y <= 7) {
						st = glass(p, u, y, v, false); // [depth] windows are one block recessed behind the buttresses
					} else {
						st = log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Z);
					}
					p.set(c, u, y, v, st);
				}
				if (pier) {
					// [depth] buttress: stone column, sloped cap, a cherry brace under the eave
					for (int y = 1; y <= 6; y++) {
						p.set(c, out, y, v, pier(p, out, y, v));
					}
					p.set(c, out, 7, v, stair(Blocks.STONE_BRICK_STAIRS, toWall, false));
					p.set(c, out, 8, v, stair(Blocks.CHERRY_STAIRS, toWall, true));
				} else {
					p.set(c, out, 1, v, slab(Blocks.POLISHED_DEEPSLATE_SLAB, true)); // [depth] windowsill
				}
			}
		}
	}

	// ------------------------------------------------------------------ back wall (v = d-2)

	private static void backWall(CitadelCanvas c, Plot p, int cu, int vb) {
		int w = p.w();
		for (int u = 1; u <= w - 2; u++) {
			boolean pier = u == 1 || u == w - 2 || u == cu - 2 || u == cu + 2;
			for (int y = 1; y <= 8; y++) {
				BlockState st;
				if (pier) {
					st = y == 8 ? b(Blocks.CHISELED_STONE_BRICKS) : pier(p, u, y, vb);
				} else if (y == 1) {
					st = base(p, u, y, vb);
				} else if (y >= 3 && y <= 7 && Math.abs(u - cu) <= 1) {
					st = y >= 6 ? tintPane(jewel(p, u, y, vb), true) : clearPane(true); // a small window to read by
				} else if (y == 8) {
					st = log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X);
				} else {
					st = infill(p, u, y, vb);
				}
				p.set(c, u, y, vb, st);
			}
			if (pier) {
				for (int y = 1; y <= 6; y++) {
					p.set(c, u, y, vb + 1, pier(p, u, y, vb + 1));
				}
				p.set(c, u, 7, vb + 1, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.NORTH, false));
			}
		}
	}

	// ------------------------------------------------------------------ plate and tie beams (y7)

	/** [depth] a cherry top plate on every wall and tie beams across the nave, to hang the lanterns from. */
	private static void frieze(CitadelCanvas c, Plot p, int vb) {
		int w = p.w();
		for (int u = 1; u <= w - 2; u++) {
			p.set(c, u, PLATE, 1, log(Blocks.CHERRY_LOG, Direction.Axis.X));
			p.set(c, u, PLATE, vb, log(Blocks.CHERRY_LOG, Direction.Axis.X));
		}
		for (int v = 1; v <= vb; v++) {
			p.set(c, 1, PLATE, v, log(Blocks.CHERRY_LOG, Direction.Axis.Z));
			p.set(c, w - 2, PLATE, v, log(Blocks.CHERRY_LOG, Direction.Axis.Z));
		}
		for (int v : tieRows(vb)) {
			for (int u = 2; u <= w - 3; u++) {
				p.set(c, u, PLATE, v, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
			}
		}
	}

	private static int[] tieRows(int vb) {
		return new int[] {4, (1 + vb) / 2 + 1, vb - 3};
	}

	// ------------------------------------------------------------------ roof

	private static int roofY(int u, int w) {
		return PLATE + Math.min(u, w - 1 - u);
	}

	/**
	 * [finish] a steep gable roof over the nave: cherry stairs on the whole perimeter (eave and rakes), deepslate
	 * tiles inside, a cap of slabs, a glazed ridge lantern (monitor) and stained skylights on both slopes.
	 */
	private static void roof(CitadelCanvas c, Plot p, Random r, int cu, int vb) {
		int w = p.w();
		int d = p.d();
		int left = (w - 1) / 2;
		int right = w / 2;
		int top = PLATE + left;
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				int y = roofY(u, w);
				boolean leftHalf = u <= left && !(u == left && left == right);
				boolean mid = left == right && u == left;
				boolean rim = v == 0 || v == d - 1 || u == 0 || u == w - 1;
				BlockState st;
				if (mid) {
					st = b(Blocks.DEEPSLATE_TILES);
				} else {
					boolean band = Math.min(u, w - 1 - u) == 3; // [palette] one pink tile stripe on each slope
					st = stair(rim || band ? Blocks.CHERRY_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS, leftHalf ? Direction.EAST : Direction.WEST, false);
				}
				p.set(c, u, y, v, st);
				// [depth] under the overhanging eaves, upside-down stairs close the underside
				if ((u == 0 || u == w - 1) && v >= 1 && v <= d - 2) {
					p.set(c, u, y - 1, v, isSidePier(v, vb) ? b(Blocks.DEEPSLATE_BRICKS) : stair(Blocks.DEEPSLATE_TILE_STAIRS, leftHalf ? Direction.WEST : Direction.EAST, true));
				}
			}
		}
		// glazed ridge lantern: panes either side of an opening, capped with slabs
		int v0 = 3;
		int v1 = d - 4;
		for (int v = v0; v <= v1; v++) {
			for (int u = left; u <= right; u++) {
				p.set(c, u, top, v, CitadelCanvas.AIR);
			}
			p.set(c, left - 1, top, v, tintPane(jewel(p, 1, 0, v / 2), false));
			p.set(c, right + 1, top, v, tintPane(jewel(p, 2, 0, v / 2), false));
			for (int u = left - 1; u <= right + 1; u++) {
				p.set(c, u, top + 1, v, u == left - 1 || u == right + 1 ? stair(Blocks.CHERRY_STAIRS, u == left - 1 ? Direction.EAST : Direction.WEST, false) : slab(Blocks.CHERRY_SLAB, false));
			}
		}
		for (int u = left - 1; u <= right + 1; u++) {
			// lantern end walls and ridge ends
			p.set(c, u, top, v0 - 1, u == left - 1 || u == right + 1 ? b(Blocks.DEEPSLATE_TILES) : b(Blocks.CHERRY_PLANKS));
			p.set(c, u, top, v1 + 1, u == left - 1 || u == right + 1 ? b(Blocks.DEEPSLATE_TILES) : b(Blocks.CHERRY_PLANKS));
		}
		for (int u = left; u <= right; u++) {
			for (int v = 0; v < d; v++) {
				if (v < v0 - 1 || v > v1 + 1) {
					p.set(c, u, top + 1, v, slab(Blocks.CHERRY_SLAB, false)); // ridge cap outside the lantern
				}
			}
		}
		// [depth] stained skylights (claraboyas): two panels per slope, glass cubes replacing roof stairs
		for (int step = 0; step < 2; step++) {
			int ul = left - 2 - step;
			int ur = right + 2 + step;
			for (int pass = 0; pass < 2; pass++) {
				int va = pass == 0 ? v0 + 1 : v1 - 2;
				for (int v = va; v <= va + 1; v++) {
					DyeColor col = jewel(p, step, pass, v);
					if (ul >= 1) {
						p.set(c, ul, roofY(ul, w), v, tintBlock(col));
					}
					if (ur <= w - 2) {
						p.set(c, ur, roofY(ur, w), v, tintBlock(col));
					}
				}
			}
		}
		// finials: a lightning rod on each gable peak
		p.set(c, left, top + 2, 0, b(Blocks.LIGHTNING_ROD.waxed().unaffected()));
		p.set(c, left, top + 2, d - 1, b(Blocks.LIGHTNING_ROD.waxed().unaffected()));
	}

	// ------------------------------------------------------------------ gables

	/** The gable triangles of front and back walls, with a stained rose window on the front. */
	private static void gables(CitadelCanvas c, Plot p, int cu, int vb) {
		int w = p.w();
		for (int u = 2; u <= w - 3; u++) {
			for (int y = PLATE + 1; y < roofY(u, w); y++) {
				boolean rake = y == roofY(u, w) - 1;
				for (int v : new int[] {1, vb}) {
					p.set(c, u, y, v, rake ? b(Blocks.STONE_BRICKS) : infill(p, u, y, v));
				}
			}
		}
		// the rose: a plus of jewel glass in a chiselled frame, centred over the door
		int ry = PLATE + 2;
		BlockState frame = b(Blocks.CHISELED_STONE_BRICKS);
		for (int du = -1; du <= 1; du++) {
			for (int dy = -1; dy <= 1; dy++) {
				boolean corner = du != 0 && dy != 0;
				BlockState st = corner ? frame : tintBlock(du == 0 && dy == 0 ? DyeColor.ORANGE : (du == 0 ? DyeColor.PINK : DyeColor.LIGHT_BLUE));
				p.set(c, cu + du, ry + dy, 1, st);
			}
		}
		// [depth] a moulding of stairs hugging the rose
		p.set(c, cu, ry + 2, 0 + 1, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, true));
		for (int du : new int[] {-2, 2}) {
			p.set(c, cu + du, ry, 1, stair(Blocks.STONE_BRICK_STAIRS, du < 0 ? Direction.WEST : Direction.EAST, false));
		}
		// back gable: a smaller round light
		p.set(c, cu, PLATE + 3, vb, tintBlock(DyeColor.YELLOW));
	}

	// ------------------------------------------------------------------ interior (seen through the windows)

	private static void interior(CitadelCanvas c, Plot p, Random r, int cu, int vb, boolean shelfLeft) {
		int w = p.w();
		BlockState shelf = b(Blocks.BOOKSHELF);
		for (int v = 2; v < vb; v++) {
			for (int side = 0; side < 2; side++) {
				int u = side == 0 ? 2 : w - 3;
				boolean chisel = (side == 0) == shelfLeft && p.hash(u, 1, v, 3) == 0;
				for (int y = 1; y <= 3; y++) {
					p.set(c, u, y, v, chisel && y == 2 ? facing(Blocks.CHISELED_BOOKSHELF, side == 0 ? Direction.EAST : Direction.WEST) : shelf);
				}
				// reading desks in front of the shelves, a stool each
				if ((v - 2) % 3 == 1) {
					int du = side == 0 ? 3 : w - 4;
					p.set(c, du, 1, v, facing(Blocks.LECTERN, side == 0 ? Direction.WEST : Direction.EAST));
					p.set(c, du + (side == 0 ? 1 : -1), 1, v, stair(Blocks.SPRUCE_STAIRS, side == 0 ? Direction.WEST : Direction.EAST, false));
				}
			}
		}
		// a long copying table down the nave, with a lantern lamp each third
		for (int v = 4; v <= vb - 3; v++) {
			p.set(c, cu, 1, v, slab(Blocks.SPRUCE_SLAB, true));
			if ((v - 4) % 3 == 1) {
				p.set(c, cu, 2, v, lantern(false));
			}
		}
		// the back wall: floor-to-rafter shelves, a chiselled band halfway
		for (int u = 2; u <= w - 3; u++) {
			for (int y = 1; y <= 7; y++) {
				if (Math.abs(u - cu) <= 1 && y >= 3) {
					continue; // leave the back window clear
				}
				p.set(c, u, y, vb - 1, y == 3 && p.hash(u, y, 0, 2) == 0 ? facing(Blocks.CHISELED_BOOKSHELF, Direction.NORTH) : shelf);
			}
		}
		// chandeliers hang from the tie beams
		for (int v : tieRows(vb)) {
			p.put(c, cu, PLATE - 1, v, lantern(true), CitadelCanvas.HANGING);
			p.put(c, 3, PLATE - 1, v, lantern(true), CitadelCanvas.HANGING);
			p.put(c, w - 4, PLATE - 1, v, lantern(true), CitadelCanvas.HANGING);
		}
	}

	// ------------------------------------------------------------------ yard

	/** [finish] steps, jardinières between the piers, and a stone kerb: where the building meets the ground. */
	private static void frontYard(CitadelCanvas c, Plot p, int cu) {
		int w = p.w();
		p.set(c, cu, 0, 0, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
		p.set(c, cu - 1, 0, 0, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
		p.set(c, cu + 1, 0, 0, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
		for (int u = 2; u <= w - 3; u++) {
			if (isFrontPier(u, w, cu) || Math.abs(u - cu) <= 1) {
				continue;
			}
			p.set(c, u, 0, 0, b(Blocks.MOSS_BLOCK));
			p.set(c, u, 1, 0, p.hash(u, 0, 0, 2) == 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.AZALEA));
		}
	}

	/** Behind the apse: book barrels, a composter and a lantern post, by seed. */
	private static void backYard(CitadelCanvas c, Plot p, Random r, int cu, int vb) {
		int w = p.w();
		int back = p.d() - 1;
		for (int u = 2; u <= w - 3; u++) {
			if (Math.abs(u - cu) <= 1) {
				continue;
			}
			switch (p.hash(u, 0, back, 5)) {
				case 0 -> p.set(c, u, 0, back, barrel());
				case 1 -> p.set(c, u, 0, back, b(Blocks.COMPOSTER));
				case 2 -> p.set(c, u, 0, back, b(Blocks.POTTED_FERN));
				default -> {
				}
			}
		}
		int lx = r.nextBoolean() ? 1 : w - 2;
		p.set(c, lx, 0, back, b(Blocks.COBBLED_DEEPSLATE_WALL));
		p.set(c, lx, 1, back, lantern(false));
	}
}
