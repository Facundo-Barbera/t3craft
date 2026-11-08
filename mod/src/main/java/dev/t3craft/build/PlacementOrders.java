package dev.t3craft.build;

/**
 * The {@link PlacementOrder} every new {@link BuildJob} uses (fix/hq-build-parallel). Default
 * {@link LayeredOrder#FACTORY}; another strategy (e.g. builders by proximity) installs itself here
 * once at start-up. Read on the server thread when a job is created.
 */
public final class PlacementOrders {
	private static volatile PlacementOrder.Factory current = LayeredOrder.FACTORY;

	private PlacementOrders() {
	}

	public static PlacementOrder.Factory current() {
		return current;
	}

	/** Null restores {@link LayeredOrder#FACTORY}. */
	public static void use(PlacementOrder.Factory f) {
		current = f == null ? LayeredOrder.FACTORY : f;
	}
}
