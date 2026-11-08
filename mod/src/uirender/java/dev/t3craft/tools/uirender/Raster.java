package dev.t3craft.tools.uirender;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Paints the {@link Ops} one frame recorded with Java2D, at the GUI scale asked for: one GUI pixel is
 * {@code scale} × {@code scale} image pixels, nearest-neighbour everywhere (like the game), glyph
 * pixels from the real font sheets ({@link JarFont}), sprites nine-sliced per their {@code .mcmeta}.
 *
 * <p>What it does not do (the game does): resource-pack overrides, the blurred background and
 * panorama, shaders, item and entity models, the exact edge pixels of rotated or fractionally scaled
 * text, and Unifont glyphs.
 */
final class Raster {
	/** Clear colour under the screen: what shows through a fully transparent gap (the game shows the world). */
	static final int BACKDROP = 0xFF0B0B0D;

	private record TintKey(Object source, int argb) {
	}

	private final int scale;
	private final JarFont font = JarFont.get();
	private final Map<TintKey, BufferedImage> tints = new HashMap<>();
	private final int guiW;
	private final int guiH;

	private Raster(int guiW, int guiH, int scale) {
		this.guiW = guiW;
		this.guiH = guiH;
		this.scale = scale;
	}

	/** Paints the frame; the result is {@code guiW*scale} × {@code guiH*scale} (callers crop to the window). */
	static BufferedImage paint(List<Ops.Op> ops, List<Ops.Tooltip> tooltips, int guiW, int guiH, int scale) {
		BufferedImage img = new BufferedImage(Math.max(1, guiW * scale), Math.max(1, guiH * scale), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
			g.setColor(new Color(BACKDROP, true));
			g.fillRect(0, 0, img.getWidth(), img.getHeight());
			Raster r = new Raster(guiW, guiH, scale);
			for (Ops.Op op : ops) {
				r.paint(g, op);
			}
			for (Ops.Op op : r.tooltipOps(tooltips)) {
				r.paint(g, op);
			}
		} finally {
			g.dispose();
		}
		return img;
	}

	private void paint(Graphics2D g, Ops.Op op) {
		clip(g, op.clip());
		switch (op) {
			case Ops.Fill f -> fill(g, f);
			case Ops.Gradient gr -> gradient(g, gr);
			case Ops.Text t -> text(g, t);
			case Ops.Sprite s -> sprite(g, s);
			case Ops.Texture t -> texture(g, t);
		}
		g.setClip(null);
	}

	private void clip(Graphics2D g, Ops.Clip c) {
		if (c == null) {
			g.setClip(null);
		} else {
			g.setClip(c.x0() * scale, c.y0() * scale, Math.max(0, c.x1() - c.x0()) * scale, Math.max(0, c.y1() - c.y0()) * scale);
		}
	}

	private int px(float guiCoord) {
		return Math.round(guiCoord * scale);
	}

	// ---- rectangles ------------------------------------------------------------------------------

	private void fill(Graphics2D g, Ops.Fill f) {
		g.setColor(new Color(f.argb(), true));
		g.fillRect(px(f.x0()), px(f.y0()), Math.max(0, px(f.x1()) - px(f.x0())), Math.max(0, px(f.y1()) - px(f.y0())));
	}

	private void gradient(Graphics2D g, Ops.Gradient gr) {
		int x0 = px(gr.x0());
		int y0 = px(gr.y0());
		int x1 = px(gr.x1());
		int y1 = px(gr.y1());
		if (y1 <= y0 || x1 <= x0) {
			return;
		}
		g.setPaint(new GradientPaint(x0, y0, new Color(gr.top(), true), x0, y1, new Color(gr.bottom(), true)));
		g.fillRect(x0, y0, x1 - x0, y1 - y0);
		g.setPaint(null);
	}

	// ---- text ------------------------------------------------------------------------------------

