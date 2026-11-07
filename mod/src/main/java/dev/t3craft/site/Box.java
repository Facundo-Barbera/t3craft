package dev.t3craft.site;

/**
 * Inclusive axis-aligned block box. Cell order (used by snapshots and plans) is y, then z, then x:
 * {@code index = ((y - minY) * sizeZ + (z - minZ)) * sizeX + (x - minX)}, so iterating indices goes
 * bottom-up, layer by layer.
 */
public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
	public Box {
		if (maxX < minX || maxY < minY || maxZ < minZ) {
			throw new IllegalArgumentException("empty box " + minX + "," + minY + "," + minZ + " .. " + maxX + "," + maxY + "," + maxZ);
		}
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

	public boolean contains(int x, int y, int z) {
		return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
	}

	public boolean containsXZ(int x, int z) {
		return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
	}

	public boolean intersects(Box o) {
		return minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY && minZ <= o.maxZ && maxZ >= o.minZ;
	}

	public boolean intersectsXZ(Box o) {
		return minX <= o.maxX && maxX >= o.minX && minZ <= o.maxZ && maxZ >= o.minZ;
	}

	public int index(int x, int y, int z) {
		return ((y - minY) * sizeZ() + (z - minZ)) * sizeX() + (x - minX);
	}

	public int xOf(int index) {
		return minX + index % sizeX();
	}

	public int zOf(int index) {
		return minZ + (index / sizeX()) % sizeZ();
	}

	public int yOf(int index) {
		return minY + index / (sizeX() * sizeZ());
	}

	/** Number of 16x16 chunk columns the box touches. */
	public int chunkCount() {
		return ((maxX >> 4) - (minX >> 4) + 1) * ((maxZ >> 4) - (minZ >> 4) + 1);
	}

	public Box grow(int dxz, int dy) {
		return new Box(minX - dxz, minY - dy, minZ - dxz, maxX + dxz, maxY + dy, maxZ + dxz);
	}

	public int[] toArray() {
		return new int[] {minX, minY, minZ, maxX, maxY, maxZ};
	}

	public static Box fromArray(int[] a) {
		if (a == null || a.length != 6) {
			throw new IllegalArgumentException("box needs 6 ints");
		}
		return new Box(a[0], a[1], a[2], a[3], a[4], a[5]);
	}

	public String shortString() {
		return minX + " " + minY + " " + minZ + " → " + maxX + " " + maxY + " " + maxZ;
	}
}
