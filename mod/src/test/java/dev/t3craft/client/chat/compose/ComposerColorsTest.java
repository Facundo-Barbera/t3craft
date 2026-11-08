package dev.t3craft.client.chat.compose;

import static org.junit.jupiter.api.Assertions.*;

import dev.t3craft.client.chat.ChatTheme;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Text drawn with a color whose alpha byte is 0 is skipped by {@code drawString} in recent
 * versions: every color the composer writes text with must be ARGB with a non-zero alpha.
 */
class ComposerColorsTest {

	static void assertOpaque(List<Integer> colors, String theme) {
		assertFalse(colors.isEmpty());
		for (int c : colors) {
			assertNotEquals(0, c >>> 24, theme + ": text color " + Integer.toHexString(c) + " has alpha 0");
		}
	}

	@Test
	void builtInThemesHaveOpaqueTextColors() {
		assertOpaque(ComposerView.textColors(ChatTheme.warmStudio()), "warmStudio");
		assertOpaque(ComposerView.textColors(ChatTheme.terminal()), "terminal");
	}

	@Test
	void bareRgbThemeColorsAreMadeOpaque() {
		ChatTheme rgb = ChatTheme.warmStudio().toBuilder().text(0x1F1E1D).ghost(0xBCAD95).muted(0x655E55).build();
		assertOpaque(ComposerView.textColors(rgb), "rgb-only");
		assertEquals(0xFF1F1E1D, ComposerView.ink(0x1F1E1D));
		assertEquals(0x80FFFFFF, ComposerView.ink(0x80FFFFFF), "an explicit alpha is kept");
	}
}
