package dev.t3craft.client.t3ui.kit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The pages that can open in the main T3 screen's right panel ({@link T3RightPanel}, #39): the piece browser
 * now, a diff, a plan or the queue later. A feature registers a factory under a stable id once (from its
 * client init or a static block); the screen makes a fresh page each time the panel opens, and the id is
 * what {@code config/t3craft.json} remembers ({@code layout.rightPanel}).
 *
 * <p>Registering an id again replaces the factory (a hot-swapped class). The order of registration is the
 * order of {@link #all()}.
 */
public final class RightPanels {
	/** One registered page: its id, a label for menus and tooltips, and the factory. */
	public record Entry(String id, String title, Supplier<? extends T3Panel> factory) {
	}

	private static final Map<String, Entry> PANELS = new LinkedHashMap<>();

	private RightPanels() {
	}

	/** Registers {@code supplier} under {@code id}; the label is the id. */
	public static void register(String id, Supplier<? extends T3Panel> supplier) {
		register(id, id, supplier);
	}

	/** Registers {@code supplier} under {@code id} with a label for menus and tooltips. */
	public static synchronized void register(String id, String title, Supplier<? extends T3Panel> supplier) {
		if (id == null || id.isBlank() || supplier == null) {
			throw new IllegalArgumentException("right panel needs an id and a supplier");
		}
		PANELS.put(id, new Entry(id, title == null || title.isBlank() ? id : title, supplier));
	}

	/** Registers only when nothing has {@code id} yet (built-in defaults that a feature may have replaced). */
	public static synchronized void registerIfAbsent(String id, String title, Supplier<? extends T3Panel> supplier) {
		if (!PANELS.containsKey(id)) {
			register(id, title, supplier);
		}
	}

	public static synchronized @Nullable Entry get(@Nullable String id) {
		return id == null ? null : PANELS.get(id);
	}

	public static synchronized List<Entry> all() {
		return List.copyOf(new ArrayList<>(PANELS.values()));
	}

	/** The first registered id, or null when there is none. */
	public static synchronized @Nullable String first() {
		return PANELS.isEmpty() ? null : PANELS.keySet().iterator().next();
	}

	/** A fresh page for {@code id}, or null when it is unknown or its factory failed. */
	public static @Nullable T3Panel create(@Nullable String id) {
		Entry e = get(id);
		if (e == null) {
			return null;
		}
		try {
			return e.factory().get();
		} catch (RuntimeException ex) {
			dev.t3craft.T3Craft.LOGGER.warn("[t3ui] right panel '{}' failed to open: {}", id, ex.toString());
			return null;
		}
	}
}
