// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
// Layout, metrics and drawing follow AgentCraft's console (mod/src/client/java/dev/agentcraft/client/
// console/ConsoleScreen.java: paper panel, 20 px header with a rule, inset feed with a 2 px speaker
// stripe, 8 px avatar and hanging text, minute separators, 6 px scrollbar, "↓ newer below" pill,
// key-cap footer, 18 px command bar with a brass ">" under the panel), the tool lines of its desk
// monitors (monitor/LogRows.java: 12 px icon + tool name + argument, 13 px rows, tinted diff rows) and
// the decision screen's header navigation (decisions/DecisionScreen.java: ‹ n/N › key caps).
package dev.t3craft.client.chat;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.t3craft.client.chat.ChatEntry.Kind;
import dev.t3craft.client.chat.ChatTurns.Block;
import dev.t3craft.client.chat.ChatTurns.Message;
import dev.t3craft.client.chat.ChatTurns.Role;
import dev.t3craft.client.chat.ChatTurns.ToolRun;
import dev.t3craft.client.chat.ChatTurns.Turn;
import dev.t3craft.client.chat.Markdown.Span;
import dev.t3craft.client.t3ui.sidebar.Spinner;
import dev.t3craft.t3.ThreadState;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Reusable widget: the read-only conversation of one T3 thread, drawn as an AgentCraft console in
 * any rectangle with any {@link ChatTheme}. Owns the {@link ThreadFeed} of the thread it shows
 * (one at a time): {@link #show} switches threads, {@link #close()} releases its subscription. No dependency
 * on a particular Screen: the host forwards render, input and tick.
 *
 * <pre>{@code
 * ConversationPanel conv = new ConversationPanel(ChatScreens.defaultSource(), ChatTheme.warmStudio());
 * conv.show(envId, envLabel, threadId, title);
 * // tick:   conv.tick();
 * // render: conv.setBounds(new Rect(x, y, w, h)); conv.render(g, font, mouseX, mouseY);
 * // input:  conv.mouseClicked(mx, my, button); conv.mouseScrolled(mx, my, dy); conv.keyPressed(event);
 * // removed(): conv.close();
 * }</pre>
 *
 * <p>Inside its bounds, top to bottom: a paper panel with the header (title, environment, state dot,
 * optional ‹ n/N › navigation), the "who needs it" callout, the inset feed and a key-cap footer; then
 * the command bar (ready when the environment can operate; read-only shows an "Enable control" button). In the feed every
 * turn has a colour stripe and an avatar (clay "you", the agent colour for the agent), consecutive
 * tool calls are one collapsed line ("4 steps · $, Edit, Search") that unfolds into AgentCraft monitor
 * tool lines, each of which unfolds its output; Markdown is rendered. Links (feat/chat-links): a T3
 * thread link is a chip with the thread's state and title and opens it ({@link ThreadLinks}), a web
 * link opens the browser after the vanilla confirmation, a file path is copied.
 * {@link #setCompact(boolean) Compact} drops the footer and the second header line for small hosts.
 */
public final class ConversationPanel implements AutoCloseable {
	private static final int FIELD_H = 18;
	private static final int HEADER_H = 20;
	private static final int COMPACT_HEADER_H = 14;
	private static final int FOOTER_H = 14;
	private static final int STRIPE_X = 3;
	private static final int AVATAR_X = 8;
	private static final int TEXT_X = 19;
	private static final int TURN_GAP = 4;
	private static final Style MONO = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.withDefaultNamespace("uniform")));
	private static final SystemToast.SystemToastId COPIED = new SystemToast.SystemToastId(2000L);
	/** How often the reply bar re-asks whether the environment can operate. */
	private static final long OPERATE_RECHECK_MS = 1000;

	/** What the conversation's buttons do; the host wires them ({@link ChatScreens#screenActions()}). */
	public interface Actions {
		/** True when {@code envId} can operate (write): the reply bar is ready, no read-only text. */
		boolean canOperate(String envId);

		/** "Enable control": opens {@code OperatePairScreen} for {@code envId}. */
		void enableControl(String envId);

		/** "Pair…": the pairing flow (bridge mode or an environment that is not paired). */
		void pair();
	}

	/**
	 * Replaces the reply bar under the panel (feat/chat-compose: the composer). The panel reserves
	 * {@link #height(int)} px at the bottom of its bounds and forwards render and clicks there.
	 */
	public interface Footer {
		int height(int width);

		void render(GuiGraphicsExtractor g, Font font, Rect r, int mouseX, int mouseY);

		boolean mouseClicked(double mx, double my, int button);
	}

	/** Rows appended after the transcript (the composer's optimistic messages), with a change counter. */
	public interface Extra {
		List<ChatEntry> entries();

		long version();
	}

	/**
	 * What a click on a {@code t3-thread://} link does (feat/chat-links). The T3 screen selects the
	 * thread in place; without one the panel opens the T3 screen on it ({@link #defaultOpenThread}).
	 */
	public interface ThreadLinks {
		/** Opens {@code ref}; {@code row} is its row in the thread list, null when the list does not have it. */
		void open(Links.ThreadRef ref, @Nullable ThreadRow row);
	}

	/** Thread navigation shown as ‹ n/N › key caps in the header (AgentCraft decision queue). */
	public interface Navigation {
		/** "3/12", or empty to hide the control. */
		String position();

		/** -1 previous, +1 next. */
		void step(int dir);
	}

	private final ChatSource source;
	private ChatTheme theme;
	private Rect bounds = Rect.EMPTY;
	private boolean compact;
	private boolean showPrompt = true;
	private boolean showHeader = true;
	private @Nullable Navigation nav;
	private @Nullable Footer footer;
	/** Line after the last transcript row (feat/t3-sidebar-rows: "Working… 4m 12s"); null = none. */
	private @Nullable Trailer trailer;
	private int trailerY = -1;
	private @Nullable Extra extra;
	private long builtExtra = -1;
	private String[] hints = {"PgUp", "scroll", "End", "newest"};
	/** Key-cap hints under the feed (off in the unified T3 screen: the keys still work). */
	private boolean showHints = true;
	/** Minute separators between turns (off in the unified T3 screen, as in T3 Code). */
	private boolean showSeparators = true;
	/**
	 * feat/t3-composer: max width of a centred column holding the feed and the footer (T3 Code's
	 * transcript and composer share one max width); 0 = full width, footer under the panel.
	 */
	private int column;

	private @Nullable ThreadFeed feed;
	private @Nullable String key;
	private String title = "";
	private String envId = "";
	private String envLabel = "";
	private @Nullable EnvBadge badge;
	private @Nullable String message;
	private ChatSource.Unavailable.Action messageAction = ChatSource.Unavailable.Action.NONE;
	private @Nullable Actions actions;
	private boolean canOperate;
	private @Nullable String operateEnv;
	private long operateCheckedAt;
	private final Set<String> expanded = new HashSet<>();
	/** Table layouts by (table, width), least recently used dropped first; cleared when the font changes. */
	private final Map<TableKey, TableLayout.Result> tableCache = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<TableKey, TableLayout.Result> eldest) {
			return size() > 64;
		}
	};
	private @Nullable Font tableFont;

	// thread links (feat/chat-links)
	/** Leading glyph of a thread chip; its place gets the thread's state dot or spinner. */
	static final String CHIP = "●";
	private static final int CHIP_MAX_CHARS = 48;
	private @Nullable ThreadLinks threadLinks;
	/** Where thread links find titles and states; null = {@link ChatScreens#defaultDirectory()}. */
	private @Nullable ThreadDirectory directory;
	/** Rows the url → row memo was built from (the directory hands back the same list while unchanged). */
	private @Nullable List<ThreadRow> linkRows;
	private final Map<String, @Nullable ThreadRow> rowByUrl = new HashMap<>();
	/** Chip labels used by the current layout (thread url → label): relayout when one changes. */
	private Map<String, String> chipLabels = new HashMap<>();
	private Map<String, String> buildingChips = new HashMap<>();
	private @Nullable List<ThreadRow> chipRows;

	// layout cache
	private List<RLine> lines = List.of();
	private List<Box> boxes = List.of();
	private final FeedScroll scroll = new FeedScroll();
	private long builtVersion = -1;
	private int builtWidth = -1;
	private boolean layoutDirty = true;
	private @Nullable String firstKey;
	private boolean lastLoading;
	private @Nullable String lastOlderError;

	// last rectangles (hit-testing)
	private Rect well = Rect.EMPTY;
	private Rect bar = Rect.EMPTY;
	private Rect pairButton = Rect.EMPTY;
	private Rect controlButton = Rect.EMPTY;
	private int prevX = -100;
	private int nextX = -100;
	private int navY;

	/**
	 * A feed line at content y {@code y}. Pieces' x are relative to the text column + {@code indent}.
	 * {@code avatar}: initial drawn in an 8 px square of {@code avatarColor}. {@code icon}: 12 px tool
	 * icon at the text column. {@code bg}: full-width row tint from the indent.
	 */
	private record RLine(int y, int h, int indent, List<TextWrap.Piece> pieces, int color, int stripe, int bg,
			@Nullable String avatar, int avatarColor, @Nullable String icon, @Nullable String toggle, boolean loadMore,
			@Nullable String sep, boolean rule, boolean caret, @Nullable Grid grid) {
	}

	/**
	 * Markdown table slice drawn behind one feed line: {@code edges} are the vertical borders (x
	 * relative to the line's indent), {@code fill} the cell background, {@code lineTop}/{@code lineBottom}
	 * a 1 px rule on the slice's first/last pixel row ({@code strongTop}: outer border or header rule
	 * instead of a faint row separator), {@code tips} the full text of cells cut with "…" (per column).
	 */
	private record Grid(int[] edges, int fill, boolean lineTop, boolean strongTop, boolean lineBottom, @Nullable String @Nullable [] tips) {
		int columnAt(int x) {
			for (int k = 0; k + 1 < edges.length; k++) {
				if (x >= edges[k] && x < edges[k + 1]) {
					return k;
				}
			}
			return -1;
		}
	}

	/** Layout of a table at a width (tables are re-laid out on every streaming update otherwise). */
	private record TableKey(Markdown.Table table, int width) {
	}

	/** A bubble behind lines {@code y0..y1} (content y), from the indent to the right edge. */
	private record Box(int y0, int y1, int indent) {
	}

	public ConversationPanel(ChatSource source, ChatTheme theme) {
		this.source = source;
		this.theme = theme;
	}

	public ConversationPanel(ChatSource source) {
		this(source, ChatTheme.warmStudio());
	}

	// ---- configuration ----------------------------------------------------------------------

	public ChatTheme theme() {
		return theme;
	}

	public void setTheme(ChatTheme t) {
		theme = t;
		layoutDirty = true;
	}

	public Rect bounds() {
		return bounds;
	}

	public void setBounds(Rect r) {
		bounds = r;
	}

	/** Small hosts: one header line, no footer hints. */
	public void setCompact(boolean v) {
		if (compact != v) {
			compact = v;
			layoutDirty = true;
		}
	}

	public boolean compact() {
		return compact;
	}

	/** Shows or hides the (disabled) command bar under the panel. */
	public void setShowPrompt(boolean v) {
		showPrompt = v;
	}

	/**
	 * Shows or hides the header (title, environment, state, ‹ n/N ›): the unified T3 screen draws its
	 * own thread header above the panel (feat/t3-ui-unified).
	 */
	public void setShowHeader(boolean v) {
		if (showHeader != v) {
			showHeader = v;
			layoutDirty = true;
		}
	}

	/**
	 * T3 Code's column: the feed and the footer (composer) centred, at most {@code maxW} wide, the
	 * footer inside the panel under the feed, no inset frame around the feed. 0 turns it off.
	 */
	public void setColumn(int maxW) {
		if (column != maxW) {
			column = Math.max(0, maxW);
			layoutDirty = true;
		}
	}

	/** Puts {@code f} (the composer) where the reply bar was; null brings the bar back. */
	public void setFooter(@Nullable Footer f) {
		footer = f;
	}

	/**
	 * A line drawn after the transcript (and its extra rows), scrolling with it, e.g. the subtle
	 * "Working… 4m 12s" of a running thread. {@link Trailer#height()} 0 hides it.
	 */
	public interface Trailer {
		int height();

		void draw(GuiGraphicsExtractor g, Font font, ChatTheme t, int x, int y, int w);
	}

	public void setTrailer(@Nullable Trailer t) {
		trailer = t;
	}

	/** Extra rows after the transcript (optimistic messages); null for none. */
	public void setExtra(@Nullable Extra e) {
		extra = e;
		layoutDirty = true;
	}

	/** Handler of thread-link clicks; null opens the T3 screen on the thread. */
	public void setThreadLinks(@Nullable ThreadLinks h) {
		threadLinks = h;
	}

	/** Source of the titles and states of linked threads; null = the default directory. */
	public void setDirectory(@Nullable ThreadDirectory d) {
		directory = d;
		linkRows = null;
		chipRows = null;
	}

	public void setNavigation(@Nullable Navigation n) {
		nav = n;
	}

	/** Shows or hides the key-cap hints under the feed (the keys keep working). */
	public void setShowHints(boolean v) {
		showHints = v;
	}

	/** Shows or hides the time separators between turns. */
	public void setShowSeparators(boolean v) {
		if (showSeparators != v) {
			showSeparators = v;
			layoutDirty = true;
		}
	}

	/** Footer key-cap hints as key/verb pairs (the host's keys). */
	public void setHints(String... keyThenVerb) {
		hints = keyThenVerb.clone();
	}

	/** Wires the buttons (pair, enable control) and the operate query; null hides the buttons. */
	public void setActions(@Nullable Actions a) {
		actions = a;
		operateEnv = null;
	}

	/** The reply bar state for the thread shown (see {@link ChatAccess#reply}). */
	public ChatAccess.Reply replyState() {
		boolean shown = key != null && !envId.isEmpty();
		if (shown && actions != null && (!envId.equals(operateEnv) || System.currentTimeMillis() - operateCheckedAt > OPERATE_RECHECK_MS)) {
			operateEnv = envId;
			operateCheckedAt = System.currentTimeMillis();
			canOperate = actions.canOperate(envId);
		}
		return ChatAccess.reply(shown, shown && actions != null && canOperate);
	}

	/** Environment icon for the header (configured colour); null derives it from the id. */
	public void setBadge(@Nullable EnvBadge b) {
		badge = b;
	}

	// ---- thread -------------------------------------------------------------------------------

	/** Key {@code env:threadId} of the thread shown, or null. */
	public @Nullable String key() {
		return key;
	}

	public @Nullable ThreadFeed feed() {
		return feed;
	}

	/**
	 * Shows one thread (no-op when it is already shown). Opens its read-only feed; when the source
	 * cannot (not paired, bridge mode, expired session) shows the reason instead.
	 */
	public void show(String envId, String envLabel, String threadId, String title) {
		String k = envId + ":" + threadId;
		if (k.equals(key) && (feed != null || message != null)) {
			return;
		}
		closeFeed();
		key = k;
		this.envId = envId;
		this.envLabel = envLabel;
		this.title = title;
		message = null;
		messageAction = ChatSource.Unavailable.Action.NONE;
		resetView();
		try {
			ChatSource.Env env = source.env(envId);
			if (envLabel.isBlank()) {
				this.envLabel = env.label();
			}
			// shared, refcounted subscription on the environment's connection (no socket of our own);
			// a cached thread paints its rows at once and reconciles when the snapshot comes
			feed = new ThreadFeed(env, threadId, () -> {
			});
			feed.start();
			ThreadCache.get().noteOpened(k);
		} catch (ChatSource.Unavailable e) {
			message = e.getMessage();
			messageAction = e.action();
		}
	}

	/** Shows a message and no thread (e.g. "Select a thread"). */
	public void showNothing(String text) {
		showNothing(text, ChatSource.Unavailable.Action.NONE);
	}

	/** Like {@link #showNothing(String)}, with a button next to the message (e.g. pair). */
	public void showNothing(String text, ChatSource.Unavailable.Action action) {
		closeFeed();
		messageAction = action;
		key = null;
		title = "";
		envId = "";
		envLabel = "";
		message = text;
		resetView();
	}

	public void reconnect() {
		if (feed != null) {
			feed.reconnect();
		}
	}

	private void resetView() {
		expanded.clear();
		lines = List.of();
		boxes = List.of();
		scroll.update(0, 1);
		scroll.toBottom();
		builtVersion = -1;
		layoutDirty = true;
		firstKey = null;
	}

	private void closeFeed() {
		if (feed != null) {
			feed.close();
			feed = null;
		}
	}

	@Override
	public void close() {
		closeFeed();
	}

	public void tick() {
		ThreadFeed f = feed;
		if (f == null) {
			return;
		}
		if (!f.env().current()) {
			// the connection was replaced (reload, pairing, Enable control): follow the new one
			String e = envId;
			String l = envLabel;
			String th = f.transcript().threadId();
			String ti = title;
			key = null;
			show(e, l, th, ti);
			return;
		}
		if (!chipLabels.isEmpty()) {
			List<ThreadRow> rows = threadRows();
			if (rows != chipRows) {
				chipRows = rows;
				for (Map.Entry<String, String> e : chipLabels.entrySet()) {
					if (!chipLabel(e.getKey()).equals(e.getValue())) {
						layoutDirty = true; // a linked thread got its title (or a new one)
						break;
					}
				}
			}
		}
		ChatTranscript.View v = f.transcript().view();
		ThreadFeed.State st = f.state();
		if (scroll.atTop() && !scroll.following() && v.hasMoreHistory() && !st.loadingOlder() && st.olderError() == null) {
			f.loadOlder(); // reached the top: fetch the previous page by itself
		}
		if (st.loadingOlder() != lastLoading || !Objects.equals(st.olderError(), lastOlderError)) {
			lastLoading = st.loadingOlder();
			lastOlderError = st.olderError();
			layoutDirty = true;
		}
	}

	// ---- layout -------------------------------------------------------------------------------

	/** Line builder with a running content y. */
	private final class Layout {
		final Font font;
		final int w;
		final List<RLine> out = new ArrayList<>();
		final List<Box> bx = new ArrayList<>();
		int y;

		Layout(Font font, int w) {
			this.font = font;
			this.w = w;
		}

		void add(int h, int indent, List<TextWrap.Piece> pieces, int color, int stripe, int bg, @Nullable String avatar, int avatarColor,
				@Nullable String icon, @Nullable String toggle, boolean loadMore, @Nullable String sep, boolean rule, boolean caret) {
			out.add(new RLine(y, h, indent, pieces, color, stripe, bg, avatar, avatarColor, icon, toggle, loadMore, sep, rule, caret, null));
			y += h;
		}

		void grid(int h, int indent, List<TextWrap.Piece> pieces, int color, int stripe, Grid grid) {
			out.add(new RLine(y, h, indent, pieces, color, stripe, 0, null, 0, null, null, false, null, false, false, grid));
			y += h;
		}

		void text(int indent, List<TextWrap.Piece> pieces, int color, int stripe) {
			add(theme.row(), indent, pieces, color, stripe, 0, null, 0, null, null, false, null, false, false);
		}

		void gap(int h, int stripe) {
			add(h, 0, List.of(), 0, stripe, 0, null, 0, null, null, false, null, false, false);
		}
	}

	private void relayoutIfNeeded(Font font, int w) {
		ThreadFeed f = feed;
		if (f == null) {
			return;
		}
		ChatTranscript.View v = f.transcript().view();
		long extraVersion = extra == null ? -1 : extra.version();
		if (!layoutDirty && v.version() == builtVersion && w == builtWidth && extraVersion == builtExtra) {
			return;
		}
		builtExtra = extraVersion;
		ChatTheme t = theme;
		int oldHeight = lines.isEmpty() ? 0 : lines.getLast().y() + lines.getLast().h();
		String oldFirst = firstKey;
		Layout L = new Layout(font, w);
		ThreadFeed.State st = f.state();
		if (v.hasMoreHistory() || st.olderError() != null) {
			String label = st.loadingOlder() ? "Loading earlier messages…"
					: st.olderError() != null ? "⚠ " + st.olderError() + " (click to retry)" : "▲ Load earlier messages";
			L.add(t.row() + 4, 0, List.of(piece(font, label)), t.link(), 0, 0, null, 0, null, null, true, null, false, false);
		} else if (v.synced() || !v.entries().isEmpty()) {
			L.add(t.row() + 2, 0, List.of(), 0, 0, 0, null, 0, null, null, false, "Start of thread", false, false);
		}
		Long prevAt = null;
		long now = System.currentTimeMillis();
		buildingChips = new HashMap<>();
		List<ChatEntry> all = v.entries();
		if (extra != null) {
			List<ChatEntry> more = extra.entries();
			if (!more.isEmpty()) {
				all = new ArrayList<>(all);
				all.addAll(more);
			}
		}
		for (Turn turn : ChatTurns.group(all)) {
			String sep = FeedRules.separator(prevAt, turn.at(), ZoneId.systemDefault(), now);
			if (sep != null && showSeparators) {
				L.add(t.row() + 2, 0, List.of(), 0, 0, 0, null, 0, null, null, false, sep, false, false);
			}
			if (turn.at() != null) {
				prevAt = turn.at();
			}
			layoutTurn(L, turn);
		}
		if (v.status().state() == ThreadState.WORKING) {
			L.add(t.row(), 0, List.of(), 0, t.agent(), 0, null, 0, null, null, false, null, false, true);
		}
		lines = L.out;
		boxes = L.bx;
		chipLabels = buildingChips;
		chipRows = threadRows();
		int newHeight = L.y;
		firstKey = v.entries().isEmpty() ? null : v.entries().getFirst().key();
		if (oldFirst != null && firstKey != null && !oldFirst.equals(firstKey)) {
			scroll.update(oldHeight, Math.max(1, scroll.view()));
			scroll.prepended(newHeight - oldHeight); // older page prepended: keep the view in place
		}
		builtVersion = v.version();
		builtWidth = w;
		layoutDirty = false;
	}

	private static TextWrap.Piece piece(Font font, String s) {
		return new TextWrap.Piece(Span.plain(s), 0, font.width(s));
	}

	private void layoutTurn(Layout L, Turn turn) {
		ChatTheme t = theme;
		switch (turn.role()) {
			case NOTICE -> {
				ChatEntry e = ((Message) turn.blocks().getFirst()).entry();
				String s = (e.title().isBlank() ? "" : e.title() + ": ") + ToolLine.firstLine(e.body());
				for (TextWrap.Line l : TextWrap.wrap(List.of(Span.plain(s)), L.w, sp -> measure(L.font, sp))) {
					L.text(0, l.pieces(), t.muted(), 0);
				}
			}
			case USER -> {
				ChatEntry e = ((Message) turn.blocks().getFirst()).entry();
				// feat/t3-composer: T3's intent marker over a message that steered (or was queued behind) the turn
				if (ChatEntry.STEER.equals(e.title()) || ChatEntry.QUEUED.equals(e.title())) {
					String mark = ChatEntry.STEER.equals(e.title()) ? "↪ Steer" : "⏱ Queued";
					L.text(0, List.of(piece(L.font, mark)), t.muted(), 0);
				}
				lead(L, "You", t.youText(), "Y", t.you(), t.you(), e.inherited() ? "from parent thread" : null);
				body(L, e, t.text(), t.you());
			}
			case AGENT -> {
				boolean first = true;
				for (Block b : turn.blocks()) {
					int stripe = stripeOf(b);
					if (first) {
						String extra = b instanceof Message m && m.entry().inherited() ? "from parent thread" : null;
						lead(L, "Agent", t.agentText(), "A", t.agent(), stripe, extra);
						first = false;
					}
					switch (b) {
						case Message m -> message(L, m.entry(), stripe);
						case ToolRun r -> toolRun(L, r, stripe);
					}
				}
			}
		}
		L.gap(TURN_GAP, 0);
	}

	private int stripeOf(Block b) {
		ChatTheme t = theme;
		if (b instanceof Message m) {
			return switch (m.entry().kind()) {
				case REQUEST -> t.needsYou();
				case ERROR -> t.error();
				default -> t.agent();
			};
		}
		return t.agent();
	}

	/** Speaker row: avatar + name (+ muted extra). */
	private void lead(Layout L, String name, int nameColor, String avatar, int avatarColor, int stripe, @Nullable String extra) {
		List<TextWrap.Piece> p = new ArrayList<>();
		Span s = new Span(name, false, false, false, null);
		p.add(new TextWrap.Piece(s, 0, L.font.width(name)));
		L.add(theme.row(), 0, p, nameColor, stripe, 0, avatar, avatarColor, null, null, false, null, false, false);
		if (extra != null) {
			L.text(0, List.of(piece(L.font, extra)), theme.muted(), stripe);
		}
	}

	private void message(Layout L, ChatEntry e, int stripe) {
		ChatTheme t = theme;
		switch (e.kind()) {
			case PLAN, TODO -> {
				L.text(0, List.of(piece(L.font, e.kind().role + (e.title().isBlank() ? "" : " · " + e.title()))), t.brass(), stripe);
				body(L, e, t.text(), stripe);
			}
			case REQUEST -> {
				// "needs you" callout in AgentCraft's speech bubble
				int y0 = L.y;
				L.gap(3, stripe);
				String head = (e.title().isBlank() ? "Needs you" : e.title()) + (e.streaming() ? "" : "");
				L.text(6, List.of(new TextWrap.Piece(new Span(head, true, false, false, null), 0, measure(L.font, new Span(head, true, false, false, null)))),
						t.youText(), stripe);
				bodyAt(L, e, t.text(), stripe, 6, L.w - 12);
				L.gap(4, stripe);
				L.bx.add(new Box(y0, L.y, 0));
				L.gap(2, stripe);
			}
			case ERROR -> {
				L.text(0, List.of(piece(L.font, "Error" + (e.title().isBlank() ? "" : " · " + e.title()))), t.errorText(), stripe);
				body(L, e, t.errorText(), stripe);
			}
			default -> body(L, e, t.text(), stripe);
		}
	}

	private void body(Layout L, ChatEntry e, int color, int stripe) {
		bodyAt(L, e, color, stripe, 0, L.w);
	}

	private void bodyAt(Layout L, ChatEntry e, int color, int stripe, int indent, int w) {
		if (!e.body().isBlank()) {
			if (e.markdown()) {
				markdown(L, Markdown.parse(e.body()), indent, w, color, stripe);
			} else {
				wrapped(L, List.of(Span.plain(e.body())), indent, w, color, stripe);
			}
		}
		if (e.detail() != null && !e.detail().isBlank() && !ChatTurns.toolLike(e.kind())) {
			code(L, List.of(e.detail().split("\n", -1)), indent, w, stripe, false);
		}
	}

	/** Collapsed run: one line; unfolded: one monitor tool line per step, each foldable. */
	private void toolRun(Layout L, ToolRun r, int stripe) {
		ChatTheme t = theme;
		boolean open = expanded.contains(r.key());
		ToolLine firstTool = ToolLine.of(r.entries().getFirst());
		if (r.entries().size() > 1 || !open) {
			String head = (open ? "▾ " : "▸ ") + r.summary(3) + (r.running() ? "  …" : "") + (r.failed() ? "  ✖" : "");
			int color = r.failed() ? t.errorText() : t.tool();
			L.add(t.toolRow(), 15, List.of(piece(L.font, head)), color, stripe, 0, null, 0, firstTool.icon(), r.key(), false, null, false,
					false);
			if (!open) {
				return;
			}
		}
		for (ChatEntry e : r.entries()) {
			toolLine(L, e, stripe, r.entries().size() > 1 ? 8 : 0);
		}
	}

	private void toolLine(Layout L, ChatEntry e, int stripe, int indent) {
		ChatTheme t = theme;
		ToolLine tl = ToolLine.of(e);
		boolean open = expanded.contains(e.key());
		int avail = L.w - indent - 15;
		String name = tl.name().contains("_") && !tl.arg().isEmpty() && L.font.width(tl.name()) > avail / 2 ? ToolLine.shortName(tl.name()) : tl.name();
		name = ChatDraw.clip(L.font, name, avail);
		List<TextWrap.Piece> p = new ArrayList<>();
		int nw = L.font.width(name);
		boolean failed = "failed".equals(e.status());
		p.add(new TextWrap.Piece(Span.plain(name), 0, nw));
		String suffix = (e.streaming() || "running".equals(e.status()) || "pending".equals(e.status())) ? " …" : failed ? " ✖" : "";
		if (!tl.arg().isEmpty() && avail - nw > 12) {
			String a = ChatDraw.clip(L.font, " " + tl.arg(), avail - nw - L.font.width(suffix));
			p.add(new TextWrap.Piece(new Span(a, false, true, false, null), nw, L.font.width(a)));
			nw += L.font.width(a);
		}
		if (!suffix.isEmpty()) {
			p.add(new TextWrap.Piece(Span.plain(suffix), nw, L.font.width(suffix)));
		}
		boolean hasDetail = e.detail() != null && !e.detail().isBlank() || e.kind() == Kind.REASONING && !e.body().isBlank();
		L.add(t.toolRow(), indent + 15, p, failed ? t.errorText() : t.tool(), stripe, 0, null, 0, tl.icon(), hasDetail ? e.key() : null,
				false, null, false, false);
		if (open && hasDetail) {
			if (e.kind() == Kind.REASONING) {
				markdown(L, Markdown.parse(e.body()), indent + 15, L.w - indent - 15, t.muted(), stripe);
			} else {
				code(L, List.of(e.detail().split("\n", -1)), indent + 15, L.w - indent - 15, stripe, failed);
			}
			L.gap(2, stripe);
		}
	}

	private void markdown(Layout L, List<Markdown.Block> blocks, int indent, int w, int color, int stripe) {
		ChatTheme t = theme;
		Markdown.Block prev = null;
		for (Markdown.Block b : blocks) {
			if (prev != null && !(b instanceof Markdown.ListItem && prev instanceof Markdown.ListItem)) {
				L.gap(3, stripe);
			}
			switch (b) {
				case Markdown.Heading hd -> {
					int c = hd.level() <= 2 ? t.text() : t.muted();
					List<Span> spans = decorate(hd.spans()).stream().map(s -> new Span(s.text(), true, s.italic(), s.code(), s.url())).toList();
					wrapped(L, spans, indent, w, c, stripe);
				}
				case Markdown.Paragraph p -> wrapped(L, decorate(p.spans()), indent, w, color, stripe);
				case Markdown.ListItem li -> {
					int ind = indent + li.depth() * 10;
					String marker = li.marker() + " ";
					int mw = L.font.width(marker);
					List<TextWrap.Line> wr = TextWrap.wrap(decorate(li.spans()), w - (ind - indent) - mw, s -> measure(L.font, s));
					for (int i = 0; i < wr.size(); i++) {
						List<TextWrap.Piece> pieces = new ArrayList<>();
						if (i == 0) {
							pieces.add(new TextWrap.Piece(Span.plain(marker), 0, mw));
						}
						for (TextWrap.Piece pc : wr.get(i).pieces()) {
							pieces.add(new TextWrap.Piece(pc.span(), pc.x() + mw, pc.width()));
						}
						L.text(ind, pieces, color, stripe);
					}
				}
				case Markdown.CodeBlock cb -> code(L, cb.lines(), indent, w, stripe, false);
				case Markdown.Quote q -> {
					List<Span> spans = new ArrayList<>();
					spans.add(Span.plain("│ "));
					decorate(q.spans()).forEach(s -> spans.add(new Span(s.text(), s.bold(), true, s.code(), s.url())));
					wrapped(L, spans, indent, w, t.muted(), stripe);
				}
				case Markdown.Rule r -> L.add(7, indent, List.of(), color, stripe, 0, null, 0, null, null, false, null, true, false);
				case Markdown.Table tb -> table(L, decorate(tb), indent, w, color, stripe);
			}
			prev = b;
		}
	}

	/**
	 * Monospaced block. Diff lines get AgentCraft's tinted rows (+ add, - del, @@ hunk); output of a
	 * failed step is drawn in the error colour.
	 */
	private void code(Layout L, List<String> codeLines, int indent, int w, int stripe, boolean failed) {
		ChatTheme t = theme;
		boolean diff = codeLines.stream().anyMatch(s -> s.startsWith("@@") || s.startsWith("+++ ") || s.startsWith("--- "));
		L.add(2, indent, List.of(), 0, stripe, t.codeBg(), null, 0, null, null, false, null, false, false);
		for (String raw : codeLines) {
			int color = failed ? t.delFg() : t.codeText();
			int bg = t.codeBg();
			if (diff && raw.startsWith("+") && !raw.startsWith("+++")) {
				color = t.addFg();
				bg = t.addBg();
			} else if (diff && raw.startsWith("-") && !raw.startsWith("---")) {
				color = t.delFg();
				bg = t.delBg();
			} else if (diff && raw.startsWith("@@")) {
				color = t.link();
			}
			List<Span> spans = List.of(new Span(raw.isEmpty() ? " " : raw.replace("\t", "  "), false, false, true, null));
			for (TextWrap.Line l : TextWrap.wrap(spans, w - 8, s -> measure(L.font, s))) {
				List<TextWrap.Piece> shifted = new ArrayList<>();
				l.pieces().forEach(p -> shifted.add(new TextWrap.Piece(p.span(), p.x() + 4, p.width())));
				L.add(theme.row(), indent, shifted, color, stripe, bg, null, 0, null, null, false, null, false, false);
			}
		}
		L.add(2, indent, List.of(), 0, stripe, t.codeBg(), null, 0, null, null, false, null, false, false);
	}

	/**
	 * GFM table as T3 Code draws it: 1 px border, bold header on a lighter fill, faint rules between
	 * rows and columns, 4 px cell padding. Every text line of a row is one feed line (so scrolling and
	 * hit-testing measure it like any text); a 3 px slice above each row carries its top rule and
	 * padding, a 2 px slice (3 px with the bottom border) closes it.
	 */
	private void table(Layout L, Markdown.Table tb, int indent, int w, int color, int stripe) {
		ChatTheme t = theme;
		TableLayout.Result r = tableLayout(L.font, tb, w);
		tableRow(L, r.header(), r.edges(), indent, t.panelHi(), t.text(), stripe, true, true, r.rows().isEmpty());
		for (int i = 0; i < r.rows().size(); i++) {
			tableRow(L, r.rows().get(i), r.edges(), indent, t.panel(), color, stripe, i == 0, false, i == r.rows().size() - 1);
		}
	}

	private void tableRow(Layout L, TableLayout.Row row, int[] edges, int indent, int fill, int color, int stripe, boolean strongTop,
			boolean header, boolean last) {
		@Nullable String @Nullable [] tips = null;
		for (int k = 0; k < row.cells().size(); k++) {
			String c = row.cells().get(k).clipped();
			if (c != null) {
				if (tips == null) {
					tips = new String[edges.length - 1];
				}
				tips[k] = c;
			}
		}
		L.grid(3, indent, List.of(), color, stripe, new Grid(edges, fill, true, strongTop || header, false, tips));
		for (int i = 0; i < row.lines(); i++) {
			List<TextWrap.Piece> pieces = new ArrayList<>();
			for (TableLayout.Cell cell : row.cells()) {
				if (i < cell.lines().size()) {
					pieces.addAll(cell.lines().get(i).pieces());
				}
			}
			L.grid(theme.row(), indent, pieces, color, stripe, new Grid(edges, fill, false, false, false, tips));
		}
		L.grid(last ? 3 : 2, indent, List.of(), color, stripe, new Grid(edges, fill, false, false, last, tips));
	}

	private TableLayout.Result tableLayout(Font font, Markdown.Table tb, int w) {
		if (font != tableFont) {
			tableCache.clear();
			tableFont = font;
		}
		return tableCache.computeIfAbsent(new TableKey(tb, w), k -> TableLayout.layout(k.table(), k.width(), s -> measure(font, s)));
	}

	private void wrapped(Layout L, List<Span> spans, int indent, int w, int color, int stripe) {
		for (TextWrap.Line l : TextWrap.wrap(spans, w, s -> measure(L.font, s))) {
			L.text(indent, l.pieces(), color, stripe);
		}
	}

	// ---- links (feat/chat-links) --------------------------------------------------------------------

	private List<ThreadRow> threadRows() {
		try {
			ThreadDirectory d = directory;
			if (d == null) {
				directory = d = ChatScreens.defaultDirectory();
			}
			return d.rows();
		} catch (RuntimeException | LinkageError e) {
			return List.of(); // headless renders: no client, no list
		}
	}

	/** Row of a thread link, memoized per directory list. */
	private @Nullable ThreadRow rowFor(String url) {
		List<ThreadRow> rows = threadRows();
		if (rows != linkRows) {
			linkRows = rows;
			rowByUrl.clear();
		}
		if (rowByUrl.containsKey(url)) {
			return rowByUrl.get(url);
		}
		Links.ThreadRef ref = Links.thread(url);
		ThreadRow r = ref == null ? null : Links.find(rows, ref);
		rowByUrl.put(url, r);
		return r;
	}

	/** Chip text of a thread link: the thread's title, else "thread 1a2b3c4d". */
	private String chipLabel(String url) {
		Links.ThreadRef ref = Links.thread(url);
		if (ref == null) {
			return url;
		}
		ThreadRow r = rowFor(url);
		String label = r != null && !r.title().isBlank() ? r.title().strip() : I18n.get("t3craft.chat.link.thread", Links.shortId(ref.threadId()));
		return label.length() > CHIP_MAX_CHARS ? label.substring(0, CHIP_MAX_CHARS - 1).strip() + "…" : label;
	}

	/**
	 * Thread links become chips: the raw url (autolink, bare) is replaced by the thread's title, a
	 * labelled link keeps its label; both start with {@link #CHIP}, where the state is drawn.
	 */
	private List<Span> decorate(List<Span> spans) {
		boolean any = false;
		for (Span s : spans) {
			if (Links.kind(s.url()) == Links.Kind.THREAD) {
				any = true;
				break;
			}
		}
		if (!any) {
			return spans;
		}
		List<Span> out = new ArrayList<>(spans.size());
		String prev = null;
		for (Span s : spans) {
			String u = s.url();
			if (u != null && Links.kind(u) == Links.Kind.THREAD && !u.equals(prev)) {
				String label = chipLabel(u);
				buildingChips.put(u, label);
				String text = s.text().equals(u) ? label : s.text();
				out.add(s.withText(CHIP + " " + text));
			} else {
				out.add(s);
			}
			prev = u;
		}
		return out;
	}

	private Markdown.Table decorate(Markdown.Table tb) {
		boolean any = tb.header().stream().anyMatch(c -> c.stream().anyMatch(s -> Links.kind(s.url()) == Links.Kind.THREAD))
				|| tb.rows().stream().anyMatch(r -> r.stream().anyMatch(c -> c.stream().anyMatch(s -> Links.kind(s.url()) == Links.Kind.THREAD)));
		if (!any) {
			return tb;
		}
		List<List<Span>> header = new ArrayList<>();
		tb.header().forEach(c -> header.add(List.copyOf(decorate(c))));
		List<List<List<Span>>> rows = new ArrayList<>();
		for (List<List<Span>> r : tb.rows()) {
			List<List<Span>> cells = new ArrayList<>();
			r.forEach(c -> cells.add(List.copyOf(decorate(c))));
			rows.add(List.copyOf(cells));
		}
		return new Markdown.Table(tb.aligns(), List.copyOf(header), List.copyOf(rows));
	}

	/** State of a linked thread in a {@code w} px slot: spinner while it runs, else the sidebar's dot. */
	private static void drawThreadState(GuiGraphicsExtractor g, ChatTheme t, @Nullable ThreadRow r, int x, int y, int w) {
		int dx = x + Math.max(0, (w - 7) / 2);
		if (r == null) {
			ChatDraw.dot(g, t, "idle", ChatTheme.withAlpha(t.muted(), 160), dx, y);
		} else if (r.state() == ThreadState.WORKING && r.attention() != Attention.NEEDS_YOU) {
			Spinner.draw(g, x + (w - Spinner.SIZE) / 2, y, t.working());
		} else if (r.needsAttention()) {
			ChatDraw.pulsingDot(g, t, ChatTheme.family(r.state()), t.color(r.state()), dx, y);
		} else {
			ChatDraw.dot(g, t, ChatTheme.family(r.state()), t.color(r.state()), dx, y);
		}
	}

	private Component linkTooltip(String url) {
		return switch (Links.kind(url)) {
			case THREAD -> {
				Links.ThreadRef ref = Links.thread(url);
				ThreadRow r = rowFor(url);
				if (ref == null) {
					yield Component.literal(url);
				}
				if (r == null) {
					yield Component.literal(I18n.get("t3craft.chat.link.thread_unknown_tip", Links.shortId(ref.threadId())));
				}
				String env = r.envLabel().isBlank() ? r.env() : r.envLabel();
				String extra = (r.project().isBlank() ? "" : " · " + r.project()) + (env.isBlank() ? "" : " · " + env) + " · " + r.state().word;
				yield Component.literal(I18n.get("t3craft.chat.link.thread_tip", chipLabel(url)) + extra);
			}
			case WEB -> Component.literal(I18n.get("t3craft.chat.link.web_tip", url));
			case FILE -> Component.literal(I18n.get("t3craft.chat.link.file_tip", Links.filePath(url)));
			case OTHER -> Component.literal(I18n.get("t3craft.chat.link.file_tip", url));
		};
	}

	/** Click on a link: thread → open it, web → browser (vanilla confirmation), file → copy the path. */
	private void openLink(String url) {
		Minecraft mc = Minecraft.getInstance();
		switch (Links.kind(url)) {
			case THREAD -> {
				Links.ThreadRef ref = Links.thread(url);
				if (ref == null) {
					copyLink(mc, url);
					return;
				}
				ThreadRow row = Links.find(threadRows(), ref);
				ThreadLinks h = threadLinks;
				if (h != null) {
					h.open(ref, row);
				} else {
					defaultOpenThread(directory, ref, row);
				}
			}
			case WEB -> openWeb(mc, url);
			case FILE -> {
				String path = Links.filePath(url);
				mc.keyboardHandler.setClipboard(path);
				toast(mc, I18n.get("t3craft.chat.link.path_copied"), path);
			}
			case OTHER -> copyLink(mc, url);
		}
	}

	/** As a chat link: off in the chat settings → copied; "prompt on links" → vanilla confirmation. */
	private static void openWeb(Minecraft mc, String url) {
		URI uri;
		try {
			uri = new URI(url);
		} catch (URISyntaxException e) {
			copyLink(mc, url);
			return;
		}
		if (!mc.options.chatLinks().get()) {
			copyLink(mc, url);
			return;
		}
		if (mc.options.chatLinksPrompt().get()) {
			Screen back = mc.gui.screen();
			mc.gui.setScreen(new ConfirmLinkScreen(open -> {
				if (open) {
					Blaze3D.openUri(uri);
				}
				mc.gui.setScreen(back);
			}, uri, false));
		} else {
			Blaze3D.openUri(uri);
		}
	}

	private static void copyLink(Minecraft mc, String url) {
		mc.keyboardHandler.setClipboard(url);
		toast(mc, I18n.get("t3craft.chat.link.link_copied"), url);
	}

	private static void toast(Minecraft mc, String title, String detail) {
		SystemToast.addOrUpdate(mc.gui.toastManager(), COPIED, Component.literal(title),
				Component.literal(detail.length() > 60 ? detail.substring(0, 59) + "…" : detail));
	}

	/** Without a {@link ThreadLinks} host: the T3 screen opened on the thread. */
	public static void defaultOpenThread(@Nullable ThreadDirectory dir, Links.ThreadRef ref, @Nullable ThreadRow row) {
		if (row != null && row.threadId() != null) {
			ChatScreens.open(row.env(), row.threadId());
			return;
		}
		String env = unlistedEnv(dir, ref);
		if (env != null) {
			ChatScreens.open(env, ref.threadId());
		}
	}

	/**
	 * Environment to ask for a thread the list does not have (archived, not loaded yet): the link's own
	 * environment when it is one of ours, the only environment when there is just one (links name T3's
	 * environment id, not necessarily this mod's slug for it). Otherwise null after a toast: the thread
	 * belongs to an environment that is not paired here, or there is none.
	 */
	public static @Nullable String unlistedEnv(@Nullable ThreadDirectory dir, Links.ThreadRef ref) {
		Map<String, ThreadList.EnvInfo> envs = Map.of();
		try {
			if (dir != null) {
				envs = dir.environments();
			}
		} catch (RuntimeException e) {
			envs = Map.of();
		}
		if (!ref.envId().isEmpty() && envs.containsKey(ref.envId())) {
			return ref.envId();
		}
		if (envs.size() == 1) {
			return envs.keySet().iterator().next();
		}
		Minecraft mc = Minecraft.getInstance();
		if (envs.isEmpty() || ref.envId().isEmpty()) {
			toast(mc, I18n.get("t3craft.chat.link.thread_missing"), I18n.get("t3craft.chat.link.thread_missing_detail", Links.shortId(ref.threadId())));
		} else {
			toast(mc, I18n.get("t3craft.chat.link.thread_other_env"), I18n.get("t3craft.chat.link.thread_other_env_detail", ref.envId()));
		}
		return null;
	}

	private static int measure(Font font, Span s) {
		return font.width(component(s, 0xFFFFFFFF, null));
	}

	private static MutableComponent component(Span s, int color, @Nullable ChatTheme t) {
		return component(s, color, t, false);
	}

	/** Links in the link colour, underlined when {@code underline} (the hovered link). */
	private static MutableComponent component(Span s, int color, @Nullable ChatTheme t, boolean underline) {
		Style style = s.code() ? MONO : Style.EMPTY;
		style = style.withBold(s.bold()).withItalic(s.italic());
		int c = color;
		if (t != null && s.url() != null) {
			style = style.withUnderlined(underline);
			c = t.link();
		} else if (t != null && s.code() && color != t.codeText() && color != t.addFg() && color != t.delFg() && color != t.link()) {
			c = t.inlineCode();
		}
		return Component.literal(s.text()).withStyle(style.withColor(c & 0xFFFFFF));
	}

	// ---- input --------------------------------------------------------------------------------

	public boolean contains(double mx, double my) {
		return bounds.contains(mx, my);
	}

	/** Wheel: 3 rows per notch (AgentCraft console). */
	public boolean mouseScrolled(double mx, double my, double dy) {
		if (!contains(mx, my)) {
			return false;
		}
		scroll.scrollBy(dy > 0 ? -3 * theme.row() : 3 * theme.row());
		return true;
	}

	public void scrollToEnd() {
		scroll.toBottom();
	}

	/** True while the feed follows the newest line (a thread just opened starts there). */
	public boolean followingNewest() {
		return scroll.following();
	}

	/** PgUp/PgDn page the feed, Home/End jump. Arrows and Tab are left to the host. */
	public boolean keyPressed(KeyEvent event) {
		int page = Math.max(theme.row(), well.h() - 6 - theme.row());
		switch (event.key()) {
			case InputConstants.KEY_PAGEUP -> scroll.scrollBy(-page);
			case InputConstants.KEY_PAGEDOWN -> scroll.scrollBy(page);
			case InputConstants.KEY_HOME -> scroll.toTop();
			case InputConstants.KEY_END -> scroll.toBottom();
			default -> {
				return false;
			}
		}
		return true;
	}

	private record Hit(RLine line, @Nullable String url) {
	}

	private int feedTop() {
		return well.y() + 4 - scroll.offset();
	}

	private @Nullable Hit hitTest(int mx, int my) {
		if (!well.contains(mx, my) || mx >= well.right() - 9) {
			return null;
		}
		int top = feedTop();
		for (RLine l : lines) {
			int y = top + l.y();
			if (my >= y && my < y + l.h()) {
				int rel = mx - well.x() - TEXT_X - l.indent();
				String url = null;
				for (TextWrap.Piece p : l.pieces()) {
					if (p.span().url() != null && rel >= p.x() && rel < p.x() + p.width()) {
						url = p.span().url();
					}
				}
				return new Hit(l, url);
			}
		}
		return null;
	}

	/** Click: header ‹ ›, open a link, load earlier messages, fold/unfold a tool run or step. */
	public boolean mouseClicked(double mx, double my, int button) {
		if (!MouseButtons.isLeft(button) || !contains(mx, my)) {
			return false;
		}
		if (nav != null && my >= navY && my < navY + 12) {
			if (mx >= prevX && mx < prevX + 12) {
				nav.step(-1);
				return true;
			}
			if (mx >= nextX && mx < nextX + 12) {
				nav.step(1);
				return true;
			}
		}
		if (actions != null && pairButton.contains(mx, my)) {
			actions.pair();
			return true;
		}
		if (actions != null && controlButton.contains(mx, my) && !envId.isEmpty()) {
			actions.enableControl(envId);
			return true;
		}
		if (footer != null && showPrompt && bar.contains(mx, my)) {
			footer.mouseClicked(mx, my, button);
			return true;
		}
		Hit hit = hitTest((int) mx, (int) my);
		if (hit == null) {
			return bar.contains(mx, my);
		}
		if (hit.url() != null) {
			openLink(hit.url());
			return true;
		}
		if (hit.line().loadMore() && feed != null) {
			feed.loadOlder();
			return true;
		}
		if (hit.line().toggle() != null) {
			if (!expanded.remove(hit.line().toggle())) {
				expanded.add(hit.line().toggle());
			}
			scroll.hold();
			layoutDirty = true;
			return true;
		}
		return false;
	}

	// ---- rendering ----------------------------------------------------------------------------

	public void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
		ChatTheme t = theme;
		if (bounds.w() < 60 || bounds.h() < 50) {
			return;
		}
		boolean col = column > 0 && showPrompt && footer != null;
		int colW = col ? Math.min(bounds.w() - 2 * t.pad(), column) : bounds.w();
		int fieldH = !showPrompt ? 0 : footer != null ? footer.height(colW) : FIELD_H;
		Rect panel = col ? bounds : new Rect(bounds.x(), bounds.y(), bounds.w(), bounds.h() - (showPrompt ? fieldH + 5 : 0));
		ChatDraw.panel(g, t, panel);
		int pad = t.pad();
		int x = panel.x() + pad;
		int w = panel.w() - 2 * pad;
		int y = panel.y() + pad;
		if (col) {
			x += (w - colW) / 2;
			w = colW;
		}
		int barY = panel.bottom() - pad - fieldH;

		ChatTranscript.Status status = null;
		ThreadFeed.State st = null;
		String shownTitle = title;
		if (feed != null) {
			ChatTranscript.View v = feed.transcript().view();
			if (!v.title().isBlank()) {
				shownTitle = v.title();
			}
			status = feed.transcript().hasSnapshot() ? v.status() : null;
			st = feed.state();
		}

		prevX = nextX = -100;
		if (showHeader) {
			// ---- header: [env icon] title · sub ............ ‹ 3/12 ›
			int right = x + w;
			prevX = nextX = -100;
			String pos = nav == null ? "" : nav.position();
			navY = y;
			if (!pos.isEmpty()) {
				nextX = right - 12;
				ChatDraw.keycap(g, font, t, "›", nextX, y);
				int pw = font.width(pos) + 10;
				int pillX = nextX - 3 - pw;
				ChatDraw.pill(g, font, t, pos, pillX, y, t.text());
				prevX = pillX - 15;
				ChatDraw.keycap(g, font, t, "‹", prevX, y);
				right = prevX - 6;
			}
			int hx = x;
			if (!envId.isEmpty()) {
				EnvBadge b = badge != null ? badge : EnvBadge.of(envId, envLabel);
				ChatDraw.envIcon(g, font, b, hx, y + 1, 10);
				hx += 14;
			}
			if (status != null) {
				String fam = ChatTheme.family(status.state());
				if (status.state() == ThreadState.NEEDS_ATTENTION) {
					ChatDraw.pulsingDot(g, t, fam, t.color(status.state()), right - 7, y + 2);
				} else {
					ChatDraw.dot(g, t, fam, t.color(status.state()), right - 7, y + 2);
				}
				right -= 12;
			}
			String tt = shownTitle.isBlank() ? (key == null ? "Conversation" : "Thread") : shownTitle;
			String sub = subtitle(status, st);
			int titleW = Math.min(font.width(tt), right - hx);
			g.text(font, ChatDraw.clip(font, tt, right - hx), hx, y + 2, t.text(), t.shadow());
			int subX = hx + titleW + 6;
			if (!compact && right - subX > 30) {
				g.text(font, ChatDraw.clip(font, sub, right - subX), subX, y + 2, t.muted(), t.shadow());
			}
			if (status != null && mouseY >= y && mouseY < y + 12 && mouseX >= right && mouseX < right + 12) {
				g.setTooltipForNextFrame(font, Component.literal(status.state().word + (status.detail().isBlank() ? "" : " · " + status.detail())),
						mouseX, mouseY);
			}
			if (!compact) {
				// second line: state detail and connection
				String detail = status == null ? "" : status.state().word + (status.detail().isBlank() || status.detail().equals(status.state().word)
						? "" : " · " + status.detail());
				String conn = connection(st);
				int cw = font.width(conn);
				int ly = y + 13;
				g.text(font, ChatDraw.clip(font, detail, w - cw - 8), x, ly, status == null ? t.muted() : t.color(status.state()), t.shadow());
				int connColor = st == null ? t.muted() : st.phase() == ThreadFeed.Phase.LIVE ? t.done()
						: st.phase() == ThreadFeed.Phase.FAILED ? t.error() : t.pending();
				g.text(font, conn, x + w - cw, ly, connColor, t.shadow());
				y += HEADER_H + 4;
			} else {
				y += COMPACT_HEADER_H;
			}
			g.fill(x, y, x + w, y + 1, t.edge());
			y += 4;
		}

		// ---- who needs it (AgentCraft speech bubble)
		if (status != null && status.needs() != null) {
			Rect nb = new Rect(x, y, w, 16);
			ChatDraw.bubble(g, t, nb, ChatTheme.withAlpha(t.needsYou(), 60));
			ChatDraw.pulsingDot(g, t, "waiting", t.needsYou(), x + 6, y + 4);
			g.text(font, ChatDraw.clip(font, status.needs(), w - 24), x + 17, y + 4, t.youText(), t.shadow());
			y += 19;
		}

		// ---- feed well
		boolean hintsShown = !compact && showHints;
		int footerH = hintsShown ? FOOTER_H : 0;
		well = new Rect(x, y, w, Math.max(0, (col ? barY - 4 : panel.bottom() - pad - footerH - (hintsShown ? 3 : 0)) - y));
		if (!col) {
			ChatDraw.inset(g, t, well);
		}
		int textW = well.w() - TEXT_X - 12;
		relayoutIfNeeded(font, textW);
		int contentH = lines.isEmpty() ? 0 : lines.getLast().y() + lines.getLast().h();
		int trailerH = trailer == null || feed == null || message != null ? 0 : trailer.height();
		trailerY = trailerH > 0 ? contentH + 3 : -1;
		scroll.update(contentH + (trailerH > 0 ? trailerH + 3 : 0), Math.max(1, well.h() - 8));

		pairButton = Rect.EMPTY;
		if (feed == null || message != null) {
			String msg = message != null ? message : "Select a thread.";
			boolean offerPair = message != null && messageAction == ChatSource.Unavailable.Action.PAIR && actions != null;
			var msgLines = font.split(Component.literal(msg), Math.max(40, well.w() - 24));
			int blockH = msgLines.size() * font.lineHeight + (offerPair ? 20 : 0);
			int ly = well.y() + Math.max(4, (well.h() - blockH) / 2);
			// an empty state with a way out reads as guidance (muted), not as an error
			int color = offerPair ? t.muted() : message != null && key != null ? t.errorText() : t.muted();
			for (var line : msgLines) {
				g.centeredText(font, line, well.x() + well.w() / 2, ly, color);
				ly += font.lineHeight;
			}
			if (offerPair) {
				pairButton = button(g, font, t, I18n.get("t3craft.chat.history.pair_button"), well.x() + well.w() / 2, ly + 6, true, mouseX, mouseY);
			}
		} else {
			drawFeed(g, font, t, textW, mouseX, mouseY);
		}

		// ---- footer: key-cap hints
		if (hintsShown) {
			int fy = panel.bottom() - pad - footerH + 2;
			String[] hs = hints;
			while (hs.length > 2 && ChatDraw.hintsWidth(font, hs) > w) {
				hs = java.util.Arrays.copyOf(hs, hs.length - 2);
			}
			if (ChatDraw.hintsWidth(font, hs) <= w) {
				ChatDraw.hints(g, font, t, x, fy, hs);
			}
		}

		// ---- command bar: ready when the environment can operate (sending arrives with item 19),
		// otherwise a neutral read-only line with an "Enable control" button
		controlButton = Rect.EMPTY;
		if (showPrompt && footer != null) {
			bar = col ? new Rect(x, barY, w, fieldH) : new Rect(bounds.x(), bounds.bottom() - fieldH, bounds.w(), fieldH);
			footer.render(g, font, bar, mouseX, mouseY);
		} else if (showPrompt) {
			bar = new Rect(bounds.x(), bounds.bottom() - fieldH, bounds.w(), fieldH);
			ChatAccess.Reply reply = replyState();
			ChatDraw.field(g, t, bar, false);
			int px = bar.x() + 5;
			g.text(font, ">", px, bar.y() + 5, t.brass(), false);
			int tx = px + font.width(">") + 4;
			if (reply == ChatAccess.Reply.READ_ONLY && actions != null) {
				String label = I18n.get("t3craft.chat.reply.enable_button");
				int bw = font.width(label) + 10;
				controlButton = button(g, font, t, label, bar.right() - 3 - bw / 2, bar.y() + 3, false, mouseX, mouseY);
				String ph = ChatDraw.clip(font, I18n.get(compact ? "t3craft.chat.reply.read_only_short" : "t3craft.chat.reply.read_only"),
						Math.max(0, controlButton.x() - 6 - tx));
				g.text(font, ph, tx, bar.y() + 5, t.ghost(), false);
				if (controlButton.contains(mouseX, mouseY)) {
					g.setTooltipForNextFrame(font, Component.translatable("t3craft.operate.enable.tooltip"), mouseX, mouseY);
				}
			}
		} else {
			bar = Rect.EMPTY;
		}
	}

	/**
	 * A small pill button centred on {@code cx} at {@code y} (12 px tall). Returns its rectangle for
	 * hit-testing. {@code primary} draws it in the accent colour.
	 */
	private static Rect button(GuiGraphicsExtractor g, Font font, ChatTheme t, String label, int cx, int y, boolean primary, int mouseX, int mouseY) {
		int w = font.width(label) + 10;
		Rect r = new Rect(cx - w / 2, y, w, 12);
		boolean hover = r.contains(mouseX, mouseY);
		ChatDraw.pillBox(g, t, r.x(), r.y(), r.w(), hover);
		g.text(font, label, r.x() + 5, r.y() + 2, hover || primary ? t.accent() : t.keycapText(), false);
		return r;
	}

	private String subtitle(ChatTranscript.@Nullable Status status, ThreadFeed.@Nullable State st) {
		String env = envLabel.isBlank() ? envId : envLabel;
		return env.isBlank() ? "" : "· " + env;
	}

	private static String connection(ThreadFeed.@Nullable State st) {
		if (st == null) {
			return "";
		}
		return switch (st.phase()) {
			case LIVE -> "● live";
			case FAILED -> "✖ " + st.detail();
			default -> "⟳ " + st.detail();
		};
	}

	private void drawFeed(GuiGraphicsExtractor g, Font font, ChatTheme t, int textW, int mouseX, int mouseY) {
		ThreadFeed f = feed;
		g.enableScissor(well.x() + 2, well.y() + 2, well.right() - 2, well.bottom() - 2);
		int top = feedTop();
		Hit hover = hitTest(mouseX, mouseY);
		String hoverUrl = hover == null ? null : hover.url();
		int tx0 = well.x() + TEXT_X;
		int x1 = tx0 + textW;
		for (Box b : boxes) {
			int y0 = top + b.y0();
			int y1 = top + b.y1();
			if (y1 >= well.y() && y0 <= well.bottom()) {
				ChatDraw.bubble(g, t, new Rect(tx0 + b.indent() - 2, y0, x1 - tx0 - b.indent() + 4, y1 - y0), ChatTheme.withAlpha(t.needsYou(), 40));
			}
		}
		for (RLine l : lines) {
			int ly = top + l.y();
			int lh = l.h();
			if (ly + lh < well.y() || ly > well.bottom()) {
				continue;
			}
			if (l.sep() != null) {
				ChatDraw.separator(g, font, t, l.sep(), well.x() + 6, well.right() - 10, ly + 1);
				continue;
			}
			if (l.stripe() != 0) {
				g.fill(well.x() + STRIPE_X, ly - (l.avatar() != null ? 1 : 0), well.x() + STRIPE_X + 2, ly + lh, l.stripe());
			}
			if (l.avatar() != null) {
				g.fill(well.x() + AVATAR_X, ly, well.x() + AVATAR_X + 8, ly + 8, l.avatarColor());
				g.text(font, l.avatar(), well.x() + AVATAR_X + (9 - font.width(l.avatar())) / 2, ly, t.panelHi(), false);
			}
			int lx = tx0 + l.indent();
			if (l.bg() != 0) {
				g.fill(lx, ly, x1, ly + lh, l.bg());
			}
			if (l.rule()) {
				g.fill(lx, ly + lh / 2, x1, ly + lh / 2 + 1, t.edge());
			}
			if (l.grid() != null) {
				drawGrid(g, t, l.grid(), lx, ly, lh);
			}
			if (l.caret() && ChatDraw.caretOn()) {
				g.fill(tx0, ly, tx0 + 5, ly + 8, t.text());
			}
			int textY = ly + (lh - 8) / 2;
			if (l.icon() != null) {
				ChatDraw.toolIcon(g, font, t, l.icon(), lx - 15, ly + (lh - 12) / 2);
			}
			boolean hot = hover != null && hover.line() == l && (l.toggle() != null || l.loadMore());
			for (TextWrap.Piece p : l.pieces()) {
				int c = l.color();
				String u = p.span().url();
				if (u != null) {
					boolean under = u.equals(hoverUrl);
					if (Links.kind(u) == Links.Kind.THREAD && p.span().text().startsWith(CHIP)) {
						// thread chip: the state where the glyph is, then the title
						int dw = measure(font, p.span().withText(CHIP));
						drawThreadState(g, t, rowFor(u), lx + p.x(), textY, dw);
						String rest = p.span().text().substring(CHIP.length());
						if (!rest.isEmpty()) {
							g.text(font, component(p.span().withText(rest), c, t, under), lx + p.x() + dw, textY, 0xFFFFFFFF, t.shadow());
						}
					} else {
						g.text(font, component(p.span(), c, t, under), lx + p.x(), textY, 0xFFFFFFFF, t.shadow());
					}
					continue;
				}
				if (l.icon() != null && p.span().italic() && !p.span().code()) {
					// tool argument: AgentCraft draws it in tool_arg, upright
					g.text(font, Component.literal(p.span().text()).withColor((hot ? t.text() : t.toolArg()) & 0xFFFFFF), lx + p.x(), textY,
							0xFFFFFFFF, t.shadow());
					continue;
				}
				if (hot) {
					c = t.text();
				}
				g.text(font, component(p.span(), c, t), lx + p.x(), textY, 0xFFFFFFFF, t.shadow());
			}
		}
		Trailer tr = trailer;
		if (tr != null && trailerY >= 0) {
			int ty = top + trailerY;
			if (ty < well.bottom() && ty + tr.height() > well.y()) {
				tr.draw(g, font, t, tx0, ty, textW);
			}
		}
		g.disableScissor();
		if (f != null && f.transcript().view().entries().isEmpty()) {
			ThreadFeed.State fs = f.state();
			String msg = fs.phase() == ThreadFeed.Phase.LIVE ? "No messages yet." : fs.detail();
			g.centeredText(font, Component.literal(ChatDraw.clip(font, msg, well.w() - 20)), well.x() + well.w() / 2, well.y() + well.h() / 2,
					t.muted());
		}
		if (f != null) {
			ThreadFeed.State rs = f.state();
			if (rs.refreshing()) {
				// subtle: the rows are the cached ones until the fresh snapshot is in
				String tag = rs.phase() == ThreadFeed.Phase.OFFLINE ? (rs.fromDisk() ? "cached · offline" : "offline")
						: rs.fromDisk() ? "⟳ cached" : "⟳ updating";
				int tw = font.width(tag) + 10;
				ChatDraw.pill(g, font, t, tag, well.right() - 12 - tw, well.y() + 4, t.muted());
			}
		}
		boolean barHover = mouseX >= well.right() - 8 && mouseX < well.right() - 2 && well.contains(mouseX, mouseY);
		ChatDraw.scrollbar(g, t, well.right() - 8, well.y() + 2, well.h() - 4, scroll.content(), scroll.view(), scroll.fraction(), barHover);
		if (!scroll.following() && scroll.scrollable()) {
			String more = "↓ newer below";
			int mw = font.width(more) + 10;
			ChatDraw.pill(g, font, t, more, well.right() - 14 - mw, well.bottom() - 15, t.muted());
		}
		if (hover != null && hover.url() != null) {
			g.requestCursor(CursorTypes.POINTING_HAND);
			g.setTooltipForNextFrame(font, linkTooltip(hover.url()), mouseX, mouseY);
		} else if (hover != null && hover.line().grid() != null && hover.line().grid().tips() != null) {
			// table cell cut with "…": its full text
			Grid gr = hover.line().grid();
			int col = gr.columnAt(mouseX - tx0 - hover.line().indent());
			String tip = col >= 0 && col < gr.tips().length ? gr.tips()[col] : null;
			if (tip != null && !tip.isBlank()) {
				g.setTooltipForNextFrame(font, Component.literal(tip), mouseX, mouseY);
			}
		}
	}

	/** One slice of a Markdown table: fill, outer border, faint inner column rules, top/bottom rule. */
	private static void drawGrid(GuiGraphicsExtractor g, ChatTheme t, Grid gr, int lx, int ly, int lh) {
		int[] e = gr.edges();
		int left = lx + e[0];
		int right = lx + e[e.length - 1] + 1;
		int faint = ChatTheme.withAlpha(t.edge(), 140);
		g.fill(left, ly, right, ly + lh, gr.fill());
		for (int k = 0; k < e.length; k++) {
			boolean outer = k == 0 || k == e.length - 1;
			g.fill(lx + e[k], ly, lx + e[k] + 1, ly + lh, outer ? t.edge() : faint);
		}
		if (gr.lineTop()) {
			g.fill(left, ly, right, ly + 1, gr.strongTop() ? t.edge() : faint);
		}
		if (gr.lineBottom()) {
			g.fill(left, ly + lh - 1, right, ly + lh, t.edge());
		}
	}
}
