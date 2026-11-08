package dev.t3craft.hq;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.SignText;

/** Block entity data for the HQ's signs (front text, blank back, waxed so players cannot edit them). */
public final class SignTags {
	/** Characters that fit on one sign line at the default font. */
	public static final int LINE = 15;

	private SignTags() {
	}

	public static CompoundTag sign(DyeColor color, boolean glowing, String... lines) {
		List<Component> msgs = new ArrayList<>(SignText.LINES);
		for (int i = 0; i < SignText.LINES; i++) {
			msgs.add(Component.literal(i < lines.length ? clip(lines[i]) : ""));
		}
		SignText front = new SignText(msgs, msgs, color, glowing);
		List<Component> blank = List.of(Component.empty(), Component.empty(), Component.empty(), Component.empty());
		SignText back = new SignText(blank, blank, DyeColor.BLACK, false);
		CompoundTag t = new CompoundTag();
		t.put("front_text", SignText.CODEC.encodeStart(NbtOps.INSTANCE, front).getOrThrow());
		t.put("back_text", SignText.CODEC.encodeStart(NbtOps.INSTANCE, back).getOrThrow());
		t.putBoolean("is_waxed", true);
		return t;
	}

	public static String clip(String s) {
		String t = s.strip();
		return t.length() <= LINE ? t : t.substring(0, LINE - 1) + "…";
	}
}
