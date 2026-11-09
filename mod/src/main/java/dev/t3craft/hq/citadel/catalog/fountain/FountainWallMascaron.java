package dev.t3craft.hq.citadel.catalog.fountain;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.FALL;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.WATER;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.stairTop;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A wall fountain: a carved stone mask on a backdrop of pilasters and cornice, spitting a thread of water
 * into a shell-shaped basin that bulges out towards the front.
 *
 * <p>Concept: the "nasone" and the Italian garden wall fountain, in citadel stone. The backdrop is
 * deepslate at the foot, stone brick in the middle and calcite above, with two pilasters carrying lanterns
 * and a cherry-wood hood over the mask. The face is built in relief: brow overhanging the eye sockets
 * (recessed blackstone), a nose block, cheeks of sloping stairs and a dark open mouth. It is the one fountain of
 * the family you read from a single side, meant to stand against a wall or at the end of a path.
 *
 * <pre>
 *  front view (w = 7)                         section through the mask (v →, wall at the right)
 *   ▮ ▒▒▒▒▒▒▒▒▒▒▒ ▮    y5 cornice + hood       y5       ◢ hood
 *   ▮  ▒ ▀▀▀ ▒    ▮    y4 brow                 y4      ▀   brow
 *   ▮  ▒ ◉█◉ ▒    ▮    y3 eyes, nose           y3      █ ◉ nose, sockets
 *   ▮  ▒ ◢≈◣ ▒    ▮    y2 cheeks, mouth ≈      y2      ≈│  stream from the mouth
 *   ▮ ▓▓≈≈≈≈≈≈≈▓▓ ▮    y0-1 basin shell        y0-1  ▓≈≈▓  basin
 * </pre>
 *
 * Size: 5–9 × 4–5 × 6+ (a two-row wall at the back, {@code v = d-2, d-1}; the basin is a half-ellipse in front of it).
 */
public final class FountainWallMascaron implements Variant {
	@Override
	public String id() {
		return "fountain_wall_mascaron";
	}

	@Override
	public Family family() {
		return Family.FOUNTAIN;
	}

	@Override
	public int minW() {
		return 5;
	}

	@Override
	public int minD() {
		return 4;
	}

	@Override
	public int minH() {
		return 6;
	}

	@Override
	public int maxW() {
		return 9;
	}

	@Override
	public int maxD() {
		return 5;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int cu = (w - 1) / 2;
		int face = d - 3; // v of the relief
		int wall = d - 2; // v of the wall the relief stands on (sockets and mouth are recessed into it)
		// the outermost row, v = d-1, is a plain calcite skin so nothing of the face shows from behind
		for (int u = 0; u < w; u++) {
			for (int y = 0; y <= 5; y++) {
				p.set(c, u, y, d - 1, y == 0 ? b(Blocks.DEEPSLATE_BRICKS) : y == 5 ? b(Blocks.POLISHED_DIORITE) : b(Blocks.CALCITE));
			}
		}
		backdrop(c, p, w, wall, face, cu);
		mask(c, p, wall, face, cu);
		basin(c, p, w, face, cu);
	}

