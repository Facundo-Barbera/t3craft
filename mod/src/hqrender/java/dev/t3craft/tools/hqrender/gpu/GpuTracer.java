package dev.t3craft.tools.hqrender.gpu;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;

/**
 * The GPU ray tracer, in the child process: {@code serve} reads one command per line from stdin and answers with a
 * line {@code @@OK ...} or {@code @@FAIL ...}.
 *
 * <pre>
 *   init  TAB embedded|nvrtc  TAB path-to-trace.cu  TAB path-to-write-the-ptx     load the kernels
 *   render TAB input-file TAB output-file                                          one image (file layout: see GpuTrace and renderOne)
 *   quit
 * </pre>
 */
final class GpuTracer {
	private GpuTracer() {
	}

	/** Target time of one kernel launch, ms: short enough that the desktop on the same GPU keeps drawing. */
	private static final double TARGET_MS = 100;

	/** The image needs more device memory than is free: not an error of the GPU, the next (smaller) image may still go. */
	private static final class NoMemory extends IllegalStateException {
		NoMemory(String m) {
			super(m);
		}
	}

	/** The kernels of trace.cu. */
	private record Kernels(MemorySegment trace, MemorySegment filter, MemorySegment glow, MemorySegment blurH, MemorySegment fin) {
	}

	static int serve(PrintStream out) throws Throwable {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		Cu cu = null;
		Kernels ks = null;
		String line;
		while ((line = in.readLine()) != null) {
			String[] w = line.split("\t", -1);
			try {
				switch (w[0]) {
					case "init" -> {
						cu = new Cu();
						StringBuilder log = new StringBuilder();
						String ptx;
						if (w[1].equals("nvrtc")) {
							ptx = Nvrtc.compile(Files.readString(Path.of(w[2])), "compute_75", log);
							if (w.length > 3 && !w[3].isEmpty()) {
								Files.writeString(Path.of(w[3]), ptx);
							}
						} else {
							ptx = TracePtx.text();
							if (ptx == null || ptx.isBlank()) {
								throw new IllegalStateException("no PTX embedded in this build (gpu/TracePtx.java)");
							}
						}
						long t0 = System.nanoTime();
						MemorySegment module = cu.loadPtx(ptx);
						ks = new Kernels(cu.function(module, "trace"), cu.function(module, "filterK"), cu.function(module, "glowK"),
								cu.function(module, "blurH"), cu.function(module, "finalK"));
						out.print(log);
						out.print(cu.log);
						reply(out, String.format(Locale.ROOT, "OK %s cc %d.%d, %d SMs, %d MB, driver %d; PTX loaded in %.0f ms, trace kernel %d registers, %d bytes local",
								cu.deviceName, cu.ccMajor, cu.ccMinor, cu.multiprocessors, cu.totalMemory >> 20, cu.driverVersion, (System.nanoTime() - t0) / 1e6,
								cu.functionAttribute(ks.trace(), 4), cu.functionAttribute(ks.trace(), 3)));
					}
					case "render" -> {
						if (cu == null || ks == null) {
							throw new IllegalStateException("render before init");
						}
						reply(out, "OK " + renderOne(cu, ks, Path.of(w[1]), Path.of(w[2])));
					}
					case "quit" -> {
						reply(out, "OK bye");
						return 0;
					}
					default -> reply(out, "FAIL unknown command " + w[0]);
				}
			} catch (Throwable t) {
				t.printStackTrace(out);
				String m = String.valueOf(t.getMessage()).replace('\n', ' ').replace('\t', ' ');
				reply(out, "FAIL " + t.getClass().getSimpleName() + ": " + (m.length() > 400 ? m.substring(0, 400) : m));
				if (t instanceof NoMemory) {
					continue; // this image does not fit; the context is fine and the next one may
				}
				return 1; // after a CUDA error the context cannot be trusted: the parent uses the CPU
			}
		}
		return 0;
	}

