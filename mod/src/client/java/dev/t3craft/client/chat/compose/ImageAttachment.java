package dev.t3craft.client.chat.compose;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * An image waiting in the composer, and T3's limits for it. Pure Java.
 *
 * <p>Mirrors {@code bridge/vendor/t3-contracts/src/chatAttachment.ts}: only
 * {@code image/png|jpeg|gif|webp} ({@code PROVIDER_SEND_TURN_SUPPORTED_IMAGE_MIME_TYPES}), at most
 * {@value #MAX_IMAGE_BYTES} bytes each ({@code PROVIDER_SEND_TURN_MAX_IMAGE_BYTES}), {@value #MAX_TOTAL_BYTES}
 * in all ({@code PROVIDER_SEND_TURN_MAX_TOTAL_IMAGE_BYTES}), {@value #MAX_ATTACHMENTS} per message, a name of
 * at most 255 characters and a data URL of at most {@value #MAX_DATA_URL_CHARS} characters. The type is
 * decided by the bytes (magic numbers), not by the file name, because T3 checks that the data URL's
 * type equals {@code mimeType} ({@code apps/server/src/ws.ts persistChatAttachments}).
 *
 * <p>Wire shapes: {@link #upload()} is an {@code UploadChatImageAttachment}
 * ({@code {type:"image", name, mimeType, sizeBytes, dataUrl}}) for {@code assets.persistChatAttachments};
 * T3 answers with {@code ChatAttachment}s ({@code {type:"image", id, name, mimeType, sizeBytes}}) that go
 * into {@code message.dispatch.attachments}.
 *
 * @param localId composer-local id (thumbnail texture, the ✕ button); never sent
 * @param name    file name shown and sent
 * @param mime    sniffed type, one of {@link #MIME_TYPES}
 * @param bytes   the file
 */
public record ImageAttachment(String localId, String name, String mime, byte[] bytes) {
	public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
	public static final long MAX_TOTAL_BYTES = 80L * 1024 * 1024;
	public static final int MAX_ATTACHMENTS = 100;
	public static final int MAX_DATA_URL_CHARS = 14_000_000;
	public static final int MAX_NAME_CHARS = 255;
	public static final List<String> MIME_TYPES = List.of("image/gif", "image/jpeg", "image/png", "image/webp");

	public ImageAttachment {
		if (localId == null || name == null || mime == null || bytes == null) {
			throw new IllegalArgumentException("localId, name, mime and bytes are required");
		}
	}

	public long size() {
		return bytes.length;
	}

	/** {@code data:<mime>;base64,<bytes>}. */
	public String dataUrl() {
		return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
	}

	/** The {@code UploadChatImageAttachment} for {@code assets.persistChatAttachments}. */
	public JsonObject upload() {
		JsonObject o = new JsonObject();
		o.addProperty("type", "image");
		o.addProperty("name", name);
		o.addProperty("mimeType", mime);
		o.addProperty("sizeBytes", bytes.length);
		o.addProperty("dataUrl", dataUrl());
		return o;
	}

	/** {@code PersistChatAttachmentsInput}. */
	public static JsonObject persistInput(String threadId, String messageId, List<ImageAttachment> images) {
		JsonObject o = new JsonObject();
		o.addProperty("threadId", threadId);
		o.addProperty("messageId", messageId);
		JsonArray a = new JsonArray();
		images.forEach(i -> a.add(i.upload()));
		o.add("attachments", a);
		return o;
	}

	/** Type from the first bytes: PNG, JPEG, GIF or WebP; null for anything else. */
	public static @Nullable String sniff(byte[] b) {
		if (b == null || b.length < 12) {
			return null;
		}
		if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
			return "image/png";
		}
		if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
			return "image/jpeg";
		}
		if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
			return "image/gif";
		}
		if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
			return "image/webp";
		}
		return null;
	}

	/** File extension for a type ({@code png}…), for names of pasted images. */
	public static String extension(String mime) {
		return switch (mime) {
			case "image/jpeg" -> "jpg";
			case "image/gif" -> "gif";
			case "image/webp" -> "webp";
			default -> "png";
		};
	}

	/** A name T3 accepts: trimmed, never empty, at most {@value #MAX_NAME_CHARS} characters (extension kept). */
	public static String cleanName(@Nullable String raw, String mime) {
		String n = raw == null ? "" : raw.strip().replaceAll("[\\p{Cntrl}]", "");
		if (n.isEmpty()) {
			n = "image." + extension(mime);
		}
		if (n.length() > MAX_NAME_CHARS) {
			int dot = n.lastIndexOf('.');
			String ext = dot > 0 && n.length() - dot <= 10 ? n.substring(dot) : "";
			n = n.substring(0, MAX_NAME_CHARS - ext.length()) + ext;
		}
		return n;
	}

	/**
	 * Why {@code bytes} cannot be attached next to {@code current}, or null when it can. The
	 * player-facing text names the limit.
	 */
	public static @Nullable String rejection(byte[] bytes, @Nullable String mime, List<ImageAttachment> current) {
		if (mime == null) {
			return "Not a supported image: T3 takes PNG, JPEG, GIF or WebP.";
		}
		if (!MIME_TYPES.contains(mime.toLowerCase(Locale.ROOT))) {
			return "Unsupported image type " + mime + ": T3 takes PNG, JPEG, GIF or WebP.";
		}
		if (bytes.length == 0) {
			return "The image is empty.";
		}
		if (bytes.length > MAX_IMAGE_BYTES) {
			return "Image too large (" + mb(bytes.length) + "): T3 takes up to 10 MB per image.";
		}
		if (current.size() >= MAX_ATTACHMENTS) {
			return "Too many images: up to " + MAX_ATTACHMENTS + " per message.";
		}
		long total = bytes.length;
		for (ImageAttachment a : current) {
			total += a.size();
		}
		if (total > MAX_TOTAL_BYTES) {
			return "Images can total up to 80 MB per message.";
		}
		// base64 = 4 chars per 3 bytes, plus the "data:<mime>;base64," prefix
		long chars = 4L * ((bytes.length + 2) / 3) + 30;
		if (chars > MAX_DATA_URL_CHARS) {
			return "Image too large to upload (" + mb(bytes.length) + ").";
		}
		return null;
	}

	/** "3.4 MB" / "120 KB". */
	public static String mb(long bytes) {
		if (bytes < 1024 * 1024) {
			return Math.max(1, bytes / 1024) + " KB";
		}
		return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ImageAttachment a && a.localId.equals(localId);
	}

	@Override
	public int hashCode() {
		return localId.hashCode();
	}

	@Override
	public String toString() {
		return "ImageAttachment[" + localId + " " + name + " " + mime + " " + bytes.length + "B]";
	}
}
