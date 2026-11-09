package dev.t3craft.hq.citadel.catalog.lamp;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Cherry street lamp: a stone foot, a stone-brick wall plinth, a slim cherry mast with upper-slab collars, one or two arms
 * and a light at every tip. Three silhouettes by plot and seed:
 *
 * <ul>
 * <li><b>Crook</b> (height 5+, seed): the arm springs from the very top of the mast and the light hangs under its
 *     tip, on an iron chain from height 6 (the lantern stays at or above y3, so a walker never ducks);</li>
 * <li><b>Crossarm</b> (height 6, seed): the arm crosses the mast three below its top, a light box stands on each
 *     tip under a thin roof, and the mast ends in a matching cap;</li>
 * <li><b>Box</b> (no room for an arm: height 4, or a 1 × 1 plot): the mast carries one light box with its roof.</li>
 * </ul>
 *
 * <p>Plots from 5 wide are a flanking pair of masts with their arms turned inwards (feet dressed with a planted pot);
 * narrower ones are a single mast with one arm or two (front and back, else left and right), by seed. Every choice is
 * a per-plot seeded stream ({@code pick}), so the three seeds of a size differ: the light (vanilla lantern, the kit's
 * paper lantern via {@link PaperLanterns} with its vanilla fallback, or a froglight box), the roof (cherry plate
 * or slab) and the foot (deepslate, chiseled, bricks or a lighter stone brick).
 *
 * <ul>
 * <li><b>Gradient dark → light</b>: deepslate or stone foot, stone-brick wall plinth, cherry fence mast, cherry slab
 *     collars, cherry roofs.</li>
 * <li><b>Depth</b>: the collar is a ring wider than the post; arms of two blocks get an upside-down stair corbel; roofs
 *     sit directly on the light; a 3 × 3 plot gets a stepped plinth round the foot.</li>
 * <li><b>Support</b>: every light hangs from a chain or fence, or stands on a fence under its roof. The arms stay
 *     inside the plot and at least 3 blocks above the ground.</li>
 * </ul>
 * Size: 1–9 × 1–5 × 4–6.
 */
public final class LampCherryPost implements Variant {
	private static final BlockState FENCE = b(Blocks.CHERRY_FENCE);
	/** The collar: an upper-half cherry slab, a ring wider than the post just under the arm joint or the light box. */
	private static final BlockState COLLAR = slab(Blocks.CHERRY_SLAB).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	private static final BlockState LANTERN = b(Blocks.LANTERN);

	@Override
	public String id() {
		return "lamp_cherry_post";
	}

	@Override
	public Family family() {
		return Family.LAMP;
	}

	@Override
	public int minW() {
		return 1;
	}

	@Override
	public int minD() {
		return 1;
	}

	@Override
	public int minH() {
		return 4;
	}

	@Override
	public int maxW() {
		return 9;
	}

	@Override
	public int maxD() {
		return 5;
	}

	/** One arm: the direction it points (local), its length and the light kind at its tip. */
	private record Arm(Direction dir, int len) {
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int hh = Math.min(p.h(), 6);
		int kind = pick(p, 1, 3); // 0 vanilla lantern, 1 kit paper lantern, 2 froglight box
		String tone = "paper"; // the warm tone: the rose one read as red wool against the cherry
		boolean two = pick(p, 3, 2) == 0;
		boolean pair = p.w() >= 5;
		int cv = p.d() >= 5 ? 2 : p.d() >= 2 ? 1 : 0;
		int[] posts = pair ? new int[] {0, p.w() - 1} : new int[] {p.w() / 2};
		// style: 0 box (no arm), 1 crook, 2 crossarm
		boolean canArm = hh >= 5;
		int style = 0;
		if (canArm) {
			style = hh >= 6 && pick(p, 5, 2) == 1 ? 2 : 1;
		}
		for (int cu : posts) {
			List<Arm> arms = canArm ? arms(p, cu, cv, pair, two) : List.of();
			if (arms.isEmpty()) {
				box(c, p, cu, cv, hh, kind, tone);
			} else {
				mast(c, p, cu, cv, hh, style, arms, kind, tone);
			}
		}
		if (!pair && p.w() >= 3 && p.d() >= 3) {
			plinth(c, p, posts[0], cv);
		}
		if (pair) {
			for (int cu : posts) {
				dress(c, p, cu, cv);
			}
		}
	}

