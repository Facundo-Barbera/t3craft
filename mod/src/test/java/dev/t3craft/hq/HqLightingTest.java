package dev.t3craft.hq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.hq.layout.HqLayoutInfo;
import dev.t3craft.hq.layout.HqLayouts;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * fix/hq-lanterns: lighting and decoration of every layout ({@link HqDecorValidator}): no floating,
 * obstructing or excessive decoration, and no walkable cell under {@link HqLightMap#MIN_LEVEL} of block
 * light at night, for the six layouts (cherry) and the classic style with 4, 10 and 20 projects; plus
 * negative cases that prove the validator catches each problem.
 */
class HqLightingTest {
	@BeforeAll
	static void boot() {
		HqTestSupport.boot();
	}

	static Stream<Arguments> cases() {
		List<Arguments> out = new ArrayList<>();
		for (HqLayoutInfo i : HqLayouts.all()) {
			for (int n : new int[] {4, 10, 20}) {
				out.add(Arguments.of(i.id(), n));
			}
		}
		for (int n : new int[] {4, 10, 20}) {
			out.add(Arguments.of("classic-style", n));
		}
		return out.stream();
	}

	private static HqStyle style(String id) {
		return id.equals("classic-style") ? HqStyles.byId(ClassicStyle.ID) : HqStyles.forLayout(id);
	}

	private static BuildPlan plan(String id, int n) {
		return HqGenerator.plan(HqLayoutsPlanTest.roster(n), null, style(id)).plan();
	}

	@ParameterizedTest(name = "{0} with {1} projects")
	@MethodSource("cases")
	void litAndTidy(String id, int n) {
		HqDecorValidator.Report r = HqDecorValidator.check(plan(id, n), style(id));
		assertTrue(r.issues().isEmpty(), id + " " + n + "p: " + r.summary() + "\n"
				+ r.issues().stream().limit(25).map(Object::toString).collect(Collectors.joining("\n")));
		assertTrue(r.walkable() > 500, id + ": the walk area was found");
	}

	// ------------------------------------------------------------------ negative cases

	private static BuildPlan base() {
		return plan(HqLayouts.CLASSIC, 4);
	}

	private static BuildPlan with(BuildPlan p, BlockPos at, BlockState s) {
		return p.replacing(q -> q.rel().equals(at) ? new Placement(at, s) : q);
	}

	@Test
	void catchesAFloatingLantern() {
		BuildPlan p = base();
		BlockPos free = null;
		for (Placement q : p.placements()) {
			BlockPos c = q.rel();
			if (q.state().isAir() && c.getY() >= 2 && isAir(p, c.above()) && isAir(p, c.below())) {
				free = c;
				break;
			}
		}
		assertTrue(free != null);
		BuildPlan bad = with(p, free, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
		HqDecorValidator.Report r = HqDecorValidator.check(bad, style(HqLayouts.CLASSIC));
		BlockPos at = free;
		assertTrue(r.of(HqDecorValidator.Kind.FLOATING).stream().anyMatch(i -> i.pos().equals(at)), r.summary());
		// standing on nothing floats too
		BuildPlan bad2 = with(p, free, Blocks.LANTERN.defaultBlockState());
		assertTrue(HqDecorValidator.check(bad2, style(HqLayouts.CLASSIC)).of(HqDecorValidator.Kind.FLOATING).stream()
				.anyMatch(i -> i.pos().equals(at)));
	}

	@Test
	void catchesALanternInTheWay() {
		BuildPlan p = base();
		BlockPos desk = p.workstations().get(0).deskAnchor().block();
		BuildPlan bad = with(p, desk, Blocks.LANTERN.defaultBlockState());
		HqDecorValidator.Report r = HqDecorValidator.check(bad, style(HqLayouts.CLASSIC));
		assertTrue(r.of(HqDecorValidator.Kind.OBSTRUCTS).stream().anyMatch(i -> i.pos().equals(desk)), r.summary());
		assertEquals(0, r.count(HqDecorValidator.Kind.FLOATING), "it stands on the desk floor: in the way, not floating");
	}

	@Test
	void catchesDarkCells() {
		BuildPlan p = base();
		// every light out: lamps let into floors and walls become stone, loose lanterns go
		BuildPlan unlit = p.replacing(q -> q.state().getLightEmission() == 0 ? q
				: new Placement(q.rel(), q.state().isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, q.rel())
						? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState()));
		HqDecorValidator.Report r = HqDecorValidator.check(unlit, style(HqLayouts.CLASSIC));
		assertTrue(r.count(HqDecorValidator.Kind.DARK) > r.walkable() / 2, "an unlit HQ is dark: " + r.summary());
		// and the lighting pass lights the very same plan again
		BuildPlan relit = HqLighting.light(unlit, style(HqLayouts.CLASSIC));
		assertEquals(0, HqDecorValidator.check(relit, style(HqLayouts.CLASSIC)).count(HqDecorValidator.Kind.DARK));
	}

	@Test
	void catchesTooManyLanterns() {
		BuildPlan p = base();
		Anchor hub = p.anchor(AnchorNames.HUB);
		BuildPlan bad = p;
		int put = 0;
		for (Placement q : p.placements()) {
			BlockPos c = q.rel();
			if (put < 6 && q.state().isAir() && c.getY() == hub.block().getY() && Math.abs(c.getX() - hub.block().getX()) <= 3
					&& Math.abs(c.getZ() - hub.block().getZ()) <= 3 && !isAir(p, c.below())) {
				bad = with(bad, c, Blocks.LANTERN.defaultBlockState());
				put++;
			}
		}
		assertEquals(6, put);
		HqDecorValidator.Report r = HqDecorValidator.check(bad, style(HqLayouts.CLASSIC));
		assertTrue(r.count(HqDecorValidator.Kind.EXCESS) > 0, r.summary());
	}

	@Test
	void lightMapFollowsTheGameRule() {
		BuildPlan p = base();
		HqLightMap m = HqLightMap.of(p);
		for (Placement q : p.placements()) {
			int e = q.state().getLightEmission();
			if (e > 0) {
				assertTrue(m.at(q.rel()) >= e, "a source holds at least its own light " + q);
				for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
					BlockPos n = q.rel().relative(d);
					assertTrue(m.at(n) <= 15, "never above 15");
				}
			}
		}
	}

	private static boolean isAir(BuildPlan p, BlockPos c) {
		Placement q = p.at(c);
		return q == null || q.state().isAir();
	}
}
