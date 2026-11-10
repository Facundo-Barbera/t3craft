package dev.t3craft.mapgen;

/**
 * Stateless deterministic hashing for generators: the same {@code (seed, x, z, salt)} gives the same
 * value on every machine and every run (no {@code java.util.Random}, no clock).
 */
public final class Noise {
	private Noise() {
	}

	public static long mix(long seed, long x, long z, long salt) {
		long h = seed ^ (salt * 0x9E3779B97F4A7C15L);
		h ^= x * 0x9E3779B97F4A7C15L;
		h = Long.rotateLeft(h, 31) * 0xBF58476D1CE4E5B9L;
		h ^= z * 0x94D049BB133111EBL;
		h ^= h >>> 29;
		h *= 0xD6E8FEB86659FD93L;
		return h ^ (h >>> 32);
	}

	/** {@code 0..n-1}, stable per {@code (seed, x, z, salt)}. */
	public static int hash(long seed, int x, int z, long salt, int n) {
		return (int) Math.floorMod(mix(seed, x, z, salt), (long) n);
	}

	/** {@code [0, 1)}, stable per {@code (seed, x, z, salt)}. */
	public static double unit(long seed, int x, int z, long salt) {
		return (mix(seed, x, z, salt) >>> 11) * 0x1.0p-53;
	}
}
