package dev.t3craft.site;

/**
 * Supplies what goes on the pad. The real base comes from {@code feat/hq-design}
 * ({@code dev.t3craft.hq}, which produces a BuildPlan + anchors); Taller adapts it to this
 * interface. Until then {@link #PLACEHOLDER} marks the corners.
 *
 * <p>Contract: return a plan whose box is {@code area} (footprint × the {@link SiteLimits#HEADROOM}
 * layers above the pad); {@code origin} is the pad centre one block above the pad, {@code facing}
 * the side the entrance should face (towards the player who chose the site). Cells left null keep
 * the leveling's air. No block entities.
 */
@FunctionalInterface
public interface StructureSource {
	CellPlan design(Box area, CellPlan.Origin origin);

	/** Four oak-log corner posts with a lantern on top, and a lantern at the front edge. */
	StructureSource PLACEHOLDER = (area, origin) -> {
		CellPlan p = new CellPlan(area, origin);
		int y = area.minY();
		int[][] corners = {{area.minX(), area.minZ()}, {area.maxX(), area.minZ()}, {area.minX(), area.maxZ()}, {area.maxX(), area.maxZ()}};
		for (int[] c : corners) {
			for (int dy = 0; dy < 3; dy++) {
				p.set(c[0], y + dy, c[1], "minecraft:oak_log[axis=y]");
			}
			p.set(c[0], y + 3, c[1], "minecraft:lantern[hanging=false,waterlogged=false]");
		}
		int fx = origin.x();
		int fz = origin.z();
		switch (origin.facing()) {
			case NORTH -> fz = area.minZ();
			case SOUTH -> fz = area.maxZ();
			case EAST -> fx = area.maxX();
			case WEST -> fx = area.minX();
		}
		p.set(fx, y, fz, "minecraft:lantern[hanging=false,waterlogged=false]");
		return p;
	};
}
