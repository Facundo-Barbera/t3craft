package dev.t3craft.hq.citadel.barrio;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;

import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.RingCampusGeometry.Lot;
import dev.t3craft.hq.RingCampusGeometry.Room;
import dev.t3craft.hq.SignTags;
import dev.t3craft.hq.citadel.CitadelBarrios;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelCanvas.Mats;
import dev.t3craft.hq.style.HqStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * The gatehouse of a barrio and the text of its signs. Sizes come from the gate {@link Room}
 * ({@code w}, {@code d}, {@code c()}); the passage (3 wide, 4 high) is a door and does not scale.
 *
 * <p>The passage has a stepped arch (upside-down stairs at the top corners), the project's hanging sign at
 * the hub end and hanging lanterns along the vault. On the hub face, a name plate and an info plate flank
 * the arch, and a lantern post stands on each side outside. The roof is the pyramid of the original with a
 * second-tone eave ring, striped courses, a slab-capped ridge and finials on its ends.
 *
 * <p>Signs: the label is split on a word boundary when it has to be wrapped, and the text colour is the
 * barrio's colour turned into a dark enough one for the cherry boards ({@link BarrioKit#readable}).
 */
public final class BarrioGate {
	private BarrioGate() {
	}

	/** Height of the passage: the arch springs at {@code PASSAGE_H}, the lintel beam sits above it. */
	private static final int PASSAGE_H = 3;
	/** Height of the gatehouse's walls (the roof's ridge course sits above). */
	private static final int WALL_H = PASSAGE_H + 2;
	/** Height of the plates on the hub face. */
	private static final int PLATE_Y = 3;

	/**
	 * The gatehouse of a barrio. From scale 2 (gate at least 15 x 10) it is the big one of {@link BarrioGateBig}
	 * (a 5-wide arched passage, two flag towers, two guard rooms, the project's name and colours, and the gate arm);
	 * below that, the original small one ({@link #legacy}), so scale 1 stays block by block what it was.
	 */
	public static void gatehouse(CitadelCanvas c, Lot l, HqLayout.Module lead, Mats mats) {
		Room r = l.gate();
		if (BarrioGateBig.fits(r)) {
			BarrioGateBig.build(c, l, lead, mats);
		} else {
			legacy(c, l, lead, mats);
		}
	}

	/** The original gatehouse (3-wide passage, one pyramid roof), kept for small scales. */
	static void legacy(CitadelCanvas c, Lot l, HqLayout.Module lead, Mats mats) {
		Room r = l.gate();
		int W = r.w();
		int D = r.d();
		int mid = r.c();
		Block roof = mats.roof();
		int roofY = WALL_H + 1;
		int kmax = Math.min((D + 1) / 2, (W + 1) / 2);
		for (int u = 0; u < D; u++) {
			for (int v = 0; v < W; v++) {
				BlockPos p = r.cell(u, v, 0);
				int dv = v - mid;
				boolean passage = Math.abs(dv) <= 1;
				c.set(p, passage ? c.accent() : FOOT);
				if (passage) {
					for (int y = 1; y <= PASSAGE_H + 1; y++) {
						c.set(p.atY(y), AIR);
					}
					if (dv != 0) {
						Direction away = dv < 0 ? r.side().getOpposite() : r.side();
						c.set(p.atY(PASSAGE_H + 1), BarrioKit.stair(roof, away, StairsShape.STRAIGHT, Half.TOP));
					}
					c.set(p.atY(WALL_H), c.kit.beam(r.back().getAxis()));
				} else {
					boolean corner = (u == 0 || u == D - 1) && (v == 0 || v == W - 1);
					for (int y = 1; y <= WALL_H; y++) {
						c.set(p.atY(y), corner ? mats.post() : (y == PASSAGE_H && u == D / 2 && (v == 0 || v == W - 1) ? c.kit.wallPanel(true) : mats.wall()));
					}
				}
				c.set(p.atY(roofY), mats.ridge());
				int ku = Math.min(u + 1, D - u);
				int kv = Math.min(v + 1, W - v);
				int k = Math.min(ku, kv);
				int y = roofY + k;
				BlockState s;
				if (k == kmax) {
					s = mats.ridge();
					c.set(p.atY(y + 1), kv == k ? BarrioKit.finial(roof) : BarrioKit.slabOf(roof));
				} else {
					s = BarrioRoofs.hipStair(r, roof, u, v, k, false, p.atY(y));
				}
				c.set(p.atY(y), s);
			}
		}
		// each slope ring rests on the one below it: one block under a stair of every ring from the second
		for (int k = 2; k <= kmax; k++) {
			c.set(r.cell(k - 1, mid, roofY + k - 1), mats.ridge());
		}
		BarrioRoofs.eaveRing(c, r, l, -1, roofY, roof);
		lanterns(c, r);
		hangingSign(c, r, lead);
		plates(c, r, lead);
		posts(c, r);
	}

