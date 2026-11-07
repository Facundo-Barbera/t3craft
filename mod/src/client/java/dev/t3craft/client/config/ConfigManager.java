package dev.t3craft.client.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * Current {@link T3CraftSettings} plus load/save and change listeners. This is the read side other
 * features use (integration points in {@code docs/feat-config.md}):
 * <pre>{@code
 * T3CraftSettings s = ConfigManager.get().settings();
 * if (s.notifications().allowSound("attention")) { ... }
 * ConfigManager.get().addListener(newSettings -> ...);   // runs on the thread that saved
 * }</pre>
 *
 * <p>Hot-swap safety: the only static is {@link #instance}, always of this exact type, and
 * {@link #reset()} drops it together with every listener (call it before re-initialising after a
 * class reload; {@code ConfigClientInit} does on start). Settings are immutable snapshots.
 */
public final class ConfigManager {
	private static volatile @Nullable ConfigManager instance;

	private final ConfigStore store;
	private final Consumer<String> warn;
	private final List<Consumer<T3CraftSettings>> listeners = new CopyOnWriteArrayList<>();
	private volatile T3CraftSettings settings = T3CraftSettings.defaults();
	private volatile List<String> warnings = List.of();
	private volatile boolean readOnly;

	public ConfigManager(Path file, Consumer<String> warn) {
		this.store = new ConfigStore(file);
		this.warn = warn;
	}

	/** The shared instance; created (and loaded) on first use from {@code <config>/t3craft.json}. */
	public static ConfigManager get() {
		ConfigManager m = instance;
		if (m == null) {
			synchronized (ConfigManager.class) {
				m = instance;
				if (m == null) {
					m = new ConfigManager(defaultFile(), msg -> dev.t3craft.T3Craft.LOGGER.warn("[config] {}", msg));
					m.load();
					instance = m;
				}
			}
		}
		return m;
	}

	/** Drops the shared instance and its listeners (hot-swap / tests). The next {@link #get()} reloads. */
	public static void reset() {
		synchronized (ConfigManager.class) {
			ConfigManager m = instance;
			if (m != null) {
				m.listeners.clear();
			}
			instance = null;
		}
	}

	private static Path defaultFile() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("t3craft.json");
	}

	public Path file() {
		return store.file();
	}

	public T3CraftSettings settings() {
		return settings;
	}

	/** Validation messages from the last load (key names only, never values). */
	public List<String> warnings() {
		return warnings;
	}

	/** True when the file was written by a newer mod version and will not be overwritten. */
	public boolean readOnly() {
		return readOnly;
	}

	/** Re-reads the file (also used by the settings screen's "Reload from disk"). */
	public synchronized T3CraftSettings load() {
		ConfigStore.Loaded l = store.load();
		settings = l.settings();
		warnings = List.copyOf(l.warnings());
		readOnly = l.readOnly();
		warnings.forEach(warn);
		fire();
		return settings;
	}

	public record SaveResult(boolean ok, String message) {
	}

	/** Applies {@code change}, saves, notifies listeners. The in-memory value changes even if the write fails. */
	public synchronized SaveResult update(UnaryOperator<T3CraftSettings> change) {
		settings = change.apply(settings);
		SaveResult r;
		try {
			r = store.save(settings)
					? new SaveResult(true, "Saved " + store.file().getFileName())
					: new SaveResult(false, store.file().getFileName() + " was written by a newer T3Craft: not overwritten");
		} catch (IOException e) {
			r = new SaveResult(false, "Could not save " + store.file().getFileName() + ": " + e.getClass().getSimpleName());
			warn.accept(r.message());
		}
		fire();
		return r;
	}

	public void addListener(Consumer<T3CraftSettings> l) {
		listeners.add(l);
	}

	public void removeListener(Consumer<T3CraftSettings> l) {
		listeners.remove(l);
	}

	private void fire() {
		T3CraftSettings s = settings;
		for (Consumer<T3CraftSettings> l : listeners) {
			try {
				l.accept(s);
			} catch (RuntimeException e) {
				warn.accept("config listener failed: " + e.getClass().getSimpleName());
			}
		}
	}
}
