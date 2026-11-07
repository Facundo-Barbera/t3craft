package dev.t3craft.site;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Evaluates a site for a {@link SiteRequest} at an origin column, and searches for the best one
 * nearby.
 *
 * <p>Geometry: the request's rects are turned into world space around the origin with the
 * entrance facing {@code facing} ({@link SiteFrame}). The <b>envelope</b> (largest footprint the HQ
 * may grow to) plus a {@link SiteLimits#APRON}-wide blend ring is the <b>reserve</b>: all of it must
 * be suitable now so later modules always fit. Only the <b>base</b> module's footprint (+ apron) is
 * leveled at first; its box goes from just below the lowest ground to {@link SiteLimits#HEADROOM}
 * above the pad (or above the highest ground). The pad (floor) height is the median ground top of
 * the whole envelope, so every module shares one level that minimises cut + fill overall.
 *
 * <p>Hard refusals: an unloaded chunk in the reserve, water or lava in the reserve (or the ring just
 * outside it, so no fluid can flow in), anything artificial (player or structure blocks) in the
 * reserve, non-mob entities in the reserve, base relief over {@link SiteLimits#MAX_RELIEF},
 * envelope relief over {@link SiteLimits#MAX_ENVELOPE_RELIEF}, boxes outside build height or over
 * the volume/chunk limits.
 */
public final class SiteEvaluator {
	private SiteEvaluator() {
	}

	/**
	 * @param box the base module's box (snapshotted and leveled on confirm), or null when refused early
	 * @param footprint base footprint (world xz, y = 0)
	 * @param envelope growth envelope (world xz, y = 0)
	 * @param reserve envelope + apron over the full height range, or null when refused early
	 * @param cut blocks cut / filled to level the base now
	 */
	public record Evaluation(int centerX, int centerZ, CellPlan.Facing facing, boolean ok, List<String> problems, @Nullable Box box, Box footprint,
			Box envelope, @Nullable Box reserve, int floorY, int minTop, int maxTop, int envelopeRelief, int cut, int fill, int reserveCut, int reserveFill,
			int trees, double score) {
		public int relief() {
			return maxTop - minTop;
		}

		public CellPlan.Origin origin() {
			return new CellPlan.Origin(centerX, floorY + 1, centerZ, facing);
		}

		public String summary() {
			String s = "pad y=" + floorY + ", relief " + relief() + ", cut " + cut + ", fill " + fill + ", trees " + trees;
			if (!envelope.equals(footprint)) {
				s += "; growth reserve " + envelope.sizeX() + "x" + envelope.sizeZ() + " (relief " + envelopeRelief + ")";
			}
			return s;
		}
	}

	public record Search(@Nullable Evaluation best, int evaluated, Map<String, Integer> rejected) {
	}

	/** The original fixed 15×15 footprint centred on (cx, cz). */
	public static Box footprint(int cx, int cz) {
		int h = SiteLimits.FOOTPRINT / 2;
		return new Box(cx - h, 0, cz - h, cx + h, 0, cz + h);
	}

	public static Evaluation evaluate(Columns cols, int cx, int cz) {
		return evaluate(cols, cx, cz, CellPlan.Facing.NORTH, SiteRequest.SQUARE);
	}

	public static Evaluation evaluate(Columns cols, int cx, int cz, CellPlan.Facing facing, SiteRequest request) {
		TerrainProbe probe = cols.probe();
		CellPlan.Origin o0 = new CellPlan.Origin(cx, 0, cz, facing);
		Box fp = request.base().rect().toWorld(o0, 0);
		Box env = request.envelope().toWorld(o0, 0);
		List<String> problems = new ArrayList<>();
		int a = SiteLimits.APRON;
		int[] envTops = new int[env.sizeX() * env.sizeZ()];
		int[] fpTops = new int[fp.sizeX() * fp.sizeZ()];
		int ne = 0;
		int nf = 0;
		int trees = 0;
		boolean unloaded = false;
		int minAll = Integer.MAX_VALUE;
		int maxAll = Integer.MIN_VALUE;
		int minBase = Integer.MAX_VALUE;
		int maxBase = Integer.MIN_VALUE;
		String artificial = null;
		String fluid = null;
		// reserve (envelope + apron) + one ring outside it
		for (int x = env.minX() - a - 1; x <= env.maxX() + a + 1; x++) {
			for (int z = env.minZ() - a - 1; z <= env.maxZ() + a + 1; z++) {
				Columns.Column c = cols.at(x, z);
				boolean ring = x < env.minX() - a || x > env.maxX() + a || z < env.minZ() - a || z > env.maxZ() + a;
				if (!c.loaded()) {
					if (!ring) {
						unloaded = true;
					}
					continue;
				}
				if (c.fluid() != null && fluid == null) {
					fluid = c.fluid().name().toLowerCase() + " at " + x + " " + c.top() + " " + z;
				}
				if (ring) {
					continue;
				}
				if (c.artificial() != null && artificial == null) {
					artificial = c.artificial();
				}
				minAll = Math.min(minAll, c.top());
				maxAll = Math.max(maxAll, c.top());
				boolean baseBox = x >= fp.minX() - a && x <= fp.maxX() + a && z >= fp.minZ() - a && z <= fp.maxZ() + a;
				if (baseBox) {
					minBase = Math.min(minBase, c.top());
					maxBase = Math.max(maxBase, c.top());
				}
				if (env.containsXZ(x, z)) {
					envTops[ne++] = c.top();
				}
				if (fp.containsXZ(x, z)) {
					fpTops[nf++] = c.top();
					trees += c.trees();
				}
			}
		}
		if (unloaded) {
			problems.add("part of the area is not loaded (move closer)");
			return new Evaluation(cx, cz, facing, false, problems, null, fp, env, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, Double.MAX_VALUE);
		}
		if (fluid != null) {
			problems.add("water/lava nearby: " + fluid);
		}
		if (artificial != null) {
			problems.add("built blocks in the way: " + artificial);
		}
		int[] envSorted = Arrays.copyOf(envTops, ne);
		Arrays.sort(envSorted);
		int[] fpSorted = Arrays.copyOf(fpTops, nf);
		Arrays.sort(fpSorted);
		int floorY = envSorted[ne / 2];
		int minTop = fpSorted[0];
		int maxTop = fpSorted[nf - 1];
		int envRelief = envSorted[ne - 1] - envSorted[0];
		if (maxTop - minTop > SiteLimits.MAX_RELIEF) {
			problems.add("too uneven: relief " + (maxTop - minTop) + " > " + SiteLimits.MAX_RELIEF);
		} else if (envRelief > SiteLimits.MAX_ENVELOPE_RELIEF) {
			problems.add("too uneven to grow: relief " + envRelief + " > " + SiteLimits.MAX_ENVELOPE_RELIEF + " in the growth reserve");
		}
		int[] cf = cutFill(fpSorted, floorY);
		int[] rcf = cutFill(envSorted, floorY);
		Box box = new Box(fp.minX() - a, Math.min(minBase, floorY) - 1, fp.minZ() - a, fp.maxX() + a,
				Math.max(floorY + SiteLimits.HEADROOM, maxBase + 2), fp.maxZ() + a);
		Box reserve = new Box(env.minX() - a, Math.min(minAll, floorY) - 1, env.minZ() - a, env.maxX() + a,
				Math.max(floorY + SiteLimits.HEADROOM, maxAll + 2), env.maxZ() + a);
		if (reserve.minY() < probe.minBuildY() || reserve.maxY() > probe.maxBuildY()) {
			problems.add("outside build height");
		}
		if (box.volume() > SiteLimits.MAX_VOLUME) {
			problems.add("area too tall (" + box.sizeY() + " blocks)");
		}
		if (box.chunkCount() > SiteLimits.MAX_CHUNKS || reserve.chunkCount() > SiteLimits.MAX_RESERVE_CHUNKS) {
			problems.add("touches too many chunks");
		}
		if (problems.isEmpty()) {
			List<String> ents = probe.foreignEntities(reserve);
			if (!ents.isEmpty()) {
				problems.add("entities in the way: " + String.join(", ", ents.subList(0, Math.min(3, ents.size()))));
			}
		}
		double score = cf[0] + cf[1] + 0.25 * (rcf[0] + rcf[1]) + 2.0 * trees + 4.0 * (maxTop - minTop) + envRelief;
		return new Evaluation(cx, cz, facing, problems.isEmpty(), List.copyOf(problems), box, fp, env, reserve, floorY, minTop, maxTop, envRelief,
				cf[0], cf[1], rcf[0], rcf[1], trees, score);
	}

	private static int[] cutFill(int[] tops, int floorY) {
		int cut = 0;
		int fill = 0;
		for (int t : tops) {
			if (t > floorY) {
				cut += t - floorY;
			} else if (t < floorY) {
				fill += floorY - t;
			}
		}
		return new int[] {cut, fill};
	}

	public static Search search(Columns cols, int px, int pz, int radius, int step, @Nullable Box avoid) {
		return search(cols, px, pz, radius, step, avoid, SiteRequest.SQUARE);
	}

	/**
	 * Best valid site within {@code radius} of (px, pz), preferring flatter, then nearer. Each
	 * candidate's entrance faces (px, pz). Candidates whose reserve would touch {@code avoid} (grown
	 * by one) are skipped.
	 */
	public static Search search(Columns cols, int px, int pz, int radius, int step, @Nullable Box avoid, SiteRequest request) {
		Evaluation best = null;
		double bestScore = Double.MAX_VALUE;
		int evaluated = 0;
		Map<String, Integer> rejected = new LinkedHashMap<>();
		Box keepOut = avoid == null ? null : avoid.grow(1, 0);
		for (int dx = -radius; dx <= radius; dx += step) {
			for (int dz = -radius; dz <= radius; dz += step) {
				if (dx * dx + dz * dz > radius * radius) {
					continue;
				}
				int cx = px + dx;
				int cz = pz + dz;
				CellPlan.Facing facing = CellPlan.Facing.towards(px - cx, pz - cz);
				if (keepOut != null) {
					Box env = request.envelope().toWorld(new CellPlan.Origin(cx, 0, cz, facing), 0).grow(SiteLimits.APRON, 0);
					if (env.intersectsXZ(keepOut)) {
						rejected.merge("overlaps the current site", 1, Integer::sum);
						continue;
					}
				}
				Evaluation e = evaluate(cols, cx, cz, facing, request);
				evaluated++;
				if (!e.ok()) {
					String key = e.problems().get(0).replaceAll(":.*", "");
					rejected.merge(key, 1, Integer::sum);
					continue;
				}
				double s = e.score() + 0.25 * Math.sqrt(dx * dx + dz * dz);
				if (s < bestScore) {
					bestScore = s;
					best = e;
				}
			}
		}
		return new Search(best, evaluated, rejected);
	}
}
