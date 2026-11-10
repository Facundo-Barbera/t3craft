package dev.t3craft.client.islands;

import dev.t3craft.T3Craft;
import dev.t3craft.build.Placement;
import dev.t3craft.build.ServerLevelSink;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.demo.VanillaDemoWorld;
import dev.t3craft.mapgen.islands.Bridge;
import dev.t3craft.mapgen.islands.BridgePlanner;
import dev.t3craft.mapgen.islands.Island;
import dev.t3craft.mapgen.islands.IslandBlocks;
import dev.t3craft.mapgen.islands.IslandField;
import dev.t3craft.mapgen.islands.IslandProps;
import dev.t3craft.mapgen.islands.IslandSize;
import dev.t3craft.mapgen.islands.bridge.BridgeNetwork;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
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
 *   <li><b>Bridges</b> (#32): islands link in up to eight directions ({@link BridgeNetwork}). For a player
 *       standing on a built island the runtime picks the bridge exit that lies ahead of them (the way they walk,
 *       or look when standing still) and is nearest ({@link #nearEdge}); within {@value #HINT} blocks the exit
 *       shows end-rod sparks, within {@value #TRIGGER} blocks (while heading towards it, or {@value #CLOSE}
 *       blocks in any direction) the bridge grows from that edge to the other island,
 *       {@value #BRIDGE_STEPS_PER_TICK} cross-sections per tick with a chime, block sounds and sparks at its tip,
 *       and the destination island is moved to the front of the queue. A finished bridge is remembered in
 *       {@link IslandsState} (same ids as the first cut).</li>
 *   <li><b>No falling to death</b>: a player who drops far below every island (and does not fly, glide, ride or
 *       spectate) is put back where they last stood on solid ground.</li>
 * </ul>
 */
final class IslandsRuntime {
	static final int GEN_RADIUS = 140;
	static final int BLOCKS_PER_TICK = 1500;
	static final int BRIDGE_STEPS_PER_TICK = 2;
	/** A bridge exit ahead of the player within this many blocks shows sparks. */
	static final int HINT = 16;
	/** A bridge exit ahead of the player within this many blocks starts the bridge. */
	static final int TRIGGER = 7;
	/** A bridge exit this close starts the bridge whichever way the player faces. */
	static final int CLOSE = 3;
	/** Cosine of the widest angle between heading and exit that still counts as "ahead" (about 70°). */
	private static final double AHEAD = 0.35;
	/** Cosine needed to start a bridge from {@value #TRIGGER} blocks (about 50°). */
	private static final double TOWARDS = 0.65;
	private static final int RESCUE_COOLDOWN = 20;
	/**
	 * New bridge plans per check ({@link #nearEdge} and {@link #warm} each run every 4 ticks). Working out the links
	 * of an island the first time plans dozens of bridges, so it is spread over ticks instead of done at once.
	 */
	private static final int PLANS_PER_CHECK = 2;

	private static volatile @Nullable IslandsRuntime active;

	private final IslandField field = IslandsWorld.field();
	private final IslandsState state;
	private final List<IslandJob> islandJobs = new ArrayList<>();
	private final Set<String> queued = new HashSet<>();
	private final List<BridgeJob> bridgeJobs = new ArrayList<>();
	private final Set<String> activeBridges = new HashSet<>();
	private final BridgeNetwork network = new BridgeNetwork(field);
	/** Islands whose links are all planned (see {@link #warmed}). */
	private final Set<String> linked = new HashSet<>();
	private final Map<UUID, Vec3> lastPos = new HashMap<>();
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
			} else if (ticks % 4 == 2) {
				warm(p);
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
				BlockPos end = j.end();
				if (end != null) {
					level.playSound(null, end, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5F, 1.4F);
				}
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

	/**
	 * Picks the bridge exit of the island the player stands on that lies ahead of them: among the links not built
	 * yet, those within {@value #HINT} blocks and less than about 70° off the player's heading (any direction
	 * within {@value #CLOSE} blocks), the one with the lowest {@code distance × (2 - cos angle)}. Close enough and
	 * heading towards it, the bridge starts; otherwise its exit shows sparks.
	 */
	private void nearEdge(ServerLevel level, ServerPlayer p) {
		Vec3 head = heading(p);
		Island cur = field.islandAt(p.getBlockX(), p.getBlockZ(), 1.5);
		if (cur == null || p.getY() < cur.top() - 1 || p.getY() > cur.top() + 8 || !state.hasIsland(cur.id())) {
			return;
		}
		if (!warmed(cur)) {
			return; // its bridges are still being planned, a few per check
		}
		Bridge best = null;
		Island bestTo = null;
		double bestScore = Double.MAX_VALUE;
		double bestDist = 0;
		double bestCos = 0;
		for (Island n : network.links(cur)) {
			String id = BridgePlanner.idOf(cur, n);
			if (state.hasBridge(id) || activeBridges.contains(id)) {
				continue;
			}
			Bridge b = network.bridge(cur, n);
			if (b == null) {
				continue;
			}
			BlockPos s = b.startOn(cur.id());
			double vx = s.getX() + 0.5 - p.getX();
			double vz = s.getZ() + 0.5 - p.getZ();
			double d = Math.sqrt(vx * vx + vz * vz);
			if (d > HINT) {
				continue;
			}
			double cos = d < 1e-3 ? 1.0 : (vx * head.x + vz * head.z) / d;
			if (d > CLOSE && cos < AHEAD) {
				continue;
			}
			double score = d * (2.0 - cos);
			if (score < bestScore) {
				bestScore = score;
				best = b;
				bestTo = n;
				bestDist = d;
				bestCos = cos;
			}
		}
		if (best == null || bestTo == null) {
			return;
		}
		if (bestDist <= CLOSE || (bestDist <= TRIGGER && bestCos >= TOWARDS)) {
			begin(level, p, cur, bestTo, best);
		} else {
			hint(level, best.startOn(cur.id()));
		}
	}

	/**
	 * Plans, ahead of time, the bridges of the island under the player and then of the islands it links to (the
	 * ones the player may cross next): {@value #PLANS_PER_CHECK} plans per call, so nothing stutters.
	 */
	private void warm(ServerPlayer p) {
		Island cur = field.islandAt(p.getBlockX(), p.getBlockZ(), 1.5);
		if (cur == null || !warmed(cur)) {
			return;
		}
		for (Island n : network.links(cur)) {
			if (!warmed(n)) {
				return; // the rest next time
			}
		}
	}

	/** Whether every bridge {@code i}'s links need is planned; if not, plans a few more. */
	private boolean warmed(Island i) {
		if (linked.contains(i.id())) {
			return true;
		}
		if (network.links(i, PLANS_PER_CHECK) == null) {
			return false;
		}
		linked.add(i.id());
		return true;
	}

	/** Where the player is heading: the way they moved since the last check, or where they look when standing still. */
	private Vec3 heading(ServerPlayer p) {
		Vec3 now = p.position();
		Vec3 before = lastPos.put(p.getUUID(), now);
		if (before != null) {
			double mx = now.x - before.x;
			double mz = now.z - before.z;
			if (mx * mx + mz * mz > 0.04) {
				return new Vec3(mx, 0, mz).normalize();
			}
		}
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() > 1.0E-4 ? flat.normalize() : Vec3.ZERO;
	}

	/** End-rod sparks over a bridge exit: a small column of light where the deck will leave the edge. */
	private static void hint(ServerLevel level, BlockPos s) {
		level.sendParticles(ParticleTypes.END_ROD, s.getX() + 0.5, s.getY() + 1.6, s.getZ() + 0.5, 3, 0.35, 0.5, 0.35, 0.01);
	}

	private void begin(ServerLevel level, ServerPlayer p, Island from, Island to, Bridge b) {
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
		BlockPos s = b.startOn(from.id());
		level.playSound(null, s, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5F, 0.8F);
		p.sendSystemMessage(Component.literal("A " + b.style().label() + " bridge unfolds towards the " + to.size().label() + " island")
				.withColor(accent()), true);
		T3Craft.LOGGER.info("[islands] {} bridge {} starts at {} ({} deck steps, {} sections)", b.style().label(), b.id(), s.toShortString(),
				b.deck(), b.steps().size());
	}

	/** Action-bar text colour: the theme's brass token. */
	private static int accent() {
		return ChatTheme.warmStudio().brass() & 0xFFFFFF;
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
			p.sendSystemMessage(Component.literal("Back on solid ground: the void is not part of the islands.").withColor(accent()), true);
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
			ServerLevelSink sink = new ServerLevelSink(level, Block.UPDATE_ALL, false);
			while (budget > 0 && next < list.size()) {
				Placement p = list.get(next);
				if (!force && !level.isLoaded(p.pos())) {
					return budget;
				}
				level.setBlock(p.pos(), p.state(), Block.UPDATE_ALL);
				if (p.hasBlockEntity()) {
					// signs, banners...: the citadel's way (ServerLevelSink) loads the piece's block entity data into what is there now
					sink.place(p.pos(), level.getBlockState(p.pos()), p.blockEntity());
				}
				next++;
				budget--;
			}
			return budget;
		}
	}

	/**
	 * A bridge's cross-sections in building order, each slice from what holds it up to its lights. Blocks take
	 * their connections from what is already there ({@link Block#updateFromNeighbourShapes}), so rails join up as
	 * they grow. Placing is idempotent, so an interrupted bridge is simply built again.
	 */
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

		/** The centre of the last section: where the bridge meets the far island. */
		@Nullable BlockPos end() {
			return steps.isEmpty() ? null : steps.get(steps.size() - 1).centre();
		}

		void run(ServerLevel level, int count) {
			Bridge.Step tip = null;
			for (int n = 0; n < count && next < steps.size(); n++) {
				Bridge.Step s = steps.get(next);
				for (Placement p : s.blocks()) {
					if (!level.isLoaded(p.pos())) {
						effects(level, tip);
						return; // retried next tick
					}
				}
				for (Placement p : s.blocks()) {
					BlockState st = Block.updateFromNeighbourShapes(p.state(), level, p.pos());
					level.setBlock(p.pos(), st, Block.UPDATE_ALL);
				}
				tip = s;
				next++;
			}
			effects(level, tip);
		}

		/** Growth feedback at the tip: the deck's own placing sound and a few end-rod sparks. */
		private static void effects(ServerLevel level, Bridge.@Nullable Step tip) {
			if (tip == null) {
				return;
			}
			BlockPos c = tip.centre();
			BlockState deck = level.getBlockState(c);
			if (!deck.isAir()) {
				SoundType sound = deck.getSoundType();
				level.playSound(null, c, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
			}
			level.sendParticles(ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() + 1.2, c.getZ() + 0.5, 4, 0.6, 0.3, 0.6, 0.02);
		}
	}
}
