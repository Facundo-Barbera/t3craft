package dev.t3craft.client.archive;

import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.client.portable.PortableLinks;
import dev.t3craft.client.screen.ThreadChatScreen;
import java.time.ZoneId;
import net.minecraft.client.Minecraft;

/**
 * Opens a closed thread read-only when the player uses its slot or a catalog row. Called on the
 * client thread.
 *
 * <p>Default ({@link #DEFAULT}): the chat/history screen that exists. With feat/chat's
 * {@code ChatScreens} on the classpath ({@link PortableLinks#hasChat()}), that one, which reads
 * history over the shared connection and cannot send without operate. Otherwise main's
 * {@link ThreadChatScreen} with a synthetic, settled {@link NpcInfo} (title, environment · project,
 * close date): the registry does not hold closed threads, so the registry-based openers would find
 * nothing. Replace it with {@code ArchiveClient.get().setOpener(...)}.
 */
@FunctionalInterface
public interface ArchiveOpener {
	void open(ArchiveEntry entry);

	ArchiveOpener DEFAULT = entry -> {
		if (PortableLinks.hasChat()) {
			PortableLinks.openChat(entry.env(), entry.threadId());
			return;
		}
		Minecraft.getInstance().gui.setScreen(new ThreadChatScreen(npc(entry)));
	};

	/** The read-only stand-in the placeholder screen shows. */
	static NpcInfo npc(ArchiveEntry e) {
		String when = ArchiveLabel.date(e.closedAtMs(), ZoneId.systemDefault());
		String label = e.kind().glyph + " " + (e.kind() == ArchiveEntry.Kind.ARCHIVED ? "ARCHIVED" : "SETTLED") + " · " + when;
		return new NpcInfo(e.key(), e.title(), label, e.envLabel(), e.envLabel() + " · " + e.projectLabel(), ArchiveLabel.envColor(e.env()),
				"read-only (archive)", 99, true, "idle", e.projectKey(), e.projectLabel(), e.env() + "/idle", null);
	}
}
