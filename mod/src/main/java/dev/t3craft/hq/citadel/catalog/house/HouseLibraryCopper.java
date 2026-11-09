package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.clearPane;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.facing;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.fence;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.lantern;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.log;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.slab;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.stair;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.tintPane;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A working library under a copper roof: a basilica with a double-height reading room, a gallery of shelves on
 * three sides, consultation tables with lecterns and chairs, an enchanting sanctum ringed by shelves, archive
 * chests by the door, and a copper hip roof with ribs that carries a glazed lantern.
 *
 * <p>Concept: a Palladian reading hall (Boston Athenaeum, the Wren library) in the citadel palette. Dark deepslate
 * plinth, stone-brick walls with calcite pilasters between tall mullioned windows (windows and blind shelf bays
 * alternate), a loggia of quartz columns on the front, and clean waxed copper (orange, with lighter ribs) rather than
 * green patina. The lantern on the roof lights the nave.
 *
 * <pre>
 *   y16-19   lantern (glass, copper cap, gold boss, rod)      plan (front = v0 at the top):
 *   y11-15   hip roof of cut-copper stairs, ribs                 loggia (columns, steps)
 *   y10      cornice, beams over the nave                        foyer: archive chests, door
 *   y7-9     gallery level, upper windows                        flights: 2 wide, landing, 3 + 2 + 3 steps
 *   y6       gallery floor                                       nave: tables, lecterns, chairs
 *   y1-5     ground floor, shelves, window seats                 sanctum: enchanting table in a shelf ring
 *   y0       floor (stone nave, wood aisles)
 * </pre>
 *
 * Aisles are 3 wide or more everywhere, the stairs are 2 wide with a landing and open air above, the gallery has
 * a 3-wide walk with a rail. Size: 17–23 × 21–29 × 20+.
 */
public final class HouseLibraryCopper implements Variant {
	@Override
	public String id() {
		return "house_library_copper";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 17;
	}

	@Override
	public int minD() {
		return 21;
	}

	@Override
	public int minH() {
		return 22;
	}

	@Override
	public int maxW() {
		return 23;
	}

	@Override
	public int maxD() {
		return 29;
	}

	private static final DyeColor[] JEWELS = {DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIGHT_BLUE, DyeColor.PINK};
	/** Rings of sloped roof before the flat top. */
	private static final int K = 4;

	// ------------------------------------------------------------------ geometry

	/**
	 * Measures of the plot. The body is {@code bu0..bu1} wide (odd, so it has a centre line {@code cu}) and
	 * {@code v0..v1} deep, with a loggia in front ({@code v 0..2}). {@code hw} is the half width: the shelf column
	 * is at {@code |u - cu| = hw - 1}, the 3-wide walk at {@code hw - 4 .. hw - 2}, the gallery rail at
	 * {@code hw - 5}, and the nave inside {@code |u - cu| <= hw - 6}.
	 */
	private record G(int w, int d, int bu0, int bu1, int cu, int hw, int v0, int v1, int iv0, int iv1, int sv, int vs) {
		static G of(Plot p) {
			int w = p.w();
			int d = p.d();
			int bw = ((w - 2) & 1) == 1 ? w - 2 : w - 3;
			int bu0 = 1;
			int bu1 = bu0 + bw - 1;
			int hw = (bw - 1) / 2;
			int cu = bu0 + hw;
			int v0 = 3;
			int vlen = ((d - 4) & 1) == 1 ? d - 4 : d - 5;
			int v1 = v0 + vlen - 1;
			int iv0 = v0 + 1;
			int iv1 = v1 - 1;
			return new G(w, d, bu0, bu1, cu, hw, v0, v1, iv0, iv1, iv1 - 2, iv0 + 3);
		}

		/** Ring of the roof over a cell: 0 on the eave, growing inwards. */
		int ring(int u, int v) {
			return Math.min(Math.min(u - (bu0 - 1), bu1 + 1 - u), Math.min(v, v1 + 1 - v));
		}

		/** Whether the gallery floor (layer 6) covers this interior cell. */
		boolean gal(int u, int v) {
			int ru = Math.abs(u - cu);
			return v <= iv1 && ((ru >= hw - 5 && ru <= hw - 1 && v >= vs + 8) || (ru <= hw - 5 && v >= iv1 - 4));
		}
	}

