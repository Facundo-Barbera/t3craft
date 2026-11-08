package dev.t3craft.hq;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.jspecify.annotations.Nullable;

/**
 * Who sits where. The HQ is a grid of identical project <b>modules</b> (a wing off the spine with
 * {@link #DESKS} desks), plus the "Archived" shelf in the hub for settled threads. This record is the
 * only state the HQ needs to grow by annexes; keep the last one (in memory or {@link #toJson()}) and
 * pass it to {@link #allocate} when the roster changes.
 *
 * <h2>Rules of {@link #allocate(HqInput, HqLayout)}</h2>
 * Inputs are canonical (sorted), so the result never depends on roster order. Then:
 * <ol>
 *   <li><b>Desks kept.</b> An active thread that had a desk in a module of its project keeps it.</li>
 *   <li><b>Modules kept.</b> A module of a project keeps its grid index while at least one of its
 *   desks is taken. A module with no active thread left is removed (its index is freed).</li>
 *   <li><b>Desk reuse.</b> Each new active thread of a project (in thread-id order) takes the free
 *   desk with the lowest {@code (module index, slot)} among its project's modules.</li>
 *   <li><b>Annex.</b> When every desk of the project is taken, a new identical module is added at
 *   the lowest free grid index (index {@code i}: spine row {@code i / 2}, east when even, west when
 *   odd), and the thread takes its slot 0. Projects are served in key order.</li>
 *   <li><b>Archived shelf.</b> A settled thread frees its desk (rule 2 may then remove the module)
 *   and its computer goes to the "Archived" shelf on the hub's south wall, which shows the
 *   {@link #ARCHIVE_CAPACITY} most recently archived threads. Every shelved thread has an archive
 *   sequence number {@link Archived#seq()}: threads already on the shelf keep their slot and
 *   number; new settled threads (in {@code (project key, thread id)} order) get the next numbers.
 *   If they do not all fit, the lowest numbers (the oldest) leave the shelf for {@link #overflow()}
 *   and the newcomers take the lowest free slots. Threads in {@code overflow} stay there (they are
 *   older than everything shelved), except that a shelf slot freed because a shelved thread left
 *   the roster or became active again is refilled from {@code overflow} in thread-id order. A
 *   thread that leaves the roster leaves the shelf and the overflow; one that becomes active again
 *   gets a desk.</li>
 * </ol>
 * The overflow is the "older" list behind the shelf (a click target, {@link AnchorNames#SHELF_MORE}).
 * Without a previous layout ({@code plan(roster)}) the result is a pure function of the roster:
 * dense modules, threads in id order. {@code allocate(in, allocate(in, null))} equals
 * {@code allocate(in, null)}.
 *
 * @param modules  sorted by index; indices unique (not necessarily contiguous)
 * @param archive  the shelf, sorted by slot; slots unique, below {@link #ARCHIVE_CAPACITY}
 * @param overflow settled threads not on the shelf, all older than the shelved ones (sorted)
 */
public record HqLayout(List<Module> modules, List<Archived> archive, List<String> overflow) {
	/** Desks per module (3 segments, a desk on each side of each segment). */
	public static final int DESKS = 6;
	/** Segments per module (2 desks each). */
	public static final int SEGMENTS = DESKS / 2;
	/** Computers the "Archived" shelf holds ({@link HqGeometry#archiveSlots()}). */
	public static final int ARCHIVE_CAPACITY = HqGeometry.archiveSlots().size();
	/** JSON format version of {@link #toJson()}. */
	public static final int FORMAT = 3;

	public static final HqLayout EMPTY = new HqLayout(List.of(), List.of(), List.of());

	/** One taken desk of a module, slot {@code 0..DESKS-1}: {@code 2k} is segment k's north desk, {@code 2k+1} its south one. */
	public record Desk(int slot, String threadId) {
	}

