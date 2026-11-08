package dev.t3craft.client.screen;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.t3.Pairing;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Pair this Minecraft instance with a T3 environment. By default read + control (operate) in a
 * single pairing ({@link Pairing#DEFAULT_SCOPES}); the "Access" toggle (or
 * {@code /t3craft pair --read-only}) asks for {@code orchestration:read} only. Paste the pairing
 * link ({@code https://host/pair#token=…}) or the bare code plus the host URL. The host command
 * shown always carries explicit {@code --scope}s ({@link Pairing#pairingHostCommand}). The session
 * is stored in {@code config/t3craft/credentials/}; nothing is shown or logged.
 */
public final class PairScreen extends Screen {
	/** {@code /t3craft pair --read-only}. */
	public static final String READ_ONLY_FLAG = "--read-only";

	private final @Nullable Screen parent;
	private boolean readOnly;
	private EditBox label;
	private EditBox link;
	private EditBox host;
	private Button pairButton;
	private Button modeButton;
	private Component message = Component.empty();
	private boolean busy;

	public PairScreen(@Nullable Screen parent) {
		this(parent, false);
	}

	public PairScreen(@Nullable Screen parent, boolean readOnly) {
		super(Component.translatable("t3craft.pair.title"));
		this.parent = parent;
		this.readOnly = readOnly;
	}

	private int top() {
		return height / 2 - 62;
	}

	private int fieldWidth() {
		return Math.min(360, width - 40);
	}

	@Override
	protected void init() {
		int w = fieldWidth();
		int x = width / 2 - w / 2;
		int y = top();
		label = new EditBox(font, x, y, w, 20, label, Component.literal("Environment name"));
		label.setMaxLength(32);
		label.setHint(Component.literal("Name shown on the villagers, e.g. my-vps").withStyle(ChatFormatting.DARK_GRAY));
		addRenderableWidget(label);
		link = new EditBox(font, x, y + 34, w, 20, link, Component.literal("Pairing link or code"));
		link.setMaxLength(4096);
		link.setHint(Component.literal("https://host/pair#token=…  or the code").withStyle(ChatFormatting.DARK_GRAY));
		addRenderableWidget(link);
		host = new EditBox(font, x, y + 68, w, 20, host, Component.literal("T3 URL (only with a bare code)"));
		host.setMaxLength(512);
		host.setHint(Component.literal("Only for a bare code: https://host.ts.net or http://host:3773").withStyle(ChatFormatting.DARK_GRAY));
		addRenderableWidget(host);
		modeButton = addRenderableWidget(Button.builder(modeLabel(), b -> {
			readOnly = !readOnly;
			rebuildWidgets();
		}).bounds(x, y + 94, w, 20).build());
		modeButton.setTooltip(Tooltip.create(Component.translatable("t3craft.pair.mode.tooltip")));
		modeButton.active = !busy;
		pairButton = addRenderableWidget(Button.builder(Component.translatable(readOnly ? "t3craft.pair.button.read" : "t3craft.pair.button"), b -> submit())
				.bounds(x, y + 118, w / 2 - 4, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
				.bounds(x + w / 2 + 4, y + 118, w / 2 - 4, 20).build());
		pairButton.active = !busy;
		setInitialFocus(link);
	}

	private Component modeLabel() {
		return readOnly
				? Component.translatable("t3craft.pair.mode.read").withStyle(ChatFormatting.GRAY)
				: Component.translatable("t3craft.pair.mode.control").withStyle(ChatFormatting.GOLD);
	}

	private void submit() {
		if (busy) {
			return;
		}
		busy = true;
		pairButton.active = false;
		modeButton.active = false;
		message = Component.literal("Pairing…").withStyle(ChatFormatting.YELLOW);
		String linkValue = link.getValue();
		T3CraftClient.get().pair(label.getValue(), linkValue, host.getValue(), readOnly, r -> {
			busy = false;
			// both buttons: the access toggle used to stay greyed out until the screen was rebuilt
			if (pairButton != null) {
				pairButton.active = true;
			}
			if (modeButton != null) {
				modeButton.active = true;
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
		int w = fieldWidth();
		int x = width / 2 - w / 2;
		int y = top();
		// Header, bottom-up from the first field: host command (wrapped), "On the T3 host:", title.
		var cmd = font.split(Component.literal(Pairing.pairingHostCommand(readOnly, null)).withStyle(ChatFormatting.AQUA), w);
		int cy = y - 14 - cmd.size() * font.lineHeight;
		graphics.text(font, Component.translatable("t3craft.pair.host").withStyle(ChatFormatting.GRAY), x, cy - 10, 0xFFFFFFFF);
		graphics.centeredText(font, title, width / 2, Math.max(4, cy - 24), 0xFFFFFFFF);
		for (FormattedCharSequence s : cmd) {
			graphics.text(font, s, x, cy, 0xFFFFFFFF);
			cy += font.lineHeight;
		}
		graphics.text(font, "Environment name", x, y - 10, 0xFFA0A0A0);
		graphics.text(font, "Pairing link or code", x, y + 24, 0xFFA0A0A0);
		graphics.text(font, "T3 URL (bare code only)", x, y + 58, 0xFFA0A0A0);
		int my = y + 144;
		if (!readOnly) {
			for (FormattedCharSequence s : font.split(Component.translatable("t3craft.pair.warning.control").withStyle(ChatFormatting.GOLD), w)) {
				graphics.text(font, s, x, my, 0xFFFFFFFF);
				my += font.lineHeight;
			}
			my += 2;
		}
		for (FormattedCharSequence s : font.split(message, w)) {
			graphics.text(font, s, width / 2 - font.width(s) / 2, my, 0xFFFFFFFF);
			my += font.lineHeight;
		}
	}

	/** Enter in any of the fields pairs, like the button (vanilla EditBox ignores Enter). */
	@Override
	public boolean keyPressed(KeyEvent e) {
		if (DialogLayout.isSubmit(e.key()) && getFocused() instanceof EditBox && pairButton != null && pairButton.active) {
			submit();
			return true;
		}
		return super.keyPressed(e);
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
