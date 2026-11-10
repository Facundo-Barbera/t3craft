package dev.t3craft.client.t3ui.actions;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.FavoritesStore;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.chat.compose.ComposerSkin;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Control;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Field;
import dev.t3craft.client.t3ui.kit.T3Form;
import dev.t3craft.client.t3ui.kit.T3List;
import dev.t3craft.client.t3ui.kit.T3Select;
import dev.t3craft.client.t3ui.kit.T3Toggle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The composer's model / provider / reasoning picker (T3 Code {@code ComposerModelEffort} +
 * {@code ProviderModelPicker} + {@code TraitsPicker} + the runtime toggle of {@code ComposerControlsBar}), as one
 * self-contained widget for the composer's control bar or any panel.
 *
 * <p>Closed it is the composer's chip {@code Provider Model Reasoning} ({@link ComposerSkin}); a click opens a
 * popover (upwards when the chip is in the lower half of the screen, aligned to the chip's right edge) made of
 * kit controls on the {@link ChatTheme} tokens:
 * <ul>
 *   <li>a search box, then the providers (a scrolling list) beside the provider's models: favorites first
 *   (the square on the left of a row, kept in {@code config/t3craft/model-favorites.json} per
 *   {@code environment/instance/model}), then T3's order, legacy models behind "Show legacy (N)" (a search looks
 *   through them too);</li>
 *   <li>under them the model's controls: a {@link T3Select} for its reasoning descriptor, one for every other
 *   select descriptor and a {@link T3Toggle} for every boolean one (fast, thinking...), all sent as
 *   {@code modelSelection.options};</li>
 *   <li>Access: a {@link T3Select} with every runtime mode the provider supports; the selected mode's
 *   description is its tooltip.</li>
 * </ul>
 * <ul>
 *   <li>{@link #forThread}: changes are staged; "Apply" sends only the changed commands
 *   ({@link ThreadCommands#configure}) through {@code OperateGate}; {@link Listener#onPicked} after
 *   T3 accepted them.</li>
 *   <li>{@link #forNewThread}: every change is the default for new threads of that environment
 *   ({@link ProviderService#setDefaultPick}) and goes to {@link Listener#onPicked} at once.</li>
 * </ul>
 * Without control (operate) the chip is drawn disabled as "Enable control" and a click opens the
 * enable/pair screen. Host contract: {@link #setBounds}, {@link #render} last (the popover draws over other
 * widgets), {@link #mouseClicked} first (it consumes clicks while open), {@link #keyPressed} and
 * {@link #charTyped} (the search box; Esc closes), {@link #mouseScrolled}. Client thread only.
 */
public final class ModelPicker {
	@FunctionalInterface
	public interface Listener {
		void onPicked(ModelPick pick);
	}

	private enum Mode {
		THREAD,
		NEW_THREAD
	}

	/** A row of the models list: a model, or the "Show legacy" switch at the end. */
	private record ModelRow(ProviderCatalog.@Nullable Model model, int legacyCount, boolean legacyShown) {
		boolean isSwitch() {
			return model == null;
		}
	}

	/** A choice of a drop-down; {@code id} null is "Default" (nothing is sent). */
	private record Opt(@Nullable String id, String label) {
	}

	private static final FavoritesStore FAVORITES = new FavoritesStore("model-favorites.json", "model picker");

	private static final int ROW = 14;
	private static final int PAD = 6;
	private static final int GAP = 4;
	private static final int LIST_ROWS = 9;
	private static final int MIN_LIST_ROWS = 4;
	private static final int POPUP_W = 330;
	private static final int STAR = 6;
	private static final int STAR_COL = 13;

	private final Font font;
	private final Mode mode;
	private final Listener listener;
	private final ProviderService providers;
	private final ActionGate gate;
	private @Nullable String envId;
	private final @Nullable String threadId;
	private final @Nullable String threadTitle;
	private ModelPickerLogic logic;
	private @Nullable ProviderCatalog seen;
	private int x;
	private int y;
	private int w;
	private int h = ComposerSkin.CHIP_H;
	private boolean open;
	private boolean busy;
	private String status = "";
	private T3Draw.Tone statusTone = T3Draw.Tone.MUTED;
	private final Hits hits = new Hits();

	// ---- the popover's controls ----------------------------------------------------------------------
	private final T3Form form = new T3Form();
	private final T3Field search;
	private final T3List<ProviderCatalog.Provider> providerList;
	private final T3List<ModelRow> modelList;
	private final T3Button cancelButton;
	private final T3Button applyButton;
	/** Label and control of the model's settings under the lists, rebuilt when the model changes. */
	private final List<Setting> settings = new ArrayList<>();
	private @Nullable String builtFor;
	private boolean showLegacy;
	private boolean focusSearch;
	private boolean reveal;
	private @Nullable Rect searchRect;
	private @Nullable ChatTheme searchTheme;
	private Rect popup = Rect.EMPTY;

	private record Setting(@Nullable String label, T3Control control) {
	}

	private ModelPicker(Font font, Mode mode, @Nullable String envId, @Nullable String threadId, @Nullable String threadTitle,
			@Nullable ModelPick initial, Listener listener, ProviderService providers, ActionGate gate) {
		this.font = font;
		this.mode = mode;
		this.envId = envId;
		this.threadId = threadId;
		this.threadTitle = threadTitle;
		this.listener = listener;
		this.providers = providers;
		this.gate = gate;
		this.logic = new ModelPickerLogic(envId == null ? ProviderCatalog.EMPTY : providers.catalog(envId).catalog(), initial);
		this.providerList = new T3List<>(ROW, new ProviderRow());
		this.modelList = new T3List<>(ROW, new ModelsRow());
		this.search = new T3Field(font, "Search models").hint("Search models...").maxLength(64).onChange(v -> modelList.scrollToTop())
				.onSubmit(this::pickFirst);
		this.cancelButton = new T3Button("Cancel", () -> close());
		this.applyButton = new T3Button("Apply", T3Button.Style.PRIMARY, this::apply);
	}

	/**
	 * Picker for an existing thread. {@code current} is what the thread uses now
	 * ({@link ModelPick#fromThreadJson} when the host has the thread object, else
	 * {@link ModelPick#fromMeta}); null shows the first provider until the catalog arrives.
	 */
	public static ModelPicker forThread(Font font, String envId, String threadId, @Nullable String title, @Nullable ModelPick current,
			Listener listener) {
		return new ModelPicker(font, Mode.THREAD, envId, threadId, title, current, listener, ActionsClient.providers(), ActionsClient.gate());
	}

	/** Picker for the new-thread defaults of {@code envId} (null until the host knows the environment). */
	public static ModelPicker forNewThread(Font font, @Nullable String envId, Listener listener) {
		ProviderService ps = ActionsClient.providers();
		return new ModelPicker(font, Mode.NEW_THREAD, envId, null, null, envId == null ? null : ps.defaultPick(envId), listener, ps,
				ActionsClient.gate());
	}

	/**
	 * A new-thread picker over a fixed {@code server.getConfig} answer and an always-enabled gate, for the UI gallery
	 * ({@code /t3dev ui model-picker}): no connection, no write.
	 */
	public static ModelPicker forGallery(Font font, String envId, com.google.gson.JsonObject serverConfig, Listener listener) {
		ProviderService ps = new ProviderService(env -> java.util.concurrent.CompletableFuture.completedFuture(serverConfig),
				System::currentTimeMillis);
		ActionGate always = new ActionGate(env -> dev.t3craft.t3.OperateGate.Availability.ENABLED);
		return new ModelPicker(font, Mode.NEW_THREAD, envId, null, null, null, listener, ps, always);
	}

	// ---- host API -------------------------------------------------------------------------------

	/** Marks this picker as the composer's chip (the only look it has); kept for the hosts that already call it. */
	public ModelPicker composerChip() {
		h = ComposerSkin.CHIP_H;
		return this;
	}

	/** Width of the composer chip for the current pick. */
	public int chipWidth() {
		String[] p = chipParts();
		return ComposerSkin.chipWidth(font, p[0], p[1], withArrow(p[2]));
	}

	private String withArrow(String detail) {
		String arrow = open ? "▴" : "▾";
		return detail.isEmpty() ? arrow : detail + " " + arrow;
	}

	/** {icon, model, detail} of the composer chip. */
	private String[] chipParts() {
		if (gate.state(envId) != ActionGate.State.ENABLED) {
			String key = ActionGate.disabledKey(gate.state(envId));
			return new String[] {"⚠", Component.translatable(key == null ? "t3craft.actions.enable_control" : key).getString(), ""};
		}
		ModelPick p = logic.pick();
		if (p == null) {
			ProviderService.Entry entry = envId == null ? null : providers.catalog(envId);
			return new String[] {"", entry != null && entry.phase() == ProviderService.Phase.LOADING ? "Loading models..." : "No models", ""};
		}
		ProviderCatalog cat = logic.catalog();
		ProviderCatalog.Provider prov = cat == null ? null : cat.provider(p.instanceId());
		ProviderCatalog.Model m = prov == null ? null : prov.model(p.model());
		String model = m == null ? p.model() : m.label();
		String provLabel = prov == null ? "" : prov.label();
		if (!provLabel.isEmpty() && model.regionMatches(true, 0, provLabel + " ", 0, provLabel.length() + 1)) {
			model = model.substring(provLabel.length() + 1); // "Claude Opus 5.5" → "Opus 5.5", as T3's trigger
		}
		StringBuilder detail = new StringBuilder();
		String reasoning = p.reasoningLabel(m);
		if (reasoning != null) {
			detail.append(reasoning);
		}
		if (p.interaction() == Modes.Interaction.PLAN) {
			detail.append(detail.isEmpty() ? "" : " · ").append("Plan");
		}
		return new String[] {providerGlyph(prov == null ? p.instanceId() : prov.driver()), model, detail.toString()};
	}

	/** A glyph standing for the provider's icon (the game font has no logos). */
	static String providerGlyph(String driver) {
		String d = driver.toLowerCase(java.util.Locale.ROOT);
		if (d.contains("claude") || d.contains("anthropic")) {
			return "✳";
		}
		if (d.contains("codex") || d.contains("openai") || d.contains("gpt")) {
			return "◎";
		}
		if (d.contains("gemini") || d.contains("google")) {
			return "✦";
		}
		if (d.contains("cursor")) {
			return "▲";
		}
		return d.isEmpty() ? "•" : d.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
	}

	static int providerColor(String glyph) {
		return switch (glyph) {
			case "✳" -> 0xFFD97757;
			case "✦" -> 0xFF7AA7FF;
			case "⚠" -> ComposerSkin.WARN;
			default -> ComposerSkin.TEXT;
		};
	}

	/** The pick's runtime mode (access), null until there is a pick. */
	public Modes.@Nullable Runtime runtime() {
		ModelPick p = logic.pick();
		return p == null ? null : p.runtime();
	}

	/** Opens or closes the popover (the access chip opens the same one). */
	public void toggle() {
		if (busy || gate.state(envId) != ActionGate.State.ENABLED) {
			return;
		}
		if (open) {
			close();
			return;
		}
		open = true;
		if (envId != null) {
			providers.invalidate(envId);
		}
		status = "";
		search.setValue("");
		showLegacy = false;
		reveal = true;
		focusSearch = true;
		builtFor = null;
	}

	public void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	/** New-thread mode: the dialog switched environment; reloads that environment's catalog and default. */
	public void setEnv(@Nullable String env) {
		if (java.util.Objects.equals(env, envId)) {
			return;
		}
		envId = env;
		seen = null;
		dismiss();
		logic = new ModelPickerLogic(env == null ? ProviderCatalog.EMPTY : providers.catalog(env).catalog(), env == null ? null : providers.defaultPick(env));
		if (mode == Mode.NEW_THREAD && logic.pick() != null) {
			listener.onPicked(logic.pick());
		}
	}

	public @Nullable ModelPick pick() {
		return logic.pick();
	}

	public ModelPickerLogic logic() {
		return logic;
	}

	public boolean isOpen() {
		return open;
	}

	/** Closes the popover; a thread's staged changes are dropped. */
	public void close() {
		if (open && mode == Mode.THREAD) {
			logic.reset();
		}
		dismiss();
	}

	private void dismiss() {
		open = false;
		search.setBoxFocused(false);
		form.closeOverlays();
	}

	public boolean enabled() {
		return gate.enabled(envId);
	}

	// ---- input ----------------------------------------------------------------------------------

	public boolean mouseClicked(double mx, double my, int button) {
		if (open && (form.overlayOpen() || popup.contains(mx, my))) {
			form.mouseClicked(mx, my, button); // a click on the popover is always the popover's
			return true;
		}
		if (!MouseButtons.isLeft(button)) {
			return false;
		}
		if (hits.click(mx, my)) {
			return true;
		}
		if (open) {
			close(); // a click outside closes it and is consumed so the host does not act on it
			return true;
		}
		return false;
	}

	public boolean keyPressed(KeyEvent e) {
		if (!open) {
			return false;
		}
		if (form.overlayOpen()) {
			return form.keyPressed(e); // the open drop-down owns the keyboard (Esc closes only it)
		}
		if (e.key() == InputConstants.KEY_ESCAPE) {
			close();
			return true;
		}
		return form.keyPressed(e) || search.boxKeyPressed(e);
	}

	/** A typed character: it goes to the search box. */
	public boolean charTyped(int codepoint) {
		if (!open) {
			return false;
		}
		return form.overlayOpen() || search.boxCharTyped(codepoint);
	}

	public boolean mouseScrolled(double mx, double my, double dy) {
		if (!open || !(form.overlayOpen() || popup.contains(mx, my))) {
			return false;
		}
		form.mouseScrolled(mx, my, dy);
		return true;
	}

	// ---- drawing --------------------------------------------------------------------------------

	public void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		hits.clear();
		refreshCatalog();
		ActionGate.State st = gate.state(envId);
		boolean hover = Hits.inside(mouseX, mouseY, x, y, x + w, y + h);
		String[] p = chipParts();
		ComposerSkin.chip(g, font, x, y, p[0], providerColor(p[0]), p[1], st == ActionGate.State.ENABLED ? ComposerSkin.TEXT : ComposerSkin.WARN,
				withArrow(p[2]), hover, open);
		if (st != ActionGate.State.ENABLED) {
			if (st != ActionGate.State.NO_ENV) {
				String env = envId;
				hits.add(x, y, x + w, y + h, () -> ActionsClient.enableControl(Minecraft.getInstance().gui.screen(), env));
			}
			if (open) {
				dismiss();
			}
			return;
		}
		if (hover && !open) {
			g.setTooltipForNextFrame(font, Component.literal(status.isEmpty() ? "Model · reasoning · access" : status), mouseX, mouseY);
		}
		if (!busy) {
			hits.add(x, y, x + w, y + h, this::toggle);
		}
		if (open) {
			renderPopup(g, mouseX, mouseY);
		}
	}

	private void refreshCatalog() {
		if (envId == null) {
			return;
		}
		ProviderCatalog c = providers.catalog(envId).catalog();
		if (c != seen) {
			seen = c;
			boolean hadPick = logic.pick() != null;
			logic.setCatalog(c);
			builtFor = null;
			if (mode == Mode.NEW_THREAD && !hadPick && logic.pick() != null) {
				providers.setDefaultPick(envId, logic.pick());
				listener.onPicked(logic.pick());
			}
		}
	}

	private void renderPopup(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ChatTheme t = ChatTheme.warmStudio();
		rebuildIfNeeded();
		int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		Rect screen = new Rect(0, 0, screenW, screenH);

		// size: the lists give way when the settings under them do not fit
		int settingsH = settings.size() * (T3Control.H + 3);
		int footerH = mode == Mode.THREAD ? T3Control.H : 0;
		int fixed = PAD + T3Control.H + GAP + GAP + 1 + GAP + settingsH + footerH + PAD;
		int listH = Math.max(MIN_LIST_ROWS * ROW + 4, Math.min(LIST_ROWS * ROW + 4, screenH - 8 - fixed));
		int pw = Math.min(screenW - 4, Math.max(POPUP_W, w));
		int ph = fixed + listH;
		boolean up = y > screenH / 2;
		int popX0 = Math.max(2, Math.min(x + w - pw, screenW - pw - 2));
		int popY0 = up ? Math.max(2, y - ph - 1) : y + h + 1;
		popup = new Rect(popX0, popY0, pw, ph);
		g.nextStratum(); // over the text drawn before (the transcript)
		ChatDraw.panel(g, t, popup);

		// places
		int ix = popX0 + PAD;
		int iw = pw - 2 * PAD;
		int cy = popY0 + PAD;
		Rect searchBounds = new Rect(ix, cy, iw, T3Control.H);
		if (!searchBounds.equals(searchRect) || t != searchTheme) {
			search.layout(t, searchBounds, true);
			searchRect = searchBounds;
			searchTheme = t;
		}
		if (focusSearch) {
			search.setBoxFocused(true);
			focusSearch = false;
		}
		cy += T3Control.H + GAP;
		int provW = 0;
		for (ProviderCatalog.Provider pr : logic.providers()) {
			provW = Math.max(provW, font.width(pr.label()) + 16);
		}
		provW = Math.min(Math.max(70, provW), iw / 3);
		providerList.setBounds(new Rect(ix, cy, provW, listH));
		modelList.setBounds(new Rect(ix + provW + GAP, cy, iw - provW - GAP, listH));
		cy += listH + GAP;
		g.fill(ix, cy, ix + iw, cy + 1, t.edge());
		cy += 1 + GAP;
		int labelW = 0;
		for (Setting s : settings) {
			if (s.label() != null) {
				labelW = Math.max(labelW, font.width(s.label()) + 8);
			}
		}
		labelW = Math.min(labelW, iw / 3);
		for (Setting s : settings) {
			if (s.label() == null) {
				s.control().setBounds(new Rect(ix, cy, iw, T3Control.H));
			} else {
				s.control().setBounds(new Rect(ix + labelW, cy, iw - labelW, T3Control.H));
			}
			cy += T3Control.H + 3;
		}
		if (mode == Mode.THREAD) {
			int bw = 58;
			applyButton.setBounds(new Rect(ix + iw - bw, cy, bw, T3Control.H));
			cancelButton.setBounds(new Rect(ix + iw - 2 * bw - GAP, cy, bw, T3Control.H));
			applyButton.setLabel(busy ? "Applying..." : "Apply");
			applyButton.setEnabled(!busy && !logic.changed().isEmpty());
		}

		// models and providers follow the search and the legacy switch
		ModelPickerLogic.ModelView view = logic.modelView(search.value(), showLegacy, this::favorite);
		List<ModelRow> rows = new ArrayList<>();
		view.models().forEach(m -> rows.add(new ModelRow(m, 0, false)));
		if (view.legacyCount() > 0) {
			rows.add(new ModelRow(null, view.legacyCount(), showLegacy));
		}
		providerList.setItems(logic.providers());
		providerList.setEmptyText(emptyProviders());
		modelList.setItems(rows);
		modelList.setEmptyText(search.value().isBlank() ? "No models." : "No model matches the search.");
		if (reveal) {
			reveal = false;
			ModelPick cur = logic.pick();
			List<ProviderCatalog.Provider> ps = logic.providers();
			for (int i = 0; i < ps.size(); i++) {
				if (cur != null && ps.get(i).instanceId().equals(cur.instanceId())) {
					providerList.ensureVisible(i);
				}
			}
			for (int i = 0; i < rows.size(); i++) {
				if (cur != null && rows.get(i).model() != null && rows.get(i).model().slug().equals(cur.model())) {
					modelList.ensureVisible(i);
				}
			}
		}

		// draw
		form.render(g, font, t, mouseX, mouseY);
		search.renderBox(g, mouseX, mouseY);
		int ly = popup.y() + PAD + T3Control.H + GAP + listH + GAP + 1 + GAP;
		for (Setting s : settings) {
			if (s.label() != null) {
				T3Draw.text(g, font, t, s.label(), ix + 2, ly + (T3Control.H - 8) / 2, t.muted());
			}
			ly += T3Control.H + 3;
		}
		if (mode == Mode.THREAD && !status.isEmpty()) {
			int sw = cancelButton.bounds().x() - ix - GAP;
			T3Draw.text(g, font, t, ChatDraw.clip(font, status, sw), ix + 2, ly + (T3Control.H - 8) / 2, T3Draw.color(t, statusTone));
		}
		form.renderOverlays(g, font, t, mouseX, mouseY, screen);
		List<String> tip = form.overlayOpen() ? null : form.tooltip(mouseX, mouseY);
		if (tip != null) {
			List<String> lines = new ArrayList<>();
			tip.forEach(l -> lines.addAll(T3Draw.wrap(font, l, 220)));
			T3Draw.tooltip(g, font, lines, mouseX, mouseY);
		}
	}

	private String emptyProviders() {
		ProviderService.Entry entry = envId == null ? null : providers.catalog(envId);
		if (entry != null && entry.phase() == ProviderService.Phase.LOADING) {
			return "Loading models...";
		}
		return entry != null && entry.error() != null ? "No models (" + entry.error() + ")" : "T3 exposes no models for this environment.";
	}

	/**
	 * (Re)creates the model's settings and the form's control list when the provider, model or catalog changed:
	 * Reasoning, the other selects, the switches, Access.
	 */
	private void rebuildIfNeeded() {
		if (form.overlayOpen()) {
			return; // a catalog refresh must not close the drop-down the player is using
		}
		ModelPick pick = logic.pick();
		ProviderCatalog.Model m = logic.model();
		String key = pick == null ? "-" : pick.instanceId() + "/" + pick.model() + "/" + logic.runtimeModes();
		if (key.equals(builtFor)) {
			return;
		}
		builtFor = key;
		settings.clear();
		if (m != null && pick != null) {
			ProviderCatalog.Option reasoning = m.reasoning();
			if (reasoning != null) {
				settings.add(new Setting(reasoning.label(), optionSelect(reasoning, pick)));
			}
			for (ProviderCatalog.Option o : m.extraSelects()) {
				settings.add(new Setting(o.label(), optionSelect(o, pick)));
			}
			for (ProviderCatalog.Option o : m.toggles()) {
				T3Toggle tg = new T3Toggle(o.label(), logic.toggleValue(o), v -> {
					if (logic.setToggle(o.id(), v)) {
						changed();
					}
				});
				if (o.description() != null) {
					tg.setTooltip(o.description());
				}
				settings.add(new Setting(null, tg));
			}
		}
		if (pick != null) {
			settings.add(new Setting("Access", accessSelect(pick)));
		}
		form.clear();
		form.add(search);
		form.add(providerList);
		form.add(modelList);
		settings.forEach(s -> form.add(s.control()));
		if (mode == Mode.THREAD) {
			form.add(cancelButton);
			form.add(applyButton);
		}
	}

	private T3Select<Opt> optionSelect(ProviderCatalog.Option o, ModelPick pick) {
		List<Opt> opts = new ArrayList<>();
		if (o.defaultValue() == null) {
			opts.add(new Opt(null, "Default"));
		}
		o.choices().forEach(c -> opts.add(new Opt(c.id(), c.label())));
		String chosen = pick.optionString(o.id());
		String shown = chosen != null ? chosen : o.defaultValue();
		Opt current = opts.stream().filter(op -> java.util.Objects.equals(op.id(), shown)).findFirst().orElse(null);
		T3Select<Opt> sel = new T3Select<>(opts, Opt::label, current, op -> {
			if (logic.selectOption(o.id(), op.id())) {
				changed();
			}
		});
		if (o.description() != null) {
			sel.setTooltip(o.description());
		}
		return sel;
	}

	/** Every runtime mode the provider supports; the selected mode's description is the tooltip. */
	private T3Select<Modes.Runtime> accessSelect(ModelPick pick) {
		List<Modes.Runtime> modes = logic.runtimeModes();
		Modes.Runtime shown = modes.contains(pick.runtime()) ? pick.runtime() : modes.getFirst();
		T3Select<Modes.Runtime> sel = new T3Select<>(modes, r -> r.label, shown, r -> {
			if (logic.selectRuntime(r)) {
				changed(); // rebuilds the select, whose tooltip is then the new mode's
			}
		});
		sel.setTooltip(shown.label + ": " + shown.description);
		return sel;
	}

	private boolean favorite(ProviderCatalog.Model m) {
		return FAVORITES.isFavorite(favoriteKey(m.slug()));
	}

	/** {@code envId/instanceId/slug}: the same slug in two environments or two instances is two favorites. */
	private String favoriteKey(String slug) {
		ProviderCatalog.Provider p = logic.provider();
		return (envId == null ? "-" : envId) + "/" + (p == null ? "-" : p.instanceId()) + "/" + slug;
	}

	/** Enter in the search box: the first model of the list. */
	private void pickFirst() {
		for (ModelRow r : modelList.items()) {
			if (r.model() != null) {
				if (logic.selectModel(r.model().slug())) {
					changed();
				}
				return;
			}
		}
	}

	private void changed() {
		builtFor = null;
		ModelPick p = logic.pick();
		if (mode == Mode.NEW_THREAD && p != null && envId != null) {
			providers.setDefaultPick(envId, p);
			logic.commit();
			listener.onPicked(p);
		}
	}

	private void apply() {
		if (envId == null || threadId == null || busy) {
			return;
		}
		busy = true;
		status = "Applying...";
		statusTone = T3Draw.Tone.WARN;
		Screen returnTo = Minecraft.getInstance().gui.screen();
		ActionsClient.configureThread(envId, threadId, threadTitle, logic, returnTo, r -> {
			busy = false;
			if (r.ok()) {
				dismiss();
				status = "Model updated";
				statusTone = T3Draw.Tone.OK;
				if (logic.pick() != null) {
					listener.onPicked(logic.pick());
				}
			} else {
				status = dev.t3craft.t3.ThreadControl.feedback(r).text();
				statusTone = T3Draw.Tone.ERROR;
			}
		});
	}

	// ---- rows -----------------------------------------------------------------------------------

	private final class ProviderRow implements T3List.Row<ProviderCatalog.Provider> {
		@Override
		public void render(GuiGraphicsExtractor g, Font f, ChatTheme t, ProviderCatalog.Provider p, Rect r, int mx, int my) {
			ModelPick cur = logic.pick();
			boolean sel = cur != null && cur.instanceId().equals(p.instanceId());
			if (sel) {
				g.fill(r.x(), r.y(), r.right(), r.bottom(), t.selected());
			}
			g.text(f, ChatDraw.clip(f, p.label(), r.w() - 8), r.x() + 4, r.y() + (r.h() - 8) / 2, sel ? t.accent() : t.text(), t.shadow());
		}

		@Override
		public boolean mouseClicked(ProviderCatalog.Provider p, Rect r, double mx, double my, int button) {
			if (MouseButtons.isLeft(button) && logic.selectProvider(p.instanceId())) {
				modelList.scrollToTop();
				changed();
			}
			return true;
		}
	}

	private final class ModelsRow implements T3List.Row<ModelRow> {
		@Override
		public void render(GuiGraphicsExtractor g, Font f, ChatTheme t, ModelRow row, Rect r, int mx, int my) {
			int ty = r.y() + (r.h() - 8) / 2;
			if (row.isSwitch()) {
				boolean hov = r.contains(mx, my);
				String label = (row.legacyShown() ? "Hide legacy (" : "Show legacy (") + row.legacyCount() + ")";
				g.text(f, label, r.x() + STAR_COL + 2, ty, hov ? t.accent() : t.muted(), t.shadow());
				return;
			}
			ProviderCatalog.Model m = row.model();
			ModelPick cur = logic.pick();
			boolean sel = cur != null && cur.model().equals(m.slug());
			if (sel) {
				g.fill(r.x(), r.y(), r.right(), r.bottom(), t.selected());
			}
			int sx = r.x() + 4;
			int sy = r.y() + (r.h() - STAR) / 2;
			if (favorite(m)) {
				g.fill(sx, sy, sx + STAR, sy + STAR, t.brass());
			} else {
				boolean overStar = mx >= r.x() && mx < r.x() + STAR_COL && r.contains(mx, my);
				ChatDraw.outline(g, sx, sy, STAR, STAR, overStar ? t.brass() : t.disabled());
			}
			int right = r.right() - 4;
			String badge = m.badge() != null ? m.badge() : m.isCustom() ? "custom" : null;
			if (badge != null) {
				int bw = f.width(badge);
				g.text(f, badge, right - bw, ty, t.brass(), t.shadow());
				right -= bw + 6;
			}
			int tx = r.x() + STAR_COL + 2;
			String name = ChatDraw.clip(f, m.label(), right - tx);
			g.text(f, name, tx, ty, sel ? t.accent() : m.isLegacy() ? t.muted() : t.text(), t.shadow());
			if (m.subProvider() != null) {
				int sx2 = tx + f.width(name) + 6;
				if (right - sx2 > 12) {
					g.text(f, ChatDraw.clip(f, m.subProvider(), right - sx2), sx2, ty, t.ghost(), t.shadow());
				}
			}
		}

		@Override
		public boolean mouseClicked(ModelRow row, Rect r, double mx, double my, int button) {
			if (!MouseButtons.isLeft(button)) {
				return true;
			}
			if (row.isSwitch()) {
				showLegacy = !showLegacy;
			} else if (mx < r.x() + STAR_COL) {
				FAVORITES.toggle(favoriteKey(row.model().slug())); // the list re-sorts on the next frame
			} else if (logic.selectModel(row.model().slug())) {
				changed();
			}
			return true;
		}

		@Override
		public @Nullable List<String> tooltip(ModelRow row, Rect r, double mx, double my) {
			if (row.isSwitch()) {
				return null;
			}
			ProviderCatalog.Model m = row.model();
			if (mx < r.x() + STAR_COL) {
				return List.of(favorite(m) ? "Remove from favorites" : "Add to favorites");
			}
			List<String> lines = new ArrayList<>();
			lines.add(m.name());
			lines.add(m.slug());
			if (m.isLegacy()) {
				lines.add("Legacy model");
			}
			return lines;
		}
	}
}
