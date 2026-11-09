package dev.t3craft.hq.citadel.catalog.plazacenter;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A monumental horizontal sundial: a round dial plate of pale stone sunk half a block inside a raised
 * rim, with hour lines computed for a real latitude ({@code tan θ = sin φ · tan 15°h}), a golden tick
 * on the rim for every hour and a boss of chiselled brick in the centre. The gnomon is a long wedge of
 * quartz stairs that climbs towards the back of the plot (so, in the city, towards the north).
 * Plot corners are gravel garden corners with clipped azalea hedges and a lantern post.
 *
 * <pre>
 *  top view                         side view (front on the left)
 *   ▓ hedge        ▓ hedge              ┌─ gold tip + rod
 *      ╲ rim ◯ ╱                       ╱│  quartz stairs, 1 step per row
 *    - - hour lines - -               ╱ │
 *      ╱ boss ▲ gnomon ╲          ___╱__│__ boss y0, plate = slabs, rim = blocks
 * </pre>
 *
 * Size: 9–15 × 9–15 × 8+ (the gnomon is as tall as the depth and the height allow, up to 8).
 */
public final class PlazaCenterMonumentalSundial implements Variant {
	/** Latitude of the dial: sin φ sets how crowded the hour lines are around noon. */
	private static final double SIN_LAT = 0.643;

	@Override
	public String id() {
		return "plaza_center_monumental_sundial";
	}

	@Override
	public Family family() {
		return Family.PLAZA_CENTER;
	}

	@Override
	public int minW() {
		return 9;
	}

	@Override
	public int minD() {
		return 9;
	}

	@Override
	public int minH() {
		return 8;
	}

	@Override
	public int maxW() {
		return 15;
	}

	@Override
	public int maxD() {
		return 15;
	}

