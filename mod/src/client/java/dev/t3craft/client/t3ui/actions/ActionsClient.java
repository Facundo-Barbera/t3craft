package dev.t3craft.client.t3ui.actions;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.operate.LaunchClient;
import dev.t3craft.client.operate.OperateClient;
import dev.t3craft.client.operate.OperateUi;
import dev.t3craft.client.search.SearchClient;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.Environments;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateResult;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.T3Connection;
import dev.t3craft.t3.ThreadLaunch;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Client glue of the T3 actions: the shared services ({@link #providers()}, {@link #projects()},
 * {@link #commands()}) and the writes, all through {@link OperateUi#run} (so {@link OperateGate}
 * decides: read-only → nothing sent; destructive → in-game confirmation). Callbacks run once on
 * the client thread. <i>Client thread only.</i>
 */
public final class ActionsClient {
	private static @Nullable ProviderService providers;
	private static @Nullable ProjectsService projects;
	private static final ThreadCommands COMMANDS = ThreadCommands.system();

	private ActionsClient() {
	}

	public static @Nullable DirectController direct() {
		T3CraftClient c = T3CraftClient.get();
		return c == null ? null : c.direct();
	}

	public static synchronized ProviderService providers() {
		if (providers == null) {
			providers = new ProviderService(env -> {
				DirectController d = direct();
				T3Connection c = d == null ? null : d.connection(env);
				return c == null ? null : c.getServerConfig();
			}, System::currentTimeMillis);
		}
		return providers;
	}

	public static synchronized ProjectsService projects() {
		if (projects == null) {
			projects = new ProjectsService(() -> {
				DirectController d = direct();
				return d == null ? Map.<String, ShellState.View>of() : d.shellViews();
			}, OperateClient::label);
		}
		return projects;
	}

	public static ThreadCommands commands() {
		return COMMANDS;
	}

	public static ActionGate gate() {
		return ActionGate.of(OperateUi.gate());
	}

	/** Paired environment ids, in configuration order. */
	public static List<String> environments() {
		return LaunchClient.environments().stream().map(Environments.Environment::id).toList();
	}

	public static String envLabel(String envId) {
		return OperateClient.label(envId);
	}

	public static ThreadLaunch.@Nullable ModelChoice projectDefault(String envId, @Nullable String projectId) {
		return projectId == null ? null : ThreadLaunch.resolveModel(LaunchClient.view(envId), projectId, null);
	}

	/** Opens the enable-control (or pairing) screen; what disabled widgets do on click. */
	public static void enableControl(@Nullable Screen parent, @Nullable String envId) {
		net.minecraft.client.Minecraft.getInstance().gui.setScreen(OperateUi.enableControlScreen(parent, envId));
	}

	/** Selects (opens) a thread in the chat, the default "thread created" behaviour. */
	public static void selectThread(String envId, String threadId) {
		T3CraftClient c = T3CraftClient.get();
		if (c != null) {
			SearchClient.open(c, envId, threadId);
		}
	}

	/**
	 * Runs the actions one after the other and stops at the first that is not OK. {@code done} gets
	 * the last result (OK when the list is empty).
	 */
	public static void runAll(List<OperateAction> actions, @Nullable Screen returnTo, Consumer<OperateResult> done) {
		Deque<OperateAction> q = new ArrayDeque<>(actions);
		step(q, returnTo, done, OperateResult.ok(null));
	}

	private static void step(Deque<OperateAction> q, @Nullable Screen returnTo, Consumer<OperateResult> done, OperateResult last) {
		OperateAction a = q.poll();
		if (a == null) {
			done.accept(last);
			return;
		}
		OperateUi.run(a, OperateGate.Origin.UI, returnTo, r -> {
			if (!r.ok()) {
				done.accept(r);
			} else {
				step(q, returnTo, done, r);
			}
		});
	}

	/** Applies the picker's changes to a thread (only the changed commands). */
	public static void configureThread(String envId, String threadId, @Nullable String title, ModelPickerLogic logic,
			@Nullable Screen returnTo, Consumer<OperateResult> done) {
		ModelPick pick = logic.pick();
		if (pick == null) {
			done.accept(OperateResult.of(OperateResult.Outcome.REJECTED, "no model chosen"));
			return;
		}
		runAll(COMMANDS.configure(envId, threadId, title, pick, logic.changed()), returnTo, r -> {
			if (r.ok()) {
				logic.commit();
			}
			done.accept(r);
		});
	}

	/** Archives a thread ({@code thread.archive}; destructive → confirmation screen). */
	public static void archive(String envId, String threadId, @Nullable String title, @Nullable Screen returnTo, Consumer<OperateResult> done) {
		OperateUi.run(COMMANDS.archive(envId, threadId, title), OperateGate.Origin.UI, returnTo, done);
	}
}
