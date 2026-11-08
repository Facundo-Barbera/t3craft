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
 * Workstation screens on the HQ's own computers: a seated thread's screen is laid on the face of its
 * desk's 3×2 screen ({@link StationPlacement#onFace}, when its wing stands), a released thread's on its computer on the hub's
 * "Archived" shelf (when the base stands; the HQ has no basement). Threads without a computer in the building get no station (null), never one on the
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
			// fix/desk-screens-3x2: the display covers the desk's whole 3×2 screen face (blocks module off;
			// with it on, the merged monitor draws itself and the display stays blank)
			byThread.put(w.threadId(), StationPlacement.onFace(c.getX(), c.getY(), c.getZ(), c.getX(), c.getY(), c.getZ(),
					Workstation.SCREEN_WIDTH, screen.yaw()));
		}
		{
			for (Anchor a : plan.anchors().values()) {
				String owner = HqSiteFrame.owner(a.block().getX(), a.block().getZ());
				if (a.name().startsWith(AnchorNames.ARCHIVE + "_") && HqSiteFrame.builds(a.block()) && owner != null
						&& (built == null || built.contains(owner))) {
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
