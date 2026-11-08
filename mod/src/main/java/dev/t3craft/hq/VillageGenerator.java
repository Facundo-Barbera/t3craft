package dev.t3craft.hq;

import static dev.t3craft.hq.VillageTown.HALF;
import static dev.t3craft.hq.VillageTown.HUB_LEVEL;

import dev.t3craft.hq.VillageLayout.Hamlet;
import dev.t3craft.hq.VillageLayout.Room;
import dev.t3craft.hq.VillageTown.Plot;
import dev.t3craft.hq.VillageTown.Street;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 * The village HQ (layout/village): an organic hill town on terraces. {@link VillageLayout} in,
 * {@link BuildPlan} out; pure and deterministic like {@link HqGenerator}, and built with the same
 * {@link PlanBuilder} rules (every cell owned by one module, every owned cell listed).
 *
 * <p>Every built cell of the {@link VillageTown} grid is one module and one build group, a
 * {@value VillageTown#CELL}×{@value VillageTown#CELL} column from {@code y = 0} (the natural ground)
 * to its roofs: the terrace body (retaining walls, fill) up to its level, then what stands on it.
 * <ul>
 *   <li>{@code hub}: the town hall (attention board, kanban wall, big screen, benches, hologram
 *   column, beacon) on the hill top; {@code plaza_*}: the paved plaza (spawn, fountain, the review
 *   market hall on the north side); the four singular buildings on the corners: {@code archive}
 *   (the chapel-library with the archive terminal), {@code lounge} (the tavern), {@code schedule}
 *   (the mill tower), {@code attention} (the guard post).</li>
 *   <li>{@code street_<i>_<j>}: a built street cell: curved lanes to every linked neighbour, small
 *   squares with a fountain where three or more lanes meet, stairs where a lane goes one terrace
 *   down, gardens and trees elsewhere.</li>
 *   <li>{@code plot_<rank>}: a project's hamlet (main house, then annexes as threads grow), or an
 *   orchard while the plot is free but its street is built.</li>
 * </ul>
 * Plot cells depend only on their own hamlet, so adding a project elsewhere never changes them.
 */
public final class VillageGenerator {
	/** Style id of the village (render tool, docs). */
	public static final String ID = "village";

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	public record Result(VillageLayout layout, BuildPlan plan) {
	}

	public static Result plan(VillageLayout.Input in, @Nullable VillageLayout previous) {
		VillageLayout layout = VillageLayout.allocate(in, previous);
		return new Result(layout, new VillageGenerator().generate(layout));
	}

	/** Group of a plot. */
	public static String plotGroup(int rank) {
		return "plot_" + rank;
	}

	public static String streetGroup(int i, int j) {
		return "street_" + i + "_" + j;
	}

	// ------------------------------------------------------------------ materials

	/** A district's materials (north stone, east adobe, south half-timber, west brick and cherry). */
	record Mat(String name, BlockState wall, BlockState plinth, Block post, Block roof, BlockState ridge, BlockState gable, BlockState path,
			BlockState path2, BlockState hedge, Block treeLog, BlockState leaves, BlockState retain, BlockState retain2, boolean flatAnnex) {
	}

	private static BlockState leaves(Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
	}

	static final Mat[] DISTRICTS = {
			new Mat("piedra", Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.STRIPPED_SPRUCE_LOG,
					Blocks.DEEPSLATE_TILE_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(),
					Blocks.COBBLESTONE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(), leaves(Blocks.SPRUCE_LEAVES), Blocks.SPRUCE_LOG,
					leaves(Blocks.SPRUCE_LEAVES), Blocks.STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), false),
			new Mat("adobe", Blocks.MUD_BRICKS.defaultBlockState(), Blocks.PACKED_MUD.defaultBlockState(), Blocks.STRIPPED_ACACIA_LOG,
					Blocks.BRICK_STAIRS, Blocks.BRICKS.defaultBlockState(), Blocks.MUD_BRICKS.defaultBlockState(),
					Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), leaves(Blocks.AZALEA_LEAVES),
					Blocks.ACACIA_LOG, leaves(Blocks.ACACIA_LEAVES), Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(),
					true),
			new Mat("entramado", Blocks.CALCITE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), Blocks.STRIPPED_DARK_OAK_LOG,
					Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState(),
					Blocks.GRAVEL.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), leaves(Blocks.OAK_LEAVES), Blocks.OAK_LOG,
					leaves(Blocks.OAK_LEAVES), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), false),
			new Mat("ladrillo", Blocks.BRICKS.defaultBlockState(), Blocks.TUFF_BRICKS.defaultBlockState(), Blocks.STRIPPED_CHERRY_LOG,
					Blocks.CHERRY_STAIRS, Blocks.CHERRY_PLANKS.defaultBlockState(), Blocks.CHERRY_PLANKS.defaultBlockState(),
					Blocks.POLISHED_GRANITE.defaultBlockState(), Blocks.GRANITE.defaultBlockState(), leaves(Blocks.FLOWERING_AZALEA_LEAVES),
					Blocks.CHERRY_LOG, leaves(Blocks.CHERRY_LEAVES), Blocks.TUFF_BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(),
					false)};

	/** The hill top: town hall and plaza. */
	static final Mat HUB_MAT = new Mat("plaza", Blocks.QUARTZ_BRICKS.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState(),
			Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
			Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(),
			leaves(Blocks.OAK_LEAVES), Blocks.OAK_LOG, leaves(Blocks.OAK_LEAVES), Blocks.STONE_BRICKS.defaultBlockState(),
			Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), false);

	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState SCREEN = Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState();
	private static final BlockState BOARD = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
	private static final BlockState BULB = Blocks.COPPER_BULB.waxed().unaffected().defaultBlockState().setValue(BlockStateProperties.LIT, false);
	private static final BlockState BEACON = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
	private static final BlockState GLASS = Blocks.GLASS_PANE.defaultBlockState();
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();

	private static HqKit kit() {
		return HqKit.active();
	}

	private static BlockState log(Block b, Direction.Axis a) {
		return b.defaultBlockState().setValue(BlockStateProperties.AXIS, a);
	}

	private static BlockState stairs(Block b, Direction facing, Half half) {
		return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing).setValue(BlockStateProperties.HALF, half);
	}

	private static BlockState slab(Block b, SlabType t) {
		return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, t);
	}

	private static Block slabOf(Block stairs) {
		if (stairs == Blocks.DEEPSLATE_TILE_STAIRS) {
			return Blocks.DEEPSLATE_TILE_SLAB;
		}
		if (stairs == Blocks.BRICK_STAIRS) {
			return Blocks.BRICK_SLAB;
		}
		if (stairs == Blocks.DARK_OAK_STAIRS) {
			return Blocks.DARK_OAK_SLAB;
		}
		if (stairs == Blocks.CHERRY_STAIRS) {
			return Blocks.CHERRY_SLAB;
		}
		return Blocks.STONE_BRICK_SLAB;
	}

	// ------------------------------------------------------------------ frames

	/**
	 * A cell's local frame: {@code u} to the right, {@code v} away from the front, centred on the
	 * cell. Local directions: NORTH = front ({@code v-}), SOUTH = back, EAST = {@code u+}, WEST =
	 * {@code u-}. {@code mirror} swaps left and right.
	 */
	record Frame(int cx, int cz, Direction front, boolean mirror) {
		Direction right() {
			return mirror ? front.getCounterClockWise() : front.getClockWise();
		}

		BlockPos at(int u, int y, int v) {
			Direction r = right();
			Direction b = front.getOpposite();
			return new BlockPos(cx + u * r.getStepX() + v * b.getStepX(), y, cz + u * r.getStepZ() + v * b.getStepZ());
		}

		Direction dir(Direction local) {
			return switch (local) {
				case NORTH -> front;
				case SOUTH -> front.getOpposite();
				case EAST -> right();
				case WEST -> right().getOpposite();
				default -> local;
			};
		}

		Direction.Axis axis(Direction.Axis local) {
			return switch (local) {
				case X -> right().getAxis();
				case Z -> front.getAxis();
				default -> Direction.Axis.Y;
			};
		}

		static Frame of(int i, int j, Direction front, boolean mirror) {
			return new Frame(VillageTown.centreX(i), VillageTown.centreZ(j), front, mirror);
		}
	}

	/** Drawing in a cell's local frame. */
	private static final class Pen {
		final PlanBuilder.Canvas c;
		final Frame f;

		Pen(PlanBuilder.Canvas c, Frame f) {
			this.c = c;
			this.f = f;
		}

		BlockPos at(int u, int y, int v) {
			return f.at(u, y, v);
		}

		void set(int u, int y, int v, BlockState s) {
			c.set(f.at(u, y, v), s);
		}

		void decor(int u, int y, int v, BlockState s) {
			c.decor(f.at(u, y, v), s);
		}

		void fill(int u1, int y1, int v1, int u2, int y2, int v2, BlockState s) {
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
				for (int v = Math.min(v1, v2); v <= Math.max(v1, v2); v++) {
					for (int u = Math.min(u1, u2); u <= Math.max(u1, u2); u++) {
						set(u, y, v, s);
					}
				}
			}
		}

		BlockState stairs(Block b, Direction local) {
			return VillageGenerator.stairs(b, f.dir(local), Half.BOTTOM);
		}

		BlockState log(Block b, Direction.Axis local) {
			return VillageGenerator.log(b, f.axis(local));
		}

		BlockState facing(BlockState s, Direction local) {
			return s.setValue(BlockStateProperties.HORIZONTAL_FACING, f.dir(local));
		}
	}

	// ------------------------------------------------------------------ state of one generation

	private final Map<Long, Integer> builtLevel = new HashMap<>();
	private final Map<Long, Set<Direction>> open = new HashMap<>();
	private PlanBuilder b;
	private VillageTown town;
	private int squares;

	private int levelOf(int i, int j) {
		Integer l = builtLevel.get(VillageTown.key(i, j));
		return l == null ? -1 : l;
	}

	private void openEdge(int i, int j, Direction d) {
		open.computeIfAbsent(VillageTown.key(i, j), k -> EnumSet.noneOf(Direction.class)).add(d);
		open.computeIfAbsent(VillageTown.key(i + d.getStepX(), j + d.getStepZ()), k -> EnumSet.noneOf(Direction.class)).add(d.getOpposite());
	}

	private Set<Direction> opened(int i, int j) {
		return open.getOrDefault(VillageTown.key(i, j), Set.of());
	}

	public BuildPlan generate(VillageLayout layout) {
		town = VillageTown.get();
		b = new PlanBuilder();
		builtLevel.clear();
		open.clear();
		squares = 0;
		Set<Integer> taken = new HashSet<>(layout.plots());
		Set<Street> built = town.builtStreets(taken);
		List<Plot> gardens = new ArrayList<>();
		for (Plot p : town.plots) {
			if (!taken.contains(p.rank()) && built.contains(p.street())) {
				gardens.add(p);
			}
		}
		for (int j = -1; j <= 1; j++) {
			for (int i = -1; i <= 1; i++) {
				builtLevel.put(VillageTown.key(i, j), HUB_LEVEL);
				if (i != 1) {
					openEdge(i, j, Direction.EAST);
				}
				if (j != 1) {
					openEdge(i, j, Direction.SOUTH);
				}
			}
		}
		for (Street s : built) {
			builtLevel.put(VillageTown.key(s.i, s.j), s.level);
		}
		for (Street s : built) {
			if (s.toPlaza != null) {
				openEdge(s.i, s.j, s.toPlaza);
			}
			for (Street o : s.links) {
				if (built.contains(o)) {
					openEdge(s.i, s.j, direction(s.i, s.j, o.i, o.j));
				}
			}
		}
		for (Plot p : town.plots) {
			if (taken.contains(p.rank()) || gardens.contains(p)) {
				builtLevel.put(VillageTown.key(p.i(), p.j()), p.level());
				openEdge(p.i(), p.j(), p.front());
			}
		}
		// --- hub and plaza
		hub(layout);
		plaza(0, 1, layout);
		plaza(1, 0, layout);
		plaza(-1, 0, layout);
		plaza(0, -1, layout);
		library(layout);
		tavern(layout);
		tower(layout);
		guardPost(layout);
		// --- streets
		for (Street s : town.streetList) {
			if (built.contains(s)) {
				street(s);
			}
		}
		// --- plots
		for (Plot p : town.plots) {
			Hamlet h = layout.hamletAt(p.rank());
			if (h != null) {
				hamlet(p, h, layout);
			} else if (gardens.contains(p)) {
				garden(p);
			}
		}
		return b.build();
	}

	private static Direction direction(int i, int j, int ni, int nj) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (i + d.getStepX() == ni && j + d.getStepZ() == nj) {
				return d;
			}
		}
		throw new IllegalArgumentException("not neighbours");
	}

	/** The module (and group) of one cell. */
	private PlanBuilder.Canvas cell(String id, String group, int i, int j, int top) {
		int cx = VillageTown.centreX(i);
		int cz = VillageTown.centreZ(j);
		b.group(group);
		return b.module(id, new Box(cx - HALF, 0, cz - HALF, cx + HALF, top, cz + HALF), List.of());
	}

	// ------------------------------------------------------------------ terrain

	/**
	 * The terrace body: retaining walls on the cell's border (a darker course every third row), fill
	 * inside, {@code top} as the walking surface at {@code y = floor}.
	 */
	private void terrace(PlanBuilder.Canvas c, int i, int j, int floor, Mat m, BlockState top) {
		Pen p = new Pen(c, Frame.of(i, j, Direction.NORTH, false));
		for (int y = 0; y < floor; y++) {
			for (int v = -HALF; v <= HALF; v++) {
				for (int u = -HALF; u <= HALF; u++) {
					boolean edge = Math.abs(u) == HALF || Math.abs(v) == HALF;
					p.set(u, y, v, edge ? (y % 3 == 0 ? m.retain2() : m.retain()) : DIRT);
				}
			}
		}
		p.fill(-HALF, floor, -HALF, HALF, floor, HALF, top);
	}

	/**
	 * What closes a cell's edges: a parapet where the neighbour is lower (or not built while this
	 * cell is raised), nothing where the edge is open; {@code hedge} draws a hedge on every closed
	 * edge instead (plots). Open edges keep a gap of {@code gap} cells either side of the middle.
	 */
	private void borders(PlanBuilder.Canvas c, int i, int j, int floor, BlockState wall, boolean hedge, int gap) {
		int level = levelOf(i, j);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			int nl = levelOf(i + d.getStepX(), j + d.getStepZ());
			boolean isOpen = opened(i, j).contains(d);
			boolean lower = nl < level && (nl >= 0 || level > 0);
			if (!hedge && !lower && !isOpen) {
				continue;
			}
			if (!hedge && isOpen && !lower) {
				continue;
			}
			Pen p = new Pen(c, Frame.of(i, j, d, false));
			for (int u = -HALF; u <= HALF; u++) {
				if (isOpen && Math.abs(u) <= gap) {
					continue;
				}
				p.set(u, floor + 1, -HALF, wall);
			}
		}
	}

	/** Stairs down through every open edge towards a lower neighbour (one terrace). */
	private void stairsDown(PlanBuilder.Canvas c, int i, int j, int floor, Mat m) {
		int level = levelOf(i, j);
		for (Direction d : opened(i, j)) {
			int nl = levelOf(i + d.getStepX(), j + d.getStepZ());
			if (nl != level - 1) {
				continue;
			}
			Pen p = new Pen(c, Frame.of(i, j, d, false));
			for (int k = 0; k < VillageTown.STEP; k++) {
				int y = floor - (VillageTown.STEP - 1) + k;
				int v = -HALF + k;
				for (int u = -2; u <= 2; u++) {
					p.set(u, y, v, p.stairs(stairsFor(m), Direction.SOUTH));
					for (int a = y + 1; a <= floor + 3; a++) {
						p.set(u, a, v, AIR);
					}
				}
				for (int s = -1; s <= 1; s += 2) {
					for (int a = y; a <= floor; a++) {
						p.set(3 * s, a, v, m.retain());
					}
					p.set(3 * s, floor + 1, v, AIR);
				}
			}
			// lanterns on the cheeks at the top of the stairs
			p.decor(-3, floor + 1, -HALF + VillageTown.STEP, kit().gardenLantern());
			p.decor(3, floor + 1, -HALF + VillageTown.STEP, kit().gardenLantern());
		}
	}

	private static Block stairsFor(Mat m) {
		if (m.retain().is(Blocks.CUT_SANDSTONE)) {
			return Blocks.SANDSTONE_STAIRS;
		}
		if (m.retain().is(Blocks.TUFF_BRICKS)) {
			return Blocks.TUFF_BRICK_STAIRS;
		}
		if (m.retain().is(Blocks.MOSSY_COBBLESTONE)) {
			return Blocks.MOSSY_COBBLESTONE_STAIRS;
		}
		return Blocks.STONE_BRICK_STAIRS;
	}

	private static void tree(Pen p, int u, int floor, int v, Mat m, int height) {
		for (int y = floor + 1; y <= floor + height; y++) {
			p.set(u, y, v, log(m.treeLog(), Direction.Axis.Y));
		}
		for (int dy = -1; dy <= 1; dy++) {
			int r = dy == 1 ? 1 : 2;
			for (int dv = -r; dv <= r; dv++) {
				for (int du = -r; du <= r; du++) {
					if (Math.abs(du) == 2 && Math.abs(dv) == 2 || du == 0 && dv == 0 && dy < 1) {
						continue;
					}
					int uu = u + du;
					int vv = v + dv;
					if (Math.abs(uu) < HALF && Math.abs(vv) < HALF) {
						p.set(uu, floor + height + dy, vv, m.leaves());
					}
				}
			}
		}
		p.set(u, floor + height + 2, v, m.leaves());
	}

	// ------------------------------------------------------------------ hub (town hall)

	private static final int H = VillageTown.floorY(HUB_LEVEL);
	/** Town hall walls at |x| = |z| = HALL. */
	private static final int HALL = 8;
	private static final int HALL_WALL = 8;

	private void hub(VillageLayout layout) {
		try (PlanBuilder.Canvas c = cell("hub", "hub", 0, 0, H + 24)) {
			terrace(c, 0, 0, H, HUB_MAT, HUB_MAT.path());
			Pen p = new Pen(c, Frame.of(0, 0, Direction.NORTH, false));
			Mat m = HUB_MAT;
			// paving around the hall
			for (int v = -HALF; v <= HALF; v++) {
				for (int u = -HALF; u <= HALF; u++) {
					p.set(u, H, v, (u + v & 1) == 0 ? m.path() : m.path2());
				}
			}
			// walls
			for (int y = H + 1; y <= H + HALL_WALL; y++) {
				for (int k = -HALL; k <= HALL; k++) {
					for (int[] w : new int[][] {{k, -HALL}, {k, HALL}, {-HALL, k}, {HALL, k}}) {
						boolean corner = Math.abs(w[0]) == HALL && Math.abs(w[1]) == HALL;
						boolean pilaster = Math.floorMod(k, 4) == 0;
						BlockState s = corner || pilaster ? p.log(m.post(), Direction.Axis.Y)
								: y == H + 1 ? m.plinth() : y == H + HALL_WALL ? p.log(m.post(), Math.abs(w[1]) == HALL ? Direction.Axis.X : Direction.Axis.Z)
										: m.wall();
						if (!corner && !pilaster && y >= H + 3 && y <= H + 6 && Math.floorMod(k, 4) == 2) {
							s = GLASS;
						}
						p.set(w[0], y, w[1], s);
					}
				}
			}
			// interior floor
			for (int v = -HALL + 1; v <= HALL - 1; v++) {
				for (int u = -HALL + 1; u <= HALL - 1; u++) {
					p.set(u, H, v, Math.abs(u) <= 2 && Math.abs(v) <= 2 || Math.abs(u) == Math.abs(v) ? kit().floorAccent() : kit().floor());
				}
			}
			// ceiling with lights, the beacon in the middle (over the hologram column)
			for (int v = -HALL + 1; v <= HALL - 1; v++) {
				for (int u = -HALL + 1; u <= HALL - 1; u++) {
					boolean light = Math.floorMod(u, 4) == 2 && Math.floorMod(v, 4) == 2;
					p.set(u, H + HALL_WALL + 1, v, light ? kit().wallPanel(true) : m.gable());
				}
			}
			// hipped roof with a lantern cupola
			for (int r = 0; r <= HALL; r++) {
				int e = HALL + 1 - r;
				int y = H + HALL_WALL + r;
				if (e <= 2) {
					break;
				}
				ring(p, e, y, m.roof());
			}
			int cy = H + HALL_WALL + HALL - 1;
			p.fill(-2, cy, -2, 2, cy, 2, m.ridge());
			for (int u = -1; u <= 1; u++) {
				for (int v = -1; v <= 1; v++) {
					boolean edge = Math.abs(u) == 1 || Math.abs(v) == 1;
					p.set(u, cy + 1, v, edge ? (Math.abs(u) == 1 && Math.abs(v) == 1 ? p.log(m.post(), Direction.Axis.Y) : GLASS) : BEACON);
					p.set(u, cy + 2, v, edge ? (Math.abs(u) == 1 && Math.abs(v) == 1 ? p.log(m.post(), Direction.Axis.Y) : GLASS) : AIR);
				}
			}
			p.fill(-1, cy + 3, -1, 1, cy + 3, 1, slab(Blocks.DEEPSLATE_TILE_SLAB, SlabType.BOTTOM));
			p.set(0, cy + 4, 0, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
			// doors: the entrance (south) and side doors near the south corners
			p.fill(-1, H + 1, HALL, 1, H + 4, HALL, AIR);
			p.fill(-HALL, H + 1, 5, -HALL, H + 3, 6, AIR);
			p.fill(HALL, H + 1, 5, HALL, H + 3, 6, AIR);
			sign(p, 0, H + 6, HALL + 1, Direction.SOUTH, DyeColor.WHITE, "", "Ayuntamiento", "HQ", "");
			// the needs-attention board on the north wall
			p.fill(-4, H + 1, -HALL, 4, H + 6, -HALL, p.log(m.post(), Direction.Axis.X));
			p.fill(-3, H + 1, -HALL, 3, H + 5, -HALL, BOARD);
			sign(p, 0, H + 6, -HALL + 1, Direction.SOUTH, DyeColor.ORANGE, "", "Needs", "attention", "");
			int lamp = 0;
			for (int x = -3; x <= 3; x += 2) {
				p.set(x, H + 5, -HALL, BULB);
				b.anchor(Anchor.face(AnchorNames.boardLamp(lamp++), p.at(x, H + 5, -HALL), Direction.SOUTH));
			}
			b.anchor(Anchor.face(AnchorNames.BOARD, p.at(0, H + 3, -HALL), Direction.SOUTH));
			b.anchor(Anchor.at(AnchorNames.BOARD_FRONT, p.at(0, H + 1, -HALL + 1), Direction.NORTH));
			int slot = 0;
			for (int row = 2; row <= 3; row++) {
				for (int k = 0; k <= 6; k++) {
					int x = k == 0 ? 0 : (k % 2 == 1 ? (k + 1) / 2 : -(k / 2));
					b.anchor(Anchor.at(AnchorNames.boardSlot(slot++), p.at(x, H + 1, -HALL + row), Direction.NORTH));
				}
			}
			// benches facing the side walls' screens
			int bench = 0;
			for (int sx = -1; sx <= 1; sx += 2) {
				Direction look = sx > 0 ? Direction.EAST : Direction.WEST;
				for (int z = -2; z <= 2; z++) {
					p.set(5 * sx, H + 1, z, stairs(Blocks.DARK_OAK_STAIRS, look.getOpposite(), Half.BOTTOM));
					b.anchor(Anchor.at(AnchorNames.bench(bench++), p.at(6 * sx, H + 1, z), look));
				}
			}
			// big screen (east) and the thread kanban wall (west)
			HqGeometry.Surface screen = new HqGeometry.Surface(new BlockPos(HALL - 1, H + 2, -2), Direction.SOUTH, Direction.WEST, 5, 3);
			for (BlockPos q : screen.cells()) {
				c.set(q, SCREEN);
			}
			for (int z = -2; z <= 2; z++) {
				c.set(new BlockPos(HALL - 1, H + 1, z), p.log(m.post(), Direction.Axis.Z));
			}
			b.anchor(Anchor.face(AnchorNames.bigScreen("hub", 5, 3, hubBinding(layout)), screen.origin().above(2), screen.facing()));
			HqGeometry.Surface wall = new HqGeometry.Surface(new BlockPos(-HALL + 1, H + 1, 4), Direction.NORTH, Direction.EAST, 9, 4);
			for (BlockPos q : wall.cells()) {
				c.set(q, SCREEN);
			}
			b.anchor(Anchor.face(AnchorNames.THREAD_WALL, wall.origin(), wall.facing()));
			b.anchor(Anchor.face(AnchorNames.THREAD_WALL_END, wall.origin().relative(wall.along(), 8).above(3), wall.facing()));
			b.anchor(Anchor.at(AnchorNames.THREAD_WALL_FRONT, wall.anchorCell().relative(wall.facing(), 3).atY(H + 1), Direction.WEST));
			// planters in the corners
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					p.decor(6 * sx, H + 1, 6 * sz, kit().gardenPlanter());
				}
			}
			// hologram column and beacon
			for (int y = H + 1; y <= H + HqGeometry.HOLOGRAM_TOP; y++) {
				p.set(0, y, 0, AIR);
			}
			p.set(0, H + HqGeometry.HOLOGRAM_TOP + 1, 0, BEACON);
			int walk = 0;
			for (int wz : new int[] {-2, 0}) {
				for (int sx = -1; sx <= 1; sx += 2) {
					b.anchor(Anchor.at(AnchorNames.walkHub(walk++), p.at(3 * sx, H + 1, wz), Direction.SOUTH));
				}
			}
			b.anchor(Anchor.at(AnchorNames.HUB, p.at(0, H + 1, 0), Direction.SOUTH));
			b.anchor(new Anchor(AnchorNames.HOLOGRAM, 0.5, H + 5.0, 0.5, Direction.SOUTH, new BlockPos(0, H + 5, 0)));
			b.anchor(Anchor.at(AnchorNames.BEACON, new BlockPos(0, H + HqGeometry.HOLOGRAM_TOP + 1, 0), Direction.SOUTH));
			b.anchor(Anchor.at(AnchorNames.ENTRANCE, p.at(0, H + 1, HALL), Direction.SOUTH));
		}
	}

	/** One ring of a hipped roof: stairs facing in, at half size {@code e} around the frame's centre. */
	private static void ring(Pen p, int e, int y, Block roof) {
		for (int k = -e; k <= e; k++) {
			p.set(k, y, -e, p.stairs(roof, Direction.SOUTH));
			p.set(k, y, e, p.stairs(roof, Direction.NORTH));
			if (Math.abs(k) < e) {
				p.set(-e, y, k, p.stairs(roof, Direction.EAST));
				p.set(e, y, k, p.stairs(roof, Direction.WEST));
			}
		}
	}

	/** What the hub's big screen shows: the environment with the most hamlets (ties: first key). */
	static String hubBinding(VillageLayout layout) {
		Map<String, Integer> count = new java.util.TreeMap<>();
		for (Hamlet h : layout.hamlets()) {
			if (!h.envKey().isEmpty()) {
				count.merge(h.envKey(), 1, Integer::sum);
			}
		}
		String best = null;
		for (var e : count.entrySet()) {
			if (best == null || e.getValue() > count.get(best)) {
				best = e.getKey();
			}
		}
		return best == null ? "" : "env:" + best;
	}

	private static void sign(Pen p, int u, int y, int v, Direction localFacing, DyeColor color, String... lines) {
		BlockState s = Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, p.f.dir(localFacing));
		p.c.decor(p.at(u, y, v), s, SignTags.sign(color, true, lines));
	}

	// ------------------------------------------------------------------ plaza

	private void plaza(int i, int j, VillageLayout layout) {
		String id = j == 1 ? "plaza_s" : i == 1 ? "plaza_e" : i == -1 ? "plaza_w" : "plaza_n";
		boolean north = j == -1;
		try (PlanBuilder.Canvas c = cell(id, north ? "review" : id, i, j, H + 14)) {
			Mat m = HUB_MAT;
			terrace(c, i, j, H, m, m.path());
			Pen p = new Pen(c, Frame.of(i, j, Direction.NORTH, false));
			for (int v = -HALF; v <= HALF; v++) {
				for (int u = -HALF; u <= HALF; u++) {
					int ring = Math.max(Math.abs(u), Math.abs(v));
					p.set(u, H, v, ring % 4 == 0 ? m.path2() : (u + v & 1) == 0 ? m.path() : Blocks.ANDESITE.defaultBlockState());
				}
			}
			// trees in round beds on the outer corners, lanterns along the edges
			Direction out = j == 1 ? Direction.SOUTH : i == 1 ? Direction.EAST : i == -1 ? Direction.WEST : Direction.NORTH;
			Pen o = new Pen(c, Frame.of(i, j, out, false));
			for (int s = -1; s <= 1; s += 2) {
				o.set(7 * s, H, -6, GRASS);
				tree(o, 7 * s, H, -6, m, 5);
				o.decor(4 * s, H + 1, -8, kit().gardenLantern());
			}
			if (j == 1) {
				// the fountain in front of the town hall and the spawn by the south stairs
				fountain(p, 0, H, 0);
				b.anchor(Anchor.at(AnchorNames.SPAWN, p.at(0, H + 1, 5), Direction.NORTH));
				b.anchor(Anchor.at(AnchorNames.walkHub(4), p.at(-4, H + 1, 0), Direction.NORTH));
			}
			if (i != 0) {
				// a long bench under each side tree row, facing the hall
				for (int v = -2; v <= 2; v++) {
					o.set(v, H + 1, -3, o.stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH));
				}
				b.anchor(Anchor.at(AnchorNames.walkHub(i > 0 ? 5 : 6), o.at(0, H + 1, -1), o.f.dir(Direction.NORTH)));
			}
			if (north) {
				lonja(p, layout);
			}
			borders(c, i, j, H, m.retain(), false, 2);
			stairsDown(c, i, j, H, m);
		}
	}

	private static void fountain(Pen p, int u, int floor, int v) {
		for (int du = -2; du <= 2; du++) {
			for (int dv = -2; dv <= 2; dv++) {
				boolean rim = Math.abs(du) == 2 || Math.abs(dv) == 2;
				p.set(u + du, floor, v + dv, Blocks.STONE_BRICKS.defaultBlockState());
				p.set(u + du, floor + 1, v + dv, rim ? Blocks.STONE_BRICKS.defaultBlockState() : WATER);
			}
		}
		p.set(u, floor + 1, v, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
		p.set(u, floor + 2, v, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
		p.set(u, floor + 3, v, WATER);
	}

	/** The review market hall: an open roof on columns over two rows of desks, the lane through the middle. */
	private void lonja(Pen p, VillageLayout layout) {
		Mat m = HUB_MAT;
		int f = H;
		for (int u = -8; u <= 8; u++) {
			for (int v = -5; v <= 5; v++) {
				p.set(u, f, v, Math.abs(u) <= 1 ? kit().floorAccent() : kit().floor());
			}
		}
		for (int u : new int[] {-8, -4, 4, 8}) {
			for (int v = -5; v <= 5; v += 10) {
				p.fill(u, f + 1, v, u, f + 4, v, p.log(m.post(), Direction.Axis.Y));
			}
		}
		for (int u = -8; u <= 8; u++) {
			p.set(u, f + 5, -5, p.log(m.post(), Direction.Axis.X));
			p.set(u, f + 5, 5, p.log(m.post(), Direction.Axis.X));
		}
		for (int v = -4; v <= 4; v++) {
			p.set(-8, f + 5, v, p.log(m.post(), Direction.Axis.Z));
			p.set(8, f + 5, v, p.log(m.post(), Direction.Axis.Z));
		}
		gable(p, -8, -5, 8, 5, f + 5, Blocks.BRICK_STAIRS, Blocks.BRICKS.defaultBlockState(), Blocks.BRICKS.defaultBlockState(), true, false);
		for (int u = -7; u <= 7; u += 14) {
			p.decor(u, f + 1, 0, kit().lantern());
		}
		p.set(0, f + 4, 5, p.log(m.post(), Direction.Axis.X));
		sign(p, 0, f + 4, 6, Direction.SOUTH, DyeColor.LIGHT_BLUE, "", "Lonja", "review / PR", "");
		List<VillageLayout.Seat> seats = layout.room(Room.REVIEW);
		int[] us = {-7, -3, 3, 7};
		for (int s = 0; s < 8; s++) {
			int u = us[s % 4];
			boolean northRow = s < 4;
			String t = threadAt(seats, s);
			desk(p, u, f + 1, northRow ? -4 : 4, northRow ? Direction.NORTH : Direction.SOUTH, t, Room.REVIEW.module(), s, f);
		}
	}

	private static @Nullable String threadAt(List<VillageLayout.Seat> seats, int slot) {
		for (VillageLayout.Seat s : seats) {
			if (s.slot() == slot) {
				return s.threadId();
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ singular buildings

	/** A corner cell of the hub: paved terrace, borders and stairs; returns the pen in the building's frame. */
	private Pen corner(PlanBuilder.Canvas c, int i, int j, Direction front) {
		Mat m = HUB_MAT;
		terrace(c, i, j, H, m, m.path());
		Pen g = new Pen(c, Frame.of(i, j, Direction.NORTH, false));
		for (int v = -HALF; v <= HALF; v++) {
			for (int u = -HALF; u <= HALF; u++) {
				g.set(u, H, v, (u * 3 + v & 3) == 0 ? m.path2() : m.path());
			}
		}
		borders(c, i, j, H, m.retain(), false, 2);
		return new Pen(c, Frame.of(i, j, front, false));
	}

	/** The chapel-library: the archive terminal under a steep roof and a belfry. Always built. */
	private void library(VillageLayout layout) {
		try (PlanBuilder.Canvas c = cell("archive", HqGenerator.GROUP_ARCHIVE, -1, 1, H + 24)) {
			Pen p = corner(c, -1, 1, Direction.NORTH);
			Mat m = DISTRICTS[0];
			int f = H;
			house(p, -5, -7, 5, 7, f, 7, m, Roof.GABLE_V, 0);
			door(p, 0, -7, f, 3);
			// bookshelves along the nave (decoration only: the terminal lists every archived thread)
			for (int v = -5; v <= 4; v++) {
				if (v == -1 || v == 0) {
					continue;
				}
				p.fill(-4, f + 1, v, -4, f + 3, v, Blocks.BOOKSHELF.defaultBlockState());
				p.fill(4, f + 1, v, 4, f + 3, v, Blocks.BOOKSHELF.defaultBlockState());
			}
			BlockPos t = p.at(0, f + 1, 6);
			c.set(t, Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, p.f.dir(Direction.NORTH)));
			b.anchor(Anchor.face(AnchorNames.ARCHIVE_TERMINAL, t, p.f.dir(Direction.NORTH)));
			b.anchor(Anchor.at(AnchorNames.ARCHIVE, p.at(0, f + 1, 5), p.f.dir(Direction.SOUTH)));
			p.decor(-2, f + 1, 6, kit().lantern());
			p.decor(2, f + 1, 6, kit().lantern());
			sign(p, 0, f + 5, -8, Direction.NORTH, DyeColor.WHITE, "", "Biblioteca", "archivo", layout.archived().size() + " hilos");
			// belfry over the door
			int top = f + 7 + 7;
			for (int y = f + 8; y <= top; y++) {
				for (int u = -1; u <= 1; u++) {
					for (int v = -8; v <= -6; v++) {
						boolean post = Math.abs(u) == 1 && (v == -8 || v == -6);
						p.set(u, y, v, post ? p.log(m.post(), Direction.Axis.Y) : y >= top - 1 ? AIR : m.wall());
					}
				}
			}
			p.set(0, top - 1, -7, Blocks.BELL.defaultBlockState());
			p.fill(-1, top + 1, -8, 1, top + 1, -6, m.ridge());
			p.set(0, top + 2, -7, slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, SlabType.BOTTOM));
		}
	}

	/** The tavern: a long bar with standing places for idle threads. Always built. */
	private void tavern(VillageLayout layout) {
		try (PlanBuilder.Canvas c = cell("lounge", "lounge", -1, -1, H + 20)) {
			Pen p = corner(c, -1, -1, Direction.SOUTH);
			Mat m = DISTRICTS[2];
			int f = H;
			house(p, -7, -5, 7, 5, f, 5, m, Roof.GABLE_U, 0);
			door(p, 0, -5, f, 3);
			// the bar along the back wall, barrels behind it
			for (int u = -6; u <= 6; u++) {
				p.set(u, f + 1, 3, Blocks.BARREL.defaultBlockState());
				p.set(u, f + 1, 4, Blocks.BARREL.defaultBlockState());
				p.set(u, f + 2, 4, Blocks.BARREL.defaultBlockState());
			}
			p.decor(-6, f + 2, 3, kit().lantern());
			p.decor(6, f + 2, 3, kit().lantern());
			// two tables
			for (int s = -1; s <= 1; s += 2) {
				p.set(4 * s, f + 1, -1, kit().desk(p.f.dir(Direction.NORTH)));
			}
			List<VillageLayout.Seat> seats = layout.room(Room.LOUNGE);
			int[][] spots = {{-5, 2}, {-3, 2}, {-1, 2}, {1, 2}, {3, 2}, {5, 2}, {-4, -2}, {4, -2}};
			for (int s = 0; s < spots.length; s++) {
				String t = threadAt(seats, s);
				if (t != null) {
					b.anchor(Anchor.at(AnchorNames.desk(t), p.at(spots[s][0], f + 1, spots[s][1]), p.f.dir(Direction.SOUTH)));
				}
			}
			sign(p, 0, f + 4, -6, Direction.NORTH, DyeColor.LIME, "", "Taberna", "lounge", "");
			// chimney
			for (int y = f + 1; y <= f + 12; y++) {
				p.set(-6, y, 5, Blocks.BRICKS.defaultBlockState());
			}
		}
	}

	/** The mill tower: scheduled tasks, three desks, sails on the back. Always built. */
	private void tower(VillageLayout layout) {
		try (PlanBuilder.Canvas c = cell("schedule", "schedule", 1, -1, H + 24)) {
			Pen p = corner(c, 1, -1, Direction.SOUTH);
			Mat m = DISTRICTS[1];
			int f = H;
			house(p, -4, -4, 4, 4, f, 12, m, Roof.HIP, 0);
			door(p, 0, -4, f, 3);
			List<VillageLayout.Seat> seats = layout.room(Room.SCHEDULE);
			desk(p, 0, f + 1, 3, Direction.SOUTH, threadAt(seats, 0), Room.SCHEDULE.module(), 0, f);
			desk(p, -3, f + 1, 0, Direction.WEST, threadAt(seats, 1), Room.SCHEDULE.module(), 1, f);
			desk(p, 3, f + 1, 0, Direction.EAST, threadAt(seats, 2), Room.SCHEDULE.module(), 2, f);
			sign(p, 0, f + 5, -5, Direction.NORTH, DyeColor.YELLOW, "", "Molino", "programadas", "");
			// sails on the back wall
			int hy = f + 9;
			BlockPos hubPos = p.at(0, hy, 5);
			c.set(hubPos, log(Blocks.STRIPPED_SPRUCE_LOG, p.f.axis(Direction.Axis.Z)));
			for (int k = 1; k <= 5; k++) {
				p.set(k, hy, 6, Blocks.SPRUCE_PLANKS.defaultBlockState());
				p.set(-k, hy, 6, Blocks.SPRUCE_PLANKS.defaultBlockState());
				p.set(0, hy + k, 6, Blocks.SPRUCE_PLANKS.defaultBlockState());
				p.set(0, hy - k, 6, Blocks.SPRUCE_PLANKS.defaultBlockState());
				if (k >= 2) {
					p.set(k, hy + 1, 6, Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState());
					p.set(-k, hy - 1, 6, Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState());
					p.set(-1, hy + k, 6, Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState());
					p.set(1, hy - k, 6, Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState());
				}
			}
			p.set(0, hy, 6, log(Blocks.SPRUCE_LOG, p.f.axis(Direction.Axis.Z)));
		}
	}

	/** The guard post: errors and blocking requests, crenellated, with a watch turret. Always built. */
	private void guardPost(VillageLayout layout) {
		try (PlanBuilder.Canvas c = cell("attention", "attention", 1, 1, H + 20)) {
			Pen p = corner(c, 1, 1, Direction.NORTH);
			Mat m = new Mat("guardia", Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState(),
					Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
					Blocks.DEEPSLATE_BRICKS.defaultBlockState(), HUB_MAT.path(), HUB_MAT.path2(), HUB_MAT.hedge(), Blocks.OAK_LOG,
					HUB_MAT.leaves(), HUB_MAT.retain(), HUB_MAT.retain2(), true);
			int f = H;
			house(p, -5, -4, 5, 4, f, 6, m, Roof.FLAT, 0);
			door(p, 0, -4, f, 3);
			for (int u = -5; u <= 5; u++) {
				p.set(u, f + 5, -4, Blocks.WOOL.pick(DyeColor.RED).defaultBlockState());
			}
			List<VillageLayout.Seat> seats = layout.room(Room.ATTENTION);
			desk(p, 0, f + 1, 3, Direction.SOUTH, threadAt(seats, 0), Room.ATTENTION.module(), 0, f);
			desk(p, -4, f + 1, 0, Direction.WEST, threadAt(seats, 1), Room.ATTENTION.module(), 1, f);
			desk(p, 4, f + 1, 0, Direction.EAST, threadAt(seats, 2), Room.ATTENTION.module(), 2, f);
			sign(p, 0, f + 3, -5, Direction.NORTH, DyeColor.RED, "", "Guardia", "errores", "");
			// watch turret on the back corner
			int r = f + 7;
			for (int y = r; y <= r + 5; y++) {
				for (int u = 3; u <= 5; u++) {
					for (int v = 2; v <= 4; v++) {
						boolean edge = u != 4 || v != 3;
						p.set(u, y, v, y >= r + 4 ? (edge && (u + v & 1) == 0 ? m.wall() : AIR) : m.wall());
					}
				}
			}
			p.set(4, r + 4, 3, Blocks.CAMPFIRE.defaultBlockState());
		}
	}

	// ------------------------------------------------------------------ buildings

	enum Roof {
		/** Ridge along u. */
		GABLE_U,
		/** Ridge along v. */
		GABLE_V,
		HIP,
		/** Flat roof with crenellations. */
		FLAT
	}

	/**
	 * A house: kit floor, walls with a plinth course, corner posts, a beam at the top, windows, a flat
	 * ceiling with lights and a roof. Doors are cut afterwards with {@link #door}.
	 */
	private static void house(Pen p, int u1, int v1, int u2, int v2, int f, int h, Mat m, Roof roof, int seed) {
		for (int v = v1; v <= v2; v++) {
			for (int u = u1; u <= u2; u++) {
				boolean edge = u == u1 || u == u2 || v == v1 || v == v2;
				p.set(u, f, v, edge ? m.plinth() : kit().floor());
				for (int y = f + 1; y <= f + h; y++) {
					if (!edge) {
						p.set(u, y, v, AIR);
						continue;
					}
					boolean corner = (u == u1 || u == u2) && (v == v1 || v == v2);
					boolean alongU = v == v1 || v == v2;
					int k = alongU ? u - u1 : v - v1;
					int len = alongU ? u2 - u1 : v2 - v1;
					BlockState s;
					if (corner) {
						s = p.log(m.post(), Direction.Axis.Y);
					} else if (y == f + h) {
						s = p.log(m.post(), alongU ? Direction.Axis.X : Direction.Axis.Z);
					} else if (y == f + 1) {
						s = m.plinth();
					} else if (k % 3 == 0 && k != 0 && k != len && h >= 5) {
						s = p.log(m.post(), Direction.Axis.Y);
					} else if (y >= f + 2 && y <= f + Math.min(h - 1, 4) && k % 3 == 2 && k > 1 && k < len - 1) {
						s = GLASS;
					} else {
						s = m.wall();
					}
					p.set(u, y, v, s);
				}
			}
		}
		// ceiling
		for (int v = v1 + 1; v <= v2 - 1; v++) {
			for (int u = u1 + 1; u <= u2 - 1; u++) {
				boolean light = Math.floorMod(u - u1, 4) == 2 && Math.floorMod(v - v1, 4) == 2;
				p.set(u, f + h + 1, v, light ? kit().wallPanel(true) : m.gable());
			}
		}
		int y0 = f + h;
		switch (roof) {
			case GABLE_U -> gable(p, u1, v1, u2, v2, y0, m.roof(), m.ridge(), m.gable(), true, true);
			case GABLE_V -> gable(p, u1, v1, u2, v2, y0, m.roof(), m.ridge(), m.gable(), false, true);
			case HIP -> hip(p, u1, v1, u2, v2, y0, m.roof(), m.ridge());
			case FLAT -> {
				for (int u = u1; u <= u2; u++) {
					for (int v = v1; v <= v2; v++) {
						boolean edge = u == u1 || u == u2 || v == v1 || v == v2;
						if (edge) {
							p.set(u, y0 + 1, v, m.wall());
							p.set(u, y0 + 2, v, (u + v & 1) == 0 ? m.wall() : AIR);
						}
					}
				}
			}
		}
	}

	/**
	 * A gable roof over the box, one block of overhang; the slopes rise one block per row from
	 * {@code y0} (the wall top) to the ridge. {@code alongU}: the ridge runs along {@code u}.
	 */
	private static void gable(Pen p, int u1, int v1, int u2, int v2, int y0, Block roof, BlockState ridge, BlockState gableFill, boolean alongU,
			boolean closeEnds) {
		int a1 = alongU ? v1 - 1 : u1 - 1;
		int a2 = alongU ? v2 + 1 : u2 + 1;
		int n = a2 - a1 + 1;
		int l1 = alongU ? u1 - 1 : v1 - 1;
		int l2 = alongU ? u2 + 1 : v2 + 1;
		for (int k = 0; k < n; k++) {
			int a = a1 + k;
			int up = Math.min(k, n - 1 - k);
			int y = y0 + up;
			boolean middle = k == n - 1 - k;
			Direction toRidge = alongU ? (k < n - 1 - k ? Direction.SOUTH : Direction.NORTH) : (k < n - 1 - k ? Direction.EAST : Direction.WEST);
			for (int l = l1; l <= l2; l++) {
				int u = alongU ? l : a;
				int v = alongU ? a : l;
				p.set(u, y, v, middle ? ridge : p.stairs(roof, toRidge));
				boolean endWall = alongU ? (l == u1 || l == u2) : (l == v1 || l == v2);
				boolean inside = alongU ? a > v1 && a < v2 : a > u1 && a < u2;
				if (closeEnds && endWall && inside) {
					for (int yy = y0 + 1; yy < y; yy++) {
						p.set(u, yy, v, gableFill);
					}
				}
			}
			if (n % 2 == 0 && (k == n / 2 || k == n / 2 - 1)) {
				for (int l = l1; l <= l2; l++) {
					int u = alongU ? l : a;
					int v = alongU ? a : l;
					p.set(u, y + 1, v, slab(slabOf(roof), SlabType.BOTTOM));
				}
			}
		}
	}

	private static void hip(Pen p, int u1, int v1, int u2, int v2, int y0, Block roof, BlockState ridge) {
		int a1 = u1 - 1;
		int a2 = u2 + 1;
		int b1 = v1 - 1;
		int b2 = v2 + 1;
		for (int r = 0;; r++) {
			int x1 = a1 + r;
			int x2 = a2 - r;
			int z1 = b1 + r;
			int z2 = b2 - r;
			int y = y0 + r;
			if (x2 - x1 <= 1 || z2 - z1 <= 1) {
				for (int u = x1; u <= x2; u++) {
					for (int v = z1; v <= z2; v++) {
						p.set(u, y, v, ridge);
					}
				}
				break;
			}
			for (int u = x1; u <= x2; u++) {
				p.set(u, y, z1, p.stairs(roof, Direction.SOUTH));
				p.set(u, y, z2, p.stairs(roof, Direction.NORTH));
			}
			for (int v = z1 + 1; v <= z2 - 1; v++) {
				p.set(x1, y, v, p.stairs(roof, Direction.EAST));
				p.set(x2, y, v, p.stairs(roof, Direction.WEST));
			}
		}
	}

	/** A doorway in the wall at (u, v), three wide when {@code width} is 3, three tall; the lintel stays. */
	private static void door(Pen p, int u, int v, int f, int width) {
		int w = width / 2;
		for (int du = -w; du <= w; du++) {
			for (int y = f + 1; y <= f + 3; y++) {
				p.set(u + du, y, v, AIR);
			}
			p.set(u + du, f, v, kit().floorAccent());
		}
	}

	/** A doorway through a wall along v (between two buildings): cells (u, v) and (u, v+1). */
	private static void passage(Pen p, int u, int v, int f) {
		for (int dv = 0; dv <= 1; dv++) {
			for (int y = f + 1; y <= f + 3; y++) {
				p.set(u, y, v + dv, AIR);
			}
			p.set(u, f, v + dv, kit().floorAccent());
		}
	}

	/**
	 * One standing desk: the kit's desk tops (three, along the wall), the 3×2 screen on them, the free
	 * standing cell in front. Vacant ({@code thread == null}): bare floor, nothing built.
	 *
	 * @param u,y,v     the middle desk top (under the screen's bottom-middle cell)
	 * @param localFace from the agent to the desk
	 */
	private void desk(Pen p, int u, int y, int v, Direction localFace, @Nullable String thread, int module, int slot, int floor) {
		if (thread == null) {
			return;
		}
		Direction f = p.f.dir(localFace);
		Direction right = f.getClockWise();
		BlockPos top = p.at(u, y, v);
		Direction front = f.getOpposite();
		p.c.set(top, kit().desk(front));
		p.c.set(top.relative(right), kit().desk(front));
		p.c.set(top.relative(right.getOpposite()), kit().desk(front));
		BlockPos monitor = top.above();
		Direction screenFacing = f.getOpposite();
		BlockPos origin = Workstation.screenOriginFor(monitor, screenFacing);
		for (BlockPos q : Workstation.screenCells(origin, screenFacing.getCounterClockWise())) {
			p.c.set(q, SCREEN);
		}
		BlockPos stand = top.relative(f.getOpposite());
		p.c.set(stand, AIR);
		p.c.set(stand.above(), AIR);
		Anchor deskA = Anchor.at(AnchorNames.deskSlot(module, slot), stand, f);
		Anchor screen = Anchor.face(AnchorNames.screenSlot(module, slot), monitor, screenFacing);
		b.workstation(new Workstation(module, slot, thread, deskA, monitor, screen, origin));
		b.anchor(deskA.renamed(AnchorNames.desk(thread)));
		b.anchor(screen.renamed(AnchorNames.monitor(thread)));
	}

	// ------------------------------------------------------------------ streets

	private void street(Street s) {
		int f = VillageTown.floorY(s.level);
		Mat m = DISTRICTS[VillageTown.district(s.i, s.j)];
		try (PlanBuilder.Canvas c = cell(streetGroup(s.i, s.j), streetGroup(s.i, s.j), s.i, s.j, f + 9)) {
			terrace(c, s.i, s.j, f, m, GRASS);
			Pen p = new Pen(c, Frame.of(s.i, s.j, Direction.NORTH, false));
			int ju = VillageTown.hash(s.i, s.j, 1) % 7 - 3;
			int jv = VillageTown.hash(s.i, s.j, 2) % 7 - 3;
			boolean[][] lane = new boolean[VillageTown.CELL][VillageTown.CELL];
			Set<Direction> edges = opened(s.i, s.j);
			int streetsMet = 0;
			for (Direction d : edges) {
				int ni = s.i + d.getStepX();
				int nj = s.j + d.getStepZ();
				if (town.street(ni, nj) != null || VillageTown.hub(ni, nj)) {
					streetsMet++;
				}
				// quadratic Bézier from the jittered centre to the edge's middle, arriving square to the edge
				double ex = d.getStepX() * HALF;
				double ez = d.getStepZ() * HALF;
				double kx = d.getStepX() * (HALF - 5);
				double kz = d.getStepZ() * (HALF - 5);
				for (int t = 0; t <= 60; t++) {
					double q = t / 60.0;
					double x = (1 - q) * (1 - q) * ju + 2 * (1 - q) * q * kx + q * q * ex;
					double z = (1 - q) * (1 - q) * jv + 2 * (1 - q) * q * kz + q * q * ez;
					paintDisc(lane, x, z, 2.3);
				}
			}
			boolean square = streetsMet >= 3;
			if (square) {
				paintDisc(lane, ju, jv, 5.5);
			}
			for (int v = -HALF; v <= HALF; v++) {
				for (int u = -HALF; u <= HALF; u++) {
					if (lane[u + HALF][v + HALF]) {
						int hsh = VillageTown.hash(s.i * 31 + u, s.j * 31 + v, 3) % 5;
						p.set(u, f, v, square && dist(u, v, ju, jv) <= 5.5 ? (Math.floorMod(u + v, 2) == 0 ? m.retain() : m.path()) : hsh == 0 ? m.path2() : m.path());
					}
				}
			}
			if (square) {
				fountain(p, ju, f, jv);
				b.anchor(Anchor.at("walk_square_" + s.i + "_" + s.j, p.at(ju + 3, f + 1, jv), Direction.WEST));
				for (int sx = -1; sx <= 1; sx += 2) {
					int lu = ju + 4 * sx;
					int lv = jv + 4 * sx;
					if (Math.abs(lu) < HALF - 1 && Math.abs(lv) < HALF - 1) {
						p.decor(lu, f + 1, lv, kit().gardenLantern());
					}
				}
			}
			// lanterns along the lanes, then trees, bushes and flowers away from them
			for (int v = -HALF + 2; v <= HALF - 2; v++) {
				for (int u = -HALF + 2; u <= HALF - 2; u++) {
					if (!lane[u + HALF][v + HALF] && Math.floorMod(u * 3 + v * 5 + s.i + s.j, 11) == 0 && nextToLane(lane, u, v)) {
						p.decor(u, f + 1, v, kit().gardenLantern());
						lane[u + HALF][v + HALF] = true;
					}
				}
			}
			for (int k = 0; k < 12; k++) {
				int tu = VillageTown.hash(s.i, s.j, 10 + k) % 15 - 7;
				int tv = VillageTown.hash(s.i, s.j, 200 + k) % 15 - 7;
				if (clearOf(lane, tu, tv, 2)) {
					tree(p, tu, f, tv, m, 4 + VillageTown.hash(s.i, s.j, 300 + k) % 3);
					lane[tu + HALF][tv + HALF] = true;
				}
			}
			for (int k = 0; k < 10; k++) {
				int bu = VillageTown.hash(s.i, s.j, 80 + k) % 17 - 8;
				int bv = VillageTown.hash(s.i, s.j, 90 + k) % 17 - 8;
				if (clearOf(lane, bu, bv, 1)) {
					p.set(bu, f + 1, bv, m.hedge());
					if (k % 3 == 0) {
						p.set(bu, f + 2, bv, m.hedge());
					}
					lane[bu + HALF][bv + HALF] = true;
				}
			}
			for (int k = 0; k < 24; k++) {
				int fu = VillageTown.hash(s.i, s.j, 40 + k) % 17 - 8;
				int fv = VillageTown.hash(s.i, s.j, 120 + k) % 17 - 8;
				if (clearOf(lane, fu, fv, 1)) {
					BlockState flower = switch (k % 4) {
						case 0 -> Blocks.POPPY.defaultBlockState();
						case 1 -> Blocks.DANDELION.defaultBlockState();
						case 2 -> Blocks.CORNFLOWER.defaultBlockState();
						default -> Blocks.SHORT_GRASS.defaultBlockState();
					};
					p.decor(fu, f + 1, fv, flower);
				}
			}
			borders(c, s.i, s.j, f, m.retain(), false, 2);
			stairsDown(c, s.i, s.j, f, m);
		}
	}

	private static double dist(int u, int v, double cu, double cv) {
		return Math.hypot(u - cu, v - cv);
	}

	private static void paintDisc(boolean[][] lane, double x, double z, double r) {
		int n = VillageTown.CELL;
		for (int v = -HALF; v <= HALF; v++) {
			for (int u = -HALF; u <= HALF; u++) {
				if (Math.hypot(u - x, v - z) <= r) {
					lane[u + HALF][v + HALF] = true;
				}
			}
		}
	}

	private static boolean nextToLane(boolean[][] lane, int u, int v) {
		return lane[u + 1 + HALF][v + HALF] || lane[u - 1 + HALF][v + HALF] || lane[u + HALF][v + 1 + HALF] || lane[u + HALF][v - 1 + HALF];
	}

	private static boolean clearOf(boolean[][] lane, int u, int v, int r) {
		if (Math.abs(u) > HALF - 3 || Math.abs(v) > HALF - 3) {
			return false;
		}
		for (int dv = -r; dv <= r; dv++) {
			for (int du = -r; du <= r; du++) {
				int uu = u + du;
				int vv = v + dv;
				if (Math.abs(uu) <= HALF && Math.abs(vv) <= HALF && lane[uu + HALF][vv + HALF]) {
					return false;
				}
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ plots

	/** A free plot whose street is built: an orchard with crops behind a hedge. */
	private void garden(Plot pl) {
		int f = VillageTown.floorY(pl.level());
		Mat m = DISTRICTS[VillageTown.district(pl.i(), pl.j())];
		try (PlanBuilder.Canvas c = cell(plotGroup(pl.rank()), plotGroup(pl.rank()), pl.i(), pl.j(), f + 9)) {
			terrace(c, pl.i(), pl.j(), f, m, GRASS);
			Pen p = new Pen(c, Frame.of(pl.i(), pl.j(), pl.front(), false));
			for (int v = -8; v <= 8; v++) {
				for (int u = -8; u <= 8; u++) {
					if (v % 4 == 0 && Math.abs(u) <= 7) {
						continue;
					}
					if (v > -6 && (u + 9) % 6 < 4) {
						p.set(u, f, v, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
						p.decor(u, f + 1, v, Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 3 + Math.floorMod(u * 7 + v, 5)));
					}
				}
			}
			for (int k = -1; k <= 1; k += 2) {
				tree(p, 7 * k, f, -7, m, 4);
			}
			borders(c, pl.i(), pl.j(), f, m.hedge(), true, 1);
		}
	}

	/** Roof and height tier of a hamlet's main house: grows with its threads. */
	static int tier(Hamlet h) {
		return h.threads() <= 4 ? 0 : h.threads() <= 9 ? 1 : 2;
	}

	/**
	 * A project's hamlet. Local frame: the street in front ({@code v = -10}), the gate in the middle.
	 * Main house {@code u -6..6, v -7..2} (desks 0..5), annex B behind it on one side ({@code u -9..-2,
	 * v 3..9}, desks 6..9, through the main house's back door), annex C on the other side
	 * ({@code u 0..9, v 3..9}, desks 10..13, from the side yard). Mirrored on every other plot.
	 */
	private void hamlet(Plot pl, Hamlet h, VillageLayout layout) {
		int f = VillageTown.floorY(pl.level());
		int district = VillageTown.district(pl.i(), pl.j());
		Mat m = DISTRICTS[district];
		boolean mirror = VillageTown.hash(pl.i(), pl.j(), 5) % 2 == 1;
		int variant = VillageTown.hash(pl.i(), pl.j(), 6) % 3;
		int tier = tier(h);
		int mainH = 5 + tier;
		try (PlanBuilder.Canvas c = cell("plot_" + pl.rank(), plotGroup(pl.rank()), pl.i(), pl.j(), f + 22)) {
			terrace(c, pl.i(), pl.j(), f, m, GRASS);
			Pen p = new Pen(c, Frame.of(pl.i(), pl.j(), pl.front(), mirror));
			// paths: gate to the front door, round to annex C
			p.fill(-1, f, -10, 1, f, -8, m.path());
			if (h.annexC()) {
				p.fill(-1, f, -9, 8, f, -8, m.path());
				p.fill(7, f, -8, 9, f, 2, m.path2());
			}
			// trees first: the houses overwrite whatever leaf reaches their walls
			tree(p, -8, f, -8, m, 4 + variant);
			if (!h.annexC()) {
				tree(p, 5, f, 6, m, 5);
			}
			// main house
			Roof mainRoof = tier == 2 ? Roof.HIP : variant == 0 ? Roof.GABLE_V : Roof.GABLE_U;
			house(p, -6, -7, 6, 2, f, mainH, m, mainRoof, variant);
			door(p, 0, -7, f, 3);
			String l1 = h.label().length() <= SignTags.LINE ? h.label() : h.label().substring(0, SignTags.LINE);
			String l2 = h.label().length() <= SignTags.LINE ? "" : h.label().substring(SignTags.LINE);
			List<Hamlet> mine = layout.hamletsOf(h.projectKey());
			int nth = mine.indexOf(h);
			sign(p, 0, f + 5, -8, Direction.NORTH, HqStyle.envColor(h.envKey().isEmpty() ? h.projectKey() : h.envKey()), l1, l2,
					nth > 0 ? "#" + (nth + 1) : "", h.envLabel());
			// a coloured runner from the door to the back
			DyeColor accent = HqStyle.envColor(h.envKey().isEmpty() ? h.projectKey() : h.envKey());
			int[][] mainDesks = {{-4, -6, 0}, {4, -6, 0}, {0, 1, 1}, {4, 1, 1}, {-5, -2, 2}, {5, -2, 3}};
			for (int s = 0; s < VillageLayout.MAIN_DESKS; s++) {
				int[] d = mainDesks[s];
				Direction face = switch (d[2]) {
					case 0 -> Direction.NORTH;
					case 1 -> Direction.SOUTH;
					case 2 -> Direction.WEST;
					default -> Direction.EAST;
				};
				desk(p, d[0], f + 1, d[1], face, h.threadAt(s), pl.rank(), s, f);
			}
			p.decor(-2, f + 1, -3, Blocks.CARPET.pick(accent).defaultBlockState());
			p.decor(2, f + 1, -3, Blocks.CARPET.pick(accent).defaultBlockState());
			if (tier >= 1) {
				// chimney through the roof
				for (int y = f + 1; y <= f + mainH + 8; y++) {
					p.set(-6, y, 2, Blocks.BRICKS.defaultBlockState());
				}
			}
			if (tier == 2) {
				// a square tower on the front corner, above the roof line
				int t0 = f + mainH + 1;
				for (int y = t0; y <= t0 + 6; y++) {
					for (int u = 4; u <= 6; u++) {
						for (int v = -7; v <= -5; v++) {
							boolean corner = (u == 4 || u == 6) && (v == -7 || v == -5);
							p.set(u, y, v, corner ? p.log(m.post(), Direction.Axis.Y) : y >= t0 + 4 ? AIR : m.wall());
						}
					}
				}
				p.fill(4, t0 + 7, -7, 6, t0 + 7, -5, m.ridge());
				p.set(5, t0 + 8, -6, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false));
			}
			// annex B
			if (h.annexB()) {
				house(p, -9, 3, -2, 9, f, 4, m, m.flatAnnex() ? Roof.FLAT : variant == 1 ? Roof.GABLE_U : Roof.GABLE_V, variant);
				passage(p, -4, 2, f);
				int[][] bd = {{-7, 8, 1}, {-4, 8, 1}, {-8, 6, 2}, {-3, 6, 3}};
				for (int s = 0; s < VillageLayout.ANNEX_B_DESKS; s++) {
					int[] d = bd[s];
					desk(p, d[0], f + 1, d[1], d[2] == 1 ? Direction.SOUTH : d[2] == 2 ? Direction.WEST : Direction.EAST,
							h.threadAt(VillageLayout.MAIN_DESKS + s), pl.rank(), VillageLayout.MAIN_DESKS + s, f);
				}
			} else {
				// vegetable patch
				for (int v = 4; v <= 8; v += 2) {
					for (int u = -8; u <= -3; u++) {
						p.set(u, f, v, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
						p.decor(u, f + 1, v, Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 5));
					}
				}
			}
			// annex C
			if (h.annexC()) {
				house(p, 0, 3, 9, 9, f, 4, m, m.flatAnnex() ? Roof.FLAT : variant == 2 ? Roof.GABLE_V : Roof.GABLE_U, variant);
				door(p, 8, 3, f, 1);
				int[][] cd = {{2, 8, 1}, {6, 8, 1}, {1, 6, 2}, {4, 4, 0}};
				for (int s = 0; s < VillageLayout.ANNEX_C_DESKS; s++) {
					int[] d = cd[s];
					Direction face = d[2] == 0 ? Direction.NORTH : d[2] == 1 ? Direction.SOUTH : Direction.WEST;
					int slot = VillageLayout.MAIN_DESKS + VillageLayout.ANNEX_B_DESKS + s;
					desk(p, d[0], f + 1, d[1], face, h.threadAt(slot), pl.rank(), slot, f);
				}
			}
			// left side yard: a well
			p.set(-8, f + 1, -2, Blocks.STONE_BRICKS.defaultBlockState());
			p.set(-8, f + 1, 0, Blocks.STONE_BRICKS.defaultBlockState());
			p.set(-8, f + 1, -1, WATER);
			p.set(-8, f, -1, Blocks.STONE_BRICKS.defaultBlockState());
			p.set(-9, f + 1, -1, Blocks.STONE_BRICKS.defaultBlockState());
			p.set(-7, f + 1, -1, Blocks.STONE_BRICKS.defaultBlockState());
			borders(c, pl.i(), pl.j(), f, m.hedge(), true, 1);
			// gate posts with lanterns
			for (int s = -1; s <= 1; s += 2) {
				p.set(2 * s, f + 1, -HALF, log(m.post(), Direction.Axis.Y));
				p.set(2 * s, f + 2, -HALF, log(m.post(), Direction.Axis.Y));
				p.decor(2 * s, f + 3, -HALF, kit().lantern());
			}
			// anchors: the door (just inside), the middle of the main house
			Direction in = p.f.dir(Direction.SOUTH);
			Anchor door = Anchor.at(AnchorNames.module(pl.rank()), p.at(0, f + 1, -6), in);
			Anchor walk = Anchor.at(AnchorNames.walkModule(pl.rank()), p.at(0, f + 1, -3), in);
			b.anchor(door);
			b.anchor(walk);
			if (nth == 0) {
				b.anchor(door.renamed(AnchorNames.wing(h.projectKey())));
				b.anchor(walk.renamed(AnchorNames.walk(h.projectKey())));
			}
		}
	}

	/** Every group of a plan with its plot rank (plots only), for tests and tools. */
	static Map<String, Integer> plotGroups(VillageLayout layout) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (Hamlet h : layout.hamlets()) {
			out.put(plotGroup(h.plot()), h.plot());
		}
		return out;
	}
}
