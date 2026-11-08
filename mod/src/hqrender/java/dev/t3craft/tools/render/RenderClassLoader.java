package dev.t3craft.tools.render;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.Predicate;

/**
 * The class loader of one render job: the worktree's compiled classes (and resources) win over the
 * daemon's own copy for every {@code dev.t3craft.*} class except the shared ones ({@link Pinned}:
 * the blocks module and the small HQ kernel it registered at boot, plus the daemon's own package).
 * Everything else (Minecraft, Gson, the JDK) comes from the parent, so the registries stay booted
 * once for the whole JVM. A new loader per job; dropping it unloads the job's classes.
 */
final class RenderClassLoader extends URLClassLoader {
	static {
		ClassLoader.registerAsParallelCapable();
	}

	private final Predicate<String> shared;

	RenderClassLoader(URL[] roots, ClassLoader parent, Predicate<String> shared) {
		super(roots, parent);
		this.shared = shared;
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			Class<?> c = findLoadedClass(name);
			if (c == null && name.startsWith("dev.t3craft.") && !shared.test(name)) {
				try {
					c = findClass(name);
				} catch (ClassNotFoundException e) {
					c = null; // not in the worktree's output: the daemon's copy
				}
			}
			if (c == null) {
				c = super.loadClass(name, false);
			}
			if (resolve) {
				resolveClass(c);
			}
			return c;
		}
	}

	/** The worktree's resources first (its assets/t3craft, lang, blockstates), then the parent's. */
	@Override
	public URL getResource(String name) {
		URL own = findResource(name);
		return own != null ? own : super.getResource(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		URL u = getResource(name);
		if (u == null) {
			return null;
		}
		try {
			return u.openStream();
		} catch (IOException e) {
			return null;
		}
	}
}
