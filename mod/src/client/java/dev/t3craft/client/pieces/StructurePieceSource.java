package dev.t3craft.client.pieces;

import dev.t3craft.piece.StructurePiece;
import java.util.ArrayList;
import java.util.List;

/**
 * Issue #49: the big structures of the HQ (the leader's office, a lead's office, the hub, a barrio's gatehouse, a barrio hall)
 * as entries of the browser, in their own folder {@value #FOLDER}. They come from the in-world catalog's group
 * {@value StructurePiece#GROUP} ({@link StructurePiece}), so the ids are the ones {@code /t3craft piece show} takes
 * ({@code struct_leader_office}...).
 *
 * <ul>
 * <li>name: the structure's title; tags: its own search words (office, oficina, leader, control...) plus {@code structure};</li>
 * <li>size: its real size ({@code minW == maxW}, {@code minD == maxD}, the height): measured on the stage once it has been built
 * this session (the browser is refreshed then), an estimate from the citadel's geometry before that;</li>
 * <li>not {@link PieceEntry#parametric() parametric}: the size, facing and seed of a request are ignored by the game.</li>
 * </ul>
 * Not cached: five entries, and the measured sizes change.
 */
public final class StructurePieceSource implements PieceCatalogSource {
	public static final String FOLDER = "Structures";

	@Override
	public String label() {
		return "HQ structures";
	}

	@Override
	public List<PieceEntry> entries() {
		List<PieceEntry> out = new ArrayList<>();
		for (StructurePiece s : StructurePiece.list()) {
			StructurePiece.Size z = s.size();
			List<String> tags = new ArrayList<>(s.tags());
			if (!tags.contains("structure")) {
				tags.add("structure");
			}
			PieceEntry.Dims dims = new PieceEntry.Dims(z.w(), z.w(), z.d(), z.d(), z.h());
			out.add(new PieceEntry(s.id(), s.title(), FOLDER, tags, dims, false));
		}
		return out;
	}
}
