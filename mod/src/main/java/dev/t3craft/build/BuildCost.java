package dev.t3craft.build;

import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * How a {@link BuildJob} pays for its blocks. {@code null} wherever a cost is accepted means free
 * (creative, demo, debug): exactly the behaviour before survival charging existed.
 *
 * @param itemOf what one placement costs: an item id, or null when free (air, upper door half, bed
 *               head, blocks without an item). Default: {@link BlockCosts#itemId}.
 * @param supply opens the {@link MaterialSupply} on the server thread, once, on the job's first tick;
 *               a null result counts as {@link MaterialSupply#EMPTY} (the job waits for materials)
 */
public record BuildCost(Function<BlockState, @Nullable String> itemOf, Opener supply) {
	@FunctionalInterface
	public interface Opener {
		@Nullable MaterialSupply open(MinecraftServer server, BuildJob job);
	}

	public static BuildCost of(Opener supply) {
		return new BuildCost(BlockCosts::itemId, supply);
	}
}
