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
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.stairTop;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.trapdoor;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * A Japanese house lived in from the veranda: a raised stone plinth, an engawa (plank veranda, 2 wide, 5 clear
 * blocks) all the way round under deep tiled eaves, shoji walls between dark posts, and inside a tatami / study
 * room with a low table, a kitchen, a bedroom and a store of chests. The sliding shoji of the garden side open
 * on a 3-5 wide doorway; the garden in front of it has a stone path, a stone lantern, a tsukubai and a small
 * raised pond.
 *
 * <p>The footprint is a set of rectangles and every part of the house is read from the ring (Chebyshev distance
 * to the outside) of a cell: ring 0 is the eave tip and plinth lip, rings 1-2 the engawa (posts on ring 1),
 * ring 3 the room walls, ring 4 and up the interior. The roof is a hip roof read from the same rings, so an
 * L-shaped footprint gets its valleys for free. The whole house is built in a virtual frame and turned by seed:
 * the garden (and so the veranda) can be on the front, the left or the right of the plot, and the roof is low,
 * medium or steep.
 *
 * <pre>
 *  section across the garden side (v →)
 *  y11+    ridge, cap slab and finials
 *  y7-10   hip roof (stairs and tiles), eave curled up at the corners
 *  y6      beam on the posts / on the wall
 *  y2-5    4 clear blocks: posts, shoji (trapdoor, panes), doorway
 *  y1      deck boards / tatami floor
 *  y0      stone plinth (a lip of slabs at the edge, steps at the doorway)
 *
 *  plan (garden in front)           rings
 *  v0      hedge, gate              0 eave tip, plinth lip
 *  v1..g   path, pond, lantern      1 posts, engawa
 *  g       steps                    2 engawa
 *  g+3     shoji wall, doorway      3 wall
 *  g+4..   zashiki, kitchen | bed | store
 * </pre>
 *
 * Size: 16–22 × 16–22 × 13+ (the interior is the plot minus 8 each way, the garden takes 3-5 rows). An L-shaped
 * house (a wing towards the garden) appears from 22 × 20.
 */
public final class HouseJapaneseEngawa implements Variant {
	private static final int FL = 1; // the floor blocks (deck, tatami); people stand on y2
	private static final int ST = 2; // the layer people stand in
	private static final int BEAM = 6; // beam on posts and walls; 4 clear blocks y2..y5 under it
	private static final int BASE = 6; // roof height of ring 0 (the eave tip)

	@Override
	public String id() {
		return "house_japanese_engawa";
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
		return 16;
	}

	@Override
	public int minH() {
		return 13;
	}

	@Override
	public int maxW() {
		return 22;
	}

	@Override
	public int maxD() {
		return 22;
	}

	/** [palette] per seed: post wood, fascia, deck boards. */
	private record Scheme(Block post, Block fascia, Block deckA, Block deckB) {
	}

	private static final Scheme[] SCHEMES = {
			new Scheme(Blocks.DARK_OAK_LOG, Blocks.CHERRY_STAIRS, Blocks.BAMBOO_MOSAIC, Blocks.SPRUCE_PLANKS),
			new Scheme(Blocks.DARK_OAK_LOG, Blocks.CHERRY_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS),
			new Scheme(Blocks.SPRUCE_LOG, Blocks.SPRUCE_STAIRS, Blocks.BAMBOO_MOSAIC, Blocks.OAK_PLANKS),
	};

	private record Rect(int a0, int b0, int a1, int b1) {
		int w() {
			return a1 - a0 + 1;
		}

		int d() {
			return b1 - b0 + 1;
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x454E_4741_5741L);
		Scheme s = SCHEMES[r.nextInt(SCHEMES.length)];
		boolean swap = r.nextBoolean();
		boolean mirror = r.nextBoolean();
		int style = r.nextInt(3);
		boolean tableLeft = r.nextBoolean();
		boolean kitchenLeft = r.nextBoolean();
		boolean pondLeft = r.nextBoolean();
		boolean wantL = r.nextBoolean();
		new Builder(c, p, s, swap, mirror, style, tableLeft, kitchenLeft, pondLeft, wantL).run();
	}

	private static final Direction[] DIRS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
	private static final int[] DA = {0, 0, 1, -1};
	private static final int[] DB = {-1, 1, 0, 0};

