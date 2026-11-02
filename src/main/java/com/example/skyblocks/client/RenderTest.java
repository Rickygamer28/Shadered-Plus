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
    };
    /** Where the player stands for each picture: x y z yaw pitch ("night " first: at night). */
    private static final String[] SPOTS = {"0.5 -60 -6.5 0 15", "0.5 -60 1.6 0 20", "night 0.5 -60 1.6 0 20"};

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
