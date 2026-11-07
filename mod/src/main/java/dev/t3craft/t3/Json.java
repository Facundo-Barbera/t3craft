package dev.t3craft.t3;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.jspecify.annotations.Nullable;

/** Tolerant Gson accessors: T3 adds fields often, older servers omit some. Never throws. */
public final class Json {
	private Json() {
	}

	public static @Nullable JsonObject obj(@Nullable JsonObject o, String key) {
		if (o == null) {
			return null;
		}
		JsonElement e = o.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	public static @Nullable JsonArray arr(@Nullable JsonObject o, String key) {
		if (o == null) {
			return null;
		}
		JsonElement e = o.get(key);
		return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
	}

	public static @Nullable String str(@Nullable JsonObject o, String key) {
		if (o == null) {
			return null;
		}
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	public static boolean bool(@Nullable JsonObject o, String key) {
		if (o == null) {
			return false;
		}
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
	}

	/** Boolean that distinguishes absent/null from false. */
	public static @Nullable Boolean optBool(@Nullable JsonObject o, String key) {
		if (o == null) {
			return null;
		}
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : null;
	}

	public static long num(@Nullable JsonObject o, String key, long def) {
		if (o == null) {
			return def;
		}
		JsonElement e = o.get(key);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsLong() : def;
		} catch (NumberFormatException ex) {
			return def;
		}
	}

	/** ISO 8601 timestamp → epoch millis, or null when absent/unparseable. */
	public static @Nullable Long time(@Nullable JsonObject o, String key) {
		String s = str(o, key);
		if (s == null || s.isEmpty()) {
			return null;
		}
		try {
			return Instant.parse(s).toEpochMilli();
		} catch (DateTimeParseException ex) {
			return null;
		}
	}
}
