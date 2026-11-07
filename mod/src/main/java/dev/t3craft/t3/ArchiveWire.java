package dev.t3craft.t3;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Wire format of the two read-only archive RPCs (contract {@code orchestrationV2.ts} @ t3code-ref
 * 15fe2832), decoded tolerantly like {@link ThreadShell}: unknown fields are ignored, missing ones
 * give empty lists, malformed threads are skipped. Nothing here throws.
 *
 * <ul>
 *   <li>{@code orchestration.getOlderSettledThreads} {@code {before: {settledAt, threadId?},
 *   projectIds?, limit: 0..50}} → {@code {snapshotSequence, threads, counts: [{projectId, count}]}}:
 *   settled top-level threads below the cursor, newest settle first ({@code settledAt} desc, then id
 *   asc). {@code limit: 0} only fills {@code counts}.</li>
 *   <li>{@code orchestration.getArchivedShellSnapshot} {@code {}} → {@code {schemaVersion,
 *   snapshotSequence, projects, threads}}.</li>
 * </ul>
 */
public final class ArchiveWire {
	/** The server caps {@code limit} at 50. */
	public static final int MAX_PAGE = 50;

	private ArchiveWire() {
	}

	/**
	 * Where a page of older settled threads starts: below {@code settledAtMs}, then after
	 * {@code threadId} (same settle time). Without an id the cursor is strict.
	 */
	public record Cursor(long settledAtMs, @Nullable String threadId) {
		/** Everything settled before now. */
		public static Cursor before(long nowMs) {
			return new Cursor(nowMs, null);
		}

		/** The cursor that continues after the last thread of a page, or null if it has no settle time. */
		public static @Nullable Cursor after(ThreadShell last) {
			return last.settledAt() == null ? null : new Cursor(last.settledAt(), last.id());
		}

		JsonObject toJson() {
			JsonObject o = new JsonObject();
			o.addProperty("settledAt", Instant.ofEpochMilli(settledAtMs).toString());
			if (threadId != null && !threadId.isEmpty()) {
				o.addProperty("threadId", threadId);
			}
			return o;
		}
	}

	/** One page of older settled threads (order as sent) and, for {@code limit: 0}, the counts per project. */
	public record OlderSettledPage(long snapshotSequence, List<ThreadShell> threads, Map<String, Long> counts) {
		public static final OlderSettledPage EMPTY = new OlderSettledPage(-1, List.of(), Map.of());

		public OlderSettledPage {
			threads = List.copyOf(threads);
			counts = Map.copyOf(counts);
		}

		/** Sum of {@link #counts}. */
		public long total() {
			long n = 0;
			for (long c : counts.values()) {
				n += c;
			}
			return n;
		}
	}

	/** Archived threads and the projects they belong to (display names, see {@link ShellState.Project#displayName()}). */
	public record ArchivedSnapshot(long snapshotSequence, List<ThreadShell> threads, Map<String, String> projectTitles) {
		public static final ArchivedSnapshot EMPTY = new ArchivedSnapshot(-1, List.of(), Map.of());

		public ArchivedSnapshot {
			threads = List.copyOf(threads);
			projectTitles = Map.copyOf(projectTitles);
		}
	}

	/** Request payload of {@code getOlderSettledThreads}. {@code limit} is clamped to 0..{@link #MAX_PAGE}. */
	public static JsonObject olderSettledRequest(Cursor before, int limit, @Nullable Collection<String> projectIds) {
		JsonObject p = new JsonObject();
		p.add("before", before.toJson());
		if (projectIds != null && !projectIds.isEmpty()) {
			JsonArray ids = new JsonArray();
			projectIds.forEach(ids::add);
			p.add("projectIds", ids);
		}
		p.addProperty("limit", Math.max(0, Math.min(MAX_PAGE, limit)));
		return p;
	}

	public static OlderSettledPage decodeOlderSettled(@Nullable JsonElement value) {
		JsonObject o = value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
		if (o == null) {
			return OlderSettledPage.EMPTY;
		}
		Map<String, Long> counts = new LinkedHashMap<>();
		JsonArray cs = Json.arr(o, "counts");
		if (cs != null) {
			for (JsonElement e : cs) {
				if (!e.isJsonObject()) {
					continue;
				}
				String pid = Json.str(e.getAsJsonObject(), "projectId");
				long n = Json.num(e.getAsJsonObject(), "count", -1);
				if (pid != null && n >= 0) {
					counts.merge(pid, n, Long::sum);
				}
			}
		}
		return new OlderSettledPage(Json.num(o, "snapshotSequence", -1), threads(Json.arr(o, "threads")), counts);
	}

	public static ArchivedSnapshot decodeArchived(@Nullable JsonElement value) {
		JsonObject o = value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
		if (o == null) {
			return ArchivedSnapshot.EMPTY;
		}
		Map<String, String> titles = new LinkedHashMap<>();
		JsonArray ps = Json.arr(o, "projects");
		if (ps != null) {
			for (JsonElement e : ps) {
				ShellState.Project p = e.isJsonObject() ? ShellState.Project.fromJson(e.getAsJsonObject()) : null;
				String name = p == null ? null : p.displayName();
				if (name != null) {
					titles.put(p.id(), name);
				}
			}
		}
		return new ArchivedSnapshot(Json.num(o, "snapshotSequence", -1), threads(Json.arr(o, "threads")), titles);
	}

	private static List<ThreadShell> threads(@Nullable JsonArray ts) {
		List<ThreadShell> out = new ArrayList<>();
		if (ts != null) {
			for (JsonElement e : ts) {
				ThreadShell t = e.isJsonObject() ? ThreadShell.fromJson(e.getAsJsonObject()) : null;
				if (t != null) {
					out.add(t);
				}
			}
		}
		return out;
	}
}
