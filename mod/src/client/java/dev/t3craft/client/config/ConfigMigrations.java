package dev.t3craft.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.function.Consumer;

/**
 * Upgrades a raw {@code config/t3craft.json} object step by step to
 * {@link T3CraftSettings#SCHEMA_VERSION}. Each step only adds or moves keys and never drops a key it
 * does not understand (other features may own them). Steps work on a deep copy.
 */
public final class ConfigMigrations {
	/** One upgrade from {@code from} to {@code from + 1}. */
	private record Step(int from, Consumer<JsonObject> apply) {
	}

	private static final List<Step> STEPS = List.of(
			// v0 → v1: files written before this branch (ModConfig defaults or hand-written).
			new Step(0, o -> {
				// ModConfig treats "bridgeUrl present, mode absent" as a pre-v2.1 bridge install: make it explicit.
				if (!o.has("mode")) {
					o.addProperty("mode", o.has("bridgeUrl") && !o.get("bridgeUrl").isJsonNull() ? "bridge" : "direct");
				}
				// Some hand-written files nest includeSubagents under visibility; ModConfig reads it at the top level.
				JsonElement vis = o.get("visibility");
				if (vis != null && vis.isJsonObject()) {
					JsonObject v = vis.getAsJsonObject();
					if (v.has("includeSubagents") && !o.has("includeSubagents")) {
						o.add("includeSubagents", v.remove("includeSubagents"));
					}
				}
				if (!o.has("notifications")) {
					o.add("notifications", new JsonObject());
				}
				if (!o.has("environments")) {
					o.add("environments", new JsonObject());
				}
			}));

	private ConfigMigrations() {
	}

	/** Version recorded in the object; 0 when absent or not a number. */
	public static int versionOf(JsonObject o) {
		JsonElement v = o.get("schemaVersion");
		if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
			return Math.max(0, v.getAsInt());
		}
		return 0;
	}

	/** Result of {@link #migrate}: the upgraded copy and the version it started from. */
	public record Migrated(JsonObject json, int fromVersion) {
		public boolean changed() {
			return fromVersion < T3CraftSettings.SCHEMA_VERSION;
		}

		/** Written by a newer mod version: read what we can, never overwrite it. */
		public boolean fromFuture() {
			return fromVersion > T3CraftSettings.SCHEMA_VERSION;
		}
	}

	public static Migrated migrate(JsonObject original) {
		JsonObject o = original.deepCopy();
		int from = versionOf(o);
		int v = from;
		for (Step s : STEPS) {
			if (s.from() == v) {
				s.apply().accept(o);
				v++;
				o.addProperty("schemaVersion", v);
			}
		}
		return new Migrated(o, from);
	}
}
