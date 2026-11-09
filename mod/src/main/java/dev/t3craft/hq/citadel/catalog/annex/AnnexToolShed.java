package dev.t3craft.hq.citadel.catalog.annex;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Tool shed: a real workshop you walk into. A two-block cobble plinth, board-and-batten walls between
 * posts, a double door (2 wide, reached by two stone steps under a porch hood on two posts), a shuttered
 * window in the side wall and potted plants under the front windows. The roof is a gable (stairs, ridge
 * running front to back) or a single pitch, by seed, with a trim fascia and an eave all round.
 *
 * <p>Inside (4 free blocks at the walls, 5 or more under the roof): a workbench in the middle of the back
 * wall (crafting table, smithing table or grindstone, by seed) between chests and barrels, tool hooks on
 * the wall above them, a row of barrels and chests along the left wall, an aisle at least 2 wide from the
 * door to the bench and one lantern hung from the roof. Outside: a rain barrel (water cauldron), a
 * composter and hay under the left eave, and the firewood stacked under the right eave. From 10 wide, a
 * three-wide open wood lean-to with a stack two deep, a chopping stump and a lantern post.
 *
 * <p>Seed: mirror (door and bench side, woodpile side), roof (gable / single pitch), wood set (dark oak and
 * spruce under cherry; spruce and oak under dark oak; dark oak and birch under deepslate tile), workstation.
 *
 * <pre>
 *  front (gable, bw = 7)                            plan (w = 9, d = 7)   v0 stoop · · · ▯▯ · · ·
 *       ▲ ridge slab                                v1  ┌───────┐ post, door ▯▯ in the front wall
 *      ╱ ╲  vent pane                               v2  │ ·  ·  │ aisle
 *    ╱     ╲  gable fill                            v3  │▤ ·  · │▤ chests / barrels on the left wall
 *  ═══╗  ▯▯ ╔═══ eave (trim stairs)                  v4  │▤ ·  · │
 *     ║ ░   ║  windows over the sill                v5  │ ▥ ▣ ▥ │ barrel, bench, chest (back row)
 *  ▓▓▓▓▓▓▓▓▓▓▓ plinth y0-y1                         v6  └───────┘ ≡ firewood under the right eave
 * </pre>
 *
 * Size: 5–11 × 5–10 × 9+ (the shed itself is at most 7 wide; wider plots grow a lean-to and a yard).
 */
public final class AnnexToolShed implements Variant {
	// vertical layout: y0 floor and plinth, y1 plinth, y2..y3 timber, y4 plate, y5+ roof
	private static final int PLATE = 4;
	private static final int ROOF = 5;

	/** Roof stair/slab, trim (fascia, ridge), wall posts and plate, wall panel, door and shutters, by wood set. */
	private record Pal(Block roofStairs, Block roofSlab, Block altStairs, Block trimStairs, Block trimSlab, Block frame, Block plate,
			Block panel, Block panelAlt, Block door, Block trapdoor) {
	}

	private static final Pal[] PALS = {
			// dark oak frame, spruce boards, pink cherry roof
			new Pal(Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB, null, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_LOG,
					Blocks.STRIPPED_DARK_OAK_LOG, Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_DOOR, Blocks.SPRUCE_TRAPDOOR),
			// spruce frame, oak boards, dark oak roof
			new Pal(Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_LOG,
					Blocks.STRIPPED_SPRUCE_LOG, Blocks.OAK_PLANKS, Blocks.STRIPPED_OAK_LOG, Blocks.OAK_DOOR, Blocks.OAK_TRAPDOOR),
			// dark oak frame, birch boards, deepslate tile roof with cherry fascia
			new Pal(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB,
					Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.BIRCH_PLANKS, Blocks.STRIPPED_BIRCH_LOG, Blocks.CHERRY_DOOR, Blocks.CHERRY_TRAPDOOR),
	};

	private static final Block[] FIREWOOD = {Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.OAK_LOG, Blocks.DARK_OAK_LOG, Blocks.CHERRY_LOG};
	private static final Block[] POTS = {Blocks.POTTED_FERN, Blocks.POTTED_AZALEA, Blocks.POTTED_CORNFLOWER};

	@Override
	public String id() {
		return "annex_tool_shed";
	}

