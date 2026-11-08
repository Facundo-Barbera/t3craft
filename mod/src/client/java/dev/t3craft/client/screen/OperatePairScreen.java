package dev.t3craft.client.screen;

import dev.t3craft.client.operate.OperateClient;
import dev.t3craft.t3.Environments;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.Pairing;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * "Enable control" for one environment paired read-only (upgrade): the risk warning, the exact host
 * command (always with explicit {@code --scope orchestration:read --scope orchestration:operate})
 * and a field for the one-time link or code. The operate session is stored apart from the
 * read-only one, which is kept; nothing is shown or logged. New pairings already get control by
 * default ({@link PairScreen}).
 */
public final class OperatePairScreen extends Screen {
	private final @Nullable Screen parent;
	private final Environments.Environment env;
	private EditBox link;
	private Button enableButton;
	private Component message = Component.empty();
	private boolean busy;

	public OperatePairScreen(@Nullable Screen parent, Environments.Environment env) {
		super(Component.translatable("t3craft.operate.enable.title", env.label()));
		this.parent = parent;
		this.env = env;
	}

	public static String hostCommand(Environments.Environment env) {
		return Pairing.hostCommand(Pairing.OPERATE_SCOPES, "T3Craft operate", env.url());
	}

	/** Set in {@link #init()}: below the warning and host command when they are tall. */
	private int fieldY;

	private int fieldY() {
		return fieldY;
	}

	/** Bottom of the header text (title, wrapped warning, host command) for width {@code w}. */
	private int headerBottom(int w) {
		int warn = font.split(Component.translatable("t3craft.operate.enable.warning", env.label()), w).size();
		int cmd = font.split(Component.literal(hostCommand(env)), w).size();
		return 16 + 16 + warn * font.lineHeight + 6 + 11 + cmd * font.lineHeight + 12;
	}

	@Override
	protected void init() {
		int w = Math.min(380, width - 40);
		int x = width / 2 - w / 2;
		// the field, the buttons (+28) and the result line (+56) must stay on screen
		fieldY = Math.max(height / 2 + 4, Math.min(headerBottom(w), height - 68));
		link = new EditBox(font, x, fieldY(), w, 20, link, Component.literal("Pairing link or code"));
		link.setMaxLength(4096);
		link.setHint(Component.literal("https://host/pair#token=…  or the code").withStyle(ChatFormatting.DARK_GRAY));
		addRenderableWidget(link);
		enableButton = addRenderableWidget(Button.builder(Component.translatable("t3craft.operate.enable.button").withStyle(ChatFormatting.RED), b -> submit())
				.bounds(x, fieldY() + 28, w / 2 - 4, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(x + w / 2 + 4, fieldY() + 28, w / 2 - 4, 20).build());
		enableButton.active = !busy;
		setInitialFocus(link);
	}

	private void submit() {
		if (busy) {
			return;
		}
		busy = true;
		enableButton.active = false;
		message = Component.literal("Pairing…").withStyle(ChatFormatting.YELLOW);
		OperateClient.get().enable(env.id(), link.getValue(), OperateGate.Origin.UI, r -> {
			busy = false;
			if (enableButton != null) {
				enableButton.active = true;
			}
			message = Component.literal(r.message()).withStyle(r.ok() ? ChatFormatting.GREEN : ChatFormatting.RED);
			if (r.ok()) {
				link.setValue(""); // spent one-time credential
			}
		});
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int w = Math.min(380, width - 40);
		int x = width / 2 - w / 2;
		int y = 16;
		g.centeredText(font, title.copy().withStyle(ChatFormatting.YELLOW), width / 2, y, 0xFFFFFFFF);
		y += 16;
		List<FormattedCharSequence> warn = font.split(Component.translatable("t3craft.operate.enable.warning", env.label()).withStyle(ChatFormatting.RED), w);
		for (FormattedCharSequence s : warn) {
			g.text(font, s, x, y, 0xFFFFFFFF);
			y += font.lineHeight;
		}
		y += 6;
		g.text(font, Component.translatable("t3craft.operate.enable.host").withStyle(ChatFormatting.GRAY), x, y, 0xFFFFFFFF);
		y += 11;
		// Only the lines that fit above the field's label: clamping every line to the same y drew
		// the overflow on top of each other (unreadable) on short windows.
		List<FormattedCharSequence> cmd = font.split(Component.literal(hostCommand(env)).withStyle(ChatFormatting.AQUA), w);
		int fit = DialogLayout.linesThatFit(y, font.lineHeight, fieldY() - 12, cmd.size());
		for (int i = 0; i < fit; i++) {
			g.text(font, cmd.get(i), x, y, 0xFFFFFFFF);
			y += font.lineHeight;
		}
		g.text(font, "Pairing link or code", x, fieldY() - 10, 0xFFA0A0A0);
		int my = fieldY() + 56; // wrapped: pairing errors are often wider than the window
		for (FormattedCharSequence s : font.split(message, w)) {
			g.centeredText(font, s, width / 2, my, 0xFFFFFFFF);
			my += font.lineHeight;
		}
	}

	/** Enter in the field enables control, like the button (vanilla EditBox ignores Enter). */
	@Override
	public boolean keyPressed(KeyEvent e) {
		if (DialogLayout.isSubmit(e.key()) && getFocused() == link && enableButton != null && enableButton.active) {
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
