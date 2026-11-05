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
            // past the biggest a tank gets: a row 5 x 2 (two 2 x 2 tanks, two single columns) and a column 10 high (8 + 2)
            "fill 6 -60 56 10 -60 57 skyblocks:tall_tank", "setblock -2 -60 -4 skyblocks:kaleidoscope", "fill 36 -60 -44 44 -52 -36 skyblocks:containment_frame hollow", "fill 37 -59 -44 43 -53 -44 air", "fill 37 -59 -36 43 -53 -36 air", "fill 36 -59 -43 36 -53 -37 air", "fill 44 -59 -43 44 -53 -37 air", "fill 37 -60 -43 43 -60 -37 air", "fill 37 -52 -43 43 -52 -37 air", "setblock 40 -56 -40 skyblocks:singularity{items:[{item:{id:\"minecraft:diamond\",count:1},count:2000000L},{item:{id:\"minecraft:grass_block\",count:1},count:9000L}],colour:16744272}", "fill 18 -60 -27 22 -56 -23 skyblocks:containment_frame hollow", "fill 19 -59 -27 21 -57 -27 air", "fill 19 -59 -23 21 -57 -23 air", "fill 18 -59 -26 18 -57 -24 air", "fill 22 -59 -26 22 -57 -24 air", "fill 19 -60 -26 21 -60 -24 air", "fill 19 -56 -26 21 -56 -24 air", "setblock 20 -58 -25 skyblocks:singularity{items:[{item:{id:\"minecraft:diamond\",count:1},count:250000L},{item:{id:\"minecraft:gold_ingot\",count:1},count:9000L},{item:{id:\"minecraft:emerald\",count:1},count:420L},{item:{id:\"minecraft:redstone\",count:1},count:3000L}],colour:16733695}", "setblock 20 -56 -23 skyblocks:display_frame", "fill 60 -60 -25 64 -56 -21 skyblocks:tank_frame hollow", "fill 61 -59 -25 63 -57 -25 skyblocks:tank_glass", "fill 61 -59 -21 63 -57 -21 skyblocks:tank_glass", "fill 60 -59 -24 60 -57 -22 skyblocks:tank_glass", "fill 64 -59 -24 64 -57 -22 skyblocks:tank_glass", "fill 61 -60 -24 63 -60 -22 skyblocks:tank_glass", "fill 61 -56 -24 63 -56 -22 skyblocks:tank_glass", "fill 61 -59 -24 63 -57 -22 air", "setblock 62 -58 -23 skyblocks:whirlpool{fluids:[{fluid:{id:\"minecraft:lava\",amount:1},amount:3600000L},{fluid:{id:\"minecraft:water\",amount:1},amount:5600000L}]}", "fill 57 -61 -18 59 -61 -17 minecraft:water", "fill 70 -46 -12 75 -46 -10 minecraft:barrier", "fill 80 -46 -15 82 -46 -13 minecraft:barrier", "fill 68 -60 -27 76 -52 -19 skyblocks:tank_frame hollow", "fill 69 -59 -27 75 -53 -27 minecraft:glass", "fill 69 -59 -19 75 -53 -19 minecraft:glass", "fill 68 -59 -26 68 -53 -20 minecraft:glass", "fill 76 -59 -26 76 -53 -20 minecraft:glass", "fill 69 -60 -26 75 -60 -20 minecraft:glass", "fill 69 -52 -26 75 -52 -20 minecraft:glass", "fill 69 -59 -26 75 -53 -20 air", "setblock 72 -56 -23 skyblocks:whirlpool{fluids:[{fluid:{id:\"minecraft:water\",amount:1},amount:150000000L},{fluid:{id:\"minecraft:lava\",amount:1},amount:90000000L}]}", "setblock 57 -60 -14 skyblocks:whirlpool{still:1b,fluids:[{fluid:{id:\"minecraft:lava\",amount:1},amount:500000L},{fluid:{id:\"minecraft:water\",amount:1},amount:700000L}]}", "setblock 58 -60 -14 skyblocks:whirlpool{drain:1b,fluids:[{fluid:{id:\"minecraft:water\",amount:1},amount:480000L},{fluid:{id:\"minecraft:lava\",amount:1},amount:240000L}]}", "setblock 19 -60 -23 skyblocks:port_frame[output=true]{filter:{id:\"minecraft:diamond\",count:1}}", "setblock 19 -60 -22 minecraft:chest", "setblock 21 -60 -23 skyblocks:redstone_frame", "setblock 21 -60 -22 minecraft:redstone_lamp", "setblock 22 -58 -23 skyblocks:monitor_frame{filter:{id:\"minecraft:gold_ingot\",count:1}}", "fill -6 -60 -4 -5 -59 -4 skyblocks:kaleidoscope", "fill -3 -60 -18 4 -55 -18 minecraft:stone", "fill 0 -60 -15 1 -58 -15 skyblocks:portal_mirror[facing=south,color=lime]", "fill 30 -60 -15 31 -58 -15 skyblocks:portal_mirror[facing=north,color=lime]", "fill 29 -60 -20 32 -57 -20 minecraft:gold_block", "setblock 30 -60 -18 minecraft:jack_o_lantern[facing=south]", "setblock 31 -60 -17 minecraft:diamond_block", "setblock -3 -60 -4 skyblocks:kaleidoscope{palette:3,symmetry:4}", "setblock 3 -60 -3 skyblocks:singularity{items:[{item:{id:\"minecraft:diamond\",count:1},count:250000L},{item:{id:\"minecraft:gold_ingot\",count:1},count:9000L},{item:{id:\"minecraft:emerald\",count:1},count:420L}],colour:3847130}", "summon item_frame 1 -59 -4 {Facing:2,Fixed:1b,Item:{id:\"shadered:space_block\",count:1}}", "setblock -30 -60 10 skyblocks:planetarium{size:1}", "fill -32 -60 13 -29 -58 13 minecraft:stone_bricks", "fill 12 -60 56 12 -51 56 skyblocks:tall_tank",
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
            "fill -20 -60 20 -17 -56 20 skyblocks:underwater_liquid", "fill -16 -60 20 -14 -56 20 skyblocks:aurora_liquid",
    };
    /** Where the player stands for each picture: x y z yaw pitch ("night " first: at night). */
    private static net.minecraft.core.BlockPos clickAt;
    private static long timedFrames, timedFrom;
    private static final String[] SPOTS = {"0.5 -60 -6.5 0 15", "itemswhole 0.5 -60 -6.5 0 15", "0.5 -60 1.6 0 20", "night 0.5 -60 1.6 0 20", "0.5 -59 5.6 0 40", "night 0.5 -59 5.6 0 40", "0.5 -59.5 8.5 0 8", "night 0.5 -59.5 8.5 0 8", "-2.5 -59.3 10.4 0 30", "-4 -58 18.3 0 55", "night -4 -58 18.3 0 55", "rain -4 -58 18.3 0 55", "4.5 -58 18.3 0 55", "night 4.5 -58 18.3 0 55", "night 0.5 -57 20.5 0 -8", "0.5 -59 27 0 10", "night 0.5 -59 27 0 10", "-4 -59.5 33.3 0 15", "3 -59.5 33.3 0 15", "-4.3 -59.4 34.7 0 22", "3.3 -59.4 34.7 0 22", "2.5 -59.6 12.3 0 23", "1 -59.3 2.9 0 45", "-3 -60 40 0 30", "1 -59.5 39.5 0 10", "night 1 -59.5 39.5 0 10", "-5.5 -60 45 0 5", "0.5 -59.5 52 0 12", "9 -55.5 45 0 6", "-30.5 -57 1 0 10", "-12.6 -58.9 10.1 0 10", "click -12 -60 10 -11.5 -60 8 0 25", "open 3 -60 -3 3.5 -60 -5.5 0 20", "open -12 -60 4 -11.5 -60 2 0 25", "-2 -60 -6.2 0 28", "-5 -59.5 -7.6 0 12", "1 -60 -10.5 180 5", "night 1 -60 -10.5 180 5", "1 -60 -10.5 180 5", "farportal 1 -60 -10.5 180 5", "farportal 3.6 -60 -11.8 160 5", "farportal -1.8 -60 -11.5 200 5", "20.5 -59 -15.5 180 8", "night 20.5 -59 -15.5 180 8", "22.4 -57.2 -20.2 160 22", "20.5 -60 -21.2 180 -12", "20.5 -59.5 -20.2 180 -18", "62.5 -59 -16 180 8", "62.5 -51 -20.5 180 72", "72.5 -45 -11 180 45", "fabulous 72.5 -45 -11 180 45", "81 -45 -14 136 45", "fabulous 81 -45 -14 136 45", "fabulous 72.5 -51 -21.5 180 80", "fabulous 72.5 -57 -17.9 180 -5", "72.5 -60 -17.8 180 -14", "75.5 -60 -17.8 205 -8", "62.5 -59 -30 0 8", "69 -59 -22.5 90 8", "56 -59 -22.5 -90 8", "67 -60 -18 135 2", "57.5 -57 -28 -45 20", "58.5 -59 -11.5 180 40", "open 58 -60 -14 58.5 -60 -11.5 180 30", "41.5 -60 -42.5 18 -42", "43.5 -60 -43 37 -30", "40.5 -60 -39.2 0 -88", "nodepth 41.5 -60 -42.5 18 -42", "nodepth 43.5 -60 -43 37 -30", "38 -59 -33 175 -20", "night 11.5 -60 -8 0 5", "11.5 -59 3.5 -45 15", "11.5 -59 3.5 -45 -25", "10.5 -59.6 -7.8 0 8", "-12.5 -59.3 2.7 0 38", "-12.5 -59.0 5.6 0 15", "night -12.5 -59.0 5.6 0 15", "-17 -59 16 -10 0", "-17 -59 16 10 0"};

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
                            com.example.skyblocks.client.SkyUsage.itemParts = !where.startsWith("itemswhole ");
                            boolean farPortal = where.startsWith("farportal ");
                            if (farPortal != MirrorRenderer.forceFarPortals) {
                                MirrorRenderer.forceFarPortals = farPortal; // (portals drawn from their last pictures, as if the other end were far)
                                MirrorRenderer.portalsChanged();
                            }
                            if (farPortal) {
                                where = where.substring("farportal ".length());
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
                        if (ticks == 80 && clickAt != null) {
                            var sbe = mc.getSingleplayerServer().overworld().getBlockEntity(clickAt);
                            var cbe = mc.level.getBlockEntity(clickAt);
                            SkyBlocksMod.LOGGER.info("Shadered+ render test: after the click - server {} dome {} speed {}, client {} dome {} speed {}",
                                    sbe, sbe instanceof com.example.skyblocks.SolarSystemBlockEntity a ? a.dome() : null,
                                    sbe instanceof com.example.skyblocks.SolarSystemBlockEntity a2 ? a2.speed() : null,
                                    cbe, cbe instanceof com.example.skyblocks.SolarSystemBlockEntity b ? b.dome() : null,
                                    cbe instanceof com.example.skyblocks.SolarSystemBlockEntity b2 ? b2.speed() : null);
                        }
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
                            SkyBlocksMod.LOGGER.info("Shadered+ render test: timing spot {} ({}): {} frames, {} ms a frame\n  {}", spot, SPOTS[spot],
                                    n, String.format(java.util.Locale.ROOT, "%.1f", ms), String.join("\n  ", lines));
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
