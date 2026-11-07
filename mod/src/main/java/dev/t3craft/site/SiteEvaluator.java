package dev.t3craft.site;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Evaluates the fixed-footprint site centred on a column and searches for the best one nearby.
 *
 * <p>Site geometry: a {@link SiteLimits#FOOTPRINT}² footprint, a {@link SiteLimits#APRON}-wide
 * blend ring around it, and a box from just below the lowest ground to {@link SiteLimits#HEADROOM}
 * above the pad (or above the highest ground). The pad (floor) height is the median ground top of
 * the footprint, which minimises the blocks cut plus filled.
 *
 * <p>Hard refusals: an unloaded chunk, water or lava in the box (or the ring just outside it, so no
 * fluid can flow in), anything artificial (player or structure blocks) in the box, non-mob entities
 * in the box, relief over {@link SiteLimits#MAX_RELIEF}, a box outside build height or over the
 * volume/chunk limits.
 */
public final class SiteEvaluator {
	private SiteEvaluator() {
	}

	public record Evaluation(int centerX, int centerZ, boolean ok, List<String> problems, @Nullable Box box, Box footprint, int floorY,
			int minTop, int maxTop, int cut, int fill, int trees, double score) {
		public int relief() {
			return maxTop - minTop;
		}

		public String summary() {
			return "pad y=" + floorY + ", relief " + relief() + ", cut " + cut + ", fill " + fill + ", trees " + trees;
		}
	}

	public record Search(@Nullable Evaluation best, int evaluated, Map<String, Integer> rejected) {
	}

	public static Box footprint(int cx, int cz) {
		int h = SiteLimits.FOOTPRINT / 2;
		return new Box(cx - h, 0, cz - h, cx + h, 0, cz + h);
	}

	public static Evaluation evaluate(Columns cols, int cx, int cz) {
		TerrainProbe probe = cols.probe();
		Box fp = footprint(cx, cz);
		List<String> problems = new ArrayList<>();
		int a = SiteLimits.APRON;
		int[] tops = new int[fp.sizeX() * fp.sizeZ()];
		int n = 0;
		int trees = 0;
		boolean unloaded = false;
		int minTopBox = Integer.MAX_VALUE;
		int maxTopBox = Integer.MIN_VALUE;
		String artificial = null;
		String fluid = null;
		// box + apron + one ring outside it
		for (int x = fp.minX() - a - 1; x <= fp.maxX() + a + 1; x++) {
			for (int z = fp.minZ() - a - 1; z <= fp.maxZ() + a + 1; z++) {
				Columns.Column c = cols.at(x, z);
				boolean ring = x < fp.minX() - a || x > fp.maxX() + a || z < fp.minZ() - a || z > fp.maxZ() + a;
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
				minTopBox = Math.min(minTopBox, c.top());
				maxTopBox = Math.max(maxTopBox, c.top());
				if (fp.containsXZ(x, z)) {
					tops[n++] = c.top();
					trees += c.trees();
				}
			}
		}
		if (unloaded) {
			problems.add("part of the area is not loaded (move closer)");
			return new Evaluation(cx, cz, false, problems, null, fp, 0, 0, 0, 0, 0, 0, Double.MAX_VALUE);
		}
		if (fluid != null) {
			problems.add("water/lava nearby: " + fluid);
		}
		if (artificial != null) {
			problems.add("built blocks in the way: " + artificial);
		}
		int[] sorted = Arrays.copyOf(tops, n);
		Arrays.sort(sorted);
		int minTop = sorted[0];
		int maxTop = sorted[n - 1];
		int floorY = sorted[n / 2];
		if (maxTop - minTop > SiteLimits.MAX_RELIEF) {
			problems.add("too uneven: relief " + (maxTop - minTop) + " > " + SiteLimits.MAX_RELIEF);
		}
		int cut = 0;
		int fill = 0;
		for (int t : sorted) {
			if (t > floorY) {
				cut += t - floorY;
			} else if (t < floorY) {
				fill += floorY - t;
			}
		}
		Box box = new Box(fp.minX() - a, Math.min(minTopBox, floorY) - 1, fp.minZ() - a, fp.maxX() + a,
				Math.max(floorY + SiteLimits.HEADROOM, maxTopBox + 2), fp.maxZ() + a);
		if (box.minY() < probe.minBuildY() || box.maxY() > probe.maxBuildY()) {
			problems.add("outside build height");
		}
		if (box.volume() > SiteLimits.MAX_VOLUME) {
			problems.add("area too tall (" + box.sizeY() + " blocks)");
		}
		if (box.chunkCount() > SiteLimits.MAX_CHUNKS) {
			problems.add("touches too many chunks");
		}
		if (problems.isEmpty()) {
			List<String> ents = probe.foreignEntities(box);
			if (!ents.isEmpty()) {
				problems.add("entities in the way: " + String.join(", ", ents.subList(0, Math.min(3, ents.size()))));
			}
		}
		double score = cut + fill + 2.0 * trees + 4.0 * (maxTop - minTop);
		return new Evaluation(cx, cz, problems.isEmpty(), List.copyOf(problems), box, fp, floorY, minTop, maxTop, cut, fill, trees, score);
	}

	/**
	 * Best valid site within {@code radius} of (px, pz), preferring flatter, then nearer. Candidates
	 * whose box would touch {@code avoid} (grown by one) are skipped.
	 */
	public static Search search(Columns cols, int px, int pz, int radius, int step, @Nullable Box avoid) {
		Evaluation best = null;
		double bestScore = Double.MAX_VALUE;
		int evaluated = 0;
		Map<String, Integer> rejected = new LinkedHashMap<>();
		int reach = SiteLimits.FOOTPRINT / 2 + SiteLimits.APRON;
		Box keepOut = avoid == null ? null : avoid.grow(1, 0);
		for (int dx = -radius; dx <= radius; dx += step) {
			for (int dz = -radius; dz <= radius; dz += step) {
				if (dx * dx + dz * dz > radius * radius) {
					continue;
				}
				int cx = px + dx;
				int cz = pz + dz;
				if (keepOut != null && new Box(cx - reach, 0, cz - reach, cx + reach, 0, cz + reach).intersectsXZ(keepOut)) {
					rejected.merge("overlaps the current site", 1, Integer::sum);
					continue;
				}
				Evaluation e = evaluate(cols, cx, cz);
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
