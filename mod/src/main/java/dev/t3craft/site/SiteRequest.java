package dev.t3craft.site;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/**
 * What a site must hold: the first module ({@code base}, leveled and built on confirm) and the
 * <b>growth envelope</b>, the largest footprint the HQ may ever reach. Both are plan-relative
 * (see {@link SiteFrame}). Selection evaluates the whole envelope (no fluids, nothing built, bounded
 * relief) so later modules always fit; only the base is leveled at first.
 *
 * <p>The envelope is a parameter because the module layout belongs to {@code feat/hq-design}; set
 * it with {@code SiteRuntime.setRequest}.
 */
public record SiteRequest(SiteModule base, Rect envelope, int baseDepth) {
	/** Largest {@link #baseDepth} (a basement of a few layers under the pad). */
	public static final int MAX_BASE_DEPTH = 8;

	/** Largest envelope side (keeps evaluation bounded and inside already-loaded chunks). */
	public static final int MAX_ENVELOPE = 64;

	/** The original fixed site: one 15×15 footprint, no room to grow. */
	public static final SiteRequest SQUARE = new SiteRequest(new SiteModule("base", "", Rect.centred(SiteLimits.FOOTPRINT, SiteLimits.FOOTPRINT,
			-(SiteLimits.FOOTPRINT / 2))), Rect.centred(SiteLimits.FOOTPRINT, SiteLimits.FOOTPRINT, -(SiteLimits.FOOTPRINT / 2)));

	/**
	 * Default until the HQ design provides its own: the 15×15 base at the front, room for the HQ to
	 * double sideways and backwards (31 wide × 31 deep, sharing the base's front edge).
	 */
	public static final SiteRequest GROWABLE = new SiteRequest(SQUARE.base(), Rect.centred(31, 31, -(SiteLimits.FOOTPRINT / 2)));

	/** No basement: the base design starts on the first layer above the pad. */
	public SiteRequest(SiteModule base, Rect envelope) {
		this(base, envelope, 0);
	}

	/**
	 * @param baseDepth layers at and below the pad that the <b>base</b> design may use (a basement):
	 *                  its design area then starts at {@code pad + 1 - baseDepth} and its snapshot box
	 *                  reaches at least one layer below that. 0 = none (the original behaviour).
	 */
	public SiteRequest {
		Objects.requireNonNull(base, "base");
		if (baseDepth < 0 || baseDepth > MAX_BASE_DEPTH) {
			throw new IllegalArgumentException("base depth " + baseDepth + " outside 0.." + MAX_BASE_DEPTH);
		}
		Objects.requireNonNull(envelope, "envelope");
		if (!envelope.contains(base.rect())) {
			throw new IllegalArgumentException("the base " + base.rect() + " must lie inside the envelope " + envelope);
		}
		if (envelope.width() > MAX_ENVELOPE || envelope.depth() > MAX_ENVELOPE) {
			throw new IllegalArgumentException("envelope larger than " + MAX_ENVELOPE + ": " + envelope);
		}
		if (!envelope.contains(new Rect(0, 0, 0, 0))) {
			throw new IllegalArgumentException("the envelope must contain the origin (0, 0)");
		}
	}

	/** Distance from the origin to the envelope's front edge plus the apron: how far ahead "here" puts the origin. */
	public int frontReach() {
		return -envelope.minZ() + SiteLimits.APRON + 2;
	}

	public CompoundTag toTag() {
		CompoundTag t = new CompoundTag();
		t.put("base", base.toTag());
		t.putIntArray("envelope", envelope.toArray());
		if (baseDepth > 0) {
			t.putInt("baseDepth", baseDepth);
		}
		return t;
	}

	public static SiteRequest fromTag(CompoundTag t) {
		return new SiteRequest(SiteModule.fromTag(t.getCompoundOrEmpty("base")), Rect.fromArray(t.getIntArray("envelope").orElse(null)),
				t.getIntOr("baseDepth", 0));
	}
}
