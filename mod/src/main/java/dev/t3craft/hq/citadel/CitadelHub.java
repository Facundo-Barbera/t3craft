package dev.t3craft.hq.citadel;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqGeometry;
import dev.t3craft.hq.HqStyle;
import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.GLASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.LIGHT;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.RIB;
import static dev.t3craft.hq.citadel.CitadelCanvas.Y0;
import static dev.t3craft.hq.citadel.CitadelCanvas.col;
import static dev.t3craft.hq.citadel.CitadelCanvas.colX;
import static dev.t3craft.hq.citadel.CitadelCanvas.colZ;
import static dev.t3craft.hq.citadel.CitadelCanvas.lattice;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

/**
 * The hub of the citadel: a basilica on the podium ({@code floor y = Y0}), and the core ring round it.
 * Static pieces taking the {@link CitadelCanvas} first; call order matters (see {@link #hub} and
 * {@link #core}).
 *
 * <h2>Pieces</h2>
 *
 * <ul>
 * <li>{@link #hub(CitadelCanvas)}: the whole basilica. In order: {@link #shell} (every column of the
 *     octagon: floor, then {@link #nave} for {@link Kind#HUB} or {@link #hubWall} for
 *     {@link Kind#HUB_WALL}), {@link #board}, {@link #benches}, {@link #screens}, {@link #hologramAndBeacon}
 *     and {@link #walkSpots}.</li>
 * <li>{@link #nave(CitadelCanvas, int, int)}: one interior column (x, z), split by its distance
 *     {@code ax = |x|} from the axis into {@link #naveArcade} ({@code ax == 6}: log arcades, clerestory,
 *     {@code PLANKS} spring), {@link #naveAisle} ({@code ax > 6}: aisle ceiling, light blocks on a
 *     7-cell lattice) and {@link #naveVault} ({@code ax < 6}: the barrel vault of planks with ribs
 *     every 4 z and a glass skylight on the axis).</li>
 * <li>{@link #hubWall(CitadelCanvas, int, int)}: one column of the outer wall ({@link #hubWallColumn}:
 *     footing, panels, glass band, calcite cornice), then {@link #naveGable} (the nave's end walls
 *     with a rose window) or {@link #aisleCap}, then {@link #portals} (south portal 5x5, north door
 *     3x3 under the board).</li>
 * <li>{@link #board(CitadelCanvas)}: the "needs attention" board at the nave's north end (z = -12).</li>
 * <li>{@link #bigScreen(CitadelCanvas, HqGeometry.Surface, String, String, boolean)}: a big screen and
 *     its anchor; shared with the nodes (review tower, schedule rotunda).</li>
 * <li>{@link #core(CitadelCanvas)}: stairs of the patio, then the bridges, the promenade and the patio
 *     (delegating to {@code CitadelGrounds.bridge/promenade/patio}, column by column in sorted
 *     order), then four walk spots.</li>
 * <li>{@link #vaultY(int)} (height of the vault above column {@code ax}) and {@link #arcadeZ(int)} (is z
 *     an arcade line) are the shared profile functions.</li>
 * </ul>
 *
 * <h2>Parameters</h2>
 *
 * Fixed geometry, no inputs beyond the canvas: the hub spans x, z in [-14, 14] (the zone map decides
 * which columns are {@code HUB} / {@code HUB_WALL}); {@link #AISLE_TOP} = 12 (aisle ceiling and outer
 * wall top), {@link #SPRING} = 16 (the vault springs, crown at {@code SPRING + 5}). The big screen of
 * the east side is a 7x4 {@link HqGeometry.Surface} at (12, Y0+2, -3) bound to
 * {@code HqGenerator.hubBinding(layout)}; the thread wall (west) is 9x4 at (-12, Y0+2, 4).
 *
 * <h2>Blocks and roles</h2>
 *
 * Palette: {@code FOOT} (deepslate tiles), {@code CALCITE}, {@code LOG}, {@code PLANKS}, {@code GLASS},
 * {@code RIB}, {@code LIGHT}, deepslate tile slab, {@code accent()}/{@code floor()} of the kit.
 * Kit: {@code beam}, {@code wallPanel(lit)}. Style roles: {@code screen()}, {@code screenBase()},
 * {@code statusLamp()}, {@code beacon()}, {@code bench(back)}, {@code palette()} {@code BOARD} and
 * {@code PLAQUE}. The patio stairs use bamboo mosaic stairs.
 *
 * <h2>Anchors and groups</h2>
 *
 * Anchors: {@code HUB}, {@code HOLOGRAM}, {@code ENTRANCE}, {@code BEACON}, {@code THREAD_WALL},
 * {@code THREAD_WALL_END}, {@code THREAD_WALL_FRONT}, {@code BOARD}, {@code BOARD_FRONT}, the board's
 * lamps ({@code boardLamp(0..4)}) and slots ({@code boardSlot(0..17)}), 14 benches
 * ({@code bench(n)}, in placement order), the big screen ({@code bigScreen("hub", 7, 4, binding)}) and
 * the walk spots ({@code walkHub}, 4 in the hub then 4 in the core). Fixtures (never replaced by the
 * light pass): the board, its lamps and sign, both screens and their bases, the beacon. Every cell
 * belongs to the {@code hub} group (by position, see {@code CitadelFinish.groupOf}) except the core
 * cells, which belong to {@code cloister}.
 */
