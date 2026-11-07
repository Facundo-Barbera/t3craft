package dev.t3craft.debug;

/** Why {@link DevReload} is resetting registered state. */
public enum ResetReason {
	/** A watched class changed shape (fields, methods, nested types): hot-swap / class redefinition. */
	REDEFINITION,
	/** A guarded module threw an error typical of stale state after a hot-swap (ClassCastException, LinkageError). */
	RECOVERY,
	/** Someone asked for it ({@code /t3dev reset}, a test, a module). */
	MANUAL
}
