package dev.t3craft.build;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuildBudgetTest {
	static BuildTuning tuning(int max, int jobs, int effects, boolean auto) {
		return new BuildTuning(max, jobs, 4, effects, auto, 40);
	}

	@Test
	void shareIsFairAndNeverAboveWhatEachWants() {
		assertArrayEquals(new double[] {10, 10, 10}, BuildBudget.share(30, new double[] {32, 32, 32}), 1e-9);
		assertArrayEquals(new double[] {2, 14, 14}, BuildBudget.share(30, new double[] {2, 32, 32}), 1e-9, "a small want leaves room");
		assertArrayEquals(new double[] {2, 8, 0}, BuildBudget.share(48, new double[] {2, 8, 0}), 1e-9, "enough for all");
		assertArrayEquals(new double[] {0, 0}, BuildBudget.share(0, new double[] {5, 5}), 1e-9);
	}

	@Test
	void concurrentJobLimitSkipsPausedJobs() {
		List<JobState> s = List.of(JobState.RUNNING, JobState.PAUSED, JobState.PENDING, JobState.RUNNING, JobState.PENDING);
		assertArrayEquals(new boolean[] {true, false, true, true, false}, BuildBudget.slots(s, 3));
		assertArrayEquals(new boolean[] {true, false, false, false, false}, BuildBudget.slots(s, 1));
	}

	@Test
	void globalBudgetIsSharedByEveryJobOfEveryRunner() {
		BuildBudget b = new BuildBudget(tuning(48, 3, 8, false));
		b.beginTick(1, 5);
		// Four HQ/site/demo jobs of 32 blocks/tick each, limit 3 concurrent.
		List<JobState> st = List.of(JobState.RUNNING, JobState.RUNNING, JobState.RUNNING, JobState.PENDING);
		double[] a = b.allocate("builders", st, new double[] {32, 32, 32, 32});
		assertArrayEquals(new double[] {16, 16, 16, 0}, a, 1e-9, "48 split three ways, the fourth waits");
		assertEquals(0, b.claim("site", 8), 1e-9, "the tick's budget is spent");

		// Next tick both runners want blocks: each is capped at half so neither starves.
		b.beginTick(2, 5);
		assertEquals(24, b.claim("builders", 96), 1e-9);
		assertEquals(8, b.claim("site", 8), 1e-9);
		b.beginTick(3, 5);
		assertEquals(24, b.claim("builders", 96), 1e-9);
		assertEquals(24, b.claim("site", 32), 1e-9);
	}

	@Test
	void beginTickIsIdempotentAndEffectsAreCapped() {
		BuildBudget b = new BuildBudget(tuning(10, 3, 2, false));
		b.beginTick(5, 5);
		assertEquals(6, b.claim("a", 6), 1e-9);
		b.beginTick(5, 5); // second runner, same tick: no refill
		assertEquals(4, b.claim("a", 6), 1e-9);
		assertTrue(b.effect());
		assertTrue(b.effect());
		assertFalse(b.effect(), "two sound/particle bursts per tick");
		b.beginTick(6, 5);
		assertTrue(b.effect());
	}

	@Test
	void slowServerLowersTheBudgetAndItRecovers() {
		BuildBudget b = new BuildBudget(tuning(48, 3, 8, true));
		long t = 0;
		for (int i = 0; i < TickGovernor.INTERVAL_TICKS; i++) {
			b.beginTick(++t, 60);
		}
		assertEquals(33, b.effectiveMax(), "60 ms > 40 ms: ×0.7");
		for (int i = 0; i < 10 * TickGovernor.INTERVAL_TICKS; i++) {
			b.beginTick(++t, 60);
		}
		assertTrue(b.effectiveMax() >= 1 && b.effectiveMax() <= 3, "keeps dropping, never to zero: " + b.effectiveMax());
		for (int i = 0; i < 20 * TickGovernor.INTERVAL_TICKS; i++) {
			b.beginTick(++t, 10);
		}
		assertEquals(48, b.effectiveMax(), "fast again: back to full");
	}

	@Test
	void governorHoldsInTheDeadBandAndCanBeDisabled() {
		TickGovernor g = new TickGovernor();
		for (int i = 0; i < TickGovernor.INTERVAL_TICKS; i++) {
			g.observe(50, 40, true);
		}
		double f = g.factor();
		assertEquals(0.7, f, 1e-9);
		for (int i = 0; i < 5 * TickGovernor.INTERVAL_TICKS; i++) {
			g.observe(35, 40, true); // between 30 and 40: hold
		}
		assertEquals(f, g.factor(), 1e-9);
		assertEquals(1.0, g.observe(500, 40, false), 1e-9);
		assertEquals(1, TickGovernor.apply(48, 0.001));
	}

	@Test
	void configIsAdditiveAndValidated() {
		assertEquals(BuildTuning.DEFAULTS, BuildTuning.parse(null).tuning());
		assertEquals(BuildTuning.DEFAULTS, BuildTuning.parse(JsonParser.parseString("{\"mode\":\"direct\"}").getAsJsonObject()).tuning());
		BuildTuning.Result ok = BuildTuning.parse(JsonParser.parseString("""
				{"build": {"maxBlocksPerTick": 16, "maxConcurrentJobs": 1, "frontsPerJob": 2,
				           "maxEffectsPerTick": 0, "autoThrottle": false, "targetMspt": 45}}""").getAsJsonObject());
		assertEquals(new BuildTuning(16, 1, 2, 0, false, 45), ok.tuning());
		assertEquals(List.of(), ok.warnings());
		BuildTuning.Result bad = BuildTuning.parse(JsonParser.parseString("""
				{"build": {"maxBlocksPerTick": 0, "frontsPerJob": 2.5, "autoThrottle": "yes", "maxConcurrentJobs": 4}}""").getAsJsonObject());
		assertEquals(new BuildTuning(48, 4, 4, 8, true, 40), bad.tuning());
		assertEquals(List.of("build.maxBlocksPerTick", "build.frontsPerJob", "build.autoThrottle"), bad.warnings());
		assertEquals(3, BuildTuning.DEFAULTS.frontsFor(3), "one front per builder");
		assertEquals(4, BuildTuning.DEFAULTS.frontsFor(8));
		assertEquals(1, BuildTuning.DEFAULTS.frontsFor(0));
	}
}
