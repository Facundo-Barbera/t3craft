package dev.t3craft.tools.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.hq.HqFixtures;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Runs one {@link RenderDaemon.Job} start to finish: cache, load gate, compile (only when the worktree's
 * code differs from the daemon's baseline), child class loader, the renderer itself, files and report.
 * Called from the single worker thread, so nothing here runs concurrently with another job.
 */
final class JobRunner {
	private static final long PREVIEW_TIMEOUT_MS = 20L * 60 * 1000;
	private static final long FINAL_TIMEOUT_MS = 60L * 60 * 1000;
	private static final long COMPILE_TIMEOUT_SEC = 25L * 60;

	/** kind → entry point (a public static method taking String[]), looked up in the job's class loader. */
	private record Entry(String className, String method) {
	}

	private static Entry entryOf(String kind) {
		return switch (kind) {
			// ./gradlew hqRender's renderer, minus the boot (the daemon booted once)
			case "hq" -> new Entry("dev.t3craft.tools.hqrender.HqRender", "run");
			// the plug for tools/ui-render: public static void render(String[] args) in this class (see docs/tools/render-service.md)
			case "ui" -> new Entry("dev.t3craft.tools.uirender.UiRender", "render");
			default -> throw new IllegalArgumentException(kind);
		};
	}

	private static volatile List<String> nicePrefix;

	private final RenderDaemon d;

	JobRunner(RenderDaemon d) {
		this.d = d;
	}

	// ------------------------------------------------------------------ checks

	/** A message when the worktree cannot be served, else null. */
	static String checkWorktree(RenderDaemon d, Request r) {
		Path wt = r.worktree();
		try {
			if (!Files.isDirectory(wt)) {
				return "the worktree does not exist: " + wt;
			}
			if (!Files.isRegularFile(wt.resolve("mod/build.gradle"))) {
				return "not a t3craft worktree (no mod/build.gradle): " + wt;
			}
			String common = Proc.git(wt, "rev-parse", "--path-format=absolute", "--git-common-dir");
			if (common == null) {
				return "not a git worktree: " + wt;
			}
			if (!Path.of(common.trim()).toRealPath().equals(Path.of(d.baseCommonDir).toRealPath())) {
				return "that worktree belongs to another repository than the daemon's (" + d.baseCommonDir + ")";
			}
			for (int i = 0; i + 1 < r.args().size(); i++) {
				if (r.args().get(i).equals("--packs") && !Files.isDirectory(Path.of(r.args().get(i + 1)))) {
					return "--packs folder does not exist: " + r.args().get(i + 1);
				}
			}
		} catch (IOException | InterruptedException | RuntimeException e) {
			return "cannot inspect the worktree: " + e;
		}
		return null;
	}

	static double load1() {
		try {
			String s = Files.readString(Path.of("/proc/loadavg"));
			return Double.parseDouble(s.trim().split("\\s+")[0]);
		} catch (IOException | RuntimeException e) {
			double l = java.lang.management.ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
			return l < 0 ? 0 : l;
		}
	}

	private void waitForLoad(Spool.Status st, String phase) throws InterruptedException {
		double l = load1();
		if (l <= RenderDaemon.LOAD_MAX) {
			return;
		}
		long last = 0;
		while ((l = load1()) > RenderDaemon.LOAD_MAX) {
			long now = System.currentTimeMillis();
			if (now - last > 15000) {
				st.message = String.format(Locale.ROOT, "paused: machine busy (load average %.1f > %.0f), waiting %s", l, RenderDaemon.LOAD_MAX, phase);
				st.position = 0;
				d.spool.write(st);
				last = now;
			}
			Thread.sleep(10000);
		}
	}

	// ------------------------------------------------------------------ cache

	/** Hash of everything that decides the output: baseline, kind, args, the worktree's commit, uncommitted diff, untracked files, pack files. Null if git fails. */
	String cacheKey(Request r) throws IOException, InterruptedException {
		Path wt = r.worktree();
		String head = Proc.git(wt, "rev-parse", "HEAD");
		if (head == null) {
			return null;
		}
		MessageDigest md = sha256();
		put(md, d.baseSha);
		put(md, r.kind());
		put(md, r.preview() ? "preview" : "final");
		for (String a : r.args()) {
			put(md, a);
		}
		put(md, head.trim());
		Proc.Result diff = Proc.capture(List.of("git", "diff", "HEAD", "--binary"), wt, 60, 64 << 20);
		md.update(diff.out());
		Proc.Result un = Proc.capture(List.of("git", "ls-files", "-o", "--exclude-standard", "-z"), wt, 60, 8 << 20);
		String[] paths = un.text().split("\0");
		Arrays.sort(paths);
		int n = 0;
		for (String p : paths) {
			if (p.isEmpty() || n++ > 3000) {
				continue;
			}
			Path f = wt.resolve(p);
			if (Files.isRegularFile(f) && Files.size(f) <= (4 << 20)) {
				put(md, p);
				md.update(Files.readAllBytes(f));
			}
		}
		for (int i = 0; i + 1 < r.args().size(); i++) {
			if (r.args().get(i).equals("--packs")) {
				hashTree(md, Path.of(r.args().get(i + 1)));
			}
		}
		return hex(md.digest());
	}

	private static void hashTree(MessageDigest md, Path dir) throws IOException {
		if (!Files.isDirectory(dir)) {
			return;
		}
		List<Path> files = new ArrayList<>();
		try (Stream<Path> s = Files.walk(dir)) {
			s.filter(Files::isRegularFile).forEach(files::add);
		}
		files.sort(null);
		int n = 0;
		for (Path f : files) {
			if (n++ > 5000 || Files.size(f) > (16 << 20)) {
				continue;
			}
			put(md, dir.relativize(f).toString());
			md.update(Files.readAllBytes(f));
		}
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void put(MessageDigest md, String s) {
		md.update(s.getBytes(StandardCharsets.UTF_8));
		md.update((byte) 0);
	}

	private static String hex(byte[] b) {
		StringBuilder sb = new StringBuilder();
		for (byte x : b) {
			sb.append(String.format("%02x", x));
		}
		return sb.toString();
	}

	/** Fills {@code st} from the stored result of an identical earlier request; false if there is none. */
	boolean restoreFromCache(String key, Request r, Spool.Status st) {
		try {
			Path entry = d.spool.cache.resolve(key);
			if (!Files.isRegularFile(entry)) {
				return false;
			}
			String origId = Files.readString(entry).trim();
			Path origDir = d.spool.outDir(origId);
			Path origStatus = origDir.resolve("status.json");
			if (!Files.isRegularFile(origStatus)) {
				return false;
			}
			JsonObject o = JsonParser.parseString(Files.readString(origStatus)).getAsJsonObject();
			if (!"done".equals(o.get("state").getAsString())) {
				return false;
			}
			Path out = Files.createDirectories(d.spool.outDir(r.id()));
			List<String> copied = new ArrayList<>();
			for (var f : o.getAsJsonArray("files")) {
				Path from = Path.of(f.getAsString());
				Path rel = origDir.relativize(from);
				Path to = out.resolve(rel.toString());
				if (!Files.isRegularFile(from)) {
					return false;
				}
				if (to.getParent() != null) {
					Files.createDirectories(to.getParent());
				}
				Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
				copied.add(to.toString());
			}
			Path rep = origDir.resolve("report.txt");
			if (Files.isRegularFile(rep)) {
				String text = "(cached result of " + origId + ": same code, same arguments)\n\n" + Files.readString(rep);
				Files.writeString(out.resolve("report.txt"), text);
				st.report = out.resolve("report.txt").toString();
			}
			st.files.clear();
			st.files.addAll(copied);
			st.state = "done";
			st.cached = true;
			st.message = "cached result of " + origId + " (same code and arguments, nothing rendered)";
			st.startedAt = st.queuedAt;
			st.finishedAt = System.currentTimeMillis();
			return true;
		} catch (IOException | RuntimeException e) {
			return false;
		}
	}

	private void remember(String key, Request r) {
		try {
			Files.writeString(d.spool.cache.resolve(key), r.id());
		} catch (IOException e) {
			d.log("cache write failed: " + e);
		}
	}

	// ------------------------------------------------------------------ the job

	void execute(RenderDaemon.Job job) throws Exception {
		Request r = job.req;
		Spool.Status st = job.status;
		Path out = Files.createDirectories(d.spool.outDir(r.id()));
		st.startedAt = System.currentTimeMillis();
		long t0 = System.nanoTime();

		// the worktree may have changed while the request waited: the key is recomputed now
		String key = cacheKey(r);
		if (key != null && restoreFromCache(key, r, st)) {
			d.spool.write(st);
			return;
		}

		List<Path> roots;
		String origin;
		long compileMs = 0;
		boolean compile = needsCompile(r.worktree());
		waitForLoad(st, compile ? "to compile" : "to render");
		if (compile) {
			set(st, "compiling", "compiling the worktree (shared gradle daemon, offline, niced)");
			long c0 = System.nanoTime();
			roots = compile(r, out, st);
			compileMs = (System.nanoTime() - c0) / 1_000_000;
			if (roots == null) {
				return; // compile() already reported the error
			}
			List<String> changed = d.pinned.changedIn(d.baselineDirs, roots);
			if (!changed.isEmpty()) {
				finish(st, "error", "your change touches classes the daemon loads once at start and cannot reload: " + String.join(", ", changed)
						+ ". Ask the coordinator to restart the daemon from a checkout that has them (docs/tools/render-service.md, \"Kernel\").");
				return;
			}
			origin = "compiled from the worktree (" + (compileMs / 1000) + " s)";
		} else {
			roots = d.baselineDirs;
			origin = "daemon baseline " + d.baseSha.substring(0, 10) + " (the worktree has no code changes; packs and arguments only)";
		}

		set(st, "rendering", "rendering (" + (r.preview() ? "preview" : "final") + ")");
		long r0 = System.nanoTime();
		Run run = render(r, roots, out);
		long renderMs = (System.nanoTime() - r0) / 1_000_000;

		List<String> pngs = new ArrayList<>();
		try (Stream<Path> s = Files.walk(out)) {
			s.filter(p -> p.getFileName().toString().endsWith(".png")).sorted().forEach(p -> pngs.add(p.toString()));
		}
		Path report = out.resolve("report.txt");
		String head = Proc.git(r.worktree(), "rev-parse", "--short=10", "HEAD");
		StringBuilder rep = new StringBuilder();
		rep.append("render ").append(r.id()).append("  agent=").append(r.agent()).append("  kind=").append(r.kind())
				.append("  priority=").append(r.preview() ? "preview" : "final").append('\n');
		rep.append("worktree: ").append(r.worktree()).append("  HEAD ").append(head == null ? "?" : head.trim()).append('\n');
		rep.append("code: ").append(origin).append('\n');
		rep.append("arguments: ").append(String.join(" ", run.argv())).append('\n');
		rep.append(String.format(Locale.ROOT, "time: waited %.0f s, compiled %.1f s, rendered %.1f s%n",
				(st.startedAt - st.queuedAt) / 1000.0, compileMs / 1000.0, renderMs / 1000.0));
		for (String n : r.notes()) {
			rep.append("note: ").append(n).append('\n');
		}
		rep.append("images: ").append(pngs.size()).append('\n');
		for (String p : pngs) {
			rep.append("  ").append(p).append('\n');
		}
		rep.append("\n--- output ---\n").append(run.log());
		Files.writeString(report, rep.toString());
		st.report = report.toString();
		st.files.clear();
		st.files.addAll(pngs);
		st.log = lastLines(run.log(), 12);

		if (run.error() != null) {
			finish(st, "error", "render failed: " + run.error());
			return;
		}
		if (pngs.isEmpty() && r.kind().equals("hq")) {
			finish(st, "error", "the renderer finished but wrote no images (a --only filter that matches nothing?); see report.txt");
			return;
		}
		finish(st, "done", pngs.size() + " image(s) in " + String.format(Locale.ROOT, "%.0f", (System.nanoTime() - t0) / 1e9) + " s");
		if (key != null) {
			remember(key, r);
		}
	}

	private void set(Spool.Status st, String state, String message) {
		st.state = state;
		st.message = message;
		st.position = 0;
		d.spool.write(st);
	}

	private void finish(Spool.Status st, String state, String message) {
		st.state = state;
		st.message = message;
		st.finishedAt = System.currentTimeMillis();
		d.spool.write(st);
	}

	private static String lastLines(String text, int n) {
		String[] l = text.split("\n");
		return String.join("\n", Arrays.asList(l).subList(Math.max(0, l.length - n), l.length));
	}

	// ------------------------------------------------------------------ compile

	/** Whether the worktree's code or build files differ from the baseline's (packs-only changes do not count). */
	private boolean needsCompile(Path wt) throws IOException, InterruptedException {
		String changed = Proc.git(wt, "diff", "--name-only", "-z", d.baseSha);
		String untracked = Proc.git(wt, "ls-files", "-o", "--exclude-standard", "-z");
		if (changed == null) {
			return true; // cannot tell: be safe
		}
		for (String list : new String[] {changed, untracked == null ? "" : untracked}) {
			for (String p : list.split("\0")) {
				if (relevant(p)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean relevant(String p) {
		if (p.isEmpty() || p.contains("hq-layouts")) {
			return false;
		}
		return p.startsWith("mod/src/") || p.equals("mod/build.gradle") || p.equals("mod/gradle.properties") || p.equals("mod/settings.gradle");
	}

	private static List<String> nice() {
		List<String> p = nicePrefix;
		if (p == null) {
			p = new ArrayList<>(List.of("nice", "-n", "19"));
			try {
				Proc.Result r = Proc.capture(List.of("ionice", "-c3", "true"), Path.of("/"), 10, 1024);
				if (r.exit() == 0) {
					p.addAll(List.of("ionice", "-c3"));
				}
			} catch (IOException | InterruptedException e) {
				// no ionice: nice alone
			}
			nicePrefix = p;
		}
		return p;
	}

	/** Compiles the worktree; returns the class and resource roots, or null after reporting the error. */
	private List<Path> compile(Request r, Path out, Spool.Status st) throws IOException, InterruptedException {
		Path mod = r.worktree().resolve("mod");
		List<String> cmd = new ArrayList<>(nice());
		cmd.addAll(List.of("./gradlew", "--offline", "--console=plain"));
		cmd.addAll(List.of("processResources", "processClientResources", "compileJava", "compileClientJava", "compileHqrenderJava"));
		if (Files.readString(mod.resolve("build.gradle")).contains("uirender")) {
			cmd.add("compileUirenderJava"); // the UI renderer's source set (branch tools/ui-render)
		}
		Path log = out.resolve("compile.log");
		int rc = Proc.toFile(cmd, mod, log, COMPILE_TIMEOUT_SEC);
		if (rc != 0) {
			StringBuilder errs = new StringBuilder();
			int n = 0;
			for (String line : Files.readString(log, StandardCharsets.ISO_8859_1).split("\n")) {
				if ((line.contains(": error:") || line.contains("What went wrong")) && n++ < 25) {
					errs.append(line).append('\n');
				}
			}
			st.log = errs.length() > 0 ? errs.toString().trim() : Proc.tail(log, 25);
			finish(st, "error", rc == -1 ? "compilation timed out after " + COMPILE_TIMEOUT_SEC / 60 + " min" : "compilation failed (exit " + rc + "); the javac errors are in log and compile.log");
			Files.writeString(out.resolve("report.txt"), "compilation failed\n\n" + Proc.tail(log, 200));
			st.report = out.resolve("report.txt").toString();
			d.spool.write(st);
			return null;
		}
		Path b = mod.resolve("build");
		List<Path> roots = new ArrayList<>();
		try (Stream<Path> s = Files.list(b.resolve("classes/java"))) {
			s.filter(Files::isDirectory).filter(p -> !p.getFileName().toString().equals("test") && !p.getFileName().toString().equals("gametest"))
					.sorted().forEach(roots::add);
		}
		for (String res : new String[] {"resources/main", "resources/client"}) {
			if (Files.isDirectory(b.resolve(res))) {
				roots.add(b.resolve(res));
			}
		}
		return roots;
	}

	// ------------------------------------------------------------------ render

	private record Run(List<String> argv, String log, String error) {
	}

	private Run render(Request r, List<Path> roots, Path out) throws Exception {
		List<String> argv = new ArrayList<>(r.args());
		if (r.kind().equals("hq")) {
			if (r.preview()) {
				// previews at low resolution unless the agent chose its own
				if (!argv.contains("--scale")) {
					argv.addAll(List.of("--scale", "0.8"));
				}
				if (!argv.contains("--ss")) {
					argv.addAll(List.of("--ss", "1"));
				}
			}
			argv.addAll(List.of("--out", out.toString()));
		} else {
			argv.addAll(List.of("--out", out.toString(), r.preview() ? "--preview" : "--final"));
		}
		Entry entry = entryOf(r.kind());
		URL[] urls = new URL[roots.size()];
		for (int i = 0; i < urls.length; i++) {
			urls[i] = roots.get(i).toUri().toURL();
		}
		Path logFile = out.resolve("run.log");
		HqFixtures fixtures = HqFixtures.active();
		PrintStream prevOut = System.out;
		PrintStream prevErr = System.err;
		String error = null;
		try (PrintStream cap = new PrintStream(new FileOutputStream(logFile.toFile()), true, StandardCharsets.UTF_8);
				RenderClassLoader cl = new RenderClassLoader(urls, RenderDaemon.class.getClassLoader(), d.pinned::isShared)) {
			System.setOut(cap);
			System.setErr(cap);
			try {
				Method m = cl.loadClass(entry.className()).getMethod(entry.method(), String[].class);
				Throwable[] failure = new Throwable[1];
				String[] a = argv.toArray(new String[0]);
				Thread t = new Thread(() -> {
					Thread.currentThread().setContextClassLoader(cl);
					try {
						m.invoke(null, new Object[] {a});
					} catch (InvocationTargetException e) {
						failure[0] = e.getCause() == null ? e : e.getCause();
					} catch (Throwable e) {
						failure[0] = e;
					}
				}, "render-job-" + r.id());
				t.start();
				t.join(r.preview() ? PREVIEW_TIMEOUT_MS : FINAL_TIMEOUT_MS);
				if (t.isAlive()) {
					t.interrupt();
					t.join(20000);
					error = "timed out after " + (r.preview() ? PREVIEW_TIMEOUT_MS : FINAL_TIMEOUT_MS) / 60000 + " min";
					if (t.isAlive()) {
						// a thread cannot be killed; the launch script restarts the daemon (exit 75)
						cap.println("the render thread did not stop; the daemon restarts");
						cap.flush();
						Run dead = new Run(argv, readLog(logFile), error + " (daemon restarting)");
						writeEarly(r, out, dead);
						d.giveUp("render thread of " + r.id() + " is stuck");
					}
				} else if (failure[0] != null) {
					failure[0].printStackTrace(cap);
					error = describe(failure[0]);
				}
			} catch (ClassNotFoundException e) {
				error = "the worktree has no " + entry.className() + " (kind " + r.kind() + " is not available in this branch)";
			} catch (NoSuchMethodException e) {
				error = entry.className() + " has no public static " + entry.method() + "(String[])";
			} catch (Throwable e) {
				e.printStackTrace(cap);
				error = describe(e);
			}
		} finally {
			System.setOut(prevOut);
			System.setErr(prevErr);
			HqFixtures.use(fixtures == HqFixtures.VANILLA ? null : fixtures);
		}
		return new Run(argv, readLog(logFile), error);
	}

	private static String describe(Throwable e) {
		Throwable root = e;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		String s = e.toString();
		if (root != e) {
			s += " (caused by " + root + ")";
		}
		if (e instanceof LinkageError || root instanceof LinkageError) {
			s += " - a class shared with the daemon changed shape; see docs/tools/render-service.md, \"Kernel\"";
		}
		return s;
	}

	private static String readLog(Path f) {
		try {
			byte[] b = Files.readAllBytes(f);
			int max = 200 * 1024;
			String s = new String(b, 0, Math.min(b.length, max), StandardCharsets.UTF_8);
			return b.length > max ? s + "\n... (log cut at 200 KB; the full text is run.log)" : s;
		} catch (IOException e) {
			return "";
		}
	}

	private void writeEarly(Request r, Path out, Run run) {
		try {
			Files.writeString(out.resolve("report.txt"), "render " + r.id() + " aborted: " + run.error() + "\n\n" + run.log());
		} catch (IOException e) {
			// best effort
		}
	}
}
