package dev.t3craft.build;

/**
 * The default {@link PlacementOrder}: the {@link Fronts} schedule (feat/parallel-build). Each layer
 * (segment) is cut into one band per front, and no front starts layer {@code s + 1} before every front
 * finished layer {@code s} (layer barrier), so nothing is placed above before what is below.
 */
public final class LayeredOrder implements PlacementOrder {
	public static final Factory FACTORY = (plan, fronts) -> new LayeredOrder(Fronts.of(plan, Math.max(1, fronts)));

	private final Fronts fronts;
	private final int[] pos;
	private int segment;

	public LayeredOrder(Fronts fronts) {
		this.fronts = fronts;
		this.pos = new int[fronts.fronts()];
		skipFinished();
	}

	public Fronts schedule() {
		return fronts;
	}

	@Override
	public int fronts() {
		return fronts.fronts();
	}

	@Override
	public int total() {
		return fronts.total();
	}

	@Override
	public int peek(int front) {
		if (segment >= fronts.segments()) {
			return -1;
		}
		int[] lane = fronts.lanes(segment)[front];
		return pos[front] < lane.length ? lane[pos[front]] : -1;
	}

	@Override
	public void advance(int front) {
		int[] lane = fronts.lanes(segment)[front];
		if (pos[front] >= lane.length) {
			throw new IllegalStateException("front " + front + " has nothing to advance");
		}
		pos[front]++;
		skipFinished();
	}

	private void skipFinished() {
		while (segment < fronts.segments() && segmentDone()) {
			segment++;
			java.util.Arrays.fill(pos, 0);
		}
	}

	private boolean segmentDone() {
		int[][] lanes = fronts.lanes(segment);
		for (int k = 0; k < pos.length; k++) {
			if (pos[k] < lanes[k].length) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean done() {
		return segment >= fronts.segments();
	}

	@Override
	public int stage() {
		return segment;
	}

	@Override
	public int[] pendingInStage() {
		if (segment >= fronts.segments()) {
			return new int[0];
		}
		int[][] lanes = fronts.lanes(segment);
		int count = 0;
		for (int k = 0; k < pos.length; k++) {
			count += lanes[k].length - pos[k];
		}
		int[] out = new int[count];
		int j = 0;
		for (int k = 0; k < pos.length; k++) {
			for (int p = pos[k]; p < lanes[k].length; p++) {
				out[j++] = lanes[k][p];
			}
		}
		java.util.Arrays.sort(out);
		return out;
	}

	@Override
	public int frontOf(int i) {
		return fronts.frontOf(i);
	}
}
