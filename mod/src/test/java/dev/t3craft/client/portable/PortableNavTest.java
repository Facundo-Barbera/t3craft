package dev.t3craft.client.portable;

import static dev.t3craft.client.portable.PortableFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortableNavTest {
	@Test
	void startsOnThreadsWithAllEnvironments() {
		PortableNav nav = new PortableNav();
		nav.update(twoEnvs());
		assertEquals(PortableNav.Page.THREADS, nav.page());
		assertNull(nav.envFilter());
		assertEquals(Arrays.asList(null, "vps", "mini"), nav.envChoices());
		assertEquals("Bravo", nav.selectedThread().title());
	}

	@Test
	void movesAndClampsSelection() {
		PortableNav nav = new PortableNav();
		nav.update(twoEnvs());
		nav.move(-5);
		assertEquals(0, nav.selected());
		nav.move(2);
		assertEquals("Charlie", nav.selectedThread().title());
		nav.move(99);
		assertEquals("Delta", nav.selectedThread().title());
	}

	@Test
	void cyclesEnvironmentFilterBothWays() {
		PortableNav nav = new PortableNav();
		nav.update(twoEnvs());
		nav.cycleEnv(1);
		assertEquals("vps", nav.envFilter());
		assertEquals(3, nav.visible().size());
		nav.cycleEnv(1);
		assertEquals("mini", nav.envFilter());
		assertEquals("Delta", nav.selectedThread().title());
		nav.cycleEnv(1);
		assertNull(nav.envFilter());
		nav.cycleEnv(-1);
		assertEquals("mini", nav.envFilter());
	}

	@Test
	void keepsTheSameThreadSelectedWhenTheListReorders() {
		PortableNav nav = new PortableNav();
		nav.update(twoEnvs());
		nav.move(1); // Alpha
		assertEquals("Alpha", nav.selectedThread().title());
		// Alpha now needs attention too and sorts first
		nav.update(PortableState.of("direct", envs("vps", "ok", "mini", "ok"), List.of(
				thread("vps", "a", "Alpha", "needs_attention", 0),
				thread("vps", "b", "Bravo", "needs_attention", 0),
				thread("mini", "d", "Delta", "idle", 4)), true, null));
		assertEquals("Alpha", nav.selectedThread().title());
	}

	@Test
	void dropsFilterWhenItsEnvironmentDisappears() {
		PortableNav nav = new PortableNav();
		nav.update(twoEnvs());
		nav.setEnv("mini");
		nav.update(PortableState.of("direct", envs("vps", "ok"), List.of(thread("vps", "a", "Alpha", "working", 2)), true, null));
		assertNull(nav.envFilter());
		assertEquals("Alpha", nav.selectedThread().title());
	}

	@Test
	void unknownFilterFallsBackToAll() {
		PortableNav nav = new PortableNav();
		nav.update(twoEnvs());
		nav.setEnv("nope");
		assertNull(nav.envFilter());
	}

	@Test
	void emptyAndDisconnectedStatesHaveNoSelection() {
		PortableNav nav = new PortableNav();
		nav.update(PortableState.EMPTY);
		assertNull(nav.selectedThread());
		nav.move(1);
		assertEquals(0, nav.selected());
		assertEquals(Arrays.asList((String) null), nav.envChoices());
		nav.update(PortableState.of("direct", envs("vps", "offline"), List.of(), true, null));
		assertNull(nav.selectedThread());
		assertEquals(PortableState.Connection.DISCONNECTED, nav.state().connection());
	}

	@Test
	void notificationsPageAndBack() {
		PortableNav nav = new PortableNav();
		nav.update(twoEnvs());
		nav.showNotifications(3);
		assertEquals(PortableNav.Page.NOTIFICATIONS, nav.page());
		nav.move(10);
		assertEquals(2, nav.notificationSelected());
		assertTrue(nav.back());
		assertEquals(PortableNav.Page.THREADS, nav.page());
		assertFalse(nav.back(), "back at the root closes the screen");
		nav.togglePage(0);
		assertEquals(PortableNav.Page.NOTIFICATIONS, nav.page());
		nav.move(1);
		assertEquals(0, nav.notificationSelected());
		nav.togglePage(0);
		assertEquals(PortableNav.Page.THREADS, nav.page());
	}

	@Test
	void notificationFocusesItsThreadOrStaysPut() {
		PortableNav nav = new PortableNav();
		nav.update(twoEnvs());
		nav.setEnv("mini");
		nav.showNotifications(2);
		assertTrue(nav.focusThread("vps:c"));
		assertEquals(PortableNav.Page.THREADS, nav.page());
		assertNull(nav.envFilter());
		assertEquals("Charlie", nav.selectedThread().title());

		nav.showNotifications(2);
		assertFalse(nav.focusThread("vps:gone"));
		assertFalse(nav.focusThread(null));
		assertEquals(PortableNav.Page.NOTIFICATIONS, nav.page());
	}
}
