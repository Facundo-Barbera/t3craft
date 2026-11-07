package dev.t3craft.client.workstation;

import static org.junit.jupiter.api.Assertions.*;

import dev.t3craft.client.npc.NpcInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkstationModelTest {
	static WorkstationThread thread(String title, StationStatus s, String activity) {
		return new WorkstationThread("vps:t1", "vps", "t1", title, "vps:p1", 2, s, activity, null);
	}

	private final WorkstationModel model = new WorkstationModel(new WorkstationModel.Layout(20, 2));

	@Test
	void titleStatusActivityInColour() {
		StationScreen s = model.render(thread("Fix login", StationStatus.WORKING, "Running tests"), "3m");
		assertEquals(List.of(
				new StationScreen.Line("Fix login", WorkstationModel.TITLE_COLOR),
				new StationScreen.Line("⚙ WORKING · 3m", StationStatus.WORKING.color),
				new StationScreen.Line("Running tests", WorkstationModel.ACTIVITY_COLOR)), s.lines());
		assertEquals(StationStatus.WORKING, s.status());
		assertEquals(StationStatus.WORKING.background(), s.background());
	}

	@Test
	void unknownAgeHasNoSuffix() {
		assertEquals("! NEEDS YOU", model.render(thread("x", StationStatus.ATTENTION, "a"), null).lines().get(1).text());
	}

	@Test
	void longTitleClippedAndActivityWrapped() {
		StationScreen s = model.render(thread("A very long thread title that does not fit", StationStatus.ERROR,
				"Build failed: compileClientJava could not resolve symbol SwingAnimation in WorkstationClient"), "now");
		assertEquals(4, s.lines().size()); // title + status + 2 activity lines
		s.lines().forEach(l -> assertTrue(l.text().length() <= 20, l.text()));
		assertTrue(s.lines().get(0).text().endsWith("…"));
		assertTrue(s.lines().get(3).text().endsWith("…"));
		assertEquals(StationStatus.ERROR.color, s.lines().get(1).color());
	}

	@Test
	void missingActivityAndTitle() {
		StationScreen s = model.render(thread(" ", StationStatus.IDLE, null), null);
		assertEquals("(untitled)", s.lines().get(0).text());
		assertEquals(new StationScreen.Line(WorkstationModel.NO_ACTIVITY, WorkstationModel.DIM_COLOR), s.lines().get(2));
	}

	@Test
	void archivedIsDimAndHasNoActivity() {
		StationScreen s = model.render(thread("Old thread", StationStatus.ARCHIVED, "whatever"), "2d");
		assertEquals(2, s.lines().size());
		assertEquals(WorkstationModel.DIM_COLOR, s.lines().get(0).color());
		assertEquals("▣ ARCHIVED · 2d", s.lines().get(1).text());
	}

	@Test
	void plainJoinsLines() {
		assertEquals("t\n✔ DONE\nok", model.render(thread("t", StationStatus.DONE, "ok"), null).plain());
	}

	@Test
	void deterministic() {
		WorkstationThread t = thread("t", StationStatus.WORKING, "a b c");
		assertEquals(model.render(t, "5m"), model.render(t, "5m"));
	}

	@Test
	void fromNpcSplitsIdAndMapsState() {
		NpcInfo n = new NpcInfo("vps:abc:def", "Title", "⚙ Title", "VPS", "VPS · proj", 0x55FFFF, "act", 2, false, "working", "vps:p",
				"proj", "vps/working", "working");
		WorkstationThread t = WorkstationThread.fromNpc(n);
		assertEquals("vps:abc:def", t.key());
		assertEquals("vps", t.envId());
		assertEquals("abc:def", t.threadId());
		assertEquals(StationStatus.WORKING, t.status());
		assertEquals("act", t.activity());
		assertEquals("vps:p", t.groupId());

		NpcInfo bridge = new NpcInfo("t9", "T", "T", "Home", "", 0, null, 4, true, "done", "g", "g", "s", null);
		WorkstationThread b = WorkstationThread.fromNpc(bridge);
		assertEquals("Home", b.envId());
		assertEquals("t9", b.threadId());
		assertTrue(b.archived());
	}

	@Test
	void ageFormat() {
		assertEquals("now", AgeFormat.format(0));
		assertEquals("now", AgeFormat.format(-5000));
		assertEquals("45s", AgeFormat.format(45_000));
		assertEquals("12m", AgeFormat.format(12 * 60_000 + 59_000));
		assertEquals("3h", AgeFormat.format(3 * 3_600_000L + 1));
		assertEquals("47h", AgeFormat.format(47 * 3_600_000L));
		assertEquals("2d", AgeFormat.format(48 * 3_600_000L));
	}
}
