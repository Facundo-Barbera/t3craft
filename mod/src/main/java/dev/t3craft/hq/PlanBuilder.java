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

	private record Cell(Placement placement, int module, int layer) {
	}

	private final Map<Long, Cell> cells = new HashMap<>();
	private final List<BuildPlan.Module> modules = new ArrayList<>();
	private final TreeMap<String, Anchor> anchors = new TreeMap<>();
	private @Nullable Canvas open;

	Canvas module(String id, Box box, List<Box> holes) {
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
			if (m.box().intersects(box) && m.holes().stream().noneMatch(h -> h.contains(box))) {
				throw new IllegalStateException("module " + id + " " + box + " collides with " + m.id() + " " + m.box());
			}
		}
		BuildPlan.Module m = new BuildPlan.Module(id, box, holes);
		modules.add(m);
		open = new Canvas(m, modules.size() - 1);
		return open;
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
		List<Cell> all = new ArrayList<>(cells.values());
		all.sort(Comparator.<Cell>comparingInt(c -> c.module)
				.thenComparingInt(c -> c.layer)
				.thenComparingInt(c -> c.placement.rel().getY())
				.thenComparingInt(c -> c.placement.rel().getZ())
				.thenComparingInt(c -> c.placement.rel().getX()));
		List<Placement> out = new ArrayList<>(all.size());
		for (Cell c : all) {
			out.add(c.placement);
		}
		return new BuildPlan(out, anchors, modules);
	}

	/** Write access to one module's cells. */
	final class Canvas implements AutoCloseable {
		final BuildPlan.Module module;
		private final int index;
		private boolean closed;

		private Canvas(BuildPlan.Module module, int index) {
			this.module = module;
			this.index = index;
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
			return module.owns(x, y, z);
		}

		private Canvas put(int x, int y, int z, BlockState state, @Nullable CompoundTag be, int layer) {
			if (closed) {
				throw new IllegalStateException(module.id() + " is closed");
			}
			if (!module.owns(x, y, z)) {
				throw new IllegalStateException(module.id() + " writes " + x + "," + y + "," + z + " outside what it owns " + module.box());
			}
			long key = BlockPos.asLong(x, y, z);
			Cell prev = cells.get(key);
			if (prev != null && prev.module != index) {
				throw new IllegalStateException(module.id() + " collides with " + modules.get(prev.module).id() + " at " + x + "," + y + "," + z);
			}
			cells.put(key, new Cell(new Placement(new BlockPos(x, y, z), state, be == null ? null : be.copy()), index, layer));
			return this;
		}

		@Override
		public void close() {
			if (closed) {
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
