package dev.t3craft.hq;

import static dev.t3craft.hq.HqGeometry.BOARD_HALF;
import static dev.t3craft.hq.HqGeometry.BOARD_Z;
import static dev.t3craft.hq.HqGeometry.DOOR_V1;
import static dev.t3craft.hq.HqGeometry.DOOR_V2;
import static dev.t3craft.hq.HqGeometry.HUB;
import static dev.t3craft.hq.HqGeometry.PORTICO_Z1;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * Deterministic HQ generator: {@link HqLayout} in, {@link BuildPlan} out. Pure (no world access, no
 * randomness, no clock, no static mutable state); see {@link HqGeometry} for where every module
 * sits and {@link AnchorNames} for what it publishes.
 *
 * <p>Pieces: the hub (benches, the "needs attention" board facing the entrance), the archive
 * basement, the entrance portico, the spine (one row per pair of wings, a sign over each wing's
 * doorway), one wing per project (inner wall, desk segments, end cap) and one desk per slot
 * (occupied: computer on, status lamp, anchors; vacant: computer off). What fills each piece's
 * cells comes from an {@link HqStyle}; the boxes, anchors, workstations and fixture cells are the
 * generator's and the same for every style.
 */
public final class HqGenerator {
	private final HqStyle style;

	/** A generator with the active style ({@link HqStyles#active()}). */
	public HqGenerator() {
		this(HqStyles.active());
	}

	public HqGenerator(HqStyle style) {
		this.style = java.util.Objects.requireNonNull(style, "style");
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
		hub(b, rows > 0);
		b.group(GROUP_ARCHIVE);
		archive(b, layout);
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

	/** Group of the archive (basement). */
	public static final String GROUP_ARCHIVE = "archive";

	/** Group name of project module {@code index} (see {@link BuildPlan#groups()}). */
	public static String group(int index) {
		return "module_" + index;
	}

	// ------------------------------------------------------------------ hub

	private void hub(PlanBuilder b, boolean northDoor) {
		try (PlanBuilder.Canvas c = b.module("hub", HqGeometry.hub(), List.of())) {
			style.hub(c, northDoor);
			board(b, c);
			int bench = 0;
			for (int sx = -1; sx <= 1; sx += 2) {
				Direction into = sx > 0 ? Direction.WEST : Direction.EAST;
				for (int z = -2; z <= 2; z++) {
					b.anchor(Anchor.at(AnchorNames.bench(bench++), new BlockPos(7 * sx, 1, z), into));
				}
			}
			int walk = 0;
			for (int wz : new int[] {-2, 0}) {
				for (int sx = -1; sx <= 1; sx += 2) {
					b.anchor(Anchor.at(AnchorNames.walkHub(walk++), new BlockPos(4 * sx, 1, wz), Direction.SOUTH));
				}
			}
			b.anchor(Anchor.at(AnchorNames.HUB, new BlockPos(0, 1, 0), Direction.SOUTH));
			b.anchor(Anchor.at(AnchorNames.ENTRANCE, new BlockPos(0, 1, HUB), Direction.SOUTH));
		}
	}

	/** The "needs attention" board: the style's frame and face, then the bulbs and the anchors. */
	private void board(PlanBuilder b, PlanBuilder.Canvas c) {
		int z = BOARD_Z;
		style.board(c, SignTags.sign(DyeColor.ORANGE, true, "", "Needs", "attention", ""));
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

	/** The basement library: the style's shell and shelves, then each archived thread's computer. */
	private void archive(PlanBuilder b, HqLayout layout) {
		int fy = HqGeometry.BASEMENT_FLOOR;
		try (PlanBuilder.Canvas c = b.module("archive", HqGeometry.basement(), List.of())) {
			style.archive(c);
			List<HqGeometry.ArchiveSlot> slots = HqGeometry.archiveSlots();
			for (HqLayout.Archived a : layout.archive()) {
				HqGeometry.ArchiveSlot slot = slots.get(a.slot());
				c.set(slot.cell(), style.computer(true));
				b.anchor(Anchor.face(AnchorNames.archive(a.threadId()), slot.cell(), slot.facing()));
			}
			b.anchor(Anchor.at(AnchorNames.ARCHIVE, new BlockPos(0, fy + 1, -1), Direction.NORTH));
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
	private static net.minecraft.nbt.CompoundTag wingSign(HqLayout layout, HqLayout.Module w) {
		String label = w.label();
		String l1 = label.length() <= SignTags.LINE ? label : label.substring(0, SignTags.LINE);
		String l2 = label.length() <= SignTags.LINE ? "" : label.substring(SignTags.LINE);
		List<HqLayout.Module> mine = layout.modulesOf(w.projectKey());
		int nth = mine.indexOf(w);
		// only annexes are numbered, so a project's first module's sign never changes when it grows
		String l3 = nth > 0 ? "#" + (nth + 1) : "";
		return SignTags.sign(HqStyle.envColor(w), true, l1, l2, l3, w.envLabel());
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
			segment(b, i, s, accent);
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

	private void segment(PlanBuilder b, int i, int s, DyeColor accent) {
		List<Box> holes = new ArrayList<>(2);
		holes.add(HqGeometry.desk(i, 2 * s));
		holes.add(HqGeometry.desk(i, 2 * s + 1));
		try (PlanBuilder.Canvas c = b.module("wing_" + i + "_seg_" + s, HqGeometry.segment(i, s), holes)) {
			style.segment(c, i, s, accent);
		}
	}

	/** One desk: the style's furniture, then the computer and the status lamp, and the anchors. */
	private void desk(PlanBuilder b, int i, int slot, @Nullable String threadId) {
		Box box = HqGeometry.desk(i, slot);
		try (PlanBuilder.Canvas c = b.module("wing_" + i + "_desk_" + slot, box, List.of())) {
			Direction f = HqGeometry.deskFacing(slot);
			Direction right = f.getClockWise();
			int cx = HqGeometry.wx(i, HqGeometry.segmentU(slot / 2) + 1);
			int dz = HqGeometry.wz(i, HqGeometry.deskV(slot));
			int chz = HqGeometry.wz(i, HqGeometry.chairV(slot));
			BlockPos desk = new BlockPos(cx, 1, dz);
			BlockPos left = desk.relative(right.getOpposite());
			BlockPos rightPos = desk.relative(right);
			BlockPos monitor = desk.above();
			BlockPos lamp = rightPos.above();
			BlockPos seat = new BlockPos(cx, 1, chz);
			style.desk(c, new HqStyle.DeskCells(i, slot, monitor, lamp, left, seat, f, threadId != null));
			c.set(monitor, style.computer(threadId != null));
			c.set(lamp, style.statusLamp());
			Anchor deskA = Anchor.at(AnchorNames.deskSlot(i, slot), seat, f);
			Anchor screen = Anchor.face(AnchorNames.screenSlot(i, slot), monitor, f.getOpposite());
			b.workstation(new Workstation(i, slot, threadId, deskA, monitor, screen, lamp));
			if (threadId != null) {
				b.anchor(deskA.renamed(AnchorNames.desk(threadId)));
				b.anchor(screen.renamed(AnchorNames.monitor(threadId)));
				b.anchor(Anchor.face(AnchorNames.lamp(threadId), lamp, f.getOpposite()));
			}
		}
	}
}
