// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.blocks;

import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Base of t3craft's block entities: a {@link Binding} string that tells the client what to show
 * ({@code thread:<npcKey>}, {@code board:<i>} or empty). The HQ plan writes it as block entity data
 * ({@code {"binding": "..."}}, see {@link HqBlockFixtures}); it is saved with the world and synced to
 * the client with the chunk and on every change.
 *
 * <p><b>Block config</b> (issue #33, additive keys, a world saved before them loads unchanged):
 * <ul>
 *   <li>{@code "manual"} (boolean, default false): the binding was chosen by hand (the block's config page,
 *       {@code /t3craft blocks link}); {@link BindingWriter} leaves the block alone until "Reset to automatic"
 *       clears it. Written only when true.</li>
 *   <li>{@code "options"} (compound of strings, default empty): the block's own display options, read by its
 *       renderer; a missing key means the default (today's look). Written only when not empty.</li>
 * </ul>
 * The binding resolution order does not change: the block's own data wins over the position fallback.
 *
 * <p>Adapted from AgentCraft's {@code StationBlockEntity} (binding field, save/load, update packet
 * and update tag for client sync).
 */
public abstract class BoundBlockEntity extends BlockEntity {
	public static final String KEY = "binding";
	public static final String MANUAL_KEY = "manual";
	public static final String OPTIONS_KEY = "options";
	/** Most option keys a block keeps, and the longest key / value (a page never needs more; junk data stays small). */
	public static final int MAX_OPTIONS = 32;
	public static final int MAX_OPTION_LENGTH = 64;
	private static final Codec<Map<String, String>> OPTIONS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING);
	private String binding = "";
	private boolean manual;
	private Map<String, String> options = Map.of();

	protected BoundBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/**
	 * False for the blocks that keep options and a config page but show everything (the thread wall, the archive
	 * terminal): the HQ never binds them and {@code /t3craft blocks link} refuses them.
	 */
	public boolean bindable() {
		return true;
	}

	/** Raw stored binding ({@link Binding#parse} it). */
	public String binding() {
		return binding;
	}

	/** Server side: sets, saves and syncs the binding. */
	public void setBinding(String value) {
		String b = Binding.parse(value).wire();
		if (!b.equals(binding)) {
			binding = b;
			setChanged();
			Level level = getLevel();
			if (level != null && !level.isClientSide()) {
				level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
				bindingChanged(level);
			}
		}
	}

	/** True when the binding was set by hand: the HQ's automatic binding ({@link BindingWriter}) skips this block. */
	public boolean manual() {
		return manual;
	}

	/** The block's display options (read-only; missing key = default). */
	public Map<String, String> options() {
		return options;
	}

	/** One option, or {@code def} when it is not set. */
	public String option(String key, String def) {
		return options.getOrDefault(key, def);
	}

	/**
	 * Server side: sets the whole config of the block (binding, manual flag, options) at once, saves and syncs it
	 * when anything changed. {@code options} is cleaned ({@link #cleanOptions}). Returns true when it changed.
	 */
	public boolean setConfig(String value, boolean manual, Map<String, String> options) {
		Map<String, String> o = cleanOptions(options);
		boolean flags = manual != this.manual || !o.equals(this.options);
		this.manual = manual;
		this.options = o;
		String before = binding;
		setBinding(value); // syncs when the binding changed
		if (flags && before.equals(binding)) {
			setChanged();
			sync();
		}
		return flags || !before.equals(binding);
	}

	/** Server side: sets the manual flag only (Reset to automatic clears it). */
	public void setManual(boolean manual) {
		if (manual != this.manual) {
			this.manual = manual;
			setChanged();
			sync();
		}
	}

	/** Sorted copy without empty, too long or extra entries. */
	public static Map<String, String> cleanOptions(Map<String, String> in) {
		TreeMap<String, String> out = new TreeMap<>();
		if (in != null) {
			for (Map.Entry<String, String> e : in.entrySet()) {
				String k = e.getKey();
				String v = e.getValue();
				if (k == null || v == null || k.isEmpty() || v.isEmpty() || k.length() > MAX_OPTION_LENGTH || v.length() > MAX_OPTION_LENGTH) {
					continue;
				}
				if (out.size() >= MAX_OPTIONS) {
					break;
				}
				out.put(k, v);
			}
		}
		return out.isEmpty() ? Map.of() : java.util.Collections.unmodifiableMap(out);
	}

	private void sync() {
		Level level = getLevel();
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** Server side, after {@link #setBinding} stored a different binding. */
	protected void bindingChanged(Level level) {
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		binding = Binding.parse(input.getStringOr(KEY, "")).wire();
		manual = input.getBooleanOr(MANUAL_KEY, false);
		options = cleanOptions(input.read(OPTIONS_KEY, OPTIONS_CODEC).orElse(Map.of()));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!binding.isEmpty()) {
			output.putString(KEY, binding);
		}
		if (manual) {
			output.putBoolean(MANUAL_KEY, true);
		}
		if (!options.isEmpty()) {
			output.store(OPTIONS_KEY, OPTIONS_CODEC, options);
		}
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}
}
