package dev.t3craft.client.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.t3craft.client.portable.PortableState;
import dev.t3craft.client.portable.PortableThread;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The unified list and the portable terminal (and held screen) rank threads the same way. */
class ThreadOrderTest {
	static PortableThread pt(String id, String state, boolean settled) {
		return new PortableThread("e:" + id, "e", "E", id, id, "", state, 0, null, 0, settled);
	}

	@Test
	void portableUsesTheSharedOrder() {
		List<PortableThread> in = List.of(pt("d", "done", false), pt("i", "idle", false), pt("w", "working", false), pt("e", "error", false),
				pt("n", "needs_attention", false), pt("c", "done", true), pt("b", "done", false));
		List<PortableThread> sorted = new ArrayList<>(in);
		sorted.sort(PortableState.ORDER);
		assertEquals(List.of("n", "e", "w", "b", "d", "c", "i"), sorted.stream().map(PortableThread::threadId).toList());

		List<ThreadRow> rows = new ArrayList<>(in.stream()
				.map(t -> new ThreadRow(t.id(), t.env(), t.envLabel(), t.threadId(), t.title(), "", ThreadOrder.state(t.state()), false, null,
						t.settled(), null))
				.toList());
		rows.sort(ThreadOrder.ROWS);
		assertEquals(sorted.stream().map(PortableThread::threadId).toList(), rows.stream().map(ThreadRow::threadId).toList());
	}
}
