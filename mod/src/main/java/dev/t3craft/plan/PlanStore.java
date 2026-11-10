package dev.t3craft.plan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.t3craft.T3Craft;
import dev.t3craft.t3.ThreadShell;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * The project plan: the single store every surface reads and writes (docs/features/project-plan.md). One JSON file
 * ({@link PlanCodec}), read on first use and written atomically (temp file + move) on every change. {@link #version}
 * grows on every change, so views can cache and re-read only when it moved.
 *
 * <p>Nothing here talks to T3: launching a thread and sending observations are the page's explicit actions; the store
 * only records their result ({@link #link}, {@link #returned}). Re-importing proposals never overwrites a task that is
 * part of the plan, a task the player edited, or any mark ({@link #importProposals}).
 *
 * <p>A file that cannot be read is copied aside ({@code plan.json.unreadable-<millis>}) before anything is written, and
 * {@link #loadProblem} says so. Thread-safe.
 */
public final class PlanStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path file;
	private final LongSupplier clock;
	private final Map<String, PlanTask> tasks = new LinkedHashMap<>();
	private JsonObject extra = new JsonObject();
	private int fileVersion = PlanCodec.FORMAT;
	private boolean loaded;
	private long version;
	private @Nullable String loadProblem;
	private @Nullable String saveProblem;

	public PlanStore(Path file, LongSupplier clock) {
		this.file = file;
		this.clock = clock;
	}

	public PlanStore(Path file) {
		this(file, System::currentTimeMillis);
	}

	// ---- reading ----------------------------------------------------------------------------------------------

	/** Grows on every change (and on the first load). */
	public synchronized long version() {
		load();
		return version;
	}

	/** Every task, in creation order. */
	public synchronized List<PlanTask> tasks() {
		load();
		return List.copyOf(tasks.values());
	}

	public synchronized @Nullable PlanTask get(String id) {
		load();
		return tasks.get(id);
	}

	public synchronized boolean isEmpty() {
		load();
		return tasks.isEmpty();
	}

	/** Why the file could not be read (it was set aside), or null. */
	public synchronized @Nullable String loadProblem() {
		load();
		return loadProblem;
	}

	/** Why the last write failed, or null. */
	public synchronized @Nullable String saveProblem() {
		return saveProblem;
	}

	// ---- writing ----------------------------------------------------------------------------------------------

	/** A new local draft; returns it. */
	public synchronized PlanTask create(String title, String outcome, List<String> dependsOn, String decision, String notes) {
		load();
		long now = clock.getAsLong();
		String id;
		do {
			id = "t-" + UUID.randomUUID().toString().substring(0, 8);
		} while (tasks.containsKey(id));
		PlanTask t = new PlanTask(id, title.strip(), outcome.strip(), clean(id, dependsOn), decision.strip(), notes.strip(),
				PlanTask.Stage.DRAFT, PlanTask.Origin.LOCAL, null, null, "", false, null, false, null, now, now, null);
		tasks.put(id, t);
		changed();
		return t;
	}

	/** New texts for a task that is not accepted yet. A proposal edited here is never refreshed by a re-import. */
	public synchronized boolean edit(String id, String title, String outcome, List<String> dependsOn, String decision, String notes) {
		load();
		PlanTask t = tasks.get(id);
		if (t == null || t.stage() == PlanTask.Stage.ACCEPTED) {
			return false;
		}
		PlanTask n = t.withText(title.strip(), outcome.strip(), clean(id, dependsOn), decision.strip(), notes.strip(),
				t.origin() == PlanTask.Origin.PROPOSAL, clock.getAsLong());
		return put(t, n);
	}

	/** A proposal enters the plan as a draft. */
	public synchronized boolean acceptProposal(String id) {
		return move(id, PlanTask.Stage.PROPOSED, PlanTask.Stage.DRAFT);
	}

	/** Deletes a pending proposal or a draft (never a linked or reviewed task). */
	public synchronized boolean remove(String id) {
		load();
		PlanTask t = tasks.get(id);
		if (t == null || t.stage() != PlanTask.Stage.PROPOSED && t.stage() != PlanTask.Stage.DRAFT) {
			return false;
		}
		tasks.remove(id);
		changed();
		return true;
	}

	/** Links a draft to a thread (launched by the page, or an existing one); it becomes "in progress". */
	public synchronized boolean link(String id, PlanTask.ThreadRef ref, boolean launched, @Nullable Long baseline) {
		load();
		PlanTask t = tasks.get(id);
		if (t == null || t.stage() != PlanTask.Stage.DRAFT) {
			return false;
		}
		long now = clock.getAsLong();
		return put(t, t.withLink(new PlanTask.Link(ref.envId(), ref.threadId(), launched, now, baseline), PlanTask.Stage.IN_PROGRESS, now));
	}

	/** Takes the thread off an in-progress task (back to draft). Nothing is sent to the thread. */
	public synchronized boolean unlink(String id) {
		load();
		PlanTask t = tasks.get(id);
		if (t == null || t.stage() != PlanTask.Stage.IN_PROGRESS) {
			return false;
		}
		return put(t, t.withLink(null, PlanTask.Stage.DRAFT, clock.getAsLong()));
	}

	/** What the agent declares it checked (free text; empty = nothing declared). */
	public synchronized boolean setAgentChecked(String id, String text) {
		load();
		PlanTask t = tasks.get(id);
		if (t == null || t.stage() == PlanTask.Stage.ACCEPTED) {
			return false;
		}
		return put(t, t.withMarks(text.strip(), t.userTested(), clock.getAsLong()));
	}

	/** The player's own "I tested it" mark. Only the page's check box calls this. */
	public synchronized boolean setUserTested(String id, boolean tested) {
		load();
		PlanTask t = tasks.get(id);
		if (t == null || t.stage() == PlanTask.Stage.ACCEPTED) {
			return false;
		}
		return put(t, t.withMarks(t.agentChecked(), tested, clock.getAsLong()));
	}

	/** Accepts a delivery. It launches nothing and authorises nothing else. */
	public synchronized boolean accept(String id) {
		return move(id, PlanTask.Stage.IN_REVIEW, PlanTask.Stage.ACCEPTED);
	}

	/**
	 * Records observations the page already sent to the linked thread: the task goes back to "in progress" and only a
	 * turn that completes after {@code baseline} counts as the next delivery.
	 */
	public synchronized boolean returned(String id, String feedback, long baseline) {
		load();
		PlanTask t = tasks.get(id);
		if (t == null || t.stage() != PlanTask.Stage.IN_REVIEW || t.link() == null) {
			return false;
		}
		PlanTask.Link l = t.link();
		PlanTask.Link nl = new PlanTask.Link(l.envId(), l.threadId(), l.launched(), l.linkedAt(), baseline);
		return put(t, t.withFeedback(feedback.strip(), nl, PlanTask.Stage.IN_PROGRESS, clock.getAsLong()));
	}

	/** Drops the "a newer proposal was not applied" notice. */
	public synchronized boolean dismissIncoming(String id) {
		load();
		PlanTask t = tasks.get(id);
		return t != null && t.incoming() != null && put(t, t.withIncoming(null, clock.getAsLong()));
	}

	/** Counts of an import, for the page's status line. */
	public record ImportResult(int added, int refreshed, int unchanged, int conflicts) {
	}

	/**
	 * Adds proposals from thread {@code from}. A new id enters as {@link PlanTask.Stage#PROPOSED}. An id already here is
	 * refreshed only while it is still a pending, unedited proposal from the same thread; otherwise the local task stays
	 * as it is and, when the proposal differs, it gets an {@link PlanTask.Incoming} notice. Nothing is ever removed.
	 */
	public synchronized ImportResult importProposals(PlanTask.ThreadRef from, List<PlanProposals.Proposed> proposed) {
		load();
		long now = clock.getAsLong();
		int added = 0;
		int refreshed = 0;
		int unchanged = 0;
		int conflicts = 0;
		for (PlanProposals.Proposed p : proposed) {
			PlanTask t = tasks.get(p.id());
			if (t == null) {
				tasks.put(p.id(), new PlanTask(p.id(), p.title(), p.outcome(), p.dependsOn(), p.decision(), p.notes(), PlanTask.Stage.PROPOSED,
						PlanTask.Origin.PROPOSAL, from, null, "", false, null, false, null, now, now, null));
				added++;
				continue;
			}
			boolean same = t.title().equals(p.title()) && t.outcome().equals(p.outcome()) && t.dependsOn().equals(p.dependsOn())
					&& t.decision().equals(p.decision()) && t.notes().equals(p.notes());
			if (same) {
				unchanged++;
				continue;
			}
			boolean refreshable = t.stage() == PlanTask.Stage.PROPOSED && !t.edited() && t.origin() == PlanTask.Origin.PROPOSAL
					&& from.equals(t.source());
			if (refreshable) {
				tasks.put(p.id(), t.withText(p.title(), p.outcome(), p.dependsOn(), p.decision(), p.notes(), false, now).withIncoming(null, now));
				refreshed++;
			} else {
				tasks.put(p.id(), t.withIncoming(new PlanTask.Incoming(p.title(), p.outcome(), p.decision(), from, now), now));
				conflicts++;
			}
		}
		if (added + refreshed + conflicts > 0) {
			changed();
		}
		return new ImportResult(added, refreshed, unchanged, conflicts);
	}

	/**
	 * Moves every in-progress task whose thread finished a turn after its baseline to "in review". {@code thread} looks a
	 * thread up in the shell the client already has (null = not visible). Returns how many moved; writes only then.
	 */
	public synchronized int syncReview(Function<PlanTask.ThreadRef, @Nullable ThreadShell> thread) {
		load();
		long now = clock.getAsLong();
		int moved = 0;
		for (PlanTask t : new ArrayList<>(tasks.values())) {
			if (t.stage() != PlanTask.Stage.IN_PROGRESS || t.link() == null) {
				continue;
			}
			ThreadShell s = thread.apply(t.link().ref());
			if (s != null && PlanReview.finished(s, t.link().baseline())) {
				tasks.put(t.id(), t.with(PlanTask.Stage.IN_REVIEW, now));
				moved++;
			}
		}
		if (moved > 0) {
			changed();
		}
		return moved;
	}

	// ---- internals ----------------------------------------------------------------------------------------------

	private boolean move(String id, PlanTask.Stage from, PlanTask.Stage to) {
		load();
		PlanTask t = tasks.get(id);
		return t != null && t.stage() == from && put(t, t.with(to, clock.getAsLong()));
	}

	private boolean put(PlanTask before, PlanTask after) {
		tasks.put(after.id(), after);
		changed();
		return true;
	}

	/** Dependencies without the task itself, blanks or repeats. */
	private static List<String> clean(String self, List<String> deps) {
		List<String> out = new ArrayList<>();
		for (String d : deps) {
			if (d != null && !d.isBlank() && !d.equals(self) && !out.contains(d)) {
				out.add(d);
			}
		}
		return out;
	}

	private void changed() {
		version++;
		save();
	}

	private void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		version++;
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			JsonObject root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
			PlanCodec.Doc doc = PlanCodec.read(root);
			fileVersion = doc.version();
			extra = doc.extra();
			for (PlanTask t : doc.tasks()) {
				tasks.put(t.id(), t);
			}
		} catch (IOException | RuntimeException e) {
			Path aside = file.resolveSibling(file.getFileName() + ".unreadable-" + clock.getAsLong());
			try {
				Files.copy(file, aside, StandardCopyOption.REPLACE_EXISTING);
				loadProblem = "could not read " + file.getFileName() + " (" + e.getClass().getSimpleName() + "); a copy is in " + aside.getFileName();
			} catch (IOException copy) {
				loadProblem = "could not read " + file.getFileName() + " (" + e.getClass().getSimpleName() + ") nor copy it aside";
			}
			T3Craft.LOGGER.warn("[plan] {}", loadProblem);
			tasks.clear();
		}
	}

	private void save() {
		try {
			Files.createDirectories(Objects.requireNonNull(file.getParent()));
			String json = GSON.toJson(PlanCodec.write(new PlanCodec.Doc(fileVersion, new ArrayList<>(tasks.values()), extra)));
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, json, StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			saveProblem = null;
		} catch (IOException | RuntimeException e) {
			saveProblem = "could not write " + file.getFileName() + " (" + e.getClass().getSimpleName() + ")";
			T3Craft.LOGGER.warn("[plan] {}", saveProblem);
		}
	}
}
