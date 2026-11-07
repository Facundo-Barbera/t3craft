package dev.t3craft.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs per-tick (or per-frame) work of a named module so that an exception never reaches
 * Minecraft's tick loop and crashes the game.
 *
 * <ul>
 *   <li>Every failure is logged: the first one of a streak with its stack trace, the rest at most
 *   once every few seconds.</li>
 *   <li>An error that smells like stale state after a hot-swap ({@link ClassCastException},
 *   {@link LinkageError} and subclasses, anywhere in the cause chain) triggers one
 *   {@link DevReload#resetAll} with {@link ResetReason#RECOVERY} per failure streak.</li>
 *   <li>After {@code maxFailures} consecutive failures the module is disabled: its body stops being
 *   called until {@link #enable}, {@code /t3dev enable <module>}, or a manual / redefinition reset.</li>
 * </ul>
 *
 * <pre>{@code
 * ClientTickEvents.END_CLIENT_TICK.register(mc -> ModuleGuard.get().run("npcs", () -> npcs.tick(mc)));
 * // or, from client code: GuardedEvents.endClientTick("npcs", npcs::tick)
 * }</pre>
 */
public final class ModuleGuard {
	private static final Logger LOG = LoggerFactory.getLogger("t3craft/guard");
	public static final int DEFAULT_MAX_FAILURES = 3;
	private static final long LOG_EVERY_NANOS = 5_000_000_000L;
	private static final ModuleGuard INSTANCE = new ModuleGuard(DevReload.get(), DEFAULT_MAX_FAILURES, System::nanoTime);

	/** Snapshot of one module for status output. */
	public record Status(String module, boolean enabled, int consecutiveFailures, long totalFailures, @Nullable String lastError) {
	}

	private static final class Module {
		volatile boolean enabled = true;
		int consecutive;
		long total;
		boolean recoveredThisStreak;
		long lastLogNanos;
		@Nullable String lastError;
	}

	private final Map<String, Module> modules = new ConcurrentHashMap<>();
	private final DevReload reload;
	private final int maxFailures;
	private final LongSupplier clock;

	public ModuleGuard(DevReload reload, int maxFailures, LongSupplier clock) {
		this.reload = reload;
		this.maxFailures = Math.max(1, maxFailures);
		this.clock = clock;
		// A deliberate reset (manual or after a redefinition) gives disabled modules a new chance.
		// A RECOVERY reset does not, or a module failing on every tick would loop forever.
		reload.register("t3craft.module-guard", reason -> {
			if (reason != ResetReason.RECOVERY) {
				enableAll();
			}
		});
	}

	public static ModuleGuard get() {
		return INSTANCE;
	}

	/** Runs {@code body} unless {@code module} is disabled. Returns true iff it ran and completed. */
	public boolean run(String module, Runnable body) {
		Module m = modules.computeIfAbsent(module, k -> new Module());
		if (!m.enabled) {
			return false;
		}
		try {
			body.run();
			synchronized (m) {
				m.consecutive = 0;
				m.recoveredThisStreak = false;
			}
			return true;
		} catch (OutOfMemoryError oom) {
			throw oom;
		} catch (Throwable t) {
			fail(module, m, t);
			return false;
		}
	}

	/** Like {@link #run} for work that returns a value; {@code fallback} when disabled or failing. */
	public <T> T call(String module, Supplier<T> body, T fallback) {
		Object[] out = {fallback};
		run(module, () -> out[0] = body.get());
		@SuppressWarnings("unchecked")
		T result = (T) out[0];
		return result;
	}

	private void fail(String module, Module m, Throwable t) {
		boolean recover;
		boolean disable;
		boolean logFull;
		boolean logLine;
		int consecutive;
		synchronized (m) {
			m.consecutive++;
			m.total++;
			m.lastError = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
			consecutive = m.consecutive;
			long now = clock.getAsLong();
			logFull = consecutive == 1;
			logLine = !logFull && now - m.lastLogNanos >= LOG_EVERY_NANOS;
			if (logFull || logLine) {
				m.lastLogNanos = now;
			}
			recover = !m.recoveredThisStreak && looksStale(t);
			if (recover) {
				m.recoveredThisStreak = true;
			}
			disable = consecutive >= maxFailures;
			if (disable) {
				m.enabled = false;
			}
		}
		if (logFull) {
			LOG.error("module '{}' failed; the game keeps running", module, t);
		} else if (logLine) {
			LOG.warn("module '{}' failed again ({} in a row): {}", module, consecutive, m.lastError);
		}
		if (recover) {
			LOG.warn("module '{}' error looks like stale state after a hot-swap; resetting registered state", module);
			reload.resetAll(ResetReason.RECOVERY);
		}
		if (disable) {
			LOG.error("module '{}' disabled after {} consecutive failures (last: {}); re-enable with /t3dev enable {}",
					module, consecutive, m.lastError, module);
		}
	}

	static boolean looksStale(Throwable t) {
		for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
			if (c instanceof ClassCastException || c instanceof LinkageError) {
				return true;
			}
		}
		return false;
	}

	public boolean isEnabled(String module) {
		Module m = modules.get(module);
		return m == null || m.enabled;
	}

	/** Re-enables a module and clears its failure streak. Returns false if the module is unknown. */
	public boolean enable(String module) {
		Module m = modules.get(module);
		if (m == null) {
			return false;
		}
		synchronized (m) {
			m.enabled = true;
			m.consecutive = 0;
			m.recoveredThisStreak = false;
		}
		return true;
	}

	public void disable(String module) {
		modules.computeIfAbsent(module, k -> new Module()).enabled = false;
	}

	public void enableAll() {
		modules.keySet().forEach(this::enable);
	}

	public List<Status> status() {
		List<Status> out = new ArrayList<>();
		modules.forEach((name, m) -> {
			synchronized (m) {
				out.add(new Status(name, m.enabled, m.consecutive, m.total, m.lastError));
			}
		});
		out.sort((a, b) -> a.module().compareTo(b.module()));
		return out;
	}
}