	/** A seeded choice in 0..n-1, one stream per salt (the cell hash of {@link Plot} is too regular for whole-plot choices). */
	private static int pick(Plot p, int salt, int n) {
		java.util.Random r = p.rng(0x1A47L * salt);
		r.nextInt();
		return r.nextInt(n);
	}

	// ------------------------------------------------------------------ layout

	private static int room(Plot p, int u, int v, Direction d) {
		return switch (d) {
			case NORTH -> v;
			case SOUTH -> p.d() - 1 - v;
			case WEST -> u;
			default -> p.w() - 1 - u;
		};
	}

	private static List<Arm> arms(Plot p, int cu, int cv, boolean pair, boolean two) {
		List<Arm> out = new ArrayList<>();
		if (pair) {
			Direction in = cu == 0 ? Direction.EAST : Direction.WEST;
			out.add(new Arm(in, p.w() >= 7 ? 2 : 1));
			if (two && room(p, cu, cv, Direction.NORTH) >= 1) {
				out.add(new Arm(Direction.NORTH, Math.min(2, cv)));
			}
			return out;
		}
		int n = room(p, cu, cv, Direction.NORTH);
		int s = room(p, cu, cv, Direction.SOUTH);
		int w = room(p, cu, cv, Direction.WEST);
		int e = room(p, cu, cv, Direction.EAST);
		if (two) {
			if (n >= 1 && s >= 1) {
				out.add(new Arm(Direction.NORTH, Math.min(2, n)));
				out.add(new Arm(Direction.SOUTH, Math.min(2, s)));
				return out;
			}
			if (w >= 1 && e >= 1) {
				out.add(new Arm(Direction.WEST, Math.min(2, w)));
				out.add(new Arm(Direction.EAST, Math.min(2, e)));
				return out;
			}
		}
		if (n >= 1) {
			out.add(new Arm(Direction.NORTH, Math.min(2, n)));
		} else if (e >= 1 || w >= 1) {
			boolean east = e >= 1 && (w < 1 || pick(p, 6, 2) == 0);
			out.add(new Arm(east ? Direction.EAST : Direction.WEST, Math.min(2, east ? e : w)));
		}
		return out;
	}

	// ------------------------------------------------------------------ parts

	private static BlockState foot(Plot p, int u, int v) {
		return switch (pick(p, 100 + 7 * u + v, 4)) {
			case 0 -> b(Blocks.CHISELED_DEEPSLATE);
			case 1 -> b(Blocks.DEEPSLATE_BRICKS);
			case 2 -> b(Blocks.STONE_BRICKS); // a lighter foot now and then: the dark → light gradient starts at the ground
			default -> b(Blocks.POLISHED_DEEPSLATE);
		};
	}

	private static BlockState plinthWall(Plot p, int u, int v) {
		return b(p.hash(u, 1, v, 4) == 0 ? Blocks.MOSSY_STONE_BRICK_WALL : Blocks.STONE_BRICK_WALL);
	}

	/** The shaft from the foot up to (not including) {@code collarY}, then the collar itself (when {@code collarY ≥ 1}). */
	private static void shaft(CitadelCanvas c, Plot p, int u, int v, int hh, int collarY) {
		p.set(c, u, 0, v, foot(p, u, v));
		for (int y = 1; y < collarY; y++) {
			BlockState s = FENCE;
			if (y == 1 && hh >= 5) {
				s = plinthWall(p, u, v); // stone first, wood above
			}
			p.set(c, u, y, v, s);
		}
		if (collarY >= 1) {
			p.set(c, u, collarY, v, COLLAR);
		}
	}

	/** A mast with no arm: one light box on top, its roof above. */
	private static void box(CitadelCanvas c, Plot p, int u, int v, int hh, int kind, String tone) {
		int top = hh - 1;
		if (hh >= 5) {
			shaft(c, p, u, v, hh, top - 2);
		} else {
			// height 4: the mast is foot + fence, the light box sits straight on it
			p.set(c, u, 0, v, foot(p, u, v));
			p.set(c, u, 1, v, FENCE);
		}
		p.set(c, u, top - 1, v, standingLight(c, p, kind));
		p.set(c, u, top, v, roof(p));
	}

	/** The roof: a thin plate that reads as a canopy, not a block. A cherry plate (two in three) or a cherry slab, by seed. */
	private static BlockState roof(Plot p) {
		return switch (pick(p, 4, 3)) {
			case 0 -> slab(Blocks.CHERRY_SLAB);
			default -> b(Blocks.CHERRY_TRAPDOOR).setValue(BlockStateProperties.HALF, Half.BOTTOM); // bottom half: it sits on what is under it
		};
	}

