package dev.t3craft.client.workstation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorkstationCacheTest {
	private final WorkstationCache cache = new WorkstationCache(new WorkstationModel());

	private static WorkstationThread t(StationStatus s, String activity) {
		return new WorkstationThread("vps:t1", "vps", "t1", "Title", "g", 2, s, activity, null);
	}

	@Test
	void firstSightRendersWithUnknownAge() {
		StationScreen s = cache.update(t(StationStatus.WORKING, "a"), 1_000);
		assertNotNull(s);
		assertEquals("⚙ WORKING", s.lines().get(1).text());
		assertEquals(1, cache.version("vps:t1"));
	}

	@Test
	void unchangedThreadIsNotReRendered() {
		cache.update(t(StationStatus.WORKING, "a"), 1_000);
		for (long now = 1_050; now < 100_000; now += 50) {
			assertNull(cache.update(t(StationStatus.WORKING, "a"), now));
		}
		assertEquals(1, cache.version("vps:t1"));
	}

	@Test
	void changeInvalidatesAndStartsAgeClock() {
		cache.update(t(StationStatus.WORKING, "a"), 1_000);
		StationScreen s = cache.update(t(StationStatus.ATTENTION, "Approval needed"), 10_000);
		assertNotNull(s);
		assertEquals("! NEEDS YOU · now", s.lines().get(1).text());
		assertEquals(2, cache.version("vps:t1"));
		// Same content, same age bucket → nothing.
		assertNull(cache.update(t(StationStatus.ATTENTION, "Approval needed"), 12_000));
		// Age label moves on → redraw with the new age only.
		StationScreen later = cache.update(t(StationStatus.ATTENTION, "Approval needed"), 10_000 + 30_000);
		assertNotNull(later);
		assertEquals("! NEEDS YOU · 30s", later.lines().get(1).text());
		// Within the same minute bucket after 60 s → one redraw per minute at most.
		assertNotNull(cache.update(t(StationStatus.ATTENTION, "Approval needed"), 10_000 + 61_000));
		assertNull(cache.update(t(StationStatus.ATTENTION, "Approval needed"), 10_000 + 100_000));
	}

	@Test
	void activityChangeAloneInvalidates() {
		cache.update(t(StationStatus.WORKING, "step 1"), 0);
		StationScreen s = cache.update(t(StationStatus.WORKING, "step 2"), 1);
		assertNotNull(s);
		assertEquals("step 2", s.lines().get(2).text());
	}

	@Test
	void sourceTimestampWinsOverObservedChange() {
		WorkstationThread known = new WorkstationThread("k", "e", "t", "T", "g", 0, StationStatus.DONE, "x", 0L);
		StationScreen s = cache.update(known, 3 * 3_600_000L);
		assertNotNull(s);
		assertEquals("✔ DONE · 3h", s.lines().get(1).text());
	}

	@Test
	void invalidateAllForcesRedrawOfSameContent() {
		cache.update(t(StationStatus.IDLE, "a"), 0);
		assertNull(cache.update(t(StationStatus.IDLE, "a"), 1));
		cache.invalidateAll();
		StationScreen again = cache.update(t(StationStatus.IDLE, "a"), 2);
		assertNotNull(again);
		assertEquals(again, cache.screen("vps:t1"));
	}

	@Test
	void retainForgetsGoneThreads() {
		cache.update(t(StationStatus.IDLE, "a"), 0);
		cache.update(new WorkstationThread("other", "e", "o", "O", "g", 0, StationStatus.IDLE, null, null), 0);
		assertEquals(2, cache.size());
		cache.retain(List.of("other"));
		assertEquals(1, cache.size());
		assertNull(cache.screen("vps:t1"));
		assertEquals(0, cache.version("vps:t1"));
	}

	@Test
	void settleMarksArchived() {
		cache.update(t(StationStatus.DONE, "ok"), 0);
		StationScreen s = cache.update(t(StationStatus.ARCHIVED, "ok"), 5);
		assertNotNull(s);
		assertEquals(StationStatus.ARCHIVED, s.status());
		assertEquals(StationStatus.ARCHIVED.background(), s.background());
	}
}
