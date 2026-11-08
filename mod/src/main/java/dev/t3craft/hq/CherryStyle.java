package dev.t3craft.hq;

import static dev.t3craft.hq.HqGeometry.BOARD_HALF;
import static dev.t3craft.hq.HqGeometry.BOARD_Z;
import static dev.t3craft.hq.HqGeometry.DOOR_V1;
import static dev.t3craft.hq.HqGeometry.DOOR_V2;
import static dev.t3craft.hq.HqGeometry.HUB;
import static dev.t3craft.hq.HqGeometry.PORTICO_X;
import static dev.t3craft.hq.HqGeometry.PORTICO_Z1;
import static dev.t3craft.hq.HqGeometry.PORTICO_Z2;
import static dev.t3craft.hq.HqGeometry.ROW;
import static dev.t3craft.hq.HqGeometry.SEGMENT;
import static dev.t3craft.hq.HqGeometry.SPINE;
import static dev.t3craft.hq.HqGeometry.WING_WALL;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * "Campus de cerezo" (docs/research/hq-styles.md §C), the default style: cherry-timber pavilions on a
 * deepslate-tile footing, shoji walls (white paper panes between stripped cherry posts, calcite
 * panels behind the monitors), concave gable roofs of deepslate tiles with a blackstone ridge, a
 * two-tier hub with a glazed clerestory and a lantern cupola that holds the beacon, a torii and
 * cherry trees at the entrance, gravel, moss, bamboo and stone lanterns in the garden strips.
 *
 * <p>Built on {@link HqKit}: desks, wall and shoji panels, floor tiles, timber, lanterns, plaques,
 * railings, planters and stone lanterns are t3craft's own blocks when the blocks module is on
 * (feat/hq-blocks-kit); when it is off the style falls back to its own vanilla choices (26.3 blocks).
 * Roofs, footing, gardens and trees are vanilla in both. Leaves
 * are always {@code persistent}, there are no trapdoors on walking floors and no fences or stone
 * walls that would need block tags to connect (a stone lantern is a lone wall post).
 */
final class CherryStyle extends HqStyle {
	static final String ID = "cherry";

	/** Roof surface y and block kind by distance from the eave (wing: 0..6, 6 = ridge over the corridor). */
	private static final int[] WING_ROOF_Y = {5, 6, 6, 7, 8, 9, 9};

	private final Map<Role, BlockState> palette;