	/** The rhythm of a wall: pilasters and bays between them, bays alternating windows and shelves. */
	private static final class Wall {
		final int len;
		final int n;
		final boolean[] pier;
		final boolean[] win;
		final int[] bay;
		final int[] off;
		final int[] width;

		Wall(int len) {
			this.len = len;
			this.n = len >= 25 ? 5 : 3;
			int inner = len - (n + 1);
			int base = inner / n;
			if ((base & 1) == 0) {
				base--;
			}
			if (base < 3) {
				base = 3;
			}
			int[] ws = new int[n];
			Arrays.fill(ws, base);
			int units = Math.max(0, (inner - n * base) / 2);
			int c = n / 2;
			int[] order = new int[n];
			order[0] = c;
			for (int k = 1; k < n; k++) {
				int dl = (k + 1) / 2;
				order[k] = (k % 2 == 1) ? c - dl : c + dl;
			}
			for (int t = 0; t < units; t++) {
				ws[order[t % n]] += 2;
			}
			pier = new boolean[len];
			win = new boolean[len];
			bay = new int[len];
			off = new int[len];
			width = new int[len];
			int i = 0;
			pier[i++] = true;
			for (int j = 0; j < n; j++) {
				for (int o = 0; o < ws[j] && i < len; o++) {
					bay[i] = j;
					off[i] = o;
					width[i] = ws[j];
					win[i] = ((j - c) & 1) != 0;
					i++;
				}
				if (i < len) {
					pier[i++] = true;
				}
			}
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		G g = G.of(p);
		Wall side = new Wall(g.v1() - g.v0() + 1);
		Wall span = new Wall(g.bu1() - g.bu0() + 1);
		floor(c, p, g);
		walls(c, p, g, side, span);
		loggia(c, p, g, span);
		gallery(c, p, g);
		stairs(c, p, g);
		furnish(c, p, g, side, span);
		sanctum(c, p, g);
		tables(c, p, g);
		rails(c, p, g);
		beams(c, p, g);
		roof(c, p, g);
		monitor(c, p, g);
	}

	// ------------------------------------------------------------------ materials

