package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.door;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.fence;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.gable;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.groundPlant;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.lantern;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.log;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.lowWall;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.mix;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.pane;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.stair;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.trap;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.Arrays;
import java.util.Random;
import java.util.function.IntPredicate;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * A corner timber-frame house, built for people to walk in. Two gabled wings meet at a corner mirador tower:
 * the front wing (along the street) is a shop below and a dwelling above, the side wing (along the side street)
 * a workshop below and a study above, and the tower is the entrance hall below and a glazed salon above under a
 * copper pyramid. Every storey has 4 free blocks; the tower and each wing are joined by 3-wide arches.
 *
 * <pre>
 *  plan (v down, front = top)       u 0 .... 9 10 ...... ue+1
 *  v 0-9        tower T (u1-9)     T T T | A A A A A A      A = front wing  (ridge along u), shop / dwelling
 *  v 10-vb+1    side wing B        B B B | garden           B = side wing   (ridge along v), workshop / study
 * </pre>
 *
 * <b>Timber-frame look.</b> Dark-oak posts and beams stand <i>proud</i> of the plaster infill, so the frame casts
 * the shadow; the stone dado is the base; the upper floor oversails the ground floor on the street sides
 * (jetty) and carries joist ends, brackets and an eave of dentils. <b>Walkability.</b> One 2-wide flight in the
 * tower (head-on first step, 2 free over every step, free arrival), no ledges or recesses a person could stand
 * on under an eave, arches 3 wide and 3 high, doors that open to the street, the side street and the garden.
 */
public final class HouseTimberCorner implements Variant {
	private static final int BEAM = 5;
	private static final int PLATE = 10;
	/** Back and right wall line of the tower, and the depth of both wings (walls at 1 and 9). */
	private static final int T = 9;
	private static final int TC = 5;

	@Override
	public String id() {
		return "house_timber_corner";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 18;
	}

	@Override
	public int minD() {
		return 18;
	}

	@Override
	public int minH() {
		return 20;
	}

	@Override
	public int maxW() {
		return 20;
	}

	@Override
	public int maxD() {
		return 20;
	}

	/** [palette] two roof schemes per seed: warm mud brick or cool deepslate tile, always on a dark-oak fascia. */
	private record Scheme(Block roof, Block alt, Block eave, Block shutter) {
	}

	private static final Scheme[] SCHEMES = {
			new Scheme(Blocks.MUD_BRICK_STAIRS, Blocks.BRICK_STAIRS, Blocks.DARK_OAK_STAIRS, Blocks.CHERRY_TRAPDOOR),
			new Scheme(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DARK_OAK_STAIRS, Blocks.BAMBOO_TRAPDOOR),
	};

	/**
	 * The footprint. Upper floor: front wing u1..ue x v1..9 and side wing u1..9 x v1..vb. Ground floor: the same
	 * shape one block in on the front and left (the jetty above it), same lines on the back and right.
	 */
	private record Geo(int w, int d) {
		int ue() {
			return Math.min(w - 2, 28);
		}

		int vb() {
			return Math.min(d - 2, 28);
		}

		boolean inH(int u, int v) {
			return (u >= 1 && u <= ue() && v >= 1 && v <= T) || (u >= 1 && u <= T && v >= 1 && v <= vb());
		}

		boolean inG(int u, int v) {
			return (u >= 2 && u <= ue() && v >= 2 && v <= T) || (u >= 2 && u <= T && v >= 2 && v <= vb());
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0xC0A3_5A11L);
		Scheme s = SCHEMES[r.nextInt(SCHEMES.length)];
		Geo g = new Geo(p.w(), p.d());
		int ue = g.ue();
		int vb = g.vb();
		// post positions (proud frame), ground and upper: the upper ones are one block further out on the street sides
		int[] fG = join(new int[] {1, 5, 9}, spaced(9, ue));
		int[] fU = join(new int[] {0, 5, 9}, spaced(9, ue + 1));
		int[] lG = join(new int[] {1, 5, 9}, spaced(9, vb));
		int[] lU = join(new int[] {0, 5, 9}, spaced(9, vb + 1));
		int[] bk = spaced(10, ue + 1);
		int[] rt = spaced(10, vb + 1);
		int[] endG = {0, 5, 10};
		int[] endU = {0, 5, 10};
		int doorShop = (9 + fG[3]) / 2;
		int doorSide = (9 + lG[3]) / 2;
		int gv = (10 + rt[1]) / 2;

