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
 *
 * <p>feat/t3-composer: drafts and waiting images live in {@link ComposerDrafts#shared()} (they
 * survive closing the screen; texts also on disk). While a turn runs a message <i>steers</i> it
 * (Ctrl+Enter / Ctrl+click: <i>queues</i> it), as T3 Code ({@link ChatSender.Delivery}); the
 * optimistic row carries the "Steer" mark. A <b>draft thread</b> ({@link #setDraft}) has no
 * thread yet: its first message goes to a {@link Launcher} (the host's {@code launchThread}).
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
	public record Pending(String threadKey, String messageId, String text, long at, List<ImageAttachment> images, ChatSender.Delivery delivery) {
		public Pending(String threadKey, String messageId, String text, long at) {
			this(threadKey, messageId, text, at, List.of(), ChatSender.Delivery.AUTO);
		}

		public Pending(String threadKey, String messageId, String text, long at, List<ImageAttachment> images) {
			this(threadKey, messageId, text, at, images, ChatSender.Delivery.AUTO);
		}

		public Pending {
			images = List.copyOf(images);
		}

		/** The optimistic "You" row shown at the end of the conversation (attachments as the feed shows them). */
		public ChatEntry entry() {
			int n = images.size();
			String body = n == 0 ? text : text + (text.isEmpty() ? "" : "\n\n") + "_(" + n + " attachment" + (n == 1 ? "" : "s") + ")_";
			String mark = switch (delivery) {
				case STEER -> ChatEntry.STEER;
				case QUEUE -> ChatEntry.QUEUED;
				default -> "";
			};
			return new ChatEntry("pending:" + messageId, messageId, messageId, ChatEntry.Kind.USER, mark, body, null, false, "pending", at,
					Long.MAX_VALUE, false);
		}
	}

	/**
	 * Sends a draft thread's first message: the host launches the thread
	 * ({@code orchestration.launchThread}) and calls {@code done} once. On success the host replaces
	 * the draft by the real thread; on failure the text and images come back here with the reason.
	 */
	@FunctionalInterface
	public interface Launcher {
		void launch(String text, List<ImageAttachment> images, java.util.function.Consumer<OperateResult> done);
	}

	private final ChatSender sender;
	private final LongSupplier clock;
	private final ComposerDrafts drafts;
	private final ComposerText editor = new ComposerText();
	private final List<Pending> pending = new ArrayList<>();
	private final Set<String> sending = new HashSet<>();
	private final Map<String, String> errors = new HashMap<>();
	private @Nullable Launcher launcher;
	private long savedAt;
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
		this(sender, clock, ComposerDrafts.shared());
	}

	public ComposerState(ChatSender sender, LongSupplier clock, ComposerDrafts drafts) {
		this.sender = sender;
		this.clock = clock;
		this.drafts = drafts;
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
		launcher = null;
		select(envId, threadId, title);
	}

	/**
	 * A draft thread ({@code draftId} is its local id, never sent): same editing, drafts and images
	 * as a thread, but the message goes to {@code launcher} and there is no Stop / steer.
	 */
	public void setDraft(String envId, String draftId, @Nullable String title, Launcher launcher) {
		this.launcher = launcher;
		select(envId, draftId, title);
	}

	public boolean isDraft() {
		return launcher != null;
	}

	/**
	 * The draft thread moved to another environment ({@code env:draftId} changes): its text and
	 * images follow it instead of being left under the old key.
	 */
	public void rekeyDraft(String envId, String draftId) {
		String nk = envId + ":" + draftId;
		if (launcher == null || key == null || nk.equals(key)) {
			return;
		}
		String ok = key;
		List<ImageAttachment> imgs = drafts.images(ok);
		drafts.remove(ok);
		drafts.save(nk, editor.text(), editor.caret());
		drafts.setImages(nk, imgs);
		String err = errors.remove(ok);
		if (err != null) {
			errors.put(nk, err);
		}
		key = nk;
		this.envId = envId;
		this.threadId = draftId;
		refreshAccess();
	}

	/** Moves the stored text and images of a thread that is not shown from {@code from} to {@code to}. */
	public void moveStored(String from, String to) {
		if (from.equals(to) || from.equals(key)) {
			return;
		}
		ComposerDrafts.Draft d = drafts.get(from);
		List<ImageAttachment> imgs = drafts.images(from);
		drafts.remove(from);
		if (d != null) {
			drafts.save(to, d.text(), d.caret());
		}
		drafts.setImages(to, imgs);
	}

	/** Forgets a discarded draft thread's text and images. */
	public void discard(String threadKey) {
		drafts.remove(threadKey);
		errors.remove(threadKey);
		if (threadKey.equals(key)) {
			editor.clear();
		}
	}

	private void select(String envId, String threadId, @Nullable String title) {
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
		launcher = null;
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
			savedAt = clock.getAsLong();
		}
	}

	/** After an edit: keeps the draft store current (at most every 1.5 s; {@link #flush} on close). */
	public void edited() {
		if (clock.getAsLong() - savedAt > 1500) {
			saveDraft();
		}
	}

	/** Writes the current text to the draft store (the screen is closing). */
	public void flush() {
		saveDraft();
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
		if (running && empty && launcher == null) {
			return stopping ? Button.STOPPING : Button.STOP;
		}
		return empty ? Button.SEND_DISABLED : Button.SEND;
	}

	/** The primary button's click: send, or stop while a turn runs with an empty input. */
	public void primary() {
		primary(false);
	}

	/** {@code alternate}: Ctrl held (queue instead of steer while a turn runs). */
	public void primary(boolean alternate) {
		switch (button()) {
			case SEND -> submit(alternate);
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
		return submit(false);
	}

	/** How the next message would go out ({@code alternate}: Ctrl held). */
	public ChatSender.Delivery delivery(boolean alternate) {
		if (!running || launcher != null) {
			return ChatSender.Delivery.AUTO;
		}
		return alternate ? ChatSender.Delivery.QUEUE : ChatSender.Delivery.STEER;
	}

	/** Sends; {@code alternate} (Ctrl+Enter, Ctrl+click) queues instead of steering a running turn. */
	public boolean submit(boolean alternate) {
		List<ImageAttachment> imgs = images();
		if (key == null || !canOperate || sending.contains(key) || editor.isBlank() && imgs.isEmpty()) {
			return false;
		}
		String k = key;
		String text = editor.text().strip();
		editor.clear();
		drafts.remove(k);
		errors.remove(k);
		sending.add(k);
		Launcher l = launcher;
		if (l != null) {
			l.launch(text, imgs, r -> {
				sending.remove(k);
				if (!r.ok()) {
					restore(k, text);
					restoreImages(k, imgs);
					if (r.outcome() != OperateResult.Outcome.CANCELLED) {
						errors.put(k, ThreadControl.feedback(r).text());
					}
				}
			});
			return true;
		}
		ChatSender.Delivery delivery = delivery(alternate);
		ChatSender.Ids ids = sender.newIds();
		Pending p = new Pending(k, ids.messageId(), text, clock.getAsLong(), imgs, delivery);
		pending.add(p);
		version++;
		sender.send(envId, threadId, title, text, imgs, ids, delivery, r -> sent(k, p, text, r));
		return true;
	}

	private void restoreImages(String k, List<ImageAttachment> imgs) {
		if (imgs.isEmpty()) {
			return;
		}
		List<ImageAttachment> back = new ArrayList<>(imgs);
		back.addAll(drafts.images(k));
		drafts.setImages(k, back);
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
		restoreImages(k, p.images());
		errors.put(k, ThreadControl.feedback(r).text());
	}

	// ---- images -------------------------------------------------------------------------------

	/** Images waiting in the composer of the current thread, in order. */
	public List<ImageAttachment> images() {
		return key == null ? List.of() : drafts.images(key);
	}

	/** Every image held (all threads and in-flight messages): the thumbnails still needed. */
	public Set<String> liveImageIds() {
		Set<String> out = new HashSet<>();
		drafts.allImages().forEach(i -> out.add(i.localId()));
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
		drafts.setImages(key, next);
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
			drafts.setImages(key, cur);
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
		if (launcher != null) {
			return false;
		}
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
