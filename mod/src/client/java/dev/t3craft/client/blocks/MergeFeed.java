package dev.t3craft.client.blocks;

import dev.t3craft.blocks.Binding;
import dev.t3craft.blocks.MergeState;
import dev.t3craft.client.chat.ThreadRow;
import dev.t3craft.client.workstation.WorkstationThread;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What the merge stations show: the pull requests of the threads the client already knows
 * ({@link ThreadRow#details()}, read from T3's shell: linked PR, state, draft flag, diff size), grouped by
 * what a station can be bound to ({@code thread:<key>}, {@code project:<groupId>}, {@code env:<envId>}).
 * Pure and deterministic (input order never matters); no subscription of its own.
 *
 * <p>A station's {@link MergeState} is the best state among its threads (open &gt; draft &gt; merged &gt; none);
 * the number it shows and the list it opens are the <b>pending</b> ones: open and draft PRs, open first.
 */
public final class MergeFeed {
	/** {@code owner/repo} as T3 spells it (used to build the PR link when the shell gives none). */
	private static final Pattern REPOSITORY = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");

	private MergeFeed() {
	}

	/** One thread with a (not closed) linked pull request. */
	public record Entry(String key, String envId, @Nullable String threadId, String title, String groupId, MergeState state, int number,
			@Nullable String repository, @Nullable String url, int additions, int deletions) {
		/** The pull request's web address, or null when neither the link nor the repository is known. */
		public @Nullable String link() {
			return MergeFeed.link(url, repository, number); // qualified: the record's own link() would hide the outer overload
		}

		public boolean pending() {
			return state.pending();
		}

		/** "#123" or "owner/repo#123". */
		public String label() {
			return (repository == null || repository.isBlank() ? "" : repository) + "#" + number;
		}
	}

	/** What one station shows: its state and its pending PRs (open first). */
	public record Summary(MergeState state, List<Entry> pending) {
		public static final Summary EMPTY = new Summary(MergeState.NONE, List.of());

		public int count() {
			return pending.size();
		}
	}

	/**
	 * The entries of {@code rows} that have a linked PR which is not closed. {@code threads} (the NPC model) gives each
	 * thread's project; a row without a known thread has no project (an {@code env:} or {@code thread:} binding still finds it).
	 */
	public static List<Entry> collect(Collection<WorkstationThread> threads, Collection<ThreadRow> rows) {
		Map<String, String> groups = new HashMap<>();
		for (WorkstationThread t : threads) {
			groups.put(t.key(), t.groupId());
		}
		List<Entry> out = new ArrayList<>();
		for (ThreadRow r : rows) {
			var d = r.details();
			MergeState s = MergeState.of(d.prState(), d.prNumber());
			if (s == MergeState.NONE) {
				continue;
			}
			out.add(new Entry(r.key(), r.env(), r.threadId(), r.title(), groups.getOrDefault(r.key(), ""), s, d.prNumber(),
					d.prRepository(), d.prUrl(), d.additions(), d.deletions()));
		}
		out.sort(ORDER);
		return List.copyOf(out);
	}

	/** Open before draft before merged, then by title and key (stable for the renderer's cache and the list). */
	static final Comparator<Entry> ORDER = Comparator.comparingInt((Entry e) -> -rank(e.state()))
			.thenComparing(Entry::title).thenComparing(Entry::key);

	private static int rank(MergeState s) {
		return switch (s) {
			case OPEN -> 3;
			case DRAFT -> 2;
			case MERGED -> 1;
			case NONE -> 0;
		};
	}

	/** True when {@code e} belongs to a station bound to {@code b}. */
	public static boolean matches(Binding b, Entry e) {
		return switch (b.kind()) {
			case THREAD -> b.value().equals(e.key());
			case PROJECT -> !e.groupId().isEmpty() && b.value().equals(e.groupId());
			case ENV -> b.value().equals(e.envId());
			default -> false;
		};
	}

	/** The summary of a station bound to {@code b} over {@code entries} (unbound or unknown: {@link Summary#EMPTY}). */
	public static Summary summarize(Binding b, Collection<Entry> entries) {
		MergeState best = MergeState.NONE;
		List<Entry> pending = new ArrayList<>();
		for (Entry e : entries) {
			if (!matches(b, e)) {
				continue;
			}
			best = MergeState.best(best, e.state());
			if (e.pending()) {
				pending.add(e);
			}
		}
		if (best == MergeState.NONE) {
			return Summary.EMPTY;
		}
		pending.sort(ORDER);
		return new Summary(best, List.copyOf(pending));
	}

	/**
	 * Binding wire string → state for every thread, project and environment that has a PR: what
	 * {@code MergeStationApplier} writes into the stations (bindings not in the map are NONE).
	 */
	public static Map<String, MergeState> wanted(Collection<Entry> entries) {
		Map<String, MergeState> out = new LinkedHashMap<>();
		for (Entry e : entries) {
			out.merge(Binding.thread(e.key()).wire(), e.state(), MergeState::best);
			if (!e.groupId().isEmpty()) {
				out.merge(Binding.project(e.groupId()).wire(), e.state(), MergeState::best);
			}
			if (!e.envId().isEmpty()) {
				out.merge(Binding.env(e.envId()).wire(), e.state(), MergeState::best);
			}
		}
		return out;
	}

	/** The link T3 gave, else {@code https://github.com/<repository>/pull/<number>}, else null. */
	static @Nullable String link(@Nullable String url, @Nullable String repository, int number) {
		if (url != null && !url.isBlank()) {
			return url.trim();
		}
		if (number > 0 && repository != null && REPOSITORY.matcher(repository).matches()) {
			return "https://github.com/" + repository + "/pull/" + number;
		}
		return null;
	}

	/** Lines the panel shows for {@code s}: title, count, state word. */
	static String headline(Summary s) {
		return switch (s.state()) {
			case NONE -> "NO PR";
			case MERGED -> "MERGED";
			case DRAFT, OPEN -> s.count() + (s.count() == 1 ? " PR" : " PRs");
		};
	}

	/** Second panel line: what the count means. */
	static String subline(Summary s) {
		return switch (s.state()) {
			case NONE -> "nothing to review";
			case MERGED -> "all merged";
			case DRAFT -> "draft only";
			case OPEN -> "to review";
		};
	}

	/** Panel colour (0xRRGGBB) per state: green for a review waiting, amber draft, violet merged, dim grey none. */
	static int color(MergeState s) {
		return switch (s) {
			case OPEN -> 0x55FF55;
			case DRAFT -> 0xFFAA00;
			case MERGED -> 0xB583FF;
			case NONE -> 0x8A8F94;
		};
	}
}
