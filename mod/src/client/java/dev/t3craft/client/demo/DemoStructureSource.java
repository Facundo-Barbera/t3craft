package dev.t3craft.client.demo;

import dev.t3craft.build.BuildPlan;
import com.google.gson.JsonObject;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What the demo builds at its fixed origin. Today {@link ShedStructureSource} (the builders' demo
 * shed); once the modular HQ lands, {@link HqStructureSource} wraps its plans and wins by priority.
 *
 * <p>All plans share the same origin (the demo origin) and are built in list order, each by its
 * own animated crew, one after another. Positions inside a plan follow the {@link BuildPlan}
 * convention (front to the north, growing toward {@code +z}).
 */
public interface DemoStructureSource {
	/** Stable id stored in the world marker, e.g. {@code "hq"} or {@code "shed"}. */
	String id();

	/**
	 * Plan version. Bump it when the plans change: worlds built with an older version get the new
	 * plan placed over the old one (equal blocks are skipped, so only the differences are built).
	 */
	int version();

	/** Higher wins when the config says {@code "auto"}. */
	int priority();

	/** Whether it can produce plans now (e.g. the HQ generator is installed). */
	default boolean available() {
		return true;
	}

	/** Plans relative to the demo origin (the whole structure). Called on the client thread when the build starts. */
	List<BuildPlan> plans();

	/**
	 * Called right before choosing a source, with what the world already holds and where it is built.
	 * Sources whose content depends on live data (the HQ roster) compute it here. Default: nothing.
	 */
	default void prepare(DemoMarker built, DemoWorldSpec spec, int worldMinY) {
	}

	/**
	 * Content hash of what {@link #plans()} builds now, stored in the marker; when non-null a different
	 * value means "build again" even with the same id and version. Default: null (id + version only).
	 */
	default @Nullable String revision() {
		return null;
	}

	/** Opaque data stored in the marker with a complete build, handed back through {@link #prepare}. */
	default @Nullable JsonObject state() {
		return null;
	}

	/** What to build on a world that holds {@code built}: by default everything ({@link #plans()}). */
	default List<BuildPlan> plansSince(DemoMarker built) {
		return plans();
	}
}
