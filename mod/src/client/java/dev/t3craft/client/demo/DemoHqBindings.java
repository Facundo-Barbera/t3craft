package dev.t3craft.client.demo;

import dev.t3craft.blocks.Binding;
import dev.t3craft.blocks.HqBindings;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqPlans;
import dev.t3craft.hq.HqSite;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The demo HQ's automatic thread ↔ block binding: for the live roster, which HQ fixture shows which
 * thread, as world cell → {@link Binding} ({@link HqBindings#world}). Desks are allocated against the
 * layout already in the world and then against the last live layout ({@link HqLayout#allocate} keeps
 * every thread at its desk), so a new thread takes a free desk, a settled one frees its desk (its
 * monitor and lamp go unbound, off) and nobody else moves. Recomputed only when the roster changes, off the
 * client thread (fix/new-thread-hitch). Pure (no Minecraft client state): {@code BlocksClient} writes the result into the block entities.
 */
public final class DemoHqBindings {
	private final HqSite site;
	private HqLayout layout;
	private @Nullable HqInput lastInput;
	private @Nullable Map<Long, Binding> last;

	/** {@code built}: the layout the demo just built; {@code site}: HQ-relative → world cells. */
	public DemoHqBindings(HqLayout built, HqSite site) {
		this.layout = Objects.requireNonNull(built, "built");
		this.site = Objects.requireNonNull(site, "site");
	}

	/** fix/new-thread-hitch: one off-thread result: the roster, the layout it allocated, its bindings. */
	private record Computed(HqInput input, HqLayout layout, Map<Long, Binding> bindings) {
	}

	private java.util.concurrent.@Nullable CompletableFuture<Computed> computing;
	private @Nullable HqInput computingInput;

	/**
	 * Bindings for {@code live} (the sample roster when nobody is connected, as the demo builds it).
	 * fix/new-thread-hitch: a new roster is planned on a background worker ({@code HqPlans.plan} is a
	 * whole HQ plan); until it is ready the previous bindings stay (null before the first one), so the
	 * monitors and lamps follow a second later instead of the frame hitching.
	 */
	public synchronized @Nullable Map<Long, Binding> bindings(Collection<NpcInfo> live) {
		return bindings(DemoHq.chooseRoster(live).input());
	}

	synchronized @Nullable Map<Long, Binding> bindings(HqInput input) {
		Map<Long, Binding> l = last;
		if (l != null && input.equals(lastInput)) {
			return l;
		}
		java.util.concurrent.CompletableFuture<Computed> f = computing;
		if (f != null) {
			if (!f.isDone()) {
				return last;
			}
			computing = null;
			try {
				Computed c = f.join();
				layout = c.layout();
				lastInput = c.input();
				last = c.bindings();
			} catch (RuntimeException e) {
				dev.t3craft.T3Craft.LOGGER.warn("[demo] HQ bindings failed for roster {}", computingInput, e);
			}
			computingInput = null;
			if (last != null && input.equals(lastInput)) {
				return last;
			}
		}
		HqLayout base = layout;
		computingInput = input;
		computing = dev.t3craft.build.Offload.supply(dev.t3craft.debug.PerfStats.STEP_DEMO_BINDINGS, () -> {
			HqGenerator.Result r = HqPlans.plan(input, base);
			return new Computed(input, r.layout(), HqBindings.world(r.plan(), site));
		});
		return last;
	}

	/** The layout the bindings follow now (tests, debug). */
	synchronized HqLayout layout() {
		return layout;
	}
}
