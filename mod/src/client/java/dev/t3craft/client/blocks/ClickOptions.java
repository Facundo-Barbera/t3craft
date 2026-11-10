package dev.t3craft.client.blocks;

import java.util.Map;

/**
 * The options of the blocks that have no screen of their own but react to a click (issue #43), stored as strings in the
 * block entity's {@code options} compound and read by {@link BlocksClient} when the block is used. A missing or unknown
 * value is the default, today's behaviour.
 *
 * <pre>
 * console terminal:  opens:  "bound" (default: the bound thread's chat, or a new thread when unbound) | "new" (always a new thread)
 * decision podium:   nobody: "message" (default: a chat line when no thread waits) | "silent"
 * </pre>
 */
public final class ClickOptions {
	public static final String CONSOLE_OPENS = "opens";
	public static final String CONSOLE_BOUND = "bound";
	public static final String CONSOLE_NEW = "new";
	public static final String PODIUM_NOBODY = "nobody";
	public static final String PODIUM_MESSAGE = "message";
	public static final String PODIUM_SILENT = "silent";

	private ClickOptions() {
	}

	/** True when the console always starts a new thread, even when it is bound to one. */
	public static boolean consoleAlwaysNew(Map<String, String> options) {
		return options != null && CONSOLE_NEW.equals(options.get(CONSOLE_OPENS));
	}

	/** True when a podium nobody waits at says nothing. */
	public static boolean podiumSilent(Map<String, String> options) {
		return options != null && PODIUM_SILENT.equals(options.get(PODIUM_NOBODY));
	}
}
