package dev.t3craft.tools.hqrender.gpu;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

/**
 * NVRTC (CUDA C to PTX) through the foreign function API. A DEV TOOL, not part of the render path: the render PC has no
 * CUDA toolkit, but some Python environments there ship {@code libnvrtc.so.12} (pip package nvidia-cuda-nvrtc); this
 * finds one and turns {@code trace.cu} into the PTX that is committed as {@link TracePtx}. At run time only the driver
 * is needed.
 */
final class Nvrtc {
	private Nvrtc() {
	}

	/** Library candidates: {@code T3CRAFT_NVRTC}, the pip package inside each Python venv listed in {@code T3CRAFT_NVRTC_VENVS} (path-separated venv roots), then the loader's search path. */
	private static String[] candidates() {
		List<String> c = new ArrayList<>();
		String env = System.getenv("T3CRAFT_NVRTC");
		if (env != null && !env.isBlank()) {
			c.add(env);
		}
		String venvs = System.getenv("T3CRAFT_NVRTC_VENVS");
		if (venvs != null && !venvs.isBlank()) {
			for (String venv : venvs.split(java.io.File.pathSeparator)) {
				if (!venv.isBlank()) {
					c.add(venv + "/lib/python3.12/site-packages/nvidia/cuda_nvrtc/lib/libnvrtc.so.12");
				}
			}
		}
		c.add("libnvrtc.so.12");
		c.add("libnvrtc.so");
		return c.toArray(new String[0]);
	}

	static String compile(String source, String arch, StringBuilder log) throws Throwable {
		try (Arena arena = Arena.ofConfined()) {
			SymbolLookup lib = Ffi.open(arena, log, candidates());
			if (lib == null) {
				throw new IllegalStateException("libnvrtc not found (set T3CRAFT_NVRTC to a libnvrtc.so.12)");
			}
			MethodHandle version = Ffi.fn(lib, "nvrtcVersion", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.P));
			MethodHandle create = Ffi.fn(lib, "nvrtcCreateProgram", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.P, Ffi.P, Ffi.I, Ffi.P, Ffi.P));
			MethodHandle compile = Ffi.fn(lib, "nvrtcCompileProgram", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.I, Ffi.P));
			MethodHandle logSize = Ffi.fn(lib, "nvrtcGetProgramLogSize", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.P));
			MethodHandle logGet = Ffi.fn(lib, "nvrtcGetProgramLog", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.P));
			MethodHandle ptxSize = Ffi.fn(lib, "nvrtcGetPTXSize", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.P));
			MethodHandle ptxGet = Ffi.fn(lib, "nvrtcGetPTX", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.P));
			MethodHandle destroy = Ffi.fn(lib, "nvrtcDestroyProgram", FunctionDescriptor.of(Ffi.I, Ffi.P));

			MemorySegment ma = arena.allocate(Ffi.I);
			MemorySegment mi = arena.allocate(Ffi.I);
			if ((int) version.invoke(ma, mi) == 0) {
				log.append("NVRTC ").append(ma.get(Ffi.I, 0)).append('.').append(mi.get(Ffi.I, 0)).append('\n');
			}
			MemorySegment pp = arena.allocate(Ffi.P);
			int rc = (int) create.invoke(pp, arena.allocateFrom(source), arena.allocateFrom("trace.cu"), 0, MemorySegment.NULL, MemorySegment.NULL);
			if (rc != 0) {
				throw new IllegalStateException("nvrtcCreateProgram failed: " + rc);
			}
			MemorySegment prog = pp.get(Ffi.P, 0);
			String[] opts = {"--gpu-architecture=" + arch};
			MemorySegment optv = arena.allocate(8L * opts.length, 8);
			for (int i = 0; i < opts.length; i++) {
				optv.setAtIndex(Ffi.P, i, arena.allocateFrom(opts[i]));
			}
			long t0 = System.nanoTime();
			int crc = (int) compile.invoke(prog, opts.length, optv);
			MemorySegment sz = arena.allocate(Ffi.L);
			String text = "";
			if ((int) logSize.invoke(prog, sz) == 0 && sz.get(Ffi.L, 0) > 1) {
				MemorySegment lb = arena.allocate(sz.get(Ffi.L, 0) + 1);
				if ((int) logGet.invoke(prog, lb) == 0) {
					text = lb.getString(0);
				}
			}
			if (!text.isBlank()) {
				log.append("nvrtc log:\n").append(text).append('\n');
			}
			if (crc != 0) {
				throw new IllegalStateException("NVRTC compile failed (" + crc + "):\n" + text);
			}
			if ((int) ptxSize.invoke(prog, sz) != 0) {
				throw new IllegalStateException("nvrtcGetPTXSize failed");
			}
			long n = sz.get(Ffi.L, 0);
			MemorySegment pb = arena.allocate(n + 1);
			if ((int) ptxGet.invoke(prog, pb) != 0) {
				throw new IllegalStateException("nvrtcGetPTX failed");
			}
			String ptx = pb.getString(0);
			log.append(String.format(java.util.Locale.ROOT, "NVRTC compiled %s in %.0f ms: %d bytes of PTX%n", arch, (System.nanoTime() - t0) / 1e6, ptx.length()));
			destroy.invoke(pp);
			return ptx;
		}
	}
}
