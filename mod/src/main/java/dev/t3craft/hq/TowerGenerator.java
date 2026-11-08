package dev.t3craft.hq;

import static dev.t3craft.hq.TowerLayout.BALUSTRADE_Z;
import static dev.t3craft.hq.TowerLayout.T1;
import static dev.t3craft.hq.TowerLayout.T2;
import static dev.t3craft.hq.TowerLayout.T3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * Builds the vertical HQ ({@link TowerLayout}) of a {@link HqLayout}. Pure and deterministic like
 * {@link HqGenerator}: every box, anchor and desk depends only on fixed geometry and on the module
 * indices, so a module that stays keeps every cell, and a new module only fills its own parcel
 * (or brings a new tower, podium hall and bridges, which are new groups).
 *
 * <p>Groups ({@link BuildPlan#groups()}): {@code plaza}, {@code podium}, {@code archive},
 * {@code lounge}, {@code hub} (T1's ground floor), {@code tower_1} (T1's upper cores),
 * {@code review}, {@code lookout} (T1's roof), {@code module_<i>} (an occupied half floor),
 * {@code parcel_<i>} (a vacant one: a hanging garden), {@code tower_2}/{@code tower_3},
 * {@code podium_east}/{@code podium_west}, one per skybridge.
 */
final class TowerGenerator {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private final VerticalStyle style;
	private final HqKit kit = HqKit.active();
	private final PlanBuilder b = new PlanBuilder();
	private final HqLayout layout;
	private final Set<Integer> towers;
	private final Map<Integer, HqLayout.Module> byIndex = new HashMap<>();
	private int bench;

	private TowerGenerator(VerticalStyle style, HqLayout layout) {
		this.style = style;
		this.layout = layout;
		this.towers = TowerLayout.towers(layout);
		for (HqLayout.Module m : layout.modules()) {
			byIndex.put(m.index(), m);
		}
	}

	static BuildPlan generate(VerticalStyle style, HqLayout layout) {
		return new TowerGenerator(style, layout).build();
	}

	// ------------------------------------------------------------------ materials

	/** The look of one tower (facade, slab edges, glass, stairs), so each tower reads apart. */
	private record Mat(BlockState band, BlockState pane, BlockState column, BlockState stairs, BlockState glass, BlockState floor, BlockState roof) {
	}

	private Mat mat(TowerLayout.Tower t) {
		if (t == T2) {
			return new Mat(Blocks.MUD_BRICKS.defaultBlockState(), Blocks.STAINED_GLASS_PANE.pick(DyeColor.LIME).defaultBlockState(),
					log(Blocks.STRIPPED_OAK_LOG), Blocks.BAMBOO_MOSAIC_STAIRS.defaultBlockState(), Blocks.GLASS.defaultBlockState(),
					Blocks.BAMBOO_MOSAIC.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState());
		}
		if (t == T3) {
			return new Mat(Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_BLUE).defaultBlockState(),
					Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState(),
					Blocks.GLASS.defaultBlockState(), Blocks.PALE_OAK_PLANKS.defaultBlockState(), Blocks.POLISHED_TUFF.defaultBlockState());
		}
		return new Mat(Blocks.SMOOTH_QUARTZ.defaultBlockState(), Blocks.GLASS_PANE.defaultBlockState(), kit.beam(Direction.Axis.Y),
				Blocks.QUARTZ_STAIRS.defaultBlockState(), Blocks.GLASS.defaultBlockState(), kit.floor(), Blocks.SMOOTH_STONE.defaultBlockState());
	}

	private static BlockState log(Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
	}

	private static BlockState stairs(BlockState s, Direction facing) {
		return s.setValue(BlockStateProperties.HORIZONTAL_FACING, facing).setValue(BlockStateProperties.HALF, Half.BOTTOM);
	}

	private BlockState floor() {
		return kit.floor();
	}

	private BlockState accent() {
		return kit.floorAccent();
	}

	private BlockState wall() {
		return kit.wallPanel(false);
	}

	private BlockState lit() {
		return kit.wallPanel(true);
	}

	private BlockState leaves() {
		return Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
	}

	private BlockState greenLeaves() {
		return Blocks.AZALEA_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
	}

	private static BlockState carpet(DyeColor c) {
		return Blocks.CARPET.pick(c).defaultBlockState();
	}

	/** Sets a cell only if the module owns it (holes and neighbours are left alone). */
	private static void put(PlanBuilder.Canvas c, int x, int y, int z, BlockState s) {
		if (c.owns(x, y, z)) {
			c.set(x, y, z, s);
		}
	}

	private static void deco(PlanBuilder.Canvas c, int x, int y, int z, BlockState s) {
		if (c.owns(x, y, z)) {
			c.decor(x, y, z, s);
		}
	}

	// ------------------------------------------------------------------ plan

	private BuildPlan build() {
		plaza();
		podium();
		lounge();
		tower(T1);
		review();
		for (TowerLayout.Tower t : List.of(T2, T3)) {
			if (towers.contains(t.id())) {
				tower(t);
				podiumHall(t);
			}
		}
		for (TowerLayout.Bridge br : TowerLayout.BRIDGES) {
			if (towers.contains(br.to().id())) {
				bridge(br);
			}
		}
		openings();
		b.group(null);
		return b.build();
	}

	// ------------------------------------------------------------------ plaza, podium, lounge

	private void plaza() {
		b.group("plaza");
		Box box = TowerLayout.PLAZA;
		BlockState stone = Blocks.SMOOTH_STONE.defaultBlockState();
		BlockState moss = Blocks.MOSS_BLOCK.defaultBlockState();
		try (PlanBuilder.Canvas c = b.module("plaza", box, List.of())) {
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					int ax = Math.abs(x);
					int dz = z - 36;
					boolean axis = ax <= 3 || Math.abs(dz) <= 1;
					boolean ring = Math.abs(ax * ax + dz * dz - 36) <= 6;
					boolean bed = ax >= 14 && ax <= 40 && ((z >= 28 && z <= 32) || (z >= 40 && z <= 44));
					c.set(x, 0, z, bed ? moss : axis || ring ? accent() : ((x + z) & 1) == 0 ? stone : floorStone());
				}
			}
			// trees and bushes in the beds, benches facing the axis, lights along it
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int zb : new int[] {30, 42}) {
					for (int k = 0; k < 3; k++) {
						int x = sx * (18 + 9 * k);
						tree(c, x, 1, zb, k == 1 ? greenLeaves() : Blocks.CHERRY_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true),
								Blocks.CHERRY_LOG.defaultBlockState(), 3);
						c.set(x + 3 * sx, 1, zb, Blocks.FLOWERING_AZALEA.defaultBlockState());
						c.set(x - 3 * sx, 1, zb, Blocks.AZALEA.defaultBlockState());
					}
					for (int x = 15; x <= 39; x += 4) {
						c.set(sx * x, 1, zb - 3, style.bench(Direction.SOUTH));
					}
				}
				for (int z = 26; z <= 46; z += 5) {
					c.set(sx * 6, 1, z, kit.gardenLantern());
				}
			}
			// the fountain ring: stone lanterns on its diagonals and a planter crown in the middle
			for (int[] d : new int[][] {{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) {
				c.set(d[0], 1, 36 + d[1], kit.gardenLantern());
			}
			for (int[] d : new int[][] {{0, 2}, {2, 0}, {-2, 0}, {0, -2}}) {
				c.set(d[0], 1, 36 + d[1] - 4, kit.gardenPlanter());
			}
			b.anchor(Anchor.at(AnchorNames.SPAWN, new BlockPos(0, 1, 40), Direction.NORTH));
			int w = 0;
			for (int[] p : new int[][] {{-11, 26}, {11, 26}, {-11, 46}, {11, 46}}) {
				b.anchor(Anchor.at("walk_plaza_" + w++, new BlockPos(p[0], 1, p[1]), Direction.NORTH));
			}
		}
	}

	private BlockState floorStone() {
		return Blocks.POLISHED_TUFF.defaultBlockState();
	}

	/** A small tree: trunk of {@code h} and a two-layer crown. */
	private static void tree(PlanBuilder.Canvas c, int x, int y, int z, BlockState leaves, BlockState trunk, int h) {
		for (int k = 0; k < h; k++) {
			put(c, x, y + k, z, trunk);
		}
		int top = y + h;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int r = Math.abs(dx) + Math.abs(dz);
				if (r <= 3 && !(dx == 0 && dz == 0)) {
					put(c, x + dx, top - 1, z + dz, leaves);
				}
				if (r <= 2) {
					put(c, x + dx, top, z + dz, leaves);
				}
				if (r <= 1) {
					put(c, x + dx, top + 1, z + dz, leaves);
				}
			}
		}
	}

	/** The podium in front of T1: entrance hall, archive (west), café (east); its roof is the lounge. */
	private void podium() {
		Box f = TowerLayout.PODIUM_FRONT;
		BlockState band = mat(T1).band();
		// archive
		b.group(HqGenerator.GROUP_ARCHIVE);
		try (PlanBuilder.Canvas c = b.module("archive", Box.of(f.minX(), 0, f.minZ(), -11, 8, f.maxZ()), List.of())) {
			podiumShell(c, band, -21, -11, true, false);
			for (int z = 17; z <= 22; z++) {
				for (int y = 1; y <= 3; y++) {
					c.set(-20, y, z, y == 2 ? Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
							: Blocks.BOOKSHELF.defaultBlockState());
				}
				c.set(-20, 4, z, lit());
			}
			for (int x = -19; x <= -12; x++) {
				c.set(x, 0, 19, accent());
			}
			BlockPos t = new BlockPos(-16, 1, 21);
			c.set(t, style.archiveTerminal(Direction.NORTH));
			c.set(-13, 1, 17, kit.gardenPlanter());
			c.set(-19, 1, 22, kit.lantern());
			b.anchor(Anchor.face(AnchorNames.ARCHIVE_TERMINAL, t, Direction.NORTH));
			b.anchor(Anchor.at(AnchorNames.ARCHIVE, new BlockPos(-16, 1, 20), Direction.SOUTH));
		}
		b.group("podium");
		try (PlanBuilder.Canvas c = b.module("podium_hall", Box.of(-10, 0, f.minZ(), 10, 8, f.maxZ()), List.of())) {
			podiumShell(c, band, -10, 10, false, false);
			for (int x = -3; x <= 3; x++) {
				for (int y = 1; y <= 4; y++) {
					c.set(x, y, 23, AIR);
				}
			}
			for (int z = 16; z <= 22; z++) {
				c.set(0, 0, z, accent());
			}
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int y = 1; y <= 7; y++) {
					c.set(4 * sx, y, 20, mat(T1).column());
				}
				c.set(9 * sx, 1, 22, kit.gardenPlanter());
				c.set(9 * sx, 1, 17, kit.lantern());
			}
			b.anchor(Anchor.at("walk_lobby_0", new BlockPos(0, 1, 19), Direction.NORTH));
			b.anchor(Anchor.at("walk_lobby_1", new BlockPos(-7, 1, 18), Direction.NORTH));
		}
		try (PlanBuilder.Canvas c = b.module("podium_cafe", Box.of(11, 0, f.minZ(), f.maxX(), 8, f.maxZ()), List.of())) {
			podiumShell(c, band, 11, 21, false, true);
			for (int x = 14; x <= 19; x++) {
				c.set(x, 1, 17, kit.desk(Direction.SOUTH));
			}
			for (int x = 14; x <= 18; x += 2) {
				c.set(x, 1, 21, style.bench(Direction.SOUTH));
				b.anchor(Anchor.at(AnchorNames.bench(bench++), new BlockPos(x, 1, 20), Direction.NORTH));
			}
			c.set(20, 1, 22, kit.gardenPlanter());
			c.set(12, 1, 22, kit.lantern());
		}
	}

	/**
	 * A podium room spanning {@code x1..x2}: floor, roof slab (the terrace), the south facade and the
	 * outer end wall; {@code west}/{@code east}: whether the room owns a closed end at x1 / x2
	 * (with a door into the hall).
	 */
	private void podiumShell(PlanBuilder.Canvas c, BlockState band, int x1, int x2, boolean archive, boolean cafe) {
		for (int x = x1; x <= x2; x++) {
			for (int z = 16; z <= 23; z++) {
				c.set(x, 0, z, floor());
				c.set(x, 8, z, band);
			}
			for (int y = 1; y <= 7; y++) {
				boolean col = Math.floorMod(x, 4) == 0;
				c.set(x, y, 23, col || y == 7 ? band : y == 1 ? wall() : mat(T1).pane());
			}
		}
		int[] ends = archive ? new int[] {x1, x2} : cafe ? new int[] {x1, x2} : new int[0];
		for (int x : ends) {
			boolean outer = archive ? x == x1 : x == x2;
			for (int z = 16; z <= 22; z++) {
				for (int y = 1; y <= 7; y++) {
					boolean door = !outer && z >= 18 && z <= 20 && y <= 3;
					c.set(x, y, z, door ? AIR : outer && y >= 2 && y <= 5 && z % 3 == 0 ? mat(T1).pane() : wall());
				}
			}
		}
	}

	/** The lounge: the terrace on the podium roof, level with T1's first floor. */
	private void lounge() {
		b.group("lounge");
		Box box = TowerLayout.LOUNGE;
		try (PlanBuilder.Canvas c = b.module("lounge", box, List.of())) {
			for (int x = box.minX(); x <= box.maxX(); x++) {
				c.set(x, 9, 23, kit.railing(Direction.SOUTH));
			}
			for (int z = 16; z <= 22; z++) {
				c.set(box.minX(), 9, z, kit.railing(Direction.WEST));
				c.set(box.maxX(), 9, z, kit.railing(Direction.EAST));
			}
			// two pergolas, planters and benches looking over the plaza
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int x : new int[] {10, 19}) {
					for (int z : new int[] {17, 22}) {
						for (int y = 9; y <= 12; y++) {
							c.set(sx * x, y, z, kit.beam(Direction.Axis.Y));
						}
					}
				}
				for (int x = 10; x <= 19; x++) {
					c.set(sx * x, 13, 17, kit.beam(Direction.Axis.X));
					c.set(sx * x, 13, 22, kit.beam(Direction.Axis.X));
					if (x % 2 == 1) {
						for (int z = 18; z <= 21; z++) {
							c.set(sx * x, 13, z, kit.beam(Direction.Axis.Z));
						}
					}
				}
				for (int x = 11; x <= 18; x += 7) {
					c.set(sx * x, 9, 17, kit.gardenPlanter());
					c.set(sx * x, 12, 18, leaves());
				}
				for (int x = 13; x <= 16; x++) {
					c.set(sx * x, 9, 21, style.bench(Direction.NORTH));
					b.anchor(Anchor.at(AnchorNames.bench(bench++), new BlockPos(sx * x, 9, 22), Direction.SOUTH));
				}
				c.set(sx * 2, 9, 22, kit.gardenLantern());
			}
			int w = 0;
			for (int[] p : new int[][] {{-6, 19}, {6, 19}, {-3, 21}, {3, 21}}) {
				b.anchor(Anchor.at("walk_lounge_" + w++, new BlockPos(p[0], 9, p[1]), Direction.SOUTH));
			}
		}
	}

	// ------------------------------------------------------------------ towers

	private String towerGroup(TowerLayout.Tower t) {
		return "tower_" + t.id();
	}

	private void tower(TowerLayout.Tower t) {
		for (int f = 0; f <= t.floors(); f++) {
			b.group(t == T1 && f == 0 ? "hub" : towerGroup(t));
			core(t, f);
			for (int side = 1; side >= -1; side -= 2) {
				half(t, f, side);
			}
		}
		b.group(t == T1 ? "lookout" : towerGroup(t));
		roof(t);
	}

	/** World x of local x in tower t. */
	private static int wx(TowerLayout.Tower t, int lx) {
		return t.cx() + lx;
	}

	private static int wz(TowerLayout.Tower t, int lz) {
		return t.cz() + lz;
	}

	/**
	 * True when the loggia of floor {@code f} is one cell wide at {@code lx} (the floor behind it is the
	 * atrium void or the stair hole): a planter there would close the way (fix/hq-lanterns).
	 */
	private static boolean narrowLoggia(TowerLayout.Tower t, int f, int lx) {
		int holeLane = f > 0 ? TowerLayout.lane(f - 1) : -1;
		int lz = -t.pz() + 2;
		for (int x = lx - 1; x <= lx + 1; x++) {
			boolean atrium = Math.max(Math.abs(x), Math.abs(lz)) <= t.atrium();
			boolean hole = holeLane >= 0 && x >= t.x0() && x < t.x0() + t.run() && lz >= TowerLayout.laneZ(holeLane)
					&& lz <= TowerLayout.laneZ(holeLane) + 1;
			if (atrium || hole) {
				return true;
			}
		}
		return false;
	}

	/** The middle band of floor f (atrium, gallery ring, north loggia, south stair hall). */
	private void core(TowerLayout.Tower t, int f) {
		int g = t.gallery();
		int a = t.atrium();
		int y0 = t.floorY(f);
		int h = t.floorH();
		int pz = t.pz();
		Mat m = mat(t);
		Box box = Box.of(wx(t, -g), y0, wz(t, -pz), wx(t, g), y0 + h - 1, wz(t, pz));
		List<Box> holes = t == T1 && f == 3 ? List.of(TowerLayout.REVIEW) : List.of();
		try (PlanBuilder.Canvas c = b.module("t" + t.id() + "_core_" + f, box, holes)) {
			int holeLane = f > 0 ? TowerLayout.lane(f - 1) : -1;
			for (int lx = -g; lx <= g; lx++) {
				for (int lz = -pz; lz <= pz; lz++) {
					int x = wx(t, lx);
					int z = wz(t, lz);
					int ring = Math.max(Math.abs(lx), Math.abs(lz));
					boolean atrium = ring <= a;
					boolean hole = holeLane >= 0 && lx >= t.x0() && lx < t.x0() + t.run() && lz >= TowerLayout.laneZ(holeLane)
							&& lz <= TowerLayout.laneZ(holeLane) + 1;
					BlockState slab;
					if (Math.abs(lz) == pz) {
						slab = m.band();
					} else if (f > 0 && atrium || hole) {
						slab = null;
					} else if (f == 0 && atrium) {
						slab = t == T1 ? (ring == a || ring == 2 || (lx == 0 && lz == 0) ? accent() : m.floor()) : Blocks.MOSS_BLOCK.defaultBlockState();
					} else {
						slab = ring == a + 1 ? accent() : m.floor();
					}
					put(c, x, y0, z, slab == null ? AIR : slab);
				}
			}
			// facades of the north loggia and the south stair hall
			for (int lx = -g; lx <= g; lx++) {
				for (int y = y0 + 1; y < y0 + h; y++) {
					boolean col = Math.floorMod(lx, 3) == 0;
					int top = y0 + h - 1;
					// north: an open loggia with railings and hanging leaves (T3: glazed)
					BlockState n;
					if (col || y == top) {
						n = col ? m.column() : m.band();
					} else if (t == T3) {
						n = m.pane();
					} else if (y == y0 + 1) {
						n = kit.railing(Direction.NORTH);
					} else if (y == top - 1) {
						n = t == T2 ? greenLeaves() : leaves();
					} else {
						n = AIR;
					}
					put(c, wx(t, lx), y, wz(t, -pz), n);
					boolean door = t == T1 && f <= 1 && Math.abs(lx) >= 6 && Math.abs(lx) <= 8 && y <= y0 + 3;
					put(c, wx(t, lx), y, wz(t, pz), door ? AIR : col || y == top ? (col ? m.column() : m.band()) : m.pane());
				}
				if (Math.floorMod(lx, 3) == 1 && f > 0 && !narrowLoggia(t, f, lx)) {
					put(c, wx(t, lx), y0 + 1, wz(t, -pz + 1), kit.gardenPlanter());
				}
			}
			// railings round the atrium void, in its outer ring (the gallery stays free)
			if (f > 0) {
				for (int lx = -a; lx <= a; lx++) {
					for (int lz = -a; lz <= a; lz++) {
						if (Math.max(Math.abs(lx), Math.abs(lz)) == a) {
							Direction edge = Math.abs(lz) == a ? (lz < 0 ? Direction.NORTH : Direction.SOUTH) : (lx < 0 ? Direction.WEST : Direction.EAST);
							put(c, wx(t, lx), y0 + 1, wz(t, lz), kit.railing(edge));
						}
					}
				}
			}
			// the balustrade between the gallery and the stair flights
			for (int lx = t.x0(); lx < t.x0() + t.run(); lx++) {
				for (int y = y0 + 1; y <= y0 + 2; y++) {
					put(c, wx(t, lx), y, wz(t, BALUSTRADE_Z), m.pane());
				}
			}
			// the flight up from this floor (T1's top floor climbs to the lookout)
			if (f < t.floors() || t == T1) {
				int lane = TowerLayout.lane(f);
				Direction up = lane == 0 ? Direction.EAST : Direction.WEST;
				for (int k = 0; k < t.run(); k++) {
					int lx = lane == 0 ? t.x0() + k : t.x0() + t.run() - 1 - k;
					for (int r = 0; r < 2; r++) {
						int z = wz(t, TowerLayout.laneZ(lane) + r);
						for (int y = y0 + 1; y < y0 + 1 + k; y++) {
							put(c, wx(t, lx), y, z, m.band());
						}
						put(c, wx(t, lx), y0 + 1 + k, z, stairs(m.stairs(), up));
					}
				}
			}
			// lights at the gallery corners, lit panels in the loggia
			int lc = a + 2;
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					deco(c, wx(t, sx * lc), y0 + 1, wz(t, sz * lc), kit.lantern());
				}
			}
			if (t == T1 && f == 0) {
				hub(c);
			} else if (t == T1 && f == 1) {
				kanban(c);
			} else if (f == 0) {
				// the light well's garden
				tree(c, wx(t, 0), 1, wz(t, 0), t == T2 ? greenLeaves() : leaves(), log(Blocks.OAK_LOG), 3);
				b.anchor(Anchor.at("walk_tower_" + t.id(), new BlockPos(wx(t, 0), 1, wz(t, a + 2)), Direction.NORTH));
			}
		}
	}

	/** T1's ground floor: the hub in the atrium. */
	private void hub(PlanBuilder.Canvas c) {
		int bz = -(T1.gallery() + 1);
		// the "needs attention" board facing the entrance
		for (int y = 1; y <= 7; y++) {
			c.set(-4, y, bz, kit.beam(Direction.Axis.Y));
			c.set(4, y, bz, kit.beam(Direction.Axis.Y));
		}
		for (int x = -3; x <= 3; x++) {
			for (int y = 1; y <= 6; y++) {
				c.set(x, y, bz, style.palette().get(HqStyle.Role.BOARD));
			}
			c.set(x, 7, bz, kit.beam(Direction.Axis.X));
		}
		c.decor(new BlockPos(0, 6, bz + 1), kit.plaque(Direction.SOUTH), kit.plaqueData(DyeColor.ORANGE, "", "Needs", "attention", ""));
		int lamp = 0;
		for (int x = -3; x <= 3; x += 2) {
			c.set(x, 5, bz, style.statusLamp());
			b.anchor(Anchor.face(AnchorNames.boardLamp(lamp++), new BlockPos(x, 5, bz), Direction.SOUTH));
		}
		b.anchor(Anchor.face(AnchorNames.BOARD, new BlockPos(0, 3, bz), Direction.SOUTH));
		b.anchor(Anchor.at(AnchorNames.BOARD_FRONT, new BlockPos(0, 1, bz + 1), Direction.NORTH));
		int slot = 0;
		for (int row = 2; row <= 3; row++) {
			for (int i = 0; i <= 6; i++) {
				int x = i == 0 ? 0 : (i % 2 == 1 ? (i + 1) / 2 : -(i / 2));
				b.anchor(Anchor.at(AnchorNames.boardSlot(slot++), new BlockPos(x, 1, bz + row), Direction.NORTH));
			}
		}
		// the hub's big screen beside the board, a living wall on the other side
		HqGeometry.Surface s = new HqGeometry.Surface(new BlockPos(5, 2, bz), Direction.EAST, Direction.SOUTH, 5, 3);
		bigScreen(c, s, "hub", HqGenerator.hubBinding(layout));
		for (int x = 5; x <= 9; x++) {
			c.set(x, 1, bz, kit.beam(Direction.Axis.X));
			for (int y = 5; y <= 7; y++) {
				c.set(x, y, bz, y == 5 ? kit.beam(Direction.Axis.X) : wall());
			}
		}
		for (int x = -9; x <= -5; x++) {
			for (int y = 1; y <= 7; y++) {
				c.set(x, y, bz, y == 1 ? Blocks.MOSS_BLOCK.defaultBlockState() : (x + y) % 3 == 0 ? leaves() : greenLeaves());
			}
		}
		// benches in the atrium facing the board, planters round the hologram
		for (int z : new int[] {3, 5}) {
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int k = 3; k <= 5; k++) {
					BlockPos p = new BlockPos(sx * k, 1, z);
					c.set(p, style.bench(Direction.SOUTH));
					b.anchor(Anchor.at(AnchorNames.bench(bench++), p.relative(Direction.NORTH), Direction.NORTH));
				}
			}
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				c.set(2 * sx, 1, 2 * sz - 1, kit.gardenPlanter());
			}
		}
		int walk = 0;
		for (int wz : new int[] {-3, 0}) {
			for (int sx = -1; sx <= 1; sx += 2) {
				b.anchor(Anchor.at(AnchorNames.walkHub(walk++), new BlockPos(4 * sx, 1, wz), Direction.SOUTH));
			}
		}
		b.anchor(Anchor.at(AnchorNames.HUB, new BlockPos(0, 1, 0), Direction.SOUTH));
		b.anchor(new Anchor(AnchorNames.HOLOGRAM, 0.5, 5.0, 0.5, Direction.SOUTH, new BlockPos(0, 5, 0)));
		b.anchor(Anchor.at(AnchorNames.ENTRANCE, new BlockPos(-7, 1, T1.pz()), Direction.SOUTH));
	}

	/** The thread kanban wall on the first-floor balcony, facing across the atrium. */
	private void kanban(PlanBuilder.Canvas c) {
		int y0 = T1.floorY(1);
		int bz = -(T1.gallery() + 1);
		HqGeometry.Surface wall = new HqGeometry.Surface(new BlockPos(-4, y0 + 1, bz), Direction.EAST, Direction.SOUTH, 9, 4);
		for (BlockPos p : wall.cells()) {
			c.set(p, style.screen());
		}
		for (int y = y0 + 1; y <= y0 + 5; y++) {
			c.set(-5, y, bz, kit.beam(Direction.Axis.Y));
			c.set(5, y, bz, kit.beam(Direction.Axis.Y));
		}
		for (int x = -4; x <= 4; x++) {
			c.set(x, y0 + 5, bz, kit.beam(Direction.Axis.X));
		}
		b.anchor(Anchor.face(AnchorNames.THREAD_WALL, wall.origin(), wall.facing()));
		b.anchor(Anchor.face(AnchorNames.THREAD_WALL_END, wall.origin().relative(wall.along(), wall.width() - 1).above(wall.height() - 1), wall.facing()));
		b.anchor(Anchor.at(AnchorNames.THREAD_WALL_FRONT, wall.anchorCell().relative(wall.facing(), 3), wall.facing().getOpposite()));
	}

	private void bigScreen(PlanBuilder.Canvas c, HqGeometry.Surface s, String key, String binding) {
		for (BlockPos p : s.cells()) {
			c.set(p, style.screen());
		}
		b.anchor(Anchor.face(AnchorNames.bigScreen(key, s.width(), s.height(), binding), s.origin().above(s.height() - 1), s.facing()));
	}

	// ------------------------------------------------------------------ halves (parcels)

	/** A door in a facade: tower, side, floor, world rows z1..z2, the group that opens it. */
	private record Opening(TowerLayout.Tower tower, int side, int floor, int z1, int z2, String group, TowerLayout.Tower needs) {
	}

	private static final List<Opening> OPENINGS = List.of(
			new Opening(T1, 1, 2, -4, -2, "bridge_e2", T2),
			new Opening(T1, 1, 5, -12, -10, "bridge_e5", T2),
			new Opening(T1, -1, 3, -12, -10, "bridge_w3", T3),
			new Opening(T1, 1, 0, TowerLayout.PODIUM_EAST_Z1, TowerLayout.PODIUM_EAST_Z2, "podium_east", T2),
			new Opening(T1, -1, 0, TowerLayout.PODIUM_WEST_Z1, TowerLayout.PODIUM_WEST_Z2, "podium_west", T3),
			new Opening(T2, -1, 2, -4, -2, "", T2),
			new Opening(T2, -1, 6, -12, -10, "", T2),
			new Opening(T2, -1, 0, TowerLayout.PODIUM_EAST_Z1, TowerLayout.PODIUM_EAST_Z2, "", T2),
			new Opening(T3, 1, 3, -12, -10, "", T3),
			new Opening(T3, 1, 0, TowerLayout.PODIUM_WEST_Z1, TowerLayout.PODIUM_WEST_Z2, "", T3));

	private static Box openingBox(Opening o) {
		TowerLayout.Tower t = o.tower();
		int x = t.cx() + o.side() * t.px();
		int y = t.floorY(o.floor());
		return Box.of(x, y + 1, o.z1(), x, y + 3, o.z2());
	}

	/** The facade cells of T1 that open when a bridge or podium hall arrives (the only T1 cells a new tower changes). */
	static List<Box> t1Openings() {
		List<Box> out = new ArrayList<>();
		for (Opening o : OPENINGS) {
			if (o.tower() == T1) {
				out.add(openingBox(o));
			}
		}
		return out;
	}

	/** The openings of T1 are their own modules (glass until the bridge or hall exists, then open), in T1's groups. */
	private void openings() {
		for (Opening o : OPENINGS) {
			if (o.tower() != T1) {
				continue;
			}
			boolean open = towers.contains(o.needs().id());
			// T1's cells stay T1's: the door only turns from glass to air when the bridge or hall arrives
			b.group(o.floor() == 0 ? "hub" : towerGroup(T1));
			Box box = openingBox(o);
			try (PlanBuilder.Canvas c = b.module("door_" + o.group(), box, List.of())) {
				c.fill(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), open ? AIR : mat(T1).pane());
			}
		}
	}

	/** Local (u, v) of a half → world cell: u from the partition (0) to the facade (width + 1). */
	private static BlockPos cell(TowerLayout.Tower t, int side, int u, int y, int lz) {
		return new BlockPos(t.cx() + side * (t.gallery() + 1 + u), y, t.cz() + lz);
	}

	private void half(TowerLayout.Tower t, int f, int side) {
		int y0 = t.floorY(f);
		int h = t.floorH();
		int pz = t.pz();
		int w = t.width();
		Mat m = mat(t);
		int index = f == 0 ? -1 : t.firstParcel() + 2 * (f - 1) + (side > 0 ? 0 : 1);
		HqLayout.Module mod = index < 0 ? null : byIndex.get(index);
		List<Box> holes = new ArrayList<>();
		List<Opening> doors = new ArrayList<>();
		for (Opening o : OPENINGS) {
			if (o.tower() == t && o.side() == side && o.floor() == f) {
				doors.add(o);
				if (t == T1) {
					holes.add(openingBox(o));
				}
			}
		}
		BlockPos c1 = cell(t, side, 0, y0, -pz);
		BlockPos c2 = cell(t, side, w + 1, y0 + h - 1, pz);
		Box box = Box.of(c1.getX(), c1.getY(), c1.getZ(), c2.getX(), c2.getY(), c2.getZ());
		String id = f == 0 ? "t" + t.id() + "_lobby_" + (side > 0 ? "e" : "w") : mod != null ? "module_" + index : "parcel_" + index;
		if (f > 0) {
			b.group(mod != null ? HqGenerator.group(index) : "parcel_" + index);
		}
		Direction out = side > 0 ? Direction.EAST : Direction.WEST;
		try (PlanBuilder.Canvas c = b.module(id, box, holes)) {
			Hv v = new Hv(c, t, side, y0);
			// slab, facade and end walls, partition
			for (int u = 0; u <= w + 1; u++) {
				for (int lz = -pz; lz <= pz; lz++) {
					boolean edge = u == w + 1 || Math.abs(lz) == pz;
					v.set(u, 0, lz, edge ? m.band() : m.floor());
				}
			}
			for (int lz = -pz; lz <= pz; lz++) {
				for (int y = 1; y < h; y++) {
					v.set(w + 1, y, lz, facade(t, lz, y, h, m));
					boolean door = f == 0 ? Math.abs(lz) <= 3 && y <= 4 : Math.abs(lz) <= 1 && y <= 3;
					BlockState p = door ? AIR : y == h - 1 || y == 1 ? wall() : Math.floorMod(lz, 4) == 2 ? lit() : (y >= 2 && y <= 3) ? m.pane() : wall();
					v.set(0, y, lz, p);
				}
			}
			for (int u = 0; u <= w; u++) {
				for (int y = 1; y < h; y++) {
					v.set(u, y, -pz, facade(t, u, y, h, m));
					v.set(u, y, pz, facade(t, u, y, h, m));
				}
			}
			for (Opening o : doors) {
				if (t != T1) {
					for (int z = o.z1(); z <= o.z2(); z++) {
						for (int y = 1; y <= 3; y++) {
							v.set(w + 1, y, z - t.cz(), AIR);
						}
					}
				}
			}
			v.deco(1, 1, -pz + 1, kit.lantern());
			v.deco(w, 1, -pz + 1, kit.lantern());
			v.deco(w, 1, pz - 1, kit.lantern());
			v.deco(1, 1, pz - 1, kit.lantern());
			if (f == 0) {
				lobby(v, t, side, w);
			} else if (mod == null) {
				garden(v, t, w);
			} else {
				work(v, t, f, side, w, mod, out);
			}
		}
	}

	/** Facade cell at position {@code p} along the wall, height {@code y} above the slab. */
	private BlockState facade(TowerLayout.Tower t, int p, int y, int h, Mat m) {
		if (y == h - 1) {
			return m.band();
		}
		if (t == T1) {
			return Math.floorMod(p, 4) == 0 ? m.column() : m.pane();
		}
		if (t == T2) {
			return Math.floorMod(p, 3) == 0 ? m.column() : y == 1 ? greenLeaves() : m.pane();
		}
		// T3: stone with tall narrow windows
		return Math.floorMod(p, 2) == 0 || y == 1 ? m.column() : m.pane();
	}

	/** Writes a half's cells in its local frame (u from the partition, height above the slab, local z). */
	private final class Hv {
		final PlanBuilder.Canvas c;
		final TowerLayout.Tower t;
		final int side;
		final int y0;

		Hv(PlanBuilder.Canvas c, TowerLayout.Tower t, int side, int y0) {
			this.c = c;
			this.t = t;
			this.side = side;
			this.y0 = y0;
		}

		BlockPos at(int u, int y, int lz) {
			return cell(t, side, u, y0 + y, lz);
		}

		void set(int u, int y, int lz, BlockState s) {
			BlockPos p = at(u, y, lz);
			put(c, p.getX(), p.getY(), p.getZ(), s);
		}

		void deco(int u, int y, int lz, BlockState s) {
			BlockPos p = at(u, y, lz);
			TowerGenerator.deco(c, p.getX(), p.getY(), p.getZ(), s);
		}
	}

	private void lobby(Hv v, TowerLayout.Tower t, int side, int w) {
		for (int lz = -9; lz <= 9; lz += 6) {
			v.set(w - 1, 1, lz, kit.gardenPlanter());
			v.set(w - 1, 2, lz, leaves());
		}
		for (int lz = -12; lz <= 12; lz += 24) {
			for (int u = 3; u <= w - 3; u += 2) {
				v.set(u, 1, lz, style.bench(lz < 0 ? Direction.NORTH : Direction.SOUTH));
				b.anchor(Anchor.at(AnchorNames.bench(bench++), v.at(u, 1, lz + (lz < 0 ? 1 : -1)), lz < 0 ? Direction.SOUTH : Direction.NORTH));
			}
		}
		for (int u = 2; u <= w - 2; u++) {
			v.set(u, 0, 0, accent());
		}
		b.anchor(Anchor.at("walk_lobby_t" + t.id() + (side > 0 ? "e" : "w"), v.at(w / 2, 1, 5), Direction.NORTH));
	}

	/** A vacant half floor: a hanging garden (moss beds, small trees, benches); paths stay free. */
	private void garden(Hv v, TowerLayout.Tower t, int w) {
		BlockState moss = Blocks.MOSS_BLOCK.defaultBlockState();
		int k = 0;
		for (int zc : new int[] {-9, 0, 9}) {
			for (int u = 4; u <= w - 2; u++) {
				for (int lz = zc - 2; lz <= zc + 2; lz++) {
					v.set(u, 0, lz, moss);
				}
			}
			int uc = (4 + w - 2) / 2;
			BlockPos p = v.at(uc, 1, zc);
			tree(v.c, p.getX(), p.getY(), p.getZ(), (k++ & 1) == 0 ? leaves() : greenLeaves(), log(Blocks.OAK_LOG), Math.min(3, t.floorH() - 5));
			v.set(4, 1, zc - 2, Blocks.FLOWERING_AZALEA.defaultBlockState());
			v.set(w - 2, 1, zc + 2, Blocks.AZALEA.defaultBlockState());
			v.set(2, 1, zc, style.bench(v.side > 0 ? Direction.WEST : Direction.EAST));
		}
	}

	/** An occupied half floor: the project's plan type, desks, sign, big screen, anchors. */
	private void work(Hv v, TowerLayout.Tower t, int f, int side, int w, HqLayout.Module mod, Direction out) {
		TowerLayout.Plan plan = t.plan(f);
		DyeColor color = HqStyle.envColor(mod);
		int pz = t.pz();
		int i = mod.index();
		// sign over the doorway (on the gallery side), a lintel behind it
		v.set(1, 4, 0, kit.beam(Direction.Axis.Z));
		BlockPos signAt = v.at(0, 4, 0);
		HqStyle.SignText st = sign(mod);
		v.c.decor(signAt, kit.plaque(out.getOpposite()), kit.plaqueData(st.color(), st.l1(), st.l2(), st.l3(), st.l4()));
		switch (plan) {
			case OPEN -> {
				for (int lz = -12; lz <= 12; lz++) {
					v.set(2, 1, lz, carpet(color));
				}
				for (int lz = -12; lz <= 12; lz += 6) {
					v.set(w, 1, lz, kit.gardenPlanter());
				}
			}
			case CELLS -> {
				for (int lz = -pz + 1; lz <= pz - 1; lz++) {
					boolean door = (lz >= -7 && lz <= -6) || (lz >= 3 && lz <= 4) || (lz >= 6 && lz <= 7);
					for (int y = 1; y < t.floorH(); y++) {
						v.set(3, y, lz, door && y <= 3 ? AIR : y == 1 || y >= t.floorH() - 2 ? wall() : mat(t).pane());
					}
					v.set(1, 1, lz, carpet(color));
				}
				for (int lz : new int[] {-5, 5}) {
					for (int u = 4; u <= w; u++) {
						for (int y = 1; y < t.floorH(); y++) {
							v.set(u, y, lz, y == 1 || y >= t.floorH() - 2 ? wall() : mat(t).pane());
						}
					}
				}
				// a planter at the south end only: at the north end the cell is the way past the first office
				v.set(w, 1, 13, kit.gardenPlanter());
			}
			case MEZZANINE -> {
				for (int u = 3; u <= w - 1; u++) {
					for (int lz = -12; lz <= 12; lz++) {
						v.set(u, 1, lz, u == 3 ? stairs(mat(t).stairs(), out) : accent());
					}
				}
				for (int lz = -12; lz <= 12; lz += 24) {
					v.set(w - 1, 2, lz, kit.gardenPlanter());
				}
				for (int lz = -12; lz <= 12; lz++) {
					v.set(2, 1, lz, carpet(color));
				}
			}
		}
		// the project's big screen on the north end wall
		int uc = plan == TowerLayout.Plan.CELLS ? (4 + w) / 2 : 1 + w / 2;
		BlockPos a1 = v.at(uc - 2, 2, -pz + 1);
		BlockPos a2 = v.at(uc + 2, 2, -pz + 1);
		BlockPos origin = new BlockPos(Math.min(a1.getX(), a2.getX()), a1.getY(), a1.getZ());
		HqGeometry.Surface s = new HqGeometry.Surface(origin, Direction.EAST, Direction.SOUTH, 5, 3);
		bigScreen(v.c, s, "module@" + i, "project:" + mod.projectKey());
		for (int k = 0; k < 5; k++) {
			v.c.set(origin.relative(Direction.EAST, k).below(), kit.beam(Direction.Axis.X));
		}
		// desks
		for (int slot = 0; slot < mod.capacity(); slot++) {
			String thread = mod.threadAt(slot);
			if (thread == null) {
				continue;
			}
			BlockPos stand;
			Direction facing;
			switch (plan) {
				case MEZZANINE -> {
					int row = slot / 3;
					int lz = (slot % 3 - 1) * 8;
					stand = v.at(5 + 2 * row, 2, lz);
					facing = out;
				}
				default -> {
					int c = plan == TowerLayout.Plan.CELLS ? (4 + w) / 2 : 1 + w / 2;
					int zc = (slot / 2 - 1) * (plan == TowerLayout.Plan.CELLS ? 10 : 9);
					boolean north = (slot & 1) == 0;
					stand = v.at(c, 1, north ? zc + 1 : zc - 2);
					facing = north ? Direction.NORTH : Direction.SOUTH;
				}
			}
			desk(v.c, i, slot, thread, stand, facing);
		}
		boolean first = layout.modulesOf(mod.projectKey()).get(0) == mod;
		Anchor door = Anchor.at(AnchorNames.module(i), v.at(1, 1, 0), out);
		Anchor walk = Anchor.at(AnchorNames.walkModule(i), v.at(2, 1, -4), out);
		b.anchor(door);
		b.anchor(walk);
		if (first) {
			b.anchor(door.renamed(AnchorNames.wing(mod.projectKey())));
			b.anchor(walk.renamed(AnchorNames.walk(mod.projectKey())));
		}
	}

	private HqStyle.SignText sign(HqLayout.Module w) {
		String label = w.label();
		String l1 = label.length() <= SignTags.LINE ? label : label.substring(0, SignTags.LINE);
		String l2 = label.length() <= SignTags.LINE ? "" : label.substring(SignTags.LINE);
		List<HqLayout.Module> mine = layout.modulesOf(w.projectKey());
		int nth = mine.indexOf(w);
		return new HqStyle.SignText(HqStyle.envColor(w), l1, l2, nth > 0 ? "#" + (nth + 1) : "", w.envLabel());
	}

	/** A standing desk: 3 desk tops across, the 3×2 screen on them, the free standing cell, anchors. */
	private void desk(PlanBuilder.Canvas c, int module, int slot, String threadId, BlockPos stand, Direction f) {
		BlockPos desk = stand.relative(f);
		Direction right = f.getClockWise();
		for (BlockPos d : List.of(desk.relative(right.getOpposite()), desk, desk.relative(right))) {
			c.set(d, kit.desk(f.getOpposite()));
		}
		BlockPos monitor = desk.above();
		Direction screenFacing = f.getOpposite();
		BlockPos origin = Workstation.screenOriginFor(monitor, screenFacing);
		for (BlockPos p : Workstation.screenCells(origin, screenFacing.getCounterClockWise())) {
			c.set(p, style.computer(true));
		}
		c.set(stand, AIR);
		c.set(stand.above(), AIR);
		Anchor deskA = Anchor.at(AnchorNames.deskSlot(module, slot), stand, f);
		Anchor screen = Anchor.face(AnchorNames.screenSlot(module, slot), monitor, screenFacing);
		b.workstation(new Workstation(module, slot, threadId, deskA, monitor, screen, origin));
		b.anchor(deskA.renamed(AnchorNames.desk(threadId)));
		b.anchor(screen.renamed(AnchorNames.monitor(threadId)));
	}

	// ------------------------------------------------------------------ roofs

	private void roof(TowerLayout.Tower t) {
		int y0 = t.roofY();
		int top = t == T1 ? TowerLayout.TOP : y0 + 6;
		Mat m = mat(t);
		int px = t.px();
		int pz = t.pz();
		int a = t.atrium();
		Box box = Box.of(wx(t, -px), y0, wz(t, -pz), wx(t, px), top, wz(t, pz));
		try (PlanBuilder.Canvas c = b.module("t" + t.id() + "_roof", box, List.of())) {
			int lane = TowerLayout.lane(t.floors());
			for (int lx = -px; lx <= px; lx++) {
				for (int lz = -pz; lz <= pz; lz++) {
					int x = wx(t, lx);
					int z = wz(t, lz);
					int ring = Math.max(Math.abs(lx), Math.abs(lz));
					boolean edge = Math.abs(lx) == px || Math.abs(lz) == pz;
					boolean hole = t == T1 && lx >= t.x0() && lx < t.x0() + t.run() && lz >= TowerLayout.laneZ(lane) && lz <= TowerLayout.laneZ(lane) + 1;
					BlockState s;
					if (hole || (t == T1 && ring < a)) {
						s = AIR;
					} else if (ring <= a) {
						s = m.glass();
					} else {
						s = edge ? m.band() : Math.abs(lx) == t.gallery() + 1 || Math.abs(lz) == t.gallery() + 1 ? accent() : m.roof();
					}
					c.set(x, y0, z, s);
					if (edge) {
						Direction e = Math.abs(lz) == pz ? (lz < 0 ? Direction.NORTH : Direction.SOUTH) : (lx < 0 ? Direction.WEST : Direction.EAST);
						c.set(x, y0 + 1, z, t == T3 ? (Math.floorMod(lx + lz, 2) == 0 ? m.band() : AIR) : kit.railing(e));
					}
				}
			}
			if (t == T1) {
				lookout(c, m);
			} else if (t == T2) {
				for (int sx = -1; sx <= 1; sx += 2) {
					for (int sz = -1; sz <= 1; sz += 2) {
						tree(c, wx(t, sx * 10), y0 + 1, wz(t, sz * 8), greenLeaves(), log(Blocks.OAK_LOG), 2);
						for (int y = y0 + 1; y <= y0 + 5; y++) {
							c.set(wx(t, sx * (px - 1)), y, wz(t, sz * (pz - 1)), m.column());
						}
					}
				}
				for (int lx = -px + 1; lx <= px - 1; lx++) {
					c.set(wx(t, lx), y0 + 6, wz(t, -(pz - 1)), m.column());
					c.set(wx(t, lx), y0 + 6, wz(t, pz - 1), m.column());
				}
			} else {
				// T3: a water tank on stilts and a crown of stone
				for (int lx = 8; lx <= 12; lx++) {
					for (int lz = -10; lz <= -6; lz++) {
						boolean shell = lx == 8 || lx == 12 || lz == -10 || lz == -6;
						for (int y = y0 + 1; y <= y0 + 5; y++) {
							if (shell || y == y0 + 5) {
								c.set(wx(t, lx), y, wz(t, lz), y == y0 + 5 ? m.band() : Blocks.DEEPSLATE_TILES.defaultBlockState());
							}
						}
					}
				}
				for (int lx = -px + 1; lx <= px - 1; lx += 4) {
					for (int y = y0 + 1; y <= y0 + 3; y++) {
						c.set(wx(t, lx), y, wz(t, -(pz - 1)), m.column());
						c.set(wx(t, lx), y, wz(t, pz - 1), m.column());
					}
				}
			}
		}
	}

	/** T1's roof: the lookout (scheduled tasks), the glass cupola over the atrium with the beacon. */
	private void lookout(PlanBuilder.Canvas c, Mat m) {
		int y0 = T1.roofY();
		int a = T1.atrium();
		for (int k = 0; k < a; k++) {
			int r = a - k;
			for (int lx = -r; lx <= r; lx++) {
				for (int lz = -r; lz <= r; lz++) {
					if (Math.max(Math.abs(lx), Math.abs(lz)) == r) {
						c.set(lx, y0 + 1 + k, lz, m.glass());
					}
				}
			}
		}
		BlockPos beacon = new BlockPos(0, y0 + 1 + a, 0);
		c.set(beacon, style.beacon());
		b.anchor(Anchor.at(AnchorNames.BEACON, beacon, Direction.SOUTH));
		// the pavilion on the north band, with the scheduled-tasks screen
		int n1 = -T1.pz() + 1;
		int n2 = -T1.gallery() - 1;
		for (int x : new int[] {-9, -3, 3, 9}) {
			for (int z : new int[] {n1, n2}) {
				for (int y = y0 + 1; y <= y0 + 4; y++) {
					c.set(x, y, z, kit.beam(Direction.Axis.Y));
				}
			}
		}
		for (int x = -9; x <= 9; x++) {
			for (int z = n1; z <= n2; z++) {
				c.set(x, y0 + 5, z, m.band());
			}
			for (int y = y0 + 1; y <= y0 + 4; y++) {
				if (Math.abs(x) != 9 && Math.abs(x) != 3) {
					c.set(x, y, n1, Math.abs(x) <= 2 ? wall() : y == y0 + 4 ? m.band() : m.pane());
				}
			}
		}
		HqGeometry.Surface s = new HqGeometry.Surface(new BlockPos(-2, y0 + 2, n1 + 1), Direction.EAST, Direction.SOUTH, 5, 3);
		bigScreen(c, s, "schedule", "");
		for (int x = -2; x <= 2; x++) {
			c.set(x, y0 + 1, n1 + 1, kit.beam(Direction.Axis.X));
		}
		int w = 0;
		for (int x : new int[] {-6, -2, 2, 6}) {
			b.anchor(Anchor.at("walk_schedule_" + w++, new BlockPos(x, y0 + 1, n2), Direction.NORTH));
		}
		for (int x : new int[] {-16, -12, 12, 16}) {
			c.set(x, y0 + 1, 0, kit.gardenPlanter());
		}
		// the antenna that tops the skyline
		for (int y = y0 + 1; y < TowerLayout.TOP; y++) {
			c.set(17, y, -12, kit.beam(Direction.Axis.Y));
		}
		c.set(17, TowerLayout.TOP, -12, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
	}

	// ------------------------------------------------------------------ review bridge

	/** The glass review bridge across the atrium on floor 3. */
	private void review() {
		b.group("review");
		Box r = TowerLayout.REVIEW;
		int y0 = r.minY();
		try (PlanBuilder.Canvas c = b.module("review", r, List.of())) {
			for (int x = r.minX(); x <= r.maxX(); x++) {
				for (int z = r.minZ(); z <= r.maxZ(); z++) {
					boolean side = x == r.minX() || x == r.maxX();
					c.set(x, y0, z, side ? mat(T1).band() : Blocks.GLASS.defaultBlockState());
					c.set(x, r.maxY(), z, Blocks.GLASS.defaultBlockState());
					if (side) {
						for (int y = y0 + 1; y < r.maxY(); y++) {
							c.set(x, y, z, Blocks.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_BLUE).defaultBlockState());
						}
					}
				}
			}
			c.set(4, y0 + 1, 0, style.bench(Direction.EAST));
			b.anchor(Anchor.at(AnchorNames.bench(bench++), new BlockPos(3, y0 + 1, 0), Direction.WEST));
			c.set(4, y0 + 1, 3, kit.gardenPlanter());
			c.set(4, y0 + 1, -3, kit.gardenPlanter());
			b.anchor(Anchor.at("walk_review_0", new BlockPos(3, y0 + 1, -4), Direction.WEST));
			b.anchor(Anchor.at("walk_review_1", new BlockPos(3, y0 + 1, 4), Direction.WEST));
		}
	}

	// ------------------------------------------------------------------ podium halls, bridges

	private void podiumHall(TowerLayout.Tower t) {
		Box h = t == T2 ? TowerLayout.PODIUM_EAST : TowerLayout.PODIUM_WEST;
		b.group(t == T2 ? "podium_east" : "podium_west");
		Mat m = mat(t);
		try (PlanBuilder.Canvas c = b.module(t == T2 ? "podium_east" : "podium_west", h, List.of())) {
			for (int x = h.minX(); x <= h.maxX(); x++) {
				for (int z = h.minZ(); z <= h.maxZ(); z++) {
					boolean wallRow = z == h.minZ() || z == h.maxZ();
					c.set(x, 0, z, wallRow ? m.band() : Math.abs(z - (t == T2 ? -4 : -11)) <= 1 ? accent() : floor());
					c.set(x, h.maxY(), z, wallRow ? m.band() : Blocks.MOSS_BLOCK.defaultBlockState());
					if (wallRow) {
						for (int y = 1; y < h.maxY(); y++) {
							c.set(x, y, z, Math.floorMod(x, 4) == 0 || y == h.maxY() - 1 ? m.column() : m.pane());
						}
					}
				}
				if (Math.floorMod(x, 5) == 0) {
					c.set(x, 1, h.minZ() + 1, kit.gardenPlanter());
				}
			}
			b.anchor(Anchor.at("walk_" + (t == T2 ? "podium_east" : "podium_west"), new BlockPos((h.minX() + h.maxX()) / 2, 1, t == T2 ? -4 : -11),
					t == T2 ? Direction.EAST : Direction.WEST));
		}
	}

	private void bridge(TowerLayout.Bridge br) {
		b.group(br.id());
		Box box = br.box();
		int n = Math.abs(br.xb() - br.xa()) + 1;
		int d = br.yb() - br.ya();
		int steps = Math.abs(d) - 1;
		int s0 = (n - Math.max(steps, 0)) / 2;
		Direction up = d > 0 ? (br.dir() > 0 ? Direction.EAST : Direction.WEST) : (br.dir() > 0 ? Direction.WEST : Direction.EAST);
		Mat m = mat(br.to());
		try (PlanBuilder.Canvas c = b.module(br.id(), box, List.of())) {
			for (int i = 0; i < n; i++) {
				int x = br.xa() + br.dir() * i;
				// surface level of this cell: flat ya before the steps, yb after, stairs in between
				int k = i - s0;
				int slabY;
				BlockState top;
				if (k < 0) {
					slabY = br.ya();
					top = null;
				} else if (k >= steps) {
					slabY = br.yb();
					top = null;
				} else {
					slabY = br.ya() + Integer.signum(d) * (k + 1);
					top = stairs(m.stairs(), up);
				}
				int low = box.minY();
				for (int z = br.z1() - 1; z <= br.z2() + 1; z++) {
					boolean wallRow = z < br.z1() || z > br.z2();
					for (int y = low; y < slabY; y++) {
						c.set(x, y, z, mat(T1).band());
					}
					c.set(x, slabY, z, top != null && !wallRow ? top : wallRow ? mat(T1).band() : accent());
					int roof = Math.max(br.ya(), br.yb()) + 4;
					for (int y = slabY + 1; y < roof; y++) {
						c.set(x, y, z, wallRow ? (Math.floorMod(x, 5) == 0 ? mat(T1).column() : Blocks.GLASS_PANE.defaultBlockState()) : AIR);
					}
					c.set(x, roof, z, Blocks.GLASS.defaultBlockState());
				}
			}
			b.anchor(Anchor.at("walk_" + br.id(), new BlockPos(br.xa() + br.dir(), br.ya() + 1, (br.z1() + br.z2()) / 2),
					br.dir() > 0 ? Direction.EAST : Direction.WEST));
		}
	}
}
