package dev.t3craft.client.config;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Shared bits of the config screens: parent handling, ON/OFF buttons, a status line. */
abstract class BaseConfigScreen extends Screen {
	protected final @Nullable Screen parent;
	protected Component status = Component.empty();

	protected BaseConfigScreen(Component title, @Nullable Screen parent) {
		super(title);
		this.parent = parent;
	}

	/** A button showing {@code "label: ON|OFF"} that flips on click. */
	protected Button toggle(String label, boolean initial, int x, int y, int w, Consumer<Boolean> onChange) {
		boolean[] value = {initial};
		Button b = Button.builder(toggleText(label, initial), btn -> {
			value[0] = !value[0];
			btn.setMessage(toggleText(label, value[0]));
			onChange.accept(value[0]);
		}).bounds(x, y, w, 20).build();
		return addRenderableWidget(b);
	}

	protected static Component toggleText(String label, boolean on) {
		return Component.literal(label + ": ").append(on
				? Component.literal("ON").withStyle(ChatFormatting.GREEN)
				: Component.literal("OFF").withStyle(ChatFormatting.RED));
	}

	protected void ok(String msg) {
		status = Component.literal(msg).withStyle(ChatFormatting.GREEN);
	}

	protected void error(String msg) {
		status = Component.literal(msg).withStyle(ChatFormatting.RED);
	}

	protected void info(String msg) {
		status = Component.literal(msg).withStyle(ChatFormatting.YELLOW);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
		// Under the lowest button row: at height - 64 the line sat on the "Environments… / Key binds…"
		// and "< / Pair new… / Refresh / >" rows and was unreadable.
		g.centeredText(font, status, width / 2, dev.t3craft.client.screen.DialogLayout.statusY(height, font.lineHeight), 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Parses an optional number field: blank → null, invalid → NaN. */
	protected static @Nullable Double optionalNumber(String raw) {
		String s = raw.trim();
		if (s.isEmpty()) {
			return null;
		}
		try {
			return Double.valueOf(s.replace(',', '.'));
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	protected static String formatNumber(double v) {
		return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
	}
}
