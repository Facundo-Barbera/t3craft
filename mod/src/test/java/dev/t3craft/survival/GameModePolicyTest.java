package dev.t3craft.survival;

import static org.junit.jupiter.api.Assertions.*;

import dev.t3craft.survival.SurvivalSettings.DebugGate;
import dev.t3craft.survival.SurvivalSettings.Requirement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GameModePolicyTest {
	static final SurvivalRules DEFAULT = new GameModePolicy(SurvivalSettings.DEFAULTS);

	@ParameterizedTest
	@EnumSource(PlayMode.class)
	void byDefaultTheKeyAlwaysOpensTheTerminal(PlayMode mode) {
		assertTrue(DEFAULT.openTerminal(PlayContext.survival(false).withMode(mode)).allowed());
	}

	@Test
	void survivalRequirementOnlyBitesInSurvivalAndAdventure() {
		SurvivalRules r = new GameModePolicy(SurvivalSettings.DEFAULTS.withTerminalForKey(Requirement.SURVIVAL));
		Verdict v = r.openTerminal(PlayContext.survival(false));
		assertFalse(v.allowed());
		assertEquals("needs_terminal", v.reason());
		assertEquals("t3craft.survival.needs_terminal", v.langKey());
		assertFalse(r.openTerminal(PlayContext.survival(false).withMode(PlayMode.ADVENTURE)).allowed());
		assertTrue(r.openTerminal(PlayContext.survival(true)).allowed(), "carrying one is enough");
		assertTrue(r.openTerminal(PlayContext.creative()).allowed());
		assertTrue(r.openTerminal(PlayContext.survival(false).withMode(PlayMode.SPECTATOR)).allowed());
	}

	@Test
	void alwaysRequirementAppliesInCreativeToo() {
		SurvivalRules r = new GameModePolicy(SurvivalSettings.DEFAULTS.withTerminalForKey(Requirement.ALWAYS));
		assertFalse(r.openTerminal(PlayContext.creative()).allowed());
		assertFalse(r.openTerminal(PlayContext.creative().withMode(PlayMode.SPECTATOR)).allowed());
	}

	@Test
	void neverLocksOutWhereTheItemCannotExist() {
		SurvivalRules r = new GameModePolicy(SurvivalSettings.DEFAULTS.withTerminalForKey(Requirement.ALWAYS));
		PlayContext remote = new PlayContext(PlayMode.SURVIVAL, false, false, false, false);
		assertTrue(r.openTerminal(remote).allowed(), "remote server / item disabled: the key keeps working");
	}

	@Test
	void buildersChargeMaterialsOnlyInSurvivalByDefault() {
		assertEquals(MaterialSource.PLAYER_INVENTORY, DEFAULT.materials(PlayContext.survival(false)));
		assertEquals(MaterialSource.PLAYER_INVENTORY, DEFAULT.materials(PlayContext.survival(false).withCheats(true)),
				"cheats do not make survival builds free; use creative");
		assertEquals(MaterialSource.FREE, DEFAULT.materials(PlayContext.creative()));
		SurvivalRules free = new GameModePolicy(SurvivalSettings.DEFAULTS.withBuilderMaterials(Requirement.NEVER));
		assertEquals(MaterialSource.FREE, free.materials(PlayContext.survival(false)));
		SurvivalRules always = new GameModePolicy(SurvivalSettings.DEFAULTS.withBuilderMaterials(Requirement.ALWAYS));
		assertEquals(MaterialSource.PLAYER_INVENTORY, always.materials(PlayContext.creative()));
	}

	@Test
	void buildingNeedsTheIntegratedServerAndAnEditableMode() {
		assertTrue(DEFAULT.buildInWorld(PlayContext.survival(false)).allowed());
		assertTrue(DEFAULT.buildInWorld(PlayContext.creative()).allowed());
		assertEquals("spectator", DEFAULT.buildInWorld(PlayContext.creative().withMode(PlayMode.SPECTATOR)).reason());
		assertEquals("adventure", DEFAULT.buildInWorld(PlayContext.survival(false).withMode(PlayMode.ADVENTURE)).reason());
		assertTrue(DEFAULT.buildInWorld(PlayContext.survival(false).withMode(PlayMode.ADVENTURE).withCheats(true)).allowed());
		assertEquals("needs_singleplayer", DEFAULT.buildInWorld(new PlayContext(PlayMode.CREATIVE, true, false, false, false)).reason());
	}

	@Test
	void freeDebugBuildsNeedCheatsOrCreative() {
		assertEquals("needs_cheats", DEFAULT.debugWorldEdit(PlayContext.survival(true)).reason());
		assertTrue(DEFAULT.debugWorldEdit(PlayContext.survival(false).withCheats(true)).allowed());
		assertTrue(DEFAULT.debugWorldEdit(PlayContext.creative().withCheats(false)).allowed());
		assertTrue(DEFAULT.debugWorldEdit(PlayContext.survival(false).withMode(PlayMode.ADVENTURE).withCheats(true)).allowed());
		assertEquals("spectator", DEFAULT.debugWorldEdit(PlayContext.creative().withMode(PlayMode.SPECTATOR)).reason());
		SurvivalRules any = new GameModePolicy(SurvivalSettings.DEFAULTS.withDebugBuilds(DebugGate.ANY));
		assertTrue(any.debugWorldEdit(PlayContext.survival(false)).allowed());
	}

	@Test
	void nullsFallBackToDefaults() {
		assertEquals(SurvivalSettings.DEFAULTS, new GameModePolicy(null).settings());
		SurvivalSettings s = new SurvivalSettings(null, null, null, true);
		assertEquals(SurvivalSettings.DEFAULTS, s);
		assertEquals(PlayMode.SURVIVAL, new PlayContext(null, false, true, true, false).mode());
		assertEquals(PlayMode.SURVIVAL, PlayMode.of("bogus"));
		assertEquals(PlayMode.CREATIVE, PlayMode.of(" Creative "));
	}

	@Test
	void holderIsNeverNull() {
		try {
			Survival.install(null);
			assertNotNull(Survival.rules());
			assertTrue(Survival.rules().firstContactAdvancement());
		} finally {
			Survival.install(null);
		}
	}
}
