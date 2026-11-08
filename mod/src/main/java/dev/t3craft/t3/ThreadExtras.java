package dev.t3craft.t3;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

/**
 * Shell fields the T3 sidebar rows show besides state and title (feat/t3-sidebar-rows), decoded
 * tolerantly from {@code OrchestrationV2ThreadShell}: the snooze and read watermarks and the linked
 * pull request with its diff size. Every field is null (or {@link #NO_COUNT}) when absent, so older
 * servers simply show plainer rows.
 *
 * <ul>
 *   <li>{@code snoozedUntil}: the thread sits in the sidebar's Snoozed section until then.</li>
 *   <li>{@code lastVisitedAt}: a run completed after it means "unread" (T3's unseen completion).</li>
 *   <li>PR: {@code linkedPullRequest} ({@code repository}, {@code number}, {@code url}) first, then the
 *       last {@code pullRequests[]} link that is not {@code stack-dismissed}, then
 *       {@code branchPullRequest}. Its {@code snapshot} gives {@code state}/{@code isDraft} and the
 *       {@code additions}/{@code deletions} shown as +N/−N.</li>
 * </ul>
 */
public record ThreadExtras(
		@Nullable Long snoozedUntil,
		@Nullable Long lastVisitedAt,
		@Nullable String prRepository,
		int prNumber,
		@Nullable String prUrl,
		@Nullable String prState,
		int additions,
		int deletions) {

	public static final int NO_COUNT = -1;
	public static final ThreadExtras EMPTY = new ThreadExtras(null, null, null, 0, null, null, NO_COUNT, NO_COUNT);

	public boolean hasPr() {
		return prNumber > 0;
	}

	public boolean hasDiff() {
		return additions >= 0 || deletions >= 0;
	}

	public static ThreadExtras fromJson(@Nullable JsonObject o) {
		if (o == null) {
			return EMPTY;
		}
		Long snoozed = Json.time(o, "snoozedUntil");
		Long visited = Json.time(o, "lastVisitedAt");
		String repo = null;
		int number = 0;
		String url = null;
		String state = null;
		int add = NO_COUNT;
		int del = NO_COUNT;
		JsonObject linked = Json.obj(o, "linkedPullRequest");
		JsonObject link = null;
		JsonArray prs = Json.arr(o, "pullRequests");
		if (prs != null) {
			for (JsonElement e : prs) {
				if (e instanceof JsonObject p && !"stack-dismissed".equals(Json.str(p, "source")) && intOf(p, "number") > 0) {
					link = p; // the last one: the most recently linked
				}
			}
		}
		if (linked != null && intOf(linked, "number") > 0) {
			repo = Json.str(linked, "repository");
			number = intOf(linked, "number");
			url = Json.str(linked, "url");
			// the same PR in pullRequests[] carries the snapshot
			if (prs != null) {
				for (JsonElement e : prs) {
					if (e instanceof JsonObject p && intOf(p, "number") == number && sameRepo(repo, Json.str(p, "repository"))) {
						link = p;
					}
				}
			}
			if (link != null && intOf(link, "number") != number) {
				link = null;
			}
		} else if (link != null) {
			repo = Json.str(link, "repository");
			number = intOf(link, "number");
			url = Json.str(link, "url");
		} else {
			JsonObject branch = Json.obj(o, "branchPullRequest");
			if (branch != null && intOf(branch, "number") > 0) {
				repo = Json.str(branch, "repository");
				number = intOf(branch, "number");
				url = Json.str(branch, "url");
			}
		}
		JsonObject snap = link == null ? null : Json.obj(link, "snapshot");
		if (snap != null) {
			state = Json.str(snap, "state");
			if (Json.bool(snap, "isDraft") && (state == null || "open".equalsIgnoreCase(state))) {
				state = "draft";
			}
			add = snap.has("additions") ? (int) Json.num(snap, "additions", NO_COUNT) : NO_COUNT;
			del = snap.has("deletions") ? (int) Json.num(snap, "deletions", NO_COUNT) : NO_COUNT;
		}
		if (snoozed == null && visited == null && number == 0) {
			return EMPTY;
		}
		return new ThreadExtras(snoozed, visited, repo, number, url, state == null ? null : state.toLowerCase(java.util.Locale.ROOT), add, del);
	}

	private static boolean sameRepo(@Nullable String a, @Nullable String b) {
		return a == null || b == null || a.equalsIgnoreCase(b);
	}

	private static int intOf(JsonObject o, String key) {
		long n = Json.num(o, key, 0);
		return n > 0 && n < Integer.MAX_VALUE ? (int) n : 0;
	}
}
