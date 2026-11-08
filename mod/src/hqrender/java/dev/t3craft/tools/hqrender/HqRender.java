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
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
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
 * (supersampling), {@code --vanilla} (no mod blocks), {@code --no-npcs}. {@code --style village} renders the
 * village layout (layout/village, {@link VillageRender}).
 */
public final class HqRender {
	private HqRender() {
	}

	private record View(String name, String title, Renderer.Clip clip, Renderer.Camera cam) {
	}

	public static void main(String[] args) throws Exception {
		Path out = Path.of("build/hq-render");
		String styleId = null;
		List<String> only = new ArrayList<>();
		double scaleMul = 1.6;
		int ss = 2;
		boolean vanilla = false;
		boolean npcs = true;
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--out" -> out = Path.of(args[++i]);
				case "--style" -> styleId = args[++i];
				case "--only" -> only.addAll(List.of(args[++i].split(",")));
				case "--scale" -> scaleMul = Double.parseDouble(args[++i]);
				case "--ss" -> ss = Integer.parseInt(args[++i]);
				case "--vanilla" -> vanilla = true;
				case "--no-npcs" -> npcs = false;
				default -> throw new IllegalArgumentException("unknown option " + args[i]);
			}
		}
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		if (!vanilla) {
			dev.t3craft.blocks.RenderBlocks.install();
		} else {
			HqFixtures.use(null);
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
	private static Scene scene(BlockModels models, BuildPlan plan, Renderer.Clip clip, boolean npcs) {
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
						|| k.equals("archive") || k.equals("thread_wall_front")) {
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

	private static void caption(BufferedImage img, String text) {
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
