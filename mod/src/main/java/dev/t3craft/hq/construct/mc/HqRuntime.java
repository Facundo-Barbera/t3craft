package dev.t3craft.hq.construct.mc;

import dev.t3craft.T3Craft;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqSite;
import dev.t3craft.hq.construct.HqConstruction;
import dev.t3craft.hq.construct.HqCost;
import dev.t3craft.hq.construct.HqSiteFrame;
import dev.t3craft.hq.construct.HqStore;
import dev.t3craft.site.Expansion;
import dev.t3craft.site.Restorer;
import dev.t3craft.site.SiteBuilder;
import dev.t3craft.site.SiteHandle;
import dev.t3craft.site.SiteListener;
import dev.t3craft.site.SiteModule;
import dev.t3craft.site.SiteRequest;
import dev.t3craft.site.SiteService;
import dev.t3craft.site.mc.BuildPlanBridge;
import dev.t3craft.site.mc.LevelBlockWorld;
import dev.t3craft.site.mc.SiteRuntime;
import dev.t3craft.survival.PlayContext;
import dev.t3craft.survival.PlayContexts;
import dev.t3craft.survival.Survival;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * The HQ construction of the running <b>integrated</b> server: wires {@link HqConstruction} to the
 * site ({@link SiteRuntime}), the survival policy and a {@link HqWriter}, and ticks it on the server
 * thread. Installs the HQ's {@link SiteRequest} and module designer into the site, so a confirmed
 * site gets the hub, portico and archive, and each wing is one site expansion.
 *
 * <p>Client side: push the roster with {@link #roster} (any thread), receive the built HQ with
 * {@link #setAnchorSink}, and optionally replace the writer ({@link #setWriter}, animated crew).
 * Everything else runs on the server thread ({@code server.execute}).
 */
public final class HqRuntime {
	/** What the client needs to place NPCs and workstations: plan, where it stands, built site modules. */
	public record Built(@Nullable BuildPlan plan, @Nullable HqSite site, Set<String> modules) {
	}

	private static @Nullable MinecraftServer server;
	private static @Nullable HqConstruction construction;
	private static volatile @Nullable HqInput roster;
	private static final PacedHqWriter paced = new PacedHqWriter();
	private static HqWriter writer = paced;
	private static Consumer<Built> anchorSink = b -> {
	};
	private static @Nullable String designKey;
	private static dev.t3craft.build.@Nullable BuildPlan designCache;
	private static boolean registered;
	private static int ticks;

	private HqRuntime() {
	}

	/** Once, from a client initializer (before any world is opened). */
	public static void init() {
		if (registered) {
			return;
		}
		registered = true;
		SiteRuntime.init();
		SiteRuntime.setRequest(HqSiteFrame.request());
		SiteRuntime.setDesigner(BuildPlanBridge.designer(HqRuntime::design));
		SiteRuntime.addListener(new SiteListener() {
			@Override
			public void built(SiteHandle site, Expansion expansion, SiteBuilder.Report report) {
				HqConstruction c = construction;
				if (c != null) {
					c.onExpansionBuilt(expansion.module().id());
				}
			}

			@Override
			public void restored(SiteHandle site, Expansion expansion, Restorer.Result result) {
				HqConstruction c = construction;
				if (c != null) {
					c.onRestored(expansion.module().id());
				}
			}

			@Override
			public void moved(SiteHandle site, List<SiteModule> toRebuild) {
				HqConstruction c = construction;
				if (c != null) {
					c.onMoved();
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			if (s == server) {
				server = null;
				construction = null;
				designKey = null;
				designCache = null;
			}
			paced.cancelAll();
		});
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (s.isDedicatedServer()) {
				return;
			}
			paced.tick();
			if (++ticks % 20 != 0) {
				return;
			}
			try {
				HqConstruction c = construction(s);
				HqInput r = roster;
				if (r != null) {
					c.roster(r);
				}
				c.tick();
			} catch (RuntimeException e) {
				T3Craft.LOGGER.error("HQ construction failed; pausing it (/t3craft hq resume)", e);
				HqConstruction c = construction;
				if (c != null) {
					c.pause();
				}
			}
		});
	}

	/** The live roster (no subagents), from the client. Any thread. */
	public static void roster(@Nullable HqInput input) {
		roster = input;
	}

	/** The default paced writer (fallback for other writers). */
	public static PacedHqWriter pacedWriter() {
		return paced;
	}

	public static void setWriter(@Nullable HqWriter w) {
		writer = w == null ? paced : w;
	}

	/** Called on the server thread whenever the built HQ changes (hop to the client yourself). */
	public static void setAnchorSink(Consumer<Built> sink) {
		anchorSink = sink;
	}

	/** The construction of this integrated server (created on first use). Server thread. */
	public static HqConstruction construction(MinecraftServer s) {
		if (s != server || construction == null) {
			server = s;
			HqStore store = new HqStore(s.getWorldPath(LevelResource.ROOT));
			construction = new HqConstruction(new Ports(s, store), store.load());
		}
		return construction;
	}

	/** Pause or resume writes in progress too. Server thread. */
	public static void pauseWrites(boolean p) {
		writer.pause(p);
		if (writer != paced) {
			paced.pause(p);
		}
	}

	/** The site plan the designer cuts modules from (the latest target), cached by fingerprint. */
	private static dev.t3craft.build.BuildPlan design(SiteModule module) {
		HqConstruction c = construction;
		MinecraftServer s = server;
		if (c == null && s != null) {
			c = construction(s);
		}
		if (c == null || HqSiteFrame.indexOf(module.id()) == null) {
			return dev.t3craft.build.BuildPlan.of(List.of());
		}
		BuildPlan plan = c.design();
		String k = plan.fingerprint();
		if (!k.equals(designKey) || designCache == null) {
			designCache = HqSiteFrame.sitePlan(plan);
			designKey = k;
		}
		return designCache;
	}

	/** The owner of this singleplayer world, when online. */
	static @Nullable ServerPlayer owner(MinecraftServer s) {
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (s.isSingleplayerOwner(p.nameAndId())) {
				return p;
			}
		}
		return null;
	}

	static Map<String, Integer> inventory(ServerPlayer p) {
		Map<String, Integer> out = new HashMap<>();
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (!st.isEmpty()) {
				out.merge(BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), st.getCount(), Integer::sum);
			}
		}
		return out;
	}

	private static final class Ports implements HqConstruction.Ports {
		private final MinecraftServer s;
		private final HqStore store;

		Ports(MinecraftServer s, HqStore store) {
			this.s = s;
			this.store = store;
		}

		@Override
		public HqConstruction.@Nullable SiteView site() {
			SiteService svc = SiteRuntime.service(s);
			SiteHandle h = svc.handle();
			if (h == null) {
				return null;
			}
			SiteRequest want = HqSiteFrame.request();
			boolean fits = h.request().envelope().contains(want.envelope()) && h.request().baseDepth() >= want.baseDepth();
			List<String> ids = h.modules().stream().map(SiteModule::id).toList();
			return new HqConstruction.SiteView(h.id(), h.built() && !svc.busy(), h.origin(), ids, h.pending(), fits);
		}

		@Override
		public @Nullable String expand(SiteModule module, BuildPlan design) {
			SiteService.Reply r = SiteRuntime.expand(s, module);
			return r.ok() ? null : String.join(" ", r.lines());
		}

		@Override
		public HqConstruction.Cost cost(String what, List<BlockState> states) {
			ServerPlayer p = owner(s);
			PlayContext ctx = p == null ? null : PlayContexts.of(p, PlayContexts.isIntegrated(p));
			HqConstruction.Cost c = HqCost.check(what, ctx, Survival.rules(), HqCost.bill(states), p == null ? Map.of() : inventory(p));
			if (c.ok() && !c.message().isEmpty()) {
				tell(c.message());
			}
			return c;
		}

		@Override
		public HqConstruction.Update update(String label, List<HqConstruction.Cell> cells, List<HqConstruction.Cell> blockEntities) {
			SiteService svc = SiteRuntime.service(s);
			SiteHandle h = svc.handle();
			ServerLevel level = h == null ? null : level(h.dimension());
			if (level == null) {
				return new HqConstruction.Update(false, 0, "the site's dimension is not loaded");
			}
			List<SiteService.CellChange> changes = new ArrayList<>(cells.size());
			Map<Long, BlockState> wanted = new HashMap<>();
			for (HqConstruction.Cell c : cells) {
				int x = c.pos().getX();
				int y = c.pos().getY();
				int z = c.pos().getZ();
				if (c.removal()) {
					changes.add(SiteService.CellChange.restore(x, y, z));
				} else {
					changes.add(new SiteService.CellChange(x, y, z, LevelBlockWorld.keyOf(c.state())));
					wanted.put(c.pos().asLong(), c.state());
				}
			}
			List<SiteService.Claimed> dry = svc.claim(changes, false);
			List<HqWriter.Write> signs = new ArrayList<>();
			for (HqConstruction.Cell c : blockEntities) {
				if (c.state() != null) {
					signs.add(new HqWriter.Write(c.pos(), c.state(), LevelBlockWorld.keyOf(c.state()), c.blockEntity()));
				}
			}
			if (dry.isEmpty() && signs.isEmpty()) {
				return HqConstruction.Update.NOTHING;
			}
			// Only what the HQ places costs materials; restoring the terrain is free.
			List<BlockState> placed = new ArrayList<>();
			for (SiteService.Claimed c : dry) {
				BlockState st = wanted.get(net.minecraft.core.BlockPos.asLong(c.x(), c.y(), c.z()));
				if (st != null && !st.isAir()) {
					placed.add(st);
				}
			}
			if (!placed.isEmpty()) {
				HqConstruction.Cost cost = cost("The HQ update (" + dry.size() + " blocks)", placed);
				if (!cost.ok()) {
					return new HqConstruction.Update(false, 0, cost.message());
				}
			}
			LevelBlockWorld lw = new LevelBlockWorld(level);
			List<HqWriter.Write> writes = new ArrayList<>();
			for (SiteService.Claimed c : svc.claim(changes, true)) {
				net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(c.x(), c.y(), c.z());
				BlockState st = wanted.get(pos.asLong());
				if (st == null) {
					try {
						st = lw.parse(c.state());
					} catch (RuntimeException e) {
						continue;
					}
				}
				writes.add(new HqWriter.Write(pos, st, c.before(), null));
			}
			writes.addAll(signs);
			HqConstruction c = construction;
			writer.write(label, level, writes, ok -> {
				if (construction == c && c != null) {
					c.onUpdated(ok);
				}
			});
			return new HqConstruction.Update(true, writes.size(), null);
		}

		private @Nullable ServerLevel level(String dimension) {
			Identifier id = Identifier.tryParse(dimension);
			return id == null ? null : s.getLevel(ResourceKey.create(Registries.DIMENSION, id));
		}

		@Override
		public void anchors(@Nullable BuildPlan plan, @Nullable HqSite site, Set<String> built) {
			anchorSink.accept(new Built(plan, site, Set.copyOf(built)));
		}

		@Override
		public void save(HqConstruction.Saved state) {
			try {
				store.save(state);
			} catch (IOException e) {
				T3Craft.LOGGER.warn("could not save {}: {}", store.file(), e.getMessage());
			}
		}

		@Override
		public void tell(String line) {
			T3Craft.LOGGER.info("[hq] {}", line);
			ServerPlayer p = owner(s);
			if (p != null) {
				p.sendSystemMessage(Component.literal("[T3Craft] " + line));
			}
		}

		@Override
		public long now() {
			return System.currentTimeMillis();
		}
	}
}
