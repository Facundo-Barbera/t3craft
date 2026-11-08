package dev.t3craft.hq;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * The "Acrópolis en terrazas" plan for tools and whoever places it (layout {@code hybrid-terraces}):
 * one {@value TerraceTown#CELL}×{@value TerraceTown#CELL} square per built cell, HQ coordinates
 * (origin = the middle of the great plaza at {@code y = 0}, the natural ground; the acropolis floor
 * is at {@link #hubFloor()}). Each build group is exactly one cell, so builders can claim, clear and
 * build cells independently.
 */
public final class TerraceFootprints {
	private TerraceFootprints() {
	}

	/** Floor y of the acropolis (plaza, temple, stoas, propylaea) and the sky islands. */
	public static int hubFloor() {
		return TerraceTown.floorY(TerraceTown.TOP_LEVEL);
	}

	/** Cell size in blocks. */
	public static int cell() {
		return TerraceTown.CELL;
	}

	/** The square of cell (i, j). */
	public static HqFootprints.Area cellArea(int i, int j) {
		int h = TerraceTown.HALF;
		return new HqFootprints.Area(TerraceTown.centreX(i) - h, TerraceTown.centreZ(j) - h, TerraceTown.centreX(i) + h, TerraceTown.centreZ(j) + h);
	}

	/** Floor y of the terrace under block column (x, z) in the fixed plan, 0 outside it. */
	public static int floorAt(int x, int z) {
		TerraceTown.Cell c = TerraceTown.get().cell(TerraceTown.cellOf(x), TerraceTown.cellOf(z));
		return c == null ? 0 : c.floor();
	}

	/** The square of the solar with this rank, or null. */
	public static HqFootprints.@Nullable Area plotArea(int rank) {
		TerraceTown t = TerraceTown.get();
		if (rank < 0 || rank >= t.plots.size()) {
			return null;
		}
		return TerraceTown.area(t.plots.get(rank));
	}

	/** Number of solares in the fixed plan. */
	public static int plots() {
		return TerraceTown.get().plots.size();
	}

	/** The xz area a layout's plan lies in (the same as the style's envelope). */
	public static HqFootprints.Area envelope(HqLayout layout) {
		return TerraceTown.get().envelope(TerraceStyle.plots(layout));
	}

	/**
	 * Block light of every cell an NPC can reach from the spawn (feet cells), for light heatmaps.
	 * Same rules as the generator and the tests ({@link PlanNav}, {@link TerraceLight}).
	 */
	public static Map<BlockPos, Integer> walkableLight(BuildPlan plan) {
		Map<Long, net.minecraft.world.level.block.state.BlockState> m = new HashMap<>(plan.size() * 2);
		for (Placement p : plan.placements()) {
			m.put(p.rel().asLong(), p.state());
		}
		TerraceLight light = new TerraceLight(m::get);
		light.addAll(m);
		Anchor spawn = plan.anchor(AnchorNames.SPAWN);
		Map<BlockPos, Integer> out = new HashMap<>();
		if (spawn == null) {
			return out;
		}
		Set<BlockPos> reach = new PlanNav(m::get).reachable(spawn.block());
		for (BlockPos p : reach) {
			out.put(p, light.at(p));
		}
		return out;
	}
}
