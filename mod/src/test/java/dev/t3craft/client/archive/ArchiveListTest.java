package dev.t3craft.client.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The archive screen's list: search, environment / project filters and pages. */
class ArchiveListTest {
	private static ArchiveEntry e(String env, String id, String project, String projectName, String title, long agoH) {
		com.google.gson.JsonObject o = ArchiveFixtures.json(id, project);
		o.addProperty("title", title);
		o.addProperty("settledOverride", "settled");
		o.addProperty("settledAt", java.time.Instant.ofEpochMilli(ArchiveFixtures.NOW - agoH * ArchiveFixtures.H).toString());
		return ArchiveEntry.of(env, env.toUpperCase(), dev.t3craft.t3.ThreadShell.fromJson(o), projectName);
	}

	private static List<ArchiveEntry> sample() {
		List<ArchiveEntry> all = new ArrayList<>(List.of(
				e("vps", "a", "p1", "t3craft", "Fix login redirect", 1),
				e("vps", "b", "p2", "agentcraft", "Bump deps", 2),
				e("mini", "c", "p1", "t3craft", "Login page polish", 3),
				e("mini", "d", "p3", "homelab", "Backup cron", 4)));
		all.sort(ArchiveEntry.NEWEST_FIRST);
		return all;
	}

	private static List<String> ids(List<ArchiveEntry> l) {
		return l.stream().map(ArchiveEntry::threadId).toList();
	}

	@Test
	void searchMatchesEveryWordCaseInsensitiveInTitleProjectOrEnv() {
		ArchiveList l = new ArchiveList();
		l.setText("LOGIN");
		assertEquals(List.of("a", "c"), ids(l.filter(sample())), "order kept: newest close first");
		l.setText("login mini");
		assertEquals(List.of("c"), ids(l.filter(sample())), "every word must match (env label)");
		l.setText("homelab");
		assertEquals(List.of("d"), ids(l.filter(sample())), "project name");
		l.setText("   ");
		assertEquals(4, l.filter(sample()).size(), "blank = everything");
	}

	@Test
	void environmentAndProjectFiltersCycleAndCombine() {
		List<ArchiveEntry> all = sample();
		ArchiveList l = new ArchiveList();
		assertEquals(List.of(new ArchiveList.Option("mini", "MINI"), new ArchiveList.Option("vps", "VPS")), ArchiveList.envs(all));
		l.cycleEnv(all);
		assertEquals("mini", l.env());
		assertEquals(List.of("c", "d"), ids(l.filter(all)));
		// projects offered are only the filtered environment's
		assertEquals(List.of("homelab", "t3craft"), ArchiveList.projects(all, l.env()).stream().map(ArchiveList.Option::label).toList());
		l.cycleProject(all);
		assertEquals("mini:p3", l.project());
		assertEquals(List.of("d"), ids(l.filter(all)));
		l.cycleEnv(all); // → vps: the mini project filter no longer applies
		assertEquals("vps", l.env());
		assertNull(l.project());
		assertEquals(List.of("a", "b"), ids(l.filter(all)));
		l.cycleEnv(all); // → all
		assertNull(l.env());
		assertEquals(4, l.filter(all).size());
		l.setProject("vps:p1");
		l.setText("fix");
		assertEquals(List.of("a"), ids(l.filter(all)), "project and search combine");
	}

	@Test
	void pagesAreClampedAndFiltersGoBackToTheFirstPage() {
		List<ArchiveEntry> all = new ArrayList<>();
		for (int i = 0; i < 23; i++) {
			all.add(e(i % 2 == 0 ? "vps" : "mini", "t" + i, "p", "proj", "Thread " + i, i));
		}
		all.sort(ArchiveEntry.NEWEST_FIRST);
		ArchiveList l = new ArchiveList();
		ArchiveList.Page p = l.page(all, 10);
		assertEquals(3, p.pages());
		assertEquals(23, p.total());
		assertEquals(10, p.rows().size());
		assertEquals("t0", p.rows().getFirst().threadId());
		l.step(1);
		l.step(1);
		p = l.page(all, 10);
		assertEquals(2, p.index());
		assertEquals(3, p.rows().size(), "last page holds the rest");
		l.step(5);
		assertEquals(2, l.page(all, 10).index(), "clamped to the last page");
		l.cycleEnv(all);
		assertEquals(0, l.pageIndex(), "a filter goes back to page 1");
		p = l.page(all, 10);
		assertEquals(11, p.total());
		assertTrue(p.rows().stream().allMatch(x -> x.env().equals("mini")));
		l.step(-3);
		assertEquals(0, l.page(all, 10).index());
		assertEquals(1, new ArchiveList().page(List.of(), 10).pages(), "empty list: one empty page");
	}
}
