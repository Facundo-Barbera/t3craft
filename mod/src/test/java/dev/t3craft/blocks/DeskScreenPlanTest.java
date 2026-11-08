package dev.t3craft.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqSite;
import dev.t3craft.hq.HqStyle;
import dev.t3craft.hq.HqStyles;
import dev.t3craft.hq.Placement;
import dev.t3craft.hq.Workstation;
import dev.t3craft.site.mc.LevelBlockWorld;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * fix/desk-screens-3x2, with the real module registered: every desk of the cherry and classic HQ
 * gets one 3×2 screen of {@code t3craft:monitor}s (merged, origin and binding right), no status lamp,
 * the agent's standing cell stays clear and neighbours never touch.
 */
class DeskScreenPlanTest {
	@BeforeAll
	static void register() throws Exception {
		BlocksTestSupport.registerModule();
	}

	@AfterAll
	static void reset() {
		T3Blocks.resetForTests();
		HqFixtures.use(null);
	}

	static Stream<HqStyle> styles() {
		return Stream.of(HqStyles.byId("cherry"), HqStyles.classic());
	}

	private static HqGenerator.Result vanilla(HqStyle style, List<HqInput.Entry> roster, HqLayout previous) {
		return HqGenerator.plan(HqInput.fromRoster(roster), previous, style);
	}

	private static BuildPlan modded(BuildPlan plan) {
		HqFixtures f = HqFixtures.active();
		assertTrue(f instanceof HqBlockFixtures, "module installed its fixtures: " + f);
		return f.apply(plan);
	}

	private static Block block(String id) {
		return BlocksTestSupport.registered(id);
	}

	@ParameterizedTest
	@MethodSource("styles")
	void everyDeskHasOne3x2MonitorScreenAndNoLamp(HqStyle style) {
		assertNotNull(style);
		BuildPlan p = modded(vanilla(style, BlocksTestSupport.roster(), null).plan());
		assertEquals(7, p.workstations().size(), "7 active threads");
		Set<BlockPos> screens = new HashSet<>();
		for (Workstation w : p.workstations()) {
			List<BlockPos> cells = w.screenCells();
			assertEquals(6, cells.size());
			for (BlockPos c : cells) {
				Placement at = p.at(c);
				assertNotNull(at, "screen cell placed " + c);
				assertSame(block("monitor"), at.state().getBlock(), style.id() + ": monitor at " + c);
				assertEquals(w.screenFacing(), at.state().getValue(MonitorBlock.FACING), "faces the agent");
				assertTrue(screens.add(c), "screen cells never shared");
			}
			assertTrue(cells.contains(w.computerPos()), "the clicked/anchor cell is part of the screen");
			// no lamp anywhere on the desk: its box spans the desk row and the standing row, y 1..4
			Direction right = w.screenRight();
			for (int col = -1; col <= Workstation.SCREEN_WIDTH; col++) {
				for (int y = 0; y <= 3; y++) {
					for (int depth = 0; depth <= 1; depth++) {
						BlockPos q = w.screenBottomLeft().relative(right, col).relative(w.screenFacing(), depth).below(1).above(y);
						Placement at = p.at(q);
						if (at != null) {
							Block b = at.state().getBlock();
							assertFalse(b == block("status_lamp") || b == block("board_lamp"), style.id() + ": lamp on a desk at " + q);
						}
					}
				}
			}
			assertNull(p.anchor(AnchorNames.lamp(w.threadId())), "no desk lamp anchor");
		}
	}

	@ParameterizedTest
	@MethodSource("styles")
	void theSixMonitorsMergeIntoOneScreenWithTheRightOrigin(HqStyle style) {
		BuildPlan p = modded(vanilla(style, BlocksTestSupport.roster(), null).plan());
		Map<BlockPos, ScreenRect> merged = MonitorWall.resolve(lookup(p), p.workstations().stream().map(Workstation::computerPos).toList());
		for (Workstation w : p.workstations()) {
			ScreenRect r = merged.get(w.computerPos());
			assertNotNull(r);
			assertEquals(new ScreenRect(w.screenOrigin(), w.screenFacing(), 3, 2), r, style.id() + ": one 3x2 screen");
			assertEquals(w.screenBottomLeft(), r.bottomLeft(), "draws from the bottom-left cell");
			for (BlockPos c : w.screenCells()) {
				BlockState s = p.at(c).state();
				assertEquals(r.isDrawOrigin(c), MonitorBlock.isDrawOrigin(s), "only the bottom-left cell draws: " + c + " " + s);
				assertTrue(MonitorBlock.isConnected(s), "baked connection flags: " + s);
			}
		}
	}

