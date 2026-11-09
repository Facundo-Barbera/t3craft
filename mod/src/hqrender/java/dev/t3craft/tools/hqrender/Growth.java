package dev.t3craft.tools.hqrender;

import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.Box;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqStyle;
import dev.t3craft.hq.RingGeometry;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * {@code --projects 4,10,20}: renders the SAME HQ growing. Each roster extends the previous one
 * (same projects first, then new ones) and is allocated against the previous layout, so what
 * existed stays put. Per size: aerial view, plan cut, hub interior, a work sector, a special room;
 * then one comparison strip from the same camera and scale. Ring style views; other styles get
 * the generic aerial/plan views.
 */
final class Growth {
	private Growth() {
	}

	private static final String[] NAMES = {"t3craft", "bridge", "website", "infra", "mobile", "docs", "billing", "search", "agentcraft", "ops",
			"design", "data", "auth", "chat", "metrics", "ml", "cli", "edge", "mail", "admin"};
	private static final String[] ENVS = {"vps", "laptop", "acmesite", "cloud"};
	/** Active threads per project (7+ adds a floor, 13+ a third). */
	private static final int[] THREADS = {5, 9, 3, 6, 2, 13, 4, 1, 7, 3, 2, 5, 8, 1, 4, 3, 6, 2, 1, 4};

	/**
	 * {@code --leads N} (feat/lead-office): thread {@code t0} of each of the first N projects is a lead and sits
	 * in its own office project ({@code lead:<threadId>}, label {@code ★ <project>}), as
	 * {@code LeadOfficeClient.project} does in the game. 0 (the default) leaves every roster as it was.
	 */
	static int leads;

	static HqInput roster(int n) {
		HqInput.Builder b = HqInput.builder();
		for (int p = 0; p < n; p++) {
			String env = ENVS[p % ENVS.length];
			String key = env + ":" + NAMES[p];
			for (int t = 0; t < THREADS[p]; t++) {
				String id = key + "-t" + t;
				if (t == 0 && p < leads) {
					b.thread(dev.t3craft.hq.lead.LeadOffices.projectKey(id), dev.t3craft.hq.lead.LeadOffices.label(NAMES[p]), env,
							env.toUpperCase(Locale.ROOT), id);
					continue;
				}
				b.thread(key, NAMES[p], env, env.toUpperCase(Locale.ROOT), id);
			}
			if (p % 3 == 0) {
				b.thread(key, NAMES[p], env, env.toUpperCase(Locale.ROOT), key + "-s0", true);
			}
		}
		return b.build();
	}

