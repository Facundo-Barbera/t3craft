package dev.t3craft.mapgen;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The map types the mod offers. Adding a type is adding an entry here and a world entry point; the HQ stays
 * the default and is never replaced by another type.
 *
 * @param id       the generator's id ({@link MapGenerator#id()}); for the citadel the layout id, never renamed
 * @param openWith the client command that opens the world of this type
 */
public record MapTypes(String id, String name, String summary, String openWith) {
	/** The HQ citadel: a ring campus around a hub, grown by the roster. The default world. */
	public static final MapTypes CITADEL = new MapTypes("hybrid-ringcampus", "Citadel HQ",
			"One solid landmass: hub, ring-1 nodes and a barrio lot per project.", "/t3craft demo");

	/** Individual floating islands of four sizes; bridges appear when a player nears an island's edge. */
	public static final MapTypes FLOATING_ISLANDS = new MapTypes("floating-islands", "Floating islands",
			"Small, medium, large and extra large islands with no solid path; bridges unfold as you near an edge.",
			"/t3craft islands");

	private static final List<MapTypes> ALL = List.of(CITADEL, FLOATING_ISLANDS);

	public static List<MapTypes> all() {
		return ALL;
	}

	public static @Nullable MapTypes byId(String id) {
		for (MapTypes t : ALL) {
			if (t.id.equals(id)) {
				return t;
			}
		}
		return null;
	}
}