	@ParameterizedTest
	@MethodSource("styles")
	void theScreenIsBoundToItsDeskThreadAndFollowsMovesAndSettles(HqStyle style) {
		List<HqInput.Entry> roster = BlocksTestSupport.roster();
		HqGenerator.Result r1 = vanilla(style, roster, null);
		BuildPlan p1 = modded(r1.plan());
		for (Workstation w : p1.workstations()) {
			String want = Binding.thread(w.threadId()).wire();
			for (BlockPos c : w.screenCells()) {
				assertEquals(want, p1.at(c).be().getStringOr(BoundBlockEntity.KEY, ""), "every cell carries the thread");
			}
			ScreenRect rect = HqBindings.deskScreen(w);
			assertEquals(want, binding(p1, rect.bottomLeft()), "the screen's origin carries the thread");
		}
		// settle one thread: its screen goes, the bindings of its six cells are released, nobody else moves
		String gone = "vps:t1";
		List<HqInput.Entry> settled = new ArrayList<>();
		for (HqInput.Entry e : roster) {
			settled.add(e.threadId().equals(gone) ? new HqInput.Entry(e.env(), e.envLabel(), e.project(), e.projectLabel(), gone, true) : e);
		}
		HqGenerator.Result r2 = vanilla(style, settled, r1.layout());
		BuildPlan p2 = modded(r2.plan());
		HqSite site = new HqSite(BlockPos.ZERO, net.minecraft.world.level.block.Rotation.NONE);
		Map<Long, Binding> ch = HqBindings.changes(HqBindings.world(p1, site), HqBindings.world(p2, site));
		Workstation desk = p1.workstation(gone);
		Set<Long> cells = new HashSet<>();
		for (BlockPos c : desk.screenCells()) {
			cells.add(site.cell(c).asLong());
			assertEquals(Binding.NONE, ch.get(site.cell(c).asLong()), "freed screen cell unbound");
		}
		for (Map.Entry<Long, Binding> e : ch.entrySet()) {
			if (!cells.contains(e.getKey())) {
				assertFalse(e.getValue().kind() == Binding.Kind.THREAD, "no other desk rebinds: " + e);
			}
		}
		// a newcomer takes the free desk: the same six cells bind to it
		List<HqInput.Entry> grown = new ArrayList<>(settled);
		grown.add(new HqInput.Entry("vps", "VPS", "api", "API", "vps:new", false));
		BuildPlan p3 = modded(vanilla(style, grown, r2.layout()).plan());
		Workstation taken = p3.workstation("vps:new");
		assertNotNull(taken);
		assertEquals(desk.screenCells(), taken.screenCells(), "same desk, same screen cells");
		Map<Long, Binding> ch3 = HqBindings.changes(HqBindings.world(p2, site), HqBindings.world(p3, site));
		for (BlockPos c : taken.screenCells()) {
			assertEquals(Binding.thread("vps:new"), ch3.get(site.cell(c).asLong()));
		}
	}

	@ParameterizedTest
	@MethodSource("styles")
	void theAgentStandsClearAndNeighboursNeverTouch(HqStyle style) {
		BuildPlan p = modded(vanilla(style, BlocksTestSupport.roster(), null).plan());
		Map<BlockPos, Workstation> owner = new HashMap<>();
		for (Workstation w : p.workstations()) {
			BlockPos stand = w.deskAnchor().block();
			assertTrue(p.at(stand).state().isAir(), "feet free");
			assertTrue(p.at(stand.above()).state().isAir(), "head free");
			assertFalse(p.at(stand.below()).state().isAir(), "floor under the agent");
			assertEquals(stand.relative(w.deskAnchor().facing()).above(), w.computerPos(), "the screen's middle, right in front at eye level");
			for (BlockPos c : w.screenCells()) {
				assertFalse(c.equals(stand) || c.equals(stand.above()), "screen clear of the agent");
				owner.put(c, w);
			}
			// desk top under the screen, wall right behind it
			for (int col = 0; col < Workstation.SCREEN_WIDTH; col++) {
				BlockPos under = w.screenBottomLeft().relative(w.screenRight(), col).below();
				assertFalse(p.at(under).state().isAir(), style.id() + ": desk under the screen at " + under);
			}
		}
		for (Map.Entry<BlockPos, Workstation> e : owner.entrySet()) {
			for (Direction d : MonitorWall.planeDirections(e.getValue().screenFacing())) {
				Workstation n = owner.get(e.getKey().relative(d));
				assertTrue(n == null || n == e.getValue(), style.id() + ": screens of two desks touch at " + e.getKey());
			}
		}
	}

	@ParameterizedTest
	@MethodSource("styles")
	void screenStatesRoundTripThroughTheRealParser(HqStyle style) {
		BuildPlan p = modded(vanilla(style, BlocksTestSupport.roster(), null).plan());
		Set<BlockState> seen = new HashSet<>();
		for (Workstation w : p.workstations()) {
			for (BlockPos c : w.screenCells()) {
				seen.add(p.at(c).state());
			}
		}
		assertTrue(seen.size() >= 6, "a state per screen position (connection flags): " + seen.size());
		for (BlockState s : seen) {
			String key = LevelBlockWorld.keyOf(s);
			assertTrue(key.startsWith("t3craft:monitor"), key);
			assertSame(s, LevelBlockWorld.parse(BuiltInRegistries.BLOCK, key), key);
		}
	}

	private static String binding(BuildPlan p, BlockPos c) {
		return p.at(c).be().getStringOr(BoundBlockEntity.KEY, "");
	}

	private static MonitorWall.Lookup lookup(BuildPlan p) {
		return pos -> {
			Placement at = p.at(pos);
			if (at == null || !(at.state().getBlock() instanceof MonitorBlock)) {
				return null;
			}
			return new MonitorWall.Cell(at.state().getValue(MonitorBlock.FACING), at.be() == null ? "" : at.be().getStringOr(BoundBlockEntity.KEY, ""));
		};
	}
}
