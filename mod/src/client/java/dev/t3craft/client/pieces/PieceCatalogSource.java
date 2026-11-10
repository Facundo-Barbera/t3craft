package dev.t3craft.client.pieces;

import java.util.List;

/**
 * Where the browser gets its pieces. Register one with {@link PieceCatalog#register} and its entries show up
 * in the browser (the next time it is opened or refreshed). A piece with the same {@link PieceEntry#id()} as
 * one of an earlier source replaces it: that is how the real in-world catalog
 * (feat/piece-catalog-preview) takes over from {@link CodePieceSource}, the provisional one, piece by piece.
 */
public interface PieceCatalogSource {
	/** Short name for the footer ("code catalog"). */
	String label();

	/** The pieces now. Called on the client thread, on every refresh: keep it cheap or cache. */
	List<PieceEntry> entries();
}
