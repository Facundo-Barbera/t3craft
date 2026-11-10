// Idea ported from AgentCraft's /status command (https://github.com/blendi-remade/agentcraft, MIT): a one-glance summary of
// the whole workspace. No code is copied; the screen is drawn with this module's own opaque screen pipeline (ScreenDraw).
package dev.t3craft.client.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.ThreadRow;
import dev.t3craft.client.workstation.AgeFormat;
import dev.t3craft.client.workstation.StationStatus;
import dev.t3craft.client.workstation.WorkstationThread;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The status screen (feat/status-screen): a large screen bound {@code beacon} (the whole HQ's aggregate, as the hub lantern's
 * lamp) shows the workspace at a glance instead of one thread's log. Five tiles with a big number each, in the colours of
 * {@code ThreadState} (needs you, error, working, done, idle, most urgent first), and under them three cells: the pull
 * requests waiting for review, the active projects, and the thread that has waited longest for the player.
 *
 * <p>Everything comes from what the client already has: the NPC-derived {@link WorkstationThread}s (state, project), the
 * thread directory's rows ({@link ThreadRow#activityMs()}) and {@link MergeFeed} (the same pending pull requests the
 * merge station counts). T3 does not tell the client how much a thread cost, so no spend or token figure is shown.
 *
 * <p>The screen reads the same at every distance (like the standby screens): big numbers, no log. {@link Summary} is a pure
 * value; {@link #panel} lays it out once per change and size, and {@link #draw} only replays the rectangles and texts.
 * Designed for 9 x 3 blocks (readable from 6 to 10 blocks); works from about 5 x 3 up.
 */
final class StatusScreen {
	private static final int MAX_PANELS = 16;

	private static final int GLASS = 0xFF0B0D0F;
	private static final int CELL = 0xFF14181C;
	private static final int CELL_EMPTY = 0xFF101316;
	private static final int TEXT = 0xFFE8E8E8;
	private static final int MUTED = 0xFF8A9096;
	private static final int ZERO_NUMBER = 0xFF4A5056;
	private static final int PR_COLOR = 0xFF55FF55;
	private static final int PROJECT_COLOR = 0xFF55FFFF;
	/** Gap between tiles and cells (face pixels at 64 per block), and the padding inside the glass. */
	private static final float GAP = 6f;
	private static final float PAD = 5f;
	/** Height of a tile's colour stripe. */
	private static final float STRIPE = 5f;

	// ---- the data -----------------------------------------------------------------------------

	/**
	 * What the screen says. Counts of the threads that are not archived, the pull requests that are pending (open or draft),
	 * projects with a thread working, waiting or failing ({@code projectsActive}) out of the projects that have a live thread
	 * ({@code projectsTotal}), and the thread that needs the player and has been quiet the longest (its title, and how long
	 * when T3 gave a time).
	 */
	record Summary(int attention, int error, int working, int done, int idle, int prs, int projectsActive, int projectsTotal,
			@Nullable String oldestTitle, @Nullable String oldestAge) {
		static final Summary EMPTY = new Summary(0, 0, 0, 0, 0, 0, 0, 0, null, null);

		int total() {
			return attention + error + working + done + idle;
		}

		int count(StationStatus s) {
			return switch (s) {
				case ATTENTION -> attention;
				case ERROR -> error;
				case WORKING -> working;
				case DONE -> done;
				case IDLE -> idle;
				case ARCHIVED -> 0;
			};
		}

		/** The most urgent state that has a thread: the LED of the screen's frame. */
		StationStatus top() {
			for (StationStatus s : TILES) {
				if (count(s) > 0) {
					return s;
				}
			}
			return StationStatus.IDLE;
		}
	}

	/** The tiles, left to right: the same urgency order as {@link ProjectFeed}. */
	static final List<StationStatus> TILES = List.of(StationStatus.ATTENTION, StationStatus.ERROR, StationStatus.WORKING,
			StationStatus.DONE, StationStatus.IDLE);

	/**
	 * The summary of {@code threads} (archived ones are not counted). {@code rows} give the time of each thread's last activity
	 * (for how long the oldest waiting thread has waited); {@code prs} are the entries of {@link MergeFeed#collect}, of which
	 * the pending ones are counted. Pure and deterministic: input order never matters.
	 */
	static Summary summarize(Collection<WorkstationThread> threads, Collection<ThreadRow> rows, Collection<MergeFeed.Entry> prs, long nowMs) {
		int[] n = new int[StationStatus.values().length];
		Set<String> projects = new HashSet<>();
		Set<String> active = new HashSet<>();
		List<WorkstationThread> waiting = new ArrayList<>();
		for (WorkstationThread t : threads) {
			if (t.archived()) {
				continue;
			}
			n[t.status().ordinal()]++;
			projects.add(t.groupId());
			StationStatus s = t.status();
			if (s == StationStatus.ATTENTION || s == StationStatus.ERROR || s == StationStatus.WORKING) {
				active.add(t.groupId());
			}
			if (s == StationStatus.ATTENTION) {
				waiting.add(t);
			}
		}
		int pending = 0;
		for (MergeFeed.Entry e : prs) {
			if (e.pending()) {
				pending++;
			}
		}
		String title = null;
		String age = null;
		if (!waiting.isEmpty()) {
			Map<String, Long> since = new HashMap<>();
			for (ThreadRow r : rows) {
				if (r.activityMs() != null) {
					since.put(r.key(), r.activityMs());
				}
			}
			// longest wait first; threads without a time after those with one; then title and key (stable)
			waiting.sort(Comparator.comparingLong((WorkstationThread t) -> since.getOrDefault(t.key(), Long.MAX_VALUE))
					.thenComparing(WorkstationThread::title).thenComparing(WorkstationThread::key));
			WorkstationThread oldest = waiting.get(0);
			title = oldest.title().isBlank() ? "(untitled)" : oldest.title();
			Long at = since.get(oldest.key());
			age = at == null ? null : AgeFormat.format(nowMs - at);
		}
		return new Summary(n[StationStatus.ATTENTION.ordinal()], n[StationStatus.ERROR.ordinal()], n[StationStatus.WORKING.ordinal()],
				n[StationStatus.DONE.ordinal()], n[StationStatus.IDLE.ordinal()], pending, active.size(), projects.size(), title, age);
	}

	// ---- the layout ---------------------------------------------------------------------------

	/** A flat rectangle; {@code layer} is its depth step (0 = the glass), {@code pulse}: breathes over {@code bg}. */
	record Rect(float x0, float y0, float x1, float y1, int layer, int argb, boolean pulse) {
	}

	/** Uncoloured text at {@code scale}; {@code argb} is applied at draw time (and breathes over {@code bg} when {@code pulse}). */
	record Text(FormattedCharSequence seq, float x, float y, float scale, int argb, int bg, boolean pulse) {
	}

	/** A laid-out screen: {@code src} is what it was built from, {@code key} the size. */
	record Panel(Summary src, String key, List<Rect> rects, List<Text> texts) {
	}

	/**
	 * The block's display options as the layout needs them (T10, {@link MonitorOptions}): {@code textScale} multiplies
	 * the largest size the numbers and labels may take (still limited to what fits their cell), {@code accent} (ARGB, 0 =
	 * the default colours) replaces the colours of the PRS TO REVIEW and ACTIVE PROJECTS cells. The tiles keep their
	 * state colours: those are the meaning of the screen.
	 */
	record Style(float textScale, int accent) {
		static final Style DEFAULT = new Style(1f, 0);

		Style {
			textScale = textScale <= 0 ? 1f : Math.max(0.5f, Math.min(2f, textScale));
		}

		/** The style of a screen with {@code o}; the monitor's {@code textSize} density is the inverse of the text scale. */
		static Style of(MonitorOptions o, ChatTheme theme) {
			return o.isDefault() ? DEFAULT : new Style(1f / o.textSize().density, BlockAccent.argb(o.accent(), theme));
		}

		String key() {
			return textScale + ":" + Integer.toHexString(accent);
		}
	}

	private final Map<String, Panel> panels = new HashMap<>();

	/** The layout of {@code s} on a screen of layout {@code l}, rebuilt only when the summary, the size or the style changed. */
	Panel panel(Font font, Summary s, MonitorLod.Layout l, Style style) {
		String key = l.extent().width() + "x" + l.extent().height() + "|" + style.key();
		Panel p = panels.get(key);
		if (p == null || !p.src().equals(s)) {
			if (panels.size() > MAX_PANELS) {
				panels.clear();
			}
			p = build(font, s, l, key, style);
			panels.put(key, p);
		}
		return p;
	}

	void clear() {
		panels.clear();
	}

	/** Lays {@code s} out in the glass of {@code l} (face pixels, origin at the screen's top left). Package-private for the mock. */
	static Panel build(Font font, Summary s, MonitorLod.Layout l, String key, Style style) {
		float ts = style.textScale();
		List<Rect> rects = new ArrayList<>();
		List<Text> texts = new ArrayList<>();
		float k = l.ppb() / 64f;
		float gap = GAP * k;
		float x0 = l.bezel() + PAD * k;
		float x1 = l.width() - l.bezel() - PAD * k;
		float y0 = l.bezel() + PAD * k;
		float y1 = l.height() - l.chin() - PAD * 0.8f * k;
		rects.add(new Rect(l.bezel(), l.bezel(), l.width() - l.bezel(), l.height() - l.chin(), 0, GLASS, false));
		float tilesH = Math.round((y1 - y0 - gap) * 0.60f);
		float tw = (x1 - x0 - gap * (TILES.size() - 1)) / TILES.size();
		for (int i = 0; i < TILES.size(); i++) {
			StationStatus st = TILES.get(i);
			float tx = x0 + i * (tw + gap);
			tile(font, rects, texts, st, s.count(st), tx, y0, tx + tw, y0 + tilesH, k, ts);
		}
		float sy0 = y0 + tilesH + gap;
		float prW = Math.round((x1 - x0) * 0.22f);
		float pjW = prW;
		float prX = x0;
		float pjX = prX + prW + gap;
		float oldX = pjX + pjW + gap;
		boolean prsHere = s.prs() > 0;
		int prColor = style.accent() != 0 ? style.accent() : PR_COLOR;
		int projectColor = style.accent() != 0 ? style.accent() : PROJECT_COLOR;
		cell(font, rects, texts, prX, sy0, prX + prW, y1, k, ts, prsHere ? prColor : ZERO_NUMBER, false, "PRS TO REVIEW", MUTED,
				Integer.toString(s.prs()), prsHere ? prColor : ZERO_NUMBER);
		boolean projHere = s.projectsActive() > 0;
		cell(font, rects, texts, pjX, sy0, pjX + pjW, y1, k, ts, projHere ? projectColor : ZERO_NUMBER, false, "ACTIVE PROJECTS", MUTED,
				s.projectsActive() + "/" + s.projectsTotal(), projHere ? projectColor : ZERO_NUMBER);
		oldest(font, rects, texts, s, oldX, sy0, x1, y1, k, ts);
		return new Panel(s, key, List.copyOf(rects), List.copyOf(texts));
	}

	private static void tile(Font font, List<Rect> rects, List<Text> texts, StationStatus st, int count, float x0, float y0, float x1, float y1, float k,
			float ts) {
		boolean on = count > 0;
		int color = 0xFF000000 | st.color;
		boolean pulse = on && st == StationStatus.ATTENTION;
		int bg = on ? ScreenDraw.shade(st.color, 0.22f) : CELL_EMPTY;
		rects.add(new Rect(x0, y0, x1, y1, 1, bg, false));
		rects.add(new Rect(x0, y0, x1, y0 + STRIPE * k, 2, on ? color : ScreenDraw.shade(st.color, 0.35f), pulse));
		String num = Integer.toString(count);
		String label = st.word;
		float w = x1 - x0 - 8 * k;
		float labelScale = Math.max(0.75f, Math.min(1.75f * k * ts, w / Math.max(1, font.width(label))));
		float numScale = Math.min(6f * k * ts, w / Math.max(1, font.width(num)));
		float areaTop = y0 + STRIPE * k;
		float areaH = y1 - areaTop;
		float numH = 8 * numScale;
		float labelH = 8 * labelScale;
		float inner = 5 * k;
		// shrink the number when the tile is short (small screens)
		if (numH + inner + labelH > areaH - 6 * k) {
			numScale = Math.max(1f, (areaH - 6 * k - inner - labelH) / 8f);
			numH = 8 * numScale;
		}
		float top = areaTop + (areaH - (numH + inner + labelH)) / 2f;
		float cx = (x0 + x1) / 2f;
		texts.add(new Text(MonitorRenderer.plain(num), cx - font.width(num) * numScale / 2f, top, numScale, on ? color : ZERO_NUMBER, bg, pulse));
		texts.add(new Text(MonitorRenderer.plain(label), cx - font.width(label) * labelScale / 2f, top + numH + inner, labelScale,
				on ? TEXT : MUTED, bg, false));
	}

	/** A cell of the strip: an accent bar on the left, a small label and a big value. */
	private static void cell(Font font, List<Rect> rects, List<Text> texts, float x0, float y0, float x1, float y1, float k, float ts, int accent,
			boolean pulse, String label, int labelColor, String value, int valueColor) {
		rects.add(new Rect(x0, y0, x1, y1, 1, CELL, false));
		rects.add(new Rect(x0, y0, x0 + 4 * k, y1, 2, accent, pulse));
		float left = x0 + 4 * k + 7 * k;
		float w = x1 - left - 4 * k;
		float labelScale = Math.max(0.75f, Math.min(1.25f * k * ts, w / Math.max(1, font.width(label))));
		float labelH = 8 * labelScale;
		float h = y1 - y0;
		float valueScale = Math.max(1f, Math.min(4f * k * ts, Math.min((h - labelH - 14 * k) / 8f, w / Math.max(1, font.width(value)))));
		float block = labelH + 4 * k + 8 * valueScale;
		float top = y0 + (h - block) / 2f;
		texts.add(new Text(MonitorRenderer.plain(label), left, top, labelScale, labelColor, CELL, false));
		texts.add(new Text(MonitorRenderer.plain(value), left, top + labelH + 4 * k, valueScale, valueColor, CELL, false));
	}

	/** The widest cell: who has waited longest for the player (or that nobody is waiting). */
	private static void oldest(Font font, List<Rect> rects, List<Text> texts, Summary s, float x0, float y0, float x1, float y1, float k, float ts) {
		boolean any = s.oldestTitle() != null;
		int accent = any ? 0xFF000000 | StationStatus.ATTENTION.color : s.total() == 0 ? ZERO_NUMBER : 0xFF000000 | StationStatus.DONE.color;
		rects.add(new Rect(x0, y0, x1, y1, 1, CELL, false));
		rects.add(new Rect(x0, y0, x0 + 4 * k, y1, 2, accent, any));
		float left = x0 + 4 * k + 7 * k;
		float w = x1 - left - 4 * k;
		String label = any ? (s.oldestAge() == null ? "OLDEST WAITING" : "OLDEST WAITING · " + s.oldestAge()) : "WAITING FOR YOU";
		float labelScale = Math.max(0.75f, Math.min(1.25f * k * ts, w / Math.max(1, font.width(label))));
		String body = any ? s.oldestTitle() : s.total() == 0 ? "NO THREADS" : "NOBODY";
		float h = y1 - y0;
		float bodyScale = Math.max(1f, Math.min(2f * k * ts, (h - 8 * labelScale - 14 * k) / 8f));
		String shown = MonitorRenderer.ellipsize(font, body, (int) (w / bodyScale));
		float block = 8 * labelScale + 4 * k + 8 * bodyScale;
		float top = y0 + (h - block) / 2f;
		texts.add(new Text(MonitorRenderer.plain(label), left, top, labelScale, any ? 0xFF000000 | StationStatus.ATTENTION.color : MUTED, CELL, any));
		texts.add(new Text(MonitorRenderer.plain(shown), left, top + 8 * labelScale + 4 * k, bodyScale,
				any ? TEXT : s.total() == 0 ? MUTED : 0xFF000000 | StationStatus.DONE.color, CELL, false));
	}

	// ---- draw ---------------------------------------------------------------------------------

	/**
	 * Draws {@code p} on the face the pose stack is already on ({@link MonitorRenderer#toFace}, the panel's origin at the
	 * top-left): the rectangles in one opaque batch, then the texts three steps above them.
	 */
	static void draw(Panel p, PoseStack ps, SubmitNodeCollector c, int light, float z, float breath) {
		c.order(0).submitCustomGeometry(ps, ScreenDraw.fill(), (pose, vc) -> {
			for (Rect r : p.rects()) {
				int argb = r.pulse() ? MonitorLod.breathe(r.argb(), CELL, breath) : r.argb();
				ScreenDraw.rect(pose, vc, r.x0(), r.y0(), r.x1(), r.y1(), -r.layer() * z, argb, light);
			}
		});
		ps.pushPose();
		ps.translate(0, 0, -3 * z);
		for (Text t : p.texts()) {
			ps.pushPose();
			ps.translate(t.x(), t.y(), 0);
			ps.scale(t.scale(), t.scale(), 1f);
			MonitorRenderer.text(ps, c, t.seq(), 0, 0, t.pulse() ? MonitorLod.breathe(t.argb(), t.bg(), breath) : t.argb(), light);
			ps.popPose();
		}
		ps.popPose();
	}
}
