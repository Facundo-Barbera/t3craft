package dev.t3craft.client.t3ui.sidebar;

import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.ConversationPanel;
import dev.t3craft.client.chat.ThreadRow;
import dev.t3craft.client.t3ui.SidebarModel;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/**
 * The "working" state where T3 Code shows it (feat/t3-sidebar-rows): elapsed time of the current
 * run ("Working 4m" in the sidebar row, spinner + "4m 12s" in the thread header, a subtle
 * "Working… 4m 12s" line at the end of the transcript). The time counts from the run's start
 * ({@code activityRunStartedAt}, else {@code latestRunStartedAt}), else the last activity.
 */
public final class WorkingStatus {
	private WorkingStatus() {
	}

	/** Start of the current run in epoch ms, or 0 when unknown. */
	public static long runStart(ThreadRow r) {
		Long s = r.details().runStartedMs();
		if (s != null && s > 0) {
			return s;
		}
		return r.activityMs() == null ? 0 : r.activityMs();
	}

	/** "12s", "4m", "2h" (sidebar). Empty when the start is unknown. */
	public static String elapsedShort(long nowMs, long startMs) {
		if (startMs <= 0) {
			return "";
		}
		long s = Math.max(0, (nowMs - startMs) / 1000);
		if (s < 60) {
			return s + "s";
		}
		long m = s / 60;
		return m < 60 ? m + "m" : (m / 60) + "h";
	}

	/** "12s", "4m 12s", "1h 03m" (header and transcript). Empty when the start is unknown. */
	public static String elapsedLong(long nowMs, long startMs) {
		if (startMs <= 0) {
			return "";
		}
		long s = Math.max(0, (nowMs - startMs) / 1000);
		if (s < 60) {
			return s + "s";
		}
		long m = s / 60;
		if (m < 60) {
			return m + "m " + (s % 60) + "s";
		}
		return (m / 60) + "h " + String.format("%02dm", m % 60);
	}

	/**
	 * Thread header: spinner + elapsed, right-aligned at {@code right}, when {@code row} runs. Returns
	 * the width used (gap included), 0 when it does not run.
	 */
	public static int drawHeader(GuiGraphicsExtractor g, Font font, ChatTheme t, @Nullable ThreadRow row, int right, int y) {
		if (row == null || !SidebarModel.working(row)) {
			return 0;
		}
		String el = elapsedLong(System.currentTimeMillis(), runStart(row));
		int tw = el.isEmpty() ? 0 : font.width(el);
		int w = Spinner.SIZE + (tw > 0 ? 3 + tw : 0);
		int x = right - w;
		Spinner.draw(g, x, y + 2, t.working());
		if (tw > 0) {
			g.text(font, el, x + Spinner.SIZE + 3, y + 2, t.working(), false);
		}
		return w + 4;
	}

	/** The line at the end of a running thread's transcript. */
	public static final class Trailer implements ConversationPanel.Trailer {
		private final Supplier<@Nullable ThreadRow> row;

		public Trailer(Supplier<@Nullable ThreadRow> row) {
			this.row = row;
		}

		@Override
		public int height() {
			ThreadRow r = row.get();
			return r != null && SidebarModel.working(r) ? 12 : 0;
		}

		@Override
		public void draw(GuiGraphicsExtractor g, Font font, ChatTheme t, int x, int y, int w) {
			ThreadRow r = row.get();
			if (r == null || !SidebarModel.working(r)) {
				return;
			}
			int c = ChatTheme.withAlpha(t.muted(), 200);
			Spinner.draw(g, x, y + 2, t.working());
			String el = elapsedLong(System.currentTimeMillis(), runStart(r));
			String s = "Working…" + (el.isEmpty() ? "" : " " + el);
			g.text(font, s, x + Spinner.SIZE + 4, y + 2, c, false);
		}
	}
}
