package dev.t3craft.tools.hqrender;

import dev.t3craft.blocks.LampStatus;
import dev.t3craft.blocks.ScreenRect;
import dev.t3craft.blocks.StatusLampBlock;
import dev.t3craft.blocks.T3Blocks;
import dev.t3craft.hq.HqKit;
import dev.t3craft.hq.plan.BuildPlan;
import dev.t3craft.hq.plan.Placement;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
 * kit/status-lamp: the status lamp in every state, the way the HQ uses it, so its look can be judged
 * without a live game (the plan always holds lamps {@code off}; the game sets the state).
 *
 * <ul>
 *   <li>{@code --lamps}: a gallery. Front row: one desk per state (standing desk, a 3×2 screen of real
 *       {@code t3craft:monitor}s, the lamp on top of the screen). Back row: the same lamps hanging from a
 *       chain under a beam (the hub beacon hangs like that). Views: {@code lamps_iso_se} (close),
 *       {@code lamps_front}, {@code lamps_closeup} (working / needs you / error) and
 *       {@code lamps_far_*} (the same scene at a few pixels per block, as a player sees it from about 50
 *       and 100 blocks away, then enlarged ×4 nearest so every pixel shows).</li>
 *   <li>{@code --lamp-demo} (any plan mode that goes through {@link #demo}): every block with a lamp
 *       {@code status} gets a demo state picked from its position, so desks and the beacon show lit in
 *       piece renders.</li>
 * </ul>
 */
final class LampViews {
	/** Order of the gallery, left to right. */
	static final LampStatus[] ORDER = {LampStatus.OFF, LampStatus.IDLE, LampStatus.WORKING, LampStatus.ATTENTION, LampStatus.ERROR, LampStatus.DONE};
	private static final String[] LABEL = {"off", "idle", "working", "needs you", "error", "done"};
	/** Desk-to-desk spacing along x. */
	private static final int STRIDE = 5;

	private LampViews() {
	}

	/** {@code --lamp-demo}: each lamp of the plan in a demo state (never off), stable per position. */
	static BuildPlan demo(BuildPlan plan) {
		return plan.replacing(p -> {
			BlockState s = p.state();
			if (!s.hasProperty(StatusLampBlock.STATUS)) {
				return p;
			}
			BlockPos r = p.rel();
			int h = Math.floorMod(r.getX() * 73 + r.getZ() * 31 + r.getY() * 17, 5);
			return new Placement(r, s.setValue(StatusLampBlock.STATUS, ORDER[1 + h]), p.be());
		});
	}

	static void run(Path out, double scaleMul, int ss, List<String> only) throws Exception {
		T3Blocks.Content k = T3Blocks.content();
		if (k == null) {
			throw new IllegalStateException("--lamps needs the blocks module (do not pass --vanilla)");
		}
		Files.createDirectories(out);
		HqKit kit = HqKit.active();
		int n = ORDER.length;
		int x2 = (n - 1) * STRIDE + 3;
		BlockModels models = new BlockModels();
		Scene scene = new Scene(models, -3, -2, -7, x2 + 2, 9, 8);
		BlockState floor = kit.state(HqKit.Role.FLOOR, Direction.NORTH);
		BlockState stone = kit.state(HqKit.Role.FLOOR_ACCENT, Direction.NORTH);
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		for (int x = scene.minX; x < scene.minX + scene.sx; x++) {
			for (int z = scene.minZ; z < scene.minZ + scene.sz; z++) {
				scene.set(new BlockPos(x, -1, z), dirt);
				boolean deck = x >= -1 && x <= x2 && z >= -5 && z <= 6;
				scene.set(new BlockPos(x, 0, z), deck ? (z == 2 ? stone : floor) : Blocks.GRASS_BLOCK.defaultBlockState());
			}
		}
		List<Object[]> labels = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			int x0 = i * STRIDE;
			// the desk: standing desk, a 3×2 screen facing the viewer (south), the lamp on its top middle
			for (int c = 0; c < 3; c++) {
				scene.set(new BlockPos(x0 + c, 1, 0), kit.state(HqKit.Role.DESK, Direction.SOUTH));
				for (int r = 0; r < 2; r++) {
					ScreenRect.Sides sides = new ScreenRect.Sides(r > 0, r < 1, c > 0, c < 2);
					scene.set(new BlockPos(x0 + c, 3 - r, 0), k.monitor(Direction.SOUTH, sides));
				}
			}
			scene.set(new BlockPos(x0 + 1, 4, 0), k.lamp().setValue(StatusLampBlock.STATUS, ORDER[i]));
			// the hanging one (beacon style), on a row behind the desks and above their screens
			scene.set(new BlockPos(x0 + 1, 6, -4), k.lamp().setValue(StatusLampBlock.STATUS, ORDER[i]));
			scene.set(new BlockPos(x0 + 1, 7, -4), Blocks.IRON_CHAIN.defaultBlockState());
			labels.add(new Object[] {LABEL[i], new double[] {x0 + 1.5, 1, 3.4}});
		}
		// the beam the lamps hang from, on two posts
		for (int x = -1; x <= x2; x++) {
			scene.set(new BlockPos(x, 8, -4), kit.state(HqKit.Role.BEAM, Direction.EAST));
		}
		for (int y = 1; y <= 7; y++) {
			scene.set(new BlockPos(-1, y, -4), kit.state(HqKit.Role.BEAM, Direction.UP));
			scene.set(new BlockPos(x2, y, -4), kit.state(HqKit.Role.BEAM, Direction.UP));
		}

		Renderer.Clip all = new Renderer.Clip(scene.minX, -1, scene.minZ, scene.minX + scene.sx - 1, 9, scene.minZ + scene.sz - 1);
		Renderer.Clip mid = new Renderer.Clip(2 * STRIDE - 1, -1, -2, 4 * STRIDE + 3, 6, 3);
		Renderer.Clip hang = new Renderer.Clip(-1, 4, -6, x2, 9, -3);
		double iso = Math.min(48, 1300.0 / ((scene.sx + scene.sz) * 0.75)) * scaleMul;
		List<Object[]> views = new ArrayList<>();
		views.add(new Object[] {"iso_se", "from the south-east", all, new Renderer.Camera(35, 28, iso), 1});
		views.add(new Object[] {"front", "from the front, eye level of a player 6 blocks away", all, new Renderer.Camera(8, 12, iso), 1});
		views.add(new Object[] {"closeup", "working, needs you, error up close", mid, new Renderer.Camera(28, 20, iso * 2.2), 1});
		views.add(new Object[] {"hang", "hanging from a chain (the hub beacon)", hang, new Renderer.Camera(25, 14, iso * 1.3), 1});
		// far: about 50 and 100 blocks away on a 1080p screen (70° FOV: 1080 / (1.4 d) px per block), enlarged ×4
		views.add(new Object[] {"far_50", "as seen from ~50 blocks (15 px/block, shown x4)", all, new Renderer.Camera(35, 20, 15), 4});
		views.add(new Object[] {"far_100", "as seen from ~100 blocks (8 px/block, shown x4)", all, new Renderer.Camera(35, 20, 8), 4});
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
			} else if (clip == all) {
				labels(img, clip, cam, labels);
			}
			HqRender.caption(img, "status lamp, " + v[1]);
			Path f = out.resolve("lamps_" + suffix + ".png");
			Png.write(img, f);
			System.out.printf(Locale.ROOT, "%-28s %5dx%-5d %6d KB  %.1fs%n", f.getFileName(), img.getWidth(), img.getHeight(), Files.size(f) / 1024,
					(System.nanoTime() - t0) / 1e9);
		}
		StringBuilder rep = new StringBuilder("status lamp gallery: front row on desks, back row hanging; left to right:");
		for (String l : LABEL) {
			rep.append(' ').append(l).append(',');
		}
		rep.setLength(rep.length() - 1);
		rep.append('\n');
		for (String p : models.problems) {
			rep.append("note: ").append(p).append('\n');
		}
		System.out.print(rep);
		Files.writeString(out.resolve("report.txt"), rep.toString());
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

	/** Each label at its scene point, projected like {@link Renderer} does (same math as the catalog's labels). */
	private static void labels(BufferedImage img, Renderer.Clip clip, Renderer.Camera cam, List<Object[]> labels) {
		try {
			double az = Math.toRadians(cam.azimuth());
			double el = Math.toRadians(Math.min(cam.elevation(), 89.9));
			double[] toCam = {Math.sin(az) * Math.cos(el), Math.sin(el), Math.cos(az) * Math.cos(el)};
			double[] d = {-toCam[0], -toCam[1], -toCam[2]};
			double[] right = BlockModels.normalize(new double[] {-d[2], 0, d[0]});
			double[] up = {right[1] * d[2] - right[2] * d[1], right[2] * d[0] - right[0] * d[2], right[0] * d[1] - right[1] * d[0]};
			double sMin = Double.MAX_VALUE;
			double tMax = -Double.MAX_VALUE;
			for (int i = 0; i < 8; i++) {
				double[] p = {(i & 1) == 0 ? clip.minX() : clip.maxX() + 1, (i & 2) == 0 ? clip.minY() : clip.maxY() + 1,
						(i & 4) == 0 ? clip.minZ() : clip.maxZ() + 1};
				sMin = Math.min(sMin, BlockModels.dot(p, right));
				tMax = Math.max(tMax, BlockModels.dot(p, up));
			}
			sMin -= 1.0;
			tMax += 1.0;
			Graphics2D g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(11, Math.min(16, cam.scale() * 0.4))));
			FontMetrics fm = g.getFontMetrics();
			for (Object[] l : labels) {
				double[] at = (double[]) l[1];
				int x = (int) ((BlockModels.dot(at, right) - sMin) * cam.scale());
				int y = (int) ((tMax - BlockModels.dot(at, up)) * cam.scale());
				String text = (String) l[0];
				int tw = fm.stringWidth(text);
				int bx = x - tw / 2 - 3;
				g.setColor(new Color(255, 255, 255, 210));
				g.fillRect(bx, y - fm.getAscent(), tw + 6, fm.getHeight());
				g.setColor(new Color(0x22, 0x28, 0x30));
				g.drawString(text, bx + 3, y);
			}
			g.dispose();
		} catch (RuntimeException | Error e) {
			// no fonts on this machine: images without labels
		}
	}
}
