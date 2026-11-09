package dev.t3craft.tools.hqrender;

import dev.t3craft.hq.HqInput;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Shared helpers of the hqrender views: the roster of N projects used by the layout galleries, and the PNG writer.
 * (The campus views that gave the class its name were removed with the campus layout.)
 */
final class CampusViews {
	private CampusViews() {
	}

	static final int[] THREADS = {9, 4, 13, 2, 6, 1, 7, 3, 26, 5, 2, 8, 1, 4, 11, 3, 6, 2, 5, 3};
	static final String[] ENVS = {"vps", "desktop", "laptop"};

	static HqInput roster(int n) {
		HqInput.Builder b = HqInput.builder();
		for (int p = 0; p < n; p++) {
			String env = ENVS[p % ENVS.length];
			String name = "proj" + (char) ('a' + p);
			String key = env + ":" + name;
			for (int t = 0; t < THREADS[p % THREADS.length]; t++) {
				b.thread(key, name, env, env.toUpperCase(Locale.ROOT), key + "-t" + t);
			}
			if (p % 3 == 0) {
				b.thread(key, name, env, env.toUpperCase(Locale.ROOT), key + "-s0", true);
			}
		}
		return b.build();
	}

	/** Writes a truecolour PNG. */
	static void save(BufferedImage img, Path f) throws Exception {
		ImageIO.write(img, "png", f.toFile());
	}
}
