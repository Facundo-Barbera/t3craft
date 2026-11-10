package dev.t3craft.build;

import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.level.block.FallingBlock;
import org.jspecify.annotations.Nullable;

/**
 * feat/builder-sites: the compiled side of {@code build.order: "sites"} ({@link SiteCursor} runs it,
 * {@link SiteDispatch} decides which site is open). Pure: plain arrays, no world access, built off-thread once per
 * job ({@link #of}); {@link #compile} (the order inside one site) is pure too and runs off-thread when a site opens.
 * Design: docs/plans/builder-scheduling.md; how to try it: docs/features/builder-sites.md.
 *
 * <p><b>Entries</b> have a kind, per column bottom-up as in {@link RadialCursor}: {@link #FILL} (hidden terrain of the
 * column's bottom stack), {@link #GROUND} (the top two entries of that stack, plus a paving block lying on it, #48: the walkable surface and the layer
 * under it), {@link #CLEAR} (air) and {@link #BUILD} (everything a builder places). Fill, ground and air are laid by
 * streams, never by a builder.
 *
 * <p><b>Sites</b> (obras) are what the crews are sent to. They come from the works of the plan
 * ({@link BuildPlan#workOf}, named by {@link BuildPlan#workName}) with these rules:
 * <ul>
 *   <li>a work with nothing for a builder (the plinth, the island) is no site;</li>
 *   <li>{@code module_<n>} works join the {@code lot_<m>} whose box holds their middle: a lot and its halls are one
 *   site;</li>
 *   <li>a {@code ring_<m>} work is cut into stretches between the nodes ({@code node_<k>}, {@code archive}), by the angle
 *   around the hub: each stretch is a site;</li>
 *   <li>scattered works (by name: {@code grounds}, gardens, lamps; or by shape: builder columns covering under
 *   {@link #SCATTER_SHARE} of a box of {@link #SCATTER_AREA} cells or more) are no site: their builder entries
 *   form the <b>details</b> pseudo-site ({@link #details}), worked last;</li>
 *   <li>every other work is a site as it is; a plan without works is one site.</li>
 * </ul>
 * A site's <b>footprint</b> is the columns whose lowest builder entry is its own, plus the columns with only ground
 * whose surface belongs to it (a courtyard). {@link #siteAt} answers "which site is the player in" with it (and
 * {@link #MARGIN} blocks around), not with the box.
 *
 * <p><b>Bays</b> (tramos) cut a site's columns into tiles of {@link #BAY} × {@link #BAY} blocks (tiles under
 * {@link #BAY_MIN} columns join a neighbour): the piece one builder raises. The free ground within {@link #APRON}
 * blocks of a footprint joins the site's bays too (for its ground wave only), so a finished building never stands
 * over the void.
 *
 * <p><b>Storeys</b> (plantas) are the building's real floors where it has them: a height where at least
 * {@link #FLOOR_SHARE} of the site's builder columns (and {@link #FLOOR_MIN} cells) hold a walkable floor cell
 * ({@link WalkLevels}: two passable cells over it, inside a 2 × 2 of walkable cells) starts a storey. Where floors are
 * further apart than a lift and a bit (towers, tall halls, roofs), or there are none, the height is cut into lifts of
 * {@code build.siteStorey} blocks.
 *
 * <p><b>Stages</b> order a storey as a building site does: {@link #SLAB} (the floor layer of a real storey),
 * {@link #STRUCTURE} (opaque solids: walls, columns, roofs), {@link #ENCLOSURE} (glass, panes, fences, doors) and
 * {@link #FINISH} (what has no collision or gives light: torches, lanterns, carpets of flowers).
 *
 * <p><b>The program of a site</b> ({@link #compile}) is a list of <i>waves</i>, each a list of <i>tasks</i> (the
 * entries of one bay). The next wave starts only when the whole wave is done, so the site rises as one. A wave is
 * one <i>round</i> of one stage of one storey, and <b>support is resolved here, not while building</b>: an entry
 * goes into a round only if what holds it (the cell below; else a side cell; for decoration also the cell above;
 * falling blocks only the cell below) is ground, or was placed in an earlier wave, or earlier in the same task. So
 * the tasks of a wave never depend on each other and nothing a builder places floats. What finds no support in
 * its stage waits for the storey's {@link #LATE} rounds (a lintel over a window, a roof spanning bays), then for the
 * next storey's (a lantern hanging from the floor above); what never finds any (a truly floating piece, or one that
 * leans on a part of the details) is the site's <b>finishing pass</b> ({@link #REMATE}), placed last, bay by bay.
 * Inside a wave the bays are ordered by distance from the <b>access</b>: the column of the site nearest the player
 * when the site opened, frozen from then on. Walking inside the site changes nothing.
 *
 * <p>Other sites are taken as standing when support is looked up (a wall that leans on the building next door is
 * not held back); the details are not.
 */
final class SiteProgram {
	/** Side of a bay tile, blocks: a builder standing by its middle reaches all of it ({@link SiteCursor#REACH}). */
	static final int BAY = 6;
	/** A tile with fewer columns joins the neighbouring bay with the most. */
	static final int BAY_MIN = 5;
	/** The player counts as in a site when a column of its footprint is at most this many blocks away (each axis). */
	static final int MARGIN = 2;
	/** ...and it is between this far under the site's lowest and this far over its highest builder entry. */
	static final int Y_BELOW = 3;
	static final int Y_ABOVE = 4;
	/** A work is scattered when its builder columns cover under 1 / this of their box... */
	static final int SCATTER_SHARE = 25;
	/** ...and that box has at least this many cells (40 × 40). */
	static final int SCATTER_AREA = 1600;

	/** Most storeys of a site (what is higher belongs to the last one). */
	static final int MAX_STOREYS = 128;
	/** Free ground this near a footprint (blocks, each axis) is laid with that site's ground wave. */
	static final int APRON = 3;
	/** A height is a real floor of a site when this share (1 / n) of its builder columns hold a floor cell there... */
	static final int FLOOR_SHARE = 6;
	/** ...and at least this many cells do. */
	static final int FLOOR_MIN = 12;
	/** Real floors nearer than this to the one below do not start a storey. */
	static final int STOREY_MIN = 3;
	/** Rounds of one stage of one storey at most; what is left waits for the late rounds. */
	static final int MAX_ROUNDS = 6;
	/**
	 * Passes over one bay inside a round (side chains inside the bay). #48: was 4, which cut a slab or a roof that grows
	 * against the sweep after four blocks and sent the rest to later rounds (a speckled surface); a pass that adds nothing
	 * ends the loop, so this is only a bound.
	 */
	static final int MAX_PASSES = 64;

	/** Stages of a storey, in order; {@link #LATE} and {@link #REMATE} exist only in a compiled program. */
	static final int SLAB = 0;
	static final int STRUCTURE = 1;
	static final int ENCLOSURE = 2;
	static final int FINISH = 3;
	static final int LATE = 4;
	static final int REMATE = 5;
	static final String[] STAGE_NAMES = {"slab", "structure", "enclosure", "finishes", "late", "finishing"};

	/** Entry kinds. */
	static final byte FILL = 0;
	static final byte GROUND = 1;
	static final byte CLEAR = 2;
	static final byte BUILD = 3;

	/** Site kinds: a building (or any compact work), a stretch of a ring, the details pass. */
	static final int BUILDING = 0;
	static final int STRETCH = 1;
	static final int DETAILS = 2;

