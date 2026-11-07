// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.t3craft.blocks.ThreadBoardBlock;
import dev.t3craft.blocks.ThreadBoardBlockEntity;
import dev.t3craft.client.chat.Attention;
import dev.t3craft.client.chat.ChatTheme;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The thread wall BER: from its anchor block ({@code t3craft:thread_board}, the wall's bottom-left
 * cell) draws a kanban of threads over a {@code width × height} wall, in the chat kit's colours
 * ({@link ChatTheme#warmStudio()}, paper cards on a walnut board): column headers with counts,
 * one card per thread (attention stripe, title, {@code EnvBadge}, project, queued runs), "+N more"
 * chips, hover outline under the crosshair, a pulsing ring on cards that need the player, and cards
 * that fly (lifted, with a shadow) to their new column and glow where they land.
 *
 * <p>Cost: the model ({@link WallBoard}) is rebuilt only when the cards change, and glyph runs only
 * when its version changes; a steady wall is one list comparison plus the draw. LOD by distance to
 * the wall's centre ({@link WallGeometry.Lod}): text and hover up to {@code detailDistance}, coloured
 * blocks only up to {@code viewDistance}, nothing beyond.
 *
 * <p>Drawing from the panel origin over the whole board, the lifted flying cards with shadows and the
 * overflow chip adapted from AgentCraft's {@code TaskBoardRenderer}.
 */
public final class ThreadBoardRenderer implements BlockEntityRenderer<ThreadBoardBlockEntity, ThreadBoardRenderer.State> {
	/** Board pixels per block (one character ≈ 6 px). */
	public static final float PPB = 64f;
	/** Depth step between layers (blocks), enough not to z-fight at the view distance. */
	static final float LAYER = 0.0016f;
	static final int WALNUT = 0xFF3B2F25;
	static final int LANE = 0xFF46372B;
	static final int SHADOW = 0x50000000;
	private static final int MAX_CACHED = 64;

	public static final class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		int width;
		int height;
		WallGeometry.Lod lod = WallGeometry.Lod.HIDDEN;
		@Nullable WallBoard board;
		@Nullable Glyphs glyphs;
		float time;
	}

	/** Glyph runs of one board version. */
	record Glyphs(long version, List<FormattedCharSequence> lanes, List<FormattedCharSequence> counts, float[] countW,
			Map<String, FormattedCharSequence[]> cards, List<FormattedCharSequence> more, @Nullable FormattedCharSequence empty) {
	}

	private final ThreadWalls walls;
	private final ChatTheme theme = ChatTheme.warmStudio();
	private final Map<WallBoard, Glyphs> glyphs = new IdentityHashMap<>();

	ThreadBoardRenderer(ThreadWalls walls) {
		this.walls = walls;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true; // the wall is much larger than its anchor block
	}

	@Override
	public int getViewDistance() {
		return walls.config().viewDistance();
	}

	@Override
	public void extractRenderState(ThreadBoardBlockEntity be, State s, float partialTicks, Vec3 camera,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderState.extractBase(be, s, breakProgress);
		BlockState bs = be.getBlockState();
		s.facing = bs.hasProperty(ThreadBoardBlock.FACING) ? bs.getValue(ThreadBoardBlock.FACING) : Direction.NORTH;
		ThreadWallConfig cfg = walls.config();
		ThreadWallConfig.Wall w = cfg.of(be.width(), be.height(), be.columns());
		s.width = w.width();
		s.height = w.height();
		BlockPos origin = be.getBlockPos();
		double[] c = WallGeometry.center(origin, s.facing, w.width(), w.height());
		double dx = c[0] - camera.x, dy = c[1] - camera.y, dz = c[2] - camera.z;
		s.lod = WallGeometry.Lod.of(dx * dx + dy * dy + dz * dz, cfg.detailDistance(), cfg.viewDistance());
		if (s.lod == WallGeometry.Lod.HIDDEN) {
			s.board = null;
			s.glyphs = null;
			return;
		}
		Font font = Minecraft.getInstance().font;
		WallBoard b = walls.board(origin, s.facing, w.width(), w.height());
		b.sync(walls.cards(), w.mode(), theme, w.width() * PPB, w.height() * PPB, font::width);
		long now = System.nanoTime();
		b.step(now);
		s.board = b;
		s.time = (now % 600_000_000_000L) / 1e9f;
		s.glyphs = s.lod == WallGeometry.Lod.FULL ? glyphs(b) : null;
		b.setHovered(s.lod == WallGeometry.Lod.FULL ? hovered(be, s.facing, w.height(), b) : null);
	}

	private Glyphs glyphs(WallBoard b) {
		Glyphs g = glyphs.get(b);
		if (g != null && g.version() == b.version()) {
			return g;
		}
		if (glyphs.size() > MAX_CACHED) {
			glyphs.clear();
		}
		WallLayout.Layout l = b.layout();
		Font font = Minecraft.getInstance().font;
		List<FormattedCharSequence> lanes = new ArrayList<>();
		List<FormattedCharSequence> counts = new ArrayList<>();
		float[] countW = new float[l == null ? 0 : l.lanes().size()];
		Map<String, FormattedCharSequence[]> cards = new HashMap<>();
		List<FormattedCharSequence> more = new ArrayList<>();
		FormattedCharSequence empty = null;
		if (l != null) {
			for (int i = 0; i < l.lanes().size(); i++) {
				WallLayout.Lane lane = l.lanes().get(i);
				lanes.add(seq(lane.label()));
				counts.add(seq(lane.count()));
				countW[i] = font.width(lane.count());
			}
			for (WallLayout.Slot s : l.cards()) {
				cards.put(s.key(), new FormattedCharSequence[] {seq(s.title()), seq(s.footer()), seq(s.badge().initial())});
			}
			for (WallLayout.More m : l.more()) {
				more.add(seq(m.text()));
			}
			if (l.total() == 0) {
				empty = seq("No threads yet");
			}
		}
		g = new Glyphs(b.version(), List.copyOf(lanes), List.copyOf(counts), countW, cards, List.copyOf(more), empty);
		glyphs.put(b, g);
		return g;
	}

	private static FormattedCharSequence seq(String s) {
		return Component.literal(s).getVisualOrderText();
	}

	/** Key of the card (or the "+N more" chip's first thread) under the crosshair, when it is on this wall. */
	private static @Nullable String hovered(ThreadBoardBlockEntity be, Direction facing, int height, WallBoard b) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() != null || !(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK
				|| bh.getDirection() != facing) {
			return null;
		}
		WallLayout.Layout l = b.layout();
		if (l == null || !WallGeometry.onWall(be.getBlockPos(), facing, (int) (l.width() / PPB), height, bh.getBlockPos())) {
			return null;
		}
		Vec3 at = bh.getLocation();
		float[] p = WallGeometry.toBoard(be.getBlockPos(), facing, height, PPB, at.x, at.y, at.z);
		WallLayout.Hit h = b.hit(p[0], p[1]);
		return h == null ? null : h.kind() == WallLayout.HitKind.CARD ? h.key() : "#more:" + h.column();
	}

	// ---- submit -------------------------------------------------------------------------------

	@Override
	public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
		WallBoard b = s.board;
		WallLayout.Layout l = b == null ? null : b.layout();
		if (b == null || l == null || s.lod == WallGeometry.Lod.HIDDEN) {
			return;
		}
		int light = LightCoordsUtil.FULL_BRIGHT;
		boolean text = s.lod == WallGeometry.Lod.FULL && s.glyphs != null;
		Glyphs g = s.glyphs;
		float w = l.width();
		float h = l.height();
		ps.pushPose();
		MonitorRenderer.toFace(ps, s.facing, -0.004f, PPB);
		ps.translate(0, -(s.height - 1) * PPB, 0);

		// layer 0: board + brass trim; layer 1: lanes and headers
		rect(collector.order(0), ps, 0, 0, w, h, theme.brass(), light);
		layer(ps);
		rect(collector.order(1), ps, 2, 2, w - 2, h - 2, WALNUT, light);
		layer(ps);
		OrderedSubmitNodeCollector lanes = collector.order(2);
		for (WallLayout.Lane lane : l.lanes()) {
			float x0 = lane.x(), x1 = lane.x() + lane.w();
			rect(lanes, ps, x0, WallLayout.PAD + WallLayout.HEADER_H + 1, x1, h - WallLayout.PAD, LANE, light);
			rect(lanes, ps, x0, WallLayout.PAD, x1, WallLayout.PAD + WallLayout.HEADER_H - 2, theme.panel(), light);
			rect(lanes, ps, x0, WallLayout.PAD + WallLayout.HEADER_H - 2, x1, WallLayout.PAD + WallLayout.HEADER_H, 0xFF000000 | lane.color(), light);
		}
		for (WallLayout.More m : l.more()) {
			rect(lanes, ps, m.x(), m.y(), m.x() + m.w(), m.y() + m.h(), theme.inset(), light);
		}
		layer(ps);

		// cards: settled ones, then the flying ones lifted above everything
		List<WallLayout.Slot> flying = new ArrayList<>();
		ps.pushPose();
		drawCards(collector, ps, s, b, l, false, flying, light);
		ps.popPose();
		if (text) {
			ps.pushPose();
			ps.translate(0, 0, -4 * LAYER);
			OrderedSubmitNodeCollector t = collector.order(7);
			for (int i = 0; i < l.lanes().size(); i++) {
				WallLayout.Lane lane = l.lanes().get(i);
				t.submitText(ps, lane.x() + WallLayout.CARD_PAD, WallLayout.PAD + 2, g.lanes().get(i), false, Font.DisplayMode.POLYGON_OFFSET,
						light, theme.text(), 0, 0);
				t.submitText(ps, lane.x() + lane.w() - WallLayout.CARD_PAD - g.countW()[i], WallLayout.PAD + 2, g.counts().get(i), false,
						Font.DisplayMode.POLYGON_OFFSET, light, theme.muted(), 0, 0);
			}
			for (int i = 0; i < l.more().size(); i++) {
				WallLayout.More m = l.more().get(i);
				boolean hot = ("#more:" + m.column()).equals(b.hovered());
				t.submitText(ps, m.x() + 5, m.y() + 2, g.more().get(i), false, Font.DisplayMode.POLYGON_OFFSET, light,
						hot ? theme.text() : theme.muted(), 0, 0);
			}
			if (g.empty() != null) {
				Font font = Minecraft.getInstance().font;
				t.submitText(ps, (w - font.width(g.empty())) / 2f, h / 2f - 4, g.empty(), false, Font.DisplayMode.POLYGON_OFFSET, light,
						theme.panel(), 0, 0);
			}
			ps.popPose();
		}
		if (!flying.isEmpty()) {
			ps.pushPose();
			ps.translate(0, 0, -6 * LAYER);
			drawCards(collector, ps, s, b, l, true, flying, light);
			ps.popPose();
		}
		ps.popPose();
	}

	/**
	 * One pass of cards: the settled ones ({@code lifted == false}, collecting the flying ones into
	 * {@code flying}) or the flying ones (shadow first). Layers: outline/shadow, paper, stripe and
	 * badge, text.
	 */
	private void drawCards(SubmitNodeCollector collector, PoseStack ps, State s, WallBoard b, WallLayout.Layout l, boolean lifted,
			List<WallLayout.Slot> flying, int light) {
		int base = lifted ? 8 : 3;
		boolean text = s.lod == WallGeometry.Lod.FULL && s.glyphs != null;
		List<WallLayout.Slot> list = lifted ? flying : l.cards();
		OrderedSubmitNodeCollector under = collector.order(base);
		for (WallLayout.Slot slot : list) {
			WallBoard.Anim a = b.anim(slot.key());
			if (a == null) {
				continue;
			}
			if (!lifted && a.flying) {
				flying.add(slot);
				continue;
			}
			if (lifted) {
				rect(under, ps, a.x + 2, a.y + 3, a.x + a.w + 2, a.y + a.h + 3, SHADOW, light);
			}
			int ring = ring(s, b, slot, a);
			if (ring != 0) {
				rect(under, ps, a.x - 1.5f, a.y - 1.5f, a.x + a.w + 1.5f, a.y + a.h + 1.5f, ring, light);
			}
		}
		ps.pushPose();
		ps.translate(0, 0, -LAYER);
		OrderedSubmitNodeCollector paper = collector.order(base + 1);
		for (WallLayout.Slot slot : list) {
			WallBoard.Anim a = b.anim(slot.key());
			if (a != null && a.flying == lifted) {
				rect(paper, ps, a.x, a.y, a.x + a.w, a.y + a.h, theme.panelHi(), light);
			}
		}
		ps.translate(0, 0, -LAYER);
		OrderedSubmitNodeCollector marks = collector.order(base + 2);
		for (WallLayout.Slot slot : list) {
			WallBoard.Anim a = b.anim(slot.key());
			if (a == null || a.flying != lifted) {
				continue;
			}
			rect(marks, ps, a.x, a.y, a.x + WallLayout.STRIPE, a.y + a.h, theme.color(slot.attention()), light);
			if (text && a.h >= WallLayout.CARD_H - 1) {
				float bx = a.x + WallLayout.STRIPE + WallLayout.CARD_PAD;
				float by = a.y + 13;
				rect(marks, ps, bx, by, bx + WallLayout.BADGE, by + WallLayout.BADGE, slot.badge().background(), light);
			}
		}
		if (text) {
			ps.translate(0, 0, -LAYER);
			Glyphs g = s.glyphs;
			Font font = Minecraft.getInstance().font;
			OrderedSubmitNodeCollector t = collector.order(base + 3);
			for (WallLayout.Slot slot : list) {
				WallBoard.Anim a = b.anim(slot.key());
				FormattedCharSequence[] seqs = g.cards().get(slot.key());
				if (a == null || a.flying != lifted || seqs == null || a.h < WallLayout.CARD_H - 1) {
					continue;
				}
				float tx = a.x + WallLayout.STRIPE + WallLayout.CARD_PAD;
				t.submitText(ps, tx, a.y + 3, seqs[0], false, Font.DisplayMode.POLYGON_OFFSET, light, theme.text(), 0, 0);
				float bx = tx + (WallLayout.BADGE - font.width(seqs[2])) / 2f + 0.5f;
				t.submitText(ps, bx, a.y + 14, seqs[2], false, Font.DisplayMode.POLYGON_OFFSET, light, slot.badge().foreground(), 0, 0);
				t.submitText(ps, tx + WallLayout.BADGE + 3, a.y + 14, seqs[1], false, Font.DisplayMode.POLYGON_OFFSET, light, theme.muted(), 0, 0);
			}
		}
		ps.popPose();
	}

	/** Outline colour of a card: hover (brass), arrival glow, or a pulsing ring when it needs the player; 0 = none. */
	private int ring(State s, WallBoard b, WallLayout.Slot slot, WallBoard.Anim a) {
		if (slot.key().equals(b.hovered())) {
			return theme.brass();
		}
		if (a.glow > 0) {
			return ChatTheme.withAlpha(theme.brass(), (int) (255 * Math.min(1f, a.glow / WallBoard.GLOW_S)));
		}
		if (slot.attention() == Attention.NEEDS_YOU) {
			float pulse = 0.5f + 0.5f * (float) Math.sin(s.time * 3.2f);
			return ChatTheme.withAlpha(theme.needsYou(), (int) (60 + 150 * pulse));
		}
		return 0;
	}

	private static void layer(PoseStack ps) {
		ps.translate(0, 0, -LAYER);
	}

	private static void rect(OrderedSubmitNodeCollector c, PoseStack ps, float x0, float y0, float x1, float y1, int argb, int light) {
		c.submitTextBackground(ps, x0, y0, x1, y1, argb, Font.DisplayMode.NORMAL, light);
	}

	/** Forget cached glyph runs (hot-swap, level change). */
	void clear() {
		glyphs.clear();
	}
}
