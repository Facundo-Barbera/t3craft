package dev.t3craft.build;

/** Lifecycle of a {@link BuildJob}. {@link #COMPLETED} and {@link #CANCELLED} are terminal. */
public enum JobState {
	/** Submitted, no block placed yet. Becomes {@link #RUNNING} on its first tick. */
	PENDING,
	RUNNING,
	PAUSED,
	COMPLETED,
	CANCELLED;

	public boolean isTerminal() {
		return this == COMPLETED || this == CANCELLED;
	}
}
