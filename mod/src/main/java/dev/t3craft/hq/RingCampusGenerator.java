package dev.t3craft.hq;

import dev.t3craft.hq.RingCampusGeometry.DeskSpot;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Lot;
import dev.t3craft.hq.RingCampusGeometry.Node;
import dev.t3craft.hq.RingCampusGeometry.Room;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelCanvas.Mats;
import dev.t3craft.hq.citadel.CitadelFinish;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;
import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.SOLID;
import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.Y0;
import static dev.t3craft.hq.citadel.CitadelCanvas.FH;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.BAMBOO;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.GLASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.GRASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.MOSS;
import static dev.t3craft.hq.citadel.CitadelCanvas.WATER;
import static dev.t3craft.hq.citadel.CitadelCanvas.RIB;
import static dev.t3craft.hq.citadel.CitadelCanvas.LIGHT;
import static dev.t3craft.hq.citadel.CitadelCanvas.col;
import static dev.t3craft.hq.citadel.CitadelCanvas.colX;
import static dev.t3craft.hq.citadel.CitadelCanvas.colZ;
import static dev.t3craft.hq.citadel.CitadelCanvas.lattice;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;

/**
 * Plan of the hybrid layout {@value RingCampusGeometry#ID} ({@link RingCampusStyle}, geometry in
 * {@link RingCampusGeometry}). Pure and deterministic: {@link HqLayout} in, {@link BuildPlan} out.
 *
 * <p>It paints the ground column by column (each ground column belongs to one
 * {@link RingCampusGeometry.Zone}), then the hub, the core (patio, bridges, elevated promenade,
 * ramps), the node rooms and the barrios; then it <b>lights</b> the plan: every cell an NPC can walk
 * to (approximated from the spawn with one-block steps) must get block light ≥ {@value #MIN_LIGHT}
 * from the lights of its own construction group, and where it does not, the generator embeds a light
 * block in the floor under it (or in the wall or ceiling next to it). Lights only ever come from
 * blocks built into floors, walls, ceilings and pillars: there are no loose lanterns.
 *
 * <p>Groups (the construction units): {@code hub}, {@code cloister} (patio, bridges, promenade,
 * ramps, terraces, inner street), {@code archive} (the archive cloister), {@code node_<k>},
 * {@code ring_<m>} (avenues, street and empty lots of ring m), {@code lot_<id>} (a barrio's
 * courtyard, paths, gardens and gatehouse), {@code module_<index>} (one floor of a hall) and
 * {@code grounds} (the lawn). A hall's cells depend only on its own modules, so adding a project
 * never touches the existing halls, nodes, hub or core.
 */
final class RingCampusGenerator {
	static final int MIN_LIGHT = CitadelFinish.MIN_LIGHT;

	private final CitadelCanvas canvas;

	RingCampusGenerator(RingCampusStyle style) {
		this.canvas = new CitadelCanvas(style, HqKit.active());
	}

	BuildPlan generate(HqLayout layout) {
		canvas.layout = layout;
		List<Integer> idx = new ArrayList<>();
		for (HqLayout.Module m : layout.modules()) {
			int lot = RingCampusGeometry.lotOf(m.index());
			if (lot >= 0) {
				idx.add(m.index());
				canvas.lots.computeIfAbsent(lot, k -> new TreeMap<>()).put(m.index() % RingCampusGeometry.LOT_STRIDE, m);
			}
		}
		canvas.rings = RingCampusGeometry.rings(idx);
		int R = RingCampusGeometry.radius(canvas.rings);
		Map<Long, Zone> zs = new HashMap<>();
		for (int x = -R; x <= R; x++) {
			for (int z = -R; z <= R; z++) {
				Zone zn = RingCampusGeometry.zone(x, z, canvas.rings);
				if (zn.kind() != Kind.NONE) {
					zs.put(col(x, z), zn);
					if (zn.kind() == Kind.LOT) {
						canvas.lotCols.computeIfAbsent(zn.lot(), k -> new ArrayList<>()).add(col(x, z));
					}
				}
			}
		}
		canvas.zones = zs;
		for (Map.Entry<Long, Zone> e : zs.entrySet()) {
			ground(colX(e.getKey()), colZ(e.getKey()), e.getValue());
		}
		hub();
		core();
		for (int k = 0; k < 8; k++) {
			node(RingCampusGeometry.node(k));
		}
		int lotCount = RingCampusGeometry.base(canvas.rings + 1);
		for (int lot = 0; lot < lotCount; lot++) {
			TreeMap<Integer, HqLayout.Module> mods = canvas.lots.get(lot);
			if (mods == null) {
				emptyLot(lot);
			} else {
				barrio(lot, mods);
			}
		}
		canvas.anchor(Anchor.at(AnchorNames.SPAWN, CitadelFinish.SPAWN, Direction.NORTH));
		return CitadelFinish.build(canvas);
	}

	// ------------------------------------------------------------------ ground

	private static int rampHalves(double r) {
		return (int) Math.max(0, Math.min(2 * Y0, Math.round(RingCampusGeometry.RAMP_R - r)));
	}

	private void stepped(int x, int z, int halves, BlockState full, BlockState half) {
		int solidTop = halves / 2;
		canvas.fill(x, 0, solidTop, z, full);
		if (halves % 2 == 1) {
			canvas.set(x, solidTop + 1, z, half);
		}
	}

