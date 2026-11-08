package dev.t3craft.hq;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Collects a {@link BuildPlan} module by module and enforces "no collisions" while doing it:
 * <ul>
 *   <li>a module may only write cells it owns (inside its box, outside its holes);</li>
 *   <li>a module's box may only overlap an earlier module's box inside one of that module's holes;</li>
 *   <li>a cell is written by exactly one module (a module may overwrite its own cells);</li>
 *   <li>on {@link Canvas#close()} every owned cell left unset becomes air.</li>
 * </ul>
 * Any violation throws {@link IllegalStateException}: an invalid plan is never produced.
 */
final class PlanBuilder {
	/** Structure first, attached decoration after (see {@link BuildPlan} order contract). */
	static final int SOLID = 0;
	static final int DECOR = 1;
	/** Hanging things (chains, hanging lanterns): last, top-down, so each comes after what holds it. */
	static final int HANGING = 2;

	private record Cell(Placement placement, int module, int layer) {
	}

	private final Map<Long, Cell> cells = new HashMap<>();
	private final List<BuildPlan.Module> modules = new ArrayList<>();
	private final TreeMap<String, Anchor> anchors = new TreeMap<>();
	private final List<Workstation> workstations = new ArrayList<>();
	private @Nullable Canvas open;
	private @Nullable String group;

	/** Modules opened from now on belong to {@code group} (null: each module is its own group). */
	void group(@Nullable String g) {
		group = g;
	}

	void workstation(Workstation w) {
		workstations.add(w);
	}

	Canvas module(String id, Box box, List<Box> holes) {
		return open(id, box, holes, false);
	}

	/**
	 * A <b>sparse</b> module: it owns only the cells it writes (inside {@code bounds}), nothing is
	 * filled with air on close, and its bounds may overlap other modules' boxes; writing a cell that
	 * another module owns still throws. For grounds that weave between buildings (campus paths,
	 * gardens, outdoor stairs). Must not be followed by a boxed module that overlaps its cells.
	 */
	Canvas sparse(String id, Box bounds) {
		return open(id, bounds, List.of(), true);
	}

	private Canvas open(String id, Box box, List<Box> holes, boolean sparse) {
		if (open != null) {
			throw new IllegalStateException("module " + open.module.id() + " still open");
		}
		for (Box h : holes) {
			if (!box.contains(h)) {
				throw new IllegalStateException(id + ": hole " + h + " outside " + box);
			}
		}
		for (BuildPlan.Module m : modules) {
			if (m.id().equals(id)) {
				throw new IllegalStateException("duplicate module id " + id);
			}
			if (!sparse && !m.sparse() && m.box().intersects(box) && m.holes().stream().noneMatch(h -> h.contains(box))) {
				throw new IllegalStateException("module " + id + " " + box + " collides with " + m.id() + " " + m.box());
			}
		}
		BuildPlan.Module m = new BuildPlan.Module(id, box, holes, group == null ? id : group, sparse ? java.util.Set.of() : null);
		modules.add(m);
		open = new Canvas(m, modules.size() - 1, sparse);
		return open;
	}

	/** True when some module already wrote this cell. */
	boolean taken(int x, int y, int z) {
		return cells.containsKey(BlockPos.asLong(x, y, z));
	}

	void anchor(Anchor a) {
		if (anchors.put(a.name(), a) != null) {
			throw new IllegalStateException("duplicate anchor " + a.name());
		}
	}

	BuildPlan build() {
		if (open != null) {
			throw new IllegalStateException("module " + open.module.id() + " still open");
		}
		resolveShapes();
		List<Cell> all = new ArrayList<>(cells.values());
		all.sort(Comparator.<Cell>comparingInt(c -> c.module)
				.thenComparingInt(c -> c.layer)
				.thenComparingInt(c -> c.layer == HANGING ? -c.placement.rel().getY() : c.placement.rel().getY())
				.thenComparingInt(c -> c.placement.rel().getZ())
				.thenComparingInt(c -> c.placement.rel().getX()));
		List<Placement> out = new ArrayList<>(all.size());
		for (Cell c : all) {
			out.add(c.placement);
		}
		return new BuildPlan(out, liftedAnchors(), modules, liftedWorkstations());
	}

	/**
	 * Standing anchors whose cell holds a thin floor layer (a carpet: a wing runner)
	 * are raised onto its top, so the NPC body at the anchor never overlaps a block.
	 */
	private Anchor lift(Anchor a) {
		if (!AnchorNames.standing(a.name())) {
			return a;
		}
		Cell c = cells.get(a.block().asLong());
		if (c == null || a.y() != a.block().getY()) {
			return a;
		}
		BlockState s = c.placement.state();
		if (s.isAir()) {
			return a;
		}
		net.minecraft.world.phys.shapes.VoxelShape shape = s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, a.block());
		double top = shape.isEmpty() ? 0 : shape.max(net.minecraft.core.Direction.Axis.Y);
		return top > 0 && top <= THIN_FLOOR ? new Anchor(a.name(), a.x(), a.y() + top, a.z(), a.facing(), a.block()) : a;
	}

	/** Thickest floor layer an anchor is lifted onto (carpets are 1/16). */
	static final double THIN_FLOOR = 0.125;

	private TreeMap<String, Anchor> liftedAnchors() {
		TreeMap<String, Anchor> out = new TreeMap<>();
		anchors.forEach((n, a) -> out.put(n, lift(a)));
		return out;
	}

	private List<Workstation> liftedWorkstations() {
		List<Workstation> out = new ArrayList<>(workstations.size());
		for (Workstation w : workstations) {
			out.add(new Workstation(w.module(), w.slot(), w.threadId(), lift(w.deskAnchor()), w.computerPos(), w.screen(), w.screenOrigin()));
		}
		return out;
	}

	/** Bakes pane, wall and stair connections into the states (see {@link PlanShapes}). */
	private void resolveShapes() {
		Map<Long, BlockState> states = new HashMap<>(cells.size() * 2);
		for (Map.Entry<Long, Cell> e : cells.entrySet()) {
			states.put(e.getKey(), e.getValue().placement.state());
		}
		PlanShapes.resolve(states);
		for (Map.Entry<Long, Cell> e : cells.entrySet()) {
			Cell c = e.getValue();
			BlockState s = states.get(e.getKey());
			if (s != c.placement.state()) {
				e.setValue(new Cell(new Placement(c.placement.rel(), s, c.placement.be()), c.module, c.layer));
			}
		}
	}

	/** Write access to one module's cells. */
	final class Canvas implements AutoCloseable {
		final BuildPlan.Module module;
		private final int index;
		private final boolean sparse;
		private final java.util.Set<Long> written = new java.util.HashSet<>();
		private boolean closed;

		private Canvas(BuildPlan.Module module, int index, boolean sparse) {
			this.module = module;
			this.index = index;
			this.sparse = sparse;
		}

		Box box() {
			return module.box();
		}

		Canvas set(int x, int y, int z, BlockState state) {
			return put(x, y, z, state, null, SOLID);
		}

		Canvas set(BlockPos p, BlockState state) {
			return put(p.getX(), p.getY(), p.getZ(), state, null, SOLID);
		}

		Canvas decor(int x, int y, int z, BlockState state) {
			return put(x, y, z, state, null, DECOR);
		}

		Canvas decor(BlockPos p, BlockState state) {
			return put(p.getX(), p.getY(), p.getZ(), state, null, DECOR);
		}

		Canvas hang(int x, int y, int z, BlockState state) {
			return put(x, y, z, state, null, HANGING);
		}

		Canvas decor(BlockPos p, BlockState state, @Nullable CompoundTag be) {
			return put(p.getX(), p.getY(), p.getZ(), state, be, DECOR);
		}

		/** Fills the owned part of a box (clipped to this module) with one state. */
		Canvas fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
			Box b = Box.of(x1, y1, z1, x2, y2, z2);
			for (int y = b.minY(); y <= b.maxY(); y++) {
				for (int z = b.minZ(); z <= b.maxZ(); z++) {
					for (int x = b.minX(); x <= b.maxX(); x++) {
						if (module.owns(x, y, z)) {
							put(x, y, z, state, null, SOLID);
						}
					}
				}
			}
			return this;
		}

		boolean owns(int x, int y, int z) {
			if (sparse) {
				Cell c = cells.get(BlockPos.asLong(x, y, z));
				return module.box().contains(x, y, z) && (c == null || c.module == index);
			}
			return module.owns(x, y, z);
		}

		/** True when some module already wrote this cell (sparse grounds use it to skip taken cells). */
		boolean taken(int x, int y, int z) {
			return cells.containsKey(BlockPos.asLong(x, y, z));
		}

		private Canvas put(int x, int y, int z, BlockState state, @Nullable CompoundTag be, int layer) {
			if (closed) {
				throw new IllegalStateException(module.id() + " is closed");
			}
			if (sparse ? !module.box().contains(x, y, z) : !module.owns(x, y, z)) {
				throw new IllegalStateException(module.id() + " writes " + x + "," + y + "," + z + " outside what it owns " + module.box());
			}
			long key = BlockPos.asLong(x, y, z);
			Cell prev = cells.get(key);
			if (prev != null && prev.module != index) {
				throw new IllegalStateException(module.id() + " collides with " + modules.get(prev.module).id() + " at " + x + "," + y + "," + z);
			}
			cells.put(key, new Cell(new Placement(new BlockPos(x, y, z), state, be == null ? null : be.copy()), index, layer));
			if (sparse) {
				written.add(key);
			}
			return this;
		}

		@Override
		public void close() {
			if (closed) {
				return;
			}
			if (sparse) {
				if (written.isEmpty()) {
					throw new IllegalStateException("sparse module " + module.id() + " wrote nothing");
				}
				BuildPlan.Module m = module;
				modules.set(index, new BuildPlan.Module(m.id(), m.box(), m.holes(), m.group(), written));
				closed = true;
				open = null;
				return;
			}
			BlockState air = Blocks.AIR.defaultBlockState();
			Box b = module.box();
			for (int y = b.minY(); y <= b.maxY(); y++) {
				for (int z = b.minZ(); z <= b.maxZ(); z++) {
					for (int x = b.minX(); x <= b.maxX(); x++) {
						if (module.owns(x, y, z) && !cells.containsKey(BlockPos.asLong(x, y, z))) {
							put(x, y, z, air, null, SOLID);
						}
					}
				}
			}
			closed = true;
			open = null;
		}
	}
}
