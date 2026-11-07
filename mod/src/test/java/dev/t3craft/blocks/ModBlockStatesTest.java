package dev.t3craft.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.build.BuildPlan;
import dev.t3craft.build.Placement;
import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.HqKit;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqPlans;
import dev.t3craft.site.mc.LevelBlockWorld;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The module on: the real {@link T3Blocks#init} registers {@code t3craft:*} blocks into the built-in
 * registry, every state of the modded HQ plan and every state of every kit block goes through the
 * site's block-state parser, and the kit's roles are t3craft blocks.
 */
class ModBlockStatesTest {
	@BeforeAll
	static void register() throws Exception {
		BlocksTestSupport.registerModule();
	}

	@AfterAll
	static void reset() {
		T3Blocks.resetForTests();
	}

	private static void roundTrip(BlockState s) {
		String key = LevelBlockWorld.keyOf(s);
		assertSame(s, LevelBlockWorld.parse(BuiltInRegistries.BLOCK, key), key);
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
		assertEquals(Set.of("t3craft:archive_terminal", "t3craft:board_lamp", "t3craft:monitor", "t3craft:room_plaque", "t3craft:status_lamp"), modIds,
				"the modded HQ uses every fixture block, the archive terminal and plaques for its signs");
		assertTrue(LevelBlockWorld.parse(BuiltInRegistries.BLOCK, "t3craft:monitor").getBlock() instanceof MonitorBlock);
	}

	@Test
	void everyKitBlockIsRegisteredAndEveryStateParses() {
		assertEquals(11, KitBlocks.IDS.size());
		assertEquals(Set.copyOf(KitBlocks.IDS), Set.copyOf(KitBlocks.ROLE_BLOCKS.values()), "every kit block plays a role");
		for (String id : KitBlocks.IDS) {
			Block b = BlocksTestSupport.registered(id);
			assertTrue(BuiltInRegistries.ITEM.getOptional(T3Blocks.id(id)).isPresent(), "block item " + id);
			for (BlockState s : b.getStateDefinition().getPossibleStates()) {
				for (Rotation r : Rotation.values()) {
					roundTrip(s.rotate(r));
				}
			}
		}
		assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(T3Blocks.id("room_plaque")).isPresent());
		BlockState lantern = BlocksTestSupport.registered("paper_lantern").defaultBlockState();
		assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(T3Blocks.id("status_lamp")).isValid(lantern),
				"the paper lantern is a status lamp block entity (breathes with its thread)");
	}

	@Test
	void kitRolesAreModBlocks() {
		HqKit kit = HqKit.active();
		assertTrue(kit.custom());
		assertEquals(KitBlocks.ID, HqKit.fingerprint());
		for (HqKit.Role role : HqKit.Role.values()) {
			for (Direction d : Direction.values()) {
				BlockState s = kit.state(role, d);
				String key = LevelBlockWorld.keyOf(s);
				assertTrue(key.startsWith("t3craft:" + KitBlocks.ROLE_BLOCKS.get(role)), role + " → " + key);
				roundTrip(s);
			}
		}
		assertEquals(Direction.Axis.X, kit.beam(Direction.Axis.X).getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS));
		assertEquals(Direction.EAST, kit.desk(Direction.EAST).getValue(KitFacingBlock.FACING));
		assertTrue(kit.wallPanel(true).getLightEmission() > 0 && kit.wallPanel(false).getLightEmission() == 0);
		assertTrue(kit.lantern().getLightEmission() > 0, "unbound paper lantern still lights the room");
		assertTrue(kit.gardenLantern().getLightEmission() > 0);
		assertEquals("Web", kit.plaqueData(DyeColor.CYAN, "Web", "", "vps").getStringOr("line0", ""));
	}

	@Test
	void moduleSignsBecomePlaquesAndAnchoredSignsStay() {
		dev.t3craft.hq.BuildPlan hq = HqPlans.generate(HqLayout.plan(BlocksTestSupport.input()));
		Set<BlockPos> anchored = new HashSet<>();
		for (Anchor a : hq.anchors().values()) {
			anchored.add(a.block());
		}
		int plaques = 0;
		boolean sawProject = false;
		for (dev.t3craft.hq.Placement p : hq.placements()) {
			if (p.state().getBlock() instanceof KitPlaqueBlock) {
				plaques++;
				assertNotNull(p.be());
				assertFalse(anchored.contains(p.rel()));
				sawProject |= p.be().getStringOr("line0", "").equals("API") || p.be().getStringOr("line0", "").equals("Web");
			} else if (p.state().getBlock() instanceof WallSignBlock) {
				assertTrue(anchored.contains(p.rel()), "only anchored signs stay vanilla: " + p.rel());
			}
		}
		assertTrue(plaques >= 2, "module signs → plaques (" + plaques + ")");
		assertTrue(sawProject, "the plaque keeps the project name");
		assertInstanceOf(KitPlaqueBlock.class, HqKit.active().plaque(Direction.WEST).getBlock());
	}
}
