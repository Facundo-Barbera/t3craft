package dev.t3craft.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Built-in plans for testing ({@code /t3craft debug build-demo}). */
public final class DemoPlans {
	private DemoPlans() {
	}

	/**
	 * A 7x7 shed: cobblestone floor one block below the origin, log corners, plank walls with glass
	 * windows, a door in the middle of the front (north, {@code z = 0}), a chest with some bread
	 * (block entity data), a crafting table, a wall torch, and a stepped slab roof with eaves.
	 * The interior is cleared with air first. Ordered bottom-up with {@link BuildPlan#layered()}.
	 */
	public static BuildPlan shed() {
		BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		BlockState log = Blocks.OAK_LOG.defaultBlockState();
		BlockState glass = Blocks.GLASS_PANE.defaultBlockState();
		BlockState ceiling = Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState eave = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
		BlockState roof = Blocks.OAK_SLAB.defaultBlockState();

		BuildPlan.Builder b = BuildPlan.builder();
		b.fill(-3, -1, 0, 3, -1, 6, cobble);
		b.fill(-2, 0, 1, 2, 2, 5, air);
		// Walls: plank ring, log corners.
		for (int y = 0; y <= 2; y++) {
			for (int x = -3; x <= 3; x++) {
				b.set(x, y, 0, planks);
				b.set(x, y, 6, planks);
			}
			for (int z = 1; z <= 5; z++) {
				b.set(-3, y, z, planks);
				b.set(3, y, z, planks);
			}
			b.set(-3, y, 0, log);
			b.set(3, y, 0, log);
			b.set(-3, y, 6, log);
			b.set(3, y, 6, log);
		}
		b.set(-3, 1, 3, glass);
		b.set(3, 1, 3, glass);
		b.set(0, 1, 6, glass);
		b.set(-2, 1, 0, glass);
		b.set(2, 1, 0, glass);
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
		b.set(0, 0, 0, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		b.set(0, 1, 0, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

		// Inside.
		b.set(new BlockPos(-2, 0, 5), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), chestLoot());
		b.set(2, 0, 5, Blocks.CRAFTING_TABLE.defaultBlockState());
		b.set(0, 2, 5, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));

		// Roof: plank ceiling, slab eaves one block out, a raised slab cap in the middle.
		b.fill(-3, 3, 0, 3, 3, 6, ceiling);
		for (int x = -4; x <= 4; x++) {
			b.set(x, 3, -1, eave);
			b.set(x, 3, 7, eave);
		}
		for (int z = 0; z <= 6; z++) {
			b.set(-4, 3, z, eave);
			b.set(4, 3, z, eave);
		}
		b.fill(-2, 4, 1, 2, 4, 5, roof);
		return b.build().layered();
	}

	private static CompoundTag chestLoot() {
		ListTag items = new ListTag();
		items.add(stack(0, "minecraft:bread", 6));
		items.add(stack(1, "minecraft:oak_sapling", 2));
		items.add(stack(13, "minecraft:torch", 8));
		CompoundTag tag = new CompoundTag();
		tag.put("Items", items);
		return tag;
	}

	private static CompoundTag stack(int slot, String id, int count) {
		CompoundTag t = new CompoundTag();
		t.putByte("Slot", (byte) slot);
		t.putString("id", id);
		t.putInt("count", count);
		return t;
	}

	/**
	 * Rotation that turns a plan built with the front to the north ({@link BuildPlan} convention)
	 * so it extends away from a viewer looking {@code facing}, front toward the viewer.
	 */
	public static Rotation rotationFacingViewer(Direction facing) {
		return switch (facing) {
			case WEST -> Rotation.CLOCKWISE_90;
			case NORTH -> Rotation.CLOCKWISE_180;
			case EAST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}
}
