package dev.t3craft.hq.lead;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.HqLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * feat/lead-review-walk: a lead's new children sit near its office.
 *
 * <p>A post-pass over the layout any style's allocator made, so it works the same for every layout (the
 * citadel being the main one) without touching an allocator:
 * <ul>
 *   <li>Only a child that got a <b>new</b> seat in this allocation moves (its seat differs from the
 *   previous layout's). Kept seats never move, so nothing jumps on a replan, and the result becomes the
 *   next allocation's {@code previous}: the allocators keep it from then on.</li>
 *   <li>It moves only between modules of <b>its own project</b> (a module belongs to one project: label,
 *   sign, colour), to the one whose desks are nearest its lead's desk (the office), when that is at least
 *   {@link #GAIN} blocks nearer than where the allocator put it: a free slot there, or the slot of another
 *   thread that is also new in this allocation and not a lead's child (they swap; nobody who had a desk
 *   is touched).</li>
 *   <li>A module left empty by a move (it can only be one this allocation created) is dropped.</li>
 *   <li>Distances come from the plan of the allocated layout ({@code desk_<id>} anchors), so they are the
 *   real positions of each style: same hall or wing first, then the nearest one.</li>
 * </ul>
 * Pure and deterministic (children in id order); no module index is created, so module indices stay
 * valid for every style and saved layouts keep their format.
 */
public final class LeadTeamSeats {
	/** A move must bring the child at least this much nearer its lead (blocks). */
	static final double GAIN = 1.0;

	private LeadTeamSeats() {
	}

	/**
	 * @param layout   the style's allocation
	 * @param previous the layout it was allocated against (null = none: everything is new)
	 * @param leadOf   a lead's live delegated children (NPC id, the HQ thread id) to the lead's NPC id
	 * @param anchors  the anchors of {@code layout}'s plan (HQ-relative)
	 * @return {@code layout} itself when nothing moves
	 */
	public static HqLayout arrange(HqLayout layout, @Nullable HqLayout previous, Map<String, String> leadOf, Map<String, Anchor> anchors) {
		if (leadOf.isEmpty() || layout.modules().isEmpty()) {
			return layout;
		}
		Map<String, HqLayout.Seat> before = previous == null ? Map.of() : previous.seats();
		Map<String, HqLayout.Seat> seats = new HashMap<>(layout.seats());
		List<String> movers = new ArrayList<>();
		for (String child : new TreeSet<>(leadOf.keySet())) {
			HqLayout.Seat s = seats.get(child);
			if (s != null && !s.equals(before.get(child)) && anchors.get(AnchorNames.desk(leadOf.get(child))) != null) {
				movers.add(child);
			}
		}
		if (movers.isEmpty()) {
			return layout;
		}
		TreeMap<Integer, TreeMap<Integer, String>> desks = new TreeMap<>();
		Map<Integer, HqLayout.Module> mods = new HashMap<>();
		Map<Integer, double[]> centre = new HashMap<>();
		for (HqLayout.Module m : layout.modules()) {
			TreeMap<Integer, String> d = new TreeMap<>();
			double x = 0;
			double y = 0;
			double z = 0;
			int n = 0;
			for (HqLayout.Desk k : m.desks()) {
				d.put(k.slot(), k.threadId());
				Anchor a = anchors.get(AnchorNames.desk(k.threadId()));
				if (a == null) {
					a = anchors.get(AnchorNames.deskSlot(m.index(), k.slot()));
				}
				if (a != null) {
					x += a.x();
					y += a.y();
					z += a.z();
					n++;
				}
			}
			desks.put(m.index(), d);
			mods.put(m.index(), m);
			if (n > 0) {
				centre.put(m.index(), new double[] {x / n, y / n, z / n});
			}
		}
		boolean changed = false;
		for (String child : movers) {
			Anchor lead = Objects.requireNonNull(anchors.get(AnchorNames.desk(leadOf.get(child))));
			HqLayout.Seat cur = seats.get(child);
			HqLayout.Module home = mods.get(cur.module());
			double best = dist(centre.get(cur.module()), lead) - GAIN;
			int bestModule = -1;
			int bestSlot = -1;
			for (HqLayout.Module m : layout.modules()) {
				if (m.index() == cur.module() || !m.projectKey().equals(home.projectKey()) || !desks.containsKey(m.index())) {
					continue;
				}
				double d = dist(centre.get(m.index()), lead);
				if (d >= best) {
					continue;
				}
				int slot = target(desks.get(m.index()), m.capacity(), leadOf, before, seats, m.index());
				if (slot >= 0) {
					best = d;
					bestModule = m.index();
					bestSlot = slot;
				}
			}
			if (bestModule < 0) {
				continue;
			}
			TreeMap<Integer, String> from = desks.get(cur.module());
			TreeMap<Integer, String> to = desks.get(bestModule);
			String other = to.get(bestSlot);
			from.remove(cur.slot());
			to.put(bestSlot, child);
			seats.put(child, new HqLayout.Seat(bestModule, bestSlot));
			if (other != null) {
				from.put(cur.slot(), other);
				seats.put(other, cur);
			} else if (from.isEmpty()) {
				desks.remove(cur.module());
			}
			changed = true;
		}
		if (!changed) {
			return layout;
		}
		List<HqLayout.Module> out = new ArrayList<>();
		for (var e : desks.entrySet()) {
			HqLayout.Module m = mods.get(e.getKey());
			List<HqLayout.Desk> ds = new ArrayList<>();
			e.getValue().forEach((s, t) -> ds.add(new HqLayout.Desk(s, t)));
			out.add(new HqLayout.Module(m.index(), m.projectKey(), m.label(), m.envKey(), m.envLabel(), ds));
		}
		return new HqLayout(out, layout.archive(), layout.overflow());
	}

	/**
	 * The slot of module {@code index} a child may take: the lowest free one, else the lowest one held by a
	 * thread that is new in this allocation and not a lead's child; -1 when none.
	 */
	private static int target(TreeMap<Integer, String> desks, int capacity, Map<String, String> leadOf, Map<String, HqLayout.Seat> before,
			Map<String, HqLayout.Seat> seats, int index) {
		for (int s = 0; s < capacity; s++) {
			if (!desks.containsKey(s)) {
				return s;
			}
		}
		for (var e : desks.entrySet()) {
			String t = e.getValue();
			HqLayout.Seat now = seats.get(t);
			if (!leadOf.containsKey(t) && now != null && now.module() == index && !now.equals(before.get(t))) {
				return e.getKey();
			}
		}
		return -1;
	}

	private static double dist(double @Nullable [] c, Anchor a) {
		if (c == null) {
			return Double.MAX_VALUE / 4;
		}
		double dx = c[0] - a.x();
		double dy = c[1] - a.y();
		double dz = c[2] - a.z();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
