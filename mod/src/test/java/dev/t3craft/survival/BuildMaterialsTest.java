package dev.t3craft.survival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.build.BuildPlan;
import dev.t3craft.build.DemoPlans;
import dev.t3craft.survival.SurvivalSettings.Requirement;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BuildMaterialsTest {
	static final SurvivalRules DEFAULT = new GameModePolicy(SurvivalSettings.DEFAULTS);

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void survivalBuildsAndPays() {
		BuildMaterials.Decision d = BuildMaterials.decide(DEFAULT, PlayContext.survival(false));
		assertTrue(d.verdict().allowed());
		assertTrue(d.charged());
		assertTrue(BuildMaterials.decide(DEFAULT, PlayContext.survival(false).withCheats(true)).charged(), "cheats do not make survival free");
	}

	@Test
	void creativeBuildsForFree() {
		BuildMaterials.Decision d = BuildMaterials.decide(DEFAULT, PlayContext.creative());
		assertTrue(d.verdict().allowed());
		assertFalse(d.charged());
		assertEquals(MaterialSource.FREE, d.source());
	}

	@Test
	void deniedModesSayWhy() {
		assertEquals("spectator", BuildMaterials.decide(DEFAULT, PlayContext.creative().withMode(PlayMode.SPECTATOR)).verdict().reason());
		BuildMaterials.Decision adv = BuildMaterials.decide(DEFAULT, PlayContext.survival(false).withMode(PlayMode.ADVENTURE));
		assertEquals("adventure", adv.verdict().reason());
		assertFalse(adv.charged());
		assertEquals("t3craft.survival.adventure", adv.verdict().langKey());
		PlayContext remote = new PlayContext(PlayMode.CREATIVE, true, false, false, false);
		assertEquals("needs_singleplayer", BuildMaterials.decide(DEFAULT, remote).verdict().reason());
		assertTrue(BuildMaterials.decide(DEFAULT, PlayContext.survival(false).withMode(PlayMode.ADVENTURE).withCheats(true)).charged(),
				"adventure with cheats builds, and pays");
	}

	@Test
	void configDecidesWhoPays() {
		SurvivalRules never = new GameModePolicy(SurvivalSettings.DEFAULTS.withBuilderMaterials(Requirement.NEVER));
		assertFalse(BuildMaterials.decide(never, PlayContext.survival(false)).charged());
		SurvivalRules always = new GameModePolicy(SurvivalSettings.DEFAULTS.withBuilderMaterials(Requirement.ALWAYS));
		assertTrue(BuildMaterials.decide(always, PlayContext.creative()).charged());
	}

	@Test
	void billCountsOneItemPerPaidBlock() {
		BuildPlan plan = BuildPlan.builder()
				.fill(0, 0, 0, 2, 0, 0, Blocks.COBBLESTONE.defaultBlockState())
				.set(new BlockPos(0, 1, 0), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER))
				.set(new BlockPos(0, 2, 0), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER))
				.set(new BlockPos(1, 1, 0), Blocks.STRAW_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT))
				.set(new BlockPos(1, 1, 1), Blocks.STRAW_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD))
				.set(new BlockPos(2, 1, 0), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH))
				.set(new BlockPos(2, 2, 0), Blocks.AIR.defaultBlockState())
				.set(new BlockPos(2, 3, 0), Blocks.WATER.defaultBlockState())
				.build();
		MaterialBill bill = BuildMaterials.bill(plan);
		assertEquals(Map.of("minecraft:cobblestone", 3, "minecraft:oak_door", 1, "minecraft:straw_bed", 1, "minecraft:torch", 1), bill.items());
		assertEquals(6, bill.total());
	}

	@Test
	void demoShedHasAPositiveBill() {
		MaterialBill bill = BuildMaterials.bill(DemoPlans.shed());
		assertTrue(bill.total() > 0);
		assertTrue(bill.total() < DemoPlans.shed().size(), "air and upper door half are free");
	}
}
