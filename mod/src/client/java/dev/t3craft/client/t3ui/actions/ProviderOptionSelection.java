package dev.t3craft.client.t3ui.actions;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.t3craft.t3.Json;
import org.jspecify.annotations.Nullable;

/**
 * One chosen option of a model ({@code ProviderOptionSelection}, {@code packages/contracts/src/modelSelection.ts}):
 * the id of an option descriptor of the model ({@code effort}, {@code fastMode}...) and its value, a string for a
 * {@code select} descriptor and a boolean for a {@code boolean} one. {@code modelSelection.options} is a list of these.
 */
public record ProviderOptionSelection(String id, Object value) {
	public ProviderOptionSelection {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("option id is required");
		}
		if (!(value instanceof String) && !(value instanceof Boolean)) {
			throw new IllegalArgumentException("option value must be a string or a boolean");
		}
	}

	public static ProviderOptionSelection of(String id, String value) {
		return new ProviderOptionSelection(id, value);
	}

	public static ProviderOptionSelection of(String id, boolean value) {
		return new ProviderOptionSelection(id, value);
	}

	/** The value of a select option, null for a boolean one. */
	public @Nullable String string() {
		return value instanceof String s ? s : null;
	}

	/** The value of a boolean option, false for a select one. */
	public boolean bool() {
		return value instanceof Boolean b && b;
	}

	/** {@code {id, value}} as T3 reads it. */
	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("id", id);
		if (value instanceof Boolean b) {
			o.addProperty("value", b);
		} else {
			o.addProperty("value", (String) value);
		}
		return o;
	}

	/** From an {@code {id, value}} object; null when the id is missing or the value is not a string or a boolean. */
	public static @Nullable ProviderOptionSelection fromJson(@Nullable JsonObject o) {
		return o == null ? null : of(Json.str(o, "id"), o.get("value"));
	}

	/** From an id and a primitive (the legacy {@code {id: value}} object); null when it is not a string or a boolean. */
	public static @Nullable ProviderOptionSelection of(@Nullable String id, @Nullable JsonElement value) {
		if (id == null || id.isBlank() || value == null || !value.isJsonPrimitive()) {
			return null;
		}
		JsonPrimitive p = value.getAsJsonPrimitive();
		if (p.isBoolean()) {
			return of(id, p.getAsBoolean());
		}
		return p.isString() ? of(id, p.getAsString()) : null;
	}
}
