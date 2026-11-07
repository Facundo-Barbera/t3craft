package dev.t3craft.client.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.t3.ThreadState;
import org.junit.jupiter.api.Test;

class AttentionAgoBadgeTest {
	@Test
	void attentionRanks() {
		assertEquals(Attention.NEEDS_YOU, Attention.of(ThreadState.NEEDS_ATTENTION, false));
		assertEquals(Attention.ERROR, Attention.of(ThreadState.ERROR, true));
		assertEquals(Attention.PENDING, Attention.of(ThreadState.WORKING, true));
		assertEquals(Attention.PENDING, Attention.of(ThreadState.IDLE, true));
		assertEquals(Attention.WORKING, Attention.of(ThreadState.WORKING, false));
		assertEquals(Attention.DONE, Attention.of(ThreadState.DONE, false));
		assertEquals(Attention.IDLE, Attention.of(ThreadState.IDLE, false));
		assertTrue(Attention.PENDING.urgent());
		assertTrue(!Attention.WORKING.urgent());
	}

	@Test
	void ago() {
		long now = 1_000_000_000_000L;
		assertEquals("", Ago.format(now, null));
		assertEquals("now", Ago.format(now, now - 30_000));
		assertEquals("now", Ago.format(now, now + 5_000));
		assertEquals("5m", Ago.format(now, now - 5 * 60_000));
		assertEquals("3h", Ago.format(now, now - 3 * 3_600_000L));
		assertEquals("2d", Ago.format(now, now - 2 * 86_400_000L));
		assertEquals("3w", Ago.format(now, now - 21 * 86_400_000L));
		assertEquals("1y", Ago.format(now, now - 400 * 86_400_000L));
		assertEquals("5 min ago", Ago.phrase(now, now - 5 * 60_000));
		assertEquals("1 day ago", Ago.phrase(now, now - 86_400_000L));
		assertEquals("just now", Ago.phrase(now, now));
	}

	@Test
	void badgeIsStableWithReadableText() {
		EnvBadge a = EnvBadge.of("my-vps", "my-vps");
		assertEquals("V", a.initial());
		assertEquals("VPS", a.text());
		assertEquals(a, EnvBadge.of("my-vps", "my-vps"));
		assertEquals("M", EnvBadge.of("mini", "Mini Acmesite").initial());
		assertEquals(0xFFFFFFFF & 0xFF000000 | 0xF0F0F0, EnvBadge.of("x", "x", 0xF0F0F0).background());
		assertEquals(0xFF1F1E1D, EnvBadge.of("x", "x", 0xF0F0F0).foreground(), "ink on a light configured colour");
		assertNotEquals(0xFF1F1E1D, EnvBadge.of("x", "x", 0x202020).foreground());
	}
}
