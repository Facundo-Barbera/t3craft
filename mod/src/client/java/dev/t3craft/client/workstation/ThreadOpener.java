package dev.t3craft.client.workstation;

/**
 * Opens the conversation of one thread when the player uses its workstation.
 *
 * <p><b>Integration point.</b> The default ({@link ChatScreenOpener}) opens the chat browser
 * ({@code ChatScreens}), the same screen an NPC opens. Replace it with
 * {@code WorkstationClient.get().setThreadOpener((env, thread) -> …)}; {@code null} restores the
 * default. Called on the client thread.
 */
@FunctionalInterface
public interface ThreadOpener {
	void open(String envId, String threadId);
}
