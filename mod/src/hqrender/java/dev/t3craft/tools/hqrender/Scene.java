package dev.t3craft.tools.hqrender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A voxel grid of quads: each cell holds a "model" (the quads of its block state, plus overlays
 * such as NPC stand-ins), interned so equal cells share one list.
 */
final class Scene {
	final int minX;
	final int minY;
	final int minZ;
	final int sx;
	final int sy;
	final int sz;
	private final int[] cells;
	private final List<List<BlockModels.Quad>> models = new ArrayList<>();
	/** Per model id: opaque half-block sub-cells ({@link BlockModels#occupancy}). */
	private int[] masks = new int[64];
	private final Map<Object, Integer> ids = new HashMap<>();
	private final Map<BlockState, Integer> stateIds = new IdentityHashMap<>();
	private final BlockModels blocks;

	Scene(BlockModels blocks, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		this.blocks = blocks;
		this.minX = minX;
		this.minY = minY;
		this.minZ = minZ;
		this.sx = maxX - minX + 1;
		this.sy = maxY - minY + 1;
		this.sz = maxZ - minZ + 1;
		this.cells = new int[sx * sy * sz];
		models.add(List.of()); // 0 = empty
	}

	boolean inside(int x, int y, int z) {
		return x >= minX && y >= minY && z >= minZ && x < minX + sx && y < minY + sy && z < minZ + sz;
	}

	private int index(int x, int y, int z) {
		return ((y - minY) * sz + (z - minZ)) * sx + (x - minX);
	}

	void set(BlockPos p, BlockState s) {
		if (!inside(p.getX(), p.getY(), p.getZ())) {
			return;
		}
		Integer id = stateIds.get(s);
		if (id == null) {
			id = intern(s, blocks.quads(s), blocks.occupancy(s));
			stateIds.put(s, id);
		}
		cells[index(p.getX(), p.getY(), p.getZ())] = id;
	}

	/** A model file placed as it is, turned {@code yRot} degrees ({@link BlockModels#modelQuads}). */
	void setModel(BlockPos p, String modelId, int yRot) {
		if (!inside(p.getX(), p.getY(), p.getZ())) {
			return;
		}
		Object key = List.of("model", modelId, yRot);
		Integer id = ids.get(key);
		if (id == null) {
			id = intern(key, blocks.modelQuads(modelId, yRot), blocks.modelOccupancy(modelId, yRot));
		}
		cells[index(p.getX(), p.getY(), p.getZ())] = id;
	}

	boolean isEmpty(int x, int y, int z) {
		return !inside(x, y, z) || cells[index(x, y, z)] == 0;
	}

	/** Adds quads on top of whatever the cell holds. */
	void overlay(int x, int y, int z, Object key, List<BlockModels.Quad> extra) {
		if (!inside(x, y, z)) {
			return;
		}
		int i = index(x, y, z);
		int cur = cells[i];
		List<Object> k = List.of(cur, key);
		Integer id = ids.get(k);
		if (id == null) {
			List<BlockModels.Quad> merged = new ArrayList<>(models.get(cur));
			merged.addAll(extra);
			id = intern(k, merged, masks[cur]);
		}
		cells[i] = id;
	}

	private int intern(Object key, List<BlockModels.Quad> quads, int mask) {
		Integer id = ids.get(key);
		if (id != null) {
			return id;
		}
		models.add(List.copyOf(quads));
		id = models.size() - 1;
		if (id >= masks.length) {
			masks = java.util.Arrays.copyOf(masks, masks.length * 2);
		}
		masks[id] = mask;
		ids.put(key, id);
		return id;
	}

	/** Opaque half-block sub-cells of a cell (0 outside the grid), see {@link BlockModels#occupancy}. */
	int occupancy(int x, int y, int z) {
		return inside(x, y, z) ? masks[cells[index(x, y, z)]] : 0;
	}

	/** The raw grid (model id per cell, x fastest, then z, then y) and the interned models and masks, for the GPU export (tools/render-gpu). */
	int[] cellsRaw() {
		return cells;
	}

	List<List<BlockModels.Quad>> modelsRaw() {
		return models;
	}

	/** Occupancy per model id; only the first {@code modelsRaw().size()} entries are meaningful. */
	int[] masksRaw() {
		return masks;
	}

	/** Quads of a cell (empty outside the grid). */
	List<BlockModels.Quad> at(int x, int y, int z) {
		return inside(x, y, z) ? models.get(cells[index(x, y, z)]) : List.of();
	}
}
