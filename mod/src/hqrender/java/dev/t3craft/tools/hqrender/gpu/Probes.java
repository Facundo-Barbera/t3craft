package dev.t3craft.tools.hqrender.gpu;

import java.io.PrintStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

/** What the child process of {@code --gpu-probe} runs: the CUDA driver, and a surfaceless OpenGL context with compute shaders. */
final class Probes {
	private Probes() {
	}

	/** Writes out[tid] = round(sqrt(tid + add) ^ 2) for 32 threads. TARGET is replaced by the sm_XX tried. */
	private static final String PROBE_PTX = """
			.version 7.5
			.target TARGET
			.address_size 64

			.visible .entry probe(
				.param .u64 p_out,
				.param .u32 p_add
			)
			{
				.reg .u32 %r<8>;
				.reg .u64 %rd<8>;
				.reg .f32 %f<6>;

				ld.param.u64 %rd1, [p_out];
				ld.param.u32 %r1, [p_add];
				cvta.to.global.u64 %rd2, %rd1;
				mov.u32 %r2, %tid.x;
				add.u32 %r3, %r2, %r1;
				cvt.rn.f32.u32 %f1, %r3;
				sqrt.rn.f32 %f2, %f1;
				mul.f32 %f3, %f2, %f2;
				cvt.rni.u32.f32 %r4, %f3;
				mul.wide.u32 %rd3, %r2, 4;
				add.u64 %rd4, %rd2, %rd3;
				st.global.u32 [%rd4], %r4;
				ret;
			}
			""";

	static void cuda(PrintStream o) throws Throwable {
		try (Cu cu = new Cu()) {
			o.println("device: " + cu.deviceName + "  compute capability " + cu.ccMajor + "." + cu.ccMinor + "  multiprocessors " + cu.multiprocessors
					+ "  memory " + (cu.totalMemory >> 20) + " MB");
			o.println("CUDA driver API version " + cu.driverVersion + " (" + (cu.driverVersion / 1000) + "." + (cu.driverVersion % 1000 / 10) + ")");
			long[] mi = cu.memInfo();
			o.println("memory free " + (mi[0] >> 20) + " MB of " + (mi[1] >> 20) + " MB");
			for (String target : new String[] {"sm_75", "sm_86", "sm_52"}) {
				try {
					long t0 = System.nanoTime();
					MemorySegment mod = cu.loadPtx(PROBE_PTX.replace("TARGET", target));
					double jit = (System.nanoTime() - t0) / 1e6;
					MemorySegment fn = cu.function(mod, "probe");
					long d = cu.alloc(32 * 4);
					cu.fill32(d, 0, 32);
					cu.launch(fn, 1, 1, 1, 32, 1, 1, d, 100);
					cu.sync();
					int bad = 0;
					try (Arena a = Arena.ofConfined()) {
						MemorySegment h = a.allocate(128, 8);
						cu.download(h, d, 128);
						for (int i = 0; i < 32; i++) {
							if (h.getAtIndex(Ffi.I, i) != 100 + i) {
								bad++;
							}
						}
					}
					cu.free(d);
					o.println("PTX " + target + ": JIT ok in " + String.format(java.util.Locale.ROOT, "%.1f", jit) + " ms, kernel ran, wrong values: " + bad
							+ ", registers " + cu.functionAttribute(fn, 4));
					cu.unload(mod);
				} catch (Throwable e) {
					o.println("PTX " + target + ": FAILED " + e);
				}
			}
			o.print(cu.log);
		}
	}

	private static final String CS_BODY = """
			layout(local_size_x = 64) in;
			layout(std430, binding = 0) buffer Out { uint outv[]; };
			void main() {
				uint i = gl_GlobalInvocationID.x;
				outv[i] = i * 3u + 7u;
			}
			""";

