package dev.t3craft.client.workstation;

/**
 * Where one workstation is: the computer block (what the player right-clicks) and the screen (a
 * TextDisplay) with the yaw its text faces. Plain numbers, no Minecraft classes, so placement
 * sources are unit-testable; the renderer converts to {@code BlockPos}/{@code Vec3}.
 *
 * <p>Yaw follows Minecraft: 0 = facing south (+z), 90 = west (−x). A FIXED TextDisplay with this yaw
 * is readable from the side it faces, so {@code screenYaw} should point at whoever sits at the desk.
 */
public record StationPlacement(int computerX, int computerY, int computerZ, double screenX, double screenY, double screenZ, float screenYaw,
		float scale) {
	/** Height of the screen's baseline above the computer block's bottom. */
	public static final double SCREEN_HEIGHT = 1.15;
	/** Text scale of a floating screen over one computer: 28 characters ≈ 1.9 blocks wide. */
	public static final float DEFAULT_SCALE = 0.45f;
	/** Text scale per block of screen width when the text is laid on a wall of computers ({@link #onFace}). */
	public static final float SCALE_PER_BLOCK = 0.215f;
	/** Gap between a wall's face and the text laid on it (blocks). */
	static final double FACE_GAP = 0.03;
	/** Margin from the wall's bottom edge to the text's bottom line (blocks). */
	static final double FACE_MARGIN = 0.12;

	public StationPlacement(int computerX, int computerY, int computerZ, double screenX, double screenY, double screenZ, float screenYaw) {
		this(computerX, computerY, computerZ, screenX, screenY, screenZ, screenYaw, DEFAULT_SCALE);
	}

	/**
	 * A computer {@code distance} blocks in front of someone standing at ({@code x, y, z}) and looking
	 * along {@code yaw}, with its screen just above it, turned back towards that person.
	 */
	public static StationPlacement inFrontOf(double x, double y, double z, float yaw, double distance) {
		double rad = Math.toRadians(yaw);
		double fx = -Math.sin(rad);
		double fz = Math.cos(rad);
		int cx = (int) Math.floor(x + fx * distance);
		int cy = (int) Math.floor(y + 0.01);
		int cz = (int) Math.floor(z + fz * distance);
		return on(cx, cy, cz, wrap(yaw + 180f));
	}

	/** Screen centred over the computer block, nudged 0.1 towards the side it faces. */
	public static StationPlacement on(int cx, int cy, int cz, float screenYaw) {
		double rad = Math.toRadians(screenYaw);
		double nx = -Math.sin(rad) * 0.1;
		double nz = Math.cos(rad) * 0.1;
		return new StationPlacement(cx, cy, cz, cx + 0.5 + nx, cy + SCREEN_HEIGHT, cz + 0.5 + nz, wrap(screenYaw));
	}

	/**
	 * The screen laid flat on the front face of a {@code width}-wide wall of computer blocks (e.g. an HQ
	 * desk's 3×2 screen with the blocks module off): centred across the face, its bottom line just above
	 * the wall's bottom edge, a hair in front of the face, scaled to the wall's width. {@code (cx, cy, cz)}
	 * is the clicked block (any cell of the wall); {@code (bx, by, bz)} its bottom-middle cell (for an
	 * even width, the cell right of the centre line) and {@code screenYaw} the way the face looks.
	 */
	public static StationPlacement onFace(int cx, int cy, int cz, int bx, int by, int bz, int width, float screenYaw) {
		double rad = Math.toRadians(screenYaw);
		double nx = -Math.sin(rad);
		double nz = Math.cos(rad);
		// the viewer's right is the facing turned counter-clockwise: (nz, -nx) in x/z
		double shift = width % 2 == 0 ? -0.5 : 0;
		double rx = nz;
		double rz = -nx;
		double out = 0.5 + FACE_GAP;
		return new StationPlacement(cx, cy, cz, bx + 0.5 + nx * out + rx * shift, by + FACE_MARGIN, bz + 0.5 + nz * out + rz * shift,
				wrap(screenYaw), SCALE_PER_BLOCK * Math.max(1, width));
	}

	/** Key of the computer block (for click lookup). */
	public long computerKey() {
		return blockKey(computerX, computerY, computerZ);
	}

	/** Packs a block position into a long (same bit layout idea as {@code BlockPos.asLong}, but self-contained). */
	public static long blockKey(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}

	static float wrap(float yaw) {
		float w = yaw % 360f;
		if (w >= 180f) {
			w -= 360f;
		}
		if (w < -180f) {
			w += 360f;
		}
		return w;
	}
}
