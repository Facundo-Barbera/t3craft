package dev.t3craft.hq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.site.mc.LevelBlockWorld;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The style layer: every style paints the same plan shape (modules, anchors, workstations, cells),
 * deterministically, with a complete palette whose blocks all parse, and the active one comes from
 * {@code hq.style} in the config (cherry by default). Plus the redesign's invariants per style.
 */
class HqStyleTest {
	@BeforeAll
	static void boot() {
		HqTestSupport.boot();
		HqFixtures.use(null);
		HqKit.use(null);
	}

	@AfterEach
	void resetStyle() {
		HqStyles.use(null);
	}

	/** 0, 1, 2 and 4 projects, with annexes (more than 6 threads) and settled threads. */
	private static List<HqInput> rosters() {
		List<HqInput> out = new ArrayList<>();
		out.add(HqInput.EMPTY);
		out.add(HqTestSupport.input(1, 3));
		out.add(HqTestSupport.settle(HqTestSupport.input(2, 8), "env:p1-t2"));
		out.add(HqTestSupport.settle(HqTestSupport.input(4, 7), "env:p0-t1"));
		out.add(HqTestSupport.input(3, 13));
		return out;
	}

	private static BuildPlan plan(HqStyle style, HqInput in) {
		return new HqGenerator(style).generate(HqLayout.plan(in));
	}

	@Test
	void everyStyleHasTheSameGeometryAnchorsAndWorkstations() {
		HqStyle ref = HqStyles.classic();
		for (HqInput in : rosters()) {
			BuildPlan a = plan(ref, in);
			for (HqStyle s : HqStyles.all()) {
				BuildPlan b = plan(s, in);
				String what = s.id() + " / " + in.projects().size() + " projects";
				assertEquals(a.modules(), b.modules(), what + ": modules (boxes, holes, groups)");
				assertEquals(a.anchors(), b.anchors(), what + ": anchors");
				assertEquals(a.workstations(), b.workstations(), what + ": workstations");
				assertEquals(a.footprint(), b.footprint(), what);
				assertEquals(a.size(), b.size(), what + ": same cells");
				for (Placement p : a.placements()) {
					assertNotNull(b.at(p.rel()), what + ": cell " + p.rel());
				}
			}
		}
	}

	@Test
	void generationIsDeterministicAndIgnoresRosterOrder() {
		for (HqStyle s : HqStyles.all()) {
			for (HqInput in : rosters()) {
				assertEquals(plan(s, in).fingerprint(), plan(s, in).fingerprint(), s.id());
				List<HqInput.Entry> roster = HqTestSupport.roster(in);
				for (long seed = 1; seed <= 3; seed++) {
					List<HqInput.Entry> shuffled = new ArrayList<>(roster);
					Collections.shuffle(shuffled, new Random(seed));
					assertEquals(plan(s, in).fingerprint(), plan(s, HqInput.fromRoster(shuffled)).fingerprint(), s.id() + " seed " + seed);
				}
			}
		}
	}

	@Test
	void everyStyleMapsEveryRole() {
		assertTrue(HqStyles.all().size() >= 2);
		for (HqStyle s : HqStyles.all()) {
			Map<HqStyle.Role, BlockState> pal = s.palette();
			for (HqStyle.Role r : HqStyle.Role.values()) {
				assertNotNull(pal.get(r), s.id() + " has no block for " + r);
				assertFalse(pal.get(r).isAir(), s.id() + ": " + r + " is air");
			}
			assertEquals(HqStyle.Role.values().length, pal.size(), s.id());
		}
		assertEquals(Set.copyOf(HqStyles.ids()).size(), HqStyles.ids().size(), "unique ids");
	}

	@Test
	void everyBlockOfEveryStyleParsesWithTheRealParser() {
		for (HqStyle s : HqStyles.all()) {
			Set<BlockState> states = new LinkedHashSet<>(s.palette().values());
			for (HqInput in : rosters()) {
				for (Placement p : plan(s, in).placements()) {
					for (Rotation r : Rotation.values()) {
						states.add(p.state().rotate(r));
					}
				}
			}
			for (BlockState st : states) {
				String key = LevelBlockWorld.keyOf(st);
				assertTrue(key.startsWith("minecraft:"), s.id() + ": vanilla only without the blocks module, got " + key);
				assertSame(st, LevelBlockWorld.parse(BuiltInRegistries.BLOCK, key), s.id() + ": " + key);
			}
		}
	}

	@Test
	void selectionByConfigIsAdditiveWithCherryByDefault() throws Exception {
		assertEquals("cherry", HqStyles.DEFAULT_ID);
		assertEquals("cherry", HqStyles.active().id(), "nothing configured: cherry");
		assertEquals("cherry", HqStyles.parse(null).style().id());
		assertEquals("cherry", HqStyles.parse(json("{}")).style().id());
		assertEquals("cherry", HqStyles.parse(json("{\"hq\":{}}")).style().id());
		assertTrue(HqStyles.parse(json("{\"hq\":{}}")).warnings().isEmpty());
		assertEquals("classic", HqStyles.parse(json("{\"hq\":{\"style\":\"classic\"}}")).style().id());
		assertEquals("classic", HqStyles.parse(json("{\"hq\":{\"style\":\" Classic \"}}")).style().id(), "trimmed, any case");
		HqStyles.Selection bad = HqStyles.parse(json("{\"hq\":{\"style\":\"bauhaus\"}}"));
		assertEquals("cherry", bad.style().id());
		assertEquals(List.of("hq.style"), bad.warnings());
		assertEquals(List.of("hq"), HqStyles.parse(json("{\"hq\":3}")).warnings());
		assertEquals(List.of("hq.style"), HqStyles.parse(json("{\"hq\":{\"style\":7}}")).warnings());
		// other sections are untouched and do not matter
		assertEquals("classic", HqStyles.parse(json("{\"survival\":{\"debugBuilds\":\"any\"},\"hq\":{\"style\":\"classic\"}}")).style().id());
		assertNull(HqStyles.byId("nope"));
		// the active style drives the default generator and HqPlans
		HqStyles.use(HqStyles.classic());
		assertEquals(plan(HqStyles.classic(), HqTestSupport.input(1, 2)).fingerprint(),
				new HqGenerator().generate(HqLayout.plan(HqTestSupport.input(1, 2))).fingerprint());
		assertTrue(HqPlans.buildKey().endsWith("@classic"));
		HqStyles.use(null);
		assertTrue(HqPlans.buildKey().endsWith("@cherry"));
	}