	private static void reply(PrintStream out, String text) {
		out.println("@@" + text);
		out.flush();
	}

	/** Sum of the byte lengths of header slots [from, to) of the input file. */
	private static long ch0(MemorySegment f, int from, int to) {
		long s = 0;
		for (int i = from; i < to; i++) {
			s += f.get(Ffi.I, 4L * i) & 0xFFFFFFFFL;
		}
		return s;
	}

	/** Launches a kernel whose last two parameters are the band of pixel rows, in bands of about 2 million pixels. */
	private static void bands(Cu cu, MemorySegment fn, int pw, int ph, Object... params) throws Throwable {
		int rows = Math.max(16, 2_000_000 / Math.max(1, pw));
		for (int y = 0; y < ph; y += rows) {
			int end = Math.min(ph, y + rows);
			Object[] p = Arrays.copyOf(params, params.length + 2);
			p[params.length] = y;
			p[params.length + 1] = end;
			cu.launch(fn, (pw + 15) / 16, (end - y + 15) / 16, 1, 16, 16, 1, p);
			cu.sync();
		}
	}

	/**
	 * Input file: 128-byte header (ints: [0] magic, [1] W, [2] H in sub-samples, [3] glow, [4] GPU resolve, [5] ss, [6] kMin, [7] kMax,
	 * [8 + i] byte length of section i, [24] bloom radius, [25] outputs: 1 sub-samples, 2 finished pixels), then 9 sections, each padded
	 * to 16 bytes: params, cells, model starts, model quads, model masks, quads, textures, sRGB table, filter taps + gaussian kernel.
	 * Output file: [sub-samples, glow bytes] if bit 1, then [finished 0xRRGGBB pixels] if bit 2.
	 */
	private static String renderOne(Cu cu, Kernels ks, Path in, Path outFile) throws Throwable {
		long tStart = System.nanoTime();
		long[] dev = new long[9];
		long dBuf = 0;
		long dGlow = 0;
		long dLin = 0;
		long dE = 0;
		long dTmp = 0;
		long dOut = 0;
		try (Arena a = Arena.ofConfined()) {
			MemorySegment f;
			try (FileChannel ch = FileChannel.open(in, StandardOpenOption.READ)) {
				f = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size(), a);
			}
			if (f.get(Ffi.I, 0) != 0x47505531) {
				throw new IllegalStateException("bad input file");
			}
			int w = f.get(Ffi.I, 4);
			int h = f.get(Ffi.I, 8);
			boolean glow = f.get(Ffi.I, 12) != 0;
			boolean resolve = f.get(Ffi.I, 16) != 0;
			int ss = f.get(Ffi.I, 20);
			int kMin = f.get(Ffi.I, 24);
			int kMax = f.get(Ffi.I, 28);
			int rad = f.get(Ffi.I, 4 * 24);
			int outputs = f.get(Ffi.I, 4 * 25);
			int pw = w / ss;
			int ph = h / ss;
			// device memory this image needs: the sections, the sub-sample buffers, and with the resolve three float images plus the pixels
			long need = ch0(f, 8, 17) + 4L * w * h + (glow ? (long) w * h : 0) + (resolve ? 40L * pw * ph : 0);
			long free = cu.memInfo()[0];
			if (need > free - (256L << 20)) {
				throw new NoMemory("not enough GPU memory: this image needs " + (need >> 20) + " MB, " + (free >> 20) + " MB are free");
			}
			long off = 128;
			try {
				for (int i = 0; i < 9; i++) {
					long len = f.get(Ffi.I, 4L * (8 + i)) & 0xFFFFFFFFL;
					dev[i] = cu.alloc(len);
					if (len > 0) {
						cu.upload(dev[i], f.asSlice(off, len), len);
					}
					off += (len + 15) & ~15L;
				}
				long bufBytes = 4L * w * h;
				long glowBytes = glow ? (long) w * h : 0;
				dBuf = cu.alloc(bufBytes);
				dGlow = cu.alloc((glowBytes + 3) & ~3L);
				if (glow) {
					cu.fill32(dGlow, 0, (glowBytes + 3) / 4);
				}
				double upMs = (System.nanoTime() - tStart) / 1e6;

				int rows = Math.max(8, 100_000 / Math.max(1, w));
				int y = 0;
				int launches = 0;
				long kernelNs = 0;
				double worst = 0;
				while (y < h) {
					int n = Math.min(rows, h - y);
					long t0 = System.nanoTime();
					cu.launch(ks.trace(), (w + 15) / 16, (n + 7) / 8, 1, 16, 8, 1, dev[0], dev[1], dev[2], dev[3], dev[4], dev[5], dev[6], dev[7], dBuf, dGlow, y,
							y + n);
					cu.sync();
					long dt = System.nanoTime() - t0;
					kernelNs += dt;
					launches++;
					double ms = Math.max(0.2, dt / 1e6);
					worst = Math.max(worst, ms);
					y += n;
					double grow = Math.min(2.0, TARGET_MS / ms);
					rows = (int) Math.max(8, Math.min(h, Math.round(rows * grow)));
					rows = (rows + 7) / 8 * 8;
				}

				double resolveMs = 0;
				if (resolve) {
					long r0 = System.nanoTime();
					long nTaps = kMax - kMin + 1;
					long wkPtr = dev[8];
					long gkPtr = dev[8] + 4 * nTaps;
					boolean bloom = glow && rad > 0;
					dLin = cu.alloc(12L * pw * ph);
					dOut = cu.alloc(4L * pw * ph);
					bands(cu, ks.filter(), pw, ph, dBuf, wkPtr, w, h, pw, ss, kMin, kMax, dLin);
					if (bloom) {
						dE = cu.alloc(12L * pw * ph);
						dTmp = cu.alloc(12L * pw * ph);
						bands(cu, ks.glow(), pw, ph, dBuf, dGlow, w, pw, ss, dE);
						bands(cu, ks.blurH(), pw, ph, dE, gkPtr, pw, rad, dTmp);
					} else {
						dTmp = cu.alloc(16);
					}
					bands(cu, ks.fin(), pw, ph, dLin, dTmp, gkPtr, pw, ph, rad, bloom ? 1 : 0, dOut);
					resolveMs = (System.nanoTime() - r0) / 1e6;
				}

				long t1 = System.nanoTime();
				boolean wantSub = (outputs & 1) != 0;
				boolean wantRgb = (outputs & 2) != 0 && resolve;
				long subBytes = wantSub ? bufBytes + glowBytes : 0;
				long rgbBytes = wantRgb ? 4L * pw * ph : 0;
				try (FileChannel oc = FileChannel.open(outFile, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING)) {
					MemorySegment o = oc.map(FileChannel.MapMode.READ_WRITE, 0, subBytes + rgbBytes, a);
					if (wantSub) {
						cu.download(o.asSlice(0, bufBytes), dBuf, bufBytes);
						if (glow) {
							cu.download(o.asSlice(bufBytes, glowBytes), dGlow, glowBytes);
						}
					}
					if (wantRgb) {
						cu.download(o.asSlice(subBytes, rgbBytes), dOut, rgbBytes);
					}
				}
				double downMs = (System.nanoTime() - t1) / 1e6;
				return String.format(Locale.ROOT, "%dx%d sub-samples: upload %.0f ms, trace %.0f ms in %d launches (longest %.0f ms), resolve %.0f ms%s, download %.0f ms", w,
						h, upMs, kernelNs / 1e6, launches, worst, resolveMs, resolve ? " (on the GPU)" : " (not done)", downMs);
			} finally {
				for (long d : dev) {
					cu.free(d);
				}
				cu.free(dBuf);
				cu.free(dGlow);
				cu.free(dLin);
				cu.free(dE);
				cu.free(dTmp);
				cu.free(dOut);
			}
		}
	}
}
