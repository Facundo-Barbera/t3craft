package dev.t3craft.client.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.build.BuildPlan;
import dev.t3craft.client.demo.DemoDecision.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DemoLogicTest {

	/** A source without real plans (BuildPlan needs a bootstrapped registry; selection does not). */
	record Fake(String id, int version, int priority, boolean available) implements DemoStructureSource {
		@Override
		public List<BuildPlan> plans() {
			return List.of();
		}
	}

	@Nested
	class CreateOrOpen {
		@ParameterizedTest(name = "inWorld={0} inDemo={1} exists={2} confirmed={3} -> {4}")
		@CsvSource({
				// title screen / world list
				"false, false, false, false, CREATE",
				"false, false, true,  false, OPEN",
				"false, false, true,  true,  OPEN",
				// already inside the demo world: never reload it
				"true,  true,  true,  false, ALREADY_HERE",
				"true,  true,  true,  true,  ALREADY_HERE",
				// in another world: ask first
				"true,  false, true,  false, CONFIRM_LEAVE",
				"true,  false, false, false, CONFIRM_LEAVE",
				// confirmed: save+leave, then open or create
				"true,  false, true,  true,  OPEN",
				"true,  false, false, true,  CREATE",
		})
		void decide(boolean inWorld, boolean inDemo, boolean exists, boolean confirmed, Action expected) {
			assertEquals(expected, DemoDecision.decide(inWorld, inDemo, exists, confirmed));
		}

		@Test
		void existingSaveIsNeverRecreated() {
			for (boolean inWorld : new boolean[] {false, true}) {
				for (boolean confirmed : new boolean[] {false, true}) {
					assertTrue(DemoDecision.decide(inWorld, false, true, confirmed) != Action.CREATE);
				}
			}
		}
	}

	@Nested
	class Idempotence {
		final DemoStructureSource shedV1 = new Fake("shed", 1, 0, true);
		final DemoStructureSource shedV2 = new Fake("shed", 2, 0, true);
		final DemoStructureSource hqV1 = new Fake("hq", 1, 100, true);

		@Test
		void freshWorldNeedsSetupAndBuild() {
			assertTrue(DemoMarker.EMPTY.needsSetup());
			assertTrue(DemoMarker.EMPTY.needsBuild(shedV1));
		}

		@Test
		void sameSourceAndVersionIsNotRebuilt() {
			DemoMarker m = DemoMarker.EMPTY.withSetup().withBuilt(shedV1);
			assertFalse(m.needsBuild(shedV1));
			assertFalse(m.needsSetup());
		}

		@Test
		void newerPlanVersionRebuilds() {
			DemoMarker m = DemoMarker.EMPTY.withBuilt(shedV1);
			assertTrue(m.needsBuild(shedV2));
		}

		@Test
		void differentSourceRebuilds() {
			// shed built while hq-design was not merged; once the HQ is registered it gets built.
			assertTrue(DemoMarker.EMPTY.withBuilt(shedV1).needsBuild(hqV1));
		}

		@Test
		void jsonRoundTrip() {
			DemoMarker m = DemoMarker.EMPTY.withSetup().withBuilt(hqV1);
			DemoMarker back = DemoMarker.parse(m.toJson());
			assertEquals(m, back);
			assertFalse(back.needsBuild(hqV1));
		}

		@Test
		void setupKeepsBuildAndBuildKeepsSetup() {
			DemoMarker m = DemoMarker.EMPTY.withBuilt(shedV1).withSetup();
			assertEquals("shed", m.structure());
			assertEquals(DemoMarker.SETUP_VERSION, m.setup());
		}

		@Test
		void unreadableMarkerMeansNothingDone() {
			for (String bad : new String[] {null, "", "   ", "not json", "[1,2]", "{\"version\":\"x\"}", "{\"structure\":{}}"}) {
				DemoMarker m = DemoMarker.parse(bad);
				assertNull(m.structure(), bad);
				assertTrue(m.needsBuild(shedV1), bad);
			}
		}

		@Test
		void unknownKeysIgnored() {
			DemoMarker m = DemoMarker.parse("{\"setup\":1,\"structure\":\"shed\",\"version\":1,\"future\":true}");
			assertFalse(m.needsBuild(shedV1));
		}
	}

	@Nested
	class SourceChoice {
		final Fake shed = new Fake("shed", 1, 0, true);
		final Fake hq = new Fake("hq", 3, 100, true);
		final Fake hqMissing = new Fake("hq", 3, 100, false);

		@Test
		void autoPicksHighestPriority() {
			assertEquals(Optional.of(hq), DemoStructures.choose(List.of(shed, hq), "auto"));
			assertEquals(Optional.of(hq), DemoStructures.choose(List.of(hq, shed), "AUTO"));
		}

		@Test
		void unavailableHqFallsBackToShed() {
			assertEquals(Optional.of(shed), DemoStructures.choose(List.of(shed, hqMissing), "auto"));
			assertEquals(Optional.of(shed), DemoStructures.choose(List.of(shed, hqMissing), "hq"));
		}

		@Test
		void explicitChoiceWinsOverPriority() {
			assertEquals(Optional.of(shed), DemoStructures.choose(List.of(shed, hq), "shed"));
		}

		@Test
		void unknownOrBlankChoiceActsAsAuto() {
			assertEquals(Optional.of(hq), DemoStructures.choose(List.of(shed, hq), "castle"));
			assertEquals(Optional.of(hq), DemoStructures.choose(List.of(shed, hq), ""));
			assertEquals(Optional.of(hq), DemoStructures.choose(List.of(shed, hq), null));
		}

		@Test
		void tieKeepsFirstRegistered() {
			Fake a = new Fake("a", 1, 5, true);
			Fake b = new Fake("b", 1, 5, true);
			assertEquals(Optional.of(a), DemoStructures.choose(List.of(a, b), "auto"));
		}

		@Test
		void noneAvailable() {
			assertEquals(Optional.empty(), DemoStructures.choose(List.of(hqMissing), "auto"));
			assertEquals(Optional.empty(), DemoStructures.choose(List.of(), "auto"));
		}

		@Test
		void throwingAvailabilityCountsAsUnavailable() {
			DemoStructureSource broken = new DemoStructureSource() {
				@Override public String id() { return "broken"; }
				@Override public int version() { return 1; }
				@Override public int priority() { return 1000; }
				@Override public boolean available() { throw new IllegalStateException("boom"); }
				@Override public List<BuildPlan> plans() { return List.of(); }
			};
			assertEquals(Optional.of(shed), DemoStructures.choose(List.of(shed, broken), "auto"));
		}

		@Test
		void registerReplacesSameId() {
			DemoStructures s = new DemoStructures();
			s.register(shed);
			s.register(new Fake("shed", 2, 0, true));
			assertEquals(1, s.all().size());
			assertEquals(2, s.all().getFirst().version());
		}

		@Test
		void defaultRegistryChoosesShedUntilHqIsRegistered() {
			DemoStructures s = new DemoStructures();
			s.register(new ShedStructureSource());
			assertEquals(ShedStructureSource.ID, s.choose("auto").orElseThrow().id());
			s.register(new Fake(HqStructureSource.ID, 1, HqStructureSource.PRIORITY, true));
			assertEquals(HqStructureSource.ID, s.choose("auto").orElseThrow().id());
		}

		@Test
		void hqAdapterUnavailableWhenGeneratorEmptyOrFailing() {
			assertFalse(new HqStructureSource(1, List::of).available());
			assertFalse(new HqStructureSource(1, () -> {
				throw new IllegalStateException("not ready");
			}).available());
			assertFalse(new HqStructureSource(1, () -> null).available());
		}

		@Test
		void hqAdapterCallsGeneratorOnce() {
			AtomicInteger calls = new AtomicInteger();
			HqStructureSource src = new HqStructureSource(7, () -> {
				calls.incrementAndGet();
				return new ArrayList<>();
			});
			src.available();
			src.plans();
			src.plans();
			assertEquals(1, calls.get());
			assertEquals(7, src.version());
			assertTrue(src.priority() > ShedStructureSource.PRIORITY);
		}
	}

	@Nested
	class Preset {
		@Test
		void defaultsAreTheDemo() {
			DemoWorldSpec s = DemoWorldSpec.defaults();
			assertEquals("t3craft-hq", s.folderId());
			assertEquals("T3Craft HQ", s.displayName());
			assertTrue(DemoWorldSpec.validFolderId(s.folderId()));
			assertTrue(DemoWorldSpec.CREATIVE && DemoWorldSpec.CHEATS && DemoWorldSpec.SUPERFLAT && DemoWorldSpec.PEACEFUL);
			assertFalse(DemoWorldSpec.STRUCTURES);
			assertEquals(DemoWorldSpec.NOON, s.dayTime());
			// Default superflat: bedrock -64, dirt -63..-62, grass -61 -> stand on -60.
			assertEquals(-60, s.originY());
		}

		@Test
		void seedIsFixed() {
			assertEquals(DemoWorldSpec.defaults().seed(), DemoWorldSpec.defaults().seed());
			assertEquals(DemoWorldSpec.DEFAULT_SEED, DemoConfig.fromRoot(null).spec().seed());
		}

		@Test
		void everyRuleOffFixedDayNoMobs() {
			var rules = DemoWorldSpec.defaults().rules();
			assertEquals(DemoWorldSpec.Rule.values().length, rules.size());
			rules.forEach((r, v) -> assertFalse(v, r.name()));
			assertTrue(rules.containsKey(DemoWorldSpec.Rule.ADVANCE_TIME));
			assertTrue(rules.containsKey(DemoWorldSpec.Rule.SPAWN_MOBS));
			assertTrue(rules.containsKey(DemoWorldSpec.Rule.SPAWN_MONSTERS));
		}

		@Test
		void viewpointInFrontOfTheSite() {
			DemoWorldSpec s = DemoWorldSpec.defaults().withOrigin(100, 64, 50);
			int[] v = DemoPlacement.viewpoint(s);
			assertEquals(100, v[0]);
			assertEquals(64, v[1]);
			assertEquals(50 - DemoPlacement.VIEW_DISTANCE, v[2]);
		}

		@ParameterizedTest
		@CsvSource({"t3craft-hq, true", "My_World2, true", "'', false", "'  ', false", "../x, false", "a/b, false", "with space, false", "..., false"})
		void folderIds(String id, boolean ok) {
			assertEquals(ok, DemoWorldSpec.validFolderId(id));
		}
	}

	@Nested
	class Config {
		private DemoConfig parse(String json) {
			return DemoConfig.fromRoot(JsonParser.parseString(json).getAsJsonObject());
		}

		@Test
		void missingSectionMeansDefaults() {
			DemoConfig c = parse("{\"mode\":\"direct\"}");
			assertEquals(DemoConfig.defaults(), c);
			assertEquals(DemoConfig.defaults(), DemoConfig.fromRoot(new JsonObject()));
		}

		@Test
		void readsEveryKey() {
			DemoConfig c = parse("""
					{"demo": {"worldFolder": "hq-test", "worldName": "Test HQ", "origin": [10, -60, 20],
					          "structure": "shed", "blocksPerTick": 8, "crew": 2, "titleButton": false}}""");
			assertEquals("hq-test", c.spec().folderId());
			assertEquals("Test HQ", c.spec().displayName());
			assertEquals(10, c.spec().originX());
			assertEquals(20, c.spec().originZ());
			assertEquals("shed", c.structure());
			assertEquals(8.0, c.blocksPerTick());
			assertEquals(2, c.crew());
			assertFalse(c.titleButton());
			assertTrue(c.warnings().isEmpty());
		}

		@Test
		void invalidValuesFallBackWithKeyOnlyWarnings() {
			DemoConfig c = parse("""
					{"demo": {"worldFolder": "../../etc", "origin": [1, 2], "blocksPerTick": 1000, "crew": "many",
					          "titleButton": "yes", "structure": 5}}""");
			assertEquals(DemoWorldSpec.DEFAULT_FOLDER, c.spec().folderId());
			assertEquals(DemoWorldSpec.DEFAULT_ORIGIN_Z, c.spec().originZ());
			assertEquals(DemoConfig.DEFAULT_BLOCKS_PER_TICK, c.blocksPerTick());
			assertEquals(DemoConfig.DEFAULT_CREW, c.crew());
			assertTrue(c.titleButton());
			assertEquals(DemoStructures.AUTO, c.structure());
			assertEquals(List.of("demo.worldFolder", "demo.origin", "demo.structure", "demo.blocksPerTick", "demo.crew", "demo.titleButton"),
					c.warnings());
			// Never the value itself.
			assertFalse(c.warnings().toString().contains("etc"));
		}

		@Test
		void originMustBeWholeAndInBuildHeight() {
			assertEquals(DemoWorldSpec.DEFAULT_ORIGIN_Y, parse("{\"demo\":{\"origin\":[0, 1.5, 0]}}").spec().originY());
			assertEquals(DemoWorldSpec.DEFAULT_ORIGIN_Y, parse("{\"demo\":{\"origin\":[0, 999, 0]}}").spec().originY());
			assertEquals(70, parse("{\"demo\":{\"origin\":[0, 70, 0]}}").spec().originY());
		}
	}
}
