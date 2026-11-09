package dev.t3craft.client.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.t3craft.blocks.ArchiveTerminalBlock;
import dev.t3craft.blocks.ArchiveTerminalBlockEntity;
import dev.t3craft.client.archive.ArchiveClient;
import dev.t3craft.client.archive.TerminalDisplay;
import java.util.ArrayList;
import java.util.List;
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
 * Draws the archive terminal's screen: "ARCHIVE · N threads" and the latest three closed threads
 * ({@link TerminalDisplay}, from {@link ArchiveClient#display()}), full-bright on a dark glass
 * background, on the front of the screen housing. Glyph runs are rebuilt only when the display's
 * version changes (the store's version), so a steady terminal costs one comparison per frame.
 */
public final class ArchiveTerminalRenderer implements BlockEntityRenderer<ArchiveTerminalBlockEntity, ArchiveTerminalRenderer.State> {
	/** Screen pixels per block (≈ 6 px per character). */
	static final float PPB = 160f;
	/**
	 * The screen housing of the north-facing model: x 1..15, y 7..15, front at z = 6 (16ths). The model's visor
	 * (a cherry cap, y 15..16) hangs 1/16 in front of the housing, so the glass starts one model pixel below the top.
	 */
	static final float SCREEN_X0 = PPB * 1 / 16f;
	static final float SCREEN_X1 = PPB * 15 / 16f;
	static final float SCREEN_Y0 = PPB * 1 / 16f;
	static final float SCREEN_Y1 = PPB * 9 / 16f;
	static final float DEPTH = 6 / 16f - 0.003f;
	static final int LINE_HEIGHT = 10;
	static final int BACKGROUND = 0xFF0B1418;

	public static final class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		List<FormattedCharSequence> lines = List.of();
		int[] colors = new int[0];
	}

	private int version = Integer.MIN_VALUE;
	private List<FormattedCharSequence> lines = List.of();
	private int[] colors = new int[0];

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ArchiveTerminalBlockEntity be, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderState.extractBase(be, state, breakProgress);
		BlockState bs = be.getBlockState();
		state.facing = bs.hasProperty(ArchiveTerminalBlock.FACING) ? bs.getValue(ArchiveTerminalBlock.FACING) : Direction.NORTH;
		ArchiveClient archive = ArchiveClient.get();
		if (archive == null) {
			state.lines = List.of();
			state.colors = new int[0];
			return;
		}
		TerminalDisplay.Screen s = archive.display();
		if (s.version() != version || lines.isEmpty() && !s.lines().isEmpty()) {
			List<FormattedCharSequence> l = new ArrayList<>(s.lines().size());
			int[] c = new int[s.lines().size()];
			for (int i = 0; i < s.lines().size(); i++) {
				l.add(Component.literal(s.lines().get(i).text()).getVisualOrderText());
				c[i] = 0xFF000000 | s.lines().get(i).color();
			}
			lines = List.copyOf(l);
			colors = c;
			version = s.version();
		}
		state.lines = lines;
		state.colors = colors;
	}

	@Override
	public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		int light = LightCoordsUtil.FULL_BRIGHT;
		poseStack.pushPose();
		MonitorRenderer.toFace(poseStack, s.facing, DEPTH, PPB);
		collector.order(0).submitTextBackground(poseStack, SCREEN_X0, SCREEN_Y0, SCREEN_X1, SCREEN_Y1, BACKGROUND, Font.DisplayMode.NORMAL, light);
		float y = SCREEN_Y0 + 5;
		for (int i = 0; i < s.lines.size() && y + 8 <= SCREEN_Y1; i++) {
			collector.order(1).submitText(poseStack, SCREEN_X0 + 5, y, s.lines.get(i), false, Font.DisplayMode.POLYGON_OFFSET, light, s.colors[i], 0, 0);
			y += i == 0 ? LINE_HEIGHT + 2 : LINE_HEIGHT;
		}
		poseStack.popPose();
	}

	@Override
	public int getViewDistance() {
		return 32;
	}
}