public final class CitadelHub {
	/** Aisle ceiling (and outer walls' top). */
	public static final int AISLE_TOP = 12;
	/** Nave vault: springs at y = 16 (12 above the floor), crown at y = 21 (17 above). */
	public static final int SPRING = 16;

	private CitadelHub() {
	}

	/** Height of the vault above a column at distance {@code ax} from the nave's axis. */
	public static int vaultY(int ax) {
		if (ax >= 6) {
			return SPRING;
		}
		return SPRING + (int) Math.round(5 * Math.sqrt(1 - (ax / 6.0) * (ax / 6.0)));
	}

	/** Whether {@code z} is an arcade line (a pier of the nave's colonnade). */
	public static boolean arcadeZ(int z) {
		return Math.floorMod(z + 2, 4) == 0 && Math.abs(z) <= 10;
	}

	// ------------------------------------------------------------------ hub: a basilica on the podium

	public static void hub(CitadelCanvas c) {
		shell(c);
		board(c);
		benches(c);
		screens(c);
		hologramAndBeacon(c);
		walkSpots(c);
	}

	/** Every column of the octagon: floor and nave for the interior, footing and wall for the wall ring. */
	public static void shell(CitadelCanvas c) {
		for (int x = -14; x <= 14; x++) {
			for (int z = -14; z <= 14; z++) {
				Kind k = c.zoneAt(x, z).kind();
				if (k == Kind.HUB) {
					double o = RingCampusGeometry.octagon(x, z);
					c.set(x, Y0, z, lattice(x, z) ? LIGHT : (o <= 3 || (int) o % 4 == 0 ? c.accent() : c.floor()));
					nave(c, x, z);
				} else if (k == Kind.HUB_WALL) {
					c.fill(x, 1, Y0, z, FOOT);
					hubWall(c, x, z);
				}
			}
		}
	}

	/** Benches facing the screens: the kanban wall (west) and the big screen (east). */
	public static void benches(CitadelCanvas c) {
		for (int sx = -1; sx <= 1; sx += 2) {
			Direction look = sx > 0 ? Direction.EAST : Direction.WEST;
			for (int z = -3; z <= 3; z++) {
				BlockPos p = new BlockPos(7 * sx, Y0 + 1, z);
				c.set(p, c.style.bench(look.getOpposite()));
				c.anchor(Anchor.at(AnchorNames.bench(c.nextBench()), p.relative(look), look));
			}
		}
	}

	/** The big screen (east) with the hub binding, and the thread wall (west) with its anchors. */
	public static void screens(CitadelCanvas c) {
		HqGeometry.Surface screen = new HqGeometry.Surface(new BlockPos(12, Y0 + 2, -3), Direction.SOUTH, Direction.WEST, 7, 4);
		bigScreen(c, screen, "hub", HqGenerator.hubBinding(c.layout), true);
		HqGeometry.Surface wall = new HqGeometry.Surface(new BlockPos(-12, Y0 + 2, 4), Direction.NORTH, Direction.EAST, 9, 4);
		for (BlockPos p : wall.cells()) {
			c.fixture(p, c.style.screen());
		}
		for (int i = 0; i < wall.width(); i++) {
			c.fixture(wall.origin().relative(wall.along(), i).below(), c.style.screenBase());
		}
		c.anchor(Anchor.face(AnchorNames.THREAD_WALL, wall.origin(), wall.facing()));
		c.anchor(Anchor.face(AnchorNames.THREAD_WALL_END, wall.origin().relative(wall.along(), wall.width() - 1).above(wall.height() - 1), wall.facing()));
		c.anchor(Anchor.at(AnchorNames.THREAD_WALL_FRONT, new BlockPos(-9, Y0 + 1, 0), Direction.WEST));
	}

