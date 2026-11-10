package dev.t3craft.tools.icons;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.batik.transcoder.image.PNGTranscoder;

/**
 * Icon pipeline (issue #24, docs/tools/icons.md): rasterises every SVG of {@code tools/art/icons/lucide}
 * into a white-on-transparent PNG sprite, which the mod's GUI atlas picks up as {@code t3craft:icons/<name>}
 * and the code tints at draw time ({@code T3Icon}). Run by the Gradle task {@code generateIcons} before
 * {@code processResources}; its own source set ({@code icons}), so neither this class nor Apache Batik
 * ends up in the mod jar.
 *
 * <p>Usage: {@code IconGen <svgDir> <spriteOutDir> <sizePx>}. It fails (exit 1) when there is no SVG, when
 * one cannot be rasterised or when a result is not a {@code sizePx} square with something drawn in it, so
 * the build never ships a game without its icons. The output folder is emptied of {@code .png} first, so
 * a deleted SVG does not leave its sprite behind.
 */
public final class IconGen {
	/** Lucide draws with {@code currentColor}; the sprite is white and the game tints it. */
	private static final String WHITE = "#ffffff";
	private static final Pattern NAME = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

	private IconGen() {
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 3) {
			System.err.println("usage: IconGen <svgDir> <spriteOutDir> <sizePx>");
			System.exit(2);
		}
		Path svgDir = Path.of(args[0]);
		Path outDir = Path.of(args[1]);
		int size = Integer.parseInt(args[2]);
		System.setProperty("java.awt.headless", "true");

		List<Path> svgs;
		try (Stream<Path> s = Files.isDirectory(svgDir) ? Files.list(svgDir) : Stream.<Path>empty()) {
			svgs = s.filter(p -> p.getFileName().toString().endsWith(".svg")).sorted().toList();
		}
		if (svgs.isEmpty()) {
			fail("no SVG found in " + svgDir);
		}

		Files.createDirectories(outDir);
		try (Stream<Path> old = Files.list(outDir)) {
			for (Path p : old.filter(p -> p.getFileName().toString().endsWith(".png")).toList()) {
				Files.delete(p);
			}
		}

		List<String> problems = new ArrayList<>();
		for (Path svg : svgs) {
			String file = svg.getFileName().toString();
			String name = file.substring(0, file.length() - ".svg".length());
			if (!NAME.matcher(name).matches()) {
				problems.add(file + ": the name must be lowercase kebab-case (a-z, 0-9, -)");
				continue;
			}
			try {
				byte[] png = rasterise(svg, size);
				check(png, size);
				Files.write(outDir.resolve(name + ".png"), png);
			} catch (Exception e) {
				problems.add(file + ": " + e);
			}
		}
		if (!problems.isEmpty()) {
			problems.forEach(p -> System.err.println("generateIcons: " + p));
			fail(problems.size() + " of " + svgs.size() + " icon(s) failed");
		}
		System.out.println("generateIcons: " + svgs.size() + " icon(s), " + size + " px, in " + outDir);
	}

	private static byte[] rasterise(Path svg, int size) throws Exception {
		String text = Files.readString(svg, StandardCharsets.UTF_8).replace("currentColor", WHITE);
		TranscoderInput in = new TranscoderInput(new StringReader(text));
		in.setURI(svg.toUri().toString());
		PNGTranscoder t = new PNGTranscoder();
		t.addTranscodingHint(ImageTranscoder.KEY_WIDTH, (float) size);
		t.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, (float) size);
		// pixels with no ink are transparent white, not transparent black: no dark fringe when the GPU filters
		t.addTranscodingHint(ImageTranscoder.KEY_FORCE_TRANSPARENT_WHITE, Boolean.TRUE);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		t.transcode(in, new TranscoderOutput(out));
		return out.toByteArray();
	}

	/** A {@code size} square with at least one visible pixel (a blank sprite would ship as an invisible icon). */
	private static void check(byte[] png, int size) throws IOException {
		BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
		if (img == null || img.getWidth() != size || img.getHeight() != size) {
			throw new IOException("not a " + size + "x" + size + " PNG");
		}
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				if ((img.getRGB(x, y) >>> 24) != 0) {
					return;
				}
			}
		}
		throw new IOException("rasterised to a fully transparent image");
	}

	private static void fail(String message) {
		System.err.println("generateIcons: " + message);
		System.exit(1);
	}
}
