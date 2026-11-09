package dev.t3craft.hq.citadel.hub;

import dev.t3craft.hq.HqStyle;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelHub;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WallSide;
import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.RIB;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.CitadelCanvas.y0;

/**
 * The archive: the hall under the nave's floor. It used to be a closed, empty room the walkable floor floated over;
 * now it is the place the hub's threads are filed.
 *
 * <ul>
 * <li><b>Depth (struct/hub3):</b> from a podium of 5 the floor of the archive is sunk below the ground
 *     ({@link HubDims#ARCH_FLOOR()}: -3 at scale 2) and the earth over it is dug out, so the hall has 8 free blocks
 *     under the nave's floor (7 under the ceiling panels) instead of 5, and its doors and stairs match the nave above.
 *     {@code y0} (the podium the gallery and the bridges share) is not touched. At scale 1 the floor stays at y = 0.</li>
 * <li><b>Access:</b> two stairs, one in each aisle (|x| from {@link HubDims#ARCH_X0()} to
 *     {@link HubDims#ARCH_X1()}, from z = {@link HubDims#ARCH_Z0()}), 5 wide at scale 2, going down towards +z. The first step is
 *     flush with the nave's floor (a stair of the nave's own height, back to the north), every cell drops one block, a
 *     flat landing of two cells splits a long run, and the foot leaves onto the archive floor. The floor of the nave is
 *     opened over the run (free headroom all the way), the sides are walled down to the archive floor and capped by low
 *     walls at the nave's level.</li>
 * <li><b>Structure:</b> the nave's piers continue down (blackstone foot, cherry shaft) and stripped-log beams
 *     join them under the pier lines, so the floor reads as held up, not hovering.</li>
 * <li><b>Use:</b> the walls are stacks (bookshelves in panels split by dark oak pilasters, with chests,
 *     barrels and workstations and a dark oak cornice), the long walls have runs of three reading desks (dark oak slabs, a
 *     lantern, a chair), the centre has a map table with four chairs on a pale patch under the oculus, the shelf islands
 *     (four high) flank the cross lanes with their gaps on the pier lines, the north quadrants are reading rooms (long
 *     consultation tables with chairs), every quadrant has lecterns facing the shelves, and the foot of each stair has
 *     a front desk (counter, two console terminals, chests and barrels behind it). Palette: deepslate, dark oak,
 *     bookshelves, calcite/quartz, warm light.</li>
 * <li><b>Mouths:</b> a dark runner with glow strips on the nave floor and an arch over the head of each stair, with a
 *     plaque on each of its four faces (north, south, and the outside of both posts).</li>
 * <li><b>Light:</b> glow panels set into the ceiling on a 4-cell lattice (at scale 1, where the hall is low, hanging lanterns
 *     as before) plus invisible fill wherever a free floor cell is more than 4 steps from one.</li>
 * </ul>
 *
 * No anchors, desks or fixtures: this is furniture, run after {@link HubInterior#furnish}.
 */
public final class HubUndercroft {
	private HubUndercroft() {
	}

	public static void build(CitadelCanvas c) {
		int ext = (int) Math.ceil(c.g.hubOut);
		int fy = fy();
		for (int x = -ext; x <= ext; x++) {
			for (int z = -ext; z <= ext; z++) {
				if (!inside(c, x, z)) {
					continue;
				}
				c.set(x, fy, z, floorBlock(x, z));
				for (int y = fy + 1; y <= 0; y++) {
					c.set(x, y, z, AIR); // the hall is dug into the earth
				}
				if (edge(c, x, z)) {
					stack(c, x, z);
				} else if (Math.abs(x) == HubDims.NAVE_HALF() && CitadelHub.arcadeZ(z)) {
					pillar(c, x, z);
				} else if (CitadelHub.arcadeZ(z) && !stairCell(x, z)) {
					c.set(x, y0() - 1, z, HubBlocks.strippedLog(Direction.Axis.X));
				} else if (Math.abs(x) == HubDims.NAVE_HALF() && Math.abs(z) <= HubDims.PILASTER_Z() + c.s(4)) {
					c.set(x, y0() - 1, z, HubBlocks.strippedLog(Direction.Axis.Z));
				}
			}
		}
		desks(c);
		deskRows(c);
		islands(c);
		indexTable(c);
		quadrants(c);
		oculus(c);
		descents(c);
		rails(c);
		mouths(c);
		lights(c);
	}

