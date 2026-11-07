package dev.t3craft.client.archive;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqPlans;
import dev.t3craft.hq.HqSite;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * Where the archive's pieces are, relative to the HQ origin (read from the HQ generator, never
 * duplicated): slot {@code s} is the {@code archive_<thread>} anchor the generator gives a thread
 * in slot {@code s}, i.e. the centre of the computer's screen face, facing the aisle. Computed once
 * by generating a plan with every slot taken.
 *
 * <p>The <b>catalog</b> lectern stands at {@link #CATALOG_REL} = {@code (6, -4, 1)}: in the basement,
 * just east of the foot of the stairs (the stairs come down at {@code x = 4..5} towards the north and
 * end at {@code z = 2}), facing west, so it is the first thing in front of someone stepping off the
 * stairs. That cell is free in every plan (a test checks it against the generator).
 */
public final class ArchiveGeometry {
	public static final BlockPos CATALOG_REL = new BlockPos(6, -4, 1);
	public static final Direction CATALOG_FACING = Direction.WEST;

	private static @Nullable List<Anchor> slots;

	private ArchiveGeometry() {
	}

	/** Relative screen-face anchors, indexed by slot ({@link HqLayout#ARCHIVE_CAPACITY} of them). */
	public static synchronized List<Anchor> slots() {
		if (slots == null) {
			List<HqLayout.Archived> all = new ArrayList<>();
			for (int s = 0; s < HqLayout.ARCHIVE_CAPACITY; s++) {
				all.add(new HqLayout.Archived(s, "slot" + s, "geometry:p", "geometry"));
			}
			BuildPlan plan = HqPlans.generate(new HqLayout(List.of(), all, List.of()));
			List<Anchor> out = new ArrayList<>();
			for (int s = 0; s < HqLayout.ARCHIVE_CAPACITY; s++) {
				Anchor a = plan.anchor(AnchorNames.archive("slot" + s));
				if (a == null) {
					throw new IllegalStateException("HQ plan has no anchor for archive slot " + s);
				}
				out.add(a.renamed("slot" + s));
			}
			slots = List.copyOf(out);
		}
		return slots;
	}

	/** One slot in world coordinates: the computer cell and the screen-face anchor. */
	public record WorldSlot(int slot, BlockPos cell, Anchor face) {
	}

	public static WorldSlot world(HqSite site, int slot) {
		Anchor face = site.anchor(slots().get(slot));
		return new WorldSlot(slot, face.block(), face);
	}

	/** The catalog's cell and the direction the reader stands in front of, in world coordinates. */
	public record WorldCatalog(BlockPos cell, Direction facing) {
	}

	public static WorldCatalog catalog(HqSite site) {
		return new WorldCatalog(site.cell(CATALOG_REL), site.direction(CATALOG_FACING));
	}
}
