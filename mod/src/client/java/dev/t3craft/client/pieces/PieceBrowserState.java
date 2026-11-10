package dev.t3craft.client.pieces;

import dev.t3craft.client.FavoritesStore;
import org.jspecify.annotations.Nullable;

/**
 * What the piece browser remembers: the filters, the selected piece and the preview parameters live for the
 * whole session (closing and reopening the browser, with J-style speed, lands where you were), and the
 * favorites are kept in {@code config/t3craft/piece-favorites.json}.
 */
public final class PieceBrowserState {
	/** How the plot size is chosen for a preview. */
	public enum SizeChoice {
		SMALLEST("Smallest it builds"), MIDDLE("Middle of its range"), LARGEST("Largest it builds"), CUSTOM("Custom W x D x H"),
		/** A piece with no size range: the preview picks the size. Never stored as the user's choice for a piece that has a range. */
		DEFAULT("Catalog default");

		private final String label;

		SizeChoice(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	/** The order of the piece list. */
	public enum Sort {
		FOLDER("Folder, then name"), NAME("Name"), SMALL_FIRST("Size, small first"), LARGE_FIRST("Size, large first");

		private final String label;

		Sort(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	private static final PieceBrowserState INSTANCE = new PieceBrowserState();

	// ---- filters ---------------------------------------------------------------------------------------
	public String query = "";
	/** A folder path ("House", "House/Japanese"), or null for every folder. */
	public @Nullable String folder;
	/** A tag, or null for any. */
	public @Nullable String tag;
	/** "small" / "medium" / "large", or null for any. */
	public @Nullable String sizeClass;
	public boolean favoritesOnly;
	public Sort sort = Sort.FOLDER;

	// ---- selection and preview parameters --------------------------------------------------------------
	public @Nullable String selectedId;
	public String front = "south";
	public SizeChoice sizeChoice = SizeChoice.MIDDLE;
	public String customW = "9";
	public String customD = "9";
	public String customH = "12";
	public String seedText = "1";
	/** Stay in the browser after asking for a preview (default: close it so the piece is on screen). */
	public boolean keepOpen;

	private final FavoritesStore favorites = new FavoritesStore("piece-favorites.json", "piece browser");

	private PieceBrowserState() {
	}

	public static PieceBrowserState get() {
		return INSTANCE;
	}

	/** Back to no filter at all (the selection and the preview parameters stay). */
	public void clearFilters() {
		query = "";
		folder = null;
		tag = null;
		sizeClass = null;
		favoritesOnly = false;
		sort = Sort.FOLDER;
	}

	public boolean filtering() {
		return !query.isBlank() || folder != null || tag != null || sizeClass != null || favoritesOnly;
	}

	// ---- favorites ---------------------------------------------------------------------------------------

	public boolean isFavorite(String id) {
		return favorites.isFavorite(id);
	}

	public int favoriteCount() {
		return favorites.count();
	}

	/** Flips the favorite flag of {@code id}; returns the new value. */
	public boolean toggleFavorite(String id) {
		return favorites.toggle(id);
	}
}
