package dev.t3craft.client.hq;

import dev.t3craft.client.npc.behavior.Anchor;
import dev.t3craft.client.npc.behavior.AnchorSource;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqAnchorSource;
import dev.t3craft.hq.HqSite;
import dev.t3craft.hq.Workstation;
import dev.t3craft.hq.construct.HqSiteFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * NPC anchors of a standing HQ, with <b>no hole left for the ring layout</b>: once there is a plan,
 * every NPC gets a spot inside the building, so none stays in the fallback ring on the grass.
 *
 * <ul>
 *   <li>{@link #desk}: its desk ({@code desk_<id>}) when its wing is built; a settled thread stands at
 *   its archived computer in the basement ({@code archive_<id>}); anyone else (no desk yet: wing
 *   still being built, or the HQ is full) waits in the hub, spread over the hub walk points and
 *   benches by a stable hash.</li>
 *   <li>{@link #home}: the project's wing corridor when built, else the hub.</li>
 *   <li>{@link #board}, {@link #boardSlots}, {@link #rest}: the hub's board and benches.</li>
 * </ul>
 * {@code built} = site modules that stand ({@code base}, {@code wing-<i>}); null = all of them (demo).
 */
public final class HqWorldAnchors implements AnchorSource {
	private final HqAnchorSource hq;
	private final BuildPlan plan;
	private final @Nullable Set<String> built;
	private final List<Anchor> lobby;
	private final List<Anchor> benches;

	public HqWorldAnchors(BuildPlan plan, HqSite site, @Nullable Set<String> built) {
		this.hq = new HqAnchorSource(plan, site);
		this.plan = plan;
		this.built = built == null ? null : Set.copyOf(built);
		List<Anchor> l = new ArrayList<>();
		for (int i = 0; hq.named(AnchorNames.walkHub(i)) != null; i++) {
			l.add(hq.named(AnchorNames.walkHub(i)));
		}
		List<Anchor> b = new ArrayList<>();
		for (int i = 0; hq.named(AnchorNames.bench(i)) != null; i++) {
			b.add(hq.named(AnchorNames.bench(i)));
		}
		this.benches = List.copyOf(b);
		l.addAll(b);
		Anchor hub = hq.named(AnchorNames.HUB);
		if (l.isEmpty() && hub != null) {
			l.add(hub);
		}
		this.lobby = List.copyOf(l);
	}

	private boolean standing(String moduleId) {
		return built == null || built.contains(moduleId);
	}

	/** Whether {@code threadId}'s desk exists in the world (its wing is built). */
	public boolean deskBuilt(String threadId) {
		Workstation w = plan.workstation(threadId);
		return w != null && standing(HqSiteFrame.wingId(w.module()));
	}

	private @Nullable Anchor lobby(String npcId) {
		return lobby.isEmpty() ? hq.named(AnchorNames.HUB) : lobby.get(Math.floorMod(npcId.hashCode(), lobby.size()));
	}

	@Override
	public @Nullable Anchor desk(String npcId, String groupId) {
		if (deskBuilt(npcId)) {
			Anchor d = hq.desk(npcId, groupId);
			if (d != null) {
				return d;
			}
		}
		dev.t3craft.hq.Anchor archived = plan.anchor(AnchorNames.archive(npcId));
		if (archived != null && HqSiteFrame.builds(archived.block())) {
			String owner = HqSiteFrame.owner(archived.block().getX(), archived.block().getZ());
			Anchor a = hq.named(AnchorNames.archive(npcId));
			if (a != null && owner != null && standing(owner)) {
				return a;
			}
		}
		return lobby(npcId);
	}

	@Override
	public @Nullable Anchor board() {
		return hq.board();
	}

	@Override
	public List<Anchor> boardSlots() {
		return hq.boardSlots();
	}

	@Override
	public @Nullable Anchor home(String groupId) {
		Anchor h = hq.home(groupId);
		if (h != null && wingOfProjectBuilt(groupId)) {
			return h;
		}
		return lobby(groupId);
	}

	private boolean wingOfProjectBuilt(String groupId) {
		dev.t3craft.hq.Anchor a = plan.anchor(AnchorNames.walk(groupId));
		if (a == null) {
			return false;
		}
		String owner = HqSiteFrame.owner(a.block().getX(), a.block().getZ());
		return owner != null && standing(owner);
	}

	@Override
	public double wanderRadius(String groupId) {
		return hq.wanderRadius(groupId);
	}

	@Override
	public @Nullable Anchor rest(String npcId, String groupId) {
		return benches.isEmpty() ? lobby(npcId) : benches.get(Math.floorMod(npcId.hashCode(), benches.size()));
	}
}
