package dev.t3craft.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.build.BuildPlan;
import dev.t3craft.build.DemoPlans;
import dev.t3craft.build.Placement;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqPlans;
import dev.t3craft.site.mc.BuildPlanBridge;
import dev.t3craft.site.mc.LevelBlockWorld;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Every block state of the whole template (HQ base, modules, archive, demo HQ, builders' shed, the
 * site's leveling and placeholder) goes through the real parser over the vanilla block registry, so a
 * key the game cannot resolve fails here and not in the world.
 */
class TemplateBlockStatesTest {
	private static HolderLookup<Block> blocks;

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		blocks = BuiltInRegistries.BLOCK;
	}

	private static List<HqInput.Entry> roster() {
		List<HqInput.Entry> out = new ArrayList<>();
		for (int p = 0; p < 4; p++) {
			for (int t = 0; t < 6; t++) {
				out.add(new HqInput.Entry("env", "env", "p" + p, "Project " + p, "p" + p + "-t" + t, t >= 4));
			}
		}
		return out;
	}

	/** All states of the template, in every rotation the builders and the site use. */
	private static Set<BlockState> templateStates() {
		List<BuildPlan> plans = new ArrayList<>();
		List<HqInput.Entry> roster = roster();
		plans.addAll(HqPlans.forRoster(roster));
		plans.addAll(HqPlans.forDemo(roster));
		plans.addAll(HqPlans.byGroup(HqGenerator.plan(HqInput.fromRoster(roster), null).plan()).values());
		plans.add(DemoPlans.shed());
		Set<BlockState> out = new LinkedHashSet<>();
		for (BuildPlan plan : plans) {
			for (Placement p : plan.placements()) {
				for (Rotation r : Rotation.values()) {
					out.add(p.state().rotate(r));
				}
			}
		}
		return out;
	}

	/** The site's own string keys: leveling materials, a real leveling plan and the placeholder. */
	private static Set<String> siteKeys() throws IllegalAccessException {
		Set<String> out = new LinkedHashSet<>();
		for (Field f : Leveling.class.getFields()) {
			if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
				out.add((String) f.get(null));
			}
		}
		FakeWorld w = new FakeWorld().flat(-40, -40, 40, 40, 64);
		for (int x = 4; x <= 9; x++) {
			for (int z = 4; z <= 9; z++) {
				w.column(x, z, 67);
			}
		}
		for (int x = -9; x <= -4; x++) {
			for (int z = -9; z <= -4; z++) {
				w.column(x, z, 61);
			}
		}
		Columns cols = new Columns(w);
		SiteEvaluator.Evaluation e = SiteEvaluator.evaluate(cols, 0, 0);
		assertTrue(e.ok(), e.problems().toString());
		CellPlan level = Leveling.plan(cols, e, CellPlan.Facing.SOUTH);
		addAll(out, level);
		Box area = new Box(level.box().minX(), e.floorY() + 1, level.box().minZ(), level.box().maxX(), e.floorY() + 8, level.box().maxZ());
		addAll(out, StructureSource.PLACEHOLDER.design(area, e.origin()));
		// HQ module plans through the bridge the site uses
		BuildPlan shed = DemoPlans.shed();
		for (CellPlan.Facing f : CellPlan.Facing.values()) {
			CellPlan.Origin o = new CellPlan.Origin(0, 65, 0, f);
			addAll(out, BuildPlanBridge.designer(m -> shed).design(new SiteModule("shed", "shed", new Rect(-16, -16, 16, 16)), new Box(-16, 60, -16, 16, 80, 16), o));
		}
		return out;
	}

	private static void addAll(Set<String> out, CellPlan plan) {
		for (int i = 0; i < plan.size(); i++) {
			if (plan.get(i) != null) {
				out.add(plan.get(i));
			}
		}
	}

	@Test
	void everyTemplateStateRoundTripsThroughTheParser() {
		Set<BlockState> states = templateStates();
		assertTrue(states.size() > 50, "template has " + states.size() + " states");
		for (BlockState s : states) {
			String key = LevelBlockWorld.keyOf(s);
			assertSame(s, LevelBlockWorld.parse(blocks, key), key);
			assertSame(s, LevelBlockWorld.parse(blocks, key.replaceFirst("^minecraft:", "")), "without namespace: " + key);
		}
	}

	@Test
	void everySiteKeyParsesAndIsCanonical() throws Exception {
		Set<String> keys = siteKeys();
		assertTrue(keys.contains("minecraft:stone_bricks"));
		for (String key : keys) {
			BlockState s = LevelBlockWorld.parse(blocks, key);
			assertFalse(s.isAir() && !StateKey.isAir(key), key + " parsed as air");
			assertEquals(key, LevelBlockWorld.keyOf(s), "keys compare by string, so they must be canonical");
		}
	}

	@Test
	void keysAndErrors() {
		assertEquals("minecraft:stone_bricks", LevelBlockWorld.keyOf(Blocks.STONE_BRICKS.defaultBlockState()));
		assertSame(Blocks.STONE_BRICKS.defaultBlockState(), LevelBlockWorld.parse(blocks, "minecraft:stone_bricks"));
		assertSame(Blocks.STONE_BRICKS.defaultBlockState(), LevelBlockWorld.parse(blocks, "stone_bricks"));
		assertEquals("minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
				LevelBlockWorld.keyOf(Blocks.OAK_STAIRS.defaultBlockState()));
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> LevelBlockWorld.parse(blocks, "minecraft:not_a_block"));
		assertTrue(e.getMessage().contains("minecraft:not_a_block"), e.getMessage());
		assertThrows(IllegalArgumentException.class, () -> LevelBlockWorld.parse(blocks, "minecraft:stone_bricks[facing=north]"));
		assertThrows(IllegalArgumentException.class, () -> LevelBlockWorld.parse(blocks, "minecraft:oak_stairs[facing=up]"));
	}
}
