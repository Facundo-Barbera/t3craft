package dev.t3craft.client.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.t3craft.client.chat.Attention;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.EnvBadge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** The thread wall's pure logic: columns, order, overflow, hit-test, cache by version, variants, config. */
class ThreadWallTest {
	static final ChatTheme THEME = ChatTheme.warmStudio();
	/** 6 px per character, like the vanilla font's average. */
	static final WallLayout.Measure M = s -> s.length() * 6;
	static final float W = 5 * ThreadBoardRenderer.PPB;
	static final float H = 3 * ThreadBoardRenderer.PPB;

	static WallCard card(String key, Attention a, Long at) {
		return card(key, a, at, "api", false);
	}

	static WallCard card(String key, Attention a, Long at, String project, boolean pinned) {
		String env = key.substring(0, key.indexOf(':'));
		return new WallCard(key, env, env.toUpperCase(), "Thread " + key, project, a, pinned, at, 0, EnvBadge.of(env, env));
	}

	static List<String> keys(WallColumns.Column c) {
		return c.cards().stream().map(WallCard::key).toList();
	}

	// ---- grouping and order ---------------------------------------------------------------------

	@Test
	void statusColumnsAreTheKanban() {
		List<WallCard> cards = List.of(
				card("vps:w", Attention.WORKING, 5L),
				card("vps:n", Attention.NEEDS_YOU, 4L),
				card("vps:p", Attention.PENDING, 3L),
				card("vps:e", Attention.ERROR, 2L),
				card("vps:d", Attention.DONE, 1L),
				card("vps:i", Attention.IDLE, 0L));
		List<WallColumns.Column> cols = WallColumns.group(cards, WallMode.STATUS, THEME, 4);
		assertEquals(List.of("working", "needs_you", "error", "done"), cols.stream().map(WallColumns.Column::id).toList());
		assertEquals(List.of("Working", "Needs you", "Error", "Done"), cols.stream().map(WallColumns.Column::label).toList());
		assertEquals(List.of("vps:w"), keys(cols.get(0)));
		assertEquals(List.of("vps:n", "vps:p"), keys(cols.get(1)), "approval pending sits with needs you, after it");
		assertEquals(List.of("vps:e"), keys(cols.get(2)));
		assertEquals(List.of("vps:d", "vps:i"), keys(cols.get(3)), "idle threads are done");
		assertEquals(THEME.working(), cols.get(0).color());
		assertEquals(THEME.needsYou(), cols.get(1).color());
	}

	@Test
	void emptyStatusColumnsStay() {
		List<WallColumns.Column> cols = WallColumns.group(List.of(), WallMode.STATUS, THEME, 4);
		assertEquals(4, cols.size());
		assertTrue(cols.stream().allMatch(c -> c.count() == 0));
	}

	@Test
	void cardsFollowTheChatOrder() {
		List<WallCard> cards = List.of(
				card("vps:old", Attention.WORKING, 100L),
				card("vps:new", Attention.WORKING, 900L),
				card("vps:unknown", Attention.WORKING, null),
				card("vps:pinned", Attention.WORKING, 1L, "api", true));
		WallColumns.Column working = WallColumns.group(cards, WallMode.STATUS, THEME, 4).get(0);
		assertEquals(List.of("vps:pinned", "vps:new", "vps:old", "vps:unknown"), keys(working),
				"pinned first, then most recent activity, unknown last");
	}

