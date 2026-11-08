package dev.t3craft.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.Placement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Large screens reserved by the HQ plan, the project/env bindings, and the plan's monitors staying apart. */
class HqBigScreenTest {
	@BeforeAll
	static void boot() {
		BlocksTestSupport.boot();
	}

	/** Stand-in states that remember the sides each monitor cell was given. */
	static final class Recording implements HqBlockFixtures.States {
		final List<ScreenRect.Sides> sides = new java.util.ArrayList<>();

		@Override
		public BlockState monitor(Direction facing) {
			return Blocks.OBSERVER.defaultBlockState();
		}

		@Override
		public BlockState monitor(Direction facing, ScreenRect.Sides s) {
			sides.add(s);
			return Blocks.OBSERVER.defaultBlockState();
		}

		@Override
		public BlockState lamp() {
			return Blocks.REDSTONE_LAMP.defaultBlockState();
		}

		@Override
		public BlockState boardLamp() {
			return Blocks.SEA_LANTERN.defaultBlockState();
		}
	}

	private static BuildPlan plan() {
		return new HqGenerator().generate(HqLayout.plan(HqInput.fromRoster(BlocksTestSupport.roster())));
	}

	/** A w×h rectangle of filled, non-fixture cells facing {@code facing} (top-left), or null. */
	private static BlockPos findWall(BuildPlan plan, Direction facing, int w, int h) {
		Set<BlockPos> fixtures = new HashSet<>();
		for (HqBindings.Fixture f : HqBindings.of(plan)) {
			fixtures.add(f.rel());
		}
		for (Placement p : plan.placements()) {
			ScreenRect r = new ScreenRect(p.rel(), facing, w, h);
			boolean ok = true;
			for (BlockPos c : r.cells()) {
				Placement q = plan.at(c);
				if (q == null || q.state().isAir() || fixtures.contains(c)) {
					ok = false;
					break;
				}
			}
			if (ok) {
				return p.rel();
			}
		}
		return null;
	}

	@Test
	void bigScreenNamesRoundTrip() {
		String n = AnchorNames.bigScreen("module@2", 3, 2, "project:vps:api");
		AnchorNames.BigScreen bs = AnchorNames.parseBigScreen(n);
		assertEquals(new AnchorNames.BigScreen("module@2", 3, 2, "project:vps:api"), bs);
		assertEquals(new AnchorNames.BigScreen("hub", 4, 3, ""), AnchorNames.parseBigScreen(AnchorNames.bigScreen("hub", 4, 3, "")));
		assertNull(AnchorNames.parseBigScreen("bigscreen_hub@3=x"));
		assertNull(AnchorNames.parseBigScreen("monitor_vps:t0"));
		assertNull(AnchorNames.parseBigScreen("bigscreen_hub@0x2="));
	}

	@Test
	void projectAndEnvBindings() {
		Binding p = Binding.parse("project:vps:api");
		assertEquals(Binding.Kind.PROJECT, p.kind());
		assertEquals("vps:api", p.value());
		assertEquals("project:vps:api", p.wire());
		assertTrue(p.isFeed());
		assertEquals(Binding.project("vps:api"), p);
		Binding e = Binding.parse("env:acmesite");
		assertEquals(Binding.env("acmesite"), e);
		assertEquals("env:acmesite", e.wire());
		assertTrue(e.isFeed());
		assertFalse(Binding.thread("vps:t0").isFeed());
		assertTrue(Binding.parse("project:").isNone(), "empty key → unbound");
		assertEquals(p, Binding.resolve("", p), "positional fallback works for feeds too");
	}

