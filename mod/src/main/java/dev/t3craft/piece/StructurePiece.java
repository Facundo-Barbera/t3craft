package dev.t3craft.piece;

import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Room;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelOffice;
import dev.t3craft.hq.plan.Box;
import dev.t3craft.hq.plan.BuildPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Issue #49: one of the big structures of the citadel (the leader's office, a lead's office, the hub, a barrio's gatehouse,
 * a barrio hall), looked at on its own on the piece stage and listed in the piece browser's "Structures" folder. See
 * {@code docs/features/piece-catalog-preview.md}, section "Structures".
 *
 * <p>Nothing is painted again here: each structure is <b>cut from a real plan</b> of the citadel (the empty one, or one with a
 * sample project or a lead, {@link PlanPiece.Source}) by its construction groups and, where the structure has no group of its
 * own, by the columns of its site. So the blocks, block entities and heights are exactly what the HQ builders place; a structure
 * never needs a canvas of its own and the citadel's plan is not touched. The plans are the ones {@code hq_*} pieces already use
 * (one per session, {@code reload} makes them again).
 *
 * <p>Group {@value #GROUP}. Not parametric: the citadel's own scale ({@code LayoutParams.RINGCAMPUS_SCALE}, a layout pack
 * parameter) decides the size; the stage's size, front and seed arguments are accepted and ignored.
 */
public final class StructurePiece implements Piece {
	/** The group of the structures in {@link PieceCatalog} and the commands. */
	public static final String GROUP = "struct";

	/**
	 * A structure's size in blocks: what the stage measured the last time it was built in this session ({@code measured}), or,
	 * before that, an estimate from the citadel's geometry (the footprint of its site and a lower bound of its height).
	 */
	public record Size(int w, int d, int h, boolean measured) {
	}

	/** Sizes measured by {@link #build}, by id, for the session. */
	private static final Map<String, Size> MEASURED = new ConcurrentHashMap<>();

	private final PlanPiece cut;
	private final List<String> tags;
	private final Supplier<Size> estimate;

	private StructurePiece(PlanPiece cut, List<String> tags, Supplier<Size> estimate) {
		this.cut = cut;
		this.tags = List.copyOf(tags);
		this.estimate = estimate;
	}

	/** The structures, in listing order. */
	static List<Piece> all() {
		List<Piece> out = new ArrayList<>();
		Predicate<String> barrio = g -> g.startsWith("lot_") || g.startsWith("module_");
		Predicate<String> hall = g -> g.startsWith("module_");
		out.add(new StructurePiece(
				new PlanPiece("struct_leader_office", GROUP, "Leader's office, the control tower",
						"CitadelOffice: lobby and workshop, archive, control room with the screens, decision podiums and merge station, balcony,"
								+ " garden; cut from the empty citadel (with the promenade deck and the terrace round its site)",
						PlanPiece.Source.CITADEL_EMPTY, g -> g.equals("cloister"), plan -> CitadelOffice.site()),
				List.of("office", "oficina", "leader", "lider", "control", "tower", "torre", "screens", "podium", "merge"),
				() -> {
					Box s = CitadelOffice.site();
					return new Size(s.sizeX(), s.sizeZ(), s.sizeY(), false);
				}));
		out.add(new StructurePiece(
				new PlanPiece("struct_lead_office", GROUP, "A lead's office", "CitadelLeadOffice: the hall of a lead's own project, with its"
						+ " doorstep; at the citadel's scale in use (one floor at the default scale 2, both floors at scale 1)", PlanPiece.Source.LEAD_OFFICE,
						barrio, around(hall, 2)),
				List.of("office", "oficina", "lead", "briefing", "hall", "screen"), () -> hallSize(1)));
		out.add(new StructurePiece(
				new PlanPiece("struct_hub", GROUP, "Hub, the central building", "CitadelHub: the basilica on the podium, group hub of the empty citadel",
						PlanPiece.Source.CITADEL_EMPTY, g -> g.equals("hub")),
				List.of("hub", "basilica", "central", "board", "screens"), StructurePiece::hubSize));
		out.add(new StructurePiece(
				new PlanPiece("struct_barrio_gate", GROUP, "Barrio gatehouse", "BarrioGateBig (scale 2 and up) or BarrioGate: the gatehouse of a"
						+ " barrio with a small project (4 threads), with the first rows of its arm and courtyard", PlanPiece.Source.BARRIO_SMALL,
						g -> g.startsWith("lot_"), StructurePiece::gateSite),
				List.of("gate", "gatehouse", "barrio", "tower", "banner"), StructurePiece::gateSize));
		out.add(new StructurePiece(
				new PlanPiece("struct_barrio_hall", GROUP, "Barrio hall", "HallInterior: the hall of a small project (4 threads) with its desks"
						+ " and roof, with its doorstep", PlanPiece.Source.BARRIO_SMALL, barrio, around(hall, 2)),
				List.of("hall", "barrio", "desks", "workshop", "interior"), () -> hallSize(2)));
		return out;
	}

