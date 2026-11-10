package dev.t3craft.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.build.BuildBudget;
import dev.t3craft.build.BuildTuning;
import dev.t3craft.survival.Survival;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The "Build order" row of Settings (docs/features/builder-sites.md): {@code build.order} of {@code config/t3craft.json}
 * ({@link BuildTuning#ORDER_SITES}, {@link BuildTuning#ORDER_RADIAL}, {@link BuildTuning#ORDER_PARTS}). Like
 * {@link WorldTextSettings} it edits one key of a block owned outside {@link T3CraftSettings}: {@link #save} writes
 * only {@code order} and keeps every other key of the {@code build} block, and does nothing when the value is what the
 * file already holds. After a write it re-reads the {@code build} section into the live budget, the same call as
 * {@code /t3craft debug build-tuning reload} ({@link BuildBudget#reload}); the order is applied by the next build that starts.
 *
 * <p>#53: the same rows write {@code build.sitesAtOnce} ("Sites at once") and {@code build.speed} ("Build speed"), each only
 * when it changed; both are read by a running {@code "sites"} build on its next tick.
 */
public final class BuildOrderSettings {
	/** Options in the order the select shows them. */
	public static final List<String> ORDERS = List.of(BuildTuning.ORDER_SITES, BuildTuning.ORDER_RADIAL, BuildTuning.ORDER_PARTS);
	/** #53: "Sites at once" options: 1 to {@link BuildTuning#SITES_AT_ONCE_MAX}, then 0 (off: the #48 rules). */
	public static final List<Integer> SITES_AT_ONCE = List.of(1, 2, 3, 4, 5, 6, 0);
	/** #53: "Build speed" options. */
	public static final List<String> SPEEDS = BuildTuning.SPEEDS;

	private BuildOrderSettings() {
	}

	/** The order in {@code config/t3craft.json} right now (the default when the key is missing or bad). */
	public static String current() {
		return onDiskTuning().orderName();
	}

	/** #53: {@code build.sitesAtOnce} in the file right now (the default when missing or bad). */
	public static int currentSitesAtOnce() {
		return onDiskTuning().sitesAtOnce();
	}

	/** #53: {@code build.speed} in the file right now (the default when missing or bad). */
	public static String currentSpeed() {
		return onDiskTuning().speed();
	}

	private static BuildTuning onDiskTuning() {
		return BuildTuning.load(ConfigManager.get().file()).tuning();
	}

	/**
	 * Writes {@code order} into {@code build.order} unless the file already holds it, then reloads the build tuning.
	 *
	 * @return the result of the write; ok with a "nothing to save" message when the value did not change
	 */
	public static ConfigManager.SaveResult save(String order) {
		return save(order, currentSitesAtOnce(), currentSpeed());
	}

	/**
	 * #53: writes {@code build.order}, {@code build.sitesAtOnce} and {@code build.speed}, each only when it differs from
	 * the file (a key at its default that the file does not hold is not added), then reloads the build tuning once.
	 *
	 * @return the result of the write; ok with a "nothing to save" message when no value changed
	 */
	public static ConfigManager.SaveResult save(String order, int sitesAtOnce, String speed) {
		if (!ORDERS.contains(order) || !SITES_AT_ONCE.contains(sitesAtOnce) || !SPEEDS.contains(speed)) {
			return new ConfigManager.SaveResult(false, "Unknown build setting");
		}
		BuildTuning now = onDiskTuning();
		boolean orderChanged = !order.equals(now.orderName());
		boolean parallelChanged = sitesAtOnce != now.sitesAtOnce();
		boolean speedChanged = !speed.equals(now.speed());
		if (!orderChanged && !parallelChanged && !speedChanged) {
			return new ConfigManager.SaveResult(true, "Build settings unchanged");
		}
		JsonElement base = onDisk();
		JsonObject build = base != null && base.isJsonObject() ? base.getAsJsonObject().deepCopy() : new JsonObject();
		if (orderChanged) {
			build.addProperty("order", order);
		}
		if (parallelChanged) {
			build.addProperty("sitesAtOnce", sitesAtOnce);
		}
		if (speedChanged) {
			build.addProperty("speed", speed);
		}
		ConfigManager.SaveResult r = ConfigManager.get().putExtra(BuildTuning.SECTION, build);
		if (r.ok()) {
			BuildBudget.reload(Survival.configFile());
		}
		return r;
	}

	/**
	 * The {@code build} block as the file holds it now, so a key edited by hand since the last load survives the save;
	 * else the block as last loaded; else null.
	 */
	private static @Nullable JsonElement onDisk() {
		Path f = ConfigManager.get().file();
		try {
			if (Files.isRegularFile(f)) {
				JsonElement root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
				if (root.isJsonObject() && root.getAsJsonObject().get(BuildTuning.SECTION) != null) {
					return root.getAsJsonObject().get(BuildTuning.SECTION);
				}
			}
		} catch (IOException | RuntimeException e) {
			// unreadable right now: fall back to what was loaded
		}
		return ConfigManager.get().extra(BuildTuning.SECTION);
	}
}
