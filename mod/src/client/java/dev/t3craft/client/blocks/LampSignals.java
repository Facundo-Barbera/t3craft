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

	/** The board bulbs, index 0 first. */
	public static List<LampStatus> board(Collection<WorkstationThread> threads, int bulbs) {
		List<WorkstationThread> urgent = new ArrayList<>();
		for (WorkstationThread t : threads) {
			if (needsPlayer(t.status())) {
				urgent.add(t);
			}
		}
		urgent.sort(Comparator.comparingInt((WorkstationThread t) -> -of(t.status()).urgency()).thenComparing(WorkstationThread::key));
		List<LampStatus> out = new ArrayList<>(bulbs);
		for (int i = 0; i < bulbs; i++) {
			out.add(i < urgent.size() ? of(urgent.get(i).status()) : LampStatus.OFF);
		}
		return out;
	}

	/** Binding wire string → wanted status, for every thread lamp and {@link #BOARD_BULBS} board bulbs. */
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
		return out;
	}
}
