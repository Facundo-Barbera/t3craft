package dev.t3craft.tools.hqrender;

import dev.t3craft.tools.hqrender.gpu.GpuSession;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Parent side of the GPU path (tools/render-gpu): flattens a {@link Scene} (grid of model ids, the quads of every model,
 * the textures) into a file, asks the child process to trace every sub-sample of the image on the GPU, and reads back the
 * same packed colours {@link Renderer#trace} would have produced. The matching kernel is {@code mod/src/hqrender/cuda/trace.cu};
 * keep the {@code Params} struct and the 32-word quad layout there in step with this class.
 */
final class GpuTrace {
	private GpuTrace() {
	}

	static final class Result {
		/** The sub-samples (null when the GPU resolve is on and they were not asked for). */
		int[] buf;
		byte[] glow;
		/** The finished pixels, 0xRRGGBB, when the GPU did the filter, bloom and sRGB too. */
		int[] rgb;
		String info;
	}

	/**
	 * Null when the GPU cannot take this image (not available, too big, child failed): the caller renders on the CPU.
	 *
	 * @param w       sub-sample columns ({@code pixels * ss})
	 * @param h       sub-sample rows
	 * @param wantSub bring the sub-samples back even if the GPU resolves (for {@code --gpu-compare})
	 */
	static Result trace(Renderer r, int w, int h, double pxScale, double s0, double t1, boolean glow, int ss, double scale, boolean wantSub) {
		GpuSession s = Gpu.session();
		if (s == null) {
			return null;
		}
		if ((long) w * h * 5 >= Integer.MAX_VALUE) {
			System.out.println("gpu: image of " + w + "x" + h + " sub-samples is too big for one buffer; using the CPU for it");
			return null;
		}
		Path dir = null;
		Path in = null;
		Path out = null;
		try {
			long t0 = System.nanoTime();
			dir = Files.createTempDirectory("t3craft-gpu-");
			in = dir.resolve("in.bin");
			out = dir.resolve("out.bin");
			boolean resolve = Gpu.resolve;
			int outputs = (resolve ? 2 : 0) | (!resolve || wantSub ? 1 : 0);
			writeInput(in, r, w, h, pxScale, s0, t1, glow, ss, scale, resolve, outputs);
			double exportMs = (System.nanoTime() - t0) / 1e6;
			StringBuilder log = new StringBuilder();
			String reply = s.request("render\t" + in + "\t" + out, 900, log);
			System.out.print(log);
			if (reply == null || !reply.startsWith("OK")) {
				System.out.println("gpu: render failed (" + reply + "); using the CPU for this image");
				return null;
			}
			long t2 = System.nanoTime();
			Result res = readOutput(out, w, h, glow, ss, outputs);
			res.info = String.format(java.util.Locale.ROOT, "export %.0f ms; %s; read %.0f ms", exportMs, reply.substring(3), (System.nanoTime() - t2) / 1e6);
			return res;
		} catch (IOException | RuntimeException e) {
			System.out.println("gpu: " + e + "; using the CPU for this image");
			return null;
		} finally {
			try {
				if (in != null) {
					Files.deleteIfExists(in);
				}
				if (out != null) {
					Files.deleteIfExists(out);
				}
				if (dir != null) {
					Files.deleteIfExists(dir);
				}
			} catch (IOException e) {
				// temp files; the OS cleans /tmp
			}
		}
	}

	private static ByteBuffer ints(int[] a) {
		ByteBuffer b = ByteBuffer.allocate(a.length * 4).order(ByteOrder.LITTLE_ENDIAN);
		b.asIntBuffer().put(a);
		return b;
	}

	private static void writeInput(Path file, Renderer r, int w, int h, double pxScale, double s0, double t1, boolean glow, int ss, double scale,
			boolean resolve, int outputs) throws IOException {
		Scene sc = r.scene;
		Renderer.Clip clip = r.clip;
		Renderer.Look lk = r.lk;
		List<List<BlockModels.Quad>> models = sc.modelsRaw();
		int m = models.size();

		// unique quads (a quad object is shared by every model that uses it) and unique textures
		IdentityHashMap<BlockModels.Quad, Integer> qid = new IdentityHashMap<>();
		List<BlockModels.Quad> qs = new ArrayList<>();
		IdentityHashMap<BlockModels.Texture, Integer> toff = new IdentityHashMap<>();
		List<BlockModels.Texture> ts = new ArrayList<>();
		int texWords = 0;
		int total = 0;
		for (List<BlockModels.Quad> l : models) {
			total += l.size();
		}
		int[] mStart = new int[m + 1];
		int[] mQuads = new int[total];
		int k = 0;
		for (int i = 0; i < m; i++) {
			mStart[i] = k;
			for (BlockModels.Quad q : models.get(i)) {
				Integer id = qid.get(q);
				if (id == null) {
					id = qs.size();
					qid.put(q, id);
					qs.add(q);
					if (!toff.containsKey(q.tex)) {
						toff.put(q.tex, texWords);
						ts.add(q.tex);
						texWords += q.tex.px.length;
					}
				}
				mQuads[k++] = id;
			}
		}
		mStart[m] = k;

		ByteBuffer qb = ByteBuffer.allocate(qs.size() * 128).order(ByteOrder.LITTLE_ENDIAN);
		for (BlockModels.Quad q : qs) {
			for (int i = 0; i < 3; i++) {
				qb.putFloat((float) q.p0[i]);
			}
			for (int i = 0; i < 3; i++) {
				qb.putFloat((float) q.e1[i]);
			}
			for (int i = 0; i < 3; i++) {
				qb.putFloat((float) q.e2[i]);
			}
			for (int i = 0; i < 3; i++) {
				qb.putFloat((float) q.n[i]);
			}
			qb.putFloat((float) q.e1len2).putFloat((float) q.e2len2);
			qb.putFloat((float) q.u0).putFloat((float) q.v0).putFloat((float) q.du).putFloat((float) q.dv);
			qb.putInt(q.uvRot).putInt(q.tint).putInt((q.emissive ? 1 : 0) | (q.shade ? 2 : 0) | (q.tex.w == 1 ? 4 : 0));
			qb.putInt(toff.get(q.tex)).putInt(q.tex.w).putInt(q.tex.h);
			qb.putFloat(q.e1len2 > 0 ? (float) (1 / Math.sqrt(q.e1len2)) : 0f);
			qb.putFloat(q.e2len2 > 0 ? (float) (1 / Math.sqrt(q.e2len2)) : 0f);
			for (int i = 0; i < 6; i++) {
				qb.putInt(0);
			}
		}
		qb.flip();

		int[] texData = new int[texWords];
		for (BlockModels.Texture t : ts) {
			System.arraycopy(t.px, 0, texData, toff.get(t), t.px.length);
		}
		int[] masks = Arrays.copyOf(sc.masksRaw(), m);
		ByteBuffer lin = ByteBuffer.allocate(256 * 4).order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < 256; i++) {
			lin.putFloat((float) Renderer.LIN[i]);
		}
		lin.flip();

		// Params, same order as the struct in trace.cu: 12 doubles, 16 ints, 13 floats
		ByteBuffer pb = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
		pb.putDouble(s0).putDouble(t1).putDouble(pxScale);
		for (int i = 0; i < 3; i++) {
			pb.putDouble(r.right[i]);
		}
		for (int i = 0; i < 3; i++) {
			pb.putDouble(r.up[i]);
		}
		for (int i = 0; i < 3; i++) {
			pb.putDouble(r.d[i]);
		}
		pb.putInt(w).putInt(h);
		pb.putInt(sc.minX).putInt(sc.minY).putInt(sc.minZ).putInt(sc.sx).putInt(sc.sy).putInt(sc.sz);
		pb.putInt(clip.minX()).putInt(clip.minY()).putInt(clip.minZ());
		pb.putInt(clip.maxX() - clip.minX() + 1).putInt(clip.maxY() - clip.minY() + 1).putInt(clip.maxZ() - clip.minZ() + 1);
		pb.putInt((lk.sky ? 1 : 0) | (lk.ao ? 2 : 0) | (lk.shadows ? 4 : 0) | (lk.hardShadows ? 8 : 0) | (glow ? 16 : 0));
		pb.putInt(lk.shadowRays);
		pb.putFloat((float) Math.tan(Math.toRadians(lk.sunRadius)));
		for (int i = 0; i < 3; i++) {
			pb.putFloat((float) r.sun[i]);
		}
		for (int i = 0; i < 3; i++) {
			pb.putFloat((float) r.sunU[i]);
		}
		for (int i = 0; i < 3; i++) {
			pb.putFloat((float) r.sunV[i]);
		}
		for (int i = 0; i < 3; i++) {
			pb.putFloat((float) r.d[i]);
		}
		pb.clear();

		// the filter taps and, with the bloom, the gaussian kernel (what Renderer.resolve and Renderer.bloom compute)
		Renderer.Taps taps = r.taps(ss);
		int rad = 0;
		double[] gk = new double[0];
		if (glow) {
			double sigma = Math.max(1.5, Math.min(14, 0.55 * scale));
			rad = (int) Math.ceil(sigma * 3);
			gk = new double[2 * rad + 1];
			double sum = 0;
			for (int i = -rad; i <= rad; i++) {
				gk[i + rad] = Math.exp(-i * i / (2 * sigma * sigma));
				sum += gk[i + rad];
			}
			for (int i = 0; i < gk.length; i++) {
				gk[i] /= sum;
			}
		}
		ByteBuffer rs = ByteBuffer.allocate(4 * (taps.wk().length + gk.length)).order(ByteOrder.LITTLE_ENDIAN);
		for (double v : taps.wk()) {
			rs.putFloat((float) v);
		}
		for (double v : gk) {
			rs.putFloat((float) v);
		}
		rs.flip();

		ByteBuffer[] sections = {pb, ints(sc.cellsRaw()), ints(mStart), ints(mQuads), ints(masks), qb, ints(texData), lin, rs};
		// header: [0] magic, [1] W, [2] H (sub-samples), [3] glow, [4] GPU resolve, [5] ss, [6] kMin, [7] kMax, [8 + i] byte length of section i,
		// [24] bloom radius, [25] outputs (1 sub-samples, 2 finished pixels)
		ByteBuffer head = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);
		head.putInt(0x47505531).putInt(w).putInt(h).putInt(glow ? 1 : 0).putInt(resolve ? 1 : 0).putInt(ss).putInt(taps.kMin()).putInt(taps.kMax());
		for (ByteBuffer s : sections) {
			head.putInt(s.remaining());
		}
		head.putInt(24 * 4, rad);
		head.putInt(25 * 4, outputs);
		head.clear();
		try (FileChannel ch = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
			writeFully(ch, head);
			for (ByteBuffer s : sections) {
				int len = s.remaining();
				writeFully(ch, s);
				int pad = (16 - (len & 15)) & 15;
				if (pad > 0) {
					writeFully(ch, ByteBuffer.allocate(pad));
				}
			}
		}
	}

	private static void writeFully(FileChannel ch, ByteBuffer b) throws IOException {
		while (b.hasRemaining()) {
			ch.write(b);
		}
	}

	/** Output file: [sub-samples (4 bytes each) and the glow bytes] when bit 1 of {@code outputs}, then [finished pixels, 4 bytes each] when bit 2. */
	private static Result readOutput(Path file, int w, int h, boolean glow, int ss, int outputs) throws IOException {
		boolean sub = (outputs & 1) != 0;
		boolean rgb = (outputs & 2) != 0;
		long bufBytes = sub ? 4L * w * h : 0;
		long glowBytes = sub && glow ? (long) w * h : 0;
		long rgbBytes = rgb ? 4L * (w / ss) * (h / ss) : 0;
		long total = bufBytes + glowBytes + rgbBytes;
		try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
			if (ch.size() < total) {
				throw new IOException("the GPU output file is short: " + ch.size() + " bytes, expected " + total);
			}
			MappedByteBuffer mb = ch.map(FileChannel.MapMode.READ_ONLY, 0, total);
			mb.order(ByteOrder.LITTLE_ENDIAN);
			Result res = new Result();
			if (sub) {
				res.buf = new int[w * h];
				mb.limit((int) bufBytes);
				mb.asIntBuffer().get(res.buf);
				if (glow) {
					res.glow = new byte[w * h];
					mb.limit((int) (bufBytes + glowBytes));
					mb.position((int) bufBytes);
					mb.get(res.glow);
				}
			}
			if (rgb) {
				res.rgb = new int[(w / ss) * (h / ss)];
				mb.limit((int) total);
				mb.position((int) (bufBytes + glowBytes));
				mb.asIntBuffer().get(res.rgb);
			}
			return res;
		}
	}
}
