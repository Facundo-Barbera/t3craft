package dev.t3craft.client.debug;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Check;
import dev.t3craft.client.t3ui.kit.T3Control;
import dev.t3craft.client.t3ui.kit.T3Dialog;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Field;
import dev.t3craft.client.t3ui.kit.T3List;
import dev.t3craft.client.t3ui.kit.T3Panel;
import dev.t3craft.client.t3ui.kit.T3Select;
import dev.t3craft.client.t3ui.kit.T3TextArea;
import dev.t3craft.client.t3ui.kit.T3Toggle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/**
 * Gallery page for the kit's keyboard and form controls (issue #22; {@code /t3dev ui kit-controls}): every
 * control in one scrolling page so Tab / Shift+Tab, the brass focus ring, Enter / Space, the list keys and
 * the draggable scrollbar can be tried by hand, plus a modal that holds a text field and a text area.
 * Nothing here touches the config or the network.
 */
public final class UiKitDemoPanel extends T3Panel {
	private static final int ROW = 18;
	private static final int LIST_H = 110;
	private static final List<String> SIZES = List.of("Small", "Medium", "Large", "Extra large");

	private final boolean[] checks = {true, false, true};
	private final List<String> rows = new ArrayList<>();
	private final T3List<String> list;
	private boolean toggle;
	private int size = 1;
	private boolean keepCopy = true;
	private String status = "Tab / Shift+Tab move the focus";

	private @Nullable T3Field search;
	private @Nullable T3Field field;
	private @Nullable T3TextArea area;
	private @Nullable T3Field modalName;
	private @Nullable T3TextArea modalNotes;

