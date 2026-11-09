package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.door;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.hanging;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.log;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.mix;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.pane;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.stair;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.house.TerraceKit.Links;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * A villa on a podium. A dark deepslate basement 2 to 4 blocks high, crowned by a light quartz cornice and a
 * balustrade, carries a two-storey house of calcite and quartz pilasters under a hipped roof with an eave, a ridge,
 * finials and two chimneys. A broad stair (3, 5 or 7 wide) climbs the front to the podium terrace, where the benches,
 * the planters and the tables are; the door opens straight onto it.
 *
 * <pre>
 *  front section (door column)                       side view
 *            ____/\____  hipped roof, eave, ridge
 *       ____|  upper    |____  balcony over the portico (seed)
 *      |  portico columns / pilasters  |
 *   ...|  ground floor: salon + kitchen |...  terrace: benches, planters, tables
 *  ▁▂▃▅▆▇█████ podium (deepslate, light cornice)  ██████
 * </pre>
 *
 * Inside, every storey is 4 blocks free: ground floor with salon (sofa, shelves, rug), kitchen (smoker, furnace,
 * crafting table, cauldron) and storage (chests, barrels); upper floor with a bedroom (two beds, chests) and a study
 * (bookshelves, enchanting table, lectern). A straight timber stair along the back wall joins them, with a fence round
 * the hole. By seed: the podium is 2, 3 or 4 high (as far as the plot allows), the roof is slate, oxidised copper or
 * red brick, and the front is either a row of free-standing columns carrying a balcony (portico) or engaged pilasters.
 *
 * <p>Size: 14–26 × 14–26 × 17+. The villa is symmetric about the door column. Plots larger than 27 are built in the
 * middle. A podium with a cellar was left out: 3 blocks is all the 4-high podium could spare, below the 4 free the
 * storeys have, so the storage is the almacén of the ground floor.
 */
public final class HouseTerracePodium implements Variant {
	@Override
	public String id() {
		return "house_terrace_podium";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 14;
	}

	@Override
	public int minD() {
		return 14;
	}

	@Override
	public int minH() {
		return 17;
	}

	@Override
	public int maxW() {
		return 26;
	}

	@Override
	public int maxD() {
		return 26;
	}

	// ------------------------------------------------------------------ geometry

	/** The roof materials of a seed. */
	private record Roof(Block stairs, Block solid, Block solidAlt, Block slab, Block ridge, Block eave) {
	}

	private static Roof roofKind(int kind) {
		return switch (kind) {
			case 0 -> new Roof(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_SLAB, Blocks.POLISHED_DEEPSLATE, Blocks.CHERRY_STAIRS);
			case 1 -> new Roof(Blocks.CUT_COPPER_STAIRS.waxed().oxidized(), Blocks.CUT_COPPER.waxed().oxidized(), Blocks.CUT_COPPER.waxed().weathered(), Blocks.CUT_COPPER_SLAB.waxed().oxidized(),
					Blocks.CUT_COPPER.waxed().oxidized(), Blocks.CHERRY_STAIRS);
			default -> new Roof(Blocks.BRICK_STAIRS, Blocks.BRICKS, Blocks.BRICKS, Blocks.BRICK_SLAB, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILE_STAIRS);
		};
	}

	/**
	 * The layout in the sub-box: the podium covers {@code u 0..wc-1, v P..dc-1}; the stair climbs in front of it ({@code v 0..P-1}); the
	 * house stands on it at {@code ha..hb × hv0..hv1} with a terrace around (front {@code tf}, sides {@code ha-1}, back the rest).
	 */
	private static final class G {
		int wc;
		int dc;
		int ou;
		int ov;
		int h;
		int pod; // podium height; the terrace is walked at y = pod
		int cu;
		int sw;
		int sh;
		int ha;
		int hb;
		int hw;
		int hv0;
		int hv1;
		int hd;
		int tf;
		boolean portico;
		Roof roof;
	}

