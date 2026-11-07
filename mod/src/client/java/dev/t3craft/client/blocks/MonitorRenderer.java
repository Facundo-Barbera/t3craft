// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
// Face transform from StationRenderer; live-log screen (header with status dot, tail of tool rows
// with kit icons, blinking caret while working, pulsing "waiting for you", opaque full-bright
// layers with explicit z steps, off-screen rendering) adapted from MonitorRenderer
// (mod/src/client/java/dev/agentcraft/client/{world/StationRenderer,monitor/MonitorRenderer}.java).
package dev.t3craft.client.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.t3craft.T3Craft;
import dev.t3craft.blocks.Binding;
import dev.t3craft.blocks.MonitorBlock;
import dev.t3craft.blocks.MonitorBlockEntity;
import dev.t3craft.client.chat.ChatTranscript;
import dev.t3craft.debug.PerfStats;
import dev.t3craft.client.workstation.StationScreen;
import dev.t3craft.client.workstation.StationStatus;
import dev.t3craft.client.workstation.WorkstationThread;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Draws a monitor's screen on its front face, full-bright, at one of two levels of detail
 * ({@link MonitorLod}):
 * <ul>
 *   <li><b>NEAR</b>: header (status dot, title, status word), the live log tail when the monitor
 *       holds a thread stream ({@link MonitorLog}: tool rows with kit icon, name, path and +/-),
 *       otherwise the status lines the client already has ({@link MonitorScreens}); footer with a
 *       blinking caret while the agent works or a pulsing "needs you" line.</li>
 *   <li><b>FAR</b>: the face in the status colour, the status word as large as fits and the title.</li>
 * </ul>
 * Every layout decision takes the screen's {@link ScreenExtent} ({@link #extent}): monitors merged into
 * one large screen ({@code MonitorWall}) give their size there and only the bottom-left cell draws.
 * A screen bound to a project or environment ({@code project:}/{@code env:}) shows its
 * {@link ProjectFeed} (header = feed title, rows = threads and their last event).
 *
 * <p><b>Visibility fixes</b> (the old screen vanished up close and was unreadable from afar): the
 * background is opaque geometry in the solid pass (no translucent sorting against the block face),
 * the layer offsets grow with the distance ({@link MonitorLod#zStep}) instead of a fixed 0.003 that
 * z-fights past ~40 blocks, the renderer draws off-screen (it is not tied to its chunk section's
 * culling, which also lets a panel draw from its origin) with a configurable view distance, and
 * the far level trades the log for big text.
 *
 * <p>Glyph runs are rebuilt only when the content version, level or extent changes; a steady screen
 * costs a map lookup per frame plus the draw.
 */
public final class MonitorRenderer implements BlockEntityRenderer<MonitorBlockEntity, MonitorRenderer.State> {
	private static final int MAX_CACHED = 512;
	private static final int MAX_LODS = 4096;
	private static final int CARET_MS = 500;
	private static final int PULSE_MS = 1200;
	private static final int HEADER_BG = 0xFF141414;
	private static final int TEXT = 0xFFE8E8E8;
	private static final int MUTED = 0xFF9A9A9A;

	public static final class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		MonitorLod.@Nullable Layout layout;
		@Nullable Glyphs glyphs;
		float zStep = MonitorLod.MIN_Z_STEP;
		boolean caretOn;
		float pulse;
	}

	/** One tool / text row, ready to draw. */
	record RowGlyph(@Nullable Identifier icon, FormattedCharSequence text, int tint) {
	}

	/** Everything a screen draws, built once per content version. */
	record Glyphs(String cacheKey, long version, int background, int accent,
			@Nullable FormattedCharSequence title, @Nullable FormattedCharSequence status, int statusColor,
			List<RowGlyph> rows, @Nullable FormattedCharSequence footer, int footerColor, boolean caret, float statusScale) {
	}

	private final BlocksClient client;
	private final Map<String, Glyphs> glyphs = new HashMap<>();
	private final Map<Long, MonitorLod.Level> lods = new HashMap<>();

	MonitorRenderer(BlocksClient client) {
		this.client = client;
	}

	/** The screen this block belongs to ({@link BlocksClient#setScreenExtents}; 1×1 by default). */
	ScreenExtent extent(MonitorBlockEntity be) {
		return client.extentOf(be);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return client.monitorSettings().viewDistance();
	}

	@Override
	public boolean shouldRender(MonitorBlockEntity be, Vec3 camera) {
		BlockPos pos = be.getBlockPos();
		if (!Vec3.atCenterOf(pos).closerThan(camera, getViewDistance())) {
			return false;
		}
		// fix/hq-fps: drawn "off screen", so vanilla never frustum-culls monitors; skip screens seen
		// from behind or lying behind the camera (RenderLod.monitor).
		BlockState bs = be.getBlockState();
		Direction f = bs.hasProperty(MonitorBlock.FACING) ? bs.getValue(MonitorBlock.FACING) : Direction.NORTH;
		ScreenExtent extent = extent(be);
		Vec3 c = screenCenter(pos, f, extent);
		Vector3fc look = RenderLod.cameraForward();
		boolean draw = RenderLod.monitor(pos.getX() + 0.5 - camera.x, pos.getZ() + 0.5 - camera.z, f.getStepX(), f.getStepZ(),
				c.x - camera.x, c.y - camera.y, c.z - camera.z, Math.max(extent.width(), extent.height()) / 2.0, look.x(), look.y(), look.z());
		PerfStats.count(draw ? PerfStats.MONITOR_DRAWN : PerfStats.MONITOR_CULLED);
		return draw;
	}

	@Override
	public void extractRenderState(MonitorBlockEntity be, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderState.extractBase(be, state, breakProgress);
		state.glyphs = null;
		state.layout = null;
		BlockState bs = be.getBlockState();
		state.facing = bs.hasProperty(MonitorBlock.FACING) ? bs.getValue(MonitorBlock.FACING) : Direction.NORTH;
		ScreenExtent extent = extent(be);
		if (!extent.origin()) {
			return; // a panel member: the origin block draws the whole screen
		}
		double distance = screenCenter(be.getBlockPos(), state.facing, extent).distanceTo(cameraPosition);
		long posKey = be.getBlockPos().asLong();
		if (lods.size() > MAX_LODS) {
			lods.clear();
		}
		MonitorLod.Level level = MonitorLod.next(lods.get(posKey), distance, extent, client.monitorSettings());
		lods.put(posKey, level);
		MonitorLod.Layout layout = MonitorLod.layout(level, extent);
		state.layout = layout;
		state.zStep = MonitorLod.zStep(distance);
		long ms = System.currentTimeMillis();
		state.caretOn = (ms / CARET_MS) % 2 == 0;
		double ph = (ms % PULSE_MS) / (double) PULSE_MS;
		state.pulse = (float) (0.5 - 0.5 * Math.cos(ph * Math.PI * 2));
		try {
			state.glyphs = glyphsFor(be, layout);
		} catch (RuntimeException e) {
			T3Craft.LOGGER.debug("T3Craft: monitor layout failed", e);
		}
	}

	private Glyphs glyphsFor(MonitorBlockEntity be, MonitorLod.Layout layout) {
		// a merged screen shows its first bound cell's binding; a project/env feed is laid out at the screen's size
		Binding b = client.screenBinding(be);
		MonitorScreens.Shown shown = b.isFeed() ? client.screens().show(b, layout.cols(), layout.rows() + 1) : client.screens().show(b);
		String key = b.kind() == Binding.Kind.THREAD ? b.value() : null;
		MonitorFeeds.Feed feed = key == null ? null : client.feeds().feed(key);
		ChatTranscript.View view = feed != null && feed.live() ? feed.transcript().view() : null;
		WorkstationThread thread = key == null ? null : client.thread(key);
		long version = Objects.hash(shown.version(), view == null ? -1 : view.version(), thread == null ? null : thread.status());
		String cacheKey = shown.id() + "|" + layout.level() + "|" + layout.extent().width() + "x" + layout.extent().height()
				+ (view == null ? "" : "|live");
		Glyphs g = glyphs.get(cacheKey);
		if (g == null || g.version() != version) {
			if (glyphs.size() > MAX_CACHED) {
				glyphs.clear();
			}
			Font font = Minecraft.getInstance().font;
			if (layout.level() == MonitorLod.Level.FAR) {
				g = far(font, cacheKey, version, shown.screen(), thread, layout);
			} else if (view != null) {
				MonitorLog.Model m = MonitorLog.of(view, thread == null ? null : thread.status(), thread == null ? null : thread.title(),
						layout.rows(), layout.cols());
				g = near(font, cacheKey, version, m, layout);
			} else {
				g = nearStatic(font, cacheKey, version, shown.screen(), layout);
			}
			glyphs.put(cacheKey, g);
			PerfStats.count(PerfStats.MONITOR_GLYPHS);
		}
		return g;
	}

	// ---- layouts ------------------------------------------------------------------------------

	private static Glyphs far(Font font, String key, long version, StationScreen screen, @Nullable WorkstationThread t, MonitorLod.Layout l) {
		StationStatus st = screen.status();
		int w = (int) (l.x1() - l.x0());
		String word = st.glyph + " " + st.word;
		float scale = Math.max(1f, Math.min(2.5f, w / (float) Math.max(1, font.width(word))));
		String title = t != null && !t.title().isBlank() ? t.title() : screen.lines().isEmpty() ? "" : screen.lines().getFirst().text();
		return new Glyphs(key, version, ScreenDraw.shade(st.color, 0.22f), 0xFF000000 | st.color,
				seq(ellipsize(font, title, w), TEXT), seq(word, 0xFF000000 | st.color), 0xFF000000 | st.color,
				List.of(), null, 0, false, scale);
	}

	private static Glyphs near(Font font, String key, long version, MonitorLog.Model m, MonitorLod.Layout l) {
		int w = (int) (l.x1() - l.x0());
		StationStatus st = m.status();
		String word = st.word;
		int wordW = font.width(word);
		List<RowGlyph> rows = new ArrayList<>(m.rows().size());
		for (MonitorLog.Row r : m.rows()) {
			rows.add(row(font, r, w));
		}
		FormattedCharSequence footer = m.needsYou() == null ? null : seq(ellipsize(font, "! " + m.needsYou(), w), 0xFF000000 | StationStatus.ATTENTION.color);
		return new Glyphs(key, version, 0xFF0B0D0F, 0xFF000000 | st.color,
				seq(ellipsize(font, m.title(), w - wordW - 14), TEXT), seq(word, 0xFF000000 | st.color), 0xFF000000 | st.color,
				List.copyOf(rows), footer, 0xFF000000 | StationStatus.ATTENTION.color, m.cursor(), 1f);
	}

	/** NEAR without a stream: the status lines the client already has, header style. */
	private static Glyphs nearStatic(Font font, String key, long version, StationScreen s, MonitorLod.Layout l) {
		int w = (int) (l.x1() - l.x0());
		StationStatus st = s.status();
		List<RowGlyph> rows = new ArrayList<>();
		List<StationScreen.Line> lines = s.lines();
		String title = lines.isEmpty() ? "" : lines.getFirst().text();
		for (int i = 1; i < lines.size() && rows.size() < l.rows(); i++) {
			StationScreen.Line line = lines.get(i);
			rows.add(new RowGlyph(null, seq(ellipsize(font, line.text(), w), 0xFF000000 | line.color()), 0));
		}
		int wordW = font.width(st.word);
		return new Glyphs(key, version, 0xFF0B0D0F, 0xFF000000 | st.color, seq(ellipsize(font, title, w - wordW - 14), TEXT),
				seq(st.word, 0xFF000000 | st.color), 0xFF000000 | st.color, List.copyOf(rows), null, 0, false, 1f);
	}

	private static RowGlyph row(Font font, MonitorLog.Row r, int width) {
		Identifier icon = r.icon() == null ? null : Identifier.fromNamespaceAndPath(T3Craft.MOD_ID, "chat/icon_" + r.icon());
		int avail = width - (icon == null ? 0 : 11);
		MutableComponent c = Component.empty();
		String counts = r.hasCounts() ? " +" + Math.max(0, r.adds()) + " -" + Math.max(0, r.dels()) : "";
		int countsW = counts.isEmpty() ? 0 : font.width(counts);
		int used = 0;
		if (!r.name().isEmpty()) {
			String n = ellipsize(font, r.name(), avail - countsW);
			c.append(Component.literal(n).withColor(r.color() & 0xFFFFFF));
			used = font.width(n);
		}
		if (!r.text().isEmpty()) {
			String sep = r.name().isEmpty() ? "" : " ";
			int room = avail - used - countsW - font.width(sep);
			if (room > 6) {
				int col = r.kind() == MonitorLog.Kind.TOOL ? 0xD8D8D8 : r.color() & 0xFFFFFF;
				c.append(Component.literal(sep + ellipsize(font, r.text(), room)).withColor(col));
			}
		}
		if (r.hasCounts()) {
			c.append(Component.literal(" +" + Math.max(0, r.adds())).withColor(MonitorLog.ADD & 0xFFFFFF));
			c.append(Component.literal(" -" + Math.max(0, r.dels())).withColor(MonitorLog.DEL & 0xFFFFFF));
		}
		int tint = r.kind() == MonitorLog.Kind.REQUEST ? 0xFF3A2A00 : r.kind() == MonitorLog.Kind.ERROR ? 0xFF3A1010 : 0;
		return new RowGlyph(icon, c.getVisualOrderText(), tint);
	}

	// ---- draw ---------------------------------------------------------------------------------

	@Override
	public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		Glyphs g = s.glyphs;
		MonitorLod.Layout l = s.layout;
		if (g == null || l == null) {
			return;
		}
		int light = LightCoordsUtil.FULL_BRIGHT;
		float z = s.zStep;
		poseStack.pushPose();
		toFace(poseStack, s.facing, -z, l.ppb());
		// a panel taller than one block extends upwards from its origin (bottom) block
		poseStack.translate(0, -(l.extent().height() - 1) * l.ppb(), 0);
		// layer 0: background and bands (opaque, solid pass)
		boolean near = l.level() == MonitorLod.Level.NEAR;
		collector.order(0).submitCustomGeometry(poseStack, ScreenDraw.fill(), (pose, vc) -> {
			ScreenDraw.rect(pose, vc, l.bezel(), l.bezel(), l.width() - l.bezel(), l.height() - l.bezel(), 0, g.background(), light);
			if (near) {
				float hy = l.bezel() + MonitorLod.HEADER;
				ScreenDraw.rect(pose, vc, l.bezel(), l.bezel(), l.width() - l.bezel(), hy, -z, HEADER_BG, light);
				ScreenDraw.rect(pose, vc, l.bezel(), hy - 1, l.width() - l.bezel(), hy, -z, g.accent(), light);
				ScreenDraw.rect(pose, vc, l.x0(), l.bezel() + 4, l.x0() + 5, l.bezel() + 9, -2 * z, g.accent(), light);
				float y = rowsTop(l, g);
				for (RowGlyph r : g.rows()) {
					if (r.tint() != 0) {
						ScreenDraw.rect(pose, vc, l.bezel(), y, l.width() - l.bezel(), y + MonitorLod.LINE, -z, r.tint(), light);
					}
					y += MonitorLod.LINE;
				}
				if (g.caret() && s.caretOn) {
					float fy = footerY(l);
					ScreenDraw.rect(pose, vc, l.x0(), fy, l.x0() + 5, fy + 8, -z, 0xFF000000 | StationStatus.WORKING.color, light);
				}
			} else {
				ScreenDraw.rect(pose, vc, l.bezel(), l.bezel(), l.width() - l.bezel(), l.bezel() + 3, -z, g.accent(), light);
				ScreenDraw.rect(pose, vc, l.bezel(), l.height() - l.bezel() - 3, l.width() - l.bezel(), l.height() - l.bezel(), -z, g.accent(), light);
			}
		});
		// layer 2: icons and text
		poseStack.translate(0, 0, -2 * z);
		if (near) {
			drawNear(s, g, l, poseStack, collector, light);
		} else {
			drawFar(g, l, poseStack, collector, light);
		}
		poseStack.popPose();
	}

	private static float rowsTop(MonitorLod.Layout l, Glyphs g) {
		// newest row sits just above the footer; fewer rows than room leave the gap at the top
		float bottom = footerY(l) - 1;
		return bottom - g.rows().size() * MonitorLod.LINE;
	}

	private static float footerY(MonitorLod.Layout l) {
		return l.y1() - MonitorLod.FOOTER + 2;
	}

	private static void drawNear(State s, Glyphs g, MonitorLod.Layout l, PoseStack ps, SubmitNodeCollector c, int light) {
		float hy = l.bezel() + 3;
		Font font = Minecraft.getInstance().font;
		if (g.title() != null) {
			text(ps, c, g.title(), l.x0() + 8, hy, TEXT, light);
		}
		if (g.status() != null) {
			text(ps, c, g.status(), l.x1() - font.width(g.status()), hy, g.statusColor(), light);
		}
		float y = rowsTop(l, g);
		for (RowGlyph r : g.rows()) {
			float x = l.x0();
			if (r.icon() != null) {
				TextureAtlasSprite sp = ScreenDraw.sprite(r.icon());
				if (sp != null) {
					float iy = y;
					float ix = x;
					c.order(0).submitCustomGeometry(ps, ScreenDraw.sprites(), (pose, vc) -> ScreenDraw.quad(pose, vc, ix, iy, ix + 9, iy + 9, 0,
							sp.getU0(), sp.getV0(), sp.getU1(), sp.getV1(), 0xFFFFFFFF, light));
				}
				x += 11;
			}
			text(ps, c, r.text(), x, y + 1, TEXT, light);
			y += MonitorLod.LINE;
		}
		if (g.footer() != null) {
			int a = 150 + (int) (105 * s.pulse);
			text(ps, c, g.footer(), l.x0(), footerY(l), (a << 24) | (g.footerColor() & 0xFFFFFF), light);
		}
	}

	private static void drawFar(Glyphs g, MonitorLod.Layout l, PoseStack ps, SubmitNodeCollector c, int light) {
		Font font = Minecraft.getInstance().font;
		float cx = (l.x0() + l.x1()) / 2f;
		float sc = g.statusScale();
		float wordH = 8 * sc;
		float block = wordH + (g.title() == null ? 0 : 4 + 8);
		float top = (l.height() - block) / 2f;
		if (g.status() != null) {
			ps.pushPose();
			ps.translate(cx - font.width(g.status()) * sc / 2f, top, 0);
			ps.scale(sc, sc, 1f);
			text(ps, c, g.status(), 0, 0, g.statusColor(), light);
			ps.popPose();
		}
		if (g.title() != null) {
			text(ps, c, g.title(), cx - font.width(g.title()) / 2f, top + wordH + 4, TEXT, light);
		}
	}

	private static void text(PoseStack ps, SubmitNodeCollector c, FormattedCharSequence t, float x, float y, int argb, int light) {
		c.order(1).submitText(ps, x, y, t, false, Font.DisplayMode.POLYGON_OFFSET, light, argb, 0, 0);
	}

	// ---- helpers ------------------------------------------------------------------------------

	static FormattedCharSequence seq(String s, int argb) {
		return Component.literal(s).withColor(argb & 0xFFFFFF).getVisualOrderText();
	}

	static String ellipsize(Font font, String s, int px) {
		if (px <= 0) {
			return "";
		}
		if (font.width(s) <= px) {
			return s;
		}
		int ell = font.width(MonitorLog.ELLIPSIS);
		return font.plainSubstrByWidth(s, Math.max(0, px - ell)) + MonitorLog.ELLIPSIS;
	}

	/** Centre of the screen face of the block at {@code pos} facing {@code facing} (world space). */
	static Vec3 faceCenter(BlockPos pos, Direction facing) {
		return Vec3.atCenterOf(pos).add(facing.getStepX() * 0.5, 0, facing.getStepZ() * 0.5);
	}

	/**
	 * Centre of a {@code extent} screen whose origin (bottom-left as the viewer faces it) is
	 * {@code pos}: the viewer's right is the facing turned clockwise.
	 */
	static Vec3 screenCenter(BlockPos pos, Direction facing, ScreenExtent extent) {
		Direction right = facing.getCounterClockWise();
		return faceCenter(pos, facing).add(right.getStepX() * (extent.width() - 1) / 2.0, (extent.height() - 1) / 2.0,
				right.getStepZ() * (extent.width() - 1) / 2.0);
	}

	/** Forget cached glyph runs and levels (hot-swap, level change). */
	void clear() {
		glyphs.clear();
		lods.clear();
	}

	/**
	 * Moves {@code poseStack} (at the block origin) onto the front face of a north-facing model turned
	 * to {@code facing}, {@code depth} blocks behind it (negative = in front), in units of
	 * {@code 1/pixelsPerBlock} block: x right and y down as seen by the viewer, origin at the face's
	 * top-left corner. Face-space z is not scaled (blocks; -z faces the viewer).
	 */
	static void toFace(PoseStack poseStack, Direction facing, float depth, float pixelsPerBlock) {
		poseStack.translate(0.5f, 0.5f, 0.5f);
		poseStack.rotateDegrees(Axis.YP, -modelRotation(facing));
		poseStack.translate(-0.5f, -0.5f, -0.5f);
		// north-facing model: the viewer stands north (-Z) looking south, so the viewer's right is -X
		poseStack.translate(1f, 1f, depth);
		poseStack.scale(-1f / pixelsPerBlock, -1f / pixelsPerBlock, 1f);
	}

	/** Blockstate y rotation of the north-facing model (N 0, E 90, S 180, W 270). */
	static float modelRotation(Direction facing) {
		return switch (facing) {
			case EAST -> 90f;
			case SOUTH -> 180f;
			case WEST -> 270f;
			default -> 0f;
		};
	}
}
