package com.example.skyblocks;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** A train, put on a line of train rails (TrainEntity): its front where it's used, its cars along the rails behind. */
public class TrainItem extends Item {
    public TrainItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!TrainLine.isRail(level, ctx.getClickedPos())) {
            if (ctx.getPlayer() != null && !level.isClientSide) {
                ctx.getPlayer().displayClientMessage(Component.translatable("message.skyblocks.train.rails"), true);
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            TrainEntity t = ModEntities.TRAIN.get().create(level);
            if (t == null) {
                return InteractionResult.FAIL;
            }
            t.setPos(ctx.getClickedPos().getCenter());
            t.placeAt(ctx.getClickedPos());
            level.addFreshEntity(t);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
                ctx.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
