package dev.t3craft.hq.citadel.barrio;

import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.fh;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.RingCampusGeometry.Room;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelCanvas.Mats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The outside of a barrio hall, by {@link HallStyle}: which block a wall cell is ({@link #wallBlock}, called by
 * the hall shell cell by cell) and everything added outside the walls afterwards ({@link #dress}): the door's
 * lintel and jambs, the porch / hood / balcony with its lanterns, the window ledges with their pots, shutters,
 * ivy. Every variation is a function of the hall's seed, never random.
 *
 * <p>Walls are numbered by {@code side}: 0 the door wall (outside is {@code u = -2}, the edge of the
 * courtyard), 1 the back wall, 2 the {@code v = -1} wall, 3 the {@code v = W} wall; {@code t} runs along each
 * wall like the shell's {@code t} (the door wall's and the back wall's {@code t} is {@code v}).
 */
public final class HallFacade {
	private HallFacade() {
	}

	private static final BlockState PANE = b(Blocks.GLASS_PANE);
	private static final BlockState STONE_BRICKS = b(Blocks.STONE_BRICKS);
	private static final BlockState MOSSY_BRICKS = b(Blocks.MOSSY_STONE_BRICKS);

	// ------------------------------------------------------------------ wall cells

	/** A beam along {@code axis} in the material of {@code post} (or the kit's beam when the post has no axis). */
	static BlockState beamLike(CitadelCanvas c, BlockState post, Direction.Axis axis) {
		return post.hasProperty(BlockStateProperties.AXIS) ? post.setValue(BlockStateProperties.AXIS, axis) : c.kit.beam(axis);
	}

	/**
	 * The block of a non-corner wall cell at height {@code ly} (1..5 of its floor), position {@code t} along the wall.
	 * Window columns are the cells with {@code t mod 3 != 0}, as ever; the piers between them are {@code t mod 3 == 0}.
	 */
	public static BlockState wallBlock(CitadelCanvas c, HallStyle s, Mats m, DyeColor env, int seed, int t, int ly, Direction.Axis along) {
		boolean pier = Math.floorMod(t, 3) == 0;
		if (tall()) {
			return tallWall(c, s, m, env, seed, pier, t, ly, along);
		}
		boolean glass = !pier && ly >= 2;
		return switch (s) {
			case TIMBER -> pier ? m.post() : (ly <= 3 ? (glass ? PANE : m.wall()) : ly == 4 ? beamLike(c, m.post(), along) : m.wall());
			case STONE -> pier || ly == 1 ? plinth(seed, t, ly) : ly <= 4 ? PANE : STONE_BRICKS;
			case SHUTTERED -> glass && ly <= 3 ? PANE : m.wall();
			case BALCONY -> glass && ly <= 3 ? b(Blocks.STAINED_GLASS_PANE.pick(env)) : (!pier && ly == 5 ? bandOf(c, m, along) : m.wall());
		};
	}

	/** Scaled halls (floor height 8 or more) get the tall-window facade of struct/barrio-hall2. */
	static boolean tall() {
		return fh() >= 8;
	}

	/**
	 * struct/barrio-hall3: the dark-oak frame of the scaled halls (pilasters, corner posts, lintels, the band, door jambs, portico
	 * columns). Dark oak on a light infill (plaster, calcite, terracotta, the lot's wall panel) gives the timber halls the contrast
	 * they lacked when the posts were the same tone as the wall.
	 */
	static BlockState frame(Direction.Axis axis) {
		return Blocks.DARK_OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	/** A post: the dark-oak frame on a scaled wooden hall, the lot's own post otherwise (stone halls keep their stone). */
	static BlockState postBlock(HallStyle s, Mats m) {
		return tall() && s != HallStyle.STONE ? frame(Direction.Axis.Y) : m.post();
	}

	/** A beam along {@code axis}: the dark-oak frame on a scaled wooden hall, the lot's post turned otherwise. */
	static BlockState beamBlock(CitadelCanvas c, HallStyle s, Mats m, Direction.Axis axis) {
		return tall() && s != HallStyle.STONE ? frame(axis) : beamLike(c, m.post(), axis);
	}

	/** The corner post of a hall shell (all heights). */
	public static BlockState cornerPost(HallStyle s, Mats m) {
		return postBlock(s, m);
	}

	/** The band that closes a floor (under the cornice / the roof), along {@code axis}. */
	public static BlockState bandBlock(CitadelCanvas c, HallStyle s, Mats m, Direction.Axis axis) {
		if (tall() && s != HallStyle.STONE) {
			return frame(axis);
		}
		return m.band() == m.post() ? c.kit.beam(axis) : m.band();
	}

	/**
	 * The wall of a scaled hall: a dark footing course (deepslate bricks, stone bricks on stone halls) under tall
	 * windows that run from the second row to one short of the band, with a lintel row over them; the piers between
	 * the windows keep their material (posts, stone bricks). No smooth stretch of wall: the windows take the
	 * height. Timber halls get a transom bar half way up the glass.
	 */
	private static BlockState tallWall(CitadelCanvas c, HallStyle s, Mats m, DyeColor env, int seed, boolean pier, int t, int ly, Direction.Axis along) {
		int lintel = fh() - 1;
		if (ly == 1) {
			return footing(s, seed, t, ly);
		}
		if (pier) {
			// struct/barrio-hall3: every style shows the wall's own light material in the pier (timber halls used to carry
			// the post here, the same tone as the pilaster outside), so the dark-oak pilasters that stand proud of it read
			return s == HallStyle.STONE ? plinth(seed, t, ly) : m.wall();
		}
		if (ly == lintel) {
			return switch (s) {
				case TIMBER -> frame(along);
				case STONE -> HallStyle.hash(seed, t, ly) % 3 == 0 ? b(Blocks.CHISELED_STONE_BRICKS) : STONE_BRICKS;
				default -> m.wall();
			};
		}
		if (s == HallStyle.TIMBER && ly == (fh() + 1) / 2) {
			return frame(along);
		}
		// the project's colour is an accent: tinted glass in one bay of three, clear glass between
		return s == HallStyle.BALCONY && Math.floorMod(t / 3, 3) == 0 ? HallStyle.stainedPane(env) : PANE;
	}

	/** The dark footing course: deepslate bricks with a cracked one now and then; stone bricks (a little moss) on stone halls. */
	private static BlockState footing(HallStyle s, int seed, int t, int ly) {
		if (s == HallStyle.STONE) {
			return plinth(seed, t, ly);
		}
		return HallStyle.hash(seed, t, ly) % 4 == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS);
	}

	private static BlockState plinth(int seed, int t, int ly) {
		return HallStyle.hash(seed, t, ly) % 5 == 0 ? MOSSY_BRICKS : STONE_BRICKS;
	}

	private static BlockState bandOf(CitadelCanvas c, Mats m, Direction.Axis along) {
		return m.band() == m.post() ? c.kit.beam(along) : m.band();
	}

	// ------------------------------------------------------------------ state factories

	static BlockState trapdoor(Block b, Direction facing, boolean open, Half half) {
		return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing).setValue(BlockStateProperties.OPEN, open)
				.setValue(BlockStateProperties.HALF, half);
	}

	static BlockState topSlab(Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	static BlockState hangingLantern() {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
	}

	static BlockState lantern() {
		return Blocks.LANTERN.defaultBlockState();
	}

	/** Vine on the outside of a wall: {@code outward} is the direction from the wall to the vine's cell. */
	static BlockState vine(Direction outward) {
		return Blocks.VINE.defaultBlockState().setValue(VineBlock.PROPERTY_BY_DIRECTION.get(outward.getOpposite()), true);
	}

	/** The colour of the project of {@code lot} (its lead module's environment colour). */
	public static DyeColor env(CitadelCanvas c, int lot) {
		return dev.t3craft.hq.style.HqStyle.envColor(c.lots.get(lot).firstEntry().getValue());
	}

	// ------------------------------------------------------------------ outside

	private static BlockPos outside(Room r, int side, int t, int y) {
		return switch (side) {
			case 0 -> r.cell(-2, t, y);
			case 1 -> r.cell(r.d() + 1, t, y);
			case 2 -> r.cell(t, -2, y);
			default -> r.cell(t, r.w() + 1, y);
		};
	}

	private static Direction outward(Room r, int side) {
		return switch (side) {
			case 0 -> r.back().getOpposite();
			case 1 -> r.back();
			case 2 -> r.side().getOpposite();
			default -> r.side();
		};
	}

	/**
	 * Everything outside the shell: door, porch, windows. Call after {@code hallShell}.
	 *
	 * @param F floors of the hall
	 */
	public static void dress(CitadelCanvas c, Room r, int F, Mats m, HallStyle s, DyeColor env, int seed) {
		door(c, r, s, m);
		porch(c, r, F, m, s, seed);
		windows(c, r, F, s, seed);
		pilasters(c, r, F, m, s);
		cornice(c, r, F, m);
	}

	/**
	 * A cornice under every floor band: upside-down stairs in the roof's material along the outside of all four
	 * walls (not beside the door, not on the piers that carry a cap), so each floor ends in a shadow line.
	 */
	private static void cornice(CitadelCanvas c, Room r, int F, Mats m) {
		int mid = r.c();
		for (int side = 0; side < 4; side++) {
			int len = side < 2 ? r.w() : r.d();
			Direction out = outward(r, side);
			for (int t = 0; t < len; t++) {
				if (side == 0 && Math.abs(t - mid) <= 2) {
					continue;
				}
				for (int f = 0; f < F - 1; f++) {
					BlockPos p = outside(r, side, t, (f + 1) * fh());
					BlockState there = c.at(p.getX(), p.getY(), p.getZ());
					if (there == null || there.isAir()) {
						c.set(p, stairs(m.roof(), out.getOpposite()).setValue(BlockStateProperties.HALF, Half.TOP));
					}
				}
			}
		}
	}

	/**
	 * Protruding piers on the free piers of timber and balcony halls: a stone plinth, a post and a stair cap under
	 * the cornice, one block proud of the wall, so the long walls have depth and a rhythm. Never beside the door
	 * (the porch is there), never next to a corner, and only into free cells.
	 */
	private static void pilasters(CitadelCanvas c, Room r, int F, Mats m, HallStyle s) {
		if (!tall() && s != HallStyle.TIMBER && s != HallStyle.BALCONY) {
			return;
		}
		int mid = r.c();
		int top = F * fh();
		BlockState foot = tall() ? b(Blocks.DEEPSLATE_BRICKS) : STONE_BRICKS;
		// barrio-hall3: dark polished deepslate shafts on stone halls, so the pilasters separate from the stone-brick wall
		BlockState shaft = tall() && s == HallStyle.STONE ? b(Blocks.POLISHED_DEEPSLATE) : postBlock(s, m);
		for (int side = 0; side < 4; side++) {
			int len = side < 2 ? r.w() : r.d();
			Direction out = outward(r, side);
			for (int t = 1; t < len - 1; t++) {
				if (!hasPilaster(s, t) || (side == 0 && Math.abs(t - mid) <= (tall() ? 3 : 2))) {
					continue;
				}
				BlockPos base = outside(r, side, t, 1);
				BlockState there = c.at(base.getX(), base.getY(), base.getZ());
				if (there != null && !there.isAir()) {
					continue;
				}
				c.set(base, foot);
				for (int y = 2; y < top; y++) {
					c.set(outside(r, side, t, y), shaft);
				}
				c.set(outside(r, side, t, top), stairs(m.roof(), out.getOpposite()));
				if (tall()) {
					// a second, lower step of the footing: the pilaster stands on a two-course plinth
					BlockPos below = outside(r, side, t, 2);
					c.set(below, foot);
				}
			}
		}
	}

	/** Which piers carry a protruding pilaster: all of them, except every other one on shuttered halls (their shutters). */
	private static boolean hasPilaster(HallStyle s, int t) {
		if (Math.floorMod(t, 3) != 0) {
			return false;
		}
		if (s == HallStyle.SHUTTERED && tall()) {
			return (t / 3) % 2 == 0;
		}
		return s == HallStyle.TIMBER || s == HallStyle.BALCONY || tall();
	}

	/** The door: jambs either side and a lintel (a keystone on stone halls). The opening (3 wide, 3 high) stays clear. */
	private static void door(CitadelCanvas c, Room r, HallStyle s, Mats m) {
		int mid = r.c();
		BlockState jamb = s == HallStyle.STONE ? STONE_BRICKS : postBlock(s, m);
		BlockState lintel = s == HallStyle.STONE ? STONE_BRICKS : beamBlock(c, s, m, r.side().getAxis());
		for (int dv = -2; dv <= 2; dv += 4) {
			for (int y = 1; y <= (tall() ? fh() - 2 : 4); y++) {
				c.set(r.cell(-1, mid + dv, y), jamb);
			}
		}
		for (int dv = -1; dv <= 1; dv++) {
			c.set(r.cell(-1, mid + dv, 4), lintel);
		}
		if (s == HallStyle.STONE) {
			c.set(r.cell(-1, mid, 4), b(Blocks.CHISELED_STONE_BRICKS));
		}
	}

	/** Porch, hood, lamp posts or balcony in front of the door, by style. Lanterns always flank the door. */
	private static void porch(CitadelCanvas c, Room r, int F, Mats m, HallStyle s, int seed) {
		if (tall()) {
			portico(c, r, F, m, s, seed);
			return;
		}
		int mid = r.c();
		Direction in = r.back();
		Direction.Axis sideAxis = r.side().getAxis();
		BlockState awning = stairs(m.roof(), in);
		switch (s) {
			case TIMBER -> {
				posts(c, r, mid, 4, m.post());
				for (int dv = -2; dv <= 2; dv++) {
					c.set(r.cell(-2, mid + dv, 5), awning);
				}
				hangLanterns(c, r, mid, 4);
			}
			case STONE -> {
				// lamp posts: two stone posts with a lantern on top
				posts(c, r, mid, 2, b(s.fence()));
				lampTops(c, r, mid);
			}
			case SHUTTERED -> {
				posts(c, r, mid, 2, b(s.fence()));
				lampTops(c, r, mid);
				for (int dv = -1; dv <= 1; dv++) {
					c.set(r.cell(-2, mid + dv, 5), awning);
				}
			}
			case BALCONY -> {
				if (F == 2) {
					// a balcony over the door: planks on two posts, a fence and flower pots on top, lanterns underneath
					posts(c, r, mid, 5, m.post());
					for (int dv = -2; dv <= 2; dv++) {
						c.set(r.cell(-2, mid + dv, fh()), PLANKS);
						if (Math.abs(dv) == 1) {
							BlockPos p = r.cell(-2, mid + dv, fh() + 1);
							c.put(p.getX(), p.getY(), p.getZ(), HallStyle.pot(HallStyle.hash(seed, dv)), DECOR);
						} else {
							c.set(r.cell(-2, mid + dv, fh() + 1), b(s.fence()));
						}
					}
					hangLanterns(c, r, mid, fh() - 1);
				} else {
					posts(c, r, mid, 4, m.post());
					for (int dv = -2; dv <= 2; dv++) {
						c.set(r.cell(-2, mid + dv, 5), beamLike(c, m.post(), sideAxis));
					}
					hangLanterns(c, r, mid, 4);
				}
			}
		}
	}

	private static boolean open(CitadelCanvas c, BlockPos p) {
		BlockState st = c.at(p.getX(), p.getY(), p.getZ());
		return st == null || st.isAir();
	}

	/**
	 * The entrance portico of a scaled hall: two columns (a dark footing, a shaft, a beam capital) three cells either
	 * side of the door's axis, two cells out when the courtyard is free there (one otherwise), joined by a beam over
	 * the approach and a stepped roof of the hall's own stairs that leans on the wall. Hanging lanterns light the door.
	 * On a balcony hall of two floors the beam carries a deck level with the upper floor, with a fence and flower pots,
	 * instead of the roof. The three-wide approach and the jambs stay clear.
	 */
	private static void portico(CitadelCanvas c, Room r, int F, Mats m, HallStyle s, int seed) {
		int mid = r.c();
		int H = fh();
		int hw = 3;
		boolean deck = s == HallStyle.BALCONY && F == 2;
		int beamY = deck ? H - 1 : H - 2;
		boolean deep = true;
		for (int dv = -hw; dv <= hw && deep; dv++) {
			for (int y = 1; y <= beamY + 2; y++) {
				deep = open(c, r.cell(-3, mid + dv, y));
				if (!deep) {
					break;
				}
			}
		}
		int uf = deep ? -3 : -2;
		Direction in = r.back();
		Direction.Axis sideAxis = r.side().getAxis();
		BlockState footing = s == HallStyle.STONE ? STONE_BRICKS : b(Blocks.DEEPSLATE_BRICKS);
		BlockState shaft = s == HallStyle.STONE ? b(Blocks.POLISHED_ANDESITE) : postBlock(s, m);
		BlockState cap = s == HallStyle.STONE ? b(Blocks.CHISELED_STONE_BRICKS) : beamBlock(c, s, m, sideAxis);
		BlockState beam = beamBlock(c, s, m, sideAxis);
		for (int dv = -hw; dv <= hw; dv += 2 * hw) {
			for (int u = uf; u <= -2; u++) {
				c.set(r.cell(u, mid + dv, 1), footing);
				for (int y = 2; y < beamY; y++) {
					c.set(r.cell(u, mid + dv, y), shaft);
				}
			}
		}
		for (int u = uf; u <= -2; u++) {
			for (int dv = -hw; dv <= hw; dv++) {
				c.set(r.cell(u, mid + dv, beamY), Math.abs(dv) == hw ? cap : beam);
			}
		}
		if (deck) {
			for (int u = uf; u <= -2; u++) {
				for (int dv = -hw; dv <= hw; dv++) {
					c.set(r.cell(u, mid + dv, H), PLANKS);
				}
			}
			for (int dv = -hw; dv <= hw; dv++) {
				BlockPos p = r.cell(uf, mid + dv, H + 1);
				if (Math.abs(dv) == 1) {
					put(c, p, HallStyle.pot(HallStyle.hash(seed, dv)));
				} else {
					c.set(p, b(s.fence()));
				}
			}
			for (int u = uf + 1; u <= -2; u++) {
				for (int dv = -hw; dv <= hw; dv += 2 * hw) {
					c.set(r.cell(u, mid + dv, H + 1), b(s.fence()));
				}
			}
		} else {
			for (int dv = -hw; dv <= hw; dv++) {
				c.set(r.cell(uf, mid + dv, beamY + 1), stairs(m.roof(), in));
				if (uf == -3) {
					c.set(r.cell(-2, mid + dv, beamY + 1), m.ridge());
					c.set(r.cell(-2, mid + dv, beamY + 2), stairs(m.roof(), in));
				}
			}
		}
		hangLanterns(c, r, mid, beamY - 1);
	}

	/** Two posts (y 1..{@code top}) either side of the door's approach. */
	private static void posts(CitadelCanvas c, Room r, int mid, int top, BlockState post) {
		for (int dv = -2; dv <= 2; dv += 4) {
			for (int y = 1; y <= top; y++) {
				c.set(r.cell(-2, mid + dv, y), post);
			}
		}
	}

	/** A standing lantern on top of each 2-high post. */
	private static void lampTops(CitadelCanvas c, Room r, int mid) {
		for (int dv = -2; dv <= 2; dv += 4) {
			put(c, r.cell(-2, mid + dv, 3), lantern());
		}
	}

	/** A lantern hanging at {@code y} either side of the door's axis, under the porch roof. */
	private static void hangLanterns(CitadelCanvas c, Room r, int mid, int y) {
		for (int dv = -1; dv <= 1; dv += 2) {
			BlockPos p = r.cell(-2, mid + dv, y);
			c.put(p.getX(), p.getY(), p.getZ(), hangingLantern(), HANGING);
		}
	}

	// ------------------------------------------------------------------ windows

	/** Per window column: ledge and pot, shutters on the piers, ivy. */
	private static void windows(CitadelCanvas c, Room r, int F, HallStyle s, int seed) {
		int mid = r.c();
		Block shutter = HallStyle.shutter(seed);
		for (int side = 0; side < 4; side++) {
			int len = side < 2 ? r.w() : r.d();
			Direction out = outward(r, side);
			for (int t = 0; t < len; t++) {
				boolean pier = Math.floorMod(t, 3) == 0;
				boolean nearDoor = side == 0 && Math.abs(t - mid) <= 2;
				for (int f = 0; f < F; f++) {
					int yb = f * fh();
					if (nearDoor && (f == 0 || s == HallStyle.BALCONY)) {
						continue;
					}
					int hsh = HallStyle.hash(seed, side, t / 3, f);
					boolean boxes = (side == 0 || f == 1) && hsh % 3 != 0;
					if (tall()) {
						tallWindow(c, r, side, t, yb, f, s, out, hsh, pier, boxes, shutter);
						continue;
					}
					switch (s) {
						case SHUTTERED -> {
							if (pier) {
								for (int ly = 2; ly <= 3; ly++) {
									put(c, outside(r, side, t, yb + ly), trapdoor(shutter, out, true, Half.BOTTOM));
								}
							} else if (boxes) {
								box(c, r, side, t, yb, s, hsh);
							}
						}
						case TIMBER, BALCONY -> {
							if (!pier && boxes) {
								box(c, r, side, t, yb, s, hsh);
							}
						}
						case STONE -> {
							if (!pier && (side == 0 || f == 1)) {
								c.set(outside(r, side, t, yb + 1), topSlab(s.slab()));
							}
							if (pier && hsh % 4 == 0) {
								ivy(c, r, side, t, yb, out, hsh);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * What goes outside a wall cell of a scaled hall: on a window column a sill (dark deepslate-brick stair on the ground
	 * floor, the style's slab above) with a flower box on some of them; on a pier, the shutters of a shuttered hall
	 * (on the piers that carry no pilaster) or ivy on stone piers without one.
	 */
	private static void tallWindow(CitadelCanvas c, Room r, int side, int t, int yb, int f, HallStyle s, Direction out, int hsh, boolean pier, boolean boxes, Block shutter) {
		if (pier) {
			if (s == HallStyle.SHUTTERED && !hasPilaster(s, t)) {
				for (int ly = 2; ly <= fh() - 3; ly++) {
					put(c, outside(r, side, t, yb + ly), trapdoor(shutter, out, true, Half.BOTTOM));
				}
			} else if (s == HallStyle.STONE && !hasPilaster(s, t) && hsh % 4 == 0) {
				ivy(c, r, side, t, yb, out, hsh);
			}
			return;
		}
		BlockPos ledge = outside(r, side, t, yb + 1);
		// dark sills on every floor (a light or yellow ledge reads as a band across the facade)
		c.set(ledge, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, out.getOpposite()));
		if (boxes) {
			put(c, outside(r, side, t, yb + 2), HallStyle.pot(hsh / 7 + t));
		}
	}

	private static void put(CitadelCanvas c, BlockPos p, BlockState s) {
		c.put(p.getX(), p.getY(), p.getZ(), s, DECOR);
	}

	/** A window box: a ledge (top slab) under the window and a pot on it. */
	private static void box(CitadelCanvas c, Room r, int side, int t, int yb, HallStyle s, int hsh) {
		c.set(outside(r, side, t, yb + 1), topSlab(s.slab()));
		put(c, outside(r, side, t, yb + 2), HallStyle.pot(hsh / 7 + t));
	}

	/** Ivy hanging from the top of a pier. */
	private static void ivy(CitadelCanvas c, Room r, int side, int t, int yb, Direction out, int hsh) {
		int bottom = 2 + hsh / 11 % 3;
		for (int ly = 5; ly >= bottom; ly--) {
			put(c, outside(r, side, t, yb + ly), vine(out));
		}
	}
}
