package dev.t3craft.client.config;

import dev.t3craft.client.config.EnvironmentActions.EnvRow;
import dev.t3craft.client.config.T3CraftSettings.EnvPrefs;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Paired T3 environments: status, show/hide, edit (name, colour, filter), unpair, and "Pair new…"
 * (main's read-only pairing screen: paste the link, or the code plus the T3 URL). Paged so it fits
 * any GUI scale. Shows ids, labels, URLs and connection status only: never a token.
 */
public final class EnvironmentsScreen extends BaseConfigScreen {
	private static final int ROW = 24;
	private final EnvironmentActions actions;
	private List<EnvRow> rows = List.of();
	private int page;

	public EnvironmentsScreen(@Nullable Screen parent, EnvironmentActions actions) {
		super(Component.literal("T3 environments"), parent);
		this.actions = actions;
	}

	private int perPage() {
		return Math.max(1, (height - 40 - 90) / ROW);
	}

	private int listWidth() {
		return Math.min(420, width - 32);
	}

	@Override
	protected void init() {
		rows = actions.list();
		int pages = Math.max(1, (rows.size() + perPage() - 1) / perPage());
		page = Math.min(page, pages - 1);
		int w = listWidth();
		int x = width / 2 - w / 2;
		int y = 36;
		T3CraftSettings s = ConfigManager.get().settings();
		int from = page * perPage();
		for (int i = from; i < Math.min(rows.size(), from + perPage()); i++) {
			EnvRow r = rows.get(i);
			EnvPrefs p = s.env(r.id());
			int ry = y + (i - from) * ROW;
			int bx = x + w - 3 * 54;
			addRenderableWidget(Button.builder(Component.literal(p.visible() ? "Hide" : "Show"), b -> {
				ConfigManager.SaveResult res = ConfigManager.get().update(st -> st.withEnv(r.id(), st.env(r.id()).withVisible(!st.env(r.id()).visible())));
				if (res.ok()) {
					actions.reload();
					ok((ConfigManager.get().settings().env(r.id()).visible() ? "Showing " : "Hiding ") + r.label());
				} else {
					error(res.message());
				}
				rebuildWidgets();
			}).bounds(bx, ry, 50, 20).build());
			addRenderableWidget(Button.builder(Component.literal("Edit"),
					b -> minecraft.gui.setScreen(new EnvironmentEditScreen(this, actions, r))).bounds(bx + 54, ry, 50, 20).build());
			addRenderableWidget(Button.builder(Component.literal("Unpair"), b -> confirmUnpair(r)).bounds(bx + 108, ry, 50, 20).build());
		}

		int by = height - 52;
		int bw = Math.min(100, (w - 12) / 4);
		int bx = width / 2 - (bw * 4 + 12) / 2;
		Button prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
			page--;
			rebuildWidgets();
		}).bounds(bx, by - 24, bw, 20).build());
		prev.active = page > 0;
		addRenderableWidget(Button.builder(Component.literal("Pair new…"),
				b -> minecraft.gui.setScreen(actions.pairScreen(this))).bounds(bx + bw + 4, by - 24, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> rebuildWidgets()).bounds(bx + 2 * (bw + 4), by - 24, bw, 20).build());
		Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
			page++;
			rebuildWidgets();
		}).bounds(bx + 3 * (bw + 4), by - 24, bw, 20).build());
		next.active = page < pages - 1;
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 75, by + 4, 150, 20).build());
	}

	private void confirmUnpair(EnvRow r) {
		minecraft.gui.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				String msg = actions.unpair(r.id());
				ok(msg);
			}
			minecraft.gui.setScreen(this);
		}, Component.literal("Unpair " + r.label() + "?"),
				Component.literal("Deletes the local read-only session for " + r.url()
						+ ". Revoke it on the host too: t3 auth session list / t3 auth session revoke <id>.")));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int w = listWidth();
		int x = width / 2 - w / 2;
		int y = 36;
		if (rows.isEmpty()) {
			g.centeredText(font, Component.literal("No T3 environment paired yet. Use \"Pair new…\".").withStyle(ChatFormatting.YELLOW),
					width / 2, y + 6, 0xFFFFFFFF);
			g.centeredText(font, Component.literal("On the T3 host: t3 auth pairing create --scope orchestration:read").withStyle(ChatFormatting.GRAY),
					width / 2, y + 20, 0xFFFFFFFF);
			return;
		}
		T3CraftSettings s = ConfigManager.get().settings();
		int from = page * perPage();
		int textW = w - 3 * 54 - 16;
		for (int i = from; i < Math.min(rows.size(), from + perPage()); i++) {
			EnvRow r = rows.get(i);
			EnvPrefs p = s.env(r.id());
			int ry = y + (i - from) * ROW;
			int swatch = p.color() == null ? 0xFF555555 : 0xFF000000 | p.color();
			g.fill(x, ry + 2, x + 6, ry + 18, swatch);
			String name = p.labelOr(r.label()) + (p.visible() ? "" : "  (hidden)");
			g.text(font, font.plainSubstrByWidth(name, textW), x + 10, ry + 1, p.visible() ? 0xFFFFFFFF : 0xFF808080);
			int sc = switch (r.status()) {
				case "ok" -> 0xFF55FF55;
				case "connecting" -> 0xFFFFFF55;
				case "-" -> 0xFF808080;
				default -> 0xFFFF5555;
			};
			String line = r.status() + " · " + r.id() + " · " + r.url();
			g.text(font, font.plainSubstrByWidth(line, textW), x + 10, ry + 11, sc);
		}
		int pages = Math.max(1, (rows.size() + perPage() - 1) / perPage());
		if (pages > 1) {
			g.centeredText(font, Component.literal("Page " + (page + 1) + "/" + pages).withStyle(ChatFormatting.GRAY), width / 2, 24, 0xFFFFFFFF);
		}
	}
}
