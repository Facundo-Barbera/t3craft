package dev.t3craft.tools.hqrender;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqFootprints;
import dev.t3craft.hq.Placement;
import dev.t3craft.hq.VillageDemo;
import dev.t3craft.hq.VillageFootprints;
import dev.t3craft.hq.VillageGenerator;
import dev.t3craft.hq.VillageLayout;
import dev.t3craft.hq.Workstation;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The village HQ (layout/village) in hq-render: the same town grown 4 → 10 → 20 projects (each step
 * allocated against the previous layout), and for each size an aerial view, a plan cut at every
 * terrace's own height, and interiors; plus the 4 → 10 → 20 comparison from one camera and scale.
 *
 * <pre>./gradlew hqRender -PhqArgs="--style village --out ../docs/research/hq-layouts/village"</pre>
 */
final class VillageRender {
	private VillageRender() {
	}

	private record View(String name, String title, Renderer.Clip clip, Renderer.Camera cam, Predicate<BlockPos> keep) {
	}

	static void run(Path out, List<String> only, double scaleMul, int ss, boolean npcs, BlockModels models) throws Exception {
		Map<Integer, VillageGenerator.Result> grown = new LinkedHashMap<>();
		VillageLayout prev = null;
		for (int n : new int[] {4, 10, 20}) {
			VillageGenerator.Result r = VillageGenerator.plan(VillageDemo.roster(n), prev);
			grown.put(n, new VillageGenerator.Result(r.layout(), HqFixtures.active().apply(r.plan())));
			prev = r.layout();
			System.out.printf(Locale.ROOT, "village %2d projects: %d hamlets, %d placements, footprint %s%n", n, r.layout().hamlets().size(),
					r.plan().size(), r.plan().footprint());
		}
		BuildPlan biggest = grown.get(20).plan();
		var big = biggest.footprint();
		Renderer.Clip common = new Renderer.Clip(big.minX() - 4, -2, big.minZ() - 4, big.maxX() + 4, big.maxY(), big.maxZ() + 4);
		Renderer.Camera aerial = new Renderer.Camera(35, 34, 1);
		double aerialScale = 4.2 * scaleMul / 1.6;
		List<BufferedImage> compare = new ArrayList<>();
		for (var e : grown.entrySet()) {
			int n = e.getKey();
			BuildPlan plan = e.getValue().plan();
			VillageLayout layout = e.getValue().layout();
			var fp = plan.footprint();
			Renderer.Clip all = new Renderer.Clip(fp.minX() - 4, -2, fp.minZ() - 4, fp.maxX() + 4, fp.maxY(), fp.maxZ() + 4);
			int hf = VillageFootprints.hubFloor();
			List<View> views = new ArrayList<>();
			double s = n == 4 ? 7 : n == 10 ? 5.5 : 4.2;
			views.add(new View("village_" + n + "_aerial", "Village HQ, " + n + " projects: aerial", all, new Renderer.Camera(35, 34, s), p -> true));
			views.add(new View("village_" + n + "_aerial_nw", "Village HQ, " + n + " projects: from the north-west", all,
					new Renderer.Camera(215, 30, s), p -> true));
			views.add(new View("village_" + n + "_plan", "Village HQ, " + n + " projects: plan cut 3 blocks above every terrace", all,
					new Renderer.Camera(0, 90, s * 1.15), p -> p.getY() <= VillageFootprints.floorAt(p.getX(), p.getZ()) + 3));
			views.add(new View("village_" + n + "_hub", "Town hall (hub) cut at y=" + (hf + 6) + ", " + n + " projects",
					new Renderer.Clip(-11, hf - 4, -11, 11, hf + 6, 11), new Renderer.Camera(25, 52, 16), p -> true));
			views.add(new View("village_" + n + "_singular_north", "Tavern, review market hall and mill tower (cut), " + n + " projects",
					new Renderer.Clip(-31, hf - 4, -31, 31, hf + 5, -11), new Renderer.Camera(20, 55, 9), p -> true));
			views.add(new View("village_" + n + "_singular_south", "Library (archive) and guard post (cut), " + n + " projects",
					new Renderer.Clip(-31, hf - 4, 11, 31, hf + 6, 31), new Renderer.Camera(200, 55, 9), p -> true));
			// the biggest hamlet, cut at its desks
			VillageLayout.Hamlet top = layout.hamlets().stream().max(java.util.Comparator.comparingInt(VillageLayout.Hamlet::threads)).orElseThrow();
			HqFootprints.Area a = VillageFootprints.areas(layout).get(VillageGenerator.plotGroup(top.plot()));
			int f = VillageFootprints.floorAt((a.minX() + a.maxX()) / 2, (a.minZ() + a.maxZ()) / 2);
			views.add(new View("village_" + n + "_hamlet", "Hamlet of " + top.label() + " (" + top.threads() + " threads), cut at desk height",
					new Renderer.Clip(a.minX(), f - 3, a.minZ(), a.maxX(), f + 4, a.maxZ()), new Renderer.Camera(30, 55, 18), p -> true));
			views.add(new View("village_" + n + "_hamlet_out", "Hamlet of " + top.label() + " (" + top.threads() + " threads)",
					new Renderer.Clip(a.minX() - 2, f - 4, a.minZ() - 2, a.maxX() + 2, fp.maxY(), a.maxZ() + 2), new Renderer.Camera(40, 30, 14),
					p -> true));
			for (View v : views) {
				if (!only.isEmpty() && only.stream().noneMatch(v.name()::contains)) {
					continue;
				}
				write(out, v, plan, models, npcs, v.cam().scale() * scaleMul / 1.6, ss);
			}
			if (only.isEmpty() || only.stream().anyMatch("compare"::contains)) {
				Scene sc = scene(models, plan, common, npcs, p -> true);
				BufferedImage img = new Renderer(sc, common, aerial).render(aerialScale, ss);
				caption(img, n + " projects (" + layout.hamlets().size() + " hamlets)");
				compare.add(img);
			}
		}
		if (compare.size() == 3) {
			int w = compare.stream().mapToInt(BufferedImage::getWidth).sum();
			int h = compare.get(0).getHeight();
			BufferedImage all = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = all.createGraphics();
			int x = 0;
			for (BufferedImage i : compare) {
				g.drawImage(i, x, 0, null);
				x += i.getWidth();
				g.setColor(new Color(0xC8, 0xCE, 0xD6));
				g.fillRect(x - 2, 0, 2, h);
			}
			g.dispose();
			caption(all, "Village HQ growing 4 -> 10 -> 20 projects (same camera, same scale)");
			Path f = out.resolve("village_compare_4_10_20.png");
			ImageIO.write(all, "png", f.toFile());
			System.out.printf(Locale.ROOT, "%-34s %5dx%-5d %6d KB%n", f.getFileName(), all.getWidth(), all.getHeight(), Files.size(f) / 1024);
		}
		for (String p : models.problems) {
			System.out.println("note: " + p);
		}
	}