	@Test
	void aReservedScreenBecomesOneMergedScreen() {
		BuildPlan base = plan();
		BlockPos topLeft = findWall(base, Direction.NORTH, 3, 2);
		assertNotNull(topLeft, "the generated HQ has some 3×2 wall to reserve");
		String name = AnchorNames.bigScreen("test", 3, 2, Binding.project("vps:api").wire());
		BuildPlan p = base.withAnchors(List.of(new Anchor(name, topLeft.getX(), topLeft.getY(), topLeft.getZ(), Direction.NORTH, topLeft)));

		// the plan already reserves its own (hub and one per module); the added one comes on top
		List<HqBindings.Screen> screens = HqBindings.screens(p);
		assertEquals(HqBindings.screens(base).size() + 1, screens.size());
		HqBindings.Screen added = screens.stream().filter(sc -> sc.extent().equals(new ScreenRect(topLeft, Direction.NORTH, 3, 2))).findFirst()
				.orElseThrow();
		assertEquals(Binding.project("vps:api"), added.binding());

		List<HqBindings.Fixture> cells = HqBindings.ofRole(p, HqBindings.Role.SCREEN);
		assertEquals(HqBindings.ofRole(base, HqBindings.Role.SCREEN).size() + 6, cells.size());
		for (HqBindings.Fixture f : cells) {
			if (added.extent().cells().contains(f.rel())) {
				assertEquals(Binding.project("vps:api"), f.binding(), "every cell carries the screen's binding");
				assertEquals(Direction.NORTH, f.facing());
			}
		}

		Recording rec = new Recording();
		BuildPlan modded = new HqBlockFixtures(rec).apply(p);
		Map<BlockPos, ScreenRect> walls = HqBlockFixtures.screens(fixtureMap(p));
		ScreenRect merged = walls.get(topLeft);
		assertEquals(new ScreenRect(topLeft, Direction.NORTH, 3, 2), merged, "the plan's cells merge by the world's rule");
		long connected = rec.sides.stream().filter(s -> !s.equals(ScreenRect.Sides.NONE)).count();
		assertTrue(connected >= 6, "the six screen cells get connection flags");
		for (BlockPos c : merged.cells()) {
			assertEquals(Binding.project("vps:api").wire(), modded.at(c).be().getStringOr(BoundBlockEntity.KEY, ""));
		}
	}

	@Test
	void eachDeskMergesIntoOne3x2ScreenAndThePlansScreensMerge() {
		BuildPlan p = plan();
		Map<BlockPos, HqBindings.Fixture> fixtures = fixtureMap(p);
		Map<BlockPos, ScreenRect> walls = HqBlockFixtures.screens(fixtures);
		assertFalse(walls.isEmpty());
		for (dev.t3craft.hq.Workstation w : p.workstations()) {
			ScreenRect want = HqBindings.deskScreen(w);
			for (BlockPos cell : w.screenCells()) {
				assertEquals(want, walls.get(cell), "desk " + w.threadId() + ": one 3x2 screen, never merged with a neighbour");
			}
		}
		for (HqBindings.Screen sc : HqBindings.screens(p)) {
			assertTrue(sc.extent().isMulti(), "the plan's big screens are multiblock: " + sc);
		}
	}

	@Test
	void fixturesAreDeterministic() {
		BuildPlan base = plan();
		BlockPos topLeft = findWall(base, Direction.NORTH, 2, 2);
		assertNotNull(topLeft);
		String name = AnchorNames.bigScreen("module@0", 2, 2, Binding.env("vps").wire());
		BuildPlan p = base.withAnchors(List.of(new Anchor(name, topLeft.getX(), topLeft.getY(), topLeft.getZ(), Direction.NORTH, topLeft)));
		BuildPlan a = new HqBlockFixtures(new Recording()).apply(p);
		BuildPlan b = new HqBlockFixtures(new Recording()).apply(p);
		assertEquals(a.fingerprint(), b.fingerprint());
	}

	private static Map<BlockPos, HqBindings.Fixture> fixtureMap(BuildPlan p) {
		Map<BlockPos, HqBindings.Fixture> m = new HashMap<>();
		for (HqBindings.Fixture f : HqBindings.of(p)) {
			m.put(f.rel(), f);
		}
		return m;
	}
}
