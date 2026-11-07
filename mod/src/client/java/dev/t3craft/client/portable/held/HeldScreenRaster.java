package dev.t3craft.client.portable.held;

import dev.t3craft.client.portable.held.HeldScreenModel.Kind;
import dev.t3craft.client.portable.held.HeldScreenModel.Row;
import dev.t3craft.client.portable.held.HeldScreenModel.Span;
import java.util.List;

/**
 * Rasterises {@link HeldScreenModel} rows into ARGB pixels on the CPU (pure, tested). The size
 * matches the screen of the item sprite (6×9 px, so 2:3): {@value #WIDTH}×{@value #HEIGHT}, i.e.
 * 16 texels per sprite pixel. Portable look: dark panel, teal frame, header band, highlighted top
 * thread, chips for environments.
 */
public final class HeldScreenRaster {
	public static final int WIDTH = 96;
	public static final int HEIGHT = 144;
	/** Frame (1 px) + inner margin (2 px). */
	public static final int PAD = 3;
	public static final int HEADER_H = 11;
	public static final int ROW_H = BitmapFont.LINE_H;
	public static final int RULE_H = 4;

	private HeldScreenRaster() {
	}

	/** Text area width the model must lay out for. */
	public static int textWidth() {
		return WIDTH - 2 * PAD;
	}

	/** Rows that fit: header + body rows (a rule is shorter than a row, so this is a safe bound). */
	public static int maxRows() {
		return 1 + (HEIGHT - 2 * PAD - HEADER_H) / ROW_H;
	}

	public static int[] render(List<Row> rows, BitmapFont font) {
		int[] px = new int[WIDTH * HEIGHT];
		fill(px, 0, 0, WIDTH, HEIGHT, HeldScreenModel.BG);
		// frame
		fill(px, 0, 0, WIDTH, 1, HeldScreenModel.BORDER);
		fill(px, 0, HEIGHT - 1, WIDTH, 1, HeldScreenModel.BORDER);
		fill(px, 0, 0, 1, HEIGHT, HeldScreenModel.BORDER);
		fill(px, WIDTH - 1, 0, 1, HEIGHT, HeldScreenModel.BORDER);

		int maxX = WIDTH - PAD - 1;
		int y = 1;
		for (Row r : rows) {
			int h = switch (r.kind()) {
				case HEADER -> HEADER_H;
				case RULE -> RULE_H;
				case TEXT -> ROW_H;
			};
			if (y + h > HEIGHT - 1) {
				break;
			}
			if (r.kind() == Kind.HEADER) {
				// full-width band under the top frame line
				fill(px, 1, y, WIDTH - 2, h, r.bg() != 0 ? r.bg() : HeldScreenModel.HEADER_BG);
				fill(px, 1, y + h - 1, WIDTH - 2, 1, HeldScreenModel.BORDER);
				drawSpans(px, font, r.spans(), PAD, y + 2, maxX);
				y += h + PAD - 1;
				continue;
			}
			if (r.kind() == Kind.RULE) {
				fill(px, PAD, y + h / 2 - 1, WIDTH - 2 * PAD, 1, HeldScreenModel.BORDER);
				y += h;
				continue;
			}
			if (r.bg() != 0) {
				fill(px, 1, y, WIDTH - 2, h, r.bg());
			}
			drawSpans(px, font, r.spans(), PAD, y + 1, maxX);
			y += h;
		}
		return px;
	}

	private static void drawSpans(int[] px, BitmapFont font, List<Span> spans, int x, int y, int maxX) {
		for (Span s : spans) {
			if (x > maxX) {
				return;
			}
			if (s.bg() != 0) {
				int w = font.width(s.text()) + HeldScreenModel.CHIP_PAD - 1; // last glyph column is spacing
				fill(px, x, y - 1, Math.min(w, maxX + 1 - x), ROW_H - 1, s.bg());
				font.draw(px, WIDTH, HEIGHT, x + 1, y, s.text(), s.color(), maxX);
				x += w + 1;
			} else {
				x = font.draw(px, WIDTH, HEIGHT, x, y, s.text(), s.color(), maxX);
			}
		}
	}

	static void fill(int[] px, int x, int y, int w, int h, int rgb) {
		int c = 0xFF000000 | rgb;
		for (int yy = Math.max(0, y); yy < Math.min(HEIGHT, y + h); yy++) {
			for (int xx = Math.max(0, x); xx < Math.min(WIDTH, x + w); xx++) {
				px[yy * WIDTH + xx] = c;
			}
		}
	}
}
