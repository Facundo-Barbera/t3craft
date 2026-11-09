package dev.t3craft.hq.citadel.nodes;

import dev.t3craft.hq.citadel.CitadelCanvas;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.GLASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.LIGHT;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.RIB;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

/**
 * The review tower: a slender stepped tower of three bodies on a 13 × 13 footprint (the node's square),
 * about 39 high with its spire. Each body is narrower than the one below; the setback is a terrace with a
 * stone-brick railing, so the silhouette reads from far: a broad base with buttresses, a middle body, an
 * open belfry and a pyramid roof of stairs with a spire.
 *
 * <p>Rings are {@code r = max(|dx|, |dz|)} from the centre. {@code a} is the cell's coordinate along its face.
 * <ul>
 * <li><b>Base</b> (wall plane r = 5, y 1..11): deepslate base (a cracked one in six), calcite body, 2-wide
 *     glass bays at {@code |a| = 3, 4} recessed behind piers at r = 6 ({@code |a| = 2} beside each door, and
 *     corner buttresses), a lit panel band at y 10, a cherry plank terrace at y 11 with a stone-brick
 *     railing, and a cherry-slab cornice at r = 6. The doors (3 wide, 4 high) are on the four faces; each
 *     has a blackstone lintel at r = 6, y 5. The big screen sits flush in the north wall plane
 *     ({@code z = cz - 5}).</li>
 * <li><b>Middle body</b> (r = 4, y 12..22) on a mezzanine ring at y 11: tall slit windows, a lit band on
 *     top, corner logs; terrace with railing at y 23 (r = 4).</li>
 * <li><b>Belfry</b> (r = 3, y 23..31) on a ring at y 22: open arches 3 wide, a log beam across with a
 *     hanging lantern as the bell, a lit band at the top.</li>
 * <li><b>Roof</b>: stairs (cherry on the two outer rings, blackstone bricks inside), each ring above a
 *     full block so the rings touch; spire of log, a light and a rod.</li>
 * </ul>
 * The interior is a tall hall of 9 × 9 (r ≤ 4) open to the belfry; the furniture is {@link NodeDecor}'s.
 */
public final class TowerRoom {
	/** Radius of the wall plane of each body (base, middle, belfry). */
	private static final int R1 = 5;
	private static final int R2 = 4;
	private static final int R3 = 3;
	/** Cornice height of each body. */
	private static final int TOP1 = 11;
	private static final int TOP2 = 22;
	private static final int TOP3 = 31;

	private TowerRoom() {
	}

	/** Paints the column {@code (x, z)}, at offset {@code (dx, dz)} from the tower's centre. */
	public static void cell(CitadelCanvas c, int x, int z, int dx, int dz) {
		int r = Math.max(Math.abs(dx), Math.abs(dz));
		int a = Math.abs(dx) >= Math.abs(dz) ? dz : dx;
		int aa = Math.abs(a);
		c.set(x, 0, z, r <= 2 ? c.accent() : c.floor());
		if (r == R1 + 1) {
			piers(c, x, z, dx, dz, aa);
		} else if (r == R1) {
			base(c, x, z, dx, dz, aa);
		} else if (r == R2) {
			middle(c, x, z, dx, dz, aa);
		} else if (r == R3) {
			belfry(c, x, z, dx, dz, aa);
		}
		roof(c, x, z, dx, dz, r);
		if (r <= 2 && dz == 0) {
			// the bell beam across the belfry
			c.set(x, TOP3 - 2, z, c.kit.beam(Direction.Axis.X));
		}
	}

	/** Base wall: dark footing, calcite, glass bays, lit band, terrace and railing. */
	private static void base(CitadelCanvas c, int x, int z, int dx, int dz, int aa) {
		boolean corner = Math.abs(dx) == R1 && Math.abs(dz) == R1;
		for (int y = 1; y <= TOP1; y++) {
			BlockState s;
			if (y == TOP1) {
				s = PLANKS;
			} else if (corner) {
				s = LOG;
			} else if (y <= 2) {
				s = NodeDecor.footMix(x, y, z);
			} else if (y == TOP1 - 1) {
				s = c.kit.wallPanel(true);
			} else if (y >= 4 && y <= 9 && (aa == 3 || aa == 4)) {
				s = GLASS;
			} else {
				s = CALCITE;
			}
			c.set(x, y, z, s);
		}
		c.set(x, TOP1 + 1, z, b(Blocks.STONE_BRICK_WALL));
		if (corner) {
			c.put(x, TOP1 + 2, z, Blocks.LANTERN.defaultBlockState(), DECOR);
		}
		if (aa <= 1) {
			c.fill(x, 1, 4, z, AIR);
		}
	}

