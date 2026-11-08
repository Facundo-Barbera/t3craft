package dev.t3craft.client.npc.behavior;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * fix/npc-position-reset: a new agent appearing (or one leaving, or the HQ growing a desk) must not
 * put the other NPCs back on their first spot. With N NPCs at arbitrary positions (some walking, some
 * at their desk), a change of anchors re-plans only the NPCs whose own spot moved; the others go on
 * exactly as if nothing had happened (same position, phase and route, tick after tick), and the
 * affected ones walk from where they stand, never jump.
 */
class PositionResetTest {
	static final NpcBrain.Navigator NAV = new GridPathfinder(Walkable.flat(64))::find;
	static final String GROUP = "env/proj";
	/** Max distance a walking NPC covers in one tick (well above walking pace, far below a jump). */
	static final double MAX_STEP = 0.5;

	/** A physical base: one desk per NPC, a shared home. */
	record Desks(Map<String, Anchor> desks) implements AnchorSource {
		Desks {
			desks = Map.copyOf(desks);
		}

		@Override
		public @Nullable Anchor desk(String npcId, String groupId) {
			return desks.get(npcId);
		}

		@Override
		public @Nullable Anchor home(String groupId) {
			return Anchor.at(20.5, 64, 20.5, 0);
		}

		Desks with(String id, Anchor a) {
			Map<String, Anchor> m = new HashMap<>(desks);
			m.put(id, a);
			return new Desks(m);
		}

		Desks without(String id) {
			Map<String, Anchor> m = new HashMap<>(desks);
			m.remove(id);
			return new Desks(m);
		}
	}

	/** N NPCs, each with its own desk, starting at arbitrary positions. */
	static final class Crowd {
		final Map<String, NpcBrain> brains = new LinkedHashMap<>();
		AnchorSource anchors;

		Crowd(int n) {
			Map<String, Anchor> desks = new HashMap<>();
			Map<String, Anchor> starts = new HashMap<>();
			for (int i = 0; i < n; i++) {
				String id = "vps:t" + i;
				desks.put(id, Anchor.at(2.5 + i * 3, 64, 2.5, 0));
				// arbitrary start, near (no snap) but not on the desk: most walk for a while
				Anchor start = Anchor.at(1.5 + (i * 7) % 11, 64, 9.5 + (i * 5) % 7 + i * 2, 0);
				starts.put(id, start);
				brains.put(id, new NpcBrain(id, start.pos()));
			}
			// first tick places everyone on their start spot, then they head for their desks
			anchors = new Desks(starts);
			tick();
			anchors = new Desks(desks);
		}

		void tick() {
			for (NpcBrain b : brains.values()) {
				Anchor desk = anchors.desk(b.id(), GROUP);
				if (desk == null) {
					desk = new Anchor(b.pos(), b.bodyYaw());
				}
				b.tick(Mode.WORKING, false, new NpcBrain.Targets(desk, null, desk, 3, desk), null, NAV);
			}
		}

		Map<String, String> groups() {
			Map<String, String> g = new HashMap<>();
			brains.keySet().forEach(id -> g.put(id, GROUP));
			return g;
		}

		/** What BehaviorDirector does on setAnchorSource + next tick. */
		Set<String> change(AnchorSource now) {
			AnchorSource before = anchors;
			anchors = now;
			return BehaviorDirector.replanAffected(brains, groups(), before, now);
		}
	}

	static void assertSame(Crowd control, Crowd test, String id, int t) {
		NpcBrain a = control.brains.get(id);
		NpcBrain b = test.brains.get(id);
		assertEquals(a.pos(), b.pos(), id + " position at tick " + t);
		assertEquals(a.phase(), b.phase(), id + " phase at tick " + t);
		assertEquals(a.walking(), b.walking(), id + " walking at tick " + t);
		assertEquals(a.bodyYaw(), b.bodyYaw(), 1e-6, id + " yaw at tick " + t);
	}

	/** Runs control and test side by side; {@code change} is applied to test at tick 15 (some NPCs mid-walk). */
	static Crowd[] runWithChange(int n, java.util.function.Function<Crowd, AnchorSource> change, List<String> expectReplan) {
		Crowd control = new Crowd(n);
		Crowd test = new Crowd(n);
		for (int t = 0; t < 15; t++) {
			control.tick();
			test.tick();
		}
		assertTrue(test.brains.values().stream().anyMatch(NpcBrain::walking), "some NPCs are mid-route when the change lands");
		Set<String> replanned = test.change(change.apply(test));
		assertEquals(Set.copyOf(expectReplan), replanned);
		return new Crowd[] {control, test};
	}

