package dev.t3craft.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.t3craft.T3Craft;
import dev.t3craft.client.hud.Toasts;
import dev.t3craft.client.link.BridgeLink;
import dev.t3craft.client.link.BridgeListener;
import dev.t3craft.client.link.LinkStatus;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.client.npc.NpcRegistry;
import dev.t3craft.client.screen.ThreadChatScreen;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;

public final class T3CraftClient implements ClientModInitializer, BridgeListener {
	private static T3CraftClient instance;

	private final NpcRegistry npcs = new NpcRegistry();
	private String modVersion = "dev";
	private ModConfig config;
	private BridgeLink link;
	private LinkStatus lastStatus = LinkStatus.DISABLED;
	private boolean started;

	public static T3CraftClient get() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		modVersion = FabricLoader.getInstance().getModContainer(T3Craft.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		config = ModConfig.load();
		link = newLink(config);

		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
			started = true;
			link.start();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> link.stop());
		ClientTickEvents.END_CLIENT_TICK.register(npcs::tick);
		T3CraftCommands.register(this);

		// Right-click on one of our client-only NPCs opens its (placeholder) chat screen. The entity
		// is unknown to the server, so the interaction is consumed client-side.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide()) {
				return InteractionResult.PASS;
			}
			String threadId = npcs.threadFor(entity);
			if (threadId == null) {
				return InteractionResult.PASS;
			}
			NpcInfo info = npcs.info(threadId);
			if (info != null) {
				Minecraft.getInstance().gui.setScreen(new ThreadChatScreen(info));
			}
			return InteractionResult.SUCCESS;
		});
	}

	private BridgeLink newLink(ModConfig cfg) {
		T3Craft.LOGGER.info("bridge URL {}", cfg.bridgeUrl());
		return new BridgeLink(cfg.bridgeUrl(), cfg.bridgeKey(), modVersion, this, Minecraft.getInstance()::execute);
	}

	/** {@code /t3craft reconnect}: same config, fresh socket now (backoff reset). */
	public void reconnect() {
		link.reconnect();
	}

	/**
	 * {@code /t3craft reload}: re-reads {@code config/t3craft.json} (and the URL overrides), drops
	 * the old link and connects with the new settings. No game restart needed. Returns the config
	 * now in use.
	 */
	public ModConfig reload() {
		ModConfig next = ModConfig.load();
		BridgeLink old = link;
		link = newLink(next);
		config = next;
		old.stop();
		lastStatus = LinkStatus.DISABLED;
		envStatus.clear();
		envDetail.clear();
		if (started) {
			link.start();
		}
		return next;
	}

	public ModConfig config() {
		return config;
	}

	public BridgeLink link() {
		return link;
	}

	public NpcRegistry npcs() {
		return npcs;
	}

	/** Last known status per environment, as sent by the bridge: id → {label, status, detail}. */
	public java.util.Map<String, String[]> environments() {
		return java.util.Collections.unmodifiableMap(envDetail);
	}

	@Override
	public void onStatus(LinkStatus status, String detail) {
		if (status == lastStatus && status != LinkStatus.BACKOFF) {
			return;
		}
		T3Craft.LOGGER.info("bridge link {}: {}", status, detail);
		Minecraft mc = Minecraft.getInstance();
		if (status == LinkStatus.CONNECTED) {
			Toasts.link(mc, "Connected");
		} else if (lastStatus == LinkStatus.CONNECTED) {
			Toasts.link(mc, "Disconnected, reconnecting…");
		}
		// TODO(phase 1): persistent HUD connection banner instead of only toasts.
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
			case "notify" -> {
				Toasts.notify(mc, optString(msg, "title", "T3"), optString(msg, "text", ""),
						NpcInfo.parseColor(optString(msg, "color", null)));
			}
			case "bridge.status" -> onEnvironments(mc, msg);
			case "ack", "hello" -> T3Craft.LOGGER.debug("{}: {}", type, msg);
			// TODO(phase 2/3): chat.history, chat.append, permission.open, permission.closed
			default -> T3Craft.LOGGER.debug("ignoring unknown message type {}", type);
		}
	}

	private final java.util.Map<String, String> envStatus = new java.util.HashMap<>();
	private final java.util.Map<String, String[]> envDetail = new java.util.LinkedHashMap<>();

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
