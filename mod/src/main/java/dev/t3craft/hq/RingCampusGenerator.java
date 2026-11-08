package dev.t3craft.hq;

import dev.t3craft.hq.RingCampusGeometry.DeskSpot;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Lot;
import dev.t3craft.hq.RingCampusGeometry.Node;
import dev.t3craft.hq.RingCampusGeometry.Room;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.hq.citadel.CitadelBarrios;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelCanvas.Mats;
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
	static final int MIN_LIGHT = 10;

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
				CitadelBarrios.barrio(canvas, lot, mods);
			}
		}
		canvas.anchor(Anchor.at(AnchorNames.SPAWN, SPAWN, Direction.NORTH));
		return build();
	}

	/** Where NPCs appear: the inner street, south, in front of the main ramp. */
	static final BlockPos SPAWN = new BlockPos(0, 1, 38);

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

	// ------------------------------------------------------------------ groups

	/** The construction group of a cell (see the class comment). */
	String groupOf(int x, int y, int z) {
		Zone zn = canvas.zoneAt(x, z);
		return switch (zn.kind()) {
			case HUB, HUB_WALL -> "hub";
			case BRIDGE, PATIO, GALLERY, RAMP, TERRACE, INNER_STREET -> "cloister";
			case NODE -> zn.node() == 4 ? HqGenerator.GROUP_ARCHIVE : "node_" + zn.node();
			case AVENUE, STREET -> "ring_" + zn.ring();
			case LOT -> {
				TreeMap<Integer, HqLayout.Module> mods = canvas.lots.get(zn.lot());
				if (mods == null) {
					yield "ring_" + zn.ring();
				}
				int[] hall = canvas.hallCols.get(col(x, z));
				if (hall == null) {
					yield "lot_" + zn.lot();
				}
				int f = y >= FH ? 1 : 0;
				HqLayout.Module m = mods.get(f * RingCampusGeometry.HALLS + hall[1]);
				if (m == null) {
					m = mods.get((1 - f) * RingCampusGeometry.HALLS + hall[1]);
				}
				yield HqGenerator.group(m.index());
			}
			case LAWN -> "grounds";
			default -> throw new IllegalStateException("no zone at " + x + "," + z);
		};
	}

	private static int groupRank(String g) {
		if (g.equals("hub")) {
			return 0;
		}
		if (g.equals(HqGenerator.GROUP_ARCHIVE)) {
			return 1;
		}
		if (g.equals("cloister")) {
			return 2;
		}
		if (g.startsWith("node_")) {
			return 3;
		}
		if (g.startsWith("ring_")) {
			return 4;
		}
		if (g.startsWith("lot_")) {
			return 5;
		}
		if (g.startsWith("module_")) {
			return 6;
		}
		return 7;
	}

	private static final Comparator<String> GROUPS = Comparator.comparingInt(RingCampusGenerator::groupRank).thenComparing((a, b) -> {
		if (a.startsWith("module_") && b.startsWith("module_")) {
			return Integer.compare(Integer.parseInt(a.substring(7)), Integer.parseInt(b.substring(7)));
		}
		return a.compareTo(b);
	});

	// ------------------------------------------------------------------ light pass

	private boolean noCollision(long k) {
		BlockState s = canvas.cells.get(k);
		return s == null || s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.of(k)).isEmpty();
	}

	private boolean standable(int x, int y, int z) {
		if (canvas.zoneAt(x, z).kind() == Kind.NONE) {
			return false;
		}
		return noCollision(BlockPos.asLong(x, y, z)) && noCollision(BlockPos.asLong(x, y + 1, z)) && !noCollision(BlockPos.asLong(x, y - 1, z));
	}

	/**
	 * Cells an NPC may stand in, walking from the spawn by one-block steps up or down: a superset of
	 * what the client's navigation reaches (it also lets an NPC "stand" on railings), so lighting
	 * these lights every cell it really walks.
	 */
	private List<BlockPos> walkable() {
		Set<Long> seen = new HashSet<>();
		ArrayDeque<BlockPos> todo = new ArrayDeque<>();
		if (standable(SPAWN.getX(), SPAWN.getY(), SPAWN.getZ())) {
			seen.add(SPAWN.asLong());
			todo.add(SPAWN);
		}
		while (!todo.isEmpty()) {
			BlockPos p = todo.poll();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos n = p.relative(d).above(dy);
					if (n.getY() < canvas.style.bottom() || n.getY() > canvas.style.top() || seen.contains(n.asLong())) {
						continue;
					}
					if (standable(n.getX(), n.getY(), n.getZ())) {
						seen.add(n.asLong());
						todo.add(n);
					}
				}
			}
		}
		List<BlockPos> out = new ArrayList<>();
		for (long k : seen) {
			out.add(BlockPos.of(k));
		}
		out.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).thenComparingInt(p -> p.getZ()).thenComparingInt(p -> p.getX()));
		return out;
	}

	private int lightAt(PlanLight l, BlockPos feet) {
		int v = l.get(feet);
		BlockState below = canvas.cells.get(feet.below().asLong());
		if (below != null && below.getLightDampening() < 15) {
			v = Math.min(v, l.get(feet.below()));
		}
		return v;
	}

	private boolean replaceable(long k, String group, Map<Long, String> groups) {
		BlockState s = canvas.cells.get(k);
		return s != null && !s.isAir() && s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.of(k)) && s.getLightDampening() >= 15
				&& s.getLightEmission() == 0 && !canvas.fixtures.contains(k) && !canvas.data.containsKey(k)
				&& group.equals(groups.get(k));
	}

	/** Where to embed a light for a dark walkable cell: the floor, under a slab, the floor beside it, the ceiling. */
	private @Nullable Long lightSpot(BlockPos feet, String group, Map<Long, String> groups) {
		List<BlockPos> tries = new ArrayList<>();
		tries.add(feet.below());
		tries.add(feet.below(2));
		for (Direction d : Direction.Plane.HORIZONTAL) {
			tries.add(feet.below().relative(d));
		}
		for (BlockPos t : tries) {
			if (replaceable(t.asLong(), group, groups)) {
				return t.asLong();
			}
		}
		for (int y = feet.getY() + 2; y <= feet.getY() + 7; y++) {
			long k = BlockPos.asLong(feet.getX(), y, feet.getZ());
			BlockState s = canvas.cells.get(k);
			if (s != null && !s.isAir()) {
				return replaceable(k, group, groups) ? k : null;
			}
		}
		return null;
	}

	/** Lights every walkable cell (see the class comment) from its own group's lights. */
	private void light(Map<Long, String> groups) {
		TreeMap<String, List<BlockPos>> byGroup = new TreeMap<>();
		for (BlockPos p : walkable()) {
			Kind k = canvas.zoneAt(p.getX(), p.getZ()).kind();
			String g = groups.get(p.below().asLong());
			if (k == Kind.LAWN || g == null) {
				continue;
			}
			byGroup.computeIfAbsent(g, x -> new ArrayList<>()).add(p);
		}
		Map<String, List<Long>> emitters = new HashMap<>();
		for (Map.Entry<Long, BlockState> e : canvas.cells.entrySet()) {
			if (e.getValue().getLightEmission() > 0) {
				emitters.computeIfAbsent(groups.get(e.getKey()), x -> new ArrayList<>()).add(e.getKey());
			}
		}
		for (Map.Entry<String, List<BlockPos>> e : byGroup.entrySet()) {
			String g = e.getKey();
			PlanLight l = new PlanLight(canvas.cells::get);
			for (long k : emitters.getOrDefault(g, List.of())) {
				l.add(k, canvas.cells.get(k).getLightEmission());
			}
			l.run();
			for (BlockPos p : e.getValue()) {
				if (lightAt(l, p) >= MIN_LIGHT) {
					continue;
				}
				Long spot = lightSpot(p, g, groups);
				if (spot != null) {
					canvas.cells.put(spot, LIGHT);
					l.add(spot, LIGHT.getLightEmission());
					l.run();
				}
			}
		}
	}

	// ------------------------------------------------------------------ build

	private BuildPlan build() {
		// clear the air in every column: from its lowest cell up to its highest (and at least 5 above the ground)
		Map<Long, int[]> span = new HashMap<>();
		for (long k : canvas.cells.keySet()) {
			BlockPos p = BlockPos.of(k);
			int[] sp = span.computeIfAbsent(col(p.getX(), p.getZ()), c -> new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE});
			sp[0] = Math.min(sp[0], p.getY());
			sp[1] = Math.max(sp[1], p.getY());
		}
		for (Map.Entry<Long, int[]> e : span.entrySet()) {
			int x = colX(e.getKey());
			int z = colZ(e.getKey());
			Kind kind = canvas.zoneAt(x, z).kind();
			int top = Math.max(e.getValue()[1], kind == Kind.LAWN ? 3 : 5);
			for (int y = e.getValue()[0]; y <= top; y++) {
				long k = BlockPos.asLong(x, y, z);
				if (!canvas.cells.containsKey(k)) {
					canvas.cells.put(k, AIR);
					canvas.layers.put(k, SOLID);
				}
			}
		}
		PlanShapes.resolve(canvas.cells);
		Map<Long, String> groups = new HashMap<>(canvas.cells.size() * 2);
		for (long k : canvas.cells.keySet()) {
			BlockPos p = BlockPos.of(k);
			groups.put(k, groupOf(p.getX(), p.getY(), p.getZ()));
		}
		light(groups);
		record Cell(BlockPos pos, String group, int layer) {
		}
		List<Cell> all = new ArrayList<>(canvas.cells.size());
		Map<BlockPos, String> index = new HashMap<>(canvas.cells.size() * 2);
		for (long k : canvas.cells.keySet()) {
			BlockPos p = BlockPos.of(k);
			String g = groups.get(k);
			all.add(new Cell(p, g, canvas.layers.getOrDefault(k, SOLID)));
			index.put(p, g);
		}
		all.sort(Comparator.<Cell, String>comparing(Cell::group, GROUPS)
				.thenComparingInt(Cell::layer)
				.thenComparingInt(c -> c.layer() == HANGING ? -c.pos().getY() : c.pos().getY())
				.thenComparingInt(c -> c.pos().getZ())
				.thenComparingInt(c -> c.pos().getX()));
		List<Placement> out = new ArrayList<>(all.size());
		for (Cell c : all) {
			long k = c.pos().asLong();
			CompoundTag be = canvas.data.get(k);
			out.add(new Placement(c.pos(), canvas.cells.get(k), be == null ? null : be.copy()));
		}
		// modules: per group, per x, runs of consecutive z
		TreeMap<String, TreeMap<Integer, TreeMap<Integer, int[]>>> byGroup = new TreeMap<>(GROUPS);
		for (Cell c : all) {
			int[] yr = byGroup.computeIfAbsent(c.group(), g -> new TreeMap<>()).computeIfAbsent(c.pos().getX(), x -> new TreeMap<>())
					.computeIfAbsent(c.pos().getZ(), z -> new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE});
			yr[0] = Math.min(yr[0], c.pos().getY());
			yr[1] = Math.max(yr[1], c.pos().getY());
		}
		List<BuildPlan.Module> modules = new ArrayList<>();
		for (var g : byGroup.entrySet()) {
			for (var xe : g.getValue().entrySet()) {
				int x = xe.getKey();
				Integer z0 = null;
				int prev = 0;
				int y1 = 0;
				int y2 = 0;
				for (var ze : xe.getValue().entrySet()) {
					int z = ze.getKey();
					int[] yr = ze.getValue();
					if (z0 != null && z == prev + 1) {
						y1 = Math.min(y1, yr[0]);
						y2 = Math.max(y2, yr[1]);
					} else {
						if (z0 != null) {
							modules.add(new BuildPlan.Module(g.getKey() + "@" + x + "," + z0, Box.of(x, y1, z0, x, y2, prev), List.of(), g.getKey()));
						}
						z0 = z;
						y1 = yr[0];
						y2 = yr[1];
					}
					prev = z;
				}
				if (z0 != null) {
					modules.add(new BuildPlan.Module(g.getKey() + "@" + x + "," + z0, Box.of(x, y1, z0, x, y2, prev), List.of(), g.getKey()));
				}
			}
		}
		canvas.workstations.sort(Comparator.comparingInt(Workstation::module).thenComparingInt(Workstation::slot));
		return new BuildPlan(out, canvas.anchors, modules, canvas.workstations, Collections.unmodifiableMap(index));
	}
}