	@Test
	void projectColumnsKeepEnvironmentsApart() {
		List<WallCard> cards = List.of(
				card("vps:a", Attention.WORKING, 1L, "api", false),
				card("mini:b", Attention.ERROR, 1L, "api", false),
				card("vps:c", Attention.DONE, 1L, "web", false),
				card("vps:d", Attention.NEEDS_YOU, 2L, "api", false));
		List<WallColumns.Column> cols = WallColumns.group(cards, WallMode.PROJECT, THEME, 8);
		assertEquals(3, cols.size(), "vps/api, mini/api, vps/web");
		assertEquals(List.of("api", "api", "web"), cols.stream().map(WallColumns.Column::label).toList(), "stable label order");
		WallColumns.Column vpsApi = cols.stream().filter(c -> c.cards().get(0).envId().equals("vps") && c.label().equals("api")).findFirst()
				.orElseThrow();
		assertEquals(List.of("vps:d", "vps:a"), keys(vpsApi), "needs you before working inside a project");
		assertEquals(THEME.needsYou(), vpsApi.color(), "a project column takes its most urgent thread's colour");
	}

	@Test
	void environmentColumnsUseTheBadge() {
		List<WallCard> cards = List.of(card("vps:a", Attention.WORKING, 1L), card("mini:b", Attention.DONE, 1L), card("vps:c", Attention.ERROR, 1L));
		List<WallColumns.Column> cols = WallColumns.group(cards, WallMode.ENVIRONMENT, THEME, 8);
		assertEquals(List.of("MINI", "VPS"), cols.stream().map(WallColumns.Column::label).toList());
		assertEquals(EnvBadge.of("vps", "vps").background(), cols.get(1).color());
		assertEquals(List.of("vps:c", "vps:a"), keys(cols.get(1)));
	}

