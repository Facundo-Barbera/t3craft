package dev.t3craft.client.blocks;

import com.mojang.blaze3d.Blaze3D;
import dev.t3craft.blocks.MergeState;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Control;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Icon;
import dev.t3craft.client.t3ui.kit.T3List;
import dev.t3craft.client.t3ui.kit.T3Panel;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * What a merge station opens, as a page of the UI kit (docs/features/ui-kit.md): the threads of its binding
 * that have a pull request waiting (open first, then drafts), one {@link T3List} row each with two buttons.
 * <b>Open</b> opens the PR in the browser through the chat's link rules (links off in the chat settings:
 * copied; "prompt on links": vanilla confirmation; only {@code http(s)}); it is disabled when T3 gave no link.
 * <b>Chat</b> opens the thread's chat. Enter on the keyboard cursor does what Open does, or opens the chat
 * when there is no link. Nothing here merges: the mod is read only, the review happens in the browser or in T3.
 *
 * <p>Re-reads {@code source} every tick (cheap: {@link BlocksClient} memoizes per refresh) and lays the page
 * out again only when the list changed.
 */
public final class MergeStationPanel extends T3Panel {
	private static final int ROW_H = 20;
	private static final int GAP = 4;
	private static final SystemToast.SystemToastId COPIED = new SystemToast.SystemToastId(2000L);

	private final Supplier<MergeFeed.Summary> source;
	private final Consumer<MergeFeed.Entry> openChat;
	private final String bindingLabel;
	private final T3List<MergeFeed.Entry> list;
	/** The Open and Chat buttons of each row (by entry key), made when the page is arranged. */
	private final Map<String, List<T3Button>> buttons = new HashMap<>();
	private MergeFeed.Summary shown = MergeFeed.Summary.EMPTY;
	private int openW = 60;
	private int chatW = 56;

