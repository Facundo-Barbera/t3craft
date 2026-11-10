package dev.t3craft.client.pieces;

/**
 * The hook between the browser and "see it in the game". The browser builds a {@link PieceRequest} and calls
 * {@link #request}; whatever answers decides how the piece reaches the world and tells the browser what to
 * show as feedback ({@link Result}).
 *
 * <p>The default is {@code PiecePreviews.CommandPiecePreview}, which runs {@code /t3craft piece show <id> <min|mid|max|WxDxH> <n|e|s|w> <seed>},
 * the command the in-world preview ({@code client/piece/PieceClient}) registers ({@link PieceRequest#command()}).
 * Something else can replace the hook with {@code PiecePreviews.install(...)} and call its own API directly.
 * See docs/features/piece-browser.md.
 */
@FunctionalInterface
public interface PiecePreview {
	enum Status {
		/** The request left for the game (a command ran, an API was called). Does not mean it was built yet. */
		SENT,
		/** It cannot be asked right now (no world, no preview installed); {@code message} says why and what to do. */
		UNAVAILABLE,
		/** It was tried and failed. */
		FAILED
	}

	/** @param message one or two short lines the browser shows under the buttons */
	record Result(Status status, String message) {
		public static Result sent(String message) {
			return new Result(Status.SENT, message);
		}

		public static Result unavailable(String message) {
			return new Result(Status.UNAVAILABLE, message);
		}

		public static Result failed(String message) {
			return new Result(Status.FAILED, message);
		}
	}

	/** Called on the client thread when the user presses "Preview in game". Must not block. */
	Result request(PieceRequest request);
}
