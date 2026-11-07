package dev.t3craft.client.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** How the archive is reached: the terminal with the blocks module, the command (and portable) without it. */
class ArchiveAccessTest {
	@Test
	void withoutTheBlocksModuleTheCommandOpensTheArchive() {
		List<ArchiveAccess.Route> off = ArchiveAccess.routes(false);
		assertFalse(off.contains(ArchiveAccess.Route.TERMINAL), "no terminal block without the module");
		assertEquals(ArchiveAccess.Route.COMMAND, off.getFirst(), "GUI only: the command first");
		assertTrue(off.contains(ArchiveAccess.Route.PORTABLE));
		assertEquals("t3craft.archive.hint.command", ArchiveAccess.hintKey(false));
		assertEquals("/t3craft archive", ArchiveAccess.COMMAND);
	}

	@Test
	void withTheBlocksModuleTheTerminalComesFirstAndTheCommandStays() {
		assertEquals(List.of(ArchiveAccess.Route.TERMINAL, ArchiveAccess.Route.COMMAND, ArchiveAccess.Route.PORTABLE), ArchiveAccess.routes(true));
		assertEquals("t3craft.archive.hint.terminal", ArchiveAccess.hintKey(true));
	}

	@Test
	void theCommandRunsTheOpener() throws Exception {
		CommandDispatcher<Object> d = new CommandDispatcher<>();
		AtomicInteger opened = new AtomicInteger();
		d.register(ArchiveClient.openCommand(opened::incrementAndGet));
		assertEquals(1, d.execute(ArchiveAccess.COMMAND.substring(1), new Object()));
		assertEquals(1, opened.get());
	}
}
