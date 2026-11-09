package dev.t3craft.hq.citadel.catalog;

import java.util.List;

/**
 * Every variant of the catalog, one line each.
 *
 * <p><b>Add your line in alphabetical order of the variant's id; do not reorder or reformat anything
 * else.</b> One line per variant, fully qualified class name (no imports to merge), trailing comma
 * (allowed in a Java array initializer). That way forty branches that each add one line merge as a
 * plain union of lines.
 */
final class CatalogEntries {
	private CatalogEntries() {
	}

	private static final Variant[] ENTRIES = {
			new dev.t3craft.hq.citadel.catalog.annex.AnnexCherryPergola(), // annex_cherry_pergola
			new dev.t3craft.hq.citadel.catalog.annex.AnnexGreenhouseCopper(), // annex_greenhouse_copper
			new dev.t3craft.hq.citadel.catalog.annex.AnnexToolShed(), // annex_tool_shed
			new dev.t3craft.hq.citadel.catalog.bannerpole.BannerPoleBuntingPair(), // banner_pole_bunting_pair
			new dev.t3craft.hq.citadel.catalog.bannerpole.BannerPoleTallStandard(), // banner_pole_tall_standard
			new dev.t3craft.hq.citadel.catalog.bench.BenchCherryPlanters(), // bench_cherry_planters
			new dev.t3craft.hq.citadel.catalog.bench.BenchStoneTable(), // bench_stone_table
			new dev.t3craft.hq.citadel.catalog.bridge.BridgeCoveredTimber(), // bridge_covered_timber
			new dev.t3craft.hq.citadel.catalog.bridge.BridgeStoneArch(), // bridge_stone_arch
			new dev.t3craft.hq.citadel.catalog.fountain.FountainKoiPond(), // fountain_koi_pond
			new dev.t3craft.hq.citadel.catalog.fountain.FountainMossyCascade(), // fountain_mossy_cascade
			new dev.t3craft.hq.citadel.catalog.fountain.FountainOctagonStatue(), // fountain_octagon_statue
			new dev.t3craft.hq.citadel.catalog.fountain.FountainStreamStones(), // fountain_stream_stones
			new dev.t3craft.hq.citadel.catalog.fountain.FountainTieredBasins(), // fountain_tiered_basins
			new dev.t3craft.hq.citadel.catalog.fountain.FountainWallMascaron(), // fountain_wall_mascaron
			new dev.t3craft.hq.citadel.catalog.garden.GardenFlowerRoseArch(), // garden_flower_rose_arch
			new dev.t3craft.hq.citadel.catalog.garden.GardenVegRaisedBeds(), // garden_veg_raised_beds
			new dev.t3craft.hq.citadel.catalog.garden.GardenZenKaresansui(), // garden_zen_karesansui
			new dev.t3craft.hq.citadel.catalog.gate.GateArchBattlement(), // gate_arch_battlement
			new dev.t3craft.hq.citadel.catalog.gate.GateToriiCherry(), // gate_torii_cherry
			new dev.t3craft.hq.citadel.catalog.gazebo.GazeboCherryOctagon(), // gazebo_cherry_octagon
			new dev.t3craft.hq.citadel.catalog.gazebo.GazeboRaisedLookout(), // gazebo_raised_lookout
			new dev.t3craft.hq.citadel.catalog.house.HouseForgeAtelier(), // house_forge_atelier
			new dev.t3craft.hq.citadel.catalog.house.HouseJapaneseEngawa(), // house_japanese_engawa
			new dev.t3craft.hq.citadel.catalog.house.HouseJapaneseTeahouse(), // house_japanese_teahouse
			new dev.t3craft.hq.citadel.catalog.house.HouseLibraryCopper(), // house_library_copper
			new dev.t3craft.hq.citadel.catalog.house.HouseMachiya(), // house_machiya
			new dev.t3craft.hq.citadel.catalog.house.HouseScriptorium(), // house_scriptorium
			new dev.t3craft.hq.citadel.catalog.house.HouseStoneManorCourt(), // house_stone_manor_court
			new dev.t3craft.hq.citadel.catalog.house.HouseStoneTower(), // house_stone_tower
			new dev.t3craft.hq.citadel.catalog.house.HouseStoneWeathered(), // house_stone_weathered
			new dev.t3craft.hq.citadel.catalog.house.HouseTerraceCascade(), // house_terrace_cascade
			new dev.t3craft.hq.citadel.catalog.house.HouseTerracePodium(), // house_terrace_podium
			new dev.t3craft.hq.citadel.catalog.house.HouseTerraceTiers(), // house_terrace_tiers
			new dev.t3craft.hq.citadel.catalog.house.HouseTimberArcade(), // house_timber_arcade
			new dev.t3craft.hq.citadel.catalog.house.HouseTimberCorner(), // house_timber_corner
			new dev.t3craft.hq.citadel.catalog.house.HouseTimberCottage(), // house_timber_cottage
			new dev.t3craft.hq.citadel.catalog.house.HouseTimberOverhang(), // house_timber_overhang
			new dev.t3craft.hq.citadel.catalog.house.HouseTowerBridge(), // house_tower_bridge
			new dev.t3craft.hq.citadel.catalog.house.HouseTowerLookout(), // house_tower_lookout
			new dev.t3craft.hq.citadel.catalog.house.HouseTowerStair(), // house_tower_stair
			new dev.t3craft.hq.citadel.catalog.lamp.LampCherryPost(), // lamp_cherry_post
			new dev.t3craft.hq.citadel.catalog.lamp.LampStreetAvenueDouble(), // lamp_street_avenue_double
			new dev.t3craft.hq.citadel.catalog.lamp.LampStreetBrazierTripod(), // lamp_street_brazier_tripod
			new dev.t3craft.hq.citadel.catalog.lamp.LampStreetToro(), // lamp_street_toro
			new dev.t3craft.hq.citadel.catalog.planter.PlanterStoneShrub(), // planter_stone_shrub
			new dev.t3craft.hq.citadel.catalog.planter.PlanterTallPot(), // planter_tall_pot
			new dev.t3craft.hq.citadel.catalog.plazacenter.PlazaCenterGlazedRosette(), // plaza_center_glazed_rosette
			new dev.t3craft.hq.citadel.catalog.plazacenter.PlazaCenterMonumentalSundial(), // plaza_center_monumental_sundial
			new dev.t3craft.hq.citadel.catalog.plazacenter.PlazaCenterOctagonBandstand(), // plaza_center_octagon_bandstand
			new dev.t3craft.hq.citadel.catalog.signpost.SignpostArrows(), // signpost_arrows
			new dev.t3craft.hq.citadel.catalog.signpost.SignpostNoticeBoard(), // signpost_notice_board
			new dev.t3craft.hq.citadel.catalog.stall.StallClothLanterns(), // stall_cloth_lanterns
			new dev.t3craft.hq.citadel.catalog.stall.StallFruitAwning(), // stall_fruit_awning
			new dev.t3craft.hq.citadel.catalog.stall.StallTeaBar(), // stall_tea_bar
			new dev.t3craft.hq.citadel.catalog.statue.StatueGuardianArmored(), // statue_guardian_armored
			new dev.t3craft.hq.citadel.catalog.statue.StatueMonumentalLantern(), // statue_monumental_lantern
			new dev.t3craft.hq.citadel.catalog.statue.StatueObeliskBraziers(), // statue_obelisk_braziers
			new dev.t3craft.hq.citadel.catalog.towertop.TowerTopCopperSpire(), // tower_top_copper_spire
			new dev.t3craft.hq.citadel.catalog.towertop.TowerTopLanternDome(), // tower_top_lantern_dome
			new dev.t3craft.hq.citadel.catalog.tree.TreeAzaleaBlossom(), // tree_azalea_blossom
			new dev.t3craft.hq.citadel.catalog.tree.TreeBirchGrove(), // tree_birch_grove
			new dev.t3craft.hq.citadel.catalog.tree.TreeCherryGrand(), // tree_cherry_grand
			new dev.t3craft.hq.citadel.catalog.tree.TreeCherryLeaning(), // tree_cherry_leaning
			new dev.t3craft.hq.citadel.catalog.tree.TreeCherryWeeping(), // tree_cherry_weeping
			new dev.t3craft.hq.citadel.catalog.tree.TreeCloudPinePot(), // tree_cloud_pine_pot
			new dev.t3craft.hq.citadel.catalog.tree.TreeMapleRed(), // tree_maple_red
			new dev.t3craft.hq.citadel.catalog.tree.TreePineTiered(), // tree_pine_tiered
			new dev.t3craft.hq.citadel.catalog.tree.TreeWillowPond(), // tree_willow_pond
			new dev.t3craft.hq.citadel.catalog.well.WellRoofedStone(), // well_roofed_stone
			new dev.t3craft.hq.citadel.catalog.well.WellTroughPump(), // well_trough_pump
	};

	static final List<Variant> ALL = List.of(ENTRIES);
}
