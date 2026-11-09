package dev.t3craft.hq.citadel.hub;

import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelHub;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.RIB;
import static dev.t3craft.hq.citadel.CitadelCanvas.Y0;
import static dev.t3craft.hq.citadel.CitadelCanvas.lattice;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.CitadelHub.AISLE_TOP;
import static dev.t3craft.hq.citadel.CitadelHub.SPRING;
import static dev.t3craft.hq.citadel.CitadelHub.arcadeZ;

/**
 * The inside of the basilica: the floor with its rug, the colonnade (piers with base, shaft and capital,
 * stained clerestory), the aisles' ceilings with rafters and hanging lanterns, the barrel vault and the
 * furniture (pews, reading nooks, planters, pilasters, pendant lamps) plus the invisible light blocks that
 * replace the lamps in the floor.
 *
 * <p>Nothing here may close a path: the nave's axis (|x| &lt;= 1) and the strips round the screens, the
 * board's queue, the benches ({@code x = +-7, |z| <= 3} and their anchors at {@code x = +-8}), the walk
 * spots ({@code (+-4, +-4)}) and the hologram column stay free; every block of furniture sits on the
 * floor beside them.
 */
public final class HubInterior {
	private HubInterior() {
	}

	// ------------------------------------------------------------------ floor

	/** Floor of an interior column: the octagon rings, a red rug with a dark border along the nave. */
	public static BlockState floor(CitadelCanvas c, int x, int z) {
		double o = RingCampusGeometry.octagon(x, z);
		boolean rug = o > 3 && z >= HubDims.RUG_Z0 && z <= HubDims.RUG_Z1;
		if (rug && Math.abs(x) <= HubDims.RUG_HALF) {
			return CitadelCanvas.terracotta(DyeColor.RED);
		}
		if (rug && Math.abs(x) == HubDims.RUG_HALF + 1) {
			return RIB;
		}
		return o <= 3 || (int) o % 4 == 0 ? c.accent() : c.floor();
	}

	// ------------------------------------------------------------------ nave columns

	/** The colonnade line ({@code |x| == 6}): piers with base, shaft and capital, then the stained clerestory. */
	public static void arcade(CitadelCanvas c, int x, int z) {
		int sx = x > 0 ? 1 : -1;
		if (arcadeZ(z)) {
			pier(c, x, z);
			Direction toPier = sx > 0 ? Direction.EAST : Direction.WEST;
			int y = HubDims.CAPITAL_Y;
			c.set(x - sx, y, z, HubBlocks.stairsTop(Blocks.CHERRY_STAIRS, toPier));
			c.set(x + sx, y, z, HubBlocks.stairsTop(Blocks.CHERRY_STAIRS, toPier.getOpposite()));
			c.set(x, y, z - 1, HubBlocks.stairsTop(Blocks.CHERRY_STAIRS, Direction.SOUTH));
			c.set(x, y, z + 1, HubBlocks.stairsTop(Blocks.CHERRY_STAIRS, Direction.NORTH));
		}
		c.set(x, AISLE_TOP, z, c.kit.beam(Direction.Axis.Z));
		DyeColor tint = HubBlocks.GLASS_COLORS[Math.floorMod(Math.floorDiv(z + 2, 4) + (sx > 0 ? 2 : 0), HubBlocks.GLASS_COLORS.length)];
		for (int y = AISLE_TOP + 1; y < SPRING; y++) {
			c.set(x, y, z, arcadeZ(z) ? LOG : HubBlocks.glass(tint));
		}
		c.set(x, SPRING, z, PLANKS);
		HubRoof.eave(c, x, z);
	}

	/** A pier or pilaster: blackstone base, cherry shaft, calcite capital block (y = {@code AISLE_TOP - 1}). */
	static void pier(CitadelCanvas c, int x, int z) {
		c.set(x, Y0 + 1, z, RIB);
		c.fill(x, Y0 + 2, AISLE_TOP - 2, z, LOG);
		c.set(x, AISLE_TOP - 1, z, CALCITE);
	}

	/** The aisles' ceiling ({@code |x| > 6}): deepslate with ribs on the pier lines, rafters, a lantern per bay. */
	public static void aisle(CitadelCanvas c, int x, int z) {
		int ax = Math.abs(x);
		HubRoof.terrace(c, x, z);
		c.set(x, AISLE_TOP, z, arcadeZ(z) ? RIB : FOOT);
		if (ax >= HubDims.RAFTER_X && arcadeZ(z)) {
			c.set(x, AISLE_TOP - 1, z, HubBlocks.strippedLog(Direction.Axis.X));
		}
		if (ax == HubDims.AISLE_LAMP_X && Math.floorMod(z, HubDims.PIER_PERIOD) == 0 && Math.abs(z) <= HubDims.AISLE_LAMP_Z) {
			c.set(x, AISLE_TOP - 1, z, HubBlocks.hangingLantern());
		}
	}

	/** The nave's barrel vault ({@code |x| < 6}): planks, ribs every 4 z, glass skylight along the axis. */
	public static void vault(CitadelCanvas c, int x, int z, int ax) {
		HubRoof.vault(c, x, z, ax);
	}

