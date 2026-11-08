package dev.t3craft.client.blocks;

import dev.t3craft.blocks.Binding;
import dev.t3craft.client.workstation.StationScreen;
import dev.t3craft.client.workstation.StationStatus;
import dev.t3craft.client.workstation.TextFit;
import dev.t3craft.client.workstation.WorkstationModel;
import dev.t3craft.client.workstation.WorkstationThread;
import dev.t3craft.hq.lead.LeadOffices;
import dev.t3craft.hq.lead.LeadRoster;
import dev.t3craft.t3.LeadSignals;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * feat/lead-office: the big screen of a lead's office ({@code project:lead:<npcId>}): the lead and its
 * children with their state. Header {@code ★ <lead title> · coordinating N}, then one line per live
 * child (subagents and delegated tasks), the most urgent first, then "+N more". Reads the shared
 * {@link LeadOffices#current()} roster (an immutable value); memoised by {@link MonitorScreens} on the
 * roster version, so it is rebuilt only when the roster or a thread changes.
 */
public final class LeadFeed {
	private LeadFeed() {
	}

	/** True for a lead office feed binding. */
	public static boolean matches(Binding b) {
		return b.kind() == Binding.Kind.PROJECT && LeadOffices.isLeadProject(b.value());
	}

	public static StationScreen render(Binding b, LeadRoster roster, Collection<WorkstationThread> threads, int cols, int rows) {
		cols = Math.max(8, cols);
		rows = Math.max(2, rows);
		String npc = LeadOffices.leadOf(b.value());
		LeadRoster.Lead lead = npc == null ? null : roster.lead(npc);
		String title = "Lead";
		StationStatus leadStatus = StationStatus.IDLE;
		for (WorkstationThread t : threads) {
			if (t.key().equals(npc)) {
				title = t.title().isBlank() ? title : t.title();
				leadStatus = t.status();
			}
		}
		List<StationScreen.Line> lines = new ArrayList<>(rows);
		if (lead == null) {
			lines.add(new StationScreen.Line(TextFit.clip("★ " + title, cols), ProjectFeed.HEADER_COLOR));
			lines.add(new StationScreen.Line(TextFit.clip("No agents right now", cols), WorkstationModel.DIM_COLOR));
			return new StationScreen(lines, leadStatus, ProjectFeed.BACKGROUND);
		}
		List<LeadSignals.Child> live = new ArrayList<>(lead.liveChildren());
		live.sort(Comparator.comparingInt((LeadSignals.Child c) -> ProjectFeed.urgency(StationStatus.of(c.stateWire(), false)))
				.thenComparing(Comparator.comparing((LeadSignals.Child c) -> c.createdAt() == null ? 0L : c.createdAt()).reversed())
				.thenComparing(LeadSignals.Child::threadId));
		lines.add(new StationScreen.Line(TextFit.clip("★ " + title + " · coordinating " + lead.coordinating(), cols), ProjectFeed.HEADER_COLOR));
		int room = rows - 1;
		if (live.isEmpty()) {
			lines.add(new StationScreen.Line(TextFit.clip("No agents right now", cols), WorkstationModel.DIM_COLOR));
			return new StationScreen(lines, leadStatus, ProjectFeed.BACKGROUND);
		}
		int shown = Math.min(live.size(), room);
		if (shown < live.size()) {
			shown = Math.max(0, room - 1);
		}
		for (int i = 0; i < shown; i++) {
			LeadSignals.Child c = live.get(i);
			StationStatus s = StationStatus.of(c.stateWire(), false);
			String name = c.title().isBlank() ? "(untitled)" : c.title();
			lines.add(new StationScreen.Line(TextFit.clip(s.glyph + " " + name, cols), s.color));
		}
		if (shown < live.size()) {
			lines.add(new StationScreen.Line("+" + (live.size() - shown) + " more", WorkstationModel.DIM_COLOR));
		}
		StationStatus top = StationStatus.of(live.get(0).stateWire(), false);
		return new StationScreen(lines, top, ProjectFeed.BACKGROUND);
	}
}
