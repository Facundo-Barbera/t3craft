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
 * {@code ServerProviderModel} in {@code packages/contracts/src/server.ts}). The effort of a model is
 * its first {@code select} option descriptor ({@code ModelCapabilities.optionDescriptors}), as
 * {@code TraitsPicker.getSelectedTraits} picks {@code primarySelectDescriptor}. Pure, immutable.
 */
public record ProviderCatalog(List<Provider> providers) {
	public static final ProviderCatalog EMPTY = new ProviderCatalog(List.of());

	public ProviderCatalog {
		providers = List.copyOf(providers);
	}

	/** One option of a select descriptor ({@code ProviderOptionChoice}). */
	public record Choice(String id, String label, boolean isDefault) {
	}

	/** The model's effort control: a {@code select} descriptor ({@code effort}, {@code reasoningEffort}…). */
	public record Effort(String id, String label, List<Choice> choices, @Nullable String currentValue) {
		public Effort {
			choices = List.copyOf(choices);
		}

		/** {@code currentValue}, else the choice marked default, else null (T3 shows "Default"). */
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
	public record Model(String slug, String name, @Nullable String shortName, boolean isDefault, @Nullable Effort effort) {
		public String label() {
			return shortName != null ? shortName : name;
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
		return new Model(slug, name, blank(Json.str(o, "shortName")), Boolean.TRUE.equals(Json.optBool(o, "isDefault")),
				effort(Json.obj(o, "capabilities")));
	}

	/** The first {@code select} descriptor with at least one option, or null. */
	static @Nullable Effort effort(@Nullable JsonObject caps) {
		JsonArray ds = Json.arr(caps, "optionDescriptors");
		if (ds == null) {
			return null;
		}
		for (JsonElement e : ds) {
			if (!(e instanceof JsonObject d) || !"select".equals(Json.str(d, "type"))) {
				continue;
			}
			String id = blank(Json.str(d, "id"));
			JsonArray opts = Json.arr(d, "options");
			if (id == null || opts == null) {
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
				return new Effort(id, Objects.requireNonNullElse(blank(Json.str(d, "label")), id), choices, blank(Json.str(d, "currentValue")));
			}
		}
		return null;
	}

	private static @Nullable String blank(@Nullable String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
