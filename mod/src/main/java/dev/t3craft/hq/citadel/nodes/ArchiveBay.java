package dev.t3craft.hq.citadel.nodes;

import dev.t3craft.hq.RingCampusGeometry.Node;
import dev.t3craft.hq.citadel.CitadelCanvas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;

/**
 * The consultation bay of the archive terminal, in the archive cloister: a niche in the gallery's north wall,
 * east of the gate, where the terminal stands (a fixture, placed by {@code CitadelNodes#cloisterFixtures}).
 *
 * <p>Offsets are from the node's centre; the terminal is at {@code (3, 1, -6)} and its screen faces south, so the
 * player stands at {@code (3, 1, -5)} (the {@code ARCHIVE} anchor). Around it:
 * <ul>
 * <li>two cherry-log posts ({@code x = 2} and {@code 4}, three high) and a log beam over them frame the
 *     terminal as a niche; above it, a plaque ("Archivo") and a hanging lantern that lights the screen;</li>
 * <li>a red rug 3 wide and 2 deep in front of it ({@code z = -5, -4}) with a white cell where the player stands,
 *     so the place to use it reads from the aisle;</li>
 * <li>the aisle in front stays clear (rings 4 and 5, no lecterns: those moved into the shelf line, see
 *     {@code NodeDecor}).</li>
 * </ul>
 * The cells {@code BAY_FROM..BAY_TO} of the north shelf line are the bay's: {@code NodeDecor} puts no bookcase there.
 */
public final class ArchiveBay {
	/** The north shelf-line cells of the bay (offsets along x): post, terminal, post. */
	public static final int BAY_FROM = 2;
	public static final int BAY_TO = 4;
	/** Offset of the terminal along x, and the shelf line's z (ring 6, north). */
	public static final int TERMINAL_X = 3;
	public static final int SHELF_Z = -6;

	private static final BlockState LANTERN_HUNG = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);

	private ArchiveBay() {
	}

	/** Paints the bay round the terminal's cell (the terminal itself is the caller's fixture). */
	public static void build(CitadelCanvas c, Node nd) {
		int cx = nd.cx();
		int cz = nd.cz();
		int wz = cz + SHELF_Z;
		// the niche: a post on each side, a beam across the top
		for (int dx : new int[] {BAY_FROM, BAY_TO}) {
			c.fill(cx + dx, 1, 3, wz, LOG);
		}
		for (int dx = BAY_FROM; dx <= BAY_TO; dx++) {
			c.set(cx + dx, 4, wz, c.kit.beam(Direction.Axis.X));
		}
		// over the terminal: the plaque on the wall behind (it faces the player) and a lantern hung from the beam
		int tx = cx + TERMINAL_X;
		c.put(tx, 2, wz, c.kit.plaque(Direction.SOUTH), DECOR);
		long k = BlockPos.asLong(tx, 2, wz);
		c.data.put(k, c.kit.plaqueData(DyeColor.ORANGE, "", "Archivo", "hilos cerrados", ""));
		c.fixtures.add(k);
		c.put(tx, 3, wz, LANTERN_HUNG, HANGING);
		// the rug in front: the player's cell (the ARCHIVE anchor) is white
		BlockState red = Blocks.CARPET.pick(DyeColor.RED).defaultBlockState();
		BlockState white = Blocks.CARPET.pick(DyeColor.WHITE).defaultBlockState();
		for (int dx = BAY_FROM; dx <= BAY_TO; dx++) {
			for (int dz = SHELF_Z + 1; dz <= SHELF_Z + 2; dz++) {
				c.put(cx + dx, 1, cz + dz, dx == TERMINAL_X && dz == SHELF_Z + 1 ? white : red, DECOR);
			}
		}
	}
}
