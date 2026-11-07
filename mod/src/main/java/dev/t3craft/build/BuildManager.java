package dev.t3craft.build;

import dev.t3craft.T3Craft;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import org.jspecify.annotations.Nullable;

/**
 * Runs {@link BuildJob}s on the server thread, one {@link #tick} per server tick. Not a singleton
 * and no static state: the owner (here the client entrypoint, for the integrated server) holds one
 * instance and calls {@link #clear()} when the server stops, so nothing survives a world change or
 * a hot reload with a stale type.
 *
 * <p>{@link #submit}, {@link #job}, {@link #jobs} and the job controls are safe from any thread;
 * {@link #tick} and listener callbacks happen on the server thread.
 */
public final class BuildManager {
	private final AtomicInteger nextId = new AtomicInteger(1);
	private final Queue<BuildJob> incoming = new ConcurrentLinkedQueue<>();
	private final List<BuildJob> active = new ArrayList<>();
	private final Map<Integer, BuildJob> byId = new ConcurrentHashMap<>();
	private final List<BuildListener> listeners = new CopyOnWriteArrayList<>();
	private final Function<ServerLevel, BlockSink> sinks;
	private final Map<Integer, UnaryOperator<BlockSink>> wraps = new ConcurrentHashMap<>();

	public BuildManager() {
		this(ServerLevelSink::new);
	}

	/** @param sinks how to place into a level (default {@link ServerLevelSink}). */
	public BuildManager(Function<ServerLevel, BlockSink> sinks) {
		this.sinks = sinks;
	}

	public void addListener(BuildListener l) {
		listeners.add(l);
	}

	public void removeListener(BuildListener l) {
		listeners.remove(l);
	}

	/** Creates and queues a job; it starts on the next server tick. */
	public BuildJob submit(String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick) {
		BuildJob job = new BuildJob(nextId.getAndIncrement(), label, dimension, origin, rotation, plan, blocksPerTick);
		byId.put(job.id(), job);
		incoming.add(job);
		return job;
	}

	/** Like {@link #submit}, with every block of this job placed through {@code wrap.apply(sink)}. */
	public BuildJob submit(String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick,
			@Nullable UnaryOperator<BlockSink> wrap) {
		BuildJob job = new BuildJob(nextId.getAndIncrement(), label, dimension, origin, rotation, plan, blocksPerTick);
		if (wrap != null) {
			wraps.put(job.id(), wrap);
		}
		byId.put(job.id(), job);
		incoming.add(job);
		return job;
	}

	public @Nullable BuildJob job(int id) {
		return byId.get(id);
	}

	/** Jobs not yet finished (pending, running or paused), any thread. */
	public Collection<BuildJob> jobs() {
		return List.copyOf(byId.values());
	}

	/** Server thread. */
	public void tick(MinecraftServer server) {
		BuildJob polled;
		while ((polled = incoming.poll()) != null) {
			BuildJob job = polled;
			active.add(job);
			for (BuildListener l : listeners) {
				safe(() -> l.started(job));
			}
		}
		Iterator<BuildJob> it = active.iterator();
		while (it.hasNext()) {
			BuildJob job = it.next();
			if (!job.state().isTerminal()) {
				ServerLevel level = server.getLevel(job.dimension());
				if (level != null) {
					BlockSink base = sinks.apply(level);
					UnaryOperator<BlockSink> wrap = wraps.get(job.id());
					BlockSink sink = wrap == null ? base : wrap.apply(base);
					try {
						job.tick(sink, (jb, i, pos, state) -> {
							for (BuildListener l : listeners) {
								safe(() -> l.placed(jb, i, pos, state));
							}
						});
					} catch (RuntimeException e) {
						T3Craft.LOGGER.error("build job {} failed, cancelling", job, e);
						job.cancel();
					}
				}
			}
			JobState s = job.state();
			if (s.isTerminal()) {
				it.remove();
				byId.remove(job.id());
				wraps.remove(job.id());
				for (BuildListener l : listeners) {
					safe(() -> l.finished(job, s));
				}
			}
		}
	}

	/** Cancels and forgets every job (server stopping / world change). Listeners are kept. */
	public void clear() {
		List<BuildJob> all = new ArrayList<>(active);
		BuildJob polled;
		while ((polled = incoming.poll()) != null) {
			all.add(polled);
		}
		active.clear();
		byId.clear();
		wraps.clear();
		for (BuildJob job : all) {
			job.cancel();
			for (BuildListener l : listeners) {
				safe(() -> l.finished(job, JobState.CANCELLED));
			}
		}
	}

	private static void safe(Runnable r) {
		try {
			r.run();
		} catch (RuntimeException e) {
			T3Craft.LOGGER.warn("build listener failed", e);
		}
	}
}
