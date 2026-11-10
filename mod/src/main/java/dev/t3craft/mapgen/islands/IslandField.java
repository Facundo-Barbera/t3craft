package dev.t3craft.mapgen.islands;

import dev.t3craft.mapgen.Area;
import dev.t3craft.mapgen.MapGenerator;
import dev.t3craft.mapgen.MapLayout;
import dev.t3craft.mapgen.Noise;
import dev.t3craft.mapgen.Slot;
import dev.t3craft.mapgen.TerrainShape;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The endless field of floating islands (generator {@value #ID}). One island per cell of a square grid: its size
 * class comes from the cell's hash and the field's {@link IslandMix}, its centre is jittered inside the cell, its
 * height varies by a few blocks and its biome ({@link IslandBiome}) follows a region of 3x3 cells most of the
 * time. Cell {@code (0, 0)} is always the large spawn island at {@code baseY}. There is no ground between
 * islands: two islands of neighbouring cells are only <i>linked</i> ({@link #neighbours}); the world builds the
 * bridge when a player nears the edge ({@link BridgePlanner}).
 *
 * <p>Spacing: the cell side is {@link #cellFor}{@code (mix)}, worked out from the biggest size the mix uses
 * (and the large spawn island): {@code 2 * (reach + MARGIN + MIN_JITTER)}. So every island, whatever its outline,
 * stays inside its cell with {@value #MARGIN} blocks to spare, islands never touch (at least
 * {@code 2 * MARGIN} blocks of void between cell neighbours) and a mix of small islands packs them closer than a
 * mix with extra large ones. Each island jitters as far as its own size leaves room for, up to
 * {@value #MAX_JITTER}.
 *
 * <p>The map is endless, so {@link #generate} lays out a {@link Window}: the islands that touch it, their slots
 * and the on-demand links between them. Everything is a pure function of the seed and the mix; islands are
 * cached (a field is used from one thread at a time, the cache is synchronized anyway).
 */
public final class IslandField implements MapGenerator<IslandField.Window> {
	public static final String ID = "floating-islands";
	/** Largest displacement of an island's centre from its cell's centre. */
	public static final int MAX_JITTER = 12;
	/** Jitter every cell leaves room for, even with its biggest island. */
	public static final int MIN_JITTER = 4;
	/** Clear border between an island's outline and its cell's edge. */
	public static final int MARGIN = 6;
	/** Islands lie within this many blocks above or below {@code baseY}. */
	public static final int HEIGHT_RANGE = 4;
	/** Islands kept in the cache. */
	private static final int CACHE = 4096;

	/**
	 * The mix the two-argument constructor uses. A shim for the runtime, which makes its field with
	 * {@code new IslandField(seed, baseY)}: the client sets the open world's mix here before the runtime starts
	 * ({@code IslandsWorld.use}). New code passes the mix explicitly.
	 */
	private static volatile IslandMix sessionMix = IslandMix.DEFAULT;

	private final long seed;
	private final int baseY;
	private final IslandMix mix;
	private final int cell;
	private final Map<Long, Island> cache = Collections.synchronizedMap(new LinkedHashMap<Long, Island>(256, 0.75F, true) {
		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Island> eldest) {
			return size() > CACHE;
		}
	});

	/** A field with the session's mix ({@link #useSessionMix}, {@link IslandMix#DEFAULT} until set). */
	public IslandField(long seed, int baseY) {
		this(seed, baseY, sessionMix);
	}

	/** @param baseY y of the grass layer of the spawn island */
	public IslandField(long seed, int baseY, IslandMix mix) {
		this.seed = seed;
		this.baseY = baseY;
		this.mix = Objects.requireNonNull(mix, "mix");
		this.cell = cellFor(mix);
	}

	/** Sets the mix {@link #IslandField(long, int)} uses from now on (the open world's). */
	public static void useSessionMix(IslandMix mix) {
		sessionMix = Objects.requireNonNull(mix, "mix");
	}

	public static IslandMix sessionMix() {
		return sessionMix;
	}

	/** The cell side for a mix: room for its biggest island (never less than the large spawn island) and the margins. */
	public static int cellFor(IslandMix mix) {
		IslandSize big = mix.largest().ordinal() >= IslandSize.LARGE.ordinal() ? mix.largest() : IslandSize.LARGE;
		int side = 2 * (big.reach() + MARGIN + MIN_JITTER);
		return side + (side & 1);
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

	public IslandMix mix() {
		return mix;
	}

	/** Distance between the grid lines of the cells. */
	public int cell() {
		return cell;
	}

	// ------------------------------------------------------------------ islands

	public static String idOf(int gx, int gz) {
		return "i_" + gx + "_" + gz;
	}

	/** The cell of an island id {@code i_<gx>_<gz>}, or null when it is not one. */
	public static int @Nullable [] cellOfId(String id) {
		if (!id.startsWith("i_")) {
			return null;
		}
		String[] p = id.substring(2).split("_");
		if (p.length != 2) {
			return null;
		}
		try {
			return new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1])};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** The island of cell {@code (gx, gz)}. */
	public Island island(int gx, int gz) {
		long key = ((long) gx << 32) | (gz & 0xFFFFFFFFL);
		Island i = cache.get(key);
		if (i == null) {
			i = compute(gx, gz);
			cache.put(key, i);
		}
		return i;
	}

	private Island compute(int gx, int gz) {
		boolean spawn = gx == 0 && gz == 0;
		IslandSize size = spawn ? IslandSize.LARGE : mix.pick(Noise.hash(seed, gx, gz, 1, mix.total()));
		int room = Math.max(0, Math.min(MAX_JITTER, cell / 2 - size.reach() - MARGIN));
		int jx = spawn ? 0 : Noise.hash(seed, gx, gz, 2, 2 * room + 1) - room;
		int jz = spawn ? 0 : Noise.hash(seed, gx, gz, 3, 2 * room + 1) - room;
		int top = spawn ? baseY : baseY + Noise.hash(seed, gx, gz, 4, 2 * HEIGHT_RANGE + 1) - HEIGHT_RANGE;
		Island bare = new Island(idOf(gx, gz), gx, gz, size, biomeOf(gx, gz), gx * cell + jx, top, gz * cell + jz, seed, form(gx, gz, size, spawn),
				List.of(), List.of());
		return IslandSlots.lay(bare);
	}

	/** The cell's biome: its 3x3 region's in 6 cases out of 10, one of its own otherwise. */
	private IslandBiome biomeOf(int gx, int gz) {
		IslandBiome[] all = IslandBiome.values();
		if (Noise.hash(seed, gx, gz, 22, 10) < 6) {
			return all[Noise.hash(seed, Math.floorDiv(gx + 1, 3), Math.floorDiv(gz + 1, 3), 21, all.length)];
		}
		return all[Noise.hash(seed, gx, gz, 23, all.length)];
	}

	/** The shape parameters of a cell's island; the spawn island is rounder, with a flat plaza in the middle. */
	private Island.Form form(int gx, int gz, IslandSize size, boolean spawn) {
		double k = spawn ? 0.5 : 1.0;
		double tau = 2 * Math.PI;
		double a2 = k * (0.04 + 0.08 * Noise.unit(seed, gx, gz, 7));
		double a3 = k * (0.03 + 0.05 * Noise.unit(seed, gx, gz, 9));
		double a5 = k * (0.02 + 0.03 * Noise.unit(seed, gx, gz, 11));
		double edgeMax = Island.EDGE_BASE + Island.EDGE_PER_RADIUS * size.radius();
		double edge = edgeMax * (spawn ? 0.5 : 0.6 + 0.4 * Noise.unit(seed, gx, gz, 13));
		double belly = 0.75 + 0.6 * Noise.unit(seed, gx, gz, 14);
		int terraces = switch (size) {
			case XLARGE -> 2;
			case LARGE -> 1;
			default -> 0;
		};
		return new Island.Form(a2, tau * Noise.unit(seed, gx, gz, 8), a3, tau * Noise.unit(seed, gx, gz, 10), a5, tau * Noise.unit(seed, gx, gz, 12),
				edge, belly, terraces, spawn ? 9 : 0);
	}

	/** The spawn island. */
	public Island origin() {
		return island(0, 0);
	}

	/** The cell a column falls in. */
	public int cellOf(int coord) {
		return Math.floorDiv(coord + cell / 2, cell);
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
		int reach = radius + cell;
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

	/** The slots of an island, in map coordinates (see {@link IslandSlots} for what each size holds). */
	public List<Slot> slots(Island i) {
		return i.slots();
	}

	// ------------------------------------------------------------------ MapGenerator

	/** The islands that touch {@code w}, nearest to its centre first. */
	public List<Island> islandsIn(Window w) {
		Area a = w.area();
		List<Island> out = new ArrayList<>();
		for (int gx = cellOf(a.minX() - cell); gx <= cellOf(a.maxX() + cell); gx++) {
			for (int gz = cellOf(a.minZ() - cell); gz <= cellOf(a.maxZ() + cell); gz++) {
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
		return new MapLayout(ID, seed, new Shape(w.area(), List.copyOf(islands)), slots, links, origin().surface().offset(0, 0, 4));
	}

	/** The ground of a window: the islands that touch it. */
	private record Shape(Area bounds, List<Island> islands) implements TerrainShape {
		@Override
		public @Nullable Span column(int x, int z) {
			for (Island i : islands) {
				if (i.contains(x, z, 0)) {
					return new Span(i.bottom(x, z) - i.hang(x, z), i.surfaceY(x, z));
				}
			}
			return null;
		}
	}
}