	// ------------------------------------------------------------------ helpers

	/** y of the archive's floor block. */
	private static int fy() {
		return HubDims.ARCH_FLOOR();
	}

	/** y of the archive's walking cells (the cell above the floor). */
	private static int sy() {
		return HubDims.ARCH_FLOOR() + 1;
	}

	/** An interior column of the hub (the zone map's {@code HUB}), i.e. one with archive under it. */
	private static boolean inside(CitadelCanvas c, int x, int z) {
		return c.zoneAt(x, z).kind() == Kind.HUB;
	}

	/** Whether a side of the column touches the wall (a column that is not interior). */
	private static boolean edge(CitadelCanvas c, int x, int z) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (!inside(c, x + d.getStepX(), z + d.getStepZ())) {
				return true;
			}
		}
		return false;
	}

	/** The side of an edge column that faces the room. */
	private static Direction inward(CitadelCanvas c, int x, int z) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (inside(c, x + d.getStepX(), z + d.getStepZ())) {
				return d;
			}
		}
		return Direction.NORTH;
	}

	/** The columns of the two stair runs (the floor above is open there). */
	private static boolean stairCell(int x, int z) {
		int ax = Math.abs(x);
		return ax >= HubDims.ARCH_X0() && ax <= HubDims.ARCH_X1() && z >= HubDims.ARCH_Z0() && z < HubDims.ARCH_Z0() + HubDims.ARCH_RUN();
	}

	private static boolean free(CitadelCanvas c, int x, int y, int z) {
		BlockState s = c.at(x, y, z);
		return s == null || s.isAir();
	}

	/** Sets a piece of furniture only on an interior column and an empty cell. */
	private static void put(CitadelCanvas c, int x, int y, int z, BlockState s) {
		if (inside(c, x, z) && free(c, x, y, z)) {
			c.set(x, y, z, s);
		}
	}

	// ------------------------------------------------------------------ floor, piers, stacks

	/** Dark tiled floor with a cross of light stone brick lanes along the axes (the walking lines to the stairs and the index table). */
	private static BlockState floorBlock(int x, int z) {
		boolean lane = Math.abs(x) <= 1 || Math.abs(z) <= 1;
		return lane ? HubBlocks.stoneBrick(x, fy(), z) : HubBlocks.darkBase(x, fy(), z);
	}

	/** The nave's pier continued down to the floor: blackstone foot and a cherry shaft up to the nave floor. */
	private static void pillar(CitadelCanvas c, int x, int z) {
		c.set(x, sy(), z, RIB);
		for (int y = sy() + 1; y <= y0() - 2; y++) {
			c.set(x, y, z, LOG);
		}
		c.set(x, y0() - 1, z, CALCITE); // the capital, as on the nave's piers
	}

	/**
	 * A column of the wall: shelves up to {@code y0() - 2} under a dark oak cornice, in bays of 8 along the wall:
	 * a dark oak pilaster at 0 and 4 (so the shelves read as panels of two or three), a workstation with a lantern
	 * at 2, a chest at 5 and a barrel at 7 (shelves above them), plain shelves elsewhere.
	 */
	private static void stack(CitadelCanvas c, int x, int z) {
		Direction in = inward(c, x, z);
		int u = Math.floorMod(HubFacade.along(x, z), 8);
		int sy = sy();
		int top = Math.max(sy, y0() - 2);
		boolean pilaster = u == 0 || u == 4;
		for (int y = sy; y <= top; y++) {
			c.set(x, y, z, pilaster ? HubBlocks.darkOakLog() : Blocks.BOOKSHELF.defaultBlockState());
		}
		if (u == 2) {
			int h = Math.floorMod(x * 3 + z * 7, 3);
			c.set(x, sy, z, (h == 0 ? Blocks.CRAFTING_TABLE : h == 1 ? Blocks.CARTOGRAPHY_TABLE : Blocks.SMITHING_TABLE).defaultBlockState());
			if (top - sy >= 2) {
				c.set(x, sy + 1, z, Blocks.LANTERN.defaultBlockState());
			}
		} else if (u == 5) {
			c.set(x, sy, z, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, in));
		} else if (u == 7) {
			c.set(x, sy, z, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		}
		if (y0() - 1 > top) {
			c.set(x, y0() - 1, z, HubBlocks.stairsTop(Blocks.DARK_OAK_STAIRS, in.getOpposite()));
		}
	}

	// ------------------------------------------------------------------ furniture

	/**
	 * A reading desk cell: dark oak slab top (a table at the height of a block) or, every fourth, a lantern on it.
	 * Desks come in runs of three cells with a gap of one between runs; the middle cell of a run has the chair.
	 */
	private static void deskCell(CitadelCanvas c, int x, int z, int phase, Direction out, int cx, int cz) {
		if (!inside(c, x, z) || !free(c, x, sy(), z)) {
			return;
		}
		int m = Math.floorMod(phase, 4);
		if (m == 0) {
			return; // the gap between runs
		}
		c.set(x, sy(), z, HubBlocks.slabTop(Blocks.DARK_OAK_SLAB));
		if (m == 1) {
			put(c, x, sy() + 1, z, Blocks.LANTERN.defaultBlockState());
		}
		if (m == 2) {
			put(c, cx, sy(), cz, stairs(Blocks.DARK_OAK_STAIRS, out.getOpposite()));
		}
	}

	/** Reading desks along the east and west walls, in runs of three with a chair on the middle one. */
	private static void desks(CitadelCanvas c) {
		int dx = HubDims.INNER() - 1;
		int zFar = HubDims.INNER() - 3;
		int zNear = c.s(4);
		for (int sx = -1; sx <= 1; sx += 2) {
			Direction out = sx > 0 ? Direction.EAST : Direction.WEST;
			for (int z = -zFar; z <= -zNear; z++) {
				deskCell(c, sx * dx, z, z, out, sx * (dx - 1), z);
			}
		}
	}

	/** Desks along the north and south walls, either side of the centre lane: the same runs. */
	private static void deskRows(CitadelCanvas c) {
		int dz = HubDims.INNER() - 1;
		int lane = c.s(1) + 1;
		int xMax = HubDims.NAVE_HALF() - 1;
		for (int sz = -1; sz <= 1; sz += 2) {
			Direction out = sz > 0 ? Direction.SOUTH : Direction.NORTH;
			for (int ax = lane; ax <= xMax; ax++) {
				for (int sx = -1; sx <= 1; sx += 2) {
					deskCell(c, sx * ax, sz * dz, ax, out, sx * ax, sz * (dz - 1));
				}
			}
		}
	}

	/**
	 * The layout of the shelf islands: {@code {x0, seg, start0, step}}. From scale 2 the islands are 5 long and start at
	 * |z| = 6, so the cross aisle between two segments is the three cells round a pier line (z = 11 to 13, 19 to 21 at
	 * scale 2: the piers stand at z = 12 and 20) and the patch of the index table plus a margin of two cells is the
	 * lane before the first one. Below scale 2 the old layout (segments of s(3), start s(2) + 1) is kept.
	 */
	private static int[] islandLayout(CitadelCanvas c) {
		int x0 = c.s(2) + 1;
		if (c.s(1) >= 2) {
			return new int[] {x0, 5, 6, 8};
		}
		int seg = c.s(3);
		return new int[] {x0, seg, c.s(2) + 1, seg + 3};
	}

	/**
	 * Free-standing shelf islands in the four quadrants: two columns of bookshelves running along z at
	 * {@code |x| = s(2) + 1} and {@code + 1}, in segments with a cross aisle of three between them (so every lane round
	 * them is at least 3 wide), as high as the hall lets them (4 from scale 2, else 1). A lantern stands on the middle
	 * of each segment; the cells of the stairs and the index patch stay free.
	 */
	private static void islands(CitadelCanvas c) {
		int[] lay = islandLayout(c);
		int x0 = lay[0];
		int seg = lay[1];
		int sy = sy();
		int height = Math.min(4, Math.max(1, HubDims.ARCH_FREE() - 3));
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				for (int start = lay[2]; start <= HubDims.INNER() - seg; start += lay[3]) {
					if (c.s(1) >= 2) {
						// a brown rug under and round each island, so the shelves stand on something
						for (int k = -1; k <= seg; k++) {
							for (int dx = -1; dx <= 2; dx++) {
								int x = sx * (x0 + dx);
								int z = sz * (start + k);
								if (inside(c, x, z) && !edge(c, x, z) && !stairCell(x, z)) {
									c.set(x, fy(), z, CitadelCanvas.terracotta(DyeColor.BROWN));
								}
							}
						}
					}
					for (int k = 0; k < seg; k++) {
						int z = sz * (start + k);
						for (int dx = 0; dx <= 1; dx++) {
							int x = sx * (x0 + dx);
							if (!inside(c, x, z) || edge(c, x, z) || stairCell(x, z) || !free(c, x, sy, z)) {
								continue;
							}
							for (int y = sy; y < sy + height; y++) {
								c.set(x, y, z, Blocks.BOOKSHELF.defaultBlockState());
							}
							if (k == seg / 2 && dx == 0) {
								c.set(x, sy + height, z, Blocks.LANTERN.defaultBlockState());
							}
						}
					}
				}
			}
		}
	}

	/**
	 * The quadrants of the hall (from scale 2; below it the hall is too small and stays as it was):
	 * <ul>
	 * <li>north: two long consultation tables per aisle (dark oak slabs, two wide, five long, a lantern in the middle and
	 *     a chair on each long side) in the aisle between the pier line and the desks of the wall;</li>
	 * <li>all four: a lectern (atril) at each island, facing it;</li>
	 * <li>south: the front desk at the foot of each stair: a counter across the stair's width, two console terminals on
	 *     it facing the people who come down, and behind it chests and barrels.</li>
	 * </ul>
	 */
	private static void quadrants(CitadelCanvas c) {
		if (c.s(1) < 2) {
			return;
		}
		int sy = sy();
		int n = HubDims.NAVE_HALF();
		int[] lay = islandLayout(c);
		for (int sx = -1; sx <= 1; sx += 2) {
			Direction away = sx > 0 ? Direction.EAST : Direction.WEST;
			Direction toCentre = away.getOpposite();
			for (int v0 : new int[] {c.s(4), c.s(8)}) {
				// a red rug under each reading table (the nave's own rug colour), one cell wider than the table and its chairs
				for (int dz = -1; dz <= 5; dz++) {
					for (int u = n + 3; u <= n + 6; u++) {
						if (inside(c, sx * u, -(v0 + dz))) {
							c.set(sx * u, fy(), -(v0 + dz), CitadelCanvas.terracotta(DyeColor.RED));
						}
					}
				}
				for (int dz = 0; dz < 5; dz++) {
					int z = -(v0 + dz);
					for (int u : new int[] {n + 4, n + 5}) {
						put(c, sx * u, sy, z, HubBlocks.slabTop(Blocks.DARK_OAK_SLAB));
					}
					if (dz == 2) {
						put(c, sx * (n + 4), sy + 1, z, Blocks.LANTERN.defaultBlockState());
					}
					if (dz == 1 || dz == 3) {
						put(c, sx * (n + 3), sy, z, stairs(Blocks.DARK_OAK_STAIRS, toCentre));
						put(c, sx * (n + 6), sy, z, stairs(Blocks.DARK_OAK_STAIRS, away));
					}
				}
			}
			for (int sz = -1; sz <= 1; sz += 2) {
				for (int start = lay[2]; start <= HubDims.INNER() - lay[1]; start += lay[3]) {
					int z = sz * (start + lay[1] / 2);
					put(c, sx * (lay[0] + 3), sy, z, Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, toCentre));
				}
			}
			frontDesk(c, sx);
		}
	}

	/** The front desk at the foot of the stair on side {@code sx}: counter, two consoles, chests and barrels behind it. */
	private static void frontDesk(CitadelCanvas c, int sx) {
		int sy = sy();
		int z = HubDims.ARCH_Z0() + HubDims.ARCH_RUN() + 3;
		int a = HubDims.ARCH_X0();
		int b = HubDims.ARCH_X1();
		for (int ax = a - 1; ax <= b + 1; ax++) {
			for (int zz = z - 1; zz <= z + 2; zz++) {
				if (inside(c, sx * ax, zz) && !edge(c, sx * ax, zz)) {
					c.set(sx * ax, fy(), zz, CitadelCanvas.terracotta(DyeColor.RED));
				}
			}
		}
		for (int ax = a; ax <= b; ax++) {
			int x = sx * ax;
			int i = ax - a;
			boolean console = i == 1 || i == b - a - 1;
			put(c, x, sy, z, console ? c.kit.console(Direction.NORTH) : HubBlocks.slabTop(Blocks.DARK_OAK_SLAB));
			put(c, x, sy, z + 1, i % 2 == 0
					? Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
					: Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		}
	}

	/**
	 * The oculus: clear glass in the nave's floor under the hologram (the light the lantern drum lets down), a
	 * calcite frame round it and a warm glow strip along the frame on the nave side, so it reads as a window of
	 * light and not as a grey-blue patch.
	 */
	private static void oculus(CitadelCanvas c) {
		int h = c.s(1) + 1;
		for (int x = -h - 1; x <= h + 1; x++) {
			for (int z = -h - 1; z <= h + 1; z++) {
				if (!inside(c, x, z)) {
					continue;
				}
				boolean ring = Math.max(Math.abs(x), Math.abs(z)) == h + 1;
				if (ring) {
					c.set(x, y0(), z, CALCITE);
				} else if (!free(c, x, y0(), z)) {
					c.set(x, y0(), z, Blocks.GLASS.defaultBlockState());
				}
			}
		}
		for (int x = -h - 1; x <= h + 1; x++) {
			for (int z = -h - 1; z <= h + 1; z++) {
				if (inside(c, x, z) && Math.max(Math.abs(x), Math.abs(z)) == h + 1 && free(c, x, y0() + 1, z)) {
					Direction toward = Math.abs(x) >= Math.abs(z) ? (x > 0 ? Direction.WEST : Direction.EAST) : (z > 0 ? Direction.NORTH : Direction.SOUTH);
					c.set(x, y0() + 1, z, c.kit.glowStrip(toward, false, false));
				}
			}
		}
	}

	/**
	 * The index table at the centre, on the patch of pale floor the oculus lights: a map table with a ring of dark
	 * oak slabs round it and a chair on each side. (Dark on pale, so its outline reads.)
	 */
	private static void indexTable(CitadelCanvas c) {
		int h = c.s(1) + 1;
		int sy = sy();
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				if (inside(c, x, z)) {
					boolean border = Math.max(Math.abs(x), Math.abs(z)) == h;
					c.set(x, fy(), z, border ? RIB : ((x + z) & 1) == 0 ? Blocks.SMOOTH_QUARTZ.defaultBlockState() : CALCITE);
				}
			}
		}
		put(c, 0, sy, 0, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				if (x != 0 || z != 0) {
					put(c, x, sy, z, HubBlocks.slabTop(Blocks.DARK_OAK_SLAB));
				}
			}
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			put(c, d.getStepX() * 2, sy, d.getStepZ() * 2, stairs(Blocks.DARK_OAK_STAIRS, d));
		}
	}

	// ------------------------------------------------------------------ stairs and rails

	/**
	 * The two stairs, as a proper flight: for each cell of the run the nave floor is opened and a stone-brick stair
	 * (the tall back to the north, the way up) stands half a block lower than the one before it, the first one flush with
	 * the nave's floor (its back is at the floor's level, so the way down starts with a half step). In the middle of a long
	 * run a landing of {@link HubDims#ARCH_LAND()} flat cells (a stone-brick slab at the level of the step before it)
	 * lets you stop. Below each cell the stair is solid down to the archive floor, the two sides are walled to the nave's
	 * floor, and the foot leaves onto the archive floor with a half step. Headroom above is the nave itself, so there is
	 * nothing to duck under.
	 */
	private static void descents(CitadelCanvas c) {
		int steps = HubDims.ARCH_STEPS();
		int land = HubDims.ARCH_LAND();
		int first = steps / 2;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int j = 0; j < HubDims.ARCH_RUN(); j++) {
				int z = HubDims.ARCH_Z0() + j;
				boolean flat = land > 0 && j >= first && j < first + land;
				int i = j < first ? j : flat ? first - 1 : j - land;
				int level = y0() - i;
				for (int ax = HubDims.ARCH_X0(); ax <= HubDims.ARCH_X1(); ax++) {
					int x = sx * ax;
					if (!inside(c, x, z)) {
						continue;
					}
					for (int y = fy() + 1; y < level; y++) {
						c.set(x, y, z, j == 0 ? wellFace(ax, x, y, z) : HubBlocks.darkBase(x, y, z));
					}
					for (int y = level + 1; y <= y0(); y++) {
						c.set(x, y, z, AIR);
					}
					c.set(x, level, z, flat ? HubBlocks.stoneSlab(false) : HubBlocks.stoneStairs(Direction.NORTH, false));
					for (int y = y0() + 1; y <= y0() + 2; y++) {
						BlockState s = c.at(x, y, z);
						if (s != null && !s.isAir() && !s.is(Blocks.LIGHT)) {
							c.set(x, y, z, AIR);
						}
					}
				}
				for (int edgeX : new int[] {HubDims.ARCH_X0() - 1, HubDims.ARCH_X1() + 1}) {
					int x = sx * edgeX;
					if (inside(c, x, z) && !edge(c, x, z)) {
						for (int y = fy() + 1; y <= y0() - 1; y++) {
							c.set(x, y, z, wellWall(x, y, z));
						}
					}
				}
			}
		}
	}

	/**
	 * A cell of the side wall of a stairwell, under the nave's floor. Where the hall is tall (scale 2) it is not a blank
	 * panel: a polished blackstone pilaster every 4 cells, bookshelves between them for the first three rows (they face the
	 * hall and the stairs alike), a cherry band over them and a cherry beam under the nave's floor.
	 */
	private static BlockState wellWall(int x, int y, int z) {
		if (HubDims.ARCH_FREE() < 5) {
			return HubBlocks.darkBase(x, y, z);
		}
		int rel = y - fy();
		if (y == y0() - 1) {
			return HubBlocks.strippedLog(Direction.Axis.Z);
		}
		if (Math.floorMod(z, 4) == 0) {
			return RIB;
		}
		if (rel <= 3) {
			return Blocks.BOOKSHELF.defaultBlockState();
		}
		return rel == 4 ? HubBlocks.strippedLog(Direction.Axis.Z) : HubBlocks.darkBase(x, y, z);
	}

	/**
	 * The north face of a stair's solid base, the wall that closes the stairwell towards the hall: a pilaster at each edge
	 * and shelves (four rows) in the three cells between, under a cherry beam.
	 */
	private static BlockState wellFace(int ax, int x, int y, int z) {
		if (HubDims.ARCH_FREE() < 5) {
			return HubBlocks.darkBase(x, y, z);
		}
		int rel = y - fy();
		if (y == y0() - 1) {
			return HubBlocks.strippedLog(Direction.Axis.X);
		}
		if (ax == HubDims.ARCH_X0() || ax == HubDims.ARCH_X1()) {
			return RIB;
		}
		return rel <= 4 ? Blocks.BOOKSHELF.defaultBlockState() : HubBlocks.darkBase(x, y, z);
	}

	/** Low deepslate walls on both sides of each stair, with a lantern on the post at the top of each. */
	private static void rails(CitadelCanvas c) {
		int run = HubDims.ARCH_RUN();
		int y = y0() + 1;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int edgeX : new int[] {HubDims.ARCH_X0() - 1, HubDims.ARCH_X1() + 1}) {
				int x = sx * edgeX;
				for (int k = 0; k < run; k++) {
					int z = HubDims.ARCH_Z0() + k;
					if (!inside(c, x, z) || !free(c, x, y, z)) {
						continue;
					}
					boolean north = k > 0 || sturdy(c, x, y, z - 1, Direction.SOUTH);
					boolean south = k < run - 1 || sturdy(c, x, y, z + 1, Direction.NORTH);
					c.set(x, y, z, rail(north, south));
					if (k == 0) {
						c.set(x, y + 1, z, Blocks.LANTERN.defaultBlockState());
					}
				}
			}
		}
	}

	private static boolean sturdy(CitadelCanvas c, int x, int y, int z, Direction face) {
		BlockState s = c.at(x, y, z);
		return s != null && !s.isAir() && s.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, face);
	}

	private static BlockState rail(boolean north, boolean south) {
		return Blocks.DEEPSLATE_TILE_WALL.defaultBlockState()
				.setValue(BlockStateProperties.UP, true)
				.setValue(BlockStateProperties.NORTH_WALL, north ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.SOUTH_WALL, south ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.EAST_WALL, WallSide.NONE)
				.setValue(BlockStateProperties.WEST_WALL, WallSide.NONE);
	}

	// ------------------------------------------------------------------ the mouths of the stairs

	/**
	 * Makes the two stair mouths findable from the nave: a dark runner with a calcite edge on the floor in front
	 * of each (z from {@code ARCH_Z0 - 4} to {@code ARCH_Z0 - 1}, along the aisle) with a warm glow strip down both
	 * edges, and over the head of the stair an arch (pale posts on the rails, a stripped-log lintel with a glow
	 * strip under it, a stepped dark cap) with a plaque on each of its four faces: north (the way in), south (seen
	 * from the pews and from the stair itself), and the outside of both posts (seen along the aisle).
	 */
	private static void mouths(CitadelCanvas c) {
		int y = y0();
		int z = HubDims.ARCH_Z0();
		for (int sx = -1; sx <= 1; sx += 2) {
			int xa = Math.min(sx * HubDims.ARCH_X0(), sx * HubDims.ARCH_X1());
			int xb = Math.max(sx * HubDims.ARCH_X0(), sx * HubDims.ARCH_X1());
			for (int x = xa; x <= xb; x++) {
				for (int zz = z - 4; zz <= z - 1; zz++) {
					if (inside(c, x, zz)) {
						boolean edge = x == xa || x == xb || zz == z - 4;
						c.set(x, y, zz, edge ? CALCITE : RIB);
					}
				}
			}
			for (int zz = z - 3; zz <= z - 1; zz++) {
				if (inside(c, xa, zz) && free(c, xa, y + 1, zz)) {
					c.set(xa, y + 1, zz, c.kit.glowStrip(Direction.EAST, false, false));
				}
				if (inside(c, xb, zz) && free(c, xb, y + 1, zz)) {
					c.set(xb, y + 1, zz, c.kit.glowStrip(Direction.WEST, false, false));
				}
			}
			int pa = xa - 1;
			int pb = xb + 1;
			for (int x : new int[] {pa, pb}) {
				// pale posts with a dark capital: the opposite of the nave's piers, so the arch is not taken for one
				for (int yy = y + 1; yy <= y + 4; yy++) {
					c.set(x, yy, z, CALCITE);
				}
				c.set(x, y + 5, z, RIB);
			}
			for (int x = pa + 1; x <= pb - 1; x++) {
				c.set(x, y + 5, z, HubBlocks.strippedLog(Direction.Axis.X));
				c.set(x, y + 4, z, c.kit.glowStrip(Direction.NORTH, true, false));
			}
			// corbels under the lintel, at the posts, and a lantern hanging in the middle
			c.set(pa + 1, y + 4, z, HubBlocks.stairsTop(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
			c.set(pb - 1, y + 4, z, HubBlocks.stairsTop(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
			c.set(Math.floorDiv(pa + pb, 2), y + 3, z, HubBlocks.hangingLantern());
			for (int x = pa; x <= pb; x++) {
				c.set(x, y + 6, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
			}
			for (int x = pa + 2; x <= pb - 2; x++) {
				c.set(x, y + 7, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
			}
			int mid = Math.floorDiv(pa + pb, 2);
			plaque(c, new BlockPos(mid, y + 5, z - 1), Direction.NORTH);
			plaque(c, new BlockPos(mid, y + 5, z + 1), Direction.SOUTH);
			plaque(c, new BlockPos(pa - 1, y + 3, z), Direction.WEST);
			plaque(c, new BlockPos(pb + 1, y + 3, z), Direction.EAST);
		}
	}

	/** A plaque "Archive / stairs down" facing {@code facing} (a fixture: the light pass leaves it). */
	private static void plaque(CitadelCanvas c, BlockPos p, Direction facing) {
		c.put(p.getX(), p.getY(), p.getZ(), c.style.palette().get(HqStyle.Role.PLAQUE).setValue(BlockStateProperties.HORIZONTAL_FACING, facing), CitadelCanvas.DECOR);
		c.data.put(p.asLong(), new HqStyle.SignText(DyeColor.ORANGE, "", "Archive", "stairs down", "").signTag());
		c.fixtures.add(p.asLong());
	}

	// ------------------------------------------------------------------ light

	/**
	 * Light of the hall: glow panels set into the ceiling (the kit's {@code glow_panel}, lit face down) on a lattice of 4
	 * cells between the beams, none under the oculus; where the hall is too low for a recessed ceiling (scale 1) hanging
	 * lanterns as before. Then invisible light wherever a free floor cell is more than 4 steps from the last one placed.
	 */
	private static void lights(CitadelCanvas c) {
		int ext = (int) Math.ceil(c.g.hubOut);
		int sy = sy();
		boolean recessed = HubDims.ARCH_FREE() >= 5;
		int oc = c.s(1) + 1;
		List<int[]> lamps = new ArrayList<>();
		for (int x = -ext; x <= ext; x++) {
			for (int z = -ext; z <= ext; z++) {
				if (!inside(c, x, z) || free(c, x, y0(), z) || !free(c, x, y0() - 1, z)) {
					continue;
				}
				if (recessed) {
					boolean underOculus = Math.abs(x) <= oc && Math.abs(z) <= oc;
					if (Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 2 && !underOculus) {
						c.set(x, y0() - 1, z, c.kit.glowPanel(Direction.DOWN, false));
					}
				} else if (Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0) {
					c.set(x, y0() - 1, z, HubBlocks.hangingLantern());
					lamps.add(new int[] {x, z});
				}
			}
		}
		for (int x = -ext; x <= ext; x++) {
			for (int z = -ext; z <= ext; z++) {
				if (!inside(c, x, z) || !free(c, x, sy, z) || !free(c, x, sy + 1, z)) {
					continue;
				}
				boolean covered = false;
				for (int[] p : lamps) {
					if (Math.abs(p[0] - x) + Math.abs(p[1] - z) <= 4) {
						covered = true;
						break;
					}
				}
				if (!covered) {
					c.set(x, sy + 1, z, HubBlocks.light());
					lamps.add(new int[] {x, z});
				}
			}
		}
	}
}
