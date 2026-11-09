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
import java.util.Locale;

/**
 * The GPU ray tracer, in the child process: {@code serve} reads one command per line from stdin and answers with a
 * line {@code @@OK ...} or {@code @@FAIL ...}.
 *
 * <pre>
 *   init  TAB embedded|nvrtc  TAB path-to-trace.cu  TAB path-to-write-the-ptx     load the kernel
 *   render TAB input-file TAB output-file                                          one image (see GpuTrace for the file layout)
 *   quit
 * </pre>
 */
final class GpuTracer {
	private GpuTracer() {
	}

	/** Target time of one kernel launch, ms: short enough that the desktop on the same GPU keeps drawing. */
	private static final double TARGET_MS = 100;

	static int serve(PrintStream out) throws Throwable {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		Cu cu = null;
		MemorySegment fn = null;
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
						fn = cu.function(module, "trace");
						out.print(log);
						out.print(cu.log);
						reply(out, String.format(Locale.ROOT, "OK %s cc %d.%d, %d SMs, %d MB, driver %d; PTX loaded in %.0f ms, %d registers, %d bytes local",
								cu.deviceName, cu.ccMajor, cu.ccMinor, cu.multiprocessors, cu.totalMemory >> 20, cu.driverVersion, (System.nanoTime() - t0) / 1e6,
								cu.functionAttribute(fn, 4), cu.functionAttribute(fn, 3)));
					}
					case "render" -> {
						if (cu == null || fn == null) {
							throw new IllegalStateException("render before init");
						}
						reply(out, "OK " + renderOne(cu, fn, Path.of(w[1]), Path.of(w[2])));
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
				return 1; // after a CUDA error the context cannot be trusted: the parent starts over or uses the CPU
			}
		}
		return 0;
	}

	private static void reply(PrintStream out, String text) {
		out.println("@@" + text);
		out.flush();
	}

	/** Layout of the input file: 128-byte header (ints: magic, W, H, glow, ..., [8 + i] = byte length of section i), then 8 sections, each padded to 16 bytes. */
	private static String renderOne(Cu cu, MemorySegment fn, Path in, Path outFile) throws Throwable {
		long tStart = System.nanoTime();
		long[] dev = new long[8];
		long dBuf = 0;
		long dGlow = 0;
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
			long off = 128;
			try {
				for (int i = 0; i < 8; i++) {
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
					cu.launch(fn, (w + 15) / 16, (n + 7) / 8, 1, 16, 8, 1, dev[0], dev[1], dev[2], dev[3], dev[4], dev[5], dev[6], dev[7], dBuf, dGlow, y, y + n);
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

				long t1 = System.nanoTime();
				try (FileChannel oc = FileChannel.open(outFile, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING)) {
					MemorySegment o = oc.map(FileChannel.MapMode.READ_WRITE, 0, bufBytes + glowBytes, a);
					cu.download(o.asSlice(0, bufBytes), dBuf, bufBytes);
					if (glow) {
						cu.download(o.asSlice(bufBytes, glowBytes), dGlow, glowBytes);
					}
				}
				double downMs = (System.nanoTime() - t1) / 1e6;
				return String.format(Locale.ROOT, "%dx%d upload %.0f ms, kernel %.0f ms in %d launches (longest %.0f ms), download %.0f ms", w, h, upMs,
						kernelNs / 1e6, launches, worst, downMs);
			} finally {
				for (long d : dev) {
					cu.free(d);
				}
				cu.free(dBuf);
				cu.free(dGlow);
			}
		}
	}
}
