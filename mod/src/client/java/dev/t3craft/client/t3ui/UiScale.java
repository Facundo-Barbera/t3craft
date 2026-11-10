package dev.t3craft.client.t3ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.Window;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.Nullable;

/**
 * The T3 screens' own GUI scale (#42, part of #15): how many framebuffer pixels one logical unit of a T3 screen
 * takes, apart from Minecraft's GUI scale, so the T3 Code-like UI can be denser on a large monitor without the
 * player changing the game's scale.
 *
 * <p>Preference, {@code layout.uiScale} in {@code config/t3craft.json} ({@link T3LayoutPrefs}):
 * <ul>
 *   <li>{@code "minecraft"} (default): the game's GUI scale, exactly as before (nothing is transformed);</li>
 *   <li>{@code "auto"}: the largest whole scale at which the window still has at least
 *       {@value #AUTO_MIN_W}×{@value #AUTO_MIN_H} logical units (1080p → 2, 1440p → 2, 4K → 4);</li>
 *   <li>a number: that scale, with the same meaning as Minecraft's GUI scale number (whole numbers only,
 *       1..{@value #MAX_SCALE}, never more than the game itself would allow for the window).</li>
 * </ul>
 * Whole numbers only: the window keeps an integer GUI scale (the IME area and the tooltips use it, see below)
 * and the bitmap font is uneven at half steps; half steps wait for the TTF font of #15.
 *
 * <p>How it is applied, in one place per root screen ({@code T3Screen}, {@code T3PanelScreen},
 * {@code T3DialogScreen}), so panels keep working in logical units without knowing:
 * <ol>
 *   <li>{@link Frame#applyTo}: {@code init()} sets the screen's {@code width}/{@code height} to the logical size,
 *       so every layout (and T7's compact mode and frame caps) is computed in logical units;</li>
 *   <li>{@link Frame#render}: drawing runs under a pose scaled by {@link Frame#k()} (scissor rectangles follow the
 *       pose: {@code enableScissor} transforms them), and for its duration the window reports the T3 scale as its
 *       GUI scale ({@code Window.setGuiScale}, restored in a {@code finally}). That makes
 *       {@code getGuiScaledWidth()}, the IME candidate area ({@code TextInputManager.setTextInputArea} multiplies
 *       by the window's scale) and tooltip placement work in logical units with no change at their call sites;</li>
 *   <li>{@link Frame#flushDeferred}: the frame's tooltips and IME preedit are drawn at the end of that scaled pass
 *       (Minecraft would draw them afterwards, unscaled, at the logical coordinates they were given);</li>
 *   <li>{@link Frame#toLogical}: mouse events (click, drag, release, wheel, move) are divided by {@code k} before
 *       the screen sees them.</li>
 * </ol>
 * With {@code "minecraft"} (or when the window cannot be read, as in the headless renderer) {@link Frame#isNative()}
 * is true and every step is a no-op.
 */
public final class UiScale {
	public static final String CONFIG_KEY = "uiScale";
	/** Smallest logical window {@code "auto"} keeps. */
	public static final int AUTO_MIN_W = 960;
	public static final int AUTO_MIN_H = 540;
	public static final int MAX_SCALE = 8;
	/** Minecraft's own limit for a GUI scale: at least 320×240 GUI pixels. */
	private static final int VANILLA_MIN_W = 320;
	private static final int VANILLA_MIN_H = 240;

	/** While {@link Frame#render} has swapped the window's scale: the game's real one (render thread only). */
	private static int realScale;

	private UiScale() {
	}

	public enum Mode {
		MINECRAFT, AUTO, FIXED
	}

	/** The preference: a mode and, for {@link Mode#FIXED}, the scale. */
	public record Pref(Mode mode, int fixed) {
		public static final Pref MINECRAFT = new Pref(Mode.MINECRAFT, 0);
		public static final Pref AUTO = new Pref(Mode.AUTO, 0);