	@Override
	public Family family() {
		return Family.ANNEX;
	}

	@Override
	public int minW() {
		return 5;
	}

	@Override
	public int minD() {
		return 5;
	}

	@Override
	public int minH() {
		return 9;
	}

	@Override
	public int maxW() {
		return 11;
	}

	@Override
	public int maxD() {
		return 10;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		new Build(c, p).run();
	}

	/** One build: the geometry of the box inside the plot and all the writes (mirrored by the seed). */
	private static final class Build {
		final CitadelCanvas c;
		final Plot p;
		final Pal pal;
		final boolean flip;
		final boolean gable;
		final int ws;
		final int w;
		final int d;
		final int h;
		final int ov; // eave strip on each side (0 or 1)
		final int bw; // body width
		final int u0;
		final int u1;
		final int rest; // columns right of the body (its eave strip included)
		final int lean; // width of the open lean-to (0 or 3)
		final int vf;
		final int vb;
		final int dl; // left column of the double door
		final int rise; // height of the gable above the plate level (columns from the wall to the ridge)
		final boolean even;

		Build(CitadelCanvas c, Plot p) {
			this.c = c;
			this.p = p;
			this.w = p.w();
			this.d = p.d();
			this.h = p.h();
			this.pal = PALS[p.hash(2, 0, 0, PALS.length)];
			this.flip = p.hash(1, 0, 0, 2) == 1;
			this.ws = p.hash(3, 0, 0, 3);
			this.ov = w >= 7 ? 1 : 0;
			this.bw = w < 7 ? w : Math.min(7, w >= 10 ? w - 4 : w - 2);
			this.u0 = ov;
			this.u1 = ov + bw - 1;
			this.rest = w - 1 - u1;
			this.lean = rest >= 3 ? 3 : 0;
			this.vf = d >= 8 ? 2 : 1;
			this.vb = d - 1;
			this.dl = u0 + (bw - 2) / 2;
			this.even = bw % 2 == 0;
			this.rise = (bw - 1) / 2;
			boolean room = h - 1 >= ROOF + rise;
			this.gable = room && p.hash(0, 0, 0, 2) == 0;
		}

		// ------------------------------------------------------------------ mirrored writes

		int mu(int u) {
			return flip ? w - 1 - u : u;
		}

		void set(int u, int y, int v, BlockState s) {
			p.set(c, mu(u), y, v, s);
		}

		void put(int u, int y, int v, BlockState s, int layer) {
			p.put(c, mu(u), y, v, s, layer);
		}

		/** A local direction mirrored with the build (east and west swap). */
		Direction fd(Direction dir) {
			if (flip && dir.getAxis() == Direction.Axis.X) {
				return dir.getOpposite();
			}
			return dir;
		}

		BlockState stair(Block blk, Direction facing) {
			return b(blk).setValue(BlockStateProperties.HORIZONTAL_FACING, fd(facing)).setValue(BlockStateProperties.HALF, Half.BOTTOM);
		}

		BlockState slab(Block blk, SlabType t) {
			return b(blk).setValue(BlockStateProperties.SLAB_TYPE, t);
		}

		BlockState log(Block blk, Direction.Axis axis) {
			return b(blk).setValue(BlockStateProperties.AXIS, axis);
		}

		// ------------------------------------------------------------------ the walls

		boolean wall(int u, int v) {
			boolean inV = v >= vf && v <= vb;
			boolean inU = u >= u0 && u <= u1;
			return (inV && (u == u0 || u == u1)) || (inU && (v == vf || v == vb));
		}

		/** The side-wall window row (the left wall) and the cells around it that stay plain boards. */
		int midV() {
			return vf + 1 + (vb - vf - 1) / 2;
		}

		boolean isDoorCol(int u) {
			return u == dl || u == dl + 1;
		}

		/** Front wall columns that carry a window: the odd ones, counted from the left corner, when they are not the door. */
		boolean frontWindow(int u) {
			return u > u0 && u < u1 && !isDoorCol(u) && (u - u0) % 2 == 1;
		}

		BlockState rubble(int u, int y, int v) {
			return switch (p.hash(u, y, v, 5)) {
				case 0 -> b(Blocks.MOSSY_COBBLESTONE);
				case 1 -> b(Blocks.COBBLED_DEEPSLATE);
				case 2 -> b(Blocks.STONE_BRICKS);
				default -> b(Blocks.COBBLESTONE);
			};
		}

