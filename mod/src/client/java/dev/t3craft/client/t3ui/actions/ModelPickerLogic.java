package dev.t3craft.client.t3ui.actions;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * State of the model / provider / effort picker, without drawing (the widget is {@link ModelPicker}).
 * Behaviour copied from T3 Code's composer ({@code ProviderModelPicker}, {@code ModelPickerContent},
 * {@code TraitsPicker}, {@code ComposerControlsBar}):
 * <ul>
 *   <li>choosing a provider selects its default model (or the first one) and that model's default options;</li>
 *   <li>choosing a model keeps each option the new model also offers with a value it accepts, the rest
 *   fall back to the model's defaults;</li>
 *   <li>the runtime mode is clamped to what the provider supports; plan mode only when the provider shows the toggle;</li>
 *   <li>{@link #changed()} says which dimensions the player touched, so applying to a thread only
 *   sends the commands that changed.</li>
 * </ul>
 * Pure: no Minecraft, no network.
 */
public final class ModelPickerLogic {
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

	/** The reasoning select of the chosen model (see {@link ProviderCatalog.Model#reasoning()}), or null. */
	public ProviderCatalog.@Nullable Descriptor reasoning() {
		ProviderCatalog.Model m = model();
		return m == null ? null : m.reasoning();
	}

	/** What {@link #listing} gives: the rows of the model list. */
	public record Listing(List<ProviderCatalog.Model> models, int legacy, boolean legacyShown) {
		public Listing {
			models = List.copyOf(models);
		}
	}

	/**
	 * The chosen provider's models for the list: those matching {@code query}, favorites first, then the
	 * rest in the server's order. Legacy models are left out unless {@code showLegacy}, except a favorite
	 * and the model in use; {@link Listing#legacy()} counts the ones that can be hidden.
	 */
	public Listing listing(String query, Predicate<ProviderCatalog.Model> favorite, boolean showLegacy) {
		String selected = pick == null ? null : pick.model();
		List<ProviderCatalog.Model> favs = new ArrayList<>();
		List<ProviderCatalog.Model> rest = new ArrayList<>();
		int legacy = 0;
		for (ProviderCatalog.Model m : models()) {
			if (!m.matches(query)) {
				continue;
			}
			boolean fav = favorite.test(m);
			if (m.isLegacy() && !fav && !m.slug().equals(selected)) {
				legacy++;
				if (!showLegacy) {
					continue;
				}
			}
			(fav ? favs : rest).add(m);
		}
		favs.addAll(rest);
		return new Listing(favs, legacy, showLegacy);
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
		pick = new ModelPick(p.instanceId(), m.slug(), carry(m, pick), pick.runtime(), pick.interaction());
		mark(before);
		return true;
	}

	/**
	 * Sets select option {@code id} of the chosen model to {@code value}; null = the server's own default
	 * (the option is not sent). False when the model has no such select or does not offer the value.
	 */
	public boolean selectOption(String id, @Nullable String value) {
		ProviderCatalog.Model m = model();
		ProviderCatalog.Descriptor d = m == null ? null : m.descriptor(id);
		if (pick == null || d == null || !d.isSelect() || (value != null && !d.has(value))) {
			return false;
		}
		ModelPick before = pick;
		pick = value == null ? pick.withoutOption(id) : pick.withOption(ProviderOptionSelection.of(id, value));
		mark(before);
		return true;
	}

	/** Sets the reasoning select of the chosen model; see {@link #selectOption}. */
	public boolean selectReasoning(@Nullable String value) {
		ProviderCatalog.Descriptor d = reasoning();
		return d != null && selectOption(d.id(), value);
	}

	/** Sets boolean option {@code id} of the chosen model. False when the model has no such switch. */
	public boolean setFlag(String id, boolean value) {
		ProviderCatalog.Model m = model();
		ProviderCatalog.Descriptor d = m == null ? null : m.descriptor(id);
		if (pick == null || d == null || !d.flag()) {
			return false;
		}
		ModelPick before = pick;
		pick = pick.withOption(ProviderOptionSelection.of(id, value));
		mark(before);
		return true;
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

	/** Default model of {@code p} with its default options; modes clamped to what {@code p} supports. */
	static ModelPick forProvider(ProviderCatalog.Provider p, Modes.Runtime runtime, Modes.Interaction interaction) {
		ProviderCatalog.Model m = p.defaultModel();
		if (m == null) {
			throw new IllegalArgumentException("provider without models");
		}
		List<Modes.Runtime> rs = p.runtimeModes();
		Modes.Runtime r = rs.contains(runtime) ? runtime : rs.contains(Modes.Runtime.DEFAULT) ? Modes.Runtime.DEFAULT : rs.getFirst();
		Modes.Interaction i = p.showInteractionModeToggle() ? interaction : Modes.Interaction.DEFAULT;
		return new ModelPick(p.instanceId(), m.slug(), carry(m, null), r, i);
	}

	/**
	 * The options of {@code m} for a pick that had {@code old}: an option {@code old} has and {@code m}
	 * accepts is kept, any other option starts at the model's default (a select with a default choice, a
	 * switch whose current value the server reports).
	 */
	static List<ProviderOptionSelection> carry(ProviderCatalog.Model m, @Nullable ModelPick old) {
		List<ProviderOptionSelection> out = new ArrayList<>();
		for (ProviderCatalog.Descriptor d : m.descriptors()) {
			ProviderOptionSelection kept = old == null ? null : old.option(d.id());
			if (d.flag()) {
				Boolean v = kept != null && kept.flag() != null ? kept.flag() : d.currentFlag();
				if (v != null) {
					out.add(ProviderOptionSelection.of(d.id(), v));
				}
			} else {
				String v = kept != null && d.has(kept.text()) ? kept.text() : d.defaultValue();
				if (v != null) {
					out.add(ProviderOptionSelection.of(d.id(), v));
				}
			}
		}
		return out;
	}
}
