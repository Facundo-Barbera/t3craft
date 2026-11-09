package dev.t3craft.tools.hqrender;

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
 * kit/paper-lantern: the paper-lantern family in the places the citadel puts it, so its look can be judged without a
 * live game. {@code --lanterns} draws four stations, left to right, each 8 blocks wide on a cherry deck:
 *
 * <ol>
 *   <li><b>stand</b>: two standing andons (the 1-block lantern) either side of a stone path;</li>
 *   <li><b>hang</b>: three hanging chochin, paper / rose / vermilion, under a cherry beam on two posts;</li>
 *   <li><b>wall</b>: three bracket lanterns on cherry pillars, one per tone, with panels between;</li>
 *   <li><b>tall</b>: two plaza lanterns (2 blocks) either side of a stone path, planters beyond.</li>
 * </ol>
 *
 * The family's models are put in the scene as model files ({@link Scene#setModel}), not through blockstates, so the
 * gallery shows blocks the daemon's registry may not have yet (kernel classes load once). Views (pick one with
 * {@code --only}): {@code all}, {@code front}, {@code stand}, {@code hang}, {@code wall}, {@code tall},
 * {@code far_50}, {@code far_100}. Each station view is one variant: ask for it alone with {@code --scale 2}.
 */
final class LanternViews {
	private static final int STATION = 8;
	private static final String[] TONES = {"paper", "rose", "vermilion"};
	private static final String M = "t3craft:block/paper_lantern";

	private LanternViews() {
	}

	/** Y rotation of the model for a block facing {@code d} (the models are drawn facing north). */
	private static int yRot(Direction d) {
		return switch (d) {
			case EAST -> 90;
			case SOUTH -> 180;
			case WEST -> 270;
			default -> 0;
		};
	}

