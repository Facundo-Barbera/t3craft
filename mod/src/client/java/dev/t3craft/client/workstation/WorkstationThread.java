package dev.t3craft.client.workstation;

import dev.t3craft.client.npc.NpcInfo;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What a workstation needs to know about one thread. {@code key} is the NPC id
 * ({@code <envId>:<threadId>} in direct mode), shared with {@code NpcRegistry} and the behaviour
 * director so a station and its NPC refer to the same thread.
 *
 * @param updatedAtMs last activity of the thread (epoch ms) when the source knows it; null makes the
 *     {@link WorkstationCache} measure age from the last change it observed itself.
 * @param branch the thread's git branch (already clipped), or null to show no branch line
 */
public record WorkstationThread(
		String key,
		String envId,
		String threadId,
		String title,
		String groupId,
		int priority,
		StationStatus status,
		@Nullable String activity,
		@Nullable Long updatedAtMs,
		@Nullable String branch) {

	/** Without branch. */
	public WorkstationThread(String key, String envId, String threadId, String title, String groupId, int priority, StationStatus status,
			@Nullable String activity, @Nullable Long updatedAtMs) {
		this(key, envId, threadId, title, groupId, priority, status, activity, updatedAtMs, null);
	}

	public WorkstationThread {
		Objects.requireNonNull(key, "key");
		envId = envId == null ? "" : envId;
		threadId = threadId == null ? key : threadId;
		title = title == null ? "" : title;
		groupId = groupId == null ? envId : groupId;
		status = status == null ? StationStatus.IDLE : status;
	}

	public boolean archived() {
		return status == StationStatus.ARCHIVED;
	}

	/**
	 * Hash of everything the screen shows except the age: the {@link WorkstationCache} only
	 * re-renders when this (or the age label) changes.
	 */
	public int contentHash() {
		return Objects.hash(title, status, activity, branch);
	}

	/**
	 * From the registry's NPC. The id is split at the first {@code ':'} into env and thread
	 * ({@code Presentation.npcId}); an id without one (bridge mode) keeps the whole id as thread id
	 * and the env label as env.
	 */
	public static WorkstationThread fromNpc(NpcInfo n) {
		return fromNpc(n, true);
	}

	/** @param withBranch false hides the branch line (config {@code chips.branchWorkstation}). */
	public static WorkstationThread fromNpc(NpcInfo n, boolean withBranch) {
		String id = n.id();
		int colon = id.indexOf(':');
		String env = colon > 0 ? id.substring(0, colon) : n.envLabel();
		String thread = colon > 0 ? id.substring(colon + 1) : id;
		return new WorkstationThread(id, env, thread, n.title(), n.groupId(), n.priority(), StationStatus.of(n.state(), n.settled()),
				n.activity(), null, withBranch ? n.branch() : null);
	}
}
