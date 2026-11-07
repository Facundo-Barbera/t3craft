package dev.t3craft.client.portable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The small environment badge shown on every thread row, like T3 Code's environment icon in its
 * sidebar: one or two letters on a colour chip. Environments are no longer separate sections, just
 * this badge (and an optional filter).
 *
 * <p>Letters: the initials of the last two words of the label ("my-vps" → "BV",
 * "my-acmesite" → "BF"), or the first two letters of a one-word label ("mini" → "MI"). When two
 * environments would get the same letters, the colliding ones use the first two letters of their
 * last word, then a number. Colour: a stable pick from a palette by environment id, unless the
 * config gives one ({@link #withColors}).
 */
public record EnvBadge(String envId, String letters, int color) {
	/** Readable on the dark terminal panel; avoids the state colours' exact hues. */
	static final int[] PALETTE = {0x5B8DEF, 0xC678DD, 0x2FA3A0, 0xD97757, 0xC9A227, 0x8FA98B, 0xE06C9F, 0x7F8C9A};

	/** Badges for all environments at once, so letters can be disambiguated. Input: id → label. */
	public static Map<String, EnvBadge> forEnvironments(Map<String, String> labels) {
		Map<String, String> letters = new LinkedHashMap<>();
		labels.forEach((id, label) -> letters.put(id, initials(label == null || label.isBlank() ? id : label)));
		disambiguate(labels, letters);
		Map<String, EnvBadge> out = new LinkedHashMap<>();
		letters.forEach((id, l) -> out.put(id, new EnvBadge(id, l, colorFor(id))));
		return out;
	}

	/** Applies configured colours (0xRRGGBB) over the defaults; ids without one keep theirs. */
	public static Map<String, EnvBadge> withColors(Map<String, EnvBadge> badges, Map<String, Integer> colors) {
		Map<String, EnvBadge> out = new LinkedHashMap<>();
		badges.forEach((id, b) -> {
			Integer c = colors.get(id);
			out.put(id, c == null ? b : new EnvBadge(id, b.letters(), c & 0xFFFFFF));
		});
		return out;
	}

	static int colorFor(String envId) {
		return PALETTE[Math.floorMod(envId.hashCode(), PALETTE.length)];
	}

	static List<String> words(String label) {
		List<String> out = new ArrayList<>();
		for (String w : label.split("[^\\p{L}\\p{N}]+")) {
			if (!w.isEmpty()) {
				out.add(w);
			}
		}
		return out;
	}

	static String initials(String label) {
		List<String> w = words(label);
		if (w.isEmpty()) {
			return "?";
		}
		if (w.size() == 1) {
			return upper(w.get(0).substring(0, Math.min(2, w.get(0).length())));
		}
		return upper(w.get(w.size() - 2).substring(0, 1) + w.get(w.size() - 1).substring(0, 1));
	}

	private static void disambiguate(Map<String, String> labels, Map<String, String> letters) {
		Map<String, List<String>> byLetters = group(letters);
		for (List<String> ids : byLetters.values()) {
			if (ids.size() < 2) {
				continue;
			}
			for (String id : ids) {
				List<String> w = words(labels.get(id) == null ? id : labels.get(id));
				if (!w.isEmpty()) {
					String last = w.get(w.size() - 1);
					letters.put(id, upper(last.substring(0, Math.min(2, last.length()))));
				}
			}
		}
		// still colliding: number them in order
		for (List<String> ids : group(letters).values()) {
			if (ids.size() < 2) {
				continue;
			}
			for (int i = 0; i < ids.size(); i++) {
				String l = letters.get(ids.get(i));
				letters.put(ids.get(i), l.substring(0, 1) + (i + 1));
			}
		}
	}

	private static Map<String, List<String>> group(Map<String, String> letters) {
		Map<String, List<String>> by = new HashMap<>();
		letters.forEach((id, l) -> by.computeIfAbsent(l, k -> new ArrayList<>()).add(id));
		by.values().forEach(ids -> ids.sort(null));
		return by;
	}

	private static String upper(String s) {
		return s.toUpperCase(Locale.ROOT);
	}

	/** Badge for an environment missing from the map (bridge mode before its status arrives). */
	public static EnvBadge fallback(String envId, Collection<EnvBadge> known) {
		return new EnvBadge(envId, initials(envId.isEmpty() ? "?" : envId), colorFor(envId));
	}
}
