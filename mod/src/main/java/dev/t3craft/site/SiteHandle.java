package dev.t3craft.site;

import java.util.List;

/**
 * Read-only view of the active site for code that builds on it (HQ design, animated builders).
 * From {@link SiteService#handle()} ({@code SiteRuntime.handle(server)} in game), server thread.
 * Immutable: ask again after an expansion, undo or move.
 *
 * <p>World geometry:
 * <ul>
 *   <li>{@link #reserve()}: growth envelope + apron, full height; nothing else may be built there
 *   by the site, and every module must lie in {@link SiteRequest#envelope()};</li>
 *   <li>{@link #footprint(Rect)} / {@link #moduleArea(Rect)}: a module's pad layer / the
 *   {@link SiteLimits#HEADROOM} layers above it, for a plan-relative rect;</li>
 *   <li>{@link #origin()}: pad centre of the base one above the pad, with the entrance side;
 *   {@link #toWorld} maps plan-relative cells (BuildPlan convention, see {@link SiteFrame}).</li>
 * </ul>
 *
 * @param modules built (or being built) modules, oldest first; the first is the base
 * @param pending modules recorded by a move that the builders still have to rebuild here
 */
public record SiteHandle(String id, String dimension, SiteJournal.Phase phase, CellPlan.Origin origin, SiteRequest request, Box reserve,
		List<SiteModule> modules, List<SiteModule> pending) {
	public int padY() {
		return origin.y() - 1;
	}

	public CellPlan.Facing facing() {
		return origin.facing();
	}

	public Box footprint(Rect rect) {
		return rect.toWorld(origin, padY());
	}

	public Box moduleArea(Rect rect) {
		Box f = footprint(rect);
		return new Box(f.minX(), padY() + 1, f.minZ(), f.maxX(), padY() + SiteLimits.HEADROOM, f.maxZ());
	}

	public int[] toWorld(int dx, int dy, int dz) {
		return SiteFrame.toWorld(origin, dx, dy, dz);
	}

	public boolean built() {
		return phase == SiteJournal.Phase.BUILT;
	}

	/** True when {@code rect} fits the envelope and does not overlap a module already built. */
	public boolean fits(Rect rect) {
		if (!request.envelope().contains(rect)) {
			return false;
		}
		for (SiteModule m : modules) {
			if (m.rect().intersects(rect)) {
				return false;
			}
		}
		return true;
	}

	static SiteHandle of(SiteJournal j) {
		return new SiteHandle(j.siteId(), j.dimension(), j.phase(), j.origin(), j.request(), j.reserve(), j.modules(), j.pending());
	}
}
