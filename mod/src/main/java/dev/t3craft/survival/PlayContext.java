package dev.t3craft.survival;

/**
 * What the policy needs to know about the player asking. Plain data: build it with
 * {@link PlayContexts#of} from a {@code Player}, or by hand in tests.
 *
 * @param mode             current game mode
 * @param cheats           the player may run cheat commands ({@code /gamemode}, {@code /give}): op level ≥ 2,
 *                         or "Allow Cheats" / LAN cheats in singleplayer
 * @param integratedServer the world runs on this game's integrated server (singleplayer or hosted LAN)
 * @param terminalItem     the T3 Terminal item exists here (registered and the server knows it); when false
 *                         nothing may require holding one, or the player would be locked out
 * @param hasTerminal      a T3 Terminal is in the player's inventory
 */
public record PlayContext(PlayMode mode, boolean cheats, boolean integratedServer, boolean terminalItem, boolean hasTerminal) {
	public PlayContext {
		if (mode == null) {
			mode = PlayMode.SURVIVAL;
		}
	}

	public static PlayContext survival(boolean hasTerminal) {
		return new PlayContext(PlayMode.SURVIVAL, false, true, true, hasTerminal);
	}

	public static PlayContext creative() {
		return new PlayContext(PlayMode.CREATIVE, true, true, true, false);
	}

	public PlayContext withMode(PlayMode m) {
		return new PlayContext(m, cheats, integratedServer, terminalItem, hasTerminal);
	}

	public PlayContext withCheats(boolean c) {
		return new PlayContext(mode, c, integratedServer, terminalItem, hasTerminal);
	}
}
