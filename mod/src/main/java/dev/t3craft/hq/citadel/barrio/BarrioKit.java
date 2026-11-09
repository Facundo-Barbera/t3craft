package dev.t3craft.hq.citadel.barrio;

import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Lot;
import dev.t3craft.hq.RingCampusGeometry.Room;
import dev.t3craft.hq.citadel.CitadelCanvas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.jspecify.annotations.Nullable;

/**
 * Shared helpers of the barrio design pieces ({@link BarrioRoofs}, {@link BarrioCourt}, {@link BarrioGate}):
 * deterministic hashes, the roof material families (stairs, tone, slab, finial, chimney) and the
 * "reserved zones" that keep the eaves of neighbouring buildings of one lot from touching.
 *
 * <p>Everything here is a pure function of the geometry: a roof never depends on which other halls of
 * the lot are built, so growing from 4 to 10 to 20 projects changes no block of a hall.
 */
final class BarrioKit {
	private BarrioKit() {
	}

	// ------------------------------------------------------------------ determinism

	/** A stable, well mixed non-negative hash of three ints (never random per run). */
	static int hash(int a, int b, int c) {
		int h = a * 0x9E3779B1 ^ Integer.rotateLeft(b * 0x85EBCA6B, 13) ^ Integer.rotateLeft(c * 0xC2B2AE35, 7);
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		h *= 0x297A2D39;
		h ^= h >>> 15;
		return h & 0x7fffffff;
	}

	// ------------------------------------------------------------------ block families

	static BlockState stair(Block b, Direction facing, StairsShape shape, Half half) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half).setValue(StairBlock.SHAPE, shape);
	}

	static BlockState stair(Block b, Direction facing) {
		return stair(b, facing, StairsShape.STRAIGHT, Half.BOTTOM);
	}

	/** The slab of a roof's stair family (bottom half). */
	static BlockState slabOf(Block roof) {
		Block s = roof == Blocks.CHERRY_STAIRS ? Blocks.CHERRY_SLAB
				: roof == Blocks.DEEPSLATE_TILE_STAIRS ? Blocks.DEEPSLATE_TILE_SLAB
				: roof == Blocks.BRICK_STAIRS ? Blocks.BRICK_SLAB : Blocks.STONE_BRICK_SLAB;
		return s.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
	}

	/** The second tone of a roof: used for the eave course and the course stripes. */
	static Block altStairs(Block roof) {
		if (roof == Blocks.CHERRY_STAIRS) {
			return Blocks.MANGROVE_STAIRS;
		} else if (roof == Blocks.DEEPSLATE_TILE_STAIRS) {
			return Blocks.MOSSY_STONE_BRICK_STAIRS;
		} else if (roof == Blocks.BRICK_STAIRS) {
			return Blocks.MUD_BRICK_STAIRS;
		}
		return roof;
	}

	/** Every this many courses of a slope is in the second tone (a stripe, not noise). */
	static final int COURSE_EVERY = 3;

	/** The roof stair at a cell: the eave course (k &lt; 0) and every third course of the slope in the second tone. */
	static Block tone(Block roof, int k, int x, int y, int z) {
		return k < 0 || (k > 0 && k % COURSE_EVERY == 0) ? altStairs(roof) : roof;
	}

	/** The finial on the ends of a gable ridge (a fence post for cherry, a copper rod otherwise). */
	static BlockState finial(Block roof) {
		return roof == Blocks.CHERRY_STAIRS ? Blocks.CHERRY_FENCE.defaultBlockState() : rod();
	}

	static BlockState rod() {
		return Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState();
	}

	/** The chimney shaft: it must read against the roof, so red brick on the dark roofs, stone or deepslate on the others. */
	static BlockState chimney(Block roof) {
		return roof == Blocks.DEEPSLATE_TILE_STAIRS ? Blocks.BRICKS.defaultBlockState()
				: roof == Blocks.BRICK_STAIRS ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
	}

	static BlockState chimneyCap(Block roof) {
		Block s = roof == Blocks.DEEPSLATE_TILE_STAIRS ? Blocks.BRICK_SLAB
				: roof == Blocks.BRICK_STAIRS ? Blocks.DEEPSLATE_BRICK_SLAB : Blocks.STONE_BRICK_SLAB;
		return s.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
	}

	/** A sign colour that reads on the cherry sign boards: the light dyes fall back to their dark kin. */
	static DyeColor readable(DyeColor d) {
		return switch (d) {
			case WHITE -> DyeColor.BLACK;
			case LIGHT_GRAY -> DyeColor.GRAY;
			case YELLOW, ORANGE -> DyeColor.BROWN;
			case MAGENTA -> DyeColor.PURPLE;
			case CYAN -> DyeColor.BLUE;
			case LIME -> DyeColor.GREEN;
			case LIGHT_BLUE -> DyeColor.BLUE;
			case PINK -> DyeColor.PURPLE;
			default -> d;
		};
	}

	static BlockState lantern(boolean hanging) {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, hanging);
	}

	// ------------------------------------------------------------------ reserved zones

	/** The lot a hall room or a gate room belongs to, found by its geometry (null if it is none of the built lots). */
	static @Nullable Lot lotOf(CitadelCanvas c, Room r) {
		for (int id : c.lots.keySet()) {
			Lot l = RingCampusGeometry.lot(id);
			if (l.gate().equals(r)) {
				return l;
			}
			for (int h = 0; h < RingCampusGeometry.HALLS; h++) {
				if (l.hall(h).equals(r)) {
					return l;
				}
			}
		}
		return null;
	}

	private static boolean inZone(Room o, BlockPos p, int u0, int u1, int v0, int v1) {
		int dx = p.getX() - o.origin().getX();
		int dz = p.getZ() - o.origin().getZ();
		int u = dx * o.back().getStepX() + dz * o.back().getStepZ();
		int v = dx * o.side().getStepX() + dz * o.side().getStepZ();
		return u >= u0 && u <= u1 && v >= v0 && v <= v1;
	}

	/**
	 * True when {@code p} (a cell of the eave ring of {@code self}) lies in the footprint or the eave ring of
	 * another building of the lot ({@code l}): both buildings skip it, so the result never depends on which
	 * slots are built. Halls reserve {@code [-2, d+1] x [-2, w+1]}, the gatehouse {@code [-1, d] x [-1, w]}.
	 */
	static boolean reserved(Lot l, Room self, BlockPos p) {
		for (int h = 0; h < RingCampusGeometry.HALLS; h++) {
			Room o = l.hall(h);
			if (!o.equals(self) && inZone(o, p, -2, o.d() + 1, -2, o.w() + 1)) {
				return true;
			}
		}
		Room g = l.gate();
		return !g.equals(self) && inZone(g, p, -1, g.d(), -1, g.w());
	}
}
