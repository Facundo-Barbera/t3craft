package dev.t3craft.tools.hqrender;

import dev.t3craft.blocks.DecisionPodiumBlock;
import dev.t3craft.blocks.LampStatus;
import dev.t3craft.blocks.MergeState;
import dev.t3craft.blocks.MergeStationBlock;
import dev.t3craft.blocks.StatusLampBlock;
import dev.t3craft.blocks.T3Blocks;
import dev.t3craft.hq.HqKit;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * kit/office-blocks-review: the control-room blocks in every state, on a gallery deck. {@code --office-blocks} draws,
 * front to back (the viewer is south):
 *
 * <ol>
 *   <li><b>podium</b>: the decision podium in its six states (off, idle, working, needs you, error, done);</li>
 *   <li><b>station / console</b>: the merge station in none / open / draft / merged, then the console terminal seen from the
 *       front, the east side and the west side (the keyboard's tilt);</li>
 *   <li><b>glow</b>: against a stone wall, strips (low and high, warm and cool, a run of five that must join) and panels (wall
 *       and ceiling), with a dark beam to hang the high strips under.</li>
 * </ol>
 *
 * Views ({@code --only}): {@code all}, {@code front}, {@code podium}, {@code station}, {@code console}, {@code glow},
 * {@code far_50}, {@code far_100}.
 */
final class OfficeBlockViews {
	private static final LampStatus[] PODIUM = {LampStatus.OFF, LampStatus.IDLE, LampStatus.WORKING, LampStatus.ATTENTION, LampStatus.ERROR, LampStatus.DONE};
	private static final int STRIDE = 3;
	private static final int Z_PODIUM = 3;
	private static final int Z_STATION = -3;
	private static final int Z_GLOW = -9;

	private OfficeBlockViews() {
	}

	static void run(Path out, double scaleMul, int ss, List<String> only) throws Exception {
		T3Blocks.Content k = T3Blocks.content();
		if (k == null) {
			throw new IllegalStateException("--office-blocks needs the blocks module (do not pass --vanilla)");
		}
		Files.createDirectories(out);
		HqKit kit = HqKit.active();
		BlockModels models = new BlockModels();
		int x2 = 7 * STRIDE;
		Scene scene = new Scene(models, -3, -2, -13, x2 + 3, 9, 8);
		BlockState floor = kit.state(HqKit.Role.FLOOR, Direction.NORTH);
		BlockState stone = kit.state(HqKit.Role.FLOOR_ACCENT, Direction.NORTH);
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		for (int x = scene.minX; x < scene.minX + scene.sx; x++) {
			for (int z = scene.minZ; z < scene.minZ + scene.sz; z++) {
				scene.set(new BlockPos(x, -1, z), dirt);
				boolean deck = x >= -1 && x <= x2 && z >= -12 && z <= 6;
				scene.set(new BlockPos(x, 0, z), deck ? (z == 0 || z == -6 ? stone : floor) : Blocks.GRASS_BLOCK.defaultBlockState());
			}
		}
		// podiums: against the wall of a low plinth row so the front is toward the viewer (south)
		for (int i = 0; i < PODIUM.length; i++) {
			scene.set(new BlockPos(i * STRIDE, 1, Z_PODIUM),
					k.decisionPodiumBlock().defaultBlockState().setValue(DecisionPodiumBlock.FACING, Direction.SOUTH)
							.setValue(StatusLampBlock.STATUS, PODIUM[i]));
		}
		// merge stations, then consoles (front, east side, west side)
		MergeState[] pr = {MergeState.NONE, MergeState.OPEN, MergeState.DRAFT, MergeState.MERGED};
		for (int i = 0; i < pr.length; i++) {
			scene.set(new BlockPos(i * STRIDE, 1, Z_STATION),
					k.mergeStationBlock().defaultBlockState().setValue(MergeStationBlock.FACING, Direction.SOUTH).setValue(MergeStationBlock.PR, pr[i]));
		}
		Direction[] cf = {Direction.SOUTH, Direction.EAST, Direction.WEST};
		for (int i = 0; i < cf.length; i++) {
			scene.set(new BlockPos((4 + i) * STRIDE + 1, 1, Z_STATION), kit.console(cf[i]));
		}
		// glow: a wall at z = Z_GLOW - 1, a ceiling slice over the middle, strips and panels
		BlockState wall = kit.state(HqKit.Role.WALL_PANEL, Direction.NORTH);
		BlockState dark = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
		for (int x = -1; x <= x2; x++) {
			for (int y = 1; y <= 6; y++) {
				scene.set(new BlockPos(x, y, Z_GLOW - 1), wall);
			}
		}
		// low strips: warm, cool, then a run of 5 warm (must join), a run of 5 cool
		int z = Z_GLOW;
		scene.set(new BlockPos(0, 1, z), kit.glowStrip(Direction.SOUTH, false, false));
		scene.set(new BlockPos(2, 1, z), kit.glowStrip(Direction.SOUTH, false, true));
		for (int i = 0; i < 5; i++) {
			scene.set(new BlockPos(4 + i, 1, z), kit.glowStrip(Direction.SOUTH, false, false));
			scene.set(new BlockPos(10 + i, 1, z), kit.glowStrip(Direction.SOUTH, false, true));
		}
		// high strips under a dark beam (the way the office hangs them under the screens)
		for (int x = 0; x <= 14; x++) {
			scene.set(new BlockPos(x, 4, z - 1), dark);
		}
		scene.set(new BlockPos(0, 3, z), kit.glowStrip(Direction.SOUTH, true, false));
		scene.set(new BlockPos(2, 3, z), kit.glowStrip(Direction.SOUTH, true, true));
		for (int i = 0; i < 5; i++) {
			scene.set(new BlockPos(4 + i, 3, z), kit.glowStrip(Direction.SOUTH, true, false));
			scene.set(new BlockPos(10 + i, 3, z), kit.glowStrip(Direction.SOUTH, true, true));
		}
		// panels: in the wall (facing south), and a ceiling slice with panels facing down
		scene.set(new BlockPos(16, 2, z - 1), kit.glowPanel(Direction.SOUTH, false));
		scene.set(new BlockPos(17, 2, z - 1), kit.glowPanel(Direction.SOUTH, true));
		for (int x = 15; x <= x2; x++) {
			for (int dz = 0; dz <= 3; dz++) {
				scene.set(new BlockPos(x, 6, z + dz), dark);
			}
		}
		scene.set(new BlockPos(16, 6, z + 1), kit.glowPanel(Direction.DOWN, false));
		scene.set(new BlockPos(18, 6, z + 1), kit.glowPanel(Direction.DOWN, true));
		scene.set(new BlockPos(20, 6, z + 1), kit.glowPanel(Direction.DOWN, true));

		Renderer.Clip all = new Renderer.Clip(scene.minX, -1, scene.minZ, scene.minX + scene.sx - 1, 9, scene.minZ + scene.sz - 1);
		double iso = Math.min(40, 1500.0 / ((scene.sx + scene.sz) * 0.75)) * scaleMul;
		double near = 80 * scaleMul;
		List<Object[]> views = new ArrayList<>();
		views.add(new Object[] {"all", "everything, from the south-east", all, new Renderer.Camera(35, 26, iso), 1});
		views.add(new Object[] {"front", "from the front, eye level", all, new Renderer.Camera(8, 10, iso), 1});
		views.add(new Object[] {"podium", "decision podium: off, idle, working, needs you, error, done",
				new Renderer.Clip(-1, 0, Z_PODIUM - 2, 5 * STRIDE + 2, 4, Z_PODIUM + 2), new Renderer.Camera(25, 22, near * 0.9), 1});
		views.add(new Object[] {"station", "merge station: none, open, draft, merged",
				new Renderer.Clip(-1, 0, Z_STATION - 2, 3 * STRIDE + 2, 4, Z_STATION + 2), new Renderer.Camera(25, 22, near), 1});
		views.add(new Object[] {"console", "console terminal: front, east side, west side",
				new Renderer.Clip(4 * STRIDE - 1, 0, Z_STATION - 2, 6 * STRIDE + 3, 4, Z_STATION + 2), new Renderer.Camera(30, 24, near * 1.2), 1});
		views.add(new Object[] {"glow", "glow strips (low/high, warm/cool) and panels",
				new Renderer.Clip(-1, 0, Z_GLOW - 2, x2, 7, Z_GLOW + 3), new Renderer.Camera(22, 18, near * 0.55), 1});
		views.add(new Object[] {"profile", "console terminal in profile (keyboard tilt), seen from the east",
				new Renderer.Clip(4 * STRIDE + 1, 0, Z_STATION, 4 * STRIDE + 1, 3, Z_STATION), new Renderer.Camera(90, 6, near * 4), 1});
		views.add(new Object[] {"ceiling", "ceiling panels and wall panels seen from below",
				new Renderer.Clip(14, 2, Z_GLOW - 1, x2, 7, Z_GLOW + 3), new Renderer.Camera(20, -28, near * 0.8), 1});
		views.add(new Object[] {"close", "podium (needs you), merge station (open) and console at eye level, ~4 blocks",
				new Renderer.Clip(-1, 0, Z_PODIUM - 1, 5 * STRIDE + 1, 3, Z_PODIUM + 1), new Renderer.Camera(12, 8, near * 1.1), 1});
		views.add(new Object[] {"far_50", "from ~50 blocks (15 px/block, shown x4)", all, new Renderer.Camera(35, 20, 15), 4});
		views.add(new Object[] {"far_100", "from ~100 blocks (8 px/block, shown x4)", all, new Renderer.Camera(35, 20, 8), 4});
		for (Object[] v : views) {
			String suffix = (String) v[0];
			if (!only.isEmpty() && only.stream().noneMatch(o -> o.equals(suffix))) {
				continue;
			}
			Renderer.Clip clip = (Renderer.Clip) v[2];
			Renderer.Camera cam = (Renderer.Camera) v[3];
			int up = (Integer) v[4];
			long t0 = System.nanoTime();
			BufferedImage img = new Renderer(scene, clip, cam).render(cam.scale(), Math.max(1, ss));
			if (up > 1) {
				BufferedImage big = new BufferedImage(img.getWidth() * up, img.getHeight() * up, BufferedImage.TYPE_INT_ARGB);
				for (int y = 0; y < big.getHeight(); y++) {
					for (int x = 0; x < big.getWidth(); x++) {
						big.setRGB(x, y, img.getRGB(x / up, y / up));
					}
				}
				img = big;
			}
			HqRender.caption(img, "office blocks, " + v[1]);
			Path f = out.resolve("office_blocks_" + suffix + ".png");
			Png.write(img, f);
			System.out.printf(Locale.ROOT, "%-30s %5dx%-5d %6d KB  %.1fs%n", f.getFileName(), img.getWidth(), img.getHeight(), Files.size(f) / 1024,
					(System.nanoTime() - t0) / 1e9);
		}
		StringBuilder rep = new StringBuilder("office blocks gallery: podiums (z=" + Z_PODIUM + "), merge stations + consoles (z=" + Z_STATION + "), glow (z=" + Z_GLOW + ")\n");
		for (String p : models.problems) {
			rep.append("note: ").append(p).append('\n');
		}
		System.out.print(rep);
		Files.writeString(out.resolve("report.txt"), rep.toString());
	}
}
