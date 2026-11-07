package dev.t3craft.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.t3craft.T3Craft;
import dev.t3craft.client.hud.Notifier;
import dev.t3craft.client.hud.PeekHud;
import dev.t3craft.client.hud.Toasts;
import dev.t3craft.client.link.BridgeLink;
import dev.t3craft.client.link.BridgeListener;
import dev.t3craft.client.link.LinkStatus;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.client.npc.NpcRegistry;
import dev.t3craft.client.npc.helpers.HelperConfig;
import dev.t3craft.client.npc.helpers.HelperDirector;
import dev.t3craft.client.npc.helpers.HelperRoster;
import dev.t3craft.client.portable.PortableClient;
import dev.t3craft.debug.HotSwapFields;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.Environments;
import dev.t3craft.t3.Pairing;
import dev.t3craft.t3.PairingClient;
import dev.t3craft.t3.T3Connection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import org.jspecify.annotations.Nullable;

/**
 * Client entrypoint. Two interchangeable sources feed the same {@link NpcRegistry}:
 * <ul>
 *   <li><b>direct</b> (default): the mod is paired with each T3 environment and reads it itself
 *   ({@link DirectController}; read-only, {@code orchestration:read}).</li>
 *   <li><b>bridge</b> (legacy): the t3craft bridge sends ready-made NPCs ({@link BridgeLink}).</li>
 * </ul>
 * {@code /t3craft reconnect} and {@code /t3craft reload} work for both without restarting the game.
 */
public final class T3CraftClient implements ClientModInitializer, BridgeListener {
	/** Re-run the direct-mode filter this often so the recent window expires on its own (ticks). */
	private static final int REEVALUATE_TICKS = 20 * 60;

	private static T3CraftClient instance;

	private final NpcRegistry npcs = new NpcRegistry();
	/**
	 * feat/npc-helpers: subagents as small villagers following their parent's NPC (direct mode).
	 * Read via {@link #helpers()} / {@link #helperConfig()}: may be null after a hot-swap (docs/dev.md).
	 */
	private @Nullable HelperDirector helpers;
	private @Nullable HelperConfig helperConfig;
	private final AtomicBoolean directDirty = new AtomicBoolean();
	private final Map<String, String> envStatus = new HashMap<>();
	private final Map<String, String[]> envDetail = new LinkedHashMap<>();
	private String modVersion = "dev";
	private ModConfig config;
	private @Nullable BridgeLink link;
	private @Nullable DirectController direct;
	private LinkStatus lastStatus = LinkStatus.DISABLED;
	private boolean started;
	private int ticks;
	private net.minecraft.client.gui.screens.@Nullable Screen pendingScreen;

