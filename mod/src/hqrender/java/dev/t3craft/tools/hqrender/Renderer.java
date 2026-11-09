package dev.t3craft.tools.hqrender;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Orthographic ray caster over a {@link Scene}: one ray per sub-pixel walks the voxel grid
 * (Amanatides–Woo), intersects the cell's quads, alpha-tests cutout texels and blends translucent
 * ones front to back. Pure maths, no Java2D, so it runs headless.
 *
 * <p>The look is chosen by {@link #look} (tools/render-quality; set per run by {@code HqRender}):
 * <ul>
 *   <li><b>sky</b>: linear-light shading with a warm sun from the south-west at about 45°, a cool sky
 *   hemisphere and a little warm ground bounce, a soft shoulder tonemap, a sky-gradient background and
 *   a faint bloom around emissive blocks. Off: the old flat Minecraft-like face factors.</li>
 *   <li><b>shadows</b>: soft sun shadows, several jittered rays toward a sun disc a few degrees wide
 *   (penumbra grows with the distance to the occluder). Off: one hard shadow ray, as before.</li>
 *   <li><b>ao</b>: ambient occlusion in the spirit of Minecraft's smooth lighting, per pixel: the
 *   blocks in front of the face are read at half-block and at block resolution and interpolated
 *   between the corners, so joints, inner corners and wall bases darken.</li>
 *   <li><b>filter</b>: how sub-samples become pixels: Mitchell–Netravali (radius 1.25 px, B = C = 1/3),
 *   a tent, or the plain box average; resolved in linear light.</li>
 * </ul>
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

	/** Image-quality switches (flags {@code --ao --shadows --sky --bloom --filter}); on in final, off in preview. */
	static final class Look {
		boolean ao;
		/** Soft (jittered, penumbra) sun shadows; when false, {@link #hardShadows} decides. */
		boolean shadows;
		/** The old single hard shadow ray, used when {@link #shadows} is off. */
		boolean hardShadows = true;
		boolean sky;
		boolean bloom;
		/** box, tent or mitchell. */
		String filter;
		/** Shadow rays per sub-sample in soft mode. */
		int shadowRays = 2;
		/** Angular radius of the sun disc for soft shadows, degrees. */
		double sunRadius = 3.0;

		static Look finalLook() {
			Look l = new Look();
			l.ao = true;
			l.shadows = true;
			l.sky = true;
			l.bloom = true;
			l.filter = "mitchell";
			return l;
		}

		static Look preview() {
			Look l = new Look();
			l.filter = "box";
			return l;
		}

		@Override
		public String toString() {
			return "ao=" + ao + " shadows=" + (shadows ? "soft x" + shadowRays : hardShadows ? "hard" : "off") + " sky=" + sky + " bloom=" + bloom
					+ " filter=" + filter;
		}
	}

	/** The look of the next renders (one render job at a time per JVM sets it; see HqRender). */
	static volatile Look look = Look.finalLook();

	/** Old sun (flat look) and the new one: from the south-west, about 45° up. */
	private static final double[] SUN_FLAT = BlockModels.normalize(new double[] {-0.45, 1.0, 0.6});
	private static final double[] SUN_SKY = BlockModels.normalize(new double[] {-0.5, 0.74, 0.5});
	private static final double[] SUN_C = {1.02, 0.95, 0.82};
	private static final double[] SKY_C = {0.27, 0.31, 0.37};
	private static final double[] GROUND_C = {0.19, 0.17, 0.14};
	private static final double[] EMIT_C = {1.30, 1.26, 1.18};
	/** Radius of the Mitchell filter in output pixels (1.25: crisp, still no stair-stepping). */
	private static final double FILTER_RADIUS = 1.25;
	private static final int BACKGROUND = 0xFFEEF1F4;
	private static final int SKY_TOP = 0xD3E2EF;
	private static final int SKY_BOTTOM = 0xF4F2EE;
	static final double[] LIN = new double[256];
	private static final double[] DEC = new double[1024];
	private static final byte[] SRGB = new byte[4097];

	static {
		for (int i = 0; i < 256; i++) {
			double c = i / 255.0;
			LIN[i] = c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
		}
		for (int i = 0; i < 1024; i++) {
			double e = i / 1023.0;
			DEC[i] = e * e;
		}
		for (int i = 0; i <= 4096; i++) {
			double v = i / 4096.0;
			double c = v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
			SRGB[i] = (byte) Math.round(Math.max(0, Math.min(1, c)) * 255);
		}
	}

	// package-private: GpuTrace (tools/render-gpu) exports them to the GPU
	final Scene scene;
	final Clip clip;
	final double[] d;
	final double[] right;
	final double[] up;
	final Look lk;
	final double[] sun;
	final double[] sunU;
	final double[] sunV;

	Renderer(Scene scene, Clip clip, Camera cam) {
		this.scene = scene;
		this.clip = clip;
		this.lk = look;
		double az = Math.toRadians(cam.azimuth());
		double el = Math.toRadians(Math.min(cam.elevation(), 89.9));
		double[] toCam = {Math.sin(az) * Math.cos(el), Math.sin(el), Math.cos(az) * Math.cos(el)};
		d = new double[] {-toCam[0], -toCam[1], -toCam[2]};
		right = BlockModels.normalize(new double[] {-d[2], 0, d[0]});
		up = cross(right, d);
		sun = lk.sky ? SUN_SKY : SUN_FLAT;
		sunU = BlockModels.normalize(cross(sun, new double[] {0, 1, 0}));
		sunV = cross(sunU, sun);
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
		final double s0 = sMin;
		final double t1 = tMax;
		// tools/render-gpu: the GPU traces the sub-samples when asked to; the CPU is the fallback (and the reference in --gpu-compare)
		Gpu.Mode gm = Gpu.mode;
		GpuTrace.Result gres = null;
		BufferedImage gpuImg = null;
		double gpuMs = 0;
		if (gm != Gpu.Mode.OFF) {
			long g0 = System.nanoTime();
			gres = GpuTrace.trace(this, W, H, px, s0, t1, lk.bloom, ss, scale, gm == Gpu.Mode.COMPARE);
			if (gres != null) {
				gpuImg = gres.rgb != null ? imageOf(gres.rgb, w, h) : resolve(gres.buf, gres.glow, w, h, ss, scale);
			}
			gpuMs = (System.nanoTime() - g0) / 1e6;
			if (gpuImg != null && gm == Gpu.Mode.AUTO) {
				System.out.printf(java.util.Locale.ROOT, "gpu: %dx%d (%dx%d sub-samples) in %.2f s (%s)%n", w, h, W, H, gpuMs / 1000, gres.info);
				return gpuImg;
			}
		}
		long c0 = System.nanoTime();
		int[] buf = new int[W * H];
		byte[] glow = lk.bloom ? new byte[W * H] : null;
		IntStream.range(0, H).parallel().forEach(py -> {
			double v = (py + 0.5) / H;
			for (int x = 0; x < W; x++) {
				double s = s0 + (x + 0.5) / px;
				double t = t1 - (py + 0.5) / px;
				buf[py * W + x] = trace(s, t, v, py * W + x, glow);
			}
		});
		BufferedImage cpuImg = resolve(buf, glow, w, h, ss, scale);
		if (gpuImg != null) {
			Gpu.compare(cpuImg, gpuImg, buf, gres.buf != null ? gres.buf : new int[0], (System.nanoTime() - c0) / 1e6, gpuMs, gres.info);
			return gpuImg;
		}
		return cpuImg;
	}

	// ------------------------------------------------------------------ reconstruction

	/** Filter weight at distance {@code x} output pixels. */
	private double filterWeight(double x) {
		x = Math.abs(x);
		switch (lk.filter == null ? "box" : lk.filter) {
			case "tent" -> {
				return Math.max(0, 1 - x);
			}
			case "mitchell" -> {
				// Mitchell–Netravali, B = C = 1/3, support 2 mapped onto FILTER_RADIUS px
				double t = x * 2 / FILTER_RADIUS;
				double b = 1 / 3.0, c = 1 / 3.0;
				if (t >= 2) {
					return 0;
				}
				if (t >= 1) {
					return ((-b - 6 * c) * t * t * t + (6 * b + 30 * c) * t * t + (-12 * b - 48 * c) * t + (8 * b + 24 * c)) / 6;
				}
				return ((12 - 9 * b - 6 * c) * t * t * t + (-18 + 12 * b + 6 * c) * t * t + (6 - 2 * b)) / 6;
			}
			default -> {
				return x < 0.5 ? 1 : 0;
			}
		}
	}

	/** The reconstruction filter's taps over the sub-sample offsets {@code kMin..kMax} (the same for every pixel); the GPU resolve uses them too. */
	record Taps(int kMin, int kMax, double[] wk) {
	}

	Taps taps(int ss) {
		boolean box = ss == 1 || lk.filter == null || lk.filter.equals("box");
		int kMin = box ? 0 : -2 * ss;
		int kMax = box ? ss - 1 : 3 * ss - 1;
		double[] wk = new double[kMax - kMin + 1];
		for (int k = kMin; k <= kMax; k++) {
			wk[k - kMin] = box ? 1 : filterWeight((k + 0.5) / ss - 0.5);
		}
		return new Taps(kMin, kMax, wk);
	}

	/** Packed 0xRRGGBB pixels (from the GPU resolve) as an image. */
	private static BufferedImage imageOf(int[] rgb, int w, int h) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, w, h, rgb, 0, w);
		return img;
	}

	private BufferedImage resolve(int[] buf, byte[] glow, int w, int h, int ss, double scale) {
		int W = w * ss;
		int H = h * ss;
		// kernel over sub-sample offsets k (sample x*ss + k), the same for every pixel since ss is an integer
		boolean box = ss == 1 || lk.filter == null || lk.filter.equals("box");
		int kMin = box ? 0 : -2 * ss;
		int kMax = box ? ss - 1 : 3 * ss - 1;
		double[] wk = new double[kMax - kMin + 1];
		for (int k = kMin; k <= kMax; k++) {
			wk[k - kMin] = box ? 1 : filterWeight((k + 0.5) / ss - 0.5);
		}
		float[] lin = new float[w * h * 3];
		IntStream.range(0, h).parallel().forEach(y -> {
			double[] acc = new double[w * 3];
			double[] wsum = new double[w];
			for (int ky = kMin; ky <= kMax; ky++) {
				int j = y * ss + ky;
				if (j < 0 || j >= H) {
					continue;
				}
				double wy = wk[ky - kMin];
				if (wy == 0) {
					continue;
				}
				int row = j * W;
				for (int x = 0; x < w; x++) {
					double r = 0, g = 0, b = 0, ws = 0;
					for (int kx = kMin; kx <= kMax; kx++) {
						int i = x * ss + kx;
						if (i < 0 || i >= W) {
							continue;
						}
						double wx = wk[kx - kMin];
						int c = buf[row + i];
						r += wx * DEC[(c >>> 20) & 1023];
						g += wx * DEC[(c >>> 10) & 1023];
						b += wx * DEC[c & 1023];
						ws += wx;
					}
					acc[x * 3] += wy * r;
					acc[x * 3 + 1] += wy * g;
					acc[x * 3 + 2] += wy * b;
					wsum[x] += wy * ws;
				}
			}
			for (int x = 0; x < w; x++) {
				double ws = wsum[x] == 0 ? 1 : wsum[x];
				for (int c = 0; c < 3; c++) {
					lin[(y * w + x) * 3 + c] = (float) Math.max(0, acc[x * 3 + c] / ws);
				}
			}
		});
		if (glow != null) {
			bloom(lin, buf, glow, w, h, ss, scale);
		}
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		int[] line = new int[w];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int i = (y * w + x) * 3;
				line[x] = (srgb(lin[i]) << 16) | (srgb(lin[i + 1]) << 8) | srgb(lin[i + 2]);
			}
			img.setRGB(0, y, w, 1, line, 0, w);
		}
		return img;
	}

	private static int srgb(double v) {
		return SRGB[(int) Math.round(Math.max(0, Math.min(1, v)) * 4096)] & 255;
	}

	/** Adds a faint halo around emissive pixels: their colour, box-averaged per pixel, gaussian-blurred, added back. */
	private static void bloom(float[] lin, int[] buf, byte[] glow, int w, int h, int ss, double scale) {
		float[] e = new float[w * h * 3];
		boolean any = false;
		int W = w * ss;
		double n = ss * ss * 255.0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				double r = 0, g = 0, b = 0;
				for (int j = 0; j < ss; j++) {
					for (int i = 0; i < ss; i++) {
						int k = (y * ss + j) * W + x * ss + i;
						int gl = glow[k] & 255;
						if (gl == 0) {
							continue;
						}
						int c = buf[k];
						r += gl * DEC[(c >>> 20) & 1023];
						g += gl * DEC[(c >>> 10) & 1023];
						b += gl * DEC[c & 1023];
					}
				}
				if (r + g + b > 0) {
					any = true;
					int o = (y * w + x) * 3;
					e[o] = (float) (r / n);
					e[o + 1] = (float) (g / n);
					e[o + 2] = (float) (b / n);
				}
			}
		}
		if (!any) {
			return;
		}
		double sigma = Math.max(1.5, Math.min(14, 0.55 * scale));
		int rad = (int) Math.ceil(sigma * 3);
		double[] k = new double[2 * rad + 1];
		double sum = 0;
		for (int i = -rad; i <= rad; i++) {
			k[i + rad] = Math.exp(-i * i / (2 * sigma * sigma));
			sum += k[i + rad];
		}
		for (int i = 0; i < k.length; i++) {
			k[i] /= sum;
		}
		float[] tmp = new float[e.length];
		IntStream.range(0, h).parallel().forEach(y -> {
			for (int x = 0; x < w; x++) {
				double r = 0, g = 0, b = 0;
				for (int i = -rad; i <= rad; i++) {
					int xx = Math.max(0, Math.min(w - 1, x + i));
					int o = (y * w + xx) * 3;
					r += k[i + rad] * e[o];
					g += k[i + rad] * e[o + 1];
					b += k[i + rad] * e[o + 2];
				}
				int o = (y * w + x) * 3;
				tmp[o] = (float) r;
				tmp[o + 1] = (float) g;
				tmp[o + 2] = (float) b;
			}
		});
		final double strength = 0.15;
		IntStream.range(0, h).parallel().forEach(y -> {
			for (int x = 0; x < w; x++) {
				double r = 0, g = 0, b = 0;
				for (int i = -rad; i <= rad; i++) {
					int yy = Math.max(0, Math.min(h - 1, y + i));
					int o = (yy * w + x) * 3;
					r += k[i + rad] * tmp[o];
					g += k[i + rad] * tmp[o + 1];
					b += k[i + rad] * tmp[o + 2];
				}
				int o = (y * w + x) * 3;
				// screen blend: never pushes past white, so it reads as a glow rather than a blown-out patch
				lin[o] = (float) (1 - (1 - lin[o]) * (1 - Math.min(1, strength * r)));
				lin[o + 1] = (float) (1 - (1 - lin[o + 1]) * (1 - Math.min(1, strength * g)));
				lin[o + 2] = (float) (1 - (1 - lin[o + 2]) * (1 - Math.min(1, strength * b)));
			}
		});
	}

	// ------------------------------------------------------------------ tracing

	private record Hit(double t, BlockModels.Quad q, int argb, double[] p) {
	}

	/**
	 * One sub-sample: linear display colour packed as three 10-bit square-root-encoded channels.
	 *
	 * @param v      vertical position in the image, 0 top .. 1 bottom (background gradient)
	 * @param sample index of the sub-sample (seeds the shadow jitter)
	 */
	private int trace(double s, double t, double v, int sample, byte[] glow) {
		double[] o = {s * right[0] + t * up[0] - d[0] * 2000, s * right[1] + t * up[1] - d[1] * 2000, s * right[2] + t * up[2] - d[2] * 2000};
		double[] range = slab(o, d);
		double[] acc = new double[5]; // r, g, b, alpha, emissive coverage
		if (range != null) {
			List<Hit> pending = new ArrayList<>(4);
			Walker wk = new Walker(o, d, range[0], range[1]);
			outer:
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
					blend(acc, h, alpha, sample);
					if (acc[3] > 0.985) {
						break outer;
					}
				}
			}
			if (acc[3] <= 0.985) {
				for (Hit h : pending) { // overhanging quads past the last cell
					blend(acc, h, ((h.argb >>> 24) & 255) / 255.0, sample);
				}
			}
		}
		double a = Math.min(1, acc[3]);
		double rest = 1 - a;
		double r, g, b;
		if (lk.sky) {
			int bg = lerpRgb(SKY_TOP, SKY_BOTTOM, Math.max(0, Math.min(1, v)));
			r = tone(acc[0]) + rest * LIN[(bg >> 16) & 255];
			g = tone(acc[1]) + rest * LIN[(bg >> 8) & 255];
			b = tone(acc[2]) + rest * LIN[bg & 255];
		} else {
			// flat look: blended in sRGB like the old renderer, then taken to linear for the filter
			r = LIN[clamp(acc[0] + rest * ((BACKGROUND >> 16) & 255))];
			g = LIN[clamp(acc[1] + rest * ((BACKGROUND >> 8) & 255))];
			b = LIN[clamp(acc[2] + rest * (BACKGROUND & 255))];
		}
		if (glow != null && acc[4] > 0) {
			glow[sample] = (byte) Math.round(Math.min(1, acc[4]) * 255);
		}
		return (enc(r) << 20) | (enc(g) << 10) | enc(b);
	}

	private static int enc(double v) {
		return (int) Math.round(Math.sqrt(Math.max(0, Math.min(1, v))) * 1023);
	}

	private static int lerpRgb(int c0, int c1, double f) {
		int r = (int) Math.round(((c0 >> 16) & 255) * (1 - f) + ((c1 >> 16) & 255) * f);
		int g = (int) Math.round(((c0 >> 8) & 255) * (1 - f) + ((c1 >> 8) & 255) * f);
		int b = (int) Math.round((c0 & 255) * (1 - f) + (c1 & 255) * f);
		return (r << 16) | (g << 8) | b;
	}

	/** Soft shoulder: linear up to 0.6, then eases toward 1 (no toe, so shadows stay readable). */
	private static double tone(double x) {
		return x <= 0.6 ? x : 0.6 + 0.4 * (1 - Math.exp(-(x - 0.6) / 0.4));
	}

	/** Front-to-back compositing of one hit into {@code acc} (linear radiance in sky mode, sRGB otherwise). */
	private void blend(double[] acc, Hit h, double alpha, int sample) {
		double k = (1 - acc[3]) * alpha;
		int c = h.argb;
		if (lk.sky) {
			double[] e = irradiance(h, sample);
			acc[0] += k * LIN[(c >> 16) & 255] * e[0];
			acc[1] += k * LIN[(c >> 8) & 255] * e[1];
			acc[2] += k * LIN[c & 255] * e[2];
		} else {
			double light = shadeFlat(h, sample);
			acc[0] += k * ((c >> 16) & 255) * light;
			acc[1] += k * ((c >> 8) & 255) * light;
			acc[2] += k * (c & 255) * light;
		}
		if (h.q.emissive) {
			acc[4] += k;
		}
		acc[3] += k;
	}

	private static int clamp(double v) {
		return (int) Math.max(0, Math.min(255, Math.round(v)));
	}

	/** The old look: Minecraft-like directional face factors plus a sun term (times AO when on). */
	private double shadeFlat(Hit h, int sample) {
		if (h.q.emissive) {
			return 1.08;
		}
		double[] n = h.q.n;
		double face = Math.abs(n[1]) > 0.5 ? (n[1] > 0 ? 1.0 : 0.55) : Math.abs(n[2]) > Math.abs(n[0]) ? 0.82 : 0.68;
		double lambert = Math.max(0, BlockModels.dot(n, sun));
		double direct = 0.35 + 0.65 * lambert;
		if (lambert > 0) {
			direct = 0.35 + (direct - 0.35) * sunVisibility(h.p, n, sample);
		}
		double ao = lk.ao ? ao(h.p, n) : 1;
		return face * (0.45 + 0.6 * direct) * ao;
	}

	/** Sky look: linear irradiance (RGB) reaching the hit: sun × shadow + sky/ground hemisphere × AO. */
	private double[] irradiance(Hit h, int sample) {
		if (h.q.emissive) {
			return EMIT_C;
		}
		double[] n = h.q.n;
		boolean shade = h.q.shade;
		double ndl = shade ? BlockModels.dot(n, sun) : 0.62;
		double ny = shade ? n[1] : 0.55;
		double ao = lk.ao ? ao(h.p, n) : 1;
		double vis = ndl > 0 ? sunVisibility(h.p, shade ? n : sun, sample) : 0;
		double direct = Math.max(0, ndl) * vis * (0.6 + 0.4 * ao);
		double skyW = 0.5 + 0.5 * ny;
		double[] e = new double[3];
		for (int i = 0; i < 3; i++) {
			e[i] = SUN_C[i] * direct + (SKY_C[i] * skyW + GROUND_C[i] * (1 - skyW)) * ao;
		}
		return e;
	}

	// ------------------------------------------------------------------ ambient occlusion

	/** Opaque half-block sub-cell at half-grid coordinates (0 or 1; outside the clip is empty). */
	private int half(int gx, int gy, int gz) {
		int bx = Math.floorDiv(gx, 2), by = Math.floorDiv(gy, 2), bz = Math.floorDiv(gz, 2);
		if (!clip.contains(bx, by, bz)) {
			return 0;
		}
		int m = scene.occupancy(bx, by, bz);
		return m == 0 ? 0 : (m >> ((gx & 1) | ((gz & 1) << 1) | ((gy & 1) << 2))) & 1;
	}

	/** How full a block is with opaque elements, 0..1. */
	private double full(int x, int y, int z) {
		return clip.contains(x, y, z) ? Integer.bitCount(scene.occupancy(x, y, z)) / 8.0 : 0;
	}

	/**
	 * Smooth-lighting style AO at {@code p} on a face with normal {@code n}: for the layer of cells in
	 * front of the face, each corner of the (half-)cell holding p gets Minecraft's vertex rule (two sides
	 * and the diagonal), interpolated bilinearly; done at half-block resolution (tight contact shadows)
	 * and at block resolution (the broad smooth-lighting gradient), multiplied.
	 */
	private double ao(double[] p, double[] n) {
		int a = Math.abs(n[0]) >= Math.abs(n[1]) ? (Math.abs(n[0]) >= Math.abs(n[2]) ? 0 : 2) : (Math.abs(n[1]) >= Math.abs(n[2]) ? 1 : 2);
		int u = (a + 1) % 3;
		int v = (a + 2) % 3;
		double sgn = n[a] >= 0 ? 1 : -1;
		int[] c = new int[3];
		// half-block resolution
		double fu = p[u] * 2, fv = p[v] * 2;
		int iu = (int) Math.floor(fu), iv = (int) Math.floor(fv);
		double tu = fu - iu, tv = fv - iv;
		c[a] = (int) Math.floor((p[a] + sgn * 0.25) * 2);
		double fine = 0;
		for (int cu = 0; cu < 2; cu++) {
			for (int cv = 0; cv < 2; cv++) {
				int du = cu == 0 ? -1 : 1, dv = cv == 0 ? -1 : 1;
				c[u] = iu + du;
				c[v] = iv;
				double s1 = half(c[0], c[1], c[2]);
				c[u] = iu;
				c[v] = iv + dv;
				double s2 = half(c[0], c[1], c[2]);
				c[u] = iu + du;
				c[v] = iv + dv;
				double dg = half(c[0], c[1], c[2]);
				double occ = s1 + s2 + Math.max(dg, s1 * s2);
				fine += (cu == 0 ? 1 - tu : tu) * (cv == 0 ? 1 - tv : tv) * (1 - 0.14 * occ);
			}
		}
		// block resolution
		iu = (int) Math.floor(p[u]);
		iv = (int) Math.floor(p[v]);
		tu = p[u] - iu;
		tv = p[v] - iv;
		c[a] = (int) Math.floor(p[a] + sgn * 0.5);
		double broad = 0;
		for (int cu = 0; cu < 2; cu++) {
			for (int cv = 0; cv < 2; cv++) {
				int du = cu == 0 ? -1 : 1, dv = cv == 0 ? -1 : 1;
				c[u] = iu + du;
				c[v] = iv;
				double s1 = full(c[0], c[1], c[2]);
				c[u] = iu;
				c[v] = iv + dv;
				double s2 = full(c[0], c[1], c[2]);
				c[u] = iu + du;
				c[v] = iv + dv;
				double dg = full(c[0], c[1], c[2]);
				double occ = s1 + s2 + Math.max(dg, s1 * s2);
				broad += (cu == 0 ? 1 - tu : tu) * (cv == 0 ? 1 - tv : tv) * (1 - 0.11 * occ);
			}
		}
		return fine * broad;
	}

	// ------------------------------------------------------------------ shadows

	/** Fraction of the sun that reaches p: soft (jittered rays over the sun disc) or one hard ray. */
	private double sunVisibility(double[] p, double[] n, int sample) {
		if (lk.shadows) {
			int rays = Math.max(1, lk.shadowRays);
			double spread = Math.tan(Math.toRadians(lk.sunRadius));
			double lit = 0;
			for (int i = 0; i < rays; i++) {
				// stratified over the disc: ring position from the ray index, jitter from a hash of the sample
				double r1 = (i + hash01(sample, 2 * i)) / rays;
				double r2 = hash01(sample, 2 * i + 1);
				double rad = Math.sqrt(r2) * spread;
				double ang = 2 * Math.PI * r1;
				double ju = Math.cos(ang) * rad, jv = Math.sin(ang) * rad;
				double[] dir = BlockModels.normalize(new double[] {sun[0] + sunU[0] * ju + sunV[0] * jv, sun[1] + sunU[1] * ju + sunV[1] * jv,
						sun[2] + sunU[2] * ju + sunV[2] * jv});
				if (!occluded(p, n, dir)) {
					lit++;
				}
			}
			return lit / rays;
		}
		if (lk.hardShadows) {
			return occluded(p, n, sun) ? 0 : 1;
		}
		return 1;
	}

	private static double hash01(int a, int b) {
		long z = (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z ^= z >>> 31;
		return (z >>> 11) * 0x1.0p-53;
	}

	private boolean occluded(double[] p, double[] n, double[] dir) {
		double[] o = {p[0] + n[0] * 0.003 + dir[0] * 0.003, p[1] + n[1] * 0.003 + dir[1] * 0.003, p[2] + n[2] * 0.003 + dir[2] * 0.003};
		double[] range = slab(o, dir);
		if (range == null) {
			return false;
		}
		Walker wk = new Walker(o, dir, Math.max(0, range[0]), Math.min(range[1], 64));
		while (wk.next()) {
			if (!clip.contains(wk.x, wk.y, wk.z)) {
				continue;
			}
			for (BlockModels.Quad q : scene.at(wk.x, wk.y, wk.z)) {
				if (q.emissive && q.tex.w == 1) {
					continue;
				}
				Hit h = hit(q, wk.x, wk.y, wk.z, o, dir, false);
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
