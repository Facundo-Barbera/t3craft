package dev.t3craft.t3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class ChipsTest {
	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	// ---- decoding ----------------------------------------------------------------------------

	@Test
	void decodesCurrentShellFields() {
		ThreadShell t = ThreadShell.fromJson(json("""
				{"id":"t1","providerInstanceId":"claudeAgent",
				 "modelSelection":{"instanceId":"claudeAgent","model":"claude-opus-5-5","options":{"effort":"high"}},
				 "branch":"feat/panel-chips","worktreePath":"/home/me/.t3/worktrees/t3craft/feat-panel-chips"}"""));
		ThreadMeta m = t.meta();
		assertEquals("claudeAgent", m.instanceId());
		assertEquals("claude-opus-5-5", m.model());
		assertEquals("feat/panel-chips", m.branch());
		assertEquals("/home/me/.t3/worktrees/t3craft/feat-panel-chips", m.worktreePath());
	}

	@Test
	void missingFieldsGiveEmptyMetaAndNoChips() {
		ThreadShell t = ThreadShell.fromJson(json("{\"id\":\"t1\"}"));
		assertEquals(ThreadMeta.EMPTY, t.meta());
		assertNull(Chips.model(t.meta()));
		assertNull(Chips.provider(t.meta()));
		assertNull(Chips.branch(t.meta()));
		assertNull(Chips.worktree(t.meta().worktreePath()));
		NpcView v = Presentation.present("vps", "VPS", t, null);
		assertNull(v.model());
		assertNull(v.branch());
		assertNull(v.worktree());
	}

	@Test
	void nullsAndBlanksAndWrongTypesAreIgnored() {
		ThreadMeta m = ThreadMeta.fromJson(json("""
				{"modelSelection":null,"branch":null,"worktreePath":"  ","providerInstanceId":7}"""));
		assertNull(m.model());
		assertNull(m.branch());
		assertNull(m.worktreePath());
		assertEquals(ThreadMeta.EMPTY, ThreadMeta.fromJson(json("{\"modelSelection\":[1,2]}")));
		assertEquals(ThreadMeta.EMPTY, ThreadMeta.fromJson(null));
	}

	@Test
	void legacyAndAliasSpellings() {
		ThreadMeta legacy = ThreadMeta.fromJson(json("""
				{"modelSelection":{"provider":"codex","model":"gpt-5-codex"},"gitBranch":"main","worktree":"/srv/repo"}"""));
		assertEquals("codex", legacy.providerKey());
		assertEquals("main", legacy.branch());
		assertEquals("/srv/repo", legacy.worktreePath());
		ThreadMeta flat = ThreadMeta.fromJson(json("{\"model\":\"o3\",\"provider\":\"codex\"}"));
		assertEquals("o3", flat.model());
		assertEquals("codex", flat.providerKey());
		assertEquals("sonnet 4.5", Chips.model(ThreadMeta.fromJson(json("{\"modelSelection\":\"claude-sonnet-4-5\"}"))));
	}

	@Test
	void presentationCarriesChips() {
		ThreadShell t = ThreadShell.fromJson(json("""
				{"id":"t1","title":"Fix","modelSelection":{"instanceId":"claudeAgent","model":"claude-opus-5-5"},
				 "branch":"feat/x","worktreePath":"/home/me/wt/feat-x"}"""));
		NpcView v = Presentation.present("vps", "VPS", t, "proj");
		assertEquals("opus 5.5", v.model());
		assertEquals("claude", v.provider());
		assertEquals("feat/x", v.branch());
		assertEquals("feat-x", v.worktree());
	}

	// ---- abbreviators ------------------------------------------------------------------------

	@Test
	void abbreviatesModels() {
		assertEquals("opus 5.5", Chips.abbreviateModel("claude-opus-5-5"));
		assertEquals("sonnet 4.5", Chips.abbreviateModel("claude-sonnet-4-5-20250929"));
		assertEquals("sonnet 3.5", Chips.abbreviateModel("claude-3-5-sonnet-latest"));
		assertEquals("haiku 5.5", Chips.abbreviateModel("claude-haiku-5-5[1m]"));
		assertEquals("fable 5.1", Chips.abbreviateModel("Claude-Fable-5-1"));
		assertEquals("sonnet 3.5", Chips.abbreviateModel("anthropic.claude-3-5-sonnet-20241022-v2:0"));
		assertEquals("opus 4.1", Chips.abbreviateModel("anthropic/claude-opus-4-1"));
		assertEquals("opus", Chips.abbreviateModel("opus"));
		assertEquals("codex 5", Chips.abbreviateModel("gpt-5-codex"));
		assertEquals("codex 5.1 max", Chips.abbreviateModel("gpt-5.1-codex-max"));
		assertEquals("gpt 5", Chips.abbreviateModel("gpt-5"));
		assertEquals("gpt 5 mini", Chips.abbreviateModel("openai/gpt-5-mini"));
		assertEquals("o3", Chips.abbreviateModel("o3"));
		assertEquals("composer 1", Chips.abbreviateModel("composer-1"));
		assertNull(Chips.abbreviateModel(null));
		assertNull(Chips.abbreviateModel("  "));
	}

	@Test
	void longUnknownModelsAreClipped() {
		String s = Chips.abbreviateModel("some-very-long-experimental-model-name");
		assertEquals(Chips.MAX_MODEL, s.length());
	}

	@Test
	void providerFallbackAndDedup() {
		ThreadMeta codexOnly = new ThreadMeta(null, "codex", null, null, null);
		assertEquals("codex", Chips.model(codexOnly));
		assertNull(Chips.provider(codexOnly), "provider not repeated next to itself");
		ThreadMeta codex = new ThreadMeta(null, "codex", "gpt-5-codex", null, null);
		assertEquals("codex 5", Chips.model(codex));
		assertNull(Chips.provider(codex));
		ThreadMeta custom = new ThreadMeta(null, "codex-work", "gpt-5", null, null);
		assertEquals("codex-work", Chips.provider(custom));
		assertEquals("claude", Chips.providerLabel("claudeAgent"));
		assertNull(Chips.providerLabel(null));
	}

	@Test
	void abbreviatesWorktrees() {
		assertEquals("feat-panel-chips", Chips.worktree("/home/me/.t3/worktrees/t3craft/feat-panel-chips"));
		assertEquals("repo", Chips.worktree("/srv/repo/"));
		assertEquals("~", Chips.worktree("/home/me"));
		assertEquals("~", Chips.worktree("/Users/me/"));
		assertEquals("~", Chips.worktree("/root"));
		assertEquals("~", Chips.worktree("C:\\Users\\me"));
		assertEquals("proj", Chips.worktree("C:\\Users\\me\\proj"));
		assertEquals("/", Chips.worktree("/"));
		assertNull(Chips.worktree(null));
		assertNull(Chips.worktree(""));
	}

	// ---- formatting --------------------------------------------------------------------------

	@Test
	void formatsLines() {
		assertEquals("◆ opus 5.5 · claude", Chips.modelLine("opus 5.5", "claude"));
		assertEquals("◆ codex", Chips.modelLine("codex", null));
		assertNull(Chips.modelLine(null, "claude"));
		assertEquals("⎇ feat/x · repo", Chips.branchLine("feat/x", "repo"));
		assertEquals("⎇ feat/x", Chips.branchLine("feat/x", "feat-x"));
		assertEquals("⎇ main", Chips.branchLine("main", null));
		assertEquals("▣ repo", Chips.branchLine(null, "repo"));
		assertNull(Chips.branchLine(null, null));
	}
}
