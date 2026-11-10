package dev.t3craft.piece;

import dev.t3craft.hq.HqKit;
import dev.t3craft.hq.RingCampusStyle;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * feat/piece-catalog-preview: a variant of the citadel catalog ({@code hq.citadel.catalog}: houses, lamps,
 * trees, fountains...) built on its own into a plot of the size asked for, facing the side asked for. The same
 * build the gallery of {@code hqRender --catalog} does, but into the world.
 */
final class VariantPiece implements Piece {
	private final Variant variant;

	VariantPiece(Variant variant) {
		this.variant = variant;
	}

	@Override
	public String id() {
		return variant.id();
	}

	@Override
	public String group() {
		return variant.family().name().toLowerCase(Locale.ROOT);
	}

	@Override
	public String title() {
		String t = variant.id().replace('_', ' ');
		return Character.toUpperCase(t.charAt(0)) + t.substring(1);
	}

	@Override
	public String note() {
		return "catalog variant of the family " + variant.family() + " (hq.citadel.catalog), plot " + variant.minW() + ".." + variant.maxW() + " x "
				+ variant.minD() + ".." + variant.maxD() + " x " + variant.minH() + "+";
	}

	@Override
	public boolean parametric() {
		return true;
	}

	@Override
	public Built build(PieceSpec spec, boolean fresh) {
		int[] box = box(spec.size());
		Plot plot = new Plot(0, 1, 0, box[0], box[1], box[2], spec.front(), spec.seed());
		CitadelCanvas canvas = new CitadelCanvas(new RingCampusStyle(), HqKit.active());
		variant.build(canvas, plot);

		List<Long> keys = new ArrayList<>(canvas.cells.size());
		for (Map.Entry<Long, BlockState> e : canvas.cells.entrySet()) {
			if (!e.getValue().isAir()) {
				keys.add(e.getKey());
			}
		}
		// placement order: solids, then decorations, then hanging blocks top-down; each bottom-up (y, z, x)
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
		PieceModel.Builder b = PieceModel.builder();
		for (long k : keys) {
			b.add(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k), canvas.cells.get(k), canvas.data.get(k));
		}
		String detail = "plot " + box[0] + "x" + box[1] + "x" + box[2] + " (frontage x depth x height), front " + spec.front().getSerializedName() + ", seed "
				+ spec.seed() + (plot.clipped() > 0 ? "; " + plot.clipped() + " write(s) fell outside the plot" : "");
		return new Built(b.build(), detail);
	}

	/** {@code {w, d, h}} for a size text: {@code WxDxH}, or {@code min} / {@code mid} (default) / {@code max} of the variant's range. */
	int[] box(@Nullable String size) {
		if (size != null) {
			int[] exact = PieceSpec.box(size);
			if (exact != null) {
				return exact;
			}
		}
		String mode = size == null ? "mid" : size.toLowerCase(Locale.ROOT);
		double t = mode.equals("min") ? 0.0 : mode.equals("max") ? 1.0 : 0.5;
		int w1 = variant.minW();
		int d1 = variant.minD();
		int h1 = variant.minH();
		int w2 = cap(variant.maxW(), w1);
		int d2 = cap(variant.maxD(), d1);
		int h2 = Math.max(h1, typicalMaxH(variant.family()));
		return new int[] {lerp(w1, w2, t), lerp(d1, d2, t), lerp(h1, h2, t)};
	}

	private static int lerp(int a, int b, double t) {
		return (int) Math.round(a + (b - a) * t);
	}

	/** Upper end of a family's typical frontage and depth (the gallery's rule). */
	private static int cap(int max, int min) {
		return Math.max(min, Math.min(max, 24));
	}

	/** Upper end of each family's typical height (the gallery's rule). */
	private static int typicalMaxH(Family f) {
		return switch (f) {
			case HOUSE -> 18;
			case LAMP -> 6;
			case ANNEX, STALL, BENCH, PLANTER, SIGNPOST, BANNER_POLE -> 8;
			case FOUNTAIN, WELL -> 7;
			case TREE -> 14;
			case GARDEN -> 5;
			case STATUE, GATE, PLAZA_CENTER, TOWER_TOP -> 10;
			case GAZEBO -> 13;
			case BRIDGE -> 6;
			case ROOF -> 8;
		};
	}
}
