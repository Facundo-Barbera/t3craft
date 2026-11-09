package dev.t3craft.tools.hqrender.gpu;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/**
 * The CUDA driver API (libcuda) through the foreign function API: no CUDA toolkit needed. The kernel is PTX text that
 * the driver compiles itself ({@code cuModuleLoadDataEx}). Single-threaded: use it from one thread.
 */
public final class Cu implements AutoCloseable {
	public final StringBuilder log = new StringBuilder();
	private final Arena arena = Arena.ofConfined();
	private final MethodHandle cuInit;
	private final MethodHandle cuDriverGetVersion;
	private final MethodHandle cuDeviceGetCount;
	private final MethodHandle cuDeviceGet;
	private final MethodHandle cuDeviceGetName;
	private final MethodHandle cuDeviceTotalMem;
	private final MethodHandle cuDeviceGetAttribute;
	private final MethodHandle cuPrimaryCtxRetain;
	private final MethodHandle cuCtxSetCurrent;
	private final MethodHandle cuCtxSynchronize;
	private final MethodHandle cuMemAlloc;
	private final MethodHandle cuMemFree;
	private final MethodHandle cuMemcpyHtoD;
	private final MethodHandle cuMemcpyDtoH;
	private final MethodHandle cuMemGetInfo;
	private final MethodHandle cuMemsetD32;
	private final MethodHandle cuModuleLoadDataEx;
	private final MethodHandle cuModuleGetFunction;
	private final MethodHandle cuModuleUnload;
	private final MethodHandle cuFuncGetAttribute;
	private final MethodHandle cuLaunchKernel;
	private final MethodHandle cuGetErrorName;
	public int driverVersion;
	public int device;
	public String deviceName = "?";
	public long totalMemory;
	public int ccMajor;
	public int ccMinor;
	public int multiprocessors;
	private MemorySegment context = MemorySegment.NULL;

