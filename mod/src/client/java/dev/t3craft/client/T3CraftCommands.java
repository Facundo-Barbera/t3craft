package dev.t3craft.client;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.t3craft.client.link.BridgeLink;
import dev.t3craft.client.link.LinkStatus;
import dev.t3craft.client.screen.PairScreen;
import dev.t3craft.t3.Environments;
import java.io.IOException;
import java.util.Map;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Client-side {@code /t3craft} command (never reaches the server).
 * <ul>
 *   <li>{@code status}: mode, connections and per-environment state.</li>
 *   <li>{@code reconnect}: fresh sockets with the same config, now.</li>
 *   <li>{@code reload}: re-read {@code config/t3craft.json}, paired environments and credentials, reconnect.</li>
 *   <li>{@code pair}: open the pairing screen (read + operate by default); {@code pair --read-only}: only {@code orchestration:read}. {@code unpair <env>}: forget an environment.</li>
 *   <li>{@code envs}: list paired environments (no secrets).</li>
 *   <li>{@code answer <env:threadId>}: the T3 screen on that thread, where its pending question or
 *   approval card is (feat/t3-user-input; the needs-input notice links to it).</li>
 *   <li>{@code operate}: control (operate) status per environment; {@code operate enable <env>} opens
 *   "Enable control" for an environment paired read-only; {@code operate revoke <env>} deletes the local operate token.</li>
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
					c.getSource().sendFeedback(prefix().append(Component.literal(client.config().mode() == ModConfig.Mode.BRIDGE
							? "reconnecting to " + client.config().bridgeUrl()
							: "reconnecting to " + client.environments().size() + " T3 environment(s)")));
					return 1;
				}))
				.then(literal("reload").executes(c -> {
					ModConfig cfg = client.reload();
					String what = cfg.mode() == ModConfig.Mode.BRIDGE
							? "bridge " + cfg.bridgeUrl() + (cfg.bridgeKey() == null ? "" : " (with bridge key)")
							: new Environments(ModConfig.dataDir()).list().size() + " paired T3 environment(s)";
					c.getSource().sendFeedback(prefix().append(Component.literal("reloaded " + ModConfig.file().getFileName()
							+ " (mode " + cfg.mode().name().toLowerCase() + "), connecting to " + what)));
					return 1;
				}))
				// Default: read + operate in one pairing. "--read-only": orchestration:read only.
				.then(literal("pair").executes(c -> {
					client.openNextTick(new PairScreen(null));
					return 1;
				}).then(literal(PairScreen.READ_ONLY_FLAG).executes(c -> {
					client.openNextTick(new PairScreen(null, true));
					return 1;
				})))
				.then(literal("unpair").then(argument("env", StringArgumentType.word())
						.suggests((c, b) -> {
							new Environments(ModConfig.dataDir()).list().forEach(e -> b.suggest(e.id()));
							return b.buildFuture();
						})
						.executes(c -> {
							String env = StringArgumentType.getString(c, "env");
							try {
								boolean ok = client.unpair(env);
								c.getSource().sendFeedback(prefix().append(Component.literal(ok
										? "forgot " + env + " (also revoke its session on the host: t3 auth session list / revoke)"
										: "no paired environment " + env)));
							} catch (IOException e) {
								c.getSource().sendError(prefix().append(Component.literal("could not remove " + env + ": " + e.getMessage())));
							}
							return 1;
						})))
				.then(dev.t3craft.client.operate.OperateCommands.build(client))
				.then(dev.t3craft.client.operate.ThreadControlUi.commands()) // feat/op-stop: operate interrupt|stop
				// feat/t3-user-input: open the T3 screen on a thread (its pending question / approval card);
				// the "[Answer]" link of a needs-input notice runs it
				.then(literal("answer").then(argument("thread", StringArgumentType.greedyString()).executes(c -> {
					String key = StringArgumentType.getString(c, "thread").strip();
					client.openNextTick(dev.t3craft.client.chat.ChatScreens.create(key.isEmpty() ? null : key));
					return 1;
				})))
				.then(literal("envs").executes(c -> {
					var list = new Environments(ModConfig.dataDir()).list();
					if (list.isEmpty()) {
						c.getSource().sendFeedback(prefix().append(Component.literal("no T3 environment paired: /t3craft pair")));
					}
					for (Environments.Environment e : list) {
						c.getSource().sendFeedback(Component.literal("  " + e.id() + " ").withStyle(ChatFormatting.AQUA)
								.append(Component.literal(e.label() + "  " + e.url()).withStyle(ChatFormatting.GRAY)));
					}
					return 1;
				})));
	}

	public static MutableComponent prefix() {
		return Component.literal("[T3Craft] ").withStyle(ChatFormatting.AQUA);
	}

	private static int status(CommandContext<FabricClientCommandSource> c, T3CraftClient client) {
		FabricClientCommandSource src = c.getSource();
		BridgeLink link = client.link();
		if (link != null) {
			ChatFormatting color = switch (link.status()) {
				case CONNECTED -> ChatFormatting.GREEN;
				case CONNECTING -> ChatFormatting.YELLOW;
				default -> ChatFormatting.RED;
			};
			src.sendFeedback(prefix().append(Component.literal("bridge mode, " + link.uri() + ": ")).append(
					Component.literal(link.status().name().toLowerCase()).withStyle(color)));
			if (!link.statusDetail().isEmpty() && link.status() != LinkStatus.CONNECTED) {
				src.sendFeedback(Component.literal("  " + link.statusDetail()).withStyle(ChatFormatting.GRAY));
			}
		} else {
			src.sendFeedback(prefix().append(Component.literal("direct mode, read-only")));
			if (client.environments().isEmpty()) {
				src.sendFeedback(Component.literal("  no T3 environment paired: /t3craft pair").withStyle(ChatFormatting.YELLOW));
			}
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
