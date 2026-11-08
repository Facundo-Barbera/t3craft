package dev.t3craft.client.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.t3craft.client.demo.DemoGateAccess;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** fix/hq-build-parallel: the structure first, the NPCs once it stands. */
class NpcGateTest {
	@AfterEach
	void reset() {
		NpcGate.open();
	}

	private static NpcInfo npc(String id) {
		return new NpcInfo(id, id, id, "vps", "", 0xFFFFFF, null, 0, false, "idle", "vps:p", "p", "", null);
	}

	private static Map<String, NpcInfo> wanted(String... ids) {
		Map<String, NpcInfo> m = new LinkedHashMap<>();
		for (String id : ids) {
			m.put(id, npc(id));
		}
		return m;
	}

	@Test
	void fullBuildHoldsEveryoneDiffHoldsOnlyNewcomers() {
		Map<String, NpcInfo> w = wanted("a", "b", "new");
		assertEquals(w.keySet(), NpcGate.filter(NpcGate.Mode.OPEN, w, Set.of("a")).keySet());
		assertEquals(Set.of(), NpcGate.filter(NpcGate.Mode.HOLD_ALL, w, Set.of("a", "b")).keySet(), "nobody on a half-built site");
		assertEquals(List.of("a", "b"), List.copyOf(NpcGate.filter(NpcGate.Mode.HOLD_NEW, w, Set.of("a", "b", "gone")).keySet()),
				"the ones at work stay, the newcomer waits");
	}

	@Test
	void demoChoosesTheModeAndEveryChangeIsSeenOnce() {
		assertEquals(NpcGate.Mode.HOLD_ALL, DemoGateAccess.gateFor(false), "manual build / first build from scratch");
		assertEquals(NpcGate.Mode.HOLD_NEW, DemoGateAccess.gateFor(true), "automatic diff for a roster change");
		int v = NpcGate.version();
		NpcGate.set(NpcGate.Mode.HOLD_ALL);
		NpcGate.set(NpcGate.Mode.HOLD_ALL);
		assertEquals(v + 1, NpcGate.version(), "no change, no bump");
		NpcGate.open();
		assertNotEquals(v + 1, NpcGate.version(), "opening bumps so the registry spawns them");
	}
}