	/** Hanging lanterns along the vault, never next to the sign at the hub end. */
	private static void lanterns(CitadelCanvas c, Room r) {
		int mid = r.c();
		for (int u = 1; u < r.d() - 2; u += 2) {
			c.set(r.cell(u, mid, PASSAGE_H + 1), BarrioKit.lantern(true));
		}
	}

	private static void hangingSign(CitadelCanvas c, Room r, HqLayout.Module lead) {
		BlockPos sign = r.cell(r.d() - 1, r.c(), PASSAGE_H + 1);
		BlockState hs = Blocks.CHERRY_HANGING_SIGN.defaultBlockState().setValue(CeilingHangingSignBlock.ROTATION, CitadelBarrios.rotation(r.back()))
				.setValue(CeilingHangingSignBlock.ATTACHED, false);
		c.put(sign.getX(), sign.getY(), sign.getZ(), hs, HANGING);
		c.data.put(sign.asLong(), CitadelBarrios.barrioSign(c, lead).signTag());
		c.fixtures.add(sign.asLong());
	}

	/** Two wall plates on the hub face, either side of the arch: the name on the left, desks and environment on the right. */
	private static void plates(CitadelCanvas c, Room r, HqLayout.Module lead) {
		HqStyle.SignText t = CitadelBarrios.barrioSign(c, lead);
		DyeColor col = HqStyle.envColor(lead);
		int mid = r.c();
		String[] name = wrap(lead.label(), SignTags.LINE);
		// the kit writes the plaque's own text format: the first line takes the colour (the name, the desk count)
		plate(c, r, mid - 2, c.kit.plaqueData(col, name[0], name[1]));
		plate(c, r, mid + 2, c.kit.plaqueData(col, t.l3(), t.l4()));
	}

	private static void plate(CitadelCanvas c, Room r, int v, CompoundTag tag) {
		BlockPos p = r.cell(r.d(), v, PLATE_Y);
		c.put(p.getX(), p.getY(), p.getZ(), c.kit.plaque(r.back()), DECOR);
		c.data.put(p.asLong(), tag);
		c.fixtures.add(p.asLong());
	}

	/** A lantern post on each side of the gate, outside the hub face. */
	private static void posts(CitadelCanvas c, Room r) {
		int mid = r.c();
		for (int s = -1; s <= 1; s += 2) {
			int v = mid + s * (mid > 0 ? mid : 1);
			BlockPos base = r.cell(r.d() + 1, v, 1);
			c.set(base, b(Blocks.CHERRY_FENCE));
			c.set(base.above(), b(Blocks.CHERRY_FENCE));
			c.set(base.above(2), c.kit.gardenLantern());
		}
	}

	// ------------------------------------------------------------------ signs

	/** Characters that fit on a line of the hanging sign (60 px wide, narrower than a wall sign). */
	private static final int HANGING_LINE = 11;

	/** The label in two lines of at most {@code width} characters, wrapped on a word boundary when it has to be. */
	static String[] wrap(String label, int width) {
		if (label.length() <= width) {
			return new String[] {label, ""};
		}
		for (int i = width; i >= 4; i--) {
			char ch = label.charAt(i);
			boolean keepsChar = ch == '-' || ch == '/' || ch == '.';
			// a kept separator ends line 1, so it has to fit in it
			if ((ch == ' ' || ch == '_' || keepsChar) && (!keepsChar || i < width)) {
				String l1 = label.substring(0, keepsChar ? i + 1 : i).strip();
				return new String[] {l1, label.substring(i + 1).strip()};
			}
		}
		return new String[] {label.substring(0, width), label.substring(width)};
	}

	/** The text of the hanging sign: the label (wrapped for its width), the desk count and the environment. */
	public static HqStyle.SignText barrioSign(CitadelCanvas c, HqLayout.Module w) {
		String[] name = wrap(w.label(), HANGING_LINE);
		int desks = 0;
		for (HqLayout.Module m : c.layout.modulesOf(w.projectKey())) {
			desks += m.desks().size();
		}
		return new HqStyle.SignText(BarrioKit.readable(HqStyle.envColor(w)), name[0], name[1], desks + (desks == 1 ? " desk" : " desks"), w.envLabel());
	}
}
