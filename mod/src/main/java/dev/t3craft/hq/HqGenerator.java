package dev.t3craft.hq;

import static dev.t3craft.hq.HqGeometry.BOARD_HALF;
import static dev.t3craft.hq.HqGeometry.BOARD_Z;
import static dev.t3craft.hq.HqGeometry.DOOR_V1;
import static dev.t3craft.hq.HqGeometry.DOOR_V2;
import static dev.t3craft.hq.HqGeometry.HUB;
import static dev.t3craft.hq.HqGeometry.PORTICO_X;
import static dev.t3craft.hq.HqGeometry.PORTICO_Z1;
import static dev.t3craft.hq.HqGeometry.PORTICO_Z2;
import static dev.t3craft.hq.HqGeometry.ROW;
import static dev.t3craft.hq.HqGeometry.SEGMENT;
import static dev.t3craft.hq.HqGeometry.SPINE;
import static dev.t3craft.hq.HqGeometry.WING_WALL;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Deterministic HQ generator: {@link HqLayout} in, {@link BuildPlan} out. Pure (no world access, no
 * randomness, no clock, no static mutable state); see {@link HqGeometry} for where every module
 * sits and {@link AnchorNames} for what it publishes.
 *
 * <p>Pieces: the hub (copper-roofed timber hall with a glazed lantern, benches, the "needs
 * attention" board facing the entrance), the entrance portico, the spine (glass-roofed gallery,
 * one row per pair of wings, a sign over each wing's doorway), one wing per project (inner wall,
 * desk segments, end cap) and one desk per slot (occupied: black monitor, status lamp, anchors;
 * vacant: grey monitor).
 */
public final class HqGenerator {
	private final Palette k = new Palette();

	/** One-shot convenience: allocate (keeping {@code previous}) and generate. */
	public record Result(HqLayout layout, BuildPlan plan) {
	}

	public static Result plan(HqInput input, @Nullable HqLayout previous) {
		HqLayout layout = HqLayout.allocate(input, previous);
		return new Result(layout, new HqGenerator().generate(layout));
	}

	public BuildPlan generate(HqLayout layout) {
		PlanBuilder b = new PlanBuilder();
		int rows = layout.rows();
		hub(b, rows > 0);
		portico(b);
		for (int r = 0; r < rows; r++) {
			spineRow(b, r, layout.wingAt(2 * r), layout.wingAt(2 * r + 1));
		}
		if (rows > 0) {
			spineCap(b, rows);
		}
		for (HqLayout.Wing w : layout.wings()) {
			wing(b, w);
		}
		return b.build();
	}

	// ------------------------------------------------------------------ hub

