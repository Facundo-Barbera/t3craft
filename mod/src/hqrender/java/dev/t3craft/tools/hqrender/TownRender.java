package dev.t3craft.tools.hqrender;

import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.EnsancheGrid;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Views of a town style (layout/blocks, {@code ensanche}): the same HQ grown from 4 to 10 to 20
 * projects (each step keeps the previous layout, so nothing moves), and for each size an aerial view,
 * a cut plan, the hub, a work room with its 3×2 screens and agents, and a special room; plus one
 * comparison strip drawn with the same camera and scale.
 */
final class TownRender {
	private TownRender() {
	}

	private record View(String name, String title, Renderer.Clip clip, Renderer.Camera cam) {
	}

	/** Thread counts of the 20 projects; the first 4 and the first 10 are the smaller HQs. */
	static final int[] THREADS = {9, 4, 7, 2, 13, 3, 1, 6, 5, 20, 2, 8, 4, 3, 11, 1, 6, 2, 5, 3};
	static final String[] NAMES = {"t3craft", "bridge", "website", "infra", "mobile", "docs", "api", "billing", "search", "platform", "auth",
			"analytics", "design", "cli", "data", "ops", "chat", "payments", "gateway", "scheduler"};
	static final String[] ENVS = {"vps", "vps", "acmesite", "laptop", "cloud", "vps", "acmesite", "cloud", "laptop", "vps", "cloud", "acmesite",
			"laptop", "vps", "cloud", "acmesite", "vps", "cloud", "laptop", "acmesite"};

	/** The first {@code n} projects of the demo roster (keys sort in roster order: {@code pNN-name}). */
	static HqInput roster(int n) {
		HqInput.Builder b = HqInput.builder();
		for (int p = 0; p < n; p++) {
			String name = String.format(Locale.ROOT, "p%02d-%s", p, NAMES[p]);
			String key = ENVS[p] + ":" + name;
			for (int t = 0; t < THREADS[p]; t++) {
				b.thread(key, NAMES[p], ENVS[p], ENVS[p].toUpperCase(Locale.ROOT), key + "-t" + t);
			}
			b.thread(key, NAMES[p], ENVS[p], ENVS[p].toUpperCase(Locale.ROOT), key + "-s0", true);
		}
		return b.build();
	}

