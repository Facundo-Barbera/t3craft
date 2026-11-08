package dev.t3craft.client.demo;

import dev.t3craft.client.npc.NpcGate;

/** Test access to {@link DemoClient#npcGateFor}. */
public final class DemoGateAccess {
	private DemoGateAccess() {
	}

	public static NpcGate.Mode gateFor(boolean quiet) {
		return DemoClient.npcGateFor(quiet);
	}
}
