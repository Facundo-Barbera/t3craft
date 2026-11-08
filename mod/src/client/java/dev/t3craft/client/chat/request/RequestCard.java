package dev.t3craft.client.chat.request;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.chat.compose.ComposerSkin;
import dev.t3craft.client.chat.compose.ComposerText;
import dev.t3craft.client.chat.compose.ComposerView;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The card of a pending request (feat/t3-user-input), where T3 Code shows
 * {@code ComposerPendingUserInputPanel} / the approval banner: over the composer, in the same dark
 * rounded box ({@link ComposerSkin}) and the same centred column.
 * <ul>
 *   <li><b>Questions</b> ({@code AskUserQuestion}): one question at a time ("2/3" in the header,
 *   ‹ Back / Next ›), the question text, its options as rows with a radio (or a checkbox when
 *   {@code multiSelect}), the description under the label and the key cap 1–9 on the right; an
 *   "Other" field for a custom answer; the {@code preview} of the hovered (or chosen) option in
 *   the monospace font. Picking an option of a single-select question that is not the last moves
 *   on after 200 ms, as T3. Send ({@code runtime-request.respond} with {@code answers}).
 *   A message-mode question can be dismissed ({@code thread.user-input.dismiss}).</li>
 *   <li><b>Approvals</b>: the request's detail (command, path…) in monospace, its reason, and its
 *   choices (the item's {@code options}, else T3's Cancel / Decline / Always allow this session /
 *   Approve) as numbered rows; Approve and Decline also sit in the bar.</li>
 * </ul>
 * Keys while it is open: 1–9 choose, Enter next / send, ←/→ previous / next question, Tab the
 * "Other" field, ↑/↓ scroll, Esc hides it (a strip over the composer brings it back). A failed
 * send shows its reason in the card and keeps every choice ({@link RequestDraft} per request id).
 * Without control (operate) the bar offers "Enable control". Client thread only.
 */
