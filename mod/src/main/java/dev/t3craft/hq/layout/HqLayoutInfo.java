package dev.t3craft.hq.layout;

/**
 * One selectable HQ layout (see {@link HqLayouts}). Pure data, safe to show in a menu.
 *
 * @param id      stable id ({@code classic}, {@code campus}, {@code ring}, {@code vertical},
 *                {@code ensanche}, {@code village}); stored in the config ({@code hqLayout}) and in
 *                the HQ world's marker ({@code HqLayoutState})
 * @param nameKey lang key of the display name ({@code t3craft.hq.layout.<id>.name})
 * @param descKey lang key of the one-line description ({@code t3craft.hq.layout.<id>.desc})
 * @param large   the layout needs a lot of room (campus): a menu may warn before choosing it
 */
public record HqLayoutInfo(String id, String nameKey, String descKey, boolean large) {
}
