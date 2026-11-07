package dev.t3craft.client.chat;

import dev.t3craft.client.chat.ChatEntry.Kind;
import dev.t3craft.client.chat.Markdown.Span;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Read-only chat/history of one T3 thread: role-labelled rows, basic Markdown (headings, lists,
 * monospaced code, copyable links), foldable tool calls, current status and who needs it, older
 * pages on demand and live updates. The reply box is present but disabled: replying needs
 * {@code orchestration:operate}, which T3Craft does not request.
 *
 * <p>Owns its {@link ThreadFeed}: opened in {@link #init()} the first time, closed in
 * {@link #removed()}. No static state, so a hot-swapped class never sees old instances.
 */
public final class ChatScreen extends Screen {
	private static final int MAX_CONTENT_WIDTH = 620;
	private static final int HEADER_H = 44;
	private static final int FOOTER_H = 34;
	private static final int LINE_GAP = 1;
	private static final int ENTRY_GAP = 6;
	private static final int INDENT = 10;
	private static final Style MONO = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.withDefaultNamespace("uniform")));
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
	private static final SystemToast.SystemToastId COPIED = new SystemToast.SystemToastId(2000L);

	private final @Nullable ThreadFeed feed;
	private final String envLabel;
	private final String fallbackTitle;
	private final @Nullable String unavailable;
	private final Set<String> expanded = new HashSet<>();

	private List<RLine> lines = List.of();
	private int contentHeight;
	private double scroll;
	private boolean stickToBottom = true;
	private long builtVersion = -1;
	private int builtWidth = -1;
	private boolean layoutDirty = true;
	private @Nullable String firstKey;
	private boolean started;

	/** A rendered line: pieces at x (relative to {@code indent}), plus what a click on it does. */
	private record RLine(int indent, List<TextWrap.Piece> pieces, int color, int height, int background,
			@Nullable String toggleKey, boolean loadMore, boolean rule, @Nullable String entryKey) {
	}

	ChatScreen(ThreadFeed feed, String fallbackTitle) {
		super(Component.literal(fallbackTitle));
		this.feed = feed;
		this.envLabel = feed.endpoint().label();
		this.fallbackTitle = fallbackTitle;
		this.unavailable = null;
	}

	/** A screen that only explains why the chat cannot open (not paired, bridge mode, ...). */
	ChatScreen(String title, String envLabel, String reason) {
		super(Component.literal(title));
		this.feed = null;
		this.envLabel = envLabel;
		this.fallbackTitle = title;
		this.unavailable = reason;
	}

	@Override
	protected void init() {
		if (feed != null && !started) {
			started = true;
			feed.start();
		}
		int w = contentWidth();
		int x = (width - w) / 2;
		int y = height - FOOTER_H + 7;
		EditBox reply = new EditBox(font, x, y, w - 72, 20, Component.literal("Reply"));
		reply.setEditable(false);
		reply.setHint(Component.literal("Replying requires operate (read-only pairing)"));
		reply.setTooltip(Tooltip.create(Component.literal(
				"T3Craft is paired with orchestration:read only. Replying would need orchestration:operate, which is not enabled.")));
		reply.active = false;
		addRenderableWidget(reply);
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x + w - 68, y, 68, 20).build());
		layoutDirty = true;
	}

	@Override
	public void removed() {
		if (feed != null) {
			feed.close();
		}
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int contentWidth() {
		return Math.min(MAX_CONTENT_WIDTH, width - 24);
	}

	private int bodyTop() {
		return HEADER_H;
	}

	private int bodyBottom() {
		return height - FOOTER_H;
	}

	// ---- layout --------------------------------------------------------------------------------

	private void relayoutIfNeeded() {
		if (feed == null) {
			return;
		}
		ChatTranscript.View v = feed.transcript().view();
		int w = contentWidth();
		if (!layoutDirty && v.version() == builtVersion && w == builtWidth) {
			return;
		}
		int oldHeight = contentHeight;
		String oldFirst = firstKey;
		List<RLine> out = new ArrayList<>();
		ThreadFeed.State st = feed.state();
		if (v.hasMoreHistory() || st.olderError() != null) {
			String label = st.loadingOlder() ? "Loading earlier messages…"
					: st.olderError() != null ? "⚠ " + st.olderError() + " (click to retry)" : "▲ Load earlier messages";
			out.add(new RLine(0, List.of(new TextWrap.Piece(Span.plain(label), 0, font.width(label))), 0xFF8AB4FF,
					font.lineHeight + 4, 0, null, true, false, null));
		} else if (v.synced() || !v.entries().isEmpty()) {
			out.add(new RLine(0, List.of(new TextWrap.Piece(Span.plain("Start of thread"), 0, font.width("Start of thread"))),
					0xFF666666, font.lineHeight + 4, 0, null, false, false, null));
		}
		for (ChatEntry e : v.entries()) {
			layoutEntry(out, e, w);
		}
		int h = 0;
		for (RLine l : out) {
			h += l.height();
		}
		lines = out;
		contentHeight = h;
		firstKey = v.entries().isEmpty() ? null : v.entries().getFirst().key();
		// Older page prepended: keep what the player was looking at in place.
		if (!stickToBottom && oldFirst != null && firstKey != null && !oldFirst.equals(firstKey)) {
			scroll += contentHeight - oldHeight;
		}
		builtVersion = v.version();
		builtWidth = w;
		layoutDirty = false;
		clampScroll();
	}

	private void layoutEntry(List<RLine> out, ChatEntry e, int w) {
		boolean folded = e.kind().foldable && !expanded.contains(e.key());
		// Role line: "You · 12:03" / "▶ $ ls -la · 12:03"
		StringBuilder head = new StringBuilder();
		if (e.kind().foldable) {
			head.append(folded ? "▶ " : "▼ ");
		}
		head.append(e.kind().foldable && !e.title().isBlank() ? e.title() : e.kind().role);
		if (!e.kind().foldable && !e.title().isBlank()) {
			head.append(" · ").append(e.title());
		}
		if (e.inherited()) {
			head.append("  (from parent thread)");
		}
		if (e.streaming() || "running".equals(e.status()) || "pending".equals(e.status())) {
			head.append("  …");
		} else if ("failed".equals(e.status())) {
			head.append("  ✖");
		}
		String time = e.at() == null ? "" : TIME.format(Instant.ofEpochMilli(e.at()));
		List<Span> headSpans = List.of(new Span(head.toString(), !e.kind().foldable, false, false, null));
		List<TextWrap.Line> headLines = TextWrap.wrap(headSpans, w - font.width(time) - 8, this::measure);
		String toggle = e.kind().foldable ? e.key() : null;
		for (int i = 0; i < headLines.size(); i++) {
			List<TextWrap.Piece> pieces = new ArrayList<>(headLines.get(i).pieces());
			if (i == 0 && !time.isEmpty()) {
				pieces.add(new TextWrap.Piece(new Span(time, false, false, false, null), w - font.width(time), font.width(time)));
			}
			out.add(new RLine(0, pieces, e.kind().color, font.lineHeight + LINE_GAP, 0, toggle, false, false, e.key()));
		}
		if (folded) {
			out.add(spacer(ENTRY_GAP));
			return;
		}
		int textColor = e.kind() == Kind.REASONING || e.kind() == Kind.SYSTEM ? 0xFFAAAAAA : e.kind() == Kind.ERROR ? 0xFFFF8888 : 0xFFE6E6E6;
		int bodyWidth = w - INDENT;
		if (!e.body().isBlank()) {
			if (e.markdown()) {
				layoutMarkdown(out, Markdown.parse(e.body()), bodyWidth, textColor);
			} else {
				addWrapped(out, List.of(Span.plain(e.body())), INDENT, bodyWidth, textColor, 0);
			}
		}
		if (e.detail() != null && !e.detail().isBlank()) {
			layoutCode(out, List.of(e.detail().split("\n", -1)), bodyWidth);
		}
		out.add(spacer(ENTRY_GAP));
	}

	private void layoutMarkdown(List<RLine> out, List<Markdown.Block> blocks, int w, int color) {
		boolean first = true;
		Markdown.Block prev = null;
		for (Markdown.Block b : blocks) {
			if (!first && !(b instanceof Markdown.ListItem && prev instanceof Markdown.ListItem)) {
				out.add(spacer(3));
			}
			first = false;
			Markdown.Block prevBlock = b;
			switch (b) {
				case Markdown.Heading h -> {
					int c = h.level() <= 1 ? 0xFFFFD27F : h.level() == 2 ? 0xFFFFE6A8 : 0xFFFFFFFF;
					List<Span> spans = h.spans().stream().map(s -> new Span(s.text(), true, s.italic(), s.code(), s.url())).toList();
					addWrapped(out, spans, INDENT, w, c, 0);
				}
				case Markdown.Paragraph p -> addWrapped(out, p.spans(), INDENT, w, color, 0);
				case Markdown.ListItem li -> {
					int ind = INDENT + li.depth() * 10;
					String marker = li.marker() + " ";
					int mw = font.width(marker);
					List<TextWrap.Line> wrapped = TextWrap.wrap(li.spans(), w - (ind - INDENT) - mw, this::measure);
					for (int i = 0; i < wrapped.size(); i++) {
						List<TextWrap.Piece> pieces = new ArrayList<>();
						if (i == 0) {
							pieces.add(new TextWrap.Piece(Span.plain(marker), 0, mw));
						}
						for (TextWrap.Piece p : wrapped.get(i).pieces()) {
							pieces.add(new TextWrap.Piece(p.span(), p.x() + mw, p.width()));
						}
						out.add(new RLine(ind, pieces, color, font.lineHeight + LINE_GAP, 0, null, false, false, null));
					}
				}
				case Markdown.CodeBlock cb -> layoutCode(out, cb.lines(), w);
				case Markdown.Quote q -> {
					List<Span> spans = new ArrayList<>();
					spans.add(Span.plain("│ "));
					q.spans().forEach(s -> spans.add(new Span(s.text(), s.bold(), true, s.code(), s.url())));
					addWrapped(out, spans, INDENT, w, 0xFFAAAAAA, 0);
				}
				case Markdown.Rule r -> out.add(new RLine(INDENT, List.of(), color, 7, 0, null, false, true, null));
			}
			prev = prevBlock;
		}
	}

	private void layoutCode(List<RLine> out, List<String> codeLines, int w) {
		int inner = w - 8;
		out.add(new RLine(INDENT, List.of(), 0, 2, 0xC0101010, null, false, false, null));
		for (String raw : codeLines) {
			List<Span> spans = List.of(new Span(raw.isEmpty() ? " " : raw, false, false, true, null));
			for (TextWrap.Line l : TextWrap.wrap(spans, inner, this::measure)) {
				List<TextWrap.Piece> shifted = new ArrayList<>();
				l.pieces().forEach(p -> shifted.add(new TextWrap.Piece(p.span(), p.x() + 4, p.width())));
				out.add(new RLine(INDENT, shifted, 0xFFD0D0D0, font.lineHeight + LINE_GAP, 0xC0101010, null, false, false, null));
			}
		}
		out.add(new RLine(INDENT, List.of(), 0, 2, 0xC0101010, null, false, false, null));
	}

	private void addWrapped(List<RLine> out, List<Span> spans, int indent, int w, int color, int bg) {
		for (TextWrap.Line l : TextWrap.wrap(spans, w, this::measure)) {
			out.add(new RLine(indent, l.pieces(), color, font.lineHeight + LINE_GAP, bg, null, false, false, null));
		}
	}

	private RLine spacer(int h) {
		return new RLine(0, List.of(), 0, h, 0, null, false, false, null);
	}

	private int measure(Span s) {
		return font.width(component(s, 0xFFFFFFFF));
	}

	private static MutableComponent component(Span s, int color) {
		Style style = s.code() ? MONO : Style.EMPTY;
		style = style.withBold(s.bold()).withItalic(s.italic());
		int c = color;
		if (s.url() != null) {
			style = style.withUnderlined(true);
			c = 0xFF6FB8FF;
		} else if (s.code()) {
			c = 0xFFFFE08A;
		}
		return Component.literal(s.text()).withStyle(style.withColor(c & 0xFFFFFF));
	}

	// ---- scrolling -----------------------------------------------------------------------------

	private int maxScroll() {
		return Math.max(0, contentHeight - (bodyBottom() - bodyTop()));
	}

	private void clampScroll() {
		int max = maxScroll();
		if (stickToBottom) {
			scroll = max;
		}
		scroll = Math.max(0, Math.min(scroll, max));
	}

	private void scrollBy(double dy) {
		scroll += dy;
		int max = maxScroll();
		scroll = Math.max(0, Math.min(scroll, max));
		stickToBottom = scroll >= max - 1;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (y >= bodyTop() && y < bodyBottom()) {
			scrollBy(-scrollY * (font.lineHeight + LINE_GAP) * 3);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int page = bodyBottom() - bodyTop() - 20;
		switch (event.key()) {
			case InputConstants.KEY_PAGEUP -> {
				scrollBy(-page);
				return true;
			}
			case InputConstants.KEY_PAGEDOWN -> {
				scrollBy(page);
				return true;
			}
			case InputConstants.KEY_HOME -> {
				scrollBy(-contentHeight);
				return true;
			}
			case InputConstants.KEY_END -> {
				scrollBy(contentHeight);
				return true;
			}
			case InputConstants.KEY_UP -> {
				scrollBy(-(font.lineHeight + LINE_GAP));
				return true;
			}
			case InputConstants.KEY_DOWN -> {
				scrollBy(font.lineHeight + LINE_GAP);
				return true;
			}
			default -> {
				return super.keyPressed(event);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && event.y() >= bodyTop() && event.y() < bodyBottom()) {
			Hit hit = hitTest((int) event.x(), (int) event.y());
			if (hit != null) {
				if (hit.url() != null) {
					minecraft.keyboardHandler.setClipboard(hit.url());
					SystemToast.addOrUpdate(minecraft.gui.toastManager(), COPIED, Component.literal("Link copied"),
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
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private record Hit(RLine line, @Nullable String url) {
	}

	private @Nullable Hit hitTest(int mx, int my) {
		int x0 = (width - contentWidth()) / 2;
		int y = bodyTop() - (int) Math.round(scroll);
		for (RLine l : lines) {
			if (my >= y && my < y + l.height()) {
				int rel = mx - x0 - l.indent();
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

	// ---- rendering -----------------------------------------------------------------------------

	@Override
	public void tick() {
		if (feed == null) {
			return;
		}
		ChatTranscript.View v = feed.transcript().view();
		ThreadFeed.State st = feed.state();
		// Reached the top: fetch the previous page by itself.
		if (scroll <= 0 && !stickToBottom && v.hasMoreHistory() && !st.loadingOlder() && st.olderError() == null) {
			feed.loadOlder();
		}
		if (st.loadingOlder() != lastLoading || !java.util.Objects.equals(st.olderError(), lastOlderError)) {
			lastLoading = st.loadingOlder();
			lastOlderError = st.olderError();
			layoutDirty = true;
		}
	}

	private boolean lastLoading;
	private @Nullable String lastOlderError;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		relayoutIfNeeded();
		int w = contentWidth();
		int x0 = (width - w) / 2;
		g.fill(x0 - 6, bodyTop() - 2, x0 + w + 6, bodyBottom() + 2, 0x90000000);
		extractHeader(g, x0, w);

		if (unavailable != null) {
			g.centeredText(font, Component.literal(unavailable), width / 2, (bodyTop() + bodyBottom()) / 2, 0xFFFFAA55);
			super.extractRenderState(g, mouseX, mouseY, a);
			return;
		}

		g.enableScissor(x0 - 6, bodyTop(), x0 + w + 6, bodyBottom());
		int y = bodyTop() - (int) Math.round(scroll);
		Hit hover = mouseY >= bodyTop() && mouseY < bodyBottom() ? hitTest(mouseX, mouseY) : null;
		for (RLine l : lines) {
			int h = l.height();
			if (y + h >= bodyTop() && y <= bodyBottom()) {
				int lx = x0 + l.indent();
				if (l.background() != 0) {
					g.fill(lx, y, x0 + w, y + h, l.background());
				}
				if (l.rule()) {
					g.horizontalLine(lx, x0 + w, y + h / 2, 0xFF555555);
				}
				boolean hot = hover != null && hover.line() == l && (l.toggleKey() != null || l.loadMore());
				for (TextWrap.Piece p : l.pieces()) {
					int color = hot ? 0xFFFFFFFF : l.color();
					g.text(font, component(p.span(), color), lx + p.x(), y + 1, 0xFFFFFFFF, false);
				}
			}
			y += h;
		}
		g.disableScissor();

		if (feed != null && feed.transcript().view().entries().isEmpty()) {
			ThreadFeed.State st = feed.state();
			String msg = st.phase() == ThreadFeed.Phase.LIVE ? "No messages yet." : st.detail();
			g.centeredText(font, Component.literal(msg), width / 2, (bodyTop() + bodyBottom()) / 2, 0xFF888888);
		}
		// Scrollbar
		int max = maxScroll();
		if (max > 0) {
			int track = bodyBottom() - bodyTop();
			int thumb = Math.max(12, track * track / Math.max(contentHeight, 1));
			int ty = bodyTop() + (int) ((track - thumb) * (scroll / max));
			g.fill(x0 + w + 3, ty, x0 + w + 5, ty + thumb, 0xFF888888);
		}
		if (!stickToBottom && max > 0) {
			String jump = "▼ newest (End)";
			int jw = font.width(jump);
			g.fill(x0 + w - jw - 6, bodyBottom() - 13, x0 + w, bodyBottom() - 1, 0xC0303030);
			g.text(font, jump, x0 + w - jw - 3, bodyBottom() - 11, 0xFFCCCCCC, false);
		}
		if (hover != null && hover.url() != null) {
			g.setTooltipForNextFrame(font, Component.literal("Click to copy: " + hover.url()), mouseX, mouseY);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	private void extractHeader(GuiGraphicsExtractor g, int x0, int w) {
		String title = fallbackTitle;
		ChatTranscript.Status status = null;
		ThreadFeed.State st = null;
		if (feed != null) {
			ChatTranscript.View v = feed.transcript().view();
			if (!v.title().isBlank()) {
				title = v.title();
			}
			status = feed.transcript().hasSnapshot() ? v.status() : null;
			st = feed.state();
		}
		String right = envLabel;
		int rw = font.width(right);
		g.text(font, Component.literal(font.plainSubstrByWidth(title, w - rw - 12)).withStyle(Style.EMPTY.withBold(true)), x0, 8, 0xFFFFFFFF, true);
		g.text(font, right, x0 + w - rw, 8, 0xFF888888, false);
		int y = 20;
		if (status != null) {
			Component line = Component.literal(status.state().glyph + " " + status.state().word).withColor(status.state().color)
					.append(Component.literal(status.detail().equals(status.state().word) ? "" : " · " + status.detail()).withColor(0xAAAAAA));
			g.text(font, line, x0, y, 0xFFFFFFFF, false);
		}
		if (st != null && st.phase() != ThreadFeed.Phase.LIVE) {
			String conn = (st.phase() == ThreadFeed.Phase.FAILED ? "✖ " : "⟳ ") + st.detail();
			conn = font.plainSubstrByWidth(conn, w / 2);
			g.text(font, conn, x0 + w - font.width(conn), y, st.phase() == ThreadFeed.Phase.FAILED ? 0xFFFF5555 : 0xFFFFAA55, false);
		} else if (st != null) {
			String live = "● live · read-only";
			g.text(font, live, x0 + w - font.width(live), y, 0xFF55AA55, false);
		}
		if (status != null && status.needs() != null) {
			g.text(font, font.plainSubstrByWidth("⚑ " + status.needs(), w), x0, y + 11, 0xFFFFAA00, false);
		}
	}
}