	/** One build, in a virtual frame (a across, b front to back) that is turned onto the plot. */
	private static final class Builder {
		final CitadelCanvas c;
		final Plot p;
		final Scheme s;
		final boolean swap;
		final boolean mirror;
		final boolean tableLeft;
		final boolean kitchenLeft;
		final boolean pondLeft;
		final boolean lShape;
		int style;
		final int vw;
		final int vd;
		int gd; // garden rows in front of the main block (single) or the courtyard depth (L)
		int bw; // L: width of the wing
		int ga; // L: first row of the main block
		final boolean[][] in;
		final int[][] ring;
		final int[][] hc;
		int fb; // the row of the front eave tip of the block that has the doorway
		int ac; // doorway column
		int ow; // doorway width
		Rect gar; // garden rectangle
		Rect zone1; // the guest tatami room (L only)
		Rect zone; // the main tatami room / hall
		Rect band; // the service rooms
		int zoneCols;
		int rmax;
		boolean[][] corner;

		Builder(CitadelCanvas c, Plot p, Scheme s, boolean swap, boolean mirror, int style, boolean tableLeft, boolean kitchenLeft, boolean pondLeft,
				boolean wantL) {
			this.c = c;
			this.p = p;
			this.s = s;
			this.swap = swap;
			this.mirror = mirror;
			this.style = style;
			this.tableLeft = tableLeft;
			this.kitchenLeft = kitchenLeft;
			this.pondLeft = pondLeft;
			this.vw = swap ? p.d() : p.w();
			this.vd = swap ? p.w() : p.d();
			this.lShape = wantL && vw >= 22 && vd >= 20;
			this.in = new boolean[vw][vd];
			this.ring = new int[vw][vd];
			this.hc = new int[vw][vd];
			this.corner = new boolean[vw][vd];
		}

		// ------------------------------------------------------------------ the virtual frame

		int mu(int a, int bb) {
			int u = swap ? bb : a;
			return mirror ? p.w() - 1 - u : u;
		}

		int mv(int a, int bb) {
			return swap ? a : bb;
		}

		BlockState xf(BlockState st) {
			BlockState t = swap ? st.rotate(Rotation.CLOCKWISE_90).mirror(Mirror.FRONT_BACK) : st;
			return mirror ? t.mirror(Mirror.FRONT_BACK) : t;
		}

		void set(int a, int y, int bb, BlockState st) {
			p.put(c, mu(a, bb), y, mv(a, bb), xf(st), CitadelCanvas.SOLID);
		}

		void put(int a, int y, int bb, BlockState st, int layer) {
			p.put(c, mu(a, bb), y, mv(a, bb), xf(st), layer);
		}

		void air(int a, int y, int bb) {
			set(a, y, bb, CitadelCanvas.AIR);
		}

		boolean inF(int a, int bb) {
			return a >= 0 && a < vw && bb >= 0 && bb < vd && in[a][bb];
		}

		int ringAt(int a, int bb) {
			return inF(a, bb) ? ring[a][bb] : -1;
		}

		void run() {
			layout();
			floors();
			garden();
			program();
			walls();
			roof();
		}

		// ------------------------------------------------------------------ layout: footprint, rings, rooms

		static BlockState faced(Block bl, Direction f) {
			BlockState st = b(bl);
			return st.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? st.setValue(BlockStateProperties.HORIZONTAL_FACING, f) : st;
		}

		static BlockState barrel() {
			return b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
		}

		void fillRect(int a0, int b0, int a1, int b1) {
			for (int a = a0; a <= a1; a++) {
				for (int bb = b0; bb <= b1; bb++) {
					in[a][bb] = true;
				}
			}
		}

		boolean outsideAt(int a, int bb, int k) {
			for (int da = -k; da <= k; da++) {
				for (int db = -k; db <= k; db++) {
					if (Math.max(Math.abs(da), Math.abs(db)) == k && !inF(a + da, bb + db)) {
						return true;
					}
				}
			}
			return false;
		}

		int g(int r) {
			return switch (style) {
				case 0 -> (r + 1) / 2;
				case 1 -> r <= 2 ? (r + 1) / 2 : r - 1;
				default -> r;
			};
		}

