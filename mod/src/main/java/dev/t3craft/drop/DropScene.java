package dev.t3craft.drop;

import dev.t3craft.hq.SignTags;
import dev.t3craft.hq.pack.RoomTemplate;
import dev.t3craft.hq.plan.Placement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * feat/ingame-preview-drop: the showroom ("recinto") as data. A walled stone floor with one pedestal per
 * schematic (rows, left to right, wrapping at {@link #ROW_MAX} blocks), a sign in front of each one
 * (name, size, format, block count) and a return arch at the south end. Positions are relative to the
 * showroom's origin: the north-west corner of the floor, {@code y = 0} is the floor layer, people stand
 * at {@code y = 1}; {@code +x} is east and {@code +z} south. Each schematic lands on its pedestal with
 * its own {@code (0,0,0)} on the pedestal's top, un-rotated; its front for a viewer standing at the sign
 * is whatever the author built facing south.
 *
 * <p>Fully deterministic: the same list of schematics (same order) gives the same scene, so
 * {@link #diff} between two scenes is exactly what changed. No world, no clock, no randomness.
 */
public final class DropScene {
	/** Floor margin around the pedestals, and from the west/north walls. */
	static final int PAD = 4;
	/** Free ground between two pedestals (the sign row is in it). */
	static final int GAP = 6;
	/** Pedestals wrap to a new row beyond this x. */
	static final int ROW_MAX = 120;
	/** Narrowest floor (the arch and its sign always fit). */
	static final int MIN_WIDTH = 25;
	/** Ground south of the last row: the arrival area and the arch. */
	static final int ARRIVAL_DEPTH = 10;
	/** Blocks from the arch to the arrival spot, towards the exhibits. */
	static final int ARRIVAL_BACK = 4;

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState FLOOR = Blocks.POLISHED_ANDESITE.defaultBlockState();
	private static final BlockState BORDER = Blocks.STONE_BRICKS.defaultBlockState();
	private static final BlockState WALL = Blocks.STONE_BRICK_WALL.defaultBlockState();
	private static final BlockState LAMP = Blocks.LANTERN.defaultBlockState();
	private static final BlockState PEDESTAL = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
	private static final BlockState PEDESTAL_RIM = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
	private static final BlockState ARCH = Blocks.CRYING_OBSIDIAN.defaultBlockState();
	private static final BlockState ARCH_LIGHT = Blocks.SEA_LANTERN.defaultBlockState();
	private static final BlockState PLATE = Blocks.GOLD_BLOCK.defaultBlockState();

	private DropScene() {
	}

	/** Where one schematic stands: its pedestal's north-west corner (the pedestal is {@code size + 2} wide and deep). */
	public record Slot(String name, int x, int z, int sizeX, int sizeY, int sizeZ) {
	}

	/**
	 * The showroom.
	 *
	 * @param cells      every block, in application order (arrival area and arch first)
	 * @param arrival    where people appear (feet), relative to the origin
	 * @param arrivalYaw the way they look on arrival (towards the exhibits)
	 * @param triggerMin the arch's walk-through box, inclusive corners (back to the HQ)
	 */
	public record Scene(Map<BlockPos, Placement> cells, List<Slot> slots, int width, int depth, int height, BlockPos arrival,
			float arrivalYaw, BlockPos triggerMin, BlockPos triggerMax) {
		public Scene {
			cells = Collections.unmodifiableMap(cells);
			slots = List.copyOf(slots);
		}
	}

	/** A walk-through arch: its blocks and the box that sends people through, relative to the arch's centre cell. */
	public record Portal(List<Placement> cells, BlockPos triggerMin, BlockPos triggerMax) {
	}

	/** The showroom for {@code schematics}, in the order given. An empty list gives the empty showroom (floor, wall, arch). */
	public static Scene build(Collection<Schematic> schematics) {
		List<Slot> slots = new ArrayList<>();
		int x = PAD;
		int z = PAD;
		int rowDepth = 0;
		int maxX = 0;
		int maxH = 8;
		for (Schematic s : schematics) {
			int pw = s.sizeX() + 2;
			int pd = s.sizeZ() + 2;
			if (x > PAD && x + pw > ROW_MAX) {
				x = PAD;
				z += rowDepth + GAP;
				rowDepth = 0;
			}
			slots.add(new Slot(s.name(), x, z, s.sizeX(), s.sizeY(), s.sizeZ()));
			x += pw + GAP;
			maxX = Math.max(maxX, x - GAP);
			rowDepth = Math.max(rowDepth, pd);
			maxH = Math.max(maxH, s.sizeY() + 2);
		}
		int exhibitsEnd = slots.isEmpty() ? PAD : z + rowDepth;
		int width = Math.max(MIN_WIDTH, maxX + PAD);
		if (width % 2 == 0) {
			width++; // an odd width has a middle column for the arch
		}
		int depth = exhibitsEnd + GAP + ARRIVAL_DEPTH;
		int cx = width / 2;
		int archZ = depth - 4;

		Map<BlockPos, Placement> cells = new LinkedHashMap<>(Math.max(64, width * depth));

		// 1. the arrival area and the arch, so they are the first thing built
		for (int zz = depth - ARRIVAL_DEPTH; zz < depth; zz++) {
			for (int xx = Math.max(0, cx - 6); xx <= Math.min(width - 1, cx + 6); xx++) {
				put(cells, xx, 0, zz, floorAt(xx, zz, width, depth));
			}
		}
		Portal arch = portal(true, true);
		for (Placement p : arch.cells()) {
			cells.put(p.rel().offset(cx, 0, archZ), new Placement(p.rel().offset(cx, 0, archZ), p.state(), p.be()));
		}
		put(cells, cx - 4, 1, archZ - 1, Blocks.OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 8),
				SignTags.sign(DyeColor.BLACK, false, "Back to", "the HQ", "<- portal", ""));

		// 2. the rest of the floor and the wall
		for (int zz = 0; zz < depth; zz++) {
			for (int xx = 0; xx < width; xx++) {
				BlockPos p = new BlockPos(xx, 0, zz);
				cells.putIfAbsent(p, new Placement(p, floorAt(xx, zz, width, depth)));
				boolean edge = xx == 0 || zz == 0 || xx == width - 1 || zz == depth - 1;
				if (edge) {
					put(cells, xx, 1, zz, WALL);
					if ((xx + zz) % 8 == 0 || ((xx == 0 || xx == width - 1) && (zz == 0 || zz == depth - 1))) {
						put(cells, xx, 2, zz, LAMP);
					}
				}
			}
		}

		// 3. the exhibits
		int i = 0;
		for (Schematic s : schematics) {
			Slot slot = slots.get(i++);
			int pw = slot.sizeX() + 2;
			int pd = slot.sizeZ() + 2;
			for (int zz = 0; zz < pd; zz++) {
				for (int xx = 0; xx < pw; xx++) {
					boolean rim = xx == 0 || zz == 0 || xx == pw - 1 || zz == pd - 1;
					put(cells, slot.x() + xx, 0, slot.z() + zz, rim ? PEDESTAL_RIM : PEDESTAL);
				}
			}
			for (RoomTemplate.Cell c : s.cells()) {
				BlockPos q = c.pos();
				if (q.getX() < 0 || q.getY() < 0 || q.getZ() < 0 || q.getX() >= s.sizeX() || q.getY() >= s.sizeY() || q.getZ() >= s.sizeZ()) {
					continue; // outside its own box: it would land on the neighbour
				}
				BlockPos at = new BlockPos(slot.x() + 1 + q.getX(), 1 + q.getY(), slot.z() + 1 + q.getZ());
				cells.put(at, new Placement(at, c.state(), c.be()));
			}
			String name = s.name();
			String first = name.length() <= SignTags.LINE ? name : name.substring(0, SignTags.LINE);
			String second = name.length() <= SignTags.LINE ? "" : name.substring(SignTags.LINE);
			put(cells, slot.x() + pw / 2, 1, slot.z() + pd, Blocks.OAK_SIGN.defaultBlockState(),
					SignTags.sign(DyeColor.BLACK, false, first, second, s.sizeText(), s.format() + " " + s.cells().size()));
		}

		return new Scene(cells, slots, width, depth, maxH, new BlockPos(cx, 1, archZ - ARRIVAL_BACK), 180.0F,
				arch.triggerMin().offset(cx, 0, archZ), arch.triggerMax().offset(cx, 0, archZ));
	}

	/**
	 * A walk-through arch of crying obsidian, 5 wide and 6 tall with its walk-through opening 3 wide and 4 high.
	 * Coordinates are relative to the arch's centre cell on the floor layer ({@code y = 0}); the arch is two blocks
	 * thick. {@code alongX}: the opening spans x (people walk through along z); otherwise it spans z. {@code plate}
	 * lays a gold plate in the opening at floor level (leave it out where a floor already exists).
	 */
	public static Portal portal(boolean alongX, boolean plate) {
		List<Placement> out = new ArrayList<>();
		for (int v = 0; v <= 1; v++) {
			if (plate) {
				for (int u = -1; u <= 1; u++) {
					out.add(new Placement(cell(alongX, u, 0, v), PLATE));
				}
			}
			for (int u = -1; u <= 1; u++) {
				for (int y = 1; y <= 4; y++) {
					out.add(new Placement(cell(alongX, u, y, v), AIR));
				}
			}
			for (int y = 1; y <= 4; y++) {
				out.add(new Placement(cell(alongX, -2, y, v), ARCH));
				out.add(new Placement(cell(alongX, 2, y, v), ARCH));
			}
			for (int u = -2; u <= 2; u++) {
				out.add(new Placement(cell(alongX, u, 5, v), ARCH));
			}
			out.add(new Placement(cell(alongX, -2, 6, v), ARCH_LIGHT));
			out.add(new Placement(cell(alongX, 2, 6, v), ARCH_LIGHT));
		}
		BlockPos a = cell(alongX, -1, 1, 0);
		BlockPos b = cell(alongX, 1, 3, 1);
		return new Portal(out, new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
				new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
	}

	private static BlockPos cell(boolean alongX, int u, int y, int v) {
		return alongX ? new BlockPos(u, y, v) : new BlockPos(v, y, u);
	}

	/**
	 * What to apply to a world that holds {@code previous} (null: nothing known) so it holds {@code next}:
	 * first the cells {@code previous} had and {@code next} no longer has (air), then every cell of
	 * {@code next} whose content changed, in {@code next}'s order.
	 */
	public static List<Placement> diff(@Nullable Scene previous, Scene next) {
		// Cells of the new scene first (the arrival area and the arch lead, so a visitor never lands on nothing);
		// the cells it no longer has go last: they are disjoint from the others, so the order does not matter.
		List<Placement> out = new ArrayList<>();
		for (Placement p : next.cells().values()) {
			Placement old = previous == null ? null : previous.cells().get(p.rel());
			if (old == null || !old.sameContent(p)) {
				out.add(p);
			}
		}
		if (previous != null) {
			for (Placement p : previous.cells().values()) {
				if (!next.cells().containsKey(p.rel()) && (!p.state().isAir() || p.be() != null)) {
					out.add(new Placement(p.rel(), AIR));
				}
			}
		}
		return out;
	}

	private static BlockState floorAt(int x, int z, int width, int depth) {
		return x == 0 || z == 0 || x == width - 1 || z == depth - 1 ? BORDER : FLOOR;
	}

	private static void put(Map<BlockPos, Placement> cells, int x, int y, int z, BlockState state) {
		put(cells, x, y, z, state, null);
	}

	private static void put(Map<BlockPos, Placement> cells, int x, int y, int z, BlockState state, net.minecraft.nbt.@Nullable CompoundTag be) {
		BlockPos p = new BlockPos(x, y, z);
		cells.put(p, new Placement(p, state, be));
	}
}
