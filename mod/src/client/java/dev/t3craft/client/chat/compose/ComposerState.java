package dev.t3craft.client.chat.compose;

import dev.t3craft.client.chat.ChatEntry;
import dev.t3craft.t3.OperateResult;
import dev.t3craft.t3.ThreadControl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Everything the composer knows, without Minecraft: the thread it writes to, the text being edited
 * ({@link ComposerText}), drafts per thread, messages sent but not yet echoed by the server
 * (optimistic rows, as T3 Code's {@code optimisticUserMessages} in
 * {@code apps/web/src/components/chat/useComposerSend.ts}), whether a turn is running, and the
 * last error per thread. {@link ComposerView} draws it; tests drive it directly.
 *
 * <p>Client thread only. Results of {@link ChatSender} must be delivered on the same thread
 * ({@code OperateUi.run} does that).
 */
public final class ComposerState {
	/** What the primary button at the right of the controls bar is, as in T3's {@code ComposerPrimaryActions}. */
	public enum Button {
		/** No thread: nothing. */
		NONE,
		/** Read-only environment: "Enable control" (to {@code OperatePairScreen}). */
		ENABLE_CONTROL,
		/** Nothing to send. */
		SEND_DISABLED,
		/** Send (steers the running turn when one is running, like T3). */
		SEND,
		/** Send in flight. */
		SENDING,
		/** A turn is running and the input is empty: interrupt it. */
		STOP,
		/** Interrupt in flight. */
		STOPPING
	}

	/** A message sent from here and not yet seen in the thread's stream. */
	public record Pending(String threadKey, String messageId, String text, long at, List<ImageAttachment> images) {
		public Pending(String threadKey, String messageId, String text, long at) {
			this(threadKey, messageId, text, at, List.of());
		}

		public Pending {
			images = List.copyOf(images);
		}

		/** The optimistic "You" row shown at the end of the conversation (attachments as the feed shows them). */
		public ChatEntry entry() {
			int n = images.size();
			String body = n == 0 ? text : text + (text.isEmpty() ? "" : "\n\n") + "_(" + n + " attachment" + (n == 1 ? "" : "s") + ")_";
			return new ChatEntry("pending:" + messageId, messageId, messageId, ChatEntry.Kind.USER, "", body, null, false, "pending", at,
					Long.MAX_VALUE, false);
		}
	}

	private final ChatSender sender;
	private final LongSupplier clock;
	private final ComposerDrafts drafts = new ComposerDrafts();
	private final ComposerText editor = new ComposerText();
	private final List<Pending> pending = new ArrayList<>();
	private final Set<String> sending = new HashSet<>();
	private final Map<String, String> errors = new HashMap<>();
	/** Images waiting in the composer, per thread (kept like drafts when switching threads). */
	private final Map<String, List<ImageAttachment>> images = new HashMap<>();
	private @Nullable String key;
	private String envId = "";
	private String threadId = "";
	private @Nullable String title;
	private boolean canOperate;
	private boolean running;
	private @Nullable String activeRunId;
	private boolean stopping;
	private long version;

	public ComposerState(ChatSender sender, LongSupplier clock) {
		this.sender = sender;
		this.clock = clock;
	}

	public ComposerState(ChatSender sender) {
		this(sender, System::currentTimeMillis);
	}

	public ComposerText editor() {
		return editor;
	}

	public @Nullable String key() {
		return key;
	}

	public String envId() {
		return envId;
	}

	public String threadId() {
		return threadId;
	}

	/** Bumped whenever the optimistic rows change (the conversation re-lays out). */
	public long version() {
		return version;
	}

	// ---- thread -------------------------------------------------------------------------------

	/**
	 * Writes to {@code envId:threadId} from now on (no-op when it already does, except the title).
	 * The current text is kept as that thread's draft and the new thread's draft comes back.
	 */
	public void setThread(String envId, String threadId, @Nullable String title) {
		String k = envId + ":" + threadId;
		this.title = title;
		if (k.equals(key)) {
			return;
		}
		saveDraft();
		key = k;
		this.envId = envId;
		this.threadId = threadId;
		running = false;
		activeRunId = null;
		stopping = false;
		ComposerDrafts.Draft d = drafts.get(k);
		if (d == null) {
			editor.clear();
		} else {
			editor.set(d.text(), d.caret());
		}
		refreshAccess();
	}

