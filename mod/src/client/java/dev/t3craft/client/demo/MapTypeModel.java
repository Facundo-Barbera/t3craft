package dev.t3craft.client.demo;

import dev.t3craft.mapgen.MapTypes;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * State of the map type selector of the HQ menu ({@link HqMenuPanel}), pure Java: the types on offer
 * (the {@link MapTypes} registry, in registry order, so a type registered later shows up by itself), which one
 * is chosen, and the save of the choice (through {@code persist}, only when it changes).
 *
 * <p>Every map type has a world of its own, so choosing a type never regenerates anything: it only decides
 * which world "Open" goes to. The HQ controls of the menu (layout, Rebuild, Reset, layout packs) apply to the
 * citadel type only, see {@link #hqApplies()}.
 */
public final class MapTypeModel {
	private final List<MapTypes> types;
	private final Consumer<String> persist;
	private int selected;

	/**
	 * @param types      the registry ({@link MapTypes#all()}); not empty
	 * @param selectedId the configured {@code mapType}; an unknown id falls back to the first type (without
	 *                   persisting: the config is only written on a real choice)
	 * @param persist    writes the new {@code mapType} (called only when the choice changes)
	 */
	public MapTypeModel(List<MapTypes> types, @Nullable String selectedId, Consumer<String> persist) {
		if (types.isEmpty()) {
			throw new IllegalArgumentException("no map types");
		}
		this.types = List.copyOf(types);
		this.persist = Objects.requireNonNull(persist);
		this.selected = Math.max(0, indexOf(selectedId));
	}

	public List<MapTypes> types() {
		return types;
	}

	public MapTypes selected() {
		return types.get(selected);
	}

	/** Chooses {@code type} (not in the list: ignored); persists only if it changed. Returns whether it did. */
	public boolean select(MapTypes type) {
		int i = indexOf(type.id());
		if (i < 0 || i == selected) {
			return false;
		}
		selected = i;
		persist.accept(type.id());
		return true;
	}

	/** The chosen type is the HQ citadel: only then do the layout list, Rebuild, Reset and the layout packs apply. */
	public boolean hqApplies() {
		return selected().id().equals(MapTypes.CITADEL.id());
	}

	private int indexOf(@Nullable String id) {
		if (id == null) {
			return -1;
		}
		for (int i = 0; i < types.size(); i++) {
			if (types.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}
}
