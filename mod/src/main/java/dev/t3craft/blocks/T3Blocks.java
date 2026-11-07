package dev.t3craft.blocks;

import dev.t3craft.T3Craft;
import dev.t3craft.hq.HqFixtures;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * The custom blocks module: monitor, status lamp and board bulb, their block entity types and block
 * items. <b>The single registration point</b>: {@link #init} is the only code in the mod that
 * registers blocks, items or block entity types for this module, and it registers nothing at all
 * when {@link BlocksSwitch} says no. Everything else (client renderers, the lamp driver, the HQ
 * adapter) first asks {@link #active()} and falls back to vanilla when it is false:
 * <ul>
 *   <li>the HQ keeps its vanilla computers and copper bulbs ({@link HqFixtures#VANILLA});</li>
 *   <li>the workstations keep drawing their own client-side screens and props.</li>
 * </ul>
 *
 * <p>Adding a block: see {@code docs/feat-blocks.md}.
 */
public final class T3Blocks {
	public static final String NS = T3Craft.MOD_ID;

	/** What the module registered. */
	public record Content(
			MonitorBlock monitorBlock,
			StatusLampBlock statusLampBlock,
			StatusLampBlock boardLampBlock,
			List<Item> items,
			BlockEntityType<MonitorBlockEntity> monitorType,
			BlockEntityType<LampBlockEntity> lampType) implements HqBlockFixtures.States {
		@Override
		public BlockState monitor(Direction facing) {
			return monitorBlock.defaultBlockState().setValue(MonitorBlock.FACING, facing);
		}

		@Override
		public BlockState monitor(Direction facing, ScreenRect.Sides sides) {
			return MonitorBlock.connected(monitor(facing), sides);
		}

		@Override
		public BlockState lamp() {
			return statusLampBlock.defaultBlockState();
		}

		@Override
		public BlockState boardLamp() {
			return boardLampBlock.defaultBlockState();
		}
	}

	private static volatile @Nullable Content content;
	private static volatile BlocksSwitch decision = BlocksSwitch.OFF;

	private T3Blocks() {
	}

	/** True when the module registered its blocks this run. */
	public static boolean active() {
		return content != null;
	}

	public static @Nullable Content content() {
		return content;
	}

	/** The switch {@link #init} was given (OFF before init). */
	public static BlocksSwitch decision() {
		return decision;
	}

	/**
	 * Registers the module through {@code r} when {@code sw} is enabled and installs the HQ adapter;
	 * otherwise registers nothing and leaves the HQ vanilla. Idempotent (a second call returns the
	 * first result).
	 */
	public static synchronized @Nullable Content init(BlocksSwitch sw, BlockRegistrar r) {
		if (content != null) {
			return content;
		}
		decision = sw;
		if (!sw.enabled()) {
			HqFixtures.use(null);
			T3Craft.LOGGER.info("T3Craft blocks module disabled ({}): HQ and workstations stay vanilla", sw.reason());
			return null;
		}
		@SuppressWarnings("unchecked")
		BlockEntityType<MonitorBlockEntity>[] monitorType = new BlockEntityType[1];
		@SuppressWarnings("unchecked")
		BlockEntityType<LampBlockEntity>[] lampType = new BlockEntityType[1];

		MonitorBlock monitor = (MonitorBlock) block(r, "monitor", p -> new MonitorBlock(p, () -> monitorType[0]),
				BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5f).sound(SoundType.METAL));
		StatusLampBlock lamp = (StatusLampBlock) block(r, "status_lamp", p -> new StatusLampBlock(p, () -> lampType[0]), lampProps());
		StatusLampBlock board = (StatusLampBlock) block(r, "board_lamp", p -> new StatusLampBlock(p, () -> lampType[0]), lampProps());

		monitorType[0] = r.blockEntity(id("monitor"), new BlockEntityType<>(
				(pos, state) -> new MonitorBlockEntity(monitorType[0], pos, state), Set.of(monitor)));
		lampType[0] = r.blockEntity(id("status_lamp"), new BlockEntityType<>(
				(pos, state) -> new LampBlockEntity(lampType[0], pos, state), Set.of(lamp, board)));

		List<Item> items = List.of(item(r, "monitor", monitor), item(r, "status_lamp", lamp), item(r, "board_lamp", board));
		r.creativeTab(items);

		Content c = new Content(monitor, lamp, board, items, monitorType[0], lampType[0]);
		HqFixtures.use(new HqBlockFixtures(c));
		content = c;
		T3Craft.LOGGER.info("T3Craft blocks module on ({}): monitor, status_lamp, board_lamp", sw.reason());
		return c;
	}

	private static BlockBehaviour.Properties lampProps() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.8f).sound(SoundType.COPPER).lightLevel(StatusLampBlock::light);
	}

	static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NS, path);
	}

	private static Block block(BlockRegistrar r, String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(name));
		return r.block(id(name), factory.apply(props.setId(key)));
	}

	private static Item item(BlockRegistrar r, String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
		return r.item(id(name), new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	/** Test/hot-swap only: forget the result (registries cannot be undone; this only resets the flag). */
	static synchronized void resetForTests() {
		content = null;
		decision = BlocksSwitch.OFF;
		HqFixtures.use(null);
	}
}
