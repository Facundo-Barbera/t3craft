package dev.t3craft.tools.hqrender;

import dev.t3craft.hq.HqKit;
import dev.t3craft.hq.RingCampusStyle;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Catalog;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The citadel catalog gallery ({@code --catalog}): builds variants of {@link Catalog} on their own,
 * each in a few plot sizes and seeds, every plot on a grass pad ringed by a path, all in one grid,
 * and draws {@code catalog_iso_se.png}, {@code catalog_iso_nw.png} and {@code catalog_plan.png} with
 * the id of each plot written next to it. Seconds per render, no plan generated: what a variant's
 * author looks at while iterating (see {@code docs/tools/citadel-catalog.md}).
 *
 * <pre>
 * tools/render/submit.sh -- --catalog LAMP                       # every lamp
 * tools/render/submit.sh -- --catalog HOUSE:house_timber_cottage # just that one
 * tools/render/submit.sh -- --catalog LAMP,HOUSE --front all     # every variant in the 4 orientations
 * </pre>
 *
 * Options read here: {@code --front N|E|S|W|all} (default S: the facade looks at the iso_se camera; a
 * row per front with {@code all}), {@code --sizes N} (1–3 sizes per variant, default 3),
 * {@code --seeds N} (default 2), plus the usual {@code --out}, {@code --scale}, {@code --ss} and
 * {@code --only}. Prints (and writes {@code report.txt}) the block count of every build, the writes the
 * plot clipped (outside its box) and any block found outside its plot.
 */
final class CatalogViews {
	private CatalogViews() {
	}

	/** One build of the gallery: a variant in a plot. */
	private record Cell(Variant v, Plot plot, String label, int row) {
	}

	/** Upper end of each family's typical height (the gallery's tallest size). */
	private static int typicalMaxH(Family f) {
		return switch (f) {
			case HOUSE -> 18;
			case LAMP -> 6;
			case ANNEX, STALL, BENCH, PLANTER, SIGNPOST, BANNER_POLE -> 8;
			case FOUNTAIN, WELL -> 7;
			case TREE -> 14;
			case GARDEN -> 5;
			case GATE -> 14; // the city asks for scy(10) = 14 at scale 2
			case STATUE, PLAZA_CENTER, TOWER_TOP -> 10;
			case GAZEBO -> 13; // the patio's gazebo plot at scale 200 is 7–13 × 7–13 × 13
			case BRIDGE -> 6;
			case ROOF -> 8;
		};
	}

	/** Upper end of each family's typical frontage/depth, for variants that declare no maximum. */
	private static int cap(int max, int min) {
		return Math.max(min, Math.min(max, 24));
	}

