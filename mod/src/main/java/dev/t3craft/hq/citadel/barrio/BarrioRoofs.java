package dev.t3craft.hq.citadel.barrio;

import dev.t3craft.hq.RingCampusGeometry.Lot;
import dev.t3craft.hq.RingCampusGeometry.Room;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelCanvas.Mats;
import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import static dev.t3craft.hq.citadel.CitadelCanvas.GLASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.fh;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;

/**
 * The roofs of the halls (and the eave ring the gatehouse shares): gable, hipped and flat green roof.
 * Every size derives from the hall's {@link Room} ({@code w}, {@code d}, {@code c()}) and from the
 * ceiling height {@code top}, so the roofs follow the geometry when the citadel is scaled.
 *
 * <p>Deterministic variation: each roof seeds its choices (dormers, chimney, pergola side) from its own
 * position ({@link BarrioKit#hash}), never from other halls, so a hall's blocks do not change with the
 * number of projects.
 *
 * <ul>
 * <li><b>gable</b>: slopes with an eave course in a second tone, every third course in a second tone, a ridge capped
 *     with slabs and a finial at each end, a round window in each gable end, up to two dormers and a
 *     chimney.</li>
 * <li><b>hipped</b>: the same family on four slopes, the hips closed by outer-corner stairs, an apex
 *     with a lightning rod, eaves all round and a chimney.</li>
 * <li><b>flat</b>: a parapet with merlons, corner posts with lanterns, a green roof with a small cherry
 *     tree and a pergola with a hanging lantern.</li>
 * </ul>
 */
public final class BarrioRoofs {
	private BarrioRoofs() {
	}

	/** Dormers sit on the slope cell this far (in cells) from the eave line. */
	private static final int DORMER_K = 2;
	/** Dormers are this many cells wide (cheek, window, cheek). */
	private static final int DORMER_HALF = 1;
	/** Height of a chimney above the roof's ridge. */
	private static final int CHIMNEY_ABOVE_RIDGE = 2;

	// ------------------------------------------------------------------ gable

	public static void gable(CitadelCanvas c, Room r, int top, Mats mats) {
		int W = r.w();
		int D = r.d();
		int mid = r.c();
		Block roof = mats.roof();
		Lot lot = BarrioKit.lotOf(c, r);
		for (int u = -1; u <= D; u++) {
			for (int v = -1; v <= W; v++) {
				BlockPos p = r.cell(u, v, 0);
				int k = Math.min(v + 1, W - v);
				int y = top + Math.max(1, k);
				if (v == mid) {
					c.set(p.atY(y), mats.ridge());
					if (u == -1 || u == D) {
						c.set(p.atY(y + 1), BarrioKit.finial(roof));
					} else {
						c.set(p.atY(y + 1), BarrioKit.slabOf(roof));
					}
				} else {
					Direction f = v < mid ? r.side() : r.side().getOpposite();
					c.set(p.atY(y), BarrioKit.stair(BarrioKit.tone(roof, k, p.getX(), y, p.getZ()), f));
				}
				if (u == -1 || u == D) {
					for (int yy = top + 1; yy < y; yy++) {
						if (grand(r)) {
							// struct/barrio-hall2: the gable end is glazed between posts on the walls' pier rhythm, not a smooth triangle
							boolean pier = Math.floorMod(v, 3) == 0;
							boolean glass = !pier && v >= 1 && v <= W - 2 && yy >= top + 2 && yy <= y - 3;
							c.set(p.atY(yy), pier ? mats.post() : glass ? GLASS : mats.wall());
							continue;
						}
						boolean round = v == mid && (yy == top + 2 || yy == top + 3);
						c.set(p.atY(yy), round ? GLASS : mats.wall());
					}
				}
			}
		}
		// eaves on the long sides, a second tone, facing the walls
		for (int u = -1; u <= D; u++) {
			for (int v : new int[] {-2, W + 1}) {
				BlockPos p = r.cell(u, v, top);
				if (lot != null && BarrioKit.reserved(lot, r, p)) {
					continue;
				}
				Direction f = v < 0 ? r.side() : r.side().getOpposite();
				c.set(p, BarrioKit.stair(BarrioKit.altStairs(roof), f));
			}
		}
		int seed = BarrioKit.hash(r.origin().getX(), r.origin().getZ(), top);
		if (grand(r)) {
			// struct/barrio-hall2: a break in the long slopes instead of two small dormers: a cross gable on each side, staggered
			IntBinaryOperator mainY = (u, v) -> top + Math.max(1, Math.min(v + 1, W - v));
			crossGable(c, r, top, mats, 2, D / 4, 3, 6, mainY);
			crossGable(c, r, top, mats, 3, D - 1 - D / 4, 3, 6, mainY);
		} else {
			dormers(c, r, top, mats, seed);
		}
		if (hasChimney(seed)) {
			IntBinaryOperator kAt = (u, v) -> Math.min(v + 1, W - v);
			chimney(c, r, top, mats, chimneySide(seed), D / 2 - 1, kAt);
		}
	}

