package dev.t3craft.blocks;

import dev.t3craft.hq.HqKit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.MapColor;

/**
 * The HQ blocks kit (cherry campus, style C): thirteen decorative blocks of t3craft's own, with their
 * own models and textures, and their {@link HqKit} by role. Registered only from {@link T3Blocks#init}
 * (so the module switch turns it off with everything else).
 *
 * <p>Blocks ({@link #IDS}): standing desk, shoji panel (lit/unlit), cherry and stone floor tiles,
 * cherry beam, column cap, paper lantern (a status lamp in disguise), room plaque (text BER),
 * cherry railing, potted cherry, stone lantern, and the emissive glow strip and glow panel (kit/glow-panel).
 */
public final class KitBlocks implements HqKit {
	/** Bump when a role's state changes (build hashes). */
	public static final String ID = "kit-3"; // kit-2: connected cherry railing (KitRailingBlock); kit-3: glow_strip, glow_panel

	/** Block ids of the kit, in creative-tab order. */
	public static final List<String> IDS = List.of("standing_desk", "shoji_panel", "cherry_floor_tile", "stone_floor_tile",
			"cherry_beam", "beam_cap", "paper_lantern", "room_plaque", "cherry_railing", "cherry_planter", "stone_lantern",
			"glow_strip", "glow_panel");

	/** Which kit block plays each role. */
	public static final Map<Role, String> ROLE_BLOCKS = roleBlocks();

	private final Map<String, Block> blocks;
	private final BlockEntityType<PlaqueBlockEntity> plaqueType;

	private KitBlocks(Map<String, Block> blocks, BlockEntityType<PlaqueBlockEntity> plaqueType) {
		this.blocks = blocks;
		this.plaqueType = plaqueType;
	}

	private static Map<Role, String> roleBlocks() {
		Map<Role, String> m = new EnumMap<>(Role.class);
		m.put(Role.DESK, "standing_desk");
		m.put(Role.WALL_PANEL, "shoji_panel");
		m.put(Role.WALL_PANEL_LIT, "shoji_panel");
		m.put(Role.FLOOR, "cherry_floor_tile");
		m.put(Role.FLOOR_ACCENT, "stone_floor_tile");
		m.put(Role.BEAM, "cherry_beam");
		m.put(Role.BEAM_CAP, "beam_cap");
		m.put(Role.LANTERN, "paper_lantern");
		m.put(Role.PLAQUE, "room_plaque");
		m.put(Role.RAILING, "cherry_railing");
		m.put(Role.GARDEN_PLANTER, "cherry_planter");
		m.put(Role.GARDEN_LANTERN, "stone_lantern");
		for (Role r : new Role[] {Role.GLOW_STRIP, Role.GLOW_STRIP_COOL, Role.GLOW_STRIP_HIGH, Role.GLOW_STRIP_HIGH_COOL}) {
			m.put(r, "glow_strip");
		}
		m.put(Role.GLOW_PANEL, "glow_panel");
		m.put(Role.GLOW_PANEL_COOL, "glow_panel");
		return Collections.unmodifiableMap(m);
	}

