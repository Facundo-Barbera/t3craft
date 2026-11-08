package dev.t3craft.client.t3ui.actions;

import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * T3 Code's thread modes, as the composer shows them. Labels and descriptions follow
 * {@code apps/web/src/components/chat/runtimeModeConfig.ts}; wire values are
 * {@code RuntimeMode} / {@code ProviderInteractionMode} in {@code packages/contracts/src/providerPolicy.ts}.
 */
public final class Modes {
	private Modes() {
	}

	/** {@code RuntimeMode}: how much the agent may do without asking. */
	public enum Runtime {
		APPROVAL_REQUIRED("approval-required", "Supervised", "Ask before commands and file changes."),
		AUTO_ACCEPT_EDITS("auto-accept-edits", "Auto-accept edits", "Auto-approve edits, ask before other actions."),
		AUTO("auto", "Auto", "Supported providers approve routine actions; others still ask."),
		FULL_ACCESS("full-access", "Full access", "Allow commands and edits without prompts.");

		/**
		 * T3Craft's default for new threads: the safest mode (T3's own default is full access; the
		 * player changes it in the picker).
		 */
		public static final Runtime DEFAULT = APPROVAL_REQUIRED;

		public final String wire;
		public final String label;
		public final String description;

		Runtime(String wire, String label, String description) {
			this.wire = wire;
			this.label = label;
			this.description = description;
		}

		public static @Nullable Runtime parse(@Nullable String wire) {
			if (wire == null) {
				return null;
			}
			String w = wire.trim().toLowerCase(Locale.ROOT);
			for (Runtime r : values()) {
				if (r.wire.equals(w)) {
					return r;
				}
			}
			return null;
		}

		/**
		 * The modes a provider supports (all of them when it reports none), in T3's order, like
		 * {@code resolveCompatibleRuntimeMode}.
		 */
		public static List<Runtime> supported(@Nullable List<Runtime> reported) {
			if (reported == null || reported.isEmpty()) {
				return List.of(values());
			}
			return java.util.Arrays.stream(values()).filter(reported::contains).toList();
		}
	}

	/** {@code ProviderInteractionMode}: normal chat or plan mode. */
	public enum Interaction {
		DEFAULT("default", "Chat"),
		PLAN("plan", "Plan");

		public final String wire;
		public final String label;

		Interaction(String wire, String label) {
			this.wire = wire;
			this.label = label;
		}

		public static @Nullable Interaction parse(@Nullable String wire) {
			for (Interaction i : values()) {
				if (i.wire.equals(wire)) {
					return i;
				}
			}
			return null;
		}
	}
}
