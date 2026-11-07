package dev.t3craft.client.operate;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.ThreadLaunch;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * {@code /t3craft operate new <env> <project> <prompt…>} (item 23): launches a thread with the
 * project's default model ({@link OperateGate.Origin#COMMAND}). {@code <project>} is the project id
 * or its exact name (quote it if it has spaces). Without control (operate) the gate answers
 * read-only and nothing is sent. The new thread's NPC appears through the normal shell flow.
 */
public final class LaunchCommand {
	private LaunchCommand() {
	}

	private static final SuggestionProvider<FabricClientCommandSource> OPERABLE_ENVS = (c, b) -> {
		LaunchClient.operableEnvs().forEach(b::suggest);
		return b.buildFuture();
	};

	private static final SuggestionProvider<FabricClientCommandSource> PROJECTS = (c, b) -> {
		String env = StringArgumentType.getString(c, "env");
		LaunchClient.projects(env).forEach(p -> b.suggest(quote(p.label())));
		return b.buildFuture();
	};

	public static LiteralArgumentBuilder<FabricClientCommandSource> build() {
		return literal("new")
				.executes(c -> {
					c.getSource().sendFeedback(T3CraftCommands.prefix().append(Component.literal(
							"usage: /t3craft operate new <env> <project> <prompt…> (project: id or \"name\")")));
					return 1;
				})
				.then(argument("env", StringArgumentType.word()).suggests(OPERABLE_ENVS)
						.then(argument("project", StringArgumentType.string()).suggests(PROJECTS)
								.then(argument("prompt", StringArgumentType.greedyString()).executes(c -> run(c.getSource(),
										StringArgumentType.getString(c, "env"), StringArgumentType.getString(c, "project"),
										StringArgumentType.getString(c, "prompt"))))));
	}

	/** Brigadier {@code string()} argument: bare when it can be, else quoted. */
	static String quote(String s) {
		if (!s.isEmpty() && s.chars().allMatch(ch -> com.mojang.brigadier.StringReader.isAllowedInUnquotedString((char) ch))) {
			return s;
		}
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static int run(FabricClientCommandSource src, String env, String projectArg, String prompt) {
		if (OperateClient.environment(env).isEmpty()) {
			src.sendError(T3CraftCommands.prefix().append(Component.literal("no paired environment " + env)));
			return 0;
		}
		var projects = LaunchClient.projects(env);
		ThreadLaunch.ProjectChoice project = ThreadLaunch.findProject(projects, projectArg);
		if (project == null) {
			src.sendError(T3CraftCommands.prefix().append(Component.literal(projects.isEmpty()
					? "no projects known for " + env + " (is it connected? /t3craft status)"
					: "no single project \"" + projectArg + "\" in " + env + "; use its id or exact name")));
			return 0;
		}
		src.sendFeedback(T3CraftCommands.prefix().append(Component.literal("launching in " + env + " / " + project.label()
				+ " (" + ThreadLaunch.promptSummary(prompt) + ")…").withStyle(ChatFormatting.GRAY)));
		LaunchClient.launch(new ThreadLaunch.Request(env, project.id(), prompt, null), OperateGate.Origin.COMMAND, null,
				r -> src.sendFeedback(LaunchClient.resultLine(env, r)));
		return 1;
	}
}
