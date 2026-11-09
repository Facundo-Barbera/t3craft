package dev.t3craft.blocks;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * kit/glow-panel: what {@link KitGlowStripBlock} and {@link KitGlowPanelBlock} share. The idea of an emissive
 * wall panel and strip comes from AgentCraft (MIT, design reference only; see THIRD_PARTY_NOTICES.md): the code,
 * models and textures here are ours.
 */
public final class KitGlow {
	/** Block light of both blocks, always on (they are lights, not switches). */
	public static final int LIGHT = 12;

	/** The colour of the light: paper/amber, or T3 cyan. The glow texture and the item look follow it. */
	public enum Tone implements StringRepresentable {
		WARM, COOL;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public static final EnumProperty<Tone> TONE = EnumProperty.create("tone", Tone.class);

	private KitGlow() {
	}

	public static int light(BlockState state) {
		return LIGHT;
	}

	/**
	 * Empty-handed right click flips warm/cool. With anything in hand it passes, so a player can still place a block
	 * (another strip, say) against the one they point at.
	 */
	static InteractionResult flipTone(BlockState state, Level level, BlockPos pos, Player player) {
		if (!player.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			level.setBlock(pos, state.cycle(TONE), Block.UPDATE_ALL);
		}
		return InteractionResult.SUCCESS;
	}
}
