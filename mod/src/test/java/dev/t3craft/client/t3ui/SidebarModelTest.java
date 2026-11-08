package dev.t3craft.client.t3ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.client.chat.Attention;
import dev.t3craft.client.chat.ThreadRow;
import dev.t3craft.t3.ThreadState;
import java.util.List;
import org.junit.jupiter.api.Test;

class SidebarModelTest {
	static ThreadRow row(String env, String id, String title, String project, ThreadState s, Long at) {
		return row(env, id, title, project, s, at, false);
	}

	static ThreadRow row(String env, String id, String title, String project, ThreadState s, Long at, boolean pinned) {
		return new ThreadRow(env + ":" + id, env, env.toUpperCase(), id, title, project, s, false, null, false, at, pinned);
	}

	static List<String> keys(SidebarModel m) {
		return m.items().stream().map(i -> i.kind().name().charAt(0) + ":" + i.key()).toList();
	}

	static SidebarModel sample() {
		SidebarModel m = new SidebarModel();
		m.update(List.of(
				row("vps", "a", "Fix login", "t3craft", ThreadState.IDLE, 100L),
				row("vps", "b", "HQ layouts", "t3craft", ThreadState.WORKING, 500L),
				row("lap", "c", "Café menu", "website", ThreadState.NEEDS_ATTENTION, 300L),
				row("lap", "d", "Login page", "website", ThreadState.DONE, 50L),
				row("lap", "e", "Scratch", "", ThreadState.IDLE, 10L),
				row("lap", "f", "Docs", "T3Craft", ThreadState.DONE, 200L), // same project, other env
				row("vps", "p", "Pinned one", "website", ThreadState.IDLE, 1L, true)));
		return m;
	}

	@Test
	void groupsByProjectLikeT3PinnedFirstFoldersByLatestActivity() {
		SidebarModel m = sample();
		assertEquals(List.of(
				"S:" + SidebarModel.PINNED, "T:vps:p",
				"P:t3craft", "T:vps:b", "T:lap:f", "T:vps:a", // t3craft latest 500; one folder across vps and lap
				"P:website", "T:lap:c", "T:lap:d",
				"P:", "T:lap:e"), keys(m));
		SidebarModel.Item t3 = m.items().get(2);
		assertEquals(3, t3.count());
		assertEquals("VPS, LAP", t3.envs());
		assertEquals(1, m.items().get(6).attention(), "website has the thread that needs you");
	}

	@Test
	void unknownActivitySortsLastAndTiesUseUrgencyThenTitle() {
		SidebarModel m = new SidebarModel();
		m.update(List.of(
				row("e", "1", "b", "p", ThreadState.IDLE, null),
				row("e", "2", "z", "p", ThreadState.IDLE, 5L),
				row("e", "3", "c", "p", ThreadState.ERROR, null),
				row("e", "4", "a", "p", ThreadState.IDLE, null)));
		assertEquals(List.of("e:2", "e:3", "e:4", "e:1"), m.threads().stream().map(ThreadRow::key).toList());
	}

	@Test
	void searchFoldsCaseAndAccentsAndNeedsEveryWord() {
		SidebarModel m = sample();
		m.setQuery("cafe");
		assertEquals(List.of("lap:c"), m.threads().stream().map(ThreadRow::key).toList());
		m.setQuery("LOGIN");
		assertEquals(List.of("vps:a", "lap:d"), m.threads().stream().map(ThreadRow::key).toList());
		m.setQuery("login website");
		assertEquals(List.of("lap:d"), m.threads().stream().map(ThreadRow::key).toList(), "the project counts as text");
		m.setQuery("lap docs");
		assertEquals(List.of("lap:f"), m.threads().stream().map(ThreadRow::key).toList(), "the environment counts as text");
		m.setQuery("nothing here");
		assertTrue(m.items().isEmpty());
		m.setQuery("  ");
		assertFalse(m.searching());
		assertEquals(7, m.threads().size());
	}

