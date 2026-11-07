package dev.t3craft.client.chat;

/**
 * What the chat can do for one environment: where its history comes from and what the reply bar
 * shows. Pure Java, tested; the Minecraft side ({@link ChatScreens}, {@link ConversationPanel}) only
 * asks these questions.
 *
 * <p><b>History over the bridge.</b> Protocol v1 defines {@code chat.open} / {@code chat.history}
 * (bridge/src/protocol.ts), but the bridge answers every mod message with "not implemented"
 * (bridge/src/main.ts, phase 2 TODO). So in bridge mode there is no read-only history to fall back on:
 * the conversation shows an empty state with a button to the pairing flow (direct mode).
 */
public final class ChatAccess {
	private ChatAccess() {
	}

	/** Where a thread's history is read from. */
	public enum History {
		/** Direct mode, environment paired: the environment's own connection. */
		DIRECT,
		/** Bridge mode: no history (the bridge does not serve it); offer to pair for direct mode. */
		PAIR_FOR_DIRECT,
		/** Direct mode but this environment has no connection: offer to pair it. */
		NOT_PAIRED
	}

	public static History history(boolean directMode, boolean envConnected) {
		if (!directMode) {
			return History.PAIR_FOR_DIRECT;
		}
		return envConnected ? History.DIRECT : History.NOT_PAIRED;
	}

	/** The reply bar under the conversation. */
	public enum Reply {
		/** No thread shown: just the prompt. */
		NONE,
		/** The environment can operate: the bar is ready, with no read-only text (sending is item 19). */
		READY,
		/** Read-only: a neutral line and an "Enable control" button to {@code OperatePairScreen}. */
		READ_ONLY
	}

	public static Reply reply(boolean threadShown, boolean canOperate) {
		if (!threadShown) {
			return Reply.NONE;
		}
		return canOperate ? Reply.READY : Reply.READ_ONLY;
	}

	/** True when the reply bar shows the "Enable control" button. */
	public static boolean showsEnableControl(boolean threadShown, boolean canOperate) {
		return reply(threadShown, canOperate) == Reply.READ_ONLY;
	}
}
