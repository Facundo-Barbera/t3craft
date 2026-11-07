package dev.t3craft.hq;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * Who sits where: which wing each project gets and which desk slot each thread gets. This is the
 * only state the HQ needs to grow by annexes instead of being rebuilt; keep the last layout
 * (in memory or with {@link #toJson()}) and pass it to {@link #allocate} when threads change.
 *
 * <p>Stability rules of {@link #allocate(HqInput, HqLayout)}:
 * <ul>
 *   <li>a project that already had a wing keeps its index; a new project takes the lowest free
 *   index (new projects in key order); a project that is gone frees its wing;</li>
 *   <li>a thread that already had a desk in its project's wing keeps the slot; a new thread takes the
 *   lowest free slot (new threads in id order); a thread that is gone leaves a vacant desk;</li>
 *   <li>a wing never shrinks while its project exists ({@code segments} only grows); it grows by one
 *   segment (two desks) at the far end when every slot is taken.</li>
 * </ul>
 * Without a previous layout the result depends on the input only (fully deterministic).
 *
 * @param wings sorted by index; indices unique (not necessarily contiguous)
 */
public record HqLayout(List<Wing> wings) {
	/** A fresh wing has this many segments (two desks each). */
	public static final int MIN_SEGMENTS = 3;
	/** JSON format version of {@link #toJson()}. */
	public static final int FORMAT = 1;

	public static final HqLayout EMPTY = new HqLayout(List.of());

	/** One desk in a wing: slot {@code 2k} is segment {@code k}'s north desk, {@code 2k+1} its south desk. */
	public record Desk(int slot, String threadId) {
	}

	/**
	 * @param index    position along the spine: row {@code index / 2}, east side when even, west when odd
	 * @param segments length of the wing in desk segments (capacity {@code 2 * segments})
	 * @param desks    occupied desks sorted by slot
	 */
	public record Wing(int index, String projectKey, String label, String envLabel, int segments, List<Desk> desks) {
		public Wing {
			if (index < 0 || segments < 1) {
				throw new IllegalArgumentException("bad wing " + index + "/" + segments);
			}
			desks = desks.stream().sorted(Comparator.comparingInt(Desk::slot)).toList();
			for (int i = 0; i < desks.size(); i++) {
				Desk d = desks.get(i);
				if (d.slot() < 0 || d.slot() >= 2 * segments || (i > 0 && desks.get(i - 1).slot() == d.slot())) {
					throw new IllegalArgumentException("bad desk slot " + d + " in wing " + projectKey);
				}
			}
		}

		public int capacity() {
			return 2 * segments;
		}

		public @Nullable String threadAt(int slot) {
			for (Desk d : desks) {
				if (d.slot() == slot) {
					return d.threadId();
				}
			}
			return null;
		}
	}

	public HqLayout {
		wings = wings.stream().sorted(Comparator.comparingInt(Wing::index)).toList();
		for (int i = 1; i < wings.size(); i++) {
			if (wings.get(i - 1).index() == wings.get(i).index()) {
				throw new IllegalArgumentException("two wings at index " + wings.get(i).index());
			}
		}
	}

	/** Spine rows needed (0 when there is no wing). */
	public int rows() {
		return wings.isEmpty() ? 0 : wings.get(wings.size() - 1).index() / 2 + 1;
	}

	public @Nullable Wing wing(String projectKey) {
		for (Wing w : wings) {
			if (w.projectKey().equals(projectKey)) {
				return w;
			}
		}
		return null;
	}

	public @Nullable Wing wingAt(int index) {
		for (Wing w : wings) {
			if (w.index() == index) {
				return w;
			}
		}
		return null;
	}

	/** Allocates wings and desks for {@code input}, keeping what {@code previous} had (see class doc). */
	public static HqLayout allocate(HqInput input, @Nullable HqLayout previous) {
		HqLayout prev = previous == null ? EMPTY : previous;
		TreeSet<Integer> used = new TreeSet<>();
		Map<String, Integer> index = new HashMap<>();
		for (HqInput.Project p : input.projects()) {
			Wing old = prev.wing(p.key());
			if (old != null) {
				index.put(p.key(), old.index());
				used.add(old.index());
			}
		}
		for (HqInput.Project p : input.projects()) {
			if (!index.containsKey(p.key())) {
				int i = lowestFree(used);
				index.put(p.key(), i);
				used.add(i);
			}
		}
		List<Wing> out = new ArrayList<>();
		for (HqInput.Project p : input.projects()) {
			Wing old = prev.wing(p.key());
			TreeMap<Integer, String> slots = new TreeMap<>();
			TreeSet<Integer> taken = new TreeSet<>();
			List<String> fresh = new ArrayList<>();
			for (String t : p.threads()) {
				Integer s = null;
				if (old != null) {
					for (Desk d : old.desks()) {
						if (d.threadId().equals(t)) {
							s = d.slot();
						}
					}
				}
				if (s != null) {
					slots.put(s, t);
					taken.add(s);
				} else {
					fresh.add(t);
				}
			}
			for (String t : fresh) {
				int s = lowestFree(taken);
				slots.put(s, t);
				taken.add(s);
			}
			int need = slots.isEmpty() ? 0 : (slots.lastKey() + 2) / 2;
			int segments = Math.max(Math.max(MIN_SEGMENTS, need), old == null ? 0 : old.segments());
			List<Desk> desks = new ArrayList<>();
			slots.forEach((s, t) -> desks.add(new Desk(s, t)));
			out.add(new Wing(index.get(p.key()), p.key(), p.label(), p.envLabel(), segments, desks));
		}
		return new HqLayout(out);
	}

	private static int lowestFree(TreeSet<Integer> used) {
		int i = 0;
		while (used.contains(i)) {
			i++;
		}
		return i;
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("format", FORMAT);
		JsonArray ws = new JsonArray();
		for (Wing w : wings) {
			JsonObject j = new JsonObject();
			j.addProperty("index", w.index());
			j.addProperty("project", w.projectKey());
			j.addProperty("label", w.label());
			j.addProperty("env", w.envLabel());
			j.addProperty("segments", w.segments());
			JsonObject desks = new JsonObject();
			for (Desk d : w.desks()) {
				desks.addProperty(Integer.toString(d.slot()), d.threadId());
			}
			j.add("desks", desks);
			ws.add(j);
		}
		o.add("wings", ws);
		return o;
	}

	/** Reads {@link #toJson()} output; anything unreadable gives {@link #EMPTY} (the HQ is then re-allocated). */
	public static HqLayout fromJson(@Nullable JsonElement e) {
		try {
			if (e == null || !e.isJsonObject() || e.getAsJsonObject().get("format").getAsInt() != FORMAT) {
				return EMPTY;
			}
			List<Wing> out = new ArrayList<>();
			for (JsonElement we : e.getAsJsonObject().getAsJsonArray("wings")) {
				JsonObject j = we.getAsJsonObject();
				List<Desk> desks = new ArrayList<>();
				for (var d : j.getAsJsonObject("desks").entrySet()) {
					desks.add(new Desk(Integer.parseInt(d.getKey()), d.getValue().getAsString()));
				}
				out.add(new Wing(j.get("index").getAsInt(), j.get("project").getAsString(), j.get("label").getAsString(),
						j.get("env").getAsString(), j.get("segments").getAsInt(), desks));
			}
			return new HqLayout(out);
		} catch (RuntimeException ex) {
			return EMPTY;
		}
	}
}