	private static void write(Path out, View v, BuildPlan plan, BlockModels models, boolean npcs, double scale, int ss) throws Exception {
		long t0 = System.nanoTime();
		Scene sc = scene(models, plan, v.clip(), npcs, v.keep());
		BufferedImage img = new Renderer(sc, v.clip(), v.cam()).render(scale, ss);
		caption(img, v.title());
		Path f = out.resolve(v.name() + ".png");
		ImageIO.write(img, "png", f.toFile());
		System.out.printf(Locale.ROOT, "%-34s %5dx%-5d %6d KB  %.1fs%n", f.getFileName(), img.getWidth(), img.getHeight(), Files.size(f) / 1024,
				(System.nanoTime() - t0) / 1e9);
	}

	private static Scene scene(BlockModels models, BuildPlan plan, Renderer.Clip clip, boolean npcs, Predicate<BlockPos> keep) {
		Scene sc = new Scene(models, clip.minX(), Math.min(clip.minY(), -3), clip.minZ(), clip.maxX(), Math.max(clip.maxY(), 11), clip.maxZ());
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		for (int x = sc.minX; x < sc.minX + sc.sx; x++) {
			for (int z = sc.minZ; z < sc.minZ + sc.sz; z++) {
				for (int y = sc.minY; y <= 0; y++) {
					sc.set(new BlockPos(x, y, z), y == 0 ? grass : dirt);
				}
			}
		}
		for (Placement p : plan.placements()) {
			if (keep.test(p.rel())) {
				sc.set(p.rel(), p.state());
			}
		}
		if (npcs) {
			int n = 0;
			java.util.Set<String> seated = new java.util.HashSet<>();
			for (Workstation w : plan.workstations()) {
				if (w.threadId() != null && keep.test(w.deskAnchor().block())) {
					HqRender.npc(sc, w.deskAnchor(), false, n++);
					seated.add(w.threadId());
				}
			}
			for (Map.Entry<String, Anchor> e : plan.anchors().entrySet()) {
				String k = e.getKey();
				boolean lounge = k.startsWith("desk_") && !seated.contains(k.substring(5)) && plan.workstation(k.substring(5)) == null;
				if ((lounge || k.equals("board_slot_0") || k.equals("board_slot_3") || k.equals("bench_1") || k.equals("bench_7")
						|| k.equals("walk_hub_0") || k.equals("archive") || k.equals("thread_wall_front") || k.startsWith("walk_square_"))
						&& keep.test(e.getValue().block())) {
					HqRender.npc(sc, e.getValue(), k.startsWith("bench"), n++);
				}
			}
		}
		return sc;
	}

	static void caption(BufferedImage img, String text) {
		try {
			Graphics2D g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
			g.setColor(new Color(0x33, 0x3A, 0x44));
			g.drawString(text, 10, 20);
			g.dispose();
		} catch (RuntimeException | Error e) {
			// no fonts: no captions
		}
	}
}
