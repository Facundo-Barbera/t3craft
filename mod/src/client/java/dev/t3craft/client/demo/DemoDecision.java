package dev.t3craft.client.demo;

/**
 * What {@code /t3craft demo} or the demo button should do, decided from plain facts (unit tested).
 */
public final class DemoDecision {
	public enum Action {
		/** No save folder yet: create the superflat world. */
		CREATE,
		/** The save exists: open it as is (never recreate, never overwrite). */
		OPEN,
		/** Already playing in the demo world: nothing to load (maybe build what is missing). */
		ALREADY_HERE,
		/** In another world: ask first, then save and leave it, then {@link #CREATE} or {@link #OPEN}. */
		CONFIRM_LEAVE
	}

	private DemoDecision() {
	}

	/**
	 * @param inWorld       playing a world or connected to a server right now
	 * @param inDemoWorld   that world is the demo world (integrated server on the demo folder)
	 * @param demoExists    the demo save folder exists
	 * @param confirmed     the user already confirmed leaving the current world
	 */
	public static Action decide(boolean inWorld, boolean inDemoWorld, boolean demoExists, boolean confirmed) {
		if (inWorld && inDemoWorld) {
			return Action.ALREADY_HERE;
		}
		if (inWorld && !confirmed) {
			return Action.CONFIRM_LEAVE;
		}
		return demoExists ? Action.OPEN : Action.CREATE;
	}
}
