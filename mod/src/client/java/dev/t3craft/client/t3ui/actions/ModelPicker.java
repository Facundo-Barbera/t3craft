package dev.t3craft.client.t3ui.actions;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.chat.compose.ComposerSkin;
import dev.t3craft.client.config.FavoritesStore;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Control;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Field;
import dev.t3craft.client.t3ui.kit.T3Form;
import dev.t3craft.client.t3ui.kit.T3List;
import dev.t3craft.client.t3ui.kit.T3Select;
import dev.t3craft.client.t3ui.kit.T3Toggle;
import dev.t3craft.t3.OperateGate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The composer's model / provider / options picker (T3 Code {@code ComposerModelEffort} +
 * {@code ProviderModelPicker} + {@code TraitsPicker} + the runtime toggle of {@code ComposerControlsBar}),
 * as one self-contained widget for the composer's control bar.
 *
 * <p>Closed it is the composer's ghost chip ({@link ComposerSkin#chip}): provider icon, model and
 * reasoning ("Opus 5.5 - Medium", plus "- Plan" in plan mode), sized to its text ({@link #chipWidth()}).
 * A click opens a popover of the chip, upwards when the chip is in the lower half of the screen and aligned to
 * its right edge. The popover is built from the UI kit and the {@link ChatTheme} tokens:
 * <ul>
 *   <li>a search box (name, short name, slug, aliases, sub-provider, badge);</li>
 *   <li>two scrolling lists: the providers, and the chosen provider's models (favorites first with a star,
 *   legacy models behind a "Show legacy (N)" row);</li>
 *   <li>the model's controls: a {@link T3Select} for the reasoning descriptor (role "reasoning", else the
 *   first select), one select per extra select descriptor and a {@link T3Toggle} per boolean descriptor;
 *   their values travel in {@code modelSelection.options} ({@link ProviderOptionSelection});</li>
 *   <li>Access: a {@link T3Select} with every runtime mode the provider supports, the description of the
 *   mode in its tooltip ({@link Modes.Runtime#description}).</li>
 * </ul>
 * Plan mode has no row any more (it stays in the pick and shows in the chip).
 *
 * <ul>
 *   <li>{@link #forThread}: changes are staged; "Apply" sends only the changed commands
 *   ({@link ThreadCommands#configure}) through {@code OperateGate}; {@link Listener#onPicked} after
 *   T3 accepted them.</li>
 *   <li>{@link #forNewThread}: every change is the default for new threads of that environment
 *   ({@link ProviderService#setDefaultPick}) and goes to {@link Listener#onPicked} at once.</li>
 * </ul>
 * Without control (operate) the chip is drawn as "Enable control" and a click opens the enable/pair screen.
 * Host contract: {@link #setBounds}, {@link #render} last (the popup draws over other widgets),
 * {@link #mouseClicked} first (it consumes clicks while open), {@link #keyPressed} and {@link #charTyped}
 * (Esc closes; the search box types), {@link #mouseScrolled}. Favorites are kept in
 * {@code config/t3craft/model-favorites.json} under {@code envId/instanceId/slug}. Client thread only.
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

	/** One choice of a select: {@code id} null is the "Default" entry of a descriptor without a default choice. */
	private record Option(@Nullable String id, String label) {
	}

	/** A row of the model list: a model, or (model null) the "Show legacy (N)" row. */
	private record Item(ProviderCatalog.@Nullable Model model, int legacy, boolean legacyShown) {
	}

	/** A line of the settings under the lists: a label and its control; {@code label} null = the control spans the row. */
	private record Setting(@Nullable String label, T3Control control) {
	}

	private static final int ROW = 14;
	private static final int PAD = 6;
	private static final int MAX_LIST_ROWS = 9;
	private static final int MIN_LIST_ROWS = 3;
	private static final int STAR = 7;

	private static @Nullable FavoritesStore sharedFavorites;

	private final Font font;
	private final Mode mode;
	private final Listener listener;
	private final ProviderService providers;
	private final ActionGate gate;
	private final FavoritesStore favorites;
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
	private Component status = Component.empty();
	private T3Draw.Tone statusTone = T3Draw.Tone.MUTED;
	private final Hits hits = new Hits();

	// ---- popup controls ---------------------------------------------------------------------------
	private final T3Form form = new T3Form();
	private final T3Field search;
	private @Nullable EditBox searchBox;
	private Rect searchRect = Rect.EMPTY;
	private boolean focusSearch;
	private boolean scrollPending;
	private boolean showLegacy;
	private final T3List<ProviderCatalog.Provider> providerList;
	private final T3List<Item> modelList;
	private final List<Setting> settings = new ArrayList<>();
	private @Nullable T3Button applyButton;
	private @Nullable T3Button cancelButton;
	private boolean controlsDirty = true;
	private @Nullable ModelPick builtPick;
	private @Nullable ProviderCatalog builtCatalog;
	private boolean builtBusy;
	private boolean builtDirty;
	private Rect popup = Rect.EMPTY;

	private ModelPicker(Font font, Mode mode, @Nullable String envId, @Nullable String threadId, @Nullable String threadTitle,
			@Nullable ModelPick initial, Listener listener, ProviderService providers, ActionGate gate, FavoritesStore favorites) {
		this.font = font;
		this.mode = mode;
		this.envId = envId;
		this.threadId = threadId;
		this.threadTitle = threadTitle;
		this.listener = listener;
		this.providers = providers;
		this.gate = gate;
		this.favorites = favorites;
		this.logic = new ModelPickerLogic(envId == null ? ProviderCatalog.EMPTY : providers.catalog(envId).catalog(), initial);
		this.search = new T3Field(font, "Search models").hint("Search models...").maxLength(64).onChange(v -> modelList().scrollToTop())
				.onSubmit(this::pickFirstMatch);
		this.providerList = new T3List<>(ROW, new ProviderRow());
		this.providerList.setEmptyText("T3 exposes no models for this environment.");
		this.modelList = new T3List<>(ROW, new ModelRow());
	}

	private static synchronized FavoritesStore favorites() {
		if (sharedFavorites == null) {
			sharedFavorites = new FavoritesStore("model-favorites.json");
		}
		return sharedFavorites;
	}

	/**
	 * Picker for an existing thread. {@code current} is what the thread uses now
	 * ({@link ModelPick#fromThreadJson} when the host has the thread object, else
	 * {@link ModelPick#fromMeta}); null shows the first provider until the catalog arrives.
	 */
	public static ModelPicker forThread(Font font, String envId, String threadId, @Nullable String title, @Nullable ModelPick current,
			Listener listener) {
		return new ModelPicker(font, Mode.THREAD, envId, threadId, title, current, listener, ActionsClient.providers(), ActionsClient.gate(),
				favorites());
	}

	/** Picker for the new-thread defaults of {@code envId} (null until the host knows the environment). */
	public static ModelPicker forNewThread(Font font, @Nullable String envId, Listener listener) {
		ProviderService ps = ActionsClient.providers();
		return new ModelPicker(font, Mode.NEW_THREAD, envId, null, null, envId == null ? null : ps.defaultPick(envId), listener, ps,
				ActionsClient.gate(), favorites());
	}

	/**
	 * A picker over a canned {@code server.getConfig} object, with control always enabled and favorites kept
	 * in memory: the UI gallery's scenarios ({@code /t3dev ui model-picker}). {@code thread} true shows the
	 * Apply / Cancel footer of a thread picker (Apply is aimed at the fake environment and fails with the
	 * usual toast).
	 */
	public static ModelPicker forGallery(Font font, String envId, JsonObject serverConfig, boolean thread, Listener listener) {
		ProviderService ps = new ProviderService(env -> CompletableFuture.completedFuture(serverConfig), System::currentTimeMillis);
		ActionGate open = new ActionGate(env -> OperateGate.Availability.ENABLED);
		return new ModelPicker(font, thread ? Mode.THREAD : Mode.NEW_THREAD, envId, thread ? "gallery-thread" : null, thread ? "Gallery thread" : null,
				null, listener, ps, open, FavoritesStore.inMemory());
	}

	// ---- host API -------------------------------------------------------------------------------

	/** Width of the composer chip for the current pick. */
	public int chipWidth() {
		String[] p = chipParts();
		return ComposerSkin.chipWidth(font, p[0], p[1], withArrow(p[2]));
	}

	private String withArrow(String detail) {
		String arrow = open ? "\u25B4" : "\u25BE";
		return detail.isEmpty() ? arrow : detail + " " + arrow;
	}

	/** {icon, model, detail} of the composer chip. */
	private String[] chipParts() {
		if (gate.state(envId) != ActionGate.State.ENABLED) {
			String key = ActionGate.disabledKey(gate.state(envId));
			return new String[] {"\u26A0", Component.translatable(key == null ? "t3craft.actions.enable_control" : key).getString(), ""};
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
			model = model.substring(provLabel.length() + 1); // "Claude Opus 5.5" becomes "Opus 5.5", as T3's trigger
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
			return "\u2733";
		}
		if (d.contains("codex") || d.contains("openai") || d.contains("gpt")) {
			return "\u25CE";
		}
		if (d.contains("gemini") || d.contains("google")) {
			return "\u2726";
		}
		if (d.contains("cursor")) {
			return "\u25B2";
		}
		return d.isEmpty() ? "\u2022" : d.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
	}

	static int providerColor(String glyph) {
		return switch (glyph) {
			case "\u2733" -> 0xFFD97757;
			case "\u2726" -> 0xFF7AA7FF;
			case "\u26A0" -> ComposerSkin.WARN;
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
			dismiss();
			return;
		}
		open = true;
		focusSearch = true;
		scrollPending = true;
		if (envId != null) {
			providers.invalidate(envId);
		}
		status = Component.empty();
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
		closePopup();
		logic = new ModelPickerLogic(env == null ? ProviderCatalog.EMPTY : providers.catalog(env).catalog(), env == null ? null : providers.defaultPick(env));
		controlsDirty = true;
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

	public void close() {
		closePopup();
	}

	public boolean enabled() {
		return gate.enabled(envId);
	}

	/** Closes the popover for good: the search box lets go of the keyboard and is emptied. */
	private void closePopup() {
		if (searchBox != null && searchBox.isFocused()) {
			searchBox.setFocused(false);
		}
		if (!search.value().isEmpty()) {
			search.setValue("");
		}
		form.closeOverlays();
		open = false;
		showLegacy = false;
		focusSearch = false;
	}

	/** Closes without applying; a thread picker goes back to what the thread uses. */
	private void dismiss() {
		closePopup();
		if (mode == Mode.THREAD) {
			logic.reset();
			controlsDirty = true;
		}
	}

	// ---- input ----------------------------------------------------------------------------------

	public boolean mouseClicked(double mx, double my, int button) {
		if (open && form.overlayOpen()) {
			form.mouseClicked(mx, my, button); // an open select owns the click, even outside the popup
			return true;
		}
		if (!MouseButtons.isLeft(button)) {
			return open && popup.contains(mx, my);
		}
		if (open && popup.contains(mx, my)) {
			clickPopup(mx, my, button);
			return true;
		}
		if (hits.click(mx, my)) {
			return true;
		}
		if (open) {
			// Click outside the popup closes it (and is consumed so the host does not act on it).
			dismiss();
			return true;
		}
		return false;
	}

	private void clickPopup(double mx, double my, int button) {
		EditBox box = searchBox;
		if (box != null && search.bounds().contains(mx, my)) {
			box.setFocused(true);
			box.mouseClicked(new MouseButtonEvent(mx, my, new MouseButtonInfo(button, 0)), false);
			return;
		}
		form.mouseClicked(mx, my, button);
	}

	public boolean keyPressed(KeyEvent e) {
		if (!open) {
			return false;
		}
		if (form.overlayOpen()) {
			form.keyPressed(e); // a select's list owns the keyboard (Esc only closes the list)
			return true;
		}
		if (e.key() == InputConstants.KEY_ESCAPE) {
			dismiss();
			return true;
		}
		if (form.keyPressed(e)) { // Enter in the search box
			return true;
		}
		EditBox box = searchBox;
		return box != null && box.isFocused() && box.keyPressed(e);
	}

	/** A typed character while open: it goes to the search box. */
	public boolean charTyped(int codepoint) {
		if (!open) {
			return false;
		}
		EditBox box = searchBox;
		if (form.overlayOpen() || box == null || !box.isFocused()) {
			return open;
		}
		box.charTyped(new CharacterEvent(codepoint));
		return true;
	}

	public boolean mouseScrolled(double mx, double my, double dy) {
		if (!open) {
			return false;
		}
		if (form.overlayOpen()) {
			form.mouseScrolled(mx, my, dy);
			return true;
		}
		if (!popup.contains(mx, my)) {
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
				closePopup();
			}
			return;
		}
		if (hover && !open) {
			String tip = status.getString().isEmpty() ? "Model, reasoning, access" : status.getString();
			g.setTooltipForNextFrame(font, Component.literal(tip), mouseX, mouseY);
		}
		if (!busy) {
			hits.add(x, y, x + w, y + h, this::toggle);
		}
		if (open) {
			renderPopup(g, mouseX, mouseY);
		} else {
			popup = Rect.EMPTY;
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
			if (mode == Mode.NEW_THREAD && !hadPick && logic.pick() != null) {
				providers.setDefaultPick(envId, logic.pick());
				listener.onPicked(logic.pick());
			}
		}
	}

	private T3List<Item> modelList() {
		return modelList;
	}

	private String favoriteKey(ProviderCatalog.Model m) {
		ProviderCatalog.Provider prov = logic.provider();
		return (envId == null ? "" : envId) + "/" + (prov == null ? "" : prov.instanceId()) + "/" + m.slug();
	}

	private boolean isFavorite(ProviderCatalog.Model m) {
		return favorites.isFavorite(favoriteKey(m));
	}

	private ModelPickerLogic.Listing listing() {
		return logic.listing(search.value(), this::isFavorite, showLegacy);
	}

	/** Enter in the search box: choose the first model of the list. */
	private void pickFirstMatch() {
		List<ProviderCatalog.Model> ms = listing().models();
		if (!ms.isEmpty() && logic.selectModel(ms.getFirst().slug())) {
			changed();
		}
	}

	private void renderPopup(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ChatTheme t = ChatTheme.warmStudio();
		syncControls();
		int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		Rect screen = new Rect(0, 0, screenW, screenH);

		// Widths: providers on the left, models on the right.
		List<ProviderCatalog.Provider> ps = logic.providers();
		int leftW = 60;
		for (ProviderCatalog.Provider p : ps) {
			leftW = Math.max(leftW, font.width(p.label()) + 22);
		}
		leftW = Math.min(110, leftW);
		int rightW = 150;
		for (ProviderCatalog.Model m : logic.models()) {
			rightW = Math.max(rightW, font.width(m.label()) + 30 + (m.badge() == null ? 0 : font.width(m.badge()) + 14));
		}
		rightW = Math.min(230, rightW);
		int pw = Math.min(screenW - 4, Math.max(w, leftW + 4 + rightW + 2 * PAD));
		int contentW = pw - 2 * PAD;
		rightW = contentW - leftW - 4;

		// Heights: the lists take what the settings leave, between MIN and MAX rows.
		int settingRows = settings.size();
		int footer = mode == Mode.THREAD ? 2 + T3Control.H : 0;
		int base = PAD + T3Control.H + 4 + 4 + 6 + settingRows * (T3Control.H + 2) + footer + PAD;
		boolean up = y > screenH / 2;
		int avail = up ? y - 3 : screenH - (y + h + 1) - 3;
		int listRows = Math.max(MIN_LIST_ROWS, Math.min(MAX_LIST_ROWS, (avail - base) / ROW));
		int listH = listRows * ROW + 4;
		int ph = base - 4 + listH;

		int anchorX = x + w - pw;
		int px = Math.max(2, Math.min(anchorX, screenW - pw - 2));
		int py = up ? Math.max(2, y - ph - 1) : y + h + 1;
		popup = new Rect(px, py, pw, ph);

		g.nextStratum(); // over the text drawn before (the transcript)
		ChatDraw.panel(g, t, popup);

		// Search box and lists.
		int cx = px + PAD;
		int cy = py + PAD;
		Rect sr = new Rect(cx, cy, contentW, T3Control.H);
		search.setBounds(sr);
		if (!sr.equals(searchRect) || searchBox == null) {
			searchRect = sr;
			searchBox = search.layout(t, sr, true);
		}
		int ly = cy + T3Control.H + 4;
		providerList.setBounds(new Rect(cx, ly, leftW, listH));
		modelList.setBounds(new Rect(cx + leftW + 4, ly, rightW, listH));
		providerList.setItems(ps);
		modelList.setItems(modelItems());
		modelList.setEmptyText(search.value().isBlank() ? "No models." : "No model matches \"" + search.value().trim() + "\".");
		if (scrollPending) {
			scrollPending = false;
			scrollToSelection(ps);
		}
		if (focusSearch && searchBox != null) {
			focusSearch = false;
			searchBox.setFocused(true);
		}

		// Settings: a label on the left, the control on the right.
		int labelW = 40;
		for (Setting s : settings) {
			String label = s.label();
			if (label != null) {
				labelW = Math.max(labelW, font.width(label) + 8);
			}
		}
		labelW = Math.min(labelW, contentW / 3);
		int sy = ly + listH + 6;
		for (Setting s : settings) {
			String label = s.label();
			if (label == null) {
				s.control().setBounds(new Rect(cx, sy, contentW, T3Control.H));
			} else {
				T3Draw.text(g, font, t, ChatDraw.clip(font, label, labelW - 4), cx + 4, sy + (T3Control.H - 8) / 2, t.muted());
				s.control().setBounds(new Rect(cx + labelW, sy, contentW - labelW, T3Control.H));
			}
			sy += T3Control.H + 2;
		}
		T3Button apply = applyButton;
		T3Button cancel = cancelButton;
		if (mode == Mode.THREAD && apply != null && cancel != null) {
			int fy = sy + 2;
			int aw = Math.max(48, font.width(apply.label()) + 16);
			int cw = Math.max(48, font.width(cancel.label()) + 16);
			apply.setBounds(new Rect(cx + contentW - aw, fy, aw, T3Control.H));
			cancel.setBounds(new Rect(cx + contentW - aw - 4 - cw, fy, cw, T3Control.H));
			String msg = status.getString();
			if (!msg.isEmpty()) {
				T3Draw.text(g, font, t, ChatDraw.clip(font, msg, contentW - aw - cw - 12), cx + 4, fy + (T3Control.H - 8) / 2, T3Draw.color(t, statusTone));
			}
		}

		form.render(g, font, t, mouseX, mouseY);
		EditBox box = searchBox;
		if (box != null) {
			box.extractRenderState(g, mouseX, mouseY, 0f); // the text, over the field's frame
		}
		form.renderOverlays(g, font, t, mouseX, mouseY, screen);
		if (!form.overlayOpen()) {
			List<String> tip = form.tooltip(mouseX, mouseY);
			if (tip != null) {
				T3Draw.tooltip(g, font, tip, mouseX, mouseY);
			}
		}
	}

	/** The model list's rows: the listing, then the "Show legacy (N)" row when there is legacy to hide. */
	private List<Item> modelItems() {
		ModelPickerLogic.Listing l = listing();
		List<Item> out = new ArrayList<>(l.models().size() + 1);
		for (ProviderCatalog.Model m : l.models()) {
			out.add(new Item(m, 0, false));
		}
		if (l.legacy() > 0) {
			out.add(new Item(null, l.legacy(), l.legacyShown()));
		}
		return out;
	}

	/** Brings the chosen provider and model into view (the popover just opened). */
	private void scrollToSelection(List<ProviderCatalog.Provider> ps) {
		ModelPick cur = logic.pick();
		if (cur == null) {
			return;
		}
		for (int i = 0; i < ps.size(); i++) {
			if (ps.get(i).instanceId().equals(cur.instanceId())) {
				providerList.ensureVisible(i);
			}
		}
		List<Item> items = modelList.items();
		for (int i = 0; i < items.size(); i++) {
			ProviderCatalog.Model m = items.get(i).model();
			if (m != null && m.slug().equals(cur.model())) {
				modelList.ensureVisible(i);
			}
		}
	}

	// ---- controls -------------------------------------------------------------------------------

	/** Rebuilds the selects, switches and buttons when the pick, the catalog or the busy / dirty state changed. */
	private void syncControls() {
		ModelPick pick = logic.pick();
		boolean dirty = !logic.changed().isEmpty();
		if (!controlsDirty && Objects.equals(pick, builtPick) && logic.catalog() == builtCatalog && busy == builtBusy && dirty == builtDirty) {
			return;
		}
		controlsDirty = false;
		builtPick = pick;
		builtCatalog = logic.catalog();
		builtBusy = busy;
		builtDirty = dirty;

		settings.clear();
		ProviderCatalog.Model m = logic.model();
		if (pick != null && m != null) {
			ProviderCatalog.Descriptor reasoning = m.reasoning();
			if (reasoning != null) {
				settings.add(new Setting("Reasoning", select(reasoning, pick)));
			}
			for (ProviderCatalog.Descriptor d : m.extraSelects()) {
				settings.add(new Setting(d.label(), select(d, pick)));
			}
			for (ProviderCatalog.Descriptor d : m.flags()) {
				settings.add(new Setting(null, toggle(d, pick)));
			}
		}
		settings.add(new Setting("Access", accessSelect(pick)));

		form.clear();
		form.add(search);
		form.add(providerList);
		form.add(modelList);
		settings.forEach(s -> form.add(s.control()));
		applyButton = null;
		cancelButton = null;
		if (mode == Mode.THREAD) {
			T3Button cancel = form.add(new T3Button("Cancel", this::dismiss));
			T3Button apply = form.add(new T3Button(busy ? "Applying..." : "Apply", T3Button.Style.PRIMARY, this::apply));
			apply.setEnabled(dirty && !busy);
			cancelButton = cancel;
			applyButton = apply;
		}
	}

	private T3Select<Option> select(ProviderCatalog.Descriptor d, ModelPick pick) {
		List<Option> opts = new ArrayList<>();
		if (d.defaultValue() == null) {
			opts.add(new Option(null, "Default"));
		}
		for (ProviderCatalog.Choice c : d.choices()) {
			opts.add(new Option(c.id(), c.label()));
		}
		String current = pick.value(d.id());
		if (current == null) {
			current = d.defaultValue();
		}
		String shown = current;
		Option cur = opts.stream().filter(o -> Objects.equals(o.id(), shown)).findFirst().orElse(null);
		Consumer<Option> onPick = o -> {
			if (logic.selectOption(d.id(), o.id())) {
				changed();
			}
			controlsDirty = true;
		};
		T3Select<Option> sel = new T3Select<>(opts, Option::label, cur, onPick);
		if (d.description() != null) {
			sel.setTooltip(d.description());
		}
		return sel;
	}

	private T3Toggle toggle(ProviderCatalog.Descriptor d, ModelPick pick) {
		Boolean set = pick.flag(d.id());
		T3Toggle tg = new T3Toggle(d.label(), set != null ? set : d.flagDefault(), v -> {
			if (logic.setFlag(d.id(), v)) {
				changed();
			}
			controlsDirty = true;
		});
		if (d.description() != null) {
			tg.setTooltip(d.description());
		}
		return tg;
	}

	private T3Select<Modes.Runtime> accessSelect(@Nullable ModelPick pick) {
		Modes.Runtime current = pick == null ? Modes.Runtime.DEFAULT : pick.runtime();
		T3Select<Modes.Runtime> sel = new T3Select<>(logic.runtimeModes(), r -> r.label, current, r -> {
			if (logic.selectRuntime(r)) {
				changed();
			}
			controlsDirty = true;
		});
		sel.setTooltip(current.description);
		return sel;
	}

	// ---- list rows ------------------------------------------------------------------------------

	private final class ProviderRow implements T3List.Row<ProviderCatalog.Provider> {
		@Override
		public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, ProviderCatalog.Provider p, Rect r, int mx, int my) {
			ModelPick cur = logic.pick();
			boolean sel = cur != null && cur.instanceId().equals(p.instanceId());
			if (sel) {
				ChatDraw.round(g, r.x() + 1, r.y(), r.w() - 2, r.h(), t.selected());
			}
			T3Draw.text(g, font, t, ChatDraw.clip(font, p.label(), r.w() - 10), r.x() + 5, r.y() + (r.h() - 8) / 2, sel ? t.accent() : t.text());
		}

		@Override
		public boolean mouseClicked(ProviderCatalog.Provider p, Rect r, double mx, double my, int button) {
			if (MouseButtons.isLeft(button) && logic.selectProvider(p.instanceId())) {
				modelList.scrollToTop();
				changed();
				return true;
			}
			return false;
		}
	}

	private final class ModelRow implements T3List.Row<Item> {
		private Rect star(Rect r) {
			return new Rect(r.right() - STAR - 5, r.y() + (r.h() - STAR) / 2, STAR, STAR);
		}

		@Override
		public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, Item item, Rect r, int mx, int my) {
			int ty = r.y() + (r.h() - 8) / 2;
			ProviderCatalog.Model m = item.model();
			if (m == null) {
				boolean hover = r.contains(mx, my);
				String label = (item.legacyShown() ? "Hide legacy (" : "Show legacy (") + item.legacy() + ")";
				T3Draw.text(g, font, t, label, r.x() + 5, ty, hover ? t.accent() : t.muted());
				return;
			}
			ModelPick cur = logic.pick();
			boolean sel = cur != null && cur.model().equals(m.slug());
			if (sel) {
				ChatDraw.round(g, r.x() + 1, r.y(), r.w() - 2, r.h(), t.selected());
			}
			Rect s = star(r);
			if (isFavorite(m)) { // a brass square: the bitmap font has no star (until the kit has icons)
				g.fill(s.x(), s.y(), s.right(), s.bottom(), t.brass());
			} else if (r.contains(mx, my)) {
				ChatDraw.outline(g, s.x(), s.y(), s.w(), s.h(), t.disabled());
			}
			String tag = m.badge() != null ? m.badge() : m.isCustom() ? "custom" : null;
			int tagW = tag == null ? 0 : font.width(tag) + 10 + 4;
			int tx = r.x() + 5;
			int limit = s.x() - 4;
			String name = ChatDraw.clip(font, m.label(), limit - tx - tagW);
			T3Draw.text(g, font, t, name, tx, ty, sel ? t.accent() : t.text());
			int end = tx + font.width(name);
			if (tag != null && end + 4 + tagW - 4 <= limit) {
				ChatDraw.pill(g, font, t, tag, end + 4, r.y() + (r.h() - 11) / 2, t.keycapText());
				end += tagW;
			}
			String sub = m.subProvider();
			if (sub != null && end + 8 + font.width(sub) <= limit) {
				T3Draw.text(g, font, t, sub, limit - font.width(sub), ty, t.muted());
			}
		}

		@Override
		public boolean mouseClicked(Item item, Rect r, double mx, double my, int button) {
			if (!MouseButtons.isLeft(button)) {
				return false;
			}
			ProviderCatalog.Model m = item.model();
			if (m == null) {
				showLegacy = !showLegacy;
				return true;
			}
			Rect s = star(r);
			if (mx >= s.x() - 3) {
				favorites.toggle(favoriteKey(m));
				return true;
			}
			if (logic.selectModel(m.slug())) {
				changed();
			}
			return true;
		}

		@Override
		public @Nullable List<String> tooltip(Item item, Rect r, double mx, double my) {
			ProviderCatalog.Model m = item.model();
			if (m == null) {
				return null;
			}
			if (mx >= star(r).x() - 3) {
				return List.of(isFavorite(m) ? "Remove from favorites" : "Add to favorites");
			}
			return m.name().equals(m.label()) ? List.of(m.slug()) : List.of(m.name(), m.slug());
		}
	}

	// ---- picking --------------------------------------------------------------------------------

	private void changed() {
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
		status = Component.literal("Applying...");
		statusTone = T3Draw.Tone.WARN;
		Screen returnTo = Minecraft.getInstance().gui.screen();
		ActionsClient.configureThread(envId, threadId, threadTitle, logic, returnTo, r -> {
			busy = false;
			controlsDirty = true;
			if (r.ok()) {
				closePopup();
				status = Component.literal("Model updated");
				statusTone = T3Draw.Tone.OK;
				if (logic.pick() != null) {
					listener.onPicked(logic.pick());
				}
			} else {
				status = Component.literal(dev.t3craft.t3.ThreadControl.feedback(r).text());
				statusTone = T3Draw.Tone.ERROR;
			}
		});
	}
}