	private static Direction out(int dx, int dz) {
		return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	/** Angle (from the front axis) of the hour line {@code h}, h = -6..6. */
	private static double hourAngle(int h) {
		if (Math.abs(h) == 6) {
			return Math.signum(h) * Math.PI / 2;
		}
		return Math.atan(SIN_LAT * Math.tan(Math.toRadians(15.0 * h)));
	}

	/** Perpendicular distance from the cell to the (infinite) hour line {@code h}. */
	private static double lineDist(double r, double alpha, int h) {
		return r * Math.abs(Math.sin(alpha - hourAngle(h)));
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int cu = w / 2;
		int cv = d / 2;
		double radius = Math.min(Math.min(cu, w - 1 - cu), Math.min(cv, d - 1 - cv)) + 0.5;
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				int dx = u - cu;
				int dz = v - cv;
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r > radius) {
					corner(c, p, u, v, r - radius, w, d);
				} else {
					dial(c, p, u, v, dx, dz, r, radius);
				}
			}
		}
		gnomon(c, p, cu, cv);
	}

	// ------------------------------------------------------------------ dial

	private void dial(CitadelCanvas c, Plot p, int u, int v, int dx, int dz, double r, double radius) {
		double e = radius - r;
		int cheb = Math.max(Math.abs(dx), Math.abs(dz));
		double alpha = Math.atan2(dx, -dz);
		if (e < 1.0) {
			// [depth] the outer ramp, low side out (the stairs face inwards: tall half towards the rim, climbed walking in)
			p.set(c, u, 0, v, p.hash(u, 0, v, 4) == 0 ? stairs(Blocks.MOSSY_STONE_BRICK_STAIRS, out(dx, dz).getOpposite()) : stairs(Blocks.STONE_BRICK_STAIRS, out(dx, dz).getOpposite()));
			return;
		}
		if (e < 2.0) {
			// raised rim: dark polished stone, a golden tick wherever an hour line meets it
			boolean tick = false;
			for (int h = -6; h <= 6 && !tick; h += 3) {
				tick = lineDist(r, alpha, h) < 0.5;
			}
			if (tick) {
				p.set(c, u, 0, v, b(Blocks.GOLD_BLOCK));
			} else {
				int k = p.hash(u, 0, v, 5);
				p.set(c, u, 0, v, k == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : k == 1 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.DEEPSLATE_BRICKS));
			}
			return;
		}
		if (cheb <= 1) {
			// boss under the gnomon
			p.set(c, u, 0, v, b(Blocks.CHISELED_STONE_BRICKS));
			return;
		}
		// [depth] the plate sits half a block below the rim; hour lines of dark slab, pale slabs mixed
		boolean line = false;
		if (r >= 2.9 && e >= 2.3) {
			for (int h = -6; h <= 6 && !line; h += 3) {
				line = lineDist(r, alpha, h) < 0.5;
			}
		}
		if (line) {
			p.set(c, u, 0, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
			return;
		}
		boolean ring = cheb == 2 && r < 2.9;
		int k = p.hash(u, 0, v, 6);
		Block plate = ring ? Blocks.POLISHED_DIORITE_SLAB : k == 0 ? Blocks.SMOOTH_STONE_SLAB : k < 3 ? Blocks.POLISHED_ANDESITE_SLAB : Blocks.POLISHED_DIORITE_SLAB;
		p.set(c, u, 0, v, slab(plate));
	}

	// ------------------------------------------------------------------ gnomon

	private void gnomon(CitadelCanvas c, Plot p, int cu, int cv) {
		int v0 = cv - 1; // low end, on the boss
		int vb = p.d() - 2;
		int rise = Math.min(1 + vb - v0, p.h() - 3);
		int vEnd = v0 + rise - 1;
		for (int i = 0; i < rise; i++) {
			int v = v0 + i;
			int y = 1 + i;
			// centre ridge: solid smooth quartz up to a stair that faces the front
			for (int yy = 1; yy < y; yy++) {
				p.set(c, cu, yy, v, yy % 3 == 0 ? b(Blocks.CHISELED_QUARTZ_BLOCK) : b(Blocks.SMOOTH_QUARTZ));
			}
			p.set(c, cu, y, v, stairs(Blocks.QUARTZ_STAIRS, Direction.NORTH));
			// shoulders: one block lower, lighter stone; they make the fin a thick wedge, not a card
			if (y >= 2) {
				for (int side = -1; side <= 1; side += 2) {
					for (int yy = 1; yy < y - 1; yy++) {
						p.set(c, cu + side, yy, v, b(Blocks.CALCITE));
					}
					p.set(c, cu + side, y - 1, v, stairs(Blocks.POLISHED_DIORITE_STAIRS, Direction.NORTH));
				}
			} else {
				for (int side = -1; side <= 1; side += 2) {
					p.set(c, cu + side, 1, v, slab(Blocks.POLISHED_DIORITE_SLAB));
				}
			}
		}
		// back pier: a vertical face with a gold tip and a rod
		int yTip = rise + 1;
		p.set(c, cu, yTip, vEnd, b(Blocks.GOLD_BLOCK));
		p.set(c, cu, yTip + 1, vEnd, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		for (int side = -1; side <= 1; side += 2) {
			p.set(c, cu + side, rise, vEnd, stairs(Blocks.POLISHED_DIORITE_STAIRS, side < 0 ? Direction.WEST : Direction.EAST));
			p.set(c, cu + side, rise - 1, vEnd, b(Blocks.CALCITE));
		}
		// a pair of lanterns on the boss corners, hanging nothing: low bronze-black stands
		int[][] stands = {{cu - 1, cv + 1}, {cu + 1, cv + 1}};
		for (int[] s : stands) {
			if (s[1] < vEnd - 1 && s[1] != v0 + 1) {
				p.set(c, s[0], 1, s[1], b(Blocks.CANDLE));
			}
		}
	}

	// ------------------------------------------------------------------ corners: gravel gardens

	private void corner(CitadelCanvas c, Plot p, int u, int v, double beyond, int w, int d) {
		boolean tip = (u == 0 || u == w - 1) && (v == 0 || v == d - 1);
		int g = p.hash(u, 0, v, 5);
		if (beyond <= 1.0) {
			// edging: a half-block stone border between the rim and the gravel
			p.set(c, u, 0, v, slab(g == 0 ? Blocks.MOSSY_STONE_BRICK_SLAB : Blocks.STONE_BRICK_SLAB));
			return;
		}
		p.set(c, u, 0, v, g == 0 ? b(Blocks.COARSE_DIRT) : g == 1 ? b(Blocks.DIRT_PATH) : b(Blocks.GRAVEL));
		if (tip) {
			p.set(c, u, 1, v, b(Blocks.POLISHED_DEEPSLATE_WALL));
			p.set(c, u, 2, v, b(Blocks.POLISHED_DEEPSLATE_WALL));
			p.set(c, u, 3, v, b(Blocks.LANTERN));
			return;
		}
		if (beyond <= 2.2) {
			// clipped hedge hugging the dial
			int k = p.hash(u, 1, v, 7);
			p.set(c, u, 1, v, CitadelCanvas.leaves(k == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
			if (k == 3) {
				p.set(c, u, 2, v, CitadelCanvas.leaves(Blocks.AZALEA_LEAVES));
			}
		} else if (p.hash(u, 1, v, 4) == 0) {
			p.set(c, u, 1, v, b(Blocks.PINK_PETALS));
		}
	}
}
