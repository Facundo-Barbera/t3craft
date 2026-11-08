package dev.t3craft.build;

/** How a {@link BuildJob} orders its plan among the fronts (see {@link JobCursor}). */
public enum BuildPlanner {
	/** Layer bands with a barrier per layer ({@link Fronts}, {@link FrontCursor}): the original order. */
	LAYERS,
	/**
	 * The crew plan ({@link CrewPlanner}, {@link CrewCursor}): each builder its own zone, nearest
	 * supported block in reach, walking between work spots.
	 */
	CREW;

	/** Entries per material segment of a charged {@link #CREW} job (checked as a whole, like a layer). */
	public static final int CREW_SEGMENT = 256;

	/** Plans up to this many entries are planned when the job is created; bigger ones off-thread. */
	public static final int SYNC_PLAN_LIMIT = 4096;
}
