package dev.t3craft.client.t3ui;

import dev.t3craft.client.chat.Attention;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.chat.ThreadRow;
import dev.t3craft.client.config.EnvironmentActions;
import dev.t3craft.client.config.EnvironmentsScreen;
import dev.t3craft.client.config.SettingsScreen;
import dev.t3craft.client.screen.NewThreadScreen;
import dev.t3craft.client.workstation.StationPlacement;
import dev.t3craft.client.workstation.WorkstationClient;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The holes of the unified screen for the self-contained widgets of the parallel branches, with the
 * fallbacks main already has. {@link T3Screen} only talks to this class, so mounting a component is
 * one change here:
 * <ul>
 *   <li>{@link #composer()}: feat/chat-compose's composer ({@code dev.t3craft.client.chat.compose});
 *       null = the conversation's own read-only command bar ("Enable control").</li>
 *   <li>{@link #drawStatusChip}: feat/t3-actions' {@code ThreadStatusChip}; fallback: a pill with
 *       the attention word.</li>
 *   <li>{@link #newThread}: feat/t3-actions' {@code NewThreadDialog}; fallback: {@link NewThreadScreen}.</li>
 *   <li>{@link #menuItems}: "Register project…" (feat/t3-actions' {@code RegisterProjectDialog}) and
 *       the game shortcuts.</li>
 *   <li>{@link #modelPicker}: feat/t3-actions' {@code ModelPicker} for the composer bar.</li>
 * </ul>
 */
public class T3Slots {
	/** Main's components only (until the branches are merged). */
	public static final T3Slots DEFAULT = new T3Slots();

	/** The selected thread, as the header, actions and dialogs need it. */
	public record Target(String envId, String threadId, @Nullable String title, @Nullable String project) {
	}

	public record MenuItem(String label, boolean enabled, Runnable action) {
	}

	/** What the screen needs from a composer (setThread, render, events, bounds). */
	public interface Composer extends AutoCloseable {
		void setThread(String envId, @Nullable String threadId);

		/** Height wanted at the bottom of the thread panel, in GUI pixels. */
		int preferredHeight();

		void setBounds(Rect r);

		void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY);

		boolean mouseClicked(double mx, double my, int button);

		boolean mouseScrolled(double mx, double my, double dy);

		boolean keyPressed(KeyEvent e);

		boolean charTyped(CharacterEvent e);

		void setFocused(boolean focused);

		void tick();

		@Override
		void close();
	}

	public T3Slots.@Nullable Composer composer() {
		return null;
	}

	/**
	 * Draws the state chip right-aligned at {@code right}; returns its width.
	 */
	public int drawStatusChip(GuiGraphicsExtractor g, Font font, ChatTheme t, ThreadRow row, int right, int y, int mouseX, int mouseY) {
		Attention a = row.attention();
		String word = a.word.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + a.word.substring(1);
		int w = font.width(word) + 18;
		int x = right - w;
		ChatDraw.pillBox(g, t, x, y, w, false);
		if (a == Attention.NEEDS_YOU) {
			ChatDraw.pulsingDot(g, t, ChatTheme.family(a), t.color(a), x + 4, y + 2);
		} else {
			ChatDraw.dot(g, t, ChatTheme.family(a), t.color(a), x + 4, y + 2);
		}
		g.text(font, word, x + 13, y + 2, t.color(a), false);
		return w;
	}

	/** "New thread": project and environment preselected from the sidebar. */
	public void newThread(Screen parent, @Nullable String envId, @Nullable String projectKey, @Nullable Target selected) {
		Minecraft.getInstance().gui.setScreen(new NewThreadScreen(parent, envId, null));
	}

	/** Model picker for the composer bar (feat/t3-actions); null = none. */
	public @Nullable Object modelPicker() {
		return null;
	}

	/** Extra ⋯ menu rows: component actions and game shortcuts for the selected thread. */
	public void menuItems(Screen screen, @Nullable Target selected, List<MenuItem> out) {
		out.add(new MenuItem("Locate the thread's desk", selected != null && deskOf(selected) != null, () -> locateDesk(screen, selected)));
	}

	public void openEnvironments(Screen parent) {
		Minecraft.getInstance().gui.setScreen(new EnvironmentsScreen(parent, EnvironmentActions.DEFAULT));
	}

	public void openSettings(Screen parent) {
		Minecraft.getInstance().gui.setScreen(new SettingsScreen(parent, EnvironmentActions.DEFAULT));
	}

	/** False when the archive module is not up. */
	public boolean openArchive(Screen parent) {
		return dev.t3craft.client.archive.ArchiveClient.openIfReady(parent);
	}

	// ---- game shortcuts -----------------------------------------------------------------------

	static @Nullable StationPlacement deskOf(@Nullable Target t) {
		WorkstationClient w = t == null ? null : WorkstationClient.get();
		if (w == null) {
			return null;
		}
		StationPlacement p = w.placement(t.envId() + ":" + t.threadId());
		return p != null ? p : w.placement(t.threadId());
	}

	/** Closes the screen and says where the desk is (coordinates and distance) in the chat. */
	static void locateDesk(Screen screen, @Nullable Target t) {
		StationPlacement p = deskOf(t);
		Minecraft mc = Minecraft.getInstance();
		if (p == null || t == null || mc.player == null) {
			return;
		}
		mc.gui.setScreen(null);
		double dx = p.computerX() + 0.5 - mc.player.getX();
		double dz = p.computerZ() + 0.5 - mc.player.getZ();
		int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		mc.gui.hud.getChat().addClientSystemMessage(dev.t3craft.client.T3CraftCommands.prefix().append(Component.literal(
				"Desk of \"" + (t.title() == null ? t.threadId() : t.title()) + "\": " + p.computerX() + " " + p.computerY() + " " + p.computerZ()
						+ " (" + dist + " blocks away)")));
	}
}
