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
		Map<String, EnvPrefs> environments,
		Portable portable,
		Hud hud,
		ChatPrefs chat) {

	public static final int SCHEMA_VERSION = 1;
	public static final String DEFAULT_BRIDGE_URL = "ws://127.0.0.1:7878/";
	public static final double DEFAULT_WINDOW_HOURS = 24;
	public static final int DEFAULT_MAX = 40;
	public static final double MAX_WINDOW_HOURS = 24 * 365;
	public static final int MAX_PER_ENV_LIMIT = 1000;

	public T3CraftSettings {
		environments = Map.copyOf(new LinkedHashMap<>(environments));
		portable = portable == null ? Portable.defaults() : portable;
		hud = hud == null ? Hud.defaults() : hud;
		chat = chat == null ? ChatPrefs.DEFAULT : chat;
	}

	/** Pre-{@code chat} shape (kept so callers written before that section still compile). */
	public T3CraftSettings(int schemaVersion, String mode, String bridgeUrl, @Nullable String bridgeKey, double recentWindowHours,
			int maxPerEnvironment, boolean includeSubagents, Notifications notifications, Map<String, EnvPrefs> environments,
			Portable portable, Hud hud) {
		this(schemaVersion, mode, bridgeUrl, bridgeKey, recentWindowHours, maxPerEnvironment, includeSubagents, notifications, environments,
				portable, hud, ChatPrefs.DEFAULT);
	}

	/** Pre-{@code hud} shape (kept so callers written before that section still compile). */
	public T3CraftSettings(int schemaVersion, String mode, String bridgeUrl, @Nullable String bridgeKey, double recentWindowHours,
			int maxPerEnvironment, boolean includeSubagents, Notifications notifications, Map<String, EnvPrefs> environments,
			Portable portable) {
		this(schemaVersion, mode, bridgeUrl, bridgeKey, recentWindowHours, maxPerEnvironment, includeSubagents, notifications, environments,
				portable, Hud.defaults());
	}

	/** Pre-{@code portable} shape (kept so callers written before that section still compile). */
	public T3CraftSettings(int schemaVersion, String mode, String bridgeUrl, @Nullable String bridgeKey, double recentWindowHours,
			int maxPerEnvironment, boolean includeSubagents, Notifications notifications, Map<String, EnvPrefs> environments) {
		this(schemaVersion, mode, bridgeUrl, bridgeKey, recentWindowHours, maxPerEnvironment, includeSubagents, notifications, environments,
				Portable.defaults());
	}

	public static T3CraftSettings defaults() {
		return new T3CraftSettings(SCHEMA_VERSION, "direct", DEFAULT_BRIDGE_URL, null, DEFAULT_WINDOW_HOURS, DEFAULT_MAX, false,
				Notifications.defaults(), Map.of(), Portable.defaults(), Hud.defaults());
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
				notifications, m, portable, hud, chat);
	}

	public T3CraftSettings withoutEnv(String id) {
		Map<String, EnvPrefs> m = new LinkedHashMap<>(environments);
		m.remove(id);
		return new T3CraftSettings(schemaVersion, mode, bridgeUrl, bridgeKey, recentWindowHours, maxPerEnvironment, includeSubagents,
				notifications, m, portable, hud, chat);
	}

	public T3CraftSettings withNotifications(Notifications n) {
		return new T3CraftSettings(schemaVersion, mode, bridgeUrl, bridgeKey, recentWindowHours, maxPerEnvironment, includeSubagents, n,
				environments, portable, hud, chat);
	}

	public T3CraftSettings withVisibility(double windowHours, int max, boolean subagents) {
		return new T3CraftSettings(schemaVersion, mode, bridgeUrl, bridgeKey, windowHours, max, subagents, notifications, environments,
				portable, hud, chat);
	}

	public T3CraftSettings withPortable(Portable p) {
		return new T3CraftSettings(schemaVersion, mode, bridgeUrl, bridgeKey, recentWindowHours, maxPerEnvironment, includeSubagents,
				notifications, environments, p, hud, chat);
	}

	public T3CraftSettings withChat(ChatPrefs c) {
		return new T3CraftSettings(schemaVersion, mode, bridgeUrl, bridgeKey, recentWindowHours, maxPerEnvironment, includeSubagents,
				notifications, environments, portable, hud, c);
	}

	public T3CraftSettings withHud(Hud h) {
		return new T3CraftSettings(schemaVersion, mode, bridgeUrl, bridgeKey, recentWindowHours, maxPerEnvironment, includeSubagents,
				notifications, environments, portable, h, chat);
	}

	public long recentWindowMs() {
		return (long) (recentWindowHours * 3_600_000L);
	}

	@Override
	public String toString() {
		return "T3CraftSettings[v" + schemaVersion + ", mode=" + mode + ", bridgeUrl=" + bridgeUrl + ", bridgeKey="
				+ (bridgeKey == null ? "none" : "<redacted>") + ", window=" + recentWindowHours + "h, max=" + maxPerEnvironment
				+ ", subagents=" + includeSubagents + ", " + notifications + ", envs=" + environments.keySet() + ", " + portable + ", " + hud + ", " + chat + "]";
	}

	/**
	 * Portable T3 Terminal section ({@code "portable": {...}}, additive: a file without it reads as
	 * the defaults). {@code heldScreen}: the terminal held in either hand shows a live summary on its
	 * screen, map-style; false = plain item. Default on.
	 */
	public record Portable(boolean heldScreen) {
		public static Portable defaults() {
			return new Portable(true);
		}

		public Portable withHeldScreen(boolean v) {
			return new Portable(v);
		}
	}

	/**
	 * HUD status bar section ({@code "hud": {...}}, additive: a file without it reads as the
	 * defaults). {@code statusBar}: show the bar with the working / needs you / error counts.
	 * {@code position}: {@code top} | {@code bottom}. {@code align}: {@code left} | {@code center} |
	 * {@code right}. {@code hideWhenIdle}: hide the bar while every count is zero.
	 */
	/**
	 * Thread browser preferences ({@code chat} in the file, added by feat/chat; absent in older files,
	 * which then read as {@link #DEFAULT}). {@code hiddenEnvironments}: environment ids the unified
	 * thread list filters out (environments are only a filter there).
	 */
	public record ChatPrefs(java.util.Set<String> hiddenEnvironments) {
		public static final ChatPrefs DEFAULT = new ChatPrefs(java.util.Set.of());

		public ChatPrefs {
			hiddenEnvironments = hiddenEnvironments == null ? java.util.Set.of()
					: java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(hiddenEnvironments));
		}

		public ChatPrefs withHiddenEnvironments(java.util.Set<String> ids) {
			return new ChatPrefs(ids);
		}
	}

	public record Hud(boolean statusBar, String position, String align, boolean hideWhenIdle, boolean hologram) {
		public static final java.util.List<String> POSITIONS = java.util.List.of("top", "bottom");
		public static final java.util.List<String> ALIGNS = java.util.List.of("left", "center", "right");

		public Hud {
			position = POSITIONS.contains(position) ? position : "top";
			align = ALIGNS.contains(align) ? align : "center";
		}

		/** Pre-{@code hologram} shape (feat/hub-hologram), kept so existing callers still compile: hologram on. */
		public Hud(boolean statusBar, String position, String align, boolean hideWhenIdle) {
			this(statusBar, position, align, hideWhenIdle, true);
		}

		public static Hud defaults() {
			return new Hud(true, "top", "center", true, true);
		}

		public boolean bottom() {
			return "bottom".equals(position);
		}

		public Hud withStatusBar(boolean v) {
			return new Hud(v, position, align, hideWhenIdle, hologram);
		}

		public Hud withPlacement(String pos, String al) {
			return new Hud(statusBar, pos, al, hideWhenIdle, hologram);
		}

		public Hud withHideWhenIdle(boolean v) {
			return new Hud(statusBar, position, align, v, hologram);
		}

		/** Hub hologram (feat/hub-hologram) on/off. */
		public Hud withHologram(boolean v) {
			return new Hud(statusBar, position, align, hideWhenIdle, v);
		}

		/** Next placement in the settings cycle: top left → top center → … → bottom right → top left. */
		public Hud nextPlacement() {
			int i = POSITIONS.indexOf(position) * ALIGNS.size() + ALIGNS.indexOf(align) + 1;
			i %= POSITIONS.size() * ALIGNS.size();
			return withPlacement(POSITIONS.get(i / ALIGNS.size()), ALIGNS.get(i % ALIGNS.size()));
		}
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
			boolean connectionToasts,
			Attention attention) {
		/** Pre-{@code attention} shape (feat/18-attention-signals), kept so existing callers still compile: default signals. */
		public Notifications(boolean enabled, boolean chat, boolean toast, boolean sound, boolean soundAttention, boolean soundDone,
				boolean soundError, boolean connectionToasts) {
			this(enabled, chat, toast, sound, soundAttention, soundDone, soundError, connectionToasts, Attention.defaults());
		}

		public static Notifications defaults() {
			return new Notifications(true, true, true, true, true, true, true, true, Attention.defaults());
		}

		public Notifications withAttention(Attention a) {
			return new Notifications(enabled, chat, toast, sound, soundAttention, soundDone, soundError, connectionToasts, a);
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
			return new Notifications(v, chat, toast, sound, soundAttention, soundDone, soundError, connectionToasts, attention);
		}
	}

	/**
	 * Silent attention signals for when the game is in the background ({@code notifications.attention}
	 * in the file, additive: absent reads as the defaults; docs/features/attention-signals.md). Each
	 * one is gated by {@code notifications.enabled} too. {@code windowTitle}: the window title carries
	 * the pending summary. {@code taskbarFlash}: ask the OS for attention (taskbar flash / dock bounce)
	 * when the window is not focused. {@code actionBar}: {@code off} | {@code also} (action bar next
	 * to the toast) | {@code instead} (action bar replaces the toast). No sound lives here.
	 */
	public record Attention(boolean windowTitle, boolean taskbarFlash, String actionBar) {
		public static final java.util.List<String> BAR_MODES = java.util.List.of("off", "also", "instead");

		public Attention {
			actionBar = BAR_MODES.contains(actionBar) ? actionBar : "off";
		}

		public static Attention defaults() {
			return new Attention(true, true, "off");
		}

		public boolean barShown() {
			return !"off".equals(actionBar);
		}

		public boolean barReplacesToast() {
			return "instead".equals(actionBar);
		}

		public Attention withWindowTitle(boolean v) {
			return new Attention(v, taskbarFlash, actionBar);
		}

		public Attention withTaskbarFlash(boolean v) {
			return new Attention(windowTitle, v, actionBar);
		}

		public Attention withActionBar(String v) {
			return new Attention(windowTitle, taskbarFlash, v);
		}

		/** Next action bar mode in the settings cycle: off → also → instead → off. */
		public Attention nextActionBar() {
			return withActionBar(BAR_MODES.get((BAR_MODES.indexOf(actionBar) + 1) % BAR_MODES.size()));
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
