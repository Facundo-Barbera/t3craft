package dev.t3craft.client.config;

import dev.t3craft.client.ModConfig;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.config.T3CraftSettings.Notifications;
import dev.t3craft.client.demo.DemoClient;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Control;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Field;
import dev.t3craft.client.t3ui.kit.T3Panel;
import dev.t3craft.client.t3ui.kit.T3Select;
import dev.t3craft.client.t3ui.kit.T3Tabs;
import dev.t3craft.client.t3ui.kit.T3Toggle;
import dev.t3craft.outbound.OutboundStore;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import org.jspecify.annotations.Nullable;

/**
 * T3Craft settings as a page of the UI kit (docs/features/ui-kit.md), like T3 Code's settings page: tabs
 * (General, Notifications, Connections) over a scrolling body and a footer with the status line,
 * "Save" and "Done". It lives inside {@code T3Screen} (the ⚙ of the sidebar and the ⋯ menu) and as the
 * screen {@link SettingsScreen} (Mod Menu, the J key, {@code /t3craft config}).
 *
 * <p>Same behaviour as the vanilla screen it replaces: edits go to a draft and are written on "Save"
 * (then the mod reloads its sources, like {@code /t3craft reload}); the bridge key is never shown, only
 * whether one is set; a config from a newer T3Craft is read-only.
 */
public final class SettingsPanel extends T3Panel {
	private static final int ROW = 18;
	private static final int MAX_W = 440;
	private static final List<String> TABS = List.of("General", "Notifications", "Connections");

	private final EnvironmentActions actions;
	private T3CraftSettings draft;
	private int tab;
	private String windowText;
	private String maxText;
	private @Nullable T3Field windowField;
	private @Nullable T3Field maxField;
	private String status = "";
	private Tone statusTone = Tone.MUTED;

	public SettingsPanel(EnvironmentActions actions) {
		this.actions = actions;
		this.draft = ConfigManager.get().settings();
		this.windowText = formatNumber(draft.recentWindowHours());
		this.maxText = Integer.toString(draft.maxPerEnvironment());
		if (ConfigManager.get().readOnly()) {
			say("config/t3craft.json is from a newer T3Craft: changes will not be saved", Tone.ERROR);
		} else if (!ConfigManager.get().warnings().isEmpty()) {
			say(ConfigManager.get().warnings().size() + " config warning(s) fixed with defaults (see log)", Tone.WARN);
		}
	}

	/** Opens on tab {@code i} (0 General, 1 Notifications, 2 Connections): the gallery. */
	public SettingsPanel tab(int i) {
		this.tab = Math.max(0, Math.min(i, TABS.size() - 1));
		return this;
	}

	@Override
	public String title() {
		return "T3Craft settings";
	}

	private void say(String msg, Tone tone) {
		status = msg;
		statusTone = tone;
	}

	// ---- layout ----------------------------------------------------------------------------------------

	@Override
	protected int headerHeight() {
		return T3Control.H + 6;
	}

	@Override
	protected int footerHeight() {
		return T3Control.H + 8;
	}

	@Override
	protected void arrangeHeader(Rect header) {
		T3Tabs tabs = new T3Tabs(font(), TABS, tab, i -> {
			tab = i;
			host().relayout();
		});
		pin(tabs, header.x(), header.y(), header.w(), T3Control.H + 2);
	}

	@Override
	protected void arrangeFooter(Rect footer) {
		int y = footer.y() + 6;
		int bw = 64;
		pin(new T3Button("Done", () -> host().back()), footer.right() - bw, y, bw, T3Control.H);
		pin(new T3Button("Save", T3Button.Style.PRIMARY, this::save), footer.right() - 2 * bw - 4, y, bw, T3Control.H);
		String line = T3Draw.wrap(font(), status, footer.w() - 2 * bw - 16, 1).getFirst();
		pinText(footer.x() + 2, y + (T3Control.H - 8) / 2, line, statusTone);
	}

	@Override
	protected int arrange(Rect body, int y0) {
		int w = Math.min(MAX_W, body.w() - 8);
		int x = body.x() + (body.w() - w) / 2 - 3;
		int y = y0 + 4;
		return switch (tab) {
			case 0 -> general(x, y, w);
			case 1 -> notifications(x, y, w);
			default -> connections(x, y, w);
		};
	}

	private int heading(int x, int y, int w, String s) {
		return text(x + 4, y, w, s, Tone.TITLE) + 3;
	}

	private int toggle(int x, int y, int w, String label, boolean value, Consumer<Boolean> set) {
		place(new T3Toggle(label, value, set), x, y, w, T3Control.H);
		return y + ROW;
	}

