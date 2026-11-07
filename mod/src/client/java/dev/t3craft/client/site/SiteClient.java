package dev.t3craft.client.site;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.t3craft.T3Craft;
import dev.t3craft.site.Restorer;
import dev.t3craft.site.SiteService;
import dev.t3craft.site.mc.SiteRuntime;
import java.util.function.Function;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionResult;
import org.jspecify.annotations.Nullable;

/**
 * Client side of the physical site ({@code /t3craft site …}). Separate entrypoint so it does not
 * touch the NPC/T3 code paths.
 *
 * <p>Works only with an <b>integrated server</b> (singleplayer / own world): the command is run on
 * that server's thread through {@link SiteRuntime}. On a multiplayer server it refuses and changes
 * nothing. Creative and survival behave the same (no material cost for now).
 *
 * <pre>
 * /t3craft site here                      preview the site right in front of you (entrance facing you)
 * /t3craft site pick                      search nearby for the flattest valid spot and preview it
 * /t3craft site wand                      the next block you right-click (any item) becomes the centre
 * /t3craft site confirm &lt;token&gt;          build the previewed site (snapshot first)
 * /t3craft site cancel                    drop the preview
 * /t3craft site undo                      report what undo would restore (and your changes there)
 * /t3craft site undo confirm &lt;token&gt; [keep|overwrite]
 * /t3craft site move                      move the base to the last preview (report)
 * /t3craft site move confirm &lt;token&gt; [keep|overwrite]
 * /t3craft site resume                    finish an interrupted build/undo/move
 * /t3craft site status
 * </pre>
 */
public final class SiteClient implements ClientModInitializer {
	private final SitePreview preview = new SitePreview();
	private boolean wandArmed;

