package dev.t3craft.client.chat.compose;

import dev.t3craft.T3Craft;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLClipboard;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLStdinc;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

/**
 * Where composer images come from, on the client: the platform's file dialog, the clipboard, or a
 * path typed by hand.
 *
 * <p>Minecraft 26.x runs on SDL3 (LWJGL {@code lwjgl-sdl}); LWJGL's TinyFileDialogs is not shipped
 * any more, so the file picker is {@code SDL_ShowOpenFileDialog} (native portal / GTK / Win32 /
 * Cocoa dialog, asynchronous: the callback may come on another thread, results are handed back on
 * the client thread). When SDL cannot open one ({@link #pickFile} returns false) the composer falls
 * back to a path field.
 *
 * <p>Clipboard images: {@code SDL_GetClipboardData} for {@code image/png|jpeg|gif|webp}, then
 * {@code text/uri-list} (a file copied in a file manager), then AWT when the JVM is not headless
 * (Minecraft normally starts headless, so this is the last resort). Text paste stays in
 * {@link ComposerText}.
 */
public final class ImageSources {
	/** An image read from somewhere: its bytes and a name to show. */
	public record Picked(byte[] bytes, String name) {
	}

	private static final String[] CLIPBOARD_TYPES = {"image/png", "image/jpeg", "image/webp", "image/gif"};
	/** The dialog's native callback; kept reachable until SDL calls it (one dialog at a time). */
	private static @Nullable SDL_DialogFileCallback pendingCallback;
	private static SDL_DialogFileFilter.@Nullable Buffer pendingFilters;
	private static @Nullable List<ByteBuffer> pendingStrings;

	private ImageSources() {
	}

	// ---- files --------------------------------------------------------------------------------

	/** Reads an image file, refusing anything above T3's per-image limit before loading it. */
	public static Picked read(Path p) throws IOException {
		long size = Files.size(p);
		if (size > ImageAttachment.MAX_IMAGE_BYTES) {
			throw new IOException("Image too large (" + ImageAttachment.mb(size) + "): T3 takes up to 10 MB per image.");
		}
		Path name = p.getFileName();
		return new Picked(Files.readAllBytes(p), name == null ? "image" : name.toString());
	}