	/**
	 * A computer on the "Archived" shelf.
	 *
	 * @param slot shelf slot in {@link HqGeometry#archiveSlots()} order (anchor {@link AnchorNames#archiveShelfSlot})
	 * @param seq  archive order: higher = archived later (only the order among present threads matters)
	 */
	public record Archived(int slot, String threadId, String projectKey, String envKey, int seq) {
	}

	/** Module types. Only wings for now; the type is in the API so floors can be added later. */
	public enum Type {
		WING
	}

	/**
	 * One project module.
	 *
	 * @param index position on the grid: spine row {@code index / 2}, east side when even, west when odd
	 * @param desks taken desks sorted by slot
	 */
	public record Module(int index, String projectKey, String label, String envKey, String envLabel, List<Desk> desks) {
		public Module {
			if (index < 0) {
				throw new IllegalArgumentException("bad module index " + index);
			}
			desks = desks.stream().sorted(Comparator.comparingInt(Desk::slot)).toList();
			for (int i = 0; i < desks.size(); i++) {
				Desk d = desks.get(i);
				if (d.slot() < 0 || d.slot() >= DESKS || (i > 0 && desks.get(i - 1).slot() == d.slot())) {
					throw new IllegalArgumentException("bad desk slot " + d + " in module " + index);
				}
			}
		}

		public Type type() {
			return Type.WING;
		}

		/** Desk segments (fixed). */
		public int segments() {
			return SEGMENTS;
		}

		public int capacity() {
			return DESKS;
		}

		/**
		 * The module's own origin relative to the HQ origin: the floor cell in the middle of its
		 * doorway, on the spine side.
		 */
		public BlockPos origin() {
			return new BlockPos(HqGeometry.wx(index, 0), 0, HqGeometry.wz(index, (HqGeometry.DOOR_V1 + HqGeometry.DOOR_V2) / 2));
		}

		/**
		 * Orientation of the module relative to the HQ: {@code NONE} = it grows east (+x), {@code
		 * CLOCKWISE_180} = it grows west. West modules are mirror images (their garden stays on the
		 * south side), and the per-module plans are already in HQ coordinates, so builders never apply
		 * this; it is for consumers that reason about modules (minimaps, labels).
		 */
		public Rotation rotation() {
			return HqGeometry.side(index) > 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
		}

		public @Nullable String threadAt(int slot) {
			for (Desk d : desks) {
				if (d.slot() == slot) {
					return d.threadId();
				}
			}
			return null;
		}

		public boolean full() {
			return desks.size() >= DESKS;
		}
	}

	/** Where a thread sits: a module index and a desk slot. */
	public record Seat(int module, int slot) {
	}

	public HqLayout {
		modules = modules.stream().sorted(Comparator.comparingInt(Module::index)).toList();
		for (int i = 1; i < modules.size(); i++) {
			if (modules.get(i - 1).index() == modules.get(i).index()) {
				throw new IllegalArgumentException("two modules at index " + modules.get(i).index());
			}
		}
		archive = archive.stream().sorted(Comparator.comparingInt(Archived::slot)).toList();
		for (int i = 0; i < archive.size(); i++) {
			int s = archive.get(i).slot();
			if (s < 0 || s >= ARCHIVE_CAPACITY || (i > 0 && archive.get(i - 1).slot() == s)) {
				throw new IllegalArgumentException("bad archive slot " + s);
			}
		}
		overflow = List.copyOf(new TreeSet<>(overflow));
	}

	/** Spine rows needed (0 when there is no module). */
	public int rows() {
		return modules.isEmpty() ? 0 : modules.get(modules.size() - 1).index() / 2 + 1;
	}

	public @Nullable Module moduleAt(int index) {
		for (Module m : modules) {
			if (m.index() == index) {
				return m;
			}
		}
		return null;
	}

	/** The project's modules, by index. */
	public List<Module> modulesOf(String projectKey) {
		return modules.stream().filter(m -> m.projectKey().equals(projectKey)).toList();
	}

