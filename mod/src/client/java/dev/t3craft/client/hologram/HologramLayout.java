// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.hologram;

/**
 * Heights of the hologram pieces above the anchor's foot level (the hub floor), pure so they are
 * tested. After AgentCraft's {@code HOLO_Y} (card ≈ 3.5 blocks over the lens) with a beam from the
 * floor to the bottom of the card.
 *
 * <p>Legibility: the card's bottom edge sits at {@link #CARD_BOTTOM} above the floor, over the NPC
 * nameplate band (name tag ≈ 2.2, detail panel up to ≈ 2.8) so from eye height (1.62) it never
 * hides a nameplate, and its top stays under the hub's hanging lantern (5 blocks over the floor).
 * Text displays grow upwards from their position and are 0.025 blocks per font pixel at scale 1.
 */
public final class HologramLayout {
	/** Bottom of the detail card above the floor (blocks). */
	public static final double CARD_BOTTOM = 2.95;
	/** Gap between the detail card and the big header. */
	public static final double GAP = 0.06;
	/** Header scale: digits readable from across the hub. */
	public static final float HEADER_SCALE = 1.4f;
	/** Detail scale. */
	public static final float DETAIL_SCALE = 0.6f;
	/** Beam width (blocks) and how far it starts above the floor. */
	public static final float BEAM_WIDTH = 0.14f;
	public static final double BEAM_FLOOR = 0.02;
	/** Free height under the hub's lantern; the header top must stay below it. */
	public static final double CEILING = 5.0;

	static final double PX = 0.025;
	static final int LINE_PX = 10;
	static final int PAD_PX = 2;

	private HologramLayout() {
	}

	/** Height in blocks of a text display with {@code lines} lines at {@code scale}. */
	public static double textHeight(int lines, float scale) {
		return lines <= 0 ? 0 : (lines * LINE_PX + PAD_PX) * PX * scale;
	}

	/** Y offset (from the floor) of the header display's origin. */
	public static double headerY(int detailLines) {
		return CARD_BOTTOM + textHeight(detailLines, DETAIL_SCALE) + GAP;
	}

	/** Top of the whole card above the floor. */
	public static double top(int detailLines) {
		return headerY(detailLines) + textHeight(1, HEADER_SCALE);
	}

	// ---- card style (world text, hologram style) ----------------------------------------------

	/** Card text scale. */
	public static final float CARD_SCALE = 0.6f;
	/** Ring radius (blocks, to the segments' centre line) and its segments. */
	public static final float RING_RADIUS = 0.42f;
	public static final int RING_SEGMENTS = 16;
	/** Segment thickness (radial) and depth; length is {@link #RING_FILL} of the arc per segment. */
	public static final float RING_THICK = 0.07f;
	public static final float RING_DEPTH = 0.01f;
	public static final float RING_FILL = 0.78f;
	/** Gap between the card's top and the ring's outer edge. */
	public static final double RING_GAP = 0.08;
	/** Caption scale (the "4/9" in the ring's middle). */
	public static final float CAPTION_SCALE = 0.9f;
	/** Soft beam: a thin core and a wider, fainter halo around it. */
	public static final float BEAM_CORE = 0.06f;
	public static final float BEAM_HALO = 0.24f;

	/** Centre of the ring above the floor, over a card of {@code cardLines} lines. */
	public static double ringCentre(int cardLines) {
		return CARD_BOTTOM + textHeight(cardLines, CARD_SCALE) + RING_GAP + RING_RADIUS + RING_THICK / 2;
	}

	/** Top of the card style (ring's outer edge) above the floor; stays under {@link #CEILING}. */
	public static double cardTop(int cardLines) {
		return ringCentre(cardLines) + RING_RADIUS + RING_THICK / 2;
	}

	/** Origin (bottom) of the caption display so its one line is centred on the ring. */
	public static double captionY(int cardLines) {
		return ringCentre(cardLines) - textHeight(1, CAPTION_SCALE) / 2;
	}

	/** Length of one ring segment (blocks). */
	public static float segmentLength() {
		return (float) (2 * Math.PI * RING_RADIUS / RING_SEGMENTS * RING_FILL);
	}

	/**
	 * Angle (radians, from +x, counter-clockwise in the ring's plane) of segment {@code i}: segment 0
	 * at the top, then clockwise as seen by the viewer, like a progress ring.
	 */
	public static double segmentAngle(int i) {
		return Math.PI / 2 - 2 * Math.PI * i / RING_SEGMENTS;
	}

	/** Beam height: from just over the floor up to the bottom of the card. */
	public static float beamHeight() {
		return (float) (CARD_BOTTOM - BEAM_FLOOR);
	}
}
