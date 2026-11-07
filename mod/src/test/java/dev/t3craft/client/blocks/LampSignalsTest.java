package dev.t3craft.client.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.t3craft.blocks.LampStatus;
import dev.t3craft.client.workstation.StationStatus;
import dev.t3craft.client.workstation.WorkstationThread;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Thread state → lamp colour/light, and the board bulbs. */
class LampSignalsTest {
	static WorkstationThread t(String key, StationStatus s) {
		return new WorkstationThread(key, "vps", key, "title " + key, "vps:api", 0, s, null, null);
	}

	@Test
	void stateToLamp() {
		assertEquals(LampStatus.WORKING, LampSignals.of(StationStatus.WORKING));
		assertEquals(LampStatus.ATTENTION, LampSignals.of(StationStatus.ATTENTION));
		assertEquals(LampStatus.ERROR, LampSignals.of(StationStatus.ERROR));
		assertEquals(LampStatus.DONE, LampSignals.of(StationStatus.DONE));
		assertEquals(LampStatus.IDLE, LampSignals.of(StationStatus.IDLE));
		assertEquals(LampStatus.OFF, LampSignals.of(StationStatus.ARCHIVED), "settled threads go dark");
	}

	@Test
	void lampColoursMatchTheScreens() {
		for (StationStatus s : StationStatus.values()) {
			if (s != StationStatus.ARCHIVED) {
				assertEquals(s.color, LampSignals.of(s).color, s.name());
			}
		}
	}

	@Test
	void wireStatesEndToEnd() {
		assertEquals(LampStatus.ATTENTION, LampSignals.of(StationStatus.of("needs_attention", false)));
		assertEquals(LampStatus.IDLE, LampSignals.of(StationStatus.of("something-new", false)));
		assertEquals(LampStatus.OFF, LampSignals.of(StationStatus.of("working", true)));
	}

	@Test
	void boardShowsTheMostUrgentFirst() {
		List<WorkstationThread> ts = List.of(t("a", StationStatus.ATTENTION), t("b", StationStatus.WORKING), t("c", StationStatus.ERROR),
				t("d", StationStatus.ATTENTION), t("e", StationStatus.DONE));
		assertEquals(List.of(LampStatus.ERROR, LampStatus.ATTENTION, LampStatus.ATTENTION, LampStatus.OFF), LampSignals.board(ts, 4));
		assertEquals(List.of(LampStatus.ERROR), LampSignals.board(ts, 1));
		assertEquals(List.of(LampStatus.OFF, LampStatus.OFF), LampSignals.board(List.of(t("x", StationStatus.WORKING)), 2));
	}

	@Test
	void wantedIsOrderIndependent() {
		List<WorkstationThread> ts = new ArrayList<>();
		StationStatus[] all = StationStatus.values();
		for (int i = 0; i < 20; i++) {
			ts.add(t("vps:t" + i, all[i % all.length]));
		}
		Map<String, LampStatus> ref = LampSignals.wanted(ts);
		for (long seed = 0; seed < 5; seed++) {
			List<WorkstationThread> s = new ArrayList<>(ts);
			Collections.shuffle(s, new Random(seed));
			assertEquals(ref, LampSignals.wanted(s));
		}
		assertEquals(LampStatus.WORKING, ref.get("thread:vps:t2"));
		assertEquals(LampStatus.ERROR, ref.get("board:0"));
		assertEquals(LampSignals.BOARD_BULBS + ts.size(), ref.size());
	}
}
