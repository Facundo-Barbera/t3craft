package dev.t3craft.client.operate;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.t3craft.client.ModConfig;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.client.screen.OperatePairScreen;
import dev.t3craft.t3.Environments;
import dev.t3craft.t3.OperateGate;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * {@code /t3craft operate}: status per environment; {@code enable <env>} opens the optional operate
 * pairing; {@code revoke <env>} deletes the local operate token (the host-side revocation is manual).
 * Action threads add their own subcommands under {@code operate} via {@link OperateUi} and
 * {@link OperateGate.Origin#COMMAND}.
 */
public final class OperateCommands {
	private OperateCommands() {
	}

	private static final SuggestionProvider<FabricClientCommandSource> ENVS = (c, b) -> {
		new Environments(ModConfig.dataDir()).list().forEach(e -> b.suggest(e.id()));
		return b.buildFuture();
	};

	public static LiteralArgumentBuilder<FabricClientCommandSource> build(T3CraftClient client) {
		return literal("operate")
				.executes(OperateCommands::status)
				.then(literal("status").executes(OperateCommands::status))
				.then(literal("enable").then(argument("env", StringArgumentType.word()).suggests(ENVS).executes(c -> {
					String env = StringArgumentType.getString(c, "env");
					var e = OperateClient.environment(env);
					if (e.isEmpty()) {
						c.getSource().sendError(T3CraftCommands.prefix().append(Component.literal("no paired environment " + env)));
						return 0;
					}
					client.openNextTick(new OperatePairScreen(null, e.get()));
					return 1;
				})))
				.then(literal("revoke").then(argument("env", StringArgumentType.word()).suggests(ENVS).executes(c -> {
					OperateClient.Result r = OperateClient.get().revoke(StringArgumentType.getString(c, "env"), OperateGate.Origin.COMMAND);
					c.getSource().sendFeedback(T3CraftCommands.prefix().append(Component.literal(r.message())
							.withStyle(r.ok() ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
					return r.ok() ? 1 : 0;
				})))
				.then(LaunchCommand.build()) // feat/op-launch: /t3craft operate new <env> <project> <prompt…>
				;
	}

	private static int status(CommandContext<FabricClientCommandSource> c) {
		var list = new Environments(ModConfig.dataDir()).list();
		if (list.isEmpty()) {
			c.getSource().sendFeedback(T3CraftCommands.prefix().append(Component.literal("no T3 environment paired: /t3craft pair")));
			return 1;
		}
		OperateGate gate = OperateUi.gate();
		c.getSource().sendFeedback(T3CraftCommands.prefix().append(Component.literal("control (operate) per environment:")));
		for (Environments.Environment e : list) {
			OperateGate.Availability av = gate.availability(e.id());
			ChatFormatting color = av == OperateGate.Availability.ENABLED ? ChatFormatting.RED : ChatFormatting.GRAY;
			String text = switch (av) {
				case ENABLED -> "control enabled (revoke: /t3craft operate revoke " + e.id() + ")";
				case EXPIRED -> "read-only (operate session expired)";
				case READ_ONLY -> "read-only";
			};
			c.getSource().sendFeedback(Component.literal("  " + e.id() + ": ").append(Component.literal(text).withStyle(color)));
		}
		return 1;
	}
}
