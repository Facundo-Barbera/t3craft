package dev.t3craft.client.chat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The UI tokens as a resource (issue #12): the colours and metrics of {@link ChatTheme} live in
 * {@code assets/t3craft/gui/ui-style.json}, read through the resource manager by {@link UiStyleReloader},
 * so <b>F3+T reloads them</b> (and a resource pack can restyle the mod). Pure Java (Gson only).
 *
 * <p>Format:
 * <pre>{@code
 * { "version": 1,
 *   "themes": { "warm_studio": { "panel": "#F4EFE6", "row": 10, ... },
 *               "terminal":    { ... } } }
 * }</pre>
 * Each theme key is a {@link ChatTheme} component name. Colours are {@code "#RRGGBB"} (opaque) or
 * {@code "#AARRGGBB"}; metrics are numbers; {@code shadow} is a boolean; {@code sprites} is the sprite
 * prefix or {@code null} for flat fills. A key that is missing keeps the built-in value
 * ({@link ChatTheme#builtinWarmStudio()}, {@link ChatTheme#builtinTerminal()}), so the file may hold only
 * what it overrides. A bad value is skipped and logged; a file that is not valid JSON is ignored whole
 * and the previous style stays (a typo while editing never blanks the UI).
 *
 * <p>{@link ChatTheme#warmStudio()} / {@link ChatTheme#terminal()} return the same instance until the
 * next reload, so a screen can tell that the style changed with {@code theme != ChatTheme.warmStudio()}
 * (see {@code T3Screen}).
 */
public final class UiStyle {
	private static final Logger LOG = LoggerFactory.getLogger("t3craft/ui-style");

	/** Resource path under {@code assets/t3craft/}. */
	public static final String PATH = "gui/ui-style.json";
	private static final String CLASSPATH = "/assets/t3craft/" + PATH;

	private record State(ChatTheme warm, ChatTheme terminal, int generation) {
	}

	/** Result of reading one document. */
	public record Parsed(ChatTheme warm, ChatTheme terminal, List<String> problems) {
	}

	private static volatile State state = initial();

	private UiStyle() {
	}

	public static ChatTheme warmStudio() {
		return state.warm;
	}

	public static ChatTheme terminal() {
		return state.terminal;
	}

	/** Counts successful reloads (0 = the initial load); for logs and {@code /t3dev}. */
	public static int generation() {
		return state.generation;
	}

	/**
	 * Applies a document read by the resource manager. Returns the problems found (empty = clean); on a
	 * document that is not valid JSON nothing changes and the exception message is the one problem.
	 */
	public static List<String> reload(Reader json) {
		State cur = state;
		try {
			// over the built-in tokens, not over the last style: deleting a key from the file brings its default back
			Parsed p = parse(json, ChatTheme.builtinWarmStudio(), ChatTheme.builtinTerminal());
			state = new State(p.warm(), p.terminal(), cur.generation + 1);
			for (String problem : p.problems()) {
				LOG.warn("{}: {}", PATH, problem);
			}
			LOG.info("{} reloaded (#{})", PATH, state.generation);
			return p.problems();
		} catch (JsonParseException | IllegalStateException e) {
			LOG.warn("{} ignored, keeping the previous style: {}", PATH, e.getMessage());
			return List.of("not valid JSON: " + e.getMessage());
		}
	}

	/** The file is gone (a resource pack removed it): back to the built-in tokens. */
	public static void reloadBuiltin() {
		State cur = state;
		state = new State(ChatTheme.builtinWarmStudio(), ChatTheme.builtinTerminal(), cur.generation + 1);
	}

	private static State initial() {
		try (InputStream in = UiStyle.class.getResourceAsStream(CLASSPATH)) {
			if (in != null) {
				Parsed p = parse(new InputStreamReader(in, StandardCharsets.UTF_8), ChatTheme.builtinWarmStudio(), ChatTheme.builtinTerminal());
				p.problems().forEach(s -> LOG.warn("{}: {}", PATH, s));
				return new State(p.warm(), p.terminal(), 0);
			}
		} catch (IOException | JsonParseException | IllegalStateException e) {
			LOG.warn("{} unreadable, using the built-in tokens: {}", PATH, e.getMessage());
		}
		return new State(ChatTheme.builtinWarmStudio(), ChatTheme.builtinTerminal(), 0);
	}

	// ---- parsing -----------------------------------------------------------------------------------

	/** Reads {@code json} over the given base themes. Throws {@link JsonParseException} when it is not an object. */
	public static Parsed parse(Reader json, ChatTheme baseWarm, ChatTheme baseTerminal) {
		JsonElement root = JsonParser.parseReader(json);
		if (!root.isJsonObject()) {
			throw new JsonParseException("the top level must be an object");
		}
		List<String> problems = new ArrayList<>();
		JsonObject themes = object(root.getAsJsonObject(), "themes");
		ChatTheme warm = baseWarm;
		ChatTheme terminal = baseTerminal;
		if (themes == null) {
			problems.add("no \"themes\" object: nothing applied");
		} else {
			JsonObject w = object(themes, "warm_studio");
			JsonObject t = object(themes, "terminal");
			warm = w == null ? baseWarm : theme(w, baseWarm, "warm_studio", problems);
			terminal = t == null ? baseTerminal : theme(t, baseTerminal, "terminal", problems);
		}
		return new Parsed(warm, terminal, problems);
	}

	private static @Nullable JsonObject object(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null;
	}

	/** {@code base} with the keys of {@code o} applied; one entry in {@code problems} per bad or unknown key. */
	static ChatTheme theme(JsonObject o, ChatTheme base, String name, List<String> problems) {
		RecordComponent[] components = ChatTheme.class.getRecordComponents();
		Class<?>[] types = new Class<?>[components.length];
		Object[] args = new Object[components.length];
		Set<String> known = new HashSet<>();
		try {
			for (int i = 0; i < components.length; i++) {
				RecordComponent c = components[i];
				types[i] = c.getType();
				known.add(c.getName());
				args[i] = c.getAccessor().invoke(base);
				if (o.has(c.getName())) {
					try {
						args[i] = value(c.getType(), o.get(c.getName()));
					} catch (RuntimeException e) {
						problems.add(name + "." + c.getName() + ": " + e.getMessage() + " (kept the previous value)");
					}
				}
			}
			for (String key : o.keySet()) {
				if (!known.contains(key)) {
					problems.add(name + "." + key + ": unknown token");
				}
			}
			ChatTheme t = ChatTheme.class.getDeclaredConstructor(types).newInstance(args);
			// the Builder setters clamp the metrics to their minimum
			return t.toBuilder().row(t.row()).toolRow(t.toolRow()).listRow(t.listRow()).margin(t.margin()).gap(t.gap()).pad(t.pad()).build();
		} catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
			throw new IllegalStateException("cannot build a ChatTheme: " + e, e);
		}
	}

	private static @Nullable Object value(Class<?> type, JsonElement e) {
		if (type == String.class) {
			return e.isJsonNull() ? null : e.getAsString();
		}
		if (e.isJsonNull() || !e.isJsonPrimitive()) {
			throw new IllegalArgumentException("expected a value, got " + e);
		}
		JsonPrimitive p = e.getAsJsonPrimitive();
		if (type == boolean.class) {
			if (!p.isBoolean()) {
				throw new IllegalArgumentException("expected true or false, got " + p);
			}
			return p.getAsBoolean();
		}
		if (p.isNumber()) {
			return p.getAsInt();
		}
		if (p.isString()) {
			return color(p.getAsString());
		}
		throw new IllegalArgumentException("expected a colour or a number, got " + p);
	}

	/** {@code "#RRGGBB"} (opaque) or {@code "#AARRGGBB"}. */
	static int color(String s) {
		String hex = s.startsWith("#") ? s.substring(1) : s;
		try {
			long v = Long.parseLong(hex, 16);
			if (hex.length() == 6) {
				return 0xFF000000 | (int) v;
			}
			if (hex.length() == 8) {
				return (int) v;
			}
		} catch (NumberFormatException ignored) {
			// falls through to the message below
		}
		throw new IllegalArgumentException("\"" + s + "\" is not #RRGGBB or #AARRGGBB");
	}
}