	/** thread id to seat, for every active thread. */
	public Map<String, Seat> seats() {
		Map<String, Seat> out = new LinkedHashMap<>();
		for (Module m : modules) {
			for (Desk d : m.desks()) {
				out.put(d.threadId(), new Seat(m.index(), d.slot()));
			}
		}
		return out;
	}

	public @Nullable Seat seat(String threadId) {
		return seats().get(threadId);
	}

	public @Nullable Archived archived(String threadId) {
		for (Archived a : archive) {
			if (a.threadId().equals(threadId)) {
				return a;
			}
		}
		return null;
	}

	/** The pure plan of a roster (no history): {@code allocate(input, null)}. */
	public static HqLayout plan(HqInput input) {
		return allocate(input, null);
	}

	/**
	 * Where a project's new module goes (rule 4, "Annex"): its grid index. The default,
	 * {@link #LOWEST_FREE}, is the classic rule (lowest free index); a layout style can give its own
	 * deterministic rule (layout/ring: an annex becomes a floor on the project's sector, a new project
	 * the first free sector in a fixed order). Must return an index not in {@code used}.
	 */
	@FunctionalInterface
	public interface Placer {
		/** The classic rule: the lowest free index. */
		Placer LOWEST_FREE = (project, own, used) -> lowestFree(used);

		/**
		 * @param project the project's key
		 * @param own     indices of the project's modules so far (ascending)
		 * @param used    every index taken so far (ascending)
		 */
		int place(String project, java.util.SortedSet<Integer> own, java.util.SortedSet<Integer> used);
	}

	/**
	 * Allocates modules, desks and archive slots for {@code input}, keeping what {@code previous} had
	 * (see class doc), on the shared spine grid (the classic layout).
	 */
	public static HqLayout allocate(HqInput input, @Nullable HqLayout previous) {
		return allocate(input, previous, Placer.LOWEST_FREE);
	}

	/** Allocation for {@code style}: the spine grid, or the style's own layout ({@link HqStyle#allocate}). */
	public static HqLayout allocate(HqInput input, @Nullable HqLayout previous, HqStyle style) {
		return style.allocate(input, previous);
	}

	/** {@link #allocate(HqInput, HqLayout)} with another rule for where new modules go. */
	public static HqLayout allocate(HqInput input, @Nullable HqLayout previous, Placer placer) {
		HqLayout prev = previous == null ? EMPTY : previous;
		Map<String, Seat> oldSeats = prev.seats();
		// 1-2. kept desks, grouped by old module (only modules of the same project)
		TreeMap<Integer, TreeMap<Integer, String>> desks = new TreeMap<>();
		Map<Integer, HqInput.Project> owner = new HashMap<>();
		Map<String, List<String>> fresh = new TreeMap<>();
		for (HqInput.Project p : input.projects()) {
			List<String> f = new ArrayList<>();
			for (String t : p.threads()) {
				Seat s = oldSeats.get(t);
				Module om = s == null ? null : prev.moduleAt(s.module());
				if (om != null && om.projectKey().equals(p.key())) {
					desks.computeIfAbsent(s.module(), k -> new TreeMap<>()).put(s.slot(), t);
					owner.put(s.module(), p);
				} else {
					f.add(t);
				}
			}
			fresh.put(p.key(), f);
		}
		TreeSet<Integer> used = new TreeSet<>(desks.keySet());
		// 3-4. new threads: lowest free desk of the project, else a new module at the lowest free index
		for (HqInput.Project p : input.projects()) {
			for (String t : fresh.get(p.key())) {
				boolean seated = false;
				for (var e : desks.entrySet()) {
					if (owner.get(e.getKey()) != p || e.getValue().size() >= DESKS) {
						continue;
					}
					int slot = lowestFree(new TreeSet<>(e.getValue().keySet()));
					e.getValue().put(slot, t);
					seated = true;
					break;
				}
				if (!seated) {
					TreeSet<Integer> own = new TreeSet<>();
					for (var o : owner.entrySet()) {
						if (o.getValue() == p) {
							own.add(o.getKey());
						}
					}
					int i = placer.place(p.key(), java.util.Collections.unmodifiableSortedSet(own), java.util.Collections.unmodifiableSortedSet(used));
					if (i < 0 || used.contains(i)) {
						throw new IllegalStateException("placer gave a taken index " + i);
					}
					used.add(i);
					TreeMap<Integer, String> m = new TreeMap<>();
					m.put(0, t);
					desks.put(i, m);
					owner.put(i, p);
				}
			}
		}
		List<Module> mods = new ArrayList<>();
		for (var e : desks.entrySet()) {
			HqInput.Project p = owner.get(e.getKey());
			List<Desk> ds = new ArrayList<>();
			e.getValue().forEach((s, t) -> ds.add(new Desk(s, t)));
			mods.add(new Module(e.getKey(), p.key(), p.label(), p.envKey(), p.envLabel(), ds));
		}
		Shelf shelf = shelve(input, prev);
		return new HqLayout(mods, shelf.archive(), shelf.overflow());
	}

