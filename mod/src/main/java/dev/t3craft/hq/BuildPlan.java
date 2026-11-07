package dev.t3craft.hq;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The HQ as data: an ordered list of {@link Placement}s relative to the plan origin (the hub floor
 * centre, {@code y = 0} is the floor layer, people stand at {@code y = 1}) plus the named
 * {@link Anchor}s. Immutable and fully deterministic: the same {@link HqLayout} always gives an
 * equal plan (same order, same states, same {@link #fingerprint()}).
 *
 * <p>Order contract: modules in build order (hub, portico, spine, wings, desks, caps); inside a
 * module, structural blocks first and attached decorations (lanterns, signs, pots, carpets) after,
 * each pass bottom-up ({@code y}, then {@code z}, then {@code x}), and hanging things (chains,
 * hanging lanterns) last, top-down, so every block comes after the block that holds it. Every cell of every module's
 * volume is listed, air included, so applying a plan on any terrain gives the same building.
 *
 * <p>No two placements share a position (a generation-time invariant; see {@link PlanBuilder}).
 */
public final class BuildPlan {
	/** A part of the HQ that owns a box of cells (minus {@code holes}, owned by other modules). */
	/**
	 * @param group the buildable unit it belongs to ({@link #groups()}): {@code hub}, {@code portico},
	 *              {@code archive}, {@code spine_<row>}, {@code spine_cap}, {@code module_<index>}
	 */
	public record Module(String id, Box box, List<Box> holes, String group) {
		public Module {
			holes = List.copyOf(holes);
		}

		public boolean owns(int x, int y, int z) {
			if (!box.contains(x, y, z)) {
				return false;
			}
			for (Box h : holes) {
				if (h.contains(x, y, z)) {
					return false;
				}
			}
			return true;
		}
	}

	private final List<Placement> placements;
	private final Map<BlockPos, Placement> byPos;
	private final SortedMap<String, Anchor> anchors;
	private final List<Module> modules;
	private final Box footprint;
	private final List<Workstation> workstations;
	private @Nullable String fingerprint;

	BuildPlan(List<Placement> placements, SortedMap<String, Anchor> anchors, List<Module> modules, List<Workstation> workstations) {
		this.placements = List.copyOf(placements);
		this.workstations = List.copyOf(workstations);
		Map<BlockPos, Placement> m = new LinkedHashMap<>(placements.size() * 2);
		for (Placement p : placements) {
			if (m.put(p.rel(), p) != null) {
				throw new IllegalStateException("two placements at " + p.rel());
			}
		}
		this.byPos = Collections.unmodifiableMap(m);
		this.anchors = Collections.unmodifiableSortedMap(new TreeMap<>(anchors));
		this.modules = List.copyOf(modules);
		Box fp = null;
		for (Module mod : modules) {
			fp = fp == null ? mod.box() : fp.union(mod.box());
		}
		if (fp == null) {
			throw new IllegalStateException("a plan needs at least one module");
		}
		this.footprint = fp;
	}

	/** All placements, in application order. */
	public List<Placement> placements() {
		return placements;
	}

	/** Anchors by name (sorted). See {@link AnchorNames}. */
	public SortedMap<String, Anchor> anchors() {
		return anchors;
	}

	public @Nullable Anchor anchor(String name) {
		return anchors.get(name);
	}

	/** The modules in build order, with the boxes they own. */
	public List<Module> modules() {
		return modules;
	}

	/** Every desk of every module (taken or vacant), by module index then slot. */
	public List<Workstation> workstations() {
		return workstations;
	}

	/** The workstation of an active thread, or null. */
	public @Nullable Workstation workstation(String threadId) {
		for (Workstation w : workstations) {
			if (threadId.equals(w.threadId())) {
				return w;
			}
		}
		return null;
	}

	/** Group of the module that owns {@code rel}, or null when no module owns it. */
	public @Nullable String groupAt(BlockPos rel) {
		for (Module m : modules) {
			if (m.owns(rel.getX(), rel.getY(), rel.getZ())) {
				return m.group();
			}
		}
		return null;
	}

	/** Group names in build order (first module of each group). */
	public List<String> groups() {
		List<String> out = new ArrayList<>();
		for (Module m : modules) {
			if (!out.contains(m.group())) {
				out.add(m.group());
			}
		}
		return out;
	}

	/** Placements of one group, in plan order. */
	public List<Placement> placements(String group) {
		List<Placement> out = new ArrayList<>();
		for (Placement p : placements) {
			if (group.equals(groupAt(p.rel()))) {
				out.add(p);
			}
		}
		return out;
	}

	/** Bounding box of every module (relative to the origin). */
	public Box footprint() {
		return footprint;
	}

	public @Nullable Placement at(BlockPos rel) {
		return byPos.get(rel);
	}

	public int size() {
		return placements.size();
	}

	/** SHA-256 over placements (in order) and anchors: equal plans have equal fingerprints. */
	public String fingerprint() {
		String f = fingerprint;
		if (f == null) {
			try {
				MessageDigest md = MessageDigest.getInstance("SHA-256");
				for (Placement p : placements) {
					md.update((p.rel().getX() + "," + p.rel().getY() + "," + p.rel().getZ() + "=" + p.state()
							+ (p.be() == null ? "" : "+" + p.be()) + "\n").getBytes(StandardCharsets.UTF_8));
				}
				for (Anchor a : anchors.values()) {
					md.update((a + "\n").getBytes(StandardCharsets.UTF_8));
				}
				f = HexFormat.of().formatHex(md.digest());
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException(e);
			}
			fingerprint = f;
		}
		return f;
	}

	/**
	 * What to apply to a world that holds {@code previous} (null = nothing yet) to make it hold
	 * {@code next}: first the cells {@code previous} had and {@code next} no longer has (set to
	 * {@code vacated}, e.g. air or the ground block for {@code y <= 0}), then every placement of
	 * {@code next} whose content changed, in {@code next}'s order. This is how the HQ grows and
	 * shrinks by annexes without rebuilding: thanks to the stable {@link HqLayout} only the touched
	 * modules differ.
	 */
	public static List<Placement> diff(@Nullable BuildPlan previous, BuildPlan next, Vacated vacated) {
		List<Placement> out = new ArrayList<>();
		if (previous != null) {
			for (Placement p : previous.placements) {
				if (next.at(p.rel()) == null) {
					BlockState s = vacated.at(p.rel());
					if (!p.state().equals(s) || p.be() != null) {
						out.add(new Placement(p.rel(), s));
					}
				}
			}
		}
		for (Placement p : next.placements) {
			Placement old = previous == null ? null : previous.at(p.rel());
			if (old == null || !old.sameContent(p)) {
				out.add(p);
			}
		}
		return out;
	}

	/**
	 * This plan with some placements swapped (see {@link HqFixtures}). {@code f} gets every placement
	 * in order and returns it unchanged, or a placement at the <b>same</b> position with another state
	 * or block entity data. Anchors, modules and workstations are kept. Returns {@code this} when
	 * nothing changed.
	 */
	public BuildPlan replacing(java.util.function.UnaryOperator<Placement> f) {
		List<Placement> out = new ArrayList<>(placements.size());
		boolean changed = false;
		for (Placement p : placements) {
			Placement q = f.apply(p);
			if (q == null) {
				q = p;
			}
			if (!q.rel().equals(p.rel())) {
				throw new IllegalArgumentException("replacing must keep positions: " + p.rel() + " -> " + q.rel());
			}
			changed |= q != p;
			out.add(q);
		}
		return changed ? new BuildPlan(out, anchors, modules, workstations) : this;
	}

	/**
	 * This plan with {@code extra} anchors added (same name: the new one wins). Placements, modules and
	 * workstations are kept. For plan post-processing and tests (e.g. reserving a large screen with
	 * {@link AnchorNames#bigScreen} on a generated plan).
	 */
	public BuildPlan withAnchors(java.util.Collection<Anchor> extra) {
		if (extra.isEmpty()) {
			return this;
		}
		TreeMap<String, Anchor> a = new TreeMap<>(anchors);
		for (Anchor x : extra) {
			a.put(x.name(), x);
		}
		return new BuildPlan(placements, a, modules, workstations);
	}

	/** The block that fills a cell a newer plan no longer uses. */
	@FunctionalInterface
	public interface Vacated {
		BlockState at(BlockPos rel);
	}

	@Override
	public String toString() {
		return "BuildPlan{" + placements.size() + " placements, " + modules.size() + " modules, " + anchors.size() + " anchors, footprint "
				+ footprint + "}";
	}
}
