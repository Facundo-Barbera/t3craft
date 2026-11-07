package dev.t3craft.survival;

import dev.t3craft.T3Craft;
import dev.t3craft.portable.PortableItems;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;

/**
 * "First contact" advancement ({@code data/t3craft/advancement/first_contact.json}): granted on the
 * server the first time a player uses the T3 Terminal. The advancement's only criterion is
 * {@code minecraft:impossible}, so nothing but this hook grants it. Never consumes the interaction
 * ({@code PASS}), so the item's own use (opening the screen) runs as before.
 */
public final class FirstContact {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(T3Craft.MOD_ID, "first_contact");
	public static final String CRITERION = "used_terminal";

	private FirstContact() {
	}

	public static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			Item terminal = PortableItems.terminal();
			if (!level.isClientSide() && terminal != null && player instanceof ServerPlayer sp
					&& player.getItemInHand(hand).is(terminal) && Survival.rules().firstContactAdvancement()) {
				award(sp);
			}
			return InteractionResult.PASS;
		});
	}

	/** Grants the advancement (idempotent). Public so the key path can call it on the integrated server too. */
	public static boolean award(ServerPlayer player) {
		AdvancementHolder h = player.level().getServer().getAdvancements().get(ID);
		return h != null && player.getAdvancements().award(h, CRITERION);
	}
}
