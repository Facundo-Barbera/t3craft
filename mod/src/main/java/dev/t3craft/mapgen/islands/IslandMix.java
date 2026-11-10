package dev.t3craft.mapgen.islands;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * How often each {@link IslandSize} appears in a field: four relative weights (they need not add up to 100).
 * Written {@code S40,M30,L20,XG10} (sizes with weight 0 may be left out) or as a preset name ({@link #PRESETS}).
 * The spawn island is always large, whatever the mix.
 */
public record IslandMix(int small, int medium, int large, int xlarge) {
	public static final IslandMix DEFAULT = new IslandMix(40, 30, 20, 10);

	/** Named mixes, in the order {@code /t3craft islands} lists them. */
	public static final Map<String, IslandMix> PRESETS = presets();

	private static final Pattern TOKEN = Pattern.compile("([A-Za-z]+)\\s*[:=]?\\s*(\\d{1,4})%?");

	public IslandMix {
		if (small < 0 || medium < 0 || large < 0 || xlarge < 0) {
			throw new IllegalArgumentException("negative weight in a size mix");
		}
		if (small + medium + large + xlarge <= 0) {
			throw new IllegalArgumentException("a size mix needs at least one size");
		}
	}

	private static Map<String, IslandMix> presets() {
		Map<String, IslandMix> m = new LinkedHashMap<>();
		m.put("default", DEFAULT);
		m.put("archipelago", new IslandMix(60, 30, 10, 0));
		m.put("small", new IslandMix(80, 20, 0, 0));
		m.put("even", new IslandMix(25, 25, 25, 25));
		m.put("continents", new IslandMix(10, 20, 40, 30));
		m.put("giants", new IslandMix(0, 10, 40, 50));
		return java.util.Collections.unmodifiableMap(m);
	}

	public int total() {
		return small + medium + large + xlarge;
	}

	public int weight(IslandSize s) {
		return switch (s) {
			case SMALL -> small;
			case MEDIUM -> medium;
			case LARGE -> large;
			case XLARGE -> xlarge;
		};
	}

	/** The size for {@code r} in {@code 0..total()-1}: the sizes in order, each taking its weight. */
	public IslandSize pick(int r) {
		int left = r;
		for (IslandSize s : IslandSize.values()) {
			left -= weight(s);
			if (left < 0) {
				return s;
			}
		}
		return IslandSize.XLARGE;
	}

	/** The biggest size with a weight above 0. */
	public IslandSize largest() {
		IslandSize[] all = IslandSize.values();
		for (int k = all.length - 1; k >= 0; k--) {
			if (weight(all[k]) > 0) {
				return all[k];
			}
		}
		return IslandSize.SMALL;
	}

	/** {@code S40,M30,L20,XG10}; sizes with weight 0 are left out. */
	public String text() {
		StringBuilder b = new StringBuilder();
		for (IslandSize s : IslandSize.values()) {
			if (weight(s) > 0) {
				if (b.length() > 0) {
					b.append(',');
				}
				b.append(s.code()).append(weight(s));
			}
		}
		return b.toString();
	}

	/** The preset's name when this mix is one, else {@link #text()}. */
	public String describe() {
		for (Map.Entry<String, IslandMix> e : PRESETS.entrySet()) {
			if (e.getValue().equals(this)) {
				return e.getKey() + " (" + text() + ")";
			}
		}
		return text();
	}

	/**
	 * A mix from text: a preset name, or tokens {@code <size><weight>} separated by commas or spaces
	 * ({@code S40,M30,L20,XG10}, {@code s50 l50}, {@code XL100}; {@code S:40} and {@code S40%} work too). A size
	 * given twice keeps the last weight. Null when the text is neither or every weight is 0.
	 */
	public static @Nullable IslandMix parse(@Nullable String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String t = text.trim();
		IslandMix preset = PRESETS.get(t.toLowerCase(Locale.ROOT));
		if (preset != null) {
			return preset;
		}
		int[] w = new int[IslandSize.values().length];
		for (String part : t.split("[,;\\s]+")) {
			if (part.isEmpty()) {
				continue;
			}
			Matcher m = TOKEN.matcher(part);
			if (!m.matches()) {
				return null;
			}
			IslandSize s = IslandSize.byCode(m.group(1));
			if (s == null) {
				return null;
			}
			w[s.ordinal()] = Integer.parseInt(m.group(2));
		}
		if (w[0] + w[1] + w[2] + w[3] <= 0) {
			return null;
		}
		return new IslandMix(w[0], w[1], w[2], w[3]);
	}

	/** The preset names, for chat and tab completion. */
	public static String presetNames() {
		return String.join(", ", PRESETS.keySet());
	}
}
