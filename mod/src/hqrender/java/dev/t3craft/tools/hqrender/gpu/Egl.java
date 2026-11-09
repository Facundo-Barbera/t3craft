package dev.t3craft.tools.hqrender.gpu;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.Map;

/**
 * A surfaceless EGL display with a desktop OpenGL context, through the foreign function API: no window, no
 * GLFW, no LWJGL (nothing can appear on the desktop). GL functions are fetched with eglGetProcAddress.
 * Single-threaded.
 */
public final class Egl implements AutoCloseable {
	public final StringBuilder log = new StringBuilder();
	final Arena arena = Arena.ofConfined();
	private final SymbolLookup lib;
	private final MethodHandle getProcAddress;
	private final MethodHandle getError;
	private final MethodHandle queryString;
	private final Map<String, MethodHandle> glFns = new HashMap<>();
	private MemorySegment display = MemorySegment.NULL;
	public String platform = "?";
	public boolean current;

	public Egl() throws Throwable {
		lib = Ffi.open(arena, log, "libEGL.so.1", "/usr/lib/x86_64-linux-gnu/libEGL.so.1", "libEGL.so");
		if (lib == null) {
			throw new IllegalStateException("libEGL not found\n" + log);
		}
		getProcAddress = Ffi.fn(lib, "eglGetProcAddress", FunctionDescriptor.of(Ffi.P, Ffi.P));
		getError = Ffi.fn(lib, "eglGetError", FunctionDescriptor.of(Ffi.I));
		queryString = Ffi.fn(lib, "eglQueryString", FunctionDescriptor.of(Ffi.P, Ffi.P, Ffi.I));
	}

	private String err() throws Throwable {
		return "0x" + Integer.toHexString((int) getError.invoke());
	}

	private MethodHandle proc(String name, FunctionDescriptor fd) throws Throwable {
		MemorySegment p = (MemorySegment) getProcAddress.invoke(arena.allocateFrom(name));
		if (p.address() == 0) {
			throw new IllegalStateException("eglGetProcAddress(" + name + ") returned null");
		}
		return Ffi.at(p, fd);
	}

	/**
	 * Opens a display and makes a context current. {@code platform}: {@code surfaceless} (EGL_MESA_platform_surfaceless),
	 * {@code device:N} (EGL_EXT_platform_device, the N-th device) or {@code default} (eglGetDisplay; may connect to the
	 * desktop's display server but creates no window).
	 */
	public void init(String platform) throws Throwable {
		this.platform = platform;
		MethodHandle getPlatformDisplay = Ffi.fn(lib, "eglGetPlatformDisplay", FunctionDescriptor.of(Ffi.P, Ffi.I, Ffi.P, Ffi.P));
		if (platform.equals("surfaceless")) {
			display = (MemorySegment) getPlatformDisplay.invoke(0x31DD, MemorySegment.NULL, MemorySegment.NULL);
		} else if (platform.startsWith("device:")) {
			int idx = Integer.parseInt(platform.substring(7));
			MethodHandle q = proc("eglQueryDevicesEXT", FunctionDescriptor.of(Ffi.I, Ffi.I, Ffi.P, Ffi.P));
			MemorySegment devs = arena.allocate(8L * 16, 8);
			MemorySegment num = arena.allocate(Ffi.I);
			int ok = (int) q.invoke(16, devs, num);
			int n = num.get(Ffi.I, 0);
			log.append("eglQueryDevicesEXT: ok=").append(ok).append(" devices=").append(n).append('\n');
			if (ok == 0 || idx >= n) {
				throw new IllegalStateException("no EGL device " + idx);
			}
			display = (MemorySegment) getPlatformDisplay.invoke(0x313F, devs.getAtIndex(Ffi.P, idx), MemorySegment.NULL);
		} else {
			MethodHandle gd = Ffi.fn(lib, "eglGetDisplay", FunctionDescriptor.of(Ffi.P, Ffi.P));
			display = (MemorySegment) gd.invoke(MemorySegment.NULL);
		}
		if (display.address() == 0) {
			throw new IllegalStateException("no EGL display for platform " + platform + " (error " + err() + ")");
		}
		MethodHandle initialize = Ffi.fn(lib, "eglInitialize", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.P, Ffi.P));
		MemorySegment mj = arena.allocate(Ffi.I);
		MemorySegment mn = arena.allocate(Ffi.I);
		if ((int) initialize.invoke(display, mj, mn) == 0) {
			throw new IllegalStateException("eglInitialize failed (error " + err() + ")");
		}
		log.append("EGL ").append(mj.get(Ffi.I, 0)).append('.').append(mn.get(Ffi.I, 0));
		log.append("  vendor=").append(str(0x3053)).append("  apis=").append(str(0x308D)).append('\n');
		String ext = str(0x3055);
		log.append("EGL extensions: ").append(ext == null ? "?" : ext).append('\n');

