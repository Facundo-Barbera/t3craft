package dev.t3craft.client.demo;

import dev.t3craft.build.BuildPlan;
import java.util.List;

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

	/** Plans relative to the demo origin. Called on the client thread when the build starts. */
	List<BuildPlan> plans();
}
