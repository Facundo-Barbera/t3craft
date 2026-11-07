package dev.t3craft.client.screen;

import dev.t3craft.client.npc.NpcInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Placeholder chat screen opened by right-clicking a thread NPC.
 *
 * <p>TODO(phase 2): on open send {@code chat.open{threadId}}, render {@code chat.history} /
 * {@code chat.append}, an input box that sends {@code chat.send} (bridge → {@code message.dispatch})
 * and an interrupt button ({@code run.interrupt}); send {@code chat.close} on close.
 */
public final class ThreadChatScreen extends Screen {
	private final NpcInfo thread;

	public ThreadChatScreen(NpcInfo thread) {
		super(Component.literal(thread.title()));
		this.thread = thread;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width / 2 - 100, height - 40, 200, 20)
				.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int y = 30;
		graphics.centeredText(font, title, width / 2, y, 0xFFFFFFFF);
		y += 14;
		String meta = thread.state().wire + (thread.project() == null ? "" : " · " + thread.project());
		graphics.centeredText(font, Component.literal(meta).withStyle(thread.state().color), width / 2, y, 0xFFFFFFFF);
		if (thread.activity() != null) {
			y += 14;
			graphics.centeredText(font, thread.activity(), width / 2, y, 0xFFAAAAAA);
		}
		graphics.centeredText(font, "Chat is not implemented yet (phase 2).", width / 2, height / 2, 0xFF888888);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