	/** A typed path: quotes, {@code file://} and {@code ~} accepted. */
	public static Path parsePath(String raw) {
		String s = raw.strip();
		if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))) {
			s = s.substring(1, s.length() - 1);
		}
		if (s.startsWith("file://")) {
			return Path.of(URI.create(s));
		}
		if (s.equals("~") || s.startsWith("~/")) {
			s = System.getProperty("user.home") + s.substring(1);
		}
		return Path.of(s);
	}

	/**
	 * Opens the platform's "open file" dialog for images. {@code onPicked} gets the chosen paths
	 * (empty when cancelled), {@code onError} a reason; both on the client thread. Returns false
	 * when no dialog could be opened (the caller offers a path field instead).
	 */
	public static synchronized boolean pickFile(Consumer<List<Path>> onPicked, Consumer<String> onError) {
		if (pendingCallback != null) {
			return true; // one already open
		}
		try {
			List<ByteBuffer> strings = new ArrayList<>();
			SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.calloc(1);
			ByteBuffer name = MemoryUtil.memUTF8("Images (PNG, JPEG, GIF, WebP)");
			ByteBuffer pattern = MemoryUtil.memUTF8("png;jpg;jpeg;gif;webp");
			strings.add(name);
			strings.add(pattern);
			filters.get(0).set(name, pattern);
			SDL_DialogFileCallback cb = SDL_DialogFileCallback.create((userdata, filelist, filter) -> {
				List<Path> out = new ArrayList<>();
				String err = null;
				if (filelist == MemoryUtil.NULL) {
					err = SDLError.SDL_GetError();
				} else {
					for (int i = 0; ; i++) {
						long s = MemoryUtil.memGetAddress(filelist + (long) i * Long.BYTES);
						if (s == MemoryUtil.NULL) {
							break;
						}
						String f = MemoryUtil.memUTF8(s);
						if (f != null && !f.isEmpty()) {
							out.add(Path.of(f));
						}
					}
				}
				String e = err;
				Minecraft.getInstance().execute(() -> {
					releaseDialog();
					if (e != null) {
						onError.accept("Could not open the file dialog: " + (e.isBlank() ? "unknown error" : e));
					} else {
						onPicked.accept(out);
					}
				});
			});
			pendingCallback = cb;
			pendingFilters = filters;
			pendingStrings = strings;
			SDLDialog.SDL_ShowOpenFileDialog(cb, MemoryUtil.NULL, MemoryUtil.NULL, filters, (CharSequence) null, true);
			return true;
		} catch (Throwable t) { // no SDL dialog support (old natives, headless): fall back to a path field
			T3Craft.LOGGER.info("[t3craft] no native file dialog ({}): composer falls back to a path field", t.toString());
			releaseDialog();
			return false;
		}
	}

	private static synchronized void releaseDialog() {
		if (pendingCallback != null) {
			pendingCallback.free();
			pendingCallback = null;
		}
		if (pendingFilters != null) {
			pendingFilters.free();
			pendingFilters = null;
		}
		if (pendingStrings != null) {
			pendingStrings.forEach(MemoryUtil::memFree);
			pendingStrings = null;
		}
	}

	// ---- clipboard ----------------------------------------------------------------------------

	/**
	 * The images on the clipboard, or an empty list when it holds none (then Ctrl+V pastes text).
	 * Client thread.
	 */
	public static List<Picked> clipboardImages() {
		try {
			for (String type : CLIPBOARD_TYPES) {
				if (SDLClipboard.SDL_HasClipboardData(type)) {
					byte[] b = sdlData(type);
					if (b != null && b.length > 0) {
						return List.of(new Picked(b, "pasted-image." + ImageAttachment.extension(type)));
					}
				}
			}
			if (SDLClipboard.SDL_HasClipboardData("text/uri-list")) {
				byte[] b = sdlData("text/uri-list");
				List<Picked> files = b == null ? List.of() : uriListImages(new String(b, java.nio.charset.StandardCharsets.UTF_8));
				if (!files.isEmpty()) {
					return files;
				}
			}
		} catch (Throwable t) {
			T3Craft.LOGGER.debug("[t3craft] SDL clipboard image read failed: {}", t.toString());
		}
		return awtImage();
	}

	private static byte @Nullable [] sdlData(String type) {
		ByteBuffer buf = SDLClipboard.SDL_GetClipboardData(type);
		if (buf == null) {
			return null;
		}
		try {
			byte[] out = new byte[buf.remaining()];
			buf.get(out);
			return out;
		} finally {
			SDLStdinc.SDL_free(buf);
		}
	}

	/** Image files named by a {@code text/uri-list} (lines of {@code file://} URIs, {@code #} comments). */
	static List<Picked> uriListImages(String list) {
		List<Picked> out = new ArrayList<>();
		for (String line : list.split("\r?\n")) {
			String l = line.strip();
			if (l.isEmpty() || l.startsWith("#") || !l.startsWith("file:")) {
				continue;
			}
			String lower = l.toLowerCase(Locale.ROOT);
			if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp"))) {
				continue;
			}
			try {
				out.add(read(Path.of(URI.create(l))));
			} catch (IOException | RuntimeException ignored) {
				// not readable: skipped
			}
		}
		return out;
	}

	/** AWT fallback, only when the JVM is not headless. */
	private static List<Picked> awtImage() {
		try {
			// fix/paste-crash: AWT's clipboard inside the SDL window on Windows can take the whole JVM down
			// (a second native message loop); only used where it is known to be safe, or when asked for
			boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
			if (windows && !Boolean.getBoolean("t3craft.awtClipboard")) {
				return List.of();
			}
			if (java.awt.GraphicsEnvironment.isHeadless()) {
				return List.of();
			}
			java.awt.datatransfer.Clipboard cb = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
			if (!cb.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.imageFlavor)) {
				return List.of();
			}
			Object o = cb.getData(java.awt.datatransfer.DataFlavor.imageFlavor);
			if (!(o instanceof java.awt.Image img)) {
				return List.of();
			}
			java.awt.image.BufferedImage bi;
			if (img instanceof java.awt.image.BufferedImage b) {
				bi = b;
			} else {
				bi = new java.awt.image.BufferedImage(img.getWidth(null), img.getHeight(null), java.awt.image.BufferedImage.TYPE_INT_ARGB);
				java.awt.Graphics2D g = bi.createGraphics();
				g.drawImage(img, 0, 0, null);
				g.dispose();
			}
			ByteArrayOutputStream png = new ByteArrayOutputStream();
			javax.imageio.ImageIO.write(bi, "png", png);
			return List.of(new Picked(png.toByteArray(), "pasted-image.png"));
		} catch (Throwable t) {
			return List.of();
		}
	}
}
