package dev.t3craft.client.config;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Control;
import dev.t3craft.client.t3ui.kit.T3Dialog;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Icon;
import dev.t3craft.client.t3ui.kit.T3List;
import dev.t3craft.client.t3ui.kit.T3Panel;
import dev.t3craft.client.t3ui.kit.T3Toggle;
import dev.t3craft.outbound.Destination;
import dev.t3craft.outbound.OutboundConfig;
import dev.t3craft.outbound.OutboundStore;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Outbound events as a page of the UI kit (docs/features/outbound-events.md): the master switch (off by
 * default; turning it on asks for confirmation because it sends data off this computer) and the list of
 * destinations. A destination opens as {@link OutboundDestinationPanel} on top of this page. Changes are
 * written at once to {@code config/t3craft/outbound.json}; the webhook URLs never show up here, only
 * whether one is set.
 */
public final class OutboundPanel extends T3Panel {
	static final String WARNING = "Turning this on sends thread events (what needs you, what failed, what finished) to the webhook URLs "
			+ "you add, outside this computer. Depending on each destination that can include thread titles, project names and "
			+ "environment ids. The URLs are stored like your T3 tokens and never shown. Use 'pending only' to send no content at all.";

	private static final int MAX_W = 440;
	private static final int ROW_H = 24;
	private static final int MIN_LIST_H = 72;

	private final OutboundStore store;
	private OutboundConfig cfg;
	/** Whether each destination has a stored URL; read when the page is arranged, not on every frame. */
	private final Map<String, Boolean> urlSet = new HashMap<>();
	private final T3List<Destination> list;
	private String status = "";
	private Tone statusTone = Tone.MUTED;

	public OutboundPanel(OutboundStore store) {
		this.store = store;
		this.cfg = store.load();
		this.list = new T3List<>(ROW_H, new T3List.Row<Destination>() {
			@Override
			public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, Destination d, Rect r, int mx, int my) {
				drawRow(g, font, t, d, r, mx, my);
			}

			@Override
			public boolean mouseClicked(Destination d, Rect r, double mx, double my, int button) {
				if (!MouseButtons.isLeft(button)) {
					return false;
				}
				host().show(new OutboundDestinationPanel(store, d.id(), OutboundPanel.this::reload));
				return true;
			}
		});
	}

	@Override
	public String title() {
		return "Outbound events";
	}

	/** The destination page changed the file: read it again on the next layout. */
	private void reload() {
		cfg = store.load();
	}

	private void say(String msg, Tone tone) {
		status = msg;
		statusTone = tone;
	}

	// ---- layout ----------------------------------------------------------------------------------------

	@Override
	protected int headerHeight() {
		return T3Control.H + 6;
	}

	@Override
	protected int footerHeight() {
		return 14;
	}

	@Override
	protected void arrangeHeader(Rect header) {
		pin(new T3Button(T3Icon.PLUS, "Add destination", T3Button.Style.PRIMARY, this::addDestination), header.x(), header.y(), 110, T3Control.H);
		pinText(header.x() + 118, header.y() + (T3Control.H - 8) / 2, cfg.destinations().size() + " / " + OutboundConfig.MAX_DESTINATIONS, Tone.MUTED);
	}

	@Override
	protected void arrangeFooter(Rect footer) {
		pinText(footer.x() + 2, footer.y() + 4, T3Draw.wrap(font(), status, footer.w() - 4, 1).getFirst(), statusTone);
	}

	@Override
	protected int arrange(Rect body, int y0) {
		int w = Math.min(MAX_W, body.w() - 8);
		int x = body.x() + (body.w() - w) / 2 - 3;
		int y = y0 + 4;

		T3Toggle[] master = new T3Toggle[1];
		master[0] = place(new T3Toggle("Send events outside the game", cfg.enabled(), on -> masterChanged(on, master[0])), x, y, w, T3Control.H);
		y += T3Control.H + 4;
		y = notice(x + 4, y, w - 8, "Off by default. This sends data outside your computer.", Tone.WARN) + 2;
		y = notice(x + 4, y, w - 8, "URLs are stored like your T3 tokens and never shown.", Tone.MUTED) + 6;

		urlSet.clear();
		for (Destination d : cfg.destinations()) {
			urlSet.put(d.id(), store.hasUrl(d.id()));
		}
		list.setItems(cfg.destinations());
		list.setEmptyText("No destinations yet.\nUse \"Add destination\".");
		int h = Math.max(MIN_LIST_H, body.h() - (y - y0) - 4);
		place(list, x, y, w, h);
		return y + h + 4;
	}

	private void drawRow(GuiGraphicsExtractor g, Font font, ChatTheme t, Destination d, Rect r, int mx, int my) {
		int x = r.x() + 6;
		g.text(font, ChatDraw.clip(font, d.name(), r.w() - 24), x, r.y() + 3, d.enabled() ? t.text() : t.disabled(), t.shadow());
		boolean hover = r.contains(mx, my);
		T3Icon.draw(g, t, T3Icon.CHEVRON_RIGHT, r.right() - 15, r.y() + (r.h() - T3Icon.SIZE) / 2, T3Icon.SIZE, T3Icon.tint(t, hover, true));
		boolean hasUrl = urlSet.getOrDefault(d.id(), false);
		String[] part = {d.template().label, d.enabled() ? "on" : "off", hasUrl ? "URL set" : "no URL"};
		int[] colour = {t.muted(), d.enabled() ? t.result() : t.disabled(), hasUrl ? t.muted() : t.youText()};
		int y = r.y() + 13;
		for (int i = 0; i < part.length; i++) {
			if (i > 0) {
				g.text(font, " · ", x, y, t.muted(), t.shadow());
				x += font.width(" · ");
			}
			g.text(font, part[i], x, y, colour[i], t.shadow());
			x += font.width(part[i]);
		}
	}

	// ---- actions ---------------------------------------------------------------------------------------------

	/** Off takes effect at once; on asks first, and the switch stays off until the player confirms. */
	private void masterChanged(boolean on, T3Toggle toggle) {
		if (!on) {
			if (persist(cfg.withEnabled(false))) {
				say("Outbound events are off", Tone.MUTED);
			}
			host().relayout();
			return;
		}
		toggle.set(false);
		host().modal(T3Dialog.confirm("Send T3 events outside this computer?", List.of(WARNING), null, "Turn on", true, () -> {
			host().modal(null);
			if (persist(store.load().withEnabled(true))) {
				say("Outbound events are on", Tone.WARN);
			}
			host().relayout();
		}, () -> {
			host().modal(null);
			host().relayout();
		}));
	}

	private void addDestination() {
		if (cfg.destinations().size() >= OutboundConfig.MAX_DESTINATIONS) {
			say("At most " + OutboundConfig.MAX_DESTINATIONS + " destinations", Tone.ERROR);
			host().relayout();
			return;
		}
		String id = Destination.freeId(cfg.destinations().stream().map(Destination::id).toList());
		Destination d = Destination.create(id, "Destination " + (cfg.destinations().size() + 1));
		if (persist(cfg.withDestination(d))) {
			host().show(new OutboundDestinationPanel(store, id, this::reload));
		} else {
			host().relayout();
		}
	}

	private boolean persist(OutboundConfig next) {
		try {
			store.save(next);
			cfg = next;
			return true;
		} catch (IOException e) {
			say("Could not save outbound.json: " + e.getClass().getSimpleName(), Tone.ERROR);
			return false;
		}
	}
}
