package dev.t3craft.client.hologram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.client.config.T3CraftSettings;
import dev.t3craft.t3.ThreadShell;
import dev.t3craft.t3.ThreadState;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HologramModelTest {
	static final ZoneId UTC = ZoneOffset.UTC;
	/** 2026-11-07T15:00Z. */
	static final long NOW = 1_794_063_600_000L;
	static final long DAY = HologramModel.startOfDay(NOW, UTC);

	static HologramModel.Entry e(String env, String project, String title, ThreadState s, int prio) {
		return new HologramModel.Entry(env, env.toUpperCase(), env + ":" + project, project, title, s.wire, prio);
	}

	static HologramModel.Entry e(String env, String project, String title, ThreadState s) {
		return e(env, project, title, s, 10);
	}

	static HologramModel build(List<HologramModel.Entry> entries, int done) {
		return HologramModel.build(new HologramModel.Inputs(entries, done, true, false));
	}

	static ThreadShell shell(String id, String status, Long completedAt, Long settledAt, Long deletedAt, String parent) {
		return new ThreadShell(id, "p", id, status, null, null, false, 0, 0, null, null, null, null, null, false, null, null, null, null,
				completedAt, null, null, deletedAt, settledAt, null, null, parent);
	}

	// ---- counts -------------------------------------------------------------------------------

	@Test
	void countsWorkingNeedsYouErrorAndDoneToday() {
		HologramModel m = build(List.of(
				e("a", "x", "w1", ThreadState.WORKING),
				e("a", "x", "w2", ThreadState.WORKING),
				e("a", "x", "n1", ThreadState.NEEDS_ATTENTION),
				e("a", "y", "err", ThreadState.ERROR),
				e("a", "y", "d", ThreadState.DONE),
				e("a", "y", "i", ThreadState.IDLE)), 4);
		assertEquals(new HologramModel.Counts(6, 2, 1, 1, 4), m.counts());
		assertEquals(ThreadState.NEEDS_ATTENTION, m.top());
		assertEquals("2 working · 1 needs you · 1 error · 4 done today", m.detail().get(0).get(0).text());
	}

	@Test
	void headerDimsZeroCounts() {
		HologramModel m = build(List.of(e("a", "x", "w", ThreadState.WORKING)), 0);
		List<HologramModel.Span> h = m.header();
		assertEquals("⚙ 1", h.get(0).text());
		assertEquals(ThreadState.WORKING.color, h.get(0).color());
		assertEquals("! 0", h.get(2).text());
		assertEquals(HologramModel.DIM, h.get(2).color());
		assertEquals("✔ 0", h.get(6).text());
	}

	@Test
	void doneTodayCountsOnlyTodaysTopLevelDoneThreads() {
		List<ThreadShell> shells = List.of(
				shell("today", "completed", NOW - 3_600_000, null, null, null),
				shell("settledToday", "completed", null, NOW - 60_000, null, null),
				shell("yesterday", "completed", DAY - 1, null, null, null),
				shell("midnight", "completed", DAY, null, null, null),
				shell("deleted", "completed", NOW - 1, null, NOW, null),
				shell("subagent", "completed", NOW - 1, null, null, "parent"),
				shell("failed", "failed", NOW - 1, null, null, null),
				shell("runningAgain", "running", NOW - 1, null, null, null),
				shell("noTime", "completed", null, null, null, null));
		assertEquals(3, HologramModel.doneToday(shells, DAY));
	}

	@Test
	void startOfDayIsLocalMidnight() {
		assertEquals(1_794_009_600_000L, DAY); // 2026-11-07T00:00Z
		long plus3 = HologramModel.startOfDay(NOW, ZoneOffset.ofHours(3)); // 18:00 local, same day
		assertEquals(DAY - 3 * 3_600_000L, plus3);
		assertEquals(DAY + 86_400_000L, HologramModel.startOfDay(DAY + 86_400_000L, UTC));
	}

	@Test
	void doneFromStatesWithoutTimestamps() {
		assertEquals(2, HologramModel.doneFromStates(List.of(e("a", "x", "1", ThreadState.DONE), e("a", "x", "2", ThreadState.DONE),
				e("a", "x", "3", ThreadState.IDLE))));
	}

	// ---- most urgent ---------------------------------------------------------------------------

	@Test
	void urgentPrefersNeedsYouThenErrorThenWorking() {
		List<HologramModel.Entry> l = new ArrayList<>(List.of(
				e("a", "x", "work", ThreadState.WORKING, 0),
				e("a", "x", "err", ThreadState.ERROR, 5),
				e("a", "x", "done", ThreadState.DONE, 0)));
		assertEquals("err", build(l, 0).urgent().title());
		l.add(e("a", "x", "ask", ThreadState.NEEDS_ATTENTION, 9));
		HologramModel m = build(l, 0);
		assertEquals("ask", m.urgent().title());
		assertEquals(ThreadState.NEEDS_ATTENTION, m.urgent().state());
		assertEquals("x", m.urgent().where()); // one environment: where = project
	}

	@Test
	void urgentTieBreaksOnPriorityThenTitle() {
		HologramModel m = build(List.of(
				e("a", "x", "b", ThreadState.NEEDS_ATTENTION, 3),
				e("a", "x", "a", ThreadState.NEEDS_ATTENTION, 3),
				e("a", "x", "z", ThreadState.NEEDS_ATTENTION, 1)), 0);
		assertEquals("z", m.urgent().title());
		m = build(List.of(e("a", "x", "b", ThreadState.WORKING, 3), e("a", "x", "a", ThreadState.WORKING, 3)), 0);
		assertEquals("a", m.urgent().title());
	}

	@Test
	void noUrgentWhenAllQuiet() {
		HologramModel m = build(List.of(e("a", "x", "d", ThreadState.DONE), e("a", "x", "i", ThreadState.IDLE)), 2);
		assertNull(m.urgent());
		assertEquals("All quiet", m.detail().get(m.detail().size() - 1).get(0).text());
		assertEquals(ThreadState.DONE, m.top());
	}

	@Test
	void urgentTitleIsCut() {
		String longTitle = "x".repeat(80);
		HologramModel m = build(List.of(e("a", "p", longTitle, ThreadState.ERROR)), 0);
		String t = m.urgent().text();
		assertTrue(t.startsWith("✖ " + "x".repeat(HologramModel.MAX_TITLE - 1) + "…"), t);
		assertTrue(t.endsWith(" · error · p"), t);
	}

	// ---- rings ---------------------------------------------------------------------------------

	@Test
	void ringsPerProjectWithOneEnvironment() {
		HologramModel m = build(List.of(
				e("a", "x", "1", ThreadState.DONE),
				e("a", "x", "2", ThreadState.DONE),
				e("a", "x", "3", ThreadState.WORKING),
				e("a", "y", "4", ThreadState.DONE)), 0);
		assertFalse(m.byEnvironment());
		assertEquals(2, m.rings().size());
		HologramModel.Ring x = m.rings().get(0); // working beats done
		assertEquals("x", x.label());
		assertEquals(3, x.total());
		assertEquals(2, x.done());
		assertEquals("◕ x 2/3", x.text()); // 0.67 → three quarters
		assertEquals(ThreadState.WORKING.color, x.color());
		HologramModel.Ring y = m.rings().get(1);
		assertEquals("● y 1/1", y.text());
		assertEquals(ThreadState.DONE.color, y.color());
	}

	@Test
	void ringsPerEnvironmentWithSeveral() {
		HologramModel m = build(List.of(
				e("a", "x", "1", ThreadState.WORKING),
				e("a", "y", "2", ThreadState.WORKING),
				e("b", "x", "3", ThreadState.NEEDS_ATTENTION)), 0);
		assertTrue(m.byEnvironment());
		assertEquals(List.of("B", "A"), m.rings().stream().map(HologramModel.Ring::label).toList());
		assertEquals(2, m.rings().get(1).total());
		assertEquals("B", m.urgent().where());
	}

	@Test
	void ringsAreCappedWithMore() {
		List<HologramModel.Entry> l = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			l.add(e("a", "p" + i, "t" + i, ThreadState.IDLE));
		}
		HologramModel m = build(l, 0);
		assertEquals(HologramModel.MAX_RINGS, m.rings().size());
		assertEquals(2, m.moreRings());
		List<HologramModel.Span> line = m.detail().get(1);
		assertEquals("   +2", line.get(line.size() - 1).text());
		assertEquals("○", m.rings().get(0).glyph());
	}

	@Test
	void ringOrderUrgentThenBiggerThenLabel() {
		HologramModel m = build(List.of(
				e("a", "small", "1", ThreadState.IDLE),
				e("a", "big", "2", ThreadState.IDLE),
				e("a", "big", "3", ThreadState.IDLE),
				e("a", "alpha", "4", ThreadState.IDLE),
				e("a", "err", "5", ThreadState.ERROR)), 0);
		assertEquals(List.of("err", "big", "alpha", "small"), m.rings().stream().map(HologramModel.Ring::label).toList());
	}

	// ---- recompute only on change, config off --------------------------------------------------

	@Test
	void rebuildsOnlyWhenInputsChange() {
		HologramState st = new HologramState();
		List<HologramModel.Entry> l = List.of(e("a", "x", "w", ThreadState.WORKING));
		assertTrue(st.update(new HologramModel.Inputs(l, 1, true, false)));
		HologramModel first = st.model();
		assertFalse(st.update(new HologramModel.Inputs(new ArrayList<>(l), 1, true, false)));
		assertSame(first, st.model());
		assertEquals(1, st.builds());
		assertTrue(st.update(new HologramModel.Inputs(l, 2, true, false)));
		assertEquals(2, st.model().counts().doneToday());
		assertTrue(st.update(new HologramModel.Inputs(List.of(e("a", "x", "w", ThreadState.ERROR)), 2, true, false)));
		assertTrue(st.update(new HologramModel.Inputs(List.of(e("a", "x", "w", ThreadState.ERROR)), 2, true, true)));
		assertEquals(4, st.builds());
		st.reset();
		assertSame(HologramModel.HIDDEN, st.model());
		assertTrue(st.update(new HologramModel.Inputs(List.of(e("a", "x", "w", ThreadState.ERROR)), 2, true, true)));
	}

	@Test
	void configOffShowsNothing() {
		List<HologramModel.Entry> l = List.of(e("a", "x", "n", ThreadState.NEEDS_ATTENTION));
		HologramModel m = HologramModel.build(new HologramModel.Inputs(l, 3, false, false));
		assertSame(HologramModel.HIDDEN, m);
		assertTrue(m.hidden());
		assertTrue(m.rings().isEmpty());
		assertNull(m.urgent());
		HologramState st = new HologramState();
		st.update(new HologramModel.Inputs(l, 3, false, false));
		assertTrue(st.model().hidden());
	}

	@Test
	void nothingToShowIsHidden() {
		assertTrue(build(List.of(), 0).hidden());
		HologramModel onlyDone = build(List.of(), 3);
		assertFalse(onlyDone.hidden());
		assertEquals(ThreadState.DONE, onlyDone.top());
	}

	@Test
	void staleAddsOfflineLine() {
		HologramModel m = HologramModel.build(new HologramModel.Inputs(List.of(e("a", "x", "w", ThreadState.WORKING)), 0, true, true));
		List<List<HologramModel.Span>> d = m.detail();
		assertEquals("Offline · last known state", d.get(d.size() - 1).get(0).text());
	}

	@Test
	void hologramConfigDefaultsOnAndIsAdditive() {
		assertTrue(T3CraftSettings.Hud.defaults().hologram());
		assertTrue(new T3CraftSettings.Hud(true, "top", "center", true).hologram());
		assertFalse(T3CraftSettings.Hud.defaults().withHologram(false).hologram());
		assertFalse(T3CraftSettings.Hud.defaults().withHologram(false).withStatusBar(false).hologram());
	}

	// ---- layout --------------------------------------------------------------------------------

	@Test
	void cardStaysAboveNameplatesAndUnderTheLantern() {
		assertTrue(HologramLayout.CARD_BOTTOM > 2.8);
		for (int lines = 1; lines <= 4; lines++) {
			assertTrue(HologramLayout.headerY(lines) > HologramLayout.CARD_BOTTOM + HologramLayout.textHeight(lines, HologramLayout.DETAIL_SCALE));
			assertTrue(HologramLayout.top(lines) < HologramLayout.CEILING, "lines " + lines);
		}
		assertEquals(HologramLayout.CARD_BOTTOM - HologramLayout.BEAM_FLOOR, HologramLayout.beamHeight(), 1e-6);
		assertNotNull(HologramModel.HIDDEN.header());
	}
}
