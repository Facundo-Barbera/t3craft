package dev.t3craft.client.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.client.npc.behavior.Anchor;
import dev.t3craft.client.npc.behavior.Pos;
import dev.t3craft.client.npc.behavior.RingLayout;
import dev.t3craft.t3.ScheduledTask;
import dev.t3craft.t3.ScheduledTask.RunStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ScheduleBoardTest {
	static final long NOW = 1_800_000_000_000L;
	static final long MIN = 60_000L;

	static ScheduledTask task(String id, String title, boolean enabled, @Nullable Long next, @Nullable Long last, RunStatus status) {
		return new ScheduledTask(id, title, "prompt of " + id, enabled, "interval", "every 1h", "p1", null, next, last, status, null, 0, null);
	}

	static ScheduleEntry entry(ScheduledTask t) {
		return new ScheduleEntry("vps", "VPS", "t3craft", t);
	}

	// ---- order --------------------------------------------------------------------------------

	@Test
	void soonestFirstThenTasksWithoutNextRun() {
		List<ScheduleEntry> in = List.of(
				entry(task("a", "Zeta paused", false, null, null, RunStatus.NEVER)),
				entry(task("b", "Later", true, NOW + 60 * MIN, null, RunStatus.NEVER)),
				entry(task("c", "Soon", true, NOW + 5 * MIN, null, RunStatus.NEVER)),
				entry(task("d", "alpha hook", true, null, null, RunStatus.NEVER)));
		List<String> ids = ScheduleEntry.sorted(in).stream().map(e -> e.task().id()).toList();
		assertEquals(List.of("c", "b", "d", "a"), ids);
	}

	@Test
	void tiesAreStable() {
		ScheduledTask t1 = task("2", "Same", true, NOW, null, RunStatus.NEVER);
		ScheduledTask t2 = task("1", "Same", true, NOW, null, RunStatus.NEVER);
		List<ScheduleEntry> s = ScheduleEntry.sorted(List.of(entry(t1), entry(t2)));
		assertEquals("1", s.get(0).task().id());
		assertEquals("vps:1", s.get(0).key());
	}

	@Test
	void bookMergesEnvironmentsSortedAndResolvesLabels() {
		ScheduleBook book = new ScheduleBook();
		book.unavailable("home", "offline");
		book.tasks("vps", List.of(task("a", "Later", true, NOW + 10 * MIN, null, RunStatus.NEVER)));
		book.tasks("home", List.of(task("b", "Sooner", true, NOW + MIN, null, RunStatus.NEVER)));
		List<ScheduleEntry> e = book.entries(env -> env.equals("vps") ? "VPS" : null, (env, p) -> env.equals("vps") ? "T3Craft" : null);
		assertEquals(List.of("b", "a"), e.stream().map(x -> x.task().id()).toList());
		assertEquals("home", e.get(0).envLabel(), "falls back to the id");
		assertEquals("p1", e.get(0).project(), "falls back to the project id");
		assertEquals("VPS", e.get(1).envLabel());
		assertEquals("T3Craft", e.get(1).project());
		assertNull(book.unavailable("home"), "a list clears the unavailable reason");
		book.retain(Set.of("vps"));
		assertEquals(Set.of("vps"), book.environments());
		book.clear();
		assertTrue(book.entries(x -> null, (x, y) -> null).isEmpty());
	}

	// ---- countdown ----------------------------------------------------------------------------

	@Test
	void spans() {
		assertEquals("0s", Countdown.span(-5));
		assertEquals("45s", Countdown.span(45_000));
		assertEquals("12m", Countdown.span(12 * MIN + 59_000));
		assertEquals("1h 05m", Countdown.span(65 * MIN));
		assertEquals("23h 59m", Countdown.span(24 * 60 * MIN - 1));
		assertEquals("2d", Countdown.span(48 * 60 * MIN));
		assertEquals("3d 4h", Countdown.span((3 * 24 + 4) * 60 * MIN + 30 * MIN));
	}

	@Test
	void untilAndAgo() {
		assertEquals("in 5m", Countdown.until(NOW + 5 * MIN, NOW));
		assertEquals("in 1s", Countdown.until(NOW + 1_500, NOW));
		assertEquals("due", Countdown.until(NOW + 500, NOW));
		assertEquals("due", Countdown.until(NOW - 10 * MIN, NOW));
		assertEquals("just now", Countdown.ago(NOW - 2_000, NOW));
		assertEquals("3h 00m ago", Countdown.ago(NOW - 180 * MIN, NOW));
	}

	@Test
	void nextColumnCoversEveryState() {
		assertEquals("in 5m", Countdown.next(task("a", "t", true, NOW + 5 * MIN, null, RunStatus.NEVER), NOW));
		assertEquals("running", Countdown.next(task("a", "t", true, NOW + 5 * MIN, null, RunStatus.RUNNING), NOW));
		assertEquals("paused", Countdown.next(task("a", "t", false, NOW + 5 * MIN, null, RunStatus.NEVER), NOW));
		assertEquals("-", Countdown.next(task("a", "t", true, null, null, RunStatus.NEVER), NOW));
		ScheduledTask hook = new ScheduledTask("h", "t", "p", true, "webhook", "webhook", "p", null, null, null, RunStatus.NEVER, null, 0, null);
		assertEquals("on webhook", Countdown.next(hook, NOW));
		ScheduledTask blocked = new ScheduledTask("h", "t", "p", true, "webhook", "webhook", "p", null, null, null, RunStatus.NEVER, null, 0, "policy");
		assertEquals("blocked", Countdown.next(blocked, NOW));
	}

	@Test
	void lastRun() {
		assertEquals("never run", Countdown.last(task("a", "t", true, null, null, RunStatus.NEVER), NOW));
		assertEquals("last ✔ 5m ago", Countdown.last(task("a", "t", true, null, NOW - 5 * MIN, RunStatus.SUCCEEDED), NOW));
		assertEquals("last ✘ 2d ago", Countdown.last(task("a", "t", true, null, NOW - 2 * 24 * 60 * MIN, RunStatus.FAILED), NOW));
		assertEquals("last …", Countdown.last(task("a", "t", true, null, null, RunStatus.RUNNING), NOW));
	}

	// ---- pagination / rotation ----------------------------------------------------------------

	@Test
	void pages() {
		List<Integer> items = IntStream.range(0, 13).boxed().toList();
		assertEquals(3, SchedulePager.pageCount(13, 6));
		assertEquals(1, SchedulePager.pageCount(0, 6));
		assertThrows(IllegalArgumentException.class, () -> SchedulePager.pageCount(1, 0));
		SchedulePager.Page<Integer> last = SchedulePager.page(items, 6, 2);
		assertEquals(List.of(12), last.items());
		assertEquals("3/3", last.label());
		assertEquals(List.of(0, 1, 2, 3, 4, 5), SchedulePager.page(items, 6, 3).items(), "index wraps");
		SchedulePager.Page<Integer> only = SchedulePager.page(List.of(1), 6, 0);
		assertEquals("", only.label());
		assertTrue(SchedulePager.page(List.<Integer>of(), 6, 0).items().isEmpty());
	}

	@Test
	void rotatesOnTheClock() {
		List<Integer> items = IntStream.range(0, 13).boxed().toList();
		assertEquals(0, SchedulePager.rotating(items, 6, 0, 8_000).index());
		assertEquals(0, SchedulePager.rotating(items, 6, 7_999, 8_000).index());
		assertEquals(1, SchedulePager.rotating(items, 6, 8_000, 8_000).index());
		assertEquals(2, SchedulePager.rotating(items, 6, 16_000, 8_000).index());
		assertEquals(0, SchedulePager.rotating(items, 6, 24_000, 8_000).index(), "back to the first page");
		assertEquals(0, SchedulePager.rotating(items, 6, 99_000, 0).index(), "no rotation");
		assertEquals(0, SchedulePager.rotating(items, 6, -5, 8_000).index());
	}

	// ---- text ---------------------------------------------------------------------------------

	@Test
	void boardFormat() {
		List<ScheduleEntry> all = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			all.add(entry(task("t" + i, "Task " + i, true, NOW + (i + 1) * MIN, NOW - 3 * 60 * MIN, RunStatus.SUCCEEDED)));
		}
		ScheduleText.Board b = ScheduleText.board(SchedulePager.page(all, 6, 0), all.size(), NOW, Map.of(), true);
		assertEquals(1 + 6 * 2, b.lines().size());
		assertEquals("SCHEDULED TASKS   1/2", b.lines().get(0).text());
		assertEquals(ScheduleText.HEADER, b.lines().get(0).color());
		assertEquals("● in 1m  Task 0", b.lines().get(1).text());
		assertEquals(ScheduleText.OK, b.lines().get(1).color());
		assertEquals("    every 1h · t3craft · VPS · last ✔ 3h 00m ago", b.lines().get(2).text());
		assertEquals(ScheduleText.DETAIL, b.lines().get(2).color());
		assertEquals(ScheduleText.BACKGROUND, b.background());
	}

	@Test
	void emptyBoardsExplainThemselves() {
		SchedulePager.Page<ScheduleEntry> none = SchedulePager.page(List.of(), 6, 0);
		assertEquals("Waiting for T3…", ScheduleText.board(none, 0, NOW, Map.of(), false).lines().get(1).text());
		assertEquals("No scheduled tasks", ScheduleText.board(none, 0, NOW, Map.of(), true).lines().get(1).text());
		List<ScheduleText.Line> l = ScheduleText.board(none, 0, NOW, Map.of("VPS", "offline"), false).lines();
		assertEquals(2, l.size());
		assertEquals("VPS: unavailable (offline)", l.get(1).text());
	}

	@Test
	void colours() {
		assertEquals(ScheduleText.RUNNING, ScheduleText.color(task("a", "t", true, null, null, RunStatus.RUNNING)));
		assertEquals(ScheduleText.MUTED, ScheduleText.color(task("a", "t", false, null, null, RunStatus.FAILED)));
		assertEquals(ScheduleText.FAILED, ScheduleText.color(task("a", "t", true, null, null, RunStatus.FAILED)));
		assertEquals(ScheduleText.SCHEDULED, ScheduleText.color(task("a", "t", true, null, null, RunStatus.NEVER)));
	}

	@Test
	void chatLineIncludesTheFailure() {
		ScheduledTask t = new ScheduledTask("a", "Deploy", "p", true, "interval", "every 1h", "p1", null, NOW + 30 * MIN, NOW - MIN, RunStatus.FAILED, "exit 1\nstack", 3, null);
		ScheduleText.Line l = ScheduleText.chat(entry(t), NOW);
		assertEquals("in 30m · Deploy · every 1h · t3craft · VPS · last ✘ 1m ago (exit 1 stack)", l.text());
		assertEquals(ScheduleText.FAILED, l.color());
	}

	@Test
	void clip() {
		assertEquals("short", ScheduleText.clip("  short ", 10));
		assertEquals("abcd…", ScheduleText.clip("abcdefghij", 5));
		assertEquals("a b", ScheduleText.clip("a\nb", 10));
		assertFalse(ScheduleText.clip("x".repeat(100), ScheduleText.NAME_MAX).length() > ScheduleText.NAME_MAX);
	}

	// ---- placement ----------------------------------------------------------------------------

	@Test
	void onTheHqBoardFace() {
		// board anchor (0.5, 3.5, -3) facing south (yaw 0)
		SchedulePlacement p = SchedulePlacement.onFace(0.5, 3.5, -3.0, 0f);
		assertEquals(0.5, p.x(), 1e-9);
		assertEquals(3.5 - SchedulePlacement.BOARD_DROP, p.y(), 1e-9);
		assertEquals(-3.0 + SchedulePlacement.FACE_GAP, p.z(), 1e-9);
		assertEquals(0f, p.yaw());
		SchedulePlacement east = SchedulePlacement.onFace(0, 0, 0, -90f);
		assertEquals(SchedulePlacement.FACE_GAP, east.x(), 1e-9, "nudged along the face normal (+x)");
	}

	@Test
	void ringPlacementFloatsBehindTheProvisionalBoard() {
		Pos origin = new Pos(10, 64, 10);
		List<RingLayout.Member> ms = List.of(new RingLayout.Member("e:a", "e:p", 1));
		RingBoardPlacement src = new RingBoardPlacement(() -> origin, () -> ms);
		SchedulePlacement p = src.placement();
		Anchor board = RingLayout.of(origin, ms).board();
		assertEquals(board.pos().x(), p.x(), 1e-6);
		assertEquals(board.pos().y() + RingBoardPlacement.HEIGHT, p.y(), 1e-6);
		assertEquals(board.pos().z() - RingBoardPlacement.DISTANCE, p.z(), 1e-6, "behind the spot, NPCs face -z");
		assertEquals(0f, p.yaw(), 1e-4f, "text turned back towards them");
		assertNull(new RingBoardPlacement(() -> null, () -> ms).placement());
		assertNull(new RingBoardPlacement(() -> origin, List::of).placement(), "no NPC, no board");
	}

	// ---- source -------------------------------------------------------------------------------

	@Test
	void pendingSourceIsReadOnlyAndExplainsItself() {
		ScheduleBook book = new ScheduleBook();
		ScheduleSource.Handle h = ScheduleSource.PENDING.open(new dev.t3craft.t3.DirectController(new dev.t3craft.t3.Environments(java.nio.file.Path.of("build", "no-such-dir")), () -> {
		}), book);
		h.close();
		assertTrue(book.environments().isEmpty(), "nothing delivered");
	}
}
