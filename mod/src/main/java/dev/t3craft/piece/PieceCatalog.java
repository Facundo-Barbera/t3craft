package dev.t3craft.piece;

import dev.t3craft.hq.citadel.catalog.Catalog;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * feat/piece-catalog-preview: every piece of the game that can be looked at on its own, at real scale, in the HQ
 * world's piece stage (see {@code docs/features/piece-catalog-preview.md}).
 *
 * <ul>
 *   <li><b>hq</b>: parts of the citadel cut from its plan: the hub (the central building), the cloister around it,
 *       the nodes of the first street, a barrio with a small and a big project, a lead's office.</li>
 *   <li><b>struct</b>: the big structures on their own ({@link StructurePiece}, issue #49): the leader's office, a lead's
 *       office, the hub, a barrio's gatehouse, a barrio hall; the piece browser shows them in its "Structures" folder.</li>
 *   <li><b>classic</b>: parts of the classic spine HQ (hub, portico, spine, wings).</li>
 *   <li><b>kit</b>: the HQ kit's blocks.</li>
 *   <li><b>pack</b>: rooms of the active layout packs (the list follows the packs).</li>
 *   <li>one group per family of the citadel catalog (<b>house</b>, <b>lamp</b>, <b>tree</b>...), every variant.</li>
 * </ul>
 * The drop folder ({@code config/t3craft/preview-drop}) stays for schematics from outside the game.
 */
public final class PieceCatalog {
	private static final List<Piece> FIXED = fixed();

	private PieceCatalog() {
	}

	private static List<Piece> fixed() {
		List<Piece> out = new ArrayList<>();
		PlanPiece.Source empty = PlanPiece.Source.CITADEL_EMPTY;
		out.add(new PlanPiece("hq_hub", "hq", "Hub, the central building", "the basilica on the podium: group hub of the empty citadel", empty,
				g -> g.equals("hub")));
		out.add(new PlanPiece("hq_core", "hq", "Core: hub, cloister and archive", "hub + patio, bridges, promenade, ramps and inner street + archive nook",
				empty, g -> g.equals("hub") || g.equals("cloister") || g.equals("archive")));
		out.add(new PlanPiece("hq_cloister", "hq", "Cloister, the grounds round the hub", "bridges, patio, gallery, ramps, terraces and the inner street",
				empty, g -> g.equals("cloister")));
		out.add(new PlanPiece("hq_node_tower", "hq", "Node: review tower", "ring-1 street node 0", empty, g -> g.equals("node_0")));
		out.add(new PlanPiece("hq_node_rotunda", "hq", "Node: schedule rotunda", "ring-1 street node 2", empty, g -> g.equals("node_2")));
		out.add(new PlanPiece("hq_node_archive", "hq", "Node: archive cloister", "ring-1 street node 4 (group archive)", empty, g -> g.equals("archive")));
		out.add(new PlanPiece("hq_node_lounge", "hq", "Node: sunken lounge", "ring-1 street node 6", empty, g -> g.equals("node_6")));
		out.add(new PlanPiece("hq_node_pillar", "hq", "Node: pillar", "ring-1 street node 1 (3, 5 and 7 are the same)", empty, g -> g.equals("node_1")));
		out.add(new PlanPiece("hq_ring_1", "hq", "Ring 1: street and avenues", "the first ring's street, avenues and empty lots (a big piece)", empty,
				g -> g.equals("ring_1")));
		Predicate<String> barrio = g -> g.startsWith("lot_") || g.startsWith("module_");
		out.add(new PlanPiece("hq_barrio_small", "hq", "Barrio with a small project", "courtyard, gatehouse and one hall (4 threads)",
				PlanPiece.Source.BARRIO_SMALL, barrio));
		out.add(new PlanPiece("hq_barrio_large", "hq", "Barrio with a big project", "courtyard, gatehouse and halls on two floors (13 threads)",
				PlanPiece.Source.BARRIO_LARGE, barrio));
		out.add(new PlanPiece("hq_lead_office", "hq", "A lead's office", "the barrio of a lead's own project", PlanPiece.Source.LEAD_OFFICE, barrio));
		out.addAll(StructurePiece.all());

		PlanPiece.Source classic = PlanPiece.Source.CLASSIC;
		out.add(new PlanPiece("classic_hub", "classic", "Classic: hub", "hub of the classic spine HQ", classic, g -> g.equals("hub")));
		out.add(new PlanPiece("classic_archive", "classic", "Classic: archive nook", "archive terminal nook in the hub's south wall", classic,
				g -> g.equals("archive")));
		out.add(new PlanPiece("classic_portico", "classic", "Classic: portico", "entrance of the classic spine HQ", classic, g -> g.equals("portico")));
		out.add(new PlanPiece("classic_spine", "classic", "Classic: spine", "the corridor with its rows and cap", classic, g -> g.startsWith("spine_")));
		out.add(new PlanPiece("classic_wings", "classic", "Classic: project wings", "the modules of two projects (5 and 9 threads)", classic,
				g -> g.startsWith("module_")));
		out.add(new PlanPiece("classic_all", "classic", "Classic: the whole HQ", "everything of the classic HQ for two projects", classic, g -> true));

		out.add(new KitPiece());
		for (Variant v : Catalog.all()) {
			out.add(new VariantPiece(v));
		}
		return List.copyOf(out);
	}

	/** Every piece: the fixed ones, then the rooms of the layout packs active now. */
	public static List<Piece> all() {
		List<Piece> out = new ArrayList<>(FIXED);
		out.addAll(PackPiece.current());
		return out;
	}

	public static @Nullable Piece byId(String id) {
		String k = id.trim().toLowerCase(Locale.ROOT);
		for (Piece p : all()) {
			if (p.id().equals(k)) {
				return p;
			}
		}
		return null;
	}

	/** Every id, for tab completion. */
	public static List<String> ids() {
		List<String> out = new ArrayList<>();
		for (Piece p : all()) {
			out.add(p.id());
		}
		return out;
	}

	/** The groups in listing order: hq, struct, classic, kit, pack, then the catalog families. */
	public static List<String> groups() {
		Set<String> out = new LinkedHashSet<>(List.of("hq", StructurePiece.GROUP, "classic", "kit", "pack"));
		for (Piece p : all()) {
			out.add(p.group());
		}
		return List.copyOf(out);
	}

	/** The pieces of a group, in order (empty for an unknown group). */
	public static List<Piece> group(String group) {
		String k = group.trim().toLowerCase(Locale.ROOT);
		List<Piece> out = new ArrayList<>();
		for (Piece p : all()) {
			if (p.group().equals(k)) {
				out.add(p);
			}
		}
		return out;
	}

	/** How many pieces each group has, in listing order. */
	public static Map<String, Integer> counts() {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (String g : groups()) {
			out.put(g, 0);
		}
		for (Piece p : all()) {
			out.merge(p.group(), 1, Integer::sum);
		}
		return out;
	}

	/** The piece after ({@code step = 1}) or before ({@code -1}) {@code current} in its own group, wrapping round. */
	public static @Nullable Piece step(Piece current, int step) {
		List<Piece> g = group(current.group());
		if (g.isEmpty()) {
			return null;
		}
		int at = 0;
		for (int i = 0; i < g.size(); i++) {
			if (g.get(i).id().equals(current.id())) {
				at = i;
				break;
			}
		}
		return g.get(Math.floorMod(at + step, g.size()));
	}
}
