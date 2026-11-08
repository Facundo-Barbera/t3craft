package dev.t3craft.tools.hqrender;

import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.EnsancheGrid;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqFootprints;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqStyle;
import dev.t3craft.hq.TowncenterGrid;
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

/**
 * Views of design/hybrid-towncenter ({@code --style hybrid-towncenter}): the same HQ grown from 4 to
 * 10 to 20 projects with {@link TownRender}'s roster (each step keeps the previous layout, so nothing
 * moves), and for each size an aerial view from the south-east and the north-west, a low skyline
 * view, a cut plan, the civic hall, the plaza's singular buildings, a podium room and a tower room
 * with their 3×2 screens and standing agents, a section through a tower's stair atrium, the covered
 * market, the signal box and the library; plus a comparison strip drawn with the same camera and
 * scale. PNGs over {@value TownRender#MAX_BYTES} bytes are written with a 256-colour palette.
 */
final class TowncenterRender {
	private TowncenterRender() {
	}

	private record View(String name, String title, Renderer.Clip clip, Renderer.Camera cam) {
	}

	static void render(BlockModels models, HqStyle style, Path out, List<String> only, int ss, boolean npcs) throws Exception {
		int[] sizes = {4, 10, 20};
		Map<Integer, BuildPlan> plans = new LinkedHashMap<>();
		Map<Integer, HqLayout> layouts = new LinkedHashMap<>();
		HqLayout prev = null;
		for (int n : sizes) {
			HqGenerator.Result r = HqGenerator.plan(TownRender.roster(n), prev, style);
			prev = r.layout();
			layouts.put(n, r.layout());
			plans.put(n, HqFixtures.active().apply(r.plan()));
			var fp = r.plan().footprint();
			System.out.printf(Locale.ROOT, "%2d projects: %d modules, %d desks, cells 0..%d, footprint %dx%d (x %d..%d, z %d..%d), top y %d, %d placements%n", n,
					r.layout().modules().size(), r.plan().workstations().size(), TowncenterGrid.maxCell(r.layout()), fp.sizeX(), fp.sizeZ(), fp.minX(),
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
			views.add(new View(pre + "aerial", "Centro urbano con torres, " + n + " proyectos: vista aerea (SE)", all, new Renderer.Camera(45, 35, s)));
			views.add(new View(pre + "aerial_nw", "Centro urbano con torres, " + n + " proyectos: vista aerea (NO)", all, new Renderer.Camera(225, 35, s)));
			views.add(new View(pre + "skyline", "Centro urbano con torres, " + n + " proyectos: skyline (vista baja desde el SO)", all,
					new Renderer.Camera(215, 14, s * 1.15)));
			views.add(new View(pre + "plan", "Centro urbano con torres, " + n + " proyectos: planta cortada a y=3",
					new Renderer.Clip(all.minX(), -1, all.minZ(), all.maxX(), 3, all.maxZ()), new Renderer.Camera(0, 90, 1150.0 / Math.max(fp.sizeX(), fp.sizeZ()))));
			views.add(new View(pre + "hub", "Casa consistorial (hub, nave en cruz), cortada a y=13", new Renderer.Clip(-23, -1, -23, 23, 13, 23),
					new Renderer.Camera(25, 55, 15)));
			views.add(new View(pre + "plaza", "Plaza mayor: casa consistorial, torre del reloj, puesto de senales, biblioteca",
					new Renderer.Clip(-28, -1, -28, 28, TowncenterGrid.TOP, 28), new Renderer.Camera(200, 28, 10)));
			View room = roomView(plan, layout, pre, n);
			if (room != null) {
				views.add(room);
			}
			View upper = towerRoomView(plan, layout, pre, n);
			if (upper != null) {
				views.add(upper);
			}
			View atrium = atriumView(layout, pre);
			if (atrium != null) {
				views.add(atrium);
			}
			views.add(new View(pre + "signal", "Puesto de senales (needs attention / errores), cortado a y=6", new Renderer.Clip(-23, -1, 7, -8, 6, 23),
					new Renderer.Camera(60, 50, 22)));
			views.add(new View(pre + "library", "Biblioteca (archivo), cortada a y=8", new Renderer.Clip(-23, -1, -23, -8, 8, -11),
					new Renderer.Camera(150, 50, 22)));
			views.add(new View(pre + "market", "Mercado cubierto (lounge), cortado a y=7", new Renderer.Clip(30, -1, -25, 80, 7, 25),
					new Renderer.Camera(30, 50, 14)));
			if (n == 20) {
				views.add(new View(pre + "manzana", "Una manzana: patio, pasajes, podios y torres", cellClip(5, TowncenterGrid.TOP), new Renderer.Camera(45, 38, 10)));
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
			HqRender.caption(strip, "Centro urbano con torres: crecimiento 4 -> 10 -> 20 proyectos (misma camara y escala; nada se mueve)");
			write(strip, out.resolve(sid + "_growth.png"));
		}
	}

	private static Renderer.Clip cellClip(int k, int top) {
		HqFootprints.Area a = EnsancheGrid.cellArea(k);
		return new Renderer.Clip(a.minX(), -1, a.minZ(), a.maxX(), top, a.maxZ());
	}

	/** The podium room with the most desks, cut under its ceiling. */
	private static View roomView(BuildPlan plan, HqLayout layout, String pre, int n) {
		HqLayout.Module best = null;
		for (HqLayout.Module m : layout.modules()) {
			if (TowncenterGrid.floorOfModule(m.index()) == 0 && (best == null || m.desks().size() > best.desks().size())) {
				best = m;
			}
		}
		return best == null ? null : roomClip(best, pre + "room", "Sala del podio (" + best.label() + ", planta baja): pantallas 3x2 y agentes de pie", 0);
	}

	/** The highest tower room, cut under its ceiling. */
	private static View towerRoomView(BuildPlan plan, HqLayout layout, String pre, int n) {
		HqLayout.Module best = null;
		for (HqLayout.Module m : layout.modules()) {
			int f = TowncenterGrid.floorOfModule(m.index());
			if (f >= 1 && (best == null || f > TowncenterGrid.floorOfModule(best.index()))) {
				best = m;
			}
		}
		if (best == null) {
			return null;
		}
		int f = TowncenterGrid.floorOfModule(best.index());
		return roomClip(best, pre + "tower_room", "Sala de la torre (" + best.label() + ", planta " + f + "): atrio con escalera, pantallas 3x2", f);
	}

	/** A vertical cut through the stair atrium of the tallest tower. */
	private static View atriumView(HqLayout layout, String pre) {
		int bestP = -1;
		int bestF = 0;
		for (HqLayout.Module m : layout.modules()) {
			int f = TowncenterGrid.floorOfModule(m.index());
			if (f > bestF) {
				bestF = f;
				bestP = TowncenterGrid.parcelOfModule(m.index());
			}
		}
		if (bestP < 0) {
			return null;
		}
		HqFootprints.Area a = EnsancheGrid.parcelArea(bestP);
		int side = EnsancheGrid.sideOfParcel(bestP);
		// cut the street half away: keep the patio half of the building, look from the street
		int mx = (a.minX() + a.maxX()) / 2;
		int mz = (a.minZ() + a.maxZ()) / 2;
		Renderer.Clip clip = switch (side) {
			case 0 -> new Renderer.Clip(a.minX() - 2, -1, a.minZ() - 2, a.maxX() + 2, TowncenterGrid.TOP, mz);
			case 1 -> new Renderer.Clip(mx, -1, a.minZ() - 2, a.maxX() + 2, TowncenterGrid.TOP, a.maxZ() + 2);
			case 2 -> new Renderer.Clip(a.minX() - 2, -1, mz, a.maxX() + 2, TowncenterGrid.TOP, a.maxZ() + 2);
			default -> new Renderer.Clip(a.minX() - 2, -1, a.minZ() - 2, mx, TowncenterGrid.TOP, a.maxZ() + 2);
		};
		double az = switch (side) {
			case 0 -> 180;
			case 1 -> 270;
			case 2 -> 0;
			default -> 90;
		};
		return new View(pre + "atrium", "Corte por el atrio de la torre mas alta: escalera en zigzag, plantas, lucernario", clip,
				new Renderer.Camera(az + 20, 15, 11));
	}

	private static View roomClip(HqLayout.Module m, String name, String title, int floor) {
		int p = TowncenterGrid.parcelOfModule(m.index());
		int side = EnsancheGrid.sideOfParcel(p);
		HqFootprints.Area a = EnsancheGrid.parcelArea(p);
		int y = EnsancheGrid.STOREY * floor + EnsancheGrid.STOREY - 1;
		double az = switch (side) {
			case 0 -> 200;
			case 1 -> 110;
			case 2 -> 20;
			default -> 290;
		};
		return new View(name, title, new Renderer.Clip(a.minX() - 2, Math.max(-1, y - EnsancheGrid.STOREY), a.minZ() - 2, a.maxX() + 2, y, a.maxZ() + 2),
				new Renderer.Camera(az, 55, 18));
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

	private static void write(BufferedImage img, Path f) throws Exception {
		TownRender.write(img, f);
	}
}
