package dev.t3craft.site;

import java.util.List;

/**
 * Site lifecycle hooks (the "expansion hook" for the builders). Register with
 * {@link SiteService#addListener} or {@code SiteRuntime.addListener}. Called on the server thread;
 * an exception is logged and never stops the workflow. All default to no-op.
 */
public interface SiteListener {
	/**
	 * The snapshot of {@code expansion.box()} is durable and verified on disk, its plan is saved and
	 * the journal says BUILDING; no block of the box has been written yet. From here on the box can
	 * be undone exactly. Called for the base (confirm, move) and for every {@link SiteService#expand}.
	 * Also called again when an interrupted build is resumed (same snapshot).
	 */
	default void beforeExpansion(SiteHandle site, Expansion expansion, Snapshot snapshot, CellPlan plan) {
	}

	/** The builder reported back for {@code expansion} (not called for a cancelled build). */
	default void built(SiteHandle site, Expansion expansion, SiteBuilder.Report report) {
	}

	/** {@code expansion} was restored from its snapshot (undo pops one; a move restores them all, newest first). */
	default void restored(SiteHandle site, Expansion expansion, Restorer.Result result) {
	}

	/**
	 * A move finished restoring the old place and started the base at the new one. {@code toRebuild}
	 * are the other modules of the old site, oldest first, to be rebuilt with {@link SiteService#expand}
	 * (also available any time from {@link SiteService#modulesToRebuild()}).
	 */
	default void moved(SiteHandle site, List<SiteModule> toRebuild) {
	}
}
