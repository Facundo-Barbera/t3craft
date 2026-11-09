package dev.t3craft.client.blocks;

import com.mojang.blaze3d.Blaze3D;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * The list a merge station opens: the threads of its binding that have a pull request waiting (open first, then
 * drafts), one row each. The wide button opens the PR in the browser (through the chat's link rules: off in the chat
 * settings → copied; "prompt on links" → vanilla confirmation; only {@code http(s)}), the small one opens the
 * thread's chat. Nothing here merges: the mod is read only, the review happens in the browser or in T3.
 *
 * <p>Re-reads {@code source} every tick (cheap: {@link BlocksClient} memoizes per refresh) and rebuilds the rows
 * only when the list changed.
 */
final class MergeStationScreen extends Screen {
	private static final int ROW = 24;
	private static final int CHAT_W = 44;
	private static final SystemToast.SystemToastId COPIED = new SystemToast.SystemToastId(2000L);

	private final Supplier<MergeFeed.Summary> source;
	private final Consumer<MergeFeed.Entry> openChat;
	private final String bindingLabel;
	private final @Nullable Screen parent;
	private MergeFeed.Summary shown = MergeFeed.Summary.EMPTY;
	private int page;

	MergeStationScreen(@Nullable Screen parent, String bindingLabel, Supplier<MergeFeed.Summary> source, Consumer<MergeFeed.Entry> openChat) {
		super(Component.translatable("t3craft.merge.title"));
		this.parent = parent;
		this.bindingLabel = bindingLabel;
		this.source = source;
		this.openChat = openChat;
	}

	private int listWidth() {
		return Math.min(460, width - 32);
	}

	private int perPage() {
		return Math.max(1, (height - 36 - 44) / ROW);
	}

	@Override
	protected void init() {
		shown = source.get();
		List<MergeFeed.Entry> rows = shown.pending();
		int per = perPage();
		int pages = Math.max(1, (rows.size() + per - 1) / per);
		page = Math.min(page, pages - 1);
		int w = listWidth();
		int x = width / 2 - w / 2;
		int from = page * per;
		for (int i = from; i < Math.min(rows.size(), from + per); i++) {
			MergeFeed.Entry e = rows.get(i);
			int y = 36 + (i - from) * ROW;
			String link = e.link();
			Button pr = Button.builder(rowLabel(e), b -> {
				String url = e.link();
				if (url != null) {
					openWeb(url);
				} else {
					openChat.accept(e);
				}
			}).bounds(x, y, w - CHAT_W - 4, 20).build();
			addRenderableWidget(pr);
			addRenderableWidget(Button.builder(Component.translatable("t3craft.merge.chat"), b -> openChat.accept(e))
					.bounds(x + w - CHAT_W, y, CHAT_W, 20).build());
			if (link == null) {
				pr.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("t3craft.merge.no_link")));
			}
		}
		int by = height - 28;
		Button prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
			page--;
			rebuildWidgets();
		}).bounds(width / 2 - 104, by, 20, 20).build());
		prev.active = page > 0;
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 80, by, 160, 20).build());
		Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
			page++;
			rebuildWidgets();
		}).bounds(width / 2 + 84, by, 20, 20).build());
		next.active = page < pages - 1;
	}

	/** {@code #123  Thread title   +N −M}, the number in the state's colour, drafts marked. */
	private static Component rowLabel(MergeFeed.Entry e) {
		int color = MergeFeed.color(e.state());
		MutableComponent c = Component.literal(e.label() + " ").withStyle(s -> s.withColor(color));
		if (e.state() == dev.t3craft.blocks.MergeState.DRAFT) {
			c.append(Component.translatable("t3craft.merge.draft").append(" ").withStyle(ChatFormatting.GOLD));
		}
		c.append(Component.literal(e.title().isBlank() ? "(untitled)" : e.title()).withStyle(ChatFormatting.WHITE));
		if (e.additions() >= 0 || e.deletions() >= 0) {
			c.append(Component.literal("   "));
			if (e.additions() >= 0) {
				c.append(Component.literal("+" + e.additions()).withStyle(ChatFormatting.GREEN));
			}
			if (e.deletions() >= 0) {
				c.append(Component.literal(" −" + e.deletions()).withStyle(ChatFormatting.RED));
			}
		}
		return c;
	}

	@Override
	public void tick() {
		MergeFeed.Summary now = source.get();
		if (!now.equals(shown)) {
			rebuildWidgets();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
		g.centeredText(font, Component.literal(bindingLabel).withStyle(ChatFormatting.GRAY), width / 2, 24, 0xFFFFFFFF);
		if (shown.pending().isEmpty()) {
			Component none = Component.translatable(shown.state() == dev.t3craft.blocks.MergeState.MERGED ? "t3craft.merge.all_merged" : "t3craft.merge.none")
					.withStyle(ChatFormatting.GRAY);
			g.centeredText(font, none, width / 2, 52, 0xFFFFFFFF);
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
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
				Component.literal(url.length() > 60 ? url.substring(0, 59) + "…" : url));
	}
}
