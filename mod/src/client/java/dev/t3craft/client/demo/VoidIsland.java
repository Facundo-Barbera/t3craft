package dev.t3craft.client.demo;

import dev.t3craft.build.BuildPlan;
import dev.t3craft.build.Placement;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * feat/hq-void-world: the floating islands of the void HQ world, as data. Deterministic (fixed seed,
 * no randomness, no world access), all in <b>demo-relative</b> cells (relative to the demo origin,
 * unrotated: the HQ's door faces north, {@code -z}, and it grows to {@code +z}). The ground level is
 * {@code y = -1}, exactly where the superflat grass used to be, so nothing in the HQ plans changes.
 *
 * <ul>
 *   <li><b>HQ island</b> ({@link #island}): under the HQ's footprint plus {@link #MARGIN}, grass on
 *       top, two blocks of dirt, then stone with tuff that tapers downward (deeper towards the middle,
 *       irregular edge). A discreet hedge rim (persistent azalea leaves, a froglight every few cells)
 *       stands on its outer edge: NPC routes never leave the island, and the player can still hop it.
 *       It only grows: its {@link Area} is the union of every footprint the HQ ever had.</li>
 *   <li><b>Starter island</b> ({@link Starter}): a small skyblock island with an oak tree and a
 *       decorative chest, in front of the HQ (north of its door, outside anything the builders
 *       place), plus a railed plank bridge to the HQ island's edge. Placed by the server as soon as a
 *       new void HQ world starts ({@link #starterBlocks}), before the player joins.</li>
 * </ul>
 */
public final class VoidIsland {
	/** Grass around the HQ footprint (the outline wobbles between {@code MARGIN - 1} and {@code MARGIN}). */
	public static final int MARGIN = 4;
	/** Deepest island column (blocks below the grass, grass included), before plan cells below ground. */
	public static final int MAX_DEPTH = 10;
	/** Radius of the starter island. */
	public static final int STARTER_RADIUS = 3;
	/** Void between the starter island and the HQ island (the bridge). */
	public static final int GAP = 4;
	/** Fixed seed of every wobble (outline, depth, tuff speckles, rim lamps). */
	static final long SEED = 0x7633566F6964L; // "v3Void"

	private VoidIsland() {
	}

	/** Columns the HQ island covers before its margin: the union of the HQ's footprints (demo-relative xz, inclusive). */
	public record Area(int minX, int minZ, int maxX, int maxZ) {
		public Area {
			if (minX > maxX || minZ > maxZ) {
				throw new IllegalArgumentException("empty island area");
			}
		}

		public Area union(@Nullable Area o) {
			return o == null ? this : new Area(Math.min(minX, o.minX), Math.min(minZ, o.minZ), Math.max(maxX, o.maxX), Math.max(maxZ, o.maxZ));
		}

		int[] toArray() {
			return new int[] {minX, minZ, maxX, maxZ};
		}

		static @Nullable Area fromArray(int @Nullable [] a) {
			if (a == null || a.length != 4 || a[0] > a[2] || a[1] > a[3]) {
				return null;
			}
			return new Area(a[0], a[1], a[2], a[3]);
		}
	}

	/** The starter island's centre ground cell (demo-relative; its grass is at {@code y}). */
	public record Starter(int x, int y, int z) {
		/** Where the player stands: on the grass, south of the centre (facing the HQ), clear of the tree and the chest. */
		public BlockPos spawn() {
			return new BlockPos(x, y + 1, z + 1);
		}

		BlockPos trunk() {
			return new BlockPos(x + 1, y + 1, z - 1);
		}

		BlockPos chest() {
			return new BlockPos(x - 1, y + 1, z - 1);
		}

		/** First bridge cell (south edge of the island) and the last one, on the HQ island's nominal edge. */
		int bridgeStartZ() {
			return z + STARTER_RADIUS + 1;
		}

		int bridgeEndZ() {
			return z + STARTER_RADIUS + GAP + 1;
		}
	}

	/** Where the starter island goes for an HQ whose (empty-roster) footprint starts at {@code frontZ}, centred on {@code x}. */
	public static Starter starterFor(int x, int frontZ) {
		return new Starter(x, -1, frontZ - MARGIN - GAP - STARTER_RADIUS - 1);
	}

	// ------------------------------------------------------------------ noise

	private static long mix(long x, long z, long salt) {
		long h = SEED ^ salt;
		h ^= x * 0x9E3779B97F4A7C15L;
		h = Long.rotateLeft(h, 31) * 0xBF58476D1CE4E5B9L;
		h ^= z * 0x94D049BB133111EBL;
		h ^= h >>> 29;
		h *= 0xD6E8FEB86659FD93L;
		return h ^ (h >>> 32);
	}

	/** 0..n-1, stable per cell. */
	static int hash(int x, int z, long salt, int n) {
		return (int) Math.floorMod(mix(x, z, salt), (long) n);
	}

	// ------------------------------------------------------------------ HQ island shape

	/** Outline radius at a column: {@code MARGIN - 1} or {@code MARGIN}, in 2×2 blobs so the edge wobbles without single-cell spikes. */
	static int reach(int x, int z) {
		return MARGIN - 1 + hash(Math.floorDiv(x, 2), Math.floorDiv(z, 2), 1, 2);
	}

	/** Euclidean distance from a column to the core area (0 inside). */
	static double outside(Area a, int x, int z) {
		int dx = Math.max(0, Math.max(a.minX() - x, x - a.maxX()));
		int dz = Math.max(0, Math.max(a.minZ() - z, z - a.maxZ()));
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Whether the island has ground at this column. */
	public static boolean inside(Area a, int x, int z) {
		return outside(a, x, z) <= reach(x, z) + 1e-9;
	}

	/** Whether the column is island ground with void next to it (the rim). */
	static boolean edge(Area a, int x, int z) {
		return inside(a, x, z) && (!inside(a, x + 1, z) || !inside(a, x - 1, z) || !inside(a, x, z + 1) || !inside(a, x, z - 1));
	}

	/** Blocks of island in a column, grass included: tapering from 2 at the edge to {@link #MAX_DEPTH} inward. */
	static int depth(Area a, int x, int z) {
		double d = outside(a, x, z);
		double in = d > 0 ? reach(x, z) - d
				: reach(x, z) + Math.min(Math.min(x - a.minX(), a.maxX() - x), Math.min(z - a.minZ(), a.maxZ() - z));
		int j = hash(x, z, 2, 3);
		return Math.max(2, Math.min(MAX_DEPTH, 2 + (int) Math.floor(in * 0.8) + j - 1));
	}

	/** The block of a ground cell at {@code y} (top {@code -1}) above the column's bottom. */
	static BlockState ground(int x, int y, int z, int bottom) {
		if (y == -1) {
			return Blocks.GRASS_BLOCK.defaultBlockState();
		}
		if (y >= -3) {
			return Blocks.DIRT.defaultBlockState();
		}
		if (y == bottom || hash(x, z * 31 + y, 3, 5) == 0) {
			return Blocks.TUFF.defaultBlockState();
		}
		return Blocks.STONE.defaultBlockState();
	}

	/** Rim hedge, or a froglight in it every few cells (the rim's only light: it lights the grass strip at night). */
	static BlockState rim(int x, int z) {
		if (hash(x, z, 4, 7) == 0) {
			return Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
		}
		return Blocks.AZALEA_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
	}

	/** The bridge lands here: no rim, so the walkway stays open. */
	static boolean bridgeGap(@Nullable Starter s, int x, int z) {
		return s != null && Math.abs(x - s.x()) <= 1 && z <= s.bridgeEndZ() + MARGIN;
	}

	/** Whether the island (with a block of slack) reaches the starter island or its bridge: then it swallows them. */
	static boolean absorbs(Area a, Starter s) {
		for (int x = s.x() - STARTER_RADIUS - 1; x <= s.x() + STARTER_RADIUS + 1; x++) {
			for (int z = s.z() - STARTER_RADIUS - 1; z <= s.bridgeStartZ(); z++) {
				if (outside(a, x, z) <= MARGIN + 1) {
					return true;
				}
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ HQ island plan

	/**
	 * What the plan already takes, in demo-relative cells (packed with {@link BlockPos#asLong}), plus the
	 * lowest plan cell below ground per column (packed xz, y = 0). The island never writes over them.
	 */
	public record Taken(LongSet cells, it.unimi.dsi.fastutil.longs.Long2IntMap lowest) {
		public static Taken of(Iterable<BlockPos> planCells) {
			LongSet cells = new LongOpenHashSet();
			it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap lowest = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
			for (BlockPos p : planCells) {
				cells.add(p.asLong());
				if (p.getY() < 0) {
					long k = BlockPos.asLong(p.getX(), 0, p.getZ());
					lowest.put(k, Math.min(lowest.getOrDefault(k, 0), p.getY()));
				}
			}
			return new Taken(cells, lowest);
		}

		boolean has(int x, int y, int z) {
			return cells.contains(BlockPos.asLong(x, y, z));
		}

		int lowest(int x, int z) {
			return lowest.getOrDefault(BlockPos.asLong(x, 0, z), 0);
		}
	}

	/**
	 * The HQ island for core {@code area} as one builders' part (layered, bottom first, so every block is
	 * supported), skipping every cell the plan takes. On a grown island ({@code previous} smaller) the old
	 * rim that now lies inside is cleared; if the island reaches the starter island its tree, chest and
	 * rails are cleared too (its ground simply becomes part of the island).
	 */
	public static BuildPlan island(Area area, Taken taken, @Nullable Starter starter, @Nullable Area previous) {
		List<Placement> out = new ArrayList<>();
		boolean absorbed = starter != null && absorbs(area, starter);
		Starter bridge = absorbed ? null : starter;
		int r = MARGIN + 1;
		LongSet rimCells = new LongOpenHashSet();
		for (int x = area.minX() - r; x <= area.maxX() + r; x++) {
			for (int z = area.minZ() - r; z <= area.maxZ() + r; z++) {
				if (!inside(area, x, z)) {
					continue;
				}
				int bottom = Math.min(-depth(area, x, z), taken.lowest(x, z) - 2);
				for (int y = -1; y >= bottom; y--) {
					if (!taken.has(x, y, z)) {
						out.add(new Placement(new BlockPos(x, y, z), ground(x, y, z, bottom)));
					}
				}
				if (taken.has(x, 0, z)) {
					continue;
				}
				if (edge(area, x, z) && !bridgeGap(bridge, x, z)) {
					out.add(new Placement(new BlockPos(x, 0, z), rim(x, z)));
					rimCells.add(BlockPos.asLong(x, 0, z));
				} else if (previous != null && !previous.equals(area) && edge(previous, x, z) && !bridgeGap(bridge, x, z)) {
					out.add(new Placement(new BlockPos(x, 0, z), Blocks.AIR.defaultBlockState())); // old rim, now inland
					rimCells.add(BlockPos.asLong(x, 0, z));
				}
			}
		}
		if (absorbed) {
			for (BlockPos p : starterDecor(starter)) {
				if (!taken.has(p.getX(), p.getY(), p.getZ()) && !rimCells.contains(p.asLong()) && p.getY() >= 0) {
					out.add(new Placement(p, Blocks.AIR.defaultBlockState()));
				}
			}
		}
		return BuildPlan.of(out).layered();
	}

	// ------------------------------------------------------------------ starter island

	/** Starter ground columns: a rounded disc whose rim wobbles a little. */
	static boolean starterInside(Starter s, int x, int z) {
		int dx = x - s.x();
		int dz = z - s.z();
		int r2 = STARTER_RADIUS * STARTER_RADIUS + hash(x, z, 5, 3);
		return dx * dx + dz * dz <= r2;
	}

	/** Every block of the starter island, its tree, chest and bridge (demo-relative, ground first). */
	public static List<Placement> starterBlocks(Starter s) {
		List<Placement> out = new ArrayList<>();
		int top = s.y();
		for (int x = s.x() - STARTER_RADIUS - 1; x <= s.x() + STARTER_RADIUS + 1; x++) {
			for (int z = s.z() - STARTER_RADIUS - 1; z <= s.z() + STARTER_RADIUS + 1; z++) {
				if (!starterInside(s, x, z)) {
					continue;
				}
				double d = Math.sqrt((x - s.x()) * (x - s.x()) + (z - s.z()) * (z - s.z()));
				int depth = Math.max(1, (int) Math.round((STARTER_RADIUS + 1 - d) * 1.4) + hash(x, z, 6, 2));
				int bottom = top - depth + 1;
				for (int y = top; y >= bottom; y--) {
					BlockState st = y == top ? Blocks.GRASS_BLOCK.defaultBlockState()
							: y >= top - 2 ? Blocks.DIRT.defaultBlockState()
							: hash(x, z * 31 + y, 3, 4) == 0 ? Blocks.TUFF.defaultBlockState() : Blocks.STONE.defaultBlockState();
					out.add(new Placement(new BlockPos(x, y, z), st));
				}
			}
		}
		// bridge: spruce deck three wide, fences on both sides, the walkway in the middle
		BlockState deck = Blocks.SPRUCE_PLANKS.defaultBlockState();
		for (int z = s.bridgeStartZ() - 1; z <= s.bridgeEndZ(); z++) {
			for (int dx = -1; dx <= 1; dx++) {
				out.add(new Placement(new BlockPos(s.x() + dx, top, z), deck));
			}
		}
		for (int z = s.bridgeStartZ(); z <= s.bridgeEndZ(); z++) {
			BlockState rail = Blocks.SPRUCE_FENCE.defaultBlockState()
					.setValue(CrossCollisionBlock.NORTH, z > s.bridgeStartZ())
					.setValue(CrossCollisionBlock.SOUTH, z < s.bridgeEndZ());
			out.add(new Placement(new BlockPos(s.x() - 1, top + 1, z), rail));
			out.add(new Placement(new BlockPos(s.x() + 1, top + 1, z), rail));
		}
		// oak tree: 5 logs, a two-layer leaf skirt and a small crown (persistent leaves)
		BlockPos t = s.trunk();
		BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
		for (int y = 3; y <= 6; y++) {
			int rad = y <= 4 ? 2 : 1;
			for (int dx = -rad; dx <= rad; dx++) {
				for (int dz = -rad; dz <= rad; dz++) {
					boolean corner = Math.abs(dx) == rad && Math.abs(dz) == rad;
					if (dx == 0 && dz == 0 && y <= 5 || corner && (y == 6 || hash(t.getX() + dx, t.getZ() + dz, 7 + y, 2) == 0)) {
						continue;
					}
					out.add(new Placement(t.offset(dx, y - 1, dz), leaves));
				}
			}
		}
		for (int y = 0; y < 5; y++) {
			out.add(new Placement(t.above(y), Blocks.OAK_LOG.defaultBlockState()));
		}
		// decorative chest, facing the HQ (south), and a lantern by the bridge
		out.add(new Placement(s.chest(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH)));
		out.add(new Placement(new BlockPos(s.x() - 2, top + 1, s.z() + 2), Blocks.LANTERN.defaultBlockState()));
		return out;
	}

	/** What stands on the starter island and its bridge (cleared when the HQ island swallows it). */
	static List<BlockPos> starterDecor(Starter s) {
		List<BlockPos> out = new ArrayList<>();
		for (Placement p : starterBlocks(s)) {
			if (p.pos().getY() > s.y()) {
				out.add(p.pos());
			}
		}
		return out;
	}
}
