package dev.t3craft.client.archive;

import static dev.t3craft.client.archive.ArchiveFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import dev.t3craft.t3.ArchiveWire;
import dev.t3craft.t3.ThreadShell;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ArchiveStoreTest {
	/** A source whose answers are scripted and whose calls are counted. */
	static final class FakeSource implements ArchiveSource {
		final List<String> calls = new ArrayList<>();
		final List<ThreadShell> olderAll = new ArrayList<>();
		ArchiveWire.ArchivedSnapshot archived = ArchiveWire.ArchivedSnapshot.EMPTY;
		boolean fail;
		CompletableFuture<ArchiveWire.OlderSettledPage> pendingOlder;

		@Override
		public CompletableFuture<ArchiveWire.ArchivedSnapshot> archived(String env) {
			calls.add("archived " + env);
			return fail ? CompletableFuture.failedFuture(new IllegalStateException("not connected")) : CompletableFuture.completedFuture(archived);
		}

		@Override
		public CompletableFuture<ArchiveWire.OlderSettledPage> older(String env, ArchiveWire.Cursor before, int limit) {
			calls.add("older " + env + " " + (before.threadId() == null ? "now" : before.threadId()));
			if (fail) {
				return CompletableFuture.failedFuture(new IllegalStateException("not connected"));
			}
			if (pendingOlder != null) {
				return pendingOlder;
			}
			// server rule: settledAt desc, then id asc; strictly after the cursor
			List<ThreadShell> sorted = olderAll.stream()
					.sorted((a, b) -> a.settledAt().equals(b.settledAt()) ? a.id().compareTo(b.id()) : Long.compare(b.settledAt(), a.settledAt()))
					.filter(t -> t.settledAt() < before.settledAtMs()
							|| (before.threadId() != null && t.settledAt() == before.settledAtMs() && t.id().compareTo(before.threadId()) > 0))
					.limit(limit).toList();
			return CompletableFuture.completedFuture(new ArchiveWire.OlderSettledPage(1, sorted, Map.of()));
		}
	}

	@Test
	void mergesShellArchivedAndOlderWithShellPriority() {
		FakeSource src = new FakeSource();
		src.archived = new ArchiveWire.ArchivedSnapshot(3, List.of(archived("arch", "p2", 50 * H)), Map.of("p2", "Forja"));
		src.olderAll.add(settled("old", "p1", 30 * H));
		src.olderAll.add(settled("back", "p1", 40 * H)); // active again in the shell: must not show
		src.olderAll.add(settled("dup", "p1", 2 * H));
		ArchiveStore s = new ArchiveStore(src);
		s.updateEnv("vps", "VPS", view(Map.of("p1", "T3Craft"), settled("dup", "p1", 2 * H), active("back", "p1"), active("live", "p1")), true);
		s.tick(NOW);

		List<String> keys = s.entries().stream().map(ArchiveEntry::key).toList();
		assertEquals(List.of("vps:dup", "vps:old", "vps:arch"), keys, "newest close first, de-duplicated, active excluded");
		ArchiveEntry old = s.entry("vps:old");
		assertEquals("T3Craft", old.projectLabel(), "older pages borrow project names from the shell");
		assertEquals("VPS", old.envLabel());
		assertEquals(ArchiveEntry.Kind.ARCHIVED, s.entry("vps:arch").kind());
		assertEquals("Forja", s.entry("vps:arch").projectLabel());
	}

	@Test
	void refreshIsRareAndOnlyWhileConnected() {
		FakeSource src = new FakeSource();
		ArchiveStore s = new ArchiveStore(src);
		s.updateEnv("vps", "vps", null, false);
		s.tick(NOW);
		assertTrue(src.calls.isEmpty(), "never reads while offline");

		s.updateEnv("vps", "vps", null, true);
		s.tick(NOW);
		assertEquals(List.of("archived vps", "older vps now"), src.calls);
		for (long t = NOW; t < NOW + ArchiveStore.REFRESH_MS; t += 1_000) {
			s.tick(t);
		}
		assertEquals(2, src.calls.size(), "no polling: nothing within the refresh interval (one tick per second for 10 min)");
		s.tick(NOW + ArchiveStore.REFRESH_MS);
		assertEquals(4, src.calls.size());

		assertFalse(s.refreshNow(NOW + ArchiveStore.REFRESH_MS + 1_000), "manual refresh is ignored right after a read");
		assertTrue(s.refreshNow(NOW + ArchiveStore.REFRESH_MS + ArchiveStore.MANUAL_MIN_MS));
		s.tick(NOW + ArchiveStore.REFRESH_MS + ArchiveStore.MANUAL_MIN_MS);
		assertEquals(6, src.calls.size());
	}

	@Test
	void failuresBackOff() {
		FakeSource src = new FakeSource();
		src.fail = true;
		ArchiveStore s = new ArchiveStore(src);
		s.updateEnv("vps", "vps", null, true);
		s.tick(NOW);
		assertEquals(2, src.calls.size());
		assertEquals("not connected", s.statuses().get(0).error());
		s.tick(NOW + ArchiveStore.BACKOFF_MIN_MS - 1);
		assertEquals(2, src.calls.size());
		s.tick(NOW + ArchiveStore.BACKOFF_MIN_MS);
		assertEquals(4, src.calls.size(), "retried after the first backoff");
		s.tick(NOW + 3 * ArchiveStore.BACKOFF_MIN_MS - 1);
		assertEquals(4, src.calls.size(), "second backoff is twice as long");
		src.fail = false;
		s.tick(NOW + 3 * ArchiveStore.BACKOFF_MIN_MS);
		assertEquals(6, src.calls.size());
		assertNull(s.statuses().get(0).error());
		assertEquals(ArchiveStore.REFRESH_MS, ArchiveStore.backoff(50), "capped");
	}

	@Test
	void paginatesOlderThreadsOnDemandWithCursor() {
		FakeSource src = new FakeSource();
		// 120 settled threads, several sharing a settle time (the cursor must use the id tie-break)
		for (int i = 0; i < 120; i++) {
			src.olderAll.add(settled(String.format("t%03d", i), "p1", (i / 3 + 1) * H));
		}
		ArchiveStore s = new ArchiveStore(src);
		s.updateEnv("vps", "vps", null, true);
		s.tick(NOW);
		assertEquals(50, s.entries().size());
		assertTrue(s.anyMore());
		s.loadMore(NOW + 1);
		assertEquals(100, s.entries().size());
		s.loadMore(NOW + 2);
		assertEquals(120, s.entries().size());
		assertFalse(s.anyMore(), "a short page ends the list");
		s.loadMore(NOW + 3);
		assertEquals(3, src.calls.stream().filter(c -> c.startsWith("older")).count(), "no read past the end");
		List<String> ids = s.entries().stream().map(ArchiveEntry::threadId).toList();
		assertEquals(120, ids.stream().distinct().count());
		assertEquals("t000", ids.get(0));
		assertEquals("t119", ids.get(119));

		// display pages
		assertEquals(10, s.pageCount(12));
		assertEquals(12, s.page(0, 12).size());
		assertEquals(120 - 9 * 12, s.page(9, 12).size());
		assertEquals(s.page(9, 12), s.page(99, 12), "page index is clamped");

		// a scheduled refresh re-reads the newest page and keeps what was loaded beyond it
		s.tick(NOW + ArchiveStore.REFRESH_MS);
		assertEquals(120, s.entries().size());
	}

	@Test
	void resultsApplyOnTickOnlyAndOneReadInFlight() {
		FakeSource src = new FakeSource();
		src.pendingOlder = new CompletableFuture<>();
		ArchiveStore s = new ArchiveStore(src);
		s.updateEnv("vps", "vps", null, true);
		s.tick(NOW);
		s.tick(NOW + ArchiveStore.REFRESH_MS + 1);
		assertEquals(1, src.calls.stream().filter(c -> c.startsWith("older")).count(), "never two older reads at once");
		int v = s.version();
		src.pendingOlder.complete(new ArchiveWire.OlderSettledPage(1, List.of(settled("x", "p", H)), Map.of()));
		assertEquals(v, s.version(), "completion on another thread is not applied until the next tick");
		s.tick(NOW + ArchiveStore.REFRESH_MS + 2);
		assertTrue(s.version() > v);
		assertNotNull(s.entry("vps:x"));
	}

	@Test
	void unavailableSourceShowsShellOnly() {
		ArchiveStore s = new ArchiveStore(ArchiveSource.UNAVAILABLE);
		s.updateEnv("vps", "vps", view(Map.of(), settled("a", "p", H), active("b", "p")), true);
		s.tick(NOW);
		assertFalse(s.fullReads());
		assertEquals(List.of("vps:a"), s.entries().stream().map(ArchiveEntry::key).toList());
		assertNull(s.statuses().get(0).error());
		s.retainEnvs(java.util.Set.of());
		assertTrue(s.entries().isEmpty());
	}

	@Test
	void adapterSendsReadOnlyTagsAndDecodesValues() throws Exception {
		List<String> tags = new ArrayList<>();
		ArchiveSource src = ArchiveSource.of((env, tag, payload) -> {
			tags.add(env + " " + tag + " " + payload);
			JsonObject v = new JsonObject();
			v.addProperty("snapshotSequence", 5);
			return CompletableFuture.completedFuture(List.<com.google.gson.JsonElement>of(v));
		});
		assertEquals(5, src.archived("vps").get().snapshotSequence());
		assertEquals(5, src.older("vps", ArchiveWire.Cursor.before(NOW), 999).get().snapshotSequence());
		assertEquals("vps orchestration.getArchivedShellSnapshot {}", tags.get(0));
		assertTrue(tags.get(1).startsWith("vps orchestration.getOlderSettledThreads "));
		assertTrue(tags.get(1).contains("\"limit\":50"));
	}
}
