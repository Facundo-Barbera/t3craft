package dev.t3craft.site;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

/**
 * Order-independent text form of NBT (compound keys sorted), used to compare block entities and to
 * checksum snapshots: {@code CompoundTag.toString()} follows hash-map order, which is not stable
 * across a write/read cycle.
 */
public final class Canon {
	private Canon() {
	}

	public static String of(@Nullable Tag tag) {
		StringBuilder sb = new StringBuilder();
		write(tag, sb, false);
		return sb.toString();
	}

	/** Block-entity identity: same content regardless of the stored x/y/z. */
	public static boolean sameBlockEntity(@Nullable CompoundTag a, @Nullable CompoundTag b) {
		if (a == null || b == null) {
			return a == b;
		}
		StringBuilder sa = new StringBuilder();
		StringBuilder sb = new StringBuilder();
		write(a, sa, true);
		write(b, sb, true);
		return sa.toString().contentEquals(sb);
	}

	private static void write(@Nullable Tag tag, StringBuilder sb, boolean dropPos) {
		if (tag == null) {
			sb.append("null");
			return;
		}
		if (tag instanceof CompoundTag c) {
			List<String> keys = new ArrayList<>(c.keySet());
			keys.sort(null);
			sb.append('{');
			for (String k : keys) {
				if (dropPos && (k.equals("x") || k.equals("y") || k.equals("z"))) {
					continue;
				}
				sb.append(quote(k)).append(':');
				write(c.get(k), sb, false);
				sb.append(',');
			}
			sb.append('}');
			return;
		}
		if (tag instanceof CollectionTag list) {
			sb.append('[').append(tag.getId()).append(';');
			for (int i = 0; i < list.size(); i++) {
				write(list.get(i), sb, false);
				sb.append(',');
			}
			sb.append(']');
			return;
		}
		sb.append(tag.getId()).append(':').append(tag);
	}

	private static String quote(String s) {
		return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
	}

	public static String sha256(String text) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public static boolean sameCell(String stateA, @Nullable CompoundTag beA, String stateB, @Nullable CompoundTag beB) {
		return Objects.equals(stateA, stateB) && sameBlockEntity(beA, beB);
	}
}
