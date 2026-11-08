package dev.t3craft.tools.hqrender;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqFootprints;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqStyles;
import dev.t3craft.hq.Placement;
import dev.t3craft.hq.TerraceFootprints;
import dev.t3craft.hq.TerraceGenerator;
import dev.t3craft.hq.Workstation;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * design/hybrid-terraces in hq-render: the "Acrópolis en terrazas" grown 4 → 10 → 20 projects
 * (each step allocated against the previous layout, the game's pipeline: {@link HqStyles#forLayout}
 * + {@link HqGenerator#plan} + active fixtures), and for each size: aerial views (south-east and
 * north-west), a plan cut three blocks above every terrace, the temple (hub) interior, the plaza
 * with the stoas, the largest barrio (cut at desk height and from outside), the library (archive)
 * and the mill island with its bridge, and a light heatmap of every walkable cell; plus the
 * 4 → 10 → 20 comparison from one camera and scale.
 *
 * <pre>./gradlew hqRender -PhqArgs="--style hybrid-terraces --out ../docs/research/hq-hybrids/terraces"</pre>
 */
final class TerracesRender {
	private TerracesRender() {
	}

	private record View(String name, String title, Renderer.Clip clip, Renderer.Camera cam, Predicate<BlockPos> keep) {
	}

	static void run(Path out, List<String> only, double scaleMul, int ss, boolean npcs, BlockModels models) throws Exception {
		Map<Integer, HqGenerator.Result> grown = new LinkedHashMap<>();
		HqLayout prev = null;
		for (int n : new int[] {4, 10, 20}) {
			HqGenerator.Result r = HqGenerator.plan(CampusViews.roster(n), prev, HqStyles.forLayout(TerraceGenerator.ID));
			grown.put(n, new HqGenerator.Result(r.layout(), HqFixtures.active().apply(r.plan())));
			prev = r.layout();
			System.out.printf(Locale.ROOT, "terraces %2d projects: %d modules, %d desks, %d placements, footprint %s%n", n, r.layout().modules().size(),
					r.plan().workstations().size(), r.plan().size(), r.plan().footprint());
		}
		var big = grown.get(20).plan().footprint();
		Renderer.Clip common = new Renderer.Clip(big.minX() - 4, -2, big.minZ() - 4, big.maxX() + 4, big.maxY(), big.maxZ() + 4);
		Renderer.Camera aerial = new Renderer.Camera(35, 34, 1);
		double aerialScale = 2.6 * scaleMul / 1.6;
		int hf = TerraceFootprints.hubFloor();
		int cell = TerraceFootprints.cell();
		int corner = hf - 4;
		List<BufferedImage> compare = new ArrayList<>();
		for (var e : grown.entrySet()) {
			int n = e.getKey();
			BuildPlan plan = e.getValue().plan();
			HqLayout layout = e.getValue().layout();
			var fp = plan.footprint();
			Renderer.Clip all = new Renderer.Clip(fp.minX() - 4, -2, fp.minZ() - 4, fp.maxX() + 4, fp.maxY(), fp.maxZ() + 4);
			double s = n == 4 ? 4.6 : n == 10 ? 3.8 : 3.0;
			String pre = "terraces_" + n + "p_";
			List<View> views = new ArrayList<>();
			views.add(new View(pre + "aerial", "Acrópolis en terrazas, " + n + " projects: aerial (south-east)", all, new Renderer.Camera(35, 34, s), p -> true));
			views.add(new View(pre + "aerial_nw", "Acrópolis en terrazas, " + n + " projects: from the north-west", all, new Renderer.Camera(215, 30, s),
					p -> true));
			views.add(new View(pre + "plan", "Acrópolis en terrazas, " + n + " projects: plan cut 3 blocks above every terrace", all,
					new Renderer.Camera(0, 90, s * 1.15), p -> p.getY() <= TerraceFootprints.floorAt(p.getX(), p.getZ()) + 3));
			// the temple (hub) cut above the benches
			HqFootprints.Area temple = TerraceFootprints.cellArea(0, -1);
			views.add(new View(pre + "hub", "Temple (hub): attention board and benches, cut at y=" + (hf + 7) + ", " + n + " projects",
					new Renderer.Clip(temple.minX(), hf - 3, temple.minZ(), temple.maxX(), hf + 7, temple.maxZ()), new Renderer.Camera(25, 52, 14), p -> true));
			// the great plaza between the two stoas: tholos over the hologram, kanban wall, big screen
			views.add(new View(pre + "plaza", "Great plaza: hologram tholos, kanban stoa (west), big-screen stoa (east), " + n + " projects",
					new Renderer.Clip(-cell - 14, hf - 3, -14, cell + 14, hf + 17, cell + 14), new Renderer.Camera(200, 32, 7.5), p -> true));
			// the largest barrio
			HqLayout.Module top = null;
			Map<Integer, Integer> desks = new HashMap<>();
			for (HqLayout.Module m : layout.modules()) {
				desks.merge(m.index() / 4, m.desks().size(), Integer::sum);
			}
			int bestPlot = 0;
			int bestDesks = -1;
			for (Map.Entry<Integer, Integer> d : new java.util.TreeMap<>(desks).entrySet()) {
				if (d.getValue() > bestDesks) {
					bestDesks = d.getValue();
					bestPlot = d.getKey();
				}
			}
			for (HqLayout.Module m : layout.modules()) {
				if (m.index() / 4 == bestPlot && top == null) {
					top = m;
				}
			}
			HqFootprints.Area a = TerraceFootprints.plotArea(bestPlot);
			if (a != null && top != null) {
				int f = TerraceFootprints.floorAt((a.minX() + a.maxX()) / 2, (a.minZ() + a.maxZ()) / 2);
				String who = top.label() + " (" + desks.get(bestPlot) + " desks)";
				views.add(new View(pre + "barrio", "Barrio of " + who + ": hall, annex and tower round the patio, cut at desk height",
						new Renderer.Clip(a.minX(), f - 3, a.minZ(), a.maxX(), f + 4, a.maxZ()), new Renderer.Camera(30, 55, 15), p -> true));
				views.add(new View(pre + "barrio_out", "Barrio of " + who, new Renderer.Clip(a.minX() - 2, f - 6, a.minZ() - 2, a.maxX() + 2, f + 16,
						a.maxZ() + 2), new Renderer.Camera(40, 30, 12), p -> true));
			}
			// special rooms: the library (archive terminal) cut, and the mill island with its bridge
			HqFootprints.Area lib = TerraceFootprints.cellArea(-1, -1);
			views.add(new View(pre + "library", "Library-hermitage (archive terminal), cut at y=" + (corner + 5) + ", " + n + " projects",
					new Renderer.Clip(lib.minX(), corner - 3, lib.minZ(), lib.maxX(), corner + 5, lib.maxZ()), new Renderer.Camera(200, 55, 14), p -> true));
			HqFootprints.Area mill = TerraceFootprints.cellArea(0, -3).union(TerraceFootprints.cellArea(0, -2));
			views.add(new View(pre + "mill", "Sky island of the mill and the bridge from the temple, " + n + " projects",
					new Renderer.Clip(mill.minX(), 4, mill.minZ(), mill.maxX(), hf + 18, mill.maxZ() + 6), new Renderer.Camera(120, 22, 6.5), p -> true));
			for (View v : views) {
				if (!only.isEmpty() && only.stream().noneMatch(v.name()::contains)) {
					continue;
				}
				write(out, v, plan, models, npcs, v.cam().scale() * scaleMul / 1.6, ss);
			}
			if (only.isEmpty() || only.stream().anyMatch((pre + "light")::contains)) {
				heatmap(out.resolve(pre + "light.png"), plan, fp, n);
			}
			if (only.isEmpty() || only.stream().anyMatch("compare"::contains)) {
				Scene sc = scene(models, plan, common, npcs, p -> true);
				BufferedImage img = new Renderer(sc, common, aerial).render(aerialScale, ss);
				VillageRender.caption(img, n + " projects (" + desks.size() + " barrios)");
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
			VillageRender.caption(all, "Acrópolis en terrazas growing 4 -> 10 -> 20 projects (same camera, same scale)");
			Path f = out.resolve("terraces_compare_4_10_20.png");
			CampusViews.save(all, f);
			System.out.printf(Locale.ROOT, "%-34s %5dx%-5d %6d KB%n", f.getFileName(), all.getWidth(), all.getHeight(), Files.size(f) / 1024);
		}
		for (String p : models.problems) {
			System.out.println("note: " + p);
		}
	}

	/** Top-down map of the walkable cells (highest per column) coloured by block light: red < 10, yellow 10-11, green ≥ 12. */
	private static void heatmap(Path f, BuildPlan plan, dev.t3craft.hq.Box fp, int n) throws Exception {
		Map<BlockPos, Integer> light = TerraceFootprints.walkableLight(plan);
		int px = 3;
		int w = (fp.maxX() - fp.minX() + 1) * px;
		int h = (fp.maxZ() - fp.minZ() + 1) * px + 24;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(0xF4, 0xF1, 0xEC));
		g.fillRect(0, 0, w, h);
		Map<Long, int[]> top = new HashMap<>();
		int dark = 0;
		for (Map.Entry<BlockPos, Integer> e : light.entrySet()) {
			BlockPos p = e.getKey();
			long k = ((long) p.getX() << 32) ^ (p.getZ() & 0xFFFFFFFFL);
			int[] cur = top.get(k);
			if (cur == null || p.getY() > cur[0]) {
				top.put(k, new int[] {p.getY(), e.getValue(), p.getX(), p.getZ()});
			}
			if (e.getValue() < 10) {
				dark++;
			}
		}
		for (int[] c : top.values()) {
			int l = c[1];
			g.setColor(l < 10 ? new Color(0xD0, 0x30, 0x30) : l < 12 ? new Color(0xE8, 0xC0, 0x30) : new Color(0x40, 0x9A + Math.min(3, l - 12) * 10, 0x50));
			g.fillRect((c[2] - fp.minX()) * px, 24 + (c[3] - fp.minZ()) * px, px, px);
		}
		g.dispose();
		VillageRender.caption(img, "Block light on every walkable cell, " + n + " projects: " + light.size() + " cells, " + dark + " under 10");
		CampusViews.save(img, f);
		System.out.printf(Locale.ROOT, "%-34s %5dx%-5d %6d KB%n", f.getFileName(), img.getWidth(), img.getHeight(), Files.size(f) / 1024);
	}

	private static void write(Path out, View v, BuildPlan plan, BlockModels models, boolean npcs, double scale, int ss) throws Exception {
		long t0 = System.nanoTime();
		Scene sc = scene(models, plan, v.clip(), npcs, v.keep());
		BufferedImage img = new Renderer(sc, v.clip(), v.cam()).render(scale, ss);
		VillageRender.caption(img, v.title());
		Path f = out.resolve(v.name() + ".png");
		CampusViews.save(img, f);
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
			if (keep.test(p.rel()) && p.rel().getY() <= clip.maxY()) {
				sc.set(p.rel(), p.state());
			}
		}
		if (npcs) {
			int i = 0;
			for (Workstation w : plan.workstations()) {
				if (w.threadId() != null && keep.test(w.deskAnchor().block())) {
					HqRender.npc(sc, w.deskAnchor(), false, i++);
				}
			}
			for (Map.Entry<String, Anchor> e : plan.anchors().entrySet()) {
				String k = e.getKey();
				if ((k.equals("board_slot_0") || k.equals("board_slot_3") || k.equals("bench_1") || k.equals("bench_9") || k.equals("walk_hub_0")
						|| k.equals("walk_hub_4") || k.equals("archive") || k.equals("thread_wall_front")) && keep.test(e.getValue().block())) {
					HqRender.npc(sc, e.getValue(), k.startsWith("bench"), i++);
				}
			}
		}
		return sc;
	}
}
