package dev.t3craft.client.t3ui.actions;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Provider catalogs per environment, fetched read-only with {@code server.getConfig}
 * ({@code T3Connection#getServerConfig}) and cached for {@link #TTL_MS}, plus the model pick that
 * new threads of each environment start with ({@link #defaultPick}; in memory, per session: the
 * picker's last choice, like T3 Code's sticky composer selection). Thread-safe. The fetcher returns
 * null when the environment is not connected; a failed or offline fetch leaves the last good
 * catalog and is retried on the next {@link #catalog} call after {@link #RETRY_MS}.
 */
public final class ProviderService {
	public static final long TTL_MS = 60_000;
	public static final long RETRY_MS = 5_000;

	public enum Phase {
		/** Never fetched yet, or fetching for the first time. */
		LOADING,
		READY,
		/** Last fetch failed or the environment is offline; {@link Entry#catalog} is the last good one (maybe empty). */
		FAILED
	}

	public record Entry(Phase phase, ProviderCatalog catalog, @Nullable String error) {
	}

	private static final class Slot {
		ProviderCatalog catalog = ProviderCatalog.EMPTY;
		Phase phase = Phase.LOADING;
		@Nullable String error;
		long fetchedAt = Long.MIN_VALUE / 2;
		@Nullable CompletableFuture<?> inflight;
	}

	private final Function<String, @Nullable CompletableFuture<JsonObject>> fetcher;
	private final LongSupplier clock;
	private final Map<String, Slot> slots = new HashMap<>();
	private final Map<String, ModelPick> defaults = new HashMap<>();

	public ProviderService(Function<String, @Nullable CompletableFuture<JsonObject>> fetcher, LongSupplier clock) {
		this.fetcher = fetcher;
		this.clock = clock;
	}

	/** Current entry for {@code envId}; starts a fetch when stale (non-blocking). */
	public synchronized Entry catalog(String envId) {
		Slot s = slots.computeIfAbsent(envId, k -> new Slot());
		long now = clock.getAsLong();
		long age = now - s.fetchedAt;
		boolean stale = s.phase == Phase.FAILED ? age >= RETRY_MS : age >= TTL_MS;
		if (stale && s.inflight == null) {
			fetch(envId, s, now);
		}
		return new Entry(s.phase, s.catalog, s.error);
	}

	/** Forces a refetch (e.g. after the player opens the picker). */
	public synchronized void invalidate(String envId) {
		Slot s = slots.get(envId);
		if (s != null) {
			s.fetchedAt = Long.MIN_VALUE / 2;
		}
	}

	private void fetch(String envId, Slot s, long now) {
		s.fetchedAt = now;
		CompletableFuture<JsonObject> f;
		try {
			f = fetcher.apply(envId);
		} catch (RuntimeException e) {
			f = CompletableFuture.failedFuture(e);
		}
		if (f == null) {
			s.phase = Phase.FAILED;
			s.error = "not connected";
			return;
		}
		s.inflight = f;
		f.whenComplete((cfg, err) -> {
			synchronized (ProviderService.this) {
				s.inflight = null;
				if (err != null || cfg == null) {
					s.phase = Phase.FAILED;
					Throwable c = err;
					while (c != null && c.getCause() != null) {
						c = c.getCause();
					}
					s.error = c == null ? "no answer" : dev.t3craft.t3.T3Connection.sanitize(c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage());
					return;
				}
				s.catalog = ProviderCatalog.fromServerConfig(cfg);
				s.phase = Phase.READY;
				s.error = null;
			}
		});
	}

	/** What a new thread in {@code envId} starts with: the last pick made for new threads there, or null. */
	public synchronized @Nullable ModelPick defaultPick(String envId) {
		return defaults.get(envId);
	}

	public synchronized void setDefaultPick(String envId, @Nullable ModelPick pick) {
		if (pick == null) {
			defaults.remove(envId);
		} else {
			defaults.put(envId, pick);
		}
	}
}
