package dev.t3craft.hq.plan;

import net.minecraft.core.BlockPos;

/** An inclusive integer box of block cells. */
public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
	public Box {
		if (minX > maxX || minY > maxY || minZ > maxZ) {
			throw new IllegalArgumentException("empty box " + minX + "," + minY + "," + minZ + " .. " + maxX + "," + maxY + "," + maxZ);
		}
	}

	/** The box spanning two corners in any order. */
	public static Box of(int x1, int y1, int z1, int x2, int y2, int z2) {
		return new Box(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
	}

	public boolean contains(int x, int y, int z) {
		return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
	}

	public boolean contains(BlockPos p) {
		return contains(p.getX(), p.getY(), p.getZ());
	}

	public boolean contains(Box o) {
		return contains(o.minX, o.minY, o.minZ) && contains(o.maxX, o.maxY, o.maxZ);
	}

	public boolean intersects(Box o) {
		return minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY && minZ <= o.maxZ && maxZ >= o.minZ;
	}

	public Box union(Box o) {
		return new Box(Math.min(minX, o.minX), Math.min(minY, o.minY), Math.min(minZ, o.minZ),
				Math.max(maxX, o.maxX), Math.max(maxY, o.maxY), Math.max(maxZ, o.maxZ));
	}

	public int sizeX() {
		return maxX - minX + 1;
	}

	public int sizeY() {
		return maxY - minY + 1;
	}

	public int sizeZ() {
		return maxZ - minZ + 1;
	}

	public long volume() {
		return (long) sizeX() * sizeY() * sizeZ();
	}

	@Override
	public String toString() {
		return "[" + minX + "," + minY + "," + minZ + " .. " + maxX + "," + maxY + "," + maxZ + "]";
	}
}
