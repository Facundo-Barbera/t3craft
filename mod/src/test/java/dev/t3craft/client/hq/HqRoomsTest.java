package dev.t3craft.client.hq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.client.npc.NpcInfo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** fix/hq-rooms-real: a project has a room only with real activity inside the window. */
class HqRoomsTest {
	static final long HOUR = 3_600_000L;
	static final long NOW = 1_000L * 24 * HOUR * 400;

	static NpcInfo npc(String env, String thread, String project, String state, boolean settled) {
		return new NpcInfo(env + ":" + thread, thread, thread, env, "", 0xFFFFFF, null, 0, settled, state, env + ":" + project, project, "",
				null);
	}

	static HqRooms.Context ctx(Map<String, Long> last) {
		return HqRooms.context(true, last, env -> 24 * HOUR);
	}

	@Test
	void projectInactiveForLongerThanTheWindowHasNoRoom() {
		// telar: one thread that "needs you" (always visible) but quiet for 40 days
		NpcInfo telar = npc("vps", "t1", "telar", "needs_attention", false);
		NpcInfo craft = npc("vps", "c1", "t3craft", "working", false);
		HqRooms.Context c = ctx(Map.of("vps:t1", NOW - 40 * 24 * HOUR, "vps:c1", NOW - HOUR));
		assertEquals(Set.of("vps:t3craft"), HqRooms.rooms(List.of(telar, craft), NpcInfo::settled, c, NOW));
		assertEquals(List.of(craft), HqRooms.filter(List.of(telar, craft), c, NOW));
	}

	@Test
	void projectActiveInsideTheWindowHasARoom() {
		NpcInfo a = npc("vps", "t1", "telar", "idle", false);
		HqRooms.Context c = ctx(Map.of("vps:t1", NOW - 23 * HOUR));
		assertEquals(List.of(a), HqRooms.filter(List.of(a), c, NOW));
	}

	@Test
	void oneRecentThreadKeepsTheWholeProjectAndItsOlderThreads() {
		NpcInfo old = npc("vps", "o", "forja", "idle", false);
		NpcInfo fresh = npc("vps", "f", "forja", "working", false);
		HqRooms.Context c = ctx(Map.of("vps:o", NOW - 30 * 24 * HOUR, "vps:f", NOW - 60_000));
		assertEquals(List.of(old, fresh), HqRooms.filter(List.of(old, fresh), c, NOW));
	}

	@Test
	void settledOrReleasedThreadsDoNotKeepARoom() {
		NpcInfo settled = npc("vps", "s", "telar", "done", true);
		NpcInfo released = npc("vps", "r", "telar", "done", false);
		HqRooms.Context c = ctx(Map.of("vps:s", NOW - HOUR, "vps:r", NOW - HOUR));
		assertTrue(HqRooms.filter(List.of(settled), c, NOW).isEmpty());
		// the release rule is fix/hq-stale-stations' (passed in), never re-implemented here
		assertTrue(HqRooms.filter(List.of(released), n -> n.id().equals("vps:r"), c, NOW).isEmpty());
	}

	@Test
	void perEnvironmentWindow() {
		NpcInfo a = npc("vps", "a", "p", "idle", false);
		NpcInfo b = npc("laptop", "b", "q", "idle", false);
		HqRooms.Context c = HqRooms.context(true, Map.of("vps:a", NOW - 30 * HOUR, "laptop:b", NOW - 30 * HOUR),
				env -> env.equals("vps") ? 48 * HOUR : 24 * HOUR);
		assertEquals(List.of(a), HqRooms.filter(List.of(a, b), c, NOW));
	}

	@Test
	void unknownActivityCountsAsActive() {
		NpcInfo a = npc("bridge", "a", "p", "idle", false);
		assertEquals(List.of(a), HqRooms.filter(List.of(a), HqRooms.NONE, NOW));
		assertFalse(HqRooms.NONE.paired());
	}
}
