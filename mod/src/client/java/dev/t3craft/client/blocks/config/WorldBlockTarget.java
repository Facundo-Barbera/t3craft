package dev.t3craft.client.blocks.config;

import dev.t3craft.T3Craft;
import dev.t3craft.blocks.Binding;
import dev.t3craft.blocks.BoundBlockEntity;
import dev.t3craft.client.blocks.BlocksClient;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A real block (and the blocks edited with it, {@link BlockConfigSection#cells}) in the client's level. Reads come
 * from the client's copy of the block entity; writes go through the integrated server, like
 * {@code BindingWriter} (singleplayer / LAN host). On a remote server the page is read-only.
 */
public final class WorldBlockTarget implements BlockConfigTarget {
	private final BlockPos pos;
	private final BlockConfigSection section;
	private final ResourceKey<Level> dimension;

	public WorldBlockTarget(Level level, BlockPos pos, BlockConfigSection section) {
		this.pos = pos.immutable();
		this.section = section;
		this.dimension = level.dimension();
	}

	private @Nullable Level level() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && mc.level.dimension().equals(dimension) ? mc.level : null;
	}

	@Override
	public BlockConfig current() {
		Level level = level();
		if (level == null || !(level.getBlockEntity(pos) instanceof BoundBlockEntity be)) {
			return BlockConfig.EMPTY;
		}
		// a wall shows its first bound cell's binding; the clicked cell may be an unbound one that joined it
		Binding b = Binding.parse(be.binding());
		if (b.isNone()) {
			for (BlockPos p : section.cells(level, pos)) {
				if (level.getBlockEntity(p) instanceof BoundBlockEntity o && !o.binding().isEmpty()) {
					b = Binding.parse(o.binding());
					break;
				}
			}
		}
		// the same for the options (a monitor added to a wall later has none of its own: the wall's are the page's, T10)
		Map<String, String> options = be.options();
		if (options.isEmpty()) {
			for (BlockPos p : section.cells(level, pos)) {
				if (level.getBlockEntity(p) instanceof BoundBlockEntity o && !o.options().isEmpty()) {
					options = o.options();
					break;
				}
			}
		}
		return new BlockConfig(b, be.manual(), options);
	}

	@Override
	public int cells() {
		Level level = level();
		return level == null ? 1 : Math.max(1, section.cells(level, pos).size());
	}

	@Override
	public @Nullable String readOnlyReason() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getSingleplayerServer() == null) {
			return "Read-only: this world runs on a remote server. Only singleplayer or the LAN host can change a block.";
		}
		return level() == null ? "Read-only: the block is no longer loaded." : null;
	}

	@Override
	public void write(BlockConfig config) {
		String wire = config.binding().wire();
		boolean manual = config.manual();
		Map<String, String> options = config.options();
		onServer(be -> be.setConfig(wire, manual, options));
		BlocksClient bc = BlocksClient.get();
		if (bc != null) {
			bc.bindingsChanged();
		}
	}

	@Override
	public void resetToAutomatic() {
		onServer(be -> be.setManual(false));
		BlocksClient bc = BlocksClient.get();
		if (bc != null) {
			bc.requestHqResync(); // the HQ writes its binding back on the next pass, not in 10 s
		}
	}

	private void onServer(java.util.function.Consumer<BoundBlockEntity> change) {
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server == null || readOnlyReason() != null) {
			return;
		}
		BlockPos at = pos;
		server.execute(() -> {
			ServerLevel level = server.getLevel(dimension);
			if (level == null || !level.isLoaded(at)) {
				return;
			}
			// the cells are read on the server: a wall is edited as a whole
			List<BlockPos> cells = section.cells(level, at);
			for (BlockPos p : cells) {
				if (level.getBlockEntity(p) instanceof BoundBlockEntity be) {
					try {
						change.accept(be);
					} catch (RuntimeException e) {
						T3Craft.LOGGER.warn("T3Craft: block config write failed at {}", p, e);
					}
				}
			}
		});
	}
}
