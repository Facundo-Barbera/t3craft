package dev.t3craft.hq.construct;

import dev.t3craft.hq.HqFootprints;
import dev.t3craft.hq.HqSite;
import dev.t3craft.hq.Placement;
import dev.t3craft.site.CellPlan;
import dev.t3craft.site.Rect;
import dev.t3craft.site.SiteFrame;
import dev.t3craft.site.SiteModule;
import dev.t3craft.site.SiteRequest;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.jspecify.annotations.Nullable;

/**
 * How the HQ of {@code feat/hq-design} sits on a site of {@code feat/site}.
 *
 * <ul>
 *   <li><b>Frames.</b> HQ coordinates have the entrance south ({@code +z}) and grow north; site
 *   plan coordinates have the front at {@code -z} and grow towards {@code +z}. So
 *   {@code site = (-x, y, -z)} (a half turn) and the HQ origin (hub floor centre) is the site origin
 *   (pad centre, one above the pad). The HQ floor ({@code y = 0}) stands on the pad; nothing below
 *   it is built ({@link #MIN_Y}).</li>
 *   <li><b>Site modules.</b> {@code base} = hub (with the "Archived" shelf) + portico, {@code wing-<i>} = grid slot
 *   {@code i} ({@link HqFootprints#slot}). One site module per HQ module: each new wing is one
 *   site expansion, with its own snapshot and undo step.</li>
 *   <li><b>Capacity.</b> The site's envelope is at most {@link SiteRequest#MAX_ENVELOPE} deep, which
 *   holds {@link #MAX_MODULES} wings (two rows).</li>
 * </ul>
 */
public final class HqSiteFrame {
	/** Wings the site envelope can hold (2 rows: 23 + 14 × 2 = 51 ≤ 64 blocks deep). */
	public static final int MAX_MODULES = 4;
	/**
	 * Lowest HQ layer that is built: the floor. The generator has no basement any more (settled
	 * threads go to the "Archived" shelf inside the hub, fix/hq-archive), so nothing goes into the pad.
	 */
	public static final int MIN_Y = 0;
	/** Layers at and below the pad the base asks the site for: none (the HQ has no basement). */
	public static final int BASE_DEPTH = Math.max(0, -MIN_Y);
	public static final String BASE = "base";
	public static final String WING = "wing-";
	public static final String SPEC_PREFIX = "t3craft-hq:1:";

	private HqSiteFrame() {
	}

	/** What the HQ asks the site for: the base (hub with the "Archived" shelf, portico), room for the wings, no depth. */
	public static SiteRequest request() {
		return new SiteRequest(module(BASE), rect(HqFootprints.envelope(MAX_MODULES)), BASE_DEPTH);
	}

	public static String wingId(int index) {
		return WING + index;
	}

	/** The wing index of a site module id, -1 for the base, or null when the id is not ours. */
	public static @Nullable Integer indexOf(String moduleId) {
		if (BASE.equals(moduleId)) {
			return -1;
		}
		if (moduleId.startsWith(WING)) {
			try {
				int i = Integer.parseInt(moduleId.substring(WING.length()));
				return i >= 0 && i < MAX_MODULES ? i : null;
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	/** The site module of {@code base} or {@code wing-<i>}, with its plan-relative rect. */
	public static SiteModule module(String id) {
		Integer i = indexOf(id);
		if (i == null) {
			throw new IllegalArgumentException("not an HQ module: " + id);
		}
		return new SiteModule(id, SPEC_PREFIX + id, rect(i < 0 ? HqFootprints.base() : HqFootprints.slot(i)));
	}

	/** HQ area → site plan rect (half turn). */
	public static Rect rect(HqFootprints.Area a) {
		return new Rect(-a.maxX(), -a.maxZ(), -a.minX(), -a.minZ());
	}

	/** Which site module owns an HQ cell: {@code base}, {@code wing-<i>} or null (outside every slot). */
	public static @Nullable String owner(int x, int z) {
		if (HqFootprints.base().contains(x, z)) {
			return BASE;
		}
		for (int i = 0; i < MAX_MODULES; i++) {
			if (HqFootprints.slot(i).contains(x, z)) {
				return wingId(i);
			}
		}
		return null;
	}

	/** Whether an HQ cell is built at all (not below {@link #MIN_Y}). */
	public static boolean builds(BlockPos hqRel) {
		return hqRel.getY() >= MIN_Y;
	}

	/** HQ relative cell → site plan relative cell. */
	public static BlockPos toSite(BlockPos hq) {
		return hq.rotate(Rotation.CLOCKWISE_180);
	}

	/**
	 * The whole HQ plan in site plan coordinates (positions and states turned half a turn), ready for
	 * {@code BuildPlanBridge.designer}, which turns it again to the site's facing and clips it to each
	 * module's area.
	 */
	public static dev.t3craft.build.BuildPlan sitePlan(dev.t3craft.hq.BuildPlan plan) {
		List<dev.t3craft.build.Placement> out = new ArrayList<>(plan.size());
		for (Placement p : plan.placements()) {
			if (!builds(p.rel())) {
				continue;
			}
			out.add(new dev.t3craft.build.Placement(toSite(p.rel()), p.state().rotate(Rotation.CLOCKWISE_180), p.be()));
		}
		return dev.t3craft.build.BuildPlan.of(out);
	}

	/** Rotation of the HQ in the world for a site facing: half a turn plus the site's own turns. */
	public static Rotation rotation(CellPlan.Facing facing) {
		return Rotation.values()[(SiteFrame.quarterTurns(facing) + 2) & 3];
	}

	/** The {@link HqSite} (world origin + rotation) of the HQ on a site with this origin. */
	public static HqSite hqSite(CellPlan.Origin origin) {
		return new HqSite(new BlockPos(origin.x(), origin.y(), origin.z()), rotation(origin.facing()));
	}
}