	private static BlockState standingLight(CitadelCanvas c, Plot p, int kind) {
		return switch (kind) {
			case 1 -> PaperLanterns.standing();
			case 2 -> b(Blocks.OCHRE_FROGLIGHT);
			default -> LANTERN;
		};
	}

	private static void mast(CitadelCanvas c, Plot p, int u, int v, int hh, int style, List<Arm> arms, int kind, String tone) {
		int top = hh - 1;
		int armY = style == 2 ? 3 : top;
		shaft(c, p, u, v, hh, armY - 1);
		BlockState tee = FENCE;
		for (Arm a : arms) {
			tee = tee.setValue(prop(a.dir()), true);
		}
		p.set(c, u, armY, v, tee);
		if (style == 2) {
			p.set(c, u, armY + 1, v, FENCE);
			p.set(c, u, top, v, roof(p)); // the mast's own cap, matching the tip roofs
		}
		for (Arm a : arms) {
			arm(c, p, u, v, a, armY, top, style, kind, tone);
		}
	}

	private static void arm(CitadelCanvas c, Plot p, int u, int v, Arm a, int armY, int top, int style, int kind, String tone) {
		Direction d = a.dir();
		for (int i = 1; i <= a.len(); i++) {
			int au = u + d.getStepX() * i;
			int av = v + d.getStepZ() * i;
			BlockState f = FENCE.setValue(prop(d.getOpposite()), true);
			if (i < a.len()) {
				f = f.setValue(prop(d), true);
			}
			p.set(c, au, armY, av, f);
			if (i < a.len() && style != 2) {
				// corbel: an upside-down stair hugging the mast under the arm (its tall back towards the mast)
				p.set(c, au, armY - 1, av, stairs(Blocks.CHERRY_STAIRS, d.getOpposite()).setValue(StairBlock.HALF, Half.TOP));
			}
		}
		int tu = u + d.getStepX() * a.len();
		int tv = v + d.getStepZ() * a.len();
		if (style == 2) {
			// light box on the tip: the light stands on the fence, a cherry slab roof one above
			p.set(c, tu, armY + 1, tv, standingLight(c, p, kind));
			p.set(c, tu, armY + 2, tv, roof(p));
			return;
		}
		// crook: a chain from the tip (height 6) and the light under it
		int ly = armY - 1;
		if (armY >= 5) {
			p.put(c, tu, ly, tv, b(Blocks.IRON_CHAIN), CitadelCanvas.HANGING);
			ly--;
		}
		BlockState light = switch (kind) {
			case 1 -> PaperLanterns.hanging(tone);
			default -> LANTERN.setValue(BlockStateProperties.HANGING, true);
		};
		p.put(c, tu, ly, tv, light, CitadelCanvas.HANGING);
	}

	/** A stepped plinth for a 3 × 3 or bigger single mast: stairs climbing to the foot, corners low, one planted. */
	private static void plinth(CitadelCanvas c, Plot p, int cu, int cv) {
		p.set(c, cu, 0, cv - 1, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH));
		p.set(c, cu, 0, cv + 1, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.NORTH));
		p.set(c, cu - 1, 0, cv, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.EAST));
		p.set(c, cu + 1, 0, cv, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.WEST));
		int planted = p.hash(1, 0, 1, 4);
		int[][] corners = {{cu - 1, cv - 1}, {cu + 1, cv - 1}, {cu - 1, cv + 1}, {cu + 1, cv + 1}};
		for (int i = 0; i < 4; i++) {
			p.set(c, corners[i][0], 0, corners[i][1], i == planted ? b(Blocks.POTTED_FLOWERING_AZALEA) : slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
	}

	/** The foot of a flanking mast: a planted pot behind it (never under an arm: a raised floor there would cut the lantern's headroom). */
	private static void dress(CitadelCanvas c, Plot p, int cu, int cv) {
		if (cv + 1 < p.d()) {
			p.set(c, cu, 0, cv + 1, p.hash(cu, 0, 7, 2) == 0 ? b(Blocks.POTTED_FLOWERING_AZALEA) : b(Blocks.POTTED_FERN));
		}
	}

	private static BooleanProperty prop(Direction d) {
		return switch (d) {
			case NORTH -> BlockStateProperties.NORTH;
			case SOUTH -> BlockStateProperties.SOUTH;
			case WEST -> BlockStateProperties.WEST;
			default -> BlockStateProperties.EAST;
		};
	}
}
