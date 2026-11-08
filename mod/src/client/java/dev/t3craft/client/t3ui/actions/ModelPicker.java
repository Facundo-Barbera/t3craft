package dev.t3craft.client.t3ui.actions;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.chat.MouseButtons;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The composer's model / provider / effort picker (T3 Code {@code ComposerModelEffort} +
 * {@code ProviderModelPicker} + {@code TraitsPicker} + the runtime / plan toggles of
 * {@code ComposerControlsBar}), as one self-contained widget for the composer's control bar
 * (feat/chat-compose) or any panel.
 *
 * <p>Closed it is a chip {@code Provider · Model · Effort ▾}; a click opens a popup (upwards when
 * the chip is in the lower half of the screen) with the providers on the left, the provider's
 * models on the right, and rows for effort, access (runtime mode) and plan mode.
 * <ul>
 *   <li>{@link #forThread}: changes are staged; "Apply" sends only the changed commands
 *   ({@link ThreadCommands#configure}) through {@code OperateGate}; {@link Listener#onPicked} after
 *   T3 accepted them.</li>
 *   <li>{@link #forNewThread}: every change is the default for new threads of that environment
 *   ({@link ProviderService#setDefaultPick}) and goes to {@link Listener#onPicked} at once.</li>
 * </ul>
 * Without control (operate) the chip is drawn disabled as "Enable control" and a click opens the
 * enable/pair screen. Host contract: {@link #setBounds}, {@link #render} last (the popup draws over
 * other widgets), {@link #mouseClicked} first (it consumes clicks while open), {@link #keyPressed}
 * (Esc closes), {@link #mouseScrolled}. Client thread only.
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

	private static final int ROW = 12;
	private static final int BG = 0xF0101418;
	private static final int CHIP = 0xFF1E252B;
	private static final int CHIP_HOVER = 0xFF27414A;
	private static final int BORDER = 0xFF3A6B73;
	private static final int TEXT = 0xFFE6E6E6;
	private static final int DIM = 0xFF8A949E;
	private static final int ACCENT = 0xFF7DD3FC;
	private static final int SELECTED = 0xFF27414A;
	private static final int WARN = 0xFFFFAA00;
	private static final int ERROR = 0xFFFF5555;
	private static final int OK = 0xFF6EE7B7;
	private static final int MAX_ROWS = 10;

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
	private int h = 14;
	private boolean open;
	private boolean busy;
	private int modelScroll;
	private Component status = Component.empty();
	private int statusColor = DIM;
	private final Hits hits = new Hits();

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

	// ---- host API -------------------------------------------------------------------------------

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
		open = false;
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

	public void close() {
		open = false;
	}

	public boolean enabled() {
		return gate.enabled(envId);
	}

	// ---- input ----------------------------------------------------------------------------------

	public boolean mouseClicked(double mx, double my, int button) {
		if (!MouseButtons.isLeft(button)) {
			return open && popupContains(mx, my);
		}
		if (hits.click(mx, my)) {
			return true;
		}
		if (open) {
			// Click outside the popup closes it (and is consumed so the host does not act on it).
			open = false;
			if (mode == Mode.THREAD) {
				logic.reset();
			}
			return true;
		}
		return false;
	}

	public boolean keyPressed(KeyEvent e) {
		if (open && e.key() == InputConstants.KEY_ESCAPE) {
			open = false;
			if (mode == Mode.THREAD) {
				logic.reset();
			}
			return true;
		}
		return false;
	}

	public boolean mouseScrolled(double mx, double my, double dy) {
		if (!open || !popupContains(mx, my)) {
			return false;
		}
		int n = logic.models().size();
		modelScroll = Math.max(0, Math.min(Math.max(0, n - MAX_ROWS), modelScroll - (int) Math.signum(dy)));
		return true;
	}

	// ---- drawing --------------------------------------------------------------------------------

	private int popX0;
	private int popY0;
	private int popX1;
	private int popY1;

	private boolean popupContains(double mx, double my) {
		return Hits.inside(mx, my, popX0, popY0, popX1, popY1);
	}

	public void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		hits.clear();
		refreshCatalog();
		ActionGate.State st = gate.state(envId);
		boolean hover = Hits.inside(mouseX, mouseY, x, y, x + w, y + h);
		g.fill(x, y, x + w, y + h, hover ? CHIP_HOVER : CHIP);
		int ty = y + (h - font.lineHeight) / 2 + 1;
		if (st != ActionGate.State.ENABLED) {
			String key = ActionGate.disabledKey(st);
			String label = "⚠ " + Component.translatable(key == null ? "t3craft.actions.enable_control" : key).getString();
			g.text(font, font.plainSubstrByWidth(label, w - 6), x + 3, ty, WARN);
			if (st != ActionGate.State.NO_ENV) {
				String env = envId;
				hits.add(x, y, x + w, y + h, () -> ActionsClient.enableControl(Minecraft.getInstance().gui.screen(), env));
			}
			open = false;
			return;
		}
		ModelPick p = logic.pick();
		ProviderService.Entry entry = envId == null ? null : providers.catalog(envId);
		String label;
		int color = TEXT;
		if (p != null) {
			label = p.label(logic.catalog()) + (p.interaction() == Modes.Interaction.PLAN ? " · Plan" : "");
		} else if (entry != null && entry.phase() == ProviderService.Phase.LOADING) {
			label = "Loading models…";
			color = DIM;
		} else {
			label = entry != null && entry.error() != null ? "No models (" + entry.error() + ")" : "No models";
			color = DIM;
		}
		String arrow = open ? " ▴" : " ▾";
		g.text(font, font.plainSubstrByWidth(label, w - 6 - font.width(arrow)) + arrow, x + 3, ty, color);
		if (!status.getString().isEmpty() && !open) {
			g.text(font, font.plainSubstrByWidth(status.getString(), Math.max(40, w)), x, y - font.lineHeight - 1, statusColor);
		}
		if (!busy) {
			hits.add(x, y, x + w, y + h, () -> {
				open = !open;
				if (open && envId != null) {
					providers.invalidate(envId);
					status = Component.empty();
				}
			});
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
			if (mode == Mode.NEW_THREAD && !hadPick && logic.pick() != null) {
				providers.setDefaultPick(envId, logic.pick());
				listener.onPicked(logic.pick());
			}
		}
	}

	private void renderPopup(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		List<ProviderCatalog.Provider> ps = logic.providers();
		List<ProviderCatalog.Model> ms = logic.models();
		int leftW = 0;
		for (ProviderCatalog.Provider p : ps) {
			leftW = Math.max(leftW, font.width(p.label()) + 10);
		}
		leftW = Math.min(110, Math.max(60, leftW));
		int rightW = 120;
		for (ProviderCatalog.Model m : ms) {
			rightW = Math.max(rightW, font.width(m.label()) + 16);
		}
		rightW = Math.min(180, rightW);
		int pw = Math.max(w, leftW + rightW + 6);
		int listRows = Math.max(1, Math.max(Math.min(MAX_ROWS, ps.size()), Math.min(MAX_ROWS, ms.size())));
		ProviderCatalog.Effort effort = logic.effort();
		int extraRows = (effort != null ? 1 : 0) + 1 + (logic.planAvailable() ? 1 : 0) + (mode == Mode.THREAD ? 1 : 0);
		int ph = 4 + listRows * ROW + 4 + extraRows * (ROW + 2) + 2;
		int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		boolean up = y > screenH / 2;
		popX0 = Math.max(2, Math.min(x, screenW - pw - 2));
		popX1 = popX0 + pw;
		popY0 = up ? Math.max(2, y - ph - 1) : y + h + 1;
		popY1 = popY0 + ph;
		g.fill(popX0 - 1, popY0 - 1, popX1 + 1, popY1 + 1, BORDER);
		g.fill(popX0, popY0, popX1, popY1, BG);
		// Swallow clicks on the popup background.
		hits.add(popX0, popY0, popX1, popY1, () -> {
		});
		int ly = popY0 + 4;
		if (ps.isEmpty()) {
			g.text(font, "T3 exposes no models for this environment.", popX0 + 4, ly, DIM);
		}
		ModelPick cur = logic.pick();
		for (int i = 0; i < ps.size() && i < MAX_ROWS; i++) {
			ProviderCatalog.Provider p = ps.get(i);
			int ry = ly + i * ROW;
			boolean sel = cur != null && cur.instanceId().equals(p.instanceId());
			if (sel) {
				g.fill(popX0 + 2, ry - 1, popX0 + leftW, ry + ROW - 1, SELECTED);
			}
			g.text(font, font.plainSubstrByWidth(p.label(), leftW - 6), popX0 + 4, ry + 1, sel ? ACCENT : TEXT);
			hits.add(popX0 + 2, ry - 1, popX0 + leftW, ry + ROW - 1, () -> {
				if (logic.selectProvider(p.instanceId())) {
					modelScroll = 0;
					changed();
				}
			});
		}
		int rx = popX0 + leftW + 4;
		g.fill(rx - 3, ly - 2, rx - 2, ly + listRows * ROW, BORDER);
		modelScroll = Math.max(0, Math.min(modelScroll, Math.max(0, ms.size() - MAX_ROWS)));
		for (int i = modelScroll; i < ms.size() && i < modelScroll + MAX_ROWS; i++) {
			ProviderCatalog.Model m = ms.get(i);
			int ry = ly + (i - modelScroll) * ROW;
			boolean sel = cur != null && cur.model().equals(m.slug());
			boolean hov = Hits.inside(mouseX, mouseY, rx, ry - 1, popX1 - 2, ry + ROW - 1);
			if (sel || hov) {
				g.fill(rx, ry - 1, popX1 - 2, ry + ROW - 1, sel ? SELECTED : CHIP);
			}
			String name = (sel ? "✔ " : "  ") + m.label();
			g.text(font, font.plainSubstrByWidth(name, popX1 - rx - 6), rx + 2, ry + 1, sel ? ACCENT : TEXT);
			hits.add(rx, ry - 1, popX1 - 2, ry + ROW - 1, () -> {
				if (logic.selectModel(m.slug())) {
					changed();
				}
			});
		}
		int ry = ly + listRows * ROW + 4;
		if (effort != null) {
			ry = chipRow(g, effort.label(), ry, effortChoices(effort), cur == null ? null : cur.effortValue(), v -> {
				if (logic.selectEffort(v)) {
					changed();
				}
			});
		}
		List<String[]> runtimes = logic.runtimeModes().stream().map(r -> new String[] {r.wire, r.label}).toList();
		ry = chipRow(g, "Access", ry, runtimes, cur == null ? null : cur.runtime().wire, v -> {
			Modes.Runtime r = Modes.Runtime.parse(v);
			if (r != null && logic.selectRuntime(r)) {
				changed();
			}
		});
		if (logic.planAvailable()) {
			ry = chipRow(g, "Mode", ry, List.of(new String[] {"default", "Chat"}, new String[] {"plan", "Plan"}),
					cur == null ? null : cur.interaction().wire, v -> {
						if (logic.setPlan("plan".equals(v))) {
							changed();
						}
					});
		}
		if (mode == Mode.THREAD) {
			boolean dirty = !logic.changed().isEmpty();
			String apply = busy ? "Applying…" : "Apply";
			int bw = font.width(apply) + 10;
			int bx = popX1 - bw - 4;
			g.fill(bx, ry, bx + bw, ry + ROW, dirty && !busy ? 0xFF2F6F4F : CHIP);
			g.text(font, apply, bx + 5, ry + 2, dirty && !busy ? TEXT : DIM);
			if (dirty && !busy) {
				hits.add(bx, ry, bx + bw, ry + ROW, this::apply);
			}
			String cancel = "Cancel";
			int cw = font.width(cancel) + 10;
			int cx = bx - cw - 4;
			g.fill(cx, ry, cx + cw, ry + ROW, CHIP);
			g.text(font, cancel, cx + 5, ry + 2, TEXT);
			hits.add(cx, ry, cx + cw, ry + ROW, () -> {
				logic.reset();
				open = false;
			});
		}
	}

	private static List<String[]> effortChoices(ProviderCatalog.Effort e) {
		java.util.ArrayList<String[]> out = new java.util.ArrayList<>();
		if (e.defaultValue() == null) {
			out.add(new String[] {null, "Default"});
		}
		for (ProviderCatalog.Choice c : e.choices()) {
			out.add(new String[] {c.id(), c.label()});
		}
		return out;
	}

	/** A labelled row of choice chips; returns the next row's y. */
	private int chipRow(GuiGraphicsExtractor g, String label, int ry, List<String[]> choices, @Nullable String current,
			java.util.function.Consumer<@Nullable String> pick) {
		int cx = popX0 + 4;
		g.text(font, label, cx, ry + 2, DIM);
		cx += Math.max(44, font.width(label) + 6);
		for (String[] c : choices) {
			int cw = font.width(c[1]) + 8;
			if (cx + cw > popX1 - 2) {
				break;
			}
			boolean sel = java.util.Objects.equals(c[0], current);
			g.fill(cx, ry, cx + cw, ry + ROW, sel ? SELECTED : CHIP);
			g.text(font, c[1], cx + 4, ry + 2, sel ? ACCENT : TEXT);
			String v = c[0];
			hits.add(cx, ry, cx + cw, ry + ROW, () -> pick.accept(v));
			cx += cw + 3;
		}
		return ry + ROW + 2;
	}

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
		status = Component.literal("Applying…");
		statusColor = WARN;
		Screen returnTo = Minecraft.getInstance().gui.screen();
		ActionsClient.configureThread(envId, threadId, threadTitle, logic, returnTo, r -> {
			busy = false;
			if (r.ok()) {
				open = false;
				status = Component.literal("Model updated");
				statusColor = OK;
				if (logic.pick() != null) {
					listener.onPicked(logic.pick());
				}
			} else {
				status = Component.literal(dev.t3craft.t3.ThreadControl.feedback(r).text());
				statusColor = ERROR;
			}
		});
	}
}
