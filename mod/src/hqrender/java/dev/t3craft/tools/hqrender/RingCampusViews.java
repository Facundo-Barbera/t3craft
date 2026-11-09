package dev.t3craft.tools.hqrender;

import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.Box;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqStyle;
import dev.t3craft.hq.HqStyles;
import dev.t3craft.hq.Placement;
import dev.t3craft.hq.PlanLight;
import dev.t3craft.hq.RingCampusGeometry;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code --layout hybrid-ringcampus}: the hybrid "Ciudadela en anillos con barrios" growing through
 * the {@code --projects} sizes (default 4, 10, 20), each roster allocated against the previous
 * layout. Per size ({@code NN} = 04, 10, 20): {@code ringcampus_NNp_aerial}, {@code _plan} (cut at
 * y = 7), {@code _hub} (cut at y = 13), {@code _hub_section} (the nave cut at z = 0, looking north),
 * {@code _barrio} and {@code _barrio_cut} (the largest barrio, whole and cut at y = 5),
 * {@code _hall} (one work hall cut at y = 5: 3×2 screens and agents standing), {@code _special}
 * (4: review tower, 10: scheduled rotunda, 20: archive cloister) and {@code _light} (block-light
 * heatmap of the walking level); for the largest size also {@code _upper} (an upper floor cut at
 * y = 11) and {@code _lounge}; then {@code ringcampus_compare} (same camera and scale for every size).
 */
final class RingCampusViews {
	private RingCampusViews() {
	}