	// ------------------------------------------------------------------ furniture

	/** Everything that stands in the nave and the aisles; run after the shell, the board, benches and screens. */
	public static void furnish(CitadelCanvas c) {
		pews(c);
		nooks(c);
		planters(c);
		pilasters(c);
		pendants(c);
		lights(c);
	}

	/** Two rows of pews (three seats wide) either side of the rug, south half of the nave, facing the board. */
	static void pews(CitadelCanvas c) {
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int r = 0; r < HubDims.PEW_ROWS; r++) {
				int z = HubDims.PEW_Z0 + r * HubDims.PEW_STEP;
				for (int i = HubDims.PEW_X0; i <= HubDims.PEW_X1; i++) {
					c.set(sx * i, Y0 + 1, z, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH));
				}
			}
		}
	}

	/** A low table with two chairs and a planter in each aisle corner, off the benches and the walk lines. */
	static void nooks(CitadelCanvas c) {
		for (int sx = -1; sx <= 1; sx += 2) {
			Direction out = sx > 0 ? Direction.EAST : Direction.WEST;
			for (int sz = -1; sz <= 1; sz += 2) {
				int z = HubDims.NOOK_Z * sz;
				int t = HubDims.NOOK_X;
				c.set(t * sx, Y0 + 1, z, HubBlocks.slabTop(Blocks.CHERRY_SLAB));
				c.set((t - 1) * sx, Y0 + 1, z, stairs(Blocks.CHERRY_STAIRS, out.getOpposite()));
				c.set((t + 1) * sx, Y0 + 1, z, stairs(Blocks.CHERRY_STAIRS, out));
				c.set((t + 2) * sx, Y0 + 1, z, HubBlocks.pot(true));
			}
		}
	}

	/** Potted azaleas at the foot of the piers, aisle side flowering. */
	static void planters(CitadelCanvas c) {
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				for (int p : new int[] {HubDims.PLANTER_Z0, HubDims.PLANTER_Z1}) {
					c.set(HubDims.PLANTER_AISLE_X * sx, Y0 + 1, p * sz, HubBlocks.pot(true));
					c.set(HubDims.PLANTER_NAVE_X * sx, Y0 + 1, p * sz, HubBlocks.pot(false));
				}
			}
		}
	}

	/** Pilasters on the aisle walls beside the screens, on the pier lines. */
	static void pilasters(CitadelCanvas c) {
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				pier(c, HubDims.PILASTER_X * sx, HubDims.PILASTER_Z * sz);
			}
		}
	}

	/** Hanging lanterns on chains under the vault's ribs, away from the hologram column. */
	static void pendants(CitadelCanvas c) {
		for (int[] p : HubDims.PENDANTS) {
			int ceil = CitadelHub.vaultY(Math.abs(p[0]));
			int len = ceil - HubDims.PENDANT_LANTERN_Y - 1;
			for (int i = 1; i <= len; i++) {
				c.set(p[0], ceil - i, p[1], HubBlocks.chain());
			}
			c.set(p[0], ceil - len - 1, p[1], HubBlocks.hangingLantern());
		}
	}

	/**
	 * Invisible light blocks (moderate level, {@code HubBlocks.light}) one cell above the walking cell:
	 * first the lattice of street lights as seeds, then one more wherever a free floor cell is more than
	 * {@code HubDims.LIGHT_RADIUS} steps from every light placed. Only fill: the light one sees in the hub
	 * comes from its pendants and lanterns (docs/feat-hq-lighting.md).
	 */
	static void lights(CitadelCanvas c) {
		List<int[]> placed = new ArrayList<>();
		for (int x = -HubDims.LIGHT_SCAN; x <= HubDims.LIGHT_SCAN; x++) {
			for (int z = -HubDims.LIGHT_SCAN; z <= HubDims.LIGHT_SCAN; z++) {
				if (lattice(x, z) && lightFree(c, x, z)) {
					c.set(x, Y0 + 2, z, HubBlocks.light());
					placed.add(new int[] {x, z});
				}
			}
		}
		for (int x = -HubDims.LIGHT_SCAN; x <= HubDims.LIGHT_SCAN; x++) {
			for (int z = -HubDims.LIGHT_SCAN; z <= HubDims.LIGHT_SCAN; z++) {
				if (!lightFree(c, x, z) || c.at(x, Y0 + 1, z) != null) {
					continue;
				}
				boolean covered = false;
				for (int[] p : placed) {
					if (Math.abs(p[0] - x) + Math.abs(p[1] - z) <= HubDims.LIGHT_RADIUS) {
						covered = true;
						break;
					}
				}
				if (!covered) {
					c.set(x, Y0 + 2, z, HubBlocks.light());
					placed.add(new int[] {x, z});
				}
			}
		}
	}

	private static boolean lightFree(CitadelCanvas c, int x, int z) {
		return c.zoneAt(x, z).kind() == RingCampusGeometry.Kind.HUB && c.at(x, Y0 + 2, z) == null;
	}
}
