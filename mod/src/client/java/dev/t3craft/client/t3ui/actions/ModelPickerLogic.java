package dev.t3craft.client.t3ui.actions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * State of the model / provider / effort picker, without drawing (the widget is {@link ModelPicker}).
 * Behaviour copied from T3 Code's composer ({@code ProviderModelPicker}, {@code ModelPickerContent},
 * {@code TraitsPicker}, {@code ComposerControlsBar}):
 * <ul>
 *   <li>choosing a provider selects its default model (or the first one) and that model's default options;</li>
 *   <li>choosing a model keeps every option the new model also has with a valid value, the rest fall back to their default;</li>
 *   <li>the runtime mode is clamped to what the provider supports; plan mode only when the provider shows the toggle;</li>
 *   <li>{@link #changed()} says which dimensions the player touched, so applying to a thread only
 *   sends the commands that changed.</li>
 * </ul>
 * Pure: no Minecraft, no network.
 */
public final class ModelPickerLogic {
	/** The models the list shows for a search and the legacy switch ({@link #modelView}). */
	public record ModelView(List<ProviderCatalog.Model> models, int legacyCount) {
		public ModelView {
			models = List.copyOf(models);
		}
	}

	/** What {@code thread.*.set} command a change maps to. */
	public enum Dimension {
		/** {@code thread.model-selection.set} (provider, model or options). */
		MODEL,
		/** {@code thread.runtime-mode.set}. */
		RUNTIME,
		/** {@code thread.interaction-mode.set}. */
		INTERACTION
	}

	private ProviderCatalog catalog;
	private @Nullable ModelPick pick;
	private @Nullable ModelPick initial;
	private final EnumSet<Dimension> changed = EnumSet.noneOf(Dimension.class);

	/**
	 * @param initial what the thread (or the new-thread defaults) currently uses; may name a model the
	 *                catalog no longer lists, and is then kept until the player picks another
	 */
	public ModelPickerLogic(ProviderCatalog catalog, @Nullable ModelPick initial) {
		this.catalog = catalog;
		this.initial = initial;
		this.pick = initial;
		if (pick == null) {
			ProviderCatalog.Provider first = catalog.selectable().isEmpty() ? null : catalog.selectable().getFirst();
			if (first != null) {
				pick = forProvider(first, Modes.Runtime.DEFAULT, Modes.Interaction.DEFAULT);
			}
		}
	}

	public ProviderCatalog catalog() {
		return catalog;
	}

	/** New catalog (e.g. the fetch finished): keeps the pick, fills it in if there was none. */
	public void setCatalog(ProviderCatalog c) {
		this.catalog = c;
		if (pick == null && !c.selectable().isEmpty()) {
			pick = forProvider(c.selectable().getFirst(), Modes.Runtime.DEFAULT, Modes.Interaction.DEFAULT);
		}
	}

	public @Nullable ModelPick pick() {
		return pick;
	}

	public @Nullable ModelPick initial() {
		return initial;
	}

	/** Dimensions that differ from {@link #initial()} because the player changed them. */
	public Set<Dimension> changed() {
		EnumSet<Dimension> out = EnumSet.noneOf(Dimension.class);
		if (pick == null) {
			return out;
		}
		for (Dimension d : changed) {
			boolean differs = initial == null || switch (d) {
				case MODEL -> !pick.sameModel(initial) || !pick.instanceId().equals(initial.instanceId());
				case RUNTIME -> pick.runtime() != initial.runtime();
				case INTERACTION -> pick.interaction() != initial.interaction();
			};
			if (differs) {
				out.add(d);
			}
		}
		return out;
	}

	/** After the change was applied: the current pick becomes the baseline. */
	public void commit() {
		initial = pick;
		changed.clear();
	}

	/** Back to the baseline. */
	public void reset() {
		pick = initial;
		changed.clear();
		setCatalog(catalog);
	}

	public List<ProviderCatalog.Provider> providers() {
		return catalog.selectable();
	}

	public ProviderCatalog.@Nullable Provider provider() {
		return pick == null ? null : catalog.provider(pick.instanceId());
	}

	public ProviderCatalog.@Nullable Model model() {
		ProviderCatalog.Provider p = provider();
		return p == null || pick == null ? null : p.model(pick.model());
	}

	public List<ProviderCatalog.Model> models() {
		ProviderCatalog.Provider p = provider();
		return p == null ? List.of() : p.models();
	}

	/**
	 * The models of the current provider the list shows, favorites first, then the rest in T3's order, legacy last.
	 * A search ({@code query}: every word must be in the label, name, slug, alias, sub-provider or badge) looks through
	 * the legacy models too; without one they are hidden unless {@code showLegacy} (the selected model always shows).
	 * {@link ModelView#legacyCount()} is how many legacy models the provider has, 0 while searching (no switch then).
	 */
	public ModelView modelView(String query, boolean showLegacy, Predicate<ProviderCatalog.Model> favorite) {
		String[] words = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
		boolean searching = !query.isBlank();
		String selected = pick == null ? null : pick.model();
		int legacy = 0;
		List<ProviderCatalog.Model> out = new ArrayList<>();
		for (ProviderCatalog.Model m : models()) {
			if (m.isLegacy() && !searching) {
				legacy++;
			}
			boolean visible = searching ? matches(m, words) : !m.isLegacy() || showLegacy || m.slug().equals(selected);
			if (visible) {
				out.add(m);
			}
		}
		out.sort(Comparator.comparingInt(m -> favorite.test(m) ? 0 : m.isLegacy() ? 2 : 1)); // stable: T3's order inside each group
		return new ModelView(out, legacy);
	}

	private static boolean matches(ProviderCatalog.Model m, String[] words) {
		StringBuilder hay = new StringBuilder(m.label()).append(' ').append(m.name()).append(' ').append(m.slug());
		m.aliases().forEach(a -> hay.append(' ').append(a));
		if (m.subProvider() != null) {
			hay.append(' ').append(m.subProvider());
		}
		if (m.badge() != null) {
			hay.append(' ').append(m.badge());
		}
		String h = hay.toString().toLowerCase(Locale.ROOT);
		for (String w : words) {
			if (!w.isEmpty() && !h.contains(w)) {
				return false;
			}
		}
		return true;
	}

	public List<Modes.Runtime> runtimeModes() {
		ProviderCatalog.Provider p = provider();
		return p == null ? List.of(Modes.Runtime.values()) : p.runtimeModes();
	}

	/** Plan mode is offered when the provider shows T3's interaction-mode toggle. */
	public boolean planAvailable() {
		ProviderCatalog.Provider p = provider();
		return p != null && p.showInteractionModeToggle();
	}

	public boolean selectProvider(String instanceId) {
		ProviderCatalog.Provider p = catalog.provider(instanceId);
		if (p == null || !p.selectable()) {
			return false;
		}
		if (pick != null && pick.instanceId().equals(instanceId)) {
			return true;
		}
		Modes.Runtime r = pick == null ? Modes.Runtime.DEFAULT : pick.runtime();
		Modes.Interaction i = pick == null ? Modes.Interaction.DEFAULT : pick.interaction();
		ModelPick before = pick;
		pick = forProvider(p, r, i);
		mark(before);
		return true;
	}

	public boolean selectModel(String slug) {
		ProviderCatalog.Provider p = provider();
		ProviderCatalog.Model m = p == null ? null : p.model(slug);
		if (m == null || pick == null) {
			return false;
		}
		ModelPick before = pick;
		pick = new ModelPick(p.instanceId(), m.slug(), defaultOptions(m, pick), pick.runtime(), pick.interaction());
		mark(before);
		return true;
	}

	/** Select option {@code id} to {@code value}; null = the option's default (nothing is sent for it). */
	public boolean selectOption(String id, @Nullable String value) {
		ProviderCatalog.Model m = model();
		ProviderCatalog.Option o = m == null ? null : m.option(id);
		if (pick == null || o == null || !o.isSelect() || (value != null && !o.has(value))) {
			return false;
		}
		ModelPick before = pick;
		pick = value == null ? pick.withoutOption(id) : pick.withOption(ProviderOptionSelection.of(id, value));
		mark(before);
		return true;
	}

	/** Boolean option {@code id} on or off. */
	public boolean setToggle(String id, boolean on) {
		ProviderCatalog.Model m = model();
		ProviderCatalog.Option o = m == null ? null : m.option(id);
		if (pick == null || o == null || o.isSelect()) {
			return false;
		}
		ModelPick before = pick;
		pick = pick.withOption(ProviderOptionSelection.of(id, on));
		mark(before);
		return true;
	}

	/** The value a boolean option shows now: the chosen one, else the descriptor's current value, else off. */
	public boolean toggleValue(ProviderCatalog.Option o) {
		ProviderOptionSelection s = pick == null ? null : pick.option(o.id());
		return s != null ? s.bool() : Boolean.TRUE.equals(o.currentBool());
	}

	public boolean selectRuntime(Modes.Runtime r) {
		if (pick == null || !runtimeModes().contains(r)) {
			return false;
		}
		ModelPick before = pick;
		pick = pick.withRuntime(r);
		mark(before);
		return true;
	}

	/** Cycles to the next runtime mode the provider supports. */
	public void cycleRuntime() {
		List<Modes.Runtime> rs = runtimeModes();
		if (pick == null || rs.isEmpty()) {
			return;
		}
		int i = rs.indexOf(pick.runtime());
		selectRuntime(rs.get((i + 1) % rs.size()));
	}

	public boolean setPlan(boolean plan) {
		if (pick == null || (plan && !planAvailable())) {
			return false;
		}
		ModelPick before = pick;
		pick = pick.withInteraction(plan ? Modes.Interaction.PLAN : Modes.Interaction.DEFAULT);
		mark(before);
		return true;
	}

	private void mark(@Nullable ModelPick before) {
		if (pick == null) {
			return;
		}
		if (before == null || !pick.sameModel(before) || !pick.instanceId().equals(before.instanceId())) {
			changed.add(Dimension.MODEL);
		}
		if (before == null || pick.runtime() != before.runtime()) {
			changed.add(Dimension.RUNTIME);
		}
		if (before == null || pick.interaction() != before.interaction()) {
			changed.add(Dimension.INTERACTION);
		}
	}

	/** Default model of {@code p} with its default effort; modes clamped to what {@code p} supports. */
	static ModelPick forProvider(ProviderCatalog.Provider p, Modes.Runtime runtime, Modes.Interaction interaction) {
		ProviderCatalog.Model m = p.defaultModel();
		if (m == null) {
			throw new IllegalArgumentException("provider without models");
		}
		List<Modes.Runtime> rs = p.runtimeModes();
		Modes.Runtime r = rs.contains(runtime) ? runtime : rs.contains(Modes.Runtime.DEFAULT) ? Modes.Runtime.DEFAULT : rs.getFirst();
		Modes.Interaction i = p.showInteractionModeToggle() ? interaction : Modes.Interaction.DEFAULT;
		return new ModelPick(p.instanceId(), m.slug(), defaultOptions(m, null), r, i);
	}

	/**
	 * The options {@code m} starts with: what {@code previous} had chosen when {@code m} has the same option with a
	 * valid value, else a select's default (when it has one) and a boolean's current value (when T3 sends one).
	 */
	static List<ProviderOptionSelection> defaultOptions(ProviderCatalog.Model m, @Nullable ModelPick previous) {
		List<ProviderOptionSelection> out = new ArrayList<>();
		for (ProviderCatalog.Option o : m.options()) {
			ProviderOptionSelection kept = previous == null ? null : previous.option(o.id());
			if (o.isSelect()) {
				String v = kept != null && o.has(kept.string()) ? kept.string() : o.defaultValue();
				if (v != null) {
					out.add(ProviderOptionSelection.of(o.id(), v));
				}
			} else if (kept != null && kept.value() instanceof Boolean) {
				out.add(kept);
			} else if (o.currentBool() != null) {
				out.add(ProviderOptionSelection.of(o.id(), o.currentBool()));
			}
		}
		return out;
	}
}
