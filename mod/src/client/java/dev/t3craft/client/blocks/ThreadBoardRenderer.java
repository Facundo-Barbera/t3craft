// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
 * <p>Look (kit/thread-board): a dark backing in a brass-keylined frame; four dark lanes, each with a
 * header in its state colour (label at 1.75x, count in a pill) and loose charcoal cards (cream text) inset from the lane
 * with a gap between them: state stripe, title (up to two lines, 1.25x), then badge, project and the thread's age at 1x.
 * Headers and titles are scaled so they read from 6-10 blocks ({@link WallLayout.Metrics}).
 *
 * <p>Opaque layers drawn in the solid pass with explicit z steps that grow with the distance
 * ({@link ScreenDraw}, {@link MonitorLod#zStep}, as the monitor does), so the wall never ghosts
 * against the block faces behind it; text in front with polygon offset.
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
	/** Dark backing of the board, the frame band around it and the lanes on it. */
	static final int BACK = 0xFF110D0B;
	static final int FRAME = 0xFF2E2620;
	static final int KEYLINE = 0xFF6F5A20;
	static final int LANE = 0xFF1C1612;
	/** A card: warm charcoal on the darker lane, with cream text (T3 Code's dark look); shadow under it; the header's count pill. */
	static final int CARD = 0xFF39312B;
	static final int SHADOW = 0xFF080605;
	static final int PILL = 0xFF17120F;
	static final int CHIP = 0xFF3B3028;
	static final int CREAM = 0xFFF4EFE6;
	static final int INK = 0xFF1F1E1D;
	/** Second-line text on a card (project, age): cream, dimmed. */
	static final int SOFT = 0xFFC4BAAA;
	private static final int MAX_CACHED = 64;
	/** A card shorter than this (still gliding to its size) draws no text. */
	private static final float MIN_TEXT_H = 20f;

	public static final class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		int width;
		int height;
		WallGeometry.Lod lod = WallGeometry.Lod.HIDDEN;
		@Nullable WallBoard board;
		@Nullable Glyphs glyphs;
		float time;
		/** Layer separation for this distance ({@link MonitorLod#zStep}). */
		float zStep = MonitorLod.MIN_Z_STEP;
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
		double distSq = dx * dx + dy * dy + dz * dz;
		s.lod = WallGeometry.Lod.of(distSq, cfg.detailDistance(), cfg.viewDistance());
		s.zStep = MonitorLod.zStep(Math.sqrt(distSq));
		if (s.lod == WallGeometry.Lod.HIDDEN) {
			s.board = null;
			s.glyphs = null;
			return;
		}
		Font font = Minecraft.getInstance().font;
		WallBoard b = walls.board(origin, s.facing, w.width(), w.height());
		b.sync(walls.cards(), w.mode(), theme, w.width() * PPB, w.height() * PPB, font::width, System.currentTimeMillis());
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
				cards.put(s.key(), new FormattedCharSequence[] {seq(s.title()), seq(s.footer()), seq(s.badge().initial()), seq(s.age()), seq(s.title2())});
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
		float z = s.zStep;
		boolean text = s.lod == WallGeometry.Lod.FULL && s.glyphs != null;
		float w = l.width();
		float h = l.height();
		WallLayout.Metrics mt = l.metrics();
		List<WallLayout.Slot> flying = new ArrayList<>();
		for (WallLayout.Slot slot : l.cards()) {
			WallBoard.Anim a = b.anim(slot.key());
			if (a != null && a.flying) {
				flying.add(slot);
			}
		}
		ps.pushPose();
		MonitorRenderer.toFace(ps, s.facing, -z, PPB);
		ps.translate(0, -(s.height - 1) * PPB, 0);

		// opaque layers (solid pass, explicit z steps): keyline, frame, backing, lanes + headers, chips, settled cards; then the flying ones
		collector.order(0).submitCustomGeometry(ps, ScreenDraw.fill(), (pose, vc) -> {
			ScreenDraw.rect(pose, vc, 0, 0, w, h, 0, KEYLINE, light);
			ScreenDraw.rect(pose, vc, 1.5f, 1.5f, w - 1.5f, h - 1.5f, -z, FRAME, light);
			ScreenDraw.rect(pose, vc, WallLayout.FRAME, WallLayout.FRAME, w - WallLayout.FRAME, h - WallLayout.FRAME, -2 * z, BACK, light);
			for (WallLayout.Lane lane : l.lanes()) {
				float x0 = lane.x(), x1 = lane.x() + lane.w();
				ScreenDraw.rect(pose, vc, x0, WallLayout.EDGE, x1, h - WallLayout.EDGE, -3 * z, LANE, light);
				ScreenDraw.rect(pose, vc, x0, WallLayout.EDGE, x1, WallLayout.EDGE + mt.headerH(), -4 * z, 0xFF000000 | lane.color(), light);
				float pw = pill(s.glyphs, lane, mt);
				ScreenDraw.rect(pose, vc, x1 - 3 - pw, WallLayout.EDGE + 3, x1 - 3, WallLayout.EDGE + mt.headerH() - 3, -5 * z, PILL, light);
			}
			for (WallLayout.More m : l.more()) {
				boolean hot = ("#more:" + m.column()).equals(b.hovered());
				ScreenDraw.rect(pose, vc, m.x(), m.y(), m.x() + m.w(), m.y() + m.h(), -4 * z, hot ? theme.brass() : CHIP, light);
			}
			cards(pose, vc, s, b, l.cards(), false, 4 * z, text, light);
			cards(pose, vc, s, b, flying, true, 9 * z, text, light);
		});
		if (text) {
			Glyphs g = s.glyphs;
			Font font = Minecraft.getInstance().font;
			ps.pushPose();
			ps.translate(0, 0, -8 * z);
			OrderedSubmitNodeCollector t = collector.order(1);
			float hs = mt.headScale();
			for (int i = 0; i < l.lanes().size(); i++) {
				WallLayout.Lane lane = l.lanes().get(i);
				float ty = WallLayout.EDGE + (mt.headerH() - 8 * hs) / 2f + 0.5f;
				int fg = onColor(lane.color());
				text(t, ps, g.lanes().get(i), lane.x() + WallLayout.CARD_PAD, ty, hs, fg, light);
				float pw = pill(g, lane, mt);
				float cx = lane.x() + lane.w() - 3 - pw + (pw - g.countW()[i] * hs) / 2f;
				text(t, ps, g.counts().get(i), cx, ty, hs, CREAM, light);
			}
			for (int i = 0; i < l.more().size(); i++) {
				WallLayout.More m = l.more().get(i);
				boolean hot = ("#more:" + m.column()).equals(b.hovered());
				float tw = font.width(g.more().get(i));
				text(t, ps, g.more().get(i), m.x() + (m.w() - tw) / 2f, m.y() + (m.h() - 8) / 2f + 0.5f, 1f, hot ? INK : CREAM, light);
			}
			if (g.empty() != null) {
				float es = mt.titleScale();
				text(t, ps, g.empty(), (w - font.width(g.empty()) * es) / 2f, h / 2f - 4 * es, es, CHIP, light);
			}
			cardText(t, ps, b, g, mt, l.cards(), false, font, light);
			ps.popPose();
			if (!flying.isEmpty()) {
				ps.pushPose();
				ps.translate(0, 0, -13 * z);
				cardText(collector.order(2), ps, b, g, mt, flying, true, font, light);
				ps.popPose();
			}
		}
		ps.popPose();
	}

	/**
	 * Opaque layers of a pass of cards from depth {@code -z0}: outline or shadow, paper, then the
	 * state stripe and the badge.
	 */
	private void cards(PoseStack.Pose pose, VertexConsumer vc, State s, WallBoard b, List<WallLayout.Slot> list, boolean lifted, float z0,
			boolean text, int light) {
		float z = s.zStep;
		for (WallLayout.Slot slot : list) {
			WallBoard.Anim a = b.anim(slot.key());
			if (a == null || a.flying != lifted) {
				continue;
			}
			int ring = ring(s, b, slot, a);
			if (lifted) {
				ScreenDraw.rect(pose, vc, a.x + 3, a.y + 4, a.x + a.w + 3, a.y + a.h + 4, -z0, SHADOW, light);
			} else if (ring != 0) {
				ScreenDraw.rect(pose, vc, a.x - 2, a.y - 2, a.x + a.w + 2, a.y + a.h + 2, -z0, ring, light);
			} else {
				// resting shadow: a dark edge under and to the right, so the card reads as a loose slip on the lane
				ScreenDraw.rect(pose, vc, a.x + 1, a.y + 2, a.x + a.w + 1.5f, a.y + a.h + 2.5f, -z0, SHADOW, light);
			}
			ScreenDraw.rect(pose, vc, a.x, a.y, a.x + a.w, a.y + a.h, -z0 - z, CARD, light);
			ScreenDraw.rect(pose, vc, a.x, a.y, a.x + WallLayout.STRIPE, a.y + a.h, -z0 - 2 * z, 0xFF000000 | WallColumns.color(slot.attention()), light);
			if (text && a.h >= MIN_TEXT_H) {
				float bx = a.x + WallLayout.STRIPE + WallLayout.CARD_PAD;
				float by = a.y + a.h - WallLayout.CARD_PAD - WallLayout.FOOT_H - 0.5f;
				ScreenDraw.rect(pose, vc, bx, by, bx + WallLayout.BADGE, by + WallLayout.BADGE, -z0 - 2 * z, 0xFF000000 | slot.badge().background(), light);
			}
		}
	}

	private void cardText(OrderedSubmitNodeCollector t, PoseStack ps, WallBoard b, Glyphs g, WallLayout.Metrics mt, List<WallLayout.Slot> list,
			boolean lifted, Font font, int light) {
		for (WallLayout.Slot slot : list) {
			WallBoard.Anim a = b.anim(slot.key());
			FormattedCharSequence[] seqs = g.cards().get(slot.key());
			if (a == null || a.flying != lifted || seqs == null || a.h < MIN_TEXT_H) {
				continue;
			}
			float tx = a.x + WallLayout.STRIPE + WallLayout.CARD_PAD;
			float fy = a.y + a.h - WallLayout.CARD_PAD - WallLayout.FOOT_H;
			text(t, ps, seqs[0], tx, a.y + 4, mt.titleScale(), CREAM, light);
			if (!slot.title2().isEmpty()) {
				text(t, ps, seqs[4], tx, a.y + 4 + mt.lineStep(), mt.titleScale(), CREAM, light);
			}
			text(t, ps, seqs[2], tx + (WallLayout.BADGE - font.width(seqs[2])) / 2f + 0.5f, fy, 1f, slot.badge().foreground(), light);
			text(t, ps, seqs[1], tx + WallLayout.BADGE + 3, fy, 1f, SOFT, light);
			if (!slot.age().isEmpty()) {
				float aw = font.width(seqs[3]);
				text(t, ps, seqs[3], a.x + a.w - WallLayout.CARD_PAD - aw, fy, 1f, SOFT, light);
			}
		}
	}

	/** Width of a header's count pill: from the measured glyph run when there is one, else from the digit count (6 px each). */
	private static float pill(@Nullable Glyphs g, WallLayout.Lane lane, WallLayout.Metrics mt) {
		float digits = g != null && lane.column() < g.countW().length ? g.countW()[lane.column()] : 6f * lane.count().length();
		return Math.max(mt.headerH() - 6, digits * mt.headScale() + 8);
	}

	/** Ink on light header colours, cream on dark ones. */
	static int onColor(int rgb) {
		double r = ((rgb >> 16) & 0xFF) / 255.0;
		double g = ((rgb >> 8) & 0xFF) / 255.0;
		double bl = (rgb & 0xFF) / 255.0;
		return 0.2126 * r + 0.7152 * g + 0.0722 * bl > 0.45 ? INK : CREAM;
	}

	/**
	 * Outline of a card (opaque, the solid pass ignores alpha): hover (brass), the arrival glow fading
	 * from brass into the lane, or a ring pulsing between the lane and the needs-you colour; 0 = none.
	 */
	private int ring(State s, WallBoard b, WallLayout.Slot slot, WallBoard.Anim a) {
		if (slot.key().equals(b.hovered())) {
			return theme.brass();
		}
		if (a.glow > 0) {
			return mix(LANE, theme.brass(), Math.min(1f, a.glow / WallBoard.GLOW_S));
		}
		if (slot.attention() == Attention.NEEDS_YOU) {
			return mix(LANE, 0xFF000000 | WallColumns.color(Attention.NEEDS_YOU), 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(s.time * 3.2f)));
		}
		return 0;
	}

	/** Opaque colour {@code t} of the way from {@code a} to {@code b}. */
	static int mix(int a, int b, float t) {
		float u = Math.max(0, Math.min(1, t));
		int r = Math.round(((a >> 16) & 0xFF) * (1 - u) + ((b >> 16) & 0xFF) * u);
		int g = Math.round(((a >> 8) & 0xFF) * (1 - u) + ((b >> 8) & 0xFF) * u);
		int bl = Math.round((a & 0xFF) * (1 - u) + (b & 0xFF) * u);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	/** A glyph run at {@code (x, y)} (board px, its top-left) scaled {@code scale}x. */
	private static void text(OrderedSubmitNodeCollector c, PoseStack ps, FormattedCharSequence t, float x, float y, float scale, int argb, int light) {
		if (scale == 1f) {
			c.submitText(ps, x, y, t, false, Font.DisplayMode.POLYGON_OFFSET, light, 0xFF000000 | argb, 0, 0);
			return;
		}
		ps.pushPose();
		ps.translate(x, y, 0);
		ps.scale(scale, scale, 1f);
		c.submitText(ps, 0, 0, t, false, Font.DisplayMode.POLYGON_OFFSET, light, 0xFF000000 | argb, 0, 0);
		ps.popPose();
	}

	/** Forget cached glyph runs (hot-swap, level change). */
	void clear() {
		glyphs.clear();
	}
}