	/** Halls big enough (scale 1.5 and up) for cross gables and a cupola; the scale-1 roofs stay as they were. */
	private static boolean grand(Room r) {
		return r.w() >= 15 && r.d() >= 16 && fh() >= 8;
	}

	/**
	 * A cross gable (a "break" of the slope): a gable roof of its own, {@code 2*half+1} cells wide, that comes out of a
	 * slope and meets it. {@code side} is the wall it faces (0 door wall, 1 back wall, 2 the {@code v = -1} wall, 3 the
	 * {@code v = W} wall), {@code tc} the cell along that wall where its ridge runs (the ridge is perpendicular to the
	 * wall), {@code mainY} the height of the main roof's surface at a cell. Its face stands flush with the wall plane as
	 * a gable end with a window, its two slopes are stairs towards the ridge (a slab ridge with a finial at the front),
	 * and the cheeks that stand clear of the main slope are closed in the ridge material. It ends where it dives into the
	 * main slope. Hollow underneath, like the roof it grows from.
	 */
	private static void crossGable(CitadelCanvas c, Room r, int top, Mats mats, int side, int tc, int half, int rise, IntBinaryOperator mainY) {
		int W = r.w();
		int D = r.d();
		Block roof = mats.roof();
		int yr = top + rise;
		boolean alongV = side < 2;
		Direction plus = alongV ? r.side() : r.back();
		for (int n = 0; n < Math.max(W, D); n++) {
			boolean any = false;
			for (int dt = -half; dt <= half; dt++) {
				int t = tc + dt;
				int u;
				int v;
				if (side == 0) {
					u = -1 + n;
					v = t;
				} else if (side == 1) {
					u = D - n;
					v = t;
				} else if (side == 2) {
					u = t;
					v = -1 + n;
				} else {
					u = t;
					v = W - n;
				}
				int yc = yr - Math.abs(dt);
				int ym = mainY.applyAsInt(u, v);
				if (yc <= ym) {
					continue;
				}
				any = true;
				BlockPos p = r.cell(u, v, 0);
				if (dt == 0) {
					// a low ridge of slabs (a full block under a slab read as a wall); a finial on the face
					c.set(p.atY(yc), BarrioKit.slabOf(roof));
					if (n == 0) {
						c.set(p.atY(yc + 1), BarrioKit.finial(roof));
					}
				} else {
					Direction f = dt < 0 ? plus : plus.getOpposite();
					c.set(p.atY(yc), BarrioKit.stair(BarrioKit.tone(roof, Math.abs(dt), p.getX(), yc, p.getZ()), f));
				}
				if (Math.abs(dt) == half) {
					for (int y = ym + 1; y < yc; y++) {
						c.set(p.atY(y), mats.ridge());
					}
				}
				if (n == 0) {
					for (int y = top + 1; y < yc; y++) {
						boolean window = Math.abs(dt) < half && y >= top + 2 && y <= yc - 2;
						c.set(p.atY(y), Math.abs(dt) == half ? mats.post() : window ? GLASS : mats.wall());
					}
				}
			}
			if (!any) {
				break;
			}
		}
	}

