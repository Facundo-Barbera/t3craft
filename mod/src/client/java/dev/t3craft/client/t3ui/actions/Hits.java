package dev.t3craft.client.t3ui.actions;

import java.util.ArrayList;
import java.util.List;

/** Click targets recorded while drawing, hit-tested on the next click (topmost = last added). Pure. */
final class Hits {
	record Hit(int x0, int y0, int x1, int y1, Runnable action) {
		boolean contains(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	private final List<Hit> hits = new ArrayList<>();

	void clear() {
		hits.clear();
	}

	void add(int x0, int y0, int x1, int y1, Runnable action) {
		hits.add(new Hit(x0, y0, x1, y1, action));
	}

	/** Runs the topmost target under (x, y); false when there is none. */
	boolean click(double x, double y) {
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (h.contains(x, y)) {
				h.action().run();
				return true;
			}
		}
		return false;
	}

	static boolean inside(double x, double y, int x0, int y0, int x1, int y1) {
		return x >= x0 && x < x1 && y >= y0 && y < y1;
	}
}
