package dev.t3craft.mapgen.islands;

import dev.t3craft.build.Placement;
import dev.t3craft.mapgen.Area;
import dev.t3craft.mapgen.MapLayout;
import dev.t3craft.mapgen.Noise;
import dev.t3craft.mapgen.PieceSource;
import dev.t3craft.mapgen.SlotPlacer;
import dev.t3craft.mapgen.TerrainShape;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The blocks of an island: the ground ({@link #terrain}) and the pieces of its slots ({@link #build}). Map
 * coordinates, no randomness outside {@link Noise}. Placement order matters (the runtime places the list as
 * is, with block updates): the solid ground goes bottom layer first, then what hangs under it (roots, dripstone
 * tips: their support is already there), then the plants on top, then the pieces.
 */
public final class IslandBlocks {
	private IslandBlocks() {
	}

	/** The ground block at {@code y} of a column whose solid part is {@code lowest..surf} and whose underside starts at {@code bottom}. */
	static BlockState ground(Island i, int x, int y, int z, int surf, int bottom) {
		IslandBiome b = i.biome();
		long s = i.seed();
		if (y == surf) {
			return b.topAlt != null && Noise.hash(s, x, z, 12, 5) == 0 ? b.topAlt : b.top;
		}
		if (y >= surf - 2) {
			return b.subAlt != null && Noise.hash(s, x, z * 31 + y, 13, 6) == 0 ? b.subAlt : b.sub;
		}
		if (y <= bottom) {
			return Noise.hash(s, x, z * 31 + y, 14, 4) == 0 ? b.underAlt : b.under;
		}
		BlockState[] bands = b.bands;
		if (bands != null) {
			int wave = (int) Math.round(IslandNoise.smooth(s, x, z, 9.0, 15) * 2);
			return bands[Math.floorMod(y + wave, bands.length)];
		}
		return b.bodyAlt != null && Noise.hash(s, x, z * 31 + y, 16, 5) == 0 ? b.bodyAlt : b.body;
	}

	/** The ground of {@code i}: solid blocks lowest layer first, then the hanging tips, then the plants. */
	public static List<Placement> terrain(Island i) {
		List<Placement> solid = new ArrayList<>();
		List<Placement> tips = new ArrayList<>();
		List<Placement> plants = new ArrayList<>();
		IslandBiome b = i.biome();
		Area a = i.area();
		for (int x = a.minX(); x <= a.maxX(); x++) {
			for (int z = a.minZ(); z <= a.maxZ(); z++) {
				if (!i.contains(x, z, 0)) {
					continue;
				}
				int surf = i.surfaceY(x, z);
				int bottom = i.bottom(x, z);
				int lowest = bottom - i.hang(x, z);
				for (int y = lowest; y <= surf; y++) {
					solid.add(new Placement(new BlockPos(x, y, z), ground(i, x, y, z, surf, bottom)));
				}
				BlockState tip = b.tip;
				if (tip != null && (lowest < bottom ? Noise.hash(i.seed(), x, z, 17, 5) < 3 : Noise.hash(i.seed(), x, z, 18, 14) == 0)) {
					tips.add(new Placement(new BlockPos(x, lowest - 1, z), tip));
				}
				BlockState plant = plant(i, x, z);
				if (plant != null) {
					plants.add(new Placement(new BlockPos(x, surf + 1, z), plant));
				}
			}
		}
		solid.sort(Comparator.comparingInt((Placement p) -> p.pos().getY()));
		solid.addAll(tips);
		solid.addAll(plants);
		return solid;
	}

	/** A plant for the top of the column, or null: never on a slot's pad, never on the outermost ring, never on an off-palette top block. */
	private static @Nullable BlockState plant(Island i, int x, int z) {
		IslandBiome b = i.biome();
		if (b.flora.isEmpty() || Noise.hash(i.seed(), x, z, 19, b.floraEvery) != 0 || !i.contains(x, z, -1.0)) {
			return null;
		}
		for (Island.Pad p : i.pads()) {
			Area a = p.area();
			if (x >= a.minX() - 1 && x <= a.maxX() + 1 && z >= a.minZ() - 1 && z <= a.maxZ() + 1) {
				return null;
			}
		}
		if (b.topAlt != null && Noise.hash(i.seed(), x, z, 12, 5) == 0) {
			return null; // the alternative top block (cobble on a mossy island) carries nothing
		}
		return b.flora.get(Noise.hash(i.seed(), x, z, 20, b.flora.size()));
	}

	/** The ground of {@code i} followed by the pieces {@code source} puts in its slots. */
	public static List<Placement> build(IslandField field, Island i, PieceSource source) {
		List<Placement> out = terrain(i);
		MapLayout layout = new MapLayout(IslandField.ID, field.seed(), new SingleShape(i), field.slots(i), List.of(), i.surface());
		SlotPlacer.fill(layout, source).values().forEach(out::addAll);
		return out;
	}

	/** The ground of one island as a {@link TerrainShape}. */
	private record SingleShape(Island island) implements TerrainShape {
		@Override
		public Area bounds() {
			return island.area();
		}

		@Override
		public @Nullable Span column(int x, int z) {
			return island.contains(x, z, 0) ? new Span(island.bottom(x, z) - island.hang(x, z), island.surfaceY(x, z)) : null;
		}
	}
}
