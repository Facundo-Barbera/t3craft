package dev.t3craft.client.hq;

import dev.t3craft.T3Craft;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.workstation.WorkstationClient;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqSite;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The HQ that stands in the current world, if any, and the one place that plugs it into the NPCs
 * ({@code behavior().setAnchorSource}) and the workstations ({@code setPlacementSource}). A site HQ
 * wins over the demo HQ. With an HQ, every NPC goes to a spot inside it ({@link HqWorldAnchors}) and
 * every screen sits on an HQ computer ({@link HqStations}); without one both go back to their
 * fallbacks. Client thread only.
 */
public final class HqPresence {
	/** One standing HQ: plan, where it stands, built site modules (null = all, demo). */
	public record Standing(BuildPlan plan, HqSite site, @Nullable Set<String> built) {
	}

	private static @Nullable Standing site;
	private static @Nullable Standing demo;
	private static @Nullable Standing applied;
	private static boolean installed;

	private HqPresence() {
	}

	public static void site(@Nullable Standing s) {
		site = s;
		apply();
	}

	public static void demo(@Nullable Standing s) {
		demo = s;
		apply();
	}

	public static @Nullable Standing current() {
		return site != null ? site : demo;
	}

	/** World left: forget both. */
	public static void clear() {
		site = null;
		demo = null;
		apply();
	}

	/** (Re)installs the sources when the standing HQ changed. */
	public static void apply() {
		Standing now = current();
		if (installed && sameAs(now, applied)) {
			return;
		}
		T3CraftClient c;
		try {
			c = T3CraftClient.get();
		} catch (RuntimeException e) {
			return;
		}
		if (c == null) {
			return;
		}
		applied = now;
		installed = true;
		WorkstationClient w = WorkstationClient.get();
		if (now == null) {
			c.npcs().behavior().setAnchorSource(null);
			if (w != null) {
				w.setPlacementSource(null);
			}
			return;
		}
		c.npcs().behavior().setAnchorSource(new HqWorldAnchors(now.plan(), now.site(), now.built()));
		if (w != null) {
			w.setPlacementSource(new HqStations(now.plan(), now.site(), now.built()));
		}
		T3Craft.LOGGER.info("[hq] NPCs and workstations follow the HQ at {} ({} modules built)", now.site().origin().toShortString(),
				now.built() == null ? "all" : now.built().size());
	}

	private static boolean sameAs(@Nullable Standing a, @Nullable Standing b) {
		if (a == null || b == null) {
			return a == b;
		}
		return a.plan().fingerprint().equals(b.plan().fingerprint()) && a.site().equals(b.site()) && Objects.equals(a.built(), b.built());
	}
}
