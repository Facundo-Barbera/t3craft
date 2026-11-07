package dev.t3craft.survival;

/**
 * User choices behind the policy, from the {@code "survival"} section of {@code config/t3craft.json}
 * ({@link SurvivalConfig}). Every value has a default, so an absent section changes nothing.
 *
 * @param terminalForKey          when opening the terminal with the key requires carrying one
 * @param builderMaterials        when builders consume blocks from the inventory
 * @param debugBuilds             who may use free world-editing debug commands
 * @param firstContactAdvancement grant "First contact" on first use of the terminal
 */
public record SurvivalSettings(Requirement terminalForKey, Requirement builderMaterials, DebugGate debugBuilds,
		boolean firstContactAdvancement) {

	/**
	 * Defaults: the key always works (as before this feature), builders charge materials only in
	 * survival/adventure, free debug builds need cheats or creative.
	 */
	public static final SurvivalSettings DEFAULTS = new SurvivalSettings(Requirement.NEVER, Requirement.SURVIVAL, DebugGate.CHEATS, true);

	public SurvivalSettings {
		// Literals, not DEFAULTS.x: DEFAULTS is still null while its own constructor runs.
		terminalForKey = terminalForKey == null ? Requirement.NEVER : terminalForKey;
		builderMaterials = builderMaterials == null ? Requirement.SURVIVAL : builderMaterials;
		debugBuilds = debugBuilds == null ? DebugGate.CHEATS : debugBuilds;
	}

	public SurvivalSettings withTerminalForKey(Requirement r) {
		return new SurvivalSettings(r, builderMaterials, debugBuilds, firstContactAdvancement);
	}

	public SurvivalSettings withBuilderMaterials(Requirement r) {
		return new SurvivalSettings(terminalForKey, r, debugBuilds, firstContactAdvancement);
	}

	public SurvivalSettings withDebugBuilds(DebugGate g) {
		return new SurvivalSettings(terminalForKey, builderMaterials, g, firstContactAdvancement);
	}

	/** When a requirement applies, by game mode. */
	public enum Requirement {
		/** Never. */
		NEVER,
		/** Only in survival and adventure ({@link PlayMode#pays()}). */
		SURVIVAL,
		/** In every mode. */
		ALWAYS;

		public boolean appliesTo(PlayMode mode) {
			return switch (this) {
				case NEVER -> false;
				case SURVIVAL -> mode.pays();
				case ALWAYS -> true;
			};
		}
	}

	/** Who may run free world-editing debug commands. */
	public enum DebugGate {
		/** Anyone (old behaviour: client commands need no op). */
		ANY,
		/** Players with cheats, or in creative. */
		CHEATS
	}
}