	private static int withAlpha(int argb, float mul) {
		int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, mul)));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	private static int shadowOf(int argb) {
		return (argb & 0xFF000000) | ((argb >> 2) & 0x3F3F3F);
	}

	private void text(Graphics2D g, Ops.Text t) {
		for (int pass = t.shadow() ? 0 : 1; pass < 2; pass++) {
			boolean shadowPass = pass == 0;
			float cx = t.x() + (shadowPass ? t.scale() : 0f);
			float cy = t.y() + (shadowPass ? t.scale() : 0f);
			for (Ops.G gl : t.glyphs()) {
				int col = gl.rgb() >= 0 ? (t.argb() & 0xFF000000) | gl.rgb() : t.argb();
				col = withAlpha(col, t.opacity());
				if (shadowPass) {
					col = shadowOf(col);
				}
				float adv = font.advance(gl.cp(), false) * t.scale();
				glyph(g, gl, cx, cy, col, t.scale());
				if (gl.bold()) {
					glyph(g, gl, cx + t.scale(), cy, col, t.scale());
				}
				float full = font.advance(gl.cp(), gl.bold()) * t.scale();
				if (gl.underline()) {
					rect(g, cx, cy + 8 * t.scale(), full, t.scale(), col);
				}
				if (gl.strike()) {
					rect(g, cx, cy + 4 * t.scale(), full, t.scale(), col);
				}
				cx += gl.bold() ? adv + t.scale() : adv;
			}
		}
	}

	private void rect(Graphics2D g, float x, float y, float w, float h, int argb) {
		g.setColor(new Color(argb, true));
		g.fillRect(px(x), px(y), Math.max(1, px(x + w) - px(x)), Math.max(1, px(y + h) - px(y)));
	}

	private void glyph(Graphics2D g, Ops.G gl, float x, float y, int argb, float poseScale) {
		if ((argb >>> 24) == 0) {
			return;
		}
		JarFont.Glyph bm = font.glyph(gl.cp());
		if (bm == null) {
			if (!font.has(gl.cp())) {
				missing(g, x, y, argb, poseScale);
			}
			return;
		}
		BufferedImage tinted = tints.computeIfAbsent(new TintKey(bm, argb), k -> tint(bm, argb));
		float gs = bm.scale * poseScale;
		int dx = px(x);
		int dy = px(y + (7 - bm.ascent) * poseScale);
		int dw = Math.max(1, Math.round(bm.cellW * gs * scale));
		int dh = Math.max(1, Math.round(bm.cellH * gs * scale));
		if (gl.italic()) {
			AffineTransform old = g.getTransform();
			g.translate(dx, dy + dh);
			g.shear(-0.2, 0);
			g.drawImage(tinted, 0, -dh, dw, dh, null);
			g.setTransform(old);
		} else {
			g.drawImage(tinted, dx, dy, dw, dh, null);
		}
	}

	private static BufferedImage tint(JarFont.Glyph bm, int argb) {
		BufferedImage out = new BufferedImage(bm.cellW, bm.cellH, BufferedImage.TYPE_INT_ARGB);
		int colA = argb >>> 24;
		for (int yy = 0; yy < bm.cellH; yy++) {
			for (int xx = 0; xx < bm.cellW; xx++) {
				int a = bm.sheet.getRGB(bm.cellX + xx, bm.cellY + yy) >>> 24;
				if (a != 0) {
					out.setRGB(xx, yy, ((a * colA / 255) << 24) | (argb & 0xFFFFFF));
				}
			}
		}
		return out;
	}

	/** The game's missing-glyph look: a hollow box. */
	private void missing(Graphics2D g, float x, float y, int argb, float s) {
		rect(g, x, y, 5 * s, s, argb);
		rect(g, x, y + 7 * s, 5 * s, s, argb);
		rect(g, x, y + s, s, 6 * s, argb);
		rect(g, x + 4 * s, y + s, s, 6 * s, argb);
	}

	// ---- sprites and textures --------------------------------------------------------------------

	private BufferedImage multiply(BufferedImage img, int argb) {
		if (argb == -1 || argb == 0xFFFFFFFF) {
			return img;
		}
		return tints.computeIfAbsent(new TintKey(img, argb), k -> {
			BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
			int ca = argb >>> 24;
			int cr = (argb >> 16) & 0xFF;
			int cg = (argb >> 8) & 0xFF;
			int cb = argb & 0xFF;
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					int p = img.getRGB(x, y);
					int a = (p >>> 24) * ca / 255;
					int r = ((p >> 16) & 0xFF) * cr / 255;
					int gg = ((p >> 8) & 0xFF) * cg / 255;
					int b = (p & 0xFF) * cb / 255;
					out.setRGB(x, y, (a << 24) | (r << 16) | (gg << 8) | b);
				}
			}
			return out;
		});
	}

	private void sprite(Graphics2D g, Ops.Sprite s) {
		Assets.SpriteImage si = Assets.sprite(s.id());
		float x = s.x();
		float y = s.y();
		float w = s.w();
		float h = s.h();
		if (si == null) {
			warn(g, x, y, w, h);
			return;
		}
		BufferedImage img = multiply(si.image(), s.argb());
		Assets.Scaling sc = si.scaling();
		switch (sc.type()) {
			case "nine_slice" -> nineSlice(g, img, sc, x, y, w, h);
			case "tile" -> tile(g, img, 0, 0, img.getWidth(), img.getHeight(), Math.max(1, sc.width()), Math.max(1, sc.height()), x, y, w, h);
			default -> g.drawImage(img, px(x), px(y), px(x + w) - px(x), px(y + h) - px(y), null);
		}
	}

	/** Nine slice: corners 1:1, edges and middle tiled (or stretched with {@code stretch_inner}). */
	private void nineSlice(Graphics2D g, BufferedImage img, Assets.Scaling sc, float x, float y, float w, float h) {
		int iw = img.getWidth();
		int ih = img.getHeight();
		int l = Math.min(sc.left(), (int) (w / 2));
		int r = Math.min(sc.right(), (int) (w / 2));
		int t = Math.min(sc.top(), (int) (h / 2));
		int b = Math.min(sc.bottom(), (int) (h / 2));
		int midSrcW = iw - sc.left() - sc.right();
		int midSrcH = ih - sc.top() - sc.bottom();
		float midW = w - l - r;
		float midH = h - t - b;
		// corners
		piece(g, img, 0, 0, sc.left(), sc.top(), x, y, l, t);
		piece(g, img, iw - sc.right(), 0, sc.right(), sc.top(), x + w - r, y, r, t);
		piece(g, img, 0, ih - sc.bottom(), sc.left(), sc.bottom(), x, y + h - b, l, b);
		piece(g, img, iw - sc.right(), ih - sc.bottom(), sc.right(), sc.bottom(), x + w - r, y + h - b, r, b);
		// edges and middle
		edge(g, img, sc, sc.left(), 0, midSrcW, sc.top(), x + l, y, midW, t);
		edge(g, img, sc, sc.left(), ih - sc.bottom(), midSrcW, sc.bottom(), x + l, y + h - b, midW, b);
		edge(g, img, sc, 0, sc.top(), sc.left(), midSrcH, x, y + t, l, midH);
		edge(g, img, sc, iw - sc.right(), sc.top(), sc.right(), midSrcH, x + w - r, y + t, r, midH);
		edge(g, img, sc, sc.left(), sc.top(), midSrcW, midSrcH, x + l, y + t, midW, midH);
	}

	private void edge(Graphics2D g, BufferedImage img, Assets.Scaling sc, int sx, int sy, int sw, int sh, float dx, float dy, float dw,
			float dh) {
		if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) {
			return;
		}
		if (sc.stretchInner()) {
			piece(g, img, sx, sy, sw, sh, dx, dy, dw, dh);
		} else {
			tile(g, img, sx, sy, sw, sh, sw, sh, dx, dy, dw, dh);
		}
	}

	private void piece(Graphics2D g, BufferedImage img, int sx, int sy, int sw, int sh, float dx, float dy, float dw, float dh) {
		if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) {
			return;
		}
		g.drawImage(img, px(dx), px(dy), px(dx + dw), px(dy + dh), sx, sy, sx + sw, sy + sh, null);
	}

	/** Repeats the source piece in {@code tw}×{@code th} GUI-pixel tiles over the destination, clipped to it. */
	private void tile(Graphics2D g, BufferedImage img, int sx, int sy, int sw, int sh, int tw, int th, float dx, float dy, float dw,
			float dh) {
		java.awt.Shape old = g.getClip();
		java.awt.Rectangle dest = new java.awt.Rectangle(px(dx), px(dy), Math.max(0, px(dx + dw) - px(dx)), Math.max(0, px(dy + dh) - px(dy)));
		g.clip(dest);
		for (float yy = 0; yy < dh; yy += th) {
			for (float xx = 0; xx < dw; xx += tw) {
				g.drawImage(img, px(dx + xx), px(dy + yy), px(dx + xx + tw), px(dy + yy + th), sx, sy, sx + sw, sy + sh, null);
			}
		}
		g.setClip(old);
	}

	private void texture(Graphics2D g, Ops.Texture t) {
		BufferedImage img = Assets.texture(t.id());
		if (img == null) {
			warn(g, t.x(), t.y(), t.w(), t.h());
			return;
		}
		BufferedImage tinted = multiply(img, t.argb());
		double kx = t.texW() > 0 ? (double) img.getWidth() / t.texW() : 1.0;
		double ky = t.texH() > 0 ? (double) img.getHeight() / t.texH() : 1.0;
		int sx = (int) Math.round(t.u() * kx);
		int sy = (int) Math.round(t.v() * ky);
		int sw = (int) Math.round(t.rw() * kx);
		int sh = (int) Math.round(t.rh() * ky);
		g.drawImage(tinted, px(t.x()), px(t.y()), px(t.x() + t.w()), px(t.y() + t.h()), sx, sy, sx + sw, sy + sh, null);
	}

	/** A sprite or texture that could not be found: magenta frame, so the gap is visible. */
	private void warn(Graphics2D g, float x, float y, float w, float h) {
		g.setColor(new Color(0xFFFF00FF, true));
		g.drawRect(px(x), px(y), Math.max(1, px(x + w) - px(x) - 1), Math.max(1, px(y + h) - px(y) - 1));
	}

	// ---- tooltips --------------------------------------------------------------------------------

	/** Vanilla tooltip look: dark purple box, violet border, 4 px padding, 12 px right and up of the pointer. */
	private List<Ops.Op> tooltipOps(List<Ops.Tooltip> tips) {
		List<Ops.Op> out = new ArrayList<>();
		for (Ops.Tooltip tip : tips) {
			float w = 0;
			for (List<Ops.G> line : tip.lines()) {
				w = Math.max(w, RecordingGraphics.width(line));
			}
			int h = tip.lines().size() * 10 - 1;
			int x = tip.x() + 12;
			int y = tip.y() - 12;
			if (x + w + 4 > guiW) {
				x = Math.max(4, tip.x() - 16 - Math.round(w));
			}
			if (y + h + 6 > guiH) {
				y = Math.max(4, guiH - h - 6);
			}
			int iw = Math.round(w);
			out.add(new Ops.Fill(x - 3, y - 4, x + iw + 3, y + h + 4, 0xF0100010, null));
			out.add(new Ops.Fill(x - 3, y - 4, x + iw + 3, y - 3, 0x505000FF, null));
			out.add(new Ops.Fill(x - 3, y + h + 3, x + iw + 3, y + h + 4, 0x505000FF, null));
			out.add(new Ops.Fill(x - 3, y - 3, x - 2, y + h + 3, 0x505000FF, null));
			out.add(new Ops.Fill(x + iw + 2, y - 3, x + iw + 3, y + h + 3, 0x505000FF, null));
			int ly = y;
			for (List<Ops.G> line : tip.lines()) {
				out.add(new Ops.Text(line, x, ly, 0xFFFFFFFF, true, 1f, 1f, null));
				ly += 10;
			}
		}
		return out;
	}
}