	static void render(BlockModels models, HqStyle style, Path out, List<String> only, int ss, boolean npcs) throws Exception {
		int[] sizes = {4, 10, 20};
		Map<Integer, BuildPlan> plans = new LinkedHashMap<>();
		Map<Integer, HqLayout> layouts = new LinkedHashMap<>();
		HqLayout prev = null;
		for (int n : sizes) {
			HqGenerator.Result r = HqGenerator.plan(roster(n), prev, style);
			prev = r.layout();
			layouts.put(n, r.layout());
			plans.put(n, HqFixtures.active().apply(r.plan()));
			var fp = r.plan().footprint();
			System.out.printf(Locale.ROOT, "%2d projects: %d modules, %d desks, cells 0..%d, footprint %dx%d (x %d..%d, z %d..%d), top y %d, %d placements%n", n,
					r.layout().modules().size(), r.plan().workstations().size(), EnsancheGrid.maxCell(r.layout()), fp.sizeX(), fp.sizeZ(), fp.minX(),
					fp.maxX(), fp.minZ(), fp.maxZ(), fp.maxY(), r.plan().size());
		}
		String sid = style.id();
		var big = plans.get(20).footprint();
		Renderer.Clip whole = new Renderer.Clip(big.minX() - 2, -2, big.minZ() - 2, big.maxX() + 2, big.maxY(), big.maxZ() + 2);
		for (int n : sizes) {
			BuildPlan plan = plans.get(n);
			HqLayout layout = layouts.get(n);
			var fp = plan.footprint();
			Renderer.Clip all = new Renderer.Clip(fp.minX() - 2, -2, fp.minZ() - 2, fp.maxX() + 2, fp.maxY(), fp.maxZ() + 2);
			double s = 1300.0 / ((fp.sizeX() + fp.sizeZ()) * 0.7071);
			List<View> views = new ArrayList<>();
			String pre = sid + "_" + n + "p_";
			views.add(new View(pre + "aerial", "Ensanche, " + n + " proyectos: vista aerea (SE)", all, new Renderer.Camera(45, 35, s)));
			views.add(new View(pre + "aerial_nw", "Ensanche, " + n + " proyectos: vista aerea (NO)", all, new Renderer.Camera(225, 35, s)));
			views.add(new View(pre + "plan", "Ensanche, " + n + " proyectos: planta cortada a y=3",
					new Renderer.Clip(all.minX(), -1, all.minZ(), all.maxX(), 3, all.maxZ()), new Renderer.Camera(0, 90, 1150.0 / Math.max(fp.sizeX(), fp.sizeZ()))));
			views.add(new View(pre + "hub", "Plaza mayor y casa consistorial (hub), " + n + " proyectos, cortada a y=11",
					new Renderer.Clip(-23, -1, -24, 23, 11, 28), new Renderer.Camera(25, 52, 16)));
			views.add(new View(pre + "plaza", "Plaza mayor: torre del reloj, lonja de revision, soportales", new Renderer.Clip(-28, -1, -28, 28, 34, 28),
					new Renderer.Camera(200, 30, 13)));
			View room = roomView(plan, layout, pre, n);
			if (room != null) {
				views.add(room);
			}
			View upper = upperRoomView(plan, layout, pre, n);
			if (upper != null) {
				views.add(upper);
			}
			views.add(new View(pre + "market", "Mercado cubierto (lounge), cortado a y=7", new Renderer.Clip(30, -1, -25, 80, 7, 25),
					new Renderer.Camera(30, 50, 14)));
			if (n == 20) {
				views.add(new View(pre + "manzana", "Una manzana: patio, pasajes, cuatro edificios", cellClip(5, 30), new Renderer.Camera(45, 38, 12)));
				views.add(new View(pre + "plazuela", "Plazuela en el paseo diagonal", cellClip(2, 12), new Renderer.Camera(45, 40, 12)));
			}
			for (View v : views) {
				draw(models, plan, v, out, only, ss, npcs);
			}
		}
		// comparison strip: same clip, camera and scale for the three sizes
		if (only.isEmpty() || only.stream().anyMatch("growth"::contains) || only.stream().anyMatch(o -> (sid + "_growth").contains(o))) {
			double s = 900.0 / ((whole.maxX() - whole.minX() + whole.maxZ() - whole.minZ()) * 0.7071);
			List<BufferedImage> imgs = new ArrayList<>();
			for (int n : sizes) {
				Scene scene = HqRender.scene(models, plans.get(n), whole, npcs);
				BufferedImage img = new Renderer(scene, whole, new Renderer.Camera(45, 35, s)).render(s, ss);
				label(img, n + " proyectos");
				imgs.add(img);
			}
			int w = 0;
			int h = 0;
			for (BufferedImage i : imgs) {
				w += i.getWidth();
				h = Math.max(h, i.getHeight());
			}
			BufferedImage strip = new BufferedImage(w, h + 30, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = strip.createGraphics();
			g.setColor(new Color(0xEE, 0xF1, 0xF4));
			g.fillRect(0, 0, w, h + 30);
			int x = 0;
			for (BufferedImage i : imgs) {
				g.drawImage(i, x, 30, null);
				x += i.getWidth();
			}
			g.dispose();
			HqRender.caption(strip, "Ensanche: crecimiento 4 -> 10 -> 20 proyectos (misma camara y escala; nada se mueve)");
			write(strip, out.resolve(sid + "_growth.png"));
		}
	}

	private static Renderer.Clip cellClip(int k, int top) {
		HqFootprints.Area a = EnsancheGrid.cellArea(k);
		return new Renderer.Clip(a.minX(), -1, a.minZ(), a.maxX(), top, a.maxZ());
	}

	/** The ground-floor room with the most desks, cut under its ceiling. */
	private static View roomView(BuildPlan plan, HqLayout layout, String pre, int n) {
		HqLayout.Module best = null;
		for (HqLayout.Module m : layout.modules()) {
			if (EnsancheGrid.floorOfModule(m.index()) == 0 && (best == null || m.desks().size() > best.desks().size())) {
				best = m;
			}
		}
		return best == null ? null : roomClip(best, pre + "room", "Sala de trabajo (" + best.label() + ", planta baja): pantallas 3x2 y agentes de pie", 0);
	}

	/** A first-floor room, cut under its ceiling (stairs and stair hall visible). */
	private static View upperRoomView(BuildPlan plan, HqLayout layout, String pre, int n) {
		for (HqLayout.Module m : layout.modules()) {
			if (EnsancheGrid.floorOfModule(m.index()) == 1) {
				return roomClip(m, pre + "room_floor1", "Sala del piso 1 (" + m.label() + ") y escalera del pasaje", 1);
			}
		}
		return null;
	}

	private static View roomClip(HqLayout.Module m, String name, String title, int floor) {
		int p = EnsancheGrid.parcelOfModule(m.index());
		int side = EnsancheGrid.sideOfParcel(p);
		HqFootprints.Area a = EnsancheGrid.parcelArea(p);
		int y = EnsancheGrid.STOREY * floor + EnsancheGrid.STOREY - 1;
		double az = switch (side) {
			case 0 -> 200;   // south building: look from the patio (north)
			case 1 -> 110;
			case 2 -> 20;
			default -> 290;
		};
		return new View(name, title, new Renderer.Clip(a.minX() - 2, -1, a.minZ() - 2, a.maxX() + 2, y, a.maxZ() + 2), new Renderer.Camera(az, 55, 18));
	}

	private static void draw(BlockModels models, BuildPlan plan, View v, Path out, List<String> only, int ss, boolean npcs) throws Exception {
		if (!only.isEmpty() && only.stream().noneMatch(v.name()::contains)) {
			return;
		}
		long t0 = System.nanoTime();
		Scene scene = HqRender.scene(models, plan, v.clip(), npcs);
		BufferedImage img = new Renderer(scene, v.clip(), v.cam()).render(v.cam().scale(), ss);
		HqRender.caption(img, v.title());
		Path f = out.resolve(v.name() + ".png");
		write(img, f);
		System.out.printf(Locale.ROOT, "%-34s %5dx%-5d %6d KB  %.1fs%n", f.getFileName(), img.getWidth(), img.getHeight(), Files.size(f) / 1024,
				(System.nanoTime() - t0) / 1e9);
	}

	private static void label(BufferedImage img, String text) {
		try {
			Graphics2D g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
			g.setColor(new Color(0x33, 0x3A, 0x44));
			g.drawString(text, 10, 22);
			g.dispose();
		} catch (RuntimeException | Error e) {
			// headless without fonts
		}
	}

	/** Writes a truecolour PNG. */
	static void write(BufferedImage img, Path f) throws Exception {
		ImageIO.write(img, "png", f.toFile());
	}

	/** Every desk of a plan, for logs. */
	static int desks(BuildPlan plan) {
		int n = 0;
		for (Workstation w : plan.workstations()) {
			if (w.threadId() != null) {
				n++;
			}
		}
		return n;
	}
}
