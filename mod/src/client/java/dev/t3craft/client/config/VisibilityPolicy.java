package dev.t3craft.client.config;

import dev.t3craft.client.config.T3CraftSettings.EnvPrefs;
import dev.t3craft.t3.NpcView;
import dev.t3craft.t3.ThreadShell;
import dev.t3craft.t3.ThreadState;
import dev.t3craft.t3.Visibility;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-environment visibility filter on top of {@link Visibility} (pure, unit-tested).
 *
 * <p>Integration point (direct mode): {@code DirectController.npcs} currently calls
 * {@code Visibility.select(v.threads(), nowMs, options)} for every environment. Replace that call
 * with {@code VisibilityPolicy.select(ConfigManager.get().settings(), envId, v.threads(), nowMs)}
 * to get hidden environments, the needs-you / working / recent toggles and the per-environment
 * window and cap. Until then {@link #filterViews} can post-filter the NPC list (everything except a
 * per-environment window narrower than the global one, which needs thread timestamps).
 */
public final class VisibilityPolicy {
	private VisibilityPolicy() {
	}

	/** The {@link Visibility.Options} for one environment (its window and cap, global subagents flag). */
	public static Visibility.Options optionsFor(T3CraftSettings s, String envId) {
		EnvPrefs p = s.env(envId);
		return new Visibility.Options((long) (p.effectiveWindowHours(s) * 3_600_000L), p.effectiveMax(s), s.includeSubagents());
	}

	/**
	 * Options that let through everything any environment could show (widest window, highest cap).
	 * Feed these to a global-only filter, then narrow with {@link #filterViews}.
	 */
	public static Visibility.Options loosest(T3CraftSettings s) {
		double hours = s.recentWindowHours();
		int max = s.maxPerEnvironment();
		for (EnvPrefs p : s.environments().values()) {
			hours = Math.max(hours, p.effectiveWindowHours(s));
			max = Math.max(max, p.effectiveMax(s));
		}
		return new Visibility.Options((long) (hours * 3_600_000L), max, s.includeSubagents());
	}

	/** Threads of one environment that get an NPC, ranked like {@link Visibility#select}. */
	public static List<ThreadShell> select(T3CraftSettings s, String envId, Iterable<ThreadShell> threads, long nowMs) {
		EnvPrefs p = s.env(envId);
		if (!p.visible()) {
			return List.of();
		}
		Visibility.Options o = optionsFor(s, envId);
		Visibility.Options uncapped = new Visibility.Options(o.recentWindowMs(), Integer.MAX_VALUE, o.includeSubagents());
		List<ThreadShell> out = new ArrayList<>();
		for (ThreadShell t : Visibility.select(threads, nowMs, uncapped)) {
			if (out.size() >= o.maxPerEnvironment()) {
				break;
			}
			if (allowsTier(p, Visibility.tier(t, nowMs, uncapped))) {
				out.add(t);
			}
		}
		return out;
	}

	/** 0 needs you, 1 working, 2 recent (see {@link Visibility#tier}). */
	public static boolean allowsTier(EnvPrefs p, int tier) {
		return switch (tier) {
			case 0 -> p.showNeedsYou();
			case 1 -> p.showWorking();
			case 2 -> p.showRecent();
			default -> false;
		};
	}

	/**
	 * Post-filter for an already filtered and ranked NPC list (bridge mode, or direct mode before
	 * the {@link #select} integration): drops hidden environments, applies the toggles by state and
	 * the per-environment cap, keeping order.
	 */
	public static List<NpcView> filterViews(T3CraftSettings s, List<NpcView> views) {
		Map<String, Integer> counts = new HashMap<>();
		List<NpcView> out = new ArrayList<>();
		for (NpcView v : views) {
			EnvPrefs p = s.env(v.env());
			if (!p.visible()) {
				continue;
			}
			int tier = v.state() == ThreadState.NEEDS_ATTENTION ? 0 : v.state() == ThreadState.WORKING ? 1 : 2;
			if (!allowsTier(p, tier)) {
				continue;
			}
			int n = counts.merge(v.env(), 1, Integer::sum);
			if (n <= p.effectiveMax(s)) {
				out.add(v);
			}
		}
		return out;
	}
}
