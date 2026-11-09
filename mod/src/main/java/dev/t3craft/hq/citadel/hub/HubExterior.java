package dev.t3craft.hq.citadel.hub;

import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelHub;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.y0;
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
		for (int x = -HubDims.SCAN(); x <= HubDims.SCAN(); x++) {
			for (int z = -HubDims.SCAN(); z <= HubDims.SCAN(); z++) {
				if (c.zoneAt(x, z).kind() == Kind.HUB_WALL) {
					outward(c, x, z);
				}
			}
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int z0 : HubDims.BUTTRESS_Z()) {
				buttress(c, sx, z0);
			}
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			niches(c, sx);
		}
		for (int sz = -1; sz <= 1; sz += 2) {
			torii(c, sz * HubDims.TORII_Z());
			toro(c, sz * HubDims.TORO_Z());
		}
		HubTowers.paint(c);
	}

	/** The plinth step (patio side only) and the corbel under the cornice, on every open side of a wall column. */
	private static void outward(CitadelCanvas c, int x, int z) {
		boolean gable = Math.abs(x) <= HubDims.NAVE_HALF() && Math.abs(z) >= HubDims.INNER();
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
					&& Math.abs(x) != HubDims.FACE() && Math.abs(z) != HubDims.FACE()) {
				pilaster(c, nx, nz);
			}
			if (HubFacade.hasWindow(x, z) && k != Kind.BRIDGE) {
				c.set(nx, y0() + 2, nz, HubBlocks.stoneSlab(true));
				c.set(nx, y0() + c.sy(6) + 1, nz, HubBlocks.stoneStairs(d.getOpposite(), true));
			}
			if (!gable && c.at(nx, CitadelHub.aisleTop(), nz) == null) {
				c.set(nx, CitadelHub.aisleTop(), nz, HubBlocks.stairsTop(Blocks.DEEPSLATE_TILE_STAIRS, d.getOpposite()));
			}
		}
	}

	/** A two-step buttress against a flat face, in line with a pier: dark base, stone-brick body, a stair on top. */
	private static void buttress(CitadelCanvas c, int sx, int z) {
		Direction toWall = sx > 0 ? Direction.WEST : Direction.EAST;
		step(c, HubDims.BUTTRESS_X0() * sx, z, y0(), HubDims.BUTTRESS_TOP0(), toWall);
		step(c, HubDims.BUTTRESS_X1() * sx, z, y0() - 1, HubDims.BUTTRESS_TOP1(), toWall);
	}

	private static void step(CitadelCanvas c, int x, int z, int darkTop, int top, Direction toWall) {
		for (int y = 0; y < top; y++) {
			c.set(x, y, z, y <= darkTop ? HubBlocks.darkBase(x, y, z) : HubBlocks.stoneBrick(x, y, z));
		}
		c.set(x, top, z, HubBlocks.stoneStairs(toWall, false));
	}

	/** A torii over a bridge: vermilion pillars, tie beam and lintel, a dark kasagi with raised tips, three lanterns. */
	private static void torii(CitadelCanvas c, int z) {
		var red = net.minecraft.world.level.block.Blocks.STRIPPED_CRIMSON_STEM;
		// heights from the lintel (y0 + 7 at scale 1): tie beam 2 below, kasagi 1 above, lanterns under the lintel
		int lintel = y0() + c.sy(7);
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int y = y0() + 1; y <= lintel - 1; y++) {
				c.set(HubDims.TORII_PILLAR() * sx, y, z, red.defaultBlockState());
			}
		}
		var beamX = red.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.X);
		for (int x = -HubDims.TORII_LINTEL(); x <= HubDims.TORII_LINTEL(); x++) {
			c.set(x, lintel - 2, z, beamX);
			c.set(x, lintel, z, beamX);
		}
		for (int x = -HubDims.TORII_ROOF(); x <= HubDims.TORII_ROOF(); x++) {
			c.set(x, lintel + 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			c.set(sx * (HubDims.TORII_ROOF() + 1), lintel + 1, z, HubBlocks.darkStairs(sx > 0 ? Direction.EAST : Direction.WEST, false));
		}
		for (int x : new int[] {-HubDims.TORII_TIE(), 0, HubDims.TORII_TIE()}) {
			c.set(x, lintel - 1, z, HubBlocks.hangingLantern());
		}
	}

	/** A pilaster against a chamfer pier: dark base, a blackstone course, calcite shaft up to the cornice. */
	private static void pilaster(CitadelCanvas c, int x, int z) {
		for (int y = 0; y <= y0(); y++) {
			c.set(x, y, z, HubBlocks.darkBase(x, y, z));
		}
		c.set(x, y0() + 1, z, CitadelCanvas.RIB);
		c.fill(x, y0() + 2, CitadelHub.aisleTop() - 1, z, CitadelCanvas.CALCITE);
	}

	/**
	 * Blind arcade on the east and west faces: between the buttresses, a niche one deep with a raised
	 * floor, a lintel, arch shoulders, a hanging lantern and a single step in front.
	 */
	private static void niches(CitadelCanvas c, int sx) {
		int x = HubDims.BUTTRESS_X0() * sx;
		java.util.Set<Integer> piers = new java.util.HashSet<>();
		for (int p : HubDims.BUTTRESS_Z()) {
			piers.add(p);
		}
		int zmax = HubDims.BUTTRESS_Z()[HubDims.BUTTRESS_Z().length - 1] - 1;
		for (int z = -zmax; z <= zmax; z++) {
			if (piers.contains(z)) {
				continue;
			}
			int u = Math.floorMod(z + HubDims.PIER_PHASE(), HubDims.PIER_PERIOD());
			c.set(x, HubDims.NICHE_FLOOR(), z, HubBlocks.darkBase(x, 1, z));
			for (int y = HubDims.NICHE_FLOOR() + 1; y < HubDims.NICHE_LINTEL(); y++) {
				c.set(x, y, z, CitadelCanvas.AIR);
			}
			c.set(x, HubDims.NICHE_LINTEL(), z, HubBlocks.stoneBrick(x, HubDims.NICHE_LINTEL(), z));
			if (u == 1 || u == 3) {
				c.set(x, HubDims.NICHE_LINTEL() - 1, z, HubBlocks.darkStairs(u == 1 ? Direction.NORTH : Direction.SOUTH, true));
			} else {
				c.set(x, HubDims.NICHE_LINTEL() - 1, z, HubBlocks.hangingLantern());
				c.set(HubDims.BUTTRESS_X1() * sx, HubDims.NICHE_FLOOR(), z, HubBlocks.darkStairs(sx > 0 ? Direction.WEST : Direction.EAST, false));
			}
		}
	}

	/** A stone lantern on each rail line at the head of a bridge: brick plinth, two posts, lantern, cap. */
	private static void toro(CitadelCanvas c, int z) {
		for (int sx = -1; sx <= 1; sx += 2) {
			int x = HubDims.TORII_PILLAR() * sx;
			c.set(x, y0() + 1, z, HubBlocks.stoneBrick(x, y0() + 1, z));
			c.set(x, y0() + 2, z, HubBlocks.stoneWall());
			c.set(x, y0() + 3, z, HubBlocks.stoneWall());
			c.set(x, y0() + 4, z, net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState());
			c.set(x, y0() + 5, z, HubBlocks.stoneSlab(false));
		}
	}
}
