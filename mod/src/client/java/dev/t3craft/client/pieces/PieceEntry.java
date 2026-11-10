package dev.t3craft.client.pieces;

import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * One piece of the browser: what the list shows and what a preview request names. This is the whole contract
 * with a catalog ({@link PieceCatalogSource}): a stable {@code id}, a display {@code name}, a {@code folder}
 * ({@code "House"} or a path like {@code "House/Japanese"}) and free {@code tags}. The size is optional: a
 * catalog that has no size range simply leaves it out and the browser hides the size controls it cannot fill.
 *
 * @param id     stable key, lower case ({@code house_timber_cottage}); what the preview command receives
 * @param name   display name ({@code Timber cottage})
 * @param folder slash-separated path, never empty; {@code "Other"} when the catalog has no folders
 * @param tags   lower-case words the filter box and the tag filter match
 * @param size   the plot sizes the piece builds well in, or null when unknown
 */
public record PieceEntry(String id, String name, String folder, List<String> tags, PieceEntry.@Nullable Dims size) {
	/**
	 * The size range of a piece, in blocks: frontage {@code minW..maxW}, depth {@code minD..maxD} and the
	 * height it needs ({@code minH}; extra height may stay empty).
	 */
	public record Dims(int minW, int maxW, int minD, int maxD, int minH) {
		/** Small / medium / large by the bigger footprint side the piece can reach. */
		public String sizeClass() {
			int side = Math.max(maxW, maxD);
			return side <= 6 ? "small" : side <= 12 ? "medium" : "large";
		}

		/** A footprint that sorts pieces by how big they get. */
		public int area() {
			return maxW * maxD;
		}

		public String text() {
			return range(minW, maxW) + " x " + range(minD, maxD) + ", h " + minH + "+";
		}

		private static String range(int a, int b) {
			return a == b ? Integer.toString(a) : a + "-" + b;
		}
	}

	public PieceEntry {
		folder = cleanFolder(folder);
		tags = List.copyOf(tags);
	}

	private static String cleanFolder(String f) {
		String s = f == null ? "" : f.strip().replace('\\', '/');
		while (s.startsWith("/")) {
			s = s.substring(1);
		}
		while (s.endsWith("/")) {
			s = s.substring(0, s.length() - 1);
		}
		return s.isEmpty() ? "Other" : s;
	}

	/** The piece is in {@code path} or in one of its sub-folders. */
	public boolean inFolder(String path) {
		return folder.equals(path) || folder.startsWith(path + "/");
	}

	/** Small / medium / large, or null when the size is unknown. */
	public @Nullable String sizeClass() {
		return size == null ? null : size.sizeClass();
	}

	/** Every word the free-text filter looks in, lower case. */
	public String haystack() {
		return (id + " " + name + " " + folder + " " + String.join(" ", tags)).toLowerCase(Locale.ROOT);
	}
}