		void run() {
			plinthAndFloor();
			walls();
			gableFill();
			roof();
			porch();
			interior();
			leftEave();
			rightEave();
			if (lean > 0) {
				leanTo();
			}
			yard();
		}

		/** [palette] the plinth: cobble, mossy cobble, cobbled deepslate and stone brick by hash, two blocks high; the floor inside is planks. */
		void plinthAndFloor() {
			for (int u = u0; u <= u1; u++) {
				for (int v = vf; v <= vb; v++) {
					if (wall(u, v)) {
						set(u, 0, v, rubble(u, 0, v));
					} else {
						set(u, 0, v, p.hash(u, 0, v, 4) == 0 ? b(pal.panelAlt()) : b(pal.panel()));
					}
				}
			}
		}

		/**
		 * [depth] posts at the corners and every third cell on the side and back walls, vertical boards
		 * between, a stripped plate round the top, a stone plinth under them. The front wall has a double door
		 * and windows over the sill, the left wall one window in the middle.
		 */
		void walls() {
			int mv = midV();
			for (int u = u0; u <= u1; u++) {
				for (int v = vf; v <= vb; v++) {
					if (!wall(u, v)) {
						continue;
					}
					boolean side = u == u0 || u == u1;
					boolean front = v == vf;
					boolean corner = side && (v == vf || v == vb);
					boolean stud = !corner && !front && (side ? (v - vf) % 3 == 0 && Math.abs(v - mv) > 1 : (u - u0) % 3 == 0);
					boolean door = front && isDoorCol(u);
					boolean window = front ? frontWindow(u) : u == u0 && v == mv;
					set(u, 1, v, rubble(u, 1, v));
					for (int y = 2; y <= PLATE; y++) {
						BlockState st;
						if (y == PLATE) {
							st = corner || stud ? log(pal.frame(), Direction.Axis.Y) : log(pal.plate(), side ? Direction.Axis.Z : Direction.Axis.X);
						} else if (corner || stud) {
							st = log(pal.frame(), Direction.Axis.Y);
						} else if (window) {
							st = b(Blocks.GLASS_PANE);
							st = front ? paneAlong(st, true) : paneAlong(st, false);
						} else {
							st = p.hash(u, y, v, 5) == 0 ? b(pal.panelAlt()) : b(pal.panel());
						}
						set(u, y, v, st);
					}
					if (door) {
						// a 2-wide double door; the third block of the doorway stays free under the plate
						boolean left = u == dl;
						DoorHingeSide hinge = (left != flip) ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
						for (int y = 1; y <= 2; y++) {
							set(u, y, v, b(pal.door()).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
									.setValue(BlockStateProperties.DOOR_HINGE, hinge)
									.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, y == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER));
						}
						set(u, 3, v, CitadelCanvas.AIR);
						set(u, PLATE, v, log(pal.plate(), Direction.Axis.X)); // lintel
					}
				}
			}
			// shutters on the left wall window, flat against the wall, outside
			if (ov == 1) {
				for (int dv : new int[] {-1, 1}) {
					for (int y = 2; y <= 3; y++) {
						put(u0 - 1, y, mv + dv, shutter(Direction.WEST), CitadelCanvas.DECOR);
					}
				}
			}
		}

		BlockState paneAlong(BlockState pane, boolean alongU) {
			return pane.setValue(BlockStateProperties.EAST, alongU).setValue(BlockStateProperties.WEST, alongU)
					.setValue(BlockStateProperties.NORTH, !alongU).setValue(BlockStateProperties.SOUTH, !alongU);
		}

		/** An open trapdoor lying flat against the wall behind it ({@code out} = the way it faces). */
		BlockState shutter(Direction out) {
			return b(pal.trapdoor()).setValue(BlockStateProperties.HORIZONTAL_FACING, fd(out)).setValue(BlockStateProperties.OPEN, true)
					.setValue(BlockStateProperties.HALF, Half.BOTTOM);
		}

		/** Height of the roof over a wall column. */
		int roofAt(int u, int v) {
			if (gable) {
				return ROOF + e(u);
			}
			int j = Math.min(v - vf, 2 * (h - 6));
			return ROOF + j / 2;
		}