	public UiKitDemoPanel() {
		for (int i = 1; i <= 60; i++) {
			rows.add("List row " + i);
		}
		this.list = new T3List<>(14, new T3List.Row<String>() {
			@Override
			public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, String item, Rect r, int mx, int my) {
				T3Draw.text(g, font, t, item, r.x() + 4, r.y() + (r.h() - 8) / 2, t.text());
			}

			@Override
			public boolean mouseClicked(String item, Rect r, double mx, double my, int button) {
				if (MouseButtons.isLeft(button)) {
					say("Picked " + item);
				}
				return true;
			}
		});
	}

	@Override
	public String title() {
		return "Kit controls";
	}

	private void say(String s) {
		status = s;
		host().relayout();
	}

	// ---- header and footer -----------------------------------------------------------------------------

	@Override
	protected int headerHeight() {
		return T3Control.H + 6;
	}

	@Override
	protected void arrangeHeader(Rect h) {
		if (search == null) {
			search = new T3Field(font(), "Pinned field").maxLength(40).hint("A pinned field (header): Tab reaches it first");
		}
		pinField(search, h.x(), h.y(), h.w() - 92);
		pin(new T3Button("Modal with fields", this::openModal), h.right() - 88, h.y(), 88, T3Control.H);
	}

	@Override
	protected int footerHeight() {
		return T3Control.H + 8;
	}

	@Override
	protected void arrangeFooter(Rect f) {
		pinText(f.x() + 2, f.y() + 8, ChatDraw.clip(font(), status, f.w() - 76), Tone.MUTED);
		pin(new T3Button("Done", T3Button.Style.PRIMARY, () -> host().back()), f.right() - 66, f.y() + 4, 66, T3Control.H);
	}

	// ---- body ------------------------------------------------------------------------------------------------

	private T3Field field() {
		if (field == null) {
			field = new T3Field(font(), "Name").maxLength(40).hint("A field in the body: it slides out of view when scrolled");
		}
		return field;
	}

	private T3TextArea area() {
		if (area == null) {
			area = new T3TextArea(font(), 4).maxLength(400).hint("A text area: Enter is a new line, Ctrl+Enter submits");
			area.onSubmit(() -> say("Submitted " + area.value().length() + " characters"));
		}
		return area;
	}

	@Override
	protected int arrange(Rect body, int y0) {
		int x = body.x();
		int w = body.w() - 10; // room for the page's scrollbar
		int y = y0;
		y = text(x, y, w, "Buttons", Tone.TITLE) + 2;
		int bw = (w - 8) / 3;
		place(new T3Button("Default", () -> say("Default pressed")), x, y, bw, T3Control.H);
		place(new T3Button("Primary", T3Button.Style.PRIMARY, () -> say("Primary pressed")), x + bw + 4, y, bw, T3Control.H);
		place(new T3Button("Disabled", () -> say("never")), x + 2 * (bw + 4), y, bw, T3Control.H).setEnabled(false);
		y += ROW + 6;

		y = text(x, y, w, "Switch and check boxes (Enter or Space flips the focused one)", Tone.TITLE) + 2;
		place(new T3Toggle("A switch", toggle, v -> {
			toggle = v;
			say("Switch " + (v ? "on" : "off"));
		}), x, y, w, T3Control.H);
		y += ROW;
		for (int i = 0; i < checks.length; i++) {
			int n = i;
			place(new T3Check("Check box " + (i + 1), checks[i], v -> {
				checks[n] = v;
				say("Check box " + (n + 1) + (v ? " checked" : " cleared"));
			}), x, y, w, T3Control.H);
			y += ROW;
		}
		place(new T3Check("A disabled check box", true, v -> {
		}), x, y, w, T3Control.H).setEnabled(false);
		y += ROW + 6;

		y = text(x, y, w, "Select (Enter, Space or Down opens it)", Tone.TITLE) + 2;
		place(new T3Select<String>(SIZES, s -> s, SIZES.get(size), v -> {
			size = SIZES.indexOf(v);
			say("Size: " + v);
		}), x, y, w, T3Control.H);
		y += ROW + 6;

		y = text(x, y, w, "Text field", Tone.TITLE) + 2;
		placeField(field(), x, y, w);
		y += T3Control.H + 8;

		y = text(x, y, w, "Text area (4 lines, scrolls)", Tone.TITLE) + 2;
		T3TextArea a = area();
		place(a, x, y, w, a.height());
		y += a.height() + 8;

		y = text(x, y, w, "List: click or Tab to it, then Up / Down / Home / End / PageUp / PageDown; drag the thumb", Tone.TITLE) + 2;
		list.setItems(rows);
		place(list, x, y, w, LIST_H);
		y += LIST_H + 8;
		return text(x, y, w, "End of the page.", Tone.MUTED) + 4;
	}

	// ---- the modal -----------------------------------------------------------------------------------------------

	private void openModal() {
		if (modalName == null) {
			modalName = new T3Field(font(), "Modal name").maxLength(40).hint("A field inside a modal");
			modalNotes = new T3TextArea(font(), 3).maxLength(300).hint("Notes");
		}
		T3Field name = modalName;
		T3TextArea notes = modalNotes;
		T3Dialog d = new T3Dialog("A modal with fields", 340)
				.text("The modal is laid out once, not on every frame, so it can hold a text field and a text area. Tab walks them.", Tone.MUTED)
				.gap(2)
				.field("Name", name)
				.area("Notes", notes)
				.control(new T3Check("Keep a copy", keepCopy, v -> keepCopy = v))
				.gap(2)
				.buttons(new T3Button("Save", T3Button.Style.PRIMARY, this::saveModal), new T3Button("Cancel", this::closeModal))
				.initialFocus(name)
				.onCancel(this::closeModal)
				.defaultAction(this::saveModal);
		host().modal(d);
	}

	private void saveModal() {
		String name = modalName == null ? "" : modalName.value();
		String s = "Saved \"" + name + "\"" + (keepCopy ? " with a copy" : "");
		host().modal(null);
		say(s);
	}

	private void closeModal() {
		host().modal(null);
		say("Modal closed");
	}
}
