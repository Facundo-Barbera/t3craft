package dev.t3craft.client.t3ui;

import dev.t3craft.client.chat.Attention;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.ThreadRow;
import dev.t3craft.client.chat.compose.ComposerView;
import dev.t3craft.client.config.EnvironmentActions;
import dev.t3craft.client.config.EnvironmentsScreen;
import dev.t3craft.client.config.SettingsScreen;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.t3ui.actions.ActionsClient;
import dev.t3craft.client.t3ui.actions.ProjectsService;
import dev.t3craft.client.t3ui.actions.RegisterProjectDialog;
import dev.t3craft.client.t3ui.actions.ThreadStatus;
import dev.t3craft.client.t3ui.actions.ThreadStatusChip;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.ThreadShell;
import java.util.function.Supplier;
import net.minecraft.client.input.KeyEvent;
import dev.t3craft.client.workstation.StationPlacement;
import dev.t3craft.client.workstation.WorkstationClient;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Where the unified screen mounts the self-contained widgets of the parallel branches. {@link T3Screen}
 * only talks to this class:
 * <ul>
 *   <li>{@link #drawStatusChip}: feat/t3-actions' {@link ThreadStatusChip} (T3's status pill) from the
 *       thread's shell; fallback without a shell (bridge mode): a pill with the attention word.</li>
 *   <li>{@link #draftProject}: which project a new draft thread starts on (feat/t3-composer: "New
 *       thread" opens no dialog, {@link T3Screen} adds a draft row and the composer launches it).</li>
 *   <li>{@link #menuItems}: "Register project…" ({@link RegisterProjectDialog}), "Archive thread"
 *       (T3's {@code thread.archive}) and the game shortcuts.</li>
 *   <li>{@link #composerControls}: feat/t3-actions' {@link dev.t3craft.client.t3ui.actions.ModelPicker}
 *       as the composer bar's model chip, plus the access chip (the composer itself, feat/chat-compose,
 *       is mounted by {@link T3Screen} as the conversation's footer).</li>
 * </ul>
 */
public class T3Slots {
	public static final T3Slots DEFAULT = new T3Slots();

	/** The selected thread, as the header, actions and dialogs need it. */
	public record Target(String envId, String threadId, @Nullable String title, @Nullable String project) {
	}

	public record MenuItem(String label, boolean enabled, Runnable action) {
	}

	/** A control whose popup lies outside its rectangle: the host routes input to it while open. */
	public interface Popup {
		boolean isOpen();

		boolean mouseClicked(double mx, double my, int button);

		boolean keyPressed(KeyEvent e);

		boolean mouseScrolled(double mx, double my, double dy);

		/** A typed character while open (a field inside the popover). */
		default boolean charTyped(int codepoint) {
			return false;
		}
	}

	/** Controls in the composer bar's left slot: the model / provider / effort picker. */
	public List<ComposerView.Control> composerControls(Screen screen, Supplier<@Nullable Target> target) {
		return composerControls(screen, target, () -> null);
	}

	/** Same, aware of the screen's draft thread (its model chip edits the draft's model). */
	List<ComposerView.Control> composerControls(Screen screen, Supplier<@Nullable Target> target, Supplier<@Nullable ThreadDraft> draft) {
		ModelPickerControl model = new ModelPickerControl(target, draft);
		return List.of(model.access(), model);
	}

	/** The thread's shell in direct mode (status, model), or null. */
	static @Nullable ThreadShell shellOf(String envId, String threadId) {
		T3CraftClient c = T3CraftClient.get();
		DirectController d = c == null ? null : c.direct();
		ShellState.View v = d == null ? null : d.view(envId);
		if (v == null) {
			return null;
		}
		for (ThreadShell s : v.threads()) {
			if (s.id().equals(threadId)) {
				return s;
			}
		}
		return null;
	}

	/** Draws the state chip right-aligned at {@code right}; returns its width. */
	public int drawStatusChip(GuiGraphicsExtractor g, Font font, ChatTheme t, ThreadRow row, int right, int y, int mouseX, int mouseY) {
		ThreadShell shell = row.threadId() == null ? null : shellOf(row.env(), row.threadId());
		if (shell != null) {
			ThreadStatus st = ThreadStatus.of(shell);
			int w = ThreadStatusChip.width(font, st);
			return ThreadStatusChip.draw(g, font, st, right - w, y, -1, System.currentTimeMillis());
		}
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

	/**
	 * The project a new draft thread starts on: the sidebar folder {@code projectKey} (filter or the
	 * folder's "+"), else the selected thread's project, else the first project of {@code envId}
	 * (preferring environments with control), else any. Null when no environment has a project.
	 */
	public ProjectsService.@Nullable Project draftProject(@Nullable String envId, @Nullable String projectKey, @Nullable Target selected) {
		List<ProjectsService.Project> all = ActionsClient.projects().all();
		ProjectsService.Project p = ProjectMatch.find(all, projectKey != null ? projectKey
				: selected == null || selected.project() == null ? null : SidebarModel.fold(selected.project()).trim(), envId);
		if (p != null) {
			return p;
		}
		for (ProjectsService.Project q : all) {
			if (envId != null && envId.equals(q.envId())) {
				return q;
			}
		}
		for (ProjectsService.Project q : all) {
			if (ActionsClient.gate().enabled(q.envId())) {
				return q;
			}
		}
		return all.isEmpty() ? null : all.getFirst();
	}

	/** A project by environment and id (a block's "new thread here"), or null. */
	public ProjectsService.@Nullable Project project(@Nullable String envId, @Nullable String projectId) {
		if (envId == null || projectId == null) {
			return null;
		}
		return ActionsClient.projects().find(new ProjectsService.Key(envId, projectId));
	}

	/** Extra ⋯ menu rows: component actions and game shortcuts for the selected thread. */
	public void menuItems(Screen screen, @Nullable Target selected, List<MenuItem> out) {
		out.add(new MenuItem("Register project…", true, () -> Minecraft.getInstance().gui.setScreen(
				new RegisterProjectDialog(screen, selected == null ? null : selected.envId(), (env, projectId) -> {
				}))));
		out.add(new MenuItem("Archive thread", selected != null, () -> {
			if (selected != null) {
				ActionsClient.archive(selected.envId(), selected.threadId(), selected.title(), screen, r -> {
				});
			}
		}));
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