	/** Piers, buttresses, door lintels and the cornice, one ring outside the wall plane. */
	private static void piers(CitadelCanvas c, int x, int z, int dx, int dz, int aa) {
		boolean buttress = aa >= R1;
		if (buttress) {
			c.fill(x, 1, 2, z, FOOT);
			c.fill(x, 3, TOP1 + 1, z, LOG);
			if (aa == R1 + 1) {
				c.set(x, TOP1 + 2, z, b(Blocks.STONE_BRICK_WALL));
				c.set(x, TOP1 + 3, z, NodeDecor.ROD);
			}
			return;
		}
		if (aa == 2) {
			c.set(x, 1, z, FOOT);
			c.fill(x, 2, TOP1 - 1, z, LOG);
		} else if (aa <= 1) {
			c.set(x, 5, z, RIB);
		} else {
			// a plinth under each bay
			c.set(x, 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
		c.set(x, TOP1, z, NodeDecor.topSlab(Blocks.CHERRY_SLAB));
	}

	/** Middle body: slit windows, corner logs, lit band, terrace railing. */
	private static void middle(CitadelCanvas c, int x, int z, int dx, int dz, int aa) {
		c.set(x, TOP1, z, FOOT);
		boolean corner = Math.abs(dx) == R2 && Math.abs(dz) == R2;
		for (int y = TOP1 + 1; y <= TOP2; y++) {
			BlockState s;
			if (corner) {
				s = LOG;
			} else if (y <= TOP1 + 2) {
				s = NodeDecor.footMix(x, y, z);
			} else if (y == TOP2) {
				s = c.kit.wallPanel(true);
			} else if (y >= TOP1 + 4 && (aa & 1) == 1) {
				s = GLASS;
			} else {
				s = CALCITE;
			}
			c.set(x, y, z, s);
		}
		c.set(x, TOP2 + 1, z, b(Blocks.STONE_BRICK_WALL));
		if (corner) {
			c.put(x, TOP2 + 2, z, Blocks.LANTERN.defaultBlockState(), DECOR);
		}
	}

	/** The belfry: open arches, jambs, a lit band. */
	private static void belfry(CitadelCanvas c, int x, int z, int dx, int dz, int aa) {
		c.set(x, TOP2, z, FOOT);
		boolean corner = Math.abs(dx) == R3 && Math.abs(dz) == R3;
		for (int y = TOP2 + 1; y <= TOP3; y++) {
			BlockState s;
			if (corner) {
				s = LOG;
			} else if (y <= TOP2 + 2) {
				s = CALCITE;
			} else if (y == TOP3) {
				s = c.kit.wallPanel(true);
			} else if (y == TOP3 - 1) {
				s = RIB;
			} else if (aa <= 1) {
				s = y == TOP3 - 2 ? RIB : AIR;
			} else {
				s = CALCITE;
			}
			c.set(x, y, z, s);
		}
	}

	/**
	 * The roof: a ring of stairs per level (cherry on the eave and the first ring, blackstone bricks inside),
	 * each inner ring on a full block so the rings touch, then the spire.
	 */
	private static void roof(CitadelCanvas c, int x, int z, int dx, int dz, int r) {
		int eave = TOP3;
		if (r == R2) {
			c.set(x, eave, z, stairs(Blocks.CHERRY_STAIRS, NodeDecor.inward(dx, dz)));
		} else if (r == R3) {
			c.set(x, eave + 1, z, stairs(Blocks.CHERRY_STAIRS, NodeDecor.inward(dx, dz)));
		} else if (r == 2 || r == 1) {
			c.set(x, eave + 1 + (3 - r) - 1, z, RIB);
			c.set(x, eave + 1 + (3 - r), z, stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, NodeDecor.inward(dx, dz)));
		} else if (r == 0) {
			c.set(x, eave + 3, z, RIB);
			c.set(x, eave + 4, z, RIB);
			c.set(x, eave + 5, z, LOG);
			c.set(x, eave + 6, z, LOG);
			c.set(x, eave + 7, z, LIGHT);
			c.set(x, eave + 8, z, NodeDecor.ROD);
		}
	}
}