	private void ground(int x, int z, Zone zn) {
		double r = RingCampusGeometry.r(x, z);
		switch (zn.kind()) {
			case HUB, HUB_WALL, GALLERY -> canvas.set(x, 0, z, FOOT);
			case BRIDGE, PATIO -> patioGround(x, z, r);
			case RAMP -> stepped(x, z, rampHalves(r), BAMBOO, slab(Blocks.BAMBOO_MOSAIC_SLAB));
			case TERRACE -> {
				int h = rampHalves(r);
				stepped(x, z, h - h % 2, FOOT, FOOT);
				int top = h / 2;
				canvas.set(x, top, z, (x * 7 + z * 13 & 7) == 0 ? MOSS : GRASS);
				if (Math.floorMod(x * 31 + z * 17, dev.t3craft.hq.pack.LayoutParams.RINGCAMPUS_TERRACE_BUSH.get()) == 0 && top + 1 < 6) {
					canvas.set(x, top + 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
				}
			}
			case INNER_STREET, STREET -> canvas.set(x, 0, z, lattice(x, z) ? LIGHT : ((int) Math.floor(r) % 3 == 0 ? FOOT : canvas.accent()));
			case AVENUE -> canvas.set(x, 0, z, lattice(x, z) ? LIGHT
					: RingCampusGeometry.radialDistance(x, z, RingCampusGeometry.lotsIn(zn.ring())) > 2.4 ? FOOT : canvas.accent());
			case LOT -> canvas.set(x, 0, z, lattice(x, z) ? LIGHT : GRASS);
			case NODE -> canvas.set(x, 0, z, canvas.floor());
			case LAWN -> canvas.set(x, 0, z, GRASS);
			default -> {
			}
		}
	}

	private void patioGround(int x, int z, double r) {
		if (r > 17.5) {
			canvas.set(x, 0, z, lattice(x, z) ? LIGHT : canvas.accent());
		} else if (r > 15.5 && RingCampusGeometry.radialDistance(x, z, 8) > 1.2) {
			canvas.set(x, -1, z, FOOT);
			canvas.set(x, 0, z, WATER);
		} else if (r > 15.5) {
			canvas.set(x, 0, z, FOOT);
		} else {
			canvas.set(x, 0, z, lattice(x, z) ? LIGHT : (x + z & 3) == 0 ? MOSS : GRASS);
		}
	}

	// ------------------------------------------------------------------ hub: a basilica on the podium

	/** Aisle ceiling (and outer walls' top). */
	private static final int AISLE_TOP = 12;
	/** Nave vault: springs at y = 16 (12 above the floor), crown at y = 21 (17 above). */
	private static final int SPRING = 16;

	private static int vaultY(int ax) {
		if (ax >= 6) {
			return SPRING;
		}
		return SPRING + (int) Math.round(5 * Math.sqrt(1 - (ax / 6.0) * (ax / 6.0)));
	}

	private void hub() {
		for (int x = -14; x <= 14; x++) {
			for (int z = -14; z <= 14; z++) {
				Kind k = canvas.zoneAt(x, z).kind();
				if (k == Kind.HUB) {
					double o = RingCampusGeometry.octagon(x, z);
					canvas.set(x, Y0, z, lattice(x, z) ? LIGHT : (o <= 3 || (int) o % 4 == 0 ? canvas.accent() : canvas.floor()));
					nave(x, z);
				} else if (k == Kind.HUB_WALL) {
					canvas.fill(x, 1, Y0, z, FOOT);
					hubWall(x, z);
				}
			}
		}
		board();
		// benches facing the screens: the kanban wall (west) and the big screen (east)
		for (int sx = -1; sx <= 1; sx += 2) {
			Direction look = sx > 0 ? Direction.EAST : Direction.WEST;
			for (int z = -3; z <= 3; z++) {
				BlockPos p = new BlockPos(7 * sx, Y0 + 1, z);
				canvas.set(p, canvas.style.bench(look.getOpposite()));
				canvas.anchor(Anchor.at(AnchorNames.bench(canvas.nextBench()), p.relative(look), look));
			}
		}
		HqGeometry.Surface screen = new HqGeometry.Surface(new BlockPos(12, Y0 + 2, -3), Direction.SOUTH, Direction.WEST, 7, 4);
		bigScreen(screen, "hub", HqGenerator.hubBinding(canvas.layout), true);
		HqGeometry.Surface wall = new HqGeometry.Surface(new BlockPos(-12, Y0 + 2, 4), Direction.NORTH, Direction.EAST, 9, 4);
		for (BlockPos p : wall.cells()) {
			canvas.fixture(p, canvas.style.screen());
		}
		for (int i = 0; i < wall.width(); i++) {
			canvas.fixture(wall.origin().relative(wall.along(), i).below(), canvas.style.screenBase());
		}
		canvas.anchor(Anchor.face(AnchorNames.THREAD_WALL, wall.origin(), wall.facing()));
		canvas.anchor(Anchor.face(AnchorNames.THREAD_WALL_END, wall.origin().relative(wall.along(), wall.width() - 1).above(wall.height() - 1), wall.facing()));
		canvas.anchor(Anchor.at(AnchorNames.THREAD_WALL_FRONT, new BlockPos(-9, Y0 + 1, 0), Direction.WEST));
		// hologram column free over the centre; the beacon crowns the vault
		for (int y = Y0 + 1; y <= Y0 + 1 + HqGeometry.HOLOGRAM_TOP; y++) {
			canvas.set(0, y, 0, AIR);
		}
		canvas.anchor(Anchor.at(AnchorNames.HUB, new BlockPos(0, Y0 + 1, 0), Direction.SOUTH));
		canvas.anchor(new Anchor(AnchorNames.HOLOGRAM, 0.5, Y0 + 5.0, 0.5, Direction.SOUTH, new BlockPos(0, Y0 + 5, 0)));
		canvas.anchor(Anchor.at(AnchorNames.ENTRANCE, new BlockPos(0, Y0 + 1, 11), Direction.SOUTH));
		BlockPos beacon = new BlockPos(0, vaultY(0) + 1, 0);
		canvas.set(beacon, canvas.style.beacon());
		canvas.fixtures.add(beacon.asLong());
		canvas.anchor(Anchor.at(AnchorNames.BEACON, beacon, Direction.SOUTH));
		for (int wz : new int[] {-4, 4}) {
			for (int sx = -1; sx <= 1; sx += 2) {
				canvas.walkSpot(new BlockPos(4 * sx, Y0 + 1, wz), Direction.SOUTH);
			}
		}
	}

	private static boolean arcadeZ(int z) {
		return Math.floorMod(z + 2, 4) == 0 && Math.abs(z) <= 10;
	}

	/** One interior column of the hub: the nave's vault, the arcades, clerestory and the aisles' ceiling. */
	private void nave(int x, int z) {
		int ax = Math.abs(x);
		if (ax == 6) {
			if (arcadeZ(z)) {
				canvas.fill(x, Y0 + 1, AISLE_TOP - 1, z, LOG);
			}
			canvas.set(x, AISLE_TOP, z, canvas.kit.beam(Direction.Axis.Z));
			for (int y = AISLE_TOP + 1; y < SPRING; y++) {
				canvas.set(x, y, z, arcadeZ(z) ? LOG : (y == AISLE_TOP + 2 ? canvas.kit.wallPanel(true) : GLASS));
			}
			canvas.set(x, SPRING, z, PLANKS);
		} else if (ax > 6) {
			canvas.set(x, AISLE_TOP, z, Math.floorMod(x * 3 + z * 5, 7) == 0 ? LIGHT : FOOT);
		} else {
			int top = vaultY(ax);
			int lo = Math.min(top, vaultY(ax + 1) + 1);
			for (int y = lo; y <= top; y++) {
				BlockState s;
				if (ax == 0 && Math.abs(z) > 1 && Math.floorMod(z, 4) != 0) {
					s = GLASS;
				} else if (Math.floorMod(z, 4) == 0) {
					s = RIB;
				} else {
					s = PLANKS;
				}
				canvas.set(x, y, z, s);
			}
		}
	}

	private void hubWall(int x, int z) {
		boolean naveEnd = Math.abs(x) <= 6 && Math.abs(z) >= 12;
		for (int y = Y0 + 1; y <= AISLE_TOP; y++) {
			BlockState s;
			if (y <= Y0 + 1) {
				s = FOOT;
			} else if (y == AISLE_TOP) {
				s = CALCITE;
			} else if (!naveEnd && y >= Y0 + 3 && y <= Y0 + 6 && Math.floorMod(x + z, 3) != 0) {
				s = GLASS;
			} else if (y == Y0 + 2 && Math.floorMod(x * 5 + z * 3, 6) == 0) {
				s = canvas.kit.wallPanel(true);
			} else {
				s = canvas.kit.wallPanel(false);
			}
			canvas.set(x, y, z, s);
		}
		if (naveEnd) {
			// gable of the nave up to the vault, a rose window in it
			int top = vaultY(Math.abs(x));
			for (int y = AISLE_TOP + 1; y <= top; y++) {
				canvas.set(x, y, z, Math.hypot(x, y - SPRING) <= 3.2 ? GLASS : CALCITE);
			}
		} else {
			canvas.set(x, AISLE_TOP + 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
		// main portal (south, 5 wide x 5 tall) and the north door under the board (3 x 3)
		if (z > 0 && Math.abs(x) <= 2) {
			canvas.fill(x, Y0 + 1, Y0 + 5, z, AIR);
		}
		if (z < 0 && Math.abs(x) <= 1) {
			canvas.fill(x, Y0 + 1, Y0 + 3, z, AIR);
		}
	}

	/** The "needs attention" board at the nave's north end, facing the entrance, with its bulbs and standing rows. */
	private void board() {
		int z = -12;
		for (int x = -5; x <= 5; x++) {
			for (int y = Y0 + 4; y <= Y0 + 10; y++) {
				boolean edge = Math.abs(x) == 5 || y == Y0 + 4 || y == Y0 + 10 || y == Y0 + 9;
				canvas.fixture(new BlockPos(x, y, z), edge ? canvas.kit.beam(Direction.Axis.X) : canvas.style.palette().get(HqStyle.Role.BOARD));
			}
		}
		int lamp = 0;
		for (int x = -4; x <= 4; x += 2) {
			canvas.fixture(new BlockPos(x, Y0 + 9, z), canvas.style.statusLamp());
			canvas.anchor(Anchor.face(AnchorNames.boardLamp(lamp++), new BlockPos(x, Y0 + 9, z), Direction.SOUTH));
		}
		BlockPos sign = new BlockPos(0, Y0 + 10, z + 1);
		canvas.put(sign.getX(), sign.getY(), sign.getZ(), canvas.style.palette().get(HqStyle.Role.PLAQUE).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), DECOR);
		canvas.data.put(sign.asLong(), new HqStyle.SignText(DyeColor.ORANGE, "", "Needs", "attention", "").signTag());
		canvas.fixtures.add(sign.asLong());
		canvas.anchor(Anchor.face(AnchorNames.BOARD, new BlockPos(0, Y0 + 6, z), Direction.SOUTH));
		canvas.anchor(Anchor.at(AnchorNames.BOARD_FRONT, new BlockPos(0, Y0 + 1, z + 1), Direction.NORTH));
		int slot = 0;
		for (int row = 2; row <= 3; row++) {
			for (int i = 0; i <= 8; i++) {
				int x = i == 0 ? 0 : (i % 2 == 1 ? (i + 1) / 2 : -(i / 2));
				canvas.anchor(Anchor.at(AnchorNames.boardSlot(slot++), new BlockPos(x, Y0 + 1, z + row), Direction.NORTH));
			}
		}
	}

	/** A big screen and its anchor; {@code base}: a beam under its bottom row (free-standing screens). */
	private void bigScreen(HqGeometry.Surface s, String key, String binding, boolean base) {
		for (BlockPos p : s.cells()) {
			canvas.fixture(p, canvas.style.screen());
		}
		if (base) {
			for (int i = 0; i < s.width(); i++) {
				canvas.fixture(s.origin().relative(s.along(), i).below(), canvas.style.screenBase());
			}
		}
		canvas.anchor(Anchor.face(AnchorNames.bigScreen(key, s.width(), s.height(), binding), s.origin().above(s.height() - 1), s.facing()));
	}

	// ------------------------------------------------------------------ core: bridges, promenade, patio

	private void core() {
		Set<Long> stairHeads = new HashSet<>();
		// patio stairs: from the promenade down into the patio on the four diagonals
		for (int d = 0; d < 4; d++) {
			double a = Math.PI / 4 + d * Math.PI / 2;
			BlockPos p = RingCampusGeometry.polar(21.2, a, 0);
			int dx = p.getX() > 0 ? -1 : 1;
			Direction up = dx < 0 ? Direction.EAST : Direction.WEST;
			for (int w = -1; w <= 1; w++) {
				for (int i = 0; i < 4; i++) {
					int x = p.getX() + dx * i;
					int z = p.getZ() + w;
					for (int y = 1; y < Y0 - i; y++) {
						canvas.set(x, y, z, FOOT);
					}
					canvas.set(x, Y0 - i, z, stairs(Blocks.BAMBOO_MOSAIC_STAIRS, up));
					for (int y = Y0 - i + 1; y <= Y0 + 3; y++) {
						canvas.set(x, y, z, AIR);
					}
				}
				for (int i = 0; i <= 3; i++) {
					stairHeads.add(col(p.getX() - dx * i, p.getZ() + w));
				}
			}
		}
		List<Long> keys = new ArrayList<>(canvas.zones.keySet());
		Collections.sort(keys);
		for (long key : keys) {
			Kind k = canvas.zones.get(key).kind();
			int x = colX(key);
			int z = colZ(key);
			double r = RingCampusGeometry.r(x, z);
			if (k == Kind.BRIDGE) {
				bridge(x, z);
			} else if (k == Kind.GALLERY) {
				promenade(x, z, r, stairHeads.contains(key));
			} else if (k == Kind.PATIO) {
				patio(x, z, r);
			}
		}
		canvas.walkSpot(new BlockPos(19, 1, 0), Direction.WEST);
		canvas.walkSpot(new BlockPos(-19, 1, 0), Direction.EAST);
		canvas.walkSpot(new BlockPos(0, Y0 + 1, -24), Direction.NORTH);
		canvas.walkSpot(new BlockPos(24, Y0 + 1, 0), Direction.EAST);
	}

	private void bridge(int x, int z) {
		if (canvas.at(x, Y0, z) != null) {
			return; // patio stairs
		}
		canvas.set(x, Y0, z, Math.abs(x) <= 2 ? (lattice(x, z) ? LIGHT : canvas.accent()) : FOOT);
		if (Math.abs(x) == 3) {
			canvas.set(x, Y0 + 1, z, canvas.kit.railing(x > 0 ? Direction.EAST : Direction.WEST));
		}
		// arches under the deck: a lintel course and piers
		canvas.set(x, Y0 - 1, z, Math.abs(z) % 4 == 1 || Math.abs(x) == 3 ? FOOT : AIR);
		if (Math.abs(z) % 4 == 1 && Math.abs(x) == 3) {
			canvas.fill(x, 1, Y0 - 2, z, FOOT);
		}
	}

	/**
	 * The elevated ring: a deck at the podium level over a solid plinth, a railing on the patio side,
	 * light posts (log and a light block) round the outer edge between the ramps.
	 */
	private void promenade(int x, int z, double r, boolean stairHead) {
		if (canvas.at(x, Y0, z) != null) {
			return; // the top step of a patio stair
		}
		canvas.fill(x, 1, Y0 - 1, z, r <= RingCampusGeometry.PATIO_R + 1.2 && Math.floorMod(x * 3 + z * 7, 9) == 0 ? canvas.kit.wallPanel(true) : FOOT);
		canvas.set(x, Y0, z, lattice(x, z) ? LIGHT : (r > RingCampusGeometry.GALLERY_R - 1 ? FOOT : canvas.floor()));
		boolean inner = r <= RingCampusGeometry.PATIO_R + 1.2;
		if (inner && Math.abs(x) > 3 && !stairHead) {
			Direction edge = RingCampusGeometry.cardinal(RingCampusGeometry.theta(x, z)).getOpposite();
			canvas.set(x, Y0 + 1, z, canvas.kit.railing(edge));
		}
		if (r > RingCampusGeometry.GALLERY_R - 1 && RingCampusGeometry.radialDistance(x, z, 16) < 0.5
				&& RingCampusGeometry.radialDistance(x, z, 8) > 5) {
			canvas.fill(x, Y0 + 1, Y0 + 2, z, LOG);
			canvas.set(x, Y0 + 3, z, LIGHT);
		}
	}

	private void patio(int x, int z, double r) {
		if (canvas.at(x, 1, z) != null) {
			return; // stairs
		}
		for (int k = 0; k < 8; k++) {
			double a = (k + 0.5) * Math.PI / 4;
			BlockPos t = RingCampusGeometry.polar(19.3, a, 0);
			int dx = x - t.getX();
			int dz = z - t.getZ();
			if (dx == 0 && dz == 0) {
				canvas.fill(x, 1, 4, z, LOG);
				canvas.set(x, 5, z, leaves(Blocks.CHERRY_LEAVES));
				canvas.set(x, 6, z, leaves(Blocks.CHERRY_LEAVES));
			} else if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2 && Math.abs(dx) + Math.abs(dz) <= 3) {
				canvas.set(x, 5, z, leaves(Blocks.CHERRY_LEAVES));
				if (Math.abs(dx) + Math.abs(dz) <= 1) {
					canvas.set(x, 6, z, leaves(Blocks.CHERRY_LEAVES));
				}
			}
		}
		if (r <= 15.5 && Math.floorMod(x * 5 + z * 3, 11) == 0 && canvas.at(x, 0, z) != LIGHT) {
			canvas.set(x, 1, z, Math.floorMod(x + z, 2) == 0 ? b(Blocks.FLOWERING_AZALEA) : canvas.kit.gardenPlanter());
		}
	}

	// ------------------------------------------------------------------ nodes: the special rooms

	private void node(Node nd) {
		int R = 8;
		for (int x = nd.cx() - R; x <= nd.cx() + R; x++) {
			for (int z = nd.cz() - R; z <= nd.cz() + R; z++) {
				Zone zn = canvas.zoneAt(x, z);
				if (zn.kind() == Kind.NODE && zn.node() == nd.k()) {
					nodeCell(nd, x, z);
				}
			}
		}
		int cx = nd.cx();
		int cz = nd.cz();
		switch (nd.kind()) {
			case REVIEW_TOWER -> {
				bigScreen(new HqGeometry.Surface(new BlockPos(cx - 3, 5, cz - 5), Direction.EAST, Direction.SOUTH, 7, 4), "review", "", false);
				canvas.walkSpot(new BlockPos(cx, 1, cz - 2), Direction.NORTH);
			}
			case SCHEDULE_ROTUNDA -> {
				bigScreen(new HqGeometry.Surface(new BlockPos(cx + 4, 2, cz - 2), Direction.SOUTH, Direction.WEST, 5, 3), "schedule", "", true);
				canvas.walkSpot(new BlockPos(cx - 1, 1, cz), Direction.EAST);
			}
			case ARCHIVE_CLOISTER -> {
				BlockPos t = new BlockPos(cx + 3, 1, cz - 6);
				canvas.fixture(t, canvas.style.archiveTerminal(Direction.SOUTH));
				canvas.anchor(Anchor.face(AnchorNames.ARCHIVE_TERMINAL, t, Direction.SOUTH));
				canvas.anchor(Anchor.at(AnchorNames.ARCHIVE, t.south(), Direction.NORTH));
				canvas.walkSpot(new BlockPos(cx - 4, 1, cz), Direction.EAST);
			}
			case LOUNGE_SUNKEN -> canvas.walkSpot(new BlockPos(cx + 1, -2, cz), Direction.WEST);
			default -> {
			}
		}
	}

	private void nodeCell(Node nd, int x, int z) {
		int dx = x - nd.cx();
		int dz = z - nd.cz();
		double d = Math.hypot(dx, dz);
		switch (nd.kind()) {
			case REVIEW_TOWER -> tower(x, z, dx, dz);
			case SCHEDULE_ROTUNDA -> rotunda(x, z, dx, dz, d);
			case ARCHIVE_CLOISTER -> cloister(x, z, dx, dz);
			case LOUNGE_SUNKEN -> sunken(x, z, dx, dz, d);
			case PILLAR -> pillar(x, z, d);
		}
	}

	private static boolean door(int dx, int dz, int edge) {
		return Math.abs(dx) <= 1 && Math.abs(dz) >= edge || Math.abs(dz) <= 1 && Math.abs(dx) >= edge;
	}

	/** Review tower: square, 13 × 13, 20 high, an inner mezzanine band, a pyramid roof and a light on its spire. */
	private void tower(int x, int z, int dx, int dz) {
		int m = Math.max(Math.abs(dx), Math.abs(dz));
		canvas.set(x, 0, z, m <= 2 ? canvas.accent() : canvas.floor());
		int top = 20;
		if (m == 6) {
			boolean corner = Math.abs(dx) == 6 && Math.abs(dz) == 6 || Math.abs(dx) == 3 || Math.abs(dz) == 3;
			for (int y = 1; y <= top; y++) {
				BlockState s = corner ? LOG : (y % 5 == 0 ? canvas.kit.wallPanel(true) : (y >= 3 && Math.floorMod(dx + dz, 2) == 0 ? GLASS : CALCITE));
				canvas.set(x, y, z, s);
			}
			if (door(dx, dz, 6)) {
				canvas.fill(x, 1, 4, z, AIR);
			}
		}
		if (m == 5) {
			canvas.set(x, 10, z, FOOT);
		}
		int lvl = top + 1 + (6 - m);
		for (int y = top + 1; y <= lvl; y++) {
			canvas.set(x, y, z, y == lvl ? RIB : AIR);
		}
		if (m == 0) {
			canvas.set(x, lvl + 1, z, LOG);
			canvas.set(x, lvl + 2, z, LIGHT);
		}
	}

	/** Scheduled-tasks rotunda: a round hall (radius 7) under a dome, a clock ring of lights set in the floor. */
	private void rotunda(int x, int z, int dx, int dz, double d) {
		boolean clock = Math.abs(d - 5) < 0.5 && Math.floorMod(dx * 7 + dz * 3, 4) == 0;
		canvas.set(x, 0, z, clock ? LIGHT : Math.abs(d - 4) < 0.5 ? canvas.accent() : canvas.floor());
		int wallTop = 8;
		if (d > 6) {
			for (int y = 1; y <= wallTop; y++) {
				canvas.set(x, y, z, y == wallTop ? canvas.kit.wallPanel(true) : (y >= 3 && y <= 6 && Math.floorMod(dx * 3 + dz, 3) == 0 ? GLASS : FOOT));
			}
			if (door(dx, dz, 6)) {
				canvas.fill(x, 1, 4, z, AIR);
			}
		}
		double R = 7.2;
		int top = wallTop + (int) Math.round(Math.sqrt(Math.max(0, R * R - d * d)));
		double dn = Math.min(R, d + 1);
		int low = wallTop + (int) Math.round(Math.sqrt(Math.max(0, R * R - dn * dn))) + 1;
		for (int y = Math.max(wallTop + 1, Math.min(low, top)); y <= top; y++) {
			canvas.set(x, y, z, d < 1 ? GLASS : PLANKS);
		}
		if (d < 0.5) {
			canvas.set(x, top + 1, z, LIGHT);
		}
	}

	/**
	 * Archive cloister (after design/mediterranean): a square of 15 × 15, white walls with four gates,
	 * an arcaded gallery round a garden court with a pool; the archive terminal stands in the north
	 * gallery.
	 */
	private void cloister(int x, int z, int dx, int dz) {
		int mx = Math.max(Math.abs(dx), Math.abs(dz));
		int mn = Math.min(Math.abs(dx), Math.abs(dz));
		if (mx == 0) {
			canvas.set(x, -1, z, FOOT);
			canvas.set(x, 0, z, WATER);
		} else if (mx <= 2) {
			canvas.set(x, 0, z, mx == 1 ? canvas.accent() : GRASS);
			if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
				canvas.set(x, 1, z, canvas.kit.gardenPlanter());
			}
		} else if (mx == 3) {
			canvas.set(x, 0, z, canvas.accent());
		} else if (mx <= 6) {
			canvas.set(x, 0, z, canvas.floor());
		} else {
			canvas.set(x, 0, z, FOOT);
		}
		if (mx == 7) {
			for (int y = 1; y <= 5; y++) {
				canvas.set(x, y, z, y == 3 && mn % 4 == 2 ? canvas.kit.wallPanel(true) : CALCITE);
			}
			canvas.set(x, 6, z, PLANKS);
			canvas.set(x, 7, z, slab(Blocks.BRICK_SLAB));
			if (mn <= 1) {
				canvas.fill(x, 1, 3, z, AIR);
			}
		} else if (mx >= 3) {
			if (mx == 3 && mn >= 2) {
				canvas.fill(x, 1, 4, z, LOG);
			}
			canvas.set(x, 5, z, PLANKS);
			canvas.set(x, 6, z, slab(Blocks.BRICK_SLAB));
		}
	}

