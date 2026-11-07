package dev.t3craft.client.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.client.chat.ChatEntry.Kind;
import dev.t3craft.t3.ThreadState;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Stream items shaped like T3's OrchestrationV2ThreadStreamItem / ThreadHistoryPage JSON. */
class ChatTranscriptTest {
	private static final String T = "thread-1";

	private static JsonObject json(String s) {
		return JsonParser.parseString(s.replace('\'', '"')).getAsJsonObject();
	}

	private static String item(String id, String type, int ordinal, String extra) {
		return "{'id':'" + id + "','threadId':'" + T + "','type':'" + type + "','ordinal':" + ordinal
				+ ",'status':'completed','title':null,'updatedAt':'2026-11-07T10:00:00Z'" + (extra.isEmpty() ? "" : "," + extra) + "}";
	}

	private static String row(String source, String visibility, String item) {
		return "{'position':0,'visibility':'" + visibility + "','sourceThreadId':'" + source + "','sourceItemId':'x','item':" + item + "}";
	}

	private static JsonObject snapshot(String cursor, String... rows) {
		return json("{'kind':'snapshot','snapshotSequence':10,'historyCursor':" + (cursor == null ? "null" : "'" + cursor + "'")
				+ ",'hasMoreHistory':" + (cursor != null) + ",'projection':{'thread':{'id':'" + T + "','title':'Fix the build'},"
				+ "'runs':[{'id':'r1','ordinal':1,'status':'completed'}],'runtimeRequests':[],'nodes':[],'subagents':[],"
				+ "'turnItems':[],'visibleTurnItems':[" + String.join(",", rows) + "]}}");
	}

	private static JsonObject event(long seq, String type, String payload) {
		return json("{'kind':'event','sequence':" + seq + ",'event':{'id':'e" + seq + "','threadId':'" + T + "','type':'" + type
				+ "','occurredAt':'2026-11-07T10:00:00Z','payload':" + payload + "}}");
	}

	private static List<String> keys(ChatTranscript t) {
		return t.view().entries().stream().map(ChatEntry::itemId).toList();
	}

	private ChatTranscript seeded() {
		ChatTranscript t = new ChatTranscript(T);
		assertTrue(t.applyStreamItem(snapshot("cur-1",
				row(T, "local", item("u1", "user_message", 1, "'messageId':'m1','text':'Hi **there**','attachments':[]")),
				row(T, "local", item("a1", "assistant_message", 2, "'messageId':'m2','text':'Hello','streaming':false")))));
		return t;
	}

	@Test
	void snapshotFillsEntriesTitleCursorAndStatus() {
		ChatTranscript t = seeded();
		ChatTranscript.View v = t.view();
		assertEquals("Fix the build", v.title());
		assertEquals(List.of("u1", "a1"), keys(t));
		assertEquals(Kind.USER, v.entries().get(0).kind());
		assertEquals("Hi **there**", v.entries().get(0).body());
		assertTrue(v.hasMoreHistory());
		assertEquals("cur-1", t.historyCursor());
		assertEquals(10, t.sequence());
		assertEquals(ThreadState.DONE, v.status().state());
		assertNull(v.status().needs());
		assertFalse(v.synced());
		assertTrue(t.applyStreamItem(json("{'kind':'synchronized'}")));
		assertTrue(t.view().synced());
	}

	@Test
	void liveTurnItemsUpsertInPlaceAndAppendNew() {
		ChatTranscript t = seeded();
		long v0 = t.view().version();
		assertTrue(t.applyStreamItem(event(11, "turn-item.updated", item("a1", "assistant_message", 2, "'messageId':'m2','text':'Hello world','streaming':true"))));
		assertEquals(List.of("u1", "a1"), keys(t));
		assertEquals("Hello world", t.view().entries().get(1).body());
		assertTrue(t.view().entries().get(1).streaming());
		assertTrue(t.view().version() > v0);
		assertTrue(t.applyStreamItem(event(12, "turn-item.updated", item("c1", "command_execution", 3, "'input':'ls -la','output':'total 0','exitCode':0"))));
		assertEquals(List.of("u1", "a1", "c1"), keys(t));
		ChatEntry cmd = t.view().entries().get(2);
		assertEquals(Kind.TOOL, cmd.kind());
		assertEquals("$ ls -la  [exit 0]", cmd.title());
		assertEquals("total 0", cmd.detail());
		assertTrue(cmd.kind().foldable);
	}

	@Test
	void outOfOrderItemIsPlacedByOrdinal() {
		ChatTranscript t = seeded();
		t.applyStreamItem(event(11, "turn-item.updated", item("late", "reasoning", 5, "'text':'hmm','streaming':false")));
		t.applyStreamItem(event(12, "turn-item.updated", item("mid", "reasoning", 3, "'text':'mid','streaming':false")));
		assertEquals(List.of("u1", "a1", "mid", "late"), keys(t));
	}

