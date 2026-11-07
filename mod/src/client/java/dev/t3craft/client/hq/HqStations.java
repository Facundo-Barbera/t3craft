package dev.t3craft.client.hq;

import dev.t3craft.client.workstation.StationPlacement;
import dev.t3craft.client.workstation.WorkstationPlacementSource;
import dev.t3craft.client.workstation.WorkstationThread;
import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqSite;
import dev.t3craft.hq.Workstation;
import dev.t3craft.hq.construct.HqSiteFrame;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Workstation screens on the HQ's own computers: a seated thread's screen sits on its desk's
 * {@code computerPos} (when its wing stands), a settled thread's on its archived computer in the
 * basement. Threads without a computer in the building get no station (null), never one on the
 * grass. Precomputed once per plan; lookups are map reads.
 */
public final class HqStations implements WorkstationPlacementSource {
	private final Map<String, StationPlacement> byThread = new HashMap<>();

	public HqStations(BuildPlan plan, HqSite site, @Nullable Set<String> built) {
		for (Workstation w : plan.workstations()) {
			if (w.threadId() == null || built != null && !built.contains(HqSiteFrame.wingId(w.module()))) {
				continue;
			}
			BlockPos c = site.cell(w.computerPos());
			Anchor screen = site.anchor(w.screen());
			byThread.put(w.threadId(), StationPlacement.on(c.getX(), c.getY(), c.getZ(), screen.yaw()));
		}
		if (built == null || built.contains(HqSiteFrame.BASE)) {
			for (Anchor a : plan.anchors().values()) {
				if (a.name().startsWith(AnchorNames.ARCHIVE + "_")) {
					String thread = a.name().substring(AnchorNames.ARCHIVE.length() + 1);
					Anchor w = site.anchor(a);
					BlockPos c = w.block();
					byThread.putIfAbsent(thread, StationPlacement.on(c.getX(), c.getY(), c.getZ(), w.yaw()));
				}
			}
		}
	}

	@Override
	public @Nullable StationPlacement placement(WorkstationThread thread) {
		return byThread.get(thread.key());
	}

	public int size() {
		return byThread.size();
	}
}
