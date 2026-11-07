package dev.t3craft.client.npc.behavior;

import static org.junit.jupiter.api.Assertions.*;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class NpcBrainTest {
	static final Anchor DESK = Anchor.at(0.5, 64, 0.5, 0);
	static final Anchor BOARD_SLOT = Anchor.at(10.5, 64, 0.5, 90);
	static final Anchor HOME = Anchor.at(0.5, 64, 4.5, 0);
	static final NpcBrain.Navigator NAV = new GridPathfinder(Walkable.flat(64))::find;

	static NpcBrain.Targets targets(@Nullable Anchor slot) {
		return new NpcBrain.Targets(DESK, slot, HOME, 3.0, DESK);
	}

	static void run(NpcBrain b, Mode m, int ticks, @Nullable Pos player) {
		for (int i = 0; i < ticks; i++) {
			b.tick(m, false, targets(m == Mode.ATTENTION ? BOARD_SLOT : null), player, NAV);
		}
	}

	@Test
	void joiningAWorldPlacesNpcsWithoutAParade() {
		NpcBrain b = new NpcBrain("vps:t1", new Pos(50, 64, 50));
		b.tick(Mode.WORKING, false, targets(null), null, NAV);
		assertTrue(b.teleported());
		assertEquals(DESK.pos(), b.pos());
		assertEquals(NpcBrain.Phase.TYPING, b.phase());
	}

	@Test
	void workingTypesAtTheDesk() {
		NpcBrain b = new NpcBrain("vps:t1", DESK.pos());
		int swings = 0;
		for (int i = 0; i < 200; i++) {
			b.tick(Mode.WORKING, false, targets(null), null, NAV);
			if (b.swing() != Hand.NONE) {
				swings++;
			}
		}
		assertEquals(NpcBrain.Phase.TYPING, b.phase());
		assertEquals(Emote.TYPING, b.emote());
		assertTrue(swings > 10, "types: " + swings);
	}

	@Test
	void attentionWalksToTheBoardAndShowsIconAndBubble() {
		NpcBrain b = new NpcBrain("vps:t1", DESK.pos());
		run(b, Mode.WORKING, 5, null);
		run(b, Mode.ATTENTION, 1, null);
		assertEquals(NpcBrain.Phase.WALKING, b.phase(), "walks, does not teleport");
		assertTrue(b.iconWanted());
		assertTrue(b.bubbleWanted(100));
		run(b, Mode.ATTENTION, 200, null);
		assertEquals(NpcBrain.Phase.WAITING, b.phase());
		assertTrue(b.pos().horizDist(BOARD_SLOT.pos()) < 1e-6);
		assertEquals(Emote.EXCLAIM, b.emote());
	}

	@Test
	void waitingNpcTurnsToANearbyPlayerAndWaves() {
		NpcBrain b = new NpcBrain("vps:t1", BOARD_SLOT.pos());
		Pos player = BOARD_SLOT.pos().add(0, 0, 3);
		boolean waved = false;
		for (int i = 0; i < 120; i++) {
			b.tick(Mode.ATTENTION, false, targets(BOARD_SLOT), player, NAV);
			waved |= b.swing() == Hand.MAIN;
		}
		assertTrue(waved);
		assertEquals(b.pos().yawTo(player), b.bodyYaw(), 1.0);
	}

	@Test
	void completedCelebratesThenRests() {
		NpcBrain b = new NpcBrain("vps:t1", DESK.pos());
		run(b, Mode.WORKING, 5, null);
		run(b, Mode.COMPLETED, 10, null);
		assertEquals(NpcBrain.Phase.CELEBRATING, b.phase());
		double maxHop = 0;
		for (int i = 0; i < NpcBrain.CELEBRATE_TICKS; i++) {
			run(b, Mode.COMPLETED, 1, null);
			maxHop = Math.max(maxHop, b.hop());
		}
		assertTrue(maxHop > 0.3, "hops");
		run(b, Mode.COMPLETED, 100, null);
		assertEquals(NpcBrain.Phase.RESTING, b.phase());
		assertTrue(b.crouch());
	}

	@Test
	void seededAsCompletedDoesNotCelebrate() {
		NpcBrain b = new NpcBrain("vps:t1", DESK.pos());
		run(b, Mode.COMPLETED, 1, null);
		assertEquals(NpcBrain.Phase.RESTING, b.phase());
	}

	@Test
	void idleWandersNearHome() {
		NpcBrain b = new NpcBrain("vps:t9", HOME.pos());
		double maxDist = 0;
		boolean moved = false;
		for (int i = 0; i < 2000; i++) {
			run(b, Mode.IDLE, 1, null);
			maxDist = Math.max(maxDist, b.pos().horizDist(HOME.pos()));
			moved |= b.walking();
		}
		assertTrue(moved, "wanders");
		assertTrue(maxDist <= 3.0 + 1e-6, "stays in the neighbourhood: " + maxDist);
	}

	@Test
	void stepsAsideWhenThePlayerWalksIntoIt() {
		NpcBrain b = new NpcBrain("vps:t1", DESK.pos());
		run(b, Mode.WORKING, 3, null);
		Pos player = DESK.pos().add(0.3, 0, 0);
		run(b, Mode.WORKING, 30, player);
		assertTrue(b.pos().horizDist(player) > 1.2, "moved away: " + b.pos());
		// Player leaves: back to the desk.
		run(b, Mode.WORKING, 200, new Pos(30, 64, 30));
		assertTrue(b.pos().horizDist(DESK.pos()) < 1e-6);
		assertEquals(NpcBrain.Phase.TYPING, b.phase());
	}

	@Test
	void deterministicPerId() {
		NpcBrain a = new NpcBrain("same", HOME.pos());
		NpcBrain c = new NpcBrain("same", HOME.pos());
		for (int i = 0; i < 500; i++) {
			run(a, Mode.IDLE, 1, null);
			run(c, Mode.IDLE, 1, null);
		}
		assertEquals(a.pos(), c.pos());
	}

	@Test
	void summaryChangeFlashesTheBubble() {
		NpcBrain b = new NpcBrain("vps:t1", DESK.pos());
		run(b, Mode.WORKING, 3, null);
		assertFalse(b.bubbleWanted(50));
		b.summaryChanged();
		run(b, Mode.WORKING, 1, null);
		assertTrue(b.bubbleWanted(50));
		run(b, Mode.WORKING, NpcBrain.FLASH_TICKS, null);
		assertFalse(b.bubbleWanted(50));
		assertTrue(b.bubbleWanted(2), "always when the player is close");
	}
}
