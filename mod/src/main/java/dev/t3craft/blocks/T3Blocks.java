package dev.t3craft.blocks;

import dev.t3craft.T3Craft;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqKit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
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
 * The custom blocks module: monitor, status lamp, board bulb, archive terminal and thread wall, the HQ decorative kit
 * ({@link KitBlocks}, installed as {@link HqKit}), their block entity types and block items. <b>The single registration point</b>: {@link #init} is the only code in the mod that
 * registers blocks, items or block entity types for this module, and it registers nothing at all
 * when {@link BlocksSwitch} says no. Everything else (client renderers, the lamp driver, the HQ
 * adapter) first asks {@link #active()} and falls back to vanilla when it is false:
 * <ul>
 *   <li>the HQ keeps its vanilla computers, copper bulbs and signs ({@link HqFixtures#VANILLA}) and
 *       its styles get vanilla stand-ins for every kit role ({@link HqKit#VANILLA});</li>
 *   <li>the workstations keep drawing their own client-side screens and props.</li>
 * </ul>
 *
 * <p>Adding a block: see {@code docs/features/blocks.md}.
 */
public final class T3Blocks {
	public static final String NS = T3Craft.MOD_ID;

	/** What the module registered. */
	public record Content(
			MonitorBlock monitorBlock,
			StatusLampBlock statusLampBlock,
			StatusLampBlock boardLampBlock,
			ThreadBoardBlock threadBoardBlock,
			List<Item> items,
			BlockEntityType<MonitorBlockEntity> monitorType,
			BlockEntityType<LampBlockEntity> lampType,
			BlockEntityType<ThreadBoardBlockEntity> threadBoardType,
			KitBlocks kit,
			ArchiveTerminalBlock archiveTerminalBlock,
			BlockEntityType<ArchiveTerminalBlockEntity> archiveTerminalType,
			// kit/console-terminal
			ConsoleTerminalBlock consoleTerminalBlock,
			BlockEntityType<ConsoleTerminalBlockEntity> consoleTerminalType,
			// kit/decision-podium
			DecisionPodiumBlock decisionPodiumBlock,
			BlockEntityType<LampBlockEntity> decisionPodiumType,
			// kit/merge-station
			MergeStationBlock mergeStationBlock,
			BlockEntityType<MergeStationBlockEntity> mergeStationType) implements HqBlockFixtures.States {
		@Override
		public BlockState consoleTerminal(Direction facing) {
			return consoleTerminalBlock.defaultBlockState().setValue(ConsoleTerminalBlock.FACING, facing);
		}

		@Override
		public BlockState podium(Direction facing) {
			return decisionPodiumBlock.defaultBlockState().setValue(DecisionPodiumBlock.FACING, facing);
		}

		@Override
		public BlockState mergeStation(Direction facing) {
			return mergeStationBlock.defaultBlockState().setValue(MergeStationBlock.FACING, facing);
		}

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

		@Override
		public BlockState threadBoard(Direction facing) {
			return threadBoardBlock.defaultBlockState().setValue(ThreadBoardBlock.FACING, facing);
		}

		@Override
		public BlockState archiveTerminal(Direction facing) {
			return archiveTerminalBlock.defaultBlockState().setValue(ArchiveTerminalBlock.FACING, facing);
		}

		@Override
		public BlockState plaque(Direction facing) {
			return kit.plaque(facing);
		}

		@Override
		public CompoundTag plaqueData(int color, String... lines) {
			return PlaqueText.data(color, lines);
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
			HqKit.use(null);
			T3Craft.LOGGER.info("T3Craft blocks module disabled ({}): HQ and workstations stay vanilla", sw.reason());
			return null;
		}
		@SuppressWarnings("unchecked")
		BlockEntityType<MonitorBlockEntity>[] monitorType = new BlockEntityType[1];
		@SuppressWarnings("unchecked")
		BlockEntityType<LampBlockEntity>[] lampType = new BlockEntityType[1];
		@SuppressWarnings("unchecked")
		BlockEntityType<ThreadBoardBlockEntity>[] wallType = new BlockEntityType[1];
		@SuppressWarnings("unchecked")
		BlockEntityType<ArchiveTerminalBlockEntity>[] terminalType = new BlockEntityType[1];
		@SuppressWarnings("unchecked")
		BlockEntityType<PlaqueBlockEntity>[] plaqueType = new BlockEntityType[1];
		@SuppressWarnings("unchecked")
		BlockEntityType<LampBlockEntity>[] podiumType = new BlockEntityType[1];

		MonitorBlock monitor = (MonitorBlock) block(r, "monitor", p -> new MonitorBlock(p, () -> monitorType[0]),
				BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5f).sound(SoundType.METAL).noOcclusion());
		StatusLampBlock lamp = (StatusLampBlock) block(r, "status_lamp", p -> new StatusLampBlock(p, () -> lampType[0]), lampProps());
		StatusLampBlock board = (StatusLampBlock) block(r, "board_lamp", p -> new StatusLampBlock(p, () -> lampType[0]), lampProps());
		ThreadBoardBlock wall = (ThreadBoardBlock) block(r, "thread_board", p -> new ThreadBoardBlock(p, () -> wallType[0]),
				BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD));
		ArchiveTerminalBlock terminal = (ArchiveTerminalBlock) block(r, "archive_terminal", p -> new ArchiveTerminalBlock(p, () -> terminalType[0]),
				BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.5f).sound(SoundType.METAL).noOcclusion()
						.lightLevel(s -> 5));
		DecisionPodiumBlock podium = (DecisionPodiumBlock) block(r, "decision_podium", p -> new DecisionPodiumBlock(p, () -> podiumType[0]),
				BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).noOcclusion()
						.lightLevel(DecisionPodiumBlock::light));
		Map<String, Block> kitBlocks = KitBlocks.register((name, factory, props) -> block(r, name, factory, props),
				() -> lampType[0], () -> plaqueType[0]);
		// kit/console-terminal: its own block + block entity (bound like a monitor); the kit plays it as Role.CONSOLE
		@SuppressWarnings("unchecked")
		BlockEntityType<ConsoleTerminalBlockEntity>[] consoleType = new BlockEntityType[1];
		ConsoleTerminalBlock console = (ConsoleTerminalBlock) block(r, "console_terminal", p -> new ConsoleTerminalBlock(p, () -> consoleType[0]),
				BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.5f).sound(SoundType.METAL).noOcclusion()
						.lightLevel(s -> 6));
		Map<String, Block> kitWithConsole = new java.util.LinkedHashMap<>(kitBlocks);
		kitWithConsole.put("console_terminal", console);
		// kit/merge-station: its own block + block entity (bound like a monitor); the kit plays it as Role.MERGE_STATION
		@SuppressWarnings("unchecked")
		BlockEntityType<MergeStationBlockEntity>[] mergeType = new BlockEntityType[1];
		MergeStationBlock merge = (MergeStationBlock) block(r, "merge_station", p -> new MergeStationBlock(p, () -> mergeType[0]),
				BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.5f).sound(SoundType.METAL).noOcclusion()
						.lightLevel(MergeStationBlock::light));
		kitWithConsole.put("merge_station", merge);

		monitorType[0] = r.blockEntity(id("monitor"), new BlockEntityType<>(
				(pos, state) -> new MonitorBlockEntity(monitorType[0], pos, state), Set.of(monitor)));
		lampType[0] = r.blockEntity(id("status_lamp"), new BlockEntityType<>(
				(pos, state) -> new LampBlockEntity(lampType[0], pos, state), Set.of(lamp, board, kitBlocks.get("paper_lantern"))));
		plaqueType[0] = r.blockEntity(id("room_plaque"), new BlockEntityType<>(
				(pos, state) -> new PlaqueBlockEntity(plaqueType[0], pos, state), Set.of(kitBlocks.get("room_plaque"))));
		wallType[0] = r.blockEntity(id("thread_board"), new BlockEntityType<>(
				(pos, state) -> new ThreadBoardBlockEntity(wallType[0], pos, state), Set.of(wall)));
		terminalType[0] = r.blockEntity(id("archive_terminal"), new BlockEntityType<>(
				(pos, state) -> new ArchiveTerminalBlockEntity(terminalType[0], pos, state), Set.of(terminal)));
		consoleType[0] = r.blockEntity(id("console_terminal"), new BlockEntityType<>(
				(pos, state) -> new ConsoleTerminalBlockEntity(consoleType[0], pos, state), Set.of(console)));
		podiumType[0] = r.blockEntity(id("decision_podium"), new BlockEntityType<>(
				(pos, state) -> new LampBlockEntity(podiumType[0], pos, state), Set.of(podium)));
		mergeType[0] = r.blockEntity(id("merge_station"), new BlockEntityType<>(
				(pos, state) -> new MergeStationBlockEntity(mergeType[0], pos, state), Set.of(merge)));
		KitBlocks kit = KitBlocks.of(kitWithConsole, plaqueType[0]);

		List<Item> items = new ArrayList<>(List.of(item(r, "monitor", monitor), item(r, "status_lamp", lamp), item(r, "board_lamp", board),
				item(r, "archive_terminal", terminal),
				item(r, "thread_board", wall)));
		items.add(item(r, "decision_podium", podium));
		for (String name : KitBlocks.IDS) {
			items.add(item(r, name, kit.block(name)));
		}
		items.add(item(r, "console_terminal", console));
		items.add(item(r, "merge_station", merge));
		items = List.copyOf(items);
		r.creativeTab(items);

		Content c = new Content(monitor, lamp, board, wall, items, monitorType[0], lampType[0], wallType[0], kit, terminal, terminalType[0],
				console, consoleType[0], podium, podiumType[0], merge, mergeType[0]);
		HqFixtures.use(new HqBlockFixtures(c));
		HqKit.use(kit);
		content = c;
		T3Craft.LOGGER.info("T3Craft blocks module on ({}): monitor, status_lamp, board_lamp, archive_terminal, console_terminal, merge_station, thread_board + HQ kit ({} blocks)", sw.reason(),
				KitBlocks.IDS.size());
		return c;
	}

	/** noOcclusion: the status lamp model is a small lantern, not a cube; neighbours must keep their faces next to it. */
	private static BlockBehaviour.Properties lampProps() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.8f).sound(SoundType.COPPER).noOcclusion().lightLevel(StatusLampBlock::light);
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

	/** Test/hot-swap/render-boot only (called by the render daemon's boot): forget the result (registries cannot be undone; this only resets the flag). */
	public static synchronized void resetForTests() {
		content = null;
		decision = BlocksSwitch.OFF;
		HqFixtures.use(null);
		HqKit.use(null);
	}
}
