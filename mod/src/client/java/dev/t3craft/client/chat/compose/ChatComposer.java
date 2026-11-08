package dev.t3craft.client.chat.compose;

import dev.t3craft.client.chat.ChatEntry;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.ChatTranscript;
import dev.t3craft.client.chat.ConversationPanel;
import dev.t3craft.client.chat.ThreadFeed;
import dev.t3craft.client.operate.OperateUi;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.ThreadState;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Production wiring of a {@link ComposerView} for a screen that shows a {@link ConversationPanel}:
 * sends through {@link OperateUi} (operate pairing, confirmation for Stop, results on the client
 * thread), "Enable control" opens {@code OperatePairScreen} over {@code screen}, and
 * {@link #attach}/{@link #sync} keep the composer on the thread the panel shows.
 */
public final class ChatComposer {
	private ChatComposer() {
	}

	/** A composer whose confirmations and pairing screens return to {@code screen}. */
	public static ComposerView create(Screen screen, ChatTheme theme, Runnable onSent) {
		ChatSender sender = new ChatSender(OperateUi.gate(), (a, done) -> OperateUi.run(a, OperateGate.Origin.UI, screen, done));
		return new ComposerView(new ComposerState(sender), theme, new ComposerView.Host() {
			@Override
			public void enableControl(String envId) {
				Minecraft.getInstance().gui.setScreen(OperateUi.enableControlScreen(screen, envId));
			}

			@Override
			public void sent() {
				onSent.run();
			}
		});
	}

	/** Puts the composer under {@code conv} (in place of the reply bar) with its optimistic rows. */
	public static void attach(ConversationPanel conv, ComposerView composer) {
		conv.setFooter(composer);
		conv.setExtra(composer.extra());
	}

	/** Points the composer at the thread {@code conv} shows and feeds it the live stream. Every frame is fine. */
	public static void sync(ConversationPanel conv, ComposerView composer) {
		String key = conv.key();
		int colon = key == null ? -1 : key.indexOf(':');
		if (colon <= 0 || colon == key.length() - 1) {
			composer.clearThread();
			return;
		}
		ThreadFeed feed = conv.feed();
		@Nullable String title = null;
		List<ChatEntry> entries = List.of();
		boolean running = false;
		String runId = null;
		if (feed != null) {
			ChatTranscript tr = feed.transcript();
			ChatTranscript.View v = tr.view();
			title = v.title().isBlank() ? null : v.title();
			entries = v.entries();
			runId = tr.activeRunId();
			running = runId != null || tr.hasSnapshot() && v.status().state() == ThreadState.WORKING;
		}
		composer.setThread(key.substring(0, colon), key.substring(colon + 1), title);
		composer.observe(entries, running, runId);
	}
}
