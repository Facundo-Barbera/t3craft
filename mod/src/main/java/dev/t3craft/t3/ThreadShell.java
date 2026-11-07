package dev.t3craft.t3;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

/**
 * The fields of T3's {@code OrchestrationV2ThreadShell} that T3Craft reads, decoded tolerantly
 * from the JSON wire format (vendored contract: bridge/vendor/t3-contracts, orchestrationV2.ts).
 * Timestamps are epoch millis or null. {@code parentThreadId} (from {@code lineage}) is the parent
 * thread of a subagent or fork, null for top-level threads; it is the last component so other
 * branches adding fields do not collide. {@code pinnedAt} (fix/chat-direct-connect) is when the
 * thread was pinned in T3, null when it is not pinned.
 */
public record ThreadShell(
		String id,
		String projectId,
		String title,
		String status,
		@Nullable String activityRunStatus,
		@Nullable PendingRequest pendingRuntimeRequest,
		boolean hasActionableProposedPlan,
		long pendingGuestMessageCount,
		int pendingBackgroundTasks,
		@Nullable String lastError,
		@Nullable String latestMessageText,
		@Nullable Long latestMessageAt,
		@Nullable String activeRunId,
		@Nullable String relationshipToParent,
		boolean sideChat,
		@Nullable Long createdAt,
		@Nullable Long latestUserMessageAt,
		@Nullable Long latestRunRequestedAt,
		@Nullable Long latestRunStartedAt,
		@Nullable Long latestRunCompletedAt,
		@Nullable Long activityRunStartedAt,
		@Nullable Long archivedAt,
		@Nullable Long deletedAt,
		@Nullable Long settledAt,
		@Nullable String settledOverride,
		ThreadMeta meta,
		@Nullable String parentThreadId,
		@Nullable Long pinnedAt) {

	public ThreadShell {
		meta = meta == null ? ThreadMeta.EMPTY : meta;
	}

	/** Shape before {@code pinnedAt}, kept so existing callers still compile. */
	public ThreadShell(String id, String projectId, String title, String status, @Nullable String activityRunStatus,
			@Nullable PendingRequest pendingRuntimeRequest, boolean hasActionableProposedPlan, long pendingGuestMessageCount,
			int pendingBackgroundTasks, @Nullable String lastError, @Nullable String latestMessageText, @Nullable Long latestMessageAt,
			@Nullable String activeRunId, @Nullable String relationshipToParent, boolean sideChat, @Nullable Long createdAt,
			@Nullable Long latestUserMessageAt, @Nullable Long latestRunRequestedAt, @Nullable Long latestRunStartedAt,
			@Nullable Long latestRunCompletedAt, @Nullable Long activityRunStartedAt, @Nullable Long archivedAt, @Nullable Long deletedAt,
			@Nullable Long settledAt, @Nullable String settledOverride, ThreadMeta meta, @Nullable String parentThreadId) {
		this(id, projectId, title, status, activityRunStatus, pendingRuntimeRequest, hasActionableProposedPlan, pendingGuestMessageCount,
				pendingBackgroundTasks, lastError, latestMessageText, latestMessageAt, activeRunId, relationshipToParent, sideChat, createdAt,
				latestUserMessageAt, latestRunRequestedAt, latestRunStartedAt, latestRunCompletedAt, activityRunStartedAt, archivedAt,
				deletedAt, settledAt, settledOverride, meta, parentThreadId, null);
	}

	/** Pinned in T3 (shows first in every thread list). */
	public boolean pinned() {
		return pinnedAt != null;
	}

	/**
	 * {@code pinnedAt} decoded tolerantly: an ISO time or epoch number; older or other servers may
	 * send a boolean {@code pinned} / {@code isPinned} instead (true counts as pinned at 0).
	 */
	static @Nullable Long pinnedAt(JsonObject o) {
		Long at = Json.time(o, "pinnedAt");
		if (at != null) {
			return at;
		}
		com.google.gson.JsonElement e = o.get("pinnedAt");
		if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
			return e.getAsLong();
		}
		if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() && !e.getAsString().isBlank()) {
			return 0L; // a time in a shape we do not parse still means pinned
		}
		return Json.bool(o, "pinned") || Json.bool(o, "isPinned") ? 0L : null;
	}

	/** Pre-{@link ThreadMeta} shape, kept so existing callers still compile. */
	public ThreadShell(String id, String projectId, String title, String status, @Nullable String activityRunStatus,
			@Nullable PendingRequest pendingRuntimeRequest, boolean hasActionableProposedPlan, long pendingGuestMessageCount,
			int pendingBackgroundTasks, @Nullable String lastError, @Nullable String latestMessageText, @Nullable Long latestMessageAt,
			@Nullable String activeRunId, @Nullable String relationshipToParent, boolean sideChat, @Nullable Long createdAt,
			@Nullable Long latestUserMessageAt, @Nullable Long latestRunRequestedAt, @Nullable Long latestRunStartedAt,
			@Nullable Long latestRunCompletedAt, @Nullable Long activityRunStartedAt, @Nullable Long archivedAt, @Nullable Long deletedAt,
			@Nullable Long settledAt, @Nullable String settledOverride) {
		this(id, projectId, title, status, activityRunStatus, pendingRuntimeRequest, hasActionableProposedPlan, pendingGuestMessageCount,
				pendingBackgroundTasks, lastError, latestMessageText, latestMessageAt, activeRunId, relationshipToParent, sideChat, createdAt,
				latestUserMessageAt, latestRunRequestedAt, latestRunStartedAt, latestRunCompletedAt, activityRunStartedAt, archivedAt,
				deletedAt, settledAt, settledOverride, ThreadMeta.EMPTY, null, null);
	}

	/** Shape before {@code parentThreadId} (feat/npc-helpers), kept so existing callers still compile. */
	public ThreadShell(String id, String projectId, String title, String status, @Nullable String activityRunStatus,
			@Nullable PendingRequest pendingRuntimeRequest, boolean hasActionableProposedPlan, long pendingGuestMessageCount,
			int pendingBackgroundTasks, @Nullable String lastError, @Nullable String latestMessageText, @Nullable Long latestMessageAt,
			@Nullable String activeRunId, @Nullable String relationshipToParent, boolean sideChat, @Nullable Long createdAt,
			@Nullable Long latestUserMessageAt, @Nullable Long latestRunRequestedAt, @Nullable Long latestRunStartedAt,
			@Nullable Long latestRunCompletedAt, @Nullable Long activityRunStartedAt, @Nullable Long archivedAt, @Nullable Long deletedAt,
			@Nullable Long settledAt, @Nullable String settledOverride, ThreadMeta meta) {
		this(id, projectId, title, status, activityRunStatus, pendingRuntimeRequest, hasActionableProposedPlan, pendingGuestMessageCount,
				pendingBackgroundTasks, lastError, latestMessageText, latestMessageAt, activeRunId, relationshipToParent, sideChat, createdAt,
				latestUserMessageAt, latestRunRequestedAt, latestRunStartedAt, latestRunCompletedAt, activityRunStartedAt, archivedAt,
				deletedAt, settledAt, settledOverride, meta, null, null);
	}

	/** {@code pendingRuntimeRequest}: {@code blocking} is null when an older server omits it. */
	public record PendingRequest(String id, String kind, @Nullable Boolean blocking) {
	}

	/** Returns null when the object lacks an id (not a thread shell). */
	public static @Nullable ThreadShell fromJson(@Nullable JsonObject o) {
		String id = Json.str(o, "id");
		if (o == null || id == null || id.isEmpty()) {
			return null;
		}
		JsonObject pending = Json.obj(o, "pendingRuntimeRequest");
		JsonObject latest = Json.obj(o, "latestVisibleMessage");
		JsonObject lineage = Json.obj(o, "lineage");
		JsonArray bg = Json.arr(o, "pendingBackgroundTasks");
		String status = Json.str(o, "status");
		return new ThreadShell(
				id,
				orEmpty(Json.str(o, "projectId")),
				orEmpty(Json.str(o, "title")),
				status == null ? "idle" : status,
				Json.str(o, "activityRunStatus"),
				pending == null ? null : new PendingRequest(orEmpty(Json.str(pending, "id")), orEmpty(Json.str(pending, "kind")), Json.optBool(pending, "blocking")),
				Json.bool(o, "hasActionableProposedPlan"),
				Json.num(o, "pendingGuestMessageCount", 0),
				bg == null ? 0 : bg.size(),
				Json.str(o, "lastError"),
				Json.str(latest, "text"),
				Json.time(latest, "updatedAt"),
				Json.str(o, "activeRunId"),
				Json.str(lineage, "relationshipToParent"),
				Json.bool(lineage, "sideChat"),
				Json.time(o, "createdAt"),
				Json.time(o, "latestUserMessageAt"),
				Json.time(o, "latestRunRequestedAt"),
				Json.time(o, "latestRunStartedAt"),
				Json.time(o, "latestRunCompletedAt"),
				Json.time(o, "activityRunStartedAt"),
				Json.time(o, "archivedAt"),
				Json.time(o, "deletedAt"),
				Json.time(o, "settledAt"),
				Json.str(o, "settledOverride"),
				ThreadMeta.fromJson(o),
				// lineage.parentThreadId (feat/npc-helpers); some servers also put it at the top level.
				Json.str(lineage, "parentThreadId") != null ? Json.str(lineage, "parentThreadId") : Json.str(o, "parentThreadId"),
				pinnedAt(o));
	}

	private static String orEmpty(@Nullable String s) {
		return s == null ? "" : s;
	}
}
