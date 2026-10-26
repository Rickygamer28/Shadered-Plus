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

    /** An aquarium lid clicked: the next light for that type of tank, said over the hotbar. */
    public static void cycleAquariumLight(boolean ocean) {
        int light = (AquariumSettings.light(ocean) + 1) % AquariumSettings.LIGHTS.length;
        AquariumSettings.setLight(ocean, light);
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
}
