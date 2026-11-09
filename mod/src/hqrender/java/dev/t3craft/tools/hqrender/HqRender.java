package dev.t3craft.tools.hqrender;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqStyle;
import dev.t3craft.hq.HqStyles;
import dev.t3craft.hq.Placement;
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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * hq-render: draws the HQ {@link BuildPlan} to PNGs, headless, without starting Minecraft. It boots
 * the vanilla registries (like the JUnit tests do), optionally registers the mod's blocks (real
 * monitors and lamps through {@link HqFixtures}), generates the plan for a few rosters and ray-casts
 * it with the block models and textures read from the Minecraft jar on the classpath.
 *
 * <pre>
 * ./gradlew hqRender                                  # every view, default style, into build/hq-render
 * ./gradlew hqRender -PhqArgs="--style classic --only full"
 * </pre>
 *
 * Options: {@code --out DIR}, {@code --style ID}, {@code --only SUBSTR} (comma-separated view-name
 * filters), {@code --scale F} (multiplies every view's pixels per block), {@code --ss N}
 * (supersampling), {@code --vanilla} (no mod blocks), {@code --no-npcs}, {@code --projects 4,10,20} (rosters of N
 * projects with varied thread counts, three environments and settled threads; the default for styles
 * with their own layout, e.g. {@code --style campus}, which also renders a 4 → 10 → 20 comparison).
 * PNGs over 480 KB are written again with a 256-colour palette.
 * {@code --style village} renders the village layout (layout/village, {@link VillageRender}).
 * {@code --style hybrid-towncenter} renders the town centre with towers ({@link TowncenterRender}).
 * {@code --style hybrid-terraces} renders the terraced acropolis (design/hybrid-terraces, {@link TerracesRender}).
 * {@code --layout ID} (feat/hq-layouts: classic, campus, ring, vertical, ensanche, village or all) renders a
 * registered layout through the game's pipeline with {@code --projects} (default 4), see {@link LayoutViews};
 * {@code --layout hybrid-ringcampus} renders that hybrid's own gallery ({@link RingCampusViews}, default 4,10,20).
 * {@code --packs DIR} (feat/hq-layout-packs) generates with the layout packs in {@code DIR} (the game's
 * {@code config/t3craft/hq-layouts}): materials, parameters and {@code .nbt} rooms, exactly as in game.
 *
 * <h2>Pieces and report (tools/hq-piece-views)</h2>
 * A region, module, group or anchor flag switches to <b>piece mode</b>: instead of the whole HQ it draws
 * just that box (see {@link PieceViews}) and writes {@code report.txt} / {@code report.json} (see
 * {@link PlanReport}) next to the PNGs. The plan is the one of {@code --layout} (default
 * {@code hybrid-ringcampus}, the citadel) grown through {@code --projects} (default 10; give the chain,
 * e.g. {@code 4,10,20}, to grow it the way the game does).
 *
 * <pre>
 * ./gradlew hqRender -PhqArgs="--region -10,0,-10:10,12,10 --scale 0.5 --ss 1"
 * ./gradlew hqRender -PhqArgs="--module module_3 --out build/hq-after --compare build/hq-before"
 * ./gradlew hqRender -PhqArgs="--anchor walk_hub_0 --radius 12"
 * ./gradlew hqRender -PhqArgs="--report-only"          # report of the whole plan, no images
 * </pre>
 *
 * <ul>
 *   <li>{@code --region x1,y1,z1:x2,y2,z2}: that box (plan-relative, inclusive, corners in any order).</li>
 *   <li>{@code --module ID}: a module of the BuildPlan by id ({@code BuildPlan.modules()}), padded by
 *   {@code --pad N} (default 1) blocks.</li>
 *   <li>{@code --group NAME}: the blocks a group owns ({@code hub}, {@code module_3}…), same padding.</li>
 *   <li>{@code --anchor NAME [--radius N]}: a cube of radius N (default 10) around an anchor; an
 *   unambiguous part of the name is enough.</li>
 *   <li>Views written, as {@code NAME_iso_se|iso_ne|iso_nw|iso_sw.png} (four corners), {@code NAME_plan.png}
 *   (cut at {@code --cut-y Y}, default 5 above the base) and {@code NAME_section.png} (vertical cut through
 *   the middle, or at {@code --section-at N}). {@code --name NAME} sets the prefix; {@code --only plan,iso}
 *   keeps just views whose suffix contains one of the words.</li>
 *   <li>{@code --scale F}: <b>in piece mode</b> multiplies the automatic fit (about 1100 px across the
 *   piece, so 1 = full detail, 0.5 = small preview); {@code --ss 1} turns supersampling off for speed.</li>
 *   <li>Look (every mode, tools/render-quality): {@code --final} (default) turns on soft sun shadows, ambient
 *   occlusion, the sky lighting with tonemap and bloom, and the Mitchell filter; {@code --preview} (or
 *   {@code --ss 1} without {@code --final}) keeps the old flat look. Override one by one with {@code --ao/--no-ao},
 *   {@code --shadows/--hard-shadows/--no-shadows}, {@code --sky/--no-sky}, {@code --bloom/--no-bloom},
 *   {@code --filter box|tent|mitchell}, {@code --shadow-rays N}. See docs/tools/hq-render.md.</li>
 *   <li>{@code --compare DIR}: after the run, for every PNG written here, the image of the same name in DIR
 *   (before) next to the new one (after) as {@code compare_NAME.png}, and {@code compare.txt} with the
 *   pixel difference and whether both {@code report.json} have the same plan signature. Works with any
 *   mode.</li>
 *   <li>Report: {@code --report-only} (no images; the whole plan unless a target is given),
 *   {@code --no-report}, {@code --no-ticks} (skip the build simulation), {@code --ticks-limit N}
 *   (entries above which it is skipped, default 600000), {@code --crew N} (builders, default 4),
 *   {@code --bpt N} (blocks per tick, default 48), {@code --low-light N} (default 10).
 *   {@code --report} alone is piece mode on the whole plan.</li>
 * </ul>
 *
 * <h2>Citadel catalog (citadel/catalog)</h2>
 * {@code --catalog FAMILY[:id][,FAMILY2…]} draws variants of {@code dev.t3craft.hq.citadel.catalog} on
 * their own, a few sizes and seeds each, in one grid ({@link CatalogViews}; {@code --front N|E|S|W|all},
 * {@code --sizes N}, {@code --seeds N}; see docs/tools/citadel-catalog.md).
 *
 * From Java (the render service): {@link #bootstrap}, then {@link PlanSource#plan} and
 * {@link PieceViews#open()}{@code .render(plan, request)}; {@link PlanReport#analyze} and {@link ImageCompare#run}
 * are public too.
 */
public final class HqRender {
	private HqRender() {
	}

	private record View(String name, String title, Renderer.Clip clip, Renderer.Camera cam) {
	}

	private static Renderer.Look look(boolean preview, java.util.Map<String, String> opts) {
		Renderer.Look l = preview ? Renderer.Look.preview() : Renderer.Look.finalLook();
		for (java.util.Map.Entry<String, String> e : opts.entrySet()) {
			switch (e.getKey()) {
				case "--ao" -> l.ao = true;
				case "--no-ao" -> l.ao = false;
				case "--shadows" -> l.shadows = true;
				case "--hard-shadows" -> {
					l.shadows = false;
					l.hardShadows = true;
				}
				case "--no-shadows" -> {
					l.shadows = false;
					l.hardShadows = false;
				}
				case "--sky" -> l.sky = true;
				case "--no-sky" -> l.sky = false;
				case "--bloom" -> l.bloom = true;
				case "--no-bloom" -> l.bloom = false;
				case "--shadow-rays" -> l.shadowRays = Math.max(1, Integer.parseInt(e.getValue()));
				case "--filter" -> {
					if (!List.of("box", "tent", "mitchell").contains(e.getValue())) {
						throw new IllegalArgumentException("--filter must be box, tent or mitchell, not " + e.getValue());
					}
					l.filter = e.getValue();
				}
				default -> {
				}
			}
		}
		return l;
	}

	/**
	 * Boots Minecraft's registries (no game, no window) and installs the mod's render blocks, or the
	 * vanilla fixtures with {@code vanilla}. Call once per JVM before anything that makes a plan.
	 */
	public static void bootstrap(boolean vanilla) throws Exception {
		dev.t3craft.tools.render.Boot.boot();
		if (vanilla) {
			HqFixtures.use(null);
		}
	}

	/** Runs {@link #render} and then, with {@code --compare DIR}, puts the images side by side with those of DIR. */
	public static void main(String[] args) throws Exception {
		// vanilla registries + the mod's blocks, once per JVM (the render daemon boots once and then calls run)
		dev.t3craft.tools.render.Boot.boot();
		run(args);
	}

	/**
	 * One render, with the options documented above, on an already booted JVM ({@code dev.t3craft.tools.render.Boot.boot()}). Leaves no
	 * state behind except {@code --vanilla}'s fixtures switch, which the caller restores
	 * ({@link HqFixtures#active()} before, {@link HqFixtures#use} after).
	 */
	public static void run(String[] args) throws Exception {
		long started = System.currentTimeMillis() - 2000;
		Path compare = null;
		Path outDir = Path.of("build/hq-render");
		List<String> rest = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("--compare") && i + 1 < args.length) {
				compare = Path.of(args[++i]);
				continue;
			}
			if (args[i].equals("--out") && i + 1 < args.length) {
				outDir = Path.of(args[i + 1]);
			}
			rest.add(args[i]);
		}
		render(rest.toArray(new String[0]));
		if (compare != null) {
			ImageCompare.run(compare, outDir, started);
		}
	}

	private static void render(String[] args) throws Exception {
		Path out = Path.of("build/hq-render");
		String styleId = null;
		List<String> only = new ArrayList<>();
		Double scaleOpt = null;
		int ss = 2;
		boolean vanilla = false;
		boolean npcs = true;
		List<Integer> projects = new ArrayList<>();
		String layoutId = null;
		Path packs = null;
		// piece mode (tools/hq-piece-views)
		PieceViews.Target target = null;
		PieceViews.Request piece = new PieceViews.Request();
		int radius = 0;
		String anchorName = null;
		boolean pieceMode = false;
		// citadel catalog gallery (citadel/catalog)
		String catalog = null;
		String catalogFront = null;
		int catalogSizes = 3;
		int catalogSeeds = 2;
		// kit/status-lamp: the status lamp gallery, and demo lamp states in plan renders
		boolean lamps = false;
		boolean lampDemo = false;
		// tools/render-quality: look switches, on in final and off in preview (see Renderer.Look)
		Boolean previewOpt = null;
		java.util.Map<String, String> lookOpts = new java.util.LinkedHashMap<>();
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--preview" -> previewOpt = true;
				case "--final" -> previewOpt = false;
				case "--ao", "--shadows", "--hard-shadows", "--sky", "--bloom", "--no-ao", "--no-shadows", "--no-sky", "--no-bloom" -> lookOpts.put(args[i], "");
				case "--filter" -> lookOpts.put("--filter", args[++i]);
				case "--shadow-rays" -> lookOpts.put("--shadow-rays", args[++i]);
				case "--out" -> out = Path.of(args[++i]);
				case "--style" -> styleId = args[++i];
				case "--layout" -> layoutId = args[++i];
				case "--packs" -> packs = Path.of(args[++i]);
				case "--only" -> only.addAll(List.of(args[++i].split(",")));
				case "--scale" -> scaleOpt = Double.parseDouble(args[++i]);
				case "--ss" -> ss = Integer.parseInt(args[++i]);
				case "--vanilla" -> vanilla = true;
				case "--no-npcs" -> npcs = false;
				case "--projects" -> {
					for (String n : args[++i].split(",")) {
						projects.add(Integer.parseInt(n.trim()));
					}
				}
				case "--region" -> {
					target = PieceViews.Target.region(args[++i]);
					pieceMode = true;
				}
				case "--module" -> {
					target = PieceViews.Target.module(args[++i]);
					pieceMode = true;
				}
				case "--group" -> {
					target = PieceViews.Target.group(args[++i]);
					pieceMode = true;
				}
				case "--anchor" -> {
					anchorName = args[++i];
					pieceMode = true;
				}
				case "--catalog" -> catalog = args[++i];
				case "--lamps" -> lamps = true;
				case "--lamp-demo" -> lampDemo = true;
				case "--front" -> catalogFront = args[++i];
				case "--sizes" -> catalogSizes = Integer.parseInt(args[++i]);
				case "--seeds" -> catalogSeeds = Integer.parseInt(args[++i]);
				case "--radius" -> radius = Integer.parseInt(args[++i]);
				case "--name" -> piece.name = args[++i];
				case "--pad" -> piece.pad = Integer.parseInt(args[++i]);
				case "--cut-y" -> piece.cutY = Integer.parseInt(args[++i]);
				case "--section-at" -> piece.sectionAt = Integer.parseInt(args[++i]);
				case "--report" -> pieceMode = true;
				case "--report-only" -> {
					piece.views = false;
					pieceMode = true;
				}
				case "--no-report" -> piece.report = false;
				case "--no-ticks" -> piece.reportOptions.ticks = false;
				case "--ticks-limit" -> piece.reportOptions.ticksLimit = Integer.parseInt(args[++i]);
				case "--crew" -> piece.reportOptions.crew = Integer.parseInt(args[++i]);
				case "--bpt" -> piece.reportOptions.blocksPerTick = Double.parseDouble(args[++i]);
				case "--low-light" -> piece.reportOptions.lowLight = Integer.parseInt(args[++i]);
				default -> throw new IllegalArgumentException("unknown option " + args[i]);
			}
		}
		// no --preview/--final: --ss 1 (what the render service adds to previews) counts as a preview
		Renderer.look = look(previewOpt != null ? previewOpt : ss <= 1, lookOpts);
		System.out.println("look: " + Renderer.look);
		double scaleMul = scaleOpt == null ? 1.6 : scaleOpt;
		if (vanilla) {
			HqFixtures.use(null);
		}
		if (lamps) {
			// kit/status-lamp: the status lamp in every state, on desks and hanging (LampViews)
			LampViews.run(out, scaleOpt == null ? 1.0 : scaleOpt, ss, only);
			return;
		}
		if (catalog != null) {
			// citadel/catalog: variants of the catalog on their own, in a grid (CatalogViews)
			CatalogViews.run(catalog, catalogFront, catalogSizes, Math.max(1, catalogSeeds), out, scaleOpt == null ? 1.0 : scaleOpt, ss, only);
			return;
		}
		if (pieceMode) {
			// tools/hq-piece-views: one box of the plan (or all of it) with its report
			if (anchorName != null) {
				target = PieceViews.Target.anchor(anchorName, radius);
			}
			piece.target = target == null ? PieceViews.Target.whole() : target;
			piece.out = out;
			piece.only = only;
			piece.ss = ss;
			piece.npcs = npcs;
			piece.scale = scaleOpt == null ? 1.0 : scaleOpt;
			piece.lampDemo = lampDemo;
			if (packs != null) {
				dev.t3craft.hq.pack.HqLayoutPacks.Load l = dev.t3craft.hq.pack.HqLayoutPacks.reload(packs);
				for (String e : l.errors()) {
					System.out.println("pack: " + e);
				}
			}
			PieceViews.run(layoutId, projects, piece);
			return;
		}
		if (packs != null) {
			// feat/hq-layout-packs: the same folder the game reads (config/t3craft/hq-layouts), so a pack edit shows here first
			dev.t3craft.hq.pack.HqLayoutPacks.Load l = dev.t3craft.hq.pack.HqLayoutPacks.reload(packs);
			for (String e : l.errors()) {
				System.out.println("pack: " + e);
			}
			System.out.println("layout packs from " + packs + ": " + (l.changedLayouts().isEmpty() ? "none" : String.join(", ", l.changedLayouts())));
		}
		if (layoutId != null) {
			// feat/hq-layouts: a registered layout (or "all") through the game's pipeline
			Files.createDirectories(out);
			BlockModels models = new BlockModels();
			LayoutViews.render(models, layoutId, out, projects.isEmpty() ? List.of(4) : projects, only, scaleMul, ss, npcs);
			for (String p : models.problems) {
				System.out.println("note: " + p);
			}
			return;
		}
		if (dev.t3craft.hq.TerraceGenerator.ID.equals(styleId)) {
			// design/hybrid-terraces: the terraced acropolis grown 4 -> 10 -> 20 (see TerracesRender)
			Files.createDirectories(out);
			TerracesRender.run(out, only, scaleMul, ss, npcs, new BlockModels());
			return;
		}
		if (dev.t3craft.hq.VillageGenerator.ID.equals(styleId)) {
			// layout/village: its own generator and views (see VillageRender)
			Files.createDirectories(out);
			VillageRender.run(out, only, scaleMul, ss, npcs, new BlockModels());
			return;
		}
		HqStyle style = styleId == null ? HqStyles.byId(HqStyles.DEFAULT_ID) : HqStyles.byId(styleId);
		if (style == null) {
			throw new IllegalArgumentException("unknown style " + styleId + "; known: " + HqStyles.ids());
		}
		Files.createDirectories(out);
		BlockModels models = new BlockModels();
		String sid = style.id();
		if (sid.equals(dev.t3craft.hq.TowncenterGrid.ID)) {
			// design/hybrid-towncenter: town centre with towers (TowncenterRender)
			TowncenterRender.render(models, style, out, only, ss, npcs);
			for (String p : models.problems) {
				System.out.println("note: " + p);
			}
			return;
		}
		if (HqStyles.towns().contains(style)) {
			TownRender.render(models, style, out, only, ss, npcs);
			for (String p : models.problems) {
				System.out.println("note: " + p);
			}
			return;
		}
		if (sid.equals("vertical")) {
			// layout/vertical: towers, bridges and podium for 4, 10 and 20 projects (VerticalRender)
			VerticalRender.run(models, style, out, only, scaleMul, ss, npcs);
			for (String p : models.problems) {
				System.out.println("note: " + p);
			}
			return;
		}
		if (sid.equals("ring")) {
			// layout/ring: growth through 4, 10 and 20 projects (Growth)
			Growth.render(style, models, out, projects.isEmpty() ? List.of(4, 10, 20) : projects, only, scaleMul, ss, npcs);
			for (String p : models.problems) {
				System.out.println("note: " + p);
			}
			return;
		}
		if (style.ownLayout() || !projects.isEmpty()) {
			CampusViews.render(models, style, out, projects.isEmpty() ? List.of(4, 10, 20) : projects, only, scaleMul, ss, npcs);
			for (String p : models.problems) {
				System.out.println("note: " + p);
			}
			return;
		}

		if (!projects.isEmpty()) {
			Growth.render(style, models, out, projects, only, scaleMul, ss, npcs);
			for (String p : models.problems) {
				System.out.println("note: " + p);
			}
			return;
		}
		Map<String, HqInput> rosters = new LinkedHashMap<>();
		rosters.put("1p", roster(new int[] {4}, 0));
		rosters.put("2p", roster(new int[] {8, 3}, 1));
		rosters.put("4p", roster(new int[] {9, 5, 7, 2}, 2));

		for (Map.Entry<String, HqInput> r : rosters.entrySet()) {
			BuildPlan plan = HqFixtures.active().apply(new HqGenerator(style).generate(HqLayout.plan(r.getValue())));
			List<View> views = new ArrayList<>();
			var fp = plan.footprint();
			int gx1 = fp.minX() - 3, gz1 = fp.minZ() - 3, gx2 = fp.maxX() + 3, gz2 = fp.maxZ() + 3;
			Renderer.Clip all = new Renderer.Clip(gx1, -2, gz1, gx2, fp.maxY(), gz2);
			Renderer.Clip cut = new Renderer.Clip(gx1, -2, gz1, gx2, 4, gz2);
			double s = r.getKey().equals("4p") ? 6 : r.getKey().equals("2p") ? 8 : 10;
			views.add(new View(sid + "_" + r.getKey() + "_full", "HQ " + sid + ", " + label(r.getKey()), all, new Renderer.Camera(45, 32, s)));
			views.add(new View(sid + "_" + r.getKey() + "_full_nw", "HQ " + sid + ", " + label(r.getKey()) + ", from the north-west", all,
					new Renderer.Camera(225, 32, s)));
			views.add(new View(sid + "_" + r.getKey() + "_cutaway", "HQ " + sid + ", " + label(r.getKey()) + ", roofs cut at y=4", cut,
					new Renderer.Camera(45, 50, s)));
			if (r.getKey().equals("1p")) {
				// close-ups: one module, the hub, sections
				Renderer.Clip module = new Renderer.Clip(3, -1, -23, 22, fp.maxY(), -8);
				views.add(new View(sid + "_module", "Module (6 desks), " + sid, module, new Renderer.Camera(45, 30, 18)));
				views.add(new View(sid + "_module_cutaway", "Module interior (cut at y=4), " + sid,
						new Renderer.Clip(3, -1, -23, 22, 4, -8), new Renderer.Camera(30, 55, 18)));
				views.add(new View(sid + "_hub", "Hub and portico, " + sid, new Renderer.Clip(-11, -1, -10, 11, fp.maxY(), 15),
						new Renderer.Camera(45, 30, 16)));
				views.add(new View(sid + "_hub_cutaway", "Hub interior (cut at y=5), " + sid, new Renderer.Clip(-11, -1, -10, 11, 5, 15),
						new Renderer.Camera(20, 55, 16)));
				views.add(new View(sid + "_section_wing", "Wing cross-section (segment 1, looking west), " + sid,
						new Renderer.Clip(9, -1, -24, 9, fp.maxY(), -8), new Renderer.Camera(90, 0, 24)));
				views.add(new View(sid + "_section_hub", "Hub cross-section (x=0, looking west), " + sid,
						new Renderer.Clip(0, -1, -12, 0, fp.maxY(), 15), new Renderer.Camera(90, 0, 18)));
				views.add(new View(sid + "_plan", "Plan view (cut at y=2), " + sid, new Renderer.Clip(gx1, -1, gz1, gx2, 2, gz2),
						new Renderer.Camera(0, 90, 12)));
			}
			for (View v : views) {
				if (!only.isEmpty() && only.stream().noneMatch(v.name()::contains)) {
					continue;
				}
				long t0 = System.nanoTime();
				Scene scene = scene(models, plan, v.clip(), npcs);
				double scale = v.cam().scale() * scaleMul;
				BufferedImage img = new Renderer(scene, v.clip(), v.cam()).render(scale, ss);
				caption(img, v.title());
				Path f = out.resolve(v.name() + ".png");
				ImageIO.write(img, "png", f.toFile());
				System.out.printf(Locale.ROOT, "%-34s %5dx%-5d %6d KB  %.1fs%n", f.getFileName(), img.getWidth(), img.getHeight(),
						Files.size(f) / 1024, (System.nanoTime() - t0) / 1e9);
			}
		}
		for (String p : models.problems) {
			System.out.println("note: " + p);
		}
	}

	private static String label(String key) {
		return switch (key) {
			case "1p" -> "1 project";
			case "2p" -> "2 projects (one with an annex)";
			default -> "4 projects (with annexes)";
		};
	}

	/**
	 * Projects with the given active-thread counts (more than 6 adds annex modules); {@code settled}
	 * threads per project go to the archive.
	 */
	static HqInput roster(int[] threads, int settled) {
		String[] names = {"t3craft", "bridge", "website", "infra"};
		String[] envs = {"vps", "vps", "acmesite", "laptop"};
		HqInput.Builder b = HqInput.builder();
		for (int p = 0; p < threads.length; p++) {
			String key = envs[p] + ":" + names[p];
			for (int t = 0; t < threads[p]; t++) {
				b.thread(key, names[p], envs[p], envs[p].toUpperCase(Locale.ROOT), key + "-t" + t);
			}
			for (int t = 0; t < settled; t++) {
				b.thread(key, names[p], envs[p], envs[p].toUpperCase(Locale.ROOT), key + "-s" + t, true);
			}
		}
		return b.build();
	}

	/** The plan in a grid, on flat ground (grass at y=0, dirt below) where the plan has nothing, plus NPC stand-ins. */
	static Scene scene(BlockModels models, BuildPlan plan, Renderer.Clip clip, boolean npcs) {
		Scene sc = new Scene(models, clip.minX(), Math.min(clip.minY(), -6), clip.minZ(), clip.maxX(), Math.max(clip.maxY(), 11), clip.maxZ());
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
			sc.set(p.rel(), p.state());
		}
		if (npcs) {
			int n = 0;
			for (Workstation w : plan.workstations()) {
				if (w.threadId() != null) {
					npc(sc, w.deskAnchor(), false, n++);
				}
			}
			for (Map.Entry<String, Anchor> e : plan.anchors().entrySet()) {
				String k = e.getKey();
				if (k.equals("board_slot_0") || k.equals("board_slot_3") || k.equals("bench_1") || k.equals("bench_7") || k.equals("walk_hub_0")
						|| k.equals("archive") || k.equals("thread_wall_front") || k.startsWith("walk_hub_")
						|| k.startsWith("walk_room_") && (k.endsWith("_0") || k.endsWith("_2")) || k.startsWith("walk_lounge_") || k.equals("walk_clock")
						|| k.equals("walk_review") || k.startsWith("walk_plazuela_")) {
					npc(sc, e.getValue(), k.startsWith("bench"), n++);
				}
			}
		}
		return sc;
	}

	private static final int[] ROBES = {0xFF6A4FB0, 0xFF2F7DB8, 0xFFC0543C, 0xFF3C9A5F, 0xFFD39A2A, 0xFF8E3F7E};

	/** A stand-in NPC (robe, head, face on the side it looks at), seated or standing at an anchor. */
	static void npc(Scene sc, Anchor a, boolean seated, int i) {
		BlockPos c = a.block();
		BlockModels.Texture robe = BlockModels.Texture.solid(ROBES[i % ROBES.length]);
		BlockModels.Texture skin = BlockModels.Texture.solid(0xFFE0B48C);
		BlockModels.Texture face = BlockModels.Texture.solid(0xFF3A2A20);
		int ry = switch (a.facing()) {
			case SOUTH -> 0;
			case WEST -> 90;
			case NORTH -> 180;
			default -> 270;
		};
		double base = seated ? 8 : 0; // seat top is half a block up
		List<BlockModels.Quad> low = new ArrayList<>();
		List<BlockModels.Quad> high = new ArrayList<>();
		// legs/robe up to y+1.1 blocks, head 0.5 above
		double top = base + 18;
		BlockModels.box(low, new double[] {4, base, 5}, new double[] {12, Math.min(16, top), 11}, robe, ry, 0xFFFFFF, false);
		if (top > 16) {
			BlockModels.box(high, new double[] {4, 0, 5}, new double[] {12, top - 16, 11}, robe, ry, 0xFFFFFF, false);
		}
		double h0 = top - 16;
		BlockModels.box(high, new double[] {4.5, h0, 4.5}, new double[] {11.5, h0 + 7, 11.5}, skin, ry, 0xFFFFFF, false);
		BlockModels.box(high, new double[] {5.5, h0 + 2.5, 11.5}, new double[] {10.5, h0 + 4, 11.7}, face, ry, 0xFFFFFF, false);
		sc.overlay(c.getX(), c.getY(), c.getZ(), "npc-low-" + i, low);
		sc.overlay(c.getX(), c.getY() + 1, c.getZ(), "npc-high-" + i, high);
	}

	static void caption(BufferedImage img, String text) {
		try {
			Graphics2D g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
			g.setColor(new Color(0x33, 0x3A, 0x44));
			g.drawString(text, 8, 18);
			g.dispose();
		} catch (RuntimeException | Error e) {
			// no fonts on this machine: images without captions
		}
	}

}
