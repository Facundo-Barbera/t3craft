package dev.t3craft.client.workstation;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Thread → where its workstation is. Called on the client thread every tick for each visible
 * thread: return cached values, do not scan the world here.
 *
 * <p><b>Integration point (hq-design).</b> When the modular HQ lands on main, an adapter maps each
 * desk's {@code computerPos} and {@code screenPos} + orientation to {@link StationPlacement} (use
 * {@link StationPlacement#on} if the HQ only gives the computer block and a facing) and registers it
 * with {@code WorkstationClient.get().setPlacementSource(src)}; {@code null} goes back to the
 * provisional {@link DeskPlacement}. Returning null for a thread hides its station (e.g. the thread
 * was settled and its desk moved to the archive).
 */
public interface WorkstationPlacementSource {
	/** No stations anywhere. */
	WorkstationPlacementSource NONE = t -> null;

	@Nullable StationPlacement placement(WorkstationThread thread);

	/**
	 * Called once per tick before {@link #placement} with every thread that will be asked about,
	 * so a source can (re)build a layout when the set changes. Default: nothing.
	 */
	default void prepare(List<WorkstationThread> threads) {
	}
}
