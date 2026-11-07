package dev.t3craft.survival;

import dev.t3craft.debug.DevReload;

/** Common-side wiring, one call from {@code T3Craft.onInitialize}. */
public final class SurvivalHooks {
	private SurvivalHooks() {
	}

	public static void register() {
		Survival.reload();
		FirstContact.register();
		// Hot-swap: rebuild the policy from config if its classes change shape.
		DevReload.get().register("survival", reason -> Survival.reload(),
				Survival.class, GameModePolicy.class, SurvivalSettings.class, PlayContext.class);
	}
}
