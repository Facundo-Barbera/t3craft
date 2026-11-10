// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
// Kit drawing (panel, inset, pill, keycap hints, text field, 6 px scrollbar, status dots with the
// waiting pulse) follows AgentCraft's ui/Panels.java and hud/UiBits.java; the sprites are
// AgentCraft's kit, copied to assets/t3craft/textures/gui/sprites/chat/.
package dev.t3craft.client.chat;

import dev.t3craft.client.t3ui.kit.T3Icon;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Drawing helpers shared by the chat components. With a theme that has {@link ChatTheme#sprites()}
 * they draw AgentCraft's nine-slice kit sprites; without, the same shapes as flat fills of the
 * theme's colours (the portable terminal look). All coordinates are GUI pixels.
 */
public final class ChatDraw {
	private ChatDraw() {
	}

	private static Identifier id(ChatTheme t, String name) {
		return Identifier.parse(t.sprites() + name);
	}

	private static void sprite(GuiGraphicsExtractor g, ChatTheme t, String name, int x, int y, int w, int h) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, id(t, name), x, y, w, h);
	}

	private static void sprite(GuiGraphicsExtractor g, ChatTheme t, String name, int x, int y, int w, int h, int argb) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, id(t, name), x, y, w, h, argb);
	}

	/** Paper window (AgentCraft panel_paper, soft shadow included). */
	public static void panel(GuiGraphicsExtractor g, ChatTheme t, Rect r) {
		if (t.hasSprites()) {
			sprite(g, t, "panel_paper", r.x(), r.y(), r.w(), r.h());
		} else {
			g.fill(r.x(), r.y(), r.right(), r.bottom(), t.panel());
			outline(g, r.x(), r.y(), r.w(), r.h(), t.edge());
		}
	}

	/** Recessed well (panel_inset). */
	public static void inset(GuiGraphicsExtractor g, ChatTheme t, Rect r) {
		if (t.hasSprites()) {
			sprite(g, t, "panel_inset", r.x(), r.y(), r.w(), r.h());
		} else {
			g.fill(r.x(), r.y(), r.right(), r.bottom(), t.inset());
		}
	}

	/** Text field box (text_field / text_field_focused). */
	public static void field(GuiGraphicsExtractor g, ChatTheme t, Rect r, boolean focused) {
		if (t.hasSprites()) {
			sprite(g, t, focused ? "text_field_focused" : "text_field", r.x(), r.y(), r.w(), r.h());
		} else {
			g.fill(r.x(), r.y(), r.right(), r.bottom(), t.inset());
			outline(g, r.x(), r.y(), r.w(), r.h(), focused ? t.edge() : t.disabled());
		}
	}

	/** Speech bubble body (AgentCraft's in-world bubble sprite) used for "needs you" callouts. */
	public static void bubble(GuiGraphicsExtractor g, ChatTheme t, Rect r, int fallback) {
		if (t.hasSprites()) {
			sprite(g, t, "bubble", r.x(), r.y(), r.w(), r.h());
		} else {
			round(g, r.x(), r.y(), r.w(), r.h(), fallback);
		}
	}

	/** 11 px pill (chips, "newer below"). Returns its width. */
	public static int pill(GuiGraphicsExtractor g, Font font, ChatTheme t, String s, int x, int y, int textArgb) {
		int w = font.width(s) + 10;
		pillBox(g, t, x, y, w, false);
		g.text(font, s, x + 5, y + 2, textArgb, false);
		return w;
	}

	public static void pillBox(GuiGraphicsExtractor g, ChatTheme t, int x, int y, int w, boolean hover) {
		if (t.hasSprites()) {
			sprite(g, t, "pill", x, y, w, 11, hover ? 0xFFF0E8DA : 0xFFFFFFFF);
		} else {
			round(g, x, y, w, 11, hover ? t.hover() : t.selected());
		}
	}

	/** A 12 px key cap with its label; returns its width. */
	public static int keycap(GuiGraphicsExtractor g, Font font, ChatTheme t, String key, int x, int y) {
		int w = keycapWidth(font, key);
		if (t.hasSprites()) {
			sprite(g, t, "keycap", x, y, w, 12);
		} else {
			round(g, x, y, w, 11, t.selected());
			g.fill(x + 1, y + 11, x + w - 1, y + 12, t.edge());
		}
		g.text(font, key, x + (w - font.width(key)) / 2, y + 2, t.keycapText(), false);
		return w;
	}

	/**
	 * A 12 px key cap holding an icon (the header's previous / next): muted at rest, accent under the pointer.
	 * Returns its width.
	 */
	public static int keycap(GuiGraphicsExtractor g, ChatTheme t, T3Icon icon, int x, int y, boolean hover) {
		int w = 12;
		if (t.hasSprites()) {
			sprite(g, t, "keycap", x, y, w, 12);
		} else {
			round(g, x, y, w, 11, t.selected());
			g.fill(x + 1, y + 11, x + w - 1, y + 12, t.edge());
		}
		T3Icon.draw(g, t, icon, x + 2, y + 1, 8, T3Icon.tint(t, hover, true));
		return w;
	}

	public static int keycapWidth(Font font, String key) {
		return Math.max(12, font.width(key) + 8);
	}

	/** A row of "[key] verb" hints (AgentCraft footer); returns the width used. */
	public static int hints(GuiGraphicsExtractor g, Font font, ChatTheme t, int x, int y, String... keyThenVerb) {
		int cx = x;
		for (int i = 0; i + 1 < keyThenVerb.length; i += 2) {
			cx += keycap(g, font, t, keyThenVerb[i], cx, y) + 3;
			g.text(font, keyThenVerb[i + 1], cx, y + 2, t.muted(), false);
			cx += font.width(keyThenVerb[i + 1]) + 9;
		}
		return Math.max(0, cx - x - 9);
	}

	public static int hintsWidth(Font font, String... keyThenVerb) {
		int w = 0;
		for (int i = 0; i + 1 < keyThenVerb.length; i += 2) {
			w += keycapWidth(font, keyThenVerb[i]) + 3 + font.width(keyThenVerb[i + 1]) + 9;
		}
		return Math.max(0, w - 9);
	}

	/** 6 px scrollbar (track, thumb, grip); nothing when everything fits. */
	public static void scrollbar(GuiGraphicsExtractor g, ChatTheme t, int x, int y, int h, int content, int view, double fraction, boolean hover) {
		if (content <= view || h < 8) {
			return;
		}
		int thumbH = Math.max(8, (int) Math.round((double) h * view / content));
		int ty = y + (int) Math.round((h - thumbH) * Math.max(0, Math.min(1, fraction)));
		if (t.hasSprites()) {
			sprite(g, t, "scroll_track", x, y, 6, h);
			sprite(g, t, hover ? "scroll_thumb_hover" : "scroll_thumb", x, ty, 6, thumbH);
			if (thumbH >= 10) {
				sprite(g, t, "scroll_grip", x + 2, ty + thumbH / 2 - 1, 2, 3);
			}
		} else {
			g.fill(x + 2, y, x + 4, y + h, t.inset());
			g.fill(x + 1, ty, x + 5, ty + thumbH, hover ? t.accent() : t.edge());
		}
	}

	/** 7 px status dot of an AgentCraft family (idle thinking working waiting error done). */
	public static void dot(GuiGraphicsExtractor g, ChatTheme t, String family, int color, int x, int y) {
		if (t.hasSprites()) {
			sprite(g, t, "dot_" + family, x, y, 7, 7);
		} else {
			g.fill(x + 1, y, x + 6, y + 7, color);
			g.fill(x, y + 1, x + 7, y + 6, color);
		}
	}

	/** Dot with the waiting pulse (halo alpha 40..150 over 1200 ms, ease in-out). */
	public static void pulsingDot(GuiGraphicsExtractor g, ChatTheme t, String family, int color, int x, int y) {
		float p = pulse();
		if (t.hasSprites()) {
			sprite(g, t, "dot_" + family + "_halo", x - 2, y - 2, 11, 11, ((int) (110 * p + 40) << 24) | 0xFFFFFF);
		} else {
			round(g, x - 2, y - 2, 11, 11, ChatTheme.withAlpha(color, (int) (90 * p)));
		}
		dot(g, t, family, color, x, y);
	}

	/** 0..1 ease-in-out pulse, 1200 ms period (AgentCraft metrics.pulse_ms). */
	public static float pulse() {
		double ph = (System.currentTimeMillis() % 1200) / 1200.0;
		return (float) (0.5 - 0.5 * Math.cos(ph * Math.PI * 2));
	}

	/** Caret blink (AgentCraft metrics.caret_blink_ms = 500). */
	public static boolean caretOn() {
		return (System.currentTimeMillis() / 500) % 2 == 0;
	}

	/**
	 * 12 px tool icon (bash decision edit git memory merge message read test): AgentCraft's {@code icon_*} sprite with
	 * the kit sprites, else the {@link T3Icon} of {@link #toolIcon(String)} in the tool colour.
	 */
	public static void toolIcon(GuiGraphicsExtractor g, Font font, ChatTheme t, String icon, int x, int y) {
		if (t.hasSprites()) {
			sprite(g, t, "icon_" + icon, x, y, 12, 12);
		} else {
			T3Icon.draw(g, t, toolIcon(icon), x, y, 12, t.tool());
		}
	}

	/** The {@link T3Icon} of a tool kind ({@link ToolLine#icon()}); a shell command for anything unknown. */
	public static T3Icon toolIcon(String icon) {
		return switch (icon) {
			case "edit" -> T3Icon.PENCIL;
			case "read" -> T3Icon.SEARCH;
			case "git", "merge" -> T3Icon.GIT_BRANCH;
			case "test" -> T3Icon.CHECK;
			case "message" -> T3Icon.MESSAGE_SQUARE;
			case "decision" -> T3Icon.INFO;
			case "memory" -> T3Icon.FILE_TEXT;
			default -> T3Icon.TERMINAL;
		};
	}

	/**
	 * The {@link T3Icon} of an attention level, for drawing on screen. {@link Attention#glyph} stays the text form (it goes
	 * into strings shown elsewhere: monitors, holograms, the action bar).
	 */
	public static T3Icon stateIcon(Attention a) {
		return switch (a) {
			case NEEDS_YOU -> T3Icon.BELL;
			case ERROR -> T3Icon.CIRCLE_X;
			case PENDING -> T3Icon.CIRCLE_EXCLAMATION_POINT;
			case WORKING -> T3Icon.REFRESH_CW;
			case DONE -> T3Icon.CIRCLE_CHECK;
			case IDLE -> T3Icon.MINUS;
		};
	}

	/** Square environment icon with its initial (T3 Code sidebar style). Returns its width. */
	public static int envIcon(GuiGraphicsExtractor g, Font font, EnvBadge b, int x, int y, int size) {
		round(g, x, y, size, size, b.background());
		int tw = font.width(b.initial());
		g.text(font, b.initial(), x + (size - tw + 1) / 2, y + (size - 8) / 2, b.foreground(), false);
		return size;
	}

	/** Filled box with the corner pixels cut (reads as rounded at GUI scale). */
	public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		if (w <= 2 || h <= 2) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}
		g.fill(x + 1, y, x + w - 1, y + h, color);
		g.fill(x, y + 1, x + 1, y + h - 1, color);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y + 1, x + 1, y + h - 1, color);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	/** Centred "── 14:05 ──" separator (AgentCraft console). */
	public static void separator(GuiGraphicsExtractor g, Font font, ChatTheme t, String label, int x0, int x1, int y) {
		int tw = font.width(label);
		int mid = (x0 + x1) / 2;
		g.fill(x0 + 2, y + 4, mid - tw / 2 - 5, y + 5, t.edge());
		g.fill(mid + tw / 2 + 5, y + 4, x1 - 2, y + 5, t.edge());
		g.text(font, label, mid - tw / 2, y, t.disabled(), false);
	}

	public static String clip(Font font, String s, int w) {
		if (w <= 0) {
			return "";
		}
		if (font.width(s) <= w) {
			return s;
		}
		return font.plainSubstrByWidth(s, Math.max(0, w - font.width("…"))) + "…";
	}
}
