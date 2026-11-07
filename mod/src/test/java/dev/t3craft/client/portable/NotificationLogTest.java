package dev.t3craft.client.portable;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NotificationLogTest {
	static NotificationLog.Entry entry(int i) {
		return new NotificationLog.Entry("vps:t" + i, "Thread finished", "t" + i, 0x55FF55, "done", 1_000L * i);
	}

	@Test
	void emptyByDefault() {
		NotificationLog log = new NotificationLog();
		assertEquals(0, log.size());
		assertEquals(0, log.unread());
		assertTrue(log.entries().isEmpty());
	}

	@Test
	void newestFirstWithUnreadCounter() {
		NotificationLog log = new NotificationLog();
		log.record(entry(1));
		log.record(entry(2));
		assertEquals("t2", log.entries().get(0).text());
		assertEquals(2, log.unread());
		log.markAllRead();
		assertEquals(0, log.unread());
		assertEquals(2, log.size(), "reading does not delete");
	}

	@Test
	void boundedRing() {
		NotificationLog log = new NotificationLog();
		for (int i = 0; i < NotificationLog.CAPACITY + 7; i++) {
			log.record(entry(i));
		}
		assertEquals(NotificationLog.CAPACITY, log.size());
		assertEquals(NotificationLog.CAPACITY, log.unread());
		assertEquals("t" + (NotificationLog.CAPACITY + 6), log.entries().get(0).text());
		assertEquals("t7", log.entries().get(NotificationLog.CAPACITY - 1).text());
	}

	@Test
	void entryWithoutThreadIsAllowed() {
		NotificationLog log = new NotificationLog();
		log.record(new NotificationLog.Entry(null, "T3", "bridge notice", 0xAAAAAA, "", 0));
		assertNull(log.entries().get(0).npcId());
		log.clear();
		assertEquals(0, log.size());
	}
}
