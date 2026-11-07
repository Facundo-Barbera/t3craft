package dev.t3craft.site;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * Tells the mod's own blocks from the player's later changes, and restores a snapshot exactly.
 *
 * <p>A cell is <b>ours</b> when it currently holds either its original content (snapshot) or what
 * the plan put there. Anything else was changed by the player (or the game) after the snapshot: a
 * <b>conflict</b>. Restoring only rewrites our cells unless the player explicitly chose to overwrite;
 * since restored cells equal the original, running a restore again (after a crash) is a no-op for
 * them: restore is idempotent.
 */
public final class Restorer {
	private Restorer() {
	}

	public record Conflict(int x, int y, int z, String found, String original) {
		public String describe() {
			return x + " " + y + " " + z + ": " + StateKey.name(found).replace("minecraft:", "") + " (was "
					+ StateKey.name(original).replace("minecraft:", "") + ")";
		}
	}

	public enum Mode {
		/** Restore our cells; leave the player's changes where they are. */
		KEEP_PLAYER_CHANGES,
		/** Restore every cell to the snapshot, discarding the player's changes inside the box. */
		OVERWRITE
	}

	/** Restored / skipped (player changes kept) / already original. */
	public record Result(int restored, int keptPlayerChanges, int unchanged) {
	}

	/** True when the cell holds its original content or the plan's. */
	public static boolean isOurs(BlockWorld w, Snapshot snap, @Nullable CellPlan plan, int x, int y, int z) {
		int i = snap.box().index(x, y, z);
		String cur = w.state(x, y, z);
		CompoundTag curBe = w.blockEntity(x, y, z);
		if (Canon.sameCell(cur, curBe, snap.state(i), snap.blockEntity(i))) {
			return true;
		}
		String planned = plan == null ? null : plan.get(x, y, z);
		return planned != null && planned.equals(cur) && curBe == null;
	}

	public static List<Conflict> conflicts(BlockWorld w, Snapshot snap, @Nullable CellPlan plan) {
		List<Conflict> out = new ArrayList<>();
		Box b = snap.box();
		for (int i = 0; i < snap.size(); i++) {
			int x = b.xOf(i);
			int y = b.yOf(i);
			int z = b.zOf(i);
			if (!isOurs(w, snap, plan, x, y, z)) {
				out.add(new Conflict(x, y, z, w.state(x, y, z), snap.state(i)));
			}
		}
		return out;
	}

	/** Restores the snapshot (bottom-up is fine: writes have no neighbour updates). */
	public static Result restore(BlockWorld w, Snapshot snap, @Nullable CellPlan plan, Mode mode) {
		int restored = 0;
		int kept = 0;
		int unchanged = 0;
		Box b = snap.box();
		for (int i = 0; i < snap.size(); i++) {
			int x = b.xOf(i);
			int y = b.yOf(i);
			int z = b.zOf(i);
			String orig = snap.state(i);
			CompoundTag origBe = snap.blockEntity(i);
			if (Canon.sameCell(w.state(x, y, z), w.blockEntity(x, y, z), orig, origBe)) {
				unchanged++;
				continue;
			}
			if (mode == Mode.KEEP_PLAYER_CHANGES && !isOurs(w, snap, plan, x, y, z)) {
				kept++;
				continue;
			}
			w.set(x, y, z, orig, origBe);
			restored++;
		}
		return new Result(restored, kept, unchanged);
	}
}
