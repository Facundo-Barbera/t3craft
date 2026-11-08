package dev.t3craft.hq.citadel.catalog;

/**
 * The families of the citadel catalog. Each {@link Variant} belongs to one; a family leader places
 * its variants in the city through {@link Catalog#pick}. Sizes are the typical box
 * ({@code w × d × h}, frontage × depth × height, in blocks): a variant states its own range with
 * {@link Variant#minW()} and friends, ideally covering most of the family's range.
 */
public enum Family {
	/** A dwelling: main body, roof, facade with depth. 9–15 × 9–15 × 10–18. */
	HOUSE,
	/** A small building next to a house: shed, workshop, greenhouse, pergola. 4–8 × 4–8 × 4–8. */
	ANNEX,
	/** A market stall: counter, awning, crates and goods. 3–6 × 3–5 × 4–6. */
	STALL,
	/** A fountain: basin, water, a central spout or sculpture. 3–9 × 3–9 × 2–7. */
	FOUNTAIN,
	/** A well: ring of stone, roof on posts, bucket or chain. 3–5 × 3–5 × 4–7. */
	WELL,
	/** A custom tree: trunk with branches, layered canopy (not a vanilla lollipop). 3–9 × 3–9 × 5–14. */
	TREE,
	/** A garden: beds, hedge, path, flowers, maybe a small tree or bench. 5–12 × 5–12 × 1–5. */
	GARDEN,
	/** A street lamp: post, lantern, base. 1–3 × 1–3 × 3–6. */
	LAMP,
	/** A bench (or a pair, with a planter or lamp). 2–5 × 1–3 × 1–3. */
	BENCH,
	/** A statue or monument on a plinth. 2–5 × 2–5 × 4–10. */
	STATUE,
	/** A gate or arch over a path (the path runs front → back through the middle, kept walkable). 5–11 × 2–4 × 5–10. */
	GATE,
	/** A footbridge (the deck runs front → back, walkable at y = 0 at both ends). 3–5 × 5–15 × 2–6. */
	BRIDGE,
	/** The centrepiece of a plaza: pavement pattern plus a feature (obelisk, pool, tree ring…). 7–15 × 7–15 × 1–10. */
	PLAZA_CENTER,
	/** A planter: raised bed or big pot with plants. 1–4 × 1–3 × 1–3. */
	PLANTER,
	/** A signpost: post with arms or a notice board. 1–3 × 1–2 × 2–4. */
	SIGNPOST,
	/** A banner or flag pole with its base. 1–3 × 1–3 × 4–9. */
	BANNER_POLE,
	/** A gazebo or open pavilion with a roof on columns. 5–9 × 5–9 × 5–9. */
	GAZEBO,
	/** The crown of a tower: spire, lantern room or crenellation, sitting on top of a tower shaft. 3–9 × 3–9 × 3–10. */
	TOWER_TOP,
	/** A roof to put over a given footprint (the box is the roof, from the eaves up). 5–15 × 5–15 × 3–8. */
	ROOF
}