	static void egl(String platform, PrintStream o) throws Throwable {
		Egl e = new Egl();
		try {
			e.init(platform);
		} catch (Throwable t) {
			o.print(e.log);
			o.println("platform " + platform + ": init failed: " + t);
			return;
		}
		o.print(e.log);
		o.println("platform " + platform + ": context current");
		o.println("GL_VENDOR   " + e.glString(0x1F00));
		o.println("GL_RENDERER " + e.glString(0x1F01));
		o.println("GL_VERSION  " + e.glString(0x1F02));
		o.println("GLSL        " + e.glString(0x8B8C));
		MethodHandle getStringi = e.gl("glGetStringi", FunctionDescriptor.of(Ffi.P, Ffi.I, Ffi.I));
		int n = e.glInt(0x821D);
		List<String> interesting = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			String s = Ffi.cstr((MemorySegment) getStringi.invoke(0x1F03, i));
			if (s != null && (s.contains("compute") || s.contains("storage") || s.contains("image_load") || s.contains("fp64") || s.contains("int64")
					|| s.contains("gpu_shader") || s.contains("float64") || s.contains("float16"))) {
				interesting.add(s);
			}
		}
		o.println("extensions: " + n + " in all; compute-related: " + interesting);
		o.println("MAX_COMPUTE_WORK_GROUP_INVOCATIONS " + e.glInt(0x90EB) + "  MAX_SHADER_STORAGE_BLOCK_SIZE " + e.glInt(0x90DE)
				+ "  MAX_COMPUTE_SHARED_MEMORY_SIZE " + e.glInt(0x8262) + "  MAX_SHADER_STORAGE_BUFFER_BINDINGS " + e.glInt(0x90DD));
		boolean ok = computeTest(e, "#version 430\n", o);
		if (!ok) {
			ok = computeTest(e, "#version 420\n#extension GL_ARB_compute_shader : require\n#extension GL_ARB_shader_storage_buffer_object : require\n", o);
		}
		o.println("RESULT compute shaders on " + platform + ": " + (ok ? "WORK" : "do not work") + " (" + e.glString(0x1F01) + ")");
		e.close();
	}

	/** Compiles and runs a tiny compute shader; true when the buffer comes back right. */
	private static boolean computeTest(Egl e, String header, PrintStream o) {
		String tag = header.lines().findFirst().orElse("?");
		try {
			Arena a = e.arena();
			MethodHandle createShader = e.gl("glCreateShader", FunctionDescriptor.of(Ffi.I, Ffi.I));
			MethodHandle shaderSource = e.gl("glShaderSource", FunctionDescriptor.ofVoid(Ffi.I, Ffi.I, Ffi.P, Ffi.P));
			MethodHandle compile = e.gl("glCompileShader", FunctionDescriptor.ofVoid(Ffi.I));
			MethodHandle getShaderiv = e.gl("glGetShaderiv", FunctionDescriptor.ofVoid(Ffi.I, Ffi.I, Ffi.P));
			MethodHandle shaderLog = e.gl("glGetShaderInfoLog", FunctionDescriptor.ofVoid(Ffi.I, Ffi.I, Ffi.P, Ffi.P));
			MethodHandle createProgram = e.gl("glCreateProgram", FunctionDescriptor.of(Ffi.I));
			MethodHandle attach = e.gl("glAttachShader", FunctionDescriptor.ofVoid(Ffi.I, Ffi.I));
			MethodHandle link = e.gl("glLinkProgram", FunctionDescriptor.ofVoid(Ffi.I));
			MethodHandle getProgramiv = e.gl("glGetProgramiv", FunctionDescriptor.ofVoid(Ffi.I, Ffi.I, Ffi.P));
			MethodHandle programLog = e.gl("glGetProgramInfoLog", FunctionDescriptor.ofVoid(Ffi.I, Ffi.I, Ffi.P, Ffi.P));
			MethodHandle useProgram = e.gl("glUseProgram", FunctionDescriptor.ofVoid(Ffi.I));
			MethodHandle genBuffers = e.gl("glGenBuffers", FunctionDescriptor.ofVoid(Ffi.I, Ffi.P));
			MethodHandle bindBuffer = e.gl("glBindBuffer", FunctionDescriptor.ofVoid(Ffi.I, Ffi.I));
			MethodHandle bufferData = e.gl("glBufferData", FunctionDescriptor.ofVoid(Ffi.I, Ffi.L, Ffi.P, Ffi.I));
			MethodHandle bindBase = e.gl("glBindBufferBase", FunctionDescriptor.ofVoid(Ffi.I, Ffi.I, Ffi.I));
			MethodHandle dispatch = e.gl("glDispatchCompute", FunctionDescriptor.ofVoid(Ffi.I, Ffi.I, Ffi.I));
			MethodHandle barrier = e.gl("glMemoryBarrier", FunctionDescriptor.ofVoid(Ffi.I));
			MethodHandle finish = e.gl("glFinish", FunctionDescriptor.ofVoid());
			MethodHandle getSub = e.gl("glGetBufferSubData", FunctionDescriptor.ofVoid(Ffi.I, Ffi.L, Ffi.L, Ffi.P));
			MethodHandle glError = e.gl("glGetError", FunctionDescriptor.of(Ffi.I));

			MemorySegment ip = a.allocate(Ffi.I);
			MemorySegment logBuf = a.allocate(16384);
			int sh = (int) createShader.invoke(0x91B9); // GL_COMPUTE_SHADER
			MemorySegment src = a.allocateFrom(header + CS_BODY);
			MemorySegment ptrs = a.allocate(Ffi.P);
			ptrs.set(Ffi.P, 0, src);
			shaderSource.invoke(sh, 1, ptrs, MemorySegment.NULL);
			compile.invoke(sh);
			getShaderiv.invoke(sh, 0x8B81, ip);
			if (ip.get(Ffi.I, 0) == 0) {
				shaderLog.invoke(sh, 16384, MemorySegment.NULL, logBuf);
				o.println("compute shader (" + tag + ") did not compile: " + logBuf.getString(0).trim());
				return false;
			}
			int prog = (int) createProgram.invoke();
			attach.invoke(prog, sh);
			link.invoke(prog);
			getProgramiv.invoke(prog, 0x8B82, ip);
			if (ip.get(Ffi.I, 0) == 0) {
				programLog.invoke(prog, 16384, MemorySegment.NULL, logBuf);
				o.println("compute program (" + tag + ") did not link: " + logBuf.getString(0).trim());
				return false;
			}
			useProgram.invoke(prog);
			genBuffers.invoke(1, ip);
			int buf = ip.get(Ffi.I, 0);
			bindBuffer.invoke(0x90D2, buf); // GL_SHADER_STORAGE_BUFFER
			bufferData.invoke(0x90D2, 1024L, MemorySegment.NULL, 0x88E8); // GL_DYNAMIC_DRAW
			bindBase.invoke(0x90D2, 0, buf);
			dispatch.invoke(4, 1, 1);
			barrier.invoke(-1);
			finish.invoke();
			MemorySegment host = a.allocate(1024, 8);
			getSub.invoke(0x90D2, 0L, 1024L, host);
			int bad = 0;
			for (int i = 0; i < 256; i++) {
				if (host.getAtIndex(Ffi.I, i) != i * 3 + 7) {
					bad++;
				}
			}
			int err = (int) glError.invoke();
			o.println("compute shader (" + tag + "): compiled, linked, ran; wrong values " + bad + " of 256; glGetError 0x" + Integer.toHexString(err));
			return bad == 0;
		} catch (Throwable t) {
			o.println("compute test (" + tag + ") threw " + t);
			return false;
		}
	}
}
