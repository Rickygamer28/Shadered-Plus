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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Only for the CI run (SKYBLOCKS_RENDER_TEST set): makes a flat world, places the shader blocks in front of the
 * player, takes screenshots from a few spots and closes the game - so the build shows how they really draw.
 */
@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RenderTest {
    static final boolean ON = System.getenv("SKYBLOCKS_RENDER_TEST") != null;
    private static final String TAG = System.getenv().getOrDefault("SKYBLOCKS_RENDER_TEST", "x");
    private static int stage = 0;
    private static int ticks = 0;

    private RenderTest() {}

    private static final String[] ROW1 = {"lava_lamp", "big_lava_lamp", "fire_tornado", "brick_hearth", "koi_pond", "vanilla_koi_pond", "infinity_cube"};
    private static final String[] ROW2 = {"aquarium", "vanilla_aquarium", "dance_floor", "jellyfish_tank", "hologram_projector", "music_visualizer", "neon_sign"};
    /** Where the player stands for each picture: x y z yaw pitch. */
    private static final String[] SPOTS = {"0.5 -60 -5.5 0 20", "0.5 -59 9.5 180 25", "-5.5 -60 -2.5 0 10", "-1.5 -60 0.5 0 15", "-1.5 -60 -2.5 0 5", "night -1.5 -60 -2.5 0 5", "night 0.5 -60 -5.5 0 20"};

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
    public static void onRenderTestTick(TickEvent.ClientTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) {
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
                                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
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
                            for (int i = 0; i < ROW1.length; i++) {
                                run(server, "setblock " + (-6 + 2 * i) + " -60 0 skyblocks:" + ROW1[i]);
                                run(server, "setblock " + (-6 + 2 * i) + " -60 4 skyblocks:" + ROW2[i]);
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
                            }
                            run(mc.getSingleplayerServer(), "tp @a " + where);
                            mc.setScreen(null);
                        }
                        if (ticks == 100) { // (the chunks built, the shaders warmed up)
                            String name = "rendertest_" + TAG + "_" + spot + ".png";
                            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> {});
                            SkyBlocksMod.LOGGER.info("Shadered+ render test: took {} (screen {})", name, mc.screen);
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