	private static G plan(Plot p, Random r) {
		G g = new G();
		int wc = Math.min(p.w(), 27);
		if ((wc & 1) == 0) {
			wc--;
		}
		int dc = Math.min(p.d(), 27);
		// p.hash mixes the seed well; java.util.Random with nearby seeds gives the same first draws
		int pod = Math.max(2, Math.min(2 + p.hash(11, 3, 5, 3), p.h() - 15));
		boolean portico = p.hash(7, 1, 13, 2) == 0;
		int roofKind = p.hash(3, 9, 17, 3);
		int wi = wc - 2;
		int bt0 = dc >= 18 ? 2 : dc >= 15 ? 1 : 0;
		int tf;
		while (true) {
			tf = portico ? 3 : 2;
			if (dc - pod - 2 - tf - bt0 >= 7) {
				break;
			}
			if (pod > 2) {
				pod--;
			} else if (portico) {
				portico = false;
			} else {
				break;
			}
		}
		int sidet = wi >= 21 ? 3 : wi >= 15 ? 2 : 1;
		g.wc = wc;
		g.dc = dc;
		g.ou = (p.w() - wc) / 2;
		g.ov = (p.d() - dc) / 2;
		g.h = p.h();
		g.pod = pod;
		g.cu = (wc - 1) / 2;
		g.sw = wi >= 21 ? 7 : wi >= 13 ? 5 : 3;
		g.sh = g.sw / 2;
		g.ha = 1 + sidet;
		g.hw = wi - 2 * sidet;
		g.hb = g.ha + g.hw - 1;
		g.tf = tf;
		g.hd = Math.min(dc - pod - 2 - tf - bt0, 17);
		g.hv0 = pod + 1 + tf;
		g.hv1 = g.hv0 + g.hd - 1;
		g.portico = portico;
		g.roof = roofKind(roofKind);
		return g;
	}

	/** Writes in the sub-box, centred in the plot. */
	private static final class K {
		final CitadelCanvas c;
		final Plot p;
		final int ou;
		final int ov;

		K(CitadelCanvas c, Plot p, int ou, int ov) {
			this.c = c;
			this.p = p;
			this.ou = ou;
			this.ov = ov;
		}

		void set(int u, int y, int v, BlockState s) {
			p.set(c, ou + u, y, ov + v, s);
		}

		void put(int u, int y, int v, BlockState s, int layer) {
			p.put(c, ou + u, y, ov + v, s, layer);
		}

		boolean free(int u, int y, int v) {
			BlockState s = p.at(c, ou + u, y, ov + v);
			return s == null || s.isAir();
		}

		int hash(int u, int y, int v, int n) {
			return p.hash(u, y, v, n);
		}

		/** Link sets work in plot coordinates, so the sub-box offset is added. */
		void member(Links l, int u, int v) {
			l.add(ou + u, ov + v);
		}

		void partner(Links l, int u, int v) {
			l.partner(ou + u, ov + v);
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x90D1_0A11L);
		G g = plan(p, r);
		K k = new K(c, p, g.ou, g.ov);
		podium(k, g);
		frontStair(k, g);
		parapet(k, g);
		house(k, g);
		portico(k, g);
		roof(k, g);
		interior(k, g);
		terrace(k, g);
		forecourt(k, g);
	}

	// ------------------------------------------------------------------ materials

