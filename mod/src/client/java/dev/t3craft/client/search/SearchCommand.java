package dev.t3craft.client.search;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.t3.RpcFrames;
import dev.t3craft.t3.ThreadSearch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * {@code /t3craft search <text>}: read-only search over every connected environment, listed in the
 * chat (environment, title, state, age). Each line is clickable and opens the thread
 * ({@code /t3craft search-open <env> <thread>}, the hidden helper the click runs).
 *
 * <p>Registered as its own {@code t3craft} literal: Brigadier merges it with the existing
 * {@code /t3craft} node (children are added, the root's own command is kept). A new search cancels
 * the previous one still in flight, so an old answer never prints after a new one.
 */
public final class SearchCommand {
	private static final AtomicReference<CompletableFuture<ThreadSearch.Outcome>> LAST = new AtomicReference<>();

	private SearchCommand() {
	}

	static void build(CommandDispatcher<FabricClientCommandSource> d, T3CraftClient client) {
		d.register(literal("t3craft")
				.then(literal("search")
						.executes(c -> {
							c.getSource().sendFeedback(T3CraftCommands.prefix().append(Component.literal(
									"usage: /t3craft search <text> (at least " + RpcFrames.SEARCH_MIN_LENGTH + " characters)")));
							return 1;
						})
						.then(argument("text", StringArgumentType.greedyString()).executes(c -> {
							run(c.getSource(), client, StringArgumentType.getString(c, "text"));
							return 1;
						})))
				.then(literal(SearchLines.OPEN_LITERAL)
						.then(argument("env", StringArgumentType.string())
								.then(argument("thread", StringArgumentType.string()).executes(c -> {
									SearchClient.open(client, StringArgumentType.getString(c, "env"), StringArgumentType.getString(c, "thread"));
									return 1;
								})))));
	}

	private static void run(FabricClientCommandSource src, T3CraftClient client, String text) {
		String q = RpcFrames.searchQuery(text);
		if (q.length() < RpcFrames.SEARCH_MIN_LENGTH) {
			src.sendError(T3CraftCommands.prefix().append(Component.literal("type at least " + RpcFrames.SEARCH_MIN_LENGTH + " characters to search")));
			return;
		}
		CompletableFuture<ThreadSearch.Outcome> f = SearchClient.search(client, q, ThreadSearch.DEFAULT_LIMIT);
		CompletableFuture<ThreadSearch.Outcome> previous = LAST.getAndSet(f);
		if (previous != null && previous != f) {
			previous.cancel(true);
		}
		if (!f.isDone()) {
			src.sendFeedback(T3CraftCommands.prefix().append(Component.literal("searching \"" + q + "\"…").withStyle(ChatFormatting.GRAY)));
		}
		boolean local = SearchClient.localOnly(client);
		f.whenComplete((outcome, err) -> Minecraft.getInstance().execute(() -> {
			if (f.isCancelled() || LAST.get() != f) {
				return; // superseded by a newer search
			}
			LAST.compareAndSet(f, null);
			if (err != null || outcome == null) {
				src.sendError(T3CraftCommands.prefix().append(Component.literal("search failed: " + SearchModel.describe(err))));
				return;
			}
			print(src, outcome, local);
		}));
	}

	private static void print(FabricClientCommandSource src, ThreadSearch.Outcome o, boolean local) {
		ChatFormatting headColor = o.noEnvironmentConnected() ? ChatFormatting.YELLOW : ChatFormatting.WHITE;
		src.sendFeedback(T3CraftCommands.prefix().append(Component.literal(SearchLines.summary(o)).withStyle(headColor)));
		if (local) {
			src.sendFeedback(Component.literal("  bridge mode: titles of the listed threads only").withStyle(ChatFormatting.DARK_GRAY));
		}
		long now = System.currentTimeMillis();
		for (SearchLines.Line l : SearchLines.lines(o.hits(), now)) {
			src.sendFeedback(component(l));
		}
		for (String e : SearchLines.errors(o)) {
			src.sendFeedback(Component.literal("  ✖ " + e).withStyle(ChatFormatting.RED));
		}
	}

	static MutableComponent component(SearchLines.Line l) {
		MutableComponent hover = Component.literal(l.title()).withColor(l.color())
				.append(Component.literal("\n" + l.envLabel() + " · " + l.state() + (l.age().isEmpty() ? "" : " · " + l.age())).withStyle(ChatFormatting.GRAY));
		if (l.snippet() != null) {
			hover.append(Component.literal("\n" + l.snippet()).withStyle(ChatFormatting.WHITE));
		}
		hover.append(Component.literal("\nClick to open").withStyle(ChatFormatting.DARK_AQUA));
		MutableComponent line = Component.literal("  " + l.glyph() + " ").withColor(l.color())
				.append(Component.literal("[" + l.envLabel() + "] ").withStyle(ChatFormatting.AQUA))
				.append(Component.literal(l.title()).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(l.state()).withColor(l.color()))
				.append(Component.literal(l.age().isEmpty() ? "" : " · " + l.age()).withStyle(ChatFormatting.GRAY));
		return line.withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(l.command())).withHoverEvent(new HoverEvent.ShowText(hover)));
	}
}
