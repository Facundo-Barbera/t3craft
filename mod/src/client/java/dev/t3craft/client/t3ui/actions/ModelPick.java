package dev.t3craft.client.t3ui.actions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.t3craft.t3.Json;
import dev.t3craft.t3.ThreadLaunch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What the model picker selects: provider instance, model, the chosen model options (reasoning, extra
 * selects, switches) and the thread modes. {@link #modelSelection()} is the wire {@code ModelSelection}
 * ({@code {instanceId, model, options?: [{id, value}]}}, {@code packages/contracts/src/modelSelection.ts}).
 *
 * @param options the options the player (or the model's defaults) set, at most one per id; an option
 *                that is not here is sent as nothing, so the server uses its own default
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

	/** The selection of option {@code id}, or null. */
	public @Nullable ProviderOptionSelection option(@Nullable String id) {
		for (ProviderOptionSelection o : options) {
			if (o.id().equals(id)) {
				return o;
			}
		}
		return null;
	}

	/** The string value of select option {@code id}, or null. */
	public @Nullable String value(@Nullable String id) {
		ProviderOptionSelection o = option(id);
		return o == null ? null : o.text();
	}

	/** The value of boolean option {@code id}, or null. */
	public @Nullable Boolean flag(@Nullable String id) {
		ProviderOptionSelection o = option(id);
		return o == null ? null : o.flag();
	}

	/** Sets {@code sel}, replacing the option with the same id. */
	public ModelPick withOption(ProviderOptionSelection sel) {
		List<ProviderOptionSelection> out = new ArrayList<>(options.size() + 1);
		boolean replaced = false;
		for (ProviderOptionSelection o : options) {
			if (o.id().equals(sel.id())) {
				out.add(sel);
				replaced = true;
			} else {
				out.add(o);
			}
		}
		if (!replaced) {
			out.add(sel);
		}
		return new ModelPick(instanceId, model, out, runtime, interaction);
	}

	/** Drops option {@code id}: the model's own default applies. */
	public ModelPick withoutOption(String id) {
		return new ModelPick(instanceId, model, options.stream().filter(o -> !o.id().equals(id)).toList(), runtime, interaction);
	}

	public ModelPick withRuntime(Modes.Runtime r) {
		return new ModelPick(instanceId, model, options, r, interaction);
	}

	public ModelPick withInteraction(Modes.Interaction i) {
		return new ModelPick(instanceId, model, options, runtime, i);
	}

	/** The wire {@code ModelSelection}: {@code {instanceId, model}} plus {@code options: [{id, value}]} when there are any. */
	public JsonObject modelSelection() {
		JsonObject o = new JsonObject();
		o.addProperty("instanceId", instanceId);
		o.addProperty("model", model);
		if (!options.isEmpty()) {
			JsonArray arr = new JsonArray();
			options.forEach(opt -> arr.add(opt.toJson()));
			o.add("options", arr);
		}
		return o;
	}

	/** Same provider, model and options (order ignored; modes ignored). */
	public boolean sameModel(@Nullable ModelPick o) {
		return o != null && instanceId.equals(o.instanceId) && model.equals(o.model)
				&& new HashSet<>(options).equals(new HashSet<>(o.options));
	}

	/** {@code Provider · Model · Reasoning} for a one-line label. */
	public String label(@Nullable ProviderCatalog catalog) {
		ProviderCatalog.Provider p = catalog == null ? null : catalog.provider(instanceId);
		ProviderCatalog.Model m = p == null ? null : p.model(model);
		StringBuilder sb = new StringBuilder(p == null ? instanceId : p.label()).append(" · ").append(m == null ? model : m.label());
		String effort = reasoningLabel(m);
		if (effort != null) {
			sb.append(" · ").append(effort);
		}
		return sb.toString();
	}

	/** The label of the chosen reasoning value of {@code m} (the raw value when {@code m} is unknown), or null when none is set. */
	public @Nullable String reasoningLabel(ProviderCatalog.@Nullable Model m) {
		if (m == null) {
			for (ProviderOptionSelection o : options) {
				if (o.text() != null) {
					return o.text();
				}
			}
			return null;
		}
		ProviderCatalog.Descriptor d = m.reasoning();
		if (d == null) {
			return null;
		}
		String v = value(d.id());
		return v == null ? null : d.labelOf(v);
	}

	/**
	 * Reads a wire {@code ModelSelection} ({@code instanceId} or legacy {@code provider}; options as
	 * {@code [{id, value}]} or the legacy {@code {id: value}} object). String and boolean values are kept.
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
				if (e instanceof JsonObject o && Json.str(o, "id") != null) {
					add(options, Json.str(o, "id"), o.get("value"));
				}
			}
		} else if (opts instanceof JsonObject obj) {
			for (var en : obj.entrySet()) {
				add(options, en.getKey(), en.getValue());
			}
		}
		return new ModelPick(c.instanceId(), c.model(), options, runtime, interaction);
	}

	private static void add(List<ProviderOptionSelection> out, @Nullable String id, @Nullable JsonElement value) {
		if (id == null || id.isBlank() || !(value instanceof JsonPrimitive p) || out.stream().anyMatch(o -> o.id().equals(id))) {
			return;
		}
		if (p.isBoolean()) {
			out.add(ProviderOptionSelection.of(id, p.getAsBoolean()));
		} else if (p.isString()) {
			out.add(ProviderOptionSelection.of(id, p.getAsString()));
		}
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
