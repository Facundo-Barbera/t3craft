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
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;

/**
 * The gatehouse of a barrio at scale 2 and up (struct/barrio-gatehouse), and the arm of the gate in front of it.
 *
 * <p><b>The building.</b> The gate room (15 x 10 at ring 1, 19 x 14 at rings 2 and 3) is split across its width into
 * three: a 5-wide passage down the middle, and on each side a block of {@code sw = (W - 5) / 2} cells. Each side
 * block is a flag tower at the hub end (solid, 10 high, pyramid roof, a pole with a flag in the project's colour on
 * top) and a guard room at the courtyard end (lower, hipped roof, a doorway on the passage). The passage has a
 * stepped arch (3, 4, 5 high across), a ridge beam, hanging lanterns and banners; over it the central block rises
 * into a gable with the sign board: three name plaques on a band of the project's colour.
 *
 * <p><b>Identification.</b> Seen from the hub: the gable's colour band and plaques, the initial of the project in
 * 3 x 5 blocks of its colour on each tower, a banner pole on each tower. The colour is the project's environment colour
 * ({@link HqStyle#envColor(HqLayout.Module)}). The plaques and signs are fixtures (the light pass never replaces them).
 *
 * <p><b>Uses.</b> The left guard room is the gate post (chests, barrels, a counter with the register); the right one is
 * the map room (a cartography table at its centre, shelves, a lectern).
 *
 * <p><b>The arm</b> (from the hub face of the gate to the end of the barrio's lot cluster, 7 wide: the width of the
 * corridor plus a lamp lane each side): a three-tone pavement (accent, deepslate tile, calcite) with a dashed centre
 * line in the project's colour, lamp posts every 4 rows, benches and planters in between, and a signpost with one
 * board per hall (an arrow, the hall's name and, for the lead's, "Lead office"). The bays beside the arm are
 * {@code CitadelPlacement#forecourt}'s; nothing here depends on them except the 7-wide walk they leave.
 *
 * <p>Palette: deepslate tile (plinth, floor), the barrio's own wall, post, band, roof and ridge ({@link Mats}, the
 * same ones the halls use), calcite and the project's wool.
 */
final class BarrioGateBig {
	private BarrioGateBig() {
	}

	/** Half width of the passage: 5 wide. */
	private static final int HALF = 2;
	/** Height of the walls of a guard room (the roof layer, which is also its ceiling, sits above). */
	private static final int ANNEX_TOP = 4;
	/** Height of the walls of a tower. */
	private static final int TOWER_TOP = 12;
	/** Height of the walls over the passage at the middle, one block less at {@code |dv| = 1}, two at {@code |dv| = 2}. */
	private static final int GABLE_WALL = 10;
	/** Height of the sign board (plaques). */
	private static final int PLAQUE_Y = 7;

	/** Whether the gate room is big enough for this design. */
	static boolean fits(Room r) {
		return r.w() >= 15 && r.d() >= 10;
	}

	static void build(CitadelCanvas c, Lot l, HqLayout.Module lead, Mats mats) {
		new Gate(c, l, lead, mats).build();
		arm(c, l, lead);
	}

	// ------------------------------------------------------------------ the building

	private static final class Gate {
		final CitadelCanvas c;
		final Lot l;
		final HqLayout.Module lead;
		final Mats mats;
		final Room r;
		final int W;
		final int D;
		final int mid;
		/** Width of a side block, depth of a guard room, first row of the towers. */
		final int sw;
		final int tu;
		final Direction back;
		final Direction side;
		final DyeColor col;
		final BlockState wool;
		final BlockState panelBg;
		final BlockState letter;

		Gate(CitadelCanvas c, Lot l, HqLayout.Module lead, Mats mats) {
			this.c = c;
			this.l = l;
			this.lead = lead;
			this.mats = mats;
			this.r = l.gate();
			this.W = r.w();
			this.D = r.d();
			this.mid = r.c();
			this.sw = (W - (2 * HALF + 1)) / 2;
			this.tu = D - sw;
			this.back = r.back();
			this.side = r.side();
			this.col = HqStyle.envColor(lead);
			this.wool = b(Blocks.WOOL.pick(col));
			this.panelBg = b(Blocks.POLISHED_BLACKSTONE_BRICKS);
			// the initial: the project's wool, or white when the colour is too dark to read on the black panel
			this.letter = dark(col) ? b(Blocks.WOOL.pick(DyeColor.WHITE)) : wool;
		}

