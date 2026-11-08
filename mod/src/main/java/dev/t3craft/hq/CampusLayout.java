package dev.t3craft.hq;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * Who sits where on the campus ({@link CampusStyle}). It produces a normal {@link HqLayout} (same
 * record, same JSON, same archive rules), but its module indices encode the campus places:
 * {@code index = parcel × }{@link CampusGeometry#SLOTS}{@code  + building}.
 *
 * <h2>Rules of {@link #allocate}</h2>
 * <ol>
 *   <li><b>Parcels kept.</b> A project that had modules keeps its parcel (the parcel of its modules)
 *   while it has an active thread. A project with no active thread left frees its parcel.</li>
 *   <li><b>New barrios.</b> Projects without a parcel, in key order, take the <b>lowest free
 *   parcel</b> in the spiral order ({@link CampusGeometry#parcel}): freed parcels are reused first,
 *   and a ring is full before the next one opens. Existing barrios never move.</li>
 *   <li><b>Desks kept.</b> An active thread keeps its building and desk.</li>
 *   <li><b>Growth inside the barrio.</b> A new thread takes the free desk with the lowest
 *   {@code (building, slot)} among its project's buildings; when they are all full, the lowest free
 *   building number of its own barrio is added ({@link #BUILDINGS} at most: three halls around the
 *   patio, then a second storey on each). A project never opens a second barrio; threads beyond
 *   {@code BUILDINGS × 6} desks wait without a desk (like a full site, see {@code HqTarget}).</li>
 *   <li><b>Archive.</b> Exactly {@link HqLayout}'s rules (settled threads: archive terminal).</li>
 * </ol>
 * Without a previous layout the result is a pure function of the roster.
 */
final class CampusLayout {
	/** Buildings (6-desk modules) per barrio: halls 0..2 on the ground, 3..5 their upper storeys. */
	static final int BUILDINGS = 6;

	private CampusLayout() {
	}

	static int parcelOf(int index) {
		return index / CampusGeometry.SLOTS;
	}

	static int buildingOf(int index) {
		return index % CampusGeometry.SLOTS;
	}

	static int index(int parcel, int building) {
		return parcel * CampusGeometry.SLOTS + building;
	}

	/** The lot (1..3 around the patio; 0 is the commons) a building stands on. */
	static int lotOf(int building) {
		return 1 + building % 3;
	}

	/** 0 = ground floor, 1 = upper storey. */
	static int storeyOf(int building) {
		return building / 3;
	}

	static HqLayout allocate(HqInput input, @Nullable HqLayout previous) {
		HqLayout prev = previous == null ? HqLayout.EMPTY : previous;
		Map<String, HqLayout.Seat> oldSeats = prev.seats();
		// 1. parcels of the projects that keep at least one active thread
		TreeMap<String, Integer> parcelOf = new TreeMap<>();
		for (HqInput.Project p : input.projects()) {
			if (p.threads().isEmpty()) {
				continue;
			}
			for (HqLayout.Module m : prev.modulesOf(p.key())) {
				parcelOf.put(p.key(), parcelOf(m.index()));
				break;
			}
		}
		TreeSet<Integer> usedParcels = new TreeSet<>(parcelOf.values());
		// 2. new barrios at the lowest free parcel, projects in key order
		for (HqInput.Project p : input.projects()) {
			if (p.threads().isEmpty() || parcelOf.containsKey(p.key())) {
				continue;
			}
			int free = 0;
			while (usedParcels.contains(free)) {
				free++;
			}
			usedParcels.add(free);
			parcelOf.put(p.key(), free);
		}
		// 3-4. desks
		List<HqLayout.Module> mods = new ArrayList<>();
		for (HqInput.Project p : input.projects()) {
			Integer parcel = parcelOf.get(p.key());
			if (parcel == null) {
				continue;
			}
			TreeMap<Integer, TreeMap<Integer, String>> buildings = new TreeMap<>();
			List<String> fresh = new ArrayList<>();
			for (String t : p.threads()) {
				HqLayout.Seat s = oldSeats.get(t);
				HqLayout.Module om = s == null ? null : prev.moduleAt(s.module());
				if (om != null && om.projectKey().equals(p.key()) && parcelOf(s.module()) == parcel && buildingOf(s.module()) < BUILDINGS) {
					buildings.computeIfAbsent(buildingOf(s.module()), k -> new TreeMap<>()).put(s.slot(), t);
				} else {
					fresh.add(t);
				}
			}
			for (String t : fresh) {
				boolean seated = false;
				for (TreeMap<Integer, String> desks : buildings.values()) {
					if (desks.size() < HqLayout.DESKS) {
						int slot = 0;
						while (desks.containsKey(slot)) {
							slot++;
						}
						desks.put(slot, t);
						seated = true;
						break;
					}
				}
				if (!seated) {
					int b = 0;
					while (buildings.containsKey(b)) {
						b++;
					}
					if (b >= BUILDINGS) {
						continue; // the barrio is full: waits without a desk
					}
					TreeMap<Integer, String> desks = new TreeMap<>();
					desks.put(0, t);
					buildings.put(b, desks);
				}
			}
			for (var e : buildings.entrySet()) {
				List<HqLayout.Desk> ds = new ArrayList<>();
				e.getValue().forEach((slot, t) -> ds.add(new HqLayout.Desk(slot, t)));
				mods.add(new HqLayout.Module(index(parcel, e.getKey()), p.key(), p.label(), p.envKey(), p.envLabel(), ds));
			}
		}
		// 5. the archive: the shared rules (independent of where the modules are)
		HqLayout shared = HqLayout.allocate(input, previous);
		return new HqLayout(mods, shared.archive(), shared.overflow());
	}
}
