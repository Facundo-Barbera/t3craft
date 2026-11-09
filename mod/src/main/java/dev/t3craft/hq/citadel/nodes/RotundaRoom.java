package dev.t3craft.hq.citadel.nodes;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelNodes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.BAMBOO;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.GLASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.LIGHT;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.RIB;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

/**
 * The scheduled-tasks rotunda: a round drum of radius 7 under a banded dome with a glass cupola.
 *
 * <ul>
 * <li><b>Floor</b>: a clock face: 12 hour marks at d = 5 (lights at 12, 3, 6 and 9), hands at ten past ten
 *     and an accent ring at d = 4.</li>
 * <li><b>Drum</b> (d &gt; 6, 10 high): dark base (a cracked one in six), a calcite body, cherry-log pilasters on the
 *     8 rays of 45 degrees, tall glass windows between them (y 4..8), a lintel course, a lit band at the
 *     top, and doors 3 wide on the four sides.</li>
 * <li><b>Dome</b> (radius 7.2 over the drum): a dark skirt of deepslate tiles, cherry planks, a golden cap of
 *     bamboo mosaic; wherever the ring inside is exactly one higher the top block is a stair, so the dome is
 *     smooth; four lanterns hang from it on the diagonals.</li>
 * <li><b>Cupola</b> on top: 3 × 3 of glass between four logs, a light inside it, a stair roof, a gold block and
 *     a rod.</li>
 * </ul>
 */
public final class RotundaRoom {
	/** Radius inside which the hall is open (the wall is further out). */
	private static final double WALL_R = 6.0;
	/** Top of the drum (a lit band). */
	private static final int WALL_TOP = 10;
	/** Radius of the dome sphere. */
	private static final double DOME_R = 7.2;
	/** The dome is stretched in height by this factor (a taller, more pointed dome). */
	private static final double STRETCH = 1.2;
	/** Height of the dome above the drum at the centre. */
	private static final int DOME_H = (int) Math.round(STRETCH * DOME_R);

	private static int domeTop(double d) {
		return WALL_TOP + (int) Math.round(STRETCH * Math.sqrt(Math.max(0, DOME_R * DOME_R - d * d)));
	}

	private RotundaRoom() {
	}

	/** Paints the column {@code (x, z)} at offset {@code (dx, dz)}, {@code d} from the centre. */
	public static void cell(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		floor(c, x, z, dx, dz, d);
		wall(c, x, z, dx, dz, d);
		dome(c, x, z, dx, dz, d);
	}

	/** The floor: a clock face. */
	public static void floor(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		BlockState s = Math.abs(d - 4) < 0.5 ? c.accent() : c.floor();
		for (int h = 0; h < 12; h++) {
			double a = h * Math.PI / 6;
			if (dx == (int) Math.round(5 * Math.sin(a)) && dz == (int) Math.round(-5 * Math.cos(a))) {
				s = h % 3 == 0 ? LIGHT : RIB;
			}
		}
		for (int r = 1; r <= 3; r++) {
			double hour = 5 * Math.PI / 3;
			double minute = Math.PI / 3;
			boolean h1 = r <= 2 && dx == (int) Math.round(r * Math.sin(hour)) && dz == (int) Math.round(-r * Math.cos(hour));
			boolean m1 = dx == (int) Math.round(r * Math.sin(minute)) && dz == (int) Math.round(-r * Math.cos(minute));
			if (h1 || m1) {
				s = RIB;
			}
		}
		c.set(x, 0, z, s);
	}

	/** The drum. */
	public static void wall(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		if (d <= WALL_R || d > WALL_R + 1.0) {
			return; // inside the hall, or the apron round the drum when the citadel is scaled (the zone grows, the room does not)
		}
		double off = NodeDecor.rayOffset(dx, dz);
		for (int y = 1; y <= WALL_TOP; y++) {
			BlockState s;
			if (y == WALL_TOP) {
				s = c.kit.wallPanel(true);
			} else if (off < 0.55) {
				s = y <= 2 ? RIB : LOG;
			} else if (y <= 2) {
				s = NodeDecor.footMix(x, y, z);
			} else if (y == 3) {
				s = CALCITE;
			} else if (y <= WALL_TOP - 2 && off >= 1.3) {
				s = GLASS;
			} else if (y == WALL_TOP - 1) {
				s = RIB;
			} else {
				s = CALCITE;
			}
			c.set(x, y, z, s);
		}
		if (CitadelNodes.door(dx, dz, 6)) {
			c.fill(x, 1, 4, z, AIR);
		}
	}

	private static BlockState shell(int y, net.minecraft.core.Direction inward, boolean stair) {
		Block full;
		Block stairs;
		if (y <= WALL_TOP + 2) {
			full = Blocks.DEEPSLATE_TILES;
			stairs = Blocks.DEEPSLATE_TILE_STAIRS;
		} else if (y >= WALL_TOP + DOME_H - 2) {
			full = Blocks.BAMBOO_MOSAIC;
			stairs = Blocks.BAMBOO_MOSAIC_STAIRS;
		} else {
			full = Blocks.CHERRY_PLANKS;
			stairs = Blocks.CHERRY_STAIRS;
		}
		return stair ? stairs(stairs, inward) : full.defaultBlockState();
	}

	/** The dome and its cupola. */
	public static void dome(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		int top = domeTop(d);
		// the shell starts at the top of the ring outside it, so the rings touch face to face
		int low = domeTop(Math.min(DOME_R, d + 1));
		int lo = Math.max(WALL_TOP + 1, Math.min(low, top));
		int topIn = domeTop(Math.max(0, d - 1));
		net.minecraft.core.Direction in = NodeDecor.inward(dx, dz);
		for (int y = lo; y <= top; y++) {
			boolean ramp = y == top && d >= 1 && topIn == top + 1;
			c.set(x, y, z, d < 1 ? GLASS : shell(y, in, ramp));
		}
		// the cupola: 3 x 3 of glass between four logs, a light inside, a roof, a gold block, a rod
		if (d < 1.5) {
			boolean center = dx == 0 && dz == 0;
			boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
			for (int y = top + 1; y <= top + 3; y++) {
				if (center) {
					if (y == top + 2) {
						c.set(x, y, z, LIGHT);
					}
				} else {
					c.set(x, y, z, corner ? LOG : GLASS);
				}
			}
			c.set(x, top + 4, z, center ? BAMBOO : stairs(Blocks.CHERRY_STAIRS, in));
			if (center) {
				c.set(x, top + 5, z, NodeDecor.ROD);
			}
		}
		// a lantern hangs from the dome on each diagonal at d ~ 4.5
		if (NodeDecor.rayOffset(dx, dz) < 0.55 && d >= 4.2 && d <= 4.9) {
			c.put(x, lo - 1, z, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), HANGING);
		}
	}
}
