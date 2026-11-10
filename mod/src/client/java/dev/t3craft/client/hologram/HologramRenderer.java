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
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.npc.WorldText;
import org.joml.Quaternionf;
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
 *
 * <p><b>Card style</b> (world text, {@code labels.style: "hologram"}, docs/features/world-text.md):
 * one card instead of loose lines, after AgentCraft's Goal Atrium hologram. A progress ring drawn by
 * code ({@link HologramLayout#RING_SEGMENTS} thin full-bright stained-glass {@link Display.BlockDisplay}
 * segments on a circle, CENTER-billboarded with the circle in the billboard plane, so it always faces
 * the viewer; done segments in the done colour) with the {@code done/total} caption in its middle,
 * the card text under it on the theme's panel colour, and a soft beam (a thin core plus a wider,
 * fainter halo). No sprite: a text display cannot draw the chat kit's {@code bubble} sprites, so the
 * shapes are entities.
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
	/** Card style: the beam's halo and the ring's segments (empty in the classic style). */
	private Display.@Nullable BlockDisplay halo;
	private final java.util.List<Display.BlockDisplay> ring = new java.util.ArrayList<>();
	private boolean cardStyle;
	private @Nullable Pos at;
	private @Nullable HologramModel shown;
	private int nextId = ID_BASE;

	boolean active() {
		return header != null;
	}

	/** Shows {@code m} at {@code floor} (anchor foot level); hidden model or null anchor removes everything. */
	void apply(ClientLevel lvl, HologramModel m, @Nullable Pos floor) {
		apply(lvl, m, floor, false);
	}

	/** {@code card}: the world-text card style (ring, card, soft beam) instead of the header + lines. */
	void apply(ClientLevel lvl, HologramModel m, @Nullable Pos floor, boolean card) {
		if (m.hidden() || floor == null) {
			clear(lvl);
			return;
		}
		boolean broken = header == null || header.isRemoved() || detail == null || detail.isRemoved() || beam == null || beam.isRemoved()
				|| (card && (halo == null || halo.isRemoved() || ring.size() != HologramLayout.RING_SEGMENTS || ring.stream().anyMatch(Entity::isRemoved)));
		if (broken || !floor.equals(at) || card != cardStyle) {
			clear(lvl);
			cardStyle = card;
			if (card) {
				spawnCard(lvl, floor);
			} else {
				spawn(lvl, floor);
			}
			at = floor;
			broken = true;
		}
		ChatTheme theme = card ? WorldText.theme() : null;
		if (m != shown || broken || (card && theme != shownTheme)) {
			if (card) {
				pushCard(m, floor, theme);
			} else {
				push(m, floor);
			}
			shown = m;
			shownTheme = theme;
		}
	}

	private @Nullable ChatTheme shownTheme;

	private void spawnCard(ClientLevel lvl, Pos floor) {
		header = text(lvl, HologramLayout.CAPTION_SCALE, 0);
		detail = text(lvl, HologramLayout.CARD_SCALE, 0);
		beam = column(lvl, floor, HologramLayout.BEAM_CORE);
		halo = column(lvl, floor, HologramLayout.BEAM_HALO);
		float len = HologramLayout.segmentLength();
		float th = HologramLayout.RING_THICK;
		float dp = HologramLayout.RING_DEPTH;
		for (int i = 0; i < HologramLayout.RING_SEGMENTS; i++) {
			double a = HologramLayout.segmentAngle(i);
			Quaternionf q = new Quaternionf().rotateZ((float) (a - Math.PI / 2)); // the segment's length along the tangent
			Vector3f half = new Vector3f(len / 2, th / 2, dp / 2).rotate(q);
			Vector3f centre = new Vector3f((float) (HologramLayout.RING_RADIUS * Math.cos(a)), (float) (HologramLayout.RING_RADIUS * Math.sin(a)), 0f);
			Display.BlockDisplay b = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, lvl);
			b.setId(id());
			b.setBrightnessOverride(Brightness.FULL_BRIGHT);
			b.setViewRange(VIEW_RANGE);
			b.setBillboardConstraints(Display.BillboardConstraints.CENTER);
			b.setTransformation(new Transformation(centre.sub(half), q, new Vector3f(len, th, dp), null));
			lvl.addEntity(b);
			ring.add(b);
		}
	}

	/** A full-bright beam column {@code w} wide from just over the floor to the card's bottom. */
	private Display.BlockDisplay column(ClientLevel lvl, Pos floor, float w) {
		Display.BlockDisplay b = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, lvl);
		b.setId(id());
		b.setBrightnessOverride(Brightness.FULL_BRIGHT);
		b.setViewRange(VIEW_RANGE);
		b.setTransformation(new Transformation(null, null, new Vector3f(w, HologramLayout.beamHeight(), w), null));
		b.snapTo(floor.x() - w / 2, floor.y() + HologramLayout.BEAM_FLOOR, floor.z() - w / 2, 0f, 0f);
		lvl.addEntity(b);
		return b;
	}

	private void pushCard(HologramModel m, Pos floor, ChatTheme theme) {
		List<List<HologramModel.Span>> lines = m.card(theme);
		detail.setText(component(lines));
		detail.setBackgroundColor(ChatTheme.withAlpha(theme.panel(), WorldText.PLATE_ALPHA));
		detail.snapTo(floor.x(), floor.y() + HologramLayout.CARD_BOTTOM, floor.z(), 0f, 0f);
		int doneRgb = theme.done() & 0xFFFFFF;
		header.setText(Component.literal(m.ringCaption()).withColor(m.ringDone() > 0 ? doneRgb : theme.text() & 0xFFFFFF));
		header.snapTo(floor.x(), floor.y() + HologramLayout.captionY(lines.size()), floor.z(), 0f, 0f);
		double cy = floor.y() + HologramLayout.ringCentre(lines.size());
		int filled = HologramModel.filled(m.ringDone(), m.ringTotal(), HologramLayout.RING_SEGMENTS);
		BlockState on = Blocks.STAINED_GLASS.lime().defaultBlockState();
		BlockState off = Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.GRAY).defaultBlockState();
		for (int i = 0; i < ring.size(); i++) {
			Display.BlockDisplay seg = ring.get(i);
			seg.setBlockState(i < filled ? on : off);
			seg.snapTo(floor.x(), cy, floor.z(), 0f, 0f);
		}
		BlockState tint = beamBlock(m.top());
		beam.setBlockState(tint);
		if (halo != null) {
			halo.setBlockState(tint);
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
		remove(lvl, halo);
		ring.forEach(e -> remove(lvl, e));
		ring.clear();
		header = null;
		detail = null;
		beam = null;
		halo = null;
		shownTheme = null;
		at = null;
		shown = null;
	}

	private static void remove(@Nullable ClientLevel lvl, @Nullable Entity e) {
		if (lvl != null && e != null && !e.isRemoved()) {
			lvl.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
		}
	}
}
