package dev.t3craft.client.npc.helpers;

import com.mojang.authlib.GameProfile;
import dev.t3craft.client.npc.AgentSkinTextures;
import dev.t3craft.client.npc.AgentSkins;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * A subagent drawn as a small client-only player (never sent to or known by any server): the
 * apprentice variant of its parent's agent skin ({@link AgentSkins#forHelper}: same face and shirt,
 * plus an apron and a cap), at {@link #SCALE} of the parent's size. The name tag
 * ({@code glyph title}) is tinted with the subagent's state colour.
 */
final class HelperAvatar extends RemotePlayer {
	static final int TITLE_MAX = 22;
	/** Model scale: about the height of the baby villagers helpers used to be. */
	static final double SCALE = 0.6;

	final String helperId;
	private Component label = Component.empty();
	private AgentSkins.Pick pick = AgentSkins.DEFAULT;
	private PlayerSkin skin;

	HelperAvatar(ClientLevel level, String helperId) {
		super(level, new GameProfile(UUID.nameUUIDFromBytes(("t3craft-helper:" + helperId).getBytes(StandardCharsets.UTF_8)), "helper"));
		this.helperId = helperId;
		this.skin = AgentSkinTextures.get(pick);
		AttributeInstance scale = getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.setBaseValue(SCALE);
		}
		setNoGravity(true);
		setSilent(true);
	}

	void update(HelperView v) {
		AgentSkins.Pick next = AgentSkins.forHelper(v.parentId(), AgentSkinTextures.envColor(AgentSkins.envOf(v.parentId())));
		if (!next.equals(pick)) {
			pick = next;
			skin = AgentSkinTextures.get(next);
		}
		String title = dev.t3craft.t3.Presentation.clip(v.title().isBlank() ? "(subagent)" : v.title(), TITLE_MAX);
		Component nextLabel = Component.literal(v.state().glyph + " " + title).withColor(v.color());
		if (!nextLabel.equals(label)) {
			label = nextLabel;
		}
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
