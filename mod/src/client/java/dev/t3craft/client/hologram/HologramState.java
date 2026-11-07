package dev.t3craft.client.hologram;

/**
 * Keeps the last {@link HologramModel.Inputs} and rebuilds the model only when they change (the
 * AgentCraft pattern: count once, relayout only on a new sequence). {@link #builds()} counts the
 * rebuilds, so tests and {@code /t3craft} debugging can check that a quiet tick does no work.
 * Client thread only.
 */
public final class HologramState {
	private HologramModel.Inputs last;
	private HologramModel model = HologramModel.HIDDEN;
	private long builds;

	/** Returns true when the inputs changed and the model was rebuilt. */
	public boolean update(HologramModel.Inputs in) {
		if (in.equals(last)) {
			return false;
		}
		last = in;
		model = HologramModel.build(in);
		builds++;
		return true;
	}

	public HologramModel model() {
		return model;
	}

	public long builds() {
		return builds;
	}

	/** Forget everything (world left): the next update rebuilds. */
	public void reset() {
		last = null;
		model = HologramModel.HIDDEN;
	}
}
