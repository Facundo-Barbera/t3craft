package dev.t3craft.client.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** The extent contract with connected monitors: origin flag and the screen centre from the origin. */
class ScreenExtentTest {
	@Test
	void originAndMembers() {
		assertTrue(ScreenExtent.ONE.origin());
		assertTrue(new ScreenExtent(3, 2).origin());
		assertFalse(ScreenExtent.member(3, 2).origin());
		assertEquals(1, new ScreenExtent(0, -4).width(), "clamped to one block");
		assertEquals(2, new ScreenExtent(3, 2).minSide());
	}

	@Test
	void screenCentreExtendsRightAndUpFromTheOrigin() {
		BlockPos o = new BlockPos(10, 64, 10);
		// facing north: the viewer stands north looking south, their right is west (-X)
		Vec3 c = MonitorRenderer.screenCenter(o, Direction.NORTH, new ScreenExtent(3, 2));
		assertEquals(new Vec3(9.5, 65.0, 10.0), c);
		assertEquals(MonitorRenderer.faceCenter(o, Direction.EAST), MonitorRenderer.screenCenter(o, Direction.EAST, ScreenExtent.ONE));
		// facing east: viewer looks west, their right is north (-Z)
		assertEquals(new Vec3(11.0, 64.5, 10.0), MonitorRenderer.screenCenter(o, Direction.EAST, new ScreenExtent(2, 1)));
	}
}
