package dev.t3craft.client.config;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.config.EnvironmentActions.EnvRow;
import dev.t3craft.client.config.T3CraftSettings.EnvPrefs;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Control;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Field;
import dev.t3craft.client.t3ui.kit.T3Panel;
import dev.t3craft.client.t3ui.kit.T3Toggle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/**
 * One environment as a page of the UI kit (docs/features/ui-kit.md): rename, accent colour (swatches),
 * show/hide, and its visibility filter (needs you / working / recent switches, recent window and cap;
 * blank = the global default). Opened by "Edit" in {@link EnvironmentsPanel} with {@code host().show(...)}, so
 * the page stays where it is (inside {@code T3Screen}, or its own screen) and "Back" returns to the list.
 *
 * <p>Same rules as the screen it replaces: edits go to a draft and are written on "Save"; a rename
 * reloads the sources by itself, anything else reloads after the write.
 */
public final class EnvironmentEditPanel extends T3Panel {
	/** The accent colours a player can pick (Minecraft's chat palette), as 0xRRGGBB: they are data saved in the config, not UI tokens. */
	static final List<Integer> PALETTE = List.of(0x55FFFF, 0x55FF55, 0xFFFF55, 0xFFAA00, 0xFF5555, 0xFF55FF, 0x5555FF, 0xFFFFFF, 0xAAAAAA);

	private static final int ROW = 18;
	private static final int MAX_W = 440;

	private final EnvironmentActions actions;
	private final EnvRow row;
	private String originalName;
	private EnvPrefs draft;
	private @Nullable T3Field nameField;
	private @Nullable T3Field windowField;
	private @Nullable T3Field maxField;
	private String status = "";
	private Tone statusTone = Tone.MUTED;

	public EnvironmentEditPanel(EnvironmentActions actions, EnvRow row) {
		this.actions = actions;
		this.row = row;
		this.draft = ConfigManager.get().settings().env(row.id());
		this.originalName = draft.labelOr(row.label());
	}

	@Override
	public String title() {
		return "Environment: " + originalName;
	}

	private void say(String msg, Tone tone) {
		status = msg;
		statusTone = tone;
	}

	// ---- layout ----------------------------------------------------------------------------------------

	@Override
	protected int footerHeight() {
		return T3Control.H + 10;
	}

	@Override
	protected void arrangeFooter(Rect footer) {
		int y = footer.y() + 5;
		int bw = 56;
		pin(new T3Button("Back", () -> host().back()), footer.right() - bw, y, bw, T3Control.H);
		pin(new T3Button("Save", T3Button.Style.PRIMARY, this::save), footer.right() - 2 * bw - 4, y, bw, T3Control.H);
		int textW = footer.w() - 2 * bw - 14;
		List<String> lines = T3Draw.wrap(font(), status, textW, 2);
		for (int i = 0; i < lines.size(); i++) {
			pinText(footer.x() + 2, footer.y() + 4 + i * T3Draw.lineHeight(font()), lines.get(i), statusTone);
		}
	}

