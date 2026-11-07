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
 * monitor and lamp go unbound, off) and nobody else moves. Recomputed only when the roster changes.
 * Pure (no Minecraft client state): {@code BlocksClient} writes the result into the block entities.
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

	/** Bindings for {@code live} (the sample roster when nobody is connected, as the demo builds it). */
	public synchronized Map<Long, Binding> bindings(Collection<NpcInfo> live) {
		return bindings(DemoHq.chooseRoster(live).input());
	}

	synchronized Map<Long, Binding> bindings(HqInput input) {
		Map<Long, Binding> l = last;
		if (l != null && input.equals(lastInput)) {
			return l;
		}
		HqGenerator.Result r = HqPlans.plan(input, layout);
		layout = r.layout();
		lastInput = input;
		Map<Long, Binding> out = HqBindings.world(r.plan(), site);
		last = out;
		return out;
	}

	/** The layout the bindings follow now (tests, debug). */
	synchronized HqLayout layout() {
		return layout;
	}
}