	/** The "Archived" shelf and the older list of a roster (rule 5), whatever the module layout. */
	record Shelf(List<Archived> archive, List<String> overflow) {
	}

	/** Rule 5 of the class doc: who is on the "Archived" shelf, in which slot, and who is in the overflow. */
	static Shelf shelve(HqInput input, HqLayout prev) {
		// 5. the "Archived" shelf
		List<Archived> kept = new ArrayList<>();
		List<String[]> newcomers = new ArrayList<>();
		TreeMap<String, String[]> older = new TreeMap<>();
		java.util.Set<String> prevOverflow = new java.util.HashSet<>(prev.overflow());
		for (HqInput.Project p : input.projects()) {
			for (String t : p.settled()) {
				Archived old = prev.archived(t);
				if (old != null) {
					kept.add(new Archived(old.slot(), t, p.key(), p.envKey(), old.seq()));
				} else if (prevOverflow.contains(t)) {
					older.put(t, new String[] {p.key(), t, p.envKey()});
				} else {
					newcomers.add(new String[] {p.key(), t, p.envKey()});
				}
			}
		}
		int next = kept.stream().mapToInt(Archived::seq).max().orElse(-1) + 1;
		// candidates newest first: newcomers (last one newest), then the kept ones by number
		List<Archived> cands = new ArrayList<>();
		for (int j = newcomers.size() - 1; j >= 0; j--) {
			String[] n = newcomers.get(j);
			cands.add(new Archived(-1, n[1], n[0], n[2], next + j));
		}
		kept.sort(Comparator.comparingInt(Archived::seq).reversed());
		cands.addAll(kept);
		List<String> over = new ArrayList<>(older.keySet());
		List<Archived> stay = new ArrayList<>();
		for (int j = 0; j < cands.size(); j++) {
			if (j < ARCHIVE_CAPACITY) {
				stay.add(cands.get(j));
			} else {
				over.add(cands.get(j).threadId());
			}
		}
		TreeSet<Integer> taken = new TreeSet<>();
		stay.forEach(a -> taken.add(a.slot()));
		List<Archived> arch = new ArrayList<>();
		// newcomers in archive order take the lowest free slots
		stay.sort(Comparator.comparingInt(Archived::seq));
		for (Archived a : stay) {
			if (a.slot() >= 0) {
				arch.add(a);
				continue;
			}
			int s = lowestFree(taken);
			taken.add(s);
			arch.add(new Archived(s, a.threadId(), a.projectKey(), a.envKey(), a.seq()));
		}
		// refill free slots from the older list (only when nothing newer is waiting)
		int low = arch.stream().mapToInt(Archived::seq).min().orElse(0);
		for (var e : older.entrySet()) {
			if (arch.size() >= ARCHIVE_CAPACITY) {
				break;
			}
			int s = lowestFree(taken);
			taken.add(s);
			String[] n = e.getValue();
			arch.add(new Archived(s, n[1], n[0], n[2], --low));
			over.remove(n[1]);
		}
		return new Shelf(arch, over);
	}

