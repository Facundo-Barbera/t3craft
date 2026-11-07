package dev.t3craft.site;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * In-memory world for tests: a sparse map of cells (missing = air), a fake classifier based on block
 * names, loaded-chunk control and fake entities.
 */
final class FakeWorld implements BlockWorld, TerrainProbe {
	private record Cell(String state, @Nullable CompoundTag be) {
	}

	private final Map<Long, Cell> cells = new HashMap<>();
	private final Set<Long> unloadedChunks = new HashSet<>();
	private final Map<Long, String> entities = new HashMap<>();
	int writes;

	static long key(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}

	/** Flat ground: stone below, dirt, grass at {@code top}, in [x0..x1]×[z0..z1]. */
	FakeWorld flat(int x0, int z0, int x1, int z1, int top) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				column(x, z, top);
			}
		}
		return this;
	}

	FakeWorld column(int x, int z, int top) {
		for (int y = minBuildY(); y <= maxBuildY(); y++) {
			cells.remove(key(x, y, z));
		}
		for (int y = top - 12; y <= top; y++) {
			put(x, y, z, y == top ? "minecraft:grass_block[snowy=false]" : y > top - 3 ? "minecraft:dirt" : "minecraft:stone", null);
		}
		return this;
	}

	void put(int x, int y, int z, String state, @Nullable CompoundTag be) {
		if (StateKey.isAir(state) && be == null) {
			cells.remove(key(x, y, z));
		} else {
			cells.put(key(x, y, z), new Cell(state, be == null ? null : be.copy()));
		}
	}

	void unloadChunk(int cx, int cz) {
		unloadedChunks.add(((long) cx << 32) ^ (cz & 0xffffffffL));
	}

	void entity(int x, int y, int z, String what) {
		entities.put(key(x, y, z), what + "@" + x + "," + y + "," + z);
	}

	// ---- BlockWorld ---------------------------------------------------------------------------

	@Override
	public String state(int x, int y, int z) {
		Cell c = cells.get(key(x, y, z));
		return c == null ? StateKey.AIR : c.state;
	}

	@Override
	public @Nullable CompoundTag blockEntity(int x, int y, int z) {
		Cell c = cells.get(key(x, y, z));
		return c == null || c.be == null ? null : c.be.copy();
	}

	@Override
	public void set(int x, int y, int z, String state, @Nullable CompoundTag be) {
		writes++;
		put(x, y, z, state, be);
	}

	@Override
	public int minBuildY() {
		return -64;
	}

	@Override
	public int maxBuildY() {
		return 319;
	}

	// ---- TerrainProbe -------------------------------------------------------------------------

	@Override
	public boolean loaded(int x, int z) {
		return !unloadedChunks.contains(((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL));
	}

	@Override
	public int surfaceY(int x, int z) {
		for (int y = maxBuildY(); y >= minBuildY(); y--) {
			if (cells.containsKey(key(x, y, z))) {
				return y;
			}
		}
		return minBuildY();
	}

	@Override
	public Kind kind(int x, int y, int z) {
		String n = StateKey.name(state(x, y, z));
		if (StateKey.isAir(n)) {
			return Kind.AIR;
		}
		if (n.equals("minecraft:water")) {
			return Kind.WATER;
		}
		if (n.equals("minecraft:lava")) {
			return Kind.LAVA;
		}
		if (n.endsWith("_log") || n.endsWith("_leaves")) {
			return Kind.TREE;
		}
		if (n.equals("minecraft:short_grass") || n.equals("minecraft:poppy")) {
			return Kind.PLANT;
		}
		if (n.equals("minecraft:grass_block") || n.equals("minecraft:dirt") || n.equals("minecraft:stone") || n.equals("minecraft:sand")) {
			return Kind.GROUND;
		}
		return Kind.ARTIFICIAL;
	}

	@Override
	public String describe(int x, int y, int z) {
		return StateKey.name(state(x, y, z)).replace("minecraft:", "");
	}

	@Override
	public List<String> foreignEntities(Box box) {
		List<String> out = new ArrayList<>();
		for (Map.Entry<Long, String> e : entities.entrySet()) {
			String[] p = e.getValue().substring(e.getValue().indexOf('@') + 1).split(",");
			if (box.contains(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]))) {
				out.add(e.getValue());
			}
		}
		return out;
	}

	static CompoundTag chest(String item, int count) {
		CompoundTag t = new CompoundTag();
		t.putString("id", "minecraft:chest");
		CompoundTag it = new CompoundTag();
		it.putString("id", item);
		it.putInt("count", count);
		net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag();
		items.add(it);
		t.put("Items", items);
		return t;
	}
}
