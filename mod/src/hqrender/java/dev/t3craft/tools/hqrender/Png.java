package dev.t3craft.tools.hqrender;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** PNG writing with a size cap: over {@link #MAX_BYTES} the image is saved with 256 colours (a fixed 6×7×6 palette). */
final class Png {
	static final long MAX_BYTES = 480 * 1024;

	private Png() {
	}

	static void write(BufferedImage img, Path f) throws Exception {
		ImageIO.write(img, "png", f.toFile());
		if (Boolean.getBoolean("t3craft.render.palette") && Files.size(f) > MAX_BYTES) {
			ImageIO.write(quantize(img), "png", f.toFile());
		}
	}

	/** 252 colours: 6 levels of red, 7 of green, 6 of blue, nearest level per channel. */
	static BufferedImage quantize(BufferedImage img) {
		int w = img.getWidth();
		int h = img.getHeight();
		int n = 6 * 7 * 6;
		byte[] r = new byte[n];
		byte[] g = new byte[n];
		byte[] b = new byte[n];
		for (int i = 0; i < n; i++) {
			r[i] = (byte) (i / 42 * 255 / 5);
			g[i] = (byte) (i / 6 % 7 * 255 / 6);
			b[i] = (byte) (i % 6 * 255 / 5);
		}
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED, new IndexColorModel(8, n, r, g, b));
		byte[] data = ((java.awt.image.DataBufferByte) out.getRaster().getDataBuffer()).getData();
		int[] px = img.getRGB(0, 0, w, h, null, 0, w);
		for (int i = 0; i < px.length; i++) {
			int cr = (px[i] >> 16) & 255;
			int cg = (px[i] >> 8) & 255;
			int cb = px[i] & 255;
			data[i] = (byte) (Math.round(cr * 5 / 255f) * 42 + Math.round(cg * 6 / 255f) * 6 + Math.round(cb * 5 / 255f));
		}
		return out;
	}
}
