package dev.t3craft.hq.citadel.hub;

import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelHub;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.Y0;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

/**
 * What the hub adds on the cells just outside its wall, after {@code CitadelHub.core} has painted the
 * patio and the bridges (it must run after, it paints on top): a plinth step round the base, a corbel
 * course under the cornice, buttresses on the east and west faces and a torii gate on each bridge.
 * Everything leaves the bridge decks, the patio stairs, the paths on the east-west axis and the walk
 * spots untouched.
 */
public final class HubExterior {
	private HubExterior() {
	}

	/** Paints the plinth, corbels, buttresses and gates. */
	public static void paint(CitadelCanvas c) {
		for (int x = -HubDims.SCAN; x <= HubDims.SCAN; x++) {
			for (int z = -HubDims.SCAN; z <= HubDims.SCAN; z++) {
				if (c.zoneAt(x, z).kind() == Kind.HUB_WALL) {
					outward(c, x, z);
				}
			}
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int z0 : HubDims.BUTTRESS_Z) {
				buttress(c, sx, z0);
			}
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			niches(c, sx);
		}
		for (int sz = -1; sz <= 1; sz += 2) {
			torii(c, sz * HubDims.TORII_Z);
			toro(c, sz * HubDims.TORO_Z);
		}
		HubTowers.paint(c);
	}

	/** The plinth step (patio side only) and the corbel under the cornice, on every open side of a wall column. */
	private static void outward(CitadelCanvas c, int x, int z) {
		boolean gable = Math.abs(x) <= HubDims.NAVE_HALF && Math.abs(z) >= HubDims.INNER;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			int nx = x + d.getStepX();
			int nz = z + d.getStepZ();
			Kind k = c.zoneAt(nx, nz).kind();
			if (k == Kind.HUB || k == Kind.HUB_WALL || k == Kind.NONE) {
				continue;
			}
			if (k == Kind.PATIO && c.at(nx, 1, nz) == null) {
				c.set(nx, 1, nz, stairs(Blocks.DEEPSLATE_TILE_STAIRS, d.getOpposite()));
			}
			if (!gable && k == Kind.PATIO && c.at(nx, 1, nz) == null && HubFacade.pier(x, z)
					&& Math.abs(x) != HubDims.FACE && Math.abs(z) != HubDims.FACE) {
				pilaster(c, nx, nz);
			}
			if (HubFacade.hasWindow(x, z) && k != Kind.BRIDGE) {
				c.set(nx, Y0 + 2, nz, HubBlocks.stoneSlab(true));
				c.set(nx, Y0 + 7, nz, HubBlocks.stoneStairs(d.getOpposite(), true));
			}
			if (!gable && c.at(nx, CitadelHub.AISLE_TOP, nz) == null) {
				c.set(nx, CitadelHub.AISLE_TOP, nz, HubBlocks.stairsTop(Blocks.DEEPSLATE_TILE_STAIRS, d.getOpposite()));
			}
		}
	}

	/** A two-step buttress of deepslate tiles against a flat face, in line with a pier. */
	private static void buttress(CitadelCanvas c, int sx, int z) {
		Direction toWall = sx > 0 ? Direction.WEST : Direction.EAST;
		c.fill(HubDims.BUTTRESS_X0 * sx, 0, HubDims.BUTTRESS_TOP0 - 1, z, FOOT);
		c.set(HubDims.BUTTRESS_X0 * sx, HubDims.BUTTRESS_TOP0, z, stairs(Blocks.DEEPSLATE_TILE_STAIRS, toWall));
		c.fill(HubDims.BUTTRESS_X1 * sx, 0, HubDims.BUTTRESS_TOP1 - 1, z, FOOT);
		c.set(HubDims.BUTTRESS_X1 * sx, HubDims.BUTTRESS_TOP1, z, stairs(Blocks.DEEPSLATE_TILE_STAIRS, toWall));
	}

	/** A torii over a bridge: two cherry pillars on the deck's edges, tie beam, lintel, dark roof, three lanterns. */
	private static void torii(CitadelCanvas c, int z) {
		for (int sx = -1; sx <= 1; sx += 2) {
			c.fill(HubDims.TORII_PILLAR * sx, Y0 + 1, Y0 + 6, z, LOG);
		}
		for (int x = -HubDims.TORII_TIE; x <= HubDims.TORII_TIE; x++) {
			c.set(x, Y0 + 5, z, PLANKS);
		}
		for (int x = -HubDims.TORII_LINTEL; x <= HubDims.TORII_LINTEL; x++) {
			c.set(x, Y0 + 7, z, PLANKS);
		}
		for (int x = -HubDims.TORII_ROOF; x <= HubDims.TORII_ROOF; x++) {
			c.set(x, Y0 + 8, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
		for (int x : new int[] {-HubDims.TORII_TIE, 0, HubDims.TORII_TIE}) {
			c.set(x, Y0 + 6, z, HubBlocks.hangingLantern());
		}
	}

	/** A pilaster against a chamfer pier: dark base, a blackstone course, calcite shaft up to the cornice. */
	private static void pilaster(CitadelCanvas c, int x, int z) {
		for (int y = 0; y <= Y0; y++) {
			c.set(x, y, z, HubBlocks.darkBase(x, y, z));
		}
		c.set(x, Y0 + 1, z, CitadelCanvas.RIB);
		c.fill(x, Y0 + 2, CitadelHub.AISLE_TOP - 1, z, CitadelCanvas.CALCITE);
	}

	/**
	 * Blind arcade on the east and west faces: between the buttresses, a niche one deep with a raised
	 * floor, a lintel, arch shoulders, a hanging lantern and a single step in front.
	 */
	private static void niches(CitadelCanvas c, int sx) {
		int x = HubDims.BUTTRESS_X0 * sx;
		java.util.Set<Integer> piers = new java.util.HashSet<>();
		for (int p : HubDims.BUTTRESS_Z) {
			piers.add(p);
		}
		int zmax = HubDims.BUTTRESS_Z[HubDims.BUTTRESS_Z.length - 1] - 1;
		for (int z = -zmax; z <= zmax; z++) {
			if (piers.contains(z)) {
				continue;
			}
			int u = Math.floorMod(z + HubDims.PIER_PHASE, HubDims.PIER_PERIOD);
			c.set(x, HubDims.NICHE_FLOOR, z, HubBlocks.darkBase(x, 1, z));
			for (int y = HubDims.NICHE_FLOOR + 1; y < HubDims.NICHE_LINTEL; y++) {
				c.set(x, y, z, CitadelCanvas.AIR);
			}
			c.set(x, HubDims.NICHE_LINTEL, z, HubBlocks.stoneBrick(x, HubDims.NICHE_LINTEL, z));
			if (u == 1 || u == 3) {
				c.set(x, HubDims.NICHE_LINTEL - 1, z, HubBlocks.darkStairs(u == 1 ? Direction.NORTH : Direction.SOUTH, true));
			} else {
				c.set(x, HubDims.NICHE_LINTEL - 1, z, HubBlocks.hangingLantern());
				c.set(HubDims.BUTTRESS_X1 * sx, HubDims.NICHE_FLOOR, z, HubBlocks.darkStairs(sx > 0 ? Direction.WEST : Direction.EAST, false));
			}
		}
	}

	/** A stone lantern on each rail line at the head of a bridge: brick plinth, two posts, lantern, cap. */
	private static void toro(CitadelCanvas c, int z) {
		for (int sx = -1; sx <= 1; sx += 2) {
			int x = HubDims.TORII_PILLAR * sx;
			c.set(x, Y0 + 1, z, HubBlocks.stoneBrick(x, Y0 + 1, z));
			c.set(x, Y0 + 2, z, HubBlocks.stoneWall());
			c.set(x, Y0 + 3, z, HubBlocks.stoneWall());
			c.set(x, Y0 + 4, z, net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState());
			c.set(x, Y0 + 5, z, HubBlocks.stoneSlab(false));
		}
	}
}
