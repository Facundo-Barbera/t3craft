package dev.t3craft.tools.render;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.PriorityBlockingQueue;
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
 * <p>What it does for each request, one at a time ({@link JobRunner}): checks the result cache, waits
 * while the machine's load is high, compiles the worktree when its code differs from the daemon's
 * baseline (a single shared gradle daemon, offline), loads the worktree's classes in a fresh child
 * class loader ({@link RenderClassLoader}) over the Minecraft classes booted once, runs the renderer
 * and publishes the files. Machine care: nice 19 / ionice idle (set by the launch script), a single
 * worker, at most one pending request per agent, previews at low resolution, outbox cleaned after 24 h.
 */
public final class RenderDaemon {
	/** Pause new work while the 1-minute load average is above this. */
	/** Pause while the 1-minute load average is above this; env RENDER_LOAD_MAX (default 8). */
	static final double LOAD_MAX = loadMax();
	private static double loadMax() {
		try {
			String v = System.getenv("RENDER_LOAD_MAX");
			return v == null || v.isBlank() ? 8.0 : Double.parseDouble(v.strip());
		} catch (NumberFormatException e) {
			return 8.0;
		}
	}

	static final long OUTBOX_MAX_AGE_MS = 24L * 60 * 60 * 1000;
	/** A final render sorts as if it had arrived this much later than it did, so previews (cheap) go first, without starving finals. */
	static final long FINAL_DELAY_MS = 10L * 60 * 1000;

	final PrintStream rawOut;
	final Spool spool;
	final Path repo;
	final String baseSha;
	final String baseCommonDir;
	final List<Path> baselineDirs;
	final Pinned pinned;
	final long startedAt = System.currentTimeMillis();

	/** A request waiting or running. */
	static final class Job implements Comparable<Job> {
		final Request req;
		final Spool.Status status;
		final long seq;
		final long sortKey;

		Job(Request req, Spool.Status status, long seq) {
			this.req = req;
			this.status = status;
			this.seq = seq;
			this.sortKey = System.currentTimeMillis() + (req.preview() ? 0 : FINAL_DELAY_MS);
		}

		@Override
		public int compareTo(Job o) {
			int c = Long.compare(sortKey, o.sortKey);
			return c != 0 ? c : Long.compare(seq, o.seq);
		}
	}

	private final PriorityBlockingQueue<Job> queue = new PriorityBlockingQueue<>();
	/** agent → id of its queued or running request (one at a time per agent). */
	private final Map<String, String> activeByAgent = new HashMap<>();
	private volatile Job running;
	private volatile boolean stop;
	private long seq;
	private long lastClean;
	private int served;

	private RenderDaemon(PrintStream rawOut, Spool spool, Path repo, String baseSha, String baseCommonDir, List<Path> baselineDirs, Pinned pinned) {
		this.rawOut = rawOut;
		this.spool = spool;
		this.repo = repo;
		this.baseSha = baseSha;
		this.baseCommonDir = baseCommonDir;
		this.baselineDirs = baselineDirs;
		this.pinned = pinned;
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
		Pinned pinned = Pinned.compute(dirs);
		RenderDaemon d = new RenderDaemon(rawOut, spool, repoRoot, sha.trim(), common.trim(), dirs, pinned);
		d.log("baseline " + d.baseSha.substring(0, 10) + " from " + repoRoot + "; " + dirs.size() + " class dirs; shared with jobs: "
				+ pinned.names.size() + " classes (blocks module + HQ kernel)");
		d.log("pinned: " + String.join(" ", pinned.sorted()));
		d.log("spool " + spool.root + "; load limit " + LOAD_MAX + "; pid " + ProcessHandle.current().pid());
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
		Thread worker = new Thread(this::workLoop, "render-worker");
		worker.setDaemon(true);
		worker.start();
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
					log("STOP file found: finishing the current job and exiting");
					Files.deleteIfExists(spool.root.resolve("STOP"));
					stop = true;
				}
			} catch (Exception e) {
				log("scan error: " + e);
			}
			Thread.sleep(2000);
		}
		while (running != null || !queue.isEmpty()) {
			Thread.sleep(1000); // let the worker drain what is already accepted
		}
		heartbeat("stopped");
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

	/** Keeps "position n of m" fresh in the status of the queued requests. */
	private void publishPositions() {
		Job[] snapshot = queue.toArray(new Job[0]);
		java.util.Arrays.sort(snapshot);
		for (int i = 0; i < snapshot.length; i++) {
			Spool.Status st = snapshot[i].status;
			String msg = "queued: position " + (i + 1) + " of " + snapshot.length + (running != null ? " (behind " + running.req.id() + ")" : "");
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
		Job r = running;
		o.addProperty("running", r == null ? "" : r.req.id());
		o.addProperty("served", served);
		o.addProperty("load1", JobRunner.load1());
		o.addProperty("loadLimit", LOAD_MAX);
		spool.heartbeat(o);
	}

	// ------------------------------------------------------------------ worker

	private void workLoop() {
		JobRunner runner = new JobRunner(this);
		while (true) {
			Job job;
			try {
				job = queue.take();
			} catch (InterruptedException e) {
				return;
			}
			running = job;
			try {
				log(job.req.id() + ": start");
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
				served++;
				synchronized (this) {
					activeByAgent.remove(job.req.agent(), job.req.id());
				}
				running = null;
			}
		}
	}

	/** The daemon cannot kill a runaway render thread; the launch script restarts it on exit code 75. */
	void giveUp(String why) {
		log("giving up: " + why);
		heartbeat("restarting");
		Runtime.getRuntime().halt(75);
	}
}
