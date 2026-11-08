package dev.t3craft.blocks;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * hq-render only: registers the mod's {@code t3craft:*} blocks into the built-in registry the way
 * {@code ModBlockStatesTest} does (unfreeze, {@link T3Blocks#init}, refreeze), which also installs
 * the HQ fixtures, so renders show the real monitors and lamps. Never part of the mod jar.
 */
public final class RenderBlocks {
	private RenderBlocks() {
	}

	public static void install() throws ReflectiveOperationException {
		List<Registry<?>> registries = List.of(BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM, BuiltInRegistries.BLOCK_ENTITY_TYPE);
		T3Blocks.resetForTests();
		for (Registry<?> r : registries) {
			set(r, "frozen", false);
			set(r, "unregisteredIntrusiveHolders", new IdentityHashMap<>());
		}
		try {
			if (T3Blocks.init(BlocksSwitch.DEFAULT, BlockRegistrar.VANILLA) == null) {
				throw new IllegalStateException("blocks module did not initialise");
			}
		} finally {
			for (Registry<?> r : registries) {
				set(r, "unregisteredIntrusiveHolders", null);
				set(r, "frozen", true);
			}
		}
	}

	private static void set(Object o, String name, Object value) throws ReflectiveOperationException {
		Field f = MappedRegistry.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(o, value);
	}
}