	/** No thread shown: keeps the draft of the previous one. */
	public void clearThread() {
		saveDraft();
		key = null;
		envId = "";
		threadId = "";
		title = null;
		running = false;
		activeRunId = null;
		editor.clear();
	}

	private void saveDraft() {
		if (key != null) {
			drafts.save(key, editor.text(), editor.caret());
		}
	}

	/** The draft kept for another thread (tests, the unified screen's sidebar hints). */
	public @Nullable String draftOf(String threadKey) {
		if (threadKey.equals(key)) {
			return editor.isEmpty() ? null : editor.text();
		}
		ComposerDrafts.Draft d = drafts.get(threadKey);
		return d == null ? null : d.text();
	}

	/** Re-asks whether the environment can operate (a read of the operate token file). */
	public void refreshAccess() {
		canOperate = key != null && sender.canOperate(envId);
	}

	public boolean canOperate() {
		return canOperate;
	}

	// ---- stream -------------------------------------------------------------------------------

	/**
	 * Folds what the thread's live subscription shows: the entries (to reconcile optimistic rows by
	 * {@code messageId}), whether a turn runs and its run id (for Stop). Returns true when the
	 * optimistic rows changed.
	 */
	public boolean observe(List<ChatEntry> entries, boolean running, @Nullable String activeRunId) {
		this.running = running;
		this.activeRunId = activeRunId;
		if (!running) {
			stopping = false;
		}
		if (pending.isEmpty() || key == null) {
			return false;
		}
		Set<String> seen = new HashSet<>();
		for (ChatEntry e : entries) {
			if (e.messageId() != null && e.kind() == ChatEntry.Kind.USER) {
				seen.add(e.messageId());
			}
		}
		String k = key;
		boolean changed = pending.removeIf(p -> p.threadKey().equals(k) && seen.contains(p.messageId()));
		if (changed) {
			version++;
		}
		return changed;
	}

	public boolean running() {
		return running;
	}

	/** Optimistic rows of the current thread, oldest first. */
	public List<ChatEntry> pendingEntries() {
		if (key == null) {
			return List.of();
		}
		List<ChatEntry> out = new ArrayList<>();
		for (Pending p : pending) {
			if (p.threadKey().equals(key)) {
				out.add(p.entry());
			}
		}
		return out;
	}

	public List<Pending> pending() {
		return List.copyOf(pending);
	}

	// ---- actions ------------------------------------------------------------------------------

	public Button button() {
		if (key == null) {
			return Button.NONE;
		}
		if (!canOperate) {
			return Button.ENABLE_CONTROL;
		}
		if (sending.contains(key)) {
			return Button.SENDING;
		}
		boolean empty = editor.isBlank() && images().isEmpty();
		if (running && empty) {
			return stopping ? Button.STOPPING : Button.STOP;
		}
		return empty ? Button.SEND_DISABLED : Button.SEND;
	}

	/** The primary button's click: send, or stop while a turn runs with an empty input. */
	public void primary() {
		switch (button()) {
			case SEND -> submit();
			case STOP -> stop();
			default -> {
			}
		}
	}

	/**
	 * Sends the text (Enter / the send button). The input empties at once and the message shows
	 * as a pending "You" row; on failure the text comes back and the error is shown. Returns true
	 * when something was sent.
	 */
	public boolean submit() {
		List<ImageAttachment> imgs = images();
		if (key == null || !canOperate || sending.contains(key) || editor.isBlank() && imgs.isEmpty()) {
			return false;
		}
		String k = key;
		String text = editor.text().strip();
		editor.clear();
		drafts.remove(k);
		errors.remove(k);
		images.remove(k);
		sending.add(k);
		ChatSender.Ids ids = sender.newIds();
		Pending p = new Pending(k, ids.messageId(), text, clock.getAsLong(), imgs);
		pending.add(p);
		version++;
		if (imgs.isEmpty()) {
			sender.send(envId, threadId, title, text, ids, r -> sent(k, p, text, r));
		} else {
			sender.send(envId, threadId, title, text, imgs, ids, r -> sent(k, p, text, r));
		}
		return true;
	}

