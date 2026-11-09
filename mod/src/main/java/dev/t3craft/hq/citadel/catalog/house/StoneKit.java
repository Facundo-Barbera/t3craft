package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Plot;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Shared bits of the stone houses ({@code house_stone_*}): palettes that go dark → light with height,
 * a wall helper that works in "along the wall / out of the wall" terms, panes, doors, lanterns,
 * discs for round towers and a few yard props. Everything is written in local (front = NORTH) terms
 * through {@link Plot}.
 */
final class StoneKit {
	private StoneKit() {
	}

	// ------------------------------------------------------------------ palettes

	/** [palette] the heavy dark base: mostly deepslate bricks, a few cracked and cobbled patches. */
	static BlockState deep(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 12)) {
			case 0 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
			case 1 -> b(Blocks.COBBLED_DEEPSLATE);
			default -> b(Blocks.DEEPSLATE_BRICKS);
		};
	}

	/** [palette] the middle body: clean stone bricks with the odd cracked or polished block. */
	static BlockState mid(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 18)) {
			case 0 -> b(Blocks.CRACKED_STONE_BRICKS);
			case 1 -> b(Blocks.POLISHED_ANDESITE);
			default -> b(Blocks.STONE_BRICKS);
		};
	}

	/** [palette] the light upper body: stone bricks, polished andesite and calcite flecks. */
	static BlockState light(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 10)) {
			case 0 -> b(Blocks.POLISHED_ANDESITE);
			case 1 -> b(Blocks.CALCITE);
			default -> b(Blocks.STONE_BRICKS);
		};
	}

	// ------------------------------------------------------------------ block factories

	static BlockState up(BlockState st) {
		return st.setValue(BlockStateProperties.HALF, Half.TOP);
	}

	static BlockState topSlab(Block s) {
		return b(s).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	static BlockState botSlab(Block s) {
		return slab(s);
	}

	/** Stair whose HIGH side is towards {@code high}. */
	static BlockState st(Block s, Direction high) {
		return stairs(s, high);
	}

	static BlockState stUp(Block s, Direction high) {
		return up(stairs(s, high));
	}

	static BlockState pane(boolean alongU) {
		BlockState g = b(Blocks.GLASS_PANE);
		return alongU ? g.setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true)
				: g.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true);
	}

	static BlockState tintedPane(boolean alongU) {
		BlockState g = Blocks.STAINED_GLASS_PANE.pick(net.minecraft.world.item.DyeColor.LIGHT_BLUE).defaultBlockState();
		return alongU ? g.setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true)
				: g.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true);
	}

	static BlockState wallBlock(Block w, boolean n, boolean e, boolean s, boolean wst, boolean up) {
		return b(w).setValue(BlockStateProperties.NORTH_WALL, n ? net.minecraft.world.level.block.state.properties.WallSide.LOW
				: net.minecraft.world.level.block.state.properties.WallSide.NONE)
				.setValue(BlockStateProperties.EAST_WALL, e ? net.minecraft.world.level.block.state.properties.WallSide.LOW
						: net.minecraft.world.level.block.state.properties.WallSide.NONE)
				.setValue(BlockStateProperties.SOUTH_WALL, s ? net.minecraft.world.level.block.state.properties.WallSide.LOW
						: net.minecraft.world.level.block.state.properties.WallSide.NONE)
				.setValue(BlockStateProperties.WEST_WALL, wst ? net.minecraft.world.level.block.state.properties.WallSide.LOW
						: net.minecraft.world.level.block.state.properties.WallSide.NONE)
				.setValue(BlockStateProperties.UP, up);
	}

	static BlockState door(Block door, Direction facing, boolean lower) {
		return b(door).setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, lower ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
	}

	/** An open trapdoor hung on a wall: it lies flat against the wall, {@code facing} = the side of the wall it is on. */
	static BlockState shutter(Block trapdoor, Direction facing) {
		return b(trapdoor).setValue(BlockStateProperties.HORIZONTAL_FACING, facing).setValue(BlockStateProperties.OPEN, true);
	}

	static BlockState lantern(boolean hanging) {
		return b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, hanging);
	}

	static BlockState log(Block log, Direction.Axis axis) {
		return b(log).setValue(BlockStateProperties.AXIS, axis);
	}

	static BlockState barrel() {
		return b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
	}

	// ------------------------------------------------------------------ a straight wall

	/**
	 * A straight wall in local terms. {@code a} is the fixed coordinate of the wall plane (v for the
	 * front/back walls, u for the side walls) and {@code out} the local direction the wall faces
	 * (NORTH front, SOUTH back, WEST left, EAST right). {@code t} runs along the wall in local u (or v)
	 * and {@code o} is the offset out of the wall: 0 = the wall plane, 1 = one block outside, -1 = inside.
	 */
	static final class Wall {
		final Plot p;
		final CitadelCanvas c;
		final int a;
		final Direction out;

		Wall(CitadelCanvas c, Plot p, int a, Direction out) {
			this.c = c;
			this.p = p;
			this.a = a;
			this.out = out;
		}

		boolean alongU() {
			return out.getAxis() == Direction.Axis.Z;
		}

		int u(int t, int o) {
			return alongU() ? t : a + o * out.getStepX();
		}

		int v(int t, int o) {
			return alongU() ? a + o * out.getStepZ() : t;
		}

		void set(int t, int y, int o, BlockState s) {
			p.set(c, u(t, o), y, v(t, o), s);
		}

		void put(int t, int y, int o, BlockState s, int layer) {
			p.put(c, u(t, o), y, v(t, o), s, layer);
		}

		/** Stair at (t, y, o) with its high side towards the building (a sill, a cornice, a lintel cap). */
		BlockState stairIn(Block s, boolean upsideDown) {
			BlockState st = stairs(s, out.getOpposite());
			return upsideDown ? up(st) : st;
		}

		/** Stair with its high side away from the building (a roof edge, a rising step). */
		BlockState stairOut(Block s, boolean upsideDown) {
			BlockState st = stairs(s, out);
			return upsideDown ? up(st) : st;
		}

		BlockState pane() {
			return StoneKit.pane(alongU());
		}

		BlockState tinted() {
			return StoneKit.tintedPane(alongU());
		}

		/** Door facing the way it opens from: the panel sits at the plane, hinge looking out. */
		BlockState door(Block d, boolean lower) {
			return StoneKit.door(d, out, lower);
		}

		/** Trapdoor flat on the outside of the wall plane beside a window, at offset 1. */
		BlockState shutter(Block t) {
			return StoneKit.shutter(t, out);
		}
	}

	// ------------------------------------------------------------------ round towers

	/** Integer disc test: squared distance from the centre is at most {@code r2}. */
	static boolean in(int du, int dv, int r2) {
		return du * du + dv * dv <= r2;
	}

	/** A cell of the disc whose 4-neighbours are not all in the disc (its rim). */
	static boolean rim(int du, int dv, int r2) {
		return in(du, dv, r2) && !(in(du + 1, dv, r2) && in(du - 1, dv, r2) && in(du, dv + 1, r2) && in(du, dv - 1, r2));
	}

	/** Direction from a rim cell to the outside: the missing neighbour of the larger offset first. */
	static Direction outward(int du, int dv, int r2) {
		boolean e = !in(du + 1, dv, r2);
		boolean w = !in(du - 1, dv, r2);
		boolean s = !in(du, dv + 1, r2);
		boolean n = !in(du, dv - 1, r2);
		boolean horiz = e || w;
		boolean vert = s || n;
		if (horiz && vert) {
			if (Math.abs(du) >= Math.abs(dv)) {
				return e ? Direction.EAST : Direction.WEST;
			}
			return s ? Direction.SOUTH : Direction.NORTH;
		}
		if (horiz) {
			return e ? Direction.EAST : Direction.WEST;
		}
		return s ? Direction.SOUTH : Direction.NORTH;
	}

	/**
	 * One layer of a disc: rim cells become stairs whose high side points to the centre (so the layer
	 * steps inwards as it rises), inner cells are {@code fill}.
	 */
	static void discLayer(CitadelCanvas c, Plot p, int cu, int cv, int y, int r2, Block stair, BlockState fill, boolean upsideDown) {
		int r = (int) Math.ceil(Math.sqrt(r2)) + 1;
		for (int dv = -r; dv <= r; dv++) {
			for (int du = -r; du <= r; du++) {
				if (!in(du, dv, r2)) {
					continue;
				}
				if (rim(du, dv, r2)) {
					BlockState s = stairs(stair, outward(du, dv, r2).getOpposite());
					p.set(c, cu + du, y, cv + dv, upsideDown ? up(s) : s);
				} else {
					p.set(c, cu + du, y, cv + dv, fill);
				}
			}
		}
	}

	// ------------------------------------------------------------------ props

	/** A wall-connected stone-brick garden wall piece at (u, v), connections decided by {@code conn}. */
	static void lowWall(CitadelCanvas c, Plot p, int u, int y, int v, boolean n, boolean e, boolean s, boolean w) {
		boolean post = !((n && s && !e && !w) || (e && w && !n && !s));
		p.set(c, u, y, v, wallBlock(Blocks.STONE_BRICK_WALL, n, e, s, w, post));
	}

	/**
	 * [finish] a small roofed well in a 3x3 footprint at local (u0, v0): stone ring with water inside,
	 * corner posts and a dark oak slab roof with a hanging lantern.
	 */
	static void well(CitadelCanvas c, Plot p, int u0, int v0) {
		for (int du = 0; du < 3; du++) {
			for (int dv = 0; dv < 3; dv++) {
				boolean centre = du == 1 && dv == 1;
				p.set(c, u0 + du, 0, v0 + dv, centre ? b(Blocks.WATER) : p.hash(u0 + du, 0, v0 + dv, 3) == 0 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
				boolean corner = du != 1 && dv != 1;
				if (!centre && !corner) {
					p.set(c, u0 + du, 1, v0 + dv, slab(Blocks.STONE_BRICK_SLAB));
				}
				if (corner) {
					p.set(c, u0 + du, 1, v0 + dv, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
					p.set(c, u0 + du, 2, v0 + dv, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
				}
				// a pitched roof: stairs rising to the middle, slabs on the corners, a slab cap
				if (du == 1 && dv == 1) {
					p.set(c, u0 + 1, 3, v0 + 1, b(Blocks.DARK_OAK_PLANKS));
					p.set(c, u0 + 1, 4, v0 + 1, slab(Blocks.DARK_OAK_SLAB));
				} else if (corner) {
					p.set(c, u0 + du, 3, v0 + dv, slab(Blocks.DARK_OAK_SLAB));
				} else {
					Direction high = du == 1 ? (dv == 0 ? Direction.SOUTH : Direction.NORTH) : (du == 0 ? Direction.EAST : Direction.WEST);
					p.set(c, u0 + du, 3, v0 + dv, stairs(Blocks.DARK_OAK_STAIRS, high));
				}
			}
		}
		p.put(c, u0 + 1, 2, v0 + 1, lantern(true), CitadelCanvas.HANGING);
	}

	/** A low stone-brick wall run along u at (v), from u a to u b, with an optional gap. */
	static void lowWallRunU(CitadelCanvas c, Plot p, int a, int b2, int v, int gap) {
		for (int u = a; u <= b2; u++) {
			if (u == gap) {
				continue;
			}
			boolean w = u > a && u - 1 != gap;
			boolean e = u < b2 && u + 1 != gap;
			lowWall(c, p, u, 0, v, false, e, false, w);
		}
	}
}
