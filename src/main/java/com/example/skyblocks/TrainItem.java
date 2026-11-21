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
        // (on smooth track: near where you clicked, under it)
        TrackNetwork.Near near = TrackNetwork.of(level).nearest(ctx.getClickLocation(), 1.6);
        if (near != null) {
            if (!level.isClientSide) {
                TrainEntity t = ModEntities.TRAIN.get().create(level);
                if (t == null) {
                    return InteractionResult.FAIL;
                }
                net.minecraft.world.phys.Vec3 p = near.seg().pts[near.index()];
                t.setPos(p);
                t.placeAt(p, ctx.getPlayer() != null ? ctx.getPlayer().getLookAngle() : near.seg().tan[near.index()]);
                level.addFreshEntity(t);
                if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
                    ctx.getItemInHand().shrink(1);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
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
            t.placeAt(ctx.getClickedPos(), ctx.getPlayer() != null ? ctx.getPlayer().getLookAngle() : new net.minecraft.world.phys.Vec3(ctx.getHorizontalDirection().getStepX(), 0, ctx.getHorizontalDirection().getStepZ()));
            level.addFreshEntity(t);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
                ctx.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
