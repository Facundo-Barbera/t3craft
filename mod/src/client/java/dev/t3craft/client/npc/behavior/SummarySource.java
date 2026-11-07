package dev.t3craft.client.npc.behavior;

import dev.t3craft.client.npc.NpcInfo;
import org.jspecify.annotations.Nullable;

/**
 * The text an NPC says in its speech bubble: the thread's latest summary. The default uses
 * {@link NpcInfo#activity()} (latest assistant message, pending request or last error, as resolved
 * by the bridge / {@code Presentation}).
 *
 * <p><b>Integration point.</b> A richer source (e.g. a turn summary from the chat feature) can be
 * plugged in with {@code NpcRegistry.behavior().setSummarySource(src)}. Called every tick per NPC
 * on the client thread: return a cached string.
 */
@FunctionalInterface
public interface SummarySource {
	SummarySource ACTIVITY = (id, info) -> info.activity();

	@Nullable String summary(String npcId, NpcInfo info);
}