	static void run(Path out, double scaleMul, int ss, List<String> only) throws Exception {
		if (T3Blocks.content() == null) {
			throw new IllegalStateException("--lanterns needs the blocks module (do not pass --vanilla)");
		}
		Files.createDirectories(out);
		HqKit kit = HqKit.active();
		BlockModels models = new BlockModels();
		int x2 = STATION * 4;
		Scene scene = new Scene(models, -3, -2, -6, x2 + 2, 10, 9);
		BlockState floor = kit.state(HqKit.Role.FLOOR, Direction.NORTH);
		BlockState stone = kit.state(HqKit.Role.FLOOR_ACCENT, Direction.NORTH);
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		for (int x = scene.minX; x < scene.minX + scene.sx; x++) {
			for (int z = scene.minZ; z < scene.minZ + scene.sz; z++) {
				scene.set(new BlockPos(x, -1, z), dirt);
				boolean deck = x >= -1 && x < x2 && z >= -3 && z <= 6;
				scene.set(new BlockPos(x, 0, z), deck ? floor : grass);
			}
		}
		// stone paths down the middle of stations 1, 2 and 4 (the way a player walks)
		for (int s : new int[] {0, 1, 3}) {
			for (int x = s * STATION + 3; x <= s * STATION + 4; x++) {
				for (int z = -3; z <= 6; z++) {
					scene.set(new BlockPos(x, 0, z), stone);
				}
			}
		}

		// 1. stand: two andons on the path's edges, on a plinth of the deck
		int s0 = 0;
		scene.setModel(new BlockPos(s0 + 2, 1, 2), M, 0);
		scene.setModel(new BlockPos(s0 + 5, 1, 2), M, 0);

		// 2. hang: two cherry posts, a beam across, three chochin hanging from it
		int s1 = STATION;
		for (int y = 1; y <= 6; y++) {
			scene.set(new BlockPos(s1, y, 0), kit.state(HqKit.Role.BEAM, Direction.UP));
			scene.set(new BlockPos(s1 + 7, y, 0), kit.state(HqKit.Role.BEAM, Direction.UP));
		}
		scene.set(new BlockPos(s1, 7, 0), kit.state(HqKit.Role.BEAM_CAP, Direction.NORTH));
		scene.set(new BlockPos(s1 + 7, 7, 0), kit.state(HqKit.Role.BEAM_CAP, Direction.NORTH));
		for (int x = s1; x <= s1 + 7; x++) {
			scene.set(new BlockPos(x, 6, 0), kit.state(HqKit.Role.BEAM, Direction.EAST));
		}
		// the middle one hangs on a chain, the others straight from the beam (the two ways the citadel hangs them)
		for (int i = 0; i < 3; i++) {
			int y = i == 1 ? 4 : 5;
			scene.setModel(new BlockPos(s1 + 1 + i * 2, y, 0), M + "_hanging_" + TONES[i], 0);
		}
		scene.set(new BlockPos(s1 + 3, 5, 0), Blocks.IRON_CHAIN.defaultBlockState());

		// 3. wall: three cherry pillars with panels between, a bracket lantern on the front of each, at head height + 1
		int s2 = 2 * STATION;
		BlockState panel = kit.state(HqKit.Role.WALL_PANEL, Direction.NORTH);
		for (int i = 0; i < 3; i++) {
			int px = s2 + 1 + i * 3;
			for (int y = 1; y <= 5; y++) {
				scene.set(new BlockPos(px, y, 0), kit.state(HqKit.Role.BEAM, Direction.UP));
				if (i < 2) {
					for (int dx = 1; dx <= 2; dx++) {
						scene.set(new BlockPos(px + dx, y, 0), panel);
					}
				}
			}
			scene.set(new BlockPos(px, 6, 0), kit.state(HqKit.Role.BEAM_CAP, Direction.NORTH));
			scene.setModel(new BlockPos(px, 3, 1), M + "_wall_" + TONES[i], yRot(Direction.SOUTH));
		}

		// 4. tall: plaza lanterns (lower + upper), planters beyond
		int s3 = 3 * STATION;
		for (int x : new int[] {s3 + 2, s3 + 5}) {
			scene.setModel(new BlockPos(x, 1, 2), M + "_tall_lower", 0);
			scene.setModel(new BlockPos(x, 2, 2), M + "_tall_upper", 0);
		}
		scene.set(new BlockPos(s3 + 1, 1, -1), kit.state(HqKit.Role.GARDEN_PLANTER, Direction.NORTH));
		scene.set(new BlockPos(s3 + 6, 1, -1), kit.state(HqKit.Role.GARDEN_PLANTER, Direction.NORTH));

		Renderer.Clip all = new Renderer.Clip(scene.minX, -1, scene.minZ, scene.minX + scene.sx - 1, 10, scene.minZ + scene.sz - 1);
		double iso = Math.min(40, 1500.0 / ((scene.sx + scene.sz) * 0.75)) * scaleMul;
		double near = 62 * scaleMul;
		List<Object[]> views = new ArrayList<>();
		views.add(new Object[] {"all", "the four lanterns, from the south-east", all, new Renderer.Camera(35, 26, iso), 1});
		views.add(new Object[] {"front", "from the front, eye level", all, new Renderer.Camera(8, 10, iso), 1});
		views.add(new Object[] {"stand", "standing andon, two at a path", station(0, -2, 5), new Renderer.Camera(30, 20, near), 1});
		views.add(new Object[] {"hang", "hanging chochin, three tones", station(1, -2, 3), new Renderer.Camera(25, 14, near), 1});
		views.add(new Object[] {"wall", "bracket lantern on a pillar, three tones", station(2, -2, 3), new Renderer.Camera(20, 14, near), 1});
		views.add(new Object[] {"tall", "plaza lantern, 2 blocks", station(3, -2, 5), new Renderer.Camera(30, 16, near * 0.8), 1});
		// far: about 50 and 100 blocks away on a 1080p screen (70° FOV: 1080 / (1.4 d) px per block), enlarged ×4
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
				img = enlarge(img, up);
			}
			HqRender.caption(img, "paper lanterns, " + v[1]);
			Path f = out.resolve("lanterns_" + suffix + ".png");
			Png.write(img, f);
			System.out.printf(Locale.ROOT, "%-28s %5dx%-5d %6d KB  %.1fs%n", f.getFileName(), img.getWidth(), img.getHeight(), Files.size(f) / 1024,
					(System.nanoTime() - t0) / 1e9);
		}
		StringBuilder rep = new StringBuilder("paper lantern gallery, left to right: standing andon (2), hanging chochin (paper, rose, vermilion), "
				+ "wall bracket (paper, rose, vermilion), tall plaza lantern (2).\n");
		for (String p : models.problems) {
			rep.append("note: ").append(p).append('\n');
		}
		System.out.print(rep);
		Files.writeString(out.resolve("report.txt"), rep.toString());
	}

	/** The box of station {@code s}: its 8 columns, from {@code zMin} (back) to {@code zMax} (front, towards the viewer), 9 blocks high. */
	private static Renderer.Clip station(int s, int zMin, int zMax) {
		int x0 = s * STATION;
		return new Renderer.Clip(x0, -1, zMin, x0 + STATION - 1, 8, zMax);
	}

	/** Nearest-neighbour enlargement (each pixel becomes a {@code k × k} square). */
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
