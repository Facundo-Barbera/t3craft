package dev.t3craft.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModuleGuardTest {
	private final DevReload reload = new DevReload();
	private final ModuleGuard guard = new ModuleGuard(reload, 3, () -> 0L);

	@Test
	void successRunsAndReportsTrue() {
		int[] n = {0};
		assertTrue(guard.run("m", () -> n[0]++));
		assertEquals(1, n[0]);
		assertTrue(guard.isEnabled("m"));
	}

	@Test
	void failuresAreSwallowedAndDisableAfterThreshold() {
		int[] calls = {0};
		Runnable bad = () -> {
			calls[0]++;
			throw new IllegalStateException("nope");
		};
		assertFalse(guard.run("m", bad));
		assertFalse(guard.run("m", bad));
		assertTrue(guard.isEnabled("m"));
		assertFalse(guard.run("m", bad));
		assertFalse(guard.isEnabled("m"));
		// Disabled: body no longer called.
		assertFalse(guard.run("m", bad));
		assertEquals(3, calls[0]);
		ModuleGuard.Status s = guard.status().get(0);
		assertEquals("m", s.module());
		assertFalse(s.enabled());
		assertEquals(3, s.totalFailures());
		assertEquals("IllegalStateException: nope", s.lastError());
	}

	@Test
	void successResetsTheStreak() {
		Runnable bad = () -> {
			throw new RuntimeException();
		};
		guard.run("m", bad);
		guard.run("m", bad);
		guard.run("m", () -> {
		});
		guard.run("m", bad);
		guard.run("m", bad);
		assertTrue(guard.isEnabled("m"));
	}

	@Test
	void staleStateErrorTriggersOneRecoveryResetPerStreak() {
		List<ResetReason> resets = new ArrayList<>();
		reload.register("npcs", resets::add);
		Runnable cce = () -> {
			throw new ClassCastException("Spawned cannot be cast to Spawned");
		};
		guard.run("npcs", cce);
		guard.run("npcs", cce);
		assertEquals(List.of(ResetReason.RECOVERY), resets);
		// A wrapped LinkageError counts too, after the streak is broken.
		guard.run("npcs", () -> {
		});
		guard.run("npcs", () -> {
			throw new RuntimeException(new NoSuchFieldError("anchor"));
		});
		assertEquals(List.of(ResetReason.RECOVERY, ResetReason.RECOVERY), resets);
	}

	@Test
	void recoveryResetDoesNotReEnableButManualDoes() {
		Runnable cce = () -> {
			throw new ClassCastException();
		};
		guard.run("m", cce);
		guard.run("m", cce);
		guard.run("m", cce);
		assertFalse(guard.isEnabled("m"));
		reload.resetAll(ResetReason.RECOVERY);
		assertFalse(guard.isEnabled("m"));
		reload.resetAll(ResetReason.MANUAL);
		assertTrue(guard.isEnabled("m"));
	}

	@Test
	void enableAndDisableByName() {
		assertFalse(guard.enable("unknown"));
		guard.disable("x");
		assertFalse(guard.run("x", () -> {
		}));
		assertTrue(guard.enable("x"));
		assertTrue(guard.run("x", () -> {
		}));
	}

	@Test
	void callReturnsFallbackOnFailure() {
		assertEquals(7, guard.call("c", () -> 7, -1));
		assertEquals(-1, guard.call("c", () -> {
			throw new IllegalArgumentException();
		}, -1));
	}

	@Test
	void outOfMemoryIsNotSwallowed() {
		assertThrows(OutOfMemoryError.class, () -> guard.run("oom", () -> {
			throw new OutOfMemoryError("test");
		}));
	}

	@Test
	void errorsAreSwallowedToo() {
		assertFalse(guard.run("so", () -> {
			throw new StackOverflowError();
		}));
		assertTrue(ModuleGuard.looksStale(new IncompatibleClassChangeError()));
		assertFalse(ModuleGuard.looksStale(new IllegalStateException()));
	}
}
