package dev.t3craft.client.queue;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueueOrderTest {
	static QueuedRun run(String id, long ordinal, Long pos) {
		return new QueuedRun("vps", "t", id, "queued", ordinal, pos, false, null, null, false, true, null);
	}

	static QueuedRun auto(String id, long ordinal, Long pos) {
		return new QueuedRun("vps", "t", id, "queued", ordinal, pos, false, null, null, true, false, null);
	}

	static List<String> ids(List<QueuedRun> runs) {
		return runs.stream().map(QueuedRun::id).toList();
	}

	@Test
	void sortsLikeT3() {
		List<QueuedRun> in = List.of(run("c", 3, 3L), run("x", 9, null), auto("z", 10, 5L), run("a", 1, 1L), run("b", 2, 2L),
				run("tie", 2, null));
		// automatic first; position ?? ordinal; then ordinal ("b" pos 2 vs "tie" ordinal 2 → same key, ordinal tie → id).
		assertEquals(List.of("z", "a", "b", "tie", "c", "x"), ids(QueueOrder.sort(in)));
	}

	@Test
	void globalOrderIsEnvThenThreadThenDelivery() {
		List<QueuedRun> in = new ArrayList<>(List.of(
				new QueuedRun("vps", "t2", "r1", "queued", 1, 1L, false, null, null, false, true, null),
				new QueuedRun("acmesite", "t9", "r2", "queued", 2, 2L, false, null, null, false, true, null),
				new QueuedRun("vps", "t1", "r3", "queued", 2, 2L, false, null, null, false, true, null),
				new QueuedRun("vps", "t1", "r4", "queued", 1, 1L, false, null, null, false, true, null)));
		in.sort(QueueOrder.GLOBAL);
		assertEquals(List.of("r2", "r4", "r3", "r1"), ids(in));
	}

	@Test
	void upAndDownGiveTheBeforeRunIdT3Expects() {
		List<QueuedRun> q = List.of(run("a", 1, 1L), run("b", 2, 2L), run("c", 3, 3L), run("d", 4, 4L));
		assertNull(QueueOrder.up(q, "a"), "already first");
		assertEquals(new QueueOrder.Move("b", "a"), QueueOrder.up(q, "b"));
		assertEquals(new QueueOrder.Move("d", "c"), QueueOrder.up(q, "d"));

		assertEquals(new QueueOrder.Move("a", "c"), QueueOrder.down(q, "a"), "before the one after the next");
		assertEquals(new QueueOrder.Move("c", null), QueueOrder.down(q, "c"), "second to last → end (null)");
		assertNull(QueueOrder.down(q, "d"), "already last");
		assertNull(QueueOrder.up(q, "nope"));
		assertNull(QueueOrder.down(q, "nope"));

		assertEquals(List.of("b", "a", "c", "d"), ids(QueueOrder.apply(q, QueueOrder.up(q, "b"))));
		assertEquals(List.of("b", "c", "a", "d"), ids(QueueOrder.apply(q, new QueueOrder.Move("a", "d"))));
		assertEquals(List.of("a", "b", "d", "c"), ids(QueueOrder.apply(q, QueueOrder.down(q, "c"))));
	}

	@Test
	void automaticRunsAreNeitherMovedNorTargets() {
		List<QueuedRun> q = List.of(auto("z", 9, 1L), run("a", 1, 2L), run("b", 2, 3L));
		assertNull(QueueOrder.up(q, "a"), "first movable: cannot go ahead of an automatic delivery");
		assertNull(QueueOrder.up(q, "z"));
		assertNull(QueueOrder.down(q, "z"));
		assertEquals(new QueueOrder.Move("a", null), QueueOrder.down(q, "a"));
		assertEquals(List.of("z", "b", "a"), ids(QueueOrder.apply(q, QueueOrder.down(q, "a"))));
	}

	@Test
	void nonQueuedRunsAreIgnored() {
		QueuedRun running = new QueuedRun("vps", "t", "r", "running", 0, null, false, null, null, false, true, null);
		List<QueuedRun> q = List.of(running, run("a", 1, 1L), run("b", 2, 2L));
		assertEquals(new QueueOrder.Move("b", "a"), QueueOrder.up(q, "b"));
		assertNull(QueueOrder.up(q, "r"));
	}
}
