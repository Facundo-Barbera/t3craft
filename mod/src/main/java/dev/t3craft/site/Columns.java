package dev.t3craft.site;

import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Per-column terrain facts, scanned lazily from a {@link TerrainProbe} and cached for one evaluation pass. */
public final class Columns {
	/**
	 * @param top ground top y (or the fluid surface when {@code fluid} is set)
	 * @param fluid WATER or LAVA at the surface or within {@link SiteLimits#SCAN_BELOW} below the ground, else null
	 * @param artificial first artificial block found ("oak_planks at 10 64 3"), else null
	 * @param trees log/leaf blocks above the ground
	 */
	public record Column(boolean loaded, int top, TerrainProbe.@Nullable Kind fluid, @Nullable String artificial, int trees) {
		static final Column UNLOADED = new Column(false, Integer.MIN_VALUE, null, null, 0);
	}

	private final TerrainProbe probe;
	private final Map<Long, Column> cache = new HashMap<>();

	public Columns(TerrainProbe probe) {
		this.probe = probe;
	}

	public TerrainProbe probe() {
		return probe;
	}

	public Column at(int x, int z) {
		return cache.computeIfAbsent(((long) x << 32) ^ (z & 0xffffffffL), k -> scan(x, z));
	}

	private Column scan(int x, int z) {
		if (!probe.loaded(x, z)) {
			return Column.UNLOADED;
		}
		int min = probe.minBuildY();
		int y = Math.min(probe.surfaceY(x, z), probe.maxBuildY());
		int trees = 0;
		String artificial = null;
		TerrainProbe.Kind fluid = null;
		int top = min;
		for (; y >= min; y--) {
			TerrainProbe.Kind k = probe.kind(x, y, z);
			if (k == TerrainProbe.Kind.AIR || k == TerrainProbe.Kind.PLANT) {
				continue;
			}
			if (k == TerrainProbe.Kind.TREE) {
				trees++;
				continue;
			}
			if (k == TerrainProbe.Kind.ARTIFICIAL) {
				if (artificial == null) {
					artificial = probe.describe(x, y, z) + " at " + x + " " + y + " " + z;
				}
				continue;
			}
			if (k == TerrainProbe.Kind.WATER || k == TerrainProbe.Kind.LAVA) {
				fluid = k;
			}
			top = y;
			break;
		}
		for (int yy = top - 1; yy >= Math.max(min, top - SiteLimits.SCAN_BELOW); yy--) {
			TerrainProbe.Kind k = probe.kind(x, yy, z);
			if (k == TerrainProbe.Kind.LAVA || (k == TerrainProbe.Kind.WATER && fluid == null)) {
				fluid = k;
			} else if (k == TerrainProbe.Kind.ARTIFICIAL && artificial == null) {
				artificial = probe.describe(x, yy, z) + " at " + x + " " + yy + " " + z;
			}
		}
		return new Column(true, top, fluid, artificial, trees);
	}
}
