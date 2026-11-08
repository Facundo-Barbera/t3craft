package dev.t3craft.hq;

import static dev.t3craft.hq.TerraceTown.HALF;
import static dev.t3craft.hq.TerraceTown.INNER;

import dev.t3craft.hq.TerraceTown.Bridge;
import dev.t3craft.hq.TerraceTown.Cell;
import dev.t3craft.hq.TerraceTown.Kind;
import dev.t3craft.hq.VillageGenerator.Mat;
import java.util.ArrayList;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * The "Acrópolis en terrazas" HQ (layout {@code hybrid-terraces}): {@link HqLayout} (encoded by
 * {@link TerraceStyle}) in, {@link BuildPlan} out; pure and deterministic.
 *
 * <p>Every built {@link TerraceTown} cell is one module and one build group, the whole
 * {@value TerraceTown#CELL}×{@value TerraceTown#CELL} column from {@code y = 0} to its roofs: the
 * terrace body (retaining walls, fill) up to its level, its promenade band, what stands on it, the
 * stairs it carves down to lower neighbours and the bridges that cross it.
 * <ul>
 *   <li>{@code hub}: the great plaza with the hologram tholos (free column, beacon under the dome);
 *   {@code temple}: the monumental hall with a stepped roof (attention board, benches);
 *   {@code stoa_w}/{@code stoa_e}: the colonnades with the kanban wall and the big screen;
 *   {@code propylaea}: the gateway (spawn) above the {@code forecourt} fountain.</li>
 *   <li>Special buildings, each on its own terrace: {@code archive} (library-hermitage with the
 *   archive terminal), {@code guard}, {@code tavern}, {@code lonja} and the {@code mill} on its sky
 *   island.</li>
 *   <li>{@code plot_<rank>}: a project's barrio (main hall, annex, annex tower round a patio) or a
 *   garden while the solar is free but on a path; {@code bridge_<i>_<j>}: the garden under a bridge.</li>
 * </ul>
 * Light: after drawing, every cell an NPC can reach from the spawn ({@link PlanNav}) gets block
 * light ≥ {@value #MIN_LIGHT} ({@link TerraceLight}) from lights embedded in the floor (froglights
 * outdoors, shroomlights indoors), placed on a regular grid where possible. No loose lanterns.
 */
public final class TerraceGenerator {
	/** Layout and style id. */
	public static final String ID = "hybrid-terraces";
	/** Minimum block light on every walkable cell. */
	static final int MIN_LIGHT = 10;

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState GLASS = Blocks.GLASS_PANE.defaultBlockState();
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();
	private static final BlockState QUARTZ = Blocks.QUARTZ_BRICKS.defaultBlockState();
	private static final BlockState SMOOTH_QUARTZ = Blocks.SMOOTH_QUARTZ.defaultBlockState();
	private static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
	private static final BlockState ANDESITE = Blocks.POLISHED_ANDESITE.defaultBlockState();
	private static final BlockState STONE_BRICKS = Blocks.STONE_BRICKS.defaultBlockState();
	private static final BlockState MOSSY = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
	private static final BlockState TUFF_BRICKS = Blocks.TUFF_BRICKS.defaultBlockState();
	private static final BlockState BLACKSTONE = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	private static final BlockState PARAPET = Blocks.STONE_BRICK_WALL.defaultBlockState();
	private static final BlockState RAIL = Blocks.CHERRY_FENCE.defaultBlockState();
	private static final BlockState LIGHT_OUT = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
	private static final BlockState LIGHT_IN = Blocks.SHROOMLIGHT.defaultBlockState();
	private static final BlockState CHERRY_LEAVES = Blocks.CHERRY_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);

	private final HqStyle style;
	private final Map<Long, BlockState> vox = new HashMap<>();
	private final Map<Long, CompoundTag> bes = new HashMap<>();
	private final Set<Long> decor = new HashSet<>();
	/** Floor blocks a light may replace: true indoors (shroomlight), false outdoors (froglight). */
	private final Map<Long, Boolean> floorable = new HashMap<>();
	private final List<Anchor> anchors = new ArrayList<>();
	private final List<Workstation> stations = new ArrayList<>();
	private TerraceTown town;
	private Map<Long, Cell> built;
	private BlockPos spawn = BlockPos.ZERO;

	TerraceGenerator(HqStyle style) {
		this.style = style;
	}

	public TerraceGenerator() {
		this(HqStyles.forLayout(ID));
	}

	private static HqKit kit() {
		return HqKit.active();
	}

	private BlockState role(HqStyle.Role r) {
		return style.palette().get(r);
	}

	// ------------------------------------------------------------------ voxel world

	private void set(int x, int y, int z, BlockState s) {
		if (y < 0 || y > TerraceTown.TOP_Y || !built.containsKey(TerraceTown.key(TerraceTown.cellOf(x), TerraceTown.cellOf(z)))) {
			return;
		}
		long k = BlockPos.asLong(x, y, z);
		vox.put(k, s);
		bes.remove(k);
		decor.remove(k);
		floorable.remove(k);
	}

	private void floor(int x, int y, int z, BlockState s, boolean interior) {
		set(x, y, z, s);
		long k = BlockPos.asLong(x, y, z);
		if (vox.containsKey(k)) {
			floorable.put(k, interior);
		}
	}

	private void decor(int x, int y, int z, BlockState s, @Nullable CompoundTag be) {
		set(x, y, z, s);
		long k = BlockPos.asLong(x, y, z);
		if (vox.containsKey(k)) {
			decor.add(k);
			if (be != null) {
				bes.put(k, be);
			}
		}
	}

	private @Nullable BlockState get(int x, int y, int z) {
		return vox.get(BlockPos.asLong(x, y, z));
	}

	private static BlockState stairs(Block b, Direction facing, Half half) {
		return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing).setValue(BlockStateProperties.HALF, half);
	}

	private static BlockState slab(Block b, SlabType t) {
		return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, t);
	}

	private static BlockState log(Block b, Direction.Axis a) {
		return b.defaultBlockState().setValue(BlockStateProperties.AXIS, a);
	}

	/** Drawing in a cell's local frame: {@code u} to the right, {@code v} away from the front. */
	private final class P {
		final int cx;
		final int cz;
		final Direction front;

		P(int cx, int cz, Direction front) {
			this.cx = cx;
			this.cz = cz;
			this.front = front;
		}

		Direction right() {
			return front.getClockWise();
		}

		int x(int u, int v) {
			return cx + u * right().getStepX() + v * front.getOpposite().getStepX();
		}

		int z(int u, int v) {
			return cz + u * right().getStepZ() + v * front.getOpposite().getStepZ();
		}

		BlockPos at(int u, int y, int v) {
			return new BlockPos(x(u, v), y, z(u, v));
		}

		void set(int u, int y, int v, BlockState s) {
			TerraceGenerator.this.set(x(u, v), y, z(u, v), s);
		}

		void floor(int u, int y, int v, BlockState s, boolean interior) {
			TerraceGenerator.this.floor(x(u, v), y, z(u, v), s, interior);
		}

		void decor(int u, int y, int v, BlockState s) {
			TerraceGenerator.this.decor(x(u, v), y, z(u, v), s, null);
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

		Direction dir(Direction local) {
			return switch (local) {
				case NORTH -> front;
				case SOUTH -> front.getOpposite();
				case EAST -> right();
				case WEST -> right().getOpposite();
				default -> local;
			};
		}

		BlockState stairs(Block b, Direction local) {
			return TerraceGenerator.stairs(b, dir(local), Half.BOTTOM);
		}

		BlockState log(Block b, Direction.Axis local) {
			return TerraceGenerator.log(b, switch (local) {
				case X -> right().getAxis();
				case Z -> front.getAxis();
				default -> Direction.Axis.Y;
			});
		}

		void sign(int u, int y, int v, Direction local, DyeColor color, String... lines) {
			BlockState s = Blocks.CHERRY_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, dir(local));
			TerraceGenerator.this.decor(x(u, v), y, z(u, v), s, SignTags.sign(color, true, lines));
		}
	}

	private P pen(Cell c, Direction front) {
		return new P(c.cx(), c.cz(), front);
	}

	// ------------------------------------------------------------------ generation

	public BuildPlan generate(HqLayout layout) {
		town = TerraceTown.get();
		vox.clear();
		bes.clear();
		decor.clear();
		floorable.clear();
		anchors.clear();
		stations.clear();
		Map<Integer, List<HqLayout.Module>> byPlot = new TreeMap<>();
		for (HqLayout.Module m : layout.modules()) {
			byPlot.computeIfAbsent(m.index() / 4, k -> new ArrayList<>()).add(m);
		}
		List<Cell> cells = town.builtCells(byPlot.keySet());
		built = TerraceTown.byKey(cells);
		for (Cell c : cells) {
			if (c.kind.island()) {
				island(c);
			} else {
				terrace(c);
			}
		}
		for (Cell c : cells) {
			switch (c.kind) {
				case PLAZA -> plaza(c);
				case TEMPLE -> temple(c);
				case STOA_KANBAN -> stoaKanban(c);
				case STOA_SCREEN -> stoaScreen(c, layout);
				case PROPYLAEA -> propylaea(c);
				case FORECOURT -> forecourt(c);
				case LIBRARY -> library(c, layout);
				case GUARD -> guard(c);
				case TAVERN -> tavern(c);
				case LONJA -> lonja(c);
				case MILL_ISLAND -> mill(c);
				case BRIDGE -> bridgeGarden(c);
				case ISLAND_PLOT, PLOT -> {
					List<HqLayout.Module> mods = byPlot.get(c.rank);
					if (mods != null) {
						barrio(c, mods);
					} else {
						garden(c);
					}
				}
			}
		}
		for (Bridge br : town.bridges) {
			if (built.containsKey(br.to().key())) {
				bridge(br);
			}
		}
		for (Cell c : cells) {
			if (!c.kind.island()) {
				edges(c);
			}
		}
		light();
		return flush(cells);
	}

	private static String moduleId(Cell c) {
		return switch (c.kind) {
			case PLAZA -> "hub";
			case TEMPLE -> "temple";
			case STOA_KANBAN -> "stoa_w";
			case STOA_SCREEN -> "stoa_e";
			case PROPYLAEA -> "propylaea";
			case FORECOURT -> "forecourt";
			case LIBRARY -> HqGenerator.GROUP_ARCHIVE;
			case GUARD -> "guard";
			case TAVERN -> "tavern";
			case LONJA -> "lonja";
			case MILL_ISLAND -> "mill";
			case BRIDGE -> "bridge_" + c.i + "_" + c.j;
			case ISLAND_PLOT, PLOT -> plotGroup(c.rank);
		};
	}

	/** Build group of a solar. */
	public static String plotGroup(int rank) {
		return "plot_" + rank;
	}

	private BuildPlan flush(List<Cell> cells) {
		Map<Long, Integer> tops = new HashMap<>();
		for (long k : vox.keySet()) {
			int x = BlockPos.getX(k);
			int z = BlockPos.getZ(k);
			tops.merge(TerraceTown.key(TerraceTown.cellOf(x), TerraceTown.cellOf(z)), BlockPos.getY(k), Math::max);
		}
		PlanBuilder b = new PlanBuilder();
		for (Cell c : cells) {
			String id = moduleId(c);
			b.group(id);
			int top = Math.max(tops.getOrDefault(c.key(), 0), 0);
			Box box = new Box(c.cx() - HALF, 0, c.cz() - HALF, c.cx() + HALF, top, c.cz() + HALF);
			try (PlanBuilder.Canvas cv = b.module(id, box, List.of())) {
				for (int y = box.minY(); y <= box.maxY(); y++) {
					for (int z = box.minZ(); z <= box.maxZ(); z++) {
						for (int x = box.minX(); x <= box.maxX(); x++) {
							long k = BlockPos.asLong(x, y, z);
							BlockState s = vox.get(k);
							if (s == null) {
								continue;
							}
							BlockPos p = new BlockPos(x, y, z);
							if (decor.contains(k)) {
								cv.decor(p, s, bes.get(k));
							} else {
								cv.set(p, s);
							}
						}
					}
				}
			}
		}
		b.group(null);
		for (Anchor a : anchors) {
			b.anchor(a);
		}
		for (Workstation w : stations) {
			b.workstation(w);
		}
		return b.build();
	}

	// ------------------------------------------------------------------ light

	/** Lights every cell NPCs can reach from the spawn to at least {@link #MIN_LIGHT}. */
	private void light() {
		PlanShapes.resolve(vox);
		PlanNav nav = new PlanNav(vox::get);
		List<BlockPos> targets = new ArrayList<>(nav.reachable(spawn));
		targets.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
		TerraceLight pl = new TerraceLight(vox::get);
		pl.addAll(vox);
		for (BlockPos t : targets) {
			if (pl.at(t) >= MIN_LIGHT) {
				continue;
			}
			List<long[]> cands = new ArrayList<>();
			for (int dy = 0; dy <= 3; dy++) {
				for (int dz = -5; dz <= 5; dz++) {
					for (int dx = -5; dx <= 5; dx++) {
						int m = Math.abs(dx) + Math.abs(dz) + dy;
						if (m > 15 - MIN_LIGHT) {
							continue;
						}
						int x = t.getX() + dx;
						int y = t.getY() - dy;
						int z = t.getZ() + dz;
						long k = BlockPos.asLong(x, y, z);
						if (!floorable.containsKey(k)) {
							continue;
						}
						boolean grid = Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0;
						// score in tenths: distance, minus a bonus for the regular grid
						cands.add(new long[] {m * 10L - (grid ? 15 : 0), k});
					}
				}
			}
			cands.sort(Comparator.<long[]>comparingLong(a -> a[0]).thenComparingLong(a -> a[1]));
			int tries = 0;
			for (long[] cnd : cands) {
				if (tries++ >= 8) {
					break;
				}
				long k = cnd[1];
				boolean interior = floorable.remove(k);
				vox.put(k, interior ? LIGHT_IN : LIGHT_OUT);
				pl.source(k, 15);
				if (pl.at(t) >= MIN_LIGHT) {
					break;
				}
			}
		}
	}

	// ------------------------------------------------------------------ terrain

	private static boolean band(int u, int v) {
		return Math.abs(u) > INNER || Math.abs(v) > INNER;
	}

	private static BlockState retain(Cell c, int y) {
		if (c.ring <= 1 || c.kind == Kind.FORECOURT) {
			return y % 4 == 3 ? ANDESITE : STONE_BRICKS;
		}
		return y % 4 == 3 ? TUFF_BRICKS : (y + c.i + c.j) % 7 == 0 ? MOSSY : STONE_BRICKS;
	}

	/** The paving of the acropolis, by world position. */
	private static BlockState acroPaving(int x, int z) {
		int r = Math.max(Math.abs(Math.floorMod(x + HALF, TerraceTown.CELL) - HALF), Math.abs(Math.floorMod(z + HALF, TerraceTown.CELL) - HALF));
		if (r == HALF) {
			return CALCITE;
		}
		return (x + z & 1) == 0 ? ANDESITE : Blocks.POLISHED_DIORITE.defaultBlockState();
	}

	/** A terrace cell: body up to its floor, retaining walls on its border, paving everywhere. */
	private void terrace(Cell c) {
		int f = c.floor();
		P p = pen(c, Direction.NORTH);
		Mat m = mat(c);
		for (int y = 0; y < f; y++) {
			for (int v = -HALF; v <= HALF; v++) {
				for (int u = -HALF; u <= HALF; u++) {
					boolean edge = Math.abs(u) == HALF || Math.abs(v) == HALF;
					p.set(u, y, v, edge ? retain(c, y) : DIRT);
				}
			}
		}
		boolean acro = c.ring <= 1 || c.kind == Kind.FORECOURT;
		for (int v = -HALF; v <= HALF; v++) {
			for (int u = -HALF; u <= HALF; u++) {
				BlockState s;
				if (acro) {
					s = acroPaving(p.x(u, v), p.z(u, v));
				} else if (band(u, v)) {
					s = (Math.abs(u) == HALF || Math.abs(v) == HALF) ? m.path2() : m.path();
				} else {
					s = GRASS;
				}
				p.floor(u, f, v, s, false);
			}
		}
	}

	/** A sky island: a rounded platform with a tapering rocky underside, a railing on the rim. */
	private void island(Cell c) {
		int f = c.floor();
		P p = pen(c, Direction.NORTH);
		for (int k = 1; k <= 9; k++) {
			int e = HALF - 1 - k;
			for (int v = -e; v <= e; v++) {
				for (int u = -e; u <= e; u++) {
					if (Math.abs(u) + Math.abs(v) > 22 - 2 * k) {
						continue;
					}
					BlockState s = k == 1 ? DIRT : (u * 3 + v + k) % 5 == 0 ? Blocks.TUFF.defaultBlockState()
							: (u + v * 2 + k) % 7 == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState();
					p.set(u, f - k, v, s);
				}
			}
		}
		Direction bridgeSide = c.parent == null ? null : TerraceTown.direction(c.i, c.j, c.parent.i, c.parent.j);
		for (int v = -HALF; v <= HALF; v++) {
			for (int u = -HALF; u <= HALF; u++) {
				if (!onIsland(u, v)) {
					continue;
				}
				p.floor(u, f, v, band(u, v) ? acroPaving(p.x(u, v), p.z(u, v)) : GRASS, false);
				boolean rim = !onIsland(u + 1, v) || !onIsland(u - 1, v) || !onIsland(u, v + 1) || !onIsland(u, v - 1);
				if (rim && !bridgeGap(bridgeSide, u, v)) {
					p.set(u, f + 1, v, PARAPET);
				}
			}
		}
	}

	private static boolean onIsland(int u, int v) {
		return Math.abs(u) <= HALF - 1 && Math.abs(v) <= HALF - 1 && Math.abs(u) + Math.abs(v) <= 22;
	}

	/** True for the rim cells left open where the bridge lands (world-aligned frame). */
	private static boolean bridgeGap(@Nullable Direction side, int u, int v) {
		if (side == null) {
			return false;
		}
		int along = side.getStepX() * u + side.getStepZ() * v;
		int across = side.getStepX() != 0 ? v : u;
		return along >= HALF - 3 && Math.abs(across) <= 2;
	}

	/** Parapets towards lower or missing neighbours, monumental stairs down to a neighbour one level below. */
	private void edges(Cell c) {
		int f = c.floor();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			Cell n = built.get(TerraceTown.key(c.i + d.getStepX(), c.j + d.getStepZ()));
			P e = pen(c, d);
			int open = bridgeOpening(c, d) ? 2 : -1;
			if (n != null && TerraceTown.walkable(c, n)) {
				if (c.level == n.level + 1) {
					stairDown(c, e, f);
				}
				continue;
			}
			if (n != null && n.level > c.level && !n.kind.island()) {
				continue;
			}
			if (c.level == 0 && n == null) {
				continue;
			}
			for (int t = -HALF; t <= HALF; t++) {
				if (Math.abs(t) <= open) {
					continue;
				}
				if (get(e.x(t, -HALF), f + 1, e.z(t, -HALF)) == null || get(e.x(t, -HALF), f + 1, e.z(t, -HALF)).isAir()) {
					e.set(t, f + 1, -HALF, PARAPET);
				}
			}
		}
	}

	private boolean bridgeOpening(Cell c, Direction d) {
		for (Bridge b : town.bridges) {
			if (b.from() == c && b.dir() == d && built.containsKey(b.to().key())) {
				return true;
			}
		}
		return false;
	}

	/** A seven-wide stair carved in the band, down one level through the edge {@code e} faces. */
	private void stairDown(Cell c, P e, int f) {
		Block blk = c.ring <= 1 || c.kind == Kind.FORECOURT ? Blocks.QUARTZ_STAIRS : Blocks.STONE_BRICK_STAIRS;
		int w = 3;
		for (int k = 0; k < TerraceTown.STEP; k++) {
			int y = f - (TerraceTown.STEP - 1) + k;
			int v = -HALF + k;
			for (int t = -w; t <= w; t++) {
				e.set(t, y, v, e.stairs(blk, Direction.SOUTH));
				for (int a = y + 1; a <= f + 3; a++) {
					if (a > f + 1 && get(e.x(t, v), a, e.z(t, v)) != null && !get(e.x(t, v), a, e.z(t, v)).isAir()) {
						continue;
					}
					e.set(t, a, v, AIR);
				}
			}
			// balustrade on the cheeks where the drop beside the stair is two blocks or more; the top
			// rows stay open, so the promenade crosses the stair head
			if (k <= 1) {
				for (int s = -1; s <= 1; s += 2) {
					e.set(s * (w + 1), f + 1, v, PARAPET);
				}
			}
		}
		// lights embedded in the cheeks, one per side, half way down
		for (int s = -1; s <= 1; s += 2) {
			e.set(s * (w + 1), f - 1, -HALF + 1, LIGHT_OUT);
		}
	}

	private Mat mat(Cell c) {
		return VillageGenerator.DISTRICTS[VillageTown.district(c.i, c.j)];
	}

	private void tree(P p, int u, int f, int v, BlockState leaves, Block logBlock, int height) {
		p.floor(u, f, v, GRASS, false);
		for (int y = f + 1; y <= f + height; y++) {
			p.set(u, y, v, log(logBlock, Direction.Axis.Y));
		}
		for (int dy = -1; dy <= 1; dy++) {
			int r = dy == 1 ? 1 : 2;
			for (int dv = -r; dv <= r; dv++) {
				for (int du = -r; du <= r; du++) {
					if (Math.abs(du) == 2 && Math.abs(dv) == 2 || du == 0 && dv == 0 && dy < 1) {
						continue;
					}
					p.set(u + du, f + height + dy, v + dv, leaves);
				}
			}
		}
		p.set(u, f + height + 2, v, leaves);
	}

	// ------------------------------------------------------------------ acropolis

	private void plaza(Cell c) {
		int f = c.floor();
		P p = pen(c, Direction.NORTH);
		// rings of calcite round the tholos
		for (int v = -INNER; v <= INNER; v++) {
			for (int u = -INNER; u <= INNER; u++) {
				int r = Math.max(Math.abs(u), Math.abs(v));
				BlockState s = r <= 1 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : r == 5 || r == 9 ? CALCITE
						: (u + v & 1) == 0 ? ANDESITE : Blocks.POLISHED_DIORITE.defaultBlockState();
				p.floor(u, f, v, s, false);
			}
		}
		// the tholos: four columns, entablature, a stepped dome; beacon hanging under it over the free column
		for (int su = -1; su <= 1; su += 2) {
			for (int sv = -1; sv <= 1; sv += 2) {
				p.set(4 * su, f + 1, 4 * sv, QUARTZ);
				for (int y = f + 2; y <= f + 10; y++) {
					p.set(4 * su, y, 4 * sv, log(Blocks.QUARTZ_PILLAR, Direction.Axis.Y));
				}
			}
		}
		for (int v = -5; v <= 5; v++) {
			for (int u = -5; u <= 5; u++) {
				p.set(u, f + 11, v, Math.max(Math.abs(u), Math.abs(v)) == 5 ? QUARTZ : SMOOTH_QUARTZ);
			}
		}
		for (int r = 4; r >= 1; r--) {
			int y = f + 12 + (4 - r);
			for (int k = -r; k <= r; k++) {
				p.set(k, y, -r, p.stairs(Blocks.QUARTZ_STAIRS, Direction.SOUTH));
				p.set(k, y, r, p.stairs(Blocks.QUARTZ_STAIRS, Direction.NORTH));
				if (Math.abs(k) < r) {
					p.set(-r, y, k, p.stairs(Blocks.QUARTZ_STAIRS, Direction.EAST));
					p.set(r, y, k, p.stairs(Blocks.QUARTZ_STAIRS, Direction.WEST));
				}
			}
			for (int v = -r + 1; v <= r - 1; v++) {
				for (int u = -r + 1; u <= r - 1; u++) {
					p.set(u, y, v, SMOOTH_QUARTZ);
				}
			}
		}
		p.set(0, f + 16, 0, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
		int beaconY = f + HqGeometry.HOLOGRAM_TOP + 1;
		p.set(0, beaconY, 0, role(HqStyle.Role.BEACON));
		for (int y = beaconY + 1; y <= f + 10; y++) {
			p.set(0, y, 0, BLACKSTONE);
		}
		for (int y = f + 1; y <= f + HqGeometry.HOLOGRAM_TOP; y++) {
			p.set(0, y, 0, AIR);
		}
		// cherry planters on the plaza corners
		for (int su = -1; su <= 1; su += 2) {
			for (int sv = -1; sv <= 1; sv += 2) {
				p.decor(9 * su, f + 1, 9 * sv, kit().gardenPlanter());
			}
		}
		anchors.add(Anchor.at(AnchorNames.HUB, new BlockPos(0, f + 1, 0), Direction.SOUTH));
		anchors.add(new Anchor(AnchorNames.HOLOGRAM, 0.5, f + 5.0, 0.5, Direction.SOUTH, new BlockPos(0, f + 5, 0)));
		anchors.add(Anchor.at(AnchorNames.BEACON, new BlockPos(0, beaconY, 0), Direction.SOUTH));
		int w = 0;
		for (int sv = -1; sv <= 1; sv += 2) {
			for (int su = -1; su <= 1; su += 2) {
				anchors.add(Anchor.at(AnchorNames.walkHub(w++), new BlockPos(7 * su, f + 1, 7 * sv), Direction.SOUTH));
			}
		}
	}

	private static final int TEMPLE_WALL = 12;

	/** The temple: a 21×21 hall twelve blocks high under a stepped roof; attention board and benches inside. */
	private void temple(Cell c) {
		int f = c.floor();
		int cz = c.cz();
		P p = pen(c, Direction.NORTH);
		BlockState pil = log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y);
		// floor
		for (int v = -INNER + 1; v <= INNER - 1; v++) {
			for (int u = -INNER + 1; u <= INNER - 1; u++) {
				boolean accent = Math.abs(u) <= 1 || Math.abs(v) <= 1 && Math.abs(u) <= 6;
				p.floor(u, f, v, accent ? kit().floorAccent() : kit().floor(), true);
			}
		}
		// walls with cherry pilasters and tall windows
		for (int y = f + 1; y <= f + TEMPLE_WALL; y++) {
			for (int k = -INNER; k <= INNER; k++) {
				for (int[] w : new int[][] {{k, -INNER}, {k, INNER}, {-INNER, k}, {INNER, k}}) {
					boolean corner = Math.abs(w[0]) == INNER && Math.abs(w[1]) == INNER;
					boolean pilaster = Math.floorMod(k, 4) == 0;
					BlockState s = corner || pilaster ? pil : y == f + 1 ? ANDESITE : y == f + TEMPLE_WALL ? SMOOTH_QUARTZ : QUARTZ;
					if (!corner && !pilaster && y >= f + 4 && y <= f + 9 && Math.floorMod(k, 4) == 2 && w[1] != -INNER) {
						s = GLASS;
					}
					p.set(w[0], y, w[1], s);
				}
			}
		}
		for (int v = -INNER + 1; v <= INNER - 1; v++) {
			for (int u = -INNER + 1; u <= INNER - 1; u++) {
				p.fill(u, f + 1, v, u, f + TEMPLE_WALL, v, AIR);
			}
		}
		// ceiling and the stepped roof with a shrine on top
		for (int v = -INNER; v <= INNER; v++) {
			for (int u = -INNER; u <= INNER; u++) {
				p.set(u, f + TEMPLE_WALL + 1, v, Math.floorMod(u, 4) == 2 && Math.floorMod(v, 4) == 2 ? kit().wallPanel(true) : SMOOTH_QUARTZ);
			}
		}
		int y0 = f + TEMPLE_WALL + 2;
		for (int t = 0; t < 4; t++) {
			int e = INNER - 2 * t;
			for (int y = y0 + 2 * t; y <= y0 + 2 * t + 1; y++) {
				for (int v = -e; v <= e; v++) {
					for (int u = -e; u <= e; u++) {
						boolean rim = Math.max(Math.abs(u), Math.abs(v)) == e;
						p.set(u, y, v, rim ? (y == y0 + 2 * t + 1 ? SMOOTH_QUARTZ : QUARTZ) : STONE_BRICKS);
					}
				}
			}
		}
		int sy = y0 + 8;
		for (int y = sy; y <= sy + 3; y++) {
			for (int v = -2; v <= 2; v++) {
				for (int u = -2; u <= 2; u++) {
					boolean edge = Math.max(Math.abs(u), Math.abs(v)) == 2;
					boolean post = Math.abs(u) == 2 && Math.abs(v) == 2;
					p.set(u, y, v, post ? pil : edge ? (y == sy + 3 ? SMOOTH_QUARTZ : AIR) : AIR);
				}
			}
		}
		p.set(0, sy, 0, role(HqStyle.Role.BEACON));
		p.fill(-2, sy + 4, -2, 2, sy + 4, 2, slab(Blocks.DEEPSLATE_TILE_SLAB, SlabType.BOTTOM));
		p.set(0, sy + 5, 0, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		// door (south, onto the plaza)
		p.fill(-2, f + 1, INNER, 2, f + 6, INNER, AIR);
		for (int u = -2; u <= 2; u++) {
			p.floor(u, f, INNER, kit().floorAccent(), true);
		}
		p.sign(0, f + 8, INNER + 1, Direction.SOUTH, DyeColor.WHITE, "", "Templo", "HQ", "");
		// interior columns
		for (int su = -1; su <= 1; su += 2) {
			for (int v : new int[] {-2, 5}) {
				p.fill(5 * su, f + 1, v, 5 * su, f + TEMPLE_WALL, v, log(Blocks.QUARTZ_PILLAR, Direction.Axis.Y));
			}
		}
		// the needs-attention board on the north wall
		p.fill(-4, f + 1, -INNER, 4, f + 6, -INNER, p.log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X));
		p.fill(-3, f + 1, -INNER, 3, f + 5, -INNER, role(HqStyle.Role.BOARD));
		p.sign(0, f + 7, -INNER + 1, Direction.SOUTH, DyeColor.ORANGE, "", "Needs", "attention", "");
		int lamp = 0;
		for (int x = -3; x <= 3; x += 2) {
			p.set(x, f + 5, -INNER, role(HqStyle.Role.STATUS_LAMP));
			anchors.add(Anchor.face(AnchorNames.boardLamp(lamp++), new BlockPos(x, f + 5, cz - INNER), Direction.SOUTH));
		}
		anchors.add(Anchor.face(AnchorNames.BOARD, new BlockPos(0, f + 3, cz - INNER), Direction.SOUTH));
		anchors.add(Anchor.at(AnchorNames.BOARD_FRONT, new BlockPos(0, f + 1, cz - INNER + 1), Direction.NORTH));
		int slot = 0;
		for (int row = 2; row <= 3; row++) {
			for (int k = 0; k <= 6; k++) {
				int x = k == 0 ? 0 : (k % 2 == 1 ? (k + 1) / 2 : -(k / 2));
				anchors.add(Anchor.at(AnchorNames.boardSlot(slot++), new BlockPos(x, f + 1, cz - INNER + row), Direction.NORTH));
			}
		}
		// benches along both sides, backs to the walls
		int bench = 0;
		for (int su = -1; su <= 1; su += 2) {
			Direction back = su < 0 ? Direction.WEST : Direction.EAST;
			for (int v = -4; v <= 2; v++) {
				p.set(8 * su, f + 1, v, style.bench(back));
				anchors.add(Anchor.at(AnchorNames.bench(bench++), new BlockPos(7 * su, f + 1, cz + v), back.getOpposite()));
			}
		}
		anchors.add(Anchor.at(AnchorNames.ENTRANCE, new BlockPos(0, f + 1, cz + INNER), Direction.SOUTH));
	}

	/** West colonnade: the thread kanban wall, facing the plaza. */
	private void stoaKanban(Cell c) {
		int f = c.floor();
		int cx = c.cx();
		stoa(c, -1);
		HqGeometry.Surface wall = new HqGeometry.Surface(new BlockPos(cx - 5, f + 1, 4), Direction.NORTH, Direction.EAST, 9, 4);
		P p = pen(c, Direction.NORTH);
		for (int z = -5; z <= 5; z++) {
			for (int y = f + 1; y <= f + 5; y++) {
				if (Math.abs(z) == 5 || y == f + 5) {
					p.set(-5, y, z, Math.abs(z) == 5 ? log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y) : log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Z));
				}
			}
		}
		for (BlockPos q : wall.cells()) {
			set(q.getX(), q.getY(), q.getZ(), role(HqStyle.Role.SCREEN));
		}
		anchors.add(Anchor.face(AnchorNames.THREAD_WALL, wall.origin(), wall.facing()));
		anchors.add(Anchor.face(AnchorNames.THREAD_WALL_END, wall.origin().relative(wall.along(), 8).above(3), wall.facing()));
		anchors.add(Anchor.at(AnchorNames.THREAD_WALL_FRONT, new BlockPos(cx - 2, f + 1, 0), Direction.WEST));
		p.sign(-5, f + 6, 0, Direction.EAST, DyeColor.WHITE, "", "Kanban", "hilos", "");
	}

	/** East colonnade: the big screen, facing the plaza. */
	private void stoaScreen(Cell c, HqLayout layout) {
		int f = c.floor();
		int cx = c.cx();
		stoa(c, 1);
		P p = pen(c, Direction.NORTH);
		HqGeometry.Surface screen = new HqGeometry.Surface(new BlockPos(cx + 5, f + 2, -2), Direction.SOUTH, Direction.WEST, 5, 3);
		for (int z = -3; z <= 3; z++) {
			p.set(5, f + 1, z, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Z));
			p.set(5, f + 5, z, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Z));
		}
		for (int y = f + 2; y <= f + 4; y++) {
			p.set(5, y, -3, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y));
			p.set(5, y, 3, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y));
		}
		for (BlockPos q : screen.cells()) {
			set(q.getX(), q.getY(), q.getZ(), role(HqStyle.Role.SCREEN));
		}
		anchors.add(Anchor.face(AnchorNames.bigScreen("hub", 5, 3, hubBinding(layout)), screen.origin().above(2), screen.facing()));
		anchors.add(Anchor.at(AnchorNames.walkHub(4), new BlockPos(cx + 1, f + 1, 0), Direction.EAST));
		p.sign(5, f + 6, 0, Direction.WEST, DyeColor.WHITE, "", "Pantalla", "grande", "");
	}

	/**
	 * A colonnade along the plaza side of a stoa cell: back wall at {@code u = 6 * side}, columns at
	 * {@code u = -4 * side}, a flat roof; {@code side}: -1 west stoa, 1 east stoa.
	 */
	private void stoa(Cell c, int side) {
		int f = c.floor();
		P p = pen(c, Direction.NORTH);
		int back = 6 * side;
		int cols = -4 * side;
		for (int v = -9; v <= 9; v++) {
			for (int u = Math.min(back, cols); u <= Math.max(back, cols); u++) {
				p.floor(u, f, v, (u + v & 1) == 0 ? kit().floor() : kit().floorAccent(), false);
			}
			for (int y = f + 1; y <= f + 7; y++) {
				p.set(back, y, v, y == f + 1 ? ANDESITE : Math.floorMod(v, 3) == 0 ? log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y) : QUARTZ);
			}
			if (Math.floorMod(v, 3) == 0) {
				p.set(cols, f + 1, v, QUARTZ);
				for (int y = f + 2; y <= f + 6; y++) {
					p.set(cols, y, v, log(Blocks.QUARTZ_PILLAR, Direction.Axis.Y));
				}
			}
		}
		for (int v = -10; v <= 10; v++) {
			for (int u = Math.min(back, cols) - 1; u <= Math.max(back, cols) + 1; u++) {
				p.set(u, f + 7, v, SMOOTH_QUARTZ);
				boolean rim = v == -10 || v == 10 || u == Math.min(back, cols) - 1 || u == Math.max(back, cols) + 1;
				p.set(u, f + 8, v, rim ? QUARTZ : slab(Blocks.SMOOTH_QUARTZ_SLAB, SlabType.BOTTOM));
			}
		}
	}

	/** The gateway south of the plaza: two rows of columns under a flat roof; the spawn behind it. */
	private void propylaea(Cell c) {
		int f = c.floor();
		int cz = c.cz();
		P p = pen(c, Direction.NORTH);
		for (int u : new int[] {-8, -4, 4, 8}) {
			for (int v : new int[] {-4, 4}) {
				p.set(u, f + 1, v, QUARTZ);
				for (int y = f + 2; y <= f + 8; y++) {
					p.set(u, y, v, log(Blocks.QUARTZ_PILLAR, Direction.Axis.Y));
				}
			}
		}
		for (int v = -5; v <= 5; v++) {
			for (int u = -9; u <= 9; u++) {
				p.set(u, f + 9, v, Math.abs(v) == 5 || Math.abs(u) == 9 ? QUARTZ : SMOOTH_QUARTZ);
			}
		}
		for (int v = -4; v <= 4; v++) {
			for (int u = -8; u <= 8; u++) {
				p.set(u, f + 10, v, Math.abs(v) == 4 || Math.abs(u) == 8 ? p.stairs(Blocks.QUARTZ_STAIRS, Math.abs(v) == 4 ? (v < 0 ? Direction.SOUTH : Direction.NORTH)
						: (u < 0 ? Direction.EAST : Direction.WEST)) : SMOOTH_QUARTZ);
			}
		}
		for (int v = -5; v <= 5; v++) {
			for (int u = -2; u <= 2; u++) {
				p.floor(u, f, v, Math.abs(u) == 2 ? CALCITE : Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), false);
			}
		}
		p.sign(0, f + 9, -6, Direction.NORTH, DyeColor.WHITE, "", "Acrópolis", "HQ", "");
		for (int su = -1; su <= 1; su += 2) {
			p.decor(10 * su, f + 1, 9, kit().gardenPlanter());
		}
		spawn = new BlockPos(0, f + 1, cz + 8);
		anchors.add(Anchor.at(AnchorNames.SPAWN, spawn, Direction.NORTH));
		anchors.add(Anchor.at(AnchorNames.walkHub(5), new BlockPos(-4, f + 1, cz + 8), Direction.NORTH));
	}

	/** The forecourt below the propylaea: a fountain, four cherry trees. */
	private void forecourt(Cell c) {
		int f = c.floor();
		P p = pen(c, Direction.NORTH);
		for (int du = -2; du <= 2; du++) {
			for (int dv = -2; dv <= 2; dv++) {
				boolean rim = Math.abs(du) == 2 || Math.abs(dv) == 2;
				p.set(du, f, dv, STONE_BRICKS);
				p.set(du, f + 1, dv, rim ? QUARTZ : WATER);
			}
		}
		p.set(0, f + 1, 0, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
		p.set(0, f + 2, 0, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
		p.set(0, f + 3, 0, WATER);
		for (int su = -1; su <= 1; su += 2) {
			for (int sv = -1; sv <= 1; sv += 2) {
				tree(p, 7 * su, f, 7 * sv, CHERRY_LEAVES, Blocks.CHERRY_LOG, 4);
			}
		}
	}

	/** What the hub's big screen shows: the environment with the most barrios (ties: first key). */
	static String hubBinding(HqLayout layout) {
		Map<String, Integer> count = new TreeMap<>();
		for (HqLayout.Module m : layout.modules()) {
			if (m.index() % 4 == 0 && !m.envKey().isEmpty()) {
				count.merge(m.envKey(), 1, Integer::sum);
			}
		}
		String best = null;
		for (Map.Entry<String, Integer> e : count.entrySet()) {
			if (best == null || e.getValue() > count.get(best)) {
				best = e.getKey();
			}
		}
		return best == null ? "" : "env:" + best;
	}

	// ------------------------------------------------------------------ special buildings

	private void library(Cell c, HqLayout layout) {
		int f = c.floor();
		int cz = c.cz();
		int cx = c.cx();
		P p = pen(c, Direction.NORTH);
		Mat m = new Mat("ermita", CALCITE, ANDESITE, Blocks.STRIPPED_CHERRY_LOG, Blocks.DEEPSLATE_TILE_STAIRS, BLACKSTONE,
				Blocks.CHERRY_PLANKS.defaultBlockState(), ANDESITE, CALCITE, CHERRY_LEAVES, Blocks.CHERRY_LOG, CHERRY_LEAVES, STONE_BRICKS, MOSSY, false);
		house(p, -5, -7, 5, 7, f, 7, m, Roof.GABLE_V);
		door(p, 0, 7, f, 3);
		for (int v = -5; v <= 4; v++) {
			if (v == 0) {
				continue;
			}
			p.fill(-4, f + 1, v, -4, f + 3, v, Blocks.BOOKSHELF.defaultBlockState());
			p.fill(4, f + 1, v, 4, f + 3, v, Blocks.BOOKSHELF.defaultBlockState());
		}
		BlockPos t = new BlockPos(cx, f + 1, cz - 6);
		set(t.getX(), t.getY(), t.getZ(), role(HqStyle.Role.TERMINAL).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		anchors.add(Anchor.face(AnchorNames.ARCHIVE_TERMINAL, t, Direction.SOUTH));
		anchors.add(Anchor.at(AnchorNames.ARCHIVE, new BlockPos(cx, f + 1, cz - 4), Direction.NORTH));
		p.sign(0, f + 5, 8, Direction.SOUTH, DyeColor.WHITE, "", "Biblioteca", "archivo", (layout.archive().size() + layout.overflow().size()) + " hilos");
		// belfry over the front gable
		int top = f + 7 + 8;
		for (int y = f + 9; y <= top; y++) {
			for (int u = -1; u <= 1; u++) {
				for (int v = 5; v <= 7; v++) {
					boolean post = Math.abs(u) == 1 && (v == 5 || v == 7);
					p.set(u, y, v, post ? log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y) : y >= top - 1 ? AIR : CALCITE);
				}
			}
		}
		p.set(0, top - 1, 6, Blocks.BELL.defaultBlockState());
		p.fill(-1, top + 1, 5, 1, top + 1, 7, BLACKSTONE);
		// a small cherry grove behind the hermitage
		tree(p, -8, f, -8, CHERRY_LEAVES, Blocks.CHERRY_LOG, 4);
		tree(p, 8, f, -8, CHERRY_LEAVES, Blocks.CHERRY_LOG, 5);
	}

	private void guard(Cell c) {
		int f = c.floor();
		P p = pen(c, Direction.NORTH);
		Mat m = new Mat("guardia", Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.STRIPPED_DARK_OAK_LOG,
				Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState(), ANDESITE, CALCITE,
				CHERRY_LEAVES, Blocks.OAK_LOG, CHERRY_LEAVES, STONE_BRICKS, MOSSY, true);
		house(p, -5, -4, 5, 4, f, 6, m, Roof.FLAT);
		door(p, 0, 4, f, 3);
		for (int u = -5; u <= 5; u++) {
			p.set(u, f + 5, 4, Blocks.WOOL.pick(DyeColor.RED).defaultBlockState());
		}
		// the alert console on the back wall (a display, not a desk)
		p.fill(-1, f + 2, -3, 1, f + 3, -3, role(HqStyle.Role.SCREEN));
		p.fill(-1, f + 1, -3, 1, f + 1, -3, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
		p.sign(0, f + 4, 5, Direction.SOUTH, DyeColor.RED, "", "Guardia", "errores", "");
		// watch turret on the back corner, a brazier on top
		int r = f + 7;
		for (int y = r; y <= r + 6; y++) {
			for (int u = 3; u <= 5; u++) {
				for (int v = -4; v <= -2; v++) {
					boolean centre = u == 4 && v == -3;
					p.set(u, y, v, y >= r + 5 ? (!centre && (u + v & 1) == 0 ? m.wall() : AIR) : m.wall());
				}
			}
		}
		p.set(4, r + 5, -3, Blocks.CAMPFIRE.defaultBlockState());
	}

	private void tavern(Cell c) {
		int f = c.floor();
		P p = pen(c, Direction.NORTH);
		Mat m = VillageGenerator.DISTRICTS[2];
		house(p, -7, -5, 7, 5, f, 5, m, Roof.GABLE_U);
		door(p, 7, 0, f, 3);
		for (int v = -3; v <= 3; v++) {
			p.set(-6, f + 1, v, Blocks.BARREL.defaultBlockState());
			p.set(-6, f + 2, v, Blocks.BARREL.defaultBlockState());
		}
		for (int s = -1; s <= 1; s += 2) {
			p.set(2, f + 1, 2 * s, kit().desk(Direction.NORTH));
		}
		p.sign(8, f + 4, 0, Direction.EAST, DyeColor.LIME, "", "Taberna", "lounge", "");
		for (int y = f + 1; y <= f + 12; y++) {
			p.set(-7, y, 4, Blocks.BRICKS.defaultBlockState());
		}
		tree(p, 3, f, -8, m.leaves(), m.treeLog(), 4);
	}

	private void lonja(Cell c) {
		int f = c.floor();
		P p = pen(c, Direction.NORTH);
		for (int u = -8; u <= 8; u++) {
			for (int v = -5; v <= 5; v++) {
				p.floor(u, f, v, Math.abs(u) <= 1 ? kit().floorAccent() : kit().floor(), false);
			}
		}
		BlockState post = log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y);
		for (int u : new int[] {-8, -4, 0, 4, 8}) {
			for (int v = -5; v <= 5; v += 10) {
				p.fill(u, f + 1, v, u, f + 5, v, post);
			}
		}
		for (int u = -8; u <= 8; u++) {
			p.set(u, f + 6, -5, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X));
			p.set(u, f + 6, 5, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X));
		}
		for (int v = -4; v <= 4; v++) {
			p.set(-8, f + 6, v, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Z));
			p.set(8, f + 6, v, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Z));
		}
		gable(p, -8, -5, 8, 5, f + 6, Blocks.CHERRY_STAIRS, Blocks.CHERRY_PLANKS.defaultBlockState(), Blocks.CHERRY_PLANKS.defaultBlockState(), true, false);
		for (int s = -1; s <= 1; s += 2) {
			for (int u = 2; u <= 6; u++) {
				p.set(u * s, f + 1, -2, kit().desk(Direction.NORTH));
				p.set(u * s, f + 1, 2, kit().desk(Direction.SOUTH));
			}
		}
		p.set(0, f + 5, -5, post);
		p.sign(0, f + 5, -6, Direction.NORTH, DyeColor.LIGHT_BLUE, "", "Lonja", "review / PR", "");
	}

	private void mill(Cell c) {
		int f = c.floor();
		P p = pen(c, Direction.NORTH);
		Mat m = VillageGenerator.DISTRICTS[1];
		house(p, -3, -3, 3, 3, f, 11, m, Roof.HIP);
		door(p, 0, 3, f, 3);
		p.sign(0, f + 5, 4, Direction.SOUTH, DyeColor.YELLOW, "", "Molino", "programadas", "");
		int hy = f + 9;
		p.set(4, hy, 0, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
		for (int k = 1; k <= 5; k++) {
			p.set(5, hy, k, Blocks.SPRUCE_PLANKS.defaultBlockState());
			p.set(5, hy, -k, Blocks.SPRUCE_PLANKS.defaultBlockState());
			p.set(5, hy + k, 0, Blocks.SPRUCE_PLANKS.defaultBlockState());
			p.set(5, hy - k, 0, Blocks.SPRUCE_PLANKS.defaultBlockState());
			if (k >= 2) {
				p.set(5, hy + 1, k, Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState());
				p.set(5, hy - 1, -k, Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState());
				p.set(5, hy + k, -1, Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState());
				p.set(5, hy - k, 1, Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState());
			}
		}
		p.set(5, hy, 0, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
		tree(p, -7, f, -6, CHERRY_LEAVES, Blocks.CHERRY_LOG, 4);
		tree(p, 7, f, -7, CHERRY_LEAVES, Blocks.CHERRY_LOG, 4);
	}

	// ------------------------------------------------------------------ bridges and gardens

	/** The deck of a bridge (5 walkable blocks between cherry rails) and its piers in the garden below. */
	private void bridge(Bridge br) {
		Direction d = br.dir();
		Direction across = d.getClockWise();
		int y = br.from().floor();
		Cell from = br.from();
		int start = HALF + 1;
		int end = 2 * TerraceTown.CELL - HALF; // the island cell's edge row, measured from `from`'s centre
		for (int s = start; s <= end; s++) {
			for (int t = -3; t <= 3; t++) {
				int x = from.cx() + d.getStepX() * s + across.getStepX() * t;
				int z = from.cz() + d.getStepZ() * s + across.getStepZ() * t;
				floor(x, y, z, Math.abs(t) == 3 ? QUARTZ : Blocks.CHERRY_PLANKS.defaultBlockState(), false);
				set(x, y + 1, z, Math.abs(t) == 3 ? RAIL : AIR);
				set(x, y + 2, z, AIR);
				set(x, y + 3, z, AIR);
				if (Math.abs(t) == 3) {
					set(x, y - 1, z, log(Blocks.STRIPPED_CHERRY_LOG, d.getAxis()));
				}
			}
		}
		// piers in the garden below
		Cell over = br.over();
		for (int s : new int[] {-7, 7}) {
			for (int t = -2; t <= 2; t++) {
				int x = over.cx() + d.getStepX() * s + across.getStepX() * t;
				int z = over.cz() + d.getStepZ() * s + across.getStepZ() * t;
				for (int yy = over.floor() + 1; yy < y; yy++) {
					set(x, yy, z, Math.abs(t) == 2 ? QUARTZ : STONE_BRICKS);
				}
			}
		}
	}

	private void bridgeGarden(Cell c) {
		int f = c.floor();
		P p = pen(c, Direction.NORTH);
		for (int v = -INNER; v <= INNER; v++) {
			for (int u = -INNER; u <= INNER; u++) {
				boolean path = Math.abs(u) <= 1 || Math.abs(v) <= 1;
				p.floor(u, f, v, path ? Blocks.GRAVEL.defaultBlockState() : GRASS, false);
			}
		}
		// lily pond off the bridge axis
		boolean alongX = c.j == 0;
		int pu = alongX ? 0 : 6;
		int pv = alongX ? 6 : 0;
		for (int du = -2; du <= 2; du++) {
			for (int dv = -2; dv <= 2; dv++) {
				boolean rim = Math.abs(du) == 2 || Math.abs(dv) == 2;
				p.set(pu + du, f, pv + dv, STONE_BRICKS);
				if (!rim) {
					p.set(pu + du, f, pv + dv, WATER);
					p.set(pu + du, f - 1, pv + dv, STONE_BRICKS);
				}
			}
		}
		int tu = alongX ? 7 : -7;
		int tv = alongX ? -7 : 7;
		tree(p, tu, f, tv, CHERRY_LEAVES, Blocks.CHERRY_LOG, 4);
	}

	/** A free solar on a path: a lawn with paths and trees. */
	private void garden(Cell c) {
		int f = c.floor();
		P p = pen(c, c.front());
		Mat m = mat(c);
		for (int v = -INNER; v <= INNER; v++) {
			for (int u = -INNER; u <= INNER; u++) {
				boolean path = Math.abs(u) <= 1 || Math.abs(v) <= 1;
				p.floor(u, f, v, path ? m.path() : GRASS, false);
			}
		}
		for (int su = -1; su <= 1; su += 2) {
			for (int sv = -1; sv <= 1; sv += 2) {
				boolean cherry = (c.i + c.j + su + sv & 2) == 0;
				tree(p, 6 * su, f, 6 * sv, cherry ? CHERRY_LEAVES : m.leaves(), cherry ? Blocks.CHERRY_LOG : m.treeLog(), 4 + ((c.i * 3 + c.j + su) & 1));
			}
		}
	}

	// ------------------------------------------------------------------ barrios

	/**
	 * A project's barrio on a solar, facing its front (the cell it is reached from): the main hall at
	 * the back (desks 0..5), annex B on the left (6..9) and the annex tower C on the right (10..13),
	 * round a paved patio, a low garden wall with the gate on the front.
	 */
	private void barrio(Cell c, List<HqLayout.Module> mods) {
		int f = c.floor();
		P p = pen(c, c.front());
		Mat m = mat(c);
		HqLayout.Module first = mods.get(0);
		Map<Integer, HqLayout.Module> byPart = new HashMap<>();
		for (HqLayout.Module md : mods) {
			byPart.put(md.index() % 4, md);
		}
		DyeColor color = HqStyle.envColor(first);
		// ground: patio and front strip paved, the rest lawn
		for (int v = -INNER; v <= INNER; v++) {
			for (int u = -INNER; u <= INNER; u++) {
				boolean patio = Math.abs(u) <= 3 && v >= -7 && v <= 1;
				boolean strip = v <= -8;
				BlockState s = patio ? ((u + v & 1) == 0 ? m.path() : m.path2()) : strip ? m.path() : GRASS;
				if (patio && Math.abs(u) <= 1 && v == -3) {
					s = kit().floorAccent();
				}
				p.floor(u, f, v, s, false);
			}
		}
		// front garden wall with the gate
		for (int u = -INNER; u <= INNER; u++) {
			if (Math.abs(u) <= 2) {
				continue;
			}
			p.set(u, f + 1, -INNER, Math.abs(u) == 3 ? m.plinth() : PARAPET);
		}
		p.set(-3, f + 2, -INNER, m.plinth());
		p.set(3, f + 2, -INNER, m.plinth());
		p.sign(3, f + 2, -INNER - 1, Direction.NORTH, color, "", first.label(), first.envLabel(), "");
		// main hall
		house(p, -INNER, 2, INNER, INNER, f, 6, m, Roof.GABLE_U);
		door(p, 0, 2, f, 3);
		for (int u = -INNER + 1; u <= INNER - 1; u++) {
			if (Math.abs(u) > 1) {
				p.set(u, f + 6, 2, Blocks.WOOL.pick(color).defaultBlockState());
			}
		}
		HqLayout.Module hall = byPart.get(0);
		int[][] hallDesks = {{0, 9, 2}, {-6, 9, 2}, {6, 9, 2}, {-6, 3, 0}, {6, 3, 0}, {9, 6, 1}};
		// face: 0 = NORTH, 1 = EAST, 2 = SOUTH, 3 = WEST (local, from the agent to the desk)
		if (hall != null) {
			for (int s = 0; s < hallDesks.length; s++) {
				desk(p, hallDesks[s][0], f + 1, hallDesks[s][1], face(hallDesks[s][2]), hall.threadAt(s), hall.index(), s);
			}
		}
		// annex B (left) and the annex tower C (right), or lawn with a tree while they are not needed
		for (int part = 1; part <= 2; part++) {
			int side = part == 1 ? -1 : 1;
			HqLayout.Module an = byPart.get(part);
			int u1 = side < 0 ? -INNER : 4;
			int u2 = side < 0 ? -4 : INNER;
			if (an == null) {
				tree(p, 7 * side, f, -3, (c.i + part & 1) == 0 ? CHERRY_LEAVES : m.leaves(), (c.i + part & 1) == 0 ? Blocks.CHERRY_LOG : m.treeLog(), 4);
				continue;
			}
			house(p, u1, -7, u2, 1, f, part == 1 ? 5 : 9, m, part == 1 ? Roof.GABLE_V : Roof.HIP);
			door(p, 7 * side, -7, f, 3);
			int outer = 9 * side;
			int inner = 5 * side;
			Direction outFace = side < 0 ? Direction.WEST : Direction.EAST;
			int[][] at = {{outer, -5}, {outer, -1}, {inner, -5}, {inner, -1}};
			for (int s = 0; s < 4; s++) {
				Direction fc = s < 2 ? outFace : outFace.getOpposite();
				desk(p, at[s][0], f + 1, at[s][1], fc, an.threadAt(s), an.index(), s);
			}
		}
		// planters in the patio corners (against the walls, out of the way)
		p.decor(-3, f + 1, 1, kit().gardenPlanter());
		p.decor(3, f + 1, 1, kit().gardenPlanter());
	}

	private static Direction face(int k) {
		return switch (k) {
			case 0 -> Direction.NORTH;
			case 1 -> Direction.EAST;
			case 2 -> Direction.SOUTH;
			default -> Direction.WEST;
		};
	}

	/**
	 * One standing desk: three desk tops along the wall, the 3×2 screen on them, the free standing
	 * cell in front. Vacant: nothing is built.
	 *
	 * @param localFace from the agent to the desk, in the pen's frame
	 */
	private void desk(P p, int u, int y, int v, Direction localFace, @Nullable String thread, int module, int slot) {
		if (thread == null) {
			return;
		}
		Direction fc = p.dir(localFace);
		Direction right = fc.getClockWise();
		BlockPos top = p.at(u, y, v);
		Direction front = fc.getOpposite();
		for (BlockPos q : new BlockPos[] {top, top.relative(right), top.relative(right.getOpposite())}) {
			set(q.getX(), q.getY(), q.getZ(), kit().desk(front));
		}
		BlockPos monitor = top.above();
		Direction screenFacing = fc.getOpposite();
		BlockPos origin = Workstation.screenOriginFor(monitor, screenFacing);
		for (BlockPos q : Workstation.screenCells(origin, screenFacing.getCounterClockWise())) {
			set(q.getX(), q.getY(), q.getZ(), style.computer(true));
		}
		BlockPos stand = top.relative(fc.getOpposite());
		set(stand.getX(), stand.getY(), stand.getZ(), AIR);
		set(stand.getX(), stand.getY() + 1, stand.getZ(), AIR);
		Anchor deskA = Anchor.at(AnchorNames.deskSlot(module, slot), stand, fc);
		Anchor screen = Anchor.face(AnchorNames.screenSlot(module, slot), monitor, screenFacing);
		stations.add(new Workstation(module, slot, thread, deskA, monitor, screen, origin));
		anchors.add(deskA.renamed(AnchorNames.desk(thread)));
		anchors.add(screen.renamed(AnchorNames.monitor(thread)));
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
	 * A house: kit floor (indoors), walls with a plinth course, posts, a beam at the top, windows,
	 * a flat ceiling and a roof. Doors are cut afterwards with {@link #door}.
	 */
	private void house(P p, int u1, int v1, int u2, int v2, int f, int h, Mat m, Roof roof) {
		for (int v = v1; v <= v2; v++) {
			for (int u = u1; u <= u2; u++) {
				boolean edge = u == u1 || u == u2 || v == v1 || v == v2;
				if (edge) {
					p.set(u, f, v, m.plinth());
				} else {
					p.floor(u, f, v, kit().floor(), true);
				}
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
					} else if (y >= f + 3 && y <= f + Math.min(h - 1, 4) && k % 3 == 2 && k > 1 && k < len - 1) {
						s = GLASS;
					} else {
						s = m.wall();
					}
					p.set(u, y, v, s);
				}
			}
		}
		for (int v = v1 + 1; v <= v2 - 1; v++) {
			for (int u = u1 + 1; u <= u2 - 1; u++) {
				p.set(u, f + h + 1, v, m.gable());
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

	/** A gable roof over the box, one block of overhang. {@code alongU}: the ridge runs along {@code u}. */
	private void gable(P p, int u1, int v1, int u2, int v2, int y0, Block roof, BlockState ridge, BlockState gableFill, boolean alongU, boolean closeEnds) {
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

	private void hip(P p, int u1, int v1, int u2, int v2, int y0, Block roof, BlockState ridge) {
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
		if (stairs == Blocks.DEEPSLATE_BRICK_STAIRS) {
			return Blocks.DEEPSLATE_BRICK_SLAB;
		}
		return Blocks.STONE_BRICK_SLAB;
	}

	/**
	 * A doorway in the wall at (u, v), {@code width} wide (odd), three tall; along u when the wall
	 * runs along u (front/back walls), along v otherwise (side walls, detected by the cell above).
	 */
	private void door(P p, int u, int v, int f, int width) {
		int w = width / 2;
		BlockState below = get(p.x(u + 1, v), f + 1, p.z(u + 1, v));
		boolean alongU = below != null && !below.isAir();
		for (int d = -w; d <= w; d++) {
			int uu = alongU ? u + d : u;
			int vv = alongU ? v : v + d;
			for (int y = f + 1; y <= f + 3; y++) {
				p.set(uu, y, vv, AIR);
			}
			p.floor(uu, f, vv, kit().floorAccent(), true);
		}
	}
}
