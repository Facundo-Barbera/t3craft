package dev.t3craft.client.hq;

import dev.t3craft.T3Craft;
import dev.t3craft.build.BlockSink;
import dev.t3craft.build.BuildCursor;
import dev.t3craft.build.BuildHandle;
import dev.t3craft.build.BuildJob;
import dev.t3craft.build.BuildPlan;
import dev.t3craft.build.BuildService;
import dev.t3craft.build.Builders;
import dev.t3craft.build.JobState;
import dev.t3craft.hq.construct.mc.HqWriter;
import dev.t3craft.hq.construct.mc.PacedHqWriter;
import dev.t3craft.site.Box;
import dev.t3craft.site.CellPlan;
import dev.t3craft.site.SiteBuilder;
import dev.t3craft.site.mc.BuildPlanBridge;
import dev.t3craft.site.mc.LevelBlockWorld;
import dev.t3craft.site.mc.SiteRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import org.jspecify.annotations.Nullable;

/**
 * Hands HQ work to the animated builders ({@code Builders.service()}), keeping the site's guards:
 * <ul>
 *   <li>{@link #siteBuilder}: a {@link SiteBuilder} for the site's expansions (base and wings). Cells
 *   the player changed after the snapshot are skipped through the sink wrapper.</li>
 *   <li>{@link #writer}: the {@link HqWriter} for in-place changes.</li>
 * </ul>
 * Calls come on the server thread; the crew is started on the client thread and its job is polled
 * from {@link #tick} (server thread). When there is no crew (player in another dimension, no
 * service), the plain paced builders do the work instead.
 */
public final class CrewBridge {
	public static final double BLOCKS_PER_TICK = 8;
	public static final int CREW = 4;

	private interface Watch {
		/** True when finished (and reported). */
		boolean poll();
	}

	private static final List<Watch> watches = new CopyOnWriteArrayList<>();

	private CrewBridge() {
	}

	/** Server thread, every tick. */
	public static void tick() {
		for (Watch w : watches) {
			try {
				if (w.poll()) {
					watches.remove(w);
				}
			} catch (RuntimeException e) {
				T3Craft.LOGGER.warn("HQ crew watch failed", e);
				watches.remove(w);
			}
		}
	}

	public static void clear() {
		watches.clear();
	}

	/** Whether a crew can be sent to {@code level} right now. */
	private static @Nullable BuildService crew(ServerLevel level) {
		BuildService svc = Builders.service();
		Minecraft mc = Minecraft.getInstance();
		if (svc == null || !svc.available() || mc.player == null || mc.level == null || !mc.level.dimension().equals(level.dimension())) {
			return null;
		}
		return svc;
	}

	/**
	 * Starts {@code plan} (world coordinates, origin zero) with a crew; {@code done} gets the finished
	 * job, or null when it was cancelled before the crew arrived. Returns false when no crew is available.
	 */
	private static boolean start(String label, ServerLevel level, BuildPlan plan, UnaryOperator<BlockSink> wrap, Consumer<@Nullable BuildJob> done) {
		BuildService svc = crew(level);
		if (svc == null) {
			return false;
		}
		BuildHandle[] handle = new BuildHandle[1];
		boolean[] failed = new boolean[1];
		Minecraft.getInstance().execute(() -> {
			try {
				handle[0] = svc.build(label, level.dimension(), BlockPos.ZERO, Rotation.NONE, plan, BLOCKS_PER_TICK, CREW, BuildService.Billing.POLICY, wrap);
			} catch (RuntimeException e) {
				T3Craft.LOGGER.warn("HQ crew could not start: {}", e.getMessage());
				failed[0] = true;
			}
		});
		watches.add(() -> {
			if (failed[0]) {
				done.accept(null);
				return true;
			}
			BuildHandle h = handle[0];
			if (h == null) {
				return false;
			}
			BuildJob j = h.job();
			if (j != null && j.state().isTerminal()) {
				done.accept(j);
				return true;
			}
			if (j == null && h.phase() == BuildHandle.Phase.DONE) {
				done.accept(null);
				return true;
			}
			return false;
		});
		return true;
	}

	/** Site builder factory: the crew, or the site's paced builder when no crew can go. */
	public static Function<ServerLevel, SiteBuilder> siteBuilder() {
		return level -> (plan, origin, guard, done) -> {
			Box b = plan.box();
			BuildPlan rel = BuildPlanBridge.toBuildPlan(plan, new LevelBlockWorld(level));
			List<dev.t3craft.build.Placement> abs = new ArrayList<>(rel.size());
			for (dev.t3craft.build.Placement p : rel.placements()) {
				abs.add(new dev.t3craft.build.Placement(p.pos().offset(b.minX(), b.minY(), b.minZ()), p.state(), p.blockEntity()));
			}
			BuildPlan world = BuildPlan.of(abs).layered();
			AtomicInteger refused = new AtomicInteger();
			List<String> sample = new CopyOnWriteArrayList<>();
			UnaryOperator<BlockSink> wrap = inner -> (pos, state, be) -> {
				if (!guard.mayWrite(pos.getX(), pos.getY(), pos.getZ())) {
					if (refused.incrementAndGet() <= 5) {
						sample.add(pos.toShortString());
					}
					return BuildCursor.Outcome.SKIPPED;
				}
				return inner.place(pos, state, be);
			};
			boolean[] cancelled = new boolean[1];
			boolean[] finished = new boolean[1];
			boolean started = start("HQ", level, world, wrap, job -> {
				finished[0] = true;
				boolean c = job == null || job.state() == JobState.CANCELLED || cancelled[0];
				int placed = job == null ? 0 : job.placed();
				int skipped = job == null ? 0 : job.skipped();
				done.accept(new SiteBuilder.Report(placed, refused.get(), Math.max(0, skipped - refused.get()), c, List.copyOf(sample)));
			});
			if (!started) {
				return SiteRuntime.paced(level).submit(plan, origin, guard, done);
			}
			return new SiteBuilder.Handle() {
				@Override
				public boolean finished() {
					return finished[0];
				}

				@Override
				public void cancel() {
					cancelled[0] = true;
					Minecraft.getInstance().execute(() -> {
						BuildService svc = Builders.service();
						if (svc != null) {
							svc.active().stream().filter(h -> "HQ".equals(h.label())).forEach(BuildHandle::cancel);
						}
					});
				}
			};
		};
	}

	/** In-place writer: the crew, or {@code fallback} when no crew can go. */
	public static HqWriter writer(PacedHqWriter fallback) {
		return new HqWriter() {
			@Override
			public void write(String label, ServerLevel level, List<Write> writes, Consumer<Boolean> done) {
				List<dev.t3craft.build.Placement> ps = new ArrayList<>(writes.size());
				for (Write w : writes) {
					ps.add(new dev.t3craft.build.Placement(w.pos(), w.state(), w.blockEntity()));
				}
				boolean ok = !ps.isEmpty() && start(label, level, BuildPlan.of(ps).layered(), HqWriter.guard(level, writes),
						job -> done.accept(job != null && job.state() == JobState.COMPLETED));
				if (!ok) {
					fallback.write(label, level, writes, done);
				}
			}

			@Override
			public void pause(boolean p) {
				fallback.pause(p);
				Minecraft.getInstance().execute(() -> {
					BuildService svc = Builders.service();
					if (svc != null) {
						svc.active().forEach(h -> {
							if (p) {
								h.pause();
							} else {
								h.resume();
							}
						});
					}
				});
			}
		};
	}
}