	@Override
	protected int arrange(Rect body, int y0) {
		int w = Math.min(MAX_W, body.w() - 8);
		int x = body.x() + (body.w() - w) / 2 - 3;
		int y = y0 + 4;
		y = text(x + 4, y, w - 8, row.url() + " · " + row.status(), Tone.MUTED) + 6;

		y = text(x + 4, y, w - 8, "Name (shown on the NPCs)", Tone.MUTED);
		placeField(nameField(), x, y + 1, w);
		y += T3Control.H + 7;

		y = text(x + 4, y, w - 8, "Accent colour", Tone.MUTED);
		List<@Nullable Integer> colours = colours();
		int swatchesH = Swatches.heightFor(colours.size(), w);
		place(new Swatches(colours, draft.color(), c -> draft = draft.withColor(c)), x, y + 1, w, swatchesH);
		y += swatchesH + 5;
		y = toggle(x, y, w, "Visible (show this environment's NPCs)", draft.visible(), v -> draft = draft.withVisible(v));
		y += 8;

		y = text(x + 4, y, w - 8, "Show threads that are...", Tone.TITLE) + 3;
		y = toggle(x, y, w, "Needs you", draft.showNeedsYou(),
				v -> draft = draft.withFilter(v, draft.showWorking(), draft.showRecent(), draft.windowHours(), draft.max()));
		y = toggle(x, y, w, "Working", draft.showWorking(),
				v -> draft = draft.withFilter(draft.showNeedsYou(), v, draft.showRecent(), draft.windowHours(), draft.max()));
		y = toggle(x, y, w, "Recent", draft.showRecent(),
				v -> draft = draft.withFilter(draft.showNeedsYou(), draft.showWorking(), v, draft.windowHours(), draft.max()));
		y += 8;

		y = text(x + 4, y, w - 8, "Recent window (hours)", Tone.MUTED);
		placeField(windowField(), x, y + 1, w);
		y += T3Control.H + 7;
		y = text(x + 4, y, w - 8, "Max NPCs", Tone.MUTED);
		placeField(maxField(), x, y + 1, w);
		y += T3Control.H + 5;
		y = text(x + 4, y, w - 8, "Leave a number blank to use the global value from Settings.", Tone.MUTED) + 8;

		T3Button reset = new T3Button("Reset to defaults", this::resetDefaults);
		reset.setTooltip("Puts every option of this page back to its default.", "Nothing is written until you press Save.");
		place(reset, x, y, w, T3Control.H);
		return y + ROW + 6;
	}

	private int toggle(int x, int y, int w, String label, boolean value, Consumer<Boolean> set) {
		place(new T3Toggle(label, value, set), x, y, w, T3Control.H);
		return y + ROW;
	}

	/** None, the colour the file holds when it is not in the palette, then the palette. */
	private List<@Nullable Integer> colours() {
		List<@Nullable Integer> c = new ArrayList<>();
		c.add(null);
		if (draft.color() != null && !PALETTE.contains(draft.color())) {
			c.add(draft.color());
		}
		c.addAll(PALETTE);
		return c;
	}

	// ---- state -----------------------------------------------------------------------------------------------

	// The fields live as long as the page: a relayout (a switch, the wheel) keeps what was typed.

	private T3Field nameField() {
		if (nameField == null) {
			nameField = new T3Field(font(), "Name").maxLength(32).hint("Name");
			nameField.setValue(originalName);
		}
		return nameField;
	}

	private T3Field windowField() {
		if (windowField == null) {
			T3CraftSettings s = ConfigManager.get().settings();
			windowField = new T3Field(font(), "Recent window (hours)").maxLength(8).hint("global: " + ConfigNumbers.format(s.recentWindowHours()));
			windowField.setValue(draft.windowHours() == null ? "" : ConfigNumbers.format(draft.windowHours()));
		}
		return windowField;
	}

	private T3Field maxField() {
		if (maxField == null) {
			T3CraftSettings s = ConfigManager.get().settings();
			maxField = new T3Field(font(), "Max NPCs").maxLength(4).hint("global: " + s.maxPerEnvironment());
			maxField.setValue(draft.max() == null ? "" : Integer.toString(draft.max()));
		}
		return maxField;
	}

	private void resetDefaults() {
		draft = EnvPrefs.DEFAULT.withLabel(draft.label());
		windowField().setValue("");
		maxField().setValue("");
		say("Defaults restored: press Save to keep them", Tone.WARN);
		host().relayout();
	}

	private void save() {
		Double hours = ConfigNumbers.optional(windowField().value());
		Double max = ConfigNumbers.optional(maxField().value());
		if (hours != null && !ConfigCodec.validWindow(hours)) {
			fail("Recent window: hours 0 to " + (long) T3CraftSettings.MAX_WINDOW_HOURS + ", or blank for the global value");
			return;
		}
		if (max != null && !ConfigCodec.validMax(max)) {
			fail("Max NPCs: whole number 1 to " + T3CraftSettings.MAX_PER_ENV_LIMIT + ", or blank for the global value");
			return;
		}
		String name = ConfigCodec.sanitizeLabel(nameField().value());
		if (name == null) {
			fail("The name cannot be empty");
			return;
		}
		EnvPrefs next = draft.withFilter(draft.showNeedsYou(), draft.showWorking(), draft.showRecent(), hours,
				max == null ? null : max.intValue());
		ConfigManager.SaveResult r = ConfigManager.get().update(s -> s.withEnv(row.id(), next));
		if (!r.ok()) {
			fail(r.message());
			return;
		}
		if (!name.equals(originalName)) {
			say(actions.rename(row.id(), name), Tone.OK); // rename reloads the sources itself
			originalName = name;
		} else {
			actions.reload();
			say(r.message() + " and reloaded", Tone.OK);
		}
		draft = ConfigManager.get().settings().env(row.id());
		host().relayout();
	}

