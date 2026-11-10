package dev.t3craft.mapgen.islands;

import dev.t3craft.build.Placement;
import dev.t3craft.mapgen.islands.bridge.BridgeStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;

/**
 * A bridge between two islands, as ordered steps from island {@code a} to island {@code b} (ids ordered, so the
 * pair has one bridge whichever side a player comes from). Each {@link Step} is one cross-section of the bridge
 * (what holds it up, the deck, curbs, rails, lights and cables of that slice, in placing order), so building the
 * steps in order makes the bridge grow from the island the player stands on. The first and last steps reach a
 * little back over each island's rim (only where there is void), so the deck and rails meet the ground cleanly.
 *
 * @param id     e.g. {@code b:i_0_0|i_1_0} (the persistence key, unchanged since the first cut)
 * @param startA the first deck cell over the void on {@code a}'s side: where the bridge leaves that island
 * @param startB the same on {@code b}'s side
 * @param deck   number of deck steps over the void (without the steps over the rims)
 */
public record Bridge(String id, String a, String b, BridgeStyle style, List<Step> steps, BlockPos startA, BlockPos startB, int deck) {

	/** One cross-section: where its centre line is (the deck block) and the blocks of the section. */
	public record Step(BlockPos centre, List<Placement> blocks) {
		public Step {
			centre = centre.immutable();
			blocks = List.copyOf(blocks);
		}
	}

	public Bridge {
		steps = List.copyOf(steps);
		startA = startA.immutable();
		startB = startB.immutable();
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

	/** The first deck cell over the void on the side of island {@code from}: where it leaves that island's edge. */
	public BlockPos startOn(String from) {
		return from.equals(b) ? startB : startA;
	}

	/** The island at the other end from {@code from}. */
	public String other(String from) {
		return from.equals(b) ? a : b;
	}

	/** Every block of the bridge. */
	public List<Placement> blocks() {
		List<Placement> out = new ArrayList<>();
		for (Step s : steps) {
			out.addAll(s.blocks());
		}
		return out;
	}

	/** Every column the bridge puts a block in, as {@link #column} keys. */
	public Set<Long> columns() {
		Set<Long> out = new HashSet<>();
		for (Step s : steps) {
			for (Placement p : s.blocks()) {
				out.add(column(p.pos().getX(), p.pos().getZ()));
			}
		}
		return out;
	}

	/** A column as one long key. */
	public static long column(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}
}