	static int lowestFree(java.util.Set<Integer> used) {
		int i = 0;
		while (used.contains(i)) {
			i++;
		}
		return i;
	}

	// ------------------------------------------------------------------ diff

	/** A desk that lost its thread (settled, gone or moved). */
	public record FreedDesk(int module, int slot, String threadId) {
	}

	/**
	 * What changed between two layouts, for builders and for the UI.
	 *
	 * @param addedModules   module indices that are new (build their module plan)
	 * @param removedModules module indices that are gone (their cells are vacated)
	 * @param freedDesks     desks whose thread left (the desk stays, now vacant, unless its module is removed)
	 * @param seated         threads that got a desk (new or moved)
	 * @param archived       threads whose computer went onto the "Archived" shelf in this change
	 * @param unarchived     threads that left the shelf (active again, gone, or pushed to the older list)
	 */
	public record Diff(List<Integer> addedModules, List<Integer> removedModules, List<FreedDesk> freedDesks, Map<String, Seat> seated,
			List<Archived> archived, List<String> unarchived) {
		public boolean isEmpty() {
			return addedModules.isEmpty() && removedModules.isEmpty() && freedDesks.isEmpty() && seated.isEmpty() && archived.isEmpty()
					&& unarchived.isEmpty();
		}
	}

	public static Diff diff(@Nullable HqLayout oldLayout, HqLayout newLayout) {
		HqLayout a = oldLayout == null ? EMPTY : oldLayout;
		TreeSet<Integer> ai = new TreeSet<>();
		a.modules.forEach(m -> ai.add(m.index()));
		TreeSet<Integer> bi = new TreeSet<>();
		newLayout.modules.forEach(m -> bi.add(m.index()));
		List<Integer> added = new ArrayList<>();
		for (int i : bi) {
			Module om = a.moduleAt(i);
			Module nm = newLayout.moduleAt(i);
			if (om == null || !om.projectKey().equals(nm.projectKey())) {
				added.add(i);
			}
		}
		List<Integer> removed = new ArrayList<>();
		for (int i : ai) {
			Module nm = newLayout.moduleAt(i);
			if (nm == null || !nm.projectKey().equals(a.moduleAt(i).projectKey())) {
				removed.add(i);
			}
		}
		Map<String, Seat> oldSeats = a.seats();
		Map<String, Seat> newSeats = newLayout.seats();
		List<FreedDesk> freed = new ArrayList<>();
		for (var e : oldSeats.entrySet()) {
			if (!Objects.equals(newSeats.get(e.getKey()), e.getValue())) {
				freed.add(new FreedDesk(e.getValue().module(), e.getValue().slot(), e.getKey()));
			}
		}
		freed.sort(Comparator.comparingInt(FreedDesk::module).thenComparingInt(FreedDesk::slot));
		Map<String, Seat> seated = new TreeMap<>();
		for (var e : newSeats.entrySet()) {
			if (!Objects.equals(oldSeats.get(e.getKey()), e.getValue())) {
				seated.put(e.getKey(), e.getValue());
			}
		}
		List<Archived> arch = new ArrayList<>();
		for (Archived x : newLayout.archive) {
			if (a.archived(x.threadId()) == null) {
				arch.add(x);
			}
		}
		List<String> unarch = new ArrayList<>();
		for (Archived x : a.archive) {
			if (newLayout.archived(x.threadId()) == null) {
				unarch.add(x.threadId());
			}
		}
		return new Diff(added, removed, freed, seated, arch, unarch);
	}

	// ------------------------------------------------------------------ envelope

