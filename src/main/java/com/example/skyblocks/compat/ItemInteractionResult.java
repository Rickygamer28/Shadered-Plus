package com.example.skyblocks.compat;

import net.minecraft.world.InteractionResult;

/**
 * 1.21's result of using an item on a block, for the blocks ported from 1.21.1: PASS_TO_DEFAULT_BLOCK_INTERACTION
 * goes on to the block's own (empty-hand) use, as there.
 */
public enum ItemInteractionResult {
    SUCCESS(InteractionResult.SUCCESS),
    CONSUME(InteractionResult.CONSUME),
    CONSUME_PARTIAL(InteractionResult.CONSUME_PARTIAL),
    PASS_TO_DEFAULT_BLOCK_INTERACTION(InteractionResult.PASS),
    SKIP_DEFAULT_BLOCK_INTERACTION(InteractionResult.PASS),
    FAIL(InteractionResult.FAIL);

    private final InteractionResult result;

    ItemInteractionResult(InteractionResult result) {
        this.result = result;
    }

    public InteractionResult result() {
        return result;
    }

    public boolean consumesAction() {
        return result.consumesAction();
    }

    public static ItemInteractionResult sidedSuccess(boolean client) {
        return client ? SUCCESS : CONSUME;
    }
}
