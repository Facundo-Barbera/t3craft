package dev.t3craft.tools.hqrender;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Orthographic ray caster over a {@link Scene}: one ray per sub-pixel walks the voxel grid
 * (Amanatides–Woo), intersects the cell's quads, alpha-tests cutout texels and blends translucent
 * ones front to back. Shading: per-face Lambert from a fixed sun plus a shadow ray, emissive blocks
 * (lanterns, froglights…) at full brightness. Pure Java2D-free maths, so it runs headless.
 */
final class Renderer {
	/** What part of the scene to draw (cells outside are treated as empty, for rays and shadows). */
	record Clip(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		boolean contains(int x, int y, int z) {
			return x >= minX && y >= minY && z >= minZ && x <= maxX && y <= maxY && z <= maxZ;
		}
	}

	/**
	 * @param azimuth   degrees; 45 looks from the south-east (+x,+z), 135 from the north-east…
	 * @param elevation degrees above the horizon (30 isometric-ish, 90 top-down plan)
	 * @param scale     output pixels per block
	 */
	record Camera(double azimuth, double elevation, double scale) {
	}

	private static final double[] SUN = BlockModels.normalize(new double[] {-0.45, 1.0, 0.6});
	private static final int BACKGROUND = 0xFFEEF1F4;

	private final Scene scene;
	private final Clip clip;
	private final double[] d;
	private final double[] right;
	private final double[] up;

	Renderer(Scene scene, Clip clip, Camera cam) {
		this.scene = scene;
		this.clip = clip;
		double az = Math.toRadians(cam.azimuth());
		double el = Math.toRadians(Math.min(cam.elevation(), 89.9));
		double[] toCam = {Math.sin(az) * Math.cos(el), Math.sin(el), Math.cos(az) * Math.cos(el)};
		d = new double[] {-toCam[0], -toCam[1], -toCam[2]};
		right = BlockModels.normalize(new double[] {-d[2], 0, d[0]});
		up = cross(right, d);
	}

