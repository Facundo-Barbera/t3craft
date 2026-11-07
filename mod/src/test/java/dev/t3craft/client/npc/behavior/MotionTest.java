package dev.t3craft.client.npc.behavior;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class MotionTest {
	@Test
	void walksToTheEndAndStops() {
		Motion m = new Motion();
		m.snapYaw(-90); // facing +x
		Pos p = new Pos(0, 64, 0);
		Pos end = new Pos(5, 64, 0);
		m.walk(List.of(p, end), 0f);
		int ticks = 0;
		while (m.walking() && ticks++ < 400) {
			p = m.step(p, null);
			assertTrue(m.speed() <= Motion.SPEED * 1.3 + 1e-9, "never faster than a walk");
		}
		assertFalse(m.walking());
		assertEquals(end, p);
		assertTrue(ticks > 5 / (Motion.SPEED * 1.3), "takes walking time, not a teleport");
	}

	@Test
	void holdsWhenThePlayerIsRightAhead() {
		Motion m = new Motion();
		m.snapYaw(-90);
		Pos p = new Pos(0, 64, 0);
		m.walk(List.of(p, new Pos(5, 64, 0)), 0f);
		Pos player = new Pos(1.0, 64, 0);
		for (int i = 0; i < 10; i++) {
			p = m.step(p, player);
		}
		assertEquals(new Pos(0, 64, 0), p);
		assertEquals(10, m.blockedTicks());
		// Player behind: keeps walking.
		p = m.step(p, new Pos(-1, 64, 0));
		assertTrue(p.x() > 0);
		assertEquals(0, m.blockedTicks());
	}

	@Test
	void wrapDegrees() {
		assertEquals(-170f, Motion.wrapDegrees(190f), 1e-4);
		assertEquals(170f, Motion.wrapDegrees(-190f), 1e-4);
		assertEquals(10f, Motion.approachAngle(0f, 90f, 10f), 1e-4);
		assertEquals(-175f, Motion.approachAngle(175f, -175f, 24f), 1e-4, "turns the short way");
	}
}
