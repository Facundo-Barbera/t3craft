package dev.t3craft.client.demo;

import dev.t3craft.T3Craft;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * feat/hq-void-world: nobody falls into the void of the HQ world. Runs on the integrated server's
 * thread, only while it runs the official HQ world <b>and</b> that world is a void world
 * ({@link DemoClient} starts and stops it; anywhere else it does not exist, so the mod does nothing:
 * the fix/hq-gate-npcs rule). Always on in that world, not only while the HQ is being built: the
 * island has edges for as long as the world exists.
 *
 * <p>Movement is never blocked: players walk, jump and build freely. Only when a player in the
 * overworld drops below {@link #fallY} (a margin under the deepest island) are they put back,
 * without damage, on the last spot they stood safely on (solid ground below, room for the body),
 * else on the starter island, else on the HQ's own spawn, with their speed and fall distance reset
 * and a short note on the action bar. Players who fly (creative flight), glide (elytra), ride, or
 * spectate are never touched: flying below the island is their choice.
 */
final class VoidGuard {
	/** Blocks below the origin's ground where a fall counts as "into the void". */
	static final int FALL_DEPTH = VoidIsland.MAX_DEPTH + 10;
	/** Ticks between two updates of a player's last safe spot. */
	private static final int SAFE_EVERY = 10;
	/** At most one rescue per player in this many ticks (the client needs a moment to land). */
	private static final int COOLDOWN = 20;

	private static volatile @Nullable VoidGuard active;

	private final int fallY;
	private final BlockPos starterSpawn;
	private final @Nullable BlockPos hqSpawn;
	private final Map<UUID, BlockPos> lastSafe = new HashMap<>();
	private final Map<UUID, Integer> cooldown = new HashMap<>();
	private int ticks;

	private VoidGuard(int fallY, BlockPos starterSpawn, @Nullable BlockPos hqSpawn) {
		this.fallY = fallY;
		this.starterSpawn = starterSpawn.immutable();
		this.hqSpawn = hqSpawn == null ? null : hqSpawn.immutable();
	}

	/** Below this y a grounded (non-flying) player is rescued: under the deepest island, above vanilla's void damage. */
	static int fallY(int originY, int worldMinY) {
		return Math.max(originY - FALL_DEPTH, worldMinY - 40);
	}

	/** Starts guarding the HQ world (server thread). {@code starterSpawn}/{@code hqSpawn} are world cells where feet go. */
	static void start(int originY, int worldMinY, BlockPos starterSpawn, @Nullable BlockPos hqSpawn) {
		active = new VoidGuard(fallY(originY, worldMinY), starterSpawn, hqSpawn);
		T3Craft.LOGGER.info("[demo] void guard on (rescue below y {})", fallY(originY, worldMinY));
	}

	static void stop() {
		active = null;
	}

	/** Server tick hook (registered once; a no-op unless {@link #start} ran for the HQ world). */
	static void tick(MinecraftServer server) {
		VoidGuard g = active;
		if (g != null) {
			g.run(server);
		}
	}

	private void run(MinecraftServer server) {
		ticks++;
		ServerLevel level = server.overworld();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!Level.OVERWORLD.equals(p.level().dimension())) {
				continue;
			}
			UUID id = p.getUUID();
			cooldown.computeIfPresent(id, (k, v) -> v > 1 ? v - 1 : null);
			if (p.isSpectator() || p.getAbilities().flying || p.isFallFlying() || p.isPassenger()) {
				continue; // never interfere with flight, gliding, riding or spectating
			}
			if (ticks % SAFE_EVERY == 0 && p.onGround() && p.getY() > fallY + 4 && safe(level, p.blockPosition())) {
				lastSafe.put(id, p.blockPosition().immutable());
			}
			if (p.getY() < fallY && !cooldown.containsKey(id)) {
				rescue(level, p);
				cooldown.put(id, COOLDOWN);
			}
		}
	}

	/** Feet cell {@code feet}: solid ground below, room for the body. */
	private static boolean safe(ServerLevel level, BlockPos feet) {
		BlockPos below = feet.below();
		return !level.getBlockState(below).getCollisionShape(level, below).isEmpty() && !level.getBlockState(below).liquid()
				&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
				&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
	}

	private void rescue(ServerLevel level, ServerPlayer p) {
		BlockPos to = lastSafe.get(p.getUUID());
		if (to == null || !safe(level, to)) {
			to = safe(level, starterSpawn) ? starterSpawn : hqSpawn != null && safe(level, hqSpawn) ? hqSpawn : null;
		}
		if (to == null) {
			// Nothing standing anywhere (the starter island was swallowed and the HQ is not up yet): one block to stand on.
			to = starterSpawn;
			if (level.getBlockState(to.below()).isAir()) {
				level.setBlock(to.below(), Blocks.GRASS_BLOCK.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
			}
		}
		p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, Set.of(), p.getYRot(), p.getXRot(), true);
		p.setDeltaMovement(Vec3.ZERO);
		p.resetFallDistance();
		p.needsSync = true; // send the zeroed motion to the client
		p.sendSystemMessage(Component.translatable("t3craft.demo.void.rescued").withStyle(ChatFormatting.AQUA), true);
		T3Craft.LOGGER.info("[demo] void guard: {} back on solid ground at {}", p.getGameProfile().name(), to.toShortString());
	}
}
