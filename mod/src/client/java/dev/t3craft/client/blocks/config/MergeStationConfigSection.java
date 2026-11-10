package dev.t3craft.client.blocks.config;

import dev.t3craft.blocks.Binding;
import dev.t3craft.client.blocks.BlockAccent;
import dev.t3craft.client.blocks.MergeOptions;
import dev.t3craft.client.chat.ChatTheme;
import java.util.ArrayList;
import java.util.List;

/**
 * The merge station's config page (issue #43). It counts the pull requests waiting for review of what it is bound to
 * (a project, an environment or one thread) and shows the number, what it means and the title of the first one. The
 * options are {@link MergeOptions}: the colour of the headline and whether the title line is shown. The lamps and the
 * frame of the model follow the block state and are not options. The preview is schematic (sample text): the real
 * count comes from the bound pull requests.
 */
public final class MergeStationConfigSection implements BlockConfigSection {
	private static final List<Binding.Kind> KINDS = List.of(Binding.Kind.PROJECT, Binding.Kind.ENV, Binding.Kind.THREAD);

	@Override
	public String blockName() {
		return "Merge station";
	}

	@Override
	public List<Binding.Kind> kinds() {
		return KINDS;
	}

	@Override
	public String kindTitle() {
		return "Counts PRs of";
	}

	@Override
	public String kindLabel(Binding.Kind kind) {
		return switch (kind) {
			case PROJECT -> "A project";
			case ENV -> "An environment";
			case THREAD -> "One thread";
			default -> BindingChoices.kindLabel(kind);
		};
	}

	@Override
	public void options(BlockConfigPanel.Form form, BlockConfig config) {
		MergeOptions o = MergeOptions.of(config.options());
		form.select("Accent", BlockAccent.CHOICES, a -> BlockAccent.label(a, "PR state colour"), o.accent(),
				a -> form.set(BlockAccent.KEY, a, BlockAccent.OWN));
		form.toggle("Show the first PR's title", o.showTitle(), v -> form.set(MergeOptions.SHOW_TITLE, Boolean.toString(v), "true"));
		form.note("The accent colours the headline. The lamps and the frame follow the PR state: green open, amber draft, violet merged.");
	}

	@Override
	public BlockPreview.Model preview(BlockConfig config, BindingChoices choices, ChatTheme t) {
		MergeOptions o = MergeOptions.of(config.options());
		Binding b = config.binding();
		int fixed = BlockAccent.argb(o.accent(), t);
		int accent = fixed != 0 ? fixed : t.done();
		List<BlockPreview.Line> lines = new ArrayList<>();
		String title = b.isNone() ? "Not linked" : choices.find(b).label();
		if (b.isNone()) {
			lines.add(new BlockPreview.Line("NO PR", accent));
			lines.add(new BlockPreview.Line("not linked", t.muted()));
		} else {
			lines.add(new BlockPreview.Line("2 PRs", accent));
			lines.add(new BlockPreview.Line("to review", t.codeText()));
			if (o.showTitle()) {
				lines.add(new BlockPreview.Line("Add the block config page", t.muted()));
			}
			lines.add(new BlockPreview.Line("(sample text: the real count comes from the bound pull requests)", t.disabled()));
		}
		return new BlockPreview.Model(title, "", accent, lines, 1f);
	}
}