public final class RequestCard {
	/** Height of the collapsed strip drawn over the composer. */
	public static final int STRIP_H = 16;
	private static final int PAD_X = 9;
	private static final int PAD_TOP = 6;
	private static final int PAD_BOTTOM = 5;
	private static final int HEADER_H = 13;
	private static final int BAR_H = 16;
	private static final int BUTTON_H = 14;
	private static final int ERROR_H = 11;
	private static final int LINE_H = 10;
	private static final int SMALL_H = 9;
	private static final int OTHER_MAX_LINES = 3;
	private static final int PREVIEW_MAX_LINES = 14;
	private static final long AUTO_ADVANCE_MS = 200;
	private static final int QUESTION_COLOR = 0xFFA5B4FC; // T3's "Awaiting Input"
	private static final int APPROVAL_COLOR = 0xFFFCD34D; // T3's "Pending Approval"
	private static final int ROW_SELECTED = 0xFF26262E;
	private static final int LABEL = 0xFFC8C8D0;
	private static final int MONO_BG = 0xFF101013;
	private static final Style MONO = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.withDefaultNamespace("uniform")));

	/** What the card asks of its screen. */
	public interface Host {
		/** Whether the environment has an operate session (answers can go out). */
		boolean canOperate(String envId);

		/** "Enable control": the pairing / enable screen. */
		void enableControl(String envId);

		/** Sends {@code action} through the operate gate; {@code done} once, client thread. */
		void run(OperateAction action, Consumer<OperateResult> done);
	}

	private record Hit(Rect r, Runnable action, @Nullable String tip) {
	}

	private final Host host;
	private final RequestCommands commands;
	private final ComposerView.TextInput textInput;
	private final ComposerText other = new ComposerText();
	private final ComposerText.Clipboard clipboard = new ComposerText.Clipboard() {
		@Override
		public String get() {
			return Minecraft.getInstance().keyboardHandler.getClipboard();
		}

		@Override
		public void set(String s) {
			Minecraft.getInstance().keyboardHandler.setClipboard(s);
		}
	};

	private @Nullable String envId;
	private @Nullable String threadKey;
	private @Nullable String title;
	private List<PendingRequest> pending = List.of();
	private final Map<String, RequestDraft> drafts = new HashMap<>();
	/** Requests answered from here, hidden until the stream drops them. */
	private final Set<String> answered = new HashSet<>();
	private @Nullable String sendingId;
	private @Nullable String error;
	private @Nullable String errorFor;
	private boolean collapsed;
	private @Nullable String collapsedFor;
	private boolean otherFocused;
	private @Nullable String otherBound;
	private int maxWidth = Integer.MAX_VALUE;
	private int maxHeight = 220;
	private int scroll;
	private int contentH;
	private long advanceAt = -1;
	private @Nullable String advanceFor;
	private long renderedAt;

	private Rect box = Rect.EMPTY;
	private Rect body = Rect.EMPTY;
	private Rect strip = Rect.EMPTY;
	private Rect otherRect = Rect.EMPTY;
	private final List<Hit> hits = new ArrayList<>();

	public RequestCard(Host host) {
		this(host, RequestCommands.system(), ComposerView.TextInput.MINECRAFT);
	}

	public RequestCard(Host host, RequestCommands commands, ComposerView.TextInput textInput) {
		this.host = host;
		this.commands = commands;
		this.textInput = textInput;
	}

	public void setMaxWidth(int w) {
		maxWidth = Math.max(120, w);
	}

	/** Upper bound of the open card (the body scrolls beyond it); the screen passes ~60% of the conversation. */
	public void setMaxHeight(int h) {
		maxHeight = Math.max(90, h);
	}

	// ---- model --------------------------------------------------------------------------------

	/**
	 * Points the card at a thread and its pending requests ({@code ChatTranscript.pendingRequests}).
	 * Every frame is fine: drafts of requests that are gone are dropped, the others are kept.
	 */
	public void setThread(@Nullable String envId, @Nullable String threadId, @Nullable String title, List<PendingRequest> requests) {
		String key = envId == null || threadId == null ? null : envId + ":" + threadId;
		if (!Objects.equals(key, threadKey)) {
			threadKey = key;
			collapsed = false;
			collapsedFor = null;
			blurOther();
			scroll = 0;
			error = null;
			errorFor = null;
			advanceAt = -1;
		}
		this.envId = envId;
		this.title = title;
		if (requests == null) {
			requests = List.of();
		}
		if (requests != pending) {
			pending = requests;
			Set<String> ids = new HashSet<>();
			requests.forEach(r -> ids.add(r.requestId()));
			drafts.keySet().retainAll(ids);
			answered.retainAll(ids);
			if (sendingId != null && !ids.contains(sendingId)) {
				sendingId = null;
			}
		}
		PendingRequest a = active();
		if (a == null) {
			blurOther();
		} else if (collapsedFor != null && !collapsedFor.equals(a.requestId())) {
			collapsed = false; // a new request opens the card again
			collapsedFor = null;
		}
	}

	/** No thread (a draft, notifications): nothing to show. */
	public void clear() {
		setThread(null, null, null, List.of());
	}

	/** The request on the card: the oldest pending one not answered from here. */
	public @Nullable PendingRequest active() {
		for (PendingRequest r : pending) {
			if (!answered.contains(r.requestId())) {
				return r;
			}
		}
		return null;
	}

	public boolean visible() {
		return active() != null;
	}

	public boolean expanded() {
		return visible() && !collapsed;
	}

	public void expand() {
		collapsed = false;
		collapsedFor = null;
	}

	public void collapse() {
		PendingRequest a = active();
		collapsed = true;
		collapsedFor = a == null ? null : a.requestId();
		blurOther();
	}

	/** Drawn in the last frames (keys only go to a card the player can see). */
	public boolean shown() {
		return expanded() && System.currentTimeMillis() - renderedAt < 250 && box.w() > 0;
	}

	/** The "Other" field has the keyboard: every key and character is its own. */
	public boolean owns() {
		return otherFocused && shown();
	}

	public boolean contains(double mx, double my) {
		return expanded() ? box.contains(mx, my) : strip.contains(mx, my);
	}

	private RequestDraft draft(PendingRequest r) {
		RequestDraft d = drafts.get(r.requestId());
		if (d == null || d.request() != r && !d.request().questions().equals(r.questions())) {
			d = new RequestDraft(r);
			drafts.put(r.requestId(), d);
		}
		return d;
	}

	private boolean canOperate() {
		return envId != null && host.canOperate(envId);
	}

	// ---- layout -------------------------------------------------------------------------------

	/** Height of the open card at {@code width} (bounded by {@link #setMaxHeight}). */
	public int height(int width) {
		PendingRequest r = active();
		if (r == null) {
			return 0;
		}
		Font font = Minecraft.getInstance().font;
		int w = Math.min(width, maxWidth);
		int inner = w - 2 * PAD_X;
		int content = paintBody(null, font, r, 0, 0, inner, -1, -1);
		return fixedH() + Math.min(content, bodyMax());
	}

	private int fixedH() {
		return PAD_TOP + HEADER_H + (errorShown() ? ERROR_H : 0) + 4 + BAR_H + PAD_BOTTOM;
	}

	private int bodyMax() {
		return Math.max(36, maxHeight - fixedH());
	}

	private boolean errorShown() {
		PendingRequest a = active();
		return error != null && a != null && a.requestId().equals(errorFor);
	}

	// ---- rendering ----------------------------------------------------------------------------

	/** The open card, bottom-aligned and centred in {@code r}. */
	public void render(GuiGraphicsExtractor g, Font font, Rect r, int mouseX, int mouseY) {
		hits.clear();
		otherRect = Rect.EMPTY;
		strip = Rect.EMPTY;
		PendingRequest req = active();
		if (req == null || r.w() < 80 || r.h() < 40) {
			box = Rect.EMPTY;
			return;
		}
		renderedAt = System.currentTimeMillis();
		RequestDraft d = req.isQuestion() ? draft(req) : null;
		if (d != null) {
			bindOther(d, false);
			runAutoAdvance(req, d);
		}
		int w = Math.min(r.w(), maxWidth);
		int h = Math.min(r.h(), height(r.w()));
		box = new Rect(r.x() + (r.w() - w) / 2, r.bottom() - h, w, h);
		int accent = req.isQuestion() ? QUESTION_COLOR : APPROVAL_COLOR;
		ComposerSkin.box(g, box.x(), box.y(), box.w(), box.h(), ComposerSkin.BOX, ComposerSkin.BORDER_FOCUS);
		g.fill(box.x() + 1, box.y() + 3, box.x() + 3, box.bottom() - 3, accent);

		int x = box.x() + PAD_X;
		int inner = box.w() - 2 * PAD_X;
		int y = box.y() + PAD_TOP;
		drawHeader(g, font, req, d, x, y, inner, mouseX, mouseY, accent);
		y += HEADER_H;

		// ---- body (scrolls)
		int bodyH = h - fixedH();
		body = new Rect(x, y, inner, Math.max(0, bodyH));
		contentH = paintBody(null, font, req, 0, 0, inner, -1, -1);
		scroll = Math.clamp(scroll, 0, Math.max(0, contentH - body.h()));
		if (body.h() > 0) {
			g.enableScissor(body.x() - 2, body.y(), body.right() + 2, body.bottom());
			paintBody(g, font, req, x, y - scroll, inner, mouseX, mouseY);
			g.disableScissor();
			if (contentH > body.h()) {
				int bx = box.right() - 4;
				g.fill(bx, body.y(), bx + 2, body.bottom(), 0xFF24242A);
				int th = Math.max(10, body.h() * body.h() / contentH);
				int ty = body.y() + (int) ((long) (body.h() - th) * scroll / Math.max(1, contentH - body.h()));
				g.fill(bx, ty, bx + 2, ty + th, 0xFF5A5A64);
			}
		}
		y += body.h();

		// ---- error line
		if (errorShown()) {
			String s = ChatDraw.clip(font, "⚠ " + error, inner);
			g.text(font, s, x, y + 2, ComposerSkin.ERROR, false);
			if (mouseX >= x && mouseX < x + inner && mouseY >= y && mouseY < y + ERROR_H && font.width("⚠ " + error) > inner) {
				g.setTooltipForNextFrame(font, Component.literal(error), mouseX, mouseY);
			}
			y += ERROR_H;
		}

		// ---- bar
		drawBar(g, font, req, d, x, box.bottom() - PAD_BOTTOM - BAR_H, inner, mouseX, mouseY);

		if (otherFocused) {
			textInput.focusChanged(this, true); // a screen change stops text input: keep it on while focused
			if (otherRect.h() > 0) {
				textInput.area(otherRect.x(), otherRect.y(), otherRect.right(), otherRect.bottom());
			}
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.tip() != null && hit.r().contains(mouseX, mouseY)) {
				g.setTooltipForNextFrame(font, Component.literal(hit.tip()), mouseX, mouseY);
				break;
			}
		}
	}

	/** The collapsed card: one line over the composer ("Question pending · header"), click to open. */
	public void renderStrip(GuiGraphicsExtractor g, Font font, Rect r, int mouseX, int mouseY) {
		hits.clear();
		box = Rect.EMPTY;
		PendingRequest req = active();
		if (req == null || r.w() < 80) {
			strip = Rect.EMPTY;
			return;
		}
		renderedAt = System.currentTimeMillis();
		int w = Math.min(r.w(), maxWidth);
		strip = new Rect(r.x() + (r.w() - w) / 2, r.y(), w, STRIP_H);
		boolean hover = strip.contains(mouseX, mouseY);
		int accent = req.isQuestion() ? QUESTION_COLOR : APPROVAL_COLOR;
		ComposerSkin.box(g, strip.x(), strip.y(), strip.w(), strip.h(), hover ? ComposerSkin.HOVER : ComposerSkin.BOX, ComposerSkin.BORDER);
		ChatDraw.round(g, strip.x() + 6, strip.y() + 6, 4, 4, accent);
		String what = req.isQuestion() ? "Question for you" : "Approval needed";
		String more = pending.size() - answered.size() > 1 ? "  +" + (pending.size() - answered.size() - 1) : "";
		String hint = "Answer ▴";
		int hw = font.width(hint);
		g.text(font, hint, strip.right() - 7 - hw, strip.y() + 4, hover ? ComposerSkin.TEXT : ComposerSkin.MUTED, false);
		String label = what + " · " + req.title() + more;
		g.text(font, ChatDraw.clip(font, label, strip.w() - 26 - hw), strip.x() + 15, strip.y() + 4, ComposerSkin.TEXT, false);
		if (hover) {
			g.setTooltipForNextFrame(font, Component.literal("Show the request (it was hidden with Esc)"), mouseX, mouseY);
		}
	}

	private void drawHeader(GuiGraphicsExtractor g, Font font, PendingRequest req, @Nullable RequestDraft d, int x, int y, int w, int mouseX,
			int mouseY, int accent) {
		int right = x + w;
		// ▾ hide
		Rect hide = new Rect(right - 10, y, 10, 10);
		boolean hh = hide.contains(mouseX, mouseY);
		g.text(font, "▾", hide.x() + 2, hide.y() + 1, hh ? ComposerSkin.TEXT : ComposerSkin.MUTED, false);
		hits.add(new Hit(hide, this::collapse, "Hide (Esc)"));
		right -= 14;
		if (req.dismissible() && canOperate() && sendingId == null) {
			Rect dis = new Rect(right - 10, y, 10, 10);
			boolean dh = dis.contains(mouseX, mouseY);
			g.text(font, "✕", dis.x() + 2, dis.y() + 1, dh ? ComposerSkin.ERROR : ComposerSkin.MUTED, false);
			hits.add(new Hit(dis, () -> dismiss(req), "Dismiss the question without answering"));
			right -= 14;
		}
		int queued = pending.size() - answered.size() - 1;
		if (queued > 0) {
			String s = "+" + queued + " more";
			right -= font.width(s);
			g.text(font, s, right, y + 1, ComposerSkin.PLACEHOLDER, false);
			right -= 6;
		}
		if (d != null && req.questions().size() > 1) {
			String s = (d.index() + 1) + "/" + req.questions().size();
			right -= font.width(s);
			g.text(font, s, right, y + 1, ComposerSkin.MUTED, false);
			right -= 6;
		}
		ChatDraw.round(g, x, y + 3, 4, 4, ChatDraw.pulse() > 0.5f ? accent : dim(accent));
		String head;
		if (d != null) {
			PendingRequest.Question q = d.active();
			head = q == null || q.header().isBlank() ? "Question" : q.header();
		} else {
			head = req.title();
		}
		g.text(font, ChatDraw.clip(font, head, right - x - 10), x + 8, y + 1, ComposerSkin.MUTED, false);
	}

	private static int dim(int argb) {
		return (argb & 0x00FFFFFF) | 0x99000000;
	}

	/**
	 * Draws (g non-null) or measures (g null) the body at {@code (x, y)}; returns its height.
	 * Clickable rows add hits only while drawn and inside the visible body.
	 */
	private int paintBody(@Nullable GuiGraphicsExtractor g, Font font, PendingRequest req, int x, int y, int w, int mouseX, int mouseY) {
		int y0 = y;
		ToIntFunction<String> measure = font::width;
		if (req.isQuestion()) {
			RequestDraft d = draft(req);
			PendingRequest.Question q = d.active();
			if (q == null) {
				return 0;
			}
			y += 2;
			y = paragraph(g, font, q.question(), x, y, w, ComposerSkin.TEXT, LINE_H, measure);
			y += 4;
			boolean locked = sendingId != null || !req.canRespond();
			PendingRequest.Option hoverOpt = null;
			for (int i = 0; i < q.options().size(); i++) {
				PendingRequest.Option o = q.options().get(i);
				int idx = i;
				boolean sel = d.selected(q, o);
				int textX = x + 16;
				int textW = w - 16 - 20;
				List<ComposerText.Line> labelLines = ComposerText.wrap(o.label(), Math.max(20, textW), measure);
				boolean showDesc = !o.description().isEmpty() && !o.description().equals(o.label());
				List<ComposerText.Line> descLines = showDesc ? ComposerText.wrap(o.description(), Math.max(20, textW), measure) : List.of();
				int rowH = 3 + labelLines.size() * LINE_H + descLines.size() * SMALL_H + 3;
				Rect row = new Rect(x - 3, y, w + 3, rowH);
				if (g != null) {
					boolean hover = row.contains(mouseX, mouseY) && body.contains(mouseX, mouseY) && !locked;
					if (hover) {
						hoverOpt = o;
					}
					if (sel || hover) {
						ComposerSkin.round(g, row.x(), row.y(), row.w(), row.h(), sel ? ROW_SELECTED : ComposerSkin.HOVER);
					}
					indicator(g, x + 2, y + 4, q.multiSelect(), sel);
					int ly = y + 3;
					String s = o.label();
					for (ComposerText.Line l : labelLines) {
						g.text(font, s.substring(l.start(), l.end()), textX, ly + 1, sel ? ComposerSkin.TEXT : LABEL, false);
						ly += LINE_H;
					}
					String ds = o.description();
					for (ComposerText.Line l : descLines) {
						g.text(font, ds.substring(l.start(), l.end()), textX, ly, ComposerSkin.MUTED, false);
						ly += SMALL_H;
					}
					if (i < 9) {
						keycap(g, font, String.valueOf(i + 1), x + w - 13, y + 3);
					}
					if (!locked) {
						addHit(row, () -> choose(req, idx), null);
					}
				}
				y += rowH + 1;
			}
			if (q.allowCustomAnswer()) {
				y += 3;
				int lines = otherLines(font, w - 12);
				int fh = 6 + lines * LINE_H;
				Rect field = new Rect(x, y, w, fh);
				if (g != null) {
					boolean hover = field.contains(mouseX, mouseY) && body.contains(mouseX, mouseY);
					ComposerSkin.box(g, field.x(), field.y(), field.w(), field.h(), MONO_BG,
							otherFocused ? ComposerSkin.BORDER_FOCUS : hover ? 0xFF3A3A42 : ComposerSkin.BORDER);
					drawOther(g, font, q, field, locked);
					if (field.y() >= body.y() && field.bottom() <= body.bottom()) {
						otherRect = field;
					}
					if (!locked) {
						addHit(field, this::focusOther, null);
					}
				}
				y += fh;
			}
			PendingRequest.Option preview = hoverOpt != null && hoverOpt.preview() != null ? hoverOpt : selectedPreview(d, q);
			if (g == null) {
				// measure with the tallest preview so the card does not jump while hovering
				preview = tallestPreview(font, q, w);
			}
			if (preview != null && preview.preview() != null && !preview.preview().isBlank()) {
				y += 5;
				y = monoBox(g, font, preview.preview(), x, y, w, preview.label());
			}
			if (!req.canRespond()) {
				y += 4;
				y = paragraph(g, font, "The agent can no longer take an answer to this question (its run ended).", x, y, w, ComposerSkin.WARN, SMALL_H,
						measure);
			}
			return y - y0 + 3;
		}
		// ---- approval
		PendingRequest.Approval a = req.approval();
		y += 2;
		if (a != null && a.detail() != null) {
			y = monoBox(g, font, a.detail(), x, y, w, null);
			y += 4;
		}
		if (a != null && a.reason() != null) {
			y = paragraph(g, font, a.reason(), x, y, w, ComposerSkin.MUTED, SMALL_H, measure);
			y += 4;
		}
		if (a == null || a.detail() == null && a.reason() == null) {
			y = paragraph(g, font, "The agent asks to " + req.title().replace("Approval · ", "").toLowerCase(java.util.Locale.ROOT) + ".", x, y, w,
					ComposerSkin.TEXT, LINE_H, measure);
			y += 4;
		}
		boolean locked = sendingId != null || !req.canRespond();
		List<PendingRequest.ApprovalOption> opts = req.approvalOptions();
		for (int i = 0; i < opts.size(); i++) {
			PendingRequest.ApprovalOption o = opts.get(i);
			int idx = i;
			int textX = x + 16;
			int textW = w - 16 - 20;
			List<ComposerText.Line> warn = o.warning() == null ? List.of() : ComposerText.wrap(o.warning(), Math.max(20, textW), measure);
			int rowH = 3 + LINE_H + warn.size() * SMALL_H + 3;
			Rect row = new Rect(x - 3, y, w + 3, rowH);
			if (g != null) {
				boolean hover = row.contains(mouseX, mouseY) && body.contains(mouseX, mouseY) && !locked;
				if (hover) {
					ComposerSkin.round(g, row.x(), row.y(), row.w(), row.h(), ComposerSkin.HOVER);
				}
				boolean ok = RequestCommands.approves(o.decision());
				g.text(font, ok ? "✔" : "✖", x + 2, y + 4, ok ? 0xFF6EE7B7 : 0xFFFCA5A5, false);
				g.text(font, ChatDraw.clip(font, o.label(), textW), textX, y + 4, LABEL, false);
				int ly = y + 3 + LINE_H;
				for (ComposerText.Line l : warn) {
					g.text(font, o.warning().substring(l.start(), l.end()), textX, ly, ComposerSkin.WARN, false);
					ly += SMALL_H;
				}
				if (i < 9) {
					keycap(g, font, String.valueOf(i + 1), x + w - 13, y + 3);
				}
				if (!locked) {
					addHit(row, () -> choose(req, idx), null);
				}
			}
			y += rowH + 1;
		}
		if (!req.canRespond()) {
			y += 4;
			y = paragraph(g, font, "The agent can no longer take an answer to this request (its run ended).", x, y, w, ComposerSkin.WARN, SMALL_H,
					measure);
		}
		return y - y0 + 3;
	}

	/** Only rows inside the visible body are clickable. */
	private void addHit(Rect r, Runnable action, @Nullable String tip) {
		int top = Math.max(r.y(), body.y());
		int bottom = Math.min(r.bottom(), body.bottom());
		if (bottom > top) {
			hits.add(new Hit(new Rect(r.x(), top, r.w(), bottom - top), action, tip));
		}
	}

	private static int paragraph(@Nullable GuiGraphicsExtractor g, Font font, String text, int x, int y, int w, int color, int lineH,
			ToIntFunction<String> measure) {
		if (text == null || text.isBlank()) {
			return y;
		}
		for (ComposerText.Line l : ComposerText.wrap(text, Math.max(20, w), measure)) {
			if (g != null) {
				g.text(font, text.substring(l.start(), l.end()), x, y + 1, color, false);
			}
			y += lineH;
		}
		return y;
	}

	/** Text in the monospace font in a dark inset box (a preview, an approval's command). */
	private int monoBox(@Nullable GuiGraphicsExtractor g, Font font, String text, int x, int y, int w, @Nullable String caption) {
		ToIntFunction<String> mono = s -> font.width(Component.literal(s).withStyle(MONO));
		List<ComposerText.Line> lines = ComposerText.wrap(text.stripTrailing(), Math.max(20, w - 10), mono);
		boolean cut = lines.size() > PREVIEW_MAX_LINES;
		int n = Math.min(lines.size(), PREVIEW_MAX_LINES);
		int capH = caption == null ? 0 : SMALL_H + 1;
		int h = 6 + capH + n * SMALL_H + (cut ? SMALL_H : 0);
		if (g != null) {
			ComposerSkin.box(g, x, y, w, h, MONO_BG, ComposerSkin.BORDER);
			int ly = y + 3;
			if (caption != null) {
				g.text(font, ChatDraw.clip(font, "Preview · " + caption, w - 10), x + 5, ly, ComposerSkin.PLACEHOLDER, false);
				ly += capH;
			}
			String s = text.stripTrailing();
			for (int i = 0; i < n; i++) {
				ComposerText.Line l = lines.get(i);
				g.text(font, Component.literal(s.substring(l.start(), l.end())).withStyle(MONO), x + 5, ly, 0xFFD4D4DC, false);
				ly += SMALL_H;
			}
			if (cut) {
				g.text(font, "… " + (lines.size() - n) + " more lines", x + 5, ly, ComposerSkin.PLACEHOLDER, false);
			}
		}
		return y + h;
	}

	private static PendingRequest.@Nullable Option selectedPreview(RequestDraft d, PendingRequest.Question q) {
		for (PendingRequest.Option o : q.options()) {
			if (o.preview() != null && !o.preview().isBlank() && d.selected(q, o)) {
				return o;
			}
		}
		return null;
	}

	private PendingRequest.@Nullable Option tallestPreview(Font font, PendingRequest.Question q, int w) {
		PendingRequest.Option best = null;
		int bestH = 0;
		for (PendingRequest.Option o : q.options()) {
			if (o.preview() == null || o.preview().isBlank()) {
				continue;
			}
			int h = monoBox(null, font, o.preview(), 0, 0, w, o.label());
			if (h > bestH) {
				bestH = h;
				best = o;
			}
		}
		return best;
	}

	private static void indicator(GuiGraphicsExtractor g, int x, int y, boolean checkbox, boolean on) {
		if (checkbox) {
			ComposerSkin.box(g, x, y, 8, 8, on ? ComposerSkin.SEND : MONO_BG, on ? ComposerSkin.SEND_HOVER : 0xFF4B4B55);
			if (on) {
				// ✓ with fills
				g.fill(x + 2, y + 4, x + 3, y + 5, 0xFFFFFFFF);
				g.fill(x + 3, y + 5, x + 4, y + 6, 0xFFFFFFFF);
				g.fill(x + 4, y + 4, x + 5, y + 5, 0xFFFFFFFF);
				g.fill(x + 5, y + 3, x + 6, y + 4, 0xFFFFFFFF);
			}
			return;
		}
		ComposerSkin.circle(g, x, y, 8, on ? ComposerSkin.SEND : 0xFF4B4B55);
		ComposerSkin.circle(g, x + 1, y + 1, 6, on ? ComposerSkin.SEND : MONO_BG);
		if (on) {
			ComposerSkin.circle(g, x + 2, y + 2, 4, 0xFFFFFFFF);
		}
	}

	private static void keycap(GuiGraphicsExtractor g, Font font, String k, int x, int y) {
		ComposerSkin.box(g, x, y, 11, 11, 0xFF222228, ComposerSkin.BORDER);
		g.text(font, k, x + (11 - font.width(k)) / 2 + 1, y + 2, ComposerSkin.MUTED, false);
	}

	private int otherLines(Font font, int w) {
		other.layout(Math.max(20, w), font::width);
		return Math.clamp(other.lines().size(), 1, OTHER_MAX_LINES);
	}

	private void drawOther(GuiGraphicsExtractor g, Font font, PendingRequest.Question q, Rect field, boolean locked) {
		int tx = field.x() + 5;
		int ty = field.y() + 3;
		int tw = field.w() - 12;
		other.layout(Math.max(20, tw), font::width);
		if (other.isEmpty()) {
			String ph = q.options().isEmpty() ? "Type your answer…" : "Other: type your own answer… (Tab)";
			g.text(font, ChatDraw.clip(font, ph, tw), tx, ty + 1, ComposerSkin.PLACEHOLDER, false);
			if (otherFocused && ChatDraw.caretOn()) {
				g.fill(tx, ty, tx + 1, ty + 9, ComposerSkin.TEXT);
			}
			return;
		}
		String s = other.text();
		List<ComposerText.Line> lines = other.lines();
		int caretLine = other.lineOf(other.caret());
		int first = Math.max(0, Math.min(caretLine - OTHER_MAX_LINES + 1, lines.size() - OTHER_MAX_LINES));
		first = Math.max(0, first);
		int selA = other.selectionStart();
		int selB = other.selectionEnd();
		for (int i = first; i < Math.min(lines.size(), first + OTHER_MAX_LINES); i++) {
			ComposerText.Line l = lines.get(i);
			int ly = ty + (i - first) * LINE_H;
			String row = s.substring(l.start(), l.end());
			int a = Math.max(selA, l.start());
			int b = Math.min(selB, l.end());
			if (otherFocused && a < b) {
				int x0 = tx + font.width(s.substring(l.start(), a));
				int x1 = tx + font.width(s.substring(l.start(), b));
				g.fill(x0, ly, x1, ly + 9, ComposerSkin.SELECTION);
			}
			g.text(font, row, tx, ly + 1, locked ? ComposerSkin.MUTED : ComposerSkin.TEXT, false);
			if (otherFocused && i == caretLine && ChatDraw.caretOn()) {
				int cx = tx + other.caretX();
				g.fill(cx, ly, cx + 1, ly + 9, ComposerSkin.TEXT);
			}
		}
	}

	private void drawBar(GuiGraphicsExtractor g, Font font, PendingRequest req, @Nullable RequestDraft d, int x, int y, int w, int mouseX,
			int mouseY) {
		int right = x + w;
		int by = y + (BAR_H - BUTTON_H) / 2;
		String hint;
		if (!canOperate()) {
			right -= button(g, font, "Enable control", right, by, true, true, mouseX, mouseY, () -> {
				if (envId != null) {
					host.enableControl(envId);
				}
			}, "Answering needs control (operate) of this environment") + 4;
			hint = "Read-only: enable control to answer from here";
		} else if (sendingId != null) {
			int bw = 34;
			ComposerSkin.round(g, right - bw, by, bw, BUTTON_H, ComposerSkin.DISABLED_BG);
			ComposerSkin.busyDots(g, right - bw + (bw - BUTTON_H) / 2, by, BUTTON_H, ComposerSkin.TEXT, ComposerSkin.DISABLED_FG);
			right -= bw + 4;
			hint = "Sending…";
		} else if (!req.canRespond()) {
			hint = "Answer it in T3 Code, or send a new message";
		} else if (d != null) {
			PendingRequest.Question q = d.active();
			boolean answered = q != null && d.answered(q);
			boolean last = d.last();
			boolean ready = last ? d.complete() : answered;
			right -= button(g, font, last ? "Send ↵" : "Next ›", right, by, true, ready, mouseX, mouseY, this::advance,
					last ? "Send the answer (Enter)" : "Next question (Enter)") + 4;
			if (d.index() > 0) {
				right -= button(g, font, "‹ Back", right, by, false, true, mouseX, mouseY, () -> step(-1), "Previous question (←)") + 4;
			}
			if (req.dismissible()) {
				right -= button(g, font, "Dismiss", right, by, false, true, mouseX, mouseY, () -> dismiss(req),
						"Leave the question unanswered") + 4;
			}
			hint = q != null && q.options().isEmpty() ? "Enter " + (last ? "send" : "next") + " · Esc hide"
					: "1–9 choose · Enter " + (last ? "send" : "next") + " · Esc hide";
		} else {
			String accept = null;
			String decline = null;
			for (PendingRequest.ApprovalOption o : req.approvalOptions()) {
				if ("accept".equals(o.decision())) {
					accept = o.label();
				} else if ("decline".equals(o.decision())) {
					decline = o.label();
				}
			}
			if (accept != null) {
				right -= button(g, font, accept, right, by, true, true, mouseX, mouseY, () -> decide(req, "accept"), "Approve") + 4;
			}
			if (decline != null) {
				right -= button(g, font, decline, right, by, false, true, mouseX, mouseY, () -> decide(req, "decline"), "Decline") + 4;
			}
			hint = "1–9 choose · Esc hide";
		}
		g.text(font, ChatDraw.clip(font, hint, Math.max(0, right - x - 4)), x, by + 3, ComposerSkin.PLACEHOLDER, false);
	}

	/** A bar button whose right edge is {@code right}; returns its width. */
	private int button(GuiGraphicsExtractor g, Font font, String label, int right, int y, boolean primary, boolean enabled, int mouseX, int mouseY,
			Runnable action, @Nullable String tip) {
		int bw = font.width(label) + 12;
		Rect r = new Rect(right - bw, y, bw, BUTTON_H);
		boolean hover = enabled && r.contains(mouseX, mouseY);
		if (primary) {
			ComposerSkin.round(g, r.x(), r.y(), r.w(), r.h(), !enabled ? ComposerSkin.DISABLED_BG : hover ? ComposerSkin.SEND_HOVER : ComposerSkin.SEND);
		} else {
			ComposerSkin.box(g, r.x(), r.y(), r.w(), r.h(), hover ? ComposerSkin.HOVER : ComposerSkin.BOX, ComposerSkin.BORDER);
		}
		int fg = !enabled ? ComposerSkin.DISABLED_FG : primary ? 0xFFFFFFFF : ComposerSkin.TEXT;
		g.text(font, label, r.x() + 6, r.y() + 3, fg, false);
		hits.add(new Hit(r, enabled ? action : () -> {
		}, tip));
		return bw;
	}

	// ---- actions ------------------------------------------------------------------------------

	/** Option {@code i} of the active question, or decision {@code i} of an approval. */
	private void choose(PendingRequest req, int i) {
		if (sendingId != null || !req.canRespond()) {
			return;
		}
		if (!req.isQuestion()) {
			List<PendingRequest.ApprovalOption> opts = req.approvalOptions();
			if (i >= 0 && i < opts.size()) {
				decide(req, opts.get(i).decision());
			}
			return;
		}
		RequestDraft d = draft(req);
		PendingRequest.Question q = d.active();
		if (q == null || i < 0 || i >= q.options().size()) {
			return;
		}
		d.toggle(q, q.options().get(i));
		bindOther(d, true);
		blurOther();
		clearError();
		if (!q.multiSelect() && !d.last()) {
			advanceAt = System.currentTimeMillis() + AUTO_ADVANCE_MS;
			advanceFor = req.requestId() + "/" + q.id();
		}
	}

	private void runAutoAdvance(PendingRequest req, RequestDraft d) {
		if (advanceAt < 0 || System.currentTimeMillis() < advanceAt) {
			return;
		}
		advanceAt = -1;
		PendingRequest.Question q = d.active();
		if (q != null && (req.requestId() + "/" + q.id()).equals(advanceFor) && d.answered(q) && !d.last()) {
			step(1);
		}
	}

	/** Enter / Next / Send: the next question, or the answers out on the last one. */
	public void advance() {
		PendingRequest req = active();
		if (req == null || !req.isQuestion() || sendingId != null || !req.canRespond()) {
			return;
		}
		RequestDraft d = draft(req);
		syncOther(d);
		PendingRequest.Question q = d.active();
		if (q == null) {
			return;
		}
		if (!d.answered(q)) {
			showError(req, q.options().isEmpty() ? "Type an answer first." : "Choose an option or type an answer first.");
			return;
		}
		if (!d.last()) {
			step(1);
			return;
		}
		JsonObject answers = d.answersJson();
		if (answers == null) {
			int miss = d.firstUnanswered();
			if (miss >= 0) {
				d.setIndex(miss);
				bindOther(d, true);
			}
			showError(req, "Answer every question first.");
		} else if (envId == null || !canOperate()) {
			showError(req, "Read-only: enable control (operate) to answer from Minecraft.");
		} else {
			send(req, commands.answer(envId, req, title, answers));
		}
	}

	private void step(int dir) {
		PendingRequest req = active();
		if (req == null || !req.isQuestion()) {
			return;
		}
		RequestDraft d = draft(req);
		syncOther(d);
		d.setIndex(d.index() + dir);
		bindOther(d, true);
		blurOther();
		scroll = 0;
		advanceAt = -1;
		clearError();
		PendingRequest.Question q = d.active();
		if (q != null && q.options().isEmpty() && q.allowCustomAnswer()) {
			focusOther(); // a free-text question: type right away
		}
	}

	private void decide(PendingRequest req, String decision) {
		if (sendingId != null || !req.canRespond()) {
			return;
		}
		if (envId == null || !canOperate()) {
			showError(req, "Read-only: enable control (operate) to answer from Minecraft.");
			return;
		}
		send(req, commands.decide(envId, req, title, decision, null));
	}

	private void dismiss(PendingRequest req) {
		if (sendingId != null || envId == null || !canOperate()) {
			return;
		}
		send(req, commands.dismiss(envId, req, title));
	}

	private void send(PendingRequest req, OperateAction action) {
		String id = req.requestId();
		sendingId = id;
		clearError();
		blurOther();
		host.run(action, r -> {
			if (id.equals(sendingId)) {
				sendingId = null;
			}
			if (r.ok()) {
				answered.add(id);
				advanceAt = -1;
				scroll = 0;
			} else if (r.outcome() != OperateResult.Outcome.CANCELLED) {
				showError(req, RequestCommands.failure(r)); // the choices stay in the draft
			}
		});
	}

	private void showError(PendingRequest req, String message) {
		error = message;
		errorFor = req.requestId();
		collapsed = false;
	}

	private void clearError() {
		error = null;
		errorFor = null;
	}

	// ---- "Other" field ------------------------------------------------------------------------

	private void bindOther(RequestDraft d, boolean force) {
		PendingRequest.Question q = d.active();
		String key = q == null ? null : d.request().requestId() + "/" + q.id();
		if (force || !Objects.equals(key, otherBound)) {
			otherBound = key;
			other.set(q == null ? "" : d.custom(q));
			other.clearHistory();
		}
	}

	private void syncOther(RequestDraft d) {
		PendingRequest.Question q = d.active();
		if (q != null && Objects.equals(otherBound, d.request().requestId() + "/" + q.id()) && q.allowCustomAnswer()) {
			d.setCustom(q, other.text());
		}
	}

	public void focusOther() {
		PendingRequest req = active();
		if (req == null || !req.isQuestion() || sendingId != null || !req.canRespond()) {
			return;
		}
		PendingRequest.Question q = draft(req).active();
		if (q == null || !q.allowCustomAnswer()) {
			return;
		}
		otherFocused = true;
		textInput.focusChanged(this, true);
	}

	public void blurOther() {
		if (otherFocused) {
			otherFocused = false;
			textInput.focusChanged(this, false);
		}
	}

	// ---- input --------------------------------------------------------------------------------

	public boolean mouseClicked(double mx, double my, int button) {
		if (!MouseButtons.isLeft(button)) {
			return contains(mx, my);
		}
		if (!expanded()) {
			if (strip.contains(mx, my)) {
				expand();
				return true;
			}
			return false;
		}
		if (!box.contains(mx, my)) {
			blurOther();
			return false;
		}
		boolean onOther = otherRect.contains(mx, my);
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (h.r().contains(mx, my)) {
				if (!onOther) {
					blurOther();
				}
				h.action().run();
				if (onOther) {
					placeCaret(mx, my);
				}
				return true;
			}
		}
		blurOther();
		return true;
	}

	private void placeCaret(double mx, double my) {
		if (!otherFocused || otherRect.h() == 0) {
			return;
		}
		List<ComposerText.Line> lines = other.lines();
		int caretLine = other.lineOf(other.caret());
		int first = Math.max(0, Math.min(caretLine - OTHER_MAX_LINES + 1, lines.size() - OTHER_MAX_LINES));
		int line = Math.clamp(first + (int) ((my - otherRect.y() - 3) / LINE_H), 0, lines.size() - 1);
		other.setCaret(other.positionAt(line, (int) (mx - otherRect.x() - 5)), false);
	}

	public boolean mouseScrolled(double mx, double my, double dy) {
		if (!expanded() || !box.contains(mx, my)) {
			return false;
		}
		if (contentH > body.h()) {
			scroll = Math.clamp(scroll - (int) Math.round(dy * 12), 0, Math.max(0, contentH - body.h()));
		}
		return true;
	}

	/**
	 * Keys of the open card. The "Other" field, when focused, takes every key (Esc / Tab leave it,
	 * Enter goes on). Otherwise: 1–9 choose, Enter next / send, ←/→ question, Tab the field, ↑/↓
	 * scroll, Esc hides the card. Other keys are left to the screen.
	 */
	public boolean keyPressed(int key, boolean shift, boolean ctrl) {
		if (!shown()) {
			return false;
		}
		PendingRequest req = active();
		if (req == null) {
			return false;
		}
		RequestDraft d = req.isQuestion() ? draft(req) : null;
		if (otherFocused && d != null) {
			if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_TAB) {
				syncOther(d);
				blurOther();
				return true;
			}
			ComposerText.KeyResult r = other.key(key, shift, ctrl, clipboard);
			if (r == ComposerText.KeyResult.SUBMIT) {
				syncOther(d);
				advance();
				return true;
			}
			if (r == ComposerText.KeyResult.HANDLED) {
				syncOther(d);
				clearError();
				return true;
			}
			return false;
		}
		if (key == InputConstants.KEY_ESCAPE) {
			collapse();
			return true;
		}
		if (ctrl) {
			return false;
		}
		int digit = digit(key);
		if (digit >= 1) {
			choose(req, digit - 1);
			return true;
		}
		switch (key) {
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
				if (d != null) {
					advance();
					return true;
				}
				return false;
			}
			case InputConstants.KEY_TAB -> {
				if (d != null) {
					focusOther();
					return true;
				}
				return false;
			}
			case InputConstants.KEY_LEFT -> {
				if (d != null && d.index() > 0) {
					step(-1);
					return true;
				}
				return false;
			}
			case InputConstants.KEY_RIGHT -> {
				if (d != null && !d.last()) {
					PendingRequest.Question q = d.active();
					if (q != null && d.answered(q)) {
						step(1);
					}
					return true;
				}
				return false;
			}
			case InputConstants.KEY_UP, InputConstants.KEY_DOWN -> {
				if (contentH > body.h()) {
					scroll = Math.clamp(scroll + (key == InputConstants.KEY_UP ? -12 : 12), 0, Math.max(0, contentH - body.h()));
					return true;
				}
				return false;
			}
			default -> {
				return false;
			}
		}
	}

	/** A typed character: only the focused "Other" field takes them. */
	public boolean charTyped(int codepoint) {
		if (!owns()) {
			return false;
		}
		PendingRequest req = active();
		if (req == null || !req.isQuestion()) {
			return false;
		}
		if (other.typed(codepoint)) {
			syncOther(draft(req));
			clearError();
		}
		return true;
	}

	/** 1–9 on the top row or the keypad, else -1. */
	static int digit(int key) {
		return switch (key) {
			case InputConstants.KEY_1, InputConstants.KEY_NUMPAD1 -> 1;
			case InputConstants.KEY_2, InputConstants.KEY_NUMPAD2 -> 2;
			case InputConstants.KEY_3, InputConstants.KEY_NUMPAD3 -> 3;
			case InputConstants.KEY_4, InputConstants.KEY_NUMPAD4 -> 4;
			case InputConstants.KEY_5, InputConstants.KEY_NUMPAD5 -> 5;
			case InputConstants.KEY_6, InputConstants.KEY_NUMPAD6 -> 6;
			case InputConstants.KEY_7, InputConstants.KEY_NUMPAD7 -> 7;
			case InputConstants.KEY_8, InputConstants.KEY_NUMPAD8 -> 8;
			case InputConstants.KEY_9, InputConstants.KEY_NUMPAD9 -> 9;
			default -> -1;
		};
	}
}
