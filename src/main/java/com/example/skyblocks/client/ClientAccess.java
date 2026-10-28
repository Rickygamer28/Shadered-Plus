package com.example.skyblocks.client;

import com.example.skyblocks.client.island.IslandScannerScreen;
import com.example.skyblocks.island.IslandScannerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Client-only actions called from common code (only ever run on the client, so servers never load this class). */
public final class ClientAccess {

    public static void openIslandScanner(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof IslandScannerBlockEntity scanner) {
            mc.setScreen(new IslandScannerScreen(pos, scanner.size(), scanner.above(), scanner.depth(), scanner.showArea()));
        }
    }

    public static void openFogMachine(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.example.skyblocks.FogMachineBlockEntity machine) {
            mc.setScreen(new FogMachineScreen(pos, machine.front(), machine.back(), machine.left(), machine.right(), machine.height(), machine.showArea()));
        }
    }

    public static void openAquarium(boolean ocean, boolean deep) {
        Minecraft.getInstance().setScreen(new AquariumScreen(ocean, deep));
    }

    /** An aquarium lid clicked: its tank's new light, said over the hotbar. */
    public static void showAquariumLight(int light) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("screen.skyblocks.aquarium_light").append(": ")
                    .append(net.minecraft.network.chat.Component.translatable("screen.skyblocks.aquarium_light." + AquariumSettings.LIGHTS[light])
                            .withStyle(net.minecraft.ChatFormatting.AQUA)), true);
        }
    }

    public static void openNeonSign(net.minecraft.core.BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.example.skyblocks.NeonSignBlockEntity sign) {
            mc.setScreen(new NeonSignScreen(pos, sign.text(), sign.colour(), sign.mode() | (sign.outline() ? com.example.skyblocks.NeonSignBlockEntity.OUTLINE_BIT : 0), sign.size(), sign.background()));
        }
    }

    public static void openVanillaPond(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.example.skyblocks.KoiPondBlockEntity pond) {
            mc.setScreen(new VanillaPondScreen(pos, pond.rim(), pond.floor()));
        }
    }

    private ClientAccess() {}

    /** The item renderer for a RenderedBlockItem. */
    public static net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer itemRenderer(String kind) {
        return switch (kind) {
            case "disco_ball" -> new DiscoBallItemRenderer();
            case "fire_tornado" -> new FireTornadoItemRenderer();
            case "jellyfish_tank" -> new JellyTankItemRenderer();
            case "big_lava_lamp" -> new LavaLampItemRenderer(true);
            default -> new LavaLampItemRenderer(false);
        };
    }
}
