package dev.t3craft.hq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * Who sits where in the town centre ({@link TowncenterGrid}). Same record and the same rules 1-3 and 5
 * as {@link HqLayout#allocate} (kept desks, kept modules, lowest free desk of the project, the archive
 * shelf); rule 4, where a new room goes when a project is full:
 * <ol>
 *   <li><b>Up.</b> The lowest free floor of a building the project already has: its podium room,
 *   then the floors of its corner tower (up to {@link TowncenterGrid#TOWER_MAX}).</li>
 *   <li><b>Its own manzana.</b> A free parcel in a manzana where the project already is.</li>
 *   <li><b>Next door.</b> A free parcel in a work manzana that shares a street with one of the
 *   project's manzanas (lowest spiral cell, then side).</li>
 *   <li><b>Lowest free.</b> Otherwise, and for a project's first room: the lowest free parcel of the
 *   spiral, so the manzana being filled is densified and every hole is refilled before the frontier
 *   moves out.</li>
 * </ol>
 * A parcel is free when no project has a room in it. Projects are served in key order, threads in
 * id order. Module indices of other layouts are not valid here and are reallocated.
 */
final class TowncenterAllocator {
	static final TowncenterAllocator INSTANCE = new TowncenterAllocator();

	private TowncenterAllocator() {
	}

	HqLayout allocate(HqInput input, @Nullable HqLayout previous) {
		HqLayout prev = previous == null ? HqLayout.EMPTY : previous;
		Map<String, HqLayout.Seat> oldSeats = prev.seats();
		TreeMap<Integer, TreeMap<Integer, String>> desks = new TreeMap<>();
		Map<Integer, HqInput.Project> owner = new HashMap<>();
		Map<String, List<String>> fresh = new TreeMap<>();
		for (HqInput.Project p : input.projects()) {
			List<String> f = new ArrayList<>();
			for (String t : p.threads()) {
				HqLayout.Seat s = oldSeats.get(t);
				HqLayout.Module om = s == null ? null : prev.moduleAt(s.module());
				if (om != null && om.projectKey().equals(p.key()) && TowncenterGrid.validModule(s.module())) {
					desks.computeIfAbsent(s.module(), k -> new TreeMap<>()).put(s.slot(), t);
					owner.put(s.module(), p);
				} else {
					f.add(t);
				}
			}
			fresh.put(p.key(), f);
		}
		for (HqInput.Project p : input.projects()) {
			for (String t : fresh.get(p.key())) {
				boolean seated = false;
				for (var e : desks.entrySet()) {
					if (owner.get(e.getKey()) != p || e.getValue().size() >= HqLayout.DESKS) {
						continue;
					}
					e.getValue().put(lowestFree(e.getValue().keySet()), t);
					seated = true;
					break;
				}
				if (!seated) {
					int i = newRoom(p, desks, owner);
					TreeMap<Integer, String> m = new TreeMap<>();
					m.put(0, t);
					desks.put(i, m);
					owner.put(i, p);
				}
			}
		}
		List<HqLayout.Module> mods = new ArrayList<>();
		for (var e : desks.entrySet()) {
			HqInput.Project p = owner.get(e.getKey());
			List<HqLayout.Desk> ds = new ArrayList<>();
			e.getValue().forEach((s, t) -> ds.add(new HqLayout.Desk(s, t)));
			mods.add(new HqLayout.Module(e.getKey(), p.key(), p.label(), p.envKey(), p.envLabel(), ds));
		}
		HqLayout.Shelf shelf = HqLayout.shelve(input, prev);
		return new HqLayout(mods, shelf.archive(), shelf.overflow());
	}

	/** Rule 4: the module index of the project's next room. */
	static int newRoom(HqInput.Project p, Map<Integer, ? extends Map<Integer, String>> desks, Map<Integer, HqInput.Project> owner) {
		Map<Integer, String> parcelOwner = new HashMap<>();
		TreeSet<Integer> mine = new TreeSet<>();
		for (int i : desks.keySet()) {
			int q = TowncenterGrid.parcelOfModule(i);
			parcelOwner.put(q, owner.get(i).key());
			if (owner.get(i).key().equals(p.key())) {
				mine.add(q);
			}
		}
		// 1. up: the next free floor of one of the project's buildings
		for (int q : mine) {
			for (int f = 0; f < TowncenterGrid.FLOORS; f++) {
				if (!desks.containsKey(TowncenterGrid.module(q, f))) {
					return TowncenterGrid.module(q, f);
				}
			}
		}
		TreeSet<Integer> myCells = new TreeSet<>();
		for (int q : mine) {
			myCells.add(EnsancheGrid.cellOfParcel(q));
		}
		// 2. around the patio: a free parcel in one of the project's manzanas
		for (int k : myCells) {
			Integer q = freeParcel(k, parcelOwner);
			if (q != null) {
				return TowncenterGrid.module(q, 0);
			}
		}
		// 3. next door: a free parcel in a neighbouring work manzana
		TreeSet<Integer> next = new TreeSet<>();
		for (int k : myCells) {
			next.addAll(EnsancheGrid.neighbours(k));
		}
		for (int k : next) {
			if (EnsancheGrid.kind(k) == EnsancheGrid.Kind.WORK) {
				Integer q = freeParcel(k, parcelOwner);
				if (q != null) {
					return TowncenterGrid.module(q, 0);
				}
			}
		}
		// 4. the lowest free parcel of the spiral
		for (int k = 1;; k++) {
			if (EnsancheGrid.kind(k) == EnsancheGrid.Kind.WORK) {
				Integer q = freeParcel(k, parcelOwner);
				if (q != null) {
					return TowncenterGrid.module(q, 0);
				}
			}
		}
	}

	private static @Nullable Integer freeParcel(int k, Map<Integer, String> parcelOwner) {
		for (int side = 0; side < EnsancheGrid.SIDES; side++) {
			int q = EnsancheGrid.parcel(k, side);
			if (!parcelOwner.containsKey(q)) {
				return q;
			}
		}
		return null;
	}

	private static int lowestFree(java.util.Set<Integer> used) {
		int i = 0;
		while (used.contains(i)) {
			i++;
		}
		return i;
	}
}