		floors(c, p, g);
		// ---- ground storey (4 free: y1..y4)
		wallRun(c, p, s, 0, true, 2, -1, 2, ue, fG, i -> i != 7 && i != doorShop && !(i >= 2 && i <= 4), 99, false);
		wallRun(c, p, s, 0, false, 2, -1, 2, vb, lG, i -> i >= 10 && i != doorSide, 99, false);
		wallRun(c, p, s, 0, true, T, 1, 10, ue, bk, i -> i >= 13 && i <= ue - 3, -1, false);
		wallRun(c, p, s, 0, false, ue, 1, 2, 9, endG, i -> true, -1, false);
		wallRun(c, p, s, 0, false, T, 1, 10, vb, rt, i -> i != gv, -1, false);
		wallRun(c, p, s, 0, true, vb, 1, 2, 9, endG, i -> true, -1, false);
		// ---- upper storey (4 free: y6..y9)
		wallRun(c, p, s, 1, true, 1, -1, 1, ue, fU, i -> true, 9, true);
		wallRun(c, p, s, 1, false, 1, -1, 1, vb, lU, i -> true, 9, true);
		wallRun(c, p, s, 1, true, T, 1, 10, ue, bk, i -> i != ue - 3 && i != ue - 2, -1, true);
		wallRun(c, p, s, 1, false, ue, 1, 1, 9, endU, i -> true, -1, true);
		wallRun(c, p, s, 1, false, T, 1, 10, vb, rt, i -> true, -1, true);
		wallRun(c, p, s, 1, true, vb, 1, 1, 9, endU, i -> true, -1, true);

		cornerPost(c, p);
		partitionU(c, p, 0);
		partitionU(c, p, 1);
		partitionV(c, p, 0);
		partitionV(c, p, 1);
		gableFill(c, p, true, ue, true, false);
		gableFill(c, p, false, vb, true, false);
		gableFill(c, p, true, T, false, true);
		gableFill(c, p, false, T, false, true);
		lanternBand(c, p);

		// doors: tower hall (street), shop (street), workshop (side street) and workshop to the garden
		entry(c, p, true, 2, 7, Direction.NORTH);
		entry(c, p, true, 2, doorShop, Direction.NORTH);
		entry(c, p, false, 2, doorSide, Direction.WEST);
		entry(c, p, false, T, gv, Direction.EAST);
		// lanterns hung under the porch beam beside the two street doors
		for (int d : new int[] {7, doorShop}) {
			for (int k = -1; k <= 1; k += 2) {
				if (!contains(fG, d + k)) {
					p.put(c, d + k, 4, 1, lantern(true), CitadelCanvas.HANGING);
				}
			}
		}
		for (int k = -1; k <= 1; k += 2) {
			if (!contains(lG, doorSide + k)) {
				p.put(c, 1, 4, doorSide + k, lantern(true), CitadelCanvas.HANGING);
			}
		}

		for (int y = 10; y <= 13; y++) {
			p.set(c, ue + 1, y, 5, dark(Direction.Axis.Y));
			p.set(c, 5, y, vb + 1, dark(Direction.Axis.Y));
		}
		roofs(c, p, g, s);
		pyramid(c, p);
		chimney(c, p, g);
		trims(c, p, g, fU, lU, fG, lG, bk, rt, endU);
		stairs(c, p);
		interiors(c, p, g, doorShop, doorSide, gv);
		yard(c, p, g, gv);
		strips(c, p, g);
	}

	// ------------------------------------------------------------------ small helpers

	private static boolean contains(int[] a, int x) {
		for (int q : a) {
			if (q == x) {
				return true;
			}
		}
		return false;
	}

	/** Post indices start, start + 5, ... then {@code end}; a last bay shorter than 3 is merged into the one before. */
	private static int[] spaced(int start, int end) {
		int[] tmp = new int[64];
		int n = 0;
		int x = start;
		tmp[n++] = x;
		while (end - (x + 6) >= 3) {
			x += 6;
			tmp[n++] = x;
		}
		tmp[n++] = end;
		return Arrays.copyOf(tmp, n);
	}

	private static int[] join(int[] a, int[] b) {
		int[] out = Arrays.copyOf(a, a.length + b.length - 1);
		System.arraycopy(b, 1, out, a.length, b.length - 1);
		return out;
	}

	/** Whether cell {@code i} is a window cell of its bay; {@code wide} glazes a whole bay of up to 4. */
	private static boolean bayWin(int[] posts, int i, boolean wide) {
		int l = Integer.MIN_VALUE;
		int r = Integer.MAX_VALUE;
		for (int q : posts) {
			if (q < i) {
				l = Math.max(l, q);
			} else if (q > i) {
				r = Math.min(r, q);
			} else {
				return false;
			}
		}
		if (l == Integer.MIN_VALUE || r == Integer.MAX_VALUE) {
			return false;
		}
		int g = r - l - 1;
		int pos = i - l - 1;
		if (wide ? g <= 4 : g <= 2) {
			return true;
		}
		if (g >= 7) {
			return pos == 1 || pos == 2 || pos == g - 3 || pos == g - 2; // two windows with a plaster pier between
		}
		return pos >= 1 && pos <= g - 2;
	}

	private static BlockState face(Block bl, Direction d) {
		return b(bl).setValue(BlockStateProperties.HORIZONTAL_FACING, d);
	}

	private static BlockState carpet(DyeColor col) {
		return Blocks.CARPET.pick(col).defaultBlockState();
	}

	private static BlockState barrel() {
		return b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
	}

