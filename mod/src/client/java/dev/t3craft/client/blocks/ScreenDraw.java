// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
// Opaque world-UI pipeline (world text snippet without blending, drawn in the solid pass with depth
// writes), flat rectangles on a white texture and GUI-atlas sprites as opaque quads follow
// DisplayDraw (mod/src/client/java/dev/agentcraft/client/monitor/DisplayDraw.java) and WorldUi
// (mod/src/client/java/dev/agentcraft/client/ui/WorldUi.java).
package dev.t3craft.client.blocks;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.t3craft.T3Craft;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Opaque drawing for in-world screens, in "face pixel space" ({@link MonitorRenderer#toFace}).
 *
 * <p>Why opaque: the old monitor drew its background with {@code submitTextBackground} (the
 * translucent text pipeline). Translucent world geometry is drawn after everything opaque, sorted by
 * distance, and does not reliably win against a coplanar block face; an opaque copy of the world
 * text pipeline is drawn in the solid pass with depth writes, so the screen never ghosts or drops
 * out whatever the submit order or the camera distance.
 */
final class ScreenDraw {
	private static final Identifier WHITE = Identifier.fromNamespaceAndPath(T3Craft.MOD_ID, "dynamic/monitor_white");
	private static boolean whiteReady;
	private static @Nullable RenderPipeline solidPipeline;
	private static @Nullable RenderType fill;
	private static @Nullable RenderType sprites;

	private ScreenDraw() {
	}

	private static RenderPipeline pipeline() {
		if (solidPipeline == null) {
			solidPipeline = RenderPipeline.builder(RenderPipelines.WORLD_TEXT_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(T3Craft.MOD_ID, "pipeline/monitor_solid"))
					.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR))
					.build();
		}
		return solidPipeline;
	}

	/** Opaque flat-colour quads. */
	static RenderType fill() {
		if (fill == null) {
			if (!whiteReady) {
				NativeImage img = new NativeImage(4, 4, false);
				img.fillRect(0, 0, 4, 4, 0xFFFFFFFF);
				Minecraft.getInstance().getTextureManager().register(WHITE, new DynamicTexture(() -> "t3craft monitor white", img));
				whiteReady = true;
			}
			fill = RenderType.create("t3craft_monitor_fill",
					RenderSetup.builder(pipeline()).withTexture("Sampler0", WHITE).useLightmap().createRenderSetup());
		}
		return fill;
	}

	/** GUI atlas sprites drawn opaque (texels under 10 % alpha are cut out). */
	static RenderType sprites() {
		if (sprites == null) {
			sprites = RenderType.create("t3craft_monitor_sprites",
					RenderSetup.builder(pipeline()).withTexture("Sampler0", Sheets.GUI_SHEET).useLightmap().createRenderSetup());
		}
		return sprites;
	}

	static @Nullable TextureAtlasSprite sprite(Identifier id) {
		try {
			return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.GUI).getSprite(id);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** One quad, vanilla glyph winding (top-left, bottom-left, bottom-right, top-right). */
	static void quad(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float x1, float y1, float z, float u0, float v0, float u1, float v1,
			int argb, int light) {
		vc.addVertex(pose, x0, y0, z).setUv(u0, v0).setColor(argb).setLight(light);
		vc.addVertex(pose, x0, y1, z).setUv(u0, v1).setColor(argb).setLight(light);
		vc.addVertex(pose, x1, y1, z).setUv(u1, v1).setColor(argb).setLight(light);
		vc.addVertex(pose, x1, y0, z).setUv(u1, v0).setColor(argb).setLight(light);
	}

	static void rect(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float x1, float y1, float z, int argb, int light) {
		if (x1 > x0 && y1 > y0) {
			quad(pose, vc, x0, y0, x1, y1, z, 0.5f, 0.5f, 0.5f, 0.5f, argb, light);
		}
	}

	/** Darkens an opaque colour ({@code f} 0 = black, 1 = unchanged). */
	static int shade(int rgb, float f) {
		int r = Math.round(((rgb >> 16) & 0xFF) * f);
		int g = Math.round(((rgb >> 8) & 0xFF) * f);
		int b = Math.round((rgb & 0xFF) * f);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
