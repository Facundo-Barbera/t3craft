package dev.t3craft.client.t3ui.actions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.t3craft.t3.Json;
import dev.t3craft.t3.ThreadLaunch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What the model picker selects: provider instance, model, the chosen options (reasoning, fast...) and the
 * thread modes. {@link #modelSelection()} is the wire {@code ModelSelection}
 * ({@code {instanceId, model, options?: [{id, value}]}}, {@code packages/contracts/src/modelSelection.ts}).
 *
 * @param options the chosen option descriptors by id; an option left out takes the model's default (nothing is sent)
 */
public record ModelPick(String instanceId, String model, List<ProviderOptionSelection> options, Modes.Runtime runtime,
		Modes.Interaction interaction) {
	public ModelPick {
		if (instanceId == null || instanceId.isBlank() || model == null || model.isBlank()) {
			throw new IllegalArgumentException("instanceId and model are required");
		}
		options = options == null ? List.of() : List.copyOf(options);
		runtime = runtime == null ? Modes.Runtime.DEFAULT : runtime;
		interaction = interaction == null ? Modes.Interaction.DEFAULT : interaction;
	}

	/** The chosen option {@code id}, or null (the model's default). */
	public @Nullable ProviderOptionSelection option(@Nullable String id) {
		for (ProviderOptionSelection s : options) {
			if (s.id().equals(id)) {
				return s;
			}
		}
		return null;
	}

	/** The chosen value of the select option {@code id}, or null. */
	public @Nullable String optionString(@Nullable String id) {
		ProviderOptionSelection s = option(id);
		return s == null ? null : s.string();
	}

	/** {@code selection} added, or replacing the one with the same id. */
	public ModelPick withOption(ProviderOptionSelection selection) {
		List<ProviderOptionSelection> out = new ArrayList<>(without(selection.id()));
		out.add(selection);
		return withOptions(out);
	}

	/** Option {@code id} back to the model's default. */
	public ModelPick withoutOption(String id) {
		return withOptions(without(id));
	}

	public ModelPick withOptions(List<ProviderOptionSelection> o) {
		return new ModelPick(instanceId, model, o, runtime, interaction);
	}

	private List<ProviderOptionSelection> without(String id) {
		return options.stream().filter(s -> !s.id().equals(id)).toList();
	}

	public ModelPick withRuntime(Modes.Runtime r) {
		return new ModelPick(instanceId, model, options, r, interaction);
	}

	public ModelPick withInteraction(Modes.Interaction i) {
		return new ModelPick(instanceId, model, options, runtime, i);
	}

	/** The wire {@code ModelSelection}: {@code {instanceId, model}} plus, only when something was chosen, {@code options: [{id, value}]}. */
	public JsonObject modelSelection() {
		JsonObject o = new JsonObject();
		o.addProperty("instanceId", instanceId);
		o.addProperty("model", model);
		if (!options.isEmpty()) {
			JsonArray arr = new JsonArray();
			options.forEach(s -> arr.add(s.toJson()));
			o.add("options", arr);
		}
		return o;
	}

	/** Same provider, model and options (modes ignored). */
	public boolean sameModel(@Nullable ModelPick o) {
		return o != null && instanceId.equals(o.instanceId) && model.equals(o.model) && byId().equals(o.byId());
	}

	private Map<String, Object> byId() {
		Map<String, Object> m = new HashMap<>();
		options.forEach(s -> m.put(s.id(), s.value()));
		return m;
	}

	/** {@code Provider · Model · Reasoning} for the closed picker chip. */
	public String label(@Nullable ProviderCatalog catalog) {
		ProviderCatalog.Provider p = catalog == null ? null : catalog.provider(instanceId);
		ProviderCatalog.Model m = p == null ? null : p.model(model);
		StringBuilder sb = new StringBuilder(p == null ? instanceId : p.label()).append(" · ").append(m == null ? model : m.label());
		String reasoning = reasoningLabel(m);
		if (reasoning != null) {
			sb.append(" · ").append(reasoning);
		}
		return sb.toString();
	}

	/** The label of the chosen reasoning value of {@code m} (the raw value without the catalog), or null when none was chosen. */
	public @Nullable String reasoningLabel(ProviderCatalog.@Nullable Model m) {
		ProviderCatalog.Option r = m == null ? null : m.reasoning();
		if (r != null) {
			String v = optionString(r.id());
			return v == null ? null : r.labelOf(v);
		}
		for (ProviderOptionSelection s : options) {
			if (s.string() != null) {
				return s.string();
			}
		}
		return null;
	}

	/**
	 * Reads a wire {@code ModelSelection} ({@code instanceId} or legacy {@code provider}; options as
	 * {@code [{id, value}]} or the legacy {@code {id: value}} object). Values that are neither a string nor
	 * a boolean are dropped.
	 */
	public static @Nullable ModelPick fromSelection(@Nullable JsonObject sel, Modes.Runtime runtime, Modes.Interaction interaction) {
		ThreadLaunch.ModelChoice c = ThreadLaunch.ModelChoice.fromJson(sel);
		if (c == null) {
			return null;
		}
		List<ProviderOptionSelection> options = new ArrayList<>();
		JsonElement opts = sel == null ? null : sel.get("options");
		if (opts instanceof JsonArray arr) {
			for (JsonElement e : arr) {
				ProviderOptionSelection s = e instanceof JsonObject o ? ProviderOptionSelection.fromJson(o) : null;
				if (s != null && options.stream().noneMatch(x -> x.id().equals(s.id()))) {
					options.add(s);
				}
			}
		} else if (opts instanceof JsonObject obj) {
			for (var en : obj.entrySet()) {
				ProviderOptionSelection s = ProviderOptionSelection.of(en.getKey(), en.getValue());
				if (s != null) {
					options.add(s);
				}
			}
		}
		return new ModelPick(c.instanceId(), c.model(), options, runtime, interaction);
	}

	/**
	 * From a thread object that carries {@code modelSelection}, {@code runtimeMode} and
	 * {@code interactionMode} (the shell thread or the projection's {@code thread}); null when it has
	 * no usable model. Unknown modes fall back to T3Craft's defaults.
	 */
	public static @Nullable ModelPick fromThreadJson(@Nullable JsonObject thread) {
		if (thread == null) {
			return null;
		}
		Modes.Runtime r = Modes.Runtime.parse(Json.str(thread, "runtimeMode"));
		Modes.Interaction i = Modes.Interaction.parse(Json.str(thread, "interactionMode"));
		return fromSelection(Json.obj(thread, "modelSelection"), r == null ? Modes.Runtime.DEFAULT : r, i == null ? Modes.Interaction.DEFAULT : i);
	}

	/** From the shell's {@link dev.t3craft.t3.ThreadMeta} (model only; modes default); null without a model. */
	public static @Nullable ModelPick fromMeta(dev.t3craft.t3.@Nullable ThreadMeta meta) {
		if (meta == null || meta.providerKey() == null || meta.model() == null) {
			return null;
		}
		return new ModelPick(meta.providerKey(), meta.model(), List.of(), Modes.Runtime.DEFAULT, Modes.Interaction.DEFAULT);
	}
}
