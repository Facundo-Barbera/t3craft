// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.blocks;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * The {@code status} block state of a status lamp (desk lamp and board bulb). The colours are the
 * thread state colours of {@code ThreadState} / {@code StationStatus}, so lamp, screen, name tag and
 * HUD agree; {@link #light} is the block light the lamp gives off.
 *
 * <p>Adapted from AgentCraft's {@code LampStatus} (a {@code status} enum property selecting the lit
 * texture); the states and mapping are t3craft's own.
 */
public enum LampStatus implements StringRepresentable {
	/** Unbound, vacant desk, archived thread, or an empty board bulb. */
	OFF(0x2A2A2A, 0),
	IDLE(0xAAAAAA, 4),
	WORKING(0x55FFFF, 11),
	ATTENTION(0xFFAA00, 14),
	ERROR(0xFF5555, 12),
	DONE(0x55FF55, 9);

	/** 0xRRGGBB of the lit lens (docs and tests; the textures use the same colours). */
	public final int color;
	/** Block light level, 0..15. */
	public final int light;
	private final String serialized;

	LampStatus(int color, int light) {
		this.color = color;
		this.light = light;
		this.serialized = name().toLowerCase(Locale.ROOT);
	}

	@Override
	public String getSerializedName() {
		return serialized;
	}

	/** How urgently it asks for the player, highest first (board bulbs show the most urgent threads). */
	public int urgency() {
		return switch (this) {
			case ERROR -> 5;
			case ATTENTION -> 4;
			case WORKING -> 3;
			case DONE -> 2;
			case IDLE -> 1;
			case OFF -> 0;
		};
	}
}
