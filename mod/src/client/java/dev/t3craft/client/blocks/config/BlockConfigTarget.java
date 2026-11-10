package dev.t3craft.client.blocks.config;

import org.jspecify.annotations.Nullable;

/**
 * The block a config page edits: a real block in the world ({@link WorldBlockTarget}, writes go through the
 * integrated server) or a fake one (the gallery). Client thread.
 */
public interface BlockConfigTarget {
	/** What the block holds now (may lag a write by a tick or two: the change comes back with the block update). */
	BlockConfig current();

	/** How many blocks the page edits at once (a monitor wall is one screen of several blocks). */
	int cells();

	/** Null when the page may write; otherwise why not (shown as a note, the page is read-only). */
	@Nullable String readOnlyReason();

	/** Writes {@code config} into every cell. */
	void write(BlockConfig config);

	/** Clears the manual flag of every cell so the HQ binds the block again (its options stay). */
	void resetToAutomatic();
}