		void build() {
			for (int u = 0; u < D; u++) {
				for (int v = 0; v < W; v++) {
					int dv = v - mid;
					if (Math.abs(dv) <= HALF) {
						passageCol(u, v, dv);
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
				pyramid(new Room(r.cell(tu, v0, 0), back, side, sw, D - tu), TOWER_TOP + 1, true, s);
			}
			gableVerge();
			passageDressing();
			boards();
			guardPost(0);
			mapRoom(W - sw);
		}

		// -------------------------------------------------------------- blocks

		BlockState band(boolean alongSide) {
			Direction.Axis ax = alongSide ? side.getAxis() : back.getAxis();
			return mats.band() == mats.post() ? c.kit.beam(ax) : mats.band();
		}

		BlockState keystone(boolean face) {
			return face ? b(Blocks.CHISELED_STONE_BRICKS) : HallFacade.beamLike(c, mats.post(), back.getAxis());
		}

		/** The stepped arch: the barrio's roof stairs inside, a light stone-brick ring on the two faces so the opening reads from afar. */
		BlockState archStair(int dv, boolean face) {
			Direction away = dv < 0 ? side.getOpposite() : side;
			return BarrioKit.stair(face ? Blocks.STONE_BRICK_STAIRS : mats.roof(), away, StairsShape.STRAIGHT, Half.TOP);
		}

		/** The roof stair on a slope that rises towards the centre line of the passage. */
		BlockState slopeStair(int dv, Block m) {
			Direction up = dv < 0 ? side : side.getOpposite();
			return BarrioKit.stair(m, up, StairsShape.STRAIGHT, Half.BOTTOM);
		}

		boolean insideGate(BlockPos p) {
			int dx = p.getX() - r.origin().getX();
			int dz = p.getZ() - r.origin().getZ();
			int gu = dx * back.getStepX() + dz * back.getStepZ();
			int gv = dx * side.getStepX() + dz * side.getStepZ();
			return gu >= 0 && gu < D && gv >= 0 && gv < W;
		}

		// -------------------------------------------------------------- passage

		BlockState passageFloor(int u, int dv) {
			int a = Math.abs(dv);
			if (a <= 1) {
				return a == 0 && u % 4 == 0 ? b(Blocks.DYED_TERRACOTTA.pick(col)) : c.floor();
			}
			return u % 2 == 0 ? CALCITE : FOOT;
		}

		void passageCol(int u, int v, int dv) {
			int a = Math.abs(dv);
			c.set(r.cell(u, v, 0), passageFloor(u, dv));
			// clear height: 3 at the sides, 4 next to the middle, 5 in the middle; the arch piece sits just above it
			int clear = 5 - a;
			int top = GABLE_WALL - a;
			for (int y = 1; y <= clear; y++) {
				c.set(r.cell(u, v, y), AIR);
			}
			boolean face = u == 0 || u == D - 1;
			if (a == 0) {
				c.set(r.cell(u, v, clear + 1), keystone(face));
			} else {
				c.set(r.cell(u, v, clear + 1), archStair(dv, face));
			}
			for (int y = clear + 2; y <= top; y++) {
				c.set(r.cell(u, v, y), wallFace(u, y, dv));
			}
			// the gable's slope: stairs up to the ridge (the verge, gableVerge, carries it one row past each face)
			c.set(r.cell(u, v, top + 1), a == 0 ? mats.ridge() : slopeStair(dv, mats.roof()));
		}

		/** The wall over the arch. On the two faces the board: a dark ground and a band of the project's colour. */
		BlockState wallFace(int u, int y, int dv) {
			boolean face = u == 0 || u == D - 1;
			if (face && y == PLAQUE_Y + 1) {
				return wool;
			}
			if (face && y == PLAQUE_Y) {
				return Math.abs(dv) == 2 ? wool : FOOT;
			}
			return mats.wall();
		}

		/** The gable's end overhang: one row of roof beyond each face, ridge included. */
		void gableVerge() {
			for (int dv = -HALF; dv <= HALF; dv++) {
				int a = Math.abs(dv);
				int ry = GABLE_WALL - a + 1;
				for (int u : new int[] {-1, D}) {
					BlockState s = a == 0 ? BarrioKit.slabOf(mats.roof()) : slopeStair(dv, BarrioKit.altStairs(mats.roof()));
					c.set(r.cell(u, mid + dv, ry), s);
				}
			}
		}

		/** Lanterns under the vault, banners on the passage walls, the hanging sign of the project at the hub end. */
		void passageDressing() {
			for (int u = 1; u < D - 2; u += 3) {
				c.set(r.cell(u, mid, 5), BarrioKit.lantern(true));
			}
			BlockPos sign = r.cell(D - 1, mid, 5);
			BlockState hs = Blocks.CHERRY_HANGING_SIGN.defaultBlockState().setValue(CeilingHangingSignBlock.ROTATION, CitadelBarrios.rotation(back))
					.setValue(CeilingHangingSignBlock.ATTACHED, false);
			c.put(sign.getX(), sign.getY(), sign.getZ(), hs, HANGING);
			c.data.put(sign.asLong(), BarrioGate.barrioSign(c, lead).signTag());
			c.fixtures.add(sign.asLong());
			// banners in the project's colour on the tower walls beside the passage
			for (int u : new int[] {tu + 1, D - 2}) {
				for (int dv = -HALF; dv <= HALF; dv += 2 * HALF) {
					Direction f = dv < 0 ? side : side.getOpposite();
					BlockPos p = r.cell(u, mid + dv, 3);
					c.put(p.getX(), p.getY(), p.getZ(), b(Blocks.WALL_BANNER.pick(col)).setValue(BlockStateProperties.HORIZONTAL_FACING, f), DECOR);
				}
			}
		}

		// -------------------------------------------------------------- towers

		void towerCol(int u, int v) {
			boolean left = v < mid;
			int tv = left ? v : v - (W - sw);
			int tu_ = u - tu;
			boolean edgeU = tu_ == 0 || tu_ == D - tu - 1;
			boolean edgeV = tv == 0 || tv == sw - 1;
			boolean corner = edgeU && edgeV;
			boolean perimeter = edgeU || edgeV;
			boolean hubFace = u == D - 1;
			boolean uFace = edgeU && !edgeV;
			c.set(r.cell(u, v, 0), FOOT);
			for (int y = 1; y <= TOWER_TOP; y++) {
				BlockState s;
				if (!perimeter) {
					s = mats.wall();
				} else if (corner) {
					s = mats.post();
				} else if (y <= 2) {
					s = FOOT;
				} else if (y == 3) {
					s = mats.wall();
				} else if (y == 4 || y == TOWER_TOP) {
					s = band(uFace);
				} else if (hubFace && !edgeV) {
					s = letterCell(tv, y, left);
				} else if (!edgeU && edgeV && (y == 6 || y == 7) && tu_ == (D - tu) / 2 && (v == 0 || v == W - 1)) {
					s = b(Blocks.GLASS_PANE);
				} else {
					s = mats.wall();
				}
				c.set(r.cell(u, v, y), s);
			}
		}

		/** A cell of the letter panel on the hub face of a tower (y 5..9, tv 1..sw-2): the project's initial in 3 x 5. */
		BlockState letterCell(int tv, int y, boolean left) {
			if (y < 5 || y > 9) {
				return mats.wall();
			}
			int inner = sw - 2;
			int start = 1 + (inner - 3) / 2;
			int cx = tv - start;
			int cy = 9 - y;
			if (cx < 0 || cx > 2) {
				return panelBg;
			}
			// the viewer comes from the hub looking along -back: his left is +side, so columns run towards -v
			int col3 = 2 - cx;
			String[] g = glyph(initial());
			return g[cy].charAt(col3) == '1' ? letter : panelBg;
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
					s = mats.post();
				} else if (y == 1) {
					s = FOOT;
				} else if (y == ANNEX_TOP) {
					s = band(edgeU && !edgeV);
				} else if (y <= 3 && !passageSide && ((u == 0 && av == sw / 2) || (edgeV && u == (tu - 1) / 2))) {
					s = b(Blocks.GLASS_PANE);
				} else {
					s = mats.wall();
				}
				c.set(r.cell(u, v, y), s);
			}
		}