	// ------------------------------------------------------------------ Piece

	@Override
	public String id() {
		return cut.id();
	}

	@Override
	public String group() {
		return GROUP;
	}

	@Override
	public String title() {
		return cut.title();
	}

	@Override
	public String note() {
		return cut.note();
	}

	@Override
	public Built build(PieceSpec spec, boolean fresh) {
		Built b = cut.build(spec, fresh);
		PieceModel m = b.model();
		if (m.hasSolid()) {
			MEASURED.put(id(), new Size(m.sizeX(), m.sizeZ(), m.sizeY(), true));
		}
		return b;
	}

	// ------------------------------------------------------------------ for the browser

	/** Lower-case search words (English, plus the Spanish words Facundo searches by: {@code oficina}, {@code torre}). */
	public List<String> tags() {
		return tags;
	}

	/** The size measured on the stage this session, else the geometry's estimate (see {@link Size}). */
	public Size size() {
		Size m = MEASURED.get(id());
		if (m != null) {
			return m;
		}
		try {
			return estimate.get();
		} catch (RuntimeException e) {
			return new Size(1, 1, 1, false); // the browser must open even if the geometry cannot be read now
		}
	}

	/** Every structure of {@link PieceCatalog}, for the browser. */
	public static List<StructurePiece> list() {
		List<StructurePiece> out = new ArrayList<>();
		for (Piece p : PieceCatalog.group(GROUP)) {
			if (p instanceof StructurePiece s) {
				out.add(s);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ sites

	/**
	 * The columns of the plan's modules whose group is {@code core}, {@code margin} more on every side: a hall and its doorstep.
	 * A plan with no such module takes everything (the group test still applies).
	 */
	private static Function<BuildPlan, Box> around(Predicate<String> core, int margin) {
		return plan -> {
			Box u = null;
			for (BuildPlan.Module m : plan.modules()) {
				if (core.test(m.group())) {
					u = u == null ? m.box() : u.union(m.box());
				}
			}
			if (u == null) {
				return new Box(-EVERYWHERE, 0, -EVERYWHERE, EVERYWHERE, 0, EVERYWHERE);
			}
			return new Box(u.minX() - margin, u.minY(), u.minZ() - margin, u.maxX() + margin, u.maxY(), u.maxZ() + margin);
		};
	}

	/** Half side of a box that takes every column of a plan. */
	private static final int EVERYWHERE = 1_000_000;

	/** Cells round the gate room taken with it: its walls, the banners and plinth in front of the towers, the lamp posts. */
	private static final int GATE_MARGIN = 3;

	/** The gatehouse of the first barrio of the plan (its {@code lot_<id>} group): the gate room, its walls and a margin. */
	private static Box gateSite(BuildPlan plan) {
		int lot = -1;
		for (String g : plan.groups()) {
			if (g.startsWith("lot_")) {
				try {
					lot = Integer.parseInt(g.substring(4));
					break;
				} catch (NumberFormatException ignored) {
					// not a barrio's group
				}
			}
		}
		if (lot < 0) {
			throw new IllegalStateException("the plan has no barrio (no lot_<id> group)");
		}
		return roomBox(RingCampusGeometry.lot(lot).gate(), GATE_MARGIN);
	}

	/** A room with its walls ({@code u = -1 .. d}, {@code v = -1 .. w}) and {@code margin} more cells all round. */
	private static Box roomBox(Room r, int margin) {
		var a = r.cell(-1 - margin, -1 - margin, 0);
		var b = r.cell(r.d() + margin, r.w() + margin, 0);
		return Box.of(a.getX(), 0, a.getZ(), b.getX(), 0, b.getZ());
	}

	// ------------------------------------------------------------------ estimates (before the first build)

	/** A hall with its walls and doorstep; {@code floors} hall floors of height at least. */
	private static Size hallSize(int floors) {
		Room r = RingCampusGeometry.lot(0).hall(0);
		return new Size(r.w() + 2 + 4, r.d() + 2 + 4, floors * CitadelCanvas.fh(), false);
	}

	private static Size gateSize() {
		Room r = RingCampusGeometry.lot(0).gate();
		return new Size(r.w() + 2 + 2 * GATE_MARGIN, r.d() + 2 + 2 * GATE_MARGIN, CitadelCanvas.fh(), false);
	}

	private static Size hubSize() {
		int side = 2 * (int) Math.ceil(CitadelCanvas.dims().hubOut) + 1;
		return new Size(side, side, CitadelCanvas.y0() + CitadelCanvas.fh(), false);
	}
}
