package dev.t3craft.client.blocks.config;

import dev.t3craft.blocks.Binding;
import dev.t3craft.client.t3ui.kit.T3PanelScreen;
import dev.t3craft.client.workstation.StationStatus;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Gallery scenarios of the block config pages ({@code /t3dev ui block-config-monitor}): the real page over a fake
 * block entity kept in memory (a 3-block monitor wall bound by hand to a thread, with a few options set) and fake
 * threads, projects and environments. Save and "Reset to automatic" change only the fake block; nothing reaches a
 * world or the network.
 */
public final class BlockConfigGallery {
	private static final String ENV = "gallery";

	private BlockConfigGallery() {
	}

	/** The monitor page over a fake wall. */
	public static Screen monitor() {
		BlockConfig start = new BlockConfig(Binding.thread(ENV + ":t-run"), true,
				Map.of("textSize", "large", "rows", "5"));
		return new T3PanelScreen(null, new BlockConfigPanel(new Fake(start, 3), new MonitorConfigSection(), choices()));
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
				thread("t-old", "Old spike (archived)", StationStatus.ARCHIVED, citadel, "citadel"));
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