	private static final String[] SCATTER_NAMES = {"grounds", "garden", "lamp", "lantern", "landscap", "tree", "decor", "detail", "scatter", "plinth", "island"};

	// ---- entries ------------------------------------------------------------------------------
	final int n;
	final int[] x;
	final int[] y;
	final int[] z;
	final boolean[] air;
	final boolean[] decor;
	final boolean[] falling;
	final byte[] kind;
	final Long2IntOpenHashMap cellAt;

	// ---- columns ------------------------------------------------------------------------------
	final Long2IntOpenHashMap colAt;
	final int cols;
	final int[] colX;
	final int[] colZ;
	final int[] colStart;
	/** Entries of each column, bottom-up: {@code colEntries[colStart[c] … colStart[c + 1])}. */
	final int[] colEntries;
	final int[] colOf;
	/** Fill entries of column {@code c}: the first {@code colFill[c]} of its entries. */
	final int[] colFill;
	/** Terrain stack of column {@code c} (fill, then ground): its first {@code colStack[c]} entries. */
	final int[] colStack;
	/** Air entries of each column, bottom-up: {@code airEntries[airStart[c] … airStart[c + 1])}. */
	final int[] airStart;
	final int[] airEntries;
	/** Walkable candidates of each column (as {@link RadialCursor}): {@code candK[candStart[c] … candStart[c + 1])}, indices into the column. */
	final int[] candStart;
	final int[] candK;
	final boolean[] candSup;

	// ---- sites --------------------------------------------------------------------------------
	final int sites;
	final String[] siteName;
	final int[] siteKind;
	/** {@code {minX, minZ, maxX, maxZ, minY, maxY}}: footprint and builder columns; heights of its builder entries. */
	final int[][] siteBox;
	/** Builder entries of each site. */
	final int[] siteTotal;
	/** Footprint columns of each site. */
	final int[] siteCols;
	/** Site of each {@link #BUILD} entry; -1 for the others. */
	final int[] siteOf;
	/** The site whose footprint holds column {@code c}; -1 = free ground (between sites). Never {@link #details}. */
	final int[] footSite;
	/** Index of the details pseudo-site (the last one), or -1 when the plan has none. */
	final int details;

	// ---- bays ---------------------------------------------------------------------------------
	final int bays;
	final int[] baySite;
	/** Bays of site {@code s}: {@code siteBayStart[s] … siteBayStart[s + 1]}. */
	final int[] siteBayStart;
	/** Columns of bay {@code b}: {@code bayCols[bayColStart[b] … bayColStart[b + 1])}. */
	final int[] bayColStart;
	final int[] bayCols;
	final double[] bayX;
	final double[] bayZ;
	/** Bay of each {@link #BUILD} entry; -1 for the others. */
	final int[] bayOf;
	/** Bays that hold column {@code c} (one per site with something there): {@code colBays[colBayStart[c] … colBayStart[c + 1])}. */
	final int[] colBayStart;
	final int[] colBays;

	// ---- storeys and the entries of each site -------------------------------------------------
	/** Base height of every storey of site {@code s}, ascending. */
	final int[][] storeyY;
	/** Whether storey {@code k} of site {@code s} starts at a real floor of the building (else it is a fixed lift). */
	final boolean[][] storeyReal;
	/** Storey of each {@link #BUILD} entry inside its site; 0 for the others. */
	final int[] storeyOf;
	/** Stage of each {@link #BUILD} entry ({@link #SLAB} … {@link #FINISH}); 0 for the others. */
	final byte[] stageOf;
	/** Free-ground column that joined a site's bays as its apron (never part of a footprint). */
	final boolean[] apronCol;
	/** Bounds of the plan's columns. */
	final int loX;
	final int hiX;
	final int loZ;
	final int hiZ;
	/** Builder entries of site {@code s}, by storey, stage, bay, height: {@code siteEnts[siteEntStart[s] … siteEntStart[s + 1])}. */
	final int[] siteEntStart;
	final int[] siteEnts;
	/** Position of each {@link #BUILD} entry inside its site's slice of {@link #siteEnts}; -1 for the others. */
	final int[] localOf;
	/** Where the build centres while no player is in it (plan-relative {@code {x, z}}). */
	final int[] fallback;

	/**
	 * The order inside one site, compiled for one access ({@link #compile}).
	 *
	 * @param bayOrder   the site's bays, nearest the access first: the order of its ground wave
	 * @param waveStart  tasks of wave {@code w}: {@code waveStart[w] … waveStart[w + 1]}
	 * @param waveStorey storey of each wave
	 * @param waveStage  stage of each wave ({@link #SLAB} … {@link #REMATE})
	 * @param remateWave the first wave of the finishing pass ({@code waves()} when there is none)
	 * @param taskStart  entries of task {@code t}: {@code entries[taskStart[t] … taskStart[t + 1])}
	 * @param taskBay    bay of each task
	 */
	record Program(int site, int accessX, int accessZ, int[] bayOrder, int[] waveStart, int[] waveStorey, int[] waveStage, int remateWave, int[] taskStart,
			int[] taskBay, int[] entries) {
		int waves() {
			return waveStorey.length;
		}

		int tasks() {
			return taskBay.length;
		}
	}

	/** Analyses {@code plan}: off-thread ({@link DeferredCursor}). {@code focus}: plan-relative {@code {x, z}} to centre on while nobody is in the build, or null. */
	static SiteProgram of(BuildPlan plan, int storey, int @Nullable [] focus) {
		return new SiteProgram(plan, Foundation.of(plan), Math.max(1, storey), focus);
	}

