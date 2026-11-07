package dev.t3craft.client.npc.behavior;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The no-HQ layout: a provisional office in tidy rows next to where the player stood. Every
 * neighbourhood (environment + project) gets one or more <em>blocks</em> of
 * {@link #BLOCK_COLS}×{@link #BLOCK_ROWS} desks, {@link #SPACING} blocks apart, with an
 * {@link #AISLE} between blocks and a header strip for the neighbourhood sign. All NPCs face −z
 * (towards the header and the board), so their desks (the computer one block in front) line up.
 *
 * <pre>
 *        board (plaza)
 *   [sign A]         [sign B]         [sign C]      ← header strip
 *   A1  A2  A3   |   B1  B2  B3   |   C1            ← SPACING between desks, AISLE between blocks
 *   A4  A5  A6   |   B4           |
 *   A7  A8  A9   |                |
 *   [sign A]  (overflow block when A has more than 9 threads)
 * </pre>
 *
 * <p><b>Deterministic and stable.</b> Cells are assigned once and kept: a fresh layout given the
 * same members always gives the same cells (new members are placed sorted by environment,
 * neighbourhood and id), and later {@link #update}s only fill free cells for new threads and free
 * the cells of removed ones. Adding or removing a thread never moves another one. A new origin
 * starts over.
 *
 * <p>Heights come from a {@link Ground} probe per cell, sampled when the cell is assigned (so the
 * layout follows hills without burying or floating anyone, and costs nothing per tick). Cells with
 * an unknown or implausible height use the origin's height.
 *
 * <p>Only the fallback: {@link LayeredAnchors} puts the HQ's {@link AnchorSource} first. Helpers
 * (subagents) are not members; they follow their parent. Pure logic, client thread, unit-tested.
 */
public final class GridLayout implements AnchorSource {
	/**
	 * Blocks between neighbouring NPCs (x and z). Keeps the one-line nameplates (≈3 blocks wide at
	 * the usual reading distance) from overlapping and leaves room for the desk in front.
	 */
	public static final int SPACING = 4;
	/** Extra blocks of walkway between two neighbourhood blocks. */
	public static final int AISLE = 4;
	public static final int BLOCK_COLS = 3;
	public static final int BLOCK_ROWS = 3;
	public static final int BLOCK_CAPACITY = BLOCK_COLS * BLOCK_ROWS;
	/** Neighbourhood blocks per row before wrapping to the next row of blocks (+z). */
	public static final int BLOCKS_PER_ROW = 3;
	/** Header strip in front (−z) of a block's first desk row, where its sign floats. */
	public static final int HEADER = 3;
	public static final int PITCH_X = BLOCK_COLS * SPACING + AISLE;
	public static final int PITCH_Z = HEADER + BLOCK_ROWS * SPACING + AISLE;
	/** The board stands this far in front (−z) of the first header strip. */
	public static final int BOARD_DEPTH = 6;
	/** Ground heights further than this from the origin's are ignored (cliffs, caves, unloaded). */
	public static final double MAX_STEP = 12;
	/** NPCs face −z: towards their computer, the sign and the board. */
	public static final float YAW = 180f;

	/** What the layout needs to know about one NPC. {@code env} only orders neighbourhoods. */
	public record Member(String id, String groupId, String env) {
		public Member {
			Objects.requireNonNull(id);
			Objects.requireNonNull(groupId);
			env = env == null ? "" : env;
		}
	}

	/** Feet height for standing on column (x, z), or NaN when unknown. */
	@FunctionalInterface
	public interface Ground {
		Ground UNKNOWN = (x, z) -> Double.NaN;

		double y(int x, int z);

		/**
		 * The walkable floor in column (x, z) closest to {@code baseY} (searching up and down by
		 * {@link #MAX_STEP}), so a cell under a tree lands on the grass, not on the canopy.
		 */
		static Ground near(Walkable w, double baseY) {
			int b = (int) Math.floor(baseY + 0.01);
			int max = (int) MAX_STEP;
			return (x, z) -> {
				for (int d = 0; d <= max; d++) {
					double up = w.floor(x, b + d, z);
					if (!Double.isNaN(up)) {
						return up;
					}
					if (d > 0) {
						double down = w.floor(x, b - d, z);
						if (!Double.isNaN(down)) {
							return down;
						}
					}
				}
				return Double.NaN;
			};
		}
	}

	private record Seat(String groupId, int slot, Anchor anchor) {
	}

	private static final class Group {
		final List<Integer> blocks = new ArrayList<>();
		final BitSet slots = new BitSet();
		@Nullable Anchor header;
	}

	private @Nullable Pos origin;
	private final Map<String, Seat> seats = new HashMap<>();
	private final Map<String, Group> groups = new LinkedHashMap<>();
	private final BitSet blocks = new BitSet();

	/**
	 * Brings the layout up to date with {@code members}; returns true when any cell changed. Keeps
	 * every existing member where it is unless {@code origin} differs from the last one.
	 */
	public boolean update(Pos origin, List<Member> members, Ground ground) {
		boolean changed = false;
		Pos o = new Pos(Math.floor(origin.x()) + 0.5, origin.y(), Math.floor(origin.z()) + 0.5);
		if (!o.equals(this.origin)) {
			changed = !seats.isEmpty() || !groups.isEmpty();
			clear();
			this.origin = o;
		}
		Map<String, Member> byId = new HashMap<>();
		for (Member m : members) {
			byId.putIfAbsent(m.id(), m);
		}
		for (String id : new ArrayList<>(seats.keySet())) {
			Seat s = seats.get(id);
			Group g = groups.get(s.groupId());
			// A thread moving to another project counts as removed + added.
			Member now = byId.get(id);
			if (now != null && now.groupId().equals(s.groupId())) {
				continue;
			}
			seats.remove(id);
			g.slots.clear(s.slot());
			if (g.slots.isEmpty()) {
				g.blocks.forEach(blocks::clear);
				groups.remove(s.groupId());
			}
			changed = true;
		}
		List<Member> fresh = new ArrayList<>();
		for (Member m : byId.values()) {
			if (!seats.containsKey(m.id())) {
				fresh.add(m);
			}
		}
		fresh.sort(Comparator.comparing(Member::env).thenComparing(Member::groupId).thenComparing(Member::id));
		for (Member m : fresh) {
			Group g = groups.computeIfAbsent(m.groupId(), k -> new Group());
			int slot = g.slots.nextClearBit(0);
			while (slot >= g.blocks.size() * BLOCK_CAPACITY) {
				int b = blocks.nextClearBit(0);
				blocks.set(b);
				g.blocks.add(b);
				if (g.header == null) {
					Pos h = blockCorner(b).add((BLOCK_COLS - 1) * SPACING / 2.0, 0, -HEADER + 1);
					g.header = new Anchor(grounded(h, ground), YAW);
				}
			}
			g.slots.set(slot);
			Pos p = cell(g, slot);
			seats.put(m.id(), new Seat(m.groupId(), slot, new Anchor(grounded(p, ground), YAW)));
			changed = true;
		}
		return changed;
	}

	/** Forget everything (level change). */
	public void clear() {
		origin = null;
		seats.clear();
		groups.clear();
		blocks.clear();
	}

	/** World position of the first desk (column 0, row 0) of block {@code b}. */
	private Pos blockCorner(int b) {
		Pos o = Objects.requireNonNull(origin);
		return o.add((b % BLOCKS_PER_ROW) * PITCH_X, 0, (b / BLOCKS_PER_ROW) * PITCH_Z + HEADER);
	}

	private Pos cell(Group g, int slot) {
		Pos corner = blockCorner(g.blocks.get(slot / BLOCK_CAPACITY));
		int c = slot % BLOCK_CAPACITY;
		return corner.add((c % BLOCK_COLS) * SPACING, 0, (c / BLOCK_COLS) * SPACING);
	}

	private Pos grounded(Pos p, Ground ground) {
		double base = Objects.requireNonNull(origin).y();
		double y = ground.y(p.blockX(), p.blockZ());
		if (Double.isNaN(y) || Math.abs(y - base) > MAX_STEP) {
			y = base;
		}
		return new Pos(p.x(), y, p.z());
	}

	/** Where the neighbourhood's sign goes (above the middle of its header strip), or null. */
	public @Nullable Pos header(String groupId) {
		Group g = groups.get(groupId);
		return g == null || g.header == null ? null : g.header.pos();
	}

	/** Number of seated NPCs. */
	public int size() {
		return seats.size();
	}

	@Override
	public @Nullable Anchor desk(String npcId, String groupId) {
		Seat s = seats.get(npcId);
		return s == null ? null : s.anchor();
	}

	@Override
	public @Nullable Anchor board() {
		if (origin == null || groups.isEmpty()) {
			return null;
		}
		int cols = Math.min(BLOCKS_PER_ROW, Math.max(1, blocks.length()));
		double width = (cols - 1) * PITCH_X + (BLOCK_COLS - 1) * SPACING;
		// NPCs at the board stand on its +z side and face −z (yaw 180).
		return new Anchor(origin.add(width / 2, 0, -BOARD_DEPTH), YAW);
	}

	@Override
	public @Nullable Anchor home(String groupId) {
		Group g = groups.get(groupId);
		if (g == null || g.blocks.isEmpty()) {
			return null;
		}
		Pos c = blockCorner(g.blocks.get(0)).add((BLOCK_COLS - 1) * SPACING / 2.0, 0, (BLOCK_ROWS - 1) * SPACING / 2.0);
		Anchor h = g.header;
		return new Anchor(new Pos(c.x(), h == null ? c.y() : h.pos().y(), c.z()), YAW);
	}

	@Override
	public double wanderRadius(String groupId) {
		return (BLOCK_COLS - 1) * SPACING / 2.0 + 1;
	}
}
