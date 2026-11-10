package dev.t3craft.hq.citadel;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.GRASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.MOSS;
import static dev.t3craft.hq.citadel.CitadelCanvas.col;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.T3Craft;
import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Lot;
import dev.t3craft.hq.RingCampusGeometry.Room;
import dev.t3craft.hq.citadel.barrio.BarrioCourt;
import dev.t3craft.hq.plan.Anchor;
import dev.t3craft.mapgen.Area;
import dev.t3craft.mapgen.MapLayout;
import dev.t3craft.mapgen.Piece;
import dev.t3craft.mapgen.PieceSource;
import dev.t3craft.mapgen.Slot;
import dev.t3craft.mapgen.SlotPlacer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * The citadel painted by slots (issue #37): the plan painter ({@code RingCampusGenerator}) asks a
 * {@link PieceSource} for the piece of some of the slots of the citadel's {@link MapLayout}
 * ({@code hq.CitadelMapGenerator}) and paints it on the canvas; when the source has nothing that fits, or the piece
 * would cover a cell the game needs, the slot keeps the painter's own piece (the legacy path).
 *
 * <h2>Switch</h2>
 * {@code config/t3craft.json} → {@code "mapgen": {"citadel": {"pieces": "legacy" | "slots"}}}, default
 * {@code "legacy"} (the citadel exactly as before, block by block). Read on every plan, so a change takes effect on
 * the next plan (RELOAD, no restart). The system property {@code -Dt3craft.citadel.pieces=legacy|slots} wins over the
 * file (for headless renders). An unknown value is {@code legacy} with one warning.
 *
 * <h2>Slots filled in this cut</h2>
 * Only the slots that hold no game anchors, desks or workstations; the painter asks for the <b>free box</b> of each
 * one, which can be smaller than the map slot:
 * <ul>
 * <li>{@code lot_n_court} ({@link Slot.Role#PLAZA}) of a barrio: the courtyard's centre ({@code |a|, |b| <= s(1)}),
 *     instead of the tree, pool, fountain or gazebo; the quadrant dressings and corner posts stay;</li>
 * <li>{@code lot_n_hall_h} ({@link Slot.Role#MODULE}, not {@code occupied}) of a barrio: the spot of a hall that is
 *     not built yet, instead of its garden (or its house in a dense citadel); grass and the corner bushes stay;</li>
 * <li>{@code lot_n} ({@link Slot.Role#MODULE}, not {@code occupied}) of an empty lot: a centrepiece on the lot's
 *     centre, courtyard-sized, before the catalog placement composes the rest of the lot around it (only with
 *     {@code catalog.enabled = 1}, the default; with it off the empty lot stays legacy).</li>
 * </ul>
 * Hub, nodes, gatehouses, built halls and the spawn stay legacy in both modes.
 *
 * <h2>Rules</h2>
 * A piece is refused (and the slot tried again one block smaller per side, down to a single cell, then left to the
 * legacy path) when any of its blocks would stand on an anchor column, the spawn, a column of a built hall, or a cell
 * the caller reserves (a courtyard's anchors), or when it straddles a terrace step of the relief. Pieces are painted
 * flat at {@code y = 1} like every other piece of the canvas and lifted with their terrace as one ({@link
 * CitadelCanvas#rigid}). The plan stays pure and deterministic: the seed of a slot comes from
 * {@link PieceSource#seedFor}, the order of the slots is the painter's.
 *
 * <p>Work per tick is not touched: this only changes which cells the plan holds; the builders place the plan with
 * their usual budget.
 */
public final class CitadelSlots {
	/** Config section, sub-section and key: {@code mapgen.citadel.pieces}. */
	public static final String SECTION = "mapgen";
	public static final String SUB = "citadel";
	public static final String KEY = "pieces";
	/** System property that overrides the config file. */
	public static final String PROPERTY = "t3craft.citadel.pieces";

	/** How the citadel's places get their pieces. */
	public enum Mode {
		/** The painter's own pieces, exactly as before (default). */
		LEGACY,
		/** Some slots ask a {@link PieceSource} first (see the class comment). */
		SLOTS;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		static @Nullable Mode parse(@Nullable String s) {
			if (s == null) {
				return null;
			}
			return switch (s.trim().toLowerCase(Locale.ROOT)) {
				case "legacy" -> LEGACY;
				case "slots" -> SLOTS;
				default -> null;
			};
		}
	}

	private static final PieceSource DEFAULT_SOURCE = new CitadelPieceSource();
	private static volatile @Nullable PieceSource override;
	private static volatile @Nullable String warned;

	private final MapLayout map;
	private final PieceSource source;

	public CitadelSlots(MapLayout map, PieceSource source) {
		this.map = map;
		this.source = source;
	}

	// ------------------------------------------------------------------ switch and source

	/**
	 * Makes {@code s} the source of the citadel's slots (the real piece catalog plugs in here); null goes back to
	 * {@link CitadelPieceSource}. Takes effect on the next plan.
	 */
	public static void use(@Nullable PieceSource s) {
		override = s;
	}

	/** The source the next plan asks. */
	public static PieceSource source() {
		PieceSource s = override;
		return s == null ? DEFAULT_SOURCE : s;
	}

	/** The mode for the plan being generated: the system property, else {@code mapgen.citadel.pieces}, else legacy. */
	public static Mode mode() {
		String prop = System.getProperty(PROPERTY);
		if (prop != null && !prop.isBlank()) {
			return orLegacy(prop);
		}
		Path file;
		try {
			file = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("t3craft.json");
		} catch (RuntimeException | LinkageError e) {
			return Mode.LEGACY; // no Fabric (a headless tool): the property is the only switch
		}
		return orLegacy(read(file));
	}

	/** The {@code mapgen.citadel.pieces} text of {@code file}, or null when absent or unreadable. */
	static @Nullable String read(Path file) {
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try {
			JsonElement root = JsonParser.parseString(Files.readString(file));
			if (root != null && root.isJsonObject() && root.getAsJsonObject().get(SECTION) instanceof JsonObject m
					&& m.get(SUB) instanceof JsonObject c && c.get(KEY) != null && c.get(KEY).isJsonPrimitive()) {
				return c.get(KEY).getAsString();
			}
		} catch (Exception e) {
			warnOnce("unreadable config file");
		}
		return null;
	}

	private static Mode orLegacy(@Nullable String text) {
		if (text == null) {
			return Mode.LEGACY;
		}
		Mode m = Mode.parse(text);
		if (m == null) {
			warnOnce("'" + text + "'");
			return Mode.LEGACY;
		}
		return m;
	}

	private static void warnOnce(String what) {
		if (!what.equals(warned)) {
			warned = what;
			T3Craft.LOGGER.warn("[citadel] {}.{}.{} {}: using legacy (known: legacy, slots)", SECTION, SUB, KEY, what);
		}
	}

	// ------------------------------------------------------------------ the places (called by the painter)

	/**
	 * The centre of a barrio courtyard ({@code lot_n_court}). True when a piece was painted (the caller then skips
	 * the legacy centre but keeps the quadrants and corners); false in legacy mode or when nothing fits.
	 */
	public static boolean courtCentre(CitadelCanvas c, Lot l) {
		CitadelSlots s = c.slots;
		Slot slot = s == null ? null : s.map.slot("lot_" + l.id() + "_court");
		if (slot == null) {
			return false;
		}
		int half = Math.max(1, c.s(1));
		int g = l.court();
		// the lot's anchors (placed after the courtyard by CitadelBarrios.barrio): wing at (-(g-1), 0), walk at (2, 2)
		Set<Long> reserved = new HashSet<>();
		reserved.add(column(l.at(-(g - 1), 0, 1)));
		reserved.add(column(l.at(2, 2, 1)));
		BlockPos ctr = l.centre();
		return s.fill(c, slot, ctr.getX(), ctr.getZ(), slot.facing(), half, half, c.sy(8), reserved);
	}

	/**
	 * The spot of a hall that is not built ({@code lot_n_hall_h}, not {@code occupied}): its ground (grass, moss,
	 * corner bushes, as the legacy garden) and a piece on the room's interior. True when painted; false in legacy mode
	 * or when nothing fits (the caller then paints the legacy garden, which repaints the ground itself).
	 */
	public static boolean hallSpot(CitadelCanvas c, Lot l, int h) {
		CitadelSlots s = c.slots;
		Slot slot = s == null ? null : s.map.slot("lot_" + l.id() + "_hall_" + h);
		if (slot == null || slot.has(CitadelPieceSource.TAG_OCCUPIED)) {
			return false;
		}
		Room r = l.hall(h);
		// the interior u in [0, d), v in [0, w), centred; an even depth loses its back row (the box needs a centre)
		BlockPos ctr = r.cell((r.d() - 1) / 2, r.c(), 1);
		// the ground first: if nothing is painted, the legacy garden repaints the same ground over it
		hallGround(c, r);
		return s.fill(c, slot, ctr.getX(), ctr.getZ(), r.back().getOpposite(), (r.w() - 1) / 2, (r.d() - 1) / 2, 2 * c.g.floorH, Set.of());
	}

	/**
	 * The centre of an empty lot ({@code lot_n}, not {@code occupied}): a courtyard-sized centrepiece, painted before
	 * the catalog placement fills the rest of the lot (it only builds on free soft ground, so it goes round the
	 * piece). Nothing in legacy mode or with the catalog placement off.
	 */
	public static void emptyLot(CitadelCanvas c, int lot) {
		CitadelSlots s = c.slots;
		Slot slot = s == null ? null : s.map.slot("lot_" + lot);
		if (slot == null || slot.has(CitadelPieceSource.TAG_OCCUPIED) || !CitadelPlacement.enabled()) {
			return;
		}
		Lot l = RingCampusGeometry.lot(lot);
		BlockPos ctr = l.centre();
		s.fill(c, slot, ctr.getX(), ctr.getZ(), slot.facing(), l.court(), l.court(), c.sy(12), Set.of());
	}

	// ------------------------------------------------------------------ filling a slot

	/** The box the painter asks for: {@code slot} moved to the canvas frame ({@code y = 1}) and cut to its free room. */
	private Slot ask(Slot slot, int x, int z, Direction facing, int halfAcross, int halfDeep, int height) {
		return new Slot(slot.id(), slot.role(), new BlockPos(x, 1, z), facing, halfAcross, halfDeep, Math.max(1, height), slot.group(),
				slot.parent(), slot.tags());
	}

	private @Nullable Piece pieceFor(Slot ask) {
		try {
			Piece p = source.pieceFor(ask, map);
			return p != null && ask.fits(p.size()) ? p : null;
		} catch (RuntimeException e) {
			T3Craft.LOGGER.warn("[citadel] piece source failed on slot {}: {}", ask.id(), e.toString());
			return null;
		}
	}

	/**
	 * Asks the source for {@code slot} with the box {@code halfAcross × halfDeep × height} centred on {@code (x, z)},
	 * one block smaller per side each time the piece is refused (see the class comment); paints the first piece that
	 * passes. False when nothing was painted.
	 */
	private boolean fill(CitadelCanvas c, Slot slot, int x, int z, Direction facing, int halfAcross, int halfDeep, int height,
			Set<Long> reserved) {
		for (int ha = halfAcross, hd = halfDeep; ha >= 0 && hd >= 0; ha--, hd--) {
			Slot ask = ask(slot, x, z, facing, ha, hd, height);
			Piece p = pieceFor(ask);
			if (p == null) {
				return false;
			}
			List<dev.t3craft.build.Placement> blocks = SlotPlacer.place(ask, p);
			if (blocked(c, blocks, reserved)) {
				continue;
			}
			if (!oneTerrace(c, ask.footprint())) {
				return false;
			}
			paint(c, ask, p, blocks);
			return true;
		}
		return false;
	}

	/** Whether a block of the piece stands where the game needs the cell (see the class comment). */
	private static boolean blocked(CitadelCanvas c, List<dev.t3craft.build.Placement> blocks, Set<Long> reserved) {
		Set<Long> anchors = new HashSet<>(reserved);
		for (Anchor a : c.anchors.values()) {
			anchors.add(column(a.block()));
		}
		anchors.add(column(CitadelFinish.spawn()));
		for (dev.t3craft.build.Placement b : blocks) {
			BlockPos q = b.pos();
			int y = q.getY();
			if (b.state().isAir() && y >= 1) {
				continue; // clearing the air above the ground hurts nobody
			}
			long k = col(q.getX(), q.getZ());
			if (c.hallCols.containsKey(k) || c.fixtures.contains(q.asLong())) {
				return true; // never a built hall, a desk or a screen
			}
			if (anchors.contains(k) && y >= 0 && y <= 3) {
				return true; // an anchor's floor, feet and head stay as the painter leaves them
			}
		}
		return false;
	}

	/** Whether every column of {@code a} stands on the same terrace of the relief. */
	private static boolean oneTerrace(CitadelCanvas c, Area a) {
		int t = CitadelRelief.halves(c, a.minX(), a.minZ());
		for (int x = a.minX(); x <= a.maxX(); x++) {
			for (int z = a.minZ(); z <= a.maxZ(); z++) {
				if (CitadelRelief.halves(c, x, z) != t) {
					return false;
				}
			}
		}
		return true;
	}

	/** Clears the lot's bushes in the footprint, then writes the piece (layers by block, block entities kept). */
	private static void paint(CitadelCanvas c, Slot ask, Piece p, List<dev.t3craft.build.Placement> blocks) {
		Area a = ask.footprint();
		for (int x = a.minX(); x <= a.maxX(); x++) {
			for (int z = a.minZ(); z <= a.maxZ(); z++) {
				for (int y = 1; y <= 3; y++) {
					BlockState s = c.at(x, y, z);
					if (s != null && (s.is(Blocks.AZALEA_LEAVES) || s.is(Blocks.FLOWERING_AZALEA_LEAVES))) {
						c.set(x, y, z, AIR);
					}
				}
			}
		}
		c.pieces.add(new CitadelCanvas.Piece("SLOT:" + ask.id() + ":" + p.id(), a.minX(), 1, a.minZ(), a.maxX() - a.minX() + 1,
				a.maxZ() - a.minZ() + 1, ask.facing().name()));
		c.rigid(ask.origin().getX(), ask.origin().getZ());
		try {
			for (dev.t3craft.build.Placement b : blocks) {
				BlockPos q = b.pos();
				c.put(q.getX(), q.getY(), q.getZ(), b.state(), layerOf(b.state()));
				CompoundTag t = b.blockEntity();
				if (t != null) {
					c.data.put(q.asLong(), t);
				}
			}
		} finally {
			c.unrigid();
		}
	}

	/** The ground of an unbuilt hall spot, as {@code BarrioCourt.slotGarden} paints it: grass with moss, bushes at the corners. */
	private static void hallGround(CitadelCanvas c, Room r) {
		for (int u = -1; u <= r.d(); u++) {
			for (int v = -1; v <= r.w(); v++) {
				BlockPos p = r.cell(u, v, 0);
				c.set(p, (u + v) % 5 == 0 ? MOSS : GRASS);
				if (r.corner(u, v)) {
					c.set(p.above(), BarrioCourt.bush(p.getX(), p.getZ()));
				}
			}
		}
	}

	/**
	 * The canvas layer of a block of a piece ({@link dev.t3craft.mapgen.Piece} carries none): hanging lanterns and
	 * ceiling signs hang ({@link CitadelCanvas#HANGING}), wall signs, banners and torches go after the solids
	 * ({@link CitadelCanvas#DECOR}), everything else is solid.
	 */
	static int layerOf(BlockState s) {
		Block b = s.getBlock();
		if (b instanceof CeilingHangingSignBlock
				|| b instanceof LanternBlock && s.hasProperty(BlockStateProperties.HANGING) && s.getValue(BlockStateProperties.HANGING)) {
			return CitadelCanvas.HANGING;
		}
		if (b instanceof WallSignBlock || b instanceof WallBannerBlock || b instanceof WallTorchBlock) {
			return CitadelCanvas.DECOR;
		}
		return CitadelCanvas.SOLID;
	}

	private static long column(BlockPos p) {
		return col(p.getX(), p.getZ());
	}
}
