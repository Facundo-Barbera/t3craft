package dev.t3craft.hq.pack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * feat/hq-layout-packs: block states as text, the command syntax
 * ({@code minecraft:spruce_stairs[facing=north,half=bottom]}; the namespace defaults to
 * {@code minecraft}). Reads the built-in block registry only (no world, no holder lookup), so it works
 * in game, in tests and in hqRender. Unknown blocks, properties or values throw
 * {@link IllegalArgumentException} with a readable message (never silently air).
 */
public final class BlockStateText {
	private BlockStateText() {
	}

	/** Parses {@code [ns:]block[k=v,...]}. */
	public static BlockState parse(String text) {
		String t = text.trim();
		int open = t.indexOf('[');
		String name = open < 0 ? t : t.substring(0, open);
		Map<String, String> props = new LinkedHashMap<>();
		if (open >= 0) {
			if (!t.endsWith("]")) {
				throw new IllegalArgumentException("'" + text + "': missing ']'");
			}
			String body = t.substring(open + 1, t.length() - 1).trim();
			if (!body.isEmpty()) {
				for (String kv : body.split(",")) {
					int eq = kv.indexOf('=');
					if (eq <= 0) {
						throw new IllegalArgumentException("'" + text + "': property '" + kv.trim() + "' is not key=value");
					}
					props.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
				}
			}
		}
		Block block = block(name);
		BlockState s = block.defaultBlockState();
		for (Map.Entry<String, String> e : props.entrySet()) {
			Property<?> p = block.getStateDefinition().getProperty(e.getKey());
			if (p == null) {
				throw new IllegalArgumentException("'" + text + "': " + name + " has no property '" + e.getKey() + "'");
			}
			Optional<BlockState> v = with(s, p, e.getValue());
			if (v.isEmpty()) {
				throw new IllegalArgumentException("'" + text + "': '" + e.getValue() + "' is not a value of " + e.getKey());
			}
			s = v.get();
		}
		return s;
	}

	/** The block named {@code name} ({@code [ns:]id}). */
	public static Block block(String name) {
		String n = name.trim();
		Identifier id = Identifier.tryParse(n.contains(":") ? n : "minecraft:" + n);
		if (id == null) {
			throw new IllegalArgumentException("'" + name + "' is not a block id");
		}
		Block b = BuiltInRegistries.BLOCK.getValue(id);
		if (b == null || !BuiltInRegistries.BLOCK.getKey(b).equals(id) || (b == Blocks.AIR && !isAirId(id))) {
			throw new IllegalArgumentException("unknown block '" + name + "'");
		}
		return b;
	}

	private static boolean isAirId(Identifier id) {
		return id.toString().equals("minecraft:air");
	}

	/** {@code s} with property {@code p} set from its text value, if valid. */
	public static <T extends Comparable<T>> Optional<BlockState> with(BlockState s, Property<T> p, String value) {
		if (!s.hasProperty(p)) {
			return Optional.empty();
		}
		return p.getValue(value).map(v -> s.setValue(p, v));
	}

	/** Canonical text of a state: id plus every property, sorted by name. */
	public static String format(BlockState s) {
		Map<String, String> props = properties(s);
		String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
		if (props.isEmpty()) {
			return id;
		}
		StringBuilder sb = new StringBuilder(id).append('[');
		boolean first = true;
		for (Map.Entry<String, String> e : props.entrySet()) {
			if (!first) {
				sb.append(',');
			}
			sb.append(e.getKey()).append('=').append(e.getValue());
			first = false;
		}
		return sb.append(']').toString();
	}

	/** Every property of {@code s} by name, as text. */
	public static Map<String, String> properties(BlockState s) {
		Map<String, String> out = new TreeMap<>();
		s.getValues().forEach(v -> out.put(v.property().getName(), v.valueName()));
		return out;
	}

	/**
	 * {@code target} carrying over every property of {@code from} that {@code target}'s block also has
	 * with that value (facing, half, axis, waterlogged...), except the ones named in {@code fixed}
	 * (written explicitly in the pack, they win).
	 */
	public static BlockState carry(BlockState from, BlockState target, java.util.Set<String> fixed) {
		BlockState s = target;
		for (Map.Entry<String, String> e : properties(from).entrySet()) {
			if (fixed.contains(e.getKey())) {
				continue;
			}
			Property<?> p = s.getBlock().getStateDefinition().getProperty(e.getKey());
			if (p != null) {
				s = with(s, p, e.getValue()).orElse(s);
			}
		}
		return s;
	}

	/** Names of the properties written explicitly in {@code text} ({@code a[b=c]} gives {@code b}). */
	public static java.util.Set<String> explicitProperties(String text) {
		java.util.Set<String> out = new java.util.TreeSet<>();
		String t = text.trim();
		int open = t.indexOf('[');
		if (open < 0 || !t.endsWith("]")) {
			return out;
		}
		String body = t.substring(open + 1, t.length() - 1);
		for (String kv : body.split(",")) {
			int eq = kv.indexOf('=');
			if (eq > 0) {
				out.add(kv.substring(0, eq).trim());
			}
		}
		return out;
	}
}
