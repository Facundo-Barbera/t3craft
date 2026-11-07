package dev.t3craft.client.archive;

import dev.t3craft.t3.ThreadShell;
import dev.t3craft.t3.Visibility;
import java.util.Comparator;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One closed thread as the archive shows it: settled (T3's settle, the thread "went home") or
 * archived (T3's archive action). Pure, no Minecraft classes.
 *
 * @param key          {@code <env>:<threadId>}, the same id NPCs and the HQ layout use
 * @param closedAtMs   when it closed: {@code archivedAt} for archived threads, else {@code settledAt}; null if unknown
 */
public record ArchiveEntry(String key, String env, String envLabel, String threadId, String title, String projectId,
		String projectLabel, @Nullable Long closedAtMs, Kind kind) {
	public enum Kind {
		SETTLED("✔"),
		ARCHIVED("▣");

		public final String glyph;

		Kind(String glyph) {
			this.glyph = glyph;
		}
	}

	/** Newest close first; unknown close times last; then by key (total, deterministic order). */
	public static final Comparator<ArchiveEntry> NEWEST_FIRST = Comparator
			.comparingLong((ArchiveEntry e) -> e.closedAtMs() == null ? Long.MAX_VALUE : -e.closedAtMs())
			.thenComparing(e -> e.key());

	public ArchiveEntry {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(kind, "kind");
		env = Objects.requireNonNullElse(env, "");
		envLabel = envLabel == null || envLabel.isBlank() ? env : envLabel;
		title = title == null || title.isBlank() ? "(untitled)" : title;
		projectId = Objects.requireNonNullElse(projectId, "");
		projectLabel = Objects.requireNonNullElse(projectLabel, "");
	}

	public static String key(String env, String threadId) {
		return env + ":" + threadId;
	}

	/**
	 * Whether the archive lists this thread: not deleted, a top-level thread (no subagent or side
	 * chat), and archived or settled (same settle rule as {@link Visibility#isSettled}).
	 */
	public static boolean isCandidate(ThreadShell t) {
		if (t.deletedAt() != null || "subagent".equals(t.relationshipToParent()) || t.sideChat()) {
			return false;
		}
		return t.archivedAt() != null || Visibility.isSettled(t);
	}

	public static ArchiveEntry of(String env, String envLabel, ThreadShell t, @Nullable String projectTitle) {
		boolean archived = t.archivedAt() != null;
		Long closed = archived ? t.archivedAt() : t.settledAt();
		return new ArchiveEntry(key(env, t.id()), env, envLabel, t.id(), t.title(), t.projectId(), projectLabel(projectTitle, t.projectId()),
				closed, archived ? Kind.ARCHIVED : Kind.SETTLED);
	}

	/** Display name, else {@code project <id prefix>}, else {@code (no project)} (same rule as the shell view). */
	static String projectLabel(@Nullable String name, @Nullable String projectId) {
		if (name != null && !name.isBlank()) {
			return name;
		}
		if (projectId == null || projectId.isBlank()) {
			return "(no project)";
		}
		String id = projectId.startsWith("mcp:") ? projectId.substring(4) : projectId;
		return "project " + (id.length() > 8 ? id.substring(0, 8) : id);
	}

	/** HQ project key: {@code <env>:<projectId>} (same as {@code NpcView.groupId()}). */
	public String projectKey() {
		return env + ":" + projectId;
	}
}
