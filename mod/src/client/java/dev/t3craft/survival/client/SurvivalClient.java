package dev.t3craft.survival.client;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.context.CommandContext;
import dev.t3craft.survival.PlayContext;
import dev.t3craft.survival.PlayContexts;
import dev.t3craft.survival.Survival;
import dev.t3craft.survival.SurvivalRules;
import dev.t3craft.survival.Verdict;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Survival client entrypoint (own line in {@code fabric.mod.json}, no edits to other features):
 * {@code /t3craft survival} shows what the policy says for the player right now,
 * {@code /t3craft survival reload} re-reads the {@code survival} section of {@code config/t3craft.json}.
 * Merged under the existing {@code /t3craft} literal by Brigadier.
 */
public final class SurvivalClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(literal("t3craft")
				.then(literal("survival")
						.executes(SurvivalClient::status)
						.then(literal("reload").executes(c -> {
							List<String> warnings = Survival.reload();
							c.getSource().sendFeedback(prefix().append(Component.literal("reloaded survival rules"
									+ (warnings.isEmpty() ? "" : ", defaults used for " + String.join(", ", warnings)))));
							return status(c);
						})))));
	}

	/** The context of the local player, for client-side callers (e.g. the terminal key). Null outside a world. */
	public static @Nullable PlayContext current(Minecraft mc) {
		return mc.player == null ? null : PlayContexts.of(mc.player, mc.hasSingleplayerServer());
	}

	private static int status(CommandContext<FabricClientCommandSource> c) {
		FabricClientCommandSource src = c.getSource();
		PlayContext ctx = current(Minecraft.getInstance());
		if (ctx == null) {
			return 0;
		}
		SurvivalRules r = Survival.rules();
		src.sendFeedback(prefix().append(Component.literal(String.format("%s, cheats %s, %s, terminal %s",
				ctx.mode().name().toLowerCase(), ctx.cheats() ? "on" : "off",
				ctx.integratedServer() ? "singleplayer" : "remote server",
				!ctx.terminalItem() ? "n/a" : ctx.hasTerminal() ? "carried" : "not carried"))));
		src.sendFeedback(line("terminal key", r.openTerminal(ctx)));
		src.sendFeedback(line("builders", r.buildInWorld(ctx)).append(Component.literal(
				"  materials: " + r.materials(ctx).name().toLowerCase()).withStyle(ChatFormatting.GRAY)));
		src.sendFeedback(line("free debug builds", r.debugWorldEdit(ctx)));
		return 1;
	}

	private static MutableComponent line(String what, Verdict v) {
		return Component.literal("  " + what + ": ").withStyle(ChatFormatting.GRAY)
				.append(Component.translatable(v.langKey()).withStyle(v.allowed() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
	}

	private static MutableComponent prefix() {
		return Component.literal("[T3Craft] ").withStyle(ChatFormatting.AQUA);
	}
}
