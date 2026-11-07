// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.t3craft.blocks.Binding;
import dev.t3craft.blocks.MonitorBlock;
import dev.t3craft.blocks.MonitorBlockEntity;
import dev.t3craft.client.workstation.StationScreen;
import dev.t3craft.debug.PerfStats;
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
 * <p>Per-frame cost ({@link RenderLod#monitor}): nothing from behind the screen or beyond
 * {@value RenderLod#MONITOR_VIEW} blocks ({@link #shouldRender}); beyond {@value RenderLod#MONITOR_TEXT}
 * blocks only the status background (the text would be unreadable).
 *
 * <p>The face transform ({@link #toFace}) is adapted from AgentCraft's {@code StationRenderer}.
 */
public final class MonitorRenderer implements BlockEntityRenderer<MonitorBlockEntity, MonitorRenderer.State> {
	/** Screen pixels per block (one character ≈ 6 px; 22 characters fit inside the bezel). */
	static final float PPB = 160f;
	static final float BEZEL = 10f;
	static final int LINE_HEIGHT = 10;
	private static final int MAX_CACHED = 512;

	public static final class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		List<FormattedCharSequence> lines = List.of();
		int[] colors = new int[0];
		int background;
		boolean text = true;
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
	public boolean shouldRender(MonitorBlockEntity be, Vec3 camera) {
		boolean draw = detail(be, camera) != RenderLod.MonitorDetail.NONE;
		if (!draw) {
			PerfStats.count(PerfStats.MONITOR_CULLED);
		}
		return draw;
	}

	private static RenderLod.MonitorDetail detail(MonitorBlockEntity be, Vec3 camera) {
		BlockState bs = be.getBlockState();
		Direction f = bs.hasProperty(MonitorBlock.FACING) ? bs.getValue(MonitorBlock.FACING) : Direction.NORTH;
		var p = be.getBlockPos();
		return RenderLod.monitor(p.getX() + 0.5 - camera.x, p.getY() + 0.5 - camera.y, p.getZ() + 0.5 - camera.z, f.getStepX(), f.getStepZ());
	}

	@Override
	public void extractRenderState(MonitorBlockEntity be, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderState.extractBase(be, state, breakProgress);
		BlockState bs = be.getBlockState();
		state.facing = bs.hasProperty(MonitorBlock.FACING) ? bs.getValue(MonitorBlock.FACING) : Direction.NORTH;
		state.text = detail(be, cameraPosition) == RenderLod.MonitorDetail.FULL;
		PerfStats.count(PerfStats.MONITOR_DRAWN);
		if (!state.text) {
			PerfStats.count(PerfStats.MONITOR_LOD);
		}
		Binding b = client.resolve(be.getBlockPos(), be.binding());
		MonitorScreens.Shown shown = client.screens().show(b);
		Glyphs g = glyphs.get(shown.id());
		if (g == null || g.version() != shown.version()) {
			if (glyphs.size() > MAX_CACHED) {
				glyphs.clear();
			}
			g = glyphs(shown);
			glyphs.put(shown.id(), g);
			PerfStats.count(PerfStats.MONITOR_GLYPHS);
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
		toFace(poseStack, s.facing, -0.003f, PPB);
		collector.order(0).submitTextBackground(poseStack, BEZEL, BEZEL, PPB - BEZEL, PPB - BEZEL, s.background, Font.DisplayMode.NORMAL, light);
		if (!s.text) {
			poseStack.popPose();
			return; // far: background only (LOD)
		}
		float y = (PPB - s.lines.size() * LINE_HEIGHT) / 2f + 1;
		for (int i = 0; i < s.lines.size(); i++) {
			collector.order(1).submitText(poseStack, BEZEL + 4, y, s.lines.get(i), false, Font.DisplayMode.POLYGON_OFFSET, light, s.colors[i], 0, 0);
			y += LINE_HEIGHT;
		}
		poseStack.popPose();
	}

	@Override
	public int getViewDistance() {
		return (int) RenderLod.MONITOR_VIEW;
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
