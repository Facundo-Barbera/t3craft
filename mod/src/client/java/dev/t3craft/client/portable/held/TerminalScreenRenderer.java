package dev.t3craft.client.portable.held;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import dev.t3craft.T3Craft;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Draws the held screen texture over the item's screen. This is a vanilla <i>special model
 * renderer</i> (type {@code t3craft:held_terminal_screen}) used by
 * {@code assets/t3craft/items/t3_terminal.json} only in the first-person hand contexts, layered on
 * top of the normal sprite with {@code minecraft:composite}. No mixin: the type is added to the
 * public {@link SpecialModelRenderers#ID_MAPPER} (Fabric has no held-item render event; this is the
 * data-driven hook vanilla uses for shields, banners, etc.). Positioning and size in the hand come
 * from the display transforms of {@code models/item/t3_terminal_held.json} (tunable with a resource
 * pack and F3+T, no restart).
 *
 * <p>Geometry is in the item model's 0..1 space (the same local transform the sprite's quads get):
 * the sprite's screen is pixels x 5..10, y 3..11, so the quad spans x 5/16..11/16 and
 * y 4/16..13/16, a hair in front of each face of the 1/16-thick generated sprite. Both faces are
 * drawn (the back one with U flipped) so the text reads from whichever side faces the camera.
 * Full-bright: it is a display. Draws nothing until {@link HeldScreen} has a texture ready.
 */
final class TerminalScreenRenderer implements NoDataSpecialModelRenderer {
	static final Identifier TYPE = Identifier.fromNamespaceAndPath(T3Craft.MOD_ID, "held_terminal_screen");

	static final float X0 = 5 / 16f;
	static final float X1 = 11 / 16f;
	static final float Y0 = 4 / 16f;
	static final float Y1 = 13 / 16f;
	static final float Z_FRONT = 8.5f / 16f + 0.002f;
	static final float Z_BACK = 7.5f / 16f - 0.002f;

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean glint, int outlineColor) {
		if (!HeldScreen.visible()) {
			return;
		}
		collector.submitCustomGeometry(pose, RenderTypes.text(HeldScreenTexture.ID), TerminalScreenRenderer::quads);
	}

	private static void quads(PoseStack.Pose p, VertexConsumer vc) {
		int l = LightCoordsUtil.FULL_BRIGHT;
		// front (+Z), counter-clockwise seen from +Z
		vc.addVertex(p, X0, Y0, Z_FRONT).setColor(-1).setUv(0, 1).setLight(l);
		vc.addVertex(p, X1, Y0, Z_FRONT).setColor(-1).setUv(1, 1).setLight(l);
		vc.addVertex(p, X1, Y1, Z_FRONT).setColor(-1).setUv(1, 0).setLight(l);
		vc.addVertex(p, X0, Y1, Z_FRONT).setColor(-1).setUv(0, 0).setLight(l);
		// back (-Z), counter-clockwise seen from -Z; U flipped so it is not mirrored
		vc.addVertex(p, X1, Y0, Z_BACK).setColor(-1).setUv(0, 1).setLight(l);
		vc.addVertex(p, X0, Y0, Z_BACK).setColor(-1).setUv(1, 1).setLight(l);
		vc.addVertex(p, X0, Y1, Z_BACK).setColor(-1).setUv(1, 0).setLight(l);
		vc.addVertex(p, X1, Y1, Z_BACK).setColor(-1).setUv(0, 0).setLight(l);
	}

	@Override
	public void getExtents(Consumer<Vector3fc> out) {
		for (float x : new float[] {X0, X1}) {
			for (float y : new float[] {Y0, Y1}) {
				out.accept(new Vector3f(x, y, Z_FRONT));
				out.accept(new Vector3f(x, y, Z_BACK));
			}
		}
	}

	/** JSON form: {@code {"type": "t3craft:held_terminal_screen"}} (no fields). */
	record Unbaked() implements NoDataSpecialModelRenderer.Unbaked {
		static final MapCodec<Unbaked> CODEC = MapCodec.unit(new Unbaked());

		@Override
		public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
			return new TerminalScreenRenderer();
		}

		@Override
		public MapCodec<Unbaked> type() {
			return CODEC;
		}
	}

	/**
	 * Adds the type to vanilla's registry of special renderers. Must run before the first resource
	 * load (client init does). Idempotent: a second call (hot-swap re-init) is ignored.
	 */
	static void registerType() {
		try {
			SpecialModelRenderers.ID_MAPPER.put(TYPE, Unbaked.CODEC);
		} catch (RuntimeException alreadyRegistered) {
			T3Craft.LOGGER.debug("[portable] {} already registered", TYPE);
		}
	}
}
