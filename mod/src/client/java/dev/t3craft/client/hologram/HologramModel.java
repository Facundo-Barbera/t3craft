// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.hologram;

import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.t3.ThreadShell;
import dev.t3craft.t3.ThreadState;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Pure model of the hub hologram (feat/hub-hologram), after AgentCraft's Goal Atrium hologram
 * ({@code StatusLampRenderer.extractHologram}: a floating card with a progress ring captioned
 * "done/total", agent counts with "n need you" and a soft beam from the floor to the card). Here the
 * card summarises <b>threads</b> of every environment: counts (working · needs you · error · done
 * today), one ring per environment (several environments) or per project (one environment), the
 * most urgent thread and the colour of the beam. No Minecraft classes, so it is unit-tested and
 * rebuilt only when its {@link Inputs} change ({@link HologramState}).
 */
public record HologramModel(Counts counts, List<Ring> rings, int moreRings, @Nullable Urgent urgent, ThreadState top,
		boolean byEnvironment, boolean stale, int doneNow) {

	/** Rings shown before "+N". */
	public static final int MAX_RINGS = 4;
	/** Thread titles longer than this are cut with "…". */
	public static final int MAX_TITLE = 36;
	/** Ring labels longer than this are cut with "…". */
	public static final int MAX_RING_LABEL = 14;
	/** Colour of zero counts and quiet text. */
	public static final int DIM = 0x777777;
	/** Colour of the words line. */
	public static final int MUTED = 0xBBBBBB;

	/** Nothing to draw (config off, or no thread at all). */
	public static final HologramModel HIDDEN = new HologramModel(Counts.ZERO, List.of(), 0, null, ThreadState.IDLE, false, false, 0);

	/**
	 * One thread as the hologram sees it (from the NPC roster, so it matches the HUD bar and the
	 * NPCs). {@code env}: key of its environment (one ring each when there are several).
	 */
	public record Entry(String env, String envLabel, String groupId, String groupLabel, String title, String state, int priority) {
		ThreadState threadState() {
			for (ThreadState s : ThreadState.values()) {
				if (s.wire.equals(state)) {
					return s;
				}
			}
			return ThreadState.IDLE;
		}
	}

	/**
	 * Everything the model depends on; the model is rebuilt only when this changes.
	 *
	 * @param entries visible, unsettled threads of every environment
	 * @param doneToday threads that finished today ({@link #doneToday(Iterable, long)})
	 * @param enabled {@code hud.hologram}
	 * @param stale no environment is in sync (last known state)
	 */
	public record Inputs(List<Entry> entries, int doneToday, boolean enabled, boolean stale) {
		public Inputs {
			entries = List.copyOf(entries);
		}
	}

	public record Counts(int total, int working, int needsYou, int error, int doneToday) {
		public static final Counts ZERO = new Counts(0, 0, 0, 0, 0);
	}

	/**
	 * One ring: an environment or a project. {@code progress} = done / total, like AgentCraft's ring
	 * that follows the done count ("2/9"); coloured by its most urgent thread.
	 */
	public record Ring(String key, String label, int total, int done, ThreadState top) {
		static final String[] GLYPHS = {"○", "◔", "◑", "◕", "●"};

		public double progress() {
			return total == 0 ? 0 : done / (double) total;
		}

		/** Ring glyph filled by quarters: ○ ◔ ◑ ◕ ●. */
		public String glyph() {
			return GLYPHS[(int) Math.round(progress() * 4)];
		}

		public int color() {
			return top == ThreadState.IDLE && done > 0 ? ThreadState.DONE.color : top.color;
		}

		public String text() {
			return glyph() + " " + cut(label, MAX_RING_LABEL) + " " + done + "/" + total;
		}
	}

	/** The thread to look at first; {@code where} = its environment or project. */
	public record Urgent(String title, ThreadState state, String where) {
		public String text() {
			return state.glyph + " " + cut(title, MAX_TITLE) + " · " + state.word + (where.isEmpty() ? "" : " · " + where);
		}
	}

	/** A run of coloured text. */
	public record Span(String text, int color) {
	}

	public boolean hidden() {
		return this == HIDDEN || (counts.total() == 0 && counts.doneToday() == 0);
	}

	// ---- build ----------------------------------------------------------------------------------

	public static HologramModel build(Inputs in) {
		if (!in.enabled() || (in.entries().isEmpty() && in.doneToday() <= 0)) {
			return HIDDEN;
		}
		int working = 0;
		int needs = 0;
		int error = 0;
		int doneNow = 0;
		ThreadState top = ThreadState.IDLE;
		for (Entry e : in.entries()) {
			ThreadState s = e.threadState();
			switch (s) {
				case WORKING -> working++;
				case NEEDS_ATTENTION -> needs++;
				case ERROR -> error++;
				case DONE -> doneNow++;
				default -> {
				}
			}
			top = urgentOf(top, s);
		}
		int doneToday = Math.max(0, in.doneToday());
		if (top == ThreadState.IDLE && doneToday > 0) {
			top = ThreadState.DONE;
		}
		boolean byEnv = in.entries().stream().map(Entry::env).distinct().count() > 1;
		List<Ring> all = rings(in.entries(), byEnv);
		List<Ring> shown = all.size() > MAX_RINGS ? all.subList(0, MAX_RINGS) : all;
		return new HologramModel(new Counts(in.entries().size(), working, needs, error, doneToday), List.copyOf(shown),
				all.size() - shown.size(), urgent(in.entries(), byEnv), top, byEnv, in.stale(), doneNow);
	}

	/** The more urgent of two states (needs you › error › working › done › idle). */
	static ThreadState urgentOf(ThreadState a, ThreadState b) {
		return b.priority < a.priority ? b : a;
	}

	/**
	 * One ring per environment ({@code byEnvironment}) or per project, the most urgent first, then the
	 * bigger, then by label.
	 */
	static List<Ring> rings(List<Entry> entries, boolean byEnvironment) {
		Map<String, int[]> counts = new LinkedHashMap<>();
		Map<String, String> labels = new LinkedHashMap<>();
		Map<String, ThreadState> tops = new LinkedHashMap<>();
		for (Entry e : entries) {
			String key = byEnvironment ? e.env() : e.groupId();
			labels.putIfAbsent(key, byEnvironment ? e.envLabel() : e.groupLabel());
			int[] c = counts.computeIfAbsent(key, k -> new int[2]);
			c[0]++;
			ThreadState s = e.threadState();
			if (s == ThreadState.DONE) {
				c[1]++;
			}
			tops.merge(key, s, HologramModel::urgentOf);
		}
		List<Ring> out = new ArrayList<>();
		counts.forEach((k, c) -> out.add(new Ring(k, labels.get(k), c[0], c[1], tops.get(k))));
		out.sort(Comparator.comparingInt((Ring r) -> r.top().priority).thenComparing(Comparator.comparingInt(Ring::total).reversed())
				.thenComparing(Ring::label));
		return out;
	}

	/**
	 * The most urgent thread that wants something (needs you › error › working); ties go to the NPC
	 * priority, then the title. Null when every thread is done or idle.
	 */
	static @Nullable Urgent urgent(List<Entry> entries, boolean byEnvironment) {
		Entry best = null;
		ThreadState bestState = null;
		for (Entry e : entries) {
			ThreadState s = e.threadState();
			if (s != ThreadState.NEEDS_ATTENTION && s != ThreadState.ERROR && s != ThreadState.WORKING) {
				continue;
			}
			if (best == null || s.priority < bestState.priority
					|| (s.priority == bestState.priority && (e.priority() < best.priority()
							|| (e.priority() == best.priority() && e.title().compareTo(best.title()) < 0)))) {
				best = e;
				bestState = s;
			}
		}
		if (best == null) {
			return null;
		}
		return new Urgent(best.title(), bestState, byEnvironment ? best.envLabel() : best.groupLabel());
	}

	// ---- done today -----------------------------------------------------------------------------

	/** Local midnight (epoch millis) of the day that contains {@code nowMs}. */
	public static long startOfDay(long nowMs, ZoneId zone) {
		return Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli();
	}

	/**
	 * Threads that are done now and finished at or after {@code dayStartMs}: state {@link ThreadState#DONE}
	 * ({@link ThreadState#of}), not deleted, top-level (subagents follow their parent), finish time =
	 * {@code latestRunCompletedAt}, else {@code settledAt}. Settled and archived threads still count:
	 * they were done today even if no NPC shows them any more.
	 */
	public static int doneToday(Iterable<ThreadShell> shells, long dayStartMs) {
		int n = 0;
		for (ThreadShell t : shells) {
			if (t == null || t.deletedAt() != null || t.parentThreadId() != null || ThreadState.of(t) != ThreadState.DONE) {
				continue;
			}
			Long at = t.latestRunCompletedAt() != null ? t.latestRunCompletedAt() : t.settledAt();
			if (at != null && at >= dayStartMs) {
				n++;
			}
		}
		return n;
	}

	/** Without timestamps (bridge mode): the entries that are done now. */
	public static int doneFromStates(List<Entry> entries) {
		int n = 0;
		for (Entry e : entries) {
			if (e.threadState() == ThreadState.DONE) {
				n++;
			}
		}
		return n;
	}

	// ---- text -----------------------------------------------------------------------------------

	/** Big line readable from afar: "⚙ 3   ! 1   ✖ 0   ✔ 4" (zero counts dimmed). */
	public List<Span> header() {
		List<Span> out = new ArrayList<>(7);
		count(out, ThreadState.WORKING, counts.working());
		count(out, ThreadState.NEEDS_ATTENTION, counts.needsYou());
		count(out, ThreadState.ERROR, counts.error());
		count(out, ThreadState.DONE, counts.doneToday());
		return out;
	}

	private static void count(List<Span> out, ThreadState s, int n) {
		if (!out.isEmpty()) {
			out.add(new Span("   ", DIM));
		}
		out.add(new Span(s.glyph + " " + n, n == 0 ? DIM : s.color));
	}

	/**
	 * Small lines under the header: the words ("3 working · 1 needs you · 0 error · 4 done today"),
	 * the rings, the most urgent thread (or "All quiet") and, when stale, "Offline · last known state".
	 */
	public List<List<Span>> detail() {
		List<List<Span>> lines = new ArrayList<>(4);
		String words = counts.working() + " working · " + counts.needsYou() + (counts.needsYou() == 1 ? " needs" : " need") + " you · "
				+ counts.error() + " error · " + counts.doneToday() + " done today";
		lines.add(List.of(new Span(words, MUTED)));
		if (!rings.isEmpty()) {
			List<Span> r = new ArrayList<>();
			for (Ring ring : rings) {
				if (!r.isEmpty()) {
					r.add(new Span("   ", DIM));
				}
				r.add(new Span(ring.text(), ring.color()));
			}
			if (moreRings > 0) {
				r.add(new Span("   +" + moreRings, DIM));
			}
			lines.add(List.copyOf(r));
		}
		lines.add(List.of(urgent == null ? new Span("All quiet", DIM) : new Span(urgent.text(), urgent.state().color)));
		if (stale) {
			lines.add(List.of(new Span("Offline · last known state", DIM)));
		}
		return lines;
	}

	// ---- card (world text, hologram style) -------------------------------------------------------

	/** Threads in the card's progress ring: the open ones (not done) plus those done today. */
	public int ringTotal() {
		return Math.max(0, counts.total() - doneNow) + Math.max(0, counts.doneToday());
	}

	/** Done today, the ring's filled part. */
	public int ringDone() {
		return Math.min(ringTotal(), Math.max(0, counts.doneToday()));
	}

	/** 0..1 for the ring (AgentCraft's ring follows the done count). */
	public double ringProgress() {
		int t = ringTotal();
		return t == 0 ? 0 : ringDone() / (double) t;
	}

	/** Segments of a ring of {@code segments} that are filled (rounded, never 0 with something done, never all with something open). */
	public static int filled(int done, int total, int segments) {
		if (total <= 0 || done <= 0) {
			return 0;
		}
		if (done >= total) {
			return segments;
		}
		int n = (int) Math.round(done * (double) segments / total);
		return Math.max(1, Math.min(segments - 1, n));
	}

	/** Caption in the middle of the ring: {@code "4/9"}. */
	public String ringCaption() {
		return ringDone() + "/" + ringTotal();
	}

	/**
	 * The card's lines under the ring, in theme colours: the counts (zero counts dimmed), the most
	 * urgent thread (or "All quiet"), the rings per environment / project in one line when there are
	 * several, and "Offline · last known state" when stale.
	 */
	public List<List<Span>> card(ChatTheme theme) {
		List<List<Span>> lines = new ArrayList<>(4);
		List<Span> head = new ArrayList<>(7);
		cardCount(head, theme, ThreadState.WORKING, counts.working(), " working");
		cardCount(head, theme, ThreadState.NEEDS_ATTENTION, counts.needsYou(), counts.needsYou() == 1 ? " needs you" : " need you");
		cardCount(head, theme, ThreadState.ERROR, counts.error(), " error");
		cardCount(head, theme, ThreadState.DONE, counts.doneToday(), " done");
		lines.add(List.copyOf(head));
		lines.add(List.of(urgent == null ? new Span("All quiet", rgb(theme.muted()))
				: new Span(urgent.text(), rgb(theme.color(urgent.state())))));
		if (rings.size() > 1) {
			List<Span> r = new ArrayList<>();
			for (Ring ring : rings) {
				if (!r.isEmpty()) {
					r.add(new Span("  ", rgb(theme.disabled())));
				}
				r.add(new Span(cut(ring.label(), MAX_RING_LABEL) + " " + ring.done() + "/" + ring.total(),
						rgb(ring.top() == ThreadState.IDLE && ring.done() > 0 ? theme.done() : theme.color(ring.top()))));
			}
			if (moreRings > 0) {
				r.add(new Span("  +" + moreRings, rgb(theme.disabled())));
			}
			lines.add(List.copyOf(r));
		}
		if (stale) {
			lines.add(List.of(new Span("Offline · last known state", rgb(theme.disabled()))));
		}
		return lines;
	}

	private static void cardCount(List<Span> out, ChatTheme theme, ThreadState s, int n, String word) {
		if (!out.isEmpty()) {
			out.add(new Span("  ", rgb(theme.disabled())));
		}
		out.add(new Span(s.glyph + " " + n + word, rgb(n == 0 ? theme.disabled() : theme.color(s))));
	}

	private static int rgb(int argb) {
		return argb & 0xFFFFFF;
	}

	static String cut(String s, int max) {
		String t = s == null ? "" : s.strip();
		return t.length() <= max ? t : t.substring(0, Math.max(0, max - 1)).stripTrailing() + "…";
	}
}
