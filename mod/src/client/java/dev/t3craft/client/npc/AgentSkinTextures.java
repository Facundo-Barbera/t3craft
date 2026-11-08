package dev.t3craft.client.npc;

import dev.t3craft.T3Craft;
import dev.t3craft.client.config.ConfigManager;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/**
 * {@link AgentSkins.Pick} → {@link PlayerSkin}, cached per path. A pick whose PNG is not in the
 * loaded resources resolves to the mod's own {@link AgentSkins#DEFAULT} (and, if even that were
 * missing, to the vanilla default skin), so an agent never shows the purple missing texture.
 * Avatars call this only when their pick changes and keep the result, never per frame.
 */
public final class AgentSkinTextures {
	private static final Map<AgentSkins.Pick, PlayerSkin> CACHE = new ConcurrentHashMap<>();

	private AgentSkinTextures() {
	}

	public static PlayerSkin get(AgentSkins.Pick pick) {
		return CACHE.computeIfAbsent(pick, p -> resolve(p, AgentSkinTextures::exists));
	}

	private static PlayerSkin resolve(AgentSkins.Pick pick, Predicate<String> exists) {
		AgentSkins.Pick p = AgentSkins.usable(pick, exists);
		if (p != pick) {
			T3Craft.LOGGER.warn("agent skin {} is missing; using {}", pick.path(), p == null ? "the vanilla default" : p.path());
		}
		if (p == null) {
			return DefaultPlayerSkin.getDefaultSkin();
		}
		ClientAsset.Texture body = new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath(T3Craft.MOD_ID, p.path()));
		return new PlayerSkin(body, null, null, p.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE, true);
	}

	private static boolean exists(String path) {
		try {
			Identifier file = Identifier.fromNamespaceAndPath(T3Craft.MOD_ID, "textures/" + path + ".png");
			return Minecraft.getInstance().getResourceManager().getResource(file).isPresent();
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Configured {@code environments.<id>.color} (0xRRGGBB) or null. */
	public static @Nullable Integer envColor(String envId) {
		try {
			return ConfigManager.get().settings().env(envId).color();
		} catch (RuntimeException e) {
			return null;
		}
	}
}
