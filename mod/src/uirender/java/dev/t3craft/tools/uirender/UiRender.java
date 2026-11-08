package dev.t3craft.tools.uirender;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.t3craft.client.chat.compose.ComposerView;
import dev.t3craft.client.chat.compose.ImageThumbs;
import dev.t3craft.client.t3ui.T3Screen;
import dev.t3craft.client.chat.MouseButtons;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * ui-render: draws the game's T3 screens to PNGs with mock data, without starting Minecraft (see
 * docs/tools/ui-render.md). It builds the real {@link T3Screen} (and the dialogs over it) from the
 * client classes with injected data sources ({@link Mock}), runs {@code init}/{@code tick}, calls
 * {@code extractRenderState} on a {@link RecordingGraphics}, and paints what it recorded with
 * {@link Raster} using the real font and sprites.
 *
 * <pre>
 * ./gradlew uiRender -PuiArgs="--all"
 * ./gradlew uiRender -PuiArgs="--scenario markdown,running --size 2560x1380 --gui-scale 3 --mouse 400,300"
 * ./gradlew uiRender -PuiArgs="--scenario many-threads --compare /home/user/projects/t3craft-ref/ui/t3craft-ingame.png"
 * </pre>
 *
 * Options: {@code --list}, {@code --scenario a,b | --all}, {@code --size WxH[,WxH]} (default
 * 2560x1380), {@code --gui-scale N[,N]} (default 3), {@code --mouse X,Y} (window pixels) or
 * {@code --mouse-gui X,Y}, {@code --compare IMAGE} (side by side with a reference), {@code --out DIR}
 * (default build/ui-render). From code (the render-service daemon, {@code kind: ui}): {@link #render}.
 */
public final class UiRender {
	private UiRender() {
	}

	/** One render. Mouse coordinates are window pixels; negative = no hover. */
	public record Request(String scenario, int width, int height, int guiScale, int mouseX, int mouseY, @Nullable Path compare, Path outDir) {
		public static Request of(String scenario, Path outDir) {
			return new Request(scenario, 2560, 1380, 3, -1, -1, null, outDir);
		}
	}

	/** {@code png}: the render; {@code comparison}: side by side with the reference, when asked; {@code notes}: things that did not work. */
	public record Result(Path png, @Nullable Path comparison, Path texts, List<String> notes) {
	}

	/** Names of the bundled scenarios. */
	public static List<String> scenarios() {
		return Fixture.names();
	}

	private record Frame(List<Ops.Op> ops, List<Ops.Tooltip> tooltips) {
	}

	private static final int OFF = -10_000;

	// ---- API -------------------------------------------------------------------------------------

	public static synchronized Result render(Request r) throws Exception {
		Headless.install();
		Fixture fx = Fixture.load(r.scenario());
		if (fx == null) {
			throw new IllegalArgumentException("unknown scenario '" + r.scenario() + "'; known: " + scenarios());
		}
		int scale = Math.max(1, r.guiScale());
		int guiW = (r.width() + scale - 1) / scale;
		int guiH = (r.height() + scale - 1) / scale;
		Headless.gui(guiW, guiH);
		Assets.clearMemory();
		List<String> notes = new ArrayList<>();

		Mock mock = new Mock(fx);
		mock.prepare();
		Mock.Screens screens = mock.build();
		Screen input = screens.top() != null ? screens.top() : screens.main();
		init(screens.main(), guiW, guiH);
		if (screens.top() != null) {
			init(screens.top(), guiW, guiH);
		}
		for (int i = 0; i < Math.max(1, fx.ticks); i++) {
			screens.main().tick();
		}

		int mx = r.mouseX() < 0 ? OFF : r.mouseX() / scale;
		int my = r.mouseY() < 0 ? OFF : r.mouseY() / scale;
		frame(screens, guiW, guiH, OFF, OFF); // lets the conversation sync the composer before it is set up
		if (fx.composer != null) {
			composer(screens.main(), fx.composer, notes);
		}
		int[] mouse = {mx, my};
		for (JsonElement e : fx.steps) {
			try {
				step(e.getAsJsonObject(), screens, input, guiW, guiH, mouse, notes);
			} catch (RuntimeException | LinkageError ex) {
				notes.add("step " + e + " failed: " + describe(ex));
			}
		}
		Frame last = frame(screens, guiW, guiH, mouse[0], mouse[1]);

		BufferedImage full = Raster.paint(last.ops(), last.tooltips(), guiW, guiH, scale);
		BufferedImage img = full.getSubimage(0, 0, Math.min(r.width(), full.getWidth()), Math.min(r.height(), full.getHeight()));
		Files.createDirectories(r.outDir());
		String base = r.scenario() + "-" + r.width() + "x" + r.height() + "-gui" + scale;
		Path png = r.outDir().resolve(base + ".png");
		ImageIO.write(img, "png", png.toFile());
		Path texts = r.outDir().resolve(base + ".texts.txt");
		Files.write(texts, textDump(last));
		Path cmp = null;
		if (r.compare() != null) {
			cmp = r.outDir().resolve(base + "-compare.png");
			sideBySide(img, r.compare(), cmp);
		}
		return new Result(png, cmp, texts, notes);
	}

	// ---- one frame -------------------------------------------------------------------------------

	private static void init(Screen s, int w, int h) throws ReflectiveOperationException {
		s.width = w;
		s.height = h;
		try {
			var m = Screen.class.getDeclaredMethod("init");
			m.setAccessible(true);
			m.invoke(s);
		} catch (InvocationTargetException e) {
			throw new IllegalStateException("init() of " + s.getClass().getSimpleName() + " failed: " + describe(e.getCause()), e.getCause());
		}
	}

	private static Frame frame(Mock.Screens s, int guiW, int guiH, int mx, int my) {
		Font font = Minecraft.getInstance().font;
		RecordingGraphics g = Headless.allocate(RecordingGraphics.class);
		g.begin(guiW, guiH, font);
		boolean dialog = s.top() != null;
		s.main().extractBackground(g, mx, my, 0f);
		s.main().extractRenderState(g, dialog ? OFF : mx, dialog ? OFF : my, 0f);
		if (dialog) {
			// the dialog has no background of its own here (the game blurs what is behind it): dim it
			g.fill(0, 0, guiW, guiH, 0xA0000000);
			s.top().extractRenderState(g, mx, my, 0f);
		}
		return new Frame(g.ops(), g.tooltips());
	}

	private static List<String> textDump(Frame f) {
		List<String> lines = new ArrayList<>();
		for (Ops.Op op : f.ops()) {
			if (op instanceof Ops.Text t) {
				lines.add(Math.round(t.x()) + "," + Math.round(t.y()) + "\t" + RecordingGraphics.plain(t.glyphs()));
			}
		}
		for (Ops.Tooltip tip : f.tooltips()) {
			for (List<Ops.G> l : tip.lines()) {
				lines.add("tooltip " + tip.x() + "," + tip.y() + "\t" + RecordingGraphics.plain(l));
			}
		}
		return lines;
	}

	// ---- scripted input ----------------------------------------------------------------------------

	/**
	 * Steps: {@code {"op":"clickText"|"rightClickText","text":"…"}} (clicks the first drawn text that
	 * contains it, so scenarios survive layout changes), {@code {"op":"hoverText","text":"…"}},
	 * {@code {"op":"scroll","text":"…","amount":-3}}, {@code {"op":"tick","n":5}}.
	 */
	private static void step(JsonObject st, Mock.Screens screens, Screen input, int guiW, int guiH, int[] mouse, List<String> notes) {
		String op = Fixture.str(st, "op", "");
		if (op.equals("tick")) {
			for (int i = 0; i < Fixture.num(st, "n", 1); i++) {
				screens.main().tick();
			}
			return;
		}
		String text = Fixture.str(st, "text", "");
		Frame f = frame(screens, guiW, guiH, mouse[0], mouse[1]);
		Ops.Text at = find(f, text);
		if (at == null) {
			notes.add("step " + op + ": no drawn text contains '" + text + "'");
			return;
		}
		double x = at.x() + 3;
		double y = at.y() + 4;
		switch (op) {
			case "clickText" -> click(input, x, y, MouseButtons.LEFT);
			case "rightClickText" -> click(input, x, y, MouseButtons.RIGHT);
			case "hoverText" -> {
				mouse[0] = (int) x;
				mouse[1] = (int) y;
			}
			case "scroll" -> input.mouseScrolled(x, y, 0, Fixture.num(st, "amount", -3));
			default -> notes.add("unknown step op '" + op + "'");
		}
		if (!op.equals("hoverText") && st.has("keepMouse") && st.get("keepMouse").getAsBoolean()) {
			mouse[0] = (int) x;
			mouse[1] = (int) y;
		}
	}

	private static void click(Screen s, double x, double y, int button) {
		MouseButtonEvent e = new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
		s.mouseClicked(e, false);
		s.mouseReleased(e);
	}

	private static Ops.@Nullable Text find(Frame f, String needle) {
		for (Ops.Op op : f.ops()) {
			if (op instanceof Ops.Text t && RecordingGraphics.plain(t.glyphs()).contains(needle)) {
				return t;
			}
		}
		return null;
	}

	// ---- composer (draft text, image attachments) ----------------------------------------------------

	/**
	 * Fills the composer through its public state API ({@code ComposerView.state()}: the editor and
	 * {@code addImage}) and hands the thumbnails their pictures (the game decodes them with stb_image
	 * and uploads textures: neither exists here). Best effort: if the composer changed shape the
	 * scenario still renders, and the note says what was skipped.
	 */
	private static void composer(T3Screen screen, JsonObject spec, List<String> notes) {
		try {
			ComposerView view = (ComposerView) Headless.get(screen, T3Screen.class, "composer");
			if (view == null) {
				notes.add("composer: the screen has none (read-only thread?)");
				return;
			}
			if (spec.has("draft")) {
				view.state().editor().set(spec.get("draft").getAsString());
			}
			int images = Fixture.num(spec, "images", 0);
			if (images > 0) {
				view.state().refreshAccess();
				ImageThumbs thumbs = (ImageThumbs) Headless.get(view, ComposerView.class, "thumbs");
				@SuppressWarnings("unchecked")
				Map<String, ImageThumbs.Thumb> ready = (Map<String, ImageThumbs.Thumb>) Headless.get(thumbs, ImageThumbs.class, "ready");
				for (int i = 0; i < images; i++) {
					String localId = "img" + i;
					BufferedImage pic = picture(i);
					ByteArrayOutputStream png = new ByteArrayOutputStream();
					ImageIO.write(pic, "png", png);
					String why = view.state().addImage(localId, "screenshot-" + (i + 1) + ".png", png.toByteArray());
					if (why != null) {
						notes.add("composer: image " + i + " refused: " + why);
						continue;
					}
					Identifier id = Identifier.fromNamespaceAndPath("uirender", "thumb/" + localId);
					Assets.register(id.toString(), pic);
					ready.put(localId, new ImageThumbs.Thumb(id, pic.getWidth(), pic.getHeight()));
				}
			}
		} catch (IOException | RuntimeException | LinkageError e) {
			notes.add("composer setup skipped: " + describe(e));
		}
	}

	/** A recognisable 64×40 picture per index (a gradient with a frame), used as an attachment. */
	private static BufferedImage picture(int i) {
		BufferedImage img = new BufferedImage(64, 40, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		float hue = (i * 0.17f) % 1f;
		for (int y = 0; y < 40; y++) {
			g.setColor(Color.getHSBColor(hue, 0.55f, 0.95f - y * 0.012f));
			g.fillRect(0, y, 64, 1);
		}
		g.setColor(new Color(0, 0, 0, 90));
		g.drawRect(0, 0, 63, 39);
		g.dispose();
		return img;
	}

	// ---- compare -----------------------------------------------------------------------------------

	/** Ours on the left, the reference on the right scaled to the same height, a 4 px magenta rule between. */
	private static void sideBySide(BufferedImage ours, Path reference, Path out) throws IOException {
		BufferedImage ref = ImageIO.read(reference.toFile());
		if (ref == null) {
			throw new IOException("cannot read the reference image " + reference);
		}
		int h = ours.getHeight();
		int rw = Math.max(1, Math.round(ref.getWidth() * (h / (float) ref.getHeight())));
		BufferedImage both = new BufferedImage(ours.getWidth() + 4 + rw, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = both.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(ours, 0, 0, null);
		g.setColor(new Color(0xFFFF00FF, true));
		g.fillRect(ours.getWidth(), 0, 4, h);
		g.drawImage(ref, ours.getWidth() + 4, 0, rw, h, null);
		g.dispose();
		ImageIO.write(both, "png", out.toFile());
	}

	private static String describe(Throwable t) {
		StringBuilder b = new StringBuilder(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
		StackTraceElement[] st = t.getStackTrace();
		for (int i = 0; i < Math.min(3, st.length); i++) {
			b.append("\n    at ").append(st[i]);
		}
		return b.toString();
	}

	// ---- CLI -----------------------------------------------------------------------------------------

	public static void main(String[] args) throws Exception {
		Path out = Path.of("build/ui-render");
		List<String> names = new ArrayList<>();
		List<int[]> sizes = new ArrayList<>();
		List<Integer> scales = new ArrayList<>();
		int mx = -1;
		int my = -1;
		boolean mouseGui = false;
		Path compare = null;
		boolean all = false;
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--list" -> {
					scenarios().forEach(System.out::println);
					return;
				}
				case "--all" -> all = true;
				case "--scenario" -> names.addAll(List.of(args[++i].split(",")));
				case "--size" -> {
					for (String s : args[++i].split(",")) {
						String[] wh = s.toLowerCase().split("x");
						sizes.add(new int[] {Integer.parseInt(wh[0].trim()), Integer.parseInt(wh[1].trim())});
					}
				}
				case "--gui-scale" -> {
					for (String s : args[++i].split(",")) {
						scales.add(Integer.parseInt(s.trim()));
					}
				}
				case "--mouse", "--mouse-gui" -> {
					mouseGui = args[i].equals("--mouse-gui");
					String[] xy = args[++i].split(",");
					mx = Integer.parseInt(xy[0].trim());
					my = Integer.parseInt(xy[1].trim());
				}
				case "--compare" -> compare = Path.of(args[++i]);
				case "--out" -> out = Path.of(args[++i]);
				default -> throw new IllegalArgumentException("unknown option " + args[i]);
			}
		}
		if (all || names.isEmpty()) {
			names = scenarios();
		}
		if (sizes.isEmpty()) {
			sizes.add(new int[] {2560, 1380});
		}
		if (scales.isEmpty()) {
			scales.add(3);
		}
		int failures = 0;
		for (String name : names) {
			for (int[] wh : sizes) {
				for (int scale : scales) {
					int px = mouseGui && mx >= 0 ? mx * scale : mx;
					int py = mouseGui && my >= 0 ? my * scale : my;
					try {
						Result r = render(new Request(name, wh[0], wh[1], scale, px, py, compare, out));
						System.out.println(r.png() + (r.comparison() != null ? "  (compare: " + r.comparison() + ")" : ""));
						r.notes().forEach(n -> System.out.println("  note: " + n));
					} catch (Exception | LinkageError e) {
						failures++;
						System.err.println(name + " " + wh[0] + "x" + wh[1] + " gui" + scale + " FAILED: " + describe(e));
						e.printStackTrace();
					}
				}
			}
		}
		System.exit(failures == 0 ? 0 : 1);
	}
}
