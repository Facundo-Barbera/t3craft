package dev.t3craft.mapgen;

/** An inclusive xz rectangle of block columns. */
public record Area(int minX, int minZ, int maxX, int maxZ) {
	public Area {
		if (minX > maxX || minZ > maxZ) {
			throw new IllegalArgumentException("empty area " + minX + "," + minZ + " .. " + maxX + "," + maxZ);
		}
	}

	public boolean contains(int x, int z) {
		return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
	}

	public boolean intersects(Area o) {
		return minX <= o.maxX && maxX >= o.minX && minZ <= o.maxZ && maxZ >= o.minZ;
	}

	public Area union(Area o) {
		return new Area(Math.min(minX, o.minX), Math.min(minZ, o.minZ), Math.max(maxX, o.maxX), Math.max(maxZ, o.maxZ));
	}
}
