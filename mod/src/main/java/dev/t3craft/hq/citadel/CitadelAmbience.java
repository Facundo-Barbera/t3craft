package dev.t3craft.hq.citadel;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.SOLID;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;

import dev.t3craft.hq.RingCampusGeometry.Kind;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * The visible, decorative light of the citadel's public space: what makes it warm and rhythmic at night.
 * The invisible {@code minecraft:light} blocks ({@link CitadelFinish#light}) do the functional job; this
 * pass only draws lamps worth looking at, and runs before it so that they count as light.
 *
 * <h2>Concept</h2>
 *
 * Reference: lantern-lit Japanese and Kyoto lanes, a Minecraft village at dusk. Palette: dark blackstone
 * bases, cherry-wood shafts, deepslate caps, amber and pink stained glass, warm lantern and campfire
 * light. Memorable: a street edge that breathes (lantern, vitral, lantern, brazier...) and lanterns
 * under every roof the public walks beneath. Everything keeps the people's paths: a post never cuts a
 * way, and the whole pass is undone around any cell it would have cut off.
 *
 * <h2>Pieces</h2>
 *
 * <ul>
 * <li>{@link #posts}: along the <b>edge</b> of the streets, avenues, inner street, patio and terraces
 *     ({@link #STREETS}) it raises a post every {@link #SPACING} blocks, in the cells of the walkway next to a
 *     wall, a hedge or a drop (see {@link #edge}). Three kinds follow one another by their number: the
 *     <i>lantern post</i> (blackstone-brick wall base, two cherry fences, a crossarm over the way with a
 *     lantern hanging from it, deepslate-tile cap), the <i>vitral</i> (the same shaft with a lantern
 *     standing on top inside a cross of amber and pink stained glass) and, in the plazas ({@link #PLAZAS}),
 *     every fourth one the <i>brazier</i> (a two-high blackstone pillar with a lit campfire above head
 *     height: nobody can step in it).</li>
 * <li>{@link #arcades}: a hanging lantern under every roof ({@link #COVERED}) the public walks beneath,
 *     on the lattice {@code (2x + 5z) mod SPACING}, never next to another lamp.</li>
 * </ul>
 *
 * <h2>Scale</h2>
 *
 * Nothing here is a size of the geometry: {@link #SPACING} comes from how far a lamp of level 15 lights
 * ({@link #REACH}, from {@link CitadelFinish#MIN_LIGHT}), so the density follows the light and not the
 * ring or lot dimensions; the human-scale heights of a post (4 cells) are fixed like doors and desks.
 *
 * <h2>Walkability</h2>
 *
 * A post takes the cell of the edge it stands on only when the cells around it stay connected
 * ({@link #keepsConnected}); the finished set of reachable cells is compared with the one before and any
 * post near a cell that became unreachable is removed. Crossarm and lanterns hang at head height + 1 or
 * higher; glass and caps sit above 3. Anchors and desk cells are never touched.
 */
final class CitadelAmbience {
	/** Public space that gets posts at its edges. */
	private static final Set<Kind> STREETS = EnumSet.of(Kind.AVENUE, Kind.STREET, Kind.INNER_STREET, Kind.PATIO, Kind.TERRACE);
	/** Plazas: some of their posts are braziers. */
	private static final Set<Kind> PLAZAS = EnumSet.of(Kind.PATIO, Kind.TERRACE, Kind.INNER_STREET);
	/** Public space where the roofs get hanging lanterns. */
	private static final Set<Kind> COVERED = EnumSet.of(Kind.GALLERY, Kind.BRIDGE, Kind.INNER_STREET, Kind.STREET, Kind.AVENUE, Kind.PATIO, Kind.TERRACE);

	/** Steps a level-15 lamp lights above the minimum ({@code 15 - MIN_LIGHT}). */
	static final int REACH = 15 - CitadelFinish.MIN_LIGHT;
	/** Spacing of the lamps: twice the reach, so two neighbours' light meets between them. */
	static final int SPACING = 2 * REACH - 1;
	/** Nearest two posts may be (blocks on the plane, squared). */
	private static final int GAP2 = (SPACING / 2) * (SPACING / 2);
	/** The cell above the walkway where the arm of a lantern post and the glass cross of a vitral are (feet + 3). */
	private static final int HEAD = 3;
	/** Cells around a lost cell in which posts are taken down. */
	private static final int UNDO = 3;

	private static final BlockState BASE = b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
	private static final BlockState SHAFT = b(Blocks.CHERRY_FENCE);
	private static final BlockState LANTERN_UP = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false);
	private static final BlockState LANTERN_DOWN = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
	private static final BlockState BRAZIER = Blocks.CAMPFIRE.defaultBlockState();
	private static final BlockState AMBER = Blocks.STAINED_GLASS.pick(DyeColor.ORANGE).defaultBlockState();
	private static final BlockState ROSE = Blocks.STAINED_GLASS.pick(DyeColor.PINK).defaultBlockState();

	private CitadelAmbience() {
	}

	/** A post: where its feet are and every cell it took (to take it down again). */
	private record Post(BlockPos feet, List<BlockPos> cells) {
	}

	/** The pass: posts at the edges, then lanterns under the roofs. */
	static void lights(CitadelCanvas c) {
		List<BlockPos> walk = CitadelFinish.walkable(c);
		Set<Long> before = new HashSet<>();
		for (BlockPos p : walk) {
			before.add(p.asLong());
		}
		Set<Long> reserved = CitadelFinish.reserved(c);
		List<Post> posts = posts(c, walk, before, reserved);
		verify(c, before, posts);
		Set<Long> feet = new HashSet<>();
		for (Post p : posts) {
			feet.add(p.feet().asLong());
		}
		arcades(c, walk, feet, reserved);
	}

	// ------------------------------------------------------------------ posts

	private static boolean free(CitadelCanvas c, BlockPos p, Set<Long> reserved) {
		BlockState s = c.cells.get(p.asLong());
		return (s == null || s.getBlock() == Blocks.AIR) && !reserved.contains(p.asLong());
	}

	private static boolean walkAt(Set<Long> walk, BlockPos q) {
		return walk.contains(q.asLong()) || walk.contains(q.above().asLong()) || walk.contains(q.below().asLong());
	}

	/**
	 * The side of {@code p} where the public way ends: the neighbour belongs to a zone that is not public
	 * space ({@link #STREETS}: a lot, the lawn, a wall, a ramp), when the opposite side is open way; else null.
	 * It follows the zones, not the blocks, so it holds at any scale of the geometry.
	 */
	private static @Nullable Direction edge(CitadelCanvas c, Set<Long> walk, BlockPos p) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos n = p.relative(d);
			if (!STREETS.contains(c.zoneAt(n.getX(), n.getZ()).kind()) && walk.contains(p.relative(d.getOpposite()).asLong())) {
				return d;
			}
		}
		return null;
	}

	private static final int[][] RING = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};

	/**
	 * True when a post at {@code p} leaves the way open: flat (no stair or ramp next to it), at least four of
	 * the eight cells around it walkable, and the walkable ones next to it (north, east, south, west) all on
	 * one run of the ring around it, so that they stay connected without {@code p}.
	 */
	private static boolean keepsConnected(Set<Long> walk, BlockPos p) {
		boolean[] w = new boolean[8];
		int open = 0;
		for (int i = 0; i < 8; i++) {
			BlockPos q = p.offset(RING[i][0], 0, RING[i][1]);
			w[i] = walk.contains(q.asLong());
			if (w[i]) {
				open++;
			}
			if (!w[i] && walkAt(walk, q) && i % 2 == 1) {
				return false; // a stair or a ramp starts here
			}
		}
		if (open < 4) {
			return false;
		}
		int runs = 0;
		// count the runs of the ring that hold an orthogonal walkable cell
		boolean[] seen = new boolean[8];
		for (int i = 1; i < 8; i += 2) {
			if (!w[i] || seen[i]) {
				continue;
			}
			runs++;
			int k = i;
			while (w[k] && !seen[k]) {
				seen[k] = true;
				k = (k + 1) % 8;
			}
			k = (i + 7) % 8;
			while (w[k] && !seen[k]) {
				seen[k] = true;
				k = (k + 7) % 8;
			}
		}
		return runs <= 1;
	}

	private static List<Post> posts(CitadelCanvas c, List<BlockPos> walk, Set<Long> walkSet, Set<Long> reserved) {
		List<Post> posts = new ArrayList<>();
		int n = 0;
		for (BlockPos p : walk) {
			Kind kind = c.zoneAt(p.getX(), p.getZ()).kind();
			if (!STREETS.contains(kind) || Math.floorMod(p.getX() + 4 * p.getZ(), SPACING) != 0) {
				continue;
			}
			Direction d = edge(c, walkSet, p);
			if (d == null || !keepsConnected(walkSet, p) || tooClose(posts, p)) {
				continue;
			}
			Post post = PLAZAS.contains(kind) && n % 4 == 0 ? brazier(c, p, reserved)
					: n % 3 == 2 ? vitral(c, p, reserved) : lanternPost(c, p, d.getOpposite(), reserved);
			if (post != null) {
				posts.add(post);
				n++;
			}
		}
		return posts;
	}

	private static boolean tooClose(List<Post> posts, BlockPos p) {
		for (Post o : posts) {
			int dx = o.feet().getX() - p.getX();
			int dz = o.feet().getZ() - p.getZ();
			if (dx * dx + dz * dz < GAP2 && Math.abs(o.feet().getY() - p.getY()) < 6) {
				return true;
			}
		}
		return false;
	}

	private static boolean allFree(CitadelCanvas c, Set<Long> reserved, List<BlockPos> cells) {
		for (BlockPos q : cells) {
			if (!free(c, q, reserved)) {
				return false;
			}
		}
		return true;
	}

	private static void put(CitadelCanvas c, List<BlockPos> took, BlockPos q, BlockState s, int layer) {
		c.put(q.getX(), q.getY(), q.getZ(), s, layer);
		took.add(q);
	}

	/** Base, two fences, a junction with a crossarm over the way, a lantern hanging from its end, a cap. */
	private static @Nullable Post lanternPost(CitadelCanvas c, BlockPos p, Direction toWay, Set<Long> reserved) {
		BlockPos arm = p.above(HEAD).relative(toWay);
		List<BlockPos> need = List.of(p.above(), p.above(2), p.above(HEAD), p.above(HEAD + 1), arm, arm.below());
		if (!free(c, p, reserved) || !allFree(c, reserved, need)) {
			return null;
		}
		List<BlockPos> took = new ArrayList<>();
		put(c, took, p, BASE, SOLID);
		put(c, took, p.above(), SHAFT, SOLID);
		put(c, took, p.above(2), SHAFT, SOLID);
		put(c, took, p.above(HEAD), SHAFT, SOLID);
		put(c, took, p.above(HEAD + 1), LANTERN_UP, DECOR);
		put(c, took, arm, SHAFT, SOLID);
		put(c, took, arm.below(), LANTERN_DOWN, HANGING);
		return new Post(p, took);
	}

	/** The same shaft with a lantern standing on top, inside a cross of stained glass. */
	private static @Nullable Post vitral(CitadelCanvas c, BlockPos p, Set<Long> reserved) {
		List<BlockPos> need = new ArrayList<>(List.of(p.above(), p.above(2), p.above(HEAD), p.above(HEAD + 1)));
		for (Direction d : Direction.Plane.HORIZONTAL) {
			need.add(p.above(HEAD).relative(d));
		}
		if (!allFree(c, reserved, need) || !free(c, p, reserved)) {
			return null;
		}
		List<BlockPos> took = new ArrayList<>();
		put(c, took, p, BASE, SOLID);
		put(c, took, p.above(), SHAFT, SOLID);
		put(c, took, p.above(2), SHAFT, SOLID);
		put(c, took, p.above(HEAD), LANTERN_UP, DECOR);
		int tint = Math.floorMod(p.getX() * 31 + p.getZ() * 17, 2);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			put(c, took, p.above(HEAD).relative(d), (d.get2DDataValue() + tint) % 2 == 0 ? AMBER : ROSE, DECOR);
		}
		put(c, took, p.above(HEAD + 1), tint == 0 ? ROSE : AMBER, DECOR);
		return new Post(p, took);
	}

	/** A two-high blackstone pillar with a lit campfire on top, above the reach of anyone walking by. */
	private static @Nullable Post brazier(CitadelCanvas c, BlockPos p, Set<Long> reserved) {
		if (!free(c, p, reserved) || !allFree(c, reserved, List.of(p.above(), p.above(2), p.above(HEAD)))) {
			return null;
		}
		List<BlockPos> took = new ArrayList<>();
		put(c, took, p, BASE, SOLID);
		put(c, took, p.above(), BASE, SOLID);
		put(c, took, p.above(2), BRAZIER, DECOR);
		return new Post(p, took);
	}

	/**
	 * Compares the cells reachable now with those before the posts: every post within {@value #UNDO}
	 * cells of a cell that was lost (and is not a post's own cell) is taken down, until nothing is lost.
	 */
	private static void verify(CitadelCanvas c, Set<Long> before, List<Post> posts) {
		for (int round = 0; round < 6 && !posts.isEmpty(); round++) {
			Set<Long> now = new HashSet<>();
			for (BlockPos p : CitadelFinish.walkable(c)) {
				now.add(p.asLong());
			}
			Set<Long> own = new HashSet<>();
			for (Post p : posts) {
				own.add(p.feet().asLong());
			}
			List<BlockPos> lost = new ArrayList<>();
			for (long k : before) {
				if (!now.contains(k) && !own.contains(k)) {
					lost.add(BlockPos.of(k));
				}
			}
			if (lost.isEmpty()) {
				return;
			}
			List<Post> gone = new ArrayList<>();
			for (Post p : posts) {
				for (BlockPos l : lost) {
					if (Math.abs(l.getX() - p.feet().getX()) <= UNDO && Math.abs(l.getZ() - p.feet().getZ()) <= UNDO
							&& Math.abs(l.getY() - p.feet().getY()) <= UNDO) {
						gone.add(p);
						break;
					}
				}
			}
			if (gone.isEmpty()) {
				gone.addAll(posts);
			}
			for (Post p : gone) {
				for (BlockPos q : p.cells()) {
					c.put(q.getX(), q.getY(), q.getZ(), AIR, SOLID);
				}
			}
			posts.removeAll(gone);
		}
	}

	// ------------------------------------------------------------------ arcades

	private static boolean emitterNear(CitadelCanvas c, BlockPos p, int r) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dy = -1; dy <= HEAD + 2; dy++) {
					BlockState s = c.cells.get(p.offset(dx, dy, dz).asLong());
					if (s != null && s.getLightEmission() > 0 && s.getBlock() != Blocks.LIGHT) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** A hanging lantern under each roof (a full block 3 to 6 cells above the way) on a lattice, never next to another lamp. */
	private static void arcades(CitadelCanvas c, List<BlockPos> walk, Set<Long> postFeet, Set<Long> reserved) {
		List<BlockPos> hung = new ArrayList<>();
		for (BlockPos p : walk) {
			Kind kind = c.zoneAt(p.getX(), p.getZ()).kind();
			if (!COVERED.contains(kind) || postFeet.contains(p.asLong()) || Math.floorMod(2 * p.getX() + 5 * p.getZ(), SPACING) != 0) {
				continue;
			}
			int ceiling = 0;
			for (int k = HEAD; k <= HEAD + 3 && ceiling == 0; k++) {
				BlockPos q = p.above(k);
				BlockState s = c.cells.get(q.asLong());
				if (s != null && !s.isAir()) {
					ceiling = s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, q) && s.getLightEmission() == 0 ? k : -1;
					break;
				}
			}
			if (ceiling <= 0) {
				continue;
			}
			boolean clear = true;
			for (int k = 1; k < ceiling; k++) {
				clear &= free(c, p.above(k), reserved);
			}
			if (!clear || emitterNear(c, p, REACH - 2) || tooCloseLamp(hung, p)) {
				continue;
			}
			BlockPos lamp = p.above(ceiling - 1);
			c.put(lamp.getX(), lamp.getY(), lamp.getZ(), LANTERN_DOWN, HANGING);
			hung.add(lamp);
		}
	}

	private static boolean tooCloseLamp(List<BlockPos> hung, BlockPos p) {
		for (BlockPos o : hung) {
			int dx = o.getX() - p.getX();
			int dz = o.getZ() - p.getZ();
			if (dx * dx + dz * dz < GAP2 && Math.abs(o.getY() - p.getY()) < 6) {
				return true;
			}
		}
		return false;
	}
}
