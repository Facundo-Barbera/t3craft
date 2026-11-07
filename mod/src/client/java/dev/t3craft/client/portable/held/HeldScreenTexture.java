package dev.t3craft.client.portable.held;

import com.mojang.blaze3d.platform.NativeImage;
import dev.t3craft.T3Craft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The held screen's GPU texture: one {@link DynamicTexture} registered under {@link #ID}, created on
 * first upload, rewritten in place on each change, released (GPU memory freed) when the world closes
 * or the feature is turned off. Same pattern as vanilla's map textures (MapTextureManager). Render
 * thread only.
 */
final class HeldScreenTexture {
	static final Identifier ID = Identifier.fromNamespaceAndPath(T3Craft.MOD_ID, "dynamic/held_terminal");

	private @Nullable DynamicTexture texture;

	boolean ready() {
		return texture != null;
	}

	void upload(int[] argb, int w, int h) {
		Minecraft mc = Minecraft.getInstance();
		DynamicTexture t = texture;
		if (t == null) {
			t = new DynamicTexture(() -> "t3craft held terminal", w, h, true);
			mc.getTextureManager().register(ID, t);
			texture = t;
		}
		NativeImage px = t.getPixels();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				px.setPixel(x, y, argb[y * w + x]);
			}
		}
		t.upload();
	}

	void release() {
		if (texture != null) {
			texture = null;
			Minecraft.getInstance().getTextureManager().release(ID); // closes the texture
		}
	}
}
