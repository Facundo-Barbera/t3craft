package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A small two-storey timber-framed cottage: stone plinth, framed ground floor, an upper floor that
 * juts out one block over the front (a jetty) on carved brackets, and a steep gable roof with eaves
 * on every side, a marked ridge and a chimney.
 *
 * <p>Template of quality for buildings. Read the comments tagged {@code [depth]}, {@code [palette]} and
 * {@code [finish]}: they are the art-direction techniques ({@code citadel-art-direction.md}) applied.
 *
 * <pre>
 *  local layout (front = v 0, looking down)       section through the door (v →)
 *  v0  . . path/eave . . . .                         y8+  roof /\ (deepslate tiles, cherry eave row)
 *  v1  ▒ planters ▒ step ▒ ▒   (under the jetty)    y5-7 upper floor wall at v1 (jetty)
 *  v2  ■ ground floor wall ■                        y4   beam at v1 on brackets at y3
 *  …   ■ body             ■                         y1-3 ground floor wall at v2, door at cu
 *  d-2 ■ back wall        ■                         y0   plinth (deepslate mix), step at v1
 *  d-1 . barrels, eave . .
 * </pre>
 *
 * Size: 9–13 × 8–11 × 14+ (the ridge rises one block per row of depth from y 8; the chimney is
 * clipped by the plot when there is no room).
 */
public final class HouseTimberCottage implements Variant {
	/** Ground-floor walls y1..y3, beam y4, upper walls y5..y7, top plate and roof from y8. */
	private static final int BEAM = 4;
	private static final int PLATE = 8;

	@Override
	public String id() {
		return "house_timber_cottage";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 9;
	}

	@Override
	public int minD() {
		return 8;
	}

	@Override
	public int minH() {
		return 14;
	}

	@Override
	public int maxW() {
		return 13;
	}

	@Override
	public int maxD() {
		return 11;
	}

	/** [palette] one of two schemes per seed: the same silhouette never looks identical twice in a row. */
	private record Scheme(Block frame, BlockState infill, Block roof, Block eave, Block ridge, Block door) {
	}

