package dev.t3craft.mapgen.islands;

import dev.t3craft.build.Placement;
import dev.t3craft.mapgen.MapLayout;
import dev.t3craft.mapgen.Piece;
import dev.t3craft.mapgen.PieceSource;
import dev.t3craft.mapgen.Slot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * The {@link PieceSource} the islands world uses ({@link #INSTANCE}): the real citadel catalog first
 * ({@link IslandCatalog}), and for a slot the catalog has nothing for (or when building a catalog piece fails),
 * the old stand-ins of the first cut: a tree for tree slots, a small pavilion for a centre and a lamp post on a
 * stone pad for modules and accents.
 */
public final class IslandProps implements PieceSource {
	public static final IslandProps INSTANCE = new IslandProps();

	private static final BlockState LOG = Blocks.OAK_LOG.defaultBlockState();
	private static final BlockState LEAVES = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
	private static final BlockState BRICKS = Blocks.STONE_BRICKS.defaultBlockState();
	private static final BlockState PAVING = Blocks.POLISHED_ANDESITE.defaultBlockState();
	private static final BlockState FENCE = Blocks.SPRUCE_FENCE.defaultBlockState();
	private static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();
	private static final BlockState HANGING_LANTERN = LANTERN.setValue(BlockStateProperties.HANGING, true);
	private static final BlockState ROOF = Blocks.SPRUCE_SLAB.defaultBlockState();

	private IslandProps() {
	}

	@Override
	public @Nullable Piece pieceFor(Slot slot, MapLayout map) {
		Piece real = IslandCatalog.INSTANCE.pieceFor(slot, map);
		if (real != null && slot.fits(real.size())) {
			return real;
		}
		return standIn(slot);
	}

	/** The first cut's stand-in for {@code slot}, or null (spawn and other roles stay empty). */
	public static @Nullable Piece standIn(Slot slot) {
		return switch (slot.role()) {
			case DECOR -> slot.has("accent") ? lampPost(0) : tree();
			case CENTRE -> pavilion(Math.min(slot.halfAcross(), slot.halfDeep()));
			case MODULE -> lampPost(Math.min(1, Math.min(slot.halfAcross(), slot.halfDeep())));
			default -> null;
		};
	}

	/** An oak: five logs, a two-layer skirt of leaves and a small crown (5x5, 7 high). */
	static Piece tree() {
		List<Placement> out = new ArrayList<>();
		for (int y = 0; y < 5; y++) {
			out.add(new Placement(new BlockPos(0, y, 0), LOG));
		}
		for (int y = 2; y <= 5; y++) {
			int rad = y <= 3 ? 2 : 1;
			for (int dx = -rad; dx <= rad; dx++) {
				for (int dz = -rad; dz <= rad; dz++) {
					boolean corner = Math.abs(dx) == rad && Math.abs(dz) == rad;
					if (dx == 0 && dz == 0 && y <= 4 || corner && (y == 5 || ((dx * 3 + dz * 5 + y) & 1) == 0)) {
						continue;
					}
					out.add(new Placement(new BlockPos(dx, y, dz), LEAVES));
				}
			}
		}
		return new Piece("island/tree", new Piece.Size(5, 5, 7), out);
	}

	/** A paved square with four posts, a slab roof and a hanging lantern; {@code half} 1..4. */
	static Piece pavilion(int half) {
		List<Placement> out = new ArrayList<>();
		for (int x = -half; x <= half; x++) {
			for (int z = -half; z <= half; z++) {
				out.add(new Placement(new BlockPos(x, -1, z), ((x + z) & 1) == 0 ? BRICKS : PAVING));
				out.add(new Placement(new BlockPos(x, 4, z), ROOF));
			}
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				for (int y = 0; y <= 3; y++) {
					out.add(new Placement(new BlockPos(sx * half, y, sz * half), LOG));
				}
			}
		}
		out.add(new Placement(new BlockPos(0, 3, 0), HANGING_LANTERN));
		return new Piece("island/pavilion", new Piece.Size(2 * half + 1, 2 * half + 1, 5), out);
	}

	/** A stone pad with a fence post and a lantern; {@code half} 0..1. */
	static Piece lampPost(int half) {
		List<Placement> out = new ArrayList<>();
		for (int x = -half; x <= half; x++) {
			for (int z = -half; z <= half; z++) {
				out.add(new Placement(new BlockPos(x, -1, z), BRICKS));
			}
		}
		for (int y = 0; y <= 2; y++) {
			out.add(new Placement(new BlockPos(0, y, 0), FENCE));
		}
		out.add(new Placement(new BlockPos(0, 3, 0), LANTERN));
		return new Piece("island/lamp", new Piece.Size(2 * half + 1, 2 * half + 1, 4), out);
	}
}
