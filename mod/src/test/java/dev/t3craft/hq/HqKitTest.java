package dev.t3craft.hq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.site.mc.LevelBlockWorld;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Every kit role has a vanilla fallback that the site's parser resolves (blocks module off). */
class HqKitTest {
	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@AfterEach
	void reset() {
		HqKit.use(null);
	}

	@Test
	void vanillaIsTheDefault() {
		HqKit.use(null);
		assertSame(HqKit.VANILLA, HqKit.active());
		assertFalse(HqKit.VANILLA.custom());
		assertEquals("vanilla", HqKit.fingerprint());
	}

	@Test
	void everyRoleHasAVanillaFallback() {
		for (HqKit.Role role : HqKit.Role.values()) {
			for (Direction d : Direction.values()) {
				BlockState s = HqKit.VANILLA.state(role, d);
				assertFalse(s.isAir(), role.id());
				String key = LevelBlockWorld.keyOf(s);
				assertTrue(key.startsWith("minecraft:"), role + " → " + key);
				assertSame(s, LevelBlockWorld.parse(BuiltInRegistries.BLOCK, key), key);
				assertEquals(s, HqKit.VANILLA.state(role, d), "pure");
			}
		}
	}

	@Test
	void orientedRolesFollowFacing() {
		HqKit k = HqKit.VANILLA;
		assertEquals(Direction.EAST, k.plaque(Direction.EAST).getValue(BlockStateProperties.HORIZONTAL_FACING));
		assertEquals(Direction.Axis.X, k.beam(Direction.Axis.X).getValue(BlockStateProperties.AXIS));
		assertEquals(Direction.Axis.Y, k.state(HqKit.Role.BEAM).getValue(BlockStateProperties.AXIS));
		assertTrue(k.wallPanel(true).getLightEmission() > 0, "lit panel emits light in vanilla too");
		assertTrue(k.lantern().getLightEmission() > 0 && k.gardenLantern().getLightEmission() > 0);
		for (HqKit.Role r : HqKit.Role.values()) {
			assertEquals(r == HqKit.Role.DESK || r == HqKit.Role.BEAM || r == HqKit.Role.PLAQUE || r == HqKit.Role.RAILING, r.oriented());
		}
	}

	@Test
	void vanillaPlaqueIsAWaxedSign() {
		CompoundTag t = HqKit.VANILLA.plaqueData(DyeColor.CYAN, "Project", "", "#2", "vps");
		assertTrue(t.contains("front_text"));
		assertTrue(t.getBooleanOr("is_waxed", false));
	}
}
