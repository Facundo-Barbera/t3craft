package dev.t3craft.client.chat;

/** A screen rectangle in GUI pixels: where a chat component draws. Pure Java. */
public record Rect(int x, int y, int w, int h) {
	public static final Rect EMPTY = new Rect(0, 0, 0, 0);

	public Rect {
		w = Math.max(0, w);
		h = Math.max(0, h);
	}

	public int right() {
		return x + w;
	}

	public int bottom() {
		return y + h;
	}

	public boolean contains(double mx, double my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	public Rect inset(int d) {
		return new Rect(x + d, y + d, w - 2 * d, h - 2 * d);
	}

	/** Left part {@code leftW} wide and the rest after a {@code gap}. */
	public Rect[] splitX(int leftW, int gap) {
		int l = Math.max(0, Math.min(leftW, w));
		return new Rect[] {new Rect(x, y, l, h), new Rect(x + l + gap, y, w - l - gap, h)};
	}
}