	private int general(int x, int y, int w) {
		T3Field window = windowField();
		T3Field max = maxField();
		y = heading(x, y, w, "Default filter");
		y = text(x + 4, y, w - 8, "Recent window (hours)", Tone.MUTED);
		placeField(window, x, y + 1, w);
		y += T3Control.H + 7;
		y = text(x + 4, y, w - 8, "Max per environment", Tone.MUTED);
		placeField(max, x, y + 1, w);
		y += T3Control.H + 5;
		y = toggle(x, y, w, "Subagents", draft.includeSubagents(),
				v -> draft = draft.withVisibility(draft.recentWindowHours(), draft.maxPerEnvironment(), v));
		y += 8;

		y = heading(x, y, w, "Display");
		y = toggle(x, y, w, "Held terminal screen", draft.portable().heldScreen(), v -> draft = draft.withPortable(draft.portable().withHeldScreen(v)));
		y = toggle(x, y, w, "HUD status bar", draft.hud().statusBar(), v -> draft = draft.withHud(draft.hud().withStatusBar(v)));
		y = toggle(x, y, w, "Hide bar when idle", draft.hud().hideWhenIdle(), v -> draft = draft.withHud(draft.hud().withHideWhenIdle(v)));
		T3Button[] placement = new T3Button[1];
		placement[0] = place(new T3Button(placementText(), () -> {
			draft = draft.withHud(draft.hud().nextPlacement());
			placement[0].setLabel(placementText());
		}), x, y, w, T3Control.H);
		y += ROW;
		y = toggle(x, y, w, "Hub hologram", draft.hud().hologram(), v -> draft = draft.withHud(draft.hud().withHologram(v)));
		y = toggle(x, y, w, "Attention arrow", draft.hud().attentionArrow(), v -> draft = draft.withHud(draft.hud().withAttentionArrow(v)));
		y += 8;

		String mode = "Mode: " + draft.mode() + (draft.mode().equals("bridge")
				? " · bridge key " + (draft.bridgeKey() == null ? "not set" : "set")
				: " · read-only") + " · " + ConfigManager.get().file().getFileName();
		return text(x + 4, y, w - 8, mode, Tone.MUTED) + 6;
	}

	private int notifications(int x, int y, int w) {
		Notifications n = draft.notifications();
		y = heading(x, y, w, "Notifications");
		y = notif(x, y, w, "All notifications", "enabled", n.enabled());
		y = notif(x, y, w, "Chat lines", "chat", n.chat());
		y = notif(x, y, w, "Toasts", "toast", n.toast());
		y = notif(x, y, w, "Connection toasts", "connectionToasts", n.connectionToasts());
		y += 8;
		y = heading(x, y, w, "Sounds");
		y = notif(x, y, w, "Sounds", "sound", n.sound());
		y = notif(x, y, w, "Needs you", "soundAttention", n.soundAttention());
		y = notif(x, y, w, "Done", "soundDone", n.soundDone());
		y = notif(x, y, w, "Error", "soundError", n.soundError());
		y += 8;
		y = heading(x, y, w, "While the game is in the background");
		y = toggle(x, y, w, "Window title", n.attention().windowTitle(), v -> setAttention(n().attention().withWindowTitle(v)));
		y = toggle(x, y, w, "Taskbar flash", n.attention().taskbarFlash(), v -> setAttention(n().attention().withTaskbarFlash(v)));
		y = text(x + 4, y + 2, w - 8, "Action bar", Tone.MUTED);
		T3Select<String> bar = new T3Select<>(T3CraftSettings.Attention.BAR_MODES, SettingsPanel::barLabel, n.attention().actionBar(),
				v -> setAttention(n().attention().withActionBar(v)));
		place(bar, x, y + 1, w, T3Control.H);
		return y + T3Control.H + 12;
	}

	private int connections(int x, int y, int w) {
		y = heading(x, y, w, "Connections");
		y = link(x, y, w, "Environments…", () -> host().show(new EnvironmentsPanel(actions)));
		y = link(x, y, w, "Piece browser…", () -> host().openScreen(new dev.t3craft.client.pieces.PieceBrowserScreen(host().screen())));
		y = link(x, y, w, "Key binds…",() -> host().openScreen(new KeyBindsScreen(host().screen(), Minecraft.getInstance().options)));
		y = link(x, y, w, "Outbound events…", () -> host().show(new OutboundPanel(new OutboundStore(ModConfig.dataDir()))));
		y = link(x, y, w, "Reload from disk", () -> {
			draft = ConfigManager.get().load();
			windowText = formatNumber(draft.recentWindowHours());
			maxText = Integer.toString(draft.maxPerEnvironment());
			if (windowField != null) {
				windowField.setValue(windowText);
			}
			if (maxField != null) {
				maxField.setValue(maxText);
			}
			actions.reload();
			say("Reloaded " + ConfigManager.get().file().getFileName(), Tone.OK);
			host().relayout();
		});
		y += 8;
		y = heading(x, y, w, "Testing");
		T3Button reset = new T3Button(T3Draw.tr("t3craft.demo.reset.button"), T3Button.Style.DANGER, this::resetHq);
		reset.setTooltip(T3Draw.tr("t3craft.demo.reset.button.tooltip"));
		place(reset, x, y, w, T3Control.H);
		return y + ROW + 6;
	}

