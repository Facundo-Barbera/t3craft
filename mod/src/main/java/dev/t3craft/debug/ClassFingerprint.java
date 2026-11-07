package dev.t3craft.debug;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Structural fingerprint of a class: declared fields, methods, constructors and nested classes
 * (recursively), sorted so the order reflection happens to return does not matter.
 *
 * <p>Plain JVM HotSwap only allows method-body changes, which keep the fingerprint and are harmless
 * to in-memory state. Enhanced redefinition (JetBrains Runtime {@code -XX:+AllowEnhancedClassRedefinition},
 * DCEVM, HotswapAgent) can add/remove fields or change their types; that is exactly when stale
 * objects held in maps start throwing {@link ClassCastException}, and exactly what this detects.
 */
public final class ClassFingerprint {
	private static final int MAX_NESTING = 4;

	private ClassFingerprint() {
	}

	public static long of(Class<?> type) {
		StringBuilder sb = new StringBuilder(256);
		append(sb, type, 0);
		// 64-bit FNV-1a: stable, cheap, no allocation beyond the builder.
		long h = 0xcbf29ce484222325L;
		for (int i = 0; i < sb.length(); i++) {
			h ^= sb.charAt(i);
			h *= 0x100000001b3L;
		}
		return h;
	}

	private static void append(StringBuilder sb, Class<?> type, int depth) {
		sb.append("C:").append(type.getName()).append('{');
		try {
			List<String> parts = new ArrayList<>();
			for (Field f : type.getDeclaredFields()) {
				parts.add("F" + f.getModifiers() + ":" + f.getGenericType().getTypeName() + " " + f.getName());
			}
			for (Method m : type.getDeclaredMethods()) {
				parts.add("M" + m.getModifiers() + ":" + m.getGenericReturnType().getTypeName() + " " + m.getName()
						+ Arrays.toString(m.getGenericParameterTypes()));
			}
			for (Constructor<?> c : type.getDeclaredConstructors()) {
				parts.add("K" + c.getModifiers() + ":" + Arrays.toString(c.getGenericParameterTypes()));
			}
			parts.sort(null);
			parts.forEach(p -> sb.append(p).append(';'));
			if (depth < MAX_NESTING) {
				Class<?>[] nested = type.getDeclaredClasses();
				Arrays.sort(nested, (a, b) -> a.getName().compareTo(b.getName()));
				for (Class<?> n : nested) {
					append(sb, n, depth + 1);
				}
			}
		} catch (Throwable t) {
			// A half-redefined class or a missing optional dependency: fingerprint the failure itself
			// so a later successful read counts as a change.
			sb.append("ERR:").append(t.getClass().getName());
		}
		sb.append('}');
	}
}
