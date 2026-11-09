package dev.t3craft.tools.hqrender;

import dev.t3craft.blocks.LampStatus;
import dev.t3craft.blocks.ScreenRect;
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
 * kit/monitor: the monitor block the way the HQ uses it, to judge the frame (bezel, chin, foot, continuous
 * frame across a wall) without a live game. {@code --monitors}. The headless renderer draws block models
 * only: the text of the screen (the block entity renderer) is NOT drawn, so the glass is the plain texture.
 *
 * <p>Scene (everything faces south, towards the camera): a control-room wall of 6×3 monitors on a stone
 * brick wall, three 3×2 desk screens (standing desk + status lamp), a lone 1×1 monitor and a 2×1 pair.
 * Views: {@code monitors_iso_se} (everything), {@code monitors_front} (eye level), {@code monitors_wall}
 * (the wall at a low angle from the left: frame depth and continuity), {@code monitors_desks} (closeup of the
 * desks), {@code monitors_far_*} (a few pixels per block, enlarged ×4).
 */
final class MonitorViews {
	private MonitorViews() {
	}

	/** A w×h screen facing south with its top-left cell at (x, top, z). */
	private static void wall(Scene scene, T3Blocks.Content k, int x, int top, int z, int w, int h) {
		for (int c = 0; c < w; c++) {
			for (int r = 0; r < h; r++) {
				ScreenRect.Sides sides = new ScreenRect.Sides(r > 0, r < h - 1, c > 0, c < w - 1);
				scene.set(new BlockPos(x + c, top - r, z), k.monitor(Direction.SOUTH, sides));
			}
		}
	}

