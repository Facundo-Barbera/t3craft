package dev.t3craft.hq;

import org.jspecify.annotations.Nullable;

/**
 * A style's own ground plan, instead of the shared spine of {@link HqGeometry} (layout/blocks: the
 * "ensanche" of {@link EnsancheStyle}). A style that returns one from {@link HqStyle#town()} decides
 * where modules go ({@link #allocate}) and what the whole HQ looks like ({@link #generate}); the
 * {@link HqLayout} record, the anchor names, the workstations and the fixtures stay the same, so
 * NPCs, screens, the blocks module and the builders work unchanged.
 *
 * <p>Same contract as the shared generator: pure, deterministic, and stable (a module index is a
 * fixed place, so keeping the previous layout keeps every building where it is).
 */
interface TownLayout {
	/** Desks and modules for {@code input}, keeping what {@code previous} had (see {@link HqLayout#allocate}). */
	HqLayout allocate(HqInput input, @Nullable HqLayout previous);

	/** The whole HQ of a layout made by {@link #allocate}. */
	BuildPlan generate(HqLayout layout, HqStyle style);
}
