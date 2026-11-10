package dev.t3craft.client.blocks;

import java.util.Map;

/**
 * A lamp's own display options (issue #43), stored as strings in the block entity's {@code options} compound by the
 * lamp's config page and read by {@link LampRenderer}. A missing or unknown value is the default, and the defaults are
 * today's look: normal glow, pulsing with the state, motes rising from lamps that ask for the player.
 *
 * <pre>
 * options: { glow:   "off" | "soft" | "normal" | "strong",
 *            pulse:  "true" | "false"   (false = the glow holds a steady level instead of breathing),
 *            motes:  "true" | "false" }
 * </pre>
 * The block state still carries the status (model and light); these options change only the additive glow the
 * renderer draws over the lens (and the paper lantern's body, and the beacon's beam).
 */
public record LampOptions(Glow glow, boolean pulse, boolean motes) {
	public static final String GLOW = "glow";
	public static final String PULSE = "pulse";
	public static final String MOTES = "motes";

	/** How strong the glow is: a factor on the alpha of every glow quad. */
	public enum Glow {
		OFF("off", "Off", 0f),
		SOFT("soft", "Soft", 0.55f),
		NORMAL("normal", "Normal", 1f),
		STRONG("strong", "Strong", 1.45f);

		public final String id;
		public final String label;
		public final float gain;

		Glow(String id, String label, float gain) {
			this.id = id;
			this.label = label;
			this.gain = gain;
		}

		static Glow parse(String s) {
			for (Glow g : values()) {
				if (g.id.equals(s)) {
					return g;
				}
			}
			return NORMAL;
		}
	}

	public static final LampOptions DEFAULT = new LampOptions(Glow.NORMAL, true, true);

	public LampOptions {
		glow = glow == null ? Glow.NORMAL : glow;
	}

	/** From a block entity's options (empty → {@link #DEFAULT}, without allocating). */
	public static LampOptions of(Map<String, String> o) {
		if (o == null || o.isEmpty()) {
			return DEFAULT;
		}
		return new LampOptions(Glow.parse(o.getOrDefault(GLOW, Glow.NORMAL.id)), !"false".equals(o.get(PULSE)), !"false".equals(o.get(MOTES)));
	}

	public boolean isDefault() {
		return equals(DEFAULT);
	}
}