	@Test
	void tooManyColumnsMergeIntoOther() {
		List<WallCard> cards = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			cards.add(card("vps:t" + i, Attention.WORKING, (long) i, "p" + i, false));
		}
		List<WallColumns.Column> cols = WallColumns.group(cards, WallMode.PROJECT, THEME, 4);
		assertEquals(4, cols.size());
		assertEquals(List.of("p0", "p1", "p2", "Other"), cols.stream().map(WallColumns.Column::label).toList());
		assertEquals(3, cols.get(3).count());
		assertEquals(WallColumns.OTHER, cols.get(3).id());
	}

	// ---- layout, "+N more" and hit-test -----------------------------------------------------------

	@Test
	void moreChipWhenAColumnOverflows() {
		int rows = WallLayout.rows(H);
		assertTrue(rows >= 3, "a 3-block wall holds a few rows: " + rows);
		List<WallCard> cards = new ArrayList<>();
		for (int i = 0; i < rows + 4; i++) {
			cards.add(card("vps:w" + i, Attention.WORKING, 1000L - i));
		}
		cards.add(card("vps:e", Attention.ERROR, 1L));
		WallLayout.Layout l = WallLayout.build(WallColumns.group(cards, WallMode.STATUS, THEME, 4), WallMode.STATUS, W, H, M);
		long working = l.cards().stream().filter(s -> s.column() == 0).count();
		assertEquals(rows - 1, working, "the last row becomes the chip");
		assertEquals(1, l.more().size());
		WallLayout.More more = l.more().get(0);
		assertEquals(5, more.hidden());
		assertEquals("+5 more", more.text());
		assertEquals("vps:w" + (rows - 1), more.firstKey(), "the chip opens the first hidden thread");
		assertEquals(rows + 5, l.total());
		assertEquals(1, l.cards().stream().filter(s -> s.column() == 2).count());
	}

	@Test
	void exactlyFullColumnHasNoChip() {
		int rows = WallLayout.rows(H);
		List<WallCard> cards = new ArrayList<>();
		for (int i = 0; i < rows; i++) {
			cards.add(card("vps:w" + i, Attention.WORKING, (long) i));
		}
		WallLayout.Layout l = WallLayout.build(WallColumns.group(cards, WallMode.STATUS, THEME, 4), WallMode.STATUS, W, H, M);
		assertEquals(rows, l.cards().size());
		assertTrue(l.more().isEmpty());
	}

	@Test
	void cardsStayInsideTheirLane() {
		List<WallCard> cards = List.of(new WallCard("vps:long", "vps", "VPS",
				"A very long thread title that never fits on one card of a small wall", "a-long-project-name", Attention.WORKING, false, 1L,
				3, EnvBadge.of("vps", "vps")));
		WallLayout.Layout l = WallLayout.build(WallColumns.group(cards, WallMode.STATUS, THEME, 4), WallMode.STATUS, W, H, M);
		WallLayout.Slot s = l.cards().get(0);
		WallLayout.Lane lane = l.lanes().get(0);
		assertEquals(lane.x(), s.x());
		assertTrue(s.x() + s.w() <= W - WallLayout.PAD + 0.01f);
		assertTrue(s.title().endsWith(WallLayout.ELLIPSIS));
		assertTrue(M.width(s.title()) <= s.w() - WallLayout.STRIPE - 2 * WallLayout.CARD_PAD);
		assertTrue(M.width(s.footer()) <= s.w() - WallLayout.STRIPE - 2 * WallLayout.CARD_PAD - WallLayout.BADGE - 3);
		assertTrue(WallLayout.footer(cards.get(0), WallMode.STATUS).endsWith("⏳3"), "queued runs on the footer");
	}

	@Test
	void hitTestFindsTheCardOrTheChip() {
		int rows = WallLayout.rows(H);
		List<WallCard> cards = new ArrayList<>();
		for (int i = 0; i < rows + 2; i++) {
			cards.add(card("vps:w" + i, Attention.WORKING, 1000L - i));
		}
		cards.add(card("vps:n", Attention.NEEDS_YOU, 1L));
		WallLayout.Layout l = WallLayout.build(WallColumns.group(cards, WallMode.STATUS, THEME, 4), WallMode.STATUS, W, H, M);
		WallLayout.Slot first = l.cards().get(0);
		WallLayout.Hit h = l.hit(first.x() + 5, first.y() + 5);
		assertNotNull(h);
		assertEquals(WallLayout.HitKind.CARD, h.kind());
		assertEquals("vps:w0", h.key());
		WallLayout.Slot n = l.card("vps:n");
		assertNotNull(n);
		assertEquals("vps:n", l.hit(n.x() + n.w() - 1, n.y() + n.h() - 1).key());
		assertNull(l.hit(first.x() + 5, first.y() + first.h() + 1), "the gap between cards");
		assertNull(l.hit(1, 1), "the trim");
		assertNull(l.hit(first.x() + 5, WallLayout.PAD + 3), "the header");
		WallLayout.More m = l.more().get(0);
		WallLayout.Hit chip = l.hit(m.x() + m.w() / 2, m.y() + m.h() / 2);
		assertNotNull(chip);
		assertEquals(WallLayout.HitKind.MORE, chip.kind());
		assertEquals("vps:w" + (rows - 1), chip.key());
	}

	@Test
	void worldHitsMapToTheSameCardForEveryFacing() {
		List<WallCard> cards = List.of(card("vps:a", Attention.WORKING, 2L), card("vps:b", Attention.DONE, 1L));
		WallBoard b = new WallBoard();
		b.sync(cards, WallMode.STATUS, THEME, W, H, M);
		WallLayout.Slot done = b.layout().card("vps:b");
		assertNotNull(done);
		BlockPos origin = new BlockPos(10, 64, -3);
		float ppb = ThreadBoardRenderer.PPB;
		for (Direction f : Direction.Plane.HORIZONTAL) {
			// world point of the card centre on the face plane
			Direction right = f.getCounterClockWise();
			double u = (done.x() + done.w() / 2) / ppb - 0.5;
			double up = 3 - (done.y() + done.h() / 2) / ppb;
			double x = origin.getX() + 0.5 + right.getStepX() * u + f.getStepX() * 0.5;
			double y = origin.getY() + up;
			double z = origin.getZ() + 0.5 + right.getStepZ() * u + f.getStepZ() * 0.5;
			float[] p = WallGeometry.toBoard(origin, f, 3, ppb, x, y, z);
			assertEquals(done.x() + done.w() / 2, p[0], 0.01, f.toString());
			assertEquals(done.y() + done.h() / 2, p[1], 0.01, f.toString());
			assertEquals("vps:b", b.hit(p[0], p[1]).key(), f.toString());
			assertEquals(0, WallGeometry.offPlane(origin, f, x, z), 1e-9);
			BlockPos cell = BlockPos.containing(x - f.getStepX() * 0.01, y, z - f.getStepZ() * 0.01);
			assertTrue(WallGeometry.onWall(origin, f, 5, 3, cell), f + " " + cell);
			assertFalse(WallGeometry.onWall(origin, f, 5, 3, origin.relative(right.getOpposite())), "left of the anchor");
			assertFalse(WallGeometry.onWall(origin, f, 5, 3, origin.relative(right, 5)), "past the right edge");
			assertFalse(WallGeometry.onWall(origin, f, 5, 3, origin.above(3)), "above the top");
			assertFalse(WallGeometry.onWall(origin, f, 5, 3, origin.relative(f)), "in front of the wall");
		}
	}

	// ---- cache by version and animation -----------------------------------------------------------

	@Test
	void rebuildsOnlyWhenSomethingChanged() {
		WallBoard b = new WallBoard();
		List<WallCard> cards = List.of(card("vps:a", Attention.WORKING, 2L), card("vps:b", Attention.DONE, 1L));
		assertTrue(b.sync(cards, WallMode.STATUS, THEME, W, H, M));
		long v = b.version();
		WallLayout.Layout l = b.layout();
		assertFalse(b.sync(cards, WallMode.STATUS, THEME, W, H, M), "same list");
		assertFalse(b.sync(new ArrayList<>(List.of(card("vps:a", Attention.WORKING, 2L), card("vps:b", Attention.DONE, 1L))),
				WallMode.STATUS, THEME, W, H, M), "an equal list (new objects) is no change");
		assertEquals(v, b.version());
		assertTrue(l == b.layout(), "cached layout kept");
		assertTrue(b.sync(List.of(card("vps:a", Attention.ERROR, 2L), card("vps:b", Attention.DONE, 1L)), WallMode.STATUS, THEME, W, H, M));
		assertNotEquals(v, b.version());
		long v2 = b.version();
		assertTrue(b.sync(List.of(card("vps:a", Attention.ERROR, 2L), card("vps:b", Attention.DONE, 1L)), WallMode.PROJECT, THEME, W, H, M),
				"mode change rebuilds");
		assertTrue(b.sync(List.of(card("vps:a", Attention.ERROR, 2L), card("vps:b", Attention.DONE, 1L)), WallMode.PROJECT, THEME, W, 2 * H, M),
				"size change rebuilds");
		assertEquals(v2 + 2, b.version());
		assertEquals(4, b.rebuilds());
	}

	@Test
	void aCardThatChangesColumnFliesAndGlows() {
		WallBoard b = new WallBoard();
		b.sync(List.of(card("vps:a", Attention.WORKING, 2L)), WallMode.STATUS, THEME, W, H, M);
		WallBoard.Anim a = b.anim("vps:a");
		assertNotNull(a);
		assertFalse(a.flying, "a new card appears in place");
		float x0 = a.x;
		b.sync(List.of(card("vps:a", Attention.ERROR, 2L)), WallMode.STATUS, THEME, W, H, M);
		WallLayout.Slot target = b.layout().card("vps:a");
		assertTrue(a == b.anim("vps:a"), "same card animates");
		assertTrue(a.flying);
		assertEquals(x0, a.x, "it starts where it was");
		assertTrue(b.advance(0.05f));
		assertTrue(a.x > x0 && a.x < target.x(), "half way: " + a.x);
		for (int i = 0; i < 40 && a.flying; i++) {
			b.advance(0.05f);
		}
		assertFalse(a.flying);
		assertEquals(target.x(), a.x);
		assertTrue(a.glow > 0, "glows where it lands");
		for (int i = 0; i < 40; i++) {
			b.advance(0.05f);
		}
		assertEquals(0, a.glow);
		assertFalse(b.advance(0.05f), "settled: nothing moves");
	}

	@Test
	void goneCardsAreDropped() {
		WallBoard b = new WallBoard();
		b.sync(List.of(card("vps:a", Attention.WORKING, 2L), card("vps:b", Attention.WORKING, 1L)), WallMode.STATUS, THEME, W, H, M);
		b.sync(List.of(card("vps:b", Attention.WORKING, 1L)), WallMode.STATUS, THEME, W, H, M);
		assertNull(b.anim("vps:a"));
		assertNotNull(b.anim("vps:b"));
		assertTrue(b.anim("vps:b").y > b.layout().card("vps:b").y() - 0.01f, "b glides up into a's slot");
	}

	// ---- variants and config ----------------------------------------------------------------------

	@Test
	void modeParsing() {
		assertEquals(WallMode.PROJECT, WallMode.parse("project", WallMode.STATUS));
		assertEquals(WallMode.ENVIRONMENT, WallMode.parse(" Env ", WallMode.STATUS));
		assertEquals(WallMode.STATUS, WallMode.parse("kanban", WallMode.PROJECT));
		assertEquals(WallMode.PROJECT, WallMode.parse("nonsense", WallMode.PROJECT));
		assertEquals(WallMode.STATUS, WallMode.parse(null, WallMode.STATUS));
	}

	@Test
	void footerDependsOnTheMode() {
		WallCard c = card("vps:a", Attention.ERROR, 1L, "api", false);
		assertEquals("api", WallLayout.footer(c, WallMode.STATUS));
		assertEquals("error", WallLayout.footer(c, WallMode.PROJECT));
		assertEquals("error · api", WallLayout.footer(c, WallMode.ENVIRONMENT));
	}

	@Test
	void configIsAdditiveAndTolerant() {
		assertEquals(ThreadWallConfig.DEFAULT, ThreadWallConfig.resolve(null));
		assertEquals(ThreadWallConfig.DEFAULT, ThreadWallConfig.resolve(JsonParser.parseString("{\"blocks\": {\"enabled\": true}}").getAsJsonObject()));
		ThreadWallConfig c = ThreadWallConfig.resolve(JsonParser.parseString(
				"{\"blocks\": {\"threadWall\": {\"width\": 7, \"height\": \"tall\", \"columns\": \"environment\", \"viewDistance\": 500}}}")
				.getAsJsonObject());
		assertEquals(7, c.width());
		assertEquals(3, c.height(), "wrong type → default");
		assertEquals(WallMode.ENVIRONMENT, c.mode());
		assertEquals(128, c.viewDistance(), "clamped");
		assertEquals(24, c.detailDistance());
		ThreadWallConfig.Wall own = c.of(4, 2, "project");
		assertEquals(new ThreadWallConfig.Wall(4, 2, WallMode.PROJECT), own, "the anchor's own data wins");
		assertEquals(new ThreadWallConfig.Wall(7, 3, WallMode.ENVIRONMENT), c.of(0, 0, ""), "else the config");
	}

	@Test
	void lodByDistance() {
		assertEquals(WallGeometry.Lod.FULL, WallGeometry.Lod.of(10 * 10, 24, 64));
		assertEquals(WallGeometry.Lod.SIMPLE, WallGeometry.Lod.of(30 * 30, 24, 64));
		assertEquals(WallGeometry.Lod.HIDDEN, WallGeometry.Lod.of(70 * 70, 24, 64));
	}

	@Test
	void clipKeepsTheStartAndAddsAnEllipsis() {
		assertEquals("abc", WallLayout.clip("abc", 18, M));
		assertEquals("abc…", WallLayout.clip("abcdefgh", 24, M));
		assertEquals("", WallLayout.clip("abc", 3, M));
		assertEquals("", WallLayout.clip("", 30, M));
	}
}