	private void sent(String k, Pending p, String text, OperateResult r) {
		sending.remove(k);
		if (r.ok()) {
			return; // the row stays until the server's user_message replaces it
		}
		if (pending.remove(p)) {
			version++;
		}
		restore(k, text);
		if (!p.images().isEmpty()) {
			List<ImageAttachment> back = new ArrayList<>(p.images());
			back.addAll(images.getOrDefault(k, List.of()));
			images.put(k, back);
		}
		errors.put(k, ThreadControl.feedback(r).text());
	}

	// ---- images -------------------------------------------------------------------------------

	/** Images waiting in the composer of the current thread, in order. */
	public List<ImageAttachment> images() {
		return key == null ? List.of() : images.getOrDefault(key, List.of());
	}

	/** Every image held (all threads and in-flight messages): the thumbnails still needed. */
	public Set<String> liveImageIds() {
		Set<String> out = new HashSet<>();
		images.values().forEach(l -> l.forEach(i -> out.add(i.localId())));
		pending.forEach(p -> p.images().forEach(i -> out.add(i.localId())));
		return out;
	}

	/**
	 * Adds an image to the current thread's composer. Returns why it was refused (no thread,
	 * read-only, a T3 limit), or null; the refusal is also the composer's error.
	 */
	public @Nullable String addImage(String localId, @Nullable String name, byte[] bytes) {
		if (key == null) {
			return "Select a thread first.";
		}
		if (!canOperate) {
			return setError("Read-only: enable control to send images.");
		}
		String mime = ImageAttachment.sniff(bytes);
		List<ImageAttachment> cur = images();
		String why = ImageAttachment.rejection(bytes, mime, cur);
		if (why != null) {
			return setError(why);
		}
		List<ImageAttachment> next = new ArrayList<>(cur);
		next.add(new ImageAttachment(localId, ImageAttachment.cleanName(name, mime), mime, bytes));
		images.put(key, next);
		errors.remove(key);
		return null;
	}

	public boolean removeImage(String localId) {
		if (key == null) {
			return false;
		}
		List<ImageAttachment> cur = new ArrayList<>(images());
		boolean changed = cur.removeIf(i -> i.localId().equals(localId));
		if (changed) {
			if (cur.isEmpty()) {
				images.remove(key);
			} else {
				images.put(key, cur);
			}
		}
		return changed;
	}

	/** Shows {@code message} as the current thread's error (a failed pick or paste); returns it. */
	public @Nullable String setError(@Nullable String message) {
		if (key != null && message != null) {
			errors.put(key, message);
		}
		return message;
	}

	/** Puts unsent text back in front of whatever was typed meanwhile. */
	private void restore(String k, String text) {
		if (k.equals(key)) {
			String now = editor.text();
			editor.set(now.isEmpty() ? text : text + "\n" + now, text.length());
		} else {
			ComposerDrafts.Draft d = drafts.get(k);
			String now = d == null ? "" : d.text();
			drafts.save(k, now.isEmpty() ? text : text + "\n" + now, text.length());
		}
	}

	/** Interrupts the running turn ({@code run.interrupt}, confirmed in game). */
	public boolean stop() {
		if (key == null || !canOperate || !running || stopping) {
			return false;
		}
		String k = key;
		if (activeRunId == null) {
			errors.put(k, "no active run to interrupt");
			return false;
		}
		stopping = true;
		sender.interrupt(envId, threadId, title, activeRunId, r -> {
			stopping = false;
			if (!r.ok() && r.outcome() != OperateResult.Outcome.CANCELLED) {
				errors.put(k, ThreadControl.feedback(r).text());
			}
		});
		return true;
	}

	/** Last error of the current thread (cleared by the next send or by editing). */
	public @Nullable String error() {
		return key == null ? null : errors.get(key);
	}

	public void clearError() {
		if (key != null) {
			errors.remove(key);
		}
	}

	@Override
	public String toString() {
		return "ComposerState[" + Objects.requireNonNullElse(key, "-") + ", " + button() + "]";
	}
}
