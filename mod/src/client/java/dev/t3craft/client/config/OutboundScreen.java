package dev.t3craft.client.config;

import dev.t3craft.client.ModConfig;
import dev.t3craft.outbound.Destination;
import dev.t3craft.outbound.OutboundConfig;
import dev.t3craft.outbound.OutboundStore;
import java.io.IOException;
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
 * Outbound events (docs/features/outbound-events.md): the master switch (off by default; turning it on asks
 * for confirmation because it sends data off this computer) and the list of destinations. Changes here are
 * written at once to {@code config/t3craft/outbound.json}; the webhook URLs never show up on this screen.
 */
public final class OutboundScreen extends BaseConfigScreen {
	static final String WARNING = "Turning this on sends thread events (what needs you, what failed, what finished) to the webhook URLs "
			+ "you add, outside this computer. Depending on each destination that can include thread titles, project names and "
			+ "environment ids. The URLs are stored like your T3 tokens and never shown. Use 'pending only' to send no content at all.";

	private final OutboundStore store = new OutboundStore(ModConfig.dataDir());
	private OutboundConfig cfg;
	private int page;

	public OutboundScreen(@Nullable Screen parent) {
		super(Component.literal("Outbound events"), parent);
		this.cfg = store.load();
	}

	@Override
	protected void init() {
		cfg = store.load();
		int w = Math.min(280, width - 40);
		int x = width / 2 - w / 2;
		int y = 36;
		addRenderableWidget(Button.builder(toggleText("Send events outside the game", cfg.enabled()), b -> toggleMaster())
				.bounds(x, y, w, 20).build());
		y += 50;

		int by = height - 52;
		int rows = Math.max(1, (by - 30 - y) / 22);
		List<Destination> all = cfg.destinations();
		int pages = Math.max(1, (all.size() + rows - 1) / rows);
		page = Math.max(0, Math.min(page, pages - 1));
		for (int i = page * rows; i < Math.min(all.size(), (page + 1) * rows); i++) {
			Destination d = all.get(i);
			addRenderableWidget(Button.builder(rowText(d), b -> minecraft.gui.setScreen(new OutboundDestinationScreen(this, store, d.id())))
					.bounds(x, y, w, 20).build());
			y += 22;
		}

		int addW = pages > 1 ? w - 48 : w;
		addRenderableWidget(Button.builder(Component.literal("Add destination"), b -> addDestination()).bounds(x, by - 24, addW, 20).build());
		if (pages > 1) {
			int p = pages;
			addRenderableWidget(Button.builder(Component.literal("<"), b -> {
				page = Math.max(0, page - 1);
				rebuildWidgets();
			}).bounds(x + w - 44, by - 24, 20, 20).build());
			addRenderableWidget(Button.builder(Component.literal(">"), b -> {
				page = Math.min(p - 1, page + 1);
				rebuildWidgets();
			}).bounds(x + w - 20, by - 24, 20, 20).build());
		}
		int half = Math.min(150, (width - 40) / 2 - 4);
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - half / 2, by + 4, half, 20).build());
	}

	private Component rowText(Destination d) {
		Component state = d.enabled() ? Component.literal("on").withStyle(ChatFormatting.GREEN) : Component.literal("off").withStyle(ChatFormatting.RED);
		Component url = store.hasUrl(d.id()) ? Component.literal("URL set").withStyle(ChatFormatting.GRAY)
				: Component.literal("no URL").withStyle(ChatFormatting.YELLOW);
		return Component.literal(d.name() + " · " + d.template().label + " · ").append(state).append(Component.literal(" · ")).append(url);
	}

	private void toggleMaster() {
		if (cfg.enabled()) {
			persist(cfg.withEnabled(false));
			rebuildWidgets();
			return;
		}
		minecraft.gui.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				persist(store.load().withEnabled(true));
			}
			minecraft.gui.setScreen(this);
		}, Component.literal("Send T3 events outside this computer?"), Component.literal(WARNING)));
	}

	private void addDestination() {
		if (cfg.destinations().size() >= OutboundConfig.MAX_DESTINATIONS) {
			error("At most " + OutboundConfig.MAX_DESTINATIONS + " destinations");
			return;
		}
		String id = Destination.freeId(cfg.destinations().stream().map(Destination::id).toList());
		Destination d = Destination.create(id, "Destination " + (cfg.destinations().size() + 1));
		if (persist(cfg.withDestination(d))) {
			minecraft.gui.setScreen(new OutboundDestinationScreen(this, store, id));
		}
	}

	private boolean persist(OutboundConfig next) {
		try {
			store.save(next);
			cfg = next;
			return true;
		} catch (IOException e) {
			error("Could not save outbound.json: " + e.getClass().getSimpleName());
			return false;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int w = Math.min(280, width - 40);
		int x = width / 2 - w / 2;
		g.text(font, font.plainSubstrByWidth("Off by default. This sends data outside your computer.", w), x, 60, 0xFFFFAA00);
		g.text(font, font.plainSubstrByWidth("URLs are stored like your T3 tokens and never shown.", w), x, 72, 0xFFA0A0A0);
		if (cfg.destinations().isEmpty()) {
			g.text(font, "No destinations yet.", x, 90, 0xFFA0A0A0);
		}
	}
}
