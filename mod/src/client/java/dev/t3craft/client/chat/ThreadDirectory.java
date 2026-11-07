package dev.t3craft.client.chat;

import java.util.List;
import java.util.Map;

/**
 * Where the unified thread list gets its rows. The default ({@link ChatScreens#defaultDirectory()})
 * reads the direct-mode controller (every visible thread of every paired environment, with real
 * activity timestamps) or, in bridge mode, the NPC model. Another source can implement this.
 * Called on the client thread, once per tick while a list is shown: keep it cheap.
 */
public interface ThreadDirectory {
	/** Every known thread of every environment, any order (the list sorts them). */
	List<ThreadRow> rows();

	/** Environment id → info, in display order (filter chips). */
	Map<String, ThreadList.EnvInfo> environments();
}
