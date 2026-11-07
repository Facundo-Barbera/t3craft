package dev.t3craft.client.chat;

import java.util.List;
import java.util.Map;

/**
 * Minimal integration point: where the unified thread list gets its rows. The default
 * ({@link ChatScreens#defaultDirectory()}) reads the NPC model of {@code T3CraftClient}, which is
 * the same filtered set the world shows. Another source (for example one with real activity
 * timestamps from the shell) can implement this. Called on the client thread.
 */
public interface ThreadDirectory {
	/** Every known thread of every environment, any order (the list sorts them). */
	List<ThreadRow> rows();

	/** Environment id → {label, status wire, detail}, in display order (filter choices). */
	Map<String, String[]> environments();
}
