package dev.t3craft.site;

/**
 * Turns a recorded {@link SiteModule} into blocks (the HQ design, {@code feat/hq-design}, plugs in
 * here through {@code SiteRuntime.setDesigner}). Contract: return a plan whose box is {@code area}
 * (the module footprint × the {@link SiteLimits#HEADROOM} layers above the pad); {@code origin} is
 * the <b>site</b> origin (so module plans share one frame, see {@link SiteFrame}). Cells left null
 * keep the leveling's air. No block entities. Must be deterministic for a given module: a move
 * rebuilds modules from their spec.
 */
@FunctionalInterface
public interface ModuleDesigner {
	CellPlan design(SiteModule module, Box area, CellPlan.Origin origin);

	static ModuleDesigner of(StructureSource source) {
		return (module, area, origin) -> source.design(area, origin);
	}

	ModuleDesigner PLACEHOLDER = of(StructureSource.PLACEHOLDER);
}
