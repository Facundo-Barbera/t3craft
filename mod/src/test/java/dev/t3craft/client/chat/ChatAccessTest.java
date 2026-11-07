package dev.t3craft.client.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChatAccessTest {
	@Test
	void historyComesFromDirectModeWhenPaired() {
		assertEquals(ChatAccess.History.DIRECT, ChatAccess.history(true, true));
	}

	@Test
	void bridgeModeHasNoHistoryAndOffersPairing() {
		// the bridge does not serve chat.history (protocol v1 defines it, the bridge answers "not implemented")
		assertEquals(ChatAccess.History.PAIR_FOR_DIRECT, ChatAccess.history(false, false));
		assertEquals(ChatAccess.History.PAIR_FOR_DIRECT, ChatAccess.history(false, true));
	}

	@Test
	void directModeWithoutConnectionOffersPairing() {
		assertEquals(ChatAccess.History.NOT_PAIRED, ChatAccess.history(true, false));
	}

	@Test
	void unavailableCarriesThePairAction() {
		assertEquals(ChatSource.Unavailable.Action.NONE, new ChatSource.Unavailable("x").action());
		assertEquals(ChatSource.Unavailable.Action.PAIR, new ChatSource.Unavailable("x", ChatSource.Unavailable.Action.PAIR).action());
	}

	@Test
	void enableControlButtonOnlyWhenReadOnly() {
		assertEquals(ChatAccess.Reply.READ_ONLY, ChatAccess.reply(true, false));
		assertTrue(ChatAccess.showsEnableControl(true, false));
		// operate token present: the bar is ready, no button and no read-only text
		assertEquals(ChatAccess.Reply.READY, ChatAccess.reply(true, true));
		assertFalse(ChatAccess.showsEnableControl(true, true));
		// no thread shown: nothing to enable
		assertEquals(ChatAccess.Reply.NONE, ChatAccess.reply(false, false));
		assertFalse(ChatAccess.showsEnableControl(false, false));
		assertFalse(ChatAccess.showsEnableControl(false, true));
	}
}