	/** Hologram column free over the centre; the beacon crowns the vault. */
	public static void hologramAndBeacon(CitadelCanvas c) {
		for (int y = Y0 + 1; y <= Y0 + 1 + HqGeometry.HOLOGRAM_TOP; y++) {
			c.set(0, y, 0, AIR);
		}
		c.anchor(Anchor.at(AnchorNames.HUB, new BlockPos(0, Y0 + 1, 0), Direction.SOUTH));
		c.anchor(new Anchor(AnchorNames.HOLOGRAM, 0.5, Y0 + 5.0, 0.5, Direction.SOUTH, new BlockPos(0, Y0 + 5, 0)));
		c.anchor(Anchor.at(AnchorNames.ENTRANCE, new BlockPos(0, Y0 + 1, 11), Direction.SOUTH));
		BlockPos beacon = new BlockPos(0, vaultY(0) + 1, 0);
		c.set(beacon, c.style.beacon());
		c.fixtures.add(beacon.asLong());
		c.anchor(Anchor.at(AnchorNames.BEACON, beacon, Direction.SOUTH));
	}

	/** Four NPC walk spots in the nave. */
	public static void walkSpots(CitadelCanvas c) {
		for (int wz : new int[] {-4, 4}) {
			for (int sx = -1; sx <= 1; sx += 2) {
				c.walkSpot(new BlockPos(4 * sx, Y0 + 1, wz), Direction.SOUTH);
			}
		}
	}

	// ------------------------------------------------------------------ nave

	/** One interior column of the hub: the nave's vault, the arcades, clerestory and the aisles' ceiling. */
	public static void nave(CitadelCanvas c, int x, int z) {
		int ax = Math.abs(x);
		if (ax == 6) {
			naveArcade(c, x, z);
		} else if (ax > 6) {
			naveAisle(c, x, z);
		} else {
			naveVault(c, x, z, ax);
		}
	}

	/** The colonnade line ({@code |x| == 6}): log piers on the arcade lines, clerestory above, spring course. */
	public static void naveArcade(CitadelCanvas c, int x, int z) {
		if (arcadeZ(z)) {
			c.fill(x, Y0 + 1, AISLE_TOP - 1, z, LOG);
		}
		c.set(x, AISLE_TOP, z, c.kit.beam(Direction.Axis.Z));
		for (int y = AISLE_TOP + 1; y < SPRING; y++) {
			c.set(x, y, z, arcadeZ(z) ? LOG : (y == AISLE_TOP + 2 ? c.kit.wallPanel(true) : GLASS));
		}
		c.set(x, SPRING, z, PLANKS);
	}

	/** The aisles' ceiling ({@code |x| > 6}): deepslate with a light block on a lattice. */
	public static void naveAisle(CitadelCanvas c, int x, int z) {
		c.set(x, AISLE_TOP, z, Math.floorMod(x * 3 + z * 5, 7) == 0 ? LIGHT : FOOT);
	}

