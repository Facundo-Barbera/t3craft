package dev.t3craft.mapgen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * What a {@link MapGenerator} produces: the {@link TerrainShape}, the {@link Slot}s where pieces go and the
 * {@link Link}s between landmasses. Pure data, relative to the map origin; no blocks.
 *
 * @param generatorId id of the generator that made it ({@link MapGenerator#id()})
 * @param spawn       where a player first stands (feet), in map coordinates
 */
public record MapLayout(String generatorId, long seed, TerrainShape shape, List<Slot> slots, List<Link> links, BlockPos spawn) {

	/** How two groups (islands, districts) connect. */
	public enum LinkKind {
		/** Already walkable: the groups are one landmass. */
		SOLID,
		/** No path exists until a player gets close; the world then builds it (a bridge). */
		ON_DEMAND
	}

	public record Link(String from, String to, LinkKind kind) {
	}

	public MapLayout {
		Objects.requireNonNull(generatorId, "generatorId");
		Objects.requireNonNull(shape, "shape");
		slots = List.copyOf(slots);
		links = List.copyOf(links);
		spawn = spawn.immutable();
		Map<String, Slot> seen = new LinkedHashMap<>();
		for (Slot s : slots) {
			if (seen.put(s.id(), s) != null) {
				throw new IllegalArgumentException("two slots with id " + s.id());
			}
		}
	}

	public @Nullable Slot slot(String id) {
		for (Slot s : slots) {
			if (s.id().equals(id)) {
				return s;
			}
		}
		return null;
	}

	public List<Slot> slotsOf(Slot.Role role) {
		List<Slot> out = new ArrayList<>();
		for (Slot s : slots) {
			if (s.role() == role) {
				out.add(s);
			}
		}
		return out;
	}

	/** The slots whose {@link Slot#parent()} is {@code id}. */
	public List<Slot> childrenOf(String id) {
		List<Slot> out = new ArrayList<>();
		for (Slot s : slots) {
			if (id.equals(s.parent())) {
				out.add(s);
			}
		}
		return out;
	}

	public List<Slot> slotsIn(String group) {
		List<Slot> out = new ArrayList<>();
		for (Slot s : slots) {
			if (s.group().equals(group)) {
				out.add(s);
			}
		}
		return out;
	}
}