		public static Pref fixed(int scale) {
			return new Pref(Mode.FIXED, Math.max(1, Math.min(MAX_SCALE, scale)));
		}

		/** How it is written in the config ({@code "minecraft"}, {@code "auto"} or a number). */
		public JsonElement toJson() {
			return switch (mode) {
				case MINECRAFT -> new JsonPrimitive("minecraft");
				case AUTO -> new JsonPrimitive("auto");
				case FIXED -> new JsonPrimitive(fixed);
			};
		}

		/** Short label for the settings row and the gallery. */
		public String label() {
			return switch (mode) {
				case MINECRAFT -> "Minecraft";
				case AUTO -> "Auto";
				case FIXED -> Integer.toString(fixed);
			};
		}
	}

	/**
	 * Lenient: {@code "auto"}, {@code "minecraft"} (any case), a number or a numeric string; anything else
	 * (missing, a typo) is {@link Pref#MINECRAFT}. Numbers are rounded to whole scales.
	 */
	public static Pref parse(@Nullable JsonElement e) {
		if (e == null || !e.isJsonPrimitive()) {
			return Pref.MINECRAFT;
		}
		JsonPrimitive p = e.getAsJsonPrimitive();
		if (p.isNumber()) {
			return fromNumber(p.getAsDouble());
		}
		if (!p.isString()) {
			return Pref.MINECRAFT;
		}
		return parse(p.getAsString());
	}

	/** The same from text (the command and the settings row). */
	public static Pref parse(@Nullable String s) {
		if (s == null) {
			return Pref.MINECRAFT;
		}
		String t = s.strip().toLowerCase(Locale.ROOT);
		if (t.equals("auto")) {
			return Pref.AUTO;
		}
		try {
			return fromNumber(Double.parseDouble(t));
		} catch (NumberFormatException ex) {
			return Pref.MINECRAFT;
		}
	}

	private static Pref fromNumber(double d) {
		if (!Double.isFinite(d) || d <= 0) {
			return Pref.MINECRAFT;
		}
		return Pref.fixed((int) Math.round(Math.min(MAX_SCALE, d)));
	}

	/**
	 * The scale the T3 screens use for a framebuffer {@code fbW}×{@code fbH} when the game's GUI scale is
	 * {@code mcScale}. Pure.
	 */
	public static int target(Pref p, int fbW, int fbH, int mcScale) {
		int cap = Math.max(1, Math.min(fbW / VANILLA_MIN_W, fbH / VANILLA_MIN_H));
		return switch (p.mode()) {
			case MINECRAFT -> mcScale;
			case AUTO -> Math.max(1, Math.min(cap, Math.min(fbW / AUTO_MIN_W, fbH / AUTO_MIN_H)));
			case FIXED -> Math.max(1, Math.min(cap, p.fixed()));
		};
	}

	/** The game's GUI scale, also while a {@link Frame#render} has swapped it. */
	public static int minecraftScale(Window w) {
		return realScale > 0 ? realScale : w.getGuiScale();
	}

	/** The preference in {@code config/t3craft.json} ({@code layout.uiScale}). */
	public static Pref configured() {
		return T3LayoutPrefs.load().uiScale();
	}

	/** The frame a T3 screen uses now with the configured preference. */
	public static Frame frame(@Nullable Minecraft mc) {
		return frame(mc, configured());
	}

	/** The frame a T3 screen uses now with {@code pref} (the gallery forces one). */
	public static Frame frame(@Nullable Minecraft mc, Pref pref) {
		if (mc == null || pref.mode() == Mode.MINECRAFT) {
			return Frame.NATIVE;
		}
		Window w;
		try {
			w = mc.getWindow();
		} catch (RuntimeException | LinkageError e) {
			return Frame.NATIVE;
		}
		if (w == null) {
			return Frame.NATIVE;
		}
		int mcs = minecraftScale(w);
		int fbW = w.getWidth();
		int fbH = w.getHeight();
		// a window that does not add up (the headless renderer) is left alone
		if (mcs <= 0 || fbW <= 0 || fbH <= 0 || ceilDiv(fbW, mcs) != w.getGuiScaledWidth() && realScale == 0) {
			return Frame.NATIVE;
		}
		int s = target(pref, fbW, fbH, mcs);
		if (s == mcs) {
			return Frame.NATIVE;
		}
		return new Frame(s, mcs, ceilDiv(fbW, s), ceilDiv(fbH, s));
	}

