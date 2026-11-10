package dev.t3craft.client.islands;

import dev.t3craft.client.demo.DemoWorldSpec;

/** What identifies the floating-islands world: its save folder, name, seed and the height of its spawn island. */
final class IslandsWorld {
	/** Save folder under {@code saves/}; fixed, so "create vs open" is a lookup (like the HQ world's). */
	static final String FOLDER = "t3craft-islands";
	static final String NAME = "T3Craft Islands";
	/** "Islnds" in ASCII: arbitrary but fixed; the whole field follows from it. */
	static final long SEED = 0x49736C6E6473L;
	/** y of the grass layer of the spawn island. */
	static final int BASE_Y = 64;

	private IslandsWorld() {
	}

	/** Creative, peaceful, fixed noon, nothing spawns: the HQ world's rules. */
	static DemoWorldSpec spec() {
		return DemoWorldSpec.defaults().withFolder(FOLDER, NAME).withVoid(true);
	}
}
