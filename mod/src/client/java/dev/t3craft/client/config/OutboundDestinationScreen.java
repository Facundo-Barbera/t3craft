package dev.t3craft.client.config;

import dev.t3craft.client.outbound.OutboundClient;
import dev.t3craft.outbound.Destination;
import dev.t3craft.outbound.EventType;
import dev.t3craft.outbound.OutboundStore;
import dev.t3craft.outbound.Template;
import java.io.IOException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * One outbound destination: name, template, webhook URL (a secret: the box starts empty, whatever is typed
 * replaces the stored URL on Save, and it is never shown again), tap action for push, the "pending only"
 * choice and the event filter. Written on "Save".
 */
final class OutboundDestinationScreen extends BaseConfigScreen {
	private final OutboundStore store;
	private final String id;
	private Destination d;
	private String nameText;
	private String urlText = "";
	private String clickText;
	private @Nullable EditBox nameBox;
	private @Nullable EditBox urlBox;
	private @Nullable EditBox clickBox;
	private int noteY;

	OutboundDestinationScreen(Screen parent, OutboundStore store, String id) {
		super(Component.literal("Outbound destination"), parent);
		this.store = store;
		this.id = id;
		Destination found = store.load().destination(id);
		this.d = found != null ? found : Destination.create(id, id);
		this.nameText = d.name();
		this.clickText = d.clickUrl() == null ? "" : d.clickUrl();
	}

	@Override
	protected void init() {
		int cw = Math.min(150, (width - 30) / 2);
		int gap = 6;
		int x0 = width / 2 - cw - gap / 2;
		int x1 = x0 + cw + gap;
		int fw = cw * 2 + gap;
		int y = 36;

		nameBox = new EditBox(font, x0, y, fw, 18, nameBox, Component.literal("Name"));
		nameBox.setMaxLength(Destination.MAX_NAME);
		nameBox.setValue(nameText);
		nameBox.setResponder(v -> nameText = v);
		nameBox.setHint(Component.literal("Name").withStyle(ChatFormatting.DARK_GRAY));
		addRenderableWidget(nameBox);
		y += 22;

		addRenderableWidget(Button.builder(Component.literal("Template: " + d.template().label), b -> {
			d = d.withTemplate(d.template().next());
			rebuildWidgets();
		}).bounds(x0, y, cw, 20).build());
		toggle("Enabled", d.enabled(), x1, y, cw, v -> d = d.withEnabled(v));
		y += 24;

		urlBox = new EditBox(font, x0, y, fw, 18, urlBox, Component.literal("Webhook URL"));
		urlBox.setMaxLength(OutboundStore.MAX_URL_LENGTH);
		urlBox.setValue(urlText);
		urlBox.setResponder(v -> urlText = v);
		urlBox.setHint(Component.literal(store.hasUrl(id) ? "Webhook URL: set (paste a new one to replace it)" : "Webhook URL (secret): paste it here")
				.withStyle(ChatFormatting.DARK_GRAY));
		addRenderableWidget(urlBox);
		y += 22;

		if (d.template() == Template.PUSH) {
			clickBox = new EditBox(font, x0, y, fw, 18, clickBox, Component.literal("Tap action"));
			clickBox.setMaxLength(Destination.MAX_CLICK);
			clickBox.setValue(clickText);
			clickBox.setResponder(v -> clickText = v);
			clickBox.setHint(Component.literal("Tap action URL (optional, {environment} {thread})").withStyle(ChatFormatting.DARK_GRAY));
			addRenderableWidget(clickBox);
			y += 22;
		}

		toggle("Pending only", d.pendingOnly(), x0, y, cw, v -> d = d.withPendingOnly(v));
		noteY = y + 24;
		y += 38;

		int i = 0;
		for (EventType type : EventType.values()) {
			int col = i % 2 == 0 ? x0 : x1;
			toggle(type.label, d.events().contains(type), col, y, cw, v -> d = d.withEvent(type, v));
			if (i % 2 == 1) {
				y += 22;
			}
			i++;
		}

		int by = height - 52;
		int bw = (fw - 8) / 3;
		addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(x0, by - 24, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Send test"), b -> test()).bounds(x0 + bw + 4, by - 24, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Delete").withStyle(ChatFormatting.RED), b -> delete())
				.bounds(x0 + 2 * (bw + 4), by - 24, bw, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose()).bounds(width / 2 - bw / 2, by + 4, bw, 20).build());
	}

	private boolean save() {
		String url = urlText.strip();
		if (!url.isEmpty()) {
			String why = OutboundStore.validate(url);
			if (why != null) {
				error(why);
				return false;
			}
		}
		Destination next = d.withName(nameText).withClickUrl(clickText);
		try {
			store.save(store.load().withDestination(next));
			if (!url.isEmpty()) {
				store.setUrl(id, url);
				urlText = "";
			}
		} catch (IOException | IllegalArgumentException e) {
			error("Could not save: " + e.getClass().getSimpleName());
			return false;
		}
		d = next;
		ok("Saved");
		rebuildWidgets();
		return true;
	}

	private void test() {
		if (!urlText.isBlank()) {
			info("Save first, then send the test");
			return;
		}
		info("Sending test…");
		OutboundClient.sendTest(id, msg -> minecraft.execute(() -> info("Test: " + msg)));
	}

	private void delete() {
		try {
			store.save(store.load().withoutDestination(id));
			store.deleteUrl(id);
		} catch (IOException e) {
			error("Could not delete: " + e.getClass().getSimpleName());
			return;
		}
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int cw = Math.min(150, (width - 30) / 2);
		int fw = cw * 2 + 6;
		int x0 = width / 2 - fw / 2;
		g.text(font, font.plainSubstrByWidth("Everything this destination receives leaves your computer.", fw), x0, 24, 0xFFFFAA00);
		String note = d.pendingOnly() ? "Pending only: no titles, projects or text are sent, just 'something is pending'."
				: "Thread titles, projects and environment ids are sent.";
		g.text(font, font.plainSubstrByWidth(note, fw), x0, noteY, d.pendingOnly() ? 0xFF55FF55 : 0xFFFFAA00);
	}
}
