package dev.t3craft.client.islands;

import dev.t3craft.T3Craft;
import dev.t3craft.build.Placement;
import dev.t3craft.client.demo.VanillaDemoWorld;
import dev.t3craft.mapgen.islands.Bridge;
import dev.t3craft.mapgen.islands.BridgePlanner;
import dev.t3craft.mapgen.islands.Island;
import dev.t3craft.mapgen.islands.IslandBlocks;
import dev.t3craft.mapgen.islands.IslandField;
import dev.t3craft.mapgen.islands.IslandProps;
import dev.t3craft.mapgen.islands.IslandSize;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The server side of the floating-islands world, on the integrated server's thread. Alive only while the server
 * runs {@link IslandsWorld#FOLDER} ({@link IslandsClient} starts and stops it); anywhere else the mod does
 * nothing here.
 *
 * <ul>
 *   <li><b>Islands</b> appear around the players: every second the islands of {@link IslandField} within
 *       {@value #GEN_RADIUS} blocks of a player that the world does not have yet are queued, nearest first, and
 *       built {@value #BLOCKS_PER_TICK} blocks per tick, only into loaded chunks. The spawn island is built
 *       before the first player joins.</li>
 *   <li><b>Bridges</b> appear when a player standing on an island comes within {@value #TRIGGER} blocks of the
 *       place where a bridge to a neighbour island leaves that island's edge: it grows from that edge to the
 *       other island, {@value #BRIDGE_STEPS_PER_TICK} cross-sections per tick, and the destination island is
 *       moved to the front of the queue. A finished bridge is remembered in {@link IslandsState}.</li>
 *   <li><b>No falling to death</b>: a player who drops far below every island (and does not fly, glide, ride or
 *       spectate) is put back where they last stood on solid ground.</li>
 * </ul>
 */
final class IslandsRuntime {
	static final int GEN_RADIUS = 140;
	static final int BLOCKS_PER_TICK = 1500;
	static final int BRIDGE_STEPS_PER_TICK = 2;
	static final int TRIGGER = 9;
	private static final int RESCUE_COOLDOWN = 20;

	private static volatile @Nullable IslandsRuntime active;

	private final IslandField field = new IslandField(IslandsWorld.SEED, IslandsWorld.BASE_Y);
	private final IslandsState state;
	private final List<IslandJob> islandJobs = new ArrayList<>();
	private final Set<String> queued = new HashSet<>();
	private final List<BridgeJob> bridgeJobs = new ArrayList<>();
	private final Set<String> activeBridges = new HashSet<>();
	private final Map<String, Optional<Bridge>> bridges = new HashMap<>();
	private final Map<UUID, BlockPos> lastSafe = new HashMap<>();
	private final Map<UUID, Integer> cooldown = new HashMap<>();
	private final int fallY = IslandsWorld.BASE_Y - IslandField.HEIGHT_RANGE - IslandSize.XLARGE.depth() - 20;
	private int ticks;

	private IslandsRuntime(Path worldRoot) {
		this.state = IslandsState.load(worldRoot);
	}

	static boolean running() {
		return active != null;
	}

	/** Server thread, once the islands world's server is up: builds the spawn island if the world is new. */
	static void start(MinecraftServer server, Path worldRoot) {
		IslandsRuntime r = new IslandsRuntime(worldRoot);
		Island spawn = r.field.origin();
		if (!r.state.hasIsland(spawn.id())) {
			new IslandJob(r.field, spawn).run(server.overworld(), Integer.MAX_VALUE, true);
			r.state.addIsland(spawn.id());
			r.state.saveIfDirty();
			VanillaDemoWorld.applySetup(server, IslandsWorld.spec(), spawn.surface().offset(0, 0, 4));
			T3Craft.LOGGER.info("[islands] new world: spawn island built at {}", spawn.surface().toShortString());
		}
		active = r;
		T3Craft.LOGGER.info("[islands] on ({} islands, {} bridges built so far)", r.state.islandCount(), r.state.bridgeCount());
	}

	static void stop() {
		IslandsRuntime r = active;
		active = null;
		if (r != null) {
			r.state.saveIfDirty();
		}
	}

	static void tick(MinecraftServer server) {
		IslandsRuntime r = active;
		if (r != null) {
			r.run(server);
		}
	}

	static String describe() {
		IslandsRuntime r = active;
		if (r == null) {
			return "not in the islands world";
		}
		return r.state.islandCount() + " islands and " + r.state.bridgeCount() + " bridges built, " + r.islandJobs.size()
				+ " islands and " + r.bridgeJobs.size() + " bridges in progress";
	}

	// ------------------------------------------------------------------ tick

	private void run(MinecraftServer server) {
		ticks++;
		ServerLevel level = server.overworld();
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (Level.OVERWORLD.equals(p.level().dimension())) {
				players.add(p);
			}
		}
		if (ticks % 20 == 1) {
			schedule(players);
		}
		for (ServerPlayer p : players) {
			cooldown.computeIfPresent(p.getUUID(), (k, v) -> v > 1 ? v - 1 : null);
			if (p.isSpectator()) {
				continue;
			}
			if (ticks % 4 == 0) {
				nearEdge(level, p);
			}
			rescue(level, p);
		}
		pump(level);
		if (ticks % 200 == 0) {
			state.saveIfDirty();
		}
	}

	/** Queues the islands within reach of a player that the world does not have yet, nearest first. */
	private void schedule(List<ServerPlayer> players) {
		for (ServerPlayer p : players) {
			for (Island i : field.within(p.getBlockX(), p.getBlockZ(), GEN_RADIUS)) {
				if (!state.hasIsland(i.id()) && queued.add(i.id())) {
					islandJobs.add(new IslandJob(field, i));
				}
			}
		}
		if (islandJobs.size() > 1 && !players.isEmpty()) {
			islandJobs.sort(Comparator.comparingDouble(j -> nearest(players, j.island)));
		}
	}

	private static double nearest(List<ServerPlayer> players, Island i) {
		double best = Double.MAX_VALUE;
		for (ServerPlayer p : players) {
			best = Math.min(best, i.distanceTo(p.getBlockX(), p.getBlockZ()));
		}
		return best;
	}

	/** Builds bridges first (they are small and visible), then islands within this tick's block budget. */
	private void pump(ServerLevel level) {
		for (Iterator<BridgeJob> it = bridgeJobs.iterator(); it.hasNext();) {
			BridgeJob j = it.next();
			j.run(level, BRIDGE_STEPS_PER_TICK);
			if (j.done()) {
				state.addBridge(j.bridge.id());
				activeBridges.remove(j.bridge.id());
				it.remove();
			}
		}
		int budget = BLOCKS_PER_TICK;
		for (Iterator<IslandJob> it = islandJobs.iterator(); it.hasNext() && budget > 0;) {
			IslandJob j = it.next();
			budget = j.run(level, budget, false);
			if (j.done()) {
				state.addIsland(j.island.id());
				queued.remove(j.island.id());
				it.remove();
			}
		}
	}

	// ------------------------------------------------------------------ bridges

	private @Nullable Bridge bridge(Island a, Island b) {
		return bridges.computeIfAbsent(BridgePlanner.idOf(a, b), k -> Optional.ofNullable(BridgePlanner.plan(a, b))).orElse(null);
	}

	/** A player standing on a built island near the spot where a bridge leaves it starts that bridge. */
	private void nearEdge(ServerLevel level, ServerPlayer p) {
		Island cur = field.islandAt(p.getBlockX(), p.getBlockZ(), 1.5);
		if (cur == null || p.getY() < cur.top() - 1 || p.getY() > cur.top() + 8 || !state.hasIsland(cur.id())) {
			return;
		}
		for (Island n : field.neighbours(cur)) {
			String id = BridgePlanner.idOf(cur, n);
			if (state.hasBridge(id) || activeBridges.contains(id)) {
				continue;
			}
			Bridge b = bridge(cur, n);
			if (b == null) {
				state.addBridge(id); // nothing to cross between them
				continue;
			}
			BlockPos s = b.startOn(cur.id());
			double dx = p.getX() - (s.getX() + 0.5);
			double dz = p.getZ() - (s.getZ() + 0.5);
			if (dx * dx + dz * dz <= (double) TRIGGER * TRIGGER) {
				begin(p, cur, n, b);
			}
		}
	}

	private void begin(ServerPlayer p, Island from, Island to, Bridge b) {
		activeBridges.add(b.id());
		bridgeJobs.add(new BridgeJob(b, b.stepsFrom(from.id())));
		if (!state.hasIsland(to.id())) {
			if (queued.add(to.id())) {
				islandJobs.add(0, new IslandJob(field, to));
			} else {
				islandJobs.stream().filter(j -> j.island.id().equals(to.id())).findFirst().ifPresent(j -> {
					islandJobs.remove(j);
					islandJobs.add(0, j);
				});
			}
		}
		p.sendSystemMessage(Component.literal("A bridge unfolds towards the " + to.size().label() + " island").withStyle(ChatFormatting.AQUA), true);
		T3Craft.LOGGER.info("[islands] bridge {} starts at {} ({} steps)", b.id(), b.startOn(from.id()).toShortString(), b.steps().size());
	}

	// ------------------------------------------------------------------ falling

	private void rescue(ServerLevel level, ServerPlayer p) {
		if (p.getAbilities().flying || p.isFallFlying() || p.isPassenger()) {
			return;
		}
		UUID id = p.getUUID();
		if (ticks % 10 == 0 && p.onGround() && p.getY() > fallY + 4) {
			lastSafe.put(id, p.blockPosition().immutable());
		}
		if (p.getY() < fallY && !cooldown.containsKey(id)) {
			BlockPos to = lastSafe.getOrDefault(id, field.origin().surface().offset(0, 0, 4));
			p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, Set.of(), p.getYRot(), p.getXRot(), true);
			p.setDeltaMovement(Vec3.ZERO);
			p.resetFallDistance();
			p.needsSync = true;
			cooldown.put(id, RESCUE_COOLDOWN);
			p.sendSystemMessage(Component.literal("Back on solid ground: the void is not part of the islands.").withStyle(ChatFormatting.AQUA), true);
			T3Craft.LOGGER.info("[islands] {} fell off, back at {}", p.getGameProfile().name(), to.toShortString());
		}
	}

	// ------------------------------------------------------------------ jobs

	/** One island's blocks, placed in order; computed when its chunks are first loaded. */
	private static final class IslandJob {
		final IslandField field;
		final Island island;
		private @Nullable List<Placement> blocks;
		private int next;

		IslandJob(IslandField field, Island island) {
			this.field = field;
			this.island = island;
		}

		boolean done() {
			return blocks != null && next >= blocks.size();
		}

		/** Places up to {@code budget} blocks; returns what is left. Stops at the first block in an unloaded chunk unless {@code force}. */
		int run(ServerLevel level, int budget, boolean force) {
			if (blocks == null) {
				if (!force && !level.isLoaded(island.surface())) {
					return budget;
				}
				blocks = IslandBlocks.build(field, island, IslandProps.INSTANCE);
			}
			List<Placement> list = blocks;
			while (budget > 0 && next < list.size()) {
				Placement p = list.get(next);
				if (!force && !level.isLoaded(p.pos())) {
					return budget;
				}
				level.setBlock(p.pos(), p.state(), Block.UPDATE_ALL);
				next++;
				budget--;
			}
			return budget;
		}
	}

	/** A bridge's cross-sections in building order. Placing is idempotent, so an interrupted bridge is simply built again. */
	private static final class BridgeJob {
		final Bridge bridge;
		private final List<Bridge.Step> steps;
		private int next;

		BridgeJob(Bridge bridge, List<Bridge.Step> steps) {
			this.bridge = bridge;
			this.steps = steps;
		}

		boolean done() {
			return next >= steps.size();
		}

		void run(ServerLevel level, int count) {
			for (int n = 0; n < count && next < steps.size(); n++) {
				Bridge.Step s = steps.get(next);
				for (Placement p : s.blocks()) {
					if (!level.isLoaded(p.pos())) {
						return; // retried next tick
					}
				}
				for (Placement p : s.blocks()) {
					level.setBlock(p.pos(), p.state(), Block.UPDATE_ALL);
				}
				next++;
			}
		}
	}
}
