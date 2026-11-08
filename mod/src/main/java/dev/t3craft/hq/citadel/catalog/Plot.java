package dev.t3craft.hq.citadel.catalog;

import dev.t3craft.hq.citadel.CitadelCanvas;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The box a {@link Variant} builds in, and the only door it writes through.
 *
 * <h2>Coordinates</h2>
 * {@code (x0, y0, z0)} is the min corner of the box in canvas coordinates. {@code w} is the frontage
 * (width along the front) and {@code d} the depth (front to back), so the box covers {@code w × d}
 * cells when {@code front} is NORTH or SOUTH and {@code d × w} cells (x × z) when it is EAST or WEST.
 * {@code h} is the height: local {@code y} 0..h-1 maps to canvas {@code y0 + y}. {@code y = 0} is the
 * first layer above the ground the plot stands on (the ground itself is {@code y0 - 1}, not in the box).
 *
 * <p>Local coordinates, always written as if {@code front} were NORTH:
 * <ul>
 * <li>{@code u} 0..w-1: left → right for someone standing in the plot looking out through the front
 *     (with front NORTH: west → east, i.e. {@code x = x0 + u});</li>
 * <li>{@code v} 0..d-1: front → back ({@code v = 0} is the front row; with front NORTH {@code z = z0 + v});</li>
 * <li>{@code y} 0..h-1: up.</li>
 * </ul>
 * {@link #set} translates to the canvas and rotates the block state with
 * {@link BlockState#rotate(Rotation)} (stairs, doors, trapdoors, logs' axis, fence/wall/pane sides,
 * hanging lanterns keep working), so a design written facing NORTH is right for all four fronts.
 * Directions in states are therefore local too: {@code Direction.NORTH} means "towards the front",
 * {@code SOUTH} "towards the back", {@code WEST} "left" and {@code EAST} "right".
 *
 * <p>Writes outside the box are dropped silently and counted ({@link #clipped()}): the catalog gallery
 * reports them. A plot is cheap: make one per build.
 */
public final class Plot {
	private final int x0;
	private final int y0;
	private final int z0;
	private final int w;
	private final int d;
	private final int h;
	private final Direction front;
	private final long seed;
	private final Rotation rotation;
	private int clipped;

	/**
	 * @param x0    min x of the box (canvas)
	 * @param y0    canvas y of local y = 0
	 * @param z0    min z of the box (canvas)
	 * @param w     frontage (local u extent)
	 * @param d     depth (local v extent)
	 * @param h     height (local y extent)
	 * @param front the side the plot faces (a horizontal direction)
	 * @param seed  the variant's randomness ({@link #rng})
	 */
	public Plot(int x0, int y0, int z0, int w, int d, int h, Direction front, long seed) {
		if (front.getAxis() == Direction.Axis.Y) {
			throw new IllegalArgumentException("front must be horizontal, got " + front);
		}
		if (w < 1 || d < 1 || h < 1) {
			throw new IllegalArgumentException("empty plot " + w + "x" + d + "x" + h);
		}
		this.x0 = x0;
		this.y0 = y0;
		this.z0 = z0;
		this.w = w;
		this.d = d;
		this.h = h;
		this.front = front;
		this.seed = seed;
		this.rotation = switch (front) {
			case EAST -> Rotation.CLOCKWISE_90;
			case SOUTH -> Rotation.CLOCKWISE_180;
			case WEST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/** A plot whose world footprint is {@code sizeX × sizeZ} at {@code (x0, z0)}: w/d swap for EAST/WEST. */
	public static Plot ofWorld(int x0, int y0, int z0, int sizeX, int sizeZ, int h, Direction front, long seed) {
		boolean side = front.getAxis() == Direction.Axis.X;
		return new Plot(x0, y0, z0, side ? sizeZ : sizeX, side ? sizeX : sizeZ, h, front, seed);
	}

	public int x0() {
		return x0;
	}

	public int y0() {
		return y0;
	}

	public int z0() {
		return z0;
	}

	public int w() {
		return w;
	}

	public int d() {
		return d;
	}

	public int h() {
		return h;
	}

	public Direction front() {
		return front;
	}

	public long seed() {
		return seed;
	}

	/** The rotation from local (front NORTH) to the canvas. */
	public Rotation rotation() {
		return rotation;
	}

	/** World extent along x. */
	public int sizeX() {
		return front.getAxis() == Direction.Axis.X ? d : w;
	}

	/** World extent along z. */
	public int sizeZ() {
		return front.getAxis() == Direction.Axis.X ? w : d;
	}

	/** Writes dropped because they fell outside the box. */
	public int clipped() {
		return clipped;
	}

	/** Whether the canvas cell {@code (x, y, z)} is inside the box. */
	public boolean containsWorld(int x, int y, int z) {
		return x >= x0 && x < x0 + sizeX() && z >= z0 && z < z0 + sizeZ() && y >= y0 && y < y0 + h;
	}

	// ------------------------------------------------------------------ local → canvas

	/** Whether local {@code (u, y, v)} is inside the box. */
	public boolean has(int u, int y, int v) {
		return u >= 0 && u < w && v >= 0 && v < d && y >= 0 && y < h;
	}

	/** The canvas position of local {@code (u, y, v)} (also outside the box). */
	public BlockPos world(int u, int y, int v) {
		int x;
		int z;
		switch (front) {
			case EAST -> {
				x = x0 + (d - 1 - v);
				z = z0 + u;
			}
			case SOUTH -> {
				x = x0 + (w - 1 - u);
				z = z0 + (d - 1 - v);
			}
			case WEST -> {
				x = x0 + v;
				z = z0 + (w - 1 - u);
			}
			default -> {
				x = x0 + u;
				z = z0 + v;
			}
		}
		return new BlockPos(x, y0 + y, z);
	}

	/** Where to put an anchor standing at local {@code (u, y, v)} (same as {@link #world}; for leaders who ask for anchors). */
	public BlockPos anchorPos(int u, int y, int v) {
		return world(u, y, v);
	}

	/** A local direction (NORTH = towards the front) as a canvas direction. */
	public Direction dir(Direction local) {
		return rotation.rotate(local);
	}

	/** {@code s} rotated from local to canvas. */
	public BlockState rotate(BlockState s) {
		return rotation == Rotation.NONE ? s : s.rotate(rotation);
	}

	/** Sets local {@code (u, y, v)} to {@code s} (rotated), solid layer. Outside the box: dropped and counted. */
	public void set(CitadelCanvas c, int u, int y, int v, BlockState s) {
		put(c, u, y, v, s, CitadelCanvas.SOLID);
	}

	/**
	 * Like {@link #set} on a given canvas layer: {@link CitadelCanvas#DECOR} for signs and things that
	 * hang on a wall, {@link CitadelCanvas#HANGING} for lanterns and chains hanging from above.
	 */
	public void put(CitadelCanvas c, int u, int y, int v, BlockState s, int layer) {
		if (!has(u, y, v)) {
			clipped++;
			return;
		}
		BlockPos p = world(u, y, v);
		c.put(p.getX(), p.getY(), p.getZ(), rotate(s), layer);
	}

	/** Fills the local box between two corners (inclusive, any order). */
	public void fill(CitadelCanvas c, int u1, int y1, int v1, int u2, int y2, int v2, BlockState s) {
		for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
			for (int v = Math.min(v1, v2); v <= Math.max(v1, v2); v++) {
				for (int u = Math.min(u1, u2); u <= Math.max(u1, u2); u++) {
					set(c, u, y, v, s);
				}
			}
		}
	}

	/** The state already on the canvas at local {@code (u, y, v)} (canvas orientation), or null. */
	public BlockState at(CitadelCanvas c, int u, int y, int v) {
		BlockPos p = world(u, y, v);
		return c.at(p.getX(), p.getY(), p.getZ());
	}

	/** A deterministic random for this plot: same seed and salt, same numbers. */
	public Random rng(long salt) {
		return new Random(seed ^ salt);
	}

	/**
	 * A cheap deterministic hash in 0..n-1 of a local cell (for texture mixes: cracked/mossy bricks,
	 * leaf variants…) that does not depend on the order of the calls.
	 */
	public int hash(int u, int y, int v, int n) {
		long k = seed * 0x9E3779B97F4A7C15L + u * 73856093L + y * 19349663L + v * 83492791L;
		k ^= k >>> 29;
		k *= 0xBF58476D1CE4E5B9L;
		k ^= k >>> 32;
		return (int) Math.floorMod(k, (long) n);
	}

	@Override
	public String toString() {
		return "Plot[" + x0 + "," + y0 + "," + z0 + " " + w + "x" + d + "x" + h + " front=" + front + " seed=" + seed + "]";
	}
}
