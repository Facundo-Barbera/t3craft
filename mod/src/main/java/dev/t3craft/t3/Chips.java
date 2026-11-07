package dev.t3craft.t3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Short, readable chips for a thread's model, provider, branch and worktree. Pure (no Minecraft).
 *
 * <pre>
 *   claude-opus-5-5             → "opus 5.5"
 *   claude-3-5-sonnet-20241022  → "sonnet 3.5"
 *   gpt-5.1-codex-max           → "codex 5.1 max"
 *   (no model, instance codex)  → "codex"
 *   /home/me/.t3/worktrees/x/y  → "y"      /home/me → "~"
 * </pre>
 */
public final class Chips {
	public static final int MAX_MODEL = 16;
	public static final int MAX_BRANCH = 28;
	public static final int MAX_WORKTREE = 24;

	private static final List<String> CLAUDE_FAMILIES = List.of("opus", "sonnet", "haiku", "fable");
	private static final List<String> FLAVOURS = List.of("max", "mini", "nano", "pro", "fast", "spark");

	private Chips() {
	}

	/** Model chip for a thread: the abbreviated model, else the provider label, else null. */
	public static @Nullable String model(ThreadMeta m) {
		String abbr = abbreviateModel(m.model());
		return abbr != null ? abbr : providerLabel(m.providerKey());
	}

	/**
	 * Provider label shown next to the model in the panel, or null when unknown or when the model chip
	 * already says it (e.g. no model → chip "codex", provider "codex").
	 */
	public static @Nullable String provider(ThreadMeta m) {
		String p = providerLabel(m.providerKey());
		String chip = model(m);
		return p == null || (chip != null && chip.contains(p)) ? null : p;
	}

	/** {@code claudeAgent → claude}; other instance ids (incl. custom ones like {@code codex-work}) as-is. */
	public static @Nullable String providerLabel(@Nullable String instanceOrProvider) {
		if (instanceOrProvider == null || instanceOrProvider.isBlank()) {
			return null;
		}
		String p = instanceOrProvider.trim();
		return switch (p) {
			case "claudeAgent", "claudeCode", "claude-code", "claude_agent" -> "claude";
			default -> Presentation.clip(p, MAX_MODEL);
		};
	}

	public static @Nullable String abbreviateModel(@Nullable String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String s = raw.trim().toLowerCase(Locale.ROOT);
		int slash = s.lastIndexOf('/');
		if (slash >= 0 && slash < s.length() - 1) {
			s = s.substring(slash + 1); // anthropic/claude-sonnet-4-5, openai/gpt-5
		}
		s = s.replaceAll("\\[[^]]*]", "") // [1m]
				.replaceAll("[-@:]?\\d{8}$", "") // dated snapshots
				.replaceAll("-(latest|preview)$", "")
				.replaceAll("-v\\d+(:\\d+)?$", "");
		List<String> tokens = new ArrayList<>();
		for (String t : s.split("[-_ ]+")) {
			if (!t.isEmpty()) {
				tokens.add(t);
			}
		}
		if (tokens.isEmpty()) {
			return null;
		}
		for (String family : CLAUDE_FAMILIES) {
			if (tokens.contains(family)) {
				return join(family, version(tokens), null);
			}
		}
		if (tokens.contains("codex")) {
			return join("codex", version(tokens), flavour(tokens));
		}
		if (tokens.get(0).equals("gpt") && tokens.size() > 1) {
			return join("gpt", version(tokens), flavour(tokens));
		}
		if (tokens.get(0).startsWith("gpt") && tokens.get(0).length() > 3) { // gpt5
			tokens.set(0, tokens.get(0).substring(3));
			return join("gpt", version(tokens), flavour(tokens));
		}
		return Presentation.clip(String.join(" ", tokens), MAX_MODEL);
	}

	/** Digits joined with dots: [5, 5] → "5.5"; tokens that already carry dots keep them. */
	private static @Nullable String version(List<String> tokens) {
		List<String> nums = new ArrayList<>();
		for (String t : tokens) {
			if (t.matches("\\d{1,4}(\\.\\d+)*")) { // skip dated snapshots
				nums.add(t);
			}
		}
		return nums.isEmpty() ? null : String.join(".", nums);
	}

	private static @Nullable String flavour(List<String> tokens) {
		for (String t : tokens) {
			if (FLAVOURS.contains(t)) {
				return t;
			}
		}
		return null;
	}

	private static String join(String name, @Nullable String version, @Nullable String flavour) {
		String out = name + (version == null ? "" : " " + version) + (flavour == null ? "" : " " + flavour);
		return Presentation.clip(out, MAX_MODEL);
	}

	/** Branch clipped for display, or null. */
	public static @Nullable String branch(ThreadMeta m) {
		return m.branch() == null ? null : Presentation.clip(m.branch(), MAX_BRANCH);
	}

	/**
	 * Worktree path → its last segment, or {@code ~} for a home directory. The path lives on the T3
	 * server's machine, so home is recognised by shape ({@code /home/<u>}, {@code /Users/<u>},
	 * {@code /root}, {@code C:\Users\<u>}), not by the local user.
	 */
	public static @Nullable String worktree(@Nullable String path) {
		if (path == null || path.isBlank()) {
			return null;
		}
		String p = path.trim().replace('\\', '/').replaceAll("/+$", "");
		if (p.isEmpty()) {
			return "/";
		}
		if (p.equals("~") || p.matches("(?i)(/home/[^/]+|/Users/[^/]+|/root|[a-z]:/Users/[^/]+)")) {
			return "~";
		}
		int slash = p.lastIndexOf('/');
		return Presentation.clip(slash >= 0 ? p.substring(slash + 1) : p, MAX_WORKTREE);
	}

	/**
	 * Panel line for branch + worktree: {@code "⎇ feat/x · wt-dir"}. The worktree is dropped when it
	 * just repeats the branch ({@code feat/x} ↔ {@code feat-x}). Null when neither is known.
	 */
	public static @Nullable String branchLine(@Nullable String branch, @Nullable String worktree) {
		if (branch == null && worktree == null) {
			return null;
		}
		if (branch == null) {
			return "▣ " + worktree;
		}
		boolean redundant = worktree != null && worktree.equalsIgnoreCase(branch.replace('/', '-'));
		return "⎇ " + branch + (worktree == null || redundant ? "" : " · " + worktree);
	}

	/** Panel line for model + provider: {@code "◆ opus 5.5 · claude"}. Null when no model chip. */
	public static @Nullable String modelLine(@Nullable String model, @Nullable String provider) {
		if (model == null) {
			return null;
		}
		return "◆ " + model + (provider == null ? "" : " · " + provider);
	}
}
