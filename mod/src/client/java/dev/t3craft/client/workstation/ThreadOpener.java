package dev.t3craft.client.workstation;

/**
 * Opens the conversation of one thread when the player uses its workstation.
 *
 * <p><b>Integration point (feat/chat).</b> The default ({@link ChatScreenOpener}) opens the chat
 * screen that exists on main today ({@code ThreadChatScreen}). When the unified list + conversation
 * UI of feat/chat lands, plug it in with {@code WorkstationClient.get().setThreadOpener((env, thread)
 * -> …)}; {@code null} restores the default. Called on the client thread.
 */
@FunctionalInterface
public interface ThreadOpener {
	void open(String envId, String threadId);
}
