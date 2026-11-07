package dev.t3craft.client.portable.held;

import static dev.t3craft.client.portable.held.HeldFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.t3craft.client.portable.PortableState;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeldScreenCacheTest {
	private static List<HeldScreenModel.Row> rows(PortableState s) {
		return HeldScreenModel.build(input(s));
	}

	@Test
	void redrawsOnlyWhenTheStateChanges() {
		HeldScreenCache c = new HeldScreenCache();
		PortableState s1 = connected(thread("vps", "a", "Alpha", "working", 2, "Running", false));
		assertTrue(c.offer(rows(s1)), "first frame draws");
		for (int frame = 0; frame < 100; frame++) {
			assertFalse(c.offer(rows(s1)), "same state, rebuilt every tick: no redraw");
		}
		assertEquals(1, c.version());

		PortableState s2 = connected(thread("vps", "a", "Alpha", "needs_attention", 0, "Approval needed: command", false));
		assertTrue(c.offer(rows(s2)), "state change redraws");
		assertEquals(2, c.version());
		assertFalse(c.offer(rows(s2)));

		PortableState s3 = connected(thread("vps", "a", "Alpha", "needs_attention", 0, "Waiting for your answer", false));
		assertTrue(c.offer(rows(s3)), "a new latest message redraws");
	}

	@Test
	void invalidateForcesTheNextRedraw() {
		HeldScreenCache c = new HeldScreenCache();
		PortableState s = connected();
		c.offer(rows(s));
		assertTrue(c.hasContent());
		c.invalidate(); // texture released (world closed)
		assertFalse(c.hasContent());
		assertTrue(c.offer(rows(s)));
	}

	@Test
	void offeredListIsCopied() {
		HeldScreenCache c = new HeldScreenCache();
		List<HeldScreenModel.Row> r = new java.util.ArrayList<>(rows(connected()));
		c.offer(r);
		r.clear();
		assertFalse(c.offer(rows(connected())), "mutating the caller's list does not poison the cache");
	}
}
