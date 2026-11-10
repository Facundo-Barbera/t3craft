package dev.t3craft.hq;

import dev.t3craft.hq.plan.Box;
import dev.t3craft.hq.plan.BuildPlan;
import dev.t3craft.hq.plan.PlanBuilder;
import dev.t3craft.hq.style.CherryStyle;
import dev.t3craft.hq.style.HqStyle;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * design/hybrid-ringcampus, "Ciudadela en anillos con barrios": the ring's legible skeleton (a
 * monumental hub on a podium, a sunken patio crossed by bridges, an elevated promenade ring, radial
 * avenues ramping down to ring streets) with the campus's barrios in the sectors: each project gets
 * a cluster of a gatehouse and up to three halls round its own courtyard, halls that take an upper
 * floor when the project grows. Special rooms stand on the ring-1 nodes, each with its own shape
 * (tower, rotunda, cloister, sunken patio). Geometry and growth rule in {@link RingCampusGeometry},
 * plan in {@link RingCampusGenerator}.
 *
 * <p>Like the other layouts it paints with the cherry palette (plus each barrio's own wall and roof
 * materials) and never calls the spine-grid pieces of {@link HqStyle}.
 */
public final class RingCampusStyle extends HqStyle {
	public static final String ID = RingCampusGeometry.ID;

	private static final Map<Role, BlockState> PALETTE = palette0();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Map<Role, BlockState> palette() {
		return PALETTE;
	}

	/** Where new modules go ({@link RingCampusGeometry#place}). */
	public HqLayout.Placer placer() {
		return RingCampusGeometry::place;
	}

	// ------------------------------------------------------------------ layout hook (HqStyle)

	@Override
	public HqLayout allocate(HqInput input, @Nullable HqLayout previous) {
		return HqLayout.allocate(input, previous, placer());
	}

	@Override
	public HqFootprints.Area envelope(HqLayout layout) {
		List<Integer> idx = layout.modules().stream().map(HqLayout.Module::index).toList();
		int r = RingCampusGeometry.radius(RingCampusGeometry.rings(idx));
		return new HqFootprints.Area(-r, -r, r, r);
	}

	@Override
	public int bottom() {
		// the sunken lounge's amphitheatre steps down to y = -3 (at scale 1; its depth is sy(3))
		return -RingCampusGeometry.dims().sy(3);
	}

	@Override
	public int top() {
		// two-floor halls with their roofs, the hub vault and beacon, the review tower's spire (40 at scale 1)
		return Math.max(40, RingCampusGeometry.dims().sy(40)) + RingCampusGeometry.level(0); // design/citadel-terraces: the core's lift
	}

	@Override
	public boolean ownLayout() {
		return true;
	}

	@Override
	public BuildPlan layoutPlan(HqLayout layout) {
		return new RingCampusGenerator(this).generate(layout);
	}

	@Override
	public dev.t3craft.mapgen.MapLayout mapLayout(HqLayout layout) {
		return new CitadelMapGenerator().generate(layout);
	}

	private static Map<Role, BlockState> palette0() {
		Map<Role, BlockState> m = new EnumMap<>(Role.class);
		m.putAll(new CherryStyle().palette());
		return Map.copyOf(m);
	}

	@Override
	public BlockState bench(Direction back) {
		return palette().get(Role.BENCH).setValue(BlockStateProperties.HORIZONTAL_FACING, back);
	}

	// ------------------------------------------------------------------ spine-grid pieces: unused

	@Override
	public void hub(PlanBuilder.Canvas c, boolean northDoor) {
	}

	@Override
	public void board(PlanBuilder.Canvas c, SignText sign) {
	}

	@Override
	public void archiveNook(PlanBuilder.Canvas c) {
	}

	@Override
	public void portico(PlanBuilder.Canvas c) {
	}

	@Override
	public void spineRow(PlanBuilder.Canvas c, int r, @Nullable SignText eastSign, @Nullable SignText westSign) {
	}

	@Override
	public void spineCap(PlanBuilder.Canvas c, Box box) {
	}

	@Override
	public void wingEndWall(PlanBuilder.Canvas c, int i, int u, boolean inner) {
	}

	@Override
	public void segment(PlanBuilder.Canvas c, int i, int s, net.minecraft.world.item.DyeColor accent) {
	}

	@Override
	public void desk(PlanBuilder.Canvas c, DeskCells cells) {
	}
}
