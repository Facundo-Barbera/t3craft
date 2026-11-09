package dev.t3craft.hq.layout;

/**
 * One selectable HQ layout (see {@link HqLayouts}). Pure data, safe to show in a menu.
 *
 * @param id      stable id ({@code hybrid-ringcampus} (the citadel), {@code classic});
 *                stored in the config ({@code hqLayout}) and in
 *                the HQ world's marker ({@code HqLayoutState})
 * @param nameKey lang key of the display name ({@code t3craft.hq.layout.<id>.name})
 * @param descKey lang key of the one-line description ({@code t3craft.hq.layout.<id>.desc})
 * @param large   the layout needs a lot of room: a menu may warn before choosing it (no layout is flagged today)
 * @param primary the main design ({@link HqLayouts#PRIMARY_ID}): the HQ menu lists it first and tags it.
 *                Presentation only.
 */
public record HqLayoutInfo(String id, String nameKey, String descKey, boolean large, boolean primary) {
	/** A non-primary layout; the constructor from before {@code primary} existed. */
	public HqLayoutInfo(String id, String nameKey, String descKey, boolean large) {
		this(id, nameKey, descKey, large, false);
	}
}
