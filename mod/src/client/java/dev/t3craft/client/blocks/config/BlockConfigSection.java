package dev.t3craft.client.blocks.config;

import dev.t3craft.blocks.Binding;
import dev.t3craft.client.chat.ChatTheme;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/**
 * The part of a block's config page that belongs to its block type (registered in {@link BlockConfigPages}):
 * which bindings it offers, what the block will show (the preview) and the block's own options. The rest of the
 * page (binding picker, preview frame, Save, "Reset to automatic", the read-only note) is {@link BlockConfigPanel}'s.
 *
 * <p>Adding a page for another block (T10: lamps, podium, thread board, merge station, console, archive):
 * implement this, register it with {@link BlockConfigPages#register} for the block's class, store the options as
 * strings in {@link BlockConfig#options()} (only non-default values, so an untouched block keeps today's look) and
 * read them in the block's renderer from {@code BoundBlockEntity.option(key, default)}.
 */
public interface BlockConfigSection {
	/** Name of the block in the page's title ("Monitor"). */
	String blockName();

	/** The binding kinds the picker offers, in order; the first is the default of an unbound block. Empty = no binding picker. */
	List<Binding.Kind> kinds();

	/** Label of a kind in the picker ("Log" for a monitor bound to a thread). */
	default String kindLabel(Binding.Kind kind) {
		return BindingChoices.kindLabel(kind);
	}

	/** Label of the picker itself ("Content" for a monitor). */
	default String kindTitle() {
		return "Binding";
	}

	/** The blocks the page edits together for the block at {@code pos} (a monitor wall: all its cells). */
	default List<BlockPos> cells(BlockGetter level, BlockPos pos) {
		return List.of(pos);
	}

	/** What the block will show with {@code config} (text; the gallery passes fake choices). */
	BlockPreview.Model preview(BlockConfig config, BindingChoices choices, ChatTheme theme);

	/** Places the block's own options under the preview; the controls write through {@code form}. */
	void options(BlockConfigPanel.Form form, BlockConfig config);
}
