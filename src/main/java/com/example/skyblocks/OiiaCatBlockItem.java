package com.example.skyblocks;

import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** The OIIA Cat's item: drawn as the 3D model in hands and item frames (client/OiiaCatItemRenderer). */
public class OiiaCatBlockItem extends BlockItem {
    public OiiaCatBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new com.example.skyblocks.client.OiiaCatItemRenderer();
                }
                return renderer;
            }
        });
    }
}
