package dev.t3craft.t3;

import static dev.t3craft.t3.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ShellStateTest {
	@Test
	void syncsInRealT3Order() {
		ShellState s = new ShellState();
		assertTrue(s.apply(authoritative(100, threadJson("a", H, ""), threadJson("b", H, ""), threadJson("c", H, ""))));
		s.apply(enrichment(100));
		s.apply(json("{\"kind\":\"synchronized\"}"));
		assertTrue(s.isSynchronized());
		assertEquals(3, s.threads().size());
		assertEquals(100, s.sequence());
	}

	/** Regression (bfaebf1): the enrichment snapshot with threads:[] used to wipe every thread. */
	@Test
	void enrichmentKeepsThreadsAndSequence() {
		ShellState s = new ShellState();
		s.apply(authoritative(200, threadJson("a", H, ""), threadJson("b", H, "")));
		assertTrue(s.apply(enrichment(999)));
		assertEquals(2, s.threads().size());
		assertEquals(200, s.sequence());
		assertNotNull(s.project("project-1").repositoryIdentity());
	}

	@Test
	void laterAuthoritativeSnapshotReplaces() {
		ShellState s = new ShellState();
		s.apply(authoritative(1, threadJson("a", H, ""), threadJson("b", H, "")));
		s.apply(authoritative(300, threadJson("c", H, "")));
		assertEquals(1, s.threads().size());
		assertNotNull(s.thread("c"));
		assertEquals(300, s.sequence());
	}

	@Test
	void updatesAndRemovals() {
		ShellState s = new ShellState();
		s.apply(authoritative(1, threadJson("a", H, "")));
		JsonUpdate:
		{
			var upd = json("{\"kind\":\"thread.updated\",\"sequence\":5,\"location\":\"active\"}");
			upd.add("thread", threadJson("b", H, "{\"status\":\"running\"}"));
			assertTrue(s.apply(upd));
		}
		assertEquals("running", s.thread("b").status());
		var arch = json("{\"kind\":\"thread.updated\",\"sequence\":6,\"location\":\"archive\"}");
		arch.add("thread", threadJson("a", H, ""));
		s.apply(arch);
		assertNull(s.thread("a"));
		assertTrue(s.apply(json("{\"kind\":\"thread.removed\",\"sequence\":7,\"location\":\"active\",\"threadId\":\"b\"}")));
		assertEquals(0, s.threads().size());
		assertEquals(7, s.sequence());
	}

	@Test
	void ignoresUnknownKinds() {
		ShellState s = new ShellState();
		assertFalse(s.apply(json("{\"kind\":\"something.new\"}")));
	}

	@Test
	void decodesTimestampsAndPending() {
		ThreadShell t = thread("x", 2 * H, "{\"pendingRuntimeRequest\":{\"id\":\"r\",\"kind\":\"command\",\"createdAt\":\"2026-11-07T11:00:00Z\"}}");
		assertEquals(NOW - 2 * H, t.createdAt());
		assertNull(t.pendingRuntimeRequest().blocking());
	}
}
