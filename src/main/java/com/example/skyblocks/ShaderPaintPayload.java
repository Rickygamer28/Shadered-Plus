package com.example.skyblocks;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The shader paint editor -> server: paint this onto the item on the altar (an amethyst shard a time, but in creative). */
public record ShaderPaintPayload(BlockPos pos, ModComponents.ShaderPaint paint) implements CustomPacketPayload {
    public static final Type<ShaderPaintPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "shader_paint"));

    public static final StreamCodec<ByteBuf, ShaderPaintPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ShaderPaintPayload::pos,
            ModComponents.ShaderPaint.STREAM_CODEC, ShaderPaintPayload::paint,
            ShaderPaintPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShaderPaintPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            ModComponents.ShaderPaint paint = payload.paint();
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos()) || player.distanceToSqr(payload.pos().getCenter()) > 8 * 8
                    || !(level.getBlockEntity(payload.pos()) instanceof InfusionAltarBlockEntity altar) || altar.item().isEmpty()
                    || paint.w() < 1 || paint.h() < 1 || paint.w() > 64 || paint.h() > 64 || paint.pixels().length != paint.w() * paint.h()) {
                return;
            }
            for (byte b : paint.pixels()) {
                if ((b & 255) >= ShaderPaintStyles.count()) {
                    return;
                }
            }
            ItemStack item = altar.item().copy();
            ModComponents.ShaderPaint had = item.get(ModComponents.SHADER_PAINT.get());
            boolean clear = paint.empty();
            if (clear ? had == null : paint.equals(had)) {
                return;
            }
            if (!clear && !player.getAbilities().instabuild) {
                int slot = player.getInventory().findSlotMatchingItem(new ItemStack(Items.AMETHYST_SHARD));
                if (slot < 0) {
                    player.displayClientMessage(Component.translatable("message.skyblocks.infusion_altar.shard"), true);
                    return;
                }
                player.getInventory().removeItem(slot, 1);
            }
            if (clear) {
                item.remove(ModComponents.SHADER_PAINT.get());
            } else {
                item.set(ModComponents.SHADER_PAINT.get(), paint);
            }
            altar.setItem(item);
            level.playSound(null, payload.pos(), clear ? SoundEvents.AMETHYST_BLOCK_BREAK : SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, payload.pos().getX() + 0.5, payload.pos().getY() + 1.3,
                    payload.pos().getZ() + 0.5, 20, 0.25, 0.25, 0.25, 0.02);
        });
    }
}
