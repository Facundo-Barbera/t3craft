package dev.t3craft.tools.hqrender;

import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqPlans;
import dev.t3craft.hq.HqStyles;
import dev.t3craft.hq.layout.HqLayoutInfo;
import dev.t3craft.hq.layout.HqLayouts;
import dev.t3craft.hq.plan.BuildPlan;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * {@code --layout <id>} (feat/hq-layouts): any registered layout through the same pipeline the game
 * uses ({@link HqLayouts#use} + {@link HqPlans#plan}, cherry style, active fixtures), grown in order
 * through the {@code --projects} sizes (default 4), with an aerial view and a plan cut per size.
 * {@code --layout all} renders every layout.
 */
final class LayoutViews {
	private LayoutViews() {
	}

	private static final double MAX_SCALE = 3.6;

	static void render(BlockModels models, String layoutArg, Path out, List<Integer> sizes, List<String> only, double scaleMul, int ss,
			boolean npcs) throws Exception {
		List<HqLayoutInfo> layouts;
		if (layoutArg.equals("all")) {
			layouts = HqLayouts.all();
		} else {
			layouts = List.of(HqLayouts.find(layoutArg).orElseThrow(() -> new IllegalArgumentException(
					"unknown layout " + layoutArg + "; known: " + HqLayouts.all().stream().map(HqLayoutInfo::id).toList())));
		}
		if (layouts.size() == 1 && layouts.get(0).id().equals(dev.t3craft.hq.RingCampusGeometry.ID)) {
			// design/hybrid-ringcampus: its own gallery (RingCampusViews), 4 → 10 → 20 by default
			RingCampusViews.render(models, out, sizes.equals(List.of(4)) ? List.of(4, 10, 20) : sizes, only, scaleMul, ss, npcs);
			return;
		}
		double mul = scaleMul / 1.6;
		for (HqLayoutInfo info : layouts) {
			HqLayouts.use(info.id());
			try {
				HqLayout previous = null;
				for (int n : sizes) {
					HqGenerator.Result r = HqPlans.plan(CampusViews.roster(n), previous);
					previous = r.layout();
					BuildPlan plan = r.plan();
					var fp = plan.footprint();
					Renderer.Clip all = new Renderer.Clip(fp.minX() - 2, fp.minY() - 2, fp.minZ() - 2, fp.maxX() + 2, fp.maxY(), fp.maxZ() + 2);
					int w = all.maxX() - all.minX() + 1;
					int d = all.maxZ() - all.minZ() + 1;
					String base = "layout_" + info.id() + "_" + n + "p";
					String what = info.id() + " (" + HqStyles.current().id() + "), " + n + " projects, " + plan.workstations().size() + " desks";
					write(models, plan, base + "_aerial", what + ": aerial view", all,
							new Renderer.Camera(35, 38, Math.min(MAX_SCALE, 1700.0 / ((w + d) * 0.71)) * mul), out, only, ss, npcs);
					write(models, plan, base + "_plan", what + ": plan cut at y=3",
							new Renderer.Clip(all.minX(), all.minY(), all.minZ(), all.maxX(), 3, all.maxZ()),
							new Renderer.Camera(0, 90, Math.min(MAX_SCALE, 1700.0 / Math.max(w, d)) * mul), out, only, ss, npcs);
				}
			} finally {
				HqLayouts.use(null);
			}
		}
	}

	private static void write(BlockModels models, BuildPlan plan, String name, String title, Renderer.Clip clip, Renderer.Camera cam, Path out,
			List<String> only, int ss, boolean npcs) throws Exception {
		if (!only.isEmpty() && only.stream().noneMatch(name::contains)) {
			return;
		}
		long t0 = System.nanoTime();
		Scene scene = HqRender.scene(models, plan, clip, npcs);
		BufferedImage img = new Renderer(scene, clip, cam).render(cam.scale(), ss);
		HqRender.caption(img, title);
		Path f = out.resolve(name + ".png");
		CampusViews.save(img, f);
		System.out.printf(Locale.ROOT, "%-34s %5dx%-5d %6d KB  %.1fs%n", f.getFileName(), img.getWidth(), img.getHeight(), Files.size(f) / 1024,
				(System.nanoTime() - t0) / 1e9);
	}
}
