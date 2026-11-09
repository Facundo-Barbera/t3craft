package dev.t3craft.tools.hqrender.gpu;

import java.io.PrintStream;

/** The GPU ray tracer that runs in the child process (placeholder until the backend is chosen from the probe). */
final class GpuTracer {
	private GpuTracer() {
	}

	static int run(String inputFile, String outputFile, PrintStream out) throws Throwable {
		out.println("gpu tracer: not implemented yet");
		return 2;
	}
}
