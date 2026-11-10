package dev.t3craft.client.blocks.config;

import dev.t3craft.blocks.Binding;
import dev.t3craft.client.blocks.ArchiveOptions;
import dev.t3craft.client.blocks.BlockAccent;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.workstation.StationStatus;
import java.util.ArrayList;
import java.util.List;

/**
 * The archive terminal's config page (issue #43). The terminal always shows the whole archive, so there is no binding
 * picker; the options are {@link ArchiveOptions}: how many of the latest closed threads it lists (1 to 3) and the
 * colour of its header. The preview lists the archived threads the client knows, or sample titles when it knows none.
 */
public final class ArchiveConfigSection implements BlockConfigSection {
	private static final List<String> SAMPLE = List.of("Fix login redirect", "Bump dependencies", "Spike: floating islands");

	@Override
	public String blockName() {
		return "Archive terminal";
	}

	@Override
	public List<Binding.Kind> kinds() {
		return List.of();
	}

	@Override
	public void options(BlockConfigPanel.Form form, BlockConfig config) {
		ArchiveOptions o = ArchiveOptions.of(config.options());
		form.select("Threads listed", ArchiveOptions.RECENT_CHOICES, n -> n == 1 ? "The latest one" : "The latest " + n, o.recent(),
				n -> form.set(ArchiveOptions.RECENT, Integer.toString(n), Integer.toString(ArchiveOptions.DEFAULT.recent())));
		form.select("Header accent", BlockAccent.CHOICES, a -> BlockAccent.label(a, "Amber"), o.accent(),
				a -> form.set(BlockAccent.KEY, a, BlockAccent.OWN));
		form.note("The terminal shows the whole archive; these only choose how much of its latest closed threads fits the screen.");
	}

	@Override
	public BlockPreview.Model preview(BlockConfig config, BindingChoices choices, ChatTheme t) {
		ArchiveOptions o = ArchiveOptions.of(config.options());
		int fixed = BlockAccent.argb(o.accent(), t);
		List<String> titles = new ArrayList<>();
		for (BindingChoices.Choice c : choices.threads()) {
			if (c.status() == StationStatus.ARCHIVED) {
				titles.add(c.label());
			}
		}
		boolean sample = titles.isEmpty();
		List<String> shown = sample ? SAMPLE : titles;
		List<BlockPreview.Line> lines = new ArrayList<>();
		for (int i = 0; i < Math.min(o.recent(), shown.size()); i++) {
			lines.add(new BlockPreview.Line(shown.get(i), t.codeText()));
		}
		if (sample) {
			lines.add(new BlockPreview.Line("(sample titles: no archived thread is known here)", t.disabled()));
		}
		return new BlockPreview.Model("ARCHIVE - " + shown.size() + (shown.size() == 1 ? " thread" : " threads"), "", fixed != 0 ? fixed : t.needsYou(), lines, 1f);
	}
}
