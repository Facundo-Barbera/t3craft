package dev.t3craft.client.blocks;

import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Distance and facing rules for the blocks' renderers (pure and unit-tested, except
 * {@link #cameraForward}). The BERs ask these in
 * {@code shouldRender} (per block, before any state is extracted) and in {@code extractRenderState}
 * (level of detail), so blocks the player cannot see or read cost nothing per frame.
 *
 * <p>Coordinates: {@code dx, dy, dz} = block centre − camera.
 */
public final class RenderLod {
	/** Status lamps / board bulbs: not drawn beyond this (the block itself still shows its colour). */
	public static final double LAMP_VIEW = 48;
	/** Lamps: halo quads only this close; farther, the core quad alone. */
	public static final double LAMP_HALO = 20;
	/** The hub beacon: seen from across the map. */
	public static final double BEACON_VIEW = 160;
	/** Lamps closer than this are always drawn (the player may be right next to one, half in view). */
	static final double NEAR = 3;
	/**
	 * Cosine of the angle from the view direction beyond which a block counts as behind the camera:
	 * −0.2 ≈ 102°, wider than any horizontal half-FOV (110° FOV on 21:9 ≈ 74°) plus the block's size.
	 */
	static final double BEHIND_COS = -0.2;

	private static final Vector3fc NO_DIRECTION = new Vector3f();

	private RenderLod() {
	}

	/** The main camera's view direction (zero before there is a camera: distance-only culling). */
	static Vector3fc cameraForward() {
		try {
			return Minecraft.getInstance().gameRenderer.mainCamera().forwardVector();
		} catch (RuntimeException | LinkageError e) {
			return NO_DIRECTION;
		}
	}

	public static double distSq(double dx, double dy, double dz) {
		return dx * dx + dy * dy + dz * dz;
	}

	/** Within {@code range} blocks. */
	public static boolean within(double dx, double dy, double dz, double range) {
		return distSq(dx, dy, dz) <= range * range;
	}

	/**
	 * The block (centre at camera + d) is clearly behind the camera looking along {@code f} (unit
	 * vector). Used for renderers that must render "off screen" for one block (the beacon's beam) and
	 * so are not frustum-culled by vanilla for any of them.
	 */
	public static boolean behind(double dx, double dy, double dz, double fx, double fy, double fz) {
		return behind(dx, dy, dz, fx, fy, fz, 0);
	}

	/** {@link #behind} for something {@code radius} blocks across around its centre. */
	public static boolean behind(double dx, double dy, double dz, double fx, double fy, double fz, double radius) {
		double d2 = distSq(dx, dy, dz);
		double near = NEAR + radius;
		if (d2 <= near * near) {
			return false;
		}
		double dot = dx * fx + dy * fy + dz * fz;
		return dot < BEHIND_COS * Math.sqrt(d2);
	}

	/**
	 * A screen on the face {@code (nx, nz)} (unit horizontal normal) of the block is visible from the
	 * camera only from in front of that face: the camera must be past the face's plane, i.e.
	 * (camera − centre)·n &gt; 0.5. With {@code d} = centre − camera that is {@code −d·n > 0.5}; a small
	 * margin keeps grazing views.
	 */
	public static boolean faceVisible(double dx, double dz, int nx, int nz) {
		return -(dx * nx + dz * nz) > 0.45;
	}

	/**
	 * Whether a monitor screen can be seen: from in front of its face (block centre at
	 * {@code (bdx, bdz)}, face normal {@code (nx, nz)}) and not behind the camera (screen centre at
	 * {@code s}, {@code radius} = half its larger side, so a big panel's edge in view keeps it).
	 * Distance is the renderer's own setting. Needed because the monitor renderer draws "off screen"
	 * (panels draw from their origin block), so vanilla never frustum-culls it.
	 */
	public static boolean monitor(double bdx, double bdz, int nx, int nz, double sdx, double sdy, double sdz, double radius,
			double fx, double fy, double fz) {
		return faceVisible(bdx, bdz, nx, nz) && !behind(sdx, sdy, sdz, fx, fy, fz, radius);
	}

	/** Whether a lamp at this offset is drawn ({@code beacon}: the hub beacon, drawn from far and always, beam included). */
	public static boolean lamp(double dx, double dy, double dz, boolean beacon, double fx, double fy, double fz) {
		if (beacon) {
			return within(dx, dy, dz, BEACON_VIEW);
		}
		return within(dx, dy, dz, LAMP_VIEW) && !behind(dx, dy, dz, fx, fy, fz);
	}
}
