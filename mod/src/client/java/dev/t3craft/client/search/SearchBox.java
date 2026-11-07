package dev.t3craft.client.search;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.t3.ThreadSearch;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Self-contained search widget for a terminal-style screen: a text field ({@link EditBox}) plus the
 * result list it draws in whatever rectangle the host gives it. All state lives in
 * {@link SearchModel} (debounce, cancellation); this class only does input and drawing, so moving
 * it to another screen is: create it, add {@link #layout}'s widget, forward {@code tick},
 * {@code keyPressed}, {@code mouseClicked} and {@code removed}, and call {@link #render} where the
 * list should go while {@link #active()}.
 *
 * <p>Keys while focused: type to search (debounced), Enter opens the selected result (or searches
 * now), ↑/↓ select, Esc clears then unfocuses. Ctrl+F focuses it from anywhere in the host.
 */
public final class SearchBox {
	@FunctionalInterface
	public interface Opener {
		void open(String env, String threadId);
	}

	private static final int ROW_H = 12;
	private static final int TEXT = 0xFFE6E6E6;
	private static final int DIM = 0xFF8A949E;
	private static final int WARN = 0xFFFFAA00;
	private static final int ERROR = 0xFFFF5555;
	private static final int SELECTED = 0xFF27414A;
	private static final int BORDER = 0xFF3A6B73;

	private final Font font;
	private final SearchModel model;
	private final Opener opener;
	private @Nullable EditBox field;
	private int selected;
	private int first;
	private final List<Row> rows = new ArrayList<>();

	private record Row(int x0, int y0, int x1, int y1, int index) {
		boolean contains(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	public SearchBox(Font font, SearchModel model, Opener opener) {
		this.font = font;
		this.model = model;
		this.opener = opener;
	}

	/** (Re)creates the field at this position, keeping the text. The host adds it as a widget. */
	public EditBox layout(int x, int y, int w, int h) {
		EditBox f = new EditBox(font, x, y, w, h, Component.literal("Search threads"));
		f.setMaxLength(dev.t3craft.t3.RpcFrames.SEARCH_MAX_LENGTH);
		f.setHint(Component.literal("Search threads… (Ctrl+F)").withStyle(EditBox.SEARCH_HINT_STYLE));
		f.setValue(model.text());
		f.setResponder(text -> {
			model.setText(text, System.currentTimeMillis());
			selected = 0;
			first = 0;
		});
		field = f;
		return f;
	}

	public SearchModel model() {
		return model;
	}

	public void setVisible(boolean visible) {
		if (field != null) {
			field.visible = visible;
			if (!visible) {
				field.setFocused(false);
			}
		}
	}

	/** True while the field has text: the host shows {@link #render} instead of its own list. */
	public boolean active() {
		return model.active();
	}

	public boolean focused() {
		return field != null && field.isFocused();
	}

	/** Unfocuses the field (the host calls this on a click outside it). Keeps the text. */
	public void blur(Screen host) {
		if (field != null && field.isFocused()) {
			field.setFocused(false);
			if (host.getFocused() == field) {
				host.setFocused(null);
			}
		}
	}

	public void tick() {
		model.tick(System.currentTimeMillis());
	}

	/** Screen closed: cancel the search in flight. */
	public void removed() {
		model.close();
	}

	public void clear() {
		if (field != null) {
			field.setValue("");
		} else {
			model.clear();
		}
	}

	/**
	 * Key handling. Returns true when consumed. While focused every key is consumed (so the host's
	 * single-key shortcuts don't fire while typing); while results are shown, ↑/↓/Enter/Esc act on
	 * them even unfocused.
	 */
	public boolean keyPressed(Screen host, KeyEvent e) {
		int k = e.key();
		if (field == null || !field.visible) {
			return false;
		}
		if (k == InputConstants.KEY_F && e.hasControlDown()) {
			host.setFocused(field);
			field.setFocused(true);
			return true;
		}
		boolean focused = field.isFocused();
		if (!focused && !active()) {
			return false;
		}
		if (k == InputConstants.KEY_ESCAPE) {
			if (!field.getValue().isEmpty()) {
				clear();
			} else {
				field.setFocused(false);
				host.setFocused(null);
			}
			return true;
		}
		if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) {
			if (!openSelected()) {
				model.submit();
			}
			return true;
		}
		if (k == InputConstants.KEY_UP || k == InputConstants.KEY_DOWN) {
			move(k == InputConstants.KEY_UP ? -1 : 1);
			return true;
		}
		if (focused) {
			field.keyPressed(e);
			return true;
		}
		return false;
	}

	/** Click on a result row: select it; a double click opens it. */
	public boolean mouseClicked(double x, double y, boolean doubleClick) {
		if (!active()) {
			return false;
		}
		for (Row r : List.copyOf(rows)) {
			if (r.contains(x, y)) {
				selected = r.index();
				if (doubleClick) {
					openSelected();
				}
				return true;
			}
		}
		return false;
	}

	public void move(int delta) {
		int n = hits().size();
		selected = n == 0 ? 0 : Math.max(0, Math.min(n - 1, selected + delta));
	}

	public ThreadSearch.@Nullable Hit selectedHit() {
		List<ThreadSearch.Hit> h = hits();
		return h.isEmpty() ? null : h.get(Math.min(selected, h.size() - 1));
	}

	/** Opens the selected result; false when there is none. */
	public boolean openSelected() {
		ThreadSearch.Hit h = selectedHit();
		if (h == null) {
			return false;
		}
		opener.open(h.env(), h.threadId());
		return true;
	}

	private List<ThreadSearch.Hit> hits() {
		ThreadSearch.Outcome o = model.outcome();
		return o == null ? List.of() : o.hits();
	}

	/** Draws the status and result list in the rectangle (panel background is the host's). */
	public void render(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		rows.clear();
		int w = x1 - x0;
		String status;
		int statusColor = DIM;
		ThreadSearch.Outcome o = model.outcome();
		switch (model.phase()) {
			case TOO_SHORT -> status = "Type at least " + dev.t3craft.t3.RpcFrames.SEARCH_MIN_LENGTH + " characters to search.";
			case PENDING, SEARCHING -> status = "Searching \"" + model.query() + "\"…";
			case FAILED -> {
				status = "Search failed: " + model.error();
				statusColor = ERROR;
			}
			case DONE -> {
				status = o == null ? "" : SearchLines.summary(o);
				if (o != null && o.noEnvironmentConnected()) {
					statusColor = WARN;
				}
			}
			default -> status = "";
		}
		int y = y0 + 3;
		y += wrap(g, status, x0 + 4, y, w - 8, 2, statusColor);
		if (o != null && !o.errors().isEmpty()) {
			y += wrap(g, "✖ " + String.join("; ", SearchLines.errors(o)), x0 + 4, y, w - 8, 2, ERROR);
		}
		List<ThreadSearch.Hit> hits = hits();
		if (hits.isEmpty()) {
			return;
		}
		int detailH = 34;
		int listBottom = y1 - detailH;
		int capacity = Math.max(1, (listBottom - y - 2) / ROW_H);
		selected = Math.min(selected, hits.size() - 1);
		if (selected < first) {
			first = selected;
		} else if (selected >= first + capacity) {
			first = selected - capacity + 1;
		}
		first = Math.max(0, Math.min(first, Math.max(0, hits.size() - capacity)));
		long now = System.currentTimeMillis();
		y += 2;
		for (int i = first; i < hits.size() && i < first + capacity; i++) {
			SearchLines.Line l = SearchLines.line(hits.get(i), now);
			if (i == selected) {
				g.fill(x0, y - 1, x1, y + ROW_H - 1, SELECTED);
			}
			g.text(font, l.glyph(), x0 + 4, y, 0xFF000000 | l.color());
			String right = l.envLabel() + " · " + l.state() + (l.age().isEmpty() ? "" : " · " + l.age());
			int rightW = Math.min(font.width(right), w / 2);
			String rightClip = font.plainSubstrByWidth(right, rightW);
			g.text(font, font.plainSubstrByWidth(l.title(), w - 26 - rightW), x0 + 16, y, TEXT);
			g.text(font, rightClip, x1 - 4 - font.width(rightClip), y, DIM);
			rows.add(new Row(x0, y - 1, x1, y + ROW_H - 1, i));
			y += ROW_H;
		}
		g.fill(x0, listBottom, x1, listBottom + 1, BORDER);
		ThreadSearch.Hit sel = selectedHit();
		if (sel != null) {
			SearchLines.Line l = SearchLines.line(sel, now);
			String detail = l.snippet() != null ? "“" + l.snippet() + "”" : (sel.project().isEmpty() ? "Title match." : sel.project() + " · title match.");
			wrap(g, detail, x0 + 4, listBottom + 4, w - 8, 2, l.snippet() != null ? TEXT : DIM);
			g.text(font, font.plainSubstrByWidth("Enter / double-click opens · Esc clears", w - 8), x0 + 4, y1 - 10, DIM);
		}
	}

	/** Draws wrapped text; returns the height used. */
	private int wrap(GuiGraphicsExtractor g, String text, int x, int y, int w, int maxLines, int color) {
		if (text.isEmpty()) {
			return 0;
		}
		List<FormattedCharSequence> split = font.split(Component.literal(text), Math.max(20, w));
		int n = Math.min(split.size(), maxLines);
		for (int i = 0; i < n; i++) {
			g.text(font, split.get(i), x, y + i * font.lineHeight, color);
		}
		return n * font.lineHeight + 2;
	}
}