		/** Distance of body column u from the nearest side wall (0 at the walls). */
		int e(int u) {
			return Math.min(u - u0, u1 - u);
		}

		/** [depth] the triangle (gable) or staircase (single pitch) of boards between the plate and the roof, with a vent pane. */
		void gableFill() {
			for (int u = u0; u <= u1; u++) {
				for (int v = vf; v <= vb; v++) {
					if (!wall(u, v)) {
						continue;
					}
					boolean side = u == u0 || u == u1;
					boolean corner = side && (v == vf || v == vb);
					int top = roofAt(u, v);
					boolean gableEnd = gable && !side;
					boolean pitchWall = !gable;
					if (!gableEnd && !pitchWall) {
						continue; // gable: the side walls end at the plate, the stairs sit on them
					}
					for (int y = PLATE + 1; y < top; y++) {
						BlockState st;
						boolean center = !even && u - u0 == rise;
						if (corner || (u - u0) % 3 == 0 && !side) {
							st = log(pal.frame(), Direction.Axis.Y);
						} else if (gable && center && y == PLATE + 2 && v == vf) {
							st = b(Blocks.GLASS_PANE); // a vent pane at the heart of the front gable
						} else if (center && gable) {
							st = log(pal.frame(), Direction.Axis.Y);
						} else {
							st = p.hash(u, y, v, 5) == 0 ? b(pal.panelAlt()) : b(pal.panel());
						}
						set(u, y, v, st);
					}
				}
			}
		}

		// ------------------------------------------------------------------ the roof

		BlockState roofStair(Block normal, Direction facing, int u, int y, int v) {
			Block blk = normal;
			if (normal == pal.roofStairs() && pal.altStairs() != null && p.hash(u, y, v, 6) == 0) {
				blk = pal.altStairs();
			}
			return stair(blk, facing);
		}

		/**
		 * [finish] the roof, seen from above. Gable: stairs climb from the eave strip on each side to a
		 * slab ridge (two facing stairs on an even width), the eave row at the front and the side strips in trim
		 * stairs. Single pitch: slabs climb half a block a row from a stair eave at the front, the side strips
		 * stay flat at the plate level as a trim ledge.
		 */
		void roof() {
			int cu0 = u0 - ov;
			int cu1 = u1 + ov;
			if (gable) {
				for (int v = vf - 1; v <= vb; v++) {
					for (int u = cu0; u <= cu1; u++) {
						boolean left;
						int y;
						boolean center = false;
						if (u < u0) {
							y = PLATE;
							left = true;
						} else if (u > u1) {
							y = PLATE;
							left = false;
						} else {
							y = ROOF + e(u);
							left = even ? u - u0 < bw / 2 : u - u0 < rise;
							center = !even && u - u0 == rise;
						}
						boolean fascia = v == vf - 1 || u < u0 || u > u1;
						if (center) {
							set(u, y, v, slab(pal.trimSlab(), SlabType.BOTTOM));
						} else {
							Block mat = fascia ? pal.trimStairs() : pal.roofStairs();
							set(u, y, v, fascia ? stair(mat, left ? Direction.EAST : Direction.WEST) : roofStair(mat, left ? Direction.EAST : Direction.WEST, u, y, v));
						}
					}
				}
			} else {
				for (int u = cu0; u <= cu1; u++) {
					set(u, PLATE, vf - 1, stair(pal.trimStairs(), Direction.SOUTH));
				}
				for (int v = vf; v <= vb; v++) {
					int j = Math.min(v - vf, 2 * (h - 6));
					int y = ROOF + j / 2;
					boolean top = j % 2 == 1;
					for (int u = cu0; u <= cu1; u++) {
						if (u < u0 || u > u1) {
							set(u, PLATE, v, slab(pal.trimSlab(), SlabType.BOTTOM)); // flat ledge at the plate level
							continue;
						}
						SlabType t = !top ? SlabType.BOTTOM : wall(u, v) ? SlabType.DOUBLE : SlabType.TOP;
						boolean edge = v == vb || u == u0 || u == u1;
						Block mat = edge ? pal.trimSlab() : pal.roofSlab();
						set(u, y, v, slab(mat, t));
					}
				}
				for (int u = cu0; u <= cu1; u++) {
					if (u < u0 || u > u1) {
						set(u, PLATE, vf - 1, slab(pal.trimSlab(), SlabType.BOTTOM));
					}
				}
			}
		}

