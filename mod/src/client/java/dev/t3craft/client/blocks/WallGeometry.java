// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * World ⇄ board coordinates of a thread wall. The anchor block is the wall's bottom-left cell as
 * seen from the front; the wall extends {@code width} blocks to the viewer's right
 * ({@code facing.getCounterClockWise()}) and {@code height} blocks up, on the plane of the anchor's
 * front face. Board pixels: x right, y down from the wall's top-left corner, {@code ppb} per block.
 * Pure. The inverse of {@code MonitorRenderer.toFace} plus a translate of {@code height - 1} blocks,
 * as AgentCraft's {@code TaskWallFeature.toBoard} does for its panels.
 */
public final class WallGeometry {
	/** Hits farther than this off the face plane are not on the wall (blocks). */
	static final double PLANE_TOLERANCE = 0.05;

	private WallGeometry() {
	}

	/** Board point {x, y} of world point {@code (hx, hy, hz)} on the wall anchored at {@code origin}. */
	public static float[] toBoard(BlockPos origin, Direction facing, int height, float ppb, double hx, double hy, double hz) {
		Direction right = facing.getCounterClockWise();
		double u = (hx - (origin.getX() + 0.5)) * right.getStepX() + (hz - (origin.getZ() + 0.5)) * right.getStepZ();
		float x = (float) ((u + 0.5) * ppb);
		float y = (float) ((height - (hy - origin.getY())) * ppb);
		return new float[] {x, y};
	}

	/** Distance of a world point from the wall's face plane (signed: positive in front). */
	public static double offPlane(BlockPos origin, Direction facing, double hx, double hz) {
		double face = 0.5;
		return (hx - (origin.getX() + 0.5)) * facing.getStepX() + (hz - (origin.getZ() + 0.5)) * facing.getStepZ() - face;
	}

	/** True when {@code pos} is one of the wall's cells (same plane as the anchor). */
	public static boolean onWall(BlockPos origin, Direction facing, int width, int height, BlockPos pos) {
		Direction right = facing.getCounterClockWise();
		int dx = pos.getX() - origin.getX();
		int dz = pos.getZ() - origin.getZ();
		int along = dx * right.getStepX() + dz * right.getStepZ();
		int depth = dx * facing.getStepX() + dz * facing.getStepZ();
		int up = pos.getY() - origin.getY();
		return depth == 0 && along >= 0 && along < width && up >= 0 && up < height;
	}

	/** World centre of the wall's face (for the LOD distance). */
	public static double[] center(BlockPos origin, Direction facing, int width, int height) {
		Direction right = facing.getCounterClockWise();
		double along = (width - 1) / 2.0;
		return new double[] {
				origin.getX() + 0.5 + right.getStepX() * along + facing.getStepX() * 0.5,
				origin.getY() + height / 2.0,
				origin.getZ() + 0.5 + right.getStepZ() * along + facing.getStepZ() * 0.5};
	}

	/** Level of detail by squared distance from the camera to the wall centre. */
	public enum Lod {
		/** Text, badges, hover. */
		FULL,
		/** Lanes and coloured card blocks, no text (cheap, readable as a heat map). */
		SIMPLE,
		/** Beyond the view distance: nothing. */
		HIDDEN;

		public static Lod of(double distSq, double detailDistance, double viewDistance) {
			if (distSq <= detailDistance * detailDistance) {
				return FULL;
			}
			return distSq <= viewDistance * viewDistance ? SIMPLE : HIDDEN;
		}
	}
}
