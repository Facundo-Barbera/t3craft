// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.blocks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * One screen made of monitors: a {@code width × height} rectangle of {@code t3craft:monitor} blocks
 * that all look along {@code facing}. A lone monitor is a 1×1 extent. This is what the renderer
 * world-side geometry of a screen; the client turns it into the renderer's
 * {@code dev.t3craft.client.blocks.ScreenExtent} (size + "this block draws it"), whose drawing origin is
 * the <b>bottom-left</b> cell ({@link #bottomLeft()}); the other cells draw nothing.
 *
 * <p>Coordinates are as seen by a viewer standing in front of the screen: {@link #origin} is the
 * <b>top-left</b> cell (the anchor of a plan's {@code bigscreen_*}), columns grow to the viewer's right
 * ({@link #right()}) and rows grow downward.
 *
 * <p>Pure value; adapted from AgentCraft's {@code PanelBlock} ({@code left = facing.getClockWise()},
 * {@code right = facing.getCounterClockWise()}).
 */
public record ScreenRect(BlockPos origin, Direction facing, int width, int height) {
	public ScreenRect {
		origin = origin.immutable();
		if (!facing.getAxis().isHorizontal()) {
			throw new IllegalArgumentException("screen facing must be horizontal: " + facing);
		}
		if (width < 1 || height < 1) {
			throw new IllegalArgumentException("empty screen: " + width + "x" + height);
		}
	}

	/** A lone monitor. */
	public static ScreenRect single(BlockPos pos, Direction facing) {
		return new ScreenRect(pos, facing, 1, 1);
	}

	/** The viewer's right, along which columns grow. */
	public Direction right() {
		return facing.getCounterClockWise();
	}

	/** The viewer's left. */
	public Direction left() {
		return facing.getClockWise();
	}

	public boolean isMulti() {
		return width > 1 || height > 1;
	}

	public int cellCount() {
		return width * height;
	}

	/** The cell at column {@code col} (0 = left) and row {@code row} (0 = top). */
	public BlockPos cell(int col, int row) {
		return origin.relative(right(), col).below(row);
	}

	/** Column of {@code p} (may be outside {@code [0, width)} when {@code p} is not in the screen). */
	public int col(BlockPos p) {
		Direction r = right();
		return (p.getX() - origin.getX()) * r.getStepX() + (p.getZ() - origin.getZ()) * r.getStepZ();
	}

	/** Row of {@code p} (0 = top). */
	public int row(BlockPos p) {
		return origin.getY() - p.getY();
	}

	public boolean contains(BlockPos p) {
		int c = col(p);
		int r = row(p);
		boolean inPlane = facing.getAxis() == Direction.Axis.Z ? p.getZ() == origin.getZ() : p.getX() == origin.getX();
		return inPlane && c >= 0 && c < width && r >= 0 && r < height;
	}

	/** The bottom-left cell as seen by the viewer: where the renderer draws the whole screen from. */
	public BlockPos bottomLeft() {
		return cell(0, height - 1);
	}

	/** True for the cell that draws the screen ({@link #bottomLeft()}). */
	public boolean isDrawOrigin(BlockPos p) {
		return bottomLeft().equals(p);
	}

	/** Every cell, row by row from the top-left (origin first). */
	public List<BlockPos> cells() {
		List<BlockPos> out = new ArrayList<>(cellCount());
		for (int r = 0; r < height; r++) {
			for (int c = 0; c < width; c++) {
				out.add(cell(c, r));
			}
		}
		return out;
	}

	/** Which neighbours of {@code p} belong to this screen (the bezel is drawn on the other sides). */
	public Sides sides(BlockPos p) {
		int c = col(p);
		int r = row(p);
		return new Sides(r > 0, r < height - 1, c > 0, c < width - 1);
	}

	/** The same screen moved by {@code offset} (plan-relative → world). */
	public ScreenRect offset(BlockPos offset) {
		return new ScreenRect(origin.offset(offset), facing, width, height);
	}

	/** Connections of one cell, as seen by the viewer (true = the neighbour on that side is in the same screen). */
	public record Sides(boolean up, boolean down, boolean left, boolean right) {
		public static final Sides NONE = new Sides(false, false, false, false);
	}
}