		// ------------------------------------------------------------------ the front

		/** The stoop (two stone steps under the hood), two posts under the front eave, pots, a rain barrel and the barrel with the lantern. */
		void porch() {
			int vs = vf - 1;
			// a stone slab apron when the yard is two deep
			if (vf == 2) {
				for (int u = dl; u <= dl + 1; u++) {
					set(u, 0, 0, slab2(u));
				}
			}
			for (int u = dl; u <= dl + 1; u++) {
				set(u, 0, vs, b(Blocks.STONE_BRICK_STAIRS).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH).setValue(BlockStateProperties.HALF, Half.BOTTOM));
			}
			// hood posts, up to the roof edge
			for (int u : new int[] {u0, u1}) {
				set(u, 0, vs, rubble(u, 0, vs));
				for (int y = 1; y <= (gable ? PLATE : PLATE - 1); y++) {
					set(u, y, vs, log(pal.frame(), Direction.Axis.Y));
				}
			}
			for (int u = u0 + 1; u < u1; u++) {
				if (isDoorCol(u)) {
					continue;
				}
				if (frontWindow(u)) {
					set(u, 0, vs, b(POTS[p.hash(u, 0, vs, POTS.length)]));
				} else {
					set(u, 0, vs, barrel());
				}
			}
			// a lantern hangs in front of the door from the front eave (gable: the single pitch has its eave too low)
			if (gable) {
				int top = ROOF + e(dl);
				for (int y = PLATE + 1; y < top; y++) {
					put(dl, y, vs, b(Blocks.IRON_CHAIN), CitadelCanvas.HANGING);
				}
				put(dl, PLATE, vs, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
			}
		}

		BlockState slab2(int u) {
			return slab(p.hash(u, 0, 0, 2) == 0 ? Blocks.STONE_BRICK_SLAB : Blocks.COBBLESTONE_SLAB, SlabType.BOTTOM);
		}

		BlockState rainBarrel() {
			return b(Blocks.WATER_CAULDRON).setValue(LayeredCauldronBlock.LEVEL, 3);
		}

		// ------------------------------------------------------------------ inside

