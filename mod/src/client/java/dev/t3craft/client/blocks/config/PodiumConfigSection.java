package dev.t3craft.client.blocks.config;

import dev.t3craft.blocks.Binding;
import dev.t3craft.client.blocks.ClickOptions;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.workstation.StationStatus;
import java.util.ArrayList;
import java.util.List;

/**
 * The decision podium's config page (issue #43). The podium shows the state of a board slot (the n-th thread that needs
 * the player, errors first; the HQ binds {@code board:<i>}) or of one thread, and a click opens that thread to answer its
 * question. Its model and light come from the block state, so the only option is what a click does when nobody waits
 * ({@link ClickOptions#PODIUM_NOBODY}): say so in the chat (default) or stay quiet.
 */
public final class PodiumConfigSection implements BlockConfigSection {
	private static final List<Binding.Kind> KINDS = List.of(Binding.Kind.BOARD, Binding.Kind.THREAD);
	private static final List<String> NOBODY = List.of(ClickOptions.PODIUM_MESSAGE, ClickOptions.PODIUM_SILENT);

	@Override
	public String blockName() {
		return "Decision podium";
	}

	@Override
	public List<Binding.Kind> kinds() {
		return KINDS;
	}

	@Override
	public String kindTitle() {
		return "Shows";
	}

	@Override
	public String kindLabel(Binding.Kind kind) {
		return kind == Binding.Kind.THREAD ? "One thread" : kind == Binding.Kind.BOARD ? "Board slot" : BindingChoices.kindLabel(kind);
	}

	@Override
	public void options(BlockConfigPanel.Form form, BlockConfig config) {
		String nobody = config.option(ClickOptions.PODIUM_NOBODY, ClickOptions.PODIUM_MESSAGE);
		form.select("When nobody waits", NOBODY, v -> ClickOptions.PODIUM_SILENT.equals(v) ? "Say nothing" : "Say so in the chat",
				NOBODY.contains(nobody) ? nobody : ClickOptions.PODIUM_MESSAGE,
				v -> form.set(ClickOptions.PODIUM_NOBODY, v, ClickOptions.PODIUM_MESSAGE));
		form.note("What a click does when no thread needs you. The podium's model and light follow the thread's state.");
	}

	@Override
	public BlockPreview.Model preview(BlockConfig config, BindingChoices choices, ChatTheme t) {
		Binding b = config.binding();
		StationStatus st = null;
		String title;
		List<BlockPreview.Line> lines = new ArrayList<>();
		if (b.kind() == Binding.Kind.BOARD) {
			List<BindingChoices.Choice> urgent = LampConfigSection.urgent(choices);
			int i = Math.max(0, b.boardIndex());
			if (i < urgent.size()) {
				st = urgent.get(i).status();
				title = "Slot " + (i + 1) + ": " + urgent.get(i).label();
				lines.add(new BlockPreview.Line("A click opens this thread to answer it.", t.codeText()));
			} else {
				title = "Slot " + (i + 1) + ": nobody waiting";
				lines.add(new BlockPreview.Line(ClickOptions.PODIUM_SILENT.equals(config.option(ClickOptions.PODIUM_NOBODY, "")) ? "A click does nothing."
						: "A click says nobody is waiting.", t.muted()));
			}
		} else if (b.kind() == Binding.Kind.THREAD) {
			BindingChoices.Choice c = choices.find(b);
			st = c.status() == StationStatus.ARCHIVED ? null : c.status();
			title = c.label();
			lines.add(new BlockPreview.Line("A click opens this thread.", t.codeText()));
		} else {
			title = "Not bound";
			lines.add(new BlockPreview.Line("An unbound podium stays dark.", t.muted()));
		}
		return new BlockPreview.Model(title, st == null ? "OFF" : st.word, st == null ? t.disabled() : MonitorConfigSection.color(st, t), lines, 1f);
	}
}
