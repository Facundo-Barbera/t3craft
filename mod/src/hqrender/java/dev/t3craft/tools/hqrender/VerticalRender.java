package dev.t3craft.tools.hqrender;

import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqFootprints;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqStyle;
import dev.t3craft.hq.TowerLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Renders of layout/vertical (towers with an atrium and bridges): the same design with 4, 10 and 20
 * projects ({@link TowerLayout#sampleRoster}), grown one after the other from the previous layout
 * (as the HQ really grows), with aerial views, cut plans, vertical sections, interiors and a
 * 4 → 10 → 20 comparison from one camera and scale. PNGs over 480 KB are written with 256 colours.
 *
 * <pre>./gradlew hqRender -PhqArgs="--style vertical --out ../docs/research/hq-layouts/vertical"</pre>
 */
final class VerticalRender {
	private VerticalRender() {
	}

	private record View(String name, String title, Renderer.Clip clip, Renderer.Camera cam) {
	}

	static void run(BlockModels models, HqStyle style, Path out, List<String> only, double scaleMul, int ss, boolean npcs) throws Exception {
		HqLayout layout = null;
		List<BufferedImage> compare = new ArrayList<>();
		HqFootprints.Area all = TowerLayout.envelope(3);
		Renderer.Clip allClip = new Renderer.Clip(all.minX() - 3, -2, all.minZ() - 3, all.maxX() + 3, TowerLayout.TOP, all.maxZ() + 3);
		for (int n : new int[] {4, 10, 20}) {
			HqInput in = TowerLayout.sampleRoster(n);
			HqGenerator.Result r = HqGenerator.plan(in, layout, style);
			layout = r.layout();
			BuildPlan plan = HqFixtures.active().apply(r.plan());
			int towers = TowerLayout.towers(layout).size();
			HqFootprints.Area env = TowerLayout.envelope(towers);
			String p = "vertical_" + n + "p";
			String what = n + " projects, " + layout.modules().size() + " half floors, " + towers + (towers == 1 ? " tower" : " towers");
			int x1 = env.minX() - 3, z1 = env.minZ() - 3, x2 = env.maxX() + 3, z2 = env.maxZ() + 3;
			int top = TowerLayout.TOP;
			double s = n == 4 ? 6 : 4.2;
			List<View> views = new ArrayList<>();
			views.add(new View(p + "_full", "Torres con atrio, " + what, new Renderer.Clip(x1, -2, z1, x2, top, z2), new Renderer.Camera(35, 30, s)));
			views.add(new View(p + "_full_nw", "Desde el noroeste, " + what, new Renderer.Clip(x1, -2, z1, x2, top, z2), new Renderer.Camera(215, 30, s)));
			views.add(new View(p + "_plan_ground", "Planta baja cortada a y=4 (podio, plaza, atrio), " + what, new Renderer.Clip(x1, -1, z1, x2, 4, z2),
					new Renderer.Camera(0, 90, s * 1.3)));
			views.add(new View(p + "_plan_f2", "Planta cortada a y=19 (T1 piso 2), " + what, new Renderer.Clip(x1, -1, z1, x2, 19, z2),
					new Renderer.Camera(0, 90, s * 1.3)));
			views.add(new View(p + "_section_ew", "Corte vertical E-O por el atrio (z=0, mirando al norte), " + what, new Renderer.Clip(x1, -2, z1, x2, top, 0),
					new Renderer.Camera(0, 0, s * 1.2)));
			views.add(new View(p + "_section_ns", "Corte vertical N-S por el atrio (x=0, mirando al oeste), " + what,
					new Renderer.Clip(x1, -2, z1, 0, top, z2), new Renderer.Camera(90, 0, s * 1.2)));
			if (towers > 1) {
				views.add(new View(p + "_section_bridges", "Corte por los skybridges (z=-11, mirando al norte), " + what,
						new Renderer.Clip(x1, -2, z1, x2, top, -11), new Renderer.Camera(0, 0, s * 1.2)));
			}
			views.add(new View(p + "_hub", "Hub en el atrio de T1 (cortado a y=7), " + what, new Renderer.Clip(-22, -1, -16, 22, 7, 16),
					new Renderer.Camera(25, 55, 12)));
			views.add(new View(p + "_atrium", "Atrio de T1 de toda la altura: tablero, pared kanban, puente de revisión, cúpula y faro",
					new Renderer.Clip(-22, -1, -16, 22, top, 0), new Renderer.Camera(0, 12, 9)));
			switch (n) {
				case 4 -> {
					views.add(work(p, layout, 0, "planta abierta"));
					views.add(work(p, layout, 2, "salas pequeñas"));
					views.add(work(p, layout, 4, "mezzanine (tarima)"));
					views.add(work(p, null, 6, "jardín colgante (medio piso libre)"));
					views.add(new View(p + "_lookout", "Azotea mirador de T1 (tareas programadas) con cúpula y faro", new Renderer.Clip(-22, 40, -16, 22, top, 16),
							new Renderer.Camera(30, 35, 13)));
					views.add(new View(p + "_lounge", "Podio: terraza lounge sobre el vestíbulo, frente a la plaza", new Renderer.Clip(-23, -1, 13, 23, 14, 30),
							new Renderer.Camera(25, 40, 14)));
					views.add(new View(p + "_archive", "Podio: archivo (terminal) y vestíbulo, cortado a y=7", new Renderer.Clip(-22, -1, 14, 2, 7, 24),
							new Renderer.Camera(20, 55, 20)));
				}
				case 10 -> {
					views.add(work(p, layout, 10, "T2, mezzanine"));
					views.add(work(p, layout, 3, "T1 piso 2 oeste, salas pequeñas"));
					views.add(new View(p + "_review", "Puente acristalado de revisión cruzando el atrio (piso 3, corte x=3)",
							new Renderer.Clip(-8, 15, -11, 3, 34, 11), new Renderer.Camera(90, 15, 18)));
					views.add(new View(p + "_skybridge", "Skybridges T1 → T2 (pisos 2→2 y 5→6) sobre el podio este", new Renderer.Clip(14, -1, -17, 45, 50, 3),
							new Renderer.Camera(160, 20, 9)));
				}
				default -> {
					views.add(work(p, layout, 24, "T3, salas pequeñas"));
					views.add(work(p, layout, 22, "T2 piso 7, planta abierta"));
					views.add(work(p, layout, 26, "T3, jardín colgante (medio piso libre)"));
					views.add(new View(p + "_skybridge", "Skybridge T1 → T3 (piso 3 → 3, sube 3) y podio oeste", new Renderer.Clip(-45, -1, -17, -14, 36, -5),
							new Renderer.Camera(200, 25, 10)));
				}
			}
			for (View v : views) {
				if (!only.isEmpty() && only.stream().noneMatch(v.name()::contains)) {
					continue;
				}
				render(models, plan, v, out, scaleMul, ss, npcs);
			}
			View cmp = new View("cmp", n + " proyectos", allClip, new Renderer.Camera(35, 30, 3.4));
			if (only.isEmpty() || only.contains("growth")) {
				BufferedImage img = new Renderer(HqRender.scene(models, plan, allClip, npcs), allClip, cmp.cam()).render(cmp.cam().scale() * scaleMul, ss);
				label(img, n + " proyectos · " + layout.modules().size() + " medios pisos · " + towers + (towers == 1 ? " torre" : " torres"));
				compare.add(img);
			}
		}
		if (compare.size() == 3) {
			int w = 0;
			int h = 0;
			for (BufferedImage i : compare) {
				w += i.getWidth();
				h = Math.max(h, i.getHeight());
			}
			BufferedImage strip = new BufferedImage(w, h + 26, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = strip.createGraphics();
			g.setColor(new Color(0xEE, 0xF1, 0xF4));
			g.fillRect(0, 0, w, h + 26);
			int x = 0;
			for (BufferedImage i : compare) {
				g.drawImage(i, x, 26, null);
				x += i.getWidth();
			}
			g.dispose();
			HqRender.caption(strip, "Crecimiento 4 → 10 → 20 proyectos (misma cámara y escala; lo existente no se mueve)");
			write(strip, out.resolve("vertical_growth_4_10_20.png"));
		}
	}

	/** Interior of parcel {@code index} (its half floor, cut under the ceiling). */
	private static View work(String p, HqLayout layout, int index, String kind) {
		TowerLayout.Parcel parcel = TowerLayout.parcel(index);
		TowerLayout.Tower t = parcel.tower();
		int xa = t.cx() + parcel.side() * (t.gallery() - 2);
		int xb = t.cx() + parcel.side() * (t.px() + 1);
		int y0 = t.floorY(parcel.floor());
		Renderer.Clip clip = new Renderer.Clip(Math.min(xa, xb), y0 - 1, t.cz() - t.pz() - 1, Math.max(xa, xb), y0 + t.floorH() - 2,
				t.cz() + t.pz() + 1);
		String title = "Medio piso " + index + " (T" + t.id() + ", piso " + parcel.floor() + (parcel.side() > 0 ? " este" : " oeste") + "): " + kind
				+ ", cortado bajo el techo";
		return new View(p + "_room_" + index, title, clip, new Renderer.Camera(parcel.side() > 0 ? 300 : 60, 55, 16));
	}

	private static void render(BlockModels models, BuildPlan plan, View v, Path out, double scaleMul, int ss, boolean npcs) throws Exception {
		long t0 = System.nanoTime();
		Scene scene = HqRender.scene(models, plan, v.clip(), npcs);
		BufferedImage img = new Renderer(scene, v.clip(), v.cam()).render(v.cam().scale() * scaleMul, ss);
		HqRender.caption(img, v.title());
		Path f = out.resolve(v.name() + ".png");
		write(img, f);
		System.out.printf(Locale.ROOT, "%-40s %5dx%-5d %6d KB  %.1fs%n", f.getFileName(), img.getWidth(), img.getHeight(), Files.size(f) / 1024,
				(System.nanoTime() - t0) / 1e9);
	}

	private static void label(BufferedImage img, String text) {
		try {
			Graphics2D g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
			g.setColor(new Color(0x33, 0x3A, 0x44));
			g.drawString(text, 10, img.getHeight() - 12);
			g.dispose();
		} catch (RuntimeException | Error e) {
			// no fonts: no label
		}
	}

	/** Writes a truecolour PNG. */
	static void write(BufferedImage img, Path f) throws Exception {
		ImageIO.write(img, "png", f.toFile());
	}
}
