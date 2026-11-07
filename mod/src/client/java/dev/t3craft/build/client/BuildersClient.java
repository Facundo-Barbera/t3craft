package dev.t3craft.build.client;

import dev.t3craft.T3Craft;
import dev.t3craft.build.BuildCost;
import dev.t3craft.build.BuildDeniedException;
import dev.t3craft.build.BuildHandle;
import dev.t3craft.build.BuildJob;
import dev.t3craft.build.BuildListener;
import dev.t3craft.build.BuildManager;
import dev.t3craft.build.BuildPlan;
import dev.t3craft.build.BuildService;
import dev.t3craft.build.Builders;
import dev.t3craft.build.JobState;
import dev.t3craft.survival.BuildMaterials;
import dev.t3craft.survival.PlayContexts;
import dev.t3craft.survival.Survival;
import dev.t3craft.survival.Verdict;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Builders entrypoint (its own {@code client} entrypoint in fabric.mod.json, so it does not touch
 * {@code T3CraftClient}). Wires:
 * <ul>
 *   <li>a {@link BuildManager} ticked on the integrated server thread and cleared when that server
 *   stops;</li>
 *   <li>a listener that hands job events from the server thread to the client thread;</li>
 *   <li>one {@link BuildCrew} per build, ticked on the client thread;</li>
 *   <li>the {@code /t3craft debug build-*} commands;</li>
 *   <li>{@link Builders#service()} for other parts of the mod.</li>
 * </ul>
 * No static state here: everything lives in this instance and is cleared on world change,
 * server stop and client stop.
 */
public final class BuildersClient implements ClientModInitializer, BuildService {
	private sealed interface Event {
		int jobId();
	}

	private record Placed(int jobId, BlockPos pos, BlockState state) implements Event {
	}

	private record Finished(int jobId, JobState state) implements Event {
	}

	private record Materials(int jobId, String label, Map<String, Integer> missing) implements Event {
	}

	/** Client-only entity ids, a range apart from the thread NPCs (which count down from -1_000_000). */
	private int nextEntityId = -2_000_000;

	private final BuildManager manager = new BuildManager();
	private final Queue<Event> events = new ConcurrentLinkedQueue<>();
	private final List<BuildCrew> crews = new ArrayList<>();
	private volatile @Nullable MinecraftServer server;
	private @Nullable ClientLevel level;

	@Override
	public void onInitializeClient() {
		manager.addListener(new BuildListener() {
			@Override
			public void placed(BuildJob job, int index, BlockPos worldPos, BlockState worldState) {
				events.add(new Placed(job.id(), worldPos, worldState));
			}

			@Override
			public void materials(BuildJob job, Map<String, Integer> missing) {
				events.add(new Materials(job.id(), job.label(), Map.copyOf(missing)));
			}

			@Override
			public void finished(BuildJob job, JobState state) {
				events.add(new Finished(job.id(), state));
			}
		});

		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			manager.clear();
			server = s;
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			server = null;
			manager.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (s == server) {
				manager.tick(s);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::clientTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> shutdown());
		BuildCommands.register(this);
		Builders.install(this);
	}

	// ---- BuildService (client thread) ---------------------------------------------------------

	@Override
	public BuildHandle build(String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick, int crewSize) {
		return build(label, dimension, origin, rotation, plan, blocksPerTick, crewSize, Billing.POLICY);
	}

	@Override
	public BuildHandle build(String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick, int crewSize,
			Billing billing) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel lvl = mc.level;
		if (!available() || lvl == null || mc.player == null) {
			throw new IllegalStateException("builders need a singleplayer world (integrated server)");
		}
		if (!lvl.dimension().equals(dimension)) {
			throw new IllegalStateException("builders can only work in the dimension you are in");
		}
		BuildCost cost = null;
		if (billing == Billing.POLICY) {
			BuildMaterials.Decision d = BuildMaterials.decide(Survival.rules(), PlayContexts.of(mc.player, true));
			Verdict v = d.verdict();
			if (!v.allowed()) {
				throw new BuildDeniedException(v.langKey(), Component.translatable(v.langKey()).getString());
			}
			if (d.charged()) {
				cost = BuildMaterials.cost(mc.player.getUUID());
			}
		}
		BuildCost jobCost = cost;
		if (lvl != level) {
			resetLevel(lvl);
		}
		Vec3 viewer = mc.player.position();
		BuildCrew crew = new BuildCrew(label, lvl, BuildJob.boundsOf(plan, origin, rotation), crewSize, viewer, () -> nextEntityId--,
				() -> manager.submit(label, dimension, origin, rotation, plan, blocksPerTick, jobCost));
		crews.add(crew);
		return crew;
	}

	@Override
	public List<BuildHandle> active() {
		return List.copyOf(crews);
	}

	@Override
	public boolean available() {
		return server != null;
	}

	BuildManager manager() {
		return manager;
	}

	// ---- client thread ------------------------------------------------------------------------

	private void clientTick(Minecraft mc) {
		if (mc.level != level) {
			resetLevel(mc.level);
		}
		for (Event e; (e = events.poll()) != null; ) {
			BuildCrew crew = crewFor(e.jobId());
			if (crew == null) {
				continue;
			}
			switch (e) {
				case Placed p -> crew.placed(p.pos(), p.state());
				case Finished f -> crew.finished();
				case Materials m -> notifyMaterials(mc, m);
			}
		}
		Iterator<BuildCrew> it = crews.iterator();
		while (it.hasNext()) {
			BuildCrew crew = it.next();
			boolean alive;
			try {
				alive = crew.tick();
			} catch (RuntimeException ex) {
				T3Craft.LOGGER.error("build crew {} failed", crew.label(), ex);
				crew.cancel();
				crew.discard();
				alive = false;
			}
			if (!alive) {
				it.remove();
			}
		}
	}

	/** Tells the player a build waits for materials, or resumed. */
	private static void notifyMaterials(Minecraft mc, Materials m) {
		if (mc.player == null) {
			return;
		}
		Component msg = m.missing().isEmpty()
				? Component.translatable("t3craft.survival.materials_resumed", m.label())
				: Component.translatable("t3craft.survival.missing_materials_job", m.label(), BuildMaterials.describe(m.missing()));
		mc.player.sendSystemMessage(Component.literal("[T3Craft] ").withStyle(net.minecraft.ChatFormatting.AQUA).append(msg));
	}

	private @Nullable BuildCrew crewFor(int jobId) {
		for (BuildCrew c : crews) {
			if (c.owns(jobId)) {
				return c;
			}
		}
		return null;
	}

	/** World changed or left: crews died with the old level; their jobs are cancelled. */
	private void resetLevel(@Nullable ClientLevel lvl) {
		for (BuildCrew c : crews) {
			c.cancel();
			if (c.level() == level) {
				c.discard();
			}
		}
		crews.clear();
		events.clear();
		level = lvl;
	}

	private void shutdown() {
		resetLevel(null);
		manager.clear();
		server = null;
		Builders.uninstall(this);
	}
}
