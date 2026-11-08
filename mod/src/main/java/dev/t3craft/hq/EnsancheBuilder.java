package dev.t3craft.hq;

import static dev.t3craft.hq.EnsancheGrid.BLOCK_HALF;
import static dev.t3craft.hq.EnsancheGrid.CELL_HALF;
import static dev.t3craft.hq.EnsancheGrid.CHAMFER;
import static dev.t3craft.hq.EnsancheGrid.STOREY;

import java.util.List;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The ensanche HQ as a {@link BuildPlan} (layout/blocks). Pure and deterministic like
 * {@link HqGenerator}; geometry from {@link EnsancheGrid}, rooms from an {@link EnsancheAllocator}
 * layout. Builds, in order: the plaza mayor (paving, fountain, arcades; the civic hall with the board,
 * the kanban wall, the big screen, benches, the hologram's free column under the lantern and its
 * beacon; the archive terminal's nook; the clock tower; the review hall), then for every cell
 * {@code 0..maxCell} its half streets and its manzana: the covered market, a small square on the
 * diagonal promenade, a vacant manzana, or a work manzana (garden patio and four parcels: a building
 * with its rooms, stairs and covered passage, or a vacant lot).
 *
 * <p>Groups (buildable units, {@link BuildPlan#groups()}): {@code plaza}, {@code hub}, {@code archive},
 * {@code clock}, {@code review}, {@code streets_<k>}, {@code lounge}, {@code plazuela_<k>},
 * {@code solar_<k>}, {@code patio_<k>}, {@code parcel_<p>}. A parcel's group holds its whole building
 * (every floor), so adding a floor rebuilds only that building.
 */
final class EnsancheBuilder {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	/** Columns of a building that belong to its corner segment (may rise one storey more). */
	static final int CORNER = 8;

	private final EnsancheStyle style;
	private final HqKit kit = HqKit.active();
	private final boolean custom = kit.custom();

	EnsancheBuilder(EnsancheStyle style) {
		this.style = style;
	}

	BuildPlan generate(HqLayout layout) {
		PlanBuilder b = new PlanBuilder();
		int maxCell = EnsancheGrid.maxCell(layout);
		TreeMap<Integer, TreeMap<Integer, HqLayout.Module>> parcels = new TreeMap<>();
		for (HqLayout.Module m : layout.modules()) {
			parcels.computeIfAbsent(EnsancheGrid.parcelOfModule(m.index()), q -> new TreeMap<>()).put(EnsancheGrid.floorOfModule(m.index()), m);
		}
		plaza(b, layout);
		for (int k = 0; k <= maxCell; k++) {
			streets(b, k);
		}
		for (int k = 1; k <= maxCell; k++) {
			switch (EnsancheGrid.kind(k)) {
				case MARKET -> market(b, k);
				case PLAZUELA -> plazuela(b, k);
				case WORK -> {
					boolean any = false;
					for (int side = 0; side < EnsancheGrid.SIDES; side++) {
						any |= parcels.containsKey(EnsancheGrid.parcel(k, side));
					}
					if (!any) {
						vacantBlock(b, k);
						break;
					}
					EnsancheStyle.Mat mat = EnsancheStyle.mat(k);
					patio(b, k, mat);
					for (int side = 0; side < EnsancheGrid.SIDES; side++) {
						TreeMap<Integer, HqLayout.Module> floors = parcels.get(EnsancheGrid.parcel(k, side));
						if (floors == null) {
							solar(b, k, side, mat);
						} else {
							building(b, layout, k, side, floors, mat);
						}
					}
				}
				default -> throw new IllegalStateException("cell " + k);
			}
		}
		b.group(null);
		return b.build();
	}

	// ------------------------------------------------------------------ materials

	private static BlockState s(net.minecraft.world.level.block.Block b) {
		return b.defaultBlockState();
	}

	private static final BlockState ROAD = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
	private static final BlockState LINE = Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
	private static final BlockState SIDEWALK = s(Blocks.SMOOTH_STONE);
	private static final BlockState CURB = s(Blocks.STONE_BRICKS);
	private static final BlockState PASEO = s(Blocks.POLISHED_GRANITE);
	private static final BlockState GRASS = s(Blocks.GRASS_BLOCK);

	private BlockState floorTile(EnsancheStyle.Mat mat) {
		return custom ? kit.floor() : mat.floor();
	}

	private BlockState accent() {
		return custom ? kit.floorAccent() : s(Blocks.POLISHED_ANDESITE);
	}

	private BlockState lightPanel() {
		return custom ? kit.wallPanel(true) : s(Blocks.PEARLESCENT_FROGLIGHT);
	}

	private BlockState partition(EnsancheStyle.Mat mat) {
		return custom ? kit.wallPanel(false) : mat.wall2();
	}

	private BlockState desk(Direction front) {
		return custom ? kit.desk(front) : style.palette().get(HqStyle.Role.DESK);
	}

	private static BlockState lantern() {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false);
	}

	private static BlockState hanging() {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
	}

	private static BlockState topSlab(net.minecraft.world.level.block.Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	private static BlockState pane() {
		return s(Blocks.GLASS_PANE);
	}

	private static BlockState wallSign(Direction front) {
		return Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, front);
	}

	private static BlockState standingSign(Direction front) {
		int rot = Math.floorMod(Math.round(front.toYRot() / 22.5f), 16);
		return Blocks.SPRUCE_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, rot);
	}

	private static CompoundTag text(DyeColor color, String l1, String l2, String l3, String l4) {
		return new HqStyle.SignText(color, l1, l2, l3, l4).signTag();
	}

	/** Small deterministic hash of a cell (decoration only). */
	private static int mix(int a, int b) {
		int h = a * 0x9E3779B1 ^ b * 0x85EBCA77;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		return (h ^ (h >>> 13)) & 0x7fffffff;
	}

	/** A tree whose cells are only written where the canvas owns them. */
	private static void tree(PlanBuilder.Canvas c, int x, int z, int height, BlockState log, BlockState leaves) {
		for (int y = 1; y < height; y++) {
			if (c.owns(x, y, z)) {
				c.set(x, y, z, log);
			}
		}
		for (int y = height - 2; y <= height + 1; y++) {
			int r = y >= height ? 1 : 2;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if ((dx == 0 && dz == 0 && y < height) || (Math.abs(dx) == r && Math.abs(dz) == r && r > 1)) {
						continue;
					}
					if (c.owns(x + dx, y, z + dz)) {
						c.set(x + dx, y, z + dz, leaves);
					}
				}
			}
		}
	}

	private static void lampPost(PlanBuilder.Canvas c, int x, int z) {
		c.set(x, 1, z, s(Blocks.POLISHED_BASALT));
		c.set(x, 2, z, s(Blocks.POLISHED_BASALT));
		c.decor(x, 3, z, lantern());
	}

	private void bigScreen(PlanBuilder b, PlanBuilder.Canvas c, HqGeometry.Surface sf, String key, String binding) {
		for (BlockPos p : sf.cells()) {
			c.set(p, style.screen());
		}
		b.anchor(Anchor.face(AnchorNames.bigScreen(key, sf.width(), sf.height(), binding), sf.origin().above(sf.height() - 1), sf.facing()));
	}

	/** Chamfered manzana corner ({@code u, v} from the manzana's north-west corner). */
	private static boolean chamfered(int u, int v) {
		int e = EnsancheGrid.BLOCK - 1;
		return Math.min(u, e - u) + Math.min(v, e - v) < CHAMFER;
	}

	// ------------------------------------------------------------------ streets

	private void streets(PlanBuilder b, int k) {
		int cx = EnsancheGrid.cx(k);
		int cz = EnsancheGrid.cz(k);
		int top = 7;
		b.group("streets_" + k);
		Box[] strips = {
				new Box(cx - CELL_HALF, 0, cz - CELL_HALF, cx + CELL_HALF, top, cz - BLOCK_HALF - 1),
				new Box(cx - CELL_HALF, 0, cz + BLOCK_HALF + 1, cx + CELL_HALF, top, cz + CELL_HALF),
				new Box(cx - CELL_HALF, 0, cz - BLOCK_HALF, cx - BLOCK_HALF - 1, top, cz + BLOCK_HALF),
				new Box(cx + BLOCK_HALF + 1, 0, cz - BLOCK_HALF, cx + CELL_HALF, top, cz + BLOCK_HALF)};
		String[] names = {"n", "s", "w", "e"};
		EnsancheGrid.Kind kind = EnsancheGrid.kind(k);
		for (int i = 0; i < 4; i++) {
			Box box = strips[i];
			try (PlanBuilder.Canvas c = b.module("street_" + k + "_" + names[i], box, List.of())) {
				for (int x = box.minX(); x <= box.maxX(); x++) {
					for (int z = box.minZ(); z <= box.maxZ(); z++) {
						street(c, kind, x - cx, z - cz, x, z);
					}
				}
			}
		}
	}

	private void street(PlanBuilder.Canvas c, EnsancheGrid.Kind kind, int dx, int dz, int x, int z) {
		int ex = Math.abs(dx) - BLOCK_HALF;
		int ez = Math.abs(dz) - BLOCK_HALF;
		if (ex > 0 && ez > 0) {
			// a corner square: rounded sidewalk, then road; the diagonal promenade crosses the plaza's and the squares' corners
			boolean ne = dx > 0 && dz < 0;
			boolean sw = dx < 0 && dz > 0;
			boolean diagonal = (kind == EnsancheGrid.Kind.PLAZA && ne) || (kind == EnsancheGrid.Kind.PLAZUELA && (ne || sw));
			if (diagonal && Math.abs(ex - ez) <= 1) {
				c.set(x, 0, z, PASEO);
			} else if (ex + ez <= 4) {
				c.set(x, 0, z, ex + ez == 4 ? CURB : SIDEWALK);
			} else {
				c.set(x, 0, z, ROAD);
			}
			return;
		}
		int e = Math.max(ex, ez);
		int t = ex > 0 ? dz : dx; // along the street
		if (e <= 2) {
			c.set(x, 0, z, e == 2 ? CURB : SIDEWALK);
			if (e == 2 && Math.abs(t) <= 18 && Math.floorMod(t, 9) == 0 && t != 0) {
				lampPost(c, x, z);
			}
			return;
		}
		boolean zebra = Math.abs(t) >= 18 && Math.abs(t) <= 21 && Math.floorMod(t, 2) == 0;
		boolean line = e == EnsancheGrid.HALF_STREET && Math.floorMod(t, 4) < 2 && Math.abs(t) < 17;
		c.set(x, 0, z, zebra || line ? LINE : ROAD);
	}

	// ------------------------------------------------------------------ plaza mayor (the hub)

	/** Civic hall: walls at x = ±14, z = -21 (north) and z = -3 (south, the entrance). */
	static final int HALL_X = 14;
	static final int HALL_N = -21;
	static final int HALL_S = -3;
	/** Centre of the hall (the hologram's column, the lantern, the beacon). */
	static final int HALL_CZ = -12;
	static final int BOARD_Z = HALL_N + 1;
	static final int BOARD_HALF = 3;
	static final BlockPos ARCHIVE_TERMINAL = new BlockPos(6, 1, HALL_S);
	static final BlockPos SPAWN = new BlockPos(0, 1, BLOCK_HALF + 3);
	static final int HOLOGRAM_TOP = 8;
	static final int CEILING = 12;

	static Box hallBox() {
		return new Box(-HALL_X, 0, HALL_N, HALL_X, CEILING + 5, HALL_S);
	}

	static Box archiveNook() {
		return new Box(5, 1, HALL_S, 7, 4, HALL_S);
	}

	static Box clockBox() {
		return new Box(13, 0, 13, BLOCK_HALF, EnsancheGrid.TOP, BLOCK_HALF);
	}

	static Box reviewBox() {
		return new Box(-BLOCK_HALF, 0, 13, -13, 14, BLOCK_HALF);
	}

	static HqGeometry.Surface threadWall() {
		return new HqGeometry.Surface(new BlockPos(-HALL_X + 1, 1, HALL_CZ + 4), Direction.NORTH, Direction.EAST, 9, 4);
	}

	static HqGeometry.Surface hubScreen() {
		return new HqGeometry.Surface(new BlockPos(HALL_X - 1, 2, HALL_CZ - 2), Direction.SOUTH, Direction.WEST, 5, 3);
	}

	private void plaza(PlanBuilder b, HqLayout layout) {
		Box block = new Box(-BLOCK_HALF, 0, -BLOCK_HALF, BLOCK_HALF, EnsancheGrid.TOP, BLOCK_HALF);
		b.group("plaza");
		try (PlanBuilder.Canvas c = b.module("plaza", block, List.of(hallBox(), clockBox(), reviewBox()))) {
			plazaGround(c);
		}
		b.group("hub");
		hall(b, HqGenerator.hubBinding(layout));
		b.group(HqGenerator.GROUP_ARCHIVE);
		archive(b);
		b.group("clock");
		clockTower(b);
		b.group("review");
		reviewHall(b);
		b.anchor(Anchor.at(AnchorNames.SPAWN, SPAWN, Direction.NORTH));
	}

	private static BlockState plazaPave(int x, int z) {
		int fz = z - 9;
		if (Math.abs(Math.abs(x) - Math.abs(fz)) <= 1 && z > HALL_S) {
			return PASEO; // diagonals from the fountain to the plaza's corners
		}
		if (Math.abs(x) >= 20 || Math.abs(z) >= 20) {
			return SIDEWALK;
		}
		return Math.floorMod(x + z, 2) == 0 ? s(Blocks.POLISHED_ANDESITE) : s(Blocks.SMOOTH_STONE);
	}

	private void plazaGround(PlanBuilder.Canvas c) {
		for (int x = -BLOCK_HALF; x <= BLOCK_HALF; x++) {
			for (int z = -BLOCK_HALF; z <= BLOCK_HALF; z++) {
				if (c.owns(x, 0, z)) {
					c.set(x, 0, z, chamfered(x + BLOCK_HALF, z + BLOCK_HALF) ? SIDEWALK : plazaPave(x, z));
				}
			}
		}
		// fountain
		int fz = 9;
		for (int x = -2; x <= 2; x++) {
			for (int z = fz - 2; z <= fz + 2; z++) {
				boolean rim = Math.abs(x) == 2 || Math.abs(z - fz) == 2;
				c.set(x, 0, z, s(Blocks.DARK_PRISMARINE));
				if (rim) {
					c.set(x, 1, z, s(Blocks.POLISHED_DEEPSLATE));
				}
			}
		}
		c.set(0, 1, fz, s(Blocks.PRISMARINE_BRICKS));
		c.set(0, 2, fz, s(Blocks.PRISMARINE_BRICKS));
		c.set(0, 3, fz, s(Blocks.SEA_LANTERN));
		// benches around the fountain, facing it
		for (int i = -1; i <= 1; i++) {
			c.set(5, 1, fz + i, style.bench(Direction.EAST));
			c.set(-5, 1, fz + i, style.bench(Direction.WEST));
			c.set(i, 1, fz + 5, style.bench(Direction.SOUTH));
		}
		// plane trees and lamps
		BlockState log = EnsancheStyle.log(Blocks.OAK_LOG);
		BlockState leaves = EnsancheStyle.leaves(Blocks.OAK_LEAVES);
		for (int sx = -1; sx <= 1; sx += 2) {
			tree(c, 9 * sx, 3, 6, log, leaves);
			tree(c, 9 * sx, 16, 6, log, leaves);
			tree(c, 18 * sx, -12, 7, log, leaves);
			lampPost(c, 6 * sx, 0);
			lampPost(c, 6 * sx, 18);
			lampPost(c, 18 * sx, -4);
			lampPost(c, 18 * sx, -19);
		}
		// arcades (soportales) on both sides of the square
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int z = -1; z <= 11; z++) {
				for (int ax = 17; ax <= 21; ax++) {
					c.set(sx * ax, 5, z, topSlab(Blocks.SMOOTH_STONE_SLAB));
				}
				if (Math.floorMod(z + 1, 4) == 0) {
					for (int y = 1; y <= 4; y++) {
						c.set(sx * 17, y, z, s(Blocks.QUARTZ_PILLAR));
						c.set(sx * 21, y, z, s(Blocks.QUARTZ_PILLAR));
					}
				} else if (Math.floorMod(z + 1, 4) == 2) {
					// a lantern can't hang from a top slab's underside: a full block of the vault holds it
					c.set(sx * 19, 5, z, s(Blocks.SMOOTH_STONE));
					c.hang(sx * 19, 4, z, hanging());
				}
			}
		}
	}

	private void hall(PlanBuilder b, String binding) {
		Box box = hallBox();
		BlockState stone = s(Blocks.STONE_BRICKS);
		BlockState pillar = s(Blocks.QUARTZ_PILLAR);
		BlockState trim = s(Blocks.SMOOTH_QUARTZ);
		BlockState plinth = s(Blocks.POLISHED_DEEPSLATE);
		try (PlanBuilder.Canvas c = b.module("hub", box, List.of(archiveNook()))) {
			for (int x = -HALL_X; x <= HALL_X; x++) {
				for (int z = HALL_N; z <= HALL_S; z++) {
					boolean wall = Math.abs(x) == HALL_X || z == HALL_N || z == HALL_S;
					boolean inner = !wall;
					if (inner) {
						int ring = Math.max(Math.abs(x), Math.abs(z - HALL_CZ));
						c.set(x, 0, z, ring <= 3 ? accent() : Math.abs(x) == HALL_X - 1 || z == HALL_N + 1 || z == HALL_S - 1 ? plinth
								: Math.floorMod(x + z, 2) == 0 ? s(Blocks.POLISHED_ANDESITE) : s(Blocks.SMOOTH_STONE));
						boolean well = Math.abs(x) <= 2 && Math.abs(z - HALL_CZ) <= 2;
						int lightEvery = dev.t3craft.hq.pack.LayoutParams.ENSANCHE_CEILING_LIGHT.get();
						c.set(x, CEILING, z, well ? AIR : Math.floorMod(x, lightEvery) == 0 && Math.floorMod(z, lightEvery) == 0 ? lightPanel() : trim);
						continue;
					}
					c.set(x, 0, z, plinth);
					boolean corner = Math.abs(x) == HALL_X && (z == HALL_N || z == HALL_S);
					boolean pil = corner || (z == HALL_N || z == HALL_S ? Math.floorMod(x, 4) == 2 : Math.floorMod(z, 4) == 0);
					boolean screened = (x == -HALL_X && z <= HALL_CZ + 4 && z >= HALL_CZ - 4) || (x == HALL_X && Math.abs(z - HALL_CZ) <= 2)
							|| (z == HALL_N && Math.abs(x) <= 4);
					boolean door = z == HALL_S && Math.abs(x) <= 2;
					for (int y = 1; y <= CEILING; y++) {
						BlockState st;
						if (door && y <= 5) {
							st = AIR;
						} else if (pil) {
							st = pillar;
						} else if (y == 1) {
							st = plinth;
						} else if (y == CEILING || y == 6) {
							st = trim;
						} else if (!screened && !door && y >= 3 && y <= 10 && y != 6) {
							st = pane();
						} else {
							st = stone;
						}
						if (c.owns(x, y, z)) {
							c.set(x, y, z, st);
						}
					}
					c.set(x, CEILING + 1, z, pil ? trim : EnsancheStyle.slab(Blocks.SMOOTH_QUARTZ_SLAB));
				}
			}
			// lantern over the light well: glazed walls, cap, beacon, rod
			for (int x = -3; x <= 3; x++) {
				for (int z = HALL_CZ - 3; z <= HALL_CZ + 3; z++) {
					boolean ring = Math.abs(x) == 3 || Math.abs(z - HALL_CZ) == 3;
					if (ring) {
						c.set(x, CEILING, z, trim);
						for (int y = CEILING + 1; y <= CEILING + 3; y++) {
							c.set(x, y, z, Math.abs(x) == 3 && Math.abs(z - HALL_CZ) == 3 ? pillar : pane());
						}
					}
					c.set(x, CEILING + 4, z, ring ? EnsancheStyle.slab(Blocks.CUT_COPPER_SLAB.waxed().unaffected()) : s(Blocks.CUT_COPPER.waxed().unaffected()));
				}
			}
			BlockPos beacon = new BlockPos(0, CEILING + 4, HALL_CZ);
			c.set(beacon, style.beacon());
			c.set(beacon.above(), Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
			// inner columns and chandeliers
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int z : new int[] {HALL_N + 3, HALL_S - 3}) {
					for (int y = 1; y < CEILING; y++) {
						c.set(10 * sx, y, z, pillar);
					}
				}
				for (int z : new int[] {HALL_CZ - 5, HALL_CZ + 5}) {
					c.hang(6 * sx, CEILING - 1, z, hanging());
				}
			}
			board(b, c);
			// benches facing the side walls: the kanban wall (west) and the big screen (east)
			int bench = 0;
			for (int sx = -1; sx <= 1; sx += 2) {
				Direction look = sx > 0 ? Direction.EAST : Direction.WEST;
				for (int dz : new int[] {-4, -3, -2, 2, 3, 4}) {
					BlockPos p = new BlockPos(7 * sx, 1, HALL_CZ + dz);
					c.set(p, style.bench(look.getOpposite()));
					b.anchor(Anchor.at(AnchorNames.bench(bench++), p.relative(look), look));
				}
			}
			HqGeometry.Surface hub = hubScreen();
			bigScreen(b, c, hub, "hub", binding);
			for (int i = 0; i < hub.width(); i++) {
				c.set(hub.origin().relative(hub.along(), i).below(), trim);
			}
			HqGeometry.Surface wall = threadWall();
			for (BlockPos p : wall.cells()) {
				c.set(p, style.screen());
			}
			b.anchor(Anchor.face(AnchorNames.THREAD_WALL, wall.origin(), wall.facing()));
			b.anchor(Anchor.face(AnchorNames.THREAD_WALL_END, wall.origin().relative(wall.along(), wall.width() - 1).above(wall.height() - 1),
					wall.facing()));
			b.anchor(Anchor.at(AnchorNames.THREAD_WALL_FRONT, wall.anchorCell().relative(wall.facing(), 3).atY(1), wall.facing().getOpposite()));
			c.decor(new BlockPos(6, 3, HALL_S - 1), wallSign(Direction.NORTH), text(DyeColor.BROWN, "", "Archivo", "", ""));
			// the hologram's column stays open up to the lantern
			for (int y = 1; y <= HOLOGRAM_TOP; y++) {
				c.set(0, y, HALL_CZ, AIR);
			}
			int walk = 0;
			for (int wz : new int[] {HALL_N + 4, HALL_S - 3}) {
				for (int sx = -1; sx <= 1; sx += 2) {
					b.anchor(Anchor.at(AnchorNames.walkHub(walk++), new BlockPos(4 * sx, 1, wz), Direction.SOUTH));
				}
			}
			b.anchor(Anchor.at(AnchorNames.HUB, new BlockPos(0, 1, HALL_CZ), Direction.SOUTH));
			b.anchor(new Anchor(AnchorNames.HOLOGRAM, 0.5, 5.0, HALL_CZ + 0.5, Direction.SOUTH, new BlockPos(0, 5, HALL_CZ)));
			b.anchor(Anchor.at(AnchorNames.BEACON, beacon, Direction.SOUTH));
			b.anchor(Anchor.at(AnchorNames.ENTRANCE, new BlockPos(0, 1, HALL_S), Direction.SOUTH));
		}
	}

	/** The "needs attention" board on the hall's north wall, facing the entrance. */
	private void board(PlanBuilder b, PlanBuilder.Canvas c) {
		int z = BOARD_Z;
		BlockState frame = s(Blocks.POLISHED_DEEPSLATE);
		for (int y = 1; y <= 6; y++) {
			c.set(-BOARD_HALF - 1, y, z, s(Blocks.QUARTZ_PILLAR));
			c.set(BOARD_HALF + 1, y, z, s(Blocks.QUARTZ_PILLAR));
		}
		for (int x = -BOARD_HALF; x <= BOARD_HALF; x++) {
			c.set(x, 1, z, frame);
			for (int y = 2; y <= 4; y++) {
				c.set(x, y, z, style.palette().get(HqStyle.Role.BOARD));
			}
			c.set(x, 5, z, frame);
			c.set(x, 6, z, s(Blocks.SMOOTH_QUARTZ));
		}
		c.decor(new BlockPos(0, 6, z + 1), wallSign(Direction.SOUTH), text(DyeColor.ORANGE, "", "Needs", "attention", ""));
		int lamp = 0;
		for (int x = -BOARD_HALF; x <= BOARD_HALF; x++) {
			if (Math.floorMod(x + BOARD_HALF, 2) == 0) {
				c.set(x, 5, z, style.statusLamp());
				b.anchor(Anchor.face(AnchorNames.boardLamp(lamp++), new BlockPos(x, 5, z), Direction.SOUTH));
			}
		}
		b.anchor(Anchor.face(AnchorNames.BOARD, new BlockPos(0, 3, z), Direction.SOUTH));
		b.anchor(Anchor.at(AnchorNames.BOARD_FRONT, new BlockPos(0, 1, z + 1), Direction.NORTH));
		int slot = 0;
		for (int row = 2; row <= 3; row++) {
			for (int i = 0; i <= 2 * BOARD_HALF; i++) {
				int x = i == 0 ? 0 : (i % 2 == 1 ? (i + 1) / 2 : -(i / 2));
				b.anchor(Anchor.at(AnchorNames.boardSlot(slot++), new BlockPos(x, 1, z + row), Direction.NORTH));
			}
		}
	}

	private void archive(PlanBuilder b) {
		Box n = archiveNook();
		try (PlanBuilder.Canvas c = b.module("archive", n, List.of())) {
			for (int x = n.minX(); x <= n.maxX(); x++) {
				for (int y = 1; y <= 4; y++) {
					boolean mid = x == ARCHIVE_TERMINAL.getX();
					c.set(x, y, n.minZ(), y == 4 ? s(Blocks.SMOOTH_QUARTZ) : mid && y == 1 ? AIR : mid ? s(Blocks.CHISELED_BOOKSHELF) : s(Blocks.BOOKSHELF));
				}
			}
			c.set(ARCHIVE_TERMINAL, style.archiveTerminal(Direction.NORTH));
			b.anchor(Anchor.face(AnchorNames.ARCHIVE_TERMINAL, ARCHIVE_TERMINAL, Direction.NORTH));
			b.anchor(Anchor.at(AnchorNames.ARCHIVE, ARCHIVE_TERMINAL.north(), Direction.SOUTH));
		}
	}

	/** Clock tower on the plaza's south-east chamfer: the scheduled tasks' room and two clock faces. */
	private void clockTower(PlanBuilder b) {
		Box box = clockBox();
		BlockState wall = s(Blocks.STONE_BRICKS);
		BlockState trim = s(Blocks.SMOOTH_QUARTZ);
		int top = 26;
		try (PlanBuilder.Canvas c = b.module("clock", box, List.of())) {
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					boolean body = x >= 14 && x <= 21 && z >= 14 && z <= 21 && x + z <= 39;
					if (!body) {
						c.set(x, 0, z, chamfered(x + BLOCK_HALF, z + BLOCK_HALF) ? SIDEWALK : plazaPave(x, z));
						continue;
					}
					boolean edge = x == 14 || x == 21 || z == 14 || z == 21 || x + z == 39;
					c.set(x, 0, z, edge ? s(Blocks.POLISHED_DEEPSLATE) : accent());
					if (!edge) {
						c.set(x, 6, z, x == 17 && z == 17 ? lightPanel() : trim);
						continue;
					}
					boolean door = (z == 14 && (x == 17 || x == 18)) || (x == 14 && (z == 17 || z == 18));
					boolean corner = (x == 14 || x == 21) && (z == 14 || z == 21) || x + z == 39 && (x == 21 || z == 21);
					for (int y = 1; y <= top; y++) {
						BlockState st = door && y <= 3 ? AIR : corner || y % 6 == 0 ? trim
								: y > 8 && y % 6 >= 2 && y % 6 <= 4 && (x + z) % 2 == 0 && !(y >= 19 && y <= 23) ? pane() : wall;
						c.set(x, y, z, st);
					}
					c.set(x, top + 1, z, trim);
				}
			}
			// roof platform and belfry
			for (int x = 14; x <= 21; x++) {
				for (int z = 14; z <= 21; z++) {
					if (x + z <= 39) {
						c.set(x, top + 1, z, trim);
					}
				}
			}
			for (int x : new int[] {16, 19}) {
				for (int z : new int[] {16, 19}) {
					for (int y = top + 2; y <= top + 4; y++) {
						c.set(x, y, z, s(Blocks.QUARTZ_PILLAR));
					}
				}
			}
			for (int x = 15; x <= 20; x++) {
				for (int z = 15; z <= 20; z++) {
					c.set(x, top + 5, z, Math.abs(x - 17.5) < 2 && Math.abs(z - 17.5) < 2 ? s(Blocks.CUT_COPPER.waxed().unaffected())
							: EnsancheStyle.slab(Blocks.CUT_COPPER_SLAB.waxed().unaffected()));
				}
			}
			c.hang(17, top + 4, 17, hanging());
			c.set(17, top + 6, 17, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
			// clock faces (north and west) and the scheduled tasks' screen inside
			bigScreen(b, c, new HqGeometry.Surface(new BlockPos(18, 20, 14), Direction.WEST, Direction.NORTH, 3, 3), "clock_n", "");
			bigScreen(b, c, new HqGeometry.Surface(new BlockPos(14, 20, 16), Direction.SOUTH, Direction.WEST, 3, 3), "clock_w", "");
			bigScreen(b, c, new HqGeometry.Surface(new BlockPos(17, 2, 20), Direction.WEST, Direction.NORTH, 3, 2), "schedule", "");
			c.decor(new BlockPos(17, 4, 15), wallSign(Direction.SOUTH), text(DyeColor.BLUE, "", "Tareas", "programadas", ""));
			b.anchor(Anchor.at("walk_clock", new BlockPos(16, 1, 17), Direction.SOUTH));
		}
	}

	/** Review hall (pull requests) on the plaza's south-west chamfer. */
	private void reviewHall(PlanBuilder b) {
		Box box = reviewBox();
		BlockState wall = s(Blocks.SMOOTH_SANDSTONE);
		BlockState trim = s(Blocks.CUT_SANDSTONE);
		int h = 8;
		try (PlanBuilder.Canvas c = b.module("review", box, List.of())) {
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					boolean body = x >= -21 && x <= -14 && z >= 14 && z <= 21 && -x + z <= 39;
					if (!body) {
						c.set(x, 0, z, chamfered(x + BLOCK_HALF, z + BLOCK_HALF) ? SIDEWALK : plazaPave(x, z));
						continue;
					}
					boolean edge = x == -21 || x == -14 || z == 14 || z == 21 || -x + z == 39;
					c.set(x, 0, z, edge ? trim : accent());
					if (!edge) {
						c.set(x, h, z, x == -17 && z == 17 ? lightPanel() : s(Blocks.SMOOTH_SANDSTONE_SLAB).setValue(BlockStateProperties.SLAB_TYPE,
								SlabType.DOUBLE));
						continue;
					}
					boolean open = (z == 14 && x >= -19 && x <= -16) || (x == -14 && z >= 16 && z <= 19);
					for (int y = 1; y <= h; y++) {
						c.set(x, y, z, open && y <= 3 ? AIR : y == 5 || y == h ? trim : wall);
					}
					c.set(x, h + 1, z, EnsancheStyle.stairs(Blocks.SMOOTH_RED_SANDSTONE_STAIRS, inward(x, z)));
				}
			}
			for (int z = 16; z <= 19; z++) {
				c.set(-20, 1, z, z == 16 || z == 19 ? s(Blocks.BOOKSHELF) : s(Blocks.CHISELED_BOOKSHELF));
			}
			bigScreen(b, c, new HqGeometry.Surface(new BlockPos(-19, 2, 18), Direction.NORTH, Direction.EAST, 3, 2), "review", "");
			c.decor(new BlockPos(-17, 4, 15), wallSign(Direction.SOUTH), text(DyeColor.GREEN, "", "Revision", "PR", ""));
			b.anchor(Anchor.at("walk_review", new BlockPos(-16, 1, 17), Direction.WEST));
		}
	}

	/** Roof edge stairs of the review hall rise towards its centre. */
	private static Direction inward(int x, int z) {
		if (z == 14) {
			return Direction.SOUTH;
		}
		if (z == 21) {
			return Direction.NORTH;
		}
		return x == -21 ? Direction.EAST : Direction.WEST;
	}

	// ------------------------------------------------------------------ special manzanas

	/** The covered market (lounge): iron-and-glass halls, stalls, seats, a big screen. */
	private void market(PlanBuilder b, int k) {
		int cx = EnsancheGrid.cx(k);
		int cz = EnsancheGrid.cz(k);
		Box box = new Box(cx - BLOCK_HALF, 0, cz - BLOCK_HALF, cx + BLOCK_HALF, 14, cz + BLOCK_HALF);
		BlockState column = s(Blocks.POLISHED_BLACKSTONE_BRICKS);
		BlockState copper = s(Blocks.CUT_COPPER.waxed().unaffected());
		BlockState glass = s(Blocks.GLASS);
		b.group("lounge");
		try (PlanBuilder.Canvas c = b.module("lounge", box, List.of())) {
			for (int dx = -BLOCK_HALF; dx <= BLOCK_HALF; dx++) {
				for (int dz = -BLOCK_HALF; dz <= BLOCK_HALF; dz++) {
					int x = cx + dx;
					int z = cz + dz;
					if (chamfered(dx + BLOCK_HALF, dz + BLOCK_HALF)) {
						c.set(x, 0, z, SIDEWALK);
						continue;
					}
					int adx = Math.abs(dx);
					int adz = Math.abs(dz);
					boolean hall = adx <= 18 && adz <= 12;
					if (!hall) {
						boolean path = adx <= 1 || adz <= 1 || adx >= 21 || adz >= 21;
						c.set(x, 0, z, path ? SIDEWALK : GRASS);
						if (!path && mix(x, z) % 9 == 0) {
							c.decor(x, 1, z, mix(z, x) % 2 == 0 ? s(Blocks.POPPY) : s(Blocks.SHORT_GRASS));
						}
						continue;
					}
					c.set(x, 0, z, Math.floorMod((dx >> 1) + (dz >> 1), 2) == 0 ? s(Blocks.POLISHED_ANDESITE) : s(Blocks.SMOOTH_STONE));
					boolean col = Math.floorMod(dx + 18, 6) == 0 && (adz == 12 || adz == 6);
					if (col) {
						for (int y = 1; y <= 7; y++) {
							c.set(x, y, z, column);
						}
					}
					if (adz >= 7) {
						c.set(x, 8, z, adz == 12 || adx == 18 || col ? copper : glass);
					} else if (adz == 6) {
						c.set(x, 8, z, copper);
						for (int y = 9; y <= 11; y++) {
							c.set(x, y, z, Math.floorMod(dx + 18, 6) == 0 ? copper : pane());
						}
						c.set(x, 12, z, copper);
					} else {
						if (adx == 18) {
							for (int y = 8; y <= 11; y++) {
								c.set(x, y, z, adz == 5 ? copper : pane());
							}
						}
						c.set(x, 12, z, adx == 18 || adz == 0 ? copper : glass);
						if (adz == 0) {
							c.set(x, 13, z, EnsancheStyle.slab(Blocks.CUT_COPPER_SLAB.waxed().unaffected()));
						}
					}
				}
			}
			// stalls in the aisles, between the columns
			BlockState counter = topSlab(Blocks.SPRUCE_SLAB);
			for (int sz = -1; sz <= 1; sz += 2) {
				for (int dx = -18; dx < 18; dx += 6) {
					for (int i = 2; i <= 4; i++) {
						int x = cx + dx + i;
						c.set(x, 1, cz + 11 * sz, i == 3 ? s(Blocks.HAY_BLOCK) : s(Blocks.BARREL));
						c.set(x, 1, cz + 10 * sz, counter);
						if (i == 3) {
							c.decor(x, 2, cz + 10 * sz, s(Blocks.DECORATED_POT));
						}
					}
				}
			}
			// lounge seating in the nave: benches facing each other around planters
			for (int dx : new int[] {-12, -4, 4, 12}) {
				for (int i = -1; i <= 1; i++) {
					c.set(cx + dx + i, 1, cz - 2, style.bench(Direction.NORTH));
					c.set(cx + dx + i, 1, cz + 2, style.bench(Direction.SOUTH));
				}
				c.set(cx + dx, 1, cz, kit.gardenPlanter());
				c.decor(cx + dx - 1, 1, cz, kit.lantern());
				c.hang(cx + dx, 11, cz, hanging());
			}
			HqGeometry.Surface screen = new HqGeometry.Surface(new BlockPos(cx + 16, 2, cz - 2), Direction.SOUTH, Direction.WEST, 5, 3);
			bigScreen(b, c, screen, "lounge", "");
			for (int i = 0; i < screen.width(); i++) {
				c.set(screen.origin().relative(screen.along(), i).below(), column);
			}
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					tree(c, cx + 20 * sx, cz + 17 * sz, 6, EnsancheStyle.log(Blocks.OAK_LOG), EnsancheStyle.leaves(Blocks.AZALEA_LEAVES));
				}
			}
			c.decor(new BlockPos(cx - 20, 1, cz + 2), standingSign(Direction.WEST), text(DyeColor.ORANGE, "", "Mercado", "lounge", ""));
			int n = 0;
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					b.anchor(Anchor.at("walk_lounge_" + n++, new BlockPos(cx + 8 * sx, 1, cz + 5 * sz), sz > 0 ? Direction.NORTH : Direction.SOUTH));
				}
			}
		}
	}

	/** A small square on the diagonal promenade: lawns, plane trees, a bandstand. */
	private void plazuela(PlanBuilder b, int k) {
		int cx = EnsancheGrid.cx(k);
		int cz = EnsancheGrid.cz(k);
		Box box = new Box(cx - BLOCK_HALF, 0, cz - BLOCK_HALF, cx + BLOCK_HALF, 10, cz + BLOCK_HALF);
		BlockState log = EnsancheStyle.log(Blocks.OAK_LOG);
		BlockState leaves = EnsancheStyle.leaves(Blocks.OAK_LEAVES);
		b.group("plazuela_" + k);
		try (PlanBuilder.Canvas c = b.module("plazuela_" + k, box, List.of())) {
			for (int dx = -BLOCK_HALF; dx <= BLOCK_HALF; dx++) {
				for (int dz = -BLOCK_HALF; dz <= BLOCK_HALF; dz++) {
					int x = cx + dx;
					int z = cz + dz;
					int u = dx + BLOCK_HALF;
					int v = dz + BLOCK_HALF;
					if (chamfered(u, v)) {
						c.set(x, 0, z, SIDEWALK);
						continue;
					}
					int diag = u + v - (EnsancheGrid.BLOCK - 1);
					boolean kiosk = dx * dx + dz * dz <= 20;
					boolean paseo = Math.abs(diag) <= 1;
					boolean cross = Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
					boolean border = Math.abs(dx) >= 21 || Math.abs(dz) >= 21;
					c.set(x, 0, z, kiosk ? accent() : paseo ? PASEO : cross || border ? SIDEWALK : GRASS);
					if (!kiosk && !paseo && !cross && !border) {
						if (Math.abs(diag) == 5 && Math.floorMod(u, 6) == 3) {
							tree(c, x, z, 6, log, leaves);
						} else if (mix(x, z) % 7 == 0) {
							c.decor(x, 1, z, mix(z, x) % 3 == 0 ? s(Blocks.CORNFLOWER) : s(Blocks.SHORT_GRASS));
						}
					}
				}
			}
			// bandstand: columns, copper canopy, lanterns
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					if (dx * dx + dz * dz <= 20) {
						c.set(cx + dx, 5, cz + dz, dx * dx + dz * dz <= 2 ? s(Blocks.CUT_COPPER.waxed().unaffected())
								: EnsancheStyle.slab(Blocks.CUT_COPPER_SLAB.waxed().unaffected()));
					}
				}
			}
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					for (int y = 1; y <= 4; y++) {
						c.set(cx + 3 * sx, y, cz + 3 * sz, s(Blocks.QUARTZ_PILLAR));
					}
				}
			}
			c.hang(cx, 4, cz, hanging());
			c.decor(cx, 6, cz, lantern());
			for (int i = -1; i <= 1; i++) {
				c.set(cx + 7 + i, 1, cz + 7 - i, style.bench(Direction.SOUTH));
				c.set(cx - 7 + i, 1, cz - 7 - i, style.bench(Direction.NORTH));
			}
			b.anchor(Anchor.at("walk_plazuela_" + k, new BlockPos(cx, 1, cz + 1), Direction.SOUTH));
		}
	}

	/** A work manzana nobody uses yet (or any more): a fenced vacant block, waiting in the spiral. */
	private void vacantBlock(PlanBuilder b, int k) {
		int cx = EnsancheGrid.cx(k);
		int cz = EnsancheGrid.cz(k);
		Box box = new Box(cx - BLOCK_HALF, 0, cz - BLOCK_HALF, cx + BLOCK_HALF, 2, cz + BLOCK_HALF);
		BlockState hedge = EnsancheStyle.leaves(Blocks.AZALEA_LEAVES);
		b.group("solar_" + k);
		try (PlanBuilder.Canvas c = b.module("solar_" + k, box, List.of())) {
			for (int dx = -BLOCK_HALF; dx <= BLOCK_HALF; dx++) {
				for (int dz = -BLOCK_HALF; dz <= BLOCK_HALF; dz++) {
					int x = cx + dx;
					int z = cz + dz;
					if (chamfered(dx + BLOCK_HALF, dz + BLOCK_HALF)) {
						c.set(x, 0, z, SIDEWALK);
						continue;
					}
					c.set(x, 0, z, mix(x, z) % 4 == 0 ? s(Blocks.COARSE_DIRT) : GRASS);
					boolean edge = Math.abs(dx) == BLOCK_HALF || Math.abs(dz) == BLOCK_HALF;
					boolean gap = Math.abs(dx) <= 2 || Math.abs(dz) <= 2;
					if (edge && !gap) {
						c.set(x, 1, z, hedge);
					}
				}
			}
			c.decor(new BlockPos(cx + 4, 1, cz + BLOCK_HALF - 1), standingSign(Direction.SOUTH), text(DyeColor.GRAY, "", "Manzana", "libre", ""));
		}
	}

	// ------------------------------------------------------------------ work manzanas

	private void patio(PlanBuilder b, int k, EnsancheStyle.Mat mat) {
		HqFootprints.Area a = EnsancheGrid.patioArea(k);
		Box box = new Box(a.minX(), 0, a.minZ(), a.maxX(), 10, a.maxZ());
		int px = a.minX() + (a.maxX() - a.minX()) / 2;
		int pz = a.minZ() + (a.maxZ() - a.minZ()) / 2;
		b.group("patio_" + k);
		try (PlanBuilder.Canvas c = b.module("patio_" + k, box, List.of())) {
			for (int x = a.minX(); x <= a.maxX(); x++) {
				for (int z = a.minZ(); z <= a.maxZ(); z++) {
					int e = Math.min(Math.min(x - a.minX(), a.maxX() - x), Math.min(z - a.minZ(), a.maxZ() - z));
					boolean ring = e <= 1;
					boolean cross = Math.abs(x - px) <= 1 || Math.abs(z - pz) <= 1;
					boolean basin = Math.max(Math.abs(x - px), Math.abs(z - pz)) <= 2;
					c.set(x, 0, z, basin ? mat.trim() : ring || cross ? mat.plinth() : GRASS);
					if (!ring && !cross && !basin && mix(x, z) % 5 == 0) {
						int f = mix(z, x) % 4;
						c.decor(x, 1, z, f == 0 ? s(Blocks.POPPY) : f == 1 ? s(Blocks.DANDELION) : f == 2 ? s(Blocks.CORNFLOWER) : s(Blocks.SHORT_GRASS));
					}
				}
			}
			// fountain-well in the middle, garden lanterns at the path crossings
			for (int x = px - 2; x <= px + 2; x++) {
				for (int z = pz - 2; z <= pz + 2; z++) {
					if (Math.max(Math.abs(x - px), Math.abs(z - pz)) == 2 && (x == px || z == pz) == false) {
						c.set(x, 1, z, EnsancheStyle.slab(Blocks.SMOOTH_STONE_SLAB));
					}
				}
			}
			c.set(px, 1, pz, mat.trim());
			c.decor(px, 2, pz, kit.gardenLantern());
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					int tx = px + 5 * sx;
					int tz = pz + 6 * sz;
					// grass is cleared under the tree: the trunk owns its cell
					tree(c, tx, tz, 6, mat.log(), mat.leaves());
					c.set(px + 3 * sx, 1, pz + 3 * sz, kit.gardenPlanter());
				}
				c.set(px + 4 * sx, 1, pz - 1, style.bench(sx > 0 ? Direction.EAST : Direction.WEST));
			}
		}
	}

	/** Base storeys of a parcel's building (2 or 3; it rises when its project needs more floors). */
	static int baseStoreys(int parcel) {
		return 2 + mix(parcel, 7) % 2;
	}

	/** Whether a parcel's chamfered corner rises one storey above the building (a corner tower). */
	static boolean tower(int parcel) {
		return mix(parcel, 13) % 3 == 0;
	}

	/** Storeys of a building whose highest room is on {@code maxFloor}. */
	static int storeys(int parcel, int maxFloor) {
		return Math.max(baseStoreys(parcel), maxFloor + 1);
	}

	/** The two ground-floor doors from a room to its patio (two cells wide each), clear of the next building. */
	static boolean isPatioDoor(int a, int len) {
		int a0 = firstDesk(len);
		return a == a0 + 10 || a == a0 + 11 || a == a0 + 16 || a == a0 + 17;
	}

	/** First desk column of a room: six desks, four columns apart, ending next to the stair hall's wall. */
	static int firstDesk(int length) {
		return length - 28;
	}

	private void solar(PlanBuilder b, int k, int side, EnsancheStyle.Mat mat) {
		int p = EnsancheGrid.parcel(k, side);
		EnsancheGrid.Frame f = EnsancheGrid.frame(k, side);
		int len = EnsancheGrid.length(side);
		int dep = EnsancheGrid.depth(side);
		BlockState hedge = EnsancheStyle.leaves(Blocks.AZALEA_LEAVES);
		b.group("parcel_" + p);
		try (PlanBuilder.Canvas c = b.module("solar_parcel_" + p, f.box(0, 0, 0, len - 1, 8, dep - 1), List.of())) {
			for (int a = 0; a < len; a++) {
				for (int d = 0; d < dep; d++) {
					BlockPos g = f.pos(a, 0, d);
					if (a + d < CHAMFER) {
						c.set(g, SIDEWALK);
						continue;
					}
					c.set(g, mix(g.getX(), g.getZ()) % 4 == 0 ? s(Blocks.COARSE_DIRT) : GRASS);
					boolean gap = a >= len - 5 || (a >= 12 && a <= 14);
					if (d == 0 && !gap) {
						c.set(g.above(), hedge);
					}
				}
			}
			tree(c, f.pos(9, 0, dep / 2).getX(), f.pos(9, 0, dep / 2).getZ(), 6, mat.log(), mat.leaves());
			tree(c, f.pos(20, 0, dep / 2 + 1).getX(), f.pos(20, 0, dep / 2 + 1).getZ(), 5, mat.log(), mat.leaves());
			c.decor(f.pos(13, 1, 1), standingSign(f.street()), text(DyeColor.GRAY, "", "Parcela", "libre", ""));
		}
	}

	/**
	 * One building: rooms on {@code storeys} floors along the street wall (desks against it, windows to
	 * the patio), the stair hall over the covered passage at the inner end (flight 1 up to floor 1 along
	 * the end wall, flight 2 up to floor 2 along the room's wall), a chamfered corner that may rise as a
	 * tower, a pitched or flat roof.
	 */
	private void building(PlanBuilder b, HqLayout layout, int k, int side, TreeMap<Integer, HqLayout.Module> floors, EnsancheStyle.Mat mat) {
		int p = EnsancheGrid.parcel(k, side);
		EnsancheGrid.Frame f = EnsancheGrid.frame(k, side);
		int len = EnsancheGrid.length(side);
		int dep = EnsancheGrid.depth(side);
		int hs = storeys(p, floors.lastKey());
		int hc = Math.min(4, hs + (tower(p) ? 1 : 0));
		boolean pitched = mat.pitched();
		int ridge = (dep - 1) / 2;
		int top = Math.max(STOREY * hc + 1, pitched ? STOREY * hs + 1 + ridge : STOREY * hs + 1);
		BlockState wall = side % 2 == 0 ? mat.wall() : mat.wall2();
		BlockState trim = mat.trim();
		BlockState plinth = mat.plinth();
		int hallA = len - 4; // stair hall / passage: a = len-4 .. len-2; the room's wall at len-5; end wall at len-1
		int part = len - 5;
		int a0 = firstDesk(len);
		int blind = EnsancheGrid.depth((side + 1) % EnsancheGrid.SIDES); // patio facade columns facing the next building's end
		Direction along = f.along();
		Direction street = f.street();
		Direction in = f.in();
		b.group("parcel_" + p);
		try (PlanBuilder.Canvas c = b.module("parcel_" + p, f.box(0, 0, 0, len - 1, top, dep - 1), List.of())) {
			for (int a = 0; a < len; a++) {
				for (int d = 0; d < dep; d++) {
					if (a + d < CHAMFER) {
						c.set(f.pos(a, 0, d), SIDEWALK);
						continue;
					}
					int h = a < CORNER ? hc : hs;
					boolean outer = d == 0 || d == dep - 1 || a == 0 || a + d == CHAMFER || a == len - 1;
					boolean hall = a >= hallA && a <= len - 2;
					boolean cornerWall = a == CORNER - 1;
					for (int y = 0; y <= STOREY * h; y++) {
						int st = y / STOREY;
						int r = y % STOREY;
						BlockPos pos = f.pos(a, y, d);
						if (r == 0) {
							// slab: footing, floors, roof
							BlockState slab;
							if (y == 0) {
								slab = outer ? plinth : hall ? plinth : floorTile(mat);
							} else if (outer) {
								slab = trim;
							} else if (y == STOREY * h) {
								slab = Math.floorMod(a, 4) == 2 && d == dep / 2 && a < part && !hall ? lightPanel() : plinth;
							} else if (hall) {
								slab = a == hallA + 1 && d == dep - 3 ? lightPanel() : plinth;
							} else {
								slab = Math.floorMod(a, 4) == 2 && d == dep / 2 && a < part ? lightPanel() : floorTile(mat);
							}
							c.set(pos, slab);
							continue;
						}
						boolean extra = st >= hs; // the corner tower's own storey
						if (outer || (extra && cornerWall)) {
							c.set(pos, facade(a, d, st, r, len, dep, blind, wall, trim, plinth, mat, extra));
						} else if (a == part && !extra) {
							boolean door = d >= 2 && d <= 3 && r <= 2;
							c.set(pos, door ? AIR : r == STOREY - 1 ? trim : partition(mat));
						}
					}
				}
			}
			stairs(c, f, len, dep, hs);
			roof(c, f, len, dep, hs, hc, pitched, ridge, wall, mat);
			// rooms
			List<HqLayout.Module> mine = layout.modulesOf(floors.firstEntry().getValue().projectKey());
			for (int fl = 0; fl < hs; fl++) {
				int y0 = STOREY * fl;
				HqLayout.Module m = floors.get(fl);
				decorateRoom(c, f, len, dep, y0, a0, mat);
				if (m == null) {
					continue;
				}
				room(b, c, f, len, dep, y0, a0, m, mine.indexOf(m), mine.get(0) == m);
			}
			// sign of the building on the street side of its passage
			HqLayout.Module first = floors.firstEntry().getValue();
			c.decor(f.pos(hallA, 3, 1), wallSign(along), text(HqStyle.envColor(first), cut(first.label(), 0), cut(first.label(), 1),
					mine.indexOf(first) > 0 ? "#" + (mine.indexOf(first) + 1) : "", first.envLabel()));
		}
	}

	private static String cut(String label, int line) {
		if (line == 0) {
			return label.length() <= SignTags.LINE ? label : label.substring(0, SignTags.LINE);
		}
		return label.length() <= SignTags.LINE ? "" : label.substring(SignTags.LINE, Math.min(label.length(), 2 * SignTags.LINE));
	}

	/** A wall cell of a building's shell. */
	private BlockState facade(int a, int d, int st, int r, int len, int dep, int blindPatio, BlockState wall, BlockState trim, BlockState plinth,
			EnsancheStyle.Mat mat, boolean extra) {
		boolean passage = a >= len - 4 && a <= len - 2 && st == 0 && (d == 0 || d == dep - 1);
		if (passage) {
			return r <= 4 ? AIR : trim;
		}
		if (a == len - 1) {
			return r == STOREY - 1 ? trim : wall; // end wall against the next building
		}
		boolean blind = d == dep - 1 && a < blindPatio;
		if (d == dep - 1 && !extra && !blind && st == 0 && isPatioDoor(a, len)) {
			return r <= 3 ? AIR : trim;
		}
		boolean pilaster = (d == 0 || d == dep - 1) && Math.floorMod(a, 8) == 0 || a + d == CHAMFER && (a == 0 || d == 0) || a == 0 && (d == 0 || d == dep - 1);
		if (pilaster) {
			return trim;
		}
		if (r == STOREY - 1) {
			return st == 0 ? trim : wall;
		}
		if (st == 0 && r == 1) {
			return plinth;
		}
		if (d == dep - 1 && !extra) {
			// patio facade: tall windows (the stretch against the next building's end wall is blind)
			return !blind && r >= 2 && r <= 4 && Math.floorMod(a, 3) != 0 ? pane() : wall;
		}
		if (d == 0 && !extra) {
			// street facade: the desks' screens stand against it, a window band above them
			return r == 4 && Math.floorMod(a, 4) != 0 ? pane() : wall;
		}
		// gable end, chamfer and the tower's storey
		return r >= 2 && r <= 4 && Math.floorMod(a + d, 2) == 1 ? pane() : wall;
	}

	/** Flight 1 (ground to floor 1) along the end wall, flight 2 (floor 1 to 2) along the room's wall, and their stairwells. */
	private void stairs(PlanBuilder.Canvas c, EnsancheGrid.Frame f, int len, int dep, int hs) {
		BlockState up = stair(f.in());
		for (int k = 1; k <= STOREY; k++) {
			c.set(f.pos(len - 2, k, k), up);
		}
		for (int d = 3; d <= 5; d++) {
			c.set(f.pos(len - 2, STOREY, d), AIR);
		}
		if (hs >= 3) {
			BlockState back = stair(f.street());
			for (int k = 1; k <= STOREY; k++) {
				c.set(f.pos(len - 4, STOREY + k, 9 - k), back);
			}
			for (int d = 4; d <= 6; d++) {
				c.set(f.pos(len - 4, 2 * STOREY, d), AIR);
			}
		}
	}

	private BlockState stair(Direction up) {
		return EnsancheStyle.stairs(Blocks.STONE_BRICK_STAIRS, up);
	}

	private void roof(PlanBuilder.Canvas c, EnsancheGrid.Frame f, int len, int dep, int hs, int hc, boolean pitched, int ridge, BlockState wall,
			EnsancheStyle.Mat mat) {
		// corner segment: flat, with a parapet
		int yc = STOREY * hc + 1;
		for (int a = 0; a < CORNER; a++) {
			for (int d = 0; d < dep; d++) {
				if (a + d < CHAMFER) {
					continue;
				}
				boolean edge = d == 0 || d == dep - 1 || a == 0 || a + d == CHAMFER || (a == CORNER - 1 && (hc > hs || pitched));
				if (edge) {
					c.set(f.pos(a, yc, d), mat.roofSlab());
				}
			}
		}
		int ys = STOREY * hs + 1;
		if (!pitched) {
			for (int a = CORNER; a < len; a++) {
				for (int d = 0; d < dep; d++) {
					if (d == 0 || d == dep - 1 || a == len - 1 || (a == CORNER && hc > hs)) {
						if (!(a == CORNER && hc > hs && d > 0 && d < dep - 1)) {
							c.set(f.pos(a, ys, d), mat.roofSlab());
						}
					}
				}
			}
			// a roof terrace: planters
			for (int a = CORNER + 3; a < len - 3; a += 6) {
				c.set(f.pos(a, ys, dep / 2), kit.gardenPlanter());
			}
			return;
		}
		for (int a = CORNER; a < len; a++) {
			for (int i = 0; i < ridge; i++) {
				c.set(f.pos(a, ys + i, i), mat.roofStairs(f.in()));
				c.set(f.pos(a, ys + i, dep - 1 - i), mat.roofStairs(f.street()));
			}
			c.set(f.pos(a, ys + ridge, ridge), mat.roofFull());
			if (a == CORNER || a == len - 1) {
				for (int d = 1; d < dep - 1; d++) {
					int yTop = ys + Math.min(d, dep - 1 - d);
					for (int y = ys; y < yTop; y++) {
						c.set(f.pos(a, y, d), wall);
					}
				}
			}
		}
	}

	/** Lights and plants of a room (every floor, used or not). */
	private void decorateRoom(PlanBuilder.Canvas c, EnsancheGrid.Frame f, int len, int dep, int y0, int a0, EnsancheStyle.Mat mat) {
		c.set(f.pos(a0 + 2, y0 + 1, dep - 2), kit.gardenPlanter());
		c.set(f.pos(len - 7, y0 + 1, dep - 2), kit.gardenPlanter());
		if (dep >= 13) {
			// deep rooms: a long meeting table across the middle
			BlockState table = topSlab(Blocks.DARK_OAK_SLAB);
			for (int a = a0 + 5; a <= a0 + 17; a++) {
				c.set(f.pos(a, y0 + 1, 7), table);
			}
			c.decor(f.pos(a0 + 11, y0 + 2, 7), kit.lantern());
		}
	}

	/** One room (HQ module): desks with their 3×2 screens, the room's big screen, anchors, workstations. */
	private void room(PlanBuilder b, PlanBuilder.Canvas c, EnsancheGrid.Frame f, int len, int dep, int y0, int a0, HqLayout.Module m, int nth,
			boolean first) {
		int i = m.index();
		Direction facing = f.street();
		Direction front = facing.getOpposite();
		Direction right = facing.getClockWise();
		for (int slot = 0; slot < HqLayout.DESKS; slot++) {
			String thread = m.threadAt(slot);
			if (thread == null) {
				continue;
			}
			int da = a0 + 4 * slot + 1;
			BlockPos mid = f.pos(da, y0 + 1, 1);
			BlockPos stand = f.pos(da, y0 + 1, 2);
			BlockPos computer = mid.above();
			c.set(mid, desk(front));
			c.set(mid.relative(right), desk(front));
			c.set(mid.relative(right.getOpposite()), desk(front));
			BlockPos origin = Workstation.screenOriginFor(computer, front);
			for (BlockPos q : Workstation.screenCells(origin, front.getCounterClockWise())) {
				c.set(q, style.computer(true));
			}
			c.set(stand, AIR);
			c.set(stand.above(), AIR);
			Anchor deskA = Anchor.at(AnchorNames.deskSlot(i, slot), stand, facing);
			Anchor screen = Anchor.face(AnchorNames.screenSlot(i, slot), computer, front);
			b.workstation(new Workstation(i, slot, thread, deskA, computer, screen, origin));
			b.anchor(deskA.renamed(AnchorNames.desk(thread)));
			b.anchor(screen.renamed(AnchorNames.monitor(thread)));
		}
		// the room's big screen on the stair hall's wall, facing into the room
		int part = len - 5;
		Direction sf = f.along().getOpposite();
		Direction sAlong = sf.getCounterClockWise();
		int d0 = sAlong == f.in() ? 5 : 7;
		HqGeometry.Surface s = new HqGeometry.Surface(f.pos(part, y0 + 2, d0), sAlong, sf, 3, 2);
		bigScreen(b, c, s, "module@" + i, "project:" + m.projectKey());
		for (int k = 0; k < 3; k++) {
			c.set(s.origin().relative(sAlong, k).below(), s(Blocks.POLISHED_DEEPSLATE));
		}
		// a plaque by the door, on the stair hall's side
		if (y0 > 0) {
			c.decor(f.pos(len - 4, y0 + 3, 1), wallSign(f.along()), text(HqStyle.envColor(m), cut(m.label(), 0), cut(m.label(), 1),
					nth > 0 ? "#" + (nth + 1) : "", m.envLabel()));
		}
		Anchor door = Anchor.at(AnchorNames.module(i), f.pos(len - 6, y0 + 1, 4), f.along().getOpposite());
		Anchor walk = Anchor.at(AnchorNames.walkModule(i), f.pos((a0 + len - 6) / 2, y0 + 1, dep - 3), facing);
		b.anchor(door);
		b.anchor(walk);
		if (first) {
			b.anchor(door.renamed(AnchorNames.wing(m.projectKey())));
			b.anchor(walk.renamed(AnchorNames.walk(m.projectKey())));
		}
	}
}
