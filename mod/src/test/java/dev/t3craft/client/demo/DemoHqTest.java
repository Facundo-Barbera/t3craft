package dev.t3craft.client.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.build.BuildPlan;
import dev.t3craft.build.Placement;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DemoHqTest {
	/** Default demo: origin y = -60 in a superflat world with minY = -64. */
	private static final int LIFT = DemoHq.lift(-60, -64, -5);

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static NpcInfo npc(String id, String env, String project, boolean settled) {
		return new NpcInfo(id, id, id, env, "", 0xFFFFFF, null, 0, settled, "idle", env + ":" + project, project, "", null);
	}

	private static List<NpcInfo> live(int projects, int threads) {
		List<NpcInfo> out = new ArrayList<>();
		for (int p = 0; p < projects; p++) {
			String env = p % 2 == 0 ? "vps" : "laptop";
			for (int t = 0; t < threads; t++) {
				out.add(npc(env + "-p" + p + "-t" + t, env, "p" + p, false));
			}
		}
		return out;
	}

	@Nested
	class Roster {
		@Test
		void noThreadsGivesTheFixedSample() {
			DemoHq.Roster r = DemoHq.chooseRoster(List.of());
			assertTrue(r.sample());
			assertFalse(r.input().projects().isEmpty());
			assertEquals(DemoHq.sample(), r.input());
		}

		@Test
		void liveThreadsOfEveryEnvironment() {
			DemoHq.Roster r = DemoHq.chooseRoster(live(2, 3));
			assertFalse(r.sample());
			assertEquals(2, r.input().projects().size());
			assertEquals(6, r.input().threadCount());
			assertNotNull(r.input().project("vps:p0"));
			assertEquals("laptop", r.input().project("laptop:p1").envKey());
		}

		@Test
		void settledThreadsAreArchivedNotSeated() {
			List<NpcInfo> l = new ArrayList<>(live(1, 2));
			l.add(npc("done", "vps", "p0", true));
			HqInput in = DemoHq.chooseRoster(l).input();
			assertEquals(2, in.threadCount());
			assertEquals(List.of("done"), in.project("vps:p0").settled());
		}
	}

	@Nested
	class HashAndIdempotency {
		@Test
		void liftForTheDefaultSuperflat() {
			assertEquals(2, LIFT);
			assertEquals(0, DemoHq.lift(-50, -64, -5));
		}

		@Test
		void hashIgnoresRosterOrderButNotContentOrLift() {
			List<NpcInfo> a = live(2, 3);
			List<NpcInfo> b = new ArrayList<>(a);
			Collections.shuffle(b, new Random(7));
			DemoHq.Step sa = DemoHq.step(DemoHq.fromNpcs(a), null, null, LIFT, true);
			DemoHq.Step sb = DemoHq.step(DemoHq.fromNpcs(b), null, null, LIFT, true);
			assertEquals(sa.hash(), sb.hash());
			assertNotEquals(sa.hash(), DemoHq.step(DemoHq.fromNpcs(live(2, 4)), null, null, LIFT, true).hash());
			assertNotEquals(sa.hash(), DemoHq.hash(sa.layout(), LIFT + 1));
		}

		@Test
		void sameLayoutBuildsNothing() {
			HqInput in = DemoHq.sample();
			DemoHq.Step first = DemoHq.step(in, null, null, LIFT, true);
			assertTrue(first.full());
			assertFalse(first.upToDate());
			DemoHq.Step again = DemoHq.step(in, first.layout(), first.hash(), LIFT, true);
			assertTrue(again.upToDate());
			assertEquals(first.hash(), again.hash());
		}

		@Test
		void notDiffableRebuildsEverything() {
			HqInput in = DemoHq.sample();
			DemoHq.Step first = DemoHq.step(in, null, null, LIFT, true);
			DemoHq.Step other = DemoHq.step(in, first.layout(), first.hash(), LIFT, false);
			assertTrue(other.full());
			assertEquals(first.parts().size(), other.parts().size());
		}

		@Test
		void markerKeepsHashAndStateAndSkipsTheSameRoster() {
			List<NpcInfo> roster = live(2, 2);
			DemoHqSource src = new DemoHqSource(() -> roster);
			DemoWorldSpec spec = DemoWorldSpec.defaults();
			src.prepare(DemoMarker.EMPTY, spec, -64);
			assertTrue(DemoMarker.EMPTY.needsBuild(src));
			assertFalse(src.plansSince(DemoMarker.EMPTY).isEmpty());

			DemoMarker built = DemoMarker.parse(new DemoMarker(1, null, 0).withBuilt(src).toJson());
			assertEquals(src.revision(), built.hash());
			assertNotNull(built.state());

			DemoHqSource next = new DemoHqSource(() -> roster);
			next.prepare(built, spec, -64);
			assertFalse(built.needsBuild(next));
			assertTrue(next.plansSince(built).isEmpty());

			List<NpcInfo> grown = new ArrayList<>(roster);
			grown.add(npc("new-one", "vps", "p0", false));
			DemoHqSource changed = new DemoHqSource(() -> grown);
			changed.prepare(built, spec, -64);
			assertTrue(built.needsBuild(changed));
			assertFalse(changed.plansSince(built).isEmpty());
		}

		@Test
		void oldMarkersWithoutHashStillParse() {
			DemoMarker m = DemoMarker.parse("{\"setup\":1,\"structure\":\"shed\",\"version\":1}");
			assertEquals("shed", m.structure());
			assertNull(m.hash());
			assertNull(m.state());
			assertFalse(m.needsBuild(new ShedStructureSource()));
		}
	}

	@Nested
	class Diff {
		private static void apply(Map<BlockPos, BlockState> world, List<BuildPlan> parts) {
			for (BuildPlan p : parts) {
				for (Placement pl : p.placements()) {
					world.put(pl.pos(), pl.state());
				}
			}
		}

		private static int size(List<BuildPlan> parts) {
			return parts.stream().mapToInt(BuildPlan::size).sum();
		}

		@Test
		void diffOnTopOfTheOldHqGivesTheNewHq() {
			DemoHq.Step before = DemoHq.step(DemoHq.fromNpcs(live(2, 3)), null, null, LIFT, true);
			List<NpcInfo> more = live(3, 3);
			more.add(npc("extra", "vps", "p0", false));
			DemoHq.Step after = DemoHq.step(DemoHq.fromNpcs(more), before.layout(), before.hash(), LIFT, true);
			assertFalse(after.full());
			assertFalse(after.upToDate());
			List<BuildPlan> full = DemoHq.full(after.plan(), LIFT);
			assertTrue(size(after.parts()) < size(full), "diff smaller than the whole HQ");

			Map<BlockPos, BlockState> world = new HashMap<>();
			apply(world, before.parts());
			apply(world, after.parts());
			for (BuildPlan p : full) {
				for (Placement pl : p.placements()) {
					assertEquals(pl.state(), world.get(pl.pos()), "cell " + pl.pos());
				}
			}
		}

		@Test
		void removedModuleIsVacated() {
			DemoHq.Step before = DemoHq.step(DemoHq.fromNpcs(live(3, 2)), null, null, LIFT, true);
			DemoHq.Step after = DemoHq.step(DemoHq.fromNpcs(live(2, 2)), before.layout(), before.hash(), LIFT, true);
			Map<BlockPos, BlockState> world = new HashMap<>();
			apply(world, before.parts());
			apply(world, after.parts());
			Map<BlockPos, BlockState> expected = new HashMap<>();
			apply(expected, DemoHq.full(after.plan(), LIFT));
			int air = 0;
			for (Map.Entry<BlockPos, BlockState> e : world.entrySet()) {
				if (!expected.containsKey(e.getKey()) && e.getKey().getY() >= LIFT) {
					assertTrue(e.getValue().isAir(), "left over at " + e.getKey());
					air++;
				}
			}
			assertTrue(air > 0);
		}

		@Test
		void bakedHqFollowsTheDemoConvention() {
			DemoHq.Step s = DemoHq.step(DemoHq.sample(), null, null, LIFT, true);
			int minZ = Integer.MAX_VALUE;
			int minY = Integer.MAX_VALUE;
			for (BuildPlan p : s.parts()) {
				minZ = Math.min(minZ, p.min().getZ());
				minY = Math.min(minY, p.min().getY());
			}
			assertEquals(0, minZ, "portico front on z = 0, growth to +z");
			assertEquals(-5 + LIFT, minY, "basement lifted above bedrock");
			assertEquals(Blocks.TUFF.defaultBlockState(), s.parts().getFirst().get(0).state(), "plinth first");
		}

		@Test
		void anchorsPutLiveThreadsAtTheirDesks() {
			List<NpcInfo> roster = live(1, 2);
			DemoHqSource src = new DemoHqSource(() -> roster);
			src.prepare(DemoMarker.EMPTY, DemoWorldSpec.defaults(), -64);
			var anchors = src.anchors(new BlockPos(0, -60, 8));
			assertNotNull(anchors);
			assertNotNull(anchors.desk(roster.getFirst().id(), roster.getFirst().groupId()));
			assertNull(anchors.desk("unknown", "vps:p0"));
		}

		@Test
		void failingRosterFallsBackToTheShed() {
			DemoHqSource src = new DemoHqSource(() -> {
				throw new IllegalStateException("no npcs");
			});
			DemoStructures s = new DemoStructures();
			s.register(new ShedStructureSource());
			s.register(src);
			assertEquals(DemoHqSource.ID, s.choose("auto").orElseThrow().id());
			src.prepare(DemoMarker.EMPTY, DemoWorldSpec.defaults(), -64);
			assertEquals(ShedStructureSource.ID, s.choose("auto").orElseThrow().id());
		}
	}
}
