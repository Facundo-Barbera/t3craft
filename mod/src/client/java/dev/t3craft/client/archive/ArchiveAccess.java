package dev.t3craft.client.archive;

import java.util.List;

/**
 * Where the archive is reached from. Pure.
 *
 * <ul>
 *   <li>Blocks module on: the {@code t3craft:archive_terminal} block (in the HQ hub, at the plan's
 *   {@code archiveTerminal} anchor, or placed by hand), plus the command and the portable.</li>
 *   <li>Blocks module off ("GUI only"): no terminal exists; the same archive screen opens with
 *   {@value #COMMAND} and from the portable terminal's "Archive" button.</li>
 * </ul>
 * The command and the portable button are always there, so a world without the block is never cut
 * off from the archive.
 */
public final class ArchiveAccess {
	/** The command that opens the archive screen (registered in every mode). */
	public static final String COMMAND = "/t3craft archive";

	public enum Route {
		/** Using the archive terminal block. */
		TERMINAL,
		/** {@value ArchiveAccess#COMMAND}. */
		COMMAND,
		/** The portable terminal's "Archive" button. */
		PORTABLE
	}

	private ArchiveAccess() {
	}

	public static List<Route> routes(boolean blocksModule) {
		return blocksModule ? List.of(Route.TERMINAL, Route.COMMAND, Route.PORTABLE) : List.of(Route.COMMAND, Route.PORTABLE);
	}

	/** Lang key of the one-line "how to open the archive" hint, with {@link #COMMAND} as its argument. */
	public static String hintKey(boolean blocksModule) {
		return blocksModule ? "t3craft.archive.hint.terminal" : "t3craft.archive.hint.command";
	}
}
