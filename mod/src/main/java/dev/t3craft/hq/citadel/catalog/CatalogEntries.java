package dev.t3craft.hq.citadel.catalog;

import java.util.List;

/**
 * Every variant of the catalog, one line each.
 *
 * <p><b>Add your line in alphabetical order of the variant's id; do not reorder or reformat anything
 * else.</b> One line per variant, fully qualified class name (no imports to merge), trailing comma
 * (allowed in a Java array initializer). That way forty branches that each add one line merge as a
 * plain union of lines.
 */
final class CatalogEntries {
	private CatalogEntries() {
	}

	private static final Variant[] ENTRIES = {
			new dev.t3craft.hq.citadel.catalog.house.HouseTimberCottage(), // house_timber_cottage
			new dev.t3craft.hq.citadel.catalog.lamp.LampCherryPost(), // lamp_cherry_post
			new dev.t3craft.hq.citadel.catalog.lamp.LampStreetAvenueDouble(), // lamp_street_avenue_double
			new dev.t3craft.hq.citadel.catalog.lamp.LampStreetBrazierTripod(), // lamp_street_brazier_tripod
			new dev.t3craft.hq.citadel.catalog.lamp.LampStreetToro(), // lamp_street_toro
	};

	static final List<Variant> ALL = List.of(ENTRIES);
}
