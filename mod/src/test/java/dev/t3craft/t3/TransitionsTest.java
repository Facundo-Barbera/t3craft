package dev.t3craft.t3;

import static dev.t3craft.t3.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransitionsTest {
	static NpcView v(String env, String id, String extra) {
		return Presentation.present(env, env + "-label", thread(id, H, extra), "proj");
	}

	@Test
	void notifiesRealTransitionsOnly() {
		var working = v("vps", "a", "{\"status\":\"running\"}");
		var waiting = v("vps", "a", "{\"status\":\"waiting\"}");
		var t = Transitions.of(working, waiting);
		assertEquals(Transitions.Sound.ATTENTION, t.sound());
		assertEquals(ThreadState.WORKING, t.from());
		assertNull(Transitions.of(null, waiting), "new NPCs are seeded silently");
		assertNull(Transitions.of(waiting, waiting));
		assertEquals(Transitions.Sound.DONE, Transitions.of(working, v("vps", "a", "{\"status\":\"completed\"}")).sound());
		assertEquals(Transitions.Sound.ERROR, Transitions.of(working, v("vps", "a", "{\"status\":\"failed\"}")).sound());
		assertNull(Transitions.of(v("vps", "a", ""), v("vps", "a", "{\"status\":\"completed\"}")), "idle → done is not news");
	}

	@Test
	void silentUntilEnvironmentWasSynced() {
		var before = Map.of("vps:a", v("vps", "a", "{\"status\":\"running\"}"), "mini:a", v("mini", "a", "{\"status\":\"running\"}"));
		var after = List.of(v("vps", "a", "{\"status\":\"failed\"}"), v("mini", "a", "{\"status\":\"failed\"}"));
		var ts = Transitions.between(before, after, Set.of("vps"));
		assertEquals(1, ts.size());
		assertEquals("vps:a", ts.get(0).npcId());
	}

	@Test
	void peekPrefersAttentionThenWorking() {
		var w = v("vps", "w", "{\"status\":\"running\",\"title\":\"Build\"}");
		var a = v("mini", "x", "{\"status\":\"waiting\",\"title\":\"Login\"}");
		var i = v("vps", "i", "");
		assertEquals("mini:x", Transitions.peek(List.of(a, w, i)).npcId());
		assertTrue(Transitions.peek(List.of(a, w, i)).text().contains("Login [mini-label]"));
		assertEquals("vps:w", Transitions.peek(List.of(w, i)).npcId());
		assertNull(Transitions.peek(List.of(i)).text());
	}
}
