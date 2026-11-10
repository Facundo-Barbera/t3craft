package dev.t3craft.client.blocks.config;

import dev.t3craft.blocks.Binding;
import dev.t3craft.blocks.BoundBlockEntity;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a block's config page edits: the block's own binding, whether it was set by hand ({@code manual}, see
 * {@link BoundBlockEntity#manual()}) and its display options (missing key = default). An immutable value; the
 * page keeps a draft and writes it through its {@link BlockConfigTarget}.
 */
public record BlockConfig(Binding binding, boolean manual, Map<String, String> options) {
	public static final BlockConfig EMPTY = new BlockConfig(Binding.NONE, false, Map.of());

	public BlockConfig {
		binding = binding == null ? Binding.NONE : binding;
		options = BoundBlockEntity.cleanOptions(options);
	}

	/** The config a block entity holds now. */
	public static BlockConfig of(BoundBlockEntity be) {
		return new BlockConfig(Binding.parse(be.binding()), be.manual(), be.options());
	}

	public String option(String key, String def) {
		return options.getOrDefault(key, def);
	}

	/** With {@code key} set to {@code value}; the default is not stored, so an untouched block keeps empty options. */
	public BlockConfig withOption(String key, String value, String def) {
		TreeMap<String, String> o = new TreeMap<>(options);
		if (value == null || value.equals(def)) {
			o.remove(key);
		} else {
			o.put(key, value);
		}
		return new BlockConfig(binding, manual, o);
	}

	public BlockConfig withBinding(Binding b) {
		return new BlockConfig(b, manual, options);
	}

	public BlockConfig withManual(boolean m) {
		return new BlockConfig(binding, m, options);
	}
}
