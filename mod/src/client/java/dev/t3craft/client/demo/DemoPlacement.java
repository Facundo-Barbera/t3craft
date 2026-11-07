package dev.t3craft.client.demo;

/** Where things go around the demo origin (pure, unit tested). */
public final class DemoPlacement {
	/** Blocks in front of the plan front (toward {@code -z}, where the plan's door faces). */
	public static final int VIEW_DISTANCE = 10;

	private DemoPlacement() {
	}

	/** Spawn / first-teleport spot: in front of the site, on the same level as the origin. */
	public static int[] viewpoint(DemoWorldSpec spec) {
		return new int[] {spec.originX(), spec.originY(), spec.originZ() - VIEW_DISTANCE};
	}
}
