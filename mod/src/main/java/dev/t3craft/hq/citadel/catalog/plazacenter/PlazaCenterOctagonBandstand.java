package dev.t3craft.hq.citadel.catalog.plazacenter;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * An octagonal bandstand: a dark deepslate dais one step above the pavement, a parquet stage of cherry
 * and bamboo in concentric octagons, eight quartz columns joined by a balustrade of slabs and cherry
 * balusters (open on the four axes), a cherry beam, and a ribbed dome of cherry stairs on a dark
 * skirt, with polished-diorite ribs over the columns, a gold finial with a rod and a chandelier
 * hanging in the middle. The leftover plot corners hold small cherry trees on moss beds.
 *
 * <pre>
 *  section                           top view (octagon metric m = max(|x|, |z|, (|x|+|z|)/√2))
 *       ╿ rod                          ·  ◆ c ◆  ·      ◆ column on every vertex
 *       ▲ gold                         ◆ ┌──────┐ ◆     ┌ balustrade, open on the axes
 *     ▗▄▙▟▄▖  dome, ribs                ·│stage │·
 *     ▌║   ║▐  columns                 ◆ └──────┘ ◆
 *   ▔▔▔▔▔▔▔▔▔  dais y0                   ·  ◆   ◆  ·
 * </pre>
 *
 * Size: 13–15 × 13–15 × 10+ (the dome gains a layer or two with more height).
 */
public final class PlazaCenterOctagonBandstand implements Variant {
	@Override
	public String id() {
		return "plaza_center_octagon_bandstand";
	}

	@Override
	public Family family() {
		return Family.PLAZA_CENTER;
	}

	@Override
	public int minW() {
		return 13;
	}

	@Override
	public int minD() {
		return 13;
	}

	@Override
	public int minH() {
		return 10;
	}

	@Override
	public int maxW() {
		return 15;
	}

	@Override
	public int maxD() {
		return 15;
	}

	/** Octagonal distance: level sets are regular octagons with apothem {@code m}. */
	private static double oct(int dx, int dz) {
		int ax = Math.abs(dx);
		int az = Math.abs(dz);
		return Math.max(Math.max(ax, az), (ax + az) / Math.sqrt(2));
	}

