package dev.t3craft.hq.citadel.catalog.garden;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * A working vegetable garden of raised beds, laid out as a grid that grows with the plot.
 *
 * <pre>
 *  v0..    apron: 2-wide slab path across the front, lamp posts at both ends
 *  beds    rows x columns of wooden beds (7 or 5 wide, 6-10 long), 2-4 wide slab lanes between them
 *  d-6..   2-wide lane, then the back zone: a roofed tool shed (composters, barrel, chest), a small
 *          roofed well and a compost yard (or, on shallow plots, a potting bench under an awning)
 *  d-1     hedge with sunflowers; side hedges open wherever a lane leaves the plot
 * </pre>
 *
 * Function: every bed is hydrated ({@code moisture=7} farmland within 4 cells of water: a water channel down the
 * middle of the bed, or a pool in the middle of a pumpkin and melon patch), each bed grows a different mix of
 * wheat, carrots, potatoes, beetroots, pumpkins and melons (stems that attach to their fruit when ripe) planted in
 * stages from seedling to ripe, lanes are slab paths (half-block steps, no jumps), the shed, bench, composters,
 * barrel and chest are at hand, a scarecrow stands in some beds, lamps on posts light the lanes. Farmland never
 * has a solid block over it (crops only), and every lantern stands on a post or hangs from a full block.
 *
 * <p>Size: 8-24 x 11-24 x 5+, and bigger plots at scale: columns and rows of beds are added (never stretched past
 * 10 long); the apron, lane widths and the margins take the rest. [depth] rims with corner posts and caps, gable
 * roofs on the shed and the well, lamps, stepped slab lanes, hedge. [palette] dark oak or cherry (by seed) for
 * every wooden part, grey stone slabs and curbs, soil and crops, hay, azalea hedge. [finish] lily pads, potted
 * ferns, mixed composter levels, sunflowers.
 */
public final class GardenVegRaisedBeds implements Variant {
	private record Wood(Block planks, Block log, Block stripped, Block fence, Block slab, Block stairs) {
	}

	private static final Wood DARK_OAK = new Wood(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DARK_OAK_FENCE,
			Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS);
	private static final Wood CHERRY = new Wood(Blocks.CHERRY_PLANKS, Blocks.CHERRY_LOG, Blocks.STRIPPED_CHERRY_LOG, Blocks.CHERRY_FENCE,
			Blocks.CHERRY_SLAB, Blocks.CHERRY_STAIRS);

	private static final int SHED = 0;
	private static final int WELL = 1;
	private static final int COMPOST = 2;
	private static final int BENCH = 3;
	private static final int CISTERN = 4;

	@Override
	public String id() {
		return "garden_veg_raised_beds";
	}

	@Override
	public Family family() {
		return Family.GARDEN;
	}

	@Override
	public int minW() {
		return 8;
	}

	@Override
	public int minD() {
		return 11;
	}

	@Override
	public int minH() {
		return 5;
	}

	@Override
	public int maxW() {
		return 24;
	}

	@Override
	public int maxD() {
		return 24;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		new Garden(c, p).run();
	}

	private static final class Garden {
		final CitadelCanvas c;
		final Plot p;
		final int w;
		final int d;
		final Wood wood;
		// layout
		int bw;
		int ncol;
		int gapU;
		int u0;
		int bl;
		int nrow;
		int gapV;
		int apron;
		boolean full;
		int modDepth;
		byte[][] kind; // 0 path, 1 bed, 2 module, 3 hedge
		final List<int[]> modules = new ArrayList<>(); // {type, u}

		Garden(CitadelCanvas c, Plot p) {
			this.c = c;
			this.p = p;
			this.w = p.w();
			this.d = p.d();
			this.wood = p.hash(0, 0, 0, 2) == 0 ? DARK_OAK : CHERRY;
		}

		void set(int u, int y, int v, BlockState s) {
			p.set(c, u, y, v, s);
		}

		void run() {
			layout();
			paint();
			for (int j = 0; j < nrow; j++) {
				for (int i = 0; i < ncol; i++) {
					bed(u0 + i * (bw + gapU), apron + j * (bl + gapV), j * ncol + i);
				}
			}
			for (int[] m : modules) {
				switch (m[0]) {
					case SHED -> shed(m[1]);
					case WELL -> well(m[1]);
					case COMPOST -> compost(m[1]);
					case BENCH -> bench(m[1]);
					default -> cistern(m[1]);
				}
			}
			post(0, 0, true);
			post(w - 1, 0, true);
		}

