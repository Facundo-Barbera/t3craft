package dev.t3craft.client.chat;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Mouse buttons as Minecraft 26.x reports them in {@code MouseButtonEvent.button()}:
 * {@link InputConstants#MOUSE_BUTTON_LEFT} = 1, {@link InputConstants#MOUSE_BUTTON_RIGHT} = 3. They are
 * no longer GLFW's 0/1/2, so a literal {@code button == 0} never matches a left click (that was the
 * "rows do not open" bug of the thread list, fix/ui-chat-sidebar). Compile-time constants, pure Java.
 */
public final class MouseButtons {
	public static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
	public static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;

	private MouseButtons() {
	}

	public static boolean isLeft(int button) {
		return button == LEFT;
	}

	public static boolean isRight(int button) {
		return button == RIGHT;
	}
}
