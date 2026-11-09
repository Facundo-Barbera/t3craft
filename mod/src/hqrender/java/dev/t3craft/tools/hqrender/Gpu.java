package dev.t3craft.tools.hqrender;

import dev.t3craft.tools.hqrender.gpu.GpuSession;
import dev.t3craft.tools.hqrender.gpu.Launcher;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

/**
 * tools/render-gpu: whether the ray tracing of {@link Renderer} runs on the GPU (the RTX of the render PC, through the CUDA
 * driver API in a child process, see {@code gpu/GpuChild}) and the one child session of the current job.
 * Like {@link Renderer#look} this is set by {@code HqRender} at the start of every run: one render job per JVM at a time.
 */
final class Gpu {
	private Gpu() {
	}

	enum Mode {
		/** CPU only (the old renderer). */
		OFF,
		/** GPU when it is there, else the CPU (automatic fallback). */
		AUTO,
		/** Both: the GPU image is kept and compared with the CPU one (timings, differences, {@code gpu-compare/}). */
		COMPARE
	}

	static volatile Mode mode = Mode.OFF;
	/** Dev: compile {@code mod/src/hqrender/cuda/trace.cu} with NVRTC instead of using the committed PTX, and write {@code trace.ptx}. */
	static volatile boolean nvrtc;
	static volatile Path outDir;

	private static GpuSession session;
	private static boolean failed;
	private static final AtomicInteger COMPARES = new AtomicInteger();

	/** The live child session, started on first use; null when the GPU is not available (the CPU takes over). */
	static synchronized GpuSession session() {
		if (failed) {
			return null;
		}
		if (session != null) {
			if (session.alive()) {
				return session;
			}
			System.out.println("gpu: the child process is gone; the CPU takes over for the rest of this job");
			failed = true;
			session = null;
			return null;
		}
		StringBuilder log = new StringBuilder();
		try {
			GpuSession s = GpuSession.start(Map.of());
			String cmd;
			if (nvrtc) {
				Path cu = Launcher.classesRoot().resolve("../../../../src/hqrender/cuda/trace.cu").normalize();
				Path ptx = outDir == null ? null : outDir.resolve("trace.ptx");
				if (outDir != null) {
					Files.createDirectories(outDir);
				}
				cmd = "init\tnvrtc\t" + cu + "\t" + (ptx == null ? "" : ptx);
			} else {
				cmd = "init\tembedded\t\t";
			}
			String r = s.request(cmd, 240, log);
			System.out.print(log);
			if (r == null || !r.startsWith("OK")) {
				System.out.println("gpu: not available (" + r + "); using the CPU");
				s.close();
				failed = true;
				return null;
			}
			System.out.println("gpu: " + r.substring(3));
			session = s;
			return s;
		} catch (Exception | LinkageError e) {
			System.out.print(log);
			System.out.println("gpu: not available (" + e + "); using the CPU");
			failed = true;
			return null;
		}
	}

	/** Ends the child (job end). Safe to call twice. */
	static synchronized void closeSession() {
		if (session != null) {
			session.close();
			session = null;
		}
		failed = false;
	}

	/** Timings and differences between the CPU and the GPU image of one render; with an output folder, also the three PNGs. */
	static void compare(BufferedImage cpu, BufferedImage gpu, int[] cpuSub, int[] gpuSub, double cpuMs, double gpuMs, String info) {
		int id = COMPARES.incrementAndGet();
		int w = cpu.getWidth();
		int h = cpu.getHeight();
		int[] a = cpu.getRGB(0, 0, w, h, null, 0, w);
		int[] b = gpu.getRGB(0, 0, w, h, null, 0, w);
		BufferedImage diff = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		long sum = 0;
		int max = 0;
		long over4 = 0;
		long over16 = 0;
		int[] dpx = new int[w * h];
		for (int i = 0; i < a.length; i++) {
			int dr = Math.abs(((a[i] >> 16) & 255) - ((b[i] >> 16) & 255));
			int dg = Math.abs(((a[i] >> 8) & 255) - ((b[i] >> 8) & 255));
			int db = Math.abs((a[i] & 255) - (b[i] & 255));
			int m = Math.max(dr, Math.max(dg, db));
			sum += dr + dg + db;
			max = Math.max(max, m);
			if (m > 4) {
				over4++;
			}
			if (m > 16) {
				over16++;
			}
			int v = Math.min(255, m * 8);
			dpx[i] = (v << 16) | (v << 8) | v;
		}
		diff.setRGB(0, 0, w, h, dpx, 0, w);
		long same = 0;
		for (int i = 0; i < cpuSub.length; i++) {
			if (cpuSub[i] == gpuSub[i]) {
				same++;
			}
		}
		double n = (double) w * h;
		System.out.printf(Locale.ROOT,
				"gpu-compare #%d %dx%d (%d sub-samples): cpu %.2f s, gpu %.2f s (%.1fx) [%s] | image diff: mean %.3f/255 per channel, max %d, >4 on %.4f%% of pixels, >16 on %.4f%% | identical sub-samples %.2f%%%n",
				id, w, h, cpuSub.length, cpuMs / 1000, gpuMs / 1000, cpuMs / Math.max(1, gpuMs), info, sum / (3 * n), max, 100.0 * over4 / n,
				100.0 * over16 / n, 100.0 * same / Math.max(1, cpuSub.length));
		Path dir = outDir == null ? null : outDir.resolve("gpu-compare");
		if (dir != null) {
			try {
				Files.createDirectories(dir);
				String name = String.format(Locale.ROOT, "%02d", id);
				ImageIO.write(cpu, "png", dir.resolve(name + "_cpu.png").toFile());
				ImageIO.write(gpu, "png", dir.resolve(name + "_gpu.png").toFile());
				ImageIO.write(diff, "png", dir.resolve(name + "_diff.png").toFile());
			} catch (IOException e) {
				System.out.println("gpu-compare: could not write the images: " + e);
			}
		}
	}
}
