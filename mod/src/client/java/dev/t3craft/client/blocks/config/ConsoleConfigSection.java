package dev.t3craft.client.blocks.config;

import dev.t3craft.blocks.Binding;
import dev.t3craft.client.blocks.ClickOptions;
import dev.t3craft.client.chat.ChatTheme;
import java.util.ArrayList;
import java.util.List;

/**
 * The console terminal's config page (issue #43). The terminal has no screen text of its own (the monitor is baked in
 * its model); a click opens the chat of the thread it is bound to, or T3's composer on a new thread draft when it is
 * not. The option ({@link ClickOptions#CONSOLE_OPENS}) makes it always start a new thread, whatever it is bound to.
 */
public final class ConsoleConfigSection implements BlockConfigSection {
	private static final List<Binding.Kind> KINDS = List.of(Binding.Kind.THREAD);
	private static final List<String> OPENS = List.of(ClickOptions.CONSOLE_BOUND, ClickOptions.CONSOLE_NEW);

	@Override
	public String blockName() {
		return "Console terminal";
	}

	@Override
	public List<Binding.Kind> kinds() {
		return KINDS;
	}

	@Override
	public String kindTitle() {
		return "Bound to";
	}

	@Override
	public void options(BlockConfigPanel.Form form, BlockConfig config) {
		String opens = config.option(ClickOptions.CONSOLE_OPENS, ClickOptions.CONSOLE_BOUND);
		form.select("A click opens", OPENS, v -> ClickOptions.CONSOLE_NEW.equals(v) ? "A new thread" : "The bound thread",
				OPENS.contains(opens) ? opens : ClickOptions.CONSOLE_BOUND,
				v -> form.set(ClickOptions.CONSOLE_OPENS, v, ClickOptions.CONSOLE_BOUND));
		form.note("\"The bound thread\" falls back to a new thread when the console is not bound.");
	}

	@Override
	public BlockPreview.Model preview(BlockConfig config, BindingChoices choices, ChatTheme t) {
		boolean fresh = ClickOptions.CONSOLE_NEW.equals(config.option(ClickOptions.CONSOLE_OPENS, ClickOptions.CONSOLE_BOUND));
		Binding b = config.binding();
		List<BlockPreview.Line> lines = new ArrayList<>();
		String title;
		if (!fresh && b.kind() == Binding.Kind.THREAD) {
			title = choices.find(b).label();
			lines.add(new BlockPreview.Line("A click opens this thread's chat.", t.codeText()));
		} else {
			title = "New thread";
			lines.add(new BlockPreview.Line("A click opens the composer on a new thread draft.", t.codeText()));
			if (fresh && b.kind() == Binding.Kind.THREAD) {
				lines.add(new BlockPreview.Line("The bound thread is ignored.", t.muted()));
			}
		}
		return new BlockPreview.Model("> " + title, "", t.accent(), lines, 1f);
	}
}