	public MergeStationPanel(String bindingLabel, Supplier<MergeFeed.Summary> source, Consumer<MergeFeed.Entry> openChat) {
		this.bindingLabel = bindingLabel;
		this.source = source;
		this.openChat = openChat;
		this.list = new T3List<>(ROW_H, new T3List.Row<MergeFeed.Entry>() {
			@Override
			public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, MergeFeed.Entry e, Rect r, int mx, int my) {
				drawRow(g, font, t, e, r, mx, my);
			}

			@Override
			public boolean mouseClicked(MergeFeed.Entry e, Rect r, double mx, double my, int button) {
				for (T3Button b : buttons.getOrDefault(e.key(), List.of())) {
					if (b.mouseClicked(mx, my, button)) {
						return true;
					}
				}
				return false;
			}

			@Override
			public void activated(MergeFeed.Entry e) {
				String url = e.link();
				if (url != null) {
					openWeb(url);
				} else {
					openChat.accept(e);
				}
			}

			@Override
			public @Nullable List<String> tooltip(MergeFeed.Entry e, Rect r, double mx, double my) {
				for (T3Button b : buttons.getOrDefault(e.key(), List.of())) {
					List<String> tip = b.tooltipAt(mx, my);
					if (tip != null) {
						return tip;
					}
				}
				return e.title().isBlank() ? null : List.of(e.title());
			}
		});
	}

	@Override
	public String title() {
		return T3Draw.tr("t3craft.merge.title");
	}

	@Override
	protected int headerHeight() {
		return bindingLabel.isBlank() ? 0 : 12;
	}

	@Override
	protected void arrangeHeader(Rect header) {
		if (!bindingLabel.isBlank()) {
			pinText(header.x() + 2, header.y() + 1, ChatDraw.clip(font(), bindingLabel, header.w() - 4), Tone.MUTED);
		}
	}

	@Override
	public void tick() {
		if (!source.get().equals(shown)) {
			host().relayout();
		}
	}

	@Override
	protected int arrange(Rect body, int y0) {
		shown = source.get();
		List<MergeFeed.Entry> rows = shown.pending();
		openW = new T3Button(T3Icon.EXTERNAL_LINK, T3Draw.tr("t3craft.merge.open"), () -> {
		}).preferredWidth(font());
		chatW = new T3Button(T3Icon.MESSAGE_SQUARE, T3Draw.tr("t3craft.merge.chat"), () -> {
		}).preferredWidth(font());
		buttons.clear();
		for (MergeFeed.Entry e : rows) {
			T3Button open = new T3Button(T3Icon.EXTERNAL_LINK, T3Draw.tr("t3craft.merge.open"), () -> {
				String url = e.link();
				if (url != null) {
					openWeb(url);
				}
			});
			if (e.link() == null) {
				open.setEnabled(false);
				open.setTooltip(T3Draw.tr("t3craft.merge.no_link"));
			} else {
				open.setTooltip(e.link());
			}
			T3Button chat = new T3Button(T3Icon.MESSAGE_SQUARE, T3Draw.tr("t3craft.merge.chat"), () -> openChat.accept(e));
			buttons.put(e.key(), List.of(open, chat));
		}
		list.setItems(rows);
		list.setEmptyText(T3Draw.tr(shown.state() == MergeState.MERGED ? "t3craft.merge.all_merged" : "t3craft.merge.none"));
		place(list, body.x(), y0, body.w(), body.h());
		return y0 + body.h(); // the list scrolls inside itself
	}

	// ---- drawing ------------------------------------------------------------------------------------------

	/** {@code #123 [draft] Thread title   +N -M}, the number in the state's colour; the buttons on the right. */
	private void drawRow(GuiGraphicsExtractor g, Font font, ChatTheme t, MergeFeed.Entry e, Rect r, int mx, int my) {
		int ty = r.y() + (r.h() - 8) / 2;
		int bx = r.right() - openW - chatW - GAP - 2;
		int x = r.x() + 4;
		int stateColour = e.state() == MergeState.DRAFT ? t.youText() : t.result();
		String label = ChatDraw.clip(font, e.label(), Math.max(0, (bx - x) / 3));
		g.text(font, label, x, ty, stateColour, t.shadow());
		x += font.width(label) + 6;
		if (e.state() == MergeState.DRAFT) {
			String draft = T3Draw.tr("t3craft.merge.draft");
			g.text(font, draft, x, ty, t.youText(), t.shadow());
			x += font.width(draft) + 6;
		}
		String add = e.additions() >= 0 ? "+" + e.additions() : "";
		String del = e.deletions() >= 0 ? "-" + e.deletions() : "";
		int diffW = (add.isEmpty() ? 0 : font.width(add)) + (del.isEmpty() ? 0 : font.width(del) + 4);
		String title = e.title().isBlank() ? "(untitled)" : e.title();
		String shownTitle = ChatDraw.clip(font, title, Math.max(0, bx - x - diffW - 10));
		g.text(font, shownTitle, x, ty, t.text(), t.shadow());
		int dx = bx - 6 - diffW;
		if (!add.isEmpty()) {
			g.text(font, add, dx, ty, t.addFg(), t.shadow());
			dx += font.width(add) + 4;
		}
		if (!del.isEmpty()) {
			g.text(font, del, dx, ty, t.delFg(), t.shadow());
		}
		int by = r.y() + (r.h() - T3Control.H) / 2;
		List<T3Button> bs = buttons.getOrDefault(e.key(), List.of());
		int[] widths = {openW, chatW};
		for (int i = 0; i < bs.size(); i++) {
			bs.get(i).setBounds(new Rect(bx, by, widths[i], T3Control.H));
			bs.get(i).render(g, font, t, mx, my);
			bx += widths[i] + GAP;
		}
		g.fill(r.x() + 2, r.bottom() - 1, r.right() - 2, r.bottom(), t.edge());
	}

	// ---- links (the chat's rules, see ConversationPanel.openWeb) ----------------------------------

	/** Opens {@code url} in the browser if it is {@code http(s)}; otherwise (or when chat links are off) copies it. */
	private void openWeb(String url) {
		Minecraft mc = Minecraft.getInstance();
		URI uri;
		try {
			uri = new URI(url);
		} catch (URISyntaxException e) {
			copyLink(mc, url);
			return;
		}
		String scheme = uri.getScheme();
		if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
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
		SystemToast.addOrUpdate(mc.gui.toastManager(), COPIED, Component.translatable("t3craft.merge.link_copied"),
				Component.literal(url.length() > 60 ? url.substring(0, 57) + "..." : url));
	}
}
