package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.barrel;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.clearPane;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.door;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.facing;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.fence;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.lantern;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.log;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.slab;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.stair;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.trap;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.wall;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Support;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A working smithy with a lodging over it. A tall two-storey-high forge nave (blast furnace, furnace and a forge fire
 * under a brick hood whose flue leaves through the roof), a wide loading gate on the street, a stone stair two wide to
 * a studio loft that looks down on the forge through a rail, a stock room under the loft, and a side yard with a
 * wood pile and a loaded cart.
 *
 * <p>Concept: a Hanseatic stone-and-brick workshop with a dark half-timbered frame, deepslate-tile gable roof with a
 * glazed lantern on the ridge, one tall brick chimney. Palette: deepslate, wall stone (stone / tuff / mud brick by
 * seed), dark oak, brick, a little calcite in the gables. The seed picks the side of the yard, the side of the hearth
 * (and so of the chimney) and the wall material.
 *
 * <pre>
 *  plan (v down = towards the back)               section across the nave
 *   yard | apron  steps, barrels                     ridge lantern
 *   yard | gate wall (v2): gate 3+ wide             /            \
 *   yard | nave: stair (2 wide) . trough . anvils   /   nave      \  hood + flue on the hearth wall
 *   yard | loft front edge (rail)                  |   9 high      |
 *   yard | stock room under the studio loft        |__ loft y5 ____|
 * </pre>
 *
 * Size: 16–26 × 15–26 × 22+. Everything inside is at least 4 blocks clear; nothing is sealed.
 */
public final class HouseForgeAtelier implements Variant {
	private static final int VF = 2; // front wall row (rows 0..1 are the apron under the gable overhang)
	private static final int PLATE = 9; // top plate of the walls; the roof starts here
	private static final int FL = 5; // layer of the loft floor (one stands at FL + 1)

	@Override
	public String id() {
		return "house_forge_atelier";
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
		return 15;
	}

	@Override
	public int minH() {
		return 22;
	}

	@Override
	public int maxW() {
		return 26;
	}

	@Override
	public int maxD() {
		return 26;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Smithy s = new Smithy(c, p);
		s.walls();
		s.gables();
		s.roof();
		s.hearth();
		s.chimney();
		s.monitor();
		s.stairAndLoft();
		s.nave();
		s.stock();
		s.loft();
		s.front();
		s.buttresses();
		s.patio();
	}

	private static final class Smithy {
		final CitadelCanvas c;
		final Plot p;
		final int w;
		final int d;
		final int h;
		final int bw; // building width (local u 0..bw-1, hearth on the high side)
		final int pw; // side-yard width
		final int u0; // plot u of the building's local 0
		final int vb; // back wall row
		final int vm; // first row of the loft
		final int vh; // row of the hearth
		final int cu; // gate centre
		final int gw; // gate width (odd, 3 or more)
		final int hu; // column of the fire bed
		final int rc; // roof rise cap
		final int rc0;
		final int ra; // first ridge column
		final int rb; // last ridge column
		final int dv; // row of the yard door (-1 when none)
		final boolean patioRight;
		final boolean flipB;
		final boolean patioLocalRight;
		final int kind;

		Smithy(CitadelCanvas c, Plot p) {
			this.c = c;
			this.p = p;
			w = p.w();
			d = p.d();
			h = p.h();
			int pw0 = Math.max(3, Math.round(w * 0.22f));
			bw = Math.min(19, w - pw0);
			pw = w - bw;
			// seed choices, mixed through the hash so near seeds do not agree: side of the yard, side of the hearth and chimney, wall stone
			patioRight = p.hash(101, 0, 7, 2) == 0;
			flipB = p.hash(211, 0, 13, 2) == 0;
			kind = p.hash(307, 0, 29, 3);
			patioLocalRight = patioRight != flipB;
			u0 = patioRight ? 0 : pw;
			vb = d - 2;
			int ld = Math.max(4, Math.min(8, Math.round((vb - 3) * 0.4f)));
			vm = Math.max(9, vb - ld);
			vh = 3 + (vm - 3) / 2;
			cu = bw / 2;
			int iw = bw - 4;
			gw = iw >= 18 ? 7 : iw >= 12 ? 5 : 3;
			hu = bw - 3;
			rc0 = (bw - 1) / 2;
			rc = Math.min(rc0, 6);
			ra = rc < rc0 ? rc : (bw - 1) / 2;
			rb = bw - 1 - ra;
			int door = -1;
			for (int v = vm + 1; v <= vb - 1; v++) {
				if ((v - VF) % 4 == 2) {
					door = v;
					break;
				}
			}
			dv = door;
		}

