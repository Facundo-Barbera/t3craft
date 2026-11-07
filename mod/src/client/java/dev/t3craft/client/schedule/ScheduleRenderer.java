package dev.t3craft.client.schedule;

import com.mojang.math.Transformation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Puts the schedule board in the world as one client-only FIXED {@link Display.TextDisplay}
 * (never known to any server, nothing registered). Display only: it has no hitbox and reacts to no
 * click (running or toggling tasks is a write, item 26, not approved).
 *
 * <p><b>Entity ids</b> count down from {@link #ID_BASE} = −7,000,000. Taken ranges: NpcRegistry
 * −1,000,000…, builders −2,000,000…, BehaviorDirector −3,000,000…, helpers −4,000,000…, workstation
 * −5,000,000…, blocks/archive −6,000,000…. NpcRegistry's orphan sweep may discard ids ≤ −1,000,000
 * after one of its rare recoveries, so every tick checks {@link Entity#isRemoved()} and re-creates the
 * display. The entity is kept by reference, never looked up by id with a cast (hot-swap safe).
 */
final class ScheduleRenderer {
	static final int ID_BASE = -7_000_000;
	/** Text scale: one font pixel = 0.025 × scale blocks. */
	static final float SCALE = 0.55f;
	/** Wrap width in font pixels: ≈ 6.6 blocks at {@link #SCALE}, inside the HQ board's 7. */
	static final int LINE_WIDTH_PX = 480;

	private Display.@Nullable TextDisplay display;
	private @Nullable SchedulePlacement placed;
	private ScheduleText.@Nullable Board shown;
	private int nextId = ID_BASE;

	boolean visible() {
		return display != null;
	}

	/** Shows {@code board} at {@code where}; re-creates the entity only when it moved or vanished. */
	void show(ClientLevel lvl, SchedulePlacement where, ScheduleText.Board board) {
		Display.TextDisplay d = display;
		if (d == null || d.isRemoved() || !where.equals(placed)) {
			clear(lvl);
			d = spawn(lvl, where);
			display = d;
			placed = where;
		}
		if (!board.equals(shown)) {
			d.setText(component(board));
			dev.t3craft.debug.PerfStats.count(dev.t3craft.debug.PerfStats.DISPLAY_SET_TEXT);
			d.setBackgroundColor(board.background());
			shown = board;
		}
	}

	/** Removes the display from {@code lvl} (null = level already gone) and forgets it. */
	void clear(@Nullable ClientLevel lvl) {
		Display.TextDisplay d = display;
		if (d != null && lvl != null && !d.isRemoved()) {
			lvl.removeEntity(d.getId(), Entity.RemovalReason.DISCARDED);
		}
		display = null;
		placed = null;
		shown = null;
	}

	private Display.TextDisplay spawn(ClientLevel lvl, SchedulePlacement p) {
		Display.TextDisplay d = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, lvl);
		if (nextId < ID_BASE + 1 - 1_000_000) {
			nextId = ID_BASE;
		}
		d.setId(nextId--);
		d.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		d.setLineWidth(LINE_WIDTH_PX);
		d.setBrightnessOverride(Brightness.FULL_BRIGHT);
		d.setTransformation(new Transformation(null, null, new Vector3f(SCALE), null));
		d.snapTo(p.x(), p.y(), p.z(), p.yaw(), 0f);
		lvl.addEntity(d);
		dev.t3craft.debug.PerfStats.count(dev.t3craft.debug.PerfStats.DISPLAY_SPAWNED);
		return d;
	}

	static Component component(ScheduleText.Board board) {
		MutableComponent out = Component.empty();
		boolean first = true;
		for (ScheduleText.Line l : board.lines()) {
			if (!first) {
				out.append("\n");
			}
			out.append(Component.literal(l.text()).withColor(l.color()));
			first = false;
		}
		return out;
	}
}
