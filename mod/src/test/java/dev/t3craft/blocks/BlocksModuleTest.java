package dev.t3craft.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqPlans;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The module switch and the "off" path: nothing registered, every adapter back to vanilla. */
class BlocksModuleTest {
	/** Records every call; a disabled module must leave it empty. */
	static final class Recording implements BlockRegistrar {
		final List<String> calls = new ArrayList<>();

		@Override
		public Block block(Identifier id, Block block) {
			calls.add("block " + id);
			return block;
		}

		@Override
		public Item item(Identifier id, Item item) {
			calls.add("item " + id);
			return item;
		}

		@Override
		public <T extends BlockEntity> BlockEntityType<T> blockEntity(Identifier id, BlockEntityType<T> type) {
			calls.add("block_entity " + id);
			return type;
		}

		@Override
		public void creativeTab(List<Item> items) {
			calls.add("tab " + items.size());
		}
	}

	@AfterEach
	void reset() {
		T3Blocks.resetForTests();
	}

	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	@Test
	void switchDefaultsToOn() {
		assertTrue(BlocksSwitch.resolve(null, null).enabled());
		assertTrue(BlocksSwitch.resolve("", json("{}")).enabled());
		assertTrue(BlocksSwitch.resolve(null, json("{\"blocks\": {\"enabled\": \"no\"}}")).enabled(), "wrong type → default");
		assertTrue(BlocksSwitch.resolve(null, json("{\"blocks\": true}")).enabled(), "not an object → default");
	}

	@Test
	void configTurnsItOff() {
		BlocksSwitch s = BlocksSwitch.resolve(null, json("{\"blocks\": {\"enabled\": false}, \"workstations\": {\"enabled\": true}}"));
		assertFalse(s.enabled());
		assertTrue(s.reason().contains("config"));
	}

	@Test
	void propertyWinsOverConfig() {
		assertFalse(BlocksSwitch.resolve("false", json("{\"blocks\": {\"enabled\": true}}")).enabled());
		assertTrue(BlocksSwitch.resolve("TRUE", json("{\"blocks\": {\"enabled\": false}}")).enabled());
		assertFalse(BlocksSwitch.resolve(" off ", null).enabled());
		assertTrue(BlocksSwitch.resolve("garbage", null).enabled(), "unknown value is ignored");
	}

	@Test
	void loadIsTolerant(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("t3craft.json");
		assertTrue(BlocksSwitch.load(f).enabled() || System.getProperty(BlocksSwitch.PROPERTY) != null, "missing file → default");
		Files.writeString(f, "{ broken");
		assertTrue(BlocksSwitch.load(f).enabled() || System.getProperty(BlocksSwitch.PROPERTY) != null, "broken file → default");
		Files.writeString(f, "{\"blocks\": {\"enabled\": false}}");
		if (System.getProperty(BlocksSwitch.PROPERTY) == null) {
			assertFalse(BlocksSwitch.load(f).enabled());
		}
	}

	@Test
	void offRegistersNothing() {
		Recording r = new Recording();
		assertNull(T3Blocks.init(BlocksSwitch.OFF, r));
		assertEquals(List.of(), r.calls);
		assertFalse(T3Blocks.active());
		assertNull(T3Blocks.content());
		assertFalse(T3Blocks.decision().enabled());
	}

	@Test
	void offLeavesTheHqVanilla() {
		HqFixtures.use(plan -> {
			throw new AssertionError("stale fixtures from an earlier run must be dropped");
		});
		T3Blocks.init(BlocksSwitch.OFF, new Recording());
		assertSame(HqFixtures.VANILLA, HqFixtures.active());
		BlocksTestSupport.boot();
		HqLayout layout = HqLayout.plan(BlocksTestSupport.input());
		BuildPlan vanilla = new HqGenerator().generate(layout);
		assertEquals(vanilla.fingerprint(), HqPlans.generate(layout).fingerprint(), "HqPlans gives exactly the vanilla plan");
	}

	@Test
	void vanillaFixturesAreIdentity() {
		BlocksTestSupport.boot();
		BuildPlan p = new HqGenerator().generate(HqLayout.plan(BlocksTestSupport.input()));
		assertSame(p, HqFixtures.VANILLA.apply(p));
		assertSame(p, p.replacing(x -> x), "no change → same plan instance");
	}

	@Test
	void hqInputIsTheNpcKey() {
		// the HQ's thread id must be the NPC key, so bindings and NpcRegistry agree
		HqInput in = BlocksTestSupport.input();
		assertTrue(in.projects().get(0).threads().get(0).contains(":"));
	}
}
