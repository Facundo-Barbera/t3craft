package dev.t3craft.tools.uirender;

import com.mojang.blaze3d.platform.Window;
import dev.t3craft.client.ModConfig;
import dev.t3craft.client.operate.OperateClient;
import dev.t3craft.t3.AuditLog;
import dev.t3craft.t3.OperateCredentials;
import dev.t3craft.t3.OperateGate;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * The minimum of a running client the T3 screens need, without starting one. {@code Minecraft} is
 * allocated without running its constructor ({@code Unsafe.allocateInstance}: no window, no GL, no
 * resource manager) and given the real {@link net.minecraft.client.gui.Font} metrics, a window object
 * that reports the GUI size, and an input type; the language gets the mod's keys; the operate layer
 * and the mod's data dir point at a scratch folder per render. Everything is reachable only through
 * the classes the screens already call ({@code Minecraft.getInstance()}, {@code OperateClient.get()},
 * {@code ModConfig.dataDir()}), so no production code changed shape for this.
 *
 * <p>Whatever a screen calls on {@code Minecraft} beyond {@code font}, {@code getWindow()} and the
 * input type (opening other screens, the player, the clipboard, narration) is null here and fails with
 * a NullPointerException: that is a click path, not a render path, and it surfaces in the report.
 */
final class Headless {
	private static boolean installed;
	private static Object unsafe;
	private static Minecraft mc;
	private static Window window;
	private static Path scratch;

	private Headless() {
	}

	// ---- reflection helpers ----------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	static <T> T allocate(Class<T> c) {
		try {
			if (unsafe == null) {
				Class<?> u = Class.forName("sun.misc.Unsafe");
				Field f = u.getDeclaredField("theUnsafe");
				f.setAccessible(true);
				unsafe = f.get(null);
			}
			Method m = unsafe.getClass().getMethod("allocateInstance", Class.class);
			return (T) m.invoke(unsafe, c);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("cannot allocate " + c.getName() + " without its constructor", e);
		}
	}

	/** Sets {@code name} declared in {@code declaring} on {@code target} (null target = static field). */
	static void set(Object target, Class<?> declaring, String name, Object value) {
		try {
			Field f = declaring.getDeclaredField(name);
			f.setAccessible(true);
			f.set(target, value);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("cannot set " + declaring.getSimpleName() + "." + name, e);
		}
	}

	static Object get(Object target, Class<?> declaring, String name) {
		try {
			Field f = declaring.getDeclaredField(name);
			f.setAccessible(true);
			return f.get(target);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("cannot read " + declaring.getSimpleName() + "." + name, e);
		}
	}

	// ---- install ---------------------------------------------------------------------------------

	/** Idempotent. Call before building any screen. */
	static synchronized void install() throws IOException {
		if (installed) {
			return;
		}
		System.setProperty("java.awt.headless", "true");
		try {
			SharedConstants.tryDetectVersion();
		} catch (RuntimeException | LinkageError ignored) {
			// only needed for version strings
		}
		installLanguage();
		mc = allocate(Minecraft.class);
		window = allocate(Window.class);
		set(mc, Minecraft.class, "font", JarFont.get().font());
		set(mc, Minecraft.class, "window", window);
		set(mc, Minecraft.class, "lastInputType", InputType.MOUSE);
		set(null, Minecraft.class, "instance", mc);
		scratch = Files.createTempDirectory("t3craft-uirender");
		Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteTree(scratch)));
		ModConfig.useDataDir(scratch.resolve("t3craft"));
		installed = true;
	}

	/** GUI size reported by {@code Minecraft.getInstance().getWindow()}. */
	static void gui(int guiW, int guiH) {
		set(window, Window.class, "guiScaledWidth", guiW);
		set(window, Window.class, "guiScaledHeight", guiH);
		set(window, Window.class, "framebufferWidth", guiW);
		set(window, Window.class, "framebufferHeight", guiH);
	}

	/** An empty data dir ({@code config/t3craft/}) for the next scenario. */
	static Path freshDataDir() throws IOException {
		Path dir = scratch.resolve("t3craft");
		deleteTree(dir);
		Files.createDirectories(dir);
		return dir;
	}

	/** Points the operate layer at {@code dataDir} (the gate reads {@code credentials/operate/<env>.json}). */
	static void operateAt(Path dataDir) {
		OperateCredentials creds = new OperateCredentials(dataDir);
		AuditLog audit = AuditLog.in(dataDir);
		OperateGate gate = new OperateGate(creds, env -> null, audit);
		OperateClient c = allocate(OperateClient.class);
		set(c, OperateClient.class, "credentials", creds);
		set(c, OperateClient.class, "audit", audit);
		set(c, OperateClient.class, "gate", gate);
		set(null, OperateClient.class, "instance", c);
	}

	private static void installLanguage() {
		Map<String, String> mod = new HashMap<>();
		try (InputStream in = Assets.open("assets/t3craft/lang/en_us.json")) {
			if (in != null) {
				Language.loadFromJson(in, mod::put);
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("[ui-render] could not read the mod's en_us.json: " + e);
		}
		Language base = Language.getInstance();
		Language.inject(new Language() {
			@Override
			public String getOrDefault(String key, String fallback) {
				String v = mod.get(key);
				return v != null ? v : base.getOrDefault(key, fallback);
			}

			@Override
			public boolean has(String key) {
				return mod.containsKey(key) || base.has(key);
			}

			@Override
			public boolean isDefaultRightToLeft() {
				return false;
			}

			@Override
			public FormattedCharSequence getVisualOrder(FormattedText text) {
				return base.getVisualOrder(text);
			}
		});
	}

	private static void deleteTree(Path dir) {
		if (dir == null || !Files.exists(dir)) {
			return;
		}
		try {
			Files.walkFileTree(dir, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
					Files.deleteIfExists(f);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
					Files.deleteIfExists(d);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException ignored) {
			// scratch folder in the temp dir
		}
	}
}