		// ------------------------------------------------------------------ layout

		void layout() {
			int usable = w - 2;
			int[] tryW = p.hash(0, 1, 0, 2) == 0 ? new int[] {9, 7, 5} : new int[] {5, 7, 9};
			int best = -1;
			int bestLeft = Integer.MAX_VALUE;
			for (int t : tryW) {
				if (usable < t) {
					continue;
				}
				int n = Math.max(1, (usable + 2) / (t + 2));
				int left = usable - n * t - (n - 1) * 2;
				if (left < bestLeft || left == bestLeft && t > best && p.hash(0, 1, 0, 2) == 0) {
					bestLeft = left;
					best = t;
				}
			}
			bw = best < 0 ? Math.max(3, usable) : best;
			ncol = Math.max(1, (usable + 2) / (bw + 2));
			int spare = Math.max(0, usable - ncol * bw - (ncol - 1) * 2);
			gapU = 2;
			while (ncol > 1 && spare >= ncol - 1 && gapU < 4) {
				gapU++;
				spare -= ncol - 1;
			}
			u0 = 1 + spare / 2;

			full = d >= 14;
			modDepth = full ? 3 : 1;
			int avail = d - 2 - (2 + modDepth + 1);
			nrow = Math.max(1, (int) Math.round((avail + 2) / 10.0));
			bl = (avail - (nrow - 1) * 2) / nrow;
			while (nrow > 1 && bl < 5) {
				nrow--;
				bl = (avail - (nrow - 1) * 2) / nrow;
			}
			bl = Math.min(bl, 10);
			int vs = Math.max(0, avail - nrow * bl - (nrow - 1) * 2);
			gapV = 2;
			while (nrow > 1 && vs >= nrow - 1 && gapV < 4) {
				gapV++;
				vs -= nrow - 1;
			}
			apron = 2 + vs;

			kind = new byte[w][d];
			for (int u = 0; u < w; u++) {
				kind[u][d - 1] = 3;
			}
			for (int v = d - 1 - modDepth; v < d; v++) {
				kind[0][v] = 3;
				kind[w - 1][v] = 3;
			}
			for (int j = 0; j < nrow; j++) {
				int v0 = apron + j * (bl + gapV);
				for (int v = v0; v < v0 + bl; v++) {
					kind[0][v] = 3;
					kind[w - 1][v] = 3;
					for (int i = 0; i < ncol; i++) {
						int ox = u0 + i * (bw + gapU);
						for (int u = ox; u < ox + bw; u++) {
							kind[u][v] = 1;
						}
					}
				}
			}
			placeModules(usable);
		}

		void placeModules(int usable) {
			int[] seq = full ? new int[] {SHED, WELL, COMPOST} : new int[] {BENCH, CISTERN, COMPOST};
			List<Integer> list = new ArrayList<>();
			int sum = 0;
			for (int i = 0; i < 400; i++) {
				int m = seq[i % 3];
				int need = width(m) + (list.isEmpty() ? 0 : 2);
				if (sum + need > usable) {
					break;
				}
				list.add(m);
				sum += need;
			}
			if (list.isEmpty()) {
				return;
			}
			if (p.hash(3, 3, 3, 2) == 1) {
				Collections.reverse(list);
			}
			int widths = 0;
			for (int m : list) {
				widths += width(m);
			}
			int n = list.size();
			int spare = usable - widths - (n - 1) * 2;
			int gap = 2 + (n > 1 ? spare / (n + 1) : 0);
			int total = widths + (n - 1) * gap;
			int u = 1 + (usable - total) / 2;
			int v0 = full ? d - 4 : d - 2;
			for (int m : list) {
				modules.add(new int[] {m, u});
				for (int x = u; x < u + width(m); x++) {
					for (int v = v0; v < v0 + modDepth; v++) {
						kind[x][v] = 2;
					}
				}
				u += width(m) + gap;
			}
		}

		static int width(int m) {
			return m == SHED || m == BENCH ? 5 : 3;
		}

		// ------------------------------------------------------------------ ground

		BlockState pathSlab(int u, int v) {
			return switch (p.hash(u, 0, v, 10)) {
				case 0, 1, 2, 3 -> slab(Blocks.STONE_BRICK_SLAB);
				case 4, 5, 6 -> slab(Blocks.COBBLESTONE_SLAB);
				case 7, 8 -> slab(Blocks.MOSSY_COBBLESTONE_SLAB);
				default -> slab(Blocks.ANDESITE_SLAB);
			};
		}