	private static BlockState plinth(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 6)) {
			case 0 -> b(Blocks.COBBLED_DEEPSLATE);
			case 1 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
			default -> b(Blocks.DEEPSLATE_BRICKS);
		};
	}

	private static BlockState pierStone(Plot p, int u, int y, int v) {
		return p.hash(u, y, v, 5) == 0 ? b(Blocks.STONE_BRICKS) : b(Blocks.CALCITE);
	}

	private static BlockState wallStone(Plot p, int u, int y, int v) {
		return p.hash(u, y, v, 6) == 0 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
	}

	private static BlockState jewelPane(Plot p, int u, int y, int v, boolean alongU) {
		return tintPane(JEWELS[p.hash(u, y, v, JEWELS.length)], alongU);
	}

	private static Block copper(int level) {
		return switch (level) {
			case 0 -> Blocks.CUT_COPPER.waxed().unaffected();
			case 1 -> Blocks.CUT_COPPER.waxed().exposed();
			case 2 -> Blocks.CUT_COPPER.waxed().weathered();
			default -> Blocks.CUT_COPPER.waxed().oxidized();
		};
	}

	private static Block copperStair(int level) {
		return switch (level) {
			case 0 -> Blocks.CUT_COPPER_STAIRS.waxed().unaffected();
			case 1 -> Blocks.CUT_COPPER_STAIRS.waxed().exposed();
			case 2 -> Blocks.CUT_COPPER_STAIRS.waxed().weathered();
			default -> Blocks.CUT_COPPER_STAIRS.waxed().oxidized();
		};
	}

	private static Block chiseled(int level) {
		return switch (level) {
			case 0 -> Blocks.CHISELED_COPPER.waxed().unaffected();
			case 1 -> Blocks.CHISELED_COPPER.waxed().exposed();
			case 2 -> Blocks.CHISELED_COPPER.waxed().weathered();
			default -> Blocks.CHISELED_COPPER.waxed().oxidized();
		};
	}

	private static BlockState lectern(Direction dir) {
		return facing(Blocks.LECTERN, dir).setValue(BlockStateProperties.HAS_BOOK, true);
	}

	// ------------------------------------------------------------------ floor

	private static void floor(CitadelCanvas c, Plot p, G g) {
		for (int v = g.v0(); v <= g.v1(); v++) {
			for (int u = g.bu0(); u <= g.bu1(); u++) {
				boolean inside = u > g.bu0() && u < g.bu1() && v > g.v0() && v < g.v1();
				BlockState st;
				if (!inside) {
					boolean door = v == g.v0() && Math.abs(u - g.cu()) <= 1;
					st = door ? b(Blocks.POLISHED_ANDESITE) : plinth(p, u, 0, v);
				} else {
					st = interiorFloor(p, g, u, v);
				}
				p.set(c, u, 0, v, st);
			}
		}
	}

	private static BlockState interiorFloor(Plot p, G g, int u, int v) {
		int ru = Math.abs(u - g.cu());
		if (v < g.iv0() + 3) {
			return p.hash(u, 0, v, 4) == 0 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
		}
		if (v >= g.sv() - 2 && ru <= 2) {
			return ru <= 1 && Math.abs(v - g.sv()) <= 1 ? b(Blocks.POLISHED_DIORITE) : b(Blocks.DEEPSLATE_TILES);
		}
		if (ru <= g.hw() - 5) {
			return ((u + v) & 1) == 0 ? b(Blocks.POLISHED_DIORITE) : b(Blocks.POLISHED_ANDESITE);
		}
		return p.hash(u, 0, v, 5) == 0 ? b(Blocks.SPRUCE_PLANKS) : b(Blocks.DARK_OAK_PLANKS);
	}

	// ------------------------------------------------------------------ walls

	private static void walls(CitadelCanvas c, Plot p, G g, Wall side, Wall span) {
		for (int i = 0; i < side.len; i++) {
			int v = g.v0() + i;
			wallCell(c, p, g, side, i, g.bu0(), v, Direction.WEST, false);
			wallCell(c, p, g, side, i, g.bu1(), v, Direction.EAST, false);
		}
		for (int i = 0; i < span.len; i++) {
			int u = g.bu0() + i;
			wallCell(c, p, g, span, i, u, g.v0(), Direction.NORTH, true);
			wallCell(c, p, g, span, i, u, g.v1(), Direction.SOUTH, false);
		}
		pierStack(c, p, g.bu0() - 1, g.v1() + 1);
		pierStack(c, p, g.bu1() + 1, g.v1() + 1);
	}

	/** A pilaster standing a block proud of a wall: dark foot, calcite shaft, chiselled capital. */
	private static void pierStack(CitadelCanvas c, Plot p, int u, int v) {
		for (int y = 1; y <= 10; y++) {
			BlockState st;
			if (y <= 2) {
				st = plinth(p, u, y, v);
			} else if (y == 10 || y == 6) {
				st = b(Blocks.CHISELED_STONE_BRICKS);
			} else {
				st = pierStone(p, u, y, v);
			}
			p.set(c, u, y, v, st);
		}
	}

	private static void wallCell(CitadelCanvas c, Plot p, G g, Wall wl, int i, int u, int v, Direction out, boolean front) {
		int top = 10 + g.ring(u, v);
		boolean alongU = out == Direction.NORTH || out == Direction.SOUTH;
		boolean pier = wl.pier[i];
		boolean doorBay = front && !pier && wl.bay[i] == wl.n / 2;
		boolean door = doorBay && Math.abs(wl.off[i] - wl.width[i] / 2) <= 1;
		boolean win = wl.win[i] && !doorBay;
		boolean mull = !pier && wl.off[i] == wl.width[i] / 2;
		for (int y = 1; y <= top; y++) {
			BlockState st;
			if (door && y <= 5) {
				st = CitadelCanvas.AIR;
			} else if (y == 1) {
				st = plinth(p, u, y, v);
			} else if (y == 2) {
				st = pier ? plinth(p, u, y, v) : b(Blocks.POLISHED_DEEPSLATE);
			} else if (door && y == 6) {
				st = b(chiseled(0));
			} else if (door && y <= 9) {
				st = jewelPane(p, u, y, v, alongU);
			} else if (pier) {
				st = y == 10 ? b(Blocks.CHISELED_STONE_BRICKS) : (y > 10 ? wallStone(p, u, y, v) : pierStone(p, u, y, v));
			} else if (y == 6 || y == 10) {
				st = b(Blocks.POLISHED_ANDESITE);
			} else if (y > 10) {
				st = wallStone(p, u, y, v);
			} else if (y <= 5) {
				if (win) {
					st = mull ? b(Blocks.CALCITE) : clearPane(alongU);
				} else {
					st = mull && y == 4 ? b(Blocks.CHISELED_STONE_BRICKS) : wallStone(p, u, y, v);
				}
			} else if (win && mull) {
				st = b(Blocks.CALCITE);
			} else if (y == 9) {
				st = jewelPane(p, u, y, v, alongU);
			} else if (win) {
				st = clearPane(alongU);
			} else {
				st = wallStone(p, u, y, v);
			}
			p.set(c, u, y, v, st);
		}
		int ou = u + out.getStepX();
		int ov = v + out.getStepZ();
		if (pier) {
			pierStack(c, p, ou, ov);
			return;
		}
		if (front) {
			return;
		}
		Direction in = out.getOpposite();
		p.set(c, ou, 6, ov, stair(Blocks.STONE_BRICK_STAIRS, in, true)); // string course
		p.set(c, ou, 10, ov, stair(Blocks.STONE_BRICK_STAIRS, in, true)); // cornice
		if (win) {
			// a solid hedge under every window, with a stone sill on it (nothing low to walk under)
			p.set(c, ou, 0, ov, CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			p.set(c, ou, 1, ov, p.hash(u, 1, v, 3) == 0 ? b(Blocks.MOSS_BLOCK) : CitadelCanvas.leaves(Blocks.AZALEA_LEAVES));
			p.set(c, ou, 2, ov, slab(Blocks.POLISHED_ANDESITE_SLAB, true));
		} else {
			p.set(c, ou, 0, ov, slab(Blocks.POLISHED_DEEPSLATE_SLAB, false)); // kerb
		}
	}

	// ------------------------------------------------------------------ loggia

	private static void loggia(CitadelCanvas c, Plot p, G g, Wall span) {
		int ua = g.bu0() - 1;
		int ub = g.bu1() + 1;
		boolean[] col = new boolean[ub - ua + 1];
		for (int i = 0; i < span.len; i++) {
			if (!span.pier[i]) {
				continue;
			}
			int u = g.bu0() + i;
			if (i == 0) {
				u = ua;
			} else if (i == span.len - 1) {
				u = ub;
			}
			col[u - ua] = true;
		}
		for (int u = ua; u <= ub; u++) {
			for (int v = 0; v <= 2; v++) {
				BlockState st;
				if (v == 0) {
					st = col[u - ua] ? b(Blocks.STONE_BRICKS) : stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false);
				} else if (Math.abs(u - g.cu()) <= 1) {
					st = b(Blocks.POLISHED_ANDESITE);
				} else {
					st = p.hash(u, 0, v, 4) == 0 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
				}
				p.set(c, u, 0, v, st);
			}
			if (col[u - ua]) {
				column(c, p, u, 0);
			} else {
				p.set(c, u, 10, 0, b(Blocks.POLISHED_ANDESITE)); // architrave
			}
		}
		for (int u : new int[] {ua, ub}) {
			column(c, p, u, 2);
			p.set(c, u, 10, 1, b(Blocks.POLISHED_ANDESITE));
		}
		for (int i = 0; i < span.len; i++) {
			if (!span.pier[i] && span.off[i] == span.width[i] / 2) {
				p.put(c, g.bu0() + i, 9, 0, lantern(true), CitadelCanvas.HANGING);
			}
		}
	}

	private static void column(CitadelCanvas c, Plot p, int u, int v) {
		p.set(c, u, 1, v, b(Blocks.POLISHED_DEEPSLATE));
		for (int y = 2; y <= 9; y++) {
			p.set(c, u, y, v, b(Blocks.QUARTZ_PILLAR));
		}
		p.set(c, u, 10, v, b(Blocks.CHISELED_STONE_BRICKS));
	}

	// ------------------------------------------------------------------ gallery, stairs, pillars

	private static void gallery(CitadelCanvas c, Plot p, G g) {
		for (int v = g.iv0(); v <= g.iv1(); v++) {
			for (int u = g.bu0() + 1; u <= g.bu1() - 1; u++) {
				if (g.gal(u, v)) {
					p.set(c, u, 6, v, p.hash(u, 6, v, 5) == 0 ? b(Blocks.DARK_OAK_PLANKS) : b(Blocks.SPRUCE_PLANKS));
				}
			}
		}
		// pillars under the gallery rail, every 4 rows, and under the back rail
		Set<Integer> rows = new HashSet<>();
		for (int r = g.vs() + 8; r <= g.iv1(); r += 4) {
			rows.add(r);
		}
		rows.add(g.iv1() - 4);
		for (int r : rows) {
			for (int s = -1; s <= 1; s += 2) {
				int u = g.cu() + s * (g.hw() - 5);
				p.set(c, u, 1, r, b(Blocks.POLISHED_DEEPSLATE));
				for (int y = 2; y <= 4; y++) {
					p.set(c, u, y, r, b(Blocks.CALCITE));
				}
				p.set(c, u, 5, r, b(Blocks.CHISELED_STONE_BRICKS));
			}
		}
	}

	/**
	 * Two flights, one each side, 2 wide: 3 steps, a 2-long landing, 3 steps, the first step met head-on from the
	 * foyer and the last one stepping onto the gallery floor. The air over the flight is open (the gallery floor
	 * starts after it).
	 */
	private static void stairs(CitadelCanvas c, Plot p, G g) {
		for (int s = -1; s <= 1; s += 2) {
			for (int k = 0; k < 2; k++) {
				int u = g.cu() + s * (g.hw() - 2 - k);
				for (int j = 0; j < 8; j++) {
					int v = g.vs() + j;
					boolean landing = j == 3 || j == 4;
					int layer = j < 3 ? 1 + j : (landing ? 3 : j - 1);
					int fill = landing ? 3 : layer - 1;
					for (int y = 1; y <= fill; y++) {
						p.set(c, u, y, v, b(Blocks.DARK_OAK_PLANKS));
					}
					if (landing) {
						p.set(c, u, 3, v, b(Blocks.DARK_OAK_PLANKS));
					} else {
						p.set(c, u, layer, v, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ shelves and seats along the walls

	private static void furnish(CitadelCanvas c, Plot p, G g, Wall side, Wall span) {
		for (int s = -1; s <= 1; s += 2) {
			int u = s < 0 ? g.bu0() + 1 : g.bu1() - 1;
			Direction in = s < 0 ? Direction.EAST : Direction.WEST;
			for (int v = g.iv0(); v <= g.iv1(); v++) {
				int i = v - g.v0();
				boolean shelf = side.pier[i] || !side.win[i];
				boolean mid = !side.pier[i] && side.off[i] == side.width[i] / 2;
				colStack(c, p, g, u, v, in, shelf, v < g.iv0() + 3, mid);
			}
		}
		for (int u = g.bu0() + 2; u <= g.bu1() - 2; u++) {
			if (Math.abs(u - g.cu()) <= 2) {
				continue;
			}
			int i = u - g.bu0();
			boolean shelf = span.pier[i] || !span.win[i];
			boolean mid = !span.pier[i] && span.off[i] == span.width[i] / 2;
			colStack(c, p, g, u, g.iv1(), Direction.NORTH, shelf, false, mid);
		}
	}

	/** The inner face of a wall cell: a shelf stack up to the roof, or a window seat (and a gallery seat). */
	private static void colStack(CitadelCanvas c, Plot p, G g, int u, int v, Direction in, boolean shelf, boolean foyer, boolean mid) {
		int top = 10 + g.ring(u, v);
		if (shelf) {
			for (int y = 1; y <= top; y++) {
				BlockState st;
				if (y == 1 && (foyer || mid)) {
					st = facing(Blocks.CHEST, in);
				} else if ((y == 2 && foyer) || (y >= 3 && p.hash(u, y, v, 5) == 0)) {
					st = facing(Blocks.CHISELED_BOOKSHELF, in);
				} else {
					st = b(Blocks.BOOKSHELF);
				}
				p.set(c, u, y, v, st);
			}
		} else {
			p.set(c, u, 1, v, foyer ? facing(Blocks.CHEST, in) : stair(Blocks.DARK_OAK_STAIRS, in, false));
			if (!foyer && g.gal(u, v)) {
				p.set(c, u, 7, v, stair(Blocks.DARK_OAK_STAIRS, in, false));
			}
		}
	}

	// ------------------------------------------------------------------ enchanting sanctum

	/**
	 * The enchanting table in the middle of a 5 × 5 ring of shelves (two high at least, which is what the table reads),
	 * one block of air round it, and a way in from the front. The gallery floor covers it: a quiet room under a low roof.
	 */
	private static void sanctum(CitadelCanvas c, Plot p, G g) {
		int cu = g.cu();
		int sv = g.sv();
		for (int du = -2; du <= 2; du++) {
			for (int dv = -2; dv <= 2; dv++) {
				if (Math.max(Math.abs(du), Math.abs(dv)) != 2) {
					continue;
				}
				Direction in = Math.abs(dv) == 2 ? (dv < 0 ? Direction.SOUTH : Direction.NORTH) : (du < 0 ? Direction.EAST : Direction.WEST);
				boolean gap = du == 0 && dv == -2;
				for (int y = 1; y <= 5; y++) {
					BlockState st;
					if (gap) {
						st = y == 5 ? facing(Blocks.CHISELED_BOOKSHELF, Direction.SOUTH) : CitadelCanvas.AIR;
					} else if (y >= 3 && p.hash(cu + du, y, sv + dv, 4) == 0) {
						st = facing(Blocks.CHISELED_BOOKSHELF, in);
					} else {
						st = b(Blocks.BOOKSHELF);
					}
					p.set(c, cu + du, y, sv + dv, st);
				}
			}
		}
		p.set(c, cu, 1, sv, b(Blocks.ENCHANTING_TABLE));
		p.put(c, cu, 5, sv, lantern(true), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ consultation tables

	private static void tables(CitadelCanvas c, Plot p, G g) {
		int cu = g.cu();
		int nave = 2 * (g.hw() - 6) + 1;
		int tw = Math.max(1, Math.min(5, nave - 2));
		int half = tw / 2;
		for (int ts = g.iv0() + 3; ts + 2 <= g.sv() - 6; ts += 6) {
			for (int r = 0; r < 3; r++) {
				for (int k = -half; k <= half; k++) {
					p.set(c, cu + k, 1, ts + r, slab(Blocks.SPRUCE_SLAB, false));
				}
			}
			p.set(c, cu, 1, ts + 1, lectern(Direction.NORTH));
			for (int s = -1; s <= 1; s += 2) {
				int u = cu + s * (half + 1);
				Direction away = s < 0 ? Direction.WEST : Direction.EAST;
				p.set(c, u, 1, ts, stair(Blocks.DARK_OAK_STAIRS, away, false));
				p.set(c, u, 1, ts + 2, stair(Blocks.DARK_OAK_STAIRS, away, false));
				p.set(c, u, 1, ts + 1, lectern(away));
			}
		}
	}

	// ------------------------------------------------------------------ gallery rail

	private static void rails(CitadelCanvas c, Plot p, G g) {
		Set<Long> rail = new HashSet<>();
		int rc = g.hw() - 5;
		for (int s = -1; s <= 1; s += 2) {
			for (int v = g.vs() + 8; v <= g.iv1(); v++) {
				rail.add(key(g.cu() + s * rc, v));
			}
			rail.add(key(g.cu() + s * (g.hw() - 4), g.vs() + 8));
		}
		for (int k = -rc; k <= rc; k++) {
			rail.add(key(g.cu() + k, g.iv1() - 4));
		}
		for (long k : rail) {
			int u = (int) (k >> 16);
			int v = (int) (k & 0xFFFF);
			p.set(c, u, 7, v, fence(Blocks.DARK_OAK_FENCE, rail.contains(key(u, v - 1)), rail.contains(key(u + 1, v)),
					rail.contains(key(u, v + 1)), rail.contains(key(u - 1, v))));
		}
		// lanterns on the rail posts above each pillar
		for (int r = g.vs() + 8; r <= g.iv1(); r += 4) {
			for (int s = -1; s <= 1; s += 2) {
				p.set(c, g.cu() + s * rc, 8, r, lantern(false));
			}
		}
		// lanterns under the gallery over the walks
		for (int r = g.vs() + 9; r <= g.iv1(); r += 4) {
			for (int s = -1; s <= 1; s += 2) {
				p.put(c, g.cu() + s * (g.hw() - 3), 5, r, lantern(true), CitadelCanvas.HANGING);
			}
		}
	}

	private static long key(int u, int v) {
		return ((long) u << 16) | v;
	}

	// ------------------------------------------------------------------ beams over the nave

	private static void beams(CitadelCanvas c, Plot p, G g) {
		int rc = g.hw() - 5;
		for (int r = g.iv0() + 1; r <= g.iv1() - 5; r += 4) {
			for (int u = g.cu() - rc; u <= g.cu() + rc; u++) {
				p.set(c, u, 10, r, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
			}
			p.put(c, g.cu(), 9, r, lantern(true), CitadelCanvas.HANGING);
		}
	}

	// ------------------------------------------------------------------ roof

	/**
	 * [finish] a hip roof of waxed cut-copper stairs over a stone cornice: bright orange (new copper) with lighter
	 * exposed ribs every 4 blocks and along the hips, and a flat crown of cut copper with ribs, carrying the lantern.
	 */
	private static void roof(CitadelCanvas c, Plot p, G g) {
		for (int u = g.bu0() - 1; u <= g.bu1() + 1; u++) {
			for (int v = 0; v <= g.v1() + 1; v++) {
				int west = u - (g.bu0() - 1);
				int east = g.bu1() + 1 - u;
				int north = v;
				int south = g.v1() + 1 - v;
				int da = Math.min(west, east);
				int db = Math.min(north, south);
				int k = Math.min(da, db);
				if (k >= K) {
					p.set(c, u, 11 + K, v, b(copper(Math.floorMod(u - g.cu(), 4) == 0 ? 1 : 0)));
					continue;
				}
				Direction face;
				int along;
				if (da <= db) {
					face = west <= east ? Direction.EAST : Direction.WEST;
					along = v;
				} else {
					face = north <= south ? Direction.SOUTH : Direction.NORTH;
					along = u - g.cu();
				}
				int level = da == db ? 2 : (Math.floorMod(along, 4) == 0 ? 1 : 0);
				p.set(c, u, 11 + k, v, stair(copperStair(level), face, false));
			}
		}
	}

	/** The lantern on the crown: glass on posts, a copper cap, a gold boss and a rod; open to the nave below. */
	private static void monitor(CitadelCanvas c, Plot p, G g) {
		int ml = Math.max(1, g.hw() - 4);
		int mu0 = g.cu() - ml;
		int mu1 = g.cu() + ml;
		int mv0 = 5;
		int mv1 = g.v1() - 4;
		for (int u = mu0; u <= mu1; u++) {
			for (int v = mv0; v <= mv1; v++) {
				p.set(c, u, 11 + K, v, CitadelCanvas.AIR);
				boolean eu = u == mu0 || u == mu1;
				boolean ev = v == mv0 || v == mv1;
				if (!eu && !ev) {
					continue;
				}
				for (int y = 16; y <= 18; y++) {
					BlockState st;
					boolean post = (eu && ev) || (eu && (v - mv0) % 3 == 0) || (ev && (u - mu0) % 3 == 0);
					if (post) {
						st = b(copper(1));
					} else {
						st = clearPane(ev);
					}
					p.set(c, u, y, v, st);
				}
			}
		}
		// the cap: two courses of stairs stepping in, then a flat top; no overhang (nothing to stand under)
		for (int u = mu0; u <= mu1; u++) {
			for (int v = mv0; v <= mv1; v++) {
				int ring = Math.min(Math.min(u - mu0, mu1 - u), Math.min(v - mv0, mv1 - v));
				if (ring >= 2) {
					p.set(c, u, 20, v, b(copper(0)));
					continue;
				}
				Direction face;
				if (u == mu0 + ring) {
					face = Direction.EAST;
				} else if (u == mu1 - ring) {
					face = Direction.WEST;
				} else if (v == mv0 + ring) {
					face = Direction.SOUTH;
				} else {
					face = Direction.NORTH;
				}
				p.set(c, u, 19 + ring, v, stair(copperStair(ring == 0 ? 0 : 1), face, false));
				if (ring == 0) {
					p.set(c, u, 19, v, stair(copperStair(0), face, false));
				}
			}
		}
		int vm = (mv0 + mv1) / 2;
		p.set(c, g.cu(), 20, vm, b(Blocks.GOLD_BLOCK));
		p.set(c, g.cu(), 21, vm, b(Blocks.LIGHTNING_ROD.waxed().oxidized()));
	}
}
