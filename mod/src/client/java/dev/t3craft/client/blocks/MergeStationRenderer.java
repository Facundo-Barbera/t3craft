package dev.t3craft.client.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.t3craft.blocks.Binding;
import dev.t3craft.blocks.MergeStationBlock;
import dev.t3craft.blocks.MergeStationBlockEntity;
import dev.t3craft.client.chat.ChatTheme;
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
 * Draws the merge station's panel: the number of pull requests waiting ("3 PRs"), what that means ("to review") and
 * the title of the first one, full-bright on a dark glass background, on the front of the screen housing. The
 * model carries the state in its colours (lamps, frame); this only adds the text. The glyph runs of a station are
 * rebuilt only when its {@link MergeFeed.Summary} object changes ({@link BlocksClient} hands out the same
 * instance until the next refresh), so a steady station costs one lookup per frame.
 */
public final class MergeStationRenderer implements BlockEntityRenderer<MergeStationBlockEntity, MergeStationRenderer.State> {
	/** Screen pixels per block (one model pixel = 10 screen pixels, one text line each). */
	static final float PPB = 160f;
	static final float PX = PPB / 16f;
	/** The panel of the north-facing model: x 2..14, y 10..15, front at z = 6.5 (16ths); the background keeps a half-pixel frame. */
	static final float SCREEN_X0 = PX * 2.5f;
	static final float SCREEN_X1 = PX * 13.5f;
	static final float SCREEN_Y0 = PX * 1.5f;
	static final float SCREEN_Y1 = PX * 5.5f;
	static final float DEPTH = 6.5f / 16f - 0.003f;
	static final int LINE_HEIGHT = 10;
	static final int BACKGROUND = 0xFF0B1418;
	/** Longest text the panel fits (about 6 screen pixels per character over the 110 px of the panel). */
	static final int MAX_CHARS = 17;

	public static final class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		List<FormattedCharSequence> lines = List.of();
		int[] colors = new int[0];
	}

	/** The last glyph runs built for one station. */
	private record Cached(MergeFeed.Summary summary, String optionsKey, List<FormattedCharSequence> lines, int[] colors) {
	}

	private final BlocksClient client;
	private final Map<Long, Cached> cache = new HashMap<>();

	MergeStationRenderer(BlocksClient client) {
		this.client = client;
	}

	void clear() {
		cache.clear();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(MergeStationBlockEntity be, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderState.extractBase(be, state, breakProgress);
		BlockState bs = be.getBlockState();
		state.facing = bs.hasProperty(MergeStationBlock.FACING) ? bs.getValue(MergeStationBlock.FACING) : Direction.NORTH;
		Binding b = client.resolve(be.getBlockPos(), be.binding());
		MergeFeed.Summary s = client.mergeSummary(b);
		long key = be.getBlockPos().asLong();
		// the page's options (issue #43); the key includes the resolved accent so a theme reload redraws
		MergeOptions opts = MergeOptions.of(be.options());
		int accent = opts == MergeOptions.DEFAULT ? 0 : BlockAccent.argb(opts.accent(), ChatTheme.warmStudio());
		String optionsKey = opts.showTitle() + ":" + Integer.toHexString(accent);
		Cached c = cache.get(key);
		if (c == null || c.summary() != s || !c.optionsKey().equals(optionsKey)) {
			c = build(s, b, opts, accent, optionsKey);
			cache.put(key, c);
		}
		state.lines = c.lines();
		state.colors = c.colors();
	}

	private static Cached build(MergeFeed.Summary s, Binding b, MergeOptions opts, int accentOption, String optionsKey) {
		int accent = accentOption != 0 ? accentOption : 0xFF000000 | MergeFeed.color(s.state());
		String first = s.pending().isEmpty() ? (b.isNone() ? "not linked" : "") : opts.showTitle() ? s.pending().get(0).title() : "";
		String[] text = {MergeFeed.headline(s), MergeFeed.subline(s), clip(first)};
		int[] argb = {accent, 0xFFB8C0C8, 0xFF7C8690};
		int n = text[2].isEmpty() ? 2 : 3;
		List<FormattedCharSequence> lines = new java.util.ArrayList<>(n);
		int[] colors = new int[n];
		for (int i = 0; i < n; i++) {
			lines.add(Component.literal(text[i]).getVisualOrderText());
			colors[i] = argb[i];
		}
		return new Cached(s, optionsKey, List.copyOf(lines), colors);
	}

	/** The title cut to the panel's width. */
	static String clip(String s) {
		String t = s == null ? "" : s.strip();
		return t.length() <= MAX_CHARS ? t : t.substring(0, MAX_CHARS - 1) + "…";
	}

	@Override
	public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		int light = LightCoordsUtil.FULL_BRIGHT;
		poseStack.pushPose();
		MonitorRenderer.toFace(poseStack, s.facing, DEPTH, PPB);
		collector.order(0).submitTextBackground(poseStack, SCREEN_X0, SCREEN_Y0, SCREEN_X1, SCREEN_Y1, BACKGROUND, Font.DisplayMode.NORMAL, light);
		float y = SCREEN_Y0 + 3;
		for (int i = 0; i < s.lines.size() && y + 8 <= SCREEN_Y1; i++) {
			collector.order(1).submitText(poseStack, SCREEN_X0 + 4, y, s.lines.get(i), false, Font.DisplayMode.POLYGON_OFFSET, light, s.colors[i], 0, 0);
			y += i == 0 ? LINE_HEIGHT + 1 : LINE_HEIGHT;
		}
		poseStack.popPose();
	}

	@Override
	public int getViewDistance() {
		return 32;
	}
}