		/** Work wall, storage and a lantern: nothing taller than a block, an aisle of at least 2 from the door to the bench. */
		void interior() {
			int iu0 = u0 + 1;
			int iu1 = u1 - 1;
			int iw = iu1 - iu0 + 1;
			int bv = vb - 1; // the back row
			int wsIdx = iw / 2;
			for (int k = 0; k < iw; k++) {
				int u = iu0 + k;
				BlockState st;
				if (k == wsIdx) {
					st = switch (ws) {
						case 0 -> b(Blocks.CRAFTING_TABLE);
						case 1 -> b(Blocks.SMITHING_TABLE);
						default -> b(Blocks.GRINDSTONE).setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
								.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
					};
				} else if (k < wsIdx) {
					st = (wsIdx - k) % 2 == 1 ? chest(Direction.NORTH) : barrel();
				} else {
					st = (k - wsIdx) % 2 == 1 ? barrel() : chest(Direction.NORTH);
				}
				set(u, 1, bv, st);
				// tool hooks on the back wall above them (no collision)
				set(u, 3, bv, b(Blocks.TRIPWIRE_HOOK).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
			}
			// storage along the left wall, starting past the door row
			for (int v = vf + 2; v <= vb - 2; v++) {
				set(iu0, 1, v, (v - vf) % 2 == 0 ? barrel() : chest(Direction.EAST));
			}
			// the lantern hangs over the aisle from the roof (the single pitch rises, so its chain is longer there)
			int lu = Math.max(iu0 + 1, u0 + (bw / 2));
			if (lu > iu1) {
				lu = iu1;
			}
			int lv = Math.max(vf + 1, Math.min(vf + 2, vb - 2));
			if (!gable && Math.min(lv - vf, 2 * (h - 6)) % 2 == 1) {
				lv--; // under a top slab the chain would not reach: take the row before it
			}
			if (lv > vf) {
				int top = roofAt(lu, lv);
				for (int y = PLATE + 1; y < top; y++) {
					put(lu, y, lv, b(Blocks.IRON_CHAIN), CitadelCanvas.HANGING);
				}
				put(lu, PLATE, lv, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
			}
		}

		BlockState chest(Direction facing) {
			return b(Blocks.CHEST).setValue(BlockStateProperties.HORIZONTAL_FACING, fd(facing));
		}

		BlockState barrel() {
			return b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
		}

		// ------------------------------------------------------------------ the eaves

		/** Under the left eave: barrels, hay, a chopping block and ferns along the side wall, by seed (nothing hollow: a cauldron or composter under a roof reads as a sealed floor). */
		void leftEave() {
			if (ov == 0) {
				return;
			}
			int off = p.hash(7, 0, 0, 5);
			for (int v = vf + 1; v <= vb; v++) {
				if (v == midV()) {
					continue; // under the window
				}
				BlockState st = switch ((v - vf - 1 + off) % 5) {
					case 0 -> b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
					case 1 -> b(Blocks.HAY_BLOCK);
					case 2 -> b(Blocks.FERN);
					case 3 -> log(Blocks.OAK_LOG, Direction.Axis.Y); // an upright chopping block
					default -> b(Blocks.MOSS_CARPET);
				};
				set(u0 - 1, 0, v, st);
			}
		}

		/** Under the right eave: firewood stacked against the wall up to the eave (four high, so no stack has a floor on top). */
		void rightEave() {
			if (ov == 0) {
				return;
			}
			int cols = lean > 0 ? 2 : 1;
			for (int u = u1 + 1; u < u1 + 1 + cols; u++) {
				for (int v = vf; v <= vb - 1; v++) {
					for (int y = 0; y <= PLATE - 1; y++) {
						Block lg = FIREWOOD[p.hash(u, y, v, FIREWOOD.length)];
						// logs lie along the wall (z) on the first column, across (x) on the second
						Direction.Axis ax = (u - u1) % 2 == 1 ? Direction.Axis.Z : Direction.Axis.X;
						set(u, y, v, log(lg, ax));
					}
				}
			}
		}

		/** The open lean-to: a flat roof at the plate level on posts, a closed back, the stack two deep and a chopping stump. */
		void leanTo() {
			int ua = u1 + 2;
			int ub = u1 + lean;
			for (int v = vf - 1; v <= vb; v++) {
				for (int u = ua; u <= ub; u++) {
					boolean edge = v == vf - 1 || v == vb || u == ub;
					set(u, PLATE, v, slab(edge ? pal.trimSlab() : pal.roofSlab(), SlabType.BOTTOM));
				}
			}
			// the back is closed with boards, a plinth under them
			for (int u = u1 + 1; u <= ub; u++) {
				set(u, 0, vb, rubble(u, 0, vb));
				set(u, 1, vb, rubble(u, 1, vb));
				for (int y = 2; y <= 3; y++) {
					set(u, y, vb, p.hash(u, y, vb, 4) == 0 ? b(pal.panelAlt()) : b(pal.panel()));
				}
			}
			// posts on the open side
			for (int v = vf - 1; v <= vb; v++) {
				if (v == vf - 1 || v == vb || (v - vf) % 3 == 0) {
					for (int y = 0; y <= 3; y++) {
						set(ub, y, v, log(pal.frame(), Direction.Axis.Y));
					}
				}
			}
			// the chopping stump in the free column
			int sv = vf + (vb - vf) / 2;
			if ((sv - vf) % 3 == 0) {
				sv++;
			}
			set(ub, 0, sv, log(Blocks.OAK_LOG, Direction.Axis.Y));
		}

		// ------------------------------------------------------------------ yard

		/** A two-deep yard: composter and hay by the path. */
		void yard() {
			if (vf < 2) {
				return;
			}
			set(u0, 0, 0, b(Blocks.COMPOSTER).setValue(BlockStateProperties.LEVEL_COMPOSTER, 3));
			set(u1, 0, 0, b(Blocks.HAY_BLOCK));
			if (u1 - 1 > dl + 1) {
				set(u1 - 1, 0, 0, rainBarrel());
			}
		}
	}
}
