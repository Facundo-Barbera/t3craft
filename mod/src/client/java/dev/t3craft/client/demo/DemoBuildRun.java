package dev.t3craft.client.demo;

import dev.t3craft.build.BuildHandle;
import dev.t3craft.build.BuildJob;
import dev.t3craft.build.BuildPlan;
import dev.t3craft.build.BuildService;
import dev.t3craft.build.JobState;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import org.jspecify.annotations.Nullable;

/**
 * One demo build: the source's plans one after another at the demo origin, each with its own
 * animated crew. The world marker is written only when every plan completed, so leaving mid-build
 * (or cancelling) means the next visit builds again; already placed blocks are skipped then.
 * Client thread only.
 */
final class DemoBuildRun {
	private final DemoStructureSource source;
	private final Deque<BuildPlan> queue;
	private final int total;
	private final Path worldRoot;
	private final DemoConfig config;
	private final Consumer<String> say;
	private int delay;
	private @Nullable BuildHandle current;
	private boolean done;

	DemoBuildRun(DemoStructureSource source, List<BuildPlan> plans, Path worldRoot, DemoConfig config, int delayTicks, Consumer<String> say) {
		this.source = source;
		this.queue = new ArrayDeque<>(plans);
		this.total = plans.size();
		this.worldRoot = worldRoot;
		this.config = config;
		this.delay = delayTicks;
		this.say = say;
	}

	boolean done() {
		return done;
	}

	@Nullable BuildHandle current() {
		return current;
	}

	String describe() {
		int index = total - queue.size();
		BuildHandle h = current;
		return String.format(Locale.ROOT, "%s v%d, plan %d/%d%s", source.id(), source.version(), index, total,
				h == null ? "" : String.format(Locale.ROOT, " %s %.0f%%", h.phase().name().toLowerCase(Locale.ROOT), h.progress() * 100));
	}

	void cancel() {
		if (current != null) {
			current.cancel();
		}
		done = true;
	}

	void tick(Minecraft mc, @Nullable BuildService service) {
		if (done) {
			return;
		}
		if (delay > 0) {
			delay--;
			return;
		}
		BuildHandle h = current;
		if (h != null) {
			if (h.phase() == BuildHandle.Phase.ARRIVING || h.phase() == BuildHandle.Phase.BUILDING) {
				return;
			}
			BuildJob job = h.job();
			if (job == null || job.state() != JobState.COMPLETED) {
				say.accept("HQ build stopped before finishing; it resumes next time you enter (or /t3craft demo rebuild)");
				done = true;
				return;
			}
			current = null;
		}
		if (service == null || !service.available() || mc.level == null || !Level.OVERWORLD.equals(mc.level.dimension())) {
			return; // wait: no builders yet, or the player is in another dimension
		}
		BuildPlan next = queue.poll();
		if (next == null) {
			DemoMarkerFile.update(worldRoot, m -> m.withBuilt(source));
			say.accept("HQ built (" + source.id() + " v" + source.version() + ")");
			done = true;
			return;
		}
		DemoWorldSpec spec = config.spec();
		try {
			current = service.build("demo-" + source.id() + "-" + (total - queue.size()), Level.OVERWORLD,
					new BlockPos(spec.originX(), spec.originY(), spec.originZ()), Rotation.NONE, next,
					config.blocksPerTick(), config.crew());
		} catch (IllegalStateException e) {
			say.accept("HQ build could not start: " + e.getMessage());
			done = true;
		}
	}
}
