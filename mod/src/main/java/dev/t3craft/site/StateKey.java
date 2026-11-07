package dev.t3craft.site;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Canonical text form of a block state, independent of any registry: {@code minecraft:oak_stairs[facing=north,half=bottom]}
 * with properties sorted by name ({@code minecraft:air} when there are none). Equal states give equal
 * keys, so the site code compares cells by string. Converts to and from the vanilla NBT block-state
 * form used in the site files ({@code {Name:"…",Properties:{…}}}). That is the pre-26 vanilla layout,
 * not what {@code NbtUtils} reads or writes now: never pass these tags to {@code NbtUtils}.
 */
public final class StateKey {
	public static final String AIR = "minecraft:air";

	private StateKey() {
	}

	public static String of(String name, Map<String, String> properties) {
		if (properties == null || properties.isEmpty()) {
			return name;
		}
		StringBuilder sb = new StringBuilder(name).append('[');
		boolean first = true;
		for (Map.Entry<String, String> e : new TreeMap<>(properties).entrySet()) {
			if (!first) {
				sb.append(',');
			}
			sb.append(e.getKey()).append('=').append(e.getValue());
			first = false;
		}
		return sb.append(']').toString();
	}

	public static String name(String key) {
		int i = key.indexOf('[');
		return i < 0 ? key : key.substring(0, i);
	}

	public static Map<String, String> properties(String key) {
		Map<String, String> out = new TreeMap<>();
		int i = key.indexOf('[');
		if (i < 0 || !key.endsWith("]")) {
			return out;
		}
		String body = key.substring(i + 1, key.length() - 1);
		if (body.isEmpty()) {
			return out;
		}
		for (String kv : body.split(",")) {
			int eq = kv.indexOf('=');
			if (eq <= 0) {
				throw new IllegalArgumentException("bad state key " + key);
			}
			out.put(kv.substring(0, eq), kv.substring(eq + 1));
		}
		return out;
	}

	/** Vanilla NBT block-state compound → canonical key. */
	public static String fromTag(CompoundTag tag) {
		String name = tag.getStringOr("Name", AIR);
		Map<String, String> props = new TreeMap<>();
		CompoundTag p = tag.getCompoundOrEmpty("Properties");
		for (String k : p.keySet()) {
			Tag v = p.get(k);
			props.put(k, v == null ? "" : v.asString().orElse(v.toString()));
		}
		return of(name, props);
	}

	/** Canonical key → vanilla NBT block-state compound. */
	public static CompoundTag toTag(String key) {
		CompoundTag tag = new CompoundTag();
		tag.putString("Name", name(key));
		Map<String, String> props = properties(key);
		if (!props.isEmpty()) {
			CompoundTag p = new CompoundTag();
			props.forEach(p::putString);
			tag.put("Properties", p);
		}
		return tag;
	}

	public static boolean isAir(String key) {
		return AIR.equals(key) || "minecraft:cave_air".equals(key) || "minecraft:void_air".equals(key);
	}
}
