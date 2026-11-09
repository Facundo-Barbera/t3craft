package dev.t3craft.tools.hqrender.gpu;

/**
 * Entry point of the child JVM that talks to the GPU. The render daemon never calls native code itself: a driver
 * crash or a hang then costs one child process, not the shared daemon. Uses only the JDK (no Minecraft classes), so
 * it starts with just this package on the class path.
 *
 * <pre>
 *   GpuChild probe-cuda
 *   GpuChild probe-egl surfaceless|device:N|default
 *   GpuChild render INPUT_FILE OUTPUT_FILE        (see GpuTracer)
 * </pre>
 */
public final class GpuChild {
	private GpuChild() {
	}

	public static void main(String[] args) {
		int rc = 0;
		try {
			switch (args[0]) {
				case "probe-cuda" -> Probes.cuda(System.out);
				case "probe-egl" -> Probes.egl(args[1], System.out);
				case "render" -> rc = GpuTracer.run(args[1], args[2], System.out);
				default -> {
					System.out.println("unknown mode " + args[0]);
					rc = 2;
				}
			}
		} catch (Throwable t) {
			t.printStackTrace(System.out);
			rc = 1;
		}
		System.out.flush();
		System.exit(rc);
	}
}
