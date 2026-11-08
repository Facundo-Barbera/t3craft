package dev.t3craft.build;

/**
 * Scheduling core of a {@link BuildJob}, pluggable: {@link FrontCursor} (layer bands with a barrier
 * per layer, the original order) or {@link CrewCursor} (the crew plan of {@link CrewPlanner}: own
 * zones, nearest supported block in reach, walking between work spots; targets picked at run time). Both take an allowance per
 * tick from the global {@link BuildBudget} and apply every plan index exactly once.
 */
public interface JobCursor {
	/** Advances by at most {@code allowance} placements; returns the steps applied. Server thread. */
	int tick(double allowance, FrontCursor.Step step);

	boolean pause();

	boolean resume();

	boolean cancel();

	/** Paused because every front with work left was blocked (materials), not by {@link #pause()}. */
	boolean blocked();

	boolean frontBlocked(int f);

	/** Current material segment (checked as a whole when it starts). */
	int segment();

	/** Plan indices of the current segment not applied yet, sorted. Server thread. */
	int[] pendingInSegment();

	int frontCount();

	int frontOf(int index);

	JobState state();

	int total();

	int done();

	int placed();

	int skipped();

	int failed();

	double progress();
}
