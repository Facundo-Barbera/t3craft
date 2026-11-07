package dev.t3craft.client.npc.behavior;

import java.util.List;

/** Outcome of a route request: found (with points, first = from), none, or still searching (ask again next tick). */
public record Route(Status status, List<Pos> points) {
	public enum Status {
		FOUND, NONE, PENDING
	}

	public static final Route NONE = new Route(Status.NONE, List.of());
	public static final Route PENDING = new Route(Status.PENDING, List.of());

	public static Route found(List<Pos> points) {
		return new Route(Status.FOUND, List.copyOf(points));
	}
}
