// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.t3craft.blocks.Binding;
import dev.t3craft.blocks.KitLanternBlock;
import dev.t3craft.blocks.LampBlockEntity;
import dev.t3craft.blocks.LampStatus;
import dev.t3craft.blocks.StatusLampBlock;
import dev.t3craft.debug.PerfStats;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Status lamp / board bulb BER. The lamp's colour and light are its block state; this adds what a
 * block state cannot: an additive glow over the lens of every side that follows {@link LampGlow}
 * (slow breath on "needs you", faster pulse on error, a shimmer while working, steady otherwise).
 * The HQ kit's paper lantern shares the lamp's block entity, so it breathes the same way over its
 * inset paper body.
 * The lamp bound {@code beacon} (hub lantern cap) also glows on top and sends a soft beam up past the
 * lightning rod, so the HQ's aggregate state reads from across the map.
 *
 * <p>Cost per frame: vanilla cannot frustum-cull this renderer (it renders "off screen" for the
 * beacon's beam), so {@link #shouldRender} does it per block with {@link RenderLod#lamp}: plain
 * lamps only within {@value RenderLod#LAMP_VIEW} blocks and not behind the camera, halos only within
 * {@value RenderLod#LAMP_HALO}; the beacon from {@value RenderLod#BEACON_VIEW}.
 *
 * <p>Seen lamps are reported to {@link BlocksClient#sawLamp} for the motes (only lamps in view get
 * them; no chunk scan).
 *
 * <p>Adapted from AgentCraft's {@code StatusLampRenderer} ({@code breathe}, {@code beacon} and
 * {@code beam}: lightning-type quads with alpha gradients).
 */
public final class LampRenderer implements BlockEntityRenderer<LampBlockEntity, LampRenderer.State> {
	/** Beam height above the beacon block (blocks); clear of the lightning rod (1 block). */
	static final float BEAM_HEIGHT = 7f;

	public static final class State extends BlockEntityRenderState {
		LampStatus status = LampStatus.OFF;
		Binding.Kind kind = Binding.Kind.NONE;
		/** How far inside the cell the glowing body's sides are (0 = full block; the paper lantern is inset). */
		float inset;
		float seconds;
		boolean halo = true;
		/** The page's options (issue #43): glow factor, and whether the glow breathes with the state. */
		float gain = 1f;
		boolean pulse = true;
	}

	private final BlocksClient client;

	LampRenderer(BlocksClient client) {
		this.client = client;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public boolean shouldRender(LampBlockEntity be, Vec3 camera) {
		BlockPos p = be.getBlockPos();
		double dx = p.getX() + 0.5 - camera.x;
		double dy = p.getY() + 0.5 - camera.y;
		double dz = p.getZ() + 0.5 - camera.z;
		boolean beacon = client.resolve(p, be.binding()).kind() == Binding.Kind.BEACON;
		Vector3fc f = RenderLod.cameraForward();
		boolean draw = RenderLod.lamp(dx, dy, dz, beacon, f.x(), f.y(), f.z());
		PerfStats.count(draw ? PerfStats.LAMP_DRAWN : PerfStats.LAMP_CULLED);
		return draw;
	}

	@Override
	public void extractRenderState(LampBlockEntity be, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderState.extractBase(be, state, breakProgress);
		BlockState bs = be.getBlockState();
		state.status = bs.hasProperty(StatusLampBlock.STATUS) ? bs.getValue(StatusLampBlock.STATUS) : LampStatus.OFF;
		state.inset = bs.getBlock() instanceof KitLanternBlock ? KitLanternBlock.BODY_INSET : 0f;
		state.kind = client.resolve(be.getBlockPos(), be.binding()).kind();
		BlockPos p = be.getBlockPos();
		state.halo = RenderLod.within(p.getX() + 0.5 - cameraPosition.x, p.getY() + 0.5 - cameraPosition.y, p.getZ() + 0.5 - cameraPosition.z,
				RenderLod.LAMP_HALO) || state.kind == Binding.Kind.BEACON;
		state.seconds = be.getLevel() == null ? 0f : (be.getLevel().getGameTime() % 1_000_000L + partialTicks) / 20f;
		LampOptions opts = LampOptions.of(be.options());
		state.gain = opts.glow().gain;
		state.pulse = opts.pulse();
		if (opts.motes()) {
			client.sawLamp(be.getBlockPos(), state.kind, state.status); // a lamp whose page turned the motes off is never reported
		}
	}

	@Override
	public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (s.status == LampStatus.OFF || s.gain <= 0f) {
			return; // glow off in the page: the model and the light of the block state remain
		}
		float level = s.pulse ? LampGlow.level(s.status, s.seconds) : LampGlow.steadyLevel(s.status);
		boolean beacon = s.kind == Binding.Kind.BEACON;
		int core = LampGlow.argb(s.status.color, LampGlow.gain(LampGlow.alpha(level, beacon ? 110 : 25, beacon ? 245 : 175), s.gain));
		int halo = LampGlow.argb(s.status.color, LampGlow.gain(LampGlow.alpha(level, beacon ? 50 : 0, beacon ? 150 : 70), s.gain));
		for (Direction d : Direction.Plane.HORIZONTAL) {
			poseStack.pushPose();
			toFace(poseStack, d, s.inset + (beacon ? -0.02f : -0.004f));
			lens(poseStack, collector, core, halo, s.halo);
			poseStack.popPose();
		}
		if (beacon) {
			poseStack.pushPose();
			// top face: rotate the north face up (viewer above, looking down)
			poseStack.translate(0.5f, 0.5f, 0.5f);
			poseStack.rotateDegrees(Axis.XP, 90f);
			poseStack.translate(-0.5f, -0.5f, -0.5f);
			toFace(poseStack, Direction.NORTH, -0.02f);
			lens(poseStack, collector, core, halo, true);
			poseStack.popPose();
			beam(poseStack, collector, LampGlow.argb(s.status.color, LampGlow.gain(LampGlow.alpha(level, 40, 130), s.gain)), s.status.color & 0xFFFFFF);
		}
	}

	/** The lens (3..13 px) at {@code core} with a halo fading out to the face's edge. */
	private static void lens(PoseStack poseStack, SubmitNodeCollector collector, int core, int halo, boolean withHalo) {
		int clear = halo & 0xFFFFFF;
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, vc) -> {
			quad(pose, vc, 3, 3, 13, 13, core, core);
			if (!withHalo) {
				return; // far: the core alone (LOD)
			}
			float e = 0.5f;
			quad(pose, vc, 3, e, 13, 3, clear, halo);
			quad(pose, vc, 3, 13, 13, 16 - e, halo, clear);
			hquad(pose, vc, e, 3, 3, 13, clear, halo);
			hquad(pose, vc, 13, 3, 16 - e, 13, halo, clear);
		});
	}

	/** Two crossed upward-fading planes from the top of the block, both windings. */
	private static void beam(PoseStack poseStack, SubmitNodeCollector collector, int lo, int hi) {
		float b = 1.02f;
		float t = b + BEAM_HEIGHT;
		float rb = 0.18f;
		float rt = 0.6f;
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, vc) -> {
			// x plane
			vc.addVertex(pose, 0.5f - rb, b, 0.5f).setColor(lo);
			vc.addVertex(pose, 0.5f + rb, b, 0.5f).setColor(lo);
			vc.addVertex(pose, 0.5f + rt, t, 0.5f).setColor(hi);
			vc.addVertex(pose, 0.5f - rt, t, 0.5f).setColor(hi);
			vc.addVertex(pose, 0.5f - rt, t, 0.5f).setColor(hi);
			vc.addVertex(pose, 0.5f + rt, t, 0.5f).setColor(hi);
			vc.addVertex(pose, 0.5f + rb, b, 0.5f).setColor(lo);
			vc.addVertex(pose, 0.5f - rb, b, 0.5f).setColor(lo);
			// z plane
			vc.addVertex(pose, 0.5f, b, 0.5f - rb).setColor(lo);
			vc.addVertex(pose, 0.5f, b, 0.5f + rb).setColor(lo);
			vc.addVertex(pose, 0.5f, t, 0.5f + rt).setColor(hi);
			vc.addVertex(pose, 0.5f, t, 0.5f - rt).setColor(hi);
			vc.addVertex(pose, 0.5f, t, 0.5f - rt).setColor(hi);
			vc.addVertex(pose, 0.5f, t, 0.5f + rt).setColor(hi);
			vc.addVertex(pose, 0.5f, b, 0.5f + rb).setColor(lo);
			vc.addVertex(pose, 0.5f, b, 0.5f - rb).setColor(lo);
		});
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true; // the beacon's beam reaches far above its block
	}

	@Override
	public int getViewDistance() {
		return (int) RenderLod.BEACON_VIEW; // upper bound; shouldRender narrows it per lamp
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float x1, float y1, int top, int bottom) {
		vc.addVertex(pose, x0, y0, 0).setColor(top);
		vc.addVertex(pose, x0, y1, 0).setColor(bottom);
		vc.addVertex(pose, x1, y1, 0).setColor(bottom);
		vc.addVertex(pose, x1, y0, 0).setColor(top);
	}

	private static void hquad(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float x1, float y1, int left, int right) {
		vc.addVertex(pose, x0, y0, 0).setColor(left);
		vc.addVertex(pose, x0, y1, 0).setColor(left);
		vc.addVertex(pose, x1, y1, 0).setColor(right);
		vc.addVertex(pose, x1, y0, 0).setColor(right);
	}

	/**
	 * Moves {@code poseStack} (at the block origin) onto the {@code facing} side of the block,
	 * {@code depth} blocks behind it (negative = in front), in 1/16 block units: x right and y down as
	 * seen by the viewer, origin at the face's top-left corner. (Same transform as the monitor's.)
	 */
	static void toFace(PoseStack poseStack, Direction facing, float depth) {
		poseStack.translate(0.5f, 0.5f, 0.5f);
		poseStack.rotateDegrees(Axis.YP, -switch (facing) {
			case EAST -> 90f;
			case SOUTH -> 180f;
			case WEST -> 270f;
			default -> 0f;
		});
		poseStack.translate(-0.5f, -0.5f, -0.5f);
		poseStack.translate(1f, 1f, depth);
		poseStack.scale(-1f / 16f, -1f / 16f, 1f);
	}
}
