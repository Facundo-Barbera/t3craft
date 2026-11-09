package dev.t3craft.hq.citadel;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.SOLID;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.col;
import static dev.t3craft.hq.citadel.CitadelCanvas.colX;
import static dev.t3craft.hq.citadel.CitadelCanvas.colZ;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.hq.Workstation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * design/citadel-terraces: the soft campus relief of the citadel ({@code terrain.relief}, 0..3, default 2).
 *
 * <h2>Concept</h2>
 *
 * An acropolis campus, not a mesa: the core (hub on its podium, patio, promenade, ramps, inner street) is the
 * highest terrace, and each ring of barrios stands one low step below the one inside it (relief 2: core +6, ring 1
 * +4, ring 2 +2, ring 3 on the ground; the lawn round the last street banks down to the natural ground in kerbed
 * grass beds). Steps are
 * 1 to 3 blocks, never more than 6 in all, so the differences read as design: low dark retaining walls of deepslate brick with
 * a light calcite parapet on top (a potted azalea or a quartz cap every 7 cells), hedges and flowers at their foot, the avenues turned into
 * wide slab ramps that start just past their torii, and stairs wherever a path meets a step.
 *
 * <h2>How</h2>
 *
 * The pieces still paint the flat citadel; {@link #apply} runs first thing in {@code CitadelFinish.build} and lifts
 * every column by its terrace ({@link #halves}, in half blocks so the avenue ramps can use slabs). A cell written
 * inside a rigid piece ({@link CitadelCanvas#rigid}: trees, catalog plots) takes the lift of its piece's column, so
 * nothing is sheared. Under a lifted column it fills the ground; where that fill shows on a step it becomes the
 * retaining wall. Anchors, desks, fixtures and the spawn move with their column. With relief 0 it does nothing and
 * the plan is the flat one block by block.
 *
 * <h2>Rules it keeps</h2>
 *
 * <ul>
 * <li>Every step a path meets gets stairs ({@link #stairs}): on the low side when the path runs on, otherwise cut
 *     into the high side. A step of one block between two paved cells gets a stair too, so the player walks it.</li>
 * <li>A balustrade only where the drop is 2 or more and the high cell is free; never on a stair head.</li>
 * <li>No walkable floor is added over a room: the relief only moves floors up with their column and fills under.</li>
 * </ul>
 */
public final class CitadelRelief {
	private CitadelRelief() {
	}

	private static final Direction[] DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	/** Last summary of {@link #check} (render diagnostics only). */
	public static volatile String lastReport = "";

	// ------------------------------------------------------------------ the terraces

	/** The terrace height of a column in half blocks (0 outside the citadel or with relief 0). */
	public static int halves(CitadelCanvas c, int x, int z) {
		if (RingCampusGeometry.relief() == 0) {
			return 0;
		}
		Zone zn = c.zoneAt(x, z);
		return switch (zn.kind()) {
			case NONE -> 0;
			case HUB, HUB_WALL, BRIDGE, PATIO, GALLERY, RAMP, TERRACE, INNER_STREET -> 2 * RingCampusGeometry.level(0);
			case NODE -> 2 * RingCampusGeometry.level(1);
			case LOT, STREET -> 2 * RingCampusGeometry.level(zn.ring());
			case AVENUE -> 2 * RingCampusGeometry.level(zn.ring()) + avenueRamp(x, z, zn.ring());
			case LAWN -> 2 * lawn(x, z, zn.ring());
		};
	}

	/**
	 * The extra half blocks of an avenue column of ring {@code m}: the avenue keeps the level of the street inside it
	 * up to just past its torii, then goes down one half block per cell to its own ring's level.
	 */
	private static int avenueRamp(int x, int z, int m) {
		int step = RingCampusGeometry.level(m - 1) - RingCampusGeometry.level(m);
		if (step <= 0) {
			return 0;
		}
		double start = RingCampusGeometry.ringInner(m) + CitadelStreetscape.GATE_IN() + 2.5;
		double t = RingCampusGeometry.r(x, z) - start;
		if (t < 0) {
			return 2 * step;
		}
		return Math.max(0, 2 * step - 1 - (int) Math.floor(t));
	}

	/** The lawn round the last street: a gentle grass bank from the street's level down to the natural ground. */
	private static int lawn(int x, int z, int rings) {
		int top = RingCampusGeometry.level(rings);
		double from = RingCampusGeometry.streetOuter(rings);
		double to = RingCampusGeometry.radius(rings);
		double w = Math.max(1.0, to - from);
		// design/citadel-terraces2: the bank is not a ring: its foot comes in by up to 55 % of the lawn's width, with a
		// slow swell (a few lobes round the circle) and a faster ripple, both periodic in the angle and in the radius
		double a = RingCampusGeometry.theta(x, z);
		double swell = 0.5 * Math.sin(3 * a + 0.7) + 0.3 * Math.sin(5 * a + 2.1) + 0.2 * Math.sin(8 * a + 4.0);
		double ripple = 0.5 * Math.sin(13 * a + 1.3 + 0.35 * RingCampusGeometry.r(x, z));
		double foot = to - w * (0.30 + 0.25 * swell + 0.08 * ripple); // where the bank reaches the natural ground
		double f = (foot - RingCampusGeometry.r(x, z)) / Math.max(1.0, foot - from);
		return (int) Math.max(0, Math.min(top, Math.round(top * f)));
	}

	// ------------------------------------------------------------------ the pass

	/** Lifts the painted flat citadel onto its terraces (see the class comment). */
	public static void apply(CitadelCanvas c) {
		CitadelPodium.buttresses(c); // design/citadel-terraces2: pilasters on the feet of the podium's walls (flat frame)
		if (RingCampusGeometry.relief() == 0) {
			return;
		}
		Map<Long, Integer> half = new HashMap<>();
		for (long cc : c.zones.keySet()) {
			half.put(cc, halves(c, colX(cc), colZ(cc)));
		}
		for (Map.Entry<Long, Integer> e : half.entrySet()) {
			c.lift.put(e.getKey(), e.getValue() / 2);
		}
		shift(c, half);
		Set<Long> filled = fill(c, half);
		slabs(c, half);
		Set<Long> keep = stairs(c, half);
		edges(c, half, keep);
		walls(c, half, filled);
		bancales(c, half);
	}

	/** The lawn's grass steps get a mossy stone-brick kerb on their edge: terraced garden beds, not contour lines. */
	private static void bancales(CitadelCanvas c, Map<Long, Integer> half) {
		for (Map.Entry<Long, Integer> e : half.entrySet()) {
			int x = colX(e.getKey());
			int z = colZ(e.getKey());
			if (c.zoneAt(x, z).kind() != Kind.LAWN || e.getValue() < 2) {
				continue;
			}
			int g = e.getValue() / 2;
			boolean edge = false;
			for (Direction dir : DIRS) {
				Integer hn = half.get(col(x + dir.getStepX(), z + dir.getStepZ()));
				if ((hn == null ? 0 : hn) / 2 < g) {
					edge = true;
				}
			}
			BlockState s = c.at(x, g, z);
			if (edge && s != null && s.getBlock() == Blocks.GRASS_BLOCK) {
				c.set(x, g, z, Math.floorMod(x * 31 + z * 7, 5) == 0 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
			}
		}
	}

	private static int dy(Map<Long, Integer> half, long cc) {
		Integer h = half.get(cc);
		return h == null ? 0 : h / 2;
	}

	/** Moves every cell, anchor, desk and fixture up by its column's (or its rigid piece's) lift. */
	private static void shift(CitadelCanvas c, Map<Long, Integer> half) {
		Map<Long, BlockState> cells = new HashMap<>(c.cells.size() * 2);
		Map<Long, Integer> layers = new HashMap<>(c.cells.size() * 2);
		Map<Long, CompoundTag> data = new HashMap<>();
		List<Long> rigid = new ArrayList<>();
		for (Map.Entry<Long, BlockState> e : c.cells.entrySet()) {
			long k = e.getKey();
			if (c.liftFrom.containsKey(k)) {
				rigid.add(k);
				continue;
			}
			int x = BlockPos.getX(k);
			int z = BlockPos.getZ(k);
			long n = BlockPos.offset(k, 0, dy(half, col(x, z)), 0);
			cells.put(n, e.getValue());
			layers.put(n, c.layers.getOrDefault(k, SOLID));
			CompoundTag t = c.data.get(k);
			if (t != null) {
				data.put(n, t);
			}
		}
		for (long k : rigid) {
			long n = BlockPos.offset(k, 0, dy(half, c.liftFrom.get(k)), 0);
			BlockState there = cells.get(n);
			if (there != null && !there.isAir()) {
				continue; // the ground or a wall of a higher terrace wins over a leaf hanging into it
			}
			cells.put(n, c.cells.get(k));
			layers.put(n, c.layers.getOrDefault(k, SOLID));
			CompoundTag t = c.data.get(k);
			if (t != null) {
				data.put(n, t);
			}
		}
		Set<Long> fixtures = new HashSet<>();
		for (long k : c.fixtures) {
			fixtures.add(BlockPos.offset(k, 0, dy(half, col(BlockPos.getX(k), BlockPos.getZ(k))), 0));
		}
		c.cells.clear();
		c.cells.putAll(cells);
		c.layers.clear();
		c.layers.putAll(layers);
		c.data.clear();
		c.data.putAll(data);
		c.fixtures.clear();
		c.fixtures.addAll(fixtures);
		c.liftFrom.clear();
		c.anchors.replaceAll((name, a) -> up(a, half));
		List<Workstation> ws = new ArrayList<>(c.workstations.size());
		for (Workstation w : c.workstations) {
			int d = dy(half, col(w.computerPos().getX(), w.computerPos().getZ()));
			ws.add(new Workstation(w.module(), w.slot(), w.threadId(), up(w.deskAnchor(), half), w.computerPos().above(d), up(w.screen(), half),
					w.screenOrigin().above(d)));
		}
		c.workstations.clear();
		c.workstations.addAll(ws);
	}

	private static Anchor up(Anchor a, Map<Long, Integer> half) {
		int d = dy(half, col(a.block().getX(), a.block().getZ()));
		return d == 0 ? a : new Anchor(a.name(), a.x(), a.y() + d, a.z(), a.facing(), a.block().above(d));
	}

	/** Fills the ground under every lifted column (from the natural ground, y = 0, up to its lowest cell). */
	private static Set<Long> fill(CitadelCanvas c, Map<Long, Integer> half) {
		Map<Long, Integer> low = new HashMap<>();
		for (long k : c.cells.keySet()) {
			long cc = col(BlockPos.getX(k), BlockPos.getZ(k));
			int y = BlockPos.getY(k);
			low.merge(cc, y, Math::min);
		}
		Set<Long> out = new HashSet<>();
		for (Map.Entry<Long, Integer> e : half.entrySet()) {
			int d = e.getValue() / 2;
			if (d <= 0) {
				continue;
			}
			int x = colX(e.getKey());
			int z = colZ(e.getKey());
			int top = Math.min(d, low.getOrDefault(e.getKey(), d)) - 1;
			for (int y = 0; y <= top; y++) {
				long k = BlockPos.asLong(x, y, z);
				if (!c.cells.containsKey(k)) {
					c.put(x, y, z, b(Blocks.DIRT), SOLID);
					out.add(k);
				}
			}
		}
		return out;
	}

	/** On a column with an odd number of half blocks (the avenue ramps), a slab of its paving on top. */
	private static void slabs(CitadelCanvas c, Map<Long, Integer> half) {
		for (Map.Entry<Long, Integer> e : half.entrySet()) {
			if (e.getValue() % 2 == 0) {
				continue;
			}
			int x = colX(e.getKey());
			int z = colZ(e.getKey());
			int g = e.getValue() / 2;
			BlockState ground = c.at(x, g, z);
			BlockState above = c.at(x, g + 1, z);
			if (ground == null || above != null && !above.isAir()) {
				continue;
			}
			c.set(x, g + 1, z, slabOf(ground));
		}
	}

	// ------------------------------------------------------------------ stairs

	/** True for a ground block one walks on as a path (not grass, soil, leaves or water). */
	static boolean paved(@Nullable BlockState s) {
		if (s == null || s.isAir() || s.getBlock() instanceof LeavesBlock || s.getBlock() instanceof LiquidBlock) {
			return false;
		}
		Block bl = s.getBlock();
		if (bl == Blocks.GRASS_BLOCK || bl == Blocks.MOSS_BLOCK || bl == Blocks.DIRT || bl == Blocks.COARSE_DIRT || bl == Blocks.ROOTED_DIRT
				|| bl == Blocks.PODZOL || bl == Blocks.FARMLAND || bl == Blocks.MUD || bl == Blocks.SAND || bl == Blocks.CLAY) {
			return false;
		}
		return !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	private static boolean free(CitadelCanvas c, int x, int y, int z) {
		BlockState s = c.at(x, y, z);
		return s == null || s.isAir();
	}

	/** A cell one stands in: free, with the head cell free. */
	private static boolean open(CitadelCanvas c, int x, int y, int z) {
		return free(c, x, y, z) && free(c, x, y + 1, z);
	}

	private record Crossing(int lx, int lz, Direction toLow, int d) {
	}

	/**
	 * Stairs wherever a path meets a step: the low paved cell and the high paved cell are both open. Returns the
	 * cells (feet level) that must stay open (stair heads and treads), so no balustrade closes them.
	 */
	private static Set<Long> stairs(CitadelCanvas c, Map<Long, Integer> half) {
		List<Crossing> todo = new ArrayList<>();
		for (Map.Entry<Long, Integer> e : half.entrySet()) {
			int hl = e.getValue();
			if (hl % 2 == 1) {
				continue;
			}
			int lx = colX(e.getKey());
			int lz = colZ(e.getKey());
			int gl = hl / 2;
			if (!paved(c.at(lx, gl, lz)) || !open(c, lx, gl + 1, lz)) {
				continue;
			}
			for (Direction dir : DIRS) {
				int nx = lx + dir.getStepX();
				int nz = lz + dir.getStepZ();
				Integer hn = half.get(col(nx, nz));
				if (hn == null) {
					continue;
				}
				int d = hn / 2 - gl;
				if (d < 1) {
					continue;
				}
				int gn = hn / 2;
				boolean slabTop = hn % 2 == 1;
				if (!paved(c.at(nx, gn, nz)) || !(slabTop ? free(c, nx, gn + 2, nz) : open(c, nx, gn + 1, nz))) {
					continue;
				}
				todo.add(new Crossing(lx, lz, dir.getOpposite(), d));
			}
		}
		Set<Long> keep = new HashSet<>();
		for (Crossing k : todo) {
			if (!lowFlight(c, half, k, keep)) {
				highFlight(c, half, k, keep);
			}
		}
		return keep;
	}

	/** A flight on the low side, running away from the step along {@code toLow}. */
	private static boolean lowFlight(CitadelCanvas c, Map<Long, Integer> half, Crossing k, Set<Long> keep) {
		int hl = half.get(col(k.lx(), k.lz()));
		int gl = hl / 2;
		int sx = k.toLow().getStepX();
		int sz = k.toLow().getStepZ();
		for (int i = 0; i < k.d(); i++) {
			int x = k.lx() + i * sx;
			int z = k.lz() + i * sz;
			Integer h = half.get(col(x, z));
			if (h == null || h != hl || !solid(c.at(x, gl, z))) {
				return false;
			}
			for (int y = gl + 1; y <= gl + k.d() - i + 2; y++) {
				if (!free(c, x, y, z) && !isStair(c.at(x, y, z))) {
					return false;
				}
			}
		}
		// the foot of the flight must be walkable ground
		int fx = k.lx() + k.d() * sx;
		int fz = k.lz() + k.d() * sz;
		if (!open(c, fx, gl + 1, fz) || !solid(c.at(fx, gl, fz))) {
			return false;
		}
		BlockState ground = c.at(k.lx(), gl, k.lz());
		BlockState step = stairsOf(ground, k.toLow().getOpposite());
		BlockState core = coreOf(ground);
		for (int i = 0; i < k.d(); i++) {
			int x = k.lx() + i * sx;
			int z = k.lz() + i * sz;
			int top = gl + k.d() - i;
			for (int y = gl + 1; y < top; y++) {
				c.set(x, y, z, core);
			}
			c.set(x, top, z, step);
			keep.add(BlockPos.asLong(x, top + 1, z));
		}
		keep.add(BlockPos.asLong(k.lx() - sx, gl + k.d() + 1, k.lz() - sz));
		return true;
	}

	/** A flight cut into the high side, climbing away from the step (against {@code toLow}). */
	private static boolean highFlight(CitadelCanvas c, Map<Long, Integer> half, Crossing k, Set<Long> keep) {
		int gl = half.get(col(k.lx(), k.lz())) / 2;
		int sx = -k.toLow().getStepX();
		int sz = -k.toLow().getStepZ();
		int hx = k.lx() + sx;
		int hz = k.lz() + sz;
		int hh = half.get(col(hx, hz));
		int gh = hh / 2;
		for (int i = 0; i < k.d(); i++) {
			int x = hx + i * sx;
			int z = hz + i * sz;
			Integer h = half.get(col(x, z));
			if (h == null || h / 2 != gh || !paved(c.at(x, gh, z)) || !free(c, x, gh + 1, z) && !(h % 2 == 1)) {
				return false;
			}
			if (!free(c, x, gh + 2, z)) {
				return false;
			}
		}
		BlockState ground = c.at(hx, gh, hz);
		BlockState step = stairsOf(ground, k.toLow().getOpposite());
		for (int i = 0; i < k.d(); i++) {
			int x = hx + i * sx;
			int z = hz + i * sz;
			int y = gl + 1 + i;
			for (int a = y + 1; a <= gh + 1; a++) {
				c.set(x, a, z, AIR);
			}
			c.set(x, y, z, step);
			keep.add(BlockPos.asLong(x, y + 1, z));
		}
		return true;
	}

	private static boolean solid(@Nullable BlockState s) {
		return s != null && !s.isAir() && !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	private static boolean isStair(@Nullable BlockState s) {
		return s != null && s.getBlock() instanceof StairBlock;
	}

	// ------------------------------------------------------------------ balustrades and planters

	/**
	 * Along every step of 2 or more: a balustrade on the high side (stone-brick wall between chiseled piers with a slab
	 * cap) where the paved edge is free, and a hedge with flowers at the foot of the wall on the low side where it is
	 * grass. Steps of one block on the lawn are left as the natural bank they are.
	 */
	private static void edges(CitadelCanvas c, Map<Long, Integer> half, Set<Long> keep) {
		Map<Long, Direction> rail = new HashMap<>();
		Set<Long> foot = new HashSet<>();
		for (Map.Entry<Long, Integer> e : half.entrySet()) {
			int x = colX(e.getKey());
			int z = colZ(e.getKey());
			int h = e.getValue();
			for (Direction dir : DIRS) {
				int nx = x + dir.getStepX();
				int nz = z + dir.getStepZ();
				Integer hn = half.get(col(nx, nz));
				int drop = h - (hn == null ? 0 : hn);
				if (hn == null || drop < 4) {
					continue;
				}
				rail.putIfAbsent(e.getKey(), dir);
				foot.add(col(nx, nz));
			}
		}
		for (Map.Entry<Long, Direction> re : rail.entrySet()) {
			long cc = re.getKey();
			int x = colX(cc);
			int z = colZ(cc);
			int h = half.get(cc);
			int g = h / 2;
			int y = g + 1 + h % 2;
			if (h % 2 == 1 || !paved(c.at(x, g, z)) || !open(c, x, y, z) || keep.contains(BlockPos.asLong(x, y, z))) {
				continue;
			}
			if (nearKeep(keep, x, y, z)) {
				continue;
			}
			lookout(c, x, y, z, re.getValue(), keep);
			double arc = RingCampusGeometry.theta(x, z) * RingCampusGeometry.r(x, z);
			// [palette] a light parapet over the dark kerb, the line that draws each terrace from above: calcite with a
			// little polished diorite, and a pier of chiseled quartz with a quartz cap every 7 cells
			c.set(x, y, z, b(Blocks.CALCITE));
			long beat = Math.floorMod(Math.round(arc), 14);
			if (beat == 0) {
				c.set(x, y + 1, z, b(Blocks.POTTED_FLOWERING_AZALEA)); // a planter on the pier
			} else if (beat == 7) {
				c.set(x, y + 1, z, slab(Blocks.SMOOTH_QUARTZ_SLAB));
			}
		}
		for (long cc : foot) {
			int x = colX(cc);
			int z = colZ(cc);
			Integer h = half.get(cc);
			if (h == null || h % 2 == 1) {
				continue;
			}
			int g = h / 2;
			BlockState ground = c.at(x, g, z);
			if (ground == null || ground.getBlock() != Blocks.GRASS_BLOCK && ground.getBlock() != Blocks.MOSS_BLOCK || !open(c, x, g + 1, z)) {
				continue;
			}
			// design/citadel-terraces2: the foot of a wall stays clear (no hedge to hide it): low flowers and grass only
			int k = Math.floorMod(x * 31 + z * 17 + (x ^ z) * 7, 9);
			BlockState plant = switch (k) {
				case 0, 1 -> b(Blocks.PINK_TULIP);
				case 2 -> b(Blocks.ALLIUM);
				case 3 -> b(Blocks.AZURE_BLUET);
				case 4, 5 -> b(Blocks.SHORT_GRASS);
				default -> null;
			};
			if (plant != null) {
				c.set(x, g + 1, z, plant);
			}
		}
	}

	/**
	 * A lookout on the street side of a parapet that faces the middle of a lot of the ring below: a long stone bench
	 * one cell in from the parapet, its back to the street, looking out over the barrio.
	 */
	private static void lookout(CitadelCanvas c, int x, int y, int z, Direction toLow, Set<Long> keep) {
		Kind k = c.zoneAt(x, z).kind();
		if (k != Kind.INNER_STREET && k != Kind.STREET) {
			return;
		}
		int ring = k == Kind.INNER_STREET ? 1 : c.zoneAt(x, z).ring() + 1;
		if (ring > RingCampusGeometry.MAX_RINGS) {
			return;
		}
		int n = RingCampusGeometry.lotsIn(ring);
		double r = RingCampusGeometry.r(x, z);
		double a = RingCampusGeometry.theta(x, z) * n / (2 * Math.PI);
		double off = Math.abs(a - Math.floor(a) - 0.5) * 2 * Math.PI / n * r; // cells of arc from the lot's middle
		if (off > 1.6) {
			return;
		}
		int bx = x - toLow.getStepX();
		int bz = z - toLow.getStepZ();
		if (c.zoneAt(bx, bz).kind() != k || !paved(c.at(bx, y - 1, bz)) || !open(c, bx, y, bz) || keep.contains(BlockPos.asLong(bx, y, bz))) {
			return;
		}
		c.set(bx, y, bz, CitadelCanvas.stairs(Blocks.POLISHED_DEEPSLATE_STAIRS, toLow.getOpposite()));
	}

	private static boolean nearKeep(Set<Long> keep, int x, int y, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dyy = -1; dyy <= 1; dyy++) {
					if (keep.contains(BlockPos.asLong(x + dx, y + dyy, z + dz))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ retaining walls

	/**
	 * The fill that shows on a step becomes a retaining wall: stone bricks with a mossy and cracked mix, a dark
	 * deepslate-brick footing course on the taller walls, and a buttress of polished andesite every few cells.
	 */
	private static void walls(CitadelCanvas c, Map<Long, Integer> half, Set<Long> filled) {
		for (long k : filled) {
			int x = BlockPos.getX(k);
			int y = BlockPos.getY(k);
			int z = BlockPos.getZ(k);
			int low = Integer.MAX_VALUE;
			for (Direction dir : DIRS) {
				int nx = x + dir.getStepX();
				int nz = z + dir.getStepZ();
				BlockState n = c.at(nx, y, nz);
				if (n == null || n.isAir() || n.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
					Integer hn = half.get(col(nx, nz));
					low = Math.min(low, hn == null ? 0 : hn / 2);
				}
			}
			if (low == Integer.MAX_VALUE) {
				continue;
			}
			Kind kind = c.zoneAt(x, z).kind();
			int g = half.getOrDefault(col(x, z), 0) / 2;
			c.set(x, y, z, wall(kind, x, y, z, y - low, g - low));
		}
	}

	private static BlockState wall(Kind kind, int x, int y, int z, int course, int height) {
		int h = Math.floorMod(x * 73856093 ^ y * 19349663 ^ z * 83492791, 23);
		if (kind == Kind.LAWN) {
			return h < 8 ? b(Blocks.MOSSY_STONE_BRICKS) : h < 12 ? b(Blocks.MOSSY_COBBLESTONE) : b(Blocks.STONE_BRICKS);
		}
		// [palette] dark heavy walls under the light parapet, so every terrace reads as a band from above
		if (course <= 1) {
			return b(Blocks.POLISHED_DEEPSLATE);
		}
		double arc = RingCampusGeometry.theta(x, z) * RingCampusGeometry.r(x, z);
		if (Math.floorMod(Math.round(arc), 7) == 0) {
			return b(Blocks.POLISHED_BLACKSTONE_BRICKS);
		}
		return h < 4 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : h < 7 ? b(Blocks.DEEPSLATE_TILES) : b(Blocks.DEEPSLATE_BRICKS);
	}

	// ------------------------------------------------------------------ materials

	/** The slab of a paving block (a smooth-stone slab when it has none). */
	static BlockState slabOf(BlockState s) {
		Block bl = s.getBlock();
		if (bl instanceof SlabBlock) {
			return s;
		}
		Block out;
		if (bl == Blocks.DEEPSLATE_TILES || bl == Blocks.CRACKED_DEEPSLATE_TILES) {
			out = Blocks.DEEPSLATE_TILE_SLAB;
		} else if (bl == Blocks.DEEPSLATE_BRICKS || bl == Blocks.CRACKED_DEEPSLATE_BRICKS) {
			out = Blocks.DEEPSLATE_BRICK_SLAB;
		} else if (bl == Blocks.POLISHED_DEEPSLATE) {
			out = Blocks.POLISHED_DEEPSLATE_SLAB;
		} else if (bl == Blocks.STONE_BRICKS || bl == Blocks.CRACKED_STONE_BRICKS || bl == Blocks.CHISELED_STONE_BRICKS) {
			out = Blocks.STONE_BRICK_SLAB;
		} else if (bl == Blocks.MOSSY_STONE_BRICKS) {
			out = Blocks.MOSSY_STONE_BRICK_SLAB;
		} else if (bl == Blocks.BRICKS) {
			out = Blocks.BRICK_SLAB;
		} else if (bl == Blocks.POLISHED_BLACKSTONE_BRICKS || bl == Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) {
			out = Blocks.POLISHED_BLACKSTONE_BRICK_SLAB;
		} else if (bl == Blocks.POLISHED_BLACKSTONE) {
			out = Blocks.POLISHED_BLACKSTONE_SLAB;
		} else if (bl == Blocks.CHERRY_PLANKS) {
			out = Blocks.CHERRY_SLAB;
		} else if (bl == Blocks.BAMBOO_MOSAIC) {
			out = Blocks.BAMBOO_MOSAIC_SLAB;
		} else if (bl == Blocks.BAMBOO_PLANKS) {
			out = Blocks.BAMBOO_SLAB;
		} else if (bl == Blocks.POLISHED_ANDESITE) {
			out = Blocks.POLISHED_ANDESITE_SLAB;
		} else if (bl == Blocks.ANDESITE) {
			out = Blocks.ANDESITE_SLAB;
		} else if (bl == Blocks.POLISHED_DIORITE) {
			out = Blocks.POLISHED_DIORITE_SLAB;
		} else if (bl == Blocks.POLISHED_GRANITE) {
			out = Blocks.POLISHED_GRANITE_SLAB;
		} else if (bl == Blocks.CALCITE || bl == Blocks.QUARTZ_BLOCK || bl == Blocks.SMOOTH_QUARTZ) {
			out = Blocks.SMOOTH_QUARTZ_SLAB;
		} else if (bl == Blocks.MUD_BRICKS) {
			out = Blocks.MUD_BRICK_SLAB;
		} else if (bl == Blocks.TUFF_BRICKS) {
			out = Blocks.TUFF_BRICK_SLAB;
		} else if (bl == Blocks.POLISHED_TUFF) {
			out = Blocks.POLISHED_TUFF_SLAB;
		} else if (bl == Blocks.STONE) {
			out = Blocks.STONE_SLAB;
		} else {
			out = Blocks.SMOOTH_STONE_SLAB;
		}
		return slab(out);
	}

	/** The stairs of a paving block, climbing towards {@code up} (stone-brick stairs when it has none). */
	static BlockState stairsOf(@Nullable BlockState s, Direction up) {
		Block bl = s == null ? Blocks.STONE_BRICKS : s.getBlock();
		Block out;
		if (bl == Blocks.DEEPSLATE_TILES || bl == Blocks.CRACKED_DEEPSLATE_TILES) {
			out = Blocks.DEEPSLATE_TILE_STAIRS;
		} else if (bl == Blocks.DEEPSLATE_BRICKS || bl == Blocks.CRACKED_DEEPSLATE_BRICKS) {
			out = Blocks.DEEPSLATE_BRICK_STAIRS;
		} else if (bl == Blocks.POLISHED_DEEPSLATE) {
			out = Blocks.POLISHED_DEEPSLATE_STAIRS;
		} else if (bl == Blocks.MOSSY_STONE_BRICKS) {
			out = Blocks.MOSSY_STONE_BRICK_STAIRS;
		} else if (bl == Blocks.BRICKS) {
			out = Blocks.BRICK_STAIRS;
		} else if (bl == Blocks.POLISHED_BLACKSTONE_BRICKS || bl == Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) {
			out = Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS;
		} else if (bl == Blocks.POLISHED_BLACKSTONE) {
			out = Blocks.POLISHED_BLACKSTONE_STAIRS;
		} else if (bl == Blocks.CHERRY_PLANKS) {
			out = Blocks.CHERRY_STAIRS;
		} else if (bl == Blocks.BAMBOO_MOSAIC) {
			out = Blocks.BAMBOO_MOSAIC_STAIRS;
		} else if (bl == Blocks.BAMBOO_PLANKS) {
			out = Blocks.BAMBOO_STAIRS;
		} else if (bl == Blocks.POLISHED_ANDESITE) {
			out = Blocks.POLISHED_ANDESITE_STAIRS;
		} else if (bl == Blocks.POLISHED_DIORITE) {
			out = Blocks.POLISHED_DIORITE_STAIRS;
		} else if (bl == Blocks.POLISHED_GRANITE) {
			out = Blocks.POLISHED_GRANITE_STAIRS;
		} else if (bl == Blocks.CALCITE || bl == Blocks.QUARTZ_BLOCK || bl == Blocks.SMOOTH_QUARTZ) {
			out = Blocks.SMOOTH_QUARTZ_STAIRS;
		} else if (bl == Blocks.MUD_BRICKS) {
			out = Blocks.MUD_BRICK_STAIRS;
		} else if (bl == Blocks.TUFF_BRICKS) {
			out = Blocks.TUFF_BRICK_STAIRS;
		} else {
			out = Blocks.STONE_BRICK_STAIRS;
		}
		return CitadelCanvas.stairs(out, up);
	}

	/** The full block under a stair tread: the paving itself when it is a plain block, else stone bricks. */
	private static BlockState coreOf(@Nullable BlockState s) {
		if (s == null || s.getBlock() instanceof SlabBlock || s.getBlock() instanceof StairBlock || !solid(s)) {
			return b(Blocks.STONE_BRICKS);
		}
		return s;
	}

	// ------------------------------------------------------------------ diagnostics (renders)

	/** What was already out of reach on the flat plan ({@link #baseline}), so {@link #check} reports only what the relief cut. */
	private static volatile Set<String> flatLost = Set.of();

	/** The desks and walk anchors not in {@code walk} (names, with the blocks at the feet and under them). */
	private static Map<String, String> lost(CitadelCanvas c, List<BlockPos> walk) {
		Set<Long> w = new HashSet<>();
		for (BlockPos p : walk) {
			w.add(p.asLong());
		}
		Map<String, String> out = new java.util.TreeMap<>();
		for (Workstation ws : c.workstations) {
			BlockPos p = ws.deskAnchor().block();
			if (!w.contains(p.asLong())) {
				out.put("desk m" + ws.module() + "/" + ws.slot(), where(c, p));
			}
		}
		for (Anchor a : c.anchors.values()) {
			String nm = a.name();
			if ((nm.startsWith("walk") || nm.equals("spawn") || nm.startsWith("wing_") || nm.startsWith("stroll")) && !w.contains(a.block().asLong())) {
				out.put(nm, where(c, a.block()));
			}
		}
		return out;
	}

	private static String where(CitadelCanvas c, BlockPos p) {
		return p.toShortString() + "[" + name(c.at(p.getX(), p.getY(), p.getZ())) + "/" + name(c.at(p.getX(), p.getY() - 1, p.getZ())) + "]";
	}

	/** Records what the flat plan already leaves out of reach (render diagnostics; call before {@link #apply}). */
	static void baseline(CitadelCanvas c, List<BlockPos> walk) {
		flatLost = Set.copyOf(lost(c, walk).keySet());
	}

	/** Counts the desks and anchors an NPC reaches from the spawn ({@code walk}: the walkable feet cells). */
	static void check(CitadelCanvas c, List<BlockPos> walk) {
		Map<String, String> now = lost(c, walk);
		int desks = c.workstations.size();
		long deskLost = now.keySet().stream().filter(k -> k.startsWith("desk")).count();
		List<String> fresh = new ArrayList<>();
		for (Map.Entry<String, String> e : now.entrySet()) {
			if (!flatLost.contains(e.getKey())) {
				fresh.add(e.getKey() + "@" + e.getValue());
			}
		}
		lastReport = "relief " + RingCampusGeometry.relief() + ": desks reachable " + (desks - deskLost) + "/" + desks + "; out of reach on the flat plan already: "
				+ flatLost.size() + "; cut by the relief: " + (fresh.isEmpty() ? "none" : fresh.size() + " " + String.join(", ", fresh.subList(0, Math.min(12, fresh.size()))));
	}

	private static String name(@Nullable BlockState s) {
		return s == null ? "-" : s.getBlock().getDescriptionId().replace("block.minecraft.", "");
	}
}
