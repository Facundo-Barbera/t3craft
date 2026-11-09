package dev.t3craft.hq.citadel;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqGeometry;
import dev.t3craft.hq.HqStyle;
import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.citadel.hub.HubExterior;
import dev.t3craft.hq.citadel.hub.HubFacade;
import dev.t3craft.hq.citadel.hub.HubInterior;
import dev.t3craft.hq.citadel.hub.HubRoof;
import dev.t3craft.hq.citadel.hub.HubUndercroft;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.dims;
import static dev.t3craft.hq.citadel.CitadelCanvas.y0;
import static dev.t3craft.hq.citadel.CitadelCanvas.col;
import static dev.t3craft.hq.citadel.CitadelCanvas.colX;
import static dev.t3craft.hq.citadel.CitadelCanvas.colZ;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

/**
 * The hub of the citadel: a basilica on the podium ({@code floor y = y0()}), and the core ring round it.
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
 * <li>{@link #hubWall(CitadelCanvas, int, int)}: one column of the outer wall, delegated to
 *     {@link HubFacade#wall} (piers, windows, cornice, parapet or gable, the south portal and the north
 *     door under the board).</li>
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
 * <h2>Detail pieces</h2>
 *
 * The look lives in {@code dev.t3craft.hq.citadel.hub}: {@code HubFacade} (outer wall, parapet, gables, portals),
 * {@code HubRoof} (pitched roof, lantern drum, aisle terraces), {@code HubInterior} (floor, colonnade, furniture,
 * invisible lights), {@code HubExterior} (plinth, corbels, buttresses, niches, torii, stone lanterns),
 * {@code HubTowers} (corner towers), {@code HubBlocks} and {@code HubDims} (all sizes). The nave's vault is a
 * pointed arch: {@link #vaultY(int)} = min(spring + s(5), spring + naveHalf - ax).
 *
 * <h2>Parameters</h2>
 *
 * Geometry from the citadel's scale (values at scale 1): the hub spans x, z in [-14, 14] (the zone map
 * decides which columns are {@code HUB} / {@code HUB_WALL}; {@code g.hubX()} = 12 is the last interior
 * column); {@link #aisleTop()} = 12 (aisle ceiling and outer wall top), {@link #spring()} = 16 (the vault
 * springs, crown at {@code spring() + s(5)}), {@link #naveHalf()} = 6. The big screen of the east side is
 * a 7x4 {@link HqGeometry.Surface} at (hubX, y0()+2, -3) bound to {@code HqGenerator.hubBinding(layout)};
 * the thread wall (west) is 9x4 at (-hubX, y0()+2, 4). Screens, the board and the 14 benches keep their
 * size at any scale (they move with their walls).
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
	private CitadelHub() {
	}

	/** Aisle ceiling (and outer walls' top): y = 12 at scale 1, 8 above the podium ({@code y0() + sy(8)}; was {@code AISLE_TOP}). */
	public static int aisleTop() {
		return y0() + dims().sy(8);
	}

	/** Nave vault springs: y = 16 at scale 1, 12 above the podium ({@code y0() + sy(12)}; was {@code SPRING}); crown {@code s(5)} higher. */
	public static int spring() {
		return y0() + dims().sy(12);
	}

	/** Half width of the nave: the colonnade stands on {@code |x| == naveHalf()} (6 at scale 1). */
	public static int naveHalf() {
		return dims().s(6);
	}

	/** Height of the vault above a column at distance {@code ax} from the nave's axis (a pointed arch). */
	public static int vaultY(int ax) {
		int n = naveHalf();
		int spring = spring();
		if (ax >= n) {
			return spring;
		}
		return Math.min(spring + dims().s(5), spring + n - ax);
	}

	/** Whether {@code z} is an arcade line (a pier of the nave's colonnade): every {@code s(4)}, phase {@code s(2)}, up to {@code |z| <= s(10)}. */
	public static boolean arcadeZ(int z) {
		RingCampusGeometry.Dims d = dims();
		return Math.floorMod(z + d.s(2), d.s(4)) == 0 && Math.abs(z) <= d.s(10);
	}

	// ------------------------------------------------------------------ hub: a basilica on the podium

	public static void hub(CitadelCanvas c) {
		shell(c);
		board(c);
		benches(c);
		screens(c);
		hologramAndBeacon(c);
		HubInterior.furnish(c);
		HubUndercroft.build(c);
		HubRoof.cupola(c);
		walkSpots(c);
	}

	/** Every column of the octagon: floor and nave for the interior, footing and wall for the wall ring. */
	public static void shell(CitadelCanvas c) {
		int ext = (int) Math.ceil(c.g.hubOut);
		for (int x = -ext; x <= ext; x++) {
			for (int z = -ext; z <= ext; z++) {
				Kind k = c.zoneAt(x, z).kind();
				if (k == Kind.HUB) {
					c.set(x, y0(), z, HubInterior.floor(c, x, z));
					nave(c, x, z);
				} else if (k == Kind.HUB_WALL) {
					HubFacade.base(c, x, z);
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
				BlockPos p = new BlockPos((naveHalf() + 1) * sx, y0() + 1, z);
				c.set(p, c.style.bench(look.getOpposite()));
				c.anchor(Anchor.at(AnchorNames.bench(c.nextBench()), p.relative(look), look));
			}
		}
	}

	/** The big screen (east) with the hub binding, and the thread wall (west) with its anchors. */
	public static void screens(CitadelCanvas c) {
		int hx = c.g.hubX();
		HqGeometry.Surface screen = new HqGeometry.Surface(new BlockPos(hx, y0() + 2, -3), Direction.SOUTH, Direction.WEST, 7, 4);
		bigScreen(c, screen, "hub", HqGenerator.hubBinding(c.layout), true);
		HqGeometry.Surface wall = new HqGeometry.Surface(new BlockPos(-hx, y0() + 2, 4), Direction.NORTH, Direction.EAST, 9, 4);
		for (BlockPos p : wall.cells()) {
			c.fixture(p, c.style.screen());
		}
		threadWallFrame(c, wall);
		c.anchor(Anchor.face(AnchorNames.THREAD_WALL, wall.origin(), wall.facing()));
		c.anchor(Anchor.face(AnchorNames.THREAD_WALL_END, wall.origin().relative(wall.along(), wall.width() - 1).above(wall.height() - 1), wall.facing()));
		c.anchor(Anchor.at(AnchorNames.THREAD_WALL_FRONT, new BlockPos(-(hx - 3), y0() + 1, 0), Direction.WEST));
	}

	/**
	 * The kanban wall's frame (kit/thread-board): so the wall neither floats in the aisle nor sits borderless on the
	 * outer wall. All in the wall's own plane except the cornice, which steps one cell out over the heads:
	 * <ul>
	 * <li>a sill of dark oak planks under the board (it replaces the old beam base);</li>
	 * <li>a post of stripped dark oak (vertical log) at each end, from the sill to the lintel, one cell outside the board;</li>
	 * <li>a lintel of stripped dark oak along the top with three lit copper bulbs (the board's warning lights);</li>
	 * <li>a cornice: upside-down dark oak stairs one cell in front of the lintel, with the back on the wall.</li>
	 * </ul>
	 * Nothing is lower than the sill, nor sticks out below head height: the aisle stays clear ({@code x = -hubX + 1} and up are free).
	 */
	static void threadWallFrame(CitadelCanvas c, HqGeometry.Surface wall) {
		Direction along = wall.along();
		Direction out = wall.facing();
		int below = wall.origin().getY() - 1;
		int top = wall.origin().getY() + wall.height();
		BlockState planks = Blocks.DARK_OAK_PLANKS.defaultBlockState();
		BlockState lamp = Blocks.COPPER_BULB.waxed().unaffected().defaultBlockState().setValue(BlockStateProperties.LIT, true);
		BlockState cornice = Blocks.DARK_OAK_STAIRS.defaultBlockState()
				.setValue(StairBlock.FACING, out.getOpposite()).setValue(StairBlock.HALF, Half.TOP);
		for (int i = -1; i <= wall.width(); i++) {
			BlockPos base = wall.origin().relative(along, i);
			c.set(base.atY(below), planks);
			if (i < 0 || i == wall.width()) {
				for (int y = below + 1; y < top; y++) {
					c.set(base.atY(y), darkLog(Direction.Axis.Y));
				}
			}
			boolean bulb = i == 1 || i == wall.width() / 2 || i == wall.width() - 2;
			c.set(base.atY(top), bulb ? lamp : darkLog(along.getAxis()));
			c.set(base.atY(top).relative(out), cornice);
		}
	}

	private static BlockState darkLog(Direction.Axis axis) {
		return Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	/** Hologram column free over the centre; the beacon crowns the vault. */
	public static void hologramAndBeacon(CitadelCanvas c) {
		for (int y = y0() + 1; y <= y0() + 1 + HqGeometry.HOLOGRAM_TOP; y++) {
			c.set(0, y, 0, AIR);
		}
		c.anchor(Anchor.at(AnchorNames.HUB, new BlockPos(0, y0() + 1, 0), Direction.SOUTH));
		c.anchor(new Anchor(AnchorNames.HOLOGRAM, 0.5, y0() + 5.0, 0.5, Direction.SOUTH, new BlockPos(0, y0() + 5, 0)));
		c.anchor(Anchor.at(AnchorNames.ENTRANCE, new BlockPos(0, y0() + 1, c.g.hubX() - 1), Direction.SOUTH));
		BlockPos beacon = new BlockPos(0, vaultY(0) + 1, 0);
		c.set(beacon, c.style.beacon());
		c.fixtures.add(beacon.asLong());
		c.anchor(Anchor.at(AnchorNames.BEACON, beacon, Direction.SOUTH));
	}

	/** Four NPC walk spots in the nave. */
	public static void walkSpots(CitadelCanvas c) {
		for (int wz : new int[] {-c.s(4), c.s(4)}) {
			for (int sx = -1; sx <= 1; sx += 2) {
				c.walkSpot(new BlockPos(c.s(4) * sx, y0() + 1, wz), Direction.SOUTH);
			}
		}
	}

	// ------------------------------------------------------------------ nave

	/** One interior column of the hub: the nave's vault, the arcades, clerestory and the aisles' ceiling. */
	public static void nave(CitadelCanvas c, int x, int z) {
		int ax = Math.abs(x);
		int n = naveHalf();
		if (ax == n) {
			naveArcade(c, x, z);
		} else if (ax > n) {
			naveAisle(c, x, z);
		} else {
			naveVault(c, x, z, ax);
		}
	}

	/** The colonnade line ({@code |x| == naveHalf()}); see {@link HubInterior#arcade}. */
	public static void naveArcade(CitadelCanvas c, int x, int z) {
		HubInterior.arcade(c, x, z);
	}

	/** The aisles' ceiling ({@code |x| > naveHalf()}); see {@link HubInterior#aisle}. */
	public static void naveAisle(CitadelCanvas c, int x, int z) {
		HubInterior.aisle(c, x, z);
	}

	/** The nave's barrel vault ({@code |x| < naveHalf()}); see {@link HubInterior#vault}. */
	public static void naveVault(CitadelCanvas c, int x, int z, int ax) {
		HubInterior.vault(c, x, z, ax);
	}

	// ------------------------------------------------------------------ outer wall

	/** One column of the outer wall: piers, windows, cornice, parapet or gable, portals ({@link HubFacade#wall}). */
	public static void hubWall(CitadelCanvas c, int x, int z) {
		HubFacade.wall(c, x, z);
	}

	// ------------------------------------------------------------------ board and screens

	/** The "needs attention" board at the nave's north end, facing the entrance, with its bulbs and standing rows. */
	public static void board(CitadelCanvas c) {
		int z = -c.g.hubX();
		for (int x = -5; x <= 5; x++) {
			for (int y = y0() + 4; y <= y0() + 10; y++) {
				boolean edge = Math.abs(x) == 5 || y == y0() + 4 || y == y0() + 10 || y == y0() + 9;
				c.fixture(new BlockPos(x, y, z), edge ? c.kit.beam(Direction.Axis.X) : c.style.palette().get(HqStyle.Role.BOARD));
			}
		}
		int lamp = 0;
		for (int x = -4; x <= 4; x += 2) {
			c.fixture(new BlockPos(x, y0() + 9, z), c.style.statusLamp());
			c.anchor(Anchor.face(AnchorNames.boardLamp(lamp++), new BlockPos(x, y0() + 9, z), Direction.SOUTH));
		}
		BlockPos sign = new BlockPos(0, y0() + 10, z + 1);
		c.put(sign.getX(), sign.getY(), sign.getZ(), c.style.palette().get(HqStyle.Role.PLAQUE).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), DECOR);
		c.data.put(sign.asLong(), new HqStyle.SignText(DyeColor.ORANGE, "", "Needs", "attention", "").signTag());
		c.fixtures.add(sign.asLong());
		c.anchor(Anchor.face(AnchorNames.BOARD, new BlockPos(0, y0() + 6, z), Direction.SOUTH));
		c.anchor(Anchor.at(AnchorNames.BOARD_FRONT, new BlockPos(0, y0() + 1, z + 1), Direction.NORTH));
		int slot = 0;
		for (int row = 2; row <= 3; row++) {
			for (int i = 0; i <= 8; i++) {
				int x = i == 0 ? 0 : (i % 2 == 1 ? (i + 1) / 2 : -(i / 2));
				c.anchor(Anchor.at(AnchorNames.boardSlot(slot++), new BlockPos(x, y0() + 1, z + row), Direction.NORTH));
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
			BlockPos p = RingCampusGeometry.polar(c.g.patioR + 0.2, a, 0);
			int dx = p.getX() > 0 ? -1 : 1;
			Direction up = dx < 0 ? Direction.EAST : Direction.WEST;
			int hw = c.s(1);
			for (int w = -hw; w <= hw; w++) {
				// one step per block of height: as many steps as the podium is high (4 at scale 1)
				for (int i = 0; i < y0(); i++) {
					int x = p.getX() + dx * i;
					int z = p.getZ() + w;
					for (int y = 1; y < y0() - i; y++) {
						c.set(x, y, z, FOOT);
					}
					c.set(x, y0() - i, z, stairs(Blocks.BAMBOO_MOSAIC_STAIRS, up));
					for (int y = y0() - i + 1; y <= y0() + 3; y++) {
						c.set(x, y, z, AIR);
					}
				}
				for (int i = 0; i < y0(); i++) {
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
		HubExterior.paint(c);
		c.walkSpot(new BlockPos(c.s(19), 1, 0), Direction.WEST);
		c.walkSpot(new BlockPos(-c.s(19), 1, 0), Direction.EAST);
		c.walkSpot(new BlockPos(0, y0() + 1, -c.s(24)), Direction.NORTH);
		c.walkSpot(new BlockPos(c.s(24), y0() + 1, 0), Direction.EAST);
	}
}
