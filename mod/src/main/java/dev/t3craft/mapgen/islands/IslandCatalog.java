package dev.t3craft.mapgen.islands;

import dev.t3craft.T3Craft;
import dev.t3craft.build.Placement;
import dev.t3craft.hq.RingCampusStyle;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Catalog;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.style.HqKit;
import dev.t3craft.mapgen.MapLayout;
import dev.t3craft.mapgen.Noise;
import dev.t3craft.mapgen.Piece;
import dev.t3craft.mapgen.PieceSource;
import dev.t3craft.mapgen.Slot;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The {@link PieceSource} of the floating islands over the <b>real</b> citadel catalog
 * ({@code hq.citadel.catalog}: houses, gazebos, fountains, wells, annexes, stalls, gardens, trees, lamps,
 * planters, benches...). Read only: it asks {@link Catalog#of} for a family's variants and builds one into a
 * {@link Plot} the size of the slot, exactly like the piece stage ({@code piece.VariantPiece}) does, then turns
 * the canvas into a {@link Piece} in the piece frame.
 *
 * <p>Choice, all deterministic from the field's seed and the slot's position:
 * <ol>
 *   <li>the families for the slot's role and hint ({@link #families}): a centre takes a house when it has room,
 *       a module an annex, stall, well, fountain, garden..., a tree slot a tree, an accent a lamp, planter,
 *       bench, banner pole or signpost;</li>
 *   <li>a family is drawn from that list (repeats weigh more) and its variants that fit the slot
 *       ({@code minW <= across}, {@code minD <= deep}, {@code minH <= height}) are drawn by weight, four times
 *       the weight for a design the island's biome {@link IslandBiome#likes} (cherry designs on a cherry island,
 *       pines on snow...);</li>
 *   <li>the variant is built into a plot as big as the slot allows (never past its own maximum) facing north,
 *       centred on the origin; {@link dev.t3craft.mapgen.SlotPlacer} turns it to the slot's facing. Buildings
 *       (role centre or module) get a pad of the biome's paving at {@code y = -1} under their plot.</li>
 * </ol>
 * When no family has a variant that fits, the slot stays empty and {@link IslandProps} puts its stand-in there.
 * Block-entity data (signs, banners) travels with the placements.
 */
public final class IslandCatalog implements PieceSource {
	public static final IslandCatalog INSTANCE = new IslandCatalog();

	private static final RingCampusStyle STYLE = new RingCampusStyle();
	private static volatile boolean warned;

	private IslandCatalog() {
	}

	@Override
	public @Nullable Piece pieceFor(Slot slot, MapLayout map) {
		List<Family> families = families(slot);
		if (families.isEmpty()) {
			return null;
		}
		long pick = Noise.mix(map.seed(), slot.origin().getX(), slot.origin().getZ(), 41);
		IslandBiome biome = IslandBiome.ofTags(slot.tags());
		int start = (int) Math.floorMod(pick, (long) families.size());
		Set<Family> tried = EnumSet.noneOf(Family.class);
		for (int k = 0; k < families.size(); k++) {
			Family f = families.get((start + k) % families.size());
			if (!tried.add(f)) {
				continue;
			}
			try {
				Piece p = build(f, slot, biome, pick);
				if (p != null) {
					return p;
				}
			} catch (RuntimeException e) {
				if (!warned) {
					warned = true;
					T3Craft.LOGGER.warn("[islands] a catalog piece of family {} failed for slot {}; stand-ins fill such slots", f, slot.id(), e);
				}
			}
		}
		return null;
	}