	/** The nave's barrel vault ({@code |x| < 6}): planks, ribs every 4 z, glass skylight along the axis. */
	public static void naveVault(CitadelCanvas c, int x, int z, int ax) {
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
			c.set(x, y, z, s);
		}
	}

	// ------------------------------------------------------------------ outer wall

	public static void hubWall(CitadelCanvas c, int x, int z) {
		boolean naveEnd = Math.abs(x) <= 6 && Math.abs(z) >= 12;
		hubWallColumn(c, x, z, naveEnd);
		if (naveEnd) {
			naveGable(c, x, z);
		} else {
			aisleCap(c, x, z);
		}
		portals(c, x, z);
	}

	/** The wall's column up to the aisle top: footing, panels, a glass band (not at the nave ends), cornice. */
	public static void hubWallColumn(CitadelCanvas c, int x, int z, boolean naveEnd) {
		for (int y = Y0 + 1; y <= AISLE_TOP; y++) {
			BlockState s;
			if (y <= Y0 + 1) {
				s = FOOT;
			} else if (y == AISLE_TOP) {
				s = CALCITE;
			} else if (!naveEnd && y >= Y0 + 3 && y <= Y0 + 6 && Math.floorMod(x + z, 3) != 0) {
				s = GLASS;
			} else if (y == Y0 + 2 && Math.floorMod(x * 5 + z * 3, 6) == 0) {
				s = c.kit.wallPanel(true);
			} else {
				s = c.kit.wallPanel(false);
			}
			c.set(x, y, z, s);
		}
	}

	/** Gable of the nave up to the vault, a rose window in it. */
	public static void naveGable(CitadelCanvas c, int x, int z) {
		int top = vaultY(Math.abs(x));
		for (int y = AISLE_TOP + 1; y <= top; y++) {
			c.set(x, y, z, Math.hypot(x, y - SPRING) <= 3.2 ? GLASS : CALCITE);
		}
	}

	/** The slab on top of the aisle walls. */
	public static void aisleCap(CitadelCanvas c, int x, int z) {
		c.set(x, AISLE_TOP + 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
	}

	/** Main portal (south, 5 wide x 5 tall) and the north door under the board (3 x 3). */
	public static void portals(CitadelCanvas c, int x, int z) {
		if (z > 0 && Math.abs(x) <= 2) {
			c.fill(x, Y0 + 1, Y0 + 5, z, AIR);
		}
		if (z < 0 && Math.abs(x) <= 1) {
			c.fill(x, Y0 + 1, Y0 + 3, z, AIR);
		}
	}

	// ------------------------------------------------------------------ board and screens

	/** The "needs attention" board at the nave's north end, facing the entrance, with its bulbs and standing rows. */
	public static void board(CitadelCanvas c) {
		int z = -12;
		for (int x = -5; x <= 5; x++) {
			for (int y = Y0 + 4; y <= Y0 + 10; y++) {
				boolean edge = Math.abs(x) == 5 || y == Y0 + 4 || y == Y0 + 10 || y == Y0 + 9;
				c.fixture(new BlockPos(x, y, z), edge ? c.kit.beam(Direction.Axis.X) : c.style.palette().get(HqStyle.Role.BOARD));
			}
		}
		int lamp = 0;
		for (int x = -4; x <= 4; x += 2) {
			c.fixture(new BlockPos(x, Y0 + 9, z), c.style.statusLamp());
			c.anchor(Anchor.face(AnchorNames.boardLamp(lamp++), new BlockPos(x, Y0 + 9, z), Direction.SOUTH));
		}
		BlockPos sign = new BlockPos(0, Y0 + 10, z + 1);
		c.put(sign.getX(), sign.getY(), sign.getZ(), c.style.palette().get(HqStyle.Role.PLAQUE).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), DECOR);
		c.data.put(sign.asLong(), new HqStyle.SignText(DyeColor.ORANGE, "", "Needs", "attention", "").signTag());
		c.fixtures.add(sign.asLong());
		c.anchor(Anchor.face(AnchorNames.BOARD, new BlockPos(0, Y0 + 6, z), Direction.SOUTH));
		c.anchor(Anchor.at(AnchorNames.BOARD_FRONT, new BlockPos(0, Y0 + 1, z + 1), Direction.NORTH));
		int slot = 0;
		for (int row = 2; row <= 3; row++) {
			for (int i = 0; i <= 8; i++) {
				int x = i == 0 ? 0 : (i % 2 == 1 ? (i + 1) / 2 : -(i / 2));
				c.anchor(Anchor.at(AnchorNames.boardSlot(slot++), new BlockPos(x, Y0 + 1, z + row), Direction.NORTH));
			}
		}
	}

	/** A big screen and its anchor; {@code base}: a beam under its bottom row (free-standing screens). */
	public static void bigScreen(CitadelCanvas c, HqGeometry.Surface s, String key, String binding, boolean base) {
		for (BlockPos p : s.cells()) {
			c.fixture(p, c.style.screen());
		}
		if (base) {
			for (int i = 0; i < s.width(); i++) {
				c.fixture(s.origin().relative(s.along(), i).below(), c.style.screenBase());
			}
		}
		c.anchor(Anchor.face(AnchorNames.bigScreen(key, s.width(), s.height(), binding), s.origin().above(s.height() - 1), s.facing()));
	}

	// ------------------------------------------------------------------ core: bridges, promenade, patio

	public static void core(CitadelCanvas c) {
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
						c.set(x, y, z, FOOT);
					}
					c.set(x, Y0 - i, z, stairs(Blocks.BAMBOO_MOSAIC_STAIRS, up));
					for (int y = Y0 - i + 1; y <= Y0 + 3; y++) {
						c.set(x, y, z, AIR);
					}
				}
				for (int i = 0; i <= 3; i++) {
					stairHeads.add(col(p.getX() - dx * i, p.getZ() + w));
				}
			}
		}
		List<Long> keys = new ArrayList<>(c.zones.keySet());
		Collections.sort(keys);
		for (long key : keys) {
			Kind k = c.zones.get(key).kind();
			int x = colX(key);
			int z = colZ(key);
			double r = RingCampusGeometry.r(x, z);
			if (k == Kind.BRIDGE) {
				CitadelGrounds.bridge(c, x, z);
			} else if (k == Kind.GALLERY) {
				CitadelGrounds.promenade(c, x, z, r, stairHeads.contains(key));
			} else if (k == Kind.PATIO) {
				CitadelGrounds.patio(c, x, z, r);
			}
		}
		c.walkSpot(new BlockPos(19, 1, 0), Direction.WEST);
		c.walkSpot(new BlockPos(-19, 1, 0), Direction.EAST);
		c.walkSpot(new BlockPos(0, Y0 + 1, -24), Direction.NORTH);
		c.walkSpot(new BlockPos(24, Y0 + 1, 0), Direction.EAST);
	}
}
