package dev.t3craft.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu "Configure" button → {@link SettingsScreen}. Mod Menu is optional: this class is only
 * referenced from the {@code modmenu} entrypoint in {@code fabric.mod.json}, which Fabric Loader
 * loads only when Mod Menu is installed; the API is a compile-only dependency.
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new SettingsScreen(parent, EnvironmentActions.DEFAULT);
	}
}
