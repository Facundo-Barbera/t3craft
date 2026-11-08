package dev.t3craft.hq;

import static dev.t3craft.hq.HqGeometry.BOARD_HALF;
import static dev.t3craft.hq.HqGeometry.BOARD_Z;
import static dev.t3craft.hq.HqGeometry.DOOR_V1;
import static dev.t3craft.hq.HqGeometry.DOOR_V2;
import static dev.t3craft.hq.HqGeometry.HUB;
import static dev.t3craft.hq.HqGeometry.PORTICO_Z1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Deterministic HQ generator: {@link HqLayout} in, {@link BuildPlan} out. Pure (no world access, no
 * randomness, no clock, no static mutable state); see {@link HqGeometry} for where every module
 * sits and {@link AnchorNames} for what it publishes.
 *
 * <p>Pieces: the hub (the "needs attention" board facing the entrance, the thread kanban wall and
 * the big screen on its side walls, benches facing them, the hologram's free column over the centre,
 * the beacon in the roof lantern, the archive terminal's nook by the entrance), the entrance portico,
 * the spine (one row per pair of wings, a sign over each wing's doorway), one wing per project
 * (inner wall with an open doorway, desk segments, end cap with the module's big screen) and one
 * standing desk per taken slot (monitor, status lamp, a free standing cell in front; vacant slots
 * are bare floor). What fills each piece's cells comes from an {@link HqStyle}; the boxes, anchors,
 * workstations, fixture cells and reserved surfaces are the generator's and the same in every style.
 */
public final class HqGenerator {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private final HqStyle style;

	/** A generator with the active style ({@link HqStyles#active()}). */
	public HqGenerator() {
		this(HqStyles.active());
	}

	public HqGenerator(HqStyle style) {
		this.style = Objects.requireNonNull(style, "style");
	}

	public HqStyle style() {
		return style;
	}

	/** One-shot convenience: allocate (keeping {@code previous}) and generate. */
	public record Result(HqLayout layout, BuildPlan plan) {
	}

	/** {@link #plan(HqInput, HqLayout, HqStyle)} with the active style. */
	public static Result plan(HqInput input, @Nullable HqLayout previous) {
		return plan(input, previous, HqStyles.active());
	}

	public static Result plan(HqInput input, @Nullable HqLayout previous, HqStyle style) {
		HqLayout layout = HqLayout.allocate(input, previous);
		return new Result(layout, new HqGenerator(style).generate(layout));
	}

	public BuildPlan generate(HqLayout layout) {
		PlanBuilder b = new PlanBuilder();
		int rows = layout.rows();
		hub(b, rows > 0, hubBinding(layout));
		b.group(GROUP_ARCHIVE);
		archive(b);
		b.group(null);
		portico(b);
		for (int r = 0; r < rows; r++) {
			spineRow(b, r, layout, layout.moduleAt(2 * r), layout.moduleAt(2 * r + 1));
		}
		if (rows > 0) {
			spineCap(b, rows);
		}
		for (HqLayout.Module w : layout.modules()) {
			b.group(group(w.index()));
			wing(b, layout, w);
		}
		b.group(null);
		return b.build();
	}

	/**
	 * What the hub's big screen shows: the environment with the most modules (ties: the first key), or
	 * nothing while the HQ has no modules. Wire format of the blocks module's bindings ({@code env:<id>}).
	 */
	static String hubBinding(HqLayout layout) {
		java.util.Map<String, Integer> count = new java.util.TreeMap<>();
		for (HqLayout.Module m : layout.modules()) {
			if (!m.envKey().isEmpty()) {
				count.merge(m.envKey(), 1, Integer::sum);
			}
		}
		String best = null;
		for (java.util.Map.Entry<String, Integer> e : count.entrySet()) {
			if (best == null || e.getValue() > count.get(best)) {
				best = e.getKey();
			}
		}
		return best == null ? "" : "env:" + best;
	}

	/** Group of the archive terminal's nook (in the hub's south wall). */
	public static final String GROUP_ARCHIVE = "archive";

	/** Group name of project module {@code index} (see {@link BuildPlan#groups()}). */
	public static String group(int index) {
		return "module_" + index;
	}

	// ------------------------------------------------------------------ hub

	private void hub(PlanBuilder b, boolean northDoor, String hubBinding) {
		try (PlanBuilder.Canvas c = b.module("hub", HqGeometry.hub(), List.of(HqGeometry.archiveNook()))) {
			style.hub(c, northDoor);
			board(b, c);
			// benches facing the side walls' screens: the kanban wall (west) and the big screen (east)
			int bench = 0;
			for (int sx = -1; sx <= 1; sx += 2) {
				Direction look = sx > 0 ? Direction.EAST : Direction.WEST;
				for (int z = -2; z <= 2; z++) {
					BlockPos p = new BlockPos(5 * sx, 1, z);
					c.set(p, style.bench(look.getOpposite()));
					// the resting spot is in front of the seat (the bench stairs have collision), facing the screen
					b.anchor(Anchor.at(AnchorNames.bench(bench++), p.relative(look), look));
				}
			}
			bigScreen(b, c, HqGeometry.hubScreen(), "hub", hubBinding);
			// the thread kanban wall (P3, t3craft:thread_board): bottom-left and top-right anchors, solid stand-in cells
			HqGeometry.Surface wall = HqGeometry.threadWall();
			fill(c, wall);
			b.anchor(Anchor.face(AnchorNames.THREAD_WALL, wall.origin(), wall.facing()));
			b.anchor(Anchor.face(AnchorNames.THREAD_WALL_END, wall.origin().relative(wall.along(), wall.width() - 1).above(wall.height() - 1),
					wall.facing()));
			b.anchor(Anchor.at(AnchorNames.THREAD_WALL_FRONT, wall.anchorCell().relative(wall.facing(), 3).atY(1), wall.facing().getOpposite()));
			// the hologram's column stays open; the beacon crowns the roof lantern over it
			for (int y = 1; y <= HqGeometry.HOLOGRAM_TOP; y++) {
				c.set(0, y, 0, AIR);
			}
			c.set(0, 9, 0, style.beacon());
			int walk = 0;
			for (int wz : new int[] {-2, 0}) {
				for (int sx = -1; sx <= 1; sx += 2) {
					b.anchor(Anchor.at(AnchorNames.walkHub(walk++), new BlockPos(4 * sx, 1, wz), Direction.SOUTH));
				}
			}
			b.anchor(Anchor.at(AnchorNames.HUB, new BlockPos(0, 1, 0), Direction.SOUTH));
			b.anchor(new Anchor(AnchorNames.HOLOGRAM, 0.5, 5.0, 0.5, Direction.SOUTH, new BlockPos(0, 5, 0)));
			b.anchor(Anchor.at(AnchorNames.BEACON, new BlockPos(0, 9, 0), Direction.SOUTH));
			b.anchor(Anchor.at(AnchorNames.ENTRANCE, new BlockPos(0, 1, HUB), Direction.SOUTH));
		}
	}

	/** Writes a reserved surface with the style's screen stand-in (the blocks module swaps the cells). */
	private void fill(PlanBuilder.Canvas c, HqGeometry.Surface s) {
		for (BlockPos p : s.cells()) {
			c.set(p, style.screen());
		}
	}

	/** A large screen (P1, feat/monitor-multiblock): the stand-in cells and its {@link AnchorNames#bigScreen} anchor at the top-left cell. */
	private void bigScreen(PlanBuilder b, PlanBuilder.Canvas c, HqGeometry.Surface s, String key, String binding) {
		fill(c, s);
		b.anchor(Anchor.face(AnchorNames.bigScreen(key, s.width(), s.height(), binding), s.origin().above(s.height() - 1), s.facing()));
	}

	/** The "needs attention" board: the style's frame and face, then the bulbs and the anchors. */
	private void board(PlanBuilder b, PlanBuilder.Canvas c) {
		int z = BOARD_Z;
		style.board(c, new HqStyle.SignText(DyeColor.ORANGE, "", "Needs", "attention", ""));
		int lamp = 0;
		for (int x = -BOARD_HALF; x <= BOARD_HALF; x++) {
			if (Math.floorMod(x + BOARD_HALF, 2) == 0) {
				c.set(x, 5, z, style.statusLamp());
				b.anchor(Anchor.face(AnchorNames.boardLamp(lamp++), new BlockPos(x, 5, z), Direction.SOUTH));
			}
		}
		b.anchor(Anchor.face(AnchorNames.BOARD, new BlockPos(0, 3, z), Direction.SOUTH));
		b.anchor(Anchor.at(AnchorNames.BOARD_FRONT, new BlockPos(0, 1, z + 1), Direction.NORTH));
		// two rows of standing spots, two and three blocks from the board, nearest the centre first
		int slot = 0;
		for (int row = 2; row <= 3; row++) {
			for (int i = 0; i <= 2 * BOARD_HALF; i++) {
				int x = i == 0 ? 0 : (i % 2 == 1 ? (i + 1) / 2 : -(i / 2));
				b.anchor(Anchor.at(AnchorNames.boardSlot(slot++), new BlockPos(x, 1, z + row), Direction.NORTH));
			}
		}
	}

	// ------------------------------------------------------------------ archive

	/**
	 * The archive terminal's nook: the style's alcove, then the terminal (a stand-in the blocks
	 * module swaps for {@code t3craft:archive_terminal} by its anchor) and the anchors.
	 */
	private void archive(PlanBuilder b) {
		try (PlanBuilder.Canvas c = b.module("archive", HqGeometry.archiveNook(), List.of())) {
			style.archiveNook(c);
			BlockPos t = HqGeometry.ARCHIVE_TERMINAL;
			c.set(t, style.archiveTerminal(Direction.NORTH));
			b.anchor(Anchor.face(AnchorNames.ARCHIVE_TERMINAL, t, Direction.NORTH));
			b.anchor(Anchor.at(AnchorNames.ARCHIVE, HqGeometry.ARCHIVE_STAND, Direction.SOUTH));
		}
	}

	// ------------------------------------------------------------------ portico

	private void portico(PlanBuilder b) {
		try (PlanBuilder.Canvas c = b.module("portico", HqGeometry.portico(), List.of())) {
			style.portico(c);
			b.anchor(Anchor.at(AnchorNames.SPAWN, new BlockPos(0, 1, PORTICO_Z1 + 2), Direction.NORTH));
		}
	}

	// ------------------------------------------------------------------ spine

	private void spineRow(PlanBuilder b, int r, HqLayout layout, HqLayout.@Nullable Module east, HqLayout.@Nullable Module west) {
		try (PlanBuilder.Canvas c = b.module("spine_" + r, HqGeometry.spineRow(r), List.of())) {
			style.spineRow(c, r, east == null ? null : wingSign(layout, east), west == null ? null : wingSign(layout, west));
		}
	}

	/** Module sign: project name (two lines), module number when the project has several, environment. */
	private static HqStyle.SignText wingSign(HqLayout layout, HqLayout.Module w) {
		String label = w.label();
		String l1 = label.length() <= SignTags.LINE ? label : label.substring(0, SignTags.LINE);
		String l2 = label.length() <= SignTags.LINE ? "" : label.substring(SignTags.LINE);
		List<HqLayout.Module> mine = layout.modulesOf(w.projectKey());
		int nth = mine.indexOf(w);
		// only annexes are numbered, so a project's first module's sign never changes when it grows
		String l3 = nth > 0 ? "#" + (nth + 1) : "";
		return new HqStyle.SignText(HqStyle.envColor(w), l1, l2, l3, w.envLabel());
	}

	private void spineCap(PlanBuilder b, int rows) {
		Box box = HqGeometry.spineCap(rows);
		try (PlanBuilder.Canvas c = b.module("spine_cap", box, List.of())) {
			style.spineCap(c, box);
		}
	}

	// ------------------------------------------------------------------ wings

	private void wing(PlanBuilder b, HqLayout layout, HqLayout.Module w) {
		int i = w.index();
		DyeColor accent = HqStyle.envColor(w);
		try (PlanBuilder.Canvas c = b.module("wing_" + i + "_inner", HqGeometry.wingInner(i), List.of())) {
			style.wingEndWall(c, i, 0, true);
		}
		for (int s = 0; s < w.segments(); s++) {
			segment(b, i, s, accent, s == w.segments() - 1 ? HqGeometry.moduleScreen(i, w.segments()) : null, "project:" + w.projectKey());
		}
		for (int slot = 0; slot < w.capacity(); slot++) {
			desk(b, i, slot, w.threadAt(slot));
		}
		boolean first = layout.modulesOf(w.projectKey()).get(0) == w;
		Anchor door = Anchor.at(AnchorNames.module(i), new BlockPos(HqGeometry.wx(i, 1), 1, HqGeometry.wz(i, (DOOR_V1 + DOOR_V2) / 2)),
				HqGeometry.outward(i));
		Anchor walk = Anchor.at(AnchorNames.walkModule(i),
				new BlockPos(HqGeometry.wx(i, HqGeometry.segmentU(1) + 1), 1, HqGeometry.wz(i, (DOOR_V1 + DOOR_V2) / 2)), HqGeometry.outward(i));
		b.anchor(door);
		b.anchor(walk);
		if (first) {
			b.anchor(door.renamed(AnchorNames.wing(w.projectKey())));
			b.anchor(walk.renamed(AnchorNames.walk(w.projectKey())));
		}
		try (PlanBuilder.Canvas c = b.module("wing_" + i + "_cap", HqGeometry.wingCap(i, w.segments()), List.of())) {
			style.wingEndWall(c, i, HqGeometry.segmentU(w.segments()), false);
		}
	}

	private void segment(PlanBuilder b, int i, int s, DyeColor accent, HqGeometry.@Nullable Surface screen, String binding) {
		List<Box> holes = new ArrayList<>(2);
		holes.add(HqGeometry.desk(i, 2 * s));
		holes.add(HqGeometry.desk(i, 2 * s + 1));
		try (PlanBuilder.Canvas c = b.module("wing_" + i + "_seg_" + s, HqGeometry.segment(i, s), holes)) {
			style.segment(c, i, s, accent);
			if (screen != null) {
				bigScreen(b, c, screen, "module@" + i, binding);
				for (int k = 0; k < screen.width(); k++) {
					c.set(screen.origin().relative(screen.along(), k).below(), style.screenBase());
				}
			}
		}
	}

	/**
	 * One desk slot: the style's standing desk, then the computer, the status lamp, the free standing
	 * cell and the anchors. A vacant slot is bare floor (fix/hq-stale-stations: no desk, computer, lamp
	 * or workstation until a thread takes it again).
	 */
	private void desk(PlanBuilder b, int i, int slot, @Nullable String threadId) {
		Box box = HqGeometry.desk(i, slot);
		try (PlanBuilder.Canvas c = b.module("wing_" + i + "_desk_" + slot, box, List.of())) {
			Direction f = HqGeometry.deskFacing(slot);
			Direction right = f.getClockWise();
			int cx = HqGeometry.wx(i, HqGeometry.segmentU(slot / 2) + 1);
			BlockPos desk = new BlockPos(cx, 1, HqGeometry.wz(i, HqGeometry.deskV(slot)));
			BlockPos left = desk.relative(right.getOpposite());
			BlockPos rightPos = desk.relative(right);
			BlockPos monitor = desk.above();
			BlockPos lamp = rightPos.above();
			BlockPos stand = new BlockPos(cx, 1, HqGeometry.wz(i, HqGeometry.chairV(slot)));
			style.desk(c, new HqStyle.DeskCells(i, slot, monitor, lamp, left, stand, f, threadId != null));
			if (threadId == null) {
				return;
			}
			c.set(monitor, style.computer(true));
			c.set(lamp, style.statusLamp());
			c.set(stand, AIR);
			c.set(stand.above(), AIR);
			Anchor deskA = Anchor.at(AnchorNames.deskSlot(i, slot), stand, f);
			Anchor screen = Anchor.face(AnchorNames.screenSlot(i, slot), monitor, f.getOpposite());
			b.workstation(new Workstation(i, slot, threadId, deskA, monitor, screen, lamp));
			b.anchor(deskA.renamed(AnchorNames.desk(threadId)));
			b.anchor(screen.renamed(AnchorNames.monitor(threadId)));
			b.anchor(Anchor.face(AnchorNames.lamp(threadId), lamp, f.getOpposite()));
		}
	}
}
