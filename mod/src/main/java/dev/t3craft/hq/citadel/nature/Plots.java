package dev.t3craft.hq.citadel.nature;

import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.GRASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.LIGHT;
import static dev.t3craft.hq.citadel.CitadelCanvas.MOSS;
import static dev.t3craft.hq.citadel.CitadelCanvas.WATER;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.col;
import static dev.t3craft.hq.citadel.CitadelCanvas.colX;
import static dev.t3craft.hq.citadel.CitadelCanvas.colZ;
import static dev.t3craft.hq.citadel.CitadelCanvas.lattice;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.nature.Flora.hash;

import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Lot;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.nature.Trees.Species;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The empty lots of the citadel: a lot with no project is a plot waiting for its barrio, and each one
 * looks like a different kind of waiting. {@link #lot(CitadelCanvas, int)} paints the whole lot: first
 * one of five plots in the middle of it (chosen by the lot id, so neighbours differ and the same lot is
 * always the same), then the meadow that covers the rest of the wedge, then a few lone trees at its
 * corners.
 *
 * <ul>
 * <li>{@link #huerto}: a vegetable garden of crop rows with water channels, a scarecrow, a composter and
 *     barrels, inside a fence with a gate towards the hub.</li>
 * <li>{@link #jardin}: a formal garden inside a hedge: paved cross paths, a fountain, four beds edged by
 *     low hedges with a palette of flowers each, and trees in two of them.</li>
 * <li>{@link #obra}: a building site: the footing and half-raised walls of a hall, a timber frame,
 *     scaffolding, piles of logs, planks and stone, a crane with a hanging lantern, trodden earth.</li>
 * <li>{@link #estanque}: a meadow with a pond (lily pads, reeds, a little dock), a winding path from the
 *     gate, birches and benches.</li>
 * <li>{@link #arboleda}: a cherry grove on moss with petals, a clearing with a stone lantern and
 *     benches, stepping stones.</li>
 * </ul>
 *
 * <p>The frame is the lot's own ({@link Lot}): {@code a} runs along {@code out} (away from the hub,
 * 0 at the lot's centre), {@code b} along {@code across}. A plot is a square of half side
 * {@code court + 6} (11 in ring 1): the barrio's cluster would fill the same square. Nothing is
 * placed outside the lot's own columns, nor over the street-light lattice at {@code y = 0}; every gate
 * is a gap, so nothing stops anyone walking in.
 */
public final class Plots {
	private Plots() {
	}

	private static final int PLOTS = 5;

	/** The plot a lot gets: lots of one ring get different plots in a row. */
	public static int variant(int lot) {
		return Math.floorMod(lot * 3, PLOTS);
	}

	/** Paints the whole empty lot {@code lot}. */
	public static void lot(CitadelCanvas c, int lot) {
		Plot p = new Plot(c, lot);
		switch (variant(lot)) {
			case 0 -> huerto(p);
			case 1 -> jardin(p);
			case 2 -> obra(p);
			case 3 -> estanque(p);
			default -> arboleda(p);
		}
		meadow(p);
		strayTrees(p);
		unpath(p);
	}

	/** A dirt path with a block over it turns into dirt in the game: make it coarse dirt where something stands on it. */
	private static void unpath(Plot p) {
		for (long k : p.colList) {
			int x = colX(k);
			int z = colZ(k);
			BlockState g = p.c.at(x, 0, z);
			BlockState above = p.c.at(x, 1, z);
			if (g != null && g.is(Blocks.DIRT_PATH) && above != null && !above.isAir()
					&& !above.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).isEmpty()) {
				p.c.set(x, 0, z, b(Blocks.COARSE_DIRT));
			}
		}
	}

	// ------------------------------------------------------------------ the lot's frame

	/** The lot's own frame and the columns it may paint. */
	private static final class Plot {
		final CitadelCanvas c;
		final int lot;
		final Lot l;
		final int h;
		final Set<Long> cols = new HashSet<>();
		final List<Long> colList;

		Plot(CitadelCanvas c, int lot) {
			this.c = c;
			this.lot = lot;
			this.l = RingCampusGeometry.lot(lot);
			this.h = l.court() + 6;
			this.colList = c.lotCols.getOrDefault(lot, List.of());
			cols.addAll(colList);
		}

		int x(int a, int bb) {
			return l.at(a, bb, 0).getX();
		}

		int z(int a, int bb) {
			return l.at(a, bb, 0).getZ();
		}

		boolean in(int a, int bb) {
			return cols.contains(col(x(a, bb), z(a, bb)));
		}

		/** The whole square of side {@code 2r + 1} round {@code (a, b)} belongs to the lot. */
		boolean fits(int a, int bb, int r) {
			for (int i = -r; i <= r; i += r) {
				for (int j = -r; j <= r; j += r) {
					if (!in(a + i, bb + j)) {
						return false;
					}
				}
			}
			return true;
		}

		/** The floor of the column (skips the lamps of the street-light lattice). */
		void ground(int a, int bb, BlockState s) {
			if (in(a, bb) && c.at(x(a, bb), 0, z(a, bb)) != LIGHT) {
				c.set(x(a, bb), 0, z(a, bb), s);
			}
		}

		BlockState floor(int a, int bb) {
			return c.at(x(a, bb), 0, z(a, bb));
		}

		/** Puts {@code s} at height {@code y} of the column if it is free. */
		void put(int a, int bb, int y, BlockState s) {
			if (in(a, bb) && Flora.free(c, x(a, bb), y, z(a, bb))) {
				c.set(x(a, bb), y, z(a, bb), s);
			}
		}

		/** Replaces whatever is at the height (a hedge, say). */
		void force(int a, int bb, int y, BlockState s) {
			if (in(a, bb)) {
				c.set(x(a, bb), y, z(a, bb), s);
			}
		}

		void column(int a, int bb, int y1, int y2, BlockState s) {
			for (int y = y1; y <= y2; y++) {
				put(a, bb, y, s);
			}
		}

		boolean free(int a, int bb, int y) {
			return in(a, bb) && Flora.free(c, x(a, bb), y, z(a, bb));
		}

		boolean planting(int a, int bb) {
			return in(a, bb) && Flora.planting(c, x(a, bb), z(a, bb));
		}

		void plant(int a, int bb, BlockState s) {
			if (in(a, bb)) {
				Flora.plant(c, x(a, bb), z(a, bb), s);
			}
		}

		void tall(int a, int bb, Block block) {
			if (in(a, bb)) {
				Flora.tall(c, x(a, bb), z(a, bb), block);
			}
		}

		void tree(int a, int bb, Species sp, int seed) {
			if (fits(a, bb, 3)) {
				Trees.tree(c, x(a, bb), z(a, bb), sp, seed);
			}
		}

		/** The direction (cardinal) from {@code (a, b)} towards {@code (a2, b2)}. */
		Direction toward(int a, int bb, int a2, int b2) {
			int da = a2 - a;
			int db = b2 - bb;
			if (Math.abs(da) >= Math.abs(db)) {
				return da >= 0 ? l.out() : l.out().getOpposite();
			}
			return db >= 0 ? l.across() : l.across().getOpposite();
		}

		/** The axis of the world that {@code out} lies on. */
		Direction.Axis outAxis() {
			return l.out().getAxis();
		}

		Direction.Axis acrossAxis() {
			return l.across().getAxis();
		}

		int salt(int k) {
			return hash(lot, k, 5309);
		}
	}

	// ------------------------------------------------------------------ shared blocks

	private static BlockState axis(Block block, Direction.Axis a) {
		return block.defaultBlockState().setValue(BlockStateProperties.AXIS, a);
	}

	private static BlockState fence(int lot) {
		return switch (Math.floorMod(lot, 3)) {
			case 0 -> b(Blocks.SPRUCE_FENCE);
			case 1 -> b(Blocks.CHERRY_FENCE);
			default -> b(Blocks.OAK_FENCE);
		};
	}

	private static BlockState lantern() {
		return b(Blocks.LANTERN);
	}

	/** A fence on the square of half side {@code h}, with a gap {@code gap} wide on the hub side and a lantern post on each side of it. */
	private static void fencedSquare(Plot p, int h, int gapHalf, boolean backGap) {
		BlockState f = fence(p.lot);
		for (int i = -h; i <= h; i++) {
			for (int side = 0; side < 4; side++) {
				int a = side == 0 ? -h : side == 1 ? h : i;
				int bb = side == 2 ? -h : side == 3 ? h : i;
				if (side == 0 && Math.abs(bb) <= gapHalf) {
					continue;
				}
				if (side == 1 && backGap && Math.abs(bb) <= gapHalf) {
					continue;
				}
				p.put(a, bb, 1, f);
			}
		}
		for (int sg = -1; sg <= 1; sg += 2) {
			int bb = sg * (gapHalf + 1);
			p.put(-h, bb, 2, f);
			p.put(-h, bb, 3, lantern());
		}
	}

	/** A path cell: the floor of the kit in two tones, matching the streets. */
	private static BlockState pave(Plot p, int a, int bb) {
		return (a + bb & 1) == 0 ? p.c.accent() : FOOT;
	}

	// ------------------------------------------------------------------ 0: vegetable garden

	/** A vegetable garden: a fence, crop rows with water channels, a path, a scarecrow, a composter, barrels and hay. */
	private static void huerto(Plot p) {
		int h = p.h;
		fencedSquare(p, h, 1, false);
		for (int a = -h + 1; a <= h - 1; a++) {
			int row = Math.floorMod(a + h, 4);
			int rowId = Math.floorDiv(a + h, 4);
			for (int bb = -h + 1; bb <= h - 1; bb++) {
				if (bb == 0) {
					p.ground(a, bb, b(Blocks.DIRT_PATH));
					continue;
				}
				if (a == -h + 1 || a == h - 1) {
					continue; // a grass margin along the fence
				}
				if (row == 3) {
					if (p.in(a, bb) && Math.abs(bb) <= h - 2) {
						p.ground(a, bb, WATER);
						if (p.floor(a, bb) == WATER) {
							p.c.set(p.x(a, bb), -1, p.z(a, bb), FOOT);
						}
					}
				} else if (row == 0) {
					if (Math.abs(bb) <= h - 2) {
						p.ground(a, bb, b(Blocks.DIRT_PATH));
					}
				} else if (Math.abs(bb) <= h - 2) {
					p.ground(a, bb, b(Blocks.FARMLAND).setValue(BlockStateProperties.MOISTURE, 7));
					if (p.floor(a, bb) != null && p.floor(a, bb).is(Blocks.FARMLAND)) {
						p.put(a, bb, 1, crop(rowId + (bb > 0 ? 1 : 0) + p.lot, a, bb));
					}
				}
			}
		}
		// the scarecrow at the end of the path
		int sa = h - 3;
		p.put(sa, 0, 1, b(Blocks.HAY_BLOCK));
		p.put(sa, 0, 2, fence(p.lot));
		p.put(sa, -1, 2, fence(p.lot));
		p.put(sa, 1, 2, fence(p.lot));
		p.put(sa, 0, 3, b(Blocks.CARVED_PUMPKIN).setValue(BlockStateProperties.HORIZONTAL_FACING, p.l.out().getOpposite()));
		// by the gate: composter, barrels and a water cauldron
		p.put(-h + 1, h - 1, 1, b(Blocks.COMPOSTER));
		p.put(-h + 1, h - 2, 1, b(Blocks.BARREL));
		p.put(-h + 1, -(h - 1), 1, b(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		p.put(-h + 1, -(h - 2), 1, b(Blocks.BARREL));
		// hay bales in a corner
		for (int i = 0; i < 2; i++) {
			for (int j = 0; j < 2; j++) {
				p.put(h - 1 - i, -(h - 1) + j, 1, b(Blocks.HAY_BLOCK));
			}
		}
		p.put(h - 1, -(h - 1), 2, b(Blocks.HAY_BLOCK));
		// flowers at the foot of the fence outside, to soften it
		for (int i = -h; i <= h; i++) {
			if (Math.abs(i) > 2) {
				p.plant(-h - 1, i, Flora.bloom(p.lot, hash(i, p.lot, 1) >> 4));
			}
		}
	}

	private static BlockState crop(int kind, int a, int bb) {
		int k = Math.floorMod(kind, 4);
		int age = 5 + hash(a, bb, 3) % 3;
		return switch (k) {
			case 0 -> b(Blocks.WHEAT).setValue(BlockStateProperties.AGE_7, 7);
			case 1 -> b(Blocks.CARROTS).setValue(BlockStateProperties.AGE_7, Math.min(7, age + 1));
			case 2 -> b(Blocks.POTATOES).setValue(BlockStateProperties.AGE_7, Math.min(7, age + 1));
			default -> b(Blocks.BEETROOTS).setValue(BlockStateProperties.AGE_3, 3);
		};
	}

	// ------------------------------------------------------------------ 1: formal garden

	/** A formal garden: a hedge with four gates, a fountain, cross paths, four beds with a palette each, two trees. */
	private static void jardin(Plot p) {
		int h = p.h;
		// hedge and corner posts
		for (int i = -h; i <= h; i++) {
			for (int side = 0; side < 4; side++) {
				int a = side == 0 ? -h : side == 1 ? h : i;
				int bb = side == 2 ? -h : side == 3 ? h : i;
				boolean gate = side < 2 ? Math.abs(bb) <= 1 : Math.abs(a) <= 1;
				if (gate || Math.abs(i) == h) {
					continue;
				}
				BlockState lf = Flora.bush(hash(a, bb, 9));
				p.put(a, bb, 1, lf);
				p.put(a, bb, 2, lf);
			}
		}
		for (int sa = -1; sa <= 1; sa += 2) {
			for (int sb = -1; sb <= 1; sb += 2) {
				p.column(sa * h, sb * h, 1, 3, b(Blocks.STONE_BRICK_WALL));
				p.put(sa * h, sb * h, 4, lantern());
			}
		}
		// gates: two paving cells under each opening, lanterns on the hedge ends
		for (int sg = -1; sg <= 1; sg += 2) {
			for (int[] q : new int[][] {{-h, sg * 2}, {h, sg * 2}, {sg * 2, -h}, {sg * 2, h}}) {
				p.force(q[0], q[1], 1, b(Blocks.STONE_BRICK_WALL));
				p.force(q[0], q[1], 2, b(Blocks.STONE_BRICK_WALL));
				p.force(q[0], q[1], 3, lantern());
			}
		}
		// beds and paths
		for (int a = -h + 1; a <= h - 1; a++) {
			for (int bb = -h + 1; bb <= h - 1; bb++) {
				p.ground(a, bb, Flora.turf(a, bb));
			}
		}
		for (int i = -h; i <= h; i++) {
			if (Math.abs(i) >= 1) {
				p.ground(i, 0, pave(p, i, 0));
				p.ground(0, i, pave(p, 0, i));
			}
		}
		p.ground(0, 0, FOOT);
		fountain(p);
		// ring path round the fountain
		for (int a = -4; a <= 4; a++) {
			for (int bb = -4; bb <= 4; bb++) {
				double r = Math.hypot(a, bb);
				if (r >= 2.8 && r < 3.7) {
					p.ground(a, bb, pave(p, a, bb));
				}
			}
		}
		// the four beds
		for (int q = 0; q < 4; q++) {
			int sa = (q & 1) == 0 ? -1 : 1;
			int sb = (q & 2) == 0 ? -1 : 1;
			int pal = p.lot + q;
			for (int ia = 1; ia <= h - 1; ia++) {
				for (int ib = 1; ib <= h - 1; ib++) {
					int a = sa * ia;
					int bb = sb * ib;
					double r = Math.hypot(a, bb);
					if (r < 3.7) {
						continue;
					}
					boolean edge = ia == 1 || ib == 1 || ia == h - 1 || ib == h - 1;
					if (edge) {
						p.put(a, bb, 1, leaves(Blocks.AZALEA_LEAVES));
					}
				}
			}
			// trees in two beds, flowers in all
			int ta = sa * (h / 2 + 2);
			int tb = sb * (h / 2 + 1);
			if (q == 0 || q == 3) {
				p.tree(ta, tb, q == 0 ? Species.CHERRY_ROUND : Species.CHERRY_WEEPING, p.salt(q));
			} else {
				p.tree(ta, tb, Species.AZALEA, p.salt(q));
			}
			for (int ia = 2; ia <= h - 2; ia++) {
				for (int ib = 2; ib <= h - 2; ib++) {
					int a = sa * ia;
					int bb = sb * ib;
					if (Math.hypot(a, bb) < 4.2) {
						continue;
					}
					int hh = hash(a, bb, 77 + q);
					int ring = Math.min(ia, ib) - 2;
					if (ring % 3 == 2) {
						p.plant(a, bb, b(Blocks.SHORT_GRASS));
					} else if (hh % 100 < 82) {
						p.plant(a, bb, Flora.bloom(pal + ring / 3, hh >> 8));
					}
				}
			}
			p.tall(sa * 4, sb * 4, Flora.bigBloom(pal));
			p.tall(sa * (h - 3), sb * 4, Flora.bigBloom(pal + 1));
			p.tall(sa * 4, sb * (h - 3), Flora.bigBloom(pal + 1));
		}
	}

	/** The fountain: a stone rim with a slab, a pool and a wall pillar with a lantern. */
	private static void fountain(Plot p) {
		for (int a = -3; a <= 3; a++) {
			for (int bb = -3; bb <= 3; bb++) {
				double r = Math.hypot(a, bb);
				if (r < 1.5) {
					p.ground(a, bb, WATER);
					if (p.floor(a, bb) == WATER) {
						p.c.set(p.x(a, bb), -1, p.z(a, bb), FOOT);
					}
				} else if (r < 2.5) {
					p.ground(a, bb, FOOT);
					p.put(a, bb, 1, slab(Blocks.STONE_BRICK_SLAB));
				}
			}
		}
		p.ground(0, 0, FOOT);
		p.column(0, 0, 1, 2, b(Blocks.STONE_BRICK_WALL));
		p.put(0, 0, 3, lantern());
	}

	// ------------------------------------------------------------------ 2: building site

	/** A building site: trodden earth, the footing and half-raised walls of a hall in a timber frame, scaffolding, piles and a crane. */
	private static void obra(Plot p) {
		int h = p.h;
		// trodden earth
		for (int a = -h + 1; a <= h - 1; a++) {
			for (int bb = -h + 1; bb <= h - 1; bb++) {
				int hh = hash(a, bb, 31);
				BlockState g = hh % 100 < 45 ? b(Blocks.COARSE_DIRT) : hh % 100 < 75 ? b(Blocks.DIRT_PATH) : hh % 100 < 85 ? b(Blocks.PACKED_MUD) : null;
				if (g != null && (Math.abs(a) <= h - 3 || Math.abs(bb) <= h - 3) && hash(a / 3, bb / 3, 8) % 4 != 0) {
					p.ground(a, bb, g);
				}
			}
		}
		// the footing: a ring of stone brick at y = 1 with raised stubs, half the floor planked
		int fa = Dims.s(4);
		int fb = Dims.s(5);
		for (int a = -fa; a <= fa; a++) {
			for (int bb = -fb; bb <= fb; bb++) {
				boolean edge = Math.abs(a) == fa || Math.abs(bb) == fb;
				boolean corner = Math.abs(a) == fa && Math.abs(bb) == fb;
				if (edge) {
					p.ground(a, bb, FOOT);
					boolean door = a == -fa && Math.abs(bb) <= 1;
					if (!door) {
						p.put(a, bb, 1, b(Blocks.STONE_BRICKS));
						int hh = hash(a, bb, 12);
						if (hh % 100 < 55 && !corner) {
							p.put(a, bb, 2, hh % 3 == 0 ? slab(Blocks.STONE_BRICK_SLAB) : b(Blocks.MOSSY_STONE_BRICKS));
						}
					}
				} else if (a >= 0) {
					p.ground(a, bb, b(Blocks.SPRUCE_PLANKS));
				} else if (hash(a, bb, 14) % 3 == 0) {
					p.ground(a, bb, b(Blocks.COBBLESTONE));
				}
			}
		}
		// timber frame: corner posts, mid posts and two beams
		int[][] posts = {{-fa, -fb}, {-fa, fb}, {fa, -fb}, {fa, fb}, {fa, 0}, {0, -fb}, {0, fb}};
		for (int[] q : posts) {
			p.column(q[0], q[1], 1, Dims.sy(5), axis(Blocks.SPRUCE_LOG, Direction.Axis.Y));
		}
		for (int bb = -fb + 1; bb < fb; bb++) {
			p.put(fa, bb, Dims.sy(5), axis(Blocks.SPRUCE_LOG, p.acrossAxis()));
			if (bb < -1 || bb > 1) {
				p.put(-fa, bb, Dims.sy(5), axis(Blocks.SPRUCE_LOG, p.acrossAxis()));
			}
		}
		for (int a = 1; a < fa; a++) {
			p.put(a, -fb, Dims.sy(5), axis(Blocks.SPRUCE_LOG, p.outAxis()));
		}
		// scaffolding wall behind the footing
		for (int bb = -2; bb <= 2; bb++) {
			for (int y = 1; y <= 4; y++) {
				p.put(fa + 2, bb, y, b(Blocks.SCAFFOLDING).setValue(ScaffoldingBlock.DISTANCE, 0).setValue(ScaffoldingBlock.BOTTOM, false));
			}
		}
		// log pile (stepped), plank stack, stone stack
		for (int y = 1; y <= 3; y++) {
			for (int a = -h + 3; a <= -h + 5 - y + 1; a++) {
				for (int bb = 4; bb <= 7; bb++) {
					p.put(a, -bb - 2, y, axis(Blocks.CHERRY_LOG, p.acrossAxis()));
				}
			}
		}
		for (int a = 0; a < 3; a++) {
			for (int bb = 0; bb < 2; bb++) {
				p.column(-h + 4 + a, 8 + bb, 1, a == 1 ? 3 : 2, b(Blocks.SPRUCE_PLANKS));
			}
		}
		p.put(-h + 4, 10, 1, slab(Blocks.SPRUCE_SLAB));
		for (int a = 0; a < 2; a++) {
			for (int bb = 0; bb < 2; bb++) {
				p.column(fa + 3 + a, 4 + bb * 1, 1, 2, b(Blocks.STONE_BRICKS));
				p.put(fa + 3 + a, 4 + bb, 3, slab(Blocks.STONE_BRICK_SLAB));
			}
		}
		// site furniture: barrels, crafting table, stonecutter
		p.put(-fa - 2, fb + 2, 1, b(Blocks.BARREL));
		p.put(-fa - 2, fb + 3, 1, b(Blocks.BARREL));
		p.put(-fa - 3, fb + 2, 1, b(Blocks.CRAFTING_TABLE));
		p.put(-fa - 3, fb + 3, 1, b(Blocks.STONECUTTER));
		// crane: a log mast with a boom, a chain and a lantern
		int ma = -fa - 2;
		int mb = -fb - 3;
		p.column(ma, mb, 1, Dims.sy(7), axis(Blocks.SPRUCE_LOG, Direction.Axis.Y));
		for (int i = 1; i <= 5; i++) {
			p.put(ma, mb + i, Dims.sy(7), axis(Blocks.STRIPPED_SPRUCE_LOG, p.acrossAxis()));
		}
		int boom = Dims.sy(7);
		p.put(ma, mb + 5, boom - 1, b(Blocks.IRON_CHAIN));
		p.put(ma, mb + 5, boom - 2, b(Blocks.IRON_CHAIN));
		p.put(ma, mb + 5, boom - 3, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true));
		// a fence along the sides, open at the gate
		BlockState f = b(Blocks.SPRUCE_FENCE);
		for (int bb = -h; bb <= h; bb++) {
			if (Math.abs(bb) >= 3) {
				p.put(-h, bb, 1, f);
			}
		}
		for (int sg = -1; sg <= 1; sg += 2) {
			p.put(-h, sg * 3, 2, f);
			p.put(-h, sg * 3, 3, lantern());
		}
	}

	// ------------------------------------------------------------------ 3: meadow with a pond

	/** A meadow with a pond and a dock, a winding path from the gate, birches, a cherry and benches. */
	private static void estanque(Plot p) {
		int h = p.h;
		int ca = Dims.s(2);
		int cb = Dims.s(-2 + Math.floorMod(p.lot, 3) * 2 - 2);
		double rx = Dims.sd(5.6);
		double ry = Dims.sd(4.3);
		// the pond: an ellipse with a wobbly edge, a deep bed
		int pr = (int) Math.ceil(rx) + 2;
		int pq = (int) Math.ceil(ry) + 2;
		for (int a = ca - pr; a <= ca + pr; a++) {
			for (int bb = cb - pq; bb <= cb + pq; bb++) {
				double dx = (a - ca) / rx;
				double dz = (bb - cb) / ry;
				double d = dx * dx + dz * dz + (hash(a, bb, 3) % 100 - 50) / 700.0;
				if (d <= 1.0) {
					p.ground(a, bb, WATER);
					if (p.floor(a, bb) == WATER) {
						p.c.set(p.x(a, bb), -1, p.z(a, bb), b(Blocks.MUD));
						if (d < 0.7 && hash(a, bb, 4) % 100 < 20) {
							p.put(a, bb, 1, b(Blocks.LILY_PAD));
						}
					}
				} else if (d <= 1.45) {
					int hh = hash(a, bb, 5) % 5;
					p.ground(a, bb, hh == 0 ? b(Blocks.MUD) : hh == 1 ? MOSS : hh == 2 ? b(Blocks.COARSE_DIRT) : GRASS);
				}
			}
		}
		// the dock: planks into the water from the side of the gate, with posts and a lantern
		int da = ca - (int) Math.ceil(rx) - 1;
		for (int a = da; a <= ca - 2; a++) {
			if (p.in(a, cb)) {
				p.ground(a, cb, b(Blocks.SPRUCE_PLANKS));
				p.ground(a, cb + 1, b(Blocks.SPRUCE_PLANKS));
			}
		}
		p.put(ca - 2, cb + 1, 1, fence(p.lot));
		p.put(ca - 2, cb + 1, 2, lantern());
		p.put(ca - 2, cb, 1, fence(p.lot));
		// a winding path from the gate to the dock
		int pb = 0;
		for (int a = -h; a <= da - 1; a++) {
			pb += Integer.compare(cb, pb) * (hash(a, 1, 21) % 3 == 0 ? 1 : 0);
			int wob = (int) Math.round(Math.sin(a * 0.55 + p.lot) * 1.4);
			for (int w = 0; w <= 1; w++) {
				p.ground(a, pb + wob + w, a % 3 == 0 ? b(Blocks.COBBLESTONE) : b(Blocks.DIRT_PATH));
			}
		}
		// reeds on the rim
		for (int a = ca - pr - 1; a <= ca + pr + 1; a++) {
			for (int bb = cb - pq - 1; bb <= cb + pq + 1; bb++) {
				if (p.planting(a, bb) && hash(a, bb, 6) % 100 < 36 && nextToWater(p, a, bb)) {
					int hgt = 1 + hash(a, bb, 7) % 3;
					for (int y = 1; y <= hgt; y++) {
						p.put(a, bb, y, b(Blocks.SUGAR_CANE));
					}
				}
			}
		}
		// benches facing the pond
		int bb0 = cb + (int) Math.ceil(ry) + 2;
		for (int k = 0; k < 2; k++) {
			p.put(ca + k * 2, bb0, 1, stairs(Blocks.SPRUCE_STAIRS, p.toward(ca + k * 2, bb0, ca, cb)));
			p.put(ca + k * 2 + 1, bb0, 1, stairs(Blocks.SPRUCE_STAIRS, p.toward(ca + k * 2 + 1, bb0, ca, cb)));
		}
		// trees: birches behind, a cherry by the water
		p.tree(h - 3, -h + 3, Species.BIRCH, p.salt(1));
		p.tree(h - 2, -h + 7, Species.BIRCH, p.salt(2));
		p.tree(h - 5, h - 3, Species.BIRCH, p.salt(3));
		p.tree(ca + 6, cb - 7, Species.CHERRY_WEEPING, p.salt(4));
		p.tree(-h + 3, -h + 4, Species.AZALEA, p.salt(5));
	}

	private static boolean nextToWater(Plot p, int a, int bb) {
		return Flora.isWater(p.floor(a + 1, bb)) || Flora.isWater(p.floor(a - 1, bb)) || Flora.isWater(p.floor(a, bb + 1))
				|| Flora.isWater(p.floor(a, bb - 1));
	}

	// ------------------------------------------------------------------ 4: cherry grove

	/** A cherry grove: trees on a jittered grid over moss and petals, a clearing with a stone lantern and benches. */
	private static void arboleda(Plot p) {
		int h = p.h;
		// moss patches on the ground
		for (int a = -h - 2; a <= h + 2; a++) {
			for (int bb = -h - 2; bb <= h + 2; bb++) {
				double v = Math.sin(a * 0.45 + p.lot) + Math.cos(bb * 0.4 - p.lot * 1.7) + (hash(a, bb, 3) % 100) / 120.0;
				if (v > 0.9) {
					p.ground(a, bb, MOSS);
				}
			}
		}
		// the clearing: moss disc with a stone lantern and two benches
		for (int a = -4; a <= 4; a++) {
			for (int bb = -4; bb <= 4; bb++) {
				double r = Math.hypot(a, bb);
				if (r <= 3.6) {
					p.ground(a, bb, r <= 1.5 ? FOOT : (hash(a, bb, 2) % 3 == 0 ? b(Blocks.COARSE_DIRT) : MOSS));
				}
			}
		}
		p.column(0, 0, 1, 3, b(Blocks.STONE_BRICK_WALL));
		p.put(0, 0, 4, b(Blocks.LANTERN));
		p.put(-2, 0, 1, stairs(Blocks.CHERRY_STAIRS, p.l.out()));
		p.put(2, 0, 1, stairs(Blocks.CHERRY_STAIRS, p.l.out().getOpposite()));
		p.put(0, 2, 1, stairs(Blocks.CHERRY_STAIRS, p.l.across().getOpposite()));
		p.put(0, -2, 1, stairs(Blocks.CHERRY_STAIRS, p.l.across()));
		// stepping stones from the gate
		for (int a = -h; a < -3; a++) {
			if (a % 2 == 0) {
				p.ground(a, 0, b(Blocks.DIRT_PATH));
				p.ground(a, 1, b(Blocks.DIRT_PATH));
			}
		}
		// the trees on a jittered grid, skipping the clearing
		int step = Dims.s(7);
		for (int i = -1; i <= 1; i++) {
			for (int j = -1; j <= 1; j++) {
				if (i == 0 && j == 0) {
					continue;
				}
				int hh = hash(i, j, p.salt(3));
				if (hh % 100 < 12) {
					continue;
				}
				int a = i * step + hh % 3 - 1;
				int bb = j * step + (hh >> 4) % 3 - 1;
				Species sp = hh % 5 == 0 ? Species.AZALEA : Trees.cherry(hh >> 8);
				p.tree(a, bb, sp, hh);
			}
		}
	}

	// ------------------------------------------------------------------ the rest of the lot

	/** The meadow over every free soil column of the lot, quiet inside the tidy plots. */
	private static void meadow(Plot p) {
		int v = variant(p.lot);
		for (long k : p.colList) {
			int x = colX(k);
			int z = colZ(k);
			int a = p.l.a(x, z);
			int bb = p.l.b(x, z);
			boolean inside = Math.max(Math.abs(a), Math.abs(bb)) <= p.h;
			if (inside && (v == 0 || v == 1 || v == 2)) {
				continue;
			}
			Flora.meadow(p.c, x, z, a, bb, p.salt(11));
		}
	}

	/** Lone trees at the corners of the wedge, away from the plot, on a loose grid. */
	private static void strayTrees(Plot p) {
		int step = Dims.s(9);
		for (int a = -4 * step; a <= 4 * step; a += step) {
			for (int bb = -4 * step; bb <= 4 * step; bb += step) {
				int hh = hash(a, bb, p.salt(21));
				int ja = a + hh % 5 - 2;
				int jb = bb + (hh >> 5) % 5 - 2;
				if (Math.max(Math.abs(ja), Math.abs(jb)) <= p.h + 3 || hh % 100 > 40) {
					continue;
				}
				if (!p.fits(ja, jb, 3) || lattice(p.x(ja, jb), p.z(ja, jb))) {
					continue;
				}
				p.tree(ja, jb, Trees.pick(hh >> 9), hh);
			}
		}
	}
}
