package dev.t3craft.client.config;

import dev.t3craft.client.ModConfig;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.config.T3CraftSettings.Notifications;
import dev.t3craft.client.demo.DemoClient;
import dev.t3craft.client.t3ui.T3LayoutPrefs;
import dev.t3craft.client.t3ui.UiScale;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Control;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Field;
import dev.t3craft.client.t3ui.kit.T3Icon;
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
 * "Save" and "Done". It lives inside {@code T3Screen} (the settings icon of the sidebar and the ellipsis menu) and as the
 * screen {@link SettingsScreen} (Mod Menu, the J key, {@code /t3craft config}).
 *
 * <p>Same behaviour as the vanilla screen it replaces: edits go to a draft and are written on "Save"
 * (then the mod reloads its sources, like {@code /t3craft reload}); the bridge key is never shown, only
 * whether one is set; a config from a newer T3Craft is read-only.
 */
public final class SettingsPanel extends T3Panel {
	private static final int ROW = 18;
	private static final int MAX_W = 440;
	private static final List<String> TABS = List.of("General", "Notifications", "Connections", "World text");
	private static final int TAB_WORLD_TEXT = 3;

	private final EnvironmentActions actions;
	private T3CraftSettings draft;
	private int tab;
	private String windowText;
	private String maxText;
	private @Nullable T3Field windowField;
	private @Nullable T3Field maxField;
	/** World text tab: the draft, what the file held when it was last read or saved, and the three number fields. */
	private WorldTextSettings worldText;
	private WorldTextSettings worldTextSaved;
	private String nearText;
	private String visibleText;
	private String panelsText;
	private @Nullable T3Field nearField;
	private @Nullable T3Field visibleField;
	private @Nullable T3Field panelsField;
	private String status = "";
	private Tone statusTone = Tone.MUTED;
	/** #42: the T3 screens' scale ({@code layout.uiScale}), written on "Save" like the rest of the page. */
	private UiScale.Pref uiScaleDraft = UiScale.configured();
	/** The build order ({@code build.order}), written on "Save" when it changed (see {@link BuildOrderSettings}). */
	private String buildOrderDraft = BuildOrderSettings.current();

	public SettingsPanel(EnvironmentActions actions) {
		this.actions = actions;
		this.draft = ConfigManager.get().settings();
		this.windowText = formatNumber(draft.recentWindowHours());
		this.maxText = Integer.toString(draft.maxPerEnvironment());
		this.worldTextSaved = WorldTextSettings.current();
		loadWorldText(worldTextSaved);
		if (ConfigManager.get().readOnly()) {
			say("config/t3craft.json is from a newer T3Craft: changes will not be saved", Tone.ERROR);
		} else if (!ConfigManager.get().warnings().isEmpty()) {
			say(ConfigManager.get().warnings().size() + " config warning(s) fixed with defaults (see log)", Tone.WARN);
		}
	}

	/** Opens on tab {@code i} (0 General, 1 Notifications, 2 Connections, 3 World text): the gallery. */
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
			case TAB_WORLD_TEXT -> worldTextTab(x, y, w);
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
		y = uiScaleRow(x, y, w);
		y += 8;

