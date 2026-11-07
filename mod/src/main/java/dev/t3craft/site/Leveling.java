package dev.t3craft.site;

/**
 * Terrain shaping for an evaluated site, as a {@link CellPlan} over the site box:
 * <ul>
 *   <li><b>footprint</b>: a solid pad at {@code floorY} (stone bricks on the edge, polished andesite
 *   inside); columns lower than the pad get a foundation up to it (stone-brick retaining wall on the
 *   edge, cobblestone inside); everything above the pad, up to the top of the box, is cleared;</li>
 *   <li><b>apron</b> (blend ring): each column's ground is moved part of the way towards the pad
 *   (more next to the footprint, less outside), topped with grass, filled with dirt or cut to air,
 *   so the base sits in the terrain instead of on a cliff or in a pit.</li>
 * </ul>
 * Cells below each column's ground that need no change are left untouched (null).
 */
public final class Leveling {
	public static final String PAD_EDGE = "minecraft:stone_bricks";
	public static final String PAD = "minecraft:polished_andesite";
	public static final String WALL = "minecraft:stone_bricks";
	public static final String CORE = "minecraft:cobblestone";
	public static final String GRASS = "minecraft:grass_block[snowy=false]";
	public static final String DIRT = "minecraft:dirt";

	private Leveling() {
	}

	public static CellPlan plan(Columns cols, SiteEvaluator.Evaluation e, CellPlan.Facing facing) {
		Box box = e.box();
		if (box == null || !e.ok()) {
			throw new IllegalArgumentException("cannot level a refused site");
		}
		Box fp = e.footprint();
		int floor = e.floorY();
		CellPlan plan = new CellPlan(box, new CellPlan.Origin(e.centerX(), floor + 1, e.centerZ(), facing));
		int a = SiteLimits.APRON;
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				Columns.Column col = cols.at(x, z);
				int top = col.top();
				if (fp.containsXZ(x, z)) {
					boolean edge = x == fp.minX() || x == fp.maxX() || z == fp.minZ() || z == fp.maxZ();
					for (int y = Math.max(box.minY(), top + 1); y < floor; y++) {
						plan.set(x, y, z, edge ? WALL : CORE);
					}
					plan.set(x, floor, z, edge ? PAD_EDGE : PAD);
					for (int y = floor + 1; y <= box.maxY(); y++) {
						plan.set(x, y, z, StateKey.AIR);
					}
					continue;
				}
				// apron ring: distance 1..a from the footprint
				int d = Math.max(Math.max(fp.minX() - x, x - fp.maxX()), Math.max(fp.minZ() - z, z - fp.maxZ()));
				int target = top + (int) Math.round((floor - top) * (double) (a + 1 - d) / (a + 1));
				target = Math.max(box.minY() + 1, Math.min(target, box.maxY() - 1));
				// trunks standing in the ring are cleared to the top of the box, plants just above the ground
				int clearTo = col.trees() > 0 ? box.maxY() : Math.min(box.maxY(), Math.max(top, target) + 2);
				if (target > top) {
					for (int y = top + 1; y < target; y++) {
						plan.set(x, y, z, DIRT);
					}
					plan.set(x, target, z, GRASS);
					// a plant that stood on the old ground is buried, anything above the new top cleared
					for (int y = target + 1; y <= clearTo; y++) {
						plan.set(x, y, z, StateKey.AIR);
					}
				} else if (target < top) {
					plan.set(x, target, z, GRASS);
					for (int y = target + 1; y <= clearTo; y++) {
						plan.set(x, y, z, StateKey.AIR);
					}
				}
			}
		}
		return plan;
	}
}
