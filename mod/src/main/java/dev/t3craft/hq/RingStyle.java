package dev.t3craft.hq;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * layout/ring, "Ciudad en anillos": a style that brings its <b>own layout</b> instead of filling the
 * spine grid of {@link HqGeometry}. A domed octagonal hub stands on a podium in the middle of a
 * sunken garden patio; an elevated cloister (the first ring) goes all the way round it, linked to the
 * hub by bridges over the patio; radial avenues ramp down from the cloister to rings of wedge-shaped
 * sectors, one per project; special rooms (a tower, rotundas, a sunken patio) stand on the nodes
 * where the radials cross the ring street. See {@link RingGeometry} for the geometry and the growth
 * rules and {@link RingGenerator} for the plan.
 *
 * <p>{@link HqGenerator#generate} hands layouts of this style to {@link RingGenerator}; the
 * spine-grid pieces of {@link HqStyle} are never called, so they do nothing. The palette lists every
 * role (other tools read it); the generator mixes it with {@link HqKit} roles (floors, wall panels,
 * beams, lanterns, desks, planters) so the blocks module's kit is used when it is on.
 */
public final class RingStyle extends HqStyle {
	public static final String ID = "ring";

	private static final Map<Role, BlockState> PALETTE = palette0();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Map<Role, BlockState> palette() {
		return PALETTE;
	}

	/**
	 * Where new modules go: an annex becomes a floor on the project's sector, a new project the first
	 * free sector in a fixed order ({@link RingGeometry#place}).
	 */
	public HqLayout.Placer placer() {
		return RingGeometry::place;
	}

	// ------------------------------------------------------------------ layout hook (HqStyle)

	@Override
	public HqLayout allocate(HqInput input, @org.jspecify.annotations.Nullable HqLayout previous) {
		return HqLayout.allocate(input, previous, placer());
	}

	@Override
	public HqFootprints.Area envelope(HqLayout layout) {
		java.util.List<Integer> idx = layout.modules().stream().map(HqLayout.Module::index).toList();
		int r = RingGeometry.radius(RingGeometry.rings(idx));
		return new HqFootprints.Area(-r, -r, r, r);
	}

	@Override
	public int bottom() {
		// the sunken lounge's amphitheatre steps down to y = -3
		return -3;
	}

	@Override
	public int top() {
		// podium, three floors of sectors and their roofs, the hub's dome and lantern
		return 64;
	}

	@Override
	public boolean ownLayout() {
		return true;
	}

	@Override
	BuildPlan layoutPlan(HqLayout layout) {
		return new RingGenerator(this).generate(layout);
	}

	private static Map<Role, BlockState> palette0() {
		Map<Role, BlockState> m = new EnumMap<>(Role.class);
		// feat/hq-layouts: one visual style for every layout, cherry (the layout keeps its own per-barrio/tower materials)
		m.putAll(new CherryStyle().palette());
		return Map.copyOf(m);
	}

	@Override
	BlockState bench(Direction back) {
		return palette().get(Role.BENCH).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, back);
	}

	// ------------------------------------------------------------------ spine-grid pieces: unused (RingGenerator draws everything)

	@Override
	void hub(PlanBuilder.Canvas c, boolean northDoor) {
	}

	@Override
	void board(PlanBuilder.Canvas c, SignText sign) {
	}

	@Override
	void archiveNook(PlanBuilder.Canvas c) {
	}

	@Override
	void portico(PlanBuilder.Canvas c) {
	}

	@Override
	void spineRow(PlanBuilder.Canvas c, int r, @Nullable SignText eastSign, @Nullable SignText westSign) {
	}

	@Override
	void spineCap(PlanBuilder.Canvas c, Box box) {
	}

	@Override
	void wingEndWall(PlanBuilder.Canvas c, int i, int u, boolean inner) {
	}

	@Override
	void segment(PlanBuilder.Canvas c, int i, int s, net.minecraft.world.item.DyeColor accent) {
	}

	@Override
	void desk(PlanBuilder.Canvas c, DeskCells cells) {
	}
}
