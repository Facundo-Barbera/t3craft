package dev.t3craft.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.build.BuildPlan;
import dev.t3craft.build.Placement;
import dev.t3craft.hq.HqPlans;
import dev.t3craft.site.mc.LevelBlockWorld;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The HQ with the blocks module on: the real {@link T3Blocks#init} registers {@code t3craft:*}
 * blocks into the built-in registry (unfrozen for the call, as Fabric keeps it during mod init) and
 * every state of the modded HQ plan goes through the site's block-state parser.
 */
class ModBlockStatesTest {
	@BeforeAll
	static void register() throws Exception {
		BlocksTestSupport.boot(); // before touching BuiltInRegistries (its class init needs the bootstrap)
		List<Registry<?>> registries = List.of(BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM, BuiltInRegistries.BLOCK_ENTITY_TYPE);
		T3Blocks.resetForTests();
		if (BuiltInRegistries.BLOCK.getOptional(T3Blocks.id("monitor")).isEmpty()) {
			for (Registry<?> r : registries) {
				set(r, "frozen", false);
				set(r, "unregisteredIntrusiveHolders", new IdentityHashMap<>());
			}
			try {
				assertNotNull(T3Blocks.init(BlocksSwitch.DEFAULT, BlockRegistrar.VANILLA));
			} finally {
				// back to frozen (freeze() itself refuses: tags are already bound)
				for (Registry<?> r : registries) {
					set(r, "unregisteredIntrusiveHolders", null);
					set(r, "frozen", true);
				}
			}
		} else {
			// already registered by an earlier run in this JVM: just re-install the adapter
			installFixtures();
		}
	}

	private static void installFixtures() {
		dev.t3craft.hq.HqFixtures.use(new HqBlockFixtures(new HqBlockFixtures.States() {
			@Override
			public BlockState monitor(net.minecraft.core.Direction facing) {
				return BuiltInRegistries.BLOCK.getValue(T3Blocks.id("monitor")).defaultBlockState().setValue(MonitorBlock.FACING, facing);
			}

			@Override
			public BlockState lamp() {
				return BuiltInRegistries.BLOCK.getValue(T3Blocks.id("status_lamp")).defaultBlockState();
			}

			@Override
			public BlockState boardLamp() {
				return BuiltInRegistries.BLOCK.getValue(T3Blocks.id("board_lamp")).defaultBlockState();
			}

			@Override
			public BlockState archiveTerminal(net.minecraft.core.Direction facing) {
				return BuiltInRegistries.BLOCK.getValue(T3Blocks.id("archive_terminal")).defaultBlockState().setValue(ArchiveTerminalBlock.FACING, facing);
			}
		}));
	}

	@AfterAll
	static void reset() {
		T3Blocks.resetForTests();
	}

	private static void set(Object registry, String field, Object value) throws ReflectiveOperationException {
		Field f = MappedRegistry.class.getDeclaredField(field);
		f.setAccessible(true);
		f.set(registry, value);
	}

	@Test
	void moddedHqStatesRoundTripThroughTheParser() {
		List<BuildPlan> plans = new ArrayList<>(HqPlans.forRoster(BlocksTestSupport.roster()));
		plans.addAll(HqPlans.forDemo(BlocksTestSupport.roster()));
		Set<BlockState> states = new LinkedHashSet<>();
		for (BuildPlan plan : plans) {
			for (Placement p : plan.placements()) {
				for (Rotation r : Rotation.values()) {
					states.add(p.state().rotate(r));
				}
			}
		}
		Set<String> modIds = new TreeSet<>();
		for (BlockState s : states) {
			String key = LevelBlockWorld.keyOf(s);
			assertSame(s, LevelBlockWorld.parse(BuiltInRegistries.BLOCK, key), key);
			if (key.startsWith(T3Blocks.NS + ":")) {
				modIds.add(key.substring(0, key.indexOf('[') < 0 ? key.length() : key.indexOf('[')));
			}
		}
		assertEquals(Set.of("t3craft:archive_terminal", "t3craft:board_lamp", "t3craft:monitor", "t3craft:status_lamp"), modIds, "the modded HQ uses every module block");
		assertTrue(LevelBlockWorld.parse(BuiltInRegistries.BLOCK, "t3craft:monitor").getBlock() instanceof MonitorBlock);
	}
}
