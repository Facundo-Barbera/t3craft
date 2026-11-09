package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.fence;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.glassPane;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.lantern;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.log;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.paneU;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.paneV;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.stair;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.trapdoor;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * A tea house (chashitsu) that is used, not just looked at: a walk-in teahouse on a stone plinth with a 2-deep engawa
 * under the eave, a tea room (4-5 blocks of free height), a service kitchen (mizuya) and a roji garden in front.
 *
 * <pre>
 *  plan (front = v 0; unmirrored, the whole plot flips left/right by seed)
 *  v0-1      chumon gate (roofed), bamboo fence, machiai (waiting bench, roofed on wide plots)
 *  v1..gd-1  roji: stepping stones, tsukubai (low water basin + bamboo spout), ishidoro, moss
 *  gd        wide stone steps up to the engawa
 *  gd+1..2   engawa: spruce deck under the eave, posts and a beam, noren over the door
 *  gd+3      front wall: shoji panes, double door into the tea room, a service door into the mizuya
 *  .. d-2    SALA (tea room): irori hearth (campfire under a kettle) with zabuton, tokonoma with a scroll
 *            and a flower, hanging lanterns  |  partition with a doorway  |  MIZUYA: chest, barrel, cauldron, smoker
 *  d-1       back eave
 *
 *  roof (2:1, stair + slab): kirizuma (gable, ridge along the front) or yosemune (hip), by seed; deepslate tiles or
 *  bamboo thatch; dark fascia, a marked ridge with finials, upturned corner horns.
 * </pre>
 *
 * Walk: floor at y0 (a stair step up), walls y1-4, plate y5; the room ceiling rises with the roof from y5, so the tea
 * room is at least 4 free blocks high and nothing is sealed or low.
 *
 * Size: 12-17 x 13-17 x 12+.
 */
public final class HouseJapaneseTeahouse implements Variant {
	private static final int PLATE = 5;

	@Override
	public String id() {
		return "house_japanese_teahouse";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 12;
	}

	@Override
	public int minD() {
		return 13;
	}

	@Override
	public int minH() {
		return 12;
	}

	@Override
	public int maxW() {
		return 17;
	}

	@Override
	public int maxD() {
		return 17;
	}

	// ------------------------------------------------------------------ geometry

	private record Mats(Block stair, Block slab, BlockState full, BlockState ridge, BlockState cap) {
	}

	/** Fixed lines of the plan, all in local coordinates of an unmirrored house (sala on the low-u side). */
	private static final class G {
		final int w;
		final int d;
		final int gd;
		final int ef;
		final int wu0;
		final int wu1;
		final int wv0;
		final int wv1;
		final int su0;
		final int su1;
		final int sv0;
		final int sv1;
		final int pu;
		final int mu0;
		final int mu1;
		final int dc;
		final int hu;
		final int hv;
		final int md;
		final int dv;
		final int bk;
		final int sx;
		final int gx;
		final int bu;
		final boolean hip;
		final boolean mirror;
		final boolean[] post;
		final DyeColor noren;
		final DyeColor cushion;
		final Mats roof;