		// ------------------------------------------------------------------ mapping (building-local u, mirrored by seed)

		int mu(int bu) {
			return u0 + (flipB ? bw - 1 - bu : bu);
		}

		BlockState mir(BlockState st) {
			return flipB ? st.mirror(Mirror.FRONT_BACK) : st;
		}

		void s(int bu, int y, int v, BlockState st) {
			p.set(c, mu(bu), y, v, mir(st));
		}

		void put(int bu, int y, int v, BlockState st, int layer) {
			p.put(c, mu(bu), y, v, mir(st), layer);
		}

		boolean present(int bu, int y, int v) {
			return Support.present(c, p, mu(bu), y, v);
		}

		// ------------------------------------------------------------------ materials

		BlockState base(int bu, int y, int v) {
			return switch (p.hash(mu(bu), y, v, 6)) {
				case 0 -> b(Blocks.COBBLED_DEEPSLATE);
				case 1 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
				default -> b(Blocks.DEEPSLATE_BRICKS);
			};
		}

		BlockState body(int bu, int y, int v) {
			int k = p.hash(mu(bu), y, v, 9);
			return switch (kind) {
				case 0 -> k == 0 ? b(Blocks.CRACKED_STONE_BRICKS) : k == 1 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
				case 1 -> k <= 1 ? b(Blocks.POLISHED_TUFF) : b(Blocks.TUFF_BRICKS);
				default -> k == 0 ? b(Blocks.PACKED_MUD) : b(Blocks.MUD_BRICKS);
			};
		}

		BlockState brick(int bu, int y, int v) {
			return p.hash(mu(bu), y, v, 4) == 0 ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.BRICKS);
		}

		BlockState plaster(int bu, int y, int v) {
			return p.hash(mu(bu), y, v, 6) == 0 ? b(Blocks.SMOOTH_STONE) : b(Blocks.CALCITE);
		}

		BlockState floorAt(int bu, int v) {
			return switch (p.hash(mu(bu), 0, v, 6)) {
				case 0 -> b(Blocks.DEEPSLATE_TILES);
				case 1 -> b(Blocks.COBBLED_DEEPSLATE);
				default -> b(Blocks.POLISHED_DEEPSLATE);
			};
		}

		static BlockState frame(Direction.Axis axis) {
			return log(Blocks.DARK_OAK_LOG, axis);
		}

		static BlockState trim(Direction.Axis axis) {
			return log(Blocks.STRIPPED_DARK_OAK_LOG, axis);
		}

		boolean patioWall(int bu) {
			return bu == (patioLocalRight ? bw - 2 : 1);
		}

		// ------------------------------------------------------------------ walls

		void walls() {
			for (int bu = 1; bu <= bw - 2; bu++) {
				for (int v = VF; v <= vb; v++) {
					boolean edge = bu == 1 || bu == bw - 2 || v == VF || v == vb;
					s(bu, 0, v, edge ? base(bu, 0, v) : floorAt(bu, v));
				}
			}
			frontWall();
			sideWall(1);
			sideWall(bw - 2);
			backWall();
		}

