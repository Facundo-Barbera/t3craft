package dev.t3craft.hq;

import dev.t3craft.hq.CampusGeometry.Area;
import dev.t3craft.hq.CampusGeometry.Edge;
import dev.t3craft.hq.CampusGeometry.Hex;
import dev.t3craft.hq.CampusGeometry.Pavilion;
import dev.t3craft.hq.CampusGeometry.Vertex;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * Blocks of the campus ({@link CampusStyle}): {@link HqLayout} (campus indices) in, {@link BuildPlan}
 * out. Pure and deterministic like {@link HqGenerator}.
 *
 * <p>Build order: hub, pavilions, barrio buildings (commons, halls, upper storeys, roofs), then the
 * <b>sparse</b> grounds ({@link PlanBuilder#sparse}): outdoor stairs, barrio patios, the hub plaza,
 * pavilion walks, parks and the path network. Groups: {@code hub}, {@code archive},
 * {@code pavilion_<key>}, {@code gate}, {@code barrio_<parcel>} (commons and patio),
 * {@code module_<index>} (a hall storey, its roof and stair), {@code park_<parcel>},
 * {@code grounds_hub} / {@code grounds_<parcel>} (paths owned by the first plot that has them).
 */
final class CampusGenerator {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	/** Hall length (desks along both long walls, three per wall). */
	static final int HALL_W = 19;
	/** Desk centres along a hall. */
	static final int[] DESK_U = {4, 9, 14};
	/** Storey height (floor layer + six of walls). */
	static final int STOREY = 7;
	/** Hall depth per lot (1..3). */
	static final int[] LOT_DEPTH = {0, 13, 15, 17};
	/** Hub building half size. */
	static final int HUB = 13;
	static final int HUB_TOP = 28;
	static final int BEACON_Y = 25;
	/** The hub's board plane and its half width. */
	static final int BOARD_Z = -HUB + 1;
	static final int BOARD_HALF = 4;

	enum Roof {
		GABLE, HIP, SAWTOOTH, FLAT
	}

	/** One barrio's materials. */
	record Pal(String key, Block log, Block stripped, BlockState wall, BlockState footing, BlockState floor, Block roof, BlockState roofSlab,
			BlockState ridge, BlockState leaves, BlockState path, BlockState accent, Roof[] roofs) {
	}

	private static BlockState leaves(Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
	}

	private static BlockState slab(Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
	}

	static final List<Pal> PALS = List.of(
			new Pal("cerezo", Blocks.CHERRY_LOG, Blocks.STRIPPED_CHERRY_LOG, Blocks.CALCITE.defaultBlockState(),
					Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.CHERRY_PLANKS.defaultBlockState(), Blocks.DEEPSLATE_TILE_STAIRS,
					slab(Blocks.DEEPSLATE_TILE_SLAB), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), leaves(Blocks.CHERRY_LEAVES),
					Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState(),
					new Roof[] {Roof.GABLE, Roof.SAWTOOTH, Roof.HIP}),
			new Pal("terracota", Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
					Blocks.BRICKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.BRICK_STAIRS, slab(Blocks.BRICK_SLAB),
					Blocks.BRICKS.defaultBlockState(), leaves(Blocks.OAK_LEAVES), Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
					Blocks.BRICKS.defaultBlockState(), new Roof[] {Roof.HIP, Roof.GABLE, Roof.FLAT}),
			new Pal("cobre", Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.TUFF_BRICKS.defaultBlockState(),
					Blocks.POLISHED_TUFF.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.CUT_COPPER_STAIRS.waxed().oxidized(),
					slab(Blocks.CUT_COPPER_SLAB.waxed().oxidized()), Blocks.CUT_COPPER.waxed().oxidized().defaultBlockState(),
					leaves(Blocks.SPRUCE_LEAVES), Blocks.POLISHED_TUFF.defaultBlockState(), Blocks.CHISELED_TUFF.defaultBlockState(),
					new Roof[] {Roof.SAWTOOTH, Roof.HIP, Roof.GABLE}),
			new Pal("prismarina", Blocks.BIRCH_LOG, Blocks.STRIPPED_BIRCH_LOG, Blocks.QUARTZ_BRICKS.defaultBlockState(),
					Blocks.SMOOTH_STONE.defaultBlockState(), Blocks.BIRCH_PLANKS.defaultBlockState(), Blocks.DARK_PRISMARINE_STAIRS,
					slab(Blocks.DARK_PRISMARINE_SLAB), Blocks.DARK_PRISMARINE.defaultBlockState(), leaves(Blocks.BIRCH_LEAVES),
					Blocks.SMOOTH_STONE.defaultBlockState(), Blocks.PRISMARINE_BRICKS.defaultBlockState(),
					new Roof[] {Roof.FLAT, Roof.GABLE, Roof.SAWTOOTH}),
			new Pal("resina", Blocks.MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_LOG, Blocks.PACKED_MUD.defaultBlockState(),
					Blocks.MUD_BRICKS.defaultBlockState(), Blocks.MANGROVE_PLANKS.defaultBlockState(), Blocks.RESIN_BRICK_STAIRS,
					slab(Blocks.RESIN_BRICK_SLAB), Blocks.RESIN_BRICKS.defaultBlockState(), leaves(Blocks.AZALEA_LEAVES),
					Blocks.MUD_BRICKS.defaultBlockState(), Blocks.CHISELED_RESIN_BRICKS.defaultBlockState(),
					new Roof[] {Roof.GABLE, Roof.FLAT, Roof.HIP}),
			new Pal("palido", Blocks.PALE_OAK_LOG, Blocks.STRIPPED_PALE_OAK_LOG, Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState(),
					Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.PALE_OAK_PLANKS.defaultBlockState(),
					Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB),
					Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), leaves(Blocks.FLOWERING_AZALEA_LEAVES),
					Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
					new Roof[] {Roof.HIP, Roof.SAWTOOTH, Roof.FLAT}));

	/** The campus' own materials (hub, pavilions, network). */
	private static final Pal CAMPUS = new Pal("campus", Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.QUARTZ_BRICKS.defaultBlockState(),
			Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.DARK_PRISMARINE_STAIRS,
			slab(Blocks.DARK_PRISMARINE_SLAB), Blocks.DARK_PRISMARINE.defaultBlockState(), leaves(Blocks.OAK_LEAVES),
			Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.SMOOTH_STONE.defaultBlockState(), new Roof[] {Roof.HIP, Roof.HIP, Roof.HIP});

	static Pal pal(int parcel) {
		return PALS.get(Math.floorMod(parcel, PALS.size()));
	}

	// ------------------------------------------------------------------ frames

	/** A local frame: (u, y, v) → world, turned by {@code rot} around (ox, oz). */
	record Frame(int ox, int oz, Rotation rot) {
		BlockPos pos(int u, int y, int v) {
			BlockPos r = new BlockPos(u, 0, v).rotate(rot);
			return new BlockPos(ox + r.getX(), y, oz + r.getZ());
		}

		Direction dir(Direction d) {
			return rot.rotate(d);
		}

		BlockState state(BlockState s) {
			return s.rotate(rot);
		}

		Frame sub(int u, int v, Rotation r) {
			BlockPos o = pos(u, 0, v);
			return new Frame(o.getX(), o.getZ(), rot.getRotated(r));
		}

		Box box(int u1, int y1, int v1, int u2, int y2, int v2) {
			BlockPos a = pos(u1, y1, v1);
			BlockPos c = pos(u2, y2, v2);
			return Box.of(a.getX(), y1, a.getZ(), c.getX(), y2, c.getZ());
		}
	}

	/** Writes through a frame (states turned with it). */
	record Pen(PlanBuilder.Canvas c, Frame f) {
		void set(int u, int y, int v, BlockState s) {
			c.set(f.pos(u, y, v), f.state(s));
		}

		void decor(int u, int y, int v, BlockState s) {
			c.decor(f.pos(u, y, v), f.state(s));
		}

		void hang(int u, int y, int v, BlockState s) {
			BlockPos p = f.pos(u, y, v);
			c.hang(p.getX(), p.getY(), p.getZ(), f.state(s));
		}
	}

	/**
	 * Cells of one sparse module, gathered first (so its bounds are tight), written on {@link #flush}.
	 * A cell some module already owns is skipped ({@link #free}).
	 */
	final class Buffer {
		private final String id;
		private final String group;
		private final LinkedHashMap<Long, Object[]> cells = new LinkedHashMap<>();
		private final @Nullable Frame frame;

		Buffer(String id, String group, @Nullable Frame frame) {
			this.id = id;
			this.group = group;
			this.frame = frame;
		}

		boolean free(int x, int y, int z) {
			return !b.taken(x, y, z) && !cells.containsKey(BlockPos.asLong(x, y, z));
		}

		boolean freeL(int u, int y, int v) {
			BlockPos p = frame.pos(u, y, v);
			return free(p.getX(), y, p.getZ());
		}

		private boolean put(int x, int y, int z, BlockState s, @Nullable CompoundTag be, int layer) {
			if (!free(x, y, z)) {
				return false;
			}
			cells.put(BlockPos.asLong(x, y, z), new Object[] {new BlockPos(x, y, z), s, be, layer});
			return true;
		}

		boolean set(int x, int y, int z, BlockState s) {
			return put(x, y, z, s, null, PlanBuilder.SOLID);
		}

		boolean decor(int x, int y, int z, BlockState s) {
			return put(x, y, z, s, null, PlanBuilder.DECOR);
		}

		boolean hang(int x, int y, int z, BlockState s) {
			return put(x, y, z, s, null, PlanBuilder.HANGING);
		}

		boolean setL(int u, int y, int v, BlockState s) {
			BlockPos p = frame.pos(u, y, v);
			return set(p.getX(), y, p.getZ(), frame.state(s));
		}

		boolean decorL(int u, int y, int v, BlockState s) {
			BlockPos p = frame.pos(u, y, v);
			return decor(p.getX(), y, p.getZ(), frame.state(s));
		}

		boolean hangL(int u, int y, int v, BlockState s) {
			BlockPos p = frame.pos(u, y, v);
			return hang(p.getX(), y, p.getZ(), frame.state(s));
		}

		void plaqueL(int u, int y, int v, Direction front, CompoundTag data) {
			BlockPos p = frame.pos(u, y, v);
			put(p.getX(), y, p.getZ(), kit().plaque(frame.dir(front)), data, PlanBuilder.DECOR);
		}

		void flush() {
			if (cells.isEmpty()) {
				return;
			}
			int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE;
			int x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
			for (Object[] c : cells.values()) {
				BlockPos p = (BlockPos) c[0];
				x1 = Math.min(x1, p.getX());
				y1 = Math.min(y1, p.getY());
				z1 = Math.min(z1, p.getZ());
				x2 = Math.max(x2, p.getX());
				y2 = Math.max(y2, p.getY());
				z2 = Math.max(z2, p.getZ());
			}
			b.group(group);
			try (PlanBuilder.Canvas c = b.sparse(id, new Box(x1, y1, z1, x2, y2, z2))) {
				for (Object[] e : cells.values()) {
					BlockPos p = (BlockPos) e[0];
					BlockState s = (BlockState) e[1];
					CompoundTag be = (CompoundTag) e[2];
					int layer = (Integer) e[3];
					if (be != null) {
						c.decor(p, s, be);
					} else if (layer == PlanBuilder.HANGING) {
						c.hang(p.getX(), p.getY(), p.getZ(), s);
					} else if (layer == PlanBuilder.DECOR) {
						c.decor(p, s);
					} else {
						c.set(p, s);
					}
				}
			}
			b.group(null);
		}
	}

	// ------------------------------------------------------------------ state

	private final CampusStyle style;
	private final HqLayout layout;
	private final PlanBuilder b = new PlanBuilder();
	/** parcel → its modules (by index). */
	private final TreeMap<Integer, List<HqLayout.Module>> barrios = new TreeMap<>();
	/** Outermost ring with a barrio (at least 1). */
	private final int outer;

	CampusGenerator(CampusStyle style, HqLayout layout) {
		this.style = style;
		this.layout = layout;
		for (HqLayout.Module m : layout.modules()) {
			barrios.computeIfAbsent(CampusLayout.parcelOf(m.index()), k -> new ArrayList<>()).add(m);
		}
		int k = 1;
		for (int p : barrios.keySet()) {
			k = Math.max(k, CampusGeometry.ringOf(p));
		}
		outer = k;
	}

	/** Parcels that are drawn: barrios, and free parcels of ring 1 and of the rings inside the outermost one (parks). */
	List<Integer> presentParcels() {
		List<Integer> out = new ArrayList<>();
		for (int p = 0; p < CampusGeometry.parcelsWithin(outer); p++) {
			int ring = CampusGeometry.ringOf(p);
			if (barrios.containsKey(p) || ring == 1 || ring < outer) {
				out.add(p);
			}
		}
		return out;
	}

	BuildPlan generate() {
		hub();
		for (Pavilion pv : Pavilion.values()) {
			pavilion(pv);
		}
		for (var e : barrios.entrySet()) {
			barrioBuildings(e.getKey(), e.getValue());
		}
		for (var e : barrios.entrySet()) {
			barrioStairs(e.getKey(), e.getValue());
		}
		for (var e : barrios.entrySet()) {
			barrioGrounds(e.getKey(), e.getValue());
		}
		hubPlaza();
		for (Pavilion pv : Pavilion.values()) {
			pavilionWalk(pv);
		}
		List<Integer> present = presentParcels();
		for (int p : present) {
			if (!barrios.containsKey(p)) {
				park(p);
			}
		}
		network(present);
		return b.build();
	}

	// ------------------------------------------------------------------ materials

	private static HqKit kit() {
		return HqKit.active();
	}

	private static boolean custom() {
		return kit().custom();
	}

	private static BlockState log(Block b, Direction.Axis axis) {
		return b.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	private static BlockState stairs(Block b, Direction back) {
		return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, back).setValue(BlockStateProperties.HALF, Half.BOTTOM);
	}

	private static BlockState glass() {
		return Blocks.GLASS_PANE.defaultBlockState();
	}

	private static BlockState litPanel() {
		return custom() ? kit().wallPanel(true) : Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
	}

	private static BlockState lantern() {
		return custom() ? kit().lantern() : Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false);
	}

	private static BlockState hanging() {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
	}

	private static BlockState floorTile() {
		return kit().floor();
	}

	private static BlockState accentTile() {
		return kit().floorAccent();
	}

	private static BlockState carpet(DyeColor c) {
		return Blocks.CARPET.pick(c).defaultBlockState();
	}

	private static BlockState water() {
		return Blocks.WATER.defaultBlockState();
	}

	private static BlockState grass() {
		return Blocks.GRASS_BLOCK.defaultBlockState();
	}

	private static BlockState moss() {
		return Blocks.MOSS_BLOCK.defaultBlockState();
	}

	private static BlockState flower(int i) {
		BlockState[] f = {Blocks.CORNFLOWER.defaultBlockState(), Blocks.LILY_OF_THE_VALLEY.defaultBlockState(),
				Blocks.PINK_PETALS.defaultBlockState(), Blocks.FERN.defaultBlockState(), Blocks.TORCHFLOWER.defaultBlockState(),
				Blocks.SHORT_GRASS.defaultBlockState(), Blocks.WILDFLOWERS.defaultBlockState(), Blocks.BUSH.defaultBlockState()};
		return f[Math.floorMod(i, f.length)];
	}

	private static int hash(int a, int b) {
		int h = a * 734287 ^ b * 912931;
		h ^= h >>> 15;
		h *= 0x2c1b3c6d;
		h ^= h >>> 12;
		return h & 0x7fffffff;
	}

	// ------------------------------------------------------------------ hub

	/**
	 * The hub: a 27 × 27 hall, eleven blocks of wall, a stepped roof up to a glazed drum and a
	 * lantern cupola that holds the beacon over the hologram's free column. Inside: the "needs
	 * attention" board on the north wall, the kanban wall (west) and the big screen (east) with
	 * benches facing them. Doors south (entrance), east and west.
	 */
	private void hub() {
		b.group("hub");
		try (PlanBuilder.Canvas c = b.module("hub", new Box(-HUB, 0, -HUB, HUB, HUB_TOP, HUB), List.of())) {
			for (int z = -HUB; z <= HUB; z++) {
				for (int x = -HUB; x <= HUB; x++) {
					int d = Math.max(Math.abs(x), Math.abs(z));
					int r2 = x * x + z * z;
					c.set(x, 0, z, d == HUB ? CAMPUS.footing() : d == HUB - 1 || r2 <= 9 && r2 >= 4 ? accentTile() : floorTile());
					if (d == HUB) {
						hubWall(c, x, z);
					}
					hubRoof(c, x, z, d);
				}
			}
			board(c);
			int bench = 0;
			for (int sx = -1; sx <= 1; sx += 2) {
				Direction look = sx > 0 ? Direction.EAST : Direction.WEST;
				for (int z = -3; z <= 3; z++) {
					BlockPos p = new BlockPos(7 * sx, 1, z);
					c.set(p, style.bench(look.getOpposite()));
					b.anchor(Anchor.at(AnchorNames.bench(bench++), p.relative(look), look));
				}
				c.decor(7 * sx, 1, -5, kit().gardenPlanter());
				c.decor(7 * sx, 1, 5, kit().gardenPlanter());
				// lanterns in the corners, lit panels in the walls (hubWall)
				for (int sz = -1; sz <= 1; sz += 2) {
					c.decor((HUB - 2) * sx, 1, (HUB - 2) * sz, lantern());
				}
			}
			// big screen (east wall) and kanban wall (west wall)
			HqGeometry.Surface screen = new HqGeometry.Surface(new BlockPos(HUB - 1, 2, -3), Direction.SOUTH, Direction.WEST, 7, 4);
			for (BlockPos p : screen.cells()) {
				c.set(p, style.screen());
			}
			for (int z = -3; z <= 3; z++) {
				c.set(HUB - 1, 1, z, style.screenBase());
			}
			b.anchor(Anchor.face(AnchorNames.bigScreen("hub", 7, 4, HqGenerator.hubBinding(layout)), screen.origin().above(3), screen.facing()));
			HqGeometry.Surface wall = new HqGeometry.Surface(new BlockPos(-HUB + 1, 2, 4), Direction.NORTH, Direction.EAST, 9, 4);
			for (BlockPos p : wall.cells()) {
				c.set(p, style.screen());
			}
			for (int z = -4; z <= 4; z++) {
				c.set(-HUB + 1, 1, z, style.screenBase());
			}
			b.anchor(Anchor.face(AnchorNames.THREAD_WALL, wall.origin(), wall.facing()));
			b.anchor(Anchor.face(AnchorNames.THREAD_WALL_END, wall.origin().relative(wall.along(), wall.width() - 1).above(wall.height() - 1),
					wall.facing()));
			b.anchor(Anchor.at(AnchorNames.THREAD_WALL_FRONT, wall.anchorCell().relative(wall.facing(), 3).atY(1), wall.facing().getOpposite()));
			// the hologram's column stays open up to the beacon in the cupola
			for (int y = 1; y < BEACON_Y; y++) {
				c.set(0, y, 0, AIR);
			}
			c.set(0, BEACON_Y, 0, style.beacon());
			int walk = 0;
			for (int wz : new int[] {6, -6}) {
				for (int sx = -1; sx <= 1; sx += 2) {
					b.anchor(Anchor.at(AnchorNames.walkHub(walk++), new BlockPos(4 * sx, 1, wz), Direction.SOUTH));
				}
			}
			b.anchor(Anchor.at(AnchorNames.HUB, new BlockPos(0, 1, 0), Direction.SOUTH));
			b.anchor(new Anchor(AnchorNames.HOLOGRAM, 0.5, 5.0, 0.5, Direction.SOUTH, new BlockPos(0, 5, 0)));
			b.anchor(Anchor.at(AnchorNames.BEACON, new BlockPos(0, BEACON_Y, 0), Direction.SOUTH));
			b.anchor(Anchor.at(AnchorNames.ENTRANCE, new BlockPos(0, 1, HUB), Direction.SOUTH));
		}
		b.group(null);
	}

	private boolean hubDoor(int x, int z) {
		return z == HUB && Math.abs(x) <= 2 || Math.abs(x) == HUB && z >= 7 && z <= 10;
	}

	private void hubWall(PlanBuilder.Canvas c, int x, int z) {
		boolean ns = Math.abs(z) == HUB;
		int t = ns ? x : z;
		Direction.Axis along = ns ? Direction.Axis.X : Direction.Axis.Z;
		boolean post = Math.abs(t) == HUB || Math.abs(t) == 9 || Math.abs(t) == 5 || (!ns && (t == 6 || t == 11));
		boolean door = hubDoor(x, z);
		for (int y = 1; y <= 11; y++) {
			BlockState s;
			if (post) {
				s = log(CAMPUS.log(), Direction.Axis.Y);
			} else if (y == 11 || door && y == 5) {
				s = log(CAMPUS.stripped(), along);
			} else if (door && y <= 4) {
				s = AIR;
			} else if (y == 1) {
				s = CAMPUS.footing();
			} else if (y >= 7 && y <= 9) {
				s = glass();
			} else if (y == 4 && !door && Math.abs(t) % 4 == 2) {
				s = litPanel();
			} else {
				s = CAMPUS.wall();
			}
			c.set(x, y, z, s);
		}
	}

	/** Stepped roof: a ring from the wall (y 12) up to the drum (|d| = 7), the drum, a cone to the cupola. */
	private void hubRoof(PlanBuilder.Canvas c, int x, int z, int d) {
		Direction in = ClassicStyle.inward(x, z);
		if (d >= 8) {
			int y = 12 + (HUB - d);
			c.set(x, y, z, stairs(CAMPUS.roof(), in));
			if (d == 9 && (x == 0 || z == 0)) {
				c.hang(x, y - 1, z, hanging());
			}
		} else if (d == 7) {
			// drum: beam at the roof line, glass ring, beam
			c.set(x, 17, z, log(CAMPUS.stripped(), Math.abs(z) == 7 ? Direction.Axis.X : Direction.Axis.Z));
			boolean post = Math.abs(x) == 7 && Math.abs(z) == 7 || x == 0 || z == 0;
			for (int y = 18; y <= 19; y++) {
				c.set(x, y, z, post ? log(CAMPUS.log(), Direction.Axis.Y) : Blocks.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_BLUE).defaultBlockState());
			}
			c.set(x, 20, z, stairs(CAMPUS.roof(), in));
		} else if (d >= 2) {
			c.set(x, 20 + (7 - d), z, d == 2 ? CAMPUS.ridge() : stairs(CAMPUS.roof(), in));
		} else if (d == 1) {
			// lantern cupola around the beacon (0, BEACON_Y, 0)
			c.set(x, BEACON_Y, z, Math.abs(x) == 1 && Math.abs(z) == 1 ? log(CAMPUS.log(), Direction.Axis.Y) : glass());
			c.set(x, BEACON_Y + 1, z, CAMPUS.roofSlab());
		} else {
			c.set(0, BEACON_Y + 1, 0, CAMPUS.ridge());
			c.set(0, BEACON_Y + 2, 0, log(CAMPUS.log(), Direction.Axis.Y));
			c.set(0, BEACON_Y + 3, 0, Blocks.END_ROD.defaultBlockState());
		}
	}

	/** The "needs attention" board against the hub's north wall: frame, face, bulbs and standing rows. */
	private void board(PlanBuilder.Canvas c) {
		int z = BOARD_Z;
		for (int y = 1; y <= 6; y++) {
			c.set(-BOARD_HALF - 1, y, z, log(CAMPUS.log(), Direction.Axis.Y));
			c.set(BOARD_HALF + 1, y, z, log(CAMPUS.log(), Direction.Axis.Y));
		}
		int lamp = 0;
		for (int x = -BOARD_HALF; x <= BOARD_HALF; x++) {
			c.set(x, 1, z, log(CAMPUS.stripped(), Direction.Axis.X));
			for (int y = 2; y <= 4; y++) {
				c.set(x, y, z, style.palette().get(HqStyle.Role.BOARD));
			}
			if (Math.floorMod(x, 2) == 0) {
				c.set(x, 5, z, style.statusLamp());
				b.anchor(Anchor.face(AnchorNames.boardLamp(lamp++), new BlockPos(x, 5, z), Direction.SOUTH));
			} else {
				c.set(x, 5, z, log(CAMPUS.stripped(), Direction.Axis.X));
			}
			c.set(x, 6, z, log(CAMPUS.stripped(), Direction.Axis.X));
		}
		c.decor(new BlockPos(0, 6, z + 1), kit().plaque(Direction.SOUTH), kit().plaqueData(DyeColor.ORANGE, "", "Needs", "attention", ""));
		b.anchor(Anchor.face(AnchorNames.BOARD, new BlockPos(0, 3, z), Direction.SOUTH));
		b.anchor(Anchor.at(AnchorNames.BOARD_FRONT, new BlockPos(0, 1, z + 1), Direction.NORTH));
		int slot = 0;
		for (int row = 2; row <= 3; row++) {
			for (int i = 0; i <= 2 * BOARD_HALF; i++) {
				int x = i == 0 ? 0 : (i % 2 == 1 ? (i + 1) / 2 : -(i / 2));
				b.anchor(Anchor.at(AnchorNames.boardSlot(slot++), new BlockPos(x, 1, z + row), Direction.NORTH));
			}
		}
	}

	/** The plaza around the hub (sparse): paving, four fountains, trees in planters, lamp posts. */
	private void hubPlaza() {
		Buffer g = new Buffer("hub_plaza", "hub", new Frame(0, 0, Rotation.NONE));
		int a = CampusGeometry.AREA;
		for (int z = -a; z <= a; z++) {
			for (int x = -a; x <= a; x++) {
				int d = Math.max(Math.abs(x), Math.abs(z));
				if (d <= HUB) {
					continue;
				}
				int ax = Math.abs(x), az = Math.abs(z);
				boolean fountain = Math.abs(ax - 19) <= 1 && Math.abs(az - 19) <= 1;
				boolean grove = ax == 19 && az == 8 || az == 19 && ax == 8;
				if (fountain) {
					boolean centre = ax == 19 && az == 19;
					g.set(x, 0, z, centre ? CAMPUS.accent() : water());
					if (centre) {
						g.set(x, 1, z, Blocks.PRISMARINE_WALL.defaultBlockState());
						g.set(x, 2, z, Blocks.SEA_LANTERN.defaultBlockState());
					}
					continue;
				}
				if (grove) {
					g.set(x, 0, z, moss());
					tree(g, x, z, Blocks.OAK_LOG, leaves(Blocks.FLOWERING_AZALEA_LEAVES), 4);
					continue;
				}
				boolean ring = d == HUB + 1 || d == a || (ax <= 1 || az <= 1);
				g.set(x, 0, z, ring ? CAMPUS.accent() : (x + z) % 2 == 0 ? CAMPUS.path() : Blocks.ANDESITE.defaultBlockState());
				if (d == HUB + 3 && (ax == HUB + 3 && az % 6 == 3 || az == HUB + 3 && ax % 6 == 3) && ax > 3 && az > 3) {
					lampPost(g, x, z);
				}
			}
		}
		g.flush();
	}

	private void lampPost(Buffer g, int x, int z) {
		if (custom()) {
			g.set(x, 1, z, kit().gardenLantern());
			return;
		}
		g.set(x, 1, z, log(CAMPUS.log(), Direction.Axis.Y));
		g.set(x, 2, z, log(CAMPUS.log(), Direction.Axis.Y));
		g.decor(x, 3, z, lantern());
	}

	/** A small tree: trunk, two layers of crown and a top. */
	private void tree(Buffer g, int x, int z, Block trunk, BlockState crown, int height) {
		for (int y = 1; y <= height; y++) {
			g.set(x, y, z, log(trunk, Direction.Axis.Y));
		}
		for (int dz = -2; dz <= 2; dz++) {
			for (int dx = -2; dx <= 2; dx++) {
				int m = Math.abs(dx) + Math.abs(dz);
				if (m == 0) {
					continue;
				}
				if (m <= 3) {
					g.set(x + dx, height, z + dz, crown);
				}
				if (m <= 2) {
					g.set(x + dx, height + 1, z + dz, crown);
				}
			}
		}
		g.set(x, height + 1, z, crown);
		g.set(x, height + 2, z, crown);
	}

	// ------------------------------------------------------------------ pavilions

	/** A pavilion's look: its footprint, wall height and materials. */
	private record PavSpec(BiPredicate<Integer, Integer> in, int wallTop, BlockState wall, BlockState footing, BlockState floor, Block roof,
			BlockState ridge, BlockState glass, boolean open) {
	}

	private PavSpec spec(Pavilion pv) {
		return switch (pv) {
			case ARCHIVE -> new PavSpec((x, z) -> x * x + z * z <= 52, 6, Blocks.QUARTZ_BRICKS.defaultBlockState(), CAMPUS.footing(),
					floorTile(), Blocks.DEEPSLATE_TILE_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
					Blocks.STAINED_GLASS_PANE.pick(DyeColor.YELLOW).defaultBlockState(), false);
			case REVIEW -> new PavSpec((x, z) -> Math.abs(x) + Math.abs(z) <= 10, 7, Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
					Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.BIRCH_PLANKS.defaultBlockState(), Blocks.CUT_COPPER_STAIRS.waxed().exposed(),
					Blocks.CUT_COPPER.waxed().exposed().defaultBlockState(), Blocks.STAINED_GLASS_PANE.pick(DyeColor.CYAN).defaultBlockState(),
					false);
			case SCHEDULED -> new PavSpec((x, z) -> Math.abs(x) <= 5 && Math.abs(z) <= 5, 17, Blocks.TUFF_BRICKS.defaultBlockState(),
					Blocks.POLISHED_TUFF.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.DEEPSLATE_TILE_STAIRS,
					Blocks.CUT_COPPER.waxed().unaffected().defaultBlockState(), Blocks.STAINED_GLASS_PANE.pick(DyeColor.ORANGE).defaultBlockState(),
					false);
			case ERRORS -> new PavSpec((x, z) -> Math.abs(x) <= 2 || Math.abs(z) <= 2, 7, Blocks.DYED_TERRACOTTA.pick(DyeColor.RED).defaultBlockState(),
					Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.NETHER_BRICK_STAIRS,
					Blocks.SHROOMLIGHT.defaultBlockState(), Blocks.STAINED_GLASS_PANE.pick(DyeColor.ORANGE).defaultBlockState(), false);
			case LOUNGE -> new PavSpec((x, z) -> z >= 1 || x <= -1, 5, Blocks.MOSS_BLOCK.defaultBlockState(), CAMPUS.footing(),
					Blocks.BAMBOO_MOSAIC.defaultBlockState(), Blocks.BAMBOO_MOSAIC_STAIRS, Blocks.MOSS_BLOCK.defaultBlockState(), glass(), true);
			case GATE -> null;
		};
	}

	/** Depth of each footprint cell of a pavilion (0 = wall), -1 outside; index [dx + 7][dz + 7]. */
	private static int[][] depth(BiPredicate<Integer, Integer> in) {
		int[][] d = new int[15][15];
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				if (!in.test(x, z) || Math.abs(x) > 7 || Math.abs(z) > 7) {
					d[x + 7][z + 7] = -1;
					continue;
				}
				int best = 99;
				for (int ox = -8; ox <= 8; ox++) {
					for (int oz = -8; oz <= 8; oz++) {
						boolean outside = Math.abs(ox) == 8 || Math.abs(oz) == 8 || !in.test(ox, oz);
						if (outside) {
							best = Math.min(best, Math.max(Math.abs(ox - x), Math.abs(oz - z)) - 1);
						}
					}
				}
				d[x + 7][z + 7] = best;
			}
		}
		return d;
	}

	private static String group(Pavilion pv) {
		return pv == Pavilion.ARCHIVE ? HqGenerator.GROUP_ARCHIVE : pv == Pavilion.GATE ? "gate" : "pavilion_" + pv.key;
	}

	/** A pavilion building (all but the gate): footprint, walls, a roof stepping up with the depth, its interior. */
	private void pavilion(Pavilion pv) {
		PavSpec s = spec(pv);
		if (s == null) {
			return;
		}
		Vertex v = pv.at();
		int[][] depth = depth(s.in());
		int maxDepth = 0;
		for (int[] col : depth) {
			for (int d : col) {
				maxDepth = Math.max(maxDepth, d);
			}
		}
		int top = s.wallTop() + maxDepth + 4;
		b.group(group(pv));
		try (PlanBuilder.Canvas c = b.module("pavilion_" + pv.key, new Box(v.x() - 7, 0, v.z() - 7, v.x() + 7, top, v.z() + 7), List.of())) {
			Pen p = new Pen(c, new Frame(v.x(), v.z(), Rotation.NONE));
			for (int x = -7; x <= 7; x++) {
				for (int z = -7; z <= 7; z++) {
					int d = depth[x + 7][z + 7];
					if (d < 0) {
						p.set(x, 0, z, (x + z) % 2 == 0 ? CAMPUS.path() : CAMPUS.accent());
						continue;
					}
					p.set(x, 0, z, d == 0 ? s.footing() : s.floor());
					boolean door = d == 0 && (Math.abs(x) <= 1 || Math.abs(z) <= 1);
					if (d == 0) {
						for (int y = 1; y <= s.wallTop(); y++) {
							BlockState w;
							boolean post = s.open() ? (Math.floorMod(x + z, 3) == 0) : (Math.abs(x) == Math.abs(z) || x == 0 && !door || z == 0 && !door);
							if (y == s.wallTop()) {
								w = log(Blocks.STRIPPED_SPRUCE_LOG, Math.abs(z) >= Math.abs(x) ? Direction.Axis.X : Direction.Axis.Z);
							} else if (door && y <= 3 || s.open() && !post) {
								w = AIR;
							} else if (post) {
								w = log(Blocks.SPRUCE_LOG, Direction.Axis.Y);
							} else if (y == 1) {
								w = s.footing();
							} else if (y >= 3 && y < s.wallTop() - 0 && (pv != Pavilion.SCHEDULED || y % 4 != 0)) {
								w = s.glass();
							} else {
								w = s.wall();
							}
							p.set(x, y, z, w);
						}
					}
					// roof: one shell layer stepping up with the depth, stairs leaning on the deeper side
					int y = s.wallTop() + 1 + d;
					Direction up = deeper(depth, x, z);
					if (up == null) {
						p.set(x, y, z, s.ridge());
						if (d == maxDepth) {
							p.set(x, y + 1, z, s.ridge());
						}
					} else {
						p.set(x, y, z, stairs(s.roof(), up));
					}
				}
			}
			pavilionInterior(pv, c, p, s, maxDepth);
		}
		b.group(null);
	}

	private static @Nullable Direction deeper(int[][] depth, int x, int z) {
		int d = depth[x + 7][z + 7];
		for (Direction dir : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			int nx = x + dir.getStepX();
			int nz = z + dir.getStepZ();
			if (Math.abs(nx) <= 7 && Math.abs(nz) <= 7 && depth[nx + 7][nz + 7] > d) {
				return dir;
			}
		}
		return null;
	}

	private void pavilionInterior(Pavilion pv, PlanBuilder.Canvas c, Pen p, PavSpec s, int maxDepth) {
		Vertex v = pv.at();
		BlockPos o = new BlockPos(v.x(), 0, v.z());
		String room = "walk_room_" + pv.key;
		switch (pv) {
			case ARCHIVE -> {
				// a rotunda of books around the single archive terminal
				for (int x = -6; x <= 6; x++) {
					for (int z = -6; z <= 6; z++) {
						int r2 = x * x + z * z;
						if (r2 >= 26 && r2 <= 40 && Math.abs(x) > 1 && Math.abs(z) > 1) {
							for (int y = 1; y <= 3; y++) {
								p.set(x, y, z, y == 3 ? Blocks.CHISELED_BOOKSHELF.defaultBlockState() : Blocks.BOOKSHELF.defaultBlockState());
							}
						}
					}
				}
				BlockPos t = o.offset(0, 1, 0);
				c.set(t, style.archiveTerminal(Direction.SOUTH));
				b.anchor(Anchor.face(AnchorNames.ARCHIVE_TERMINAL, t, Direction.SOUTH));
				b.anchor(Anchor.at(AnchorNames.ARCHIVE, t.south(), Direction.NORTH));
				p.decor(-2, 1, 2, lantern());
				p.decor(2, 1, 2, lantern());
				p.hang(0, s.wallTop() + maxDepth, 0, hanging());
				b.anchor(Anchor.at(room, o.offset(0, 1, 6), Direction.NORTH));
				for (int i = 0; i < 3; i++) {
					b.anchor(Anchor.at(room + "_" + i, o.offset(-2 + 2 * i, 1, 4), Direction.NORTH));
				}
			}
			case REVIEW, ERRORS, SCHEDULED -> {
				// a big screen on the north side, standing spots facing it
				int w = pv == Pavilion.REVIEW ? 5 : 3;
				int h = pv == Pavilion.SCHEDULED ? 2 : 3;
				int sz = pv == Pavilion.SCHEDULED ? -4 : -6;
				HqGeometry.Surface scr = new HqGeometry.Surface(o.offset(-(w / 2), 2, sz), Direction.EAST, Direction.SOUTH, w, h);
				for (BlockPos q : scr.cells()) {
					c.set(q, style.screen());
				}
				for (int i = 0; i < w; i++) {
					c.set(scr.origin().relative(Direction.EAST, i).below(), style.screenBase());
				}
				b.anchor(Anchor.face(AnchorNames.bigScreen(pv.key, w, h, ""), scr.origin().above(h - 1), Direction.SOUTH));
				int n = 0;
				int step = pv == Pavilion.ERRORS ? 1 : 2;
				for (int row = 0; row < 2; row++) {
					for (int x = -step; x <= step; x += step) {
						b.anchor(Anchor.at(room + "_" + n++, o.offset(x, 1, sz + 3 + 2 * row), Direction.NORTH));
					}
				}
				b.anchor(Anchor.at(room, o.offset(0, 1, pv == Pavilion.SCHEDULED ? 4 : 6), Direction.NORTH));
				int lx = pv == Pavilion.SCHEDULED ? 4 : pv == Pavilion.ERRORS ? 1 : 2;
				int lz = pv == Pavilion.SCHEDULED ? 3 : pv == Pavilion.ERRORS ? 4 : 5;
				p.decor(-lx, 1, lz, lantern());
				p.decor(lx, 1, lz, lantern());
				if (pv == Pavilion.SCHEDULED) {
					// the clock: a 3×3 screen on the tower's south face, high up
					HqGeometry.Surface clock = new HqGeometry.Surface(o.offset(-1, 12, 5), Direction.EAST, Direction.SOUTH, 3, 3);
					for (BlockPos q : clock.cells()) {
						c.set(q, style.screen());
					}
					b.anchor(Anchor.face(AnchorNames.bigScreen("clock", 3, 3, ""), clock.origin().above(2), Direction.SOUTH));
					for (int y = 6; y <= 14; y += 4) {
						p.hang(-3, y, 0, hanging());
						p.hang(3, y, 0, hanging());
						for (int x = -4; x <= 4; x++) {
							if (Math.abs(x) >= 3) {
								p.set(x, y + 1, 0, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
							}
						}
					}
				}
				if (pv == Pavilion.ERRORS) {
					p.set(0, 1, -5, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, true));
				}
			}
			case LOUNGE -> {
				// benches round a fire bowl in the L, plants, a reading corner
				int n = 0;
				for (int x = -5; x <= 5; x += 2) {
					p.set(x, 1, 5, style.bench(Direction.SOUTH));
					b.anchor(Anchor.at(room + "_" + n++, o.offset(x, 1, 4), Direction.NORTH));
				}
				for (int z = -5; z <= 1; z += 2) {
					p.set(-5, 1, z, style.bench(Direction.WEST));
					b.anchor(Anchor.at(room + "_" + n++, o.offset(-4, 1, z), Direction.EAST));
				}
				p.set(-2, 1, 2, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
				p.decor(-6, 1, 6, kit().gardenPlanter());
				p.decor(6, 1, 6, kit().gardenPlanter());
				p.decor(-6, 1, -6, kit().gardenPlanter());
				// the open corner of the L: a lily pond
				for (int x = 1; x <= 6; x++) {
					for (int z = -6; z <= -1; z++) {
						if ((x - 4) * (x - 4) + (z + 4) * (z + 4) <= 6) {
							p.set(x, 0, z, water());
						}
					}
				}
				p.decor(4, 1, -4, Blocks.LILY_PAD.defaultBlockState());
				b.anchor(Anchor.at(room, o.offset(0, 1, 2), Direction.NORTH));
			}
			default -> {
			}
		}
		if (pv != Pavilion.ARCHIVE) {
			String label = switch (pv) {
				case REVIEW -> "Review";
				case SCHEDULED -> "Scheduled";
				case ERRORS -> "Errors";
				case LOUNGE -> "Lounge";
				default -> "";
			};
			if (pv != Pavilion.LOUNGE) {
				int pz = pv == Pavilion.SCHEDULED ? 4 : 6;
				c.decor(o.offset(0, 5, pz), kit().plaque(Direction.NORTH), kit().plaqueData(DyeColor.WHITE, "", label, "", ""));
			}
		} else {
			c.decor(o.offset(0, 4, 6), kit().plaque(Direction.NORTH), kit().plaqueData(DyeColor.BROWN, "", "Archive", "", ""));
		}
	}

	/** The paved walk round a pavilion plot (|d| 8..10); the whole plot for the entrance gate, with its torii and the spawn. */
	private void pavilionWalk(Pavilion pv) {
		Vertex v = pv.at();
		Buffer g = new Buffer(pv.key + "_walk", group(pv), new Frame(v.x(), v.z(), Rotation.NONE));
		int r = CampusGeometry.PAVILION;
		for (int dz = -r; dz <= r; dz++) {
			for (int dx = -r; dx <= r; dx++) {
				int d = Math.max(Math.abs(dx), Math.abs(dz));
				if (pv != Pavilion.GATE && d < 8) {
					continue;
				}
				if (pv == Pavilion.GATE) {
					// two lily ponds either side of the processional way
					if (Math.abs(dx) >= 4 && Math.abs(dx) <= 7 && Math.abs(dz) <= 4) {
						g.setL(dx, 0, dz, water());
						if ((dx + dz) % 3 == 0) {
							g.decorL(dx, 1, dz, Blocks.LILY_PAD.defaultBlockState());
						}
						continue;
					}
				}
				boolean edge = d == r || d == 8 && pv != Pavilion.GATE || Math.abs(dx) <= 1 && pv == Pavilion.GATE;
				g.setL(dx, 0, dz, edge ? CAMPUS.accent() : CAMPUS.path());
			}
		}
		// lamps at the plot corners
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				BlockPos q = new BlockPos(v.x() + 9 * sx, 0, v.z() + 9 * sz);
				lampPost(g, q.getX(), q.getZ());
			}
		}
		if (pv == Pavilion.GATE) {
			// a torii over the way north, towards the hub
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int y = 1; y <= 5; y++) {
					g.setL(3 * sx, y, -5, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y));
				}
				g.setL(3 * sx, 1, 5, kit().gardenLantern());
			}
			for (int x = -4; x <= 4; x++) {
				g.setL(x, 6, -5, Math.abs(x) == 4 ? CAMPUS.roofSlab() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
				if (Math.abs(x) <= 2) {
					g.setL(x, 5, -5, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X));
				}
			}
			BlockPos spawn = new BlockPos(v.x(), 1, v.z());
			g.set(spawn.getX(), 1, spawn.getZ(), AIR);
			g.set(spawn.getX(), 2, spawn.getZ(), AIR);
			b.anchor(Anchor.at(AnchorNames.SPAWN, spawn, Direction.NORTH));
		}
		g.flush();
	}

	// ------------------------------------------------------------------ barrios

	/** Frame of quadrant {@code q} of barrio {@code p}: base coordinates are the north-west quadrant's. */
	static Frame quadrant(int p, int q) {
		Hex h = CampusGeometry.parcel(p);
		return new Frame(h.x(), h.z(), CampusGeometry.barrioRotation(p)).sub(0, 0, Rotation.values()[q]);
	}

	/** Frame of the hall on lot {@code lot} (1..3) of barrio {@code p}: u along the hall, v across. */
	static Frame hallFrame(int p, int lot) {
		return quadrant(p, lot).sub(-23, -23, Rotation.NONE);
	}

	private void barrioBuildings(int p, List<HqLayout.Module> mods) {
		HqLayout.Module first = mods.get(0);
		Pal pal = pal(p);
		DyeColor accent = HqStyle.envColor(first);
		commons(p, first, pal, accent);
		for (int lot = 1; lot <= 3; lot++) {
			int g = CampusLayout.index(p, lot - 1);
			int u = CampusLayout.index(p, lot - 1 + 3);
			HqLayout.Module ground = layout.moduleAt(g);
			HqLayout.Module upper = layout.moduleAt(u);
			if (ground == null && upper == null) {
				continue;
			}
			Frame f = hallFrame(p, lot);
			int d = LOT_DEPTH[lot];
			hallStorey(f, pal, d, 0, g, ground, accent);
			if (upper != null) {
				hallStorey(f, pal, d, 1, u, upper, accent);
			}
			roof(f, pal, d, upper != null ? 2 * STOREY : STOREY, pal.roofs()[lot - 1], "roof_" + g, HqGenerator.group(upper != null ? u : g));
		}
	}

	/** One storey of a hall: floor, walls, tie beams and lanterns, a runner and the six desk slots. */
	private void hallStorey(Frame f, Pal pal, int d, int storey, int index, HqLayout.@Nullable Module m, DyeColor accent) {
		int w = HALL_W;
		int mid = d / 2;
		int y0 = storey * STOREY;
		b.group(HqGenerator.group(index));
		try (PlanBuilder.Canvas c = b.module("hall_" + index, f.box(0, y0, 0, w - 1, y0 + 6, d - 1), List.of())) {
			Pen p = new Pen(c, f);
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					boolean edge = u == 0 || u == w - 1 || v == 0 || v == d - 1;
					p.set(u, y0, v, edge ? pal.footing() : pal.floor());
					if (edge) {
						hallWall(p, pal, u, v, w, d, y0, storey);
					}
				}
			}
			for (int u : new int[] {6, 13}) {
				for (int v = 1; v < d - 1; v++) {
					p.set(u, y0 + 6, v, log(pal.stripped(), Direction.Axis.Z));
				}
				p.hang(u, y0 + 5, mid, hanging());
			}
			for (int u = 1; u < w - 1; u++) {
				p.decor(u, y0 + 1, mid, carpet(accent));
			}
			p.decor(1, y0 + 1, 3, lantern());
			p.decor(1, y0 + 1, d - 4, lantern());
			p.decor(w - 2, y0 + 1, 3, kit().gardenPlanter());
			p.decor(w - 2, y0 + 1, d - 4, kit().gardenPlanter());
			for (int slot = 0; slot < HqLayout.DESKS; slot++) {
				desk(c, f, y0, d, index, slot, m == null ? null : m.threadAt(slot));
			}
			if (m != null) {
				BlockPos door = storey == 0 ? f.pos(w - 2, 1, mid) : f.pos(11, y0 + 1, d - 2);
				Direction in = f.dir(storey == 0 ? Direction.WEST : Direction.NORTH);
				b.anchor(Anchor.at(AnchorNames.module(index), door, in));
				b.anchor(Anchor.at(AnchorNames.walkModule(index), f.pos(9, y0 + 1, mid), f.dir(Direction.WEST)));
			}
		}
		b.group(null);
	}

	private void hallWall(Pen p, Pal pal, int u, int v, int w, int d, int y0, int storey) {
		int mid = d / 2;
		boolean longWall = v == 0 || v == d - 1;
		boolean corner = (u == 0 || u == w - 1) && longWall;
		boolean post = corner || longWall && (u == 6 || u == 13) || !longWall && (v == mid - 2 || v == mid + 2);
		Direction.Axis along = longWall ? Direction.Axis.X : Direction.Axis.Z;
		boolean door = storey == 0 ? u == w - 1 && Math.abs(v - mid) <= 1 : v == d - 1 && (u == 11 || u == 12);
		for (int h = 1; h <= 6; h++) {
			BlockState s;
			if (post) {
				s = log(pal.log(), Direction.Axis.Y);
			} else if (h == 6 || door && h == 4) {
				s = log(pal.stripped(), along);
			} else if (door && h <= 3) {
				s = AIR;
			} else if (h == 1) {
				s = pal.footing();
			} else if (longWall) {
				s = h >= 4 ? glass() : pal.wall();
			} else {
				s = h >= 2 && h <= 4 && Math.abs(v - mid) <= 1 ? (u == 0 ? glass() : litPanel()) : pal.wall();
			}
			p.set(u, y0 + h, v, s);
		}
	}

	/** One desk slot: vacant = bare floor; taken = a standing desk with its 3×2 screen and the free standing cell. */
	private void desk(PlanBuilder.Canvas c, Frame f, int y0, int d, int index, int slot, @Nullable String thread) {
		if (thread == null) {
			return;
		}
		int k = slot / 2;
		boolean north = slot % 2 == 0;
		int cu = DESK_U[k];
		Direction facing = f.dir(north ? Direction.NORTH : Direction.SOUTH);
		BlockPos monitor = f.pos(cu, y0 + 2, north ? 1 : d - 2);
		BlockPos top = monitor.below();
		BlockPos stand = f.pos(cu, y0 + 1, north ? 2 : d - 3);
		Direction front = facing.getOpposite();
		Direction side = facing.getClockWise();
		c.set(top.relative(side.getOpposite()), kit().desk(front));
		c.set(top, kit().desk(front));
		c.set(top.relative(side), kit().desk(front));
		for (BlockPos cell : Workstation.screenCells(Workstation.screenOriginFor(monitor, front), front.getCounterClockWise())) {
			c.set(cell, style.computer(true));
		}
		c.set(stand, AIR);
		c.set(stand.above(), AIR);
		Anchor deskA = Anchor.at(AnchorNames.deskSlot(index, slot), stand, facing);
		Anchor screen = Anchor.face(AnchorNames.screenSlot(index, slot), monitor, front);
		b.workstation(new Workstation(index, slot, thread, deskA, monitor, screen, Workstation.screenOriginFor(monitor, front)));
		b.anchor(deskA.renamed(AnchorNames.desk(thread)));
		b.anchor(screen.renamed(AnchorNames.monitor(thread)));
	}

	/** A hall's roof on top of its highest storey. */
	private void roof(Frame f, Pal pal, int d, int yb, Roof type, String id, String group) {
		int w = HALL_W;
		int mid = d / 2;
		int rise = switch (type) {
			case GABLE, HIP -> mid + 1;
			case SAWTOOTH -> 5;
			case FLAT -> 2;
		};
		b.group(group);
		try (PlanBuilder.Canvas c = b.module(id, f.box(0, yb, 0, w - 1, yb + rise, d - 1), List.of())) {
			Pen p = new Pen(c, f);
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					boolean side = v == 0 || v == d - 1;
					switch (type) {
						case GABLE -> {
							int h = Math.min(v, d - 1 - v);
							int y = yb + h;
							if (v == mid) {
								p.set(u, y, v, pal.ridge());
								p.set(u, y + 1, v, pal.roofSlab());
							} else {
								p.set(u, y, v, stairs(pal.roof(), v < mid ? Direction.SOUTH : Direction.NORTH));
							}
							if (u == 0 || u == w - 1) {
								for (int yy = yb; yy < y; yy++) {
									p.set(u, yy, v, yy == yb + 2 && Math.abs(v - mid) <= 1 ? glass() : pal.wall());
								}
								if (v == mid) {
									for (int yy = yb; yy < y; yy++) {
										p.set(u, yy, v, yy == yb + 2 ? glass() : log(pal.log(), Direction.Axis.Y));
									}
								}
							}
						}
						case HIP -> {
							int hu = Math.min(u, w - 1 - u);
							int hv = Math.min(v, d - 1 - v);
							int h = Math.min(hu, hv);
							int y = yb + h;
							if (h == mid) {
								p.set(u, y, v, pal.ridge());
								p.set(u, y + 1, v, pal.roofSlab());
							} else {
								Direction back = h == v ? Direction.SOUTH : h == d - 1 - v ? Direction.NORTH : h == u ? Direction.EAST : Direction.WEST;
								p.set(u, y, v, stairs(pal.roof(), back));
							}
						}
						case SAWTOOTH -> {
							int t = u % 5;
							if (t < 4) {
								int y = yb + t;
								p.set(u, y, v, stairs(pal.roof(), Direction.EAST));
								if (side || u == w - 1) {
									for (int yy = yb; yy < y; yy++) {
										p.set(u, yy, v, pal.wall());
									}
								}
							} else {
								for (int yy = yb; yy <= yb + 3; yy++) {
									p.set(u, yy, v, side ? log(pal.log(), Direction.Axis.Y) : Blocks.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_BLUE).defaultBlockState());
								}
								p.set(u, yb + 4, v, pal.roofSlab());
							}
						}
						case FLAT -> {
							boolean edge = side || u == 0 || u == w - 1;
							p.set(u, yb, v, edge ? pal.ridge() : moss());
							if (edge) {
								p.set(u, yb + 1, v, pal.roofSlab());
							} else if (hash(u, v) % 7 == 0) {
								p.decor(u, yb + 1, v, Blocks.FLOWERING_AZALEA.defaultBlockState());
							} else if (hash(v, u) % 3 == 0) {
								p.decor(u, yb + 1, v, Blocks.MOSS_CARPET.defaultBlockState());
							}
						}
					}
				}
			}
		}
		b.group(null);
	}

	/**
	 * The commons house on lot 0 of every barrio: a 13 × 13 pavilion with a tall pyramid roof, a spire
	 * with the environment's flag, the project's big screen, its plaque, and the project's anchors
	 * ({@link AnchorNames#wing}, {@link AnchorNames#walk}), so they never move as the barrio grows.
	 */
	private void commons(int p, HqLayout.Module first, Pal pal, DyeColor accent) {
		Frame f = quadrant(p, 0).sub(-19, -19, Rotation.NONE);
		b.group("barrio_" + p);
		try (PlanBuilder.Canvas c = b.module("commons_" + p, f.box(0, 0, 0, 12, 18, 12), List.of())) {
			Pen pen = new Pen(c, f);
			for (int u = 0; u <= 12; u++) {
				for (int v = 0; v <= 12; v++) {
					boolean edge = u == 0 || u == 12 || v == 0 || v == 12;
					boolean centre = Math.abs(u - 6) <= 1 && Math.abs(v - 6) <= 1;
					pen.set(u, 0, v, edge ? pal.footing() : centre ? pal.accent() : custom() ? floorTile() : pal.floor());
					if (edge) {
						boolean corner = (u == 0 || u == 12) && (v == 0 || v == 12);
						boolean post = corner || u == 4 || u == 8 || v == 4 || v == 8;
						boolean door = u == 12 && v >= 5 && v <= 7 || v == 12 && u >= 5 && u <= 7;
						Direction.Axis along = v == 0 || v == 12 ? Direction.Axis.X : Direction.Axis.Z;
						for (int y = 1; y <= 7; y++) {
							BlockState s;
							if (post) {
								s = log(pal.log(), Direction.Axis.Y);
							} else if (y == 7 || door && y == 4) {
								s = log(pal.stripped(), along);
							} else if (door && y <= 3) {
								s = AIR;
							} else if (y == 1) {
								s = pal.footing();
							} else if (y == 2) {
								s = litPanel();
							} else if (y >= 3 && y <= 5 && v != 0) {
								s = glass();
							} else {
								s = pal.wall();
							}
							pen.set(u, y, v, s);
						}
					}
					int h = Math.min(Math.min(u, 12 - u), Math.min(v, 12 - v));
					int y = 8 + h;
					if (h == 6) {
						pen.set(u, y, v, pal.ridge());
					} else {
						Direction back = h == v ? Direction.SOUTH : h == 12 - v ? Direction.NORTH : h == u ? Direction.EAST : Direction.WEST;
						pen.set(u, y, v, stairs(pal.roof(), back));
					}
				}
			}
			// spire and the environment's flag
			for (int y = 15; y <= 18; y++) {
				pen.set(6, y, 6, log(pal.log(), Direction.Axis.Y));
			}
			for (int u = 7; u <= 8; u++) {
				for (int y = 16; y <= 17; y++) {
					pen.set(u, y, 6, Blocks.WOOL.pick(accent).defaultBlockState());
				}
			}
			// the project's big screen on the north wall, facing the room
			BlockPos origin = f.pos(4, 2, 1);
			Direction along = f.dir(Direction.EAST);
			Direction facing = f.dir(Direction.SOUTH);
			HqGeometry.Surface scr = new HqGeometry.Surface(origin, along, facing, 5, 3);
			for (BlockPos q : scr.cells()) {
				c.set(q, style.screen());
			}
			for (int i = 0; i < 5; i++) {
				c.set(origin.relative(along, i).below(), style.screenBase());
			}
			b.anchor(Anchor.face(AnchorNames.bigScreen("barrio@" + p, 5, 3, "project:" + first.projectKey()), origin.above(2), facing));
			for (int v = 3; v <= 9; v++) {
				pen.set(1, 1, v, Blocks.BOOKSHELF.defaultBlockState());
				pen.set(1, 2, v, Blocks.BOOKSHELF.defaultBlockState());
			}
			pen.decor(11, 1, 1, lantern());
			pen.decor(11, 1, 11, kit().gardenPlanter());
			pen.decor(2, 1, 11, lantern());
			String label = first.label();
			String l1 = label.length() <= SignTags.LINE ? label : label.substring(0, SignTags.LINE);
			String l2 = label.length() <= SignTags.LINE ? "" : label.substring(SignTags.LINE);
			BlockPos plaque = f.pos(11, 5, 6);
			c.decor(plaque, kit().plaque(f.dir(Direction.WEST)), kit().plaqueData(accent, l1, l2, "", first.envLabel()));
			b.anchor(Anchor.at(AnchorNames.wing(first.projectKey()), f.pos(11, 1, 6), f.dir(Direction.WEST)));
			b.anchor(Anchor.at(AnchorNames.walk(first.projectKey()), f.pos(6, 1, 4), f.dir(Direction.NORTH)));
		}
		b.group(null);
	}

	/** The outdoor stair of every upper storey: a landing at the upper door and six steps down along the hall. */
	private void barrioStairs(int p, List<HqLayout.Module> mods) {
		Pal pal = pal(p);
		for (int lot = 1; lot <= 3; lot++) {
			int u = CampusLayout.index(p, lot - 1 + 3);
			if (layout.moduleAt(u) == null) {
				continue;
			}
			int d = LOT_DEPTH[lot];
			Frame f = hallFrame(p, lot);
			Buffer g = new Buffer("stair_" + u, HqGenerator.group(u), f);
			for (int v = d; v <= d + 1; v++) {
				for (int uu = 11; uu <= 12; uu++) {
					for (int y = 1; y <= STOREY; y++) {
						g.setL(uu, y, v, y == STOREY ? pal.floor() : pal.footing());
					}
				}
				// seven steps: the top one level with the landing, the bottom one half a block above the strip
				for (int k = 0; k <= 6; k++) {
					int su = 10 - k;
					int sy = 7 - k;
					for (int y = 1; y < sy; y++) {
						g.setL(su, y, v, pal.footing());
					}
					g.setL(su, sy, v, stairs(pal.roof(), Direction.EAST));
				}
			}
			g.flush();
		}
	}

	/**
	 * A barrio's grounds (sparse): the cross of walks from the four gates to the patio, the patio with
	 * its fountain or tree, the verges, the strips in front of the halls, gardens on the empty lots
	 * and the commons' lot, a hedge round the plot with gateposts.
	 */
	private void barrioGrounds(int p, List<HqLayout.Module> mods) {
		Pal pal = pal(p);
		Hex h = CampusGeometry.parcel(p);
		Frame fb = new Frame(h.x(), h.z(), CampusGeometry.barrioRotation(p));
		Buffer g = new Buffer("barrio_" + p + "_grounds", "barrio_" + p, fb);
		int a = CampusGeometry.AREA;
		// patio feature
		boolean fountain = p % 2 == 0;
		for (int lx = -1; lx <= 1; lx++) {
			for (int lz = -1; lz <= 1; lz++) {
				boolean centre = lx == 0 && lz == 0;
				if (fountain) {
					g.setL(lx, 0, lz, centre ? pal.accent() : water());
					if (centre) {
						g.setL(0, 1, 0, log(pal.log(), Direction.Axis.Y));
						g.setL(0, 2, 0, log(pal.log(), Direction.Axis.Y));
						g.setL(0, 3, 0, Blocks.SEA_LANTERN.defaultBlockState());
					}
				} else {
					g.setL(lx, 0, lz, moss());
					if (!centre) {
						g.decorL(lx, 1, lz, flower(lx * 3 + lz));
					}
				}
			}
		}
		if (!fountain) {
			BlockPos t = fb.pos(0, 0, 0);
			tree(g, t.getX(), t.getZ(), pal.log(), pal.leaves(), 5);
		}
		for (int lx = -a; lx <= a; lx++) {
			for (int lz = -a; lz <= a; lz++) {
				int ax = Math.abs(lx);
				int az = Math.abs(lz);
				if (ax <= 1 && az <= 1) {
					continue;
				}
				boolean corridor = ax <= 2 || az <= 2;
				boolean patio = ax <= 6 && az <= 6;
				if (patio) {
					g.setL(lx, 0, lz, ax == 6 || az == 6 || ax == 2 && az == 2 ? pal.accent() : pal.path());
					if (ax == 6 && az == 6) {
						BlockPos q = fb.pos(lx, 0, lz);
						lampPost(g, q.getX(), q.getZ());
					}
				} else if (corridor) {
					boolean gatePost = (ax == a || az == a) && (ax == 3 || az == 3);
					g.setL(lx, 0, lz, ax == 0 || az == 0 ? pal.accent() : pal.path());
					if (gatePost) {
						continue;
					}
				}
				if (ax == a || az == a) {
					int other = ax == a ? az : ax;
					if (other == 3) {
						// gateposts and the lintel across the gate
						for (int y = 1; y <= 4; y++) {
							g.setL(lx, y, lz, log(pal.log(), Direction.Axis.Y));
						}
						g.setL(lx, 0, lz, pal.footing());
						g.decorL(lx, 5, lz, lantern());
					} else if (other > 3) {
						g.setL(lx, 0, lz, moss());
						g.setL(lx, 1, lz, pal.leaves());
					}
				}
			}
		}
		// lintels over the four gates
		for (int q = 0; q < 4; q++) {
			Frame fq = fb.sub(0, 0, Rotation.values()[q]);
			for (int lx = -2; lx <= 2; lx++) {
				BlockPos c = fq.pos(lx, 4, -a);
				g.set(c.getX(), 4, c.getZ(), fq.state(log(pal.stripped(), Direction.Axis.X)));
			}
		}
		// quadrants
		for (int q = 0; q < 4; q++) {
			Frame fq = quadrant(p, q);
			boolean hall = q > 0 && (layout.moduleAt(CampusLayout.index(p, q - 1)) != null || layout.moduleAt(CampusLayout.index(p, q - 1 + 3)) != null);
			int d = q > 0 ? LOT_DEPTH[q] : 0;
			for (int x = -a + 1; x <= -3; x++) {
				for (int z = -a + 1; z <= -3; z++) {
					if (Math.abs(x) <= 6 && Math.abs(z) <= 6) {
						continue;
					}
					BlockPos w = fq.pos(x, 0, z);
					boolean verge = x >= -4 || z >= -4;
					if (verge) {
						g.set(w.getX(), 0, w.getZ(), pal.path());
					} else if (q == 0) {
						boolean house = x >= -19 && x <= -7 && z >= -19 && z <= -7;
						boolean apron = x >= -6 && z >= -14 && z <= -12 || z >= -6 && x >= -14 && x <= -12;
						if (apron) {
							g.set(w.getX(), 0, w.getZ(), pal.path());
						} else if (!house) {
							garden(g, w, x, z, p);
						}
					} else if (hall) {
						boolean inHall = z <= -23 + d - 1;
						if (!inHall) {
							g.set(w.getX(), 0, w.getZ(), z == -23 + d + 2 ? pal.accent() : pal.path());
						}
					} else {
						garden(g, w, x, z, p);
					}
				}
			}
			// trees in the gardens
			if (q == 0) {
				for (int[] t : new int[][] {{-22, -22}, {-22, -9}, {-9, -22}}) {
					BlockPos w = fq.pos(t[0], 0, t[1]);
					tree(g, w.getX(), w.getZ(), pal.log(), pal.leaves(), 4);
				}
			} else if (!hall) {
				for (int[] t : new int[][] {{-19, -19}, {-11, -20}, {-20, -10}, {-11, -11}}) {
					BlockPos w = fq.pos(t[0], 0, t[1]);
					tree(g, w.getX(), w.getZ(), pal.log(), pal.leaves(), 4 + hash(p, t[0]) % 2);
				}
			}
		}
		g.flush();
	}

	private void garden(Buffer g, BlockPos w, int x, int z, int seed) {
		g.set(w.getX(), 0, w.getZ(), grass());
		int hsh = hash(x * 31 + seed, z);
		if (hsh % 5 == 0) {
			g.decor(w.getX(), 1, w.getZ(), flower(hsh / 5));
		}
	}

	// ------------------------------------------------------------------ parks

	/** A free parcel inside the campus: a pond with an island, a ring walk, paths from the four sides, trees. */
	private void park(int p) {
		Hex h = CampusGeometry.parcel(p);
		Frame f = new Frame(h.x(), h.z(), Rotation.NONE);
		Buffer g = new Buffer("park_" + p, "park_" + p, f);
		int a = CampusGeometry.AREA;
		for (int lx = -a; lx <= a; lx++) {
			for (int lz = -a; lz <= a; lz++) {
				double r = Math.sqrt(lx * lx + lz * lz);
				int ax = Math.abs(lx);
				int az = Math.abs(lz);
				if (r <= 2.3) {
					g.setL(lx, 0, lz, moss());
				} else if (r <= 8) {
					g.setL(lx, 0, lz, water());
					if (hash(lx + p, lz) % 11 == 0) {
						g.decorL(lx, 1, lz, Blocks.LILY_PAD.defaultBlockState());
					}
				} else if (r <= 9.5) {
					g.setL(lx, 0, lz, Blocks.SAND.defaultBlockState());
				} else if (r <= 12 || (ax <= 1 && r > 12) || (az <= 1 && r > 12)) {
					g.setL(lx, 0, lz, Blocks.PACKED_MUD.defaultBlockState());
				} else if (hash(lx * 7 + p, lz * 13) % 9 == 0) {
					g.setL(lx, 0, lz, grass());
					g.decorL(lx, 1, lz, flower(lx + lz + p));
				}
			}
		}
		BlockPos c = f.pos(0, 0, 0);
		tree(g, c.getX(), c.getZ(), Blocks.CHERRY_LOG, leaves(Blocks.CHERRY_LEAVES), 4);
		int[][] trees = {{17, 8}, {8, 17}, {-17, 8}, {-8, 17}, {17, -8}, {8, -17}, {-17, -8}, {-8, -17}, {19, 19}, {-19, -19}, {19, -19}, {-19, 19}};
		for (int i = 0; i < trees.length; i++) {
			BlockPos t = f.pos(trees[i][0], 0, trees[i][1]);
			Block log = (i + p) % 3 == 0 ? Blocks.BIRCH_LOG : (i + p) % 3 == 1 ? Blocks.OAK_LOG : Blocks.SPRUCE_LOG;
			BlockState crown = (i + p) % 3 == 0 ? leaves(Blocks.BIRCH_LEAVES) : (i + p) % 3 == 1 ? leaves(Blocks.OAK_LEAVES) : leaves(Blocks.SPRUCE_LEAVES);
			tree(g, t.getX(), t.getZ(), log, crown, 4 + (i % 2));
		}
		for (int[] l : new int[][] {{12, 2}, {-12, -2}, {2, -12}, {-2, 12}}) {
			BlockPos q = f.pos(l[0], 0, l[1]);
			lampPost(g, q.getX(), q.getZ());
		}
		g.flush();
	}

	// ------------------------------------------------------------------ the path network

	/**
	 * Paths along the edges of every drawn cell (hub, barrios, parks), a plaza at every vertex they
	 * reach (pavilion plots are the plazas round the hub), spurs from each plot's four gates, ponds
	 * with bridges and pergolas on the north–south edges, trees and lamps along the rest. Each edge
	 * belongs to the first drawn cell that has it (the hub, then parcels in order), so adding a barrio
	 * only adds paths.
	 */
	private void network(List<Integer> present) {
		List<Hex> owners = new ArrayList<>();
		List<String> groups = new ArrayList<>();
		owners.add(CampusGeometry.HUB);
		groups.add("grounds_hub");
		for (int p : present) {
			owners.add(CampusGeometry.parcel(p));
			groups.add("grounds_" + p);
		}
		Set<Edge> seenEdges = new LinkedHashSet<>();
		Set<Vertex> seenVertices = new LinkedHashSet<>();
		for (int i = 0; i < owners.size(); i++) {
			Hex h = owners.get(i);
			Buffer g = new Buffer(groups.get(i), groups.get(i), new Frame(0, 0, Rotation.NONE));
			List<Edge> mine = new ArrayList<>();
			for (Edge e : CampusGeometry.edges(h)) {
				if (seenEdges.add(e)) {
					mine.add(e);
				}
			}
			for (Vertex v : CampusGeometry.vertices(h)) {
				if (seenVertices.add(v) && Pavilion.at(v) == null) {
					plaza(g, v);
				}
			}
			for (Edge e : mine) {
				edgePath(g, e);
			}
			spurs(g, h);
			for (Edge e : mine) {
				edgeDecor(g, e);
			}
			g.flush();
		}
	}

	static boolean pond(Edge e) {
		return e.vertical() && e.hash() % 4 == 0;
	}

	static boolean pergola(Edge e) {
		return e.vertical() && e.hash() % 4 == 1;
	}

	/** True when (x, z) is inside the node square of vertex {@code v} (plaza or pavilion plot). */
	private static boolean inNode(Vertex v, int x, int z) {
		int r = Pavilion.at(v) != null ? CampusGeometry.PAVILION : CampusGeometry.PLAZA;
		return Math.abs(x - v.x()) <= r && Math.abs(z - v.z()) <= r;
	}

	private void plaza(Buffer g, Vertex v) {
		int r = CampusGeometry.PLAZA;
		int kind = hash(v.x(), v.z()) % 3;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int d = Math.max(Math.abs(dx), Math.abs(dz));
				int x = v.x() + dx;
				int z = v.z() + dz;
				if (kind == 0 && d <= 1) {
					g.set(x, 0, z, d == 0 ? CAMPUS.accent() : water());
					continue;
				}
				g.set(x, 0, z, d == r ? CAMPUS.accent() : (dx + dz) % 2 == 0 ? CAMPUS.path() : Blocks.ANDESITE.defaultBlockState());
			}
		}
		if (kind == 0) {
			g.set(v.x(), 1, v.z(), Blocks.PRISMARINE_WALL.defaultBlockState());
			g.set(v.x(), 2, v.z(), Blocks.SEA_LANTERN.defaultBlockState());
		} else if (kind == 1) {
			g.set(v.x(), 0, v.z(), moss());
			tree(g, v.x(), v.z(), Blocks.OAK_LOG, leaves(Blocks.OAK_LEAVES), 4);
		} else {
			lampPost(g, v.x(), v.z());
		}
	}

	private static double segDist(double px, double pz, double ax, double az, double bx, double bz) {
		double vx = bx - ax;
		double vz = bz - az;
		double t = ((px - ax) * vx + (pz - az) * vz) / (vx * vx + vz * vz);
		t = Math.max(0, Math.min(1, t));
		double dx = px - (ax + t * vx);
		double dz = pz - (az + t * vz);
		return Math.sqrt(dx * dx + dz * dz);
	}

	private void edgePath(Buffer g, Edge e) {
		Vertex a = e.a();
		Vertex c = e.b();
		boolean pond = pond(e);
		int x1 = Math.min(a.x(), c.x()) - 2;
		int x2 = Math.max(a.x(), c.x()) + 2;
		int z1 = Math.min(a.z(), c.z()) - 2;
		int z2 = Math.max(a.z(), c.z()) + 2;
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				if (inNode(a, x, z) || inNode(c, x, z)) {
					continue;
				}
				double d = segDist(x, z, a.x(), a.z(), c.x(), c.z());
				if (d <= CampusGeometry.PATH + 0.5) {
					boolean bridge = pond && Math.hypot(x - e.midX(), z - e.midZ()) <= 6.5;
					g.set(x, 0, z, bridge ? Blocks.SPRUCE_PLANKS.defaultBlockState() : d < 0.5 ? CAMPUS.accent() : CAMPUS.path());
				}
			}
		}
	}

	/** The four spurs of a cell: from its gates (N, S, E, W of its square) to the vertex or edge beyond. */
	private void spurs(Buffer g, Hex h) {
		int a = CampusGeometry.AREA;
		int[][] dirs = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}};
		for (int[] dir : dirs) {
			int len = dir[0] == 0 ? 2 * CampusGeometry.VZ : CampusGeometry.VX;
			Vertex end = dir[0] == 0 ? new Vertex(h.x(), h.z() + dir[1] * len) : null;
			for (int s = a + 1; s <= len; s++) {
				for (int w = -CampusGeometry.PATH; w <= CampusGeometry.PATH; w++) {
					int x = h.x() + dir[0] * s + (dir[0] == 0 ? w : 0);
					int z = h.z() + dir[1] * s + (dir[1] == 0 ? w : 0);
					if (end != null && inNode(end, x, z)) {
						continue;
					}
					boolean bridge = false;
					if (dir[0] != 0) {
						Edge side = CampusGeometry.Edge.of(new Vertex(h.x() + dir[0] * CampusGeometry.VX, h.z() - CampusGeometry.VZ),
								new Vertex(h.x() + dir[0] * CampusGeometry.VX, h.z() + CampusGeometry.VZ));
						bridge = pond(side) && Math.hypot(x - side.midX(), z - side.midZ()) <= 6.5;
					}
					g.set(x, 0, z, bridge ? Blocks.SPRUCE_PLANKS.defaultBlockState() : w == 0 ? CAMPUS.accent() : CAMPUS.path());
				}
			}
		}
	}

	private void edgeDecor(Buffer g, Edge e) {
		Vertex a = e.a();
		Vertex c = e.b();
		if (pond(e)) {
			for (int dx = -7; dx <= 7; dx++) {
				for (int dz = -7; dz <= 7; dz++) {
					double r = Math.hypot(dx, dz);
					int x = e.midX() + dx;
					int z = e.midZ() + dz;
					if (r <= 6.5 && g.free(x, 0, z)) {
						g.set(x, 0, z, water());
						if (hash(x, z) % 9 == 0) {
							g.decor(x, 1, z, Blocks.LILY_PAD.defaultBlockState());
						}
					} else if (r > 6.5 && r <= 7.5 && g.free(x, 0, z) && Math.abs(dx) > 1 && Math.abs(dz) > 1) {
						g.set(x, 0, z, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
					}
				}
			}
			// lanterns at the bridge heads
			for (int s = -1; s <= 1; s += 2) {
				int x = e.midX() + 2;
				int z = e.midZ() + 7 * s;
				if (g.free(x, 1, z) && g.free(x, 0, z)) {
					lampPost(g, x, z);
				}
			}
			return;
		}
		if (pergola(e)) {
			int x = e.midX();
			int zA = Math.min(a.z(), c.z()) + CampusGeometry.PLAZA + 2;
			int zB = Math.max(a.z(), c.z()) - CampusGeometry.PLAZA - 2;
			for (int z = zA; z <= zB; z++) {
				if (Math.abs(z - e.midZ()) <= 2) {
					continue;
				}
				boolean post = Math.floorMod(z - zA, 4) == 0;
				for (int dx = -2; dx <= 2; dx++) {
					if (post && Math.abs(dx) == 2) {
						for (int y = 1; y <= 3; y++) {
							g.set(x + dx, y, z, log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.Y));
						}
						g.set(x + dx, 0, z, CAMPUS.footing());
					}
					if (post) {
						g.set(x + dx, 4, z, log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.X));
					} else if (Math.abs(dx) <= 2 && hash(x + dx, z) % 3 != 0) {
						g.set(x + dx, 4, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
					}
				}
			}
			return;
		}
		// trees and lamps along the edge, on alternating sides
		double len = Math.hypot(c.x() - a.x(), c.z() - a.z());
		double ux = (c.x() - a.x()) / len;
		double uz = (c.z() - a.z()) / len;
		double nx = -uz;
		double nz = ux;
		int n = 0;
		for (double t = 9; t <= len - 9; t += 8) {
			int side = n++ % 2 == 0 ? 1 : -1;
			int lx = (int) Math.round(a.x() + ux * t + nx * 3 * side);
			int lz = (int) Math.round(a.z() + uz * t + nz * 3 * side);
			if (decorFree(g, lx, lz, 0)) {
				lampPost(g, lx, lz);
			}
			int tx = (int) Math.round(a.x() + ux * (t + 4) - nx * 6 * side);
			int tz = (int) Math.round(a.z() + uz * (t + 4) - nz * 6 * side);
			if (decorFree(g, tx, tz, 2)) {
				int kind = hash(tx, tz) % 3;
				Block log = kind == 0 ? Blocks.BIRCH_LOG : kind == 1 ? Blocks.OAK_LOG : Blocks.CHERRY_LOG;
				BlockState crown = kind == 0 ? leaves(Blocks.BIRCH_LEAVES) : kind == 1 ? leaves(Blocks.OAK_LEAVES) : leaves(Blocks.CHERRY_LEAVES);
				g.set(tx, 0, tz, grass());
				tree(g, tx, tz, log, crown, 4 + kind % 2);
			}
		}
	}

	/**
	 * Whether decoration of radius {@code r} may stand at (x, z): clear of every plot, every possible
	 * path or spur and every pond (whatever cells exist now, so a later barrio never meets a tree).
	 */
	private boolean decorFree(Buffer g, int x, int z, int r) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (CampusFootprints.reserved(x + dx, z + dz, 1) || !g.free(x + dx, 0, z + dz) || !g.free(x + dx, 1, z + dz)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Every area the campus may ever use for a plot (for {@link CampusFootprints}). */
	static Map<String, Area> plots(int parcels) {
		Map<String, Area> out = new LinkedHashMap<>();
		out.put("hub", CampusGeometry.HUB.area());
		for (Pavilion pv : Pavilion.values()) {
			out.put(group(pv), pv.plot());
		}
		for (int p = 0; p < parcels; p++) {
			out.put("parcel_" + p, CampusGeometry.parcel(p).area());
		}
		return out;
	}
}
