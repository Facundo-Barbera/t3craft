package dev.t3craft.client.portable;

import static dev.t3craft.client.portable.PortableFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PortableStateTest {
	@Test
	void sortsNeedsAttentionFirstAndCountsPerEnvironment() {
		PortableState s = twoEnvs();
		assertEquals(PortableState.Connection.CONNECTED, s.connection());
		assertEquals(List.of("Bravo", "Alpha", "Charlie", "Delta"), s.threads().stream().map(PortableThread::title).toList());
		assertEquals(1, s.needsAttention());
		PortableEnv vps = s.environments().get(0);
		assertEquals("vps", vps.id());
		assertEquals(3, vps.threads());
		assertEquals(1, vps.needsAttention());
		assertEquals(1, s.environments().get(1).threads());
		assertEquals(3, s.threadsOf("vps").size());
		assertEquals(4, s.threadsOf(null).size());
	}

	@Test
	void notConfiguredWhenNothingIsPaired() {
		PortableState s = PortableState.of("direct · read-only", Map.of(), List.of(), false, null);
		assertEquals(PortableState.Connection.NOT_CONFIGURED, s.connection());
		assertTrue(s.threads().isEmpty());
	}

	@Test
	void disconnectedWhenNoEnvironmentIsOk() {
		PortableState s = PortableState.of("direct", envs("vps", "offline", "mini", "auth_failed"), List.of(), true, null);
		assertEquals(PortableState.Connection.DISCONNECTED, s.connection());
		assertEquals("offline", s.environments().get(0).status());
	}

	@Test
	void oneEnvironmentOkIsEnough() {
		PortableState s = PortableState.of("direct", envs("vps", "offline", "mini", "ok"), List.of(), true, null);
		assertEquals(PortableState.Connection.CONNECTED, s.connection());
	}

	@Test
	void bridgeLinkDecides() {
		var rows = List.of(thread("vps", "a", "Alpha", "working", 2));
		assertEquals(PortableState.Connection.DISCONNECTED, PortableState.of("bridge", Map.of(), rows, true, false).connection(),
				"stale rows must not look connected when the bridge link is down");
		assertEquals(PortableState.Connection.CONNECTED, PortableState.of("bridge", Map.of(), List.of(), true, true).connection(),
				"bridge up without per-environment statuses yet");
	}

	@Test
	void splitsNpcIds() {
		assertArrayEquals(new String[] {"vps", "thread:with:colons"}, PortableThread.splitId("vps:thread:with:colons"));
		assertNull(PortableThread.splitId("noseparator"));
		assertNull(PortableThread.splitId(":x"));
		assertNull(PortableThread.splitId("x:"));
	}
}
