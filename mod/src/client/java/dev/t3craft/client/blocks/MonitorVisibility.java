// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
// The "drop what nobody looked at for a while" idea follows MonitorFeature's 30 s sweep of unused
// screens (mod/src/client/java/dev/agentcraft/client/monitor/MonitorFeature.java).
package dev.t3craft.client.blocks;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Which monitors deserve a live thread stream right now. Pure (the caller passes the clock and what
 * it measured), client thread only.
 *
 * <p>A monitor <b>acquires</b> when it is within {@link MonitorSettings#subscribeRadius()} and
 * visible. Once acquired it <b>keeps</b> while it stays within radius + {@link
 * MonitorSettings#releaseHysteresis()} and was visible less than {@link
 * MonitorSettings#releaseAfterMs()} ago, so turning around or stepping back and forth at the edge
 * does not churn subscriptions. Anything else <b>releases</b>, and a monitor the caller stops
 * reporting (unloaded, broken) is forgotten.
 *
 * <p>{@link #update} returns the distinct thread keys wanted: two monitors on the same thread give
 * one key, so the caller holds one stream per thread, never one per monitor.
 */
public final class MonitorVisibility {
	/** One loaded monitor as measured this tick. {@code threadKey} null = not bound to a thread. */
	public record Observation(long pos, @Nullable String threadKey, double distance, boolean visible) {
	}

	private static final class Track {
		boolean live;
		long lastVisibleMs;
	}

	private final Map<Long, Track> tracks = new HashMap<>();
	private MonitorSettings settings;

	public MonitorVisibility(MonitorSettings settings) {
		this.settings = settings;
	}

	public void settings(MonitorSettings s) {
		settings = s;
	}

	/** Applies one round of observations; returns the thread keys that should be subscribed. */
	public Set<String> update(Collection<Observation> observations, long nowMs) {
		Set<String> wanted = new LinkedHashSet<>();
		Set<Long> seen = new HashSet<>();
		double acquire = settings.subscribeRadius();
		double keep = acquire + settings.releaseHysteresis();
		for (Observation o : observations) {
			seen.add(o.pos());
			Track t = tracks.computeIfAbsent(o.pos(), k -> new Track());
			if (!settings.liveLog() || o.threadKey() == null) {
				t.live = false;
				continue;
			}
			if (o.visible() && o.distance() <= keep) {
				t.lastVisibleMs = nowMs;
			}
			if (!t.live) {
				t.live = o.visible() && o.distance() <= acquire;
			} else {
				t.live = o.distance() <= keep && nowMs - t.lastVisibleMs <= settings.releaseAfterMs();
			}
			if (t.live) {
				wanted.add(o.threadKey());
			}
		}
		tracks.keySet().retainAll(seen);
		return wanted;
	}

	/** True while the monitor at {@code pos} holds (a share of) a subscription. */
	public boolean live(long pos) {
		Track t = tracks.get(pos);
		return t != null && t.live;
	}

	public int tracked() {
		return tracks.size();
	}

	public void clear() {
		tracks.clear();
	}

	/**
	 * Whether direction {@code (dx,dy,dz)} (camera → target, not normalised) lies within
	 * {@code halfAngleDeg} of the unit view vector {@code (fx,fy,fz)}. A cheap stand-in for the
	 * frustum: callers pass the wider half-angle of the view plus a margin.
	 */
	public static boolean inCone(double dx, double dy, double dz, double fx, double fy, double fz, double halfAngleDeg) {
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1e-6) {
			return true;
		}
		double cos = (dx * fx + dy * fy + dz * fz) / len;
		return cos >= Math.cos(Math.toRadians(Math.min(180, Math.max(0, halfAngleDeg))));
	}

	/** Horizontal half-angle of a view with vertical FOV {@code fovDeg} and {@code aspect} (w/h). */
	public static double horizontalHalfAngle(double fovDeg, double aspect) {
		return Math.toDegrees(Math.atan(Math.tan(Math.toRadians(fovDeg) / 2) * Math.max(aspect, 0.1)));
	}
}