	/** Lounge: a sunken patio, a round amphitheatre of half steps down to y = -3 round a light column. */
	private void sunken(int x, int z, int dx, int dz, double d) {
		int k = (int) Math.max(0, Math.min(6, Math.round(7 - d)));
		long g = BlockPos.asLong(x, 0, z);
		canvas.cells.remove(g);
		canvas.layers.remove(g);
		int solid = k % 2 == 0 ? -k / 2 : -(k + 1) / 2;
		canvas.set(x, solid, z, k % 2 == 0 ? (k == 6 ? canvas.accent() : MOSS) : FOOT);
		if (k % 2 == 1) {
			canvas.set(x, solid + 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
		for (int y = solid + (k % 2 == 1 ? 2 : 1); y <= 2; y++) {
			canvas.set(x, y, z, AIR);
		}
		if (dx == 0 && dz == 0) {
			canvas.set(x, -2, z, LOG);
			canvas.set(x, -1, z, LOG);
			canvas.set(x, 0, z, LIGHT);
		}
		if (d > 6.2 && Math.abs(Math.abs(dx) - Math.abs(dz)) <= 1 && Math.abs(dx) >= 4) {
			canvas.set(x, 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			canvas.set(x, 2, z, leaves(Blocks.AZALEA_LEAVES));
		}
	}

	/** Diagonal nodes: a light pillar in a paved round. */
	private void pillar(int x, int z, double d) {
		canvas.set(x, 0, z, canvas.accent());
		if (d < 0.5) {
			canvas.fill(x, 1, 4, z, LOG);
			canvas.set(x, 5, z, LIGHT);
		}
	}

	// ------------------------------------------------------------------ barrios

	private void emptyLot(int lot) {
		Lot l = RingCampusGeometry.lot(lot);
		for (long c : canvas.lotCols.getOrDefault(lot, List.of())) {
			int x = colX(c);
			int z = colZ(c);
			if (Math.floorMod(x * 7 + z * 11, 29) == 0 && !lattice(x, z)) {
				canvas.set(x, 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			}
		}
		tree(l.centre().getX(), l.centre().getZ());
	}

	/** A cherry tree (trunk 4, canopy from y = 4) on a ground cell. */
	private void tree(int x, int z) {
		canvas.fill(x, 1, 4, z, LOG);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int m = Math.abs(dx) + Math.abs(dz);
				if (m == 0 || m > 2) {
					continue;
				}
				if (canvas.at(x + dx, 1, z + dz) != null && canvas.at(x + dx, 1, z + dz) != AIR) {
					continue; // a bush underneath: keep the canopy off it
				}
				canvas.set(x + dx, 4, z + dz, leaves(Blocks.CHERRY_LEAVES));
				if (m == 1) {
					canvas.set(x + dx, 5, z + dz, leaves(Blocks.CHERRY_LEAVES));
				}
			}
		}
		canvas.set(x, 5, z, leaves(Blocks.CHERRY_LEAVES));
		canvas.set(x, 6, z, leaves(Blocks.CHERRY_LEAVES));
	}

	private void barrio(int lot, TreeMap<Integer, HqLayout.Module> mods) {
		Lot l = RingCampusGeometry.lot(lot);
		HqLayout.Module lead = mods.firstEntry().getValue();
		DyeColor env = HqStyle.envColor(lead);
		Mats mats = canvas.mats(lot, env);
		int g = l.court();
		int reach = l.reach();
		for (long c : canvas.lotCols.getOrDefault(lot, List.of())) {
			int x = colX(c);
			int z = colZ(c);
			int a = Math.abs(l.a(x, z));
			int bb = Math.abs(l.b(x, z));
			if (a <= g && bb <= g) {
				canvas.set(x, 0, z, lattice(x, z) ? LIGHT : (a == g || bb == g ? canvas.accent() : canvas.floor()));
			} else if (a <= reach && bb <= reach && (a <= g + 1 || bb <= g + 1)) {
				canvas.set(x, 0, z, lattice(x, z) ? LIGHT : canvas.accent());
			} else if (a <= reach && bb <= reach) {
				if (Math.floorMod(a * 3 + bb * 5, 7) == 0 && !lattice(x, z)) {
					canvas.set(x, 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
				}
			} else if (Math.floorMod(x * 7 + z * 11, 29) == 0 && !lattice(x, z)) {
				canvas.set(x, 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			}
		}
		// the courtyard's centre: a cherry tree or a pool with planters
		BlockPos c = l.centre();
		if (lot % 2 == 0) {
			tree(c.getX(), c.getZ());
		} else {
			canvas.set(c.getX(), -1, c.getZ(), FOOT);
			canvas.set(c.getX(), 0, c.getZ(), WATER);
			for (int sa = -1; sa <= 1; sa += 2) {
				for (int sb = -1; sb <= 1; sb += 2) {
					canvas.set(l.at(sa, sb, 1), canvas.kit.gardenPlanter());
				}
			}
		}
		for (int h = 0; h < RingCampusGeometry.HALLS; h++) {
			HqLayout.Module ground = mods.get(h);
			HqLayout.Module upper = mods.get(h + RingCampusGeometry.HALLS);
			if (ground != null || upper != null) {
				hall(l, h, ground, upper, mats);
			} else {
				slotGarden(l.hall(h));
			}
		}
		gatehouse(l, lead, mats);
		HqLayout.Module first = canvas.layout.modulesOf(lead.projectKey()).get(0);
		Direction in = l.out();
		canvas.anchor(Anchor.at(AnchorNames.wing(first.projectKey()), l.at(-(g - 1), 0, 1), in));
		canvas.anchor(Anchor.at(AnchorNames.walk(first.projectKey()), l.at(2, 2, 1), in));
	}

	/** An empty hall slot: a little garden with a tree. */
	private void slotGarden(Room r) {
		for (int u = -1; u <= r.d(); u++) {
			for (int v = -1; v <= r.w(); v++) {
				BlockPos p = r.cell(u, v, 0);
				canvas.set(p, lattice(p.getX(), p.getZ()) ? LIGHT : ((u + v) % 5 == 0 ? MOSS : GRASS));
				if (r.corner(u, v) && !lattice(p.getX(), p.getZ())) {
					canvas.set(p.above(), leaves(Blocks.FLOWERING_AZALEA_LEAVES));
				}
			}
		}
		BlockPos t = r.cell(r.d() / 2, r.c(), 0);
		tree(t.getX(), t.getZ());
	}

	/**
	 * A work hall of one or two floors: walls with windows, the door on the courtyard, 6 standing
	 * desks per floor with their 3×2 screens, straight stairs up the centre line, one of three roofs.
	 */
	private void hall(Lot l, int h, HqLayout.@Nullable Module ground, HqLayout.@Nullable Module upper, Mats mats) {
		Room r = l.hall(h);
		int W = r.w();
		int D = r.d();
		int c = r.c();
		int F = upper != null ? 2 : 1;
		int top = F * FH;
		Set<Long> hole = new HashSet<>();
		if (F == 2) {
			for (int u = 2; u <= 5; u++) {
				hole.add(r.cell(u, c, FH).asLong());
			}
		}
		for (int u = -1; u <= D; u++) {
			for (int v = -1; v <= W; v++) {
				BlockPos g0 = r.cell(u, v, 0);
				canvas.hallCols.put(col(g0.getX(), g0.getZ()), new int[] {l.id(), h});
				boolean wall = r.wall(u, v);
				boolean corner = r.corner(u, v);
				canvas.set(g0, wall ? FOOT : (lattice(g0.getX(), g0.getZ()) ? LIGHT : canvas.floor()));
				Direction.Axis along = (u == -1 || u == D) ? r.side().getAxis() : r.back().getAxis();
				for (int f = 0; f < F; f++) {
					int yb = f * FH;
					if (wall) {
						int t = (u == -1 || u == D) ? v : u;
						for (int y = yb + 1; y < yb + FH; y++) {
							int ly = y - yb;
							BlockState s;
							if (corner) {
								s = mats.post();
							} else if (ly >= 2 && ly <= 4 && Math.floorMod(t, 3) != 0) {
								s = GLASS;
							} else {
								s = mats.wall();
							}
							canvas.set(g0.atY(y), s);
						}
						if (f == 0 && u == -1 && Math.abs(v - c) <= 1) {
							canvas.fill(g0.getX(), 1, 3, g0.getZ(), AIR);
						}
						canvas.set(g0.atY(yb + FH), corner ? mats.post() : (mats.band() == mats.post() ? canvas.kit.beam(along) : mats.band()));
					} else {
						for (int y = yb + 1; y < yb + FH; y++) {
							canvas.set(g0.atY(y), AIR);
						}
						BlockPos ceil = g0.atY(yb + FH);
						if (f < F - 1) {
							canvas.set(ceil, hole.contains(ceil.asLong()) ? AIR : (lattice(g0.getX(), g0.getZ()) ? LIGHT : canvas.floor()));
						} else {
							canvas.set(ceil, (u % 4 == 1 && v % 4 == 1) ? LIGHT : mats.ceiling());
						}
					}
				}
			}
		}
		roof(r, top, Math.floorMod(h + l.id(), 3), mats);
		if (F == 2) {
			for (int i = 0; i < RingCampusGeometry.STAIR_STEPS; i++) {
				BlockPos p = r.cell(1 + i, c, 1 + i);
				for (int y = 1; y <= i; y++) {
					canvas.set(p.atY(y), PLANKS);
				}
				canvas.set(p, stairs(Blocks.CHERRY_STAIRS, r.back()));
			}
		}
		for (HqLayout.Module mod : new HqLayout.Module[] {ground, upper}) {
			if (mod == null) {
				continue;
			}
			int f = RingCampusGeometry.floorOf(mod.index());
			int y = f * FH + 1;
			for (int slot = 0; slot < mod.capacity(); slot++) {
				String thread = mod.threadAt(slot);
				if (thread != null) {
					desk(mod.index(), r, f, slot, thread);
				}
			}
			BlockPos door = f == 0 ? r.cell(0, c - 1, y) : r.cell(7, c, y);
			BlockPos inside = f == 0 ? r.cell(2, c - 1, y) : r.cell(D - 2, c, y);
			canvas.anchor(Anchor.at(AnchorNames.module(mod.index()), door, r.back()));
			canvas.anchor(Anchor.at(AnchorNames.walkModule(mod.index()), inside, r.back()));
		}
	}

	/** Roofs: 0 gable (ridge along the depth), 1 hipped, 2 flat green roof behind a parapet. */
	private void roof(Room r, int top, int type, Mats mats) {
		int W = r.w();
		int D = r.d();
		int c = r.c();
		for (int u = -1; u <= D; u++) {
			for (int v = -1; v <= W; v++) {
				BlockPos p = r.cell(u, v, 0);
				if (type == 2) {
					if (r.wall(u, v)) {
						canvas.set(p.atY(top + 1), mats.wall());
						canvas.set(p.atY(top + 2), slab(Blocks.DEEPSLATE_TILE_SLAB));
					} else {
						canvas.set(p.atY(top + 1), Math.floorMod(u * 3 + v * 5, 7) == 0 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : b(Blocks.MOSS_CARPET));
					}
					continue;
				}
				int dv1 = v + 1;
				int dv2 = W - v;
				int du1 = u + 1;
				int du2 = D - u;
				int k = type == 0 ? Math.min(dv1, dv2) : Math.min(Math.min(dv1, dv2), Math.min(du1, du2));
				int y = top + Math.max(1, k);
				boolean ridge = type == 0 ? v == c : v == c && Math.min(du1, du2) > c + 1;
				BlockState s;
				if (ridge) {
					s = mats.ridge();
				} else {
					Direction facing;
					if (k == dv1) {
						facing = r.side();
					} else if (k == dv2) {
						facing = r.side().getOpposite();
					} else if (k == du1) {
						facing = r.back();
					} else {
						facing = r.back().getOpposite();
					}
					s = stairs(mats.roof(), facing);
				}
				canvas.set(p.atY(y), s);
				if (type == 0 && (u == -1 || u == D)) {
					for (int yy = top + 1; yy < y; yy++) {
						canvas.set(p.atY(yy), mats.wall());
					}
				}
			}
		}
	}

	/** The gatehouse on the hub side: an arched passage into the courtyard, the project's sign under the arch. */
	private void gatehouse(Lot l, HqLayout.Module lead, Mats mats) {
		Room r = l.gate();
		int W = r.w();
		int D = r.d();
		int c = r.c();
		for (int u = 0; u < D; u++) {
			for (int v = 0; v < W; v++) {
				BlockPos p = r.cell(u, v, 0);
				boolean passage = Math.abs(v - c) <= 1;
				canvas.set(p, passage ? canvas.accent() : FOOT);
				if (passage) {
					for (int y = 1; y <= 4; y++) {
						canvas.set(p.atY(y), AIR);
					}
					canvas.set(p.atY(5), canvas.kit.beam(r.back().getAxis()));
				} else {
					boolean corner = (u == 0 || u == D - 1) && (v == 0 || v == W - 1);
					for (int y = 1; y <= 5; y++) {
						canvas.set(p.atY(y), corner ? mats.post() : (y == 3 && u == D / 2 && (v == 0 || v == W - 1) ? canvas.kit.wallPanel(true) : mats.wall()));
					}
				}
				canvas.set(p.atY(6), mats.ridge());
				int k = Math.min(Math.min(u + 1, D - u), Math.min(v + 1, W - v));
				int kmax = Math.min((D + 1) / 2, (W + 1) / 2);
				BlockState s;
				if (k == kmax) {
					s = mats.ridge();
				} else if (k == u + 1) {
					s = stairs(mats.roof(), r.back());
				} else if (k == D - u) {
					s = stairs(mats.roof(), r.back().getOpposite());
				} else if (k == v + 1) {
					s = stairs(mats.roof(), r.side());
				} else {
					s = stairs(mats.roof(), r.side().getOpposite());
				}
				canvas.set(p.atY(6 + k), s);
			}
		}
		BlockPos sign = r.cell(D - 1, c, 4);
		BlockState hs = Blocks.CHERRY_HANGING_SIGN.defaultBlockState().setValue(CeilingHangingSignBlock.ROTATION, rotation(r.back()))
				.setValue(CeilingHangingSignBlock.ATTACHED, false);
		canvas.put(sign.getX(), sign.getY(), sign.getZ(), hs, HANGING);
		canvas.data.put(sign.asLong(), barrioSign(lead).signTag());
		canvas.fixtures.add(sign.asLong());
	}

	private static int rotation(Direction facing) {
		return switch (facing) {
			case SOUTH -> 0;
			case WEST -> 4;
			case NORTH -> 8;
			default -> 12;
		};
	}

	private HqStyle.SignText barrioSign(HqLayout.Module w) {
		String label = w.label();
		String l1 = label.length() <= SignTags.LINE ? label : label.substring(0, SignTags.LINE);
		String l2 = label.length() <= SignTags.LINE ? "" : label.substring(SignTags.LINE);
		int desks = 0;
		for (HqLayout.Module m : canvas.layout.modulesOf(w.projectKey())) {
			desks += m.desks().size();
		}
		return new HqStyle.SignText(HqStyle.envColor(w), l1, l2, desks + (desks == 1 ? " desk" : " desks"), w.envLabel());
	}

	private void desk(int module, Room r, int f, int slot, String thread) {
		DeskSpot d = RingCampusGeometry.desk(r, f, slot);
		Direction face = d.facing();
		Direction right = face.getClockWise();
		BlockPos desk = d.desk();
		for (int k = -1; k <= 1; k++) {
			canvas.fixture(desk.relative(right, k), canvas.kit.desk(face.getOpposite()));
		}
		BlockPos monitor = desk.above();
		Direction screenFacing = face.getOpposite();
		for (BlockPos p : Workstation.screenCells(Workstation.screenOriginFor(monitor, screenFacing), screenFacing.getCounterClockWise())) {
			canvas.fixture(p, canvas.style.computer(true));
		}
		BlockPos stand = d.stand();
		canvas.set(stand, AIR);
		canvas.set(stand.above(), AIR);
		Anchor deskA = Anchor.at(AnchorNames.deskSlot(module, slot), stand, face);
		Anchor screen = Anchor.face(AnchorNames.screenSlot(module, slot), monitor, screenFacing);
		canvas.workstations.add(new Workstation(module, slot, thread, deskA, monitor, screen, Workstation.screenOriginFor(monitor, screenFacing)));
		canvas.anchor(deskA.renamed(AnchorNames.desk(thread)));
		canvas.anchor(screen.renamed(AnchorNames.monitor(thread)));
	}
}
