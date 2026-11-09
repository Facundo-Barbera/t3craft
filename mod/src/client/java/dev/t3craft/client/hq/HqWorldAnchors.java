package dev.t3craft.client.hq;

import dev.t3craft.client.npc.behavior.Anchor;
import dev.t3craft.client.npc.behavior.AnchorSource;
import dev.t3craft.hq.HqSite;
import dev.t3craft.hq.construct.HqSiteFrame;
import dev.t3craft.hq.plan.AnchorNames;
import dev.t3craft.hq.plan.BuildPlan;
import dev.t3craft.hq.plan.Workstation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * NPC anchors of a standing HQ, with <b>no hole left for the ring layout</b>: once there is a plan,
 * every NPC gets a spot inside the building, so none stays in the fallback ring on the grass.
 *
 * <ul>
 *   <li>{@link #desk}: its desk ({@code desk_<id>}) when its wing is built; a settled thread stands on
 *   the hub floor in front of its computer on the "Archived" shelf ({@code archive_<id>}), looking at
 *   it; anyone else (no desk yet: wing
 *   still being built, or the HQ is full) waits in the hub, spread over the hub walk points and
 *   benches by a stable hash.</li>
 *   <li>{@link #home}: the project's wing corridor when built, else the hub.</li>
 *   <li>{@link #board}, {@link #boardSlots}, {@link #rest}: the hub's board and benches.</li>
 *   <li>{@link #exit}: the hub's main doorway (once the base stands).</li>
 * </ul>
 * {@code built} = site modules that stand ({@code base}, {@code wing-<i>}); null = all of them (demo).
 */
public final class HqWorldAnchors implements AnchorSource {
	private final HqAnchorSource hq;
	private final BuildPlan plan;
	private final HqSite site;
	private final @Nullable Set<String> built;
	private final List<Anchor> lobby;
	private final List<Anchor> benches;
	/** feat/npc-idle-wander: stroll spots, seats and sights of the built part, world coordinates. */
	private final List<Anchor> strolls;
	private final List<Anchor> seats;
	private final List<dev.t3craft.client.npc.behavior.Pos> sights;

	public HqWorldAnchors(BuildPlan plan, HqSite site, @Nullable Set<String> built) {
		this.hq = new HqAnchorSource(plan, site);
		this.plan = plan;
		this.site = site;
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
		List<Anchor> st = new ArrayList<>();
		List<Anchor> se = new ArrayList<>();
		List<dev.t3craft.client.npc.behavior.Pos> si = new ArrayList<>();
		for (dev.t3craft.hq.plan.Anchor a : plan.anchors().values()) {
			String n = a.name();
			if (!HqSiteFrame.builds(a.block()) || !standsAt(a.block())) {
				continue;
			}
			if (n.startsWith(AnchorNames.BENCH)) {
				se.add(HqAnchorSource.convert(site.anchor(a)));
			} else if (strollPoint(n)) {
				st.add(HqAnchorSource.convert(site.anchor(a)));
			} else if (n.equals(AnchorNames.BOARD) || n.equals(AnchorNames.THREAD_WALL)) {
				dev.t3craft.hq.plan.Anchor w = site.anchor(a);
				si.add(new dev.t3craft.client.npc.behavior.Pos(w.x(), w.y(), w.z()));
			} else {
				AnchorNames.BigScreen bs = AnchorNames.parseBigScreen(n);
				if (bs != null) {
					si.add(screenCentre(a, bs));
				}
			}
		}
		this.strolls = List.copyOf(st);
		this.seats = List.copyOf(se);
		this.sights = List.copyOf(si);
	}

	/** Standing spots worth strolling to: every stroll point ({@code walk*}), the hub, in front of the kanban wall and the archive. */
	static boolean strollPoint(String name) {
		if (!AnchorNames.standing(name)) {
			return false;
		}
		return name.startsWith("walk") || name.equals(AnchorNames.HUB) || name.equals(AnchorNames.THREAD_WALL_FRONT) || name.equals(AnchorNames.ARCHIVE);
	}

	/** Middle of a big screen (its anchor is the top-left cell's face as seen from the front), world coordinates. */
	private dev.t3craft.client.npc.behavior.Pos screenCentre(dev.t3craft.hq.plan.Anchor a, AnchorNames.BigScreen bs) {
		net.minecraft.core.Direction right = a.facing().getCounterClockWise();
		double half = (bs.width() - 1) / 2.0;
		dev.t3craft.hq.plan.Anchor c = new dev.t3craft.hq.plan.Anchor(a.name(), a.x() + right.getStepX() * half, a.y() - (bs.height() - 1) / 2.0,
				a.z() + right.getStepZ() * half, a.facing(), a.block());
		dev.t3craft.hq.plan.Anchor w = site.anchor(c);
		return new dev.t3craft.client.npc.behavior.Pos(w.x(), w.y(), w.z());
	}

	/** Whether the module owning an HQ-relative cell stands (cells outside every slot go with the base). */
	private boolean standsAt(net.minecraft.core.BlockPos rel) {
		if (built == null) {
			return true;
		}
		String owner = HqSiteFrame.owner(rel.getX(), rel.getZ());
		return standing(owner != null ? owner : HqSiteFrame.BASE);
	}

	@Override
	public List<Anchor> strollSpots() {
		return strolls;
	}

	@Override
	public List<Anchor> seats() {
		return seats;
	}

	@Override
	public List<dev.t3craft.client.npc.behavior.Pos> sights() {
		return sights;
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
		dev.t3craft.hq.plan.Anchor archived = plan.anchor(AnchorNames.archive(npcId));
		if (archived != null && HqSiteFrame.builds(archived.block())) {
			String owner = HqSiteFrame.owner(archived.block().getX(), archived.block().getZ());
			if (owner != null && standing(owner)) {
				// the anchor is the screen face on the shelf; stand on the floor in front of it, facing it
				net.minecraft.core.BlockPos front = archived.block().relative(archived.facing()).atY(1);
				dev.t3craft.hq.plan.Anchor spot = new dev.t3craft.hq.plan.Anchor(AnchorNames.archive(npcId), front.getX() + 0.5, front.getY(),
						front.getZ() + 0.5, archived.facing().getOpposite(), front);
				return HqAnchorSource.convert(site.anchor(spot));
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
		dev.t3craft.hq.plan.Anchor a = plan.anchor(AnchorNames.walk(groupId));
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

	/** The hub's main doorway: where settled NPCs leave, and the via point of long routes into the HQ. */
	@Override
	public @Nullable Anchor exit() {
		return standing(HqSiteFrame.BASE) ? hq.exit() : null;
	}

	/** The plan's spawn (the way in), once the base stands: where stranded NPCs are put back. */
	@Override
	public @Nullable Anchor spawn() {
		return standing(HqSiteFrame.BASE) ? hq.spawn() : null;
	}

	@Override
	public @Nullable Anchor rest(String npcId, String groupId) {
		return benches.isEmpty() ? lobby(npcId) : benches.get(Math.floorMod(npcId.hashCode(), benches.size()));
	}

	/** feat/lead-review-walk: {@link #reviewSeats} per lead, computed once per instance (a new plan or build state makes a new one). */
	private final java.util.Map<String, List<ReviewSeat>> reviewSeats = new java.util.HashMap<>();

	/**
	 * feat/lead-review-walk: the lead office's visitor chairs ({@code leadseat_<id>#k}, built ones), then
	 * three spots outside the office's door ({@code module@<i>} of the lead's desk module), facing in.
	 * Empty when the lead has no built desk.
	 */
	@Override
	public List<ReviewSeat> reviewSeats(String leadNpcId) {
		return reviewSeats.computeIfAbsent(leadNpcId, this::computeReviewSeats);
	}

	private List<ReviewSeat> computeReviewSeats(String lead) {
		if (!deskBuilt(lead)) {
			return List.of();
		}
		List<ReviewSeat> out = new ArrayList<>();
		for (int k = 0;; k++) {
			dev.t3craft.hq.plan.Anchor a = plan.anchor(AnchorNames.leadSeat(lead, k));
			if (a == null) {
				break;
			}
			if (HqSiteFrame.builds(a.block()) && standsAt(a.block())) {
				out.add(new ReviewSeat(HqAnchorSource.convert(site.anchor(a)), true));
			}
		}
		Workstation w = plan.workstation(lead);
		dev.t3craft.hq.plan.Anchor door = w == null ? null : plan.anchor(AnchorNames.module(w.module()));
		if (door != null) {
			net.minecraft.core.Direction in = door.facing();
			net.minecraft.core.BlockPos outside = door.block().relative(in.getOpposite(), 2);
			for (net.minecraft.core.BlockPos p : List.of(outside, outside.relative(in.getClockWise()), outside.relative(in.getCounterClockWise()))) {
				if (HqSiteFrame.builds(p) && standsAt(p)) {
					out.add(new ReviewSeat(HqAnchorSource.convert(site.anchor(dev.t3craft.hq.plan.Anchor.at(door.name(), p, in))), false));
				}
			}
		}
		return List.copyOf(out);
	}
}
