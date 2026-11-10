package dev.t3craft.mapgen.islands;

import dev.t3craft.build.Placement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * A bridge between two islands, as ordered steps from island {@code a} to island {@code b} (ids ordered, so the
 * pair has one bridge whichever side a player comes from). Each {@link Step} is one cross-section of the deck
 * (and its posts), so building the steps in order makes the bridge grow from the island the player stands on.
 *
 * @param id e.g. {@code b:i_0_0|i_1_0}
 */
public record Bridge(String id, String a, String b, List<Step> steps) {

	/** One cross-section: where its centre line is and the blocks of the section. */
	public record Step(BlockPos centre, List<Placement> blocks) {
		public Step {
			centre = centre.immutable();
			blocks = List.copyOf(blocks);
		}
	}

	public Bridge {
		steps = List.copyOf(steps);
	}

	/** The steps in the order they are built when the bridge starts at island {@code from}. */
	public List<Step> stepsFrom(String from) {
		if (from.equals(b)) {
			List<Step> r = new ArrayList<>(steps);
			Collections.reverse(r);
			return r;
		}
		return steps;
	}

	/** The first deck cell on the side of island {@code from}: where it meets that island's edge. */
	public BlockPos startOn(String from) {
		return from.equals(b) ? steps.get(steps.size() - 1).centre() : steps.get(0).centre();
	}

	/** Every block of the bridge. */
	public List<Placement> blocks() {
		List<Placement> out = new ArrayList<>();
		for (Step s : steps) {
			out.addAll(s.blocks());
		}
		return out;
	}
}
