package dev.t3craft.client.hq.layout;

import com.google.gson.JsonObject;
import dev.t3craft.hq.layout.HqLayouts;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * Which layout the HQ world is built with. Persisted in the HQ world's own save, in its demo marker
 * ({@code <world>/t3craft-demo.json}, {@code state.layoutId}), so copying or deleting the world
 * carries it along. A world built before layouts existed (marker with the HQ but no
 * {@code layoutId}) counts as {@code classic}.
 *
 * <p>Lives in the client source set ({@code dev.t3craft.client.hq.layout}) because the HQ world and
 * its marker are client-side (integrated server of the demo world).
 */
public final class HqLayoutState {
	/** Key inside the marker's {@code state} object. */
	public static final String KEY = "layoutId";

	private HqLayoutState() {
	}

	/**
	 * The layout the current HQ is built with, or null when there is none (not in the HQ world, or the
	 * HQ world holds no completed HQ build yet). Client thread.
	 */
	public static @Nullable String builtLayoutId() {
		return dev.t3craft.client.demo.DemoClient.builtLayoutId();
	}

	/** {@link #builtLayoutId()} of the HQ world saved at {@code worldRoot} (null if nothing built). */
	public static @Nullable String builtLayoutId(Path worldRoot) {
		return dev.t3craft.client.demo.DemoClient.builtLayoutId(worldRoot);
	}

	/**
	 * Pure: the layout id recorded in a marker, given the id of the structure it built and its state.
	 * Null when the marker records no HQ build; {@code classic} for an HQ built before layouts.
	 */
	public static @Nullable String fromMarker(@Nullable String structure, @Nullable JsonObject state) {
		if (!"hq".equals(structure)) {
			return null;
		}
		if (state != null && state.has(KEY) && state.get(KEY).isJsonPrimitive()) {
			return HqLayouts.resolve(state.get(KEY).getAsString()).id();
		}
		return HqLayouts.DEFAULT_ID;
	}
}
