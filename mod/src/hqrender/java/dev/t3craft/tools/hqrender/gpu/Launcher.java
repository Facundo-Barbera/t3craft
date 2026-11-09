package dev.t3craft.tools.hqrender.gpu;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Runs {@link GpuChild} in its own JVM (same java, only this package on the class path) and captures its output. */
public final class Launcher {
	private Launcher() {
	}

	public record Result(int exit, String output, boolean timedOut) {
	}

	/** The folder or jar the gpu classes were loaded from. */
	static Path classesRoot() throws Exception {
		return Path.of(GpuChild.class.getProtectionDomain().getCodeSource().getLocation().toURI());
	}

	public static Result run(List<String> childArgs, Map<String, String> env, long timeoutSec) throws Exception {
		List<String> cmd = new ArrayList<>();
		cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
		cmd.add("--enable-native-access=ALL-UNNAMED");
		cmd.add("-Xmx1g");
		cmd.add("-Djava.awt.headless=true");
		cmd.add("-cp");
		cmd.add(classesRoot().toString());
		cmd.add(GpuChild.class.getName());
		cmd.addAll(childArgs);
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(true);
		pb.environment().putAll(env);
		Process p = pb.start();
		p.getOutputStream().close();
		byte[][] out = new byte[1][];
		Thread reader = new Thread(() -> {
			try {
				out[0] = p.getInputStream().readAllBytes();
			} catch (IOException e) {
				// the process ended badly; whatever was read is lost
			}
		}, "gpu-child-reader");
		reader.setDaemon(true);
		reader.start();
		boolean done = p.waitFor(timeoutSec, TimeUnit.SECONDS);
		if (!done) {
			p.destroyForcibly();
			p.waitFor(10, TimeUnit.SECONDS);
		}
		reader.join(5000);
		String text = out[0] == null ? "" : new String(out[0], java.nio.charset.StandardCharsets.UTF_8);
		return new Result(done ? p.exitValue() : -1, text, !done);
	}
}
