package dev.t3craft.client.pieces;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.pieces.PieceBrowserState.Sort;
import dev.t3craft.client.pieces.PieceBrowserState.SizeChoice;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Control;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Field;
import dev.t3craft.client.t3ui.kit.T3List;
import dev.t3craft.client.t3ui.kit.T3Panel;
import dev.t3craft.client.t3ui.kit.T3Select;
import dev.t3craft.client.t3ui.kit.T3Toggle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The piece browser as a page of the UI kit (docs/features/piece-browser.md): folders on the left, the
 * pieces of the chosen folder in the middle, and on the right the selected piece with what to ask of the
 * preview (facing, plot size, seed). The header filters (search words, tag, size class, favorites, sort);
 * the footer asks for the preview, copies its command, flips the favorite flag and shows what the game
 * answered. It lives inside {@code T3Screen} (the ⋯ menu → Pieces) and as {@link PieceBrowserScreen}
 * ({@code K}, {@code /t3craft pieces}, Settings → Connections).
 *
 * <p>The pieces come from {@link PieceCatalog}; the preview goes through {@link PiecePreviews#current()}.
 * Everything the user chooses lives in {@link PieceBrowserState}, so reopening the browser lands where it was.
 * The page is arranged again after every change, like the other kit pages (the lists are fields, so their
 * scroll survives).
 */
public final class PieceBrowserPanel extends T3Panel {
	private static final int GAP = 6;
	private static final int PIECE_ROW = 24;
	private static final int FOLDER_ROW = 14;
	/** Below this body width the folder column gives way to a folder select in the header. */
	private static final int NARROW_W = 430;
	private static final int FOOTER_LINES = 2;
	private static final int TOP_TAGS = 40;

	/** A row of the folder column: {@code path} null is "All pieces". */
	private record Folder(@Nullable String path, String label, int depth, int count) {
	}

	/** A choice of a filter select; {@code value} null = no filter. */
	private record Opt(@Nullable String value, String label) {
	}

	private final PieceBrowserState state = PieceBrowserState.get();
	private final T3List<Folder> folderList;
	private final T3List<PieceEntry> pieceList;
	private final KeyNav keys = new KeyNav();

	private @Nullable T3Field search;
	private @Nullable T3Field seedField;
	private @Nullable T3Field wField;
	private @Nullable T3Field dField;
	private @Nullable T3Field hField;

	/** The model, rebuilt at the start of every arrange. */
	private List<Folder> folders = List.of();
	private List<PieceEntry> shown = List.of();
	private boolean narrow;
	private boolean reveal = true;
	private boolean queryDirty;
	private String status;
	private Tone statusTone = Tone.MUTED;

	public PieceBrowserPanel() {
		PieceCatalog.refresh();
		this.status = PieceCatalog.summary();
		this.folderList = new T3List<>(FOLDER_ROW, new T3List.Row<Folder>() {
			@Override
			public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, Folder f, Rect rr, int mx, int my) {
				drawFolder(g, font, t, f, rr);
			}

			@Override
			public boolean mouseClicked(Folder f, Rect rr, double mx, double my, int button) {
				if (MouseButtons.isLeft(button)) {
					chooseFolder(f);
				}
				return true;
			}
		});
		this.pieceList = new T3List<>(PIECE_ROW, new T3List.Row<PieceEntry>() {
			@Override
			public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, PieceEntry e, Rect rr, int mx, int my) {
				drawPiece(g, font, t, e, rr);
			}

			@Override
			public boolean mouseClicked(PieceEntry e, Rect rr, double mx, double my, int button) {
				if (MouseButtons.isLeft(button)) {
					select(e, false);
				}
				return true;
			}

			@Override
			public @Nullable List<String> tooltip(PieceEntry e, Rect rr, double mx, double my) {
				List<String> tip = new ArrayList<>();
				tip.add(e.id());
				if (e.size() != null) {
					tip.add(e.size().text());
				}
				if (!e.tags().isEmpty()) {
					tip.add(String.join(", ", e.tags()));
				}
				return tip;
			}
		});
	}

	/** Opens with {@code id} selected and its folder open (the {@code /t3craft pieces <id>} form). */
	public PieceBrowserPanel select(@Nullable String id) {
		PieceEntry e = PieceCatalog.byId(id);
		if (e != null) {
			state.selectedId = e.id();
			state.clearFilters();
			state.folder = e.folder();
			reveal = true;
		}
		return this;
	}

	@Override
	public String title() {
		return "Piece browser";
	}

	// ---- chrome ------------------------------------------------------------------------------------------

	@Override
	protected int headerHeight() {
		return 2 * (T3Control.H + 4);
	}

	@Override
	protected int footerHeight() {
		return T3Control.H + 6 + FOOTER_LINES * T3Draw.lineHeight(font());
	}

	@Override
	public void tick() {
		if (queryDirty) { // typing re-filters on the next tick, never inside the key event that changed the box
			queryDirty = false;
			pieceList.scrollToTop();
			host().relayout();
		}
	}

	private void say(String msg, Tone tone) {
		status = msg;
		statusTone = tone;
	}

	// ---- the model -----------------------------------------------------------------------------------------

	/** Filters and sorts. Runs first in {@link #arrange}; the header and the footer read what it left. */
	private void refresh(int width) {
		narrow = width < NARROW_W;
		List<PieceEntry> all = PieceCatalog.all();
		String[] words = state.query.isBlank() ? new String[0] : state.query.toLowerCase(Locale.ROOT).strip().split("\\s+");
		List<PieceEntry> base = new ArrayList<>();
		for (PieceEntry e : all) {
			if (state.favoritesOnly && !state.isFavorite(e.id())) {
				continue;
			}
			if (state.tag != null && !e.tags().contains(state.tag)) {
				continue;
			}
			if (state.sizeClass != null && !state.sizeClass.equals(e.sizeClass())) {
				continue;
			}
			String hay = e.haystack();
			boolean match = true;
			for (String w : words) {
				if (!hay.contains(w)) {
					match = false;
					break;
				}
			}
			if (match) {
				base.add(e);
			}
		}
		// folders: every path of the whole catalog (so the column does not jump while filtering), counts from `base`
		TreeSet<String> paths = new TreeSet<>(Comparator.comparing((String p) -> p.replace('/', '\u0001'), String.CASE_INSENSITIVE_ORDER));
		for (PieceEntry e : all) {
			String[] seg = e.folder().split("/");
			StringBuilder p = new StringBuilder();
			for (String s : seg) {
				p.append(p.isEmpty() ? "" : "/").append(s);
				paths.add(p.toString());
			}
		}
		if (state.folder != null && !paths.contains(state.folder)) {
			state.folder = null; // the catalog changed under the selection
		}
		List<Folder> rows = new ArrayList<>();
		rows.add(new Folder(null, "All pieces", 0, base.size()));
		for (String p : paths) {
			int depth = (int) p.chars().filter(c -> c == '/').count();
			int count = (int) base.stream().filter(e -> e.inFolder(p)).count();
			rows.add(new Folder(p, p.substring(p.lastIndexOf('/') + 1), depth, count));
		}
		folders = rows;
		List<PieceEntry> list = new ArrayList<>();
		for (PieceEntry e : base) {
			if (state.folder == null || e.inFolder(state.folder)) {
				list.add(e);
			}
		}
		Comparator<PieceEntry> byName = Comparator.comparing(PieceEntry::name, String.CASE_INSENSITIVE_ORDER);
		Comparator<PieceEntry> byArea = Comparator.comparingInt(e -> e.size() == null ? Integer.MAX_VALUE : e.size().area());
		switch (state.sort) {
			case NAME -> list.sort(byName);
			case SMALL_FIRST -> list.sort(byArea.thenComparing(byName));
			case LARGE_FIRST -> list.sort(byArea.reversed().thenComparing(byName));
			default -> {
				// the catalog order already is folder, then name
			}
		}
		shown = list;
	}

	private @Nullable PieceEntry selected() {
		return PieceCatalog.byId(state.selectedId);
	}

	private int selectedIndex() {
		for (int i = 0; i < shown.size(); i++) {
			if (shown.get(i).id().equals(state.selectedId)) {
				return i;
			}
		}
		return -1;
	}

	private void select(PieceEntry e, boolean scrollTo) {
		state.selectedId = e.id();
		reveal = scrollTo;
		host().relayout();
	}

	private void chooseFolder(Folder f) {
		state.folder = f.path();
		pieceList.scrollToTop();
		host().relayout();
	}

	/** Up / Down through the list from anywhere in the page (also while the search box has the keyboard). */
	private void step(int dir) {
		if (shown.isEmpty()) {
			return;
		}
		int i = selectedIndex();
		int next = i < 0 ? (dir > 0 ? 0 : shown.size() - 1) : Math.max(0, Math.min(shown.size() - 1, i + dir));
		select(shown.get(next), true);
	}

	private final class KeyNav extends T3Control {
		@Override
		public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
		}

		@Override
		public boolean keyPressed(KeyEvent e) {
			if (e.key() == InputConstants.KEY_DOWN) {
				step(1);
				return true;
			}
			if (e.key() == InputConstants.KEY_UP) {
				step(-1);
				return true;
			}
			return false;
		}
	}

	// ---- header: search and filters -----------------------------------------------------------------------

	private T3Field search() {
		if (search == null) {
			search = new T3Field(font(), "Search pieces").maxLength(64).hint("Search: words of the name, folder or tags").onChange(v -> {
				state.query = v;
				queryDirty = true;
			}).onSubmit(this::preview);
			search.setValue(state.query);
		}
		return search;
	}

	@Override
	protected void arrangeHeader(Rect h) {
		int x = h.x();
		int y = h.y();
		int w = h.w();
		int sortW = Math.min(130, Math.max(92, w / 4));
		pinField(search(), x, y, w - sortW - 4);
		pin(new T3Select<>(List.of(Sort.values()), Sort::label, state.sort, v -> {
			state.sort = v;
			pieceList.scrollToTop();
			host().relayout();
		}), x + w - sortW, y, sortW, T3Control.H);
		pin(keys, x, y, 0, 0);

		int y2 = y + T3Control.H + 4;
		int favW = 104;
		int free = w - favW - 4 * (narrow ? 3 : 2);
		int cx = x;
		if (narrow) {
			int fw = free * 3 / 10;
			pin(folderSelect(), cx, y2, fw, T3Control.H);
			cx += fw + 4;
		}
		int tw = (narrow ? free * 4 / 10 : free * 55 / 100);
		pin(tagSelect(), cx, y2, tw, T3Control.H);
		cx += tw + 4;
		int sw = (narrow ? free * 3 / 10 : free - tw);
		pin(sizeSelect(), cx, y2, sw, T3Control.H);
		cx += sw + 4;
		pin(new T3Toggle("Favorites only", state.favoritesOnly, v -> {
			state.favoritesOnly = v;
			pieceList.scrollToTop();
			host().relayout();
		}), cx, y2, x + w - cx, T3Control.H);
	}

	private T3Select<Folder> folderSelect() {
		Folder cur = folders.get(0);
		for (Folder f : folders) {
			if (Objects.equals(f.path(), state.folder)) {
				cur = f;
			}
		}
		return new T3Select<>(folders, f -> "  ".repeat(f.depth()) + (f.path() == null ? "All folders" : f.label()) + " (" + f.count() + ")", cur, this::chooseFolder);
	}

	private T3Select<Opt> tagSelect() {
		Map<String, Integer> counts = new HashMap<>();
		for (PieceEntry e : PieceCatalog.all()) {
			for (String t : e.tags()) {
				counts.merge(t, 1, Integer::sum);
			}
		}
		List<String> tags = new ArrayList<>(counts.keySet());
		tags.sort(Comparator.<String>comparingInt(counts::get).reversed().thenComparing(Comparator.naturalOrder()));
		List<Opt> opts = new ArrayList<>();
		opts.add(new Opt(null, "Any tag"));
		for (String t : tags) {
			if (opts.size() > TOP_TAGS) {
				break;
			}
			if (counts.get(t) >= 2 || t.equals(state.tag)) {
				opts.add(new Opt(t, t + " (" + counts.get(t) + ")"));
			}
		}
		if (state.tag != null && opts.stream().noneMatch(o -> state.tag.equals(o.value()))) {
			opts.add(new Opt(state.tag, state.tag));
		}
		return optSelect(opts, state.tag, v -> state.tag = v);
	}

	private T3Select<Opt> sizeSelect() {
		List<Opt> opts = List.of(new Opt(null, "Any size"), new Opt("small", "Small (up to 6)"), new Opt("medium", "Medium (7-12)"), new Opt("large", "Large (13+)"));
		return optSelect(opts, state.sizeClass, v -> state.sizeClass = v);
	}

	private T3Select<Opt> optSelect(List<Opt> opts, @Nullable String current, java.util.function.Consumer<@Nullable String> set) {
		Opt cur = opts.get(0);
		for (Opt o : opts) {
			if (Objects.equals(o.value(), current)) {
				cur = o;
			}
		}
		return new T3Select<>(opts, Opt::label, cur, o -> {
			set.accept(o.value());
			pieceList.scrollToTop();
			host().relayout();
		});
	}

	// ---- body: folders, list, detail ------------------------------------------------------------------------

	@Override
	protected int arrange(Rect body, int y0) {
		refresh(body.w());
		int h = Math.max(body.h(), 60);
		int x = body.x();
		int w = body.w();
		int folderW = narrow ? 0 : Math.max(88, Math.min(130, w / 5));
		int rest = w - (narrow ? 0 : folderW + GAP);
		int listW = Math.max(110, Math.min(230, rest * 11 / 20));
		int detailW = rest - listW - GAP;
		if (detailW < 110) {
			detailW = 110;
			listW = Math.max(80, rest - GAP - detailW);
		}
		int top = y0 + 12;
		int listH = h - 12;
		int cx = x;
		if (!narrow) {
			text(cx + 2, y0 + 1, folderW, "Folders", Tone.MUTED);
			folderList.setItems(folders);
			place(folderList, cx, top, folderW, listH);
			cx += folderW + GAP;
		}
		int total = PieceCatalog.all().size();
		text(cx + 2, y0 + 1, listW, state.filtering() ? shown.size() + " of " + total + " pieces" : total + " pieces", Tone.MUTED);
		pieceList.setItems(shown);
		pieceList.setEmptyText(total == 0
				? "The catalog is empty.\n\nThe code catalog could not be read; see the log."
				: "No piece matches.\n\nClear the filters (button below) or widen the search.");
		place(pieceList, cx, top, listW, listH);
		if (reveal) {
			reveal = false;
			int i = selectedIndex();
			if (i >= 0) {
				pieceList.ensureVisible(i);
			}
		}
		cx += listW + GAP;
		int bottom = detail(cx, y0, detailW);
		return Math.max(y0 + h, bottom);
	}

	private int detail(int x, int y0, int w) {
		PieceEntry p = selected();
		int y = y0;
		if (p == null) {
			y = text(x, y, w, "Pick a piece from the list.", Tone.NORMAL);
			y = text(x, y + 4, w, "Up and Down move through the list. Enter in the search box asks for the preview of the selected piece.", Tone.MUTED);
			return text(x, y + 6, w, PieceCatalog.summary(), Tone.MUTED);
		}
		y = text(x, y, w, p.name(), Tone.TITLE);
		y = text(x, y, w, p.id(), Tone.CODE);
		y = text(x, y, w, p.folder().replace("/", " / ") + (p.size() == null ? "" : "  |  " + p.size().sizeClass()), Tone.MUTED);
		if (p.size() != null) {
			y = text(x, y, w, "Builds " + p.size().text(), Tone.MUTED);
		}
		if (!p.tags().isEmpty()) {
			y = text(x, y, w, String.join("\n", T3Draw.wrap(font(), String.join(", ", p.tags()), w, 2)), Tone.MUTED);
		}
		y += 6;

		// facing
		y = text(x, y, w, "Faces", Tone.MUTED);
		place(new T3Select<>(PieceRequest.FRONTS, PieceBrowserPanel::cap, state.front, v -> state.front = v), x, y, w, T3Control.H);
		y += T3Control.H + 4;

		// plot size
		SizeChoice choice = effectiveChoice(p);
		y = text(x, y, w, "Plot size", Tone.MUTED);
		List<SizeChoice> choices = p.size() == null ? List.of(SizeChoice.DEFAULT, SizeChoice.CUSTOM) : List.of(SizeChoice.SMALLEST, SizeChoice.MIDDLE, SizeChoice.LARGEST, SizeChoice.CUSTOM);
		place(new T3Select<>(choices, SizeChoice::label, choice, v -> {
			state.sizeChoice = v;
			host().relayout();
		}), x, y, w, T3Control.H);
		y += T3Control.H + 4;
		if (choice == SizeChoice.CUSTOM) {
			int fw = (w - 8) / 3;
			placeField(wField(), x, y, fw);
			placeField(dField(), x + fw + 4, y, fw);
			placeField(hField(), x + 2 * (fw + 4), y, w - 2 * (fw + 4));
			y += T3Control.H + 4;
		} else {
			int[] box = boxFor(p, choice);
			String what = box == null || box[0] == 0 ? "the piece's own default size" : box[0] + " x " + box[1] + " x " + box[2] + " (W x D x H)";
			y = text(x + 2, y, w - 4, what, Tone.MUTED) + 4;
		}

		// seed
		y = text(x, y, w, "Seed (same seed, same blocks)", Tone.MUTED);
		int bw = 16 + 2 + 16 + 2 + 48 + 2;
		placeField(seedField(), x, y, Math.max(40, w - bw));
		int bx = x + Math.max(40, w - bw) + 2;
		place(new T3Button("-", () -> nudgeSeed(-1)), bx, y, 16, T3Control.H);
		place(new T3Button("+", () -> nudgeSeed(1)), bx + 18, y, 16, T3Control.H);
		place(new T3Button("Random", () -> {
			state.seedText = Integer.toString(ThreadLocalRandom.current().nextInt(1, 100000));
			seedField().setValue(state.seedText);
			host().relayout();
		}), bx + 36, y, 48, T3Control.H);
		y += T3Control.H + 6;

		place(new T3Toggle("Stay here after asking", state.keepOpen, v -> state.keepOpen = v), x, y, w, T3Control.H);
		return y + T3Control.H + 2;
	}

	private SizeChoice effectiveChoice(PieceEntry p) {
		SizeChoice c = state.sizeChoice;
		if (p.size() == null) {
			return c == SizeChoice.CUSTOM ? c : SizeChoice.DEFAULT;
		}
		return c == SizeChoice.DEFAULT ? SizeChoice.MIDDLE : c;
	}

	/** {@code {w, d, h}} for the choice, {@code {0, 0, 0}} for "default size", null when custom text is not valid. */
	private int @Nullable [] boxFor(PieceEntry p, SizeChoice c) {
		PieceEntry.Dims d = p.size();
		if (c == SizeChoice.CUSTOM) {
			Integer w = dim(state.customW);
			Integer dd = dim(state.customD);
			Integer hh = dim(state.customH);
			return w == null || dd == null || hh == null ? null : new int[] {w, dd, hh};
		}
		if (d == null || c == SizeChoice.DEFAULT) {
			return new int[] {0, 0, 0};
		}
		return switch (c) {
			case SMALLEST -> new int[] {d.minW(), d.minD(), d.minH()};
			case LARGEST -> new int[] {Math.max(d.minW(), Math.min(24, d.maxW())), Math.max(d.minD(), Math.min(24, d.maxD())), d.minH() + 4};
			default -> new int[] {(d.minW() + d.maxW()) / 2, (d.minD() + d.maxD()) / 2, d.minH() + 2};
		};
	}

	private static @Nullable Integer dim(String s) {
		try {
			int n = Integer.parseInt(s.strip());
			return n >= 1 && n <= 64 ? n : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private @Nullable Long seed() {
		try {
			return Long.parseLong(state.seedText.strip());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void nudgeSeed(int by) {
		Long s = seed();
		state.seedText = Long.toString((s == null ? 0 : s) + by);
		seedField().setValue(state.seedText);
		host().relayout();
	}

	private T3Field seedField() {
		if (seedField == null) {
			seedField = new T3Field(font(), "Seed").maxLength(18).onChange(v -> state.seedText = v).onSubmit(this::preview);
			seedField.setValue(state.seedText);
		}
		return seedField;
	}

	private T3Field wField() {
		if (wField == null) {
			wField = new T3Field(font(), "Plot width").maxLength(2).hint("W").onChange(v -> state.customW = v);
			wField.setValue(state.customW);
		}
		return wField;
	}

	private T3Field dField() {
		if (dField == null) {
			dField = new T3Field(font(), "Plot depth").maxLength(2).hint("D").onChange(v -> state.customD = v);
			dField.setValue(state.customD);
		}
		return dField;
	}

	private T3Field hField() {
		if (hField == null) {
			hField = new T3Field(font(), "Plot height").maxLength(2).hint("H").onChange(v -> state.customH = v);
			hField.setValue(state.customH);
		}
		return hField;
	}

	private static String cap(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	// ---- rows --------------------------------------------------------------------------------------------------

	private void drawFolder(GuiGraphicsExtractor g, Font font, ChatTheme t, Folder f, Rect rr) {
		boolean sel = Objects.equals(f.path(), state.folder);
		if (sel) {
			ChatDraw.round(g, rr.x() + 1, rr.y(), rr.w() - 2, rr.h(), t.selected());
		}
		String count = Integer.toString(f.count());
		int cw = font.width(count);
		int tx = rr.x() + 5 + 7 * f.depth();
		int color = sel ? t.accent() : f.count() == 0 ? t.disabled() : t.text();
		T3Draw.text(g, font, t, ChatDraw.clip(font, f.label(), rr.right() - 8 - cw - tx), tx, rr.y() + (rr.h() - 8) / 2, color);
		T3Draw.text(g, font, t, count, rr.right() - 5 - cw, rr.y() + (rr.h() - 8) / 2, t.muted());
	}

	private void drawPiece(GuiGraphicsExtractor g, Font font, ChatTheme t, PieceEntry e, Rect rr) {
		boolean sel = e.id().equals(state.selectedId);
		boolean fav = state.isFavorite(e.id());
		if (sel) {
			ChatDraw.round(g, rr.x() + 1, rr.y() + 1, rr.w() - 2, rr.h() - 2, t.selected());
		}
		int tx = rr.x() + 6;
		int right = rr.right() - 6 - (fav ? 9 : 0);
		T3Draw.text(g, font, t, ChatDraw.clip(font, e.name(), right - tx), tx, rr.y() + 3, sel ? t.accent() : t.text());
		String sub = e.folder().substring(e.folder().lastIndexOf('/') + 1) + (e.size() == null ? "" : "  |  " + e.size().text());
		T3Draw.text(g, font, t, ChatDraw.clip(font, sub, rr.right() - 6 - tx), tx, rr.y() + 13, t.muted());
		if (fav) { // a small brass marker, no glyph (the bitmap font has no star)
			g.fill(rr.right() - 12, rr.y() + 4, rr.right() - 6, rr.y() + 10, t.brass());
		}
	}

	// ---- footer: actions and feedback -----------------------------------------------------------------------------

	@Override
	protected void arrangeFooter(Rect f) {
		PieceEntry p = selected();
		boolean compact = f.w() < 340;
		int x = f.x();
		int y = f.y();
		T3Button go = new T3Button(compact ? "Preview" : "Preview in game", T3Button.Style.PRIMARY, this::preview);
		int goW = compact ? 64 : 112;
		go.setEnabled(p != null);
		go.setTooltip(p == null ? new String[] {"Select a piece first"}
				: new String[] {"Asks the game to build it at real size:", "/" + describe(p), "(the browser closes unless \"Stay here\" is on)"});
		pin(go, x, y, goW, T3Control.H);
		x += goW + 4;
		T3Button copy = new T3Button(compact ? "Copy" : "Copy command", this::copyCommand);
		int copyW = compact ? 40 : 98;
		copy.setEnabled(p != null);
		copy.setTooltip("Puts the preview command on the clipboard", "(paste it in chat: T, Ctrl+V, Enter)");
		pin(copy, x, y, copyW, T3Control.H);
		x += copyW + 4;
		boolean fav = p != null && state.isFavorite(p.id());
		T3Button favourite = new T3Button(fav ? "Unfavorite" : "Favorite", this::toggleFavorite);
		int favW = compact ? 66 : 84;
		favourite.setEnabled(p != null);
		pin(favourite, x, y, favW, T3Control.H);
		x += favW + 4;
		if (state.filtering() && f.right() - x >= 80) {
			pin(new T3Button("Clear filters", T3Button.Style.GHOST, this::clearFilters), x, y, Math.min(86, f.right() - x), T3Control.H);
		}
		int ty = y + T3Control.H + 4;
		for (String line : T3Draw.wrap(font(), status, f.w() - 4, FOOTER_LINES)) {
			pinText(f.x() + 2, ty, line, statusTone);
			ty += T3Draw.lineHeight(font());
		}
	}

	/** The command of the current choices, or a placeholder when they are not valid yet. */
	private String describe(PieceEntry p) {
		PieceRequest r = request(p);
		return r == null ? PieceRequest.COMMAND + " " + p.id() + " ..." : r.command();
	}

	/** The request for the current choices, or null when the size or the seed is not a number. */
	private @Nullable PieceRequest request(PieceEntry p) {
		int[] box = boxFor(p, effectiveChoice(p));
		Long seed = seed();
		return box == null || seed == null ? null : new PieceRequest(p, state.front, box[0], box[1], box[2], seed);
	}

	private @Nullable PieceRequest checkedRequest() {
		PieceEntry p = selected();
		if (p == null) {
			say("Select a piece first.", Tone.WARN);
			host().relayout();
			return null;
		}
		PieceRequest r = request(p);
		if (r == null) {
			say(boxFor(p, effectiveChoice(p)) == null ? "Width, depth and height must be whole numbers from 1 to 64." : "The seed must be a whole number.", Tone.ERROR);
			host().relayout();
		}
		return r;
	}

	private void preview() {
		PieceRequest r = checkedRequest();
		if (r == null) {
			return;
		}
		PiecePreview.Result res = PiecePreviews.current().request(r);
		Tone tone = switch (res.status()) {
			case SENT -> Tone.OK;
			case UNAVAILABLE -> Tone.WARN;
			case FAILED -> Tone.ERROR;
		};
		say(res.message(), tone);
		Minecraft mc = Minecraft.getInstance();
		if (res.status() == PiecePreview.Status.SENT && !state.keepOpen) {
			mc.gui.setScreen(null);
			if (mc.player != null) {
				mc.player.sendOverlayMessage(Component.literal("Piece browser: asked for " + r.piece().id() + " (" + r.front() + ", seed " + r.seed() + ")"));
			}
			return;
		}
		host().relayout();
	}

	private void copyCommand() {
		PieceRequest r = checkedRequest();
		if (r == null) {
			return;
		}
		Minecraft.getInstance().keyboardHandler.setClipboard("/" + r.command());
		say("Copied /" + r.command(), Tone.OK);
		host().relayout();
	}

	private void toggleFavorite() {
		PieceEntry p = selected();
		if (p == null) {
			return;
		}
		boolean now = state.toggleFavorite(p.id());
		say((now ? "Added " : "Removed ") + p.name() + (now ? " to" : " from") + " your favorites.", Tone.OK);
		host().relayout();
	}

	private void clearFilters() {
		state.clearFilters();
		if (search != null) {
			search.setValue("");
		}
		pieceList.scrollToTop();
		say(PieceCatalog.summary(), Tone.MUTED);
		host().relayout();
	}
}
