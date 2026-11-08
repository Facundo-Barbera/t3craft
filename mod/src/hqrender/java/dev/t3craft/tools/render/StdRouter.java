package dev.t3craft.tools.render;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * {@code System.out} / {@code System.err} for a daemon that runs several jobs at once. Installed ONCE at
 * start ({@link #install}); swapping the global streams per job (what a single worker could do) would mix
 * the logs of concurrent jobs. Bytes written from a job's render thread ({@link JobRunner.JobThread}, the
 * threads of the job's own fork-join pool, parallel streams included) go to that job's {@code run.log};
 * from any other thread they go where the streams pointed before (the daemon log).
 *
 * <p>{@link PrintStream} encodes and flushes each print while holding its lock, so the bytes always reach
 * {@link #write} on the thread that printed them.
 */
final class StdRouter extends OutputStream {
	private final OutputStream fallback;
	private final boolean err;

	private StdRouter(OutputStream fallback, boolean err) {
		this.fallback = fallback;
		this.err = err;
	}

	private static boolean installed;

	static synchronized void install() {
		if (installed) {
			return;
		}
		System.setOut(new PrintStream(new StdRouter(System.out, false), true, StandardCharsets.UTF_8));
		System.setErr(new PrintStream(new StdRouter(System.err, true), true, StandardCharsets.UTF_8));
		installed = true;
	}

	private OutputStream target() {
		if (Thread.currentThread() instanceof JobRunner.JobThread t) {
			return t.log;
		}
		return fallback;
	}

	@Override
	public void write(int b) throws IOException {
		target().write(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		target().write(b, off, len);
	}

	@Override
	public void flush() throws IOException {
		target().flush();
	}

	@Override
	public String toString() {
		return "StdRouter(" + (err ? "err" : "out") + ")";
	}
}
