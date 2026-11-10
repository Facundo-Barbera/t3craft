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
 * column's bottom stack), {@link #GROUND} (the top two entries of that stack: the walkable surface and the layer
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
 * {@link #BAY_MIN} columns join a neighbour): the piece one builder raises. <b>Storeys</b> (plantas) cut its height
 * into lifts of {@code build.siteStorey} blocks from its lowest builder entry.
 *
 * <p><b>The program of a site</b> ({@link #compile}) is a list of <i>waves</i>, each a list of <i>tasks</i> (one bay's
 * entries, bottom-up, solids before decoration). A wave is one storey; the next starts only when the whole wave is
 * done, so the site rises as one. Inside a wave the bays are ordered by distance from the <b>access</b>: the
 * column of the site nearest the player when the site opened, frozen from then on. Walking inside the site changes
 * nothing.
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
	/** Storey of each {@link #BUILD} entry inside its site; 0 for the others. */
	final int[] storeyOf;
	/** Builder entries of site {@code s}, by storey, bay, height, solids before decoration: {@code siteEnts[siteEntStart[s] … siteEntStart[s + 1])}. */
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
	 * @param taskStart  entries of task {@code t}: {@code entries[taskStart[t] … taskStart[t + 1])}
	 * @param taskBay    bay of each task
	 */
	record Program(int site, int accessX, int accessZ, int[] bayOrder, int[] waveStart, int[] waveStorey, int[] taskStart, int[] taskBay, int[] entries) {
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
		for (int c = 0; c < nc; c++) {
			int a = colStart[c];
			int b = colStart[c + 1];
			int t = 0;
			while (a + t < b && terrain[colEntries[a + t]]) {
				t++;
			}
			colFill[c] = Math.max(0, t - 2);
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

		// ---- bays: every (site, column) pair, tiled per site --------------------------------------
		LongArrayList pairList = new LongArrayList();
		for (int c = 0; c < nc; c++) {
			if (footSite[c] >= 0) {
				pairList.add(((long) footSite[c] << 32) | c);
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

		// ---- storeys, and the builder entries of each site in program order -----------------------
		storeyY = new int[sites][];
		for (int s = 0; s < sites; s++) {
			int lo = siteBox[s][4];
			int hi = siteBox[s][5];
			if (siteTotal[s] == 0 || s == details) {
				storeyY[s] = new int[] {siteTotal[s] == 0 ? 0 : lo}; // the details are one pass, bay by bay
				continue;
			}
			int count = Math.min(MAX_STOREYS, (hi - lo) / lift + 1);
			storeyY[s] = new int[count];
			for (int k = 0; k < count; k++) {
				storeyY[s][k] = lo + k * lift;
			}
		}
		storeyOf = new int[n];
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
			long bayLocal = bayOf[i] - siteBayStart[s];
			// storey (7 bits) | bay inside the site (18) | height (12) | decoration last (1) | plan index (25)
			keys[s][filled[s]++] = ((long) (st & 0x7F) << 56) | ((bayLocal & 0x3FFFF) << 38) | ((long) ((y[i] + 2048) & 0xFFF) << 26) | ((decor[i] ? 1L : 0L) << 25)
					| (i & 0x1FFFFFF);
		}
		for (int s = 0; s < sites; s++) {
			Arrays.sort(keys[s]);
			int a = siteEntStart[s];
			for (int k = 0; k < keys[s].length; k++) {
				int i = (int) (keys[s][k] & 0x1FFFFFF);
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
	 * The site the point is in: a column of its footprint within {@link #MARGIN} blocks (each axis), and the height
	 * between {@link #Y_BELOW} under and {@link #Y_ABOVE} over its builder entries ({@code py} NaN = no height test).
	 * The nearest footprint wins, then the smaller one (a room before the hall around it); -1 = none.
	 */
	int siteAt(double px, double pz, double py) {
		int bx = (int) Math.floor(px);
		int bz = (int) Math.floor(pz);
		int best = -1;
		int bestD = Integer.MAX_VALUE;
		for (int dx = -MARGIN; dx <= MARGIN; dx++) {
			for (int dz = -MARGIN; dz <= MARGIN; dz++) {
				int c = colAt.get(CrewPlanner.key(bx + dx, 0, bz + dz));
				if (c < 0) {
					continue;
				}
				int s = footSite[c];
				if (s < 0) {
					continue;
				}
				int[] sb = siteBox[s];
				if (!Double.isNaN(py) && sb[4] <= sb[5] && (py < sb[4] - Y_BELOW || py > sb[5] + Y_ABOVE)) {
					continue;
				}
				int d = Math.max(Math.abs(dx), Math.abs(dz));
				if (d < bestD || d == bestD && best >= 0 && siteCols[s] < siteCols[best]) {
					best = s;
					bestD = d;
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
	 * the column of the site nearest that point; the bays are ordered by distance from it; every storey is one wave,
	 * one task per bay. Entries already placed ({@code doneLocal}, indexed like the site's slice of {@link #siteEnts};
	 * null = none) are left out.
	 */
	Program compile(int s, double px, double pz, boolean @Nullable [] doneLocal) {
		int b0 = siteBayStart[s];
		int nb = siteBayStart[s + 1] - b0;
		int accessCol = -1;
		double bestD = Double.MAX_VALUE;
		for (int b = b0; b < b0 + nb; b++) {
			for (int k = bayColStart[b]; k < bayColStart[b + 1]; k++) {
				int c = bayCols[k];
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
		for (int j = 0; j < nb; j++) {
			bayOrder[j] = b0 + (int) byDist[j];
		}
		int a = siteEntStart[s];
		int e = siteEntStart[s + 1];
		IntArrayList waveStart = new IntArrayList();
		IntArrayList waveStorey = new IntArrayList();
		IntArrayList taskStart = new IntArrayList();
		IntArrayList taskBay = new IntArrayList();
		IntArrayList entries = new IntArrayList(e - a);
		int[] runFrom = new int[nb];
		int[] runTo = new int[nb];
		int k = a;
		while (k < e) {
			// one storey: its entries are contiguous in siteEnts, bay by bay
			int storey = storeyOf[siteEnts[k]];
			Arrays.fill(runFrom, -1);
			int end = k;
			while (end < e && storeyOf[siteEnts[end]] == storey) {
				int j = bayOf[siteEnts[end]] - b0;
				if (runFrom[j] < 0) {
					runFrom[j] = end;
				}
				runTo[j] = end + 1;
				end++;
			}
			int tasksBefore = taskBay.size();
			for (int j = 0; j < nb; j++) {
				int bj = bayOrder[j] - b0;
				if (runFrom[bj] < 0) {
					continue;
				}
				int before = entries.size();
				for (int q = runFrom[bj]; q < runTo[bj]; q++) {
					if (doneLocal == null || !doneLocal[q - a]) {
						entries.add(siteEnts[q]);
					}
				}
				if (entries.size() > before) {
					taskStart.add(before);
					taskBay.add(bayOrder[j]);
				}
			}
			if (taskBay.size() > tasksBefore) {
				waveStart.add(tasksBefore);
				waveStorey.add(storey);
			}
			k = end;
		}
		waveStart.add(taskBay.size());
		taskStart.add(entries.size());
		return new Program(s, ax, az, bayOrder, waveStart.toIntArray(), waveStorey.toIntArray(), taskStart.toIntArray(), taskBay.toIntArray(),
				entries.toIntArray());
	}
}
