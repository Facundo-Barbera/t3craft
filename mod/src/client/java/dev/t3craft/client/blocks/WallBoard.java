// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.blocks;

import dev.t3craft.client.chat.ChatTheme;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * One wall's model: the cached {@link WallLayout.Layout} and the animated card positions. Pure
 * (the caller passes the clock), one instance per wall anchor, render thread only.
 *
 * <ul>
 *   <li><b>Cache by version.</b> {@link #sync} regroups and lays out only when the cards (compared
 *       by value: the shell's version as the client sees it), the mode or the wall size changed; then
 *       {@link #version()} goes up and the renderer rebuilds its glyph runs. A steady wall costs one
 *       list comparison per frame.</li>
 *   <li><b>Cards fly.</b> A card glides from where it was to its new slot (time constant
 *       {@value #TAU} s). A card that changed column is {@link Anim#flying} (drawn lifted, over the
 *       others) and glows for {@value #GLOW_S} s where it lands. New cards appear in place.</li>
 * </ul>
 *
 * <p>Glide and "fly + glow on column change" adapted from AgentCraft's {@code TaskBoard.step} /
 * {@code Card.arrivedFrom}.
 */
public final class WallBoard {
	static final float TAU = 0.085f;
	static final float GLOW_S = 1.2f;
	/** Under this many px from the target a card snaps and stops flying. */
	static final float SNAP = 0.5f;

	/** A card's drawn rectangle, gliding towards its slot. */
	public static final class Anim {
		public float x, y, w, h;
		float tx, ty, tw, th;
		int column;
		public boolean flying;
		/** Seconds of arrival glow left (after a column change). */
		public float glow;

		Anim(WallLayout.Slot s) {
			x = tx = s.x();
			y = ty = s.y();
			w = tw = s.w();
			h = th = s.h();
			column = s.column();
		}
	}

	private @Nullable List<WallCard> lastCards;
	private @Nullable WallMode lastMode;
	private float lastW = -1;
	private float lastH = -1;
	private WallLayout.@Nullable Layout layout;
	private long version;
	private int rebuilds;
	private final Map<String, Anim> anims = new HashMap<>();
	private @Nullable String hovered;
	private long lastStepNanos;
	public long lastUsedTick;

	/**
	 * Brings the board up to date. Returns true when it rebuilt (new version), false when nothing
	 * changed (the cached layout stays).
	 */
	public boolean sync(List<WallCard> cards, WallMode mode, ChatTheme theme, float width, float height, WallLayout.Measure m) {
		if (layout != null && mode == lastMode && width == lastW && height == lastH && cards.equals(lastCards)) {
			return false;
		}
		int max = mode == WallMode.STATUS ? 4 : WallLayout.maxColumns(width);
		WallLayout.Layout next = WallLayout.build(WallColumns.group(cards, mode, theme, max), mode, width, height, m);
		boolean resized = width != lastW || height != lastH || mode != lastMode;
		Set<String> keep = new HashSet<>();
		for (WallLayout.Slot s : next.cards()) {
			keep.add(s.key());
			Anim a = anims.get(s.key());
			if (a == null || resized) {
				anims.put(s.key(), new Anim(s));
				continue;
			}
			if (a.column != s.column()) {
				a.flying = true;
				a.column = s.column();
			}
			a.tx = s.x();
			a.ty = s.y();
			a.tw = s.w();
			a.th = s.h();
		}
		anims.keySet().retainAll(keep);
		lastCards = List.copyOf(cards);
		lastMode = mode;
		lastW = width;
		lastH = height;
		layout = next;
		version++;
		rebuilds++;
		return true;
	}

	/** Advances the animation to {@code nowNanos}. Returns true while something still moves or glows. */
	public boolean step(long nowNanos) {
		float dt = lastStepNanos == 0 ? 0 : Math.min(0.25f, (nowNanos - lastStepNanos) / 1e9f);
		lastStepNanos = nowNanos;
		return advance(dt);
	}

	/** Advances by {@code dt} seconds (tests). */
	boolean advance(float dt) {
		float k = dt <= 0 ? 0 : 1f - (float) Math.exp(-dt / TAU);
		boolean moving = false;
		for (Anim a : anims.values()) {
			a.x += (a.tx - a.x) * k;
			a.y += (a.ty - a.y) * k;
			a.w += (a.tw - a.w) * k;
			a.h += (a.th - a.h) * k;
			if (Math.abs(a.tx - a.x) < SNAP && Math.abs(a.ty - a.y) < SNAP && Math.abs(a.tw - a.w) < SNAP && Math.abs(a.th - a.h) < SNAP) {
				a.x = a.tx;
				a.y = a.ty;
				a.w = a.tw;
				a.h = a.th;
				if (a.flying) {
					a.flying = false;
					a.glow = GLOW_S;
				}
			} else {
				moving = true;
			}
			if (a.glow > 0) {
				a.glow = Math.max(0, a.glow - dt);
				moving = true;
			}
		}
		return moving;
	}

	public WallLayout.@Nullable Layout layout() {
		return layout;
	}

	/** Goes up on every rebuild (glyph caches key on it). */
	public long version() {
		return version;
	}

	public int rebuilds() {
		return rebuilds;
	}

	public @Nullable Anim anim(String key) {
		return anims.get(key);
	}

	/** The card or chip under board point {@code (x, y)} with the cached layout, or null. */
	public WallLayout.@Nullable Hit hit(float x, float y) {
		WallLayout.Layout l = layout;
		return l == null ? null : l.hit(x, y);
	}

	public @Nullable String hovered() {
		return hovered;
	}

	public void setHovered(@Nullable String key) {
		hovered = key;
	}
}
