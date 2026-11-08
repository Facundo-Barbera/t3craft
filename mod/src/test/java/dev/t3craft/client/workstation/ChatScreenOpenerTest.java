package dev.t3craft.client.workstation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ChatScreenOpenerTest {
	@Test
	void listedThreadOpensByItsNpcKey() {
		assertEquals("vps:thr_1", ChatScreenOpener.key("vps", "thr_1", Set.of("vps:thr_1")::contains));
	}

	@Test
	void bridgeModeUsesTheBareThreadId() {
		assertEquals("thr_1", ChatScreenOpener.key("", "thr_1", Set.of("thr_1")::contains));
		assertEquals("thr_1", ChatScreenOpener.key("vps", "thr_1", Set.of("thr_1")::contains));
	}

	@Test
	void unlistedThreadOfAKnownEnvironmentStillOpens() {
		// the placeholder used to do nothing here; the browser shows unlisted threads
		assertEquals("vps:thr_9", ChatScreenOpener.key("vps", "thr_9", k -> false));
	}

	@Test
	void nothingToOpen() {
		assertNull(ChatScreenOpener.key("", "thr_9", k -> false));
		assertNull(ChatScreenOpener.key("vps", "", k -> true));
	}
}
