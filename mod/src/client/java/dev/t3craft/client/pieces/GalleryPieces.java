package dev.t3craft.client.pieces;

import java.util.List;

/**
 * Fake catalog for the UI gallery ({@code /t3dev ui pieces}, issue #30): a few folders with a sub-folder, a piece
 * with no size range, names with accents (to try the folded search: "cafe" finds "Café") and enough rows to
 * scroll. Most ids are real ({@code hq_hub}, catalog variants), so "Preview in game" also works from the gallery
 * inside the T3Craft HQ world; the {@code gallery_} ones are not and the chat says "no piece".
 */
public final class GalleryPieces {
	private GalleryPieces() {
	}

	public static final String LABEL = "gallery (fake data)";

	public static List<PieceEntry> entries() {
		return List.of(
				new PieceEntry("hq_hub", "Hub, the central building", "HQ", List.of("hq", "hub", "basilica"), null),
				new PieceEntry("house_timber_cottage", "Timber cottage", "House/Timber", List.of("house", "timber", "cottage"), dims(7, 11, 7, 11, 9)),
				new PieceEntry("house_timber_overhang", "Timber overhang", "House/Timber", List.of("house", "timber", "overhang"), dims(8, 13, 8, 12, 11)),
				new PieceEntry("house_japanese_teahouse", "Japanese teahouse", "House/Japanese", List.of("house", "japanese", "teahouse", "cherry"),
						dims(6, 10, 6, 10, 7)),
				new PieceEntry("house_stone_tower", "Stone tower", "House/Stone", List.of("house", "stone", "tower"), dims(5, 7, 5, 7, 14)),
				new PieceEntry("house_machiya", "Machiya", "House", List.of("house", "machiya", "japanese"), dims(5, 8, 10, 16, 9)),
				new PieceEntry("lamp_cherry_post", "Cherry post", "Lamp", List.of("lamp", "cherry", "post"), dims(1, 1, 1, 1, 4)),
				new PieceEntry("lamp_paper_plaza", "Paper plaza", "Lamp", List.of("lamp", "paper", "plaza"), dims(3, 5, 3, 5, 5)),
				new PieceEntry("fountain_koi_pond", "Koi pond", "Fountain", List.of("fountain", "koi", "pond", "water"), dims(7, 13, 7, 13, 3)),
				new PieceEntry("fountain_tiered_basins", "Tiered basins", "Fountain", List.of("fountain", "tiered", "water"), dims(9, 15, 9, 15, 6)),
				new PieceEntry("gallery_cafe_kiosk", "Café kiosk", "Stall", List.of("stall", "café", "kiosk"), dims(4, 6, 4, 6, 5)),
				new PieceEntry("gallery_jardin", "Jardín japonés", "Garden", List.of("garden", "jardín", "japonés"), dims(12, 24, 12, 24, 4)),
				new PieceEntry("gallery_plaza_grande", "Plaza grande", "Plaza center", List.of("plaza", "center", "large"), dims(16, 32, 16, 32, 8)));
	}

	private static PieceEntry.Dims dims(int minW, int maxW, int minD, int maxD, int minH) {
		return new PieceEntry.Dims(minW, maxW, minD, maxD, minH);
	}
}
