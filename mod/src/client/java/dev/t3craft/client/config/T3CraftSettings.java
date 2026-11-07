package dev.t3craft.client.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Validated, immutable snapshot of {@code config/t3craft.json} (schema {@value #SCHEMA_VERSION}).
 * Pure data: no Minecraft classes, so it is unit-tested and safe to keep across a hot-swap (a new
 * snapshot replaces the old one; nothing mutates it).
 *
 * <p>The keys {@code mode}, {@code bridgeUrl}, {@code bridgeKey}, {@code visibility.*} and
 * {@code includeSubagents} are the ones {@code dev.t3craft.client.ModConfig} already reads; this
 * class keeps their meaning and location so both readers agree.
 *
 * <p>{@code bridgeKey} is a secret: {@link #toString()} redacts it and no screen displays it.
 */
public record T3CraftSettings(
		int schemaVersion,
		String mode,
		String bridgeUrl,
		@Nullable String bridgeKey,
		double recentWindowHours,
		int maxPerEnvironment,
		boolean includeSubagents,
		Notifications notifications,
		Map<String, EnvPrefs> environments) {

	public static final int SCHEMA_VERSION = 1;
	public static final String DEFAULT_BRIDGE_URL = "ws://127.0.0.1:7878/";
	public static final double DEFAULT_WINDOW_HOURS = 24;
	public static final int DEFAULT_MAX = 40;
	public static final double MAX_WINDOW_HOURS = 24 * 365;
	public static final int MAX_PER_ENV_LIMIT = 1000;

	public T3CraftSettings {
		environments = Map.copyOf(new LinkedHashMap<>(environments));
	}

	public static T3CraftSettings defaults() {
		return new T3CraftSettings(SCHEMA_VERSION, "direct", DEFAULT_BRIDGE_URL, null, DEFAULT_WINDOW_HOURS, DEFAULT_MAX, false,
				Notifications.defaults(), Map.of());
	}

	/** Preferences of one environment; defaults for an environment that has no entry. */
	public EnvPrefs env(String id) {
		return environments.getOrDefault(id, EnvPrefs.DEFAULT);
	}

	public T3CraftSettings withEnv(String id, EnvPrefs prefs) {
		Map<String, EnvPrefs> m = new LinkedHashMap<>(environments);
		if (prefs.equals(EnvPrefs.DEFAULT)) {
			m.remove(id);
		} else {
			m.put(id, prefs);
		}
		return new T3CraftSettings(schemaVersion, mode, bridgeUrl, bridgeKey, recentWindowHours, maxPerEnvironment, includeSubagents,
				notifications, m);
	}

	public T3CraftSettings withoutEnv(String id) {
		Map<String, EnvPrefs> m = new LinkedHashMap<>(environments);
		m.remove(id);
		return new T3CraftSettings(schemaVersion, mode, bridgeUrl, bridgeKey, recentWindowHours, maxPerEnvironment, includeSubagents,
				notifications, m);
	}

	public T3CraftSettings withNotifications(Notifications n) {
		return new T3CraftSettings(schemaVersion, mode, bridgeUrl, bridgeKey, recentWindowHours, maxPerEnvironment, includeSubagents, n,
				environments);
	}

	public T3CraftSettings withVisibility(double windowHours, int max, boolean subagents) {
		return new T3CraftSettings(schemaVersion, mode, bridgeUrl, bridgeKey, windowHours, max, subagents, notifications, environments);
	}

	public long recentWindowMs() {
		return (long) (recentWindowHours * 3_600_000L);
	}

	@Override
	public String toString() {
		return "T3CraftSettings[v" + schemaVersion + ", mode=" + mode + ", bridgeUrl=" + bridgeUrl + ", bridgeKey="
				+ (bridgeKey == null ? "none" : "<redacted>") + ", window=" + recentWindowHours + "h, max=" + maxPerEnvironment
				+ ", subagents=" + includeSubagents + ", " + notifications + ", envs=" + environments.keySet() + "]";
	}

	/**
	 * Notification toggles. {@code enabled} is the master switch (also flipped by the "mute"
	 * keybind); the others refine it.
	 */
	public record Notifications(
			boolean enabled,
			boolean chat,
			boolean toast,
			boolean sound,
			boolean soundAttention,
			boolean soundDone,
			boolean soundError,
			boolean connectionToasts) {
		public static Notifications defaults() {
			return new Notifications(true, true, true, true, true, true, true, true);
		}

		public boolean allowChat() {
			return enabled && chat;
		}

		public boolean allowToast() {
			return enabled && toast;
		}

		public boolean allowConnectionToast() {
			return enabled && connectionToasts;
		}

		/** {@code kind}: attention | done | error (the names Transitions/Notifier use). */
		public boolean allowSound(String kind) {
			if (!enabled || !sound) {
				return false;
			}
			return switch (kind == null ? "" : kind.toLowerCase(java.util.Locale.ROOT)) {
				case "attention", "needs_attention" -> soundAttention;
				case "done" -> soundDone;
				case "error" -> soundError;
				default -> true;
			};
		}

		public Notifications withEnabled(boolean v) {
			return new Notifications(v, chat, toast, sound, soundAttention, soundDone, soundError, connectionToasts);
		}
	}

	/**
	 * Per-environment preferences. {@code label}: display name (the canonical label lives in
	 * {@code config/t3craft/environments.json}; this is only an override used when that file
	 * cannot be rewritten). {@code color}: 0xRRGGBB accent or null. {@code windowHours} /
	 * {@code max}: null means "use the global value".
	 */
	public record EnvPrefs(
			@Nullable String label,
			@Nullable Integer color,
			boolean visible,
			boolean showNeedsYou,
			boolean showWorking,
			boolean showRecent,
			@Nullable Double windowHours,
			@Nullable Integer max) {
		public static final EnvPrefs DEFAULT = new EnvPrefs(null, null, true, true, true, true, null, null);

		public double effectiveWindowHours(T3CraftSettings s) {
			return windowHours != null ? windowHours : s.recentWindowHours();
		}

		public int effectiveMax(T3CraftSettings s) {
			return max != null ? max : s.maxPerEnvironment();
		}

		public String labelOr(String fallback) {
			return label == null || label.isBlank() ? fallback : label;
		}

		public EnvPrefs withLabel(@Nullable String v) {
			return new EnvPrefs(v, color, visible, showNeedsYou, showWorking, showRecent, windowHours, max);
		}

		public EnvPrefs withColor(@Nullable Integer v) {
			return new EnvPrefs(label, v, visible, showNeedsYou, showWorking, showRecent, windowHours, max);
		}

		public EnvPrefs withVisible(boolean v) {
			return new EnvPrefs(label, color, v, showNeedsYou, showWorking, showRecent, windowHours, max);
		}

		public EnvPrefs withFilter(boolean needsYou, boolean working, boolean recent, @Nullable Double hours, @Nullable Integer cap) {
			return new EnvPrefs(label, color, visible, needsYou, working, recent, hours, cap);
		}
	}
}