	/** The wall: dark foot, stone-brick middle, calcite above, a banded cornice, pilasters with lanterns. */
	private static void backdrop(CitadelCanvas c, Plot p, int w, int wall, int face, int cu) {
		for (int u = 0; u < w; u++) {
			boolean pilaster = u == 0 || u == w - 1;
			int du = Math.abs(u - cu);
			// [palette] foot → body → top: deepslate, stone brick (mossy here and there), calcite
			p.set(c, u, 0, wall, p.hash(u, 0, wall, 4) == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS));
			p.set(c, u, 1, wall, p.hash(u, 1, wall, 4) == 0 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
			for (int y = 2; y <= 4; y++) {
				// [depth] the wall between mask and pilasters is panelled: every other column steps back as a dark recess
				boolean panel = du >= 3 && du < cu && (u % 2 == 0) && y == 3;
				p.set(c, u, y, wall, panel ? b(Blocks.POLISHED_DEEPSLATE) : p.hash(u, y, wall, 5) == 0 ? b(Blocks.SMOOTH_QUARTZ) : b(Blocks.CALCITE));
			}
			// cornice: an upside-down stair course under a slab cap
			p.set(c, u, 5, wall, b(Blocks.POLISHED_DIORITE));
			if (face >= 0 && !pilaster) {
				p.set(c, u, 4, face, stairTop(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
			}
			if (pilaster) {
				// pilaster, 1 block proud of the wall, with a lantern on the cap
				p.set(c, u, 0, face, b(Blocks.POLISHED_DEEPSLATE));
				p.set(c, u, 1, face, b(Blocks.STONE_BRICKS));
				for (int y = 2; y <= 3; y++) {
					p.set(c, u, y, face, b(Blocks.CALCITE));
				}
				p.set(c, u, 4, face, b(Blocks.CHISELED_STONE_BRICKS));
				p.set(c, u, 5, face, slab(Blocks.POLISHED_DIORITE_SLAB));
				p.set(c, u, 5, wall, b(Blocks.POLISHED_DIORITE));
			}
		}
		// the stair course below the cornice must not hide the mask's brow row at the centre
		for (int u = 1; u < w - 1; u++) {
			p.set(c, u, 5, face, stairTop(Blocks.POLISHED_DIORITE_STAIRS, Direction.SOUTH));
		}
	}

	/** The face in relief against the wall, a cherry hood above it and a gold-lined frame. */
	private static void mask(CitadelCanvas c, Plot p, int wall, int face, int cu) {
		// eyes: sockets recessed into the wall, brow above (the backdrop's course at y4 is the brow)
		for (int s = -1; s <= 1; s += 2) {
			p.set(c, cu + s, 3, face, b(Blocks.AIR));
			p.set(c, cu + s, 3, wall, b(Blocks.BLACKSTONE));
			// cheeks: stairs sloping out of the wall
			p.set(c, cu + s, 2, face, stairs(Blocks.POLISHED_ANDESITE_STAIRS, Direction.SOUTH));
			// frame of the mask in front of the wall
			p.set(c, cu + 2 * s, 2, face, b(Blocks.CHISELED_STONE_BRICKS));
			p.set(c, cu + 2 * s, 3, face, b(Blocks.STONE_BRICK_WALL));
			p.set(c, cu + 2 * s, 4, face, b(Blocks.STONE_BRICK_WALL));
		}
		p.set(c, cu, 3, face, b(Blocks.CHISELED_STONE_BRICKS)); // nose
		p.set(c, cu, 4, face, b(Blocks.CHISELED_STONE_BRICKS)); // forehead
		p.set(c, cu, 5, face, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH)); // hood
		p.set(c, cu - 1, 5, face, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH));
		p.set(c, cu + 1, 5, face, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH));
		// the mouth: dark opening with the thread of water leaving it
		// [water] the source sits in the recessed mouth (walled on every side but the front), so the thread in
		// front of it is fed and never dries up; it lands on the basin's water and goes no further
		p.set(c, cu, 2, wall, WATER);
		p.set(c, cu, 2, face, FALL);
	}

	/** The shell basin: a half-ellipse of dark base and light rim, water inside, a lip of stairs on the front. */
	private static void basin(CitadelCanvas c, Plot p, int w, int face, int cu) {
		int rx = (w - 1) / 2;
		int rz = face; // rows in front of the relief row
		for (int v = 0; v <= face; v++) {
			for (int u = 0; u < w; u++) {
				if (!inside(u, v, cu, rx, rz, face)) {
					continue;
				}
				boolean rim = !inside(u - 1, v, cu, rx, rz, face) || !inside(u + 1, v, cu, rx, rz, face) || !inside(u, v - 1, cu, rx, rz, face);
				if (rim) {
					p.set(c, u, 0, v, p.hash(u, 0, v, 3) == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.POLISHED_DEEPSLATE));
					BlockState body = p.hash(u, 1, v, 4) == 0 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
					p.set(c, u, 1, v, body);
					p.set(c, u, 2, v, slab(Blocks.POLISHED_DIORITE_SLAB));
				} else {
					p.set(c, u, 0, v, p.hash(u, 0, v, 3) == 0 ? b(Blocks.DEEPSLATE_TILES) : b(Blocks.POLISHED_DEEPSLATE));
					p.set(c, u, 1, v, WATER);
				}
			}
		}
	}

	private static boolean inside(int u, int v, int cu, int rx, int rz, int face) {
		if (v < 0 || v > face || u < 0) {
			return false;
		}
		double du = (u - cu) / (rx + 0.5);
		double dv = (face - v) / (rz + 0.5);
		return du * du + dv * dv <= 1.0;
	}
}
