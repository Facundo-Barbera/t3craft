package dev.t3craft.tools.hqrender;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.Box;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.Placement;
import dev.t3craft.hq.PlanProbe;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * Views of one piece of the HQ instead of the whole thing, plus the headless {@link PlanReport}: what a
 * designer of a door or a stretch of wall looks at, in seconds. Given a plan and a {@link Target} (a box,
 * a module, a group or the surroundings of an anchor) it draws four isometric views from the corners, a
 * plan view cut at a height and a vertical section, all cropped to that box, and writes
 * {@code report.txt} / {@code report.json} next to the PNGs.
 *
 * <pre>
 * ./gradlew hqRender -PhqArgs="--region -10,0,-10:10,12,10"                  # a box of the citadel
 * ./gradlew hqRender -PhqArgs="--module module_3 --scale 0.5"                 # a module, small preview
 * ./gradlew hqRender -PhqArgs="--group hub --out build/hq-after --compare build/hq-before"
 * ./gradlew hqRender -PhqArgs="--anchor walk_hub_0 --radius 12"
 * ./gradlew hqRender -PhqArgs="--report-only"                                  # report of the whole plan, no images
 * </pre>
 *
 * From Java (the render service): {@code HqRender.bootstrap(false)}, then {@link PlanSource#plan}, then
 * {@code PieceViews.open().render(plan, request)}. A {@link Session} keeps the block models (the slow part
 * to load) between renders.
 */
public final class PieceViews {
	private PieceViews() {
	}

	// ------------------------------------------------------------------ what to look at

	/** The piece to draw: build one with the factories. */
	public record Target(String kind, Box box, String name, int radius) {
		/** The whole plan. */
		public static Target whole() {
			return new Target("whole", null, "", 0);
		}

		/** A box of cells (inclusive corners, plan-relative). */
		public static Target region(Box box) {
			return new Target("region", box, "", 0);
		}

		/** {@code x1,y1,z1:x2,y2,z2} (corners in any order). */
		public static Target region(String spec) {
			String[] halves = spec.trim().split(":");
			if (halves.length != 2) {
				throw new IllegalArgumentException("--region wants x1,y1,z1:x2,y2,z2, got '" + spec + "'");
			}
			int[] a = ints(halves[0]);
			int[] b = ints(halves[1]);
			return region(Box.of(a[0], a[1], a[2], b[0], b[1], b[2]));
		}

		/** A module of the BuildPlan by id (a module cut into pieces matches {@code id@...} too). */
		public static Target module(String id) {
			return new Target("module", null, id, 0);
		}

		/** A group of the BuildPlan ({@code hub}, {@code module_3}…): the blocks its modules own. */
		public static Target group(String name) {
			return new Target("group", null, name, 0);
		}

		/** A cube of {@code radius} blocks around an anchor ({@code radius} 0 = {@value #DEFAULT_RADIUS}). */
		public static Target anchor(String name, int radius) {
			return new Target("anchor", null, name, radius);
		}

		public static final int DEFAULT_RADIUS = 10;

		private static int[] ints(String s) {
			String[] p = s.trim().split(",");
			if (p.length != 3) {
				throw new IllegalArgumentException("expected x,y,z, got '" + s + "'");
			}
			return new int[] {Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())};
		}

		/** A file-name friendly label of this target. */
		public String label() {
			return switch (kind) {
				case "region" -> "region_" + box.minX() + "_" + box.minY() + "_" + box.minZ() + "_" + box.maxX() + "_" + box.maxY() + "_" + box.maxZ();
				case "module" -> "module_" + name;
				case "group" -> "group_" + name;
				case "anchor" -> "anchor_" + name;
				default -> "whole";
			};
		}
	}

	/** Everything {@link Session#render} reads; set the fields you care about, the rest are the CLI defaults. */
	public static final class Request {
		public Target target = Target.whole();
		/** Output directory for the PNGs and the report. */
		public Path out = Path.of("build/hq-render");
		/** File-name prefix of the PNGs ({@code <name>_iso_se.png}…); null = derived from the target. */
		public String name;
		/** Multiplies the pixels per block (1 = the automatic fit, about 1100 px across; 0.5 = small previews). */
		public double scale = 1.0;
		/** Supersampling per axis (1 = fast, 2 = smooth). */
		public int ss = 2;
		/** Stand-in NPCs at the desks and anchors. */
		public boolean npcs = true;
		/** kit/status-lamp {@code --lamp-demo}: lamps lit in demo states ({@link LampViews#demo}). */
		public boolean lampDemo;
		/** Blocks added around a module/group box (horizontally; 1 up and down). */
		public int pad = 1;
		/** Height the plan view is cut at; null = 5 above the piece's base. */
		public Integer cutY;
		/** Coordinate of the vertical section plane; null = the middle of the piece. */
		public Integer sectionAt;
		/** Draw the PNGs (false = report only). */
		public boolean views = true;
		/** Substrings of the view names to draw ({@code iso_se}, {@code plan}, {@code section}…); empty = all. */
		public List<String> only = new ArrayList<>();
		/** Write the report. */
		public boolean report = true;
		/** Base name of the report files ({@code report} → report.txt / report.json). */
		public String reportName = "report";
		public PlanReport.Options reportOptions = new PlanReport.Options();
	}

	/**
	 * @param box    the piece that was drawn
	 * @param images the PNGs written
	 * @param files  the report files written
	 * @param report the report (null when not asked for)
	 * @param notes  textures or models the renderer could not find
	 */
	public record Result(Box box, List<Path> images, List<Path> files, PlanReport report, List<String> notes) {
	}

	// ------------------------------------------------------------------ resolving a target

	/** The box of {@code target} in {@code plan}; throws with the known names when a module, group or anchor does not exist. */
	public static Box resolve(BuildPlan plan, Target target, int pad) {
		switch (target.kind()) {
			case "region":
				return target.box();
			case "whole": {
				return plan.footprint();
			}
			case "anchor": {
				Anchor a = plan.anchor(target.name());
				if (a == null) {
					List<String> hits = new ArrayList<>();
					for (String k : plan.anchors().keySet()) {
						if (k.contains(target.name())) {
							hits.add(k);
						}
					}
					if (hits.size() != 1) {
						throw new IllegalArgumentException("no anchor '" + target.name() + "'" + (hits.isEmpty() ? "" : " (several match: " + sample(hits) + ")")
								+ "; known: " + sample(new ArrayList<>(plan.anchors().keySet())));
					}
					a = plan.anchor(hits.get(0));
				}
				int r = target.radius() > 0 ? target.radius() : Target.DEFAULT_RADIUS;
				BlockPos b = a.block();
				return Box.of(b.getX() - r, b.getY() - 3, b.getZ() - r, b.getX() + r, b.getY() + r, b.getZ() + r);
			}
			case "module": {
				List<BuildPlan.Module> found = new ArrayList<>();
				for (BuildPlan.Module m : plan.modules()) {
					if (m.id().equals(target.name())) {
						found.add(m);
					}
				}
				if (found.isEmpty()) {
					for (BuildPlan.Module m : plan.modules()) {
						if (m.id().startsWith(target.name() + "@")) {
							found.add(m);
						}
					}
				}
				if (found.isEmpty()) {
					List<String> ids = new ArrayList<>();
					for (BuildPlan.Module m : plan.modules()) {
						ids.add(m.id());
					}
					throw new IllegalArgumentException("no module '" + target.name() + "'; known: " + sample(ids));
				}
				Bounds bd = new Bounds();
				if (found.size() <= 64) {
					for (Placement p : plan.placements()) {
						if (p.state().isAir()) {
							continue;
						}
						BlockPos r = p.rel();
						for (BuildPlan.Module m : found) {
							if (m.owns(r.getX(), r.getY(), r.getZ())) {
								bd.add(r);
								break;
							}
						}
					}
				}
				if (bd.empty()) {
					for (BuildPlan.Module m : found) {
						bd.add(m.box());
					}
				}
				return bd.box(pad);
			}
			case "group": {
				Map<BlockPos, String> groups = PlanProbe.groups(plan);
				Bounds bd = new Bounds();
				for (Placement p : plan.placements()) {
					if (!p.state().isAir() && target.name().equals(groups.get(p.rel()))) {
						bd.add(p.rel());
					}
				}
				if (bd.empty()) {
					for (BuildPlan.Module m : plan.modules()) {
						if (m.group().equals(target.name())) {
							bd.add(m.box());
						}
					}
				}
				if (bd.empty()) {
					throw new IllegalArgumentException("no group '" + target.name() + "'; known: " + sample(plan.groups()));
				}
				return bd.box(pad);
			}
			default:
				throw new IllegalArgumentException("unknown target " + target.kind());
		}
	}

	private static String sample(List<String> names) {
		int n = Math.min(names.size(), 30);
		return String.join(", ", names.subList(0, n)) + (names.size() > n ? ", … (" + names.size() + " in all)" : "");
	}

	private static final class Bounds {
		private int x1 = Integer.MAX_VALUE;
		private int y1 = Integer.MAX_VALUE;
		private int z1 = Integer.MAX_VALUE;
		private int x2 = Integer.MIN_VALUE;
		private int y2 = Integer.MIN_VALUE;
		private int z2 = Integer.MIN_VALUE;

		void add(BlockPos p) {
			x1 = Math.min(x1, p.getX());
			y1 = Math.min(y1, p.getY());
			z1 = Math.min(z1, p.getZ());
			x2 = Math.max(x2, p.getX());
			y2 = Math.max(y2, p.getY());
			z2 = Math.max(z2, p.getZ());
		}

		void add(Box b) {
			add(new BlockPos(b.minX(), b.minY(), b.minZ()));
			add(new BlockPos(b.maxX(), b.maxY(), b.maxZ()));
		}

		boolean empty() {
			return x1 > x2;
		}

		Box box(int pad) {
			return Box.of(x1 - pad, y1 - 1, z1 - pad, x2 + pad, y2 + 1, z2 + pad);
		}
	}

	// ------------------------------------------------------------------ the views

	private record ViewSpec(String suffix, String title, Renderer.Clip clip, Renderer.Camera cam) {
	}

	/** Pixels per block that fit about 1100 px across {@code extent} blocks, within 3..40, times {@code mul}. */
	private static double fit(double extent, double mul) {
		return Math.max(0.5, Math.min(40, 1100.0 / Math.max(extent, 1)) * mul);
	}

	/** The six views of {@code b}: iso from the four corners, plan, section. */
	private static List<ViewSpec> views(Box b, Request rq, String what) {
		double mul = rq.scale;
		List<ViewSpec> out = new ArrayList<>();
		Renderer.Clip all = new Renderer.Clip(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
		String box = " " + b;
		double iso = fit(Math.max((b.sizeX() + b.sizeZ()) * 0.75, b.sizeY() * 1.2), mul);
		out.add(new ViewSpec("iso_se", what + ", from the south-east" + box, all, new Renderer.Camera(45, 32, iso)));
		out.add(new ViewSpec("iso_ne", what + ", from the north-east" + box, all, new Renderer.Camera(135, 32, iso)));
		out.add(new ViewSpec("iso_nw", what + ", from the north-west" + box, all, new Renderer.Camera(225, 32, iso)));
		out.add(new ViewSpec("iso_sw", what + ", from the south-west" + box, all, new Renderer.Camera(315, 32, iso)));
		int cut = rq.cutY != null ? Math.max(b.minY(), Math.min(rq.cutY, b.maxY()))
				: Math.max(b.minY(), Math.min(b.maxY(), Math.max(b.minY(), 0) + 5));
		out.add(new ViewSpec("plan", what + ", plan cut at y=" + cut,
				new Renderer.Clip(b.minX(), b.minY(), b.minZ(), b.maxX(), cut, b.maxZ()), new Renderer.Camera(0, 90, fit(Math.max(b.sizeX(), b.sizeZ()), mul))));
		if (b.sizeX() >= b.sizeZ()) {
			int z = rq.sectionAt != null ? Math.max(b.minZ(), Math.min(rq.sectionAt, b.maxZ())) : (b.minZ() + b.maxZ()) / 2;
			out.add(new ViewSpec("section", what + ", section at z=" + z + " looking north",
					new Renderer.Clip(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), z),
					new Renderer.Camera(0, 10, fit(Math.max(b.sizeX(), b.sizeY() * 1.5), mul))));
		} else {
			int x = rq.sectionAt != null ? Math.max(b.minX(), Math.min(rq.sectionAt, b.maxX())) : (b.minX() + b.maxX()) / 2;
			out.add(new ViewSpec("section", what + ", section at x=" + x + " looking west",
					new Renderer.Clip(b.minX(), b.minY(), b.minZ(), x, b.maxY(), b.maxZ()),
					new Renderer.Camera(90, 10, fit(Math.max(b.sizeZ(), b.sizeY() * 1.5), mul))));
		}
		return out;
	}

	private static String safe(String s) {
		return s.replaceAll("[^A-Za-z0-9_.,@-]", "_");
	}

	/** Models and textures loaded once, to render many pieces. Not thread safe: one render at a time. */
	public static final class Session {
		private final BlockModels models = new BlockModels();

		private Session() {
		}

		/** Textures or models the renderer could not find so far. */
		public List<String> problems() {
			return new ArrayList<>(models.problems);
		}

		/**
		 * Draws {@code plan} as asked and writes the images and the report into {@code rq.out}. The plan
		 * should come from {@link PlanSource#plan} (or any {@link BuildPlan}).
		 */
		public Result render(BuildPlan plan, Request rq) throws Exception {
			Box box = resolve(plan, rq.target, rq.pad);
			Files.createDirectories(rq.out);
			String name = safe(rq.name != null ? rq.name : rq.target.label());
			String what = rq.target.kind().equals("whole") ? "HQ" : rq.target.label();
			List<Path> images = new ArrayList<>();
			if (rq.views) {
				List<ViewSpec> specs = new ArrayList<>();
				for (ViewSpec v : views(box, rq, what)) {
					if (rq.only.isEmpty() || rq.only.stream().anyMatch(v.suffix()::contains)) {
						specs.add(v);
					}
				}
				if (!specs.isEmpty()) {
					// one scene for every view: the box, drawn through each view's own clip
					Renderer.Clip whole = new Renderer.Clip(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
					Scene scene = HqRender.scene(models, plan, whole, rq.npcs);
					for (ViewSpec v : specs) {
						long t0 = System.nanoTime();
						BufferedImage img = new Renderer(scene, v.clip(), v.cam()).render(v.cam().scale(), Math.max(1, rq.ss));
						HqRender.caption(img, v.title());
						Path f = rq.out.resolve(name + "_" + v.suffix() + ".png");
						Png.write(img, f);
						images.add(f);
						System.out.printf(Locale.ROOT, "%-40s %5dx%-5d %6d KB  %.1fs%n", f.getFileName(), img.getWidth(), img.getHeight(), Files.size(f) / 1024,
								(System.nanoTime() - t0) / 1e9);
					}
				}
			}
			PlanReport report = null;
			List<Path> files = new ArrayList<>();
			if (rq.report) {
				boolean whole = rq.target.kind().equals("whole");
				PlanReport.Options ro = rq.reportOptions.copy();
				ro.label = whole ? ro.label : (rq.target.label() + " " + ro.label).trim();
				report = PlanReport.analyze(plan, whole ? null : box, ro);
				files = report.write(rq.out, rq.reportName);
				for (Path p : files) {
					System.out.println("wrote " + p + " (" + Files.size(p) / 1024 + " KB)");
				}
			}
			return new Result(box, images, files, report, problems());
		}
	}

	/** A session with fresh block models. Minecraft's registries must be up ({@link HqRender#bootstrap}). */
	public static Session open() {
		return new Session();
	}

	/** Runs one request the way the CLI does: generates the plan, draws, prints the notes. */
	static void run(String layoutId, List<Integer> sizes, Request rq) throws Exception {
		PlanSource.Planned planned = PlanSource.plan(layoutId, sizes);
		System.out.println(planned.label() + ": " + planned.plan().size() + " placements, " + planned.plan().workstations().size() + " desks, footprint "
				+ planned.plan().footprint());
		rq.reportOptions.label = planned.label();
		Result r = open().render(rq.lampDemo ? LampViews.demo(planned.plan()) : planned.plan(), rq);
		System.out.println("piece " + r.box() + ": " + r.images().size() + " images" + (r.report() == null ? "" : ", report"));
		for (String p : r.notes()) {
			System.out.println("note: " + p);
		}
	}
}
