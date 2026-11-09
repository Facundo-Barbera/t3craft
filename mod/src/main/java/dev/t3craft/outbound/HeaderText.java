package dev.t3craft.outbound;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HTTP header values must be plain ASCII for the JDK client. Non-ASCII text is sent as an RFC 2047
 * encoded word ({@code =?UTF-8?B?...?=}), which the push services decode.
 */
final class HeaderText {
	private HeaderText() {
	}

	static String encode(String s, int max) {
		String clean = Text.clip(Text.oneLine(s), max);
		if (isPlain(clean)) {
			return clean;
		}
		return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(clean.getBytes(StandardCharsets.UTF_8)) + "?=";
	}

	/** Printable ASCII only (0x20 to 0x7E). */
	static boolean isPlain(String s) {
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c < 0x20 || c > 0x7E) {
				return false;
			}
		}
		return true;
	}
}
