package dev.t3craft.client.t3ui.actions;

import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

/**
 * One chosen model option, {@code ProviderOptionSelection} of {@code packages/contracts/src/modelSelection.ts}:
 * the descriptor {@code id} and its value, a string (a {@code select} choice id) or a boolean (a switch).
 * Immutable.
 *
 * @param value a {@link String} or a {@link Boolean}
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

	/** The value when it is a string, else null. */
	public @Nullable String text() {
		return value instanceof String s ? s : null;
	}

	/** The value when it is a boolean, else null. */
	public @Nullable Boolean flag() {
		return value instanceof Boolean b ? b : null;
	}

	/** {@code {id, value}} as the wire carries it. */
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
}
