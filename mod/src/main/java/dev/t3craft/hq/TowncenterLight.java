package dev.t3craft.hq;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Integrated lighting for hybrid-towncenter: before a module is closed, every cell an NPC can stand
 * in gets block light {@code >= MIN}, counting <em>only the module's own blocks</em> (light never
 * enters cells the module does not own, so neighbours can only add light: a building stays lit
 * whatever is built next to it, and adding a building never changes another one).
 *
 * <p>The designed lights (ceiling panels, floor inlays, lit curbs) come first; this pass then walks
 * the walkable cells in a fixed order (y, z, x) and, for each one still darker than {@code MIN},
 * swaps one full block for a flush lamp: the floor under it, else the ceiling over its head, else a
 * wall beside it. Never a free-standing lantern. Pure and deterministic like the builders.
 *
 * <p>Light model (a lower bound of the game's): a block stops light when it renders solid, uses its
 * shape for occlusion (slabs, stairs) or dampens it fully; any other block costs
 * {@code max(1, dampening)} levels; a low block someone stands in (slab, step, bench) takes the best
 * of its neighbours minus one. Walkable: feet on a block with a collision box (or inside a low
 * block), nothing at the head; counted when it is at street level ({@code y <= 3}) or covered (some
 * block of the module within 12 above), so roofs and parapets nobody walks on are left alone.
 */
final class TowncenterLight {
	/** Minimum block light on walkable cells. */
	static final int MIN = 10;
	private static final int COVER = 12;
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private final PlanBuilder.Canvas c;
	private final Box b;
	private final int nx;
	private final int ny;
	private final int nz;
	private final BlockState[] st;
	private final byte[] lv;
	private final List<List<Integer>> buckets = new ArrayList<>();

	private TowncenterLight(PlanBuilder.Canvas c) {
		this.c = c;
		this.b = c.box();
		nx = b.maxX() - b.minX() + 1;
		ny = b.maxY() - b.minY() + 1;
		nz = b.maxZ() - b.minZ() + 1;
		st = new BlockState[nx * ny * nz];
		lv = new byte[st.length];
		for (int i = 0; i <= 15; i++) {
			buckets.add(new ArrayList<>());
		}
	}

	/** Lights the open canvas {@code c}; returns how many lamps were added. */
	static int fill(PlanBuilder.Canvas c, BlockState lamp, Predicate<BlockState> keep) {
		TowncenterLight l = new TowncenterLight(c);
		l.load();
		return l.light(lamp, keep);
	}

	// ------------------------------------------------------------------ model

	static boolean opaque(BlockState s) {
		return !s.isAir() && (s.isSolidRender() || s.useShapeForLightOcclusion() || s.getLightDampening() >= 15);
	}

	static int damp(BlockState s) {
		return Math.max(1, s.getLightDampening());
	}

	static boolean passable(BlockState s, BlockPos p) {
		return s.isAir() || s.getCollisionShape(EmptyBlockGetter.INSTANCE, p).isEmpty() && !(s.getBlock() instanceof LiquidBlock);
	}

	static boolean standable(BlockState s, BlockPos p) {
		return !s.isAir() && !s.getCollisionShape(EmptyBlockGetter.INSTANCE, p).isEmpty();
	}

	// ------------------------------------------------------------------ grid

	private int idx(int x, int y, int z) {
		return ((y - b.minY()) * nz + (z - b.minZ())) * nx + (x - b.minX());
	}

	private boolean in(int x, int y, int z) {
		return x >= b.minX() && x <= b.maxX() && y >= b.minY() && y <= b.maxY() && z >= b.minZ() && z <= b.maxZ();
	}

	private void load() {
		for (int y = b.minY(); y <= b.maxY(); y++) {
			for (int z = b.minZ(); z <= b.maxZ(); z++) {
				for (int x = b.minX(); x <= b.maxX(); x++) {
					if (!c.owns(x, y, z)) {
						continue; // null: not ours, light never enters
					}
					BlockState s = c.get(x, y, z);
					int i = idx(x, y, z);
					st[i] = s == null ? AIR : s;
					int e = st[i].getLightEmission();
					if (e > 0) {
						lv[i] = (byte) e;
						buckets.get(e).add(i);
					}
				}
			}
		}
		spread();
	}

	private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	/** Bucket flood fill from the highest level down. */
	private void spread() {
		for (int level = 15; level >= 1; level--) {
			List<Integer> q = buckets.get(level);
			for (int n = 0; n < q.size(); n++) {
				int i = q.get(n);
				if (lv[i] != level) {
					continue;
				}
				int x = i % nx + b.minX();
				int z = (i / nx) % nz + b.minZ();
				int y = i / (nx * nz) + b.minY();
				for (int[] d : DIRS) {
					int xx = x + d[0];
					int yy = y + d[1];
					int zz = z + d[2];
					if (!in(xx, yy, zz)) {
						continue;
					}
					int j = idx(xx, yy, zz);
					BlockState s = st[j];
					if (s == null || opaque(s)) {
						continue;
					}
					int nl = level - damp(s);
					if (nl > lv[j]) {
						lv[j] = (byte) nl;
						buckets.get(nl).add(j);
					}
				}
			}
			q.clear();
		}
	}

	/**
	 * Light at a cell: its own level; for a cell we do not own, or a low block light does not enter in
	 * this model (a slab or a step someone stands in), the best of its neighbours minus one.
	 */
	private int lightAt(int x, int y, int z) {
		int i = idx(x, y, z);
		if (st[i] != null && !opaque(st[i])) {
			return lv[i];
		}
		int best = 0;
		for (int[] d : DIRS) {
			int xx = x + d[0];
			int yy = y + d[1];
			int zz = z + d[2];
			if (!in(xx, yy, zz)) {
				continue;
			}
			int j = idx(xx, yy, zz);
			if (st[j] != null && (!opaque(st[j]) || st[j].getLightEmission() > 0)) {
				best = Math.max(best, lv[j] - 1);
			}
		}
		return best;
	}

	/** A low block an NPC stands <em>in</em> (bottom slab, step, bench): it has a collision box but is not a full cube. */
	static boolean low(BlockState s) {
		return !s.isAir() && !s.isSolidRender() && s.useShapeForLightOcclusion();
	}

	/**
	 * Whether an NPC may stand with its feet in this cell: on top of a block (air or a plant at the feet)
	 * or inside a low block; head room above; counted at street level or under cover.
	 */
	private boolean walkable(int x, int y, int z) {
		BlockState feet = st[idx(x, y, z)];
		BlockPos fp = new BlockPos(x, y, z);
		if (feet != null && !passable(feet, fp)) {
			if (!low(feet)) {
				return false;
			}
		} else {
			if (y - 1 < b.minY()) {
				return false;
			}
			BlockState below = st[idx(x, y - 1, z)];
			if (below == null || !standable(below, new BlockPos(x, y - 1, z))) {
				return false;
			}
		}
		if (in(x, y + 1, z)) {
			BlockState head = st[idx(x, y + 1, z)];
			if (head != null && !passable(head, new BlockPos(x, y + 1, z))) {
				return false;
			}
		}
		if (y <= 3) {
			return true;
		}
		for (int yy = y + 2; yy <= Math.min(b.maxY(), y + COVER); yy++) {
			BlockState s = st[idx(x, yy, z)];
			if (s != null && !s.isAir()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether a block may become a lamp: a full cube that is not a fixture, not already a light, not a
	 * log or a pillar (axis blocks: tree trunks, columns), with no plant or sign standing on it.
	 */
	private boolean swappable(int x, int y, int z, Predicate<BlockState> keep) {
		if (!in(x, y, z)) {
			return false;
		}
		BlockState s = st[idx(x, y, z)];
		if (s == null || !s.isSolidRender() || s.getLightEmission() > 0 || keep.test(s) || s.hasProperty(BlockStateProperties.AXIS)) {
			return false;
		}
		if (in(x, y + 1, z)) {
			BlockState up = st[idx(x, y + 1, z)];
			if (up != null && !up.isAir() && passable(up, new BlockPos(x, y + 1, z))) {
				return false;
			}
		}
		return true;
	}

	private int light(BlockState lamp, Predicate<BlockState> keep) {
		int added = 0;
		int e = lamp.getLightEmission();
		for (int pass = 0; pass < 3; pass++) {
			int before = added;
			for (int y = b.minY() + 1; y <= b.maxY(); y++) {
				for (int z = b.minZ(); z <= b.maxZ(); z++) {
					for (int x = b.minX(); x <= b.maxX(); x++) {
						if (!walkable(x, y, z) || lightAt(x, y, z) >= MIN) {
							continue;
						}
						int[][] spots = {{x, y - 1, z}, {x, y + 2, z}, {x + 1, y, z}, {x - 1, y, z}, {x, y, z + 1}, {x, y, z - 1}, {x + 1, y + 1, z},
								{x - 1, y + 1, z}, {x, y + 1, z + 1}, {x, y + 1, z - 1}, {x + 1, y - 1, z}, {x - 1, y - 1, z}, {x, y - 1, z + 1},
								{x, y - 1, z - 1}};
						for (int[] p : spots) {
							if (!swappable(p[0], p[1], p[2], keep)) {
								continue;
							}
							int j = idx(p[0], p[1], p[2]);
							c.set(p[0], p[1], p[2], lamp);
							st[j] = lamp;
							lv[j] = (byte) e;
							buckets.get(e).add(j);
							spread();
							added++;
							break;
						}
					}
				}
			}
			if (added == before) {
				break;
			}
		}
		return added;
	}
}
