package dev.t3craft.client.blocks;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.t3craft.blocks.ThreadBoardBlockEntity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * The thread wall's settings: {@code config/t3craft.json} →
 * <pre>{@code "blocks": { "threadWall": { "width": 5, "height": 3, "columns": "status",
 *                                  "detailDistance": 24, "viewDistance": 64 } }}</pre>
 * Every key is optional and additive (missing or wrong type → default). {@code width}/{@code height}
 * are the wall size in blocks for anchors without their own size (the HQ plan writes its own);
 * {@code columns}: {@code status | project | environment}; text and hover are drawn up to
 * {@code detailDistance} blocks, coloured cards only up to {@code viewDistance}. Pure apart from
 * {@link #load}.
 */
public record ThreadWallConfig(int width, int height, WallMode mode, int detailDistance, int viewDistance) {
	public static final ThreadWallConfig DEFAULT = new ThreadWallConfig(5, 3, WallMode.STATUS, 24, 64);
	public static final String KEY = "threadWall";

	public ThreadWallConfig {
		width = clamp(width, 1, ThreadBoardBlockEntity.MAX_WIDTH);
		height = clamp(height, 1, ThreadBoardBlockEntity.MAX_HEIGHT);
		mode = mode == null ? WallMode.STATUS : mode;
		viewDistance = clamp(viewDistance, 8, 128);
		detailDistance = clamp(detailDistance, 4, viewDistance);
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	/** The effective size and mode of one anchor: its own data wins, the config fills the rest. */
	public record Wall(int width, int height, WallMode mode) {
	}

	public Wall of(int ownWidth, int ownHeight, @Nullable String ownColumns) {
		return new Wall(ownWidth > 0 ? Math.min(ownWidth, ThreadBoardBlockEntity.MAX_WIDTH) : width,
				ownHeight > 0 ? Math.min(ownHeight, ThreadBoardBlockEntity.MAX_HEIGHT) : height,
				WallMode.parse(ownColumns, mode));
	}

	/** From the whole config object (null = defaults). */
	public static ThreadWallConfig resolve(@Nullable JsonObject root) {
		if (root == null || !(root.get("blocks") instanceof JsonObject b) || !(b.get(KEY) instanceof JsonObject o)) {
			return DEFAULT;
		}
		return new ThreadWallConfig(
				integer(o, "width", DEFAULT.width),
				integer(o, "height", DEFAULT.height),
				WallMode.parse(o.get("columns") instanceof JsonElement e && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()
						? e.getAsString() : null, DEFAULT.mode),
				integer(o, "detailDistance", DEFAULT.detailDistance),
				integer(o, "viewDistance", DEFAULT.viewDistance));
	}

	private static int integer(JsonObject o, String key, int fallback) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : fallback;
	}

	/** Reads {@code configFile} (missing or broken → defaults). Never throws. */
	public static ThreadWallConfig load(Path configFile) {
		try {
			if (Files.exists(configFile)) {
				return resolve(new Gson().fromJson(Files.readString(configFile, StandardCharsets.UTF_8), JsonObject.class));
			}
		} catch (IOException | JsonParseException | IllegalStateException | ClassCastException e) {
			// broken file: defaults
		}
		return DEFAULT;
	}
}