		void paint() {
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					switch (kind[u][v]) {
						case 0 -> set(u, 0, v, pathSlab(u, v));
						case 3 -> hedge(u, v);
						default -> {
						}
					}
				}
			}
		}

		void hedge(int u, int v) {
			int k = p.hash(u, 5, v, 7);
			if (v == d - 1 && k == 0 && u > 0 && u < w - 1) {
				set(u, 0, v, b(Blocks.SUNFLOWER).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
				set(u, 1, v, b(Blocks.SUNFLOWER).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
				return;
			}
			set(u, 0, v, k == 1 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : leaves(Blocks.AZALEA_LEAVES));
			if (k < 5) {
				set(u, 1, v, k == 2 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : leaves(Blocks.AZALEA_LEAVES));
			}
		}

		/** A post: two logs and a slab cap, or two logs, a fence and a standing lantern. */
		void post(int u, int v, boolean lamp) {
			set(u, 0, v, b(wood.log()));
			set(u, 1, v, b(wood.log()));
			if (lamp) {
				set(u, 2, v, b(wood.fence()));
				set(u, 3, v, b(Blocks.LANTERN));
			} else {
				set(u, 2, v, slab(wood.slab()));
			}
		}

		// ------------------------------------------------------------------ beds

		static BlockState farm() {
			return b(Blocks.FARMLAND).setValue(BlockStateProperties.MOISTURE, 7);
		}

		/** Crop kinds: 0 wheat, 1 carrots, 2 potatoes, 3 beetroots. */
		static BlockState crop(int kind, int age) {
			int a = Math.max(0, Math.min(7, age));
			return switch (kind) {
				case 0 -> b(Blocks.WHEAT).setValue(BlockStateProperties.AGE_7, a);
				case 1 -> b(Blocks.CARROTS).setValue(BlockStateProperties.AGE_7, a);
				case 2 -> b(Blocks.POTATOES).setValue(BlockStateProperties.AGE_7, a);
				default -> b(Blocks.BEETROOTS).setValue(BlockStateProperties.AGE_3, Math.min(3, (a * 3 + 3) / 7));
			};
		}

		/** Growth stage 2..7 along a bed: seedlings at one end, ripe at the other, with some noise. */
		int stage(int bi, int iu, int iv, int il) {
			double t = il > 1 ? iv / (il - 1.0) : 1.0;
			if (p.hash(bi, 2, 2, 2) == 1) {
				t = 1.0 - t;
			}
			int a = 2 + (int) Math.round(5 * t) + (p.hash(bi * 31 + iu, 3, iv, 3) - 1);
			return Math.max(2, Math.min(7, a));
		}

		void bed(int ox, int oz, int bi) {
			int iw = bw - 2;
			int il = bl - 2;
			int midC = iw / 2;
			int midR = il / 2;
			boolean patch = il >= 5 && p.hash(bi, 7, 3, 3) == 0;
			for (int u = ox; u < ox + bw; u++) {
				for (int v = oz; v < oz + bl; v++) {
					boolean eu = u == ox || u == ox + bw - 1;
					boolean ev = v == oz || v == oz + bl - 1;
					if (eu && ev) {
						continue;
					}
					if (eu || ev) {
						int k = p.hash(u, 1, v, 6);
						if (k == 0) {
							set(u, 0, v, b(wood.stripped()).setValue(BlockStateProperties.AXIS, eu ? Direction.Axis.Z : Direction.Axis.X));
						} else {
							set(u, 0, v, b(wood.planks()));
						}
						if (eu && p.hash(u, 13, v, 6) == 0) {
							set(u, 1, v, pot(u, v));
						}
						continue;
					}
					int iu = u - ox - 1;
					int iv = v - oz - 1;
					if (patch) {
						patchCell(u, v, iu, iv, iw, il, midC, midR, bi);
					} else {
						channelCell(u, v, iu, iv, iw, il, midC, bi);
					}
				}
			}
			int[][] corners = {{ox, oz}, {ox + bw - 1, oz}, {ox, oz + bl - 1}, {ox + bw - 1, oz + bl - 1}};
			for (int i = 0; i < 4; i++) {
				post(corners[i][0], corners[i][1], p.hash(bi, 10 + i, 0, 3) == 0);
			}
			if (!patch && il >= 5 && p.hash(bi, 9, 9, 4) == 0) {
				scarecrow(ox + 1, oz + 1 + midR);
			}
		}

		BlockState pot(int u, int v) {
			return b(switch (p.hash(u, 14, v, 5)) {
				case 0 -> Blocks.POTTED_RED_TULIP;
				case 1 -> Blocks.POTTED_OXEYE_DAISY;
				case 2 -> Blocks.POTTED_CORNFLOWER;
				case 3 -> Blocks.POTTED_ALLIUM;
				default -> Blocks.POTTED_AZURE_BLUET;
			});
		}

		int rootKind(int bi, int iu, int iv, int iw, int il, int midC) {
			boolean left = iu < midC;
			boolean outer = left ? iu == 0 : iu == iw - 1;
			int segs = il >= 7 ? 3 : il >= 5 ? 2 : 1;
			int seg = iv * segs / il;
			int rot = p.hash(bi, 1, 1, 4);
			return Math.floorMod(rot + (left ? 0 : 1) + (outer ? 0 : 2) + seg, 4);
		}

		void channelCell(int u, int v, int iu, int iv, int iw, int il, int midC, int bi) {
			if (iu == midC) {
				set(u, 0, v, b(Blocks.WATER));
				if (p.hash(u, 4, v, 6) == 0) {
					set(u, 1, v, b(Blocks.LILY_PAD));
				}
				return;
			}
			set(u, 0, v, farm());
			set(u, 1, v, crop(rootKind(bi, iu, iv, iw, il, midC), stage(bi, iu, iv, il)));
		}

		void patchCell(int u, int v, int iu, int iv, int iw, int il, int midC, int midR, int bi) {
			if (iu == midC) {
				if (iv == midR) {
					set(u, 0, v, b(Blocks.WATER));
					set(u, 1, v, b(Blocks.LILY_PAD));
					return;
				}
				set(u, 0, v, b(Blocks.DIRT));
				boolean pumpkinRow = iv % 2 == 0;
				if (stage(bi, pumpkinRow ? midC - 1 : midC + 1, iv, il) >= 5) {
					set(u, 1, v, pumpkinRow ? b(Blocks.PUMPKIN) : b(Blocks.MELON));
				}
				return;
			}
			set(u, 0, v, farm());
			boolean left = iu < midC;
			if (Math.abs(iu - midC) == 1) {
				boolean stemRow = left ? iv % 2 == 0 : iv % 2 == 1;
				if (stemRow) {
					int age = stage(bi, iu, iv, il);
					if (iv == midR) {
						age = Math.min(age, 4);
					}
					Block stem = left ? Blocks.PUMPKIN_STEM : Blocks.MELON_STEM;
					Block attached = left ? Blocks.ATTACHED_PUMPKIN_STEM : Blocks.ATTACHED_MELON_STEM;
					if (age >= 5 && iv != midR) {
						set(u, 1, v, b(attached).setValue(BlockStateProperties.HORIZONTAL_FACING, left ? Direction.EAST : Direction.WEST));
					} else {
						set(u, 1, v, b(stem).setValue(BlockStateProperties.AGE_7, age));
					}
				} else {
					set(u, 1, v, crop(3, stage(bi, iu, iv, il)));
				}
			} else {
				set(u, 1, v, crop(rootKind(bi, iu, iv, iw, il, midC), stage(bi, iu, iv, il)));
			}
		}

		void scarecrow(int u, int v) {
			set(u, 0, v, b(Blocks.DIRT));
			set(u, 1, v, b(wood.fence()));
			set(u, 2, v, b(Blocks.HAY_BLOCK));
			set(u - 1, 2, v, b(wood.fence()).setValue(BlockStateProperties.EAST, true));
			set(u + 1, 2, v, b(wood.fence()).setValue(BlockStateProperties.WEST, true));
			set(u, 3, v, b(Blocks.CARVED_PUMPKIN).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
			set(u, 4, v, slab(wood.slab()));
		}

		// ------------------------------------------------------------------ back zone

		BlockState composter(int u, int v) {
			return b(Blocks.COMPOSTER).setValue(BlockStateProperties.LEVEL_COMPOSTER, 2 + p.hash(u, 6, v, 7));
		}

		BlockState barrel() {
			return b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.NORTH);
		}

		BlockState chest() {
			return b(Blocks.CHEST).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
		}

		BlockState cauldron() {
			return b(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3);
		}

		/** Gable roof one block thick on a row of three: stairs, ridge planks, stairs. */
		void roof(int ux0, int ux1, int vf) {
			for (int u = ux0; u <= ux1; u++) {
				set(u, 4, vf, stairs(wood.stairs(), Direction.NORTH));
				set(u, 4, vf + 1, b(wood.planks()));
				set(u, 4, vf + 2, stairs(wood.stairs(), Direction.SOUTH));
			}
		}

		void shed(int su) {
			int vf = d - 4;
			int vm = d - 3;
			int vb = d - 2;
			int ue = su + 4;
			for (int y = 0; y <= 3; y++) {
				set(su, y, vf, b(wood.log()));
				set(ue, y, vf, b(wood.log()));
				set(su, y, vb, b(wood.log()));
				set(ue, y, vb, b(wood.log()));
			}
			set(su, 0, vm, b(wood.planks()));
			set(ue, 0, vm, b(wood.planks()));
			set(su, 1, vm, b(wood.fence()));
			set(ue, 1, vm, b(wood.fence()));
			for (int u = su + 1; u <= su + 3; u++) {
				set(u, 0, vf, slab(wood.slab()));
				set(u, 0, vm, slab(wood.slab()));
			}
			boolean flip = p.hash(su, 8, 0, 2) == 1;
			set(su + 1, 0, vb, flip ? chest() : composter(su + 1, vb));
			set(su + 2, 0, vb, barrel());
			set(su + 2, 1, vb, b(Blocks.LANTERN));
			set(su + 3, 0, vb, flip ? composter(su + 3, vb) : chest());
			set(su + 1, 0, vm, b(Blocks.HAY_BLOCK));
			roof(su, ue, vf);
		}

		void well(int wu) {
			int vf = d - 4;
			int vm = d - 3;
			int vb = d - 2;
			for (int u = wu; u <= wu + 2; u++) {
				for (int v = vf; v <= vb; v++) {
					if (u == wu + 1 && v == vm) {
						continue;
					}
					BlockState s = switch (p.hash(u, 11, v, 4)) {
						case 0 -> b(Blocks.MOSSY_STONE_BRICKS);
						case 1 -> b(Blocks.COBBLESTONE);
						default -> b(Blocks.STONE_BRICKS);
					};
					set(u, 0, v, s);
				}
			}
			set(wu + 1, 0, vm, b(Blocks.WATER));
			set(wu + 1, 1, vm, b(Blocks.LILY_PAD));
			for (int y = 1; y <= 3; y++) {
				set(wu, y, vm, b(wood.fence()));
				set(wu + 2, y, vm, b(wood.fence()));
			}
			roof(wu, wu + 2, vf);
			p.put(c, wu + 1, 3, vm, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
		}

		void compost(int cu) {
			if (full) {
				int vm = d - 3;
				int vb = d - 2;
				for (int u = cu; u <= cu + 2; u++) {
					set(u, 0, vb, composter(u, vb));
				}
				set(cu, 0, vm, b(Blocks.HAY_BLOCK));
				set(cu + 1, 0, vm, b(Blocks.POTTED_FERN));
				set(cu + 2, 0, vm, cauldron());
			} else {
				int v = d - 2;
				for (int u = cu; u <= cu + 2; u++) {
					set(u, 0, v, composter(u, v));
				}
			}
		}

		void bench(int bu) {
			int v = d - 2;
			set(bu, 0, v, b(wood.log()));
			set(bu, 1, v, b(wood.log()));
			set(bu, 2, v, b(wood.log()));
			set(bu, 3, v, b(wood.log()));
			set(bu + 4, 0, v, b(wood.log()));
			set(bu + 4, 1, v, b(wood.log()));
			set(bu + 4, 2, v, b(wood.log()));
			set(bu + 4, 3, v, b(wood.log()));
			boolean flip = p.hash(bu, 8, 0, 2) == 1;
			set(bu + 1, 0, v, flip ? chest() : composter(bu + 1, v));
			set(bu + 2, 0, v, barrel());
			set(bu + 3, 0, v, flip ? composter(bu + 3, v) : chest());
			set(flip ? bu : bu + 4, 3, v, b(Blocks.LANTERN));
			for (int u = bu; u <= bu + 4; u++) {
				set(u, 4, v, stairs(wood.stairs(), Direction.NORTH));
			}
		}

		void cistern(int cu) {
			int v = d - 2;
			set(cu, 0, v, barrel());
			set(cu + 1, 0, v, cauldron());
			set(cu + 2, 0, v, p.hash(cu, 12, v, 2) == 0 ? b(Blocks.POTTED_FERN) : b(Blocks.POTTED_RED_TULIP));
		}
	}
}