	@Test
	void projectFilterCombinesWithSearchAndListsEveryProject() {
		SidebarModel m = sample();
		assertEquals(List.of("t3craft", "website", ""), m.projects().stream().map(SidebarModel.Project::key).toList());
		m.setProject("website");
		assertEquals(List.of("vps:p", "lap:c", "lap:d"), m.threads().stream().map(ThreadRow::key).toList());
		assertEquals("website", m.projectLabel());
		m.setQuery("login");
		assertEquals(List.of("lap:d"), m.threads().stream().map(ThreadRow::key).toList());
		assertEquals(3, m.projects().size(), "the choices ignore the search");
		m.setProject("gone");
		assertNull(m.project());
		assertEquals("All projects", m.projectLabel());
	}

	@Test
	void cycleProjectWrapsThroughAll() {
		SidebarModel m = sample();
		m.cycleProject(1);
		assertEquals("t3craft", m.project());
		m.cycleProject(-1);
		assertNull(m.project());
		m.cycleProject(-1);
		assertEquals(SidebarModel.NO_PROJECT, m.project());
	}

	@Test
	void filterOnAProjectThatDisappearsIsDropped() {
		SidebarModel m = sample();
		m.setProject("website");
		m.update(List.of(row("vps", "a", "Fix login", "t3craft", ThreadState.IDLE, 100L)));
		assertNull(m.project());
		assertEquals(1, m.threads().size());
	}

	@Test
	void collapsedFoldersHideRowsButNotWhileSearching() {
		SidebarModel m = sample();
		m.toggleCollapsed("t3craft");
		assertTrue(m.items().get(2).collapsed());
		assertEquals("P:website", keys(m).get(3));
		m.setQuery("hq");
		assertEquals(List.of("P:t3craft", "T:vps:b"), keys(m));
	}

	@Test
	void moveWalksThreadRowsSkippingHeadersAndCollapsedFolders() {
		SidebarModel m = sample();
		assertTrue(m.move(1));
		assertEquals("vps:p", m.selectedKey());
		m.move(1);
		assertEquals("vps:b", m.selectedKey(), "the folder header is skipped");
		m.toggleCollapsed("website");
		m.move(3);
		assertEquals("lap:e", m.selectedKey(), "collapsed website is skipped");
		assertFalse(m.move(1), "clamped at the end");
		m.move(-100);
		assertEquals("vps:p", m.selectedKey());
		assertEquals(1, m.selectedItem());
	}

	@Test
	void selectionFollowsTheThreadWhenTheListReorders() {
		SidebarModel m = sample();
		m.select("lap:d");
		int before = m.selectedItem();
		m.update(List.of(row("lap", "d", "Login page", "website", ThreadState.DONE, 9999L), row("vps", "a", "Fix login", "t3craft", ThreadState.IDLE, 100L)));
		assertEquals("lap:d", m.selectedKey());
		assertEquals(1, m.selectedItem());
		assertTrue(before != m.selectedItem());
	}

	@Test
	void openingFromABlockSelectsItsThreadAndClearsHidingFilters() {
		SidebarModel m = sample();
		m.setProject("t3craft");
		m.setQuery("hq");
		m.toggleCollapsed("website");
		assertTrue(m.focus("lap:d"));
		assertEquals("lap:d", m.selectedKey());
		assertEquals("", m.query());
		assertNull(m.project());
		assertEquals("lap:d", m.selected().key());
		assertTrue(m.selectedItem() >= 0, "its folder was opened");
		// a thread not listed (archived, filtered by the mod): selected all the same, nothing else touched
		m.setQuery("cafe");
		assertFalse(m.focus("vps:gone"));
		assertEquals("vps:gone", m.selectedKey());
		assertEquals("cafe", m.query());
	}

	@Test
	void selectionBeforeTheFirstFillIsKept() {
		SidebarModel m = new SidebarModel();
		m.select("lap:c");
		m.update(sample().all());
		assertEquals("lap:c", m.selected().key());
	}

	@Test
	void folderDotIsTheMostUrgentState() {
		assertEquals(Attention.NEEDS_YOU, SidebarModel.folderDot(sample().all()));
		assertNull(SidebarModel.folderDot(List.of(row("e", "1", "x", "", ThreadState.DONE, 1L))));
	}
}