	public static T3CraftClient get() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		modVersion = FabricLoader.getInstance().getModContainer(T3Craft.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		config = ModConfig.load();
		helperConfig = loadHelperConfig();
		helpers(); // creates the director and wires its panel extra

		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
			started = true;
			startSource();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> stopSource());
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (pendingScreen != null) {
				mc.gui.setScreen(pendingScreen);
				pendingScreen = null;
			}
			if (direct != null && (directDirty.getAndSet(false) || ++ticks % REEVALUATE_TICKS == 0)) {
				try {
					refreshDirect(mc);
				} catch (RuntimeException | LinkageError e) {
					// Retried by the next change or the periodic re-evaluation; never per tick.
					T3Craft.LOGGER.warn("T3Craft refresh failed; will retry", e);
				}
			}
			npcs.tick(mc);
			helpers().tick(mc, npcs::avatar);
		});
		T3CraftCommands.register(this);
		dev.t3craft.hq.HqPreviewCommand.register(npcs::all); // feat/hq-design: /t3craft debug hq-preview
		PeekHud.register();
		dev.t3craft.client.hud.StatusBar.register(this); // feat/hud-bar
		PortableClient.register(this);
		dev.t3craft.client.search.SearchClient.register(this); // feat/search: /t3craft search

		// Right-click on one of our client-only NPCs opens its chat screen. The entity is unknown to
		// the server, so the interaction is consumed client-side.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide()) {
				return InteractionResult.PASS;
			}
			String threadId = npcs.threadFor(entity);
			if (threadId == null) {
				threadId = helpers().parentFor(entity); // a helper opens its parent's chat
			}
			if (threadId == null) {
				return InteractionResult.PASS;
			}
			NpcInfo info = npcs.info(threadId);
			if (info != null) {
				dev.t3craft.client.chat.ChatScreens.openFor(info);
			}
			return InteractionResult.SUCCESS;
		});
		// Left-click on an NPC toggles its detail panel (compact labels). Our entities are unknown
		// to the server, so the attack is consumed client-side.
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide()) {
				return InteractionResult.PASS;
			}
			String threadId = npcs.threadFor(entity);
			if (threadId == null) {
				return InteractionResult.PASS;
			}
			npcs.toggleDetail(threadId);
			return InteractionResult.FAIL;
		});
	}

	// ---- source lifecycle ---------------------------------------------------------------------

	private void startSource() {
		T3Craft.LOGGER.info("T3Craft mode: {}", config.mode());
		if (config.mode() == ModConfig.Mode.BRIDGE) {
			T3Craft.LOGGER.info("bridge URL {}", config.bridgeUrl());
			link = new BridgeLink(config.bridgeUrl(), config.bridgeKey(), modVersion, this, Minecraft.getInstance()::execute);
			link.start();
		} else {
			direct = new DirectController(new Environments(ModConfig.dataDir()), () -> directDirty.set(true));
			direct.setOptions(config.visibility());
			direct.start();
			if (direct.environments().list().isEmpty()) {
				T3Craft.LOGGER.info("no T3 environment paired yet: use /t3craft pair");
			}
		}
	}

	private void stopSource() {
		if (link != null) {
			link.stop();
			link = null;
		}
		if (direct != null) {
			direct.stop();
			direct = null;
		}
		lastStatus = LinkStatus.DISABLED;
		envStatus.clear();
		envDetail.clear();
		lastViews.clear();
		syncedEnvs.clear();
		PeekHud.setPeek(null, 0);
		PeekHud.setBanner(null);
	}

	/** {@code /t3craft reconnect}: same config, fresh sockets now (backoff reset). */
	public void reconnect() {
		if (link != null) {
			link.reconnect();
		}
		if (direct != null) {
			direct.reconnectAll();
		}
	}

	/**
	 * {@code /t3craft reload}: re-reads {@code config/t3craft.json}, the paired environments and
	 * their credentials, drops the current connections and starts again. No game restart needed.
	 */
	public ModConfig reload() {
		stopSource();
		config = ModConfig.load();
		helperConfig = loadHelperConfig();
		helpers().update(HelperRoster.Collected.EMPTY, List.of(), 0);
		npcs.replaceAll(List.of());
		if (started) {
			startSource();
		}
		return config;
	}

	// Lazy getters: fields added by hot-swap are null and onInitializeClient does not run again.
	private HelperDirector helpers() {
		HelperDirector h = helpers;
		if (h == null) {
			helpers = h = new HelperDirector();
			npcs.setPanelExtra(h::panelExtra);
		}
		return h;
	}

	private HelperConfig helperConfig() {
		return helperConfig = HotSwapFields.orInit(helperConfig, T3CraftClient::loadHelperConfig);
	}

	private static HelperConfig loadHelperConfig() {
		return HelperConfig.load(FabricLoader.getInstance().getConfigDir().resolve("t3craft.json"));
	}

	/** Opens a screen on the next tick (a command's chat screen closes right after it runs). */
	public void openNextTick(net.minecraft.client.gui.screens.Screen screen) {
		pendingScreen = screen;
	}

	public ModConfig config() {
		return config;
	}

	public @Nullable BridgeLink link() {
		return link;
	}

	public @Nullable DirectController direct() {
		return direct;
	}

	public NpcRegistry npcs() {
		return npcs;
	}

	/** Last known status per environment: id → {label, status, detail}. */
	public Map<String, String[]> environments() {
		if (direct != null) {
			Map<String, String[]> m = new LinkedHashMap<>();
			for (DirectController.EnvStatus s : direct.statuses()) {
				m.put(s.id(), new String[] {s.label(), s.status().wire, s.detail()});
			}
			return m;
		}
		return java.util.Collections.unmodifiableMap(envDetail);
	}

	// ---- direct mode --------------------------------------------------------------------------

	/** NPCs shown at the previous direct refresh, and environments that were in sync then. */
	private final Map<String, dev.t3craft.t3.NpcView> lastViews = new HashMap<>();
	private final java.util.Set<String> syncedEnvs = new java.util.HashSet<>();

	private void refreshDirect(Minecraft mc) {
		DirectController d = direct;
		if (d == null) {
			return;
		}
		List<dev.t3craft.t3.NpcView> views = d.npcs(System.currentTimeMillis());
		// feat/npc-helpers: with helpers on, subagents never get a desk/NPC of their own (even with
		// includeSubagents); they follow their parent instead. Filtered before the registry, so every
		// roster reader (behaviour, workstations, HQ preview, portable) sees parents only.
		HelperRoster.Collected sub = helperConfig().enabled() ? HelperRoster.collect(d.shellViews()) : HelperRoster.Collected.EMPTY;
		views = HelperRoster.deskRoster(views, dev.t3craft.t3.NpcView::id, sub.subagentNpcIds());
		helpers().update(sub, views.stream().map(dev.t3craft.t3.NpcView::id).toList(), helperConfig().maxPerParent());
		List<NpcInfo> list = new ArrayList<>();
		views.forEach(v -> list.add(NpcInfo.fromView(v)));
		npcs.replaceAll(list);

		for (dev.t3craft.t3.Transitions.Transition t : dev.t3craft.t3.Transitions.between(lastViews, views, syncedEnvs)) {
			Notifier.transition(mc, t.npcId(), t.title(), t.text(), t.color(), t.sound().name().toLowerCase(), true);
		}
		lastViews.clear();
		views.forEach(v -> lastViews.put(v.id(), v));
		dev.t3craft.t3.Transitions.Peek peek = dev.t3craft.t3.Transitions.peek(views);
		PeekHud.setPeek(peek.text(), peek.color());

		syncedEnvs.clear();
		List<String> problems = new ArrayList<>();
		for (DirectController.EnvStatus s : d.statuses()) {
			if (s.status() == T3Connection.Status.OK) {
				syncedEnvs.add(s.id());
			} else if (s.status() != T3Connection.Status.CONNECTING) {
				problems.add(s.label() + " " + s.status().wire);
			}
			String before = envStatus.put(s.id(), s.status().wire);
			if (before != null && !before.equals(s.status().wire) && s.status() != T3Connection.Status.CONNECTING) {
				Toasts.link(mc, s.label() + ": " + s.status().wire);
			}
		}
		PeekHud.setBanner(problems.isEmpty() ? null : "T3: " + String.join(", ", problems) + "  (/t3craft status)");
	}

	/** {@link #pair(String, String, String, boolean, Consumer)} with the default scopes (read + operate). */
	public void pair(String label, String linkOrCode, String hostUrl, Consumer<PairResult> done) {
		pair(label, linkOrCode, hostUrl, false, done);
	}

	/**
	 * Pairs this instance with a T3 environment: exchanges the one-time credential for a session
	 * with {@link Pairing#DEFAULT_SCOPES} (read + operate, one pairing) or, with {@code readOnly},
	 * {@code orchestration:read} only. {@link Environments#pair} stores it under
	 * {@code config/t3craft/credentials/} (and {@code credentials/operate/} when it carries
	 * operate), then reconnects. If the host issued a read-only credential the environment is
	 * paired read-only. {@code done} gets a player-facing message on the client thread (never a secret).
	 */
	public void pair(String label, String linkOrCode, String hostUrl, boolean readOnly, Consumer<PairResult> done) {
		Minecraft mc = Minecraft.getInstance();
		Pairing.Target target;
		try {
			target = Pairing.parse(linkOrCode, hostUrl);
		} catch (Pairing.PairingException e) {
			done.accept(new PairResult(false, e.getMessage()));
			return;
		}
		String shown = label == null || label.isBlank() ? target.baseUrl().getHost() : label.trim();
		String id = Environments.slug(shown);
		Environments envs = new Environments(ModConfig.dataDir());
		java.util.Set<String> scopes = Pairing.pairingScopes(readOnly);
		new PairingClient(java.net.http.HttpClient.newHttpClient())
				.exchange(target, "T3Craft (Minecraft " + modVersion + ")", System.getProperty("os.name", ""), scopes)
				.whenComplete((session, err) -> {
					PairResult result;
					if (err != null) {
						Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
						result = new PairResult(false, cause instanceof Pairing.PairingException
								? cause.getMessage()
								: "Could not reach " + target.baseUrl() + ": " + T3Connection.sanitize(cause.getClass().getSimpleName()));
					} else {
						try {
							boolean control = envs.pair(new Environments.Environment(id, shown, target.baseUrl()), session);
							String until = java.time.Instant.ofEpochMilli(session.expiresAtMs()).toString().substring(0, 10);
							String how = control
									? "with control (read + operate). Revoke control any time: /t3craft operate revoke " + id
									: readOnly ? "read-only"
									: "read-only: the credential had no orchestration:operate (use \"Enable control\" to add it)";
							result = new PairResult(true, "Paired with " + shown + " (" + target.baseUrl() + "), " + how
									+ ". Session valid until " + until + ".");
							dev.t3craft.t3.AuditLog.in(ModConfig.dataDir()).append(new dev.t3craft.t3.AuditLog.Entry(System.currentTimeMillis(), id, null,
									"pair", false, "ok", "ui", control ? "read+operate" : "read-only"));
						} catch (IOException | IllegalArgumentException e) {
							result = new PairResult(false, "Paired, but could not save the credential: " + e.getClass().getSimpleName());
						}
					}
					PairResult r = result;
					mc.execute(() -> {
						if (r.ok() && config.mode() == ModConfig.Mode.DIRECT) {
							reload();
						}
						done.accept(r);
					});
				});
	}

	public record PairResult(boolean ok, String message) {
	}

	/** {@code /t3craft unpair <env>}: deletes the local credential (revoke the session on the host too). */
	public boolean unpair(String envId) throws IOException {
		boolean removed = new Environments(ModConfig.dataDir()).remove(envId);
		if (removed) {
			reload();
		}
		return removed;
	}

	// ---- bridge mode (legacy) -----------------------------------------------------------------

	@Override
	public void onStatus(LinkStatus status, String detail) {
		if (status == lastStatus && status != LinkStatus.BACKOFF) {
			return;
		}
		T3Craft.LOGGER.info("bridge link {}: {}", status, detail);
		Minecraft mc = Minecraft.getInstance();
		PeekHud.setBanner(status == LinkStatus.CONNECTED ? null : "T3Craft bridge: " + status.name().toLowerCase() + "  (/t3craft status)");
		if (status == LinkStatus.CONNECTED) {
			Toasts.link(mc, "Connected");
		} else if (lastStatus == LinkStatus.CONNECTED) {
			Toasts.link(mc, "Disconnected, reconnecting…");
		}
		lastStatus = status;
	}

	@Override
	public void onMessage(String type, JsonObject msg) {
		Minecraft mc = Minecraft.getInstance();
		switch (type) {
			case "snapshot" -> {
				List<NpcInfo> list = new ArrayList<>();
				JsonElement arr = msg.get("npcs");
				if (arr != null && arr.isJsonArray()) {
					for (JsonElement e : arr.getAsJsonArray()) {
						if (e.isJsonObject()) {
							NpcInfo n = NpcInfo.fromJson(e.getAsJsonObject());
							if (n != null) {
								list.add(n);
							}
						}
					}
				}
				npcs.replaceAll(list);
				T3Craft.LOGGER.info("snapshot: {} threads", list.size());
			}
			case "npc.upsert" -> {
				JsonElement n = msg.get("npc");
				if (n != null && n.isJsonObject()) {
					NpcInfo info = NpcInfo.fromJson(n.getAsJsonObject());
					if (info != null) {
						npcs.upsert(info);
					}
				}
			}
			case "npc.remove" -> {
				JsonElement id = msg.get("npcId");
				if (id != null && id.isJsonPrimitive()) {
					npcs.remove(id.getAsString());
				}
			}
			case "notify" -> Toasts.notify(mc, optString(msg, "title", "T3"), optString(msg, "text", ""),
					NpcInfo.parseColor(optString(msg, "color", null)));
			case "bridge.status" -> onEnvironments(mc, msg);
			// Toast comes from the paired `notify`; transition adds chat line + sound.
			case "npc.transition" -> Notifier.transition(mc, optString(msg, "npcId", null), optString(msg, "title", "T3"), optString(msg, "text", ""),
					NpcInfo.parseColor(optString(msg, "color", null)), optString(msg, "sound", ""), false);
			case "hud.peek" -> PeekHud.setPeek(optString(msg, "text", null), NpcInfo.parseColor(optString(msg, "color", null)));
			case "ack", "hello" -> T3Craft.LOGGER.debug("{}: {}", type, msg);
			default -> T3Craft.LOGGER.debug("ignoring unknown message type {}", type);
		}
	}

	/** Toast when an environment goes offline / fails auth / comes back. Text from the bridge. */
	private void onEnvironments(Minecraft mc, JsonObject msg) {
		JsonElement arr = msg.get("environments");
		if (arr == null || !arr.isJsonArray()) {
			return;
		}
		for (JsonElement e : arr.getAsJsonArray()) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject o = e.getAsJsonObject();
			String id = optString(o, "id", "?");
			String status = optString(o, "status", "?");
			envDetail.put(id, new String[] {optString(o, "label", id), status, optString(o, "detail", "")});
			String before = envStatus.put(id, status);
			if (before != null && !before.equals(status) && !"connecting".equals(status)) {
				Toasts.link(mc, optString(o, "label", id) + ": " + status);
			}
		}
	}

	private static String optString(JsonObject o, String key, String def) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
	}
}
