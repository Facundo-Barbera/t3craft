package dev.t3craft.piece;

import dev.t3craft.drop.DropScene;
import dev.t3craft.hq.Placement;
import dev.t3craft.hq.SignTags;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * feat/piece-catalog-preview: the stage a piece stands on, as data. A piece is put on a grass floor as big as it
 * needs (the piece plus {@link #MARGIN} blocks all round, so the central building of the HQ fits as well as a
 * lamp), with a lobby at the south end: the arrival spot, a return arch and a sign with the piece's name, size and
 * block count. Never the 120-block showroom of the preview drop.
 *
 * <p>Positions are relative to the stage origin, the north-west corner of the floor: {@code y = 0} is the floor
 * layer (people stand at {@code y = 1}), {@code +x} east, {@code +z} south. A piece keeps its own heights (a
 * catalog variant has its ground at {@code y = 0}, a part of the citadel too), so a sunken room goes below the floor.
 *
 * <p>The north and west border carry a ruler counted from the piece's own corner: a chiseled brick every 10 blocks,
 * gold every 50, to read real distances by eye. Order of the cells: arch and lobby first (a visitor never lands on
 * nothing), then the floor, then the piece. Deterministic.
 *
 * @param pieceSolid solid blocks of the piece (not counting the stage)
 * @param minY       lowest layer the stage uses (the piece may go below the floor)
 * @param maxY       highest layer the stage uses
 * @param lobbyCells how many cells at the head of {@code model} a visitor lands on (the pad, the arch and the signs): the
 *                   stage can take the player there once that many are placed
 */
public record PieceScene(String id, String title, String detail, PieceModel model, int width, int depth, int minY, int maxY, int pieceSolid,
		BlockPos arrival, float arrivalYaw, BlockPos triggerMin, BlockPos triggerMax, int lobbyCells) {
	/** Floor between the piece and the edge of the stage, on every side. */
	public static final int MARGIN = 14;
	/** Extra floor south of the piece for the arrival spot, the arch and the sign. */
	public static final int LOBBY = 16;
	/** Narrowest stage (the lobby always fits). */
	public static final int MIN_WIDTH = 45;
	/** Stage floors never get bigger than this on a side, whatever the piece (a runaway generator). */
	public static final int MAX_SIDE = 600;
	/** The floor is emitted in tiles of this many blocks a side, so the stage works on a few chunks at a time. */
	private static final int TILE = 16;

	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState LOBBY_FLOOR = Blocks.POLISHED_ANDESITE.defaultBlockState();
	private static final BlockState BORDER = Blocks.STONE_BRICKS.defaultBlockState();
	private static final BlockState MARK_10 = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
	private static final BlockState MARK_50 = Blocks.GOLD_BLOCK.defaultBlockState();
	private static final BlockState LAMP = Blocks.LANTERN.defaultBlockState();

	/**
	 * The stage for {@code piece}.
	 *
	 * @throws IllegalArgumentException when the piece is bigger than {@link #MAX_SIDE}
	 */
	public static PieceScene compose(String id, String title, String detail, PieceModel piece) {
		int pw = Math.max(piece.sizeX(), 8);
		int pd = Math.max(piece.sizeZ(), 8);
		int ph = Math.max(piece.sizeY(), 8);
		int width = Math.max(MIN_WIDTH, pw + 2 * MARGIN);
		int depth = MARGIN + pd + MARGIN + LOBBY;
		if (width > MAX_SIDE || depth > MAX_SIDE) {
			throw new IllegalArgumentException(id + " is " + pw + "x" + ph + "x" + pd + " blocks: more than the stage takes (" + MAX_SIDE + " a side)");
		}
		if (width % 2 == 0) {
			width++; // an odd width has a middle column for the arch
		}
		int cx = width / 2;
		int archZ = depth - 4;
		int dx = piece.hasSolid() ? (width - pw) / 2 - piece.minX() : 0;
		int dz = piece.hasSolid() ? MARGIN - piece.minZ() : 0;
		PieceModel p = piece.shifted(dx, 0, dz);
		int cornerX = piece.hasSolid() ? p.minX() : MARGIN;
		int cornerZ = piece.hasSolid() ? p.minZ() : MARGIN;

		// 1. the lobby: the arrival pad, the arch, signs, lamps on the ruler. Everything that is not the floor itself.
		// The pad and the arch go first: they are what a visitor lands on, so they are up before the first teleport.
		Map<Long, Placement> special = new LinkedHashMap<>();
		for (int z = archZ - 7; z <= archZ - 1; z++) {
			for (int x = cx - 3; x <= cx + 3; x++) {
				put(special, new Placement(new BlockPos(x, 0, z), LOBBY_FLOOR));
			}
		}
		DropScene.Portal arch = DropScene.portal(true, true);
		for (Placement c : arch.cells()) {
			put(special, new Placement(c.rel().offset(cx, 0, archZ), c.state(), c.be()));
		}
		BlockState sign = Blocks.OAK_SIGN.defaultBlockState();
		put(special, new Placement(new BlockPos(cx - 4, 1, archZ - 1), sign.setValue(BlockStateProperties.ROTATION_16, 8),
				SignTags.sign(DyeColor.BLACK, false, "Back to", "the HQ", "<- arch", "")));
		String dims = piece.sizeX() + "x" + piece.sizeY() + "x" + piece.sizeZ();
		put(special, new Placement(new BlockPos(cx, 1, archZ - 9), sign.setValue(BlockStateProperties.ROTATION_16, 0),
				SignTags.sign(DyeColor.BLACK, false, SignTags.clip(title), dims, piece.solid() + " blocks", SignTags.clip(id))));
		int lobbySpecial = special.size(); // the pad, the arch and the signs: what a visitor lands on
		for (int x = 0; x < width; x++) {
			int rel = x - cornerX;
			if (rel >= 0 && rel <= pw && rel % 50 == 0) {
				put(special, new Placement(new BlockPos(x, 1, 0), LAMP));
			}
		}
		for (int z = 0; z < depth - LOBBY; z++) {
			int rel = z - cornerZ;
			if (rel >= 0 && rel <= pd && rel % 50 == 0) {
				put(special, new Placement(new BlockPos(0, 1, z), LAMP));
			}
		}

		// 2. the cells, in placement order: special, floor, piece
		PieceModel.Builder b = PieceModel.builder();
		int lobbyCells = 0;
		int seen = 0;
		for (Placement c : special.values()) {
			boolean lobby = seen++ < lobbySpecial;
			if (!p.contains(c.rel().asLong())) {
				b.add(c.rel().getX(), c.rel().getY(), c.rel().getZ(), c.state(), c.be());
				if (lobby) {
					lobbyCells++;
				}
			}
		}
		// South first, so the lobby is up before the rest of the floor; inside a row of tiles west to east, and inside a
		// tile row by row. Same cells as a plain row-by-row sweep, but the stage then works on one or two chunks at a
		// time instead of on the whole width of the stage (every chunk of a row would be loaded in one server tick).
		for (int tz = depth - 1; tz >= 0; tz -= TILE) {
			int z0 = Math.max(0, tz - TILE + 1);
			for (int tx = 0; tx < width; tx += TILE) {
				int x1 = Math.min(width, tx + TILE);
				for (int z = tz; z >= z0; z--) {
					for (int x = tx; x < x1; x++) {
						long k = BlockPos.asLong(x, 0, z);
						if (special.containsKey(k) || p.contains(k)) {
							continue;
						}
						b.add(x, 0, z, floorAt(x, z, width, depth, cornerX, cornerZ));
					}
				}
			}
		}
		for (int i = 0; i < p.size(); i++) {
			b.add(BlockPos.getX(p.key(i)), BlockPos.getY(p.key(i)), BlockPos.getZ(p.key(i)), p.state(i), p.tag(i));
		}
		PieceModel model = b.build();
		return new PieceScene(id, title, detail, model, width, depth, Math.min(0, p.hasSolid() ? p.minY() : 0), Math.max(p.hasSolid() ? p.maxY() : 8, 8) + 3,
				piece.solid(), new BlockPos(cx, 1, archZ - 4), 180.0F, arch.triggerMin().offset(cx, 0, archZ),
				arch.triggerMax().offset(cx, 0, archZ), lobbyCells);
	}

	/** A stage with nothing on it: what {@code clear} turns the world into. */
	public static PieceScene blank() {
		return new PieceScene("", "", "", PieceModel.empty(), 1, 1, 0, 0, 0, BlockPos.ZERO, 0.0F, BlockPos.ZERO, BlockPos.ZERO, 0);
	}

	public boolean isBlank() {
		return model.size() == 0;
	}

	private static void put(Map<Long, Placement> m, Placement p) {
		m.put(p.rel().asLong(), p);
	}

	private static BlockState floorAt(int x, int z, int width, int depth, int cornerX, int cornerZ) {
		boolean edge = x == 0 || z == 0 || x == width - 1 || z == depth - 1;
		if (!edge) {
			return z >= depth - LOBBY ? LOBBY_FLOOR : GRASS;
		}
		int rel = -1;
		if (z == 0) {
			rel = x - cornerX;
		} else if (x == 0) {
			rel = z - cornerZ;
		}
		if (rel > 0 && rel % 50 == 0) {
			return MARK_50;
		}
		if (rel > 0 && rel % 10 == 0) {
			return MARK_10;
		}
		return BORDER;
	}
}
