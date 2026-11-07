// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
// Same model as AgentCraft's ui/TextUtil.Scroll (follow the bottom until the player scrolls up),
// in pixels instead of rows because feed rows have different heights (text 10, tool lines 13).
package dev.t3craft.client.chat;

/**
 * Scroll state of a feed: content and view height in pixels, offset from the top, and whether it
 * follows the newest line (auto-scroll). Scrolling up stops following; reaching the bottom again
 * (wheel, PgDn, End) resumes it. Pure Java (tested).
 */
public final class FeedScroll {
	private int content;
	private int view = 1;
	private int offset;
	private boolean follow = true;

	public int content() {
		return content;
	}

	public int view() {
		return view;
	}

	public int offset() {
		return offset;
	}

	public boolean following() {
		return follow;
	}

	public boolean scrollable() {
		return content > view;
	}

	public int max() {
		return Math.max(0, content - view);
	}

	/** 0 = top, 1 = bottom. */
	public double fraction() {
		return max() == 0 ? 0 : (double) offset / max();
	}

	public boolean atTop() {
		return offset <= 0;
	}

	/** New sizes (call each frame or when the layout changes). Following keeps the bottom in view. */
	public FeedScroll update(int contentPx, int viewPx) {
		content = Math.max(0, contentPx);
		view = Math.max(1, viewPx);
		offset = follow ? max() : Math.max(0, Math.min(offset, max()));
		return this;
	}

	/**
	 * Content grew above the view (an older page was prepended): keep what the player was looking at
	 * in place.
	 */
	public void prepended(int addedPx) {
		if (!follow && addedPx > 0) {
			offset = Math.min(max(), offset + addedPx);
		}
	}

	public void scrollBy(int px) {
		offset = Math.max(0, Math.min(offset + px, max()));
		follow = offset >= max();
	}

	public void toTop() {
		offset = 0;
		follow = max() == 0;
	}

	public void toBottom() {
		offset = max();
		follow = true;
	}

	/** Stop following without moving (e.g. the player unfolded something above). */
	public void hold() {
		follow = offset >= max() && max() == 0;
	}
}