		void frontWall() {
			int half = gw / 2;
			for (int bu = 1; bu <= bw - 2; bu++) {
				int dist = Math.abs(bu - cu);
				boolean gate = dist <= half;
				boolean jamb = dist == half + 1;
				boolean post = bu == 1 || bu == bw - 2 || jamb || (!gate && (bu - 1) % 4 == 0);
				boolean win = !post && !gate && ((bu - 1) % 4 == 1 || (bu - 1) % 4 == 2);
				for (int y = 1; y <= PLATE; y++) {
					BlockState st;
					if (y == PLATE) {
						st = frame(Direction.Axis.X);
					} else if (gate) {
						if (y <= 4) {
							continue;
						}
						st = y == 5 ? trim(Direction.Axis.X) : y <= 7 ? clearPane(true) : body(bu, y, VF);
					} else if (post) {
						st = jamb ? trim(Direction.Axis.Y) : frame(Direction.Axis.Y);
					} else if (y <= 2) {
						st = base(bu, y, VF);
					} else if (y <= 5) {
						st = win ? clearPane(true) : body(bu, y, VF);
					} else if (y == 6) {
						st = trim(Direction.Axis.X);
					} else {
						st = body(bu, y, VF);
					}
					s(bu, y, VF, st);
				}
			}
		}

		void sideWall(int bu) {
			boolean hearth = bu == bw - 2;
			for (int v = VF + 1; v <= vb - 1; v++) {
				boolean zone = hearth && Math.abs(v - vh) <= 3;
				boolean post = (v - VF) % 4 == 0;
				int m = (v - VF) % 4;
				boolean isDoor = patioWall(bu) && v == dv;
				boolean stairSide = bu == 1 && v >= vm - 5 && v <= vm - 1;
				boolean win = !post && (m == 1 || m == 2) && !zone && !isDoor;
				for (int y = 1; y <= PLATE; y++) {
					BlockState st;
					if (zone) {
						st = y <= 2 ? base(bu, y, v) : brick(bu, y, v);
					} else if (isDoor && y <= 2) {
						st = door(Blocks.DARK_OAK_DOOR, bu == 1 ? Direction.WEST : Direction.EAST, y == 2);
					} else if (y == PLATE) {
						st = frame(Direction.Axis.Z);
					} else if (post) {
						st = frame(Direction.Axis.Y);
					} else if (y <= 2) {
						st = base(bu, y, v);
					} else if (y <= 5) {
						st = win && !stairSide ? clearPane(false) : body(bu, y, v);
					} else if (y == 6) {
						st = trim(Direction.Axis.Z);
					} else {
						st = win ? clearPane(false) : body(bu, y, v);
					}
					s(bu, y, v, st);
				}
			}
		}

		void backWall() {
			for (int bu = 1; bu <= bw - 2; bu++) {
				boolean post = bu == 1 || bu == bw - 2 || (bu - 1) % 4 == 0;
				boolean win = !post && ((bu - 1) % 4 == 1 || (bu - 1) % 4 == 2);
				for (int y = 1; y <= PLATE; y++) {
					BlockState st;
					if (y == PLATE) {
						st = frame(Direction.Axis.X);
					} else if (post) {
						st = frame(Direction.Axis.Y);
					} else if (y <= 2) {
						st = base(bu, y, vb);
					} else if (y <= 4) {
						st = win ? clearPane(true) : body(bu, y, vb);
					} else if (y == 5) {
						st = body(bu, y, vb);
					} else if (y == 6) {
						st = trim(Direction.Axis.X);
					} else {
						st = win ? clearPane(true) : body(bu, y, vb);
					}
					s(bu, y, vb, st);
				}
			}
		}

		// ------------------------------------------------------------------ roof

		int roofY(int bu) {
			return PLATE + Math.min(Math.min(bu, bw - 1 - bu), rc);
		}