	private void hub(PlanBuilder b, boolean northDoor) {
		try (PlanBuilder.Canvas c = b.module("hub", HqGeometry.hub(), List.of())) {
			for (int z = -HUB; z <= HUB; z++) {
				for (int x = -HUB; x <= HUB; x++) {
					int m = Math.max(Math.abs(x), Math.abs(z));
					boolean door = Math.abs(x) <= 1 && (z == HUB || (northDoor && z == -HUB));
					// floor: tuff under the walls, andesite border, copper ring, spruce centre, copper rose
					BlockState floor = m == HUB ? k.plinth : m == 7 ? k.stoneFloor : m == 6 ? k.copper
							: Math.abs(x) <= 1 && Math.abs(z) <= 1 ? k.copperInlay : k.woodFloor;
					c.set(x, 0, z, door ? k.stoneFloor : floor);
					if (m == HUB) {
						hubWall(c, x, z, door);
					}
					// roof: two copper eave steps, flat copper terrace, glazed lantern over the light well
					if (m == HUB) {
						c.set(x, 6, z, k.copperRoof(inward(x, z)));
					} else if (m > 2) {
						c.set(x, 6, z, k.darkPlanks);
					}
					if (m == 7) {
						c.set(x, 7, z, k.copperRoof(inward(x, z)));
					} else if (m >= 3 && m <= 6) {
						c.set(x, 7, z, k.copperSlab);
					} else if (m == 2) {
						BlockState lw = Math.abs(x) == 2 && Math.abs(z) == 2 ? k.copper : k.glass;
						c.set(x, 7, z, lw);
						c.set(x, 8, z, lw);
					}
					if (m <= 2) {
						c.set(x, 9, z, x == 0 && z == 0 ? k.copper : k.copperSlab);
					}
				}
			}
			c.decor(0, 10, 0, k.lightningRod);
			// chandelier hanging from the lantern cap into the light well
			c.hang(0, 8, 0, k.chain);
			c.hang(0, 7, 0, k.chain);
			c.hang(0, 6, 0, k.copperLantern(true));
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					c.hang(5 * sx, 5, 5 * sz, k.lantern(true));
					// reading corners
					c.set(7 * sx, 1, 7 * sz, k.bookshelf);
					c.set(7 * sx, 2, 7 * sz, k.bookshelf);
					c.decor(7 * sx, 3, 7 * sz, k.fern);
				}
				// benches along the side walls, planters at their ends
				Direction wall = sx > 0 ? Direction.EAST : Direction.WEST;
				for (int z = -2; z <= 2; z++) {
					c.set(7 * sx, 1, z, k.bench(wall));
				}
				c.decor(7 * sx, 1, -3, k.pottedFloweringAzalea);
				c.decor(7 * sx, 1, 3, k.pottedFloweringAzalea);
				// lamp pedestals by both doorways
				for (int dz = -1; dz <= 1; dz += 2) {
					c.set(2 * sx, 1, 7 * dz, k.copperGrate);
					c.decor(2 * sx, 2, 7 * dz, k.copperLantern(false));
				}
			}
			// runner from the entrance to the board
			for (int z = 2; z <= 7; z++) {
				for (int x = -1; x <= 1; x++) {
					c.decor(x, 1, z, k.carpet(DyeColor.CYAN));
				}
			}
			board(b, c);
			b.anchor(Anchor.at(AnchorNames.HUB, new BlockPos(0, 1, 0), Direction.SOUTH));
			b.anchor(Anchor.at(AnchorNames.ENTRANCE, new BlockPos(0, 1, HUB), Direction.SOUTH));
		}
	}

	private void hubWall(PlanBuilder.Canvas c, int x, int z, boolean door) {
		boolean ns = Math.abs(z) == HUB;
		int t = ns ? x : z;
		boolean post = Math.abs(t) == HUB || Math.abs(t) == 5 || Math.abs(t) == 2;
		Direction.Axis along = ns ? Direction.Axis.X : Direction.Axis.Z;
		for (int y = 1; y <= 5; y++) {
			BlockState s;
			if (post) {
				s = k.post();
			} else if (y == 5) {
				s = k.beam(along);
			} else if (door) {
				s = y == 4 ? k.beam(along) : k.air;
			} else {
				s = y == 1 ? k.plinth : y == 4 ? k.infill : k.pane;
			}
			c.set(x, y, z, s);
		}
	}

	/** The "needs attention" board: a dark slate panel framed in timber with copper signal bulbs. */
	private void board(PlanBuilder b, PlanBuilder.Canvas c) {
		int z = BOARD_Z;
		for (int y = 1; y <= 5; y++) {
			c.set(-BOARD_HALF - 1, y, z, k.post());
			c.set(BOARD_HALF + 1, y, z, k.post());
		}
		int lamp = 0;
		for (int x = -BOARD_HALF; x <= BOARD_HALF; x++) {
			c.set(x, 1, z, k.beam(Direction.Axis.X));
			for (int y = 2; y <= 4; y++) {
				c.set(x, y, z, k.boardFace);
			}
			boolean bulb = Math.floorMod(x + BOARD_HALF, 2) == 0;
			c.set(x, 5, z, bulb ? k.lamp() : k.copper);
			if (bulb) {
				b.anchor(Anchor.face(AnchorNames.boardLamp(lamp++), new BlockPos(x, 5, z), Direction.SOUTH));
			}
		}
		c.decor(new BlockPos(0, 5, z + 1), k.wallSign(Direction.SOUTH), SignTags.sign(DyeColor.ORANGE, true, "", "Needs", "attention", ""));
		b.anchor(Anchor.face(AnchorNames.BOARD, new BlockPos(0, 3, z), Direction.SOUTH));
	}

	/** Direction from an edge cell towards the centre (the tall side of a roof step). */
	private static Direction inward(int x, int z) {
		if (z == -Math.max(Math.abs(x), Math.abs(z))) {
			return Direction.SOUTH;
		}
		if (z == Math.max(Math.abs(x), Math.abs(z))) {
			return Direction.NORTH;
		}
		return x < 0 ? Direction.EAST : Direction.WEST;
	}

	// ------------------------------------------------------------------ portico

	private void portico(PlanBuilder b) {
		try (PlanBuilder.Canvas c = b.module("portico", HqGeometry.portico(), List.of())) {
			for (int z = PORTICO_Z1; z <= PORTICO_Z2; z++) {
				for (int x = -PORTICO_X; x <= PORTICO_X; x++) {
					boolean deck = Math.abs(x) <= 3 && z <= PORTICO_Z2 - 1;
					BlockState g = deck ? (Math.abs(x) == 3 ? k.plinth : k.stoneFloor) : Math.abs(x) <= 1 ? k.dirtPath : k.grass;
					c.set(x, 0, z, g);
					c.set(x, 5, z, Math.abs(x) == PORTICO_X || z == PORTICO_Z2 ? k.copperSlab : k.copper);
					if (Math.abs(x) <= 2 && z <= PORTICO_Z2 - 2) {
						c.set(x, 6, z, k.copperSlab);
					}
				}
			}
			int front = PORTICO_Z2 - 1;
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int y = 1; y <= 4; y++) {
					c.set(3 * sx, y, front, k.post());
					c.set(3 * sx, y, PORTICO_Z1, k.post());
				}
				for (int z = PORTICO_Z1 + 1; z < front; z++) {
					c.set(3 * sx, 4, z, k.beam(Direction.Axis.Z));
				}
				c.decor(4 * sx, 1, PORTICO_Z1 + 1, k.floweringAzalea);
				c.decor(4 * sx, 1, front, k.azalea);
				c.set(2 * sx, 1, PORTICO_Z2, k.plinthWall);
				c.decor(2 * sx, 2, PORTICO_Z2, k.copperLantern(false));
			}
			for (int x = -2; x <= 2; x++) {
				c.set(x, 4, front, k.beam(Direction.Axis.X));
				c.set(x, 4, PORTICO_Z1, k.beam(Direction.Axis.X));
			}
			c.hang(0, 4, PORTICO_Z1 + 2, k.copperLantern(true));
			b.anchor(Anchor.at(AnchorNames.SPAWN, new BlockPos(0, 1, PORTICO_Z1 + 2), Direction.NORTH));
		}
	}

	// ------------------------------------------------------------------ spine

	private void spineRow(PlanBuilder b, int r, HqLayout.@Nullable Wing east, HqLayout.@Nullable Wing west) {
		int zn = HqGeometry.rowNorth(r);
		try (PlanBuilder.Canvas c = b.module("spine_" + r, HqGeometry.spineRow(r), List.of())) {
			for (int v = 0; v < ROW; v++) {
				int z = zn + v;
				boolean beamLine = v == 0 || v == 4 || v == 8 || v == ROW - 1;
				for (int x = -SPINE; x <= SPINE; x++) {
					int ax = Math.abs(x);
					HqLayout.Wing w = x > 0 ? east : west;
					boolean door = ax == SPINE && w != null && v >= DOOR_V1 && v <= DOOR_V2;
					BlockState floor = ax == SPINE ? (door ? k.woodFloor : k.plinth) : ax == 2 ? k.darkPlanks
							: x == 0 && v % 4 == 2 ? k.copperInlay : k.woodFloor;
					c.set(x, 0, z, floor);
					if (ax == SPINE) {
						for (int y = 1; y <= 4; y++) {
							BlockState s;
							if (beamLine) {
								s = k.post();
							} else if (y == 4) {
								s = k.beam(Direction.Axis.Z);
							} else if (door) {
								s = k.air;
							} else {
								s = y == 1 ? k.plinth : w != null ? k.infill : k.pane;
							}
							c.set(x, y, z, s);
						}
						c.set(x, 5, z, k.darkPlanks);
						c.set(x, 6, z, k.copperSlab);
					} else {
						c.set(x, 5, z, beamLine ? k.beam(Direction.Axis.X) : k.glass);
					}
				}
				if (v == 4 || v == 8) {
					c.hang(0, 4, z, k.copperLantern(true));
				}
				if (v == 1 || v == ROW - 2) {
					c.decor(-2, 1, z, k.fern);
					c.decor(2, 1, z, k.fern);
				}
			}
			int zs = zn + (DOOR_V1 + DOOR_V2) / 2;
			if (east != null) {
				c.decor(new BlockPos(2, 4, zs), k.wallSign(Direction.WEST), wingSign(east));
			}
			if (west != null) {
				c.decor(new BlockPos(-2, 4, zs), k.wallSign(Direction.EAST), wingSign(west));
			}
		}
	}

	private net.minecraft.nbt.CompoundTag wingSign(HqLayout.Wing w) {
		String label = w.label();
		String l1 = label.length() <= SignTags.LINE ? label : label.substring(0, SignTags.LINE);
		String l2 = label.length() <= SignTags.LINE ? "" : label.substring(SignTags.LINE);
		return SignTags.sign(DyeColor.WHITE, true, l1, l2, "", w.envLabel());
	}

	private void spineCap(PlanBuilder b, int rows) {
		Box box = HqGeometry.spineCap(rows);
		int z = box.minZ();
		try (PlanBuilder.Canvas c = b.module("spine_cap", box, List.of())) {
			for (int x = -SPINE; x <= SPINE; x++) {
				int ax = Math.abs(x);
				c.set(x, 0, z, k.plinth);
				for (int y = 1; y <= 4; y++) {
					c.set(x, y, z, ax == SPINE ? k.post() : y == 4 ? k.beam(Direction.Axis.X) : y == 1 ? k.plinth : ax <= 1 ? k.pane : k.infill);
				}
				c.set(x, 5, z, k.darkPlanks);
				c.set(x, 6, z, k.copperSlab);
			}
		}
	}

	// ------------------------------------------------------------------ wings

	private void wing(PlanBuilder b, HqLayout.Wing w) {
		int i = w.index();
		DyeColor accent = k.accent(w.projectKey());
		try (PlanBuilder.Canvas c = b.module("wing_" + i + "_inner", HqGeometry.wingInner(i), List.of())) {
			wingEndWall(c, i, 0, true);
		}
		for (int s = 0; s < w.segments(); s++) {
			segment(b, i, s, accent);
		}
		for (int slot = 0; slot < w.capacity(); slot++) {
			desk(b, i, slot, w.threadAt(slot));
		}
		try (PlanBuilder.Canvas c = b.module("wing_" + i + "_cap", HqGeometry.wingCap(i, w.segments()), List.of())) {
			wingEndWall(c, i, HqGeometry.segmentU(w.segments()), false);
		}
		b.anchor(Anchor.at(AnchorNames.wing(w.projectKey()), new BlockPos(HqGeometry.wx(i, 1), 1, HqGeometry.wz(i, (DOOR_V1 + DOOR_V2) / 2)),
				HqGeometry.outward(i)));
	}

	/** The wall across a wing at column {@code u}: the inner one with the doorway, or the end cap with windows. */
	private void wingEndWall(PlanBuilder.Canvas c, int i, int u, boolean inner) {
		int x = HqGeometry.wx(i, u);
		for (int v = 0; v < ROW; v++) {
			int z = HqGeometry.wz(i, v);
			if (v == ROW - 1) {
				c.set(x, 0, z, k.grass);
				continue;
			}
			boolean door = inner && v >= DOOR_V1 && v <= DOOR_V2;
			c.set(x, 0, z, door ? k.woodFloor : k.plinth);
			boolean post = inner ? v == 0 || v == 4 || v == 8 || v == WING_WALL : v == 0 || v == 6 || v == WING_WALL;
			boolean window = !inner && ((v >= 2 && v <= 4) || (v >= 8 && v <= 10));
			for (int y = 1; y <= 4; y++) {
				BlockState s = post ? k.post() : y == 4 ? k.beam(Direction.Axis.Z) : door ? k.air : y == 1 ? k.plinth : window ? k.pane : k.infill;
				c.set(x, y, z, s);
			}
			c.set(x, 5, z, k.darkPlanks);
			c.set(x, 6, z, k.copperSlab);
		}
	}

	private void segment(PlanBuilder b, int i, int s, DyeColor accent) {
		List<Box> holes = new ArrayList<>(2);
		holes.add(HqGeometry.desk(i, 2 * s));
		holes.add(HqGeometry.desk(i, 2 * s + 1));
		try (PlanBuilder.Canvas c = b.module("wing_" + i + "_seg_" + s, HqGeometry.segment(i, s), holes)) {
			int u0 = HqGeometry.segmentU(s);
			for (int col = 0; col < SEGMENT; col++) {
				int x = HqGeometry.wx(i, u0 + col);
				boolean pillar = col == SEGMENT - 1;
				for (int v = 0; v < ROW; v++) {
					int z = HqGeometry.wz(i, v);
					if (v == ROW - 1) {
						// garden strip between rows: grass, a bush mid-segment, a lamp post at the pillar
						c.set(x, 0, z, k.grass);
						if (pillar) {
							c.set(x, 1, z, k.plinthWall);
							c.decor(x, 2, z, k.copperLantern(false));
						} else {
							c.decor(x, 1, z, col == 1 ? (s % 2 == 0 ? k.floweringAzalea : k.azalea) : k.shortGrass);
						}
						continue;
					}
					boolean wall = v == 0 || v == WING_WALL;
					if (wall) {
						c.set(x, 0, z, k.plinth);
						for (int y = 1; y <= 4; y++) {
							c.set(x, y, z, pillar ? k.post() : y == 1 ? k.plinth : y == 2 ? k.infill : y == 3 ? k.pane : k.beam(Direction.Axis.X));
						}
						c.set(x, 5, z, k.darkPlanks);
						c.set(x, 6, z, k.copperSlab);
						continue;
					}
					if (c.owns(x, 0, z)) {
						c.set(x, 0, z, pillar ? k.darkPlanks : k.woodFloor);
					}
					boolean sky = v >= 4 && v <= 8;
					c.set(x, 5, z, pillar ? k.beam(Direction.Axis.Z) : sky ? k.glass : k.infill);
					if (!sky || pillar) {
						c.set(x, 6, z, k.copperSlab);
					}
					if (pillar && (v == 1 || v == WING_WALL - 1)) {
						c.set(x, 1, z, k.bookshelf);
						c.decor(x, 2, z, k.pottedAzalea);
					}
					if (pillar && (v == 3 || v == WING_WALL - 3)) {
						c.hang(x, 4, z, k.copperLantern(true));
					}
					if (v >= DOOR_V1 && v <= DOOR_V2) {
						c.decor(x, 1, z, k.carpet(accent));
					}
				}
			}
		}
	}

	/** One desk: desk row against the wall (drawer barrel, two desk tops, monitor, status lamp), chair behind. */
	private void desk(PlanBuilder b, int i, int slot, @Nullable String threadId) {
		Box box = HqGeometry.desk(i, slot);
		try (PlanBuilder.Canvas c = b.module("wing_" + i + "_desk_" + slot, box, List.of())) {
			Direction f = HqGeometry.deskFacing(slot);
			Direction right = f.getClockWise();
			int cx = HqGeometry.wx(i, HqGeometry.segmentU(slot / 2) + 1);
			int dz = HqGeometry.wz(i, HqGeometry.deskV(slot));
			int chz = HqGeometry.wz(i, HqGeometry.chairV(slot));
			for (int dx = -1; dx <= 1; dx++) {
				c.set(cx + dx, 0, dz, k.woodFloor);
				c.set(cx + dx, 0, chz, k.woodFloor);
			}
			BlockPos desk = new BlockPos(cx, 1, dz);
			BlockPos left = desk.relative(right.getOpposite());
			BlockPos rightPos = desk.relative(right);
			c.set(left, k.barrel(f.getOpposite()));
			c.set(desk, k.deskTop);
			c.set(rightPos, k.deskTop);
			BlockPos monitor = desk.above();
			BlockPos lamp = rightPos.above();
			c.set(monitor, threadId == null ? k.monitorOff : k.monitorOn);
			c.set(lamp, k.lamp());
			c.decor(left.above(), k.fern);
			BlockPos seat = new BlockPos(cx, 1, chz);
			c.set(seat, k.chair(f.getOpposite()));
			if (threadId != null) {
				b.anchor(Anchor.at(AnchorNames.desk(threadId), seat, f));
				b.anchor(Anchor.face(AnchorNames.monitor(threadId), monitor, f.getOpposite()));
				b.anchor(Anchor.face(AnchorNames.lamp(threadId), lamp, f.getOpposite()));
			}
		}
	}
}
