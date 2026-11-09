package dev.t3craft.tools.hqrender.gpu;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/** Small helpers over the foreign function API (final since Java 22). Nothing here touches Minecraft classes. */
final class Ffi {
	static final Linker LINKER = Linker.nativeLinker();
	static final ValueLayout.OfInt I = ValueLayout.JAVA_INT;
	static final ValueLayout.OfLong L = ValueLayout.JAVA_LONG;
	static final ValueLayout.OfFloat F = ValueLayout.JAVA_FLOAT;
	static final AddressLayout P = ValueLayout.ADDRESS;

	private Ffi() {
	}

	/** The first library that opens (dlopen name or path), or null; what happened is appended to {@code log}. */
	static SymbolLookup open(Arena arena, StringBuilder log, String... names) {
		for (String n : names) {
			try {
				SymbolLookup l = SymbolLookup.libraryLookup(n, arena);
				log.append("opened ").append(n).append('\n');
				return l;
			} catch (RuntimeException | LinkageError e) {
				log.append("cannot open ").append(n).append(": ").append(e.getMessage()).append('\n');
			}
		}
		return null;
	}

	static MethodHandle fn(SymbolLookup lib, String name, FunctionDescriptor fd) {
		MemorySegment s = lib.find(name).orElseThrow(() -> new IllegalStateException("symbol not found: " + name));
		return LINKER.downcallHandle(s, fd);
	}

	static MethodHandle at(MemorySegment address, FunctionDescriptor fd) {
		return LINKER.downcallHandle(address, fd);
	}

	/** A C string returned by native code (null for a null pointer). */
	static String cstr(MemorySegment p) {
		if (p == null || p.address() == 0) {
			return null;
		}
		return p.reinterpret(Long.MAX_VALUE).getString(0);
	}
}
