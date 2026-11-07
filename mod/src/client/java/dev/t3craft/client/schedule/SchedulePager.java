package dev.t3craft.client.schedule;

import java.util.List;

/**
 * Splits the sorted tasks into pages and rotates through them on a clock, so a board with more
 * tasks than rows still shows them all (the client restarts the rotation on page 0, the soonest
 * runs, whenever the page count changes). Pure; unit-tested.
 */
public final class SchedulePager {
	public record Page<T>(int index, int count, List<T> items) {
		/** "2/3", or "" when there is a single page. */
		public String label() {
			return count <= 1 ? "" : (index + 1) + "/" + count;
		}
	}

	private SchedulePager() {
	}

	public static int pageCount(int items, int pageSize) {
		if (pageSize <= 0) {
			throw new IllegalArgumentException("pageSize must be > 0");
		}
		return Math.max(1, (items + pageSize - 1) / pageSize);
	}

	/** Page {@code index} (clamped into range). */
	public static <T> Page<T> page(List<T> items, int pageSize, int index) {
		int count = pageCount(items.size(), pageSize);
		int i = Math.floorMod(index, count);
		int from = i * pageSize;
		return new Page<>(i, count, List.copyOf(items.subList(Math.min(from, items.size()), Math.min(from + pageSize, items.size()))));
	}

	/**
	 * The page showing at {@code elapsedMs} since rotation started, one page every {@code rotateMs}
	 * ({@code rotateMs <= 0}: always the first page).
	 */
	public static <T> Page<T> rotating(List<T> items, int pageSize, long elapsedMs, long rotateMs) {
		int count = pageCount(items.size(), pageSize);
		int index = rotateMs <= 0 || count == 1 ? 0 : (int) ((Math.max(0, elapsedMs) / rotateMs) % count);
		return page(items, pageSize, index);
	}
}
