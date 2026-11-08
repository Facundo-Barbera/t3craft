package dev.t3craft.client.chat.compose;

import com.mojang.blaze3d.platform.NativeImage;
import dev.t3craft.T3Craft;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Thumbnails of the composer's images: decoded off the client thread with stb_image (PNG, JPEG,
 * GIF first frame; WebP has no decoder in the game, so it gets a labelled tile), scaled down to at
 * most {@value #SIZE} px and uploaded as a {@link DynamicTexture}. Released when the image leaves the
 * composer ({@link #retain}). Render thread only, except the decoding.
 */
public final class ImageThumbs {
	public static final int SIZE = 64;

	/** A ready thumbnail: texture and its size in texels. */
	public record Thumb(Identifier id, int w, int h) {
	}

	private final Map<String, Thumb> ready = new HashMap<>();
	private final Set<String> loading = new HashSet<>();
	/** Images that cannot be previewed (WebP, decode failure). */
	private final Set<String> none = new HashSet<>();

	/** The thumbnail of {@code img}, or null while it decodes / when it has none. Starts decoding on first ask. */
	public @Nullable Thumb get(ImageAttachment img) {
		String id = img.localId();
		Thumb t = ready.get(id);
		if (t != null || none.contains(id) || loading.contains(id)) {
			return t;
		}
		if ("image/webp".equals(img.mime())) {
			none.add(id);
			return null;
		}
		loading.add(id);
		byte[] bytes = img.bytes();
		CompletableFuture.supplyAsync(() -> decode(bytes)).whenComplete((px, err) -> Minecraft.getInstance().execute(() -> {
			if (!loading.remove(id)) {
				return; // removed meanwhile
			}
			if (px == null) {
				none.add(id);
				return;
			}
			try {
				NativeImage ni = new NativeImage(px.w, px.h, false);
				for (int y = 0; y < px.h; y++) {
					for (int x = 0; x < px.w; x++) {
						ni.setPixel(x, y, px.argb[y * px.w + x]);
					}
				}
				Identifier tex = Identifier.fromNamespaceAndPath(T3Craft.MOD_ID, "dynamic/composer_thumb_" + id.toLowerCase(Locale.ROOT));
				Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(() -> "t3craft composer thumbnail", ni));
				ready.put(id, new Thumb(tex, px.w, px.h));
			} catch (RuntimeException e) {
				none.add(id);
			}
		}));
		return null;
	}

	/** Frees the thumbnails of images no longer held. */
	public void retain(Set<String> live) {
		ready.entrySet().removeIf(e -> {
			if (live.contains(e.getKey())) {
				return false;
			}
			Minecraft.getInstance().getTextureManager().release(e.getValue().id());
			return true;
		});
		loading.retainAll(live);
		none.retainAll(live);
	}

	private record Pixels(int w, int h, int[] argb) {
	}

	/** stb_image decode + nearest/box scale to {@value #SIZE} px; null when stb cannot read it. */
	private static @Nullable Pixels decode(byte[] bytes) {
		ByteBuffer in = MemoryUtil.memAlloc(bytes.length);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			in.put(bytes).flip();
			IntBuffer w = stack.mallocInt(1);
			IntBuffer h = stack.mallocInt(1);
			IntBuffer c = stack.mallocInt(1);
			ByteBuffer rgba = STBImage.stbi_load_from_memory(in, w, h, c, 4);
			if (rgba == null) {
				return null;
			}
			try {
				int sw = w.get(0);
				int sh = h.get(0);
				if (sw <= 0 || sh <= 0) {
					return null;
				}
				double scale = Math.min(1.0, (double) SIZE / Math.max(sw, sh));
				int tw = Math.max(1, (int) Math.round(sw * scale));
				int th = Math.max(1, (int) Math.round(sh * scale));
				int[] out = new int[tw * th];
				for (int y = 0; y < th; y++) {
					int y0 = y * sh / th;
					int y1 = Math.max(y0 + 1, (y + 1) * sh / th);
					for (int x = 0; x < tw; x++) {
						int x0 = x * sw / tw;
						int x1 = Math.max(x0 + 1, (x + 1) * sw / tw);
						// box filter, sampling at most 4×4 source texels per target texel
						long r = 0, g = 0, b = 0, a = 0, n = 0;
						int stepY = Math.max(1, (y1 - y0) / 4);
						int stepX = Math.max(1, (x1 - x0) / 4);
						for (int yy = y0; yy < y1; yy += stepY) {
							for (int xx = x0; xx < x1; xx += stepX) {
								int i = (yy * sw + xx) * 4;
								r += rgba.get(i) & 0xFF;
								g += rgba.get(i + 1) & 0xFF;
								b += rgba.get(i + 2) & 0xFF;
								a += rgba.get(i + 3) & 0xFF;
								n++;
							}
						}
						out[y * tw + x] = (int) (a / n) << 24 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
					}
				}
				return new Pixels(tw, th, out);
			} finally {
				STBImage.stbi_image_free(rgba);
			}
		} catch (RuntimeException | LinkageError e) {
			return null;
		} finally {
			MemoryUtil.memFree(in);
		}
	}
}
