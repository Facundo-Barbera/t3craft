package dev.t3craft.hq.pack;

import dev.t3craft.T3Craft;
import dev.t3craft.hq.layout.HqLayoutInfo;
import dev.t3craft.hq.layout.HqLayouts;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * feat/hq-layout-packs: the layout packs in {@code config/t3craft/hq-layouts/} and the ones plans are
 * generated with right now.
 *
 * <pre>
 * config/t3craft/hq-layouts/
 *   campus.json                    roles / palette / blocks / params / rooms (all optional)
 *   rooms/campus/hall_3.nbt        a structure-block room replacing module "hall_3"
 *   _defaults/campus.json          written by the export: every default, for reference
 * </pre>
 *
 * Nothing in the folder: every layout is exactly the code's. A file that fails to read keeps the
 * last good pack of that layout (and its rooms) and reports {@code file:line: what}. Reading is pure
 * file IO, so the client polls it ({@link #poll}) and hqRender loads it once ({@code --packs}).
 * Generators read the active packs through {@link #pack} while generating; the pack's hash goes into
 * the plan's fingerprint. Thread-safe: the active set is an immutable map swapped atomically.
 */
public final class HqLayoutPacks {
	/** Folder under the config dir. */
	public static final String FOLDER = "t3craft/hq-layouts";
	/** Reference copies written by the export (never read as packs). */
	public static final String DEFAULTS = "_defaults";

	private static volatile Map<String, LayoutPack> active = Map.of();
	private static final AtomicInteger GENERATION = new AtomicInteger();
	private static final ThreadLocal<@Nullable Map<String, LayoutPack>> OVERRIDE = new ThreadLocal<>();
	/** Packs seen this session by {@code layout@hash}, so a plan built with an older pack can be regenerated for a diff. */
	private static final Map<String, LayoutPack> HISTORY = Collections.synchronizedMap(new LinkedHashMap<String, LayoutPack>(32, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, LayoutPack> e) {
			return size() > 64;
		}
	});
	private static volatile @Nullable String stamp;
	private static volatile List<String> lastErrors = List.of();

	private HqLayoutPacks() {
	}

	/** Result of a (re)load: the problems ({@code file:line: what}) and whether any layout's pack changed. */
	public record Load(List<String> errors, boolean changed, List<String> changedLayouts) {
	}

	// ------------------------------------------------------------------ reading the active packs

	/** The pack plans of {@code layoutId} are generated with now, or null when it has none. */
	public static @Nullable LayoutPack pack(String layoutId) {
		Map<String, LayoutPack> o = OVERRIDE.get();
		LayoutPack p = (o != null ? o : active).get(layoutId);
		return p == null || p.isEmpty() ? null : p;
	}

	/** Hash of {@code layoutId}'s pack ("" when it has none): goes into plan fingerprints and demo markers. */
	public static String hash(String layoutId) {
		LayoutPack p = pack(layoutId);
		return p == null ? "" : p.hash();
	}

	/** The layout plans are generated for now (the active HQ layout). */
	public static String generatingLayout() {
		return HqLayouts.active().id();
	}

	/** Bumped every time a reload changes some layout's pack. */
	public static int generation() {
		return GENERATION.get();
	}

	/** Problems of the last reload. */
	public static List<String> errors() {
		return lastErrors;
	}

	/**
	 * Runs {@code body} generating {@code layoutId} with the pack whose hash is {@code hash} ("": no pack),
	 * the other layouts unchanged. Returns null when that pack is not known any more (not seen this
	 * session): the caller cannot rebuild the old plan and must fall back (full rebuild).
	 */
	public static <T> @Nullable T withPack(String layoutId, String hash, Supplier<T> body) {
		LayoutPack p;
		if (hash.isEmpty()) {
			p = LayoutPack.empty(layoutId);
		} else {
			p = HISTORY.get(layoutId + "@" + hash);
			if (p == null) {
				return null;
			}
		}
		Map<String, LayoutPack> prev = OVERRIDE.get();
		Map<String, LayoutPack> m = new TreeMap<>(prev != null ? prev : active);
		m.put(layoutId, p);
		OVERRIDE.set(m);
		try {
			return body.get();
		} finally {
			OVERRIDE.set(prev);
		}
	}

	// ------------------------------------------------------------------ loading

	/** The packs folder in the game's config dir. */
	public static Path dir() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve(FOLDER);
	}

	/** Re-reads {@code dir} when any file in it changed since the last read (cheap: names, sizes and mtimes); null if nothing changed. */
	public static synchronized @Nullable Load poll(Path dir) {
		String s = stampOf(dir);
		if (s.equals(stamp)) {
			return null;
		}
		return reload(dir);
	}

	/** Reads every pack in {@code dir} now and makes the good ones active. */
	public static synchronized Load reload(Path dir) {
		stamp = stampOf(dir);
		Map<String, LayoutPack> old = active;
		Map<String, LayoutPack> next = new TreeMap<>();
		List<String> errors = new ArrayList<>();
		for (HqLayoutInfo info : HqLayouts.all()) {
			String id = info.id();
			LayoutPack before = old.get(id);
			LayoutPack p = loadLayout(dir, id, before, errors);
			if (p != null && !p.isEmpty()) {
				next.put(id, p);
				HISTORY.put(id + "@" + p.hash(), p);
			}
		}
		if (Files.isDirectory(dir)) {
			try (Stream<Path> files = Files.list(dir)) {
				files.filter(f -> f.getFileName().toString().endsWith(".json")).forEach(f -> {
					String id = f.getFileName().toString().replaceFirst("\\.json$", "");
					if (HqLayouts.find(id).isEmpty() || !HqLayouts.find(id).get().id().equals(id)) {
						errors.add(f.getFileName() + ":1: no layout '" + id + "' (known: " + layoutIds() + "); file ignored");
					}
				});
			} catch (IOException e) {
				errors.add(dir + ": " + e.getMessage());
			}
		}
		List<String> changed = new ArrayList<>();
		for (HqLayoutInfo info : HqLayouts.all()) {
			String a = old.containsKey(info.id()) ? old.get(info.id()).hash() : "";
			String b = next.containsKey(info.id()) ? next.get(info.id()).hash() : "";
			if (!a.equals(b)) {
				changed.add(info.id());
			}
		}
		active = Collections.unmodifiableMap(next);
		lastErrors = List.copyOf(errors);
		if (!changed.isEmpty()) {
			GENERATION.incrementAndGet();
			T3Craft.LOGGER.info("[hq] layout packs changed: {} (from {})", changed, dir);
		}
		for (String e : errors) {
			T3Craft.LOGGER.warn("[hq] layout pack: {}", e);
		}
		return new Load(List.copyOf(errors), !changed.isEmpty(), List.copyOf(changed));
	}

	/** Makes {@code packs} active directly (hqRender, tests). */
	public static synchronized void use(Map<String, LayoutPack> packs) {
		active = Collections.unmodifiableMap(new TreeMap<>(packs));
		for (LayoutPack p : packs.values()) {
			HISTORY.put(p.layoutId() + "@" + p.hash(), p);
		}
		GENERATION.incrementAndGet();
	}

	private static List<String> layoutIds() {
		List<String> out = new ArrayList<>();
		for (HqLayoutInfo i : HqLayouts.all()) {
			out.add(i.id());
		}
		return out;
	}

	/** One layout's pack from {@code dir}; on errors, what can be kept from {@code before}. */
	private static @Nullable LayoutPack loadLayout(Path dir, String id, @Nullable LayoutPack before, List<String> errors) {
		Path json = dir.resolve(id + ".json");
		Path roomsDir = dir.resolve("rooms").resolve(id);
		Map<String, RoomTemplate> rooms = new TreeMap<>();
		if (Files.isDirectory(roomsDir)) {
			try (Stream<Path> files = Files.list(roomsDir)) {
				for (Path f : files.filter(f -> f.getFileName().toString().endsWith(".nbt")).sorted().toList()) {
					String module = f.getFileName().toString().replaceFirst("\\.nbt$", "");
					try {
						rooms.put(module, RoomTemplate.read(f));
					} catch (IOException | RuntimeException e) {
						errors.add("rooms/" + id + "/" + e.getMessage());
						LayoutPack.Room keep = before == null ? null : before.rooms().get(module);
						if (keep != null) {
							rooms.put(module, keep.template()); // last good
						}
					}
				}
			} catch (IOException e) {
				errors.add("rooms/" + id + ": " + e.getMessage());
			}
		}
		if (!Files.isRegularFile(json)) {
			return rooms.isEmpty() ? null : LayoutPack.ofRooms(id, rooms);
		}
		try {
			String text = Files.readString(json, StandardCharsets.UTF_8);
			return LayoutPack.parse(id, json.getFileName().toString(), text, rooms);
		} catch (LayoutPack.PackException e) {
			errors.add(e.getMessage() + " (keeping the last good " + id + " pack)");
			return before;
		} catch (IOException | RuntimeException e) {
			errors.add(json.getFileName() + ":1: " + e + " (keeping the last good " + id + " pack)");
			return before;
		}
	}

	/** Names, sizes and modification times of every pack file under {@code dir}. */
	static String stampOf(Path dir) {
		if (!Files.isDirectory(dir)) {
			return "none";
		}
		StringBuilder sb = new StringBuilder();
		try (Stream<Path> files = Files.walk(dir, 3)) {
			for (Path f : files.filter(Files::isRegularFile).sorted().toList()) {
				String rel = dir.relativize(f).toString().replace('\\', '/');
				if (rel.startsWith(DEFAULTS + "/")) {
					continue;
				}
				if (!rel.endsWith(".json") && !rel.endsWith(".nbt")) {
					continue;
				}
				sb.append(rel).append(':').append(Files.size(f)).append(':').append(Files.getLastModifiedTime(f).toMillis()).append('\n');
			}
		} catch (IOException | RuntimeException e) {
			return "error:" + e;
		}
		return sb.toString();
	}

	// ------------------------------------------------------------------ export

	/**
	 * Writes the current values of every layout to {@code dir}: {@code _defaults/<id>.json} (every
	 * role, palette field and parameter with the code's value, always rewritten) and {@code <id>.json}
	 * for layouts that have no pack file yet (the same content, ready to edit). Existing pack files
	 * are never overwritten. Returns the files written.
	 */
	public static List<Path> export(Path dir) throws IOException {
		List<Path> out = new ArrayList<>();
		Path defaults = dir.resolve(DEFAULTS);
		Files.createDirectories(defaults);
		Files.createDirectories(dir.resolve("rooms"));
		for (HqLayoutInfo info : HqLayouts.all()) {
			String text = dev.t3craft.hq.PackDefaults.exportJson(info.id());
			Path ref = defaults.resolve(info.id() + ".json");
			Files.writeString(ref, text, StandardCharsets.UTF_8);
			out.add(ref);
			Path own = dir.resolve(info.id() + ".json");
			if (!Files.exists(own)) {
				Files.writeString(own, text, StandardCharsets.UTF_8);
				out.add(own);
			}
			Files.createDirectories(dir.resolve("rooms").resolve(info.id()));
		}
		Path modules = dir.resolve(DEFAULTS).resolve("MODULES.txt");
		Files.writeString(modules, dev.t3craft.hq.PackDefaults.moduleList(), StandardCharsets.UTF_8);
		out.add(modules);
		return out;
	}
}
