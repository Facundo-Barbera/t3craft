package dev.t3craft.tools.hqrender;

import dev.t3craft.hq.PlanProbe;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Plot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Walkability audit of one catalog build (the gallery's {@code report.txt}, "walk:" lines). A person walks the
 * build with the navigation rules of {@code PlanNav} (0.6 × 1.8 body, collision boxes, moves of at most one block,
 * a stair is stood on from the cell above it) from the corner of the path ring, doors and gates read as open.
 * It reports, in coordinates relative to the pad ({@code x - x0, y - 1, z - z0}, world axes):
 * <ul>
 * <li><b>reach</b>: feet cells reached, the floor levels among them, how many are under a roof (an interior);</li>
 * <li><b>stairs</b>: bottom-half stairs whose stand cell was reached, and the standable ones that were not (a
 *     flight nobody can start, or whose first step is a jump; roof stairs show up here too: read by height);</li>
 * <li><b>sealed</b>: standable cells under a roof that no walk reached (a floor shut in, with no stair to it);</li>
 * <li><b>low</b>: reached cells under a roof with less than 2.85 blocks of headroom, doorways aside;</li>
 * <li><b>fire</b>: a campfire, fire or lava with water within 2 cells.</li>
 * </ul>
 * Lines that start with {@code FLAG} are the ones to look at.
 */
final class WalkAudit {
	private WalkAudit() {
	}

	private static boolean solid(Map<Long, BlockState> m, int x, int y, int z) {
		BlockState s = m.get(BlockPos.asLong(x, y, z));
		return s != null && !s.isAir() && !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	private static boolean water(BlockState s) {
		return s.getBlock() == Blocks.WATER || s.hasProperty(BlockStateProperties.WATERLOGGED) && s.getValue(BlockStateProperties.WATERLOGGED);
	}

	static List<String> run(Plot p, CitadelCanvas canvas, int gap, StringBuilder dump) {
		int x1 = p.x0();
		int z1 = p.z0();
		int x2 = x1 + p.sizeX() - 1;
		int z2 = z1 + p.sizeZ() - 1;
		int top = p.y0() + p.h() + 2;
		Map<Long, BlockState> m = new HashMap<>();
		for (Map.Entry<Long, BlockState> e : canvas.cells.entrySet()) {
			BlockState s = e.getValue();
			Block b = s.getBlock();
			if (s.isAir() || b instanceof DoorBlock || b instanceof FenceGateBlock) {
				continue; // doors and gates are open for a walker
			}
			m.put(e.getKey(), s);
		}
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		for (int x = x1 - gap; x <= x2 + gap; x++) {
			for (int z = z1 - gap; z <= z2 + gap; z++) {
				m.putIfAbsent(BlockPos.asLong(x, 0, z), grass); // a full block even on the path ring: PlanNav models a stair only by its high half, so a 15/16 path under the first step reads as a 1.06 jump
			}
		}
		PlanProbe.Walk walk = PlanProbe.walk(m::get);
		Set<BlockPos> reach = walk.reachable(new BlockPos(x1 - 1, 1, z1 - 1));
		List<String> out = new ArrayList<>();

		// ---- reach and interiors
		TreeMap<Integer, Integer> levels = new TreeMap<>();
		int covered = 0;
		int low = 0;
		String lowAt = "";
		int maxY = 0;
		for (BlockPos q : reach) {
			if (q.getX() < x1 || q.getX() > x2 || q.getZ() < z1 || q.getZ() > z2) {
				continue;
			}
			int rel = q.getY() - 1;
			dump.append(String.format(Locale.ROOT, "R %d %d %d %.1f %.1f%n", q.getX() - x1, rel, q.getZ() - z1, walk.floor(q.getX(), q.getY(), q.getZ()) - 1.0,
					Math.min(99.0, walk.clearance(q.getX(), q.getY(), q.getZ(), 8))));
			maxY = Math.max(maxY, rel);
			levels.merge((int) Math.round(walk.floor(q.getX(), q.getY(), q.getZ()) - 1.0), 1, Integer::sum);
			double clear = walk.clearance(q.getX(), q.getY(), q.getZ(), 8);
			if (Double.isInfinite(clear)) {
				continue;
			}
			covered++;
			if (clear < 2.85) {
				int yh = (int) Math.floor(walk.floor(q.getX(), q.getY(), q.getZ())) + 1;
				boolean doorway = solid(m, q.getX() - 1, yh, q.getZ()) && solid(m, q.getX() + 1, yh, q.getZ())
						|| solid(m, q.getX(), yh, q.getZ() - 1) && solid(m, q.getX(), yh, q.getZ() + 1);
				if (!doorway) {
					if (low++ == 0) {
						lowAt = String.format(Locale.ROOT, "(%d,%d,%d) %.1f", q.getX() - x1, rel, q.getZ() - z1, clear);
					}
				}
			}
		}
		StringBuilder lv = new StringBuilder();
		levels.forEach((k, v) -> lv.append(lv.length() == 0 ? "" : " ").append('y').append(k).append(':').append(v));
		out.add("reach " + reach.size() + ", inside the box: " + lv + ", under a roof " + covered + ", highest foot level y" + maxY);

		// ---- stairs
		int stairs = 0;
		int stairsReached = 0;
		List<String> unreached = new ArrayList<>();
		for (Map.Entry<Long, BlockState> e : m.entrySet()) {
			BlockState s = e.getValue();
			if (!(s.getBlock() instanceof StairBlock) || s.getValue(StairBlock.HALF) != Half.BOTTOM) {
				continue;
			}
			BlockPos q = BlockPos.of(e.getKey());
			stairs++;
			dump.append(String.format(Locale.ROOT, "S %d %d %d %s %d %s%n", q.getX() - x1, q.getY() - 1, q.getZ() - z1, s.getValue(StairBlock.FACING).getSerializedName().charAt(0),
					reach.contains(q.above()) ? 1 : 0, Double.isNaN(walk.floor(q.getX(), q.getY() + 1, q.getZ())) ? "blocked" : "standable"));
			if (reach.contains(q.above())) {
				stairsReached++;
			} else if (!Double.isNaN(walk.floor(q.getX(), q.getY() + 1, q.getZ()))) {
				unreached.add(String.format(Locale.ROOT, "(%d,%d,%d)", q.getX() - x1, q.getY() - 1, q.getZ() - z1));
			}
		}
		unreached.sort(null);
		StringBuilder un = new StringBuilder();
		TreeMap<Integer, Integer> unByY = new TreeMap<>();
		for (Map.Entry<Long, BlockState> e : m.entrySet()) {
			BlockState s = e.getValue();
			if (s.getBlock() instanceof StairBlock && s.getValue(StairBlock.HALF) == Half.BOTTOM) {
				BlockPos q = BlockPos.of(e.getKey());
				if (!reach.contains(q.above()) && !Double.isNaN(walk.floor(q.getX(), q.getY() + 1, q.getZ()))) {
					unByY.merge(q.getY() - 1, 1, Integer::sum);
				}
			}
		}
		unByY.forEach((k, v) -> un.append(un.length() == 0 ? "" : " ").append('y').append(k).append(':').append(v));
		out.add("stairs " + stairsReached + "/" + stairs + " reached; standable but not reached by height: " + (un.length() == 0 ? "none" : un)
				+ (unreached.isEmpty() ? "" : " e.g. " + String.join(" ", unreached.subList(0, Math.min(6, unreached.size())))));

		// ---- sealed floors under a roof
		TreeMap<Integer, Integer> sealed = new TreeMap<>();
		String sealedAt = "";
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				for (int y = 1; y <= top; y++) {
					if (reach.contains(new BlockPos(x, y, z))) {
						continue;
					}
					double f = walk.floor(x, y, z);
					if (Double.isNaN(f) || Double.isInfinite(walk.clearance(x, y, z, 8))) {
						continue;
					}
					dump.append(String.format(Locale.ROOT, "U %d %d %d %.1f %.1f%n", x - x1, y - 1, z - z1, f - 1.0, Math.min(99.0, walk.clearance(x, y, z, 8))));
					if (sealed.merge(y - 1, 1, Integer::sum) == 1 && sealedAt.isEmpty()) {
						sealedAt = String.format(Locale.ROOT, "(%d,%d,%d)", x - x1, y - 1, z - z1);
					}
				}
			}
		}
		if (!sealed.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			sealed.forEach((k, v) -> sb.append(sb.length() == 0 ? "" : " ").append('y').append(k).append(':').append(v));
			out.add("FLAG sealed: standable cells under a roof that nobody reaches " + sb + " first " + sealedAt);
		}
		if (low > 0) {
			out.add("FLAG low: " + low + " reached cells under a roof with under 2.85 blocks of headroom, doorways aside, first " + lowAt);
		}

		for (Map.Entry<Long, BlockState> e : canvas.cells.entrySet()) {
			if (e.getValue().getBlock() instanceof DoorBlock && e.getValue().getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
				BlockPos q = BlockPos.of(e.getKey());
				dump.append(String.format(Locale.ROOT, "D %d %d %d%n", q.getX() - x1, q.getY() - 1, q.getZ() - z1));
			}
		}

		// ---- fire next to water
		for (Map.Entry<Long, BlockState> e : m.entrySet()) {
			Block b = e.getValue().getBlock();
			if (b != Blocks.CAMPFIRE && b != Blocks.SOUL_CAMPFIRE && b != Blocks.FIRE && b != Blocks.SOUL_FIRE && b != Blocks.LAVA) {
				continue;
			}
			BlockPos q = BlockPos.of(e.getKey());
			boolean wet = water(e.getValue());
			for (int dx = -2; dx <= 2 && !wet; dx++) {
				for (int dz = -2; dz <= 2 && !wet; dz++) {
					for (int dy = -1; dy <= 1 && !wet; dy++) {
						BlockState n = m.get(BlockPos.asLong(q.getX() + dx, q.getY() + dy, q.getZ() + dz));
						wet = n != null && water(n);
					}
				}
			}
			if (wet) {
				out.add(String.format(Locale.ROOT, "FLAG fire: %s at (%d,%d,%d) with water within 2 cells", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath(),
						q.getX() - x1, q.getY() - 1, q.getZ() - z1));
			}
		}
		return out;
	}
}
