package dev.t3craft.t3;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Thread search over T3's read-only {@code orchestration.searchThreads} (scope
 * {@code orchestration:read}), kept pure so it can be unit-tested without sockets:
 * <ol>
 *   <li>{@link #decode}: the RPC's success value → {@link Decoded} (tolerant: missing fields get
 *   defaults, entries without a thread id are skipped, older servers without {@code quality} or
 *   {@code threads} still work).</li>
 *   <li>{@link #enrich}: one environment's matches + its shell view → displayable {@link Hit}s, plus
 *   local title matches from the shell (the shell already has every title, so a title hit needs no
 *   round trip).</li>
 *   <li>{@link #merge}: hits of every environment in a stable order — relevance, then latest
 *   activity, then environment, title and id.</li>
 * </ol>
 * Wire contract: bridge/vendor/t3-contracts/src/threadSearch.ts and orchestrationV2.ts
 * ({@code OrchestrationV2SearchThreadsResult}).
 */
public final class ThreadSearch {
	private ThreadSearch() {
	}

	public static final int DEFAULT_LIMIT = 20;
	public static final long DEFAULT_TIMEOUT_MS = 8_000;
	/** Relevance of a match whose server omits {@code quality} (older T3). */
	static final double UNKNOWN_QUALITY = 0.5;
	/** Relevance of a local title match: above a plain whole-word message hit (T3 gives ≤ 1.25). */
	static final double TITLE_QUALITY = 1.3;
	/** A title that starts with the query ranks above one that only contains it. */
	static final double TITLE_PREFIX_BONUS = 0.1;

	/** One server match, as decoded. Times are epoch ms or null. */
	public record Match(
			String threadId,
			String projectId,
			String source,
			String snippet,
			@Nullable Long messageCreatedAt,
			@Nullable Double quality,
			@Nullable Integer hits,
			@Nullable String messageId) {
	}

	/** The success value: matches in server order (one per thread) and any thread rows sent along. */
	public record Decoded(List<Match> matches, List<ThreadShell> threads) {
		public static final Decoded EMPTY = new Decoded(List.of(), List.of());
	}

	/**
	 * A result ready to show. {@code state} is null when the thread is not in the shell (an older
	 * settled thread the server found); {@code snippet} is null for a title-only hit.
	 */
	public record Hit(
			String env,
			String envLabel,
			String threadId,
			String title,
			String project,
			@Nullable ThreadState state,
			@Nullable String snippet,
			double relevance,
			long activityMs,
			int hits) {
	}

	/** An environment that could not be searched; {@code message} is already sanitized. */
	public record EnvError(String env, String envLabel, String message) {
	}

	/**
	 * Everything one search produced. {@code searched} counts the connected environments asked;
	 * {@code paired} the environments known at all, so callers can tell "nothing paired" from
	 * "nothing connected" from "no match".
	 */
	public record Outcome(String query, List<Hit> hits, int searched, int paired, List<EnvError> errors) {
		public boolean noEnvironmentConnected() {
			return searched == 0;
		}
	}

	/** A failed search: the message is sanitized and safe for the chat. */
	public static final class SearchException extends RuntimeException {
		public SearchException(String message) {
			super(T3Connection.sanitize(message), null, false, false);
		}
	}

	// ---- decode -------------------------------------------------------------------------------

	public static Decoded decode(@Nullable JsonElement value) {
		if (value == null || !value.isJsonObject()) {
			return Decoded.EMPTY;
		}
		JsonObject o = value.getAsJsonObject();
		Map<String, Match> byThread = new LinkedHashMap<>();
		JsonArray ms = Json.arr(o, "matches");
		if (ms != null) {
			for (JsonElement e : ms) {
				Match m = e.isJsonObject() ? match(e.getAsJsonObject()) : null;
				if (m == null) {
					continue;
				}
				Match prev = byThread.get(m.threadId());
				if (prev == null || quality(m) > quality(prev)) {
					byThread.put(m.threadId(), m);
				}
			}
		}
		List<ThreadShell> threads = new ArrayList<>();
		JsonArray ts = Json.arr(o, "threads");
		if (ts != null) {
			for (JsonElement e : ts) {
				ThreadShell t = e.isJsonObject() ? ThreadShell.fromJson(e.getAsJsonObject()) : null;
				if (t != null) {
					threads.add(t);
				}
			}
		}
		return new Decoded(List.copyOf(byThread.values()), List.copyOf(threads));
	}

	static @Nullable Match match(JsonObject o) {
		String id = Json.str(o, "threadId");
		if (id == null || id.isBlank()) {
			return null;
		}
		String snippet = Json.str(o, "snippet");
		String source = Json.str(o, "source");
		return new Match(
				id,
				orEmpty(Json.str(o, "projectId")),
				source == null ? "" : source,
				snippet == null ? "" : snippet.strip(),
				Json.time(o, "messageCreatedAt"),
				finite(o.get("quality")),
				integer(o.get("hits")),
				Json.str(o, "messageId"));
	}

	private static @Nullable Double finite(@Nullable JsonElement e) {
		if (e instanceof JsonPrimitive p && p.isNumber()) {
			double d = p.getAsDouble();
			return Double.isFinite(d) ? d : null;
		}
		return null;
	}

	private static @Nullable Integer integer(@Nullable JsonElement e) {
		if (e instanceof JsonPrimitive p && p.isNumber()) {
			double d = p.getAsDouble();
			return Double.isFinite(d) ? (int) Math.max(0, Math.min(Integer.MAX_VALUE, d)) : null;
		}
		return null;
	}

	private static double quality(Match m) {
		return m.quality() == null ? UNKNOWN_QUALITY : m.quality();
	}

	// ---- enrich -------------------------------------------------------------------------------

	/**
	 * One environment's hits: each server match gets its title, project and state from the thread
	 * rows the server sent, else from the shell view; then every shell thread whose title contains
	 * the query (case-insensitive) is added or boosted. Deleted threads are dropped.
	 */
	public static List<Hit> enrich(String env, String envLabel, String query, Decoded d, ShellState.@Nullable View view) {
		Map<String, ThreadShell> shells = new HashMap<>();
		if (view != null) {
			view.threads().forEach(t -> shells.put(t.id(), t));
		}
		d.threads().forEach(t -> shells.put(t.id(), t));
		String needle = RpcFrames.searchQuery(query).toLowerCase(Locale.ROOT);

		Map<String, Hit> out = new LinkedHashMap<>();
		for (Match m : d.matches()) {
			ThreadShell t = shells.get(m.threadId());
			if (t != null && t.deletedAt() != null) {
				continue;
			}
			String projectId = t != null && !t.projectId().isEmpty() ? t.projectId() : m.projectId();
			long activity = Math.max(t == null ? 0 : Visibility.lastActivity(t), m.messageCreatedAt() == null ? 0 : m.messageCreatedAt());
			double relevance = Math.max(quality(m), t == null ? 0 : titleRelevance(t.title(), needle));
			out.put(m.threadId(), new Hit(env, envLabel, m.threadId(), title(t, m.threadId()), project(view, projectId),
					t == null ? null : ThreadState.of(t), m.snippet().isEmpty() ? null : m.snippet(), relevance, activity,
					m.hits() == null ? 1 : m.hits()));
		}
		if (!needle.isEmpty() && view != null) {
			for (ThreadShell t : view.threads()) {
				double r = titleRelevance(t.title(), needle);
				if (r <= 0 || out.containsKey(t.id()) || t.deletedAt() != null) {
					continue;
				}
				out.put(t.id(), new Hit(env, envLabel, t.id(), title(t, t.id()), project(view, t.projectId()), ThreadState.of(t),
						null, r, Visibility.lastActivity(t), 0));
			}
		}
		return List.copyOf(out.values());
	}

	static double titleRelevance(@Nullable String title, String needleLower) {
		if (title == null || needleLower.isEmpty()) {
			return 0;
		}
		String t = title.toLowerCase(Locale.ROOT);
		if (t.startsWith(needleLower)) {
			return TITLE_QUALITY + TITLE_PREFIX_BONUS;
		}
		return t.contains(needleLower) ? TITLE_QUALITY : 0;
	}

	private static String title(@Nullable ThreadShell t, String threadId) {
		if (t != null && !t.title().isBlank()) {
			return t.title().strip();
		}
		return "thread " + (threadId.length() > 8 ? threadId.substring(0, 8) : threadId);
	}

	private static String project(ShellState.@Nullable View view, String projectId) {
		if (view == null || projectId.isEmpty()) {
			return "";
		}
		String p = view.projectTitle(projectId);
		return p == null ? "" : p;
	}

	// ---- merge --------------------------------------------------------------------------------

	/** Most relevant first, then most recent activity, then environment label, title and id. */
	public static final Comparator<Hit> ORDER = Comparator.comparingDouble(Hit::relevance).reversed()
			.thenComparing(Comparator.comparingLong(Hit::activityMs).reversed())
			.thenComparing(Hit::envLabel)
			.thenComparing(Hit::env)
			.thenComparing(Hit::title)
			.thenComparing(Hit::threadId);

	/** Hits of every environment in {@link #ORDER}, at most {@code limit}. Pure: inputs untouched. */
	public static List<Hit> merge(List<List<Hit>> perEnvironment, int limit) {
		List<Hit> all = new ArrayList<>();
		perEnvironment.forEach(all::addAll);
		all.sort(ORDER);
		return List.copyOf(all.subList(0, Math.min(Math.max(0, limit), all.size())));
	}

	private static String orEmpty(@Nullable String s) {
		return s == null ? "" : s;
	}
}
