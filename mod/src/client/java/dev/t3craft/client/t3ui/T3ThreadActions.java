package dev.t3craft.client.t3ui;

import dev.t3craft.client.chat.Attention;
import dev.t3craft.client.chat.ThreadRow;
import dev.t3craft.client.operate.OperateUi;
import dev.t3craft.client.operate.ThreadControlUi;
import dev.t3craft.client.operate.ThreadOpsUi;
import dev.t3craft.client.screen.SnoozeScreen;
import dev.t3craft.client.t3ui.actions.ActionsClient;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateKind;
import dev.t3craft.t3.ThreadControl;
import dev.t3craft.t3.ThreadOps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The thread actions that used to be the header's button row (feat/t3-ui-clean), now where T3 Code
 * puts them: the sidebar row's right-click menu ({@link #menu}), its hover icons (settle, pin) and,
 * only while the thread runs, Stop in the header (the composer's send button is the interrupt).
 * Every action still goes through the guarded flows: {@link ThreadOpsUi} / {@link ThreadControlUi} /
 * {@link ActionsClient#archive} on top of {@link OperateUi#run} (read-only → nothing sent;
 * destructive ones ask in game first). Nothing here writes by itself.
 */
final class T3ThreadActions {
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(5001L);

	private T3ThreadActions() {
	}

	static ThreadOps.Target ops(T3Slots.Target t) {
		return new ThreadOps.Target(t.envId(), t.threadId(), t.title());
	}

	static ThreadControl.Target control(T3Slots.Target t) {
		return new ThreadControl.Target(t.envId(), t.threadId());
	}

	/** The row's thread as a target, or null for a bridge row without a thread id. */
	static T3Slots.@Nullable Target target(ThreadRow r) {
		return r.threadId() == null ? null : new T3Slots.Target(r.env(), r.threadId(), r.title(), r.project());
	}

	static boolean canOperate(T3Slots.Target t) {
		return OperateUi.canOperate(t.envId());
	}

	/** A turn is running: an active run in the shell, or the row says working. */
	static boolean running(@Nullable ThreadRow r, T3Slots.Target t) {
		return ThreadControlUi.activeRunId(control(t)) != null || r != null && r.attention() == Attention.WORKING;
	}

	static void settle(Screen screen, T3Slots.Target t) {
		ThreadOpsUi.run(ThreadOpsUi.ops().settle(ops(t)), screen);
	}

	static void pin(Screen screen, T3Slots.Target t, boolean pin) {
		ThreadOpsUi.run(pin ? ThreadOpsUi.ops().pin(ops(t)) : ThreadOpsUi.ops().unpin(ops(t)), screen);
	}

	static void markRead(Screen screen, T3Slots.Target t) {
		ThreadOpsUi.run(ThreadOpsUi.ops().markRead(ops(t)), screen);
	}

	static void snooze(Screen screen, T3Slots.Target t) {
		Minecraft.getInstance().gui.setScreen(new SnoozeScreen(screen, ops(t)));
	}

	static void interrupt(Screen screen, T3Slots.Target t) {
		ThreadControlUi.interrupt(control(t), t.title(), OperateGate.Origin.UI, screen, f -> toast(OperateKind.RUN_INTERRUPT, f));
	}

	static void stop(Screen screen, T3Slots.Target t) {
		ThreadControlUi.stop(control(t), t.title(), OperateGate.Origin.UI, screen, f -> toast(OperateKind.THREAD_STOP, f));
	}

	static void archive(Screen screen, T3Slots.Target t) {
		ActionsClient.archive(t.envId(), t.threadId(), t.title(), screen, r -> {
		});
	}

	/**
	 * The right-click menu of a sidebar thread (T3's thread context menu): Interrupt and Stop while it
	 * runs, Settle, Snooze…, Pin/Unpin, Mark as read, Archive, then the game shortcut and "Enable
	 * control…" when the environment is read-only (the writes are disabled then).
	 */
	static List<T3Slots.MenuItem> menu(Screen screen, @Nullable ThreadRow row, T3Slots.Target t) {
		boolean can = canOperate(t);
		boolean running = running(row, t);
		boolean pinned = row != null && row.pinned();
		List<T3Slots.MenuItem> out = new ArrayList<>();
		if (running) {
			out.add(new T3Slots.MenuItem("Interrupt turn", can && ThreadControlUi.activeRunId(control(t)) != null, () -> interrupt(screen, t)));
			out.add(new T3Slots.MenuItem("Stop thread", can, () -> stop(screen, t)));
		}
		out.add(new T3Slots.MenuItem("Settle", can, () -> settle(screen, t)));
		out.add(new T3Slots.MenuItem("Snooze…", can, () -> snooze(screen, t)));
		out.add(new T3Slots.MenuItem(pinned ? "Unpin" : "Pin", can, () -> pin(screen, t, !pinned)));
		out.add(new T3Slots.MenuItem("Mark as read", can, () -> markRead(screen, t)));
		out.add(new T3Slots.MenuItem("Archive", can, () -> archive(screen, t)));
		out.add(new T3Slots.MenuItem("Locate the thread's desk", T3Slots.deskOf(t) != null, () -> T3Slots.locateDesk(screen, t)));
		if (!can) {
			out.add(new T3Slots.MenuItem("Enable control…", true,
					() -> Minecraft.getInstance().gui.setScreen(OperateUi.enableControlScreen(screen, t.envId()))));
		}
		return out;
	}

	private static void toast(OperateKind kind, ThreadControl.Feedback f) {
		Minecraft mc = Minecraft.getInstance();
		SystemToast.addOrUpdate(mc.gui.toastManager(), TOAST,
				Component.translatable(kind.langKey()).withStyle(f.ok() ? ChatFormatting.GREEN : ChatFormatting.YELLOW), Component.literal(f.text()));
	}

	/** A short toast for the screen's own notices (what used to be the header's flash line). */
	static void notice(String text) {
		Minecraft mc = Minecraft.getInstance();
		SystemToast.addOrUpdate(mc.gui.toastManager(), TOAST, Component.literal("T3"), Component.literal(text));
	}
}
