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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
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
 * <li>{@link #posts}: along the <b>edge</b> of the public way (streets, avenues, inner street, patio, terraces,
 *     gallery, bridges: {@link #STREETS}), where the next zone is not public (see {@link #edge}), it raises a
 *     post every {@link #SPACING} blocks along the edge, mirrored about the hub's axes ({@link #along}), so both
 *     sides of a street are paired and the rhythm is regular. Three kinds follow one another by their place on
 *     that rhythm (turn 0 to 3: toro, vitral, toro, brazier):
 *     the <i>toro</i> ({@link #toro}: polished-blackstone-brick plinth, two stripped cherry logs, a shroomlight
 *     lamp chamber, a pagoda roof of cherry slab and stairs, a fence finial), the <i>vitral</i>
 *     ({@link #vitral}: plinth, log, a lantern, a block of amber or rose stained glass, a hanging lantern, the
 *     same roof) and, in the plazas ({@link #PLAZAS}), the <i>brazier</i> ({@link #brazier}: a pedestal with a lit
 *     campfire in a tray of four slabs above head height: nobody can step in it).</li>
 * <li>{@link #relamp}: the old free-standing white froglight lamps (promenade posts) turn into the same warm
 *     chamber under the same roof.</li>
 * <li>{@link #arcades}: a hanging lantern under every roof ({@link #COVERED}) the public walks beneath,
 *     on the mirrored lattice {@code (2|x| + 5|z|) mod SPACING}, never closer than {@link #SPACING} - 1 to another
 *     hanging lantern (so a street gets one every {@link #SPACING} cells instead of a clump), never next to another
 *     lamp. The lantern is the kit's {@code paper_lantern_hanging} (a chochin on a cord; tone paper, rose or
 *     vermilion by the lattice cell, mirrored like the lattice), or the vanilla hanging lantern when the blocks
 *     module is off ({@link #hanging}).</li>
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
	private static final Set<Kind> STREETS = EnumSet.of(Kind.AVENUE, Kind.STREET, Kind.INNER_STREET, Kind.PATIO, Kind.TERRACE, Kind.GALLERY, Kind.BRIDGE);
	/** Plazas: some of their posts are braziers. */
	private static final Set<Kind> PLAZAS = EnumSet.of(Kind.PATIO, Kind.TERRACE, Kind.INNER_STREET);
	/** Public space where the roofs get hanging lanterns. */
	private static final Set<Kind> COVERED = EnumSet.of(Kind.GALLERY, Kind.BRIDGE, Kind.INNER_STREET, Kind.STREET, Kind.AVENUE, Kind.PATIO, Kind.TERRACE);

	/** Steps a level-15 lamp lights above the minimum ({@code 15 - MIN_LIGHT}). */
	static final int REACH = 15 - CitadelFinish.MIN_LIGHT;
	/** Spacing of the lamps: twice the reach, so two neighbours' light meets between them. */
	static final int SPACING = 2 * REACH - 1;
	/** The cell above the walkway where the arm of a lantern post and the glass cross of a vitral are (feet + 3). */
	private static final int HEAD = 3;
	/** Cells around a lost cell in which posts are taken down. */
	private static final int UNDO = 3;

	private static final BlockState PLINTH = b(Blocks.POLISHED_BLACKSTONE_BRICKS);
	private static final BlockState PEDESTAL = b(Blocks.CHISELED_POLISHED_BLACKSTONE);
	private static final BlockState POST = b(Blocks.STRIPPED_CHERRY_LOG);
	private static final BlockState SHAFT = b(Blocks.CHERRY_FENCE);
	private static final BlockState CHAMBER = b(Blocks.SHROOMLIGHT);
	private static final BlockState ROOF_SLAB = CitadelCanvas.slab(Blocks.CHERRY_SLAB);
	private static final BlockState TRAY = CitadelCanvas.slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
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
		relamp(c, reserved);
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

	/**
	 * Where along its edge a cell lies, mirrored about the citadel's axes: {@code |x|} on an edge that runs
	 * along x (the way ends to the north or south), {@code |z|} on one that runs along z. Posts stand only where
	 * it is a multiple of {@link #SPACING} (citadel/light-aesthetic): a regular rhythm along every straight edge,
	 * the two edges of a street paired post for post, and the whole pattern symmetric about the hub's axes.
	 */
	private static int along(BlockPos p, Direction edge) {
		return edge.getAxis() == Direction.Axis.Z ? Math.abs(p.getX()) : Math.abs(p.getZ());
	}

	private static List<Post> posts(CitadelCanvas c, List<BlockPos> walk, Set<Long> walkSet, Set<Long> reserved) {
		List<Post> posts = new ArrayList<>();
		for (BlockPos p : walk) {
			Kind kind = c.zoneAt(p.getX(), p.getZ()).kind();
			if (!STREETS.contains(kind)) {
				continue;
			}
			Direction d = edge(c, walkSet, p);
			if (d == null || Math.floorMod(along(p, d), SPACING) != 0 || !keepsConnected(walkSet, p) || tooClose(posts, p)) {
				continue;
			}
			// the kind follows the place on the rhythm (toro, vitral, toro, brazier in plazas), not the order of the scan
			int turn = Math.floorMod(along(p, d) / SPACING, 4);
			Post post = turn == 3 && PLAZAS.contains(kind) ? brazier(c, p, reserved) : turn == 1 ? vitral(c, p, reserved) : toro(c, p, reserved);
			if (post != null) {
				posts.add(post);
			}
		}
		return posts;
	}

	/** Greedy spacing along the way: no two posts nearer than {@link #SPACING} on the plane (about 6 floors apart or more may share a column). */
	private static boolean tooClose(List<Post> posts, BlockPos p) {
		for (Post o : posts) {
			int dx = o.feet().getX() - p.getX();
			int dz = o.feet().getZ() - p.getZ();
			if (dx * dx + dz * dz < SPACING * SPACING && Math.abs(o.feet().getY() - p.getY()) < 6) {
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

	/** The four cells around {@code center} (a roof's eaves, a brazier's tray). */
	private static List<BlockPos> ring(BlockPos center) {
		List<BlockPos> out = new ArrayList<>(4);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			out.add(center.relative(d));
		}
		return out;
	}

	/** True when a roof (slab, four stairs around it, a finial) fits over {@code top}: its cells are free air. */
	private static boolean roofFits(CitadelCanvas c, Set<Long> reserved, BlockPos top) {
		List<BlockPos> need = new ArrayList<>(ring(top));
		need.add(top);
		need.add(top.above());
		return allFree(c, reserved, need);
	}

	/** A pagoda roof: a cherry slab with four stairs falling away from it and a fence finial. */
	private static void roof(CitadelCanvas c, List<BlockPos> took, BlockPos top) {
		put(c, took, top, ROOF_SLAB, SOLID);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			put(c, took, top.relative(d), CitadelCanvas.stairs(Blocks.CHERRY_STAIRS, d), SOLID);
		}
		put(c, took, top.above(), SHAFT, SOLID);
	}

	/**
	 * The stone lantern (toro): a blackstone-brick plinth, two stripped cherry logs, a shroomlight lamp
	 * chamber and a pagoda roof over it, five blocks tall. A full-block mass, not a pin.
	 */
	private static @Nullable Post toro(CitadelCanvas c, BlockPos p, Set<Long> reserved) {
		BlockPos lamp = p.above(HEAD);
		if (!free(c, p, reserved) || !allFree(c, reserved, List.of(p.above(), p.above(2), lamp)) || !roofFits(c, reserved, lamp.above())) {
			return null;
		}
		List<BlockPos> took = new ArrayList<>();
		put(c, took, p, PLINTH, SOLID);
		put(c, took, p.above(), POST, SOLID);
		put(c, took, p.above(2), POST, SOLID);
		put(c, took, lamp, CHAMBER, SOLID);
		roof(c, took, lamp.above());
		return new Post(p, took);
	}

	/**
	 * The vitral lamp: the same plinth and log, then a lantern, a block of amber or rose stained glass lit
	 * from below and from above by two lanterns, under the pagoda roof (six blocks tall).
	 */
	private static @Nullable Post vitral(CitadelCanvas c, BlockPos p, Set<Long> reserved) {
		BlockPos glass = p.above(HEAD);
		if (!free(c, p, reserved) || !allFree(c, reserved, List.of(p.above(), p.above(2), glass, glass.above())) || !roofFits(c, reserved, glass.above(2))) {
			return null;
		}
		int tint = Math.floorMod(p.getX() * 31 + p.getZ() * 17, 2);
		List<BlockPos> took = new ArrayList<>();
		put(c, took, p, PLINTH, SOLID);
		put(c, took, p.above(), POST, SOLID);
		put(c, took, p.above(2), LANTERN_UP, DECOR);
		put(c, took, glass, tint == 0 ? AMBER : ROSE, SOLID);
		put(c, took, glass.above(), LANTERN_DOWN, HANGING);
		roof(c, took, glass.above(2));
		return new Post(p, took);
	}

	/** A pedestal with a lit campfire in a tray of four slabs, above head height: nobody can step in the fire. */
	private static @Nullable Post brazier(CitadelCanvas c, BlockPos p, Set<Long> reserved) {
		BlockPos fire = p.above(2);
		List<BlockPos> need = new ArrayList<>(ring(fire));
		need.addAll(List.of(p.above(), fire, fire.above()));
		if (!free(c, p, reserved) || !allFree(c, reserved, need)) {
			return null;
		}
		List<BlockPos> took = new ArrayList<>();
		put(c, took, p, PLINTH, SOLID);
		put(c, took, p.above(), PEDESTAL, SOLID);
		put(c, took, fire, BRAZIER, DECOR);
		for (BlockPos t : ring(fire)) {
			put(c, took, t, TRAY, SOLID);
		}
		return new Post(p, took);
	}

	/**
	 * The old free-standing froglight lamps (a white block on a pillar, open on all four sides and above:
	 * the promenade posts) become warm: a shroomlight under the same pagoda roof, when it fits.
	 */
	private static void relamp(CitadelCanvas c, Set<Long> reserved) {
		List<Long> lamps = new ArrayList<>();
		for (java.util.Map.Entry<Long, BlockState> e : c.cells.entrySet()) {
			if (e.getValue() == CitadelCanvas.LIGHT) {
				lamps.add(e.getKey());
			}
		}
		java.util.Collections.sort(lamps);
		for (long k : lamps) {
			BlockPos p = BlockPos.of(k);
			BlockState below = c.cells.get(p.below().asLong());
			if (reserved.contains(k) || below == null || !below.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, p.below()) || !open(c, p.above())) {
				continue;
			}
			boolean sides = true;
			for (BlockPos q : ring(p)) {
				sides &= open(c, q);
			}
			if (!sides) {
				continue;
			}
			c.put(p.getX(), p.getY(), p.getZ(), CHAMBER, SOLID);
			if (roofFits(c, reserved, p.above())) {
				roof(c, new ArrayList<>(), p.above());
			}
		}
	}

	/** True for a cell with nothing solid in it (absent, air or a block without collision). */
	private static boolean open(CitadelCanvas c, BlockPos q) {
		BlockState s = c.cells.get(q.asLong());
		return s == null || s.isAir() || s.getCollisionShape(EmptyBlockGetter.INSTANCE, q).isEmpty() && s.getLightEmission() == 0;
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

	private static final String[] TONES = {"paper", "paper", "rose", "paper", "vermilion", "paper"};

	/**
	 * The hanging lantern under a roof: the kit's {@code t3craft:paper_lantern_hanging} (looked up by id: the blocks
	 * module may be off) in the tone of the lattice cell of {@code p}, mirrored about the axes like everything here;
	 * the vanilla hanging lantern when it is not registered.
	 */
	private static BlockState hanging(BlockPos p) {
		Identifier id = Identifier.fromNamespaceAndPath("t3craft", "paper_lantern_hanging");
		if (!BuiltInRegistries.BLOCK.containsKey(id)) {
			return LANTERN_DOWN;
		}
		BlockState s = BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
		String tone = TONES[Math.floorMod(Math.floorDiv(Math.abs(p.getX()), SPACING) + 2 * Math.floorDiv(Math.abs(p.getZ()), SPACING), TONES.length)];
		Property<?> prop = s.getBlock().getStateDefinition().getProperty("tone");
		return prop == null ? s : withValue(s, prop, tone);
	}

	private static <T extends Comparable<T>> BlockState withValue(BlockState s, Property<T> prop, String value) {
		return prop.getValue(value).map(v -> s.setValue(prop, v)).orElse(s);
	}

	/** A hanging lantern under each roof (a full block 3 to 6 cells above the way) on a mirrored lattice, evenly spaced, never next to another lamp. */
	private static void arcades(CitadelCanvas c, List<BlockPos> walk, Set<Long> postFeet, Set<Long> reserved) {
		List<BlockPos> hung = new ArrayList<>();
		for (BlockPos p : walk) {
			Kind kind = c.zoneAt(p.getX(), p.getZ()).kind();
			// mirrored lattice (|x|, |z|): the lanterns of the four quarters of the citadel answer one another
			if (!COVERED.contains(kind) || postFeet.contains(p.asLong()) || Math.floorMod(2 * Math.abs(p.getX()) + 5 * Math.abs(p.getZ()), SPACING) != 0) {
				continue;
			}
			int ceiling = 0;
			for (int k = HEAD; k <= HEAD + 3 && ceiling == 0; k++) {
				BlockPos q = p.above(k);
				BlockState s = c.cells.get(q.asLong());
				if (s != null && !s.isAir()) {
					// leaves are a full block too, but a tree crown is not an eave: nothing hangs from it
					ceiling = s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, q) && s.getLightEmission() == 0 && !(s.getBlock() instanceof LeavesBlock) ? k : -1;
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
			c.put(lamp.getX(), lamp.getY(), lamp.getZ(), hanging(lamp), HANGING);
			hung.add(lamp);
		}
	}

	/**
	 * Whether another hanging lantern is within {@code SPACING - 1} cells (flat, same storey): the lattice has a short
	 * diagonal vector (2, 1), so without this a three-wide street got a lantern every two cells in a row.
	 */
	private static boolean tooCloseLamp(List<BlockPos> hung, BlockPos p) {
		int min = SPACING - 1;
		for (BlockPos o : hung) {
			int dx = o.getX() - p.getX();
			int dz = o.getZ() - p.getZ();
			if (dx * dx + dz * dz < min * min && Math.abs(o.getY() - p.getY()) < 6) {
				return true;
			}
		}
		return false;
	}
}