	/**
	 * The cupola of a grand hipped roof, the focal point of the roof: five by five on the flat deck whose blocks are at
	 * {@code yDeck}, centred on {@code (uc, vc)}: corner posts, glass on the four faces
	 * three high, a hanging lantern inside, a two-step hip roof of the hall's own stairs (slabs on the corners) and a rod on
	 * top.
	 */
	private static void cupola(CitadelCanvas c, Room r, int yDeck, Mats mats, int uc, int vc) {
		Block roof = mats.roof();
		int y0 = yDeck;
		for (int du = -2; du <= 2; du++) {
			for (int dv = -2; dv <= 2; dv++) {
				BlockPos p = r.cell(uc + du, vc + dv, 0);
				boolean edge = Math.abs(du) == 2 || Math.abs(dv) == 2;
				boolean corner = Math.abs(du) == 2 && Math.abs(dv) == 2;
				if (edge) {
					BlockState wall = corner ? mats.post() : GLASS;
					for (int y = y0 + 1; y <= y0 + 3; y++) {
						c.set(p.atY(y), wall);
					}
					Direction in = Math.abs(dv) == 2 ? (dv < 0 ? r.side() : r.side().getOpposite()) : (du < 0 ? r.back() : r.back().getOpposite());
					c.set(p.atY(y0 + 4), corner ? BarrioKit.slabOf(roof) : BarrioKit.stair(roof, in));
				} else {
					for (int y = y0 + 1; y <= y0 + 3; y++) {
						c.set(p.atY(y), CitadelCanvas.AIR);
					}
					boolean inner = Math.abs(du) == 1 || Math.abs(dv) == 1;
					boolean icorner = Math.abs(du) == 1 && Math.abs(dv) == 1;
					if (inner) {
						Direction in = Math.abs(dv) == 1 && !icorner ? (dv < 0 ? r.side() : r.side().getOpposite())
								: (du < 0 ? r.back() : r.back().getOpposite());
						c.set(p.atY(y0 + 4), mats.ridge());
						c.set(p.atY(y0 + 5), icorner ? BarrioKit.slabOf(roof) : BarrioKit.stair(roof, in));
					} else {
						c.set(p.atY(y0 + 4), mats.ridge());
						c.set(p.atY(y0 + 5), mats.ridge());
					}
				}
			}
		}
		BlockPos lamp = r.cell(uc, vc, y0 + 3);
		c.put(lamp.getX(), lamp.getY(), lamp.getZ(), BarrioKit.lantern(true), CitadelCanvas.HANGING);
		c.set(r.cell(uc, vc, y0 + 6), BarrioKit.rod());
	}

	private static boolean hasChimney(int seed) {
		return (seed >>> 8) % 4 != 0;
	}

	private static boolean chimneySide(int seed) {
		return ((seed >>> 4) & 1) == 0;
	}

	/** Dormers on the long slopes: pattern 0 one on each side, 1 both on the near side, 2 both on the far side. */
	private static void dormers(CitadelCanvas c, Room r, int top, Mats mats, int seed) {
		int D = r.d();
		int pattern = seed % 3;
		int[] centres = {2, D - 3};
		for (int i = 0; i < centres.length; i++) {
			boolean near = switch (pattern) {
				case 0 -> i == 0;
				case 1 -> true;
				default -> false;
			};
			dormer(c, r, top, mats, centres[i], near);
		}
	}

	private static void dormer(CitadelCanvas c, Room r, int top, Mats mats, int uc, boolean near) {
		int W = r.w();
		Block roof = mats.roof();
		int vF = near ? DORMER_K - 1 : W - DORMER_K;
		int dj = near ? 1 : -1;
		Direction in = near ? r.side() : r.side().getOpposite();
		int yRoof = top + DORMER_K + 3;
		for (int du = -DORMER_HALF; du <= DORMER_HALF; du++) {
			int u = uc + du;
			for (int y = top + DORMER_K + 1; y <= top + DORMER_K + 2; y++) {
				c.set(r.cell(u, vF, y), du == 0 ? GLASS : mats.ridge());
			}
			// the cheeks run one cell back along the slope, closing the sides of the dormer
			if (du != 0) {
				c.set(r.cell(u, vF + dj, top + DORMER_K + 2), mats.ridge());
			}
			c.set(r.cell(u, vF, yRoof), BarrioKit.stair(roof, in));
			c.set(r.cell(u, vF + dj, yRoof), BarrioKit.slabOf(roof));
		}
	}

	/** A 1x2 chimney through the slope, up to {@code CHIMNEY_ABOVE_RIDGE} above the ridge, capped with a slab. */
	private static void chimney(CitadelCanvas c, Room r, int top, Mats mats, boolean near, int uc, IntBinaryOperator kAt) {
		int mid = r.c();
		int v = near ? mid - 2 : mid + 2;
		int yTop = top + mid + 1 + CHIMNEY_ABOVE_RIDGE;
		BlockState ch = BarrioKit.chimney(mats.roof());
		for (int du = 0; du <= 1; du++) {
			int u = uc + du;
			int ys = top + Math.max(1, kAt.applyAsInt(u, v));
			for (int y = ys; y <= yTop; y++) {
				c.set(r.cell(u, v, y), ch);
			}
			c.set(r.cell(u, v, yTop + 1), BarrioKit.chimneyCap(mats.roof()));
		}
	}

