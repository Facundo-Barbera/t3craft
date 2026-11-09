package dev.t3craft.hq.citadel.hub;

import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelHub;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WallSide;
import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
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
 * <li><b>Use:</b> the walls are stacks (bookshelves two high, with chests, barrels and workstations and a
 *     cherry cornice), the long walls have reading desks with a chair and a lectern, and the centre has an index
 *     table with four chairs on a cross of light stone lanes in the dark tiled floor.</li>
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
		indexTable(c);
		oculus(c);
		descents(c);
		rails(c);
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
	 * A column of the wall: bookshelves up to {@code y0() - 2} under a cherry cornice, in bays of 8 along the wall:
	 * a chest (bay 3), a workstation with a lantern on it (bay 4), a barrel (bay 7), the rest shelves.
	 */
	private static void stack(CitadelCanvas c, int x, int z) {
		Direction in = inward(c, x, z);
		int u = Math.floorMod(HubFacade.along(x, z), 8);
		int top = Math.max(1, y0() - 2);
		for (int y = 1; y <= top; y++) {
			c.set(x, y, z, Blocks.BOOKSHELF.defaultBlockState());
		}
		if (u == 3) {
			c.set(x, 1, z, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, in));
		} else if (u == 4) {
			int h = Math.floorMod(x * 3 + z * 7, 3);
			c.set(x, 1, z, (h == 0 ? Blocks.CRAFTING_TABLE : h == 1 ? Blocks.CARTOGRAPHY_TABLE : Blocks.SMITHING_TABLE).defaultBlockState());
			if (top >= 2) {
				c.set(x, 2, z, Blocks.LANTERN.defaultBlockState());
			}
		} else if (u == 7) {
			c.set(x, 1, z, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		}
		if (y0() - 1 > top) {
			c.set(x, y0() - 1, z, HubBlocks.stairsTop(Blocks.CHERRY_STAIRS, in.getOpposite()));
		}
	}

	// ------------------------------------------------------------------ furniture

	/** Reading desks along the east and west walls: a cherry slab desk with a chair and a lectern, one reader per two cells. */
	private static void desks(CitadelCanvas c) {
		int dx = HubDims.INNER() - 1;
		int zFar = HubDims.INNER() - 3;
		int zNear = c.s(4);
		for (int sx = -1; sx <= 1; sx += 2) {
			Direction out = sx > 0 ? Direction.EAST : Direction.WEST;
			for (int z = -zFar; z <= -zNear; z++) {
				if (!inside(c, sx * dx, z) || !free(c, sx * dx, 1, z)) {
					continue;
				}
				boolean seat = Math.floorMod(z, 2) == 1;
				boolean lectern = seat && Math.floorMod(Math.floorDiv(z, 2), 2) == 0;
				c.set(sx * dx, 1, z, lectern
						? Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, out.getOpposite())
						: slab(Blocks.CHERRY_SLAB));
				if (seat) {
					put(c, sx * (dx - 1), 1, z, stairs(Blocks.CHERRY_STAIRS, out.getOpposite()));
				}
			}
		}
	}

	/** Desks along the north and south walls, either side of the centre lane: the same desk, chair and lectern. */
	private static void deskRows(CitadelCanvas c) {
		int dz = HubDims.INNER() - 1;
		int lane = c.s(1) + 1;
		int xMax = HubDims.NAVE_HALF() - 1;
		for (int sz = -1; sz <= 1; sz += 2) {
			Direction out = sz > 0 ? Direction.SOUTH : Direction.NORTH;
			for (int ax = lane; ax <= xMax; ax++) {
				for (int sx = -1; sx <= 1; sx += 2) {
					int x = sx * ax;
					if (!inside(c, x, sz * dz) || !free(c, x, 1, sz * dz)) {
						continue;
					}
					boolean seat = ax % 2 == 1;
					boolean lectern = seat && (ax / 2) % 2 == 0;
					c.set(x, 1, sz * dz, lectern
							? Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, out.getOpposite())
							: slab(Blocks.CHERRY_SLAB));
					if (seat) {
						put(c, x, 1, sz * (dz - 1), stairs(Blocks.CHERRY_STAIRS, out.getOpposite()));
					}
				}
			}
		}
	}

	/** A glass oculus in the nave's floor under the hologram: the index table and its lantern show through it. */
	private static void oculus(CitadelCanvas c) {
		int h = c.s(1);
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				if (inside(c, x, z) && !free(c, x, y0(), z)) {
					c.set(x, y0(), z, HubBlocks.glass(net.minecraft.world.item.DyeColor.LIGHT_BLUE));
				}
			}
		}
	}

	/** The index table at the centre: a slab table with a chair on each side, under the hanging lantern of the lattice. */
	private static void indexTable(CitadelCanvas c) {
		int h = c.s(1);
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				put(c, x, 1, z, slab(Blocks.CHERRY_SLAB));
			}
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			put(c, d.getStepX() * (h + 1), 1, d.getStepZ() * (h + 1), stairs(Blocks.CHERRY_STAIRS, d));
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
