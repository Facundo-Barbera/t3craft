package dev.t3craft.tools.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.hq.HqFixtures;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * The shared render service: ONE long-lived JVM, started by the coordinator
 * ({@code tools/render/start-daemon.sh}), that renders for dozens of agents who cannot run gradle or
 * java themselves. Protocol and limits: {@code docs/tools/render-service.md}.
 *
 * <p>Agents drop {@code inbox/<agent>-<n>.json} in {@code ~/.cache/t3craft-render} (see {@link Request});
 * the daemon answers in {@code outbox/<id>/} with the PNGs, {@code report.txt} and {@code status.json}
 * ({@code queued → compiling → rendering → done | error}).
 *
 * <p>What it does for each request ({@link JobRunner}): checks the result cache, compiles the worktree
 * when its code differs from the daemon's baseline (a single shared gradle daemon, offline, ONE compile
 * at a time: {@link #compileLock}), loads the worktree's classes in a fresh child class loader
 * ({@link RenderClassLoader}) over the Minecraft classes booted once, runs the renderer in a small
 * fork-join pool of its own and publishes the files.
 *
 * <p>Concurrency: {@link #WORKERS} worker threads (env {@code RENDER_WORKERS}, default 3) each take one
 * job at a time, so up to N jobs compile-wait/render at once; {@code RENDER_PARALLELISM} render threads
 * in total are split between them ({@link #THREADS_PER_JOB}). What is global and cannot be shared:
 * <ul>
 *   <li>the gradle daemon: {@link #compileLock} (fair), one compile at a time;</li>
 *   <li>a worktree's {@code build/} folder: {@link #worktreeLock}, so a job never renders classes another
 *       job of the same worktree is recompiling;</li>
 *   <li>the pinned (shared) globals a render may switch: {@code HqFixtures} with {@code --vanilla}, and
 *       {@code HqLayoutPacks} with {@code --packs} when that class is pinned. Such jobs take
 *       {@link #globalsLock} for writing (they render alone and restore the globals after); every other
 *       render takes it for reading;</li>
 *   <li>{@code System.out/err}: routed per thread by {@link StdRouter} into the job's {@code run.log}.</li>
 * </ul>
 * {@code ModelCache} is concurrent maps of immutable data; the renderer's own statics live in each job's
 * class loader, so they are per job.
 *
 * <p>Machine care: nice 19 / ionice idle (set by the launch script), at most one pending request per
 * agent, round-robin between agents ({@link #ordered}), previews at low resolution, outbox cleaned after
 * 24 h; when the 1-minute load is above {@link #LOAD_MAX} no job is STARTED while another one runs (it
 * drops to one active worker, never to zero, so the queue keeps moving).
 *
 * <p>The queue survives restarts: at start ({@link #recover}) every {@code outbox/<id>} left in
 * {@code queued}, {@code compiling} or {@code rendering} is queued again under the same id, in its
 * original arrival order; a STOP ({@code start-daemon.sh --stop}) finishes the running jobs and leaves
 * the rest {@code queued} on disk for the next start.
 */
public final class RenderDaemon {
	/** Start no new job while another runs and the 1-minute load average is above this; env RENDER_LOAD_MAX (default 8). */
	static final double LOAD_MAX = loadMax();
	private static double loadMax() {
		try {
			String v = System.getenv("RENDER_LOAD_MAX");
			return v == null || v.isBlank() ? 8.0 : Double.parseDouble(v.strip());
		} catch (NumberFormatException e) {
			return 8.0;
		}
	}

	/** Jobs at once: -Dt3craft.render.workers or env RENDER_WORKERS (default 3). */
	static final int WORKERS = setting("t3craft.render.workers", "RENDER_WORKERS", 3, 1, 32);
	/** Render threads in total: -Dt3craft.render.parallelism or env RENDER_PARALLELISM (default 6). */
	static final int PARALLELISM = setting("t3craft.render.parallelism", "RENDER_PARALLELISM", 6, 1, 256);
	/** Fork-join threads of one job (RENDER_PARALLELISM / RENDER_WORKERS, at least 1). */
	static final int THREADS_PER_JOB = Math.max(1, PARALLELISM / WORKERS);

	private static int setting(String prop, String env, int dflt, int min, int max) {
		String v = System.getProperty(prop);
		if (v == null || v.isBlank()) {
			v = System.getenv(env);
		}
		try {
			return v == null || v.isBlank() ? dflt : Math.max(min, Math.min(max, Integer.parseInt(v.strip())));
		} catch (NumberFormatException e) {
			return dflt;
		}
	}

	/** Round-robin window: an agent's job starts in the last 30 min decide its turn. */
	static final long RR_WINDOW_MS = 30L * 60 * 1000;
	/**
	 * Global layout-pack state a {@code --packs} render switches (shared between jobs only when pinned).
	 * A string on purpose: read by {@code pinned.isShared} and {@code JobRunner.exclusive}, to name a class that
	 * {@code RenderClassLoader} loads per job (this tool's source set cannot reference it).
	 */
	static final String PACKS_CLASS = "dev.t3craft.hq.pack.HqLayoutPacks";

	static final long OUTBOX_MAX_AGE_MS = 24L * 60 * 60 * 1000;
	/** A final render sorts as if it had arrived this much later than it did, so previews (cheap) go first, without starving finals. */
	static final long FINAL_DELAY_MS = 10L * 60 * 1000;
	/** A request interrupted mid-job by more daemon restarts than this is failed instead of queued again (it may be what kills the daemon). */
	static final int MAX_RESTARTS = 2;
	static final String REQUEUED = "requeued after daemon restart";

	final PrintStream rawOut;
	final Spool spool;
	final Path repo;
	final String baseSha;
	final String baseCommonDir;
	final List<Path> baselineDirs;
	final Pinned pinned;
	/** The fixtures the daemon booted with; restored after each {@code --vanilla} render. */
	final HqFixtures fixtures;
	final long startedAt = System.currentTimeMillis();

	/** One compile at a time (the gradle daemon is shared); fair, so compiles go in arrival order. */
	final ReentrantLock compileLock = new ReentrantLock(true);
	/** Read: a normal render. Write: a render that switches a shared global (see the class comment). */
	final ReentrantReadWriteLock globalsLock = new ReentrantReadWriteLock(true);
	private final Map<Path, ReentrantLock> worktreeLocks = new ConcurrentHashMap<>();

	/** The lock of one worktree's build folder, held for a job's compile and render. */
	ReentrantLock worktreeLock(Path worktree) {
		Path key = worktree.toAbsolutePath().normalize();
		return worktreeLocks.computeIfAbsent(key, k -> new ReentrantLock(true));
	}

	/** A request waiting or running. */
	static final class Job implements Comparable<Job> {
		final Request req;
		final Spool.Status status;
		final long seq;
		final long sortKey;
		/** Recovered from the outbox at start (shown in its queued message). */
		final boolean requeued;

		Job(Request req, Spool.Status status, long seq) {
			this(req, status, seq, System.currentTimeMillis(), false);
		}

		/** {@code arrivedAt}: when the request first arrived, so a recovered job keeps its place. */
		Job(Request req, Spool.Status status, long seq, long arrivedAt, boolean requeued) {
			this.req = req;
			this.status = status;
			this.seq = seq;
			this.sortKey = arrivedAt + (req.preview() ? 0 : FINAL_DELAY_MS);
			this.requeued = requeued;
		}

		@Override
		public int compareTo(Job o) {
			int c = Long.compare(sortKey, o.sortKey);
			return c != 0 ? c : Long.compare(seq, o.seq);
		}
	}

	private final PriorityBlockingQueue<Job> queue = new PriorityBlockingQueue<>();
	/** agent → id of its queued or running request (one at a time per agent). Guarded by {@code this}. */
	private final Map<String, String> activeByAgent = new HashMap<>();
	/** The jobs being run, in start order. Guarded by {@code this}. */
	private final List<Job> running = new ArrayList<>();
	/** agent → start times of its jobs in the last {@link #RR_WINDOW_MS} (round-robin). Guarded by {@code this}. */
	private final Map<String, Deque<Long>> starts = new HashMap<>();
	/** Last admission found the load above the limit (only one job runs). */
	private volatile boolean loadPaused;
	private volatile boolean stop;
	private long seq;
	private long lastClean;
	private final AtomicInteger served = new AtomicInteger();

	private RenderDaemon(PrintStream rawOut, Spool spool, Path repo, String baseSha, String baseCommonDir, List<Path> baselineDirs, Pinned pinned,
			HqFixtures fixtures) {
		this.rawOut = rawOut;
		this.spool = spool;
		this.repo = repo;
		this.baseSha = baseSha;
		this.baseCommonDir = baseCommonDir;
		this.baselineDirs = baselineDirs;
		this.pinned = pinned;
		this.fixtures = fixtures;
	}

	public static void main(String[] args) throws Exception {
		PrintStream rawOut = System.out; // before Minecraft's bootstrap wraps the streams
		System.setProperty("java.awt.headless", "true");
		Path repoRoot = Path.of(System.getProperty("t3craft.render.repo", System.getProperty("user.dir"))).toAbsolutePath().normalize();
		Spool spool = new Spool(Spool.defaultRoot());
		String sha = Proc.git(repoRoot, "rev-parse", "HEAD");
		String common = Proc.git(repoRoot, "rev-parse", "--path-format=absolute", "--git-common-dir");
		if (sha == null || common == null) {
			throw new IllegalStateException(repoRoot + " is not a git checkout (set -Dt3craft.render.repo=...)");
		}
		List<Path> dirs = new ArrayList<>();
		for (String e : System.getProperty("java.class.path", "").split(java.io.File.pathSeparator)) {
			if (!e.isBlank() && Files.isDirectory(Path.of(e))) {
				dirs.add(Path.of(e).toAbsolutePath().normalize());
			}
		}
		stamp(rawOut, "booting Minecraft registries and the mod's blocks (once)...");
		Boot.boot();
		// after the bootstrap wrapped the streams: from here each job's threads print into its own run.log
		StdRouter.install();
		Pinned pinned = Pinned.compute(dirs);
		RenderDaemon d = new RenderDaemon(rawOut, spool, repoRoot, sha.trim(), common.trim(), dirs, pinned, HqFixtures.active());
		d.log("baseline " + d.baseSha.substring(0, 10) + " from " + repoRoot + "; " + dirs.size() + " class dirs; shared with jobs: "
				+ pinned.names.size() + " classes (blocks module + HQ kernel)");
		d.log("pinned: " + String.join(" ", pinned.sorted()));
		d.log("spool " + spool.root + "; load limit " + LOAD_MAX + "; " + WORKERS + " worker(s) x " + THREADS_PER_JOB + " render thread(s); max heap "
				+ (Runtime.getRuntime().maxMemory() >> 20) + " MB; HqLayoutPacks " + (pinned.isShared(PACKS_CLASS) ? "pinned (--packs renders alone)" : "per job")
				+ "; pid " + ProcessHandle.current().pid());
		Runtime.getRuntime().addShutdownHook(new Thread(() -> d.heartbeat("stopped")));
		d.run();
	}

	private static void stamp(PrintStream out, String msg) {
		out.println(Instant.now() + " " + msg);
	}

	void log(String msg) {
		stamp(rawOut, msg);
	}

	// ------------------------------------------------------------------ main loop

	private void run() throws Exception {
		try {
			recover();
		} catch (Exception e) {
			log("recovery error: " + e);
		}
		List<Thread> workers = new ArrayList<>();
		for (int i = 1; i <= WORKERS; i++) {
			Thread worker = new Thread(this::workLoop, "render-worker-" + i);
			worker.setDaemon(true);
			worker.start();
			workers.add(worker);
		}
		spool.clean(OUTBOX_MAX_AGE_MS, busyIds());
		lastClean = System.currentTimeMillis();
		while (!stop) {
			try {
				scan();
				publishPositions();
				heartbeat("running");
				if (System.currentTimeMillis() - lastClean > 60 * 60 * 1000L) {
					int n = spool.clean(OUTBOX_MAX_AGE_MS, busyIds());
					lastClean = System.currentTimeMillis();
					if (n > 0) {
						log("cleaned " + n + " outbox folders older than 24 h");
					}
				}
				if (Files.exists(spool.root.resolve("STOP"))) {
					log("STOP file found: finishing the running job(s) and exiting; " + queue.size() + " queued request(s) stay queued for the next start");
					Files.deleteIfExists(spool.root.resolve("STOP"));
					stop = true;
				}
			} catch (Exception e) {
				log("scan error: " + e);
			}
			Thread.sleep(2000);
		}
		// each worker sees `stop`, finishes the job in hand and takes no other
		for (Thread worker : workers) {
			while (worker.isAlive()) {
				heartbeat("stopping");
				worker.join(2000);
			}
		}
		Job[] left = queue.toArray(new Job[0]);
		java.util.Arrays.sort(left);
		for (Job j : left) {
			Spool.Status st = j.status;
			st.state = "queued";
			st.position = 0;
			st.message = "queued; the render daemon stopped and resumes this request when it starts again";
			spool.write(st);
		}
		if (left.length > 0) {
			log(left.length + " request(s) left queued in the outbox for the next start");
		}
		heartbeat("stopped");
	}

	// ------------------------------------------------------------------ recovery

	/** One unfinished outbox folder found at start. */
	private record Unfinished(String id, Request req, Spool.Status status, long arrivedAt) {
	}

	/**
	 * Requeues what a previous daemon left unfinished: {@code outbox/<id>/status.json} in {@code queued},
	 * {@code compiling} or {@code rendering}. Same id (an agent waiting on it with {@code submit.sh} just sees
	 * the state move on), original arrival order, still one pending request per agent (the newest wins, older
	 * ones end as "superseded by &lt;id&gt;"). Without a valid {@code request.json} the folder ends in
	 * {@code error} asking to send it again. Runs before the worker starts and before the first inbox scan.
	 */
	private void recover() throws IOException {
		List<Path> dirs = new ArrayList<>();
		try (Stream<Path> s = Files.list(spool.outbox)) {
			s.filter(Files::isDirectory).forEach(dirs::add);
		}
		List<Unfinished> found = new ArrayList<>();
		long now = System.currentTimeMillis();
		for (Path dir : dirs) {
			String id = dir.getFileName().toString();
			Path statusFile = dir.resolve("status.json");
			if (!Files.isRegularFile(statusFile)) {
				continue;
			}
			JsonObject old;
			try {
				JsonElement e = JsonParser.parseString(Files.readString(statusFile));
				if (!e.isJsonObject()) {
					continue;
				}
				old = e.getAsJsonObject();
			} catch (IOException | RuntimeException e) {
				log("recovery: unreadable " + statusFile + ": " + e);
				continue;
			}
			String state = str(old, "state");
			if (!"queued".equals(state) && !"compiling".equals(state) && !"rendering".equals(state)) {
				continue;
			}
			boolean wasRunning = !state.equals("queued");
			int restarts = (old.has("restarts") ? intOr(old.get("restarts"), 0) : 0) + (wasRunning ? 1 : 0);
			long arrivedAt = instantOr(str(old, "queuedAt"), fileTime(dir.resolve("request.json"), fileTime(statusFile, now)));

			Request req = null;
			String why = null;
			Path reqFile = dir.resolve("request.json");
			if (!Request.ID.matcher(id).matches()) {
				why = "its id is not valid";
			} else if (!Files.isRegularFile(reqFile)) {
				why = "its request.json is missing";
			} else {
				try {
					req = Request.parse(id, Files.readString(reqFile));
				} catch (IOException | RuntimeException e) {
					why = "its request.json is not valid (" + e.getMessage() + ")";
				}
			}
			if (req == null) {
				Spool.Status st = new Spool.Status(id, strOr(old, "agent", Request.agentOf(id)), strOr(old, "kind", "?"), strOr(old, "priority", "?"));
				st.queuedAt = arrivedAt;
				fail(st, "the render daemon restarted and could not resume this request: " + why
						+ ". Send it again (tools/render/submit.sh, a new number in the file name).");
				continue;
			}
			Spool.Status st = new Spool.Status(req);
			st.queuedAt = arrivedAt;
			st.restarts = restarts;
			if (now - arrivedAt > OUTBOX_MAX_AGE_MS) {
				fail(st, "the render daemon restarted and this request is older than 24 h; send it again if you still need it (a new number in the file name)");
				continue;
			}
			if (restarts > MAX_RESTARTS) {
				fail(st, "the render daemon restarted " + restarts + " times while running this request, so it is not resumed again "
						+ "(it may be what brings the daemon down). Check report.txt / run.log, then send it again with a new number.");
				continue;
			}
			found.add(new Unfinished(id, req, st, arrivedAt));
		}
		if (found.isEmpty()) {
			return;
		}
		// arrival order; within one agent the newest one stays pending
		found.sort(Comparator.comparingLong(Unfinished::arrivedAt).thenComparing(Unfinished::id));
		Map<String, Unfinished> newest = new HashMap<>();
		for (Unfinished u : found) {
			newest.put(u.req().agent(), u); // later in arrival order overwrites
		}
		int requeued = 0;
		for (Unfinished u : found) {
			Unfinished keep = newest.get(u.req().agent());
			if (keep != u) {
				fail(u.status(), "superseded by " + keep.id());
				continue;
			}
			String bad = JobRunner.checkWorktree(this, u.req());
			if (bad != null) {
				fail(u.status(), "rejected: " + bad);
				continue;
			}
			synchronized (this) {
				activeByAgent.put(u.req().agent(), u.id());
			}
			Spool.Status st = u.status();
			st.state = "queued";
			st.position = 0;
			st.message = REQUEUED;
			queue.add(new Job(u.req(), st, seq++, u.arrivedAt(), true));
			spool.write(st);
			requeued++;
			log(u.id() + ": " + REQUEUED + " (" + u.req().kind() + ", " + st.priority + (st.restarts > 0 ? ", interrupted " + st.restarts + "x" : "") + ")");
		}
		log("recovery: " + requeued + " request(s) requeued, " + (found.size() - requeued) + " closed");
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || !e.isJsonPrimitive() ? null : e.getAsString();
	}

	private static String strOr(JsonObject o, String k, String dflt) {
		String v = str(o, k);
		return v == null || v.isBlank() ? dflt : v;
	}

	private static int intOr(JsonElement e, int dflt) {
		try {
			return e.getAsInt();
		} catch (RuntimeException ex) {
			return dflt;
		}
	}

	private static long instantOr(String iso, long dflt) {
		if (iso == null) {
			return dflt;
		}
		try {
			return Instant.parse(iso).toEpochMilli();
		} catch (RuntimeException e) {
			return dflt;
		}
	}

	private static long fileTime(Path p, long dflt) {
		try {
			return Files.getLastModifiedTime(p).toMillis();
		} catch (IOException e) {
			return dflt;
		}
	}

	private synchronized Set<String> busyIds() {
		Set<String> ids = new HashSet<>(activeByAgent.values());
		return ids;
	}

	/** New inbox files → status + queue (or cache hit, or rejection). */
	private void scan() throws IOException {
		List<Path> files = new ArrayList<>();
		try (Stream<Path> s = Files.list(spool.inbox)) {
			s.filter(p -> {
				String n = p.getFileName().toString();
				return n.endsWith(".json") && !n.startsWith(".");
			}).forEach(files::add);
		}
		files.sort(null);
		long now = System.currentTimeMillis();
		for (Path f : files) {
			FileTime t = Files.getLastModifiedTime(f);
			if (now - t.toMillis() < 700) {
				continue; // may still be being written
			}
			try {
				accept(f);
			} catch (Exception e) {
				log("could not accept " + f.getFileName() + ": " + e);
				Files.deleteIfExists(f);
			}
		}
	}

	private void accept(Path file) throws IOException, InterruptedException {
		String name = file.getFileName().toString();
		String id = name.substring(0, name.length() - ".json".length());
		if (!Request.ID.matcher(id).matches()) {
			log("rejected inbox file with a bad name: " + name + " (use letters, digits, . _ - only)");
			Files.move(file, spool.inbox.resolve("." + name + ".rejected"), StandardCopyOption.REPLACE_EXISTING);
			return;
		}
		if (busyIds().contains(id)) {
			// the same request written again (e.g. after a daemon restart): it is already queued or running
			Files.deleteIfExists(file);
			log(id + ": inbox copy ignored, the request is already queued or running");
			return;
		}
		if (Files.exists(spool.outDir(id).resolve("status.json"))) {
			// the old answer stays untouched; the refusal goes to a folder of its own
			Spool.Status dup = new Spool.Status(id + "-dup-" + System.currentTimeMillis(), Request.agentOf(id), "?", "?");
			dup.state = "error";
			dup.message = "rejected: a request with id " + id + " was already handled; use a new number in the file name";
			dup.finishedAt = System.currentTimeMillis();
			Files.deleteIfExists(file);
			spool.write(dup);
			log(dup.message);
			return;
		}
		Request req;
		try {
			req = Request.read(file);
		} catch (RuntimeException | IOException e) {
			Spool.Status st = new Spool.Status(id, Request.agentOf(id), "?", "?");
			st.state = "error";
			st.message = "invalid request: " + e.getMessage();
			st.finishedAt = System.currentTimeMillis();
			Files.createDirectories(spool.outDir(id));
			Files.move(file, spool.outDir(id).resolve("request.json"), StandardCopyOption.REPLACE_EXISTING);
			spool.write(st);
			log(id + ": " + st.message);
			return;
		}
		Files.createDirectories(spool.outDir(id));
		Files.move(file, spool.outDir(id).resolve("request.json"), StandardCopyOption.REPLACE_EXISTING);
		Spool.Status st = new Spool.Status(req);
		String bad = JobRunner.checkWorktree(this, req);
		if (bad != null) {
			fail(st, "rejected: " + bad);
			return;
		}
		// a repeated request (same code, same arguments) is answered from the cache, even if the agent is busy
		JobRunner runner = new JobRunner(this);
		String key = runner.cacheKey(req);
		if (key != null && runner.restoreFromCache(key, req, st)) {
			spool.write(st);
			log(id + ": served from cache");
			return;
		}
		synchronized (this) {
			String other = activeByAgent.get(req.agent());
			if (other != null) {
				fail(st, "rejected: agent " + req.agent() + " already has request " + other + " queued or running; only one at a time. "
						+ "Wait for it (tools/render/submit.sh waits by default) and send the next one after.");
				return;
			}
			activeByAgent.put(req.agent(), id);
		}
		Job job = new Job(req, st, seq++);
		st.message = "queued";
		queue.add(job);
		spool.write(st);
		log(id + ": queued (" + req.kind() + ", " + st.priority + ", " + req.worktree() + ")");
	}

	private void fail(Spool.Status st, String message) {
		st.state = "error";
		st.message = message;
		st.finishedAt = System.currentTimeMillis();
		spool.write(st);
		log(st.id + ": " + message);
	}

	/**
	 * The queued jobs in the order they will start: round-robin between agents (fewest job starts in the
	 * last {@link #RR_WINDOW_MS} first), and within the same turn the usual order (previews first, finals by
	 * age, {@link Job#compareTo}). With one pending request per agent this means every agent waiting gets a
	 * turn before an agent that just had one gets another.
	 */
	private synchronized List<Job> ordered() {
		long now = System.currentTimeMillis();
		Map<String, Integer> turns = new HashMap<>();
		List<Job> jobs = new ArrayList<>(List.of(queue.toArray(new Job[0])));
		for (Job j : jobs) {
			turns.computeIfAbsent(j.req.agent(), a -> recentStarts(a, now));
		}
		jobs.sort(Comparator.<Job>comparingInt(j -> turns.get(j.req.agent())).thenComparing(Comparator.naturalOrder()));
		return jobs;
	}

	/** Guarded by {@code this}. */
	private int recentStarts(String agent, long now) {
		Deque<Long> d = starts.get(agent);
		if (d == null) {
			return 0;
		}
		while (!d.isEmpty() && now - d.peekFirst() > RR_WINDOW_MS) {
			d.pollFirst();
		}
		if (d.isEmpty()) {
			starts.remove(agent);
			return 0;
		}
		return d.size();
	}

	/**
	 * Admission: the next job to run, now marked running, or null (queue empty, stopping, or the load is
	 * above {@link #LOAD_MAX} while another job runs: under load it drops to ONE active job, never zero).
	 */
	private synchronized Job admit() {
		if (queue.isEmpty()) {
			loadPaused = false;
			return null;
		}
		if (stop || running.size() >= WORKERS) {
			return null;
		}
		if (!running.isEmpty() && JobRunner.load1() > LOAD_MAX) {
			loadPaused = true;
			return null;
		}
		loadPaused = false;
		List<Job> order = ordered();
		if (order.isEmpty()) {
			return null;
		}
		Job job = order.get(0);
		if (!queue.remove(job)) {
			return null;
		}
		running.add(job);
		starts.computeIfAbsent(job.req.agent(), a -> new ArrayDeque<>()).addLast(System.currentTimeMillis());
		return job;
	}

	private synchronized List<Job> runningJobs() {
		return new ArrayList<>(running);
	}

	/**
	 * Keeps "position n of m" fresh in the status of the queued requests. Holds the admission lock while
	 * writing, so a job a worker just took is never written back as queued.
	 */
	private synchronized void publishPositions() {
		List<Job> order = ordered();
		String behind = running.isEmpty() ? "" : " (running: " + String.join(", ", running.stream().map(j -> j.req.id()).toList()) + ")";
		String paused = loadPaused ? String.format(java.util.Locale.ROOT, "; machine busy (load > %.0f): one job at a time", LOAD_MAX) : "";
		for (int i = 0; i < order.size(); i++) {
			Job j = order.get(i);
			Spool.Status st = j.status;
			String msg = "queued: position " + (i + 1) + " of " + order.size() + behind + paused + (j.requeued ? "; " + REQUEUED : "");
			if (st.position != i + 1 || !msg.equals(st.message)) {
				st.position = i + 1;
				st.message = msg;
				spool.write(st);
			}
		}
	}

	private void heartbeat(String state) {
		JsonObject o = new JsonObject();
		o.addProperty("state", state);
		o.addProperty("pid", ProcessHandle.current().pid());
		o.addProperty("startedAt", Instant.ofEpochMilli(startedAt).toString());
		o.addProperty("now", Instant.now().toString());
		o.addProperty("nowMillis", System.currentTimeMillis());
		o.addProperty("baseline", baseSha);
		o.addProperty("queued", queue.size());
		List<Job> now = runningJobs();
		JsonArray arr = new JsonArray();
		for (Job j : now) {
			Spool.Status st = j.status;
			JsonObject r = new JsonObject();
			r.addProperty("id", j.req.id());
			r.addProperty("agent", j.req.agent());
			r.addProperty("kind", j.req.kind());
			r.addProperty("priority", st.priority);
			r.addProperty("state", st.state);
			r.addProperty("message", st.message);
			if (st.startedAt > 0) {
				r.addProperty("startedAt", Instant.ofEpochMilli(st.startedAt).toString());
			}
			arr.add(r);
		}
		// every job in progress; runningId keeps the old single string (the first one, "" when idle)
		o.add("running", arr);
		o.addProperty("runningId", now.isEmpty() ? "" : now.get(0).req.id());
		o.addProperty("workers", WORKERS);
		o.addProperty("threadsPerJob", THREADS_PER_JOB);
		o.addProperty("served", served.get());
		o.addProperty("load1", JobRunner.load1());
		o.addProperty("loadLimit", LOAD_MAX);
		o.addProperty("loadPaused", loadPaused);
		spool.heartbeat(o);
	}

	// ------------------------------------------------------------------ workers

	private void workLoop() {
		JobRunner runner = new JobRunner(this);
		while (!stop) {
			Job job = admit();
			if (job == null) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					return;
				}
				continue;
			}
			try {
				log(job.req.id() + ": start on " + Thread.currentThread().getName());
				runner.execute(job);
				log(job.req.id() + ": " + job.status.state + " - " + job.status.message);
			} catch (Throwable t) {
				job.status.state = "error";
				job.status.message = "internal error in the render daemon: " + t;
				job.status.finishedAt = System.currentTimeMillis();
				spool.write(job.status);
				log(job.req.id() + ": internal error " + t);
				t.printStackTrace(rawOut);
			} finally {
				served.incrementAndGet();
				synchronized (this) {
					activeByAgent.remove(job.req.agent(), job.req.id());
					running.remove(job);
				}
			}
		}
	}

	/**
	 * The daemon cannot kill a runaway render thread; the launch script restarts it on exit code 75. The
	 * stuck job is failed (it would get stuck again); the other running jobs are left {@code queued} on disk
	 * so {@link #recover} resumes them without counting a restart against them.
	 */
	void giveUp(String why, Job stuck) {
		log("giving up: " + why);
		if (stuck != null && !stuck.status.terminal()) {
			fail(stuck.status, "the render thread did not stop and the daemon restarted; not resumed. See report.txt");
		}
		for (Job j : runningJobs()) {
			if (j != stuck && !j.status.terminal()) {
				j.status.state = "queued";
				j.status.position = 0;
				j.status.message = "queued; another job's stuck render restarted the daemon";
				spool.write(j.status);
			}
		}
		heartbeat("restarting");
		Runtime.getRuntime().halt(75);
	}
}