	private SiteProgram(BuildPlan plan, Foundation.Split split, int lift, int @Nullable [] focus) {
		n = plan.size();
		x = new int[n];
		y = new int[n];
		z = new int[n];
		air = new boolean[n];
		decor = new boolean[n];
		falling = new boolean[n];
		kind = new byte[n];
		cellAt = new Long2IntOpenHashMap(Math.max(16, n));
		cellAt.defaultReturnValue(-1);
		colAt = new Long2IntOpenHashMap();
		colAt.defaultReturnValue(-1);
		colOf = new int[n];
		int[] colCount = new int[Math.max(16, n / 4)];
		int[] cxs = new int[colCount.length];
		int[] czs = new int[colCount.length];
		int nc = 0;
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		WalkLevels.Shapes shapes = new WalkLevels.Shapes();
		boolean[] stand = new boolean[n];
		boolean[] pass = new boolean[n];
		boolean[] full = new boolean[n];
		boolean[] finish = new boolean[n];
		for (int i = 0; i < n; i++) {
			Placement pl = plan.get(i);
			x[i] = pl.pos().getX();
			y[i] = pl.pos().getY();
			z[i] = pl.pos().getZ();
			air[i] = pl.state().isAir();
			decor[i] = !air[i] && !pl.state().canOcclude();
			falling[i] = pl.state().getBlock() instanceof FallingBlock;
			stand[i] = !air[i] && shapes.standable(pl.state());
			pass[i] = shapes.passable(pl.state());
			full[i] = stand[i] && shapes.full(pl.state());
			// finishes: what a body walks through, or what gives light (torches, lanterns, flowers, signs)
			finish[i] = decor[i] && (!stand[i] || pl.state().getLightEmission() > 0);
			cellAt.put(CrewPlanner.key(x[i], y[i], z[i]), i); // a later entry at the same cell wins (as in plan order)
			long ck = CrewPlanner.key(x[i], 0, z[i]);
			int c = colAt.get(ck);
			if (c < 0) {
				c = nc++;
				if (c >= colCount.length) {
					int len = colCount.length * 2;
					colCount = Arrays.copyOf(colCount, len);
					cxs = Arrays.copyOf(cxs, len);
					czs = Arrays.copyOf(czs, len);
				}
				colAt.put(ck, c);
				cxs[c] = x[i];
				czs[c] = z[i];
			}
			colOf[i] = c;
			colCount[c]++;
			minX = Math.min(minX, x[i]);
			maxX = Math.max(maxX, x[i]);
			minZ = Math.min(minZ, z[i]);
			maxZ = Math.max(maxZ, z[i]);
		}
		cols = nc;
		loX = n == 0 ? 0 : minX;
		hiX = n == 0 ? 0 : maxX;
		loZ = n == 0 ? 0 : minZ;
		hiZ = n == 0 ? 0 : maxZ;
		colX = Arrays.copyOf(cxs, nc);
		colZ = Arrays.copyOf(czs, nc);
		colStart = new int[nc + 1];
		for (int c = 0; c < nc; c++) {
			colStart[c + 1] = colStart[c] + colCount[c];
		}
		colEntries = new int[n];
		int[] put = Arrays.copyOf(colStart, nc);
		for (int i = 0; i < n; i++) {
			colEntries[put[colOf[i]]++] = i;
		}
		// bottom-up inside each column (stable: plan order for the same height)
		long[] tmp = new long[0];
		for (int c = 0; c < nc; c++) {
			int a = colStart[c];
			int b = colStart[c + 1];
			if (b - a < 2) {
				continue;
			}
			if (tmp.length < b - a) {
				tmp = new long[Math.max(b - a, tmp.length * 2)];
			}
			for (int k = a; k < b; k++) {
				int i = colEntries[k];
				tmp[k - a] = ((long) (y[i] + (1 << 20)) << 32) | i;
			}
			Arrays.sort(tmp, 0, b - a);
			for (int k = a; k < b; k++) {
				colEntries[k] = (int) tmp[k - a];
			}
		}
		// kinds: the bottom terrain stack of a column is fill (hidden) and ground (its top two entries), air clears, the rest is built
		boolean[] terrain = new boolean[n];
		for (int i : split.foundation()) {
			terrain[i] = !air[i];
		}
		colFill = new int[nc];
		colStack = new int[nc];
		airStart = new int[nc + 1];
		IntArrayList airs = new IntArrayList();
		int[] stackT = new int[nc];
		for (int c = 0; c < nc; c++) {
			int a = colStart[c];
			int b = colStart[c + 1];
			int t = 0;
			while (a + t < b && terrain[colEntries[a + t]]) {
				t++;
			}
			stackT[c] = t;
		}
		// fix/builder-sites-stalls (#48): a paving block that is not a plain full cube (a slab, a stair, a path block) lying
		// right on a terrain stack, walkable, next to a column whose ground top is at its height, is ground too. Else a mixed
		// pavement (a plaza, a path) was half streams (its full cubes, laid at once) and half builders (laid much later):
		// a speckled floor for most of the build.
		boolean[] pave = new boolean[nc];
		for (int c = 0; c < nc; c++) {
			int a = colStart[c];
			int t = stackT[c];
			if (t == 0 || a + t >= colStart[c + 1]) {
				continue;
			}
			int i = colEntries[a + t];
			int top = colEntries[a + t - 1];
			if (air[i] || decor[i] || falling[i] || !stand[i] || y[i] != y[top] + 1 || !passAt(pass, x[i], y[i] + 1, z[i]) || !passAt(pass, x[i], y[i] + 2, z[i])) {
				continue;
			}
			for (int d = 0; d < 4 && !pave[c]; d++) {
				int c2 = colAt.get(CrewPlanner.key(colX[c] + (d == 0 ? 1 : d == 1 ? -1 : 0), 0, colZ[c] + (d == 2 ? 1 : d == 3 ? -1 : 0)));
				if (c2 >= 0 && stackT[c2] > 0 && y[colEntries[colStart[c2] + stackT[c2] - 1]] == y[i]) {
					pave[c] = true;
				}
			}
		}
		for (int c = 0; c < nc; c++) {
			int a = colStart[c];
			int b = colStart[c + 1];
			int t = stackT[c] + (pave[c] ? 1 : 0);
			colFill[c] = Math.max(0, stackT[c] - 2);
			colStack[c] = t;
			airStart[c] = airs.size();
			for (int k = a; k < b; k++) {
				int i = colEntries[k];
				if (k - a < colFill[c]) {
					kind[i] = FILL;
				} else if (k - a < t) {
					kind[i] = GROUND;
				} else if (air[i]) {
					kind[i] = CLEAR;
					airs.add(i);
				} else {
					kind[i] = BUILD;
				}
			}
		}
		airStart[nc] = airs.size();
		airEntries = airs.toIntArray();
		// walkable candidates per column (floors with two passable cells over them, and the terrain top when it is walkable)
		boolean[] walk = new boolean[n];
		for (int i = 0; i < n; i++) {
			walk[i] = stand[i] && cellAt.get(CrewPlanner.key(x[i], y[i], z[i])) == i && passAt(pass, x[i], y[i] + 1, z[i])
					&& passAt(pass, x[i], y[i] + 2, z[i]);
		}
		candStart = new int[nc + 1];
		IntArrayList ck = new IntArrayList();
		BooleanArrayList csup = new BooleanArrayList();
		for (int c = 0; c < nc; c++) {
			candStart[c] = ck.size();
			int a = colStart[c];
			int len = colStart[c + 1] - a;
			int t = colStack[c];
			for (int kk = colFill[c]; kk < len; kk++) {
				int i = colEntries[a + kk];
				if (!walk[i]) {
					continue;
				}
				boolean groundTop = kk == t - 1;
				if (!groundTop && (kk < t || !floor(walk, i))) {
					continue; // the ground's support layer, or a lone walkable cell (a wall top, a fence line)
				}
				boolean sup = false;
				if (!groundTop && kk >= 1) {
					int e = colEntries[a + kk - 1];
					sup = y[e] == y[i] - 1 && stand[e] && (!full[i] || edge(stand, i));
				}
				ck.add(kk);
				csup.add(sup);
			}
		}
		candStart[nc] = ck.size();
		candK = ck.toIntArray();
		candSup = csup.toBooleanArray();

		// ---- sites: works grouped by the rules of the class comment --------------------------------
		int works = plan.works();
		int nw = Math.max(1, works);
		String[] wname = new String[nw];
		int[][] wbox = new int[nw][];
		int[] group = new int[nw];
		for (int w = 0; w < nw; w++) {
			wname[w] = works == 0 ? null : plan.workName(w);
			wbox[w] = new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
			group[w] = w;
		}
		int[] workOfEntry = new int[n];
		for (int i = 0; i < n; i++) {
			int w = works == 0 ? 0 : plan.workOf(i);
			workOfEntry[i] = w;
			if (w >= 0) {
				int[] wb = wbox[w];
				wb[0] = Math.min(wb[0], x[i]);
				wb[1] = Math.min(wb[1], z[i]);
				wb[2] = Math.max(wb[2], x[i]);
				wb[3] = Math.max(wb[3], z[i]);
			}
		}
		// a lot and its halls are one site: a module joins the smallest lot whose box holds its middle
		for (int w = 0; w < nw; w++) {
			if (wname[w] == null || !wname[w].startsWith("module_") || wbox[w][0] > wbox[w][2]) {
				continue;
			}
			int mx = (wbox[w][0] + wbox[w][2]) / 2;
			int mz = (wbox[w][1] + wbox[w][3]) / 2;
			long bestArea = Long.MAX_VALUE;
			for (int l = 0; l < nw; l++) {
				int[] lb = wbox[l];
				if (wname[l] == null || !wname[l].startsWith("lot_") || lb[0] > lb[2] || mx < lb[0] || mx > lb[2] || mz < lb[1] || mz > lb[3]) {
					continue;
				}
				long area = (long) (lb[2] - lb[0] + 1) * (lb[3] - lb[1] + 1);
				if (area < bestArea) {
					bestArea = area;
					group[w] = l;
				}
			}
		}
		// rings are cut at the nodes, by the angle around the hub (else around the middle of the plan)
		double hubX = n == 0 ? 0 : (minX + maxX + 1) / 2.0;
		double hubZ = n == 0 ? 0 : (minZ + maxZ + 1) / 2.0;
		for (int w = 0; w < nw; w++) {
			if ("hub".equals(wname[w]) && wbox[w][0] <= wbox[w][2]) {
				hubX = (wbox[w][0] + wbox[w][2] + 1) / 2.0;
				hubZ = (wbox[w][1] + wbox[w][3] + 1) / 2.0;
			}
		}
		double[] cuts = cuts(wname, wbox, hubX, hubZ);
		boolean[] ring = new boolean[nw];
		boolean[] scatter = new boolean[nw];
		int[] base = new int[nw]; // first candidate site of a group (a ring has one per stretch)
		int ncand = 0;
		for (int w = 0; w < nw; w++) {
			if (group[w] != w) {
				continue;
			}
			String nm = wname[w];
			ring[w] = nm != null && nm.startsWith("ring_");
			scatter[w] = scatteredName(nm);
			base[w] = ncand;
			ncand += ring[w] ? cuts.length : 1;
		}
		// count what a builder places per candidate; a candidate with nothing is no site, a sparse one goes to the details
		int[] candBuild = new int[ncand];
		int[] candColumns = new int[ncand];
		int[] candLastCol = new int[ncand];
		Arrays.fill(candLastCol, -1);
		int[][] candBox = new int[ncand][];
		for (int q = 0; q < ncand; q++) {
			candBox[q] = new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		}
		int[] candOfEntry = new int[n]; // -1: none (not built by a builder), -2: details
		Arrays.fill(candOfEntry, -1);
		int detailEntries = 0;
		for (int c = 0; c < nc; c++) {
			int sector = cuts.length <= 1 ? 0 : sector(cuts, colX[c] + 0.5 - hubX, colZ[c] + 0.5 - hubZ);
			for (int k = colStart[c]; k < colStart[c + 1]; k++) {
				int i = colEntries[k];
				if (kind[i] != BUILD) {
					continue;
				}
				int w = workOfEntry[i];
				int g = w < 0 ? -1 : group[w];
				if (g < 0 || scatter[g]) {
					candOfEntry[i] = -2;
					detailEntries++;
					continue;
				}
				int q = base[g] + (ring[g] ? sector : 0);
				candOfEntry[i] = q;
				candBuild[q]++;
				if (candLastCol[q] != c) {
					candLastCol[q] = c;
					candColumns[q]++;
					int[] qb = candBox[q];
					qb[0] = Math.min(qb[0], colX[c]);
					qb[1] = Math.min(qb[1], colZ[c]);
					qb[2] = Math.max(qb[2], colX[c]);
					qb[3] = Math.max(qb[3], colZ[c]);
				}
			}
		}
		int[] candSite = new int[ncand]; // -1: no site, -2: details
		List<String> names = new ArrayList<>();
		IntArrayList kinds = new IntArrayList();
		for (int w = 0; w < nw; w++) {
			if (group[w] != w) {
				continue;
			}
			int stretches = ring[w] ? cuts.length : 1;
			for (int j = 0; j < stretches; j++) {
				int q = base[w] + j;
				if (candBuild[q] == 0) {
					candSite[q] = -1;
					continue;
				}
				int[] qb = candBox[q];
				long area = (long) (qb[2] - qb[0] + 1) * (qb[3] - qb[1] + 1);
				if (!ring[w] && area >= SCATTER_AREA && (long) candColumns[q] * SCATTER_SHARE < area) {
					candSite[q] = -2;
					detailEntries += candBuild[q];
					continue;
				}
				candSite[q] = names.size();
				String nm = wname[w] == null ? (works == 0 ? "plan" : "work_" + w) : wname[w];
				names.add(ring[w] ? nm + "/" + j : nm);
				kinds.add(ring[w] ? STRETCH : BUILDING);
			}
		}
		details = detailEntries > 0 ? names.size() : -1;
		if (details >= 0) {
			names.add("details");
			kinds.add(DETAILS);
		}
		sites = names.size();
		siteName = names.toArray(new String[0]);
		siteKind = kinds.toIntArray();
		siteOf = new int[n];
		Arrays.fill(siteOf, -1);
		siteTotal = new int[sites];
		siteBox = new int[sites][];
		for (int s = 0; s < sites; s++) {
			siteBox[s] = new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE};
		}
		for (int i = 0; i < n; i++) {
			if (kind[i] != BUILD) {
				continue;
			}
			int q = candOfEntry[i];
			int s = q == -2 ? details : candSite[q] == -2 ? details : candSite[q];
			siteOf[i] = s;
			siteTotal[s]++;
			int[] sb = siteBox[s];
			sb[0] = Math.min(sb[0], x[i]);
			sb[1] = Math.min(sb[1], z[i]);
			sb[2] = Math.max(sb[2], x[i]);
			sb[3] = Math.max(sb[3], z[i]);
			sb[4] = Math.min(sb[4], y[i]);
			sb[5] = Math.max(sb[5], y[i]);
		}
		// footprints: the site of a column's lowest builder entry, else the site its surface belongs to
		footSite = new int[nc];
		siteCols = new int[sites];
		for (int c = 0; c < nc; c++) {
			int fs = -1;
			for (int k = colStart[c]; k < colStart[c + 1] && fs < 0; k++) {
				int i = colEntries[k];
				if (kind[i] == BUILD && siteOf[i] != details) {
					fs = siteOf[i];
				}
			}
			if (fs < 0 && colStack[c] > 0) {
				int top = colEntries[colStart[c] + colStack[c] - 1];
				int w = workOfEntry[top];
				int g = w < 0 ? -1 : group[w];
				if (g >= 0 && !scatter[g]) {
					int q = base[g] + (ring[g] && cuts.length > 1 ? sector(cuts, colX[c] + 0.5 - hubX, colZ[c] + 0.5 - hubZ) : 0);
					fs = candSite[q] >= 0 ? candSite[q] : -1;
				}
			}
			footSite[c] = fs;
			if (fs >= 0) {
				siteCols[fs]++;
				int[] sb = siteBox[fs];
				sb[0] = Math.min(sb[0], colX[c]);
				sb[1] = Math.min(sb[1], colZ[c]);
				sb[2] = Math.max(sb[2], colX[c]);
				sb[3] = Math.max(sb[3], colZ[c]);
			}
		}

