package dev.t3craft.client.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.client.workstation.StationScreen;
import dev.t3craft.client.workstation.StationStatus;
import dev.t3craft.client.workstation.WorkstationModel;
import dev.t3craft.client.workstation.WorkstationThread;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NameplateChipsTest {
	private static NpcInfo npc(String model, String provider, String branch, String worktree) {
		return new NpcInfo("vps:t1", "Fix", "⚙ Fix", "VPS", "VPS · proj", 0x55FFFF, "act", 2, false, "working", "vps:p", "proj",
				"vps/working", "working", model, provider, branch, worktree);
	}

	@Test
	void defaultsShowModelOnNameplateAndBranchOnlyInPanel() {
		NpcInfo n = npc("opus 5.5", "claude", "feat/x", "repo");
		assertEquals("[opus 5.5]", Nameplate.nameplateChips(n, ChipsConfig.DEFAULTS));
		assertEquals("◆ opus 5.5 · claude   ⎇ feat/x · repo", Nameplate.panelChips(n, ChipsConfig.DEFAULTS));
	}

	@Test
	void configTogglesEachChip() {
		NpcInfo n = npc("opus 5.5", null, "feat/x", null);
		ChipsConfig all = new ChipsConfig(true, true, true, true, true);
		assertEquals("[opus 5.5] ⎇ feat/x", Nameplate.nameplateChips(n, all));
		ChipsConfig none = new ChipsConfig(false, false, false, false, false);
		assertNull(Nameplate.nameplateChips(n, none));
		assertNull(Nameplate.panelChips(n, none));
		assertEquals("⎇ feat/x", Nameplate.panelChips(n, new ChipsConfig(true, false, false, true, true)));
	}

	@Test
	void nothingKnownNothingShown() {
		NpcInfo legacy = new NpcInfo("t", "T", "T", "", "", 0, null, 9, false, "idle", "", "", "", null);
		assertNull(Nameplate.nameplateChips(legacy, ChipsConfig.DEFAULTS));
		assertNull(Nameplate.panelChips(legacy, ChipsConfig.DEFAULTS));
	}

	@Test
	void bridgeJsonChipsAreOptional() {
		JsonObject with = JsonParser.parseString("{\"id\":\"a\",\"model\":\"codex 5\",\"branch\":\"main\",\"worktree\":\"\"}").getAsJsonObject();
		NpcInfo n = NpcInfo.fromJson(with);
		assertEquals("codex 5", n.model());
		assertEquals("main", n.branch());
		assertNull(n.worktree());
		NpcInfo bare = NpcInfo.fromJson(JsonParser.parseString("{\"id\":\"a\"}").getAsJsonObject());
		assertNull(bare.model());
		assertNull(bare.provider());
	}

	@Test
	void configParsingIsTolerant() {
		assertEquals(ChipsConfig.DEFAULTS, ChipsConfig.parse(null));
		assertEquals(ChipsConfig.DEFAULTS, ChipsConfig.parse(new JsonObject()));
		assertEquals(ChipsConfig.DEFAULTS, ChipsConfig.parse(JsonParser.parseString("{\"chips\": 3}").getAsJsonObject()));
		ChipsConfig c = ChipsConfig.parse(JsonParser.parseString(
				"{\"chips\": {\"modelNameplate\": false, \"branchNameplate\": true, \"branchPanel\": \"no\"}}").getAsJsonObject());
		assertFalse(c.modelNameplate());
		assertTrue(c.modelPanel());
		assertTrue(c.branchNameplate());
		assertTrue(c.branchPanel());
		assertTrue(c.branchWorkstation());
	}

	@Test
	void loadsFromFile(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("t3craft.json");
		assertEquals(ChipsConfig.DEFAULTS, ChipsConfig.load(f));
		Files.writeString(f, "{\"other\":1,\"chips\":{\"branchWorkstation\":false}}");
		assertFalse(ChipsConfig.load(f).branchWorkstation());
		Files.writeString(f, "not json{");
		assertEquals(ChipsConfig.DEFAULTS, ChipsConfig.load(f));
	}

	@Test
	void workstationShowsBranchLineWhenEnabled() {
		NpcInfo n = npc("opus 5.5", "claude", "feat/x", "repo");
		WorkstationModel model = new WorkstationModel();
		StationScreen on = model.render(WorkstationThread.fromNpc(n, true), "3m");
		assertEquals("⎇ feat/x", on.lines().get(2).text());
		StationScreen off = model.render(WorkstationThread.fromNpc(n, false), "3m");
		assertEquals("act", off.lines().get(2).text());
		WorkstationThread noBranch = new WorkstationThread("k", "e", "t", "T", "g", 0, StationStatus.WORKING, "act", null);
		assertEquals("act", model.render(noBranch, null).lines().get(2).text());
	}
}
