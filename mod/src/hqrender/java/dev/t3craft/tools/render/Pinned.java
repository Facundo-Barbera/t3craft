package dev.t3craft.tools.render;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The classes a job must NOT reload. The daemon registers the mod's blocks into Minecraft's global
 * registries once, at boot; those {@code Block} instances belong to the daemon's copy of
 * {@code dev.t3craft.blocks.*} and of every class those blocks touch ({@code BuildPlan}, {@code Anchor},
 * {@code HqFixtures}...). A job that reloaded them would see a second, unregistered world, so they are
 * "pinned" to the parent loader.
 *
 * <p>The set is computed, not listed: every class under {@code dev/t3craft/blocks} is a seed, and the
 * closure follows the {@code dev/t3craft/...} names written in the constant pool of each class
 * (class refs, descriptors, signatures) while the visited class lives in {@code blocks} or {@code hq}.
 * Classes outside those packages that get reached ({@code T3Craft}, {@code T3CraftTab}) are pinned but
 * not followed. In practice it is {@code blocks.*} plus a dozen HQ data classes; the generators,
 * styles, geometries and packs (what agents edit) are not in it.
 */
final class Pinned {
	private static final Pattern NAME = Pattern.compile("dev/t3craft/[A-Za-z0-9_/$]+");
	private static final String BLOCKS = "dev.t3craft.blocks.";
	private static final String HQ = "dev.t3craft.hq.";

	/** Binary class names (dots) pinned to the parent, nested classes included. */
	final Set<String> names;
	/** The pinned classes whose bytes must match in a worktree (everything except the two unfollowed leaves). */
	final Set<String> kernel;

	private Pinned(Set<String> names, Set<String> kernel) {
		this.names = names;
		this.kernel = kernel;
	}

	/** Whether the loader of a job must delegate {@code className} to its parent. */
	boolean isShared(String className) {
		return names.contains(className) || className.startsWith(BLOCKS) || className.startsWith("dev.t3craft.tools.render.");
	}

	/** Computes the set from the class directories of the daemon's own build. */
	static Pinned compute(List<Path> classDirs) throws IOException {
		Map<String, Path> files = new HashMap<>();
		for (Path dir : classDirs) {
			Path base = dir.resolve("dev/t3craft");
			if (!Files.isDirectory(base)) {
				continue;
			}
			try (Stream<Path> s = Files.walk(base)) {
				for (Path p : (Iterable<Path>) s::iterator) {
					String f = p.getFileName().toString();
					if (f.endsWith(".class")) {
						String rel = dir.relativize(p).toString().replace('\\', '/');
						files.putIfAbsent(rel.substring(0, rel.length() - 6).replace('/', '.'), p);
					}
				}
			}
		}
		Set<String> pinned = new TreeSet<>();
		Set<String> followed = new TreeSet<>();
		Deque<String> todo = new ArrayDeque<>();
		for (String n : files.keySet()) {
			if (n.startsWith(BLOCKS)) {
				todo.add(n);
			}
		}
		Set<String> seen = new HashSet<>();
		while (!todo.isEmpty()) {
			String n = todo.poll();
			if (!seen.add(n)) {
				continue;
			}
			Path file = files.get(n);
			if (file == null) {
				continue; // a package name, a string, a class not built here
			}
			pinned.add(n);
			if (!n.startsWith(BLOCKS) && !n.startsWith(HQ)) {
				continue;
			}
			followed.add(n);
			String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
			Matcher m = NAME.matcher(text);
			while (m.find()) {
				todo.add(m.group().replace('/', '.'));
			}
		}
		return new Pinned(Set.copyOf(pinned), Set.copyOf(followed));
	}

	/** The pinned names, sorted, for the log. */
	List<String> sorted() {
		List<String> l = new ArrayList<>(names);
		l.sort(null);
		return l;
	}

	/**
	 * The kernel classes whose compiled bytes differ between the daemon's build and a worktree's output
	 * (those cannot be hot-reloaded: the daemon would run its own copy). Empty when they match.
	 */
	List<String> changedIn(List<Path> baselineDirs, List<Path> worktreeDirs) throws IOException {
		List<String> changed = new ArrayList<>();
		for (String n : kernel) {
			String rel = n.replace('.', '/') + ".class";
			Path was = find(baselineDirs, rel);
			Path now = find(worktreeDirs, rel);
			if (now == null && was == null) {
				continue;
			}
			if (now == null || was == null || !Arrays.equals(Files.readAllBytes(was), Files.readAllBytes(now))) {
				changed.add(n);
			}
		}
		changed.sort(null);
		return changed;
	}

	private static Path find(List<Path> dirs, String rel) {
		for (Path d : dirs) {
			Path p = d.resolve(rel);
			if (Files.isRegularFile(p)) {
				return p;
			}
		}
		return null;
	}
}