	/**
	 * The box (relative to the HQ origin) that every plan with at most {@code maxModules} modules
	 * fits in. Reserve it before the first build so the HQ can grow in place:
	 * <pre>
	 *   rows = ceil(maxModules / 2)
	 *   x: -17 .. 17          (-8 .. 17 when maxModules == 1; -8 .. 8 when 0)
	 *   y: 0 .. 10            (floor .. lightning rod)
	 *   z: -9 - 14 * rows .. 13    (spine cap .. portico; -8 .. 13 when 0)
	 * </pre>
	 * Lowest-free allocation keeps every module index below the largest number of modules the HQ
	 * ever had at once, so {@code maxModules} is that peak, not the number of projects.
	 */
	public static Box envelope(int maxModules) {
		return HqGeometry.envelope(maxModules);
	}

	// ------------------------------------------------------------------ json

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("format", FORMAT);
		JsonArray ms = new JsonArray();
		for (Module m : modules) {
			JsonObject j = new JsonObject();
			j.addProperty("index", m.index());
			j.addProperty("project", m.projectKey());
			j.addProperty("label", m.label());
			j.addProperty("envKey", m.envKey());
			j.addProperty("env", m.envLabel());
			JsonObject desks = new JsonObject();
			for (Desk d : m.desks()) {
				desks.addProperty(Integer.toString(d.slot()), d.threadId());
			}
			j.add("desks", desks);
			ms.add(j);
		}
		o.add("modules", ms);
		JsonArray as = new JsonArray();
		for (Archived a : archive) {
			JsonObject j = new JsonObject();
			j.addProperty("slot", a.slot());
			j.addProperty("thread", a.threadId());
			j.addProperty("project", a.projectKey());
			j.addProperty("envKey", a.envKey());
			j.addProperty("seq", a.seq());
			as.add(j);
		}
		o.add("archive", as);
		JsonArray ov = new JsonArray();
		overflow.forEach(ov::add);
		o.add("overflow", ov);
		return o;
	}

	/**
	 * Reads {@link #toJson()} output; anything unreadable gives {@link #EMPTY} (the HQ is then
	 * re-allocated). Format 2 (basement archive) keeps its modules and drops the archive, so settled
	 * threads are shelved afresh on the next {@link #allocate}.
	 */
	public static HqLayout fromJson(@Nullable JsonElement e) {
		try {
			int format = e == null || !e.isJsonObject() ? -1 : e.getAsJsonObject().get("format").getAsInt();
			if (format != FORMAT && format != 2) {
				return EMPTY;
			}
			JsonObject root = e.getAsJsonObject();
			List<Module> mods = new ArrayList<>();
			for (JsonElement me : root.getAsJsonArray("modules")) {
				JsonObject j = me.getAsJsonObject();
				List<Desk> desks = new ArrayList<>();
				for (var d : j.getAsJsonObject("desks").entrySet()) {
					desks.add(new Desk(Integer.parseInt(d.getKey()), d.getValue().getAsString()));
				}
				mods.add(new Module(j.get("index").getAsInt(), j.get("project").getAsString(), j.get("label").getAsString(),
						j.get("envKey").getAsString(), j.get("env").getAsString(), desks));
			}
			List<Archived> arch = new ArrayList<>();
			List<String> over = new ArrayList<>();
			if (format == 2) {
				return new HqLayout(mods, arch, over);
			}
			for (JsonElement ae : root.getAsJsonArray("archive")) {
				JsonObject j = ae.getAsJsonObject();
				arch.add(new Archived(j.get("slot").getAsInt(), j.get("thread").getAsString(), j.get("project").getAsString(),
						j.get("envKey").getAsString(), j.get("seq").getAsInt()));
			}
			for (JsonElement oe : root.getAsJsonArray("overflow")) {
				over.add(oe.getAsString());
			}
			return new HqLayout(mods, arch, over);
		} catch (RuntimeException ex) {
			return EMPTY;
		}
	}
}
