package dev.t3craft.client.blocks.config;

import dev.t3craft.blocks.Binding;
import dev.t3craft.blocks.ThreadBoardBlockEntity;
import dev.t3craft.client.blocks.WallMode;
import dev.t3craft.client.chat.ChatTheme;
import java.util.ArrayList;
import java.util.List;

/**
 * The thread wall's config page (issue #43). The wall shows every thread, so there is no binding picker; the option is
 * how its columns are split ({@link WallMode}): by state (the kanban), by project or by environment, overriding what the
 * plan wrote for the anchor and what {@code blocks.threadWall.columns} says. The wall's size is the plan's (it is a
 * frame of real blocks) and is not an option.
 */
public final class ThreadBoardConfigSection implements BlockConfigSection {
	/** "" = what the plan and the config say. */
	private static final List<String> MODES = List.of("", WallMode.STATUS.wire(), WallMode.PROJECT.wire(), WallMode.ENVIRONMENT.wire());

	@Override
	public String blockName() {
		return "Thread wall";
	}

	@Override
	public List<Binding.Kind> kinds() {
		return List.of();
	}

	private static String label(String mode) {
		return switch (mode) {
			case "status" -> "By state";
			case "project" -> "By project";
			case "environment" -> "By environment";
			default -> "As the plan says";
		};
	}

	@Override
	public void options(BlockConfigPanel.Form form, BlockConfig config) {
		String mode = config.option(ThreadBoardBlockEntity.OPTION_COLUMNS, "");
		form.select("Columns", MODES, ThreadBoardConfigSection::label, MODES.contains(mode) ? mode : "",
				m -> form.set(ThreadBoardBlockEntity.OPTION_COLUMNS, m, ""));
		form.note("\"As the plan says\" keeps the anchor's own mode, else blocks.threadWall.columns. The wall's size is not an option.");
	}

	@Override
	public BlockPreview.Model preview(BlockConfig config, BindingChoices choices, ChatTheme t) {
		String raw = config.option(ThreadBoardBlockEntity.OPTION_COLUMNS, "");
		boolean own = !raw.isEmpty();
		WallMode mode = WallMode.parse(raw, WallMode.STATUS);
		List<BlockPreview.Line> lines = new ArrayList<>();
		if (!own) {
			lines.add(new BlockPreview.Line("Columns follow the plan or the config.", t.muted()));
		} else if (mode == WallMode.STATUS) {
			lines.add(new BlockPreview.Line("Working | Needs you | Error | Done", t.codeText()));
		} else {
			List<BindingChoices.Choice> groups = choices.of(mode == WallMode.PROJECT ? Binding.Kind.PROJECT : Binding.Kind.ENV, "");
			if (groups.isEmpty()) {
				lines.add(new BlockPreview.Line("No " + (mode == WallMode.PROJECT ? "project" : "environment") + " is known yet.", t.muted()));
			}
			for (BindingChoices.Choice c : groups) {
				lines.add(new BlockPreview.Line(c.label(), t.codeText()));
			}
		}
		lines.add(new BlockPreview.Line(choices.threads().size() + " threads known", t.muted()));
		return new BlockPreview.Model("Thread wall", "", t.accent(), lines, 1f);
	}
}
