package dev.t3craft.client.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.client.hq.HqRooms;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** fix/hq-rooms-real: the demo HQ's rooms and signs come from real, recent projects only. */
class DemoHqRoomsTest {
	static final long HOUR = 3_600_000L;
	static final long NOW = 1_000L * 24 * HOUR * 400;
	private static final int LIFT = DemoHq.lift(-60, -64, -5);

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	static NpcInfo npc(String env, String envLabel, String thread, String project, String label) {
		return new NpcInfo(env + ":" + thread, thread, thread, envLabel, "", 0xFFFFFF, null, 0, false, "working", env + ":" + project, label, "",
				null);
	}

	@Test
	void neverTheExampleRosterWhenEnvironmentsArePaired() {
		HqRooms.Context paired = HqRooms.context(true, Map.of(), e -> 24 * HOUR);
		DemoHq.Roster r = DemoHq.chooseRoster(List.of(), paired, NOW);
		assertFalse(r.sample(), "paired but nothing loaded yet: empty HQ, not the example");
		assertEquals(HqInput.EMPTY, r.input());
		assertNull(r.input().project("vps:telar"));
		// paired, with data: the live projects, no example project mixed in
		DemoHq.Roster live = DemoHq.chooseRoster(List.of(npc("vps", "VPS", "a", "t3craft", "t3craft")), paired, NOW);
		assertFalse(live.sample());
		assertEquals(List.of("vps:t3craft"), live.input().projects().stream().map(HqInput.Project::key).toList());
		// paired, every project stale: still no example
		HqRooms.Context stale = HqRooms.context(true, Map.of("vps:a", NOW - 40 * 24 * HOUR), e -> 24 * HOUR);
		DemoHq.Roster none = DemoHq.chooseRoster(List.of(npc("vps", "VPS", "a", "telar", "telar")), stale, NOW);
		assertFalse(none.sample());
		assertTrue(none.input().projects().isEmpty());
	}

	@Test
	void exampleOnlyWithNothingPaired() {
		assertTrue(DemoHq.chooseRoster(List.of(), HqRooms.NONE, NOW).sample());
	}

	@Test
	void staleProjectLosesItsRoomInTheDiff() {
		HqRooms.Context both = HqRooms.context(true, Map.of("vps:a", NOW - HOUR, "vps:b", NOW - HOUR), e -> 24 * HOUR);
		List<NpcInfo> npcs = List.of(npc("vps", "VPS", "a", "t3craft", "t3craft"), npc("vps", "VPS", "b", "telar", "telar"));
		DemoHq.Step first = DemoHq.step(DemoHq.chooseRoster(npcs, both, NOW).input(), null, null, LIFT, true);
		assertEquals(2, first.layout().modules().size());
		HqRooms.Context later = HqRooms.context(true, Map.of("vps:a", NOW - HOUR, "vps:b", NOW - 25 * HOUR), e -> 24 * HOUR);
		DemoHq.Step next = DemoHq.step(DemoHq.chooseRoster(npcs, later, NOW).input(), first.layout(), first.hash(), LIFT, true);
		assertEquals(List.of("vps:t3craft"), next.layout().modules().stream().map(HqLayout.Module::projectKey).toList());
		assertFalse(next.upToDate(), "the telar module is removed by the diff");
	}

	@Test
	void signShowsTheRealProjectAndEnvironmentAndFollowsARename() {
		HqRooms.Context c = HqRooms.context(true, Map.of(), e -> 24 * HOUR);
		DemoHq.Step s = DemoHq.step(DemoHq.chooseRoster(List.of(npc("vps", "My VPS", "a", "p1", "t3craft")), c, NOW).input(), null, null, LIFT,
				true);
		HqLayout.Module m = s.layout().modules().getFirst();
		assertEquals("t3craft", m.label());
		assertEquals("My VPS", m.envLabel());
		DemoHq.Step renamed = DemoHq.step(DemoHq.chooseRoster(List.of(npc("vps", "My VPS", "a", "p1", "t3craft-2")), c, NOW).input(),
				s.layout(), s.hash(), LIFT, true);
		assertEquals("t3craft-2", renamed.layout().modules().getFirst().label());
		assertNotEquals(s.hash(), renamed.hash(), "a renamed project rebuilds its sign");
		assertFalse(renamed.upToDate());
	}
}
