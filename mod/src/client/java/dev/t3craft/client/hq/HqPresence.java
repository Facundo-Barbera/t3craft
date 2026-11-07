package dev.t3craft.client.hq;

import dev.t3craft.T3Craft;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.workstation.WorkstationClient;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqSite;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The HQ that stands in the current world, if any, and the one place that plugs it into the NPCs
 * ({@code behavior().setAnchorSource}) and the workstations ({@code setPlacementSource}). A site HQ
 * wins over the demo HQ. With an HQ, every NPC goes to a spot inside it ({@link HqWorldAnchors}) and
 * every screen sits on an HQ computer ({@link HqStations}); without one both go back to their
 * fallbacks. Client thread only.
 *
 * <p>Also the one place that tells the blocks module which bindings the HQ's monitors and lamps must
 * have ({@code BlocksClient.setHqBindings}): the site HQ's plan as built ({@code HqBindings.world}),
 * or the demo's live roster ({@link #demoBindings}). No-op when the blocks module is off.
 */
public final class HqPresence {
	/** One standing HQ: plan, where it stands, built site modules (null = all, demo). */
	public record Standing(BuildPlan plan, HqSite site, @Nullable Set<String> built) {
	}

	private static @Nullable Standing site;
	private static @Nullable Standing demo;
	private static @Nullable Standing applied;
	private static boolean installed;
	private static @Nullable Supplier<@Nullable Map<Long, dev.t3craft.blocks.Binding>> demoBindings;
	private static @Nullable Object boundTo;

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

	/** The demo's live fixture bindings (used while the demo HQ is the standing one; null = none). */
	public static void demoBindings(@Nullable Supplier<@Nullable Map<Long, dev.t3craft.blocks.Binding>> b) {
		demoBindings = b;
		bindFixtures(current());
	}

	public static @Nullable Standing current() {
		return site != null ? site : demo;
	}

	/** World left: forget both. */
	public static void clear() {
		site = null;
		demo = null;
		demoBindings = null;
		apply();
	}

	/** (Re)installs the sources when the standing HQ changed. */
	public static void apply() {
		Standing now = current();
		bindFixtures(now);
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

	/** Monitors and lamps follow the standing HQ: the site plan's bindings, or the demo's live ones. */
	private static void bindFixtures(@Nullable Standing now) {
		dev.t3craft.client.blocks.BlocksClient b = dev.t3craft.client.blocks.BlocksClient.get();
		if (b == null) {
			return;
		}
		Object key = now == null ? null : now == site ? now : demoBindings;
		if (key == boundTo) {
			return;
		}
		boundTo = key;
		if (now == null) {
			b.setHqBindings(null);
		} else if (now == site) {
			Map<Long, dev.t3craft.blocks.Binding> m = dev.t3craft.blocks.HqBindings.world(now.plan(), now.site());
			b.setHqBindings(() -> m);
		} else {
			b.setHqBindings(demoBindings);
		}
	}

	private static boolean sameAs(@Nullable Standing a, @Nullable Standing b) {
		if (a == null || b == null) {
			return a == b;
		}
		return a.plan().fingerprint().equals(b.plan().fingerprint()) && a.site().equals(b.site()) && Objects.equals(a.built(), b.built());
	}
}