	// ------------------------------------------------------------------ hipped

	public static void hipped(CitadelCanvas c, Room r, int top, Mats mats) {
		int W = r.w();
		int D = r.d();
		int mid = r.c();
		Block roof = mats.roof();
		Lot lot = BarrioKit.lotOf(c, r);
		int ridgeA = mid;
		int ridgeB = D - mid - 1;
		// struct/barrio-hall2: a grand hall's pyramid is cut at ring kcap into a flat deck that carries a big cupola, so the
		// roof is about as tall as the wall instead of twice as tall
		boolean cap = grand(r);
		int kcap = cap ? Math.max(3, mid - 4) : Integer.MAX_VALUE;
		for (int u = -1; u <= D; u++) {
			for (int v = -1; v <= W; v++) {
				BlockPos p = r.cell(u, v, 0);
				int k = Math.min(Math.min(v + 1, W - v), Math.min(u + 1, D - u));
				int y = top + Math.max(1, k);
				if (cap && k > kcap) {
					c.set(p.atY(top + kcap + 1), mats.ridge());
					continue;
				}
				if (v == mid && k == mid + 1) {
					c.set(p.atY(y), mats.ridge());
					c.set(p.atY(y + 1), u == ridgeA || u == ridgeB ? BarrioKit.rod() : BarrioKit.slabOf(roof));
				} else {
					c.set(p.atY(y), hipStair(r, roof, u, v, k, false, p.atY(y)));
				}
			}
		}
		eaveRing(c, r, lot, -2, top, roof);
		// each slope ring rests on the one below it: one block under a stair of every ring from the second
		for (int k = 2; k <= Math.min(mid + 1, cap ? kcap + 1 : mid + 1); k++) {
			c.set(r.cell(k - 1, mid, top + k - 1), mats.ridge());
		}
		int seed = BarrioKit.hash(r.origin().getX(), r.origin().getZ(), top);
		if (cap) {
			// a gablet on each of the four slopes (a pediment over the door) and a glazed cupola on the deck
			IntBinaryOperator mainY = (u, v) -> {
				int k = Math.min(Math.min(v + 1, W - v), Math.min(u + 1, D - u));
				return k > kcap ? top + kcap + 1 : top + Math.max(1, k);
			};
			int rise = Math.min(3 + 3, kcap);
			crossGable(c, r, top, mats, 0, mid, 3, rise, mainY);
			crossGable(c, r, top, mats, 1, mid, 3, rise, mainY);
			crossGable(c, r, top, mats, 2, D / 2, 3, rise, mainY);
			crossGable(c, r, top, mats, 3, D / 2, 3, rise, mainY);
			cupola(c, r, top + kcap + 1, mats, (D - 1) / 2, mid);
		} else if (hasChimney(seed)) {
			IntBinaryOperator kAt = (u, v) -> Math.min(Math.min(v + 1, W - v), Math.min(u + 1, D - u));
			chimney(c, r, top, mats, chimneySide(seed), D / 2 - 1, kAt);
		}
	}

	/**
	 * The stair of a hip roof at a cell whose distance to the edge is {@code k}: straight on the slopes, an
	 * outer-corner stair on the hips (where two edges tie), so the corners close without gaps.
	 */
	static BlockState hipStair(Room r, Block mat, int u, int v, int k, boolean eave, BlockPos at) {
		int W = r.w();
		int D = r.d();
		boolean nv1 = v + 1 == k;
		boolean nv2 = W - v == k;
		boolean nu1 = u + 1 == k;
		boolean nu2 = D - u == k;
		Block m = eave ? BarrioKit.altStairs(mat) : BarrioKit.tone(mat, k, at.getX(), at.getY(), at.getZ());
		if ((nv1 || nv2) && (nu1 || nu2) && !(nv1 && nv2) && !(nu1 && nu2)) {
			Direction f = nu1 ? r.back() : r.back().getOpposite();
			boolean interiorPlus = nv1;
			boolean plusIsRight = nu1;
			StairsShape shape = interiorPlus == plusIsRight ? StairsShape.OUTER_RIGHT : StairsShape.OUTER_LEFT;
			return BarrioKit.stair(m, f, shape, Half.BOTTOM);
		}
		Direction f;
		if (nv1) {
			f = r.side();
		} else if (nv2) {
			f = r.side().getOpposite();
		} else if (nu1) {
			f = r.back();
		} else {
			f = r.back().getOpposite();
		}
		return BarrioKit.stair(m, f);
	}

