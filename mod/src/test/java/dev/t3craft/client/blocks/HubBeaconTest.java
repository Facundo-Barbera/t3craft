package dev.t3craft.client.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.construct.HqSiteFrame;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The client-only hub beacon (blocks module off): where it goes and when the hub stands. */
class HubBeaconTest {
	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static BuildPlan plan() {
		return new HqGenerator().generate(HqLayout.plan(HqInput.fromRoster(List.of(
				new HqInput.Entry("vps", "VPS", "api", "API", "vps:t0", false)))));
	}

	@Test
	void sitsOnTheLanternCapOverTheHub() {
		BuildPlan p = plan();
		BlockPos spot = HubBeacon.spot(p);
		assertNotNull(spot);
		assertEquals(p.anchor(AnchorNames.BEACON).block(), spot);
		BlockPos hub = p.anchor(AnchorNames.HUB).block();
		assertEquals(hub.getX(), spot.getX());
		assertEquals(hub.getZ(), spot.getZ());
		assertTrue(spot.getY() > hub.getY() + 6, "above the roof");
		assertTrue(HubBeacon.LIFT > 2f, "clear of the lightning rod");
		// everything over the cap up to the glow is air or the rod: nothing hides it
		for (int dy = 2; dy <= 3; dy++) {
			assertTrue(p.at(spot.above(dy)) == null || p.at(spot.above(dy)).state().isAir(), "free at +" + dy);
		}
	}

	@Test
	void onlyWhenTheHubStands() {
		BlockPos spot = HubBeacon.spot(plan());
		assertTrue(HubBeacon.standing(spot, null), "demo: everything stands");
		String owner = HqSiteFrame.owner(spot.getX(), spot.getZ());
		assertNotNull(owner);
		assertTrue(HubBeacon.standing(spot, Set.of(owner)));
		assertFalse(HubBeacon.standing(spot, Set.of()), "hub not built yet");
		// module off + hub standing → the client glow
		assertEquals(LampGlow.BeaconMode.CLIENT, LampGlow.beaconMode(HubBeacon.standing(spot, null), false));
		assertEquals(LampGlow.BeaconMode.NONE, LampGlow.beaconMode(HubBeacon.standing(spot, Set.of()), false));
	}
}
