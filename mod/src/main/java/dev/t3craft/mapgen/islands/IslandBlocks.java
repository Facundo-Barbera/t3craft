package dev.t3craft.mapgen.islands;

import dev.t3craft.build.Placement;
import dev.t3craft.mapgen.Area;
import dev.t3craft.mapgen.MapLayout;
import dev.t3craft.mapgen.Noise;
import dev.t3craft.mapgen.PieceSource;
import dev.t3craft.mapgen.SlotPlacer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The blocks of an island: the ground ({@link #terrain}, bottom layer first so every block has support below
 * it as it is placed) and the pieces of its slots ({@link #build}). Map coordinates, no randomness.
 */
public final class IslandBlocks {
	private IslandBlocks() {
	}

	/** Grass on top, two dirt, then stone with tuff speckles and a tuff bottom. */
	static BlockState ground(Island i, int x, int y, int z, int bottom) {
		if (y == i.top()) {
			return Blocks.GRASS_BLOCK.defaultBlockState();
		}
		if (y >= i.top() - 2) {
			return Blocks.DIRT.defaultBlockState();
		}
		if (y == bottom || Noise.hash(i.seed(), x, z * 31 + y, 12, 5) == 0) {
			return Blocks.TUFF.defaultBlockState();
		}
		return Blocks.STONE.defaultBlockState();
	}

	/** The ground of {@code i}, lowest layer first. */
	public static List<Placement> terrain(Island i) {
		List<Placement> out = new ArrayList<>();
		Area a = i.area();
		for (int x = a.minX(); x <= a.maxX(); x++) {
			for (int z = a.minZ(); z <= a.maxZ(); z++) {
				if (!i.contains(x, z, 0)) {
					continue;
				}
				int bottom = i.top() - i.depth(x, z) + 1;
				for (int y = bottom; y <= i.top(); y++) {
					out.add(new Placement(new BlockPos(x, y, z), ground(i, x, y, z, bottom)));
				}
			}
		}
		out.sort(Comparator.comparingInt((Placement p) -> p.pos().getY()));
		return out;
	}

	/** The ground of {@code i} followed by the pieces {@code source} puts in its slots. */
	public static List<Placement> build(IslandField field, Island i, PieceSource source) {
		List<Placement> out = terrain(i);
		MapLayout layout = new MapLayout(IslandField.ID, field.seed(), new SingleShape(i), field.slots(i), List.of(), i.surface());
		SlotPlacer.fill(layout, source).values().forEach(out::addAll);
		return out;
	}

	/** The ground of one island as a {@link dev.t3craft.mapgen.TerrainShape}. */
	private record SingleShape(Island island) implements dev.t3craft.mapgen.TerrainShape {
		@Override
		public Area bounds() {
			return island.area();
		}

		@Override
		public @Nullable Span column(int x, int z) {
			return island.contains(x, z, 0) ? new Span(island.top() - island.depth(x, z) + 1, island.top()) : null;
		}
	}
}