		y = buildOrderRow(x, y, w);
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
		// a page stacked here: its own icon; another screen: external-link
		y = link(x, y, w, T3Icon.CLOUD, "Environments...", () -> host().show(new EnvironmentsPanel(actions)));
		y = link(x, y, w, T3Icon.EXTERNAL_LINK, "Piece browser...",
				() -> host().openScreen(new dev.t3craft.client.pieces.PieceBrowserScreen(host().screen())));
		y = link(x, y, w, T3Icon.EXTERNAL_LINK, "Key binds...", () -> host().openScreen(new KeyBindsScreen(host().screen(), Minecraft.getInstance().options)));
		y = link(x, y, w, T3Icon.SEND, "Outbound events...", () -> host().show(new OutboundPanel(new OutboundStore(ModConfig.dataDir()))));
		y = link(x, y, w, T3Icon.REFRESH_CW, "Reload from disk", () -> {
			draft = ConfigManager.get().load();
			uiScaleDraft = UiScale.configured(); // #42
			buildOrderDraft = BuildOrderSettings.current();
			windowText = formatNumber(draft.recentWindowHours());
			maxText = Integer.toString(draft.maxPerEnvironment());
			if (windowField != null) {
				windowField.setValue(windowText);
			}
			if (maxField != null) {
				maxField.setValue(maxText);
			}
			worldTextSaved = WorldTextSettings.current();
			loadWorldText(worldTextSaved);
			actions.reload();
			say("Reloaded " + ConfigManager.get().file().getFileName(), Tone.OK);
			host().relayout();
		});
		y += 8;
		y = heading(x, y, w, "Testing");
		T3Button reset = new T3Button(T3Icon.TRIANGLE_EXCLAMATION_POINT, T3Draw.tr("t3craft.demo.reset.button"), T3Button.Style.DANGER, this::resetHq);
		reset.setTooltip(T3Draw.tr("t3craft.demo.reset.button.tooltip"));
		place(reset, x, y, w, T3Control.H);
		return y + ROW + 6;
	}

	private int link(int x, int y, int w, T3Icon icon, String label, Runnable action) {
		place(new T3Button(icon, label, action), x, y, w, T3Control.H);
		return y + ROW;
	}

	/** The floating text of the HQ (docs/features/world-text.md): the {@code labels} block of the config file. */
	private int worldTextTab(int x, int y, int w) {
		T3Field near = nearField();
		T3Field visible = visibleField();
		T3Field panels = panelsField();
		y = heading(x, y, w, "Style");
		y = text(x + 4, y, w - 8, "Hologram: one pill per NPC, speech bubbles on events and the hub card. Classic: the vanilla name tag.", Tone.MUTED);
		T3Select<String> style = new T3Select<>(WorldTextSettings.STYLES, SettingsPanel::styleLabel, worldText.style(),
				v -> worldText = worldText.withStyle(v));
		place(style, x, y + 2, w, T3Control.H);
		y += T3Control.H + 10;

		y = heading(x, y, w, "NPC plates");
		y = text(x + 4, y, w - 8, "Show plates within (blocks, 0 = any distance)", Tone.MUTED);
		placeField(near, x, y + 1, w);
		y += T3Control.H + 7;
		y = text(x + 4, y, w - 8, "Plates shown at once (0 = no cap)", Tone.MUTED);
		placeField(visible, x, y + 1, w);
		y += T3Control.H + 7;
		y = text(x + 4, y, w - 8, "Detail panels open at once (0 = no cap)", Tone.MUTED);
		placeField(panels, x, y + 1, w);
		y += T3Control.H + 5;
		y = toggle(x, y, w, "Hide overlapping plates", worldText.declutter(), v -> worldText = worldText.withDeclutter(v));
		y += 8;

		y = heading(x, y, w, "Show world text for");
		y = toggle(x, y, w, "NPC tags and panels", worldText.npcTags(), v -> worldText = worldText.withNpcTags(v));
		y = toggle(x, y, w, "Desk screens", worldText.workstationScreens(), v -> worldText = worldText.withWorkstationScreens(v));
		y = toggle(x, y, w, "Hub card and podium bubbles", worldText.hubHologram(), v -> worldText = worldText.withHubHologram(v));
		y = toggle(x, y, w, "Schedule board", worldText.scheduleBoard(), v -> worldText = worldText.withScheduleBoard(v));
		y += 8;

		place(new T3Button(T3Icon.REFRESH_CW, "Reset to defaults", this::resetWorldText), x, y, w, T3Control.H);
		y += ROW;
		return text(x + 4, y + 2, w - 8, "Save writes the labels block of " + ConfigManager.get().file().getFileName()
				+ "; the game picks it up within a couple of seconds.", Tone.MUTED) + 6;
	}

	private static String styleLabel(String style) {
		return WorldTextSettings.CLASSIC.equals(style) ? "Classic" : "Hologram";
	}

	/** Puts {@code s} in the draft and in the three number fields. */
	private void loadWorldText(WorldTextSettings s) {
		worldText = s;
		nearText = formatNumber(s.nearDistance());
		visibleText = Integer.toString(s.maxVisible());
		panelsText = Integer.toString(s.maxPanels());
		if (nearField != null) {
			nearField.setValue(nearText);
		}
		if (visibleField != null) {
			visibleField.setValue(visibleText);
		}
		if (panelsField != null) {
			panelsField.setValue(panelsText);
		}
	}

	private void resetWorldText() {
		loadWorldText(WorldTextSettings.DEFAULTS);
		say("World text is back to its defaults: Save to keep them", Tone.MUTED);
		host().relayout();
	}

	private T3Field nearField() {
		if (nearField == null) {
			nearField = new T3Field(font(), "Show plates within (blocks)").maxLength(8).onChange(v -> nearText = v);
			nearField.setValue(nearText);
		}
		return nearField;
	}

	private T3Field visibleField() {
		if (visibleField == null) {
			visibleField = new T3Field(font(), "Plates shown at once").maxLength(4).onChange(v -> visibleText = v);
			visibleField.setValue(visibleText);
		}
		return visibleField;
	}

	private T3Field panelsField() {
		if (panelsField == null) {
			panelsField = new T3Field(font(), "Detail panels open at once").maxLength(4).onChange(v -> panelsText = v);
			panelsField.setValue(panelsText);
		}
		return panelsField;
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

	/** #42: "UI scale" select (Minecraft, Auto, 1..4, or the number the config already holds). */
	private int uiScaleRow(int x, int y, int w) {
		y = text(x + 4, y + 2, w - 8, "UI scale (T3 screens)", Tone.MUTED);
		List<UiScale.Pref> options = new java.util.ArrayList<>(List.of(UiScale.Pref.MINECRAFT, UiScale.Pref.AUTO,
				UiScale.Pref.fixed(1), UiScale.Pref.fixed(2), UiScale.Pref.fixed(3), UiScale.Pref.fixed(4)));
		if (!options.contains(uiScaleDraft)) {
			options.add(uiScaleDraft);
		}
		T3Select<UiScale.Pref> select = new T3Select<>(options, SettingsPanel::uiScaleLabel, uiScaleDraft, v -> uiScaleDraft = v);
		place(select, x, y + 1, w, T3Control.H);
		return y + T3Control.H + 5;
	}

	private static String uiScaleLabel(UiScale.Pref p) {
		return switch (p.mode()) {
			case MINECRAFT -> "Minecraft (the game's GUI scale)";
			case AUTO -> "Auto (denser on large monitors)";
			case FIXED -> "Scale " + p.fixed();
		};
	}

	/** "Build order" select (Sites, Radial, Parts) with a line saying when it applies. */
	private int buildOrderRow(int x, int y, int w) {
		y = heading(x, y, w, T3Draw.tr("t3craft.settings.buildorder.heading"));
		y = text(x + 4, y, w - 8, T3Draw.tr("t3craft.settings.buildorder.label"), Tone.MUTED);
		T3Select<String> select = new T3Select<>(BuildOrderSettings.ORDERS, SettingsPanel::buildOrderLabel, buildOrderDraft,
				v -> buildOrderDraft = v);
		place(select, x, y + 1, w, T3Control.H);
		y += T3Control.H + 4;
		return text(x + 4, y, w - 8, T3Draw.tr("t3craft.settings.buildorder.help"), Tone.MUTED) + 2;
	}

	private static String buildOrderLabel(String order) {
		return T3Draw.tr("t3craft.settings.buildorder." + order);
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
		WorldTextSettings world;
		try {
			world = worldText.withNumbers(nearText, visibleText, panelsText);
		} catch (IllegalArgumentException e) {
			say(e.getMessage(), Tone.ERROR);
			return;
		}
		T3CraftSettings next = draft.withVisibility(hours, max.intValue(), draft.includeSubagents());
		ConfigManager.SaveResult r = ConfigManager.get().update(s -> next);
		draft = ConfigManager.get().settings();
		if (r.ok()) {
			// #42: the UI scale lives in the layout prefs, written whatever the labels block does
			boolean scaleOk = uiScaleDraft.equals(UiScale.configured()) || T3LayoutPrefs.saveUiScale(uiScaleDraft);
			// the build order lives in the build block: only written (and the tuning reloaded) when it changed
			boolean orderOk = BuildOrderSettings.save(buildOrderDraft).ok();
			// the labels block is only touched when this page changed something in it
			ConfigManager.SaveResult w = world.equals(worldTextSaved) ? r : world.save();
			if (w.ok()) {
				worldText = world;
				worldTextSaved = world;
				actions.reload();
				if (!orderOk) {
					say(T3Draw.tr("t3craft.settings.buildorder.failed"), Tone.WARN);
				} else if (scaleOk) {
					say(r.message() + " and reloaded", Tone.OK);
				} else {
					say("Settings saved, but the UI scale could not be written", Tone.WARN);
				}
			} else {
				say(w.message(), Tone.ERROR);
			}
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
