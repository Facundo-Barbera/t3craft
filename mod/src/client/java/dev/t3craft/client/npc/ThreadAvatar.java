package dev.t3craft.client.npc;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * A thread rendered as a player: a client-only {@link RemotePlayer} (no registered entity type, no
 * network traffic, so the mod works in any world or server). The skin is one of Minecraft's
 * built-in default skins, picked deterministically from the NPC's {@code skin} seed
 * ({@code <env>/<state>}), so threads of one machine look alike and a state change shows. The
 * vanilla name tag shows the resolved label in the state colour.
 */
public final class ThreadAvatar extends RemotePlayer {
	private Component label = Component.empty();
	private UUID skinId;

	public ThreadAvatar(ClientLevel level, String npcId) {
		super(level, new GameProfile(uuid("t3craft-npc:" + npcId), "t3craft"));
		this.skinId = getUUID();
	}

	static UUID uuid(String seed) {
		return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
	}

	void update(NpcInfo info) {
		this.label = Nameplate.of(info);
		this.skinId = uuid("t3craft-skin:" + info.skin());
	}

	@Override
	public Component getDisplayName() {
		return label;
	}

	@Override
	public PlayerSkin getSkin() {
		return DefaultPlayerSkin.get(skinId);
	}
}
