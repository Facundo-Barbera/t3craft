package dev.t3craft.tools.uirender;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * A {@link GuiGraphicsExtractor} that, instead of building the game's GPU render state, records what
 * the screens ask for ({@link Ops}) so {@link Raster} can paint it with Java2D. Screens, widgets and
 * our own drawing helpers only see the extractor's public API, so every call they make lands here.
 *
 * <p>An instance is made with {@code Unsafe.allocateInstance} (see {@link Headless}): the real
 * constructors need a live {@code Minecraft} (atlas manager, window). That is why there are no field
 * initialisers: {@link #begin} sets everything. Methods not overridden here would touch the
 * (absent) game and fail loudly: items, entities, maps, skins, books, banners and the profiler chart
 * are not drawn by our screens. Everything else the T3 screens and vanilla widgets call is covered.
 */
final class RecordingGraphics extends GuiGraphicsExtractor {
	private List<Ops.Op> ops;
	private List<Ops.Tooltip> tooltips;
	private Matrix3x2fStack pose;
	private List<Ops.Clip> scissors;
	private Font font;
	private int guiW;
	private int guiH;
	private ActiveTextCollector.Parameters collectorParams;
	private ActiveTextCollector collector;

	/** Never run: instances come from {@code allocateInstance}. */
	private RecordingGraphics() {
		super(null, null, 0, 0);
	}

	void begin(int guiWidth, int guiHeight, Font font) {
		this.ops = new ArrayList<>();
		this.tooltips = new ArrayList<>();
		this.pose = new Matrix3x2fStack(32);
		this.scissors = new ArrayList<>();
		this.font = font;
		this.guiW = guiWidth;
		this.guiH = guiHeight;
		this.collectorParams = new ActiveTextCollector.Parameters(pose);
		this.collector = newCollector();
	}

	List<Ops.Op> ops() {
		return ops;
	}

	List<Ops.Tooltip> tooltips() {
		return tooltips;
	}

	// ---- state -----------------------------------------------------------------------------------

	@Override
	public int guiWidth() {
		return guiW;
	}

	@Override
	public int guiHeight() {
		return guiH;
	}

	@Override
	public Matrix3x2fStack pose() {
		return pose;
	}

	@Override
	public void nextStratum() {
	}

	@Override
	public void blurBeforeThisStratum() {
	}

	@Override
	public void extractDeferredElements(int mouseX, int mouseY, float partialTick) {
	}

	private Ops.@Nullable Clip clip() {
		return scissors.isEmpty() ? null : scissors.get(scissors.size() - 1);
	}

	@Override
	public void enableScissor(int x0, int y0, int x1, int y1) {
		Ops.Clip c = new Ops.Clip(Math.round(tx(pose, x0, y0)), Math.round(ty(pose, x0, y0)), Math.round(tx(pose, x1, y1)),
				Math.round(ty(pose, x1, y1)));
		Ops.Clip top = clip();
		scissors.add(top == null ? c : top.intersect(c));
	}

	@Override
	public void disableScissor() {
		if (!scissors.isEmpty()) {
			scissors.remove(scissors.size() - 1);
		}
	}

	@Override
	public boolean containsPointInScissor(int x, int y) {
		Ops.Clip c = clip();
		return c == null || c.contains(x, y);
	}

	private static float tx(Matrix3x2fc m, float x, float y) {
		return m.m00() * x + m.m10() * y + m.m20();
	}

	private static float ty(Matrix3x2fc m, float x, float y) {
		return m.m01() * x + m.m11() * y + m.m21();
	}

	// ---- rectangles ------------------------------------------------------------------------------

	private void rect(float x0, float y0, float x1, float y1, int argb) {
		if ((argb >>> 24) == 0) {
			return;
		}
		float ax = tx(pose, x0, y0);
		float ay = ty(pose, x0, y0);
		float bx = tx(pose, x1, y1);
		float by = ty(pose, x1, y1);
		ops.add(new Ops.Fill(Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx), Math.max(ay, by), argb, clip()));
	}

	@Override
	public void fill(int x0, int y0, int x1, int y1, int color) {
		rect(x0, y0, x1, y1, color);
	}

	@Override
	public void fill(RenderPipeline pipeline, int x0, int y0, int x1, int y1, int color) {
		rect(x0, y0, x1, y1, color);
	}

	@Override
	public void fill(RenderPipeline pipeline, TextureSetup textures, int x0, int y0, int x1, int y1) {
		// textured fills (the panorama, the loading overlay) are not part of our screens
	}

	@Override
	public void fillGradient(int x0, int y0, int x1, int y1, int topColor, int bottomColor) {
		float ax = tx(pose, x0, y0);
		float ay = ty(pose, x0, y0);
		float bx = tx(pose, x1, y1);
		float by = ty(pose, x1, y1);
		ops.add(new Ops.Gradient(Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx), Math.max(ay, by), topColor, bottomColor, clip()));
	}

	@Override
	public void horizontalLine(int x0, int x1, int y, int color) {
		rect(Math.min(x0, x1), y, Math.max(x0, x1) + 1, y + 1, color);
	}

	@Override
	public void verticalLine(int x, int y0, int y1, int color) {
		rect(x, Math.min(y0, y1) + 1, x + 1, Math.max(y0, y1), color);
	}

	@Override
	public void outline(int x, int y, int w, int h, int color) {
		rect(x, y, x + w, y + 1, color);
		rect(x, y + h - 1, x + w, y + h, color);
		rect(x, y + 1, x + 1, y + h - 1, color);
		rect(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	@Override
	public void textHighlight(int x0, int y0, int x1, int y1, boolean invert) {
		rect(x0, y0, x1, y1, 0xA03355FF);
	}

	// ---- text ------------------------------------------------------------------------------------

	static List<Ops.G> glyphs(FormattedCharSequence seq) {
		List<Ops.G> out = new ArrayList<>();
		seq.accept((index, style, cp) -> {
			TextColor c = style.getColor();
			out.add(new Ops.G(cp, c == null ? -1 : c.getValue() & 0xFFFFFF, style.isBold(), style.isItalic(), style.isUnderlined(),
					style.isStrikethrough()));
			return true;
		});
		return out;
	}

	static String plain(List<Ops.G> glyphs) {
		StringBuilder b = new StringBuilder();
		for (Ops.G g : glyphs) {
			b.appendCodePoint(g.cp());
		}
		return b.toString();
	}

	static float width(List<Ops.G> glyphs) {
		JarFont f = JarFont.get();
		float w = 0;
		for (Ops.G g : glyphs) {
			w += f.advance(g.cp(), g.bold());
		}
		return w;
	}

	private static FormattedCharSequence seq(String s) {
		return Language.getInstance().getVisualOrder(FormattedText.of(s));
	}

	private void draw(Matrix3x2fc m, FormattedCharSequence seq, float x, float y, int argb, boolean shadow, float opacity) {
		if ((argb >>> 24) == 0 || seq == null) {
			return;
		}
		List<Ops.G> g = glyphs(seq);
		if (g.isEmpty()) {
			return;
		}
		ops.add(new Ops.Text(g, tx(m, x, y), ty(m, x, y), argb, shadow, m.m00(), opacity, clip()));
	}

	private void draw(FormattedCharSequence seq, int x, int y, int argb, boolean shadow) {
		draw(pose, seq, x, y, argb, shadow, 1f);
	}

	@Override
	public void text(Font font, String text, int x, int y, int color) {
		text(font, text, x, y, color, true);
	}

	@Override
	public void text(Font font, String text, int x, int y, int color, boolean shadow) {
		if (text != null) {
			draw(seq(text), x, y, color, shadow);
		}
	}

	@Override
	public void text(Font font, FormattedCharSequence text, int x, int y, int color) {
		draw(text, x, y, color, true);
	}

	@Override
	public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
		draw(text, x, y, color, shadow);
	}

	@Override
	public void text(Font font, Component text, int x, int y, int color) {
		draw(text.getVisualOrderText(), x, y, color, true);
	}

	@Override
	public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
		draw(text.getVisualOrderText(), x, y, color, shadow);
	}

	@Override
	public void centeredText(Font font, String text, int cx, int y, int color) {
		FormattedCharSequence s = seq(text);
		draw(s, cx - font.width(s) / 2, y, color, true);
	}

	@Override
	public void centeredText(Font font, Component text, int cx, int y, int color) {
		FormattedCharSequence s = text.getVisualOrderText();
		draw(s, cx - font.width(s) / 2, y, color, true);
	}

	@Override
	public void centeredText(Font font, FormattedCharSequence text, int cx, int y, int color) {
		draw(text, cx - font.width(text) / 2, y, color, true);
	}

	@Override
	public int textWithWordWrap(Font font, FormattedText text, int x, int y, int width, int color) {
		return textWithWordWrap(font, text, x, y, width, color, true);
	}

	@Override
	public int textWithWordWrap(Font font, FormattedText text, int x, int y, int width, int color, boolean shadow) {
		int yy = y;
		for (FormattedCharSequence line : font.split(text, width)) {
			draw(line, x, yy, color, shadow);
			yy += font.lineHeight;
		}
		return yy;
	}

	@Override
	public void textWithBackdrop(Font font, Component text, int x, int y, int width, int color) {
		rect(x - 2, y - 2, x + width + 2, y + font.lineHeight + 2, 0x80000000);
		draw(text.getVisualOrderText(), x, y, color, true);
	}

	// ---- text collector (vanilla widgets: buttons, edit boxes, labels) -------------------------------

	private ActiveTextCollector newCollector() {
		return new ActiveTextCollector() {
			@Override
			public Parameters defaultParameters() {
				return collectorParams;
			}

			@Override
			public void defaultParameters(Parameters p) {
				collectorParams = p;
			}

			@Override
			public void accept(TextAlignment alignment, int x, int y, Parameters p, FormattedCharSequence text) {
				int left = alignment.calculateLeft(x, font, text);
				int alpha = Math.max(0, Math.min(255, Math.round(p.opacity() * 255f)));
				draw(p.pose(), text, left, y, (alpha << 24) | 0xFFFFFF, true, p.opacity());
			}

			@Override
			public void acceptScrolling(Component message, int centerX, int left, int right, int top, int bottom, Parameters p) {
				FormattedCharSequence s = message.getVisualOrderText();
				int w = font.width(s);
				int y = (top + bottom - font.lineHeight) / 2 + 1;
				int x = w <= right - left ? centerX - w / 2 : left;
				int alpha = Math.max(0, Math.min(255, Math.round(p.opacity() * 255f)));
				if (w > right - left) {
					enableScissor(left, top, right, bottom);
				}
				draw(p.pose(), s, x, y, (alpha << 24) | 0xFFFFFF, true, p.opacity());
				if (w > right - left) {
					disableScissor();
				}
			}
		};
	}

	@Override
	public ActiveTextCollector textRenderer() {
		return collector;
	}

	@Override
	public ActiveTextCollector textRenderer(GuiGraphicsExtractor.HoveredTextEffects effects) {
		return collector;
	}

	@Override
	public ActiveTextCollector textRenderer(GuiGraphicsExtractor.HoveredTextEffects effects, Consumer<Style> onHover) {
		return collector;
	}

	@Override
	public ActiveTextCollector textRendererForWidget(AbstractWidget widget, GuiGraphicsExtractor.HoveredTextEffects effects) {
		collectorParams = new ActiveTextCollector.Parameters(pose, widget.getAlpha(), collectorParams.scissor());
		return collector;
	}

	// ---- pictures --------------------------------------------------------------------------------

	private void sprite(Identifier id, int x, int y, int w, int h, int argb) {
		float ax = tx(pose, x, y);
		float ay = ty(pose, x, y);
		float bx = tx(pose, x + w, y + h);
		float by = ty(pose, x + w, y + h);
		ops.add(new Ops.Sprite(id.toString(), ax, ay, bx - ax, by - ay, argb, clip()));
	}

	private void texture(Identifier id, int x, int y, int w, int h, float u, float v, int rw, int rh, int texW, int texH, int argb) {
		float ax = tx(pose, x, y);
		float ay = ty(pose, x, y);
		float bx = tx(pose, x + w, y + h);
		float by = ty(pose, x + w, y + h);
		ops.add(new Ops.Texture(id.toString(), ax, ay, bx - ax, by - ay, u, v, rw, rh, texW, texH, argb, clip()));
	}

	@Override
	public void blitSprite(RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
		sprite(sprite, x, y, w, h, -1);
	}

	@Override
	public void blitSprite(RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, float alpha) {
		sprite(sprite, x, y, w, h, ((int) (alpha * 255f) << 24) | 0xFFFFFF);
	}

	@Override
	public void blitSprite(RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, int argb) {
		sprite(sprite, x, y, w, h, argb);
	}

	@Override
	public void blit(RenderPipeline pipeline, Identifier tex, int x, int y, float u, float v, int w, int h, int texW, int texH) {
		texture(tex, x, y, w, h, u, v, w, h, texW, texH, -1);
	}

	@Override
	public void blit(RenderPipeline pipeline, Identifier tex, int x, int y, float u, float v, int w, int h, int texW, int texH, int argb) {
		texture(tex, x, y, w, h, u, v, w, h, texW, texH, argb);
	}

	@Override
	public void blit(RenderPipeline pipeline, Identifier tex, int x, int y, float u, float v, int w, int h, int rw, int rh, int texW,
			int texH) {
		texture(tex, x, y, w, h, u, v, rw, rh, texW, texH, -1);
	}

	@Override
	public void blit(RenderPipeline pipeline, Identifier tex, int x, int y, float u, float v, int w, int h, int rw, int rh, int texW,
			int texH, int argb) {
		texture(tex, x, y, w, h, u, v, rw, rh, texW, texH, argb);
	}

	/** {@code blit(id, x0, y0, x1, y1, u0, u1, v0, v1)}: normalised UVs. */
	@Override
	public void blit(Identifier tex, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1) {
		java.awt.image.BufferedImage img = Assets.texture(tex.toString());
		int tw = img == null ? 64 : img.getWidth();
		int th = img == null ? 64 : img.getHeight();
		texture(tex, x0, y0, x1 - x0, y1 - y0, u0 * tw, v0 * th, Math.round((u1 - u0) * tw), Math.round((v1 - v0) * th), tw, th, -1);
	}

	// ---- tooltips (drawn by Raster last, over everything) ---------------------------------------------

	private void tip(List<FormattedCharSequence> lines, int x, int y) {
		List<List<Ops.G>> out = new ArrayList<>();
		for (FormattedCharSequence l : lines) {
			out.add(glyphs(l));
		}
		if (!out.isEmpty()) {
			tooltips.add(new Ops.Tooltip(out, Math.round(tx(pose, x, y)), Math.round(ty(pose, x, y))));
		}
	}

	@Override
	public void setTooltipForNextFrame(Component text, int x, int y) {
		tip(List.of(text.getVisualOrderText()), x, y);
	}

	@Override
	public void setTooltipForNextFrame(Font font, Component text, int x, int y) {
		tip(List.of(text.getVisualOrderText()), x, y);
	}

	@Override
	public void setTooltipForNextFrame(List<FormattedCharSequence> lines, int x, int y) {
		tip(lines, x, y);
	}

	@Override
	public void setTooltipForNextFrame(Font font, List<? extends FormattedCharSequence> lines, int x, int y) {
		tip(new ArrayList<FormattedCharSequence>(lines), x, y);
	}

	@Override
	public void setComponentTooltipForNextFrame(Font font, List<Component> lines, int x, int y) {
		List<FormattedCharSequence> out = new ArrayList<>();
		for (Component c : lines) {
			out.add(c.getVisualOrderText());
		}
		tip(out, x, y);
	}
}
