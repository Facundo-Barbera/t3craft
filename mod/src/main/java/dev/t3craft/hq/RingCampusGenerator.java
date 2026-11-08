package dev.t3craft.hq;

import dev.t3craft.hq.RingCampusGeometry.DeskSpot;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Lot;
import dev.t3craft.hq.RingCampusGeometry.Node;
import dev.t3craft.hq.RingCampusGeometry.Room;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * Plan of the hybrid layout {@value RingCampusGeometry#ID} ({@link RingCampusStyle}, geometry in
 * {@link RingCampusGeometry}). Pure and deterministic: {@link HqLayout} in, {@link BuildPlan} out.
 *
 * <p>It paints the ground column by column (each ground column belongs to one
 * {@link RingCampusGeometry.Zone}), then the hub, the core (patio, bridges, elevated promenade,
 * ramps), the node rooms and the barrios; then it <b>lights</b> the plan: every cell an NPC can walk
 * to (approximated from the spawn with one-block steps) must get block light ≥ {@value #MIN_LIGHT}
 * from the lights of its own construction group, and where it does not, the generator embeds a light
 * block in the floor under it (or in the wall or ceiling next to it). Lights only ever come from
 * blocks built into floors, walls, ceilings and pillars: there are no loose lanterns.
 *
 * <p>Groups (the construction units): {@code hub}, {@code cloister} (patio, bridges, promenade,
 * ramps, terraces, inner street), {@code archive} (the archive cloister), {@code node_<k>},
 * {@code ring_<m>} (avenues, street and empty lots of ring m), {@code lot_<id>} (a barrio's
 * courtyard, paths, gardens and gatehouse), {@code module_<index>} (one floor of a hall) and
 * {@code grounds} (the lawn). A hall's cells depend only on its own modules, so adding a project
 * never touches the existing halls, nodes, hub or core.
 */
final class RingCampusGenerator {
	static final int MIN_LIGHT = 10;
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final int SOLID = 0;
	private static final int DECOR = 1;
	private static final int HANGING = 2;
	private static final int Y0 = RingCampusGeometry.PODIUM;
	private static final int FH = RingCampusGeometry.FLOOR_H;

	private final RingCampusStyle style;
	private final HqKit kit;

	private final Map<Long, BlockState> cells = new HashMap<>();
	private final Map<Long, CompoundTag> data = new HashMap<>();
	private final Map<Long, Integer> layers = new HashMap<>();
	/** Fixture cells the light pass never replaces (desks, screens, terminal, board). */
	private final Set<Long> fixtures = new HashSet<>();
	private final TreeMap<String, Anchor> anchors = new TreeMap<>();
	private final List<Workstation> workstations = new ArrayList<>();
	private int walk;
	private int bench;

	private int rings;
	private HqLayout layout;
	/** lot → module slot b (0..5) → module. */
	private final Map<Integer, TreeMap<Integer, HqLayout.Module>> lots = new TreeMap<>();
	private Map<Long, Zone> zones = Map.of();
	private final Map<Integer, List<Long>> lotCols = new TreeMap<>();
	/** Ground columns of the built halls: {lot, hall}. */
	private final Map<Long, int[]> hallCols = new HashMap<>();

	RingCampusGenerator(RingCampusStyle style) {
		this.style = style;
		this.kit = HqKit.active();
	}

	BuildPlan generate(HqLayout layout) {
		this.layout = layout;
		List<Integer> idx = new ArrayList<>();
		for (HqLayout.Module m : layout.modules()) {
			int lot = RingCampusGeometry.lotOf(m.index());
			if (lot >= 0) {
				idx.add(m.index());
				lots.computeIfAbsent(lot, k -> new TreeMap<>()).put(m.index() % RingCampusGeometry.LOT_STRIDE, m);
			}
		}
		rings = RingCampusGeometry.rings(idx);
		int R = RingCampusGeometry.radius(rings);
		Map<Long, Zone> zs = new HashMap<>();
		for (int x = -R; x <= R; x++) {
			for (int z = -R; z <= R; z++) {
				Zone zn = RingCampusGeometry.zone(x, z, rings);
				if (zn.kind() != Kind.NONE) {
					zs.put(col(x, z), zn);
					if (zn.kind() == Kind.LOT) {
						lotCols.computeIfAbsent(zn.lot(), k -> new ArrayList<>()).add(col(x, z));
					}
				}
			}
		}
		this.zones = zs;
		for (Map.Entry<Long, Zone> e : zs.entrySet()) {
			ground(colX(e.getKey()), colZ(e.getKey()), e.getValue());
		}
		hub();
		core();
		for (int k = 0; k < 8; k++) {
			node(RingCampusGeometry.node(k));
		}
		int lotCount = RingCampusGeometry.base(rings + 1);
		for (int lot = 0; lot < lotCount; lot++) {
			TreeMap<Integer, HqLayout.Module> mods = lots.get(lot);
			if (mods == null) {
				emptyLot(lot);
			} else {
				barrio(lot, mods);
			}
		}
		anchor(Anchor.at(AnchorNames.SPAWN, SPAWN, Direction.NORTH));
		return build();
	}

	/** Where NPCs appear: the inner street, south, in front of the main ramp. */
	static final BlockPos SPAWN = new BlockPos(0, 1, 38);

	// ------------------------------------------------------------------ cell helpers

	private static long col(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	private static int colX(long c) {
		return (int) (c >> 32);
	}

	private static int colZ(long c) {
		return (int) c;
	}

	private void set(int x, int y, int z, BlockState s) {
		put(x, y, z, s, SOLID);
	}

	private void set(BlockPos p, BlockState s) {
		put(p.getX(), p.getY(), p.getZ(), s, SOLID);
	}

	private void put(int x, int y, int z, BlockState s, int layer) {
		long k = BlockPos.asLong(x, y, z);
		cells.put(k, s);
		layers.put(k, layer);
		data.remove(k);
	}

	private void fixture(BlockPos p, BlockState s) {
		set(p, s);
		fixtures.add(p.asLong());
	}

	private void fill(int x, int y1, int y2, int z, BlockState s) {
		for (int y = y1; y <= y2; y++) {
			set(x, y, z, s);
		}
	}

	private @Nullable BlockState at(int x, int y, int z) {
		return cells.get(BlockPos.asLong(x, y, z));
	}

	private Zone zoneAt(int x, int z) {
		Zone zn = zones.get(col(x, z));
		return zn == null ? Zone.NONE : zn;
	}

	private void anchor(Anchor a) {
		if (anchors.put(a.name(), a) != null) {
			throw new IllegalStateException("duplicate anchor " + a.name());
		}
	}

	private void walkSpot(BlockPos p, Direction f) {
		anchor(Anchor.at(AnchorNames.walkHub(walk++), p, f));
	}

	/** A light every 41 cells in a lattice that leaves no open floor cell more than 4 steps from one. */
	private static boolean lattice(int x, int z) {
		return Math.floorMod(4 * x + 5 * z, 41) == 0;
	}

	// ------------------------------------------------------------------ materials (cherry palette)

	private static BlockState b(Block b) {
		return b.defaultBlockState();
	}

	private static final BlockState FOOT = b(Blocks.DEEPSLATE_TILES);
	private static final BlockState BAMBOO = b(Blocks.BAMBOO_MOSAIC);
	private static final BlockState CALCITE = b(Blocks.CALCITE);
	private static final BlockState LOG = b(Blocks.CHERRY_LOG);
	private static final BlockState PLANKS = b(Blocks.CHERRY_PLANKS);
	private static final BlockState GLASS = b(Blocks.GLASS);
	private static final BlockState GRASS = b(Blocks.GRASS_BLOCK);
	private static final BlockState MOSS = b(Blocks.MOSS_BLOCK);
	private static final BlockState WATER = b(Blocks.WATER);
	private static final BlockState RIB = b(Blocks.POLISHED_BLACKSTONE_BRICKS);
	/** The light built into floors, walls, ceilings and pillars. */
	static final BlockState LIGHT = b(Blocks.PEARLESCENT_FROGLIGHT);

	private BlockState floor() {
		return kit.floor();
	}

	private BlockState accent() {
		return kit.floorAccent();
	}

	private static BlockState slab(Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
	}

	private static BlockState stairs(Block b, Direction facing) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM);
	}

	private static BlockState leaves(Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
	}

	static BlockState terracotta(DyeColor c) {
		return Blocks.DYED_TERRACOTTA.pick(c).defaultBlockState();
	}

	/** A barrio's wall and roof materials (each barrio picks one of three by its lot id). */
	private record Mats(BlockState wall, BlockState post, BlockState band, Block roof, BlockState ridge, BlockState ceiling) {
	}

	private Mats mats(int lot, DyeColor env) {
		return switch (Math.floorMod(lot * 5 + RingCampusGeometry.ringOfLot(lot), 3)) {
			case 0 -> new Mats(kit.wallPanel(false), kit.beam(Direction.Axis.Y), kit.beam(Direction.Axis.Y), Blocks.CHERRY_STAIRS, PLANKS, PLANKS);
			case 1 -> new Mats(terracotta(env), LOG, CALCITE, Blocks.DEEPSLATE_TILE_STAIRS, FOOT, PLANKS);
			default -> new Mats(CALCITE, b(Blocks.CHERRY_WOOD), PLANKS, Blocks.BRICK_STAIRS, b(Blocks.BRICKS), b(Blocks.BAMBOO_PLANKS));
		};
	}

	// ------------------------------------------------------------------ ground

	private static int rampHalves(double r) {
		return (int) Math.max(0, Math.min(2 * Y0, Math.round(RingCampusGeometry.RAMP_R - r)));
	}

	private void stepped(int x, int z, int halves, BlockState full, BlockState half) {
		int solidTop = halves / 2;
		fill(x, 0, solidTop, z, full);
		if (halves % 2 == 1) {
			set(x, solidTop + 1, z, half);
		}
	}

	private void ground(int x, int z, Zone zn) {
		double r = RingCampusGeometry.r(x, z);
		switch (zn.kind()) {
			case HUB, HUB_WALL, GALLERY -> set(x, 0, z, FOOT);
			case BRIDGE, PATIO -> patioGround(x, z, r);
			case RAMP -> stepped(x, z, rampHalves(r), BAMBOO, slab(Blocks.BAMBOO_MOSAIC_SLAB));
			case TERRACE -> {
				int h = rampHalves(r);
				stepped(x, z, h - h % 2, FOOT, FOOT);
				int top = h / 2;
				set(x, top, z, (x * 7 + z * 13 & 7) == 0 ? MOSS : GRASS);
				if (Math.floorMod(x * 31 + z * 17, 23) == 0 && top + 1 < 6) {
					set(x, top + 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
				}
			}
			case INNER_STREET, STREET -> set(x, 0, z, lattice(x, z) ? LIGHT : ((int) Math.floor(r) % 3 == 0 ? FOOT : accent()));
			case AVENUE -> set(x, 0, z, lattice(x, z) ? LIGHT
					: RingCampusGeometry.radialDistance(x, z, RingCampusGeometry.lotsIn(zn.ring())) > 2.4 ? FOOT : accent());
			case LOT -> set(x, 0, z, lattice(x, z) ? LIGHT : GRASS);
			case NODE -> set(x, 0, z, floor());
			case LAWN -> set(x, 0, z, GRASS);
			default -> {
			}
		}
	}

	private void patioGround(int x, int z, double r) {
		if (r > 17.5) {
			set(x, 0, z, lattice(x, z) ? LIGHT : accent());
		} else if (r > 15.5 && RingCampusGeometry.radialDistance(x, z, 8) > 1.2) {
			set(x, -1, z, FOOT);
			set(x, 0, z, WATER);
		} else if (r > 15.5) {
			set(x, 0, z, FOOT);
		} else {
			set(x, 0, z, lattice(x, z) ? LIGHT : (x + z & 3) == 0 ? MOSS : GRASS);
		}
	}

	// ------------------------------------------------------------------ hub: a basilica on the podium

	/** Aisle ceiling (and outer walls' top). */
	private static final int AISLE_TOP = 12;
	/** Nave vault: springs at y = 16 (12 above the floor), crown at y = 21 (17 above). */
	private static final int SPRING = 16;

	private static int vaultY(int ax) {
		if (ax >= 6) {
			return SPRING;
		}
		return SPRING + (int) Math.round(5 * Math.sqrt(1 - (ax / 6.0) * (ax / 6.0)));
	}

	private void hub() {
		for (int x = -14; x <= 14; x++) {
			for (int z = -14; z <= 14; z++) {
				Kind k = zoneAt(x, z).kind();
				if (k == Kind.HUB) {
					double o = RingCampusGeometry.octagon(x, z);
					set(x, Y0, z, lattice(x, z) ? LIGHT : (o <= 3 || (int) o % 4 == 0 ? accent() : floor()));
					nave(x, z);
				} else if (k == Kind.HUB_WALL) {
					fill(x, 1, Y0, z, FOOT);
					hubWall(x, z);
				}
			}
		}
		board();
		// benches facing the screens: the kanban wall (west) and the big screen (east)
		for (int sx = -1; sx <= 1; sx += 2) {
			Direction look = sx > 0 ? Direction.EAST : Direction.WEST;
			for (int z = -3; z <= 3; z++) {
				BlockPos p = new BlockPos(7 * sx, Y0 + 1, z);
				set(p, style.bench(look.getOpposite()));
				anchor(Anchor.at(AnchorNames.bench(bench++), p.relative(look), look));
			}
		}
		HqGeometry.Surface screen = new HqGeometry.Surface(new BlockPos(12, Y0 + 2, -3), Direction.SOUTH, Direction.WEST, 7, 4);
		bigScreen(screen, "hub", HqGenerator.hubBinding(layout), true);
		HqGeometry.Surface wall = new HqGeometry.Surface(new BlockPos(-12, Y0 + 2, 4), Direction.NORTH, Direction.EAST, 9, 4);
		for (BlockPos p : wall.cells()) {
			fixture(p, style.screen());
		}
		for (int i = 0; i < wall.width(); i++) {
			fixture(wall.origin().relative(wall.along(), i).below(), style.screenBase());
		}
		anchor(Anchor.face(AnchorNames.THREAD_WALL, wall.origin(), wall.facing()));
		anchor(Anchor.face(AnchorNames.THREAD_WALL_END, wall.origin().relative(wall.along(), wall.width() - 1).above(wall.height() - 1), wall.facing()));
		anchor(Anchor.at(AnchorNames.THREAD_WALL_FRONT, new BlockPos(-9, Y0 + 1, 0), Direction.WEST));
		// hologram column free over the centre; the beacon crowns the vault
		for (int y = Y0 + 1; y <= Y0 + 1 + HqGeometry.HOLOGRAM_TOP; y++) {
			set(0, y, 0, AIR);
		}
		anchor(Anchor.at(AnchorNames.HUB, new BlockPos(0, Y0 + 1, 0), Direction.SOUTH));
		anchor(new Anchor(AnchorNames.HOLOGRAM, 0.5, Y0 + 5.0, 0.5, Direction.SOUTH, new BlockPos(0, Y0 + 5, 0)));
		anchor(Anchor.at(AnchorNames.ENTRANCE, new BlockPos(0, Y0 + 1, 11), Direction.SOUTH));
		BlockPos beacon = new BlockPos(0, vaultY(0) + 1, 0);
		set(beacon, style.beacon());
		fixtures.add(beacon.asLong());
		anchor(Anchor.at(AnchorNames.BEACON, beacon, Direction.SOUTH));
		for (int wz : new int[] {-4, 4}) {
			for (int sx = -1; sx <= 1; sx += 2) {
				walkSpot(new BlockPos(4 * sx, Y0 + 1, wz), Direction.SOUTH);
			}
		}
	}

	private static boolean arcadeZ(int z) {
		return Math.floorMod(z + 2, 4) == 0 && Math.abs(z) <= 10;
	}

	/** One interior column of the hub: the nave's vault, the arcades, clerestory and the aisles' ceiling. */
	private void nave(int x, int z) {
		int ax = Math.abs(x);
		if (ax == 6) {
			if (arcadeZ(z)) {
				fill(x, Y0 + 1, AISLE_TOP - 1, z, LOG);
			}
			set(x, AISLE_TOP, z, kit.beam(Direction.Axis.Z));
			for (int y = AISLE_TOP + 1; y < SPRING; y++) {
				set(x, y, z, arcadeZ(z) ? LOG : (y == AISLE_TOP + 2 ? kit.wallPanel(true) : GLASS));
			}
			set(x, SPRING, z, PLANKS);
		} else if (ax > 6) {
			set(x, AISLE_TOP, z, Math.floorMod(x * 3 + z * 5, 7) == 0 ? LIGHT : FOOT);
		} else {
			int top = vaultY(ax);
			int lo = Math.min(top, vaultY(ax + 1) + 1);
			for (int y = lo; y <= top; y++) {
				BlockState s;
				if (ax == 0 && Math.abs(z) > 1 && Math.floorMod(z, 4) != 0) {
					s = GLASS;
				} else if (Math.floorMod(z, 4) == 0) {
					s = RIB;
				} else {
					s = PLANKS;
				}
				set(x, y, z, s);
			}
		}
	}

	private void hubWall(int x, int z) {
		boolean naveEnd = Math.abs(x) <= 6 && Math.abs(z) >= 12;
		for (int y = Y0 + 1; y <= AISLE_TOP; y++) {
			BlockState s;
			if (y <= Y0 + 1) {
				s = FOOT;
			} else if (y == AISLE_TOP) {
				s = CALCITE;
			} else if (!naveEnd && y >= Y0 + 3 && y <= Y0 + 6 && Math.floorMod(x + z, 3) != 0) {
				s = GLASS;
			} else if (y == Y0 + 2 && Math.floorMod(x * 5 + z * 3, 6) == 0) {
				s = kit.wallPanel(true);
			} else {
				s = kit.wallPanel(false);
			}
			set(x, y, z, s);
		}
		if (naveEnd) {
			// gable of the nave up to the vault, a rose window in it
			int top = vaultY(Math.abs(x));
			for (int y = AISLE_TOP + 1; y <= top; y++) {
				set(x, y, z, Math.hypot(x, y - SPRING) <= 3.2 ? GLASS : CALCITE);
			}
		} else {
			set(x, AISLE_TOP + 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
		// main portal (south, 5 wide x 5 tall) and the north door under the board (3 x 3)
		if (z > 0 && Math.abs(x) <= 2) {
			fill(x, Y0 + 1, Y0 + 5, z, AIR);
		}
		if (z < 0 && Math.abs(x) <= 1) {
			fill(x, Y0 + 1, Y0 + 3, z, AIR);
		}
	}

	/** The "needs attention" board at the nave's north end, facing the entrance, with its bulbs and standing rows. */
	private void board() {
		int z = -12;
		for (int x = -5; x <= 5; x++) {
			for (int y = Y0 + 4; y <= Y0 + 10; y++) {
				boolean edge = Math.abs(x) == 5 || y == Y0 + 4 || y == Y0 + 10 || y == Y0 + 9;
				fixture(new BlockPos(x, y, z), edge ? kit.beam(Direction.Axis.X) : style.palette().get(HqStyle.Role.BOARD));
			}
		}
		int lamp = 0;
		for (int x = -4; x <= 4; x += 2) {
			fixture(new BlockPos(x, Y0 + 9, z), style.statusLamp());
			anchor(Anchor.face(AnchorNames.boardLamp(lamp++), new BlockPos(x, Y0 + 9, z), Direction.SOUTH));
		}
		BlockPos sign = new BlockPos(0, Y0 + 10, z + 1);
		put(sign.getX(), sign.getY(), sign.getZ(), style.palette().get(HqStyle.Role.PLAQUE).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), DECOR);
		data.put(sign.asLong(), new HqStyle.SignText(DyeColor.ORANGE, "", "Needs", "attention", "").signTag());
		fixtures.add(sign.asLong());
		anchor(Anchor.face(AnchorNames.BOARD, new BlockPos(0, Y0 + 6, z), Direction.SOUTH));
		anchor(Anchor.at(AnchorNames.BOARD_FRONT, new BlockPos(0, Y0 + 1, z + 1), Direction.NORTH));
		int slot = 0;
		for (int row = 2; row <= 3; row++) {
			for (int i = 0; i <= 8; i++) {
				int x = i == 0 ? 0 : (i % 2 == 1 ? (i + 1) / 2 : -(i / 2));
				anchor(Anchor.at(AnchorNames.boardSlot(slot++), new BlockPos(x, Y0 + 1, z + row), Direction.NORTH));
			}
		}
	}

	/** A big screen and its anchor; {@code base}: a beam under its bottom row (free-standing screens). */
	private void bigScreen(HqGeometry.Surface s, String key, String binding, boolean base) {
		for (BlockPos p : s.cells()) {
			fixture(p, style.screen());
		}
		if (base) {
			for (int i = 0; i < s.width(); i++) {
				fixture(s.origin().relative(s.along(), i).below(), style.screenBase());
			}
		}
		anchor(Anchor.face(AnchorNames.bigScreen(key, s.width(), s.height(), binding), s.origin().above(s.height() - 1), s.facing()));
	}

	// ------------------------------------------------------------------ core: bridges, promenade, patio

	private void core() {
		Set<Long> stairHeads = new HashSet<>();
		// patio stairs: from the promenade down into the patio on the four diagonals
		for (int d = 0; d < 4; d++) {
			double a = Math.PI / 4 + d * Math.PI / 2;
			BlockPos p = RingCampusGeometry.polar(21.2, a, 0);
			int dx = p.getX() > 0 ? -1 : 1;
			Direction up = dx < 0 ? Direction.EAST : Direction.WEST;
			for (int w = -1; w <= 1; w++) {
				for (int i = 0; i < 4; i++) {
					int x = p.getX() + dx * i;
					int z = p.getZ() + w;
					for (int y = 1; y < Y0 - i; y++) {
						set(x, y, z, FOOT);
					}
					set(x, Y0 - i, z, stairs(Blocks.BAMBOO_MOSAIC_STAIRS, up));
					for (int y = Y0 - i + 1; y <= Y0 + 3; y++) {
						set(x, y, z, AIR);
					}
				}
				for (int i = 0; i <= 3; i++) {
					stairHeads.add(col(p.getX() - dx * i, p.getZ() + w));
				}
			}
		}
		List<Long> keys = new ArrayList<>(zones.keySet());
		Collections.sort(keys);
		for (long key : keys) {
			Kind k = zones.get(key).kind();
			int x = colX(key);
			int z = colZ(key);
			double r = RingCampusGeometry.r(x, z);
			if (k == Kind.BRIDGE) {
				bridge(x, z);
			} else if (k == Kind.GALLERY) {
				promenade(x, z, r, stairHeads.contains(key));
			} else if (k == Kind.PATIO) {
				patio(x, z, r);
			}
		}
		walkSpot(new BlockPos(19, 1, 0), Direction.WEST);
		walkSpot(new BlockPos(-19, 1, 0), Direction.EAST);
		walkSpot(new BlockPos(0, Y0 + 1, -24), Direction.NORTH);
		walkSpot(new BlockPos(24, Y0 + 1, 0), Direction.EAST);
	}

	private void bridge(int x, int z) {
		if (at(x, Y0, z) != null) {
			return; // patio stairs
		}
		set(x, Y0, z, Math.abs(x) <= 2 ? (lattice(x, z) ? LIGHT : accent()) : FOOT);
		if (Math.abs(x) == 3) {
			set(x, Y0 + 1, z, kit.railing(x > 0 ? Direction.EAST : Direction.WEST));
		}
		// arches under the deck: a lintel course and piers
		set(x, Y0 - 1, z, Math.abs(z) % 4 == 1 || Math.abs(x) == 3 ? FOOT : AIR);
		if (Math.abs(z) % 4 == 1 && Math.abs(x) == 3) {
			fill(x, 1, Y0 - 2, z, FOOT);
		}
	}

	/**
	 * The elevated ring: a deck at the podium level over a solid plinth, a railing on the patio side,
	 * light posts (log and a light block) round the outer edge between the ramps.
	 */
	private void promenade(int x, int z, double r, boolean stairHead) {
		if (at(x, Y0, z) != null) {
			return; // the top step of a patio stair
		}
		fill(x, 1, Y0 - 1, z, r <= RingCampusGeometry.PATIO_R + 1.2 && Math.floorMod(x * 3 + z * 7, 9) == 0 ? kit.wallPanel(true) : FOOT);
		set(x, Y0, z, lattice(x, z) ? LIGHT : (r > RingCampusGeometry.GALLERY_R - 1 ? FOOT : floor()));
		boolean inner = r <= RingCampusGeometry.PATIO_R + 1.2;
		if (inner && Math.abs(x) > 3 && !stairHead) {
			Direction edge = RingCampusGeometry.cardinal(RingCampusGeometry.theta(x, z)).getOpposite();
			set(x, Y0 + 1, z, kit.railing(edge));
		}
		if (r > RingCampusGeometry.GALLERY_R - 1 && RingCampusGeometry.radialDistance(x, z, 16) < 0.5
				&& RingCampusGeometry.radialDistance(x, z, 8) > 5) {
			fill(x, Y0 + 1, Y0 + 2, z, LOG);
			set(x, Y0 + 3, z, LIGHT);
		}
	}

	private void patio(int x, int z, double r) {
		if (at(x, 1, z) != null) {
			return; // stairs
		}
		for (int k = 0; k < 8; k++) {
			double a = (k + 0.5) * Math.PI / 4;
			BlockPos t = RingCampusGeometry.polar(19.3, a, 0);
			int dx = x - t.getX();
			int dz = z - t.getZ();
			if (dx == 0 && dz == 0) {
				fill(x, 1, 4, z, LOG);
				set(x, 5, z, leaves(Blocks.CHERRY_LEAVES));
				set(x, 6, z, leaves(Blocks.CHERRY_LEAVES));
			} else if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2 && Math.abs(dx) + Math.abs(dz) <= 3) {
				set(x, 5, z, leaves(Blocks.CHERRY_LEAVES));
				if (Math.abs(dx) + Math.abs(dz) <= 1) {
					set(x, 6, z, leaves(Blocks.CHERRY_LEAVES));
				}
			}
		}
		if (r <= 15.5 && Math.floorMod(x * 5 + z * 3, 11) == 0 && at(x, 0, z) != LIGHT) {
			set(x, 1, z, Math.floorMod(x + z, 2) == 0 ? b(Blocks.FLOWERING_AZALEA) : kit.gardenPlanter());
		}
	}

	// ------------------------------------------------------------------ nodes: the special rooms

	private void node(Node nd) {
		int R = 8;
		for (int x = nd.cx() - R; x <= nd.cx() + R; x++) {
			for (int z = nd.cz() - R; z <= nd.cz() + R; z++) {
				Zone zn = zoneAt(x, z);
				if (zn.kind() == Kind.NODE && zn.node() == nd.k()) {
					nodeCell(nd, x, z);
				}
			}
		}
		int cx = nd.cx();
		int cz = nd.cz();
		switch (nd.kind()) {
			case REVIEW_TOWER -> {
				bigScreen(new HqGeometry.Surface(new BlockPos(cx - 3, 5, cz - 5), Direction.EAST, Direction.SOUTH, 7, 4), "review", "", false);
				walkSpot(new BlockPos(cx, 1, cz - 2), Direction.NORTH);
			}
			case SCHEDULE_ROTUNDA -> {
				bigScreen(new HqGeometry.Surface(new BlockPos(cx + 4, 2, cz - 2), Direction.SOUTH, Direction.WEST, 5, 3), "schedule", "", true);
				walkSpot(new BlockPos(cx - 1, 1, cz), Direction.EAST);
			}
			case ARCHIVE_CLOISTER -> {
				BlockPos t = new BlockPos(cx + 3, 1, cz - 6);
				fixture(t, style.archiveTerminal(Direction.SOUTH));
				anchor(Anchor.face(AnchorNames.ARCHIVE_TERMINAL, t, Direction.SOUTH));
				anchor(Anchor.at(AnchorNames.ARCHIVE, t.south(), Direction.NORTH));
				walkSpot(new BlockPos(cx - 4, 1, cz), Direction.EAST);
			}
			case LOUNGE_SUNKEN -> walkSpot(new BlockPos(cx + 1, -2, cz), Direction.WEST);
			default -> {
			}
		}
	}

	private void nodeCell(Node nd, int x, int z) {
		int dx = x - nd.cx();
		int dz = z - nd.cz();
		double d = Math.hypot(dx, dz);
		switch (nd.kind()) {
			case REVIEW_TOWER -> tower(x, z, dx, dz);
			case SCHEDULE_ROTUNDA -> rotunda(x, z, dx, dz, d);
			case ARCHIVE_CLOISTER -> cloister(x, z, dx, dz);
			case LOUNGE_SUNKEN -> sunken(x, z, dx, dz, d);
			case PILLAR -> pillar(x, z, d);
		}
	}

	private static boolean door(int dx, int dz, int edge) {
		return Math.abs(dx) <= 1 && Math.abs(dz) >= edge || Math.abs(dz) <= 1 && Math.abs(dx) >= edge;
	}

	/** Review tower: square, 13 × 13, 20 high, an inner mezzanine band, a pyramid roof and a light on its spire. */
	private void tower(int x, int z, int dx, int dz) {
		int m = Math.max(Math.abs(dx), Math.abs(dz));
		set(x, 0, z, m <= 2 ? accent() : floor());
		int top = 20;
		if (m == 6) {
			boolean corner = Math.abs(dx) == 6 && Math.abs(dz) == 6 || Math.abs(dx) == 3 || Math.abs(dz) == 3;
			for (int y = 1; y <= top; y++) {
				BlockState s = corner ? LOG : (y % 5 == 0 ? kit.wallPanel(true) : (y >= 3 && Math.floorMod(dx + dz, 2) == 0 ? GLASS : CALCITE));
				set(x, y, z, s);
			}
			if (door(dx, dz, 6)) {
				fill(x, 1, 4, z, AIR);
			}
		}
		if (m == 5) {
			set(x, 10, z, FOOT);
		}
		int lvl = top + 1 + (6 - m);
		for (int y = top + 1; y <= lvl; y++) {
			set(x, y, z, y == lvl ? RIB : AIR);
		}
		if (m == 0) {
			set(x, lvl + 1, z, LOG);
			set(x, lvl + 2, z, LIGHT);
		}
	}

	/** Scheduled-tasks rotunda: a round hall (radius 7) under a dome, a clock ring of lights set in the floor. */
	private void rotunda(int x, int z, int dx, int dz, double d) {
		boolean clock = Math.abs(d - 5) < 0.5 && Math.floorMod(dx * 7 + dz * 3, 4) == 0;
		set(x, 0, z, clock ? LIGHT : Math.abs(d - 4) < 0.5 ? accent() : floor());
		int wallTop = 8;
		if (d > 6) {
			for (int y = 1; y <= wallTop; y++) {
				set(x, y, z, y == wallTop ? kit.wallPanel(true) : (y >= 3 && y <= 6 && Math.floorMod(dx * 3 + dz, 3) == 0 ? GLASS : FOOT));
			}
			if (door(dx, dz, 6)) {
				fill(x, 1, 4, z, AIR);
			}
		}
		double R = 7.2;
		int top = wallTop + (int) Math.round(Math.sqrt(Math.max(0, R * R - d * d)));
		double dn = Math.min(R, d + 1);
		int low = wallTop + (int) Math.round(Math.sqrt(Math.max(0, R * R - dn * dn))) + 1;
		for (int y = Math.max(wallTop + 1, Math.min(low, top)); y <= top; y++) {
			set(x, y, z, d < 1 ? GLASS : PLANKS);
		}
		if (d < 0.5) {
			set(x, top + 1, z, LIGHT);
		}
	}

	/**
	 * Archive cloister (after design/mediterranean): a square of 15 × 15, white walls with four gates,
	 * an arcaded gallery round a garden court with a pool; the archive terminal stands in the north
	 * gallery.
	 */
	private void cloister(int x, int z, int dx, int dz) {
		int mx = Math.max(Math.abs(dx), Math.abs(dz));
		int mn = Math.min(Math.abs(dx), Math.abs(dz));
		if (mx == 0) {
			set(x, -1, z, FOOT);
			set(x, 0, z, WATER);
		} else if (mx <= 2) {
			set(x, 0, z, mx == 1 ? accent() : GRASS);
			if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
				set(x, 1, z, kit.gardenPlanter());
			}
		} else if (mx == 3) {
			set(x, 0, z, accent());
		} else if (mx <= 6) {
			set(x, 0, z, floor());
		} else {
			set(x, 0, z, FOOT);
		}
		if (mx == 7) {
			for (int y = 1; y <= 5; y++) {
				set(x, y, z, y == 3 && mn % 4 == 2 ? kit.wallPanel(true) : CALCITE);
			}
			set(x, 6, z, PLANKS);
			set(x, 7, z, slab(Blocks.BRICK_SLAB));
			if (mn <= 1) {
				fill(x, 1, 3, z, AIR);
			}
		} else if (mx >= 3) {
			if (mx == 3 && mn >= 2) {
				fill(x, 1, 4, z, LOG);
			}
			set(x, 5, z, PLANKS);
			set(x, 6, z, slab(Blocks.BRICK_SLAB));
		}
	}

	/** Lounge: a sunken patio, a round amphitheatre of half steps down to y = -3 round a light column. */
	private void sunken(int x, int z, int dx, int dz, double d) {
		int k = (int) Math.max(0, Math.min(6, Math.round(7 - d)));
		long g = BlockPos.asLong(x, 0, z);
		cells.remove(g);
		layers.remove(g);
		int solid = k % 2 == 0 ? -k / 2 : -(k + 1) / 2;
		set(x, solid, z, k % 2 == 0 ? (k == 6 ? accent() : MOSS) : FOOT);
		if (k % 2 == 1) {
			set(x, solid + 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
		for (int y = solid + (k % 2 == 1 ? 2 : 1); y <= 2; y++) {
			set(x, y, z, AIR);
		}
		if (dx == 0 && dz == 0) {
			set(x, -2, z, LOG);
			set(x, -1, z, LOG);
			set(x, 0, z, LIGHT);
		}
		if (d > 6.2 && Math.abs(Math.abs(dx) - Math.abs(dz)) <= 1 && Math.abs(dx) >= 4) {
			set(x, 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			set(x, 2, z, leaves(Blocks.AZALEA_LEAVES));
		}
	}

	/** Diagonal nodes: a light pillar in a paved round. */
	private void pillar(int x, int z, double d) {
		set(x, 0, z, accent());
		if (d < 0.5) {
			fill(x, 1, 4, z, LOG);
			set(x, 5, z, LIGHT);
		}
	}

	// ------------------------------------------------------------------ barrios

	private void emptyLot(int lot) {
		Lot l = RingCampusGeometry.lot(lot);
		for (long c : lotCols.getOrDefault(lot, List.of())) {
			int x = colX(c);
			int z = colZ(c);
			if (Math.floorMod(x * 7 + z * 11, 29) == 0 && !lattice(x, z)) {
				set(x, 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			}
		}
		tree(l.centre().getX(), l.centre().getZ());
	}

	/** A cherry tree (trunk 4, canopy from y = 4) on a ground cell. */
	private void tree(int x, int z) {
		fill(x, 1, 4, z, LOG);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int m = Math.abs(dx) + Math.abs(dz);
				if (m == 0 || m > 2) {
					continue;
				}
				if (at(x + dx, 1, z + dz) != null && at(x + dx, 1, z + dz) != AIR) {
					continue; // a bush underneath: keep the canopy off it
				}
				set(x + dx, 4, z + dz, leaves(Blocks.CHERRY_LEAVES));
				if (m == 1) {
					set(x + dx, 5, z + dz, leaves(Blocks.CHERRY_LEAVES));
				}
			}
		}
		set(x, 5, z, leaves(Blocks.CHERRY_LEAVES));
		set(x, 6, z, leaves(Blocks.CHERRY_LEAVES));
	}

	private void barrio(int lot, TreeMap<Integer, HqLayout.Module> mods) {
		Lot l = RingCampusGeometry.lot(lot);
		HqLayout.Module lead = mods.firstEntry().getValue();
		DyeColor env = HqStyle.envColor(lead);
		Mats mats = mats(lot, env);
		int g = l.court();
		int reach = l.reach();
		for (long c : lotCols.getOrDefault(lot, List.of())) {
			int x = colX(c);
			int z = colZ(c);
			int a = Math.abs(l.a(x, z));
			int bb = Math.abs(l.b(x, z));
			if (a <= g && bb <= g) {
				set(x, 0, z, lattice(x, z) ? LIGHT : (a == g || bb == g ? accent() : floor()));
			} else if (a <= reach && bb <= reach && (a <= g + 1 || bb <= g + 1)) {
				set(x, 0, z, lattice(x, z) ? LIGHT : accent());
			} else if (a <= reach && bb <= reach) {
				if (Math.floorMod(a * 3 + bb * 5, 7) == 0 && !lattice(x, z)) {
					set(x, 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
				}
			} else if (Math.floorMod(x * 7 + z * 11, 29) == 0 && !lattice(x, z)) {
				set(x, 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			}
		}
		// the courtyard's centre: a cherry tree or a pool with planters
		BlockPos c = l.centre();
		if (lot % 2 == 0) {
			tree(c.getX(), c.getZ());
		} else {
			set(c.getX(), -1, c.getZ(), FOOT);
			set(c.getX(), 0, c.getZ(), WATER);
			for (int sa = -1; sa <= 1; sa += 2) {
				for (int sb = -1; sb <= 1; sb += 2) {
					set(l.at(sa, sb, 1), kit.gardenPlanter());
				}
			}
		}
		for (int h = 0; h < RingCampusGeometry.HALLS; h++) {
			HqLayout.Module ground = mods.get(h);
			HqLayout.Module upper = mods.get(h + RingCampusGeometry.HALLS);
			if (ground != null || upper != null) {
				hall(l, h, ground, upper, mats);
			} else {
				slotGarden(l.hall(h));
			}
		}
		gatehouse(l, lead, mats);
		HqLayout.Module first = layout.modulesOf(lead.projectKey()).get(0);
		Direction in = l.out();
		anchor(Anchor.at(AnchorNames.wing(first.projectKey()), l.at(-(g - 1), 0, 1), in));
		anchor(Anchor.at(AnchorNames.walk(first.projectKey()), l.at(2, 2, 1), in));
	}

	/** An empty hall slot: a little garden with a tree. */
	private void slotGarden(Room r) {
		for (int u = -1; u <= r.d(); u++) {
			for (int v = -1; v <= r.w(); v++) {
				BlockPos p = r.cell(u, v, 0);
				set(p, lattice(p.getX(), p.getZ()) ? LIGHT : ((u + v) % 5 == 0 ? MOSS : GRASS));
				if (r.corner(u, v) && !lattice(p.getX(), p.getZ())) {
					set(p.above(), leaves(Blocks.FLOWERING_AZALEA_LEAVES));
				}
			}
		}
		BlockPos t = r.cell(r.d() / 2, r.c(), 0);
		tree(t.getX(), t.getZ());
	}

	/**
	 * A work hall of one or two floors: walls with windows, the door on the courtyard, 6 standing
	 * desks per floor with their 3×2 screens, straight stairs up the centre line, one of three roofs.
	 */
	private void hall(Lot l, int h, HqLayout.@Nullable Module ground, HqLayout.@Nullable Module upper, Mats mats) {
		Room r = l.hall(h);
		int W = r.w();
		int D = r.d();
		int c = r.c();
		int F = upper != null ? 2 : 1;
		int top = F * FH;
		Set<Long> hole = new HashSet<>();
		if (F == 2) {
			for (int u = 2; u <= 5; u++) {
				hole.add(r.cell(u, c, FH).asLong());
			}
		}
		for (int u = -1; u <= D; u++) {
			for (int v = -1; v <= W; v++) {
				BlockPos g0 = r.cell(u, v, 0);
				hallCols.put(col(g0.getX(), g0.getZ()), new int[] {l.id(), h});
				boolean wall = r.wall(u, v);
				boolean corner = r.corner(u, v);
				set(g0, wall ? FOOT : (lattice(g0.getX(), g0.getZ()) ? LIGHT : floor()));
				Direction.Axis along = (u == -1 || u == D) ? r.side().getAxis() : r.back().getAxis();
				for (int f = 0; f < F; f++) {
					int yb = f * FH;
					if (wall) {
						int t = (u == -1 || u == D) ? v : u;
						for (int y = yb + 1; y < yb + FH; y++) {
							int ly = y - yb;
							BlockState s;
							if (corner) {
								s = mats.post();
							} else if (ly >= 2 && ly <= 4 && Math.floorMod(t, 3) != 0) {
								s = GLASS;
							} else {
								s = mats.wall();
							}
							set(g0.atY(y), s);
						}
						if (f == 0 && u == -1 && Math.abs(v - c) <= 1) {
							fill(g0.getX(), 1, 3, g0.getZ(), AIR);
						}
						set(g0.atY(yb + FH), corner ? mats.post() : (mats.band() == mats.post() ? kit.beam(along) : mats.band()));
					} else {
						for (int y = yb + 1; y < yb + FH; y++) {
							set(g0.atY(y), AIR);
						}
						BlockPos ceil = g0.atY(yb + FH);
						if (f < F - 1) {
							set(ceil, hole.contains(ceil.asLong()) ? AIR : (lattice(g0.getX(), g0.getZ()) ? LIGHT : floor()));
						} else {
							set(ceil, (u % 4 == 1 && v % 4 == 1) ? LIGHT : mats.ceiling());
						}
					}
				}
			}
		}
		roof(r, top, Math.floorMod(h + l.id(), 3), mats);
		if (F == 2) {
			for (int i = 0; i < RingCampusGeometry.STAIR_STEPS; i++) {
				BlockPos p = r.cell(1 + i, c, 1 + i);
				for (int y = 1; y <= i; y++) {
					set(p.atY(y), PLANKS);
				}
				set(p, stairs(Blocks.CHERRY_STAIRS, r.back()));
			}
		}
		for (HqLayout.Module mod : new HqLayout.Module[] {ground, upper}) {
			if (mod == null) {
				continue;
			}
			int f = RingCampusGeometry.floorOf(mod.index());
			int y = f * FH + 1;
			for (int slot = 0; slot < mod.capacity(); slot++) {
				String thread = mod.threadAt(slot);
				if (thread != null) {
					desk(mod.index(), r, f, slot, thread);
				}
			}
			BlockPos door = f == 0 ? r.cell(0, c - 1, y) : r.cell(7, c, y);
			BlockPos inside = f == 0 ? r.cell(2, c - 1, y) : r.cell(D - 2, c, y);
			anchor(Anchor.at(AnchorNames.module(mod.index()), door, r.back()));
			anchor(Anchor.at(AnchorNames.walkModule(mod.index()), inside, r.back()));
		}
	}

	/** Roofs: 0 gable (ridge along the depth), 1 hipped, 2 flat green roof behind a parapet. */
	private void roof(Room r, int top, int type, Mats mats) {
		int W = r.w();
		int D = r.d();
		int c = r.c();
		for (int u = -1; u <= D; u++) {
			for (int v = -1; v <= W; v++) {
				BlockPos p = r.cell(u, v, 0);
				if (type == 2) {
					if (r.wall(u, v)) {
						set(p.atY(top + 1), mats.wall());
						set(p.atY(top + 2), slab(Blocks.DEEPSLATE_TILE_SLAB));
					} else {
						set(p.atY(top + 1), Math.floorMod(u * 3 + v * 5, 7) == 0 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : b(Blocks.MOSS_CARPET));
					}
					continue;
				}
				int dv1 = v + 1;
				int dv2 = W - v;
				int du1 = u + 1;
				int du2 = D - u;
				int k = type == 0 ? Math.min(dv1, dv2) : Math.min(Math.min(dv1, dv2), Math.min(du1, du2));
				int y = top + Math.max(1, k);
				boolean ridge = type == 0 ? v == c : v == c && Math.min(du1, du2) > c + 1;
				BlockState s;
				if (ridge) {
					s = mats.ridge();
				} else {
					Direction facing;
					if (k == dv1) {
						facing = r.side();
					} else if (k == dv2) {
						facing = r.side().getOpposite();
					} else if (k == du1) {
						facing = r.back();
					} else {
						facing = r.back().getOpposite();
					}
					s = stairs(mats.roof(), facing);
				}
				set(p.atY(y), s);
				if (type == 0 && (u == -1 || u == D)) {
					for (int yy = top + 1; yy < y; yy++) {
						set(p.atY(yy), mats.wall());
					}
				}
			}
		}
	}

	/** The gatehouse on the hub side: an arched passage into the courtyard, the project's sign under the arch. */
	private void gatehouse(Lot l, HqLayout.Module lead, Mats mats) {
		Room r = l.gate();
		int W = r.w();
		int D = r.d();
		int c = r.c();
		for (int u = 0; u < D; u++) {
			for (int v = 0; v < W; v++) {
				BlockPos p = r.cell(u, v, 0);
				boolean passage = Math.abs(v - c) <= 1;
				set(p, passage ? accent() : FOOT);
				if (passage) {
					for (int y = 1; y <= 4; y++) {
						set(p.atY(y), AIR);
					}
					set(p.atY(5), kit.beam(r.back().getAxis()));
				} else {
					boolean corner = (u == 0 || u == D - 1) && (v == 0 || v == W - 1);
					for (int y = 1; y <= 5; y++) {
						set(p.atY(y), corner ? mats.post() : (y == 3 && u == D / 2 && (v == 0 || v == W - 1) ? kit.wallPanel(true) : mats.wall()));
					}
				}
				set(p.atY(6), mats.ridge());
				int k = Math.min(Math.min(u + 1, D - u), Math.min(v + 1, W - v));
				int kmax = Math.min((D + 1) / 2, (W + 1) / 2);
				BlockState s;
				if (k == kmax) {
					s = mats.ridge();
				} else if (k == u + 1) {
					s = stairs(mats.roof(), r.back());
				} else if (k == D - u) {
					s = stairs(mats.roof(), r.back().getOpposite());
				} else if (k == v + 1) {
					s = stairs(mats.roof(), r.side());
				} else {
					s = stairs(mats.roof(), r.side().getOpposite());
				}
				set(p.atY(6 + k), s);
			}
		}
		BlockPos sign = r.cell(D - 1, c, 4);
		BlockState hs = Blocks.CHERRY_HANGING_SIGN.defaultBlockState().setValue(CeilingHangingSignBlock.ROTATION, rotation(r.back()))
				.setValue(CeilingHangingSignBlock.ATTACHED, false);
		put(sign.getX(), sign.getY(), sign.getZ(), hs, HANGING);
		data.put(sign.asLong(), barrioSign(lead).signTag());
		fixtures.add(sign.asLong());
	}

	private static int rotation(Direction facing) {
		return switch (facing) {
			case SOUTH -> 0;
			case WEST -> 4;
			case NORTH -> 8;
			default -> 12;
		};
	}

	private HqStyle.SignText barrioSign(HqLayout.Module w) {
		String label = w.label();
		String l1 = label.length() <= SignTags.LINE ? label : label.substring(0, SignTags.LINE);
		String l2 = label.length() <= SignTags.LINE ? "" : label.substring(SignTags.LINE);
		int desks = 0;
		for (HqLayout.Module m : layout.modulesOf(w.projectKey())) {
			desks += m.desks().size();
		}
		return new HqStyle.SignText(HqStyle.envColor(w), l1, l2, desks + (desks == 1 ? " desk" : " desks"), w.envLabel());
	}

	private void desk(int module, Room r, int f, int slot, String thread) {
		DeskSpot d = RingCampusGeometry.desk(r, f, slot);
		Direction face = d.facing();
		Direction right = face.getClockWise();
		BlockPos desk = d.desk();
		for (int k = -1; k <= 1; k++) {
			fixture(desk.relative(right, k), kit.desk(face.getOpposite()));
		}
		BlockPos monitor = desk.above();
		Direction screenFacing = face.getOpposite();
		for (BlockPos p : Workstation.screenCells(Workstation.screenOriginFor(monitor, screenFacing), screenFacing.getCounterClockWise())) {
			fixture(p, style.computer(true));
		}
		BlockPos stand = d.stand();
		set(stand, AIR);
		set(stand.above(), AIR);
		Anchor deskA = Anchor.at(AnchorNames.deskSlot(module, slot), stand, face);
		Anchor screen = Anchor.face(AnchorNames.screenSlot(module, slot), monitor, screenFacing);
		workstations.add(new Workstation(module, slot, thread, deskA, monitor, screen, Workstation.screenOriginFor(monitor, screenFacing)));
		anchor(deskA.renamed(AnchorNames.desk(thread)));
		anchor(screen.renamed(AnchorNames.monitor(thread)));
	}

	// ------------------------------------------------------------------ groups

	/** The construction group of a cell (see the class comment). */
	String groupOf(int x, int y, int z) {
		Zone zn = zoneAt(x, z);
		return switch (zn.kind()) {
			case HUB, HUB_WALL -> "hub";
			case BRIDGE, PATIO, GALLERY, RAMP, TERRACE, INNER_STREET -> "cloister";
			case NODE -> zn.node() == 4 ? HqGenerator.GROUP_ARCHIVE : "node_" + zn.node();
			case AVENUE, STREET -> "ring_" + zn.ring();
			case LOT -> {
				TreeMap<Integer, HqLayout.Module> mods = lots.get(zn.lot());
				if (mods == null) {
					yield "ring_" + zn.ring();
				}
				int[] hall = hallCols.get(col(x, z));
				if (hall == null) {
					yield "lot_" + zn.lot();
				}
				int f = y >= FH ? 1 : 0;
				HqLayout.Module m = mods.get(f * RingCampusGeometry.HALLS + hall[1]);
				if (m == null) {
					m = mods.get((1 - f) * RingCampusGeometry.HALLS + hall[1]);
				}
				yield HqGenerator.group(m.index());
			}
			case LAWN -> "grounds";
			default -> throw new IllegalStateException("no zone at " + x + "," + z);
		};
	}

	private static int groupRank(String g) {
		if (g.equals("hub")) {
			return 0;
		}
		if (g.equals(HqGenerator.GROUP_ARCHIVE)) {
			return 1;
		}
		if (g.equals("cloister")) {
			return 2;
		}
		if (g.startsWith("node_")) {
			return 3;
		}
		if (g.startsWith("ring_")) {
			return 4;
		}
		if (g.startsWith("lot_")) {
			return 5;
		}
		if (g.startsWith("module_")) {
			return 6;
		}
		return 7;
	}

	private static final Comparator<String> GROUPS = Comparator.comparingInt(RingCampusGenerator::groupRank).thenComparing((a, b) -> {
		if (a.startsWith("module_") && b.startsWith("module_")) {
			return Integer.compare(Integer.parseInt(a.substring(7)), Integer.parseInt(b.substring(7)));
		}
		return a.compareTo(b);
	});

	// ------------------------------------------------------------------ light pass

	private boolean noCollision(long k) {
		BlockState s = cells.get(k);
		return s == null || s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.of(k)).isEmpty();
	}

	private boolean standable(int x, int y, int z) {
		if (zoneAt(x, z).kind() == Kind.NONE) {
			return false;
		}
		return noCollision(BlockPos.asLong(x, y, z)) && noCollision(BlockPos.asLong(x, y + 1, z)) && !noCollision(BlockPos.asLong(x, y - 1, z));
	}

	/**
	 * Cells an NPC may stand in, walking from the spawn by one-block steps up or down: a superset of
	 * what the client's navigation reaches (it also lets an NPC "stand" on railings), so lighting
	 * these lights every cell it really walks.
	 */
	private List<BlockPos> walkable() {
		Set<Long> seen = new HashSet<>();
		ArrayDeque<BlockPos> todo = new ArrayDeque<>();
		if (standable(SPAWN.getX(), SPAWN.getY(), SPAWN.getZ())) {
			seen.add(SPAWN.asLong());
			todo.add(SPAWN);
		}
		while (!todo.isEmpty()) {
			BlockPos p = todo.poll();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos n = p.relative(d).above(dy);
					if (n.getY() < style.bottom() || n.getY() > style.top() || seen.contains(n.asLong())) {
						continue;
					}
					if (standable(n.getX(), n.getY(), n.getZ())) {
						seen.add(n.asLong());
						todo.add(n);
					}
				}
			}
		}
		List<BlockPos> out = new ArrayList<>();
		for (long k : seen) {
			out.add(BlockPos.of(k));
		}
		out.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).thenComparingInt(p -> p.getZ()).thenComparingInt(p -> p.getX()));
		return out;
	}

	private int lightAt(PlanLight l, BlockPos feet) {
		int v = l.get(feet);
		BlockState below = cells.get(feet.below().asLong());
		if (below != null && below.getLightDampening() < 15) {
			v = Math.min(v, l.get(feet.below()));
		}
		return v;
	}

	private boolean replaceable(long k, String group, Map<Long, String> groups) {
		BlockState s = cells.get(k);
		return s != null && !s.isAir() && s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.of(k)) && s.getLightDampening() >= 15
				&& s.getLightEmission() == 0 && !fixtures.contains(k) && !data.containsKey(k)
				&& group.equals(groups.get(k));
	}

	/** Where to embed a light for a dark walkable cell: the floor, under a slab, the floor beside it, the ceiling. */
	private @Nullable Long lightSpot(BlockPos feet, String group, Map<Long, String> groups) {
		List<BlockPos> tries = new ArrayList<>();
		tries.add(feet.below());
		tries.add(feet.below(2));
		for (Direction d : Direction.Plane.HORIZONTAL) {
			tries.add(feet.below().relative(d));
		}
		for (BlockPos t : tries) {
			if (replaceable(t.asLong(), group, groups)) {
				return t.asLong();
			}
		}
		for (int y = feet.getY() + 2; y <= feet.getY() + 7; y++) {
			long k = BlockPos.asLong(feet.getX(), y, feet.getZ());
			BlockState s = cells.get(k);
			if (s != null && !s.isAir()) {
				return replaceable(k, group, groups) ? k : null;
			}
		}
		return null;
	}

	/** Lights every walkable cell (see the class comment) from its own group's lights. */
	private void light(Map<Long, String> groups) {
		TreeMap<String, List<BlockPos>> byGroup = new TreeMap<>();
		for (BlockPos p : walkable()) {
			Kind k = zoneAt(p.getX(), p.getZ()).kind();
			String g = groups.get(p.below().asLong());
			if (k == Kind.LAWN || g == null) {
				continue;
			}
			byGroup.computeIfAbsent(g, x -> new ArrayList<>()).add(p);
		}
		Map<String, List<Long>> emitters = new HashMap<>();
		for (Map.Entry<Long, BlockState> e : cells.entrySet()) {
			if (e.getValue().getLightEmission() > 0) {
				emitters.computeIfAbsent(groups.get(e.getKey()), x -> new ArrayList<>()).add(e.getKey());
			}
		}
		for (Map.Entry<String, List<BlockPos>> e : byGroup.entrySet()) {
			String g = e.getKey();
			PlanLight l = new PlanLight(cells::get);
			for (long k : emitters.getOrDefault(g, List.of())) {
				l.add(k, cells.get(k).getLightEmission());
			}
			l.run();
			for (BlockPos p : e.getValue()) {
				if (lightAt(l, p) >= MIN_LIGHT) {
					continue;
				}
				Long spot = lightSpot(p, g, groups);
				if (spot != null) {
					cells.put(spot, LIGHT);
					l.add(spot, LIGHT.getLightEmission());
					l.run();
				}
			}
		}
	}

	// ------------------------------------------------------------------ build

	private BuildPlan build() {
		// clear the air in every column: from its lowest cell up to its highest (and at least 5 above the ground)
		Map<Long, int[]> span = new HashMap<>();
		for (long k : cells.keySet()) {
			BlockPos p = BlockPos.of(k);
			int[] sp = span.computeIfAbsent(col(p.getX(), p.getZ()), c -> new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE});
			sp[0] = Math.min(sp[0], p.getY());
			sp[1] = Math.max(sp[1], p.getY());
		}
		for (Map.Entry<Long, int[]> e : span.entrySet()) {
			int x = colX(e.getKey());
			int z = colZ(e.getKey());
			Kind kind = zoneAt(x, z).kind();
			int top = Math.max(e.getValue()[1], kind == Kind.LAWN ? 3 : 5);
			for (int y = e.getValue()[0]; y <= top; y++) {
				long k = BlockPos.asLong(x, y, z);
				if (!cells.containsKey(k)) {
					cells.put(k, AIR);
					layers.put(k, SOLID);
				}
			}
		}
		PlanShapes.resolve(cells);
		Map<Long, String> groups = new HashMap<>(cells.size() * 2);
		for (long k : cells.keySet()) {
			BlockPos p = BlockPos.of(k);
			groups.put(k, groupOf(p.getX(), p.getY(), p.getZ()));
		}
		light(groups);
		record Cell(BlockPos pos, String group, int layer) {
		}
		List<Cell> all = new ArrayList<>(cells.size());
		Map<BlockPos, String> index = new HashMap<>(cells.size() * 2);
		for (long k : cells.keySet()) {
			BlockPos p = BlockPos.of(k);
			String g = groups.get(k);
			all.add(new Cell(p, g, layers.getOrDefault(k, SOLID)));
			index.put(p, g);
		}
		all.sort(Comparator.<Cell, String>comparing(Cell::group, GROUPS)
				.thenComparingInt(Cell::layer)
				.thenComparingInt(c -> c.layer() == HANGING ? -c.pos().getY() : c.pos().getY())
				.thenComparingInt(c -> c.pos().getZ())
				.thenComparingInt(c -> c.pos().getX()));
		List<Placement> out = new ArrayList<>(all.size());
		for (Cell c : all) {
			long k = c.pos().asLong();
			CompoundTag be = data.get(k);
			out.add(new Placement(c.pos(), cells.get(k), be == null ? null : be.copy()));
		}
		// modules: per group, per x, runs of consecutive z
		TreeMap<String, TreeMap<Integer, TreeMap<Integer, int[]>>> byGroup = new TreeMap<>(GROUPS);
		for (Cell c : all) {
			int[] yr = byGroup.computeIfAbsent(c.group(), g -> new TreeMap<>()).computeIfAbsent(c.pos().getX(), x -> new TreeMap<>())
					.computeIfAbsent(c.pos().getZ(), z -> new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE});
			yr[0] = Math.min(yr[0], c.pos().getY());
			yr[1] = Math.max(yr[1], c.pos().getY());
		}
		List<BuildPlan.Module> modules = new ArrayList<>();
		for (var g : byGroup.entrySet()) {
			for (var xe : g.getValue().entrySet()) {
				int x = xe.getKey();
				Integer z0 = null;
				int prev = 0;
				int y1 = 0;
				int y2 = 0;
				for (var ze : xe.getValue().entrySet()) {
					int z = ze.getKey();
					int[] yr = ze.getValue();
					if (z0 != null && z == prev + 1) {
						y1 = Math.min(y1, yr[0]);
						y2 = Math.max(y2, yr[1]);
					} else {
						if (z0 != null) {
							modules.add(new BuildPlan.Module(g.getKey() + "@" + x + "," + z0, Box.of(x, y1, z0, x, y2, prev), List.of(), g.getKey()));
						}
						z0 = z;
						y1 = yr[0];
						y2 = yr[1];
					}
					prev = z;
				}
				if (z0 != null) {
					modules.add(new BuildPlan.Module(g.getKey() + "@" + x + "," + z0, Box.of(x, y1, z0, x, y2, prev), List.of(), g.getKey()));
				}
			}
		}
		workstations.sort(Comparator.comparingInt(Workstation::module).thenComparingInt(Workstation::slot));
		return new BuildPlan(out, anchors, modules, workstations, Collections.unmodifiableMap(index));
	}
}
