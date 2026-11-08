package dev.t3craft.hq;

import dev.t3craft.hq.lead.LeadOffices;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * feat/lead-office: the generic fallback for layouts that draw no lead office of their own. Every
 * layout already gives a lead a module of its own (see {@link LeadOffices}); here its desk becomes an
 * "enlarged desk": a second screen of 2×2 next to the desk screen (on a base, bound to the lead's
 * children feed, {@code project:lead:<npcId>}) and a banner in the environment's colour beside the
 * standing spot.
 *
 * <p>Pure plan post-processing, layout-agnostic: only cells the plan already lists as <b>air</b> are
 * changed (same positions, so groups, site modules and the diff are untouched), the desk's own cells
 * and every anchor's cell are left alone, and nothing is done when the side is not free. A layout that
 * draws a real office reserves the {@code lead@<module>} big screen itself (e.g.
 * {@code citadel.CitadelLeadOffice}); then this does nothing for that module.
 */
final class LeadOfficeDecor {
	private LeadOfficeDecor() {
	}

	static BuildPlan apply(BuildPlan plan, HqLayout layout, HqStyle style) {
		List<HqLayout.Module> offices = new ArrayList<>();
		for (HqLayout.Module m : layout.modules()) {
			if (m.type() == HqLayout.Type.LEAD_OFFICE && !m.desks().isEmpty()) {
				offices.add(m);
			}
		}
		if (offices.isEmpty()) {
			return plan;
		}
		java.util.Set<BlockPos> anchored = new java.util.HashSet<>();
		java.util.Set<String> reserved = new java.util.HashSet<>();
		for (Anchor a : plan.anchors().values()) {
			anchored.add(a.block());
			AnchorNames.BigScreen bs = AnchorNames.parseBigScreen(a.name());
			if (bs != null) {
				reserved.add(bs.key());
			}
		}
		Map<BlockPos, BlockState> changes = new HashMap<>();
		List<Anchor> extra = new ArrayList<>();
		for (HqLayout.Module m : offices) {
			String key = LeadOffices.SCREEN_KEY + m.index();
			if (reserved.contains(key)) {
				continue; // the layout drew a real office
			}
			Workstation w = plan.workstation(m.desks().get(0).threadId());
			if (w == null) {
				continue;
			}
			Direction right = w.screenRight();
			BlockPos topLeft = null;
			for (int start : new int[] {Workstation.SCREEN_WIDTH, -2}) {
				BlockPos tl = w.screenOrigin().relative(right, start);
				if (free(plan, changes, anchored, screenCells(tl, right), true)) {
					topLeft = tl;
					break;
				}
			}
			if (topLeft != null) {
				for (BlockPos p : screenCells(topLeft, right)) {
					changes.put(p, style.screen());
				}
				for (int c = 0; c < 2; c++) {
					BlockPos base = topLeft.relative(right, c).below(2);
					if (isAir(plan, base) && !anchored.contains(base)) {
						changes.put(base, style.screenBase());
					}
				}
				extra.add(Anchor.face(AnchorNames.bigScreen(key, 2, 2, "project:" + m.projectKey()), topLeft, w.screenFacing()));
			}
			BlockPos stand = w.deskAnchor().block();
			for (int side : new int[] {-2, 2}) {
				BlockPos b = stand.relative(right, side);
				BlockState below = stateAt(plan, changes, b.below());
				if (below != null && !below.isAir() && free(plan, changes, anchored, List.of(b, b.above()), false)) {
					changes.put(b, LeadOfficeRoom.banner(HqStyle.envColor(m)));
					break;
				}
			}
		}
		if (changes.isEmpty()) {
			return plan;
		}
		BuildPlan out = plan.replacing(p -> {
			BlockState s = changes.get(p.rel());
			return s == null ? p : new Placement(p.rel(), s);
		});
		return out.withAnchors(extra);
	}

	private static List<BlockPos> screenCells(BlockPos topLeft, Direction right) {
		List<BlockPos> out = new ArrayList<>(4);
		for (int r = 0; r < 2; r++) {
			for (int c = 0; c < 2; c++) {
				out.add(topLeft.relative(right, c).below(r));
			}
		}
		return out;
	}

	/** Every cell is listed by the plan as air, is no anchor's cell and is not changed yet. */
	private static boolean free(BuildPlan plan, Map<BlockPos, BlockState> changes, java.util.Set<BlockPos> anchored, List<BlockPos> cells,
			boolean strict) {
		for (BlockPos p : cells) {
			if (changes.containsKey(p) || anchored.contains(p) || !isAir(plan, p)) {
				return false;
			}
		}
		return !strict || !cells.isEmpty();
	}

	private static boolean isAir(BuildPlan plan, BlockPos p) {
		Placement pl = plan.at(p);
		return pl != null && pl.state().isAir() && pl.be() == null;
	}

	private static @Nullable BlockState stateAt(BuildPlan plan, Map<BlockPos, BlockState> changes, BlockPos p) {
		BlockState s = changes.get(p);
		if (s != null) {
			return s;
		}
		Placement pl = plan.at(p);
		return pl == null ? null : pl.state();
	}
}
