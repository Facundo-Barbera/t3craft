package dev.t3craft.client.t3ui.actions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.t3craft.t3.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The provider instances of one environment and their models, decoded tolerantly from
 * {@code server.getConfig} ({@code ServerConfig.providers}, {@code ServerProvider} /
 * {@code ServerProviderModel} in {@code packages/contracts/src/server.ts}). A model's controls are its
 * {@code ModelCapabilities.optionDescriptors}: {@code select} and {@code boolean} descriptors. The reasoning
 * control is the select whose {@code role} is {@code "reasoning"}, else the first select, as
 * {@code TraitsPicker.getSelectedTraits} picks {@code primarySelectDescriptor}; the other selects and the
 * booleans are extra controls. Pure, immutable.
 */
public record ProviderCatalog(List<Provider> providers) {
	public static final ProviderCatalog EMPTY = new ProviderCatalog(List.of());

	public ProviderCatalog {
		providers = List.copyOf(providers);
	}

	/** One option of a select descriptor ({@code ProviderOptionChoice}). */
	public record Choice(String id, String label, boolean isDefault) {
	}

	/**
	 * One control of a model ({@code ProviderOptionDescriptor}): a {@code select} (choices, a current
	 * value) or a {@code boolean} switch (fast, thinking...).
	 *
	 * @param flag         true for a {@code boolean} descriptor
	 * @param role         {@code "reasoning"} for the reasoning control, else null
	 * @param currentValue select: the value the server marks as current/default, else null
	 * @param currentFlag  boolean: the server's current value, null when it does not say
	 */
	public record Descriptor(String id, String label, boolean flag, @Nullable String role, List<Choice> choices, @Nullable String currentValue,
			@Nullable Boolean currentFlag, @Nullable String description) {
		public Descriptor {
			choices = List.copyOf(choices);
		}

		public boolean isSelect() {
			return !flag;
		}

		public boolean isReasoning() {
			return "reasoning".equals(role);
		}

		/** Select: {@code currentValue}, else the choice marked default, else null (T3 shows "Default"). */
		public @Nullable String defaultValue() {
			if (currentValue != null && has(currentValue)) {
				return currentValue;
			}
			return choices.stream().filter(Choice::isDefault).map(Choice::id).findFirst().orElse(null);
		}

		/** Select: the descriptor offers this choice. */
		public boolean has(@Nullable String value) {
			return value != null && choices.stream().anyMatch(c -> c.id().equals(value));
		}

		public String labelOf(@Nullable String value) {
			return choices.stream().filter(c -> c.id().equals(value)).map(Choice::label).findFirst().orElse(value == null ? "Default" : value);
		}

		/** Boolean: the starting value (false when the server does not say). */
		public boolean flagDefault() {
			return Boolean.TRUE.equals(currentFlag);
		}
	}

	/** {@code ServerProviderModel}. */
	public record Model(String slug, String name, @Nullable String shortName, boolean isDefault, boolean isLegacy, boolean isCustom,
			@Nullable String badge, @Nullable String subProvider, List<String> aliases, List<Descriptor> descriptors) {
		public Model {
			aliases = List.copyOf(aliases);
			descriptors = List.copyOf(descriptors);
		}

		public String label() {
			return shortName != null ? shortName : name;
		}

		/** The reasoning control: the select with role "reasoning", else the first select; null when there is no select. */
		public @Nullable Descriptor reasoning() {
			Descriptor first = null;
			for (Descriptor d : descriptors) {
				if (d.isSelect()) {
					if (d.isReasoning()) {
						return d;
					}
					if (first == null) {
						first = d;
					}
				}
			}
			return first;
		}

		/** The selects other than {@link #reasoning()}, in the server's order. */
		public List<Descriptor> extraSelects() {
			Descriptor r = reasoning();
			return descriptors.stream().filter(d -> d.isSelect() && d != r).toList();
		}

		/** The boolean switches, in the server's order. */
		public List<Descriptor> flags() {
			return descriptors.stream().filter(Descriptor::flag).toList();
		}

		public @Nullable Descriptor descriptor(@Nullable String id) {
			return descriptors.stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
		}

		/**
		 * Every word of {@code query} (split on spaces, case-insensitive) is part of the name, short name,
		 * slug, an alias, the sub-provider or the badge. A blank query matches everything.
		 */
		public boolean matches(String query) {
			String q = query.trim().toLowerCase(Locale.ROOT);
			if (q.isEmpty()) {
				return true;
			}
			StringBuilder hay = new StringBuilder(name).append(' ').append(slug);
			if (shortName != null) {
				hay.append(' ').append(shortName);
			}
			if (subProvider != null) {
				hay.append(' ').append(subProvider);
			}
			if (badge != null) {
				hay.append(' ').append(badge);
			}
			aliases.forEach(a -> hay.append(' ').append(a));
			String h = hay.toString().toLowerCase(Locale.ROOT);
			for (String word : q.split("\\s+")) {
				if (!h.contains(word)) {
					return false;
				}
			}
			return true;
		}
	}

