package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Only for the CI run (SKYBLOCKS_RENDER_TEST set): makes a flat world, builds the scene below (commands run as the
 * server), takes a screenshot from each spot and closes the game - so every build shows how the blocks really draw.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class RenderTest {
    static final boolean ON = System.getenv("SKYBLOCKS_RENDER_TEST") != null;
    private static final String TAG = System.getenv().getOrDefault("SKYBLOCKS_RENDER_TEST", "x");
    private static int stage = 0;
    /** The first spot to shoot (SKYBLOCKS_RENDER_FROM: to shoot just the newest; the shots keep their numbers). */
    private static final int FROM = Math.max(0, parseInt(System.getenv("SKYBLOCKS_RENDER_FROM")));

    private static int parseInt(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    private static int ticks = 0;

    private RenderTest() {}

    /** The scene: commands run once the world is there (the ground is at y -61, blocks stand at -60). */
    private static final String[] SCENE = {
            "setblock -6 -60 0 skyblocks:lava_lamp",
            "setblock -4 -60 0 skyblocks:fire_tornado",
            "setblock -2 -60 0 skyblocks:koi_pond",
            "setblock 0 -60 0 skyblocks:aquarium",
            "setblock 2 -60 0 skyblocks:jellyfish_tank",
            "setblock 4 -60 0 skyblocks:infinity_cube",
            "setblock 6 -60 0 skyblocks:dance_floor",
            // the new blocks, in a row of their own (closer up)
            "setblock -1 -60 4 skyblocks:plasma_ball",
            "setblock 1 -60 4 skyblocks:plasma_ball",
            "data merge block 1 -60 4 {colour: 3407871, rgb: 0b}",
            "fill -1 -60 8 0 -60 9 skyblocks:magma_basin",
            "setblock 2 -60 8 skyblocks:magma_basin",
            "data merge block 2 -60 8 {colour: 3381759, rgb: 0b}",
            "setblock -2 -60 12 skyblocks:black_hole",
            "setblock 2 -59 13 skyblocks:big_black_hole",
            "fill -4 -60 16 4 -57 16 minecraft:white_concrete",
            "fill -4 -60 15 4 -60 15 minecraft:red_concrete",
            "fill -4 -58 15 4 -58 15 minecraft:blue_concrete",
            // behind the big hole: a cat and one of Shadered's sky blocks (drawn in its own pass: they must still be bent)
            "setblock 2 -60 15 skyblocks:oiia_cat",
            "setblock 1 -59 15 shadered:space_block",
            // koi ponds (lilies open by day, closed at night; rain)
            "fill -6 -61 20 -3 -61 22 skyblocks:koi_pond",
            "fill 3 -61 20 6 -61 22 skyblocks:vanilla_koi_pond",
            // neon signs: the new letterings and glows (facing north, towards the camera)
            "fill -4 -58 26 4 -55 26 minecraft:black_concrete",
            "setblock -3 -57 25 skyblocks:neon_sign[facing=north]{text:\"Italic\",font:1,mode:8,colour:16728031,colour2:4176127,size:2}",
            "setblock 0 -57 25 skyblocks:neon_sign[facing=north]{text:\"BOLD\",font:2,mode:0,colour:4194047,size:2}",
            "setblock 3 -57 25 skyblocks:neon_sign[facing=north]{text:\"thin\",font:3,mode:7,colour:16760896,size:2}",
            "setblock -2 -56 25 skyblocks:neon_sign[facing=north]{text:\"WIDE\",font:4,mode:8,colour:65340,colour2:16711935,size:2}",
            "setblock 2 -56 25 skyblocks:neon_sign[facing=north]{text:\"NARROW\",font:5,mode:0,colour:16777215,size:2}",
            // hologram projectors: a player, a mob, an item
            "setblock -3 -60 31 skyblocks:hologram_projector[size=2]{mode:1,player:[I;1,2,3,4],playerName:\"Steve\"}",
            "setblock 0 -60 31 skyblocks:hologram_projector[size=2]{mode:2,mob:\"minecraft:fox\",colour:16752190}",
            "setblock 3 -60 31 skyblocks:hologram_projector[size=2]{mode:0,shown:{id:\"minecraft:diamond_sword\",count:1},colour:8453888}",
            // the themed aquariums: 3 x 2 x 2 tanks
            "fill -5 -60 36 -3 -59 37 skyblocks:reef_aquarium",
            "fill 2 -60 36 4 -59 37 skyblocks:jungle_aquarium",
            // the small tanks: fishbowls (three sets of fish), a tall tank 3 high, slim tanks on a wall (2 x 2 and 1)
            "setblock -4 -60 42 skyblocks:fishbowl",
            "setblock -3 -60 42 skyblocks:fishbowl{variant:1}",
            "setblock -2 -60 42 skyblocks:fishbowl{variant:2}",
            "fill 0 -60 43 0 -58 43 skyblocks:tall_tank",
            "fill 2 -60 44 5 -57 44 minecraft:stone_bricks",
            "fill 2 -59 43 3 -58 43 skyblocks:slim_tank[facing=north]",
            "setblock 5 -59 43 skyblocks:slim_tank[facing=north]{variant:1}",
            // the mirrors: a round one, a cracked wall of them, a vanity mirror 3 x 2 (facing the camera, north)
            "setblock -9 -59 51 minecraft:stone_bricks",
            "setblock -9 -59 50 skyblocks:round_mirror[facing=north]",
            "fill -7 -60 50 -6 -59 50 skyblocks:cracked_mirror",
            "fill -4 -60 50 -2 -59 50 skyblocks:vanity_mirror",
            "setblock -5 -60 46 minecraft:red_concrete",
            "setblock -8 -60 47 minecraft:blue_concrete",
            // wider tall tanks: 2 x 2 (a reef) and 3 x 3 (jellyfish)
            "fill 2 -60 56 3 -59 57 skyblocks:tall_tank{variant:1}",
            "fill -3 -60 56 -1 -59 58 skyblocks:tall_tank{variant:1}",
            // past the biggest a tank gets: a row 5 x 2 (two 2 x 2 tanks, two single columns) and a column 10 high (8 + 2)
            "fill 6 -60 56 10 -60 57 skyblocks:tall_tank", "setblock -2 -60 -4 skyblocks:kaleidoscope", "fill 36 -60 -44 44 -52 -36 skyblocks:containment_frame hollow", "fill 37 -59 -44 43 -53 -44 air", "fill 37 -59 -36 43 -53 -36 air", "fill 36 -59 -43 36 -53 -37 air", "fill 44 -59 -43 44 -53 -37 air", "fill 37 -52 -43 43 -52 -37 air", "setblock 40 -56 -40 skyblocks:singularity{items:[{item:{id:\"minecraft:diamond\",count:1},count:2000000L},{item:{id:\"minecraft:grass_block\",count:1},count:9000L}],colour:16744272}", "fill 18 -60 -27 22 -56 -23 skyblocks:containment_frame hollow", "fill 19 -59 -27 21 -57 -27 air", "fill 19 -59 -23 21 -57 -23 air", "fill 18 -59 -26 18 -57 -24 air", "fill 22 -59 -26 22 -57 -24 air", "fill 19 -60 -26 21 -60 -24 air", "fill 19 -56 -26 21 -56 -24 air", "setblock 20 -58 -25 skyblocks:singularity{items:[{item:{id:\"minecraft:diamond\",count:1},count:250000L},{item:{id:\"minecraft:gold_ingot\",count:1},count:9000L},{item:{id:\"minecraft:emerald\",count:1},count:420L},{item:{id:\"minecraft:redstone\",count:1},count:3000L}],colour:16733695}", "setblock 20 -56 -23 skyblocks:display_frame", "fill 60 -60 -25 64 -56 -21 skyblocks:tank_frame hollow", "fill 61 -59 -25 63 -57 -25 skyblocks:tank_glass", "fill 61 -59 -21 63 -57 -21 skyblocks:tank_glass", "fill 60 -59 -24 60 -57 -22 skyblocks:tank_glass", "fill 64 -59 -24 64 -57 -22 skyblocks:tank_glass", "fill 61 -60 -24 63 -60 -22 skyblocks:tank_glass", "fill 61 -56 -24 63 -56 -22 skyblocks:tank_glass", "fill 61 -59 -24 63 -57 -22 air", "setblock 62 -58 -23 skyblocks:whirlpool{fluids:[{fluid:{id:\"minecraft:lava\",amount:1},amount:3600000L},{fluid:{id:\"minecraft:water\",amount:1},amount:5600000L}]}", "fill 57 -61 -18 59 -61 -17 minecraft:water", "fill 70 -46 -12 75 -46 -10 minecraft:barrier", "fill 80 -46 -15 82 -46 -13 minecraft:barrier", "fill 68 -60 -27 76 -52 -19 skyblocks:tank_frame hollow", "fill 69 -59 -27 75 -53 -27 minecraft:glass", "fill 69 -59 -19 75 -53 -19 minecraft:glass", "fill 68 -59 -26 68 -53 -20 minecraft:glass", "fill 76 -59 -26 76 -53 -20 minecraft:glass", "fill 69 -60 -26 75 -60 -20 minecraft:glass", "fill 69 -52 -26 75 -52 -20 minecraft:glass", "fill 69 -59 -26 75 -53 -20 air", "setblock 72 -56 -23 skyblocks:whirlpool{fluids:[{fluid:{id:\"minecraft:water\",amount:1},amount:150000000L},{fluid:{id:\"minecraft:lava\",amount:1},amount:90000000L}]}", "setblock 57 -60 -14 skyblocks:whirlpool{still:1b,fluids:[{fluid:{id:\"minecraft:lava\",amount:1},amount:500000L},{fluid:{id:\"minecraft:water\",amount:1},amount:700000L}]}", "setblock 58 -60 -14 skyblocks:whirlpool{drain:1b,fluids:[{fluid:{id:\"minecraft:water\",amount:1},amount:480000L},{fluid:{id:\"minecraft:lava\",amount:1},amount:240000L}]}", "setblock 19 -60 -23 skyblocks:port_frame[output=true]{filter:{id:\"minecraft:diamond\",count:1}}", "setblock 19 -60 -22 minecraft:chest", "setblock 21 -60 -23 skyblocks:redstone_frame", "setblock 21 -60 -22 minecraft:redstone_lamp", "setblock 22 -58 -23 skyblocks:monitor_frame{filter:{id:\"minecraft:gold_ingot\",count:1}}", "fill -6 -60 -4 -5 -59 -4 skyblocks:kaleidoscope", "fill -3 -60 -18 4 -55 -18 minecraft:stone", "fill 0 -60 -15 1 -58 -15 skyblocks:portal_mirror[facing=south,color=lime]", "fill 30 -60 -15 31 -58 -15 skyblocks:portal_mirror[facing=north,color=lime]", "fill -12 -60 -15 -11 -58 -15 skyblocks:portal_mirror[facing=south,color=orange]", "fill 84 -61 -32 94 -61 -16 minecraft:red_concrete", "fill 88 -60 -15 89 -58 -15 skyblocks:portal_mirror[facing=north,color=orange]", "fill 86 -60 -24 91 -57 -24 minecraft:gold_block", "setblock 88 -60 -19 minecraft:jack_o_lantern[facing=south]", "fill 29 -60 -20 32 -57 -20 minecraft:gold_block", "setblock 30 -60 -18 minecraft:jack_o_lantern[facing=south]", "setblock 31 -60 -17 minecraft:diamond_block", "setblock -3 -60 -4 skyblocks:kaleidoscope{palette:3,symmetry:4}", "setblock 3 -60 -3 skyblocks:singularity{items:[{item:{id:\"minecraft:diamond\",count:1},count:250000L},{item:{id:\"minecraft:gold_ingot\",count:1},count:9000L},{item:{id:\"minecraft:emerald\",count:1},count:420L}],colour:3847130}", "summon item_frame 1 -59 -4 {Facing:2,Fixed:1b,Item:{id:\"shadered:space_block\",count:1}}", "setblock -30 -60 10 skyblocks:planetarium{size:1}", "fill -32 -60 13 -29 -58 13 minecraft:stone_bricks", "fill 12 -60 56 12 -51 56 skyblocks:tall_tank",
            // tesla coils (two near each other), and a galaxy projector in a closed stone room
            "setblock 10 -60 -4 skyblocks:tesla_coil",
            "setblock 13 -60 -4 skyblocks:tesla_coil",
            "setblock 8 -60 -3 minecraft:iron_block",
            "setblock 15 -60 -3 minecraft:anvil",
            "setblock 11 -58 -3 minecraft:copper_block",
            "fill 10 -61 2 18 -55 10 minecraft:stone_bricks hollow",
            "fill 10 -60 2 18 -60 10 minecraft:oak_planks",
            "setblock 14 -59 6 skyblocks:galaxy_projector",
            // the solar systems: a brass orrery, a hologram planetarium
            "setblock -12 -60 4 skyblocks:orrery",
            "setblock -12 -60 10 skyblocks:planetarium",
            // effect blocks: a joined wall (the swirl), another (plasma), a floor (ripples), and every effect on its own
            "fill 24 -60 24 29 -57 24 skyblocks:effect_block{group:11L,anchor:6597069868996L,settings:{effect:0,count:3,speed:100,size:100,brightness:100,pixel:1b}}", "fill 31 -60 24 36 -57 24 skyblocks:effect_block{group:12L,anchor:8521215217604L,settings:{effect:6,count:5,speed:100,size:100,brightness:100,pixel:0b,colours:[I;16724838,16763904,3407769,3381759,11154431,0,0,0]}}", "fill 24 -61 16 29 -61 21 skyblocks:effect_block{group:13L,anchor:6597069836227L,settings:{effect:8,count:4,speed:100,size:100,brightness:100,pixel:0b,colours:[I;58879,7995647,16711850,1052736,0,0,0,0]}}", "setblock 24 -60 36 skyblocks:effect_block{group:100L,anchor:6597069918148L,settings:{effect:0,count:4,speed:100,size:100,brightness:100,pixel:1b,colours:[I;16728128,4243711,16764992,6356864,0,0,0,0]}}", "setblock 25 -60 36 skyblocks:effect_block{group:101L,anchor:6871947825092L,settings:{effect:1,count:4,speed:100,size:100,brightness:100,pixel:0b,colours:[I;16728128,4243711,16764992,6356864,0,0,0,0]}}", "setblock 26 -60 36 skyblocks:effect_block{group:102L,anchor:7146825732036L,settings:{effect:2,count:4,speed:100,size:100,brightness:100,pixel:0b,colours:[I;16728128,4243711,16764992,6356864,0,0,0,0]}}", "setblock 27 -60 36 skyblocks:effect_block{group:103L,anchor:7421703638980L,settings:{effect:3,count:4,speed:100,size:100,brightness:100,pixel:0b,colours:[I;16728128,4243711,16764992,6356864,0,0,0,0]}}", "setblock 28 -60 36 skyblocks:effect_block{group:104L,anchor:7696581545924L,settings:{effect:4,count:4,speed:100,size:100,brightness:100,pixel:0b,colours:[I;16728128,4243711,16764992,6356864,0,0,0,0]}}", "setblock 29 -60 36 skyblocks:effect_block{group:105L,anchor:7971459452868L,settings:{effect:5,count:4,speed:100,size:100,brightness:100,pixel:1b,colours:[I;16728128,4243711,16764992,6356864,0,0,0,0]}}", "setblock 30 -60 36 skyblocks:effect_block{group:106L,anchor:8246337359812L,settings:{effect:6,count:4,speed:100,size:100,brightness:100,pixel:0b,colours:[I;16728128,4243711,16764992,6356864,0,0,0,0]}}", "setblock 31 -60 36 skyblocks:effect_block{group:107L,anchor:8521215266756L,settings:{effect:7,count:4,speed:100,size:100,brightness:100,pixel:0b,colours:[I;16728128,4243711,16764992,6356864,0,0,0,0]}}", "setblock 32 -60 36 skyblocks:effect_block{group:108L,anchor:8796093173700L,settings:{effect:8,count:4,speed:100,size:100,brightness:100,pixel:0b,colours:[I;16728128,4243711,16764992,6356864,0,0,0,0]}}", "setblock 33 -60 36 skyblocks:effect_block{group:109L,anchor:9070971080644L,settings:{effect:9,count:4,speed:100,size:100,brightness:100,pixel:0b,colours:[I;16728128,4243711,16764992,6356864,0,0,0,0]}}", "setblock 34 -60 36 skyblocks:effect_block{group:110L,anchor:9345848987588L,settings:{effect:10,count:4,speed:100,size:100,brightness:100,pixel:0b,colours:[I;16728128,4243711,16764992,6356864,0,0,0,0]}}", "setblock 35 -60 36 skyblocks:effect_block{group:111L,anchor:9620726894532L,settings:{effect:11,count:4,speed:100,size:100,brightness:100,pixel:0b,colours:[I;16728128,4243711,16764992,6356864,0,0,0,0]}}",
            // a wall of the underwater sky, seen from the same spot turned two ways (the sky must stay put)
            "fill -20 -60 20 -17 -56 20 skyblocks:underwater_liquid", "fill -16 -60 20 -14 -56 20 skyblocks:aurora_liquid",
            // the arcade: a cabinet, painted ones beside it (yellow, blue, red), a wall of big screens over them
            "forceload add 262 -6 280 6",
            "setblock 270 -60 0 skyblocks:arcade[facing=north,half=lower]", "setblock 270 -59 0 skyblocks:arcade[facing=north,half=upper]",
            "setblock 272 -60 0 skyblocks:arcade[facing=north,half=lower,color=5]", "setblock 272 -59 0 skyblocks:arcade[facing=north,half=upper,color=5]",
            "setblock 273 -60 0 skyblocks:arcade[facing=north,half=lower,color=12]", "setblock 273 -59 0 skyblocks:arcade[facing=north,half=upper,color=12]",
            "setblock 268 -60 0 skyblocks:arcade[facing=north,half=lower,color=15]", "setblock 268 -59 0 skyblocks:arcade[facing=north,half=upper,color=15]",
            "fill 267 -57 1 274 -54 1 skyblocks:big_screen[facing=north]",
            // the garden: raked zen sand (lines, waves, a ring round stones), bonsais of each style, a sunken hot spring,
            // OIIA cats (standing, sitting), photos (on a wall, standing); a map table (and a big one of four) over built land
            "forceload add 320 -70 450 70",
            "forceload add 451 -20 500 20", // (the newest things, east of the rest)
            "fill 330 -61 0 336 -61 1 skyblocks:zen_sand[pattern=lines_x]", "fill 330 -61 5 336 -61 6 skyblocks:zen_sand[pattern=waves_x]",
            "fill 330 -61 2 331 -61 4 skyblocks:zen_sand[pattern=lines_z]", "fill 335 -61 2 336 -61 4 skyblocks:zen_sand[pattern=waves_z]",
            "setblock 332 -61 2 skyblocks:zen_sand[pattern=ring_nw]", "setblock 333 -61 2 skyblocks:zen_sand[pattern=ring_n]",
            "setblock 334 -61 2 skyblocks:zen_sand[pattern=ring_ne]", "setblock 332 -61 3 skyblocks:zen_sand[pattern=ring_w]",
            "setblock 333 -61 3 skyblocks:zen_sand[pattern=ring]", "setblock 334 -61 3 skyblocks:zen_sand[pattern=ring_e]",
            "setblock 332 -61 4 skyblocks:zen_sand[pattern=ring_sw]", "setblock 333 -61 4 skyblocks:zen_sand[pattern=ring_s]",
            "setblock 334 -61 4 skyblocks:zen_sand[pattern=ring_se]", "setblock 333 -60 3 skyblocks:zen_stone[rocks=2]",
            "setblock 330 -60 6 skyblocks:zen_stone[rocks=1]", "setblock 336 -60 0 skyblocks:zen_stone[rocks=0]",
            "setblock 330 -60 9 skyblocks:bonsai[kind=oak,age=4,style=upright,facing=north]",
            "setblock 332 -60 9 skyblocks:bonsai[kind=cherry,age=4,style=cascade,facing=north]",
            "setblock 334 -60 9 skyblocks:bonsai[kind=azalea,age=4,style=clouds,facing=north]",
            "setblock 336 -60 9 skyblocks:bonsai[kind=spruce,age=3,style=windswept,facing=north]",
            "setblock 338 -60 9 skyblocks:bonsai[kind=birch,age=4,style=round,facing=north]",
            "setblock 340 -60 9 skyblocks:bonsai[kind=jungle,age=1,style=upright,facing=north]",
            "setblock 330 -60 12 skyblocks:bonsai[kind=oak,age=4,style=natural,facing=north]",
            "setblock 332 -60 12 skyblocks:bonsai[kind=cherry,age=4,style=natural,facing=north]",
            "setblock 334 -60 12 skyblocks:bonsai[kind=spruce,age=4,style=natural,facing=north]",
            "setblock 336 -60 12 skyblocks:bonsai[kind=azalea,age=4,style=natural,facing=north]",
            "setblock 338 -60 12 skyblocks:bonsai[kind=dark_oak,age=3,style=natural,facing=north]",
            "setblock 342 -61 0 skyblocks:hot_spring[east=true,south=true]", "setblock 343 -61 0 skyblocks:hot_spring[east=true,west=true,south=true]",
            "setblock 344 -61 0 skyblocks:hot_spring[west=true,south=true]", "setblock 342 -61 1 skyblocks:hot_spring[east=true,north=true]",
            "setblock 343 -61 1 skyblocks:hot_spring[east=true,west=true,north=true]", "setblock 344 -61 1 skyblocks:hot_spring[west=true,north=true]",
            "summon skyblocks:oiia_cat 338.5 -60 3.5 {NoAI:1b,Rotation:[180f,0f]}", "summon skyblocks:oiia_cat 339.7 -60 4.6 {NoAI:1b,Sitting:1b,Rotation:[150f,0f]}",
            "fill 344 -60 10 349 -57 10 minecraft:stone_bricks", "#photo 345 -59 9 north false", "#photo 348 -60 8 north true",
            "fill 352 -60 12 362 -56 12 minecraft:stone_bricks", "#photo 354 -58 11 north false medium", "#photo 359 -58 11 north false large",
            "#photo 353 -60 8 north true medium", "#photo 357 -60 8 north true large",
            // the cat's things, a cat at each: napping in its bed, scratching, eating, in a box, up its tree; a cat flap; yarn; a named cat
            "setblock 370 -60 2 skyblocks:cat_bed[facing=south,color=light_blue]", "setblock 373 -60 2 skyblocks:scratching_post",
            "setblock 376 -60 2 skyblocks:cat_bowl[food=3,color=pink]", "setblock 379 -60 2 skyblocks:cardboard_box",
            "setblock 382 -60 2 skyblocks:cat_tree[facing=south,half=lower]", "setblock 382 -59 2 skyblocks:cat_tree[facing=south,half=upper]",
            "fill 385 -60 1 389 -58 1 minecraft:stone_bricks", "setblock 387 -60 1 skyblocks:cat_flap[facing=south,open=true]{material:{Name:\"minecraft:stone_bricks\"}}",
            "setblock 388 -60 4 skyblocks:cat_flap[facing=south]", "setblock 389 -60 4 skyblocks:cat_bowl", "setblock 390 -60 4 skyblocks:cat_bed",
            "summon skyblocks:oiia_cat 370.5 -59.8 2.5 {NoAI:1b,Owner:[I;1,2,3,4],Mood:1b,Rotation:[20f,0f]}",
            "summon skyblocks:oiia_cat 373.5 -60 3.15 {NoAI:1b,Owner:[I;1,2,3,4],Mood:3b,CollarColor:4b,Rotation:[180f,0f]}",
            "summon skyblocks:oiia_cat 376.5 -60 3.2 {NoAI:1b,Owner:[I;1,2,3,4],Mood:4b,CollarColor:5b,Rotation:[180f,0f]}",
            "summon skyblocks:oiia_cat 379.5 -59.94 2.5 {NoAI:1b,Owner:[I;1,2,3,4],Mood:5b,CollarColor:10b,Rotation:[10f,0f]}",
            "summon skyblocks:oiia_cat 382.5 -58.12 2.5 {NoAI:1b,Owner:[I;1,2,3,4],Mood:6b,CollarColor:1b,Rotation:[0f,0f]}",
            "summon skyblocks:oiia_cat 374.5 -60 5.5 {NoAI:1b,Owner:[I;1,2,3,4],CollarColor:11b,CustomName:'\"Mochi\"',Rotation:[15f,0f]}",
            "summon skyblocks:yarn_ball 371.6 -60 4.6",
            // crystal geode lamps in a stone room (rainbow, amethyst)
            "fill 393 -61 -4 401 -55 4 minecraft:smooth_stone hollow", "fill 397 -60 4 397 -59 4 minecraft:air",
            "setblock 395 -60 0 skyblocks:geode_lamp[crystal=4]", "setblock 399 -60 -1 skyblocks:geode_lamp[crystal=0]",
            // infusion altars holding shader-painted swords (galaxy blade, magma hilt; holo foil / aurora), and one dropped
            "setblock 404 -60 0 skyblocks:infusion_altar{item:{id:\"minecraft:diamond_sword\",count:1,components:{\"skyblocks:shader_paint\":{w:16,h:16,pixels:[B;0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b]}}}}",
            "setblock 407 -60 0 skyblocks:infusion_altar{item:{id:\"minecraft:netherite_sword\",count:1,components:{\"skyblocks:shader_paint\":{w:16,h:16,pixels:[B;0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,0b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,0b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b]}}}}",
            "setblock 410 -60 0 skyblocks:infusion_altar{item:{id:\"minecraft:diamond_sword\",count:1,components:{\"skyblocks:shader_paint\":{w:16,h:16,pixels:[B;0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,42b,42b,42b,42b,42b,42b,42b,0b,0b,0b,0b,0b,0b,0b,0b,42b,42b,42b,42b,42b,42b,42b,0b,0b,0b,0b,0b,0b,0b,0b,42b,42b,42b,42b,42b,42b,42b,0b,0b,0b,0b,0b,0b,0b,0b,0b,42b,42b,42b,42b,42b,42b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,42b,42b,42b,42b,42b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,42b,42b,42b,42b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b]}}}}",
            "setblock 413 -60 0 skyblocks:infusion_altar{item:{id:\"minecraft:diamond_sword\",count:1,components:{\"skyblocks:shader_paint\":{w:16,h:16,pixels:[B;0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,39b,39b,39b,39b,39b,39b,39b,0b,0b,0b,0b,0b,0b,0b,0b,39b,39b,39b,39b,39b,39b,39b,0b,0b,0b,0b,0b,0b,0b,0b,39b,39b,39b,39b,39b,39b,39b,0b,0b,0b,0b,0b,0b,0b,0b,0b,39b,39b,39b,39b,39b,39b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,39b,39b,39b,39b,39b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,39b,39b,39b,39b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b]}}}}",
            "summon item 405.5 -60 2 {Item:{id:\"minecraft:diamond_sword\",count:1,components:{\"skyblocks:shader_paint\":{w:16,h:16,pixels:[B;0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b]}}},PickupDelay:32767s,Age:-32768s}",
            // armour painted with skies, worn by armour stands; an altar with a chestplate (its editor opened as worn below)
            armourStandA(), armourStandB(), armourAltar(),
            // the cat things, remodelled: a bed, posts (new, worn, frayed), a tree, bowls (full, a little), a box, flaps
            "setblock 425 -60 0 skyblocks:cat_bed[color=purple,facing=south]", "setblock 427 -60 0 skyblocks:scratching_post[wear=0]",
            "setblock 428 -60 0 skyblocks:scratching_post[wear=1]", "setblock 429 -60 0 skyblocks:scratching_post[wear=2]",
            "setblock 431 -60 0 skyblocks:cat_tree[facing=south,half=lower]", "setblock 431 -59 0 skyblocks:cat_tree[facing=south,half=upper]",
            "setblock 433 -60 0 skyblocks:cat_bowl[food=4]", "setblock 434 -60 0 skyblocks:cat_bowl[food=1,color=lime]", "setblock 436 -60 0 skyblocks:cardboard_box",
            "fill 438 -60 -1 440 -59 -1 minecraft:stone_bricks", "setblock 438 -60 -1 skyblocks:cat_flap[facing=south,open=true]{material:{Name:\"minecraft:stone_bricks\"}}",
            "setblock 440 -60 -1 skyblocks:cat_flap[facing=south]",
            // two cats together: one grooming the other, two touching noses, two asleep in one bed
            "summon skyblocks:oiia_cat 443.1 -60 1.5 {NoAI:1b,Owner:[I;1,2,3,4],Mood:7b,CollarColor:3b,Rotation:[-90f,0f]}",
            "summon skyblocks:oiia_cat 443.9 -60 1.5 {NoAI:1b,Owner:[I;1,2,3,4],Mood:8b,CollarColor:6b,Rotation:[90f,0f]}",
            "summon skyblocks:oiia_cat 446.1 -60 1.5 {NoAI:1b,Owner:[I;1,2,3,4],Mood:9b,CollarColor:5b,Rotation:[-90f,0f]}",
            "summon skyblocks:oiia_cat 446.85 -60 1.5 {NoAI:1b,Owner:[I;1,2,3,4],Mood:9b,CollarColor:14b,Rotation:[90f,0f]}",
            "setblock 449 -60 1 skyblocks:cat_bed[color=pink,facing=south]",
            "summon skyblocks:oiia_cat 449.31 -59.81 1.5 {NoAI:1b,Owner:[I;1,2,3,4],Mood:1b,CollarColor:11b,Rotation:[0f,0f]}",
            "summon skyblocks:oiia_cat 449.69 -59.81 1.5 {NoAI:1b,Owner:[I;1,2,3,4],Mood:1b,CollarColor:1b,Rotation:[180f,0f]}",
            // sky lanterns in a dark stone room: golden hour and underwater standing, a nebula hanging from the ceiling
            "fill 452 -61 -4 460 -55 4 minecraft:smooth_stone hollow", "fill 456 -60 4 456 -59 4 minecraft:air",
            "setblock 454 -60 -1 skyblocks:sky_lantern{sky:\"SKYBLOCKS_GOLDEN_HOUR\",pass:\"NORMAL\"}",
            "setblock 458 -60 -1 skyblocks:sky_lantern{sky:\"SKYBLOCKS_UNDERWATER\",pass:\"NORMAL\"}",
            "setblock 456 -56 1 skyblocks:sky_lantern[hanging=true]{sky:\"SKYBLOCKS_NEBULA\",pass:\"NORMAL\"}",
            // a cat in its window perch looking out (butterflies outside); a cat by a koi pond; ferrofluid with redstone and
            // without; a sky clock on a wall; a prism in the sun
            "fill 463 -60 -1 466 -58 -1 minecraft:glass", "setblock 464 -59 0 skyblocks:cat_window_perch[facing=south]",
            "summon skyblocks:oiia_cat 464.5 -58.725 0.344 {NoAI:1b,Owner:[I;1,2,3,4],Mood:10b,CollarColor:4b,Rotation:[180f,0f]}",
            "fill 468 -61 0 469 -61 1 skyblocks:koi_pond", "summon skyblocks:oiia_cat 470.4 -60 0.9 {NoAI:1b,Mood:11b,CollarColor:9b,Rotation:[90f,0f]}",
            "setblock 473 -60 0 skyblocks:ferrofluid", "setblock 473 -60 -1 minecraft:redstone_block", "setblock 475 -60 0 skyblocks:ferrofluid",
            "fill 477 -60 -1 479 -58 -1 minecraft:stone_bricks", "setblock 478 -59 0 skyblocks:sky_clock[facing=south]",
            "setblock 482 -60 0 skyblocks:prism[axis=x]",
            // the cats' animations, held still: the walk and the gallop through their strides, a jump going up and coming
            // down, the stretch on waking, sat
            "summon skyblocks:oiia_cat 486.0 -60 0.5 {NoAI:1b,CustomName:'\"anim:walk:0\"',Rotation:[-90f,0f]}",
            "summon skyblocks:oiia_cat 487.4 -60 0.5 {NoAI:1b,CustomName:'\"anim:walk:0.25\"',Rotation:[-90f,0f]}",
            "summon skyblocks:oiia_cat 488.8 -60 0.5 {NoAI:1b,CustomName:'\"anim:walk:0.5\"',Rotation:[-90f,0f]}",
            "summon skyblocks:oiia_cat 490.2 -60 0.5 {NoAI:1b,CustomName:'\"anim:walk:0.75\"',Rotation:[-90f,0f]}",
            "summon skyblocks:oiia_cat 491.6 -60 0.5 {NoAI:1b,CustomName:'\"anim:gallop:0\"',Rotation:[-90f,0f]}",
            "summon skyblocks:oiia_cat 493.0 -60 0.5 {NoAI:1b,CustomName:'\"anim:gallop:0.25\"',Rotation:[-90f,0f]}",
            "summon skyblocks:oiia_cat 494.4 -60 0.5 {NoAI:1b,CustomName:'\"anim:gallop:0.5\"',Rotation:[-90f,0f]}",
            "summon skyblocks:oiia_cat 495.8 -60 0.5 {NoAI:1b,CustomName:'\"anim:gallop:0.75\"',Rotation:[-90f,0f]}",
            "summon skyblocks:oiia_cat 486.0 -59.4 4.5 {NoAI:1b,NoGravity:1b,CustomName:'\"anim:up\"',Rotation:[-90f,0f]}",
            "summon skyblocks:oiia_cat 487.4 -59.4 4.5 {NoAI:1b,NoGravity:1b,CustomName:'\"anim:down\"',Rotation:[-90f,0f]}",
            "summon skyblocks:oiia_cat 488.8 -60 4.5 {NoAI:1b,NoGravity:1b,CustomName:'\"anim:stretch:0.3\"',Rotation:[-90f,0f]}",
            "summon skyblocks:oiia_cat 490.2 -60 4.5 {NoAI:1b,NoGravity:1b,CustomName:'\"anim:stretch:0.75\"',Rotation:[-90f,0f]}",
            "summon skyblocks:oiia_cat 491.6 -60 4.5 {NoAI:1b,NoGravity:1b,CustomName:'\"anim:sit\"',Rotation:[-90f,0f]}",
    };
    /** The newer things (SKYBLOCKS_RENDER_SET=new: only these), well away from the rest. */
    static final boolean NEW = "new".equals(System.getenv("SKYBLOCKS_RENDER_SET"));
    private static final String[] NEW_SCENE = {
            "forceload add 190 -10 300 60", // (far from where the world starts: loaded, so things can be put there)
            // a claw machine and an arcade cabinet
            "setblock 200 -60 0 skyblocks:claw_machine[facing=north,half=lower]", "setblock 200 -59 0 skyblocks:claw_machine[facing=north,half=upper]",
            "setblock 270 -60 0 skyblocks:arcade[facing=north,half=lower]", "setblock 270 -59 0 skyblocks:arcade[facing=north,half=upper]",
            // painted cabinets beside it (yellow, blue, red), and a wall of big screens over them
            "setblock 272 -60 0 skyblocks:arcade[facing=north,half=lower,color=5]", "setblock 272 -59 0 skyblocks:arcade[facing=north,half=upper,color=5]",
            "setblock 273 -60 0 skyblocks:arcade[facing=north,half=lower,color=12]", "setblock 273 -59 0 skyblocks:arcade[facing=north,half=upper,color=12]",
            "setblock 268 -60 0 skyblocks:arcade[facing=north,half=lower,color=15]", "setblock 268 -59 0 skyblocks:arcade[facing=north,half=upper,color=15]",
            "fill 267 -57 1 274 -54 1 skyblocks:big_screen[facing=north]",
            // every plushie
            "setblock 280 -60 0 skyblocks:plushie_cat[facing=north]", "setblock 281 -60 0 skyblocks:plushie_ember[facing=north]",
            "setblock 282 -60 0 skyblocks:plushie_frost[facing=north]", "setblock 283 -60 0 skyblocks:plushie_moss[facing=north]",
            "setblock 284 -60 0 skyblocks:plushie_bloom[facing=north]", "setblock 285 -60 0 skyblocks:plushie_frog[facing=north]",
            "setblock 286 -60 0 skyblocks:plushie_axolotl[facing=north]", "setblock 287 -60 0 skyblocks:plushie_clownfish[facing=north]",
            "setblock 288 -60 0 skyblocks:plushie_blue_tang[facing=north]", "setblock 289 -60 0 skyblocks:plushie_goldfish[facing=north]",
            "setblock 290 -60 0 skyblocks:plushie_angelfish[facing=north]", "setblock 291 -60 0 skyblocks:plushie_shark[facing=north]",
            "setblock 292 -60 0 skyblocks:plushie_octopus[facing=north]", "setblock 293 -60 0 skyblocks:plushie_crab[facing=north]",
            "setblock 294 -60 0 skyblocks:plushie_ocean_sunfish[facing=north]",
            // neon tubes: along the floor, up a wall (round the corner), along a ceiling
            "fill 200 -60 6 204 -60 6 skyblocks:neon_tube[down=true,color=cyan]",
            "fill 200 -60 11 204 -56 11 minecraft:stone_bricks",
            "fill 200 -60 10 204 -60 10 skyblocks:neon_tube[down=true,south=true,color=pink]",
            "fill 202 -59 10 202 -57 10 skyblocks:neon_tube[down=false,south=true,color=pink]",
            "fill 205 -60 11 208 -56 11 minecraft:stone_bricks", "fill 206 -60 7 206 -60 9 skyblocks:neon_tube[down=true,color=yellow]",
            "setblock 206 -60 10 skyblocks:neon_tube[down=true,south=true,color=yellow]",
            "fill 206 -59 10 206 -57 10 skyblocks:neon_tube[down=false,south=true,color=yellow]",
            "fill 199 -55 6 205 -55 9 minecraft:stone_bricks",
            "fill 200 -56 8 204 -56 8 skyblocks:neon_tube[down=false,up=true,color=lime]",
            // light bridges: round a bender; a ramp; an arch; a tunnel; a wall; a wide moving walkway with rails and stars
            "setblock 220 -60 10 skyblocks:light_bridge_emitter[facing=east]", "setblock 226 -60 10 skyblocks:light_bridge_bender[facing=west,radius=2]",
            "setblock 226 -60 4 skyblocks:light_bridge_emitter[facing=south]",
            "setblock 232 -60 10 skyblocks:light_bridge_emitter[facing=east]", "fill 240 -61 10 240 -57 10 minecraft:stone_bricks",
            "setblock 240 -56 10 skyblocks:light_bridge_emitter[facing=west]",
            "setblock 232 -60 20 skyblocks:light_bridge_emitter[facing=east]{mode:1}", "setblock 244 -60 20 skyblocks:light_bridge_emitter[facing=west]{mode:1}",
            "setblock 232 -60 30 skyblocks:light_bridge_emitter[facing=east,color=3]{mode:4,colour:3847130}",
            "setblock 244 -60 30 skyblocks:light_bridge_emitter[facing=west,color=3]{mode:4,colour:3847130}",
            "setblock 232 -60 40 skyblocks:light_bridge_emitter[facing=east,color=14]{mode:3,colour:11546150,pattern:1}",
            "setblock 244 -60 40 skyblocks:light_bridge_emitter[facing=west,color=14]{mode:3,colour:11546150,pattern:1}",
            "setblock 232 -60 50 skyblocks:light_bridge_emitter[facing=east]{mode:2,width:2,rails:1b,pattern:4}",
            "setblock 244 -60 50 skyblocks:light_bridge_emitter[facing=west]{mode:2,width:2,rails:1b,pattern:4}",
            // terrariums: four joined into one big desert tank, and a jungle one on its own
            "setblock 250 -60 0 skyblocks:terrarium[scene=0,north=false,west=false,east=true,south=true]",
            "setblock 251 -60 0 skyblocks:terrarium[scene=0,north=false,west=true,east=false,south=true]",
            "setblock 250 -60 1 skyblocks:terrarium[scene=0,north=true,west=false,east=true,south=false]",
            "setblock 251 -60 1 skyblocks:terrarium[scene=0,north=true,west=true,east=false,south=false]",
            "setblock 254 -60 0 skyblocks:terrarium[scene=1]",
            // teleport pads with names
            "setblock 260 -60 0 skyblocks:teleport_pad[color=5]{name:\"Home\"}", "setblock 262 -60 0 skyblocks:teleport_pad[color=5]{name:\"Mine\"}",
            "setblock 264 -60 0 skyblocks:teleport_pad[color=14]{name:\"Farm\"}",
            // the garden: raked zen sand (lines, waves, a ring round stones), bonsais of each style, a sunken hot spring,
            // OIIA cats (standing, sitting), photos (on a wall, standing); a map table (and a big one of four) over built land
            "forceload add 320 -70 450 70",
            "fill 330 -61 0 336 -61 1 skyblocks:zen_sand[pattern=lines_x]", "fill 330 -61 5 336 -61 6 skyblocks:zen_sand[pattern=waves_x]",
            "fill 330 -61 2 331 -61 4 skyblocks:zen_sand[pattern=lines_z]", "fill 335 -61 2 336 -61 4 skyblocks:zen_sand[pattern=waves_z]",
            "setblock 332 -61 2 skyblocks:zen_sand[pattern=ring_nw]", "setblock 333 -61 2 skyblocks:zen_sand[pattern=ring_n]",
            "setblock 334 -61 2 skyblocks:zen_sand[pattern=ring_ne]", "setblock 332 -61 3 skyblocks:zen_sand[pattern=ring_w]",
            "setblock 333 -61 3 skyblocks:zen_sand[pattern=ring]", "setblock 334 -61 3 skyblocks:zen_sand[pattern=ring_e]",
            "setblock 332 -61 4 skyblocks:zen_sand[pattern=ring_sw]", "setblock 333 -61 4 skyblocks:zen_sand[pattern=ring_s]",
            "setblock 334 -61 4 skyblocks:zen_sand[pattern=ring_se]", "setblock 333 -60 3 skyblocks:zen_stone[rocks=2]",
            "setblock 330 -60 6 skyblocks:zen_stone[rocks=1]", "setblock 336 -60 0 skyblocks:zen_stone[rocks=0]",
            "setblock 330 -60 9 skyblocks:bonsai[kind=oak,age=4,style=upright,facing=north]",
            "setblock 332 -60 9 skyblocks:bonsai[kind=cherry,age=4,style=cascade,facing=north]",
            "setblock 334 -60 9 skyblocks:bonsai[kind=azalea,age=4,style=clouds,facing=north]",
            "setblock 336 -60 9 skyblocks:bonsai[kind=spruce,age=3,style=windswept,facing=north]",
            "setblock 338 -60 9 skyblocks:bonsai[kind=birch,age=4,style=round,facing=north]",
            "setblock 340 -60 9 skyblocks:bonsai[kind=jungle,age=1,style=upright,facing=north]",
            "setblock 330 -60 12 skyblocks:bonsai[kind=oak,age=4,style=natural,facing=north]",
            "setblock 332 -60 12 skyblocks:bonsai[kind=cherry,age=4,style=natural,facing=north]",
            "setblock 334 -60 12 skyblocks:bonsai[kind=spruce,age=4,style=natural,facing=north]",
            "setblock 336 -60 12 skyblocks:bonsai[kind=azalea,age=4,style=natural,facing=north]",
            "setblock 338 -60 12 skyblocks:bonsai[kind=dark_oak,age=3,style=natural,facing=north]",
            "setblock 342 -61 0 skyblocks:hot_spring[east=true,south=true]", "setblock 343 -61 0 skyblocks:hot_spring[east=true,west=true,south=true]",
            "setblock 344 -61 0 skyblocks:hot_spring[west=true,south=true]", "setblock 342 -61 1 skyblocks:hot_spring[east=true,north=true]",
            "setblock 343 -61 1 skyblocks:hot_spring[east=true,west=true,north=true]", "setblock 344 -61 1 skyblocks:hot_spring[west=true,north=true]",
            "summon skyblocks:oiia_cat 338.5 -60 3.5 {NoAI:1b,Rotation:[180f,0f]}", "summon skyblocks:oiia_cat 339.7 -60 4.6 {NoAI:1b,Sitting:1b,Rotation:[150f,0f]}",
            "fill 344 -60 10 349 -57 10 minecraft:stone_bricks", "#photo 345 -59 9 north false", "#photo 348 -60 8 north true",
            "fill 352 -60 12 362 -56 12 minecraft:stone_bricks", "#photo 354 -58 11 north false medium", "#photo 359 -58 11 north false large",
            "#photo 353 -60 8 north true medium", "#photo 357 -60 8 north true large",
            // the cat's things, a cat at each: napping in its bed, scratching, eating, in a box, up its tree; a cat flap; yarn; a named cat
            "setblock 370 -60 2 skyblocks:cat_bed[facing=south,color=light_blue]", "setblock 373 -60 2 skyblocks:scratching_post",
            "setblock 376 -60 2 skyblocks:cat_bowl[food=3,color=pink]", "setblock 379 -60 2 skyblocks:cardboard_box",
            "setblock 382 -60 2 skyblocks:cat_tree[facing=south,half=lower]", "setblock 382 -59 2 skyblocks:cat_tree[facing=south,half=upper]",
            "fill 385 -60 1 389 -58 1 minecraft:stone_bricks", "setblock 387 -60 1 skyblocks:cat_flap[facing=south,open=true]{material:{Name:\"minecraft:stone_bricks\"}}",
            "setblock 388 -60 4 skyblocks:cat_flap[facing=south]", "setblock 389 -60 4 skyblocks:cat_bowl", "setblock 390 -60 4 skyblocks:cat_bed",
            "summon skyblocks:oiia_cat 370.5 -59.8 2.5 {NoAI:1b,Owner:[I;1,2,3,4],Mood:1b,Rotation:[20f,0f]}",
            "summon skyblocks:oiia_cat 373.5 -60 3.15 {NoAI:1b,Owner:[I;1,2,3,4],Mood:3b,CollarColor:4b,Rotation:[180f,0f]}",
            "summon skyblocks:oiia_cat 376.5 -60 3.2 {NoAI:1b,Owner:[I;1,2,3,4],Mood:4b,CollarColor:5b,Rotation:[180f,0f]}",
            "summon skyblocks:oiia_cat 379.5 -59.94 2.5 {NoAI:1b,Owner:[I;1,2,3,4],Mood:5b,CollarColor:10b,Rotation:[10f,0f]}",
            "summon skyblocks:oiia_cat 382.5 -58.12 2.5 {NoAI:1b,Owner:[I;1,2,3,4],Mood:6b,CollarColor:1b,Rotation:[0f,0f]}",
            "summon skyblocks:oiia_cat 374.5 -60 5.5 {NoAI:1b,Owner:[I;1,2,3,4],CollarColor:11b,CustomName:'\"Mochi\"',Rotation:[15f,0f]}",
            "summon skyblocks:yarn_ball 371.6 -60 4.6",
            // crystal geode lamps in a stone room (rainbow, amethyst)
            "fill 393 -61 -4 401 -55 4 minecraft:smooth_stone hollow", "fill 397 -60 4 397 -59 4 minecraft:air",
            "setblock 395 -60 0 skyblocks:geode_lamp[crystal=4]", "setblock 399 -60 -1 skyblocks:geode_lamp[crystal=0]",
            // infusion altars holding shader-painted swords (galaxy blade, magma hilt; holo foil / aurora), and one dropped
            "setblock 404 -60 0 skyblocks:infusion_altar{item:{id:\"minecraft:diamond_sword\",count:1,components:{\"skyblocks:shader_paint\":{w:16,h:16,pixels:[B;0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b]}}}}",
            "setblock 407 -60 0 skyblocks:infusion_altar{item:{id:\"minecraft:netherite_sword\",count:1,components:{\"skyblocks:shader_paint\":{w:16,h:16,pixels:[B;0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,0b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,0b,5b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,16b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,5b,16b,5b,16b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b]}}}}",
            "setblock 410 -60 0 skyblocks:infusion_altar{item:{id:\"minecraft:diamond_sword\",count:1,components:{\"skyblocks:shader_paint\":{w:16,h:16,pixels:[B;0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,21b,21b,21b,21b,21b,21b,21b,0b,0b,0b,0b,0b,0b,0b,0b,42b,42b,42b,42b,42b,42b,42b,0b,0b,0b,0b,0b,0b,0b,0b,42b,42b,42b,42b,42b,42b,42b,0b,0b,0b,0b,0b,0b,0b,0b,42b,42b,42b,42b,42b,42b,42b,0b,0b,0b,0b,0b,0b,0b,0b,0b,42b,42b,42b,42b,42b,42b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,42b,42b,42b,42b,42b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,42b,42b,42b,42b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b]}}}}",
            "setblock 413 -60 0 skyblocks:infusion_altar{item:{id:\"minecraft:diamond_sword\",count:1,components:{\"skyblocks:shader_paint\":{w:16,h:16,pixels:[B;0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,41b,41b,41b,41b,41b,41b,41b,0b,0b,0b,0b,0b,0b,0b,0b,39b,39b,39b,39b,39b,39b,39b,0b,0b,0b,0b,0b,0b,0b,0b,39b,39b,39b,39b,39b,39b,39b,0b,0b,0b,0b,0b,0b,0b,0b,39b,39b,39b,39b,39b,39b,39b,0b,0b,0b,0b,0b,0b,0b,0b,0b,39b,39b,39b,39b,39b,39b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,39b,39b,39b,39b,39b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,39b,39b,39b,39b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b]}}}}",
            "summon item 405.5 -60 2 {Item:{id:\"minecraft:diamond_sword\",count:1,components:{\"skyblocks:shader_paint\":{w:16,h:16,pixels:[B;0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,1b,1b,1b,1b,1b,1b,1b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,9b,9b,9b,9b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b,0b]}}},PickupDelay:32767s,Age:-32768s}",
            // armour painted with skies, worn by armour stands (painted as worn: stripes, redrawn pixels, checks; or left to their icons: all over, round the edges), and an altar with a chestplate
            armourStandA(), armourStandB(), armourAltar(),
    };
    private static final String[] NEW_SPOTS = {"200.5 -59.2 -3 0 18", "open 200 -60 0 200.5 -60 -1.6 0 15", "283.5 -59.6 -2.4 0 32",
            "290.5 -59.6 -2.4 0 32", "286.5 -60 -1.2 0 30", "202.5 -58.5 2.6 0 22", "night 202.5 -58.5 2.6 0 22", "229 -56 13 130 35",
            "night 229 -56 13 130 35", "236 -58 2 0 10", "238 -58 12 0 10", "238 -58 22 0 8", "night 238 -58 22 0 8", "238 -58 32 0 8",
            "238 -57 43 0 20", "251 -59 -3 0 30", "254.5 -59 -2.2 0 32", "262 -59 -3 0 30", "open 260 -60 0 260.5 -60 -1.5 0 30",
            "open 220 -60 10 219.5 -60 8 0 30", "playpick 7 open 270 -60 0 270.5 -60 -1.6 0 10", "play 7 open 270 -60 0 270.5 -60 -1.6 0 10",
            "play 3 open 270 -60 0 270.5 -60 -1.6 0 10", "playmoves 7 open 270 -60 0 270.5 -60 -1.6 0 10", "controls open 270 -60 0 270.5 -60 -1.6 0 10",
            "334 -58 -4 0 32", "333.5 -57.6 0.8 0 72", "335 -59.6 6.6 0 18", "331.5 -59.8 7.7 0 24", "334.5 -59.8 7.7 0 24", "343.5 -59.2 -2.2 0 38",
            "night 343.5 -59.2 -2.2 0 38", "338.8 -59.6 1.4 0 18", "346.5 -59.5 6.4 0 10",
            "catwalk 338.8 -59.6 1.4 0 18", "catwalk 340.6 -59.7 3.2 90 12", "holdphoto 333.5 -60 -3 0 8", "holdcamera 333.5 -60 -3 0 8", "356.5 -59.4 4.5 0 8", "night 356.5 -59.4 4.5 0 8", "334 -59.5 10.2 0 22", "331 -59.7 10.9 0 25", 
            "376 -58.3 8.8 180 22", "373.5 -59.35 5.4 180 12", "376.5 -59.35 5.4 180 15", "379.5 -59.1 4.4 180 32", "382.5 -57.6 5.6 180 12",
            "370.5 -59.2 4.6 180 30", "374.5 -59.45 7.0 180 4", "372 -59.6 6.2 200 18", "387.5 -59.2 4.2 180 8", "night 397.5 -58.5 3.3 180 25", "397 -58.2 3.3 160 10", "405.5 -58.6 3.4 180 18", "405.5 -59.2 3.6 180 28", "411.5 -58.6 3.4 180 18", 
            "watch 7 open 270 -60 0 270.5 -58.6 -7 0 3", "watch 7 open 270 -60 0 270.5 -59.3 -1.1 0 52", "watch 7 open 270 -60 0 night 270.5 -58.6 -7 0 3",
            "418 -59.1 3.9 180 10", "416.5 -58.7 2.2 180 14", "419.5 -58.7 2.2 180 14", "418 -59.1 -2.9 0 10", "night 418 -59.1 3.9 180 10",
            "worn open 422 -60 0 422.5 -60 -1.6 0 15", "wornflat open 422 -60 0 422.5 -60 -1.6 0 15", "open 422 -60 0 422.5 -60 -1.6 0 15"};
    // ---- (painted armour) ----

    /** NBT of an ArmorPaint, w x h: style(x, y) on each pixel; colour(x, y) (0: as it was), or none. */
    private static String armourPaint(int w, int h, java.util.function.IntBinaryOperator style, @org.jetbrains.annotations.Nullable java.util.function.IntBinaryOperator colour) {
        StringBuilder b = new StringBuilder("{w:").append(w).append(",h:").append(h).append(",skies:[B;");
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int s = style.applyAsInt(x, y);
                b.append((byte) (s >> 8)).append("b,").append((byte) s).append("b,");
            }
        }
        b.setLength(b.length() - 1);
        b.append(']');
        if (colour != null) {
            b.append(",argb:[B;");
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = colour.applyAsInt(x, y);
                    b.append((byte) (c >>> 24)).append("b,").append((byte) (c >> 16)).append("b,").append((byte) (c >> 8)).append("b,").append((byte) c).append("b,");
                }
            }
            b.setLength(b.length() - 1);
            b.append(']');
        }
        return b.append('}').toString();
    }

    /** NBT of an icon's ShaderPaint (16 x 16): the first `count` pixels (row by row) painted with style. */
    private static String iconPaint(int count, int style) {
        StringBuilder b = new StringBuilder("{w:16,h:16,pixels:[B;");
        for (int i = 0; i < 256; i++) {
            int s = i < count ? style : 0;
            b.append((byte) (s >> 8)).append("b,").append((byte) s).append("b,");
        }
        b.setLength(b.length() - 1);
        return b.append("]}").toString();
    }

    private static String piece(String id, String components) {
        return "{id:\"minecraft:" + id + "\",count:1,components:{" + components + "}}";
    }

    private static String armourStandA() {
        return "summon armor_stand 416.5 -60 0.5 {NoGravity:1b,ShowArms:1b,NoBasePlate:1b,Rotation:[0f,0f],ArmorItems:["
                + piece("diamond_boots", "\"skyblocks:armor_paint\":" + armourPaint(64, 32, (x, y) -> 0, (x, y) -> (x + y) % 3 == 0 ? 0xFFFFC030 : 0)) + ","
                + piece("diamond_leggings", "\"skyblocks:armor_paint\":" + armourPaint(64, 32, (x, y) -> ((x + y) >> 1 & 1) == 0 ? 48 : 0, null)) + ","
                + piece("diamond_chestplate", "\"skyblocks:shader_paint\":" + iconPaint(256, 96)) + ","
                + piece("diamond_helmet", "\"skyblocks:shader_paint\":" + iconPaint(30, 64)) + "]}";
    }

    private static String armourStandB() {
        return "summon armor_stand 419.5 -60 0.5 {NoGravity:1b,ShowArms:1b,NoBasePlate:1b,Rotation:[0f,0f],ArmorItems:["
                + piece("golden_boots", "\"skyblocks:shader_paint\":" + iconPaint(256, 112)) + ",{},"
                + piece("iron_chestplate", "\"skyblocks:armor_paint\":" + armourPaint(64, 32, (x, y) -> ((x >> 1) + (y >> 1) & 1) == 0 ? 32 : 0,
                        (x, y) -> x % 4 == 0 ? 0xFF9040E0 : 0)) + ","
                + piece("leather_helmet", "\"minecraft:dyed_color\":{rgb:3361970},\"skyblocks:armor_paint\":" + armourPaint(64, 32, (x, y) -> 145, null)) + "]}";
    }

    private static String armourAltar() {
        return "setblock 422 -60 0 skyblocks:infusion_altar{item:" + piece("diamond_chestplate", "\"skyblocks:shader_paint\":" + iconPaint(60, 64)
                + ",\"skyblocks:armor_paint\":" + armourPaint(64, 32, (x, y) -> ((x + y) >> 1 & 1) == 0 ? 96 : 0, null)) + "}";
    }

    private static int playGame = -1;
    private static boolean pickOnly, controls, moveList, watch;
    /** (the picture a cabinet sends, given back to its own player too: there's nobody else here to see it) */
    static boolean keepPicture() {
        return ON && watch;
    }

    private static String[] spots() {
        return NEW ? NEW_SPOTS : SPOTS;
    }

    /** Where the player stands for each picture: x y z yaw pitch ("night " first: at night). */
    private static net.minecraft.core.BlockPos clickAt;
    private static long timedFrames, timedFrom;
    private static final String[] SPOTS = {"0.5 -60 -6.5 0 15", "itemswhole 0.5 -60 -6.5 0 15", "0.5 -60 1.6 0 20", "night 0.5 -60 1.6 0 20", "0.5 -59 5.6 0 40", "night 0.5 -59 5.6 0 40", "0.5 -59.5 8.5 0 8", "night 0.5 -59.5 8.5 0 8", "-2.5 -59.3 10.4 0 30", "-4 -58 18.3 0 55", "night -4 -58 18.3 0 55", "rain -4 -58 18.3 0 55", "4.5 -58 18.3 0 55", "night 4.5 -58 18.3 0 55", "night 0.5 -57 20.5 0 -8", "0.5 -59 27 0 10", "night 0.5 -59 27 0 10", "-4 -59.5 33.3 0 15", "3 -59.5 33.3 0 15", "-4.3 -59.4 34.7 0 22", "3.3 -59.4 34.7 0 22", "2.5 -59.6 12.3 0 23", "1 -59.3 2.9 0 45", "-3 -60 40 0 30", "1 -59.5 39.5 0 10", "night 1 -59.5 39.5 0 10", "-5.5 -60 45 0 5", "0.5 -59.5 52 0 12", "9 -55.5 45 0 6", "-30.5 -57 1 0 10", "-12.6 -58.9 10.1 0 10", "click -12 -60 10 -11.5 -60 8 0 25", "open 3 -60 -3 3.5 -60 -5.5 0 20", "open -12 -60 4 -11.5 -60 2 0 25", "-2 -60 -6.2 0 28", "-5 -59.5 -7.6 0 12", "1 -60 -10.5 180 5", "night 1 -60 -10.5 180 5", "1 -60 -10.5 180 5", "farportal 1 -60 -10.5 180 5", "farportal 3.6 -60 -11.8 160 5", "farportal -1.8 -60 -11.5 200 5", "20.5 -59 -15.5 180 8", "night 20.5 -59 -15.5 180 8", "22.4 -57.2 -20.2 160 22", "20.5 -60 -21.2 180 -12", "20.5 -59.5 -20.2 180 -18", "62.5 -59 -16 180 8", "62.5 -51 -20.5 180 72", "72.5 -45 -11 180 45", "fabulous 72.5 -45 -11 180 45", "81 -45 -14 136 45", "fabulous 81 -45 -14 136 45", "fabulous 72.5 -51 -21.5 180 80", "fabulous 72.5 -57 -17.9 180 -5", "72.5 -60 -17.8 180 -14", "75.5 -60 -17.8 205 -8", "72.5 -59 -20.3 180 35", "74 -59 -20.5 220 10", "62.5 -59 -30 0 8", "69 -59 -22.5 90 8", "56 -59 -22.5 -90 8", "67 -60 -18 135 2", "57.5 -57 -28 -45 20", "58.5 -59 -11.5 180 40", "open 58 -60 -14 58.5 -60 -11.5 180 30", "41.5 -59 -42.5 18 -42", "43.5 -59 -43 37 -30", "40.5 -59 -39.2 0 -88", "40.5 -59 -43.2 0 -18", "42.4 -59 -39.5 70 -40", "42.9 -59 -39.5 70 -40", "43.4 -59 -39.5 70 -40", "37.5 -59 -42.6 -35 -8", "nodepth 41.5 -59 -42.5 18 -42", "nodepth 43.5 -59 -43 37 -30", "38 -59 -33 175 -20", "night 11.5 -60 -8 0 5", "11.5 -59 3.5 -45 15", "11.5 -59 3.5 -45 -25", "10.5 -59.6 -7.8 0 8", "-12.5 -59.3 2.7 0 38", "-12.5 -59.0 5.6 0 15", "night -12.5 -59.0 5.6 0 15", "-17 -59 16 -10 0", "-17 -59 16 10 0", "burst 1 -56 -2 180 10", "burst 1 -53 8 180 12", "burst 1 -50 20 180 13", "burst move 1 -56 -2 180 10", "burst move 1 -53 8 180 12", "farportal burst 1 -53 8 180 12", "farportal burst move 1 -53 8 180 12", "-11 -59 -10 180 5", "burst -11 -56 -3 180 10", "burst move -11 -56 -3 180 10", "burst move -11 -59 -9 180 4", "nobridge -11 -59 -10 180 5", "nobridge burst move -11 -56 -3 180 10", "27 -60 18.5 0 5", "night 27 -60 18.5 0 5", "34 -60 18.5 0 5", "27 -56 13 0 40", "29.5 -59 28 0 12", "night 29.5 -59 28 0 12", "burst 27 -60 19.5 0 5",
            "334 -58 -4 0 32", "333.5 -57.6 0.8 0 72", "335 -59.6 6.6 0 18", "331.5 -59.8 7.7 0 24", "334.5 -59.8 7.7 0 24", "343.5 -59.2 -2.2 0 38",
            "night 343.5 -59.2 -2.2 0 38", "338.8 -59.6 1.4 0 18", "346.5 -59.5 6.4 0 10",
            "catwalk 338.8 -59.6 1.4 0 18", "catwalk 340.6 -59.7 3.2 90 12", "holdphoto 333.5 -60 -3 0 8", "holdcamera 333.5 -60 -3 0 8", "356.5 -59.4 4.5 0 8", "night 356.5 -59.4 4.5 0 8", "334 -59.5 10.2 0 22", "331 -59.7 10.9 0 25", 
            "376 -58.3 8.8 180 22", "373.5 -59.35 5.4 180 12", "376.5 -59.35 5.4 180 15", "379.5 -59.1 4.4 180 32", "382.5 -57.6 5.6 180 12",
            "370.5 -59.2 4.6 180 30", "374.5 -59.45 7.0 180 4", "372 -59.6 6.2 200 18", "387.5 -59.2 4.2 180 8", "night 397.5 -58.5 3.3 180 25", "397 -58.2 3.3 160 10", "405.5 -58.6 3.4 180 18", "405.5 -59.2 3.6 180 28", "411.5 -58.6 3.4 180 18", 
            "watch 7 open 270 -60 0 270.5 -58.6 -7 0 3", "watch 7 open 270 -60 0 270.5 -59.3 -1.1 0 52", "watch 7 open 270 -60 0 night 270.5 -58.6 -7 0 3", "418 -59.1 3.9 180 10", "416.5 -58.7 2.2 180 14", "419.5 -58.7 2.2 180 14", "418 -59.1 -2.9 0 10", "worn open 422 -60 0 422.5 -60 -1.6 0 15", "wornflat open 422 -60 0 422.5 -60 -1.6 0 15", "open 422 -60 0 422.5 -60 -1.6 0 15", "426.5 -59 2.6 180 24", "431.5 -58.8 2.9 180 20", "437.5 -59 2.4 180 22", "444.8 -59.3 2.8 180 24", "449.5 -59 2.6 180 38", "456.5 -59.2 3.4 180 5", "night 456.5 -59.2 3.4 180 5", "456.5 -59.2 3.4 180 -32", "464.5 -58.5 2.6 180 12", "464.5 -58.6 -3.4 0 8", "470 -59 2.6 160 32", "474 -59.2 1.7 180 38", "478.5 -58.6 1.5 180 0", "night 478.5 -58.6 1.5 180 0", "482.5 -58.3 2.6 180 42", "486.7 -59.4 2.7 180 6", "489.5 -59.4 2.7 180 6", "492.3 -59.4 2.7 180 6", "495.1 -59.4 2.7 180 6", "486.7 -59.3 6.7 180 6", "489.5 -59.3 6.7 180 6", "491.6 -59.3 6.3 180 6"};

    private static boolean burst, moving, burstOn;
    private static int burstFrame;

    /**
     * A burst spot: every frame (not every tick) for a while, the middle of the screen saved and what the mirrors did
     * written to the log - a picture that flickers shows up as one frame unlike the ones either side of it.
     */
    @SubscribeEvent
    public static void onRenderTestFrame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        if (!ON || !burstOn) {
            if (!burstOn) {
                burstFrame = 0;
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int spot = stage - 2;
        try (com.mojang.blaze3d.platform.NativeImage full = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int cw = Math.min(full.getWidth(), 640), ch = Math.min(full.getHeight(), 400);
            int x0 = (full.getWidth() - cw) / 2, y0 = (full.getHeight() - ch) / 2;
            try (com.mojang.blaze3d.platform.NativeImage crop = new com.mojang.blaze3d.platform.NativeImage(cw, ch, false)) {
                full.resizeSubRectTo(x0, y0, cw, ch, crop);
                java.io.File dir = new java.io.File(mc.gameDirectory, "screenshots");
                dir.mkdirs();
                crop.writeToFile(new java.io.File(dir, String.format(java.util.Locale.ROOT, "rendertest_%s_%d_f%02d.png", TAG, spot, burstFrame)));
            }
        } catch (Exception ex) {
            SkyBlocksMod.LOGGER.warn("Shadered+ render test: burst frame failed", ex);
        }
        SkyBlocksMod.LOGGER.info("Shadered+ render test: burst {} frame {}: {}", spot, burstFrame, MirrorRenderer.lastFrameInfo);
        burstFrame++;
    }

    private static void run(MinecraftServer server, String command) {
        server.execute(() -> {
            try {
                if (command.startsWith("#")) {
                    special(server, command.split(" "));
                    return;
                }
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
            } catch (Throwable t) {
                SkyBlocksMod.LOGGER.warn("Shadered+ render test: '{}' failed", command, t);
            }
        });
    }

    /**
     * Scene steps no command can do. "#photo x y z facing standing": a framed photo (a test picture).
     */
    private static void special(MinecraftServer server, String[] a) {
        net.minecraft.server.level.ServerLevel lvl = server.overworld();
        if (a[0].equals("#photo")) {
            int w = com.example.skyblocks.PhotoStore.W, h = com.example.skyblocks.PhotoStore.H;
            int[] rgb = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    // (a test picture: a sunset sky, a sun, hills, a lake - drawn at 160 x 120, scaled up)
                    int ox = x, oy = y;
                    x = x * 160 / w;
                    y = y * 120 / h;
                    float t = y / 120.0F;
                    int r = (int) (255 * (0.35F + 0.6F * t)), g = (int) (255 * (0.3F + 0.35F * t)), b = (int) (255 * (0.7F - 0.4F * t));
                    double sun = Math.hypot(x - 110, y - 52);
                    if (sun < 14) { r = 255; g = 220; b = 120; }
                    double hill = 78 + 10 * Math.sin(x * 0.05) + 6 * Math.sin(x * 0.13 + 1);
                    if (y > hill) { r = 50 + (int) (20 * Math.sin(x * 0.3)); g = 110 + (y - (int) hill); b = 60; }
                    if (y > 100) { r = 60; g = 110 + (int) (12 * Math.sin(x * 0.4 + y)); b = 170; }
                    rgb[oy * w + ox] = Math.min(255, Math.max(0, r)) << 16 | Math.min(255, Math.max(0, g)) << 8 | Math.min(255, Math.max(0, b));
                    x = ox;
                    y = oy;
                }
            }
            int id = com.example.skyblocks.PhotoStore.get(server).add(PhotoCodec.encodeFull(rgb, w, h));
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
            lvl.setBlock(pos, com.example.skyblocks.ModBlocks.PHOTO_FRAME.get().defaultBlockState()
                    .setValue(com.example.skyblocks.PhotoFrameBlock.FACING, net.minecraft.core.Direction.byName(a[4]))
                    .setValue(com.example.skyblocks.PhotoFrameBlock.STANDING, Boolean.parseBoolean(a[5]))
                    .setValue(com.example.skyblocks.PhotoFrameBlock.SIZE, a.length > 6 ? com.example.skyblocks.PhotoFrameBlock.Size.valueOf(a[6].toUpperCase())
                            : com.example.skyblocks.PhotoFrameBlock.Size.SMALL), 3);
            if (lvl.getBlockEntity(pos) instanceof com.example.skyblocks.PhotoFrameBlockEntity be) {
                be.setPhoto(new com.example.skyblocks.ModComponents.Photo(id, "Test", 1));
            }
        }
    }

    @SubscribeEvent
    public static void onRenderTestTick(ClientTickEvent.Post event) {
        if (!ON) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        try {
            switch (stage) {
                case 0 -> {
                    if (mc.getOverlay() == null && ((mc.screen instanceof TitleScreen && ticks > 40) || ticks > 600)) {
                        mc.options.pauseOnLostFocus = false;
                        SkyBlocksMod.LOGGER.info("Shadered+ render test: making a world");
                        mc.createWorldOpenFlows().createFreshLevel("rendertest" + System.currentTimeMillis(),
                                new LevelSettings("Render test", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                                new WorldOptions(1L, false, false),
                                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                                mc.screen);
                        stage = 1;
                        ticks = 0;
                    }
                }
                case 1 -> {
                    if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null) {
                        if (ticks > 40) {
                            MinecraftServer server = mc.getSingleplayerServer();
                            mc.setScreen(null);
                            mc.options.hideGui = true;
                            run(server, "gamerule doDaylightCycle false");
                            run(server, "gamerule doWeatherCycle false");
                            run(server, "gamerule doMobSpawning false");
                            run(server, "time set 6000");
                            for (String c : NEW ? NEW_SCENE : SCENE) {
                                run(server, c);
                            }
                            stage = 2 + FROM;
                            ticks = 0;
                        }
                    } else if (ticks > 2400) {
                        SkyBlocksMod.LOGGER.warn("Shadered+ render test: no world after 2 minutes");
                        stage = 99;
                        ticks = 0;
                    }
                }
                default -> {
                    if (stage >= 2 && stage < 2 + spots().length) {
                        int spot = stage - 2;
                        if (ticks == 1 && "shaders".equals(TAG) && (spots()[spot].contains("burst ") || spots()[spot].startsWith("nobridge "))) {
                            stage++; // (with a shader pack the frames come slowly: no frame-by-frame spots)
                            ticks = 0;
                            return;
                        }
                        if (ticks == 1) {
                            String where = spots()[spot];
                            playGame = -1;
                            pickOnly = controls = moveList = watch = false;
                            // (holding a photo and the camera, the hand and HUD shown; or the cats walking on the spot)
                            boolean holdPhoto = where.startsWith("holdphoto "), holdCamera = where.startsWith("holdcamera ");
                            OiiaCatEntityRenderer.testWalk = where.startsWith("catwalk ");
                            if (holdPhoto || holdCamera || OiiaCatEntityRenderer.testWalk) {
                                where = where.substring(where.indexOf(' ') + 1);
                            }
                            mc.options.hideGui = !(holdPhoto || holdCamera);
                            if (holdPhoto || holdCamera) {
                                String photo = "skyblocks:photo[skyblocks:photo={id:1,by:\"Test\",day:1}]";
                                run(mc.getSingleplayerServer(), "item replace entity @a weapon.mainhand with " + (holdPhoto ? photo : "skyblocks:camera"));
                                run(mc.getSingleplayerServer(), "item replace entity @a weapon.offhand with " + (holdPhoto ? "skyblocks:camera" : photo));
                                run(mc.getSingleplayerServer(), "item replace entity @a hotbar.1 with " + photo);
                                run(mc.getSingleplayerServer(), "item replace entity @a hotbar.2 with skyblocks:camera");
                            }
                            if (where.startsWith("play ") || where.startsWith("playpick ") || where.startsWith("playmoves ") || where.startsWith("watch ")) { // (an arcade game, playing)
                                pickOnly = where.startsWith("playpick ");
                                watch = where.startsWith("watch ");
                                moveList = where.startsWith("playmoves ");
                                String[] w = where.split(" ", 3);
                                playGame = Integer.parseInt(w[1]);
                                where = w[2];
                            }
                            if (where.startsWith("controls ")) {
                                controls = true;
                                where = where.substring("controls ".length());
                            }
                            com.example.skyblocks.client.SkyUsage.itemParts = !where.startsWith("itemswhole ");
                            boolean farPortal = where.startsWith("farportal ");
                            if (farPortal != MirrorRenderer.forceFarPortals) {
                                MirrorRenderer.forceFarPortals = farPortal; // (portals drawn from their last pictures, as if the other end were far)
                                MirrorRenderer.portalsChanged();
                            }
                            if (farPortal) {
                                where = where.substring("farportal ".length());
                            }
                            MirrorCulling.noBridges = where.startsWith("nobridge ");
                            if (MirrorCulling.noBridges) {
                                where = where.substring("nobridge ".length());
                            }
                            burst = where.startsWith("burst ");
                            if (burst) {
                                where = where.substring("burst ".length());
                            }
                            moving = where.startsWith("move ");
                            if (moving) {
                                where = where.substring("move ".length());
                            }
                            if (where.startsWith("itemswhole ")) {
                                where = where.substring("itemswhole ".length());
                            }
                            // (Fabulous graphics for this one: its see-through things drawn into pictures of their own, mixed by depth)
                            boolean fabulous = where.startsWith("fabulous ");
                            if (fabulous) {
                                where = where.substring("fabulous ".length());
                            }
                            net.minecraft.client.GraphicsStatus graphics = fabulous ? net.minecraft.client.GraphicsStatus.FABULOUS : net.minecraft.client.GraphicsStatus.FANCY;
                            if (mc.options.graphicsMode().get() != graphics) {
                                mc.options.graphicsMode().set(graphics);
                                mc.levelRenderer.allChanged();
                            }
                            BlackHoleRenderer.noDepth = where.startsWith("nodepth "); // (as if the depth couldn't be copied)
                            if (BlackHoleRenderer.noDepth) {
                                where = where.substring("nodepth ".length());
                            }
                            ShaderPaintScreen.testStart = 0; // (the altar's editor opened as worn armour: 1 in 3D, 2 flat)
                            if (where.startsWith("worn ")) {
                                ShaderPaintScreen.testStart = 1;
                                where = where.substring("worn ".length());
                            } else if (where.startsWith("wornflat ")) {
                                ShaderPaintScreen.testStart = 2;
                                where = where.substring("wornflat ".length());
                            }
                            boolean open = where.startsWith("open ");
                            if (open) { // (right-click a block with an empty hand: its menu, in the picture)
                                where = "click " + where.substring(5);
                            }
                            if (where.startsWith("click ")) { // (then: right-click a block with glass, as a player would)
                                String[] w = where.split(" ");
                                clickAt = new net.minecraft.core.BlockPos(Integer.parseInt(w[1]), Integer.parseInt(w[2]), Integer.parseInt(w[3]));
                                where = String.join(" ", java.util.Arrays.copyOfRange(w, 4, w.length));
                                run(mc.getSingleplayerServer(), open ? "item replace entity @a weapon.mainhand with minecraft:air"
                                        : "item replace entity @a weapon.mainhand with minecraft:glass");
                            } else {
                                clickAt = null;
                            }
                            if (where.startsWith("night ")) {
                                run(mc.getSingleplayerServer(), "time set 18000");
                                where = where.substring(6);
                            } else {
                                run(mc.getSingleplayerServer(), "time set 6000");
                            }
                            if (where.startsWith("rain ")) {
                                run(mc.getSingleplayerServer(), "weather rain");
                                where = where.substring(5);
                            } else {
                                run(mc.getSingleplayerServer(), "weather clear");
                            }
                            run(mc.getSingleplayerServer(), "tp @a " + where);
                            mc.setScreen(null);
                        }
                        if (ticks == 30 && clickAt != null && mc.player != null && mc.gameMode != null) {
                            net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                                    net.minecraft.world.phys.Vec3.atCenterOf(clickAt).add(0.0, 0.4, 0.0), net.minecraft.core.Direction.UP, clickAt, false);
                            var r = mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                            SkyBlocksMod.LOGGER.info("Shadered+ render test: clicked {} with {}: {}", clickAt, mc.player.getMainHandItem(), r);
                        }
                        if (ticks == 45 && mc.screen instanceof ArcadeScreen arcade) {
                            if (controls) {
                                arcade.testControls();
                            } else if (playGame >= 0) {
                                arcade.testPlay(playGame, playGame == 7 && !pickOnly ? new int[] {org.lwjgl.glfw.GLFW.GLFW_KEY_U, org.lwjgl.glfw.GLFW.GLFW_KEY_U} : new int[0]);
                            }
                        }
                        if (watch && ticks == 70 && mc.screen instanceof ArcadeScreen arcade) {
                            arcade.testKey(org.lwjgl.glfw.GLFW.GLFW_KEY_D); // (the stick pushed, a button held: seen on the cabinet)
                        }
                        if (watch && ticks == 92) {
                            mc.setScreen(null); // (looking at it from the room, the game's picture still on)
                        }
                        if (ticks == 94 && playGame == 7 && !pickOnly && mc.screen instanceof ArcadeScreen arcade) {
                            arcade.testKey(moveList ? org.lwjgl.glfw.GLFW.GLFW_KEY_H : org.lwjgl.glfw.GLFW.GLFW_KEY_J); // (a kick, mid-fight; or the move list)
                        }
                        if (ticks == 80 && clickAt != null) {
                            var sbe = mc.getSingleplayerServer().overworld().getBlockEntity(clickAt);
                            var cbe = mc.level.getBlockEntity(clickAt);
                            SkyBlocksMod.LOGGER.info("Shadered+ render test: after the click - server {} dome {} speed {}, client {} dome {} speed {}",
                                    sbe, sbe instanceof com.example.skyblocks.SolarSystemBlockEntity a ? a.dome() : null,
                                    sbe instanceof com.example.skyblocks.SolarSystemBlockEntity a2 ? a2.speed() : null,
                                    cbe, cbe instanceof com.example.skyblocks.SolarSystemBlockEntity b ? b.dome() : null,
                                    cbe instanceof com.example.skyblocks.SolarSystemBlockEntity b2 ? b2.speed() : null);
                        }
                        if (burst && ticks == 2 && mc.player != null) { // (flying: standing still in the air)
                            mc.player.getAbilities().flying = true;
                            mc.player.onUpdateAbilities();
                        }
                        if (moving && ticks >= 50 && ticks < 100 && mc.player != null) {
                            // (walking sideways, as a player would: the camera between ticks follows smoothly)
                            mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                            mc.player.setPos(mc.player.getX() + 0.12, mc.player.getY(), mc.player.getZ());
                        }
                        burstOn = burst && ticks >= 70 && ticks < 100;
                        if (ticks == 60) {
                            WorldTiming.force = true;
                            timedFrames = WorldTiming.frames;
                            timedFrom = System.nanoTime();
                        }
                        if (ticks == 99) { // (how long its frames took: the whole frame, and by part)
                            long n = WorldTiming.frames - timedFrames;
                            double ms = n > 0 ? (System.nanoTime() - timedFrom) / 1.0e6 / n : -1.0;
                            java.util.List<String> lines = new java.util.ArrayList<>();
                            WorldTiming.report(lines);
                            SkyBlocksMod.LOGGER.info("Shadered+ render test: timing spot {} ({}): {} frames, {} ms a frame\n  {}", spot, spots()[spot],
                                    n, String.format(java.util.Locale.ROOT, "%.1f", ms), String.join("\n  ", lines));
                        }
                        if (ticks == 100) { // (the chunks built, the shaders warmed up)
                            String name = "rendertest_" + TAG + "_" + spot + ".png";
                            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> {});
                            SkyBlocksMod.LOGGER.info("Shadered+ render test: took {}", name);
                            stage++;
                            ticks = 0;
                        }
                    } else if (stage == 2 + spots().length || stage == 99) {
                        if (ticks == 60) {
                            SkyBlocksMod.LOGGER.info("Shadered+ render test done");
                            mc.stop();
                        }
                    }
                }
            }
        } catch (Throwable t) {
            SkyBlocksMod.LOGGER.error("Shadered+ render test failed", t);
            stage = 99;
            ticks = 0;
        }
    }
}
