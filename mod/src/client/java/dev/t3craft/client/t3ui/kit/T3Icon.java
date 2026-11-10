package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatTheme;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The icons of the UI kit (issue #24, docs/tools/icons.md). Each constant is a Lucide SVG in
 * {@code tools/art/icons/lucide/<id>.svg} (the id is the constant in kebab-case), rasterised at build time
 * by {@code generateIcons} into the white-on-transparent GUI sprite {@code t3craft:icons/<id>}. The sprite
 * is white so that the draw call tints it with a theme colour: one picture, every theme and state.
 *
 * <p>The GUI atlas picks the sprites up by their folder ({@code textures/gui/sprites/icons/}), no atlas
 * file is needed. To add an icon: drop the SVG in {@code tools/art/icons/lucide/}, add its constant here
 * (the gallery, {@code /t3dev ui icons}, lists every constant) and add the file to the third-party notice
 * if it is not from Lucide.
 */
public enum T3Icon {
	ARCHIVE,
	ARROW_LEFT,
	ARROW_RIGHT,
	BELL,
	BRAIN,
	CHECK,
	CHEVRON_DOWN,
	CHEVRON_LEFT,
	CHEVRON_RIGHT,
	CHEVRON_UP,
	CIRCLE_CHECK,
	CIRCLE_EXCLAMATION_POINT,
	CIRCLE_X,
	CLOUD,
	COPY,
	DOWNLOAD,
	ELLIPSIS,
	EXTERNAL_LINK,
	EYE,
	EYE_OFF,
	FILE_TEXT,
	FOLDER,
	GIT_BRANCH,
	INFO,
	LINK,
	LOCK,
	MESSAGE_SQUARE,
	MINUS,
	PANEL_LEFT,
	PAPERCLIP,
	PENCIL,
	PIN,
	PLAY,
	PLUS,
	REFRESH_CW,
	SEARCH,
	SEND,
	SETTINGS,
	SHIELD,
	SLIDERS_HORIZONTAL,
	SQUARE,
	STAR,
	TERMINAL,
	TRASH,
	TRIANGLE_EXCLAMATION_POINT,
	X;

	/** The usual size in GUI pixels (the sprite is 32 px, so it stays sharp up to GUI scale 2-3). */
	public static final int SIZE = 12;

	private final String id;
	private final Identifier sprite;

	T3Icon() {
		this.id = name().toLowerCase(Locale.ROOT).replace('_', '-');
		this.sprite = Identifier.parse("t3craft:icons/" + id);
	}

	/** File name without extension, e.g. {@code chevron-down}. */
	public String id() {
		return id;
	}

	/** The GUI sprite, {@code t3craft:icons/<id>}. */
	public Identifier sprite() {
		return sprite;
	}

	/** The icon with this id, or null. */
	public static @Nullable T3Icon byId(String id) {
		for (T3Icon i : values()) {
			if (i.id.equals(id)) {
				return i;
			}
		}
		return null;
	}

	/** Resting colour of an icon: muted; accent under the pointer; disabled when it cannot be used. */
	public static int tint(ChatTheme t, boolean hover, boolean enabled) {
		return !enabled ? t.disabled() : hover ? t.accent() : t.muted();
	}

	/**
	 * Draws {@code icon} as a {@code size} px square at {@code x, y}, multiplied by {@code argb} (the sprite is
	 * white, so the result is {@code argb}). A tint with alpha 0 means "no colour given": the theme's muted one.
	 */
	public static void draw(GuiGraphicsExtractor g, ChatTheme t, T3Icon icon, int x, int y, int size, int argb) {
		int tint = (argb >>> 24) == 0 ? t.muted() : argb;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, icon.sprite, x, y, size, size, tint);
	}

	/** {@link #draw} with the state colour of {@link #tint}. */
	public static void draw(GuiGraphicsExtractor g, ChatTheme t, T3Icon icon, int x, int y, int size, boolean hover, boolean enabled) {
		draw(g, t, icon, x, y, size, tint(t, hover, enabled));
	}
}
