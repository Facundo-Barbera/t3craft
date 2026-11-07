package dev.t3craft.build;

/**
 * Where a charged {@link BuildJob} takes its blocks from, by item id ({@code "minecraft:oak_planks"}).
 * Called on the server thread only. Implementations: the player's inventory and storage chests by the
 * site ({@code dev.t3craft.survival.BuildMaterials}); fakes in tests.
 */
public interface MaterialSupply {
	/** How many of {@code itemId} can be taken right now. */
	int count(String itemId);

	/** Takes up to {@code n} of {@code itemId}; returns how many were actually taken. */
	int take(String itemId, int n);

	/** Hint before a batch of {@link #count} calls (start of a layer, a retry): rescan containers etc. */
	default void refresh() {
	}

	/** Nothing to take from (e.g. the player left): everything is missing. */
	MaterialSupply EMPTY = new MaterialSupply() {
		@Override
		public int count(String itemId) {
			return 0;
		}

		@Override
		public int take(String itemId, int n) {
			return 0;
		}
	};
}
