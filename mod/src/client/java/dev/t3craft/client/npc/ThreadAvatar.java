package dev.t3craft.client.npc;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.jspecify.annotations.Nullable;

/**
 * A thread rendered as a player: a client-only {@link RemotePlayer} (no registered entity type, no
 * network traffic, so the mod works in any world or server). The skin is one of the mod's own
 * agent skins ({@link AgentSkins}): the character comes from the thread's NPC id (same thread,
 * same face), the shirt from its environment's colour. The vanilla name tag shows the resolved
 * label in the state colour.
 *
 * <p>World text (docs/features/world-text.md): the vanilla name tag shows only while the registry
 * says so ({@link #setTagHidden}); a hidden tag is the vanilla team rule "name tag visibility:
 * never", answered through a client-only team that is never added to the scoreboard.
 */
public final class ThreadAvatar extends RemotePlayer {
	private final String npcId;
	private Component label = Component.empty();
	private AgentSkins.Pick pick;
	private PlayerSkin skin;
	private boolean tagHidden;
	private @Nullable PlayerTeam hiddenTeam;

	public ThreadAvatar(ClientLevel level, String npcId) {
		super(level, new GameProfile(uuid("t3craft-npc:" + npcId), "t3craft"));
		this.npcId = npcId;
		this.pick = AgentSkins.forThread(npcId, AgentSkinTextures.envColor(AgentSkins.envOf(npcId)));
		this.skin = AgentSkinTextures.get(pick);
	}

	static UUID uuid(String seed) {
		return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
	}

	void update(NpcInfo info) {
		update(info, Nameplate.of(info));
	}

	/** fix/hq-load-npc-reconcile: with a name tag already built (off-thread, {@link Nameplate} is pure). */
	void update(NpcInfo info, Component nameTag) {
		this.label = nameTag;
		// Only the environment colour can change the pick; re-resolve only then (cached lookup).
		AgentSkins.Pick next = AgentSkins.forThread(npcId, AgentSkinTextures.envColor(AgentSkins.envOf(npcId)));
		if (!next.equals(pick)) {
			pick = next;
			skin = AgentSkinTextures.get(next);
		}
	}

	/** Hide (true) or show the vanilla name tag. Cheap: a field read by the renderer. */
	void setTagHidden(boolean hidden) {
		tagHidden = hidden;
	}

	boolean tagHidden() {
		return tagHidden;
	}

	/**
	 * The name tag's visibility goes through the entity's team (vanilla {@code LivingEntityRenderer}
	 * hides the tag of an entity whose team says {@link Team.Visibility#NEVER}). The team is local to
	 * this avatar and never registered with the scoreboard.
	 */
	@Override
	public @Nullable PlayerTeam getTeam() {
		if (!tagHidden) {
			return super.getTeam();
		}
		PlayerTeam t = hiddenTeam;
		if (t == null) {
			t = new PlayerTeam(level().getScoreboard(), "t3craft_npc_tag_hidden");
			t.setNameTagVisibility(Team.Visibility.NEVER);
			hiddenTeam = t;
		}
		return t;
	}

	@Override
	public Component getDisplayName() {
		return label;
	}

	@Override
	public PlayerSkin getSkin() {
		return skin;
	}
}
