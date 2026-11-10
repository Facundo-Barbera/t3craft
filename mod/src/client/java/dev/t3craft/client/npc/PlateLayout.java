package dev.t3craft.client.npc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The declutter pass over the NPC plates (world text, docs/features/world-text.md): which plates
 * show this tick so that at most {@code max} are visible and no two overlap on screen. Pure (no
 * Minecraft classes); {@link Cache} re-runs it only when something moved noticeably.
 *
 * <p>Plates are compared in view-independent angles from the eye (yaw and pitch of the plate's
 * centre, half-extents = half-size / distance), so turning the head never changes the result, only
 * walking or an NPC moving does. Ranking: pinned plates (the open detail panel) first, then the
 * aimed ones (closest to the view ray), then the nearest; ties by id. Greedy: a plate is kept when it
 * overlaps none of the plates kept before it. No entity is created or re-identified here: it only
 * decides which existing plates show.
 */
public final class PlateLayout {
	/** Blocks per font pixel of a name tag / text display at scale 1. */
	public static final double PX = 0.025;
	/** Advance of an average glyph (font pixels). */
	public static final int GLYPH_PX = 6;
	/** Height of a text line (font pixels) incl. padding. */
	public static final int LINE_PX = 10;
	/** Extra margin around each plate (fraction of its size) so plates do not touch. */
	static final double MARGIN = 0.1;
	/** Plates moving less than this (blocks), and an eye moving less than this, reuse the last result. */
	public static final double MOVE_EPS = 0.25;

	private PlateLayout() {
	}

	/**
	 * One plate: its centre in the world, size in blocks, distance to the eye, {@link NpcLabels#aimMiss}
	 * value, and whether it is pinned (always ranked first: the open detail panel).
	 */
	public record Plate(String id, double x, double y, double z, double width, double height, double dist, double aim, boolean pinned) {
		boolean aimed() {
			return aim <= NpcLabels.AIM_KEEP;
		}
	}

	/** Width in blocks of {@code cols} average glyphs at {@code scale}. */
	public static double width(int cols, float scale) {
		return Math.max(1, cols) * GLYPH_PX * PX * scale;
	}

	/** Height in blocks of {@code lines} text lines at {@code scale}. */
	public static double height(int lines, float scale) {
		return Math.max(1, lines) * LINE_PX * PX * scale;
	}

	/** Ranking order: pinned, aimed (best aim first), nearest, id. */
	static final Comparator<Plate> RANK = Comparator.comparing((Plate p) -> !p.pinned())
			.thenComparing(p -> !p.aimed())
			.thenComparingDouble(p -> p.aimed() ? p.aim() : 0)
			.thenComparingDouble(Plate::dist)
			.thenComparing(Plate::id);

	/**
	 * The plates that show, best ranked first.
	 *
	 * @param max       at most this many (0 = no cap)
	 * @param declutter drop a plate that overlaps one kept before it
	 */
	public static List<String> select(List<Plate> plates, double ex, double ey, double ez, int max, boolean declutter) {
		List<Plate> ranked = new ArrayList<>(plates);
		ranked.sort(RANK);
		List<String> out = new ArrayList<>();
		List<double[]> kept = new ArrayList<>();
		for (Plate p : ranked) {
			if (max > 0 && out.size() >= max) {
				break;
			}
			double[] box = angles(p, ex, ey, ez);
			if (declutter && box != null) {
				boolean clash = false;
				for (double[] k : kept) {
					if (overlap(box, k)) {
						clash = true;
						break;
					}
				}
				if (clash) {
					continue;
				}
			}
			if (box != null) {
				kept.add(box);
			}
			out.add(p.id());
		}
		return out;
	}

	/** {yaw, pitch, halfWidth, halfHeight} in radians, or null for a plate at the eye. */
	static double[] angles(Plate p, double ex, double ey, double ez) {
		double dx = p.x() - ex;
		double dy = p.y() - ey;
		double dz = p.z() - ez;
		double horiz = Math.sqrt(dx * dx + dz * dz);
		double d = Math.sqrt(horiz * horiz + dy * dy);
		if (d < 1e-3) {
			return null;
		}
		double yaw = Math.atan2(dz, dx);
		double pitch = Math.atan2(dy, horiz);
		double hw = Math.atan((p.width() * (1 + MARGIN) / 2) / d);
		double hh = Math.atan((p.height() * (1 + MARGIN) / 2) / d);
		return new double[] {yaw, pitch, hw, hh};
	}

	static boolean overlap(double[] a, double[] b) {
		// Near the zenith yaw spreads out: compare yaw in screen terms (scaled by cos(pitch)).
		double dyaw = Math.abs(a[0] - b[0]);
		if (dyaw > Math.PI) {
			dyaw = 2 * Math.PI - dyaw;
		}
		double cos = Math.cos((a[1] + b[1]) / 2);
		return dyaw * cos < a[2] + b[2] && Math.abs(a[1] - b[1]) < a[3] + b[3];
	}

	/**
	 * Keeps the last {@link #select} result and re-runs it only when the candidates, the aimed set, a
	 * plate's size or {@code max}/{@code declutter} changed, or when the eye or a plate moved more than
	 * {@link #MOVE_EPS}. Client thread only.
	 */
	public static final class Cache {
		private final Map<String, Plate> last = new HashMap<>();
		private double ex = Double.NaN;
		private double ey;
		private double ez;
		private int max = -1;
		private boolean declutter;
		private Set<String> result = Set.of();
		private long runs;

		/** Visible plate ids (insertion order = rank). */
		public Set<String> update(List<Plate> plates, double ex, double ey, double ez, int max, boolean declutter) {
			if (!changed(plates, ex, ey, ez, max, declutter)) {
				return result;
			}
			runs++;
			this.ex = ex;
			this.ey = ey;
			this.ez = ez;
			this.max = max;
			this.declutter = declutter;
			last.clear();
			for (Plate p : plates) {
				last.put(p.id(), p);
			}
			result = new LinkedHashSet<>(select(plates, ex, ey, ez, max, declutter));
			return result;
		}

		/** Times the layout was recomputed (debugging: a quiet scene does not recompute). */
		public long runs() {
			return runs;
		}

		public void clear() {
			last.clear();
			ex = Double.NaN;
			result = Set.of();
		}

		private boolean changed(List<Plate> plates, double ex, double ey, double ez, int max, boolean declutter) {
			if (Double.isNaN(this.ex) || max != this.max || declutter != this.declutter || plates.size() != last.size()) {
				return true;
			}
			if (moved(this.ex, this.ey, this.ez, ex, ey, ez)) {
				return true;
			}
			Set<String> seen = new HashSet<>();
			for (Plate p : plates) {
				Plate q = last.get(p.id());
				if (q == null || !seen.add(p.id()) || q.pinned() != p.pinned() || q.aimed() != p.aimed()
						|| Math.abs(q.width() - p.width()) > 1e-6 || Math.abs(q.height() - p.height()) > 1e-6
						|| moved(q.x(), q.y(), q.z(), p.x(), p.y(), p.z())) {
					return true;
				}
			}
			return false;
		}

		private static boolean moved(double ax, double ay, double az, double bx, double by, double bz) {
			double dx = ax - bx;
			double dy = ay - by;
			double dz = az - bz;
			return dx * dx + dy * dy + dz * dz > MOVE_EPS * MOVE_EPS;
		}
	}
}
