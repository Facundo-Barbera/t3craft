package dev.t3craft.build;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A build with its animated crew, from the moment the builders set off until they leave. Exists
 * before the {@link BuildJob} does: the job is submitted only once the crew reaches the site.
 * Controls are safe from any thread; a pause or cancel issued while the crew is still walking in
 * is applied to the job when it is created.
 */
public interface BuildHandle {
	enum Phase {
		/** Builders walking to the site; no block placed yet. */
		ARRIVING,
		BUILDING,
		/** Job finished or cancelled; builders walking away. */
		LEAVING,
		/** Builders gone. */
		DONE
	}

	String label();

	Phase phase();

	/** The job, once the crew has arrived; null while arriving or if cancelled before arriving. */
	@Nullable BuildJob job();

	void pause();

	void resume();

	void cancel();

	/** Item id → count the build is waiting for (survival); empty otherwise. See {@link BuildJob#missingMaterials()}. */
	default Map<String, Integer> missingMaterials() {
		BuildJob j = job();
		return j == null ? Map.of() : j.missingMaterials();
	}

	/** 0..1; 0 while arriving. */
	default double progress() {
		BuildJob j = job();
		return j == null ? 0.0 : j.progress();
	}
}
