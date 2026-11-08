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
 *
 * <p><b>fix/hq-gate-npcs: the one gate</b> ({@link #allows}). Nothing of the mod's world presence
 * (thread NPCs and their panels, helpers, behaviour overlays, workstations, the hologram, the hub
 * beacon, the schedule board) exists unless the HQ of this world stands: the game runs its own
 * (integrated) server, an HQ is standing here with its base built, and the player is in the HQ's
 * dimension. On a server without the mod (or any remote server) the gate is always shut, so the mod
 * puts nothing in the world; the T3 screen (key Y) does not depend on it. See docs/feat-hq-gate.md.
 */
public final class HqPresence {
	/** Where the demo HQ and a site HQ without a recorded dimension stand. */
	public static final String OVERWORLD = "minecraft:overworld";

	/**
	 * One standing HQ: plan, where it stands, built site modules (null = all, demo), and the
	 * dimension it stands in (its identifier, e.g. {@code minecraft:overworld}).
	 */
	public record Standing(BuildPlan plan, HqSite site, @Nullable Set<String> built, String dimension) {
		public Standing {
			dimension = dimension == null || dimension.isBlank() ? OVERWORLD : dimension;
		}

		public Standing(BuildPlan plan, HqSite site, @Nullable Set<String> built) {
			this(plan, site, built, OVERWORLD);
		}

		/** Built enough to receive anyone: the demo HQ (built == null) or a site whose base stands. */
		public boolean ready() {
			return built == null || built.contains(dev.t3craft.hq.construct.HqSiteFrame.BASE);
		}
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

	/**
	 * fix/hq-gate-npcs: whether the mod may put anything in the world the player is in now. True
	 * only on the game's own server ({@code hasSingleplayerServer}), with a standing, ready HQ
	 * ({@link Standing#ready}) in the player's dimension. Never true on a remote server.
	 */
	public static boolean allows(net.minecraft.client.@Nullable Minecraft mc) {
		if (mc == null || mc.level == null || mc.player == null || !mc.hasSingleplayerServer()) {
			return false;
		}
		Standing s = current();
		return s != null && allows(s, true, mc.level.dimension().identifier().toString());
	}

	/** {@link #allows(net.minecraft.client.Minecraft)} without the game: pure. */
	public static boolean allows(@Nullable Standing s, boolean integratedServer, @Nullable String dimension) {
		return integratedServer && s != null && s.ready() && s.dimension().equals(dimension);
	}

	/** {@link #allows(net.minecraft.client.Minecraft)} for the running game. */
	public static boolean allows() {
		return allows(net.minecraft.client.Minecraft.getInstance());
	}

	/** The standing HQ only when {@link #allows} lets it be shown here, else null. */
	public static @Nullable Standing shown(net.minecraft.client.@Nullable Minecraft mc) {
		return allows(mc) ? current() : null;
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
		return a.plan().fingerprint().equals(b.plan().fingerprint()) && a.site().equals(b.site()) && Objects.equals(a.built(), b.built())
				&& a.dimension().equals(b.dimension());
	}
}
