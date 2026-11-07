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
import java.net.URI;
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
	private final NpcRegistry npcs = new NpcRegistry();
	private BridgeLink link;
	private LinkStatus lastStatus = LinkStatus.DISABLED;

	@Override
	public void onInitializeClient() {
		String version = FabricLoader.getInstance().getModContainer(T3Craft.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		link = new BridgeLink(bridgeUri(), version, this, Minecraft.getInstance()::execute);

		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> link.start());
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> link.stop());
		ClientTickEvents.END_CLIENT_TICK.register(npcs::tick);

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

	/** {@code T3CRAFT_PORT} (env) or {@code -Dt3craft.port}; host is always loopback. */
	private static URI bridgeUri() {
		String port = System.getProperty("t3craft.port", System.getenv().getOrDefault("T3CRAFT_PORT", "7878"));
		int p;
		try {
			p = Integer.parseInt(port.trim());
		} catch (NumberFormatException e) {
			p = 7878;
		}
		return URI.create("ws://127.0.0.1:" + p + "/");
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
				JsonElement id = msg.get("id");
				if (id != null && id.isJsonPrimitive()) {
					npcs.remove(id.getAsString());
				}
			}
			case "notify" -> {
				String level = optString(msg, "level", "info");
				String text = optString(msg, "text", "");
				String threadId = optString(msg, "threadId", null);
				NpcInfo info = threadId == null ? null : npcs.info(threadId);
				Toasts.notify(mc, level, text, info == null ? null : info.title());
			}
			case "ack", "bridge.status" -> T3Craft.LOGGER.debug("{}: {}", type, msg);
			// TODO(phase 2/3): chat.history, chat.append, permission.open, permission.closed
			default -> T3Craft.LOGGER.debug("ignoring unknown message type {}", type);
		}
	}

	private static String optString(JsonObject o, String key, String def) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
	}
}