	@Override
	public void onInitializeClient() {
		SiteRuntime.init();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> register(dispatcher));
		ClientTickEvents.END_CLIENT_TICK.register(preview::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			wandArmed = false;
			preview.clear();
			IntegratedServer server = mc.getSingleplayerServer();
			if (server != null) {
				server.execute(() -> {
					String msg = SiteRuntime.service(server).onLoad();
					if (msg != null) {
						mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(prefix().append(Component.literal(msg).withStyle(ChatFormatting.YELLOW))));
					}
				});
			}
		});
		// Wand: with the wand armed, the next right-clicked block becomes the site centre. The click
		// is consumed on the client (FAIL = no packet), so nothing is placed or used.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide() || !wandArmed) {
				return InteractionResult.PASS;
			}
			wandArmed = false;
			int x = hit.getBlockPos().getX();
			int z = hit.getBlockPos().getZ();
			int px = player.getBlockX();
			int pz = player.getBlockZ();
			String dim = SiteRuntime.dimensionId(level);
			run(null, s -> s.here(dim, x, z, px, pz));
			return InteractionResult.FAIL;
		});
	}

	private void register(CommandDispatcher<FabricClientCommandSource> d) {
		LiteralArgumentBuilder<FabricClientCommandSource> site = literal("site")
				.executes(c -> run(c, SiteService::status))
				.then(literal("status").executes(c -> run(c, SiteService::status)))
				.then(literal("here").executes(c -> {
					var p = c.getSource().getPlayer();
					String dim = SiteRuntime.dimensionId(c.getSource().getLevel());
					int x = p.getBlockX();
					int z = p.getBlockZ();
					// The site goes right in front of the player (outside its reserve), entrance facing them.
					var dir = p.getDirection();
					return run(c, s -> {
						int ahead = s.request().frontReach();
						return s.here(dim, x + dir.getStepX() * ahead, z + dir.getStepZ() * ahead, x, z);
					});
				}))
				.then(literal("pick").executes(c -> {
					var p = c.getSource().getPlayer();
					String dim = SiteRuntime.dimensionId(c.getSource().getLevel());
					int x = p.getBlockX();
					int z = p.getBlockZ();
					return run(c, s -> s.pick(dim, x, z));
				}))
				.then(literal("wand").executes(c -> {
					if (!singleplayer(c)) {
						return 0;
					}
					wandArmed = true;
					c.getSource().sendFeedback(prefix().append(Component.literal("Right-click the block that should be the centre of the base (any item; nothing is placed).")));
					return 1;
				}))
				.then(literal("confirm").then(argument("token", StringArgumentType.word())
						.executes(c -> run(c, s -> s.confirm(StringArgumentType.getString(c, "token"))))))
				.then(literal("cancel").executes(c -> {
					preview.clear();
					wandArmed = false;
					return run(c, SiteService::cancel);
				}))
				.then(literal("undo").executes(c -> run(c, SiteService::undo))
						.then(literal("confirm").then(withMode("token", (c, mode) -> run(c, s -> s.undoConfirm(StringArgumentType.getString(c, "token"), mode))))))
				.then(literal("move").executes(c -> run(c, SiteService::move))
						.then(literal("confirm").then(withMode("token", (c, mode) -> run(c, s -> s.moveConfirm(StringArgumentType.getString(c, "token"), mode))))))
				.then(literal("resume").executes(c -> run(c, SiteService::resume)));
		d.register(literal("t3craft").then(site));
	}

	private interface ModeCommand {
		int run(CommandContext<FabricClientCommandSource> c, Restorer.@Nullable Mode mode);
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<FabricClientCommandSource, String> withMode(String name, ModeCommand cmd) {
		return argument(name, StringArgumentType.word())
				.executes(c -> cmd.run(c, null))
				.then(literal("keep").executes(c -> cmd.run(c, Restorer.Mode.KEEP_PLAYER_CHANGES)))
				.then(literal("overwrite").executes(c -> cmd.run(c, Restorer.Mode.OVERWRITE)));
	}

	private static boolean singleplayer(@Nullable CommandContext<FabricClientCommandSource> c) {
		if (Minecraft.getInstance().getSingleplayerServer() != null) {
			return true;
		}
		Component msg = prefix().append(Component.literal("Site commands only work in your own singleplayer world; multiplayer servers are never modified."));
		if (c != null) {
			c.getSource().sendError(msg);
		} else {
			Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(msg);
		}
		return false;
	}

	/** Runs {@code action} on the integrated server thread and shows the reply on the client thread. */
	private int run(@Nullable CommandContext<FabricClientCommandSource> c, Function<SiteService, SiteService.Reply> action) {
		if (!singleplayer(c)) {
			return 0;
		}
		Minecraft mc = Minecraft.getInstance();
		IntegratedServer server = mc.getSingleplayerServer();
		server.execute(() -> {
			SiteService.Reply r;
			try {
				r = action.apply(SiteRuntime.service(server));
			} catch (RuntimeException e) {
				T3Craft.LOGGER.error("site command failed", e);
				r = new SiteService.Reply(false, java.util.List.of("Internal error: " + e.getClass().getSimpleName() + " (see log). Nothing further was changed."),
						null, null, java.util.List.of(), java.util.List.of());
			}
			SiteService.Reply reply = r;
			mc.execute(() -> show(mc, reply));
		});
		return 1;
	}

	private void show(Minecraft mc, SiteService.Reply r) {
		boolean first = true;
		for (String line : r.lines()) {
			MutableComponent m = first ? prefix() : Component.empty();
			m.append(Component.literal(line).withStyle(r.ok() ? ChatFormatting.WHITE : ChatFormatting.RED));
			mc.gui.hud.getChat().addClientSystemMessage(m);
			first = false;
		}
		if (!r.actions().isEmpty()) {
			MutableComponent row = Component.literal("  ");
			for (String a : r.actions()) {
				String label = a.replace(SiteService.CMD + " ", "").replaceAll(" [0-9a-f]{6}", "");
				ChatFormatting color = a.endsWith("overwrite") ? ChatFormatting.RED : a.endsWith("cancel") ? ChatFormatting.GRAY : ChatFormatting.GREEN;
				row.append(Component.literal("[" + label + "]").withStyle(s -> s.withColor(color)
						.withClickEvent(new ClickEvent.RunCommand(a.substring(1)))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal(a)))));
				row.append(Component.literal(" "));
			}
			mc.gui.hud.getChat().addClientSystemMessage(row);
		}
		if (r.outline() != null || !r.marks().isEmpty()) {
			SitePreview.Style style = !r.ok() ? SitePreview.Style.BAD : r.actions().isEmpty() ? SitePreview.Style.SITE : SitePreview.Style.OK;
			preview.show(r.outline(), r.footprint(), r.marks(), style);
		} else if (r.ok()) {
			preview.clear();
		}
	}

	static MutableComponent prefix() {
		return Component.literal("[T3Craft] ").withStyle(ChatFormatting.AQUA);
	}
}
