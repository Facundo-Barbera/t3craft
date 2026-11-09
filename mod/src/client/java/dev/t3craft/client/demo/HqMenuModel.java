package dev.t3craft.client.demo;

import dev.t3craft.hq.layout.HqLayoutInfo;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * State of the HQ menu ({@link HqMenuScreen}), pure Java and unit tested: which layout is chosen
 * (persisted through {@code persist} the moment it changes), which one the HQ on disk is built
 * with, and which actions the menu offers.
 *
 * <ul>
 *   <li><b>Reset</b>: only when the HQ world exists.</li>
 *   <li><b>Rebuild with &lt;layout&gt;</b>: only when an HQ is built and the chosen layout differs from
 *       it. With no HQ built, choosing is enough: the next "Open HQ" builds with it.</li>
 * </ul>
 */
public final class HqMenuModel {
	private final List<HqLayoutInfo> layouts;
	private final Consumer<String> persist;
	private final @Nullable String builtId;
	private final boolean hqExists;
	private int selected;

	/**
	 * @param layouts    the registry in menu order ({@code HqLayouts.menuOrder()}: primary first); not empty
	 * @param selectedId the configured {@code hqLayout}; unknown ids fall back to the first layout
	 *                   (without persisting: the config is only written on a real choice)
	 * @param builtId    layout of the HQ standing in the world, or null if none is built
	 * @param hqExists   the HQ save folder exists
	 * @param persist    writes the new {@code hqLayout} (called only when the choice changes)
	 */
	public HqMenuModel(List<HqLayoutInfo> layouts, @Nullable String selectedId, @Nullable String builtId, boolean hqExists,
			Consumer<String> persist) {
		if (layouts.isEmpty()) {
			throw new IllegalArgumentException("no HQ layouts");
		}
		this.layouts = List.copyOf(layouts);
		this.persist = Objects.requireNonNull(persist);
		this.builtId = builtId;
		this.hqExists = hqExists;
		int i = indexOf(selectedId);
		this.selected = Math.max(0, i);
	}

	public List<HqLayoutInfo> layouts() {
		return layouts;
	}

	public int size() {
		return layouts.size();
	}

	public HqLayoutInfo get(int index) {
		return layouts.get(index);
	}

	public int selectedIndex() {
		return selected;
	}

	public HqLayoutInfo selected() {
		return layouts.get(selected);
	}

	/** Id of the layout the standing HQ uses, or null if none is built. */
	public @Nullable String builtId() {
		return builtId;
	}

	/** The built layout's entry, or null (none built, or an id the registry no longer knows). */
	public @Nullable HqLayoutInfo built() {
		int i = indexOf(builtId);
		return i < 0 ? null : layouts.get(i);
	}

	public boolean hqExists() {
		return hqExists;
	}

	public boolean isSelected(int index) {
		return index == selected;
	}

	public boolean isBuilt(int index) {
		return builtId != null && layouts.get(index).id().equals(builtId);
	}

	/** Any layout flagged {@code large}: shown with a warning tag. */
	public boolean warnLarge(int index) {
		return layouts.get(index).large();
	}

	/** Chooses {@code index} (out of range is ignored); persists only if it changed. Returns whether it did. */
	public boolean select(int index) {
		if (index < 0 || index >= layouts.size() || index == selected) {
			return false;
		}
		selected = index;
		persist.accept(layouts.get(index).id());
		return true;
	}

	/** Chooses by id; unknown ids are ignored. */
	public boolean select(String id) {
		return select(indexOf(id));
	}

	/** Moves the choice by {@code delta}, clamped to the list (keyboard). */
	public boolean move(int delta) {
		return select(Math.max(0, Math.min(layouts.size() - 1, selected + delta)));
	}

	/** "Rebuild with &lt;layout&gt;": an HQ is built and the choice differs from it. */
	public boolean rebuildOffered() {
		return hqExists && builtId != null && !selected().id().equals(builtId);
	}

	/** "Reset HQ": only an existing HQ world can be reset (from the menu, nothing else is ever touched). */
	public boolean resetOffered() {
		return hqExists;
	}

	private int indexOf(@Nullable String id) {
		if (id == null) {
			return -1;
		}
		for (int i = 0; i < layouts.size(); i++) {
			if (layouts.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}
}
