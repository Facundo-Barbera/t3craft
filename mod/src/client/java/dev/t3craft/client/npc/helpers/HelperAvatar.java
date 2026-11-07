package dev.t3craft.client.npc.helpers;

import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;

/**
 * A subagent drawn as a client-only baby villager (never sent to or known by any server). The
 * villager's biome type and profession are picked from the helper id, so each helper keeps its own
 * look; the name tag ({@code glyph title}) is tinted with the subagent's state colour.
 */
final class HelperAvatar extends Villager {
	static final List<ResourceKey<VillagerType>> TYPES = List.of(VillagerType.PLAINS, VillagerType.DESERT, VillagerType.JUNGLE,
			VillagerType.SAVANNA, VillagerType.SNOW, VillagerType.SWAMP, VillagerType.TAIGA);
	static final List<ResourceKey<VillagerProfession>> PROFESSIONS = List.of(VillagerProfession.LIBRARIAN, VillagerProfession.CARTOGRAPHER,
			VillagerProfession.CLERIC, VillagerProfession.TOOLSMITH, VillagerProfession.MASON, VillagerProfession.FLETCHER);
	static final int TITLE_MAX = 22;

	final String helperId;
	private int variant = -1;
	private Component label = Component.empty();

	HelperAvatar(ClientLevel level, String helperId) {
		super(EntityTypes.VILLAGER, level);
		this.helperId = helperId;
		setBaby(true);
		setNoAi(true);
		setNoGravity(true);
		setSilent(true);
		setCustomNameVisible(true);
	}

	void update(HelperView v) {
		int want = v.variant(TYPES.size() * PROFESSIONS.size());
		if (want != variant) {
			variant = want;
			// Built-in (static) registries: no dependency on what the server synced.
			setVillagerData(getVillagerData()
					.withType(BuiltInRegistries.VILLAGER_TYPE.getOrThrow(TYPES.get(want % TYPES.size())))
					.withProfession(BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(PROFESSIONS.get(want / TYPES.size()))));
		}
		String title = dev.t3craft.t3.Presentation.clip(v.title().isBlank() ? "(subagent)" : v.title(), TITLE_MAX);
		Component next = Component.literal(v.state().glyph + " " + title).withColor(v.color());
		if (!next.equals(label)) {
			label = next;
			setCustomName(next);
		}
	}
}
