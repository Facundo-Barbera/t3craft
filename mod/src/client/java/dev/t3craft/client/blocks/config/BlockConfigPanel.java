package dev.t3craft.client.blocks.config;

import dev.t3craft.blocks.Binding;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Control;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Field;
import dev.t3craft.client.t3ui.kit.T3Panel;
import dev.t3craft.client.t3ui.kit.T3PanelScreen;
import dev.t3craft.client.t3ui.kit.T3Select;
import dev.t3craft.client.t3ui.kit.T3Toggle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A block's config page (issue #33), opened with sneak + right click on a block that has one
 * ({@link BlockConfigPages}). Top to bottom:
 * <ul>
 *   <li>a read-only note on a remote server ({@link BlockConfigTarget#readOnlyReason()});</li>
 *   <li>whether the binding is automatic (the HQ writes it) or set by hand;</li>
 *   <li>the binding picker: the kind ({@link BlockConfigSection#kinds()}), a search box and the target, fed by
 *       {@link BindingChoices} (the same keys as {@code /t3craft blocks link});</li>
 *   <li>a preview of what the block will show ({@link BlockPreview});</li>
 *   <li>the block's own options ({@link BlockConfigSection#options}).</li>
 * </ul>
 * The footer saves (binding, manual flag and options of every cell at once), resets the block to automatic or
 * closes. Choosing a binding other than the stored one makes it manual: the HQ leaves the block alone until
 * "Reset to automatic". Changing only the options keeps the block's current mode.
 */
public final class BlockConfigPanel extends T3Panel {
	private static final int GAP = 4;
	private static final int H = T3Control.H;
	private static final int LABEL_W = 96;
	private static final int PREVIEW_ROWS = 6;
	private static final int QUIET_TICKS = 20;

	private final BlockConfigTarget target;
	private final BlockConfigSection section;
	private final BindingChoices choices;
	private BlockConfig saved;
	private BlockConfig draft;
	private Binding.Kind kind;
	private @Nullable T3Field search;
	private boolean searchChanged;
	/** Ticks left during which the page does not follow the block (a write is on its way back from the server). */
	private int quiet;
	private String status = "";

	public BlockConfigPanel(BlockConfigTarget target, BlockConfigSection section, BindingChoices choices) {
		this.target = target;
		this.section = section;
		this.choices = choices;
		this.saved = target.current();
		this.draft = saved;
		this.kind = kindOf(saved.binding());
	}

	/** Opens the page of the block at {@code pos} over the current screen (null when the block has no page). */
	public static boolean open(Level level, BlockPos pos) {
		BlockConfigSection s = BlockConfigPages.find(level.getBlockState(pos).getBlock());
		if (s == null) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		mc.gui.setScreen(new T3PanelScreen(null, new BlockConfigPanel(new WorldBlockTarget(level, pos, s), s, BindingChoices.live())));
		return true;
	}

	@Override
	public String title() {
		int n = target.cells();
		return "Configure " + section.blockName().toLowerCase(java.util.Locale.ROOT) + (n > 1 ? " wall (" + n + " blocks)" : "");
	}

	private boolean readOnly() {
		return target.readOnlyReason() != null;
	}

	private Binding.Kind kindOf(Binding b) {
		List<Binding.Kind> kinds = section.kinds();
		if (kinds.contains(b.kind())) {
			return b.kind();
		}
		return kinds.isEmpty() ? Binding.Kind.NONE : kinds.getFirst();
	}

	private T3Field search() {
		if (search == null) {
			search = new T3Field(font(), "Search").maxLength(60).hint("Search by name").onChange(v -> searchChanged = true);
		}
		return search;
	}

	@Override
	public void tick() {
		if (searchChanged) {
			searchChanged = false; // re-arranged here, not inside the box's own key handler
			host().relayout();
			return;
		}
		if (quiet > 0) {
			quiet--;
			return;
		}
		// nothing edited: follow the block (the HQ rebinding it after a reset, another player's change)
		BlockConfig now = target.current();
		if (draft.equals(saved) && !now.equals(saved)) {
			saved = now;
			draft = now;
			kind = kindOf(now.binding());
			host().relayout();
		}
	}

	private void say(String s) {
		status = s;
		host().relayout();
	}

	// ---- body ----------------------------------------------------------------------------------------------------

	@Override
	protected int arrange(Rect body, int y0) {
		int x = body.x();
		int w = body.w() - 10; // clear of the scrollbar
		int y = y0;
		String ro = target.readOnlyReason();
		if (ro != null) {
			y = text(x, y, w, ro, Tone.WARN) + GAP;
		}
		if (!section.kinds().isEmpty()) {
			y = text(x, y, w, draft.manual()
					? "Binding set by hand: the HQ leaves this block alone until you reset it to automatic."
					: "Automatic: the HQ binds this block. Choosing a binding here pins it.", Tone.MUTED) + GAP;
			Binding stored = draft.binding();
			if (!stored.isNone() && !section.kinds().contains(stored.kind())) {
				// e.g. a lamp the HQ bound to a module: the picker has no such kind; say what it is bound to
				y = text(x, y, w, "Now bound to " + stored.wire() + " (set by the HQ). Picking something below replaces it.", Tone.MUTED) + GAP;
			}
		}
		Form form = new Form(x, y, w);
		bindingPicker(form);
		y = form.y + GAP;
		y = text(x, y, w, "Preview", Tone.TITLE);
		place(new BlockPreview(section.preview(draft, choices, theme())), x, y, w, BlockPreview.height(PREVIEW_ROWS));
		y += BlockPreview.height(PREVIEW_ROWS) + GAP * 2;
		y = text(x, y, w, "Options", Tone.TITLE);
		form = new Form(x, y, w);
		section.options(form, draft);
		return form.y + GAP;
	}

	private void bindingPicker(Form form) {
		List<Binding.Kind> kinds = section.kinds();
		if (kinds.isEmpty()) {
			return;
		}
		form.select(section.kindTitle(), kinds, section::kindLabel, kind, k -> {
			kind = k;
			List<BindingChoices.Choice> list = choices.of(k, "");
			Binding next = k == Binding.Kind.BEACON ? Binding.BEACON : draft.binding().kind() == k ? draft.binding()
					: list.isEmpty() ? Binding.NONE : list.getFirst().binding();
			draft = draft.withBinding(next);
			host().relayout();
		});
		if (kind == Binding.Kind.BEACON) {
			return; // one possible target
		}
		form.field("Search", search());
		List<BindingChoices.Choice> list = new ArrayList<>(choices.of(kind, search().value()));
		BindingChoices.Choice current = draft.binding().kind() == kind ? choices.find(draft.binding()) : null;
		if (current != null && list.stream().noneMatch(c -> c.binding().equals(draft.binding()))) {
			list.addFirst(current); // the stored binding stays visible whatever the search (and when the client does not know it)
		}
		if (list.isEmpty()) {
			form.note(search().value().isBlank() ? "Nothing of this kind is known yet (connect an environment)." : "Nothing matches the search.");
			return;
		}
		BindingChoices.Choice shown = null;
		for (BindingChoices.Choice c : list) {
			if (c.binding().equals(draft.binding())) {
				shown = c;
			}
		}
		form.select(section.kindLabel(kind), list, BindingChoices.Choice::text, shown, c -> {
			draft = draft.withBinding(c.binding());
			host().relayout();
		});
		if (shown == null) {
			form.note("Not bound yet: pick one above.");
		}
	}

	// ---- footer ---------------------------------------------------------------------------------------------------

	@Override
	protected int footerHeight() {
		return H + 18;
	}

	@Override
	protected void arrangeFooter(Rect f) {
		pinText(f.x() + 2, f.y() + 4, ChatDraw.clip(font(), status, f.w() - 4), Tone.MUTED);
		int by = f.y() + 14;
		int bw = (f.w() - 2 * GAP) / 3;
		T3Button save = pin(new T3Button("Save", T3Button.Style.PRIMARY, this::save), f.x(), by, bw, H);
		save.setEnabled(!readOnly() && !draft.equals(saved));
		T3Button reset = pin(new T3Button("Reset to automatic", this::reset), f.x() + bw + GAP, by, bw, H);
		reset.setEnabled(!readOnly() && saved.manual());
		reset.setTooltip("Clears the manual flag: the HQ binds this block again.", "The options stay as they are.");
		pin(new T3Button("Close", host()::back), f.x() + 2 * (bw + GAP), by, f.w() - 2 * (bw + GAP), H);
	}

	private void save() {
		if (readOnly()) {
			return;
		}
		boolean manual = draft.manual() || !draft.binding().equals(saved.binding());
		BlockConfig next = draft.withManual(manual);
		target.write(next);
		quiet = QUIET_TICKS;
		saved = next;
		draft = next;
		say(manual ? "Saved. The binding is manual now." : "Saved.");
	}

	private void reset() {
		if (readOnly()) {
			return;
		}
		target.resetToAutomatic();
		quiet = QUIET_TICKS;
		saved = saved.withManual(false);
		draft = draft.withManual(false);
		say("Automatic again: the HQ binds this block on its next pass.");
	}

	// ---- the form sections write through ------------------------------------------------------------------------

	/**
	 * Rows of label + control, top-down, for {@link BlockConfigSection#options}. Every control is disabled on a
	 * read-only page. Changes go into the page's draft (saved with the footer's Save).
	 */
	public final class Form {
		private final int x;
		private final int w;
		private int y;

		Form(int x, int y, int w) {
			this.x = x;
			this.y = y;
			this.w = w;
		}

		/** The page's current draft. */
		public BlockConfig draft() {
			return draft;
		}

		/** Sets an option of the draft ({@code def} is not stored) and re-arranges the page. */
		public void set(String key, String value, String def) {
			draft = draft.withOption(key, value, def);
			host().relayout();
		}

		public <T> void select(String label, List<T> options, Function<T, String> labelOf, @Nullable T current, Consumer<T> onChange) {
			label(label);
			T3Select<T> s = place(new T3Select<>(options, labelOf, current, onChange), x + LABEL_W, y, w - LABEL_W, H);
			s.setEnabled(!readOnly());
			y += H + GAP;
		}

		public void toggle(String label, boolean value, Consumer<Boolean> onChange) {
			T3Toggle t = place(new T3Toggle(label, value, onChange), x, y, w, H);
			t.setEnabled(!readOnly());
			y += H + GAP;
		}

		void field(String label, T3Field f) {
			label(label);
			placeField(f, x + LABEL_W, y, w - LABEL_W);
			y += H + GAP;
		}

		/** A muted line under the last row. */
		public void note(String s) {
			y = text(x + LABEL_W, y, w - LABEL_W, s, Tone.MUTED) + GAP;
		}

		private void label(String s) {
			text(x, y + (H - 8) / 2, LABEL_W - GAP, ChatDraw.clip(font(), s, LABEL_W - GAP), Tone.NORMAL);
		}
	}
}
