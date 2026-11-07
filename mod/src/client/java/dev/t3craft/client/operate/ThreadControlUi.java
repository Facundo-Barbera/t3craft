package dev.t3craft.client.operate;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.client.screen.ConfirmActionScreen;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateKind;
import dev.t3craft.t3.OperateResult;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.ThreadControl;
import dev.t3craft.t3.ThreadShell;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Item 21 on the client: "Interrupt" / "Stop" buttons for the NPC screen and
 * {@code /t3craft operate interrupt|stop <npc or env:thread>}. Both actions are destructive: they
 * go through {@link ConfirmActionScreen}; read-only environments get disabled buttons
 * ({@link OperateUi#guard}) and a {@code READ_ONLY} answer from the command. The result is shown as
 * a toast (UI) or a chat line (command); {@code FAILED} is never retried.
 */
public final class ThreadControlUi {
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(5000L);

	private ThreadControlUi() {
	}

	/** The thread's current {@code activeRunId} from the direct shell view, or null (idle / bridge mode). */
	public static @Nullable String activeRunId(ThreadControl.Target t) {
		T3CraftClient c = T3CraftClient.get();
		DirectController d = c == null ? null : c.direct();
		ShellState.View v = d == null ? null : d.shellViews().get(t.envId());
		if (v == null) {
			return null;
		}
		for (ThreadShell s : v.threads()) {
			if (s.id().equals(t.threadId())) {
				return s.activeRunId();
			}
		}
		return null;
	}

	// ---- UI -----------------------------------------------------------------------------------

	/**
	 * Adds the "Interrupt" and "Stop" buttons for {@code npc} at {@code (x, y)} (each {@code w/2 - 2}
	 * wide), guarded by {@link OperateUi#guard}. Returns false (and adds nothing) when the NPC id has
	 * no environment (bridge NPCs).
	 */
	public static boolean addButtons(Screen screen, Consumer<Button> add, NpcInfo npc, int x, int y, int w) {
		ThreadControl.Target t = ThreadControl.parse(npc.id());
		if (t == null) {
			return false;
		}
		int half = w / 2 - 2;
		Button interrupt = Button.builder(Component.translatable("t3craft.operate.control.interrupt"),
				b -> interrupt(t, npc.title(), OperateGate.Origin.UI, screen, f -> toast(OperateKind.RUN_INTERRUPT, f)))
				.bounds(x, y, half, 20).build();
		Button stop = Button.builder(Component.translatable("t3craft.operate.control.stop"),
				b -> stop(t, npc.title(), OperateGate.Origin.UI, screen, f -> toast(OperateKind.THREAD_STOP, f)))
				.bounds(x + w - half, y, half, 20).build();
		if (OperateUi.guard(interrupt, t.envId()) && activeRunId(t) == null) {
			interrupt.active = false;
			interrupt.setTooltip(Tooltip.create(Component.translatable("t3craft.operate.control.no_run")));
		}
		OperateUi.guard(stop, t.envId());
		add.accept(interrupt);
		add.accept(stop);
		return true;
	}

	public static void interrupt(ThreadControl.Target t, @Nullable String title, OperateGate.Origin origin, @Nullable Screen returnTo,
			Consumer<ThreadControl.Feedback> done) {
		String runId = activeRunId(t);
		if (runId == null && OperateUi.canOperate(t.envId())) {
			done.accept(new ThreadControl.Feedback(false, "no active run to interrupt"));
			return;
		}
		OperateGate.Env env = OperateUi.gate().operate(t.envId());
		// Read-only: the gate answers READ_ONLY (and audits it) whatever the run id.
		OperateAction a = ThreadControl.interrupt(env, t.threadId(), title, runId == null ? "unknown" : runId);
		run(a, origin, returnTo, done);
	}

	public static void stop(ThreadControl.Target t, @Nullable String title, OperateGate.Origin origin, @Nullable Screen returnTo,
			Consumer<ThreadControl.Feedback> done) {
		run(ThreadControl.stop(OperateUi.gate().operate(t.envId()), t.threadId(), title), origin, returnTo, done);
	}

	private static void run(OperateAction a, OperateGate.Origin origin, @Nullable Screen returnTo, Consumer<ThreadControl.Feedback> done) {
		Consumer<OperateResult> show = r -> done.accept(ThreadControl.feedback(r));
		if (origin == OperateGate.Origin.UI) {
			OperateUi.run(a, origin, returnTo, show);
			return;
		}
		// From a command the chat screen is still closing: open the confirmation on the next tick.
		OperateGate gate = OperateUi.gate();
		T3CraftClient client = T3CraftClient.get();
		if (!gate.canOperate(a.envId()) || client == null) {
			OperateUi.run(a, origin, returnTo, show);
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		client.openNextTick(new ConfirmActionScreen(null, a, OperateClient.label(a.envId()), yes -> {
			mc.gui.setScreen(null);
			if (yes) {
				gate.submit(a, origin, true).thenAccept(r -> mc.execute(() -> show.accept(r)));
			} else {
				show.accept(gate.cancelled(a, origin));
			}
		}));
	}

	private static void toast(OperateKind kind, ThreadControl.Feedback f) {
		Minecraft mc = Minecraft.getInstance();
		SystemToast.addOrUpdate(mc.gui.toastManager(), TOAST,
				Component.translatable(kind.langKey()).withStyle(f.ok() ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
				Component.literal(f.text()));
	}

	// ---- command ------------------------------------------------------------------------------

	private static final SuggestionProvider<FabricClientCommandSource> NPCS = (c, b) -> {
		T3CraftClient client = T3CraftClient.get();
		if (client != null) {
			client.npcs().all().stream().filter(n -> ThreadControl.parse(n.id()) != null).forEach(n -> b.suggest(n.id()));
		}
		return b.buildFuture();
	};

	/**
	 * {@code operate interrupt|stop <target>}, merged by Brigadier into the existing
	 * {@code /t3craft operate} node (same literal, no executor here).
	 */
	public static LiteralArgumentBuilder<FabricClientCommandSource> commands() {
		return literal("operate")
				.then(literal("interrupt").then(argument("target", StringArgumentType.greedyString()).suggests(NPCS)
						.executes(c -> command(c, OperateKind.RUN_INTERRUPT))))
				.then(literal("stop").then(argument("target", StringArgumentType.greedyString()).suggests(NPCS)
						.executes(c -> command(c, OperateKind.THREAD_STOP))));
	}

	private static int command(CommandContext<FabricClientCommandSource> c, OperateKind kind) {
		FabricClientCommandSource src = c.getSource();
		String arg = StringArgumentType.getString(c, "target").trim();
		T3CraftClient client = T3CraftClient.get();
		List<NpcInfo> npcs = client == null ? List.of() : client.npcs().all();
		NpcInfo npc = resolve(npcs, arg);
		ThreadControl.Target t = npc != null ? ThreadControl.parse(npc.id()) : ThreadControl.parse(arg);
		if (t == null) {
			src.sendError(T3CraftCommands.prefix().append(Component.literal("no NPC \"" + arg + "\": use its name or env:thread")));
			return 0;
		}
		String title = npc != null ? npc.title() : null;
		Consumer<ThreadControl.Feedback> done = f -> src.sendFeedback(T3CraftCommands.prefix()
				.append(Component.translatable(kind.langKey())).append(": ")
				.append(Component.literal(f.text()).withStyle(f.ok() ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
		if (kind == OperateKind.RUN_INTERRUPT) {
			interrupt(t, title, OperateGate.Origin.COMMAND, null, done);
		} else {
			stop(t, title, OperateGate.Origin.COMMAND, null, done);
		}
		return 1;
	}

	/** By NPC id, then a unique case-insensitive label or title match. */
	static @Nullable NpcInfo resolve(List<NpcInfo> npcs, String arg) {
		for (NpcInfo n : npcs) {
			if (n.id().equals(arg)) {
				return n;
			}
		}
		String q = arg.toLowerCase(Locale.ROOT);
		List<NpcInfo> named = npcs.stream()
				.filter(n -> n.label().toLowerCase(Locale.ROOT).equals(q) || n.title().toLowerCase(Locale.ROOT).equals(q)).toList();
		return named.size() == 1 ? named.get(0) : null;
	}
}
