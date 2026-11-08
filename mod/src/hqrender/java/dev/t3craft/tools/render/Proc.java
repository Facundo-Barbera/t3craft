package dev.t3craft.tools.render;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Small helpers to run git and gradle: bounded time, output captured to memory or to a file. */
final class Proc {
	private Proc() {
	}

	record Result(int exit, byte[] out) {
		String text() {
			return new String(out, java.nio.charset.StandardCharsets.UTF_8);
		}
	}

	/** Runs {@code cmd} in {@code dir}, stdout+stderr in memory (capped at {@code maxBytes}); exit -1 on timeout. */
	static Result capture(List<String> cmd, Path dir, long timeoutSec, int maxBytes) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true);
		Process p = pb.start();
		p.getOutputStream().close();
		java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
		Thread pump = pump(p.getInputStream(), buf, maxBytes);
		boolean done = p.waitFor(timeoutSec, TimeUnit.SECONDS);
		if (!done) {
			p.destroyForcibly();
			p.waitFor(10, TimeUnit.SECONDS);
		}
		pump.join(5000);
		return new Result(done ? p.exitValue() : -1, buf.toByteArray());
	}

	/** Runs {@code cmd} in {@code dir} writing stdout+stderr to {@code log}; returns the exit code, -1 on timeout. */
	static int toFile(List<String> cmd, Path dir, Path log, long timeoutSec) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true);
		pb.redirectOutput(ProcessBuilder.Redirect.to(log.toFile()));
		pb.redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")));
		Process p = pb.start();
		try {
			if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
				p.destroy();
				if (!p.waitFor(15, TimeUnit.SECONDS)) {
					p.destroyForcibly();
				}
				return -1;
			}
			return p.exitValue();
		} catch (InterruptedException e) {
			p.destroyForcibly();
			throw e;
		}
	}

	private static Thread pump(InputStream in, OutputStream out, int max) {
		Thread t = new Thread(() -> {
			byte[] b = new byte[8192];
			int total = 0;
			try (in) {
				int n;
				while ((n = in.read(b)) >= 0) {
					if (total < max) {
						int w = Math.min(n, max - total);
						out.write(b, 0, w);
						total += w;
					}
				}
			} catch (IOException e) {
				// the process ended
			}
		}, "render-proc-pump");
		t.setDaemon(true);
		t.start();
		return t;
	}

	/** git in {@code dir}; the output, or null when git failed. */
	static String git(Path dir, String... args) throws IOException, InterruptedException {
		java.util.ArrayList<String> cmd = new java.util.ArrayList<>();
		cmd.add("git");
		cmd.addAll(List.of(args));
		Result r = capture(cmd, dir, 60, 64 << 20);
		return r.exit() == 0 ? r.text() : null;
	}

	/** The last {@code lines} lines of a text file (empty if unreadable). */
	static String tail(Path file, int lines) {
		try {
			String[] all = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8).split("\n");
			return String.join("\n", java.util.Arrays.asList(all).subList(Math.max(0, all.length - lines), all.length));
		} catch (IOException | RuntimeException e) {
			return "";
		}
	}
}
