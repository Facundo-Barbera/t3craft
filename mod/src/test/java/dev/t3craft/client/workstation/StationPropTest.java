package dev.t3craft.client.workstation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StationPropTest {
	@Test
	void realBlockMeansNoProp() {
		assertEquals(StationProp.NONE, StationProp.choose(true, true));
		assertEquals(StationProp.NONE, StationProp.choose(false, true));
		assertFalse(StationProp.NONE.drawn());
	}

	@Test
	void blocksModuleUsesMonitor() {
		assertEquals(StationProp.MONITOR, StationProp.choose(true, false));
		assertTrue(StationProp.MONITOR.drawn());
	}

	@Test
	void guiOnlyUsesDiscreetPanel() {
		assertEquals(StationProp.PANEL, StationProp.choose(false, false));
		assertTrue(StationProp.PANEL.drawn());
	}

	@Test
	void noKindIsATechnicalBlock() {
		for (StationProp p : StationProp.values()) {
			String n = p.name();
			assertFalse(n.contains("OBSERVER") || n.contains("REDSTONE") || n.contains("PISTON") || n.contains("DISPENSER"), n);
		}
		for (boolean blocks : new boolean[] {true, false}) {
			for (boolean real : new boolean[] {true, false}) {
				StationProp p = StationProp.choose(blocks, real);
				assertTrue(p == StationProp.NONE || p == StationProp.MONITOR || p == StationProp.PANEL);
			}
		}
	}
}