		G(Plot p, Random r) {
			w = p.w();
			d = p.d();
			gd = 3 + (d - 12) / 2;
			ef = gd + 1;
			wu0 = 1;
			wu1 = w - 2;
			wv0 = gd + 3;
			wv1 = d - 2;
			int iw = w - 4;
			int idd = wv1 - wv0 - 1;
			double[] frac = {0.58, 0.66, 0.74};
			int sw = (int) Math.round(iw * frac[p.hash(2, 0, 0, 3)]);
			sw = Math.max(5, Math.min(iw - 3, sw));
			su0 = wu0 + 1;
			su1 = su0 + sw - 1;
			pu = su1 + 1;
			mu0 = pu + 1;
			mu1 = wu1 - 1;
			sv0 = wv0 + 1;
			sv1 = wv1 - 1;
			dc = su0 + (sw - 2) / 2;
			hu = su0 + (sw - 1) / 2;
			hv = sv0 + 1 + Math.max(0, (idd - 4) / 2);
			md = mu0 + (mu1 - mu0) / 2;
			dv = sv0 + (idd - 2) / 2;
			bk = (d - gd) % 2 == 0 ? 1 : 0;
			sx = dc;
			// [variation] by seed, through the hash (java.util.Random of nearby seeds gives correlated first draws)
			hip = p.hash(3, 0, 0, 2) == 0;
			boolean thatch = p.hash(4, 0, 0, 2) == 0;
			mirror = p.hash(5, 0, 0, 2) == 0;
			boolean rj = p.hash(6, 0, 0, 2) == 0;
			DyeColor[] norens = {DyeColor.BLUE, DyeColor.RED, DyeColor.CYAN, DyeColor.PURPLE};
			noren = norens[p.hash(7, 0, 0, norens.length)];
			DyeColor[] cush = {DyeColor.RED, DyeColor.ORANGE, DyeColor.PURPLE, DyeColor.GREEN};
			cushion = cush[p.hash(8, 0, 0, cush.length)];
			int maxGx = w >= 14 ? w - 10 : w - 8;
			gx = Math.max(3, Math.min(maxGx, sx + (rj ? -2 : 1)));
			bu = w - 4;
			roof = thatch
					? new Mats(Blocks.BAMBOO_MOSAIC_STAIRS, Blocks.BAMBOO_MOSAIC_SLAB, b(Blocks.BAMBOO_MOSAIC), b(Blocks.DARK_OAK_PLANKS), slab(Blocks.DARK_OAK_SLAB))
					: new Mats(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, b(Blocks.DEEPSLATE_TILES), b(Blocks.STONE_BRICKS), slab(Blocks.STONE_BRICK_SLAB));
			post = new boolean[w];
			post[wu0] = true;
			post[wu1] = true;
			post[pu] = true;
			for (int u : posts(wu0, pu)) {
				if (u < dc - 1 || u > dc + 2) {
					post[u] = true;
				}
			}
			for (int u : posts(pu, wu1)) {
				if (u < md - 1 || u > md + 1) {
					post[u] = true;
				}
			}
		}

		/** Roof surface in half blocks above the eave line (2:1): 0, 1, 2 ... from the eave in; -1 outside the roof. */
		int g(int u, int v) {
			if (u < 0 || u >= w || v < gd || v >= d) {
				return -1;
			}
			int g = Math.min(v - gd, d - 1 - v + bk);
			if (hip) {
				g = Math.min(g, Math.min(u, w - 1 - u));
			}
			return g;
		}

		/** The y of the lowest roof block above local (u, v): the room's ceiling there. */
		int ceil(int u, int v) {
			return Math.max(PLATE, 4 + g(u, v) / 2);
		}
	}

	/** Post positions between {@code a} and {@code bb}: both ends and evenly spaced, about every 4 cells. */
	private static int[] posts(int a, int bb) {
		int span = bb - a;
		int nb = Math.max(1, (int) Math.round(span / 4.5));
		int[] out = new int[nb + 1];
		for (int k = 0; k <= nb; k++) {
			out[k] = a + (int) Math.round(k * (double) span / nb);
		}
		return out;
	}

	/** Writes through the plot, flipping the whole house left to right when {@code mirror} (states are mirrored too). */
	private static final class Pen {
		final CitadelCanvas c;
		final Plot p;
		final boolean mirror;
		final int w;

		Pen(CitadelCanvas c, Plot p, boolean mirror) {
			this.c = c;
			this.p = p;
			this.mirror = mirror;
			this.w = p.w();
		}

		int mu(int u) {
			return mirror ? w - 1 - u : u;
		}

		void set(int u, int y, int v, BlockState s) {
			put(u, y, v, s, CitadelCanvas.SOLID);
		}

		void put(int u, int y, int v, BlockState s, int layer) {
			if (!p.has(mu(u), y, v)) {
				return;
			}
			p.put(c, mu(u), y, v, mirror ? s.mirror(Mirror.FRONT_BACK) : s, layer);
		}

		boolean free(int u, int y, int v) {
			if (!p.has(mu(u), y, v)) {
				return false;
			}
			BlockState s = p.at(c, mu(u), y, v);
			return s == null || s.isAir();
		}
	}

	// ------------------------------------------------------------------ materials

