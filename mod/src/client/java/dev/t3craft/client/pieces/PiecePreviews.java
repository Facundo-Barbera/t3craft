package dev.t3craft.client.pieces;

import net.minecraft.client.Minecraft;

/** Holds the active {@link PiecePreview}: {@link CommandPiecePreview} until something installs its own. */
public final class PiecePreviews {
	private static volatile PiecePreview current = new CommandPiecePreview();

	private PiecePreviews() {
	}

	public static PiecePreview current() {
		return current;
	}

	/** Replaces the hook (the in-world preview calls this from its client initializer). */
	public static void install(PiecePreview preview) {
		current = preview;
	}

	/** The default: runs the preview command as the player. */
	static final class CommandPiecePreview implements PiecePreview {
		@Override
		public Result request(PieceRequest r) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) {
				return Result.unavailable("Join a world first (title screen > T3Craft HQ), then ask again. Copy command keeps the request.");
			}
			String cmd = r.command();
			try {
				mc.player.connection.sendCommand(cmd);
			} catch (RuntimeException e) {
				return Result.failed("Could not send /" + cmd + ": " + e.getMessage());
			}
			return Result.sent("Sent /" + cmd + ". The piece stage lives in the T3Craft HQ world; the chat says what was built.");
		}
	}
}