		/** The gable walls above the plates, timber-framed, with a glazed loft window on the street side. */
		void gables() {
			for (int pass = 0; pass < 2; pass++) {
				int v = pass == 0 ? VF : vb;
				for (int bu = 2; bu <= bw - 3; bu++) {
					int top = roofY(bu);
					for (int y = PLATE + 1; y < top; y++) {
						BlockState st;
						if (y == PLATE + 1) {
							st = trim(Direction.Axis.X); // tie beam
						} else if ((bu - cu) % 3 == 0) {
							st = frame(Direction.Axis.Y); // studs
						} else if (pass == 0 && Math.abs(bu - cu) <= 1 && y >= PLATE + 3 && y <= PLATE + 4) {
							st = clearPane(true); // lucarne over the gate
						} else if (pass == 1 && bu == cu && y >= PLATE + 3 && y <= PLATE + 4) {
							st = clearPane(true);
						} else {
							st = plaster(bu, y, v);
						}
						s(bu, y, v, st);
					}
				}
			}
		}

		/**
		 * [finish] deepslate tiles, blackstone-brick rims, a ridge of tile blocks, the rise capped so a wide hall gets a flat
		 * crown for the lantern. A full block of the roof's own material under every cell keeps it face-connected.
		 */
		void roof() {
			for (int bu = 0; bu < bw; bu++) {
				int rs = Math.min(bu, bw - 1 - bu);
				int y = PLATE + Math.min(rs, rc);
				boolean left = bu < bw - 1 - bu;
				for (int v = 1; v <= d - 1; v++) {
					boolean edge = v == 1 || v == d - 1 || bu == 0 || bu == bw - 1 || (v - VF) % 4 == 0; // [depth] a lighter rafter course over every wall post
					BlockState st;
					if (rs < rc || (bw % 2 == 0 && rc == rc0 && rs == rc)) {
						Block tile = edge ? Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS
								: p.hash(mu(bu), y, v, 7) == 0 ? Blocks.DEEPSLATE_BRICK_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS; // a few weathered tiles
						st = stair(tile, left ? Direction.EAST : Direction.WEST, false);
					} else {
						st = b(Blocks.DEEPSLATE_TILES);
					}
					s(bu, y, v, st);
					if (rs >= 1) {
						Support.fillIfFree(c, p, mu(bu), y - 1, v, b(Blocks.DEEPSLATE_TILES));
					}
				}
			}
		}

		/** A glazed lantern on the ridge over the forge: light in, heat out. */
		void monitor() {
			int ma = ra - 1;
			int mb = rb + 1;
			int mv0 = vh - 2;
			int mv1 = vh + 2;
			int yb = PLATE + rc + 1;
			for (int v = mv0; v <= mv1; v++) {
				s(ma, yb - 1, v, b(Blocks.DEEPSLATE_TILES));
				s(mb, yb - 1, v, b(Blocks.DEEPSLATE_TILES));
			}
			for (int bu = ma; bu <= mb; bu++) {
				for (int v = mv0; v <= mv1; v++) {
					for (int y = yb; y <= yb + 1; y++) {
						boolean corner = (bu == ma || bu == mb) && (v == mv0 || v == mv1);
						s(bu, y, v, corner ? frame(Direction.Axis.Y) : b(Blocks.GLASS));
					}
				}
			}
			for (int bu = ma; bu <= mb; bu++) {
				for (int v = mv0; v <= mv1; v++) {
					boolean rim = bu == ma || bu == mb || v == mv0 || v == mv1; // flush: nothing hangs over a roof you could stand on
					s(bu, yb + 2, v, slab(rim ? Blocks.POLISHED_BLACKSTONE_BRICK_SLAB : Blocks.DEEPSLATE_TILE_SLAB, false));
				}
			}
		}

		// ------------------------------------------------------------------ the forge

