package dev.t3craft.hq.citadel.nodes;

import dev.t3craft.hq.RingCampusGeometry.Node;
import dev.t3craft.hq.citadel.CitadelCanvas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;

/**
 * The consultation bay of the archive terminal, in the archive cloister: a niche in the gallery's north wall,
 * east of the gate, where the terminal stands (a fixture, placed by {@code CitadelNodes#cloisterFixtures}).
 *
 * <p><b>Design and real coordinates.</b> The cloister is designed at scale 1 (15 × 15, offsets from the node's
 * centre) and {@code CitadelNodes} magnifies it horizontally by the citadel's scale {@code k} (default 2). The
 * design bay is the north shelf line (design {@code z = -6}) between {@code x = 2} and {@code 4}, where
 * {@code NodeDecor} puts no bookcase; the terminal is at design {@code (3, 1, -6)}, screen south, so the player
 * stands in front of it (the {@code ARCHIVE} anchor). This class paints the bay <b>after</b> the magnification, in
 * real offsets worked out from {@code k} ({@link #span}, {@link #shelfRows}): the terminal sits in the middle of
 * the span, and the niche keeps its size (3 wide, never stretched):
 * <ul>
 * <li>two cherry-log posts three high and a log beam over them frame the terminal (from scale 2, three cells
 *     inside: the terminal between two desk wings, a lectern with a book on one and a lantern on the other, with
 *     upside-down stairs under the beam as a chamfer; behind the terminal a calcite backdrop); the rest of the
 *     span, on both sides and behind (the shelf line is {@code k} cells deep), is bookcases like the rest of the wall;</li>
 * <li>over the terminal, a plaque ("Archivo") on the wall behind and a lantern hung from the beam, which lights
 *     the screen;</li>
 * <li>a grey rug as wide as the niche in front of it, with a gold cell where the player stands, so the place to
 *     use the terminal reads from the aisle (the aisle in front stays clear: lecterns moved into the shelf line,
 *     see {@code NodeDecor}).</li>
 * </ul>
 */
public final class ArchiveBay {
	/** The north shelf-line cells of the design bay (offsets along x): post, terminal, post. */
	public static final int BAY_FROM = 2;
	public static final int BAY_TO = 4;

