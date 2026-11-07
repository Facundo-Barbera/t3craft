package dev.t3craft.client.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.t3craft.blocks.KitPlaqueBlock;
import dev.t3craft.blocks.PlaqueBlockEntity;
import dev.t3craft.blocks.PlaqueText;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a room plaque's text (project, module number, environment) centred on its ink plate,
 * full-bright, the first line in the accent colour. Glyph runs and their widths are cached by
 * {@link PlaqueText#version()} ({@link VersionedCache}): a plaque whose text did not change costs a
 * map lookup per frame plus the draw.
 */
public final class PlaqueRenderer implements BlockEntityRenderer<PlaqueBlockEntity, PlaqueRenderer.State> {
	/** Text units per block (the plate is 14 × 10 px: about 17 characters × 4 lines). */
	static final float PPB = 128f;
	static final int LINE_HEIGHT = 10;
	private static final int MAX_CACHED = 512;

	/** Glyph runs of one plaque text. */
	record Glyphs(List<FormattedCharSequence> lines, float[] x, int[] colors) {
	}

	public static final class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		@Nullable Glyphs glyphs;
	}

	private final VersionedCache<Long, Glyphs> cache = new VersionedCache<>(MAX_CACHED);

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PlaqueBlockEntity be, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderState.extractBase(be, state, breakProgress);
		BlockState bs = be.getBlockState();
		state.facing = bs.hasProperty(KitPlaqueBlock.FACING) ? bs.getValue(KitPlaqueBlock.FACING) : Direction.NORTH;
		PlaqueText t = be.text();
		state.glyphs = t.lines().isEmpty() ? null : cache.get(be.getBlockPos().asLong(), t.version(), () -> glyphs(t));
	}

	private static Glyphs glyphs(PlaqueText t) {
		Font font = Minecraft.getInstance().font;
		List<FormattedCharSequence> lines = new ArrayList<>(t.lines().size());
		float[] x = new float[t.lines().size()];
		int[] colors = new int[t.lines().size()];
		for (int i = 0; i < t.lines().size(); i++) {
			FormattedCharSequence seq = Component.literal(t.lines().get(i)).getVisualOrderText();
			lines.add(seq);
			x[i] = (PPB - font.width(seq)) / 2f;
			colors[i] = 0xFF000000 | (i == 0 ? t.color() : PlaqueText.BODY_COLOR);
		}
		return new Glyphs(List.copyOf(lines), x, colors);
	}

	@Override
	public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		Glyphs g = s.glyphs;
		if (g == null) {
			return;
		}
		int light = LightCoordsUtil.FULL_BRIGHT;
		poseStack.pushPose();
		MonitorRenderer.toFace(poseStack, s.facing, KitPlaqueBlock.FACE_DEPTH - 0.003f, PPB);
		float y = (PPB - g.lines().size() * LINE_HEIGHT) / 2f + 1;
		for (int i = 0; i < g.lines().size(); i++) {
			collector.order(1).submitText(poseStack, g.x()[i], y, g.lines().get(i), false, Font.DisplayMode.POLYGON_OFFSET, light, g.colors()[i], 0, 0);
			y += LINE_HEIGHT;
		}
		poseStack.popPose();
	}

	@Override
	public int getViewDistance() {
		return 24;
	}

	void clear() {
		cache.clear();
	}
}
