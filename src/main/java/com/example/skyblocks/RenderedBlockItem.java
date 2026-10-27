package com.example.skyblocks;

import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * A block item drawn by its own item renderer (the disco ball, lava lamps, fire tornado, jellyfish tank: their real
 * moving models in hands, frames and the inventory). 1.20.1: the item says so itself (1.21.1 registers it in an event).
 */
public class RenderedBlockItem extends BlockItem {
    private final String kind;

    public RenderedBlockItem(Block block, Properties properties, String kind) {
        super(block, properties);
        this.kind = kind;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = com.example.skyblocks.client.ClientAccess.itemRenderer(kind); // (client-only class)
                }
                return renderer;
            }
        });
    }
}
