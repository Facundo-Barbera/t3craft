package dev.t3craft.client.chat.compose;

import org.jspecify.annotations.Nullable;

/**
 * How a screen hands keyboard input to its composer: the unified T3 screen ({@code T3Screen}) calls
 * these from its {@code keyPressed} / {@code charTyped}, and the tests type through the same path.
 *
 * <p>Key presses and characters are separate platform events: under SDL3 (Minecraft 26.x) a letter
 * is a key press (which the focused composer swallows, so no shortcut fires) plus a text event
 * that only exists while text input is started ({@link ComposerView.TextInput}). Swallowing the
 * press does not cancel the character.
 */
public final class ComposerInput {
	private ComposerInput() {
	}

	/** Whether {@code c} takes the keyboard right now (focused): the screen's shortcuts must not fire. */
	public static boolean owns(@Nullable ComposerView c) {
		return c != null && c.focused();
	}

	/** First step of the screen's {@code keyPressed} (after open popups): true when the composer consumed it. */
	public static boolean keyPressed(@Nullable ComposerView c, int key, boolean shift, boolean ctrl) {
		return owns(c) && c.keyPressed(key, shift, ctrl);
	}

	/** The screen's {@code charTyped} while {@link #owns}: the character goes into the composer. */
	public static boolean charTyped(@Nullable ComposerView c, int codepoint) {
		return owns(c) && c.charTyped(codepoint);
	}
}
