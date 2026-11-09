package dev.t3craft.hq.citadel.barrio;

import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqStyle;
import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Lot;
import dev.t3craft.hq.RingCampusGeometry.Room;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.hq.SignTags;
import dev.t3craft.hq.citadel.CitadelBarrios;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelCanvas.Mats;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;

/**
 * The gatehouse of a barrio at scale 2 and up (struct/barrio-gatehouse, round 2 struct/barrio-gatehouse2), and the arm of
 * the gate in front of it.
 *
 * <p><b>The building.</b> The gate room (15 x 10 at ring 1, 19 x 14 at rings 2 and 3) is split across its width into
 * three: a 5-wide passage down the middle, and on each side a block of {@code sw = (W - 5) / 2} cells. Each side block is
 * a tower at the hub end (square, 13 high, pyramid roof, a pole with a flag in the project's colour on top) and a guard
 * room at the courtyard end (5 high inside, hipped roof, a doorway on the passage).
 *
 * <p><b>The wall-walk.</b> Over the passage, at the depth of the towers, a 5-deep block joins the two towers: the
 * stepped arch below (3, 4, 5 high across), at y = 9 a deck with a parapet and merlons on both edges (the hub edge carries a corbel course and
 * three name plaques on a dark board, the courtyard edge looks over the lane). Each tower is hollow in a 3 x 3 shaft
 * beside the passage: a spiral stair of stone-brick stairs and landings (one step per cell, a landing at each corner,
 * 4 blocks between laps) climbs from a doorway on the passage to a doorway onto the deck, so the whole thing is walkable.
 * Between the guard rooms the passage is an open lane that ends on a screen wall (arch ring, plaque towards the courtyard).
 *
 * <p><b>Round 3 (struct/barrio-gatehouse3).</b> Dark pilasters back on the hub face of every tower (also at ring 1); the banner
 * hangs one block in front of the face from a jutting cherry beam; stepped deepslate plinth at the foot of the towers; the
 * vault has stone-brick ribs and a raised iron-bar portcullis; rings 2 and 3 get a stone wall-walk with a dark belt and caps.
 *
 * <p><b>Identification.</b> From the hub: on each tower a flat banner of the project's wool (the colour is
 * {@link HqStyle#envColor(HqLayout.Module)}) 3 x 8 (5 x 8 at ring 2+) hung from a cherry beam between two dark pilasters,
 * with the project's initial in 3 x 5 blocks of white or black wool on it (whichever contrasts), and the plaques on the board.
 *
 * <p><b>Uses.</b> The left guard room is the gate post (chests, barrels, a counter with the register); the right one is
 * the map room (a cartography table, shelves, a lectern). Both keep a free aisle from the door to the far wall.
 *
 * <p><b>The arm</b> (from the hub face of the gate to the end of the barrio's lot cluster, 7 wide: the width of the
 * corridor plus a lamp lane each side): a three-tone pavement (the courtyard's pink floor, deepslate tile, stone bricks)
 * with a dashed centre line in the project's colour that fades into stone at the end (the corridor of
 * {@code CitadelPlacement} takes over from there), lamp posts every 4 rows, benches and planters in between, and a
 * signpost with one board per hall. The bays beside the arm are {@code CitadelPlacement#forecourt}'s.
 *
 * <p>Palette, four materials: deepslate (plinth, posts, roofs, floor), stone bricks (walls, merlons), cherry (beams,
 * eaves, fences, benches) and the project's wool. The gate no longer uses the barrio's {@link Mats}.
 */
final class BarrioGateBig {
	private BarrioGateBig() {
	}

	/** Half width of the passage: 5 wide. */
	private static final int HALF = 2;
	/** Height of the walls of a guard room (the roof layer, which is also its ceiling, sits above). */
	private static final int ANNEX_TOP = 5;
	/** Height of the walls of a tower. */
	private static final int TOWER_TOP = 13;
	/** The deck of the wall-walk (block level; one stands at DECK + 1). */
	private static final int DECK = 9;
	/** Height of the sign board (plaques). */
	private static final int PLAQUE_Y = 8;
	/** Temporary: walk spots inside the guard rooms and on the stair tops, to read their reachability in the report. */
	private static final boolean TEST_SPOTS = false;

	private static final BlockState STONE = b(Blocks.STONE_BRICKS);
	private static final BlockState POST = b(Blocks.POLISHED_DEEPSLATE);
	private static final Block ROOF = Blocks.DEEPSLATE_TILE_STAIRS;
	/** The shaft's ring in the order a climber walks it, as {@code {a, b}} (a along the depth, b towards the passage): entry first. */
	private static final int[][] RING = {{0, 2}, {1, 2}, {2, 2}, {2, 1}, {2, 0}, {1, 0}, {0, 0}, {0, 1}};

	/** Whether the gate room is big enough for this design. */
	static boolean fits(Room r) {
		return r.w() >= 15 && r.d() >= 10;
	}

	static void build(CitadelCanvas c, Lot l, HqLayout.Module lead, Mats mats) {
		new Gate(c, l, lead).build();
		arm(c, l, lead);
	}

