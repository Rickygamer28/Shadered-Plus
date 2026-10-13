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
            mc.setScreen(new IslandScannerScreen(pos, scanner.size(), scanner.above(), scanner.depth()));
        }
    }

    private ClientAccess() {}
}
