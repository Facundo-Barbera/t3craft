package dev.t3craft.hq.citadel.hub;

import dev.t3craft.hq.citadel.CitadelCanvas;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.RIB;
import static dev.t3craft.hq.citadel.CitadelCanvas.y0;

/**
 * The surround of the nave's screens (struct/hub2): a black screen on a plain wall read as a hole, so each one
 * now has a frame (cherry posts and a header), a cornice that overhangs the room, a warm glow strip under the
 * cornice that washes the screen, and a cool strip along the sill. Only the cells round the screen are touched
 * (the screen, its base, the anchors and the standing cells in front stay as they were).
 */
public final class HubScreens {
	private HubScreens() {
	}

	/**
	 * The frame of a screen on a side wall.
	 *
	 * @param x the screen's plane (the last interior column, {@code +-hubX})
	 * @param zLo first z the screen covers
	 * @param zHi last z the screen covers
	 * @param height rows of the screen (it starts at {@code y0() + 2})
	 */
	public static void frame(CitadelCanvas c, int x, int zLo, int zHi, int height) {
		int sx = x > 0 ? 1 : -1;
		Direction look = sx > 0 ? Direction.WEST : Direction.EAST;
		int top = y0() + 2 + height;
		int a = zLo - 1;
		int b = zHi + 1;
		for (int z : new int[] {a, b}) {
			c.set(x, y0() + 1, z, RIB);
			for (int y = y0() + 2; y < top; y++) {
				c.fixture(new net.minecraft.core.BlockPos(x, y, z), c.kit.beam(Direction.Axis.Y));
			}
		}
		for (int z = a; z <= b; z++) {
			c.fixture(new net.minecraft.core.BlockPos(x, top, z), c.kit.beam(Direction.Axis.Z));
			// the cornice: a top slab on the wall plane and one more into the room
			c.set(x, top + 1, z, HubBlocks.slabTop(Blocks.CHERRY_SLAB));
			c.set(x - sx, top + 1, z, HubBlocks.slabTop(Blocks.CHERRY_SLAB));
		}
		c.set(x - sx, top + 1, a, HubBlocks.stairsTop(Blocks.CHERRY_STAIRS, Direction.SOUTH));
		c.set(x - sx, top + 1, b, HubBlocks.stairsTop(Blocks.CHERRY_STAIRS, Direction.NORTH));
		for (int z = zLo; z <= zHi; z++) {
			// warm wash from the cornice, cool line along the sill (no collision: the standing cells stay free)
			c.set(x - sx, top, z, c.kit.glowStrip(look, true, false));
			c.set(x - sx, y0() + 1, z, c.kit.glowStrip(look, false, true));
		}
	}

	/** The board's cornice (a ledge over its top beam) and the warm strip under it, leaving the sign's cell. */
	public static void boardCornice(CitadelCanvas c, int z, int signX) {
		int y = y0() + 11;
		for (int x = -6; x <= 6; x++) {
			c.set(x, y, z, HubBlocks.slabTop(Blocks.CHERRY_SLAB));
			c.set(x, y, z + 1, HubBlocks.slabTop(Blocks.CHERRY_SLAB));
			if (Math.abs(x) <= 4 && x != signX) {
				c.set(x, y0() + 10, z + 1, c.kit.glowStrip(Direction.SOUTH, true, false));
			}
		}
		c.set(-6, y0() + 4, z, CALCITE);
		c.set(6, y0() + 4, z, CALCITE);
		for (int yy = y0() + 5; yy <= y0() + 10; yy++) {
			c.set(-6, yy, z, c.kit.beam(Direction.Axis.Y));
			c.set(6, yy, z, c.kit.beam(Direction.Axis.Y));
		}
	}
}
