package dev.t3craft.t3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * State changes worth telling the player (chat + toast + sound) and the one-line HUD peek
 * (ports of bridge/src/state/npcStore.ts {@code transitionFor} / {@code peekFor}).
 * Initial state is seeded silently: only NPCs that were already shown before, in an environment
 * that was already in sync, can notify.
 */
public final class Transitions {
	private Transitions() {
	}

	public enum Sound {
		ATTENTION,
		DONE,
		ERROR
	}

	public record Transition(String npcId, ThreadState from, ThreadState to, String title, String text, int color, Sound sound) {
	}

	public static @Nullable Transition of(@Nullable NpcView before, NpcView after) {
		if (before == null || before.state() == after.state()) {
			return null;
		}
		String text = after.title() + " · " + after.groupLabel() + " (" + after.envLabel() + ")";
		return switch (after.state()) {
			case NEEDS_ATTENTION -> new Transition(after.id(), before.state(), after.state(),
					after.activity() == null ? "Needs your input" : after.activity(), text, after.color(), Sound.ATTENTION);
			case ERROR -> new Transition(after.id(), before.state(), after.state(), "Thread failed", text, after.color(), Sound.ERROR);
			case DONE -> before.state() == ThreadState.WORKING || before.state() == ThreadState.NEEDS_ATTENTION
					? new Transition(after.id(), before.state(), after.state(), "Thread finished", text, after.color(), Sound.DONE)
					: null;
			default -> null;
		};
	}

	/**
	 * @param before NPCs shown at the previous refresh, by id
	 * @param after NPCs to show now
	 * @param syncedEnvs environments that were already synchronized at the previous refresh
	 */
	public static List<Transition> between(Map<String, NpcView> before, List<NpcView> after, Set<String> syncedEnvs) {
		List<Transition> out = new ArrayList<>();
		for (NpcView v : after) {
			if (!syncedEnvs.contains(v.env())) {
				continue;
			}
			Transition t = of(before.get(v.id()), v);
			if (t != null) {
				out.add(t);
			}
		}
		return out;
	}

	public record Peek(@Nullable String text, int color, @Nullable String npcId) {
	}

	/** Needs-attention first, then working; empty otherwise. {@code npcs} in display order. */
	public static Peek peek(List<NpcView> npcs) {
		NpcView pick = npcs.stream().filter(n -> n.state() == ThreadState.NEEDS_ATTENTION).findFirst()
				.orElseGet(() -> npcs.stream().filter(n -> n.state() == ThreadState.WORKING).findFirst().orElse(null));
		if (pick == null) {
			return new Peek(null, ThreadState.IDLE.color, null);
		}
		long others = npcs.stream().filter(n -> n.state() == pick.state()).count() - 1;
		return new Peek(pick.glyph() + " " + pick.title() + " [" + pick.envLabel() + "]"
				+ (pick.activity() == null ? "" : " — " + pick.activity())
				+ (others > 0 ? "  (+" + others + " more)" : ""), pick.color(), pick.id());
	}
}
