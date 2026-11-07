package dev.t3craft.client.operate;

import static org.junit.jupiter.api.Assertions.*;

import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.t3.ThreadOps;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThreadOpsCommandsTest {
	static NpcInfo npc(String id, String title) {
		return new NpcInfo(id, title, title, "VPS", "", 0xFFFFFF, null, 1, false, "idle", "g", "G", "s", null);
	}

	static final List<NpcInfo> NPCS = List.of(npc("vps:t-1", "Fix the build"), npc("vps:t-2", "Docs"), npc("lap:t-3", "Docs"));

	@Test
	void resolvesIdsTitlesAndBareTargets() {
		ThreadOps.Target byId = ThreadOpsCommands.resolve(NPCS, "vps:t-1");
		assertEquals(new ThreadOps.Target("vps", "t-1", "Fix the build"), byId);
		assertEquals(new ThreadOps.Target("vps", "t-1", "Fix the build"), ThreadOpsCommands.resolve(NPCS, "  fix THE build "));
		assertNull(ThreadOpsCommands.resolve(NPCS, "Docs"), "ambiguous title");
		assertEquals(new ThreadOps.Target("other", "t-9", null), ThreadOpsCommands.resolve(NPCS, "other:t-9"));
		assertNull(ThreadOpsCommands.resolve(NPCS, "no such thread"));
		assertNull(ThreadOpsCommands.resolve(NPCS, ""));
	}

	@Test
	void snoozeTakesTheDurationFromTheLastWord() {
		assertEquals(new ThreadOpsCommands.SnoozeArgs("vps:t-1", ThreadOps.Snooze.HOURS_4), ThreadOpsCommands.SnoozeArgs.parse("vps:t-1 4h"));
		assertEquals(new ThreadOpsCommands.SnoozeArgs("Fix the build", ThreadOps.Snooze.TOMORROW),
				ThreadOpsCommands.SnoozeArgs.parse("Fix the build tomorrow"));
		assertEquals(new ThreadOpsCommands.SnoozeArgs("Fix the build", ThreadOps.Snooze.HOUR_1), ThreadOpsCommands.SnoozeArgs.parse("Fix the build"));
	}
}
