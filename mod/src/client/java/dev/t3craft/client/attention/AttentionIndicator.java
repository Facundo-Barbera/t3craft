package dev.t3craft.client.attention;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Geometry of the HUD edge indicator, pure (no Minecraft classes): from where the player stands and
 * looks, and where the thread's NPC is, to a point on the screen's edge and the arrow's direction.
 * Docs: {@code docs/features/attention-queue.md}.
 *
 * <p>Only the horizontal bearing is used (the citadel is mostly flat and a vertical hint is not worth
 * a camera projection). Minecraft's yaw is 0 facing +Z and grows clockwise seen from above, so a
 * target with a positive bearing is to the right.
 */
public final class AttentionIndicator {
	/** Nearer than this (blocks) there is nothing to point at. */
	public static final double MIN_DISTANCE = 3.0;
	/**
	 * Within this many degrees of straight ahead the NPC is in view (its floating name is readable), so
	 * the indicator hides instead of competing with it.
	 */
	public static final double HIDE_CONE_DEG = 28;

	private AttentionIndicator() {
	}

	/** Free space kept around the arrow: the status bar and the hotbar live there. */
	public record Insets(int left, int top, int right, int bottom) {
	}

	/**
	 * @param x      centre of the arrow, screen pixels
	 * @param y      centre of the arrow
	 * @param dirX   unit direction the arrow points to (screen: x right, y down)
	 * @param dirY   see {@code dirX}
	 * @param meters distance to the target, rounded
	 */
	public record Placement(int x, int y, double dirX, double dirY, int meters) {
	}

	/** Bearing of the target relative to where the player looks, degrees in (-180, 180], positive = to the right. */
	public static double bearing(double playerX, double playerZ, float playerYaw, double targetX, double targetZ) {
		double dx = targetX - playerX;
		double dz = targetZ - playerZ;
		double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
		double rel = targetYaw - playerYaw;
		rel = ((rel + 180) % 360 + 360) % 360 - 180;
		return rel == -180 ? 180 : rel;
	}

	/** Where to draw the arrow, or null when it should not show (target near or in view). */
	public static @Nullable Placement place(double playerX, double playerZ, float playerYaw, double targetX, double targetZ, int screenW,
			int screenH, Insets in) {
		double dist = Math.hypot(targetX - playerX, targetZ - playerZ);
		if (dist < MIN_DISTANCE) {
			return null;
		}
		double rel = bearing(playerX, playerZ, playerYaw, targetX, targetZ);
		if (Math.abs(rel) < HIDE_CONE_DEG) {
			return null;
		}
		double dirX = Math.sin(Math.toRadians(rel));
		double dirY = -Math.cos(Math.toRadians(rel));
		double cx = screenW / 2.0;
		double cy = screenH / 2.0;
		double t = Double.MAX_VALUE;
		if (dirX > 1e-6) {
			t = Math.min(t, (screenW - in.right() - cx) / dirX);
		} else if (dirX < -1e-6) {
			t = Math.min(t, (in.left() - cx) / dirX);
		}
		if (dirY > 1e-6) {
			t = Math.min(t, (screenH - in.bottom() - cy) / dirY);
		} else if (dirY < -1e-6) {
			t = Math.min(t, (in.top() - cy) / dirY);
		}
		t = Math.max(0, t == Double.MAX_VALUE ? 0 : t);
		return new Placement((int) Math.round(cx + dirX * t), (int) Math.round(cy + dirY * t), dirX, dirY, (int) Math.round(dist));
	}

	/**
	 * The arrow as horizontal runs, one per pixel row: {@code {y, x0, x1}} (x1 exclusive). A triangle
	 * of radius {@code size} centred on ({@code cx}, {@code cy}) pointing along ({@code dirX}, {@code dirY}).
	 */
	public static List<int[]> arrowRows(int cx, int cy, double dirX, double dirY, int size) {
		double px = -dirY;
		double py = dirX;
		double[][] p = {
				{cx + dirX * size, cy + dirY * size},
				{cx - dirX * size * 0.6 + px * size * 0.75, cy - dirY * size * 0.6 + py * size * 0.75},
				{cx - dirX * size * 0.6 - px * size * 0.75, cy - dirY * size * 0.6 - py * size * 0.75}};
		List<int[]> rows = new ArrayList<>();
		for (int y = cy - size; y <= cy + size; y++) {
			double yy = y + 0.5;
			double lo = Double.MAX_VALUE;
			double hi = -Double.MAX_VALUE;
			for (int i = 0; i < 3; i++) {
				double[] a = p[i];
				double[] b = p[(i + 1) % 3];
				if ((a[1] <= yy && b[1] > yy) || (b[1] <= yy && a[1] > yy)) {
					double x = a[0] + (yy - a[1]) * (b[0] - a[0]) / (b[1] - a[1]);
					lo = Math.min(lo, x);
					hi = Math.max(hi, x);
				}
			}
			if (hi > lo) {
				rows.add(new int[] {y, (int) Math.floor(lo), (int) Math.ceil(hi)});
			}
		}
		return rows;
	}
}
