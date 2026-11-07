package dev.t3craft.client.portable;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Navigation state of the portable terminal, kept apart from rendering so it can be unit-tested
 * (no Minecraft classes).
 * <ul>
 *   <li>{@link Page#THREADS}: environment filter ("All" first) + threads of that filter + a
 *   read-only summary of the selected one. "Open" hands the thread to the chat/history screen of
 *   feat/chat through {@link PortableLinks}; this package does not render history itself.</li>
 *   <li>{@link Page#NOTIFICATIONS}: recent notices; opening one selects its thread.</li>
 * </ul>
 * There is no action that changes T3: the terminal is read-only by construction.
 */
public final class PortableNav {
	public enum Page {
		THREADS, NOTIFICATIONS
	}

	private Page page = Page.THREADS;
	private @Nullable String envFilter;
	private int selected;
	private int notificationSelected;
	private int notificationCount;
	private PortableState state = PortableState.EMPTY;

	public Page page() {
		return page;
	}

	public @Nullable String envFilter() {
		return envFilter;
	}

	public int selected() {
		return selected;
	}

	public int notificationSelected() {
		return notificationSelected;
	}

	public PortableState state() {
		return state;
	}

	/** New snapshot: drops a filter whose environment vanished and clamps the selection. */
	public void update(PortableState s) {
		String keep = selectedThread() == null ? null : selectedThread().id();
		state = s;
		if (envFilter != null && !envChoices().contains(envFilter)) {
			envFilter = null;
		}
		// keep the same thread selected when the list reorders (states change priority)
		if (keep != null) {
			List<PortableThread> v = visible();
			for (int i = 0; i < v.size(); i++) {
				if (v.get(i).id().equals(keep)) {
					selected = i;
					break;
				}
			}
		}
		selected = clamp(selected, visible().size());
	}

	/** Environment filter choices: null ("All") then each environment id. */
	public List<@Nullable String> envChoices() {
		List<@Nullable String> out = new ArrayList<>();
		out.add(null);
		state.environments().forEach(e -> out.add(e.id()));
		// bridge mode may list threads of environments it has not reported a status for yet
		state.threads().forEach(t -> {
			if (!t.env().isEmpty() && !out.contains(t.env())) {
				out.add(t.env());
			}
		});
		return out;
	}

	public List<PortableThread> visible() {
		return state.threadsOf(envFilter);
	}

	public @Nullable PortableThread selectedThread() {
		List<PortableThread> v = visible();
		return v.isEmpty() ? null : v.get(Math.min(selected, v.size() - 1));
	}

	public void cycleEnv(int delta) {
		List<@Nullable String> choices = envChoices();
		int i = Math.max(0, choices.indexOf(envFilter));
		int n = choices.size();
		envFilter = choices.get(((i + delta) % n + n) % n);
		selected = 0;
	}

	public void setEnv(@Nullable String env) {
		envFilter = envChoices().contains(env) ? env : null;
		selected = 0;
	}

	public void move(int delta) {
		if (page == Page.THREADS) {
			selected = clamp(selected + delta, visible().size());
		} else {
			notificationSelected = clamp(notificationSelected + delta, notificationCount);
		}
	}

	public void select(int index) {
		selected = clamp(index, visible().size());
	}

	public void showThreads() {
		page = Page.THREADS;
	}

	public void showNotifications(int count) {
		page = Page.NOTIFICATIONS;
		setNotificationCount(count);
	}

	public void togglePage(int notificationCount) {
		if (page == Page.THREADS) {
			showNotifications(notificationCount);
		} else {
			showThreads();
		}
	}

	public void setNotificationCount(int count) {
		notificationCount = count;
		notificationSelected = clamp(notificationSelected, count);
	}

	public void selectNotification(int index) {
		notificationSelected = clamp(index, notificationCount);
	}

	/**
	 * Goes to the thread of a notification: back to THREADS, filter cleared, that thread selected.
	 * Returns false (and stays put) when the thread is not listed any more.
	 */
	public boolean focusThread(@Nullable String npcId) {
		if (npcId == null) {
			return false;
		}
		List<PortableThread> all = state.threads();
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i).id().equals(npcId)) {
				envFilter = null;
				selected = i;
				page = Page.THREADS;
				return true;
			}
		}
		return false;
	}

	/** Back one level. Returns false when already at the root (the screen should close). */
	public boolean back() {
		if (page == Page.NOTIFICATIONS) {
			page = Page.THREADS;
			return true;
		}
		return false;
	}

	private static int clamp(int i, int size) {
		return size <= 0 ? 0 : Math.max(0, Math.min(i, size - 1));
	}
}
