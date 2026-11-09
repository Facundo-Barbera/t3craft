package dev.t3craft.tools.hqrender.gpu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A long-lived {@link GpuChild} in {@code serve} mode: the parent writes one tab-separated command per line to its
 * stdin and waits for the reply line that starts with {@code @@}. Every other line the child prints (warnings, JIT
 * logs) is collected and passed to {@code sink}. If the child dies or hangs, {@link #request} returns null and the
 * session is dead: the caller falls back to the CPU.
 */
public final class GpuSession implements AutoCloseable {
	private final Process proc;
	private final Writer toChild;
	private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
	private volatile boolean dead;

	private GpuSession(Process proc) {
		this.proc = proc;
		this.toChild = new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.UTF_8);
		Thread reader = new Thread(() -> {
			try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
				String l;
				while ((l = r.readLine()) != null) {
					lines.add(l);
				}
			} catch (IOException e) {
				// the child went away
			}
			lines.add("@@EOF");
		}, "gpu-session-reader");
		reader.setDaemon(true);
		reader.start();
	}

	public static GpuSession start(Map<String, String> env) throws Exception {
		Process p = Launcher.command(List.of("serve"), env).start();
		return new GpuSession(p);
	}

	public boolean alive() {
		return !dead && proc.isAlive();
	}

	/**
	 * Sends a command and returns the reply text after {@code @@} ("OK ..." or "FAIL ..."), or null when the child
	 * died or took longer than {@code timeoutSec}. Lines printed meanwhile go to {@code sink}.
	 */
	public String request(String command, long timeoutSec, StringBuilder sink) {
		if (!alive()) {
			return null;
		}
		try {
			toChild.write(command);
			toChild.write('\n');
			toChild.flush();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSec);
			while (true) {
				long left = deadline - System.nanoTime();
				String l = left <= 0 ? null : lines.poll(left, TimeUnit.NANOSECONDS);
				if (l == null) {
					sink.append("gpu child: no reply in ").append(timeoutSec).append(" s, killing it\n");
					kill();
					return null;
				}
				if (l.equals("@@EOF")) {
					sink.append("gpu child: ended without a reply\n");
					dead = true;
					return null;
				}
				if (l.startsWith("@@")) {
					return l.substring(2);
				}
				sink.append(l).append('\n');
			}
		} catch (IOException e) {
			sink.append("gpu child: ").append(e).append('\n');
			kill();
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			kill();
			return null;
		}
	}

	private void kill() {
		dead = true;
		proc.destroyForcibly();
	}

	@Override
	public void close() {
		if (!dead && proc.isAlive()) {
			try {
				toChild.write("quit\n");
				toChild.flush();
				proc.waitFor(5, TimeUnit.SECONDS);
			} catch (IOException | InterruptedException e) {
				// killed below
			}
		}
		dead = true;
		proc.destroyForcibly();
	}
}
