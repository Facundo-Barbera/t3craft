package dev.t3craft.client.blocks;

import dev.t3craft.blocks.Binding;
import dev.t3craft.client.workstation.StationScreen;
import dev.t3craft.client.workstation.StationStatus;
import dev.t3craft.client.workstation.TextFit;
import dev.t3craft.client.workstation.WorkstationModel;
import dev.t3craft.client.workstation.WorkstationThread;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * The content of a large screen bound to a project ({@code project:<groupId>}) or an environment
 * ({@code env:<envId>}): a header with the name and the number of active threads, then one entry per
 * active (not settled) thread, the most urgent first (needs you, error, working, done, idle; then by
 * priority and title). Each entry is the thread's status glyph and title in its status colour and,
 * when there is room, its last event on a second, dimmer line. Entries that do not fit collapse into
 * "+N more". Pure.
 */
public final class ProjectFeed {
	static final int HEADER_COLOR = 0xFFFFFF;
	static final int BACKGROUND = 0xF0101418;

	private ProjectFeed() {
	}

	/** True when {@code t} belongs to the feed {@code b}. */
	public static boolean matches(Binding b, WorkstationThread t) {
		return switch (b.kind()) {
			case PROJECT -> b.value().equals(t.groupId());
			case ENV -> b.value().equals(t.envId());
			default -> false;
		};
	}

	/** The feed screen of {@code b} over {@code threads}, {@code cols} characters × {@code rows} lines. */
	public static StationScreen render(Binding b, Collection<WorkstationThread> threads, int cols, int rows) {
		cols = Math.max(8, cols);
		rows = Math.max(2, rows);
		List<WorkstationThread> active = new ArrayList<>();
		for (WorkstationThread t : threads) {
			if (matches(b, t) && !t.archived()) {
				active.add(t);
			}
		}
		active.sort(Comparator.comparingInt((WorkstationThread t) -> urgency(t.status()))
				.thenComparing(Comparator.comparingInt(WorkstationThread::priority).reversed())
				.thenComparing(WorkstationThread::title)
				.thenComparing(WorkstationThread::key));
		List<StationScreen.Line> lines = new ArrayList<>(rows);
		String name = name(b);
		String count = active.size() == 1 ? "1 active thread" : active.size() + " active threads";
		lines.add(new StationScreen.Line(TextFit.clip("▤ " + name + " · " + count, cols), HEADER_COLOR));
		int room = rows - 1;
		if (active.isEmpty()) {
			lines.add(new StationScreen.Line(TextFit.clip(b.kind() == Binding.Kind.ENV ? "No threads in this environment" : "No active threads", cols),
					WorkstationModel.DIM_COLOR));
			return new StationScreen(lines, StationStatus.IDLE, BACKGROUND);
		}
		// two lines per thread (title + last event) when everything fits that way, else one
		boolean twoLines = active.size() * 2 <= room;
		int perEntry = twoLines ? 2 : 1;
		int shown = Math.min(active.size(), room / perEntry);
		if (shown < active.size()) {
			shown = Math.max(0, (room - 1) / perEntry); // keep a line for "+N more"
		}
		for (int i = 0; i < shown; i++) {
			WorkstationThread t = active.get(i);
			StationStatus s = t.status();
			String title = t.title().isBlank() ? "(untitled)" : t.title();
			String event = TextFit.normalize(t.activity());
			if (twoLines) {
				lines.add(new StationScreen.Line(TextFit.clip(s.glyph + " " + title, cols), s.color));
				lines.add(new StationScreen.Line(TextFit.clip("↳ " + (event.isEmpty() ? WorkstationModel.NO_ACTIVITY : event), cols),
						WorkstationModel.ACTIVITY_COLOR));
			} else {
				String line = s.glyph + " " + title + (event.isEmpty() ? "" : " — " + event);
				lines.add(new StationScreen.Line(TextFit.clip(line, cols), s.color));
			}
		}
		if (shown < active.size()) {
			lines.add(new StationScreen.Line("+" + (active.size() - shown) + " more", WorkstationModel.DIM_COLOR));
		}
		StationStatus top = active.get(0).status();
		return new StationScreen(lines, top, BACKGROUND);
	}

	/** Display name: the part after the environment for a project key ({@code env:proj} → {@code proj}). */
	static String name(Binding b) {
		String v = b.value();
		if (b.kind() == Binding.Kind.PROJECT) {
			int colon = v.indexOf(':');
			return colon > 0 && colon < v.length() - 1 ? v.substring(colon + 1) : v;
		}
		return v;
	}

	/** Lower = more urgent. */
	static int urgency(StationStatus s) {
		return switch (s) {
			case ATTENTION -> 0;
			case ERROR -> 1;
			case WORKING -> 2;
			case DONE -> 3;
			case IDLE -> 4;
			case ARCHIVED -> 5;
		};
	}
}
