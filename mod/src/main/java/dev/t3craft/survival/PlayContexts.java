package dev.t3craft.survival;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;

/** Builds a {@link PlayContext} from a live player (client {@code LocalPlayer} or {@code ServerPlayer}). */
public final class PlayContexts {
	private PlayContexts() {
	}

	/**
	 * @param integratedServer whether this world runs on the game's own server. Server side: pass
	 *                         {@link #isIntegrated(Player)}; client side: {@code mc.hasSingleplayerServer()}.
	 */
	public static PlayContext of(Player player, boolean integratedServer) {
		Item terminal = PortableItems.terminal();
		boolean has = terminal != null && player.getInventory().contains(s -> s.is(terminal));
		return new PlayContext(mode(player.gameMode()), cheats(player), integratedServer,
				terminal != null && integratedServer, has);
	}

	/** Server side only: true when the player's level runs on an integrated (non-dedicated) server. */
	public static boolean isIntegrated(Player player) {
		return player.level() instanceof ServerLevel sl && !sl.getServer().isDedicatedServer();
	}

	public static boolean cheats(Player player) {
		return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	public static PlayMode mode(GameType type) {
		if (type == null) {
			return PlayMode.SURVIVAL;
		}
		return switch (type) {
			case CREATIVE -> PlayMode.CREATIVE;
			case ADVENTURE -> PlayMode.ADVENTURE;
			case SPECTATOR -> PlayMode.SPECTATOR;
			default -> PlayMode.SURVIVAL;
		};
	}
}
