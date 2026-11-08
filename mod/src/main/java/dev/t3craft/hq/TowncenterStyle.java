package dev.t3craft.hq;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * "Centro urbano con torres" (layout {@code hybrid-towncenter}): the ensanche's manzanas with garden
 * patios, chamfered street grid and covered passages; a monumental civic hall on the plaza mayor
 * (a cross-shaped nave with a crossing tower, in the scale of the gothic crossing and the
 * steampunk nave) that several streets reach; singular buildings for the special rooms (covered
 * market = lounge, clock tower = scheduled tasks, signal box = needs attention / errors, library =
 * archive); and selective verticality from layout/vertical: projects with many threads grow up as
 * a tower with a stair atrium on their manzana's corner, joined to facing towers by skybridges
 * (see {@link TowncenterGrid}, {@link TowncenterAllocator}, {@link TowncenterBuilder}).
 *
 * <p>Cherry palette like every layout; each manzana keeps the ensanche's barrio materials
 * ({@link EnsancheStyle#mat}). The spine pieces of {@link HqStyle} are never called for an
 * own-layout style; they delegate to cherry so the class stays a complete style.
 */
final class TowncenterStyle extends HqStyle {
	static final String ID = TowncenterGrid.ID;

	private final Map<Role, BlockState> palette;
	private final CherryStyle spine = new CherryStyle();

	TowncenterStyle() {
		Map<Role, BlockState> m = new EnumMap<>(Role.class);
		m.putAll(new CherryStyle().palette());
		palette = Collections.unmodifiableMap(m);
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Map<Role, BlockState> palette() {
		return palette;
	}

	// ------------------------------------------------------------------ layout hook (HqStyle)

	@Override
	public HqLayout allocate(HqInput input, @Nullable HqLayout previous) {
		return TowncenterAllocator.INSTANCE.allocate(input, previous);
	}

	@Override
	public HqFootprints.Area envelope(HqLayout layout) {
		return TowncenterGrid.envelope(TowncenterGrid.maxCell(layout));
	}

	@Override
	public int top() {
		return TowncenterGrid.TOP;
	}

	@Override
	public boolean ownLayout() {
		return true;
	}

	@Override
	BuildPlan layoutPlan(HqLayout layout) {
		return new TowncenterBuilder(this).generate(layout);
	}

	@Override
	BlockState bench(Direction back) {
		return palette().get(Role.BENCH).setValue(BlockStateProperties.HORIZONTAL_FACING, back);
	}

	// ------------------------------------------------------------------ spine pieces (unused: see class doc)

	@Override
	void hub(PlanBuilder.Canvas c, boolean northDoor) {
		spine.hub(c, northDoor);
	}

	@Override
	void board(PlanBuilder.Canvas c, SignText sign) {
		spine.board(c, sign);
	}

	@Override
	void archiveNook(PlanBuilder.Canvas c) {
		spine.archiveNook(c);
	}

	@Override
	void portico(PlanBuilder.Canvas c) {
		spine.portico(c);
	}

	@Override
	void spineRow(PlanBuilder.Canvas c, int r, @Nullable SignText eastSign, @Nullable SignText westSign) {
		spine.spineRow(c, r, eastSign, westSign);
	}

	@Override
	void spineCap(PlanBuilder.Canvas c, Box box) {
		spine.spineCap(c, box);
	}

	@Override
	void wingEndWall(PlanBuilder.Canvas c, int i, int u, boolean inner) {
		spine.wingEndWall(c, i, u, inner);
	}

	@Override
	void segment(PlanBuilder.Canvas c, int i, int s, DyeColor accent) {
		spine.segment(c, i, s, accent);
	}

	@Override
	void desk(PlanBuilder.Canvas c, DeskCells cells) {
		spine.desk(c, cells);
	}
}