	private static BlockState dark(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.POLISHED_DEEPSLATE);
	}

	private static BlockState cornice(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BRICKS);
	}

	private static BlockState pave(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.POLISHED_TUFF, Blocks.POLISHED_TUFF, Blocks.TUFF_BRICKS, Blocks.POLISHED_ANDESITE);
	}

	private static BlockState white(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.CALCITE, Blocks.CALCITE, Blocks.CALCITE, Blocks.SMOOTH_QUARTZ);
	}

	private static BlockState plinth(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.TUFF_BRICKS, Blocks.TUFF_BRICKS, Blocks.POLISHED_TUFF);
	}

	private static BlockState pillar() {
		return b(Blocks.QUARTZ_PILLAR);
	}

	private static BlockState face(Block blk, Direction f) {
		return blk.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f);
	}

	private static BlockState glass(Plot p, int u, int y, int v, boolean alongU) {
		boolean pink = p.hash(u, y, v, 7) == 0;
		return pane(pink ? Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK) : Blocks.GLASS_PANE, alongU);
	}

	/** A stair with an outer corner at a ring corner (the high quarter on the inside diagonal). */
	private static BlockState ringStair(Block blk, boolean n, boolean s, boolean w, boolean e, boolean top) {
		Direction f;
		StairsShape sh = StairsShape.STRAIGHT;
		if (n && w) {
			f = Direction.SOUTH;
			sh = StairsShape.OUTER_LEFT;
		} else if (n && e) {
			f = Direction.SOUTH;
			sh = StairsShape.OUTER_RIGHT;
		} else if (s && w) {
			f = Direction.NORTH;
			sh = StairsShape.OUTER_RIGHT;
		} else if (s && e) {
			f = Direction.NORTH;
			sh = StairsShape.OUTER_LEFT;
		} else if (n) {
			f = Direction.SOUTH;
		} else if (s) {
			f = Direction.NORTH;
		} else if (w) {
			f = Direction.EAST;
		} else {
			f = Direction.WEST;
		}
		return stair(blk, f, top).setValue(BlockStateProperties.STAIRS_SHAPE, sh);
	}

	// ------------------------------------------------------------------ podium and stair

	/**
	 * [palette] dark to light: a polished-deepslate base course, deepslate bricks, a quartz cornice. [depth] chiselled piers
	 * every fourth cell and lantern niches on the front face.
	 */
	private static void podium(K k, G g) {
		Plot p = k.p;
		int pod = g.pod;
		for (int u = 0; u < g.wc; u++) {
			for (int v = pod; v < g.dc; v++) {
				boolean ring = u == 0 || u == g.wc - 1 || v == pod || v == g.dc - 1;
				boolean frontBack = v == pod || v == g.dc - 1;
				int edge = frontBack ? Math.abs(u - g.cu) : v - pod;
				boolean pier = ring && edge % 4 == 2;
				for (int y = 0; y < pod; y++) {
					BlockState st;
					if (y == pod - 1) {
						st = ring ? cornice(p, u, y, v) : pave(p, u, y, v);
					} else if (y == 0) {
						st = ring ? mix(p, u, y, v, Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS) : dark(p, u, y, v);
					} else if (ring && v == pod && y == 1 && Math.abs(u - g.cu) % 4 == 0 && Math.abs(u - g.cu) > g.sh + 1) {
						st = b(Blocks.LANTERN);
					} else if (pier) {
						st = b(Blocks.CHISELED_DEEPSLATE);
					} else {
						st = dark(p, u, y, v);
					}
					k.set(u, y, v, st);
				}
			}
		}
	}

	/**
	 * The front stair, {@code sw} wide: a quartz step per block of height, first step met head-on from the ground, a cheek of
	 * deepslate on each side stepping up with the flight, and a lantern post where each cheek meets the balustrade. The row of the
	 * podium front is the landing at the top.
	 */
	private static void frontStair(K k, G g) {
		Plot p = k.p;
		for (int kk = 0; kk < g.pod; kk++) {
			for (int du = -g.sh; du <= g.sh; du++) {
				int u = g.cu + du;
				for (int y = 0; y < kk; y++) {
					k.set(u, y, kk, b(Blocks.POLISHED_TUFF));
				}
				k.set(u, kk, kk, stair(Blocks.SMOOTH_QUARTZ_STAIRS, Direction.SOUTH, false));
			}
			for (int s : new int[] {-1, 1}) {
				int u = g.cu + s * (g.sh + 1);
				for (int y = 0; y <= kk; y++) {
					k.set(u, y, kk, y == 0 ? b(Blocks.POLISHED_DEEPSLATE) : dark(p, u, y, kk));
				}
				k.set(u, kk + 1, kk, slab(Blocks.POLISHED_DEEPSLATE_SLAB));
			}
		}
	}

	/** [depth] a balustrade of deepslate-brick wall round the terrace, linked by hand, with polished posts and lanterns. */
	private static void parapet(K k, G g) {
		Links run = new Links();
		java.util.List<int[]> posts = new java.util.ArrayList<>();
		int open = g.sh + 1;
		for (int u = 0; u < g.wc; u++) {
			for (int v = g.pod; v < g.dc; v++) {
				boolean ring = u == 0 || u == g.wc - 1 || v == g.pod || v == g.dc - 1;
				if (!ring) {
					continue;
				}
				int du = Math.abs(u - g.cu);
				if (v == g.pod && du <= g.sh) {
					continue; // the stair arrives here
				}
				boolean corner = (u == 0 || u == g.wc - 1) && (v == g.pod || v == g.dc - 1);
				boolean end = v == g.pod && du == open;
				if (corner || end) {
					posts.add(new int[] {u, v});
					k.partner(run, u, v);
				} else {
					k.member(run, u, v);
				}
			}
		}
		run.emitWalls(k.c, k.p, g.pod, Blocks.DEEPSLATE_BRICK_WALL);
		for (int[] q : posts) {
			k.set(q[0], g.pod, q[1], b(Blocks.POLISHED_DEEPSLATE));
			k.set(q[0], g.pod + 1, q[1], b(Blocks.LANTERN));
		}
	}

	// ------------------------------------------------------------------ the house

	/** What a ring cell of the house is: 0 pilaster/corner, 1 front door, 2 bay (window). */
	private static boolean pilAt(int kk, int len) {
		int dk = Math.abs(kk - (len - 1) / 2);
		boolean corner = kk == 0 || kk == len - 1;
		return corner || (dk % 4 == 2 && (len < 9 || (kk != 1 && kk != len - 2)));
	}

	private static int cellKind(G g, int u, int v) {
		boolean horiz = v == g.hv0 || v == g.hv1;
		int kk = horiz ? u - g.ha : v - g.hv0;
		int len = horiz ? g.hw : g.hd;
		if (pilAt(kk, len)) {
			return 0;
		}
		if (v == g.hv0 && kk == (len - 1) / 2) {
			return 1;
		}
		return 2;
	}

	/**
	 * Walls of both storeys on the ring, the string courses at y = pod + 4 and pod + 9, the floors and the ceiling. Each storey is
	 * 4 free blocks: a dado, two rows of glass, a cherry lintel.
	 */
	private static void house(K k, G g) {
		Plot p = k.p;
		int pod = g.pod;
		for (int u = g.ha; u <= g.hb; u++) {
			for (int v = g.hv0; v <= g.hv1; v++) {
				boolean ring = u == g.ha || u == g.hb || v == g.hv0 || v == g.hv1;
				boolean horiz = v == g.hv0 || v == g.hv1;
				Direction.Axis beam = horiz ? Direction.Axis.X : Direction.Axis.Z;
				if (!ring) {
					k.set(u, pod - 1, v, mix(p, u, pod - 1, v, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS));
					k.set(u, pod + 4, v, mix(p, u, pod + 4, v, Blocks.CHERRY_PLANKS, Blocks.CHERRY_PLANKS, Blocks.SPRUCE_PLANKS));
					k.set(u, pod + 9, v, mix(p, u, pod + 9, v, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS));
					continue;
				}
				int kind = cellKind(g, u, v);
				for (int s = 0; s < 2; s++) {
					int y0 = pod + 5 * s;
					for (int t = 0; t <= 3; t++) {
						BlockState st;
						if (kind == 0) {
							st = t == 0 ? b(Blocks.SMOOTH_QUARTZ) : t == 3 ? b(Blocks.CHISELED_QUARTZ_BLOCK) : pillar();
						} else if (kind == 1 && (s == 0 || g.portico)) {
							st = switch (t) {
								case 0 -> door(Blocks.CHERRY_DOOR, Direction.NORTH, true);
								case 1 -> door(Blocks.CHERRY_DOOR, Direction.NORTH, false);
								case 2 -> CitadelCanvas.AIR; // an open transom: the doorway is 3 free blocks high
								default -> log(Blocks.STRIPPED_CHERRY_LOG, beam);
							};
						} else if (t == 3) {
							st = log(Blocks.STRIPPED_CHERRY_LOG, beam);
						} else if (t == 0) {
							st = s == 0 ? plinth(p, u, y0, v) : white(p, u, y0 + t, v);
						} else {
							st = glass(p, u, y0 + t, v, horiz);
						}
						k.set(u, y0 + t, v, st);
					}
				}
				k.set(u, pod + 4, v, log(Blocks.STRIPPED_CHERRY_LOG, beam));
				k.set(u, pod + 9, v, log(Blocks.STRIPPED_CHERRY_LOG, beam));
			}
		}
		// [depth] engaged pilasters standing out of the front wall, from the terrace to under the eave (no portico)
		if (!g.portico) {
			for (int u = g.ha; u <= g.hb; u++) {
				if (!pilAt(u - g.ha, g.hw)) {
					continue;
				}
				int v = g.hv0 - 1;
				k.set(u, pod, v, b(Blocks.SMOOTH_QUARTZ));
				for (int y = pod + 1; y <= pod + 7; y++) {
					k.set(u, y, v, pillar());
				}
				k.set(u, pod + 8, v, b(Blocks.CHISELED_QUARTZ_BLOCK));
			}
		}
	}

	/**
	 * The portico (seed): free-standing columns along the front carry the floor of a balcony, with a cherry front beam and a
	 * fence; the upper floor opens onto it through the door above the entrance.
	 */
	private static void portico(K k, G g) {
		if (!g.portico) {
			return;
		}
		Plot p = k.p;
		int pod = g.pod;
		int vf = g.hv0 - 2;
		for (int u = g.ha; u <= g.hb; u++) {
			for (int v = vf; v <= g.hv0 - 1; v++) {
				boolean edgeF = v == vf;
				boolean edgeS = u == g.ha || u == g.hb;
				BlockState st = edgeF ? log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X) : edgeS ? log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Z)
						: mix(p, u, pod + 4, v, Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BRICKS);
				k.set(u, pod + 4, v, st);
			}
			if (pilAt(u - g.ha, g.hw)) {
				k.set(u, pod, vf, b(Blocks.SMOOTH_QUARTZ));
				k.set(u, pod + 1, vf, pillar());
				k.set(u, pod + 2, vf, pillar());
				k.set(u, pod + 3, vf, b(Blocks.CHISELED_QUARTZ_BLOCK));
			}
		}
		Links fence = new Links();
		for (int u = g.ha; u <= g.hb; u++) {
			k.member(fence, u, vf);
		}
		for (int v = vf + 1; v <= g.hv0 - 1; v++) {
			k.member(fence, g.ha, v);
			k.member(fence, g.hb, v);
		}
		k.partner(fence, g.ha, g.hv0);
		k.partner(fence, g.hb, g.hv0);
		fence.emitFences(k.c, k.p, pod + 5, Blocks.CHERRY_FENCE);
		for (int du : new int[] {-4, 0, 4}) {
			k.put(g.cu + du, pod + 3, g.hv0 - 1, hanging(Blocks.LANTERN), CitadelCanvas.HANGING);
		}
	}

	// ------------------------------------------------------------------ roof

	/**
	 * [finish] a hipped roof on the house, with a cherry (or deepslate) eave one block out, a quartz cove under it, a ridge capped
	 * with slabs, a lightning-rod finial at each end of the ridge and two chimneys through the slopes. The mass under the
	 * stairs is solid: no attic, nothing floating.
	 */
	private static void roof(K k, G g) {
		Plot p = k.p;
		Roof rf = g.roof;
		int pp = g.pod + 9;
		int a = g.ha - 1;
		int bb = g.hb + 1;
		int v0 = g.hv0 - 1;
		int e = g.hv1 + 1;
		int minDim = Math.min(bb - a + 1, e - v0 + 1);
		int room = g.h - 1 - pp;
		int tMax = Math.min(Math.min(7, (minDim - 1) / 2), room - 1);
		if (tMax < 1) {
			return;
		}
		for (int u = a; u <= bb; u++) {
			for (int v = v0; v <= e; v++) {
				int rr = Math.min(Math.min(u - a, bb - u), Math.min(v - v0, e - v));
				if (rr < tMax) {
					boolean n = v == v0 + rr;
					boolean s = v == e - rr;
					boolean w = u == a + rr;
					boolean ee = u == bb - rr;
					k.set(u, pp + rr, v, ringStair(rr == 0 ? rf.eave() : rf.stairs(), n, s, w, ee, false));
					for (int y = pp + 1; y < pp + rr; y++) {
						k.set(u, y, v, mix(p, u, y, v, rf.solid(), rf.solid(), rf.solidAlt()));
					}
				} else {
					for (int y = pp + 1; y < pp + tMax; y++) {
						k.set(u, y, v, mix(p, u, y, v, rf.solid(), rf.solid(), rf.solidAlt()));
					}
					k.set(u, pp + tMax, v, b(rf.ridge()));
					if (pp + tMax + 1 <= g.h - 1) {
						k.set(u, pp + tMax + 1, v, slab(rf.slab()));
					}
				}
			}
		}
		// finials at both ends of the ridge
		int fu = bb - a + 1 - 2 * tMax;
		int fv = e - v0 + 1 - 2 * tMax;
		if (pp + tMax + 1 <= g.h - 1 && Math.max(fu, fv) >= 2) {
			int mu = (a + bb) / 2;
			int mv = (v0 + e) / 2;
			BlockState rod = b(Blocks.LIGHTNING_ROD.waxed().unaffected());
			if (fu >= fv) {
				k.set(a + tMax, pp + tMax + 1, mv, rod);
				k.set(bb - tMax, pp + tMax + 1, mv, rod);
			} else {
				k.set(mu, pp + tMax + 1, v0 + tMax, rod);
				k.set(mu, pp + tMax + 1, e - tMax, rod);
			}
		}
		// two chimneys through the slopes, either side
		if (tMax >= 3) {
			int cv = (v0 + e) / 2;
			int top = Math.min(pp + tMax + 2, g.h - 2);
			for (int cu : new int[] {a + 2, bb - 2}) {
				for (int y = pp + 2; y <= top; y++) {
					k.set(cu, y, cv, k.hash(cu, y, cv, 3) == 0 ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.BRICKS));
				}
				k.set(cu, top + 1, cv, slab(Blocks.DEEPSLATE_BRICK_SLAB));
			}
		}
		// [depth] the cove: inverted stairs one block out under the eave, wherever nothing stands
		int y = pp - 1;
		for (int u = a; u <= bb; u++) {
			for (int v = v0; v <= e; v++) {
				boolean ring = u == a || u == bb || v == v0 || v == e;
				if (ring && k.free(u, y, v)) {
					k.set(u, y, v, ringStair(Blocks.SMOOTH_QUARTZ_STAIRS, v == v0, v == e, u == a, u == bb, true));
				}
			}
		}
	}

	// ------------------------------------------------------------------ interior

	private static void place(K k, int u, int y, int v, BlockState st) {
		if (k.free(u, y, v)) {
			k.set(u, y, v, st);
		}
	}

	private static void bed(K k, int u, int y, int v, DyeColor col) {
		BlockState base = Blocks.BED.pick(col).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
		place(k, u, y, v, base.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		place(k, u, y, v + 1, base.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
	}

	private static void shelf(K k, int u, int y, int v) {
		for (int dy = 0; dy < 3; dy++) {
			place(k, u, y + dy, v, b(Blocks.BOOKSHELF));
		}
	}

	/**
	 * The stair, then the two storeys. The flight runs along the back wall from the left corner: an approach cell, five steps, a
	 * landing on the upper floor, the floor cut over the first four, a fence on the room side. The centre of every room is free
	 * (3 wide from the door) and the furniture hugs the walls.
	 */
	private static void interior(K k, G g) {
		int pod = g.pod;
		int iu0 = g.ha + 1;
		int iu1 = g.hb - 1;
		int iv0 = g.hv0 + 1;
		int iv1 = g.hv1 - 1;
		int cu = g.cu;
		int id = iv1 - iv0 + 1;
		int last = iv1 - 1;
		// ---- the stair
		for (int j = 0; j < 5; j++) {
			int u = iu0 + 1 + j;
			for (int y = pod; y < pod + j; y++) {
				k.set(u, y, iv1, b(Blocks.SPRUCE_PLANKS));
			}
			k.set(u, pod + j, iv1, stair(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
			if (j < 4) {
				k.set(u, pod + 4, iv1, CitadelCanvas.AIR);
			}
		}
		// the post that carries the landing: it also fills the corner under it, which the kitchen counter would seal off
		for (int y = pod; y <= pod + 3; y++) {
			k.set(iu0 + 6, y, iv1, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y));
		}
		Links rail = new Links();
		for (int u = iu0; u <= iu0 + 4; u++) {
			k.member(rail, u, iv1 - 1);
		}
		k.member(rail, iu0, iv1);
		k.partner(rail, g.ha, iv1);
		k.partner(rail, g.ha, iv1 - 1);
		k.partner(rail, iu0, g.hv1);
		rail.emitFences(k.c, k.p, pod + 5, Blocks.SPRUCE_FENCE);
		// ---- ground floor: salon (front left), kitchen (right), storage (left back)
		shelf(k, iu0, pod, iv0);
		for (int v = iv0 + 1; v <= iv0 + 2; v++) {
			place(k, iu0, pod, v, stair(Blocks.CHERRY_STAIRS, Direction.WEST, false)); // the sofa, back to the wall
		}
		place(k, iu0 + 1, pod, iv0 + 1, slab(Blocks.CHERRY_SLAB)); // low table
		for (int v = iv0 + 3; v <= last; v++) {
			place(k, iu0, pod, v, (v - iv0) % 2 == 1 ? face(Blocks.CHEST, Direction.EAST) : b(Blocks.BARREL));
			place(k, iu0 + 1, pod, v, (v - iv0) % 2 == 1 ? b(Blocks.BARREL) : face(Blocks.CHEST, Direction.EAST)); // one block high: nothing to climb
		}
		place(k, iu1, pod, iv0, b(Blocks.POTTED_FLOWERING_AZALEA));
		BlockState[] kit = {
				face(Blocks.SMOKER, Direction.WEST),
				b(Blocks.CRAFTING_TABLE),
				face(Blocks.FURNACE, Direction.WEST),
				b(Blocks.CAULDRON),
				b(Blocks.BARREL),
				face(Blocks.CHEST, Direction.WEST),
		};
		for (int v = iv0 + 1; v <= last; v++) {
			place(k, iu1, pod, v, kit[(v - iv0 - 1) % kit.length]);
		}
		int m = iv0 + 2;
		place(k, iu1 - 1, pod, m, b(Blocks.CHERRY_FENCE)); // dining table and two stools
		place(k, iu1 - 1, pod + 1, m, b(Blocks.CHERRY_PRESSURE_PLATE));
		place(k, iu1 - 1, pod, m - 1, stair(Blocks.CHERRY_STAIRS, Direction.NORTH, false));
		place(k, iu1 - 1, pod, m + 1, stair(Blocks.CHERRY_STAIRS, Direction.SOUTH, false));
		for (int u = cu - 1; u <= cu + 1; u++) {
			for (int v = iv0 + 1; v <= Math.min(iv0 + 3, last); v++) {
				place(k, u, pod, v, Blocks.CARPET.pick(DyeColor.PINK).defaultBlockState());
			}
		}
		for (int u = iu0 + 7; u <= iu1; u++) {
			place(k, u, pod, iv1, (u - iu0) % 2 == 1 ? face(Blocks.CHEST, Direction.NORTH) : b(Blocks.BARREL));
		}
		// ---- upper floor: bedroom (left), study (right)
		int yu = pod + 5;
		bed(k, iu0, yu, iv0, DyeColor.PINK);
		bed(k, iu0 + 1, yu, iv0, DyeColor.WHITE);
		place(k, iu0, yu, iv0 + 2, face(Blocks.CHEST, Direction.NORTH));
		place(k, iu0 + 1, yu, iv0 + 2, b(Blocks.BARREL));
		for (int v = iv0 + 3; v <= iv1 - 2; v++) {
			place(k, iu0, yu, v, v % 2 == 0 ? face(Blocks.CHEST, Direction.EAST) : b(Blocks.POTTED_FERN));
		}
		shelf(k, iu1, yu, iv0);
		shelf(k, iu1, yu, iv0 + 1);
		place(k, iu1, yu, iv0 + 2, b(Blocks.ENCHANTING_TABLE));
		place(k, iu1 - 1, yu, iv0 + 2, stair(Blocks.CHERRY_STAIRS, Direction.WEST, false));
		if (iv0 + 3 <= iv1 - 2) {
			place(k, iu1, yu, iv0 + 3, face(Blocks.LECTERN, Direction.WEST).setValue(BlockStateProperties.HAS_BOOK, true));
		}
		if (iv0 + 4 <= iv1 - 2) {
			place(k, iu1, yu, iv0 + 4, b(Blocks.CARTOGRAPHY_TABLE));
		}
		for (int u = iu0 + 8; u <= iu1; u++) {
			shelf(k, u, yu, iv1);
		}
		for (int u = cu - 1; u <= cu + 1; u++) {
			for (int v = iv0 + 1; v <= Math.min(iv0 + 3, last); v++) {
				place(k, u, yu, v, Blocks.CARPET.pick(DyeColor.WHITE).defaultBlockState());
			}
		}
		// ---- lanterns hung from the ceilings over the middle of the room: the floor there is flat (carpet), so nothing
		// stands under them and the headroom stays above 3
		for (int storey = 0; storey < 2; storey++) {
			int y = pod + 3 + 5 * storey;
			k.put(cu, y, iv0 + 2, hanging(Blocks.LANTERN), CitadelCanvas.HANGING);
			k.put(cu, y, last, hanging(Blocks.LANTERN), CitadelCanvas.HANGING);
			if (id >= 8) {
				k.put(cu, y, (iv0 + last) / 2 + 1, hanging(Blocks.LANTERN), CitadelCanvas.HANGING);
			}
		}
	}

	// ------------------------------------------------------------------ terrace and forecourt

	private static void planter(K k, int u, int y, int v) {
		Plot p = k.p;
		k.set(u, y, v, mix(p, u, y, v, Blocks.MOSS_BLOCK, Blocks.MOSS_BLOCK, Blocks.GRASS_BLOCK));
		BlockState plant = switch (k.hash(u, y, v, 9)) {
			case 0, 1 -> b(Blocks.AZALEA);
			case 2, 3 -> b(Blocks.FLOWERING_AZALEA);
			case 4 -> b(Blocks.PINK_PETALS);
			case 5 -> b(Blocks.ALLIUM);
			case 6 -> b(Blocks.CORNFLOWER);
			case 7 -> b(Blocks.FERN);
			default -> b(Blocks.PINK_TULIP);
		};
		k.set(u, y + 1, v, plant);
	}

	/**
	 * The podium terrace: in front, a table with two stools on each side of the way to the door, planters beyond them and
	 * pots under the pilasters; on the sides and the back a lane next to the walls stays free, and the outer cell has benches and
	 * planters where the terrace is wide enough.
	 */
	private static void terrace(K k, G g) {
		int pod = g.pod;
		int cu = g.cu;
		int vf = pod + 1;
		for (int u = 1; u <= g.wc - 2; u++) {
			int du = Math.abs(u - cu);
			boolean left = u < cu;
			if (du == 4) {
				k.set(u, pod, vf, b(Blocks.CHERRY_FENCE));
				k.set(u, pod + 1, vf, slab(Blocks.CHERRY_SLAB));
			} else if (du == 3) {
				k.set(u, pod, vf, stair(Blocks.CHERRY_STAIRS, left ? Direction.EAST : Direction.WEST, false));
			} else if (du == 5) {
				k.set(u, pod, vf, stair(Blocks.CHERRY_STAIRS, left ? Direction.WEST : Direction.EAST, false));
			} else if (du >= 6) {
				planter(k, u, pod, vf);
			}
			if (du == 3 && !g.portico) { // under the portico a pot would be a step under a low ceiling
				k.set(u, pod, g.hv0 - 1, b(k.hash(u, pod, g.hv0, 2) == 0 ? Blocks.POTTED_FLOWERING_AZALEA : Blocks.POTTED_FERN));
			}
		}
		int sidet = g.ha - 1;
		if (sidet >= 2) {
			for (int v = pod + 1; v <= g.dc - 2; v++) {
				boolean bench = (v - pod - 1) % 6 < 3;
				if (bench) {
					k.set(1, pod, v, stair(Blocks.CHERRY_STAIRS, Direction.WEST, false));
					k.set(g.wc - 2, pod, v, stair(Blocks.CHERRY_STAIRS, Direction.EAST, false));
				} else {
					planter(k, 1, pod, v);
					planter(k, g.wc - 2, pod, v);
				}
			}
		}
		int nb = g.dc - 2 - g.hv1;
		if (nb >= 2) {
			int v = g.dc - 2;
			int from = sidet >= 2 ? 2 : 1;
			int to = sidet >= 2 ? g.wc - 3 : g.wc - 2;
			for (int u = from; u <= to; u++) {
				if (Math.abs(u - cu) % 6 <= 2) {
					k.set(u, pod, v, stair(Blocks.CHERRY_STAIRS, Direction.SOUTH, false));
				} else {
					planter(k, u, pod, v);
				}
			}
		}
	}

	/** [finish] hedges and pots against the podium face beside the stair, so it rises out of a garden. */
	private static void forecourt(K k, G g) {
		int v = g.pod - 1;
		for (int u = 0; u < g.wc; u++) {
			int du = Math.abs(u - g.cu);
			if (du <= g.sh + 1) {
				continue;
			}
			k.set(u, 0, v, leaves(Blocks.AZALEA_LEAVES));
			if (g.pod >= 3) {
				k.set(u, 1, v, leaves(k.hash(u, 1, v, 3) == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
			}
			if (g.pod >= 3 && v - 1 >= 0 && du % 3 == 0) {
				k.set(u, 0, v - 1, b(k.hash(u, 0, v - 1, 2) == 0 ? Blocks.POTTED_AZALEA : Blocks.POTTED_FERN));
			}
		}
	}
}
