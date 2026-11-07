package dev.t3craft.client.portable;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.link.BridgeLink;
import dev.t3craft.client.link.LinkStatus;
import dev.t3craft.client.npc.NpcInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal read-only adapter from the client's existing state to {@link PortableState}. It opens no
 * connection and keeps no copy of T3 data: every call reads what {@link T3CraftClient} already
 * holds — the NPC model ({@code NpcRegistry.all()}, same filter as the world) and the
 * per-environment statuses ({@code T3CraftClient.environments()}) — in either mode.
 */
public final class PortableAdapter {
	private PortableAdapter() {
	}

	public static PortableState snapshot(T3CraftClient client) {
		Map<String, String[]> envs = client.environments();
		List<NpcInfo> infos = client.npcs().all();
		List<PortableThread> rows = new ArrayList<>(infos.size());
		for (NpcInfo n : infos) {
			rows.add(row(n));
		}
		BridgeLink link = client.link();
		if (link != null) {
			return PortableState.of("bridge · " + link.uri(), envs, rows, true, link.status() == LinkStatus.CONNECTED);
		}
		if (client.direct() != null) {
			return PortableState.of("direct · read-only", envs, rows, !envs.isEmpty(), null);
		}
		return PortableState.of("not started", envs, rows, false, null);
	}

	static PortableThread row(NpcInfo n) {
		String[] parts = PortableThread.splitId(n.id());
		return new PortableThread(
				n.id(),
				parts == null ? "" : parts[0],
				n.envLabel(),
				parts == null ? null : parts[1],
				n.title(),
				n.groupLabel(),
				n.state(),
				n.color(),
				n.activity(),
				n.priority(),
				n.settled());
	}
}
