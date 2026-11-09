package dev.t3craft.client.blocks;

import dev.t3craft.blocks.Binding;
import dev.t3craft.blocks.LampStatus;
import dev.t3craft.client.workstation.StationStatus;
import dev.t3craft.client.workstation.WorkstationThread;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thread states → what each lamp shows. Pure and deterministic (input order never matters).
 *
 * <ul>
 *   <li>Desk lamp ({@code thread:<key>}): the thread's own state; archived (settled) → OFF.</li>
 *   <li>Board bulb {@code board:<i>}: the i-th thread that needs the player, most urgent first
 *       (errors, then "needs you"; ties by key), in its colour; bulbs beyond that count are OFF. So
 *       the board reads "how many are waiting, and how bad" from across the hub.</li>
 *   <li>Beacon ({@code beacon}, the hub lantern): the whole HQ at a glance, {@link #beacon}.</li>
 * </ul>
 */
public final class LampSignals {
	/** Board bulbs computed (more than any HQ board has; unknown indexes are OFF anyway). */
	public static final int BOARD_BULBS = 16;

	private LampSignals() {
	}

	public static LampStatus of(StationStatus s) {
		return switch (s) {
			case ATTENTION -> LampStatus.ATTENTION;
			case ERROR -> LampStatus.ERROR;
			case WORKING -> LampStatus.WORKING;
			case DONE -> LampStatus.DONE;
			case IDLE -> LampStatus.IDLE;
			case ARCHIVED -> LampStatus.OFF;
		};
	}

	/** True for threads that a board bulb lights up for. */
	public static boolean needsPlayer(StationStatus s) {
		return s == StationStatus.ATTENTION || s == StationStatus.ERROR;
	}

	/** The threads that need the player, most urgent first (errors, then "needs you"; ties by key): what board bulb and podium {@code i} show. */
	public static List<WorkstationThread> urgent(Collection<WorkstationThread> threads) {
		List<WorkstationThread> urgent = new ArrayList<>();
		for (WorkstationThread t : threads) {
			if (needsPlayer(t.status())) {
				urgent.add(t);
			}
		}
		urgent.sort(Comparator.comparingInt((WorkstationThread t) -> -of(t.status()).urgency()).thenComparing(WorkstationThread::key));
		return urgent;
	}

	/** The thread key of board bulb / decision podium {@code index} ({@code board:<index>}), or null when fewer threads need the player. */
	public static @org.jspecify.annotations.Nullable String boardThread(Collection<WorkstationThread> threads, int index) {
		List<WorkstationThread> urgent = urgent(threads);
		return index >= 0 && index < urgent.size() ? urgent.get(index).key() : null;
	}

	/** The board bulbs, index 0 first. */
	public static List<LampStatus> board(Collection<WorkstationThread> threads, int bulbs) {
		List<WorkstationThread> urgent = urgent(threads);
		List<LampStatus> out = new ArrayList<>(bulbs);
		for (int i = 0; i < bulbs; i++) {
			out.add(i < urgent.size() ? of(urgent.get(i).status()) : LampStatus.OFF);
		}
		return out;
	}

	/**
	 * The HQ's aggregate state for the hub beacon, most urgent first: anything that needs you
	 * (ATTENTION) &gt; an error &gt; work going on &gt; IDLE (also done threads and an empty HQ). Settled
	 * threads do not count. Adapted from AgentCraft's {@code HqWorldDriver.beaconLamp}.
	 */
	public static LampStatus beacon(Collection<WorkstationThread> threads) {
		boolean error = false;
		boolean working = false;
		for (WorkstationThread t : threads) {
			switch (t.status()) {
				case ATTENTION -> {
					return LampStatus.ATTENTION;
				}
				case ERROR -> error = true;
				case WORKING -> working = true;
				default -> {
				}
			}
		}
		return error ? LampStatus.ERROR : working ? LampStatus.WORKING : LampStatus.IDLE;
	}

	/** Binding wire string → wanted status, for every thread lamp, {@link #BOARD_BULBS} board bulbs and the beacon. */
	public static Map<String, LampStatus> wanted(Collection<WorkstationThread> threads) {
		Map<String, LampStatus> out = new LinkedHashMap<>();
		List<WorkstationThread> sorted = new ArrayList<>(threads);
		sorted.sort(Comparator.comparing(WorkstationThread::key));
		for (WorkstationThread t : sorted) {
			out.put(Binding.thread(t.key()).wire(), of(t.status()));
		}
		List<LampStatus> board = board(threads, BOARD_BULBS);
		for (int i = 0; i < board.size(); i++) {
			out.put(Binding.board(i).wire(), board.get(i));
		}
		out.put(Binding.BEACON.wire(), beacon(threads));
		return out;
	}
}
