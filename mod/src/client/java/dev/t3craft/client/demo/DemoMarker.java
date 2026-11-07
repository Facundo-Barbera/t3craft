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
 * @param hash      {@link DemoStructureSource#revision()} of what was built (e.g. the HQ layout hash), or null
 * @param state     opaque data of the source to compute the next diff (e.g. the HQ layout), or null
 */
public record DemoMarker(int setup, @Nullable String structure, int version, @Nullable String hash, @Nullable JsonObject state) {
	public static final String FILE_NAME = "t3craft-demo.json";
	/** Bump when {@link VanillaDemoWorld#applySetup} changes and should run again on old demo worlds. */
	public static final int SETUP_VERSION = 1;
	public static final DemoMarker EMPTY = new DemoMarker(0, null, 0);

	public DemoMarker(int setup, @Nullable String structure, int version) {
		this(setup, structure, version, null, null);
	}

	/** Whether this marker records a complete build of {@code source} with its current version. */
	public boolean builtWith(DemoStructureSource source) {
		return structure != null && structure.equals(source.id()) && version == source.version();
	}

	/**
	 * Whether {@code source} still has to be built: never built, a different source, an older plan, or
	 * (for sources with a {@link DemoStructureSource#revision()}) different content.
	 */
	public boolean needsBuild(DemoStructureSource source) {
		if (!builtWith(source)) {
			return true;
		}
		String rev = source.revision();
		return rev != null && !rev.equals(hash);
	}

	public boolean needsSetup() {
		return setup < SETUP_VERSION;
	}

	public DemoMarker withBuilt(DemoStructureSource source) {
		return new DemoMarker(setup, source.id(), source.version(), source.revision(), source.state());
	}

	public DemoMarker withSetup() {
		return new DemoMarker(SETUP_VERSION, structure, version, hash, state);
	}

	public String toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("setup", setup);
		if (structure != null) {
			o.addProperty("structure", structure);
		}
		o.addProperty("version", version);
		if (hash != null) {
			o.addProperty("hash", hash);
		}
		if (state != null) {
			o.add("state", state);
		}
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
			String hash = o.has("hash") && o.get("hash").isJsonPrimitive() ? o.get("hash").getAsString() : null;
			JsonObject state = o.has("state") && o.get("state").isJsonObject() ? o.getAsJsonObject("state") : null;
			return new DemoMarker(setup, structure, intOr(o, "version", 0), hash, state);
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
