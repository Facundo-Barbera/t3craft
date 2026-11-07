package dev.t3craft.survival;

/**
 * Default {@link SurvivalRules}: game-mode based, driven by {@link SurvivalSettings}.
 * <ul>
 *   <li>Terminal key: allowed unless the setting applies to this mode and no terminal is carried. Never
 *       denied where the item cannot exist ({@link PlayContext#terminalItem()} false: item disabled,
 *       or a server without T3Craft), so nobody gets locked out.</li>
 *   <li>Building: needs the integrated server (builders run there); spectators never; adventure only with
 *       cheats (adventure maps protect their blocks).</li>
 *   <li>Materials: from the inventory when the setting applies to this mode.</li>
 *   <li>Free debug edits: with {@code CHEATS}, only creative or players with cheats.</li>
 * </ul>
 */
public record GameModePolicy(SurvivalSettings settings) implements SurvivalRules {
	public GameModePolicy {
		settings = settings == null ? SurvivalSettings.DEFAULTS : settings;
	}

	@Override
	public Verdict openTerminal(PlayContext ctx) {
		if (!ctx.terminalItem() || ctx.hasTerminal() || !settings.terminalForKey().appliesTo(ctx.mode())) {
			return Verdict.OK;
		}
		return Verdict.deny("needs_terminal");
	}

	@Override
	public Verdict buildInWorld(PlayContext ctx) {
		if (!ctx.integratedServer()) {
			return Verdict.deny("needs_singleplayer");
		}
		return switch (ctx.mode()) {
			case SPECTATOR -> Verdict.deny("spectator");
			case ADVENTURE -> ctx.cheats() ? Verdict.OK : Verdict.deny("adventure");
			case SURVIVAL, CREATIVE -> Verdict.OK;
		};
	}

	@Override
	public MaterialSource materials(PlayContext ctx) {
		return settings.builderMaterials().appliesTo(ctx.mode()) ? MaterialSource.PLAYER_INVENTORY : MaterialSource.FREE;
	}

	@Override
	public Verdict debugWorldEdit(PlayContext ctx) {
		Verdict where = buildInWorld(ctx.withCheats(true)); // same physical limits, minus the adventure rule
		if (!where.allowed()) {
			return where;
		}
		return switch (settings.debugBuilds()) {
			case ANY -> Verdict.OK;
			case CHEATS -> ctx.cheats() || ctx.mode() == PlayMode.CREATIVE ? Verdict.OK : Verdict.deny("needs_cheats");
		};
	}

	@Override
	public boolean firstContactAdvancement() {
		return settings.firstContactAdvancement();
	}
}