	@Test
	void aNewAgentAppearingLeavesEveryoneElseWhereTheyAre() {
		Crowd[] c = runWithChange(6, cr -> ((Desks) cr.anchors).with("vps:new", Anchor.at(40.5, 64, 2.5, 0)), List.of());
		Crowd control = c[0];
		Crowd test = c[1];
		// the newcomer gets its brain (as the director would on its first tick)
		test.brains.put("vps:new", new NpcBrain("vps:new", new Pos(30.5, 64, 9.5)));
		for (int t = 0; t < 400; t++) {
			control.tick();
			test.tick();
			for (String id : control.brains.keySet()) {
				assertSame(control, test, id, t);
			}
		}
		assertTrue(test.brains.get("vps:new").pos().horizDist(Anchor.at(40.5, 64, 2.5, 0).pos()) < 1e-6, "the newcomer reached its desk");
	}

	@Test
	void anAgentLeavingLeavesEveryoneElseWhereTheyAre() {
		Crowd[] c = runWithChange(6, cr -> ((Desks) cr.anchors).without("vps:t2"), List.of("vps:t2"));
		Crowd control = c[0];
		Crowd test = c[1];
		// the registry forgets the one that left (BehaviorDirector.forget)
		test.brains.remove("vps:t2");
		control.brains.remove("vps:t2");
		for (int t = 0; t < 400; t++) {
			control.tick();
			test.tick();
			for (String id : control.brains.keySet()) {
				assertSame(control, test, id, t);
			}
		}
	}

	@Test
	void aLayoutChangeReplansOnlyTheAffectedAndTheyWalkFromWhereTheyAre() {
		Anchor moved = Anchor.at(14.5, 64, 14.5, 90);
		Crowd[] c = runWithChange(6, cr -> ((Desks) cr.anchors).with("vps:t3", moved), List.of("vps:t3"));
		Crowd control = c[0];
		Crowd test = c[1];
		Pos last = test.brains.get("vps:t3").pos();
		for (int t = 0; t < 600; t++) {
			control.tick();
			test.tick();
			for (String id : control.brains.keySet()) {
				if (!id.equals("vps:t3")) {
					assertSame(control, test, id, t);
				}
			}
			NpcBrain b = test.brains.get("vps:t3");
			assertFalse(b.teleported(), "no teleport at tick " + t);
			assertTrue(b.pos().horizDist(last) <= MAX_STEP, "walks, no jump: " + last + " → " + b.pos() + " at tick " + t);
			last = b.pos();
		}
		NpcBrain b = test.brains.get("vps:t3");
		assertTrue(b.pos().horizDist(moved.pos()) < 1e-6, "reached the new desk: " + b.pos());
		assertEquals(NpcBrain.Phase.TYPING, b.phase());
	}

	@Test
	void anIdenticalSourceReplansNobody() {
		Crowd cr = new Crowd(4);
		for (int t = 0; t < 10; t++) {
			cr.tick();
		}
		Desks same = new Desks(((Desks) cr.anchors).desks());
		assertEquals(Set.of(), cr.change(same));
	}

	@Test
	void spotsMovedComparesDeskHomeAndRest() {
		Desks a = new Desks(Map.of("x", Anchor.at(1.5, 64, 1.5, 0)));
		assertFalse(BehaviorDirector.spotsMoved(a, new Desks(Map.of("x", Anchor.at(1.6, 64, 1.5, 0))), "x", GROUP), "jitter is not a move");
		assertTrue(BehaviorDirector.spotsMoved(a, new Desks(Map.of("x", Anchor.at(4.5, 64, 1.5, 0))), "x", GROUP));
		assertTrue(BehaviorDirector.spotsMoved(a, new Desks(Map.of()), "x", GROUP), "desk gone");
		assertTrue(BehaviorDirector.spotsMoved(AnchorSource.NONE, a, "x", GROUP), "desk appeared");
		assertFalse(BehaviorDirector.spotsMoved(a, a, "y", GROUP), "someone else's desk");
	}

	@Test
	void anNpcWaitingOutANoRouteTriesAgainOnAnyChange() {
		// desk walled off: no route, the NPC waits RETRY_TICKS
		NpcBrain.Navigator blocked = (from, to, avoid) -> null;
		NpcBrain b = new NpcBrain("vps:w", new Pos(0.5, 64, 0.5));
		Anchor desk = Anchor.at(10.5, 64, 0.5, 0);
		NpcBrain.Targets t = new NpcBrain.Targets(desk, null, desk, 3, desk);
		b.tick(Mode.IDLE, false, t, null, blocked);
		b.tick(Mode.WORKING, false, t, null, blocked);
		assertTrue(b.waitingForRoute());
		Map<String, NpcBrain> brains = new HashMap<>();
		brains.put("vps:w", b);
		Desks same = new Desks(Map.of("vps:w", desk));
		assertEquals(Set.of("vps:w"), BehaviorDirector.replanAffected(brains, Map.of("vps:w", GROUP), same, new Desks(Map.of("vps:w", desk))));
		for (int i = 0; i < 200; i++) {
			b.tick(Mode.WORKING, false, t, null, NAV);
		}
		assertTrue(b.pos().horizDist(desk.pos()) < 1e-6, "got there once the world changed: " + b.pos());
	}
}
