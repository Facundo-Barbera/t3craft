package dev.t3craft.mapgen.islands;

import dev.t3craft.mapgen.Noise;

/**
 * Smooth deterministic 2D value noise for the island shapes: {@link Noise#unit} on a lattice of {@code scale}
 * blocks, blended with a smoothstep. Same {@code (seed, x, z, scale, salt)}, same value, on every machine.
 */
final class IslandNoise {
	private IslandNoise() {
	}

	/** {@code [0, 1)}, varying smoothly over about {@code scale} blocks. */
	static double smooth(long seed, int x, int z, double scale, long salt) {
		double fx = x / scale;
		double fz = z / scale;
		double floorX = Math.floor(fx);
		double floorZ = Math.floor(fz);
		int x0 = (int) floorX;
		int z0 = (int) floorZ;
		double tx = fade(fx - floorX);
		double tz = fade(fz - floorZ);
		double a = Noise.unit(seed, x0, z0, salt);
		double b = Noise.unit(seed, x0 + 1, z0, salt);
		double c = Noise.unit(seed, x0, z0 + 1, salt);
		double d = Noise.unit(seed, x0 + 1, z0 + 1, salt);
		return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
	}

	/** Two octaves ({@code scale} and {@code scale / 2}, weights 2:1) mapped to {@code [-1, 1]}. */
	static double signed2(long seed, int x, int z, double scale, long salt) {
		double v = (2 * smooth(seed, x, z, scale, salt) + smooth(seed, x, z, scale / 2, salt + 1)) / 3;
		return 2 * v - 1;
	}

	private static double fade(double t) {
		return t * t * (3 - 2 * t);
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}
}