	private static BlockState stone(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 6)) {
			case 0 -> b(Blocks.MOSSY_STONE_BRICKS);
			case 1 -> b(Blocks.CRACKED_STONE_BRICKS);
			default -> b(Blocks.STONE_BRICKS);
		};
	}

	private static BlockState plaster(Plot p, int u, int y, int v) {
		return p.hash(u, y, v, 8) == 0 ? b(Blocks.TUFF_BRICKS) : b(Blocks.CALCITE);
	}

	private static BlockState door(boolean leftHinge, boolean lower) {
		return b(Blocks.DARK_OAK_DOOR).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
				.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, lower ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
				.setValue(BlockStateProperties.DOOR_HINGE, leftHinge ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
	}

	private static BlockState carpet(DyeColor col) {
		return Blocks.CARPET.pick(col).defaultBlockState();
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x5445_4148_4F55L);
		G q = new G(p, r);
		Pen g = new Pen(c, p, q.mirror);
		floors(g, p, q);
		roof(g, q);
		walls(g, p, q);
		interior(g, p, q);
		engawa(g, p, q);
		garden(g, p, q);
	}

	// ------------------------------------------------------------------ floors (y0)

	/** [palette] a stone plinth (the zocalo), a spruce engawa deck, bamboo-mosaic tatami in the sala, a stone doma in the mizuya. */
	private static void floors(Pen g, Plot p, G q) {
		for (int v = q.ef; v <= q.wv1; v++) {
			for (int u = q.wu0; u <= q.wu1; u++) {
				BlockState st;
				boolean engawa = v < q.wv0;
				boolean edge = u == q.wu0 || u == q.wu1 || v == q.wv1 || v == q.wv0 || u == q.pu;
				if (engawa) {
					st = u == q.wu0 || u == q.wu1 ? stone(p, u, 0, v) : p.hash(u, 0, v, 5) == 0 ? b(Blocks.DARK_OAK_PLANKS) : b(Blocks.SPRUCE_PLANKS);
				} else if (edge) {
					st = stone(p, u, 0, v);
				} else if (u > q.pu) {
					st = p.hash(u, 0, v, 3) == 0 ? b(Blocks.POLISHED_ANDESITE) : b(Blocks.STONE_BRICKS);
				} else {
					st = ((u + v) & 1) == 0 ? b(Blocks.BAMBOO_MOSAIC) : b(Blocks.BAMBOO_PLANKS);
				}
				g.set(u, 0, v, st);
			}
		}
		// the tokonoma board
		int tc = q.dc;
		g.set(tc, 0, q.sv1, b(Blocks.DARK_OAK_PLANKS));
		g.set(tc + 1, 0, q.sv1, b(Blocks.DARK_OAK_PLANKS));
	}

	// ------------------------------------------------------------------ roof

	private static List<Direction> higher4(G q, int u, int v, int gg) {
		Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
		int[][] off = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
		List<Direction> out = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			if (q.g(u + off[i][0], v + off[i][1]) > gg) {
				out.add(dirs[i]);
			}
		}
		return out;
	}

	/** The first higher diagonal neighbour as {north-south direction, east-west direction}, or null. */
	private static Direction[] higherDiag(G q, int u, int v, int gg) {
		for (int dv = -1; dv <= 1; dv += 2) {
			for (int du = -1; du <= 1; du += 2) {
				if (q.g(u + du, v + dv) > gg) {
					return new Direction[] {dv > 0 ? Direction.SOUTH : Direction.NORTH, du > 0 ? Direction.EAST : Direction.WEST};
				}
			}
		}
		return null;
	}

	/** An outer-corner stair whose tall quarter is where {@code d1} and {@code d2} meet. */
	private static BlockState cornerStair(Block stairs, Direction d1, Direction d2) {
		StairsShape shape = d1.getCounterClockWise() == d2 ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
		return stair(stairs, d1, Half.BOTTOM, shape);
	}

	/**
	 * [finish] a 2:1 roof (slab, stair, slab, stair...) over the whole footprint with the eave one block past the engawa
	 * posts: a dark fascia, a full block under every cell from the third in (so the room's ceiling steps up with the roof
	 * and no light gets through), a ridge of full blocks with a cap and finials, and a horn at each corner.
	 */
	private static void roof(Pen g, G q) {
		Mats m = q.roof;
		for (int v = q.gd; v < q.d; v++) {
			for (int u = 0; u < q.w; u++) {
				int gg = q.g(u, v);
				int y = PLATE + gg / 2;
				List<Direction> hi = higher4(q, u, v, gg);
				Direction d1 = null;
				Direction d2 = null;
				if (hi.isEmpty()) {
					Direction[] dg = higherDiag(q, u, v, gg);
					if (dg == null) {
						ridge(g, q, u, v, gg, y);
						continue;
					}
					d1 = dg[0];
					d2 = dg[1];
				} else {
					d1 = hi.get(0);
					for (Direction o : hi) {
						if (o.getAxis() != d1.getAxis()) {
							d2 = o;
							break;
						}
					}
				}
				if (gg >= 2) {
					g.set(u, y - 1, v, m.full());
				}
				if (gg % 2 == 0) {
					g.set(u, y, v, gg == 0 ? slab(Blocks.DARK_OAK_SLAB) : slab(m.slab()));
				} else {
					Block sb = gg == 1 ? Blocks.DARK_OAK_STAIRS : m.stair(); // the second course is dark too: a deep fascia
					g.set(u, y, v, d2 != null ? cornerStair(sb, d1, d2) : stair(sb, d1));
				}
			}
		}
		// [finish] the horns: an outer corner stair of dark oak standing on each corner of the eave
		for (int v : new int[] {q.gd, q.d - 1}) {
			for (int u : new int[] {0, q.w - 1}) {
				Direction a = v == q.gd ? Direction.NORTH : Direction.SOUTH;
				Direction bb = u == 0 ? Direction.WEST : Direction.EAST;
				g.set(u, PLATE + q.g(u, v) / 2 + 1, v, cornerStair(Blocks.DARK_OAK_STAIRS, a, bb));
			}
		}
	}

	private static void ridge(Pen g, G q, int u, int v, int gg, int y) {
		int same = 0;
		Direction toRidge = null;
		Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
		int[][] off = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
		for (int i = 0; i < 4; i++) {
			if (q.g(u + off[i][0], v + off[i][1]) == gg) {
				same++;
				toRidge = dirs[i];
			}
		}
		if (same <= 1) {
			// [finish] the ridge ends in a full block with a dark oak stair that rises towards the ridge: a low kerabu
			g.set(u, y, v, q.roof.ridge());
			g.set(u, y + 1, v, same == 1 ? stair(Blocks.DARK_OAK_STAIRS, toRidge) : slab(Blocks.DARK_OAK_SLAB));
		} else if (gg % 2 == 1) {
			g.set(u, y, v, q.roof.ridge());
		} else {
			g.set(u, y, v, q.roof.cap());
		}
	}

	// ------------------------------------------------------------------ walls

	private static void column(Pen g, int u, int v) {
		for (int y = 1; y <= 4; y++) {
			g.set(u, y, v, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
		}
	}

	/**
	 * The top of a wall: where the roof is 2+ half blocks up, its under-block at the plate line becomes a beam and the
	 * wall carries on in plaster up to the roof (gable ends).
	 */
	private static void top(Pen g, Plot p, G q, int u, int v, Direction.Axis axis) {
		int gg = q.g(u, v);
		if (gg < 2) {
			return;
		}
		g.set(u, PLATE, v, log(Blocks.SPRUCE_LOG, axis));
		int ce = q.ceil(u, v);
		for (int y = PLATE + 1; y < ce; y++) {
			g.set(u, y, v, plaster(p, u, y, v));
		}
		if (!q.hip && (u == q.wu0 || u == q.wu1) && ce > PLATE + 1) {
			g.set(u, ce, v, plaster(p, u, ce, v)); // the gable face: plaster up to the roof, under the rake
		}
	}

	/** [depth] posts, shoji panes between them, a stone sill, a lintel; doors with their lintel; the partition. */
	private static void walls(Pen g, Plot p, G q) {
		BlockState paperU = paneU(glassPane(DyeColor.WHITE));
		BlockState paperV = paneV(glassPane(DyeColor.WHITE));
		// front wall
		for (int u = q.wu0; u <= q.wu1; u++) {
			int v = q.wv0;
			boolean doorS = u == q.dc || u == q.dc + 1;
			boolean doorM = u == q.md;
			if (q.post[u]) {
				column(g, u, v);
			} else if (doorS || doorM) {
				boolean left = !doorS || u == q.dc;
				g.set(u, 1, v, door(left, true));
				g.set(u, 2, v, door(left, false));
				g.set(u, 4, v, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
			} else {
				boolean win = u < q.pu;
				g.set(u, 1, v, stone(p, u, 1, v));
				g.set(u, 2, v, win ? paperU : plaster(p, u, 2, v));
				g.set(u, 3, v, win ? paperU : plaster(p, u, 3, v));
				g.set(u, 4, v, plaster(p, u, 4, v));
			}
			top(g, p, q, u, v, Direction.Axis.X);
		}
		// back wall
		for (int u = q.wu0; u <= q.wu1; u++) {
			int v = q.wv1;
			if (q.post[u]) {
				column(g, u, v);
			} else {
				boolean win = u == (q.mu0 + q.mu1) / 2;
				g.set(u, 1, v, stone(p, u, 1, v));
				g.set(u, 2, v, win ? paperU : plaster(p, u, 2, v));
				g.set(u, 3, v, win ? paperU : plaster(p, u, 3, v));
				g.set(u, 4, v, plaster(p, u, 4, v));
			}
			top(g, p, q, u, v, Direction.Axis.X);
		}
		// side walls
		for (int v = q.wv0 + 1; v < q.wv1; v++) {
			for (int u : new int[] {q.wu0, q.wu1}) {
				boolean win = u == q.wu0 ? (v - q.sv0) % 3 == 1 : v == (q.sv0 + q.sv1) / 2;
				g.set(u, 1, v, stone(p, u, 1, v));
				g.set(u, 2, v, win ? paperV : plaster(p, u, 2, v));
				g.set(u, 3, v, win ? paperV : plaster(p, u, 3, v));
				g.set(u, 4, v, plaster(p, u, 4, v));
				top(g, p, q, u, v, Direction.Axis.Z);
			}
		}
		// partition between the sala and the mizuya, with a doorway
		for (int v = q.wv0 + 1; v < q.wv1; v++) {
			int u = q.pu;
			int ce = q.ceil(u, v);
			boolean doorway = v == q.dv || v == q.dv + 1;
			for (int y = 1; y < ce; y++) {
				if (doorway && y <= 3) {
					continue;
				}
				g.set(u, y, v, doorway && y == 4 ? log(Blocks.SPRUCE_LOG, Direction.Axis.Z) : plaster(p, u, y, v));
			}
		}
	}

	// ------------------------------------------------------------------ interior

	/** [finish] the tea room and the mizuya, each element with a use. */
	private static void interior(Pen g, Plot p, G q) {
		// irori: a campfire in a stone cross, the kettle on it, zabuton round it
		g.set(q.hu, 0, q.hv, b(Blocks.CAMPFIRE).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH).setValue(BlockStateProperties.LIT, true));
		g.set(q.hu, 1, q.hv, b(Blocks.CAULDRON));
		// the kettle hangs from a chain (jizai-kagi); the chain also keeps the top of the kettle from being a floor
		for (int y = 2; y < q.ceil(q.hu, q.hv); y++) {
			g.put(q.hu, y, q.hv, Blocks.IRON_CHAIN.defaultBlockState(), CitadelCanvas.HANGING);
		}
		for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			g.set(q.hu + o[0], 0, q.hv + o[1], b(Blocks.POLISHED_ANDESITE));
		}
		for (int[] o : new int[][] {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
			g.set(q.hu + o[0], 1, q.hv + o[1], carpet(q.cushion));
		}
		// tokonoma: a scroll (open dark trapdoors) over the board, a flower beside it, two posts
		int tc = q.dc;
		g.set(tc, 1, q.sv1, b(Blocks.POTTED_BAMBOO));
		g.set(tc + 1, 1, q.sv1, b(Blocks.POTTED_CHERRY_SAPLING));
		g.set(tc, 2, q.sv1, trapdoor(Blocks.DARK_OAK_TRAPDOOR, Direction.NORTH, true, Half.BOTTOM));
		g.set(tc, 3, q.sv1, trapdoor(Blocks.DARK_OAK_TRAPDOOR, Direction.NORTH, true, Half.BOTTOM));
		for (int u : new int[] {tc - 1, tc + 2}) {
			if (u >= q.su0 && u <= q.su1) {
				for (int y = 1; y < q.ceil(u, q.sv1); y++) {
					g.set(u, y, q.sv1, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
				}
			}
		}
		// tie beams across the sala ceiling and two hanging lanterns
		for (int v : new int[] {q.sv0, q.sv1}) {
			for (int u = q.su0; u <= q.su1; u++) {
				if (v == q.sv1 && (u == tc || u == tc + 1)) {
					continue;
				}
				g.set(u, q.ceil(u, v) - 1, v, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
			}
		}
		for (int u : new int[] {q.su0, q.su1}) {
			g.put(u, q.ceil(u, q.hv) - 1, q.hv, lantern(true), CitadelCanvas.HANGING);
		}
		// mizuya: chest, barrel, water cauldron and a smoker as the kamado
		int mw = q.mu1 - q.mu0 + 1;
		g.set(q.mu0, 1, q.sv1, b(Blocks.CHEST).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		g.set(q.mu0 + 1, 1, q.sv1, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		if (mw >= 3) {
			g.set(q.mu0 + 2, 1, q.sv1, b(Blocks.WATER_CAULDRON));
			g.set(q.mu1, 1, q.sv1 - 1, b(Blocks.SMOKER).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
		} else {
			g.set(q.mu1, 1, q.sv1 - 1, b(Blocks.WATER_CAULDRON));
			g.set(q.mu1, 1, q.sv1 - 2, b(Blocks.SMOKER).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
		}
		g.put(q.mu0, q.ceil(q.mu0, q.hv) - 1, q.hv, lantern(true), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ engawa

	/** [depth] the veranda: posts and a beam under the eave, wide stone steps, a noren over the door, standing lanterns. */
	private static void engawa(Pen g, Plot p, G q) {
		for (int u = q.wu0; u <= q.wu1; u++) {
			if (q.post[u] && (u < q.dc - 1 || u > q.dc + 2)) {
				for (int y = 1; y <= 3; y++) {
					g.set(u, y, q.ef, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
				}
			}
			g.set(u, 4, q.ef, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
		}
		// steps up from the roji: first step in front, then the deck
		for (int u = q.dc - 1; u <= q.dc + 2; u++) {
			g.set(u, 0, q.gd, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
		}
		// the noren: a dyed valance hanging above head height, over the door
		for (int u : new int[] {q.dc, q.dc + 1}) {
			g.set(u, 4, q.wv0 - 1, paneU(glassPane(q.noren)));
		}
		for (int u : new int[] {q.dc - 2, q.dc + 3}) {
			if (u > q.wu0 && u < q.wu1 && !q.post[u]) {
				g.set(u, 1, q.ef + 1, lantern(false));
			}
		}
	}

	// ------------------------------------------------------------------ roji garden (v 0 .. gd-1)

	private static BlockState stepStone(Plot p, int u, int v) {
		return switch (p.hash(u, 0, v, 3)) {
			case 0 -> slab(Blocks.MOSSY_STONE_BRICK_SLAB);
			case 1 -> slab(Blocks.STONE_BRICK_SLAB);
			default -> slab(Blocks.POLISHED_ANDESITE_SLAB);
		};
	}

	private static void garden(Pen g, Plot p, G q) {
		int w = q.w;
		int gd = q.gd;
		int gx = q.gx;
		boolean[][] fen = new boolean[w][gd];
		boolean[][] pst = new boolean[w][gd];
		for (int u = 0; u < w; u++) {
			fen[u][0] = true;
		}
		for (int v = 0; v < gd; v++) {
			fen[0][v] = true;
			fen[w - 1][v] = true;
		}
		// the chumon: two posts each side, a beam, a small roof over a 3-wide opening
		for (int u = gx - 2; u <= gx + 2; u++) {
			fen[u][0] = false;
		}
		for (int u : new int[] {gx - 2, gx + 2}) {
			pst[u][0] = true;
			for (int y = 0; y <= 3; y++) {
				g.set(u, y, 0, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
			}
		}
		for (int u = gx - 2; u <= gx + 2; u++) {
			g.set(u, 4, 0, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
		}
		for (int u = gx - 3; u <= gx + 3; u++) {
			g.set(u, 5, 0, stair(q.roof.stair(), Direction.SOUTH));
			g.set(u, 5, 1, stair(q.roof.stair(), Direction.NORTH));
		}
		g.put(gx, 3, 0, lantern(true), CitadelCanvas.HANGING);
		// machiai: the waiting bench against the fence, roofed on wide plots
		int bu = q.bu;
		boolean shelter = w >= 14;
		if (shelter) {
			for (int u : new int[] {bu - 2, bu + 2}) {
				fen[u][0] = false;
				for (int v = 0; v <= 1; v++) {
					pst[u][v] = true;
					for (int y = 0; y <= 3; y++) {
						g.set(u, y, v, fence(Blocks.SPRUCE_FENCE, false, false, false, false));
					}
				}
			}
			for (int u = bu - 2; u <= bu + 2; u++) {
				g.set(u, 4, 0, stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
				g.set(u, 4, 1, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH));
			}
		}
		for (int u = bu - 1; u <= bu + 1; u++) {
			g.set(u, 0, 1, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH));
		}
		// the fence itself
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < gd; v++) {
				if (!fen[u][v]) {
					continue;
				}
				boolean n = v > 0 && (fen[u][v - 1] || pst[u][v - 1]);
				boolean s = v + 1 < gd && (fen[u][v + 1] || pst[u][v + 1]);
				boolean we = u > 0 && (fen[u - 1][v] || pst[u - 1][v]);
				boolean e = u + 1 < w && (fen[u + 1][v] || pst[u + 1][v]);
				g.set(u, 0, v, fence(Blocks.BAMBOO_FENCE, n, e, s, we));
			}
		}
		// roji: stepping stones from the gate to the steps
		for (int v = 1; v < gd; v++) {
			int u = gx + (int) Math.round((q.sx - gx) * (v - 1) / (double) (gd - 2));
			if (g.free(u, 0, v)) {
				g.set(u, 0, v, stepStone(p, u, v));
			}
			if (v % 2 == 1 && g.free(u + 1, 0, v)) {
				g.set(u + 1, 0, v, stepStone(p, u + 1, v));
			}
		}
		// tsukubai: a low basin of water on a mossy stone, a bamboo spout, a hand stone; an ishidoro beyond
		int tv = gd - 1;
		int tu = q.sx + 3;
		if (g.free(tu, 0, tv) && g.free(tu + 1, 0, tv) && g.free(tu - 1, 0, tv)) {
			g.set(tu, 0, tv, b(Blocks.WATER_CAULDRON));
			g.set(tu - 1, 0, tv, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
			g.set(tu + 1, 0, tv, b(Blocks.MOSSY_COBBLESTONE));
			g.set(tu + 1, 1, tv, b(Blocks.BAMBOO));
			g.set(tu + 1, 2, tv, b(Blocks.BAMBOO));
		}
		int lu = Math.min(w - 3, tu + 3);
		if (g.free(lu, 0, tv) && g.free(lu, 3, tv)) {
			JapaneseKit.stoneLantern(g.c, g.p, g.mu(lu), 0, tv);
		}
		// azaleas, moss and ferns on what is left
		for (int[] a : new int[][] {{1, gd - 1}, {w - 2, gd - 1}, {1, 1}}) {
			if (g.free(a[0], 0, a[1]) && g.free(a[0], 1, a[1])) {
				g.set(a[0], 0, a[1], CitadelCanvas.leaves(p.hash(a[0], 0, a[1], 2) == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
			}
		}
		for (int v = 1; v < gd; v++) {
			for (int u = 1; u < w - 1; u++) {
				if (!g.free(u, 0, v)) {
					continue;
				}
				int hsh = p.hash(u, 0, v, 100);
				if (hsh < 45) {
					g.set(u, 0, v, b(Blocks.MOSS_CARPET));
				} else if (hsh < 55) {
					g.set(u, 0, v, b(Blocks.FERN));
				}
			}
		}
	}
}
