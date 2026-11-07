package dev.t3craft.client.demo;

import dev.t3craft.build.BuildPlan;
import dev.t3craft.build.DemoPlans;
import java.util.List;

/**
 * Stand-in until the modular HQ exists: the builders' 7x7 test shed ({@link DemoPlans#shed()}).
 * Lowest priority, so any real HQ source replaces it under {@code "auto"}.
 */
public final class ShedStructureSource implements DemoStructureSource {
	public static final String ID = "shed";
	public static final int VERSION = 1;
	public static final int PRIORITY = 0;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int version() {
		return VERSION;
	}

	@Override
	public int priority() {
		return PRIORITY;
	}

	@Override
	public List<BuildPlan> plans() {
		return List.of(DemoPlans.shed());
	}
}