	private static final BlockState LANTERN_HUNG = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);

	private ArchiveBay() {
	}

	/** The design offset of a real offset {@code n}: {@code round(|n| / k)} with the sign of {@code n}. */
	private static int design(int n, double k) {
		int a = (int) Math.floor(Math.abs(n) / k + 0.5);
		return n < 0 ? -a : a;
	}

	/** The real x offsets {@code {lo, hi}} of the design bay's span (design x 2..4). */
	static int[] span(double k) {
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int n = 0; n <= (int) Math.ceil(8 * k); n++) {
			int d = design(n, k);
			if (d >= BAY_FROM && d <= BAY_TO) {
				lo = Math.min(lo, n);
				hi = Math.max(hi, n);
			}
		}
		return new int[] {lo, hi};
	}

	/** The real z offsets of the design north shelf line (design z = -6), from the aisle side outwards (decreasing). */
	static int[] shelfRows(double k) {
		int first = Integer.MIN_VALUE;
		int last = Integer.MAX_VALUE;
		for (int n = 0; n >= -(int) Math.ceil(8 * k); n--) {
			if (design(n, k) == -6) {
				first = Math.max(first, n);
				last = Math.min(last, n);
			}
		}
		return new int[] {first, last};
	}

	/** Real x offset of the terminal: the middle of the bay's span (3 at scale 1). */
	public static int terminalX(double k) {
		int[] s = span(k);
		return (s[0] + s[1]) / 2;
	}

	/** Real z offset of the terminal: the aisle-side row of the north shelf line (-6 at scale 1). */
	public static int terminalZ(double k) {
		return shelfRows(k)[0];
	}

	/** Paints the bay round the terminal's cell (the terminal itself is the caller's fixture). */
	public static void build(CitadelCanvas c, Node nd) {
		double k = c.k;
		int cx = nd.cx();
		int cz = nd.cz();
		int[] span = span(k);
		int[] rows = shelfRows(k);
		int tdx = terminalX(k);
		int front = rows[0];
		// the niche keeps its size: posts 2 either side of the terminal when the span allows it (scale 2 and up: three
		// cells inside, the terminal between two desk wings), else 1 either side (scale 1)
		int half = span[1] - span[0] >= 5 ? 2 : 1;
		BlockState topSlab = NodeDecor.topSlab(Blocks.CHERRY_SLAB);
		for (int dx = span[0]; dx <= span[1]; dx++) {
			int off = dx - tdx;
			boolean niche = Math.abs(off) <= half;
			for (int dz = rows[1]; dz <= rows[0]; dz++) {
				boolean frontRow = dz == front;
				for (int y = 1; y <= 3; y++) {
					BlockState st;
					if (!niche) {
						st = bookcase(y);
					} else if (Math.abs(off) == half) {
						st = LOG;
					} else if (off == 0) {
						// the middle: the terminal (front row); behind it a pale backdrop that makes the dark screen and
						// the plaque read
						st = frontRow ? null : CALCITE;
					} else if (frontRow) {
						// a desk wing: a table top beside the terminal with a lectern (left) or a lantern (right) on it
						st = y == 1 ? topSlab : y == 2 ? wingTop(off < 0) : (y == 3 ? archStairs(off) : null);
					} else {
						st = bookcase(y);
					}
					if (st != null) {
						c.set(cx + dx, y, cz + dz, st);
					}
				}
				if (niche) {
					c.set(cx + dx, 4, cz + dz, c.kit.beam(Direction.Axis.X));
				}
			}
		}
		// over the terminal: the plaque on the wall behind (it faces the player) and a lantern hung from the beam
		int x = cx + tdx;
		int z = cz + front;
		c.put(x, 2, z, c.kit.plaque(Direction.SOUTH), DECOR);
		c.data.put(BlockPos.asLong(x, 2, z), c.kit.plaqueData(DyeColor.ORANGE, "", "Archivo", "hilos cerrados", ""));
		c.fixtures.add(BlockPos.asLong(x, 2, z));
		c.put(x, 3, z, LANTERN_HUNG, HANGING);
		// the rug in front, as wide as the niche; the player's cell (the ARCHIVE anchor) is a gold cell
		BlockState rug = Blocks.CARPET.pick(DyeColor.GRAY).defaultBlockState();
		BlockState stand = Blocks.CARPET.pick(DyeColor.YELLOW).defaultBlockState();
		int depth = Math.max(2, (int) Math.round(2 * k));
		for (int dx = tdx - 1; dx <= tdx + 1; dx++) {
			for (int i = 1; i <= depth; i++) {
				c.put(cx + dx, 1, cz + front + i, dx == tdx && i == 1 ? stand : rug, DECOR);
			}
		}
	}

	/** What stands on a desk wing (cell y = 2): a lectern with a book on the west one, a standing lantern on the east one. */
	private static BlockState wingTop(boolean west) {
		return west ? NodeDecor.facing(Blocks.LECTERN, Direction.SOUTH).setValue(BlockStateProperties.HAS_BOOK, true)
				: Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false);
	}

	/** The chamfer under the beam on the cells beside the middle: an upside-down stair whose low step faces the post. */
	private static BlockState archStairs(int off) {
		return NodeDecor.invStairs(Blocks.CHERRY_STAIRS, off < 0 ? Direction.WEST : Direction.EAST);
	}

	/** One bookcase cell of the bay's shelf line at height {@code y}: chiseled, bookshelf, chiseled. */
	private static BlockState bookcase(int y) {
		return y == 2 ? b(Blocks.BOOKSHELF) : NodeDecor.facing(Blocks.CHISELED_BOOKSHELF, Direction.SOUTH);
	}
}
