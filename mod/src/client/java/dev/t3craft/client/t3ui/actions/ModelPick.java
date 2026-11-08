package dev.t3craft.client.t3ui.actions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.t3craft.t3.Json;
import dev.t3craft.t3.ThreadLaunch;
import org.jspecify.annotations.Nullable;

/**
 * What the model picker selects: provider instance, model, effort (optional) and the thread modes.
 * {@link #modelSelection()} is the wire {@code ModelSelection}
 * ({@code {instanceId, model, options?: [{id, value}]}}, {@code packages/contracts/src/modelSelection.ts}).
 *
 * @param effortId    the effort descriptor id ({@code effort}, {@code reasoningEffort}…), null = none
 * @param effortValue the chosen effort, null = the model's default (no option sent)
 */
public record ModelPick(String instanceId, String model, @Nullable String effortId, @Nullable String effortValue,
		Modes.Runtime runtime, Modes.Interaction interaction) {
	public ModelPick {
		if (instanceId == null || instanceId.isBlank() || model == null || model.isBlank()) {
			throw new IllegalArgumentException("instanceId and model are required");
		}
		runtime = runtime == null ? Modes.Runtime.DEFAULT : runtime;
		interaction = interaction == null ? Modes.Interaction.DEFAULT : interaction;
		if (effortId == null || effortValue == null) {
			effortValue = null;
		}
	}

	public ModelPick withEffort(@Nullable String id, @Nullable String value) {
		return new ModelPick(instanceId, model, id, value, runtime, interaction);
	}

	public ModelPick withRuntime(Modes.Runtime r) {
		return new ModelPick(instanceId, model, effortId, effortValue, r, interaction);
	}

	public ModelPick withInteraction(Modes.Interaction i) {
		return new ModelPick(instanceId, model, effortId, effortValue, runtime, i);
	}

	/**
	 * The wire {@code ModelSelection}: {@code {instanceId, model}} plus, only when an effort was
	 * chosen, the canonical {@code options: [{id, value}]}.
	 */
	public JsonObject modelSelection() {
		JsonObject o = new JsonObject();
		o.addProperty("instanceId", instanceId);
		o.addProperty("model", model);
		if (effortId != null && effortValue != null) {
			JsonArray arr = new JsonArray();
			JsonObject opt = new JsonObject();
			opt.addProperty("id", effortId);
			opt.addProperty("value", effortValue);
			arr.add(opt);
			o.add("options", arr);
		}
		return o;
	}

	/** Same model and effort (modes ignored). */
	public boolean sameModel(@Nullable ModelPick o) {
		return o != null && instanceId.equals(o.instanceId) && model.equals(o.model) && java.util.Objects.equals(effortValue, o.effortValue);
	}

	/** {@code Provider · Model · Effort} for the closed picker chip. */
	public String label(@Nullable ProviderCatalog catalog) {
		ProviderCatalog.Provider p = catalog == null ? null : catalog.provider(instanceId);
		ProviderCatalog.Model m = p == null ? null : p.model(model);
		StringBuilder sb = new StringBuilder(p == null ? instanceId : p.label()).append(" · ").append(m == null ? model : m.label());
		if (effortValue != null) {
			sb.append(" · ").append(m != null && m.effort() != null ? m.effort().labelOf(effortValue) : effortValue);
		}
		return sb.toString();
	}

	/**
	 * Reads a wire {@code ModelSelection} ({@code instanceId} or legacy {@code provider}; options as
	 * {@code [{id, value}]} or the legacy {@code {id: value}} object). Effort = the first string option.
	 */
	public static @Nullable ModelPick fromSelection(@Nullable JsonObject sel, Modes.Runtime runtime, Modes.Interaction interaction) {
		ThreadLaunch.ModelChoice c = ThreadLaunch.ModelChoice.fromJson(sel);
		if (c == null) {
			return null;
		}
		String effortId = null;
		String effortValue = null;
		JsonElement opts = sel == null ? null : sel.get("options");
		if (opts instanceof JsonArray arr) {
			for (JsonElement e : arr) {
				if (e instanceof JsonObject o && o.get("value") != null && o.get("value").isJsonPrimitive()
						&& o.get("value").getAsJsonPrimitive().isString() && Json.str(o, "id") != null) {
					effortId = Json.str(o, "id");
					effortValue = Json.str(o, "value");
					break;
				}
			}
		} else if (opts instanceof JsonObject obj) {
			for (var en : obj.entrySet()) {
				if (en.getValue().isJsonPrimitive() && en.getValue().getAsJsonPrimitive().isString()) {
					effortId = en.getKey();
					effortValue = en.getValue().getAsString();
					break;
				}
			}
		}
		return new ModelPick(c.instanceId(), c.model(), effortId, effortValue, runtime, interaction);
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
		return new ModelPick(meta.providerKey(), meta.model(), null, null, Modes.Runtime.DEFAULT, Modes.Interaction.DEFAULT);
	}
}
