package dev.t3craft.client.t3ui.actions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.t3craft.t3.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The provider instances of one environment and their models, decoded tolerantly from
 * {@code server.getConfig} ({@code ServerConfig.providers}, {@code ServerProvider} /
 * {@code ServerProviderModel} in {@code packages/contracts/src/server.ts}). A model's controls are its
 * option descriptors ({@code ModelCapabilities.optionDescriptors}): the reasoning select is the one with
 * {@code role: "reasoning"}, else the first select, as {@code TraitsPicker} picks its primary select
 * descriptor; the other selects and the booleans follow. Pure, immutable.
 */
public record ProviderCatalog(List<Provider> providers) {
	public static final ProviderCatalog EMPTY = new ProviderCatalog(List.of());

	public ProviderCatalog {
		providers = List.copyOf(providers);
	}

	/** One option of a select descriptor ({@code ProviderOptionChoice}). */
	public record Choice(String id, String label, boolean isDefault) {
	}

	/** The two kinds of option descriptor the picker draws: a drop-down and an on/off switch. */
	public enum Kind {
		SELECT, BOOLEAN
	}

	/**
	 * One option descriptor of a model ({@code ModelCapabilities.optionDescriptors}): a {@code select}
	 * ({@code effort}, {@code reasoningEffort}...) with its {@link Choice}s, or a {@code boolean}
	 * ({@code fastMode}, {@code thinking}...). {@code role} is T3's hint of what it controls
	 * ({@code "reasoning"} marks the reasoning select).
	 */
	public record Option(Kind kind, String id, String label, @Nullable String role, @Nullable String description, List<Choice> choices,
			@Nullable String currentValue, @Nullable Boolean currentBool) {
		public Option {
			choices = List.copyOf(choices);
		}

		public boolean isSelect() {
			return kind == Kind.SELECT;
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

		public boolean has(@Nullable String value) {
			return value != null && choices.stream().anyMatch(c -> c.id().equals(value));
		}

		public String labelOf(@Nullable String value) {
			return choices.stream().filter(c -> c.id().equals(value)).map(Choice::label).findFirst().orElse(value == null ? "Default" : value);
		}
	}

	/** {@code ServerProviderModel}. */
	public record Model(String slug, String name, @Nullable String shortName, boolean isDefault, boolean isLegacy, @Nullable String badge,
			List<String> aliases, @Nullable String subProvider, boolean isCustom, List<Option> options) {
		public Model {
			aliases = List.copyOf(aliases);
			options = List.copyOf(options);
		}

		public String label() {
			return shortName != null ? shortName : name;
		}

		/** The reasoning control: the select with {@code role: "reasoning"}, else the first select; null when there is none. */
		public @Nullable Option reasoning() {
			Option first = null;
			for (Option o : options) {
				if (o.isSelect()) {
					if (o.isReasoning()) {
						return o;
					}
					if (first == null) {
						first = o;
					}
				}
			}
			return first;
		}

		/** The other select descriptors, each its own drop-down under Reasoning. */
		public List<Option> extraSelects() {
			Option r = reasoning();
			return options.stream().filter(o -> o.isSelect() && o != r).toList();
		}

		/** The boolean descriptors (fast, thinking...), each a switch under Reasoning. */
		public List<Option> toggles() {
			return options.stream().filter(o -> !o.isSelect()).toList();
		}

		public @Nullable Option option(@Nullable String id) {
			return options.stream().filter(o -> o.id().equals(id)).findFirst().orElse(null);
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

		/** The model marked {@code isDefault}, else the first one; null when there is none. */
		public @Nullable Model defaultModel() {
			return models.stream().filter(Model::isDefault).findFirst().orElse(models.isEmpty() ? null : models.getFirst());
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
				Boolean.TRUE.equals(Json.optBool(o, "isLegacy")), blank(Json.str(o, "badge")), aliases, blank(Json.str(o, "subProvider")),
				Boolean.TRUE.equals(Json.optBool(o, "isCustom")), options(Json.obj(o, "capabilities")));
	}

	/**
	 * The {@code select} descriptors with at least one option and the {@code boolean} ones, in the order
	 * T3 lists them; other kinds are left out.
	 */
	static List<Option> options(@Nullable JsonObject caps) {
		JsonArray ds = Json.arr(caps, "optionDescriptors");
		if (ds == null) {
			return List.of();
		}
		List<Option> out = new ArrayList<>();
		for (JsonElement e : ds) {
			if (!(e instanceof JsonObject d)) {
				continue;
			}
			String type = Json.str(d, "type");
			String id = blank(Json.str(d, "id"));
			if (id == null || out.stream().anyMatch(x -> x.id().equals(id))) {
				continue;
			}
			String label = Objects.requireNonNullElse(blank(Json.str(d, "label")), id);
			String role = blank(Json.str(d, "role"));
			String description = blank(Json.str(d, "description"));
			if ("boolean".equals(type)) {
				out.add(new Option(Kind.BOOLEAN, id, label, role, description, List.of(), null, Json.optBool(d, "currentValue")));
				continue;
			}
			JsonArray opts = "select".equals(type) ? Json.arr(d, "options") : null;
			if (opts == null) {
				continue;
			}
			List<Choice> choices = new ArrayList<>();
			for (JsonElement c : opts) {
				if (c instanceof JsonObject co) {
					String cid = blank(Json.str(co, "id"));
					if (cid != null) {
						choices.add(new Choice(cid, Objects.requireNonNullElse(blank(Json.str(co, "label")), cid),
								Boolean.TRUE.equals(Json.optBool(co, "isDefault"))));
					}
				}
			}
			if (!choices.isEmpty()) {
				out.add(new Option(Kind.SELECT, id, label, role, description, choices, blank(Json.str(d, "currentValue")), null));
			}
		}
		return out;
	}

	private static @Nullable String blank(@Nullable String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