	CherryStyle() {
		Map<Role, BlockState> m = new EnumMap<>(Role.class);
		m.put(Role.FLOOR, Blocks.BAMBOO_MOSAIC.defaultBlockState());
		m.put(Role.FOOTING, Blocks.DEEPSLATE_TILES.defaultBlockState());
		m.put(Role.WALL, Blocks.CALCITE.defaultBlockState());
		m.put(Role.COLUMN, log(Blocks.CHERRY_LOG, Direction.Axis.Y));
		m.put(Role.BEAM, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X));
		m.put(Role.ROOF, stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.NORTH));
		m.put(Role.ROOF_TRIM, slab(Blocks.DEEPSLATE_TILE_SLAB, SlabType.BOTTOM));
		m.put(Role.RIDGE, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
		m.put(Role.WINDOW, Blocks.STAINED_GLASS_PANE.pick(DyeColor.WHITE).defaultBlockState());
		m.put(Role.DOOR, Blocks.CHERRY_PLANKS.defaultBlockState());
		m.put(Role.DECOR, Blocks.POTTED_CHERRY_SAPLING.defaultBlockState());
		m.put(Role.PATH, Blocks.GRAVEL.defaultBlockState());
		m.put(Role.GARDEN, Blocks.MOSS_BLOCK.defaultBlockState());
		m.put(Role.GARDEN_TREE, Blocks.CHERRY_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true));
		m.put(Role.LIGHT, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false));
		m.put(Role.DESK, slab(Blocks.CHERRY_SLAB, SlabType.TOP));
		m.put(Role.BENCH, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH));
		m.put(Role.BOARD, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
		m.put(Role.COMPUTER_ON, Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState());
		m.put(Role.COMPUTER_OFF, Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState());
		m.put(Role.STATUS_LAMP, Blocks.COPPER_BULB.waxed().unaffected().defaultBlockState().setValue(BlockStateProperties.LIT, false));
		m.put(Role.SCREEN, Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState());
		m.put(Role.BEACON, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
		m.put(Role.TERMINAL, Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		m.put(Role.PLAQUE, Blocks.CHERRY_WALL_SIGN.defaultBlockState());
		palette = Collections.unmodifiableMap(m);
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Map<Role, BlockState> palette() {
		return palette;
	}

	// ------------------------------------------------------------------ materials

	private BlockState role(Role r) {
		return palette.get(r);
	}

	private static HqKit kit() {
		return HqKit.active();
	}

	private static boolean custom() {
		return kit().custom();
	}

	private static BlockState log(Block b, Direction.Axis axis) {
		return b.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	private static BlockState stairs(Block b, Direction back) {
		return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, back).setValue(BlockStateProperties.HALF, Half.BOTTOM);
	}

	private static BlockState slab(Block b, SlabType t) {
		return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, t);
	}

	private final BlockState air = Blocks.AIR.defaultBlockState();
	private final BlockState planks = Blocks.CHERRY_PLANKS.defaultBlockState();
	private final BlockState trunk = log(Blocks.CHERRY_LOG, Direction.Axis.Y);
	private final BlockState ridgeCap = slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, SlabType.BOTTOM);
	private final BlockState stoneLanternPost = Blocks.STONE_BRICK_WALL.defaultBlockState();
	private final BlockState moss = Blocks.MOSS_CARPET.defaultBlockState();
	private final BlockState azaleaPot = Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState();
	private final BlockState shelf = Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
	private final BlockState bookshelf = Blocks.BOOKSHELF.defaultBlockState();

	private BlockState floor() {
		return custom() ? kit().floor() : role(Role.FLOOR);
	}

	/** Thresholds, doorways and the hub's border. */
	private BlockState accentFloor() {
		return custom() ? kit().floorAccent() : role(Role.DOOR);
	}

	private BlockState footing() {
		return role(Role.FOOTING);
	}

	private BlockState wall() {
		return kit().wallPanel(false);
	}

	/** Paper windows: lit shoji panels with the kit, white panes without. */
	private BlockState shoji() {
		return custom() ? kit().wallPanel(true) : role(Role.WINDOW);
	}

	private BlockState post() {
		return custom() ? kit().beam(Direction.Axis.Y) : role(Role.COLUMN);
	}

	private BlockState beam(Direction.Axis axis) {
		return custom() ? kit().beam(axis) : log(Blocks.STRIPPED_CHERRY_LOG, axis);
	}

	private BlockState roof(Direction back) {
		return stairs(Blocks.DEEPSLATE_TILE_STAIRS, back);
	}

	private BlockState roofSlab() {
		return role(Role.ROOF_TRIM);
	}

	private BlockState ridge() {
		return role(Role.RIDGE);
	}

	private BlockState leaves() {
		return role(Role.GARDEN_TREE);
	}

	private BlockState lantern(boolean hanging) {
		return hanging || !custom() ? Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, hanging) : kit().lantern();
	}

	private BlockState plaque(Direction facing) {
		return kit().plaque(facing);
	}

	private BlockState petals(int amount, Direction facing) {
		return Blocks.PINK_PETALS.defaultBlockState().setValue(BlockStateProperties.FLOWER_AMOUNT, amount)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
	}

	private BlockState bamboo(boolean top) {
		return Blocks.BAMBOO.defaultBlockState().setValue(BlockStateProperties.BAMBOO_LEAVES,
				top ? net.minecraft.world.level.block.state.properties.BambooLeaves.LARGE
						: net.minecraft.world.level.block.state.properties.BambooLeaves.NONE);
	}

	private BlockState carpet(DyeColor c) {
		return Blocks.CARPET.pick(c).defaultBlockState();
	}

	@Override
	BlockState bench(Direction back) {
		return stairs(Blocks.CHERRY_STAIRS, back);
	}

	/** A stone lantern (tōrō): the kit's, or a lone stone-brick post with a lantern on top. */
	private void stoneLantern(PlanBuilder.Canvas c, int x, int z) {
		if (custom()) {
			c.set(x, 1, z, kit().gardenLantern());
			return;
		}
		c.set(x, 1, z, stoneLanternPost);
		c.decor(x, 2, z, lantern(false));
	}

	private BlockState planter() {
		return kit().gardenPlanter();
	}

	private CompoundTag plaqueData(SignText t) {
		return kit().plaqueData(t.color(), t.l1(), t.l2(), t.l3(), t.l4());
	}

	// ------------------------------------------------------------------ hub

	@Override
	void hub(PlanBuilder.Canvas c, boolean northDoor) {
		for (int z = -HUB; z <= HUB; z++) {
			for (int x = -HUB; x <= HUB; x++) {
				int m = Math.max(Math.abs(x), Math.abs(z));
				boolean door = Math.abs(x) <= 1 && (z == HUB || (northDoor && z == -HUB));
				BlockState f = m == HUB ? footing() : m == 7 ? accentFloor() : floor();
				c.set(x, 0, z, door ? accentFloor() : f);
				if (m == HUB) {
					hubWall(c, x, z, door);
				}
				Direction in = ClassicStyle.inward(x, z);
				// lower roof: eave on the wall, a second course on the inner beam ring
				if (m == HUB) {
					c.set(x, 6, z, roof(in));
				} else if (m == 7) {
					c.set(x, 6, z, beam(Math.abs(z) == 7 ? Direction.Axis.X : Direction.Axis.Z));
					c.set(x, 7, z, roof(in));
				} else if (m == 6) {
					// clerestory: posts at corners and mid-sides, paper panes between, upper eave on top
					c.set(x, 6, z, beam(Math.abs(z) == 6 ? Direction.Axis.X : Direction.Axis.Z));
					boolean p = Math.abs(x) == 6 && Math.abs(z) == 6 || x == 0 || z == 0 || Math.abs(x) == 3 || Math.abs(z) == 3;
					c.set(x, 7, z, p ? post() : shoji());
					c.set(x, 8, z, p ? post() : shoji());
					c.set(x, 9, z, roof(in));
				} else if (m >= 2) {
					c.set(x, 10, z, Math.abs(x) == Math.abs(z) ? ridgeCap : roofSlab());
				} else if (m == 1) {
					// lantern cupola around the beacon (0,9,0)
					c.set(x, 9, z, Math.abs(x) == 1 && Math.abs(z) == 1 ? post() : shoji());
					c.set(x, 10, z, roof(in));
				}
			}
		}
		c.set(0, 10, 0, ridge());
		// lanterns hanging from the clerestory beam ring
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				c.hang(6 * sx, 5, 4 * sz, lantern(true));
				// reading corners: bookshelf, cherry bonsai on top
				c.set(7 * sx, 1, 7 * sz, bookshelf);
				c.set(7 * sx, 2, 7 * sz, bookshelf);
				c.decor(7 * sx, 3, 7 * sz, planter());
			}
			// planters at the ends of the bench rows (benches: generator, x = ±5)
			c.decor(5 * sx, 1, -3, azaleaPot);
			c.decor(5 * sx, 1, 3, azaleaPot);
			// stone lanterns by both doorways
			for (int dz = -1; dz <= 1; dz += 2) {
				stoneLantern(c, 2 * sx, 7 * dz);
			}
		}
		// a pink runner from the entrance to the board
		for (int z = 2; z <= 7; z++) {
			for (int x = -1; x <= 1; x++) {
				c.decor(x, 1, z, carpet(DyeColor.PINK));
			}
		}
	}

	private void hubWall(PlanBuilder.Canvas c, int x, int z, boolean door) {
		boolean ns = Math.abs(z) == HUB;
		int t = ns ? x : z;
		boolean post = Math.abs(t) == HUB || Math.abs(t) == 5 || Math.abs(t) == 2;
		Direction.Axis along = ns ? Direction.Axis.X : Direction.Axis.Z;
		for (int y = 1; y <= 5; y++) {
			BlockState s;
			if (post) {
				s = post();
			} else if (y == 5) {
				s = beam(along);
			} else if (door) {
				s = y == 4 ? beam(along) : air;
			} else {
				s = y == 1 ? footing() : y == 3 ? shoji() : wall();
			}
			c.set(x, y, z, s);
		}
	}

	/** The board: a dark slate notice board in a cherry frame under its own little tiled roof. */
	@Override
	void board(PlanBuilder.Canvas c, SignText sign) {
		int z = BOARD_Z;
		for (int y = 1; y <= 5; y++) {
			c.set(-BOARD_HALF - 1, y, z, post());
			c.set(BOARD_HALF + 1, y, z, post());
		}
		for (int x = -BOARD_HALF; x <= BOARD_HALF; x++) {
			c.set(x, 1, z, beam(Direction.Axis.X));
			for (int y = 2; y <= 4; y++) {
				c.set(x, y, z, role(Role.BOARD));
			}
			c.set(x, 5, z, beam(Direction.Axis.X)); // bulbs overwritten by the generator
		}
		for (int x = -BOARD_HALF - 2; x <= BOARD_HALF + 2; x++) {
			c.set(x, 6, z, Math.abs(x) == BOARD_HALF + 2 ? roofSlab() : ridge());
			c.set(x, 6, z - 1, roof(Direction.SOUTH));
			c.set(x, 6, z + 1, roof(Direction.NORTH));
		}
		c.decor(new BlockPos(0, 5, z + 1), plaque(Direction.SOUTH), plaqueData(sign));
	}

	// ------------------------------------------------------------------ archive nook

	@Override
	void archiveNook(PlanBuilder.Canvas c) {
		Box n = HqGeometry.archiveNook();
		int tx = HqGeometry.ARCHIVE_TERMINAL.getX();
		for (int x = n.minX(); x <= n.maxX(); x++) {
			for (int y = 1; y <= 4; y++) {
				c.set(x, y, n.minZ(), x == tx ? air : y == 4 ? beam(Direction.Axis.X) : shelf);
			}
		}
		c.decor(HqGeometry.ARCHIVE_TERMINAL.above(2), plaque(Direction.NORTH), plaqueData(new SignText(DyeColor.BROWN, "", "Archive", "", "")));
	}

	// ------------------------------------------------------------------ portico

	/** Entrance garden: a cherry deck (engawa) under a torii, gravel path, two cherry trees, stone lanterns. */
	@Override
	void portico(PlanBuilder.Canvas c) {
		int gate = PORTICO_Z2 - 1;
		for (int z = PORTICO_Z1; z <= PORTICO_Z2; z++) {
			for (int x = -PORTICO_X; x <= PORTICO_X; x++) {
				int ax = Math.abs(x);
				boolean deck = ax <= 3 && z < gate;
				c.set(x, 0, z, deck ? (custom() ? kit().floor() : planks) : ax <= 1 ? role(Role.PATH) : role(Role.GARDEN));
				if (!deck && ax >= 2 && !(ax == 2 && z == gate)) {
					c.decor(x, 1, z, (x + z) % 3 == 0 ? petals(1 + Math.floorMod(x * 7 + z, 4), Direction.from2DDataValue(Math.floorMod(x + z, 4)))
							: moss);
				}
			}
		}
		// the torii over the path
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int y = 1; y <= 4; y++) {
				c.set(2 * sx, y, gate, post());
			}
			c.set(3 * sx, 5, gate, ridgeCap);
			stoneLantern(c, 4 * sx, PORTICO_Z2);
			// cherry trees either side of the deck
			int tx = 4 * sx;
			int tz = PORTICO_Z1 + 1;
			for (int y = 1; y <= 3; y++) {
				c.set(tx, y, tz, trunk);
			}
			for (int x = 3; x <= 4; x++) {
				for (int z = PORTICO_Z1; z <= PORTICO_Z1 + 2; z++) {
					c.set(x * sx, 4, z, leaves());
					if (x == 4 || z == tz) {
						c.set(x * sx, 5, z, leaves());
					}
				}
			}
			c.set(tx, 6, tz, leaves());
		}
		for (int x = -1; x <= 1; x++) {
			c.set(x, 4, gate, beam(Direction.Axis.X));
		}
		for (int x = -2; x <= 2; x++) {
			c.set(x, 5, gate, ridge());
		}
	}

	// ------------------------------------------------------------------ spine

	@Override
	void spineRow(PlanBuilder.Canvas c, int r, @Nullable SignText eastSign, @Nullable SignText westSign) {
		int zn = HqGeometry.rowNorth(r);
		for (int v = 0; v < ROW; v++) {
			int z = zn + v;
			boolean beamLine = v == 0 || v == 4 || v == 8 || v == ROW - 1;
			for (int x = -SPINE; x <= SPINE; x++) {
				int ax = Math.abs(x);
				boolean wing = (x > 0 ? eastSign : westSign) != null;
				boolean door = ax == SPINE && wing && v >= DOOR_V1 && v <= DOOR_V2;
				c.set(x, 0, z, ax == SPINE ? (door ? accentFloor() : footing()) : ax <= 1 ? accentFloor() : floor());
				if (ax == SPINE) {
					for (int y = 1; y <= 4; y++) {
						BlockState s;
						if (beamLine) {
							s = post();
						} else if (y == 4) {
							s = beam(Direction.Axis.Z);
						} else if (door) {
							s = air;
						} else if (wing) {
							s = y == 1 ? footing() : wall();
						} else {
							// open gallery side (engawa): a low rail, open above
							s = y == 1 ? (custom() ? kit().railing(x < 0 ? Direction.WEST : Direction.EAST) : beam(Direction.Axis.Z)) : air;
						}
						c.set(x, y, z, s);
					}
				} else if (beamLine) {
					c.set(x, 5, z, beam(Direction.Axis.X));
				}
				spineRoof(c, x, z);
			}
			if (v == 4 || v == 8) {
				c.hang(0, 4, z, lantern(true));
			}
			if (v == 1 || v == ROW - 2) {
				c.decor(-2, 1, z, planter());
				c.decor(2, 1, z, planter());
			}
		}
		int zs = zn + (DOOR_V1 + DOOR_V2) / 2;
		if (eastSign != null) {
			c.decor(new BlockPos(2, 4, zs), plaque(Direction.WEST), plaqueData(eastSign));
		}
		if (westSign != null) {
			c.decor(new BlockPos(-2, 4, zs), plaque(Direction.EAST), plaqueData(westSign));
		}
	}

	/** The spine's gable roof (ridge along z over x = 0): eave stairs, a slab course, stairs, ridge. */
	private void spineRoof(PlanBuilder.Canvas c, int x, int z) {
		int d = SPINE - Math.abs(x);
		Direction back = x < 0 ? Direction.EAST : Direction.WEST;
		switch (d) {
			case 0 -> c.set(x, 5, z, roof(back));
			case 1 -> c.set(x, 6, z, roofSlab());
			case 2 -> c.set(x, 6, z, roof(back));
			default -> {
				c.set(x, 6, z, ridge());
				c.set(x, 7, z, ridgeCap);
			}
		}
	}

	@Override
	void spineCap(PlanBuilder.Canvas c, Box box) {
		int z = box.minZ();
		for (int x = -SPINE; x <= SPINE; x++) {
			int ax = Math.abs(x);
			c.set(x, 0, z, footing());
			for (int y = 1; y <= 4; y++) {
				c.set(x, y, z, ax == SPINE ? post() : y == 4 ? beam(Direction.Axis.X) : y == 1 ? footing() : ax <= 1 ? shoji() : wall());
			}
			if (ax < SPINE) {
				c.set(x, 5, z, ax == 0 ? post() : planks); // gable under the spine roof
			}
			spineRoof(c, x, z);
		}
	}

	// ------------------------------------------------------------------ wings

	/** Distance of wing line {@code v} from the nearer eave (0 at the walls, 6 at the ridge). */
	private static int eaveDistance(int v) {
		return Math.min(v, WING_WALL - v);
	}

	/** The wing roof over line {@code v} at world (x, z): concave, shallow at the eaves and steep at the ridge. */
	private void wingRoof(PlanBuilder.Canvas c, int x, int z, int v) {
		int d = eaveDistance(v);
		Direction back = v < WING_WALL / 2 ? Direction.SOUTH : Direction.NORTH;
		int y = WING_ROOF_Y[d];
		switch (d) {
			case 1 -> c.set(x, y, z, roofSlab());
			case 6 -> {
				c.set(x, y, z, ridge());
				c.set(x, y + 1, z, ridgeCap);
			}
			default -> c.set(x, y, z, roof(back));
		}
	}

	@Override
	void wingEndWall(PlanBuilder.Canvas c, int i, int u, boolean inner) {
		int x = HqGeometry.wx(i, u);
		for (int v = 0; v < ROW; v++) {
			int z = HqGeometry.wz(i, v);
			if (v == ROW - 1) {
				c.set(x, 0, z, role(Role.GARDEN));
				c.decor(x, 1, z, moss);
				continue;
			}
			boolean door = inner && v >= DOOR_V1 && v <= DOOR_V2;
			c.set(x, 0, z, door ? accentFloor() : footing());
			boolean post = v == 0 || v == 4 || v == 8 || v == WING_WALL;
			boolean window = !inner && (v >= 1 && v <= 3 || v >= 9 && v <= 11);
			for (int y = 1; y <= 4; y++) {
				BlockState s = post ? post() : y == 4 ? beam(Direction.Axis.Z) : door ? air : y == 1 ? footing() : window ? shoji() : wall();
				c.set(x, y, z, s);
			}
			// gable: fill under the roof, a king post under the ridge
			int top = WING_ROOF_Y[eaveDistance(v)];
			for (int y = 5; y < top; y++) {
				c.set(x, y, z, v == WING_WALL / 2 ? post() : planks);
			}
			wingRoof(c, x, z, v);
		}
	}

	@Override
	void segment(PlanBuilder.Canvas c, int i, int s, DyeColor accent) {
		int u0 = HqGeometry.segmentU(s);
		for (int col = 0; col < SEGMENT; col++) {
			int x = HqGeometry.wx(i, u0 + col);
			boolean pillar = col == SEGMENT - 1;
			for (int v = 0; v < ROW; v++) {
				int z = HqGeometry.wz(i, v);
				if (v == ROW - 1) {
					garden(c, x, z, s, col);
					continue;
				}
				boolean wall = v == 0 || v == WING_WALL;
				if (wall) {
					c.set(x, 0, z, footing());
					for (int y = 1; y <= 4; y++) {
						// lit paper panels at y = 3 (full blocks even without the kit: a pane would change shape with the desk's 3×2 screen in front of it); col 1 a solid one
						c.set(x, y, z, pillar ? post() : y == 1 ? footing() : y == 4 ? beam(Direction.Axis.X) : y == 3 && col != 1 ? kit().wallPanel(true) : wall());
					}
					wingRoof(c, x, z, v);
					continue;
				}
				if (c.owns(x, 0, z)) {
					c.set(x, 0, z, v >= DOOR_V1 && v <= DOOR_V2 || pillar ? accentFloor() : floor());
				}
				if (pillar) {
					c.set(x, 5, z, beam(Direction.Axis.Z)); // tie beam across the hall
				}
				wingRoof(c, x, z, v);
				// one lantern down the hall's axis at every pillar (lit panels and the floor lamps do the lighting)
				if (pillar && v == WING_WALL / 2) {
					c.hang(x, 4, z, lantern(true));
				}
				if (pillar && (v == 1 || v == WING_WALL - 1)) {
					c.decor(x, 1, z, planter());
				}
				if (v == WING_WALL / 2) {
					c.decor(x, 1, z, carpet(accent));
				}
			}
		}
	}

	/** Garden strip between rows: cherry trees and bamboo on moss, petals, a stone lantern at each pillar. */
	private void garden(PlanBuilder.Canvas c, int x, int z, int s, int col) {
		boolean pillar = col == SEGMENT - 1;
		c.set(x, 0, z, pillar ? role(Role.PATH) : role(Role.GARDEN));
		if (pillar) {
			// a stone lantern at every other pillar, between the hall's lanterns
			if (s % 2 == 1) {
				stoneLantern(c, x, z);
			}
			return;
		}
		if (s % 2 == 0) {
			// a small cherry tree: trunk mid-segment, crown along the strip
			if (col == 1) {
				c.set(x, 1, z, trunk);
				c.set(x, 2, z, trunk);
				c.set(x, 5, z, leaves());
			} else {
				c.decor(x, 1, z, petals(col == 0 ? 3 : 2, col == 0 ? Direction.EAST : Direction.WEST));
			}
			c.set(x, 3, z, leaves());
			c.set(x, 4, z, leaves());
		} else if (col == 1) {
			for (int y = 1; y <= 4; y++) {
				c.set(x, y, z, bamboo(y >= 3));
			}
		} else {
			c.decor(x, 1, z, moss);
		}
	}

	/** A standing desk against the wall (cabinet and two tops; the 3×2 screen goes on top), the agent's free cell behind. */
	@Override
	void desk(PlanBuilder.Canvas c, DeskCells d) {
		BlockPos top = d.computer().below();
		BlockPos right = d.right();
		Direction side = d.facing().getClockWise();
		for (int dx = -1; dx <= 1; dx++) {
			c.set(top.below().relative(side, dx), floor());
			c.set(d.stand().below().relative(side, dx), floor());
		}
		if (!d.occupied()) {
			return;
		}
		Direction front = d.facing().getOpposite();
		if (custom()) {
			// the kit's standing desk along the whole row
			c.set(d.drawer(), kit().desk(front));
			c.set(top, kit().desk(front));
			c.set(right, kit().desk(front));
		} else {
			c.set(d.drawer(), shelf.setValue(BlockStateProperties.HORIZONTAL_FACING, front));
			c.set(top, role(Role.DESK));
			c.set(right, role(Role.DESK));
		}
	}
}
