// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.hologram;

import com.mojang.math.Transformation;
import dev.t3craft.client.npc.behavior.Pos;
import dev.t3craft.t3.ThreadState;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Draws the {@link HologramModel} with three client-only display entities (never known to any
 * server, nothing registered, no block): a big CENTER-billboard header with the counts, a smaller
 * detail card under it (words, rings, most urgent thread) and a thin full-bright stained-glass beam
 * from the floor to the card, tinted by the most urgent state (AgentCraft's beam from the lens to
 * the card). Heights come from {@link HologramLayout}.
 *
 * <p><b>Entity ids</b> count down from {@link #ID_BASE} = −8,000,000 (NpcRegistry −1,000,000…,
 * builders −2,000,000…, BehaviorDirector −3,000,000…, helpers −4,000,000…, workstations
 * −5,000,000…, archive shelf −6,000,000…, schedule board −7,000,000…). NpcRegistry's orphan sweep
 * treats every id ≤ −1,000,000 as its own and may discard ours after a recovery: each tick checks
 * {@link Entity#isRemoved()} and respawns. Entities are kept by reference, never looked up by id.
 *
 * <p>Entities are spawned once per anchor and moved only when the anchor moves; text and beam
 * colour are pushed only when the model instance changes.
 */
final class HologramRenderer {
	static final int ID_BASE = -8_000_000;
	/** Ids used per spawn (header, detail, beam); the counter wraps inside a 100,000-id band. */
	private static final int ID_BAND = 100_000;
	private static final int HEADER_BG = 0x80101418;
	private static final int DETAIL_BG = 0xA0101418;
	private static final int LINE_WIDTH_PX = 400;
	/** Render distance multiplier: the card is seen from the far side of the hub and beyond. */
	private static final float VIEW_RANGE = 4f;

	private Display.@Nullable TextDisplay header;
	private Display.@Nullable TextDisplay detail;
	private Display.@Nullable BlockDisplay beam;
	private @Nullable Pos at;
	private @Nullable HologramModel shown;
	private int nextId = ID_BASE;

	boolean active() {
		return header != null;
	}

	/** Shows {@code m} at {@code floor} (anchor foot level); hidden model or null anchor removes everything. */
	void apply(ClientLevel lvl, HologramModel m, @Nullable Pos floor) {
		if (m.hidden() || floor == null) {
			clear(lvl);
			return;
		}
		boolean broken = header == null || header.isRemoved() || detail == null || detail.isRemoved() || beam == null || beam.isRemoved();
		if (broken || !floor.equals(at)) {
			clear(lvl);
			spawn(lvl, floor);
			at = floor;
		}
		if (m != shown || broken) {
			push(m, floor);
			shown = m;
		}
	}

	private void spawn(ClientLevel lvl, Pos floor) {
		header = text(lvl, HologramLayout.HEADER_SCALE, HEADER_BG);
		detail = text(lvl, HologramLayout.DETAIL_SCALE, DETAIL_BG);
		Display.BlockDisplay b = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, lvl);
		b.setId(id());
		b.setBrightnessOverride(Brightness.FULL_BRIGHT);
		b.setViewRange(VIEW_RANGE);
		float w = HologramLayout.BEAM_WIDTH;
		b.setTransformation(new Transformation(null, null, new Vector3f(w, HologramLayout.beamHeight(), w), null));
		b.snapTo(floor.x() - w / 2, floor.y() + HologramLayout.BEAM_FLOOR, floor.z() - w / 2, 0f, 0f);
		lvl.addEntity(b);
		beam = b;
	}

	private Display.TextDisplay text(ClientLevel lvl, float scale, int bg) {
		Display.TextDisplay d = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, lvl);
		d.setId(id());
		d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		d.setLineWidth(LINE_WIDTH_PX);
		d.setBrightnessOverride(Brightness.FULL_BRIGHT);
		d.setViewRange(VIEW_RANGE);
		d.setBackgroundColor(bg);
		d.setTransformation(new Transformation(null, null, new Vector3f(scale), null));
		lvl.addEntity(d);
		return d;
	}

	private int id() {
		int id = nextId--;
		if (nextId <= ID_BASE - ID_BAND) {
			nextId = ID_BASE;
		}
		return id;
	}

	private void push(HologramModel m, Pos floor) {
		List<List<HologramModel.Span>> lines = m.detail();
		detail.setText(component(lines));
		detail.snapTo(floor.x(), floor.y() + HologramLayout.CARD_BOTTOM, floor.z(), 0f, 0f);
		header.setText(component(List.of(m.header())));
		header.snapTo(floor.x(), floor.y() + HologramLayout.headerY(lines.size()), floor.z(), 0f, 0f);
		beam.setBlockState(beamBlock(m.top()));
	}

	static BlockState beamBlock(ThreadState s) {
		return switch (s) {
			case NEEDS_ATTENTION -> Blocks.STAINED_GLASS.orange().defaultBlockState();
			case ERROR -> Blocks.STAINED_GLASS.red().defaultBlockState();
			case WORKING -> Blocks.STAINED_GLASS.lightBlue().defaultBlockState();
			case DONE -> Blocks.STAINED_GLASS.lime().defaultBlockState();
			case IDLE -> Blocks.STAINED_GLASS.white().defaultBlockState();
		};
	}

	static Component component(List<List<HologramModel.Span>> lines) {
		MutableComponent out = Component.empty();
		boolean first = true;
		for (List<HologramModel.Span> line : lines) {
			if (!first) {
				out.append("\n");
			}
			for (HologramModel.Span s : line) {
				out.append(Component.literal(s.text()).withColor(s.color()));
			}
			first = false;
		}
		return out;
	}

	/** Removes our entities from {@code lvl} (null = level already gone) and forgets them. */
	void clear(@Nullable ClientLevel lvl) {
		remove(lvl, header);
		remove(lvl, detail);
		remove(lvl, beam);
		header = null;
		detail = null;
		beam = null;
		at = null;
		shown = null;
	}

	private static void remove(@Nullable ClientLevel lvl, @Nullable Entity e) {
		if (lvl != null && e != null && !e.isRemoved()) {
			lvl.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
		}
	}
}
