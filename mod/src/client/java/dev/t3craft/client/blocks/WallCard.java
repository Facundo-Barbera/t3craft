package dev.t3craft.client.blocks;

import dev.t3craft.client.chat.Attention;
import dev.t3craft.client.chat.EnvBadge;
import dev.t3craft.client.chat.ThreadOrder;
import dev.t3craft.client.chat.ThreadRow;
import java.util.Comparator;
import org.jspecify.annotations.Nullable;

/**
 * One thread as a card on the thread wall: what the card shows (title, environment badge, project,
 * attention colour, queued runs) and what orders it. Built from the chat's {@link ThreadRow}s (the
 * shell the client already keeps, {@code subscribeShell}; read only). Records compare by value, so
 * an unchanged roster gives an equal list and the wall does not rebuild. Pure.
 *
 * @param key    {@code env:threadId}, the NPC / chat key
 * @param queued queued runs of the thread ({@code queuePosition} rows), 0 when none or unknown
 */
public record WallCard(
		String key,
		String envId,
		String envLabel,
		String title,
		String project,
		Attention attention,
		boolean pinned,
		@Nullable Long activityMs,
		int queued,
		EnvBadge badge) {

	/** The chat's order (pinned, attention, most recent activity, title, key). */
	public static final Comparator<WallCard> ORDER = ThreadOrder.of(WallCard::pinned, WallCard::attention, WallCard::activityMs,
			c -> false, WallCard::title, WallCard::key);

	public WallCard {
		envId = envId == null ? "" : envId;
		envLabel = envLabel == null || envLabel.isBlank() ? envId : envLabel;
		title = title == null || title.isBlank() ? key : title;
		project = project == null ? "" : project;
		attention = attention == null ? Attention.IDLE : attention;
		queued = Math.max(0, queued);
	}

	public static WallCard of(ThreadRow r, EnvBadge badge, int queued) {
		return new WallCard(r.key(), r.env(), r.envLabel(), r.title(), r.project(), r.attention(), r.pinned(), r.activityMs(), queued, badge);
	}

	/** {@code env:threadId} → env ("" when there is no colon). */
	public String threadId() {
		int colon = key.indexOf(':');
		return colon > 0 ? key.substring(colon + 1) : key;
	}
}
