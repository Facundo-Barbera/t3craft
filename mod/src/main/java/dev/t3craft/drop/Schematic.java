package dev.t3craft.drop;

import dev.t3craft.hq.pack.RoomTemplate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * feat/ingame-preview-drop: one schematic dropped in {@code config/t3craft/preview-drop/}, read without a
 * world: its box and its non-air blocks (with block entity data), relative to the schematic's corner.
 *
 * <p>Formats: {@code .nbt} (a vanilla structure block, the file {@code Save} writes) and {@code .schem}
 * (Sponge schematic v2 and v3, what WorldEdit, FAWE and most editors export). Entities are ignored.
 * Failures carry the file name and what is wrong, never a stack trace.
 *
 * @param name   the display name: the path under the drop folder without its extension
 * @param file   the path under the drop folder, with its extension
 * @param format {@code "nbt"} or {@code "schem"}
 */
public record Schematic(String name, String file, String format, int sizeX, int sizeY, int sizeZ, List<RoomTemplate.Cell> cells) {
	/** Longest side accepted (blocks). */
	public static final int MAX_SIDE = 256;
	/** Largest box accepted (blocks); the box is only walked, never stored. */
	public static final long MAX_VOLUME = 8_000_000L;
	/** Most solid blocks accepted in one file: each one costs about 100 bytes while the showroom is kept in memory. */
	public static final int MAX_CELLS = 1_500_000;
	/** Largest file accepted (bytes on disk). */
	public static final long MAX_BYTES = 64L * 1024 * 1024;

	public Schematic {
		cells = List.copyOf(cells);
	}

	/** Reads {@code file}; {@code name} is its display name (path under the drop folder, no extension). */
	public static Schematic read(Path file, String name) throws IOException {
		String fn = file.getFileName().toString();
		String lower = fn.toLowerCase(Locale.ROOT);
		if (Files.size(file) > MAX_BYTES) {
			throw new IOException(fn + ": file is larger than " + (MAX_BYTES >> 20) + " MB");
		}
		Schematic s;
		if (lower.endsWith(".nbt")) {
			RoomTemplate t = RoomTemplate.read(file);
			List<RoomTemplate.Cell> solid = new ArrayList<>(t.cells().size());
			for (RoomTemplate.Cell c : t.cells()) {
				if (!c.state().isAir()) {
					solid.add(c);
				}
			}
			s = new Schematic(name, fn, "nbt", t.sizeX(), t.sizeY(), t.sizeZ(), solid);
		} else if (lower.endsWith(".schem")) {
			s = SpongeSchem.read(file, name);
		} else {
			throw new IOException(fn + ": unsupported file type (use .nbt or .schem)");
		}
		s.check();
		return s;
	}

	private void check() throws IOException {
		if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
			throw new IOException(file + ": empty box (" + sizeX + "x" + sizeY + "x" + sizeZ + ")");
		}
		if (sizeX > MAX_SIDE || sizeY > MAX_SIDE || sizeZ > MAX_SIDE || (long) sizeX * sizeY * sizeZ > MAX_VOLUME) {
			throw new IOException(file + ": box " + sizeX + "x" + sizeY + "x" + sizeZ + " is too big (sides up to " + MAX_SIDE + ", "
					+ MAX_VOLUME + " blocks in all)");
		}
		if (cells.size() > MAX_CELLS) {
			throw new IOException(file + ": " + cells.size() + " solid blocks is too many (up to " + MAX_CELLS + ")");
		}
	}

	/** {@code WxHxD}, for labels and chat. */
	public String sizeText() {
		return sizeX + "x" + sizeY + "x" + sizeZ;
	}
}