	static void render(BlockModels models, Path out, List<Integer> sizes, List<String> only, double scaleMul, int ss, boolean npcs) throws Exception {
		HqStyle style = HqStyles.byId(RingCampusGeometry.ID);
		System.setProperty("t3craft.citadel.reliefReport", "true"); // design/citadel-terraces: reachability line per size
		List<BuildPlan> plans = new ArrayList<>();
		List<HqLayout> layouts = new ArrayList<>();
		HqLayout layout = null;
		for (int n : sizes) {
			HqGenerator.Result r = HqGenerator.plan(Growth.roster(n), layout, style);
			layout = r.layout();
			layouts.add(layout);
			plans.add(HqFixtures.active().apply(r.plan()));
			System.out.printf(Locale.ROOT, "%d projects: %d modules, %d desks, %d placements, footprint %s%n", n, layout.modules().size(),
					r.plan().workstations().size(), r.plan().size(), r.plan().footprint());
			System.out.println("  " + dev.t3craft.hq.citadel.CitadelRelief.lastReport);
		}
		double mul = scaleMul / 1.6;
		// citadel/scale: the close-up clips follow the plan's scale (the zoom shrinks so they keep their size on the page)
		RingCampusGeometry.Dims gd = RingCampusGeometry.dims();
		double zk = mul / gd.k;
		// design/citadel-terraces: the cuts follow the terrace each piece stands on
		int l0 = RingCampusGeometry.level(0);
		int l1 = RingCampusGeometry.level(1);
		for (int i = 0; i < sizes.size(); i++) {
			int n = sizes.get(i);
			BuildPlan plan = plans.get(i);
			HqLayout l = layouts.get(i);
			Box fp = plan.footprint();
			String tag = "ringcampus_" + String.format(Locale.ROOT, "%02d", n) + "p";
			String who = n + " projects, " + plan.workstations().size() + " desks";
			Renderer.Clip all = new Renderer.Clip(fp.minX() - 1, -4, fp.minZ() - 1, fp.maxX() + 1, fp.maxY(), fp.maxZ() + 1);
			int w = fp.maxX() - fp.minX() + 3;
			view(models, plan, out, tag + "_aerial", "Ciudadela en anillos, " + who + ": aerial view", all,
					new Renderer.Camera(30, 38, Math.min(3.6, 1700.0 / (w * 1.45)) * mul), ss, npcs, only);
			view(models, plan, out, tag + "_plan", "Ciudadela en anillos, " + who + ": plan cut at y=7",
					new Renderer.Clip(all.minX(), -4, all.minZ(), all.maxX(), 7, all.maxZ()), new Renderer.Camera(0, 90, Math.min(3.6, 1500.0 / w) * mul), ss,
					npcs, only);
			view(models, plan, out, tag + "_hub", "Hub: basilica nave, board, kanban wall, big screen (cut at y=13), " + n + " projects",
					new Renderer.Clip(-gd.s(15), -1 + l0, -gd.s(15), gd.s(15), gd.sy(13) + l0, gd.s(15)), new Renderer.Camera(25, 52, 16 * zk), ss, npcs, only);
			view(models, plan, out, tag + "_hub_section", "Hub section at z=0 looking north: nave vault 12 to 17, aisles, sunken patio",
					new Renderer.Clip(-gd.s(30), -2, -gd.s(30), gd.s(30), Math.max(24, gd.s(5) + gd.podium + gd.sy(12) + 3) + l0, 0), new Renderer.Camera(0, 8, 11 * zk), ss,
					npcs, only);
			// the barrio with the most modules (ties: the lowest lot)
			int best = -1;
			int most = 0;
			int[] count = new int[RingCampusGeometry.MAX_LOTS];
			for (HqLayout.Module m : l.modules()) {
				int lot = RingCampusGeometry.lotOf(m.index());
				if (lot >= 0 && ++count[lot] > most) {
					most = count[lot];
					best = lot;
				}
			}
			if (best >= 0) {
				Box b = RingCampusGeometry.lotBounds(best);
				int lb = b.minY();
				Renderer.Clip lotClip = new Renderer.Clip(b.minX() - 2, -1, b.minZ() - 2, b.maxX() + 2, b.maxY(), b.maxZ() + 2);
				view(models, plan, out, tag + "_barrio", "Barrio " + best + " (" + most + " modules: gatehouse, halls, courtyard), " + n + " projects", lotClip,
						new Renderer.Camera(210, 34, 12 * zk), ss, npcs, only);
				view(models, plan, out, tag + "_barrio_cut", "Barrio " + best + " cut at y=5, " + n + " projects",
						new Renderer.Clip(lotClip.minX(), -1, lotClip.minZ(), lotClip.maxX(), 5 + lb, lotClip.maxZ()), new Renderer.Camera(200, 58, 12 * zk), ss, npcs,
						only);
				Box h = hallBox(plan, best * RingCampusGeometry.LOT_STRIDE);
				if (h != null) {
					view(models, plan, out, tag + "_hall", "Work hall: 6 standing desks with 3x2 screens (cut at y=5), " + n + " projects",
							new Renderer.Clip(h.minX() - 1, -1, h.minZ() - 1, h.maxX() + 1, 5 + lb, h.maxZ() + 1), new Renderer.Camera(160, 55, 24 * zk), ss, npcs, only);
				}
				if (i == sizes.size() - 1) {
					for (HqLayout.Module m : l.modules()) {
						if (RingCampusGeometry.floorOf(m.index()) == 1) {
							Box u = hallBox(plan, m.index());
							if (u != null) {
								view(models, plan, out, tag + "_upper", "Upper floor of a hall, stairs up the centre line (cut at y=11)",
										new Renderer.Clip(u.minX() - 1, -1, u.minZ() - 1, u.maxX() + 1, gd.floorH + 5 + lb, u.maxZ() + 1), new Renderer.Camera(160, 50, 22 * zk), ss,
										npcs, only);
							}
							break;
						}
					}
				}
			}
			int k = switch (i % 3) {
				case 0 -> 0;
				case 1 -> 2;
				default -> 4;
			};
			int[] c = RingCampusGeometry.nodeCentre(k);
			String what = switch (k) {
				case 0 -> "Review tower (cut at y=9)";
				case 2 -> "Scheduled-tasks rotunda (cut at y=7)";
				default -> "Archive cloister with the terminal (cut at y=5)";
			};
			int cut = gd.sy(k == 0 ? 9 : k == 2 ? 7 : 5) + l1;
			int rc = gd.s(10);
			view(models, plan, out, tag + "_special", what + ", " + n + " projects", new Renderer.Clip(c[0] - rc, -1 - gd.sy(3), c[1] - rc, c[0] + rc, cut, c[1] + rc),
					new Renderer.Camera(35, 52, 16 * zk), ss, npcs, only);
			if (i == sizes.size() - 1) {
				int[] lo = RingCampusGeometry.nodeCentre(6);
				int rl = gd.s(10);
				view(models, plan, out, tag + "_lounge", "Lounge: sunken patio on the west node", new Renderer.Clip(lo[0] - rl, -1 - gd.sy(3), lo[1] - rl, lo[0] + rl, 6 + l1, lo[1] + rl),
						new Renderer.Camera(60, 45, 16 * zk), ss, npcs, only);
			}
			{
				int zr = RingCampusGeometry.streetOuter(Math.min(2, RingCampusGeometry.rings(l.modules().stream().map(HqLayout.Module::index).toList()))) + gd.s(6);
				view(models, plan, out, tag + "_terraces", "Terraces of the south quarter: steps, ramps, retaining walls (relief " + RingCampusGeometry.relief() + "), "
						+ n + " projects", new Renderer.Clip(-gd.s(50), -4, gd.s(14), gd.s(50), gd.sy(30) + l0, zr), new Renderer.Camera(25, 30, 7.5 * zk), ss, npcs, only);
			}
			if (only.isEmpty() || only.stream().anyMatch((tag + "_light")::contains)) {
				BufferedImage heat = heatmap(plan, fp, 3);
				HqRender.caption(heat, "Block light at the walking level (red < 10, yellow 10-12, green 13+), " + n + " projects");
				Path f = out.resolve(tag + "_light.png");
				CampusViews.save(heat, f);
				System.out.printf(Locale.ROOT, "%-34s %5dx%-5d %6d KB%n", f.getFileName(), heat.getWidth(), heat.getHeight(), Files.size(f) / 1024);
			}
		}
		if (only.isEmpty() || only.stream().anyMatch("compare"::contains)) {
			Box big = plans.get(plans.size() - 1).footprint();
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
			HqRender.caption(strip, "Ciudadela en anillos growing " + sizes + " projects (same camera and scale)");
			Path f = out.resolve("ringcampus_compare.png");
			CampusViews.save(strip, f);
			System.out.printf(Locale.ROOT, "%-34s %5dx%-5d %6d KB%n", f.getFileName(), strip.getWidth(), strip.getHeight(), Files.size(f) / 1024);
		}
	}

