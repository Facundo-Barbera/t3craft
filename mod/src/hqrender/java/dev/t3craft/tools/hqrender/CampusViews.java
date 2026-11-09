package dev.t3craft.tools.hqrender;

import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.CampusFootprints;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqFootprints;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqStyle;
import dev.t3craft.hq.Workstation;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Views of a style with its own layout (the campus) for rosters of N projects: for each N an aerial
 * view, a plan cut, the hub, a work hall and a special pavilion inside; for the largest N a close-up
 * of the busiest barrio (upper storeys and their stairs); and one 4 → 10 → 20 comparison from the
 * same camera at the same scale. The roster is the one {@code CampusLayoutTest} uses.
 */
final class CampusViews {
	private CampusViews() {
	}

	/** The ray caster veils big scenes at exactly 4 px per block (sample grid on cell edges); stay below. */
	private static final double MAX_SCALE = 3.6;

	static final int[] THREADS = {9, 4, 13, 2, 6, 1, 7, 3, 26, 5, 2, 8, 1, 4, 11, 3, 6, 2, 5, 3};
	static final String[] ENVS = {"vps", "desktop", "laptop"};

	static HqInput roster(int n) {
		HqInput.Builder b = HqInput.builder();
		for (int p = 0; p < n; p++) {
			String env = ENVS[p % ENVS.length];
			String name = "proj" + (char) ('a' + p);
			String key = env + ":" + name;
			for (int t = 0; t < THREADS[p % THREADS.length]; t++) {
				b.thread(key, name, env, env.toUpperCase(Locale.ROOT), key + "-t" + t);
			}
			if (p % 3 == 0) {
				b.thread(key, name, env, env.toUpperCase(Locale.ROOT), key + "-s0", true);
			}
		}
		return b.build();
	}

	private record View(String name, String title, Renderer.Clip clip, Renderer.Camera cam) {
	}

