package dev.t3craft.tools.uirender;

import com.google.gson.JsonObject;
import dev.t3craft.client.chat.ChatSource;
import dev.t3craft.client.chat.ConversationPanel;
import dev.t3craft.client.chat.ThreadCache;
import dev.t3craft.client.chat.ThreadDirectory;
import dev.t3craft.client.chat.ThreadList;
import dev.t3craft.client.chat.ThreadRow;
import dev.t3craft.client.t3ui.T3Screen;
import dev.t3craft.client.t3ui.T3Slots;
import dev.t3craft.client.t3ui.actions.ActionsClient;
import dev.t3craft.client.t3ui.actions.ProjectsService;
import dev.t3craft.client.t3ui.actions.ProviderService;
import dev.t3craft.t3.Environments;
import dev.t3craft.t3.Pairing;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.StreamRegistry;
import dev.t3craft.t3.T3Connection;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Wires a {@link Fixture} into the places the screens read from, with no network and no T3: the
 * {@link ThreadDirectory}, the {@link ChatSource} (it answers {@code subscribeThread} with the
 * fixture's snapshot at once), the conversation actions, and the few static services behind the
 * dialogs (providers, projects, paired environments, operate sessions in a scratch data dir).
 */
final class Mock {
	private final Fixture fx;

	Mock(Fixture fx) {
		this.fx = fx;
	}

	private Fixture.@Nullable Env env(String id) {
		return fx.envs.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
	}

	/** Scratch data dir with the paired environments and operate sessions, and the static services. */
	void prepare() throws IOException {
		Path dir = Headless.freshDataDir();
		Environments environments = new Environments(dir);
		long expires = System.currentTimeMillis() + 7L * 86_400_000L;
		for (Fixture.Env e : fx.envs) {
			Environments.Environment pairing = new Environments.Environment(e.id(), e.label(), URI.create("http://127.0.0.1:3773/"));
			environments.pair(pairing, new Pairing.Session("mock-token-" + e.id(), e.control() ? Pairing.OPERATE_SCOPES : Pairing.READ_SCOPES, expires));
		}
		Headless.operateAt(dir);

		Map<String, ShellState.View> views = new LinkedHashMap<>();
		for (Fixture.Env e : fx.envs) {
			Map<String, String> titles = new LinkedHashMap<>();
			fx.projects.stream().filter(p -> p.env().equals(e.id())).forEach(p -> titles.put(p.id(), p.title()));
			views.put(e.id(), new ShellState.View(List.of(), titles, true));
		}
		Headless.set(null, ActionsClient.class, "providers",
				new ProviderService(id -> CompletableFuture.completedFuture(fx.serverConfig.deepCopy()), System::currentTimeMillis));
		Headless.set(null, ActionsClient.class, "projects", new ProjectsService(() -> views, id -> {
			Fixture.Env e = env(id);
			return e == null ? id : e.label();
		}));

		// the transcript cache is a process-wide singleton: every scenario starts empty
		ThreadCache cache = ThreadCache.get();
		Object entries = Headless.get(cache, ThreadCache.class, "entries");
		if (entries instanceof Map<?, ?> m) {
			m.clear();
		}
		Headless.set(cache, ThreadCache.class, "lastOpened", null);
	}

	// ---- data sources ----------------------------------------------------------------------------

	ThreadDirectory directory() {
		Map<String, ThreadList.EnvInfo> envs = new LinkedHashMap<>();
		for (Fixture.Env e : fx.envs) {
			envs.put(e.id(), new ThreadList.EnvInfo(e.label(), e.status(), e.color()));
		}
		List<ThreadRow> rows = List.copyOf(fx.rows);
		return new ThreadDirectory() {
			@Override
			public List<ThreadRow> rows() {
				return rows;
			}

			@Override
			public boolean cached() {
				return fx.cached;
			}

			@Override
			public Map<String, ThreadList.EnvInfo> environments() {
				return envs;
			}
		};
	}

	ChatSource source() {
		return envId -> {
			Fixture.Env e = env(envId);
			if (e == null) {
				throw new ChatSource.Unavailable("No paired environment " + envId, ChatSource.Unavailable.Action.PAIR);
			}
			return new ChatSource.Env() {
				@Override
				public String id() {
					return e.id();
				}

				@Override
				public String label() {
					return e.label();
				}

				@Override
				public T3Connection.Status status() {
					try {
						return T3Connection.Status.valueOf(e.status().toUpperCase(Locale.ROOT));
					} catch (IllegalArgumentException ex) {
						return T3Connection.Status.OK;
					}
				}

				@Override
				public String statusDetail() {
					return status() == T3Connection.Status.OFFLINE ? "connection refused" : "";
				}

				@Override
				public StreamRegistry.Subscription subscribeThread(String threadId, StreamRegistry.Listener listener) {
					if (status() == T3Connection.Status.OK) {
						JsonObject snap = fx.snapshots.get(e.id() + ":" + threadId);
						JsonObject sync = new JsonObject();
						sync.addProperty("kind", "synchronized");
						listener.onSubscribed();
						List<JsonObject> items = new ArrayList<>();
						if (snap != null) {
							items.add(snap);
						}
						items.add(sync);
						listener.onItems(items);
					}
					return () -> {
					};
				}

				@Override
				public CompletableFuture<JsonObject> threadHistory(String threadId, String cursor) {
					return CompletableFuture.failedFuture(new IllegalStateException("mock: no older history"));
				}

				@Override
				public CompletableFuture<@Nullable JsonObject> getTurnItem(String threadId, String itemId) {
					return CompletableFuture.completedFuture(null);
				}
			};
		};
	}

	ConversationPanel.Actions actions() {
		return new ConversationPanel.Actions() {
			@Override
			public boolean canOperate(String envId) {
				Fixture.Env e = env(envId);
				return e != null && e.control();
			}

			@Override
			public void enableControl(String envId) {
			}

			@Override
			public void pair() {
			}
		};
	}

	// ---- screens ---------------------------------------------------------------------------------

	/** The main screen ({@code new-thread}: with a draft open), and a screen drawn over it if any. */
	record Screens(T3Screen main, @Nullable Screen top) {
	}

	Screens build() {
		T3Screen main = new T3Screen(directory(), source(), actions(), T3Slots.DEFAULT, fx.selected);
		if (!"new-thread".equals(fx.screen)) {
			return new Screens(main, null);
		}
		JsonObject nt = fx.raw.has("newThread") && fx.raw.get("newThread").isJsonObject() ? fx.raw.getAsJsonObject("newThread") : new JsonObject();
		String envId = nt.has("env") ? nt.get("env").getAsString() : fx.envs.isEmpty() ? null : fx.envs.get(0).id();
		String projectId = nt.has("project") ? nt.get("project").getAsString() : null;
		// feat/t3-composer-fidelity: "New thread" is a draft row + the composer in the main screen, no dialog
		return new Screens(main.withDraft(envId, projectId), null);
	}
}