	static void run(String spec, String fronts, int nSizes, int nSeeds, Path out, double scaleMul, int ss, List<String> only) throws Exception {
		List<Variant> variants = select(spec);
		if (variants.isEmpty()) {
			throw new IllegalArgumentException("--catalog " + spec + ": no variants (known: " + ids() + ")");
		}
		List<Direction> dirs = fronts(fronts);
		Files.createDirectories(out);

		// ---- lay out the grid: one row per variant and front, cells = sizes × seeds
		List<Cell> cells = new ArrayList<>();
		int gap = 2; // path ring + one grass cell between pads
		int z = 0;
		int row = 0;
		int maxH = 1;
		for (Variant v : variants) {
			for (Direction f : dirs) {
				int x = 0;
				int rowDepth = 0;
				for (int[] sz : sizes(v, nSizes)) {
					for (int s = 0; s < nSeeds; s++) {
						long seed = 1 + s * 7919L;
						boolean side = f.getAxis() == Direction.Axis.X;
						int sizeX = side ? sz[1] : sz[0];
						int sizeZ = side ? sz[0] : sz[1];
						Plot p = new Plot(x, 1, z, sz[0], sz[1], sz[2], f, seed);
						String label = v.id() + " " + sz[0] + "x" + sz[1] + "x" + sz[2] + " s" + seed + (dirs.size() > 1 ? " " + f.getSerializedName().charAt(0) : "");
						cells.add(new Cell(v, p, label, row));
						x += sizeX + 2 * gap + 1;
						rowDepth = Math.max(rowDepth, sizeZ);
						maxH = Math.max(maxH, sz[2]);
					}
				}
				z += rowDepth + 2 * gap + 3; // room for the label row
				row++;
			}
		}

		// ---- build every cell on its own canvas, then into one scene
		RingCampusStyle style = new RingCampusStyle();
		int gx2 = 0;
		for (Cell c : cells) {
			gx2 = Math.max(gx2, c.plot().x0() + c.plot().sizeX() + gap);
		}
		int gz2 = z;
		BlockModels models = new BlockModels();
		Scene scene = new Scene(models, -gap - 1, -3, -gap - 1, gx2, maxH + 2, gz2);
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		BlockState path = Blocks.DIRT_PATH.defaultBlockState();
		BlockState gravel = Blocks.GRAVEL.defaultBlockState();
		for (int x = scene.minX; x < scene.minX + scene.sx; x++) {
			for (int zz = scene.minZ; zz < scene.minZ + scene.sz; zz++) {
				scene.set(new BlockPos(x, -2, zz), dirt);
				scene.set(new BlockPos(x, -1, zz), dirt);
				scene.set(new BlockPos(x, 0, zz), gravel);
			}
		}
		StringBuilder rep = new StringBuilder();
		rep.append("catalog gallery: ").append(spec).append(" — ").append(variants.size()).append(" variant(s), ")
				.append(cells.size()).append(" build(s), fronts ").append(dirs).append('\n');
		rep.append(String.format(Locale.ROOT, "%-44s %6s %8s %8s  %s%n", "build", "blocks", "clipped", "outside", "notes"));
		int problems = 0;
		// walkability audit (docs/tools/citadel-catalog.md): on by default for the gallery's report, -Dt3craft.walkAudit=false skips it
		boolean walkAudit = !"false".equalsIgnoreCase(System.getProperty("t3craft.walkAudit"));
		StringBuilder walkFlags = new StringBuilder();
		StringBuilder walkDump = new StringBuilder();
		StringBuilder walkBlocks = new StringBuilder();
		for (Cell c : cells) {
			Plot p = c.plot();
			int x1 = p.x0();
			int z1 = p.z0();
			int x2 = x1 + p.sizeX() - 1;
			int z2 = z1 + p.sizeZ() - 1;
			// pad: grass inside, a path ring around it
			for (int x = x1 - gap; x <= x2 + gap; x++) {
				for (int zz = z1 - gap; zz <= z2 + gap; zz++) {
					boolean in = x >= x1 && x <= x2 && zz >= z1 && zz <= z2;
					boolean ring = !in && x >= x1 - 1 && x <= x2 + 1 && zz >= z1 - 1 && zz <= z2 + 1;
					scene.set(new BlockPos(x, 0, zz), in ? grass : ring ? path : grass);
				}
			}
			CitadelCanvas canvas = new CitadelCanvas(style, HqKit.active());
			String note = "";
			try {
				c.v().build(canvas, p);
			} catch (RuntimeException e) {
				note = "BUILD FAILED: " + e;
				problems++;
			}
			int blocks = 0;
			int outside = 0;
			Map<String, Integer> byBlock = new TreeMap<>();
			for (Map.Entry<Long, BlockState> e : canvas.cells.entrySet()) {
				BlockPos pos = BlockPos.of(e.getKey());
				BlockState s = e.getValue();
				if (!p.containsWorld(pos.getX(), pos.getY(), pos.getZ())) {
					outside++;
				}
				if (s.isAir()) {
					continue;
				}
				blocks++;
				byBlock.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath(), 1, Integer::sum);
				scene.set(pos, s);
			}
			if (p.clipped() > 0 || outside > 0) {
				problems++;
			}
			rep.append(String.format(Locale.ROOT, "%-44s %6d %8d %8d  %s%n", c.label(), blocks, p.clipped(), outside, note));
			if (walkAudit) {
				try {
					walkBlocks.append("# ").append(c.label()).append('\n');
					walkDump.append("# ").append(c.label()).append(" box ").append(p.sizeX()).append('x').append(p.sizeZ()).append('x').append(p.h()).append(" front ").append(p.front()).append('\n');
					for (Map.Entry<Long, BlockState> be : canvas.cells.entrySet()) {
						if (!be.getValue().isAir()) {
							BlockPos bp = BlockPos.of(be.getKey());
							walkBlocks.append("B ").append(bp.getX() - p.x0()).append(' ').append(bp.getY() - 1).append(' ').append(bp.getZ() - p.z0()).append(' ')
									.append(be.getValue().toString().replace("Block{minecraft:", "").replace("}[", " ").replace("}", "")).append('\n');
						}
					}
					List<String> w = WalkAudit.run(p, canvas, gap, walkDump);
					for (String line : w) {
						rep.append("    walk: ").append(line).append('\n');
						if (line.startsWith("FLAG")) {
							walkFlags.append(String.format(Locale.ROOT, "%-44s %s%n", c.label(), line));
						}
					}
				} catch (RuntimeException e) {
					rep.append("    walk: audit failed: ").append(e).append('\n');
				}
			}
			if (c == firstOf(cells, c.v())) {
				rep.append("    palette: ");
				byBlock.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(14)
						.forEach(e -> rep.append(e.getKey()).append('×').append(e.getValue()).append(' '));
				rep.append('\n');
			}
		}
		rep.append(problems == 0 ? "OK: every build stayed inside its plot.\n"
				: "WARNING: " + problems + " build(s) clipped, wrote outside the plot or failed (clipped = writes dropped by Plot; outside = blocks set on the canvas directly, outside the box).\n");

