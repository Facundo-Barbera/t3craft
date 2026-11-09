package dev.t3craft.client.operate;

import dev.t3craft.client.ModConfig;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.client.search.SearchLines;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.Environments;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateResult;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.ThreadLaunch;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Client glue for item 23 (launch a thread), shared by the T3 screen's draft launcher and
 * {@code /t3craft operate new}. Reads the shell read-only from {@link DirectController#shellViews()};
 * the only write goes through {@link OperateUi#run}. <i>Client thread only.</i>
 */
public final class LaunchClient {
	private LaunchClient() {
	}

	/** Paired environments, in configuration order. */
	public static List<Environments.Environment> environments() {
		return new Environments(ModConfig.dataDir()).list();
	}

	/** Ids of the paired environments with control (operate) enabled. */
	public static List<String> operableEnvs() {
		return ThreadLaunch.operableEnvs(environments().stream().map(Environments.Environment::id).toList(), OperateUi.gate());
	}

	/** Latest read-only shell view of {@code envId}, or null when not connected (bridge mode, offline). */
	public static ShellState.@Nullable View view(String envId) {
		T3CraftClient c = T3CraftClient.get();
		DirectController d = c == null ? null : c.direct();
		return d == null ? null : d.shellViews().get(envId);
	}

	public static List<ThreadLaunch.ProjectChoice> projects(String envId) {
		return ThreadLaunch.projects(view(envId));
	}

	/** Validates, builds and runs the launch. {@code done} runs once on the client thread. */
	public static void launch(ThreadLaunch.Request r, OperateGate.Origin origin, @Nullable Screen returnTo, Consumer<OperateResult> done) {
		ThreadLaunch.Built b = ThreadLaunch.build(r, view(r.envId()), ThreadLaunch.Ids::random);
		if (b.action() == null) {
			done.accept(OperateResult.of(OperateResult.Outcome.REJECTED, b.error()));
			return;
		}
		OperateUi.run(b.action(), origin, returnTo, done);
	}

	/**
	 * Chat line for a result: the outcome, and when T3 returned the new thread id, a clickable
	 * "[open chat]" link (the search's open helper, which hands it to the chat screen).
	 */
	public static MutableComponent resultLine(String envId, OperateResult r) {
		MutableComponent line = T3CraftCommands.prefix().append(Component.literal(r.ok() ? "thread launched in " + envId : r.outcome().wire + ": " + r.message())
				.withStyle(r.ok() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		String threadId = ThreadLaunch.threadId(r);
		if (threadId != null) {
			line.append(Component.literal(" "));
			line.append(openLink(envId, threadId));
		}
		return line;
	}

	public static MutableComponent openLink(String envId, String threadId) {
		return Component.literal("[open chat]").withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
				.withClickEvent(new ClickEvent.RunCommand(SearchLines.openCommand(envId, threadId)))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Open " + threadId + " in the chat"))));
	}
}
