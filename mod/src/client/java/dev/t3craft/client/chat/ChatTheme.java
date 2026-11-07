// Layout ideas adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.chat;

/**
 * Colours and metrics of the chat UI. The palette is the portable terminal's (dark teal
 * "T3 TERMINAL" look, {@code client.portable.PortableScreen}) so both screens read as one product;
 * the structure (colour stripe per speaker, time separators, inset feed, prompt bar with a live
 * hint, key-cap hints in the footer, "newer below" pill) follows AgentCraft's console. Pure
 * constants: safe across hot swaps.
 */
public final class ChatTheme {
	private ChatTheme() {
	}

	// Portable terminal palette (same values as PortableScreen).
	public static final int BACKDROP = 0x88000000;
	public static final int BG = 0xF0101418;
	public static final int PANEL = 0xFF1A2027;
	public static final int BORDER = 0xFF3A6B73;
	public static final int ACCENT = 0xFF55FFFF;
	public static final int TEXT = 0xFFE6E6E6;
	public static final int DIM = 0xFF8A949E;
	public static final int SELECTED = 0xFF27414A;

	// Chat additions in the same family.
	/** Recessed feed well (darker than PANEL). */
	public static final int WELL = 0xFF12171C;
	public static final int HOVER = 0xFF1F2A31;
	public static final int CODE_BG = 0xFF0B0F12;
	public static final int CODE_TEXT = 0xFFD0D6DC;
	public static final int INLINE_CODE = 0xFFFFE08A;
	public static final int LINK = 0xFF6FD3FF;
	public static final int RULE = 0xFF2C3A42;
	public static final int ATTENTION = 0xFFFFAA00;
	public static final int ERROR = 0xFFFF5555;
	public static final int OK = 0xFF55FF55;
	public static final int CONNECTING = 0xFFFFFF55;
	public static final int KEYCAP_BG = 0xFF26323A;

	public static final int ROW_H = 12;
	public static final int LINE_H = 10;

	public static int connectionColor(String status) {
		return switch (status) {
			case "ok", "live" -> OK;
			case "connecting", "" -> CONNECTING;
			default -> ERROR;
		};
	}
}