	/** Registration helpers of {@link T3Blocks} (block with id, block item). */
	interface Registry {
		Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props);
	}

	/**
	 * Registers the kit's blocks through {@code r}. The paper lantern shares the status lamp's block
	 * entity type ({@code lampType}, resolved later); the plaque's type ({@code plaqueType}) is
	 * registered by the caller once the plaque block exists.
	 */
	static Map<String, Block> register(Registry r, Supplier<BlockEntityType<LampBlockEntity>> lampType,
			Supplier<BlockEntityType<PlaqueBlockEntity>> plaqueType) {
		Map<String, Block> out = new java.util.LinkedHashMap<>();
		out.put("standing_desk", r.block("standing_desk", p -> new KitFacingBlock(p, KitFacingBlock.deskShape()),
				wood().noOcclusion()));
		out.put("shoji_panel", r.block("shoji_panel", KitPanelBlock::new,
				BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(1.0f).sound(SoundType.CHERRY_WOOD)
						.lightLevel(KitPanelBlock::light)));
		out.put("cherry_floor_tile", r.block("cherry_floor_tile", Block::new, wood()));
		out.put("stone_floor_tile", r.block("stone_floor_tile", Block::new,
				BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 6f).sound(SoundType.POLISHED_TUFF)));
		out.put("cherry_beam", r.block("cherry_beam", RotatedPillarBlock::new, wood()));
		out.put("beam_cap", r.block("beam_cap", p -> new KitShapedBlock(p, KitShapedBlock.capShape()), wood().noOcclusion()));
		out.put("paper_lantern", r.block("paper_lantern", p -> new KitLanternBlock(p, lampType),
				BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.5f).sound(SoundType.BAMBOO_WOOD)
						.noOcclusion().lightLevel(KitLanternBlock::light)));
		out.put("room_plaque", r.block("room_plaque", p -> new KitPlaqueBlock(p, plaqueType),
				wood().noCollision().noOcclusion()));
		out.put("cherry_railing", r.block("cherry_railing", KitRailingBlock::new,
				wood().noOcclusion()));
		out.put("cherry_planter", r.block("cherry_planter", p -> new KitShapedBlock(p, KitShapedBlock.planterShape()),
				BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.5f).sound(SoundType.CHERRY_LEAVES)
						.noOcclusion()));
		out.put("stone_lantern", r.block("stone_lantern", p -> new KitShapedBlock(p, KitShapedBlock.stoneLanternShape()),
				BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 6f).sound(SoundType.STONE)
						.noOcclusion().lightLevel(s -> 13)));
		out.put("glow_strip", r.block("glow_strip", KitGlowStripBlock::new,
				BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(0.8f).sound(SoundType.DEEPSLATE_TILES)
						.noCollision().noOcclusion().lightLevel(KitGlow::light)));
		out.put("glow_panel", r.block("glow_panel", KitGlowPanelBlock::new,
				BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(1.5f, 6f).sound(SoundType.DEEPSLATE_TILES)
						.lightLevel(KitGlow::light)));
		if (!out.keySet().equals(new java.util.LinkedHashSet<>(IDS))) {
			throw new IllegalStateException("kit ids out of sync: " + out.keySet());
		}
		return out;
	}

	private static BlockBehaviour.Properties wood() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_PINK).strength(2.0f, 3.0f).sound(SoundType.CHERRY_WOOD);
	}

	static KitBlocks of(Map<String, Block> blocks, BlockEntityType<PlaqueBlockEntity> plaqueType) {
		return new KitBlocks(Map.copyOf(blocks), plaqueType);
	}

	public Block block(String id) {
		Block b = blocks.get(id);
		if (b == null) {
			throw new IllegalArgumentException("not a kit block: " + id);
		}
		return b;
	}

	public List<Block> blocks() {
		List<Block> out = new ArrayList<>(IDS.size());
		for (String id : IDS) {
			out.add(block(id));
		}
		return out;
	}

	public BlockEntityType<PlaqueBlockEntity> plaqueType() {
		return plaqueType;
	}

	// ---- HqKit --------------------------------------------------------------------------------

	@Override
	public BlockState state(Role role, Direction facing) {
		return stateOf(block(ROLE_BLOCKS.get(role)), role, facing);
	}

	/** {@code b}'s state for {@code role} (shared with tests that look the blocks up by id). */
	static BlockState stateOf(Block b, Role role, Direction facing) {
		Direction h = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
		BlockState s = b.defaultBlockState();
		return switch (role) {
			case DESK -> s.setValue(KitFacingBlock.FACING, h);
			// the edge only; PlanConnect (or the world) resolves the ends from the neighbours
			case RAILING -> s.setValue(KitRailingBlock.FACING, h);
			case PLAQUE -> s.setValue(KitPlaqueBlock.FACING, h);
			case BEAM -> s.setValue(RotatedPillarBlock.AXIS, facing.getAxis());
			case WALL_PANEL -> s.setValue(KitPanelBlock.LIT, false);
			case WALL_PANEL_LIT -> s.setValue(KitPanelBlock.LIT, true);
			// strips: facing = where it looks (the horizontal facing; the edge it hugs is behind it); HIGH = top of the cell
			case GLOW_STRIP, GLOW_STRIP_COOL, GLOW_STRIP_HIGH, GLOW_STRIP_HIGH_COOL -> s.setValue(KitGlowStripBlock.FACING, h)
					.setValue(KitGlowStripBlock.HALF, role.high() ? Half.TOP : Half.BOTTOM)
					.setValue(KitGlow.TONE, role.cool() ? KitGlow.Tone.COOL : KitGlow.Tone.WARM);
			// panels: facing = the glowing face, any of the six
			case GLOW_PANEL, GLOW_PANEL_COOL -> s.setValue(KitGlowPanelBlock.FACING, facing)
					.setValue(KitGlow.TONE, role.cool() ? KitGlow.Tone.COOL : KitGlow.Tone.WARM);
			default -> s;
		};
	}

	@Override
	public CompoundTag plaqueData(DyeColor accent, String... lines) {
		return PlaqueText.data(accent.getTextColor(), lines);
	}

	@Override
	public String id() {
		return ID;
	}
}
