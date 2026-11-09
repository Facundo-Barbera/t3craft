package dev.t3craft.hq.citadel.hub;

import dev.t3craft.hq.citadel.CitadelCanvas;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.y0;

/**
 * The four towers flanking the north and south gables: a hollow 3 x 3 shaft at each outer vertex of the
 * faces (it never takes an interior cell, nor the diagonal paths and patio stairs the walkers use). Dark base, stone-brick shaft with
 * calcite corner posts and slit windows on the outer faces, a corbelled cornice and a stepped cherry cap
 * with a spire. Paints after the wall, on top of it.
 */
final class HubTowers {
	private HubTowers() {
	}

	/** One tower at each end of the north and south faces, flanking the gables and the bridges; the heights alternate. */
	static void paint(CitadelCanvas c) {
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				if (HubDims.TALL()) {
					campanile(c, sx * HubDims.TOWER_X(), sz * HubDims.TOWER_Z(), sx, sz, sx * sz > 0 ? HubDims.TOWER_DECK_A() : HubDims.TOWER_DECK_B());
					continue;
				}
				int top = sx * sz > 0 ? HubDims.TOWER_TOP_A() : HubDims.TOWER_TOP_B();
				tower(c, sx * HubDims.TOWER_X(), sz * HubDims.TOWER_Z(), sx, sz, top);
			}
		}
	}

	/**
	 * struct/hub5, from scale 2: the tower is a campanile. The stone-brick shaft (calcite corners, slit windows at two heights, a
	 * chiselled string course) ends in a deck with a calcite ring and a corbel course; on it an OPEN belfry: four calcite corner
	 * posts, a stone balustrade, three arched openings per side (the arch shoulders are upside-down stairs, the lintel calcite with
	 * a chiselled keystone) and a bell standing on the deck; over it the cherry pyramid, now with a pinnacle on each corner and the
	 * same finial as the lantern (calcite collar, gilded ball, post, rod), so tower and lantern talk to each other.
	 */
	private static void campanile(CitadelCanvas c, int cx, int cz, int sx, int sz, int deck) {
		int hw = HubDims.TOWER_HALF();
		int slit = deck - 4;
		int course = (y0() + 1 + deck) / 2;
		for (int dx = -hw; dx <= hw; dx++) {
			for (int dz = -hw; dz <= hw; dz++) {
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				int x = cx + dx;
				int z = cz + dz;
				if (m < hw) {
					c.set(x, deck, z, HubBlocks.darkBase(x, deck, z)); // the hollow shaft is closed by the deck
					continue;
				}
				boolean corner = Math.abs(dx) == hw && Math.abs(dz) == hw;
				boolean outer = dx == sx * hw || dz == sz * hw;
				for (int y = 0; y <= y0(); y++) {
					c.set(x, y, z, HubBlocks.darkBase(x, y, z));
				}
				for (int y = y0() + 1; y < deck; y++) {
					if (corner) {
						c.set(x, y, z, y == y0() + 1 ? CitadelCanvas.RIB : CALCITE);
					} else if (outer && (y == y0() + 4 || y == y0() + 5)) {
						c.set(x, y, z, HubBlocks.pane(y == y0() + 4 ? HubBlocks.GLASS_COLORS[1] : HubBlocks.GLASS_COLORS[0]));
					} else if (outer && (y == slit || y == slit + 1)) {
						c.set(x, y, z, HubBlocks.pane(y == slit ? HubBlocks.GLASS_COLORS[0] : HubBlocks.GLASS_COLORS[1]));
					} else if (y == course) {
						c.set(x, y, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
					} else {
						c.set(x, y, z, HubBlocks.stoneBrick(x, y, z));
					}
				}
				c.set(x, deck, z, CALCITE);
				// the belfry: posts at the corners, a balustrade and three arched openings on each side
				if (corner) {
					for (int y = deck + 1; y <= deck + 5; y++) {
						c.set(x, y, z, CALCITE);
					}
				} else {
					boolean alongZ = Math.abs(dx) == hw;
					int side = alongZ ? dz : dx;
					c.set(x, deck + 1, z, HubBlocks.stoneWall());
					if (side != 0) {
						Direction toPost = alongZ ? (side > 0 ? Direction.SOUTH : Direction.NORTH) : (side > 0 ? Direction.EAST : Direction.WEST);
						c.set(x, deck + 4, z, HubBlocks.stairsTop(Blocks.DEEPSLATE_TILE_STAIRS, toPost));
					}
					c.set(x, deck + 5, z, side == 0 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : CALCITE);
				}
				// the cornice over the belfry: a calcite ring with a corbel course round it
				c.set(x, deck + 6, z, CALCITE);
			}
		}
		for (int dx = -(hw + 1); dx <= hw + 1; dx++) {
			for (int dz = -(hw + 1); dz <= hw + 1; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) == hw + 1) {
					c.set(cx + dx, deck, cz + dz, HubBlocks.stoneStairs(inward(dx, dz), true));
					c.set(cx + dx, deck + 6, cz + dz, HubBlocks.stoneStairs(inward(dx, dz), true));
				}
			}
		}
		c.set(cx, deck + 1, cz, Blocks.BELL.defaultBlockState());
		int roof = deck + 7;
		int eave = HubDims.TOWER_EAVE();
		for (int dx = -eave; dx <= eave; dx++) {
			for (int dz = -eave; dz <= eave; dz++) {
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				for (int k = 0; k < eave + 1; k++) {
					int ring = eave - k;
					if (m == ring) {
						c.set(cx + dx, roof + k, cz + dz, HubBlocks.roofStairs(inward(dx, dz)));
					} else if (m < ring) {
						c.set(cx + dx, roof + k, cz + dz, PLANKS);
					}
				}
			}
		}
		for (int a = -1; a <= 1; a += 2) {
			for (int b = -1; b <= 1; b += 2) {
				c.set(cx + a * eave, roof + 1, cz + b * eave, HubBlocks.stoneWall());
				c.set(cx + a * eave, roof + 2, cz + b * eave, HubBlocks.rod());
			}
		}
		c.set(cx, roof + eave + 1, cz, CALCITE);
		c.set(cx, roof + eave + 2, cz, Blocks.GOLD_BLOCK.defaultBlockState());
		c.set(cx, roof + eave + 3, cz, HubBlocks.stoneWall());
		c.set(cx, roof + eave + 4, cz, HubBlocks.rod());
	}

	private static void tower(CitadelCanvas c, int cx, int cz, int sx, int sz, int topY) {
		int hw = HubDims.TOWER_HALF();
		int cornice = topY - 1;
		for (int dx = -hw; dx <= hw; dx++) {
			for (int dz = -hw; dz <= hw; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) < hw) {
					continue; // the hollow shaft
				}
				int x = cx + dx;
				int z = cz + dz;
				boolean corner = Math.abs(dx) == hw && Math.abs(dz) == hw;
				boolean outer = dx == sx * hw || dz == sz * hw;
				for (int y = 0; y <= y0(); y++) {
					c.set(x, y, z, HubBlocks.darkBase(x, y, z));
				}
				for (int y = y0() + 1; y < cornice; y++) {
					if (corner) {
						c.set(x, y, z, y == y0() + 1 ? CitadelCanvas.RIB : CALCITE);
					} else if (outer && (y == y0() + 4 || y == y0() + 5)) {
						c.set(x, y, z, HubBlocks.pane(y == y0() + 4 ? HubBlocks.GLASS_COLORS[1] : HubBlocks.GLASS_COLORS[0]));
					} else {
						c.set(x, y, z, HubBlocks.stoneBrick(x, y, z));
					}
				}
				c.set(x, cornice, z, CALCITE);
			}
		}
		int roof = topY;
		int eave = HubDims.TOWER_EAVE();
		for (int dx = -eave; dx <= eave; dx++) {
			for (int dz = -eave; dz <= eave; dz++) {
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				int x = cx + dx;
				int z = cz + dz;
				if (m == eave) {
					c.set(x, cornice, z, HubBlocks.stoneStairs(inward(dx, dz), true));
				}
				for (int k = 0; k < eave + 1; k++) {
					int ring = eave - k;
					if (m == ring) {
						c.set(x, roof + k, z, HubBlocks.roofStairs(inward(dx, dz)));
					} else if (m < ring) {
						c.set(x, roof + k, z, PLANKS);
					}
				}
			}
		}
		c.set(cx, roof + eave + 1, cz, HubBlocks.stoneWall());
		c.set(cx, roof + eave + 2, cz, HubBlocks.stoneWall());
		c.set(cx, roof + eave + 3, cz, HubBlocks.rod());
	}

	/** Direction from a ring cell towards the centre (the stair's tall back is on that side). */
	private static Direction inward(int dx, int dz) {
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx > 0 ? Direction.WEST : Direction.EAST;
		}
		return dz > 0 ? Direction.NORTH : Direction.SOUTH;
	}
}
