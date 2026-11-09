package dev.t3craft.drop;

import dev.t3craft.hq.pack.BlockStateText;
import dev.t3craft.hq.pack.RoomTemplate;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.state.BlockState;

/**
 * feat/ingame-preview-drop: reader of Sponge schematics ({@code .schem}) version 2 and 3.
 *
 * <ul>
 *   <li>v2: {@code Width/Height/Length} (unsigned shorts), {@code Palette} (block state text → id),
 *       {@code BlockData} (one var-int id per block, index {@code (y * Length + z) * Width + x}) and
 *       {@code BlockEntities} ({@code Pos}, {@code Id} and the data beside them).</li>
 *   <li>v3: the same under a {@code Schematic} tag, with {@code Blocks.Palette}, {@code Blocks.Data} and
 *       {@code Blocks.BlockEntities} (their data in {@code Data}).</li>
 * </ul>
 * The state text of the palette is the command syntax ({@code minecraft:oak_stairs[facing=north]}), read by
 * {@link BlockStateText}: an unknown block or property is an error with the file name, never silent air.
 * Air is dropped. Offsets and entities are ignored.
 */
final class SpongeSchem {
	private SpongeSchem() {
	}

	static Schematic read(Path file, String name) throws IOException {
		String fn = file.getFileName().toString();
		CompoundTag root;
		try {
			root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		} catch (IOException | RuntimeException e) {
			throw new IOException(fn + ": not a .schem file (" + e.getMessage() + ")", e);
		}
		CompoundTag schem = root.getCompound("Schematic").orElse(root);
		int version = schem.getIntOr("Version", 2);
		int w = schem.getShortOr("Width", (short) 0) & 0xFFFF;
		int h = schem.getShortOr("Height", (short) 0) & 0xFFFF;
		int l = schem.getShortOr("Length", (short) 0) & 0xFFFF;
		if (w <= 0 || h <= 0 || l <= 0) {
			throw new IOException(fn + ": no Width/Height/Length (is it a Sponge .schem?)");
		}
		long volume = (long) w * h * l;
		if (w > Schematic.MAX_SIDE || h > Schematic.MAX_SIDE || l > Schematic.MAX_SIDE || volume > Schematic.MAX_VOLUME) {
			throw new IOException(fn + ": box " + w + "x" + h + "x" + l + " is too big (sides up to " + Schematic.MAX_SIDE + ", "
					+ Schematic.MAX_VOLUME + " blocks in all)");
		}
		CompoundTag palette;
		byte[] data;
		ListTag entities;
		if (version >= 3) {
			CompoundTag blocks = schem.getCompoundOrEmpty("Blocks");
			palette = blocks.getCompoundOrEmpty("Palette");
			data = blocks.getByteArray("Data").orElse(new byte[0]);
			entities = blocks.getListOrEmpty("BlockEntities");
		} else {
			palette = schem.getCompoundOrEmpty("Palette");
			data = schem.getByteArray("BlockData").orElse(new byte[0]);
			entities = schem.getListOrEmpty("BlockEntities");
		}
		if (palette.isEmpty() || data.length == 0) {
			throw new IOException(fn + ": no Palette / block data (version " + version + ")");
		}
		BlockState[] byId = palette(fn, palette);

		List<RoomTemplate.Cell> cells = new ArrayList<>();
		Map<Long, Integer> index = new HashMap<>();
		int pos = 0;
		int wl = w * l;
		for (int i = 0; i < volume; i++) {
			int value = 0;
			int shift = 0;
			while (true) {
				if (pos >= data.length) {
					throw new IOException(fn + ": block data ends early (block " + i + " of " + volume + ")");
				}
				int b = data[pos++];
				value |= (b & 0x7F) << shift;
				if ((b & 0x80) == 0) {
					break;
				}
				shift += 7;
				if (shift > 28) {
					throw new IOException(fn + ": bad var-int in the block data (block " + i + ")");
				}
			}
			BlockState s = value >= 0 && value < byId.length ? byId[value] : null;
			if (s == null) {
				throw new IOException(fn + ": block " + i + " uses palette id " + value + ", which the palette does not define");
			}
			if (s.isAir()) {
				continue;
			}
			if (cells.size() >= Schematic.MAX_CELLS) {
				throw new IOException(fn + ": more than " + Schematic.MAX_CELLS + " solid blocks is too many");
			}
			BlockPos p = new BlockPos(i % w, i / wl, (i / w) % l);
			if (!entities.isEmpty()) {
				index.put(p.asLong(), cells.size());
			}
			cells.add(new RoomTemplate.Cell(p, s, null));
		}
		for (int i = 0; i < entities.size(); i++) {
			CompoundTag e = entities.getCompoundOrEmpty(i);
			int[] at = e.getIntArray("Pos").orElse(null);
			if (at == null || at.length != 3) {
				continue;
			}
			Integer ci = index.get(new BlockPos(at[0], at[1], at[2]).asLong());
			if (ci == null) {
				continue;
			}
			CompoundTag be = version >= 3 ? e.getCompoundOrEmpty("Data").copy() : e.copy();
			be.remove("Pos");
			be.remove("Id");
			be.remove("Extra");
			if (!be.isEmpty()) {
				RoomTemplate.Cell old = cells.get(ci);
				cells.set(ci, new RoomTemplate.Cell(old.pos(), old.state(), be));
			}
		}
		return new Schematic(name, fn, "schem", w, h, l, cells);
	}

	/** The palette as an array indexed by id (null where an id is not defined). */
	private static BlockState[] palette(String fn, CompoundTag palette) throws IOException {
		int max = -1;
		for (String key : palette.keySet()) {
			max = Math.max(max, palette.getIntOr(key, -1));
		}
		if (max < 0 || max > 1_000_000) {
			throw new IOException(fn + ": palette ids out of range");
		}
		BlockState[] byId = new BlockState[max + 1];
		for (String key : palette.keySet()) {
			int id = palette.getIntOr(key, -1);
			if (id < 0) {
				continue;
			}
			try {
				byId[id] = BlockStateText.parse(key);
			} catch (IllegalArgumentException ex) {
				throw new IOException(fn + ": " + ex.getMessage(), ex);
			}
		}
		return byId;
	}
}