		void layout() {
			if (lShape) {
				bw = Math.max(13, vw * 11 / 20);
				ga = vd - 13;
				gd = ga;
				fb = ga;
				fillRect(0, 1, bw - 1, ga + 4);
				fillRect(0, ga, vw - 1, vd - 1);
				gar = new Rect(bw, 0, vw - 1, ga - 1);
			} else {
				gd = vd >= 21 ? 5 : vd >= 18 ? 4 : 3;
				fb = gd;
				fillRect(0, gd, vw - 1, vd - 1);
				gar = new Rect(0, 0, vw - 1, gd - 1);
			}
			rmax = 0;
			for (int a = 0; a < vw; a++) {
				for (int bb = 0; bb < vd; bb++) {
					if (!in[a][bb]) {
						ring[a][bb] = -1;
						hc[a][bb] = -1000;
						continue;
					}
					int k = 1;
					while (k < 40 && !outsideAt(a, bb, k)) {
						k++;
					}
					ring[a][bb] = k - 1;
					rmax = Math.max(rmax, k - 1);
				}
			}
			// a roof that fits the plot: the ridge cap and its finial must stay under the top
			while (style > 0 && BASE + g(rmax) + 2 > p.h() - 1) {
				style--;
			}
			for (int a = 0; a < vw; a++) {
				for (int bb = 0; bb < vd; bb++) {
					if (in[a][bb]) {
						hc[a][bb] = BASE + g(ring[a][bb]);
					}
				}
			}
			// rooms
			if (lShape) {
				zone1 = new Rect(4, 5, bw - 5, ga + 3);
				int idA = (vd - 5) - (ga + 4) + 1;
				int sd = idA >= 6 ? 3 : 2;
				int hall = idA - sd;
				zone = new Rect(4, ga + 4, vw - 5, ga + 3 + hall);
				band = new Rect(4, ga + 4 + hall, vw - 5, vd - 5);
			} else {
				int id = vd - 5 - (gd + 4) + 1;
				int sd = id >= 8 ? 4 : id >= 6 ? 3 : 2;
				int fd = id - sd;
				zone = new Rect(4, gd + 4, vw - 5, gd + 3 + fd);
				band = new Rect(4, gd + 4 + fd, vw - 5, vd - 5);
			}
			boolean left = lShape || tableLeft;
			zoneCols = zone.w() >= 7 ? 3 : 2;
			int f0 = left ? zone.a0() + zoneCols : zone.a0();
			int f1 = left ? zone.a1() : zone.a1() - zoneCols;
			if (zone.w() >= 11) {
				if (left) {
					f1--;
				} else {
					f0++;
				}
			}
			int fw = f1 - f0 + 1;
			ow = fw >= 7 ? 5 : 3;
			ac = (f0 + f1) / 2;
			if (lShape) {
				ac = Math.max(bw + 1, Math.min(vw - 7, ac));
			}
			ac = Math.max(f0 + ow / 2, Math.min(f1 - ow / 2, ac));
			// corners of the roof's outer ring
			for (int a = 0; a < vw; a++) {
				for (int bb = 0; bb < vd; bb++) {
					if (in[a][bb] && ring[a][bb] == 0) {
						boolean vertLow = !inF(a, bb - 1) || !inF(a, bb + 1);
						boolean horLow = !inF(a - 1, bb) || !inF(a + 1, bb);
						corner[a][bb] = vertLow && horLow;
					}
				}
			}
		}

		/** Run direction of an outer-ring cell: 'A' along a, 'B' along b, 'C' corner. {@code k} is its ring. */
		char runAxis(int a, int bb, int k) {
			boolean vert = ringAt(a, bb - 1) == k - 1 || ringAt(a, bb + 1) == k - 1;
			boolean hor = ringAt(a + 1, bb) == k - 1 || ringAt(a - 1, bb) == k - 1;
			if (vert && hor) {
				return 'C';
			}
			if (vert) {
				return 'A';
			}
			if (hor) {
				return 'B';
			}
			return 'C';
		}

		boolean inEntryZone(int a, int bb) {
			return bb >= fb && bb <= fb + 2 && a >= ac - ow / 2 - 1 && a <= ac + ow / 2 + 1;
		}

		boolean entryCol(int a) {
			return a >= ac - ow / 2 && a <= ac + ow / 2;
		}

		// ------------------------------------------------------------------ materials

		BlockState stone(int a, int y, int bb) {
			return switch (p.hash(a, y, bb, 6)) {
				case 0 -> b(Blocks.MOSSY_STONE_BRICKS);
				case 1 -> b(Blocks.CRACKED_STONE_BRICKS);
				default -> b(Blocks.STONE_BRICKS);
			};
		}

		BlockState deck(int a, int bb) {
			return ((a + bb) & 1) == 0 && p.hash(a, 0, bb, 3) != 0 ? b(s.deckA()) : b(s.deckB());
		}

		BlockState plaster(int a, int y, int bb) {
			return p.hash(a, y, bb, 9) == 0 ? b(Blocks.TUFF_BRICKS) : b(Blocks.CALCITE);
		}