		/** Furnace, forge fire and blast furnace in a brick alcove under a hood; the flue rises from the fire. */
		void hearth() {
			for (int dz = -1; dz <= 1; dz++) {
				s(hu, 0, vh + dz, b(Blocks.BRICKS));
			}
			s(hu, 1, vh - 1, facing(Blocks.FURNACE, Direction.WEST).setValue(BlockStateProperties.LIT, true));
			s(hu, 1, vh + 1, facing(Blocks.BLAST_FURNACE, Direction.WEST).setValue(BlockStateProperties.LIT, true));
			s(hu, 1, vh, b(Blocks.CAMPFIRE));
			for (int y = 1; y <= 4; y++) {
				for (int uu = hu - 1; uu <= hu; uu++) {
					for (int vv : new int[] {vh - 2, vh + 2}) {
						s(uu, y, vv, brick(uu, y, vv));
					}
				}
			}
			for (int uu = hu - 1; uu <= hu; uu++) {
				for (int vv = vh - 2; vv <= vh + 2; vv++) {
					if (uu == hu && vv == vh) {
						continue; // the flue
					}
					BlockState st = brick(uu, 5, vv);
					if (uu == hu - 1 && Math.abs(vv - vh) <= 1) {
						st = stair(Blocks.BRICK_STAIRS, Direction.EAST, true); // [depth] the hood lip
					}
					s(uu, 5, vv, st);
				}
				// the hood corners rise as a chimney breast to the roof, so no ledge is left to stand on
				for (int vv : new int[] {vh - 2, vh + 2}) {
					for (int y = 6; y < roofY(uu); y++) {
						s(uu, y, vv, brick(uu, y, vv));
					}
				}
			}
		}

		void chimney() {
			int top = Math.min(PLATE + rc + 4, h - 3);
			for (int y = 6; y <= top; y++) {
				for (int uu = hu - 1; uu <= hu + 1; uu++) {
					for (int vv = vh - 1; vv <= vh + 1; vv++) {
						if (uu == hu && vv == vh) {
							s(uu, y, vv, CitadelCanvas.AIR);
						} else {
							s(uu, y, vv, brick(uu, y, vv));
						}
					}
				}
			}
			// [finish] a dark band under the crown and a slab cap round the open flue, all inside the 3x3 footprint
			for (int du = -1; du <= 1; du++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (du != 0 || dz != 0) {
						s(hu + du, top - 1, vh + dz, b(Blocks.DEEPSLATE_BRICKS));
						s(hu + du, top, vh + dz, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
						s(hu + du, top + 1, vh + dz, slab(Blocks.DEEPSLATE_BRICK_SLAB, false));
					}
				}
			}
			s(hu, top + 1, vh, CitadelCanvas.AIR);
		}

		// ------------------------------------------------------------------ stair, loft, nave, stock

