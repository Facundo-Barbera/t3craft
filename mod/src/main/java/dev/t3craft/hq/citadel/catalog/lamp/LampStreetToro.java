package dev.t3craft.hq.citadel.catalog.lamp;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Kasuga-style stone lantern (tōrō), all in stone: a stepped dark foot (kiso), a slim shaft (sao), a
 * cross-shaped flared platform (chūdai), a single glowing light box (hibukuro) open to the air on every
 * side, a square hipped hat (kasa) sloping down from a crown, and a jewel finial (hōju).
 *
 * <ul>
 * <li><b>Silhouette</b>: wide → slim → wide → open cage → wide hat → point. Every level has a different
 *     width (3, 1, cross, 1, 3, 1), so it reads as a tōrō from far away, not as a stack of cubes.</li>
 * <li><b>Gradient</b>: deepslate foot, stone-brick shaft and platform, andesite cage, polished andesite hat
 *     with pale diorite tips (the curled-up corners catch the light), a diorite-wall jewel.</li>
 * <li><b>Depth</b>: the platform flares on upside-down stairs; the hat sides are stairs sloping out and the
 *     corners upper slabs, so the eave line rises at each corner (warabi-te).</li>
 * </ul>
 * Size: 3 × 3 × 4–6. Height 6 is the full tōrō; 5 drops the finial; 4 also drops the shaft.
 */
public final class LampStreetToro implements Variant {
	private static final int[][] SIDES = {{1, 0}, {1, 2}, {0, 1}, {2, 1}};
	/** The direction from each side cell to the centre. */
	private static final Direction[] IN = {Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST};
	private static final int[][] CORNERS = {{0, 0}, {2, 0}, {0, 2}, {2, 2}};

	@Override
	public String id() {
		return "lamp_street_toro";
	}

	@Override
	public Family family() {
		return Family.LAMP;
	}

	@Override
	public int minW() {
		return 3;
	}

	@Override
	public int minD() {
		return 3;
	}

	@Override
	public int minH() {
		return 4;
	}

	@Override
	public int maxW() {
		return 3;
	}

	@Override
	public int maxD() {
		return 3;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		boolean shaft = p.h() >= 5;
		boolean finial = p.h() >= 6;
		int y = 0;

		// --- kiso: a dark stepped foot, stairs stepping down on all sides (the corners take outer shapes), one
		// corner a potted fern
		p.set(c, 1, y, 1, b(Blocks.POLISHED_DEEPSLATE));
		ring(c, p, y, Blocks.DEEPSLATE_BRICK_STAIRS, Half.BOTTOM);
		int[] pot = CORNERS[p.hash(0, 0, 0, 4)];
		p.set(c, pot[0], y, pot[1], b(Blocks.POTTED_FERN));
		y++;

		// --- sao: a slim round shaft, so the lantern stands clear of its foot
		if (shaft) {
			p.set(c, 1, y++, 1, b(p.hash(1, 1, 1, 3) == 0 ? Blocks.MOSSY_STONE_BRICK_WALL : Blocks.STONE_BRICK_WALL));
		}

		// --- chūdai: a cross-shaped platform flaring on upside-down stairs, its corners left open
		p.set(c, 1, y, 1, b(Blocks.CHISELED_STONE_BRICKS));
		for (int i = 0; i < 4; i++) {
			p.set(c, SIDES[i][0], y, SIDES[i][1], stairs(Blocks.STONE_BRICK_STAIRS, IN[i]).setValue(StairBlock.HALF, Half.TOP));
		}
		y++;

		// --- hibukuro: a single glowing box, open air all round it: the waist between platform and hat
		p.set(c, 1, y, 1, b(Blocks.OCHRE_FROGLIGHT));
		y++;

		// --- kasa: a hipped stone hat sloping down on all four sides from a crown block (the corner stairs
		// face along their row, so they meet the side stairs as outer corners: a square hat, never a cross)
		p.set(c, 1, y, 1, b(Blocks.POLISHED_ANDESITE));
		ring(c, p, y, Blocks.POLISHED_ANDESITE_STAIRS, Half.BOTTOM);
		y++;

		// --- hōju: the jewel on the crown
		if (finial) {
			p.set(c, 1, y, 1, b(Blocks.DIORITE_WALL));
		}
	}

	/**
	 * The eight cells round the centre as stairs of one half: the sides face the centre, the corners face along
	 * their row towards the middle, so they meet the side stairs as outer corners (a hipped ring).
	 */
	private static void ring(CitadelCanvas c, Plot p, int y, Block stair, Half half) {
		for (int i = 0; i < 4; i++) {
			p.set(c, SIDES[i][0], y, SIDES[i][1], stairs(stair, IN[i]).setValue(StairBlock.HALF, half));
		}
		for (int[] k : CORNERS) {
			p.set(c, k[0], y, k[1], stairs(stair, k[0] == 0 ? Direction.EAST : Direction.WEST).setValue(StairBlock.HALF, half));
		}
	}
}
