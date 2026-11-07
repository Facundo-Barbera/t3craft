// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.t3craft.blocks.Binding;
import dev.t3craft.blocks.MonitorBlock;
import dev.t3craft.blocks.MonitorBlockEntity;
import dev.t3craft.blocks.ScreenExtent;
import dev.t3craft.client.workstation.StationScreen;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Draws a monitor's screen on its front face: the bound thread's title, status (in the status
 * colour, with age) and last activity, over the status background, full-bright. The content comes
 * from {@link MonitorScreens} (state the client already holds); glyph runs are rebuilt only when the
 * screen's version changes, so a steady monitor costs a map lookup per frame plus the draw.
 *
 * <p>Large screens: monitors merged into one screen ({@code MonitorWall}) are drawn <b>once</b>, by
 * the screen's origin (top-left cell, {@link ScreenExtent}), over the whole surface at the density
 * {@link ScreenExtent#pixelsPerBlock()} gives; the other cells draw nothing ({@link #shouldRender}).
 * The whole screen shows the first bound cell's binding.
 *
 * <p>The face transform ({@link #toFace}) is adapted from AgentCraft's {@code StationRenderer}; drawing
 * a multi-block surface once from its origin follows AgentCraft's {@code PanelBlock.origin/extent}.
 */
public final class MonitorRenderer implements BlockEntityRenderer<MonitorBlockEntity, MonitorRenderer.State> {
	/** Screen pixels per block (one character ≈ 6 px; 22 characters fit inside the bezel). */
	static final float PPB = ScreenExtent.PPB_SINGLE;
	static final float BEZEL = 10f;
	/** View distance of a large screen (a lone monitor: {@value #SMALL_VIEW}). */
	static final int LARGE_VIEW = 96;
	static final int SMALL_VIEW = 32;
	/** Width of a character in screen pixels (for the large screens' column count). */
	static final int CHAR_WIDTH = 6;
	static final int LINE_HEIGHT = 10;
	private static final int MAX_CACHED = 512;

	public static final class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		List<FormattedCharSequence> lines = List.of();
		int[] colors = new int[0];
		int background;
		/** The screen's size and density (1×1 at {@link #PPB} for a lone monitor). */
		int width = 1;
		int height = 1;
		float ppb = PPB;
	}

	private record Glyphs(long version, List<FormattedCharSequence> lines, int[] colors, int background) {
	}

	private final BlocksClient client;
	private final Map<String, Glyphs> glyphs = new HashMap<>();

	MonitorRenderer(BlocksClient client) {
		this.client = client;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(MonitorBlockEntity be, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderState.extractBase(be, state, breakProgress);
		BlockState bs = be.getBlockState();
		state.facing = bs.hasProperty(MonitorBlock.FACING) ? bs.getValue(MonitorBlock.FACING) : Direction.NORTH;
		ScreenExtent extent = be.getLevel() == null ? ScreenExtent.single(be.getBlockPos(), state.facing)
				: MonitorBlock.extent(be.getLevel(), be.getBlockPos(), bs);
		state.width = extent.width();
		state.height = extent.height();
		state.ppb = extent.pixelsPerBlock();
		MonitorScreens.Shown shown;
		if (extent.isMulti()) {
			Binding b = client.resolve(extent.origin(), MonitorBlock.screenBinding(be.getLevel(), extent));
			float inset = state.ppb / 16f;
			int cols = (int) ((extent.width() * state.ppb - 2 * inset - 8) / CHAR_WIDTH);
			int rows = (int) ((extent.height() * state.ppb - 2 * inset - 4) / LINE_HEIGHT);
			shown = client.screens().show(b, cols, rows);
		} else {
			shown = client.screens().show(client.resolve(be.getBlockPos(), be.binding()));
		}
		Glyphs g = glyphs.get(shown.id());
		if (g == null || g.version() != shown.version()) {
			if (glyphs.size() > MAX_CACHED) {
				glyphs.clear();
			}
			g = glyphs(shown);
			glyphs.put(shown.id(), g);
		}
		state.lines = g.lines();
		state.colors = g.colors();
		state.background = g.background();
	}

	private static Glyphs glyphs(MonitorScreens.Shown shown) {
		StationScreen s = shown.screen();
		List<FormattedCharSequence> lines = new ArrayList<>(s.lines().size());
		int[] colors = new int[s.lines().size()];
		for (int i = 0; i < s.lines().size(); i++) {
			StationScreen.Line l = s.lines().get(i);
			lines.add(Component.literal(l.text()).getVisualOrderText());
			colors[i] = 0xFF000000 | l.color();
		}
		return new Glyphs(shown.version(), List.copyOf(lines), colors, s.background() | 0xFF000000);
	}

	@Override
	public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		int light = LightCoordsUtil.FULL_BRIGHT;
		poseStack.pushPose();
		toFace(poseStack, s.facing, -0.003f, s.ppb);
		float inset = s.ppb / 16f; // = BEZEL at the lone monitor's density
		float w = s.width * s.ppb;
		float h = s.height * s.ppb;
		collector.order(0).submitTextBackground(poseStack, inset, inset, w - inset, h - inset, s.background, Font.DisplayMode.NORMAL, light);
		// a lone monitor centres its few lines; a large screen reads from the top like a page
		float y = s.width * s.height == 1 ? (h - s.lines.size() * LINE_HEIGHT) / 2f + 1 : inset + 4;
		for (int i = 0; i < s.lines.size(); i++) {
			collector.order(1).submitText(poseStack, inset + 4, y, s.lines.get(i), false, Font.DisplayMode.POLYGON_OFFSET, light, s.colors[i], 0, 0);
			y += LINE_HEIGHT;
		}
		poseStack.popPose();
	}

	@Override
	public int getViewDistance() {
		return LARGE_VIEW;
	}

	/** Only a screen's origin draws (the others are covered by its surface); large screens are seen from further away. */
	@Override
	public boolean shouldRender(MonitorBlockEntity be, Vec3 camera) {
		BlockState bs = be.getBlockState();
		if (!MonitorBlock.isOrigin(bs)) {
			return false;
		}
		boolean large = bs.getValue(MonitorBlock.RIGHT) || bs.getValue(MonitorBlock.DOWN);
		return Vec3.atCenterOf(be.getBlockPos()).closerThan(camera, large ? LARGE_VIEW : SMALL_VIEW);
	}

	/** The origin draws the whole surface, which reaches past its own block: do not cull it by its block alone. */
	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	/** Forget cached glyph runs (hot-swap, level change). */
	void clear() {
		glyphs.clear();
	}

	/**
	 * Moves {@code poseStack} (at the block origin) onto the front face of a north-facing model turned
	 * to {@code facing}, {@code depth} blocks behind it (negative = in front), in units of
	 * {@code 1/pixelsPerBlock} block: x right and y down as seen by the viewer, origin at the face's
	 * top-left corner.
	 */
	static void toFace(PoseStack poseStack, Direction facing, float depth, float pixelsPerBlock) {
		poseStack.translate(0.5f, 0.5f, 0.5f);
		poseStack.rotateDegrees(Axis.YP, -modelRotation(facing));
		poseStack.translate(-0.5f, -0.5f, -0.5f);
		// north-facing model: the viewer stands north (-Z) looking south, so the viewer's right is -X
		poseStack.translate(1f, 1f, depth);
		poseStack.scale(-1f / pixelsPerBlock, -1f / pixelsPerBlock, 1f);
	}

	/** Blockstate y rotation of the north-facing model (N 0, E 90, S 180, W 270). */
	static float modelRotation(Direction facing) {
		return switch (facing) {
			case EAST -> 90f;
			case SOUTH -> 180f;
			case WEST -> 270f;
			default -> 0f;
		};
	}
}
