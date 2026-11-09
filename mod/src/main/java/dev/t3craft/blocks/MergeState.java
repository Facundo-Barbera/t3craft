package dev.t3craft.blocks;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/**
 * The {@code pr} block state of the merge station: what the pull requests of the threads it is bound to
 * look like at a glance. {@link #OPEN} (a PR waiting for review) &gt; {@link #DRAFT} &gt; {@link #MERGED} &gt;
 * {@link #NONE}; the station shows the highest one among its threads. {@link #light} is the block light.
 *
 * <p>Pure. The mapping from T3's PR snapshot ({@code state}: open, draft, merged, closed) is {@link #of}.
 */
public enum MergeState implements StringRepresentable {
	/** No linked PR, a closed PR, or nothing bound. */
	NONE(0, 0),
	MERGED(6, 1),
	DRAFT(8, 2),
	OPEN(12, 3);

	/** Block light level, 0..15. */
	public final int light;
	private final int rank;
	private final String serialized;

	MergeState(int light, int rank) {
		this.light = light;
		this.rank = rank;
		this.serialized = name().toLowerCase(Locale.ROOT);
	}

	@Override
	public String getSerializedName() {
		return serialized;
	}

	/** True for the states that wait for the player: an open PR or a draft. */
	public boolean pending() {
		return this == OPEN || this == DRAFT;
	}

	/** The more urgent of two states. */
	public static MergeState best(MergeState a, MergeState b) {
		return b.rank > a.rank ? b : a;
	}

	/**
	 * The state of a thread's linked PR: {@code prNumber} 0 (or less) = none; a link without a snapshot
	 * ({@code prState} null) counts as open; {@code closed} (not merged) and unknown words count as none.
	 */
	public static MergeState of(@Nullable String prState, int prNumber) {
		if (prNumber <= 0) {
			return NONE;
		}
		if (prState == null || prState.isBlank()) {
			return OPEN;
		}
		return switch (prState.toLowerCase(Locale.ROOT)) {
			case "open" -> OPEN;
			case "draft" -> DRAFT;
			case "merged" -> MERGED;
			default -> NONE;
		};
	}
}
