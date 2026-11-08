package dev.t3craft.hq;

import static dev.t3craft.hq.EnsancheGrid.BLOCK_HALF;
import static dev.t3craft.hq.EnsancheGrid.CELL_HALF;
import static dev.t3craft.hq.EnsancheGrid.CHAMFER;
import static dev.t3craft.hq.EnsancheGrid.STOREY;
import static dev.t3craft.hq.TowncenterGrid.BRIDGE_DOOR;
import static dev.t3craft.hq.TowncenterGrid.LANDING;
import static dev.t3craft.hq.TowncenterGrid.TOWER_LEN;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The town-centre HQ as a {@link BuildPlan} (layout {@code hybrid-towncenter}). Pure and
 * deterministic like {@link HqGenerator}; ground plan from {@link EnsancheGrid} /
 * {@link TowncenterGrid}, rooms from a {@link TowncenterAllocator} layout. Builds, in order:
 * <ol>
 *   <li>the plaza mayor: paving, fountain, a garden on the diagonal promenade; the civic hall (the
 *   hub: a cross-shaped nave, 33 × 33, 14 high, with a glazed crossing tower and lantern over the
 *   hologram, the beacon on top, the kanban wall and the big screen at the ends of the transept);
 *   the library (archive terminal) on the north-west corner, the signal box (the needs-attention
 *   board) on the south-west corner and the clock tower (scheduled tasks) on the south-east corner;</li>
 *   <li>for every cell {@code 0..maxCell}: its half streets, then the covered market (lounge), a
 *   small square, a vacant manzana or a work manzana (garden patio and four parcels: a podium
 *   building, maybe with its corner tower, or a vacant lot);</li>
 *   <li>the skybridges between facing towers, or the windows that close their doors.</li>
 * </ol>
 * Every module is lit before it is closed ({@link TowncenterLight}): ceiling panels, floor inlays
 * and lit curbs, never a free-standing lantern.
 *
 * <p>Groups: {@code plaza}, {@code hub}, {@code archive}, {@code clock}, {@code signal},
 * {@code streets_<k>}, {@code lounge}, {@code plazuela_<k>}, {@code solar_<k>}, {@code patio_<k>},
 * {@code parcel_<p>} (the whole building: podium and tower), {@code bridge_<p>_<q>} and
 * {@code plug_<p>_d|a}. A new tower next door only swaps a plug for a bridge: the buildings' own
 * groups do not change.
 */
final class TowncenterBuilder {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	/** Columns of a podium that belong to its corner segment (flat roof). */
	static final int CORNER = 8;

	private final TowncenterStyle style;
	private final HqKit kit = HqKit.active();
	private final boolean custom = kit.custom();
	private final Set<Block> keep = new HashSet<>();

	TowncenterBuilder(TowncenterStyle style) {
		this.style = style;
		for (HqStyle.Role r : new HqStyle.Role[] {HqStyle.Role.SCREEN, HqStyle.Role.COMPUTER_ON, HqStyle.Role.COMPUTER_OFF, HqStyle.Role.BEACON,
				HqStyle.Role.STATUS_LAMP, HqStyle.Role.TERMINAL, HqStyle.Role.BOARD}) {
			keep.add(style.palette().get(r).getBlock());
		}
		keep.add(Blocks.BOOKSHELF);
		keep.add(Blocks.CHISELED_BOOKSHELF);
	}

