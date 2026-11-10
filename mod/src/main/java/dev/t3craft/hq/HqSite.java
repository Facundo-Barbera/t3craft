package dev.t3craft.hq;

import dev.t3craft.hq.plan.Anchor;
import dev.t3craft.hq.plan.AnchorNames;
import dev.t3craft.hq.plan.BuildPlan;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

/**
 * Where an HQ plan stands in the world: the world cell of the plan origin and the rotation applied
 * around it. Uses exactly the transform of {@code dev.t3craft.build.BuildJob}: a relative cell
 * {@code rel} lands at {@code origin + rel.rotate(rotation)} and its state is
 * {@code state.rotate(rotation)}. So a builder given {@code (origin, rotation)} and
 * {@link HqBuild#toBuildPlan} builds the HQ where this site says, and {@link #anchor} gives the
 * matching world anchors.
 *
 * <p>The unrotated HQ has its entrance on the south ({@code +z}) and grows north ({@code -z}).
 * {@link #facing} picks the rotation that turns the entrance towards a given world direction.
 */
public record HqSite(BlockPos origin, Rotation rotation) {
	public HqSite {
		origin = origin.immutable();
		Objects.requireNonNull(rotation, "rotation");
	}

	/** Rotation that makes the HQ entrance face {@code entrance} (horizontal). */
	public static Rotation facing(Direction entrance) {
		return switch (entrance) {
			case SOUTH -> Rotation.NONE;
			case WEST -> Rotation.CLOCKWISE_90;
			case NORTH -> Rotation.CLOCKWISE_180;
			case EAST -> Rotation.COUNTERCLOCKWISE_90;
			default -> throw new IllegalArgumentException("entrance must be horizontal: " + entrance);
		};
	}

	/**
	 * The site whose anchor {@code anchorName} of {@code plan} stands on {@code world} with the entrance
	 * facing {@code entrance}. Typical use: {@code at(plan, AnchorNames.SPAWN, chosenCell, Direction.SOUTH)}.
	 */
	public static HqSite at(BuildPlan plan, String anchorName, BlockPos world, Direction entrance) {
		Anchor a = Objects.requireNonNull(plan.anchor(anchorName), anchorName);
		Rotation r = facing(entrance);
		return new HqSite(world.subtract(a.block().rotate(r)), r);
	}

	/** World cell of a relative cell. */
	public BlockPos cell(BlockPos rel) {
		return origin.offset(rel.rotate(rotation));
	}

	/** World direction of a relative direction. */
	public Direction direction(Direction rel) {
		return rotation.rotate(rel);
	}

	/**
	 * World point of a relative point. Cells rotate about their own corner, so continuous points
	 * rotate about {@code (0.5, 0.5)}: the centre of cell {@code c} lands on the centre of {@code cell(c)}.
	 */
	public double[] point(double x, double y, double z) {
		double rx;
		double rz;
		switch (rotation) {
			case CLOCKWISE_90 -> {
				rx = 1.0 - z;
				rz = x;
			}
			case CLOCKWISE_180 -> {
				rx = 1.0 - x;
				rz = 1.0 - z;
			}
			case COUNTERCLOCKWISE_90 -> {
				rx = z;
				rz = 1.0 - x;
			}
			default -> {
				rx = x;
				rz = z;
			}
		}
		return new double[] {origin.getX() + rx, origin.getY() + y, origin.getZ() + rz};
	}

	/** The anchor in world coordinates (same name; point, facing and block transformed). */
	public Anchor anchor(Anchor rel) {
		double[] p = point(rel.x(), rel.y(), rel.z());
		return new Anchor(rel.name(), p[0], p[1], p[2], direction(rel.facing()), cell(rel.block()));
	}
}