	private static int ceilDiv(int a, int b) {
		return (int) Math.ceil(a / (double) b);
	}

	/**
	 * One root screen's scale: {@code scale} framebuffer px per logical unit, {@code mcScale} the game's, and the
	 * screen's logical size. {@link #NATIVE} = Minecraft's scale, nothing transformed.
	 */
	public record Frame(int scale, int mcScale, int width, int height) {
		public static final Frame NATIVE = new Frame(0, 0, 0, 0);

		public boolean isNative() {
			return scale <= 0 || mcScale <= 0 || scale == mcScale;
		}

		/** Logical units → Minecraft GUI units. */
		public float k() {
			return isNative() ? 1f : (float) scale / mcScale;
		}

		/** A coordinate in Minecraft GUI units (an event, the render's mouse) in logical units. */
		public double toLogical(double gui) {
			return isNative() ? gui : gui * mcScale / scale;
		}

		public int toLogical(int gui) {
			return isNative() ? gui : (int) Math.floor(gui * (double) mcScale / scale);
		}

		public MouseButtonEvent toLogical(MouseButtonEvent e) {
			return isNative() ? e : new MouseButtonEvent(toLogical(e.x()), toLogical(e.y()), e.buttonInfo());
		}

		/**
		 * Gives {@code s} its logical size; called first thing in {@code init()} (also from {@code rebuildWidgets()},
		 * so it reads the window, never the screen's current size). Native: the window's GUI size, as
		 * {@code Screen.init(w, h)} set it.
		 */
		public void applyTo(Screen s, @Nullable Minecraft mc) {
			if (!isNative()) {
				s.width = width;
				s.height = height;
				return;
			}
			if (mc != null && realScale == 0) {
				try {
					Window w = mc.getWindow();
					if (w != null && w.getGuiScaledWidth() > 0 && w.getGuiScaledHeight() > 0) {
						s.width = w.getGuiScaledWidth();
						s.height = w.getGuiScaledHeight();
					}
				} catch (RuntimeException | LinkageError e) {
					// keep what Screen.init set
				}
			}
		}

		/**
		 * Runs {@code body} (drawing, in logical units) under the pose scale and the window scale swap. Native:
		 * just runs it.
		 */
		public void render(GuiGraphicsExtractor g, Runnable body) {
			if (isNative()) {
				body.run();
				return;
			}
			Window w = Minecraft.getInstance().getWindow();
			boolean swap = realScale == 0 && w.getGuiScale() == mcScale;
			float k = k();
			g.pose().pushMatrix();
			g.pose().scale(k, k);
			if (swap) {
				realScale = mcScale;
				w.setGuiScale(scale);
			}
			try {
				body.run();
			} finally {
				if (swap) {
					w.setGuiScale(mcScale);
					realScale = 0;
				}
				g.pose().popMatrix();
			}
		}

		/**
		 * Draws this frame's tooltip and IME preedit now, inside {@link #render}, at the logical mouse; Minecraft's own
		 * pass after the screen then finds nothing left. Call last in the scaled pass. Native: no-op.
		 */
		public void flushDeferred(GuiGraphicsExtractor g, int logicalMouseX, int logicalMouseY, float a) {
			if (isNative()) {
				return;
			}
			g.extractDeferredElements(logicalMouseX, logicalMouseY, a);
			g.setPreeditOverlay(null); // drawn here; the field is not cleared by extractDeferredElements
		}
	}
}