	BufferedImage render(double scale, int ss) {
		double sMin = Double.MAX_VALUE, sMax = -Double.MAX_VALUE, tMin = Double.MAX_VALUE, tMax = -Double.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			double[] p = {(i & 1) == 0 ? clip.minX : clip.maxX + 1, (i & 2) == 0 ? clip.minY : clip.maxY + 1, (i & 4) == 0 ? clip.minZ : clip.maxZ + 1};
			double s = BlockModels.dot(p, right);
			double t = BlockModels.dot(p, up);
			sMin = Math.min(sMin, s);
			sMax = Math.max(sMax, s);
			tMin = Math.min(tMin, t);
			tMax = Math.max(tMax, t);
		}
		double pad = 1.0;
		sMin -= pad;
		tMin -= pad;
		sMax += pad;
		tMax += pad;
		int w = (int) Math.ceil((sMax - sMin) * scale);
		int h = (int) Math.ceil((tMax - tMin) * scale);
		int W = w * ss;
		int H = h * ss;
		double px = scale * ss;
		int[] buf = new int[W * H];
		final double s0 = sMin;
		final double t1 = tMax;
		IntStream.range(0, H).parallel().forEach(py -> {
			for (int x = 0; x < W; x++) {
				double s = s0 + (x + 0.5) / px;
				double t = t1 - (py + 0.5) / px;
				buf[py * W + x] = trace(s, t);
			}
		});
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int r = 0, g = 0, b = 0;
				for (int j = 0; j < ss; j++) {
					for (int i = 0; i < ss; i++) {
						int c = buf[(y * ss + j) * W + x * ss + i];
						r += (c >> 16) & 255;
						g += (c >> 8) & 255;
						b += c & 255;
					}
				}
				int n = ss * ss;
				img.setRGB(x, y, ((r / n) << 16) | ((g / n) << 8) | (b / n));
			}
		}
		return img;
	}

	private record Hit(double t, BlockModels.Quad q, int argb, double[] p) {
	}

	private int trace(double s, double t) {
		double[] o = {s * right[0] + t * up[0] - d[0] * 2000, s * right[1] + t * up[1] - d[1] * 2000, s * right[2] + t * up[2] - d[2] * 2000};
		double[] range = slab(o, d);
		if (range == null) {
			return BACKGROUND;
		}
		double r = 0, g = 0, b = 0, a = 0;
		List<Hit> pending = new ArrayList<>(4);
		Walker wk = new Walker(o, d, range[0], range[1]);
		while (wk.next()) {
			if (clip.contains(wk.x, wk.y, wk.z)) {
				for (BlockModels.Quad q : scene.at(wk.x, wk.y, wk.z)) {
					Hit h = hit(q, wk.x, wk.y, wk.z, o, d, true);
					if (h != null) {
						pending.add(h);
					}
				}
			}
			if (pending.isEmpty()) {
				continue;
			}
			pending.sort((h1, h2) -> Double.compare(h1.t, h2.t));
			while (!pending.isEmpty() && pending.get(0).t <= wk.tExit + 1e-6) {
				Hit h = pending.remove(0);
				double alpha = ((h.argb >>> 24) & 255) / 255.0;
				if (alpha < 0.94) {
					alpha *= 0.85;
				}
				double light = shade(h);
				double k = (1 - a) * alpha;
				r += k * ((h.argb >> 16) & 255) * light;
				g += k * ((h.argb >> 8) & 255) * light;
				b += k * (h.argb & 255) * light;
				a += k;
				if (a > 0.985) {
					return pack(r, g, b, a);
				}
			}
		}
		for (Hit h : pending) { // overhanging quads past the last cell
			double alpha = ((h.argb >>> 24) & 255) / 255.0;
			double light = shade(h);
			double k = (1 - a) * alpha;
			r += k * ((h.argb >> 16) & 255) * light;
			g += k * ((h.argb >> 8) & 255) * light;
			b += k * (h.argb & 255) * light;
			a += k;
		}
		return pack(r, g, b, a);
	}

	private static int pack(double r, double g, double b, double a) {
		double rest = 1 - Math.min(1, a);
		r += rest * ((BACKGROUND >> 16) & 255);
		g += rest * ((BACKGROUND >> 8) & 255);
		b += rest * (BACKGROUND & 255);
		return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	private static int clamp(double v) {
		return (int) Math.max(0, Math.min(255, Math.round(v)));
	}

	private double shade(Hit h) {
		if (h.q.emissive) {
			return 1.08;
		}
		double[] n = h.q.n;
		// Minecraft-like directional face factors, plus sun
		double face = Math.abs(n[1]) > 0.5 ? (n[1] > 0 ? 1.0 : 0.55) : Math.abs(n[2]) > Math.abs(n[0]) ? 0.82 : 0.68;
		double lambert = Math.max(0, BlockModels.dot(n, SUN));
		double direct = 0.35 + 0.65 * lambert;
		if (lambert > 0 && occluded(h.p, n)) {
			direct = 0.35;
		}
		return face * (0.45 + 0.6 * direct);
	}

	private boolean occluded(double[] p, double[] n) {
		double[] o = {p[0] + n[0] * 0.003 + SUN[0] * 0.003, p[1] + n[1] * 0.003 + SUN[1] * 0.003, p[2] + n[2] * 0.003 + SUN[2] * 0.003};
		double[] range = slab(o, SUN);
		if (range == null) {
			return false;
		}
		Walker wk = new Walker(o, SUN, Math.max(0, range[0]), Math.min(range[1], 48));
		while (wk.next()) {
			if (!clip.contains(wk.x, wk.y, wk.z)) {
				continue;
			}
			for (BlockModels.Quad q : scene.at(wk.x, wk.y, wk.z)) {
				if (q.emissive && q.tex.w == 1) {
					continue;
				}
				Hit h = hit(q, wk.x, wk.y, wk.z, o, SUN, false);
				if (h != null && h.t > 1e-4 && ((h.argb >>> 24) & 255) > 200) {
					return true;
				}
			}
		}
		return false;
	}

	/** Ray/quad intersection in cell (cx,cy,cz); null on miss, back face (when culling) or a transparent texel. */
	private static Hit hit(BlockModels.Quad q, int cx, int cy, int cz, double[] o, double[] dir, boolean cull) {
		double denom = BlockModels.dot(q.n, dir);
		if (cull ? denom >= -1e-9 : Math.abs(denom) < 1e-9) {
			return null;
		}
		double px = cx + q.p0[0], py = cy + q.p0[1], pz = cz + q.p0[2];
		double t = (q.n[0] * (px - o[0]) + q.n[1] * (py - o[1]) + q.n[2] * (pz - o[2])) / denom;
		if (t < 0) {
			return null;
		}
		double hx = o[0] + t * dir[0] - px, hy = o[1] + t * dir[1] - py, hz = o[2] + t * dir[2] - pz;
		double a = (hx * q.e1[0] + hy * q.e1[1] + hz * q.e1[2]) / q.e1len2;
		double b = (hx * q.e2[0] + hy * q.e2[1] + hz * q.e2[2]) / q.e2len2;
		if (a < -1e-6 || a > 1 + 1e-6 || b < -1e-6 || b > 1 + 1e-6) {
			return null;
		}
		int c = q.sample(Math.min(a, 0.99999), Math.min(b, 0.99999));
		if (((c >>> 24) & 255) < 16) {
			return null;
		}
		return new Hit(t, q, c, new double[] {hx + px, hy + py, hz + pz});
	}

	/** Entry/exit distances of a ray through the clip box, or null when it misses. */
	private double[] slab(double[] o, double[] dir) {
		double[] lo = {clip.minX, clip.minY, clip.minZ};
		double[] hi = {clip.maxX + 1, clip.maxY + 1, clip.maxZ + 1};
		double t0 = -Double.MAX_VALUE, t1 = Double.MAX_VALUE;
		for (int i = 0; i < 3; i++) {
			if (Math.abs(dir[i]) < 1e-12) {
				if (o[i] < lo[i] || o[i] > hi[i]) {
					return null;
				}
				continue;
			}
			double a = (lo[i] - o[i]) / dir[i];
			double b = (hi[i] - o[i]) / dir[i];
			t0 = Math.max(t0, Math.min(a, b));
			t1 = Math.min(t1, Math.max(a, b));
		}
		return t0 <= t1 && t1 >= 0 ? new double[] {Math.max(t0, 0), t1} : null;
	}

	/** Voxel walk from tStart to tEnd; after {@link #next()}, (x,y,z) is the current cell and tExit where the ray leaves it. */
	private static final class Walker {
		int x;
		int y;
		int z;
		double tExit;
		private final int stepX;
		private final int stepY;
		private final int stepZ;
		private double tMaxX;
		private double tMaxY;
		private double tMaxZ;
		private final double tDX;
		private final double tDY;
		private final double tDZ;
		private final double tEnd;
		private boolean started;

		Walker(double[] o, double[] dir, double tStart, double tEnd) {
			this.tEnd = tEnd;
			double t = tStart + 1e-7;
			double px = o[0] + dir[0] * t, py = o[1] + dir[1] * t, pz = o[2] + dir[2] * t;
			x = (int) Math.floor(px);
			y = (int) Math.floor(py);
			z = (int) Math.floor(pz);
			stepX = dir[0] > 0 ? 1 : -1;
			stepY = dir[1] > 0 ? 1 : -1;
			stepZ = dir[2] > 0 ? 1 : -1;
			tDX = Math.abs(1 / dir[0]);
			tDY = Math.abs(1 / dir[1]);
			tDZ = Math.abs(1 / dir[2]);
			tMaxX = t + (dir[0] > 0 ? (x + 1 - px) : (px - x)) * tDX;
			tMaxY = t + (dir[1] > 0 ? (y + 1 - py) : (py - y)) * tDY;
			tMaxZ = t + (dir[2] > 0 ? (z + 1 - pz) : (pz - z)) * tDZ;
		}

		boolean next() {
			if (started) {
				if (tMaxX < tMaxY && tMaxX < tMaxZ) {
					x += stepX;
					tMaxX += tDX;
				} else if (tMaxY < tMaxZ) {
					y += stepY;
					tMaxY += tDY;
				} else {
					z += stepZ;
					tMaxZ += tDZ;
				}
			}
			started = true;
			double enter = tExit;
			tExit = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
			return enter <= tEnd;
		}
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}
}
