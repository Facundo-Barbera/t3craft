package dev.t3craft.client.screen;

import dev.t3craft.client.T3CraftClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Pair this Minecraft instance with a T3 environment, read-only. Paste the pairing link
 * ({@code https://host/pair#token=…}) or the bare code plus the host URL. On the host:
 * {@code t3 auth pairing create --scope orchestration:read --base-url https://host}.
 * The credential is exchanged for an {@code orchestration:read} session and stored in
 * {@code config/t3craft/credentials/}; nothing is shown or logged.
 */
public final class PairScreen extends Screen {
	private final Screen parent;
	private EditBox label;
	private EditBox link;
	private EditBox host;
	private Button pairButton;
	private Component message = Component.empty();
	private boolean busy;

	public PairScreen(Screen parent) {
		super(Component.literal("Pair with T3 (read-only)"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int w = Math.min(360, width - 40);
		int x = width / 2 - w / 2;
		int y = height / 2 - 70;
		label = new EditBox(font, x, y, w, 20, label, Component.literal("Environment name"));
		label.setMaxLength(32);
		label.setHint(Component.literal("Name shown on the villagers, e.g. my-vps").withStyle(ChatFormatting.DARK_GRAY));
		addRenderableWidget(label);
		link = new EditBox(font, x, y + 36, w, 20, link, Component.literal("Pairing link or code"));
		link.setMaxLength(4096);
		link.setHint(Component.literal("https://host/pair#token=…  or the code").withStyle(ChatFormatting.DARK_GRAY));
		addRenderableWidget(link);
		host = new EditBox(font, x, y + 72, w, 20, host, Component.literal("T3 URL (only with a bare code)"));
		host.setMaxLength(512);
		host.setHint(Component.literal("Only for a bare code: https://host.ts.net or http://host:3773").withStyle(ChatFormatting.DARK_GRAY));
		addRenderableWidget(host);
		pairButton = addRenderableWidget(Button.builder(Component.literal("Pair (read-only)"), b -> submit())
				.bounds(x, y + 108, w / 2 - 4, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
				.bounds(x + w / 2 + 4, y + 108, w / 2 - 4, 20).build());
		pairButton.active = !busy;
		setInitialFocus(link);
	}

	private void submit() {
		if (busy) {
			return;
		}
		busy = true;
		pairButton.active = false;
		message = Component.literal("Pairing…").withStyle(ChatFormatting.YELLOW);
		String linkValue = link.getValue();
		T3CraftClient.get().pair(label.getValue(), linkValue, host.getValue(), r -> {
			busy = false;
			if (pairButton != null) {
				pairButton.active = true;
			}
			message = Component.literal(r.message()).withStyle(r.ok() ? ChatFormatting.GREEN : ChatFormatting.RED);
			if (r.ok()) {
				link.setValue(""); // the one-time credential is spent; don't keep it on screen
			}
		});
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int y = height / 2 - 70;
		graphics.centeredText(font, title, width / 2, y - 30, 0xFFFFFFFF);
		graphics.centeredText(font, Component.literal("On the T3 host: t3 auth pairing create --scope orchestration:read").withStyle(ChatFormatting.GRAY),
				width / 2, y - 16, 0xFFFFFFFF);
		int x = width / 2 - Math.min(360, width - 40) / 2;
		graphics.text(font, "Environment name", x, y - 10 + 0, 0xFFA0A0A0);
		graphics.text(font, "Pairing link or code", x, y + 26, 0xFFA0A0A0);
		graphics.text(font, "T3 URL (bare code only)", x, y + 62, 0xFFA0A0A0);
		graphics.centeredText(font, message, width / 2, y + 140, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
