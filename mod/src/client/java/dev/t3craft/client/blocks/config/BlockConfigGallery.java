package dev.t3craft.client.blocks.config;

import dev.t3craft.blocks.Binding;
import dev.t3craft.client.blocks.MonitorLog;
import dev.t3craft.client.chat.ChatEntry;
import dev.t3craft.client.t3ui.kit.T3PanelScreen;
import dev.t3craft.client.workstation.StationStatus;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Gallery scenarios of the block config pages ({@code /t3dev ui block-config-monitor}, {@code -status}, {@code -lamp},
 * {@code -lantern}, {@code -podium}, {@code -merge}, {@code -console}, {@code -archive}, {@code -thread-wall}): the
 * real page over a fake block entity kept in memory (with a few options set) and fake threads, projects and
 * environments. Save and "Reset to automatic" change only the fake block; nothing reaches a world or the network.
 */
public final class BlockConfigGallery {
	private static final String ENV = "gallery";

	private BlockConfigGallery() {
	}

	/** The monitor page over a fake wall; the thread {@code t-run} has real log rows, the others show sample rows. */
	public static Screen monitor() {
		BlockConfig start = new BlockConfig(Binding.thread(ENV + ":t-run"), true,
				Map.of("textSize", "large", "rows", "5"));
		return page(new MonitorConfigSection(BlockConfigGallery::fakeLog), start, 3);
	}

	/** The status screen: the monitor page bound to the whole HQ (only text size and accent apply). */
	public static Screen status() {
		return page(new MonitorConfigSection(BlockConfigGallery::fakeLog), new BlockConfig(Binding.BEACON, true, Map.of("textSize", "large", "accent", "brass")), 27);
	}

	/** A board bulb with a soft glow that does not breathe. */
	public static Screen lamp() {
		return page(new LampConfigSection("Lamp"), new BlockConfig(Binding.board(0), true, Map.of("glow", "soft", "pulse", "false")), 1);
	}

	/** A paper lantern bound to a thread, motes off. */
	public static Screen lantern() {
		return page(new LampConfigSection("Paper lantern"), new BlockConfig(Binding.thread(ENV + ":t-ask"), true, Map.of("glow", "strong", "motes", "false")), 1);
	}

	/** A decision podium on board slot 1 that stays quiet when nobody waits. */
	public static Screen podium() {
		return page(new PodiumConfigSection(), new BlockConfig(Binding.board(1), true, Map.of("nobody", "silent")), 1);
	}

	/** A merge station counting the PRs of a project, with the title line off and a brass headline. */
	public static Screen merge() {
		return page(new MergeStationConfigSection(), new BlockConfig(Binding.project(ENV + ":studio"), true, Map.of("accent", "brass", "showTitle", "false")), 1);
	}

	/** A console terminal bound to a thread that always starts a new thread. */
	public static Screen console() {
		return page(new ConsoleConfigSection(), new BlockConfig(Binding.thread(ENV + ":t-run"), true, Map.of("opens", "new")), 1);
	}

	/** An archive terminal that lists two threads under a blue header. */
	public static Screen archive() {
		return page(new ArchiveConfigSection(), new BlockConfig(Binding.NONE, false, Map.of("recent", "2", "accent", "agent")), 1);
	}

	/** A thread wall that splits its columns by project. */
	public static Screen threadWall() {
		return page(new ThreadBoardConfigSection(), new BlockConfig(Binding.NONE, false, Map.of("columns", "project")), 1);
	}

	private static Screen page(BlockConfigSection section, BlockConfig start, int cells) {
		return new T3PanelScreen(null, new BlockConfigPanel(new Fake(start, cells), section, choices()));
	}

	/** Real-looking rows for the fake running thread (the page then shows them instead of the sample rows). */
	private static List<MonitorLog.Row> fakeLog(String threadKey, int max) {
		if (!threadKey.equals(ENV + ":t-run")) {
			return List.of();
		}
		List<MonitorLog.Row> rows = List.of(
				new MonitorLog.Row(MonitorLog.Kind.TOOL, "read", "Read", "client/blocks/config/BlockConfigPanel.java", ChatEntry.Kind.TOOL.color, -1, -1),
				new MonitorLog.Row(MonitorLog.Kind.TOOL, "edit", "Edit", "client/blocks/LampRenderer.java", ChatEntry.Kind.TOOL.color, 14, 2),
				new MonitorLog.Row(MonitorLog.Kind.PROSE, null, "", "The lamp glow now follows its options.", ChatEntry.Kind.ASSISTANT.color, -1, -1));
		return rows.subList(Math.max(0, rows.size() - max), rows.size());
	}

	static BindingChoices choices() {
		String studio = ENV + ":studio";
		String citadel = ENV + ":citadel";
		List<BindingChoices.Choice> threads = List.of(
				thread("t-run", "fix/hq-builders-independent", StationStatus.WORKING, studio, "studio"),
				thread("t-ask", "Which tile set for the archive?", StationStatus.ATTENTION, citadel, "citadel"),
				thread("t-err", "render daemon: out of memory", StationStatus.ERROR, studio, "studio"),
				thread("t-done", "docs: block config page", StationStatus.DONE, studio, "studio"),
				thread("t-idle", "Refactor the lamp signals", StationStatus.IDLE, citadel, "citadel"),
				thread("t-old", "Old spike (archived)", StationStatus.ARCHIVED, citadel, "citadel"),
				thread("t-old2", "Tile set trial (archived)", StationStatus.ARCHIVED, studio, "studio"));
		List<BindingChoices.Choice> projects = List.of(
				new BindingChoices.Choice(Binding.project(citadel), "citadel", "Gallery", StationStatus.IDLE, "", ""),
				new BindingChoices.Choice(Binding.project(studio), "studio", "Gallery", StationStatus.IDLE, "", ""));
		List<BindingChoices.Choice> envs = List.of(new BindingChoices.Choice(Binding.env(ENV), "Gallery", "", StationStatus.IDLE, "", ""));
		return new BindingChoices(threads, projects, envs);
	}

	private static BindingChoices.Choice thread(String id, String title, StationStatus status, String project, String projectLabel) {
		return new BindingChoices.Choice(Binding.thread(ENV + ":" + id), title, projectLabel, status, project, ENV);
	}

	/** A block entity in memory. */
	private static final class Fake implements BlockConfigTarget {
		private BlockConfig config;
		private final int cells;

		Fake(BlockConfig config, int cells) {
			this.config = config;
			this.cells = cells;
		}

		@Override
		public BlockConfig current() {
			return config;
		}

		@Override
		public int cells() {
			return cells;
		}

		@Override
		public @Nullable String readOnlyReason() {
			return null;
		}

		@Override
		public void write(BlockConfig c) {
			config = c;
		}

		@Override
		public void resetToAutomatic() {
			config = config.withManual(false);
		}
	}
}