		MethodHandle bindApi = Ffi.fn(lib, "eglBindAPI", FunctionDescriptor.of(Ffi.I, Ffi.I));
		if ((int) bindApi.invoke(0x30A2) == 0) { // EGL_OPENGL_API
			throw new IllegalStateException("eglBindAPI(OpenGL) failed (error " + err() + ")");
		}
		MethodHandle choose = Ffi.fn(lib, "eglChooseConfig", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.P, Ffi.P, Ffi.I, Ffi.P));
		MemorySegment cfgAttr = arena.allocate(5L * 4, 4);
		int[] ca = {0x3033, 0, 0x3040, 0x0008, 0x3038}; // EGL_SURFACE_TYPE 0 (no surface needed), EGL_RENDERABLE_TYPE = OPENGL_BIT
		for (int i = 0; i < ca.length; i++) {
			cfgAttr.setAtIndex(Ffi.I, i, ca[i]);
		}
		MemorySegment cfgs = arena.allocate(8L * 4, 8);
		MemorySegment ncfg = arena.allocate(Ffi.I);
		int okc = (int) choose.invoke(display, cfgAttr, cfgs, 4, ncfg);
		log.append("eglChooseConfig: ok=").append(okc).append(" configs=").append(ncfg.get(Ffi.I, 0)).append('\n');
		MemorySegment config = ncfg.get(Ffi.I, 0) > 0 ? cfgs.getAtIndex(Ffi.P, 0) : MemorySegment.NULL;

		MethodHandle create = Ffi.fn(lib, "eglCreateContext", FunctionDescriptor.of(Ffi.P, Ffi.P, Ffi.P, Ffi.P, Ffi.P));
		MemorySegment ctx = MemorySegment.NULL;
		// ladder: 4.6 core, 4.3 core, whatever the driver gives by default
		int[][] ladders = {{4, 6}, {4, 3}, {0, 0}};
		for (int[] v : ladders) {
			MemorySegment at;
			if (v[0] == 0) {
				at = arena.allocate(4, 4);
				at.setAtIndex(Ffi.I, 0, 0x3038);
			} else {
				int[] ctxAttr = {0x3098, v[0], 0x30FB, v[1], 0x30FD, 1, 0x3038}; // major, minor, core profile
				at = arena.allocate(4L * ctxAttr.length, 4);
				for (int i = 0; i < ctxAttr.length; i++) {
					at.setAtIndex(Ffi.I, i, ctxAttr[i]);
				}
			}
			ctx = (MemorySegment) create.invoke(display, config, MemorySegment.NULL, at);
			log.append("eglCreateContext ").append(v[0] == 0 ? "default" : v[0] + "." + v[1] + " core").append(": ")
					.append(ctx.address() == 0 ? "failed (error " + err() + ")" : "ok").append('\n');
			if (ctx.address() != 0) {
				break;
			}
		}
		if (ctx.address() == 0) {
			throw new IllegalStateException("no OpenGL context");
		}
		MethodHandle makeCurrent = Ffi.fn(lib, "eglMakeCurrent", FunctionDescriptor.of(Ffi.I, Ffi.P, Ffi.P, Ffi.P, Ffi.P));
		if ((int) makeCurrent.invoke(display, MemorySegment.NULL, MemorySegment.NULL, ctx) == 0) {
			throw new IllegalStateException("eglMakeCurrent without a surface failed (error " + err() + ")");
		}
		current = true;
	}

	private String str(int name) throws Throwable {
		return Ffi.cstr((MemorySegment) queryString.invoke(display, name));
	}

	/** A GL function by name (cached). */
	public MethodHandle gl(String name, FunctionDescriptor fd) throws Throwable {
		MethodHandle h = glFns.get(name);
		if (h == null) {
			h = proc(name, fd);
			glFns.put(name, h);
		}
		return h;
	}

	/** glGetString(name). */
	public String glString(int name) throws Throwable {
		return Ffi.cstr((MemorySegment) gl("glGetString", FunctionDescriptor.of(Ffi.P, Ffi.I)).invoke(name));
	}

	public int glInt(int pname) throws Throwable {
		MemorySegment v = arena.allocate(Ffi.I);
		gl("glGetIntegerv", FunctionDescriptor.ofVoid(Ffi.I, Ffi.P)).invoke(pname, v);
		return v.get(Ffi.I, 0);
	}

	public Arena arena() {
		return arena;
	}

	@Override
	public void close() {
		arena.close();
	}
}