	static void run(Path out, double scaleMul, int ss, List<String> only) throws Exception {
		T3Blocks.Content k = T3Blocks.content();
		if (k == null) {
			throw new IllegalStateException("--monitors needs the blocks module (do not pass --vanilla)");
		}
		Files.createDirectories(out);
		HqKit kit = HqKit.active();
		BlockModels models = new BlockModels();
		int x2 = 25;
		Scene scene = new Scene(models, -3, -2, -6, x2 + 2, 8, 9);
		BlockState floor = kit.state(HqKit.Role.FLOOR, Direction.NORTH);
		BlockState stone = kit.state(HqKit.Role.FLOOR_ACCENT, Direction.NORTH);
		for (int x = scene.minX; x < scene.minX + scene.sx; x++) {
			for (int z = scene.minZ; z < scene.minZ + scene.sz; z++) {
				scene.set(new BlockPos(x, -1, z), Blocks.DIRT.defaultBlockState());
				scene.set(new BlockPos(x, 0, z), (x >= -1 && x <= x2 && z >= -5 && z <= 7) ? (z == 3 ? stone : floor) : Blocks.GRASS_BLOCK.defaultBlockState());
			}
		}
		// the wall they hang on (north of them: the screens face south)
		for (int x = -1; x <= x2; x++) {
			for (int y = 1; y <= 6; y++) {
				scene.set(new BlockPos(x, y, -1), Blocks.STONE_BRICKS.defaultBlockState());
				scene.set(new BlockPos(x, y, -2), Blocks.STONE_BRICKS.defaultBlockState());
			}
		}
		// control room wall: 6×3, bottom row on the floor line (y 1), in front of the stone wall
		wall(scene, k, 0, 3, 0, 6, 3);
		// three desk screens, 3×2, on standing desks, lamp on top
		LampStatus[] lamps = {LampStatus.WORKING, LampStatus.ATTENTION, LampStatus.ERROR};
		for (int i = 0; i < 3; i++) {
			int x0 = 8 + i * 5;
			for (int c = 0; c < 3; c++) {
				scene.set(new BlockPos(x0 + c, 1, 0), kit.state(HqKit.Role.DESK, Direction.SOUTH));
			}
			wall(scene, k, x0, 3, 0, 3, 2);
			scene.set(new BlockPos(x0 + 1, 4, 0), k.lamp().setValue(StatusLampBlock.STATUS, lamps[i]));
		}
		// a lone monitor on a desk, and a 2×1 pair
		for (int x = 21; x <= 22; x++) {
			scene.set(new BlockPos(x, 1, 0), kit.state(HqKit.Role.DESK, Direction.SOUTH));
		}
		wall(scene, k, 21, 2, 0, 2, 1);
		scene.set(new BlockPos(24, 1, 0), kit.state(HqKit.Role.DESK, Direction.SOUTH));
		scene.set(new BlockPos(24, 2, 0), k.monitor(Direction.SOUTH));

		Renderer.Clip all = new Renderer.Clip(scene.minX, -1, scene.minZ, scene.minX + scene.sx - 1, 7, scene.minZ + scene.sz - 1);
		Renderer.Clip wallClip = new Renderer.Clip(-2, 0, -3, 7, 6, 3);
		Renderer.Clip desks = new Renderer.Clip(7, 0, -3, 25, 5, 3);
		double iso = Math.min(48, 1300.0 / ((scene.sx + scene.sz) * 0.75)) * scaleMul;
		List<Object[]> views = new ArrayList<>();
		views.add(new Object[] {"iso_se", "from the south-east", all, new Renderer.Camera(35, 26, iso), 1});
		views.add(new Object[] {"front", "eye level, 7 blocks away", all, new Renderer.Camera(6, 10, iso), 1});
		views.add(new Object[] {"wall", "6x3 wall from the left, low: frame depth and continuity", wallClip, new Renderer.Camera(-38, 14, iso * 2.4), 1});
		views.add(new Object[] {"desks", "desk screens and the lone monitor, close", desks, new Renderer.Camera(28, 20, iso * 1.6), 1});
		views.add(new Object[] {"far_20", "as seen from ~20 blocks (38 px/block, x2)", all, new Renderer.Camera(20, 14, 38), 2});
		views.add(new Object[] {"far_50", "as seen from ~50 blocks (15 px/block, x4)", all, new Renderer.Camera(20, 14, 15), 4});
		for (Object[] v : views) {
			String suffix = (String) v[0];
			if (!only.isEmpty() && only.stream().noneMatch(suffix::contains)) {
				continue;
			}
			Renderer.Clip clip = (Renderer.Clip) v[2];
			Renderer.Camera cam = (Renderer.Camera) v[3];
			int up = (Integer) v[4];
			long t0 = System.nanoTime();
			BufferedImage img = new Renderer(scene, clip, cam).render(cam.scale(), Math.max(1, ss));
			if (up > 1) {
				img = enlarge(img, up);
			}
			HqRender.caption(img, "monitor, " + v[1]);
			Path f = out.resolve("monitors_" + suffix + ".png");
			Png.write(img, f);
			System.out.printf(Locale.ROOT, "%-28s %5dx%-5d %6d KB  %.1fs%n", f.getFileName(), img.getWidth(), img.getHeight(), Files.size(f) / 1024,
					(System.nanoTime() - t0) / 1e9);
		}
		StringBuilder rep = new StringBuilder("monitor gallery: 6x3 wall, three 3x2 desk screens with lamp, a lone 1x1, all facing south (screen text is not drawn headless)\n");
		for (String p : models.problems) {
			rep.append("note: ").append(p).append('\n');
		}
		System.out.print(rep);
		Files.writeString(out.resolve("report.txt"), rep.toString());
	}

	private static BufferedImage enlarge(BufferedImage src, int k) {
		BufferedImage dst = new BufferedImage(src.getWidth() * k, src.getHeight() * k, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < dst.getHeight(); y++) {
			for (int x = 0; x < dst.getWidth(); x++) {
				dst.setRGB(x, y, src.getRGB(x / k, y / k));
			}
		}
		return dst;
	}
}
