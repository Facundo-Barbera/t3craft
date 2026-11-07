package dev.t3craft.hq;

import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Temporary stub. */
final class CherryStyle extends HqStyle {
	static final String ID = "cherry";
	private final ClassicStyle d = new ClassicStyle();
	@Override public String id() { return ID; }
	@Override public Map<Role, BlockState> palette() { return d.palette(); }
	@Override void hub(PlanBuilder.Canvas c, boolean n) { d.hub(c, n); }
	@Override void board(PlanBuilder.Canvas c, CompoundTag s) { d.board(c, s); }
	@Override void archive(PlanBuilder.Canvas c) { d.archive(c); }
	@Override void portico(PlanBuilder.Canvas c) { d.portico(c); }
	@Override void spineRow(PlanBuilder.Canvas c, int r, @Nullable CompoundTag e, @Nullable CompoundTag w) { d.spineRow(c, r, e, w); }
	@Override void spineCap(PlanBuilder.Canvas c, Box b) { d.spineCap(c, b); }
	@Override void wingEndWall(PlanBuilder.Canvas c, int i, int u, boolean in) { d.wingEndWall(c, i, u, in); }
	@Override void segment(PlanBuilder.Canvas c, int i, int s, DyeColor a) { d.segment(c, i, s, a); }
	@Override void desk(PlanBuilder.Canvas c, DeskCells x) { d.desk(c, x); }
}
