package dev.t3craft.site;

import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * What the site feature was doing, persisted in {@code state.nbt} before each phase starts so an
 * interrupted build/undo/move (crash, quit) can be finished or reverted on the next load.
 *
 * @param siteId the active site (snapshot + plan files), or null when there is none
 * @param nextSiteId during a move: the new site whose snapshot and plan are already saved
 * @param restoreMode while RESTORING: what the player chose for their own changes
 */
public record SiteJournal(Phase phase, @Nullable String siteId, @Nullable String nextSiteId, String dimension,
		Restorer.@Nullable Mode restoreMode, long updatedMs) {
	public enum Phase {
		/** No site. */
		NONE,
		/** Snapshot and plan are durable; blocks are being written (or were, until interrupted). */
		BUILDING,
		/** Build finished. */
		BUILT,
		/** Restoring {@code siteId} from its snapshot (undo, or the first half of a move). */
		RESTORING
	}

	public static final SiteJournal EMPTY = new SiteJournal(Phase.NONE, null, null, "", null, 0L);

	public CompoundTag toTag() {
		CompoundTag t = new CompoundTag();
		t.putString("format", "t3craft-site-journal");
		t.putInt("version", 1);
		t.putString("phase", phase.name());
		if (siteId != null) {
			t.putString("site", siteId);
		}
		if (nextSiteId != null) {
			t.putString("next", nextSiteId);
		}
		t.putString("dimension", dimension);
		if (restoreMode != null) {
			t.putString("mode", restoreMode.name());
		}
		t.putLong("updated", updatedMs);
		return t;
	}

	public static SiteJournal fromTag(CompoundTag t) throws java.io.IOException {
		if (!"t3craft-site-journal".equals(t.getStringOr("format", ""))) {
			throw new java.io.IOException("state.nbt is not a t3craft site journal");
		}
		Phase phase;
		try {
			phase = Phase.valueOf(t.getStringOr("phase", "NONE"));
		} catch (IllegalArgumentException e) {
			throw new java.io.IOException("unknown site phase " + t.getStringOr("phase", ""));
		}
		String site = t.getString("site").orElse(null);
		if (phase != Phase.NONE && site == null) {
			throw new java.io.IOException("site journal in phase " + phase + " without a site id");
		}
		Restorer.Mode mode = null;
		String m = t.getString("mode").orElse(null);
		if (m != null) {
			try {
				mode = Restorer.Mode.valueOf(m);
			} catch (IllegalArgumentException e) {
				throw new java.io.IOException("unknown restore mode " + m);
			}
		}
		return new SiteJournal(phase, site, t.getString("next").orElse(null), t.getStringOr("dimension", ""), mode, t.getLongOr("updated", 0L));
	}
}
