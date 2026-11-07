package dev.t3craft.debug;

/**
 * Something that keeps in-memory state which must be dropped when classes are redefined (hot-swap)
 * or when a developer asks for a clean slate. Register it with {@link DevReload#register}.
 *
 * <p>Contract for {@link #reset}: drop caches, maps and spawned client-only entities so that the
 * next tick rebuilds them from the source of truth (config, T3 snapshot, bridge). It must be
 * idempotent, must not block, and runs on the thread that triggered the reset (the client thread
 * for automatic resets). It must not throw; if it does, {@link DevReload} logs it and carries on
 * with the other resettables.
 *
 * <p>Keep this interface stable: it is the one type shared across hot-swaps, so changing its shape
 * would itself break a running dev session.
 */
@FunctionalInterface
public interface Resettable {
	void reset(ResetReason reason);
}
