package dev.t3craft.debug;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HotSwapFieldsTest {
	/** Mimics an entrypoint whose field was added by hot-swap: it starts null, init never ran. */
	static final class Holder {
		Object field;
		final AtomicInteger created = new AtomicInteger();

		Object field() {
			return field = HotSwapFields.orInit(field, () -> {
				created.incrementAndGet();
				return new Object();
			});
		}
	}

	@Test
	void createsOnFirstAccessWithoutInit() {
		Holder h = new Holder();
		Object first = h.field();
		assertNotNull(first);
		assertSame(first, h.field());
		assertSame(first, h.field);
		assertSame(1, h.created.get());
	}

	@Test
	void keepsExistingValue() {
		Object existing = new Object();
		assertSame(existing, HotSwapFields.orInit(existing, Object::new));
	}

	@Test
	void rejectsNullInit() {
		assertThrows(IllegalStateException.class, () -> HotSwapFields.orInit(null, () -> null));
	}
}