	@Test
	void replayedOrIdenticalEventsAreIgnored() {
		ChatTranscript t = seeded();
		assertFalse(t.applyStreamItem(event(9, "turn-item.updated", item("zz", "reasoning", 9, "'text':'old','streaming':false"))));
		assertEquals(List.of("u1", "a1"), keys(t));
		String same = item("a1", "assistant_message", 2, "'messageId':'m2','text':'Hello','streaming':false");
		assertFalse(t.applyStreamItem(event(11, "turn-item.updated", same)));
	}

	@Test
	void messageUpdatedStreamsIntoTheMatchingRow() {
		ChatTranscript t = seeded();
		assertTrue(t.applyStreamItem(event(11, "message.updated", "{'id':'m2','role':'assistant','text':'Hello, streaming','streaming':true}")));
		assertEquals("Hello, streaming", t.view().entries().get(1).body());
		assertFalse(t.applyStreamItem(event(12, "message.updated", "{'id':'unknown','role':'assistant','text':'x','streaming':false}")));
	}

	@Test
	void olderPageIsPrependedDedupedAndStaleCursorIgnored() {
		ChatTranscript t = seeded();
		JsonObject page = json("{'snapshotSequence':10,'nextCursor':'cur-0','hasMoreHistory':true,'items':["
				+ row(T, "local", item("old1", "user_message", 0, "'messageId':'m0','text':'first','attachments':[]")) + ","
				+ row(T, "local", item("u1", "user_message", 1, "'messageId':'m1','text':'dup','attachments':[]")) + "]}");
		assertFalse(t.applyHistoryPage(page, "wrong-cursor"));
		assertTrue(t.applyHistoryPage(page, "cur-1"));
		assertEquals(List.of("old1", "u1", "a1"), keys(t));
		assertEquals("Hi **there**", t.view().entries().get(1).body()); // kept the live row
		assertEquals("cur-0", t.historyCursor());
		JsonObject last = json("{'snapshotSequence':10,'nextCursor':null,'hasMoreHistory':false,'items':[]}");
		assertTrue(t.applyHistoryPage(last, "cur-0"));
		assertNull(t.historyCursor());
		assertFalse(t.view().hasMoreHistory());
	}

	@Test
	void newSnapshotReplacesTheWindow() {
		ChatTranscript t = seeded();
		t.applyStreamItem(snapshot(null, row(T, "local", item("n1", "assistant_message", 7, "'messageId':'m7','text':'fresh','streaming':false"))));
		assertEquals(List.of("n1"), keys(t));
		assertNull(t.historyCursor());
	}

	@Test
	void inheritedRowsAreMarked() {
		ChatTranscript t = new ChatTranscript(T);
		t.applyStreamItem(snapshot(null, row("parent", "inherited", item("p1", "user_message", 1, "'messageId':'pm','text':'from parent','attachments':[]"))));
		ChatEntry e = t.view().entries().getFirst();
		assertTrue(e.inherited());
		assertEquals("parent:p1", e.key());
	}

	@Test
	void pendingRequestSaysWhoNeedsWhat() {
		ChatTranscript t = seeded();
		t.applyStreamItem(event(11, "subagent.updated", "{'id':'node-7','title':'Researcher','prompt':'look things up'}"));
		t.applyStreamItem(event(12, "runtime-request.updated",
				"{'id':'req1','nodeId':'node-7','kind':'command','status':'pending','createdAt':'2026-11-07T10:00:00Z'}"));
		ChatTranscript.Status s = t.view().status();
		assertEquals(ThreadState.NEEDS_ATTENTION, s.state());
		assertNotNull(s.needs());
		assertTrue(s.needs().startsWith("You: approve command, asked by subagent \"Researcher\""), s.needs());
		t.applyStreamItem(event(13, "runtime-request.updated", "{'id':'req1','nodeId':'node-7','kind':'command','status':'resolved'}"));
		assertEquals(ThreadState.DONE, t.view().status().state());
	}

	@Test
	void userInputRequestFromTheThreadItself() {
		ChatTranscript t = seeded();
		t.applyStreamItem(event(11, "runtime-request.updated", "{'id':'q','nodeId':'root','kind':'user_input','status':'pending'}"));
		assertEquals("You: answer a question, asked by this thread's agent", t.view().status().needs());
	}

	@Test
	void runStatusDrivesState() {
		ChatTranscript t = seeded();
		t.applyStreamItem(event(11, "run.created", "{'id':'r2','ordinal':2,'status':'running'}"));
		assertEquals(ThreadState.WORKING, t.view().status().state());
		t.applyStreamItem(event(12, "run.updated", "{'id':'r2','ordinal':2,'status':'failed'}"));
		assertEquals(ThreadState.ERROR, t.view().status().state());
		t.applyStreamItem(event(13, "thread.metadata-updated", "{'id':'" + T + "','title':'Renamed'}"));
		assertEquals("Renamed", t.view().title());
	}

