package dev.t3craft.client.t3ui.actions;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Parsing of {@code server.getConfig} providers, the picker logic and the provider cache. */
class ProviderPickerTest {
	static final String CONFIG = """
			{"cwd":"/srv","providers":[
			  {"instanceId":"claudeAgent","driver":"claudeAgent","displayName":"Claude","enabled":true,"installed":true,
			   "showInteractionModeToggle":true,"supportedRuntimeModes":["approval-required","full-access","bogus"],
			   "models":[
			     {"slug":"claude-sonnet-5-5","name":"Claude Sonnet 5.5","shortName":"Sonnet 5.5","isCustom":false,
			      "capabilities":{"optionDescriptors":[
			        {"id":"thinking","label":"Thinking","type":"boolean","currentValue":true},
			        {"id":"effort","label":"Effort","type":"select","options":[
			          {"id":"low","label":"Low"},{"id":"high","label":"High","isDefault":true},{"id":"max","label":"Max"}]}]}},
			     {"slug":"claude-opus-5-5","name":"Claude Opus 5.5","isCustom":false,"isDefault":true,
			      "capabilities":{"optionDescriptors":[
			        {"id":"effort","label":"Effort","type":"select","currentValue":"max","options":[
			          {"id":"high","label":"High"},{"id":"max","label":"Max"}]}]}},
			     {"slug":"claude-opus-5-5","name":"duplicate","isCustom":false,"capabilities":null}]},
			  {"instanceId":"codex","driver":"codex","enabled":true,"installed":true,
			   "models":[{"slug":"gpt-5","name":"GPT-5","isCustom":false,"capabilities":null}]},
			  {"instanceId":"cursor","driver":"cursor","enabled":true,"installed":true,"availability":"unavailable",
			   "unavailableReason":"Sign in","models":[{"slug":"c1","name":"C1","isCustom":false,"capabilities":null}]},
			  {"instanceId":"off","driver":"grok","enabled":false,"installed":true,"models":[{"slug":"g","name":"G","isCustom":false}]},
			  {"driver":"noid","models":[]}
			]}""";

	static ProviderCatalog catalog() {
		return ProviderCatalog.fromServerConfig(JsonParser.parseString(CONFIG).getAsJsonObject());
	}

	@Test
	void parsesProvidersModelsAndEffort() {
		ProviderCatalog c = catalog();
		assertEquals(List.of("claudeAgent", "codex", "cursor", "off"), c.providers().stream().map(ProviderCatalog.Provider::instanceId).toList());
		assertEquals(List.of("claudeAgent", "codex"), c.selectable().stream().map(ProviderCatalog.Provider::instanceId).toList());
		ProviderCatalog.Provider claude = c.provider("claudeAgent");
		assertEquals("Claude", claude.label());
		assertTrue(claude.showInteractionModeToggle());
		assertEquals(List.of(Modes.Runtime.APPROVAL_REQUIRED, Modes.Runtime.FULL_ACCESS), claude.runtimeModes(), "unknown modes dropped");
		assertEquals(2, claude.models().size(), "duplicate slugs dropped");
		assertEquals("claude-opus-5-5", claude.defaultModel().slug(), "isDefault wins over order");
		ProviderCatalog.Model sonnet = claude.model("claude-sonnet-5-5");
		assertEquals("Sonnet 5.5", sonnet.label());
		ProviderCatalog.Effort e = sonnet.effort();
		assertEquals("effort", e.id(), "the first select descriptor (booleans skipped)");
		assertEquals(List.of("low", "high", "max"), e.choices().stream().map(ProviderCatalog.Choice::id).toList());
		assertEquals("high", e.defaultValue());
		assertEquals("max", claude.model("claude-opus-5-5").effort().defaultValue(), "currentValue wins");
		assertNull(c.provider("codex").model("gpt-5").effort());
		assertEquals("codex", c.provider("codex").label(), "no displayName → instance id");
		assertEquals(List.of(Modes.Runtime.values()), c.provider("codex").runtimeModes(), "none reported → all");
		assertEquals("Sign in", c.provider("cursor").unavailableReason());
	}