	BuildPlan generate(HqLayout layout) {
		PlanBuilder b = new PlanBuilder();
		int maxCell = TowncenterGrid.maxCell(layout);
		TreeMap<Integer, TreeMap<Integer, HqLayout.Module>> parcels = new TreeMap<>();
		for (HqLayout.Module m : layout.modules()) {
			parcels.computeIfAbsent(TowncenterGrid.parcelOfModule(m.index()), q -> new TreeMap<>()).put(TowncenterGrid.floorOfModule(m.index()), m);
		}
		plaza(b, layout);
		for (int k = 0; k <= maxCell; k++) {
			streets(b, k);
		}
		TreeMap<Integer, Integer> towers = new TreeMap<>();
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
						int p = EnsancheGrid.parcel(k, side);
						TreeMap<Integer, HqLayout.Module> floors = parcels.get(p);
						if (floors == null) {
							solar(b, k, side, mat);
						} else {
							int tf = TowncenterGrid.towerFloors(p, floors.lastKey());
							if (tf > 0) {
								towers.put(p, tf);
							}
							building(b, layout, k, side, floors, mat, tf);
						}
					}
				}
				default -> throw new IllegalStateException("cell " + k);
			}
		}
		bridges(b, towers);
		b.group(null);
		return b.build();
	}

	// ------------------------------------------------------------------ materials

	private static BlockState s(Block b) {
		return b.defaultBlockState();
	}

	private static final BlockState ROAD = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
	private static final BlockState LINE = Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
	private static final BlockState SIDEWALK = s(Blocks.SMOOTH_STONE);
	private static final BlockState CURB = s(Blocks.STONE_BRICKS);
	private static final BlockState PASEO = s(Blocks.POLISHED_GRANITE);
	private static final BlockState GRASS = s(Blocks.GRASS_BLOCK);
	/** Flush lamp of interiors (ceiling panels, floor inlays, the light pass). */
	static final BlockState LAMP = s(Blocks.PEARLESCENT_FROGLIGHT);
	/** Flush lamp of the street (lit curbs, the patio well). */
	static final BlockState LAMP_OUT = s(Blocks.OCHRE_FROGLIGHT);
	private static final BlockState PLINTH = s(Blocks.POLISHED_DEEPSLATE);
	private static final BlockState TRIM = s(Blocks.SMOOTH_QUARTZ);
	private static final BlockState PILLAR = s(Blocks.QUARTZ_PILLAR);
	private static final BlockState CIVIC = s(Blocks.CALCITE);
	private static final BlockState STONE = s(Blocks.STONE_BRICKS);
	private static final BlockState COPPER = s(Blocks.CUT_COPPER.waxed().unaffected());
	private static final BlockState COPPER_SLAB = EnsancheStyle.slab(Blocks.CUT_COPPER_SLAB.waxed().unaffected());

	private BlockState floorTile(EnsancheStyle.Mat mat) {
		return custom ? kit.floor() : mat.floor();
	}

	private BlockState accent() {
		return custom ? kit.floorAccent() : s(Blocks.POLISHED_ANDESITE);
	}

	private BlockState partition(EnsancheStyle.Mat mat) {
		return custom ? kit.wallPanel(false) : mat.wall2();
	}

	private BlockState desk(Direction front) {
		return custom ? kit.desk(front) : style.palette().get(HqStyle.Role.DESK);
	}

	private static BlockState topSlab(Block b) {
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

	private static BlockState stair(Block b, Direction up) {
		return EnsancheStyle.stairs(b, up);
	}

	/** Small deterministic hash of a cell (decoration only). */
	private static int mix(int a, int b) {
		int h = a * 0x9E3779B1 ^ b * 0x85EBCA77;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		return (h ^ (h >>> 13)) & 0x7fffffff;
	}

	/** The integrated light pass, last thing before a module closes. */
	private void lit(PlanBuilder.Canvas c) {
		TowncenterLight.fill(c, LAMP, st -> keep.contains(st.getBlock()));
	}

	/** Writes a cell only when the canvas owns it (towers have holes for the skybridge doors). */
	private static void put(PlanBuilder.Canvas c, BlockPos p, BlockState st) {
		if (c.owns(p.getX(), p.getY(), p.getZ())) {
			c.set(p, st);
		}
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

	/**
	 * One standing desk: the agent at {@code stand} looking {@code facing} at a three-cell desk with
	 * its 3×2 screen on the two rows above (no chair, no lamp).
	 */
	private void deskAt(PlanBuilder b, PlanBuilder.Canvas c, int module, int slot, String thread, BlockPos stand, Direction facing) {
		Direction front = facing.getOpposite();
		Direction right = facing.getClockWise();
		BlockPos mid = stand.relative(facing);
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
		Anchor deskA = Anchor.at(AnchorNames.deskSlot(module, slot), stand, facing);
		Anchor screen = Anchor.face(AnchorNames.screenSlot(module, slot), computer, front);
		b.workstation(new Workstation(module, slot, thread, deskA, computer, screen, origin));
		b.anchor(deskA.renamed(AnchorNames.desk(thread)));
		b.anchor(screen.renamed(AnchorNames.monitor(thread)));
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
				lit(c);
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
				c.set(x, 0, z, LAMP_OUT); // a lit curb, flush with the sidewalk
			}
			return;
		}
		boolean zebra = Math.abs(t) >= 18 && Math.abs(t) <= 21 && Math.floorMod(t, 2) == 0;
		boolean line = e == EnsancheGrid.HALF_STREET && Math.floorMod(t, 4) < 2 && Math.abs(t) < 17;
		c.set(x, 0, z, zebra || line ? LINE : ROAD);
	}

	// ------------------------------------------------------------------ plaza mayor

	/** Centre z of the crossing (hologram column, lantern, beacon). */
	static final int HC = -4;
	/** Nave (north-south): |x| <= NS_X, z NS_N..NS_S. */
	static final int NS_X = 6;
	static final int NS_N = -20;
	static final int NS_S = 12;
	/** Transept (east-west): |x| <= EW_X, z EW_N..EW_S. */
	static final int EW_X = 16;
	static final int EW_N = HC - 6;
	static final int EW_S = HC + 6;
	/** Half size of the crossing square. */
	static final int CROSS = 6;
	static final int WALL_TOP = 14;
	static final int CEIL = 15;
	static final int ROOF_T = 25;
	static final int CAP = 29;
	static final int HUB_TOP = 31;
	static final int HOLOGRAM_TOP = 8;
	static final BlockPos SPAWN = new BlockPos(0, 1, BLOCK_HALF + 3);
	static final int FOUNTAIN_Z = 17;

	static Box hubNave() {
		return new Box(-NS_X, 0, NS_N, NS_X, HUB_TOP, NS_S);
	}

	static Box hubEast() {
		return new Box(NS_X + 1, 0, EW_N, EW_X, CEIL + 1, EW_S);
	}

	static Box hubWest() {
		return new Box(-EW_X, 0, EW_N, -NS_X - 1, CEIL + 1, EW_S);
	}

	static Box clockBox() {
		return new Box(9, 0, 8, BLOCK_HALF, TowncenterGrid.TOP, BLOCK_HALF);
	}

	static Box signalBox() {
		return new Box(-BLOCK_HALF, 0, 8, -9, 20, BLOCK_HALF);
	}

	static Box libraryBox() {
		return new Box(-BLOCK_HALF, 0, -BLOCK_HALF, -9, 16, -12);
	}

	static HqGeometry.Surface threadWall() {
		return new HqGeometry.Surface(new BlockPos(-EW_X + 1, 1, HC + 4), Direction.NORTH, Direction.EAST, 9, 4);
	}

	static HqGeometry.Surface hubScreen() {
		return new HqGeometry.Surface(new BlockPos(EW_X - 1, 2, HC - 2), Direction.SOUTH, Direction.WEST, 5, 3);
	}

	private void plaza(PlanBuilder b, HqLayout layout) {
		Box block = new Box(-BLOCK_HALF, 0, -BLOCK_HALF, BLOCK_HALF, TowncenterGrid.TOP, BLOCK_HALF);
		b.group("plaza");
		try (PlanBuilder.Canvas c = b.module("plaza", block, List.of(hubNave(), hubEast(), hubWest(), clockBox(), signalBox(), libraryBox()))) {
			plazaGround(c);
			lit(c);
		}
		b.group("hub");
		hall(b, HqGenerator.hubBinding(layout));
		b.group(HqGenerator.GROUP_ARCHIVE);
		library(b);
		b.group("clock");
		clockTower(b);
		b.group("signal");
		signalBox(b);
		b.anchor(Anchor.at(AnchorNames.SPAWN, SPAWN, Direction.NORTH));
	}

	/** Plaza paving: the garden on the diagonal promenade (north-east), sidewalks on the edges, a checker elsewhere. */
	private static BlockState plazaPave(int x, int z) {
		if (chamfered(x + BLOCK_HALF, z + BLOCK_HALF)) {
			return SIDEWALK;
		}
		if (x >= 8 && z <= -12) {
			if (Math.abs(x + z) <= 1) {
				return PASEO;
			}
			return x >= 20 || z <= -20 ? SIDEWALK : GRASS;
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
					c.set(x, 0, z, plazaPave(x, z));
				}
			}
		}
		// fountain in the forecourt, in front of the civic hall's south door
		int fz = FOUNTAIN_Z;
		for (int x = -2; x <= 2; x++) {
			for (int z = fz - 2; z <= fz + 2; z++) {
				c.set(x, 0, z, s(Blocks.DARK_PRISMARINE));
				if (Math.abs(x) == 2 || Math.abs(z - fz) == 2) {
					c.set(x, 1, z, EnsancheStyle.slab(Blocks.POLISHED_DEEPSLATE_SLAB));
				}
			}
		}
		c.set(0, 1, fz, s(Blocks.PRISMARINE_BRICKS));
		c.set(0, 2, fz, s(Blocks.PRISMARINE_BRICKS));
		c.set(0, 3, fz, s(Blocks.SEA_LANTERN));
		for (int i = -1; i <= 1; i++) {
			c.set(5, 1, fz + i, style.bench(Direction.EAST));
			c.set(-5, 1, fz + i, style.bench(Direction.WEST));
		}
		// the garden of the diagonal promenade (north-east corner): plane trees and benches
		BlockState log = EnsancheStyle.log(Blocks.OAK_LOG);
		BlockState leaves = EnsancheStyle.leaves(Blocks.OAK_LEAVES);
		tree(c, 13, -16, 6, log, leaves);
		tree(c, 19, -15, 6, log, leaves);
		tree(c, 12, -19, 7, log, leaves);
		for (int x = 15; x <= 17; x++) {
			c.set(x, 1, -12, style.bench(Direction.SOUTH));
		}
		// trees on the east and west forecourts, in front of the transept's ends
		BlockState cherryLog = EnsancheStyle.log(Blocks.CHERRY_LOG);
		BlockState cherry = EnsancheStyle.leaves(Blocks.CHERRY_LEAVES);
		tree(c, 19, -7, 6, cherryLog, cherry);
		tree(c, 19, -1, 6, cherryLog, cherry);
		tree(c, -19, -7, 6, cherryLog, cherry);
		tree(c, -19, -1, 6, cherryLog, cherry);
		c.decor(new BlockPos(0, 1, 21), standingSign(Direction.SOUTH), text(DyeColor.ORANGE, "", "Plaza", "mayor", ""));
	}

	// ------------------------------------------------------------------ the civic hall (hub)

	static boolean inHub(int x, int z) {
		return (Math.abs(x) <= NS_X && z >= NS_N && z <= NS_S) || (Math.abs(x) <= EW_X && z >= EW_N && z <= EW_S);
	}

	static boolean hubWall(int x, int z) {
		return inHub(x, z) && (!inHub(x + 1, z) || !inHub(x - 1, z) || !inHub(x, z + 1) || !inHub(x, z - 1));
	}

	/** Doors: north and south of the nave (3 wide), north and south of each transept arm (2 wide). */
	static int hubDoor(int x, int z) {
		int ax = Math.abs(x);
		if (ax <= 1 && (z == NS_S || z == NS_N)) {
			return 4;
		}
		if ((ax == 11 || ax == 12) && (z == EW_S || z == EW_N)) {
			return 3;
		}
		return 0;
	}

	/** The block of one cell of the civic hall (null: air), the same for its three modules. */
	private BlockState hubCell(int x, int y, int z) {
		if (!inHub(x, z)) {
			return null;
		}
		boolean wall = hubWall(x, z);
		int ax = Math.abs(x);
		int az = Math.abs(z - HC);
		boolean sq = ax <= CROSS && az <= CROSS;
		if (y == 0) {
			if (wall) {
				return PLINTH;
			}
			if (Math.max(ax, az) <= 3) {
				return accent();
			}
			if ((ax == 2 && Math.floorMod(z, 4) == 0 && !sq) || (az == 2 && Math.floorMod(x, 4) == 0 && ax > CROSS)) {
				return LAMP; // floor inlays along the aisles
			}
			return Math.floorMod(x + z, 2) == 0 ? s(Blocks.POLISHED_ANDESITE) : s(Blocks.SMOOTH_STONE);
		}
		if (y <= WALL_TOP) {
			if (!wall) {
				if (sq && (ax == CROSS || az == CROSS) && y >= 13) {
					return TRIM; // the crossing's arches
				}
				if (ax == 4 && (z == -17 || z == -13 || z == 5 || z == 9)) {
					return PILLAR; // nave columns
				}
				return null;
			}
			int door = hubDoor(x, z);
			if (door > 0 && y <= door) {
				return null;
			}
			boolean nsFace = !inHub(x, z + 1) || !inHub(x, z - 1);
			boolean ewFace = !inHub(x + 1, z) || !inHub(x - 1, z);
			boolean pil = (nsFace && ewFace) || (nsFace && Math.floorMod(x, 4) == 0) || (ewFace && Math.floorMod(z, 4) == 0);
			boolean screened = (x == -EW_X && az <= 5) || (x == EW_X && az <= 3);
			if (pil) {
				return PILLAR;
			}
			if (y == 1) {
				return PLINTH;
			}
			if (y == 6 || y == WALL_TOP) {
				return TRIM;
			}
			if (screened && y <= 6) {
				return CIVIC;
			}
			return y >= 3 && y <= 12 ? pane() : CIVIC;
		}
		if (sq) {
			boolean ring = ax == CROSS || az == CROSS;
			int m = Math.max(ax, az);
			if (y < ROOF_T) {
				if (!ring) {
					return null; // the crossing is open from the floor to the lantern
				}
				if ((ax == CROSS && az == CROSS) || (ax == CROSS ? Math.floorMod(z - HC, 3) == 0 : Math.floorMod(x, 3) == 0)) {
					return PILLAR;
				}
				if (y == CEIL || y == CEIL + 1 || y == ROOF_T - 1) {
					return TRIM;
				}
				return y >= 17 && y <= 22 ? pane() : CIVIC;
			}
			if (y == ROOF_T) {
				return m <= 1 ? null : m == 2 ? TRIM : COPPER;
			}
			if (y < CAP) {
				if (m == 2) {
					return ax == 2 && az == 2 ? PILLAR : pane();
				}
				return y == ROOF_T + 1 && m == CROSS ? COPPER_SLAB : null;
			}
			return y == CAP && m <= 2 ? COPPER : null;
		}
		if (y == CEIL) {
			return !wall && Math.floorMod(x, 6) == 0 && Math.floorMod(z - HC, 6) == 3 ? LAMP : TRIM;
		}
		if (y == CEIL + 1 && wall) {
			return EnsancheStyle.slab(Blocks.SMOOTH_QUARTZ_SLAB);
		}
		return null;
	}

	private void hall(PlanBuilder b, String binding) {
		Box[] boxes = {hubNave(), hubEast(), hubWest()};
		String[] ids = {"hub", "hub_e", "hub_w"};
		for (int m = 0; m < boxes.length; m++) {
			Box box = boxes[m];
			try (PlanBuilder.Canvas c = b.module(ids[m], box, List.of())) {
				for (int y = box.minY(); y <= box.maxY(); y++) {
					for (int z = box.minZ(); z <= box.maxZ(); z++) {
						for (int x = box.minX(); x <= box.maxX(); x++) {
							BlockState st = hubCell(x, y, z);
							if (st != null) {
								c.set(x, y, z, st);
							}
						}
					}
				}
				if (m == 0) {
					BlockPos beacon = new BlockPos(0, CAP, HC);
					c.set(beacon, style.beacon());
					c.set(beacon.above(), Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
					for (int y = 1; y <= HOLOGRAM_TOP; y++) {
						c.set(0, y, HC, AIR);
					}
					c.decor(new BlockPos(0, 6, NS_S - 1), wallSign(Direction.NORTH), text(DyeColor.ORANGE, "", "Casa", "consistorial", ""));
					int walk = 0;
					for (int wz : new int[] {-15, 7}) {
						for (int sx = -1; sx <= 1; sx += 2) {
							b.anchor(Anchor.at(AnchorNames.walkHub(walk++), new BlockPos(3 * sx, 1, wz), Direction.SOUTH));
						}
					}
					b.anchor(Anchor.at(AnchorNames.HUB, new BlockPos(0, 1, HC), Direction.SOUTH));
					b.anchor(new Anchor(AnchorNames.HOLOGRAM, 0.5, 5.0, HC + 0.5, Direction.SOUTH, new BlockPos(0, 5, HC)));
					b.anchor(Anchor.at(AnchorNames.BEACON, beacon, Direction.SOUTH));
					b.anchor(Anchor.at(AnchorNames.ENTRANCE, new BlockPos(0, 1, NS_S), Direction.SOUTH));
				} else {
					int sx = m == 1 ? 1 : -1;
					Direction look = sx > 0 ? Direction.EAST : Direction.WEST;
					int bench = m == 1 ? 0 : 6;
					for (int dz : new int[] {-4, -3, -2, 2, 3, 4}) {
						BlockPos p = new BlockPos(10 * sx, 1, HC + dz);
						c.set(p, style.bench(look.getOpposite()));
						b.anchor(Anchor.at(AnchorNames.bench(bench++), p.relative(look), look));
					}
					if (m == 1) {
						HqGeometry.Surface hub = hubScreen();
						bigScreen(b, c, hub, "hub", binding);
						for (int i = 0; i < hub.width(); i++) {
							c.set(hub.origin().relative(hub.along(), i).below(), TRIM);
						}
					} else {
						HqGeometry.Surface wall = threadWall();
						for (BlockPos p : wall.cells()) {
							c.set(p, style.screen());
						}
						b.anchor(Anchor.face(AnchorNames.THREAD_WALL, wall.origin(), wall.facing()));
						b.anchor(Anchor.face(AnchorNames.THREAD_WALL_END, wall.origin().relative(wall.along(), wall.width() - 1).above(wall.height() - 1),
								wall.facing()));
						b.anchor(Anchor.at(AnchorNames.THREAD_WALL_FRONT, wall.anchorCell().relative(wall.facing(), 3).atY(1), wall.facing().getOpposite()));
					}
				}
				lit(c);
			}
		}
	}

	// ------------------------------------------------------------------ singular buildings of the plaza

	/** The library on the plaza's north-west corner: reading room, shelves on the walls, the archive terminal. */
	private void library(PlanBuilder b) {
		Box box = libraryBox();
		int x0 = -21;
		int x1 = -10;
		int z0 = -21;
		int z1 = -13;
		BlockState wall = s(Blocks.SMOOTH_SANDSTONE);
		BlockState trim = s(Blocks.CUT_SANDSTONE);
		BlockState post = EnsancheStyle.log(Blocks.STRIPPED_DARK_OAK_LOG);
		BlockState floor = custom ? kit.floor() : s(Blocks.DARK_OAK_PLANKS);
		try (PlanBuilder.Canvas c = b.module("archive", box, List.of())) {
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					boolean body = x >= x0 && x <= x1 && z >= z0 && z <= z1;
					if (!body) {
						c.set(x, 0, z, plazaPave(x, z));
						continue;
					}
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					c.set(x, 0, z, edge ? PLINTH : floor);
					c.set(x, 10, z, !edge && Math.floorMod(x, 4) == 1 && Math.floorMod(z, 3) == 0 ? LAMP : trim);
					if (!edge) {
						continue;
					}
					boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
					boolean door = (z == z0 && (x == -16 || x == -15)) || (x == x1 && (z == -17 || z == -16));
					boolean pil = corner || ((z == z0 || z == z1) ? Math.floorMod(x, 3) == 0 : Math.floorMod(z, 3) == 0);
					for (int y = 1; y <= 9; y++) {
						BlockState st = door && y <= 3 ? AIR : pil ? post : y == 1 ? PLINTH : y == 5 || y == 9 ? trim : y >= 6 ? pane() : wall;
						c.set(x, y, z, st);
					}
				}
			}
			// gable roof along x, ridge over z = -17
			BlockState roof = s(Blocks.DEEPSLATE_TILES);
			for (int l = 0; l <= 5; l++) {
				int y = 11 + l;
				int zs = box.minZ() + l;
				int zn = box.maxZ() - l;
				for (int x = box.minX(); x <= box.maxX(); x++) {
					if (zs == zn) {
						c.set(x, y, zs, roof);
						continue;
					}
					c.set(x, y, zs, stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.SOUTH));
					c.set(x, y, zn, stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.NORTH));
				}
				for (int x : new int[] {x0, x1}) {
					for (int z = zs + 1; z < zn; z++) {
						c.set(x, y, z, wall);
					}
				}
			}
			// shelves against the walls (never in the way of the doors)
			for (int y = 1; y <= 3; y++) {
				for (int z = -19; z <= -15; z++) {
					c.set(-20, y, z, Math.floorMod(z, 2) == 0 ? s(Blocks.CHISELED_BOOKSHELF) : s(Blocks.BOOKSHELF));
				}
				for (int x : new int[] {-19, -18, -13, -12}) {
					c.set(x, y, -20, s(Blocks.BOOKSHELF));
				}
				for (int x : new int[] {-19, -18, -17, -13, -12, -11}) {
					c.set(x, y, -14, s(Blocks.BOOKSHELF));
				}
				for (int z : new int[] {-20, -19}) {
					c.set(-11, y, z, s(Blocks.BOOKSHELF));
				}
				c.set(-16, y, -14, s(Blocks.CHISELED_BOOKSHELF));
				c.set(-14, y, -14, s(Blocks.CHISELED_BOOKSHELF));
			}
			c.set(-15, 2, -14, s(Blocks.CHISELED_BOOKSHELF));
			c.set(-15, 3, -14, s(Blocks.CHISELED_BOOKSHELF));
			for (int x : new int[] {-18, -13}) {
				c.set(x, 1, -17, topSlab(Blocks.DARK_OAK_SLAB));
			}
			BlockPos terminal = new BlockPos(-15, 1, -14);
			c.set(terminal, style.archiveTerminal(Direction.NORTH));
			b.anchor(Anchor.face(AnchorNames.ARCHIVE_TERMINAL, terminal, Direction.NORTH));
			b.anchor(Anchor.at(AnchorNames.ARCHIVE, terminal.north(), Direction.SOUTH));
			c.decor(new BlockPos(-15, 4, -14), wallSign(Direction.NORTH), text(DyeColor.BROWN, "", "Biblioteca", "Archivo", ""));
			lit(c);
		}
	}

	/** The clock tower on the plaza's south-east corner: the scheduled tasks' room, a tall shaft, four clock faces, a belfry. */
	private void clockTower(PlanBuilder b) {
		Box box = clockBox();
		int lo = 12;
		int hi = 20;
		int roof = 38;
		BlockState wall = STONE;
		try (PlanBuilder.Canvas c = b.module("clock", box, List.of())) {
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					boolean body = x >= lo && x <= hi && z >= lo && z <= hi;
					if (!body) {
						c.set(x, 0, z, plazaPave(x, z));
						continue;
					}
					boolean edge = x == lo || x == hi || z == lo || z == hi;
					c.set(x, 0, z, edge ? PLINTH : accent());
					if (!edge) {
						boolean lamp = (x == 16 && z == 16) || (Math.abs(x - 16) == 2 && Math.abs(z - 16) == 2);
						c.set(x, 6, z, lamp ? LAMP : TRIM);
						continue;
					}
					boolean corner = (x == lo || x == hi) && (z == lo || z == hi);
					boolean door = (z == lo && (x == 16 || x == 17)) || (x == lo && (z == 16 || z == 17));
					boolean mid = (z == lo || z == hi) ? x == 16 : z == 16;
					for (int y = 1; y < roof; y++) {
						BlockState st = door && y <= 3 ? AIR
								: corner || y % 6 == 0 ? TRIM : mid && y >= 8 && y <= 30 && y % 6 >= 2 && y % 6 <= 4 ? pane() : wall;
						c.set(x, y, z, st);
					}
				}
			}
			for (int x = lo; x <= hi; x++) {
				for (int z = lo; z <= hi; z++) {
					c.set(x, roof, z, TRIM);
				}
			}
			for (int x : new int[] {lo, hi}) {
				for (int z : new int[] {lo, hi}) {
					for (int y = roof + 1; y <= roof + 3; y++) {
						c.set(x, y, z, PILLAR);
					}
				}
			}
			for (int i = 0; i <= 4; i++) {
				for (int x = lo + i; x <= hi - i; x++) {
					for (int z = lo + i; z <= hi - i; z++) {
						c.set(x, roof + 4 + i, z, COPPER);
					}
				}
			}
			c.set(16, roof + 4, 16, LAMP); // the belfry's ceiling light
			c.set(16, roof + 9, 16, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
			bigScreen(b, c, new HqGeometry.Surface(new BlockPos(17, 33, lo), Direction.WEST, Direction.NORTH, 3, 3), "clock_n", "");
			bigScreen(b, c, new HqGeometry.Surface(new BlockPos(lo, 33, 15), Direction.SOUTH, Direction.WEST, 3, 3), "clock_w", "");
			bigScreen(b, c, new HqGeometry.Surface(new BlockPos(15, 33, hi), Direction.EAST, Direction.SOUTH, 3, 3), "clock_s", "");
			bigScreen(b, c, new HqGeometry.Surface(new BlockPos(hi, 33, 17), Direction.NORTH, Direction.EAST, 3, 3), "clock_e", "");
			HqGeometry.Surface schedule = new HqGeometry.Surface(new BlockPos(17, 2, hi - 1), Direction.WEST, Direction.NORTH, 3, 2);
			bigScreen(b, c, schedule, "schedule", "");
			for (int i = 0; i < schedule.width(); i++) {
				c.set(schedule.origin().relative(schedule.along(), i).below(), TRIM);
			}
			c.decor(new BlockPos(14, 4, lo + 1), wallSign(Direction.SOUTH), text(DyeColor.BLUE, "", "Tareas", "programadas", ""));
			b.anchor(Anchor.at("walk_clock", new BlockPos(14, 1, 15), Direction.SOUTH));
			lit(c);
		}
	}

	/**
	 * The signal box on the plaza's south-west corner (needs attention, errors): brick base, a glazed
	 * cabin all round, a hipped roof with a signal mast; the attention board on its west wall.
	 */
	private void signalBox(PlanBuilder b) {
		Box box = signalBox();
		int x0 = -21;
		int x1 = -10;
		int z0 = 9;
		int z1 = 20;
		BlockState brick = s(Blocks.BRICKS);
		try (PlanBuilder.Canvas c = b.module("signal", box, List.of())) {
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					boolean body = x >= x0 && x <= x1 && z >= z0 && z <= z1;
					if (!body) {
						c.set(x, 0, z, plazaPave(x, z));
						continue;
					}
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					c.set(x, 0, z, edge ? PLINTH : accent());
					c.set(x, 7, z, !edge && Math.floorMod(x, 4) == 1 && Math.floorMod(z, 4) == 1 ? LAMP : TRIM);
					if (!edge) {
						continue;
					}
					boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
					boolean door = (x == x1 && (z == 14 || z == 15)) || (z == z0 && (x == -16 || x == -15));
					boolean pil = corner || ((z == z0 || z == z1) ? Math.floorMod(x, 4) == 0 : Math.floorMod(z, 4) == 0);
					for (int y = 1; y <= 6; y++) {
						BlockState st = door && y <= 3 ? AIR : pil ? s(Blocks.POLISHED_BLACKSTONE_BRICKS) : x == x0 || y <= 2 ? brick : y == 6 ? TRIM : pane();
						c.set(x, y, z, st);
					}
				}
			}
			// hipped roof with a one-block overhang
			for (int l = 0;; l++) {
				int xa = box.minX() + l;
				int xb = box.maxX() - l;
				int za = box.minZ() + l;
				int zb = box.maxZ() - 1 - l;
				if (xa > xb || za > zb) {
					break;
				}
				int y = 8 + l;
				boolean last = xb - xa <= 1 || zb - za <= 1;
				for (int x = xa; x <= xb; x++) {
					for (int z = za; z <= zb; z++) {
						if (last) {
							c.set(x, y, z, s(Blocks.DEEPSLATE_TILES));
							continue;
						}
						Direction up = x == xa ? Direction.EAST : x == xb ? Direction.WEST : z == za ? Direction.SOUTH : z == zb ? Direction.NORTH : null;
						if (up != null) {
							c.set(x, y, z, stair(Blocks.DEEPSLATE_TILE_STAIRS, up));
						}
					}
				}
				if (last) {
					// the signal mast on the ridge, its lamp on top and its arm
					for (int yy = y + 1; yy <= y + 4; yy++) {
						c.set(xa, yy, za, EnsancheStyle.log(Blocks.STRIPPED_DARK_OAK_LOG));
					}
					c.set(xa, y + 5, za, s(Blocks.SHROOMLIGHT));
					c.set(xa + 1, y + 3, za, s(Blocks.STRIPPED_DARK_OAK_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X));
					break;
				}
			}
			board(b, c);
			c.decor(new BlockPos(x1 - 1, 2, z1 - 1), wallSign(Direction.WEST), text(DyeColor.RED, "", "Puesto de", "senales", ""));
			b.anchor(Anchor.at("walk_signal", new BlockPos(-12, 1, 17), Direction.WEST));
			lit(c);
		}
	}

	static final int BOARD_X = -20;
	static final int BOARD_Z = 15;
	static final int BOARD_HALF = 3;

	/** The "needs attention" board on the signal box's west wall, facing the doors. */
	private void board(PlanBuilder b, PlanBuilder.Canvas c) {
		int x = BOARD_X;
		BlockState frame = PLINTH;
		for (int y = 1; y <= 6; y++) {
			c.set(x, y, BOARD_Z - BOARD_HALF - 1, PILLAR);
			c.set(x, y, BOARD_Z + BOARD_HALF + 1, PILLAR);
		}
		for (int t = -BOARD_HALF; t <= BOARD_HALF; t++) {
			int z = BOARD_Z + t;
			c.set(x, 1, z, frame);
			for (int y = 2; y <= 4; y++) {
				c.set(x, y, z, style.palette().get(HqStyle.Role.BOARD));
			}
			c.set(x, 5, z, frame);
			c.set(x, 6, z, TRIM);
		}
		c.decor(new BlockPos(x + 1, 6, BOARD_Z), wallSign(Direction.EAST), text(DyeColor.ORANGE, "", "Needs", "attention", ""));
		int lamp = 0;
		for (int t = -BOARD_HALF; t <= BOARD_HALF; t++) {
			if (Math.floorMod(t + BOARD_HALF, 2) == 0 && t != 0) {
				c.set(x, 5, BOARD_Z + t, style.statusLamp());
				b.anchor(Anchor.face(AnchorNames.boardLamp(lamp++), new BlockPos(x, 5, BOARD_Z + t), Direction.EAST));
			}
		}
		b.anchor(Anchor.face(AnchorNames.BOARD, new BlockPos(x, 3, BOARD_Z), Direction.EAST));
		b.anchor(Anchor.at(AnchorNames.BOARD_FRONT, new BlockPos(x + 1, 1, BOARD_Z), Direction.WEST));
		int slot = 0;
		for (int row = 2; row <= 3; row++) {
			for (int i = 0; i <= 2 * BOARD_HALF; i++) {
				int t = i == 0 ? 0 : (i % 2 == 1 ? (i + 1) / 2 : -(i / 2));
				b.anchor(Anchor.at(AnchorNames.boardSlot(slot++), new BlockPos(x + row, 1, BOARD_Z + t), Direction.WEST));
			}
		}
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
				c.set(cx + dx, 12, cz, LAMP); // a lamp in the nave's ridge
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
			lit(c);
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
			// bandstand: columns, copper canopy with its lamp
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
			c.set(cx, 5, cz, LAMP); // a lamp in the canopy's ceiling
			for (int i = -1; i <= 1; i++) {
				c.set(cx + 7 + i, 1, cz + 7 - i, style.bench(Direction.SOUTH));
				c.set(cx - 7 + i, 1, cz - 7 - i, style.bench(Direction.NORTH));
			}
			b.anchor(Anchor.at("walk_plazuela_" + k, new BlockPos(cx, 1, cz + 1), Direction.SOUTH));
			lit(c);
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
			lit(c);
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
			// fountain-well in the middle, its lit capstone
			for (int x = px - 2; x <= px + 2; x++) {
				for (int z = pz - 2; z <= pz + 2; z++) {
					if (Math.max(Math.abs(x - px), Math.abs(z - pz)) == 2 && (x == px || z == pz) == false) {
						c.set(x, 1, z, EnsancheStyle.slab(Blocks.SMOOTH_STONE_SLAB));
					}
				}
			}
			c.set(px, 1, pz, mat.trim());
			c.set(px, 2, pz, LAMP_OUT); // the well's lit capstone
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
			lit(c);
		}
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
			lit(c);
		}
	}

	// ------------------------------------------------------------------ podium and tower

	/** The two ground-floor doors from a room to its patio (two cells wide each), clear of the next building. */
	static boolean isPatioDoor(int a, int len) {
		int a0 = firstDesk(len);
		return a == a0 + 10 || a == a0 + 11 || a == a0 + 16 || a == a0 + 17;
	}

	/** First desk column of a podium room: six desks, four columns apart, ending next to the passage's wall. */
	static int firstDesk(int length) {
		return length - 28;
	}

	/**
	 * One building: the podium (the ground-floor room along the street, desks against the street
	 * wall, doors to the patio, the covered passage at the inner end) and, when its project has rooms
	 * above the ground floor, the tower on the corner with {@code tf} floors and its stair atrium.
	 */
	private void building(PlanBuilder b, HqLayout layout, int k, int side, TreeMap<Integer, HqLayout.Module> floors, EnsancheStyle.Mat mat, int tf) {
		int p = EnsancheGrid.parcel(k, side);
		EnsancheGrid.Frame f = EnsancheGrid.frame(k, side);
		int len = EnsancheGrid.length(side);
		int dep = EnsancheGrid.depth(side);
		boolean tower = tf > 0;
		boolean pitched = mat.pitched();
		int ridge = (dep - 1) / 2;
		int top = Math.max(pitched ? STOREY + 1 + ridge : STOREY + 1, tower ? STOREY * (tf + 1) + 1 : 0);
		BlockState wall = side % 2 == 0 ? mat.wall() : mat.wall2();
		int a0 = firstDesk(len);
		List<Box> holes = tower ? List.of(TowncenterGrid.streetDoor(p), TowncenterGrid.endDoor(p)) : List.of();
		b.group("parcel_" + p);
		try (PlanBuilder.Canvas c = b.module("parcel_" + p, f.box(0, 0, 0, len - 1, top, dep - 1), holes)) {
			podium(c, f, len, dep, side, wall, mat, tower);
			if (tower) {
				tower(c, f, dep, tf, mat);
			}
			podiumRoof(c, f, len, dep, pitched, ridge, wall, mat, tower);
			List<HqLayout.Module> mine = layout.modulesOf(floors.firstEntry().getValue().projectKey());
			decorateRoom(c, f, len, dep, a0);
			HqLayout.Module ground = floors.get(0);
			if (ground != null) {
				room(b, c, f, len, dep, a0, ground, mine.indexOf(ground), mine.get(0) == ground);
			}
			for (int fl = 1; fl <= tf; fl++) {
				HqLayout.Module m = floors.get(fl);
				if (m != null) {
					towerRoom(b, c, f, dep, fl, m, mine.indexOf(m), mine.get(0) == m);
				}
			}
			HqLayout.Module first = floors.firstEntry().getValue();
			c.decor(f.pos(len - 4, 3, 1), wallSign(f.along()), text(HqStyle.envColor(first), cut(first.label(), 0), cut(first.label(), 1),
					mine.indexOf(first) > 0 ? "#" + (mine.indexOf(first) + 1) : "", first.envLabel()));
			lit(c);
		}
	}

	private static String cut(String label, int line) {
		if (line == 0) {
			return label.length() <= SignTags.LINE ? label : label.substring(0, SignTags.LINE);
		}
		return label.length() <= SignTags.LINE ? "" : label.substring(SignTags.LINE, Math.min(label.length(), 2 * SignTags.LINE));
	}

	/** The podium's single storey: floor, facades, the partition of the passage, its ceiling (the tower writes its own over the corner). */
	private void podium(PlanBuilder.Canvas c, EnsancheGrid.Frame f, int len, int dep, int side, BlockState wall, EnsancheStyle.Mat mat, boolean tower) {
		BlockState trim = mat.trim();
		BlockState plinth = mat.plinth();
		int part = len - 5;
		int hallA = len - 4;
		int blind = EnsancheGrid.depth((side + 1) % EnsancheGrid.SIDES);
		for (int a = 0; a < len; a++) {
			for (int d = 0; d < dep; d++) {
				if (a + d < CHAMFER) {
					c.set(f.pos(a, 0, d), SIDEWALK);
					continue;
				}
				boolean outer = d == 0 || d == dep - 1 || a == 0 || a + d == CHAMFER || a == len - 1;
				boolean hall = a >= hallA && a <= len - 2;
				boolean inTower = tower && a < TOWER_LEN;
				for (int y = 0; y <= STOREY; y++) {
					BlockPos pos = f.pos(a, y, d);
					if (y == 0) {
						c.set(pos, outer || hall ? plinth : floorTile(mat));
					} else if (y == STOREY) {
						if (inTower) {
							continue;
						}
						BlockState slab;
						if (outer) {
							slab = trim;
						} else if (hall) {
							slab = a == hallA + 1 && d == dep / 2 ? LAMP : plinth;
						} else {
							slab = Math.floorMod(a, 4) == 2 && d == dep / 2 && a < part ? LAMP : plinth;
						}
						c.set(pos, slab);
					} else if (outer) {
						c.set(pos, facade(a, d, 0, y, len, dep, blind, wall, trim, plinth, mat, false));
					} else if (a == part) {
						boolean door = d >= 2 && d <= 3 && y <= 2;
						c.set(pos, door ? AIR : y == STOREY - 1 ? trim : partition(mat));
					}
				}
			}
		}
	}

	/**
	 * The corner tower: {@code tf} floors over the podium ({@code y = 6 * floor}), a stair atrium in
	 * the middle (two lanes: even flights rise along {@code +a} in lane {@code L0}, odd flights come
	 * back along {@code -a} in lane {@code L1}; the slabs are open over each flight), windows all round,
	 * a roof with a skylight over the atrium and a parapet.
	 */
	private void tower(PlanBuilder.Canvas c, EnsancheGrid.Frame f, int dep, int tf, EnsancheStyle.Mat mat) {
		int l0 = TowncenterGrid.lane(dep);
		int l1 = l0 + 1;
		BlockState trim = mat.trim();
		BlockState wall = mat.wall();
		int yr = STOREY * (tf + 1);
		for (int a = 0; a < TOWER_LEN; a++) {
			for (int d = 0; d < dep; d++) {
				if (a + d < CHAMFER) {
					continue;
				}
				boolean ring = d == 0 || d == dep - 1 || a == 0 || a == TOWER_LEN - 1 || a + d == CHAMFER;
				for (int fl = 1; fl <= tf; fl++) {
					int y0 = STOREY * fl;
					put(c, f.pos(a, y0, d), ring ? trim : towerSlab(a, d, fl, dep, l0, l1, mat));
					if (ring) {
						for (int r = 1; r < STOREY; r++) {
							put(c, f.pos(a, y0 + r, d), towerFacade(a, d, r, dep, wall, trim));
						}
					}
				}
				// roof: skylight over the atrium, lamps in the top floor's ceiling, a parapet
				boolean sky = a >= LANDING && a <= LANDING + 7 && d >= l0 && d <= l1;
				BlockState roof = ring ? trim : sky ? s(Blocks.GLASS) : Math.floorMod(a, 4) == 3 && (d == 3 || d == dep - 4) ? LAMP : mat.plinth();
				c.set(f.pos(a, yr, d), roof);
				if (ring) {
					c.set(f.pos(a, yr + 1, d), mat.roofSlab());
				}
			}
		}
		c.set(f.pos(TOWER_LEN - 3, yr + 1, dep / 2), Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		// the switchback stair of the atrium, from the podium floor to the top floor
		for (int fl = 0; fl < tf; fl++) {
			int y0 = STOREY * fl;
			for (int k = 1; k <= STOREY; k++) {
				if (fl % 2 == 0) {
					c.set(f.pos(LANDING + k, y0 + k, l0), stair(Blocks.STONE_BRICK_STAIRS, f.along()));
				} else {
					c.set(f.pos(LANDING + 7 - k, y0 + k, l1), stair(Blocks.STONE_BRICK_STAIRS, f.along().getOpposite()));
				}
			}
		}
	}

	/** An inner cell of tower floor {@code fl}'s slab: open over the flight that arrives at it, ceiling lamps, floor tiles. */
	private BlockState towerSlab(int a, int d, int fl, int dep, int l0, int l1, EnsancheStyle.Mat mat) {
		if ((fl - 1) % 2 == 0) {
			if (d == l0 && a >= LANDING + 1 && a <= LANDING + 5) {
				return AIR;
			}
		} else if (d == l1 && a >= LANDING + 2 && a <= LANDING + 6) {
			return AIR;
		}
		if (Math.floorMod(a, 4) == 3 && (d == 3 || d == dep - 4)) {
			return LAMP;
		}
		return floorTile(mat);
	}

	/** A wall cell of a tower storey ({@code r = 1..5} above its slab). */
	private static BlockState towerFacade(int a, int d, int r, int dep, BlockState wall, BlockState trim) {
		boolean street = d == 0;
		boolean patio = d == dep - 1;
		boolean end = a == 0;
		boolean inner = a == TOWER_LEN - 1;
		if ((end || inner) && (street || patio) || a + d == CHAMFER && (a == 0 || d == 0)) {
			return trim;
		}
		if ((street || patio) && Math.floorMod(a, 4) == 0 || (end || inner) && Math.floorMod(d, 4) == 0) {
			return trim;
		}
		if (r == STOREY - 1) {
			return trim;
		}
		if (street) {
			return r == 4 && a >= 8 ? pane() : wall; // desk screens against it from a = 8, the big screen over the bridge door at a = 5..7
		}
		if (patio) {
			return r == 4 || a < 4 && r >= 2 ? pane() : wall;
		}
		if (inner) {
			int dm = dep / 2;
			return r >= 2 && r <= 4 && !(Math.abs(d - dm) <= 1 && r <= 3) ? pane() : wall;
		}
		return r >= 2 && r <= 4 ? pane() : wall; // end face and chamfer
	}

	private void podiumRoof(PlanBuilder.Canvas c, EnsancheGrid.Frame f, int len, int dep, boolean pitched, int ridge, BlockState wall, EnsancheStyle.Mat mat,
			boolean tower) {
		int ys = STOREY + 1;
		int start = tower ? TOWER_LEN : CORNER;
		if (!tower) {
			// corner segment: a flat roof with a parapet
			for (int a = 0; a < CORNER; a++) {
				for (int d = 0; d < dep; d++) {
					if (a + d < CHAMFER) {
						continue;
					}
					if (d == 0 || d == dep - 1 || a == 0 || a + d == CHAMFER || (a == CORNER - 1 && pitched)) {
						c.set(f.pos(a, ys, d), mat.roofSlab());
					}
				}
			}
		}
		if (!pitched) {
			// a roof terrace (the common podium): parapet and planters
			for (int a = start; a < len; a++) {
				for (int d = 0; d < dep; d++) {
					if (d == 0 || d == dep - 1 || a == len - 1) {
						c.set(f.pos(a, ys, d), mat.roofSlab());
					}
				}
			}
			for (int a = start + 3; a < len - 3; a += 6) {
				c.set(f.pos(a, ys, dep / 2), kit.gardenPlanter());
			}
			return;
		}
		for (int a = start; a < len; a++) {
			for (int i = 0; i < ridge; i++) {
				c.set(f.pos(a, ys + i, i), mat.roofStairs(f.in()));
				c.set(f.pos(a, ys + i, dep - 1 - i), mat.roofStairs(f.street()));
			}
			c.set(f.pos(a, ys + ridge, ridge), mat.roofFull());
			if (a == start || a == len - 1) {
				for (int d = 1; d < dep - 1; d++) {
					int yTop = ys + Math.min(d, dep - 1 - d);
					for (int y = ys; y < yTop; y++) {
						c.set(f.pos(a, y, d), wall);
					}
				}
			}
		}
	}

	/** Plants and the meeting table of the podium room (used or not). */
	private void decorateRoom(PlanBuilder.Canvas c, EnsancheGrid.Frame f, int len, int dep, int a0) {
		c.set(f.pos(a0 + 2, 1, dep - 2), kit.gardenPlanter());
		c.set(f.pos(len - 7, 1, dep - 2), kit.gardenPlanter());
		if (dep >= 13) {
			BlockState table = topSlab(Blocks.DARK_OAK_SLAB);
			for (int a = a0 + 5; a <= a0 + 17; a++) {
				c.set(f.pos(a, 1, 7), table);
			}
		}
	}

	/** The podium room (HQ module on floor 0): six desks against the street wall, the room's big screen, anchors. */
	private void room(PlanBuilder b, PlanBuilder.Canvas c, EnsancheGrid.Frame f, int len, int dep, int a0, HqLayout.Module m, int nth, boolean first) {
		int i = m.index();
		for (int slot = 0; slot < HqLayout.DESKS; slot++) {
			String thread = m.threadAt(slot);
			if (thread != null) {
				deskAt(b, c, i, slot, thread, f.pos(a0 + 4 * slot + 1, 1, 2), f.street());
			}
		}
		int part = len - 5;
		Direction sf = f.along().getOpposite();
		Direction sAlong = sf.getCounterClockWise();
		int d0 = sAlong == f.in() ? 5 : 7;
		HqGeometry.Surface s = new HqGeometry.Surface(f.pos(part, 2, d0), sAlong, sf, 3, 2);
		bigScreen(b, c, s, "module@" + i, "project:" + m.projectKey());
		for (int k = 0; k < 3; k++) {
			c.set(s.origin().relative(sAlong, k).below(), PLINTH);
		}
		anchors(b, i, m, first, f.pos(len - 6, 1, 4), f.along().getOpposite(), f.pos((a0 + len - 6) / 2, 1, dep - 3), f.street());
	}

	/**
	 * A tower room (HQ module on floor {@code fl}): three desks along the patio wall, two along the
	 * street wall, one against the inner wall; the room's big screen over the skybridge door.
	 */
	private void towerRoom(PlanBuilder b, PlanBuilder.Canvas c, EnsancheGrid.Frame f, int dep, int fl, HqLayout.Module m, int nth, boolean first) {
		int i = m.index();
		int y = STOREY * fl + 1;
		BlockPos[] stands = {f.pos(5, y, dep - 3), f.pos(9, y, dep - 3), f.pos(13, y, dep - 3), f.pos(9, y, 2), f.pos(13, y, 2), f.pos(13, y, dep / 2)};
		Direction[] facing = {f.in(), f.in(), f.in(), f.street(), f.street(), f.along()};
		for (int slot = 0; slot < HqLayout.DESKS; slot++) {
			String thread = m.threadAt(slot);
			if (thread != null) {
				deskAt(b, c, i, slot, thread, stands[slot], facing[slot]);
			}
		}
		Direction sf = f.in();
		Direction sAlong = sf.getCounterClockWise();
		int as = sAlong == f.along() ? BRIDGE_DOOR : BRIDGE_DOOR + 2;
		bigScreen(b, c, new HqGeometry.Surface(f.pos(as, y + 2, 1), sAlong, sf, 3, 2), "module@" + i, "project:" + m.projectKey());
		c.decor(f.pos(1, y, dep - 2), wallSign(f.along()), text(HqStyle.envColor(m), cut(m.label(), 0), cut(m.label(), 1),
				nth > 0 ? "#" + (nth + 1) : "", "Planta " + fl));
		anchors(b, i, m, first, f.pos(2, y, 3), f.along(), f.pos(12, y, dep - 4), f.in());
	}

	private static void anchors(PlanBuilder b, int i, HqLayout.Module m, boolean first, BlockPos doorPos, Direction doorFacing, BlockPos walkPos,
			Direction walkFacing) {
		Anchor door = Anchor.at(AnchorNames.module(i), doorPos, doorFacing);
		Anchor walk = Anchor.at(AnchorNames.walkModule(i), walkPos, walkFacing);
		b.anchor(door);
		b.anchor(walk);
		if (first) {
			b.anchor(door.renamed(AnchorNames.wing(m.projectKey())));
			b.anchor(walk.renamed(AnchorNames.walk(m.projectKey())));
		}
	}

	// ------------------------------------------------------------------ skybridges

	/**
	 * For every tower: a skybridge to the tower facing its street face (when there is one), else a
	 * window in that door; and a window in its end door unless the tower facing it has a bridge to it.
	 */
	private void bridges(PlanBuilder b, TreeMap<Integer, Integer> towers) {
		for (int p : towers.keySet()) {
			int q = TowncenterGrid.streetPartner(p);
			if (q >= 0 && towers.containsKey(q)) {
				bridge(b, p, q);
			} else {
				plug(b, p, TowncenterGrid.streetDoor(p), "d");
			}
			int r = TowncenterGrid.endPartner(p);
			if (r < 0 || !towers.containsKey(r)) {
				plug(b, p, TowncenterGrid.endDoor(p), "a");
			}
		}
	}

	private void bridge(PlanBuilder b, int p, int q) {
		EnsancheGrid.Frame f = TowncenterGrid.frameOf(p);
		int y = STOREY * TowncenterGrid.BRIDGE_FLOOR;
		b.group("bridge_" + p + "_" + q);
		try (PlanBuilder.Canvas c = b.module("bridge_door_" + p + "_d", TowncenterGrid.streetDoor(p), List.of())) {
			lit(c);
		}
		try (PlanBuilder.Canvas c = b.module("bridge_door_" + q + "_a", TowncenterGrid.endDoor(q), List.of())) {
			lit(c);
		}
		try (PlanBuilder.Canvas c = b.module("bridge_" + p + "_" + q, TowncenterGrid.bridgeSpan(p), List.of())) {
			for (int t = 1; t <= EnsancheGrid.STREET; t++) {
				for (int a = BRIDGE_DOOR - 1; a <= BRIDGE_DOOR + 2; a++) {
					boolean edge = a == BRIDGE_DOOR - 1 || a == BRIDGE_DOOR + 2;
					c.set(f.pos(a, y, -t), edge ? COPPER : accent());
					if (edge) {
						c.set(f.pos(a, y + 1, -t), pane());
						c.set(f.pos(a, y + 2, -t), pane());
					}
					c.set(f.pos(a, y + 3, -t), edge ? COPPER : s(Blocks.GLASS));
				}
			}
			lit(c);
		}
	}

	private void plug(PlanBuilder b, int p, Box hole, String face) {
		b.group("plug_" + p + "_" + face);
		try (PlanBuilder.Canvas c = b.module("plug_" + p + "_" + face, hole, List.of())) {
			c.fill(hole.minX(), hole.minY(), hole.minZ(), hole.maxX(), hole.maxY(), hole.maxZ(), pane());
		}
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

}