	private static BlockState log(Direction.Axis ax) {
		return b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, ax);
	}

	// ------------------------------------------------------------------ the building

	private static final class Gate {
		final CitadelCanvas c;
		final Lot l;
		final HqLayout.Module lead;
		final Room r;
		final int W;
		final int D;
		final int mid;
		/** Width of a side block (a tower is {@code sw x sw}), first row of the towers, rows between the tower wall and the shaft. */
		final int sw;
		final int tu;
		final int rowsOff;
		final Direction back;
		final Direction side;
		final DyeColor col;
		final BlockState wool;
		final BlockState letter;
		final Map<Integer, BlockState> stepsL;
		final Map<Integer, BlockState> stepsR;
		/** Ring 1: dark slopes under a cherry eave; rings 2 and 3: cherry slopes under a dark eave (each ring keeps its own look). */
		final Block roofMat;
		final Block eaveMat;

		Gate(CitadelCanvas c, Lot l, HqLayout.Module lead) {
			this.c = c;
			this.l = l;
			this.lead = lead;
			this.r = l.gate();
			this.W = r.w();
			this.D = r.d();
			this.mid = r.c();
			this.sw = (W - (2 * HALF + 1)) / 2;
			this.tu = D - sw;
			this.rowsOff = (sw - 5) / 2;
			this.back = r.back();
			this.side = r.side();
			this.col = HqStyle.envColor(lead);
			this.wool = b(Blocks.WOOL.pick(col));
			// the initial: white wool on the dark colours, black on the light ones
			this.letter = b(Blocks.WOOL.pick(dark(col) ? DyeColor.WHITE : DyeColor.BLACK));
			this.roofMat = l.ring() >= 2 ? Blocks.CHERRY_STAIRS : ROOF;
			this.eaveMat = l.ring() >= 2 ? ROOF : Blocks.CHERRY_STAIRS;
			this.stepsL = buildSteps(true);
			this.stepsR = buildSteps(false);
			this.stoneWalk = true;
		}

		/** The wall-walk is stone with a dark belt and dark caps (all rings), so it does not melt into the roofs. */
		final boolean stoneWalk;

		/** The belt course on the edges of the deck. */
		BlockState walkBelt() {
			return stoneWalk ? POST : log(side.getAxis());
		}

		/** The parapet (one high) on the edges of the deck. */
		BlockState parapet() {
			return stoneWalk ? STONE : PLANKS;
		}

		/** The merlon: two high, the second block is the cap. */
		BlockState merlon(boolean cap) {
			return stoneWalk ? (cap ? POST : STONE) : PLANKS;
		}

		void build() {
			for (int u = 0; u < D; u++) {
				for (int v = 0; v < W; v++) {
					int dv = v - mid;
					if (Math.abs(dv) <= HALF) {
						if (u >= tu) {
							bridgeCol(u, v, dv);
						} else if (u == 0) {
							screenCol(u, v, dv);
						} else {
							laneCol(u, v, dv);
						}
					} else if (u >= tu) {
						towerCol(u, v);
					} else {
						annexCol(u, v);
					}
				}
			}
			for (int s = 0; s < 2; s++) {
				int v0 = s == 0 ? 0 : W - sw;
				pyramid(new Room(r.cell(0, v0, 0), back, side, sw, tu), ANNEX_TOP + 1, false, s);
				Room tower = new Room(r.cell(tu, v0, 0), back, side, sw, D - tu);
				pyramid(tower, TOWER_TOP + 1, true, s);
				cornice(tower, TOWER_TOP - 1);
			}
			corbel();
			bannerFront();
			plinth();
			portcullis();
			buttresses();
			passageDressing();
			boards();
			guardPost();
			mapRoom();
			if (TEST_SPOTS) {
				testSpots();
			}
		}

		// -------------------------------------------------------------- blocks

		boolean insideGate(BlockPos p) {
			int dx = p.getX() - r.origin().getX();
			int dz = p.getZ() - r.origin().getZ();
			int gu = dx * back.getStepX() + dz * back.getStepZ();
			int gv = dx * side.getStepX() + dz * side.getStepZ();
			return gu >= 0 && gu < D && gv >= 0 && gv < W;
		}

		/** The stepped arch: deepslate-tile stairs inside, a light stone-brick ring on the faces so the opening reads from afar. */
		BlockState archStair(int dv, boolean face) {
			Direction away = dv < 0 ? side.getOpposite() : side;
			return BarrioKit.stair(face ? Blocks.STONE_BRICK_STAIRS : ROOF, away, StairsShape.STRAIGHT, Half.TOP);
		}

		BlockState keystone(boolean face) {
			return face ? b(Blocks.CHISELED_STONE_BRICKS) : log(back.getAxis());
		}

		/** The light stone of the towers: bricks, a few cracked ones. */
		BlockState towerStone(int u, int v, int y) {
			int h = Math.floorMod(u * 7 + v * 13 + y * 5, 11);
			return h == 0 ? b(Blocks.CRACKED_STONE_BRICKS) : STONE;
		}

		// -------------------------------------------------------------- passage

		BlockState passageFloor(int u, int dv) {
			int a = Math.abs(dv);
			if (a <= 1) {
				return a == 0 && u % 4 == 0 ? b(Blocks.DYED_TERRACOTTA.pick(col)) : c.floor();
			}
			return u % 2 == 0 ? STONE : FOOT;
		}

		/** The open lane between the guard rooms. */
		void laneCol(int u, int v, int dv) {
			c.set(r.cell(u, v, 0), passageFloor(u, dv));
			for (int y = 1; y <= TOWER_TOP; y++) {
				c.set(r.cell(u, v, y), AIR);
			}
		}

		/** The screen wall on the courtyard end of the lane: the arch ring, a wall to y = 8, a cherry fascia and merlons. */
		void screenCol(int u, int v, int dv) {
			int a = Math.abs(dv);
			c.set(r.cell(u, v, 0), passageFloor(u, dv));
			int clear = 6 - a;
			for (int y = 1; y <= clear; y++) {
				c.set(r.cell(u, v, y), AIR);
			}
			c.set(r.cell(u, v, clear + 1), a == 0 ? keystone(true) : archStair(dv, true));
			for (int y = clear + 2; y < DECK; y++) {
				c.set(r.cell(u, v, y), STONE);
			}
			c.set(r.cell(u, v, DECK), walkBelt());
			c.set(r.cell(u, v, DECK + 1), dv % 2 == 0 ? parapet() : AIR);
			for (int y = DECK + 2; y <= TOWER_TOP; y++) {
				c.set(r.cell(u, v, y), AIR);
			}
		}

		/** The bridge over the passage at the depth of the towers: arch, mass, deck, parapet with merlons on both edges. */
		void bridgeCol(int u, int v, int dv) {
			int a = Math.abs(dv);
			int row = u - tu;
			boolean hub = row == sw - 1;
			boolean edge = row == 0 || hub;
			c.set(r.cell(u, v, 0), passageFloor(u, dv));
			int clear = 6 - a;
			for (int y = 1; y <= clear; y++) {
				c.set(r.cell(u, v, y), AIR);
			}
			// the vault is a row of stone-brick ribs (the faces and every other row) between dark rows
			boolean rib = edge || row % 2 == 0;
			c.set(r.cell(u, v, clear + 1), a == 0 ? keystone(rib) : archStair(dv, rib));
			for (int y = clear + 2; y < DECK; y++) {
				BlockState s = STONE;
				if (hub && y == PLAQUE_Y) {
					s = a == 2 ? wool : FOOT;
				}
				c.set(r.cell(u, v, y), s);
			}
			c.set(r.cell(u, v, DECK), edge ? walkBelt() : dv == 0 ? PLANKS : FOOT);
			// the battlement: a parapet and merlons two high on every other cell (cherry at ring 1; stone with dark belt and caps at rings 2-3)
			c.set(r.cell(u, v, DECK + 1), edge ? parapet() : AIR);
			c.set(r.cell(u, v, DECK + 2), edge && dv % 2 == 0 ? merlon(false) : AIR);
			c.set(r.cell(u, v, DECK + 3), edge && dv % 2 == 0 ? merlon(true) : AIR);
			for (int y = DECK + 4; y <= TOWER_TOP; y++) {
				c.set(r.cell(u, v, y), AIR);
			}
		}

		/** A course of upside-down stairs under the parapet, one block out on the hub face and on the lane side of the bridge. */
		void corbel() {
			for (int dv = -HALF; dv <= HALF; dv++) {
				c.set(r.cell(D, mid + dv, DECK), BarrioKit.stair(Blocks.STONE_BRICK_STAIRS, back.getOpposite(), StairsShape.STRAIGHT, Half.TOP));
				c.set(r.cell(tu - 1, mid + dv, DECK), BarrioKit.stair(Blocks.STONE_BRICK_STAIRS, back, StairsShape.STRAIGHT, Half.TOP));
			}
		}

		/**
		 * The raised portcullis: iron bars hanging from the vault flush with the hub face, so it is read from the street (the name sign hangs behind it) (three high in the middle, two at
		 * one in from the sides and one at the sides, following the arch), always above head height (at least 3 free below).
		 */
		void portcullis() {
			int u = D - 1;
			for (int dv = -HALF; dv <= HALF; dv++) {
				int a = Math.abs(dv);
				int clear = 6 - a;
				int n = a == HALF ? 1 : a == 1 ? 2 : 3;
				for (int y = clear - n + 1; y <= clear; y++) {
					c.set(r.cell(u, mid + dv, y), b(Blocks.IRON_BARS));
				}
			}
		}

		/**
		 * The foot of the towers: a step of deepslate-tile stairs one block out on the hub face and on the outer face (not under the
		 * buttresses), a slab at the corner, so the base reads as a plinth.
		 */
		void plinth() {
			for (int s = 0; s < 2; s++) {
				boolean left = s == 0;
				int v0 = left ? 0 : W - sw;
				for (int tv = 0; tv < sw; tv++) {
					c.set(r.cell(D, v0 + tv, 1), BarrioKit.stair(ROOF, back.getOpposite()));
				}
				int v = left ? -1 : W;
				Direction towardsWall = left ? side : side.getOpposite();
				for (int u = tu; u < D; u++) {
					if (u == tu + 1 || u == tu + sw - 2) {
						continue;
					}
					c.set(r.cell(u, v, 1), BarrioKit.stair(ROOF, towardsWall));
				}
				c.set(r.cell(D, v, 1), b(Blocks.DEEPSLATE_TILE_SLAB));
			}
		}

		/** Two piers one block out on the outer face of each tower, either side of its window: 8 high, a sloped cap at the belt. */
		void buttresses() {
			for (int s = 0; s < 2; s++) {
				boolean left = s == 0;
				int v = left ? -1 : W;
				Direction towardsWall = left ? side : side.getOpposite();
				for (int a : new int[] {1, sw - 2}) {
					int u = tu + a;
					for (int y = 1; y < DECK; y++) {
						c.set(r.cell(u, v, y), y <= 2 ? FOOT : STONE);
					}
					c.set(r.cell(u, v, DECK), BarrioKit.stair(Blocks.STONE_BRICK_STAIRS, towardsWall));
				}
			}
		}

		/** Lanterns under the vault and on the merlons, banners on the tower walls, the hanging sign of the project at the hub end. */
		void passageDressing() {
			// (the portcullis hangs at D - 1 and the sign at D - 2, so the second lantern of the wider gates is at D - 4)
			for (int u : sw >= 7 ? new int[] {tu + 1, D - 4} : new int[] {tu + 1}) {
				c.set(r.cell(u, mid, 6), BarrioKit.lantern(true));
			}
			BlockPos sign = r.cell(D - 2, mid, 6);
			BlockState hs = Blocks.CHERRY_HANGING_SIGN.defaultBlockState().setValue(CeilingHangingSignBlock.ROTATION, CitadelBarrios.rotation(back))
					.setValue(CeilingHangingSignBlock.ATTACHED, false);
			c.put(sign.getX(), sign.getY(), sign.getZ(), hs, HANGING);
			c.data.put(sign.asLong(), BarrioGate.barrioSign(c, lead).signTag());
			c.fixtures.add(sign.asLong());
			c.set(r.cell(D - 1, mid, DECK + 4), BarrioKit.lantern(false));
			c.set(r.cell(tu, mid, DECK + 4), BarrioKit.lantern(false));
			// a lantern hanging in the street doorway of each tower from its stone lintel, so the door reads from afar
			for (int dv = -HALF - 1; dv <= HALF + 1; dv += 2 * (HALF + 1)) {
				c.set(r.cell(tu + 1 + rowsOff, mid + dv, 3), BarrioKit.lantern(true));
			}
			// banners in the project's colour on the tower walls beside the passage (the doorway row is left clear)
			for (int a : new int[] {2 + rowsOff, 3 + rowsOff}) {
				for (int dv = -HALF; dv <= HALF; dv += 2 * HALF) {
					Direction f = dv < 0 ? side : side.getOpposite();
					BlockPos p = r.cell(tu + a, mid + dv, 3);
					c.put(p.getX(), p.getY(), p.getZ(), b(Blocks.WALL_BANNER.pick(col)).setValue(BlockStateProperties.HORIZONTAL_FACING, f), DECOR);
				}
			}
		}

		// -------------------------------------------------------------- towers

		void towerCol(int u, int v) {
			boolean left = v < mid;
			int tv = left ? v : v - (W - sw);
			int a = u - tu;
			boolean edgeU = a == 0 || a == sw - 1;
			boolean edgeV = tv == 0 || tv == sw - 1;
			boolean corner = edgeU && edgeV;
			boolean perimeter = edgeU || edgeV;
			boolean hubFace = a == sw - 1;
			boolean passSide = left ? tv == sw - 1 : tv == 0;
			int sa = a - (1 + rowsOff);
			int sb = left ? tv - (sw - 4) : 3 - tv;
			boolean shaft = !perimeter && sa >= 0 && sa <= 2 && sb >= 0 && sb <= 2;
			c.set(r.cell(u, v, 0), FOOT);
			for (int y = 1; y <= TOWER_TOP; y++) {
				BlockState s;
				if (shaft) {
					s = shaftBlock(sa, sb, y, left);
				} else if (!perimeter) {
					s = STONE;
				} else if (corner) {
					s = POST;
				} else if (passSide && ((a == 1 + rowsOff && y <= 3) || (a == 2 + rowsOff && (y == DECK + 1 || y == DECK + 2)))) {
					s = AIR;
				} else if (y <= 3) {
					s = FOOT;
				} else if (y == DECK) {
					s = log(edgeU ? side.getAxis() : back.getAxis());
				} else if (!passSide && !hubFace && y >= 5 && y <= 8 && (edgeU ? tv == sw / 2 : a == sw / 2)) {
					// a lancet window in the middle of the plain faces: a dark frame (sill and head) around two panes
					s = y == 6 || y == 7 ? b(Blocks.GLASS_PANE) : POST;
				} else {
					s = towerStone(u, v, y);
				}
				c.set(r.cell(u, v, y), s);
			}
		}

		/**
		 * The banner of a tower: hung one block in front of the hub face (row {@code D}), between the two dark pilasters of the
		 * corners, from a cherry beam that juts out over the whole face at y = 12. The face behind it stays stone and the pilasters
		 * stay flush, so the tower keeps its relief.
		 */
		void bannerFront() {
			for (int s = 0; s < 2; s++) {
				int v0 = s == 0 ? 0 : W - sw;
				for (int tv = 0; tv < sw; tv++) {
					// ring 1 has a cherry eave right above: its beam is dark so the top does not stack three pink stripes
					c.set(r.cell(D, v0 + tv, TOWER_TOP - 1), l.ring() >= 2 ? log(side.getAxis()) : POST);
					if (tv >= 1 && tv <= sw - 2) {
						// a stone sill under the banner
						c.set(r.cell(D, v0 + tv, 3), BarrioKit.stair(Blocks.STONE_BRICK_STAIRS, back.getOpposite()));
						for (int y = 4; y <= TOWER_TOP - 2; y++) {
							c.set(r.cell(D, v0 + tv, y), banner(tv, y));
						}
					}
				}
			}
		}

		/** A cell of the banner (tv 1..sw-2, y 4..11). */
		BlockState banner(int tv, int y) {
			if (y >= 10) {
				return wool;
			}
			if (y >= 5) {
				return letterCell(tv, y);
			}
			// the swallow-tail at the foot of the banner
			return tv == (sw - 1) / 2 ? AIR : wool;
		}

		/** A cell of the initial (y 5..9) in 3 x 5 on the banner: the project's letter, or the banner's wool. */
		BlockState letterCell(int tv, int y) {
			int start = 1 + ((sw - 2) - 3) / 2;
			int cx = tv - start;
			int cy = 9 - y;
			if (cx < 0 || cx > 2) {
				return wool;
			}
			// the viewer comes from the hub looking along -back: his left is +side, so columns run towards -v
			String[] g = glyph(initial());
			return g[cy].charAt(2 - cx) == '1' ? letter : wool;
		}

		String initial() {
			String t = lead.label();
			for (int i = 0; i < t.length(); i++) {
				if (Character.isLetterOrDigit(t.charAt(i))) {
					return String.valueOf(Character.toUpperCase(t.charAt(i)));
				}
			}
			return "#";
		}

		/** A row of upside-down stone-brick stairs one block out under the top of a tower: a shadow line (not at the corners). */
		void cornice(Room s, int y) {
			int w = s.w();
			int d = s.d();
			for (int u = -1; u <= d; u++) {
				for (int v = -1; v <= w; v++) {
					boolean uEdge = u == -1 || u == d;
					boolean vEdge = v == -1 || v == w;
					if (uEdge == vEdge) {
						continue;
					}
					BlockPos p = s.cell(u, v, y);
					if (insideGate(p)) {
						continue;
					}
					Direction in = u == -1 ? back : u == d ? back.getOpposite() : v == -1 ? side : side.getOpposite();
					c.set(p, BarrioKit.stair(Blocks.STONE_BRICK_STAIRS, in, StairsShape.STRAIGHT, Half.TOP));
				}
			}
		}

		// -------------------------------------------------------------- the stair of a tower

		static int key(int sa, int sb, int y) {
			return (sa * 3 + sb) * 64 + y;
		}

		Direction dir(int da, int db, boolean left) {
			if (da != 0) {
				return da > 0 ? back : back.getOpposite();
			}
			Direction towardsPassage = left ? side : side.getOpposite();
			return db > 0 ? towardsPassage : towardsPassage.getOpposite();
		}

		/**
		 * The spiral of a tower, as {@code key -> block}. It walks {@link #RING} from the entry cell (flat, the floor): a stair
		 * (facing the way one climbs) on every cell that is not a corner, a landing flush with the top of the stair before it on
		 * every corner, so that a climber always steps up half a block in front of a stair. Four blocks separate two laps; the
		 * last stair is at {@code DECK}, beside the doorway on the deck.
		 */
		Map<Integer, BlockState> buildSteps(boolean left) {
			Map<Integer, BlockState> m = new HashMap<>();
			int s = 0;
			for (int k = 1; k < 200; k++) {
				int idx = k % RING.length;
				int[] cell = RING[idx];
				int[] next = RING[(k + 1) % RING.length];
				if (idx % 2 == 0) {
					m.put(key(cell[0], cell[1], s), STONE);
				} else {
					s++;
					m.put(key(cell[0], cell[1], s), BarrioKit.stair(Blocks.STONE_BRICK_STAIRS, dir(next[0] - cell[0], next[1] - cell[1], left)));
					if (s == DECK) {
						break;
					}
				}
			}
			return m;
		}

		BlockState shaftBlock(int sa, int sb, int y, boolean left) {
			if (sa == 1 && sb == 1) {
				return y <= DECK ? STONE : y == DECK + 1 ? BarrioKit.lantern(false) : AIR;
			}
			BlockState s = (left ? stepsL : stepsR).get(key(sa, sb, y));
			return s != null ? s : AIR;
		}

		/** A cell of the shaft of a tower in world coordinates. */
		BlockPos shaftCell(boolean left, int sa, int sb, int y) {
			int u = tu + 1 + rowsOff + sa;
			int v = left ? (sw - 4) + sb : (W - sw) + (3 - sb);
			return r.cell(u, v, y);
		}

		// -------------------------------------------------------------- guard rooms

		void annexCol(int u, int v) {
			boolean left = v < mid;
			int av = left ? v : v - (W - sw);
			boolean edgeU = u == 0 || u == tu - 1;
			boolean edgeV = av == 0 || av == sw - 1;
			boolean corner = edgeU && edgeV;
			boolean perimeter = edgeU || edgeV;
			boolean passageSide = left ? av == sw - 1 : av == 0;
			boolean door = passageSide && u == (tu - 1) / 2;
			c.set(r.cell(u, v, 0), perimeter ? FOOT : c.floor());
			for (int y = 1; y <= ANNEX_TOP; y++) {
				BlockState s;
				if (!perimeter) {
					s = AIR;
				} else if (door && y <= 3) {
					s = AIR;
				} else if (corner) {
					s = POST;
				} else if (y == 1) {
					s = FOOT;
				} else if (y == ANNEX_TOP) {
					s = log(edgeU && !edgeV ? side.getAxis() : back.getAxis());
				} else if ((y == 2 || y == 3) && !passageSide && ((u == 0 && av == sw / 2) || (edgeV && u == (tu - 1) / 2))) {
					s = b(Blocks.GLASS_PANE);
				} else {
					s = towerStone(u, v, y);
				}
				c.set(r.cell(u, v, y), s);
			}
		}

		void put(int u, int v, int y, BlockState s) {
			c.set(r.cell(u, v, y), s);
		}

		/** The column {@code v} of the interior cell {@code col} of a guard room (0 beside the passage, {@code sw - 3} on the outer wall). */
		int vOf(boolean left, int col) {
			return left ? sw - 2 - col : (W - sw) + 1 + col;
		}

		/** The left guard room: chests and barrels on the outer wall, a counter with the register on the court wall; the row of the door stays free. */
		void guardPost() {
			int rows = tu - 2;
			int cols = sw - 2;
			int aisle = (tu - 1) / 2 - 1;
			Direction in = side;
			for (int i = 0; i < rows; i++) {
				if (i != aisle) {
					put(1 + i, vOf(true, cols - 1), 1, i % 2 == 0 ? chest(in) : b(Blocks.BARREL));
				}
			}
			for (int k = 0; k <= cols - 2; k++) {
				put(1, vOf(true, k), 1, k == cols - 2 ? lectern(back) : b(Blocks.CHERRY_PLANKS));
			}
			hang(1 + aisle, vOf(true, (cols - 1) / 2));
		}

		/** The right guard room, the map room: shelves on the outer wall, a cartography table and a lectern, the aisle free. */
		void mapRoom() {
			int rows = tu - 2;
			int cols = sw - 2;
			int aisle = (tu - 1) / 2 - 1;
			for (int i = 0; i < rows; i++) {
				put(1 + i, vOf(false, cols - 1), 1, b(Blocks.BOOKSHELF));
				put(1 + i, vOf(false, cols - 1), 2, b(Blocks.BOOKSHELF));
			}
			int cm = (cols - 1) / 2;
			put(1, vOf(false, cm), 1, b(Blocks.CARTOGRAPHY_TABLE));
			put(rows, vOf(false, cm), 1, lectern(side.getOpposite()));
			hang(1 + aisle, vOf(false, cm));
		}

		/** A lantern hanging from the ceiling of a guard room. */
		void hang(int u, int v) {
			BlockPos q = r.cell(u, v, ANNEX_TOP);
			c.put(q.getX(), q.getY(), q.getZ(), BarrioKit.lantern(true), HANGING);
		}

		BlockState chest(Direction facing) {
			return b(Blocks.CHEST).setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
		}

		BlockState lectern(Direction facing) {
			return b(Blocks.LECTERN).setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
		}

		/** Temporary: standing spots (anchors) in both guard rooms, at the top stair of both towers and on the deck. */
		void testSpots() {
			int aisle = (tu - 1) / 2;
			int cm = (sw - 3) / 2;
			c.walkSpot(r.cell(aisle, vOf(true, cm), 1), back);
			c.walkSpot(r.cell(aisle, vOf(false, cm), 1), back);
			c.walkSpot(shaftCell(true, 1, 2, DECK + 1), back);
			c.walkSpot(shaftCell(false, 1, 2, DECK + 1), back);
			c.walkSpot(r.cell(tu + sw / 2, mid, DECK + 1), back);
		}

		// -------------------------------------------------------------- roofs

		/** The stair of a hip roof at a cell whose distance to the edge is {@code k}: straight on the slopes, outer corners on the hips. */
		BlockState hip(Room s, Block mat, int u, int v, int k) {
			int w = s.w();
			int d = s.d();
			boolean nv1 = v + 1 == k;
			boolean nv2 = w - v == k;
			boolean nu1 = u + 1 == k;
			boolean nu2 = d - u == k;
			if ((nv1 || nv2) && (nu1 || nu2) && !(nv1 && nv2) && !(nu1 && nu2)) {
				Direction f = nu1 ? s.back() : s.back().getOpposite();
				StairsShape shape = nv1 == nu1 ? StairsShape.OUTER_RIGHT : StairsShape.OUTER_LEFT;
				return BarrioKit.stair(mat, f, shape, Half.BOTTOM);
			}
			Direction f = nv1 ? s.side() : nv2 ? s.side().getOpposite() : nu1 ? s.back() : s.back().getOpposite();
			return BarrioKit.stair(mat, f);
		}

		/**
		 * A pyramid roof over {@code s}: the layer at {@code roofY} (ceiling and cornice), the slopes above, and on top
		 * a finial, or on a tower a pole with a flag in the project's colour. {@code s} is the side block (0 left, 1 right).
		 */
		void pyramid(Room s, int roofY, boolean tower, int sideIdx) {
			int w = s.w();
			int d = s.d();
			int kmax = Math.min((d + 1) / 2, (w + 1) / 2);
			for (int u = 0; u < d; u++) {
				for (int v = 0; v < w; v++) {
					BlockPos p = s.cell(u, v, 0);
					c.set(p.atY(roofY), FOOT);
					int k = Math.min(Math.min(u + 1, d - u), Math.min(v + 1, w - v));
					int y = roofY + k;
					if (k == kmax) {
						c.set(p.atY(y), FOOT);
						boolean centre = d == w && u == (d - 1) / 2 && v == (w - 1) / 2;
						if (tower && centre) {
							flag(p.atY(y), sideIdx);
						} else {
							c.set(p.atY(y + 1), centre ? BarrioKit.finial(Blocks.CHERRY_STAIRS) : BarrioKit.slabOf(roofMat));
						}
					} else {
						c.set(p.atY(y), hip(s, roofMat, u, v, k));
					}
				}
			}
			int midv = (w - 1) / 2;
			for (int k = 2; k <= kmax; k++) {
				c.set(s.cell(k - 1, midv, roofY + k - 1), FOOT);
			}
			eave(s, roofY);
		}

		/** The cherry eave ring outside {@code s}, except where it would run into the rest of the gate. */
		void eave(Room s, int y) {
			int w = s.w();
			int d = s.d();
			for (int u = -1; u <= d; u++) {
				for (int v = -1; v <= w; v++) {
					if (u != -1 && u != d && v != -1 && v != w) {
						continue;
					}
					BlockPos p = s.cell(u, v, y);
					if (insideGate(p)) {
						continue;
					}
					c.set(p, hip(s, eaveMat, u, v, 0));
				}
			}
		}

		/** A pole on top of a tower and a pennant of the project's wool, flying away from the passage. */
		void flag(BlockPos top, int sideIdx) {
			for (int i = 1; i <= 5; i++) {
				c.set(top.above(i), b(Blocks.CHERRY_FENCE));
			}
			Direction out = sideIdx == 0 ? side.getOpposite() : side;
			for (int i = 1; i <= 4; i++) {
				c.set(top.above(5).relative(out, i), wool);
				if (i <= 3) {
					c.set(top.above(4).relative(out, i), wool);
				}
				if (i <= 2) {
					c.set(top.above(3).relative(out, i), wool);
				}
			}
			c.set(top.above(6), BarrioKit.finial(Blocks.CHERRY_STAIRS));
		}

		// -------------------------------------------------------------- the board

		/** The plaques of the board, on the hub face (name in the middle) and a name plaque on the courtyard face. */
		void boards() {
			HqStyle.SignText t = BarrioGate.barrioSign(c, lead);
			String[] name = BarrioGate.wrap(lead.label(), SignTags.LINE);
			plaque(D, mid, back, c.kit.plaqueData(col, name[0], name[1]));
			plaque(D, mid + 1, back, c.kit.plaqueData(col, t.l3(), t.l4()));
			TreeMap<Integer, HqLayout.Module> mods = c.lots.get(l.id());
			int halls = 0;
			boolean leadOffice = false;
			if (mods != null) {
				for (int h = 0; h < RingCampusGeometry.HALLS; h++) {
					if (mods.get(h) != null || mods.get(h + RingCampusGeometry.HALLS) != null) {
						halls++;
					}
				}
				for (HqLayout.Module m : mods.values()) {
					leadOffice |= m.type() == HqLayout.Type.LEAD_OFFICE;
				}
			}
			plaque(D, mid - 1, back, c.kit.plaqueData(col, halls + (halls == 1 ? " hall" : " halls"), leadOffice ? "Lead office" : "Work wing"));
			plaque(-1, mid, back.getOpposite(), c.kit.plaqueData(col, name[0], name[1]));
		}

		void plaque(int u, int v, Direction facing, CompoundTag tag) {
			BlockPos p = r.cell(u, v, PLAQUE_Y);
			c.put(p.getX(), p.getY(), p.getZ(), c.kit.plaque(facing), DECOR);
			c.data.put(p.asLong(), tag);
			c.fixtures.add(p.asLong());
		}
	}

	// ------------------------------------------------------------------ the arm

	/** Half width of the walk the bays of {@code CitadelPlacement#forecourt} leave: the corridor's half width plus the lamp lane. */
	private static int walk(CitadelCanvas c) {
		return c.s(1) + 1;
	}

	private static void arm(CitadelCanvas c, Lot l, HqLayout.Module lead) {
		int g = l.court();
		int walk = walk(c);
		int a0 = g + 1 + l.gateD();
		int a1 = l.reach();
		DyeColor col = HqStyle.envColor(lead);
		for (int a = a0; a <= a1; a++) {
			int n = a - a0;
			for (int bb = -walk; bb <= walk; bb++) {
				BlockPos p = l.at(-a, bb, 0);
				if (!inLot(c, l, p)) {
					continue;
				}
				c.set(p, armFloor(c, n, bb, col, a1 - a));
			}
		}
		apron(c, l, a0, walk);
		flanks(c, l, a0, a1, walk);
		for (int a = a0 + 1; a < a1; a++) {
			int n = a - a0;
			if (n % 4 == 1) {
				lamp(c, l, a, -walk);
				lamp(c, l, a, walk);
			} else if (n % 4 == 3) {
				int s = (n / 4) % 2 == 0 ? 1 : -1;
				bench(c, l, a, s * walk);
				planter(c, l, a, -s * walk);
			}
		}
		signpost(c, l, lead, a1 - 1, walk);
	}

	/** The row in front of the gate's hub face, beyond the walk: a checker of the courtyard's pink floor and calcite instead of the plain grey of the cross. */
	private static void apron(CitadelCanvas c, Lot l, int a0, int walk) {
		int edge = l.court() + 1;
		for (int bb = -edge; bb <= edge; bb++) {
			if (Math.abs(bb) <= walk) {
				continue;
			}
			BlockPos p = l.at(-a0, bb, 0);
			BlockState above = c.at(p.getX(), 1, p.getZ());
			if (!inLot(c, l, p) || (above != null && !above.isAir())) {
				continue;
			}
			c.set(p, Math.floorMod(bb, 2) == 0 ? c.floor() : STONE);
		}
	}

	/**
	 * The strips of the cross beside the gate and beside the walk that were the plain grey accent (with the old light lattice's cyan dots):
	 * stone bricks, mossy and cracked ones. The bays of the forecourt take most of them over afterwards; what they leave stays paved.
	 */
	private static void flanks(CitadelCanvas c, Lot l, int a0, int a1, int walk) {
		int edge = l.court() + 1;
		int gateEdge = (l.gateW() + 1) / 2 + 1;
		for (int a = l.court() + 1; a <= a1; a++) {
			int from = a < a0 ? gateEdge : walk + 1;
			for (int side = -1; side <= 1; side += 2) {
				for (int k = from; k <= edge; k++) {
					BlockPos p = l.at(-a, side * k, 0);
					BlockState above = c.at(p.getX(), 1, p.getZ());
					if (!inLot(c, l, p) || (above != null && !above.isAir())) {
						continue;
					}
					int h = Math.floorMod(a * 5 + k * 3, 7);
					c.set(p, h <= 1 ? b(Blocks.CRACKED_STONE_BRICKS) : STONE);
				}
			}
		}
	}

	private static boolean inLot(CitadelCanvas c, Lot l, BlockPos p) {
		Zone zn = c.zoneAt(p.getX(), p.getZ());
		return zn.kind() == Kind.LOT && zn.lot() == l.id();
	}

	/** Three tones: the courtyard's own floor (pink) as the runner in the middle, deepslate tile either side of it, calcite and tile outside, a dashed line of the project's colour. */
	private static BlockState armFloor(CitadelCanvas c, int n, int bb, DyeColor col, int rowsLeft) {
		int a = Math.abs(bb);
		if (a <= 1) {
			// the last two rows fade the pink runner into stone, towards the grey corridor of CitadelPlacement that takes over
			if (rowsLeft == 0) {
				return STONE;
			}
			if (rowsLeft == 1) {
				return a == 1 ? STONE : c.floor();
			}
			return a == 0 && n % 4 == 0 ? b(Blocks.DYED_TERRACOTTA.pick(col)) : c.floor();
		}
		if (a == 2) {
			return FOOT;
		}
		return n % 2 == 0 ? STONE : POST;
	}

	private static Direction outward(Lot l, int bb) {
		return bb > 0 ? l.across() : l.across().getOpposite();
	}

	private static void lamp(CitadelCanvas c, Lot l, int a, int bb) {
		BlockPos p = l.at(-a, bb, 1);
		c.set(p, b(Blocks.DEEPSLATE_TILE_WALL));
		c.set(p.above(), b(Blocks.CHERRY_FENCE));
		c.set(p.above(2), b(Blocks.CHERRY_FENCE));
		c.set(p.above(3), c.kit.gardenLantern());
	}

	/** A bench of three cherry stairs, back to the kerb. */
	private static void bench(CitadelCanvas c, Lot l, int a, int bb) {
		for (int k = -1; k <= 1; k++) {
			c.set(l.at(-(a + k), bb, 1), BarrioKit.stair(Blocks.CHERRY_STAIRS, outward(l, bb)));
		}
	}

	private static void planter(CitadelCanvas c, Lot l, int a, int bb) {
		for (int k = -1; k <= 1; k++) {
			BlockPos p = l.at(-(a + k), bb, 1);
			c.set(p, c.kit.gardenPlanter());
			if (k == 0) {
				c.set(p.above(), b(Blocks.FLOWERING_AZALEA));
			}
		}
	}

	/**
	 * The signpost at the street end of the arm: a post of cherry log with a lantern on top and a board for every hall
	 * (the walker comes from the hub heading {@code out}, so hall 0 is on the left, 1 on the right, 2 ahead).
	 */
	private static void signpost(CitadelCanvas c, Lot l, HqLayout.Module lead, int a, int walk) {
		TreeMap<Integer, HqLayout.Module> mods = c.lots.get(l.id());
		if (mods == null) {
			return;
		}
		int bb = walk;
		BlockPos post = l.at(-a, bb, 1);
		Direction hub = l.out().getOpposite();
		for (int y = 0; y < 5; y++) {
			c.set(post.above(y), b(Blocks.CHERRY_LOG));
		}
		c.set(post.above(5), BarrioKit.lantern(false));
		int y = 4;
		int leadHall = RingCampusGeometry.hallOf(lead.index());
		for (int h = RingCampusGeometry.HALLS - 1; h >= 0; h--) {
			HqLayout.Module m = mods.get(h) != null ? mods.get(h) : mods.get(h + RingCampusGeometry.HALLS);
			if (m == null) {
				continue;
			}
			boolean isLead = h == leadHall || m.type() == HqLayout.Type.LEAD_OFFICE;
			String arrow = h == 0 ? "<- Hall 1" : h == 1 ? "Hall 2 ->" : "^ Hall 3";
			String l3 = isLead ? "Lead office" : m.desks().size() + (m.desks().size() == 1 ? " desk" : " desks");
			BlockPos sp = post.above(y - 1).relative(hub);
			BlockState sign = b(Blocks.CHERRY_WALL_SIGN).setValue(BlockStateProperties.HORIZONTAL_FACING, hub);
			c.put(sp.getX(), sp.getY(), sp.getZ(), sign, DECOR);
			c.data.put(sp.asLong(), SignTags.sign(BarrioKit.readable(isLead ? HqStyle.envColor(lead) : DyeColor.BLACK), true, arrow, m.label(), l3, ""));
			c.fixtures.add(sp.asLong());
			y--;
			if (y < 2) {
				break;
			}
		}
	}

	// ------------------------------------------------------------------ the initial

	private static boolean dark(DyeColor d) {
		return switch (d) {
			case BLACK, GRAY, BROWN, BLUE, PURPLE, GREEN, RED, CYAN -> true;
			default -> false;
		};
	}

	/** The 3 x 5 glyph of a character: five rows of three 0/1. */
	static String[] glyph(String ch) {
		return switch (ch.charAt(0)) {
			case 'A' -> g("010", "101", "111", "101", "101");
			case 'B' -> g("110", "101", "110", "101", "110");
			case 'C' -> g("011", "100", "100", "100", "011");
			case 'D' -> g("110", "101", "101", "101", "110");
			case 'E' -> g("111", "100", "110", "100", "111");
			case 'F' -> g("111", "100", "110", "100", "100");
			case 'G' -> g("011", "100", "101", "101", "011");
			case 'H' -> g("101", "101", "111", "101", "101");
			case 'I' -> g("111", "010", "010", "010", "111");
			case 'J' -> g("001", "001", "001", "101", "010");
			case 'K' -> g("101", "101", "110", "101", "101");
			case 'L' -> g("100", "100", "100", "100", "111");
			case 'M' -> g("101", "111", "111", "101", "101");
			case 'N' -> g("110", "101", "101", "101", "101");
			case 'O' -> g("010", "101", "101", "101", "010");
			case 'P' -> g("110", "101", "110", "100", "100");
			case 'Q' -> g("010", "101", "101", "110", "011");
			case 'R' -> g("110", "101", "110", "101", "101");
			case 'S' -> g("011", "100", "010", "001", "110");
			case 'T' -> g("111", "010", "010", "010", "010");
			case 'U' -> g("101", "101", "101", "101", "111");
			case 'V' -> g("101", "101", "101", "101", "010");
			case 'W' -> g("101", "101", "111", "111", "101");
			case 'X' -> g("101", "101", "010", "101", "101");
			case 'Y' -> g("101", "101", "010", "010", "010");
			case 'Z' -> g("111", "001", "010", "100", "111");
			case '0' -> g("111", "101", "101", "101", "111");
			case '1' -> g("010", "110", "010", "010", "111");
			case '2' -> g("110", "001", "010", "100", "111");
			case '3' -> g("110", "001", "110", "001", "110");
			case '4' -> g("101", "101", "111", "001", "001");
			case '5' -> g("111", "100", "110", "001", "110");
			case '6' -> g("011", "100", "111", "101", "111");
			case '7' -> g("111", "001", "010", "010", "010");
			case '8' -> g("111", "101", "111", "101", "111");
			case '9' -> g("111", "101", "111", "001", "110");
			default -> g("010", "111", "111", "111", "010");
		};
	}

	private static String[] g(String... rows) {
		return rows;
	}
}
