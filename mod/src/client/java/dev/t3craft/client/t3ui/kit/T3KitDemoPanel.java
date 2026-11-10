package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/**
 * The UI kit on one page, for trying the keyboard, the scrollbars and the text boxes by hand
 * ({@code /t3dev ui kit}, docs/features/ui-kit.md "Probarlo"). Nothing here touches the config or the
 * network: every control only writes a line in the footer.
 *
 * <p>Tab "Controls": a switch, check boxes, the four button styles, a select and a 60-row list (Up, Down,
 * Home, End, Page Up, Page Down, Enter; drag its scrollbar). Tab "Text": a field and a multi-line box in
 * the scrolling body (the field is cut at the viewport's edge, the box is hidden until it fits), and a
 * second field below the fold (Tab to it and the page scrolls). The footer opens a dialog with fields.
 */
public final class T3KitDemoPanel extends T3Panel {
	private static final List<String> TABS = List.of("Controls", "Text");
	private static final int GAP = 4;
	private static final int H = T3Control.H;

	private final T3List<String> list;
	private int tab;
	private boolean sw = true;
	private boolean remember = true;
	private boolean other;
	private String size = "Medium";
	private String status = "Tab and Shift+Tab move the brass ring; Enter or Space press the focused control.";
	private @Nullable T3Field search;
	private @Nullable T3Field name;
	private @Nullable T3Field tail;
	private @Nullable T3TextArea notes;
	private @Nullable T3Field modalName;
	private @Nullable T3TextArea modalNotes;

