package dev.t3craft.piece;

import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqEmptyPlan;
import dev.t3craft.hq.HqPlans;
import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.layout.HqLayouts;
import dev.t3craft.hq.lead.LeadOffices;
import dev.t3craft.hq.pack.HqLayoutPacks;
import dev.t3craft.hq.plan.BuildPlan;
import dev.t3craft.hq.plan.Placement;
import dev.t3craft.hq.style.HqKit;
import dev.t3craft.hq.style.HqStyle;
import dev.t3craft.hq.style.HqStyles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * feat/piece-catalog-preview: a part of an HQ plan, cut out by the plan's own construction groups
 * ({@link BuildPlan#groups()}: {@code hub}, {@code cloister}, {@code node_<k>}, {@code lot_<id>}, {@code module_<i>}
 * for the citadel; {@code hub}, {@code portico}, {@code spine_<r>}, {@code module_<i>} for the classic spine HQ). The
 * blocks are exactly the ones the HQ builders place, with the plan's own heights and lighting.
 *
 * <p>The plan itself is generated once per session and kept ({@link Source}); {@code fresh} (the {@code reload}
 * command) generates it again, so a generator edited and hot-swapped in the IDE shows without restarting.
 */
final class PlanPiece implements Piece {
	/** Which plan a piece is cut from. */
	enum Source {
		/** The empty citadel: the hub, the ring of the cloister, the nodes. */
		CITADEL_EMPTY,
		/** The citadel with one small project (a barrio with one hall). */
		BARRIO_SMALL,
		/** The citadel with one big project (13 threads: halls on two floors). */
		BARRIO_LARGE,
		/** The citadel with a lead's own office. */
		LEAD_OFFICE,
		/** The classic spine HQ (hub, portico, spine, wings) with two projects. */
		CLASSIC
	}

	/** Layers under the ground that are kept (the sunken lounge goes down to about -4). */
	private static final int DEEPEST = -8;

	private final String id;
	private final String group;
	private final String title;
	private final String note;
	private final Source source;
	private final Predicate<String> wanted;

	PlanPiece(String id, String group, String title, String note, Source source, Predicate<String> wanted) {
		this.id = id;
		this.group = group;
		this.title = title;
		this.note = note;
		this.source = source;
		this.wanted = wanted;
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public String group() {
		return group;
	}

	@Override
	public String title() {
		return title;
	}

	@Override
	public String note() {
		return note;
	}

	@Override
	public Built build(PieceSpec spec, boolean fresh) {
		BuildPlan plan = Plans.get(source, fresh);
		List<String> used = new ArrayList<>();
		for (String g : plan.groups()) {
			if (wanted.test(g)) {
				used.add(g);
			}
		}
		if (used.isEmpty()) {
			throw new IllegalStateException("the " + source + " plan has no group for " + id + " (groups: " + plan.groups().size() + ")");
		}
		PieceModel.Builder b = PieceModel.builder();
		for (Placement p : plan.placements()) {
			int y = p.rel().getY();
			if (y < DEEPEST || (p.state().isAir() && y != 0)) {
				continue; // air above the ground is nothing; air at the ground layer clears a sunken room
			}
			String g = plan.groupAt(p.rel());
			if (g != null && wanted.test(g)) {
				b.add(p.rel().getX(), y, p.rel().getZ(), p.state(), p.be());
			}
		}
		String groups = used.size() <= 4 ? String.join(", ", used) : used.size() + " groups";
		return new Built(b.build(), "from the " + source.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + " plan (" + groups + "), citadel scale "
				+ RingCampusGeometry.scale() + ", plan " + plan.fingerprint().substring(0, 8));
	}

	/** The plans the pieces are cut from. */
	private static final class Plans {
		private record Cached(String key, BuildPlan plan) {
		}

		private static final Map<Source, Cached> CACHE = new ConcurrentHashMap<>();

		private Plans() {
		}

		static BuildPlan get(Source s, boolean fresh) {
			HqStyle citadel = Objects.requireNonNull(HqStyles.byId(RingCampusGeometry.ID), "citadel style");
			if (s == Source.CITADEL_EMPTY && !fresh) {
				return HqEmptyPlan.of(citadel); // the one the game computes for the HQ world anyway
			}
			String key = HqFixtures.fingerprint() + "|" + HqKit.fingerprint() + "|" + HqLayoutPacks.generation() + "|" + RingCampusGeometry.scale();
			Cached c = CACHE.get(s);
			if (!fresh && c != null && c.key().equals(key)) {
				return c.plan();
			}
			BuildPlan plan = generate(s, citadel);
			CACHE.put(s, new Cached(key, plan));
			return plan;
		}

		private static BuildPlan generate(Source s, HqStyle citadel) {
			return switch (s) {
				case CITADEL_EMPTY -> HqPlans.generate(HqLayout.EMPTY, citadel);
				case BARRIO_SMALL -> plan(project("vps:alpha", "alpha", 4), citadel);
				case BARRIO_LARGE -> plan(project("vps:beta", "beta", 13), citadel);
				case LEAD_OFFICE -> {
					String thread = "vps:gamma-t0";
					HqInput in = HqInput.builder().thread(LeadOffices.projectKey(thread), LeadOffices.label("gamma"), "vps", "VPS", thread).build();
					yield plan(in, citadel);
				}
				case CLASSIC -> plan(twoProjects(), HqStyles.forLayout(HqLayouts.CLASSIC));
			};
		}

		private static HqInput project(String key, String name, int threads) {
			HqInput.Builder b = HqInput.builder();
			for (int t = 0; t < threads; t++) {
				b.thread(key, name, "vps", "VPS", key + "-t" + t);
			}
			return b.build();
		}

		private static HqInput twoProjects() {
			HqInput.Builder b = HqInput.builder();
			for (int t = 0; t < 5; t++) {
				b.thread("vps:alpha", "alpha", "vps", "VPS", "vps:alpha-t" + t);
			}
			for (int t = 0; t < 9; t++) {
				b.thread("laptop:beta", "beta", "laptop", "LAPTOP", "laptop:beta-t" + t);
			}
			return b.build();
		}

		private static BuildPlan plan(HqInput in, HqStyle style) {
			return HqFixtures.active().apply(HqGenerator.plan(in, null, style).plan());
		}
	}
}