	static void render(BlockModels models, HqStyle style, Path out, List<Integer> sizes, List<String> only, double scaleMul, int ss, boolean npcs)
			throws Exception {
		String sid = style.id();
		Map<Integer, BuildPlan> plans = new LinkedHashMap<>();
		Map<Integer, HqLayout> layouts = new LinkedHashMap<>();
		HqLayout previous = null;
		for (int n : sizes) {
			// grown in order (4, then 10 with the 4's layout...), as the HQ would
			HqGenerator.Result r = HqGenerator.plan(roster(n), previous, style);
			previous = r.layout();
			layouts.put(n, r.layout());
			plans.put(n, HqFixtures.active().apply(r.plan()));
		}
		double mul = scaleMul / 1.6;
		String[] specials = {"review", "archive", "lounge", "errors", "scheduled"};
		int k = 0;
		for (int n : sizes) {
			BuildPlan plan = plans.get(n);
			HqLayout layout = layouts.get(n);
			var fp = plan.footprint();
			List<View> views = new ArrayList<>();
			Renderer.Clip all = new Renderer.Clip(fp.minX() - 2, -2, fp.minZ() - 2, fp.maxX() + 2, fp.maxY(), fp.maxZ() + 2);
			int w = all.maxX() - all.minX() + 1;
			int d = all.maxZ() - all.minZ() + 1;
			double aerial = Math.min(MAX_SCALE, 1700.0 / ((w + d) * 0.71)) * mul;
			views.add(new View(sid + "_" + n + "p_aerial", "Campus, " + n + " projects (" + threads(layout) + " desks): aerial view", all,
					new Renderer.Camera(35, 38, aerial)));
			views.add(new View(sid + "_" + n + "p_plan", "Campus, " + n + " projects: plan cut at y=3", new Renderer.Clip(all.minX(), -1, all.minZ(),
					all.maxX(), 3, all.maxZ()), new Renderer.Camera(0, 90, Math.min(MAX_SCALE, 1700.0 / Math.max(w, d)) * mul)));
			views.add(new View(sid + "_" + n + "p_hub", "Hub, " + n + " projects (cut at y=8): board, kanban wall, big screen, hologram",
					new Renderer.Clip(-15, -1, -15, 15, 8, 15), new Renderer.Camera(30, 52, 15 * mul)));
			Renderer.Clip hall = hallClip(plan, layout);
			if (hall != null) {
				views.add(new View(sid + "_" + n + "p_hall", "Work hall (6 desks, 3x2 screens, agents standing), cut at y=6", hall,
						new Renderer.Camera(25, 55, 16 * mul)));
			}
			String sp = specials[k++ % specials.length];
			HqFootprints.Area pa = CampusFootprints.pavilion(sp);
			views.add(new View(sid + "_" + n + "p_" + sp, "Pavilion '" + sp + "' (cut at y=6)", new Renderer.Clip(pa.minX(), -1, pa.minZ(), pa.maxX(), 6,
					pa.maxZ()), new Renderer.Camera(25, 55, 16 * mul)));
			if (n == sizes.get(sizes.size() - 1)) {
				Renderer.Clip barrio = busiestBarrio(layout, fp.maxY());
				views.add(new View(sid + "_" + n + "p_barrio", "Busiest barrio: three halls, upper storeys with outdoor stairs, commons, patio", barrio,
						new Renderer.Camera(40, 32, 9 * mul)));
				views.add(new View(sid + "_" + n + "p_barrio_cut", "Busiest barrio cut at y=10 (upper storeys)",
						new Renderer.Clip(barrio.minX(), -1, barrio.minZ(), barrio.maxX(), 10, barrio.maxZ()), new Renderer.Camera(40, 50, 9 * mul)));
				views.add(new View(sid + "_" + n + "p_specials", "The pavilions round the hub plaza (archive, review, scheduled, lounge, errors, gate)",
						new Renderer.Clip(-50, -2, -55, 50, fp.maxY(), 55), new Renderer.Camera(20, 40, 7 * mul)));
			}
			for (View v : views) {
				if (!only.isEmpty() && only.stream().noneMatch(v.name()::contains)) {
					continue;
				}
				write(models, plan, v, out, ss, npcs);
			}
		}
		// the comparison: same camera, same clip (the largest campus), same scale
		String cmp = sid + "_compare";
		if (sizes.size() > 1 && (only.isEmpty() || only.stream().anyMatch(cmp::contains))) {
			var fp = plans.get(sizes.get(sizes.size() - 1)).footprint();
			Renderer.Clip all = new Renderer.Clip(fp.minX() - 2, -2, fp.minZ() - 2, fp.maxX() + 2, fp.maxY(), fp.maxZ() + 2);
			int w = all.maxX() - all.minX() + 1;
			int d = all.maxZ() - all.minZ() + 1;
			double scale = 1000.0 / ((w + d) * 0.71) * mul;
			List<BufferedImage> imgs = new ArrayList<>();
			for (int n : sizes) {
				Scene scene = HqRender.scene(models, plans.get(n), all, npcs);
				BufferedImage img = new Renderer(scene, all, new Renderer.Camera(35, 38, scale)).render(scale, ss);
				HqRender.caption(img, n + " projects, " + threads(layouts.get(n)) + " desks");
				imgs.add(img);
			}
			int tw = imgs.stream().mapToInt(BufferedImage::getWidth).sum();
			int th = imgs.stream().mapToInt(BufferedImage::getHeight).max().orElse(1);
			BufferedImage row = new BufferedImage(tw, th + 26, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = row.createGraphics();
			g.setColor(new Color(0xEE, 0xF1, 0xF4));
			g.fillRect(0, 0, tw, th + 26);
			int x = 0;
			for (BufferedImage img : imgs) {
				g.drawImage(img, x, 26, null);
				g.setColor(new Color(0xB0, 0xB6, 0xBE));
				g.drawLine(x, 26, x, th + 26);
				x += img.getWidth();
			}
			try {
				g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
				g.setColor(new Color(0x33, 0x3A, 0x44));
				g.drawString("Campus " + sizes + " projects: same camera, same scale, grown in order (nothing moves)", 8, 19);
			} catch (RuntimeException | Error e) {
				// headless without fonts
			}
			g.dispose();
			save(row, out.resolve(cmp + ".png"));
		}
	}

	private static int threads(HqLayout l) {
		int n = 0;
		for (HqLayout.Module m : l.modules()) {
			n += m.desks().size();
		}
		return n;
	}

	/** The ground hall with the most desks, cut at y=6. */
	private static Renderer.Clip hallClip(BuildPlan plan, HqLayout layout) {
		HqLayout.Module best = null;
		for (HqLayout.Module m : layout.modules()) {
			if (m.index() % 8 < 3 && (best == null || m.desks().size() > best.desks().size())) {
				best = m;
			}
		}
		if (best == null) {
			return null;
		}
		int x1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
		for (Workstation w : plan.workstations()) {
			if (w.module() == best.index()) {
				x1 = Math.min(x1, w.deskAnchor().block().getX());
				x2 = Math.max(x2, w.deskAnchor().block().getX());
				z1 = Math.min(z1, w.deskAnchor().block().getZ());
				z2 = Math.max(z2, w.deskAnchor().block().getZ());
			}
		}
		return new Renderer.Clip(x1 - 5, -1, z1 - 5, x2 + 5, 6, z2 + 5);
	}

	/** The parcel with the most buildings. */
	private static Renderer.Clip busiestBarrio(HqLayout layout, int top) {
		Map<Integer, Integer> count = new HashMap<>();
		for (HqLayout.Module m : layout.modules()) {
			count.merge(CampusFootprints.parcelOf(m.index()), 1, Integer::sum);
		}
		int best = count.entrySet().stream().max(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
				.map(Map.Entry::getKey).orElse(0);
		HqFootprints.Area a = CampusFootprints.parcel(best);
		return new Renderer.Clip(a.minX() - 2, -2, a.minZ() - 2, a.maxX() + 2, top, a.maxZ() + 2);
	}

	private static void write(BlockModels models, BuildPlan plan, View v, Path out, int ss, boolean npcs) throws Exception {
		long t0 = System.nanoTime();
		Scene scene = HqRender.scene(models, plan, v.clip(), npcs);
		BufferedImage img = new Renderer(scene, v.clip(), v.cam()).render(v.cam().scale(), ss);
		HqRender.caption(img, v.title());
		Path f = out.resolve(v.name() + ".png");
		save(img, f);
		System.out.printf(Locale.ROOT, "%-34s %5dx%-5d %6d KB  %.1fs%n", f.getFileName(), img.getWidth(), img.getHeight(), Files.size(f) / 1024,
				(System.nanoTime() - t0) / 1e9);
	}

	/** Writes a truecolour PNG. */
	static void save(BufferedImage img, Path f) throws Exception {
		ImageIO.write(img, "png", f.toFile());
	}
}
