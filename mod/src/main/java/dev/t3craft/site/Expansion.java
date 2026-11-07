package dev.t3craft.site;

import net.minecraft.nbt.CompoundTag;

/**
 * One entry of a site's expansion stack: a module and the box snapshotted right before it was
 * leveled and built. {@code key} names its files ({@code snapshots/<key>.nbt}, {@code plans/<key>.nbt}).
 * Undo pops the newest entry and restores its snapshot; repeating it walks back to the original
 * terrain.
 */
public record Expansion(String key, SiteModule module, Box box) {
	public CompoundTag toTag() {
		CompoundTag t = new CompoundTag();
		t.putString("key", key);
		t.put("module", module.toTag());
		t.putIntArray("box", box.toArray());
		return t;
	}

	public static Expansion fromTag(CompoundTag t) {
		return new Expansion(t.getStringOr("key", ""), SiteModule.fromTag(t.getCompoundOrEmpty("module")), Box.fromArray(t.getIntArray("box").orElse(null)));
	}
}