	private int link(int x, int y, int w, String label, Runnable action) {
		place(new T3Button(label, action), x, y, w, T3Control.H);
		return y + ROW;
	}

	// ---- state ---------------------------------------------------------------------------------------------

	private T3Field windowField() {
		if (windowField == null) {
			windowField = new T3Field(font(), "Recent window (hours)").maxLength(8).onChange(v -> windowText = v);
			windowField.setValue(windowText);
		}
		return windowField;
	}

	private T3Field maxField() {
		if (maxField == null) {
			maxField = new T3Field(font(), "Max per environment").maxLength(4).onChange(v -> maxText = v);
			maxField.setValue(maxText);
		}
		return maxField;
	}

	private Notifications n() {
		return draft.notifications();
	}

	private int notif(int x, int y, int w, String label, String key, boolean value) {
		return toggle(x, y, w, label, value, v -> draft = draft.withNotifications(with(n(), key, v)));
	}

	/** {@code n} with one flag changed; the attention signals are kept as they were. */
	private static Notifications with(Notifications n, String key, boolean v) {
		return switch (key) {
			case "enabled" -> n.withEnabled(v);
			case "chat" -> new Notifications(n.enabled(), v, n.toast(), n.sound(), n.soundAttention(), n.soundDone(), n.soundError(),
					n.connectionToasts(), n.attention());
			case "toast" -> new Notifications(n.enabled(), n.chat(), v, n.sound(), n.soundAttention(), n.soundDone(), n.soundError(),
					n.connectionToasts(), n.attention());
			case "sound" -> new Notifications(n.enabled(), n.chat(), n.toast(), v, n.soundAttention(), n.soundDone(), n.soundError(),
					n.connectionToasts(), n.attention());
			case "soundAttention" -> new Notifications(n.enabled(), n.chat(), n.toast(), n.sound(), v, n.soundDone(), n.soundError(),
					n.connectionToasts(), n.attention());
			case "soundDone" -> new Notifications(n.enabled(), n.chat(), n.toast(), n.sound(), n.soundAttention(), v, n.soundError(),
					n.connectionToasts(), n.attention());
			case "soundError" -> new Notifications(n.enabled(), n.chat(), n.toast(), n.sound(), n.soundAttention(), n.soundDone(), v,
					n.connectionToasts(), n.attention());
			case "connectionToasts" -> new Notifications(n.enabled(), n.chat(), n.toast(), n.sound(), n.soundAttention(), n.soundDone(),
					n.soundError(), v, n.attention());
			default -> n;
		};
	}

	private void setAttention(T3CraftSettings.Attention a) {
		draft = draft.withNotifications(n().withAttention(a));
	}

	private static String barLabel(String mode) {
		return switch (mode) {
			case "also" -> "Also (toast and action bar)";
			case "instead" -> "Instead of the toast";
			default -> "Off";
		};
	}

	private String placementText() {
		return "Bar: " + draft.hud().position() + " " + draft.hud().align();
	}

	private void resetHq() {
		String refusal = DemoClient.resetRefusal();
		if (refusal != null) {
			say(refusal, Tone.ERROR);
			host().relayout();
			return;
		}
		confirm(T3Draw.tr("t3craft.demo.reset.title"), List.of(T3Draw.tr("t3craft.demo.reset.body")), null,
				T3Draw.tr("t3craft.demo.reset.confirm"), true, () -> {
					DemoClient.requestReset();
					Minecraft.getInstance().gui.setScreen(null);
				});
	}

	private void save() {
		Double hours = optionalNumber(windowText);
		Double max = optionalNumber(maxText);
		if (hours == null || !ConfigCodec.validWindow(hours)) {
			say("Recent window must be a number of hours, 0 to " + (long) T3CraftSettings.MAX_WINDOW_HOURS, Tone.ERROR);
			return;
		}
		if (max == null || !ConfigCodec.validMax(max)) {
			say("Max per environment must be a whole number, 1 to " + T3CraftSettings.MAX_PER_ENV_LIMIT, Tone.ERROR);
			return;
		}
		T3CraftSettings next = draft.withVisibility(hours, max.intValue(), draft.includeSubagents());
		ConfigManager.SaveResult r = ConfigManager.get().update(s -> next);
		draft = ConfigManager.get().settings();
		if (r.ok()) {
			actions.reload();
			say(r.message() + " and reloaded", Tone.OK);
		} else {
			say(r.message(), Tone.ERROR);
		}
		host().relayout();
	}

	/** Parses an optional number field: blank → null, invalid → NaN. */
	private static @Nullable Double optionalNumber(String raw) {
		String s = raw.trim();
		if (s.isEmpty()) {
			return null;
		}
		try {
			return Double.valueOf(s.replace(',', '.'));
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	private static String formatNumber(double v) {
		return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
	}
}
