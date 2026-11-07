package dev.t3craft.client.workstation;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Thread → screen lines. Pure and deterministic (no clock, no Minecraft): the caller passes the age.
 *
 * <pre>
 *   Fix login redirect loop          ← title, white, clipped to one line
 *   ⚙ WORKING · 3m                   ← glyph + status word + age, in the status colour
 *   ⎇ feat/login-fix                 ← branch, dim (only when known)
 *   Running tests in mod/: 12 of     ← last action / message, wrapped, light grey
 *   40 passed…
 * </pre>
 *
 * Archived (settled) threads get a single dim "archived" line under the title instead of activity.
 */
public final class WorkstationModel {
	public static final int TITLE_COLOR = 0xFFFFFF;
	public static final int ACTIVITY_COLOR = 0xC8C8C8;
	public static final int DIM_COLOR = 0x808080;
	/** Shown when a thread has no activity text yet. */
	public static final String NO_ACTIVITY = "—";

	/** Characters per line and how many lines the activity may take. */
	public record Layout(int width, int activityLines) {
		public static final Layout DEFAULT = new Layout(28, 3);

		public Layout {
			width = Math.max(8, width);
			activityLines = Math.max(1, activityLines);
		}
	}

	private final Layout layout;

	public WorkstationModel() {
		this(Layout.DEFAULT);
	}

	public WorkstationModel(Layout layout) {
		this.layout = layout;
	}

	public Layout layout() {
		return layout;
	}

	/**
	 * @param ageLabel already formatted age ({@link AgeFormat}), or null when unknown (then the status
	 *     line has no age)
	 */
	public StationScreen render(WorkstationThread t, @Nullable String ageLabel) {
		StationStatus s = t.status();
		List<StationScreen.Line> lines = new ArrayList<>(3 + layout.activityLines());
		String title = TextFit.clip(t.title().isBlank() ? "(untitled)" : t.title(), layout.width());
		lines.add(new StationScreen.Line(title, s == StationStatus.ARCHIVED ? DIM_COLOR : TITLE_COLOR));
		String status = s.glyph + " " + s.word + (ageLabel == null || ageLabel.isEmpty() ? "" : " · " + ageLabel);
		lines.add(new StationScreen.Line(TextFit.clip(status, layout.width()), s.color));
		if (t.branch() != null && !t.branch().isBlank()) {
			lines.add(new StationScreen.Line(TextFit.clip("⎇ " + t.branch(), layout.width()), DIM_COLOR));
		}
		if (s != StationStatus.ARCHIVED) {
			List<String> activity = TextFit.wrap(t.activity(), layout.width(), layout.activityLines());
			if (activity.isEmpty()) {
				lines.add(new StationScreen.Line(NO_ACTIVITY, DIM_COLOR));
			} else {
				for (String a : activity) {
					lines.add(new StationScreen.Line(a, ACTIVITY_COLOR));
				}
			}
		}
		return new StationScreen(lines, s, s.background());
	}
}
