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
	/** The dark glass of a standby screen, its status word, and the glass reflections. */
	private static final int STANDBY_BG = 0xFF08090B;
	private static final int STANDBY_TEXT = 0xFF8A929C;
	private static final int SHEEN_1 = 0xFF0F141A;
	private static final int SHEEN_2 = 0xFF0C1015;
	/** The "T3" mark of a standby screen: 5 x 7 units per letter, a unit apart. */
	private static final String[] LOGO_T = {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."};
	private static final String[] LOGO_3 = {"####.", "....#", "....#", ".###.", "....#", "....#", "####."};
	/** A model pixel (1/16 block): how far the frame's lip stands proud of the glass. */
	private static final float LIP = 1f / 16f;

	public static final class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		MonitorLod.@Nullable Layout layout;
		@Nullable Glyphs glyphs;
		float zStep = MonitorLod.MIN_Z_STEP;
		boolean caretOn;
		float pulse;
		/** {@link MonitorLod#breath}: the "needs you" breathing of the header / far face. */
		float breath;
	}

	/** One tool / text row, ready to draw. */
	record RowGlyph(@Nullable Identifier icon, FormattedCharSequence text, int tint) {
	}

	/** Everything a screen draws, built once per content version. */
	record Glyphs(String cacheKey, long version, int background, int accent,
			@Nullable FormattedCharSequence title, @Nullable FormattedCharSequence status, int statusColor,
			List<RowGlyph> rows, @Nullable FormattedCharSequence footer, int footerColor, boolean caret, float statusScale,
			float titleScale, boolean attention, Mode mode, @Nullable FormattedCharSequence chip, int chipW, boolean topAlign,
			float logo) {
	}

	/** What a screen is: a thread's live screen, or one of the dark "standby" screens (no thread behind it). */
	enum Mode {
		LIVE, NO_THREAD, OFFLINE, ARCHIVED
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
		state.breath = MonitorLod.breath(ms);
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
			Mode mode = modeOf(b, shown);
			if (mode != Mode.LIVE) {
				g = standby(font, cacheKey, version, mode, shown.screen(), layout);
			} else if (layout.level() == MonitorLod.Level.FAR) {
				g = far(font, cacheKey, version, shown.screen(), thread, layout);
			} else if (view != null) {
				MonitorLog.Model m = MonitorLog.of(view, thread == null ? null : thread.status(), thread == null ? null : thread.title(),
						layout.rows(), layout.cols());
				g = near(font, cacheKey, version, m, shown.screen(), layout);
			} else {
				g = nearStatic(font, cacheKey, version, shown.screen(), b.isFeed(), layout);
			}
			glyphs.put(cacheKey, g);
			PerfStats.count(PerfStats.MONITOR_GLYPHS);
		}
		return g;
	}

	/** No thread behind the screen (unbound, a thread the client does not know, or a settled one): the dark standby screen. */
	static Mode modeOf(Binding b, MonitorScreens.Shown shown) {
		if (b.isFeed()) {
			return Mode.LIVE;
		}
		if (b.kind() != Binding.Kind.THREAD) {
			return Mode.NO_THREAD;
		}
		if (shown.id().startsWith("#missing")) {
			return Mode.OFFLINE;
		}
		return shown.screen().status() == StationStatus.ARCHIVED ? Mode.ARCHIVED : Mode.LIVE;
	}

	/**
	 * The status chip of the header: the status word and the age the workstation model put on its status line
	 * ("⚙ WORKING · 3m" gives "WORKING · 3m"); just the word when the age is not known.
	 */
	static String chipText(StationStatus st, StationScreen screen) {
		if (screen.lines().size() > 1) {
			String line = screen.lines().get(1).text();
			int i = line.indexOf(" · ");
			if (i >= 0 && line.startsWith(screen.status().glyph) && screen.status() == st) {
				String age = line.substring(i + 3).trim();
				if (!age.isEmpty()) {
					return st.word + " · " + age;
				}
			}
		}
		return st.word;
	}

	/**
	 * The dark screen of a monitor with no thread behind it: a "T3" mark, a big word ({@code NO THREAD},
	 * {@code OFFLINE}, {@code ARCHIVED}) and, for the last two, the thread's name. Same at every distance: the
	 * mark and the word scale with the screen.
	 */
	private static Glyphs standby(Font font, String key, long version, Mode mode, StationScreen screen, MonitorLod.Layout l) {
		String word = mode == Mode.NO_THREAD ? "NO THREAD" : mode == Mode.OFFLINE ? "OFFLINE" : "ARCHIVED";
		int wordColor = mode == Mode.NO_THREAD ? STANDBY_TEXT : mode == Mode.OFFLINE ? 0xFF8FA3BA : 0xFF8A8A8A;
		int logoColor = mode == Mode.NO_THREAD ? 0xFF2F7F8A : mode == Mode.OFFLINE ? 0xFF4A5A70 : 0xFF59606A;
		String title = mode == Mode.NO_THREAD || screen.lines().isEmpty() ? "" : screen.lines().getFirst().text();
		float w = l.x1() - l.x0();
		float h = l.y1() - l.y0();
		// the mark: 11 x 7 units, at most 40 % of the height and 28 % of the width
		float u = Math.max(1f, Math.min(h * 0.40f / 7f, w * 0.28f / 11f));
		float rest = h - 9 * u;
		int wordW = Math.max(1, font.width(word));
		float ws = Math.max(1f, Math.min(3f, Math.min(w * 0.8f / wordW, rest * (title.isEmpty() ? 0.9f : 0.55f) / 8f)));
		float ts = Math.min(1.5f, Math.max(1f, ws * 0.5f));
		String shownTitle = title.isEmpty() ? "" : ellipsize(font, title, (int) (w / ts));
		return new Glyphs(key, version, STANDBY_BG, logoColor, shownTitle.isEmpty() ? null : seq(shownTitle, 0xFF6F7680), plain(word), wordColor,
				List.of(), null, 0, false, ws, ts, false, mode, null, 0, false, u);
	}

	// ---- layouts ------------------------------------------------------------------------------

	private static Glyphs far(Font font, String key, long version, StationScreen screen, @Nullable WorkstationThread t, MonitorLod.Layout l) {
		StationStatus st = screen.status();
		int w = (int) (l.x1() - l.x0());
		String word = st.glyph + " " + st.word;
		String title = t != null && !t.title().isBlank() ? t.title() : screen.lines().isEmpty() ? "" : screen.lines().getFirst().text();
		MonitorLod.FarText ft = MonitorLod.farText(l, font.width(word), title.isEmpty() ? 0 : font.width(title));
		String shown = title.isEmpty() ? "" : ellipsize(font, title, (int) (w / Math.max(1f, ft.titleScale())));
		// status word uncoloured: the draw colours it (and breathes it when the thread needs the user)
		return new Glyphs(key, version, ScreenDraw.shade(st.color, 0.22f), 0xFF000000 | st.color,
				shown.isEmpty() ? null : seq(shown, TEXT), plain(word), 0xFF000000 | st.color,
				List.of(), null, 0, false, ft.statusScale(), ft.titleScale(), st == StationStatus.ATTENTION, Mode.LIVE, null, 0, false, 0f);
	}

	private static Glyphs near(Font font, String key, long version, MonitorLog.Model m, StationScreen screen, MonitorLod.Layout l) {
		int w = (int) (l.x1() - l.x0());
		StationStatus st = m.status();
		String word = chipText(st, screen);
		int chipW = font.width(word) + 6;
		List<RowGlyph> rows = new ArrayList<>(m.rows().size());
		for (MonitorLog.Row r : m.rows()) {
			rows.add(row(font, r, w));
		}
		FormattedCharSequence footer = m.needsYou() == null ? null : seq(ellipsize(font, "! " + m.needsYou(), w), 0xFF000000 | StationStatus.ATTENTION.color);
		return new Glyphs(key, version, 0xFF0B0D0F, 0xFF000000 | st.color,
				seq(ellipsize(font, m.title(), w), TEXT), plain(word), 0xFF000000 | st.color,
				List.copyOf(rows), footer, 0xFF000000 | StationStatus.ATTENTION.color, m.cursor(), 1f, 1f, st == StationStatus.ATTENTION,
				Mode.LIVE, plain(word), chipW, false, 0f);
	}

	/**
	 * NEAR without a stream: the status lines the client already has, header style. A thread screen puts the
	 * status line (and the age) in the header's chip and stacks the rest under the header (branch, last message);
	 * a project / environment feed ({@code feed}) keeps its own first line as the title and all the rest as rows.
	 */
	private static Glyphs nearStatic(Font font, String key, long version, StationScreen s, boolean feed, MonitorLod.Layout l) {
		int w = (int) (l.x1() - l.x0());
		StationStatus st = s.status();
		List<RowGlyph> rows = new ArrayList<>();
		List<StationScreen.Line> lines = s.lines();
		String title = lines.isEmpty() ? "" : lines.getFirst().text();
		for (int i = feed ? 1 : 2; i < lines.size() && rows.size() < l.rows(); i++) {
			StationScreen.Line line = lines.get(i);
			rows.add(new RowGlyph(null, seq(ellipsize(font, line.text(), w), 0xFF000000 | line.color()), 0));
		}
		String chip = feed ? null : chipText(st, s);
		int chipW = chip == null ? 0 : font.width(chip) + 6;
		return new Glyphs(key, version, 0xFF0B0D0F, 0xFF000000 | st.color, seq(ellipsize(font, title, chip == null ? w - font.width(st.word) - 14 : w), TEXT),
				plain(st.word), 0xFF000000 | st.color, List.copyOf(rows), null, 0, false, 1f, 1f, st == StationStatus.ATTENTION,
				Mode.LIVE, chip == null ? null : plain(chip), chipW, true, 0f);
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
		// a thread that needs the user breathes: header band, status word and the far face's bands
		int accent = g.attention() ? MonitorLod.breathe(g.accent(), g.background(), s.breath) : g.accent();
		int statusColor = g.attention() ? MonitorLod.breathe(g.statusColor(), g.background(), s.breath) : g.statusColor();
		poseStack.pushPose();
		toFace(poseStack, s.facing, -z, l.ppb());
		// a panel taller than one block extends upwards from its origin (bottom) block
		poseStack.translate(0, -(l.extent().height() - 1) * l.ppb(), 0);
		if (g.mode() != Mode.LIVE) {
			drawStandby(s, g, l, poseStack, collector, light, z);
			drawChin(s, g, l, poseStack, collector, light, z, g.mode() == Mode.NO_THREAD ? MonitorLod.breathe(0xFF3A4048, STANDBY_BG, s.breath) : g.mode() == Mode.OFFLINE ? 0xFF4A5A70 : 0xFF23262B);
			poseStack.popPose();
			return;
		}
		// layer 0: background and bands (opaque, solid pass); the glass ends where the 3 px chin starts
		boolean near = l.level() == MonitorLod.Level.NEAR;
		float glassBottom = l.height() - l.chin();
		collector.order(0).submitCustomGeometry(poseStack, ScreenDraw.fill(), (pose, vc) -> {
			ScreenDraw.rect(pose, vc, l.bezel(), l.bezel(), l.width() - l.bezel(), glassBottom, 0, g.background(), light);
			if (near) {
				float hy = l.bezel() + MonitorLod.HEADER;
				ScreenDraw.rect(pose, vc, l.bezel(), l.bezel(), l.width() - l.bezel(), hy, -z, HEADER_BG, light);
				if (g.chip() != null) {
					// the status band under the title: the state's colour, full width, dark text (reads from further than coloured text)
					ScreenDraw.rect(pose, vc, l.bezel(), hy, l.width() - l.bezel(), hy + MonitorLod.STATUS_BAND, -z, accent, light);
				} else {
					ScreenDraw.rect(pose, vc, l.bezel(), hy - 2, l.width() - l.bezel(), hy, -z, accent, light);
					ScreenDraw.rect(pose, vc, l.x0(), l.bezel() + 4, l.x0() + 5, l.bezel() + 9, -2 * z, accent, light);
				}
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
				ScreenDraw.rect(pose, vc, l.bezel(), l.bezel(), l.width() - l.bezel(), l.bezel() + 3, -z, accent, light);
				ScreenDraw.rect(pose, vc, l.bezel(), glassBottom - 3, l.width() - l.bezel(), glassBottom, -z, accent, light);
			}
		});
		// layer 3: icons and text, one step above the chip and the bands
		poseStack.translate(0, 0, -3 * z);
		if (near) {
			drawNear(s, g, l, poseStack, collector, light, statusColor);
		} else {
			drawFar(g, l, poseStack, collector, light, statusColor);
		}
		poseStack.translate(0, 0, 3 * z);
		drawChin(s, g, l, poseStack, collector, light, z, statusColor);
		poseStack.popPose();
	}

	/**
	 * The 3 px chin of the screen's frame: a status LED at the right (the state's colour) and the "T3CRAFT" mark at
	 * the left, in muted steel. They sit on the chin's face, which stands one model pixel proud of the glass.
	 */
	private static void drawChin(State s, Glyphs g, MonitorLod.Layout l, PoseStack ps, SubmitNodeCollector c, int light, float z, int led) {
		float mp = l.modelPx();
		float depth = -(LIP + z);
		float x1 = l.width() - 3 * mp;
		float x0 = x1 - 2 * mp;
		// the visible chin band is the 2 model px above the foot: y from height - 4 mp to height - 2 mp
		float y0 = l.height() - 3.5f * mp;
		float y1 = l.height() - 2.5f * mp;
		ps.pushPose();
		ps.translate(0, 0, depth);
		c.order(0).submitCustomGeometry(ps, ScreenDraw.fill(), (pose, vc) -> {
			// a dark housing one unit round the LED, then the LED
			ScreenDraw.rect(pose, vc, x0 - mp * 0.5f, y0 - mp * 0.25f, x1 + mp * 0.5f, y1 + mp * 0.25f, 0, 0xFF111316, light);
			ScreenDraw.rect(pose, vc, x0, y0, x1, y1, -z, 0xFF000000 | led, light);
		});
		float bs = Math.min(1f, 1.7f * mp / 7f);
		if (bs >= 0.6f) {
			ps.pushPose();
			ps.translate(3 * mp, l.height() - 3 * mp - 3.5f * bs, -z);
			ps.scale(bs, bs, 1f);
			text(ps, c, plain("T3CRAFT"), 0, 0, 0xFF8A919B, light);
			ps.popPose();
		}
		ps.popPose();
	}

	/**
	 * A standby screen: very dark glass with two slanted reflections, the "T3" mark in the middle and the big word
	 * under it (and the thread's name, if it has one). Nothing blinks.
	 */
	private static void drawStandby(State s, Glyphs g, MonitorLod.Layout l, PoseStack ps, SubmitNodeCollector c, int light, float z) {
		float x0 = l.bezel();
		float x1 = l.width() - l.bezel();
		float y0 = l.bezel();
		float y1 = l.height() - l.chin();
		float iw = x1 - x0;
		float u = g.logo();
		Font font = Minecraft.getInstance().font;
		float ws = g.statusScale();
		float ts = Math.max(1f, g.titleScale());
		float logoH = 7 * u;
		float gap = 2 * u;
		float titleGap = g.title() == null ? 0 : gap * 0.6f;
		float block = logoH + gap + 8 * ws + titleGap + (g.title() == null ? 0 : 8 * ts);
		float top = y0 + ((y1 - y0) - block) / 2f;
		float cx = (x0 + x1) / 2f;
		float logoX = cx - 11 * u / 2f;
		// reflections: parallelograms slanted to the left, kept inside the glass
		float dx = Math.min(0.2f * iw, 0.5f * (y1 - y0));
		float a = x0 + dx + 0.12f * iw;
		float b = a + 0.16f * iw;
		c.order(0).submitCustomGeometry(ps, ScreenDraw.fill(), (pose, vc) -> {
			ScreenDraw.rect(pose, vc, x0, y0, x1, y1, 0, g.background(), light);
			ScreenDraw.slant(pose, vc, a, a + 0.10f * iw, a - dx, a + 0.10f * iw - dx, y0, y1, -z, SHEEN_1, light);
			ScreenDraw.slant(pose, vc, b, b + 0.04f * iw, b - dx, b + 0.04f * iw - dx, y0, y1, -z, SHEEN_2, light);
			for (int row = 0; row < 7; row++) {
				for (int col = 0; col < 5; col++) {
					if (LOGO_T[row].charAt(col) == '#') {
						ScreenDraw.rect(pose, vc, logoX + col * u, top + row * u, logoX + (col + 1) * u, top + (row + 1) * u, -2 * z, g.accent(), light);
					}
					if (LOGO_3[row].charAt(col) == '#') {
						ScreenDraw.rect(pose, vc, logoX + (6 + col) * u, top + row * u, logoX + (7 + col) * u, top + (row + 1) * u, -2 * z, g.accent(), light);
					}
				}
			}
		});
		ps.pushPose();
		ps.translate(0, 0, -3 * z);
		float wordTop = top + logoH + gap;
		if (g.status() != null) {
			ps.pushPose();
			ps.translate(cx - font.width(g.status()) * ws / 2f, wordTop, 0);
			ps.scale(ws, ws, 1f);
			text(ps, c, g.status(), 0, 0, g.statusColor(), light);
			ps.popPose();
		}
		if (g.title() != null) {
			ps.pushPose();
			ps.translate(cx - font.width(g.title()) * ts / 2f, wordTop + 8 * ws + titleGap, 0);
			ps.scale(ts, ts, 1f);
			text(ps, c, g.title(), 0, 0, 0xFF6F7680, light);
			ps.popPose();
		}
		ps.popPose();
	}

	private static float rowsTop(MonitorLod.Layout l, Glyphs g) {
		if (g.topAlign()) {
			// static status screens read top-down, right under the header
			return l.bezel() + MonitorLod.HEADER + (g.chip() != null ? MonitorLod.STATUS_BAND : 0) + 3;
		}
		// newest row sits just above the footer; fewer rows than room leave the gap at the top
		float bottom = footerY(l) - 1;
		return bottom - g.rows().size() * MonitorLod.LINE;
	}

	private static float footerY(MonitorLod.Layout l) {
		return l.y1() - MonitorLod.FOOTER + 2;
	}

	private static void drawNear(State s, Glyphs g, MonitorLod.Layout l, PoseStack ps, SubmitNodeCollector c, int light, int statusColor) {
		float hy = l.bezel() + 3;
		Font font = Minecraft.getInstance().font;
		if (g.title() != null) {
			text(ps, c, g.title(), g.chip() != null ? l.x0() : l.x0() + 8, hy, TEXT, light);
		}
		if (g.chip() != null) {
			text(ps, c, g.chip(), l.x0(), l.bezel() + MonitorLod.HEADER + 2, 0xFF0B0D0F, light);
		} else if (g.status() != null) {
			text(ps, c, g.status(), l.x1() - font.width(g.status()), hy, statusColor, light);
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

	private static void drawFar(Glyphs g, MonitorLod.Layout l, PoseStack ps, SubmitNodeCollector c, int light, int statusColor) {
		Font font = Minecraft.getInstance().font;
		float cx = (l.x0() + l.x1()) / 2f;
		float sc = g.statusScale();
		float ts = Math.max(1f, g.titleScale());
		float wordH = 8 * sc;
		float block = wordH + (g.title() == null ? 0 : MonitorLod.FAR_GAP + 8 * ts);
		float top = l.bezel() + ((l.height() - l.bezel() - l.chin()) - block) / 2f;
		if (g.status() != null) {
			ps.pushPose();
			ps.translate(cx - font.width(g.status()) * sc / 2f, top, 0);
			ps.scale(sc, sc, 1f);
			text(ps, c, g.status(), 0, 0, statusColor, light);
			ps.popPose();
		}
		if (g.title() != null) {
			ps.pushPose();
			ps.translate(cx - font.width(g.title()) * ts / 2f, top + wordH + MonitorLod.FAR_GAP, 0);
			ps.scale(ts, ts, 1f);
			text(ps, c, g.title(), 0, 0, TEXT, light);
			ps.popPose();
		}
	}

	private static void text(PoseStack ps, SubmitNodeCollector c, FormattedCharSequence t, float x, float y, int argb, int light) {
		c.order(1).submitText(ps, x, y, t, false, Font.DisplayMode.POLYGON_OFFSET, light, argb, 0, 0);
	}

	// ---- helpers ------------------------------------------------------------------------------

	/** Uncoloured text: takes the colour passed at draw time. */
	static FormattedCharSequence plain(String s) {
		return Component.literal(s).getVisualOrderText();
	}

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