	@Test
	void loadReadsTheConfigFile(@TempDir Path dir) throws Exception {
		assertEquals("cherry", HqStyles.load(dir.resolve("absent.json")).style().id());
		Path f = dir.resolve("t3craft.json");
		Files.writeString(f, "{\"hq\":{\"style\":\"classic\"}}");
		assertEquals("classic", HqStyles.load(f).style().id());
		Files.writeString(f, "{not json");
		HqStyles.Selection broken = HqStyles.load(f);
		assertEquals("cherry", broken.style().id());
		assertFalse(broken.warnings().isEmpty());
	}

	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	// ------------------------------------------------------------------ redesign invariants, per style

	@Test
	void redesignInvariantsHoldInEveryStyle() {
		for (HqStyle s : HqStyles.all()) {
			for (HqInput in : rosters()) {
				BuildPlan p = plan(s, in);
				String what = s.id() + " / " + in.projects().size() + " projects";
				for (Placement pl : p.placements()) {
					assertTrue(pl.rel().getY() >= 0 && pl.rel().getY() <= HqGeometry.TOP, what + ": out of height " + pl.rel());
					if (pl.state().getBlock() instanceof LeavesBlock) {
						assertTrue(pl.state().getValue(BlockStateProperties.PERSISTENT), what + ": leaves must be persistent at " + pl.rel());
					}
					assertFalse(pl.state().is(Blocks.CHERRY_TRAPDOOR) && pl.rel().getY() == 0, what + ": trapdoor on a floor");
				}
				// agents stand: free feet and head over a solid floor, facing the monitor
				for (Workstation w : p.workstations()) {
					BlockPos stand = w.deskAnchor().block();
					assertTrue(p.at(stand).state().isAir() && p.at(stand.above()).state().isAir(), what + ": standing cell " + stand);
					assertFalse(p.at(stand.below()).state().isAir(), what + ": floor under " + stand);
					assertEquals(w.computerPos(), stand.relative(w.deskAnchor().facing()).above(), what + ": monitor in front, at eye level");
				}
				// every standing anchor is reachable: free feet and head, solid floor
				for (Anchor a : p.anchors().values()) {
					if (AnchorNames.standing(a.name())) {
						BlockState feet = p.at(a.block()).state();
						assertTrue(feet.isAir() || feet.getBlock().getDescriptionId().contains("carpet"), what + ": feet of " + a);
						assertTrue(p.at(a.block().above()).state().isAir(), what + ": head of " + a);
						assertFalse(p.at(a.block().below()).state().isAir(), what + ": floor of " + a);
					}
				}
				// the hologram's column is open, the beacon crowns it
				for (int y = 1; y <= HqGeometry.HOLOGRAM_TOP; y++) {
					assertTrue(p.at(new BlockPos(0, y, 0)).state().isAir(), what + ": hologram column at y=" + y);
				}
				assertFalse(p.at(p.anchor(AnchorNames.BEACON).block()).state().isAir(), what + ": beacon");
				// reserved surfaces are solid stand-ins, the kanban wall is sized by its two anchors
				for (Anchor a : p.anchors().values()) {
					AnchorNames.BigScreen bs = AnchorNames.parseBigScreen(a.name());
					if (bs != null) {
						assertFalse(p.at(a.block()).state().isAir(), what + ": " + a.name());
					}
				}
				Anchor wall = p.anchor(AnchorNames.THREAD_WALL);
				Anchor end = p.anchor(AnchorNames.THREAD_WALL_END);
				assertNotNull(wall);
				assertNotNull(end);
				assertEquals(wall.block().getY() + HqGeometry.threadWall().height() - 1, end.block().getY());
				for (BlockPos c : HqGeometry.threadWall().cells()) {
					assertFalse(p.at(c).state().isAir(), what + ": kanban wall cell " + c);
				}
				// doorways: open, three high, on a flat floor (no steps for the NPCs' A*)
				for (HqLayout.Module m : HqLayout.plan(in).modules()) {
					int i = m.index();
					for (int v = HqGeometry.DOOR_V1; v <= HqGeometry.DOOR_V2; v++) {
						for (int u : new int[] {-1, 0}) {
							int x = u < 0 ? HqGeometry.side(i) * HqGeometry.SPINE : HqGeometry.wx(i, 0);
							BlockPos d = new BlockPos(x, 1, HqGeometry.wz(i, v));
							for (int y = 0; y < 3; y++) {
								assertTrue(p.at(d.above(y)).state().isAir(), what + ": doorway " + d.above(y));
							}
							assertTrue(p.at(d.below()).state().isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, d.below()),
									what + ": flat floor under the doorway " + d);
						}
					}
				}
				for (int x = -1; x <= 1; x++) {
					BlockPos e = new BlockPos(x, 1, HqGeometry.HUB);
					assertTrue(p.at(e).state().isAir() && p.at(e.above()).state().isAir() && p.at(e.above(2)).state().isAir(), what + ": entrance");
				}
			}
		}
	}
}