		void put(int u, int v, int y, BlockState s) {
			c.set(r.cell(u, v, y), s);
		}

		/** The left guard room: chests and barrels along the outer wall, a counter with the register across the court wall. */
		void guardPost(int v0) {
			int iu0 = 1;
			int iu1 = tu - 2;
			int iv0 = v0 + 1;
			int iv1 = v0 + sw - 2;
			for (int u = iu0; u <= iu1; u++) {
				put(u, iv0, 1, (u - iu0) % 2 == 0 ? chest(side) : b(Blocks.BARREL));
			}
			// the counter: planks with the register (a lectern) on it, along the court wall
			for (int v = iv0 + 1; v <= iv1; v++) {
				put(iu0, v, 1, v == iv1 ? lectern(back) : b(Blocks.CHERRY_PLANKS));
			}
			// a bed of straw for the night watch? no: a second counter piece and a barrel by the far wall
			put(iu1, iv1, 1, b(Blocks.BARREL));
			hang((iu0 + iu1) / 2, (iv0 + iv1) / 2);
		}

		/** The right guard room, the map room: shelves on the outer wall, a cartography table at the centre, lecterns. */
		void mapRoom(int v0) {
			int iu0 = 1;
			int iu1 = tu - 2;
			int iv0 = v0 + 1;
			int iv1 = v0 + sw - 2;
			for (int u = iu0; u <= iu1; u++) {
				put(u, iv1, 1, b(Blocks.BOOKSHELF));
				put(u, iv1, 2, b(Blocks.BOOKSHELF));
			}
			put((iu0 + iu1) / 2, (iv0 + iv1) / 2, 1, b(Blocks.CARTOGRAPHY_TABLE));
			put(iu0, iv0, 1, lectern(side));
			put(iu1, iv0, 1, lectern(side));
			hang((iu0 + iu1) / 2, (iv0 + iv1) / 2);
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

		// -------------------------------------------------------------- roofs

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
					c.set(p.atY(roofY), mats.ridge());
					int k = Math.min(Math.min(u + 1, d - u), Math.min(v + 1, w - v));
					int y = roofY + k;
					if (k == kmax) {
						c.set(p.atY(y), mats.ridge());
						boolean centre = d == w && u == (d - 1) / 2 && v == (w - 1) / 2;
						if (tower && centre) {
							flag(p.atY(y), sideIdx);
						} else {
							c.set(p.atY(y + 1), centre ? BarrioKit.finial(mats.roof()) : BarrioKit.slabOf(mats.roof()));
						}
					} else {
						c.set(p.atY(y), BarrioRoofs.hipStair(s, mats.roof(), u, v, k, false, p.atY(y)));
					}
				}
			}
			int midv = (w - 1) / 2;
			for (int k = 2; k <= kmax; k++) {
				c.set(s.cell(k - 1, midv, roofY + k - 1), mats.ridge());
			}
			eave(s, roofY);
		}

		/** The eave ring outside {@code s}, except where it would run into the rest of the gate. */
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
					c.set(p, BarrioRoofs.hipStair(s, mats.roof(), u, v, 0, true, p.atY(y)));
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
			c.set(top.above(6), BarrioKit.finial(mats.roof()));
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
				c.set(p, armFloor(c, n, bb, col));
			}
		}
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

	private static boolean inLot(CitadelCanvas c, Lot l, BlockPos p) {
		Zone zn = c.zoneAt(p.getX(), p.getZ());
		return zn.kind() == Kind.LOT && zn.lot() == l.id();
	}

	/** Three tones: the courtyard's own floor (pink) as the runner in the middle, deepslate tile either side of it, calcite and tile outside, a dashed line of the project's colour. */
	private static BlockState armFloor(CitadelCanvas c, int n, int bb, DyeColor col) {
		int a = Math.abs(bb);
		if (a <= 1) {
			return a == 0 && n % 4 == 0 ? b(Blocks.DYED_TERRACOTTA.pick(col)) : c.floor();
		}
		if (a == 2) {
			return FOOT;
		}
		return n % 2 == 0 ? CALCITE : b(Blocks.STONE_BRICKS);
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