	private static final Scheme[] SCHEMES = {
			new Scheme(Blocks.CHERRY_LOG, b(Blocks.CALCITE), Blocks.DEEPSLATE_TILE_STAIRS, Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB, Blocks.CHERRY_DOOR),
			new Scheme(Blocks.DARK_OAK_LOG, b(Blocks.CALCITE), Blocks.CHERRY_STAIRS, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DARK_OAK_DOOR),
	};

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x5EED_C077L);
		Scheme s = SCHEMES[r.nextInt(SCHEMES.length)];
		int w = p.w();
		int d = p.d();
		int u1 = 1;
		int u2 = w - 2; // side walls (eaves overhang to u 0 and w-1)
		int vf = 2; // ground floor front wall
		int vj = 1; // upper floor front wall (the jetty)
		int vb = d - 2; // back wall
		int cu = w / 2; // door column

		plinth(c, p, u1, u2, vf, vb);
		groundFloor(c, p, s, u1, u2, vf, vb, cu);
		jetty(c, p, s, u1, u2, vj, vf, cu);
		upperFloor(c, p, s, u1, u2, vj, vb, cu);
		roof(c, p, s, u1, u2, vj, vb);
		chimney(c, p, u2 - 1, vb - 1);
		yard(c, p, r, u1, u2, vf, vb, cu);
	}

	// ------------------------------------------------------------------ base

	/** [palette] the heavy, dark band: three deepslate textures mixed by a per-cell hash, never uniform. */
	private static BlockState stone(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 6)) {
			case 0 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
			case 1 -> b(Blocks.COBBLED_DEEPSLATE);
			default -> b(Blocks.DEEPSLATE_BRICKS);
		};
	}

	private static void plinth(CitadelCanvas c, Plot p, int u1, int u2, int vf, int vb) {
		for (int u = u1; u <= u2; u++) {
			for (int v = vf; v <= vb; v++) {
				boolean edge = u == u1 || u == u2 || v == vf || v == vb;
				p.set(c, u, 0, v, edge ? stone(p, u, 0, v) : b(Blocks.SPRUCE_PLANKS));
			}
		}
	}

	// ------------------------------------------------------------------ ground floor (y1..y3)

	private static BlockState post(Scheme s) {
		return s.frame().defaultBlockState(); // axis Y
	}

	private static BlockState beam(Scheme s, Direction.Axis axis) {
		return s.frame().defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	/** Posts of a wall run a..b: both ends, both sides of the door, and every 4 cells otherwise. */
	private static boolean isPost(int i, int a, int b, int door) {
		return i == a || i == b || (door >= 0 && Math.abs(i - door) == 1) || (door < 0 && (i - a) % 4 == 0);
	}

	/** A window cell: inside a gap between posts, not touching a post (or the only cell of a 1-wide gap). */
	private static boolean isWindow(int i, int a, int b, int door) {
		if (isPost(i, a, b, door) || i == door) {
			return false;
		}
		boolean left = isPost(i - 1, a, b, door) || i - 1 == door;
		boolean right = isPost(i + 1, a, b, door) || i + 1 == door;
		return left == right; // a 1-wide gap, or the inner cells of a wider one
	}

	private static BlockState pane(boolean alongU, boolean accent) {
		BlockState g = accent ? Blocks.STAINED_GLASS_PANE.pick(net.minecraft.world.item.DyeColor.PINK).defaultBlockState() : b(Blocks.GLASS_PANE);
		return alongU ? g.setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true)
				: g.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true);
	}

	private static void groundFloor(CitadelCanvas c, Plot p, Scheme s, int u1, int u2, int vf, int vb, int cu) {
		for (int y = 1; y <= 3; y++) {
			// front wall with the door
			for (int u = u1; u <= u2; u++) {
				BlockState st;
				if (u == cu) {
					st = y == 3 ? beam(s, Direction.Axis.X) // [finish] lintel over the door
							: s.door().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH) // panel at the back: [depth] a recessed door
									.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, y == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
				} else if (isPost(u, u1, u2, cu)) {
					st = post(s);
				} else if (y == 2 && isWindow(u, u1, u2, cu)) {
					st = pane(true, false);
				} else {
					st = s.infill();
				}
				p.set(c, u, y, vf, st);
			}
			// side walls and back wall: posts every 4, one window per gap
			for (int v = vf + 1; v <= vb; v++) {
				for (int u : new int[] {u1, u2}) {
					boolean postV = isPost(v, vf, vb, -1);
					p.set(c, u, y, v, postV ? post(s) : y == 2 && isWindow(v, vf, vb, -1) ? pane(false, false) : s.infill());
				}
			}
			for (int u = u1 + 1; u < u2; u++) {
				p.set(c, u, y, vb, isPost(u, u1, u2, -1) ? post(s) : y == 2 && isWindow(u, u1, u2, -1) ? pane(true, false) : s.infill());
			}
		}
	}

	// ------------------------------------------------------------------ jetty (y3..y4 at the front)

	/**
	 * [depth] the upper floor overhangs the ground floor by one block: a beam row at y4 over v = vj,
	 * carried by upside-down stairs (brackets) at y3 under every front post. The porch it makes gets
	 * lanterns hanging on both sides of the door.
	 */
	private static void jetty(CitadelCanvas c, Plot p, Scheme s, int u1, int u2, int vj, int vf, int cu) {
		for (int u = u1; u <= u2; u++) {
			p.set(c, u, BEAM, vj, beam(s, Direction.Axis.X));
			if (isPost(u, u1, u2, cu)) {
				p.set(c, u, 3, vj, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH).setValue(BlockStateProperties.HALF, Half.TOP));
			}
		}
		// the floor between the storeys: beams round the edge, planks inside
		int vb = p.d() - 2;
		for (int u = u1; u <= u2; u++) {
			for (int v = vf; v <= vb; v++) {
				boolean side = u == u1 || u == u2;
				boolean edge = side || v == vf || v == vb;
				p.set(c, u, BEAM, v, edge ? beam(s, side ? Direction.Axis.Z : Direction.Axis.X) : b(Blocks.SPRUCE_PLANKS));
			}
		}
		// [finish] porch lanterns, hanging from the jetty beam beside the door
		BlockState hanging = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
		p.put(c, cu - 2, 3, vj, hanging, CitadelCanvas.HANGING);
		p.put(c, cu + 2, 3, vj, hanging, CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ upper floor (y5..y7)

	private static void upperFloor(CitadelCanvas c, Plot p, Scheme s, int u1, int u2, int vj, int vb, int cu) {
		for (int y = 5; y <= 7; y++) {
			for (int u = u1; u <= u2; u++) {
				boolean post = isPost(u, u1, u2, -1) || u == cu;
				BlockState st;
				if (post) {
					st = post(s);
				} else if (y == 6) {
					st = pane(true, Math.abs(u - cu) == 1); // [palette] tinted glass as accent beside the centre post
				} else {
					st = s.infill();
				}
				p.set(c, u, y, vj, st);
				p.set(c, u, y, vb, isPost(u, u1, u2, -1) ? post(s) : y == 6 && isWindow(u, u1, u2, -1) ? pane(true, false) : s.infill());
			}
			for (int v = vj + 1; v < vb; v++) {
				for (int u : new int[] {u1, u2}) {
					p.set(c, u, y, v, isPost(v, vj, vb, -1) ? post(s) : y == 6 && isWindow(v, vj, vb, -1) ? pane(false, false) : s.infill());
				}
			}
		}
		// [depth] window sills: upside-down stairs sticking out under every front window
		for (int u = u1 + 1; u < u2; u++) {
			if (!(isPost(u, u1, u2, -1) || u == cu)) {
				p.set(c, u, 5, vj - 1, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH).setValue(BlockStateProperties.HALF, Half.TOP));
			}
		}
		// [depth] a top plate: a horizontal beam over every wall at y8, under the roof
		for (int u = u1; u <= u2; u++) {
			p.set(c, u, PLATE, vj, beam(s, Direction.Axis.X));
			p.set(c, u, PLATE, vb, beam(s, Direction.Axis.X));
		}
		for (int v = vj; v <= vb; v++) {
			p.set(c, u1, PLATE, v, beam(s, Direction.Axis.Z));
			p.set(c, u2, PLATE, v, beam(s, Direction.Axis.Z));
		}
	}

	// ------------------------------------------------------------------ roof (y8+)

	/** Height of the roof surface over row v: one up per row from both eaves. */
	private static int roofY(int v, int d) {
		return PLATE + Math.min(v, d - 1 - v);
	}

	/**
	 * [finish] gable roof, ridge along u: eaves overhang one block on all sides (u 0 and w-1, v 0 and
	 * d-1), the lowest row in a contrasting material (a coloured eave line), a slab cap on the ridge,
	 * gable triangles in planks with a central king post and a small window.
	 */
	private static void roof(CitadelCanvas c, Plot p, Scheme s, int u1, int u2, int vj, int vb) {
		int w = p.w();
		int d = p.d();
		for (int v = 0; v < d; v++) {
			int y = roofY(v, d);
			boolean front = v < d - 1 - v;
			boolean middle = v == d - 1 - v;
			boolean eave = v == 0 || v == d - 1;
			for (int u = 0; u < w; u++) {
				BlockState st;
				if (middle) {
					st = b(Blocks.POLISHED_DEEPSLATE); // odd depth: a full ridge row
				} else {
					st = stairs(eave ? s.eave() : s.roof(), front ? Direction.SOUTH : Direction.NORTH);
				}
				p.set(c, u, y, v, st);
				// [depth] under the overhanging gable rows, an upside-down stair closes the eave
				if ((u == 0 || u == w - 1) && !middle && y - 1 >= PLATE) {
					p.set(c, u, y - 1, v, stairs(s.roof(), front ? Direction.NORTH : Direction.SOUTH).setValue(BlockStateProperties.HALF, Half.TOP));
				}
			}
		}
		// ridge cap
		int top = roofY((d - 1) / 2, d);
		for (int u = 0; u < w; u++) {
			if (d % 2 == 1) {
				p.set(c, u, top + 1, d / 2, slab(s.ridge()));
			} else {
				p.set(c, u, top + 1, d / 2 - 1, slab(s.ridge()));
				p.set(c, u, top + 1, d / 2, slab(s.ridge()));
			}
		}
		// gables: fill the triangles of both side walls under the roof
		int mid = (vj + vb) / 2;
		for (int u : new int[] {u1, u2}) {
			for (int v = vj; v <= vb; v++) {
				for (int y = PLATE + 1; y < roofY(v, d); y++) {
					BlockState st = v == mid ? post(s) : b(Blocks.CHERRY_PLANKS);
					if (y == PLATE + 2 && Math.abs(v - mid) == 1) {
						st = pane(false, true); // [palette] a pair of tinted lights flanking the king post
					}
					p.set(c, u, y, v, st);
				}
			}
		}
	}

	/** [finish] a brick and deepslate chimney through the back slope, capped with a wall and a slab. */
	private static void chimney(CitadelCanvas c, Plot p, int u, int v) {
		int d = p.d();
		int top = Math.min(roofY((d - 1) / 2, d) + 2, p.h() - 1);
		for (int y = PLATE; y < top; y++) {
			p.set(c, u, y, v, y < roofY(v, d) ? b(Blocks.BRICKS) : p.hash(u, y, v, 3) == 0 ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.BRICKS));
		}
		p.set(c, u, top, v, slab(Blocks.DEEPSLATE_BRICK_SLAB).setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
	}

	// ------------------------------------------------------------------ yard (y0, outside the walls)

	/**
	 * [finish] the transition to the ground and the human-scale props: a step at the door, planters
	 * under the porch, a stone ledge along the plinth, barrels and a woodpile in the side and back
	 * strips. Which props go where depends on the seed.
	 */
	private static void yard(CitadelCanvas c, Plot p, Random r, int u1, int u2, int vf, int vb, int cu) {
		p.set(c, cu, 0, vf - 1, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH));
		for (int u = u1; u <= u2; u++) {
			if (u == cu) {
				continue;
			}
			if (Math.abs(u - cu) >= 2 && u != u1 && u != u2 && isWindow(u, u1, u2, cu)) {
				p.set(c, u, 0, vf - 1, b(Blocks.MOSS_BLOCK));
				p.set(c, u, 1, vf - 1, r.nextBoolean() ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.AZALEA));
			} else {
				p.set(c, u, 0, vf - 1, slab(Blocks.POLISHED_DEEPSLATE_SLAB));
			}
		}
		// side strip (u 0 or w-1): a woodpile, a barrel or a pot, by seed
		int side = r.nextBoolean() ? 0 : p.w() - 1;
		for (int v = vf + 1; v < vb; v++) {
			int k = p.hash(side, 0, v, 4);
			if (k == 0) {
				p.set(c, side, 0, v, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
			} else if (k == 1) {
				p.set(c, side, 0, v, b(Blocks.STRIPPED_SPRUCE_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
			} else if (k == 2) {
				p.set(c, side, 0, v, b(Blocks.POTTED_FERN));
			}
		}
		// back strip: barrels and a composter
		int back = p.d() - 1;
		p.set(c, u1 + 1, 0, back, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		p.set(c, u1 + 2, 0, back, b(Blocks.COMPOSTER));
		p.set(c, u2 - 1, 0, back, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
	}
}