	/** The families a slot may hold, repeats weighing more. */
	static List<Family> families(Slot slot) {
		List<Family> out = new ArrayList<>();
		int across = Math.min(slot.across(), slot.deep());
		switch (slot.role()) {
			case CENTRE -> {
				if (across >= 9) {
					out.addAll(List.of(Family.HOUSE, Family.HOUSE, Family.HOUSE));
					if (!slot.has("spawn")) {
						out.addAll(List.of(Family.GAZEBO, Family.PLAZA_CENTER, Family.FOUNTAIN));
					}
				} else {
					out.addAll(List.of(Family.GAZEBO, Family.GAZEBO, Family.FOUNTAIN, Family.WELL, Family.STATUE, Family.GARDEN));
				}
			}
			case MODULE -> {
				if (across >= 9) {
					out.addAll(List.of(Family.HOUSE, Family.HOUSE));
				}
				out.addAll(List.of(Family.ANNEX, Family.ANNEX, Family.STALL, Family.WELL, Family.FOUNTAIN, Family.GARDEN, Family.GAZEBO, Family.STATUE));
			}
			case DECOR -> {
				if (slot.has("accent")) {
					out.addAll(List.of(Family.LAMP, Family.LAMP, Family.PLANTER, Family.BENCH, Family.BANNER_POLE, Family.SIGNPOST));
				} else {
					out.add(Family.TREE);
				}
			}
			default -> {
			}
		}
		return out;
	}

	private static @Nullable Piece build(Family f, Slot slot, IslandBiome biome, long pick) {
		List<Variant> fit = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		int total = 0;
		for (Variant v : Catalog.of(f)) {
			if (v.minW() > slot.across() || v.minD() > slot.deep() || v.minH() > slot.height()) {
				continue;
			}
			int w = v.weight();
			if (w <= 0) {
				continue;
			}
			if (biome.likes(v.id())) {
				w *= 4;
			}
			fit.add(v);
			weights.add(w);
			total += w;
		}
		if (total == 0) {
			return null;
		}
		int r = (int) Math.floorMod(Noise.mix(pick, f.ordinal(), 0, 43), (long) total);
		Variant chosen = fit.get(fit.size() - 1);
		for (int k = 0; k < fit.size(); k++) {
			r -= weights.get(k);
			if (r < 0) {
				chosen = fit.get(k);
				break;
			}
		}
		int w = Math.min(slot.across(), Math.max(chosen.minW(), chosen.maxW()));
		int d = Math.min(slot.deep(), Math.max(chosen.minD(), chosen.maxD()));
		int h = slot.height();
		Plot plot = new Plot(-(w - 1) / 2, 0, -(d - 1) / 2, w, d, h, Direction.NORTH, pick);
		if (!chosen.fits(plot)) {
			return null;
		}
		CitadelCanvas canvas = new CitadelCanvas(STYLE, HqKit.active());
		chosen.build(canvas, plot);

		List<Placement> blocks = new ArrayList<>();
		boolean building = slot.role() == Slot.Role.CENTRE || slot.role() == Slot.Role.MODULE;
		if (building) {
			for (int x = -(w - 1) / 2; x <= w / 2; x++) {
				for (int z = -(d - 1) / 2; z <= d / 2; z++) {
					blocks.add(new Placement(new BlockPos(x, -1, z), Noise.hash(pick, x, z, 44, 4) == 0 ? biome.padAlt : biome.pad));
				}
			}
		}
		List<Long> keys = new ArrayList<>(canvas.cells.size());
		for (Map.Entry<Long, BlockState> e : canvas.cells.entrySet()) {
			if (!e.getValue().isAir() && BlockPos.getY(e.getKey()) >= 0) {
				keys.add(e.getKey());
			}
		}
		// the piece stage's order: solids, then decorations, then hanging blocks top-down; each bottom-up (y, z, x)
		keys.sort((a, b) -> {
			int la = canvas.layers.getOrDefault(a, CitadelCanvas.SOLID);
			int lb = canvas.layers.getOrDefault(b, CitadelCanvas.SOLID);
			if (la != lb) {
				return Integer.compare(la, lb);
			}
			int ya = BlockPos.getY(a);
			int yb = BlockPos.getY(b);
			if (ya != yb) {
				return la == CitadelCanvas.HANGING ? Integer.compare(yb, ya) : Integer.compare(ya, yb);
			}
			int za = BlockPos.getZ(a);
			int zb = BlockPos.getZ(b);
			if (za != zb) {
				return Integer.compare(za, zb);
			}
			return Integer.compare(BlockPos.getX(a), BlockPos.getX(b));
		});
		for (long k : keys) {
			blocks.add(new Placement(BlockPos.of(k), canvas.cells.get(k), canvas.data.get(k)));
		}
		if (blocks.isEmpty()) {
			return null;
		}
		return new Piece("catalog/" + chosen.id(), new Piece.Size(w, d, h), blocks);
	}
}
