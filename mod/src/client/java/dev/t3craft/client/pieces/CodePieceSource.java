package dev.t3craft.client.pieces;

import dev.t3craft.T3Craft;
import dev.t3craft.hq.citadel.catalog.Catalog;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The provisional catalog: every {@link Variant} the code already registers
 * ({@code CatalogEntries}), read through {@link Catalog#all()}. Nothing here is hand-written per piece:
 * <ul>
 * <li>the folder is the family ({@code House}); houses get a sub-folder by style
 * ({@code House/Japanese}, {@code House/Timber}…) when the id says which;</li>
 * <li>the name is the id without the family prefix ({@code house_timber_cottage} → "Timber cottage");</li>
 * <li>the tags are the words of that name, plus the family, so "cherry" finds every cherry piece;</li>
 * <li>the size is the variant's own range ({@code minW..maxW} and so on).</li>
 * </ul>
 * Read once, lazily: the catalog classes are only touched when the browser is opened.
 */
public final class CodePieceSource implements PieceCatalogSource {
	/** Second words of a house id that name a style worth a sub-folder. */
	private static final Set<String> HOUSE_STYLES = Set.of("timber", "stone", "japanese", "terrace", "tower");
	/** Words that carry no meaning as a tag. */
	private static final Set<String> NOISE = Set.of("the", "and", "of", "with", "a");

	private List<PieceEntry> cached;

	@Override
	public String label() {
		return "code catalog";
	}

	@Override
	public synchronized List<PieceEntry> entries() {
		if (cached == null) {
			cached = read();
		}
		return cached;
	}

	private static List<PieceEntry> read() {
		List<PieceEntry> out = new ArrayList<>();
		try {
			for (Variant v : Catalog.all()) {
				out.add(entry(v));
			}
		} catch (RuntimeException | LinkageError e) {
			// a broken variant must not take the browser down: show what could be read
			T3Craft.LOGGER.warn("piece browser: could not read the code catalog (kept {} piece(s))", out.size(), e);
		}
		return out;
	}

	static PieceEntry entry(Variant v) {
		Family f = v.family();
		String prefix = f.name().toLowerCase(Locale.ROOT) + "_";
		String rest = v.id().startsWith(prefix) ? v.id().substring(prefix.length()) : v.id();
		List<String> words = new ArrayList<>();
		for (String w : rest.split("_")) {
			if (w.length() >= 2 && !NOISE.contains(w) && !w.chars().allMatch(Character::isDigit)) {
				words.add(w);
			}
		}
		String folder = familyLabel(f);
		if (f == Family.HOUSE && !words.isEmpty() && HOUSE_STYLES.contains(words.get(0))) {
			folder = folder + "/" + capitalise(words.get(0));
		}
		List<String> tags = new ArrayList<>();
		tags.add(f.name().toLowerCase(Locale.ROOT).replace('_', ' '));
		for (String w : words) {
			if (!tags.contains(w)) {
				tags.add(w);
			}
		}
		PieceEntry.Dims dims = new PieceEntry.Dims(v.minW(), Math.max(v.minW(), v.maxW()), v.minD(), Math.max(v.minD(), v.maxD()), v.minH());
		return new PieceEntry(v.id(), capitalise(String.join(" ", words.isEmpty() ? List.of(rest.replace('_', ' ')) : words)), folder, tags, dims);
	}

	/** {@code PLAZA_CENTER} → "Plaza center". */
	static String familyLabel(Family f) {
		return capitalise(f.name().toLowerCase(Locale.ROOT).replace('_', ' '));
	}

	private static String capitalise(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