	/**
	 * A ring of eave stairs (second tone) one cell outside a roof whose outermost cells are at {@code lo}
	 * (hall roofs: -1, so {@code lo = -2}; the gatehouse: 0, so {@code lo = -1}), at height {@code y}.
	 * Cells inside another building's reserved zone are skipped, whatever its state.
	 */
	static void eaveRing(CitadelCanvas c, Room r, Lot lot, int lo, int y, Block roof) {
		int W = r.w();
		int D = r.d();
		int k = lo + 1;
		for (int u = lo; u <= D - 1 - lo; u++) {
			for (int v = lo; v <= W - 1 - lo; v++) {
				if (u != lo && u != D - 1 - lo && v != lo && v != W - 1 - lo) {
					continue;
				}
				BlockPos p = r.cell(u, v, y);
				if (lot != null && BarrioKit.reserved(lot, r, p)) {
					continue;
				}
				c.set(p, hipStair(r, roof, u, v, k, true, p));
			}
		}
	}

	// ------------------------------------------------------------------ flat

	public static void flat(CitadelCanvas c, Room r, int top, Mats mats) {
		int W = r.w();
		int D = r.d();
		int mid = r.c();
		int seed = BarrioKit.hash(r.origin().getX(), r.origin().getZ(), top);
		boolean pergolaNear = (seed & 1) == 0;
		int pu = D - 4;
		int pv = pergolaNear ? 1 : W - 4;
		for (int u = -1; u <= D; u++) {
			for (int v = -1; v <= W; v++) {
				BlockPos p = r.cell(u, v, 0);
				if (r.wall(u, v)) {
					if (r.corner(u, v)) {
						c.set(p.atY(top + 1), mats.post());
						c.set(p.atY(top + 2), mats.post());
						c.set(p.atY(top + 3), BarrioKit.lantern(false));
					} else {
						int t = (u == -1 || u == D) ? v : u;
						c.set(p.atY(top + 1), mats.wall());
						c.set(p.atY(top + 2), Math.floorMod(t, 3) == 1 ? mats.wall() : slab(Blocks.DEEPSLATE_TILE_SLAB));
					}
				} else {
					c.set(p.atY(top + 1), Math.floorMod(u * 3 + v * 5, 7) == 0 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : b(Blocks.MOSS_CARPET));
				}
			}
		}
		// a small cherry tree on a bed of moss, clear of the pergola
		int tu = D / 2 - 1;
		BlockPos t = r.cell(tu, mid, top + 1);
		c.set(t, b(Blocks.MOSS_BLOCK));
		for (int i = 1; i <= 4; i++) {
			c.set(t.above(i), b(Blocks.CHERRY_LOG));
		}
		for (int du = -2; du <= 2; du++) {
			for (int dv = -2; dv <= 2; dv++) {
				int m = Math.abs(du) + Math.abs(dv);
				if (m == 0 || m > 2) {
					continue;
				}
				c.set(r.cell(tu + du, mid + dv, top + 4), leaves(Blocks.CHERRY_LEAVES));
				if (m == 1) {
					c.set(r.cell(tu + du, mid + dv, top + 5), leaves(Blocks.CHERRY_LEAVES));
				}
			}
		}
		c.set(r.cell(tu, mid, top + 6), leaves(Blocks.CHERRY_LEAVES));
		pergola(c, r, top, pu, pv);
	}

	/** A 3x3 pergola: four posts, an H of slabs on top and a lantern hanging from the middle. */
	private static void pergola(CitadelCanvas c, Room r, int top, int pu, int pv) {
		for (int du = 0; du <= 2; du++) {
			for (int dv = 0; dv <= 2; dv++) {
				boolean post = du != 1 && dv != 1;
				if (post) {
					for (int y = top + 1; y <= top + 3; y++) {
						c.set(r.cell(pu + du, pv + dv, y), b(Blocks.CHERRY_FENCE));
					}
				}
				if (du % 2 == 0 || dv == 1) {
					c.set(r.cell(pu + du, pv + dv, top + 4), BarrioKit.slabOf(Blocks.CHERRY_STAIRS));
				}
			}
		}
		c.set(r.cell(pu + 1, pv + 1, top + 3), BarrioKit.lantern(true));
	}
}
