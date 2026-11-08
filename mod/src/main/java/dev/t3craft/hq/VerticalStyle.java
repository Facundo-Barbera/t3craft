package dev.t3craft.hq;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * "Torres con atrio y puentes" (layout/vertical): not a skin of the shared wing grid but its own
 * layout. {@link HqGenerator} hands plans of this style to {@link TowerGenerator}, which builds the
 * towers of {@link TowerLayout} (atrium hub, half-floor parcels, monumental stairs, skybridges,
 * podium and plaza). The wing pieces of {@link HqStyle} are never asked for, so they throw.
 *
 * <p>Listed in {@link HqStyles#layouts()}, not in {@link HqStyles#all()}: the styles of
 * {@code all()} share one geometry (their tests compare them cell by cell); this one does not.
 * Select it with {@code "hq": {"style": "vertical"}}.
 */
final class VerticalStyle extends HqStyle {
	static final String ID = "vertical";

	private final Map<Role, BlockState> palette;

	VerticalStyle() {
		Map<Role, BlockState> m = new EnumMap<>(Role.class);
		// feat/hq-layouts: one visual style for every layout, cherry (the layout keeps its own per-barrio/tower materials)
		m.putAll(new CherryStyle().palette());
		palette = Collections.unmodifiableMap(m);
	}

	@Override
	public String id() {
		return ID;
	}

	// ------------------------------------------------------------------ layout hook (HqStyle)

	@Override
	public HqFootprints.Area envelope(HqLayout layout) {
		return TowerLayout.envelope(TowerLayout.towers(layout).size());
	}

	@Override
	public int top() {
		return TowerLayout.TOP;
	}

	@Override
	public boolean ownLayout() {
		return true;
	}

	@Override
	BuildPlan layoutPlan(HqLayout layout) {
		return TowerGenerator.generate(this, layout);
	}

	@Override
	public Map<Role, BlockState> palette() {
		return palette;
	}

	@Override
	BlockState bench(Direction back) {
		return palette.get(Role.BENCH).setValue(BlockStateProperties.HORIZONTAL_FACING, back);
	}

	private static UnsupportedOperationException notAWing() {
		return new UnsupportedOperationException("the vertical HQ has no wing pieces (TowerGenerator builds it)");
	}

	@Override
	void hub(PlanBuilder.Canvas c, boolean northDoor) {
		throw notAWing();
	}

	@Override
	void board(PlanBuilder.Canvas c, SignText sign) {
		throw notAWing();
	}

	@Override
	void archiveNook(PlanBuilder.Canvas c) {
		throw notAWing();
	}

	@Override
	void portico(PlanBuilder.Canvas c) {
		throw notAWing();
	}

	@Override
	void spineRow(PlanBuilder.Canvas c, int r, SignText eastSign, SignText westSign) {
		throw notAWing();
	}

	@Override
	void spineCap(PlanBuilder.Canvas c, Box box) {
		throw notAWing();
	}

	@Override
	void wingEndWall(PlanBuilder.Canvas c, int i, int u, boolean inner) {
		throw notAWing();
	}

	@Override
	void segment(PlanBuilder.Canvas c, int i, int s, DyeColor accent) {
		throw notAWing();
	}

	@Override
	void desk(PlanBuilder.Canvas c, DeskCells cells) {
		throw notAWing();
	}
}
