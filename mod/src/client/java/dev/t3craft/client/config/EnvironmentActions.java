package dev.t3craft.client.config;

import dev.t3craft.client.ModConfig;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.t3.Environments;
import dev.t3craft.t3.Pairing;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The minimal surface the settings screens need from the rest of the mod. {@link #DEFAULT} wires
 * it to what main already provides (public API of {@link T3CraftClient} and {@link Environments});
 * nothing here touches a token: credentials are only read to be re-saved unchanged on rename.
 *
 * <p>Integration: if another branch changes how pairing or the environment list works, implement
 * this interface there and hand it to {@code SettingsScreen} / {@code EnvironmentsScreen}.
 */
public interface EnvironmentActions {
	/** One paired environment as shown in the list (no secrets). */
	record EnvRow(String id, String label, String url, String status, String detail) {
	}

	List<EnvRow> list();

	/** Opens main's read-only pairing screen (paste link, or code + URL). */
	net.minecraft.client.gui.screens.Screen pairScreen(net.minecraft.client.gui.screens.Screen parent);

	/** Forgets the environment locally (credential deleted) and reconnects. Returns a player-facing message. */
	String unpair(String id);

	/** Renames the environment's display label. Returns a player-facing message. */
	String rename(String id, String newLabel);

	/** Re-reads config + environments and reconnects (same as {@code /t3craft reload}). */
	void reload();

	EnvironmentActions DEFAULT = new EnvironmentActions() {
		@Override
		public List<EnvRow> list() {
			Map<String, String[]> live = client() == null ? Map.of() : client().environments();
			return envs().list().stream().map(e -> {
				String[] s = live.get(e.id());
				return new EnvRow(e.id(), e.label(), e.url().toString(), s == null ? "-" : s[1], s == null ? "" : s[2]);
			}).toList();
		}

		@Override
		public net.minecraft.client.gui.screens.Screen pairScreen(net.minecraft.client.gui.screens.Screen parent) {
			return new dev.t3craft.client.screen.PairScreen(parent);
		}

		@Override
		public String unpair(String id) {
			try {
				boolean removed = client() != null ? client().unpair(id) : envs().remove(id);
				ConfigManager.get().update(s -> s.withoutEnv(id));
				return removed ? "Forgot " + id + ". Also revoke its session on the host (t3 auth session list / revoke)."
						: "No paired environment " + id;
			} catch (IOException e) {
				return "Could not remove " + id + ": " + e.getClass().getSimpleName();
			}
		}

		@Override
		public String rename(String id, String newLabel) {
			String label = ConfigCodec.sanitizeLabel(newLabel);
			if (label == null) {
				return "The name cannot be empty";
			}
			Environments envs = envs();
			Environments.Environment env = envs.list().stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
			if (env == null) {
				return "No paired environment " + id;
			}
			Pairing.@Nullable Session session = envs.credential(id);
			if (session != null) {
				try {
					// Same id, url and credential; only the label in environments.json changes.
					envs.save(new Environments.Environment(id, label, env.url()), session);
					ConfigManager.get().update(s -> s.withEnv(id, s.env(id).withLabel(null)));
					reload();
					return "Renamed " + id + " to " + label;
				} catch (IOException e) {
					// fall through to the display override
				}
			}
			ConfigManager.get().update(s -> s.withEnv(id, s.env(id).withLabel(label)));
			return "Saved " + label + " as display name for " + id + " (credential missing or expired: pair again to apply everywhere)";
		}

		@Override
		public void reload() {
			if (client() != null) {
				client().reload();
			}
		}

		private static @Nullable T3CraftClient client() {
			return T3CraftClient.get();
		}

		private static Environments envs() {
			return new Environments(ModConfig.dataDir());
		}
	};
}