	@Test
	void toleratesMissingOrBrokenConfig() {
		assertTrue(ProviderCatalog.fromServerConfig(null).isEmpty());
		assertTrue(ProviderCatalog.fromServerConfig(new JsonObject()).isEmpty());
		assertTrue(ProviderCatalog.fromServerConfig(JsonParser.parseString("{\"providers\":[1,\"x\",{}]}").getAsJsonObject()).providers().isEmpty());
	}

	@Test
	void pickerStartsOnFirstProviderDefaultModelAndEffort() {
		ModelPickerLogic l = new ModelPickerLogic(catalog(), null);
		ModelPick p = l.pick();
		assertEquals("claudeAgent", p.instanceId());
		assertEquals("claude-opus-5-5", p.model());
		assertEquals("max", p.effortValue());
		assertEquals(Modes.Runtime.APPROVAL_REQUIRED, p.runtime());
		assertTrue(l.planAvailable());
		assertEquals("Claude · Claude Opus 5.5 · Max", p.label(l.catalog()));
	}

	@Test
	void switchingModelKeepsCompatibleEffortElseDefault() {
		ModelPickerLogic l = new ModelPickerLogic(catalog(), null);
		assertTrue(l.selectEffort("high"));
		assertTrue(l.selectModel("claude-sonnet-5-5"));
		assertEquals("high", l.pick().effortValue(), "sonnet also has high");
		assertTrue(l.selectEffort("low"));
		assertTrue(l.selectModel("claude-opus-5-5"));
		assertEquals("max", l.pick().effortValue(), "opus has no low → its default");
		assertFalse(l.selectEffort("ultra"), "unknown effort refused");
		assertFalse(l.selectModel("nope"));
	}

	@Test
	void switchingProviderClampsModes() {
		ModelPickerLogic l = new ModelPickerLogic(catalog(), null);
		assertTrue(l.selectRuntime(Modes.Runtime.FULL_ACCESS));
		assertFalse(l.selectRuntime(Modes.Runtime.AUTO), "claude does not support auto");
		assertTrue(l.setPlan(true));
		assertTrue(l.selectProvider("codex"));
		ModelPick p = l.pick();
		assertEquals("gpt-5", p.model());
		assertNull(p.effortId());
		assertEquals(Modes.Runtime.FULL_ACCESS, p.runtime(), "codex supports every mode: kept");
		assertEquals(Modes.Interaction.DEFAULT, p.interaction(), "codex has no plan toggle");
		assertFalse(l.setPlan(true));
		assertFalse(l.selectProvider("cursor"), "unavailable providers cannot be picked");
		assertFalse(l.selectProvider("off"));
	}

	@Test
	void cycleRuntimeWalksTheSupportedModes() {
		ModelPickerLogic l = new ModelPickerLogic(catalog(), null);
		l.cycleRuntime();
		assertEquals(Modes.Runtime.FULL_ACCESS, l.pick().runtime());
		l.cycleRuntime();
		assertEquals(Modes.Runtime.APPROVAL_REQUIRED, l.pick().runtime());
	}

	@Test
	void changedOnlyListsWhatDiffersFromTheThread() {
		ModelPick current = new ModelPick("claudeAgent", "claude-opus-5-5", "effort", "max", Modes.Runtime.FULL_ACCESS, Modes.Interaction.DEFAULT);
		ModelPickerLogic l = new ModelPickerLogic(catalog(), current);
		assertEquals(Set.of(), l.changed());
		l.selectEffort("high");
		assertEquals(Set.of(ModelPickerLogic.Dimension.MODEL), l.changed());
		l.selectEffort("max");
		assertEquals(Set.of(), l.changed(), "back to the thread's value: nothing to send");
		l.setPlan(true);
		assertEquals(Set.of(ModelPickerLogic.Dimension.INTERACTION), l.changed());
		l.selectRuntime(Modes.Runtime.APPROVAL_REQUIRED);
		assertEquals(Set.of(ModelPickerLogic.Dimension.RUNTIME, ModelPickerLogic.Dimension.INTERACTION), l.changed());
		l.commit();
		assertEquals(Set.of(), l.changed());
		assertEquals(Modes.Runtime.APPROVAL_REQUIRED, l.initial().runtime());
		l.selectModel("claude-sonnet-5-5");
		l.reset();
		assertEquals("claude-opus-5-5", l.pick().model());
	}

