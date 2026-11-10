package dev.t3craft.client.blocks.config;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.t3ui.kit.T3Control;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The preview box of a block's config page: a small dark screen with a title row, an accent band (with a status
 * chip when there is one) and text rows, drawn with the theme's code colours. It shows what the block will
 * display, schematically (the real screen is the block's renderer). Not focusable; draws only.
 */
public final class BlockPreview extends T3Control {
	/** One text row in its colour (ARGB). */
	public record Line(String text, int color) {
	}

	/**
	 * What the preview shows.
	 *
	 * @param title  the header text
	 * @param chip   the status chip on the accent band ("WORKING - 3m"), or empty for a thin band
	 * @param accent the band's colour (ARGB)
	 * @param lines  the rows under the header (cut to what fits)
	 * @param scale  text size: 1 normal; below 1 shows more rows (small), above fewer (large)
	 */
	public record Model(String title, String chip, int accent, List<Line> lines, float scale) {
		public Model {
			title = title == null ? "" : title;
			chip = chip == null ? "" : chip;
			lines = lines == null ? List.of() : List.copyOf(lines);
			scale = scale <= 0 ? 1f : scale;
		}
	}

	private static final int ROW = 10;
	private static final int HEADER = 13;
	private static final int BAND = 11;

	private Model model;

	public BlockPreview(Model model) {
		this.model = model;
	}

	public void set(Model m) {
		model = m;
	}

	/** Height that shows {@code rows} rows. */
	public static int height(int rows) {
		return HEADER + BAND + rows * ROW + 6;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
		if (!visible()) {
			return;
		}
		Rect r = bounds();
		g.fill(r.x(), r.y(), r.right(), r.bottom(), t.edge());
		g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1, t.codeBg());
		int x = r.x() + 5;
		int w = r.w() - 10;
		g.text(font, ChatDraw.clip(font, model.title(), w), x, r.y() + 3, t.codeText(), false);
		int y = r.y() + HEADER;
		if (model.chip().isEmpty()) {
			g.fill(r.x() + 1, y, r.right() - 1, y + 2, model.accent());
			y += 4;
		} else {
			g.fill(r.x() + 1, y, r.right() - 1, y + BAND, model.accent());
			g.text(font, ChatDraw.clip(font, model.chip(), w), x, y + 2, t.codeBg(), false);
			y += BAND + 2;
		}
		// text size: the real screen packs more (small) or fewer (large) rows into the same glass
		int row = Math.max(8, Math.round(ROW * model.scale()));
		for (Line l : model.lines()) {
			if (y + row > r.bottom() - 2) {
				break;
			}
			g.text(font, ChatDraw.clip(font, l.text(), w), x, y + Math.max(0, (row - 8) / 2), l.color(), false);
			y += row;
		}
	}
}
