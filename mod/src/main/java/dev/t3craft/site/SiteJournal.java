package dev.t3craft.site;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jspecify.annotations.Nullable;

/**
 * The site record, persisted in {@code state.nbt} before each phase starts so an interrupted
 * build/undo/move (crash, quit) can be finished on the next load. It is also the site's registry:
 * where it is, what it may grow to, and the stack of modules built so far (ids and specs, never raw
 * blocks), each with the snapshot taken right before it.
 *
 * @param origin site origin (pad centre one above the pad, entrance side); null when there is no site
 * @param request base + growth envelope the site was chosen for; null when there is no site
 * @param reserve world box of the growth reserve (envelope + apron); null when there is no site
 * @param stack expansions, oldest (the base) first; while BUILDING the last one is being built
 * @param restoreMode while RESTORING: what the player chose for their own changes
 * @param restoreDownTo while RESTORING: restore (newest first) until the stack has this many entries
 * @param next during a move: the new site (its base snapshot and plan already saved), started once
 *     the restore is done
 * @param pending modules waiting to be rebuilt by the builders after a move (see
 *     {@link SiteService#modulesToRebuild()})
 */
public record SiteJournal(Phase phase, String dimension, CellPlan.@Nullable Origin origin, @Nullable SiteRequest request, @Nullable Box reserve,
		List<Expansion> stack, Restorer.@Nullable Mode restoreMode, int restoreDownTo, @Nullable SiteJournal next, List<SiteModule> pending,
		long updatedMs) {
	public enum Phase {
		/** No site. */
		NONE,
		/** The top expansion's snapshot and plan are durable; its blocks are being written (or were, until interrupted). */
		BUILDING,
		/** Every expansion is built. */
		BUILT,
		/** Restoring expansions from their snapshots, newest first (undo, or the first half of a move). */
		RESTORING
	}

	public static final int VERSION = 2;
	public static final SiteJournal EMPTY = new SiteJournal(Phase.NONE, "", null, null, null, List.of(), null, 0, null, List.of(), 0L);

	public SiteJournal {
		stack = List.copyOf(stack);
		pending = List.copyOf(pending);
	}

	/** The site's id: its base expansion's key, or null when there is no site. */
	public @Nullable String siteId() {
		return stack.isEmpty() ? null : stack.get(0).key();
	}

	public @Nullable Expansion top() {
		return stack.isEmpty() ? null : stack.get(stack.size() - 1);
	}

	public List<SiteModule> modules() {
		List<SiteModule> out = new ArrayList<>();
		stack.forEach(e -> out.add(e.module()));
		return out;
	}

	public SiteJournal with(Phase p, long now) {
		return new SiteJournal(p, dimension, origin, request, reserve, stack, null, 0, null, pending, now);
	}

	public SiteJournal push(Expansion e, List<SiteModule> stillPending, long now) {
		List<Expansion> s = new ArrayList<>(stack);
		s.add(e);
		return new SiteJournal(Phase.BUILDING, dimension, origin, request, reserve, s, null, 0, null, stillPending, now);
	}

	public SiteJournal restoring(Restorer.Mode mode, int downTo, @Nullable SiteJournal then, long now) {
		return new SiteJournal(Phase.RESTORING, dimension, origin, request, reserve, stack, mode, downTo, then, pending, now);
	}

	/** Without the top expansion (still RESTORING); the empty stack becomes {@link #EMPTY}-like. */
	public SiteJournal pop(long now) {
		List<Expansion> s = new ArrayList<>(stack.subList(0, stack.size() - 1));
		return new SiteJournal(phase, dimension, origin, request, reserve, s, restoreMode, restoreDownTo, next, pending, now);
	}

	public CompoundTag toTag() {
		CompoundTag t = new CompoundTag();
		t.putString("format", "t3craft-site-journal");
		t.putInt("version", VERSION);
		t.putString("phase", phase.name());
		t.putString("dimension", dimension);
		if (origin != null) {
			t.putIntArray("origin", new int[] {origin.x(), origin.y(), origin.z()});
			t.putString("facing", origin.facing().name());
		}
		if (request != null) {
			t.put("request", request.toTag());
		}
		if (reserve != null) {
			t.putIntArray("reserve", reserve.toArray());
		}
		ListTag st = new ListTag();
		stack.forEach(e -> st.add(e.toTag()));
		t.put("stack", st);
		if (restoreMode != null) {
			t.putString("mode", restoreMode.name());
		}
		t.putInt("downTo", restoreDownTo);
		if (next != null) {
			t.put("next", next.toTag());
		}
		ListTag pend = new ListTag();
		pending.forEach(m -> pend.add(m.toTag()));
		t.put("pending", pend);
		t.putLong("updated", updatedMs);
		return t;
	}

	public static SiteJournal fromTag(CompoundTag t) throws IOException {
		if (!"t3craft-site-journal".equals(t.getStringOr("format", ""))) {
			throw new IOException("state.nbt is not a t3craft site journal");
		}
		int version = t.getIntOr("version", -1);
		if (version != VERSION) {
			throw new IOException("site journal version " + version + " is not supported (expected " + VERSION
					+ "); an early test build wrote it. Its snapshots are still in t3craft/site/snapshots/");
		}
		try {
			Phase phase = Phase.valueOf(t.getStringOr("phase", "NONE"));
			CellPlan.Origin origin = null;
			int[] o = t.getIntArray("origin").orElse(null);
			if (o != null) {
				if (o.length != 3) {
					throw new IOException("bad origin");
				}
				origin = new CellPlan.Origin(o[0], o[1], o[2], CellPlan.Facing.valueOf(t.getStringOr("facing", "SOUTH")));
			}
			SiteRequest request = t.getCompound("request").map(SiteRequest::fromTag).orElse(null);
			Box reserve = t.getIntArray("reserve").map(Box::fromArray).orElse(null);
			List<Expansion> stack = new ArrayList<>();
			ListTag st = t.getListOrEmpty("stack");
			for (int i = 0; i < st.size(); i++) {
				stack.add(Expansion.fromTag(st.getCompoundOrEmpty(i)));
			}
			Restorer.Mode mode = t.getString("mode").map(Restorer.Mode::valueOf).orElse(null);
			SiteJournal next = null;
			CompoundTag n = t.getCompound("next").orElse(null);
			if (n != null) {
				next = fromTag(n);
			}
			List<SiteModule> pending = new ArrayList<>();
			ListTag pend = t.getListOrEmpty("pending");
			for (int i = 0; i < pend.size(); i++) {
				pending.add(SiteModule.fromTag(pend.getCompoundOrEmpty(i)));
			}
			SiteJournal j = new SiteJournal(phase, t.getStringOr("dimension", ""), origin, request, reserve, stack, mode, t.getIntOr("downTo", 0), next,
					pending, t.getLongOr("updated", 0L));
			if (phase != Phase.NONE && (stack.isEmpty() || origin == null || request == null || reserve == null)) {
				throw new IOException("site journal in phase " + phase + " without a site");
			}
			return j;
		} catch (IllegalArgumentException e) {
			throw new IOException("bad site journal: " + e.getMessage(), e);
		}
	}
}
