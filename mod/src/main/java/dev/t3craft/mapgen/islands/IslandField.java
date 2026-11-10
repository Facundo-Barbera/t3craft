package dev.t3craft.mapgen.islands;

import dev.t3craft.mapgen.Area;
import dev.t3craft.mapgen.MapGenerator;
import dev.t3craft.mapgen.MapLayout;
import dev.t3craft.mapgen.Noise;
import dev.t3craft.mapgen.Slot;
import dev.t3craft.mapgen.TerrainShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * The endless field of floating islands (generator {@value #ID}). One island per cell of a {@value #CELL}-block
 * grid: its size class comes from the cell's hash, its centre is jittered inside the cell and its height varies
 * by a few blocks. Cell {@code (0, 0)} is always the medium spawn island at {@code baseY}. There is no ground
 * between islands: two islands of neighbouring cells are only <i>linked</i> ({@link #neighbours}); the world
 * builds the bridge when a player nears the edge ({@link BridgePlanner}).
 *
 * <p>Spacing guarantees: every island stays inside its cell with a margin of {@value #MARGIN} blocks (outline
 * wobble included), so islands never touch (at least {@code 2 * MARGIN} blocks of void between cell neighbours)
 * and every cell neighbour is within one bridge of at most about 75 blocks.
 *
 * <p>The map is endless, so {@link #generate} lays out a {@link Window}: the islands that touch it, their slots
 * and the on-demand links between them. Everything is a pure function of the seed.
 */
public final class IslandField implements MapGenerator<IslandField.Window> {
	public static final String ID = "floating-islands";
	/** Distance between the grid lines of the cells. */
	public static final int CELL = 64;
	/** Largest displacement of an island's centre from its cell's centre. */
	public static final int MAX_JITTER = 10;
	/** Clear border between an island's outline and its cell's edge. */
	public static final int MARGIN = 6;
	/** Islands lie within this many blocks above or below {@code baseY}. */
	public static final int HEIGHT_RANGE = 4;

	private final long seed;
	private final int baseY;

	/** @param baseY y of the grass layer of the spawn island */
	public IslandField(long seed, int baseY) {
		this.seed = seed;
		this.baseY = baseY;
	}

	/** The columns {@code [cx - radius, cx + radius] x [cz - radius, cz + radius]}. */
	public record Window(int cx, int cz, int radius) {
		public Window {
			if (radius < 0) {
				throw new IllegalArgumentException("negative radius");
			}
		}

		Area area() {
			return new Area(cx - radius, cz - radius, cx + radius, cz + radius);
		}
	}

	@Override
	public String id() {
		return ID;
	}

	public long seed() {
		return seed;
	}

	public int baseY() {
		return baseY;
	}

	// ------------------------------------------------------------------ islands

	public static String idOf(int gx, int gz) {
		return "i_" + gx + "_" + gz;
	}

	/** The island of cell {@code (gx, gz)}. */
	public Island island(int gx, int gz) {
		boolean spawn = gx == 0 && gz == 0;
		IslandSize size = spawn ? IslandSize.MEDIUM : IslandSize.pick(Noise.hash(seed, gx, gz, 1, 10));
		int room = Math.min(MAX_JITTER, CELL / 2 - size.radius() - MARGIN);
		int jx = spawn ? 0 : Noise.hash(seed, gx, gz, 2, 2 * room + 1) - room;
		int jz = spawn ? 0 : Noise.hash(seed, gx, gz, 3, 2 * room + 1) - room;
		int top = spawn ? baseY : baseY + Noise.hash(seed, gx, gz, 4, 2 * HEIGHT_RANGE + 1) - HEIGHT_RANGE;
		double phaseA = Noise.unit(seed, gx, gz, 5) * 2 * Math.PI;
		double phaseB = Noise.unit(seed, gx, gz, 6) * 2 * Math.PI;
		return new Island(idOf(gx, gz), gx, gz, size, gx * CELL + jx, top, gz * CELL + jz, seed, phaseA, phaseB);
	}

	/** The spawn island. */
	public Island origin() {
		return island(0, 0);
	}

	/** The cell a column falls in. */
	public static int cellOf(int coord) {
		return Math.floorDiv(coord + CELL / 2, CELL);
	}

	/** The island whose ground (widened by {@code slack} blocks) contains the column, or null. */
	public @Nullable Island islandAt(int x, int z, double slack) {
		int gx = cellOf(x);
		int gz = cellOf(z);
		Island best = null;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Island i = island(gx + dx, gz + dz);
				if (i.contains(x, z, slack) && (best == null || i.distanceTo(x, z) < best.distanceTo(x, z))) {
					best = i;
				}
			}
		}
		return best;
	}

	/** Every island within {@code radius} blocks (centre to column) of {@code (x, z)}, nearest first. */
	public List<Island> within(int x, int z, int radius) {
		int reach = radius + CELL;
		List<Island> out = new ArrayList<>();
		for (int gx = cellOf(x - reach); gx <= cellOf(x + reach); gx++) {
			for (int gz = cellOf(z - reach); gz <= cellOf(z + reach); gz++) {
				Island i = island(gx, gz);
				if (i.distanceTo(x, z) <= radius) {
					out.add(i);
				}
			}
		}
		out.sort((a, b) -> Double.compare(a.distanceTo(x, z), b.distanceTo(x, z)));
		return out;
	}

	/** The islands {@code i} can be bridged to: its four cell neighbours. Symmetric. */
	public List<Island> neighbours(Island i) {
		return List.of(island(i.gx() + 1, i.gz()), island(i.gx(), i.gz() + 1), island(i.gx() - 1, i.gz()), island(i.gx(), i.gz() - 1));
	}

	// ------------------------------------------------------------------ slots

	/**
	 * The slots of an island, in map coordinates ({@code origin} on the grass layer + 1). Small islands hold
	 * a tree; medium a centre piece and a tree; large a bigger centre, two modules and two trees; extra large
	 * the biggest centre, four modules on the axes and four trees on the diagonals.
	 */
	public List<Slot> slots(Island i) {
		List<Slot> out = new ArrayList<>();
		String g = i.id();
		switch (i.size()) {
			case SMALL -> out.add(slot(i, "tree", Slot.Role.DECOR, 0, 0, Direction.SOUTH, 2, 2, 8));
			case MEDIUM -> {
				out.add(slot(i, "centre", Slot.Role.CENTRE, 0, 0, Direction.SOUTH, 2, 2, 7));
				out.add(slot(i, "tree_0", Slot.Role.DECOR, 5, 4, Direction.SOUTH, 2, 2, 8));
			}
			case LARGE -> {
				out.add(slot(i, "centre", Slot.Role.CENTRE, 0, 0, Direction.SOUTH, 3, 3, 8));
				out.add(slot(i, "module_0", Slot.Role.MODULE, 8, 0, Direction.WEST, 1, 1, 6));
				out.add(slot(i, "module_1", Slot.Role.MODULE, -8, 0, Direction.EAST, 1, 1, 6));
				out.add(slot(i, "tree_0", Slot.Role.DECOR, 0, 8, Direction.SOUTH, 2, 2, 8));
				out.add(slot(i, "tree_1", Slot.Role.DECOR, 0, -8, Direction.SOUTH, 2, 2, 8));
			}
			case XLARGE -> {
				out.add(slot(i, "centre", Slot.Role.CENTRE, 0, 0, Direction.SOUTH, 4, 4, 9));
				out.add(slot(i, "module_0", Slot.Role.MODULE, 11, 0, Direction.WEST, 2, 2, 6));
				out.add(slot(i, "module_1", Slot.Role.MODULE, -11, 0, Direction.EAST, 2, 2, 6));
				out.add(slot(i, "module_2", Slot.Role.MODULE, 0, 11, Direction.NORTH, 2, 2, 6));
				out.add(slot(i, "module_3", Slot.Role.MODULE, 0, -11, Direction.SOUTH, 2, 2, 6));
				out.add(slot(i, "tree_0", Slot.Role.DECOR, 10, 10, Direction.SOUTH, 2, 2, 8));
				out.add(slot(i, "tree_1", Slot.Role.DECOR, -10, 10, Direction.SOUTH, 2, 2, 8));
				out.add(slot(i, "tree_2", Slot.Role.DECOR, 10, -10, Direction.SOUTH, 2, 2, 8));
				out.add(slot(i, "tree_3", Slot.Role.DECOR, -10, -10, Direction.SOUTH, 2, 2, 8));
			}
		}
		if (i.gx() == 0 && i.gz() == 0) {
			out.add(new Slot(g + ":spawn", Slot.Role.SPAWN, i.surface().offset(0, 0, 4), Direction.NORTH, 0, 0, 3, g, null, Set.of()));
		}
		return out;
	}

	private static Slot slot(Island i, String name, Slot.Role role, int dx, int dz, Direction facing, int halfAcross, int halfDeep, int height) {
		return new Slot(i.id() + ":" + name, role, i.surface().offset(dx, 0, dz), facing, halfAcross, halfDeep, height, i.id(), null,
				Set.of(i.size().name().toLowerCase(Locale.ROOT)));
	}

	// ------------------------------------------------------------------ MapGenerator

	/** The islands that touch {@code w}, nearest to its centre first. */
	public List<Island> islandsIn(Window w) {
		Area a = w.area();
		List<Island> out = new ArrayList<>();
		for (int gx = cellOf(a.minX() - CELL); gx <= cellOf(a.maxX() + CELL); gx++) {
			for (int gz = cellOf(a.minZ() - CELL); gz <= cellOf(a.maxZ() + CELL); gz++) {
				Island i = island(gx, gz);
				if (i.area().intersects(a)) {
					out.add(i);
				}
			}
		}
		out.sort((p, q) -> Double.compare(p.distanceTo(w.cx(), w.cz()), q.distanceTo(w.cx(), w.cz())));
		return out;
	}

	@Override
	public MapLayout generate(Window w) {
		List<Island> islands = islandsIn(w);
		List<Slot> slots = new ArrayList<>();
		List<MapLayout.Link> links = new ArrayList<>();
		for (Island i : islands) {
			slots.addAll(slots(i));
			for (Island n : neighbours(i)) {
				if (i.id().compareTo(n.id()) < 0 && islands.contains(n)) {
					links.add(new MapLayout.Link(i.id(), n.id(), MapLayout.LinkKind.ON_DEMAND));
				}
			}
		}
		return new MapLayout(ID, seed, new Shape(w.area(), List.copyOf(islands)), slots, links, origin().surface());
	}

	/** The ground of a window: the islands that touch it. */
	private record Shape(Area bounds, List<Island> islands) implements TerrainShape {
		@Override
		public @Nullable Span column(int x, int z) {
			for (Island i : islands) {
				if (i.contains(x, z, 0)) {
					return new Span(i.top() - i.depth(x, z) + 1, i.top());
				}
			}
			return null;
		}
	}
}
