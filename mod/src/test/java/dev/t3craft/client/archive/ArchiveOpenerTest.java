package dev.t3craft.client.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.client.chat.ChatAccess;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Picking a closed thread opens it in the chat, read-only. */
class ArchiveOpenerTest {
	@Test
	void requestIsTheThreadReadOnly() {
		ArchiveEntry e = ArchiveEntry.of("vps", "VPS", ArchiveFixtures.archived("thr_9", "p1", ArchiveFixtures.H), "t3craft");
		ArchiveOpener.Request r = ArchiveOpener.request(e);
		assertEquals("vps:thr_9", r.key());
		assertEquals("vps", r.env());
		assertEquals("thr_9", r.threadId());
		assertEquals("Thread thr_9", r.title());
		assertEquals("VPS", r.envLabel());
		assertTrue(r.readOnly());
	}

	@Test
	void theOpenerHandsEveryPickAsReadOnly() {
		List<ArchiveOpener.Request> got = new ArrayList<>();
		ArchiveOpener o = ArchiveOpener.of(got::add);
		o.open(ArchiveFixtures.entry("mini", "a", "p", ArchiveFixtures.H));
		o.open(ArchiveEntry.of("vps", "VPS", ArchiveFixtures.archived("b", "p", 2 * ArchiveFixtures.H), null));
		assertEquals(List.of("mini:a", "vps:b"), got.stream().map(ArchiveOpener.Request::key).toList());
		assertTrue(got.stream().allMatch(ArchiveOpener.Request::readOnly));
		// the chat opened without actions answers "read-only" (no send, no "Enable control")
		assertEquals(ChatAccess.Reply.READ_ONLY, ChatAccess.reply(true, false));
	}
}
