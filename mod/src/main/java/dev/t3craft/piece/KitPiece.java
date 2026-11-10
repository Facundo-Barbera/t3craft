package dev.t3craft.piece;

import dev.t3craft.hq.HqKit;
import dev.t3craft.hq.SignTags;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * feat/piece-catalog-preview: the HQ kit ({@link HqKit}: the blocks every part of the HQ is drawn with) laid out
 * as a swatch: one block per {@link HqKit.Role} on its own plinth, with a sign carrying the role's name. The kit
 * is the active one: t3craft's own blocks when the blocks module is on, vanilla stand-ins otherwise.
 */
final class KitPiece implements Piece {
	private static final int PER_ROW = 8;
	private static final int STEP_X = 4;
	private static final int STEP_Z = 5;

	@Override
	public String id() {
		return "kit_swatch";
	}

	@Override
	public String group() {
		return "kit";
	}

	@Override
	public String title() {
		return "HQ kit swatch";
	}

	@Override
	public String note() {
		return "every role of the active HqKit (floor, beam, desk, lantern, glow strips, console...), one block each";
	}

	@Override
	public Built build(PieceSpec spec, boolean fresh) {
		HqKit kit = HqKit.active();
		BlockState plinth = Blocks.SMOOTH_STONE.defaultBlockState();
		BlockState sign = Blocks.OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 0);
		PieceModel.Builder b = PieceModel.builder();
		HqKit.Role[] roles = HqKit.Role.values();
		for (int i = 0; i < roles.length; i++) {
			HqKit.Role role = roles[i];
			int x = 1 + (i % PER_ROW) * STEP_X;
			int z = 1 + (i / PER_ROW) * STEP_Z;
			b.add(x, 0, z, plinth);
			b.add(x, 1, z, kit.state(role, role == HqKit.Role.BEAM ? Direction.UP : Direction.NORTH));
			String[] lines = lines(role.id());
			b.add(x, 1, z + 2, sign, SignTags.sign(DyeColor.BLACK, false, lines[0], lines[1], role.oriented() ? "oriented" : "", ""));
		}
		return new Built(b.build(), roles.length + " roles of " + kit.getClass().getSimpleName());
	}

	/** A role id such as {@code glow_strip_high_cool} on two sign lines. */
	private static String[] lines(String id) {
		String t = id.replace('_', ' ');
		if (t.length() <= SignTags.LINE) {
			return new String[] {t, ""};
		}
		int cut = t.lastIndexOf(' ', SignTags.LINE);
		if (cut <= 0) {
			cut = SignTags.LINE;
		}
		String rest = t.substring(cut).trim();
		return new String[] {t.substring(0, cut), rest.length() <= SignTags.LINE ? rest : rest.substring(0, SignTags.LINE)};
	}
}