		void stairAndLoft() {
			for (int bu = 2; bu <= bw - 3; bu++) {
				for (int v = vm; v <= vb - 1; v++) {
					BlockState st;
					if (v == vm) {
						st = trim(Direction.Axis.X); // front beam
					} else if ((v - vm) % 3 == 0) {
						st = frame(Direction.Axis.X); // joist
					} else {
						st = p.hash(mu(bu), FL, v, 3) == 0 ? b(Blocks.SPRUCE_PLANKS) : b(Blocks.DARK_OAK_PLANKS);
					}
					s(bu, FL, v, st);
				}
			}
			for (int bu = 4; bu <= bw - 3; bu += 4) {
				if (bw - 3 - bu == 1) {
					continue; // too close to the post at the hearth wall
				}
				for (int y = 1; y <= 4; y++) {
					s(bu, y, vm, frame(Direction.Axis.Y));
				}
			}
			for (int y = 1; y <= 4; y++) {
				s(bw - 3, y, vm, frame(Direction.Axis.Y));
			}
			for (int bu = 4; bu <= bw - 3; bu++) {
				s(bu, FL + 1, vm, fence(Blocks.DARK_OAK_FENCE, false, bu + 1 <= bw - 3, false, bu - 1 >= 4));
			}
			// the stair: two wide, five masonry steps up along the left wall, first step met head-on from the gate
			for (int k = 1; k <= 5; k++) {
				int v = vm - 6 + k;
				for (int bu = 2; bu <= 3; bu++) {
					for (int y = 1; y < k; y++) {
						s(bu, y, v, b(Blocks.STONE_BRICKS));
					}
					s(bu, k, v, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
				}
			}
		}

		void hang(int bu, int y, int v) {
			put(bu, y, v, lantern(true), CitadelCanvas.HANGING);
			for (int yy = y + 1; yy <= PLATE + rc + 1; yy++) {
				if (present(bu, yy, v)) {
					break;
				}
				put(bu, yy, v, Blocks.IRON_CHAIN.defaultBlockState(), CitadelCanvas.HANGING);
			}
		}

		void nave() {
			int tu0 = Math.max(5, bw - 8);
			// [function] a contained quench trough, well away from the fire
			for (int t = 0; t < 2; t++) {
				s(tu0 + t, 0, vh, b(Blocks.WATER));
				s(tu0 + t, 1, vh - 1, slab(Blocks.STONE_BRICK_SLAB, false));
				s(tu0 + t, 1, vh + 1, slab(Blocks.STONE_BRICK_SLAB, false));
			}
			s(hu - 3, 1, vh - 1, facing(Blocks.ANVIL, Direction.NORTH));
			s(hu - 3, 1, vh + 1, facing(Blocks.ANVIL, Direction.NORTH));
			// stations along the gate wall
			int ul = cu - gw / 2 - 1;
			int ur = cu + gw / 2 + 1;
			if (ul >= 4) {
				s(ul, 1, 3, b(Blocks.SMITHING_TABLE));
			}
			s(ur, 1, 3, b(Blocks.CRAFTING_TABLE));
			if (ur + 1 <= hu - 1) {
				s(ur + 1, 1, 3, b(Blocks.GRINDSTONE).setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
			}
			for (int bu = ur + 2; bu <= hu - 1; bu++) {
				s(bu, 1, 3, bu % 2 == 0 ? facing(Blocks.CHEST, Direction.SOUTH) : barrel());
			}
			s(hu, 1, 3, barrel());
			s(hu, 2, 3, b(Blocks.COAL_BLOCK));
			// tool chests on the stair wall ahead of the flight, if the nave is deep enough
			for (int v = 3; v <= vm - 7; v++) {
				s(2, 1, v, facing(Blocks.CHEST, Direction.EAST));
			}
			hang(cu, 7, 4);
			hang(hu - 3, 7, vh);
			hang(tu0, 7, vh + 2);
		}

		void stock() {
			for (int bu = 3; bu <= bw - 4; bu++) {
				switch ((bu - 3) % 4) {
					case 0, 1 -> s(bu, 1, vb - 1, facing(Blocks.CHEST, Direction.NORTH));
					case 2 -> s(bu, 1, vb - 1, b(Blocks.COAL_BLOCK)); // one high only: the loft is 4 over the floor
					default -> s(bu, 1, vb - 1, barrel());
				}
			}
			int nc = patioLocalRight ? 2 : bw - 3;
			s(nc, 1, vm + 1, b(Blocks.CRAFTING_TABLE));
			s(nc, 1, vm + 2, facing(Blocks.CHEST, patioLocalRight ? Direction.EAST : Direction.WEST));
			put(cu - 3, 4, vm + 2, lantern(true), CitadelCanvas.HANGING);
			put(Math.min(cu + 3, bw - 4), 4, vm + 2, lantern(true), CitadelCanvas.HANGING);
		}

		/** The smith's studio: bed, desk and shelves, a table with a lamp, windows on three sides. */
		void loft() {
			int y = FL + 1;
			BlockState bed = b(Blocks.BED.pick(DyeColor.RED)).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
			s(2, y, vb - 1, bed.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
			s(2, y, vb - 2, bed.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
			s(bw - 4, y, vb - 1, b(Blocks.BOOKSHELF));
			s(bw - 4, y + 1, vb - 1, b(Blocks.BOOKSHELF));
			s(bw - 5, y, vb - 1, b(Blocks.BOOKSHELF));
			s(bw - 3, y, vb - 1, facing(Blocks.LECTERN, Direction.NORTH));
			s(bw - 3, y, vm + 1, facing(Blocks.CHEST, Direction.WEST));
			int tu = Math.min(bw - 6, cu + 1);
			int tv = vm + 2;
			s(tu, y, tv, slab(Blocks.DARK_OAK_SLAB, false));
			s(tu, y + 1, tv, lantern(false));
			s(tu - 1, y, tv, stair(Blocks.DARK_OAK_STAIRS, Direction.WEST, false));
			s(tu + 1, y, tv, stair(Blocks.DARK_OAK_STAIRS, Direction.EAST, false));
		}

		// ------------------------------------------------------------------ the front: apron, steps, loading beam

		void front() {
			int half = gw / 2;
			for (int bu = 1; bu <= bw - 2; bu++) {
				s(bu, 0, 1, p.hash(mu(bu), 0, 1, 3) == 0 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
				int dist = Math.abs(bu - cu);
				if (dist <= half + 1) {
					s(bu, 0, 0, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false)); // [function] the loading ramp
				} else if (bu == 1 || bu == bw - 2) {
					s(bu, 0, 0, wall(Blocks.COBBLED_DEEPSLATE_WALL, false, false, false, false, true));
					s(bu, 1, 0, lantern(false));
				} else if (p.hash(mu(bu), 0, 0, 3) == 0) {
					s(bu, 0, 0, barrel());
				}
			}
			// two lanterns hang from the gate lintel, either side of the way in
			put(cu - half, 4, VF, lantern(true), CitadelCanvas.HANGING);
			put(cu + half, 4, VF, lantern(true), CitadelCanvas.HANGING);
		}

		/** [depth] stone piers at the posts, with a sloped cap under the eave; the yard door gets its step. */
		void buttresses() {
			for (int v = VF; v <= vb; v++) {
				if ((v - VF) % 4 != 0) {
					continue;
				}
				pier(0, v, Direction.EAST);
				pier(bw - 1, v, Direction.WEST);
			}
			for (int bu = 1; bu <= bw - 2; bu++) {
				boolean post = bu == 1 || bu == bw - 2 || (bu - 1) % 4 == 0;
				if (post && (bu == 1 || bu == bw - 2 || Math.abs(bu - cu) > gw / 2 + 1)) {
					pier(bu, d - 1, Direction.NORTH);
				}
				boolean jambOrGate = Math.abs(bu - cu) <= gw / 2 + 1;
				if ((bu == 1 || bu == bw - 2) && !jambOrGate) {
					pier(bu, 1, Direction.SOUTH);
				}
			}
			if (dv >= 0) {
				int ext = patioLocalRight ? bw - 1 : 0;
				s(ext, 0, dv, stair(Blocks.STONE_BRICK_STAIRS, patioLocalRight ? Direction.WEST : Direction.EAST, false));
			}
		}

		void pier(int bu, int v, Direction toWall) {
			int y0 = v == 1 ? 1 : 0; // on the apron the pier stands on its paving
			int top = roofY(bu) - 2; // up to just under the roof, so there is no ledge under the eave
			for (int y = y0; y < top; y++) {
				s(bu, y, v, y <= y0 + 1 ? base(bu, y, v) : body(bu, y, v));
			}
			s(bu, top, v, stair(Blocks.STONE_BRICK_STAIRS, toWall, false));
		}

		// ------------------------------------------------------------------ the side yard

		int pl(int pu) {
			return patioRight ? bw + pu : pw - 1 - pu;
		}

		void ps(int pu, int y, int v, BlockState st) {
			p.set(c, pl(pu), y, v, patioRight ? st : st.mirror(Mirror.FRONT_BACK));
		}

		void patio() {
			int v0 = 4; // first row of the cart bed
			int pc = pw / 2; // the cart's column
			int vmid = v0 + 1;
			int gapEnd = Math.min(pc + 1, pw - 2);
			boolean[][] fn = new boolean[pw][d];
			for (int pu = gapEnd + 1; pu < pw; pu++) {
				fn[pu][0] = true;
			}
			for (int v = 1; v <= v0 + 3; v++) {
				fn[pw - 1][v] = true;
			}
			for (int v = d - 2; v <= d - 1; v++) {
				fn[pw - 1][v] = true;
			}
			for (int pu = 0; pu < pw; pu++) {
				fn[pu][d - 1] = true;
			}
			if (pc - 1 >= 0) {
				fn[pc - 1][vmid] = false;
			}
			if (pc + 1 < pw) {
				fn[pc + 1][vmid] = false;
			}
			for (int pu = 0; pu < pw; pu++) {
				for (int v = 0; v < d; v++) {
					if (fn[pu][v]) {
						boolean n = v > 0 && fn[pu][v - 1];
						boolean sd = v < d - 1 && fn[pu][v + 1];
						boolean e = pu < pw - 1 && fn[pu + 1][v];
						boolean wv = pu > 0 && fn[pu - 1][v];
						ps(pu, 0, v, fence(Blocks.DARK_OAK_FENCE, n, e, sd, wv));
					}
				}
			}
			// a lantern post at the corner of the gate
			ps(pw - 1, 0, 0, wall(Blocks.COBBLED_DEEPSLATE_WALL, false, false, true, false, true));
			ps(pw - 1, 1, 0, lantern(false));
			// slab pavers from the gate to the yard door
			int last = dv >= 0 ? dv - 1 : d - 3;
			for (int v = 1; v <= last; v++) {
				if (v == vmid) {
					continue;
				}
				ps(0, 0, v, slab(p.hash(pl(0), 0, v, 2) == 0 ? Blocks.COBBLESTONE_SLAB : Blocks.STONE_BRICK_SLAB, false));
			}
			// the cart: bed, axle, two wheels, shafts towards the street, a load of barrels and hay
			for (int v = v0; v <= v0 + 2; v++) {
				ps(pc, 1, v, b(Blocks.SPRUCE_PLANKS));
			}
			ps(pc, 0, vmid, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
			for (int y = 0; y <= 1; y++) {
				if (pc - 1 >= 0) {
					ps(pc - 1, y, vmid, trap(Blocks.DARK_OAK_TRAPDOOR, Direction.WEST, true, false));
				}
				if (pc + 1 < pw) {
					ps(pc + 1, y, vmid, trap(Blocks.DARK_OAK_TRAPDOOR, Direction.EAST, true, false));
				}
			}
			ps(pc, 1, v0 - 1, fence(Blocks.DARK_OAK_FENCE, true, false, true, false));
			ps(pc, 1, v0 - 2, fence(Blocks.DARK_OAK_FENCE, false, false, true, false));
			ps(pc, 0, v0 - 2, fence(Blocks.DARK_OAK_FENCE, false, false, false, false));
			ps(pc, 2, v0, barrel());
			ps(pc, 3, v0, barrel());
			ps(pc, 2, v0 + 1, barrel());
			ps(pc, 2, v0 + 2, b(Blocks.HAY_BLOCK));
			// the wood pile along the outer edge
			for (int k = 0; k < (pw >= 5 ? 2 : 1); k++) {
				int pu = pw - 1 - k;
				for (int v = v0 + 4; v <= d - 3; v++) {
					if ((v - (v0 + 4)) % 4 == 3) {
						continue;
					}
					int hh = 2 + p.hash(pl(pu), 0, v, 2);
					for (int y = 0; y < hh; y++) {
						ps(pu, y, v, log(p.hash(pl(pu), y, v, 3) == 0 ? Blocks.SPRUCE_LOG : Blocks.DARK_OAK_LOG, Direction.Axis.Z));
					}
					if (p.hash(pl(pu), hh, v, 2) == 0) {
						ps(pu, hh, v, trap(Blocks.SPRUCE_TRAPDOOR, Direction.NORTH, false, false));
					}
				}
			}
			// a chopping block and a hay bale at the back of the yard
			ps(Math.max(1, pw / 2), 0, d - 3, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
			if (pw >= 4) {
				ps(1, 0, d - 2, b(Blocks.HAY_BLOCK));
			}
		}
	}
}
