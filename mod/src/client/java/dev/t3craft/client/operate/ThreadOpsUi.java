package dev.t3craft.client.operate;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.client.screen.ConfirmActionScreen;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateKind;
import dev.t3craft.t3.OperateResult;
import dev.t3craft.t3.ThreadOps;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Item 22 on the client: settle, snooze, pin / unpin and mark-read of a thread, from the NPC menu,
 * the portable terminal ({@link ThreadOpsBar}) or {@code /t3craft operate …} ({@link ThreadOpsCommands}).
 * Everything goes through {@link OperateUi} / {@link OperateGate}; settle is destructive and asks
 * for the confirmation. A successful settle sends the NPC home ({@code BehaviorDirector.depart}).
 * <i>Client thread only.</i>
 */
public final class ThreadOpsUi {
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(4000L);

	private ThreadOpsUi() {
	}

	public static ThreadOps ops() {
		return ThreadOps.system();
	}

	/** The target for an NPC ({@code <env>:<threadId>} id), or null when its id has no thread. */
	public static ThreadOps.@Nullable Target target(NpcInfo npc) {
		return ThreadOps.Target.parse(npc.id(), npc.title());
	}

	/** From a screen: confirmation for settle, toast with the result. */
	public static void run(OperateAction a, @Nullable Screen returnTo) {
		run(a, OperateGate.Origin.UI, returnTo, r -> toast(a, r));
	}

	public static void run(OperateAction a, OperateGate.Origin origin, @Nullable Screen returnTo, Consumer<OperateResult> feedback) {
		OperateUi.run(a, origin, returnTo, r -> {
			after(a, r);
			feedback.accept(r);
		});
	}

	/**
	 * From a command (chat is closing): the confirmation for a destructive action opens on the
	 * next tick, back to the game afterwards.
	 */
	public static void runFromCommand(OperateAction a, Consumer<OperateResult> feedback) {
		OperateGate gate = OperateUi.gate();
		if (!a.destructive() || !gate.canOperate(a.envId())) {
			run(a, OperateGate.Origin.COMMAND, null, feedback);
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		T3CraftClient.get().openNextTick(new ConfirmActionScreen(null, a, OperateClient.label(a.envId()), yes -> {
			mc.gui.setScreen(null);
			if (!yes) {
				feedback.accept(gate.cancelled(a, OperateGate.Origin.COMMAND));
				return;
			}
			gate.submit(a, OperateGate.Origin.COMMAND, true).thenAccept(r -> mc.execute(() -> {
				after(a, r);
				feedback.accept(r);
			}));
		}));
	}

	/** Side effects of a result: a settled thread's NPC goes home. */
	private static void after(OperateAction a, OperateResult r) {
		if (r.ok() && a.kind() == OperateKind.THREAD_SETTLE && a.threadId() != null) {
			T3CraftClient.get().npcs().behavior().depart(a.envId() + ":" + a.threadId());
		}
	}

	/** Player-facing line for a result ({@code null} when cancelled: nothing to say). */
	public static @Nullable Component message(OperateAction a, OperateResult r) {
		Component kind = Component.translatable(a.kind().langKey());
		return switch (r.outcome()) {
			case CANCELLED -> null;
			case OK -> Component.translatable("t3craft.operate.result.ok", kind);
			default -> Component.translatable("t3craft.operate.result.failed", kind,
					r.outcome().wire.replace('_', ' ').toUpperCase(Locale.ROOT) + " — " + r.message());
		};
	}

	private static void toast(OperateAction a, OperateResult r) {
		Component m = message(a, r);
		if (m != null) {
			SystemToast.addOrUpdate(Minecraft.getInstance().gui.toastManager(), TOAST,
					Component.literal(a.threadTitle() != null ? a.threadTitle() : "T3Craft"), m);
		}
	}
}
