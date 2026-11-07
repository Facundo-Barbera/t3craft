package dev.t3craft.client;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.t3craft.client.link.BridgeLink;
import java.util.Map;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Client-side {@code /t3craft} command (never reaches the server).
 * <ul>
 *   <li>{@code status}: bridge URL, link state, per-environment state.</li>
 *   <li>{@code reconnect}: fresh socket to the same bridge, now.</li>
 *   <li>{@code reload}: re-read {@code config/t3craft.json} and reconnect with it.</li>
 * </ul>
 */
public final class T3CraftCommands {
	private T3CraftCommands() {
	}

	public static void register(T3CraftClient client) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> build(dispatcher, client));
	}

	private static void build(CommandDispatcher<FabricClientCommandSource> d, T3CraftClient client) {
		d.register(literal("t3craft")
				.executes(c -> status(c, client))
				.then(literal("status").executes(c -> status(c, client)))
				.then(literal("reconnect").executes(c -> {
					client.reconnect();
					c.getSource().sendFeedback(prefix().append(Component.literal("reconnecting to " + client.config().bridgeUrl())));
					return 1;
				}))
				.then(literal("reload").executes(c -> {
					ModConfig cfg = client.reload();
					c.getSource().sendFeedback(prefix().append(Component.literal("reloaded " + ModConfig.file().getFileName()
							+ ", connecting to " + cfg.bridgeUrl() + (cfg.bridgeKey() == null ? "" : " (with bridge key)"))));
					return 1;
				})));
	}

	static MutableComponent prefix() {
		return Component.literal("[T3Craft] ").withStyle(ChatFormatting.AQUA);
	}

	private static int status(CommandContext<FabricClientCommandSource> c, T3CraftClient client) {
		BridgeLink link = client.link();
		FabricClientCommandSource src = c.getSource();
		ChatFormatting color = switch (link.status()) {
			case CONNECTED -> ChatFormatting.GREEN;
			case CONNECTING -> ChatFormatting.YELLOW;
			default -> ChatFormatting.RED;
		};
		src.sendFeedback(prefix().append(Component.literal("bridge " + link.uri() + ": ")).append(
				Component.literal(link.status().name().toLowerCase()).withStyle(color)));
		if (!link.statusDetail().isEmpty() && link.status() != dev.t3craft.client.link.LinkStatus.CONNECTED) {
			src.sendFeedback(Component.literal("  " + link.statusDetail()).withStyle(ChatFormatting.GRAY));
		}
		for (Map.Entry<String, String[]> e : client.environments().entrySet()) {
			String[] v = e.getValue();
			ChatFormatting ec = "ok".equals(v[1]) ? ChatFormatting.GREEN : "connecting".equals(v[1]) ? ChatFormatting.YELLOW : ChatFormatting.RED;
			MutableComponent line = Component.literal("  " + v[0] + ": ").append(Component.literal(v[1]).withStyle(ec));
			if (!v[2].isEmpty()) {
				line.append(Component.literal(" — " + v[2]).withStyle(ChatFormatting.GRAY));
			}
			src.sendFeedback(line);
		}
		src.sendFeedback(Component.literal("  " + client.npcs().size() + " threads shown").withStyle(ChatFormatting.GRAY));
		return 1;
	}
}
