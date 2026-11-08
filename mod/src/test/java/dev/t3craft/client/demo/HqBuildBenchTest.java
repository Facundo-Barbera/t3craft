package dev.t3craft.client.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.build.BuildBudget;
import dev.t3craft.build.BuildPlan;
import dev.t3craft.build.BuildTuning;
import dev.t3craft.hq.HqPlans;
import dev.t3craft.hq.HqRosters;
import dev.t3craft.hq.layout.HqLayoutInfo;
import dev.t3craft.hq.layout.HqLayouts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * fix/hq-build-parallel benchmark (headless, no Minecraft launched): the real HQ world plan of every
 * layout with 20 projects ({@link DemoHq#full}), built in {@link HqBuildSim} the way main did (one part
 * after another) and the way {@link DemoBuildRun} does now (parts in parallel). Writes the table to
 * {@code build/reports/hq-build-bench.md} ({@code ./gradlew hqBuildBench}); docs/feat-hq-build-parallel.md
 * explains the columns.
 */
class HqBuildBenchTest {
	/** Campus with 20 projects must finish in at least this many times fewer ticks (see the doc). */
	static final double CAMPUS_SPEEDUP = 5.0;

	record Row(String layout, int parts, int entries, HqBuildSim.Result before, HqBuildSim.Result after, int badBefore, int badAfter) {
	}

	private static final Map<String, Row> rows = new LinkedHashMap<>();

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@AfterAll
	static void reset() {
		HqLayouts.use(null);
	}

	static List<BuildPlan> parts(String layout, int projects) {
		HqLayouts.use(layout);
		try {
			return DemoHq.full(HqPlans.plan(HqRosters.projects(projects), null).plan(), 0);
		} finally {
			HqLayouts.use(null);
		}
	}

	static synchronized Row row(String layout) {
		return rows.computeIfAbsent(layout, id -> {
			List<BuildPlan> parts = parts(id, 20);
			DemoConfig c = DemoConfig.defaults();
			BuildTuning t = BuildTuning.DEFAULTS;
			var expected = HqBuildSim.expected(parts);
			HqBuildSim a = new HqBuildSim();
			HqBuildSim.Result before = a.run(parts, HqBuildSim.Policy.before(c, t), new BuildBudget(t), Long.MAX_VALUE);
			HqBuildSim b = new HqBuildSim();
			HqBuildSim.Result after = b.run(parts, HqBuildSim.Policy.after(c, t), new BuildBudget(t), Long.MAX_VALUE);
			int entries = parts.stream().mapToInt(BuildPlan::size).sum();
			return new Row(id, parts.size(), entries, before, after, a.mismatches(expected), b.mismatches(expected));
		});
	}

	@Test
	void everyLayoutBuildsTheSameWorldFasterWithinBudget() throws Exception {
		StringBuilder md = new StringBuilder();
		md.append("| layout | parts | entries | placed | ticks before | ticks after | time before | time after | speed-up | blocks/tick before (mean/max) | blocks/tick after (mean/max) | fronts before (mean/max) | fronts after (mean/max) | parts at once (max) |\n");
		md.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
		for (HqLayoutInfo i : HqLayouts.all()) {
			Row r = row(i.id());
			HqBuildSim.Result b = r.before();
			HqBuildSim.Result a = r.after();
			assertEquals(0, r.badBefore(), i.id() + " before: world differs from the plan");
			assertEquals(0, r.badAfter(), i.id() + " after: world differs from the plan");
			assertEquals(b.placed(), a.placed(), i.id() + ": same blocks placed");
			assertFalse(a.overBudget() || b.overBudget(), i.id() + ": a tick went over the budget");
			assertFalse(a.overlap(), i.id() + ": parts that touch ran at once");
			assertTrue(a.maxPerTick() <= BuildTuning.DEFAULTS.maxBlocksPerTick(), i.id() + ": blocks per tick");
			assertTrue(a.ticks() < b.ticks(), i.id() + ": faster");
			md.append(String.format(Locale.ROOT, "| %s | %d | %d | %d | %d | %d | %.0f s | %.0f s | %.1f× | %.1f / %d | %.1f / %d | %.1f / %d | %.1f / %d | %d |%n",
					i.id(), r.parts(), r.entries(), a.placed(), b.ticks(), a.ticks(), b.seconds(), a.seconds(), (double) b.ticks() / a.ticks(),
					b.meanPerTick(), b.maxPerTick(), a.meanPerTick(), a.maxPerTick(), b.meanFronts(), b.maxFronts(), a.meanFronts(), a.maxFronts(),
					a.maxParts()));
		}
		Path out = Path.of("build", "reports", "hq-build-bench.md");
		Files.createDirectories(out.getParent());
		Files.writeString(out, md.toString());
		System.out.println("HQ build benchmark (20 projects)\n" + md);
	}

	@Test
	void campusWithTwentyProjectsIsAtLeastTheTargetFaster() {
		Row r = row(HqLayouts.CAMPUS);
		double speedup = (double) r.before().ticks() / r.after().ticks();
		assertTrue(speedup >= CAMPUS_SPEEDUP, "campus speed-up " + speedup + " < " + CAMPUS_SPEEDUP);
	}

	@Test
	void leavingMidBuildAndComingBackFinishesTheSameWorld() {
		List<BuildPlan> parts = parts(HqLayouts.CAMPUS, 20);
		DemoConfig c = DemoConfig.defaults();
		BuildTuning t = BuildTuning.DEFAULTS;
		HqBuildSim sim = new HqBuildSim();
		HqBuildSim.Result first = sim.run(parts, HqBuildSim.Policy.after(c, t), new BuildBudget(t), 1200);
		assertEquals(1200, first.ticks(), "stopped mid-build");
		var expected = HqBuildSim.expected(parts);
		assertTrue(sim.mismatches(expected) > 0, "not finished yet");
		// Next visit: the marker was never written, so the run starts over on the same world.
		HqBuildSim.Result second = sim.run(parts, HqBuildSim.Policy.after(c, t), new BuildBudget(t), Long.MAX_VALUE);
		assertEquals(0, sim.mismatches(expected), "resumed world equals the plan");
		assertTrue(second.placed() < row(HqLayouts.CAMPUS).after().placed(), "what was already built is skipped");
		assertFalse(second.overBudget() || second.overlap());
	}
}
