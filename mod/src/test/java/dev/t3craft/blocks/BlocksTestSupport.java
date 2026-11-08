package dev.t3craft.blocks;

import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqKit;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Vanilla bootstrap (block registries) and a small HQ roster with NPC-style keys. */
final class BlocksTestSupport {
	private static boolean booted;

	private BlocksTestSupport() {
	}

	static synchronized void boot() {
		if (!booted) {
			SharedConstants.tryDetectVersion();
			Bootstrap.bootStrap();
			booted = true;
		}
	}

	/**
	 * Runs the real {@link T3Blocks#init} into the built-in registries (unfrozen for the call, as Fabric
	 * keeps them during mod init) once per JVM; later calls only re-install the HQ adapters.
	 */
	static synchronized void registerModule() throws ReflectiveOperationException {
		boot();
		List<Registry<?>> registries = List.of(BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM, BuiltInRegistries.BLOCK_ENTITY_TYPE);
		T3Blocks.resetForTests();
		if (BuiltInRegistries.BLOCK.getOptional(T3Blocks.id("monitor")).isEmpty()) {
			for (Registry<?> r : registries) {
				set(r, "frozen", false);
				set(r, "unregisteredIntrusiveHolders", new IdentityHashMap<>());
			}
			try {
				if (T3Blocks.init(BlocksSwitch.DEFAULT, BlockRegistrar.VANILLA) == null) {
					throw new AssertionError("module did not register");
				}
			} finally {
				// back to frozen (freeze() itself refuses: tags are already bound)
				for (Registry<?> r : registries) {
					set(r, "unregisteredIntrusiveHolders", null);
					set(r, "frozen", true);
				}
			}
		} else {
			installAdapters();
		}
	}

	static Block registered(String id) {
		return BuiltInRegistries.BLOCK.getOptional(T3Blocks.id(id)).orElseThrow(() -> new AssertionError("not registered: " + id));
	}

	/** Already registered by an earlier run in this JVM: rebuild fixtures and kit from the registry. */
	@SuppressWarnings("unchecked")
	private static void installAdapters() {
		Map<String, Block> kitBlocks = new LinkedHashMap<>();
		for (String id : KitBlocks.IDS) {
			kitBlocks.put(id, registered(id));
		}
		KitBlocks kit = KitBlocks.of(kitBlocks,
				(BlockEntityType<PlaqueBlockEntity>) BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(T3Blocks.id("room_plaque")));
		HqKit.use(kit);
		HqFixtures.use(new HqBlockFixtures(new HqBlockFixtures.States() {
			@Override
			public BlockState monitor(Direction facing) {
				return registered("monitor").defaultBlockState().setValue(MonitorBlock.FACING, facing);
			}

			// same as T3Blocks.Content, so test order never changes what the fixtures place
			@Override
			public BlockState monitor(Direction facing, ScreenRect.Sides sides) {
				return MonitorBlock.connected(monitor(facing), sides);
			}

			@Override
			public BlockState threadBoard(Direction facing) {
				return registered("thread_board").defaultBlockState().setValue(ThreadBoardBlock.FACING, facing);
			}

			@Override
			public BlockState lamp() {
				return registered("status_lamp").defaultBlockState();
			}

			@Override
			public BlockState boardLamp() {
				return registered("board_lamp").defaultBlockState();
			}

			@Override
			public BlockState archiveTerminal(Direction facing) {
				return registered("archive_terminal").defaultBlockState().setValue(ArchiveTerminalBlock.FACING, facing);
			}

			@Override
			public BlockState plaque(Direction facing) {
				return kit.plaque(facing);
			}

			@Override
			public CompoundTag plaqueData(int color, String... lines) {
				return PlaqueText.data(color, lines);
			}
		}));
	}

	private static void set(Object registry, String field, Object value) throws ReflectiveOperationException {
		Field f = MappedRegistry.class.getDeclaredField(field);
		f.setAccessible(true);
		f.set(registry, value);
	}

	/** Two projects in two environments; {@code vps:t4} is settled (goes to the archive). */
	static List<HqInput.Entry> roster() {
		List<HqInput.Entry> r = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			r.add(new HqInput.Entry("vps", "VPS", "api", "API", "vps:t" + i, i == 4));
		}
		for (int i = 0; i < 3; i++) {
			r.add(new HqInput.Entry("acmesite", "Acmesite", "web", "Web", "acmesite:w" + i, false));
		}
		return r;
	}

	static List<HqInput.Entry> shuffled(long seed) {
		List<HqInput.Entry> r = roster();
		Collections.shuffle(r, new Random(seed));
		return r;
	}

	static HqInput input() {
		return HqInput.fromRoster(roster());
	}
}