		// ------------------------------------------------------------------ plinth and floors

		void floors() {
			for (int a = 0; a < vw; a++) {
				for (int bb = 0; bb < vd; bb++) {
					if (!in[a][bb]) {
						continue;
					}
					int r = ring[a][bb];
					set(a, 0, bb, stone(a, 0, bb));
					if (r == 0) {
						boolean step = bb == fb && entryCol(a) && !inF(a, bb - 1);
						set(a, FL, bb, step ? stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH) : slab(Blocks.POLISHED_DEEPSLATE_SLAB));
					} else if (r <= 2) {
						set(a, FL, bb, deck(a, bb));
					} else if (r == 3) {
						set(a, FL, bb, stone(a, 1, bb));
					} else {
						set(a, FL, bb, b(Blocks.DARK_OAK_PLANKS));
					}
				}
			}
		}

		// ------------------------------------------------------------------ walls, posts and beams

		boolean cornerPost(int a, int bb) {
			return inF(a, bb) && ring[a][bb] == 1 && runAxis(a, bb, 1) == 'C';
		}

		boolean isPost(int a, int bb) {
			char ax = runAxis(a, bb, 1);
			if (ax == 'C') {
				return true;
			}
			if (inEntryZone(a, bb)) {
				return false;
			}
			int idx = ax == 'A' ? a : bb;
			if (idx % 3 != 0) {
				return false;
			}
			return !(cornerPost(a - 1, bb) || cornerPost(a + 1, bb) || cornerPost(a, bb - 1) || cornerPost(a, bb + 1));
		}

		boolean isPillar(int a, int bb) {
			char ax = runAxis(a, bb, 3);
			if (ax == 'C') {
				return true;
			}
			if (bb == fb + 3 && ax == 'A' && (a == ac - ow / 2 - 1 || a == ac + ow / 2 + 1)) {
				return true;
			}
			int idx = ax == 'A' ? a : bb;
			return idx % 3 == 0;
		}

		void walls() {
			for (int a = 0; a < vw; a++) {
				for (int bb = 0; bb < vd; bb++) {
					if (!in[a][bb]) {
						continue;
					}
					int r = ring[a][bb];
					if (r == 1) {
						char ax = runAxis(a, bb, 1);
						set(a, BEAM, bb, log(Blocks.DARK_OAK_LOG, ax == 'B' ? Direction.Axis.Z : Direction.Axis.X));
						if (isPost(a, bb)) {
							set(a, FL, bb, stone(a, 2, bb)); // the foundation stone under the post
							for (int y = ST; y <= BEAM - 1; y++) {
								set(a, y, bb, log(s.post(), Direction.Axis.Y));
							}
						} else if (!inEntryZone(a, bb) && (ax == 'A' ? a : bb) % 3 == 1 && p.hash(a, 3, bb, 2) == 0) {
							put(a, BEAM - 1, bb, lantern(true), CitadelCanvas.HANGING); // [finish] a lantern between the posts
						}
					} else if (r == 3) {
						char ax = runAxis(a, bb, 3);
						boolean alongA = ax != 'B';
						boolean doorway = bb == fb + 3 && ax == 'A' && entryCol(a);
						boolean pillar = isPillar(a, bb);
						set(a, BEAM, bb, log(Blocks.DARK_OAK_LOG, alongA ? Direction.Axis.X : Direction.Axis.Z));
						for (int y = ST; y <= BEAM - 1; y++) {
							BlockState st;
							if (doorway) {
								st = CitadelCanvas.AIR;
							} else if (pillar) {
								st = log(s.post(), Direction.Axis.Y);
							} else if (y == ST) {
								st = trapdoor(Blocks.BIRCH_TRAPDOOR, alongA ? Direction.NORTH : Direction.WEST, true, Half.BOTTOM);
							} else if (y == BEAM - 1 || y == ST + 1) {
								st = plaster(a, y, bb);
							} else if ((alongA ? a : bb) % 3 == 2) {
								st = b(Blocks.BIRCH_PLANKS);
							} else {
								st = alongA ? paneU(glassPane(DyeColor.WHITE)) : paneV(glassPane(DyeColor.WHITE));
							}
							set(a, y, bb, st);
						}
						// the plaster band between the beam and the roof
						for (int y = BEAM + 1; y < hc[a][bb]; y++) {
							set(a, y, bb, pillar ? log(s.post(), Direction.Axis.Y) : plaster(a, y, bb));
						}
					}
				}
			}
		}

