package dev.t3craft.tools.hqrender;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** PNG writing: truecolour, no size cap and no palette. */
final class Png {
	private Png() {
	}

	static void write(BufferedImage img, Path f) throws Exception {
		ImageIO.write(img, "png", f.toFile());
	}
}
