// Layout ideas adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.chat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.chat.ChatEntry.Kind;
import dev.t3craft.client.chat.Markdown.Span;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Reusable widget: the read-only conversation of one T3 thread, drawn in any rectangle. Owns the
 * live {@link ThreadFeed} of the thread it shows (one at a time): {@link #show} switches threads,
 * {@link #close()} drops the socket. Embed it in any screen and forward render/input/tick:
 *
 * <pre>{@code
 * ConversationPanel conv = new ConversationPanel(ChatScreens.defaultSource());
 * conv.show(envId, envLabel, threadId, title);
 * // tick: conv.tick();   render: conv.extract(g, font, x, y, w, h, mouseX, mouseY);
 * // input: conv.mouseClicked(mx, my, button); conv.mouseScrolled(mx, my, dy); conv.keyPressed(event);
 * // screen removed(): conv.close();
 * }</pre>
 *
 * <p>Layout (after AgentCraft's console): header with title, environment badge, state and who needs
 * it; an inset feed with a colour stripe per speaker, time separators, foldable tool calls, basic
 * Markdown and copyable links; a "newer below" pill; a prompt bar at the bottom that is disabled
 * (replying needs {@code orchestration:operate}). Colours: {@link ChatTheme} (portable terminal).
 */
public final class ConversationPanel implements AutoCloseable {
	private static final int HEADER_H = 26;
	private static final int PROMPT_H = 16;
	private static final int PAD = 6;
	private static final int STRIPE_X = 3;
	private static final int TEXT_X = 9;
	private static final int INDENT = 6;
	private static final int ENTRY_GAP = 5;
	private static final Style MONO = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.withDefaultNamespace("uniform")));
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
	private static final SystemToast.SystemToastId COPIED = new SystemToast.SystemToastId(2000L);
	public static final String REPLY_HINT = "Replying requires orchestration:operate · read-only";

	private final ChatSource source;
	private @Nullable ThreadFeed feed;
	private @Nullable String key;
	private String title = "";
	private String envId = "";
	private String envLabel = "";
	private @Nullable String message;
	private final Set<String> expanded = new HashSet<>();

	// layout cache
	private List<RLine> lines = List.of();
	private int contentHeight;
	private double scroll;
	private boolean stickToBottom = true;
	private long builtVersion = -1;
	private int builtWidth = -1;
	private boolean layoutDirty = true;
	private @Nullable String firstKey;
	private boolean lastLoading;
	private @Nullable String lastOlderError;
	private @Nullable Font font;

	// last rectangles (hit-testing)
	private int px;
	private int py;
	private int pw;
	private int ph;
	private int wellX;
	private int wellY;
	private int wellW;
	private int wellH;

	/** A rendered feed line; x of pieces is relative to {@code indent}. */
	private record RLine(int indent, List<TextWrap.Piece> pieces, int color, int height, int background, int stripe,
			@Nullable String toggleKey, boolean loadMore, boolean rule, boolean separator) {
	}

	public ConversationPanel(ChatSource source) {
		this.source = source;
	}

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
		resetView();
		try {
			ChatSource.Endpoint endpoint = source.endpoint(envId);
			if (envLabel.isBlank()) {
				this.envLabel = endpoint.label();
			}
			feed = new ThreadFeed(endpoint, threadId, source.http(), () -> {
			});
			feed.start();
		} catch (ChatSource.Unavailable e) {
			message = e.getMessage();
		}
	}

	/** Shows a message and no thread (e.g. "Select a thread"). */
	public void showNothing(String text) {
		closeFeed();
		key = null;
		title = "";
		envId = "";
		envLabel = "";
		message = text;
		resetView();
	}

	/** Reconnects the current feed now. */
	public void reconnect() {
		if (feed != null) {
			feed.reconnect();
		}
	}

	private void resetView() {
		expanded.clear();
		lines = List.of();
		contentHeight = 0;
		scroll = 0;
		stickToBottom = true;
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
		ChatTranscript.View v = f.transcript().view();
		ThreadFeed.State st = f.state();
		if (scroll <= 0 && !stickToBottom && v.hasMoreHistory() && !st.loadingOlder() && st.olderError() == null) {
			f.loadOlder(); // reached the top: fetch the previous page by itself
		}
		if (st.loadingOlder() != lastLoading || !Objects.equals(st.olderError(), lastOlderError)) {
			lastLoading = st.loadingOlder();
			lastOlderError = st.olderError();
			layoutDirty = true;
		}
	}

	// ---- layout ------------------------------------------------------------------------------

	private int lineH() {
		return ChatTheme.LINE_H;
	}

	private void relayoutIfNeeded(Font font, int w) {
		ThreadFeed f = feed;
		if (f == null) {
			return;
		}
		ChatTranscript.View v = f.transcript().view();
		if (!layoutDirty && v.version() == builtVersion && w == builtWidth) {
			return;
		}
		int oldHeight = contentHeight;
		String oldFirst = firstKey;
		List<RLine> out = new ArrayList<>();
		ThreadFeed.State st = f.state();
		if (v.hasMoreHistory() || st.olderError() != null) {
			String label = st.loadingOlder() ? "Loading earlier messages…"
					: st.olderError() != null ? "⚠ " + st.olderError() + " (click to retry)" : "▲ Load earlier messages";
			out.add(new RLine(0, List.of(piece(font, label)), ChatTheme.LINK, lineH() + 4, 0, 0, null, true, false, false));
		} else if (v.synced() || !v.entries().isEmpty()) {
			out.add(new RLine(0, List.of(piece(font, "Start of thread")), ChatTheme.DIM, lineH() + 4, 0, 0, null, false, false, true));
		}
		Long prevAt = null;
		long now = System.currentTimeMillis();
		for (ChatEntry e : v.entries()) {
			String sep = FeedRules.separator(prevAt, e.at(), ZoneId.systemDefault(), now);
			if (sep != null) {
				out.add(new RLine(0, List.of(piece(font, sep)), ChatTheme.DIM, lineH() + 3, 0, 0, null, false, false, true));
			}
			if (e.at() != null) {
				prevAt = e.at();
			}
			layoutEntry(font, out, e, w);
		}
		int h = 0;
		for (RLine l : out) {
			h += l.height();
		}
		lines = out;
		contentHeight = h;
		firstKey = v.entries().isEmpty() ? null : v.entries().getFirst().key();
		if (!stickToBottom && oldFirst != null && firstKey != null && !oldFirst.equals(firstKey)) {
			scroll += contentHeight - oldHeight; // older page prepended: keep the view in place
		}
		builtVersion = v.version();
		builtWidth = w;
		layoutDirty = false;
		clampScroll();
	}

	private static TextWrap.Piece piece(Font font, String s) {
		return new TextWrap.Piece(Span.plain(s), 0, font.width(s));
	}

	private static int stripeColor(Kind k) {
		return switch (k) {
			case USER -> 0xFF7FD4FF;
			case ASSISTANT -> 0xFF55FFFF;
			case REQUEST -> ChatTheme.ATTENTION;
			case ERROR -> ChatTheme.ERROR;
			case TOOL, SUBAGENT -> 0xFF4A5A64;
			default -> 0xFF39454D;
		};
	}

	private void layoutEntry(Font font, List<RLine> out, ChatEntry e, int w) {
		boolean folded = e.kind().foldable && !expanded.contains(e.key());
		int stripe = stripeColor(e.kind());
		StringBuilder head = new StringBuilder();
		if (e.kind().foldable) {
			head.append(folded ? "▸ " : "▾ ");
			head.append(e.title().isBlank() ? e.kind().role : e.title());
		} else {
			head.append(e.kind().role);
			if (!e.title().isBlank()) {
				head.append(" · ").append(e.title());
			}
		}
		if (e.inherited()) {
			head.append("  (parent thread)");
		}
		if (e.streaming() || "running".equals(e.status()) || "pending".equals(e.status())) {
			head.append("  …");
		} else if ("failed".equals(e.status())) {
			head.append("  ✖");
		}
		String time = e.at() == null ? "" : TIME.format(Instant.ofEpochMilli(e.at()));
		boolean bold = !e.kind().foldable;
		List<TextWrap.Line> headLines = TextWrap.wrap(List.of(new Span(head.toString(), bold, false, false, null)), w - font.width(time) - 8,
				s -> measure(font, s));
		String toggle = e.kind().foldable ? e.key() : null;
		int headColor = e.kind().foldable ? ChatTheme.DIM : e.kind().color;
		for (int i = 0; i < headLines.size(); i++) {
			List<TextWrap.Piece> pieces = new ArrayList<>(headLines.get(i).pieces());
			if (i == 0 && !time.isEmpty()) {
				pieces.add(new TextWrap.Piece(new Span(time, false, false, false, null), w - font.width(time), font.width(time)));
			}
			out.add(new RLine(0, pieces, headColor, lineH() + 1, 0, stripe, toggle, false, false, false));
		}
		if (folded) {
			out.add(spacer(e.kind().foldable ? 2 : ENTRY_GAP));
			return;
		}
		int textColor = e.kind() == Kind.REASONING || e.kind() == Kind.SYSTEM ? ChatTheme.DIM
				: e.kind() == Kind.ERROR ? 0xFFFF8888 : ChatTheme.TEXT;
		int bodyW = w - INDENT;
		if (!e.body().isBlank()) {
			if (e.markdown()) {
				layoutMarkdown(font, out, Markdown.parse(e.body()), bodyW, textColor, stripe);
			} else {
				addWrapped(font, out, List.of(Span.plain(e.body())), INDENT, bodyW, textColor, stripe);
			}
		}
		if (e.detail() != null && !e.detail().isBlank()) {
			layoutCode(font, out, List.of(e.detail().split("\n", -1)), bodyW, stripe);
		}
		out.add(spacer(ENTRY_GAP));
	}

	private void layoutMarkdown(Font font, List<RLine> out, List<Markdown.Block> blocks, int w, int color, int stripe) {
		Markdown.Block prev = null;
		for (Markdown.Block b : blocks) {
			if (prev != null && !(b instanceof Markdown.ListItem && prev instanceof Markdown.ListItem)) {
				out.add(new RLine(0, List.of(), 0, 3, 0, stripe, null, false, false, false));
			}
			switch (b) {
				case Markdown.Heading hd -> {
					int c = hd.level() <= 1 ? ChatTheme.ACCENT : hd.level() == 2 ? 0xFF9FEFEF : ChatTheme.TEXT;
					List<Span> spans = hd.spans().stream().map(s -> new Span(s.text(), true, s.italic(), s.code(), s.url())).toList();
					addWrapped(font, out, spans, INDENT, w, c, stripe);
				}
				case Markdown.Paragraph p -> addWrapped(font, out, p.spans(), INDENT, w, color, stripe);
				case Markdown.ListItem li -> {
					int ind = INDENT + li.depth() * 10;
					String marker = li.marker() + " ";
					int mw = font.width(marker);
					List<TextWrap.Line> wrapped = TextWrap.wrap(li.spans(), w - (ind - INDENT) - mw, s -> measure(font, s));
					for (int i = 0; i < wrapped.size(); i++) {
						List<TextWrap.Piece> pieces = new ArrayList<>();
						if (i == 0) {
							pieces.add(new TextWrap.Piece(Span.plain(marker), 0, mw));
						}
						for (TextWrap.Piece pc : wrapped.get(i).pieces()) {
							pieces.add(new TextWrap.Piece(pc.span(), pc.x() + mw, pc.width()));
						}
						out.add(new RLine(ind, pieces, color, lineH(), 0, stripe, null, false, false, false));
					}
				}
				case Markdown.CodeBlock cb -> layoutCode(font, out, cb.lines(), w, stripe);
				case Markdown.Quote q -> {
					List<Span> spans = new ArrayList<>();
					spans.add(Span.plain("│ "));
					q.spans().forEach(s -> spans.add(new Span(s.text(), s.bold(), true, s.code(), s.url())));
					addWrapped(font, out, spans, INDENT, w, ChatTheme.DIM, stripe);
				}
				case Markdown.Rule r -> out.add(new RLine(INDENT, List.of(), color, 7, 0, stripe, null, false, true, false));
			}
			prev = b;
		}
	}

	private void layoutCode(Font font, List<RLine> out, List<String> codeLines, int w, int stripe) {
		out.add(new RLine(INDENT, List.of(), 0, 2, ChatTheme.CODE_BG, stripe, null, false, false, false));
		for (String raw : codeLines) {
			List<Span> spans = List.of(new Span(raw.isEmpty() ? " " : raw, false, false, true, null));
			for (TextWrap.Line l : TextWrap.wrap(spans, w - 8, s -> measure(font, s))) {
				List<TextWrap.Piece> shifted = new ArrayList<>();
				l.pieces().forEach(p -> shifted.add(new TextWrap.Piece(p.span(), p.x() + 4, p.width())));
				out.add(new RLine(INDENT, shifted, ChatTheme.CODE_TEXT, lineH(), ChatTheme.CODE_BG, stripe, null, false, false, false));
			}
		}
		out.add(new RLine(INDENT, List.of(), 0, 2, ChatTheme.CODE_BG, stripe, null, false, false, false));
	}

	private void addWrapped(Font font, List<RLine> out, List<Span> spans, int indent, int w, int color, int stripe) {
		for (TextWrap.Line l : TextWrap.wrap(spans, w, s -> measure(font, s))) {
			out.add(new RLine(indent, l.pieces(), color, lineH(), 0, stripe, null, false, false, false));
		}
	}

	private static RLine spacer(int h) {
		return new RLine(0, List.of(), 0, h, 0, 0, null, false, false, false);
	}

	private static int measure(Font font, Span s) {
		return font.width(component(s, 0xFFFFFFFF));
	}

	private static MutableComponent component(Span s, int color) {
		Style style = s.code() ? MONO : Style.EMPTY;
		style = style.withBold(s.bold()).withItalic(s.italic());
		int c = color;
		if (s.url() != null) {
			style = style.withUnderlined(true);
			c = ChatTheme.LINK;
		} else if (s.code() && color != ChatTheme.CODE_TEXT) {
			c = ChatTheme.INLINE_CODE;
		}
		return Component.literal(s.text()).withStyle(style.withColor(c & 0xFFFFFF));
	}

	// ---- scrolling ---------------------------------------------------------------------------

	private int maxScroll() {
		return Math.max(0, contentHeight - (wellH - 6));
	}

	private void clampScroll() {
		int max = maxScroll();
		if (stickToBottom) {
			scroll = max;
		}
		scroll = Math.max(0, Math.min(scroll, max));
	}

	public void scrollBy(double dy) {
		scroll += dy;
		int max = maxScroll();
		scroll = Math.max(0, Math.min(scroll, max));
		stickToBottom = scroll >= max - 1;
	}

	public void scrollToEnd() {
		stickToBottom = true;
		clampScroll();
	}

	public boolean contains(double mx, double my) {
		return mx >= px && mx < px + pw && my >= py && my < py + ph;
	}

	public boolean mouseScrolled(double mx, double my, double dy) {
		if (!contains(mx, my)) {
			return false;
		}
		scrollBy(-dy * lineH() * 3);
		return true;
	}

	/** PgUp/PgDn/Home/End scroll the feed. Arrow keys are left to the host (thread list). */
	public boolean keyPressed(KeyEvent event) {
		int page = Math.max(lineH(), wellH - 20);
		switch (event.key()) {
			case InputConstants.KEY_PAGEUP -> scrollBy(-page);
			case InputConstants.KEY_PAGEDOWN -> scrollBy(page);
			case InputConstants.KEY_HOME -> scrollBy(-contentHeight);
			case InputConstants.KEY_END -> scrollToEnd();
			default -> {
				return false;
			}
		}
		return true;
	}

	private record Hit(RLine line, @Nullable String url) {
	}

	private @Nullable Hit hitTest(int mx, int my) {
		if (mx < wellX || mx >= wellX + wellW || my < wellY || my >= wellY + wellH) {
			return null;
		}
		int y = wellY + 3 - (int) Math.round(scroll);
		for (RLine l : lines) {
			if (my >= y && my < y + l.height()) {
				int rel = mx - wellX - TEXT_X - l.indent();
				String url = null;
				for (TextWrap.Piece p : l.pieces()) {
					if (p.span().url() != null && rel >= p.x() && rel < p.x() + p.width()) {
						url = p.span().url();
					}
				}
				return new Hit(l, url);
			}
			y += l.height();
		}
		return null;
	}

	/** Click in the feed: copy a link, load earlier messages, fold/unfold a tool call. */
	public boolean mouseClicked(double mx, double my, int button) {
		if (button != 0 || !contains(mx, my)) {
			return false;
		}
		Hit hit = hitTest((int) mx, (int) my);
		if (hit == null) {
			return false;
		}
		if (hit.url() != null) {
			Minecraft mc = Minecraft.getInstance();
			mc.keyboardHandler.setClipboard(hit.url());
			SystemToast.addOrUpdate(mc.gui.toastManager(), COPIED, Component.literal("Link copied"),
					Component.literal(hit.url().length() > 60 ? hit.url().substring(0, 59) + "…" : hit.url()));
			return true;
		}
		if (hit.line().loadMore() && feed != null) {
			feed.loadOlder();
			return true;
		}
		if (hit.line().toggleKey() != null) {
			if (!expanded.remove(hit.line().toggleKey())) {
				expanded.add(hit.line().toggleKey());
			}
			stickToBottom = false;
			layoutDirty = true;
			return true;
		}
		return false;
	}

	// ---- rendering ---------------------------------------------------------------------------

	public void extract(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
		this.font = font;
		px = x;
		py = y;
		pw = w;
		ph = h;
		g.fill(x, y, x + w, y + h, ChatTheme.PANEL);
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

		// ---- header: title + badge/env on the right; state + connection below
		int hx = x + PAD;
		int hw = w - 2 * PAD;
		int right = hx + hw;
		if (!envId.isEmpty()) {
			EnvBadge b = EnvBadge.of(envId, envLabel);
			String env = envLabel;
			int ew = Math.min(font.width(env), hw / 3);
			int bw = ChatDraw.badgeWidth(font, b);
			g.text(font, ChatDraw.clip(font, env, ew), right - ew, y + 4, ChatTheme.DIM, false);
			ChatDraw.badge(g, font, b, right - ew - bw - 3, y + 3);
			right -= ew + bw + 8;
		}
		String t = shownTitle.isBlank() ? "Conversation" : shownTitle;
		g.text(font, Component.literal(ChatDraw.clip(font, t, right - hx)).withStyle(Style.EMPTY.withBold(true)), hx, y + 4, ChatTheme.TEXT, false);
		int sy = y + 15;
		int statusEnd = hx;
		if (status != null) {
			Component line = Component.literal(status.state().glyph + " " + status.state().word).withColor(status.state().color)
					.append(Component.literal(status.detail().equals(status.state().word) ? "" : " · " + status.detail()).withColor(ChatTheme.DIM & 0xFFFFFF));
			g.text(font, line, hx, sy, ChatTheme.TEXT, false);
			statusEnd = hx + font.width(line) + 8;
		}
		if (st != null) {
			String conn = st.phase() == ThreadFeed.Phase.LIVE ? "● live" : (st.phase() == ThreadFeed.Phase.FAILED ? "✖ " : "⟳ ") + st.detail();
			int color = st.phase() == ThreadFeed.Phase.LIVE ? ChatTheme.OK : st.phase() == ThreadFeed.Phase.FAILED ? ChatTheme.ERROR : ChatTheme.ATTENTION;
			conn = ChatDraw.clip(font, conn, Math.max(0, x + w - PAD - statusEnd));
			g.text(font, conn, x + w - PAD - font.width(conn), sy, color, false);
		}
		int top = y + HEADER_H;
		g.fill(x + 3, top - 1, x + w - 3, top, ChatTheme.BORDER);

		// ---- who needs it
		if (status != null && status.needs() != null) {
			g.fill(x + 3, top + 2, x + w - 3, top + 14, 0x40FFAA00);
			g.fill(x + 3, top + 2, x + 5, top + 14, ChatTheme.ATTENTION);
			g.text(font, ChatDraw.clip(font, "⚑ " + status.needs(), w - 16), x + 9, top + 4, ChatTheme.ATTENTION, false);
			top += 15;
		}

		// ---- feed well
		wellX = x + 3;
		wellY = top + 3;
		wellW = w - 6;
		wellH = y + h - PROMPT_H - 6 - wellY;
		g.fill(wellX, wellY, wellX + wellW, wellY + wellH, ChatTheme.WELL);
		int textW = wellW - TEXT_X - 8;
		relayoutIfNeeded(font, textW);
		clampScroll();

		if (feed == null || message != null) {
			String msg = message != null ? message : "Select a thread on the left.";
			int ly = wellY + wellH / 2 - 10;
			for (var line : font.split(Component.literal(msg), Math.max(40, wellW - 30))) {
				g.centeredText(font, line, wellX + wellW / 2, ly, message != null && key != null ? ChatTheme.ATTENTION : ChatTheme.DIM);
				ly += font.lineHeight;
			}
		} else {
			g.enableScissor(wellX, wellY + 1, wellX + wellW, wellY + wellH - 1);
			int ly = wellY + 3 - (int) Math.round(scroll);
			Hit hover = hitTest(mouseX, mouseY);
			int tx0 = wellX + TEXT_X;
			for (RLine l : lines) {
				int lh = l.height();
				if (ly + lh >= wellY && ly <= wellY + wellH) {
					if (l.stripe() != 0) {
						g.fill(wellX + STRIPE_X, ly, wellX + STRIPE_X + 2, ly + lh, l.stripe());
					}
					int lx = tx0 + l.indent();
					if (l.background() != 0) {
						g.fill(lx, ly, tx0 + textW, ly + lh, l.background());
					}
					if (l.rule()) {
						g.fill(lx, ly + lh / 2, tx0 + textW, ly + lh / 2 + 1, ChatTheme.RULE);
					}
					if (l.separator() && !l.pieces().isEmpty()) {
						// ── 14:05 ── centred, rules on both sides
						TextWrap.Piece p = l.pieces().getFirst();
						int mid = tx0 + textW / 2;
						int half = p.width() / 2;
						g.fill(tx0, ly + 4, mid - half - 5, ly + 5, ChatTheme.RULE);
						g.fill(mid + half + 5, ly + 4, tx0 + textW, ly + 5, ChatTheme.RULE);
						g.text(font, p.span().text(), mid - half, ly, l.color(), false);
					} else {
						boolean hot = hover != null && hover.line() == l && (l.toggleKey() != null || l.loadMore());
						for (TextWrap.Piece p : l.pieces()) {
							g.text(font, component(p.span(), hot ? ChatTheme.TEXT : l.color()), lx + p.x(), ly + 1, 0xFFFFFFFF, false);
						}
					}
				}
				ly += lh;
			}
			g.disableScissor();
			ThreadFeed.State fs = feed.state();
			if (feed.transcript().view().entries().isEmpty()) {
				String msg = fs.phase() == ThreadFeed.Phase.LIVE ? "No messages yet." : fs.detail();
				g.centeredText(font, Component.literal(ChatDraw.clip(font, msg, wellW - 20)), wellX + wellW / 2, wellY + wellH / 2, ChatTheme.DIM);
			}
			int max = maxScroll();
			if (max > 0) {
				int track = wellH - 4;
				int thumb = Math.max(10, (int) ((long) track * track / Math.max(contentHeight, 1)));
				int ty = wellY + 2 + (int) ((track - thumb) * (scroll / max));
				g.fill(wellX + wellW - 3, ty, wellX + wellW - 1, ty + thumb, ChatTheme.BORDER);
			}
			if (!stickToBottom && max > 0) {
				String more = "↓ newer below (End)";
				int mw = font.width(more) + 8;
				ChatDraw.pill(g, font, more, wellX + wellW - mw - 8, wellY + wellH - 14, ChatTheme.KEYCAP_BG, ChatTheme.DIM);
			}
			if (hover != null && hover.url() != null) {
				g.setTooltipForNextFrame(font, Component.literal("Click to copy: " + hover.url()), mouseX, mouseY);
			}
		}

		// ---- prompt bar (disabled: read-only)
		int by = y + h - PROMPT_H - 3;
		boolean barHover = mouseX >= x + 3 && mouseX < x + w - 3 && mouseY >= by && mouseY < by + PROMPT_H;
		g.fill(x + 3, by, x + w - 3, by + PROMPT_H, ChatTheme.WELL);
		g.outline(x + 3, by, w - 6, PROMPT_H, barHover ? ChatTheme.BORDER : ChatTheme.RULE);
		g.text(font, "›", x + 8, by + 4, ChatTheme.BORDER, false);
		g.text(font, ChatDraw.clip(font, REPLY_HINT, w - 30), x + 16, by + 4, ChatTheme.DIM, false);
		if (barHover) {
			g.setTooltipForNextFrame(font, Component.literal(
					"T3Craft is paired with orchestration:read only. Replying would need orchestration:operate, which is not enabled."), mouseX, mouseY);
		}
	}
}
