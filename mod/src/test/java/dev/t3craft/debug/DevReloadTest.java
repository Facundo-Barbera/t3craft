package dev.t3craft.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DevReloadTest {
	static final class Holder {
		int a;

		void m() {
		}

		record Inner(String x) {
		}
	}

	static final class HolderV2 {
		long a;

		void m() {
		}

		record Inner(String x) {
		}
	}

	@Test
	void fingerprintIsStableAndShapeSensitive() {
		assertEquals(ClassFingerprint.of(Holder.class), ClassFingerprint.of(Holder.class));
		// Same member names, different field type and enclosing name: different fingerprint.
		assertNotEquals(ClassFingerprint.of(Holder.class), ClassFingerprint.of(HolderV2.class));
	}

	@Test
	void resetAllCallsEveryTargetAndIsolatesFailures() {
		DevReload r = new DevReload();
		List<String> calls = new ArrayList<>();
		r.register("a", reason -> calls.add("a:" + reason));
		r.register("boom", reason -> {
			throw new IllegalStateException("boom");
		});
		r.register("c", reason -> calls.add("c:" + reason));
		assertEquals(2, r.resetAll(ResetReason.MANUAL));
		assertEquals(List.of("a:MANUAL", "c:MANUAL"), calls);
		assertEquals(1, r.resets());
	}

	@Test
	void registeringSameIdReplaces() {
		DevReload r = new DevReload();
		List<String> calls = new ArrayList<>();
		r.register("x", reason -> calls.add("old"));
		r.register("x", reason -> calls.add("new"));
		r.resetAll(ResetReason.MANUAL);
		assertEquals(List.of("new"), calls);
		assertEquals(List.of("x"), r.ids());
		r.unregister("x");
		assertTrue(r.ids().isEmpty());
	}

	@Test
	void reentrantResetIsIgnored() {
		DevReload r = new DevReload();
		int[] count = {0};
		r.register("loop", reason -> {
			count[0]++;
			r.resetAll(ResetReason.MANUAL);
		});
		r.resetAll(ResetReason.MANUAL);
		assertEquals(1, count[0]);
	}

	@Test
	void noRedefinitionWithoutChange() {
		DevReload r = new DevReload();
		List<ResetReason> calls = new ArrayList<>();
		r.register("h", calls::add, Holder.class);
		assertFalse(r.checkForRedefinition());
		assertTrue(calls.isEmpty());
	}

	@Test
	void redefinitionTriggersResetOnce() throws Exception {
		DevReload r = new DevReload();
		List<ResetReason> calls = new ArrayList<>();
		r.register("h", calls::add, Holder.class);
		// Simulate a hot-swap that changed the class layout by corrupting the stored fingerprint.
		corruptFingerprints(r);
		assertTrue(r.checkForRedefinition());
		assertEquals(List.of(ResetReason.REDEFINITION), calls);
		assertEquals(1, r.redefinitions());
		// The new fingerprint is remembered: no second reset.
		assertFalse(r.checkForRedefinition());
		assertEquals(1, calls.size());
	}

	@Test
	void lambdaTargetsAreNotWatched() {
		DevReload r = new DevReload();
		r.register("l", reason -> {
		});
		assertFalse(r.checkForRedefinition());
	}

	@SuppressWarnings("unchecked")
	private static void corruptFingerprints(DevReload r) throws Exception {
		Field entries = DevReload.class.getDeclaredField("entries");
		entries.setAccessible(true);
		for (Object e : ((java.util.Map<String, Object>) entries.get(r)).values()) {
			var watched = (List<Object>) e.getClass().getDeclaredMethod("watched").invoke(e);
			for (Object w : watched) {
				Field fp = w.getClass().getDeclaredField("fingerprint");
				fp.setAccessible(true);
				fp.setLong(w, 42L);
			}
		}
	}
}
