package dev.t3craft.client.npc;

import net.minecraft.ChatFormatting;

/** Mirror of the bridge's mapped thread state (protocol {@code npc.state}). */
public enum NpcState {
	WORKING("working", ChatFormatting.AQUA, "⚙"),
	NEEDS_ATTENTION("needs_attention", ChatFormatting.GOLD, "!"),
	DONE("done", ChatFormatting.GREEN, "✔"),
	ERROR("error", ChatFormatting.RED, "✖"),
	IDLE("idle", ChatFormatting.GRAY, "·");

	public final String wire;
	public final ChatFormatting color;
	public final String glyph;

	NpcState(String wire, ChatFormatting color, String glyph) {
		this.wire = wire;
		this.color = color;
		this.glyph = glyph;
	}

	public static NpcState fromWire(String s) {
		for (NpcState st : values()) {
			if (st.wire.equals(s)) {
				return st;
			}
		}
		return IDLE;
	}
}
