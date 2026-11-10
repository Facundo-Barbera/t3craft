package dev.t3craft.hq.plan;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;

/**
 * Resolves connection shapes inside a plan, without a world: glass panes and fences
 * ({@link CrossCollisionBlock}), walls ({@link WallBlock}) and stair corners ({@link StairBlock}).
 * Those blocks store their shape in the state, which vanilla computes from the neighbours when they
 * are placed or updated. Builders place plans without neighbour updates (so attached blocks don't
 * pop off), so the plan has to carry the final shapes itself. With them baked in, the plan is
 * correct in any order and under any {@code Rotation} ({@code BlockState#rotate} turns the shape
 * properties too).
 *
 * <p>It runs vanilla's own {@link Block#updateFromNeighbourShapes} against a read-only view of the
 * plan (cells outside the plan read as air). The view is a dynamic proxy of {@link LevelAccessor}
 * that answers only block/fluid reads and swallows scheduled ticks; anything else throws, and then
 * the cell keeps its original state. Only the three block families above are touched, so blocks
 * whose update would check survival (lanterns, signs, plants) are never changed here.
 */
public final class PlanShapes {
	private PlanShapes() {
	}

	static boolean shaped(BlockState s) {
		Block b = s.getBlock();
		return b instanceof CrossCollisionBlock || b instanceof WallBlock || b instanceof StairBlock;
	}

	/**
	 * Updates, in place, every shaped cell of {@code cells} from its neighbours. Two passes, because a
	 * wall's post depends on the resolved wall above it.
	 */
	public static void resolve(Map<Long, BlockState> cells) {
		LevelAccessor view = view(cells);
		for (int pass = 0; pass < 2; pass++) {
			boolean changed = false;
			for (Map.Entry<Long, BlockState> e : cells.entrySet()) {
				BlockState s = e.getValue();
				if (!shaped(s)) {
					continue;
				}
				BlockState u;
				try {
					u = Block.updateFromNeighbourShapes(s, view, BlockPos.of(e.getKey()));
				} catch (RuntimeException ex) {
					u = s;
				}
				if (u != s && u.getBlock() == s.getBlock()) {
					e.setValue(u);
					changed = true;
				}
			}
			if (!changed) {
				break;
			}
		}
	}

	static LevelAccessor view(Map<Long, BlockState> cells) {
		BlockState air = Blocks.AIR.defaultBlockState();
		RandomSource random = RandomSource.create(0L);
		InvocationHandler h = (proxy, m, args) -> {
			String n = m.getName();
			switch (n) {
				case "getBlockState":
					if (args != null && args.length == 1 && args[0] instanceof BlockPos p) {
						return cells.getOrDefault(p.asLong(), air);
					}
					break;
				case "getFluidState":
					if (args != null && args.length == 1 && args[0] instanceof BlockPos p) {
						return cells.getOrDefault(p.asLong(), air).getFluidState();
					}
					break;
				case "getBlockEntity":
					return null;
				case "getRandom":
					return random;
				case "isClientSide":
					return false;
				case "getMinY":
					return -2048;
				case "getHeight":
					if (args == null || args.length == 0) {
						return 4096;
					}
					break;
				case "scheduleTick":
					return null;
				case "hashCode":
					return System.identityHashCode(proxy);
				case "equals":
					return proxy == args[0];
				case "toString":
					return "PlanShapes.view";
				default:
					break;
			}
			if (m.isDefault()) {
				return InvocationHandler.invokeDefault(proxy, m, args);
			}
			throw new UnsupportedOperationException("PlanShapes view: " + describe(m));
		};
		return (LevelAccessor) Proxy.newProxyInstance(PlanShapes.class.getClassLoader(), new Class<?>[] {LevelAccessor.class}, h);
	}

	private static String describe(Method m) {
		return m.getDeclaringClass().getSimpleName() + "." + m.getName();
	}
}