	/** Opens libcuda, initialises the driver and makes the first device's primary context current. */
	public Cu() throws Throwable {
		SymbolLookup lib = Ffi.open(arena, log, "/usr/lib/wsl/lib/libcuda.so.1", "/usr/lib/wsl/lib/libcuda.so", "libcuda.so.1", "libcuda.so");
		if (lib == null) {
			throw new IllegalStateException("libcuda not found\n" + log);
		}
		cuInit = Ffi.fn(lib, "cuInit", FunctionDescriptor.of(Ffi.I, Ffi.I));
		cuDriverGetVersion = Ffi.fn(lib, "cuDriverGetVersion", FunctionDescriptor.of(Ffi.I, Ffi.P));
		cuDeviceGetCount = Ffi.fn(lib, "cuDeviceGetCount", FunctionDescriptor.of(Ffi.I, Ffi.P));
		cuDeviceGet = Ffi.fn(lib, "cuDeviceGet", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.I));
		cuDeviceGetName = Ffi.fn(lib, "cuDeviceGetName", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.I, Ffi.I));
		cuDeviceTotalMem = Ffi.fn(lib, "cuDeviceTotalMem_v2", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.I));
		cuDeviceGetAttribute = Ffi.fn(lib, "cuDeviceGetAttribute", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.I, Ffi.I));
		cuPrimaryCtxRetain = Ffi.fn(lib, "cuDevicePrimaryCtxRetain", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.I));
		cuCtxSetCurrent = Ffi.fn(lib, "cuCtxSetCurrent", FunctionDescriptor.of(Ffi.I, Ffi.P));
		cuCtxSynchronize = Ffi.fn(lib, "cuCtxSynchronize", FunctionDescriptor.of(Ffi.I));
		cuMemAlloc = Ffi.fn(lib, "cuMemAlloc_v2", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.L));
		cuMemFree = Ffi.fn(lib, "cuMemFree_v2", FunctionDescriptor.of(Ffi.I, Ffi.L));
		cuMemcpyHtoD = Ffi.fn(lib, "cuMemcpyHtoD_v2", FunctionDescriptor.of(Ffi.I, Ffi.L, Ffi.P, Ffi.L));
		cuMemcpyDtoH = Ffi.fn(lib, "cuMemcpyDtoH_v2", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.L, Ffi.L));
		cuMemGetInfo = Ffi.fn(lib, "cuMemGetInfo_v2", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.P));
		cuMemsetD32 = Ffi.fn(lib, "cuMemsetD32_v2", FunctionDescriptor.of(Ffi.I, Ffi.L, Ffi.I, Ffi.L));
		cuModuleLoadDataEx = Ffi.fn(lib, "cuModuleLoadDataEx", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.P, Ffi.I, Ffi.P, Ffi.P));
		cuModuleGetFunction = Ffi.fn(lib, "cuModuleGetFunction", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.P, Ffi.P));
		cuModuleUnload = Ffi.fn(lib, "cuModuleUnload", FunctionDescriptor.of(Ffi.I, Ffi.P));
		cuFuncGetAttribute = Ffi.fn(lib, "cuFuncGetAttribute", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.I, Ffi.P));
		cuLaunchKernel = Ffi.fn(lib, "cuLaunchKernel",
				FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.I, Ffi.I, Ffi.I, Ffi.I, Ffi.I, Ffi.I, Ffi.I, Ffi.P, Ffi.P, Ffi.P));
		cuGetErrorName = Ffi.fn(lib, "cuGetErrorName", FunctionDescriptor.of(Ffi.I, Ffi.I, Ffi.P));

		check((int) cuInit.invoke(0), "cuInit");
		MemorySegment ip = arena.allocate(Ffi.I);
		check((int) cuDriverGetVersion.invoke(ip), "cuDriverGetVersion");
		driverVersion = ip.get(Ffi.I, 0);
		check((int) cuDeviceGetCount.invoke(ip), "cuDeviceGetCount");
		int count = ip.get(Ffi.I, 0);
		if (count < 1) {
			throw new IllegalStateException("no CUDA device");
		}
		check((int) cuDeviceGet.invoke(ip, 0), "cuDeviceGet");
		device = ip.get(Ffi.I, 0);
		MemorySegment name = arena.allocate(256);
		check((int) cuDeviceGetName.invoke(name, 256, device), "cuDeviceGetName");
		deviceName = name.getString(0);
		MemorySegment lp = arena.allocate(Ffi.L);
		check((int) cuDeviceTotalMem.invoke(lp, device), "cuDeviceTotalMem");
		totalMemory = lp.get(Ffi.L, 0);
		check((int) cuDeviceGetAttribute.invoke(ip, 75, device), "attr cc major");
		ccMajor = ip.get(Ffi.I, 0);
		check((int) cuDeviceGetAttribute.invoke(ip, 76, device), "attr cc minor");
		ccMinor = ip.get(Ffi.I, 0);
		check((int) cuDeviceGetAttribute.invoke(ip, 16, device), "attr multiprocessors");
		multiprocessors = ip.get(Ffi.I, 0);
		MemorySegment cp = arena.allocate(Ffi.P);
		check((int) cuPrimaryCtxRetain.invoke(cp, device), "cuDevicePrimaryCtxRetain");
		context = cp.get(Ffi.P, 0);
		check((int) cuCtxSetCurrent.invoke(context), "cuCtxSetCurrent");
	}

	public String errorName(int rc) {
		try {
			MemorySegment pp = arena.allocate(Ffi.P);
			int r = (int) cuGetErrorName.invoke(rc, pp);
			if (r == 0) {
				String s = Ffi.cstr(pp.get(Ffi.P, 0));
				if (s != null) {
					return s + " (" + rc + ")";
				}
			}
		} catch (Throwable e) {
			// fall through
		}
		return "error " + rc;
	}

	private void check(int rc, String what) {
		if (rc != 0) {
			throw new IllegalStateException(what + " failed: " + errorName(rc));
		}
	}

	/** Free and total device memory, bytes. */
	public long[] memInfo() throws Throwable {
		MemorySegment a = arena.allocate(Ffi.L);
		MemorySegment b = arena.allocate(Ffi.L);
		check((int) cuMemGetInfo.invoke(a, b), "cuMemGetInfo");
		return new long[] {a.get(Ffi.L, 0), b.get(Ffi.L, 0)};
	}

	/** Compiles PTX text with the driver's JIT; returns the module handle. The JIT logs are kept in {@link #log}. */
	public MemorySegment loadPtx(String ptx) throws Throwable {
		try (Arena a = Arena.ofConfined()) {
			int logSize = 64 * 1024;
			MemorySegment errBuf = a.allocate(logSize);
			MemorySegment infoBuf = a.allocate(logSize);
			MemorySegment image = a.allocateFrom(ptx);
			// CU_JIT_INFO_LOG_BUFFER = 3, ..._SIZE_BYTES = 4, CU_JIT_ERROR_LOG_BUFFER = 5, ..._SIZE_BYTES = 6
			MemorySegment opts = a.allocate(4L * 4, 4);
			opts.setAtIndex(Ffi.I, 0, 3);
			opts.setAtIndex(Ffi.I, 1, 4);
			opts.setAtIndex(Ffi.I, 2, 5);
			opts.setAtIndex(Ffi.I, 3, 6);
			MemorySegment vals = a.allocate(8L * 4, 8);
			vals.setAtIndex(Ffi.P, 0, infoBuf);
			vals.setAtIndex(Ffi.P, 1, MemorySegment.ofAddress(logSize));
			vals.setAtIndex(Ffi.P, 2, errBuf);
			vals.setAtIndex(Ffi.P, 3, MemorySegment.ofAddress(logSize));
			MemorySegment mod = a.allocate(Ffi.P);
			int rc = (int) cuModuleLoadDataEx.invoke(mod, image, 4, opts, vals);
			String err = errBuf.getString(0);
			String info = infoBuf.getString(0);
			if (!info.isBlank()) {
				log.append("jit info: ").append(info.trim()).append('\n');
			}
			if (!err.isBlank()) {
				log.append("jit error: ").append(err.trim()).append('\n');
			}
			if (rc != 0) {
				throw new IllegalStateException("cuModuleLoadDataEx failed: " + errorName(rc) + "\n" + err);
			}
			return mod.get(Ffi.P, 0);
		}
	}

	public void unload(MemorySegment module) throws Throwable {
		cuModuleUnload.invoke(module);
	}

	public MemorySegment function(MemorySegment module, String name) throws Throwable {
		MemorySegment fp = arena.allocate(Ffi.P);
		check((int) cuModuleGetFunction.invoke(fp, module, arena.allocateFrom(name)), "cuModuleGetFunction " + name);
		return fp.get(Ffi.P, 0);
	}

	/** A function attribute (0 max threads per block, 3 local bytes, 4 registers). */
	public int functionAttribute(MemorySegment fn, int attribute) throws Throwable {
		MemorySegment ip = arena.allocate(Ffi.I);
		check((int) cuFuncGetAttribute.invoke(ip, attribute, fn), "cuFuncGetAttribute");
		return ip.get(Ffi.I, 0);
	}

	public long alloc(long bytes) throws Throwable {
		MemorySegment lp = arena.allocate(Ffi.L);
		check((int) cuMemAlloc.invoke(lp, Math.max(16, bytes)), "cuMemAlloc " + bytes);
		return lp.get(Ffi.L, 0);
	}

	public void free(long dptr) throws Throwable {
		if (dptr != 0) {
			cuMemFree.invoke(dptr);
		}
	}

	/** Copies {@code bytes} bytes from a native (or mapped) segment to the device. */
	public void upload(long dptr, MemorySegment host, long bytes) throws Throwable {
		check((int) cuMemcpyHtoD.invoke(dptr, host, bytes), "cuMemcpyHtoD");
	}

	public void download(MemorySegment host, long dptr, long bytes) throws Throwable {
		check((int) cuMemcpyDtoH.invoke(host, dptr, bytes), "cuMemcpyDtoH");
	}

	public void fill32(long dptr, int value, long count) throws Throwable {
		check((int) cuMemsetD32.invoke(dptr, value, count), "cuMemsetD32");
	}

	/** Parameters are Long (64-bit: device pointers), Integer or Float, in the kernel's order. */
	public void launch(MemorySegment fn, int gx, int gy, int gz, int bx, int by, int bz, Object... params) throws Throwable {
		try (Arena a = Arena.ofConfined()) {
			MemorySegment arr = a.allocate(8L * Math.max(1, params.length), 8);
			for (int i = 0; i < params.length; i++) {
				Object v = params[i];
				MemorySegment s;
				if (v instanceof Long l) {
					s = a.allocate(8, 8);
					s.set(Ffi.L, 0, l);
				} else if (v instanceof Integer n) {
					s = a.allocate(4, 4);
					s.set(Ffi.I, 0, n);
				} else if (v instanceof Float f) {
					s = a.allocate(4, 4);
					s.set(Ffi.F, 0, f);
				} else {
					throw new IllegalArgumentException("kernel parameter type " + v);
				}
				arr.setAtIndex(Ffi.P, i, s);
			}
			check((int) cuLaunchKernel.invoke(fn, gx, gy, gz, bx, by, bz, 0, MemorySegment.NULL, arr, MemorySegment.NULL), "cuLaunchKernel");
		}
	}

	public void sync() throws Throwable {
		check((int) cuCtxSynchronize.invoke(), "kernel run (cuCtxSynchronize)");
	}

	@Override
	public void close() {
		arena.close();
	}
}
