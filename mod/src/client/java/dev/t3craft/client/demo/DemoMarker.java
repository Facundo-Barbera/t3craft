package dev.t3craft.client.demo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

/**
 * What has already been done to a demo world, stored next to its {@code level.dat} as
 * {@code t3craft-demo.json}. Lives in the save (not in the global config) so deleting or copying
 * the world carries it along.
 *
 * @param setup     world setup (game rules, time, first teleport) applied; version of that step
 * @param structure id of the structure source last built completely, or null
 * @param version   plan version of that structure
 */
public record DemoMarker(int setup, @Nullable String structure, int version) {
	public static final String FILE_NAME = "t3craft-demo.json";
	/** Bump when {@link VanillaDemoWorld#applySetup} changes and should run again on old demo worlds. */
	public static final int SETUP_VERSION = 1;
	public static final DemoMarker EMPTY = new DemoMarker(0, null, 0);

	/** Whether {@code source} still has to be built: never built, a different source, or an older plan. */
	public boolean needsBuild(DemoStructureSource source) {
		return structure == null || !structure.equals(source.id()) || version != source.version();
	}

	public boolean needsSetup() {
		return setup < SETUP_VERSION;
	}

	public DemoMarker withBuilt(DemoStructureSource source) {
		return new DemoMarker(setup, source.id(), source.version());
	}

	public DemoMarker withSetup() {
		return new DemoMarker(SETUP_VERSION, structure, version);
	}

	public String toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("setup", setup);
		if (structure != null) {
			o.addProperty("structure", structure);
		}
		o.addProperty("version", version);
		return o.toString();
	}

	/** Lenient: anything unreadable counts as "nothing done yet" (worst case the HQ is re-placed, which skips equal blocks). */
	public static DemoMarker parse(@Nullable String json) {
		if (json == null || json.isBlank()) {
			return EMPTY;
		}
		try {
			JsonElement e = JsonParser.parseString(json);
			if (!e.isJsonObject()) {
				return EMPTY;
			}
			JsonObject o = e.getAsJsonObject();
			int setup = intOr(o, "setup", 0);
			String structure = o.has("structure") && o.get("structure").isJsonPrimitive() ? o.get("structure").getAsString() : null;
			return new DemoMarker(setup, structure, intOr(o, "version", 0));
		} catch (RuntimeException ex) {
			return EMPTY;
		}
	}

	private static int intOr(JsonObject o, String key, int def) {
		try {
			return o.has(key) ? o.get(key).getAsInt() : def;
		} catch (RuntimeException ex) {
			return def;
		}
	}
}
