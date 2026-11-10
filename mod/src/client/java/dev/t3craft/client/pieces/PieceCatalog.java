package dev.t3craft.client.pieces;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

/**
 * The catalog the piece browser shows: the entries of every registered {@link PieceCatalogSource}, merged by
 * id (a later source replaces an earlier piece of the same id).
 *
 * <p><b>The hook for the real catalog:</b> the in-world catalog (feat/piece-catalog-preview) calls
 * {@code PieceCatalog.register(source)} from its client initializer. Nothing else changes: the browser reads
 * {@link #all()} every time it is opened or refreshed. Until then the only source is {@link CodePieceSource}.
 */
public final class PieceCatalog {
	private static final List<PieceCatalogSource> SOURCES = new CopyOnWriteArrayList<>(List.of(new CodePieceSource()));
	private static volatile @Nullable List<PieceEntry> merged;

	private PieceCatalog() {
	}

	/** Adds a source after the existing ones (so it wins on equal ids). Registering the same instance twice is a no-op. */
	public static void register(PieceCatalogSource source) {
		if (!SOURCES.contains(source)) {
			SOURCES.add(source);
		}
		merged = null;
	}

	public static void unregister(PieceCatalogSource source) {
		SOURCES.remove(source);
		merged = null;
	}

	/** Forget the merged list: the next {@link #all()} asks every source again. */
	public static void refresh() {
		merged = null;
	}

	/** Every piece, folder first and then name. */
	public static List<PieceEntry> all() {
		List<PieceEntry> m = merged;
		if (m == null) {
			Map<String, PieceEntry> byId = new LinkedHashMap<>();
			for (PieceCatalogSource s : SOURCES) {
				try {
					for (PieceEntry e : s.entries()) {
						byId.put(e.id(), e);
					}
				} catch (RuntimeException | LinkageError e) {
					dev.t3craft.T3Craft.LOGGER.warn("piece browser: source '{}' failed", s.label(), e);
				}
			}
			List<PieceEntry> list = new ArrayList<>(byId.values());
			list.sort(Comparator.comparing(PieceEntry::folder, String.CASE_INSENSITIVE_ORDER).thenComparing(PieceEntry::name, String.CASE_INSENSITIVE_ORDER));
			m = List.copyOf(list);
			merged = m;
		}
		return m;
	}

	public static @Nullable PieceEntry byId(@Nullable String id) {
		if (id == null) {
			return null;
		}
		for (PieceEntry e : all()) {
			if (e.id().equals(id)) {
				return e;
			}
		}
		return null;
	}

	/** "68 pieces: code catalog" for the footer. */
	public static String summary() {
		StringBuilder sb = new StringBuilder();
		for (PieceCatalogSource s : SOURCES) {
			if (!sb.isEmpty()) {
				sb.append(", ");
			}
			sb.append(s.label());
		}
		return all().size() + " pieces from: " + sb;
	}
}
