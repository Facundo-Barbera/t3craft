package dev.t3craft.client.npc.behavior;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApproachTest {
	static final ApproachConfig CFG = new ApproachConfig(true, 16, 2, 10, 5); // 10 s cooldown, 5 s max
	static final Pos PLAYER = new Pos(0.5, 64, 0.5);

	static ApproachPlanner.Candidate att(String id, double x, int prio) {
		return new ApproachPlanner.Candidate(id, new Pos(x, 64, 0.5), true, prio);
	}

	static ApproachPlanner.Candidate calm(String id, double x) {
		return new ApproachPlanner.Candidate(id, new Pos(x, 64, 0.5), false, 0);
	}

	// ---- planner -----------------------------------------------------------------------------

	@Test
	void attentionNpcNearbyApproachesToAComfortableDistance() {
		ApproachPlanner p = new ApproachPlanner();
		Map<String, Anchor> m = p.update(0, CFG, PLAYER, List.of(att("a", 10.5, 0)));
		Anchor s = m.get("a");
		assertNotNull(s);
		assertEquals(ApproachPlanner.COMFORT, s.pos().horizDist(PLAYER), 1e-6);
		assertTrue(s.pos().x() > PLAYER.x(), "stands on the side it comes from");
		assertEquals(s.pos().yawTo(PLAYER), s.yaw(), 1e-3, "faces the player");
	}

	@Test
	void disabledOrNoPlayerOrCalmNpcDoesNotApproach() {
		ApproachPlanner p = new ApproachPlanner();
		assertTrue(p.update(0, ApproachConfig.OFF, PLAYER, List.of(att("a", 5, 0))).isEmpty());
		assertTrue(p.update(1, CFG, null, List.of(att("a", 5, 0))).isEmpty());
		assertTrue(p.update(2, CFG, PLAYER, List.of(calm("a", 5))).isEmpty());
	}

	@Test
	void farPlayerIsNotApproached() {
		ApproachPlanner p = new ApproachPlanner();
		assertTrue(p.update(0, CFG, PLAYER, List.of(att("a", 40, 0))).isEmpty(), "does not cross the map");
		assertFalse(p.update(1, CFG, PLAYER, List.of(att("a", 16, 0))).isEmpty(), "within maxDistance");
	}

	@Test
	void concurrencyIsLimitedMostUrgentFirstAndSpotsDoNotOverlap() {
		ApproachPlanner p = new ApproachPlanner();
		Map<String, Anchor> m = p.update(0, CFG, PLAYER, List.of(att("c", 6, 2), att("a", 6, 0), att("b", 6, 1)));
		assertEquals(2, m.size());
		assertTrue(m.containsKey("a") && m.containsKey("b"));
		assertTrue(m.get("a").pos().horizDist(m.get("b").pos()) > 1.0);
		// When one leaves ATTENTION, the next one may come.
		m = p.update(1, CFG, PLAYER, List.of(att("c", 6, 2), calm("a", 6), att("b", 6, 1)));
		assertEquals(2, m.size());
		assertTrue(m.containsKey("c") && m.containsKey("b"));
	}

	@Test
	void leavingAttentionEndsTheApproachAndStartsTheCooldown() {
		ApproachPlanner p = new ApproachPlanner();
		p.update(0, CFG, PLAYER, List.of(att("a", 6, 0)));
		assertTrue(p.approaching("a"));
		assertTrue(p.update(1, CFG, PLAYER, List.of(calm("a", 6))).isEmpty());
		assertFalse(p.approaching("a"));
		assertTrue(p.update(2, CFG, PLAYER, List.of(att("a", 6, 0))).isEmpty(), "cooling down");
		assertTrue(p.update(1 + CFG.cooldownTicks() - 1, CFG, PLAYER, List.of(att("a", 6, 0))).isEmpty());
		assertFalse(p.update(1 + CFG.cooldownTicks(), CFG, PLAYER, List.of(att("a", 6, 0))).isEmpty(), "cooldown over");
	}

	@Test
	void timeLimitSendsItBackForTheRestOfTheEpisode() {
		ApproachPlanner p = new ApproachPlanner();
		p.update(0, CFG, PLAYER, List.of(att("a", 6, 0)));
		assertFalse(p.update(CFG.maxTicks() - 1, CFG, PLAYER, List.of(att("a", 6, 0))).isEmpty());
		assertTrue(p.update(CFG.maxTicks(), CFG, PLAYER, List.of(att("a", 6, 0))).isEmpty(), "gives up");
		long later = CFG.maxTicks() + CFG.cooldownTicks() + 100;
		assertTrue(p.update(later, CFG, PLAYER, List.of(att("a", 6, 0))).isEmpty(), "not again while still in ATTENTION");
		p.update(later + 1, CFG, PLAYER, List.of(calm("a", 6)));
		assertFalse(p.update(later + 2, CFG, PLAYER, List.of(att("a", 6, 0))).isEmpty(), "new episode");
	}

	@Test
	void playerWalkingAwayEndsTheApproach() {
		ApproachPlanner p = new ApproachPlanner();
		p.update(0, CFG, PLAYER, List.of(att("a", 6, 0)));
		Pos away = PLAYER.add(60, 0, 0);
		assertTrue(p.update(1, CFG, away, List.of(att("a", 6, 0))).isEmpty());
	}

	@Test
	void spotFollowsThePlayerLoosely() {
		ApproachPlanner p = new ApproachPlanner();
		Anchor s0 = p.update(0, CFG, PLAYER, List.of(att("a", 6, 0))).get("a");
		Anchor s1 = p.update(1, CFG, PLAYER.add(1, 0, 0), List.of(att("a", 6, 0))).get("a");
		assertEquals(s0, s1, "small moves: same spot");
		Pos moved = PLAYER.add(0, 0, 5);
		Anchor s2 = p.update(2, CFG, moved, List.of(att("a", 6, 0))).get("a");
		assertEquals(ApproachPlanner.COMFORT, s2.pos().horizDist(moved), 1e-6);
	}

	// ---- config ------------------------------------------------------------------------------

	@Test
	void configParsesWithDefaultsAndRejectsBadValues() {
		assertEquals(ApproachConfig.DEFAULTS, ApproachConfig.parse(JsonParser.parseString("{}").getAsJsonObject()));
		ApproachConfig c = ApproachConfig.parse(JsonParser.parseString(
				"{\"npcApproach\":{\"enabled\":false,\"maxDistance\":8,\"maxConcurrent\":1,\"cooldownSeconds\":5,\"maxSeconds\":12}}").getAsJsonObject());
		assertEquals(new ApproachConfig(false, 8, 1, 5, 12), c);
		ApproachConfig bad = ApproachConfig.parse(JsonParser.parseString(
				"{\"npcApproach\":{\"enabled\":\"yes\",\"maxDistance\":-3,\"maxConcurrent\":999,\"cooldownSeconds\":\"x\",\"maxSeconds\":0}}").getAsJsonObject());
		assertEquals(ApproachConfig.DEFAULTS, bad);
	}

	// ---- brain -------------------------------------------------------------------------------

	static final NpcBrain.Navigator NAV = new GridPathfinder(Walkable.flat(64))::find;
	static final Anchor SLOT = Anchor.at(10.5, 64, 0.5, 90);

	static void tick(NpcBrain b, Mode m, int n, Pos player) {
		for (int i = 0; i < n; i++) {
			b.tick(m, false, NpcBrainTest.targets(m == Mode.ATTENTION ? SLOT : null), player, NAV);
		}
	}

	@Test
	void brainWalksToTheApproachSpotFacesAndWavesThenGoesBack() {
		Pos player = new Pos(0.5, 64, 8.5);
		NpcBrain b = new NpcBrain("vps:t1", SLOT.pos());
		tick(b, Mode.ATTENTION, 5, player);
		Anchor spot = ApproachPlanner.spot(player, b.pos(), 0);
		b.approach(spot);
		tick(b, Mode.ATTENTION, 1, player);
		assertEquals(NpcBrain.Phase.WALKING, b.phase(), "walks, no teleport");
		assertFalse(b.teleported());
		assertTrue(b.approaching());
		boolean waved = false;
		for (int i = 0; i < 300; i++) {
			tick(b, Mode.ATTENTION, 1, player);
			waved |= b.phase() == NpcBrain.Phase.WAITING && b.swing() == Hand.MAIN;
		}
		assertEquals(NpcBrain.Phase.WAITING, b.phase());
		assertTrue(b.pos().horizDist(spot.pos()) < 0.1);
		assertEquals(b.pos().yawTo(player), b.headYaw(), 2.0);
		assertTrue(waved);

		// Approach over: back to the board slot.
		b.approach(null);
		tick(b, Mode.ATTENTION, 300, player);
		assertTrue(b.pos().horizDist(SLOT.pos()) < 0.1);
		assertFalse(b.approaching());
	}

	@Test
	void brainLeavingAttentionGoesBackToItsDesk() {
		Pos player = new Pos(0.5, 64, 8.5);
		NpcBrain b = new NpcBrain("vps:t1", SLOT.pos());
		tick(b, Mode.ATTENTION, 5, player);
		b.approach(ApproachPlanner.spot(player, b.pos(), 0));
		tick(b, Mode.ATTENTION, 200, player);
		b.approach(null);
		tick(b, Mode.WORKING, 300, player);
		assertEquals(NpcBrain.Phase.TYPING, b.phase());
		assertTrue(b.pos().horizDist(NpcBrainTest.DESK.pos()) < 0.1);
	}

	@Test
	void noRouteToThePlayerStaysAndGestures() {
		Pos player = new Pos(0.5, 64, 8.5);
		NpcBrain.Navigator none = (from, to, avoid) -> from.distSqr(to) < 1e-6 ? List.of(from, to) : null;
		NpcBrain b = new NpcBrain("vps:t1", SLOT.pos());
		for (int i = 0; i < 5; i++) {
			b.tick(Mode.ATTENTION, false, NpcBrainTest.targets(SLOT), player, none);
		}
		b.approach(ApproachPlanner.spot(player, b.pos(), 0));
		boolean waved = false;
		for (int i = 0; i < 120; i++) {
			b.tick(Mode.ATTENTION, false, NpcBrainTest.targets(SLOT), player, none);
			waved |= b.swing() == Hand.MAIN;
			assertFalse(b.teleported());
		}
		assertTrue(b.pos().horizDist(SLOT.pos()) < 1e-6, "stays put");
		assertEquals(NpcBrain.Phase.WAITING, b.phase());
		assertTrue(waved);
	}
}