	/** {@code ServerProvider}: an instance ({@code instanceId} is what {@code modelSelection} routes by). */
	public record Provider(String instanceId, String driver, @Nullable String displayName, boolean enabled, boolean installed,
			boolean available, @Nullable String unavailableReason, boolean showInteractionModeToggle,
			List<Modes.Runtime> supportedRuntimeModes, List<Model> models) {
		public Provider {
			supportedRuntimeModes = List.copyOf(supportedRuntimeModes);
			models = List.copyOf(models);
		}

		/** Shown in the picker: enabled, installed, not unavailable and with at least one model. */
		public boolean selectable() {
			return enabled && installed && available && !models.isEmpty();
		}

		public String label() {
			return displayName != null ? displayName : instanceId;
		}

		public @Nullable Model model(@Nullable String slug) {
			return models.stream().filter(m -> m.slug().equals(slug)).findFirst().orElse(null);
		}

		/** The model marked {@code isDefault}, else the first one that is not legacy, else the first; null when there is none. */
		public @Nullable Model defaultModel() {
			return models.stream().filter(Model::isDefault).findFirst()
					.orElse(models.stream().filter(m -> !m.isLegacy()).findFirst().orElse(models.isEmpty() ? null : models.getFirst()));
		}

		public List<Modes.Runtime> runtimeModes() {
			return Modes.Runtime.supported(supportedRuntimeModes);
		}
	}

	/** Providers the player can pick from (see {@link Provider#selectable()}). */
	public List<Provider> selectable() {
		return providers.stream().filter(Provider::selectable).toList();
	}

	public @Nullable Provider provider(@Nullable String instanceId) {
		return providers.stream().filter(p -> p.instanceId().equals(instanceId)).findFirst().orElse(null);
	}

	public boolean isEmpty() {
		return selectable().isEmpty();
	}

	/** From the whole {@code ServerConfig} object. */
	public static ProviderCatalog fromServerConfig(@Nullable JsonObject config) {
		return fromProviders(Json.arr(config, "providers"));
	}

	/** From a {@code ServerProviders} array. Entries without {@code instanceId} are skipped. */
	public static ProviderCatalog fromProviders(@Nullable JsonArray arr) {
		if (arr == null) {
			return EMPTY;
		}
		List<Provider> out = new ArrayList<>();
		for (JsonElement e : arr) {
			if (e instanceof JsonObject o) {
				Provider p = provider(o);
				if (p != null && out.stream().noneMatch(q -> q.instanceId().equals(p.instanceId()))) {
					out.add(p);
				}
			}
		}
		return new ProviderCatalog(out);
	}