	private static BlockState dark(Direction.Axis ax) {
		return log(Blocks.DARK_OAK_LOG, ax);
	}

	// ------------------------------------------------------------------ materials

	/** [palette] the dado: stone bricks with a little moss and cracks, never one flat colour. */
	private static BlockState stone(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, b(Blocks.STONE_BRICKS), b(Blocks.STONE_BRICKS), b(Blocks.MOSSY_STONE_BRICKS), b(Blocks.CRACKED_STONE_BRICKS), b(Blocks.STONE_BRICKS));
	}

	/** [palette] plaster is mostly calcite with white terracotta patches. */
	private static BlockState plaster(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, b(Blocks.CALCITE), b(Blocks.CALCITE), b(Blocks.CALCITE), CitadelCanvas.terracotta(DyeColor.WHITE));
	}

	private static BlockState paving(Plot p, int u, int v) {
		return mix(p, u, 0, v, b(Blocks.POLISHED_ANDESITE), b(Blocks.STONE_BRICKS), b(Blocks.ANDESITE), b(Blocks.POLISHED_ANDESITE));
	}

	private static BlockState floorMix(Plot p, int u, int v) {
		return mix(p, u, 0, v, b(Blocks.SPRUCE_PLANKS), b(Blocks.SPRUCE_PLANKS), b(Blocks.SPRUCE_PLANKS), b(Blocks.DARK_OAK_PLANKS));
	}

	private static Block copperStairs(boolean exposed) {
		return exposed ? Blocks.CUT_COPPER_STAIRS.waxed().exposed() : Blocks.CUT_COPPER_STAIRS.waxed().oxidized();
	}

	private static BlockState copperBlock() {
		return Blocks.CUT_COPPER.waxed().oxidized().defaultBlockState();
	}

	// ------------------------------------------------------------------ floors

	/** The ground floor (y0), the deck of the upper floor (y5), porch strips and the stone footing round the back. */
	private static void floors(CitadelCanvas c, Plot p, Geo g) {
		int ue = g.ue();
		int vb = g.vb();
		for (int u = 0; u < p.w(); u++) {
			for (int v = 0; v < p.d(); v++) {
				if (g.inH(u, v)) {
					p.set(c, u, BEAM, v, b(Blocks.SPRUCE_PLANKS));
					if (g.inG(u, v)) {
						boolean edge = !g.inG(u - 1, v) || !g.inG(u + 1, v) || !g.inG(u, v - 1) || !g.inG(u, v + 1);
						p.set(c, u, 0, v, edge ? stone(p, u, 0, v) : floorMix(p, u, v));
					} else {
						p.set(c, u, 0, v, paving(p, u, v)); // the porch under the jetty
					}
				} else if (g.inH(u - 1, v) || g.inH(u, v - 1)) {
					p.set(c, u, 0, v, stone(p, u, 0, v)); // footing round the back and ends
				} else if ((u == 0 && v <= vb + 1) || (v == 0 && u <= ue + 1)) {
					p.set(c, u, 0, v, paving(p, u, v)); // the yard strip on the two streets
				}
			}
		}
	}

	// ------------------------------------------------------------------ walls

	/**
	 * One wall run on one storey. The plaster infill and the glass sit on the wall line; the dark posts stand
	 * proud of it on the line outside ({@code proud}). On the street sides ({@code out < 0}) the upper run is one
	 * block further out than the ground run (the jetty): ground posts carry a beam along the whole line, upper
	 * posts rest on joist ends and the top rail runs along the eave. Between the posts nothing stands on the
	 * proud line, so there is no ledge to stand on under an eave.
	 */
	private void wallRun(CitadelCanvas c, Plot p, Scheme s, int fl, boolean alongU, int line, int out, int ia, int ib,
			int[] posts, IntPredicate allow, int wideTo, boolean shutters) {
		int yb = fl == 0 ? 1 : 6;
		int yt = fl == 0 ? 4 : 9;
		int w1 = fl == 0 ? 2 : 7;
		int w2 = fl == 0 ? 3 : 8;
		int proud = line + out;
		boolean jetty = out < 0;
		Direction.Axis ax = alongU ? Direction.Axis.X : Direction.Axis.Z;
		Direction.Axis perp = alongU ? Direction.Axis.Z : Direction.Axis.X;
		IntPredicate win = i -> i >= ia && i <= ib && allow.test(i) && bayWin(posts, i, i <= wideTo);
		for (int i = ia; i <= ib; i++) {
			int u = alongU ? i : line;
			int v = alongU ? line : i;
			boolean w = win.test(i);
			for (int y = yb; y <= yt; y++) {
				BlockState st;
				if (w && y >= w1 && y <= w2) {
					st = pane(alongU, null);
				} else if (fl == 0 && y == yb) {
					st = stone(p, u, y, v);
				} else {
					st = plaster(p, u, y, v);
				}
				TimberKit.put(c, p, alongU, line, i, y, st);
			}
			if (w) {
				// [depth] a dark lintel over every window and, upstairs, a dark sill under it: the frame reads as timber
				TimberKit.put(c, p, alongU, line, i, w2 + 1, dark(ax));
			}
			if (fl == 1) {
				TimberKit.put(c, p, alongU, line, i, BEAM, dark(ax));
			}
			TimberKit.put(c, p, alongU, line, i, yt + 1, dark(ax));
			if (shutters && w) {
				boolean left = win.test(i - 1);
				boolean right = win.test(i + 1);
				if (left != right) {
					Direction lowFace = alongU ? Direction.EAST : Direction.SOUTH;
					Direction highFace = alongU ? Direction.WEST : Direction.NORTH;
					for (int y = w1; y <= w2; y++) {
						TimberKit.put(c, p, alongU, proud, i, y, trap(s.shutter(), left ? highFace : lowFace, true, false));
					}
				}
			}
		}
		int pa = posts[0];
		int pb = posts[posts.length - 1];
		for (int i = pa; i <= pb; i++) {
			boolean post = contains(posts, i);
			if (post) {
				for (int y = yb; y <= yt; y++) {
					TimberKit.put(c, p, alongU, proud, i, y, dark(Direction.Axis.Y));
				}
				if (!jetty) {
					TimberKit.put(c, p, alongU, proud, i, BEAM, dark(Direction.Axis.Y));
				} else if (fl == 1) {
					TimberKit.put(c, p, alongU, proud, i, BEAM, dark(perp)); // joist end under the post
				}
			}
			if (jetty && fl == 0) {
				TimberKit.put(c, p, alongU, proud, i, BEAM, dark(ax)); // the porch beam, along the whole line
			}
			if (jetty && fl == 1) {
				TimberKit.put(c, p, alongU, proud, i, PLATE, dark(ax)); // the top rail (the wing eaves replace it)
			}
		}
	}

	/** [depth] A cherry door with a lit transom and a dark lintel across three cells. */
	private static void entry(CitadelCanvas c, Plot p, boolean alongU, int line, int i, Direction f) {
		TimberKit.put(c, p, alongU, line, i, 1, door(Blocks.CHERRY_DOOR, f, false));
		TimberKit.put(c, p, alongU, line, i, 2, door(Blocks.CHERRY_DOOR, f, true));
		TimberKit.put(c, p, alongU, line, i, 3, pane(alongU, DyeColor.YELLOW));
		for (int k = -1; k <= 1; k++) {
			TimberKit.put(c, p, alongU, line, i + k, 4, dark(alongU ? Direction.Axis.X : Direction.Axis.Z));
		}
	}

	/** The re-entrant corner of the L: one dark column from the footing to the plate. */
	private static void cornerPost(CitadelCanvas c, Plot p) {
		for (int y = 1; y <= 9; y++) {
			if (y != BEAM) {
				p.set(c, T, y, T, dark(Direction.Axis.Y));
			}
		}
		p.set(c, T, BEAM, T, dark(Direction.Axis.X));
		p.set(c, T, PLATE, T, dark(Direction.Axis.X));
	}

	/**
	 * The wall between the tower and the front wing (plane u = 9): plaster between dark jambs, with a 3-wide, 3-high
	 * arch under a lintel. {@code fl} 0 = ground, 1 = upper.
	 */
	private static void partitionU(CitadelCanvas c, Plot p, int fl) {
		int yb = fl == 0 ? 1 : 6;
		int yt = fl == 0 ? 4 : 9;
		int v0 = fl == 0 ? 3 : 2;
		for (int v = v0; v <= 8; v++) {
			for (int y = yb; y <= yt; y++) {
				BlockState st;
				boolean arch = v >= 4 && v <= 6;
				if (arch && y <= yb + 2) {
					st = CitadelCanvas.AIR;
				} else if (arch && y == yb + 3) {
					st = dark(Direction.Axis.Z);
				} else if (v == 3 || v == 7) {
					st = dark(Direction.Axis.Y);
				} else {
					st = plaster(p, T, y, v);
				}
				p.set(c, T, y, v, st);
			}
			p.set(c, T, fl == 0 ? BEAM : PLATE, v, dark(Direction.Axis.Z));
		}
	}

	/** The wall between the tower and the side wing (plane v = 9), arch on u 6..8. */
	private static void partitionV(CitadelCanvas c, Plot p, int fl) {
		int yb = fl == 0 ? 1 : 6;
		int yt = fl == 0 ? 4 : 9;
		int u0 = fl == 0 ? 3 : 2;
		for (int u = u0; u <= 8; u++) {
			for (int y = yb; y <= yt; y++) {
				BlockState st;
				boolean arch = u >= 6 && u <= 8;
				if (arch && y <= yb + 2) {
					st = CitadelCanvas.AIR;
				} else if (arch && y == yb + 3) {
					st = dark(Direction.Axis.X);
				} else if (u == 5) {
					st = dark(Direction.Axis.Y);
				} else {
					st = plaster(p, u, y, T);
				}
				p.set(c, u, y, T, st);
			}
			p.set(c, u, fl == 0 ? BEAM : PLATE, T, dark(Direction.Axis.X));
		}
	}

	/**
	 * The triangle that closes a gable: plaster under a dark rafter that follows the roof, a small window in the
	 * middle of an end gable. {@code minWall} keeps at least two rows (the tower's wall behind the lantern band).
	 */
	private static void gableFill(CitadelCanvas c, Plot p, boolean planeIsU, int plane, boolean decorate, boolean minWall) {
		for (int t = 1; t <= 9; t++) {
			int top = PLATE + Math.min(t, 10 - t);
			int last = minWall ? Math.max(top - 1, PLATE + 3) : top - 1;
			for (int y = PLATE + 1; y <= last; y++) {
				BlockState st;
				if (decorate && y == top - 1 && top - 1 > PLATE + 2) {
					st = b(Blocks.DARK_OAK_PLANKS);
				} else if (decorate && t == 5 && y <= PLATE + 2) {
					st = pane(!planeIsU, DyeColor.PINK);
				} else {
					st = plaster(p, planeIsU ? plane : t, y, planeIsU ? t : plane);
				}
				p.set(c, planeIsU ? plane : t, y, planeIsU ? t : plane, st);
			}
		}
	}

	/** The glazed lantern of the mirador: dark posts and panes on the two street faces (y11..12). */
	private static void lanternBand(CitadelCanvas c, Plot p) {
		for (int i = 1; i <= 9; i++) {
			boolean post = i == 1 || i == 5 || i == 9;
			DyeColor tint = i == 3 || i == 7 ? DyeColor.PINK : null;
			for (int y = 11; y <= 13; y++) {
				p.set(c, i, y, 1, post ? dark(Direction.Axis.Y) : pane(true, tint));
				p.set(c, 1, y, i, post ? dark(Direction.Axis.Y) : pane(false, tint));
			}
		}
		// [depth] a copper cove under the pyramid on the street faces
		for (int v = 0; v <= 9; v++) {
			p.set(c, 0, 11, v, stair(Blocks.DARK_OAK_STAIRS, Direction.EAST, true));
		}
		for (int u = 1; u <= 9; u++) {
			p.set(c, u, 11, 0, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, true));
		}
	}

	// ------------------------------------------------------------------ roofs

	private static void roofs(CitadelCanvas c, Plot p, Geo g, Scheme s) {
		Block ridge = Blocks.DARK_OAK_PLANKS;
		Block cap = Blocks.DARK_OAK_SLAB;
		gable(c, p, true, 10, g.ue() + 1, 0, 10, PLATE, s.roof(), s.eave(), ridge, cap, s.alt(), false, true);
		gable(c, p, false, 10, g.vb() + 1, 0, 10, PLATE, s.roof(), s.eave(), ridge, cap, s.alt(), false, true);
		// the valley where the two wings meet
		p.set(c, 10, PLATE, 10, b(Blocks.DEEPSLATE_TILES));
	}

	/**
	 * [finish] The mirador roof: a copper pyramid (9 wide at y13 down to one block at y17), clean outer-corner
	 * hips, a lightning rod, and a lantern hung from its ceiling over the salon.
	 */
	private static void pyramid(CitadelCanvas c, Plot p) {
		for (int ring = 0; ring <= 4; ring++) {
			int half = 4 - ring;
			int y = 14 + ring;
			for (int u = TC - half; u <= TC + half; u++) {
				for (int v = TC - half; v <= TC + half; v++) {
					boolean eu = u == TC - half || u == TC + half;
					boolean ev = v == TC - half || v == TC + half;
					BlockState st;
					if (half == 0) {
						st = b(Blocks.DEEPSLATE_TILE_WALL);
					} else if (eu && ev) {
						st = JapaneseKit.stair(copperStairs(false), cornerFacing(v == TC - half, u == TC - half), Half.BOTTOM, StairsShape.OUTER_LEFT);
					} else if (eu || ev) {
						Direction f = u == TC - half ? Direction.EAST : u == TC + half ? Direction.WEST : v == TC - half ? Direction.SOUTH : Direction.NORTH;
						st = stair(copperStairs(false), f, false);
					} else {
						st = copperBlock();
					}
					p.set(c, u, y, v, st);
				}
			}
		}
		p.set(c, TC, 19, TC, Blocks.LIGHTNING_ROD.waxed().oxidized().defaultBlockState());
		p.put(c, TC, 13, TC, lantern(true), CitadelCanvas.HANGING);
	}

	/** Facing of an outer-corner stair at a ring corner: {@code top} = the north row, {@code left} = the west column. */
	private static Direction cornerFacing(boolean top, boolean left) {
		if (top) {
			return left ? Direction.SOUTH : Direction.WEST;
		}
		return left ? Direction.EAST : Direction.NORTH;
	}

	/** A brick chimney breast against the back wall of the dwelling, with a stove, rising through the back slope. */
	private static void chimney(CitadelCanvas c, Plot p, Geo g) {
		int cu = g.ue() - 3;
		for (int du = 0; du <= 1; du++) {
			int u = cu + du;
			for (int y = 6; y <= 16; y++) {
				BlockState st = y == 6 && du == 0 ? face(Blocks.FURNACE, Direction.NORTH).setValue(BlockStateProperties.LIT, true)
						: mix(p, u, y, 8, b(Blocks.BRICKS), b(Blocks.BRICKS), b(Blocks.STONE_BRICKS));
				p.set(c, u, y, 8, st);
			}
			p.set(c, u, 17, 8, TimberKit.slab(Blocks.STONE_BRICK_SLAB, false));
		}
	}

	// ------------------------------------------------------------------ trims

	/**
	 * [depth] Brackets under the jetty, a corner lantern, and under every eave a cornice of dentils (beam tails and
	 * upside-down stairs alternating) between the posts.
	 */
	private static void trims(CitadelCanvas c, Plot p, Geo g, int[] fU, int[] lU, int[] fG, int[] lG, int[] bk, int[] rt, int[] endU) {
		int ue = g.ue();
		int vb = g.vb();
		for (int q : fU) {
			if (q >= 1 && q <= ue) {
				p.set(c, q, 4, 0, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, true));
			}
		}
		for (int q : lU) {
			if (q >= 1 && q <= vb) {
				p.set(c, 0, 4, q, stair(Blocks.DARK_OAK_STAIRS, Direction.EAST, true));
			}
		}
		p.put(c, 0, 4, 0, lantern(true), CitadelCanvas.HANGING);
		dentils(c, p, true, 1, -1, 1, ue, fU);
		dentils(c, p, false, 1, -1, 1, vb, lU);
		dentils(c, p, true, T, 1, 10, ue, bk);
		dentils(c, p, false, T, 1, 10, vb, rt);
	}

	private static void dentils(CitadelCanvas c, Plot p, boolean alongU, int line, int out, int ia, int ib, int[] posts) {
		Direction toWall = TimberKit.out(alongU, out).getOpposite();
		int proud = line + out;
		for (int i = ia; i <= ib; i++) {
			if (contains(posts, i)) {
				continue;
			}
			BlockState st = (i & 1) == 0 ? dark(toWall.getAxis()) : stair(Blocks.DARK_OAK_STAIRS, toWall, true);
			TimberKit.put(c, p, alongU, proud, i, 9, st);
		}
	}

	// ------------------------------------------------------------------ the stair

	/**
	 * The way up, in the tower hall: a flight 2 wide on u3..4 climbing north from v7 to v3, the first step met head-on
	 * from the cells (3..4, 8), two free blocks over every step, and the arrival cells (3..4, 2) on the upper floor.
	 * Under it the steps are solid; on the upper floor a dark-oak rail guards the hole.
	 */
	private static void stairs(CitadelCanvas c, Plot p) {
		for (int j = 0; j < 5; j++) {
			int v = 7 - j;
			for (int u = 3; u <= 4; u++) {
				for (int y = 1; y < 1 + j; y++) {
					p.set(c, u, y, v, b(Blocks.SPRUCE_PLANKS));
				}
				p.set(c, u, 1 + j, v, CitadelCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
				if (j < 4) {
					p.set(c, u, BEAM, v, CitadelCanvas.AIR); // the hole over the flight
				}
			}
		}
		for (int v = 4; v <= 8; v++) {
			rail(c, p, 2, v);
			rail(c, p, 5, v);
		}
		rail(c, p, 3, 8);
		rail(c, p, 4, 8);
	}

	private static boolean isRail(int u, int v) {
		return ((u == 2 || u == 5) && v >= 4 && v <= 8) || ((u == 3 || u == 4) && v == 8);
	}

	private static void rail(CitadelCanvas c, Plot p, int u, int v) {
		p.set(c, u, 6, v, fence(Blocks.DARK_OAK_FENCE, isRail(u, v - 1), isRail(u + 1, v), isRail(u, v + 1), isRail(u - 1, v)));
	}

	// ------------------------------------------------------------------ interiors

	private static void interiors(CitadelCanvas c, Plot p, Geo g, int doorShop, int doorSide, int gv) {
		int ue = g.ue();
		int vb = g.vb();
		// ---- ground: the entrance hall (tower): a runner from the door to the arch, a chest and a pot
		for (int v = 3; v <= 8; v++) {
			p.set(c, 7, 1, v, carpet(DyeColor.RED));
		}
		p.set(c, 8, 1, 7, face(Blocks.CHEST, Direction.WEST));
		p.set(c, 8, 1, 3, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
		p.put(c, 6, 4, 5, lantern(true), CitadelCanvas.HANGING);
		p.put(c, 4, 4, 5, lantern(true), CitadelCanvas.HANGING);

		// ---- ground: the shop (front wing): shelves on the tower wall, a counter, stock along the back wall
		for (int v : new int[] {3, 7, 8}) {
			for (int y = 1; y <= 3; y++) {
				p.set(c, 10, y, v, y == 2 && v == 8 ? face(Blocks.CHISELED_BOOKSHELF, Direction.EAST) : b(Blocks.BOOKSHELF));
			}
		}
		for (int u = 13; u <= ue - 2; u++) {
			p.set(c, u, 1, 5, b(Blocks.DARK_OAK_PLANKS));
		}
		for (int u = 11; u <= ue - 1; u++) {
			int k = p.hash(u, 1, 8, 3);
			p.set(c, u, 1, 8, k == 0 ? face(Blocks.CHEST, Direction.NORTH) : barrel());
		}
		for (int v = 6; v <= 7; v++) {
			p.set(c, ue - 1, 1, v, barrel());
		}
		p.put(c, 12, 4, 4, lantern(true), CitadelCanvas.HANGING);
		p.put(c, ue - 3, 4, 4, lantern(true), CitadelCanvas.HANGING);

		// ---- ground: the workshop (side wing): stations along the walls, shelves on the tower wall
		for (int u = 3; u <= 5; u++) {
			for (int y = 1; y <= 3; y++) {
				p.set(c, u, y, 10, b(Blocks.BOOKSHELF));
			}
		}
		int back = vb - 1;
		BlockState[] row = {
				b(Blocks.CRAFTING_TABLE),
				b(Blocks.SMITHING_TABLE),
				face(Blocks.BLAST_FURNACE, Direction.NORTH).setValue(BlockStateProperties.LIT, true),
				face(Blocks.FURNACE, Direction.NORTH),
				b(Blocks.GRINDSTONE).setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
				face(Blocks.ANVIL, Direction.EAST),
		};
		for (int k = 0; k < row.length; k++) {
			p.set(c, 3 + k, 1, back, row[k]);
		}
		for (int v = 11; v < back; v++) {
			if (Math.abs(v - gv) > 1) {
				p.set(c, 8, 1, v, v % 2 == 0 ? barrel() : face(Blocks.CHEST, Direction.WEST));
			}
			if (Math.abs(v - doorSide) > 1) {
				p.set(c, 3, 1, v, v % 2 == 0 ? face(Blocks.LOOM, Direction.EAST) : b(Blocks.CARTOGRAPHY_TABLE));
			}
		}
		p.put(c, 5, 4, 12, lantern(true), CitadelCanvas.HANGING);
		p.put(c, 5, 4, back - 2, lantern(true), CitadelCanvas.HANGING);

		// ---- upper: the salon (tower): a rug, a reading corner and a map table under the lantern
		for (int u = 6; u <= 8; u++) {
			for (int v = 3; v <= 7; v++) {
				p.set(c, u, 6, v, carpet(DyeColor.RED));
			}
		}
		p.set(c, 8, 6, 2, face(Blocks.LECTERN, Direction.SOUTH).setValue(BlockStateProperties.HAS_BOOK, true));
		p.set(c, 7, 6, 2, b(Blocks.CARTOGRAPHY_TABLE));
		p.set(c, 6, 6, 2, b(Blocks.ENCHANTING_TABLE));
		p.set(c, 2, 6, 2, b(Blocks.BOOKSHELF));
		p.set(c, 2, 6, 3, b(Blocks.BOOKSHELF));

		// ---- upper: the dwelling (front wing): two beds against the end wall, a table with chairs, a stove corner
		bed(c, p, ue - 2, 6, 3, Direction.EAST, DyeColor.RED);
		bed(c, p, ue - 2, 6, 7, Direction.EAST, DyeColor.BLUE);
		p.set(c, ue - 1, 6, 5, face(Blocks.CHEST, Direction.WEST));
		p.set(c, 12, 6, 5, TimberKit.slab(Blocks.DARK_OAK_SLAB, false));
		p.set(c, 13, 6, 5, TimberKit.slab(Blocks.DARK_OAK_SLAB, false));
		for (int u = 12; u <= 13; u++) {
			p.set(c, u, 6, 4, stair(Blocks.DARK_OAK_STAIRS, Direction.NORTH, false));
			p.set(c, u, 6, 6, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
		}
		p.set(c, 10, 6, 8, b(Blocks.BOOKSHELF));
		p.set(c, 11, 6, 8, face(Blocks.CHEST, Direction.NORTH));
		pendant(c, p, 12, 5, true);
		if (ue - 3 > 14) {
			pendant(c, p, ue - 3, 5, true);
		}

		// ---- upper: the study (side wing): desk, shelves, a map table and a rug
		for (int u = 3; u <= 7; u++) {
			p.set(c, u, 6, vb - 1, b(Blocks.BOOKSHELF));
		}
		p.set(c, 8, 6, vb - 1, b(Blocks.CARTOGRAPHY_TABLE));
		p.set(c, 2, 6, vb - 1, face(Blocks.CHEST, Direction.EAST));
		for (int u = 4; u <= 6; u++) {
			for (int v = 12; v <= vb - 3; v++) {
				p.set(c, u, 6, v, carpet(DyeColor.GREEN));
			}
		}
		p.set(c, 5, 6, vb - 3, face(Blocks.LECTERN, Direction.NORTH).setValue(BlockStateProperties.HAS_BOOK, true));
		p.set(c, 3, 6, 12, b(Blocks.BOOKSHELF));
		p.set(c, 8, 6, 12, b(Blocks.BOOKSHELF));
		pendant(c, p, 5, 12, false);
		if (vb - 3 > 14) {
			pendant(c, p, 5, vb - 3, false);
		}
	}

	/** A lantern on a fence stem hung from the ridge (the ridge runs along u at v5 for the front wing, along v at u5 for the side wing). */
	private static void pendant(CitadelCanvas c, Plot p, int u, int v, boolean unused) {
		for (int y = 12; y <= 14; y++) {
			p.set(c, u, y, v, fence(Blocks.DARK_OAK_FENCE, false, false, false, false));
		}
		p.put(c, u, 11, v, lantern(true), CitadelCanvas.HANGING);
	}

	private static void bed(CitadelCanvas c, Plot p, int u, int y, int v, Direction head, DyeColor col) {
		BlockState base = Blocks.BED.pick(col).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, head);
		p.set(c, u, y, v, base.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		p.set(c, u + head.getStepX(), y, v + head.getStepZ(), base.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
	}

	// ------------------------------------------------------------------ garden and streets

	private static boolean gardenWall(int gu, int gd, int gv, int u, int v) {
		return u >= 11 && u <= gu && v >= 11 && v <= gd && (u == gu || v == gd) && !(u == gu && (v == gv || v == gv + 1));
	}

	/**
	 * The garden in the inner angle of the L: a low stone wall with a gate (two lantern piers) on the east side, a
	 * paved path from the gate to the workshop's back door, mossy beds with plants, a bench and a lamp post.
	 */
	private static void yard(CitadelCanvas c, Plot p, Geo g, int gv) {
		int gu = g.ue() + 1;
		int gd = g.vb() + 1;
		for (int u = 11; u <= gu; u++) {
			for (int v = 11; v <= gd; v++) {
				if (gardenWall(gu, gd, gv, u, v)) {
					p.set(c, u, 0, v, mix(p, u, 0, v, b(Blocks.STONE_BRICKS), b(Blocks.MOSSY_STONE_BRICKS), b(Blocks.STONE_BRICKS)));
					Block wb = p.hash(u, 1, v, 3) == 0 ? Blocks.COBBLESTONE_WALL : Blocks.STONE_BRICK_WALL;
					p.set(c, u, 1, v, lowWall(wb, gardenWall(gu, gd, gv, u, v - 1), gardenWall(gu, gd, gv, u + 1, v),
							gardenWall(gu, gd, gv, u, v + 1), gardenWall(gu, gd, gv, u - 1, v)));
					continue;
				}
				if (v == gv || v == gv + 1) {
					p.set(c, u, 0, v, paving(p, u, v));
					continue;
				}
				p.set(c, u, 0, v, p.hash(u, 0, v, 5) == 0 ? b(Blocks.COARSE_DIRT) : b(Blocks.MOSS_BLOCK));
				BlockState plant = groundPlant(p, u, 1, v);
				if (plant != null) {
					p.set(c, u, 1, v, plant);
				}
			}
		}
		for (int v : new int[] {gv - 1, gv + 2}) {
			if (v >= 11 && v <= gd) {
				p.set(c, gu, 1, v, b(Blocks.POLISHED_DEEPSLATE));
				p.set(c, gu, 2, v, lantern(false));
			}
		}
		// a bench against the back wall and a lamp post in the corner
		for (int u = 12; u <= 13; u++) {
			if (gd - 1 != gv && gd - 1 != gv + 1) {
				p.set(c, u, 1, gd - 1, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
			}
		}
		if (gd - 1 != gv && gd - 1 != gv + 1) {
			p.set(c, gu - 1, 1, gd - 1, b(Blocks.POLISHED_DEEPSLATE));
			p.set(c, gu - 1, 2, gd - 1, fence(Blocks.DARK_OAK_FENCE, false, false, false, false));
			p.set(c, gu - 1, 3, gd - 1, fence(Blocks.DARK_OAK_FENCE, false, false, false, false));
			p.set(c, gu - 1, 4, gd - 1, lantern(false));
		}
	}

	/** Soft plants (no collision) on the street strips, so nothing there can be stood on under the jetty. */
	private static void strips(CitadelCanvas c, Plot p, Geo g) {
		for (int u = 0; u <= g.ue() + 1; u++) {
			soft(c, p, u, 0);
		}
		for (int v = 1; v <= g.vb() + 1; v++) {
			soft(c, p, 0, v);
		}
	}

	private static void soft(CitadelCanvas c, Plot p, int u, int v) {
		BlockState st = switch (p.hash(u, 1, v, 6)) {
			case 0 -> b(Blocks.FERN);
			case 1 -> b(Blocks.PINK_PETALS);
			case 2 -> b(Blocks.CHERRY_SAPLING);
			default -> null;
		};
		if (st != null) {
			p.set(c, u, 1, v, st);
		}
	}
}
