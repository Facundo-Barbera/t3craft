package dev.t3craft.hq;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * Who lives where in the village HQ (layout/village). The town plan is fixed ({@link VillageTown});
 * this record only says which plot each project's hamlet takes, which desk each thread has, and who
 * is in the singular buildings. Keep the last one and pass it to {@link #allocate} when the roster
 * changes.
 *
 * <h2>Rules of {@link #allocate}</h2>
 * Inputs are canonical (sorted), so the result never depends on roster order.
 * <ol>
 *   <li><b>Singular rooms.</b> A thread the roster sends to a {@link Room} other than {@link Room#WORK}
 *   keeps its place there; newcomers take the lowest free place in thread-id order. A full room sends
 *   the rest to their project's hamlet (as work).</li>
 *   <li><b>Desks kept.</b> A work thread that had a desk in a hamlet of its project keeps it.</li>
 *   <li><b>Hamlets kept.</b> A hamlet keeps its plot while one of its desks is taken; an empty one is
 *   removed and its plot is free again.</li>
 *   <li><b>Desk reuse.</b> A new work thread takes the lowest free desk (main building first, then
 *   the annexes) of its project's hamlets, by plot rank.</li>
 *   <li><b>New hamlet.</b> When every desk of the project is taken (or it has no hamlet), it settles
 *   on the free plot with the lowest {@linkplain VillageTown.Plot#rank() rank}: interior plots freed
 *   earlier are refilled before the town grows outwards. Projects are served in key order.</li>
 *   <li><b>Archive.</b> Settled threads have no desk: the library's terminal lists them all.</li>
 * </ol>
 * {@code allocate(in, allocate(in, null))} equals {@code allocate(in, null)}.
 */
public record VillageLayout(List<Hamlet> hamlets, Map<Room, List<Seat>> rooms, List<String> archived) {
	/** Desks of one hamlet: the main building, then annex B, then annex C. */
	public static final int MAIN_DESKS = 6;
	public static final int ANNEX_B_DESKS = 4;
	public static final int ANNEX_C_DESKS = 4;
	public static final int DESKS = MAIN_DESKS + ANNEX_B_DESKS + ANNEX_C_DESKS;

	/** Where a thread works. Work threads go to their project's hamlet; the others to a singular building. */
	public enum Room {
		/** A project's hamlet. */
		WORK(0),
		/** Waiting for review / a merge / the user's answer: the market hall ({@code lonja}) on the plaza. */
		REVIEW(8),
		/** Scheduled tasks: the mill tower. */
		SCHEDULE(3),
		/** Errors and blocking requests: the guard post. */
		ATTENTION(3),
		/** Idle threads: the tavern (they stand at the bar; no desk). */
		LOUNGE(8);

		private final int capacity;

		Room(int capacity) {
			this.capacity = capacity;
		}

		public int capacity() {
			return capacity;
		}

		/** Module index of the singular building in workstations and anchors. */
		public int module() {
			return 1000 + ordinal();
		}
	}

	/** A taken place (desk or standing spot) of a building. */
	public record Seat(int slot, String threadId) {
	}

	/**
	 * One project's hamlet.
	 *
	 * @param plot  rank of its plot in {@link VillageTown#plots}
	 * @param desks taken desks by slot ({@code 0..5} main, {@code 6..9} annex B, {@code 10..13} annex C)
	 */
	public record Hamlet(int plot, String projectKey, String label, String envKey, String envLabel, List<Seat> desks) {
		public Hamlet {
			desks = desks.stream().sorted(Comparator.comparingInt(Seat::slot)).toList();
		}

		public @Nullable String threadAt(int slot) {
			for (Seat s : desks) {
				if (s.slot() == slot) {
					return s.threadId();
				}
			}
			return null;
		}

		/** Threads at work here. */
		public int threads() {
			return desks.size();
		}

		boolean annexB() {
			return desks.stream().anyMatch(s -> s.slot() >= MAIN_DESKS && s.slot() < MAIN_DESKS + ANNEX_B_DESKS);
		}

		boolean annexC() {
			return desks.stream().anyMatch(s -> s.slot() >= MAIN_DESKS + ANNEX_B_DESKS);
		}
	}

	/** The roster plus the room each thread goes to (absent: {@link Room#WORK}). */
	public record Input(HqInput roster, Map<String, Room> rooms) {
		public Input {
			rooms = Map.copyOf(rooms);
		}

		public Input(HqInput roster) {
			this(roster, Map.of());
		}

		Room room(String threadId) {
			return rooms.getOrDefault(threadId, Room.WORK);
		}
	}

	public static final VillageLayout EMPTY = new VillageLayout(List.of(), Map.of(), List.of());

	public VillageLayout {
		hamlets = hamlets.stream().sorted(Comparator.comparingInt(Hamlet::plot)).toList();
		EnumMap<Room, List<Seat>> r = new EnumMap<>(Room.class);
		rooms.forEach((k, v) -> {
			if (!v.isEmpty()) {
				r.put(k, v.stream().sorted(Comparator.comparingInt(Seat::slot)).toList());
			}
		});
		rooms = java.util.Collections.unmodifiableMap(r);
		archived = List.copyOf(new TreeSet<>(archived));
	}

	public @Nullable Hamlet hamletAt(int plot) {
		for (Hamlet h : hamlets) {
			if (h.plot() == plot) {
				return h;
			}
		}
		return null;
	}

	public List<Hamlet> hamletsOf(String projectKey) {
		return hamlets.stream().filter(h -> h.projectKey().equals(projectKey)).toList();
	}

	public List<Seat> room(Room r) {
		return rooms.getOrDefault(r, List.of());
	}

	/** Ranks of the taken plots. */
	public List<Integer> plots() {
		return hamlets.stream().map(Hamlet::plot).toList();
	}

	public static VillageLayout plan(Input in) {
		return allocate(in, null);
	}

	public static VillageLayout allocate(Input in, @Nullable VillageLayout previous) {
		VillageLayout prev = previous == null ? EMPTY : previous;
		int plotCount = VillageTown.get().plots.size();
		// 1. singular rooms
		Map<String, HqInput.Project> projectOf = new HashMap<>();
		List<String> work = new ArrayList<>();
		EnumMap<Room, TreeMap<Integer, String>> rooms = new EnumMap<>(Room.class);
		EnumMap<Room, List<String>> newcomers = new EnumMap<>(Room.class);
		Map<String, Map.Entry<Room, Integer>> oldRoom = new HashMap<>();
		prev.rooms.forEach((r, seats) -> seats.forEach(s -> oldRoom.put(s.threadId(), Map.entry(r, s.slot()))));
		List<String> archived = new ArrayList<>();
		for (HqInput.Project p : in.roster().projects()) {
			archived.addAll(p.settled());
			for (String t : p.threads()) {
				projectOf.put(t, p);
				Room r = in.room(t);
				if (r == Room.WORK) {
					work.add(t);
					continue;
				}
				var old = oldRoom.get(t);
				if (old != null && old.getKey() == r) {
					rooms.computeIfAbsent(r, k -> new TreeMap<>()).put(old.getValue(), t);
				} else {
					newcomers.computeIfAbsent(r, k -> new ArrayList<>()).add(t);
				}
			}
		}
		for (Room r : Room.values()) {
			List<String> fresh = new ArrayList<>(newcomers.getOrDefault(r, List.of()));
			fresh.sort(null);
			TreeMap<Integer, String> seats = rooms.computeIfAbsent(r, k -> new TreeMap<>());
			for (String t : fresh) {
				if (seats.size() < r.capacity()) {
					seats.put(lowestFree(new TreeSet<>(seats.keySet())), t);
				} else {
					work.add(t);
				}
			}
		}
		// 2-3. kept desks, by old hamlet of the same project
		Map<String, int[]> oldDesk = new HashMap<>();
		for (Hamlet h : prev.hamlets) {
			for (Seat s : h.desks()) {
				oldDesk.put(s.threadId(), new int[] {h.plot(), s.slot()});
			}
		}
		TreeMap<Integer, TreeMap<Integer, String>> desks = new TreeMap<>();
		Map<Integer, HqInput.Project> owner = new HashMap<>();
		TreeMap<String, List<String>> fresh = new TreeMap<>();
		work.sort(null);
		for (String t : work) {
			HqInput.Project p = projectOf.get(t);
			int[] od = oldDesk.get(t);
			Hamlet oh = od == null ? null : prev.hamletAt(od[0]);
			if (oh != null && oh.projectKey().equals(p.key())) {
				desks.computeIfAbsent(od[0], k -> new TreeMap<>()).put(od[1], t);
				owner.put(od[0], p);
			} else {
				fresh.computeIfAbsent(p.key(), k -> new ArrayList<>()).add(t);
			}
		}
		TreeSet<Integer> used = new TreeSet<>(desks.keySet());
		// 4-5. new threads: lowest free desk of the project, else a new hamlet on the lowest free plot
		for (var e : fresh.entrySet()) {
			for (String t : e.getValue()) {
				HqInput.Project p = projectOf.get(t);
				boolean seated = false;
				for (var d : desks.entrySet()) {
					if (owner.get(d.getKey()) != p || d.getValue().size() >= DESKS) {
						continue;
					}
					d.getValue().put(lowestFree(new TreeSet<>(d.getValue().keySet())), t);
					seated = true;
					break;
				}
				if (!seated) {
					int plot = lowestFree(used);
					if (plot >= plotCount) {
						throw new IllegalStateException("the village has no free plot left (" + plotCount + " plots)");
					}
					used.add(plot);
					TreeMap<Integer, String> m = new TreeMap<>();
					m.put(0, t);
					desks.put(plot, m);
					owner.put(plot, p);
				}
			}
		}
		List<Hamlet> hs = new ArrayList<>();
		desks.forEach((plot, m) -> {
			HqInput.Project p = owner.get(plot);
			List<Seat> ss = new ArrayList<>();
			m.forEach((s, t) -> ss.add(new Seat(s, t)));
			hs.add(new Hamlet(plot, p.key(), p.label(), p.envKey(), p.envLabel(), ss));
		});
		Map<Room, List<Seat>> rs = new EnumMap<>(Room.class);
		rooms.forEach((r, m) -> {
			List<Seat> ss = new ArrayList<>();
			m.forEach((s, t) -> ss.add(new Seat(s, t)));
			rs.put(r, ss);
		});
		return new VillageLayout(hs, rs, archived);
	}

	private static int lowestFree(TreeSet<Integer> used) {
		int i = 0;
		while (used.contains(i)) {
			i++;
		}
		return i;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof VillageLayout v && hamlets.equals(v.hamlets) && rooms.equals(v.rooms) && archived.equals(v.archived);
	}

	@Override
	public int hashCode() {
		return Objects.hash(hamlets, rooms, archived);
	}
}
