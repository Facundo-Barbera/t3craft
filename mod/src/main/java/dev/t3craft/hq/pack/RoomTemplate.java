package dev.t3craft.hq.pack;

import dev.t3craft.site.StateKey;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * feat/hq-layout-packs: a room saved with a vanilla structure block ({@code .nbt}, the file
 * {@code Save} writes under {@code <world>/generated/<ns>/structure/}), read without a world: size,
 * blocks (with their block entity data) and data markers.
 *
 * <p>Cells the structure does not list (structure void) keep what the generator put there. A
 * structure block in <b>DATA</b> mode is a marker, never placed (the cell becomes air); its metadata
 * says what it marks:
 * <ul>
 *   <li>{@code anchor:<name>} moves that anchor of the module to this cell (keeping its facing);</li>
 *   <li>{@code anchor:<name>:<north|south|east|west>} moves it and turns it.</li>
 * </ul>
 * Every other metadata is ignored (the cell is air).
 */
public record RoomTemplate(String file, int sizeX, int sizeY, int sizeZ, List<Cell> cells, List<Marker> markers) {
	/** One block of the room, at {@code pos} relative to the structure's corner. */
	public record Cell(BlockPos pos, BlockState state, @Nullable CompoundTag be) {
	}

	/** A data marker: an anchor moved to {@code pos}, optionally turned to {@code facing}. */
	public record Marker(BlockPos pos, String anchor, @Nullable Direction facing) {
	}

	public RoomTemplate {
		cells = List.copyOf(cells);
		markers = List.copyOf(markers);
	}

	/** Reads a (gzip) structure file. Errors carry the file name and what is wrong. */
	public static RoomTemplate read(Path file) throws IOException {
		CompoundTag root;
		try {
			root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		} catch (IOException | RuntimeException e) {
			throw new IOException(file.getFileName() + ": not a structure file (" + e.getMessage() + ")", e);
		}
		return fromTag(file.getFileName().toString(), root);
	}

	/** The room in a structure tag ({@code size}, {@code palette} or {@code palettes[0]}, {@code blocks}). */
	public static RoomTemplate fromTag(String file, CompoundTag root) throws IOException {
		ListTag size = root.getListOrEmpty("size");
		if (size.size() != 3) {
			throw new IOException(file + ": no 'size' (is it a structure block .nbt?)");
		}
		int sx = size.getIntOr(0, 0);
		int sy = size.getIntOr(1, 0);
		int sz = size.getIntOr(2, 0);
		ListTag pal = root.getListOrEmpty("palette");
		if (pal.isEmpty()) {
			ListTag pals = root.getListOrEmpty("palettes");
			if (!pals.isEmpty()) {
				pal = pals.getListOrEmpty(0);
			}
		}
		List<BlockState> palette = new ArrayList<>(pal.size());
		for (int i = 0; i < pal.size(); i++) {
			palette.add(state(file, pal.getCompoundOrEmpty(i)));
		}
		ListTag blocks = root.getListOrEmpty("blocks");
		List<Cell> cells = new ArrayList<>(blocks.size());
		List<Marker> markers = new ArrayList<>();
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag b = blocks.getCompoundOrEmpty(i);
			ListTag pos = b.getListOrEmpty("pos");
			if (pos.size() != 3) {
				throw new IOException(file + ": block " + i + " has no pos");
			}
			BlockPos p = new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0));
			int si = b.getIntOr("state", -1);
			if (si < 0 || si >= palette.size()) {
				throw new IOException(file + ": block " + i + " at " + p.toShortString() + " has state " + si + " outside the palette");
			}
			BlockState s = palette.get(si);
			CompoundTag nbt = b.getCompound("nbt").orElse(null);
			if (s.is(Blocks.STRUCTURE_BLOCK)) {
				Marker m = marker(p, nbt);
				if (m != null) {
					markers.add(m);
				}
				cells.add(new Cell(p, Blocks.AIR.defaultBlockState(), null));
				continue;
			}
			if (s.is(Blocks.STRUCTURE_VOID)) {
				continue;
			}
			cells.add(new Cell(p, s, nbt == null ? null : clean(nbt)));
		}
		return new RoomTemplate(file, sx, sy, sz, cells, markers);
	}

	private static BlockState state(String file, CompoundTag t) throws IOException {
		// 26.x writes {id, properties}; older structure files {Name, Properties}
		CompoundTag legacy = new CompoundTag();
		legacy.putString("Name", t.getStringOr("id", t.getStringOr("Name", StateKey.AIR)));
		CompoundTag props = t.getCompound("properties").orElse(t.getCompoundOrEmpty("Properties"));
		if (!props.isEmpty()) {
			legacy.put("Properties", props);
		}
		String key = StateKey.fromTag(legacy);
		try {
			return BlockStateText.parse(key);
		} catch (IllegalArgumentException e) {
			throw new IOException(file + ": " + e.getMessage(), e);
		}
	}

	/** The block entity data without the position and id the structure block saves with it. */
	private static CompoundTag clean(CompoundTag nbt) {
		CompoundTag c = nbt.copy();
		c.remove("x");
		c.remove("y");
		c.remove("z");
		return c;
	}

	private static @Nullable Marker marker(BlockPos p, @Nullable CompoundTag nbt) {
		if (nbt == null) {
			return null;
		}
		String mode = nbt.getStringOr("mode", "DATA");
		if (!mode.equalsIgnoreCase("DATA")) {
			return null;
		}
		String meta = nbt.getStringOr("metadata", "").trim();
		if (!meta.startsWith("anchor:")) {
			return null;
		}
		String[] parts = meta.substring("anchor:".length()).split(":");
		if (parts.length == 0 || parts[0].isBlank()) {
			return null;
		}
		Direction facing = null;
		if (parts.length > 1) {
			Direction d = Direction.byName(parts[1].trim().toLowerCase(java.util.Locale.ROOT));
			facing = d != null && d.getAxis().isHorizontal() ? d : null;
		}
		return new Marker(p, parts[0].trim(), facing);
	}

	/** Stable text of the content, for the pack hash. */
	String digestText() {
		StringBuilder sb = new StringBuilder(file).append('|').append(sizeX).append(',').append(sizeY).append(',').append(sizeZ).append('\n');
		for (Cell c : cells) {
			sb.append(c.pos().getX()).append(',').append(c.pos().getY()).append(',').append(c.pos().getZ()).append('=')
					.append(BlockStateText.format(c.state()));
			if (c.be() != null) {
				sb.append('+').append(c.be());
			}
			sb.append('\n');
		}
		for (Marker m : markers) {
			sb.append("marker ").append(m.anchor()).append('@').append(m.pos().toShortString()).append(m.facing() == null ? "" : ":" + m.facing())
					.append('\n');
		}
		return sb.toString();
	}
}
