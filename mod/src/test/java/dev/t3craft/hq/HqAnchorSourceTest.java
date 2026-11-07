package dev.t3craft.hq;

import static dev.t3craft.hq.HqTestSupport.input;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.t3craft.client.npc.behavior.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The NPC behaviour adapter: world coordinates under rotation, names, fallbacks. */
class HqAnchorSourceTest {
	@BeforeAll
	static void boot() {
		HqTestSupport.boot();
	}

	@Test
	void mapsDeskBoardHomeAndRestToWorldAnchors() {
		BuildPlan p = HqGenerator.plan(input(2, 3), null).plan();
		for (Direction e : Direction.Plane.HORIZONTAL) {
			HqSite site = HqSite.at(p, AnchorNames.SPAWN, new BlockPos(50, 70, -20), e);
			HqAnchorSource src = new HqAnchorSource(p, site);
			Anchor desk = src.desk("env:p1-t2", "env:p1");
			assertNotNull(desk);
			dev.t3craft.hq.Anchor rel = p.anchor(AnchorNames.desk("env:p1-t2"));
			BlockPos cell = site.cell(rel.block());
			assertEquals(cell.getX() + 0.5, desk.pos().x(), 1e-9);
			assertEquals(site.anchor(rel).y(), desk.pos().y(), 1e-9);
			assertEquals(cell.getY(), desk.pos().y(), 1e-9, "standing on the desk floor");
			assertEquals(cell.getZ() + 0.5, desk.pos().z(), 1e-9);
			assertEquals(site.direction(rel.facing()).toYRot(), desk.yaw(), 1e-6);

			Anchor board = src.board();
			assertNotNull(board);
			// NPCs at the board look at it: the opposite of where the entrance looks
			assertEquals(e.getOpposite().toYRot(), board.yaw(), 1e-6);
			assertEquals(14, src.boardSlots().size());
			assertNotNull(src.home("env:p0"));
			assertEquals(HqAnchorSource.convert(site.anchor(p.anchor(AnchorNames.walk("env:p0")))), src.home("env:p0"));
			assertNotNull(src.rest("env:p0-t0", "env:p0"));
			assertEquals(src.rest("env:p0-t0", "env:p0"), new HqAnchorSource(p, site).rest("env:p0-t0", "env:p0"), "stable bench");
		}
	}

	@Test
	void unknownIdsFallBack() {
		BuildPlan p = HqGenerator.plan(input(1, 1), null).plan();
		HqAnchorSource src = new HqAnchorSource(p, new HqSite(BlockPos.ZERO, Rotation.NONE));
		assertNull(src.desk("nope", "env:p0"));
		assertNull(src.home("nope"));
		assertNull(src.rest("nope", "env:p0"));
	}
}