		if (walkDump.length() > 0) {
			Files.writeString(out.resolve("walk_cells.txt"), walkDump.toString());
			Files.writeString(out.resolve("walk_blocks.txt"), walkBlocks.toString());
		}
		if (walkFlags.length() > 0) {
			rep.append("WALK FLAGS (builds with something to look at):\n").append(walkFlags);
		}

		// ---- views
		Renderer.Clip all = new Renderer.Clip(scene.minX, -1, scene.minZ, scene.minX + scene.sx - 1, maxH + 1, scene.minZ + scene.sz - 1);
		int ex = scene.sx;
		int ez = scene.sz;
		double iso = fit(Math.max((ex + ez) * 0.75, (maxH + 2) * 1.2), scaleMul);
		double plan = fit(Math.max(ex, ez), scaleMul);
		List<Object[]> views = List.of(
				new Object[] {"iso_se", "from the south-east", new Renderer.Camera(45, 32, iso)},
				new Object[] {"iso_nw", "from the north-west", new Renderer.Camera(225, 32, iso)},
				new Object[] {"plan", "plan (top down)", new Renderer.Camera(0, 90, plan)});
		for (Object[] v : views) {
			String suffix = (String) v[0];
			if (!only.isEmpty() && only.stream().noneMatch(suffix::contains)) {
				continue;
			}
			Renderer.Camera cam = (Renderer.Camera) v[2];
			long t0 = System.nanoTime();
			BufferedImage img = new Renderer(scene, all, cam).render(cam.scale(), Math.max(1, ss));
			labels(img, all, cam, cells);
			HqRender.caption(img, "catalog " + spec + ", " + v[1] + " (front " + (dirs.size() > 1 ? "N/E/S/W rows" : dirs.get(0).getSerializedName()) + ")");
			Path f = out.resolve("catalog_" + suffix + ".png");
			Png.write(img, f);
			System.out.printf(Locale.ROOT, "%-28s %5dx%-5d %6d KB  %.1fs%n", f.getFileName(), img.getWidth(), img.getHeight(), Files.size(f) / 1024,
					(System.nanoTime() - t0) / 1e9);
		}
		System.out.print(rep);
		Files.writeString(out.resolve("report.txt"), rep.toString());
		for (String n : models.problems) {
			System.out.println("note: " + n);
		}
	}

	private static Cell firstOf(List<Cell> cells, Variant v) {
		for (Cell c : cells) {
			if (c.v() == v) {
				return c;
			}
		}
		return null;
	}

	/** Pixels per block fitting about 1400 px across {@code extent} blocks, within 2..40, times {@code mul}. */
	private static double fit(double extent, double mul) {
		return Math.max(0.5, Math.max(2, Math.min(40, 1400.0 / Math.max(extent, 1))) * mul);
	}

	// ------------------------------------------------------------------ selection

	/** {@code FAMILY[:id][,FAMILY2[:id2]…]}; a bare id works too. */
	static List<Variant> select(String spec) {
		List<Variant> out = new ArrayList<>();
		for (String part : spec.split(",")) {
			String t = part.trim();
			if (t.isEmpty()) {
				continue;
			}
			String fam = t;
			String id = null;
			int colon = t.indexOf(':');
			if (colon >= 0) {
				fam = t.substring(0, colon);
				id = t.substring(colon + 1);
			}
			Family f = null;
			try {
				f = Family.valueOf(fam.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				Variant v = Catalog.byId(t);
				if (v == null) {
					throw new IllegalArgumentException("--catalog: '" + t + "' is neither a family " + List.of(Family.values()) + " nor a variant id (known: " + ids() + ")");
				}
				out.add(v);
				continue;
			}
			if (id == null || id.isEmpty() || id.equals("*")) {
				out.addAll(Catalog.of(f));
			} else {
				Variant v = Catalog.byId(id);
				if (v == null || v.family() != f) {
					throw new IllegalArgumentException("--catalog: no " + f + " variant '" + id + "' (known: " + Catalog.of(f).stream().map(Variant::id).toList() + ")");
				}
				out.add(v);
			}
		}
		return out;
	}

	private static List<String> ids() {
		return Catalog.all().stream().map(Variant::id).toList();
	}

	private static List<Direction> fronts(String s) {
		if (s == null || s.isBlank()) {
			return List.of(Direction.SOUTH);
		}
		if (s.equalsIgnoreCase("all")) {
			return List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
		}
		List<Direction> out = new ArrayList<>();
		for (String p : s.split(",")) {
			out.add(switch (p.trim().toUpperCase(Locale.ROOT)) {
				case "N", "NORTH" -> Direction.NORTH;
				case "E", "EAST" -> Direction.EAST;
				case "W", "WEST" -> Direction.WEST;
				case "S", "SOUTH" -> Direction.SOUTH;
				default -> throw new IllegalArgumentException("--front wants N, E, S, W or all, got " + p);
			});
		}
		return out;
	}

	/** {@code --box WxDxH[,WxDxH...]}: exact sizes for every variant (the sizes the city asks for), or null for the spread. */
	static String boxes;

	/** {w, d, h} sizes spread over the variant's range: min, middle, max (deduplicated, up to n). */
	private static List<int[]> sizes(Variant v, int n) {
		if (boxes != null) {
			List<int[]> fixed = new ArrayList<>();
			for (String b : boxes.split(",")) {
				String[] t = b.trim().split("x");
				if (t.length != 3) {
					throw new IllegalArgumentException("--box wants WxDxH[,WxDxH...], got " + boxes);
				}
				fixed.add(new int[] {Integer.parseInt(t[0]), Integer.parseInt(t[1]), Integer.parseInt(t[2])});
			}
			return fixed;
		}
		int w1 = v.minW();
		int w2 = cap(v.maxW(), w1);
		int d1 = v.minD();
		int d2 = cap(v.maxD(), d1);
		int h1 = v.minH();
		int h2 = Math.max(h1, typicalMaxH(v.family()));
		List<int[]> out = new ArrayList<>();
		int k = Math.max(1, Math.min(3, n));
		for (int i = 0; i < k; i++) {
			double t = k == 1 ? 0.5 : i / (double) (k - 1);
			int[] s = {lerp(w1, w2, t), lerp(d1, d2, t), lerp(h1, h2, t)};
			if (out.stream().noneMatch(o -> o[0] == s[0] && o[1] == s[1] && o[2] == s[2])) {
				out.add(s);
			}
		}
		return out;
	}

	private static int lerp(int a, int b, double t) {
		return (int) Math.round(a + (b - a) * t);
	}

	// ------------------------------------------------------------------ labels

	/** Writes each cell's label just in front (south) of its pad, projected like {@link Renderer} does. */
	private static void labels(BufferedImage img, Renderer.Clip clip, Renderer.Camera cam, List<Cell> cells) {
		try {
			double az = Math.toRadians(cam.azimuth());
			double el = Math.toRadians(Math.min(cam.elevation(), 89.9));
			double[] toCam = {Math.sin(az) * Math.cos(el), Math.sin(el), Math.cos(az) * Math.cos(el)};
			double[] d = {-toCam[0], -toCam[1], -toCam[2]};
			double[] right = BlockModels.normalize(new double[] {-d[2], 0, d[0]});
			double[] up = {right[1] * d[2] - right[2] * d[1], right[2] * d[0] - right[0] * d[2], right[0] * d[1] - right[1] * d[0]};
			double sMin = Double.MAX_VALUE;
			double tMax = -Double.MAX_VALUE;
			for (int i = 0; i < 8; i++) {
				double[] p = {(i & 1) == 0 ? clip.minX() : clip.maxX() + 1, (i & 2) == 0 ? clip.minY() : clip.maxY() + 1,
						(i & 4) == 0 ? clip.minZ() : clip.maxZ() + 1};
				sMin = Math.min(sMin, BlockModels.dot(p, right));
				tMax = Math.max(tMax, BlockModels.dot(p, up));
			}
			sMin -= 1.0;
			tMax += 1.0;
			Graphics2D g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			int px = (int) Math.max(9, Math.min(13, cam.scale() * 0.9));
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, px));
			FontMetrics fm = g.getFontMetrics();
			for (Cell c : cells) {
				Plot p = c.plot();
				// the middle of the pad's south edge, on the ground (two cells out: on the path ring)
				double[] at = {p.x0() + p.sizeX() / 2.0, 1, p.z0() + p.sizeZ() + 2.0};
				int x = (int) ((BlockModels.dot(at, right) - sMin) * cam.scale());
				int y = (int) ((tMax - BlockModels.dot(at, up)) * cam.scale());
				String text = c.label();
				int tw = fm.stringWidth(text);
				int bx = x - tw / 2 - 3;
				g.setColor(new Color(255, 255, 255, 200));
				g.fillRect(bx, y - fm.getAscent(), tw + 6, fm.getHeight());
				g.setColor(new Color(0x22, 0x28, 0x30));
				g.drawString(text, bx + 3, y);
			}
			g.dispose();
		} catch (RuntimeException | Error e) {
			// no fonts on this machine: images without labels
		}
	}
}
