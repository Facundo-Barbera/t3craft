package dev.t3craft.hq.citadel;

import dev.t3craft.T3Craft;
import dev.t3craft.hq.RingCampusStyle;
import dev.t3craft.hq.citadel.catalog.Catalog;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.style.HqKit;
import dev.t3craft.mapgen.MapLayout;
import dev.t3craft.mapgen.Piece;
import dev.t3craft.mapgen.PieceSource;
import dev.t3craft.mapgen.Slot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The {@link PieceSource} of the citadel by slots ({@code mapgen.citadel.pieces = "slots"}, see {@link CitadelSlots}):
 * a variant of the citadel catalog ({@link Catalog}) for a slot, built into the biggest box of its size range that
 * fits the slot, in the piece frame (front north, centred on the origin).
 *
 * <h2>Which families fill which slots</h2>
 * <ul>
 * <li>a tag {@code family:<name>} ({@code family:fountain}) on the slot: only that family;</li>
 * <li>{@link Slot.Role#PLAZA} (a barrio courtyard's centre): fountain, well, statue, tree;</li>
 * <li>{@link Slot.Role#MODULE} with a parent (an unbuilt hall spot of a barrio): house, gazebo, annex, garden;</li>
 * <li>{@link Slot.Role#MODULE} without a parent (the centre of an empty lot): plaza centre, fountain, gazebo, garden;</li>
 * <li>{@link Slot.Role#DECOR}: tree, lamp, planter, bench;</li>
 * <li>anything else, and every slot tagged {@code occupied} (hub, nodes, gatehouses, halls with desks): nothing, so
 *     the painter keeps its own building there.</li>
 * </ul>
 * The family tried first comes from the slot's seed ({@link PieceSource#seedFor}); when it has nothing that fits, the
 * next one in the list is tried. Within a family the variant is drawn by weight with the same seed, so the same slot
 * gets the same piece every run. Pure: no world, no clock.
 */
public final class CitadelPieceSource implements PieceSource {
	/** Tag prefix that pins a slot to one catalog family ({@code family:house}). */
	public static final String TAG_FAMILY = "family:";
	/** Tag that marks a slot already used by the game (a hall with desks); never filled. */
	public static final String TAG_OCCUPIED = "occupied";

	private static final List<Family> PLAZA = List.of(Family.FOUNTAIN, Family.WELL, Family.STATUE, Family.TREE);
	private static final List<Family> HALL_SPOT = List.of(Family.HOUSE, Family.GAZEBO, Family.ANNEX, Family.GARDEN);
	private static final List<Family> LOT_CENTRE = List.of(Family.PLAZA_CENTER, Family.FOUNTAIN, Family.GAZEBO, Family.GARDEN);
	private static final List<Family> DECOR = List.of(Family.TREE, Family.LAMP, Family.PLANTER, Family.BENCH);

	@Override
	public @Nullable Piece pieceFor(Slot slot, MapLayout map) {
		if (slot.has(TAG_OCCUPIED)) {
			return null;
		}
		List<Family> families = families(slot);
		if (families.isEmpty()) {
			return null;
		}
		long seed = PieceSource.seedFor(slot, map);
		int start = (int) Math.floorMod(seed, (long) families.size());
		for (int i = 0; i < families.size(); i++) {
			Piece p = piece(families.get((start + i) % families.size()), slot, seed);
			if (p != null) {
				return p;
			}
		}
		return null;
	}

	/** The families that may fill {@code slot}, in order of preference (see the class comment). */
	public static List<Family> families(Slot slot) {
		for (String t : slot.tags()) {
			if (t.startsWith(TAG_FAMILY)) {
				try {
					return List.of(Family.valueOf(t.substring(TAG_FAMILY.length()).trim().toUpperCase(Locale.ROOT)));
				} catch (IllegalArgumentException e) {
					return List.of();
				}
			}
		}
		return switch (slot.role()) {
			case PLAZA -> PLAZA;
			case MODULE -> slot.parent() != null ? HALL_SPOT : LOT_CENTRE;
			case DECOR -> DECOR;
			default -> List.of();
		};
	}

	/** A variant of {@code f} built into the biggest box that fits {@code slot}, chosen by weight; null if none fits. */
	private static @Nullable Piece piece(Family f, Slot slot, long seed) {
		List<Variant> fit = new ArrayList<>();
		List<int[]> boxes = new ArrayList<>();
		int total = 0;
		for (Variant v : Catalog.of(f)) {
			if (v.weight() <= 0) {
				continue;
			}
			int[] box = box(v, slot.across(), slot.deep(), slot.height(), seed);
			if (box != null) {
				fit.add(v);
				boxes.add(box);
				total += v.weight();
			}
		}
		if (total == 0) {
			return null;
		}
		int r = new Random(seed * 31 + f.ordinal()).nextInt(total);
		int i = 0;
		for (; i < fit.size() - 1; i++) {
			r -= fit.get(i).weight();
			if (r < 0) {
				break;
			}
		}
		return build(fit.get(i), boxes.get(i), seed);
	}

	/**
	 * The biggest frontage (then depth) up to {@code maxW × maxD} that {@code v} accepts at height {@code h}, as
	 * {@code {w, d, h}}; null when none does. The height is the slot's (extra height may stay empty).
	 */
	static int @Nullable [] box(Variant v, int maxW, int maxD, int h, long seed) {
		if (h < v.minH()) {
			return null;
		}
		for (int w = maxW; w >= Math.max(1, v.minW()); w--) {
			for (int d = maxD; d >= Math.max(1, v.minD()); d--) {
				if (v.fits(new Plot(0, 0, 0, w, d, h, Direction.NORTH, seed))) {
					return new int[] {w, d, h};
				}
			}
		}
		return null;
	}

	/** {@code v} built on its own into a {@code box}, front north, centred on the origin: the piece frame. */
	private static @Nullable Piece build(Variant v, int[] box, long seed) {
		int w = box[0];
		int d = box[1];
		int h = box[2];
		Plot plot = new Plot(-(w - 1) / 2, 0, -(d - 1) / 2, w, d, h, Direction.NORTH, seed);
		CitadelCanvas canvas = new CitadelCanvas(new RingCampusStyle(), HqKit.active());
		try {
			v.build(canvas, plot);
		} catch (RuntimeException e) {
			// a broken variant must not take the citadel down: the slot falls back to the painter's own piece
			T3Craft.LOGGER.warn("[citadel] slot piece {} failed on {}: {}", v.id(), plot, e.toString());
			return null;
		}
		List<Long> keys = new ArrayList<>();
		for (Map.Entry<Long, BlockState> e : canvas.cells.entrySet()) {
			if (BlockPos.getY(e.getKey()) >= -1) {
				keys.add(e.getKey());
			}
		}
		// a fixed order (bottom-up, then z, then x), whatever the map's iteration order
		keys.sort((a, b) -> {
			int c = Integer.compare(BlockPos.getY(a), BlockPos.getY(b));
			if (c == 0) {
				c = Integer.compare(BlockPos.getZ(a), BlockPos.getZ(b));
			}
			return c != 0 ? c : Integer.compare(BlockPos.getX(a), BlockPos.getX(b));
		});
		List<dev.t3craft.build.Placement> blocks = new ArrayList<>(keys.size());
		for (long k : keys) {
			blocks.add(new dev.t3craft.build.Placement(BlockPos.of(k), canvas.cells.get(k), canvas.data.get(k)));
		}
		return new Piece(v.id(), new Piece.Size(w, d, h), blocks);
	}
}