	private void fail(String msg) {
		say(msg, Tone.ERROR);
		host().relayout();
	}

	// ---- the accent swatches ---------------------------------------------------------------------------

	/**
	 * A row of colour squares (wrapping onto more rows when narrow), the chosen one ringed with the accent
	 * token. The first square is "no colour". The colours themselves are the player's data, not tokens.
	 */
	private static final class Swatches extends T3Control {
		private static final int SIZE = 14;
		private static final int GAP = 6;
		private static final int PAD = 2;

		private final List<@Nullable Integer> colours;
		private final Consumer<@Nullable Integer> onPick;
		private @Nullable Integer selected;

		Swatches(List<@Nullable Integer> colours, @Nullable Integer selected, Consumer<@Nullable Integer> onPick) {
			this.colours = colours;
			this.selected = selected;
			this.onPick = onPick;
		}

		private static int perRow(int w) {
			return Math.max(1, (w - 2 * PAD + GAP) / (SIZE + GAP));
		}

		static int heightFor(int count, int w) {
			int rows = (count + perRow(w) - 1) / perRow(w);
			return 2 * PAD + rows * SIZE + (rows - 1) * GAP;
		}

		private Rect cell(int i) {
			Rect b = bounds();
			int per = perRow(b.w());
			return new Rect(b.x() + PAD + (i % per) * (SIZE + GAP), b.y() + PAD + (i / per) * (SIZE + GAP), SIZE, SIZE);
		}

		private int indexAt(double mx, double my) {
			for (int i = 0; i < colours.size(); i++) {
				if (cell(i).contains(mx, my)) {
					return i;
				}
			}
			return -1;
		}

		@Override
		public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
			if (!visible()) {
				return;
			}
			for (int i = 0; i < colours.size(); i++) {
				Rect c = cell(i);
				Integer rgb = colours.get(i);
				boolean on = Objects.equals(rgb, selected);
				if (on) {
					ChatDraw.outline(g, c.x() - 2, c.y() - 2, SIZE + 4, SIZE + 4, t.accent());
				} else if (hovered(mx, my) && c.contains(mx, my)) {
					ChatDraw.outline(g, c.x() - 2, c.y() - 2, SIZE + 4, SIZE + 4, t.muted());
				}
				if (rgb == null) {
					ChatDraw.round(g, c.x(), c.y(), c.w(), c.h(), t.inset());
					ChatDraw.outline(g, c.x(), c.y(), c.w(), c.h(), t.edge());
					T3Draw.centered(g, font, t, "-", c.x(), c.y(), c.w(), c.h(), t.muted());
				} else {
					ChatDraw.round(g, c.x(), c.y(), c.w(), c.h(), 0xFF000000 | rgb);
					ChatDraw.outline(g, c.x(), c.y(), c.w(), c.h(), t.edge());
				}
			}
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (!enabled() || !visible() || !MouseButtons.isLeft(button)) {
				return false;
			}
			int i = indexAt(mx, my);
			if (i < 0) {
				return false;
			}
			selected = colours.get(i);
			onPick.accept(selected);
			return true;
		}

		@Override
		public @Nullable List<String> tooltipAt(double mx, double my) {
			if (!visible() || !bounds().contains(mx, my)) {
				return null;
			}
			int i = indexAt(mx, my);
			if (i < 0) {
				return null;
			}
			Integer rgb = colours.get(i);
			return List.of(rgb == null ? "No colour" : ConfigCodec.formatColor(rgb));
		}
	}
}
