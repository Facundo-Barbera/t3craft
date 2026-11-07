package dev.t3craft.client.site;

import dev.t3craft.site.Box;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import org.jspecify.annotations.Nullable;

/**
 * Client-side particle preview: the site box outline (green = can build, red = refused, blue =
 * existing site), the pad edge in white, and conflicting cells (the player's changes) in orange.
 * Purely visual; never touches blocks. Cleared after a minute, on cancel/confirm or world change.
 */
final class SitePreview {
	private static final int EVERY_TICKS = 8;
	private static final int LIFETIME_TICKS = 20 * 60;
	private static final ParticleOptions OK = new DustParticleOptions(0x40FF60, 1.2f);
	private static final ParticleOptions BAD = new DustParticleOptions(0xFF3030, 1.2f);
	private static final ParticleOptions SITE = new DustParticleOptions(0x40A0FF, 1.2f);
	private static final ParticleOptions PAD = new DustParticleOptions(0xFFFFFF, 1.0f);
	private static final ParticleOptions MARK = new DustParticleOptions(0xFF9020, 1.5f);

	enum Style {
		OK, BAD, SITE
	}

	private @Nullable Box outline;
	private @Nullable Box pad;
	private List<int[]> marks = List.of();
	private Style style = Style.OK;
	private @Nullable ClientLevel level;
	private int age;

	void show(@Nullable Box outline, @Nullable Box pad, List<int[]> marks, Style style) {
		this.outline = outline;
		this.pad = pad;
		this.marks = List.copyOf(marks);
		this.style = style;
		this.level = Minecraft.getInstance().level;
		this.age = 0;
	}

	void clear() {
		outline = null;
		pad = null;
		marks = List.of();
	}

	void tick(Minecraft mc) {
		if (outline == null && marks.isEmpty()) {
			return;
		}
		if (mc.level == null || mc.level != level || ++age > LIFETIME_TICKS) {
			clear();
			return;
		}
		if (age % EVERY_TICKS != 0) {
			return;
		}
		ClientLevel l = mc.level;
		if (outline != null) {
			edges(l, outline, style == Style.OK ? OK : style == Style.BAD ? BAD : SITE);
		}
		if (pad != null) {
			edges(l, pad, PAD);
		}
		for (int[] m : marks) {
			l.addAlwaysVisibleParticle(MARK, m[0] + 0.5, m[1] + 0.5, m[2] + 0.5, 0, 0, 0);
		}
	}

	/** Particles along the 12 edges of the box (block-corner coordinates). */
	private static void edges(ClientLevel l, Box b, ParticleOptions p) {
		double x0 = b.minX();
		double y0 = b.minY();
		double z0 = b.minZ();
		double x1 = b.maxX() + 1;
		double y1 = b.maxY() + 1;
		double z1 = b.maxZ() + 1;
		for (double x = x0; x <= x1; x += 1) {
			dot(l, p, x, y0, z0);
			dot(l, p, x, y0, z1);
			dot(l, p, x, y1, z0);
			dot(l, p, x, y1, z1);
		}
		for (double z = z0; z <= z1; z += 1) {
			dot(l, p, x0, y0, z);
			dot(l, p, x1, y0, z);
			dot(l, p, x0, y1, z);
			dot(l, p, x1, y1, z);
		}
		for (double y = y0; y <= y1; y += 1) {
			dot(l, p, x0, y, z0);
			dot(l, p, x1, y, z0);
			dot(l, p, x0, y, z1);
			dot(l, p, x1, y, z1);
		}
	}

	private static void dot(ClientLevel l, ParticleOptions p, double x, double y, double z) {
		l.addAlwaysVisibleParticle(p, x, y, z, 0, 0, 0);
	}
}
