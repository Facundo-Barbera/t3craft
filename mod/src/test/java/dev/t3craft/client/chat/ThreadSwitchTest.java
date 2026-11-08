package dev.t3craft.client.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.t3.RpcFrames;
import dev.t3craft.t3.StreamRegistry;
import dev.t3craft.t3.T3Connection;
import dev.t3craft.t3.ThreadState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Switching conversations from the thread list (fix/ui-chat-sidebar): a left click on drawn row N
 * opens thread N (also scrolled, also after a re-sort), the previous thread's subscription is
 * released and the new one taken, the feed starts at the newest line and the reply bar targets the
 * new thread's environment. Real {@link ThreadBrowser}, {@link ThreadListView} and
 * {@link StreamRegistry}; a "frame" is {@link ThreadListView#layoutRows}.
 */
class ThreadSwitchTest {
	/** One environment over a real registry, with its own socket stand-in. */
	static final class Env implements ChatSource.Env {
		final String id;
		final StreamRegistry registry = new StreamRegistry(RpcFrames::subscribeThread);
		final List<JsonObject> sent = new ArrayList<>();

		Env(String id) {
			this.id = id;
			registry.onOpen(f -> sent.add(JsonParser.parseString(f).getAsJsonObject()));
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public String label() {
			return id.toUpperCase();
		}

		@Override
		public T3Connection.Status status() {
			return T3Connection.Status.OK;
		}

		@Override
		public String statusDetail() {
			return "";
		}

		@Override
		public StreamRegistry.Subscription subscribeThread(String threadId, StreamRegistry.Listener listener) {
			return registry.subscribe(threadId, listener);
		}

		@Override
		public CompletableFuture<JsonObject> threadHistory(String threadId, String cursor) {
			return new CompletableFuture<>();
		}

		@Override
		public CompletableFuture<@Nullable JsonObject> getTurnItem(String threadId, String itemId) {
			return CompletableFuture.completedFuture(null);
		}

		/** Request id of the last subscribe sent for {@code threadId}. */
		String requestFor(String threadId) {
			for (int i = sent.size() - 1; i >= 0; i--) {
				JsonObject o = sent.get(i);
				if (o.toString().contains("\"" + threadId + "\"") && !"Interrupt".equals(o.has("_tag") ? o.get("_tag").getAsString() : "")) {
					return o.has("id") ? o.get("id").getAsString() : o.get("requestId").getAsString();
				}
			}
			throw new AssertionError("no subscribe for " + threadId);
		}
	}

	final Map<String, Env> envs = new LinkedHashMap<>();
	final List<ThreadRow> rows = new ArrayList<>();
	final List<String> operateAsked = new ArrayList<>();
	ThreadBrowser browser;

	/** Rows A..F in env "vps", newest first (so the list shows them in that order). */
	@BeforeEach
	void setUp() {
		envs.put("vps", new Env("vps"));
		envs.put("lab", new Env("lab"));
		long at = 1000;
		for (String id : List.of("A", "B", "C", "D", "E", "F")) {
			rows.add(row("vps", id, at--));
		}
		ThreadDirectory dir = new ThreadDirectory() {
			@Override
			public List<ThreadRow> rows() {
				return List.copyOf(rows);
			}

			@Override
			public Map<String, ThreadList.EnvInfo> environments() {
				Map<String, ThreadList.EnvInfo> m = new LinkedHashMap<>();
				envs.keySet().forEach(e -> m.put(e, new ThreadList.EnvInfo(e.toUpperCase(), "ok", null)));
				return m;
			}
		};
		ChatSource source = envId -> {
			Env e = envs.get(envId);
			if (e == null) {
				throw new ChatSource.Unavailable("not paired");
			}
			return e;
		};
		browser = new ThreadBrowser(dir, source, ChatTheme.warmStudio(), null);
		browser.conversation().setActions(new ConversationPanel.Actions() {
			@Override
			public boolean canOperate(String envId) {
				operateAsked.add(envId);
				return envId.equals("lab");
			}

			@Override
			public void enableControl(String envId) {
			}

			@Override
			public void pair() {
			}
		});
	}

	static ThreadRow row(String env, String id, long at) {
		return new ThreadRow(env + ":" + id, env, env.toUpperCase(), id, "t-" + id, "", ThreadState.IDLE, false, null, false, at);
	}

	/** A frame: the list's inset rectangle with room for exactly {@code cap} rows (13 px each). */
	DrawnRows frame(int cap) {
		browser.list().setBounds(new Rect(10, 20, 150, 400));
		return browser.list().layoutRows(new Rect(10, 50, 150, cap * ChatTheme.warmStudio().listRow() + 6));
	}

	/** Left click in the middle of visible row {@code index} as last drawn. */
	boolean clickRow(int index, int button) {
		Rect r = browser.list().drawn().rowRect(index);
		assertTrue(r.w() > 0, "row " + index + " is drawn");
		return browser.mouseClicked(r.x() + r.w() / 2.0, r.y() + r.h() / 2.0, button);
	}

	int refs(String env, String thread) {
		return envs.get(env).registry.refCount(thread);
	}

	@Test
	void minecraftLeftButtonIsOneNotGlfwZero() {
		// the root cause: 26.x reports left = 1, right = 3; the list compared with 0 and 1
		assertEquals(InputConstants.MOUSE_BUTTON_LEFT, MouseButtons.LEFT);
		assertEquals(1, MouseButtons.LEFT);
		assertEquals(3, MouseButtons.RIGHT);
		assertFalse(MouseButtons.isLeft(0));
	}

	@Test
	void clickOnRowNOpensThreadNAndMovesTheSubscription() {
		assertEquals("vps:A", browser.conversation().key(), "first fill opens the top thread");
		assertEquals(1, refs("vps", "A"));
		frame(4);

		assertTrue(clickRow(2, MouseButtons.LEFT));

		assertEquals("vps:C", browser.model().selectedKey(), "selection shown in the list");
		assertEquals("vps:C", browser.conversation().key(), "conversation switched");
		assertNotNull(browser.conversation().feed());
		assertEquals("C", browser.conversation().feed().transcript().threadId());
		assertEquals(0, refs("vps", "A"), "previous thread released");
		assertEquals(1, refs("vps", "C"), "new thread subscribed once");
		assertEquals("Interrupt", envs.get("vps").sent.get(envs.get("vps").sent.size() - 2).get("_tag").getAsString(),
				"the old stream is interrupted before the new subscribe");
		assertTrue(browser.conversation().followingNewest(), "the new conversation starts at the newest line");

		// clicking the open thread again changes nothing (no resubscribe)
		int sent = envs.get("vps").sent.size();
		clickRow(2, MouseButtons.LEFT);
		assertEquals(sent, envs.get("vps").sent.size());
		assertEquals(1, refs("vps", "C"));
	}

	@Test
	void clickWithScrollOpensTheRowUnderTheCursor() {
		frame(3);
		browser.list().mouseScrolled(20, 60, -1); // wheel down: 3 rows
		DrawnRows d = frame(3);
		assertEquals(3, d.first());
		assertEquals(List.of("vps:D", "vps:E", "vps:F"), d.keys());

		clickRow(4, MouseButtons.LEFT); // second slot on screen
		assertEquals("vps:E", browser.conversation().key());
		assertEquals(1, refs("vps", "E"));
		assertEquals(0, refs("vps", "A"));
	}

	@Test
	void nonLeftButtonsDoNotOpen() {
		frame(4);
		assertTrue(clickRow(1, MouseButtons.RIGHT), "consumed by the list");
		clickRow(1, 0); // GLFW's "left" is not a 26.x button
		assertEquals("vps:A", browser.conversation().key());
		assertEquals("vps:A", browser.model().selectedKey());
		assertEquals(0, refs("vps", "B"));
	}

	@Test
	void reSortBetweenFrameAndClickStillOpensTheDrawnRow() {
		frame(4);
		// B jumps to the top before the click lands (new activity)
		rows.set(1, row("vps", "B", 5000));
		browser.tick();
		assertEquals("vps:B", browser.model().visible().getFirst().key());

		clickRow(2, MouseButtons.LEFT); // C was drawn there
		assertEquals("vps:C", browser.conversation().key());
	}

	@Test
	void olderThreadItemsNeverReachTheNewConversation() {
		Env vps = envs.get("vps");
		String reqA = vps.requestFor("A");
		frame(4);
		clickRow(1, MouseButtons.LEFT);
		assertEquals("vps:B", browser.conversation().key());

		// a late snapshot of A on its old request: acked as stale, delivered to nobody
		String snapshotA = ThreadFeedTest.SNAPSHOT.replace("\"T\"", "\"A\"");
		assertTrue(vps.registry.onChunk(reqA, List.of(JsonParser.parseString(snapshotA).getAsJsonObject())));
		ChatTranscript b = browser.conversation().feed().transcript();
		assertFalse(b.hasSnapshot());
		assertTrue(b.view().entries().isEmpty(), "no messages of the previous thread");

		vps.registry.onChunk(vps.requestFor("B"), List.of(JsonParser.parseString(ThreadFeedTest.SNAPSHOT.replace("\"T\"", "\"B\""))
				.getAsJsonObject()));
		assertTrue(b.hasSnapshot());
		assertEquals(1, b.view().entries().size(), "B's own history");
	}

	@Test
	void replyBarAndOperateFollowTheNewThread() {
		rows.add(row("lab", "L", 2000)); // newest: listed first
		browser.tick();
		frame(8);
		clickRow(1, MouseButtons.LEFT); // vps:A
		assertEquals("vps:A", browser.conversation().key());
		assertEquals(ChatAccess.Reply.READ_ONLY, browser.conversation().replyState());
		assertEquals("vps", operateAsked.getLast());

		clickRow(0, MouseButtons.LEFT); // lab:L
		assertEquals("lab:L", browser.conversation().key(), "the reply goes to the shown thread");
		assertEquals(ChatAccess.Reply.READY, browser.conversation().replyState(), "operate re-asked for the new environment");
		assertEquals("lab", operateAsked.getLast());
		assertEquals(1, refs("lab", "L"));
		assertEquals(0, refs("vps", "A"));
	}

	@Test
	void filteredOutSinceTheFrameOpensNothing() {
		frame(4);
		browser.model().setHidden(Set.of("vps"));
		clickRow(2, MouseButtons.LEFT);
		assertEquals("vps:A", browser.conversation().key());
		assertNull(browser.model().selected());
	}
}