	static @Nullable Provider provider(JsonObject o) {
		String id = blank(Json.str(o, "instanceId"));
		if (id == null) {
			return null;
		}
		List<Modes.Runtime> runtimes = new ArrayList<>();
		JsonArray rm = Json.arr(o, "supportedRuntimeModes");
		if (rm != null) {
			for (JsonElement e : rm) {
				Modes.Runtime r = e.isJsonPrimitive() ? Modes.Runtime.parse(e.getAsString()) : null;
				if (r != null && !runtimes.contains(r)) {
					runtimes.add(r);
				}
			}
		}
		List<Model> models = new ArrayList<>();
		JsonArray ms = Json.arr(o, "models");
		if (ms != null) {
			for (JsonElement e : ms) {
				Model m = e instanceof JsonObject mo ? model(mo) : null;
				if (m != null && models.stream().noneMatch(x -> x.slug().equals(m.slug()))) {
					models.add(m);
				}
			}
		}
		Boolean enabled = Json.optBool(o, "enabled");
		Boolean installed = Json.optBool(o, "installed");
		return new Provider(id, Objects.requireNonNullElse(blank(Json.str(o, "driver")), id), blank(Json.str(o, "displayName")),
				enabled == null || enabled, installed == null || installed, !"unavailable".equals(Json.str(o, "availability")),
				blank(Json.str(o, "unavailableReason")), Boolean.TRUE.equals(Json.optBool(o, "showInteractionModeToggle")), runtimes, models);
	}

	static @Nullable Model model(JsonObject o) {
		String slug = blank(Json.str(o, "slug"));
		if (slug == null) {
			return null;
		}
		String name = Objects.requireNonNullElse(blank(Json.str(o, "name")), slug);
		List<String> aliases = new ArrayList<>();
		JsonArray al = Json.arr(o, "aliases");
		if (al != null) {
			for (JsonElement e : al) {
				String a = e.isJsonPrimitive() ? blank(e.getAsString()) : null;
				if (a != null) {
					aliases.add(a);
				}
			}
		}
		return new Model(slug, name, blank(Json.str(o, "shortName")), Boolean.TRUE.equals(Json.optBool(o, "isDefault")),
				Boolean.TRUE.equals(Json.optBool(o, "isLegacy")), Boolean.TRUE.equals(Json.optBool(o, "isCustom")), blank(Json.str(o, "badge")),
				blank(Json.str(o, "subProvider")), aliases, descriptors(Json.obj(o, "capabilities")));
	}

	/** The {@code select} (with at least one option) and {@code boolean} descriptors; unknown types are skipped. */
	static List<Descriptor> descriptors(@Nullable JsonObject caps) {
		List<Descriptor> out = new ArrayList<>();
		JsonArray ds = Json.arr(caps, "optionDescriptors");
		if (ds == null) {
			return out;
		}
		for (JsonElement e : ds) {
			if (!(e instanceof JsonObject d)) {
				continue;
			}
			String id = blank(Json.str(d, "id"));
			String type = Json.str(d, "type");
			if (id == null || out.stream().anyMatch(x -> x.id().equals(id))) {
				continue;
			}
			String label = Objects.requireNonNullElse(blank(Json.str(d, "label")), id);
			String role = blank(Json.str(d, "role"));
			String description = blank(Json.str(d, "description"));
			if ("boolean".equals(type)) {
				out.add(new Descriptor(id, label, true, role, List.of(), null, Json.optBool(d, "currentValue"), description));
			} else if ("select".equals(type)) {
				List<Choice> choices = new ArrayList<>();
				JsonArray opts = Json.arr(d, "options");
				if (opts != null) {
					for (JsonElement c : opts) {
						if (c instanceof JsonObject co) {
							String cid = blank(Json.str(co, "id"));
							if (cid != null) {
								choices.add(new Choice(cid, Objects.requireNonNullElse(blank(Json.str(co, "label")), cid),
										Boolean.TRUE.equals(Json.optBool(co, "isDefault"))));
							}
						}
					}
				}
				if (!choices.isEmpty()) {
					out.add(new Descriptor(id, label, false, role, choices, blank(Json.str(d, "currentValue")), null, description));
				}
			}
		}
		return out;
	}

	private static @Nullable String blank(@Nullable String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