	static void render(HqStyle style, BlockModels models, Path out, List<Integer> sizes, List<String> only, double scaleMul, int ss, boolean npcs)
			throws Exception {
		String sid = style.id();
		List<BuildPlan> plans = new ArrayList<>();
		HqLayout layout = null;
		for (int n : sizes) {
			HqGenerator.Result r = HqGenerator.plan(roster(n), layout, style);
			layout = r.layout();
			plans.add(HqFixtures.active().apply(r.plan()));
			System.out.printf(Locale.ROOT, "%d projects: %d modules, %d placements, footprint %s%n", n, layout.modules().size(), r.plan().size(),
					r.plan().footprint());
		}
		Box big = plans.get(plans.size() - 1).footprint();
		for (int i = 0; i < sizes.size(); i++) {
			int n = sizes.get(i);
			BuildPlan plan = plans.get(i);
			Box fp = plan.footprint();
			String tag = sid + "_" + String.format(Locale.ROOT, "%02d", n) + "p";
			Renderer.Clip all = new Renderer.Clip(fp.minX() - 1, -4, fp.minZ() - 1, fp.maxX() + 1, fp.maxY(), fp.maxZ() + 1);
			double aerialScale = 1700.0 / ((fp.maxX() - fp.minX()) * 1.45) / scaleMul;
			view(models, plan, out, tag + "_aerial", "HQ " + sid + ", " + n + " projects: aerial view", all, new Renderer.Camera(30, 38, aerialScale), scaleMul, ss, npcs, only);
			view(models, plan, out, tag + "_plan", "HQ " + sid + ", " + n + " projects: plan cut at y=6", new Renderer.Clip(all.minX(), -4, all.minZ(), all.maxX(), 6, all.maxZ()),
					new Renderer.Camera(0, 90, 1500.0 / (fp.maxX() - fp.minX()) / scaleMul), scaleMul, ss, npcs, only);
			if (!sid.equals("ring")) {
				continue;
			}
			view(models, plan, out, tag + "_hub", "Hub interior (cut at y=11), " + n + " projects", new Renderer.Clip(-15, -1, -15, 15, 11, 15),
					new Renderer.Camera(25, 52, 16), scaleMul, ss, npcs, only);
			// the work sector of the project with the most floors (ties: lowest index)
			int best = -1;
			int floors = 0;
			for (HqLayout.Module m : layoutOf(sizes, i).modules()) {
				int s = RingGeometry.sectorOf(m.index());
				int f = RingGeometry.floorOf(m.index()) + 1;
				if (s >= 0 && (f > floors || f == floors && s < best)) {
					best = s;
					floors = f;
				}
			}
			if (best >= 0) {
				Box sb = RingGeometry.sectorBounds(best);
				view(models, plan, out, tag + "_sector", "Work sector " + best + " (ground floor, cut at y=5), " + n + " projects",
						new Renderer.Clip(sb.minX() - 2, -1, sb.minZ() - 2, sb.maxX() + 2, 5, sb.maxZ() + 2), new Renderer.Camera(200, 55, 14), scaleMul, ss, npcs, only);
				view(models, plan, out, tag + "_sector_floors", "Work sector " + best + " (" + floors + " floors), " + n + " projects",
						new Renderer.Clip(sb.minX() - 2, -1, sb.minZ() - 2, sb.maxX() + 2, 40, sb.maxZ() + 2), new Renderer.Camera(200, 30, 10), scaleMul, ss, npcs, only);
			}
			int k = switch (i % 3) {
				case 0 -> 0;
				case 1 -> 2;
				default -> 6;
			};
			int[] c = RingGeometry.nodeCentre(k);
			String what = switch (k) {
				case 0 -> "Review tower (cut at y=9)";
				case 2 -> "Scheduled-tasks rotunda (cut at y=7)";
				default -> "Lounge: sunken patio";
			};
			int cut = k == 0 ? 9 : k == 2 ? 7 : 12;
			view(models, plan, out, tag + "_special", what + ", " + n + " projects", new Renderer.Clip(c[0] - 12, -4, c[1] - 12, c[0] + 12, cut, c[1] + 12),
					new Renderer.Camera(35, 50, 16), scaleMul, ss, npcs, only);
			if (i == sizes.size() - 1) {
				int[] a = RingGeometry.nodeCentre(4);
				view(models, plan, out, tag + "_archive", "Archive rotunda (cut at y=5), " + n + " projects", new Renderer.Clip(a[0] - 10, -1, a[1] - 10, a[0] + 10, 5, a[1] + 10),
						new Renderer.Camera(160, 50, 18), scaleMul, ss, npcs, only);
				view(models, plan, out, tag + "_cloister", "Cloister, bridges and sunken patio (cut at y=9)", new Renderer.Clip(-30, -2, -2, 30, 9, 40),
						new Renderer.Camera(20, 40, 12), scaleMul, ss, npcs, only);
			}
		}
		// comparison strip: same camera and scale (the largest footprint), side by side
		if (only.isEmpty() || only.stream().anyMatch("compare"::contains)) {
			double sc = 900.0 / ((big.maxX() - big.minX()) * 1.45);
			Renderer.Clip clip = new Renderer.Clip(big.minX() - 1, -4, big.minZ() - 1, big.maxX() + 1, big.maxY(), big.maxZ() + 1);
			List<BufferedImage> imgs = new ArrayList<>();
			for (int i = 0; i < plans.size(); i++) {
				Scene scene = HqRender.scene(models, plans.get(i), clip, false);
				BufferedImage img = new Renderer(scene, clip, new Renderer.Camera(30, 38, sc)).render(sc, ss);
				HqRender.caption(img, sizes.get(i) + " projects");
				imgs.add(img);
			}
			int w = 0;
			int h = 0;
			for (BufferedImage im : imgs) {
				w += im.getWidth();
				h = Math.max(h, im.getHeight());
			}
			BufferedImage strip = new BufferedImage(w, h + 24, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = strip.createGraphics();
			g.setColor(new Color(0xEE, 0xF1, 0xF4));
			g.fillRect(0, 0, w, h + 24);
			int x = 0;
			for (BufferedImage im : imgs) {
				g.drawImage(im, x, 24, null);
				x += im.getWidth();
			}
			g.dispose();
			HqRender.caption(strip, "HQ " + sid + " growing " + sizes + " projects (same camera and scale)");
			Path f = out.resolve(sid + "_compare.png");
			Png.write(strip, f);
			System.out.printf(Locale.ROOT, "%-34s %5dx%-5d %6d KB%n", f.getFileName(), strip.getWidth(), strip.getHeight(), Files.size(f) / 1024);
		}
	}

	private static HqLayout layoutOf(List<Integer> sizes, int upTo) {
		HqLayout layout = null;
		for (int i = 0; i <= upTo; i++) {
			layout = HqLayout.allocate(roster(sizes.get(i)), layout, ((dev.t3craft.hq.RingStyle) dev.t3craft.hq.HqStyles.byId("ring")).placer());
		}
		return layout;
	}

	private static void view(BlockModels models, BuildPlan plan, Path out, String name, String title, Renderer.Clip clip, Renderer.Camera cam, double scaleMul,
			int ss, boolean npcs, List<String> only) throws Exception {
		if (!only.isEmpty() && only.stream().noneMatch(name::contains)) {
			return;
		}
		long t0 = System.nanoTime();
		Scene scene = HqRender.scene(models, plan, clip, npcs);
		BufferedImage img = new Renderer(scene, clip, cam).render(cam.scale() * scaleMul, ss);
		HqRender.caption(img, title);
		Path f = out.resolve(name + ".png");
		Png.write(img, f);
		System.out.printf(Locale.ROOT, "%-34s %5dx%-5d %6d KB  %.1fs%n", f.getFileName(), img.getWidth(), img.getHeight(), Files.size(f) / 1024,
				(System.nanoTime() - t0) / 1e9);
	}
}