	@Test
	void unknownEventsAdvanceSequenceOnly() {
		ChatTranscript t = seeded();
		assertFalse(t.applyStreamItem(json("{'kind':'unknown-event','sequence':40,'eventType':'future.thing'}")));
		assertEquals(40, t.sequence());
		assertFalse(t.applyStreamItem(event(41, "checkpoint.captured", "{'id':'c'}")));
	}

	@Test
	void turnItemTypesDecode() {
		assertEquals(Kind.TOOL, TurnItems.decode(json(item("f", "file_change", 1, "'fileName':'a.java','additions':3,'deletions':1,'diffStr':'@@'")), T, false).kind());
		assertEquals("Edit a.java (+3 −1)", TurnItems.decode(json(item("f", "file_change", 1, "'fileName':'a.java','additions':3,'deletions':1")), T, false).title());
		ChatEntry todo = TurnItems.decode(json(item("t", "todo_list", 1,
				"'planId':'p','steps':[{'id':'1','text':'one','status':'completed'},{'id':'2','text':'two','status':'running'}]")), T, false);
		assertEquals("- [x] one\n- [ ] two _(in progress)_", todo.body());
		ChatEntry q = TurnItems.decode(json(item("q", "user_input_request", 1,
				"'requestId':'r','questions':[{'id':'1','header':'Pick','question':'Which?','options':[{'label':'A','description':'first'}]}]")), T, false);
		assertEquals(Kind.REQUEST, q.kind());
		assertEquals("**Pick**\nWhich?\n- A: first", q.body());
		ChatEntry err = TurnItems.decode(json(item("e", "error", 1, "'failure':{'class':'usage_limit','message':'Out of credits','code':null,'retryable':false}")), T, false);
		assertEquals(Kind.ERROR, err.kind());
		assertEquals("Out of credits", err.body());
		ChatEntry tool = TurnItems.decode(json(item("d", "dynamic_tool", 1, "'toolName':'Read','input':{'path':'x'},'outputOmitted':true")), T, false);
		assertEquals("Read", tool.title());
		assertEquals("input: {\"path\":\"x\"}\n(output not sent)", tool.detail());
		ChatEntry sub = TurnItems.decode(json(item("s", "subagent", 1, "'subagentId':'n','prompt':'Do X\\nthen Y','result':'ok','childThreadId':null")), T, false);
		assertEquals("Subagent: Do X …", sub.title());
		assertNull(TurnItems.decode(json(item("u", "brand_new_type", 1, "")), T, false));
		assertNull(TurnItems.decode(json("{'type':'user_message'}"), T, false));
	}

	@Test
	void hugeToolOutputIsCapped() {
		String big = "x".repeat(TurnItems.MAX_DETAIL_CHARS + 500);
		ChatEntry e = TurnItems.decode(json(item("c", "command_execution", 1, "'input':'cat log','output':'" + big + "'")), T, false);
		assertTrue(e.detail().length() < big.length());
		assertTrue(e.detail().endsWith("(500 more characters)"));
	}

	@Test
	void subscribeFrameAndHistoryUri() {
		JsonObject f = json(ThreadFeed.subscribeFrame("1", T, -1));
		assertEquals("Request", f.get("_tag").getAsString());
		assertEquals("orchestration.subscribeThread", f.get("tag").getAsString());
		JsonObject p = f.getAsJsonObject("payload");
		assertEquals(T, p.get("threadId").getAsString());
		assertTrue(p.get("requestCompletionMarker").getAsBoolean());
		assertTrue(p.get("acceptBoundedSnapshot").getAsBoolean());
		assertFalse(p.has("afterSequence"));
		assertEquals(42, json(ThreadFeed.subscribeFrame("2", T, 42)).getAsJsonObject("payload").get("afterSequence").getAsLong());
		// Read-only: never a dispatch.
		assertFalse(ThreadFeed.subscribeFrame("1", T, -1).contains("dispatchCommand"));

		URI u = ThreadFeed.historyUri(URI.create("https://host:3773"), "th/1 x", "a+b/c=");
		assertEquals("https://host:3773/api/orchestration/threads/th%2F1%20x/history?cursor=a%2Bb%2Fc%3D", u.toString());
		URI ws = ThreadFeed.socketUri(URI.create("http://127.0.0.1:3773"), "t+/=");
		assertEquals("ws://127.0.0.1:3773/ws?orchestrationProtocol=2&wsTicket=t%2B%2F%3D", ws.toString());
	}

	@Test
	void agoFormatting() {
		assertEquals("5 s ago", ChatTranscript.ago(5_000));
		assertEquals("3 min ago", ChatTranscript.ago(180_000));
		assertEquals("2 h ago", ChatTranscript.ago(7_200_000));
		assertEquals("1 d ago", ChatTranscript.ago(90_000_000));
	}
}
