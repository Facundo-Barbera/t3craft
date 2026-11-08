package dev.t3craft.tools.hqrender;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * {@code --compare <other_dir>}: for every PNG this run wrote, the image of the same name in the other
 * directory (before) next to the new one (after), as {@code compare_<name>.png} in the output directory,
 * and {@code compare.txt} saying how many pixels differ and whether the plan signatures of the two
 * {@code report.json} files are equal (the refactor check).
 */
public final class ImageCompare {
	private ImageCompare() {
	}

	private static final Pattern SIGNATURE = Pattern.compile("\"cells\"\\s*:\\s*\"([0-9a-f]{64})\"");

	/**
	 * @param before the directory with the earlier renders
	 * @param after  the directory with the new ones (the output directory)
	 * @param since  only PNGs of {@code after} modified at or after this time (ms) are compared; 0 = all
	 * @return the files written
	 */
	public static List<Path> run(Path before, Path after, long since) throws IOException {
		List<Path> written = new ArrayList<>();
		StringBuilder txt = new StringBuilder("COMPARE\n  before: " + before + "\n  after:  " + after + "\n");
		List<Path> pngs = new ArrayList<>();
		try (Stream<Path> s = Files.list(after)) {
			s.filter(p -> p.getFileName().toString().endsWith(".png") && !p.getFileName().toString().startsWith("compare_")).sorted().forEach(pngs::add);
		}
		int pairs = 0;
		for (Path now : pngs) {
			if (since > 0 && Files.getLastModifiedTime(now).toMillis() < since) {
				continue;
			}
			Path old = before.resolve(now.getFileName().toString());
			if (!Files.exists(old)) {
				txt.append("  ").append(now.getFileName()).append(": no image of that name in ").append(before).append('\n');
				continue;
			}
			BufferedImage a = ImageIO.read(old.toFile());
			BufferedImage b = ImageIO.read(now.toFile());
			if (a == null || b == null) {
				continue;
			}
			Path f = after.resolve("compare_" + now.getFileName());
			ImageIO.write(sideBySide(a, b), "png", f.toFile());
			written.add(f);
			pairs++;
			txt.append(String.format(Locale.ROOT, "  %s: %s%n", now.getFileName(), differ(a, b)));
		}
		txt.append("  ").append(pairs).append(" images compared\n");
		String sa = signature(before.resolve("report.json"));
		String sb = signature(after.resolve("report.json"));
		if (sa != null && sb != null) {
			txt.append("  plan signature before ").append(sa, 0, 16).append("… after ").append(sb, 0, 16).append("…: ")
					.append(sa.equals(sb) ? "SAME (the plan is block-for-block identical)" : "DIFFERENT (the plan changed)").append('\n');
		} else {
			txt.append("  plan signature: report.json missing in ").append(sa == null ? before : after).append('\n');
		}
		Path t = after.resolve("compare.txt");
		Files.writeString(t, txt.toString(), StandardCharsets.UTF_8);
		written.add(t);
		System.out.print(txt);
		return written;
	}

	private static String signature(Path reportJson) {
		try {
			if (!Files.exists(reportJson)) {
				return null;
			}
			Matcher m = SIGNATURE.matcher(Files.readString(reportJson, StandardCharsets.UTF_8));
			return m.find() ? m.group(1) : null;
		} catch (IOException e) {
			return null;
		}
	}

	private static String differ(BufferedImage a, BufferedImage b) {
		if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
			return "different size (" + a.getWidth() + "x" + a.getHeight() + " vs " + b.getWidth() + "x" + b.getHeight() + ")";
		}
		long diff = 0;
		for (int y = 0; y < a.getHeight(); y++) {
			for (int x = 0; x < a.getWidth(); x++) {
				if ((a.getRGB(x, y) & 0xFFFFFF) != (b.getRGB(x, y) & 0xFFFFFF)) {
					diff++;
				}
			}
		}
		long total = (long) a.getWidth() * a.getHeight();
		return diff == 0 ? "identical" : String.format(Locale.ROOT, "%d of %d pixels differ (%.2f%%)", diff, total, diff * 100.0 / total);
	}

	/** Before on the left, after on the right, each under a label. */
	public static BufferedImage sideBySide(BufferedImage before, BufferedImage after) {
		int gap = 6;
		int head = 24;
		int w = before.getWidth() + gap + after.getWidth();
		int h = Math.max(before.getHeight(), after.getHeight()) + head;
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.setColor(new Color(0xEE, 0xF1, 0xF4));
		g.fillRect(0, 0, w, h);
		g.drawImage(before, 0, head, null);
		g.drawImage(after, before.getWidth() + gap, head, null);
		g.setColor(new Color(0x9A, 0xA3, 0xAE));
		g.fillRect(before.getWidth(), head, gap, h - head);
		try {
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
			g.setColor(new Color(0x33, 0x3A, 0x44));
			g.drawString("BEFORE", 8, 17);
			g.drawString("AFTER", before.getWidth() + gap + 8, 17);
		} catch (RuntimeException | Error e) {
			// no fonts on this machine: no labels
		}
		g.dispose();
		return out;
	}
}
