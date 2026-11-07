package dev.t3craft.client.npc.behavior;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The no-base layout (what {@code NpcRegistry} has always drawn): neighbourhood plots on a grid next
 * to {@code origin} (most urgent neighbourhood first), each NPC on a ring inside its plot facing the
 * centre. That ring spot is its "desk"; the plot centre is its home; the central board is a plaza
 * just in front of the first row of plots. Immutable; rebuild with {@link #of} when the NPC set or
 * origin changes.
 */
public final class RingLayout implements AnchorSource {
	public static final double PLOT = 12.0;
	public static final int PLOTS_PER_ROW = 3;

	/** What the layout needs to know about one NPC. */
	public record Member(String id, String groupId, int priority) {
	}

	private final Map<String, Anchor> desks = new HashMap<>();
	private final Map<String, Anchor> homes = new HashMap<>();
	private final @Nullable Anchor board;

	private RingLayout(Pos origin, List<Member> members) {
		Map<String, List<Member>> groups = new LinkedHashMap<>();
		List<Member> ordered = new ArrayList<>(members);
		ordered.sort(Comparator.comparingInt(Member::priority).thenComparing(Member::id));
		for (Member m : ordered) {
			groups.computeIfAbsent(m.groupId(), k -> new ArrayList<>()).add(m);
		}
		int g = 0;
		for (Map.Entry<String, List<Member>> group : groups.entrySet()) {
			List<Member> ms = group.getValue();
			Pos center = origin.add((g % PLOTS_PER_ROW) * PLOT, 0, (g / PLOTS_PER_ROW) * PLOT);
			g++;
			homes.put(group.getKey(), new Anchor(center, 0f));
			double radius = ringRadius(ms.size());
			for (int i = 0; i < ms.size(); i++) {
				double angle = (Math.PI * 2 * i) / Math.max(1, ms.size());
				Pos p = new Pos(center.x() + Math.cos(angle) * radius, center.y(), center.z() + Math.sin(angle) * radius);
				desks.put(ms.get(i).id(), new Anchor(p, p.yawTo(center)));
			}
		}
		if (groups.isEmpty()) {
			board = null;
		} else {
			int cols = Math.min(PLOTS_PER_ROW, groups.size());
			// NPCs at the board stand on its +z side and face -z (yaw 180).
			board = new Anchor(origin.add((cols - 1) * PLOT / 2, 0, -PLOT / 2 - 3), 180f);
		}
	}

	public static RingLayout of(Pos origin, List<Member> members) {
		return new RingLayout(origin, members);
	}

	/** Same radius rule as the original registry layout. */
	public static double ringRadius(int members) {
		return Math.max(2.0, Math.min(PLOT / 2 - 1, members * 0.55));
	}

	@Override
	public @Nullable Anchor desk(String npcId, String groupId) {
		return desks.get(npcId);
	}

	@Override
	public @Nullable Anchor board() {
		return board;
	}

	@Override
	public @Nullable Anchor home(String groupId) {
		return homes.get(groupId);
	}

	@Override
	public double wanderRadius(String groupId) {
		return PLOT / 2 - 1.5;
	}
}
