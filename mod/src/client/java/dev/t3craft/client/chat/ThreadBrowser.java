// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
// Navigation follows AgentCraft's decision screen (Tab / Shift+Tab step through the queue, ‹ n/N ›
// in the header, Esc backs out one level) and its console (PgUp/PgDn, End to the newest line).
package dev.t3craft.client.chat;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Reusable widget: the unified thread list and the conversation of the selected thread, in any
 * rectangle with any {@link ChatTheme}. Not tied to a Screen: a host (a full screen, the portable
 * terminal's frame, a HUD panel) gives it bounds and forwards tick, render and input.
 *
 * <pre>{@code
 * ThreadBrowser b = new ThreadBrowser(ChatScreens.defaultDirectory(), ChatScreens.defaultSource(),
 *         ChatTheme.terminal(), ChatScreens.configFilterStore());
 * b.open("vps:thr_123");                      // optional preselection
 * // tick:    b.tick();
 * // render:  b.setBounds(new Rect(x, y, w, h)); b.render(g, font, mouseX, mouseY);
 * // input:   b.mouseClicked(mx, my, button); b.mouseScrolled(mx, my, dy);
 * //          if (!b.keyPressed(event)) { host handles it (Esc closes) }
 * // removed: b.close();
 * }</pre>
 *
 * <p><b>Modes.</b> {@link Mode#SPLIT}: list panel on the left (30 % of the width, 130..200 px),
 * conversation on the right. {@link Mode#COMPACT}: one panel at a time, for small hosts: the list;
 * Enter or a click opens the conversation (compact header, no footer, ‹ n/N › to step); Esc or
 * Backspace goes back to the list (then {@link #keyPressed} returns false and the host may close).
 * {@link Mode#AUTO} (default) is COMPACT below {@value #COMPACT_W}×{@value #COMPACT_H} px, else SPLIT.
 *
 * <p><b>Keys</b> (SPLIT): ↑/↓ or Tab/Shift+Tab select the previous/next thread and open it,
 * PgUp/PgDn/Home/End scroll the conversation. Clicks: rows open, chips filter (click toggles,
 * right-click solo, "All").
 */
public final class ThreadBrowser implements AutoCloseable {
	public static final int COMPACT_W = BrowserLayout.COMPACT_W;
	public static final int COMPACT_H = BrowserLayout.COMPACT_H;

	public enum Mode {
		AUTO, SPLIT, COMPACT
	}

	/** Persists the environment filter (hidden environment ids). */
	public interface FilterStore {
		Set<String> load();

		void save(Set<String> hidden);
	}

	private final ThreadDirectory directory;
	private final ThreadList model = new ThreadList();
	private final ThreadListView list;
	private final ConversationPanel conversation;
	private final @Nullable FilterStore store;
	private ChatTheme theme;
	private Rect bounds = Rect.EMPTY;
	private Mode mode = Mode.AUTO;
	/** Compact mode: showing the conversation (else the list). */
	private boolean compactConversation;
	private boolean autoSelected;

	public ThreadBrowser(ThreadDirectory directory, ChatSource source, ChatTheme theme, @Nullable FilterStore store) {
		this.directory = directory;
		this.theme = theme;
		this.store = store;
		this.list = new ThreadListView(model, theme);
		this.conversation = new ConversationPanel(source, theme);
		if (store != null) {
			model.setHidden(store.load());
			list.setOnFilterChanged(store::save);
		}
		list.setFocused(true);
		conversation.setNavigation(new ConversationPanel.Navigation() {
			@Override
			public String position() {
				int i = model.selectedIndex();
				int n = model.visible().size();
				return i < 0 || n < 2 ? "" : (i + 1) + "/" + n;
			}

			@Override
			public void step(int dir) {
				ThreadBrowser.this.step(dir);
			}
		});
		refresh();
	}

	// ---- configuration ------------------------------------------------------------------------

	public ThreadList model() {
		return model;
	}

	public ThreadListView list() {
		return list;
	}

	public ConversationPanel conversation() {
		return conversation;
	}

	public ChatTheme theme() {
		return theme;
	}

	public void setTheme(ChatTheme t) {
		theme = t;
		list.setTheme(t);
		conversation.setTheme(t);
	}

	public Rect bounds() {
		return bounds;
	}

	public void setBounds(Rect r) {
		bounds = r;
	}

	public void setMode(Mode m) {
		mode = m;
	}

	/** The mode in effect for the current bounds. */
	public Mode effectiveMode() {
		if (mode != Mode.AUTO) {
			return mode;
		}
		return BrowserLayout.isCompact(bounds) ? Mode.COMPACT : Mode.SPLIT;
	}

	/** Selects and opens a thread by key ({@code env:threadId}), even before it is listed. */
	public void open(String key) {
		open(key, "", "");
	}

	/**
	 * Like {@link #open(String)}; {@code envLabel} and {@code title} fill the header when the thread
	 * is not listed (an archived thread).
	 */
	public void open(String key, String envLabel, String title) {
		model.select(key);
		autoSelected = true;
		ThreadRow r = model.find(key);
		if (r != null) {
			showRow(r);
		} else {
			int colon = key.indexOf(':');
			if (colon > 0 && colon < key.length() - 1) {
				conversation.show(key.substring(0, colon), envLabel, key.substring(colon + 1), title);
			}
		}
		compactConversation = true;
		list.revealSelected();
	}

	// ---- lifecycle ----------------------------------------------------------------------------

	private void refresh() {
		model.update(directory.rows(), directory.environments());
		if (model.all().isEmpty()) {
			list.setEmptyText("No threads yet. Pair an environment with /t3craft pair.");
		} else {
			list.setEmptyText(null);
		}
		if (!autoSelected && !model.visible().isEmpty()) {
			// first fill: open the most urgent thread
			autoSelected = true;
			model.select(model.visible().getFirst().key());
			showRow(model.visible().getFirst());
		}
		if (conversation.key() == null && model.selected() == null) {
			conversation.showNothing(model.all().isEmpty() ? "No threads yet." : "Select a thread.");
		}
	}

	public void tick() {
		refresh();
		conversation.tick();
	}

	@Override
	public void close() {
		conversation.close();
	}

	private void showRow(ThreadRow r) {
		conversation.setBadge(model.badge(r));
		if (r.threadId() == null) {
			conversation.showNothing(net.minecraft.client.resources.language.I18n.get("t3craft.chat.history.bridge"), ChatSource.Unavailable.Action.PAIR);
			return;
		}
		conversation.show(r.env(), r.envLabel(), r.threadId(), r.title());
	}

	/** Previous/next visible thread, opened at once. */
	public void step(int dir) {
		if (model.move(dir >= 0 ? 1 : -1)) {
			ThreadRow r = model.selected();
			if (r != null) {
				showRow(r);
			}
			list.revealSelected();
		}
	}

	// ---- rendering ----------------------------------------------------------------------------

	public void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
		ChatTheme t = theme;
		if (effectiveMode() == Mode.COMPACT) {
			conversation.setCompact(true);
			if (compactConversation) {
				conversation.setHints();
				conversation.setBounds(bounds);
				list.setBounds(Rect.EMPTY);
				conversation.render(g, font, mouseX, mouseY);
			} else {
				list.setBounds(bounds);
				conversation.setBounds(Rect.EMPTY);
				list.render(g, font, mouseX, mouseY);
			}
		} else {
			conversation.setCompact(false);
			conversation.setHints("↑↓", "thread", "PgUp", "scroll", "End", "newest", "Esc", "close");
			Rect[] split = BrowserLayout.split(bounds, t.gap());
			list.setBounds(split[0]);
			conversation.setBounds(split[1]);
			list.render(g, font, mouseX, mouseY);
			conversation.render(g, font, mouseX, mouseY);
		}
		List<String> tip = list.tooltip(mouseX, mouseY);
		if (tip != null) {
			g.setTooltipForNextFrame(font, tip.stream().map(s -> (net.minecraft.util.FormattedCharSequence) Component.literal(s).getVisualOrderText())
					.toList(), mouseX, mouseY);
		}
	}

	// ---- input --------------------------------------------------------------------------------

	public boolean mouseClicked(double mx, double my, int button) {
		if (list.contains(mx, my)) {
			ThreadRow r = list.mouseClicked(mx, my, button);
			if (r != null) {
				showRow(r);
				compactConversation = true;
			}
			return true;
		}
		return conversation.mouseClicked(mx, my, button);
	}

	public boolean mouseScrolled(double mx, double my, double dy) {
		return list.mouseScrolled(mx, my, dy) || conversation.mouseScrolled(mx, my, dy);
	}

	/** Returns false for keys the host should handle (Esc at the top level closes the host). */
	public boolean keyPressed(KeyEvent e) {
		int k = e.key();
		boolean compact = effectiveMode() == Mode.COMPACT;
		if (compact && compactConversation && (e.isEscape() || k == InputConstants.KEY_BACKSPACE)) {
			compactConversation = false;
			list.revealSelected();
			return true;
		}
		if (compact && !compactConversation && (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER)) {
			ThreadRow r = model.selected();
			if (r != null) {
				showRow(r);
				compactConversation = true;
			}
			return true;
		}
		if (k == InputConstants.KEY_TAB) {
			step(e.hasShiftDown() ? -1 : 1);
			return true;
		}
		if (k == InputConstants.KEY_UP || k == InputConstants.KEY_DOWN) {
			int dir = k == InputConstants.KEY_UP ? -1 : 1;
			if (compact && !compactConversation) {
				model.move(dir);
				list.revealSelected();
			} else {
				step(dir);
			}
			return true;
		}
		return conversation.keyPressed(e);
	}
}
