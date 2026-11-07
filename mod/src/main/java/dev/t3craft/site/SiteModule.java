package dev.t3craft.site;

import java.util.Objects;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;

/**
 * One module of the HQ as the site records it: <b>what</b> was built where, never raw blocks, so the
 * same modules can be rebuilt after a move. The HQ design ({@code feat/hq-design}) owns the meaning
 * of {@code spec}; the site only stores it and hands it back to the {@link ModuleDesigner}.
 *
 * @param id unique within a site, e.g. {@code base} or {@code project-t3craft}; {@code [a-z0-9_.-]{1,48}}
 * @param spec opaque design spec (module kind, project, desks…), at most 1024 chars
 * @param rect footprint, plan-relative (must lie inside the site's envelope)
 */
public record SiteModule(String id, String spec, Rect rect) {
	private static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,48}");

	public SiteModule {
		Objects.requireNonNull(rect, "rect");
		if (id == null || !ID.matcher(id).matches()) {
			throw new IllegalArgumentException("bad module id " + id);
		}
		if (spec == null || spec.length() > 1024) {
			throw new IllegalArgumentException("module spec missing or too long");
		}
	}

	public SiteModule withRect(Rect r) {
		return new SiteModule(id, spec, r);
	}

	public CompoundTag toTag() {
		CompoundTag t = new CompoundTag();
		t.putString("id", id);
		t.putString("spec", spec);
		t.putIntArray("rect", rect.toArray());
		return t;
	}

	public static SiteModule fromTag(CompoundTag t) {
		return new SiteModule(t.getStringOr("id", ""), t.getStringOr("spec", ""), Rect.fromArray(t.getIntArray("rect").orElse(null)));
	}
}