	/** Bounds of a module group's cells (a hall floor), or null. */
	private static Box hallBox(BuildPlan plan, int moduleIndex) {
		int x1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
		for (Placement p : plan.placements(HqGenerator.group(moduleIndex))) {
			x1 = Math.min(x1, p.rel().getX());
			z1 = Math.min(z1, p.rel().getZ());
			x2 = Math.max(x2, p.rel().getX());
			z2 = Math.max(z2, p.rel().getZ());
		}
		return x1 > x2 ? null : Box.of(x1, 0, z1, x2, 20, z2);
	}

	/**
	 * Top-down heatmap: per column, the block light of the lowest cell between y = -3 and 8 an NPC
	 * could stand in (no collision there and above, collision below) that gets any light at all
	 * (skips the hollow under the podium); red when no such cell is lit, grey where there is none.
	 */
	private static BufferedImage heatmap(BuildPlan plan, Box fp, int px) {
		PlanLight light = PlanLight.of(plan);
		int w = fp.maxX() - fp.minX() + 1;
		int d = fp.maxZ() - fp.minZ() + 1;
		BufferedImage img = new BufferedImage(w * px, d * px + 24, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(0xEE, 0xF1, 0xF4));
		g.fillRect(0, 0, img.getWidth(), img.getHeight());
		for (int x = fp.minX(); x <= fp.maxX(); x++) {
			for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
				int v = -1;
				for (int y = -3; y <= 8; y++) {
					BlockPos p = new BlockPos(x, y, z);
					if (free(plan, p) && free(plan, p.above()) && !free(plan, p.below())) {
						v = Math.max(v, 0);
						if (light.get(p) > 0) {
							v = light.get(p);
							break;
						}
					}
				}
				Color c = v < 0 ? new Color(0xC8, 0xCC, 0xD2) : v < 10 ? new Color(0xD6, 0x3A, 0x2F) : v < 13 ? new Color(0xE8, 0xB9, 0x2E)
						: new Color(0x3C, 0x9A, 0x5F);
				g.setColor(c);
				g.fillRect((x - fp.minX()) * px, 24 + (z - fp.minZ()) * px, px, px);
			}
		}
		g.dispose();
		return img;
	}

	private static boolean free(BuildPlan plan, BlockPos p) {
		Placement pl = plan.at(p);
		if (pl == null) {
			return true;
		}
		BlockState s = pl.state();
		return s.getCollisionShape(EmptyBlockGetter.INSTANCE, p).isEmpty();
	}

	private static void view(BlockModels models, BuildPlan plan, Path out, String name, String title, Renderer.Clip clip, Renderer.Camera cam, int ss,
			boolean npcs, List<String> only) throws Exception {
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
