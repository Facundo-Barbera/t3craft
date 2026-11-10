package dev.t3craft.client.islands;

import dev.t3craft.client.demo.DemoWorldSpec;
import dev.t3craft.mapgen.islands.IslandField;
import dev.t3craft.mapgen.islands.IslandMix;

/**
 * What identifies the floating-islands world: its save folder, name, the height of its spawn island, and the
 * seed and size mix of the world open now (kept per world in {@link IslandsState}).
 */
final class IslandsWorld {
	/** Save folder under {@code saves/}; fixed, so "create vs open" is a lookup (like the HQ world's). */
	static final String FOLDER = "t3craft-islands";
	static final String NAME = "T3Craft Islands";
	/** "Islnds" in ASCII: the seed of a world made without one (and of worlds from before seeds were saved). */
	static final long DEFAULT_SEED = 0x49736C6E6473L;
	/** y of the grass layer of the spawn island. */
	static final int BASE_Y = 64;

	/**
	 * Seed of the islands world open now. Not a constant: {@link IslandsClient} sets it from the world's
	 * {@link IslandsState} ({@link #use}) before {@link IslandsRuntime} starts, and the runtime makes its field with
	 * {@link #field()}.
	 */
	static volatile long SEED = DEFAULT_SEED;
	private static volatile IslandMix mix = IslandMix.DEFAULT;

	private IslandsWorld() {
	}

	/** Makes {@code seed} and {@code m} the open world's: what {@link #field()} builds the field from. */
	static void use(long seed, IslandMix m) {
		SEED = seed;
		mix = m;
	}

	static IslandMix mix() {
		return mix;
	}

	/** The field of the open world (seed, base height and mix). */
	static IslandField field() {
		return new IslandField(SEED, BASE_Y, mix);
	}

	/** Creative, peaceful, fixed noon, nothing spawns: the HQ world's rules. */
	static DemoWorldSpec spec() {
		return DemoWorldSpec.defaults().withFolder(FOLDER, NAME).withVoid(true);
	}
}
