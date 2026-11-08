package dev.t3craft.hq;

import dev.t3craft.hq.RingGeometry.DeskSpot;
import dev.t3craft.hq.RingGeometry.Kind;
import dev.t3craft.hq.RingGeometry.Node;
import dev.t3craft.hq.RingGeometry.Stairs;
import dev.t3craft.hq.RingGeometry.Zone;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
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
 * Plan of layout/ring ({@link RingStyle}, geometry in {@link RingGeometry}). Pure and deterministic
 * like {@link HqGenerator}: {@link HqLayout} in, {@link BuildPlan} out.
 *
 * <p>It paints cells column by column (each ground column belongs to one {@link RingGeometry.Zone}),
 * then cuts the plan into modules: one box per run of columns of the same <b>group</b> along z.
 * Groups (the construction units): {@code hub}, {@code cloister} (patio, bridges, gallery, ramps,
 * terraces, inner street), {@code archive} (the archive rotunda), {@code node_<q>_<k>} (special
 * rooms), {@code ring_<m>} (avenues, street, aprons and the empty lots of ring m),
 * {@code module_<index>} (a sector building's floor: {@code index = floor·100 + sector}) and
 * {@code grounds} (the lawn). A sector's cells depend only on that sector's modules, so adding or
 * removing a project only touches its own groups (and the ring it opens).
 */
final class RingGenerator {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final int SOLID = 0;
	private static final int DECOR = 1;
	private static final int HANGING = 2;

	private final RingStyle style;
	private final HqKit kit;

	private final Map<Long, BlockState> cells = new HashMap<>();
	private final Map<Long, CompoundTag> data = new HashMap<>();
	private final Map<Long, Integer> layers = new HashMap<>();
	private final Map<Long, String> groupOverride = new HashMap<>();
	private final TreeMap<String, Anchor> anchors = new TreeMap<>();
	private final List<Workstation> workstations = new ArrayList<>();
	private int walk;
	private int bench;

	private int rings;
	private HqLayout layout;
	/** sector → floor → module. */
	private final Map<Integer, Map<Integer, HqLayout.Module>> sectors = new TreeMap<>();

	RingGenerator(RingStyle style) {
		this.style = style;
		this.kit = HqKit.active();
	}

	BuildPlan generate(HqLayout layout) {
		this.layout = layout;
		List<Integer> idx = new ArrayList<>();
		for (HqLayout.Module m : layout.modules()) {
			int s = RingGeometry.sectorOf(m.index());
			if (s >= 0) {
				idx.add(m.index());
				sectors.computeIfAbsent(s, k -> new TreeMap<>()).put(RingGeometry.floorOf(m.index()), m);
			}
		}
		rings = RingGeometry.rings(idx);
		int R = RingGeometry.radius(rings);
		Map<Long, Zone> zones = new HashMap<>();
		for (int x = -R; x <= R; x++) {
			for (int z = -R; z <= R; z++) {
				Zone zn = RingGeometry.zone(x, z, rings);
				if (zn.kind() != Kind.NONE) {
					zones.put(col(x, z), zn);
				}
			}
		}
		this.zones = zones;
		// ground and streets first, then the buildings that stand on them
		for (Map.Entry<Long, Zone> e : zones.entrySet()) {
			int x = colX(e.getKey());
			int z = colZ(e.getKey());
			ground(x, z, e.getValue());
		}
		hub();
		cloister();
		for (Node nd : RingGeometry.nodes(rings)) {
			node(nd);
		}
		for (int m = 1; m <= rings; m++) {
			for (int j = 0; j < RingGeometry.sectorsIn(m); j++) {
				int s = RingGeometry.base(m) + j;
				Map<Integer, HqLayout.Module> floors = sectors.get(s);
				if (floors == null) {
					emptyLot(s);
				} else {
					sector(s, floors);
				}
			}
		}
		anchor(Anchor.at(AnchorNames.SPAWN, new BlockPos(0, 1, 38), Direction.NORTH));
		return build();
	}

	private Map<Long, Zone> zones = Map.of();

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

	private void decor(BlockPos p, BlockState s) {
		put(p.getX(), p.getY(), p.getZ(), s, DECOR);
	}

	private void put(int x, int y, int z, BlockState s, int layer) {
		long k = BlockPos.asLong(x, y, z);
		cells.put(k, s);
		layers.put(k, layer);
		data.remove(k);
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

	// ------------------------------------------------------------------ materials

	private BlockState floor() {
		return kit.floor();
	}

	private BlockState accent() {
		return kit.floorAccent();
	}

	private static BlockState b(Block b) {
		return b.defaultBlockState();
	}

	// feat/hq-layouts: one visual style (cherry) for every layout; the ring's former tuff, quartz,
	// copper and mud-brick shell maps to the cherry palette's footing, floor, wall, column and roof.
	// The names stay as the roles they fill in the ring.
	private static final BlockState TUFF_BRICKS = b(Blocks.DEEPSLATE_TILES);
	private static final BlockState POLISHED_TUFF = b(Blocks.BAMBOO_MOSAIC);
	private static final BlockState QUARTZ = b(Blocks.CALCITE);
	private static final BlockState PILLAR = b(Blocks.CHERRY_LOG);
	private static final BlockState GLASS = b(Blocks.GLASS);
	private static final BlockState GRASS = b(Blocks.GRASS_BLOCK);
	private static final BlockState MOSS = b(Blocks.MOSS_BLOCK);
	private static final BlockState WATER = b(Blocks.WATER);
	private static final BlockState SEA_LANTERN = b(Blocks.SEA_LANTERN);
	private static final BlockState DEEPSLATE_TILES = b(Blocks.DEEPSLATE_TILES);
	private static final BlockState COPPER = b(Blocks.POLISHED_BLACKSTONE_BRICKS);
	private static final BlockState COPPER_WARM = b(Blocks.CHERRY_PLANKS);
	private static final BlockState MUD_BRICKS = b(Blocks.CALCITE);
	private static final BlockState LOG = b(Blocks.CHERRY_LOG);

	private static BlockState slab(Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
	}

	private static BlockState stairs(Block b, Direction facing) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM);
	}

	private static BlockState leaves(Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
	}

	private static BlockState hangingLantern() {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
	}

	static BlockState terracotta(DyeColor c) {
		return Blocks.DYED_TERRACOTTA.pick(c).defaultBlockState();
	}

	// ------------------------------------------------------------------ ground

	/** Half-block ramp profile: top of the walking surface (in half blocks above y = 1). */
	private static int rampHalves(double r) {
		return (int) Math.max(0, Math.min(2 * RingGeometry.PODIUM, Math.round(RingGeometry.RAMP_R - r)));
	}

	/** A column whose walking surface is {@code halves} half blocks above the ground's top (y = 1). */
	private void stepped(int x, int z, int halves, BlockState full, BlockState half) {
		int solidTop = halves / 2;
		fill(x, 0, solidTop, z, full);
		if (halves % 2 == 1) {
			set(x, solidTop + 1, z, half);
		}
	}

	private void ground(int x, int z, Zone zn) {
		double r = RingGeometry.r(x, z);
		switch (zn.kind()) {
			case HUB, HUB_WALL -> set(x, 0, z, TUFF_BRICKS);
			case BRIDGE, PATIO -> patioGround(x, z, r);
			case GALLERY -> set(x, 0, z, r > RingGeometry.GALLERY_R - 1 ? TUFF_BRICKS : accent());
			case RAMP -> stepped(x, z, rampHalves(r), POLISHED_TUFF, slab(Blocks.BAMBOO_MOSAIC_SLAB));
			case TERRACE -> {
				int h = rampHalves(r);
				stepped(x, z, h - h % 2, b(Blocks.DEEPSLATE_TILES), b(Blocks.DEEPSLATE_TILES));
				int top = h / 2;
				set(x, top, z, (x * 7 + z * 13 & 7) == 0 ? MOSS : GRASS);
				if (Math.floorMod(x * 31 + z * 17, 23) == 0 && top + 1 < 6) {
					set(x, top + 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
				}
			}
			case INNER_STREET, STREET, APRON -> set(x, 0, z, street(x, z, r));
			case AVENUE -> set(x, 0, z, RingGeometry.radialDistance(x, z, RingGeometry.sectorsIn(zn.ring())) > 2.4 ? TUFF_BRICKS : accent());
			case LAWN -> set(x, 0, z, GRASS);
			case NODE, SECTOR -> set(x, 0, z, floor());
			default -> {
			}
		}
		if (zn.kind() == Kind.AVENUE || zn.kind() == Kind.STREET || zn.kind() == Kind.INNER_STREET) {
			lamp(x, z, r, zn);
		}
	}

	private BlockState street(int x, int z, double r) {
		// paving rings: accent courses every 3 blocks of radius
		return ((int) Math.floor(r)) % 3 == 0 ? TUFF_BRICKS : accent();
	}

	/** Street lights: on the avenue edges every 8 blocks of radius, on the streets' outer edge every 12°. */
	private void lamp(int x, int z, double r, Zone zn) {
		if (zn.kind() == Kind.AVENUE) {
			double d = RingGeometry.radialDistance(x, z, RingGeometry.sectorsIn(zn.ring()));
			if (d > 2.6 && Math.floorMod((int) Math.round(r), 8) == 0 && lampCell(x, z, r)) {
				set(x, 1, z, kit.gardenLantern());
			}
			return;
		}
		double outer = zn.kind() == Kind.INNER_STREET ? RingGeometry.INNER_STREET_R : RingGeometry.streetOuter(zn.ring());
		if (r > outer - 1 && RingGeometry.radialDistance(x, z, 30) < 0.5 && RingGeometry.radialDistance(x, z, 8) > 5) {
			set(x, 1, z, kit.gardenLantern());
		}
	}

	/** One lamp per 8-block step: the cell nearest the step's circle. */
	private static boolean lampCell(int x, int z, double r) {
		return Math.abs(r - Math.round(r)) < 0.5;
	}

	private void patioGround(int x, int z, double r) {
		if (r > 17.5) {
			set(x, 0, z, accent());
		} else if (r > 15.5 && RingGeometry.radialDistance(x, z, 8) > 1.2) {
			set(x, -1, z, TUFF_BRICKS);
			set(x, 0, z, WATER);
		} else if (r > 15.5) {
			set(x, 0, z, TUFF_BRICKS);
		} else {
			set(x, 0, z, (x + z & 3) == 0 ? MOSS : GRASS);
		}
	}

	// ------------------------------------------------------------------ hub

	private void hub() {
		int y0 = RingGeometry.PODIUM;
		int drumTop = 15;
		for (int x = -14; x <= 14; x++) {
			for (int z = -14; z <= 14; z++) {
				Kind k = zoneAt(x, z).kind();
				if (k == Kind.HUB) {
					double o = RingGeometry.octagon(x, z);
					set(x, y0, z, o <= 3 ? accent() : ((int) o % 3 == 0 ? accent() : floor()));
				} else if (k == Kind.HUB_WALL) {
					fill(x, 1, y0, z, TUFF_BRICKS);
					hubWall(x, z, y0, drumTop);
				}
			}
		}
		dome(drumTop);
		// four great columns on the diagonals
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				for (int y = y0 + 1; y < drumTop; y++) {
					set(7 * sx, y, 7 * sz, PILLAR);
				}
				set(7 * sx, drumTop, 7 * sz, kit.beamCap());
				set(7 * sx, drumTop + 1, 7 * sz, kit.lantern());
			}
		}
		board(y0);
		// benches facing the screens: the kanban wall (west) and the big screen (east)
		for (int sx = -1; sx <= 1; sx += 2) {
			Direction look = sx > 0 ? Direction.EAST : Direction.WEST;
			for (int z = -3; z <= 3; z++) {
				BlockPos p = new BlockPos(7 * sx, y0 + 1, z);
				set(p, style.bench(look.getOpposite()));
				anchor(Anchor.at(AnchorNames.bench(bench++), p.relative(look), look));
			}
		}
		HqGeometry.Surface screen = new HqGeometry.Surface(new BlockPos(12, y0 + 2, -3), Direction.SOUTH, Direction.WEST, 7, 4);
		bigScreen(screen, "hub", HqGenerator.hubBinding(layout));
		HqGeometry.Surface wall = new HqGeometry.Surface(new BlockPos(-12, y0 + 2, 4), Direction.NORTH, Direction.EAST, 9, 4);
		for (BlockPos p : wall.cells()) {
			set(p, style.screen());
		}
		anchor(Anchor.face(AnchorNames.THREAD_WALL, wall.origin(), wall.facing()));
		anchor(Anchor.face(AnchorNames.THREAD_WALL_END, wall.origin().relative(wall.along(), wall.width() - 1).above(wall.height() - 1), wall.facing()));
		anchor(Anchor.at(AnchorNames.THREAD_WALL_FRONT, new BlockPos(-9, y0 + 1, 0), Direction.WEST));
		// hologram column free over the dais; the beacon crowns the dome's lantern
		for (int y = y0 + 1; y <= y0 + 1 + HqGeometry.HOLOGRAM_TOP; y++) {
			set(0, y, 0, AIR);
		}
		anchor(Anchor.at(AnchorNames.HUB, new BlockPos(0, y0 + 1, 0), Direction.SOUTH));
		anchor(new Anchor(AnchorNames.HOLOGRAM, 0.5, y0 + 5.0, 0.5, Direction.SOUTH, new BlockPos(0, y0 + 5, 0)));
		anchor(Anchor.at(AnchorNames.ENTRANCE, new BlockPos(0, y0 + 1, 11), Direction.SOUTH));
		for (int wz : new int[] {-4, 4}) {
			for (int sx = -1; sx <= 1; sx += 2) {
				walkSpot(new BlockPos(4 * sx, y0 + 1, wz), Direction.SOUTH);
			}
		}
	}

	private void hubWall(int x, int z, int y0, int top) {
		boolean cardinalFace = Math.abs(x) <= 7 && Math.abs(z) >= 12 || Math.abs(z) <= 7 && Math.abs(x) >= 12;
		boolean vertex = !cardinalFace && (Math.abs(x) <= 8 && Math.abs(z) >= 11 || Math.abs(z) <= 8 && Math.abs(x) >= 11);
		for (int y = y0 + 1; y <= top; y++) {
			BlockState s;
			if (y <= y0 + 2) {
				s = TUFF_BRICKS;
			} else if (y == top) {
				s = kit.wallPanel(true);
			} else if (!cardinalFace && y >= y0 + 5 && y <= top - 2 && !vertex) {
				s = GLASS;
			} else if (!cardinalFace && vertex) {
				s = PILLAR;
			} else {
				s = kit.wallPanel(false);
			}
			set(x, y, z, s);
		}
		// main portal (south, 5 wide x 5 tall) and the north arch under the board (3 x 3)
		if (z > 0 && Math.abs(x) <= 2) {
			for (int y = y0 + 1; y <= y0 + 5; y++) {
				set(x, y, z, AIR);
			}
		}
		if (z < 0 && Math.abs(x) <= 1) {
			for (int y = y0 + 1; y <= y0 + 3; y++) {
				set(x, y, z, AIR);
			}
		}
	}

	private void dome(int drumTop) {
		double R = 13.5;
		for (int x = -14; x <= 14; x++) {
			for (int z = -14; z <= 14; z++) {
				double r = Math.hypot(x, z);
				if (r > R) {
					continue;
				}
				int top = drumTop + (int) Math.round(Math.sqrt(R * R - r * r));
				double rn = Math.min(R, r + 1);
				int low = drumTop + (int) Math.round(Math.sqrt(Math.max(0, R * R - rn * rn))) + 1;
				boolean rib = RingGeometry.radialDistance(x, z, 8) < 0.6;
				for (int y = Math.max(drumTop + 1, Math.min(low, top)); y <= top; y++) {
					BlockState s = r <= 1.5 ? GLASS : rib ? QUARTZ : (y % 4 == 0 ? COPPER_WARM : COPPER);
					set(x, y, z, s);
				}
			}
		}
		int top = drumTop + (int) Math.round(R);
		set(0, top + 1, 0, style.beacon());
		anchor(Anchor.at(AnchorNames.BEACON, new BlockPos(0, top + 1, 0), Direction.SOUTH));
	}

	/** The "needs attention" board above the north arch, facing the entrance, with its bulbs and standing rows. */
	private void board(int y0) {
		int z = -12;
		for (int x = -5; x <= 5; x++) {
			for (int y = y0 + 4; y <= y0 + 10; y++) {
				boolean edge = Math.abs(x) == 5 || y == y0 + 4 || y == y0 + 10 || y == y0 + 9;
				set(x, y, z, edge ? kit.beam(Direction.Axis.X) : style.palette().get(HqStyle.Role.BOARD));
			}
		}
		int lamp = 0;
		for (int x = -4; x <= 4; x += 2) {
			set(x, y0 + 9, z, style.statusLamp());
			anchor(Anchor.face(AnchorNames.boardLamp(lamp++), new BlockPos(x, y0 + 9, z), Direction.SOUTH));
		}
		BlockPos sign = new BlockPos(0, y0 + 10, z + 1);
		put(sign.getX(), sign.getY(), sign.getZ(), style.palette().get(HqStyle.Role.PLAQUE).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), DECOR);
		data.put(sign.asLong(), new HqStyle.SignText(DyeColor.ORANGE, "", "Needs", "attention", "").signTag());
		anchor(Anchor.face(AnchorNames.BOARD, new BlockPos(0, y0 + 6, z), Direction.SOUTH));
		anchor(Anchor.at(AnchorNames.BOARD_FRONT, new BlockPos(0, y0 + 1, z + 1), Direction.NORTH));
		int slot = 0;
		for (int row = 2; row <= 3; row++) {
			for (int i = 0; i <= 8; i++) {
				int x = i == 0 ? 0 : (i % 2 == 1 ? (i + 1) / 2 : -(i / 2));
				anchor(Anchor.at(AnchorNames.boardSlot(slot++), new BlockPos(x, y0 + 1, z + row), Direction.NORTH));
			}
		}
	}

	private void bigScreen(HqGeometry.Surface s, String key, String binding) {
		for (BlockPos p : s.cells()) {
			set(p, style.screen());
		}
		anchor(Anchor.face(AnchorNames.bigScreen(key, s.width(), s.height(), binding), s.origin().above(s.height() - 1), s.facing()));
	}

	// ------------------------------------------------------------------ cloister: bridges, gallery, patio, ramps

	private static final int GALLERY_ROOF = 10;
	private static final int ARCADE = 32;

	private void cloister() {
		int y0 = RingGeometry.PODIUM;
		java.util.Set<Long> arcade = new java.util.HashSet<>();
		for (int k = 0; k < ARCADE; k++) {
			double a = (k + 0.5) * 2 * Math.PI / ARCADE;
			BlockPos p = RingGeometry.polar(21.6, a, 0);
			arcade.add(col(p.getX(), p.getZ()));
		}
		// patio stairs: from the gallery down into the patio on the four diagonals
		java.util.Set<Long> stairHeads = new java.util.HashSet<>();
		for (int d = 0; d < 4; d++) {
			double a = Math.PI / 4 + d * Math.PI / 2;
			BlockPos p = RingGeometry.polar(21.2, a, 0);
			int dx = p.getX() > 0 ? -1 : 1;
			Direction up = dx < 0 ? Direction.EAST : Direction.WEST;
			for (int w = -1; w <= 1; w++) {
				for (int i = 0; i < 4; i++) {
					int x = p.getX() + dx * i;
					int z = p.getZ() + w;
					for (int y = 1; y < y0 - i; y++) {
						set(x, y, z, TUFF_BRICKS);
					}
					set(x, y0 - i, z, stairs(Blocks.BAMBOO_MOSAIC_STAIRS, up));
					for (int y = y0 - i + 1; y <= y0 + 3; y++) {
						set(x, y, z, AIR);
					}
				}
				for (int i = 0; i <= 3; i++) {
					stairHeads.add(col(p.getX() - dx * i, p.getZ() + w));
				}
			}
		}
		for (Map.Entry<Long, Zone> e : zones.entrySet()) {
			Kind k = e.getValue().kind();
			int x = colX(e.getKey());
			int z = colZ(e.getKey());
			double r = RingGeometry.r(x, z);
			if (k == Kind.BRIDGE) {
				bridge(x, z, y0);
			} else if (k == Kind.GALLERY) {
				gallery(x, z, r, y0, arcade.contains(e.getKey()), stairHeads.contains(e.getKey()));
			} else if (k == Kind.PATIO) {
				patio(x, z, r);
			}
		}
		walkSpot(new BlockPos(19, 1, 0), Direction.WEST);
		walkSpot(new BlockPos(-19, 1, 0), Direction.EAST);
		walkSpot(new BlockPos(0, y0 + 1, -24), Direction.NORTH);
		walkSpot(new BlockPos(24, y0 + 1, 0), Direction.EAST);
	}

	private void bridge(int x, int z, int y0) {
		if (at(x, y0, z) != null) {
			return; // patio stairs
		}
		set(x, y0, z, Math.abs(x) <= 2 ? accent() : TUFF_BRICKS);
		if (Math.abs(x) == 3) {
			set(x, y0 + 1, z, kit.railing(x > 0 ? Direction.EAST : Direction.WEST));
			if (Math.floorMod(z, 4) == 0) {
				set(x, y0 + 2, z, kit.gardenLantern());
			}
		}
		// arches under the deck: a lintel course and piers where the deck meets the patio's ring path
		set(x, y0 - 1, z, Math.abs(z) % 4 == 1 || Math.abs(x) == 3 ? TUFF_BRICKS : AIR);
	}

	private void gallery(int x, int z, double r, int y0, boolean column, boolean stairHead) {
		if (at(x, y0, z) == null) {
			set(x, y0, z, floor());
		}
		boolean outer = r > RingGeometry.GALLERY_R - 1;
		boolean inner = r <= RingGeometry.PATIO_R + 1.2;
		boolean avenue = RingGeometry.radialDistance(x, z, 8) <= RingGeometry.AVENUE_HALF;
		if (outer) {
			fill(x, 1, y0 - 1, z, TUFF_BRICKS);
			for (int y = y0 + 1; y < GALLERY_ROOF; y++) {
				boolean opening = avenue && y <= y0 + 4;
				set(x, y, z, opening ? AIR : (y == y0 + 1 || y == GALLERY_ROOF - 1) ? QUARTZ
						: (Math.floorMod(x * 3 + z * 5, 4) == 0 ? PILLAR : GLASS));
			}
		} else if (column && !stairHead) {
			fill(x, 1, GALLERY_ROOF - 1, z, PILLAR);
			set(x, y0, z, kit.beamCap());
		} else if (inner && !stairHead && Math.abs(x) > 3) {
			set(x, y0 + 1, z, kit.railing(Direction.NORTH));
		}
		// roof: tiles, a slab trim on the patio edge, a parapet over the outer wall
		set(x, GALLERY_ROOF, z, inner ? slab(Blocks.DEEPSLATE_TILE_SLAB) : DEEPSLATE_TILES);
		if (outer) {
			set(x, GALLERY_ROOF + 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
		if (!outer && !inner && RingGeometry.radialDistance(x, z, 16) < 0.5 && Math.abs(r - 24.5) < 0.6) {
			put(x, GALLERY_ROOF - 1, z, hangingLantern(), HANGING);
		}
		// small copper domes on the roof over the diagonal stair heads
		double dd = Math.min(Math.min(Math.hypot(x - 17, z - 17), Math.hypot(x + 17, z - 17)), Math.min(Math.hypot(x - 17, z + 17), Math.hypot(x + 17, z + 17)));
		if (dd <= 3.2) {
			int h = (int) Math.round(Math.sqrt(Math.max(0, 3.2 * 3.2 - dd * dd)));
			for (int y = GALLERY_ROOF + 1; y <= GALLERY_ROOF + 1 + h; y++) {
				set(x, y, z, y == GALLERY_ROOF + 1 + h ? COPPER : (dd > 2.2 ? COPPER : AIR));
			}
			if (dd < 0.5) {
				set(x, GALLERY_ROOF + 2 + h, z, kit.lantern());
			}
		}
	}

	private void patio(int x, int z, double r) {
		if (at(x, 1, z) != null) {
			return; // stairs
		}
		// cherry trees on the patio ring, between the radials
		for (int k = 0; k < 8; k++) {
			double a = (k + 0.5) * Math.PI / 4;
			BlockPos t = RingGeometry.polar(19.3, a, 0);
			int dx = x - t.getX();
			int dz = z - t.getZ();
			if (dx == 0 && dz == 0) {
				fill(x, 1, 4, z, LOG);
			}
			if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2 && Math.abs(dx) + Math.abs(dz) <= 3 && !(dx == 0 && dz == 0)) {
				set(x, 5, z, leaves(Blocks.CHERRY_LEAVES));
				if (Math.abs(dx) + Math.abs(dz) <= 1) {
					set(x, 6, z, leaves(Blocks.CHERRY_LEAVES));
				}
			}
			if (dx == 0 && dz == 0) {
				set(x, 5, z, leaves(Blocks.CHERRY_LEAVES));
				set(x, 6, z, leaves(Blocks.CHERRY_LEAVES));
			}
		}
		// planters on the inner patio only: past r = 14.5 the way along the drum is too narrow (fix/hq-lanterns)
		if (r <= 14.5 && Math.floorMod(x * 5 + z * 3, 11) == 0) {
			set(x, 1, z, Math.floorMod(x + z, 2) == 0 ? b(Blocks.FLOWERING_AZALEA) : kit.gardenPlanter());
		}
	}

	// ------------------------------------------------------------------ nodes

	private void node(Node nd) {
		int R = (int) Math.ceil(nd.footprint()) + 1;
		for (int x = nd.cx() - R; x <= nd.cx() + R; x++) {
			for (int z = nd.cz() - R; z <= nd.cz() + R; z++) {
				Zone zn = zoneAt(x, z);
				if (zn.kind() == Kind.NODE && zn.node() == nd.id()) {
					nodeCell(nd, x, z);
				}
			}
		}
		int cx = nd.cx();
		int cz = nd.cz();
		switch (nd.kind()) {
			case REVIEW_TOWER -> {
				bigScreen(new HqGeometry.Surface(new BlockPos(cx - 3, 5, cz - 5), Direction.EAST, Direction.SOUTH, 7, 4), "review", "");
				walkSpot(new BlockPos(cx, 1, cz - 2), Direction.NORTH);
			}
			case SCHEDULE_ROTUNDA -> {
				bigScreen(new HqGeometry.Surface(new BlockPos(cx + 4, 2, cz - 2), Direction.SOUTH, Direction.WEST, 5, 3), "schedule", "");
				walkSpot(new BlockPos(cx - 1, 1, cz), Direction.EAST);
			}
			case ARCHIVE_ROTUNDA -> {
				BlockPos t = new BlockPos(cx, 1, cz + 2);
				set(t, style.archiveTerminal(Direction.NORTH));
				anchor(Anchor.face(AnchorNames.ARCHIVE_TERMINAL, t, Direction.NORTH));
				anchor(Anchor.at(AnchorNames.ARCHIVE, t.north(), Direction.SOUTH));
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
			case ARCHIVE_ROTUNDA -> archiveRotunda(x, z, dx, dz);
			case LOUNGE_SUNKEN -> sunken(x, z, dx, dz, d);
			case PLAZA -> plaza(x, z, dx, dz, d);
			case OBELISK -> obelisk(x, z, d);
		}
	}

	private static boolean door(int dx, int dz, int edge) {
		return Math.abs(dx) <= 1 && Math.abs(dz) >= edge || Math.abs(dz) <= 1 && Math.abs(dx) >= edge;
	}

	/** Review tower: a square tower, 13 × 13, 21 high, pyramid roof and a lantern spire. */
	private void tower(int x, int z, int dx, int dz) {
		int m = Math.max(Math.abs(dx), Math.abs(dz));
		set(x, 0, z, m <= 2 ? accent() : floor());
		int top = 20;
		if (m == 6) {
			boolean corner = Math.abs(dx) == 6 && Math.abs(dz) == 6 || Math.abs(dx) == 3 || Math.abs(dz) == 3;
			for (int y = 1; y <= top; y++) {
				BlockState s = corner ? PILLAR : (y % 5 == 0 ? kit.wallPanel(true) : (y >= 3 && Math.floorMod(dx + dz, 2) == 0 ? GLASS : QUARTZ));
				set(x, y, z, s);
			}
			if (door(dx, dz, 6)) {
				fill(x, 1, 4, z, AIR);
			}
		}
		// mezzanine ring (a gallery inside the tower at y = 10) with lanterns
		if (m == 5) {
			set(x, 10, z, DEEPSLATE_TILES);
			if (Math.abs(dx) == 5 && Math.abs(dz) == 5) {
				set(x, 11, z, kit.lantern());
			}
		}
		int lvl = top + 1 + (6 - m);
		for (int y = top + 1; y <= lvl; y++) {
			set(x, y, z, y == lvl ? COPPER : AIR);
		}
		if (m == 0) {
			set(x, lvl + 1, z, PILLAR);
			set(x, lvl + 2, z, SEA_LANTERN);
			put(x, lvl - 1, z, hangingLantern(), HANGING);
		}
	}

	/** Scheduled-tasks rotunda: round hall (radius 7) with a copper dome and a clock ring of lanterns on the floor. */
	private void rotunda(int x, int z, int dx, int dz, double d) {
		set(x, 0, z, Math.abs(d - 4) < 0.5 ? kit.floorAccent() : floor());
		int wallTop = 8;
		if (d > 6) {
			for (int y = 1; y <= wallTop; y++) {
				set(x, y, z, y == wallTop ? kit.wallPanel(true) : (y >= 3 && y <= 6 && Math.floorMod(dx * 3 + dz, 3) == 0 ? GLASS : TUFF_BRICKS));
			}
			if (door(dx, dz, 6)) {
				fill(x, 1, 4, z, AIR);
			}
		}
		double R = 7.2;
		int top = wallTop + (int) Math.round(Math.sqrt(Math.max(0, R * R - d * d)));
		int low = wallTop + (int) Math.round(Math.sqrt(Math.max(0, R * R - Math.min(R, d + 1) * Math.min(R, d + 1)))) + 1;
		for (int y = Math.max(wallTop + 1, Math.min(low, top)); y <= top; y++) {
			set(x, y, z, d < 1 ? GLASS : COPPER_WARM);
		}
		if (Math.abs(d - 5) < 0.5 && Math.floorMod(dx * 7 + dz * 3, 4) == 0 && Math.abs(dx) > 1 && Math.abs(dz) > 1) {
			set(x, 1, z, kit.lantern());
		}
		if (d < 0.5) {
			set(x, top + 1, z, SEA_LANTERN);
		}
	}

	/** Archive rotunda: a low octagon with a flat roof and a lantern; the archive terminal stands inside. */
	private void archiveRotunda(int x, int z, int dx, int dz) {
		double o = Math.max(Math.max(Math.abs(dx), Math.abs(dz)), (Math.abs(dx) + Math.abs(dz)) * 0.75);
		set(x, 0, z, o <= 2 ? accent() : floor());
		if (o > 6.2) {
			return;
		}
		if (o > 5.2) {
			for (int y = 1; y <= 6; y++) {
				set(x, y, z, y == 6 ? kit.wallPanel(true) : (y >= 2 && y <= 4 && Math.abs(Math.abs(dx) - Math.abs(dz)) < 2 ? GLASS : kit.wallPanel(false)));
			}
			if (door(dx, dz, 5)) {
				fill(x, 1, 4, z, AIR);
			}
		}
		set(x, 7, z, o > 5.2 ? slab(Blocks.DEEPSLATE_TILE_SLAB) : DEEPSLATE_TILES);
		if (o <= 1) {
			set(x, 8, z, COPPER);
			if (dx == 0 && dz == 0) {
				set(x, 9, z, kit.lantern());
			}
		}
		if (o < 5 && Math.abs(dx) == 4 && Math.abs(dz) == 2) {
			set(x, 1, z, kit.gardenPlanter());
		}
	}

	/** Lounge: a sunken patio, a round amphitheatre of half steps down to y = -3 around a lantern column. */
	private void sunken(int x, int z, int dx, int dz, double d) {
		int k = (int) Math.max(0, Math.min(6, Math.round(7 - d)));
		int top = 1 - k; // in half blocks: surface = 1 - k/2
		// clear the ground first (the street's paving at y = 0)
		cells.remove(BlockPos.asLong(x, 0, z));
		int solid = k % 2 == 0 ? -k / 2 : -(k + 1) / 2;
		set(x, solid, z, k % 2 == 0 ? (k == 6 ? accent() : MOSS) : TUFF_BRICKS);
		if (k % 2 == 1) {
			set(x, solid + 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
		for (int y = solid + (k % 2 == 1 ? 2 : 1); y <= 2; y++) {
			set(x, y, z, AIR);
		}
		if (dx == 0 && dz == 0) {
			set(x, -2, z, PILLAR);
			set(x, -1, z, PILLAR);
			set(x, 0, z, kit.lantern());
		}
		if (d > 6.2 && Math.abs(Math.abs(dx) - Math.abs(dz)) <= 1 && Math.abs(dx) >= 4) {
			set(x, 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			set(x, 2, z, leaves(Blocks.AZALEA_LEAVES));
		}
	}

	private void plaza(int x, int z, int dx, int dz, double d) {
		set(x, 0, z, Math.abs(d - 3) < 0.5 ? TUFF_BRICKS : accent());
		if (dx == 0 && dz == 0) {
			set(x, -1, z, TUFF_BRICKS);
			set(x, 0, z, WATER);
		}
		if (Math.abs(dx) == 3 && Math.abs(dz) == 3) {
			fill(x, 1, 3, z, LOG);
			for (int ax = -1; ax <= 1; ax++) {
				for (int az = -1; az <= 1; az++) {
					set(x + ax, 4, z + az, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
				}
			}
			set(x, 5, z, leaves(Blocks.AZALEA_LEAVES));
		}
	}

	private void obelisk(int x, int z, double d) {
		set(x, 0, z, accent());
		if (d < 0.5) {
			set(x, 1, z, POLISHED_TUFF);
			fill(x, 2, 7, z, PILLAR);
			set(x, 8, z, SEA_LANTERN);
		}
	}

	// ------------------------------------------------------------------ sectors

	/** Cells of a sector, with whether each one is on its boundary (a wall). */
	private record SectorCells(List<long[]> interior, List<long[]> walls) {
	}

	private SectorCells sectorCells(int s) {
		int m = RingGeometry.ringOf(s);
		int R = RingGeometry.ringOuter(m) + 1;
		double a = RingGeometry.centreAngle(s);
		double half = Math.PI / RingGeometry.sectorsIn(m) + 0.05;
		List<long[]> in = new ArrayList<>();
		List<long[]> walls = new ArrayList<>();
		// scan the wedge's bounding box
		double[] xs = new double[] {0, 0};
		double[] zs = new double[] {0, 0};
		for (double rr : new double[] {RingGeometry.ringInner(m), R}) {
			for (double aa : new double[] {a - half, a, a + half}) {
				xs[0] = Math.min(xs[0] == 0 && zs[0] == 0 ? rr * Math.sin(aa) : xs[0], rr * Math.sin(aa));
				xs[1] = Math.max(xs[1], rr * Math.sin(aa));
				zs[0] = Math.min(zs[0], -rr * Math.cos(aa));
				zs[1] = Math.max(zs[1], -rr * Math.cos(aa));
			}
		}
		int x1 = (int) Math.floor(Math.min(xs[0], 0)) - 2;
		int x2 = (int) Math.ceil(Math.max(xs[1], 0)) + 2;
		int z1 = (int) Math.floor(zs[0]) - 2;
		int z2 = (int) Math.ceil(zs[1]) + 2;
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				if (!mine(s, x, z)) {
					continue;
				}
				boolean wall = !mine(s, x + 1, z) || !mine(s, x - 1, z) || !mine(s, x, z + 1) || !mine(s, x, z - 1);
				(wall ? walls : in).add(new long[] {x, z});
			}
		}
		return new SectorCells(in, walls);
	}

	private boolean mine(int s, int x, int z) {
		Zone zn = zoneAt(x, z);
		return zn.kind() == Kind.SECTOR && zn.sector() == s;
	}

	/** Tangential offset of a cell from a sector's centre line, and its radius. */
	private static double tangential(int s, int x, int z) {
		double a = RingGeometry.centreAngle(s);
		double r = Math.hypot(x, z);
		double d = RingGeometry.theta(x, z) - a;
		d = Math.atan2(Math.sin(d), Math.cos(d));
		return r * d;
	}

	private void emptyLot(int s) {
		int m = RingGeometry.ringOf(s);
		int ri = RingGeometry.ringInner(m);
		SectorCells sc = sectorCells(s);
		for (long[] c : sc.interior()) {
			int x = (int) c[0];
			int z = (int) c[1];
			set(x, 0, z, (x * 3 + z & 7) == 0 ? MOSS : GRASS);
		}
		for (long[] c : sc.walls()) {
			int x = (int) c[0];
			int z = (int) c[1];
			set(x, 0, z, GRASS);
			double r = Math.hypot(x, z);
			boolean gap = Math.abs(tangential(s, x, z)) <= 1.6 && r < ri + 2;
			if (!gap) {
				set(x, 1, z, leaves(Blocks.AZALEA_LEAVES));
			}
		}
		BlockPos c = RingGeometry.polar(ri + 13, RingGeometry.centreAngle(s), 1);
		if (mine(s, c.getX(), c.getZ())) {
			set(c, kit.gardenLantern());
		}
		for (int i = 0; i < 4; i++) {
			BlockPos t = RingGeometry.polar(ri + 8 + 6 * (i / 2), RingGeometry.centreAngle(s) + (i % 2 == 0 ? -7.0 : 7.0) / (ri + 8 + 6 * (i / 2)), 1);
			if (mine(s, t.getX(), t.getZ())) {
				set(t, kit.gardenPlanter());
			}
		}
	}

	/** One project sector building, one or more floors (one module per floor). */
	private void sector(int s, Map<Integer, HqLayout.Module> floors) {
		int m = RingGeometry.ringOf(s);
		int ri = RingGeometry.ringInner(m);
		int ro = RingGeometry.ringOuter(m);
		int F = Collections.max(floors.keySet()) + 1;
		int roofY = RingGeometry.floorY(F);
		HqLayout.Module lead = floors.values().iterator().next();
		DyeColor accentColor = HqStyle.envColor(lead);
		BlockState envBlock = terracotta(accentColor);
		SectorCells sc = sectorCells(s);
		Stairs st = RingGeometry.stairs(s);
		java.util.Set<Long> hole = new java.util.HashSet<>();
		for (int f = 0; f + 1 < F; f++) {
			for (int v : st.lane(f)) {
				for (int i = 0; i < 6; i++) {
					BlockPos p = st.cell(st.stepU(f, i), v, RingGeometry.floorY(f + 1));
					hole.add(p.asLong());
				}
			}
		}
		// interior: floors (slabs), ceilings, the roof
		for (long[] c : sc.interior()) {
			int x = (int) c[0];
			int z = (int) c[1];
			double r = Math.hypot(x, z);
			double t = tangential(s, x, z);
			for (int f = 0; f < F; f++) {
				int y = RingGeometry.floorY(f);
				if (f == 0) {
					set(x, 0, z, Math.abs(t) <= 1.2 && r < ri + 13 ? accent() : floor());
				} else if (!hole.contains(BlockPos.asLong(x, y, z))) {
					set(x, y, z, (Math.floorMod(x * 5 + z * 3, 13) == 0) ? SEA_LANTERN : floor());
				}
				for (int yy = y + 1; yy < y + RingGeometry.FLOOR_H; yy++) {
					if (at(x, yy, z) == null) {
						set(x, yy, z, AIR);
					}
				}
			}
			roof(m, s, x, z, r, t, roofY, ri, false, accentColor);
		}
		// walls, doors, windows
		BlockPos signCell = null;
		double signT = Double.MAX_VALUE;
		for (long[] c : sc.walls()) {
			int x = (int) c[0];
			int z = (int) c[1];
			double r = Math.hypot(x, z);
			double t = tangential(s, x, z);
			boolean arcIn = r < ri + 2.2;
			boolean arcOut = r > ro - 2.2;
			set(x, 0, z, TUFF_BRICKS);
			for (int y = 1; y < roofY; y++) {
				int ly = y % RingGeometry.FLOOR_H;
				BlockState w;
				if (ly == 0) {
					w = m == 2 ? DEEPSLATE_TILES : kit.beam(Direction.Axis.Y);
				} else if (ly == 1) {
					w = m == 1 ? TUFF_BRICKS : DEEPSLATE_TILES;
				} else if (ly >= 3 && ly <= 5 && Math.floorMod((int) Math.round(r + t), 4) != 0) {
					w = GLASS;
				} else if (Math.floorMod((int) Math.round(r + t), 4) == 0) {
					w = m == 1 ? kit.beam(Direction.Axis.Y) : PILLAR;
				} else {
					w = switch (m) {
						case 1 -> kit.wallPanel(ly == 6);
						case 2 -> envBlock;
						default -> MUD_BRICKS;
					};
				}
				set(x, y, z, w);
			}
			// the env band under the eaves (ring 1) / parapet (ring 2+)
			set(x, roofY, z, m == 1 ? envBlock : DEEPSLATE_TILES);
			boolean door = (arcIn || arcOut) && Math.abs(t) <= 1.6
					|| !arcIn && !arcOut && Math.abs(r - (ri + 8)) <= 1.1;
			if (door) {
				fill(x, 1, 3, z, AIR);
				if (arcIn && Math.abs(t) < signT - 1e-9) {
					signT = Math.abs(t);
					signCell = new BlockPos(x, 3, z);
				}
			}
			roof(m, s, x, z, r, t, roofY, ri, true, accentColor);
		}
		// the project's sign: a hanging sign under the lintel of the inner door
		if (signCell != null) {
			Direction f = RingGeometry.cardinal(RingGeometry.centreAngle(s));
			BlockState hs = Blocks.CHERRY_HANGING_SIGN.defaultBlockState().setValue(CeilingHangingSignBlock.ROTATION, rotation(f.getOpposite()))
					.setValue(CeilingHangingSignBlock.ATTACHED, false);
			put(signCell.getX(), 3, signCell.getZ(), hs, HANGING);
			data.put(BlockPos.asLong(signCell.getX(), 3, signCell.getZ()), wingSign(lead).signTag());
		}
		// stairs between floors
		for (int f = 0; f + 1 < F; f++) {
			Direction up = st.climbs(f);
			int y0 = RingGeometry.floorY(f);
			for (int v : st.lane(f)) {
				for (int i = 0; i < 7; i++) {
					BlockPos p = st.cell(st.stepU(f, i), v, y0 + 1 + i);
					set(p, stairs(Blocks.BAMBOO_MOSAIC_STAIRS, up));
					for (int y = p.getY() + 1; y <= p.getY() + 3 && y < RingGeometry.floorY(f + 1) + 3; y++) {
						if (y != RingGeometry.floorY(f + 1) || hole.contains(BlockPos.asLong(p.getX(), y, p.getZ()))) {
							set(p.getX(), y, p.getZ(), AIR);
						}
					}
				}
			}
		}
		// desks, anchors
		for (Map.Entry<Integer, HqLayout.Module> e : floors.entrySet()) {
			int f = e.getKey();
			HqLayout.Module mod = e.getValue();
			int i = mod.index();
			for (int slot = 0; slot < mod.capacity(); slot++) {
				String thread = mod.threadAt(slot);
				if (thread != null) {
					desk(i, s, f, slot, thread);
				}
			}
			double a = RingGeometry.centreAngle(s);
			Direction out = RingGeometry.cardinal(a);
			Anchor door = Anchor.at(AnchorNames.module(i), RingGeometry.polar(f == 0 ? ri + 3 : ri + 12, a, RingGeometry.floorY(f) + 1), out);
			Anchor walkA = Anchor.at(AnchorNames.walkModule(i), RingGeometry.polar(ri + 7.5, a, RingGeometry.floorY(f) + 1), out);
			anchor(door);
			anchor(walkA);
			if (layout.modulesOf(mod.projectKey()).get(0) == mod) {
				anchor(door.renamed(AnchorNames.wing(mod.projectKey())));
				anchor(walkA.renamed(AnchorNames.walk(mod.projectKey())));
			}
		}
		// planters on the inner corners of each floor
		for (int f = 0; f < F; f++) {
			for (int side = -1; side <= 1; side += 2) {
				BlockPos p = RingGeometry.polar(ri + 2.5, RingGeometry.centreAngle(s) + side * 6.5 / (ri + 2.5), RingGeometry.floorY(f) + 1);
				if (mine(s, p.getX(), p.getZ()) && isInterior(sc, p) && at(p.getX(), p.getY(), p.getZ()) == AIR) {
					set(p, kit.gardenPlanter());
				}
			}
		}
	}

	private static boolean isInterior(SectorCells sc, BlockPos p) {
		for (long[] c : sc.interior()) {
			if (c[0] == p.getX() && c[1] == p.getZ()) {
				return true;
			}
		}
		return false;
	}

	private static int rotation(Direction facing) {
		return switch (facing) {
			case SOUTH -> 0;
			case WEST -> 4;
			case NORTH -> 8;
			default -> 12;
		};
	}

	/**
	 * Roofs by ring, so each ring reads as its own quarter: ring 1 a copper barrel vault along the arc
	 * with a glass skylight on the centre line, ring 2 a flat roof garden behind a parapet, ring 3+ a
	 * pitched tile roof.
	 */
	private void roof(int m, int s, int x, int z, double r, double t, int roofY, int ri, boolean wall, DyeColor accentColor) {
		double u = (r - ri) / RingGeometry.DEPTH;
		if (m == 1 || m >= 3) {
			int h = m == 1 ? (int) Math.round(4.5 * Math.sin(Math.PI * Math.max(0, Math.min(1, u))))
					: (int) Math.round(5 * (1 - Math.abs(2 * u - 1)));
			int base = roofY;
			if (wall) {
				for (int y = base + 1; y <= base + h; y++) {
					set(x, y, z, m == 1 ? kit.wallPanel(false) : MUD_BRICKS);
				}
				set(x, base + h + 1, z, m == 1 ? COPPER : DEEPSLATE_TILES);
			} else {
				for (int y = base; y <= base + h; y++) {
					if (at(x, y, z) == null) {
						set(x, y, z, AIR);
					}
				}
				BlockState skin = m == 1 ? (Math.abs(t) <= 1.0 ? GLASS : (Math.floorMod((int) Math.round(t), 6) == 0 ? COPPER_WARM : COPPER)) : DEEPSLATE_TILES;
				set(x, base + h + 1, z, skin);
			}
			if (!wall && Math.abs(t) > 1.0 && Math.floorMod(x * 7 + z * 11, 17) == 0) {
				put(x, base + h, z, hangingLantern(), HANGING);
			}
		} else {
			// ring 2: flat roof garden
			if (wall) {
				set(x, roofY + 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
			} else {
				set(x, roofY, z, Math.floorMod(x * 5 + z * 3, 13) == 0 ? SEA_LANTERN : DEEPSLATE_TILES);
				int pattern = Math.floorMod(x * 3 + z * 7, 9);
				if (pattern == 0) {
					set(x, roofY + 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
				} else if (pattern == 4) {
					set(x, roofY + 1, z, b(Blocks.MOSS_CARPET));
				}
			}
		}
	}

	private HqStyle.SignText wingSign(HqLayout.Module w) {
		String label = w.label();
		String l1 = label.length() <= SignTags.LINE ? label : label.substring(0, SignTags.LINE);
		String l2 = label.length() <= SignTags.LINE ? "" : label.substring(SignTags.LINE);
		int floors = layout.modulesOf(w.projectKey()).size();
		return new HqStyle.SignText(HqStyle.envColor(w), l1, l2, floors > 1 ? floors + " floors" : "", w.envLabel());
	}

	private void desk(int module, int s, int f, int slot, String thread) {
		DeskSpot d = RingGeometry.desk(s, f, slot);
		Direction face = d.facing();
		Direction right = face.getClockWise();
		BlockPos desk = d.desk();
		for (int k = -1; k <= 1; k++) {
			set(desk.relative(right, k), kit.desk(face.getOpposite()));
		}
		BlockPos monitor = desk.above();
		Direction screenFacing = face.getOpposite();
		for (BlockPos p : Workstation.screenCells(Workstation.screenOriginFor(monitor, screenFacing), screenFacing.getCounterClockWise())) {
			set(p, style.computer(true));
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

	// ------------------------------------------------------------------ groups and modules

	/** The construction group of a cell (see the class comment). */
	String groupOf(int x, int y, int z) {
		Zone zn = zoneAt(x, z);
		return switch (zn.kind()) {
			case HUB, HUB_WALL -> "hub";
			case BRIDGE, PATIO, GALLERY, RAMP, TERRACE, INNER_STREET -> "cloister";
			case NODE -> zn.node() == 1004 ? HqGenerator.GROUP_ARCHIVE : "node_" + (zn.node() / 1000) + "_" + (zn.node() % 1000);
			case AVENUE, STREET, APRON -> "ring_" + zn.ring();
			case SECTOR -> {
				Map<Integer, HqLayout.Module> floors = sectors.get(zn.sector());
				if (floors == null) {
					yield "ring_" + zn.ring();
				}
				int F = Collections.max(floors.keySet()) + 1;
				int f = Math.max(0, Math.min(F - 1, Math.floorDiv(y, RingGeometry.FLOOR_H)));
				yield HqGenerator.group(f * RingGeometry.FLOOR_STRIDE + zn.sector());
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
		if (g.startsWith("module_")) {
			return 5;
		}
		return 6;
	}

	private static final Comparator<String> GROUPS = Comparator.comparingInt(RingGenerator::groupRank).thenComparing((a, b) -> {
		if (a.startsWith("module_") && b.startsWith("module_")) {
			return Integer.compare(Integer.parseInt(a.substring(7)), Integer.parseInt(b.substring(7)));
		}
		return a.compareTo(b);
	});

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
		// group of every cell, placements in build order
		record Cell(BlockPos pos, String group, int layer) {
		}
		List<Cell> all = new ArrayList<>(cells.size());
		Map<BlockPos, String> index = new HashMap<>(cells.size() * 2);
		for (long k : cells.keySet()) {
			BlockPos p = BlockPos.of(k);
			String g = groupOf(p.getX(), p.getY(), p.getZ());
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