	@Test
	void unknownCurrentModelIsKeptUntilThePlayerPicks() {
		ModelPick current = new ModelPick("legacy", "old-model", null, null, Modes.Runtime.DEFAULT, Modes.Interaction.DEFAULT);
		ModelPickerLogic l = new ModelPickerLogic(catalog(), current);
		assertEquals(current, l.pick());
		assertTrue(l.models().isEmpty());
		assertTrue(l.selectProvider("codex"));
		assertEquals(Set.of(ModelPickerLogic.Dimension.MODEL), l.changed());
	}

	@Test
	void emptyCatalogThenLoaded() {
		ModelPickerLogic l = new ModelPickerLogic(ProviderCatalog.EMPTY, null);
		assertNull(l.pick());
		l.setCatalog(catalog());
		assertEquals("claudeAgent", l.pick().instanceId());
	}

	@Test
	void readsSelectionsInBothOptionShapes() {
		JsonObject thread = JsonParser.parseString("""
				{"modelSelection":{"provider":"codex","model":"gpt-5","options":{"reasoningEffort":"high","fastMode":true}},
				 "runtimeMode":"full-access","interactionMode":"plan"}""").getAsJsonObject();
		ModelPick p = ModelPick.fromThreadJson(thread);
		assertEquals(new ModelPick("codex", "gpt-5", "reasoningEffort", "high", Modes.Runtime.FULL_ACCESS, Modes.Interaction.PLAN), p);
		JsonObject canon = JsonParser.parseString("""
				{"instanceId":"claudeAgent","model":"m","options":[{"id":"thinking","value":true},{"id":"effort","value":"low"}]}""")
				.getAsJsonObject();
		ModelPick q = ModelPick.fromSelection(canon, Modes.Runtime.DEFAULT, Modes.Interaction.DEFAULT);
		assertEquals("effort", q.effortId());
		assertEquals("low", q.effortValue());
		assertNull(ModelPick.fromThreadJson(JsonParser.parseString("{\"modelSelection\":{\"model\":\"m\"}}").getAsJsonObject()));
	}

	@Test
	@SuppressWarnings("unchecked")
	void providerServiceCachesRetriesAndKeepsDefaults() {
		AtomicLong now = new AtomicLong(1000);
		AtomicInteger calls = new AtomicInteger();
		CompletableFuture<JsonObject>[] pending = new CompletableFuture[1];
		ProviderService s = new ProviderService(env -> {
			calls.incrementAndGet();
			if ("offline".equals(env)) {
				return null;
			}
			pending[0] = new CompletableFuture<>();
			return pending[0];
		}, now::get);
		assertEquals(ProviderService.Phase.LOADING, s.catalog("vps").phase());
		s.catalog("vps");
		assertEquals(1, calls.get(), "one fetch in flight at a time");
		pending[0].complete(JsonParser.parseString(CONFIG).getAsJsonObject());
		ProviderService.Entry e = s.catalog("vps");
		assertEquals(ProviderService.Phase.READY, e.phase());
		assertEquals(2, e.catalog().selectable().size());
		now.addAndGet(ProviderService.TTL_MS - 1);
		s.catalog("vps");
		assertEquals(1, calls.get(), "fresh: cached");
		now.addAndGet(1);
		s.catalog("vps");
		assertEquals(2, calls.get(), "stale: refetched");
		pending[0].completeExceptionally(new RuntimeException("boom"));
		ProviderService.Entry failed = s.catalog("vps");
		assertEquals(ProviderService.Phase.FAILED, failed.phase());
		assertEquals(2, failed.catalog().selectable().size(), "last good catalog kept");
		assertEquals(ProviderService.Phase.FAILED, s.catalog("offline").phase());

		assertNull(s.defaultPick("vps"));
		ModelPick p = new ModelPick("codex", "gpt-5", null, null, Modes.Runtime.AUTO, Modes.Interaction.DEFAULT);
		s.setDefaultPick("vps", p);
		assertEquals(p, s.defaultPick("vps"));
		assertNull(s.defaultPick("other"), "defaults are per environment");
	}
}
