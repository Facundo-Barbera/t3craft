package dev.t3craft.client.npc.behavior;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DepartureTest {
	@Test
	void wavesWalksFadesThenHides() {
		Pos start = new Pos(0, 64, 0);
		Departure d = new Departure(start, 0f, Anchor.at(6, 64, 0, -90f));
		assertEquals(Departure.Stage.WAVE, d.stage());
		assertTrue(d.visible());
		Pos last = d.pos();
		int ticks = 0;
		while (d.stage() != Departure.Stage.GONE && ticks++ < 400) {
			d.tick(NpcBrain.Navigator.STRAIGHT, null);
			assertTrue(last.distSqr(d.pos()) < 0.5 * 0.5, "walks, never teleports");
			last = d.pos();
			if (d.stage() == Departure.Stage.WAVE) {
				assertEquals(start, d.pos(), "waves on the spot");
			}
		}
		assertEquals(Departure.Stage.GONE, d.stage());
		assertFalse(d.visible());
		assertTrue(d.pos().horizDist(new Pos(6, 64, 0)) < 0.01, "reached the exit");
		assertFalse(d.expired());
	}

	@Test
	void farExitFadesAfterABriefWalk() {
		Departure d = new Departure(new Pos(0, 64, 0), 0f, Anchor.at(200, 64, 0, 0f));
		for (int i = 0; i < Departure.WAVE_TICKS + Departure.WALK_TICKS + Departure.FADE_TICKS + 5; i++) {
			d.tick(NpcBrain.Navigator.STRAIGHT, null);
		}
		assertEquals(Departure.Stage.GONE, d.stage());
		assertTrue(d.pos().x() > 5 && d.pos().x() < 30, "walked a bit, then vanished in place: " + d.pos());
	}

	@Test
	void expiresWhenTheSettleIsNeverReflected() {
		Departure d = new Departure(new Pos(0, 64, 0), 0f, Anchor.at(0, 64, 0, 0f));
		for (int i = 0; i < Departure.MAX_TICKS; i++) {
			d.tick(NpcBrain.Navigator.STRAIGHT, null);
		}
		assertTrue(d.expired());
	}
}
