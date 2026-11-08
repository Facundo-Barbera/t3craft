package dev.t3craft.hq.citadel;

/**
 * A piece of the citadel: a self-contained part of the plan that paints itself on the shared
 * {@link CitadelCanvas}. A piece reads the canvas (zones, layout, lots) and writes cells, fixtures and
 * anchors through its helpers; it never keeps state of its own, so pieces can be edited, rendered and
 * reasoned about one at a time. See the extraction map in {@link CitadelCanvas} for the families
 * ({@code CitadelHub}, {@code CitadelGrounds}, {@code CitadelNodes}, {@code CitadelBarrios},
 * {@code CitadelFinish}).
 *
 * <p>A piece that needs arguments (a node, a lot) is a lambda or a small record capturing them:
 * {@code c -> CitadelNodes.node(c, nd)}. Pieces run in the order the generator lists them: the order
 * of the calls decides which block wins on a shared cell, so it never changes without changing the plan.
 */
@FunctionalInterface
public interface CitadelPiece {
	/** Paints this piece on {@code c}. */
	void place(CitadelCanvas c);
}