		// the apron: free ground within APRON blocks of a footprint is laid with that site's ground wave (a breadth-first
		// spread from the footprints, so a column goes to the nearest one)
		apronCol = new boolean[nc];
		int[] reach = footSite.clone();
		IntArrayList frontier = new IntArrayList();
		for (int c = 0; c < nc; c++) {
			if (footSite[c] >= 0) {
				frontier.add(c);
			}
		}
		for (int d = 0; d < APRON && !frontier.isEmpty(); d++) {
			IntArrayList next = new IntArrayList();
			for (int q = 0; q < frontier.size(); q++) {
				int c = frontier.getInt(q);
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						int nb = dx == 0 && dz == 0 ? -1 : colAt.get(CrewPlanner.key(colX[c] + dx, 0, colZ[c] + dz));
						if (nb < 0 || reach[nb] >= 0 || colStack[nb] <= colFill[nb] && airStart[nb + 1] == airStart[nb]) {
							continue; // no column, taken, or nothing there for a stream
						}
						reach[nb] = reach[c];
						apronCol[nb] = true;
						next.add(nb);
					}
				}
			}
			frontier = next;
		}

		// ---- bays: every (site, column) pair, tiled per site --------------------------------------
		LongArrayList pairList = new LongArrayList();
		for (int c = 0; c < nc; c++) {
			if (footSite[c] >= 0 || apronCol[c]) {
				pairList.add(((long) reach[c] << 32) | c);
			}
			int last = footSite[c];
			for (int k = colStart[c]; k < colStart[c + 1]; k++) {
				int i = colEntries[k];
				if (kind[i] == BUILD && siteOf[i] != last) {
					last = siteOf[i];
					pairList.add(((long) last << 32) | c);
				}
			}
		}
		long[] pairs = pairList.toLongArray();
		Arrays.sort(pairs);
		int m = 0;
		for (int k = 0; k < pairs.length; k++) {
			if (k == 0 || pairs[k] != pairs[k - 1]) {
				pairs[m++] = pairs[k];
			}
		}
		Long2IntOpenHashMap bayAt = new Long2IntOpenHashMap(Math.max(16, m));
		bayAt.defaultReturnValue(-1);
		IntArrayList bSite = new IntArrayList();
		IntArrayList bStart = new IntArrayList();
		IntArrayList bColumns = new IntArrayList();
		siteBayStart = new int[sites + 1];
		int from = 0;
		for (int s = 0; s < sites; s++) {
			siteBayStart[s] = bSite.size();
			int to = from;
			while (to < m && (int) (pairs[to] >>> 32) == s) {
				to++;
			}
			if (to > from) {
				tile(s, pairs, from, to, bSite, bStart, bColumns, bayAt);
			}
			from = to;
		}
		siteBayStart[sites] = bSite.size();
		bays = bSite.size();
		baySite = bSite.toIntArray();
		bStart.add(bColumns.size());
		bayColStart = bStart.toIntArray();
		bayCols = bColumns.toIntArray();
		bayX = new double[bays];
		bayZ = new double[bays];
		int[] perCol = new int[nc + 1];
		for (int b = 0; b < bays; b++) {
			double sx = 0;
			double sz = 0;
			for (int k = bayColStart[b]; k < bayColStart[b + 1]; k++) {
				int c = bayCols[k];
				sx += colX[c] + 0.5;
				sz += colZ[c] + 0.5;
				perCol[c + 1]++;
			}
			int cnt = Math.max(1, bayColStart[b + 1] - bayColStart[b]);
			bayX[b] = sx / cnt;
			bayZ[b] = sz / cnt;
		}
		for (int c = 0; c < nc; c++) {
			perCol[c + 1] += perCol[c];
		}
		colBayStart = perCol;
		colBays = new int[bayCols.length];
		int[] at = Arrays.copyOf(perCol, nc);
		for (int b = 0; b < bays; b++) {
			for (int k = bayColStart[b]; k < bayColStart[b + 1]; k++) {
				colBays[at[bayCols[k]]++] = b;
			}
		}
		bayOf = new int[n];
		Arrays.fill(bayOf, -1);
		for (int i = 0; i < n; i++) {
			if (kind[i] == BUILD) {
				bayOf[i] = bayAt.get(((long) siteOf[i] << 32) | colOf[i]);
			}
		}

		// ---- storeys: real floors where the site has them, fixed lifts elsewhere --------------------
		int[] buildCols = new int[sites];
		for (int c = 0; c < nc; c++) {
			int last = -1;
			for (int k = colStart[c]; k < colStart[c + 1]; k++) {
				int i = colEntries[k];
				if (kind[i] == BUILD && siteOf[i] != last) {
					last = siteOf[i];
					buildCols[last]++;
				}
			}
		}
		int[][] hist = new int[sites][];
		for (int s = 0; s < sites; s++) {
			hist[s] = siteTotal[s] == 0 || s == details ? new int[0] : new int[siteBox[s][5] - siteBox[s][4] + 1];
		}
		for (int i = 0; i < n; i++) {
			if (kind[i] == BUILD && siteOf[i] != details && walk[i] && floor(walk, i)) {
				hist[siteOf[i]][y[i] - siteBox[siteOf[i]][4]]++;
			}
		}
		storeyY = new int[sites][];
		storeyReal = new boolean[sites][];
		for (int s = 0; s < sites; s++) {
			int lo = siteBox[s][4];
			int hi = siteBox[s][5];
			if (siteTotal[s] == 0 || s == details) {
				storeyY[s] = new int[] {siteTotal[s] == 0 ? 0 : lo}; // the details are one pass, bay by bay
				storeyReal[s] = new boolean[1];
				continue;
			}
			int need = Math.max(FLOOR_MIN, buildCols[s] / FLOOR_SHARE);
			IntArrayList bases = new IntArrayList();
			BooleanArrayList real = new BooleanArrayList();
			bases.add(lo);
			real.add(hist[s][0] >= need);
			int lastBase = lo;
			for (int yy = lo + 1; yy <= hi + 1 && bases.size() < MAX_STOREYS; yy++) {
				boolean floorHere = yy <= hi && hist[s][yy - lo] >= need && yy - lastBase >= STOREY_MIN;
				if (!floorHere && yy <= hi) {
					continue;
				}
				// lifts between the base below and this floor (or the top), while at least a small storey is left over them
				for (int cut = lastBase + lift; yy - cut >= STOREY_MIN && bases.size() < MAX_STOREYS; cut += lift) {
					bases.add(cut);
					real.add(false);
				}
				if (floorHere && bases.size() < MAX_STOREYS) {
					bases.add(yy);
					real.add(true);
					lastBase = yy;
				}
			}
			storeyY[s] = bases.toIntArray();
			storeyReal[s] = real.toBooleanArray();
		}
		storeyOf = new int[n];
		stageOf = new byte[n];
		siteEntStart = new int[sites + 1];
		for (int s = 0; s < sites; s++) {
			siteEntStart[s + 1] = siteEntStart[s] + siteTotal[s];
		}
		siteEnts = new int[siteEntStart[sites]];
		localOf = new int[n];
		Arrays.fill(localOf, -1);
		long[][] keys = new long[sites][];
		int[] filled = new int[sites];
		for (int s = 0; s < sites; s++) {
			keys[s] = new long[siteTotal[s]];
		}
		for (int i = 0; i < n; i++) {
			if (kind[i] != BUILD) {
				continue;
			}
			int s = siteOf[i];
			int st = storeyIndex(storeyY[s], y[i]);
			storeyOf[i] = st;
			int stage = finish[i] ? FINISH : decor[i] ? ENCLOSURE : storeyReal[s][st] && y[i] == storeyY[s][st] ? SLAB : STRUCTURE;
			stageOf[i] = (byte) stage;
			long bayLocal = bayOf[i] - siteBayStart[s];
			// storey (7 bits) | stage (3) | bay inside the site (17) | height (12) | plan index (24)
			keys[s][filled[s]++] = ((long) (st & 0x7F) << 56) | ((long) stage << 53) | ((bayLocal & 0x1FFFF) << 36) | ((long) ((y[i] + 2048) & 0xFFF) << 24)
					| (i & 0xFFFFFF);
		}
		for (int s = 0; s < sites; s++) {
			Arrays.sort(keys[s]);
			int a = siteEntStart[s];
			for (int k = 0; k < keys[s].length; k++) {
				int i = (int) (keys[s][k] & 0xFFFFFF);
				siteEnts[a + k] = i;
				localOf[i] = k;
			}
		}
		// where the build centres while no player is in it: the given focus, else the origin when it is inside the footprint, else the middle
		if (focus != null) {
			fallback = new int[] {focus[0], focus[1]};
		} else if (n > 0 && minX <= 0 && 0 <= maxX && minZ <= 0 && 0 <= maxZ) {
			fallback = new int[] {0, 0};
		} else {
			fallback = new int[] {n == 0 ? 0 : (minX + maxX) / 2, n == 0 ? 0 : (minZ + maxZ) / 2};
		}
	}

	// ---- constructor helpers ------------------------------------------------------------------

	/** The final plan cell at {@code (bx, by, bz)} lets a body through (absent, air, no collision). */
	private boolean passAt(boolean[] pass, int bx, int by, int bz) {
		int j = cellAt.get(CrewPlanner.key(bx, by, bz));
		return j < 0 || pass[j];
	}

	private boolean walkAt(boolean[] walk, int bx, int by, int bz) {
		int j = cellAt.get(CrewPlanner.key(bx, by, bz));
		return j >= 0 && walk[j];
	}

	/** Walkable cell {@code i} lies in a 2 × 2 square of walkable cells at its height (a floor, not a wall top). */
	private boolean floor(boolean[] walk, int i) {
		for (int dx = -1; dx <= 1; dx += 2) {
			for (int dz = -1; dz <= 1; dz += 2) {
				if (walkAt(walk, x[i] + dx, y[i], z[i]) && walkAt(walk, x[i], y[i], z[i] + dz) && walkAt(walk, x[i] + dx, y[i], z[i] + dz)) {
					return true;
				}
			}
		}
		return false;
	}

	/** A side cell of {@code i} at its height holds nothing to stand on (the floor's edge). */
	private boolean edge(boolean[] stand, int i) {
		for (int d = 0; d < 4; d++) {
			int j = cellAt.get(CrewPlanner.key(x[i] + (d == 0 ? 1 : d == 1 ? -1 : 0), y[i], z[i] + (d == 2 ? 1 : d == 3 ? -1 : 0)));
			if (j < 0 || !stand[j]) {
				return true;
			}
		}
		return false;
	}

	private static boolean scatteredName(@Nullable String name) {
		if (name == null) {
			return false;
		}
		for (String s : SCATTER_NAMES) {
			if (name.startsWith(s)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Where the rings are cut: the angles (radians, ascending) of the middles of the node works around the hub; with
	 * fewer than two nodes, the four diagonals. A ring column belongs to stretch {@link #sector}.
	 */
	private static double[] cuts(String[] wname, int[][] wbox, double hubX, double hubZ) {
		double[] out = new double[wname.length];
		int k = 0;
		for (int w = 0; w < wname.length; w++) {
			String nm = wname[w];
			if (nm == null || wbox[w][0] > wbox[w][2] || !(nm.startsWith("node_") || nm.equals("archive"))) {
				continue;
			}
			double dx = (wbox[w][0] + wbox[w][2] + 1) / 2.0 - hubX;
			double dz = (wbox[w][1] + wbox[w][3] + 1) / 2.0 - hubZ;
			if (dx * dx + dz * dz < 4.0) {
				continue; // a node on the hub itself tells no direction
			}
			out[k++] = Math.atan2(dz, dx);
		}
		if (k < 2) {
			return new double[] {-0.75 * Math.PI, -0.25 * Math.PI, 0.25 * Math.PI, 0.75 * Math.PI};
		}
		double[] cut = Arrays.copyOf(out, k);
		Arrays.sort(cut);
		return cut;
	}

	/** Stretch of a ring column at offset {@code (dx, dz)} from the hub: how many cuts lie at or under its angle (the last stretch wraps around). */
	private static int sector(double[] cuts, double dx, double dz) {
		double a = Math.atan2(dz, dx);
		int s = 0;
		while (s < cuts.length && cuts[s] <= a) {
			s++;
		}
		return s == 0 ? cuts.length - 1 : s - 1;
	}

	/** Storey of height {@code yy} for the storey bases {@code bases} (ascending): the last base at or under it. */
	private static int storeyIndex(int[] bases, int yy) {
		int st = 0;
		while (st + 1 < bases.length && bases[st + 1] <= yy) {
			st++;
		}
		return st;
	}

	/**
	 * Cuts the columns of site {@code s} ({@code pairs[from … to)}, low 32 bits) into bays: tiles of {@link #BAY}
	 * blocks from the corner of their box; a tile with fewer than {@link #BAY_MIN} columns joins the neighbouring
	 * bay with the most. Appends the bays (site, column range) and records the bay of every pair in {@code bayAt}.
	 */
	private void tile(int s, long[] pairs, int from, int to, IntArrayList bSite, IntArrayList bStart, IntArrayList bColumns, Long2IntOpenHashMap bayAt) {
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (int k = from; k < to; k++) {
			int c = (int) pairs[k];
			minX = Math.min(minX, colX[c]);
			minZ = Math.min(minZ, colZ[c]);
			maxX = Math.max(maxX, colX[c]);
			maxZ = Math.max(maxZ, colZ[c]);
		}
		int tx = (maxX - minX) / BAY + 1;
		int tz = (maxZ - minZ) / BAY + 1;
		int[] count = new int[tx * tz];
		int[] tileOf = new int[to - from];
		for (int k = from; k < to; k++) {
			int c = (int) pairs[k];
			int t = (colX[c] - minX) / BAY + (colZ[c] - minZ) / BAY * tx;
			tileOf[k - from] = t;
			count[t]++;
		}
		int[] root = new int[tx * tz];
		for (int t = 0; t < root.length; t++) {
			root[t] = t;
		}
		for (int t = 0; t < root.length; t++) {
			if (count[t] == 0 || count[t] >= BAY_MIN || root[t] != t) {
				continue;
			}
			int best = -1;
			for (int d = 0; d < 4; d++) {
				int ux = t % tx + (d == 0 ? 1 : d == 1 ? -1 : 0);
				int uz = t / tx + (d == 2 ? 1 : d == 3 ? -1 : 0);
				if (ux < 0 || ux >= tx || uz < 0 || uz >= tz) {
					continue;
				}
				int r = ux + uz * tx;
				while (root[r] != r) {
					r = root[r];
				}
				if (r != t && count[r] > 0 && (best < 0 || count[r] > count[best])) {
					best = r;
				}
			}
			if (best >= 0) {
				root[t] = best;
				count[best] += count[t];
				count[t] = 0;
			}
		}
		int first = bSite.size();
		int[] bayOfTile = new int[root.length];
		Arrays.fill(bayOfTile, -1);
		for (int t = 0; t < root.length; t++) {
			if (root[t] == t && count[t] > 0) {
				bayOfTile[t] = bSite.size();
				bSite.add(s);
			}
		}
		int nb = bSite.size() - first;
		int[] bay = new int[to - from];
		int[] starts = new int[nb + 1];
		for (int k = 0; k < bay.length; k++) {
			int r = tileOf[k];
			while (root[r] != r) {
				r = root[r];
			}
			bay[k] = bayOfTile[r];
			starts[bay[k] - first + 1]++;
		}
		for (int j = 0; j < nb; j++) {
			starts[j + 1] += starts[j];
		}
		int[] out = new int[bay.length];
		int[] pos = Arrays.copyOf(starts, nb);
		for (int k = 0; k < bay.length; k++) {
			out[pos[bay[k] - first]++] = (int) pairs[from + k];
			bayAt.put(pairs[from + k], bay[k]);
		}
		int offset = bColumns.size();
		for (int j = 0; j < nb; j++) {
			bStart.add(offset + starts[j]);
		}
		for (int c : out) {
			bColumns.add(c);
		}
	}

	// ---- queries (any thread: the data never changes) -----------------------------------------

	/**
	 * The site the point is in: a column of its footprint, or one it builds in, within {@link #MARGIN} blocks (each
	 * axis), and the height between {@link #Y_BELOW} under and {@link #Y_ABOVE} over its builder entries ({@code py}
	 * NaN = no height test). The nearest column wins, then the smaller footprint (a room before the hall around it);
	 * -1 = none. The apron and the details never count.
	 */
	int siteAt(double px, double pz, double py) {
		int bx = (int) Math.floor(px);
		int bz = (int) Math.floor(pz);
		int best = -1;
		int bestD = Integer.MAX_VALUE;
		for (int dx = -MARGIN; dx <= MARGIN; dx++) {
			for (int dz = -MARGIN; dz <= MARGIN; dz++) {
				int c = colAt.get(CrewPlanner.key(bx + dx, 0, bz + dz));
				if (c < 0 || apronCol[c]) {
					continue;
				}
				int d = Math.max(Math.abs(dx), Math.abs(dz));
				// every site with something in this column (stacked sites: a basement under a hall), told apart by height
				for (int q = colBayStart[c]; q < colBayStart[c + 1]; q++) {
					int s = baySite[colBays[q]];
					int[] sb = siteBox[s];
					if (s == details || !Double.isNaN(py) && sb[4] <= sb[5] && (py < sb[4] - Y_BELOW || py > sb[5] + Y_ABOVE)) {
						continue;
					}
					if (d < bestD || d == bestD && best >= 0 && siteCols[s] < siteCols[best]) {
						best = s;
						bestD = d;
					}
				}
			}
		}
		return best;
	}

	/** Horizontal distance (blocks) from the point to the box of site {@code s}; 0 inside it. */
	double distance(int s, double px, double pz) {
		int[] sb = siteBox[s];
		if (sb[0] > sb[2]) {
			return Double.MAX_VALUE;
		}
		double dx = Math.max(0, Math.max(sb[0] - px, px - (sb[2] + 1)));
		double dz = Math.max(0, Math.max(sb[1] - pz, pz - (sb[3] + 1)));
		return Math.hypot(dx, dz);
	}

	/**
	 * The order inside site {@code s} for a crew that arrives from {@code (px, pz)}: pure, off-thread. The access is
	 * the column of the site nearest that point; the bays are ordered by distance from it; every storey goes stage
	 * by stage, every stage in rounds that only hold entries with support (see the class comment). Entries already
	 * placed ({@code doneLocal}, indexed like the site's slice of {@link #siteEnts}; null = none) are left out and
	 * count as support.
	 */
	Program compile(int s, double px, double pz, boolean @Nullable [] doneLocal) {
		return compile(s, px, pz, doneLocal, null);
	}

	/**
	 * Like {@link #compile(int, double, double, boolean[])}; {@code doneAll} (indexed by entry, null = unknown) tells
	 * which entries of <b>other</b> sites are placed: only those hold this site's entries (#48: a walkway or a wall that
	 * leans on a building not built yet no longer goes up early, floating; it waits for the late rounds and, at worst,
	 * the finishing pass).
	 */
	Program compile(int s, double px, double pz, boolean @Nullable [] doneLocal, boolean @Nullable [] doneAll) {
		int b0 = siteBayStart[s];
		int nb = siteBayStart[s + 1] - b0;
		int accessCol = -1;
		double bestD = Double.MAX_VALUE;
		for (int b = b0; b < b0 + nb; b++) {
			for (int k = bayColStart[b]; k < bayColStart[b + 1]; k++) {
				int c = bayCols[k];
				if (apronCol[c]) {
					continue; // the access is a column of the site itself
				}
				double dx = colX[c] + 0.5 - px;
				double dz = colZ[c] + 0.5 - pz;
				double d = dx * dx + dz * dz;
				if (d < bestD) {
					bestD = d;
					accessCol = c;
				}
			}
		}
		int ax = accessCol < 0 ? (int) Math.floor(px) : colX[accessCol];
		int az = accessCol < 0 ? (int) Math.floor(pz) : colZ[accessCol];
		// the wave: bays by distance from the access (quarter blocks), the bay index breaks ties
		long[] byDist = new long[nb];
		for (int j = 0; j < nb; j++) {
			double dx = bayX[b0 + j] - (ax + 0.5);
			double dz = bayZ[b0 + j] - (az + 0.5);
			long dq = (long) Math.min(1 << 28, Math.floor(Math.sqrt(dx * dx + dz * dz) * 4));
			byDist[j] = (dq << 32) | j;
		}
		Arrays.sort(byDist);
		int[] bayOrder = new int[nb];
		int[] rank = new int[nb];
		for (int j = 0; j < nb; j++) {
			bayOrder[j] = b0 + (int) byDist[j];
			rank[(int) byDist[j]] = j;
		}
		int a = siteEntStart[s];
		int e = siteEntStart[s + 1];
		boolean[] sim = new boolean[e - a];
		if (doneLocal != null) {
			System.arraycopy(doneLocal, 0, sim, 0, Math.min(sim.length, doneLocal.length));
		}
		Compiler cmp = new Compiler(s, rank, sim, doneAll, ax, az);
		IntArrayList late = new IntArrayList();
		int lastStorey = 0;
		int k = a;
		while (k < e) {
			// one storey: its entries are contiguous in siteEnts, stage by stage
			int st = storeyOf[siteEnts[k]];
			for (int stage = SLAB; stage <= FINISH; stage++) {
				IntArrayList cand = new IntArrayList();
				while (k < e && storeyOf[siteEnts[k]] == st && stageOf[siteEnts[k]] == stage) {
					if (!sim[k - a]) {
						cand.add(k - a);
					}
					k++;
				}
				if (!cand.isEmpty()) {
					IntArrayList left = cmp.rounds(cand, st, stage, false);
					for (int q = 0; q < left.size(); q++) {
						late.add(left.getInt(q));
					}
				}
			}
			if (!late.isEmpty()) {
				late = cmp.rounds(late, st, LATE, false); // what is still left waits for the next storey's late rounds
			}
			lastStorey = st;
		}
		int remateWave = cmp.waveStorey.size();
		if (!late.isEmpty()) {
			cmp.rounds(late, lastStorey, REMATE, true); // the finishing pass: placed whatever holds it
		}
		cmp.waveStart.add(cmp.taskBay.size());
		cmp.taskStart.add(cmp.entries.size());
		return new Program(s, ax, az, bayOrder, cmp.waveStart.toIntArray(), cmp.waveStorey.toIntArray(), cmp.waveStage.toIntArray(), remateWave,
				cmp.taskStart.toIntArray(), cmp.taskBay.toIntArray(), cmp.entries.toIntArray());
	}

	/** The working state of one {@link #compile}: what is placed in the simulation, and the waves and tasks so far. */
	private final class Compiler {
		final int site;
		final int bay0;
		final int ent0;
		/** Wave rank of each bay of the site (by distance from the access). */
		final int[] rank;
		/** Site-local: the entry is placed (before the compile, or in a wave already closed). */
		final boolean[] sim;
		/** Site-local: the task that took the entry (ids from 1), to find support inside the task being filled. */
		final int[] stamp;
		int task;
		final IntArrayList waveStart = new IntArrayList();
		final IntArrayList waveStorey = new IntArrayList();
		final IntArrayList waveStage = new IntArrayList();
		final IntArrayList taskStart = new IntArrayList();
		final IntArrayList taskBay = new IntArrayList();
		final IntArrayList entries = new IntArrayList();
		/** Entries of the whole plan already placed (other sites hold only where placed); null = all count as standing. */
		final boolean @Nullable [] doneAll;
		/** The access column: bays are swept from its side. */
		final int ax;
		final int az;

		Compiler(int site, int[] rank, boolean[] sim, boolean @Nullable [] doneAll, int ax, int az) {
			this.site = site;
			this.bay0 = siteBayStart[site];
			this.ent0 = siteEntStart[site];
			this.rank = rank;
			this.sim = sim;
			this.stamp = new int[sim.length];
			this.doneAll = doneAll;
			this.ax = ax;
			this.az = az;
		}

		/**
		 * The site-local entries {@code locals} ordered by the wave rank of their bay, then inside a bay bottom-up and,
		 * in each layer, a serpentine from the access's side: rows across the direction from the access to the bay,
		 * nearest row first, alternating direction (#48: the plan index used to decide inside a layer; cells a later
		 * part rewrote sit at the end of the plan, so a layer came out in a scattered order).
		 */
		int[] byBay(IntArrayList locals) {
			int m = locals.size();
			int[] kRank = new int[m];
			int[] kY = new int[m];
			int[] kRow = new int[m];
			int[] kCol = new int[m];
			for (int q = 0; q < m; q++) {
				int i = siteEnts[ent0 + locals.getInt(q)];
				int b = bayOf[i];
				kRank[q] = rank[b - bay0];
				kY[q] = y[i];
				double ddx = bayX[b] - (ax + 0.5);
				double ddz = bayZ[b] - (az + 0.5);
				boolean alongX = Math.abs(ddx) >= Math.abs(ddz);
				int sgn = (alongX ? ddx : ddz) < 0 ? -1 : 1;
				int row = alongX ? sgn * (x[i] - ax) : sgn * (z[i] - az);
				int col = alongX ? z[i] - az : x[i] - ax;
				kRow[q] = row;
				kCol[q] = (row & 1) == 0 ? col : -col;
			}
			int[] perm = Layers.sortByKeys(kRank, kY, kRow, kCol);
			int[] out = new int[m];
			for (int q = 0; q < m; q++) {
				out[q] = locals.getInt(perm[q]);
			}
			return out;
		}

		/** Whether the cell holds something that supports a neighbour now: ground, another site's block, or one of this site placed already (or in this task). */
		boolean solid(int bx, int by, int bz) {
			int j = cellAt.get(CrewPlanner.key(bx, by, bz));
			if (j < 0 || air[j]) {
				return false;
			}
			if (kind[j] != BUILD) {
				return true; // ground and fill: a bay waits for its ground before its builders start
			}
			int sj = siteOf[j];
			if (sj != site) {
				boolean[] da = doneAll;
				// other sites hold where they are placed (#48; unknown: taken as standing); the details come last
				return sj != details && (da == null || da[j]);
			}
			int l = localOf[j];
			return sim[l] || stamp[l] == task;
		}

		/** The support rule of {@link RadialCursor}, on the simulated state. */
		boolean supported(int i) {
			if (colEntries[colStart[colOf[i]]] == i) {
				return true; // the lowest entry of its column rests on the world
			}
			if (solid(x[i], y[i] - 1, z[i])) {
				return true;
			}
			if (falling[i]) {
				return false;
			}
			if (solid(x[i] + 1, y[i], z[i]) || solid(x[i] - 1, y[i], z[i]) || solid(x[i], y[i], z[i] + 1) || solid(x[i], y[i], z[i] - 1)) {
				return true;
			}
			if (decor[i]) {
				return solid(x[i], y[i] + 1, z[i]);
			}
			// #48: a stair or slab roof rises row by row and its rows only touch diagonally: the block one down and one
			// aside holds it too. Without this every row that did not reach a wall went to the late rounds or the
			// finishing pass, and the roof stood striped for most of the build.
			return solid(x[i] + 1, y[i] - 1, z[i]) || solid(x[i] - 1, y[i] - 1, z[i]) || solid(x[i], y[i] - 1, z[i] + 1) || solid(x[i], y[i] - 1, z[i] - 1);
		}

		/**
		 * Schedules {@code cand} (site-local entries of one storey and stage) in rounds: each round is a wave with one
		 * task per bay, holding the entries supported by what earlier waves placed or by the task itself;
		 * {@code force} takes everything in one round. Returns what found no support.
		 */
		IntArrayList rounds(IntArrayList cand, int storey, int stage, boolean force) {
			int[] cur = byBay(cand);
			int size = cur.length;
			for (int round = 0; round < MAX_ROUNDS && size > 0; round++) {
				int tasksBefore = taskBay.size();
				int entriesBefore = entries.size();
				int keep = 0;
				int pos = 0;
				while (pos < size) {
					int bay = bayOf[siteEnts[ent0 + cur[pos]]];
					int end = pos;
					while (end < size && bayOf[siteEnts[ent0 + cur[end]]] == bay) {
						end++;
					}
					task++;
					int start = entries.size();
					boolean grew = true;
					for (int pass = 0; pass < MAX_PASSES && grew; pass++) {
						grew = false;
						for (int q = pos; q < end; q++) {
							int l = cur[q];
							if (stamp[l] == task) {
								continue;
							}
							int i = siteEnts[ent0 + l];
							if (force || supported(i)) {
								stamp[l] = task;
								entries.add(i);
								grew = true;
							}
						}
					}
					if (entries.size() > start) {
						taskStart.add(start);
						taskBay.add(bay);
					}
					for (int q = pos; q < end; q++) {
						if (stamp[cur[q]] != task) {
							cur[keep++] = cur[q]; // left for the next round (keep never passes q)
						}
					}
					pos = end;
				}
				if (taskBay.size() == tasksBefore) {
					break; // nothing found support this round: more rounds would not either
				}
				for (int q = entriesBefore; q < entries.size(); q++) {
					sim[localOf[entries.getInt(q)]] = true;
				}
				waveStart.add(tasksBefore);
				waveStorey.add(storey);
				waveStage.add(stage);
				size = keep;
			}
			IntArrayList left = new IntArrayList(size);
			for (int q = 0; q < size; q++) {
				left.add(cur[q]);
			}
			return left;
		}
	}
}