		// ------------------------------------------------------------------ roof

		boolean isRidge(int a, int bb) {
			int r = ring[a][bb];
			if (r < 2) {
				return false;
			}
			for (int da = -1; da <= 1; da++) {
				for (int db = -1; db <= 1; db++) {
					if (inF(a + da, bb + db) && ring[a + da][bb + db] > r) {
						return false;
					}
				}
			}
			return true;
		}

		static Direction cornerFacing(boolean top, boolean left) {
			if (top) {
				return left ? Direction.SOUTH : Direction.WEST;
			}
			return left ? Direction.EAST : Direction.NORTH;
		}

		boolean nearCorner(int a, int bb) {
			for (int i = -1; i <= 1; i++) {
				for (int j = -1; j <= 1; j++) {
					if (Math.abs(i) + Math.abs(j) <= 1 && a + i >= 0 && a + i < vw && bb + j >= 0 && bb + j < vd && corner[a + i][bb + j]) {
						return true;
					}
				}
			}
			return false;
		}

		void roof() {
			for (int a = 0; a < vw; a++) {
				for (int bb = 0; bb < vd; bb++) {
					if (!in[a][bb]) {
						continue;
					}
					int r = ring[a][bb];
					int h = hc[a][bb];
					boolean[] up = new boolean[4];
					boolean[] low = new boolean[4];
					int firstUp = -1;
					for (int i = 0; i < 4; i++) {
						int na = a + DA[i];
						int nb = bb + DB[i];
						int nh = inF(na, nb) ? hc[na][nb] : -1000;
						up[i] = nh > h;
						low[i] = nh < h;
						if (up[i] && firstUp < 0) {
							firstUp = i;
						}
					}
					Block mat = r == 0 ? s.fascia() : r == 1 ? Blocks.STONE_BRICK_STAIRS : r % 3 == 2 ? Blocks.DEEPSLATE_BRICK_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS;
					boolean ridge = isRidge(a, bb);
					int y = h;
					if (ridge) {
						set(a, h, bb, b(Blocks.CHERRY_PLANKS));
						set(a, h + 1, bb, slab(Blocks.CHERRY_SLAB));
						int rn = 0;
						boolean opp = false;
						boolean ns = false;
						boolean ew = false;
						for (int i = 0; i < 4; i++) {
							int na = a + DA[i];
							int nb = bb + DB[i];
							if (inF(na, nb) && isRidge(na, nb)) {
								rn++;
								if (i < 2) {
									ns = true;
								} else {
									ew = true;
								}
							}
						}
						opp = rn == 2 && (ns != ew);
						if (rn <= 1 || (rn == 2 && !opp)) {
							// [finish] onigawara: a raised end block and a rod at the ridge ends
							set(a, h + 1, bb, b(Blocks.DEEPSLATE_BRICKS));
							set(a, h + 2, bb, b(Blocks.GOLD_BLOCK));
							if (h + 3 < p.h()) {
								set(a, h + 3, bb, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
							}
						}
						continue;
					}
					if (firstUp >= 0) {
						BlockState st = stair(mat, DIRS[firstUp]);
						boolean curl = r == 0 && nearCorner(a, bb);
						if (curl) {
							y = h + 1;
							Direction out = DIRS[firstUp].getOpposite();
							set(a, h, bb, stairTop(s.fascia(), out)); // [depth] the eave tip curls up, an upside-down stair scoops under it
						}
						set(a, y, bb, st);
						continue;
					}
					int vi = low[0] ? 0 : low[1] ? 1 : -1;
					int hi = low[3] ? 3 : low[2] ? 2 : -1;
					if (vi >= 0 && hi >= 0) {
						Direction f = cornerFacing(vi == 0, hi == 3);
						boolean curl = r == 0;
						if (curl) {
							y = h + 1;
							set(a, h, bb, stair(s.fascia(), f, Half.TOP, StairsShape.OUTER_LEFT));
							put(a, h - 1, bb, lantern(true), CitadelCanvas.HANGING); // [finish] a lantern under each lifted corner
						}
						set(a, y, bb, stair(mat, f, Half.BOTTOM, StairsShape.OUTER_LEFT));
					} else {
						set(a, h, bb, r % 3 == 2 ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_TILES));
					}
				}
			}
		}

		// ------------------------------------------------------------------ the rooms

