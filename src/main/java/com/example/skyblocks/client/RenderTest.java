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
            // a wall of the underwater sky, seen from the same spot turned two ways (the sky must stay put)
            "fill -20 -60 20 -14 -56 20 skyblocks:underwater_sky_block",
    };
    /** Where the player stands for each picture: x y z yaw pitch ("night " first: at night). */
    private static final String[] SPOTS = {"0.5 -60 -6.5 0 15", "0.5 -60 1.6 0 20", "night 0.5 -60 1.6 0 20", "0.5 -59 5.6 0 40", "night 0.5 -59 5.6 0 40", "0.5 -59.5 8.5 0 8", "night 0.5 -59.5 8.5 0 8", "-2.5 -59.3 10.4 0 30", "-4 -58 18.3 0 55", "night -4 -58 18.3 0 55", "rain -4 -58 18.3 0 55", "4.5 -58 18.3 0 55", "night 4.5 -58 18.3 0 55", "night 0.5 -57 20.5 0 -8", "0.5 -59 27 0 10", "night 0.5 -59 27 0 10", "-4 -59.5 33.3 0 15", "3 -59.5 33.3 0 15", "2.5 -59.6 12.3 0 23", "1 -59.3 2.9 0 45", "-3 -60 40 0 30", "1 -59.5 39.5 0 10", "night 1 -59.5 39.5 0 10", "-5.5 -60 45 0 5", "0.5 -59.5 52 0 12", "night 11.5 -60 -8 0 5", "11.5 -59 3.5 -45 15", "11.5 -59 3.5 -45 -25", "10.5 -59.6 -7.8 0 8", "-12.5 -59.3 2.7 0 38", "-12.5 -59.0 5.6 0 15", "night -12.5 -59.0 5.6 0 15", "-17 -59 16 -10 0", "-17 -59 16 10 0"};

    private static void run(MinecraftServer server, String command) {
        server.execute(() -> {
            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
            } catch (Throwable t) {
                SkyBlocksMod.LOGGER.warn("Shadered+ render test: '{}' failed", command, t);
            }
        });
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
                            for (String c : SCENE) {
                                run(server, c);
                            }
                            stage = 2;
                            ticks = 0;
                        }
                    } else if (ticks > 2400) {
                        SkyBlocksMod.LOGGER.warn("Shadered+ render test: no world after 2 minutes");
                        stage = 99;
                        ticks = 0;
                    }
                }
                default -> {
                    if (stage >= 2 && stage < 2 + SPOTS.length) {
                        int spot = stage - 2;
                        if (ticks == 1) {
                            String where = SPOTS[spot];
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
                        if (ticks == 100) { // (the chunks built, the shaders warmed up)
                            String name = "rendertest_" + TAG + "_" + spot + ".png";
                            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> {});
                            SkyBlocksMod.LOGGER.info("Shadered+ render test: took {}", name);
                            stage++;
                            ticks = 0;
                        }
                    } else if (stage == 2 + SPOTS.length || stage == 99) {
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
