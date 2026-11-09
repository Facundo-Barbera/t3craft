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
 * The archive: the hall under the nave's floor (y = 1 to {@code y0() - 1}). It used to be a closed, empty
 * room the walkable floor floated over; now it is the place the hub's threads are filed.
 *
 * <ul>
 * <li><b>Access:</b> two stairs, one in each aisle (|x| from {@link HubDims#ARCH_X0()} to
 *     {@link HubDims#ARCH_X1()}, from z = {@link HubDims#ARCH_Z0()}), 3 wide, one cell per block of drop, going
 *     down towards +z. The floor is opened over the run (free headroom all the way), the sides are low walls
 *     and a lantern marks the top of each rail. The foot of the stair is the archive floor.</li>
 * <li><b>Structure:</b> the nave's piers continue down (blackstone foot, cherry shaft) and stripped-log beams
 *     join them under the pier lines, so the floor reads as held up, not hovering.</li>
 * <li><b>Use (struct/hub2):</b> the walls are stacks (bookshelves in panels split by dark oak pilasters, with chests,
 *     barrels and workstations and a dark oak cornice), the long walls have runs of three reading desks (dark oak slabs, a
 *     lantern, a chair), and the centre has a map table with four chairs on a pale patch under the oculus. Palette: deepslate,
 *     dark oak, bookshelves, calcite/quartz, lantern light.</li>
 * <li><b>Mouths:</b> a dark runner with glow strips on the nave floor and an arch with a plaque over the head of each stair.</li>
 * <li><b>Light:</b> hanging lanterns on a 4-cell lattice under the ceiling plus invisible fill wherever a free
 *     floor cell is more than 4 steps from one.</li>
 * </ul>
 *
 * No anchors, desks or fixtures: this is furniture, run after {@link HubInterior#furnish}.
 */
public final class HubUndercroft {
	private HubUndercroft() {
	}

	public static void build(CitadelCanvas c) {
		int ext = (int) Math.ceil(c.g.hubOut);
		for (int x = -ext; x <= ext; x++) {
			for (int z = -ext; z <= ext; z++) {
				if (!inside(c, x, z)) {
					continue;
				}
				c.set(x, 0, z, floorBlock(x, z));
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
		oculus(c);
		descents(c);
		rails(c);
		mouths(c);
		lights(c);
	}

	// ------------------------------------------------------------------ helpers

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
		return lane ? HubBlocks.stoneBrick(x, 0, z) : HubBlocks.darkBase(x, 0, z);
	}

	/** The nave's pier continued down to the floor: blackstone foot and a cherry shaft up to the nave floor. */
	private static void pillar(CitadelCanvas c, int x, int z) {
		c.set(x, 1, z, RIB);
		for (int y = 2; y <= y0() - 1; y++) {
			c.set(x, y, z, LOG);
		}
	}

	/**
	 * A column of the wall: shelves up to {@code y0() - 2} under a dark oak cornice, in bays of 8 along the wall:
	 * a dark oak pilaster at 0 and 4 (so the shelves read as panels of two or three), a workstation with a lantern
	 * at 2, a chest at 5 and a barrel at 7 (shelves above them), plain shelves elsewhere.
	 */
	private static void stack(CitadelCanvas c, int x, int z) {
		Direction in = inward(c, x, z);
		int u = Math.floorMod(HubFacade.along(x, z), 8);
		int top = Math.max(1, y0() - 2);
		boolean pilaster = u == 0 || u == 4;
		for (int y = 1; y <= top; y++) {
			c.set(x, y, z, pilaster ? HubBlocks.darkOakLog() : Blocks.BOOKSHELF.defaultBlockState());
		}
		if (u == 2) {
			int h = Math.floorMod(x * 3 + z * 7, 3);
			c.set(x, 1, z, (h == 0 ? Blocks.CRAFTING_TABLE : h == 1 ? Blocks.CARTOGRAPHY_TABLE : Blocks.SMITHING_TABLE).defaultBlockState());
			if (top >= 3) {
				c.set(x, 2, z, Blocks.LANTERN.defaultBlockState());
			}
		} else if (u == 5) {
			c.set(x, 1, z, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, in));
		} else if (u == 7) {
			c.set(x, 1, z, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
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
		if (!inside(c, x, z) || !free(c, x, 1, z)) {
			return;
		}
		int m = Math.floorMod(phase, 4);
		if (m == 0) {
			return; // the gap between runs
		}
		c.set(x, 1, z, HubBlocks.slabTop(Blocks.DARK_OAK_SLAB));
		if (m == 1) {
			put(c, x, 2, z, Blocks.LANTERN.defaultBlockState());
		}
		if (m == 2) {
			put(c, cx, 1, cz, stairs(Blocks.DARK_OAK_STAIRS, out.getOpposite()));
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
	 * Free-standing shelf islands in the four quadrants: two columns of bookshelves, {@code 3} high, running along z at
	 * {@code |x| = s(2) + 1} and {@code + 1}, in segments of {@code s(3)} cells with a cross aisle of three between them
	 * (so every lane round them is at least 3 wide). A lantern stands on the middle of each segment; the cells of the
	 * stairs and the index patch stay free.
	 */
	private static void islands(CitadelCanvas c) {
		int x0 = c.s(2) + 1;
		int seg = c.s(3);
		int top = Math.min(3, Math.max(1, y0() - 3));
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				for (int start = c.s(2) + 1; start <= HubDims.INNER() - seg; start += seg + 3) {
					for (int k = 0; k < seg; k++) {
						int z = sz * (start + k);
						for (int dx = 0; dx <= 1; dx++) {
							int x = sx * (x0 + dx);
							if (!inside(c, x, z) || edge(c, x, z) || stairCell(x, z) || !free(c, x, 1, z)) {
								continue;
							}
							for (int y = 1; y <= top; y++) {
								c.set(x, y, z, Blocks.BOOKSHELF.defaultBlockState());
							}
							if (k == seg / 2 && dx == 0) {
								c.set(x, top + 1, z, Blocks.LANTERN.defaultBlockState());
							}
						}
					}
				}
			}
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
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				if (inside(c, x, z)) {
					boolean border = Math.max(Math.abs(x), Math.abs(z)) == h;
					c.set(x, 0, z, border ? RIB : ((x + z) & 1) == 0 ? Blocks.SMOOTH_QUARTZ.defaultBlockState() : CALCITE);
				}
			}
		}
		put(c, 0, 1, 0, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				if (x != 0 || z != 0) {
					put(c, x, 1, z, HubBlocks.slabTop(Blocks.DARK_OAK_SLAB));
				}
			}
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			put(c, d.getStepX() * 2, 1, d.getStepZ() * 2, stairs(Blocks.DARK_OAK_STAIRS, d));
		}
	}

	// ------------------------------------------------------------------ stairs and rails

	/**
	 * The two stairs: for each cell of the run the nave floor is opened, a stone-brick stair (the tall back to the
	 * north, the way up) sits one block lower than the cell before, with solid under it. Headroom above is the nave
	 * itself, so there is nothing to duck under; the foot meets the archive floor.
	 */
	private static void descents(CitadelCanvas c) {
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int k = 0; k < HubDims.ARCH_RUN(); k++) {
				int z = HubDims.ARCH_Z0() + k;
				for (int ax = HubDims.ARCH_X0(); ax <= HubDims.ARCH_X1(); ax++) {
					int x = sx * ax;
					if (!inside(c, x, z)) {
						continue;
					}
					c.set(x, y0(), z, AIR);
					for (int y = 1; y <= y0() - 2 - k; y++) {
						c.set(x, y, z, HubBlocks.darkBase(x, y, z));
					}
					c.set(x, y0() - 1 - k, z, HubBlocks.stoneStairs(Direction.NORTH, false));
					for (int y = y0() - k; y < y0(); y++) {
						c.set(x, y, z, AIR);
					}
					for (int y = y0() + 1; y <= y0() + 2; y++) {
						BlockState s = c.at(x, y, z);
						if (s != null && !s.isAir() && !s.is(Blocks.LIGHT)) {
							c.set(x, y, z, AIR);
						}
					}
				}
			}
		}
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
	 * edges, and over the head of the stair an arch (cherry posts on the rails, a stripped-log lintel with a glow
	 * strip under it, a stepped dark cap) and a plaque saying where it goes.
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
				c.set(x, y + 1, z, RIB);
				for (int yy = y + 2; yy <= y + 4; yy++) {
					c.set(x, yy, z, LOG);
				}
				c.set(x, y + 5, z, CALCITE);
			}
			for (int x = pa + 1; x <= pb - 1; x++) {
				c.set(x, y + 5, z, HubBlocks.strippedLog(Direction.Axis.X));
				c.set(x, y + 4, z, c.kit.glowStrip(Direction.NORTH, true, false));
			}
			for (int x = pa; x <= pb; x++) {
				c.set(x, y + 6, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
			}
			for (int x = pa + 2; x <= pb - 2; x++) {
				c.set(x, y + 7, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
			}
			BlockPos sign = new BlockPos(Math.floorDiv(pa + pb, 2), y + 5, z - 1);
			c.put(sign.getX(), sign.getY(), sign.getZ(), c.style.palette().get(HqStyle.Role.PLAQUE).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), CitadelCanvas.DECOR);
			c.data.put(sign.asLong(), new HqStyle.SignText(DyeColor.ORANGE, "", "Archive", "stairs down", "").signTag());
			c.fixtures.add(sign.asLong());
		}
	}

	// ------------------------------------------------------------------ light

	/** Hanging lanterns on a 4-cell lattice, then invisible light wherever a free floor cell is more than 4 steps from a lamp. */
	private static void lights(CitadelCanvas c) {
		int ext = (int) Math.ceil(c.g.hubOut);
		List<int[]> lamps = new ArrayList<>();
		for (int x = -ext; x <= ext; x++) {
			for (int z = -ext; z <= ext; z++) {
				if (inside(c, x, z) && Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0 && !free(c, x, y0(), z) && free(c, x, y0() - 1, z)) {
					c.set(x, y0() - 1, z, HubBlocks.hangingLantern());
					lamps.add(new int[] {x, z});
				}
			}
		}
		for (int x = -ext; x <= ext; x++) {
			for (int z = -ext; z <= ext; z++) {
				if (!inside(c, x, z) || !free(c, x, 1, z) || !free(c, x, 2, z)) {
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
					c.set(x, 2, z, HubBlocks.light());
					lamps.add(new int[] {x, z});
				}
			}
		}
	}
}