	public T3KitDemoPanel() {
		List<String> rows = new ArrayList<>();
		for (int i = 1; i <= 60; i++) {
			rows.add("Row " + i);
		}
		this.list = new T3List<>(14, new T3List.Row<String>() {
			@Override
			public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, String item, Rect r, int mx, int my) {
				g.text(font, item, r.x() + 4, r.y() + (r.h() - 8) / 2, t.text(), t.shadow());
			}

			@Override
			public boolean mouseClicked(String item, Rect r, double mx, double my, int button) {
				return true;
			}

			@Override
			public void activated(String item) {
				say("Activated " + item);
			}
		});
		this.list.setItems(rows);
	}

	@Override
	public String title() {
		return "UI kit demo";
	}

	private void say(String s) {
		status = s;
		host().relayout();
	}

	// ---- lazily made boxes (they keep their text across re-arranging) ---------------------------------------

	private T3Field search() {
		if (search == null) {
			search = new T3Field(font(), "Search").maxLength(40).hint("A pinned field never scrolls");
		}
		return search;
	}

	private T3Field name() {
		if (name == null) {
			name = new T3Field(font(), "Name").maxLength(40).hint("Scroll slowly: this field is cut, not hidden");
		}
		return name;
	}

	private T3Field tail() {
		if (tail == null) {
			tail = new T3Field(font(), "Tail").maxLength(40).hint("Below the fold: Tab scrolls here");
		}
		return tail;
	}

	private T3TextArea notes() {
		if (notes == null) {
			notes = new T3TextArea(font(), "Notes").maxLength(300).hint("Several lines; Enter types a new line");
		}
		return notes;
	}

	// ---- header and footer -------------------------------------------------------------------------------------

	@Override
	protected int headerHeight() {
		return 2 * H + 8;
	}

	@Override
	protected void arrangeHeader(Rect h) {
		pin(new T3Tabs(font(), TABS, tab, i -> {
			tab = i;
			host().relayout();
		}), h.x(), h.y(), h.w(), H + 2);
		pinField(search(), h.x(), h.y() + H + 6, h.w());
	}

	@Override
	protected int footerHeight() {
		return H + 18;
	}

	@Override
	protected void arrangeFooter(Rect f) {
		pinText(f.x() + 2, f.y() + 4, ChatDraw.clip(font(), status, f.w() - 4), Tone.MUTED);
		int bw = Math.min(150, (f.w() - GAP) * 2 / 3);
		int by = f.y() + 14;
		pin(new T3Button("Dialog with fields", T3Button.Style.PRIMARY, this::openModal), f.x(), by, bw, H);
		pin(new T3Button("Done", host()::back), f.x() + bw + GAP, by, Math.max(40, f.w() - bw - GAP), H);
	}

	// ---- body ----------------------------------------------------------------------------------------------------

	@Override
	protected int arrange(Rect body, int y0) {
		int x = body.x();
		int w = body.w() - 10; // clear of the scrollbar
		return tab == 0 ? controls(x, y0, w) : texts(x, y0, w);
	}

	private int controls(int x, int y, int w) {
		y = text(x, y, w, "Keyboard: Tab and Shift+Tab move the brass ring. Enter or Space press a button or flip a switch. "
				+ "The list takes Up, Down, Home, End, Page Up, Page Down and Enter; drag its scrollbar.", Tone.MUTED);
		y += GAP;
		place(new T3Toggle("Switch (applies at once)", sw, v -> {
			sw = v;
			say("Switch " + (v ? "on" : "off"));
		}), x, y, w, H);
		y += H + GAP;
		place(new T3Check("Check box (part of a form)", remember, v -> {
			remember = v;
			say("Check box " + (v ? "checked" : "unchecked"));
		}), x, y, w, H);
		y += H + GAP;
		T3Check off = place(new T3Check("A disabled check box", other, v -> other = v), x, y, w, H);
		off.setEnabled(false);
		y += H + GAP;
		int bw = (w - 3 * GAP) / 4;
		place(new T3Button("Default", () -> say("Default pressed")), x, y, bw, H);
		place(new T3Button("Primary", T3Button.Style.PRIMARY, () -> say("Primary pressed")), x + (bw + GAP), y, bw, H);
		place(new T3Button("Danger", T3Button.Style.DANGER, () -> say("Danger pressed")), x + 2 * (bw + GAP), y, bw, H);
		place(new T3Button("Ghost", T3Button.Style.GHOST, () -> say("Ghost pressed")), x + 3 * (bw + GAP), y, bw, H);
		y += H + GAP;
		place(new T3Select<>(List.of("Small", "Medium", "Large"), s -> s, size, v -> {
			size = v;
			say("Select: " + v);
		}), x, y, Math.max(80, w / 2), H);
		y += H + GAP * 2;
		y = text(x, y, w, "List of 60 rows", Tone.TITLE);
		place(list, x, y, w, 100);
		y += 100 + GAP;
		y = text(x, y, w, "The page scrolls with the wheel or by dragging its own scrollbar; the list inside it has its own.", Tone.MUTED);
		return y + GAP;
	}

	private int texts(int x, int y, int w) {
		y = text(x, y, w, "A field in a scrolling page is cut at the edge of the viewport instead of jumping out of it. "
				+ "Scroll this tab slowly and watch the first field.", Tone.MUTED);
		y += GAP;
		y = text(x, y, w, "Name", Tone.NORMAL);
		placeField(name(), x, y, w);
		y += H + GAP * 2;
		y = text(x, y, w, "Notes (the multi-line box is hidden until it is wholly in view)", Tone.NORMAL);
		placeTextArea(notes(), x, y, w, 56);
		y += 56 + GAP * 2;
		y = text(x, y, w, "Some filler so that this tab is taller than the window, which makes the page scroll. "
				+ "Press Tab until the focus reaches the field below: the page scrolls to show it. "
				+ "Shift+Tab goes back. A click in a field puts the cursor there, as always.", Tone.MUTED);
		y += 60;
		y = text(x, y, w, "Tail", Tone.NORMAL);
		placeField(tail(), x, y, w);
		return y + H + GAP;
	}

	// ---- dialog ------------------------------------------------------------------------------------------------------

	private T3Field modalName() {
		if (modalName == null) {
			modalName = new T3Field(font(), "Name").maxLength(40).hint("Type here");
		}
		return modalName;
	}

	private T3TextArea modalNotes() {
		if (modalNotes == null) {
			modalNotes = new T3TextArea(font(), "Notes").maxLength(200).hint("Notes");
		}
		return modalNotes;
	}

	private void openModal() {
		T3Field nameField = modalName();
		T3TextArea notesArea = modalNotes();
		T3Dialog d = new T3Dialog("Dialog with fields", 340);
		d.text("This dialog is laid out once, not every frame. Its boxes are drawn by the dialog itself. Tab moves between them.", Tone.MUTED);
		d.field("Name", nameField);
		d.textArea("Notes", notesArea, 48);
		d.control(new T3Check("Remember this", remember, v -> remember = v));
		d.gap(2);
		Runnable close = () -> host().modal(null);
		T3Button save = new T3Button("Save", T3Button.Style.PRIMARY, () -> {
			close.run();
			say("Saved '" + nameField.value() + "', " + notesArea.value().length() + " characters of notes");
		});
		T3Button cancel = new T3Button("Cancel", close);
		d.buttons(save, cancel);
		d.onCancel(close);
		d.initialFocus(nameField);
		host().modal(d);
	}
}
