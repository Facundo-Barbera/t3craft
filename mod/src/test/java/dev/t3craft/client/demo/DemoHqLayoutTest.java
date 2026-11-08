package dev.t3craft.client.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.t3craft.client.hq.layout.HqLayoutState;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.hq.layout.HqLayouts;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** feat/hq-layouts: the HQ world remembers its layout, and another layout never builds over it. */
class DemoHqLayoutTest {
	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@AfterEach
	void reset() {
		HqLayouts.use(null);
	}

	private static List<NpcInfo> live() {
		List<NpcInfo> out = new ArrayList<>();
		for (int p = 0; p < 4; p++) {
			String env = p % 2 == 0 ? "vps" : "laptop";
			for (int t = 0; t < 1 + p; t++) {
				String id = env + "-p" + p + "-t" + t;
				out.add(new NpcInfo(id, id, id, env, "", 0xFFFFFF, null, 0, false, "idle", env + ":p" + p, "p" + p, "", null));
			}
		}
		return out;
	}

	/** Prepares a source with {@code configured} against {@code marker} and records the build (the marker the world would save). */
	private static DemoMarker build(DemoMarker marker, String configured) {
		List<NpcInfo> roster = live();
		DemoHqSource src = new DemoHqSource(() -> roster, () -> configured);
		src.prepare(marker, DemoWorldSpec.defaults(), -64);
		assertTrue(src.available(), "plan of " + configured);
		return DemoMarker.parse(marker.withBuilt(src).toJson());
	}

	@Test
	void builtLayoutIdIsPersistedInTheWorldMarker(@TempDir Path world) {
		assertNull(DemoClient.builtLayoutId(world), "nothing built yet");
		DemoMarker built = build(DemoMarker.EMPTY, "ring");
		DemoMarkerFile.update(world, m -> built);
		assertEquals("ring", DemoClient.builtLayoutId(world));
		assertEquals("ring", HqLayoutState.builtLayoutId(world));
		assertEquals("ring", DemoMarkerFile.read(world).state().get(HqLayoutState.KEY).getAsString());
	}

	@Test
	void markerRules() {
		assertNull(HqLayoutState.fromMarker(null, null));
		assertNull(HqLayoutState.fromMarker("shed", null), "the shed is not an HQ");
		assertEquals("classic", HqLayoutState.fromMarker("hq", new JsonObject()), "HQ built before layouts");
		JsonObject st = new JsonObject();
		st.addProperty(HqLayoutState.KEY, "village");
		assertEquals("village", HqLayoutState.fromMarker("hq", st));
		st.addProperty(HqLayoutState.KEY, "gone");
		assertEquals("classic", HqLayoutState.fromMarker("hq", st));
	}

	@Test
	void anotherConfiguredLayoutIsNotAppliedWithoutARebuild() {
		DemoMarker built = build(DemoMarker.EMPTY, "vertical");
		List<NpcInfo> roster = live();
		DemoHqSource src = new DemoHqSource(() -> roster, () -> "campus");
		src.prepare(built, DemoWorldSpec.defaults(), -64);
		assertEquals("vertical", src.layoutId(), "keeps the layout it is built with");
		assertEquals("campus", src.pendingLayout(), "the configured one waits for a reset");
		assertEquals("vertical", HqLayouts.active().id());
		assertFalse(built.needsBuild(src), "nothing to (re)build: no campus mixed over the towers");
		assertTrue(src.plansSince(built).isEmpty());
		assertEquals("vertical", DemoMarker.parse(built.withBuilt(src).toJson()).state().get(HqLayoutState.KEY).getAsString());

		// after a reset the world is empty: the configured layout is built
		DemoHqSource fresh = new DemoHqSource(() -> roster, () -> "campus");
		fresh.prepare(DemoMarker.EMPTY, DemoWorldSpec.defaults(), -64);
		assertEquals("campus", fresh.layoutId());
		assertNull(fresh.pendingLayout());
		assertFalse(fresh.plans().isEmpty());
	}

	@Test
	void effectiveLayout() {
		assertEquals("ring", DemoHqSource.effectiveLayout("ring", "campus"));
		assertEquals("campus", DemoHqSource.effectiveLayout(null, "campus"));
		assertEquals("classic", DemoHqSource.effectiveLayout(null, null));
		assertEquals("classic", DemoHqSource.effectiveLayout(null, "nope"));
	}

	@Test
	void everyLayoutPreparesInTheDemo() {
		for (var i : HqLayouts.all()) {
			DemoMarker m = build(DemoMarker.EMPTY, i.id());
			assertEquals(i.id(), HqLayoutState.fromMarker(m.structure(), m.state()));
		}
	}
}