	private static Direction out(int dx, int dz) {
		return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	private static BlockState log(net.minecraft.world.level.block.Block blk, Direction.Axis axis) {
		return blk.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int cu = w / 2;
		int cv = d / 2;
		double a0 = Math.min(Math.min(cu, w - 1 - cu), Math.min(cv, d - 1 - cv)) + 0.5;
		int k = (int) Math.floor(a0 - 2.0); // radius of the column ring
		int t = (int) Math.round(0.4142 * k); // offset of the two columns on each face
		int jTop = Math.max(2, Math.min(5, p.h() - 7)); // dome layers above the base layer
		double rho0 = k + 1.1;

		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				int dx = u - cu;
				int dz = v - cv;
				double m = oct(dx, dz);
				if (m > a0) {
					corner(c, p, u, v, m - a0, w, d);
				} else if (m > a0 - 1.0) {
					// [depth] the single step up to the dais, low side out: the stair faces inwards (tall half towards the dais) so it is climbed walking in
					p.set(c, u, 0, v, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, out(dx, dz).getOpposite()));
				} else {
					floor(c, p, u, v, dx, dz, m, k);
				}
				ring(c, p, u, v, dx, dz, m, k, t);
				dome(c, p, u, v, dx, dz, m, k, jTop, rho0);
			}
		}
		int apex = 5 + jTop;
		p.set(c, cu, apex, cv, b(Blocks.GOLD_BLOCK));
		p.set(c, cu, apex + 1, cv, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		for (int y = 4; y < apex; y++) {
			p.put(c, cu, y, cv, Blocks.IRON_CHAIN.defaultBlockState(), CitadelCanvas.HANGING);
		}
		p.put(c, cu, 3, cv, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ dais and stage

	private void floor(CitadelCanvas c, Plot p, int u, int v, int dx, int dz, double m, int k) {
		int h = p.hash(u, 0, v, 6);
		if (m > k + 0.5) {
			// promenade around the rail: heavy dark stone, mixed
			p.set(c, u, 0, v, h == 0 ? b(Blocks.POLISHED_ANDESITE) : h < 3 ? b(Blocks.STONE_BRICKS) : b(Blocks.DEEPSLATE_TILES));
		} else if (m <= 1.0) {
			p.set(c, u, 0, v, b(Blocks.CHISELED_DEEPSLATE));
		} else if (m <= 2.2) {
			p.set(c, u, 0, v, h == 0 ? log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y) : b(Blocks.CHERRY_PLANKS));
		} else if (m <= 3.2) {
			p.set(c, u, 0, v, b(Blocks.BAMBOO_MOSAIC));
		} else {
			p.set(c, u, 0, v, h == 0 ? log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y) : b(Blocks.CHERRY_PLANKS));
		}
	}

	// ------------------------------------------------------------------ columns, balustrade, beam, eaves

	private void ring(CitadelCanvas c, Plot p, int u, int v, int dx, int dz, double m, int k, int t) {
		int ax = Math.abs(dx);
		int az = Math.abs(dz);
		boolean column = (ax == k && az == t) || (ax == t && az == k);
		boolean inRing = m > k - 0.5 && m <= k + 0.5;
		if (column) {
			p.set(c, u, 1, v, b(Blocks.POLISHED_DEEPSLATE));
			p.set(c, u, 2, v, b(Blocks.QUARTZ_PILLAR));
			p.set(c, u, 3, v, b(Blocks.CHISELED_QUARTZ_BLOCK));
			p.set(c, u, 4, v, log(Blocks.STRIPPED_CHERRY_WOOD, ax >= az ? Direction.Axis.Z : Direction.Axis.X));
			return;
		}
		if (inRing) {
			// beam over everything; axis follows the face, diagonal steps use planks
			boolean faceX = ax == k && az < t;
			boolean faceZ = az == k && ax < t;
			p.set(c, u, 4, v, faceX ? log(Blocks.STRIPPED_CHERRY_WOOD, Direction.Axis.Z) : faceZ ? log(Blocks.STRIPPED_CHERRY_WOOD, Direction.Axis.X) : b(Blocks.CHERRY_PLANKS));
			boolean gap = (faceX && az <= 1) || (faceZ && ax <= 1);
			if (!gap) {
				// [depth] balustrade: slabs with cherry balusters on every second cell
				if (((ax + az) & 1) == 0) {
					p.set(c, u, 1, v, slab(Blocks.POLISHED_DIORITE_SLAB));
				} else {
					p.set(c, u, 1, v, b(Blocks.CHERRY_FENCE));
					if (p.hash(u, 3, v, 3) == 0) {
						p.put(c, u, 3, v, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
					}
				}
			} else if (ax + az == k) {
				// hanging lantern over the middle of each entrance
				p.put(c, u, 3, v, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
			}
		} else if (m > k + 0.5 && m <= k + 1.5) {
			// eaves: dark tile stairs, low side out
			p.set(c, u, 4, v, stairs(Blocks.DEEPSLATE_TILE_STAIRS, out(dx, dz)));
		}
	}

	// ------------------------------------------------------------------ ribbed dome

	private static double rhoOf(int j, int jTop, double rho0) {
		return rho0 * (1.0 - Math.pow(j / (jTop + 0.7), 1.8));
	}

	private void dome(CitadelCanvas c, Plot p, int u, int v, int dx, int dz, double m, int k, int jTop, double rho0) {
		double angle = Math.atan2(dz, dx);
		// distance to the nearest rib (they run over the column vertices, 22.5° + 45° k)
		double step = Math.PI / 4;
		double offs = Math.PI / 8;
		double rel = (angle - offs) / step;
		double da = Math.abs(angle - (Math.round(rel) * step + offs));
		double rib = m * Math.sin(da);
		for (int j = 0; j <= jTop; j++) {
			double rho = rhoOf(j, jTop, rho0);
			boolean last = j == jTop;
			// each ring reaches one block under the next ring's outer edge, so the shell has no hole
			double thick = last ? rho : rho - rhoOf(j + 1, jTop, rho0) + 1.0;
			if (m > rho || (!last && m <= rho - thick)) {
				continue;
			}
			double next = last ? 0 : rhoOf(j + 1, jTop, rho0);
			boolean exposed = !last && m > next;
			boolean isRib = rib < 0.6 && m > 1.4;
			int y = 5 + j;
			if (last) {
				p.set(c, u, y, v, b(Blocks.CHERRY_PLANKS));
			} else if (exposed) {
				p.set(c, u, y, v, isRib ? stairs(Blocks.POLISHED_DIORITE_STAIRS, out(dx, dz)) : stairs(Blocks.CHERRY_STAIRS, out(dx, dz)));
			} else {
				p.set(c, u, y, v, isRib ? b(Blocks.POLISHED_DIORITE) : b(Blocks.CHERRY_PLANKS));
			}
		}
	}

	// ------------------------------------------------------------------ corners: cherry trees on moss

	private void corner(CitadelCanvas c, Plot p, int u, int v, double beyond, int w, int d) {
		if (beyond <= 0.5) {
			int h = p.hash(u, 0, v, 4);
			p.set(c, u, 0, v, h == 0 ? slab(Blocks.MOSSY_STONE_BRICK_SLAB) : slab(Blocks.STONE_BRICK_SLAB));
			return;
		}
		boolean edge = u == 0 || v == 0 || u == w - 1 || v == d - 1;
		p.set(c, u, 0, v, edge ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.MOSS_BLOCK));
		boolean tip = (u == 1 || u == w - 2) && (v == 1 || v == d - 2);
		if (tip) {
			p.set(c, u, 1, v, log(Blocks.CHERRY_LOG, Direction.Axis.Y));
			p.set(c, u, 2, v, log(Blocks.CHERRY_LOG, Direction.Axis.Y));
			for (int dv = -1; dv <= 1; dv++) {
				for (int du = -1; du <= 1; du++) {
					if (du == 0 && dv == 0) {
						continue;
					}
					p.set(c, u + du, 3, v + dv, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
				}
			}
			p.set(c, u, 3, v, log(Blocks.CHERRY_LOG, Direction.Axis.Y));
			p.set(c, u, 4, v, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
		} else if (!edge && p.hash(u, 1, v, 3) == 0) {
			p.set(c, u, 1, v, p.hash(u, 2, v, 2) == 0 ? b(Blocks.PINK_PETALS) : CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES));
		}
	}
}