		BlockState bed(DyeColor color, BedPart part) {
			return b(Blocks.BED.pick(color)).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH).setValue(BlockStateProperties.BED_PART, part);
		}

		void hang(int a, int bb) {
			put(a, hc[a][bb] - 1, bb, lantern(true), CitadelCanvas.HANGING);
		}

		void program() {
			zashiki(zone, lShape || tableLeft, zoneCols);
			if (lShape) {
				zashiki(zone1, false, 2);
			}
			service();
		}

		/** A tatami room: moss-carpet mats on boards, tansu chests and shelves on one wall, a low table with cushions. */
		void zashiki(Rect z, boolean left, int cols) {
			for (int a = z.a0(); a <= z.a1(); a++) {
				for (int bb = z.b0(); bb <= z.b1(); bb++) {
					set(a, FL, bb, ((a + bb) & 1) == 0 ? b(Blocks.SPRUCE_PLANKS) : b(Blocks.BAMBOO_MOSAIC));
					set(a, ST, bb, b(Blocks.MOSS_CARPET));
				}
			}
			if (z == zone) {
				// the genkan: a stone-floored entry right inside the doorway
				int depth = z.d() >= 3 ? 2 : 1;
				for (int a = ac - ow / 2; a <= ac + ow / 2; a++) {
					for (int k = 0; k < depth; k++) {
						set(a, FL, z.b0() + k, stone(a, 1, z.b0() + k));
						air(a, ST, z.b0() + k);
					}
				}
			}
			if (z.w() >= 11) {
				// a study along the far wall: crafting, lectern, books, loom
				Block[] desk = {Blocks.CRAFTING_TABLE, Blocks.LECTERN, Blocks.CHISELED_BOOKSHELF, Blocks.LOOM};
				int oside = left ? z.a1() : z.a0();
				for (int bb = z.b0(); bb <= z.b1(); bb++) {
					set(oside, ST, bb, faced(desk[(bb - z.b0()) % desk.length], left ? Direction.WEST : Direction.EAST));
				}
			}
			int side = left ? z.a0() : z.a1();
			int dir = left ? 1 : -1;
			Direction inward = left ? Direction.EAST : Direction.WEST;
			for (int bb = z.b0(); bb <= z.b1(); bb++) {
				int k = bb - z.b0();
				set(side, ST, bb, k % 2 == 0 ? faced(Blocks.CHEST, inward) : faced(Blocks.CHISELED_BOOKSHELF, inward));
				set(side + dir, ST, bb, slab(Blocks.SPRUCE_SLAB));
				if (cols >= 3 && k % 2 == 0) {
					set(side + 2 * dir, ST, bb, stair(Blocks.SPRUCE_STAIRS, inward));
				}
			}
			// light: a lantern over the table and one over the free floor
			hang(side + dir, (z.b0() + z.b1()) / 2);
			int free = left ? side + cols + (z.w() - cols) / 2 : z.a0() + (z.w() - cols) / 2;
			hang(free, (z.b0() + z.b1()) / 2);
		}

		/** Kitchen, bedroom and store side by side along the back; each opens to the tatami room across its whole width. */
		void service() {
			int n = band.w() >= 9 ? 3 : 2;
			int avail = band.w() - (n - 1);
			int[] ws = new int[n];
			if (n == 3) {
				ws[0] = avail / 3;
				ws[2] = avail / 3;
				ws[1] = avail - ws[0] - ws[2];
			} else {
				ws[0] = avail / 2;
				ws[1] = avail - ws[0];
			}
			// kind: 0 kitchen, 1 bedroom, 2 store
			int[] kind = n == 3 ? new int[] {0, 1, 2} : new int[] {0, 1};
			if (!kitchenLeft) {
				for (int i = 0; i < n / 2; i++) {
					int t = kind[i];
					kind[i] = kind[n - 1 - i];
					kind[n - 1 - i] = t;
				}
			}
			int x = band.a0();
			int s0 = band.b0();
			int s1 = band.b1();
			for (int i = 0; i < n; i++) {
				int x0 = x;
				int x1 = x + ws[i] - 1;
				switch (kind[i]) {
					case 0 -> kitchen(x0, x1, s0, s1);
					case 1 -> bedroom(x0, x1, s0, s1);
					default -> store(x0, x1, s0, s1);
				}
				if (i < n - 1) {
					divider(x1 + 1, s0, s1);
				}
				x = x1 + 2;
			}
		}

		void divider(int a, int s0, int s1) {
			for (int bb = s0; bb <= s1; bb++) {
				set(a, FL, bb, stone(a, 1, bb));
				boolean end = bb == s0;
				for (int y = ST; y < hc[a][bb]; y++) {
					BlockState st;
					if (y == BEAM) {
						st = log(Blocks.DARK_OAK_LOG, Direction.Axis.Z);
					} else if (end && y < BEAM) {
						st = log(s.post(), Direction.Axis.Y);
					} else if (y == ST) {
						st = b(Blocks.DARK_OAK_PLANKS);
					} else if (y < BEAM) {
						st = y == BEAM - 1 ? b(Blocks.BIRCH_PLANKS) : b(Blocks.CALCITE);
					} else {
						st = plaster(a, y, bb);
					}
					set(a, y, bb, st);
				}
			}
		}

		void kitchen(int x0, int x1, int s0, int s1) {
			for (int a = x0; a <= x1; a++) {
				for (int bb = s0; bb <= s1; bb++) {
					set(a, FL, bb, p.hash(a, 1, bb, 3) == 0 ? b(Blocks.STONE_BRICKS) : b(Blocks.MUD_BRICKS));
				}
			}
			Block[] kit = {Blocks.FURNACE, Blocks.SMOKER, Blocks.CRAFTING_TABLE, Blocks.BARREL, Blocks.WATER_CAULDRON};
			for (int a = x0; a <= x1; a++) {
				Block bl = kit[(a - x0) % kit.length];
				set(a, ST, s1, bl == Blocks.BARREL ? barrel() : faced(bl, Direction.NORTH));
			}
			if (s1 - s0 >= 2) {
				set(x0, ST, s1 - 1, barrel());
			}
			hang((x0 + x1) / 2, s0 + (s1 - s0) / 2);
		}

		void bedroom(int x0, int x1, int s0, int s1) {
			for (int a = x0; a <= x1; a++) {
				for (int bb = s0; bb <= s1; bb++) {
					set(a, FL, bb, b(Blocks.SPRUCE_PLANKS));
					set(a, ST, bb, b(Blocks.MOSS_CARPET));
				}
			}
			for (int a = x0; a <= x1; a++) {
				boolean bedCol = a == x0 || (a == x1 && x1 - x0 >= 2);
				if (bedCol) {
					DyeColor col = a == x0 ? DyeColor.WHITE : DyeColor.RED;
					set(a, ST, s1 - 1, bed(col, BedPart.FOOT));
					set(a, ST, s1, bed(col, BedPart.HEAD));
				} else {
					set(a, ST, s1, faced(Blocks.CHEST, Direction.NORTH));
				}
			}
			hang((x0 + x1) / 2, s0);
		}

		void store(int x0, int x1, int s0, int s1) {
			for (int a = x0; a <= x1; a++) {
				for (int bb = s0; bb <= s1; bb++) {
					set(a, FL, bb, b(Blocks.DARK_OAK_PLANKS));
				}
				set(a, ST, s1, faced(Blocks.CHEST, Direction.NORTH));
				if (s1 - s0 >= 2 && (a - x0) % 2 == 0) {
					set(a, ST, s1 - 1, barrel());
				}
			}
			hang((x0 + x1) / 2, s0);
		}

		// ------------------------------------------------------------------ the garden

		boolean[][] fenceCell;

		void garden() {
			int g0 = gar.a0();
			int g1 = gar.a1();
			int gb = gar.b1();
			fenceCell = new boolean[vw][vd];
			// ground: moss, gravel and coarse dirt in patches
			for (int a = g0; a <= g1; a++) {
				for (int bb = 1; bb <= gb; bb++) {
					BlockState st = switch (p.hash(a, 0, bb, 6)) {
						case 0 -> b(Blocks.MOSS_BLOCK);
						case 1 -> b(Blocks.COARSE_DIRT);
						case 2, 3 -> b(Blocks.GRAVEL);
						default -> b(Blocks.MOSS_BLOCK);
					};
					set(a, 0, bb, st);
				}
			}
			// the path to the steps, 3 wide: stepping stones set in gravel
			for (int a = ac - 1; a <= ac + 1; a++) {
				for (int bb = 1; bb <= gb; bb++) {
					set(a, 0, bb, ((a + bb) & 1) == 0 ? b(Blocks.POLISHED_ANDESITE) : b(Blocks.STONE_BRICKS));
				}
			}
			// the fence line along the street and the outer side
			for (int a = g0; a <= g1; a++) {
				boolean gate = a >= ac - 1 && a <= ac + 1;
				set(a, 0, 0, gate ? stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH) : stone(a, 0, 0));
				if (!gate) {
					fenceCell[a][0] = true;
				}
			}
			for (int bb = 1; bb <= gb; bb++) {
				fenceCell[g1][bb] = true;
				if (!lShape) {
					fenceCell[g0][bb] = true;
				}
			}
			for (int a = g0; a <= g1; a++) {
				for (int bb = 0; bb <= gb; bb++) {
					if (!fenceCell[a][bb]) {
						continue;
					}
					boolean n = bb > 0 && fenceCell[a][bb - 1];
					boolean sS = bb < gb && fenceCell[a][bb + 1];
					boolean e = a < g1 && fenceCell[a + 1][bb];
					boolean w = a > g0 && fenceCell[a - 1][bb];
					for (int y = 1; y <= 2; y++) {
						set(a, y, bb, fence(Blocks.BAMBOO_FENCE, n, e, sS, w));
					}
					if (p.hash(a, 1, bb, 4) == 0) {
						set(a, 3, bb, b(Blocks.BAMBOO));
						set(a, 4, bb, b(Blocks.BAMBOO));
					}
				}
			}
			// the gate: two posts with a lantern
			for (int a : new int[] {ac - 2, ac + 2}) {
				if (a >= g0 && a <= g1) {
					for (int y = 1; y <= 3; y++) {
						set(a, y, 0, log(s.post(), Direction.Axis.Y));
					}
					set(a, 4, 0, lantern(false));
				}
			}
			// pond on the wider side, the rest on the other
			int leftLo = lShape ? g0 : g0 + 1;
			int leftHi = ac - 2;
			int rightLo = ac + 2;
			int rightHi = g1 - 1;
			boolean leftOk = leftHi - leftLo + 1 >= 5;
			boolean rightOk = rightHi - rightLo + 1 >= 5;
			boolean pondOnLeft = leftOk && (pondLeft || !rightOk);
			boolean pondOnRight = rightOk && !pondOnLeft;
			if (pondOnLeft) {
				pond(leftLo + 1, gb);
			} else if (pondOnRight) {
				pond(rightHi - 3, gb);
			}
			int lo = pondOnLeft ? rightLo : leftLo;
			int hi = pondOnLeft ? rightHi : leftHi;
			if (pondOnRight) {
				lo = leftLo;
				hi = leftHi;
			}
			if (!pondOnLeft && !pondOnRight) {
				lo = leftHi - leftLo >= rightHi - rightLo ? leftLo : rightLo;
				hi = leftHi - leftLo >= rightHi - rightLo ? leftHi : rightHi;
			}
			boolean nearHigh = lo < ac; // the near end is hi when the free side is left of the path
			int near = nearHigh ? hi : lo;
			int far = nearHigh ? lo : hi;
			int dirIn = nearHigh ? -1 : 1;
			if (hi - lo >= 1) {
				// tsukubai: a stone basin with a bamboo spout
				set(near, 1, 1, b(Blocks.WATER_CAULDRON));
				set(near, 2, 1, b(Blocks.BAMBOO));
				set(near, 0, 1, b(Blocks.STONE_BRICKS));
				// ishidoro
				int lu = near + dirIn * 2;
				if (lu >= lo && lu <= hi) {
					JapaneseKit.stoneLantern(c, p, mu(lu, 1), 1, mv(lu, 1));
				}
			}
			if (hi - lo >= 3) {
				int fd2 = nearHigh ? 1 : -1;
				// a blossoming cherry shrub: low enough to leave no pocket under it
				set(far, 1, gb, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
				set(far, 2, gb, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
				set(far + fd2, 1, gb, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
				set(far + 2 * fd2, 1, gb, b(Blocks.FLOWERING_AZALEA));
				if (gb >= 2) {
					set(far, 1, gb - 1, b(Blocks.PINK_PETALS));
				}
			}
		}

		/** A raised koi pond 3 wide: a mossy rim, water and lily pads, a flat stone to sit on. */
		void pond(int a0, int gb) {
			for (int a = a0 - 1; a <= a0 + 3; a++) {
				for (int bb = 1; bb <= gb; bb++) {
					boolean rim = a == a0 - 1 || a == a0 + 3;
					if (rim) {
						set(a, 0, bb, p.hash(a, 0, bb, 3) == 0 ? b(Blocks.COBBLED_DEEPSLATE) : b(Blocks.MOSSY_COBBLESTONE));
					} else {
						set(a, 0, bb, b(Blocks.WATER));
						if (p.hash(a, 1, bb, 3) == 0) {
							set(a, 1, bb, b(Blocks.LILY_PAD));
						}
					}
				}
			}
		}
	}
}
